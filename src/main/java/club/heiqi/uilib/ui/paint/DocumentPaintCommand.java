package club.heiqi.uilib.ui.paint;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * HTML-like 中立绘制命令。
 *
 * <p>该命令只描述“画什么”和“画在哪里”，不直接绑定 `UiRenderContext` 或 OpenGL。</p>
 */
public final class DocumentPaintCommand {

    private final DocumentPaintCommandType type;
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
        this.type = Objects.requireNonNull(type, "type");
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
    }

    public DocumentPaintCommandType getType() {
        return type;
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
}
