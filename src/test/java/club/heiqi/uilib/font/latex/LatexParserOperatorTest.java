package club.heiqi.uilib.font.latex;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.latex.node.LatexAtom;
import club.heiqi.uilib.font.latex.node.LatexFrac;
import club.heiqi.uilib.font.latex.node.LatexGroup;
import club.heiqi.uilib.font.latex.node.LatexLeftRight;
import club.heiqi.uilib.font.latex.node.LatexMatrix;
import club.heiqi.uilib.font.latex.node.LatexOperator;
import club.heiqi.uilib.font.latex.node.LatexSqrt;
import club.heiqi.uilib.font.latex.node.LatexSupSub;

/** 名称参数的浅层边界与 amsopn 单次 limits lookahead；不依赖字体或渲染器。 */
public class LatexParserOperatorTest {

    @Test
    public void directNameSymbolsBecomeOrdinaryAndAllPunctuationIsNormalized() {
        List<LatexNode> body = body(operator(single("\\operatorname{a--b**c\\ast+=}")));
        StringBuilder text = new StringBuilder();
        for (LatexNode child : body) {
            LatexAtom atom = atom(child);
            Assert.assertEquals(LatexAtom.AtomClass.ORD, atom.getAtomClass());
            Assert.assertEquals(MathFontStyle.UPRIGHT, atom.getMathFontStyle());
            text.append(atom.getText());
        }
        Assert.assertEquals("a--b**c*+=", text.toString());
    }

    @Test
    public void extraGroupsAndNestedStructuresKeepTheirMathClasses() {
        List<LatexNode> body = body(operator(single(
                "\\operatorname{{a-b}\\frac{c-d}{e+f}\\sqrt{g-h}}")));
        List<LatexNode> extra = group(body.get(0)).getChildren();
        Assert.assertFalse(group(body.get(0)).isTransparentForAtomClass());
        Assert.assertEquals(LatexAtom.AtomClass.BIN, atom(extra.get(1)).getAtomClass());
        LatexFrac fraction = (LatexFrac) body.get(1);
        Assert.assertEquals(LatexAtom.AtomClass.BIN,
                atom(group(fraction.getNumerator()).getChildren().get(1)).getAtomClass());
        Assert.assertEquals(MathFontStyle.UPRIGHT,
                atom(group(fraction.getDenominator()).getChildren().get(0)).getMathFontStyle());
        LatexSqrt sqrt = (LatexSqrt) body.get(2);
        Assert.assertEquals(LatexAtom.AtomClass.BIN,
                atom(group(sqrt.getRadicand()).getChildren().get(1)).getAtomClass());
    }

    @Test
    public void transparentFontArgumentChangesOnlyPunctuationText() {
        List<LatexNode> body = body(operator(single(
                "\\operatorname{a\\mathit{\\ast}b\\mathit{c\\ast d}e}")));
        LatexGroup singleFont = group(body.get(1));
        Assert.assertTrue(singleFont.isTransparentForAtomClass());
        LatexAtom star = atom(singleFont.getChildren().get(0));
        Assert.assertEquals("*", star.getText());
        Assert.assertEquals(LatexAtom.AtomClass.BIN, star.getAtomClass());
        Assert.assertEquals(MathFontStyle.ITALIC, star.getMathFontStyle());
        LatexGroup multiFont = group(body.get(3));
        Assert.assertFalse(multiFont.isTransparentForAtomClass());
        LatexAtom nestedStar = atom(multiFont.getChildren().get(1));
        Assert.assertEquals("∗", nestedStar.getText());
        Assert.assertEquals(LatexAtom.AtomClass.BIN, nestedStar.getAtomClass());
        Assert.assertEquals(MathFontStyle.UPRIGHT, atom(body.get(4)).getMathFontStyle());
    }

    @Test
    public void spacesTextAndFunctionCommandsKeepTheirOwnSemantics() {
        List<LatexNode> body = body(operator(single(
                "\\operatorname{a b\\,c\\!\\sin\\text{d e}\\unknown}")));
        Assert.assertEquals("a", atom(body.get(0)).getText());
        Assert.assertEquals("b", atom(body.get(1)).getText());
        Assert.assertEquals(LatexNode.Kind.SPACE, body.get(2).getKind());
        Assert.assertEquals(LatexNode.Kind.SPACE, body.get(4).getKind());
        Assert.assertEquals(LatexAtom.AtomClass.OP, atom(body.get(5)).getAtomClass());
        Assert.assertEquals(LatexAtom.AtomClass.TEXT, atom(body.get(6)).getAtomClass());
        Assert.assertEquals("d e", atom(body.get(6)).getText());
        Assert.assertEquals(MathFontStyle.INHERIT, atom(body.get(6)).getMathFontStyle());
        Assert.assertEquals("\\unknown", atom(body.get(7)).getText());
    }

    @Test
    public void nameFontOverridesOuterFontAndRestoresBeforeOuterScripts() {
        LatexSupSub outer = scripts(single("\\mathit{\\operatorname{a_b\\mathbf{c}d}}^e"));
        LatexOperator operator = operator(group(outer.getBase()).getChildren().get(0));
        List<LatexNode> body = body(operator);
        LatexSupSub inner = scripts(body.get(0));
        Assert.assertEquals(MathFontStyle.UPRIGHT, atom(inner.getBase()).getMathFontStyle());
        Assert.assertEquals(MathFontStyle.UPRIGHT, atom(inner.getSub()).getMathFontStyle());
        Assert.assertEquals(MathFontStyle.BOLD,
                atom(group(body.get(1)).getChildren().get(0)).getMathFontStyle());
        Assert.assertEquals(MathFontStyle.UPRIGHT, atom(body.get(2)).getMathFontStyle());
        Assert.assertEquals(MathFontStyle.INHERIT, atom(outer.getSup()).getMathFontStyle());
    }

    @Test
    public void emptyAndBareArgumentsPreserveOperatorAndScriptBinding() {
        Assert.assertTrue(body(operator(single("\\operatorname{}"))).isEmpty());
        LatexOperator missing = operator(single("\\operatorname"));
        Assert.assertTrue(group(body(missing).get(0)).getChildren().isEmpty());
        LatexSupSub bare = scripts(single("\\operatorname x_i^j"));
        Assert.assertEquals("x", atom(body(operator(bare.getBase())).get(0)).getText());
        Assert.assertEquals("i", atom(bare.getSub()).getText());
        Assert.assertEquals("j", atom(bare.getSup()).getText());
        LatexOperator starred = operator(single("\\operatorname * {}"));
        Assert.assertTrue(starred.isLimitsInDisplayStyle());
        LatexSupSub missingBeforeScript = scripts(single("\\operatorname^x"));
        Assert.assertTrue(group(body(operator(missingBeforeScript.getBase())).get(0)).getChildren().isEmpty());
    }

    @Test
    public void nonstarSwallowsExactlyOneImmediateLimits() {
        Assert.assertEquals(LatexAtom.LIMITS_DEFAULT,
                baseOperator("\\operatorname{f} \n\\limits_i").getLimitsFlag());
        Assert.assertEquals(LatexAtom.LIMITS_LIMITS,
                baseOperator("\\operatorname{f}\\limits\\limits_i").getLimitsFlag());
        Assert.assertEquals(LatexAtom.LIMITS_LIMITS,
                baseOperator("\\operatorname{f}_i\\limits").getLimitsFlag());
        Assert.assertEquals(LatexAtom.LIMITS_NOLIMITS,
                baseOperator("\\operatorname{f}\\limits\\nolimits_i").getLimitsFlag());
        Assert.assertEquals(LatexAtom.LIMITS_LIMITS,
                baseOperator("\\operatorname{f}\\nolimits\\limits_i").getLimitsFlag());
    }

    @Test
    public void starRetainsDefaultPolicyAndLastExplicitModifier() {
        LatexOperator defaultStar = baseOperator("\\operatorname*{f}_i");
        Assert.assertTrue(defaultStar.isLimitsInDisplayStyle());
        Assert.assertEquals(LatexAtom.LIMITS_DEFAULT, defaultStar.getLimitsFlag());
        Assert.assertEquals(LatexAtom.LIMITS_LIMITS,
                baseOperator("\\operatorname*{f}\\limits_i").getLimitsFlag());
        Assert.assertEquals(LatexAtom.LIMITS_NOLIMITS,
                baseOperator("\\operatorname*{f}\\limits_i\\nolimits").getLimitsFlag());
        LatexSupSub wrapped = scripts(single("\\mathrm{\\operatorname*{f}}\\nolimits_i"));
        Assert.assertEquals(LatexAtom.LIMITS_NOLIMITS,
                operator(group(wrapped.getBase()).getChildren().get(0)).getLimitsFlag());
        LatexSupSub explicitGroup = scripts(single("{\\operatorname*{f}}\\nolimits_i"));
        Assert.assertEquals(LatexAtom.LIMITS_DEFAULT,
                operator(group(explicitGroup.getBase()).getChildren().get(0)).getLimitsFlag());
    }

    @Test
    public void outerAndInnerSizeDeclarationsRemainSeparate() {
        LatexOperator operator = operator(single(
                "\\scriptstyle\\operatorname*{\\displaystyle f}\\nolimits"));
        Assert.assertEquals(MathStyleOverride.SCRIPT, operator.getMathStyleOverride());
        Assert.assertEquals(MathStyleOverride.DISPLAY, body(operator).get(0).getMathStyleOverride());
        Assert.assertEquals(LatexAtom.LIMITS_NOLIMITS, operator.getLimitsFlag());
        Assert.assertTrue(operator.isLimitsInDisplayStyle());
        LatexOperator bareStyle = operator(single("\\scriptstyle\\operatorname\\displaystyle f"));
        Assert.assertEquals(MathStyleOverride.SCRIPT, bareStyle.getMathStyleOverride());
        Assert.assertEquals(MathStyleOverride.DISPLAY, bareStyle.getBody().getMathStyleOverride());
    }

    @Test
    public void missingArgumentsDoNotConsumeMatrixOrFenceTerminators() {
        LatexMatrix matrix = (LatexMatrix) single("\\begin{matrix}\\operatorname&x\\\\y&z\\end{matrix}");
        Assert.assertEquals(2, matrix.getRows().size());
        Assert.assertEquals(2, matrix.getRows().get(0).size());
        Assert.assertEquals(LatexNode.Kind.OPERATOR, matrix.getRows().get(0).get(0).get(0).getKind());
        Assert.assertEquals("x", atom(matrix.getRows().get(0).get(1).get(0)).getText());
        LatexLeftRight fence = (LatexLeftRight) single("\\left(\\operatorname\\right)");
        Assert.assertEquals(LatexNode.Kind.OPERATOR, group(fence.getParts().get(0)).getChildren().get(0).getKind());
        Assert.assertEquals(")", fence.getRightDelimiter());
    }

    @Test
    public void commandBoundariesAndExistingOperatorModesRemainUnchanged() {
        Assert.assertEquals("\\operatornamefoo", atom(single("\\operatornamefoo")).getText());
        Assert.assertEquals("\\mathbfUnknown", atom(single("\\mathbfUnknown")).getText());
        Assert.assertEquals("\\mathnormalUnknown", atom(single("\\mathnormalUnknown")).getText());
        Assert.assertEquals(LatexAtom.OperatorMode.LIMITS_OPERATOR, atom(scripts(single("\\lim_i")).getBase()).getOperatorMode());
        Assert.assertEquals(LatexAtom.OperatorMode.BIG_OPERATOR, atom(scripts(single("\\sum_i")).getBase()).getOperatorMode());
        List<LatexNode> twice = LatexParser.parse("\\operatorname**{x}");
        Assert.assertTrue(operator(twice.get(0)).isLimitsInDisplayStyle());
        Assert.assertEquals("*", atom(body(operator(twice.get(0))).get(0)).getText());
        Assert.assertEquals(LatexNode.Kind.GROUP, twice.get(1).getKind());
    }

    @Test
    public void operatorPublicDefaultsKeepBodyAndRejectInvalidInput() {
        LatexNode empty = new LatexGroup(null);
        LatexOperator operator = new LatexOperator(empty, false);
        Assert.assertSame(empty, operator.getBody());
        Assert.assertEquals(LatexNode.Kind.OPERATOR, operator.getKind());
        Assert.assertEquals(MathStyleOverride.INHERIT, operator.getMathStyleOverride());
        Assert.assertEquals(LatexAtom.LIMITS_DEFAULT, operator.getLimitsFlag());
        try {
            new LatexOperator(null, false);
            Assert.fail("null body");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("body"));
        }
        try {
            operator.setLimitsFlag(-1);
            Assert.fail("invalid limits");
        } catch (IllegalArgumentException expected) {
            Assert.assertEquals(LatexAtom.LIMITS_DEFAULT, operator.getLimitsFlag());
        }
    }

    private static LatexNode single(String source) {
        List<LatexNode> nodes = LatexParser.parse(source);
        Assert.assertEquals(source, 1, nodes.size());
        return nodes.get(0);
    }

    private static LatexOperator baseOperator(String source) {
        return operator(scripts(single(source)).getBase());
    }

    private static List<LatexNode> body(LatexOperator operator) {
        return group(operator.getBody()).getChildren();
    }

    private static LatexOperator operator(LatexNode node) {
        Assert.assertTrue(node.toString(), node instanceof LatexOperator);
        return (LatexOperator) node;
    }

    private static LatexGroup group(LatexNode node) {
        Assert.assertTrue(node.toString(), node instanceof LatexGroup);
        return (LatexGroup) node;
    }

    private static LatexAtom atom(LatexNode node) {
        Assert.assertTrue(node.toString(), node instanceof LatexAtom);
        return (LatexAtom) node;
    }

    private static LatexSupSub scripts(LatexNode node) {
        Assert.assertTrue(node.toString(), node instanceof LatexSupSub);
        return (LatexSupSub) node;
    }
}
