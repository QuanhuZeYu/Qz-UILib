package club.heiqi.uilib.font.latex.layout;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.MathFontStyle;
import club.heiqi.uilib.font.latex.MathStyleOverride;
import club.heiqi.uilib.font.latex.node.LatexAccent;
import club.heiqi.uilib.font.latex.node.LatexAtom;
import club.heiqi.uilib.font.latex.node.LatexAtom.AtomClass;
import club.heiqi.uilib.font.latex.node.LatexAtom.OperatorMode;
import club.heiqi.uilib.font.latex.node.LatexFrac;
import club.heiqi.uilib.font.latex.node.LatexGroup;
import club.heiqi.uilib.font.latex.node.LatexSpace;
import club.heiqi.uilib.font.latex.node.LatexSupSub;

/** 字体元数据与透明参数的几何契约；直接构造 AST，不依赖 parser 或字体资源。 */
public class MathFontStyleGeometryRegressionTest {
    private static final float ROOT = 10.0F;
    private static final float EPS = 1e-4F;
    private static final MathLayoutService SERVICE = new MathLayoutService();
    private static final MathMetrics METRICS = new MathMetrics() {
        public float advance(String text, float size) { return text.length() * size / 2.0F; }
        public float ascent(float size) { return size * 0.8F; }
        public float descent(float size) { return size * 0.2F; }
        public float xHeight(float size) { return size * 0.45F; }
        public float italicOverhang(String text, float size) { return size / 4.0F; }
        public float italicCorrection(String text, float size) { return size / 10.0F; }
    };

    private static LatexAtom atom(String text, MathFontStyle font) {
        return new LatexAtom(text, AtomClass.ORD, OperatorMode.NONE, MathStyleOverride.INHERIT, font);
    }

    private static LatexGroup transparent(LatexNode child, MathStyleOverride style) {
        return new LatexGroup(Collections.singletonList(child), style, true);
    }

    private static MathBox layout(LatexNode... nodes) {
        return SERVICE.layout(Arrays.asList(nodes), ROOT, METRICS);
    }

    private static GlyphElem glyph(MathBox box, String text) {
        for (GlyphElem glyph : box.getGlyphs()) {
            if (text.equals(glyph.getText())) { return glyph; }
        }
        throw new AssertionError("Missing glyph: " + text);
    }

    private static void sameGeometry(MathBox expected, MathBox actual) {
        Assert.assertEquals(expected.getWidth(), actual.getWidth(), EPS);
        Assert.assertEquals(expected.getHeight(), actual.getHeight(), EPS);
        Assert.assertEquals(expected.getDepth(), actual.getDepth(), EPS);
        Assert.assertEquals(expected.getLeftInkOverhang(), actual.getLeftInkOverhang(), EPS);
        Assert.assertEquals(expected.getRightInkOverhang(), actual.getRightInkOverhang(), EPS);
        Assert.assertEquals(expected.getGlyphs().size(), actual.getGlyphs().size());
        for (int i = 0; i < expected.getGlyphs().size(); i++) {
            GlyphElem e = expected.getGlyphs().get(i);
            GlyphElem a = actual.getGlyphs().get(i);
            Assert.assertEquals(e.getText(), a.getText());
            Assert.assertEquals(e.getX(), a.getX(), EPS);
            Assert.assertEquals(e.getY(), a.getY(), EPS);
            Assert.assertEquals(e.getSizeScale(), a.getSizeScale(), EPS);
            Assert.assertEquals(e.isItalic(), a.isItalic());
            Assert.assertEquals(e.isInheritTextItalic(), a.isInheritTextItalic());
        }
    }

    @Test
    public void legacyGlyphConstructorsAndAtomsKeepInheritance() {
        Assert.assertTrue(new GlyphElem("x", 0, 0, 1).isInheritTextItalic());
        Assert.assertTrue(new GlyphElem("x", 0, 0, 1, true).isInheritTextItalic());
        sameGeometry(layout(new LatexAtom("x", AtomClass.ORD)), layout(atom("x", MathFontStyle.INHERIT)));
        Assert.assertTrue(glyph(layout(atom("x", MathFontStyle.INHERIT)), "x").isItalic());
        Assert.assertFalse(glyph(layout(atom("α", MathFontStyle.INHERIT)), "α").isItalic());
    }

    @Test
    public void explicitItalicUsesBoundedOrdAlphabetAndSuppressesHostInheritance() {
        for (String text : new String[] { "x", "7", "α", "ϑ", "Γ" }) {
            GlyphElem g = glyph(layout(atom(text, MathFontStyle.ITALIC)), text);
            Assert.assertTrue(text, g.isItalic());
            Assert.assertFalse(text, g.isInheritTextItalic());
            Assert.assertEquals(ROOT / 4.0F, layout(atom(text, MathFontStyle.ITALIC)).getRightInkOverhang(), EPS);
        }
        for (String text : new String[] { "中", "Ж", "∞", "+", "ο" }) {
            Assert.assertFalse(text, glyph(layout(atom(text, MathFontStyle.ITALIC)), text).isItalic());
        }
        for (AtomClass cls : new AtomClass[] { AtomClass.BIN, AtomClass.REL, AtomClass.OPEN, AtomClass.OP }) {
            LatexAtom a = new LatexAtom("x", cls, OperatorMode.NONE, MathStyleOverride.INHERIT, MathFontStyle.ITALIC);
            Assert.assertFalse(glyph(layout(a), "x").isItalic());
            Assert.assertFalse(glyph(layout(a), "x").isInheritTextItalic());
        }
        LatexAtom text = new LatexAtom("x", AtomClass.TEXT, OperatorMode.NONE,
                MathStyleOverride.INHERIT, MathFontStyle.ITALIC);
        Assert.assertFalse(glyph(layout(text), "x").isItalic());
        Assert.assertTrue(glyph(layout(text), "x").isInheritTextItalic());
    }

    @Test
    public void scriptsUseActualItalicForLettersDigitsAndGreek() {
        for (String text : new String[] { "x", "7", "α" }) {
            LatexAtom script = atom("2", MathFontStyle.UPRIGHT);
            MathBox upright = layout(new LatexSupSub(atom(text, MathFontStyle.UPRIGHT), script, null));
            MathBox italic = layout(new LatexSupSub(atom(text, MathFontStyle.ITALIC), script, null));
            Assert.assertEquals(METRICS.italicCorrection(text, ROOT) + METRICS.italicOverhang(text, ROOT),
                    glyph(italic, "2").getX() - glyph(upright, "2").getX(), EPS);
            sameGeometry(italic, layout(new LatexSupSub(transparent(atom(text, MathFontStyle.ITALIC),
                    MathStyleOverride.INHERIT), script, null)));
        }
    }

    @Test
    public void accentSkewUsesActualItalicAndLocalGlyphSize() {
        for (String text : new String[] { "x", "7", "α" }) {
            LatexAtom italic = new LatexAtom(text, AtomClass.ORD, OperatorMode.NONE,
                    MathStyleOverride.SCRIPT, MathFontStyle.ITALIC);
            LatexAtom upright = new LatexAtom(text, AtomClass.ORD, OperatorMode.NONE,
                    MathStyleOverride.SCRIPT, MathFontStyle.UPRIGHT);
            MathBox i = layout(new LatexAccent("^", transparent(italic, MathStyleOverride.DISPLAY), false, false));
            MathBox u = layout(new LatexAccent("^", transparent(upright, MathStyleOverride.DISPLAY), false, false));
            Assert.assertEquals(MathConstants.ACCENT_SKEW_FACTOR
                    * METRICS.xHeight(ROOT * glyph(i, text).getSizeScale()),
                    glyph(i, "^").getX() - glyph(u, "^").getX(), EPS);
        }
    }

    @Test
    public void transparentBinAndKernPreserveOuterListRules() {
        LatexAtom a = atom("a", MathFontStyle.UPRIGHT);
        LatexAtom b = atom("b", MathFontStyle.UPRIGHT);
        LatexAtom plus = new LatexAtom("+", AtomClass.BIN, OperatorMode.NONE,
                MathStyleOverride.SCRIPT, MathFontStyle.UPRIGHT);
        LatexGroup wrapped = transparent(plus, MathStyleOverride.INHERIT);
        MathBox row = layout(a, wrapped, b);
        Assert.assertEquals(METRICS.advance("a", ROOT) + 4.0F / 18.0F * ROOT,
                glyph(row, "+").getX(), EPS);
        Assert.assertTrue(row.getWidth() > layout(a, new LatexGroup(Collections.<LatexNode>singletonList(plus)), b).getWidth());
        LatexSpace kern = new LatexSpace(-0.25);
        sameGeometry(layout(a, kern, wrapped, b), layout(a, transparent(kern, MathStyleOverride.INHERIT), wrapped, b));
        sameGeometry(layout(plus, b), layout(wrapped, b));
        sameGeometry(layout(a, plus), layout(a, wrapped));
        sameGeometry(layout(kern), layout(transparent(kern, MathStyleOverride.INHERIT)));
    }

    @Test
    public void transparentStyleLayersDoNotPromoteOuterScriptsOrRepeatBigOpTail() {
        for (OperatorMode mode : new OperatorMode[] { OperatorMode.NONE, OperatorMode.BIG_OPERATOR }) {
            for (int limits : new int[] { LatexAtom.LIMITS_DEFAULT, LatexAtom.LIMITS_NOLIMITS }) {
                LatexAtom base = new LatexAtom(mode == OperatorMode.NONE ? "x" : "∑",
                        mode == OperatorMode.NONE ? AtomClass.ORD : AtomClass.OP, mode,
                        MathStyleOverride.DISPLAY, MathFontStyle.UPRIGHT);
                base.setLimitsFlag(limits);
                LatexNode wrapped = transparent(transparent(base, MathStyleOverride.SCRIPTSCRIPT), MathStyleOverride.SCRIPT);
                LatexAtom script = atom("2", MathFontStyle.UPRIGHT);
                MathStyle outer = new MathStyle(ROOT, MathStyle.Level.SCRIPT, false);
                MathBox direct = SERVICE.layout(Collections.<LatexNode>singletonList(new LatexSupSub(base, script, null)), outer, METRICS);
                MathBox nested = SERVICE.layout(Collections.<LatexNode>singletonList(new LatexSupSub(wrapped, script, null)), outer, METRICS);
                sameGeometry(direct, nested);
                Assert.assertEquals(outer.superscript().size() / ROOT, glyph(nested, "2").getSizeScale(), EPS);
                sameGeometry(layout(base), layout(wrapped));
            }
        }
    }

    @Test
    public void fractionAndBarCoverItalicInkAndKeepExplicitFlags() {
        LatexAtom numerator = atom("α", MathFontStyle.ITALIC);
        LatexAtom denominator = atom("x", MathFontStyle.UPRIGHT);
        MathBox fraction = layout(new LatexFrac(numerator, denominator));
        GlyphElem g = glyph(fraction, "α");
        RuleElem rule = fraction.getRules().get(0);
        float inkRight = g.getX() + METRICS.advance("α", ROOT * g.getSizeScale())
                + METRICS.italicOverhang("α", ROOT * g.getSizeScale());
        Assert.assertTrue(rule.getX() + rule.getWidth() + EPS >= inkRight);
        Assert.assertFalse(g.isInheritTextItalic());
        Assert.assertFalse(glyph(fraction, "x").isInheritTextItalic());
        MathBox bar = layout(new LatexAccent(null, numerator, true, false));
        Assert.assertEquals(METRICS.advance("α", ROOT) + METRICS.italicOverhang("α", ROOT),
                bar.getRules().get(0).getWidth(), EPS);
    }
}
