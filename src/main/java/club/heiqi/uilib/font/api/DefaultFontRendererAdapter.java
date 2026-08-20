package club.heiqi.uilib.font.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.LatexParser;
import club.heiqi.uilib.font.latex.layout.GlyphElem;
import club.heiqi.uilib.font.latex.layout.LatexCache;
import club.heiqi.uilib.font.latex.layout.MathBox;
import club.heiqi.uilib.font.latex.layout.MathLayoutService;
import club.heiqi.uilib.font.latex.layout.MathMetrics;
import club.heiqi.uilib.font.latex.layout.RuleElem;
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
import club.heiqi.uilib.font.util.UnicodeTextClassifier;
import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureStyle;

/**
 * 默认字体适配器。
 */
public class DefaultFontRendererAdapter implements FontRendererAdapter {

    private static final DefaultFontRendererAdapter INSTANCE = new DefaultFontRendererAdapter();
    /** 数学布局引擎（无状态，与测量侧 TextLayoutService 共享同一定位口径）。 */
    private static final MathLayoutService MATH_LAYOUT = new MathLayoutService();
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
            initializeForRender(fontService);
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
            initializeForRender(fontService);
            FontRuntimeSettings settings = fontService.getRuntimeSettings();
            PreparedText preparedText = prepareTextDemand(fontService, text, normalizeColor(color), textContentMode,
                    fontWeight, fontStyle, 1.0F, (float) settings.getCharSize(), settings);
            if (preparedText.isEmpty()) {
                return (int) Math.ceil(x);
            }
            return drawWithRenderStateGuardIfNeeded(fontService, new DrawStringTask() {
                @Override
                public int run() {
                    return drawPreparedText(fontService, preparedText, x, y, dropShadow,
                            (float) settings.getCharSize(), 1.0F);
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
            initializeForRender(fontService);
            FontRuntimeSettings settings = fontService.getRuntimeSettings();
            float resolvedRenderScale = Math.max(0.01F, renderScale);
            PreparedText preparedText = prepareTextDemand(fontService, text, normalizeColor(color), textContentMode,
                    fontWeight, fontStyle, resolvedRenderScale, (float) settings.getCharSize(), settings);
            if (preparedText.isEmpty()) {
                return (int) Math.ceil(x);
            }
            return drawWithRenderStateGuardIfNeeded(fontService, new DrawStringTask() {
                @Override
                public int run() {
                    return drawPreparedText(fontService, preparedText, x, y, dropShadow,
                            (float) settings.getCharSize() * resolvedRenderScale, resolvedRenderScale);
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
            initializeForRender(fontService);
            FontRuntimeSettings settings = fontService.getRuntimeSettings();
            float charSize = Math.max(1.0F, (float) resolvedStyle.getFontSizePx());
            // 富文本 span 字号语义 = 绝对 UI 像素（以调用方 px 字号为基准），
            // 缩放统一由 renderScale 表达；px 路径 renderScale=1.0，避免与 span 字号双重放大。
            float renderScale = 1.0F;
            PreparedText preparedText = prepareTextDemand(fontService, text, normalizeColor(color),
                    resolvedStyle.getTextContentMode(), resolvedStyle.getFontWeight(), resolvedStyle.getFontStyle(),
                    renderScale, charSize, settings);
            if (preparedText.isEmpty()) {
                return (int) Math.ceil(x);
            }
            return drawWithRenderStateGuardIfNeeded(fontService, new DrawStringTask() {
                @Override
                public int run() {
                    return drawPreparedText(fontService, preparedText, x, y, dropShadow, charSize, renderScale);
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

    /**
     * 在 scene/HUD replay 前批量发布当前 plan 的 raw visible text demand，不执行 upload 或 draw。
     *
     * @param texts 当前 paint plan 中的 raw 文本
     */
    public void publishVisibleRawTextDemand(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return;
        }
        FontService fontService = FontService.getInstance();
        synchronized (fontService) {
            initializeForRender(fontService);
            FontRuntimeSettings settings = fontService.getRuntimeSettings();
            GlyphRuntimeTablesView tables = fontService.getGlyphRuntimeTablesView();
            int runtimeVersion = tables.getRuntimeVersion();
            int glyphSize = settings.getGlyphSize();
            Set<Long> submittedDemands = new HashSet<Long>();
            for (String text : texts) {
                if (text == null || text.isEmpty()) {
                    continue;
                }
                for (int index = 0; index < text.length();) {
                    int codepoint = text.codePointAt(index);
                    submitVisibleDemandIfNeeded(fontService, tables, runtimeVersion, glyphSize, codepoint,
                            FontType.NORMAL, submittedDemands);
                    index += Character.charCount(codepoint);
                }
            }
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

    private PreparedText prepareTextDemand(FontService fontService, String text, int color,
            TextContentMode textContentMode, UiFontWeight fontWeight, UiFontStyle fontStyle, float renderScale,
            float baseFontSizePx, FontRuntimeSettings settings) {
        TextLayoutService textLayoutService = fontService.getTextLayoutService();
        List<TextSegment> segments = textLayoutService.layoutSegments(text, color, textContentMode, fontWeight,
                fontStyle);
        if (segments.isEmpty()) {
            return PreparedText.empty(settings);
        }

        GlyphRuntimeTablesView demandTables = fontService.getGlyphRuntimeTablesView();
        PreparedText preparedText = prepareGlyphs(settings, segments, textLayoutService, renderScale, baseFontSizePx,
                demandTables);
        int runtimeVersion = demandTables.getRuntimeVersion();
        int glyphSize = settings.getGlyphSize();
        Set<Long> submittedDemands = new HashSet<Long>();
        for (int index = 0; index < preparedText.size(); index++) {
            submitVisibleDemandIfNeeded(fontService, demandTables, runtimeVersion, glyphSize,
                    preparedText.renderCodepoints[index], preparedText.fontTypes[index], submittedDemands);
        }
        return preparedText;
    }

    private int drawPreparedText(FontService fontService, PreparedText preparedText, float x, float y,
            boolean dropShadow, float charSize, float renderScale) {
        FontRuntimeSettings settings = preparedText.settings;
        if (!fontService.isRenderThreadCaptured()) {
            // 主渲染上下文建立前（如 Forge Splash 阶段）：在调用线程的 GL 上下文内同步泵送上传，
            // 使字符页纹理在当帧可用；主渲染线程捕获后由 FontService 检测上下文切换并全量重建。
            fontService.pumpWorldLoadUploads();
        }
        GlyphRuntimeTablesView tables = fontService.getGlyphRuntimeTablesView();
        int glyphSize = settings.getGlyphSize();
        float currentX = x;
        float drawY = y;
        // 基线按行内最大字号换算（整段一致，循环外只算一次）
        float baselineCharSize = resolveBaselineCharSize(renderScale, preparedText.maxFontSizePx);
        // 全段同字号（无 span 缩放的默认文本）走 uniform 快路径：循环内直接复用常量，
        // per-glyph 零 Math.max/乘法（与逐 glyph 解析结果恒等，非基准相等类快路径）。
        boolean uniformSize = preparedText.maxFontSizePx <= preparedText.baseFontSizePx;
        float uniformGlyphCharSize = uniformSize
                ? resolveGlyphCharSize(renderScale, preparedText.baseFontSizePx) : 0.0F;
        for (int glyphIndex = 0; glyphIndex < preparedText.size(); glyphIndex++) {
            TextStyle style = preparedText.styles[glyphIndex];
            FontType fontType = preparedText.fontTypes[glyphIndex];
            int pageCount = tables.getPageCount(fontType);
            int renderCodepoint = preparedText.renderCodepoints[glyphIndex];
            float measuredWidth = preparedText.measuredWidths[glyphIndex];
            float glyphCharSize = uniformSize
                    ? uniformGlyphCharSize
                    : resolveGlyphCharSize(renderScale, preparedText.fontSizePx[glyphIndex]);
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
                        // 帧级页表快照直读：页无效返回 0，等效旧路径三次逐页 call 的语义，
                        // 绘制循环内零 FontRuntimeAccess 开销。
                        textureId = tables.getPageTextureIdSnapshot(fontType, pageIndex);
                        textureSize = tables.getPageTextureSizeSnapshot(fontType, pageIndex);
                        if (slotIndex >= 0 && textureId > 0) {
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

            // GPOS mark 定位：组合标记按锚点堆叠（xOffset 吸附、yOffset 上浮），常规字符恒 0。
            float glyphX = currentX + preparedText.xOffsets[glyphIndex];
            float glyphDrawY = drawY + resolveBaselineOffsetY(style, glyphCharSize)
                    + preparedText.yOffsets[glyphIndex];
            if (dropShadow) {
                collectGlyph(fontService, fontType, glyphReady, pageIndex, textureId, textureSize, slotX, slotY,
                        slotWidth, slotHeight, atlasBaselineX, atlasBaselineY, lineBaselineY, glyphSize, glyphFlags,
                        inkWidth, inkHeight, bearingX, bearingY,
                        glyphX + (float) FontConfig.shadowOffsetX * renderScale,
                        glyphDrawY + (float) FontConfig.shadowOffsetY * renderScale,
                        measuredWidth, glyphCharSize, baselineCharSize, renderScale, style,
                        darkenShadow(style.getColor()), false);
            }
            collectGlyph(fontService, fontType, glyphReady, pageIndex, textureId, textureSize, slotX, slotY,
                    slotWidth, slotHeight, atlasBaselineX, atlasBaselineY, lineBaselineY, glyphSize, glyphFlags,
                    inkWidth, inkHeight, bearingX, bearingY,
                    glyphX, glyphDrawY, measuredWidth, glyphCharSize, baselineCharSize, renderScale, style,
                    style.getColor(), true);
            currentX += measuredWidth;
        }
        // LaTeX 规则线（分数线/根号横线等）：随字形同帧收集，装饰线批次在字形页之后 flush
        for (int ruleIndex = 0; ruleIndex < preparedText.latexRules.length; ruleIndex++) {
            float[] rule = preparedText.latexRules[ruleIndex];
            fontService.getBatchRenderer().collectDecoration(x + rule[0], drawY + rule[1], rule[2], rule[3],
                    preparedText.latexRuleColors[ruleIndex]);
        }
        if (!isDeferredFlushScopeActive()) {
            flushCollectedBatches(fontService);
        }
        return (int) Math.ceil(currentX);
    }

    private PreparedText prepareGlyphs(FontRuntimeSettings settings, List<TextSegment> segments,
            TextLayoutService textLayoutService, float renderScale, float baseFontSizePx,
            GlyphRuntimeTablesView tables) {
        int resolvedBaseFontSizePx = Math.max(1, (int) baseFontSizePx);
        // 第一遍：计数 + 预布局 LaTeX 段（避免第二遍重复布局；缓存见 M4 LatexCache）
        int glyphCount = 0;
        MathBox[] latexBoxes = new MathBox[segments.size()];
        for (int s = 0; s < segments.size(); s++) {
            TextSegment segment = segments.get(s);
            if (segment.isLatex()) {
                MathBox box = layoutLatexSegment(segment, textLayoutService, resolvedBaseFontSizePx,
                        tables.getRuntimeVersion());
                latexBoxes[s] = box;
                for (GlyphElem elem : box.getGlyphs()) {
                    glyphCount += countRenderableCodepoints(elem.getText());
                }
                continue;
            }
            String segmentText = segment.getText();
            for (int index = 0; index < segmentText.length(); ) {
                int codepoint = segmentText.codePointAt(index);
                index += Character.charCount(codepoint);
                if (UnicodeTextClassifier.isRenderSkipped(codepoint)) {
                    continue;
                }
                glyphCount++;
            }
        }
        int[] renderCodepoints = new int[glyphCount];
        FontType[] fontTypes = new FontType[glyphCount];
        float[] measuredWidths = new float[glyphCount];
        TextStyle[] styles = new TextStyle[glyphCount];
        int[] fontSizePx = new int[glyphCount];
        float[] xOffsets = new float[glyphCount];
        float[] yOffsets = new float[glyphCount];
        List<float[]> latexRules = new ArrayList<float[]>();
        List<Integer> latexRuleColors = new ArrayList<Integer>();
        int maxFontSizePx = resolvedBaseFontSizePx;
        int glyphIndex = 0;
        for (int s = 0; s < segments.size(); s++) {
            TextSegment segment = segments.get(s);
            TextStyle style = segment.getStyle();
            String segmentText = segment.getText();
            int segmentFontSizePx = style.resolveEffectiveFontSizePx(resolvedBaseFontSizePx);
            if (segment.isLatex()) {
                fillLatexSegment(segment, latexBoxes[s], style, segmentFontSizePx, resolvedBaseFontSizePx,
                        textLayoutService, tables, renderScale, renderCodepoints, fontTypes, measuredWidths,
                        styles, fontSizePx, xOffsets, yOffsets, glyphIndex, latexRules, latexRuleColors);
                continue;
            }
            // 含组合标记/变体选择符的段落：AWT GPOS 定位（组合附加符逐层堆叠），
            // 常规段落返回 null 走零偏移快路径（零分配恒零数组）。
            float[] markPositions = textLayoutService.resolveMarkPositions(segmentText, style,
                    segmentFontSizePx);
            float segmentRunningAdvance = 0.0F;
            int codePointIndex = 0;
            for (int index = 0; index < segmentText.length(); ) {
                int codepoint = segmentText.codePointAt(index);
                int charCount = Character.charCount(codepoint);
                if (UnicodeTextClassifier.isRenderSkipped(codepoint)) {
                    index += charCount;
                    codePointIndex++;
                    continue;
                }
                double codepointWidth = resolveSegmentCodepointWidth(textLayoutService, codepoint, style,
                        segmentFontSizePx);
                int renderCodepoint = style.isRandomStyle()
                        ? resolveRandomStyleCodepoint(codepoint, style, codepointWidth, textLayoutService)
                        : resolveDisplayCodepoint(codepoint, style.getFontType(), tables);
                renderCodepoints[glyphIndex] = renderCodepoint;
                fontTypes[glyphIndex] = style.getFontType();
                // 推进宽度经 TextLayoutService.resolveAdvance 同源（测量/trim/wrap 共用口径，
                // 内部按 sup/sub 解析有效字号）；装饰线/高亮矩形随 advance 覆盖间隙，整体同乘 renderScale。
                measuredWidths[glyphIndex] = (float) textLayoutService.resolveAdvance(
                        codepoint, style, resolvedBaseFontSizePx) * renderScale;
                styles[glyphIndex] = style;
                fontSizePx[glyphIndex] = segmentFontSizePx;
                if (markPositions != null && codePointIndex * 2 + 1 < markPositions.length) {
                    // GPOS 位置换算到渲染坐标：xOffset = 锚点位置 - 段内 advance 累加位置；
                    // yOffset 为相对基线的纵向偏移（mark 上浮为负）。
                    xOffsets[glyphIndex] = markPositions[codePointIndex * 2] * renderScale
                            - segmentRunningAdvance;
                    yOffsets[glyphIndex] = markPositions[codePointIndex * 2 + 1] * renderScale;
                }
                segmentRunningAdvance += measuredWidths[glyphIndex];
                if (segmentFontSizePx > maxFontSizePx) {
                    maxFontSizePx = segmentFontSizePx;
                }
                glyphIndex++;
                codePointIndex++;
                index += charCount;
            }
        }
        float[][] ruleArray = latexRules.toArray(new float[latexRules.size()][]);
        int[] ruleColors = new int[latexRuleColors.size()];
        for (int r = 0; r < ruleColors.length; r++) {
            ruleColors[r] = latexRuleColors.get(r).intValue();
        }
        return new PreparedText(settings, renderCodepoints, fontTypes, measuredWidths, styles, fontSizePx,
                maxFontSizePx, resolvedBaseFontSizePx, xOffsets, yOffsets, ruleArray, ruleColors);
    }

    /**
     * 填充 LaTeX 段的字形与规则线：MathBox 元素按码点展开，x/y 偏移进 xOffsets/yOffsets，
     * 字号按 sizeScale 缩放；段尾推进差补偿到段内末字形，保证整体推进 = 盒宽。
     */
    private void fillLatexSegment(TextSegment segment, MathBox box, TextStyle style, int segmentFontSizePx,
            int resolvedBaseFontSizePx, TextLayoutService textLayoutService, GlyphRuntimeTablesView tables,
            float renderScale, int[] renderCodepoints, FontType[] fontTypes, float[] measuredWidths,
            TextStyle[] styles, int[] fontSizePx, float[] xOffsets, float[] yOffsets, int startGlyphIndex,
            List<float[]> latexRules, List<Integer> latexRuleColors) {
        int glyphIndex = startGlyphIndex;
        float segmentAdvanceSum = 0.0F;
        for (GlyphElem elem : box.getGlyphs()) {
            int glyphSizePx = Math.max(1, Math.round(segmentFontSizePx * elem.getSizeScale()));
            float elemInnerAdvance = 0.0F;
            String elemText = elem.getText();
            for (int i = 0; i < elemText.length(); ) {
                int codepoint = elemText.codePointAt(i);
                int charCount = Character.charCount(codepoint);
                if (UnicodeTextClassifier.isRenderSkipped(codepoint)) {
                    i += charCount;
                    continue;
                }
                renderCodepoints[glyphIndex] = resolveDisplayCodepoint(codepoint, style.getFontType(), tables);
                fontTypes[glyphIndex] = style.getFontType();
                double advance = textLayoutService.resolveAdvance(codepoint, style, resolvedBaseFontSizePx)
                        * elem.getSizeScale();
                measuredWidths[glyphIndex] = (float) advance * renderScale;
                styles[glyphIndex] = style;
                fontSizePx[glyphIndex] = glyphSizePx;
                xOffsets[glyphIndex] = (elem.getX() + elemInnerAdvance) * renderScale;
                yOffsets[glyphIndex] = elem.getY() * renderScale;
                segmentAdvanceSum += (float) advance;
                glyphIndex++;
                elemInnerAdvance += (float) advance;
                i += charCount;
            }
        }
        // 段尾推进差补偿（盒宽 - 段内 advance 和；布局度量与渲染度量口径差异的兜底）
        float tail = (box.getWidth() - segmentAdvanceSum) * renderScale;
        if (glyphIndex > startGlyphIndex) {
            measuredWidths[glyphIndex - 1] += tail;
        }
        for (RuleElem rule : box.getRules()) {
            latexRules.add(new float[] { rule.getX() * renderScale, rule.getY() * renderScale,
                    rule.getWidth() * renderScale, rule.getThickness() * renderScale });
            latexRuleColors.add(Integer.valueOf(style.getColor()));
        }
    }

    /** 布局 LaTeX 段（经 LatexCache 缓存；与测量侧 TextLayoutService.measureLatexWidth 同口径）。 */
    private MathBox layoutLatexSegment(TextSegment segment, TextLayoutService textLayoutService,
            int baseFontSizePx, int runtimeVersion) {
        return LatexCache.getInstance().getOrLayout(segment.getLatexSource(), baseFontSizePx, runtimeVersion,
                segment.getStyle().getFontType(), MATH_LAYOUT,
                textLayoutService.createMathMetrics(segment.getStyle(), baseFontSizePx));
    }

    /** 文本内可渲染码点计数（跳过零宽/剥离类，与 glyph 收集同口径）。 */
    private static int countRenderableCodepoints(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); ) {
            int codepoint = text.codePointAt(i);
            i += Character.charCount(codepoint);
            if (!UnicodeTextClassifier.isRenderSkipped(codepoint)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 解析码点的显示用码点：
     * <ul>
     *   <li>tab → 空格字形（列宽由测量层给 8×space）；</li>
     *   <li>Cc 控制字符 → Control Pictures/U+FFFD 可见映射（CSS3+ 口径）；</li>
     *   <li>组合标记缺 glyph → U+FFFD 替换符（标准 .notdef 降级，不再静默跳过）。</li>
     * </ul>
     *
     * @param codepoint 原始码点
     * @param fontType  字重
     * @param tables    glyph 运行时表视图
     * @return 显示用码点
     */
    private static int resolveDisplayCodepoint(int codepoint, FontType fontType, GlyphRuntimeTablesView tables) {
        UnicodeTextClassifier.CharClass cls = UnicodeTextClassifier.classify(codepoint);
        if (cls == UnicodeTextClassifier.CharClass.CONTROL) {
            return UnicodeTextClassifier.controlPictureCodepoint(codepoint);
        }
        if (codepoint == '\t') {
            return ' ';
        }
        if (cls == UnicodeTextClassifier.CharClass.COMBINING_MARK && isGlyphMissing(tables, codepoint, fontType)) {
            return 0xFFFD;
        }
        return codepoint;
    }

    /**
     * 判断码点在运行时表中是否缺位图（NO_BITMAP 或 slot/页无效）；生成中（NOT_READY）不算缺失。
     *
     * @param tables    glyph 运行时表视图
     * @param codepoint 码点
     * @param fontType  字重
     * @return true 表示无可用位图
     */
    private static boolean isGlyphMissing(GlyphRuntimeTablesView tables, int codepoint, FontType fontType) {
        if (!GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            return true;
        }
        int packedLocation = tables.getPackedLocation(codepoint, fontType);
        if (packedLocation == GlyphRuntimeTables.LOCATION_NOT_READY) {
            return false;
        }
        if (packedLocation == GlyphRuntimeTables.LOCATION_NO_BITMAP) {
            return true;
        }
        int pageIndex = GlyphRuntimeTables.unpackPageIndex(packedLocation);
        int slotIndex = GlyphRuntimeTables.unpackSlotIndex(packedLocation);
        return pageIndex < 0 || pageIndex >= tables.getPageCount(fontType) || slotIndex < 0
                || tables.getPageTextureIdSnapshot(fontType, pageIndex) <= 0
                || tables.getSlotWidth(codepoint, fontType) <= 0
                || tables.getSlotHeight(codepoint, fontType) <= 0;
    }

    /**
     * 解析段内码点的推进宽度（settings.charSize 坐标系 × 段字号比例）。
     *
     * <p>必须统一走带字号测量，不得按"段字号==基准字号"回落到无字号版——px 路径的调用方
     * 基准字号不等于 settings.charSize，回落会拿到引擎坐标系的原始宽（真机回归：
     * 横排文字挤在一起）。</p>
     *
     * @param textLayoutService 字体布局服务
     * @param codepoint         码点
     * @param style             段样式
     * @param segmentFontSizePx 段有效字号（>=1）
     * @return settings.charSize 坐标系下的推进宽度（已按段字号比例缩放）
     */
    static double resolveSegmentCodepointWidth(TextLayoutService textLayoutService, int codepoint,
            TextStyle style, int segmentFontSizePx) {
        return textLayoutService.getCodepointWidth(codepoint, style, segmentFontSizePx);
    }

    /**
     * 解析单个 glyph 的渲染尺寸：有效字号（绝对 UI 像素语义）乘以调用方缩放。
     *
     * @param renderScale     调用方缩放（px 路径恒 1.0）
     * @param glyphFontSizePx glyph 所在段落的有效字号（>=1）
     * @return glyph 渲染尺寸
     */
    static float resolveGlyphCharSize(float renderScale, int glyphFontSizePx) {
        return Math.max(1, glyphFontSizePx) * Math.max(0.01F, renderScale);
    }

    /**
     * 解析上/下标的基线偏移：上标抬升、下标下沉，em 相对 glyph 自身渲染尺寸。
     *
     * @param style        glyph 样式
     * @param glyphCharSize glyph 渲染尺寸
     * @return 相对行 em-box 顶的 Y 偏移（上标为负）
     */
    static float resolveBaselineOffsetY(TextStyle style, float glyphCharSize) {
        if (style.isSuperscript()) {
            return -TextStyle.SUP_RAISE_EM * glyphCharSize;
        }
        if (style.isSubscript()) {
            return TextStyle.SUB_DROP_EM * glyphCharSize;
        }
        return 0.0F;
    }

    /**
     * 解析整行的基线渲染尺寸：行内最大有效字号乘以调用方缩放，使大字 ascender
     * 完整落在行框内（行高与基线的 ascent 模型对齐），小字共享同一基线。
     *
     * @param renderScale    调用方缩放（px 路径恒 1.0）
     * @param maxFontSizePx  行内最大有效字号（>=1）
     * @return 基线换算用的渲染尺寸
     */
    static float resolveBaselineCharSize(float renderScale, int maxFontSizePx) {
        return Math.max(1, maxFontSizePx) * Math.max(0.01F, renderScale);
    }

    private void submitVisibleDemandIfNeeded(FontService fontService, GlyphRuntimeTablesView tables,
            int runtimeVersion, int glyphSize, int codepoint, FontType fontType, Set<Long> submittedDemands) {
        if (!requiresGlyphDemand(tables, codepoint, fontType)
                || !submittedDemands.add(Long.valueOf(packDemandKey(codepoint, fontType)))) {
            return;
        }
        fontService.submitGlyphGeneration(new GlyphGenerationTask(runtimeVersion, codepoint, fontType, glyphSize,
                GlyphGenerationPriority.HIGH));
    }

    private boolean requiresGlyphDemand(GlyphRuntimeTablesView tables, int codepoint, FontType fontType) {
        if (!GlyphRuntimeTables.isValidCodepoint(codepoint)) {
            return false;
        }
        int packedLocation = tables.getPackedLocation(codepoint, fontType);
        if (packedLocation == GlyphRuntimeTables.LOCATION_NO_BITMAP) {
            return false;
        }
        if (packedLocation == GlyphRuntimeTables.LOCATION_NOT_READY) {
            return true;
        }
        int pageIndex = GlyphRuntimeTables.unpackPageIndex(packedLocation);
        int slotIndex = GlyphRuntimeTables.unpackSlotIndex(packedLocation);
        return pageIndex < 0 || pageIndex >= tables.getPageCount(fontType) || slotIndex < 0
                || tables.getPageTextureIdSnapshot(fontType, pageIndex) <= 0
                || tables.getSlotWidth(codepoint, fontType) <= 0
                || tables.getSlotHeight(codepoint, fontType) <= 0;
    }

    private long packDemandKey(int codepoint, FontType fontType) {
        return ((long) codepoint << 1) | (fontType == FontType.BOLD ? 1L : 0L);
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
            float baseCharSize, float renderScale, TextStyle style, int renderColor, boolean withMarkBackground) {
        boolean hasGlyphQuad = glyphReady && textureId > 0 && slotWidth > 0 && slotHeight > 0
                && inkWidth > 0 && inkHeight > 0;
        if (hasGlyphQuad || style.isUnderline() || style.isStrikethrough() || style.getMarkColor() != 0) {
            markDeferredFlushDirtyIfNeeded();
        }
        if (hasGlyphQuad) {
            fontService.getBatchRenderer().collectBaselineAlignedGlyph(fontType, pageIndex, textureId, textureSize,
                    slotX, slotY, slotWidth, slotHeight, atlasBaselineX, atlasBaselineY, lineBaselineY, glyphSize,
                    inkWidth, inkHeight, bearingX, bearingY, currentX, drawY, charSize, renderColor, style.isItalic(),
                    glyphFlags, baseCharSize);
        }
        collectDecorations(fontService, currentX, drawY, measuredWidth, charSize, baseCharSize, renderScale,
                style, renderColor, withMarkBackground);
    }

    private void collectDecorations(FontService fontService, float currentX, float drawY, float width, float charSize,
            float baseCharSize, float renderScale, TextStyle style, int color, boolean withMarkBackground) {
        if (withMarkBackground && style.getMarkColor() != 0) {
            // 行内高亮矩形覆盖整行 em-box，垫在字形之下（独立背景批次先渲染）；
            // 阴影 pass 不收集，避免偏移后的第二层矩形叠影。
            fontService.getBatchRenderer().collectMarkBackground(currentX, drawY, width, baseCharSize,
                    style.getMarkColor());
        }
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

    private void initializeForRender(final FontService fontService) {
        if (fontService.isInitialized()) {
            return;
        }
        if (isDeferredFlushScopeActive()) {
            fontService.initialize();
            return;
        }
        renderStateGuard.run(new Runnable() {
            @Override
            public void run() {
                fontService.initialize();
            }
        }, false);
    }

    private interface DrawStringTask {

        int run();
    }

    private static final class PreparedText {

        private final FontRuntimeSettings settings;
        private final int[] renderCodepoints;
        private final FontType[] fontTypes;
        private final float[] measuredWidths;
        private final TextStyle[] styles;
        private final int[] fontSizePx;
        private final int maxFontSizePx;
        private final int baseFontSizePx;
        /** GPOS mark 定位横向调整量（相对 advance 累加位置；无 mark 段落恒 0）。 */
        private final float[] xOffsets;
        /** GPOS mark 定位纵向偏移（相对 drawY，向上为负；无 mark 段落恒 0）。 */
        private final float[] yOffsets;
        /** LaTeX 规则线（分数线/根号线等），每条 {x, y, w, t} 已乘 renderScale（x 相对绘制起点、y 相对 drawY）。 */
        private final float[][] latexRules;
        /** 每条规则的颜色（ARGB，继承所在公式段样式）。 */
        private final int[] latexRuleColors;

        private PreparedText(FontRuntimeSettings settings, int[] renderCodepoints, FontType[] fontTypes,
                float[] measuredWidths, TextStyle[] styles, int[] fontSizePx, int maxFontSizePx,
                int baseFontSizePx, float[] xOffsets, float[] yOffsets, float[][] latexRules,
                int[] latexRuleColors) {
            this.settings = settings;
            this.renderCodepoints = renderCodepoints;
            this.fontTypes = fontTypes;
            this.measuredWidths = measuredWidths;
            this.styles = styles;
            this.fontSizePx = fontSizePx;
            this.maxFontSizePx = maxFontSizePx;
            this.baseFontSizePx = baseFontSizePx;
            this.xOffsets = xOffsets;
            this.yOffsets = yOffsets;
            this.latexRules = latexRules;
            this.latexRuleColors = latexRuleColors;
        }

        private boolean isEmpty() {
            return renderCodepoints.length == 0 && latexRules.length == 0;
        }

        private int size() {
            return renderCodepoints.length;
        }

        private static PreparedText empty(FontRuntimeSettings settings) {
            return new PreparedText(settings, new int[0], new FontType[0], new float[0], new TextStyle[0],
                    new int[0], (int) settings.getCharSize(), (int) settings.getCharSize(),
                    new float[0], new float[0], new float[0][0], new int[0]);
        }
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
