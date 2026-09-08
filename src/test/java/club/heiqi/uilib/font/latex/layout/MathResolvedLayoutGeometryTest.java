package club.heiqi.uilib.font.latex.layout;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.LatexParser;
import club.heiqi.uilib.font.latex.MathFontStyle;
import club.heiqi.uilib.font.latex.MathStyleOverride;
import club.heiqi.uilib.font.latex.node.LatexAccent;
import club.heiqi.uilib.font.latex.node.LatexAtom;
import club.heiqi.uilib.font.latex.node.LatexAtom.AtomClass;
import club.heiqi.uilib.font.latex.node.LatexAtom.OperatorMode;
import club.heiqi.uilib.font.latex.node.LatexFrac;
import club.heiqi.uilib.font.latex.node.LatexGroup;
import club.heiqi.uilib.font.latex.node.LatexLeftRight;
import club.heiqi.uilib.font.latex.node.LatexSqrt;
import club.heiqi.uilib.font.latex.node.LatexSupSub;

/** 与生产字体资源独立的真实引用路径几何锁。 */
public class MathResolvedLayoutGeometryTest {
    private static final float SIZE = 10;
    private static final float EPS = 1e-4F;
    private static final int FENCE_VARIANT = 50001;
    private static final int ROOT_VARIANT = 50002;
    private static final int ACCENT_VARIANT = 50003;
    private static final int OP_VARIANT = 50004;
    private static final MathLayoutService SERVICE = new MathLayoutService();
    private static MathGlyphRef ref(int id) { return MathGlyphRef.forFontGlyph("fixture-face", id); }
    private static final MathFontSupport SUPPORT = new MathFontSupport() {
        public MathGlyphRef resolve(int cp, MathFontStyle font, FontType weight) { return ref(cp); }
        public MathGlyphMetrics measure(MathGlyphRef glyph, int size) {
            float scale = size / SIZE;
            if (glyph.getKind() == MathGlyphRef.Kind.PROCEDURAL_ACCENT) {
                ProceduralAccentSpec shape = glyph.getProceduralAccent();
                float width = shape.getWidthUnits() / 65536.0F * size;
                float height = shape.getHeightUnits() / 65536.0F * size;
                return new MathGlyphMetrics(width, 0, -height, width, 0, 0, true, width / 2);
            }
            int id = glyph.getGlyphId();
            if (id == FENCE_VARIANT || id == ROOT_VARIANT || id == OP_VARIANT) {
                return new MathGlyphMetrics(8 * scale, -scale, -(id == ROOT_VARIANT ? 28 : 18) * scale,
                        10 * scale, 2 * scale, 4 * scale, false, 0);
            }
            if (id == 0x302 || id == 0x303) {
                return new MathGlyphMetrics(0, -scale, -4 * scale, 5 * scale, -scale, 0, true, scale);
            }
            if (id == ACCENT_VARIANT) {
                return new MathGlyphMetrics(20 * scale, 0, -4 * scale, 20 * scale, -scale, 0, false, 0);
            }
            return new MathGlyphMetrics(6 * scale, -scale, -8 * scale, 8 * scale, 2 * scale,
                    2 * scale, true, 4 * scale);
        }
        public MathFontParameters constants(int size) {
            float scale = size / SIZE;
            return new MathFontParameters(3 * scale, 0.5F * scale, scale, 2 * scale,
                    scale, 60, 5 * scale);
        }
        public MathGlyphConstruction construction(MathGlyphRef glyph, MathStretchAxis axis, int size) {
            if (glyph.getKind() != MathGlyphRef.Kind.FONT_GLYPH) { return null; }
            int cp = glyph.getGlyphId();
            int id = cp == '(' || cp == ')' ? FENCE_VARIANT : cp == 0x221A ? ROOT_VARIANT
                    : cp == 0x222B ? OP_VARIANT : cp == 0x302 || cp == 0x303 ? ACCENT_VARIANT : -1;
            if (id < 0) { return null; }
            return new MathGlyphConstruction(Collections.singletonList(new MathGlyphConstruction.Variant(ref(id),
                    (id == ROOT_VARIANT ? 30 : 20) * size / SIZE)), null);
        }
    };
    private static final MathMetrics METRICS = new MathMetrics() {
        public MathFontSupport mathFontSupport() { return SUPPORT; }
        public float advance(String text, float size) { return text.length() * size * 100; }
        public float ascent(float size) { return size * 80; }
        public float descent(float size) { return size * 20; }
        public float xHeight(float size) { return size * 0.4F; }
        public float italicCorrection(String text, float size) { return size * 50; }
        public float italicOverhang(String text, float size) { return size * 30; }
    };
    private static LatexAtom atom(String text) { return new LatexAtom(text, AtomClass.ORD); }
    private static MathBox layout(LatexNode node) { return SERVICE.layout(Collections.singletonList(node), SIZE, METRICS); }
    private static GlyphElem glyph(MathBox box, String text) {
        for (GlyphElem glyph : box.getGlyphs()) { if (text.equals(glyph.getText())) { return glyph; } }
        throw new AssertionError(text);
    }
    private static MathGlyphMetrics measure(GlyphElem glyph) {
        return SUPPORT.measure(glyph.getMathGlyphRef(), Math.round(SIZE * glyph.getSizeScale()));
    }

    @Test
    public void physicalReferenceControlsInkAndDisablesSyntheticStyles() {
        MathBox box = layout(atom("x"));
        GlyphElem glyph = glyph(box, "x");
        Assert.assertEquals(ref('x'), glyph.getMathGlyphRef());
        Assert.assertFalse(glyph.isItalic());
        Assert.assertFalse(glyph.isInheritTextItalic());
        MathGlyphMetrics metrics = measure(glyph);
        Assert.assertEquals(metrics.getAdvance(), box.getWidth(), EPS);
        Assert.assertEquals(-metrics.getInkLeft(), box.getLeftInkOverhang(), EPS);
        Assert.assertEquals(metrics.getInkRight() - metrics.getAdvance(), box.getRightInkOverhang(), EPS);
        GlyphElem manual = new GlyphElem("x", 0, 0, 1, true, true, MathFontStyle.BOLD, ref('x'));
        Assert.assertFalse(manual.isItalic());
        Assert.assertFalse(manual.isInheritTextItalic());
        Assert.assertNull(new GlyphElem("x", 0, 0, 1).getMathGlyphRef());
        Assert.assertNull(glyph(layout(new LatexAtom("text", AtomClass.TEXT)), "text").getMathGlyphRef());
        Assert.assertNull(glyph(layout(atom("\\unknown")), "\\unknown").getMathGlyphRef());
    }

    @Test
    public void trueItalicCorrectionIsAppliedEvenWithoutSyntheticItalic() {
        MathBox box = layout(new LatexSupSub(atom("x"), atom("2"), atom("3")));
        MathGlyphMetrics base = measure(glyph(box, "x"));
        Assert.assertEquals(base.getAdvance() + base.getItalicCorrection(), glyph(box, "2").getX(), EPS);
        Assert.assertEquals(base.getAdvance(), glyph(box, "3").getX(), EPS);
        Assert.assertFalse(glyph(box, "x").isItalic());
    }

    @Test
    public void nativeFenceKeepsSizeAndCentersRealInkOnFontAxis() {
        MathBox box = layout(new LatexLeftRight("(", atom("x"), ")"));
        for (String token : new String[] { "(", ")" }) {
            GlyphElem glyph = glyph(box, token);
            Assert.assertEquals(ref(FENCE_VARIANT), glyph.getMathGlyphRef());
            Assert.assertEquals(1, glyph.getSizeScale(), EPS);
            MathGlyphMetrics ink = measure(glyph);
            Assert.assertEquals(-SUPPORT.constants(10).getAxisHeight(),
                    glyph.getY() + (ink.getInkTop() + ink.getInkBottom()) / 2, EPS);
        }
        Assert.assertEquals(ref('x'), glyph(box, "x").getMathGlyphRef());
    }

    @Test
    public void nestedStyleAndBoxCopiesPreserveReferencesAndScriptCap() {
        LatexNode node = new LatexSupSub(atom("a"), new LatexSupSub(atom("b"),
                new LatexSupSub(atom("c"), atom("d"), null), null), null);
        MathBox box = layout(new LatexFrac(new LatexLeftRight("(", node, ")"), atom("z")));
        for (GlyphElem g : box.getGlyphs()) { Assert.assertNotNull(g.getMathGlyphRef()); }
        Assert.assertEquals(0.5F, glyph(box, "c").getSizeScale(), EPS);
        Assert.assertEquals(glyph(box, "c").getSizeScale(), glyph(box, "d").getSizeScale(), EPS);
        Assert.assertEquals(SUPPORT.constants(10).getRuleThickness(), box.getRules().get(0).getThickness(), EPS);
    }

    @Test
    public void limitsUseVariantCorrectionAndNeverScaleResolvedGlyph() {
        LatexAtom integral = new LatexAtom("∫", AtomClass.OP, OperatorMode.BIG_OPERATOR);
        MathBox box = layout(new LatexSupSub(integral, atom("2"), atom("3")));
        GlyphElem op = glyph(box, "∫");
        Assert.assertEquals(ref(OP_VARIANT), op.getMathGlyphRef());
        Assert.assertEquals(1, op.getSizeScale(), EPS);
        Assert.assertEquals(measure(op).getItalicCorrection(), glyph(box, "2").getX() - glyph(box, "3").getX(), EPS);
    }

    @Test
    public void radicalUsesFontGapDegreePercentAndInkRuleJoin() {
        MathBox box = layout(new LatexSqrt(atom("3"), atom("x")));
        GlyphElem radical = glyph(box, "√");
        MathGlyphMetrics ink = measure(radical);
        RuleElem bar = box.getRules().get(0);
        MathFontParameters constants = SUPPORT.constants(10);
        Assert.assertEquals(ref(ROOT_VARIANT), radical.getMathGlyphRef());
        Assert.assertEquals(1, radical.getSizeScale(), EPS);
        Assert.assertEquals(radical.getX() + ink.getInkRight() - constants.getRuleThickness(), bar.getX(), EPS);
        Assert.assertEquals(constants.getRuleThickness(), bar.getThickness(), EPS);
        GlyphElem degree = glyph(box, "3");
        Assert.assertEquals(radical.getY() + ink.getInkBottom()
                - (ink.getInkBottom() - ink.getInkTop()) * constants.getRadicalDegreeBottomRaisePercent() / 100.0F,
                degree.getY() + Math.max(0, measure(degree).getInkBottom()), EPS);
    }

    @Test
    public void fixedAccentUsesBothAttachmentsAndWideAccentHasExplicitShapeIdentity() {
        MathBox fixed = layout(new LatexAccent("^", atom("x"), false, false));
        GlyphElem accent = glyph(fixed, "̂");
        Assert.assertEquals(measure(glyph(fixed, "x")).getTopAccentAttachment() - measure(accent).getTopAccentAttachment(),
                accent.getX(), EPS);
        LatexAccent small = new LatexAccent("̂", atom("x"), LatexAccent.AccentMode.WIDE, false);
        Assert.assertEquals(ref(ACCENT_VARIANT), glyph(layout(small), "̂").getMathGlyphRef());
        LatexAccent wide = new LatexAccent("̂", new LatexGroup(Arrays.<LatexNode>asList(atom("abcdef"))),
                LatexAccent.AccentMode.WIDE, false);
        MathBox box = layout(new LatexLeftRight("(", wide, ")"));
        MathGlyphRef shape = glyph(box, "̂").getMathGlyphRef();
        Assert.assertEquals(MathGlyphRef.Kind.PROCEDURAL_ACCENT, shape.getKind());
        Assert.assertEquals(ProceduralAccentSpec.Kind.HAT, shape.getProceduralAccent().getKind());
        Assert.assertFalse(glyph(box, "̂").isItalic());
        Assert.assertEquals(1, glyph(box, "̂").getSizeScale(), EPS);
    }

    @Test
    public void parserPreservesWideModeThroughStyleCopy() {
        LatexNode node = LatexParser.parse("\\scriptstyle\\widehat{x}").get(0);
        Assert.assertEquals(MathStyleOverride.SCRIPT, node.getMathStyleOverride());
        Assert.assertEquals(LatexAccent.AccentMode.WIDE, ((LatexAccent) node).getAccentMode());
        Assert.assertEquals("̂", ((LatexAccent) node).getAccentText());
        Assert.assertFalse(((LatexAccent) node).isStretchable());
        Assert.assertEquals(LatexAccent.AccentMode.RULE, new LatexAccent(null, atom("x"), true, false).getAccentMode());
    }
}
