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
    public static void main(String[] args) throws Exception { new NativeFontVisualScenario().run(args); }

    private FontSession service;
    @Override protected void setup() { service = own(new LwjglFontEngine().openSession()); }

    @Override protected void runTests() throws Exception {
        String[] args = arguments();
        File fonts = new File(args[0]);
        File output = TestResources.outputDirectory(args, 1, args[1]).toFile();
        BufferedImage image = new BufferedImage(760, 350, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = image.createGraphics();
        onClose(g::dispose);
        g.setColor(new Color(0xff181d25, true)); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        String[] faces = {"NotoSansArabic", "NotoSansDevanagari", "NotoSansThai", "NotoColorEmoji", "NotoColorEmoji"};
        String[] samples = {"العربية", "हिन्दी", "ภาษาไทย", "😀", "👩‍💻"};
        String[] languages = {"ar", "hi", "th", "en", "en"};

            for (int row = 0; row < faces.length; row++) {
                final int index = row;
                scenario("原生脚本软件合成-" + faces[index] + "-" + row, () -> {
                FontFace face = own(service.openFace(TestResources.font(fonts.toPath(), faces[index]), 0, 36));
                List<ShapedGlyph> glyphs = face.shape(samples[index], index == 0 ? ShapeDirection.RTL : ShapeDirection.LTR, languages[index]);
                float pen = 250, baseline = 50 + index * 60;
                g.setColor(Color.WHITE); g.drawString(faces[index] + " / " + glyphs.size() + " glyphs", 15, (int) baseline);
                for (ShapedGlyph glyph : glyphs) {
                    if (glyph.glyphId == 0) throw new AssertionError("真实字形缺失：" + faces[index]);
                    GlyphBitmap bitmap = face.rasterize(glyph.glyphId);
                    if (bitmap.width > 0 && bitmap.height > 0) {
                        BufferedImage pixels = new BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB_PRE);
                        int[] raster = ((DataBufferInt) pixels.getRaster().getDataBuffer()).getData();
                        ByteBuffer rgba = bitmap.pixelsBuffer();
                        for (int i = 0; i < raster.length; i++) {
                            int r = rgba.get() & 255, green = rgba.get() & 255, b = rgba.get() & 255, a = rgba.get() & 255;
                            raster[i] = a << 24 | r << 16 | green << 8 | b;
                        }
                        AffineTransform transform = AffineTransform.getTranslateInstance(pen + glyph.xOffset + bitmap.bearingX,
                            baseline - glyph.yOffset - bitmap.bearingY);
                        transform.scale(bitmap.pixelScale, bitmap.pixelScale); g.drawImage(pixels, transform, null);
                    }
                    pen += glyph.xAdvance;
                }
                });
            }
        scenario("原生字体图像输出", () -> {
        File target = new File(output, "native-fonts.png");
        ImageIO.write(image, "png", target);
        System.out.println("真实FT/HB字体出图：" + target);
        });
    }
}
