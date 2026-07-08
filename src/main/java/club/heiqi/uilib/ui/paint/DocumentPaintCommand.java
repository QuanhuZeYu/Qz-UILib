package club.heiqi.uilib.ui.paint;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentEffectType;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.values.UiBackgroundImage;
import club.heiqi.uilib.ui.style.values.UiBoxShadow;
import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.base.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.base.values.UiSurfaceStyle;
import club.heiqi.uilib.ui.base.values.UiTransform;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureStyle;

/**
 * HTML-like 中立绘制命令。
 *
 * <p>该命令只描述“画什么”和“画在哪里”，不直接绑定 `UiRenderContext` 或 OpenGL。</p>
 */
public final class DocumentPaintCommand {

    private final DocumentPaintCommandType type;
    private final DocumentEffectType effectType;
    private final ElementNode element;
    private final int left;
    private final int top;
    private final int right;
    private final int bottom;
    private final int color;
    private final int borderWidth;
    private final int borderRadius;
    private final int cornerMask;
    private final UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii;
    private final String text;
    private final TextContentMode textContentMode;
    private final UiFontWeight fontWeight;
    private final UiFontStyle fontStyle;
    private final TextMeasureStyle textMeasureStyle;
    private UiBoxShadow boxShadow;
    private UiBackgroundImage backgroundImage;
    private final DocumentCustomRenderer customRenderer;
    private final int backdropBlurRadius;
    private final float backdropSaturation;
    private float paintContextOpacity;
    private UiTransform transform;
    private ComputedStyle elementStyle;
    private DocumentCustomRenderBounds customRenderBounds;
    private boolean clipDeferred;
    private DocumentScrollbarThumbReplay thumbReplay;

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius, null,
                TextContentMode.UILIB_RAW, UiFontWeight.NORMAL, UiFontStyle.NORMAL, null, 0, 1.0F, 1.0F);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, String text) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius, text,
                TextContentMode.UILIB_RAW, UiFontWeight.NORMAL, UiFontStyle.NORMAL, null, 0, 1.0F, 1.0F);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, String text, DocumentCustomRenderer customRenderer) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius, text,
                TextContentMode.UILIB_RAW, UiFontWeight.NORMAL, UiFontStyle.NORMAL, customRenderer, 0, 1.0F, 1.0F);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, String text, DocumentCustomRenderer customRenderer,
            int backdropBlurRadius, float backdropSaturation) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius, text,
                TextContentMode.UILIB_RAW, UiFontWeight.NORMAL, UiFontStyle.NORMAL, customRenderer, backdropBlurRadius,
                backdropSaturation, 1.0F);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, String text, DocumentCustomRenderer customRenderer,
            int backdropBlurRadius, float backdropSaturation, float paintContextOpacity,
            DocumentEffectType effectType) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius, text,
                TextContentMode.UILIB_RAW, UiFontWeight.NORMAL, UiFontStyle.NORMAL, customRenderer,
                backdropBlurRadius, backdropSaturation,
                paintContextOpacity, effectType);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, int cornerMask, String text,
            DocumentCustomRenderer customRenderer, int backdropBlurRadius, float backdropSaturation,
            float paintContextOpacity, DocumentEffectType effectType) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius, cornerMask, text,
                TextContentMode.UILIB_RAW, UiFontWeight.NORMAL, UiFontStyle.NORMAL, customRenderer,
                backdropBlurRadius, backdropSaturation,
                paintContextOpacity, effectType);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, String text, TextContentMode textContentMode,
            DocumentCustomRenderer customRenderer, int backdropBlurRadius, float backdropSaturation,
            float paintContextOpacity) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius, text, textContentMode,
                UiFontWeight.NORMAL, UiFontStyle.NORMAL, customRenderer, backdropBlurRadius, backdropSaturation,
                paintContextOpacity, null);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, String text, TextContentMode textContentMode,
            UiFontWeight fontWeight, UiFontStyle fontStyle,
            DocumentCustomRenderer customRenderer, int backdropBlurRadius, float backdropSaturation,
            float paintContextOpacity,
            DocumentEffectType effectType) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius,
                UiBorderRadiusResolver.ResolvedCornerRadii.uniform(Math.max(0, borderRadius)), UiSurfaceStyle.CORNER_ALL,
                text, textContentMode, fontWeight, fontStyle, null, customRenderer, backdropBlurRadius,
                backdropSaturation, paintContextOpacity, effectType);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, int cornerMask, String text, TextContentMode textContentMode,
            DocumentCustomRenderer customRenderer, int backdropBlurRadius, float backdropSaturation,
            float paintContextOpacity, DocumentEffectType effectType) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius,
                UiBorderRadiusResolver.ResolvedCornerRadii.uniform(Math.max(0, borderRadius)), cornerMask, text,
                textContentMode, UiFontWeight.NORMAL, UiFontStyle.NORMAL, null, customRenderer, backdropBlurRadius,
                backdropSaturation, paintContextOpacity, effectType);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, int cornerMask, String text, TextContentMode textContentMode,
            UiFontWeight fontWeight, UiFontStyle fontStyle, DocumentCustomRenderer customRenderer,
            int backdropBlurRadius, float backdropSaturation, float paintContextOpacity,
            DocumentEffectType effectType) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius,
                UiBorderRadiusResolver.ResolvedCornerRadii.uniform(Math.max(0, borderRadius)), cornerMask, text,
                textContentMode, fontWeight, fontStyle, null, customRenderer, backdropBlurRadius, backdropSaturation,
                paintContextOpacity, effectType);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, String text, TextContentMode textContentMode,
            UiFontWeight fontWeight, UiFontStyle fontStyle, TextMeasureStyle textMeasureStyle,
            DocumentCustomRenderer customRenderer, int backdropBlurRadius, float backdropSaturation,
            float paintContextOpacity) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius,
                UiBorderRadiusResolver.ResolvedCornerRadii.uniform(Math.max(0, borderRadius)), UiSurfaceStyle.CORNER_ALL,
                text, textContentMode, fontWeight, fontStyle, textMeasureStyle, customRenderer, backdropBlurRadius,
                backdropSaturation, paintContextOpacity, null);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, String text, TextContentMode textContentMode,
            UiFontWeight fontWeight, UiFontStyle fontStyle, DocumentCustomRenderer customRenderer,
            int backdropBlurRadius, float backdropSaturation, float paintContextOpacity) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius, text, textContentMode,
                fontWeight, fontStyle, customRenderer, backdropBlurRadius, backdropSaturation, paintContextOpacity,
                null);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
        this(type, element, left, top, right, bottom, color, borderWidth,
                resolveLegacyBorderRadius(cornerRadii), cornerRadii, UiSurfaceStyle.CORNER_ALL, null,
                TextContentMode.UILIB_RAW, UiFontWeight.NORMAL, UiFontStyle.NORMAL, null, null, 0, 1.0F, 1.0F,
                null);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, UiBackgroundImage backgroundImage) {
        this(type, element, left, top, right, bottom, 0, 0, cornerRadii);
        if (type != DocumentPaintCommandType.BACKGROUND_IMAGE) {
            throw new IllegalArgumentException("backgroundImage command type expected");
        }
        this.backgroundImage = Objects.requireNonNull(backgroundImage, "backgroundImage");
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii,
            UiBoxShadow boxShadow) {
        this(type, element, left, top, right, bottom, color, borderWidth,
                resolveLegacyBorderRadius(cornerRadii), cornerRadii, UiSurfaceStyle.CORNER_ALL, null,
                TextContentMode.UILIB_RAW, UiFontWeight.NORMAL, UiFontStyle.NORMAL, null, null, 0, 1.0F, 1.0F,
                null);
        if (type != DocumentPaintCommandType.BOX_SHADOW && type != DocumentPaintCommandType.BOX_SHADOW_INSET) {
            throw new IllegalArgumentException("boxShadow command type expected");
        }
        this.boxShadow = Objects.requireNonNull(boxShadow, "boxShadow");
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            UiTransform transform) {
        this(type, element, left, top, right, bottom, 0, 0, 0);
        if (type != DocumentPaintCommandType.TRANSFORM_START && type != DocumentPaintCommandType.TRANSFORM_END) {
            throw new IllegalArgumentException("transform command type expected");
        }
        this.transform = transform == null ? UiTransform.identity() : transform;
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, int cornerMask,
            String text, DocumentCustomRenderer customRenderer, int backdropBlurRadius, float backdropSaturation,
            float paintContextOpacity, DocumentEffectType effectType) {
        this(type, element, left, top, right, bottom, color, borderWidth,
                resolveLegacyBorderRadius(cornerRadii), cornerRadii, cornerMask, text, TextContentMode.UILIB_RAW,
                UiFontWeight.NORMAL, UiFontStyle.NORMAL, null, customRenderer, backdropBlurRadius, backdropSaturation,
                paintContextOpacity, effectType);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii,
            int cornerMask, String text, TextContentMode textContentMode, UiFontWeight fontWeight,
            UiFontStyle fontStyle, TextMeasureStyle textMeasureStyle, DocumentCustomRenderer customRenderer,
            int backdropBlurRadius, float backdropSaturation, float paintContextOpacity,
            DocumentEffectType effectType) {
        this.type = Objects.requireNonNull(type, "type");
        this.effectType = resolveEffectType(this.type, effectType);
        this.element = Objects.requireNonNull(element, "element");
        this.left = left;
        this.top = top;
        this.right = Math.max(left, right);
        this.bottom = Math.max(top, bottom);
        this.color = color;
        this.borderWidth = Math.max(0, borderWidth);
        this.borderRadius = Math.max(0, borderRadius);
        this.cornerMask = cornerMask & UiSurfaceStyle.CORNER_ALL;
        this.cornerRadii = cornerRadii == null ? UiBorderRadiusResolver.ResolvedCornerRadii.uniform(this.borderRadius)
                : cornerRadii;
        this.text = text == null ? "" : text;
        this.textContentMode = textContentMode == null ? TextContentMode.UILIB_RAW : textContentMode;
        this.fontWeight = fontWeight == null ? UiFontWeight.NORMAL : fontWeight;
        this.fontStyle = fontStyle == null ? UiFontStyle.NORMAL : fontStyle;
        this.textMeasureStyle = textMeasureStyle == null
                ? TextMeasureStyle.DEFAULT.withTextContentMode(this.textContentMode).withFontStyle(this.fontWeight,
                        this.fontStyle)
                : textMeasureStyle.withTextContentMode(this.textContentMode).withFontStyle(this.fontWeight,
                        this.fontStyle);
        this.customRenderer = customRenderer;
        this.backdropBlurRadius = Math.max(0, backdropBlurRadius);
        this.backdropSaturation = Math.max(0.0F, backdropSaturation);
        this.paintContextOpacity = Math.max(0.0F, Math.min(1.0F, paintContextOpacity));
        this.boxShadow = null;
        this.transform = null;
    }

    public DocumentPaintCommandType getType() {
        return type;
    }

    /**
     * 返回该命令对应的显式效果类型；普通绘制命令返回 null。
     *
     * @return 效果类型
     */
    public DocumentEffectType getEffectType() {
        return effectType;
    }

    public ElementNode getElement() {
        return element;
    }

    public int getLeft() {
        return left;
    }

    public int getTop() {
        return top;
    }

    public int getRight() {
        return right;
    }

    public int getBottom() {
        return bottom;
    }

    public int getWidth() {
        return right - left;
    }

    public int getHeight() {
        return bottom - top;
    }

    public int getColor() {
        return color;
    }

    public int getBorderWidth() {
        return borderWidth;
    }

    public int getBorderRadius() {
        return borderRadius;
    }

    /**
     * 返回四角圆角值。
     *
     * @return 四角圆角；旧单值构造器会返回对应的统一圆角
     */
    public UiBorderRadiusResolver.ResolvedCornerRadii getCornerRadii() {
        return cornerRadii;
    }

    /**
     * 返回参与圆角绘制的角位掩码。
     *
     * @return 圆角位掩码
     */
    public int getCornerMask() {
        return cornerMask;
    }

    public String getText() {
        return text;
    }

    /**
     * 返回文本命令的解析模式。
     *
     * @return 文本内容解析模式
     */
    public TextContentMode getTextContentMode() {
        return textContentMode;
    }

    public UiFontWeight getFontWeight() {
        return fontWeight;
    }

    public UiFontStyle getFontStyle() {
        return fontStyle;
    }

    /**
     * 返回文本绘制使用的布局期样式快照。
     *
     * @return 文本样式快照
     */
    public TextMeasureStyle getTextMeasureStyle() {
        return textMeasureStyle;
    }

    /**
     * 返回背景图值。
     *
     * @return 背景图值；非背景图命令返回 null
     */
    public UiBackgroundImage getBackgroundImage() {
        return backgroundImage;
    }

    /**
     * 返回 box-shadow 命令携带的阴影值。
     *
     * @return 阴影值；非 box-shadow 命令返回 null
     */
    public UiBoxShadow getBoxShadow() {
        return boxShadow;
    }

    public DocumentCustomRenderer getCustomRenderer() {
        return customRenderer;
    }

    public int getBackdropBlurRadius() {
        return backdropBlurRadius;
    }

    public float getBackdropSaturation() {
        return backdropSaturation;
    }

    public float getPaintContextOpacity() {
        return paintContextOpacity;
    }

    /**
     * 返回 transform 命令携带的变换值。
     *
     * @return transform 值；非 transform 命令返回 null
     */
    public UiTransform getTransform() {
        return transform;
    }

    /**
     * 就地更新 {@code TRANSFORM_START}/{@code TRANSFORM_END} 命令的变换值。
     *
     * <p>供 composite-only 回放路径使用：当只有 transform/opacity 变化（compositeVersion 变、paintVersion
     * 未变）且命令结构未变时，跳过整批命令重建，仅就地刷新变换值。transform 不影响命令坐标（坐标是变换前
     * 的盒坐标，矩阵在回放期由 {@link DocumentPaintRenderer} 叠加），故只需替换该字段。</p>
     *
     * @param transform 新的变换值；为 null 时按 identity 处理
     */
    void updateTransform(UiTransform transform) {
        this.transform = transform == null ? UiTransform.identity() : transform;
    }

    /**
     * 就地更新 {@code PAINT_CONTEXT_START} 命令的局部 opacity。
     *
     * <p>供 composite-only 回放路径使用：opacity 经回放期 paint context 的 {@code fallbackOpacity} 应用，
     * 未烘焙进命令颜色（见 {@code DocumentPaintEngine} 中 {@code boxOpacity} 的计算），故只需替换该字段。</p>
     *
     * @param paintContextOpacity 新的局部 opacity，钳制到 [0, 1]
     */
    void updatePaintContextOpacity(float paintContextOpacity) {
        this.paintContextOpacity = Math.max(0.0F, Math.min(1.0F, paintContextOpacity));
    }

    /**
     * 返回构建期固化的元素计算样式。
     *
     * <p>绘制命令缓存与布局/绘制版本号绑定，命令存活期间元素样式不变，因此可在构建期固化、
     * 回放期直接读取，避免每帧对每条命令重算 computed style。</p>
     *
     * @return 元素计算样式；未固化时返回 null
     */
    public ComputedStyle getElementStyle() {
        return elementStyle;
    }

    /**
     * 固化该命令对应元素的计算样式，供回放期免重算读取。
     *
     * @param elementStyle 元素计算样式
     * @return 当前命令
     */
    DocumentPaintCommand withElementStyle(ComputedStyle elementStyle) {
        this.elementStyle = elementStyle;
        return this;
    }

    /**
     * 返回构建期固化的自定义渲染器边界快照。
     *
     * <p>仅 CUSTOM 命令会在构建期固化该快照，供回放期 {@link DocumentCustomRenderSurface} 免实时查询读取
     * 视口/内容/图层文档坐标边界与滚动偏移。</p>
     *
     * @return 自定义渲染器边界快照；未固化时返回 null
     */
    DocumentCustomRenderBounds getCustomRenderBounds() {
        return customRenderBounds;
    }

    /**
     * 固化该 CUSTOM 命令的自定义渲染器边界快照，供回放期免实时查询读取。
     *
     * @param customRenderBounds 自定义渲染器边界快照
     * @return 当前命令
     */
    DocumentPaintCommand withCustomRenderBounds(DocumentCustomRenderBounds customRenderBounds) {
        this.customRenderBounds = customRenderBounds;
        return this;
    }

    /**
     * 返回该 TEXT 命令是否把可见性裁剪推迟到回放期。
     *
     * <p>免重建滚动容器子树内的文本命令坐标是与滚动无关的内容坐标，构建期无法按当前滚动位置做视口剔除，
     * 否则滚动后进入视口的文本会缺失。这类命令在构建期全量生成并打此标记，由回放期按实时反算的可见窗口
     * 做整 run 剔除与超长文本横向裁切。</p>
     *
     * @return 是否回放期裁剪
     */
    boolean isClipDeferred() {
        return clipDeferred;
    }

    /**
     * 标记该 TEXT 命令把可见性裁剪推迟到回放期。
     *
     * @return 当前命令
     */
    DocumentPaintCommand withClipDeferred() {
        this.clipDeferred = true;
        return this;
    }

    /**
     * 返回该 SCROLLBAR_THUMB 命令的回放期实时重算描述。
     *
     * <p>免重建滚动容器滚动时 thumb 命令坐标不随滚动平移（track 基于视口框，落在 SCROLL_OFFSET 作用域外），
     * 但 thumb 在轨道内的位置由当前滚动偏移决定，必须随滚动跟手。该描述固化构建期轨道几何与可滚范围，
     * 回放期按实时滚动偏移重算 thumb 主轴起点。</p>
     *
     * @return thumb 回放期重算描述；非 thumb 命令或未附加时返回 null
     */
    DocumentScrollbarThumbReplay getThumbReplay() {
        return thumbReplay;
    }

    /**
     * 给该 SCROLLBAR_THUMB 命令附加回放期实时重算描述。
     *
     * @param thumbReplay thumb 回放期重算描述
     * @return 当前命令
     */
    DocumentPaintCommand withThumbReplay(DocumentScrollbarThumbReplay thumbReplay) {
        this.thumbReplay = thumbReplay;
        return this;
    }

    private static DocumentEffectType resolveEffectType(DocumentPaintCommandType type, DocumentEffectType effectType) {
        if (effectType == null) {
            return getDefaultEffectType(type);
        }
        DocumentEffectType defaultEffectType = getDefaultEffectType(type);
        if (defaultEffectType != effectType) {
            throw new IllegalArgumentException("effectType does not match command type: " + type);
        }
        return effectType;
    }

    private static DocumentEffectType getDefaultEffectType(DocumentPaintCommandType type) {
        if (type == DocumentPaintCommandType.PAINT_CONTEXT_START
                || type == DocumentPaintCommandType.PAINT_CONTEXT_END) {
            return DocumentEffectType.PAINT_CONTEXT;
        }
        if (type == DocumentPaintCommandType.BACKDROP_FILTER) {
            return DocumentEffectType.BACKDROP_FILTER;
        }
        if (type == DocumentPaintCommandType.CLIP_START || type == DocumentPaintCommandType.CLIP_END) {
            return DocumentEffectType.OVERFLOW_CLIP;
        }
        return null;
    }

    private static int resolveLegacyBorderRadius(UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
        return cornerRadii != null && cornerRadii.isUniform() ? cornerRadii.getUniformRadius() : 0;
    }
}
