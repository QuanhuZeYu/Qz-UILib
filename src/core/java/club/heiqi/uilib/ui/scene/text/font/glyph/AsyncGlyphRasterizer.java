package club.heiqi.uilib.ui.scene.text.font.glyph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontEngine;

/** 单个按需启动的 owned worker。GL/render 请求只操作短临界区，不打开字体或等待光栅。
 * 位图、pending、ready通知和错误各有上限；错误保留身份与原因，不自动重试。
 * close 只发关闭信号；awaitClose 专用于宿主销毁验收，不能用来等待每帧字形。
 */
public final class AsyncGlyphRasterizer implements AutoCloseable {
    public static final class Budget {
        public final int pending, cacheEntries, ready, faces, errors;
        public final long bitmapBytes, faceBytes;
        public final float maxSize;

        public Budget(
                int pending,
                int cacheEntries,
                int ready,
                long bitmapBytes,
                int faces,
                long faceBytes,
                int errors,
                float maxSize) {
            if (pending < 1
                    || cacheEntries < 1
                    || ready < 1
                    || bitmapBytes < 1
                    || faces < 1
                    || faceBytes < 1
                    || errors < 1
                    || !Float.isFinite(maxSize)
                    || maxSize <= 0) throw new IllegalArgumentException("无效 CPU 字形预算");
            this.pending = pending;
            this.cacheEntries = cacheEntries;
            this.ready = ready;
            this.bitmapBytes = bitmapBytes;
            this.faces = faces;
            this.faceBytes = faceBytes;
            this.errors = errors;
            this.maxSize = maxSize;
        }
    }

    public static final class Failure {
        public final GlyphKey key;
        public final String message;

        private Failure(GlyphKey key, Throwable failure) {
            this.key = key;
            message = failure.toString();
        }
    }

    public static final class Stats {
        public final int pending, ready, cacheEntries, faces, errors;
        public final long bitmapBytes,
                faceBytes,
                requests,
                deduplicated,
                rasterized,
                rejected,
                evicted,
                cancelled,
                version;
        public final boolean started, closed, terminated;

        private Stats(AsyncGlyphRasterizer r) {
            pending = r.pending.size();
            ready = r.ready.size();
            cacheEntries = r.cache.size();
            faces = r.faceCount;
            errors = r.failures.size();
            bitmapBytes = r.bytes;
            faceBytes = r.faceBytes;
            requests = r.requests;
            deduplicated = r.deduplicated;
            rasterized = r.rasterized;
            rejected = r.rejected;
            evicted = r.evicted;
            cancelled = r.cancelled;
            version = r.version;
            started = r.worker != null;
            closed = r.closed;
            terminated = r.terminated;
        }
    }

    private static final class FaceKey {
        final FontSource source;
        final float size;

        FaceKey(GlyphKey key) {
            source = key.source;
            size = key.size;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(source) + Float.floatToIntBits(size);
        }

        @Override
        public boolean equals(Object value) {
            return value instanceof FaceKey
                    && source == ((FaceKey) value).source
                    && size == ((FaceKey) value).size;
        }
    }

    private final Budget budget;
    private final FontEngine engine;
    private final LinkedHashMap<GlyphKey, Boolean> pending = new LinkedHashMap<GlyphKey, Boolean>();
    private final LinkedHashMap<GlyphKey, GlyphBitmap> cache =
            new LinkedHashMap<GlyphKey, GlyphBitmap>(16, .75f, true);
    private final LinkedHashMap<GlyphKey, Failure> failures =
            new LinkedHashMap<GlyphKey, Failure>(16, .75f, true);
    private final ArrayDeque<GlyphKey> ready = new ArrayDeque<GlyphKey>();
    private Thread worker;
    private boolean closed, terminated;
    private long bytes,
            faceBytes,
            requests,
            deduplicated,
            rasterized,
            rejected,
            evicted,
            cancelled,
            version;
    private int faceCount;
    private String workerFailure;

    public AsyncGlyphRasterizer(Budget budget, FontEngine engine) {
        this.budget = java.util.Objects.requireNonNull(budget, "budget");
        this.engine = java.util.Objects.requireNonNull(engine, "engine");
    }

    public synchronized GlyphBitmap request(GlyphKey key) {
        java.util.Objects.requireNonNull(key, "key");
        if (closed) throw new IllegalStateException("glyph worker 已关闭");
        requests++;
        GlyphBitmap bitmap = cache.get(key);
        if (bitmap != null) {
            deduplicated++;
            return bitmap;
        }
        if (pending.containsKey(key) || failures.containsKey(key)) {
            deduplicated++;
            return null;
        }
        if (workerFailure != null
                || key.size > budget.maxSize
                || pending.size() >= budget.pending) {
            rejected++;
            return null;
        }
        pending.put(key, Boolean.FALSE);
        if (worker == null) {
            worker = new Thread(this::work, "UILib-glyph-raster");
            worker.setDaemon(true);
            worker.start();
        }
        notifyAll();
        return null;
    }

    public synchronized GlyphBitmap peek(GlyphKey key) {
        return cache.get(key);
    }

    /** 当前宿主可见集合更新，移除尚未执行的过期需求；不打断正在进行的单次原生调用。 */
    public synchronized void retainPending(Collection<GlyphKey> visible) {
        Set<GlyphKey> keys = new HashSet<GlyphKey>(visible);
        Iterator<Map.Entry<GlyphKey, Boolean>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<GlyphKey, Boolean> e = it.next();
            if (!e.getValue() && !keys.contains(e.getKey())) {
                it.remove();
                cancelled++;
            }
        }
    }

    public synchronized List<GlyphKey> drainReady(int limit) {
        if (limit < 0) throw new IllegalArgumentException("无效通知预算");
        List<GlyphKey> result = new ArrayList<GlyphKey>();
        while (result.size() < limit && !ready.isEmpty()) result.add(ready.removeFirst());
        notifyAll();
        return result;
    }

    public synchronized List<Failure> failures() {
        return new ArrayList<Failure>(failures.values());
    }

    public synchronized String workerFailure() {
        return workerFailure;
    }

    public synchronized Stats stats() {
        return new Stats(this);
    }

    private void fail(GlyphKey key, Throwable error) {
        failures.put(key, new Failure(key, error));
        while (failures.size() > budget.errors)
            failures.remove(failures.keySet().iterator().next());
        version++;
    }

    private GlyphKey next() throws InterruptedException {
        synchronized (this) {
            for (; ; ) {
                if (closed) return null;
                // 满通知队列暂停生产，宿主在 prepare drain 后继续；不抛弃 ready 事件。
                if (ready.size() < budget.ready)
                    for (Map.Entry<GlyphKey, Boolean> entry : pending.entrySet())
                        if (!entry.getValue()) {
                            entry.setValue(Boolean.TRUE);
                            return entry.getKey();
                        }
                wait();
            }
        }
    }

    private void work() {
        LinkedHashMap<FaceKey, FontFace> faces =
                new LinkedHashMap<FaceKey, FontFace>(16, .75f, true);
        long ownedFaceBytes = 0;
        try (FontSession service = engine.openSession()) {
            for (GlyphKey key; (key = next()) != null; ) {
                GlyphBitmap bitmap = null;
                Throwable failure = null;
                try {
                    FaceKey faceKey = new FaceKey(key);
                    FontFace face = faces.get(faceKey);
                    if (face == null) {
                        int sourceBytes = key.source.byteSize();
                        if (sourceBytes > budget.faceBytes)
                            throw new IllegalArgumentException("字体超过 face bytes 预算: " + key.source);
                        while (faces.size() >= budget.faces
                                || ownedFaceBytes + sourceBytes > budget.faceBytes) {
                            FaceKey first = faces.keySet().iterator().next();
                            FontFace old = faces.remove(first);
                            ownedFaceBytes -= first.source.byteSize();
                            old.close();
                        }
                        face = key.source.openFace(service, key.size);
                        faces.put(faceKey, face);
                        ownedFaceBytes += sourceBytes;
                    }
                    bitmap = face.rasterize(key.glyphId, key.rasterStyle);
                    if (bitmap.byteSize() > budget.bitmapBytes)
                        throw new IllegalArgumentException("单字形超过 CPU bitmap bytes 预算: " + key);
                } catch (RuntimeException error) {
                    failure = error;
                }
                synchronized (this) {
                    faceCount = faces.size();
                    faceBytes = ownedFaceBytes;
                    pending.remove(key);
                    if (closed) break;
                    if (failure != null) fail(key, failure);
                    else {
                        long size = bitmap.byteSize();
                        while (cache.size() >= budget.cacheEntries
                                || bytes + size > budget.bitmapBytes) {
                            GlyphKey first = cache.keySet().iterator().next();
                            GlyphBitmap old = cache.remove(first);
                            bytes -= old.byteSize();
                            ready.remove(first);
                            evicted++;
                        }
                        cache.put(key, bitmap);
                        bytes += size;
                        ready.addLast(key);
                        rasterized++;
                        version++;
                    }
                    notifyAll();
                }
            }
        } catch (Throwable failure) {
            synchronized (this) {
                workerFailure = failure.toString();
                version++;
            }
        } finally {
            synchronized (this) {
                faceCount = 0;
                faceBytes = 0;
                pending.clear();
                terminated = true;
                notifyAll();
            }
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        pending.clear();
        ready.clear();
        cache.clear();
        failures.clear();
        bytes = 0;
        if (worker == null) terminated = true;
        notifyAll();
    }

    public synchronized boolean awaitClose(long timeoutMillis) throws InterruptedException {
        if (!closed) throw new IllegalStateException("先关闭 worker");
        if (timeoutMillis < 0) throw new IllegalArgumentException("无效超时");
        long end = System.nanoTime() + timeoutMillis * 1000000L;
        while (!terminated) {
            long left = end - System.nanoTime();
            if (left <= 0) return false;
            wait(Math.max(1, left / 1000000L));
        }
        return true;
    }
}
