package club.heiqi.uilib.ui.scene.text.font.nativefont;

import java.nio.ByteBuffer;

/**
 * 独立于 face 生命周期的不可变 owned 像素，逐行从上到下。
 * 正式单色光栅保留单通道 coverage；彩色及公开 RGBA 输入保留 premult RGBA。
 * width/height 是纹理像素；绘制尺寸为 width/height * pixelScale。
 * bearingX 向右为正，bearingY 为基线到图像顶部的向上距离，均已应用 pixelScale。
 */
public final class GlyphBitmap {
    public final int width, height;
    public final float bearingX, bearingY, pixelScale;
    public final boolean color;
    private final byte[] data;
    private final boolean singleChannel;

    /** 既有输入合同：rgba 始终是四通道 premult 数据，color 只决定是否保留原色。 */
    public GlyphBitmap(
            int width,
            int height,
            float bearingX,
            float bearingY,
            float pixelScale,
            boolean color,
            byte[] rgba) {
        this(width, height, bearingX, bearingY, pixelScale, color, rgba, false);
    }

    /** FT 灰度/mono 的正式 owned 存储；每像素一字节覆盖率。 */
    public static GlyphBitmap coverage(
            int width,
            int height,
            float bearingX,
            float bearingY,
            float pixelScale,
            byte[] coverage) {
        return new GlyphBitmap(width, height, bearingX, bearingY, pixelScale, false, coverage, true);
    }

    private GlyphBitmap(
            int width,
            int height,
            float bearingX,
            float bearingY,
            float pixelScale,
            boolean color,
            byte[] data,
            boolean singleChannel) {
        if (width < 0
                || height < 0
                || !Float.isFinite(bearingX)
                || !Float.isFinite(bearingY)
                || !Float.isFinite(pixelScale)
                || pixelScale <= 0
                || data.length != Math.multiplyExact(Math.multiplyExact(width, height), singleChannel ? 1 : 4))
            throw new IllegalArgumentException("无效 owned bitmap");
        this.width = width;
        this.height = height;
        this.bearingX = bearingX;
        this.bearingY = bearingY;
        this.pixelScale = pixelScale;
        this.color = color;
        this.singleChannel = singleChannel;
        this.data = data.clone();
    }

    public boolean singleChannel() {
        return singleChannel;
    }

    /** CPU 缓存按实际 owned 字节记账，不包含按需导出数据。 */
    public int byteSize() {
        return data.length;
    }

    /** 返回 premult RGBA 防御拷贝，保持既有软件采样与调用方合同。 */
    public byte[] pixels() {
        if (!singleChannel) return data.clone();
        byte[] rgba = new byte[Math.multiplyExact(data.length, 4)];
        copyRgba(ByteBuffer.wrap(rgba), 0, height);
        return rgba;
    }

    public ByteBuffer pixelsBuffer() {
        return ByteBuffer.wrap(singleChannel ? pixels() : data).asReadOnlyBuffer();
    }

    /** 仅导出指定行，mono 按需展开为白色 premult RGBA，不增加主存储。 */
    public void copyRgba(ByteBuffer destination, int firstRow, int rows) {
        checkCopy(destination, firstRow, rows, 4);
        int first = firstRow * width, count = rows * width;
        if (!singleChannel) destination.put(data, first * 4, count * 4);
        else
            for (int i = first; i < first + count; i++)
                for (int c = 0; c < 4; c++) destination.put(data[i]);
    }

    /** RGBA 输入的 coverage 来自 alpha；不能按 color=false 重新解释调用者的字节。 */
    public void copyCoverage(ByteBuffer destination, int firstRow, int rows) {
        checkCopy(destination, firstRow, rows, 1);
        int first = firstRow * width, count = rows * width;
        if (singleChannel) destination.put(data, first, count);
        else
            for (int i = first; i < first + count; i++) destination.put(data[i * 4 + 3]);
    }

    private void checkCopy(ByteBuffer destination, int firstRow, int rows, int channels) {
        java.util.Objects.requireNonNull(destination, "destination");
        if (firstRow < 0 || rows < 0 || rows > height || firstRow > height - rows)
            throw new IndexOutOfBoundsException("无效 bitmap 行范围");
        if (destination.remaining() < Math.multiplyExact(Math.multiplyExact(width, rows), channels))
            throw new java.nio.BufferOverflowException();
    }

    /** NORMAL 不复制像素或增加边框；其余显式样式保留原 owned 格式。 */
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
        int channels = singleChannel ? 1 : 4;
        // 单次变体暂存最多64MiB；worker另按实际位图大小执行配置的cache预算。
        // 在任何数组分配/整数转换前拒绝极端shear/offset与坐标溢出。
        if (!Double.isFinite(w)
                || !Double.isFinite(h)
                || w < 1
                || h < 1
                || w > Integer.MAX_VALUE
                || h > Integer.MAX_VALUE
                || w * h * channels > 64L * 1024 * 1024
                || Math.abs(e[0]) > Integer.MAX_VALUE
                || Math.abs(e[1]) > Integer.MAX_VALUE
                || Math.abs(e[2]) > Integer.MAX_VALUE
                || Math.abs(e[3]) > Integer.MAX_VALUE)
            throw new IllegalArgumentException("字形变体超过可分配像素范围");
        float bx = (float) (e[0] * pixelScale), by = (float) (-e[1] * pixelScale);
        if (!Float.isFinite(bx) || !Float.isFinite(by))
            throw new IllegalArgumentException("变体bearing溢出");
        int outWidth = (int) w, outHeight = (int) h;
        byte[] out = new byte[Math.multiplyExact(Math.multiplyExact(outWidth, outHeight), channels)];
        double left = bearingX / (double) pixelScale, top = -bearingY / (double) pixelScale;
        double shift = style.boldOffset / (double) pixelScale;
        for (int y = 0; y < outHeight; y++) {
            double baselineY = e[1] + y + 0.5, sy = baselineY - top - 0.5;
            for (int x = 0; x < outWidth; x++) {
                double sx = e[0] + x + 0.5 + style.italicShear * baselineY - left - 0.5;
                double copyAlpha = shift == 0 ? 0 : sample(sx - shift, sy, 3);
                for (int channel = 0; channel < channels; channel++) {
                    double value = sample(sx, sy, channel);
                    if (shift != 0)
                        value = sample(sx - shift, sy, channel) + value * (1 - copyAlpha / 255);
                    out[(y * outWidth + x) * channels + channel] =
                            (byte) Math.max(0, Math.min(255, Math.round(value)));
                }
            }
        }
        return new GlyphBitmap(outWidth, outHeight, bx, by, pixelScale, color, out, singleChannel);
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
                : data[singleChannel ? y * width + x : (y * width + x) * 4 + channel] & 255;
    }
}
