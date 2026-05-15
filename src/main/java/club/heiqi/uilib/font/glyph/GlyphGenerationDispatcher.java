package club.heiqi.uilib.font.glyph;

import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
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
    private ExecutorService executorService;

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
     * 提交字符生成任务。
     *
     * @param task 生成任务
     */
    public void submit(GlyphGenerationTask task) {
        if (!initialized.get() || !acceptingTasks.get()) {
            return;
        }
        if (glyphPageManager == null || !glyphPageManager.tryMarkGenerating(task.getCodepoint(), task.getFontType())) {
            return;
        }

        final int taskGenerationEpoch = generationEpoch.get();
        executorService.submit(() -> {
            if (!isTaskCurrent(taskGenerationEpoch) || fontMatcher == null) {
                return;
            }

            FontType fontType = task.getFontType();
            if (fontMatcher.match(task.getCodepoint(), fontType) == null) {
                if (!isTaskCurrent(taskGenerationEpoch)) {
                    return;
                }
                glyphPageManager.markFailed(task.getCodepoint(), fontType);
                MyMod.LOG.warn("未找到可显示字符的字体，codepoint={} type={}", task.getCodepoint(), fontType);
                return;
            }

            GlyphGenerationResult result = glyphGenerator.generate(task);
            if (!isTaskCurrent(taskGenerationEpoch)) {
                return;
            }
            if (result == null) {
                glyphPageManager.markFailed(task.getCodepoint(), fontType);
                return;
            }
            if (resultHandler != null) {
                resultHandler.handle(result);
            }

            MyMod.LOG.debug("已接收字符生成任务 codepoint={} size={} priority={} awtCharSize={}",
                    task.getCodepoint(),
                    task.getGlyphSize(),
                    task.getPriority(),
                    FontConfig.awtCharSize);
        });
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
