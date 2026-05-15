package club.heiqi.uilib.font.glyph;

import java.awt.image.BufferedImage;
import java.util.List;
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
import club.heiqi.uilib.font.util.FontMatcher;

/**
 * 字符生成任务调度器骨架。
 */
public class GlyphGenerationDispatcher {

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean acceptingTasks = new AtomicBoolean(true);
    private final AtomicInteger generationEpoch = new AtomicInteger(0);
    private final ConcurrentHashMap<GlyphGenerationRequestKey, GlyphGenerationTask> inFlightTasks =
            new ConcurrentHashMap<GlyphGenerationRequestKey, GlyphGenerationTask>();
    private ExecutorService executorService;
    private volatile int runtimeVersion;

    private FontMatcher fontMatcher;
    private GlyphPageManager glyphPageManager;
    private GlyphGenerator glyphGenerator;
    private GlyphGenerationResultHandler resultHandler;

    /**
     * 初始化调度器。
     *
     * @param fontMatcher 字体匹配器
     * @param glyphPageManager 字符页管理器
     * @param resultHandler 结果处理器
     */
    public void initialize(
            FontMatcher fontMatcher,
            GlyphPageManager glyphPageManager,
            GlyphGenerationResultHandler resultHandler) {
        this.fontMatcher = fontMatcher;
        this.glyphPageManager = glyphPageManager;
        this.resultHandler = resultHandler;
        this.glyphGenerator = new GlyphGenerator(fontMatcher);
        generationEpoch.incrementAndGet();
        if (executorService == null || executorService.isShutdown()) {
            executorService = new ThreadPoolExecutor(
                    1,
                    2,
                    60L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>(),
                    new FontWorkerThreadFactory());
        }
        initialized.compareAndSet(false, true);
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
        if (!initialized.get() || !acceptingTasks.get()) {
            return;
        }
        if (task.getRuntimeVersion() != runtimeVersion) {
            return;
        }
        if (glyphPageManager == null || !glyphPageManager.tryMarkGenerating(
                task.getRuntimeVersion(), task.getCodepoint(), task.getFontType())) {
            return;
        }

        final long taskGenerationId = glyphPageManager.getGenerationId(task.getRuntimeVersion(), task.getCodepoint(),
                task.getFontType());
        final GlyphGenerationTask generationTask = task.withGenerationId(taskGenerationId);
        final int taskGenerationEpoch = generationEpoch.get();
        final int taskRuntimeVersion = generationTask.getRuntimeVersion();
        final GlyphGenerationRequestKey requestKey = new GlyphGenerationRequestKey(taskRuntimeVersion,
                generationTask.getCodepoint(), generationTask.getFontType());
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
                    if (!isTaskCurrent(taskGenerationEpoch) || fontMatcher == null || taskRuntimeVersion != runtimeVersion) {
                        cancelTask(generationTask);
                        return;
                    }

                    FontType fontType = generationTask.getFontType();
                    if (fontMatcher.match(taskRuntimeVersion, generationTask.getCodepoint(), fontType) == null) {
                        if (!isTaskCurrent(taskGenerationEpoch) || taskRuntimeVersion != runtimeVersion) {
                            cancelTask(generationTask);
                            return;
                        }
                        glyphPageManager.markFailed(taskRuntimeVersion, generationTask.getCodepoint(), fontType);
                        MyMod.LOG.warn("未找到可显示字符的字体，codepoint={} type={}", generationTask.getCodepoint(), fontType);
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

                    MyMod.LOG.debug("已接收字符生成任务 codepoint={} size={} priority={} awtCharSize={}",
                            generationTask.getCodepoint(),
                            generationTask.getGlyphSize(),
                            generationTask.getPriority(),
                            FontConfig.awtCharSize);
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
    }

    /**
     * 恢复接收新任务。
     */
    public void resume() {
        if (initialized.get()) {
            acceptingTasks.set(true);
        }
    }

    /**
     * 清理调度状态。
     */
    public void reset() {
        pause();
        generationEpoch.incrementAndGet();
        cancelInFlightTasks();
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        initialized.set(false);
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

    /**
     * 获取当前仍在飞行中的字符任务快照。
     *
     * @return 字符任务快照
     */
    public List<GlyphGenerationTask> snapshotInFlightTasks() {
        return new java.util.ArrayList<GlyphGenerationTask>(inFlightTasks.values());
    }

    private void cancelInFlightTasks() {
        List<GlyphGenerationTask> tasks = snapshotInFlightTasks();
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

    /**
     * 生成请求唯一键。
     */
    private static class GlyphGenerationRequestKey {

        private final int runtimeVersion;
        private final int codepoint;
        private final FontType fontType;

        private GlyphGenerationRequestKey(int runtimeVersion, int codepoint, FontType fontType) {
            this.runtimeVersion = runtimeVersion;
            this.codepoint = codepoint;
            this.fontType = fontType;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof GlyphGenerationRequestKey)) {
                return false;
            }
            GlyphGenerationRequestKey other = (GlyphGenerationRequestKey) obj;
            return runtimeVersion == other.runtimeVersion
                    && codepoint == other.codepoint
                    && fontType == other.fontType;
        }

        @Override
        public int hashCode() {
            int result = Integer.valueOf(runtimeVersion).hashCode();
            result = 31 * result + Integer.valueOf(codepoint).hashCode();
            result = 31 * result + fontType.hashCode();
            return result;
        }
    }

    /**
     * 字体生成线程工厂。
     */
    private static class FontWorkerThreadFactory implements ThreadFactory {

        private int index = 0;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "QzFontWorker-" + index++);
            thread.setDaemon(true);
            return thread;
        }
    }
}
