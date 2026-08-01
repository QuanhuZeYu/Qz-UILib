package club.heiqi.uilib.font.glyph;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.FontRuntimeAccess;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontMatcher;

/**
 * 字符生成任务调度器骨架。
 */
public class GlyphGenerationDispatcher {

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean acceptingTasks = new AtomicBoolean(true);
    private final AtomicBoolean reloading = new AtomicBoolean(false);
    private final AtomicInteger generationEpoch = new AtomicInteger(0);
    private final ConcurrentHashMap<Long, GlyphGenerationTask> inFlightTasks =
            new ConcurrentHashMap<Long, GlyphGenerationTask>();
    private volatile ExecutorService executorService;
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
        this.ownerToken = ownerToken;
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
     * 初始化调度器。
     *
     * <p>若调度器已初始化，会先走完 {@link #reset()} 的关停流程（含 awaitTermination 与代际隔离），
     * 再用新的 generation 协作对象重新建池；旧任务的最终 table 写入由 version/lifecycle gate 拒绝。</p>
     *
     * @param fontMatcher 字体匹配器
     * @param glyphPageManager 字符页管理器
     * @param derivedFontCache 派生字体缓存
     * @param resultHandler 结果处理器
     */
    public synchronized void initialize(
            FontMatcher fontMatcher,
            GlyphPageManager glyphPageManager,
            DerivedFontCache derivedFontCache,
            GlyphGenerationResultHandler resultHandler) {
        assertRuntimeAccess();
        if (initialized.get() || executorService != null) {
            reset();
        }
        this.fontMatcher = fontMatcher;
        this.glyphPageManager = glyphPageManager;
        this.resultHandler = resultHandler;
        this.glyphGenerator = new GlyphGenerator(fontMatcher, derivedFontCache);
        generationEpoch.incrementAndGet();
        executorService = new ThreadPoolExecutor(
                1,
                2,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(),
                new FontWorkerThreadFactory());
        initialized.compareAndSet(false, true);
        reloading.set(false);
        acceptingTasks.set(true);
    }

    /**
     * 设置当前运行时版本。
     *
     * @param runtimeVersion 运行时版本
     */
    public void setRuntimeVersion(int runtimeVersion) {
        assertRuntimeAccess();
        this.runtimeVersion = runtimeVersion;
    }

    /**
     * 提交字符生成任务。
     *
     * @param task 生成任务
     */
    public void submit(GlyphGenerationTask task) {
        assertRuntimeAccess();
        if (!initialized.get() || !acceptingTasks.get() || reloading.get()) {
            return;
        }
        if (task.getRuntimeVersion() != runtimeVersion) {
            return;
        }
        if (glyphPageManager == null || !glyphPageManager.tryMarkGenerating(
                task.getRuntimeVersion(), task.getCodepoint(), task.getFontType())) {
            return;
        }

        task.assignGenerationId(glyphPageManager.getGenerationId(task.getRuntimeVersion(), task.getCodepoint(),
                task.getFontType()));
        final GlyphGenerationTask generationTask = task;
        final int taskGenerationEpoch = generationEpoch.get();
        final int taskRuntimeVersion = generationTask.getRuntimeVersion();
        final Long requestKey = Long.valueOf(packRequestKey(taskRuntimeVersion, generationTask.getCodepoint(),
                generationTask.getFontType()));
        inFlightTasks.put(requestKey, generationTask);
        ExecutorService currentExecutorService = executorService;
        if (currentExecutorService == null) {
            inFlightTasks.remove(requestKey);
            cancelTask(generationTask);
            return;
        }
        final Object taskOwnerToken = ownerToken;
        try {
            currentExecutorService.submit(() -> FontRuntimeAccess.run(taskOwnerToken, () -> {
                try {
                    if (!isTaskCurrent(taskGenerationEpoch) || fontMatcher == null
                            || taskRuntimeVersion != runtimeVersion) {
                        cancelTask(generationTask);
                        return;
                    }

                    FontType fontType = generationTask.getFontType();
                    if (fontMatcher.matchFontIndex(taskRuntimeVersion, generationTask.getCodepoint(), fontType) < 0) {
                        if (!isTaskCurrent(taskGenerationEpoch) || taskRuntimeVersion != runtimeVersion) {
                            cancelTask(generationTask);
                            return;
                        }
                        glyphPageManager.markFailed(taskRuntimeVersion, generationTask.getCodepoint(), fontType);
                        MyMod.LOG.warn("未找到可显示字符的字体，codepoint={} type={}",
                                generationTask.getCodepoint(), fontType);
                        return;
                    }

                    GlyphGenerationResult result = glyphGenerator.generate(generationTask);
                    if (!isTaskCurrent(taskGenerationEpoch) || taskRuntimeVersion != runtimeVersion) {
                        cancelTask(generationTask);
                        return;
                    }
                    if (result == null) {
                        glyphPageManager.markFailed(taskRuntimeVersion, generationTask.getCodepoint(), fontType);
                        return;
                    }
                    if (resultHandler != null) {
                        resultHandler.handle(result);
                    }

                } finally {
                    inFlightTasks.remove(requestKey);
                }
            }));
        } catch (RejectedExecutionException exception) {
            inFlightTasks.remove(requestKey);
            cancelTask(generationTask);
        }
    }

    /**
     * 停止接收新任务。
     */
    public void pause() {
        assertRuntimeAccess();
        acceptingTasks.set(false);
        reloading.set(true);
    }

    /**
     * 恢复接收新任务。
     */
    public void resume() {
        assertRuntimeAccess();
        if (initialized.get()) {
            reloading.set(false);
            acceptingTasks.set(true);
        }
    }

    /**
     * 清理调度状态。
     *
     * <p>会在终止线程池后短暂等待残留任务退出。等待超时或被中断时显式失败，调用方不得继续转移当前 generation
     * 的唯一 table storage。</p>
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
            }
        }
    }

    /**
     * 判断生成链路是否处于重载屏障中。
     *
     * @return 是否正在重载
     */
    public boolean isReloading() {
        return reloading.get();
    }

    /**
     * 判断当前是否已初始化。
     *
     * @return 是否已初始化
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    private boolean isTaskCurrent(int taskGenerationEpoch) {
        return acceptingTasks.get() && generationEpoch.get() == taskGenerationEpoch;
    }

    private void cancelInFlightTasks() {
        GlyphGenerationTask[] tasks = inFlightTasks.values().toArray(new GlyphGenerationTask[0]);
        inFlightTasks.clear();
        for (GlyphGenerationTask task : tasks) {
            cancelTask(task);
        }
    }

    private void cancelTask(GlyphGenerationTask task) {
        GlyphPageManager manager = glyphPageManager;
        if (manager != null) {
            manager.markGenerationCancelled(task.getRuntimeVersion(), task.getCodepoint(), task.getFontType());
        }
    }

    private void assertRuntimeAccess() {
        if (!FontRuntimeAccess.isActive(ownerToken)) {
            throw new IllegalStateException("GlyphGenerationDispatcher 只能由字体 runtime owner 修改");
        }
    }

    private long packRequestKey(int runtimeVersion, int codepoint, FontType fontType) {
        long versionBits = ((long) runtimeVersion & 0xFFFFFFFFL) << 32;
        long codepointBits = ((long) codepoint & 0x1FFFFFL) << 1;
        long typeBit = fontType == FontType.BOLD ? 1L : 0L;
        return versionBits | codepointBits | typeBit;
    }

    /**
     * 字体生成线程工厂。
     */
    private static class FontWorkerThreadFactory implements ThreadFactory {

        private final AtomicInteger index = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "QzFontWorker-" + index.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
