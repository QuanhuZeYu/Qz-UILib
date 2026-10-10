package club.heiqi.uilib.ui.scene.text.font.nativefont;

import java.nio.ByteBuffer;

/**
 * 独立于 face 生命周期的不可变 RGBA 像素。每像素四字节，逐行从上到下，颜色已 premultiply alpha。
 * width/height 是纹理像素；绘制尺寸为 width/height * pixelScale。
 * bearingX 向右为正，bearingY 为基线到图像顶部的向上距离，均已应用 pixelScale。
 * 灰度/mono 为白色 coverage，可着色；color=true 时保留字体原色。
 */
public final class GlyphBitmap {
    public final int width, height;
    public final float bearingX, bearingY, pixelScale;
    public final boolean color;
    private final byte[] rgba;

    public GlyphBitmap(
            int width,
            int height,
            float bearingX,
            float bearingY,
            float pixelScale,
            boolean color,
            byte[] rgba) {
        if (width < 0
                || height < 0
                || !Float.isFinite(bearingX)
                || !Float.isFinite(bearingY)
                || !Float.isFinite(pixelScale)
                || pixelScale <= 0
                || rgba.length != Math.multiplyExact(Math.multiplyExact(width, height), 4))
            throw new IllegalArgumentException("无效owned RGBA bitmap");
        this.width = width;
        this.height = height;
        this.bearingX = bearingX;
        this.bearingY = bearingY;
        this.pixelScale = pixelScale;
        this.color = color;
        this.rgba = rgba.clone();
    }

    /** 返回防御拷贝，不向调用方暴露可修改的内部存储或 native 内存。 */
    public byte[] pixels() {
        return rgba.clone();
    }

    public ByteBuffer pixelsBuffer() {
        return ByteBuffer.wrap(rgba).asReadOnlyBuffer();
    }

    /** NORMAL 不复制像素或增加边框；其余样式只在 owned CPU 光栅阶段合成。 */
    public GlyphBitmap styled(GlyphRasterStyle style) {
        java.util.Objects.requireNonNull(style, "style");
        if (style.normal() || width == 0 || height == 0) return this;
        double[] e =
                style.envelope(
                        bearingX,
                        -bearingY,
                        bearingX + (double) width * pixelScale,
                        -bearingY + (double) height * pixelScale,
                        pixelScale);
        double w = e[2] - e[0], h = e[3] - e[1];
        // 单次变体暂存最多64MiB；worker另按实际位图大小执行配置的cache预算。
        // 在任何数组分配/整数转换前拒绝极端shear/offset与坐标溢出。
        if (!Double.isFinite(w)
                || !Double.isFinite(h)
                || w < 1
                || h < 1
                || w > Integer.MAX_VALUE
                || h > Integer.MAX_VALUE
                || w * h > 16L * 1024 * 1024
                || Math.abs(e[0]) > Integer.MAX_VALUE
                || Math.abs(e[1]) > Integer.MAX_VALUE
                || Math.abs(e[2]) > Integer.MAX_VALUE
                || Math.abs(e[3]) > Integer.MAX_VALUE)
            throw new IllegalArgumentException("字形变体超过可分配像素范围");
        float bx = (float) (e[0] * pixelScale), by = (float) (-e[1] * pixelScale);
        if (!Float.isFinite(bx) || !Float.isFinite(by))
            throw new IllegalArgumentException("变体bearing溢出");
        int outWidth = (int) w, outHeight = (int) h;
        byte[] out = new byte[Math.multiplyExact(Math.multiplyExact(outWidth, outHeight), 4)];
        double left = bearingX / (double) pixelScale, top = -bearingY / (double) pixelScale;
        double shift = style.boldOffset / (double) pixelScale;
        for (int y = 0; y < outHeight; y++) {
            double baselineY = e[1] + y + 0.5, sy = baselineY - top - 0.5;
            for (int x = 0; x < outWidth; x++) {
                double sx = e[0] + x + 0.5 + style.italicShear * baselineY - left - 0.5;
                double copyAlpha = shift == 0 ? 0 : sample(sx - shift, sy, 3);
                for (int channel = 0; channel < 4; channel++) {
                    double value = sample(sx, sy, channel);
                    if (shift != 0)
                        value = sample(sx - shift, sy, channel) + value * (1 - copyAlpha / 255);
                    out[(y * outWidth + x) * 4 + channel] =
                            (byte) Math.max(0, Math.min(255, Math.round(value)));
                }
            }
        }
        return new GlyphBitmap(outWidth, outHeight, bx, by, pixelScale, color, out);
    }

    private double sample(double x, double y, int channel) {
        if (x <= -1 || y <= -1 || x >= width || y >= height) return 0;
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        return pixel(ix, iy, channel) * (1 - fx) * (1 - fy)
                + pixel(ix + 1, iy, channel) * fx * (1 - fy)
                + pixel(ix, iy + 1, channel) * (1 - fx) * fy
                + pixel(ix + 1, iy + 1, channel) * fx * fy;
    }

    private int pixel(int x, int y, int channel) {
        return x < 0 || y < 0 || x >= width || y >= height
                ? 0
                : rgba[(y * width + x) * 4 + channel] & 255;
    }
}
