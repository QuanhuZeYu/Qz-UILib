package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.TestResources;
import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeDirection;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;
import javax.imageio.ImageIO;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapedGlyph;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;

/** 直接回放生产HB定位和FT像素，Java2D只承担软件合成，不负责文本整形/度量/字形生成。 */
public final class NativeFontVisualScenario extends TestApplication {
    public static void main(String[] args) throws Exception {
        new NativeFontVisualScenario().run(args);
    }

    private FontSession service;

    /** 软件出口仍为 premult RGBA，主 owned mono 不因导出变成四通道缓存。 */
    private static void checkOwnedRows(GlyphBitmap bitmap) {
        int bytes = bitmap.width * bitmap.height * (bitmap.color ? 4 : 1);
        require(bitmap.byteSize() == bytes && bitmap.singleChannel() != bitmap.color,
                "FT owned mono/color 格式错误");
        byte[] expected = bitmap.pixels();
        ByteBuffer rows = ByteBuffer.allocate(expected.length + 2);
        rows.put((byte) 37);
        for (int y = 0; y < bitmap.height; y++) bitmap.copyRgba(rows, y, 1);
        rows.put((byte) 91);
        require(rows.position() == expected.length + 2 && rows.get(0) == 37
                && rows.get(expected.length + 1) == 91, "逐行 RGBA 导出越界");
        for (int i = 0; i < expected.length; i++)
            require(rows.get(i + 1) == expected[i], "逐行 RGBA 导出丢行");
        ByteBuffer alpha = ByteBuffer.allocate(bitmap.width * bitmap.height);
        for (int y = 0; y < bitmap.height; y++) bitmap.copyCoverage(alpha, y, 1);
        for (int i = 0; i < alpha.capacity(); i++)
            require(alpha.get(i) == expected[i * 4 + 3], "coverage 导出不是 alpha");
        try {
            bitmap.copyRgba(ByteBuffer.allocate(0), bitmap.height, 1);
            throw new AssertionError("越界行被接受");
        } catch (IndexOutOfBoundsException expectedFailure) {
        }
        require(bitmap.byteSize() == bytes, "导出改变 owned 存储成本");
    }

    @Override
    protected void setup() {
        service = own(new LwjglFontEngine().openSession());
    }

    @Override
    protected void runTests() throws Exception {
        scenario("既有 RGBA 输入不按 mono 重解释", () -> {
            byte[] rgba = {11, 22, 33, 44, 55, 66, 77, 88};
            GlyphBitmap bitmap = new GlyphBitmap(1, 2, 0, 0, 1, false, rgba);
            rgba[0] = 0;
            require(!bitmap.singleChannel() && bitmap.byteSize() == 8 && bitmap.pixels()[0] == 11,
                    "公开 RGBA 构造语义或防御复制被改变");
            ByteBuffer row = ByteBuffer.allocate(4);
            bitmap.copyRgba(row, 1, 1);
            require(row.get(0) == 55 && row.get(3) == 88, "RGBA 指定行复制错误");
            ByteBuffer coverage = ByteBuffer.allocate(2);
            bitmap.copyCoverage(coverage, 0, 2);
            require(coverage.get(0) == 44 && coverage.get(1) == 88, "RGBA mono coverage 应取 alpha");
        });
        String[] args = arguments();
        File fonts = new File(args[0]);
        File output = TestResources.outputDirectory(args, 1, args[1]).toFile();
        BufferedImage image = new BufferedImage(760, 350, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = image.createGraphics();
        onClose(g::dispose);
        g.setColor(new Color(0xff181d25, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        String[] faces = {
            "NotoSansArabic",
            "NotoSansDevanagari",
            "NotoSansThai",
            "NotoColorEmoji",
            "NotoColorEmoji"
        };
        String[] samples = {"العربية", "हिन्दी", "ภาษาไทย", "😀", "👩‍💻"};
        String[] languages = {"ar", "hi", "th", "en", "en"};

        for (int row = 0; row < faces.length; row++) {
            final int index = row;
            scenario(
                    "原生脚本软件合成-" + faces[index] + "-" + row,
                    () -> {
                        FontFace face =
                                own(
                                        service.openFace(
                                                TestResources.font(fonts.toPath(), faces[index]),
                                                0,
                                                36));
                        List<ShapedGlyph> glyphs =
                                face.shape(
                                        samples[index],
                                        index == 0 ? ShapeDirection.RTL : ShapeDirection.LTR,
                                        languages[index]);
                        float pen = 250, baseline = 50 + index * 60;
                        g.setColor(Color.WHITE);
                        g.drawString(
                                faces[index] + " / " + glyphs.size() + " glyphs",
                                15,
                                (int) baseline);
                        for (ShapedGlyph glyph : glyphs) {
                            if (glyph.glyphId == 0)
                                throw new AssertionError("真实字形缺失：" + faces[index]);
                            GlyphBitmap bitmap = face.rasterize(glyph.glyphId);
                            checkOwnedRows(bitmap);
                            if (bitmap.width > 0 && bitmap.height > 0) {
                                BufferedImage pixels =
                                        new BufferedImage(
                                                bitmap.width,
                                                bitmap.height,
                                                BufferedImage.TYPE_INT_ARGB_PRE);
                                int[] raster =
                                        ((DataBufferInt) pixels.getRaster().getDataBuffer())
                                                .getData();
                                ByteBuffer rgba = bitmap.pixelsBuffer();
                                for (int i = 0; i < raster.length; i++) {
                                    int r = rgba.get() & 255,
                                            green = rgba.get() & 255,
                                            b = rgba.get() & 255,
                                            a = rgba.get() & 255;
                                    raster[i] = a << 24 | r << 16 | green << 8 | b;
                                }
                                AffineTransform transform =
                                        AffineTransform.getTranslateInstance(
                                                pen + glyph.xOffset + bitmap.bearingX,
                                                baseline - glyph.yOffset - bitmap.bearingY);
                                transform.scale(bitmap.pixelScale, bitmap.pixelScale);
                                g.drawImage(pixels, transform, null);
                            }
                            pen += glyph.xAdvance;
                        }
                    });
        }
        scenario(
                "原生字体图像输出",
                () -> {
                    File target = new File(output, "native-fonts.png");
                    ImageIO.write(image, "png", target);
                    System.out.println("真实FT/HB字体出图：" + target);
                });
    }
}
