package club.heiqi.uilib.font.glyph;

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Collections;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.MathFontStyle;
import club.heiqi.uilib.font.util.BundledMathFont;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;
import club.heiqi.uilib.font.util.DerivedFontCache;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.latex.layout.MathGlyphMetrics;
import club.heiqi.uilib.font.latex.layout.MathGlyphRef;
import club.heiqi.uilib.font.latex.layout.ProceduralAccentSpec;

/** 实际 STIX gid 与程序路径均经过生产 raster/crop/RGBA 冻结；不需要 GL。 */
public class MathGlyphGeneratorRasterTest {
    private final GlyphGenerator generator = new GlyphGenerator(null, null);

    @Test
    public void productionDispatchUsesRegisteredFaceAndRejectsWrongIdentityOrGeneration() {
        BundledMathFont math = BundledMathFont.shared();
        FontCatalog catalog = new FontCatalog();
        catalog.publish(catalog.prepareSnapshotWithMath(Collections.<Font>emptyList(), math));
        DerivedFontCache cache = new DerivedFontCache(catalog);
        GlyphGenerator production = new GlyphGenerator(new FontMatcher(catalog, cache), cache);
        MathGlyphRef ref = math.resolve('(', MathFontStyle.UPRIGHT, FontType.NORMAL);
        GlyphRequestToken token = GlyphRequestToken.forMathGlyph(0, 1, ref, 32, 0);
        GlyphGenerationResult result = production.generate(new GlyphGenerationTask(token, 32, GlyphGenerationPriority.HIGH));
        Assert.assertNotNull(result);
        Assert.assertSame(token, result.getToken());
        Assert.assertTrue(result.getMathGlyphInfo().hasBitmap());
        Assert.assertEquals(math.measure(ref, 32).getAdvance(), result.getMathGlyphInfo().getAdvance(), 0f);
        MathGlyphRef wrongFace = MathGlyphRef.forFontGlyph("wrong-resource-identity", ref.getGlyphId());
        Assert.assertNull(production.generate(new GlyphGenerationTask(
                GlyphRequestToken.forMathGlyph(0, 2, wrongFace, 32, 0), 32, GlyphGenerationPriority.HIGH)));
        Assert.assertNull(production.generate(new GlyphGenerationTask(
                GlyphRequestToken.forMathGlyph(1, 3, ref, 32, 0), 32, GlyphGenerationPriority.HIGH)));
        MathGlyphRef invalidGid = MathGlyphRef.forFontGlyph(math.getFaceKey(), Integer.MAX_VALUE);
        Assert.assertNull(production.generate(new GlyphGenerationTask(
                GlyphRequestToken.forMathGlyph(0, 4, invalidGid, 32, 0), 32, GlyphGenerationPriority.HIGH)));
        MathGlyphRef accent = MathGlyphRef.forProceduralAccent(
                ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.HAT, 1, 65536 * 4, 19661, 3604));
        Assert.assertTrue(production.generate(new GlyphGenerationTask(
                GlyphRequestToken.forMathGlyph(0, 5, accent, 32, 0), 32, GlyphGenerationPriority.HIGH))
                .getMathGlyphInfo().hasBitmap());
    }

    @Test
    public void realStixGlyphUsesRequestedPhysicalGlyphAndFreezesPixels() throws Exception {
        Font font;
        try (InputStream input = getClass().getResourceAsStream(
                "/assets/qz_uilib/fonts/math/stix-two/STIXTwoMath-Regular.otf")) {
            Assert.assertNotNull(input);
            font = Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(32f);
        }
        FontRenderContext context = new FontRenderContext(new AffineTransform(), true, true);
        int gid = font.createGlyphVector(context, "(").getGlyphCode(0);
        GlyphVector vector = font.createGlyphVector(context, new int[] { gid });
        Assert.assertEquals(gid, vector.getGlyphCode(0));
        MathGlyphRef ref = MathGlyphRef.forFontGlyph("actual-stix-test-face", gid);
        MathGlyphMetrics metrics = metrics(vector.getVisualBounds(), vector.getGlyphMetrics(0).getAdvance());
        GlyphGenerationResult result = generator.rasterMathTile(token(ref, 0), metrics, vector, null,
                new MathGlyphRasterPlan(metrics, 128, 4));
        Assert.assertEquals(ref, result.getMathGlyphRef());
        Assert.assertTrue(result.getMathGlyphInfo().hasBitmap());
        byte[] before = result.copyRgbaPixels();
        BufferedImage image = result.getImage();
        image.setRGB(0, 0, 0xffffffff);
        Assert.assertArrayEquals(before, result.copyRgbaPixels());
        try {
            result.getCodepoint();
            Assert.fail("数学结果没有码点身份");
        } catch (IllegalStateException expected) { }
    }

    @Test
    public void longHatAndTildeTilesReassembleWithoutDuplicateAlpha() {
        for (ProceduralAccentSpec.Kind kind : ProceduralAccentSpec.Kind.values()) {
            MathGlyphRef ref = MathGlyphRef.forProceduralAccent(
                    ProceduralAccentSpec.of(kind, 1, 65536 * 40, 19661, 3604));
            Shape shape = ProceduralAccentShape.create(ref.getProceduralAccent(), 32);
            MathGlyphMetrics metrics = metrics(shape.getBounds2D(), 1280f);
            MathGlyphRasterPlan tiled = new MathGlyphRasterPlan(metrics, 128, 4);
            Assert.assertTrue(tiled.getTileCount() > 1);
            GlyphGenerationResult whole = generator.rasterMathTile(token(ref, 0), metrics, null, shape,
                    new MathGlyphRasterPlan(metrics, 2048, 4));
            BufferedImage expected = whole.getImage();
            BufferedImage assembled = new BufferedImage(expected.getWidth(), expected.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            GlyphInfo overall = whole.getMathGlyphInfo();
            for (int tile = 0; tile < tiled.getTileCount(); tile++) {
                GlyphGenerationResult part = generator.rasterMathTile(token(ref, tile), metrics, null, shape, tiled);
                GlyphInfo info = part.getMathGlyphInfo();
                if (!info.hasBitmap()) { continue; }
                BufferedImage image = part.getImage();
                Assert.assertTrue(image.getWidth() <= 128);
                Assert.assertTrue(image.getHeight() <= 128);
                // gutter 保存相邻核心的采样邻域；仅核心参与 quad，不重复合成 alpha。
                int dx = overall.getAtlasBaselineX() - info.getAtlasBaselineX();
                int dy = overall.getAtlasBaselineY() - info.getAtlasBaselineY();
                for (int y = 4; y < image.getHeight() - 4; y++) {
                    for (int x = 4; x < image.getWidth() - 4; x++) {
                        int pixel = image.getRGB(x, y);
                        if ((pixel >>> 24) == 0) { continue; }
                        Assert.assertEquals("核心不得重复 alpha", 0, assembled.getRGB(x + dx, y + dy));
                        assembled.setRGB(x + dx, y + dy, pixel);
                    }
                }
            }
            for (int y = 0; y < expected.getHeight(); y++) {
                for (int x = 0; x < expected.getWidth(); x++) {
                    Assert.assertEquals("分片拼回必须保持全局路径覆盖率", expected.getRGB(x, y), assembled.getRGB(x, y));
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownProceduralProfile() {
        ProceduralAccentShape.create(ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.HAT,
                2, 65536, 19661, 3604), 32);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfRangeTileBeforeAllocating() {
        MathGlyphMetrics metrics = new MathGlyphMetrics(10, 0, -10, 10, 0, 0, false, 0);
        new MathGlyphRasterPlan(metrics, 128, 4).getLeft(1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsHeightBeyondAtlasInsteadOfClipping() {
        new MathGlyphRasterPlan(new MathGlyphMetrics(10, 0, -200, 10, 0, 0, false, 0), 128, 4);
    }

    private static GlyphRequestToken token(MathGlyphRef ref, int tile) {
        return GlyphRequestToken.forMathGlyph(1, tile + 1L, ref, 32, tile);
    }

    private static MathGlyphMetrics metrics(Rectangle2D bounds, float advance) {
        return new MathGlyphMetrics(advance, (float) bounds.getX(), (float) bounds.getY(),
                (float) bounds.getMaxX(), (float) bounds.getMaxY(), 0, false, 0);
    }

}
