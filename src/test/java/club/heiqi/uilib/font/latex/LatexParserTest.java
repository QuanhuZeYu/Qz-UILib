package club.heiqi.uilib.font.latex;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.latex.node.LatexAccent;
import club.heiqi.uilib.font.latex.node.LatexAtom;
import club.heiqi.uilib.font.latex.node.LatexAtom.AtomClass;
import club.heiqi.uilib.font.latex.node.LatexBinom;
import club.heiqi.uilib.font.latex.node.LatexFrac;
import club.heiqi.uilib.font.latex.node.LatexGroup;
import club.heiqi.uilib.font.latex.node.LatexLeftRight;
import club.heiqi.uilib.font.latex.node.LatexMatrix;
import club.heiqi.uilib.font.latex.node.LatexMatrix.Fence;
import club.heiqi.uilib.font.latex.node.LatexSpace;
import club.heiqi.uilib.font.latex.node.LatexSqrt;
import club.heiqi.uilib.font.latex.node.LatexSupSub;

/**
 * {@link LatexParser} M1 解析测试：AST 形状、命令映射、容错恢复。
 */
public class LatexParserTest {

    // ==================== 基本原子与符号映射 ====================

    @Test
    public void shouldParseSimpleAtoms() {
        List<LatexNode> nodes = LatexParser.parse("a+b");
        Assert.assertEquals(3, nodes.size());
        assertAtom(nodes.get(0), "a", AtomClass.ORD);
        assertAtom(nodes.get(1), "+", AtomClass.BIN);
        assertAtom(nodes.get(2), "b", AtomClass.ORD);
    }

    @Test
    public void shouldMapGreekSymbols() {
        List<LatexNode> nodes = LatexParser.parse("\\alpha\\beta\\Gamma");
        Assert.assertEquals(3, nodes.size());
        assertAtom(nodes.get(0), "\u03B1", AtomClass.ORD);
        assertAtom(nodes.get(1), "\u03B2", AtomClass.ORD);
        assertAtom(nodes.get(2), "\u0393", AtomClass.ORD);
    }

    @Test
    public void shouldMapGreekVariantPairs() {
        List<LatexNode> nodes = LatexParser.parse("\\epsilon\\varepsilon\\phi\\varphi\\theta\\vartheta");
        Assert.assertEquals(6, nodes.size());
        assertAtom(nodes.get(0), "\u03F5", AtomClass.ORD);
        assertAtom(nodes.get(1), "\u03B5", AtomClass.ORD);
        assertAtom(nodes.get(2), "\u03D5", AtomClass.ORD);
        assertAtom(nodes.get(3), "\u03C6", AtomClass.ORD);
        assertAtom(nodes.get(4), "\u03B8", AtomClass.ORD);
        assertAtom(nodes.get(5), "\u03D1", AtomClass.ORD);
    }

    @Test
    public void shouldMapOperatorsAndRelations() {
        List<LatexNode> nodes = LatexParser.parse("\\pm\\times\\leq\\neq\\in\\infty\\sum\\int");
        Assert.assertEquals(8, nodes.size());
        assertAtom(nodes.get(0), "\u00B1", AtomClass.BIN);
        assertAtom(nodes.get(1), "\u00D7", AtomClass.BIN);
        assertAtom(nodes.get(2), "\u2264", AtomClass.REL);
        assertAtom(nodes.get(3), "\u2260", AtomClass.REL);
        assertAtom(nodes.get(4), "\u2208", AtomClass.REL);
        assertAtom(nodes.get(5), "\u221E", AtomClass.ORD);
        assertAtom(nodes.get(6), "\u2211", AtomClass.OP);
        assertAtom(nodes.get(7), "\u222B", AtomClass.OP);
    }

    // ==================== 上下标与分组 ====================

    @Test
    public void shouldParseSuperscriptAndSubscript() {
        List<LatexNode> nodes = LatexParser.parse("x^2");
        LatexSupSub supSub = asKind(nodes.get(0), LatexSupSub.class);
        assertAtom(supSub.getBase(), "x", AtomClass.ORD);
        assertAtom(supSub.getSup(), "2", AtomClass.ORD);
        Assert.assertNull(supSub.getSub());

        nodes = LatexParser.parse("x_i");
        supSub = asKind(nodes.get(0), LatexSupSub.class);
        Assert.assertNull(supSub.getSup());
        assertAtom(supSub.getSub(), "i", AtomClass.ORD);

        nodes = LatexParser.parse("x_i^2");
        supSub = asKind(nodes.get(0), LatexSupSub.class);
        assertAtom(supSub.getSub(), "i", AtomClass.ORD);
        assertAtom(supSub.getSup(), "2", AtomClass.ORD);
    }

    @Test
    public void shouldParseGroupedSupSub() {
        List<LatexNode> nodes = LatexParser.parse("x^{i+1}");
        LatexSupSub supSub = asKind(nodes.get(0), LatexSupSub.class);
        LatexGroup group = asKind(supSub.getSup(), LatexGroup.class);
        Assert.assertEquals(3, group.getChildren().size());
    }

    @Test
    public void shouldParseGrouping() {
        List<LatexNode> nodes = LatexParser.parse("{a+b}c");
        Assert.assertEquals(2, nodes.size());
        LatexGroup group = asKind(nodes.get(0), LatexGroup.class);
        Assert.assertEquals(3, group.getChildren().size());
        assertAtom(nodes.get(1), "c", AtomClass.ORD);
    }

    // ==================== 分数与根号 ====================

    @Test
    public void shouldParseFrac() {
        List<LatexNode> nodes = LatexParser.parse("\\frac{a}{b}");
        LatexFrac frac = asKind(nodes.get(0), LatexFrac.class);
        LatexGroup num = asKind(frac.getNumerator(), LatexGroup.class);
        LatexGroup den = asKind(frac.getDenominator(), LatexGroup.class);
        Assert.assertEquals(1, num.getChildren().size());
        Assert.assertEquals(1, den.getChildren().size());
    }

    @Test
    public void shouldParseFracWithBareArgs() {
        List<LatexNode> nodes = LatexParser.parse("\\frac12");
        LatexFrac frac = asKind(nodes.get(0), LatexFrac.class);
        assertAtom(frac.getNumerator(), "1", AtomClass.ORD);
        assertAtom(frac.getDenominator(), "2", AtomClass.ORD);
    }

    @Test
    public void shouldParseSqrt() {
        List<LatexNode> nodes = LatexParser.parse("\\sqrt{x}");
        LatexSqrt sqrt = asKind(nodes.get(0), LatexSqrt.class);
        Assert.assertNull(sqrt.getIndex());
        Assert.assertEquals(1, asKind(sqrt.getRadicand(), LatexGroup.class).getChildren().size());

        nodes = LatexParser.parse("\\sqrt[3]{x}");
        sqrt = asKind(nodes.get(0), LatexSqrt.class);
        LatexGroup index = asKind(sqrt.getIndex(), LatexGroup.class);
        Assert.assertEquals(1, index.getChildren().size());
    }

    // ==================== 伸缩括号 ====================

    @Test
    public void shouldParseLeftRight() {
        List<LatexNode> nodes = LatexParser.parse("\\left(\\frac{a}{b}\\right)");
        LatexLeftRight lr = asKind(nodes.get(0), LatexLeftRight.class);
        Assert.assertEquals("(", lr.getLeftDelimiter());
        Assert.assertEquals(")", lr.getRightDelimiter());
        asKind(lr.getContent(), LatexGroup.class);
    }

    @Test
    public void shouldParseInvisibleDelimiter() {
        List<LatexNode> nodes = LatexParser.parse("\\left. x \\right|");
        LatexLeftRight lr = asKind(nodes.get(0), LatexLeftRight.class);
        Assert.assertNull(lr.getLeftDelimiter());
        Assert.assertEquals("|", lr.getRightDelimiter());
    }

    @Test
    public void shouldParseNestedLeftRight() {
        List<LatexNode> nodes = LatexParser.parse("\\left( \\left[ x \\right] \\right)");
        LatexLeftRight outer = asKind(nodes.get(0), LatexLeftRight.class);
        LatexGroup content = asKind(outer.getContent(), LatexGroup.class);
        Assert.assertEquals(1, content.getChildren().size());
        LatexLeftRight inner = asKind(content.getChildren().get(0), LatexLeftRight.class);
        Assert.assertEquals("[", inner.getLeftDelimiter());
        Assert.assertEquals("]", inner.getRightDelimiter());
    }

    // ==================== 大运算符与函数名 ====================

    @Test
    public void shouldParseBigOperatorWithLimits() {
        List<LatexNode> nodes = LatexParser.parse("\\sum_{i=1}^{n}");
        LatexSupSub supSub = asKind(nodes.get(0), LatexSupSub.class);
        assertAtom(supSub.getBase(), "\u2211", AtomClass.OP);
        Assert.assertEquals(3, asKind(supSub.getSub(), LatexGroup.class).getChildren().size());
        Assert.assertEquals(1, asKind(supSub.getSup(), LatexGroup.class).getChildren().size());

        nodes = LatexParser.parse("\\int_0^\\infty");
        supSub = asKind(nodes.get(0), LatexSupSub.class);
        assertAtom(supSub.getBase(), "\u222B", AtomClass.OP);
        assertAtom(supSub.getSub(), "0", AtomClass.ORD);
        assertAtom(supSub.getSup(), "\u221E", AtomClass.ORD);
    }

    @Test
    public void shouldParseFunctionNames() {
        List<LatexNode> nodes = LatexParser.parse("\\sin x");
        Assert.assertEquals(2, nodes.size());
        assertAtom(nodes.get(0), "sin", AtomClass.OP);
        assertAtom(nodes.get(1), "x", AtomClass.ORD);

        nodes = LatexParser.parse("\\lim_{x\\to0}");
        LatexSupSub supSub = asKind(nodes.get(0), LatexSupSub.class);
        assertAtom(supSub.getBase(), "lim", AtomClass.OP);
        Assert.assertEquals(3, asKind(supSub.getSub(), LatexGroup.class).getChildren().size());
    }

    // ==================== 矩阵环境 ====================

    @Test
    public void shouldParseMatrix() {
        List<LatexNode> nodes = LatexParser.parse("\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}");
        LatexMatrix matrix = asKind(nodes.get(0), LatexMatrix.class);
        Assert.assertEquals(Fence.PAREN, matrix.getFence());
        Assert.assertEquals(2, matrix.getRows().size());
        Assert.assertEquals(2, matrix.getRows().get(0).size());
        Assert.assertEquals(2, matrix.getRows().get(1).size());
        assertAtom(matrix.getRows().get(0).get(0).get(0), "a", AtomClass.ORD);
        assertAtom(matrix.getRows().get(1).get(1).get(0), "d", AtomClass.ORD);
    }

    @Test
    public void shouldParseCases() {
        List<LatexNode> nodes = LatexParser.parse("\\begin{cases}x&x>0\\\\-x&x\\leq0\\end{cases}");
        LatexMatrix matrix = asKind(nodes.get(0), LatexMatrix.class);
        Assert.assertEquals(Fence.CASES, matrix.getFence());
        Assert.assertEquals(2, matrix.getRows().size());
        Assert.assertEquals(2, matrix.getRows().get(1).size());
    }

    @Test
    public void shouldParseMatrixWithCells() {
        List<LatexNode> nodes = LatexParser.parse("\\begin{matrix}1+\\frac{1}{2}&x\\end{matrix}");
        LatexMatrix matrix = asKind(nodes.get(0), LatexMatrix.class);
        Assert.assertEquals(Fence.NONE, matrix.getFence());
        Assert.assertEquals(2, matrix.getRows().get(0).size());
        Assert.assertEquals(3, matrix.getRows().get(0).get(0).size());
        asKind(matrix.getRows().get(0).get(0).get(2), LatexFrac.class);
    }

    // ==================== 组合数与重音 ====================

    @Test
    public void shouldParseBinom() {
        List<LatexNode> nodes = LatexParser.parse("\\binom{n}{k}");
        LatexBinom binom = asKind(nodes.get(0), LatexBinom.class);
        Assert.assertEquals(1, asKind(binom.getUpper(), LatexGroup.class).getChildren().size());
        Assert.assertEquals(1, asKind(binom.getLower(), LatexGroup.class).getChildren().size());
    }

    @Test
    public void shouldParseAccents() {
        List<LatexNode> nodes = LatexParser.parse("\\hat x");
        LatexAccent accent = asKind(nodes.get(0), LatexAccent.class);
        Assert.assertEquals("\u0302", accent.getAccentText());
        Assert.assertFalse(accent.isStretchable());
        assertAtom(accent.getBase(), "x", AtomClass.ORD);

        nodes = LatexParser.parse("\\overline{AB}");
        accent = asKind(nodes.get(0), LatexAccent.class);
        Assert.assertTrue(accent.isStretchable());
        Assert.assertFalse(accent.isBelow());
        asKind(accent.getBase(), LatexGroup.class);

        nodes = LatexParser.parse("\\underline{x}");
        accent = asKind(nodes.get(0), LatexAccent.class);
        Assert.assertTrue(accent.isStretchable());
        Assert.assertTrue(accent.isBelow());
    }

    // ==================== 间距与文本 ====================

    @Test
    public void shouldParseSpacing() {
        List<LatexNode> nodes = LatexParser.parse("a\\,b\\quad c");
        Assert.assertEquals(5, nodes.size());
        assertAtom(nodes.get(0), "a", AtomClass.ORD);
        Assert.assertEquals(3.0D / 18.0D, asKind(nodes.get(1), LatexSpace.class).getEmWidth(), 1e-9);
        assertAtom(nodes.get(2), "b", AtomClass.ORD);
        Assert.assertEquals(1.0D, asKind(nodes.get(3), LatexSpace.class).getEmWidth(), 1e-9);
        assertAtom(nodes.get(4), "c", AtomClass.ORD);
    }

    @Test
    public void shouldIgnoreMathSpaces() {
        List<LatexNode> nodes = LatexParser.parse("a  +  \n b");
        Assert.assertEquals(3, nodes.size());
        assertAtom(nodes.get(0), "a", AtomClass.ORD);
        assertAtom(nodes.get(1), "+", AtomClass.BIN);
        assertAtom(nodes.get(2), "b", AtomClass.ORD);
    }

    @Test
    public void shouldPreserveTextContent() {
        List<LatexNode> nodes = LatexParser.parse("\\text{a b 中文}");
        assertAtom(nodes.get(0), "a b \u4E2D\u6587", AtomClass.TEXT);

        nodes = LatexParser.parse("\\text{a\\%b\\}}");
        assertAtom(nodes.get(0), "a%b}", AtomClass.TEXT);

        nodes = LatexParser.parse("\\text{a\\ b}");
        assertAtom(nodes.get(0), "a b", AtomClass.TEXT);
    }

    @Test
    public void shouldEscapeBracesInMathMode() {
        List<LatexNode> nodes = LatexParser.parse("\\{a\\}");
        Assert.assertEquals(3, nodes.size());
        assertAtom(nodes.get(0), "{", AtomClass.OPEN);
        assertAtom(nodes.get(1), "a", AtomClass.ORD);
        assertAtom(nodes.get(2), "}", AtomClass.CLOSE);
    }

    // ==================== 容错 ====================

    @Test
    public void shouldKeepUnknownCommandLiteral() {
        List<LatexNode> nodes = LatexParser.parse("\\foobar");
        Assert.assertEquals(1, nodes.size());
        assertAtom(nodes.get(0), "\\foobar", AtomClass.ORD);
    }

    @Test
    public void shouldTolerateUnclosedGroup() {
        List<LatexNode> nodes = LatexParser.parse("{a+b");
        Assert.assertEquals(1, nodes.size());
        LatexGroup group = asKind(nodes.get(0), LatexGroup.class);
        Assert.assertEquals(3, group.getChildren().size());
    }

    @Test
    public void shouldTolerateOrphanSup() {
        List<LatexNode> nodes = LatexParser.parse("a^");
        Assert.assertEquals(1, nodes.size());
        assertAtom(nodes.get(0), "a", AtomClass.ORD);
    }

    @Test
    public void shouldTolerateOrphanSubAtEnd() {
        List<LatexNode> nodes = LatexParser.parse("x_");
        Assert.assertEquals(1, nodes.size());
        assertAtom(nodes.get(0), "x", AtomClass.ORD);
    }

    @Test
    public void shouldTolerateEmptyInput() {
        Assert.assertTrue(LatexParser.parse("").isEmpty());
        Assert.assertTrue(LatexParser.parse(null).isEmpty());
    }

    @Test
    public void shouldTolerateMissingFracArgs() {
        List<LatexNode> nodes = LatexParser.parse("\\frac");
        LatexFrac frac = asKind(nodes.get(0), LatexFrac.class);
        Assert.assertTrue(asKind(frac.getNumerator(), LatexGroup.class).getChildren().isEmpty());
        Assert.assertTrue(asKind(frac.getDenominator(), LatexGroup.class).getChildren().isEmpty());
    }

    @Test
    public void shouldTolerateUnclosedMatrix() {
        List<LatexNode> nodes = LatexParser.parse("\\begin{matrix}a&b");
        LatexMatrix matrix = asKind(nodes.get(0), LatexMatrix.class);
        Assert.assertEquals(Fence.NONE, matrix.getFence());
        Assert.assertEquals(1, matrix.getRows().size());
        Assert.assertEquals(2, matrix.getRows().get(0).size());
    }

    @Test
    public void shouldTolerateRightWithoutLeft() {
        List<LatexNode> nodes = LatexParser.parse("\\right) x");
        Assert.assertEquals(1, nodes.size());
        assertAtom(nodes.get(0), "x", AtomClass.ORD);
    }

    @Test
    public void shouldTolerateExtraClosingBrace() {
        List<LatexNode> nodes = LatexParser.parse("a}b");
        Assert.assertEquals(2, nodes.size());
        assertAtom(nodes.get(0), "a", AtomClass.ORD);
        assertAtom(nodes.get(1), "b", AtomClass.ORD);
    }

    @Test
    public void shouldTolerateUnknownEnvironment() {
        List<LatexNode> nodes = LatexParser.parse("\\begin{foo}a\\end{foo}");
        LatexMatrix matrix = asKind(nodes.get(0), LatexMatrix.class);
        Assert.assertEquals(Fence.NONE, matrix.getFence());
        Assert.assertEquals(1, matrix.getRows().size());
    }

    @Test
    public void shouldParseSupOnCommand() {
        List<LatexNode> nodes = LatexParser.parse("x^\\frac12");
        LatexSupSub supSub = asKind(nodes.get(0), LatexSupSub.class);
        asKind(supSub.getSup(), LatexFrac.class);
    }

    // ==================== 断言辅助 ====================

    private static void assertAtom(LatexNode node, String text, AtomClass atomClass) {
        Assert.assertNotNull("节点不能为空", node);
        LatexAtom atom = asKind(node, LatexAtom.class);
        Assert.assertEquals("文本不符: " + atom, text, atom.getText());
        Assert.assertEquals("类别不符: " + atom, atomClass, atom.getAtomClass());
    }

    @SuppressWarnings("unchecked")
    private static <T extends LatexNode> T asKind(LatexNode node, Class<T> type) {
        Assert.assertNotNull("节点不能为空", node);
        Assert.assertTrue("节点类型不符: " + node + "（期望 " + type.getSimpleName() + "）", type.isInstance(node));
        return (T) node;
    }
}
