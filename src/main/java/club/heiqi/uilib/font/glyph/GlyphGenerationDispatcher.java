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
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontConfig;
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
    private ExecutorService executorService;
    private volatile int runtimeVersion;

    private FontMatcher fontMatcher;
    private GlyphPageManager glyphPageManager;
    private GlyphGenerator glyphGenerator;
    private GlyphGenerationResultHandler resultHandler;

    /**
     * 初始化调度器。
     *
     * <p>若调度器已初始化，会先走完 {@link #reset()} 的关停流程（含 awaitTermination 与代际隔离），
     * 再用新的协作对象重新建池，避免旧任务继续访问已被替换的 {@link GlyphPageManager}。</p>
     *
     * @param fontMatcher 字体匹配器
     * @param glyphPageManager 字符页管理器
     * @param derivedFontCache 派生字体缓存
     * @param resultHandler 结果处理器
     */
    public void initialize(
            FontMatcher fontMatcher,
            GlyphPageManager glyphPageManager,
            DerivedFontCache derivedFontCache,
            GlyphGenerationResultHandler resultHandler) {
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
        this.runtimeVersion = runtimeVersion;
    }

    /**
     * 提交字符生成任务。
     *
     * @param task 生成任务
     */
    public void submit(GlyphGenerationTask task) {
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
        try {
            currentExecutorService.submit(() -> {
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

                    if (club.heiqi.uilib.Config.fontRuntimeDebug) {
                        MyMod.LOG.info("已接收字符生成任务 codepoint={} size={} priority={} awtCharSize={}",
                                generationTask.getCodepoint(),
                                generationTask.getGlyphSize(),
                                generationTask.getPriority(),
                                FontConfig.awtCharSize);
                    }
                } finally {
                    inFlightTasks.remove(requestKey);
                }
            });
        } catch (RejectedExecutionException exception) {
            inFlightTasks.remove(requestKey);
            cancelTask(generationTask);
        }
    }

    /**
     * 停止接收新任务。
     */
    public void pause() {
        acceptingTasks.set(false);
        reloading.set(true);
    }

    /**
     * 恢复接收新任务。
     */
    public void resume() {
        if (initialized.get()) {
            reloading.set(false);
            acceptingTasks.set(true);
        }
    }

    /**
     * 清理调度状态。
     *
     * <p>会在终止线程池后短暂等待残留任务退出，避免代际隔离尚未完成时旧任务继续访问 {@link GlyphPageManager}。</p>
     */
    public void reset() {
        pause();
        generationEpoch.incrementAndGet();
        cancelInFlightTasks();
        if (executorService != null) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(2L, TimeUnit.SECONDS)) {
                    MyMod.LOG.warn("字体生成线程池在 2 秒内未完全关停，继续推进重载流程");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            executorService = null;
        }
        initialized.set(false);
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
