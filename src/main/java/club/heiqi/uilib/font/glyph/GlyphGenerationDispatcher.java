package club.heiqi.uilib.font.glyph;

import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

import club.heiqi.uilib.font.FontRuntimeAccess;
import club.heiqi.uilib.font.FontRuntimeDiagnostics;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.page.GlyphState;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontMatcher;

/**
 * 单 worker、有界 admission 且支持 priority promotion/aging 的字形 demand 调度器。
 */
public class GlyphGenerationDispatcher {

    private static final int DEFAULT_MAX_DEMAND_COUNT = 1024;
    private static final int DEFAULT_VISIBLE_RESERVE = 256;
    private static final long DEFAULT_AGING_STEP_NANOS = TimeUnit.MILLISECONDS.toNanos(500L);

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean acceptingTasks = new AtomicBoolean(true);
    private final AtomicBoolean reloading = new AtomicBoolean(false);
    private final AtomicInteger generationEpoch = new AtomicInteger(0);
    private final AtomicInteger admittedDemandCount = new AtomicInteger(0);
    private final AtomicInteger demandHighWaterMark = new AtomicInteger(0);
    private final AtomicLong rejectedDemandCount = new AtomicLong(0L);
    private final AtomicLong promotedDemandCount = new AtomicLong(0L);
    private final AtomicLong enqueueSequence = new AtomicLong(0L);
    private final ConcurrentHashMap<Long, GlyphGenerationTask> inFlightTasks =
            new ConcurrentHashMap<Long, GlyphGenerationTask>();
    private final int maxDemandCount;
    private final int visibleReserve;
    private final long agingStepNanos;
    private final LongSupplier nanoTime;
    private volatile ExecutorService executorService;
    private volatile AgingDemandQueue demandQueue;
    private volatile Object ownerToken;
    private volatile int runtimeVersion;

    private FontMatcher fontMatcher;
    private GlyphPageManager glyphPageManager;
    private GlyphGenerator glyphGenerator;
    private GlyphGenerationResultHandler resultHandler;

    /** 创建未绑定 owner 的独立调度器。 */
    public GlyphGenerationDispatcher() {
        this(null);
    }

    /**
     * 创建绑定字体 singleton owner 的调度器。
     *
     * @param ownerToken 内部 owner token；独立测试对象可传 null
     */
    public GlyphGenerationDispatcher(Object ownerToken) {
        this(ownerToken, DEFAULT_MAX_DEMAND_COUNT, DEFAULT_VISIBLE_RESERVE, DEFAULT_AGING_STEP_NANOS,
                System::nanoTime);
    }

    GlyphGenerationDispatcher(int maxDemandCount, int visibleReserve, long agingStepNanos,
            LongSupplier nanoTime) {
        this(null, maxDemandCount, visibleReserve, agingStepNanos, nanoTime);
    }

    private GlyphGenerationDispatcher(Object ownerToken, int maxDemandCount, int visibleReserve,
            long agingStepNanos, LongSupplier nanoTime) {
        if (maxDemandCount <= 0 || visibleReserve < 0 || visibleReserve >= maxDemandCount) {
            throw new IllegalArgumentException("demand capacity/reserve 配置无效");
        }
        if (agingStepNanos <= 0L || nanoTime == null) {
            throw new IllegalArgumentException("agingStepNanos 和 nanoTime 必须有效");
        }
        this.ownerToken = ownerToken;
        this.maxDemandCount = maxDemandCount;
        this.visibleReserve = visibleReserve;
        this.agingStepNanos = agingStepNanos;
        this.nanoTime = nanoTime;
    }

    /** 绑定由 FontService 持有的内部 owner；仅允许首次绑定或同一 token 重复绑定。 */
    public synchronized void bindOwner(Object ownerToken) {
        if (ownerToken == null) {
            throw new IllegalArgumentException("ownerToken 不得为 null");
        }
        if (this.ownerToken != null && this.ownerToken != ownerToken) {
            throw new IllegalStateException("GlyphGenerationDispatcher 已绑定其他 runtime owner");
        }
        this.ownerToken = ownerToken;
    }

    /**
     * 初始化单 worker 调度器。
     *
     * @param fontMatcher 字体匹配器
     * @param glyphPageManager 字符页管理器
     * @param derivedFontCache 派生字体缓存
     * @param resultHandler 结果处理器
     */
    public synchronized void initialize(FontMatcher fontMatcher, GlyphPageManager glyphPageManager,
            DerivedFontCache derivedFontCache, GlyphGenerationResultHandler resultHandler) {
        assertRuntimeAccess();
        if (initialized.get() || executorService != null) {
            reset();
        }
        this.fontMatcher = fontMatcher;
        this.glyphPageManager = glyphPageManager;
        this.resultHandler = resultHandler;
        this.glyphGenerator = new GlyphGenerator(fontMatcher, derivedFontCache);
        generationEpoch.incrementAndGet();
        AgingDemandQueue queue = new AgingDemandQueue(nanoTime, agingStepNanos);
        demandQueue = queue;
        executorService = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue,
                new FontWorkerThreadFactory());
        initialized.set(true);
        reloading.set(false);
        acceptingTasks.set(true);
    }

    /** 设置当前运行时版本。 */
    public void setRuntimeVersion(int runtimeVersion) {
        assertRuntimeAccess();
        this.runtimeVersion = runtimeVersion;
    }

    /**
     * 提交 glyph demand。容量压力是正常控制流：拒绝发生在 manager claim 前，不创建悬挂 token。
     *
     * @param task 尚未领取 token 的 demand
     */
    public synchronized void submit(GlyphGenerationTask task) {
        assertRuntimeAccess();
        if (task == null) {
            return;
        }
        if (task.getToken() != null) {
            throw new IllegalArgumentException("dispatcher 只接受尚未领取 token 的 glyph demand");
        }
        if (!initialized.get() || !acceptingTasks.get() || reloading.get()
                || task.getRuntimeVersion() != runtimeVersion || glyphPageManager == null) {
            return;
        }

        GlyphDemandLevel requestedLevel = task.getDemandLevel();
        Long requestKey = Long.valueOf(packRequestKey(task.getRuntimeVersion(), task.getCodepoint(),
                task.getFontType()));
        GlyphGenerationTask existingTask = inFlightTasks.get(requestKey);
        if (existingTask != null) {
            GlyphRequestToken promotedToken = promoteActiveTask(existingTask, task, requestedLevel);
            if (promotedToken != null) {
                recordPromotion(promotedToken, requestedLevel, true, "ACTIVE_DEMAND");
                return;
            }
            if (glyphPageManager.hasActiveDemand(task.getRuntimeVersion(), task.getCodepoint(), task.getFontType())) {
                return;
            }
            removeInFlightTask(requestKey, existingTask);
        }

        GlyphRequestToken mailboxToken = glyphPageManager.promoteDemand(task.getRuntimeVersion(),
                task.getCodepoint(), task.getFontType(), requestedLevel.getPriorityOrder());
        if (mailboxToken != null) {
            recordPromotion(mailboxToken, requestedLevel, true, "UPLOAD_DEMAND");
            return;
        }
        if (glyphPageManager.hasActiveDemand(task.getRuntimeVersion(), task.getCodepoint(), task.getFontType())) {
            return;
        }

        if (!hasAdmissionCapacity(requestedLevel)) {
            int currentCount = admittedDemandCount.get();
            rejectedDemandCount.incrementAndGet();
            FontRuntimeDiagnostics.logGlyphCapacityEvent(null, task, "demand_admission", requestedLevel.name(),
                    currentCount, maxDemandCount, 0L, 0L, "CAPACITY_REJECTED");
            return;
        }

        final long enqueuedNanos = nanoTime.getAsLong();
        final long sequence = enqueueSequence.incrementAndGet();

        GlyphRequestToken token = glyphPageManager.claimRequest(task.getRuntimeVersion(), task.getCodepoint(),
                task.getFontType(), requestedLevel.getPriorityOrder());
        if (token == null) {
            return;
        }
        final GlyphGenerationTask generationTask;
        final ScheduledGlyphTask scheduledTask;
        try {
            generationTask = task.claimedBy(token);
            scheduledTask = new ScheduledGlyphTask(generationTask, generationEpoch.get(), requestKey, ownerToken,
                    sequence, enqueuedNanos);
        } catch (RuntimeException exception) {
            settleClaimFailure(token, "TASK_MATERIALIZATION_SETTLED", "TASK_MATERIALIZATION_STALE", exception);
            throw exception;
        } catch (Error error) {
            settleClaimFailure(token, "TASK_MATERIALIZATION_ERROR_SETTLED", "TASK_MATERIALIZATION_ERROR_STALE",
                    error);
            throw error;
        }
        try {
            inFlightTasks.put(requestKey, generationTask);
            int admitted = admittedDemandCount.incrementAndGet();
            updateHighWaterMark(admitted);
            ExecutorService currentExecutorService = executorService;
            if (currentExecutorService == null) {
                removeInFlightTask(requestKey, generationTask);
                cancelTask(generationTask);
                FontRuntimeDiagnostics.logGlyphTokenEvent(token, "dispatch", GlyphState.QUEUED,
                        glyphPageManager.getTokenState(token), "EXECUTOR_MISSING_CANCELLED");
                return;
            }
            currentExecutorService.execute(scheduledTask);
        } catch (RejectedExecutionException exception) {
            removeInFlightTask(requestKey, generationTask);
            cancelTask(generationTask);
            FontRuntimeDiagnostics.logGlyphTokenEvent(token, "dispatch", GlyphState.QUEUED,
                    glyphPageManager.getTokenState(token), "EXECUTOR_REJECTED_CANCELLED");
        } catch (RuntimeException exception) {
            settleDispatchFailure(requestKey, generationTask, token, exception);
            throw exception;
        } catch (Error error) {
            settleDispatchFailure(requestKey, generationTask, token, error);
            throw error;
        }
    }

    /** 停止接收新任务。 */
    public synchronized void pause() {
        assertRuntimeAccess();
        acceptingTasks.set(false);
        reloading.set(true);
    }

    /** 恢复接收新任务。 */
    public synchronized void resume() {
        assertRuntimeAccess();
        if (initialized.get()) {
            reloading.set(false);
            acceptingTasks.set(true);
        }
    }

    /**
     * 终止唯一 worker，并显式结算所有已接纳 demand。
     */
    public synchronized void reset() {
        assertRuntimeAccess();
        pause();
        generationEpoch.incrementAndGet();
        cancelInFlightTasks();
        ExecutorService stoppingExecutor = executorService;
        initialized.set(false);
        if (stoppingExecutor != null) {
            if (!stoppingExecutor.isShutdown()) {
                stoppingExecutor.shutdownNow();
                try {
                    if (!stoppingExecutor.awaitTermination(2L, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("字体生成线程池在 2 秒内未完全关停");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("等待字体生成线程池关停时被中断", exception);
                }
            } else if (!stoppingExecutor.isTerminated()) {
                throw new IllegalStateException("字体生成线程池仍未完全关停");
            }
            if (executorService == stoppingExecutor) {
                executorService = null;
                demandQueue = null;
            }
        } else {
            demandQueue = null;
        }
    }

    public boolean isReloading() {
        return reloading.get();
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public int getActiveDemandCount() {
        return admittedDemandCount.get();
    }

    public int getMaxDemandCount() {
        return maxDemandCount;
    }

    public int getDemandHighWaterMark() {
        return demandHighWaterMark.get();
    }

    public long getRejectedDemandCount() {
        return rejectedDemandCount.get();
    }

    public long getPromotedDemandCount() {
        return promotedDemandCount.get();
    }

    private boolean hasAdmissionCapacity(GlyphDemandLevel level) {
        int count = admittedDemandCount.get();
        int limit = level == GlyphDemandLevel.VISIBLE ? maxDemandCount : maxDemandCount - visibleReserve;
        return count < limit;
    }

    private GlyphRequestToken promoteActiveTask(GlyphGenerationTask existingTask, GlyphGenerationTask request,
            GlyphDemandLevel requestedLevel) {
        AgingDemandQueue queue = demandQueue;
        if (queue != null) {
            return queue.promote(existingTask, request, requestedLevel);
        }
        GlyphRequestToken token = glyphPageManager.promoteDemand(request.getRuntimeVersion(), request.getCodepoint(),
                request.getFontType(), requestedLevel.getPriorityOrder());
        if (token != null) {
            existingTask.promoteTo(requestedLevel);
        }
        return token;
    }

    private void recordPromotion(GlyphRequestToken token, GlyphDemandLevel level, boolean promoted, String reason) {
        if (!promoted) {
            return;
        }
        promotedDemandCount.incrementAndGet();
        FontRuntimeDiagnostics.logGlyphCapacityEvent(token, null, "demand_promotion", level.name(),
                admittedDemandCount.get(), maxDemandCount, 0L, 0L, reason);
    }

    private void updateHighWaterMark(int current) {
        while (true) {
            int previous = demandHighWaterMark.get();
            if (current <= previous || demandHighWaterMark.compareAndSet(previous, current)) {
                return;
            }
        }
    }

    private void settleClaimFailure(GlyphRequestToken token, String settledReason, String staleReason,
            Throwable throwable) {
        GlyphState actualState = glyphPageManager.getTokenState(token);
        boolean settled = settleFailed(token);
        FontRuntimeDiagnostics.logGlyphPipelineFailure(token, "claim", GlyphState.QUEUED, actualState,
                settled ? settledReason : staleReason, throwable);
    }

    private void settleDispatchFailure(Long requestKey, GlyphGenerationTask generationTask, GlyphRequestToken token,
            Throwable throwable) {
        GlyphState actualState = glyphPageManager.getTokenState(token);
        boolean settled = settleFailed(token);
        removeInFlightTask(requestKey, generationTask);
        FontRuntimeDiagnostics.logGlyphPipelineFailure(token, "dispatch", GlyphState.QUEUED, actualState,
                settled ? "SUBMIT_EXCEPTION_SETTLED" : "SUBMIT_EXCEPTION_STALE", throwable);
    }

    private boolean isTaskCurrent(int taskGenerationEpoch) {
        return generationEpoch.get() == taskGenerationEpoch;
    }

    private void executeGenerationTask(GlyphGenerationTask task, int taskGenerationEpoch) {
        GlyphRequestToken token = task.getToken();
        String stage = "worker_gate";
        try {
            if (!isTaskCurrent(taskGenerationEpoch) || fontMatcher == null || glyphGenerator == null
                    || task.getRuntimeVersion() != runtimeVersion) {
                cancelTask(task);
                return;
            }
            if (!glyphPageManager.markRasterizing(token)) {
                FontRuntimeDiagnostics.logGlyphTokenRejection(token, stage, GlyphState.QUEUED,
                        glyphPageManager.getTokenState(token), "RASTERIZE_CLAIM_REJECTED");
                return;
            }
            if (!isTaskCurrent(taskGenerationEpoch) || task.getRuntimeVersion() != runtimeVersion) {
                cancelTask(task);
                return;
            }

            stage = "matcher";
            FontType fontType = task.getFontType();
            if (fontMatcher.matchFontIndex(task.getRuntimeVersion(), task.getCodepoint(), fontType) < 0) {
                GlyphState actualState = glyphPageManager.getTokenState(token);
                boolean settled = glyphPageManager.markFailed(token, GlyphState.RASTERIZING);
                FontRuntimeDiagnostics.logGlyphTokenEvent(token, stage, GlyphState.RASTERIZING, actualState,
                        settled ? "NO_MATCHING_FONT" : "NO_MATCHING_FONT_STALE");
                return;
            }

            stage = "rasterize";
            GlyphGenerationResult result = glyphGenerator.generate(task);
            if (!isTaskCurrent(taskGenerationEpoch) || task.getRuntimeVersion() != runtimeVersion) {
                cancelTask(task);
                return;
            }
            if (result == null) {
                GlyphState actualState = glyphPageManager.getTokenState(token);
                boolean settled = glyphPageManager.markFailed(token, GlyphState.RASTERIZING);
                FontRuntimeDiagnostics.logGlyphTokenEvent(token, stage, GlyphState.RASTERIZING, actualState,
                        settled ? "RASTERIZER_RETURNED_NULL" : "RASTERIZER_NULL_STALE");
                return;
            }

            stage = "result_handler";
            if (resultHandler == null) {
                throw new IllegalStateException("glyph result handler 未初始化");
            }
            if (!resultHandler.handle(result)) {
                GlyphState actualState = glyphPageManager.getTokenState(token);
                boolean settled = glyphPageManager.markFailed(token, GlyphState.RASTERIZING);
                FontRuntimeDiagnostics.logGlyphTokenEvent(token, stage, GlyphState.RASTERIZING, actualState,
                        settled ? "RESULT_REJECTED_SETTLED" : "RESULT_REJECTED_STALE");
            }
        } catch (RuntimeException exception) {
            settleWorkerFailure(token, stage, exception);
        } catch (Error error) {
            settleWorkerFailure(token, stage, error);
            throw error;
        }
    }

    private void settleWorkerFailure(GlyphRequestToken token, String stage, Throwable throwable) {
        GlyphState actualState = glyphPageManager == null ? null : glyphPageManager.getTokenState(token);
        boolean settled = settleFailed(token);
        FontRuntimeDiagnostics.logGlyphPipelineFailure(token, stage, GlyphState.RASTERIZING, actualState,
                settled ? "WORKER_EXCEPTION_SETTLED" : "WORKER_EXCEPTION_STALE", throwable);
    }

    private boolean settleFailed(GlyphRequestToken token) {
        GlyphPageManager manager = glyphPageManager;
        return manager != null && (manager.markFailed(token, GlyphState.QUEUED)
                || manager.markFailed(token, GlyphState.RASTERIZING)
                || manager.markFailed(token, GlyphState.UPLOAD_QUEUED)
                || manager.markFailed(token, GlyphState.UPLOADING));
    }

    private void cancelInFlightTasks() {
        GlyphGenerationTask[] tasks = inFlightTasks.values().toArray(new GlyphGenerationTask[0]);
        for (GlyphGenerationTask task : tasks) {
            GlyphRequestToken token = task.getToken();
            Long requestKey = Long.valueOf(packRequestKey(token.getGeneration(), token.getCodepoint(),
                    token.getFontType()));
            removeInFlightTask(requestKey, task);
            cancelTask(task);
        }
    }

    private void removeInFlightTask(Long requestKey, GlyphGenerationTask task) {
        if (inFlightTasks.remove(requestKey, task)) {
            admittedDemandCount.decrementAndGet();
        }
    }

    private void cancelTask(GlyphGenerationTask task) {
        GlyphPageManager manager = glyphPageManager;
        GlyphRequestToken token = task == null ? null : task.getToken();
        if (manager != null && token != null) {
            manager.markCancelled(token, GlyphState.QUEUED);
            manager.markCancelled(token, GlyphState.RASTERIZING);
            manager.markCancelled(token, GlyphState.UPLOAD_QUEUED);
            manager.markCancelled(token, GlyphState.UPLOADING);
        }
    }

    int getInFlightTaskCount() {
        return inFlightTasks.size();
    }

    private void assertRuntimeAccess() {
        if (!FontRuntimeAccess.isActive(ownerToken)) {
            throw new IllegalStateException("GlyphGenerationDispatcher 只能由字体 runtime owner 修改");
        }
    }

    private long packRequestKey(int generation, int codepoint, FontType fontType) {
        long versionBits = ((long) generation & 0xFFFFFFFFL) << 32;
        long codepointBits = ((long) codepoint & 0x1FFFFFL) << 1;
        long typeBit = fontType == FontType.BOLD ? 1L : 0L;
        return versionBits | codepointBits | typeBit;
    }

    private final class ScheduledGlyphTask implements Runnable {

        private final GlyphGenerationTask task;
        private final int taskGenerationEpoch;
        private final Long requestKey;
        private final Object taskOwnerToken;
        private final long sequence;
        private final long enqueuedNanos;

        private ScheduledGlyphTask(GlyphGenerationTask task, int taskGenerationEpoch, Long requestKey,
                Object taskOwnerToken, long sequence, long enqueuedNanos) {
            this.task = task;
            this.taskGenerationEpoch = taskGenerationEpoch;
            this.requestKey = requestKey;
            this.taskOwnerToken = taskOwnerToken;
            this.sequence = sequence;
            this.enqueuedNanos = enqueuedNanos;
        }

        @Override
        public void run() {
            try {
                FontRuntimeAccess.run(taskOwnerToken, () -> executeGenerationTask(task, taskGenerationEpoch));
            } finally {
                removeInFlightTask(requestKey, task);
            }
        }

        private int effectivePriority(long nowNanos, long stepNanos) {
            int priority = task.getDemandLevel().getPriorityOrder();
            long elapsed = Math.max(0L, nowNanos - enqueuedNanos);
            long agingSteps = elapsed / stepNanos;
            return (int) Math.min((long) GlyphDemandLevel.VISIBLE.getPriorityOrder(), priority + agingSteps);
        }
    }

    /** 每次 dequeue 都按当前 priority 与当前时钟重选，避免动态 comparator 的陈旧堆序。 */
    private final class AgingDemandQueue extends AbstractQueue<Runnable> implements BlockingQueue<Runnable> {

        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notEmpty = lock.newCondition();
        private final List<Runnable> tasks = new ArrayList<Runnable>();
        private final LongSupplier clock;
        private final long stepNanos;

        private AgingDemandQueue(LongSupplier clock, long stepNanos) {
            this.clock = clock;
            this.stepNanos = stepNanos;
        }

        @Override
        public boolean offer(Runnable runnable) {
            if (runnable == null) {
                throw new NullPointerException("runnable");
            }
            lock.lock();
            try {
                tasks.add(runnable);
                notEmpty.signal();
                return true;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void put(Runnable runnable) {
            offer(runnable);
        }

        @Override
        public boolean offer(Runnable runnable, long timeout, TimeUnit unit) {
            return offer(runnable);
        }

        @Override
        public Runnable take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (tasks.isEmpty()) {
                    notEmpty.await();
                }
                return removeBest();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
            long remaining = unit.toNanos(timeout);
            lock.lockInterruptibly();
            try {
                while (tasks.isEmpty()) {
                    if (remaining <= 0L) {
                        return null;
                    }
                    remaining = notEmpty.awaitNanos(remaining);
                }
                return removeBest();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public int remainingCapacity() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int drainTo(Collection<? super Runnable> target) {
            return drainTo(target, Integer.MAX_VALUE);
        }

        @Override
        public int drainTo(Collection<? super Runnable> target, int maxElements) {
            if (target == null || target == this) {
                throw new IllegalArgumentException("target 无效");
            }
            if (maxElements <= 0) {
                return 0;
            }
            lock.lock();
            try {
                int count = Math.min(maxElements, tasks.size());
                for (int index = 0; index < count; index++) {
                    target.add(removeBest());
                }
                return count;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public Runnable poll() {
            lock.lock();
            try {
                return tasks.isEmpty() ? null : removeBest();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public Runnable peek() {
            lock.lock();
            try {
                int index = bestIndex();
                return index < 0 ? null : tasks.get(index);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public Iterator<Runnable> iterator() {
            final Iterator<Runnable> snapshot;
            lock.lock();
            try {
                snapshot = new ArrayList<Runnable>(tasks).iterator();
            } finally {
                lock.unlock();
            }
            return new Iterator<Runnable>() {

                private Runnable current;
                private boolean removable;

                @Override
                public boolean hasNext() {
                    return snapshot.hasNext();
                }

                @Override
                public Runnable next() {
                    current = snapshot.next();
                    removable = true;
                    return current;
                }

                @Override
                public void remove() {
                    if (!removable) {
                        throw new IllegalStateException("iterator 当前没有可删除元素");
                    }
                    AgingDemandQueue.this.remove(current);
                    removable = false;
                }
            };
        }

        @Override
        public int size() {
            lock.lock();
            try {
                return tasks.size();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean remove(Object target) {
            lock.lock();
            try {
                return tasks.remove(target);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void clear() {
            lock.lock();
            try {
                tasks.clear();
            } finally {
                lock.unlock();
            }
        }

        private Runnable removeBest() {
            return tasks.remove(bestIndex());
        }

        private int bestIndex() {
            if (tasks.isEmpty()) {
                return -1;
            }
            long now = clock.getAsLong();
            int bestIndex = 0;
            ScheduledGlyphTask best = (ScheduledGlyphTask) tasks.get(0);
            int bestPriority = best.effectivePriority(now, stepNanos);
            for (int index = 1; index < tasks.size(); index++) {
                ScheduledGlyphTask candidate = (ScheduledGlyphTask) tasks.get(index);
                int candidatePriority = candidate.effectivePriority(now, stepNanos);
                if (candidatePriority > bestPriority
                        || candidatePriority == bestPriority && candidate.sequence < best.sequence) {
                    bestIndex = index;
                    best = candidate;
                    bestPriority = candidatePriority;
                }
            }
            return bestIndex;
        }

        private GlyphRequestToken promote(GlyphGenerationTask existingTask, GlyphGenerationTask request,
                GlyphDemandLevel requestedLevel) {
            lock.lock();
            try {
                GlyphRequestToken token = glyphPageManager.promoteDemand(request.getRuntimeVersion(),
                        request.getCodepoint(), request.getFontType(), requestedLevel.getPriorityOrder());
                if (token != null) {
                    existingTask.promoteTo(requestedLevel);
                }
                return token;
            } finally {
                lock.unlock();
            }
        }
    }

    /** 字体生成线程工厂。 */
    private static final class FontWorkerThreadFactory implements ThreadFactory {

        private final AtomicInteger index = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "QzFontWorker-" + index.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
