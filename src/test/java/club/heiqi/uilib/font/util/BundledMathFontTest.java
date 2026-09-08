package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.glyph.ProceduralAccentShape;
import club.heiqi.uilib.font.latex.MathFontStyle;
import club.heiqi.uilib.font.latex.layout.MathFontParameters;
import club.heiqi.uilib.font.latex.layout.MathGlyphConstruction;
import club.heiqi.uilib.font.latex.layout.MathGlyphMetrics;
import club.heiqi.uilib.font.latex.layout.MathGlyphRef;
import club.heiqi.uilib.font.latex.layout.MathStretchAxis;
import club.heiqi.uilib.font.latex.layout.ProceduralAccentSpec;

/** 加载实际 OTF 和固定 sidecar；不声称覆盖 glyph page/GL 或未导出的 MathKernInfo。 */
public class BundledMathFontTest {
    private final BundledMathFont font = BundledMathFont.shared();

    private MathGlyphRef upright(int cp) { return font.resolve(cp, MathFontStyle.UPRIGHT, FontType.NORMAL); }

    @Test
    public void loadsPhysicalFaceAndMapsLatinExceptionAndRealWeight() {
        MathGlyphRef h = font.resolve('h', MathFontStyle.ITALIC, FontType.NORMAL);
        Assert.assertNotNull(h);
        Assert.assertEquals(1224, h.getGlyphId());
        Assert.assertEquals(upright(0x210e), h);
        Assert.assertEquals(3354, font.resolve('x', MathFontStyle.MATH_NORMAL, FontType.NORMAL).getGlyphId());
        Assert.assertEquals(3246, font.resolve('A', MathFontStyle.BOLD, FontType.NORMAL).getGlyphId());
        Assert.assertEquals(upright(0x1d468), font.resolve('A', MathFontStyle.ITALIC, FontType.BOLD));
        Assert.assertEquals(upright(0x1d400), font.resolve('A', MathFontStyle.UPRIGHT, FontType.BOLD));
        Assert.assertEquals(upright(0x1d468), font.resolve('A', MathFontStyle.MATH_NORMAL, FontType.BOLD));
        Assert.assertEquals(upright(0x1d7d0), font.resolve('2', MathFontStyle.BOLD, FontType.NORMAL));
        // Unicode 无数学 italic digits：真实字体路径保留正体，不借助几何斜切。
        Assert.assertEquals(upright('2'), font.resolve('2', MathFontStyle.ITALIC, FontType.NORMAL));
        Font physical = font.getPhysicalFont(h, 20);
        Assert.assertEquals("STIXTwoMath-Regular", physical.getPSName());
        Assert.assertEquals(6760, physical.getNumGlyphs());
        Assert.assertEquals(Font.PLAIN, physical.getStyle());
        Assert.assertEquals(20, physical.getSize());
        Assert.assertTrue(physical.getTransform().isIdentity());
        Assert.assertTrue(font.getFaceKey().contains("95bc2729e41faf93b0bcae9e96c4dc4da45855067fd0581e621e30734fe8d90b"));
        Assert.assertTrue(font.getFaceKey().endsWith(":face:0:profile:stix-two-math-v1"));
    }

    @Test
    public void mapsGreekVariantsExplicitlyAndDoesNotBoldSymbols() {
        int[] variants = { 0x3f5, 0x3d1, 0x3f0, 0x3d5, 0x3f1, 0x3d6 };
        int[] italic = { 0x1d716, 0x1d717, 0x1d718, 0x1d719, 0x1d71a, 0x1d71b };
        int[] bold = { 0x1d6dc, 0x1d6dd, 0x1d6de, 0x1d6df, 0x1d6e0, 0x1d6e1 };
        for (int i = 0; i < variants.length; i++) {
            Assert.assertEquals(upright(italic[i]), font.resolve(variants[i], MathFontStyle.ITALIC, FontType.NORMAL));
            Assert.assertEquals(upright(italic[i]), font.resolve(variants[i], MathFontStyle.INHERIT, FontType.NORMAL));
            Assert.assertEquals(upright(bold[i]), font.resolve(variants[i], MathFontStyle.BOLD, FontType.NORMAL));
        }
        Assert.assertEquals(upright(0x1d6fc), font.resolve(0x3b1, MathFontStyle.MATH_NORMAL, FontType.NORMAL));
        Assert.assertEquals(upright(0x393), font.resolve(0x393, MathFontStyle.INHERIT, FontType.NORMAL));
        Assert.assertEquals(upright(0x1d6e4), font.resolve(0x393, MathFontStyle.ITALIC, FontType.NORMAL));
        Assert.assertEquals(upright(0x1d70d), font.resolve(0x3c2, MathFontStyle.ITALIC, FontType.NORMAL));
        for (int cp : new int[] { '+', '=', 0x2211, 0x222b, 0x2202, 0x2207, 0x302 }) {
            Assert.assertNotNull(upright(cp));
            Assert.assertEquals(upright(cp), font.resolve(cp, MathFontStyle.BOLD, FontType.BOLD));
            Assert.assertEquals(upright(cp), font.resolve(cp, MathFontStyle.ITALIC, FontType.NORMAL));
        }
    }

    @Test
    public void usesFixedAdvanceCorrectionsAndSameGlyphVectorInk() {
        MathGlyphRef glyph = font.resolve('x', MathFontStyle.ITALIC, FontType.NORMAL);
        MathGlyphMetrics measured = font.measure(glyph, 20);
        Assert.assertEquals(11.18f, measured.getAdvance(), 0.0001f);
        Assert.assertEquals(0.2f, measured.getItalicCorrection(), 0.0001f);
        Assert.assertTrue(measured.hasTopAccentAttachment());
        Assert.assertEquals(6.9f, measured.getTopAccentAttachment(), 0.0001f);
        Rectangle2D ink = font.getPhysicalFont(glyph, 20)
                .createGlyphVector(new FontRenderContext(null, true, true), new int[] { glyph.getGlyphId() })
                .getGlyphVisualBounds(0).getBounds2D();
        Assert.assertFalse(ink.isEmpty());
        Assert.assertEquals(ink.getMinX(), measured.getInkLeft(), 0.0001);
        Assert.assertEquals(ink.getMinY(), measured.getInkTop(), 0.0001);
        Assert.assertEquals(ink.getMaxX(), measured.getInkRight(), 0.0001);
        Assert.assertEquals(ink.getMaxY(), measured.getInkBottom(), 0.0001);
        Assert.assertFalse(font.measure(upright(' '), 20).hasTopAccentAttachment());
    }

    @Test
    public void appliesDeviceAtEffectivePpemAndKeepsDegreePercent() {
        MathGlyphRef correction = MathGlyphRef.forFontGlyph(font.getFaceKey(), 4010);
        Assert.assertEquals(1.38f, font.measure(correction, 19).getItalicCorrection(), 0.0001f);
        Assert.assertEquals(0.4f, font.measure(correction, 20).getItalicCorrection(), 0.0001f);
        MathGlyphRef attachment = MathGlyphRef.forFontGlyph(font.getFaceKey(), 3309);
        Assert.assertEquals(2.015f, font.measure(attachment, 9).getTopAccentAttachment(), 0.0001f);
        Assert.assertEquals(3.685f, font.measure(attachment, 11).getTopAccentAttachment(), 0.0001f);
        MathFontParameters constants = font.constants(20);
        Assert.assertEquals(5.16f, constants.getAxisHeight(), 0.0001f);
        Assert.assertEquals(1.36f, constants.getRuleThickness(), 0.0001f);
        Assert.assertEquals(1.7f, constants.getRadicalVerticalGap(), 0.0001f);
        Assert.assertEquals(3.4f, constants.getRadicalDisplayStyleVerticalGap(), 0.0001f);
        Assert.assertEquals(1.56f, constants.getRadicalExtraAscender(), 0.0001f);
        Assert.assertEquals(9.6f, constants.getAccentBaseHeight(), 0.0001f);
        Assert.assertEquals(55, constants.getRadicalDegreeBottomRaisePercent());
        Assert.assertEquals(55, font.constants(40).getRadicalDegreeBottomRaisePercent());
        Assert.assertEquals(10.32f, font.constants(40).getAxisHeight(), 0.0001f);
    }

    @Test
    public void returnsOrderedSameFaceVariantsAndSafeAssemblies() {
        MathGlyphConstruction paren = font.construction(upright('('), MathStretchAxis.VERTICAL, 20);
        Assert.assertEquals(13, paren.getVariants().size());
        Assert.assertEquals(18.66f, paren.getVariants().get(0).getStretchAdvance(), 0.0001f);
        Assert.assertEquals(76.42f, paren.getVariants().get(12).getStretchAdvance(), 0.0001f);
        Assert.assertNotNull(paren.getAssembly());
        Assert.assertEquals(2, paren.getAssembly().getMinConnectorOverlap(), 0.0001f);
        for (MathGlyphConstruction.Variant variant : paren.getVariants()) {
            Assert.assertEquals(font.getFaceKey(), variant.getGlyphRef().getFaceKey());
            Assert.assertNotNull(font.measure(variant.getGlyphRef(), 20));
        }
        for (MathGlyphConstruction.Part part : paren.getAssembly().getParts()) {
            Assert.assertEquals(font.getFaceKey(), part.getGlyphRef().getFaceKey());
            Assert.assertTrue(part.getStartConnector() <= part.getFullAdvance());
            Assert.assertTrue(part.getEndConnector() <= part.getFullAdvance());
            Assert.assertNotNull(font.getPhysicalFont(part.getGlyphRef(), 20));
        }
        MathGlyphConstruction radical = font.construction(upright(0x221a), MathStretchAxis.VERTICAL, 20);
        Assert.assertEquals(4, radical.getVariants().size());
        Assert.assertNotNull(radical.getAssembly());
        for (int cp : new int[] { 0x302, 0x303, 0x305 }) {
            MathGlyphConstruction wide = font.construction(upright(cp), MathStretchAxis.HORIZONTAL, 20);
            Assert.assertEquals(6, wide.getVariants().size());
            // U+0305/gid746 的上游非法连接只禁用 assembly，不拒绝其变体或 font。
            Assert.assertNull(wide.getAssembly());
        }
        for (int id : new int[] { 1510, 1514, 1532 }) {
            MathGlyphConstruction shortSeam = font.construction(MathGlyphRef.forFontGlyph(font.getFaceKey(), id),
                    MathStretchAxis.HORIZONTAL, 20);
            Assert.assertNotNull(shortSeam);
            Assert.assertFalse(shortSeam.getVariants().isEmpty());
            Assert.assertNull(shortSeam.getAssembly());
        }
        Assert.assertNull(font.construction(upright('x'), MathStretchAxis.VERTICAL, 20));
    }

    @Test
    public void localAssemblyOmissionsArePinnedToOriginalResourceDefects() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(
                "/assets/qz_uilib/fonts/math/stix-two/math-data.json")) {
            Assert.assertNotNull(input);
            JsonObject data = new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject horizontal = data.getAsJsonObject("constructions").getAsJsonObject("horizontal");
            Assert.assertFalse(horizontal.getAsJsonObject("746").getAsJsonObject("assembly")
                    .get("validConnectorLengths").getAsBoolean());
            int[] ids = { 1510, 1514, 1532 };
            int[] seamIndices = { 1, 2, 2 };
            Assert.assertEquals(100, data.get("minConnectorOverlap").getAsInt());
            for (int i = 0; i < ids.length; i++) {
                JsonObject assembly = horizontal.getAsJsonObject(Integer.toString(ids[i])).getAsJsonObject("assembly");
                Assert.assertTrue(assembly.get("validConnectorLengths").getAsBoolean());
                JsonArray parts = assembly.getAsJsonArray("parts");
                Assert.assertEquals(0, parts.get(seamIndices[i]).getAsJsonObject().get("startConnector").getAsInt());
                Assert.assertEquals(250, parts.get(seamIndices[i] - 1).getAsJsonObject().get("endConnector").getAsInt());
            }
        }
    }

    @Test
    public void missingCodepointOrForeignIdentityNeverBecomesNotdef() {
        for (int cp : new int[] { 0x4e2d, 0x10ffff, -1, 0x110000, 0xd800 }) {
            Assert.assertNull(font.resolve(cp, MathFontStyle.INHERIT, FontType.NORMAL));
        }
        MathGlyphRef foreign = MathGlyphRef.forFontGlyph("other-face", 3354);
        MathGlyphRef notdef = MathGlyphRef.forFontGlyph(font.getFaceKey(), 0);
        MathGlyphRef outOfRange = MathGlyphRef.forFontGlyph(font.getFaceKey(), 6760);
        for (MathGlyphRef glyph : new MathGlyphRef[] { foreign, notdef, outOfRange, null }) {
            Assert.assertNull(font.getPhysicalFont(glyph, 20));
            Assert.assertNull(font.measure(glyph, 20));
            Assert.assertNull(font.construction(glyph, MathStretchAxis.VERTICAL, 20));
        }
    }

    @Test
    public void proceduralMeasureUsesTheExactGeneratorShape() {
        for (ProceduralAccentSpec.Kind kind : ProceduralAccentSpec.Kind.values()) {
            ProceduralAccentSpec spec = ProceduralAccentSpec.of(kind, 1, 655360, 16384, 4096);
            MathGlyphRef glyph = MathGlyphRef.forProceduralAccent(spec);
            MathGlyphMetrics metrics = font.measure(glyph, 20);
            Rectangle2D ink = ProceduralAccentShape.create(spec, 20).getBounds2D();
            Assert.assertEquals(200, metrics.getAdvance(), 0.0001f);
            Assert.assertEquals(100, metrics.getTopAccentAttachment(), 0.0001f);
            Assert.assertTrue(metrics.hasTopAccentAttachment());
            Assert.assertEquals(0, metrics.getItalicCorrection(), 0);
            Assert.assertEquals(ink.getMinX(), metrics.getInkLeft(), 0.0001);
            Assert.assertEquals(ink.getMinY(), metrics.getInkTop(), 0.0001);
            Assert.assertEquals(ink.getMaxX(), metrics.getInkRight(), 0.0001);
            Assert.assertEquals(ink.getMaxY(), metrics.getInkBottom(), 0.0001);
            Assert.assertNull(font.getPhysicalFont(glyph, 20));
            Assert.assertNull(font.construction(glyph, MathStretchAxis.HORIZONTAL, 20));
        }
        Assert.assertNull(font.measure(MathGlyphRef.forProceduralAccent(
                ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.HAT, 2, 65536, 65536, 1000)), 20));
        Assert.assertNull(font.measure(MathGlyphRef.forProceduralAccent(
                ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.HAT, 1, 65536, 1000, 1000)), 20));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonpositiveEffectiveSize() { font.constants(0); }
}
