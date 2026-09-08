package club.heiqi.uilib.font.latex;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.latex.node.LatexAccent;
import club.heiqi.uilib.font.latex.node.LatexAtom;
import club.heiqi.uilib.font.latex.node.LatexBinom;
import club.heiqi.uilib.font.latex.node.LatexFrac;
import club.heiqi.uilib.font.latex.node.LatexGroup;
import club.heiqi.uilib.font.latex.node.LatexLeftRight;
import club.heiqi.uilib.font.latex.node.LatexMatrix;
import club.heiqi.uilib.font.latex.node.LatexSpace;
import club.heiqi.uilib.font.latex.node.LatexSqrt;
import club.heiqi.uilib.font.latex.node.LatexSupSub;

/** 样式声明只标注本列表的完整因子；不复制父结构的样式到子列表。 */
public class LatexParserStyleTest {

    @Test
    public void declarationsKeepFlatAdjacencyAndApplyToEveryFollowingFactor() {
        List<LatexNode> nodes = LatexParser.parse("a+\\scriptstyle b+c\\textstyle d");
        Assert.assertEquals(6, nodes.size());
        style(nodes.get(0), MathStyleOverride.INHERIT);
        Assert.assertEquals(LatexAtom.AtomClass.BIN, ((LatexAtom) nodes.get(1)).getAtomClass());
        style(nodes.get(1), MathStyleOverride.INHERIT);
        style(nodes.get(2), MathStyleOverride.SCRIPT);
        style(nodes.get(3), MathStyleOverride.SCRIPT);
        style(nodes.get(4), MathStyleOverride.SCRIPT);
        style(nodes.get(5), MathStyleOverride.TEXT);
        Assert.assertEquals(LatexAtom.AtomClass.BIN, ((LatexAtom) nodes.get(3)).getAtomClass());
    }

    @Test
    public void groupsAndConsecutiveDeclarationsHaveLocalScope() {
        List<LatexNode> nodes = LatexParser.parse("{\\scriptstyle a}b\\displaystyle\\scriptscriptstyle c");
        style(nodes.get(0), MathStyleOverride.INHERIT);
        style(children(nodes.get(0)).get(0), MathStyleOverride.SCRIPT);
        style(nodes.get(1), MathStyleOverride.INHERIT);
        style(nodes.get(2), MathStyleOverride.SCRIPTSCRIPT);
        Assert.assertTrue(LatexParser.parse("\\displaystyle\\textstyle").isEmpty());
        Assert.assertEquals(1, LatexParser.parse("a\\scriptstyle").size());
        Assert.assertTrue(children(single("{\\scriptstyle}")).isEmpty());
    }

    @Test
    public void declarationWrapsCompleteFactorWithoutStampingChildren() {
        LatexSupSub scripts = (LatexSupSub) single("\\scriptstyle\\dfrac ab^2");
        style(scripts, MathStyleOverride.SCRIPT);
        style(scripts.getSup(), MathStyleOverride.INHERIT);
        LatexFrac fraction = (LatexFrac) scripts.getBase();
        style(fraction, MathStyleOverride.INHERIT);
        Assert.assertEquals(LatexFrac.FractionStyle.DISPLAY, fraction.getFractionStyle());
        style(fraction.getNumerator(), MathStyleOverride.INHERIT);
        style(fraction.getDenominator(), MathStyleOverride.INHERIT);
        Assert.assertEquals("b", ((LatexAtom) fraction.getDenominator()).getText());
        Assert.assertEquals("2", ((LatexAtom) scripts.getSup()).getText());
        Assert.assertEquals(LatexFrac.FractionStyle.TEXT, ((LatexFrac) single("\\tfrac ab")).getFractionStyle());
        Assert.assertEquals(LatexFrac.FractionStyle.INHERIT, ((LatexFrac) single("\\frac ab")).getFractionStyle());
    }

    @Test
    public void explicitChildDeclarationsDoNotLeakToSiblings() {
        LatexSupSub scripts = (LatexSupSub) single("\\scriptstyle x_{\\displaystyle y}^z");
        style(scripts, MathStyleOverride.SCRIPT);
        style(scripts.getBase(), MathStyleOverride.INHERIT);
        style(scripts.getSub(), MathStyleOverride.INHERIT);
        style(children(scripts.getSub()).get(0), MathStyleOverride.DISPLAY);
        style(scripts.getSup(), MathStyleOverride.INHERIT);
        LatexFrac fraction = (LatexFrac) single("\\scriptstyle\\frac{\\textstyle a}{b}");
        style(fraction, MathStyleOverride.SCRIPT);
        style(fraction.getNumerator(), MathStyleOverride.INHERIT);
        style(children(fraction.getNumerator()).get(0), MathStyleOverride.TEXT);
        style(children(fraction.getDenominator()).get(0), MathStyleOverride.INHERIT);
    }

    @Test
    public void middleDoesNotEndDeclarationAndNestedFencesDoNotLeak() {
        List<LatexNode> nodes = LatexParser.parse(
                "\\left(\\scriptstyle a\\middle|b\\left[\\textstyle c\\right]d\\right)e");
        LatexLeftRight fence = (LatexLeftRight) nodes.get(0);
        style(children(fence.getParts().get(0)).get(0), MathStyleOverride.SCRIPT);
        List<LatexNode> second = children(fence.getParts().get(1));
        style(second.get(0), MathStyleOverride.SCRIPT);
        style(second.get(1), MathStyleOverride.SCRIPT);
        style(children(((LatexLeftRight) second.get(1)).getContent()).get(0), MathStyleOverride.TEXT);
        style(second.get(2), MathStyleOverride.SCRIPT);
        style(nodes.get(1), MathStyleOverride.INHERIT);
    }

    @Test
    public void middleRetainsItsEntryStyleEvenWithoutAnAdjacentFactor() {
        LatexLeftRight fence = (LatexLeftRight) single(
                "\\left.a\\scriptstyle\\middle|\\textstyle b\\scriptscriptstyle\\middle|\\right.");
        style(fence.getParts().get(0), MathStyleOverride.INHERIT);
        style(children(fence.getParts().get(0)).get(0), MathStyleOverride.INHERIT);
        style(fence.getParts().get(1), MathStyleOverride.SCRIPT);
        style(children(fence.getParts().get(1)).get(0), MathStyleOverride.TEXT);
        style(fence.getParts().get(2), MathStyleOverride.SCRIPTSCRIPT);
        Assert.assertTrue(children(fence.getParts().get(2)).isEmpty());
    }

    @Test
    public void matrixCellsResetDeclarationsAndPreserveColumnAlignment() {
        LatexMatrix matrix = (LatexMatrix) single(
                "\\scriptstyle\\begin{array}{lr}\\displaystyle a&b\\\\c&\\textstyle d\\end{array}");
        style(matrix, MathStyleOverride.SCRIPT);
        style(matrix.getRows().get(0).get(0).get(0), MathStyleOverride.DISPLAY);
        style(matrix.getRows().get(0).get(1).get(0), MathStyleOverride.INHERIT);
        style(matrix.getRows().get(1).get(0).get(0), MathStyleOverride.INHERIT);
        style(matrix.getRows().get(1).get(1).get(0), MathStyleOverride.TEXT);
        Assert.assertEquals('l', matrix.columnAlignOf(0));
        Assert.assertEquals('r', matrix.columnAlignOf(1));
    }

    @Test
    public void styledArraysRetainEvenUnusedExplicitColumnAlignments() {
        String source = "\\scriptstyle\\begin{array}{lrr}a\\end{array}";
        LatexMatrix matrix = (LatexMatrix) single(source);
        style(matrix, MathStyleOverride.SCRIPT);
        Assert.assertEquals('r', matrix.columnAlignOf(2));
        LatexSupSub scripts = (LatexSupSub) single(source + "^2");
        style(scripts, MathStyleOverride.SCRIPT);
        style(scripts.getBase(), MathStyleOverride.INHERIT);
        Assert.assertEquals('r', ((LatexMatrix) scripts.getBase()).columnAlignOf(2));
        LatexFrac fraction = (LatexFrac) single("\\frac" + source + " b");
        style(fraction.getNumerator(), MathStyleOverride.SCRIPT);
        Assert.assertEquals('r', ((LatexMatrix) fraction.getNumerator()).columnAlignOf(2));
    }

    @Test
    public void copyingStyledAtomsPreservesLimitsFlags() {
        LatexAtom alone = (LatexAtom) single("\\scriptstyle\\sum\\nolimits");
        Assert.assertEquals(LatexAtom.LIMITS_NOLIMITS, alone.getLimitsFlag());
        style(alone, MathStyleOverride.SCRIPT);
        LatexSupSub scripts = (LatexSupSub) single("\\textstyle\\sum\\limits_i");
        style(scripts, MathStyleOverride.TEXT);
        LatexAtom base = (LatexAtom) scripts.getBase();
        style(base, MathStyleOverride.INHERIT);
        Assert.assertEquals(LatexAtom.LIMITS_LIMITS, base.getLimitsFlag());
    }

    @Test
    public void bareArgumentDeclarationsKeepAtomBindingAndTolerateMissingArguments() {
        LatexSupSub scripts = (LatexSupSub) single("\\dfrac\\scriptstyle a\\textstyle b^2");
        LatexFrac fraction = (LatexFrac) scripts.getBase();
        style(fraction.getNumerator(), MathStyleOverride.SCRIPT);
        style(fraction.getDenominator(), MathStyleOverride.TEXT);
        Assert.assertEquals("2", ((LatexAtom) scripts.getSup()).getText());
        LatexSupSub bareScript = (LatexSupSub) single("x_\\displaystyle i^2");
        style(bareScript.getSub(), MathStyleOverride.DISPLAY);
        Assert.assertEquals("2", ((LatexAtom) bareScript.getSup()).getText());
        Assert.assertTrue(children(((LatexFrac) single("\\dfrac\\scriptstyle")).getNumerator()).isEmpty());
        LatexFrac missing = (LatexFrac) children(single("{\\frac\\textstyle}")).get(0);
        Assert.assertTrue(children(missing.getDenominator()).isEmpty());
        LatexLeftRight fence = (LatexLeftRight) single("\\left(\\frac a\\scriptstyle\\middle|b\\right)");
        Assert.assertEquals(2, fence.getParts().size());
        Assert.assertTrue(children(((LatexFrac) children(fence.getContent()).get(0)).getDenominator()).isEmpty());
        style(children(fence.getParts().get(1)).get(0), MathStyleOverride.INHERIT);
        Assert.assertEquals("x", ((LatexAtom) single("x_\\scriptstyle")).getText());
    }

    @Test
    public void textAndUnknownCommandContentsStayLiteral() {
        LatexAtom text = (LatexAtom) single("\\text{\\scriptstyle x}");
        Assert.assertEquals("\\scriptstyle x", text.getText());
        Assert.assertEquals("\\scriptstyleUnknown", ((LatexAtom) single("\\scriptstyleUnknown")).getText());
    }

    @Test
    public void allParserKindsCarryStyleWithoutChangingKind() {
        String[] inputs = {"a", "a^b", "\\frac ab", "\\sqrt a", "{a}", "\\binom ab", "\\quad",
                "\\hat a", "\\left(a\\right)", "\\begin{matrix}a\\end{matrix}"};
        for (String input : inputs) {
            LatexNode plain = single(input);
            LatexNode styled = single("\\scriptstyle " + input);
            Assert.assertEquals(plain.getKind(), styled.getKind());
            style(plain, MathStyleOverride.INHERIT);
            style(styled, MathStyleOverride.SCRIPT);
        }
    }

    @Test
    public void oldConstructorsInheritAndEveryNewEnumRejectsNull() {
        final LatexAtom atom = new LatexAtom("a", LatexAtom.AtomClass.ORD);
        final List<LatexNode> children = Collections.<LatexNode>singletonList(atom);
        final List<List<List<LatexNode>>> rows = Collections.singletonList(Collections.singletonList(children));
        List<LatexNode> old = Arrays.<LatexNode>asList(atom, new LatexSupSub(atom, atom, null),
                new LatexFrac(atom, atom), new LatexSqrt(null, atom), new LatexGroup(children),
                new LatexBinom(atom, atom), new LatexSpace(1), new LatexAccent("^", atom, false, false),
                new LatexLeftRight("(", atom, ")"), new LatexMatrix(LatexMatrix.Fence.NONE, rows));
        for (LatexNode node : old) {
            style(node, MathStyleOverride.INHERIT);
        }
        Assert.assertEquals(LatexFrac.FractionStyle.INHERIT, ((LatexFrac) old.get(2)).getFractionStyle());
        rejectsNull(() -> new LatexNode(LatexNode.Kind.ATOM, null) {});
        rejectsNull(() -> new LatexAtom("a", LatexAtom.AtomClass.ORD, LatexAtom.OperatorMode.NONE, null));
        rejectsNull(() -> new LatexSupSub(atom, atom, null, null));
        rejectsNull(() -> new LatexFrac(atom, atom, null, LatexFrac.FractionStyle.INHERIT));
        rejectsNull(() -> new LatexFrac(atom, atom, MathStyleOverride.INHERIT, null));
        rejectsNull(() -> new LatexSqrt(null, atom, null));
        rejectsNull(() -> new LatexGroup(children, null));
        rejectsNull(() -> new LatexBinom(atom, atom, null));
        rejectsNull(() -> new LatexSpace(1, null));
        rejectsNull(() -> new LatexAccent("^", atom, false, false, null));
        rejectsNull(() -> new LatexLeftRight("(", children, Collections.<String>emptyList(), ")", null));
        rejectsNull(() -> new LatexMatrix(LatexMatrix.Fence.NONE, rows, null, null));
    }

    private static void rejectsNull(Runnable construction) {
        try {
            construction.run();
            Assert.fail("null style must be rejected");
        } catch (IllegalArgumentException expected) {
            // 新增枚举参数与现有构造器的非法参数异常约定一致。
        }
    }

    private static LatexNode single(String source) {
        List<LatexNode> nodes = LatexParser.parse(source);
        Assert.assertEquals(source, 1, nodes.size());
        return nodes.get(0);
    }

    private static List<LatexNode> children(LatexNode node) {
        return ((LatexGroup) node).getChildren();
    }

    private static void style(LatexNode node, MathStyleOverride expected) {
        Assert.assertEquals(expected, node.getMathStyleOverride());
    }
}
