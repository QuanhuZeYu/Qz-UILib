package club.heiqi.uilib.font.latex.layout;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.latex.MathFontStyle;

/** 裁片是 glyph-local 的已生效 px；坐标平移和字号参照换算必须保留同一个值。 */
public class MathGlyphClipGeometryTest {
    @Test
    public void validatesDirectionalInfinityOrderingAndValueIdentity() {
        MathGlyphClip clip = new MathGlyphClip(Float.NEGATIVE_INFINITY, -8, Float.POSITIVE_INFINITY, -2);
        MathGlyphClip same = new MathGlyphClip(Float.NEGATIVE_INFINITY, -8, Float.POSITIVE_INFINITY, -2);
        Assert.assertEquals(clip, same);
        Assert.assertEquals(clip.hashCode(), same.hashCode());
        Assert.assertNotEquals(clip, new MathGlyphClip(Float.NEGATIVE_INFINITY, -7, Float.POSITIVE_INFINITY, -2));
        Assert.assertEquals(-8, clip.getTop(), 0);
        Assert.assertEquals(-2, clip.getBottom(), 0);
        for (int index = 0; index < 4; index++) {
            float[] values = {-2, -3, 4, 5};
            values[index] = Float.NaN;
            rejects(values);
        }
        rejects(new float[] {Float.POSITIVE_INFINITY, 0, Float.POSITIVE_INFINITY, 1});
        rejects(new float[] {0, Float.POSITIVE_INFINITY, 1, Float.POSITIVE_INFINITY});
        rejects(new float[] {Float.NEGATIVE_INFINITY, 0, Float.NEGATIVE_INFINITY, 1});
        rejects(new float[] {0, Float.NEGATIVE_INFINITY, 1, Float.NEGATIVE_INFINITY});
        rejects(new float[] {2, 0, 1, 1});
        rejects(new float[] {0, 2, 1, 1});
        new MathGlyphClip(0, 0, 0, 0); // 空范围合法，collector 可跳过。
    }

    @Test
    public void everyLegacyConstructorDefaultsToNoClip() {
        MathGlyphRef ref = MathGlyphRef.forFontGlyph("clip-test", 1);
        GlyphElem[] old = {
                new GlyphElem("x", 0, 0, 1),
                new GlyphElem("x", 0, 0, 1, true),
                new GlyphElem("x", 0, 0, 1, true, false),
                new GlyphElem("x", 0, 0, 1, true, false, MathFontStyle.BOLD),
                new GlyphElem("x", 0, 0, 1, true, false, MathFontStyle.BOLD, ref)};
        for (GlyphElem glyph : old) { Assert.assertNull(glyph.getMathGlyphClip()); }
    }

    @Test
    public void normalizationTranslationAndChildCompositionPreserveLocalClip() throws Exception {
        MathGlyphClip clip = new MathGlyphClip(Float.NEGATIVE_INFINITY, -8, Float.POSITIVE_INFINITY, -2);
        MathGlyphRef ref = MathGlyphRef.forFontGlyph("clip-test", 1);
        GlyphElem source = new GlyphElem("x", 3, -4, 1, false, false, MathFontStyle.UPRIGHT, ref, clip);
        MathBox box = new MathBox(12, 16, 4, Collections.singletonList(source), null);
        Method normalize = MathLayoutService.class.getDeclaredMethod("normalizeGlyphScale", MathBox.class, float.class);
        normalize.setAccessible(true);
        MathBox normalized = (MathBox) normalize.invoke(null, box, 0.5F);
        assertCopy(source, normalized.getGlyphs().get(0), 3, -4, 0.5F);
        Method shift = MathLayoutService.class.getDeclaredMethod("shiftBox", MathBox.class, float.class, float.class);
        shift.setAccessible(true);
        MathBox shifted = (MathBox) shift.invoke(null, normalized, 2F, -3F);
        assertCopy(source, shifted.getGlyphs().get(0), source.getX() + 2F, source.getY() - 3F, 0.5F);
        Class<?> builderType = Class.forName(MathLayoutService.class.getName() + "$Builder");
        Constructor<?> constructor = builderType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object builder = constructor.newInstance();
        Method addBox = builderType.getDeclaredMethod("addBox", MathBox.class, float.class, float.class, float.class);
        addBox.setAccessible(true);
        addBox.invoke(builder, box, -2F, 3F, 0.5F);
        Method toBox = builderType.getDeclaredMethod("toBox");
        toBox.setAccessible(true);
        MathBox composed = (MathBox) toBox.invoke(builder);
        assertCopy(source, composed.getGlyphs().get(0), source.getX() - 2F, source.getY() + 3F, 0.5F);
    }

    private static void assertCopy(GlyphElem source, GlyphElem copy, float x, float y, float scale) {
        Assert.assertSame(source.getMathGlyphClip(), copy.getMathGlyphClip());
        Assert.assertEquals(source.getMathGlyphRef(), copy.getMathGlyphRef());
        Assert.assertEquals(source.getMathFontStyle(), copy.getMathFontStyle());
        Assert.assertEquals(x, copy.getX(), 0);
        Assert.assertEquals(y, copy.getY(), 0);
        Assert.assertEquals(scale, copy.getSizeScale(), 0);
    }

    private static void rejects(float[] values) {
        try {
            new MathGlyphClip(values[0], values[1], values[2], values[3]);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 不接受 NaN 或方向错误的无限边界。
        }
    }
}
