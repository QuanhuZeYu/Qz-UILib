package club.heiqi.uilib.ui.paint;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentEffectType;

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
    private final String text;
    private final DocumentCustomRenderer customRenderer;
    private final int backdropBlurRadius;
    private final float backdropSaturation;
    private final float paintContextOpacity;

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius, null, null, 0, 1.0F);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, String text) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius, text, null, 0, 1.0F);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, String text, DocumentCustomRenderer customRenderer) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius, text, customRenderer, 0, 1.0F);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, String text, DocumentCustomRenderer customRenderer,
            int backdropBlurRadius, float backdropSaturation) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius, text, customRenderer,
                backdropBlurRadius, backdropSaturation, 1.0F);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, String text, DocumentCustomRenderer customRenderer,
            int backdropBlurRadius, float backdropSaturation, float paintContextOpacity) {
        this(type, element, left, top, right, bottom, color, borderWidth, borderRadius, text, customRenderer,
                backdropBlurRadius, backdropSaturation, paintContextOpacity, null);
    }

    DocumentPaintCommand(DocumentPaintCommandType type, ElementNode element, int left, int top, int right, int bottom,
            int color, int borderWidth, int borderRadius, String text, DocumentCustomRenderer customRenderer,
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
        this.text = text == null ? "" : text;
        this.customRenderer = customRenderer;
        this.backdropBlurRadius = Math.max(0, backdropBlurRadius);
        this.backdropSaturation = Math.max(0.0F, backdropSaturation);
        this.paintContextOpacity = Math.max(0.0F, Math.min(1.0F, paintContextOpacity));
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

    public String getText() {
        return text;
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
}
