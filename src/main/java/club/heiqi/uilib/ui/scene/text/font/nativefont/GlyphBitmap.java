package club.heiqi.uilib.ui.scene.text.font.nativefont;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FreeType;

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

    private GlyphBitmap(int width, int height, float bearingX, float bearingY, float pixelScale, boolean color, byte[] rgba) {
        this.width = width; this.height = height; this.bearingX = bearingX; this.bearingY = bearingY;
        this.pixelScale = pixelScale; this.color = color; this.rgba = rgba;
    }

    /** 返回防御拷贝，不向调用方暴露可修改的内部存储或 native 内存。 */
    public byte[] pixels() { return rgba.clone(); }

    public ByteBuffer pixelsBuffer() { return ByteBuffer.wrap(rgba).asReadOnlyBuffer(); }

    /** NORMAL 不复制像素或增加边框；其余样式只在 owned CPU 光栅阶段合成。 */
    GlyphBitmap styled(GlyphRasterStyle style) {
        java.util.Objects.requireNonNull(style, "style");
        if (style.normal() || width == 0 || height == 0) return this;
        double[] e = style.envelope(bearingX, -bearingY, bearingX + (double) width * pixelScale,
            -bearingY + (double) height * pixelScale, pixelScale);
        double w = e[2] - e[0], h = e[3] - e[1];
        // 单次变体暂存最多64MiB；worker另按实际位图大小执行配置的cache预算。
        // 在任何数组分配/整数转换前拒绝极端shear/offset与坐标溢出。
        if (!Double.isFinite(w) || !Double.isFinite(h) || w < 1 || h < 1 || w > Integer.MAX_VALUE
            || h > Integer.MAX_VALUE || w * h > 16L * 1024 * 1024
            || Math.abs(e[0]) > Integer.MAX_VALUE || Math.abs(e[1]) > Integer.MAX_VALUE
            || Math.abs(e[2]) > Integer.MAX_VALUE || Math.abs(e[3]) > Integer.MAX_VALUE)
            throw new IllegalArgumentException("字形变体超过可分配像素范围");
        float bx = (float) (e[0] * pixelScale), by = (float) (-e[1] * pixelScale);
        if (!Float.isFinite(bx) || !Float.isFinite(by)) throw new IllegalArgumentException("变体bearing溢出");
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
                    if (shift != 0) value = sample(sx - shift, sy, channel) + value * (1 - copyAlpha / 255);
                    out[(y * outWidth + x) * 4 + channel] = (byte) Math.max(0, Math.min(255, Math.round(value)));
                }
            }
        }
        return new GlyphBitmap(outWidth, outHeight, bx, by, pixelScale, color, out);
    }
    private double sample(double x, double y, int channel) {
        if (x <= -1 || y <= -1 || x >= width || y >= height) return 0;
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        return pixel(ix,iy,channel) * (1-fx) * (1-fy) + pixel(ix+1,iy,channel) * fx * (1-fy)
            + pixel(ix,iy+1,channel) * (1-fx) * fy + pixel(ix+1,iy+1,channel) * fx * fy;
    }
    private int pixel(int x, int y, int channel) {
        return x < 0 || y < 0 || x >= width || y >= height ? 0 : rgba[(y * width + x) * 4 + channel] & 255;
    }

    static GlyphBitmap copy(FT_Bitmap bitmap, float bearingX, float bearingY, float pixelScale) {
        int width = bitmap.width(), height = bitmap.rows();
        byte[] rgba = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
        int mode = bitmap.pixel_mode() & 255;
        boolean color = mode == FreeType.FT_PIXEL_MODE_BGRA;
        if (width == 0 || height == 0) return new GlyphBitmap(width, height, bearingX, bearingY, pixelScale, color, rgba);
        if (!color && mode != FreeType.FT_PIXEL_MODE_GRAY && mode != FreeType.FT_PIXEL_MODE_MONO) {
            throw new IllegalStateException("Unsupported FreeType bitmap pixel mode: " + mode);
        }
        int rowBytes = color ? Math.multiplyExact(width, 4) : mode == FreeType.FT_PIXEL_MODE_MONO ? (width + 7) / 8 : width;
        if (Math.abs((long) bitmap.pitch()) < rowBytes) throw new IllegalStateException("Invalid FreeType bitmap pitch");
        ByteBuffer buffer = bitmap.buffer(1);
        if (buffer == null) throw new IllegalStateException("Missing FreeType bitmap buffer");
        long address = MemoryUtil.memAddress(buffer);
        int grayLevels = bitmap.num_grays() & 65535;
        if (mode == FreeType.FT_PIXEL_MODE_GRAY && grayLevels < 2) throw new IllegalStateException("Invalid gray levels");
        for (int y = 0; y < height; y++) {
            // FT 的 buffer 指向第一行，pitch 的符号决定下一行地址；不要假定正 pitch。
            long row = address + (long) y * bitmap.pitch();
            for (int x = 0; x < width; x++) {
                int dest = (y * width + x) * 4;
                if (color) {
                    long src = row + (long) x * 4;
                    rgba[dest] = MemoryUtil.memGetByte(src + 2);
                    rgba[dest + 1] = MemoryUtil.memGetByte(src + 1);
                    rgba[dest + 2] = MemoryUtil.memGetByte(src);
                    rgba[dest + 3] = MemoryUtil.memGetByte(src + 3);
                } else {
                    int alpha = mode == FreeType.FT_PIXEL_MODE_MONO
                            ? ((MemoryUtil.memGetByte(row + x / 8) & (0x80 >>> (x & 7))) != 0 ? 255 : 0)
                            : Math.min(255, (MemoryUtil.memGetByte(row + x) & 255) * 255 / (grayLevels - 1));
                    rgba[dest] = rgba[dest + 1] = rgba[dest + 2] = rgba[dest + 3] = (byte) alpha;
                }
            }
        }
        return new GlyphBitmap(width, height, bearingX, bearingY, pixelScale, color, rgba);
    }
}
