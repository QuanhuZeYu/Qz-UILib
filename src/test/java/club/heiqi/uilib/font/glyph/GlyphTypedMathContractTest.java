package club.heiqi.uilib.font.glyph;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.layout.MathGlyphRef;
import club.heiqi.uilib.font.latex.layout.ProceduralAccentSpec;

/** 数学请求必须保持物理身份、尺寸和发布快照，同时保留旧码点 API。 */
public class GlyphTypedMathContractTest {
    private static final MathGlyphRef REF = MathGlyphRef.forFontGlyph("face-a", 42);

    @Test
    public void tokensSeparateEveryIdentityDimensionAndKind() {
        GlyphRequestToken token = token(1, REF, 32, 0);
        Assert.assertEquals(token, token(1, MathGlyphRef.forFontGlyph("face-a", 42), 32, 0));
        Assert.assertEquals(token.hashCode(), token(1, REF, 32, 0).hashCode());
        Set<GlyphRequestToken> tokens = new HashSet<GlyphRequestToken>();
        tokens.add(token);
        tokens.add(token(2, REF, 32, 0));
        tokens.add(token(1, MathGlyphRef.forFontGlyph("face-b", 42), 32, 0));
        tokens.add(token(1, MathGlyphRef.forFontGlyph("face-a", 43), 32, 0));
        tokens.add(token(1, REF, 64, 0));
        tokens.add(token(1, REF, 32, 1));
        tokens.add(GlyphRequestToken.forMathGlyph(1, 2L, REF, 32, 0));
        tokens.add(new GlyphRequestToken(1, 1L, 42, FontType.NORMAL));
        Assert.assertEquals(8, tokens.size());
        MathGlyphRef accent = MathGlyphRef.forProceduralAccent(
                ProceduralAccentSpec.of(ProceduralAccentSpec.Kind.HAT, 1, 65536, 8192, 1024));
        Assert.assertEquals(accent, token(1, accent, 32, 0).getMathGlyphRef());
        Assert.assertFalse(token.toString().contains("codepoint="));
        rejects(IllegalStateException.class, token::getCodepoint);
        rejects(IllegalStateException.class, token::getFontType);
        rejects(IllegalArgumentException.class, () -> token(1, REF, 0, 0));
        rejects(IllegalArgumentException.class, () -> token(1, REF, 32, -1));
        rejects(IllegalArgumentException.class, () -> token(1, null, 32, 0));
        rejects(IllegalArgumentException.class, () -> GlyphRequestToken.forMathGlyph(1, 0L, REF, 32, 0));
    }

    @Test
    public void claimSharesDemandPromotionsAndRejectsIdentityOrSizeDrift() {
        GlyphGenerationTask demand = GlyphGenerationTask.forMathGlyph(1, REF, 32, 2, GlyphDemandLevel.WARMUP);
        Assert.assertNull(demand.getToken());
        Assert.assertEquals(GlyphRequestToken.Kind.MATH_GLYPH, demand.getKind());
        GlyphGenerationTask worker = demand.claimedBy(token(1, REF, 32, 2));
        Assert.assertTrue(demand.promoteTo(GlyphDemandLevel.PREFETCH));
        Assert.assertEquals(GlyphDemandLevel.PREFETCH, worker.getDemandLevel());
        Assert.assertTrue(worker.promoteTo(GlyphDemandLevel.VISIBLE));
        Assert.assertEquals(GlyphDemandLevel.VISIBLE, demand.getDemandLevel());
        Assert.assertFalse(demand.promoteTo(GlyphDemandLevel.FOREGROUND));
        Assert.assertEquals(32, worker.getGlyphSize());
        Assert.assertEquals(worker.getToken().getRasterSize(), worker.getRasterSize());
        Assert.assertEquals(2, worker.getTileIndex());
        rejects(IllegalStateException.class, worker::getCodepoint);
        rejects(IllegalStateException.class, worker::getFontType);
        rejects(IllegalStateException.class, () -> worker.claimedBy(token(1, REF, 32, 2)));
        rejects(IllegalArgumentException.class, () -> demand.claimedBy(token(2, REF, 32, 2)));
        rejects(IllegalArgumentException.class, () -> demand.claimedBy(token(1, REF, 64, 2)));
        rejects(IllegalArgumentException.class, () -> demand.claimedBy(token(1, REF, 32, 0)));
        rejects(IllegalArgumentException.class, () -> demand.claimedBy(
                token(1, MathGlyphRef.forFontGlyph("other", 42), 32, 2)));
        rejects(IllegalArgumentException.class, () -> demand.claimedBy(new GlyphRequestToken(1, 1L, 42, FontType.NORMAL)));
        rejects(IllegalArgumentException.class,
                () -> new GlyphGenerationTask(token(1, REF, 32, 0), 64, GlyphGenerationPriority.HIGH));
        Assert.assertEquals(32, new GlyphGenerationTask(token(1, REF, 32, 0), 32,
                GlyphGenerationPriority.HIGH).getGlyphSize());
        rejects(IllegalArgumentException.class,
                () -> GlyphGenerationTask.forMathGlyph(1, REF, 0, 0, GlyphGenerationPriority.LOW));
    }

    @Test
    public void mathResultFreezesPixelsMetadataAndSignedBaselines() {
        final float[] mutableAdvance = { 7.5F };
        GlyphInfo source = new GlyphInfo(REF, 32, 32, 7.5F, 8, 2, 1, 5, 6,
                2, 2, -3, -4, 20, 3, 4, true, false) {
            @Override public float getAdvance() { return mutableAdvance[0]; }
        };
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x7F123456);
        GlyphGenerationResult result = GlyphGenerationResult.forMathGlyph(token(1, REF, 32, 0), image, source);
        mutableAdvance[0] = 99;
        image.setRGB(0, 0, 0);
        result.getImage().setRGB(0, 0, 0);
        result.copyRgbaPixels()[0] = 0;
        Assert.assertEquals(0x7F123456, result.getImage().getRGB(0, 0));
        GlyphInfo frozen = result.getMathGlyphInfo();
        Assert.assertNotSame(source, frozen);
        Assert.assertEquals(GlyphInfo.class, frozen.getClass());
        Assert.assertEquals(7.5F, frozen.getAdvance(), 0);
        Assert.assertEquals(-3, frozen.getAtlasBaselineX());
        Assert.assertEquals(-4, frozen.getAtlasBaselineY());
        Assert.assertEquals(REF, frozen.getMathGlyphRef());
        Assert.assertEquals(REF, result.getMathGlyphRef());
        Assert.assertEquals(32, result.getRasterSize());
        Assert.assertEquals(0, result.getTileIndex());
        rejects(IllegalStateException.class, frozen::getCodepoint);
        rejects(IllegalStateException.class, result::getCodepoint);
        rejects(IllegalStateException.class, result::getFontType);
        rejects(IllegalStateException.class, result::getGlyphInfo);
        rejects(IllegalArgumentException.class, () -> new GlyphGenerationResult(token(1, REF, 32, 0), null, source));
        rejects(IllegalArgumentException.class, () -> GlyphGenerationResult.forMathGlyph(
                token(1, MathGlyphRef.forFontGlyph("other", 42), 32, 0), null, source));
        rejects(IllegalArgumentException.class, () -> GlyphGenerationResult.forMathGlyph(
                token(1, REF, 32, 0), null, new GlyphInfo(42, 2, 2, 1, 1, 1, false)));
        GlyphGenerationResult empty = GlyphGenerationResult.forMathGlyph(token(1, REF, 32, 0), null, null);
        Assert.assertNull(empty.getMathGlyphInfo());
        Assert.assertNull(empty.copyRgbaPixels());
    }

    @Test
    public void legacyCodepointContractsRemainAvailable() {
        GlyphRequestToken token = new GlyphRequestToken(1, 1L, 'x', FontType.NORMAL);
        GlyphGenerationTask task = new GlyphGenerationTask(1, 'x', FontType.NORMAL, 32, GlyphGenerationPriority.LOW)
                .claimedBy(token);
        GlyphInfo info = new GlyphInfo('x', 2, 2, 1, 1, 1, false);
        GlyphGenerationResult result = new GlyphGenerationResult(token, null, info);
        Assert.assertEquals(GlyphRequestToken.Kind.CODEPOINT, task.getKind());
        Assert.assertEquals('x', task.getCodepoint());
        Assert.assertEquals(FontType.NORMAL, task.getFontType());
        Assert.assertEquals('x', result.getGlyphInfo().getCodepoint());
        Assert.assertNotSame(info, result.getGlyphInfo());
        Assert.assertNull(new GlyphGenerationResult(token, null, null).getGlyphInfo());
        Assert.assertNull(GlyphInfo.copyOf(null));
        rejects(IllegalStateException.class, token::getMathGlyphRef);
        rejects(IllegalStateException.class, token::getRasterSize);
        rejects(IllegalStateException.class, token::getTileIndex);
        rejects(IllegalStateException.class, task::getMathGlyphRef);
        rejects(IllegalStateException.class, task::getRasterSize);
        rejects(IllegalStateException.class, task::getTileIndex);
        rejects(IllegalStateException.class, result::getMathGlyphInfo);
        rejects(IllegalStateException.class, result::getMathGlyphRef);
        rejects(IllegalStateException.class, result::getRasterSize);
        rejects(IllegalStateException.class, result::getTileIndex);
        rejects(IllegalStateException.class, info::getMathGlyphRef);
        rejects(IllegalArgumentException.class, () -> GlyphGenerationResult.forMathGlyph(token, null, null));
    }

    private static GlyphRequestToken token(int generation, MathGlyphRef ref, int size, int tile) {
        return GlyphRequestToken.forMathGlyph(generation, 1L, ref, size, tile);
    }

    private static void rejects(Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
            Assert.fail("expected " + type.getSimpleName());
        } catch (RuntimeException failure) {
            if (!type.isInstance(failure)) { throw failure; }
        }
    }
}
