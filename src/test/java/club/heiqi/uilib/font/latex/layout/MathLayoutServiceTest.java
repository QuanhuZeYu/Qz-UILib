package club.heiqi.uilib.font.latex.layout;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.LatexParser;

/**
 * {@link MathLayoutService} M2 布局测试：盒尺寸/元素偏移/字号缩放/间距规则。
 *
 * <p>度量注入 fake：每码点 advance = 0.5em，ascent = 0.8em，descent = 0.2em
 * （em = 正文字号 size）。所有断言基于此模型的精确几何值。</p>
 */
public class MathLayoutServiceTest {

    /** fake 度量：advance=0.5em/码点，ascent=0.8em，descent=0.2em。 */
    private static final MathMetrics METRICS = new MathMetrics() {
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

        @Override
        public float italicCorrection(String text, float sizePx) {
            return 0.25F * xHeight(sizePx);
        }
    };

    private static final MathLayoutService SERVICE = new MathLayoutService();

    private static final float S = 10.0F; // 正文字号（px）
    private static final float EPS = 1e-4F;

    private static MathBox layout(String latex) {
        List<LatexNode> nodes = LatexParser.parse(latex);
        return SERVICE.layout(nodes, S, METRICS);
    }

    // ==================== 原子与列表 ====================

    @Test
    public void shouldLayoutAtom() {
        MathBox box = layout("x");
        Assert.assertEquals(0.5F * S, box.getWidth(), EPS);
        Assert.assertEquals(0.8F * S, box.getHeight(), EPS);
        Assert.assertEquals(0.2F * S, box.getDepth(), EPS);
        Assert.assertEquals(1, box.getGlyphs().size());
        Assert.assertEquals("x", box.getGlyphs().get(0).getText());
        Assert.assertEquals(0.0F, box.getGlyphs().get(0).getY(), EPS);
        Assert.assertEquals(1.0F, box.getGlyphs().get(0).getSizeScale(), EPS);
        // TeX mathnormal：ORD 类 ASCII 字母为数学变量（斜体）
        Assert.assertTrue("数学变量应斜体", box.getGlyphs().get(0).isItalic());
    }

    @Test
    public void shouldKeepDigitsAndOperatorsUpright() {
        // 数字（ORD）与函数名（OP）不斜体，变量斜体
        MathBox digits = layout("12");
        for (GlyphElem glyph : digits.getGlyphs()) {
            Assert.assertFalse("数字应直体", glyph.isItalic());
        }
        MathBox func = layout("\\sin x");
        for (GlyphElem glyph : func.getGlyphs()) {
            if ("sin".equals(glyph.getText())) {
                Assert.assertFalse("函数名应直体", glyph.isItalic());
            }
            if ("x".equals(glyph.getText())) {
                Assert.assertTrue("函数后变量应斜体", glyph.isItalic());
            }
        }
    }

    @Test
    public void shouldApplyBinSpacing() {
        // a+b：ORD BIN ORD → 两侧 med 间距（4mu = 4/18 em）
        MathBox box = layout("a+b");
        Assert.assertEquals(0.5F * S + (4.0F / 18.0F) * S + 0.5F * S + (4.0F / 18.0F) * S + 0.5F * S,
                box.getWidth(), EPS);
    }

    @Test
    public void shouldApplyRelSpacing() {
        // a=b：ORD REL ORD → 两侧 thick 间距（5mu）
        MathBox box = layout("a=b");
        Assert.assertEquals(0.5F * S + (5.0F / 18.0F) * S + 0.5F * S + (5.0F / 18.0F) * S + 0.5F * S,
                box.getWidth(), EPS);
    }

    @Test
    public void shouldApplyExplicitSpace() {
        MathBox box = layout("a\\quad b");
        Assert.assertEquals(0.5F * S + 1.0F * S + 0.5F * S, box.getWidth(), EPS);
    }

    // ==================== 上下标 ====================

    @Test
    public void shouldLayoutSuperscript() {
        MathBox box = layout("x^2");
        // 单字符基底（CharSymbol 路径）：shiftUp 起点 0，上标抬升 = sup2（下限 depth+xHeight/4 不生效）
        float xHeight = 0.45F * S;
        float supDepth = 0.14F * S;
        float shiftUp = Math.max(Math.max(0.0F, MathConstants.SUP2_EM * S), supDepth + xHeight / 4.0F);
        float expectedSupY = -shiftUp;
        GlyphElem supGlyph = box.getGlyphs().get(1);
        Assert.assertEquals("2", supGlyph.getText());
        Assert.assertEquals(expectedSupY, supGlyph.getY(), EPS);
        Assert.assertEquals(0.7F, supGlyph.getSizeScale(), EPS);
        // 斜体校正：单字符变量基底时上标右移 italicCorrection（0.25×xHeight = 0.1125S）
        float italicCorrection = 0.25F * 0.45F * S;
        Assert.assertEquals(0.5F * S + MathConstants.SCRIPT_SPACE_EM * S + italicCorrection, supGlyph.getX(), EPS);
        Assert.assertEquals(0.5F * S + MathConstants.SCRIPT_SPACE_EM * S + 0.35F * S + italicCorrection,
                box.getWidth(), EPS);
        Assert.assertEquals(0.56F * S - expectedSupY, box.getHeight(), EPS);
    }

    @Test
    public void shouldUseCrampedSupInsideSqrt() {
        // 根式内是 cramped style：上标抬升用 sup3（路径覆盖 + 值锚定）
        MathBox box = layout("\\sqrt{x^2}");
        float supDepth = 0.14F * S;
        float xHeight = 0.45F * S;
        float expectedSupY = -Math.max(Math.max(0.0F, MathConstants.SUP3_EM * S),
                supDepth + xHeight / 4.0F);
        boolean found = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("2".equals(glyph.getText())) {
                Assert.assertEquals(expectedSupY, glyph.getY(), EPS);
                found = true;
            }
        }
        Assert.assertTrue("根式内应包含上标 2", found);
    }

    @Test
    public void shouldLayoutSubscript() {
        MathBox box = layout("x_i");
        // 单字符基底：shiftDown 起点 0，下限 max(sub1, sub.h − 4·xHeight/5)
        float subHeight = 0.56F * S;
        float xHeight = 0.45F * S;
        float shiftDown = Math.max(Math.max(0.0F, MathConstants.SUB1_EM * S),
                subHeight - 4.0F * xHeight / 5.0F);
        GlyphElem subGlyph = box.getGlyphs().get(1);
        Assert.assertEquals("i", subGlyph.getText());
        Assert.assertEquals(shiftDown, subGlyph.getY(), EPS);
        Assert.assertEquals(0.7F, subGlyph.getSizeScale(), EPS);
        Assert.assertEquals(0.5F * S + MathConstants.SCRIPT_SPACE_EM * S + 0.35F * S, box.getWidth(), EPS);
        Assert.assertEquals(shiftDown + 0.14F * S, box.getDepth(), EPS);
    }

    @Test
    public void shouldPlaceBigOperatorScriptsBeside() {
        // \sum 在 text 口径：符号轴居中 + 上下标侧挂（SCRIPT_NORMAL），不再是上下堆叠
        MathBox box = layout("\\sum_{i=1}^{n}");
        // sub = i=1：3 码点 × 0.35S + 2 个 thick 间距（5/18 × 0.7S）；sup = n：0.35S；base = ∑：0.5S
        float subWidth = 3.0F * 0.35F * S + 2.0F * (MathConstants.THICK_MU / 18.0F) * 0.7F * S;
        // 轴居中：centerUp=(0.8S−0.2S)/2=0.3S，axis=0.25S → baseShift=0.05S；refH=0.85S refD=0.15S
        float baseShift = 0.3F * S - MathConstants.AXIS_HEIGHT_EM * S;
        float refHeight = 0.8F * S + baseShift;
        float refDepth = 0.2F * S - baseShift;
        // supDrop/subDrop 按 script 缩放
        float supDrop = MathConstants.SUP_DROP_EM * MathConstants.SCRIPT_SCALE * S;
        float subDrop = MathConstants.SUB_DROP_EM * MathConstants.SCRIPT_SCALE * S;
        float drt = MathConstants.RULE_THICKNESS_EM * S;
        float xHeight = 0.45F * S;
        float shiftUp = Math.max(Math.max(refHeight - supDrop, MathConstants.SUP2_EM * S),
                0.14F * S + xHeight / 4.0F);
        float shiftDown = Math.max(refDepth + subDrop, MathConstants.SUB2_EM * S);
        float interSpace = shiftUp - 0.14F * S + shiftDown - 0.56F * S;
        if (interSpace < 4.0F * drt) {
            shiftUp += 4.0F * drt - interSpace;
        }
        float supY = -shiftUp;
        float subY = shiftDown;
        boolean foundSup = false;
        boolean foundSub = false;
        boolean foundBase = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("n".equals(glyph.getText())) {
                Assert.assertEquals(supY, glyph.getY(), EPS);
                Assert.assertEquals(0.5F * S + MathConstants.SCRIPT_SPACE_EM * S, glyph.getX(), EPS);
                foundSup = true;
            }
            if ("i".equals(glyph.getText())) {
                Assert.assertEquals(subY, glyph.getY(), EPS);
                Assert.assertEquals(0.5F * S + MathConstants.SCRIPT_SPACE_EM * S, glyph.getX(), EPS);
                foundSub = true;
            }
            if ("\u2211".equals(glyph.getText())) {
                Assert.assertEquals(-baseShift, glyph.getY(), EPS);
                Assert.assertEquals(0.0F, glyph.getX(), EPS);
                foundBase = true;
            }
        }
        Assert.assertTrue("应包含上标 n", foundSup);
        Assert.assertTrue("应包含下标 i", foundSub);
        Assert.assertTrue("应包含轴居中的 ∑", foundBase);
        Assert.assertEquals(0.5F * S + MathConstants.SCRIPT_SPACE_EM * S + subWidth, box.getWidth(), EPS);
    }

    @Test
    public void shouldStackLimitsForLimOperator() {
        MathBox box = layout("\\lim_{x}");
        // \lim = limits 算子：恒上下堆叠（BigOperatorAtom limits 路径，下隙 max(bigop2, bigop4 − sub.h)）
        float subHeight = 0.56F * S;
        float underKern = Math.max(MathConstants.BIGOP2_EM * S,
                MathConstants.BIGOP4_EM * S - subHeight);
        float subY = 0.2F * S + underKern + subHeight;
        float expectedDepth = 0.7F * S + underKern + 0.2F * S + MathConstants.BIGOP5_EM * S;
        boolean foundSub = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("x".equals(glyph.getText())) {
                Assert.assertEquals(subY, glyph.getY(), EPS);
                Assert.assertEquals((1.5F * S - 0.35F * S) / 2.0F, glyph.getX(), EPS);
                foundSub = true;
            }
        }
        Assert.assertTrue("\\lim 应包含下方 x", foundSub);
        Assert.assertEquals(0.8F * S, box.getHeight(), EPS);
        Assert.assertEquals(expectedDepth, box.getDepth(), EPS);
    }

    // ==================== 分数 ====================

    @Test
    public void shouldLayoutFrac() {
        MathBox box = layout("\\frac{a}{b}");
        // TeX 轴高对齐（行内=text 口径）：num2/denom2 + clr 补足；分子分母 0.7×（宽 0.35S、高 0.56S、深 0.14S）
        float drt = MathConstants.RULE_THICKNESS_EM * S;
        float axis = MathConstants.AXIS_HEIGHT_EM * S;
        float delta = drt / 2.0F;
        float scriptW = 0.35F * S;
        float sideSpace = MathConstants.NULL_DELIMITER_SPACE_EM * S;
        float expectedWidth = scriptW + 2.0F * sideSpace;
        Assert.assertEquals(expectedWidth, box.getWidth(), EPS);
        Assert.assertEquals(1, box.getRules().size());
        RuleElem bar = box.getRules().get(0);
        // bar 中心落在数学轴上：顶 = -(axis + drt/2)
        Assert.assertEquals(-(axis + delta), bar.getY(), EPS);
        Assert.assertEquals(scriptW, bar.getWidth(), EPS);
        Assert.assertEquals(drt, bar.getThickness(), EPS);
        Assert.assertEquals(sideSpace, bar.getX(), EPS);
        // kern1/kern2 补足到 clr：numY = -(axis + delta + kern1 + num.depth)
        float clr = drt;
        float kern1 = Math.max(clr, MathConstants.NUM2_EM * S - 0.14F * S - (axis + delta));
        float kern2 = Math.max(clr, axis - delta - (0.56F * S - MathConstants.DENOM2_EM * S));
        float numY = -(axis + delta + kern1 + 0.14F * S);
        float denY = -axis + delta + kern2 + 0.56F * S;
        boolean foundNum = false;
        boolean foundDen = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("a".equals(glyph.getText())) {
                Assert.assertEquals(numY, glyph.getY(), EPS);
                Assert.assertEquals(sideSpace, glyph.getX(), EPS);
                Assert.assertEquals(0.7F, glyph.getSizeScale(), EPS);
                foundNum = true;
            }
            if ("b".equals(glyph.getText())) {
                Assert.assertEquals(denY, glyph.getY(), EPS);
                foundDen = true;
            }
        }
        Assert.assertTrue(foundNum);
        Assert.assertTrue(foundDen);
        Assert.assertEquals(-(numY - 0.56F * S), box.getHeight(), EPS);
        Assert.assertEquals(denY + 0.14F * S, box.getDepth(), EPS);
    }

    /** 斜体越量度量：变量字形 ink 右超 0.1S（几何斜切抽象）。 */
    private static final MathMetrics ITALIC_METRICS = new MathMetrics() {
        @Override
        public float advance(String text, float sizePx) {
            return METRICS.advance(text, sizePx);
        }

        @Override
        public float ascent(float sizePx) {
            return METRICS.ascent(sizePx);
        }

        @Override
        public float descent(float sizePx) {
            return METRICS.descent(sizePx);
        }

        @Override
        public float xHeight(float sizePx) {
            return METRICS.xHeight(sizePx);
        }

        @Override
        public float italicOverhang(String text, float sizePx) {
            return 0.1F * sizePx;
        }
    };

    @Test
    public void shouldExtendFracBarOverItalicInk() {
        // 换元抽象：盒携带 ink 越量（右超 0.1S）向上嵌套，横线右端外扩覆盖视觉墨水
        MathBox box = SERVICE.layout(LatexParser.parse("\\frac{a}{b}"), S, ITALIC_METRICS);
        Assert.assertEquals(1, box.getRules().size());
        RuleElem bar = box.getRules().get(0);
        float sideSpace = MathConstants.NULL_DELIMITER_SPACE_EM * S;
        Assert.assertEquals(sideSpace, bar.getX(), EPS);
        // 越量按字形自身字号（script 0.7S）计算：0.1 × 0.7S
        Assert.assertEquals(0.35F * S + 0.1F * 0.7F * S, bar.getWidth(), EPS);
        // 盒宽保持排版口径（0.35S + 2×sideSpace），越量只外扩横线不撑盒
        Assert.assertEquals(0.35F * S + 2.0F * sideSpace, box.getWidth(), EPS);
        // 横线已把斜体 ink 覆盖进盒内（bar 右端 ≤ 盒右界），分数盒自身无右越量
        Assert.assertEquals(0.0F, box.getRightInkOverhang(), EPS);
    }

    // ==================== 根号 ====================

    @Test
    public void shouldLayoutSqrt() {
        MathBox box = layout("\\sqrt{x}");
        // TeX NthRoot（text 口径）：clr = θ + θ/4 恒定；横线中心 = 内容顶 + clr + θ/2——
        // 只依赖内容几何与 TeX 常数，不做 delta/2 补偿（横线位置约束锚定）
        float drt = MathConstants.RULE_THICKNESS_EM * S;
        float clr = drt + drt / 4.0F;
        float barTop = 0.8F * S + clr + drt;
        float totalH = 1.0F * S + clr + drt;
        float radicalScale = Math.max(1.0F, totalH / (1.0F * S));
        float radicalAscent = 0.8F * radicalScale * S;
        float radicalDescent = 0.2F * radicalScale * S;
        float radicalY = barTop - radicalAscent;
        float radicalWidth = 0.5F * radicalScale * S;
        float mu = S / 18.0F;
        Assert.assertEquals(1, box.getRules().size());
        RuleElem rule = box.getRules().get(0);
        Assert.assertEquals(-(barTop - drt / 2.0F), rule.getY(), EPS);
        Assert.assertEquals(radicalWidth, rule.getX(), EPS);
        // 横线右端 = 内容宽 + 1mu
        Assert.assertEquals(0.5F * S + mu, rule.getWidth(), EPS);
        Assert.assertEquals(radicalWidth + 0.5F * S, box.getWidth(), EPS);
        Assert.assertEquals(barTop, box.getHeight(), EPS);
        Assert.assertEquals(Math.max(0.2F * S, radicalY + radicalDescent), box.getDepth(), EPS);
        boolean foundRadical = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("\u221A".equals(glyph.getText())) {
                Assert.assertEquals(radicalScale, glyph.getSizeScale(), EPS);
                Assert.assertEquals(radicalY, glyph.getY(), EPS);
                foundRadical = true;
            }
        }
        Assert.assertTrue("应包含根号字形", foundRadical);
    }

    @Test
    public void shouldLayoutSqrtWithIndex() {
        MathBox box = layout("\\sqrt[3]{x}");
        float drt = MathConstants.RULE_THICKNESS_EM * S;
        float clr = drt + drt / 4.0F;
        float totalH = 1.0F * S + clr + drt;
        float radicalScale = Math.max(1.0F, totalH / (1.0F * S));
        float radicalDescent = 0.2F * radicalScale * S;
        float barTop = 0.8F * S + clr + drt;
        float radicalY = barTop - 0.8F * radicalScale * S;
        float height = barTop;
        float depth = Math.max(0.2F * S, radicalY + radicalDescent);
        // TeX NthRoot：指数基线 = sqrtBox.depth − index.depth − 0.55×(sqrtBox 总高)
        float indexY = depth - 0.14F * S - MathConstants.SQRT_INDEX_FACTOR * (height + depth);
        // 水平：10mu 负 kern，index 左缘 = max(0, 10mu − index 宽)
        float indexLeft = Math.max(0.0F, MathConstants.SQRT_INDEX_NEG_KERN_MU * S / 18.0F - 0.35F * S);
        boolean foundIndex = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("3".equals(glyph.getText())) {
                Assert.assertEquals(0.7F, glyph.getSizeScale(), EPS);
                Assert.assertEquals(indexY, glyph.getY(), EPS);
                Assert.assertEquals(indexLeft, glyph.getX(), EPS);
                foundIndex = true;
            }
        }
        Assert.assertTrue("应包含根指数 3", foundIndex);
    }

    // ==================== 伸缩括号 ====================

    @Test
    public void shouldLayoutLeftRight() {
        MathBox box = layout("\\left(x\\right)");
        // TeX FencedAtom：minHeight = max(δ×901/500, 2δ − 5pt)；定界符轴居中；两侧 glue 用原子间距表（ORD→0）
        float axis = MathConstants.AXIS_HEIGHT_EM * S;
        float delta = Math.max(0.8F * S - axis, 0.2F * S + axis);
        float minHeight = Math.max(delta * MathConstants.DELIMITER_FACTOR,
                2.0F * delta - MathConstants.DELIMITER_SHORTFALL_EM * S);
        // TeX 阶梯语义：定界符不小于自然尺寸（scale 下限 1.0）
        float scale = Math.max(1.0F, minHeight / (1.0F * S));
        float delimWidth = 0.5F * scale * S;
        Assert.assertEquals(delimWidth + 0.5F * S + delimWidth, box.getWidth(), EPS);
        boolean foundLeft = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("(".equals(glyph.getText())) {
                Assert.assertEquals(scale, glyph.getSizeScale(), EPS);
                foundLeft = true;
            }
        }
        Assert.assertTrue(foundLeft);
    }

    // ==================== 矩阵 ====================

    @Test
    public void shouldLayoutMatrixGrid() {
        MathBox box = layout("\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}");
        // TeX MatrixAtom：列间 1em、行间 1ex、外沿 0.4ex；定界符 FencedAtom minHeight + 轴居中
        float ex = 0.45F * S;
        float colSep = MathConstants.MATRIX_COL_GAP_EM * S;
        float outerPad = MathConstants.MATRIX_OUTER_PAD_EX * ex;
        float totalHeight = outerPad + 0.8F * S + 0.2F * S + MathConstants.MATRIX_ROW_SEP_EX * ex
                + 0.8F * S + 0.2F * S + outerPad;
        float axis = MathConstants.AXIS_HEIGHT_EM * S;
        float fenceMin = Math.max(totalHeight / 2.0F * MathConstants.DELIMITER_FACTOR,
                totalHeight - MathConstants.DELIMITER_SHORTFALL_EM * S);
        float fenceScale = fenceMin / (1.0F * S);
        float leftW = 0.5F * fenceScale * S;
        float height = totalHeight / 2.0F + axis;
        float aX = leftW;
        float bX = leftW + 0.5F * S + colSep;
        float aY = -height + outerPad + 0.8F * S;
        float cY = aY + 0.8F * S + MathConstants.MATRIX_ROW_SEP_EX * ex + 0.2F * S;
        int foundA = 0;
        int foundB = 0;
        int foundC = 0;
        int delimCount = 0;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("a".equals(glyph.getText())) {
                Assert.assertEquals(aX, glyph.getX(), EPS);
                Assert.assertEquals(aY, glyph.getY(), EPS);
                foundA++;
            }
            if ("b".equals(glyph.getText())) {
                Assert.assertEquals(bX, glyph.getX(), EPS);
                foundB++;
            }
            if ("c".equals(glyph.getText())) {
                Assert.assertEquals(cY, glyph.getY(), EPS);
                foundC++;
            }
            if ("(".equals(glyph.getText()) || ")".equals(glyph.getText())) {
                delimCount++;
            }
        }
        Assert.assertEquals(1, foundA);
        Assert.assertEquals(1, foundB);
        Assert.assertEquals(1, foundC);
        Assert.assertEquals(2, delimCount);
        Assert.assertEquals(leftW + 2.0F * 0.5F * S + colSep + leftW, box.getWidth(), EPS);
    }

    @Test
    public void shouldLayoutCasesWithLeftBrace() {
        MathBox box = layout("\\begin{cases}x&x>0\\\\-x&x\\\\leq0\\\\end{cases}");
        boolean foundBrace = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("{".equals(glyph.getText())) {
                foundBrace = true;
                // 花括号位于盒内左缘 x=0（内容整体右移左括号宽）
                Assert.assertEquals(0.0F, glyph.getX(), EPS);
            }
        }
        Assert.assertTrue("cases 应包含左花括号", foundBrace);
    }

    // ==================== 组合数与重音 ====================

    @Test
    public void shouldLayoutBinom() {
        MathBox box = layout("\\binom{n}{k}");
        // TeX 无分数线分数：shiftUp = num3、shiftDown = denom2，间隙不足 3θ 时上下均分 delta
        float drt = MathConstants.RULE_THICKNESS_EM * S;
        float clr = MathConstants.NO_RULE_CLR_FACTOR * drt;
        float shiftUp = MathConstants.NUM3_EM * S;
        float shiftDown = MathConstants.DENOM2_EM * S;
        float kern = shiftUp - 0.14F * S - (0.56F * S - shiftDown);
        float delta = (clr - kern) / 2.0F;
        if (delta > 0.0F) {
            shiftUp += delta;
            shiftDown += delta;
        }
        boolean foundN = false;
        boolean foundK = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("n".equals(glyph.getText())) {
                Assert.assertEquals(0.7F, glyph.getSizeScale(), EPS);
                Assert.assertEquals(-shiftUp, glyph.getY(), EPS);
                foundN = true;
            }
            if ("k".equals(glyph.getText())) {
                Assert.assertEquals(0.7F, glyph.getSizeScale(), EPS);
                Assert.assertEquals(shiftDown, glyph.getY(), EPS);
                foundK = true;
            }
        }
        Assert.assertTrue(foundN);
        Assert.assertTrue(foundK);
    }

    @Test
    public void shouldLayoutAccentOverlay() {
        MathBox box = layout("\\hat x");
        // TeX AccentedAtom（acc=false 路径）：重音按 script 字号，delta = min(base.h, xHeight)，
        // 重音基线 = delta − base.h（负 kern 语义）
        float delta = Math.min(0.8F * S, 0.45F * S);
        float accentY = delta - 0.8F * S;
        boolean foundAccent = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("\u0302".equals(glyph.getText())) {
                Assert.assertEquals(accentY, glyph.getY(), EPS);
                Assert.assertEquals(0.7F, glyph.getSizeScale(), EPS);
                // fake 度量下重音宽 = 0.35S → x = (base.w − accent.w)/2 = 0.075S
                Assert.assertEquals((0.5F * S - 0.35F * S) / 2.0F, glyph.getX(), EPS);
                foundAccent = true;
            }
        }
        Assert.assertTrue(foundAccent);
        Assert.assertEquals(0.7F * 0.8F * S - delta + 0.8F * S, box.getHeight(), EPS);
    }

    @Test
    public void shouldLayoutOverline() {
        MathBox box = layout("\\overline{AB}");
        Assert.assertEquals(1, box.getRules().size());
        RuleElem rule = box.getRules().get(0);
        Assert.assertEquals(1.0F * S, rule.getWidth(), EPS);
        // TeX OverlinedAtom：kern 3θ + 线 θ，盒高 h+5θ
        float drt = MathConstants.RULE_THICKNESS_EM * S;
        Assert.assertEquals(-(0.8F * S + MathConstants.OVERBAR_KERN_FACTOR * drt + drt / 2.0F),
                rule.getY(), EPS);
        Assert.assertEquals(0.8F * S + MathConstants.OVERBAR_BOX_FACTOR * drt, box.getHeight(), EPS);
        Assert.assertEquals(0.2F * S, box.getDepth(), EPS);
    }

    @Test
    public void shouldLayoutUnderline() {
        MathBox box = layout("\\underline{x}");
        Assert.assertEquals(1, box.getRules().size());
        RuleElem rule = box.getRules().get(0);
        Assert.assertEquals(0.5F * S, rule.getWidth(), EPS);
        // TeX UnderlinedAtom：kern 3θ + 线 θ，盒深 d+5θ
        float drt = MathConstants.RULE_THICKNESS_EM * S;
        Assert.assertEquals(0.2F * S + MathConstants.OVERBAR_KERN_FACTOR * drt + drt / 2.0F,
                rule.getY(), EPS);
        Assert.assertEquals(0.2F * S + MathConstants.OVERBAR_BOX_FACTOR * drt, box.getDepth(), EPS);
        Assert.assertEquals(0.8F * S, box.getHeight(), EPS);
    }

    // ==================== 嵌套与空 ====================

    @Test
    public void shouldLayoutNestedFracSqrt() {
        MathBox box = layout("\\frac{1}{\\sqrt{x}}");
        Assert.assertTrue(box.getWidth() > 0.0F);
        Assert.assertTrue(box.getHeight() > 0.0F);
        Assert.assertTrue(box.getDepth() > 0.0F);
        // 嵌套后规则线：分数 bar + 根号横线 ≥ 2
        Assert.assertTrue(box.getRules().size() >= 2);
    }

    @Test
    public void shouldLayoutEmpty() {
        MathBox box = SERVICE.layout(LatexParser.parse(""), S, METRICS);
        Assert.assertEquals(0.0F, box.getWidth(), EPS);
        Assert.assertEquals(0.0F, box.getHeight(), EPS);
        Assert.assertEquals(0.0F, box.getDepth(), EPS);
        Assert.assertTrue(box.getGlyphs().isEmpty());
        Assert.assertTrue(box.getRules().isEmpty());
    }
}
