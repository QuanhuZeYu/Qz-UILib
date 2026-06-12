package club.heiqi.uilib.ui.paint;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentEffectType;
import club.heiqi.uilib.ui.style.values.UiBackgroundImage;
import club.heiqi.uilib.ui.style.values.UiBoxShadow;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.values.UiSurfaceStyle;
import club.heiqi.uilib.ui.style.values.UiTransform;
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
    private final float paintContextOpacity;
    private UiTransform transform;

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
