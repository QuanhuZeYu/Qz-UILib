package club.heiqi.uilib.font.api;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.glyph.GlyphGenerationPriority;
import club.heiqi.uilib.font.glyph.GlyphGenerationTask;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.page.GlyphPage;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;
import club.heiqi.uilib.font.render.FontRenderStateGuard;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * 默认字体适配器。
 */
public class DefaultFontRendererAdapter implements FontRendererAdapter {

    private static final DefaultFontRendererAdapter INSTANCE = new DefaultFontRendererAdapter();
    private static final String RANDOM_SAMPLE = "ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ!\"#$%&'()*+,-./0123456789:;<=>?"
            + "@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
            + "ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀"
            + "αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■";
    private final FontRenderStateGuard renderStateGuard = new FontRenderStateGuard();
    private final ThreadLocal<Integer> deferredFlushScopeDepth = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return Integer.valueOf(0);
        }
    };
    private final ThreadLocal<Boolean> deferredFlushDirty = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    private DefaultFontRendererAdapter() {}

    /**
     * 获取默认字体适配器。
     *
     * @return 适配器实例
     */
    public static DefaultFontRendererAdapter getInstance() {
        return INSTANCE;
    }

    /**
     * 开始一个受控延迟 flush 边界。
     *
     * <p>该能力仅供 UILib 内部渲染链路把同一可控 paint pass 中的相邻文本绘制合并提交；
     * 外部未进入该边界的 `drawString` 仍保持单次调用后立即 flush。调用方需要用
     * try/finally 配对结束边界。</p>
     */
    public void beginDeferredFlushScope() {
        beginDeferredFlushScope(0, 0);
    }

    /**
     * 开始一个可提供内部 UI 投影尺寸的受控延迟 flush 边界。
     *
     * @param targetWidth 渲染目标宽度
     * @param targetHeight 渲染目标高度
     */
    public void beginDeferredFlushScope(int targetWidth, int targetHeight) {
        if (!isDeferredFlushScopeActive()) {
            FontService fontService = FontService.getInstance();
            if (targetWidth > 0 && targetHeight > 0) {
                fontService.getBatchRenderer().configureInternalUiProjection(targetWidth, targetHeight);
                fontService.getBatchRenderer().setAssumeInternalUiMatrices(true);
            }
        }
        int depth = deferredFlushScopeDepth.get().intValue();
        deferredFlushScopeDepth.set(Integer.valueOf(depth + 1));
    }

    /**
     * 结束一个受控延迟 flush 边界，并在最外层边界结束时提交已收集的字形与装饰线。
     */
    public void endDeferredFlushScope() {
        int depth = deferredFlushScopeDepth.get().intValue();
        if (depth <= 0) {
            throw new IllegalStateException("字体延迟 flush 边界结束调用缺少对应 begin");
        }
        if (depth > 1) {
            deferredFlushScopeDepth.set(Integer.valueOf(depth - 1));
            return;
        }

        try {
            flushDeferredFlushScope();
        } finally {
            FontService.getInstance().getBatchRenderer().setAssumeInternalUiMatrices(false);
            deferredFlushScopeDepth.remove();
            deferredFlushDirty.remove();
        }
    }

    /**
     * 提交当前线程已收集的延迟字体批次，但不结束当前 scope。
     */
    public void flushDeferredFlushScope() {
        if (!isDeferredFlushScopeActive() || !deferredFlushDirty.get().booleanValue()) {
            return;
        }

        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            try {
                flushCollectedBatches(fontService);
            } finally {
                deferredFlushDirty.set(Boolean.FALSE);
            }
        }
    }

    /**
     * 判断当前线程是否处于受控延迟 flush 边界内。
     *
     * @return 是否处于延迟 flush scope
     */
    public boolean isDeferredFlushScopeActive() {
        return deferredFlushScopeDepth.get().intValue() > 0;
    }

    @Override
    public int drawString(String text, int x, int y, int color, boolean dropShadow) {
        return drawString(text, x, y, color, dropShadow, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 使用指定文本模式绘制字符串。
     *
     * @param text 文本
     * @param x 横坐标
     * @param y 纵坐标
     * @param color 颜色
     * @param dropShadow 是否启用阴影
     * @param textContentMode 文本内容解析模式
     * @return 绘制结束后的光标位置
     */
    public int drawString(String text, int x, int y, int color, boolean dropShadow, TextContentMode textContentMode) {
        if (text == null || text.isEmpty()) {
            return x;
        }

        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            fontService.tickDrawStage(FontConfig.drawStageUploadBatchSize);

            return drawInternal(fontService, text, x, y, normalizeColor(color), dropShadow, textContentMode, 1.0F);
        }
    }

    /**
     * 以指定 UI 缩放收集字符串绘制数据。
     *
     * <p>该入口供 `UiRenderContext` 在延迟 flush scope 内使用，直接写入最终屏幕坐标，避免批次提交时
     * 依赖单次 `drawText` 调用期间的 OpenGL 矩阵状态。</p>
     *
     * @param text 文本
     * @param x 屏幕坐标 X
     * @param y 屏幕坐标 Y
     * @param color 颜色
     * @param dropShadow 是否启用阴影
     * @param textContentMode 文本内容解析模式
     * @param renderScale UI 渲染缩放
     * @return 绘制结束后的光标位置
     */
    public int drawStringScaled(String text, float x, float y, int color, boolean dropShadow,
            TextContentMode textContentMode, float renderScale) {
        if (text == null || text.isEmpty()) {
            return (int) Math.ceil(x);
        }

        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            fontService.tickDrawStage(FontConfig.drawStageUploadBatchSize);

            return drawInternal(fontService, text, x, y, normalizeColor(color), dropShadow, textContentMode,
                    Math.max(0.01F, renderScale));
        }
    }

    @Override
    public int getStringWidth(String text) {
        return getStringWidth(text, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 使用指定文本模式测量字符串宽度。
     *
     * @param text 文本
     * @param textContentMode 文本内容解析模式
     * @return 宽度
     */
    public int getStringWidth(String text, TextContentMode textContentMode) {
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            return fontService.getTextLayoutService().getStringWidth(text, textContentMode);
        }
    }

    @Override
    public int getLineHeight() {
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            return fontService.getTextLayoutService().getLineHeight();
        }
    }

    @Override
    public String trimStringToWidth(String text, int targetWidth) {
        return trimStringToWidth(text, targetWidth, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 使用指定文本模式按宽度裁剪字符串。
     *
     * @param text 文本
     * @param targetWidth 目标宽度
     * @param textContentMode 文本内容解析模式
     * @return 裁剪结果
     */
    public String trimStringToWidth(String text, int targetWidth, TextContentMode textContentMode) {
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            return fontService.getTextLayoutService().trimStringToWidth(text, targetWidth, textContentMode);
        }
    }

    public String trimStringToWidth(String text, int targetWidth, boolean reverse) {
        return trimStringToWidth(text, targetWidth, reverse, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 使用指定文本模式按宽度裁剪字符串，可选从尾部保留。
     *
     * @param text 文本
     * @param targetWidth 目标宽度
     * @param reverse 是否从尾部保留
     * @param textContentMode 文本内容解析模式
     * @return 裁剪结果
     */
    public String trimStringToWidth(String text, int targetWidth, boolean reverse,
            TextContentMode textContentMode) {
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            return fontService.getTextLayoutService().trimStringToWidth(text, targetWidth, reverse, textContentMode);
        }
    }

    @Override
    public String wrapFormattedStringToWidth(String text, int wrapWidth) {
        return wrapFormattedStringToWidth(text, wrapWidth, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 使用指定文本模式按宽度插入换行。
     *
     * @param text 文本
     * @param wrapWidth 最大宽度
     * @param textContentMode 文本内容解析模式
     * @return 包含换行的新文本
     */
    public String wrapFormattedStringToWidth(String text, int wrapWidth, TextContentMode textContentMode) {
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            return fontService.getTextLayoutService().wrapFormattedStringToWidth(text, wrapWidth, textContentMode);
        }
    }

    @Override
    public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
        return listFormattedStringToWidth(text, wrapWidth, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 使用指定文本模式按宽度拆分文本。
     *
     * @param text 文本
     * @param wrapWidth 最大宽度
     * @param textContentMode 文本内容解析模式
     * @return 拆分结果
     */
    public List<String> listFormattedStringToWidth(String text, int wrapWidth, TextContentMode textContentMode) {
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            return fontService.getTextLayoutService().listFormattedStringToWidth(text, wrapWidth, textContentMode);
        }
    }

    @Override
    public int splitStringWidth(String text, int wrapWidth) {
        return splitStringWidth(text, wrapWidth, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 使用指定文本模式计算拆行高度。
     *
     * @param text 文本
     * @param wrapWidth 最大宽度
     * @param textContentMode 文本内容解析模式
     * @return 高度
     */
    public int splitStringWidth(String text, int wrapWidth, TextContentMode textContentMode) {
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            return fontService.getTextLayoutService().splitStringWidth(text, wrapWidth, textContentMode);
        }
    }

    /**
     * 绘制多行文本。
     *
     * @param text 文本
     * @param x 起始 X
     * @param y 起始 Y
     * @param wrapWidth 最大宽度
     * @param color 颜色
     */
    public void drawSplitString(String text, int x, int y, int wrapWidth, int color) {
        drawSplitString(text, x, y, wrapWidth, color, TextContentMode.MINECRAFT_FORMATTED);
    }

    /**
     * 使用指定文本模式绘制多行文本。
     *
     * @param text 文本
     * @param x 起始 X
     * @param y 起始 Y
     * @param wrapWidth 最大宽度
     * @param color 颜色
     * @param textContentMode 文本内容解析模式
     */
    public void drawSplitString(String text, int x, int y, int wrapWidth, int color, TextContentMode textContentMode) {
        List<String> lines = listFormattedStringToWidth(text, wrapWidth, textContentMode);
        int lineHeight = getLineHeight();
        for (String line : lines) {
            drawString(line, x, y, color, false, textContentMode);
            y += lineHeight;
        }
    }

    private int drawInternal(FontService fontService, String text, float x, float y, int color, boolean dropShadow,
            TextContentMode textContentMode, float renderScale) {
        TextLayoutService textLayoutService = fontService.getTextLayoutService();
        GlyphPageManager glyphPageManager = fontService.getGlyphPageManager();
        List<TextSegment> segments = textLayoutService.layoutSegments(text, color, textContentMode);
        if (segments.isEmpty()) {
            return (int) Math.ceil(x);
        }

        float currentX = x;
        float drawY = y;
        float charSize = (float) FontConfig.charSize * renderScale;
        int runtimeVersion = fontService.getRuntimeVersion();
        int glyphSize = Math.max(8, (int) Math.ceil(FontConfig.awtCharSize));
        GlyphRuntimeTables tables = glyphPageManager.getRuntimeTables();
        for (TextSegment segment : segments) {
            TextStyle style = segment.getStyle();
            String segmentText = segment.getText();
            FontType fontType = style.getFontType();
            int[] locations = tables.locationArray(fontType);
            byte[] flags = tables.flagsArray(fontType);
            short[] slotXByIndex = tables.slotXByIndex;
            short[] slotYByIndex = tables.slotYByIndex;
            GlyphPage[] pages = tables.pages(fontType);
            int pageCount = tables.pageCount(fontType);
            for (int i = 0; i < segmentText.length();) {
                int codepoint = segmentText.codePointAt(i);
                double codepointWidth = textLayoutService.getCodepointWidth(codepoint, style);
                float measuredWidth = (float) codepointWidth * renderScale;
                int renderCodepoint = style.isRandomStyle()
                        ? resolveRandomStyleCodepoint(codepoint, style, codepointWidth, textLayoutService)
                        : codepoint;
                int pageIndex = -1;
                int textureId = 0;
                int textureSize = 0;
                GlyphPage glyphPage = null;
                int slotIndex = -1;
                int slotX = 0;
                int slotY = 0;
                byte glyphFlags = 0;
                boolean validCodepoint = GlyphRuntimeTables.isValidCodepoint(renderCodepoint);
                boolean glyphReady = false;
                if (validCodepoint) {
                    int packedLocation = locations[renderCodepoint];
                    if (packedLocation != GlyphRuntimeTables.LOCATION_NOT_READY) {
                        pageIndex = GlyphRuntimeTables.unpackPageIndex(packedLocation);
                        if (pageIndex >= 0 && pageIndex < pageCount) {
                            glyphPage = pages[pageIndex];
                            slotIndex = GlyphRuntimeTables.unpackSlotIndex(packedLocation);
                            if (slotIndex >= 0 && slotIndex < slotXByIndex.length
                                    && slotIndex < slotYByIndex.length && glyphPage != null
                                    && glyphPage.getRuntimeVersion() == runtimeVersion) {
                                textureSize = glyphPage.getTextureSize();
                                slotX = slotXByIndex[slotIndex] & 0xFFFF;
                                slotY = slotYByIndex[slotIndex] & 0xFFFF;
                                glyphFlags = flags[renderCodepoint];
                                glyphReady = true;
                            } else {
                                pageIndex = -1;
                            }
                        }
                    }
                }

                if (!glyphReady && validCodepoint) {
                    fontService.getGlyphGenerationDispatcher().submit(new GlyphGenerationTask(
                            runtimeVersion,
                            renderCodepoint,
                            fontType,
                            glyphSize,
                            GlyphGenerationPriority.HIGH));
                }

                if (dropShadow) {
                    if (glyphReady && glyphPage != null) {
                        textureId = glyphPage.getOrCreateTextureId();
                    }
                    collectGlyph(fontService, fontType, glyphReady, pageIndex, textureId, textureSize, slotX, slotY,
                            glyphSize, glyphFlags,
                            currentX + (float) FontConfig.shadowOffsetX * renderScale,
                            drawY + (float) FontConfig.shadowOffsetY * renderScale,
                            measuredWidth, charSize, renderScale, style, darkenShadow(style.getColor()));
                }
                if (glyphReady && textureId == 0 && glyphPage != null) {
                    textureId = glyphPage.getOrCreateTextureId();
                }
                collectGlyph(fontService, fontType, glyphReady, pageIndex, textureId, textureSize, slotX, slotY,
                        glyphSize, glyphFlags, currentX, drawY, measuredWidth, charSize, renderScale, style,
                        style.getColor());
                currentX += measuredWidth;

                i += Character.charCount(codepoint);
            }
        }
        if (!isDeferredFlushScopeActive()) {
            flushCollectedBatches(fontService);
        }
        return (int) Math.ceil(currentX);
    }

    private int resolveRandomStyleCodepoint(int originalCodepoint, TextStyle style, double originalWidth,
            TextLayoutService textLayoutService) {
        int fallbackCodepoint = originalCodepoint;
        double bestDifference = Double.MAX_VALUE;

        for (int i = 0; i < 16; i++) {
            int randomIndex = ThreadLocalRandom.current().nextInt(RANDOM_SAMPLE.length());
            int candidateCodepoint = RANDOM_SAMPLE.codePointAt(randomIndex);
            double candidateWidth = textLayoutService.getCodepointWidth(candidateCodepoint, style);
            double difference = Math.abs(candidateWidth - originalWidth);
            if (difference < 0.05D) {
                return candidateCodepoint;
            }
            if (difference < bestDifference) {
                bestDifference = difference;
                fallbackCodepoint = candidateCodepoint;
            }
        }
        return fallbackCodepoint;
    }

    private void collectGlyph(FontService fontService, FontType fontType, boolean glyphReady, int pageIndex,
            int textureId, int textureSize, int slotX, int slotY, int glyphSize, byte glyphFlags, float currentX,
            float drawY, float measuredWidth, float charSize, float renderScale, TextStyle style, int renderColor) {
        if (glyphReady || style.isUnderline() || style.isStrikethrough()) {
            markDeferredFlushDirtyIfNeeded();
        }
        if (glyphReady && textureId > 0) {
            fontService.getBatchRenderer().collect(fontType, pageIndex, textureId, textureSize, slotX, slotY,
                    glyphSize, glyphSize, currentX, drawY, charSize, renderColor, style.isItalic(), glyphFlags);
        }
        collectDecorations(fontService, currentX, drawY, measuredWidth, charSize, renderScale, style, renderColor);
    }

    private void collectDecorations(FontService fontService, float currentX, float drawY, float width, float charSize,
            float renderScale, TextStyle style, int color) {
        if (style.isUnderline()) {
            fontService.getBatchRenderer().collectDecoration(currentX, drawY + charSize - renderScale, width,
                    renderScale, color);
        }
        if (style.isStrikethrough()) {
            fontService.getBatchRenderer().collectDecoration(currentX, drawY + (charSize / 2.0F) - (0.5F * renderScale),
                    width, renderScale, color);
        }
    }

    private void markDeferredFlushDirtyIfNeeded() {
        if (isDeferredFlushScopeActive()) {
            deferredFlushDirty.set(Boolean.TRUE);
        }
    }

    private void flushCollectedBatches(final FontService fontService) {
        try {
            renderStateGuard.run(new Runnable() {
                @Override
                public void run() {
                    fontService.getBatchRenderer().flushWithinActiveState(fontService.getShaderProgram());
                }
            }, !fontService.getBatchRenderer().isAssumingInternalUiMatrices());
        } catch (RuntimeException exception) {
            clearCollectedBatches(fontService);
            throw exception;
        } catch (Error error) {
            clearCollectedBatches(fontService);
            throw error;
        }
    }

    private void clearCollectedBatches(FontService fontService) {
        fontService.getBatchRenderer().clearFrame();
    }

    private int normalizeColor(int color) {
        if ((color & 0xFC000000) == 0) {
            return color | 0xFF000000;
        }
        return color;
    }

    private int darkenShadow(int color) {
        int normalized = normalizeColor(color);
        return (normalized & 0xFCFCFC) >> 2 | normalized & 0xFF000000;
    }

}
