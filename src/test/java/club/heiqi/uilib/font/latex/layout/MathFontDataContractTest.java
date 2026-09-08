package club.heiqi.uilib.font.latex.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/** 数学字体数据边界：内容身份、真实有符号度量、拼接合法性和防御复制。 */
public class MathFontDataContractTest {
    private static MathGlyphRef glyph(String face, int id) {
        return MathGlyphRef.forFontGlyph(face, id);
    }

    @Test
    public void contentIdentitySeparatesFacesAndEveryShapeParameter() {
        MathGlyphRef a = glyph("sha-a:face-0:profile-1", 7);
        Map<MathGlyphRef, String> cache = new HashMap<MathGlyphRef, String>();
        cache.put(a, "a");
        Assert.assertEquals("a", cache.get(glyph("sha-a:face-0:profile-1", 7)));
        Assert.assertNull(cache.get(glyph("sha-b:face-0:profile-1", 7)));
        Assert.assertNull(cache.get(glyph("sha-a:face-0:profile-1", 8)));
        ProceduralAccentSpec spec = ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.HAT, 1, 65536, 4096, 512);
        MathGlyphRef shape = MathGlyphRef.forProceduralAccent(spec);
        cache.put(shape, "shape");
        Assert.assertEquals("shape", cache.get(MathGlyphRef.forProceduralAccent(
                ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.HAT, 1, 65536, 4096, 512))));
        for (ProceduralAccentSpec different : Arrays.asList(
                ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.TILDE, 1, 65536, 4096, 512),
                ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.HAT, 2, 65536, 4096, 512),
                ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.HAT, 1, 65537, 4096, 512),
                ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.HAT, 1, 65536, 4097, 512),
                ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.HAT, 1, 65536, 4096, 513))) {
            Assert.assertNull(cache.get(MathGlyphRef.forProceduralAccent(different)));
        }
    }

    @Test
    public void wrongKindNeverLeaksPlaceholderIdentity() {
        MathGlyphRef font = glyph("sha:face:profile", 0);
        MathGlyphRef shape = MathGlyphRef.forProceduralAccent(
                ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.HAT, 1, 1, 1, 1));
        expect(IllegalStateException.class, () -> font.getProceduralAccent());
        expect(IllegalStateException.class, () -> shape.getFaceKey());
        expect(IllegalStateException.class, () -> shape.getGlyphId());
        expect(IllegalArgumentException.class, () -> glyph(" ", 0));
        expect(IllegalArgumentException.class, () -> glyph(null, 0));
        expect(IllegalArgumentException.class, () -> glyph("face", -1));
        expect(IllegalArgumentException.class, () -> MathGlyphRef.forProceduralAccent(null));
        expect(IllegalArgumentException.class, () -> ProceduralAccentSpec.of(null, 1, 1, 1, 1));
        expect(IllegalArgumentException.class, () -> ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.HAT, 0, 1, 1, 1));
        expect(IllegalArgumentException.class, () -> ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.HAT, 1, 0, 1, 1));
        expect(IllegalArgumentException.class, () -> ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.HAT, 1, 1, -1, 1));
        expect(IllegalArgumentException.class, () -> ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.HAT, 1, 1, 1, 0));
    }

    @Test
    public void signedInkAndCorrectionsRemainIndependentOfAdvance() {
        MathGlyphMetrics metrics = new MathGlyphMetrics(0, -3, -8, 5, -1, -2, true, -4);
        Assert.assertEquals(-3, metrics.getInkLeft(), 0);
        Assert.assertEquals(-8, metrics.getInkTop(), 0);
        Assert.assertEquals(-2, metrics.getItalicCorrection(), 0);
        Assert.assertTrue(metrics.hasTopAccentAttachment());
        Assert.assertEquals(-4, metrics.getTopAccentAttachment(), 0);
        Assert.assertFalse(new MathGlyphMetrics(0, 0, 0, 0, 0, 0, false, 0).hasTopAccentAttachment());
        expect(IllegalArgumentException.class, () -> new MathGlyphMetrics(-1, 0, 0, 1, 1, 0, false, 0));
        expect(IllegalArgumentException.class, () -> new MathGlyphMetrics(1, 2, 0, 1, 1, 0, false, 0));
        expect(IllegalArgumentException.class, () -> new MathGlyphMetrics(1, 0, 2, 1, 1, 0, false, 0));
        for (float bad : new float[] { Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY }) {
            expect(IllegalArgumentException.class, () -> new MathGlyphMetrics(bad, 0, 0, 1, 1, 0, false, 0));
            expect(IllegalArgumentException.class, () -> new MathGlyphMetrics(1, bad, 0, 1, 1, 0, false, 0));
            expect(IllegalArgumentException.class, () -> new MathGlyphMetrics(1, 0, 0, 1, 1, bad, false, 0));
            expect(IllegalArgumentException.class, () -> new MathGlyphMetrics(1, 0, 0, 1, 1, 0, false, bad));
        }
    }

    @Test
    public void radicalRaiseStaysDimensionlessUntilActualRadicalHeightIsKnown() {
        MathFontParameters params = new MathFontParameters(2, 1, 3, 4, 1, 60, 5);
        Assert.assertEquals(60, params.getRadicalDegreeBottomRaisePercent());
        Assert.assertEquals(4, params.getRadicalDisplayStyleVerticalGap(), 0);
        new MathFontParameters(0, 0, 0, 0, 0, 0, 0);
        new MathFontParameters(0, 0, 0, 0, 0, 100, 0);
        expect(IllegalArgumentException.class, () -> new MathFontParameters(0, 0, 0, 0, 0, -1, 0));
        expect(IllegalArgumentException.class, () -> new MathFontParameters(0, 0, 0, 0, 0, 101, 0));
        expect(IllegalArgumentException.class, () -> new MathFontParameters(0, -1, 0, 0, 0, 60, 0));
        expect(IllegalArgumentException.class, () -> new MathFontParameters(0, 0, Float.NaN, 0, 0, 60, 0));
    }

    @Test
    public void constructionCopiesListsAndKeepsOrderAndSignedCorrection() {
        MathGlyphRef ref = glyph("face", 1);
        List<MathGlyphConstruction.Variant> variants = new ArrayList<MathGlyphConstruction.Variant>();
        variants.add(new MathGlyphConstruction.Variant(ref, 10));
        variants.add(new MathGlyphConstruction.Variant(glyph("face", 2), 20));
        List<MathGlyphConstruction.Part> parts = new ArrayList<MathGlyphConstruction.Part>();
        parts.add(new MathGlyphConstruction.Part(ref, 0, 2, 10, false));
        parts.add(new MathGlyphConstruction.Part(ref, 2, 2, 10, true));
        parts.add(new MathGlyphConstruction.Part(ref, 2, 0, 10, false));
        MathGlyphConstruction.Assembly assembly = new MathGlyphConstruction.Assembly(parts, 1, -2);
        MathGlyphConstruction construction = new MathGlyphConstruction(variants, assembly);
        variants.clear();
        parts.clear();
        Assert.assertEquals(10, construction.getVariants().get(0).getStretchAdvance(), 0);
        Assert.assertEquals(20, construction.getVariants().get(1).getStretchAdvance(), 0);
        Assert.assertTrue(assembly.getParts().get(1).isExtender());
        Assert.assertEquals(-2, assembly.getItalicCorrection(), 0);
        expect(UnsupportedOperationException.class, () -> construction.getVariants().clear());
        expect(UnsupportedOperationException.class, () -> assembly.getParts().clear());
        Assert.assertNull(new MathGlyphConstruction(construction.getVariants(), null).getAssembly());
    }

    @Test
    public void constructionRejectsUnusableConnectorsAndMixedFaces() {
        MathGlyphRef ref = glyph("face", 1);
        expect(IllegalArgumentException.class, () -> new MathGlyphConstruction.Part(ref, 3, 0, 2, false));
        expect(IllegalArgumentException.class, () -> new MathGlyphConstruction.Part(ref, 0, 0, 0, true));
        expect(IllegalArgumentException.class, () -> new MathGlyphConstruction.Part(ref, Float.NaN, 0, 2, false));
        expect(IllegalArgumentException.class, () -> new MathGlyphConstruction.Variant(ref, Float.POSITIVE_INFINITY));
        expect(IllegalArgumentException.class, () -> new MathGlyphConstruction.Assembly(
                Collections.singletonList(new MathGlyphConstruction.Part(ref, 0, 1, 2, true)), 1, 0));
        expect(IllegalArgumentException.class, () -> new MathGlyphConstruction.Assembly(Arrays.asList(
                new MathGlyphConstruction.Part(ref, 0, 0, 2, false),
                new MathGlyphConstruction.Part(ref, 1, 0, 2, false)), 1, 0));
        expect(IllegalArgumentException.class, () -> new MathGlyphConstruction(Arrays.asList(
                new MathGlyphConstruction.Variant(ref, 2),
                new MathGlyphConstruction.Variant(glyph("other", 1), 3)), null));
        expect(IllegalArgumentException.class, () -> new MathGlyphConstruction(Arrays.asList(
                new MathGlyphConstruction.Variant(ref, 3), new MathGlyphConstruction.Variant(ref, 2)), null));
        expect(IllegalArgumentException.class, () -> new MathGlyphConstruction(null, null));
        expect(IllegalArgumentException.class, () -> new MathGlyphConstruction(
                Collections.<MathGlyphConstruction.Variant>singletonList(null), null));
        expect(IllegalArgumentException.class, () -> new MathGlyphConstruction.Assembly(
                Collections.<MathGlyphConstruction.Part>emptyList(), 0, 0));
    }

    private static void expect(Class<? extends Throwable> type, Runnable operation) {
        try {
            operation.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) { return; }
            throw new AssertionError("错误异常类型", failure);
        }
        Assert.fail("预期异常: " + type.getName());
    }
}
