package club.heiqi.uilib.ui.scene.image;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;

/** 绘制计划的值型几何，不含节点/callback/GL句柄；fractional quad保持实际比例。 */
public final class ImagePlacement {
    public final ImageContent content;
    public final float x, y, width, height;

    private ImagePlacement(ImageContent content, float x, float y, float width, float height) {
        this.content = content;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public ImagePlacement translated(int dx, int dy) { return new ImagePlacement(content, x + dx, y + dy, width, height); }
    public static ImagePlacement fit(ImageContent content, LayoutBox box, ImageOptions options) {
        float width = content.width, height = content.height;
        if (options.fit == ImageOptions.Fit.STRETCH) {
            width = box.width;
            height = box.height;
        } else if (options.fit != ImageOptions.Fit.NATIVE) {
            float scale =
                    options.fit == ImageOptions.Fit.COVER
                            ? Math.max(box.width / width, box.height / height)
                            : Math.min(box.width / width, box.height / height);
            width *= scale;
            height *= scale;
        }
        return new ImagePlacement(
                content,
                box.x + (box.width - width) * options.alignX,
                box.y + (box.height - height) * options.alignY,
                width,
                height);
    }
}
