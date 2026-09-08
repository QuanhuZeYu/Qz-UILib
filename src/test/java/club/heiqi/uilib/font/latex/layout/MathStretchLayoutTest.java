package club.heiqi.uilib.font.latex.layout;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.MathFontStyle;
import club.heiqi.uilib.font.latex.layout.MathGlyphConstruction.Assembly;
import club.heiqi.uilib.font.latex.layout.MathGlyphConstruction.Part;
import club.heiqi.uilib.font.latex.layout.MathGlyphConstruction.Variant;

/** Synthetic 字体直接提供 px 度量；不启动字体服务或平台 renderer。 */
public class MathStretchLayoutTest {
    private static final float EPS = 0.0001F;
    private static final MathGlyphRef ROOT = ref(0);

    @Test
    public void glyphBoxKeepsRealAdvanceSignedInkAndEffectiveSize() {
        Synthetic support = new Synthetic();
        support.metrics.put(ROOT, metrics(6, -2, -10, 11, -1));
        MathBox box = MathStretchLayout.glyphBox("(", ROOT, support, 12, 24);
        Assert.assertEquals(6, box.getWidth(), 0);
        Assert.assertEquals(10, box.getHeight(), 0);
        Assert.assertEquals(0, box.getDepth(), 0);
        Assert.assertEquals(2, box.getLeftInkOverhang(), 0);
        Assert.assertEquals(11 - 6, box.getRightInkOverhang(), 0);
        GlyphElem glyph = box.getGlyphs().get(0);
        Assert.assertEquals(ROOT, glyph.getMathGlyphRef());
        Assert.assertNull(glyph.getMathGlyphClip());
        Assert.assertEquals(12F / 24F, glyph.getSizeScale(), 0);
        Assert.assertFalse(glyph.isItalic());
        Assert.assertFalse(glyph.isInheritTextItalic());
        Assert.assertEquals(MathFontStyle.UPRIGHT, glyph.getMathFontStyle());
        Assert.assertEquals(12, support.lastSize);
    }

    @Test
    public void variantsUseStretchAdvanceAtAndAcrossExactThreshold() {
        Synthetic support = new Synthetic();
        support.construction = new MathGlyphConstruction(Arrays.asList(
                new Variant(ref(1), 10), new Variant(ref(2), 20)), null);
        for (MathStretchAxis axis : MathStretchAxis.values()) {
            assertRef(ref(1), stretch(support, axis, 1));
            assertRef(ref(1), stretch(support, axis, 10));
            assertRef(ref(2), stretch(support, axis, Math.nextUp(10F)));
            assertRef(ref(2), stretch(support, axis, 20));
            MathBox largest = stretch(support, axis, Float.MAX_VALUE);
            assertRef(ref(2), largest);
            Assert.assertEquals(6, largest.getWidth(), 0);
            Assert.assertEquals(1, largest.getGlyphs().get(0).getSizeScale(), 0);
            Assert.assertEquals(axis, support.lastAxis);
        }
        support.construction = new MathGlyphConstruction(Collections.<Variant>emptyList(), null);
        assertRef(ROOT, stretch(support, MathStretchAxis.VERTICAL, 100));
        support.construction = null;
        assertRef(ROOT, stretch(support, MathStretchAxis.HORIZONTAL, 100));
    }

    @Test
    public void nativeEqualThresholdWinsBeforeAssembly() {
        Synthetic support = pair();
        support.construction = new MathGlyphConstruction(Collections.singletonList(new Variant(ref(9), 17)),
                support.construction.getAssembly());
        assertRef(ref(9), stretch(support, MathStretchAxis.HORIZONTAL, 17));
        Assert.assertEquals(2, stretch(support, MathStretchAxis.HORIZONTAL, Math.nextUp(17F)).getGlyphs().size());
    }

    @Test
    public void horizontalSeamUsesConnectorsAndIndependentAdvance() {
        MathBox box = stretch(pair(), MathStretchAxis.HORIZONTAL, 17);
        Assert.assertEquals(17, box.getWidth(), 0);
        Assert.assertEquals(0, box.getGlyphs().get(0).getX(), 0);
        Assert.assertEquals(17 - 10, box.getGlyphs().get(1).getX(), EPS);
        Assert.assertEquals(2, box.getLeftInkOverhang(), 0);
        Assert.assertEquals((17 - 10) + 11 - 17, box.getRightInkOverhang(), EPS);
        assertSeams(box, MathStretchAxis.HORIZONTAL, new float[] {10, 10}, 1, 4);
    }

    @Test
    public void verticalOrderKeepsGlyphOriginAndNaturalBaseline() {
        MathBox box = stretch(pair(), MathStretchAxis.VERTICAL, 17);
        Assert.assertEquals(ref(1), box.getGlyphs().get(0).getMathGlyphRef());
        Assert.assertEquals(ref(2), box.getGlyphs().get(1).getMathGlyphRef());
        Assert.assertEquals(0, box.getGlyphs().get(0).getY(), 0);
        Assert.assertEquals(-(17 - 10), box.getGlyphs().get(1).getY(), EPS);
        Assert.assertEquals(17, box.getHeight(), EPS);
        Assert.assertEquals(0, box.getDepth(), 0);
        Assert.assertEquals(6, box.getWidth(), 0);
        Assert.assertEquals(11 - 6, box.getRightInkOverhang(), 0);
        assertSeams(box, MathStretchAxis.VERTICAL, new float[] {10, 10}, 1, 4);
        Synthetic offsetInk = pair();
        offsetInk.metrics.put(ref(1), metrics(6, -2, -10, 2, 3));
        MathBox offset = stretch(offsetInk, MathStretchAxis.VERTICAL, 17);
        Assert.assertEquals(0, offset.getGlyphs().get(0).getY(), 0);
        Assert.assertEquals(-(17 - 10), offset.getGlyphs().get(1).getY(), EPS);
        Assert.assertEquals(3, offset.getDepth(), 0);
        assertSeams(offset, MathStretchAxis.VERTICAL, new float[] {10, 10}, 1, 4);
        for (int i = 0; i < box.getGlyphs().size(); i++) {
            Assert.assertEquals("ink offsets must not move connector cuts",
                    box.getGlyphs().get(i).getMathGlyphClip(), offset.getGlyphs().get(i).getMathGlyphClip());
        }
    }

    @Test
    public void maximumOverlapStopsAtSmallestNativeAssembly() {
        MathBox box = stretch(pair(), MathStretchAxis.HORIZONTAL, 1);
        Assert.assertEquals(10 + 10 - 4, box.getWidth(), 0);
        assertSeams(box, MathStretchAxis.HORIZONTAL, new float[] {10, 10}, 4, 4);
    }

    @Test
    public void extendersRepeatEquallyWithMinimumCountAndContinuousSeams() {
        Synthetic support = new Synthetic();
        support.construction = assembly(1, part(1, 10, 3, false), part(2, 8, 3, true),
                part(3, 6, 3, false), part(4, 8, 3, true), part(5, 10, 3, false));
        MathBox box = stretch(support, MathStretchAxis.HORIZONTAL, 50);
        Assert.assertEquals(7, box.getGlyphs().size());
        int[] ids = {1, 2, 2, 3, 4, 4, 5};
        float[] advances = {10, 8, 8, 6, 8, 8, 10};
        // Python 独立验算：repeat=2，overlap=1.3333333333333333。
        float[] cursors = {0, 8.6666667F, 15.3333333F, 22, 26.6666667F, 33.3333333F, 40};
        MathBox vertical = stretch(support, MathStretchAxis.VERTICAL, 50);
        for (int i = 0; i < ids.length; i++) {
            Assert.assertEquals(ref(ids[i]), box.getGlyphs().get(i).getMathGlyphRef());
            Assert.assertEquals(cursors[i], box.getGlyphs().get(i).getX(), EPS);
            Assert.assertEquals(-cursors[i], vertical.getGlyphs().get(i).getY(), EPS);
        }
        Assert.assertEquals(50, box.getWidth(), EPS);
        Assert.assertEquals(1, box.getRightInkOverhang(), EPS);
        assertSeams(box, MathStretchAxis.HORIZONTAL, advances, 1, 3);
        assertSeams(vertical, MathStretchAxis.VERTICAL, advances, 1, 3);
        Assert.assertEquals(2, stretch(support, MathStretchAxis.HORIZONTAL, 50).getGlyphs().stream()
                .filter(g -> g.getMathGlyphRef().equals(ref(2))).count());
        // 以同一最小 overlap 求上一份数可达上界，必须小于 target。
        Assert.assertTrue(10 + 8 + 6 + 8 + 10 - 4 * 1 < 50);
        MathBox noExtenders = stretch(support, MathStretchAxis.HORIZONTAL, 1);
        Assert.assertEquals(3, noExtenders.getGlyphs().size());
    }

    @Test
    public void clipPixelsIgnoreParentSizeScaleAndKeepOuterEndsUnbounded() {
        for (MathStretchAxis axis : MathStretchAxis.values()) {
            MathBox box = MathStretchLayout.stretch("(", ROOT, pair(), axis, 17, 12, 24);
            MathBox unscaled = stretch(pair(), axis, 17);
            Assert.assertEquals(unscaled.getWidth(), box.getWidth(), 0);
            Assert.assertEquals(unscaled.getHeight(), box.getHeight(), 0);
            Assert.assertEquals(unscaled.getDepth(), box.getDepth(), 0);
            for (int i = 0; i < box.getGlyphs().size(); i++) {
                Assert.assertEquals(12F / 24F, box.getGlyphs().get(i).getSizeScale(), 0);
                Assert.assertEquals(unscaled.getGlyphs().get(i).getMathGlyphClip(),
                        box.getGlyphs().get(i).getMathGlyphClip());
            }
            MathGlyphClip first = box.getGlyphs().get(0).getMathGlyphClip();
            MathGlyphClip last = box.getGlyphs().get(box.getGlyphs().size() - 1).getMathGlyphClip();
            Assert.assertEquals(Float.NEGATIVE_INFINITY,
                    axis == MathStretchAxis.HORIZONTAL ? first.getLeft() : last.getTop(), 0);
            Assert.assertEquals(Float.POSITIVE_INFINITY,
                    axis == MathStretchAxis.HORIZONTAL ? last.getRight() : first.getBottom(), 0);
            assertSeams(box, axis, new float[] {10, 10}, 1, 4);
        }
    }

    @Test
    public void rejectsRootFaceMismatchEvenWhenAFirstVariantWouldFit() {
        Synthetic support = new Synthetic();
        MathGlyphRef foreign = MathGlyphRef.forFontGlyph("other-face", 1);
        support.construction = new MathGlyphConstruction(Collections.singletonList(new Variant(foreign, 20)), null);
        rejects(() -> stretch(support, MathStretchAxis.VERTICAL, 1));
        support.construction = new MathGlyphConstruction(Collections.<Variant>emptyList(),
                new Assembly(Collections.singletonList(new Part(foreign, 1, 1, 10, false)), 1, 0));
        rejects(() -> stretch(support, MathStretchAxis.HORIZONTAL, 1));
    }

    @Test
    public void invalidTargetsSizesAndUnreachableAssembliesAreBounded() {
        Synthetic support = pair();
        for (float target : new float[] {0, -1, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            rejects(() -> stretch(support, MathStretchAxis.HORIZONTAL, target));
        }
        rejects(() -> stretch(support, null, 1));
        rejects(() -> MathStretchLayout.glyphBox("x", ROOT, support, 0, 12));
        rejects(() -> MathStretchLayout.glyphBox("x", ROOT, support, 12, Float.NaN));
        rejects(() -> MathStretchLayout.glyphBox("x", ROOT, support, 12, Float.MIN_VALUE));
        rejects(() -> stretch(support, MathStretchAxis.VERTICAL, Float.MAX_VALUE));
        support.construction = assembly(1, part(1, 1, 1, true));
        rejects(() -> stretch(support, MathStretchAxis.VERTICAL, 2));
        support.construction = assembly(0, part(1, 1, 0, true));
        Assert.assertEquals(4096, stretch(support, MathStretchAxis.HORIZONTAL, 4096).getGlyphs().size());
        rejects(() -> stretch(support, MathStretchAxis.HORIZONTAL, 4097));
        support.construction = new MathGlyphConstruction(Collections.<Variant>emptyList(),
                new Assembly(Collections.nCopies(4097, part(1, 1, 0, false)), 0, 0));
        rejects(() -> stretch(support, MathStretchAxis.HORIZONTAL, 1));
    }

    private static void assertSeams(MathBox box, MathStretchAxis axis, float[] advances, float min, float max) {
        for (int i = 1; i < box.getGlyphs().size(); i++) {
            GlyphElem previous = box.getGlyphs().get(i - 1);
            GlyphElem next = box.getGlyphs().get(i);
            float distance = axis == MathStretchAxis.HORIZONTAL
                    ? next.getX() - previous.getX() : previous.getY() - next.getY();
            float overlap = advances[i - 1] - distance;
            Assert.assertTrue("overlap below min", overlap >= min - EPS);
            Assert.assertTrue("overlap above connector", overlap <= max + EPS);
            MathGlyphClip a = previous.getMathGlyphClip();
            MathGlyphClip b = next.getMathGlyphClip();
            Assert.assertNotNull(a);
            Assert.assertNotNull(b);
            if (axis == MathStretchAxis.HORIZONTAL) {
                float seam = (previous.getX() + advances[i - 1] + next.getX()) / 2;
                Assert.assertEquals("previous visible end is connector midpoint", seam,
                        previous.getX() + a.getRight(), EPS);
                Assert.assertEquals("no gap and no duplicate owner", previous.getX() + a.getRight(),
                        next.getX() + b.getLeft(), EPS);
                Assert.assertEquals(Float.NEGATIVE_INFINITY, a.getTop(), 0);
                Assert.assertEquals(Float.POSITIVE_INFINITY, b.getBottom(), 0);
            } else {
                float seam = (previous.getY() - advances[i - 1] + next.getY()) / 2;
                Assert.assertEquals("bottom-to-top visible end is connector midpoint", seam,
                        previous.getY() + a.getTop(), EPS);
                Assert.assertEquals("no gap and no duplicate owner", previous.getY() + a.getTop(),
                        next.getY() + b.getBottom(), EPS);
                Assert.assertEquals(Float.NEGATIVE_INFINITY, a.getLeft(), 0);
                Assert.assertEquals(Float.POSITIVE_INFINITY, b.getRight(), 0);
            }
        }
    }

    private static MathBox stretch(Synthetic support, MathStretchAxis axis, float target) {
        return MathStretchLayout.stretch("(", ROOT, support, axis, target, 12, 12);
    }

    private static Synthetic pair() {
        Synthetic support = new Synthetic();
        support.construction = assembly(1, part(1, 10, 4, false), part(2, 10, 4, false));
        return support;
    }

    private static MathGlyphConstruction assembly(float min, Part... parts) {
        return new MathGlyphConstruction(Collections.<Variant>emptyList(), new Assembly(Arrays.asList(parts), min, 0));
    }

    private static Part part(int id, float advance, float connector, boolean extender) {
        return new Part(ref(id), connector, connector, advance, extender);
    }

    private static MathGlyphRef ref(int id) { return MathGlyphRef.forFontGlyph("synthetic-face", id); }

    private static MathGlyphMetrics metrics(float advance, float left, float top, float right, float bottom) {
        return new MathGlyphMetrics(advance, left, top, right, bottom, 0, false, 0);
    }

    private static void assertRef(MathGlyphRef expected, MathBox box) {
        Assert.assertEquals(1, box.getGlyphs().size());
        Assert.assertEquals(expected, box.getGlyphs().get(0).getMathGlyphRef());
    }

    private static void rejects(Runnable action) {
        try {
            action.run();
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 有界且明确的失败，不接受 NPE/溢出/无限循环。
        }
    }

    private static final class Synthetic implements MathFontSupport {
        private final Map<MathGlyphRef, MathGlyphMetrics> metrics = new HashMap<MathGlyphRef, MathGlyphMetrics>();
        private MathGlyphConstruction construction;
        private int lastSize;
        private MathStretchAxis lastAxis;

        @Override
        public MathGlyphRef resolve(int codepoint, MathFontStyle style, FontType weight) { return ROOT; }

        @Override
        public MathGlyphMetrics measure(MathGlyphRef glyph, int effectiveSizePx) {
            Assert.assertEquals("synthetic-face", glyph.getFaceKey());
            lastSize = effectiveSizePx;
            MathGlyphMetrics value = metrics.get(glyph);
            return value == null ? metrics(6, -2, -10, 11, 0) : value;
        }

        @Override
        public MathFontParameters constants(int effectiveSizePx) {
            throw new AssertionError("helper must not move the math axis");
        }

        @Override
        public MathGlyphConstruction construction(MathGlyphRef glyph, MathStretchAxis axis, int effectiveSizePx) {
            Assert.assertEquals(ROOT, glyph);
            Assert.assertEquals(12, effectiveSizePx);
            lastAxis = axis;
            return construction;
        }
    }
}
