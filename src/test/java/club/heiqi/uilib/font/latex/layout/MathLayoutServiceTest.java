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
        // TeX 约束链：shiftUp = base.h − supdrop = 0.8S − 0.386108S；下限 max(sup2, sup.d + xHeight/4)
        float xHeight = 0.45F * S;
        float supDepth = 0.14F * S;
        float shiftUp = Math.max(Math.max(0.8F * S - MathConstants.SUP_DROP_EM * S,
                MathConstants.SUP2_EM * S), supDepth + xHeight / 4.0F);
        float expectedSupY = -shiftUp;
        GlyphElem supGlyph = box.getGlyphs().get(1);
        Assert.assertEquals("2", supGlyph.getText());
        Assert.assertEquals(expectedSupY, supGlyph.getY(), EPS);
        Assert.assertEquals(0.7F, supGlyph.getSizeScale(), EPS);
        Assert.assertEquals(0.5F * S + MathConstants.SCRIPT_SPACE_EM * S + 0.35F * S, box.getWidth(), EPS);
        Assert.assertEquals(0.56F * S - expectedSupY, box.getHeight(), EPS);
    }

    @Test
    public void shouldLayoutSubscript() {
        MathBox box = layout("x_i");
        // TeX 约束链：shiftDown = base.d + subdrop；下限 max(sub1, sub.h − 4·xHeight/5)
        float subHeight = 0.56F * S;
        float xHeight = 0.45F * S;
        float shiftDown = Math.max(Math.max(0.2F * S + MathConstants.SUB_DROP_EM * S,
                MathConstants.SUB1_EM * S), subHeight - 4.0F * xHeight / 5.0F);
        GlyphElem subGlyph = box.getGlyphs().get(1);
        Assert.assertEquals("i", subGlyph.getText());
        Assert.assertEquals(shiftDown, subGlyph.getY(), EPS);
        Assert.assertEquals(0.7F, subGlyph.getSizeScale(), EPS);
        Assert.assertEquals(0.5F * S + MathConstants.SCRIPT_SPACE_EM * S + 0.35F * S, box.getWidth(), EPS);
        Assert.assertEquals(shiftDown + 0.14F * S, box.getDepth(), EPS);
    }

    @Test
    public void shouldStackLimitsForBigOperator() {
        MathBox box = layout("\\sum_{i=1}^{n}");
        // sub = i=1：3 码点 × 0.35S + 2 个 thick 间距（5/18 × 0.7S）；sup = n：0.35S；base = ∑：0.5S
        float subWidth = 3.0F * 0.35F * S + 2.0F * (MathConstants.THICK_MU / 18.0F) * 0.7F * S;
        float supWidth = 0.35F * S;
        float contentWidth = subWidth;
        Assert.assertEquals(contentWidth, box.getWidth(), EPS);
        // sup 基线 = -(baseH + overGap + supD)（limits 上标按底对齐）；sub = baseD + underGap + subH
        float supY = -(0.8F * S + MathConstants.LIMITS_OVER_GAP_EM * S + 0.14F * S);
        float subY = 0.2F * S + MathConstants.LIMITS_UNDER_GAP_EM * S + 0.56F * S;
        boolean foundSup = false;
        boolean foundSub = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("n".equals(glyph.getText())) {
                Assert.assertEquals(supY, glyph.getY(), EPS);
                Assert.assertEquals((contentWidth - supWidth) / 2.0F, glyph.getX(), EPS);
                foundSup = true;
            }
            if ("i".equals(glyph.getText())) {
                Assert.assertEquals(subY, glyph.getY(), EPS);
                Assert.assertEquals(0.0F, glyph.getX(), EPS);
                foundSub = true;
            }
        }
        Assert.assertTrue("应包含上标 n", foundSup);
        Assert.assertTrue("应包含下标 i", foundSub);
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

    // ==================== 根号 ====================

    @Test
    public void shouldLayoutSqrt() {
        MathBox box = layout("\\sqrt{x}");
        // TeX NthRoot（text 口径）：clr = drt + |xHeight|/4；根号字形差量 delta/2 回补 clr
        float drt = MathConstants.RULE_THICKNESS_EM * S;
        float xHeight = 0.45F * S;
        float clr = drt + xHeight / 4.0F;
        float totalH = 1.0F * S + clr + drt;
        float radicalScale = totalH / (0.8F * S);
        float radicalAscent = 0.8F * radicalScale * S;
        float radicalDescent = 0.2F * radicalScale * S;
        float radicalTotal = radicalAscent + radicalDescent;
        float delta = radicalTotal - totalH;
        if (delta > 0.0F) {
            clr += delta / 2.0F;
        }
        float radicalWidth = 0.5F * radicalScale * S;
        Assert.assertEquals(1, box.getRules().size());
        RuleElem rule = box.getRules().get(0);
        Assert.assertEquals(-(0.8F * S + clr + drt), rule.getY(), EPS);
        Assert.assertEquals(radicalWidth, rule.getX(), EPS);
        Assert.assertEquals(0.5F * S, rule.getWidth(), EPS);
        Assert.assertEquals(radicalWidth + 0.5F * S, box.getWidth(), EPS);
        Assert.assertEquals(0.8F * S + clr + drt, box.getHeight(), EPS);
        Assert.assertEquals(0.2F * S, box.getDepth(), EPS);
        boolean foundRadical = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("\u221A".equals(glyph.getText())) {
                Assert.assertEquals(radicalScale, glyph.getSizeScale(), EPS);
                foundRadical = true;
            }
        }
        Assert.assertTrue("应包含根号字形", foundRadical);
    }

    @Test
    public void shouldLayoutSqrtWithIndex() {
        MathBox box = layout("\\sqrt[3]{x}");
        boolean foundIndex = false;
        RuleElem rule = box.getRules().get(0);
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("3".equals(glyph.getText())) {
                Assert.assertEquals(0.7F, glyph.getSizeScale(), EPS);
                // index 在根号上方（负 y）
                Assert.assertTrue(glyph.getY() < 0.0F);
                // 精确锚定：index 基线 = sqrtBox.depth − index.depth − 0.55×(sqrtBox 总高)
                // （TeX NthRoot r.setShift 语义：指数位于根号内侧中上部）
                float drt = MathConstants.RULE_THICKNESS_EM * S;
                float clr = drt + 0.45F * S / 4.0F;
                float totalH = 1.0F * S + clr + drt;
                float radicalScale = totalH / (0.8F * S);
                float radicalAscent = 0.8F * radicalScale * S;
                float radicalDescent = 0.2F * radicalScale * S;
                float delta = (radicalAscent + radicalDescent) - totalH;
                if (delta > 0.0F) {
                    clr += delta / 2.0F;
                }
                float sqrtHeight = 0.8F * S + clr + radicalAscent;
                float sqrtDepth = 0.2F * S;
                float indexY = sqrtDepth - 0.14F * S
                        - MathConstants.SQRT_INDEX_FACTOR * (sqrtHeight + sqrtDepth);
                Assert.assertEquals(indexY, glyph.getY(), EPS);
                foundIndex = true;
            }
        }
        Assert.assertTrue("应包含根指数 3", foundIndex);
    }

    // ==================== 伸缩括号 ====================

    @Test
    public void shouldLayoutLeftRight() {
        MathBox box = layout("\\left(x\\right)");
        float gap = MathConstants.DELIM_GAP_EM * S;
        float targetHeight = 1.0F * S + 2.0F * gap;
        float scale = targetHeight / (1.0F * S);
        float delimSize = scale * S;
        float delimWidth = 0.5F * delimSize;
        // 宽度 = leftDelim + gap + content + gap + rightDelim
        Assert.assertEquals(delimWidth + gap + 0.5F * S + gap + delimWidth, box.getWidth(), EPS);
        boolean foundLeft = false;
        boolean foundRight = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("(".equals(glyph.getText())) {
                Assert.assertEquals(scale, glyph.getSizeScale(), EPS);
                foundLeft = true;
            }
            if (")".equals(glyph.getText())) {
                foundRight = true;
            }
        }
        Assert.assertTrue(foundLeft);
        Assert.assertTrue(foundRight);
    }

    // ==================== 矩阵 ====================

    @Test
    public void shouldLayoutMatrixGrid() {
        MathBox box = layout("\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}");
        float colGap = MathConstants.MATRIX_COL_GAP_EM * S;
        float rowGap = MathConstants.MATRIX_ROW_GAP_EM * S;
        float contentWidth = 2.0F * 0.5F * S + colGap;
        float totalHeight = 2.0F * 1.0F * S + rowGap;
        float axis = MathConstants.AXIS_HEIGHT_EM * S;
        // 盒中心对齐数学轴；高度可能被伸缩括号抬高（fence 超内容 2×overhang）
        float overhang = MathConstants.MATRIX_FENCE_OVERHANG_EM * S;
        float nativeHeight = 1.0F * S;
        float fenceScale = Math.max(1.0F, (totalHeight + 2.0F * overhang) / nativeHeight);
        float delimAscent = 0.8F * fenceScale * S;
        float delimDescent = 0.2F * fenceScale * S;
        float fenceBaseline = -axis + (delimAscent - delimDescent) / 2.0F;
        float expectedHeight = Math.max(totalHeight / 2.0F + axis, delimAscent - fenceBaseline);
        float expectedDepth = Math.max(totalHeight / 2.0F - axis, fenceBaseline + delimDescent);
        Assert.assertEquals(expectedHeight, box.getHeight(), EPS);
        Assert.assertEquals(expectedDepth, box.getDepth(), EPS);
        // 四格字形齐全，第二列 x 更大
        float aX = Float.NaN;
        float bX = Float.NaN;
        float aY = Float.NaN;
        float cY = Float.NaN;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("a".equals(glyph.getText())) {
                aX = glyph.getX();
                aY = glyph.getY();
            }
            if ("b".equals(glyph.getText())) {
                bX = glyph.getX();
            }
            if ("c".equals(glyph.getText())) {
                cY = glyph.getY();
            }
        }
        Assert.assertTrue("第二列 x 应大于第一列", bX > aX);
        Assert.assertTrue("第二行 y 应大于第一行", cY > aY);
        Assert.assertEquals(colGap / 2.0F, aX, EPS);
        // 括号定界符存在（2 个非内容字形）
        int delimCount = 0;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("(".equals(glyph.getText()) || ")".equals(glyph.getText())) {
                delimCount++;
            }
        }
        Assert.assertEquals(2, delimCount);
    }

    @Test
    public void shouldLayoutCasesWithLeftBrace() {
        MathBox box = layout("\\begin{cases}x&x>0\\\\-x&x\\\\leq0\\\\end{cases}");
        boolean foundBrace = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("{".equals(glyph.getText())) {
                foundBrace = true;
                // 左花括号在负 x 侧
                Assert.assertTrue(glyph.getX() < 0.0F);
            }
        }
        Assert.assertTrue("cases 应包含左花括号", foundBrace);
    }

    // ==================== 组合数与重音 ====================

    @Test
    public void shouldLayoutBinom() {
        MathBox box = layout("\\binom{n}{k}");
        // 上下元素 script 0.7，垂直堆叠，圆括号伸缩
        boolean foundN = false;
        boolean foundK = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("n".equals(glyph.getText())) {
                Assert.assertEquals(0.7F, glyph.getSizeScale(), EPS);
                Assert.assertTrue(glyph.getY() < 0.0F); // 上元素在基线上方
                foundN = true;
            }
            if ("k".equals(glyph.getText())) {
                Assert.assertEquals(0.7F, glyph.getSizeScale(), EPS);
                Assert.assertTrue(glyph.getY() > 0.0F); // 下元素在基线下方
                foundK = true;
            }
        }
        Assert.assertTrue(foundN);
        Assert.assertTrue(foundK);
    }

    @Test
    public void shouldLayoutAccentOverlay() {
        MathBox box = layout("\\hat x");
        float gap = MathConstants.ACCENT_GAP_EM * S;
        boolean foundAccent = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("\u0302".equals(glyph.getText())) {
                Assert.assertEquals(-(0.8F * S + gap + 0.2F * S), glyph.getY(), EPS);
                // fake 度量下重音宽 = 0.5S → x = (base.w - accent.w)/2 = 0（居中）
                Assert.assertEquals(0.0F, glyph.getX(), EPS);
                foundAccent = true;
            }
        }
        Assert.assertTrue(foundAccent);
    }

    @Test
    public void shouldLayoutOverline() {
        MathBox box = layout("\\overline{AB}");
        Assert.assertEquals(1, box.getRules().size());
        RuleElem rule = box.getRules().get(0);
        Assert.assertEquals(1.0F * S, rule.getWidth(), EPS); // AB 两码点 = 1.0S
        float gap = MathConstants.ACCENT_GAP_EM * S;
        Assert.assertEquals(-(0.8F * S + gap + MathConstants.RULE_THICKNESS_EM * S), rule.getY(), EPS);
    }

    @Test
    public void shouldLayoutUnderline() {
        MathBox box = layout("\\underline{x}");
        Assert.assertEquals(1, box.getRules().size());
        RuleElem rule = box.getRules().get(0);
        Assert.assertEquals(0.5F * S, rule.getWidth(), EPS);
        Assert.assertEquals(0.2F * S + MathConstants.ACCENT_GAP_EM * S, rule.getY(), EPS);
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
