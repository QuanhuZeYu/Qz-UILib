package club.heiqi.uilib.font.latex;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.latex.node.LatexAtom;
import club.heiqi.uilib.font.latex.node.LatexAtom.AtomClass;
import club.heiqi.uilib.font.latex.node.LatexFrac;
import club.heiqi.uilib.font.latex.node.LatexGroup;
import club.heiqi.uilib.font.latex.node.LatexLeftRight;
import club.heiqi.uilib.font.latex.node.LatexMatrix;
import club.heiqi.uilib.font.latex.node.LatexSqrt;
import club.heiqi.uilib.font.latex.node.LatexSupSub;

/** 解析边界回归：直接断言 AST 归属，避免渲染快照掩盖错误绑定。 */
public class LatexParserRegressionTest {

    @Test
    public void commandsInsideFencesKeepTheirScripts() {
        LatexLeftRight fence = kind(single("\\left(\\alpha_i+\\sum_{i=1}^{n}x_i\\right)"), LatexLeftRight.class);
        List<LatexNode> body = children(fence.getContent());
        Assert.assertEquals(4, body.size());
        LatexSupSub alpha = kind(body.get(0), LatexSupSub.class);
        atom(alpha.getBase(), "α", AtomClass.ORD);
        atom(alpha.getSub(), "i", AtomClass.ORD);
        LatexSupSub sum = kind(body.get(2), LatexSupSub.class);
        atom(sum.getBase(), "∑", AtomClass.OP);
        Assert.assertEquals(3, children(sum.getSub()).size());
        atom(children(sum.getSup()).get(0), "n", AtomClass.ORD);
        kind(body.get(3), LatexSupSub.class);
        Assert.assertEquals(")", fence.getRightDelimiter());
    }

    @Test
    public void nestedFencesAndMiddleKeepFactorAndSegmentBoundaries() {
        LatexLeftRight outer = kind(single("\\left(\\left[\\alpha_i\\right]^2\\middle|\\beta_j\\right)"), LatexLeftRight.class);
        Assert.assertEquals(2, outer.getParts().size());
        Assert.assertEquals("|", outer.getMiddleDelimiters().get(0));
        LatexSupSub nested = kind(children(outer.getParts().get(0)).get(0), LatexSupSub.class);
        atom(nested.getSup(), "2", AtomClass.ORD);
        LatexLeftRight inner = kind(nested.getBase(), LatexLeftRight.class);
        kind(children(inner.getContent()).get(0), LatexSupSub.class);
        kind(children(outer.getParts().get(1)).get(0), LatexSupSub.class);
    }

    @Test
    public void unclosedFenceLeavesMatrixEndForItsEnvironment() {
        List<LatexNode> nodes = LatexParser.parse("\\begin{matrix}\\left(\\alpha_i\\end{matrix}+x");
        Assert.assertEquals(3, nodes.size());
        LatexMatrix matrix = kind(nodes.get(0), LatexMatrix.class);
        LatexLeftRight fence = kind(matrix.getRows().get(0).get(0).get(0), LatexLeftRight.class);
        Assert.assertNull(fence.getRightDelimiter());
        kind(children(fence.getContent()).get(0), LatexSupSub.class);
        atom(nodes.get(2), "x", AtomClass.ORD);
    }

    @Test
    public void bareFractionAndRootArgumentsLeaveScriptsToOuterFactor() {
        for (String source : new String[] {"\\frac23^2", "\\frac{2}{3}^2", "\\frac2\\alpha^2"}) {
            LatexSupSub script = kind(single(source), LatexSupSub.class);
            LatexFrac frac = kind(script.getBase(), LatexFrac.class);
            Assert.assertFalse(frac.getDenominator() instanceof LatexSupSub);
            atom(script.getSup(), "2", AtomClass.ORD);
        }
        for (String source : new String[] {"\\sqrt x^2", "\\sqrt{x}^2", "\\sqrt\\alpha^2"}) {
            LatexSupSub script = kind(single(source), LatexSupSub.class);
            LatexSqrt root = kind(script.getBase(), LatexSqrt.class);
            Assert.assertFalse(root.getRadicand() instanceof LatexSupSub);
            atom(script.getSup(), "2", AtomClass.ORD);
        }
        LatexFrac bare = kind(kind(single("\\frac23^2"), LatexSupSub.class).getBase(), LatexFrac.class);
        atom(bare.getNumerator(), "2", AtomClass.ORD);
        atom(bare.getDenominator(), "3", AtomClass.ORD);
        LatexSqrt bareRoot = kind(kind(single("\\sqrt x^2"), LatexSupSub.class).getBase(), LatexSqrt.class);
        atom(bareRoot.getRadicand(), "x", AtomClass.ORD);
        LatexFrac grouped = kind(single("\\frac{2}{3^2}"), LatexFrac.class);
        kind(children(grouped.getDenominator()).get(0), LatexSupSub.class);
    }

    @Test
    public void emptyTextUsesEmptyGroupAndRetainsFollowingInput() {
        for (String source : new String[] {"\\text{}", "\\text{{}}", "\\text"}) {
            Assert.assertTrue(children(single(source)).isEmpty());
        }
        List<LatexNode> nodes = LatexParser.parse("\\text{}+x");
        Assert.assertEquals(3, nodes.size());
        Assert.assertTrue(children(nodes.get(0)).isEmpty());
        atom(nodes.get(2), "x", AtomClass.ORD);
        LatexSupSub script = kind(single("\\text{}^2"), LatexSupSub.class);
        Assert.assertTrue(children(script.getBase()).isEmpty());
        atom(single("\\text{ }"), " ", AtomClass.TEXT);
    }

    @Test
    public void nestedMatricesConsumeOnlyTheirOwnEnd() {
        for (String innerName : new String[] {"matrix", "pmatrix"}) {
            List<LatexNode> nodes = LatexParser.parse("\\begin{pmatrix}\\begin{" + innerName
                    + "}a&b\\end{" + innerName + "}&c\\\\d&e\\end{pmatrix}+x");
            Assert.assertEquals(3, nodes.size());
            LatexMatrix outer = kind(nodes.get(0), LatexMatrix.class);
            Assert.assertEquals(2, outer.getRows().size());
            Assert.assertEquals(2, outer.getRows().get(0).size());
            Assert.assertEquals(2, outer.getRows().get(1).size());
            LatexMatrix inner = kind(outer.getRows().get(0).get(0).get(0), LatexMatrix.class);
            Assert.assertEquals(1, inner.getRows().size());
            Assert.assertEquals(2, inner.getRows().get(0).size());
            atom(outer.getRows().get(0).get(1).get(0), "c", AtomClass.ORD);
            atom(outer.getRows().get(1).get(1).get(0), "e", AtomClass.ORD);
            atom(nodes.get(2), "x", AtomClass.ORD);
        }
    }

    @Test
    public void matrixEndSupportsEmptyTrailingRowAndMissingInnerEnd() {
        for (String body : new String[] {"", "a", "a\\\\", "a "}) {
            List<LatexNode> nodes = LatexParser.parse("\\begin{matrix}" + body + "\\end{matrix}x");
            Assert.assertEquals(2, nodes.size());
            kind(nodes.get(0), LatexMatrix.class);
            atom(nodes.get(1), "x", AtomClass.ORD);
        }
        List<LatexNode> nodes = LatexParser.parse("\\begin{pmatrix}\\begin{matrix}a\\end{pmatrix}x");
        Assert.assertEquals(2, nodes.size());
        atom(nodes.get(1), "x", AtomClass.ORD);
        for (String source : new String[] {"\\begin{matrix}a", "\\begin{matrix}a ", "\\begin{matrix} "}) {
            kind(single(source), LatexMatrix.class);
        }
    }

    @Test
    public void supplementaryMathCharactersRemainCompleteAtoms() {
        String x = "\uD835\uDC65";
        LatexSupSub script = kind(single(x + "^2"), LatexSupSub.class);
        atom(script.getBase(), x, AtomClass.ORD);
        atom(script.getSup(), "2", AtomClass.ORD);
        List<LatexNode> nodes = LatexParser.parse(x + x);
        Assert.assertEquals(2, nodes.size());
        atom(nodes.get(0), x, AtomClass.ORD);
        atom(nodes.get(1), x, AtomClass.ORD);
        LatexFrac frac = kind(single("\\frac" + x + "2"), LatexFrac.class);
        atom(frac.getNumerator(), x, AtomClass.ORD);
    }

    @Test
    public void mathWhitespaceDoesNotSplitScriptBinding() {
        for (String space : new String[] {"", " ", "\t", "\r\n", "\f", "\u000B"}) {
            LatexSupSub script = kind(single("x" + space + "_" + space + "i" + space + "^" + space + "2" + space), LatexSupSub.class);
            atom(script.getBase(), "x", AtomClass.ORD);
            atom(script.getSub(), "i", AtomClass.ORD);
            atom(script.getSup(), "2", AtomClass.ORD);
            atom(single("x" + space), "x", AtomClass.ORD);
        }
    }

    @Test
    public void modifiersBindCurrentNamedOperatorOnly() {
        List<LatexNode> nodes = LatexParser.parse("\\sum_{i=1}^n+\\lim\\nolimits_{x\\to0}x");
        Assert.assertEquals(4, nodes.size());
        LatexAtom sum = kind(kind(nodes.get(0), LatexSupSub.class).getBase(), LatexAtom.class);
        LatexAtom lim = kind(kind(nodes.get(2), LatexSupSub.class).getBase(), LatexAtom.class);
        Assert.assertEquals(LatexAtom.LIMITS_DEFAULT, sum.getLimitsFlag());
        Assert.assertEquals(LatexAtom.LIMITS_NOLIMITS, lim.getLimitsFlag());
        Assert.assertEquals(LatexAtom.OperatorMode.LIMITS_OPERATOR, lim.getOperatorMode());
        LatexSupSub spaced = kind(single("\\sum _i ^n \\nolimits "), LatexSupSub.class);
        Assert.assertEquals(LatexAtom.LIMITS_NOLIMITS, kind(spaced.getBase(), LatexAtom.class).getLimitsFlag());
        LatexAtom sin = kind(single("\\sin \\limits"), LatexAtom.class);
        Assert.assertEquals(LatexAtom.LIMITS_LIMITS, sin.getLimitsFlag());
    }

    @Test
    public void modifiersNeverReachAcrossGroupsOrScriptArguments() {
        LatexSupSub script = kind(single("\\sum_{\\int}\\nolimits"), LatexSupSub.class);
        Assert.assertEquals(LatexAtom.LIMITS_NOLIMITS, kind(script.getBase(), LatexAtom.class).getLimitsFlag());
        Assert.assertEquals(LatexAtom.LIMITS_DEFAULT, kind(children(script.getSub()).get(0), LatexAtom.class).getLimitsFlag());
        for (String source : new String[] {"{\\sum}\\nolimits", "\\sum+x\\nolimits", "\\sum+\\nolimits"}) {
            List<LatexNode> nodes = LatexParser.parse(source);
            LatexNode first = nodes.get(0);
            LatexAtom sum = kind(first instanceof LatexGroup ? children(first).get(0) : first, LatexAtom.class);
            Assert.assertEquals(LatexAtom.LIMITS_DEFAULT, sum.getLimitsFlag());
        }
    }

    @Test
    public void nestedTextGroupsPreserveSpacesAndEscapedBraces() {
        List<LatexNode> nodes = LatexParser.parse("\\text{a {b} c}+x");
        Assert.assertEquals(3, nodes.size());
        atom(nodes.get(0), "a b c", AtomClass.TEXT);
        atom(nodes.get(2), "x", AtomClass.ORD);
        atom(single("\\text{a {b {c}} \\{d\\} \\%}"), "a b c {d} %", AtomClass.TEXT);
        atom(single("\\text{a {b"), "a b", AtomClass.TEXT);
    }

    private static LatexNode single(String source) {
        List<LatexNode> nodes = LatexParser.parse(source);
        Assert.assertEquals(source, 1, nodes.size());
        return nodes.get(0);
    }

    private static List<LatexNode> children(LatexNode node) {
        return kind(node, LatexGroup.class).getChildren();
    }

    private static void atom(LatexNode node, String text, AtomClass atomClass) {
        LatexAtom atom = kind(node, LatexAtom.class);
        Assert.assertEquals(text, atom.getText());
        Assert.assertEquals(atomClass, atom.getAtomClass());
    }

    private static <T extends LatexNode> T kind(LatexNode node, Class<T> type) {
        Assert.assertTrue("Expected " + type.getSimpleName() + ": " + node, type.isInstance(node));
        return type.cast(node);
    }
}
