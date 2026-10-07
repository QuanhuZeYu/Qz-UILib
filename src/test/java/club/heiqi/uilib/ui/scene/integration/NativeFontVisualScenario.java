package club.heiqi.uilib.ui.scene.integration;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;
import javax.imageio.ImageIO;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.NativeFontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.NativeFontFace.Glyph;
import club.heiqi.uilib.ui.scene.text.font.nativefont.NativeFontService;

/** 直接回放生产HB定位和FT像素，Java2D只承担软件合成，不负责文本整形/度量/字形生成。 */
public final class NativeFontVisualScenario {
    public static void main(String[] args) throws Exception {
        File fonts = new File(args[0]), output = new File(args[1]);
        if (!output.isDirectory() && !output.mkdirs()) throw new IllegalStateException("不能创建字体图像目录");
        BufferedImage image = new BufferedImage(760, 350, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0xff181d25, true)); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        String[] faces = {"NotoSansArabic", "NotoSansDevanagari", "NotoSansThai", "NotoColorEmoji", "NotoColorEmoji"};
        String[] samples = {"العربية", "हिन्दी", "ภาษาไทย", "😀", "👩‍💻"};
        String[] languages = {"ar", "hi", "th", "en", "en"};
        try (NativeFontService service = new NativeFontService()) {
            for (int row = 0; row < faces.length; row++) {
                NativeFontFace face = service.openFace(ByteBuffer.wrap(Files.readAllBytes(new File(fonts, faces[row] + ".ttf").toPath())), 0, 36);
                List<Glyph> glyphs = face.shape(samples[row], row == 0 ? NativeFontFace.Direction.RTL : NativeFontFace.Direction.LTR, languages[row]);
                float pen = 250, baseline = 50 + row * 60;
                g.setColor(Color.WHITE); g.drawString(faces[row] + " / " + glyphs.size() + " glyphs", 15, (int) baseline);
                for (Glyph glyph : glyphs) {
                    if (glyph.glyphId == 0) throw new AssertionError("真实字形缺失：" + faces[row]);
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
                face.close();
            }
        } finally { g.dispose(); }
        File target = new File(output, "native-fonts.png");
        ImageIO.write(image, "png", target);
        System.out.println("真实FT/HB字体出图：" + target);
    }
}
