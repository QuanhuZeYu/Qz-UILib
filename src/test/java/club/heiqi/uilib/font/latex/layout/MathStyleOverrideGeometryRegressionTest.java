package club.heiqi.uilib.font.latex.layout;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.LatexParser;
import club.heiqi.uilib.font.latex.MathStyleOverride;
import club.heiqi.uilib.font.latex.node.LatexAtom;
import club.heiqi.uilib.font.latex.node.LatexAtom.AtomClass;
import club.heiqi.uilib.font.latex.node.LatexAtom.OperatorMode;
import club.heiqi.uilib.font.latex.node.LatexFrac;
import club.heiqi.uilib.font.latex.node.LatexFrac.FractionStyle;
import club.heiqi.uilib.font.latex.node.LatexGroup;
import club.heiqi.uilib.font.latex.node.LatexSpace;
import club.heiqi.uilib.font.latex.node.LatexSupSub;

/** B2a 几何契约：注入独立线性度量，不依赖软件截图或生产字体。 */
public class MathStyleOverrideGeometryRegressionTest {
    private static final float EPS = 1e-4F;
    private static final float ROOT = 10.0F;
    private static final MathLayoutService SERVICE = new MathLayoutService();
    private static final MathMetrics METRICS = new MathMetrics() {
        public float advance(String text, float size) { return text.length() * size / 2.0F; }
        public float ascent(float size) { return size * 0.8F; }
        public float descent(float size) { return size * 0.2F; }
        public float xHeight(float size) { return size * 0.45F; }
    };

    private static MathBox layout(String source) {
        return SERVICE.layout(LatexParser.parse(source), ROOT, METRICS);
    }

    private static MathBox layout(LatexNode... nodes) {
        return SERVICE.layout(Arrays.asList(nodes), ROOT, METRICS);
    }

    private static LatexAtom atom(String text, MathStyleOverride style) {
        return new LatexAtom(text, AtomClass.ORD, OperatorMode.NONE, style);
    }

    private static GlyphElem glyph(MathBox box, String text) {
        for (GlyphElem glyph : box.getGlyphs()) {
            if (text.equals(glyph.getText())) { return glyph; }
        }
        throw new AssertionError("Missing glyph: " + text);
    }

    private static float size(MathBox box, String text) {
        return ROOT * glyph(box, text).getSizeScale();
    }

    private static float rise(MathBox box, String base, String script) {
        return glyph(box, base).getY() - glyph(box, script).getY();
    }

    private static float gap(MathBox box, String left, String right) {
        return glyph(box, right).getX() - glyph(box, left).getX()
                - METRICS.advance(left, size(box, left));
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
        }
        Assert.assertEquals(expected.getRules().size(), actual.getRules().size());
        for (int i = 0; i < expected.getRules().size(); i++) {
            RuleElem e = expected.getRules().get(i);
            RuleElem a = actual.getRules().get(i);
            Assert.assertEquals(e.getX(), a.getX(), EPS);
            Assert.assertEquals(e.getY(), a.getY(), EPS);
            Assert.assertEquals(e.getWidth(), a.getWidth(), EPS);
            Assert.assertEquals(e.getThickness(), a.getThickness(), EPS);
            Assert.assertEquals(e.getClearanceAbove(), a.getClearanceAbove(), EPS);
            Assert.assertEquals(e.getClearanceBelow(), a.getClearanceBelow(), EPS);
        }
    }

    @Test
    public void explicitDeclarationsResetCrampedAtEveryLevel() {
        for (MathStyle.Level level : MathStyle.Level.values()) {
            MathStyle parent = new MathStyle(ROOT, level, true);
            Assert.assertSame(parent, parent.withOverride(MathStyleOverride.INHERIT));
            for (MathStyleOverride override : MathStyleOverride.values()) {
                if (override == MathStyleOverride.INHERIT) { continue; }
                MathStyle result = parent.withOverride(override);
                Assert.assertEquals(override.name(), result.level.name());
                Assert.assertFalse(result.cramped);
                Assert.assertEquals(ROOT, result.rootSize, 0.0F);
                Assert.assertTrue(result.denominator().cramped);
                Assert.assertTrue(result.subscript().cramped);
            }
        }
    }

    @Test
    public void fractionCommandControlsChildSizesClearanceAndHeight() {
        MathBox display = layout("\\dfrac{a}{b}");
        MathBox text = layout("\\tfrac{a}{b}");
        Assert.assertEquals(ROOT, size(display, "a"), EPS);
        Assert.assertEquals(ROOT, size(display, "b"), EPS);
        Assert.assertEquals(7.0F, size(text, "a"), EPS);
        Assert.assertEquals(7.0F, size(text, "b"), EPS);
        Assert.assertTrue(display.getTotalHeight() > text.getTotalHeight());
        Assert.assertTrue(display.getRules().get(0).getClearanceAbove()
                > text.getRules().get(0).getClearanceAbove());
        sameGeometry(layout("\\frac{a}{b}"), text);
    }

    @Test
    public void dfracDoesNotPromoteItsOuterScript() {
        MathBox plain = layout("\\dfrac ab^c");
        MathBox script = layout("\\scriptstyle\\dfrac ab^c");
        Assert.assertEquals(ROOT, size(plain, "a"), EPS);
        Assert.assertEquals(7.0F, size(plain, "c"), EPS);
        Assert.assertEquals(ROOT, size(script, "a"), EPS);
        Assert.assertEquals(ROOT, size(script, "b"), EPS);
        Assert.assertEquals(5.0F, size(script, "c"), EPS);
        Assert.assertEquals(plain.getRules().get(0).getThickness(),
                script.getRules().get(0).getThickness(), EPS);
    }

    @Test
    public void denominatorDeclarationResetsCrampedWithoutLeakingToNumerator() {
        MathBox inherited = layout("\\dfrac{a^b}{c^d}");
        MathBox explicit = layout("\\dfrac{a^b}{\\textstyle c^d}");
        Assert.assertTrue(rise(explicit, "c", "d") > rise(inherited, "c", "d"));
        Assert.assertEquals(rise(explicit, "a", "b"), rise(explicit, "c", "d"), EPS);
        Assert.assertEquals(rise(inherited, "a", "b"), rise(explicit, "a", "b"), EPS);
        MathBox small = layout("\\dfrac{\\scriptstyle a^b}{c^d}");
        Assert.assertEquals(7.0F, size(small, "a"), EPS);
        Assert.assertEquals(ROOT, size(small, "c"), EPS);
    }

    @Test
    public void rightDeclarationControlsBothSidesOfMixedSpacing() {
        MathBox mixed = layout("a+\\scriptstyle b\\textstyle+c");
        Assert.assertEquals(4.0F / 18.0F * ROOT, gap(mixed, "a", "+"), EPS);
        Assert.assertEquals(0.0F, gap(mixed, "+", "b"), EPS);
        // 第二个 + 仍是 BIN，声明不能把同一数学列表切断。
        GlyphElem secondPlus = mixed.getGlyphs().get(3);
        Assert.assertEquals(4.0F / 18.0F * ROOT,
                secondPlus.getX() - glyph(mixed, "b").getX() - METRICS.advance("b", 7.0F), EPS);
        Assert.assertEquals(4.0F / 18.0F * ROOT,
                glyph(mixed, "c").getX() - secondPlus.getX() - METRICS.advance("+", ROOT), EPS);
        Assert.assertEquals(0.0F, gap(layout("a\\scriptstyle+b"), "a", "+"), EPS);
        Assert.assertEquals(0.0F, gap(layout("a\\textstyle+"), "a", "+"), EPS);
    }

    @Test
    public void fractionOverrideDoesNotControlOuterGlue() {
        sameGeometry(layout("a\\dfrac{b}{c}d"), layout("a\\displaystyle\\frac{b}{c}d"));
        MathBox script = layout("\\scriptstyle a\\dfrac{b}{c}d");
        MathBox fraction = layout("\\dfrac{b}{c}");
        Assert.assertEquals(METRICS.advance("a", 7.0F) + fraction.getWidth(),
                glyph(script, "d").getX(), EPS);
        Assert.assertEquals(7.0F, size(script, "d"), EPS);
    }

    @Test
    public void nestedExplicitDisplayNormalizesOnlyGlyphs() {
        MathBox direct = layout("\\dfrac{a}{b}");
        MathBox nested = layout("\\scriptstyle{\\displaystyle\\frac{a}{b}}");
        sameGeometry(direct, nested);
        MathBox deep = layout("a^{b^{c^{\\displaystyle d^{e^{f^g}}}}}");
        Assert.assertEquals(ROOT, size(deep, "d"), EPS);
        Assert.assertEquals(7.0F, size(deep, "e"), EPS);
        Assert.assertEquals(5.0F, size(deep, "f"), EPS);
        Assert.assertEquals(5.0F, size(deep, "g"), EPS);
    }

    @Test
    public void handBuiltStyledAtomicBaseKeepsOuterScriptStyle() {
        LatexAtom base = atom("a", MathStyleOverride.SCRIPTSCRIPT);
        MathBox box = layout(new LatexSupSub(base, atom("b", MathStyleOverride.INHERIT), null));
        Assert.assertEquals(5.0F, size(box, "a"), EPS);
        Assert.assertEquals(7.0F, size(box, "b"), EPS);
        Assert.assertEquals(METRICS.advance("a", 5.0F), glyph(box, "b").getX(), EPS);
        MathBox outer = layout(new LatexSupSub(atom("a", MathStyleOverride.DISPLAY),
                atom("b", MathStyleOverride.INHERIT), null, MathStyleOverride.SCRIPT));
        Assert.assertEquals(ROOT, size(outer, "a"), EPS);
        Assert.assertEquals(5.0F, size(outer, "b"), EPS);
    }

    @Test
    public void styledBigOperatorBaseKeepsNaturalSizeAndScriptConversion() {
        for (int limits : new int[] {LatexAtom.LIMITS_LIMITS, LatexAtom.LIMITS_NOLIMITS}) {
            LatexAtom base = new LatexAtom("∑", AtomClass.OP, OperatorMode.BIG_OPERATOR,
                    MathStyleOverride.SCRIPTSCRIPT);
            base.setLimitsFlag(limits);
            MathBox box = layout(new LatexSupSub(base, atom("b", MathStyleOverride.INHERIT), null));
            Assert.assertEquals(5.0F, size(box, "∑"), EPS);
            Assert.assertEquals(7.0F, size(box, "b"), EPS);
        }
    }

    @Test
    public void negativeKernPreservesAdvanceAndVisualBoundsThroughStyleBoundary() {
        LatexGroup group = new LatexGroup(Arrays.<LatexNode>asList(
                atom("a", MathStyleOverride.INHERIT), new LatexSpace(-2.0)), MathStyleOverride.SCRIPT);
        MathBox box = layout(group);
        Assert.assertEquals(METRICS.advance("a", 7.0F) - 2.0F * 7.0F, box.getWidth(), EPS);
        Assert.assertTrue(box.getRightInkOverhang() > 0.0F);
        Assert.assertEquals(METRICS.advance("a", 7.0F),
                box.getWidth() + box.getRightInkOverhang(), EPS);
        MathBox leading = layout(new LatexSpace(-2.0, MathStyleOverride.SCRIPT),
                atom("b", MathStyleOverride.INHERIT));
        Assert.assertEquals(-2.0F * 7.0F, glyph(leading, "b").getX(), EPS);
        Assert.assertTrue(leading.getLeftInkOverhang() > 0.0F);
        MathBox kern = layout(new LatexSpace(-2.0, MathStyleOverride.SCRIPT));
        Assert.assertEquals(-2.0F * 7.0F, kern.getWidth(), EPS);
        MathBox fraction = layout("\\scriptstyle\\dfrac{a\\!}{b}");
        RuleElem rule = fraction.getRules().get(0);
        Assert.assertTrue(rule.getX() + rule.getWidth() + EPS >=
                glyph(fraction, "a").getX() + METRICS.advance("a", ROOT));
    }

    @Test
    public void scriptThinGlueUsesRightEffectiveSize() {
        MathBox box = layout("a\\scriptstyle\\sin b\\textstyle c");
        Assert.assertEquals(3.0F / 18.0F * 7.0F, gap(box, "a", "sin"), EPS);
        Assert.assertEquals(3.0F / 18.0F * 7.0F, gap(box, "sin", "b"), EPS);
        Assert.assertEquals(0.0F, gap(box, "b", "c"), EPS);
        MathBox reverse = layout("\\scriptstyle a\\textstyle\\sin b");
        Assert.assertEquals(3.0F / 18.0F * ROOT, gap(reverse, "a", "sin"), EPS);
    }

    @Test
    public void nestedListScopesAndMatrixCellsKeepTheirOwnDeclarations() {
        MathBox group = layout("{\\scriptstyle a}b");
        Assert.assertEquals(7.0F, size(group, "a"), EPS);
        Assert.assertEquals(ROOT, size(group, "b"), EPS);
        MathBox fences = layout("\\left(a\\scriptstyle b\\middle|c\\right)d");
        Assert.assertEquals(ROOT, size(fences, "a"), EPS);
        Assert.assertEquals(7.0F, size(fences, "b"), EPS);
        Assert.assertEquals(7.0F, size(fences, "c"), EPS);
        Assert.assertEquals(ROOT, size(fences, "d"), EPS);
        MathBox matrix = layout("\\scriptstyle\\begin{matrix}\\scriptscriptstyle a&b\\\\c&d\\end{matrix}");
        Assert.assertEquals(5.0F, size(matrix, "a"), EPS);
        Assert.assertEquals(ROOT, size(matrix, "b"), EPS);
        Assert.assertEquals(ROOT, size(matrix, "c"), EPS);
        Assert.assertEquals(ROOT, size(matrix, "d"), EPS);
    }

    @Test
    public void middleGlueUsesDeclarationAtMiddleAndAtFollowingFactor() {
        MathBox before = layout("\\left.a\\scriptstyle\\middle|b\\right.");
        Assert.assertEquals(ROOT, size(before, "a"), EPS);
        Assert.assertEquals(7.0F, size(before, "b"), EPS);
        Assert.assertEquals(0.0F, gap(before, "a", "|"), EPS);
        Assert.assertEquals(0.0F, gap(before, "|", "b"), EPS);
        MathBox after = layout("\\left.a\\middle|\\scriptstyle b\\right.");
        Assert.assertEquals(5.0F / 18.0F * ROOT, gap(after, "a", "|"), EPS);
        Assert.assertEquals(0.0F, gap(after, "|", "b"), EPS);
        MathBox reset = layout("\\left.a\\scriptstyle\\middle|\\textstyle b\\right.");
        Assert.assertEquals(0.0F, gap(reset, "a", "|"), EPS);
        Assert.assertEquals(5.0F / 18.0F * ROOT, gap(reset, "|", "b"), EPS);
        MathBox empty = layout("\\left.a\\scriptstyle\\middle|\\right.");
        Assert.assertEquals(0.0F, gap(empty, "a", "|"), EPS);
        Assert.assertEquals(glyph(empty, "|").getX() + METRICS.advance("|", size(empty, "|")),
                empty.getWidth(), EPS);
    }

    @Test
    public void explicitSizesStillDeriveFromUnroundedRoot() {
        MathBox box = SERVICE.layout(LatexParser.parse(
                "\\scriptscriptstyle a^{\\displaystyle b^{c^d}}"), 14.9F, METRICS);
        Assert.assertEquals(7.0F, glyph(box, "a").getSizeScale() * 14.9F, EPS);
        Assert.assertEquals(14.0F, glyph(box, "b").getSizeScale() * 14.9F, EPS);
        Assert.assertEquals(10.0F, glyph(box, "c").getSizeScale() * 14.9F, EPS);
        Assert.assertEquals(7.0F, glyph(box, "d").getSizeScale() * 14.9F, EPS);
    }

    @Test
    public void oldConstructorsMatchExplicitInherit() {
        LatexNode a = new LatexAtom("a", AtomClass.ORD);
        LatexNode b = new LatexAtom("b", AtomClass.ORD);
        sameGeometry(layout(new LatexFrac(a, b)),
                layout(new LatexFrac(a, b, MathStyleOverride.INHERIT, FractionStyle.INHERIT)));
        sameGeometry(layout(new LatexSupSub(a, b, null)),
                layout(new LatexSupSub(a, b, null, MathStyleOverride.INHERIT)));
        sameGeometry(layout(new LatexGroup(Collections.singletonList(a))),
                layout(new LatexGroup(Collections.singletonList(a), MathStyleOverride.INHERIT)));
    }
}
