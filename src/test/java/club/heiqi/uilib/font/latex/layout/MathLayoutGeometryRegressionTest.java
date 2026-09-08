package club.heiqi.uilib.font.latex.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.node.LatexAccent;
import club.heiqi.uilib.font.latex.node.LatexAtom;
import club.heiqi.uilib.font.latex.node.LatexAtom.AtomClass;
import club.heiqi.uilib.font.latex.node.LatexFrac;
import club.heiqi.uilib.font.latex.node.LatexGroup;
import club.heiqi.uilib.font.latex.node.LatexMatrix;
import club.heiqi.uilib.font.latex.node.LatexSqrt;
import club.heiqi.uilib.font.latex.node.LatexSupSub;

/** 直接构造 AST，隔离 parser；逐字形与规则线检查布局盒包含性。 */
public class MathLayoutGeometryRegressionTest {
    private static final float SIZE = 10.0F;
    private static final float EPS = 1e-4F;
    private static final MathMetrics METRICS = new MockMetrics();
    private static final MathMetrics OFFSET_INK = new MockMetrics() {
        @Override
        public float inkCenterOffsetY(String text, float sizePx) {
            return "√".equals(text) || "∑".equals(text) ? 0.6F * sizePx
                    : super.inkCenterOffsetY(text, sizePx);
        }

        @Override
        public float inkWidth(String text, float sizePx) {
            return "√".equals(text) ? 0.3F * sizePx : super.inkWidth(text, sizePx);
        }

        @Override
        public float inkLeftBearing(String text, float sizePx) {
            return "√".equals(text) ? 0.1F * sizePx : 0.0F;
        }
    };

    private static class MockMetrics implements MathMetrics {
        @Override
        public float advance(String text, float sizePx) {
            return text.codePointCount(0, text.length()) * 0.5F * sizePx;
        }

        @Override
        public float ascent(float sizePx) {
            return 0.8F * sizePx;
        }

        @Override
        public float descent(float sizePx) {
            return 0.2F * sizePx;
        }

        @Override
        public float xHeight(float sizePx) {
            return 0.45F * sizePx;
        }
    }

    private static LatexAtom atom(String text) {
        return new LatexAtom(text, AtomClass.ORD);
    }

    private static MathBox layout(MathMetrics metrics, LatexNode... nodes) {
        return new MathLayoutService().layout(Arrays.asList(nodes), SIZE, metrics);
    }

    private static LatexNode matrix(int count) {
        List<List<List<LatexNode>>> rows = new ArrayList<List<List<LatexNode>>>();
        for (int i = 0; i < count; i++) {
            rows.add(Collections.singletonList(Collections.<LatexNode>singletonList(atom("x"))));
        }
        return new LatexMatrix(LatexMatrix.Fence.NONE, rows);
    }

    private static void assertContained(MathBox box, MathMetrics metrics) {
        for (GlyphElem glyph : box.getGlyphs()) {
            float size = SIZE * glyph.getSizeScale();
            float center = glyph.getY() + metrics.inkCenterOffsetY(glyph.getText(), size);
            float half = metrics.inkHeight(glyph.getText(), size) / 2.0F;
            Assert.assertTrue("glyph top: " + glyph.getText(), center - half >= -box.getHeight() - EPS);
            Assert.assertTrue("glyph bottom: " + glyph.getText(), center + half <= box.getDepth() + EPS);
            float left = glyph.getX() + metrics.inkLeftBearing(glyph.getText(), size);
            float right = left + metrics.inkWidth(glyph.getText(), size);
            Assert.assertTrue("glyph left", left >= -box.getLeftInkOverhang() - EPS);
            Assert.assertTrue("glyph right", right <= box.getWidth() + box.getRightInkOverhang() + EPS);
        }
        for (RuleElem rule : box.getRules()) {
            Assert.assertTrue("rule top", rule.getY() - rule.getThickness() / 2.0F >= -box.getHeight() - EPS);
            Assert.assertTrue("rule bottom", rule.getY() + rule.getThickness() / 2.0F <= box.getDepth() + EPS);
            Assert.assertTrue("rule left", rule.getX() >= -box.getLeftInkOverhang() - EPS);
            Assert.assertTrue("rule right", rule.getX() + rule.getWidth()
                    <= box.getWidth() + box.getRightInkOverhang() + EPS);
        }
    }

    @Test
    public void tallRadicalsExtendBeyondLastVariantAndContainEveryElement() {
        for (MathMetrics metrics : Arrays.asList(METRICS, OFFSET_INK)) {
            for (int rows : new int[] {4, 12}) {
                LatexNode content = matrix(rows);
                MathBox radicand = layout(metrics, content);
                Assert.assertTrue(radicand.getTotalHeight() > SIZE
                        * MathConstants.SQRT_VARIANT_DEPTH_EM[MathConstants.SQRT_VARIANT_DEPTH_EM.length - 1]);
                MathBox root = layout(metrics, new LatexSqrt(atom("3"), content));
                assertContained(root, metrics);
                RuleElem bar = root.getRules().get(0);
                Assert.assertTrue("positive clearance", -radicand.getHeight()
                        - (bar.getY() + bar.getThickness() / 2.0F) >= MathConstants.RULE_THICKNESS_EM * SIZE - EPS);
                GlyphElem radical = root.getGlyphs().get(0);
                float radicalSize = SIZE * radical.getSizeScale();
                float radicalBottom = radical.getY() + metrics.inkCenterOffsetY("√", radicalSize)
                        + metrics.inkHeight("√", radicalSize) / 2.0F;
                Assert.assertTrue("radical covers content bottom", radicalBottom >= radicand.getDepth() - EPS);
                float contentRight = root.getGlyphs().get(1).getX() + radicand.getWidth();
                Assert.assertTrue("bar covers content right", bar.getX() + bar.getWidth() >= contentRight - EPS);
            }
        }
    }

    @Test
    public void nestedTallFractionsAndTallRootIndexRemainContained() {
        LatexNode tall = new LatexFrac(matrix(12), new LatexFrac(matrix(8), matrix(4)));
        for (MathMetrics metrics : Arrays.asList(METRICS, OFFSET_INK)) {
            assertContained(layout(metrics, new LatexSqrt(matrix(20), tall)), metrics);
            assertContained(layout(metrics, new LatexFrac(new LatexSqrt(atom("3"), tall), atom("2"))), metrics);
            assertContained(layout(metrics, new LatexSqrt(null, new LatexSqrt(null, tall))), metrics);
        }
    }

    @Test
    public void groupingAndScriptsDoNotAddOrdinarySpacing() {
        LatexNode x = atom("x");
        float plainX = layout(METRICS, x, x).getGlyphs().get(1).getX();
        Assert.assertEquals(plainX, layout(METRICS, x, new LatexGroup(Arrays.asList(x)))
                .getGlyphs().get(1).getX(), EPS);
        Assert.assertEquals(plainX, layout(METRICS, x, new LatexSupSub(x, atom("2"), null))
                .getGlyphs().get(1).getX(), EPS);
        Assert.assertEquals(plainX, layout(METRICS, x, new LatexSqrt(null, x))
                .getGlyphs().get(1).getX(), EPS);
        Assert.assertEquals(plainX, layout(METRICS, x, new LatexAccent("^", x, false, false))
                .getGlyphs().get(1).getX(), EPS);
    }

    @Test
    public void scriptsKeepOperatorAndRelationSpacingIncludingBinDemotion() {
        for (AtomClass type : new AtomClass[] {AtomClass.OP, AtomClass.REL, AtomClass.BIN}) {
            LatexNode base = new LatexAtom(type == AtomClass.OP ? "f" : "+", type);
            MathBox plain = layout(METRICS, atom("x"), base, atom("y"));
            MathBox scripted = layout(METRICS, atom("x"), new LatexSupSub(base, atom("2"), null), atom("y"));
            Assert.assertEquals(plain.getGlyphs().get(1).getX(), scripted.getGlyphs().get(1).getX(), EPS);
        }
        LatexNode op = new LatexAtom("f", AtomClass.OP);
        LatexNode scriptedOp = new LatexSupSub(op, atom("2"), null);
        MathBox combined = layout(METRICS, scriptedOp, new LatexAtom("+", AtomClass.BIN), atom("x"));
        Assert.assertEquals("BIN after scripted OP becomes ORD", layout(METRICS, scriptedOp).getWidth()
                + MathConstants.THIN_MU / 18.0F * SIZE, combined.getGlyphs().get(2).getX(), EPS);
    }

    @Test
    public void fractionRetainsInnerSpacing() {
        LatexNode frac = new LatexFrac(atom("1"), atom("2"));
        MathBox alone = layout(METRICS, frac);
        MathBox afterX = layout(METRICS, atom("x"), frac);
        Assert.assertEquals(SIZE * (0.5F + MathConstants.THIN_MU / 18.0F),
                afterX.getGlyphs().get(1).getX() - alone.getGlyphs().get(0).getX(), EPS);
    }

    private static LatexAtom sum() {
        LatexAtom sum = new LatexAtom("∑", AtomClass.OP, LatexAtom.OperatorMode.BIG_OPERATOR);
        sum.setLimitsFlag(LatexAtom.LIMITS_NOLIMITS);
        return sum;
    }

    @Test
    public void nolimitsBoundsFollowAxisShiftForBothInkCenters() {
        for (MathMetrics metrics : Arrays.asList(METRICS, OFFSET_INK)) {
            MathBox box = layout(metrics, sum());
            assertContained(box, metrics);
            Assert.assertEquals(7.5F, box.getHeight(), EPS);
            Assert.assertEquals(2.5F, box.getDepth(), EPS);
            assertContained(layout(metrics, new LatexFrac(sum(), atom("2"))), metrics);
            assertContained(layout(metrics, new LatexSqrt(null, sum())), metrics);
            assertContained(layout(metrics, new LatexSupSub(sum(), atom("n"), atom("i"))), metrics);
        }
    }
}
