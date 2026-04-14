package club.heiqi.uilib.font;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.event.FontReloadRequest;
import club.heiqi.uilib.font.glyph.GlyphGenerationDispatcher;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.render.FontBatchRenderer;
import club.heiqi.uilib.font.render.TextDecorationRenderer;
import club.heiqi.uilib.font.shader.FontShaderProgram;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;
import club.heiqi.uilib.font.util.FontRegistry;
import club.heiqi.uilib.ui.widget.UiLayoutInvalidationRegistry;

/**
 * 字体系统总入口。
 */
public class FontService {

    private static final FontService INSTANCE = new FontService();

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean layoutRuntimeReady = new AtomicBoolean(false);
    private final FontCatalog fontCatalog = new FontCatalog();
    private final FontRegistry fontRegistry = new FontRegistry(fontCatalog);
    private final FontMatcher fontMatcher = new FontMatcher(fontCatalog);
    private final GlyphPageManager glyphPageManager = new GlyphPageManager();
    private final GlyphGenerationDispatcher glyphGenerationDispatcher = new GlyphGenerationDispatcher();
    private final TextLayoutService textLayoutService = new TextLayoutService(fontMatcher, glyphPageManager);
    private final FontBatchRenderer batchRenderer = new FontBatchRenderer();
    private final TextDecorationRenderer decorationRenderer = new TextDecorationRenderer();
    private final FontShaderProgram shaderProgram = new FontShaderProgram();
    private final Deque<Long> drawStageUploadTimestamps = new ArrayDeque<Long>();

    private long lastDrawStageUploadAt = 0L;
    private int runtimeVersion;
    private int textMeasureEpoch;

    private FontService() {}

    /**
     * 获取字体系统单例。
     *
     * @return 字体系统实例
     */
    public static FontService getInstance() {
        return INSTANCE;
    }

    /**
     * 确保布局期文本测量所需的轻量运行时已就绪。
     *
     * <p>该入口只准备字体注册、匹配缓存与布局缓存，不触碰字符页、调度器、批渲染器或着色器等重型渲染运行时。</p>
     */
    public void ensureLayoutRuntimeReady() {
        if (layoutRuntimeReady.get()) {
            return;
        }

        synchronized (this) {
            if (layoutRuntimeReady.get()) {
                return;
            }

            refreshTextMeasureRuntime();
            MyMod.LOG.info("字体布局测量运行时初始化完成：{}", FontConfig.buildSummary());
        }
    }

    /**
     * 初始化字体系统基础骨架。
     */
    public void initialize() {
        if (initialized.get()) {
            return;
        }

        synchronized (this) {
            if (initialized.get()) {
                return;
            }

            ensureLayoutRuntimeReady();
            glyphPageManager.initialize();
            glyphGenerationDispatcher.initialize(fontMatcher, glyphPageManager, glyphPageManager::queueUpload);
            initialized.set(true);
            runtimeVersion++;
        }

        MyMod.LOG.info("字体系统骨架初始化完成：{}", FontConfig.buildSummary());
    }

    /**
     * 重新加载字体系统基础状态。
     *
     * @param request 重载请求
     */
    public void reload(FontReloadRequest request) {
        synchronized (this) {
            if (!initialized.get()) {
                initialize();
            }

            refreshTextMeasureRuntime();
            glyphPageManager.reset();
            glyphGenerationDispatcher.initialize(fontMatcher, glyphPageManager, glyphPageManager::queueUpload);
            batchRenderer.clearFrame();
            decorationRenderer.clear();
            shaderProgram.close();
            runtimeVersion++;
        }
        int invalidatedRootCount = UiLayoutInvalidationRegistry.invalidateAll();

        MyMod.LOG.info("字体系统重载完成，原因：{}，布局树已失效：{}，运行时版本：{}", request.getReason(),
                Integer.valueOf(invalidatedRootCount), Integer.valueOf(runtimeVersion));
    }

    /**
     * 刷新字体系统主线程状态。
     *
     * @param maxUploadCount 本次最多处理的待上传数量
     */
    public void tickMainThread(int maxUploadCount) {
        if (!initialized.get()) {
            return;
        }

        glyphPageManager.flushPendingUploads(maxUploadCount);
        debugLogStats("render_tick");
    }

    /**
     * 在 drawString 阶段尝试限速执行字符页上传。
     *
     * @param maxUploadCount 本次最多处理的待上传数量
     */
    public void tickDrawStage(int maxUploadCount) {
        if (!initialized.get() || maxUploadCount <= 0) {
            return;
        }
        if (!canRunDrawStageUpload()) {
            return;
        }

        glyphPageManager.flushPendingUploads(maxUploadCount);
        long now = System.currentTimeMillis();
        lastDrawStageUploadAt = now;
        drawStageUploadTimestamps.addLast(Long.valueOf(now));
        debugLogStats("draw_stage");
    }

    /**
     * 判断字体系统是否已初始化。
     *
     * @return 是否已初始化
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * 获取字体运行时版本号。
     *
     * @return 当前字体运行时版本号
     */
    public int getRuntimeVersion() {
        return runtimeVersion;
    }

    /**
     * 获取文本测量缓存失效纪元。
     *
     * <p>该纪元在布局期轻量初始化与完整运行时重载共享：只要字体注册、匹配缓存或文本布局缓存基础发生变化，就会递增。</p>
     *
     * @return 文本测量缓存失效纪元
     */
    public int getTextMeasureEpoch() {
        ensureLayoutRuntimeReady();
        return textMeasureEpoch;
    }

    /**
     * 获取字符页管理器。
     *
     * @return 字符页管理器
     */
    public GlyphPageManager getGlyphPageManager() {
        return glyphPageManager;
    }

    /**
     * 获取字体匹配器。
     *
     * @return 字体匹配器
     */
    public FontMatcher getFontMatcher() {
        return fontMatcher;
    }

    /**
     * 获取字符生成调度器。
     *
     * @return 字符生成调度器
     */
    public GlyphGenerationDispatcher getGlyphGenerationDispatcher() {
        return glyphGenerationDispatcher;
    }

    /**
     * 获取文本布局服务。
     *
     * @return 文本布局服务
     */
    public TextLayoutService getTextLayoutService() {
        return textLayoutService;
    }

    /**
     * 获取批渲染器。
     *
     * @return 批渲染器
     */
    public FontBatchRenderer getBatchRenderer() {
        return batchRenderer;
    }

    /**
     * 获取着色器程序封装。
     *
     * @return 着色器程序
     */
    public FontShaderProgram getShaderProgram() {
        return shaderProgram;
    }

    /**
     * 获取文本装饰线渲染器。
     *
     * @return 装饰线渲染器
     */
    public TextDecorationRenderer getDecorationRenderer() {
        return decorationRenderer;
    }

    /**
     * 获取当前字体系统运行时统计。
     *
     * @return 运行时统计快照
     */
    public FontRuntimeStats getRuntimeStats() {
        return new FontRuntimeStats(
                glyphPageManager.getPendingUploadCount(),
                glyphPageManager.getReadyGlyphCount(),
                glyphPageManager.getNormalPageCount(),
                glyphPageManager.getBoldPageCount(),
                drawStageUploadTimestamps.size(),
                batchRenderer.getQuadCount(),
                fontMatcher.getCacheHitCount(),
                fontMatcher.getCacheMissCount(),
                textLayoutService.getWidthCacheHitCount(),
                textLayoutService.getWidthCacheMissCount());
    }

    private boolean canRunDrawStageUpload() {
        long now = System.currentTimeMillis();

        while (!drawStageUploadTimestamps.isEmpty() && now - drawStageUploadTimestamps.peekFirst().longValue() >= 1000L) {
            drawStageUploadTimestamps.pollFirst();
        }
        if (now - lastDrawStageUploadAt < (long) FontConfig.drawStageUploadIntervalMs) {
            return false;
        }
        return drawStageUploadTimestamps.size() < FontConfig.drawStageUploadLimitPerSecond;
    }

    /**
     * 刷新文本测量所依赖的基础状态。
     */
    private void refreshTextMeasureRuntime() {
        fontRegistry.reload();
        fontMatcher.clearCache();
        textLayoutService.clearCache();
        layoutRuntimeReady.set(true);
        textMeasureEpoch++;
    }

    private void debugLogStats(String source) {
        if (!club.heiqi.uilib.Config.useDebug) {
            return;
        }
        MyMod.LOG.info("字体运行统计[{}]: {}", source, getRuntimeStats());
    }
}
