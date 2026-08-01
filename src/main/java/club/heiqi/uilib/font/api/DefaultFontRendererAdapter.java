package club.heiqi.uilib.font.api;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.GlyphRuntimeTablesView;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.glyph.GlyphGenerationPriority;
import club.heiqi.uilib.font.glyph.GlyphGenerationTask;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;
import club.heiqi.uilib.font.render.FontRenderStateGuard;
import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureStyle;

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
    private final ThreadLocal<Integer> deferredFlushTargetWidth = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return Integer.valueOf(0);
        }
    };
    private final ThreadLocal<Integer> deferredFlushTargetHeight = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return Integer.valueOf(0);
        }
    };
    private final ThreadLocal<Boolean> deferredFlushInternalUiProjectionConfigured = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };
    private final ThreadLocal<Boolean> deferredFlushRenderStateGuardActive = new ThreadLocal<Boolean>() {
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
        int depth = deferredFlushScopeDepth.get().intValue();
        if (depth <= 0) {
            renderStateGuard.push(false);
            deferredFlushTargetWidth.set(Integer.valueOf(Math.max(0, targetWidth)));
            deferredFlushTargetHeight.set(Integer.valueOf(Math.max(0, targetHeight)));
            deferredFlushInternalUiProjectionConfigured.set(Boolean.FALSE);
            deferredFlushRenderStateGuardActive.set(Boolean.TRUE);
        }
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
            try {
                if (deferredFlushInternalUiProjectionConfigured.get().booleanValue()) {
                    FontService.getInstance().getBatchRenderer().setAssumeInternalUiMatrices(false);
                }
            } finally {
                try {
                    if (deferredFlushRenderStateGuardActive.get().booleanValue()) {
                        renderStateGuard.pop();
                    }
                } finally {
                    deferredFlushScopeDepth.remove();
                    deferredFlushDirty.remove();
                    deferredFlushTargetWidth.remove();
                    deferredFlushTargetHeight.remove();
                    deferredFlushInternalUiProjectionConfigured.remove();
                    deferredFlushRenderStateGuardActive.remove();
                }
            }
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
        return drawBaselineAlignedString(text, x, y, color, dropShadow);
    }

    /**
     * 按字体 atlas 基线对齐契约绘制字符串。
     *
     * @param text 文本
     * @param x 横坐标
     * @param y 纵坐标
     * @param color 颜色
     * @param dropShadow 是否启用阴影
     * @return 绘制结束后的光标位置
     */
    @Override
    public int drawBaselineAlignedString(String text, int x, int y, int color, boolean dropShadow) {
        return drawBaselineAlignedString(text, x, y, color, dropShadow, TextContentMode.MINECRAFT_FORMATTED);
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
        return drawBaselineAlignedString(text, x, y, color, dropShadow, textContentMode);
    }

    /**
     * 使用指定文本模式按字体 atlas 基线对齐契约绘制字符串。
     *
     * @param text 文本
     * @param x 横坐标
     * @param y 纵坐标
     * @param color 颜色
     * @param dropShadow 是否启用阴影
     * @param textContentMode 文本内容解析模式
     * @return 绘制结束后的光标位置
     */
    public int drawBaselineAlignedString(String text, int x, int y, int color, boolean dropShadow,
            TextContentMode textContentMode) {
        return drawBaselineAlignedString(text, x, y, color, dropShadow, textContentMode, UiFontWeight.NORMAL,
                UiFontStyle.NORMAL);
    }

    /**
     * 使用指定文本模式和基础字体样式绘制字符串。
     *
     * @param text 文本
     * @param x 横坐标
     * @param y 纵坐标
     * @param color 颜色
     * @param dropShadow 是否启用阴影
     * @param textContentMode 文本内容解析模式
     * @param fontWeight 字体粗细
     * @param fontStyle 字体样式
     * @return 绘制结束后的光标位置
     */
    public int drawString(String text, int x, int y, int color, boolean dropShadow, TextContentMode textContentMode,
            UiFontWeight fontWeight, UiFontStyle fontStyle) {
        return drawBaselineAlignedString(text, x, y, color, dropShadow, textContentMode, fontWeight, fontStyle);
    }

    /**
     * 使用指定文本模式和基础字体样式按字体 atlas 基线对齐契约绘制字符串。
     *
     * @param text 文本
     * @param x 横坐标
     * @param y 纵坐标
     * @param color 颜色
     * @param dropShadow 是否启用阴影
     * @param textContentMode 文本内容解析模式
     * @param fontWeight 字体粗细
     * @param fontStyle 字体样式
     * @return 绘制结束后的光标位置
     */
    public int drawBaselineAlignedString(String text, int x, int y, int color, boolean dropShadow,
            TextContentMode textContentMode, UiFontWeight fontWeight, UiFontStyle fontStyle) {
        if (text == null || text.isEmpty()) {
            return x;
        }

        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            return drawWithRenderStateGuardIfNeeded(fontService, new DrawStringTask() {
                @Override
                public int run() {
                    fontService.initialize();
                    fontService.tickDrawStage(FontConfig.drawStageUploadBatchSize);
                    return drawBaselineAlignedStringInternal(fontService, text, x, y, normalizeColor(color), dropShadow,
                            textContentMode, fontWeight, fontStyle, 1.0F);
                }
            });
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
        return drawBaselineAlignedStringScaled(text, x, y, color, dropShadow, textContentMode, renderScale);
    }

    /**
     * 以指定 UI 缩放按字体 atlas 基线对齐契约收集字符串绘制数据。
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
    public int drawBaselineAlignedStringScaled(String text, float x, float y, int color, boolean dropShadow,
            TextContentMode textContentMode, float renderScale) {
        return drawBaselineAlignedStringScaled(text, x, y, color, dropShadow, textContentMode, UiFontWeight.NORMAL,
                UiFontStyle.NORMAL, renderScale);
    }

    /**
     * 以指定 UI 缩放和基础字体样式收集字符串绘制数据。
     *
     * @param text 文本
     * @param x 屏幕坐标 X
     * @param y 屏幕坐标 Y
     * @param color 颜色
     * @param dropShadow 是否启用阴影
     * @param textContentMode 文本内容解析模式
     * @param fontWeight 字体粗细
     * @param fontStyle 字体样式
     * @param renderScale UI 渲染缩放
     * @return 绘制结束后的光标位置
     */
    public int drawStringScaled(String text, float x, float y, int color, boolean dropShadow,
            TextContentMode textContentMode, UiFontWeight fontWeight, UiFontStyle fontStyle, float renderScale) {
        return drawBaselineAlignedStringScaled(text, x, y, color, dropShadow, textContentMode, fontWeight, fontStyle,
                renderScale);
    }

    /**
     * 以指定 UI 缩放和基础字体样式按字体 atlas 基线对齐契约收集字符串绘制数据。
     *
     * @param text 文本
     * @param x 屏幕坐标 X
     * @param y 屏幕坐标 Y
     * @param color 颜色
     * @param dropShadow 是否启用阴影
     * @param textContentMode 文本内容解析模式
     * @param fontWeight 字体粗细
     * @param fontStyle 字体样式
     * @param renderScale UI 渲染缩放
     * @return 绘制结束后的光标位置
     */
    public int drawBaselineAlignedStringScaled(String text, float x, float y, int color, boolean dropShadow,
            TextContentMode textContentMode, UiFontWeight fontWeight, UiFontStyle fontStyle, float renderScale) {
        if (text == null || text.isEmpty()) {
            return (int) Math.ceil(x);
        }

        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            return drawWithRenderStateGuardIfNeeded(fontService, new DrawStringTask() {
                @Override
                public int run() {
                    fontService.initialize();
                    fontService.tickDrawStage(FontConfig.drawStageUploadBatchSize);
                    return drawBaselineAlignedStringInternal(fontService, text, x, y, normalizeColor(color), dropShadow,
                            textContentMode, fontWeight, fontStyle, Math.max(0.01F, renderScale));
                }
            });
        }
    }

    /**
     * 按目标 UI 像素字号和字体 atlas 基线契约收集字符串绘制数据。
     *
     * @param text 文本
     * @param x 屏幕坐标 X
     * @param y 屏幕坐标 Y
     * @param color 颜色
     * @param dropShadow 是否启用阴影
     * @param style 文本样式快照
     * @return 绘制结束后的光标位置
     */
    public int drawBaselineAlignedStringPx(String text, float x, float y, int color, boolean dropShadow,
            TextMeasureStyle style) {
        if (text == null || text.isEmpty()) {
            return (int) Math.ceil(x);
        }
        final TextMeasureStyle resolvedStyle = style == null ? TextMeasureStyle.DEFAULT : style;

        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            return drawWithRenderStateGuardIfNeeded(fontService, new DrawStringTask() {
                @Override
                public int run() {
                    fontService.initialize();
                    FontRuntimeSettings settings = fontService.getRuntimeSettings();
                    fontService.tickDrawStage(FontConfig.drawStageUploadBatchSize);
                    return drawBaselineAlignedStringInternal(fontService, text, x, y, normalizeColor(color), dropShadow,
                            resolvedStyle.getTextContentMode(), resolvedStyle.getFontWeight(),
                            resolvedStyle.getFontStyle(), Math.max(1.0F, (float) resolvedStyle.getFontSizePx()),
                            Math.max(0.01F, resolvedStyle.getFontSizePx()
                                    / Math.max(1.0F, (float) settings.getCharSize())));
                }
            });
        }
    }

    /**
     * 使用语义化文本样式测量字符串 UI 像素宽度。
     *
     * @param text 文本
     * @param style 文本样式快照
     * @return UI 像素宽度
     */
    public int getStringWidth(String text, TextMeasureStyle style) {
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            return fontService.getTextLayoutService().getStringWidth(text, style);
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

    /**
     * 计算字符串按码点边界切分的原始前缀宽度向量（{@code UILIB_RAW} 语义）。
     *
     * @param text 文本
     * @param fontWeight 字体粗细
     * @param fontStyle 字体样式
     * @return 原始坐标系下的前缀宽度向量
     */
    public int[] prefixWidthsRaw(String text, UiFontWeight fontWeight, UiFontStyle fontStyle) {
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            fontService.initialize();
            return fontService.getTextLayoutService().prefixWidthsRaw(text, fontWeight, fontStyle);
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
    public int getTextMeasureEpoch() {
        return FontService.getInstance().getTextMeasureEpoch();
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
            drawBaselineAlignedString(line, x, y, color, false, textContentMode);
            y += lineHeight;
        }
    }

    private int drawBaselineAlignedStringInternal(FontService fontService, String text, float x, float y, int color,
            boolean dropShadow, TextContentMode textContentMode, UiFontWeight fontWeight, UiFontStyle fontStyle,
            float renderScale) {
        FontRuntimeSettings settings = fontService.getRuntimeSettings();
        return drawBaselineAlignedStringInternal(fontService, text, x, y, color, dropShadow, textContentMode,
                fontWeight, fontStyle, (float) settings.getCharSize() * renderScale, renderScale);
    }

    private int drawBaselineAlignedStringInternal(FontService fontService, String text, float x, float y, int color,
            boolean dropShadow, TextContentMode textContentMode, UiFontWeight fontWeight, UiFontStyle fontStyle,
            float charSize, float renderScale) {
        FontRuntimeSettings settings = fontService.getRuntimeSettings();
        TextLayoutService textLayoutService = fontService.getTextLayoutService();
        List<TextSegment> segments = textLayoutService.layoutSegments(text, color, textContentMode, fontWeight,
                fontStyle);
        if (segments.isEmpty()) {
            return (int) Math.ceil(x);
        }

        float currentX = x;
        float drawY = y;
        GlyphRuntimeTablesView tables = fontService.getGlyphRuntimeTablesView();
        int runtimeVersion = tables.getRuntimeVersion();
        int glyphSize = settings.getGlyphSize();
        for (TextSegment segment : segments) {
            TextStyle style = segment.getStyle();
            String segmentText = segment.getText();
            FontType fontType = style.getFontType();
            int pageCount = tables.getPageCount(fontType);
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
                int slotIndex = -1;
                int slotX = 0;
                int slotY = 0;
                int slotWidth = 0;
                int slotHeight = 0;
                int atlasBaselineX = 0;
                int atlasBaselineY = 0;
                int lineBaselineY = glyphSize;
                int inkWidth = 0;
                int inkHeight = 0;
                int bearingX = 0;
                int bearingY = 0;
                byte glyphFlags = 0;
                boolean validCodepoint = GlyphRuntimeTables.isValidCodepoint(renderCodepoint);
                boolean glyphReady = false;
                if (validCodepoint) {
                    int packedLocation = tables.getPackedLocation(renderCodepoint, fontType);
                    if (packedLocation == GlyphRuntimeTables.LOCATION_NO_BITMAP) {
                        glyphReady = true;
                    } else if (packedLocation != GlyphRuntimeTables.LOCATION_NOT_READY) {
                        pageIndex = GlyphRuntimeTables.unpackPageIndex(packedLocation);
                        if (pageIndex >= 0 && pageIndex < pageCount) {
                            slotIndex = GlyphRuntimeTables.unpackSlotIndex(packedLocation);
                            if (slotIndex >= 0 && tables.isCurrentPage(fontType, pageIndex)) {
                                textureSize = tables.getPageTextureSize(fontType, pageIndex);
                                slotX = tables.getSlotX(renderCodepoint, fontType);
                                slotY = tables.getSlotY(renderCodepoint, fontType);
                                slotWidth = tables.getSlotWidth(renderCodepoint, fontType);
                                slotHeight = tables.getSlotHeight(renderCodepoint, fontType);
                                atlasBaselineX = tables.getAtlasBaselineX(renderCodepoint, fontType);
                                atlasBaselineY = tables.getAtlasBaselineY(renderCodepoint, fontType);
                                lineBaselineY = tables.getLineBaselineY(renderCodepoint, fontType);
                                inkWidth = tables.getInkWidth(renderCodepoint, fontType);
                                inkHeight = tables.getInkHeight(renderCodepoint, fontType);
                                bearingX = tables.getBearingX(renderCodepoint, fontType);
                                bearingY = tables.getBearingY(renderCodepoint, fontType);
                                glyphFlags = tables.getFlags(renderCodepoint, fontType);
                                glyphReady = slotWidth > 0 && slotHeight > 0;
                            } else {
                                pageIndex = -1;
                            }
                        }
                    }
                }

                if (!glyphReady && validCodepoint) {
                    fontService.submitGlyphGeneration(new GlyphGenerationTask(
                            runtimeVersion,
                            renderCodepoint,
                            fontType,
                            glyphSize,
                            GlyphGenerationPriority.HIGH));
                }

                if (dropShadow) {
                    if (glyphReady && pageIndex >= 0) {
                        textureId = tables.getPageTextureId(fontType, pageIndex);
                    }
                    collectGlyph(fontService, fontType, glyphReady, pageIndex, textureId, textureSize, slotX, slotY,
                            slotWidth, slotHeight, atlasBaselineX, atlasBaselineY, lineBaselineY, glyphSize, glyphFlags,
                            inkWidth, inkHeight, bearingX, bearingY,
                            currentX + (float) FontConfig.shadowOffsetX * renderScale,
                            drawY + (float) FontConfig.shadowOffsetY * renderScale,
                            measuredWidth, charSize, (float) settings.getCharSize(), renderScale, style,
                            darkenShadow(style.getColor()));
                }
                if (glyphReady && textureId == 0 && pageIndex >= 0) {
                    textureId = tables.getPageTextureId(fontType, pageIndex);
                }
                collectGlyph(fontService, fontType, glyphReady, pageIndex, textureId, textureSize, slotX, slotY,
                        slotWidth, slotHeight, atlasBaselineX, atlasBaselineY, lineBaselineY, glyphSize, glyphFlags,
                        inkWidth, inkHeight, bearingX, bearingY,
                        currentX, drawY, measuredWidth, charSize, (float) settings.getCharSize(), renderScale, style,
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
            int textureId, int textureSize, int slotX, int slotY, int slotWidth, int slotHeight, int atlasBaselineX,
            int atlasBaselineY, int lineBaselineY, int glyphSize, byte glyphFlags, int inkWidth, int inkHeight,
            int bearingX, int bearingY, float currentX, float drawY, float measuredWidth, float charSize,
            float baseCharSize, float renderScale, TextStyle style, int renderColor) {
        boolean hasGlyphQuad = glyphReady && textureId > 0 && slotWidth > 0 && slotHeight > 0
                && inkWidth > 0 && inkHeight > 0;
        if (hasGlyphQuad || style.isUnderline() || style.isStrikethrough()) {
            markDeferredFlushDirtyIfNeeded();
        }
        if (hasGlyphQuad) {
            fontService.getBatchRenderer().collectBaselineAlignedGlyph(fontType, pageIndex, textureId, textureSize,
                    slotX, slotY, slotWidth, slotHeight, atlasBaselineX, atlasBaselineY, lineBaselineY, glyphSize,
                    inkWidth, inkHeight, bearingX, bearingY, currentX, drawY, charSize, renderColor, style.isItalic(),
                    glyphFlags, baseCharSize);
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
            configureDeferredFlushProjectionIfNeeded();
            deferredFlushDirty.set(Boolean.TRUE);
        }
    }

    private void configureDeferredFlushProjectionIfNeeded() {
        if (deferredFlushInternalUiProjectionConfigured.get().booleanValue()) {
            return;
        }
        int targetWidth = deferredFlushTargetWidth.get().intValue();
        int targetHeight = deferredFlushTargetHeight.get().intValue();
        if (targetWidth <= 0 || targetHeight <= 0) {
            return;
        }
        FontService fontService = FontService.getInstance();
        fontService.getBatchRenderer().configureInternalUiProjection(targetWidth, targetHeight);
        fontService.getBatchRenderer().setAssumeInternalUiMatrices(true);
        deferredFlushInternalUiProjectionConfigured.set(Boolean.TRUE);
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

    private int drawWithRenderStateGuardIfNeeded(FontService fontService, DrawStringTask task) {
        if (isDeferredFlushScopeActive()) {
            return task.run();
        }
        renderStateGuard.push(false);
        try {
            return task.run();
        } finally {
            renderStateGuard.pop();
        }
    }

    private interface DrawStringTask {

        int run();
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
