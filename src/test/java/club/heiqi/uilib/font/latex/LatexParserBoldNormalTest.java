package club.heiqi.uilib.font.latex;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.latex.node.LatexAtom;
import club.heiqi.uilib.font.latex.node.LatexFrac;
import club.heiqi.uilib.font.latex.node.LatexGroup;
import club.heiqi.uilib.font.latex.node.LatexMatrix;
import club.heiqi.uilib.font.latex.node.LatexOperator;
import club.heiqi.uilib.font.latex.node.LatexSupSub;

/** 粗体与显式默认数学字体沿用首批字体命令的参数和恢复语义。 */
public class LatexParserBoldNormalTest {

    @Test
    public void nestedChoicesReplaceOuterFontAndRestoreAfterArgument() {
        List<LatexNode> nodes = LatexParser.parse("\\mathbf{x_i\\mathnormal{y}\\mathrm{z}\\mathit{q}r}^j k");
        LatexSupSub scripts = (LatexSupSub) nodes.get(0);
        List<LatexNode> body = group(scripts.getBase()).getChildren();
        LatexSupSub inner = (LatexSupSub) body.get(0);
        font(inner.getBase(), MathFontStyle.BOLD);
        font(inner.getSub(), MathFontStyle.BOLD);
        font(child(body.get(1)), MathFontStyle.MATH_NORMAL);
        font(child(body.get(2)), MathFontStyle.UPRIGHT);
        font(child(body.get(3)), MathFontStyle.ITALIC);
        font(body.get(4), MathFontStyle.BOLD);
        font(scripts.getSup(), MathFontStyle.INHERIT);
        font(nodes.get(1), MathFontStyle.INHERIT);
    }

    @Test
    public void normalIsExplicitAndBareFontArgumentsDoNotEatScripts() {
        for (String name : new String[] {"mathbf", "mathnormal"}) {
            MathFontStyle expected = "mathbf".equals(name) ? MathFontStyle.BOLD : MathFontStyle.MATH_NORMAL;
            LatexSupSub scripts = (LatexSupSub) LatexParser.parse("\\" + name + " x_i^j").get(0);
            font(child(scripts.getBase()), expected);
            font(scripts.getSub(), MathFontStyle.INHERIT);
            font(scripts.getSup(), MathFontStyle.INHERIT);
        }
        List<LatexNode> nodes = group(LatexParser.parse("\\mathit{\\mathnormal{x}y}").get(0)).getChildren();
        font(child(nodes.get(0)), MathFontStyle.MATH_NORMAL);
        font(nodes.get(1), MathFontStyle.ITALIC);
    }

    @Test
    public void structuralDescendantsInheritFontButTextAndUnknownCommandsDoNot() {
        List<LatexNode> body = group(LatexParser.parse(
                "\\mathbf{\\frac{x}{\\mathnormal{y}}\\text{ab}\\unknown\\begin{matrix}a&b\\end{matrix}}").get(0)).getChildren();
        LatexFrac fraction = (LatexFrac) body.get(0);
        font(child(fraction.getNumerator()), MathFontStyle.BOLD);
        font(child(child(fraction.getDenominator())), MathFontStyle.MATH_NORMAL);
        font(body.get(1), MathFontStyle.INHERIT);
        font(body.get(2), MathFontStyle.INHERIT);
        LatexMatrix matrix = (LatexMatrix) body.get(3);
        font(matrix.getRows().get(0).get(0).get(0), MathFontStyle.BOLD);
        font(matrix.getRows().get(0).get(1).get(0), MathFontStyle.BOLD);
    }

    @Test
    public void fontCommandsPreserveTransparentClassesAndSizeScopes() {
        LatexGroup bold = group(LatexParser.parse("\\scriptstyle\\mathbf{\\displaystyle +}").get(0));
        Assert.assertEquals(MathStyleOverride.SCRIPT, bold.getMathStyleOverride());
        Assert.assertTrue(bold.isTransparentForAtomClass());
        LatexAtom plus = font(child(bold), MathFontStyle.BOLD);
        Assert.assertEquals(MathStyleOverride.DISPLAY, plus.getMathStyleOverride());
        Assert.assertEquals(LatexAtom.AtomClass.BIN, plus.getAtomClass());
        LatexGroup extra = group(child(LatexParser.parse("\\mathnormal{{+}}").get(0)));
        Assert.assertFalse(extra.isTransparentForAtomClass());
        Assert.assertEquals(LatexAtom.AtomClass.BIN, font(child(extra), MathFontStyle.MATH_NORMAL).getAtomClass());
    }

    @Test
    public void operatorNameCanRestoreMathNormalInsideItsRomanBody() {
        LatexOperator operator = (LatexOperator) LatexParser.parse("\\operatorname{a\\mathnormal{b}c}").get(0);
        List<LatexNode> body = group(operator.getBody()).getChildren();
        font(body.get(0), MathFontStyle.UPRIGHT);
        font(child(body.get(1)), MathFontStyle.MATH_NORMAL);
        font(body.get(2), MathFontStyle.UPRIGHT);
    }

    @Test
    public void oldEnumMembersAndAtomConstructorsKeepTheirDefaults() {
        Assert.assertArrayEquals(new MathFontStyle[] {MathFontStyle.INHERIT, MathFontStyle.UPRIGHT,
                MathFontStyle.ITALIC, MathFontStyle.BOLD, MathFontStyle.MATH_NORMAL}, MathFontStyle.values());
        font(new LatexAtom("x", LatexAtom.AtomClass.ORD), MathFontStyle.INHERIT);
        Assert.assertEquals("\\mathnormalUnknown",
                ((LatexAtom) LatexParser.parse("\\mathnormalUnknown").get(0)).getText());
    }

    private static LatexGroup group(LatexNode node) {
        Assert.assertTrue(node.toString(), node instanceof LatexGroup);
        return (LatexGroup) node;
    }

    private static LatexNode child(LatexNode node) {
        List<LatexNode> children = group(node).getChildren();
        Assert.assertEquals(1, children.size());
        return children.get(0);
    }

    private static LatexAtom font(LatexNode node, MathFontStyle expected) {
        Assert.assertTrue(node.toString(), node instanceof LatexAtom);
        LatexAtom atom = (LatexAtom) node;
        Assert.assertEquals(expected, atom.getMathFontStyle());
        return atom;
    }
}
