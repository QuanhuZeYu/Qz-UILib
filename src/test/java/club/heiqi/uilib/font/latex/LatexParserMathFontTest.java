package club.heiqi.uilib.font.latex;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.latex.node.LatexAtom;
import club.heiqi.uilib.font.latex.node.LatexFrac;
import club.heiqi.uilib.font.latex.node.LatexGroup;
import club.heiqi.uilib.font.latex.node.LatexLeftRight;
import club.heiqi.uilib.font.latex.node.LatexMatrix;
import club.heiqi.uilib.font.latex.node.LatexSqrt;
import club.heiqi.uilib.font.latex.node.LatexSupSub;

/** 字体命令只消费一个参数，透明性与字号边界分别保留。 */
public class LatexParserMathFontTest {

    @Test
    public void singleSymbolArgumentsRetainTheirClassesAndExtraGroupsStayOrdinary() {
        String[] tokens = {"+", "=", "\\sum", "\\sin", "("};
        LatexAtom.AtomClass[] classes = {LatexAtom.AtomClass.BIN, LatexAtom.AtomClass.REL,
                LatexAtom.AtomClass.OP, LatexAtom.AtomClass.OP, LatexAtom.AtomClass.OPEN};
        for (int i = 0; i < tokens.length; i++) {
            LatexGroup group = group(single("\\mathrm{" + tokens[i] + "}"));
            Assert.assertTrue(group.isTransparentForAtomClass());
            LatexAtom atom = font(child(group), MathFontStyle.UPRIGHT);
            Assert.assertEquals(classes[i], atom.getAtomClass());
            LatexGroup extra = group(child(single("\\mathrm{{" + tokens[i] + "}}")));
            Assert.assertFalse(extra.isTransparentForAtomClass());
        }
        Assert.assertFalse(group(single("{x}")).isTransparentForAtomClass());
        Assert.assertFalse(group(single("\\mathrm{ab}")).isTransparentForAtomClass());
        Assert.assertFalse(group(single("\\mathrm{}")).isTransparentForAtomClass());
        LatexGroup space = group(single("\\mathit{\\quad}"));
        Assert.assertTrue(space.isTransparentForAtomClass());
        Assert.assertEquals(LatexNode.Kind.SPACE, child(space).getKind());
    }

    @Test
    public void nestedFontScopesRestoreBeforeOuterScriptsAndFollowingAtoms() {
        List<LatexNode> nodes = LatexParser.parse("\\mathrm{x_i\\mathit{2}z}^j k");
        LatexSupSub outer = (LatexSupSub) nodes.get(0);
        List<LatexNode> argument = group(outer.getBase()).getChildren();
        LatexSupSub inner = (LatexSupSub) argument.get(0);
        font(inner.getBase(), MathFontStyle.UPRIGHT);
        font(inner.getSub(), MathFontStyle.UPRIGHT);
        font(child(argument.get(1)), MathFontStyle.ITALIC);
        font(argument.get(2), MathFontStyle.UPRIGHT);
        font(outer.getSup(), MathFontStyle.INHERIT);
        font(nodes.get(1), MathFontStyle.INHERIT);
        LatexSupSub bare = (LatexSupSub) single("\\mathrm x_i^2");
        font(child(bare.getBase()), MathFontStyle.UPRIGHT);
        font(bare.getSub(), MathFontStyle.INHERIT);
        font(bare.getSup(), MathFontStyle.INHERIT);
        LatexSupSub multi = (LatexSupSub) single("\\mathrm{ab}^2");
        Assert.assertEquals(2, group(multi.getBase()).getChildren().size());
        Assert.assertFalse(group(multi.getBase()).isTransparentForAtomClass());
        LatexGroup nested = group(single("\\mathrm\\mathit x"));
        Assert.assertTrue(nested.isTransparentForAtomClass());
        Assert.assertTrue(group(child(nested)).isTransparentForAtomClass());
        font(child(child(nested)), MathFontStyle.ITALIC);
    }

    @Test
    public void sizeCopiesPreserveFontAndBothGroupStyleLayers() {
        LatexGroup group = group(single("\\scriptstyle\\mathrm{\\displaystyle x}"));
        Assert.assertTrue(group.isTransparentForAtomClass());
        Assert.assertEquals(MathStyleOverride.SCRIPT, group.getMathStyleOverride());
        Assert.assertEquals(MathStyleOverride.DISPLAY, child(group).getMathStyleOverride());
        font(child(group), MathFontStyle.UPRIGHT);
        LatexGroup bare = group(single("\\scriptstyle\\mathrm\\displaystyle x"));
        Assert.assertEquals(MathStyleOverride.SCRIPT, bare.getMathStyleOverride());
        Assert.assertEquals(MathStyleOverride.DISPLAY, child(bare).getMathStyleOverride());
        font(child(child(bare)), MathFontStyle.UPRIGHT);
        LatexGroup bin = group(LatexParser.parse("a\\mathrm{\\scriptstyle +}b").get(1));
        Assert.assertEquals(MathStyleOverride.INHERIT, bin.getMathStyleOverride());
        Assert.assertEquals(MathStyleOverride.SCRIPT, child(bin).getMathStyleOverride());
        Assert.assertEquals(LatexAtom.AtomClass.BIN, font(child(bin), MathFontStyle.UPRIGHT).getAtomClass());
    }

    @Test
    public void limitsReachOperatorsOnlyThroughTransparentGroups() {
        LatexSupSub scripts = (LatexSupSub) single("\\scriptstyle\\mathrm{\\mathit{\\sum}}\\nolimits_i");
        LatexAtom sum = font(child(child(scripts.getBase())), MathFontStyle.ITALIC);
        Assert.assertEquals(LatexAtom.LIMITS_NOLIMITS, sum.getLimitsFlag());
        LatexGroup explicit = group(single("\\mathrm{{\\sum}}\\nolimits"));
        Assert.assertEquals(LatexAtom.LIMITS_DEFAULT, ((LatexAtom) child(child(explicit))).getLimitsFlag());
        LatexGroup copied = group(single("\\textstyle\\mathrm{\\scriptstyle\\sum\\nolimits}"));
        Assert.assertTrue(copied.isTransparentForAtomClass());
        Assert.assertEquals(LatexAtom.LIMITS_NOLIMITS, font(child(copied), MathFontStyle.UPRIGHT).getLimitsFlag());
    }

    @Test
    public void structuresInheritFontLexicallyWithoutChangingSizeConversions() {
        LatexFrac frac = (LatexFrac) child(single("\\mathit{\\dfrac{\\scriptstyle a}{\\mathrm b}}"));
        Assert.assertEquals(LatexFrac.FractionStyle.DISPLAY, frac.getFractionStyle());
        font(child(frac.getNumerator()), MathFontStyle.ITALIC);
        Assert.assertEquals(MathStyleOverride.SCRIPT, child(frac.getNumerator()).getMathStyleOverride());
        font(child(child(frac.getDenominator())), MathFontStyle.UPRIGHT);
        LatexSqrt sqrt = (LatexSqrt) child(single("\\mathrm{\\sqrt[3]{x}}"));
        font(child(sqrt.getIndex()), MathFontStyle.UPRIGHT);
        font(child(sqrt.getRadicand()), MathFontStyle.UPRIGHT);
        LatexMatrix matrix = (LatexMatrix) child(single(
                "\\mathit{\\begin{array}{lr}a&\\mathrm b\\\\\\scriptstyle c&d\\end{array}}"));
        font(matrix.getRows().get(0).get(0).get(0), MathFontStyle.ITALIC);
        font(child(matrix.getRows().get(0).get(1).get(0)), MathFontStyle.UPRIGHT);
        font(matrix.getRows().get(1).get(0).get(0), MathFontStyle.ITALIC);
        font(matrix.getRows().get(1).get(1).get(0), MathFontStyle.ITALIC);
        Assert.assertEquals(MathStyleOverride.SCRIPT, matrix.getRows().get(1).get(0).get(0).getMathStyleOverride());
        Assert.assertEquals(MathStyleOverride.INHERIT, matrix.getRows().get(1).get(1).get(0).getMathStyleOverride());
        Assert.assertEquals('r', matrix.columnAlignOf(1));
        LatexLeftRight fence = (LatexLeftRight) child(single("\\mathrm{\\left(a\\middle|\\mathit b\\right)}"));
        font(child(fence.getParts().get(0)), MathFontStyle.UPRIGHT);
        font(child(child(fence.getParts().get(1))), MathFontStyle.ITALIC);
    }

    @Test
    public void textAndUnknownLiteralsKeepTheirOriginalSemantics() {
        LatexGroup outer = group(single("\\mathit{\\text{a {b} \\% \\mathrm{x}}\\unknown\\?\\alpha 2}"));
        List<LatexNode> nodes = outer.getChildren();
        LatexAtom text = font(nodes.get(0), MathFontStyle.INHERIT);
        Assert.assertEquals(LatexAtom.AtomClass.TEXT, text.getAtomClass());
        Assert.assertEquals("a b % \\mathrmx", text.getText());
        Assert.assertEquals("\\unknown", font(nodes.get(1), MathFontStyle.INHERIT).getText());
        Assert.assertEquals("\\?", font(nodes.get(2), MathFontStyle.INHERIT).getText());
        font(nodes.get(3), MathFontStyle.ITALIC);
        font(nodes.get(4), MathFontStyle.ITALIC);
        Assert.assertEquals("\\mathrmUnknown", font(single("\\mathrmUnknown"), MathFontStyle.INHERIT).getText());
    }

    @Test
    public void missingArgumentsStayEmptyAndDoNotConsumeStructuralTerminators() {
        Assert.assertTrue(group(child(single("\\mathrm"))).getChildren().isEmpty());
        Assert.assertTrue(group(child(child(single("\\mathit\\scriptstyle")))).getChildren().isEmpty());
        LatexGroup explicit = group(single("{\\mathrm}"));
        Assert.assertTrue(group(child(child(explicit))).getChildren().isEmpty());
        LatexSupSub scripts = (LatexSupSub) single("\\mathrm^j");
        Assert.assertTrue(group(child(scripts.getBase())).getChildren().isEmpty());
        font(scripts.getSup(), MathFontStyle.INHERIT);
        LatexLeftRight fence = (LatexLeftRight) single("\\left(\\mathrm\\middle|b\\right)");
        Assert.assertEquals(2, fence.getParts().size());
        Assert.assertTrue(group(child(child(fence.getParts().get(0)))).getChildren().isEmpty());
        font(child(fence.getParts().get(1)), MathFontStyle.INHERIT);
    }

    @Test
    public void approvedConstructorsKeepOldDefaultsAndValidateNewState() {
        LatexAtom atom = new LatexAtom("x", LatexAtom.AtomClass.ORD);
        font(atom, MathFontStyle.INHERIT);
        font(new LatexAtom("x", LatexAtom.AtomClass.ORD, LatexAtom.OperatorMode.NONE), MathFontStyle.INHERIT);
        font(new LatexAtom("x", LatexAtom.AtomClass.ORD, LatexAtom.OperatorMode.NONE,
                MathStyleOverride.DISPLAY), MathFontStyle.INHERIT);
        List<LatexNode> one = Collections.<LatexNode>singletonList(atom);
        Assert.assertFalse(new LatexGroup(one).isTransparentForAtomClass());
        Assert.assertFalse(new LatexGroup(one, MathStyleOverride.DISPLAY).isTransparentForAtomClass());
        Assert.assertTrue(new LatexGroup(one, MathStyleOverride.DISPLAY, true).isTransparentForAtomClass());
        rejects(() -> new LatexAtom("x", LatexAtom.AtomClass.ORD, LatexAtom.OperatorMode.NONE,
                MathStyleOverride.INHERIT, null));
        rejects(() -> new LatexGroup(null, MathStyleOverride.INHERIT, true));
        rejects(() -> new LatexGroup(Collections.<LatexNode>emptyList(), MathStyleOverride.INHERIT, true));
        rejects(() -> new LatexGroup(Arrays.<LatexNode>asList(atom, atom), MathStyleOverride.INHERIT, true));
    }

    private static void rejects(Runnable construction) {
        try {
            construction.run();
            Assert.fail("invalid metadata must be rejected");
        } catch (IllegalArgumentException expected) {
            // 与既有 AST 参数验证一致。
        }
    }

    private static LatexNode single(String source) {
        List<LatexNode> nodes = LatexParser.parse(source);
        Assert.assertEquals(source, 1, nodes.size());
        return nodes.get(0);
    }

    private static LatexGroup group(LatexNode node) {
        return (LatexGroup) node;
    }

    private static LatexNode child(LatexNode node) {
        Assert.assertEquals(1, group(node).getChildren().size());
        return group(node).getChildren().get(0);
    }

    private static LatexAtom font(LatexNode node, MathFontStyle expected) {
        LatexAtom atom = (LatexAtom) node;
        Assert.assertEquals(expected, atom.getMathFontStyle());
        return atom;
    }
}
