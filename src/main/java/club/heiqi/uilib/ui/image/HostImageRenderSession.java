package club.heiqi.uilib.ui.image;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 跨帧 ItemStack icon 栅格缓存、预算与公平补图会话。
 *
 * <p>会话只按 {@link HostImageSource} identity 与单一正方形 raster side 区分条目，
 * 绝不读取 registry、NBT 或显示名。</p>
 */
public final class HostImageRenderSession implements AutoCloseable {
    public static final int DEFAULT_MAX_ENTRIES = 128;
    public static final int DEFAULT_CALLS_PER_FRAME = 2;
    public static final long DEFAULT_BUDGET_NANOS = 2_000_000L;
    public static final long FAILURE_COOLDOWN_NANOS = 5_000_000_000L;

    /** 由渲染线程拥有并释放的缓存栅格。 */
    public interface CachedRaster extends AutoCloseable {
        @Override
        void close();
    }

    /** 单次昂贵栅格化测试缝。 */
    public interface Rasterizer {
        RasterizeResult rasterize(HostImageSource source, int rasterSide);
    }

    /** 可替换的单调时钟。 */
    public interface NanoClock { long nanoTime(); }

    /** 栅格化产物；只有 publishable outcome 且 raster 非空时资源才会发布。 */
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
        public enum Status { CACHE_HIT, RASTERIZED, PLACEHOLDER, UNAVAILABLE, ABORT_FRAME }
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
    private final Map<CacheKey, Long> pendingLastSeenFrame = new LinkedHashMap<CacheKey, Long>();
    private final Map<CacheKey, Long> failureUntil = new LinkedHashMap<CacheKey, Long>();
    private final Deque<CachedRaster> cleanupPending = new ArrayDeque<CachedRaster>();
    private Throwable cleanupFailureThisFrame;
    private long spentNanos;
    private int callsThisFrame;
    private long frameIndex;

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

    /** 重置本帧软预算；仅保留上一帧仍被请求的公平队列项。 */
    public void beginFrame() {
        cleanupFailureThisFrame = retryPendingCleanup();
        if (frameIndex > 0L) {
            Iterator<CacheKey> iterator = pending.iterator();
            while (iterator.hasNext()) {
                CacheKey key = iterator.next();
                Long lastSeen = pendingLastSeenFrame.get(key);
                // beginFrame 早于本帧遍历，只能淘汰在完整上一帧中未再次出现的项。
                if (lastSeen == null || lastSeen.longValue() < frameIndex) {
                    iterator.remove();
                    pendingSet.remove(key);
                    pendingLastSeenFrame.remove(key);
                }
            }
        }
        long now = clock.nanoTime();
        Iterator<Map.Entry<CacheKey, Long>> failureIterator = failureUntil.entrySet().iterator();
        while (failureIterator.hasNext()) {
            if (failureIterator.next().getValue().longValue() <= now) {
                failureIterator.remove();
            }
        }
        frameIndex++;
        callsThisFrame = 0;
        spentNanos = 0L;
    }

    /**
     * 请求一个正方形 ItemStack icon 栅格。
     *
     * @return 命中、补图、占位或 fail-closed 决策
     */
    public RequestResult request(HostImageSource source, int rasterSide, Rasterizer rasterizer) {
        if (source == null || source.getKind() != HostImageSource.Kind.ITEM_ICON || rasterizer == null
                || rasterSide <= 0) {
            return result(RequestResult.Status.UNAVAILABLE, null,
                    HostImageRenderOutcome.unavailable("request", null, "invalid-item-icon-request"));
        }
        CacheKey key = new CacheKey(source, rasterSide);
        CacheEntry entry = cache.get(key);
        long now = clock.nanoTime();
        if (entry != null) {
            return result(RequestResult.Status.CACHE_HIT, entry.raster, HostImageRenderOutcome.publishable());
        }
        if (cleanupFailureThisFrame != null || !cleanupPending.isEmpty()) {
            return result(RequestResult.Status.ABORT_FRAME, null,
                    cleanupFailureOutcome(cleanupFailureThisFrame, null));
        }

        enqueue(key);
        Long cooldown = failureUntil.get(key);
        if (cooldown != null && now < cooldown.longValue()) {
            removePending(key);
            return result(RequestResult.Status.UNAVAILABLE, null,
                    HostImageRenderOutcome.unavailable("cooldown", null, "failure-cooldown"));
        }
        if (!key.equals(pending.peekFirst()) || callsThisFrame >= callsPerFrame
                || spentNanos >= budgetNanos) {
            return result(RequestResult.Status.PLACEHOLDER, null,
                    HostImageRenderOutcome.unavailable("budget", null, "raster-deferred"));
        }

        removePending(key);
        callsThisFrame++;
        RasterizeResult rendered;
        long callStart = clock.nanoTime();
        try {
            rendered = rasterizer.rasterize(source, key.rasterSide);
        } catch (RuntimeException exception) {
            rendered = new RasterizeResult(null,
                    HostImageRenderOutcome.hostStateLost("rasterize", exception, "uncaught-rasterizer"));
        } catch (LinkageError error) {
            rendered = new RasterizeResult(null,
                    HostImageRenderOutcome.hostStateLost("rasterize", error, "uncaught-linkage"));
        } finally {
            spentNanos += Math.max(0L, clock.nanoTime() - callStart);
        }
        HostImageRenderOutcome outcome = rendered == null ? null : rendered.getOutcome();
        CachedRaster candidate = rendered == null ? null : rendered.getRaster();
        if (outcome == null) {
            outcome = HostImageRenderOutcome.unavailable("rasterize", null, "missing-outcome");
        } else if (outcome.isPublishable() && candidate == null) {
            outcome = HostImageRenderOutcome.unavailable("publish", null, "missing-raster");
        }
        if (!outcome.isPublishable()) {
            Throwable cleanupFailure = closeOrRetain(candidate);
            if (cleanupFailure != null) {
                failureUntil.remove(key);
                return result(RequestResult.Status.ABORT_FRAME, null,
                        cleanupFailureOutcome(cleanupFailure, outcome));
            }
            if (outcome.isHostStateLost()) {
                // 宿主状态异常不能降级成下一帧 placeholder；下一帧必须重新探测。
                failureUntil.remove(key);
                return result(RequestResult.Status.ABORT_FRAME, null, outcome);
            }
            failureUntil.put(key, Long.valueOf(now + FAILURE_COOLDOWN_NANOS));
            trimFailureCooldowns();
            return result(RequestResult.Status.UNAVAILABLE, null, outcome);
        }

        failureUntil.remove(key);
        CacheEntry replacement = new CacheEntry(candidate);
        CacheEntry previous = cache.put(key, replacement);
        Throwable cleanupFailure = null;
        if (previous != null && previous.raster != candidate) {
            cleanupFailure = closeOrRetain(previous.raster);
        }
        cleanupFailure = appendCloseFailure(cleanupFailure, trimLru());
        if (cleanupFailure != null) {
            return result(RequestResult.Status.ABORT_FRAME, null,
                    cleanupFailureOutcome(cleanupFailure, outcome));
        }
        return result(RequestResult.Status.RASTERIZED, candidate, outcome);
    }

    /** @return 缓存条目数（诊断/测试） */
    public int getCacheSize() { return cache.size(); }
    /** @return 当前公平等待队列长度（诊断/测试） */
    public int getPendingCount() { return pending.size(); }
    /** @return 当前失败冷却条目数（包内诊断/测试） */
    int getFailureCooldownCount() { return failureUntil.size(); }
    /** @return 等待统一 cleanup 重试的栅格数（包内诊断/测试） */
    int getPendingCleanupCount() { return cleanupPending.size(); }
    /** @return 是否仍有失败栅格等待下一帧或 close 重试 */
    public boolean hasPendingCleanup() { return !cleanupPending.isEmpty(); }

    /** 直接清空 item raster、队列与 cooldown；必须在拥有 GL context 的 render thread 调用。 */
    public void clear() {
        Throwable firstFailure = null;
        Deque<CachedRaster> rasters = new ArrayDeque<CachedRaster>();
        for (CacheEntry entry : cache.values()) {
            if (entry.raster != null) {
                rasters.addLast(entry.raster);
            }
        }
        rasters.addAll(cleanupPending);
        cache.clear();
        cleanupPending.clear();
        pending.clear();
        pendingSet.clear();
        pendingLastSeenFrame.clear();
        failureUntil.clear();
        while (!rasters.isEmpty()) {
            CachedRaster raster = rasters.removeFirst();
            try {
                raster.close();
            } catch (RuntimeException failure) {
                firstFailure = appendCloseFailure(firstFailure, failure);
                retainForCleanup(raster);
            } catch (LinkageError failure) {
                firstFailure = appendCloseFailure(firstFailure, failure);
                retainForCleanup(raster);
            } catch (Error failure) {
                firstFailure = appendCloseFailure(firstFailure, failure);
                retainForCleanup(raster);
            }
        }
        cleanupFailureThisFrame = firstFailure;
        if (firstFailure instanceof RuntimeException) throw (RuntimeException) firstFailure;
        if (firstFailure instanceof Error) throw (Error) firstFailure;
    }

    @Override
    public void close() {
        clear();
    }

    private void enqueue(CacheKey key) {
        if (pendingSet.add(key)) pending.addLast(key);
        pendingLastSeenFrame.put(key, Long.valueOf(frameIndex));
    }

    private void removePending(CacheKey key) {
        pending.remove(key);
        pendingSet.remove(key);
        pendingLastSeenFrame.remove(key);
    }

    private Throwable trimLru() {
        Throwable firstFailure = null;
        Iterator<Map.Entry<CacheKey, CacheEntry>> iterator = cache.entrySet().iterator();
        while (cache.size() > maxEntries && iterator.hasNext()) {
            Map.Entry<CacheKey, CacheEntry> eldest = iterator.next();
            firstFailure = appendCloseFailure(firstFailure, closeOrRetain(eldest.getValue().raster));
            iterator.remove();
            failureUntil.remove(eldest.getKey());
        }
        return firstFailure;
    }

    private void trimFailureCooldowns() {
        Iterator<CacheKey> iterator = failureUntil.keySet().iterator();
        while (failureUntil.size() > maxEntries && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static RequestResult result(RequestResult.Status status, CachedRaster raster,
            HostImageRenderOutcome outcome) {
        return new RequestResult(status, raster, outcome);
    }

    private Throwable closeOrRetain(CachedRaster raster) {
        if (raster == null) {
            return null;
        }
        try {
            raster.close();
            return null;
        } catch (RuntimeException failure) {
            retainForCleanup(raster);
            cleanupFailureThisFrame = appendCloseFailure(cleanupFailureThisFrame, failure);
            return failure;
        } catch (LinkageError failure) {
            retainForCleanup(raster);
            cleanupFailureThisFrame = appendCloseFailure(cleanupFailureThisFrame, failure);
            return failure;
        } catch (Error failure) {
            retainForCleanup(raster);
            cleanupFailureThisFrame = appendCloseFailure(cleanupFailureThisFrame, failure);
            throw failure;
        }
    }

    private Throwable retryPendingCleanup() {
        Throwable firstFailure = null;
        int attempts = cleanupPending.size();
        while (attempts-- > 0) {
            CachedRaster raster = cleanupPending.removeFirst();
            try {
                raster.close();
            } catch (RuntimeException failure) {
                firstFailure = appendCloseFailure(firstFailure, failure);
                cleanupPending.addLast(raster);
            } catch (LinkageError failure) {
                firstFailure = appendCloseFailure(firstFailure, failure);
                cleanupPending.addLast(raster);
            } catch (Error failure) {
                firstFailure = appendCloseFailure(firstFailure, failure);
                cleanupPending.addLast(raster);
            }
        }
        if (isFatal(firstFailure)) throw (Error) firstFailure;
        return firstFailure;
    }

    private void retainForCleanup(CachedRaster raster) {
        if (raster != null && !cleanupPending.contains(raster)) {
            cleanupPending.addLast(raster);
        }
    }

    private static HostImageRenderOutcome cleanupFailureOutcome(Throwable cleanupFailure,
            HostImageRenderOutcome previousOutcome) {
        Throwable previousFailure = previousOutcome == null ? null : previousOutcome.getFailure();
        if (cleanupFailure != null && previousFailure != null && previousFailure != cleanupFailure) {
            cleanupFailure.addSuppressed(previousFailure);
        }
        return HostImageRenderOutcome.hostStateLost("cleanup", cleanupFailure, "raster-close-failed");
    }

    private static Throwable appendCloseFailure(Throwable firstFailure, Throwable nextFailure) {
        if (nextFailure == null) return firstFailure;
        if (firstFailure == null) return nextFailure;
        if (isFatal(nextFailure) && !isFatal(firstFailure)) {
            if (firstFailure != nextFailure) nextFailure.addSuppressed(firstFailure);
            return nextFailure;
        }
        if (firstFailure != nextFailure) firstFailure.addSuppressed(nextFailure);
        return firstFailure;
    }

    private static boolean isFatal(Throwable failure) {
        return failure instanceof Error && !(failure instanceof LinkageError);
    }

    /** identity key，equals 故意不委托 source。 */
    private static final class CacheKey {
        private final HostImageSource source;
        private final int rasterSide;
        private final int hash;

        private CacheKey(HostImageSource source, int rasterSide) {
            this.source = source;
            this.rasterSide = rasterSide;
            this.hash = 31 * System.identityHashCode(source) + rasterSide;
        }

        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof CacheKey)) return false;
            CacheKey that = (CacheKey) other;
            return source == that.source && rasterSide == that.rasterSide;
        }
    }

    private static final class CacheEntry {
        private final CachedRaster raster;
        private CacheEntry(CachedRaster raster) {
            this.raster = raster;
        }
    }
}
