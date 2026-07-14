package club.heiqi.uilib.ui.image;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 跨帧宿主物品栅格缓存、预算与公平补图会话。
 *
 * <p>会话只按 {@link HostImageSource} identity 与栅格尺寸区分条目，绝不读取 registry、NBT 或显示名。</p>
 */
public final class HostImageRenderSession implements AutoCloseable {
    public static final int DEFAULT_MAX_ENTRIES = 128;
    public static final int DEFAULT_CALLS_PER_FRAME = 2;
    public static final long DEFAULT_BUDGET_NANOS = 2_000_000L;
    public static final long LIVE_REFRESH_NANOS = 500_000_000L;
    public static final long FAILURE_COOLDOWN_NANOS = 5_000_000_000L;

    /** 由渲染线程拥有并释放的缓存栅格。 */
    public interface CachedRaster extends AutoCloseable {
        @Override
        void close();
    }

    /** 单次昂贵栅格化测试缝。 */
    public interface Rasterizer {
        RasterizeResult rasterize(HostImageSource source, int width, int height);
    }

    /** 可替换的单调时钟。 */
    public interface NanoClock { long nanoTime(); }

    /** 栅格化产物；只有 outcome 成功且 recovered 时资源才会发布。 */
    public static final class RasterizeResult {
        private final CachedRaster raster;
        private final HostImageRenderOutcome outcome;

        public RasterizeResult(CachedRaster raster, HostImageRenderOutcome outcome) {
            this.raster = raster;
            this.outcome = outcome;
        }

        public CachedRaster getRaster() { return raster; }
        public HostImageRenderOutcome getOutcome() { return outcome; }
    }

    /** 当前请求的决策。 */
    public static final class RequestResult {
        public enum Status { CACHE_HIT, RASTERIZED, PLACEHOLDER, FAILED_RECOVERED, ABORT_FRAME }
        private final Status status;
        private final CachedRaster raster;
        private final HostImageRenderOutcome outcome;

        private RequestResult(Status status, CachedRaster raster, HostImageRenderOutcome outcome) {
            this.status = status;
            this.raster = raster;
            this.outcome = outcome;
        }

        public Status getStatus() { return status; }
        public CachedRaster getRaster() { return raster; }
        public HostImageRenderOutcome getOutcome() { return outcome; }
    }

    private final int maxEntries;
    private final int callsPerFrame;
    private final long budgetNanos;
    private final NanoClock clock;
    private final LinkedHashMap<CacheKey, CacheEntry> cache =
            new LinkedHashMap<CacheKey, CacheEntry>(16, 0.75F, true);
    private final Deque<CacheKey> pending = new ArrayDeque<CacheKey>();
    private final Set<CacheKey> pendingSet = Collections.newSetFromMap(new LinkedHashMap<CacheKey, Boolean>());
    private final Map<CacheKey, Long> failureUntil = new LinkedHashMap<CacheKey, Long>();
    private long spentNanos;
    private int callsThisFrame;

    /** 创建生产预算会话。 */
    public HostImageRenderSession() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_CALLS_PER_FRAME, DEFAULT_BUDGET_NANOS, System::nanoTime);
    }

    /** 创建可确定测试的预算会话。 */
    public HostImageRenderSession(int maxEntries, int callsPerFrame, long budgetNanos, NanoClock clock) {
        this.maxEntries = Math.max(1, maxEntries);
        this.callsPerFrame = Math.max(1, callsPerFrame);
        this.budgetNanos = Math.max(1L, budgetNanos);
        this.clock = clock == null ? System::nanoTime : clock;
        beginFrame();
    }

    /** 重置本帧软预算；公平队列跨帧保留。 */
    public void beginFrame() {
        callsThisFrame = 0;
        spentNanos = 0L;
    }

    /**
     * 请求一个 ItemStack 栅格。
     *
     * @return 命中、补图、占位或 fail-closed 决策
     */
    public RequestResult request(HostImageSource source, int width, int height, Rasterizer rasterizer) {
        if (source == null || source.getKind() != HostImageSource.Kind.ITEM_STACK || rasterizer == null) {
            return result(RequestResult.Status.PLACEHOLDER, null, null);
        }
        CacheKey lookup = new CacheKey(source, Math.max(1, width), Math.max(1, height));
        CacheEntry entry = cache.get(lookup);
        long now = clock.nanoTime();
        boolean stale = entry != null && source.getItemPolicy() == HostImageSource.ItemPolicy.LIVE
                && now - entry.renderedAtNanos >= LIVE_REFRESH_NANOS;
        if (entry != null && !stale) {
            return result(RequestResult.Status.CACHE_HIT, entry.raster, HostImageRenderOutcome.success());
        }

        CacheKey key = entry == null ? lookup : entry.key;
        enqueue(key);
        Long cooldown = failureUntil.get(key);
        if (cooldown != null && now < cooldown.longValue()) {
            return result(entry == null ? RequestResult.Status.FAILED_RECOVERED : RequestResult.Status.CACHE_HIT,
                    entry == null ? null : entry.raster, null);
        }
        if (!key.equals(pending.peekFirst()) || callsThisFrame >= callsPerFrame
                || spentNanos >= budgetNanos) {
            return result(entry == null ? RequestResult.Status.PLACEHOLDER : RequestResult.Status.CACHE_HIT,
                    entry == null ? null : entry.raster, null);
        }

        pending.removeFirst();
        pendingSet.remove(key);
        callsThisFrame++;
        RasterizeResult rendered;
        long callStart = clock.nanoTime();
        try {
            rendered = rasterizer.rasterize(source, key.width, key.height);
        } catch (RuntimeException exception) {
            rendered = new RasterizeResult(null,
                    HostImageRenderOutcome.failure("rasterize", exception, false, "uncaught-rasterizer"));
        } catch (LinkageError error) {
            rendered = new RasterizeResult(null,
                    HostImageRenderOutcome.failure("rasterize", error, false, "uncaught-linkage"));
        } finally {
            spentNanos += Math.max(0L, clock.nanoTime() - callStart);
        }
        HostImageRenderOutcome outcome = rendered == null ? null : rendered.getOutcome();
        CachedRaster candidate = rendered == null ? null : rendered.getRaster();
        if (outcome == null || !outcome.isRendered() || !outcome.isRecovered() || candidate == null) {
            closeQuietly(candidate);
            failureUntil.put(key, Long.valueOf(now + FAILURE_COOLDOWN_NANOS));
            boolean recovered = outcome != null && outcome.isRecovered();
            return result(recovered ? RequestResult.Status.FAILED_RECOVERED : RequestResult.Status.ABORT_FRAME,
                    entry == null ? null : entry.raster, outcome);
        }

        failureUntil.remove(key);
        CacheEntry replacement = new CacheEntry(key, candidate, now);
        CacheEntry previous = cache.put(key, replacement);
        if (previous != null && previous.raster != candidate) {
            closeQuietly(previous.raster);
        }
        trimLru();
        return result(RequestResult.Status.RASTERIZED, candidate, outcome);
    }

    /** @return 缓存条目数（诊断/测试） */
    public int getCacheSize() { return cache.size(); }
    /** @return 当前公平等待队列长度（诊断/测试） */
    public int getPendingCount() { return pending.size(); }

    /**
     * 推进宿主资源纪元并丢弃旧 GPU 栅格。
     *
     * <p>必须在拥有 GL context 的 render thread 调用；SNAPSHOT 会在下一次可见请求时重新补图。</p>
     */
    public void advanceEpoch() {
        close();
        beginFrame();
    }

    @Override
    public void close() {
        for (CacheEntry entry : cache.values()) closeQuietly(entry.raster);
        cache.clear();
        pending.clear();
        pendingSet.clear();
        failureUntil.clear();
    }

    private void enqueue(CacheKey key) {
        if (pendingSet.add(key)) pending.addLast(key);
    }

    private void trimLru() {
        Iterator<Map.Entry<CacheKey, CacheEntry>> iterator = cache.entrySet().iterator();
        while (cache.size() > maxEntries && iterator.hasNext()) {
            Map.Entry<CacheKey, CacheEntry> eldest = iterator.next();
            closeQuietly(eldest.getValue().raster);
            iterator.remove();
            failureUntil.remove(eldest.getKey());
        }
    }

    private static RequestResult result(RequestResult.Status status, CachedRaster raster,
            HostImageRenderOutcome outcome) {
        return new RequestResult(status, raster, outcome);
    }

    private static void closeQuietly(CachedRaster raster) {
        if (raster != null) {
            try { raster.close(); } catch (RuntimeException ignored) { /* close 仅作 GPU 释放兜底 */ }
        }
    }

    /** identity key，equals 故意不委托 source。 */
    private static final class CacheKey {
        private final HostImageSource source;
        private final int width;
        private final int height;
        private final int hash;

        private CacheKey(HostImageSource source, int width, int height) {
            this.source = source;
            this.width = width;
            this.height = height;
            this.hash = 31 * (31 * System.identityHashCode(source) + width) + height;
        }

        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof CacheKey)) return false;
            CacheKey that = (CacheKey) other;
            return source == that.source && width == that.width && height == that.height;
        }
    }

    private static final class CacheEntry {
        private final CacheKey key;
        private final CachedRaster raster;
        private final long renderedAtNanos;
        private CacheEntry(CacheKey key, CachedRaster raster, long renderedAtNanos) {
            this.key = key;
            this.raster = raster;
            this.renderedAtNanos = renderedAtNanos;
        }
    }
}
