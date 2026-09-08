package club.heiqi.uilib.font.latex.layout;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.LatexParser;
import club.heiqi.uilib.font.latex.node.LatexAtom;
import club.heiqi.uilib.font.latex.node.LatexAtom.AtomClass;
import club.heiqi.uilib.font.latex.node.LatexSpace;
import club.heiqi.uilib.font.latex.node.LatexGroup;

/** B1 正式几何回归：从实际字形基线、推进和规则线验证样式传播，不依赖软件渲染夹具。 */
public class MathStyleGeometryRegressionTest {
    private static final float EPS = 1e-4F;
    private static final MathLayoutService SERVICE = new MathLayoutService();
    private static final MathMetrics METRICS = new MathMetrics() {
        public float advance(String text, float size) { return text.length() * size / 2.0F; }
        public float ascent(float size) { return size * 0.8F; }
        public float descent(float size) { return size * 0.2F; }
        public float xHeight(float size) { return size * 0.45F; }
    };

    private static MathBox layout(String source, float root, MathStyle.Level level, boolean cramped) {
        return SERVICE.layout(LatexParser.parse(source), new MathStyle(root, level, cramped), METRICS);
    }

    private static MathBox layout(String source) {
        return layout(source, 10.0F, MathStyle.Level.TEXT, false);
    }

    private static GlyphElem glyph(MathBox box, String text) {
        for (GlyphElem glyph : box.getGlyphs()) {
            if (text.equals(glyph.getText())) { return glyph; }
        }
        throw new AssertionError("Missing glyph: " + text);
    }

    private static float rise(MathBox box, String base, String script) {
        return glyph(box, base).getY() - glyph(box, script).getY();
    }

    private static void state(MathStyle style, MathStyle.Level level, boolean cramped) {
        Assert.assertEquals(level, style.level);
        Assert.assertEquals(cramped, style.cramped);
        Assert.assertEquals(14.9F, style.rootSize, 0.0F);
    }

    @Test
    public void allEightStatesHaveExplicitTransitions() {
        MathStyle.Level[] levels = MathStyle.Level.values();
        MathStyle.Level[] scripts = {MathStyle.Level.SCRIPT, MathStyle.Level.SCRIPT,
                MathStyle.Level.SCRIPTSCRIPT, MathStyle.Level.SCRIPTSCRIPT};
        MathStyle.Level[] fractions = {MathStyle.Level.TEXT, MathStyle.Level.SCRIPT,
                MathStyle.Level.SCRIPTSCRIPT, MathStyle.Level.SCRIPTSCRIPT};
        for (int i = 0; i < levels.length; i++) {
            for (boolean cramped : new boolean[] {false, true}) {
                MathStyle parent = new MathStyle(14.9F, levels[i], cramped);
                state(parent.superscript(), scripts[i], cramped);
                state(parent.subscript(), scripts[i], true);
                state(parent.numerator(), fractions[i], cramped);
                state(parent.denominator(), fractions[i], true);
                state(parent.cramp(), levels[i], true);
                state(parent.rootIndex(), MathStyle.Level.SCRIPTSCRIPT, false);
                state(parent.matrixCell(), MathStyle.Level.TEXT, false);
                state(parent, levels[i], cramped);
            }
        }
    }

    @Test
    public void deepScriptsUseRootSizesAndStopAtScriptscript() {
        // Python 独立验算的有效字号表，非整数根字号不能先截断再派生脚本。
        float[] roots = {1.0F, 1.9F, 12.0F, 14.0F, 14.9F, 16.0F, 24.0F};
        int[][] sizes = {{1, 1, 1, 1, 1}, {1, 1, 1, 1, 1}, {12, 8, 6, 6, 6},
                {14, 9, 7, 7, 7}, {14, 10, 7, 7, 7}, {16, 11, 8, 8, 8}, {24, 16, 12, 12, 12}};
        for (int row = 0; row < roots.length; row++) {
            for (String source : new String[] {"a^{b^{c^{d^e}}}", "a_{b_{c_{d_e}}}"}) {
                MathBox box = SERVICE.layout(LatexParser.parse(source), roots[row], METRICS);
                for (int i = 0; i < sizes[row].length; i++) {
                    GlyphElem g = glyph(box, String.valueOf((char) ('a' + i)));
                    float actualSize = roots[row] * g.getSizeScale();
                    Assert.assertEquals(sizes[row][i], actualSize, EPS);
                    Assert.assertTrue(g.getY() - METRICS.ascent(actualSize) >= -box.getHeight() - EPS);
                    Assert.assertTrue(g.getY() + METRICS.descent(actualSize) <= box.getDepth() + EPS);
                    Assert.assertTrue(g.getX() + actualSize / 2.0F <= box.getWidth() + EPS);
                }
            }
        }
    }

    @Test
    public void fractionNumeratorInheritsAndDenominatorCramps() {
        MathBox fraction = layout("\\frac{a^b}{c^d}");
        Assert.assertEquals(2.540244F, rise(fraction, "a", "b"), EPS);
        Assert.assertEquals(2.022223F, rise(fraction, "c", "d"), EPS);
        MathBox cramped = layout("\\frac{a^b}{c^d}", 10.0F, MathStyle.Level.TEXT, true);
        Assert.assertEquals(rise(cramped, "a", "b"), rise(cramped, "c", "d"), EPS);
        MathBox binom = layout("\\binom{a^b}{c^d}");
        Assert.assertEquals(rise(fraction, "a", "b"), rise(binom, "a", "b"), EPS);
        Assert.assertEquals(rise(fraction, "c", "d"), rise(binom, "c", "d"), EPS);
    }

    @Test
    public void wrappersPreserveCrampedAndUnderlineInherits() {
        float normal = rise(layout("a^b"), "a", "b");
        float cramped = rise(layout("a^b", 10.0F, MathStyle.Level.TEXT, true), "a", "b");
        Assert.assertTrue(normal > cramped);
        for (String source : new String[] {"{a^b}", "{a^b}^c", "\\left(a^b\\middle|c\\right)",
                "\\underline{a^b}"}) {
            Assert.assertEquals(source, cramped,
                    rise(layout(source, 10.0F, MathStyle.Level.TEXT, true), "a", "b"), EPS);
        }
        for (String source : new String[] {"\\sqrt{a^b}", "\\hat{a^b}", "\\overline{a^b}"}) {
            Assert.assertEquals(source, cramped, rise(layout(source), "a", "b"), EPS);
        }
        Assert.assertEquals(normal, rise(layout("\\underline{a^b}"), "a", "b"), EPS);
        // 规则线上下沿属于外脚本的基底，不能一律去掉重音再用 cramped 裸基底参照。
        for (String command : new String[] {"\\underline", "\\overline"}) {
            MathBox base = layout(command + "{a^b}");
            MathBox outer = layout(command + "{a^b}^c");
            Assert.assertEquals(base.getHeight() - MathConstants.SUP_DROP_EM * 7.0F,
                    -glyph(outer, "c").getY(), EPS);
        }
    }

    @Test
    public void rootIndexIsFixedNonCrampedScriptscriptAndMatrixCellsAreText() {
        MathBox root = layout("x^{\\sqrt[a^b]{c^d}}");
        Assert.assertEquals(0.5F, glyph(root, "a").getSizeScale(), EPS);
        Assert.assertEquals(0.5F, glyph(root, "b").getSizeScale(), EPS);
        MathBox reference = layout("a^b", 10.0F, MathStyle.Level.SCRIPTSCRIPT, false);
        Assert.assertEquals(rise(reference, "a", "b"), rise(root, "a", "b"), EPS);
        for (String environment : new String[] {"matrix", "cases"}) {
            MathBox matrix = layout("x_{\\begin{" + environment + "}a^b\\end{" + environment + "}}");
            Assert.assertEquals(1.0F, glyph(matrix, "a").getSizeScale(), EPS);
            Assert.assertEquals(0.7F, glyph(matrix, "b").getSizeScale(), EPS);
            Assert.assertEquals(rise(layout("a^b"), "a", "b"), rise(matrix, "a", "b"), EPS);
        }
    }

    @Test
    public void scriptscriptDropUsesCappedChildSize() {
        MathBox sup = layout("{a}^b", 10.0F, MathStyle.Level.SCRIPTSCRIPT, false);
        MathBox sub = layout("{a_b}_c", 10.0F, MathStyle.Level.SCRIPTSCRIPT, false);
        Assert.assertEquals(2.06946F, -glyph(sup, "b").getY(), EPS);
        Assert.assertEquals(3.45F, glyph(sub, "c").getY(), EPS);
    }

    @Test
    public void scriptGlueKeepsOnlyFiveThinPairsAndExplicitKerns() {
        for (MathStyle.Level level : new MathStyle.Level[] {MathStyle.Level.SCRIPT, MathStyle.Level.SCRIPTSCRIPT}) {
            MathStyle style = new MathStyle(10.0F, level, false);
            for (AtomClass left : AtomClass.values()) {
                for (AtomClass right : AtomClass.values()) {
                    MathBox pair = SERVICE.layout(Arrays.<LatexNode>asList(new LatexAtom("1", left),
                            new LatexAtom("2", right)), style, METRICS);
                    boolean thin = (right == AtomClass.OP && (left == AtomClass.ORD || left == AtomClass.OP
                            || left == AtomClass.CLOSE || left == AtomClass.INNER))
                            || (left == AtomClass.OP && right == AtomClass.ORD);
                    // BIN 在两元素边缘降为 ORD，故与 OP 相邻时按 Ord/Op glue。
                    if ((left == AtomClass.BIN && right == AtomClass.OP)
                            || (left == AtomClass.OP && right == AtomClass.BIN)) { thin = true; }
                    Assert.assertEquals(left + " -> " + right, style.size() * (thin ? 7.0F / 6.0F : 1.0F),
                            pair.getWidth(), EPS);
                }
            }
            MathBox explicit = layout("a\\quad b", 10.0F, level, false);
            Assert.assertEquals(style.size() * 2.0F, explicit.getWidth(), EPS);
        }
        MathBox negative = SERVICE.layout(Collections.<LatexNode>singletonList(new LatexSpace(-1.0)), 10.0F, METRICS);
        Assert.assertEquals(-10.0F, negative.getWidth(), EPS);
        LatexNode kern = new LatexSpace(-1.0);
        LatexNode grouped = new LatexGroup(Collections.singletonList(kern));
        LatexNode atom = new LatexAtom("a", AtomClass.ORD);
        for (LatexNode space : new LatexNode[] {kern, grouped}) {
            MathBox leading = SERVICE.layout(Arrays.asList(space, atom), 10.0F, METRICS);
            MathBox trailing = SERVICE.layout(Arrays.asList(atom, space), 10.0F, METRICS);
            MathBox middle = SERVICE.layout(Arrays.asList(atom, space, atom), 10.0F, METRICS);
            Assert.assertEquals(-5.0F, leading.getWidth(), EPS);
            Assert.assertEquals(-10.0F, glyph(leading, "a").getX(), EPS);
            Assert.assertEquals(-5.0F, trailing.getWidth(), EPS);
            Assert.assertEquals(0.0F, middle.getWidth(), EPS);
            Assert.assertEquals(-5.0F, middle.getGlyphs().get(1).getX(), EPS);
            Assert.assertTrue(leading.getLeftInkOverhang() >= 10.0F);
            Assert.assertTrue(trailing.getRightInkOverhang() >= 10.0F);
        }
    }

    @Test
    public void displayFractionUsesTextChildrenAndLargerClearance() {
        MathBox display = layout("\\frac{a}{b}", 10.0F, MathStyle.Level.DISPLAY, false);
        MathBox text = layout("\\frac{a}{b}");
        Assert.assertEquals(1.0F, glyph(display, "a").getSizeScale(), EPS);
        Assert.assertEquals(0.7F, glyph(text, "a").getSizeScale(), EPS);
        Assert.assertTrue(display.getTotalHeight() > text.getTotalHeight());
        Assert.assertTrue(display.getRules().get(0).getClearanceAbove()
                >= 3.0F * display.getRules().get(0).getThickness() - EPS);
        for (MathStyle.Level level : MathStyle.Level.values()) {
            MathBox frac = layout("\\frac{a}{b}", 10.0F, level, false);
            RuleElem rule = frac.getRules().get(0);
            float sizeA = glyph(frac, "a").getSizeScale() * 10.0F;
            float sizeB = glyph(frac, "b").getSizeScale() * 10.0F;
            Assert.assertEquals(rule.getClearanceAbove(), rule.getY() - rule.getThickness() / 2.0F
                    - (glyph(frac, "a").getY() + METRICS.descent(sizeA)), EPS);
            Assert.assertEquals(rule.getClearanceBelow(), glyph(frac, "b").getY() - METRICS.ascent(sizeB)
                    - (rule.getY() + rule.getThickness() / 2.0F), EPS);
        }
    }

    @Test
    public void publicEntryRemainsTextAndInvalidSizesProduceFinitePositiveGlyphScales() {
        MathBox publicBox = SERVICE.layout(LatexParser.parse("a^b"), 10.0F, METRICS);
        Assert.assertEquals(layout("a^b").getHeight(), publicBox.getHeight(), EPS);
        for (float size : new float[] {0.0F, -1.0F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            MathBox box = SERVICE.layout(LatexParser.parse("a"), size, METRICS);
            float scale = glyph(box, "a").getSizeScale();
            Assert.assertFalse(Float.isNaN(scale));
            Assert.assertFalse(Float.isInfinite(scale));
            Assert.assertTrue(scale > 0.0F);
        }
    }
}
