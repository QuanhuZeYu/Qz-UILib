package club.heiqi.uilib.ui.scene.image;

import java.util.Objects;
import club.heiqi.uilib.ui.scene.layout.Size;

/** 图像声明：尺寸沿Size，未知来源占位不提供宽高比；document默认只缩大图。 */
public final class ImageOptions {
    public enum Fit {
        CONTAIN,
        COVER,
        STRETCH,
        NATIVE
    }

    public final Size width, height;
    public final Fit fit;
    public final float alignX, alignY;
    public final int placeholderWidth, placeholderHeight;
    public final String alt;
    public final boolean document;
    public final boolean quantity, durability, tooltip;

    public ImageOptions(
            Size width,
            Size height,
            Fit fit,
            float alignX,
            float alignY,
            int placeholderWidth,
            int placeholderHeight,
            String alt,
            boolean document) {
        this(
                width,
                height,
                fit,
                alignX,
                alignY,
                placeholderWidth,
                placeholderHeight,
                alt,
                document,
                false,
                false,
                false);
    }

    private ImageOptions(
            Size width,
            Size height,
            Fit fit,
            float alignX,
            float alignY,
            int placeholderWidth,
            int placeholderHeight,
            String alt,
            boolean document,
            boolean quantity,
            boolean durability,
            boolean tooltip) {
        this.quantity = quantity;
        this.durability = durability;
        this.tooltip = tooltip;
        this.width = Objects.requireNonNull(width, "width");
        this.height = Objects.requireNonNull(height, "height");
        this.fit = Objects.requireNonNull(fit, "fit");
        if (!Float.isFinite(alignX)
                || !Float.isFinite(alignY)
                || alignX < 0
                || alignX > 1
                || alignY < 0
                || alignY > 1
                || placeholderWidth < 0
                || placeholderHeight < 0) throw new IllegalArgumentException("无效图像占位/对齐");
        this.alignX = alignX;
        this.alignY = alignY;
        this.placeholderWidth = placeholderWidth;
        this.placeholderHeight = placeholderHeight;
        this.alt = Objects.requireNonNull(alt, "alt");
        this.document = document;
    }

    public static ImageOptions defaults() {
        return new ImageOptions(
                Size.natural(), Size.natural(), Fit.CONTAIN, .5f, .5f, 32, 32, "", false);
    }

    public ImageOptions size(Size width, Size height) {
        return new ImageOptions(
                width,
                height,
                fit,
                alignX,
                alignY,
                placeholderWidth,
                placeholderHeight,
                alt,
                document,
                quantity,
                durability,
                tooltip);
    }

    public ImageOptions fit(Fit fit) {
        return new ImageOptions(
                width,
                height,
                fit,
                alignX,
                alignY,
                placeholderWidth,
                placeholderHeight,
                alt,
                document,
                quantity,
                durability,
                tooltip);
    }

    public ImageOptions alt(String alt) {
        return new ImageOptions(
                width,
                height,
                fit,
                alignX,
                alignY,
                placeholderWidth,
                placeholderHeight,
                alt,
                document,
                quantity,
                durability,
                tooltip);
    }

    /** 各项独立启用，默认关闭；图标声明不隐含库存操作。 */
    public ImageOptions quantity(boolean value) {
        return new ImageOptions(
                width,
                height,
                fit,
                alignX,
                alignY,
                placeholderWidth,
                placeholderHeight,
                alt,
                document,
                value,
                durability,
                tooltip);
    }

    public ImageOptions durability(boolean value) {
        return new ImageOptions(
                width,
                height,
                fit,
                alignX,
                alignY,
                placeholderWidth,
                placeholderHeight,
                alt,
                document,
                quantity,
                value,
                tooltip);
    }

    public ImageOptions tooltip(boolean value) {
        return new ImageOptions(
                width,
                height,
                fit,
                alignX,
                alignY,
                placeholderWidth,
                placeholderHeight,
                alt,
                document,
                quantity,
                durability,
                value);
    }

    public ImageOptions document() {
        return new ImageOptions(
                width,
                height,
                fit,
                alignX,
                alignY,
                placeholderWidth,
                placeholderHeight,
                alt,
                true,
                quantity,
                durability,
                tooltip);
    }
}
