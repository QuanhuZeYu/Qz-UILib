package club.heiqi.uilib.host.font.freetype;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FreeType;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;

/** FT native bitmap拷贝边界，结果只含owned JDK数据。 */
final class FreeTypeBitmap {
    static GlyphBitmap copy(FT_Bitmap bitmap, float bearingX, float bearingY, float pixelScale) {
        int width = bitmap.width(), height = bitmap.rows();
        int mode = bitmap.pixel_mode() & 255;
        boolean color = mode == FreeType.FT_PIXEL_MODE_BGRA;
        byte[] pixels = new byte[Math.multiplyExact(Math.multiplyExact(width, height), color ? 4 : 1)];
        if (width == 0 || height == 0)
            return color
                    ? new GlyphBitmap(width, height, bearingX, bearingY, pixelScale, true, pixels)
                    : GlyphBitmap.coverage(width, height, bearingX, bearingY, pixelScale, pixels);
        if (!color && mode != FreeType.FT_PIXEL_MODE_GRAY && mode != FreeType.FT_PIXEL_MODE_MONO) {
            throw new IllegalStateException("Unsupported FreeType bitmap pixel mode: " + mode);
        }
        int rowBytes =
                color
                        ? Math.multiplyExact(width, 4)
                        : mode == FreeType.FT_PIXEL_MODE_MONO ? (width + 7) / 8 : width;
        if (Math.abs((long) bitmap.pitch()) < rowBytes)
            throw new IllegalStateException("Invalid FreeType bitmap pitch");
        ByteBuffer buffer = bitmap.buffer(1);
        if (buffer == null) throw new IllegalStateException("Missing FreeType bitmap buffer");
        long address = MemoryUtil.memAddress(buffer);
        int grayLevels = bitmap.num_grays() & 65535;
        if (mode == FreeType.FT_PIXEL_MODE_GRAY && grayLevels < 2)
            throw new IllegalStateException("Invalid gray levels");
        for (int y = 0; y < height; y++) {
            // FT 的 buffer 指向第一行，pitch 的符号决定下一行地址；不要假定正 pitch。
            long row = address + (long) y * bitmap.pitch();
            for (int x = 0; x < width; x++) {
                int dest = (y * width + x) * (color ? 4 : 1);
                if (color) {
                    long src = row + (long) x * 4;
                    pixels[dest] = MemoryUtil.memGetByte(src + 2);
                    pixels[dest + 1] = MemoryUtil.memGetByte(src + 1);
                    pixels[dest + 2] = MemoryUtil.memGetByte(src);
                    pixels[dest + 3] = MemoryUtil.memGetByte(src + 3);
                } else {
                    int alpha =
                            mode == FreeType.FT_PIXEL_MODE_MONO
                                    ? ((MemoryUtil.memGetByte(row + x / 8) & (0x80 >>> (x & 7)))
                                                    != 0
                                            ? 255
                                            : 0)
                                    : Math.min(
                                            255,
                                            (MemoryUtil.memGetByte(row + x) & 255)
                                                    * 255
                                                    / (grayLevels - 1));
                    pixels[dest] = (byte) alpha;
                }
            }
        }
        return color
                ? new GlyphBitmap(width, height, bearingX, bearingY, pixelScale, true, pixels)
                : GlyphBitmap.coverage(width, height, bearingX, bearingY, pixelScale, pixels);
    }
}
