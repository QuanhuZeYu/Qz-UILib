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
            // 新语义：字形自然 ink 右越量（正体字形无越出回退 0）；渲染斜切避让由布局侧折算
            return 0.0F;
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
        // 斜体校正：单字符变量基底时上标右移 = 自然 ink 越量（mock 0）+ 渲染斜切避让
        //（ACCENT_SKEW_FACTOR×xHeight = 0.125×0.45S）；TeX ScriptsAtom：scriptspace 加在上标盒内部，
        // 上标左缘紧贴基底右缘（无 scriptspace 间隙）
        float supShift = MathConstants.ACCENT_SKEW_FACTOR * 0.45F * S;
        Assert.assertEquals(0.5F * S + supShift, supGlyph.getX(), EPS);
        Assert.assertEquals(0.5F * S + 0.35F * S + supShift,
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
        Assert.assertEquals(0.5F * S + 0.35F * S, box.getWidth(), EPS);
        Assert.assertEquals(shiftDown + 0.14F * S, box.getDepth(), EPS);
    }

    /** 重音 skew：斜体单字符基底上重音右移 skew（TeX \accent skewchar，JLaTeXMath getSkew 同语义）。 */
    @Test
    public void shouldSkewAccentOverItalicBase() {
        MathBox box = layout("\\hat{x}");
        // advance 均 0.5S → diff=0 → 重音 x = skew = 0.125 × 0.45S（tan14°/2 × xHeight 几何近似）
        float skew = MathConstants.ACCENT_SKEW_FACTOR * 0.45F * S;
        boolean foundBase = false;
        boolean foundAccent = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("x".equals(glyph.getText())) {
                Assert.assertEquals(0.0F, glyph.getX(), EPS);
                Assert.assertTrue("基底应为斜体", glyph.isItalic());
                foundBase = true;
            }
            if ("\u0302".equals(glyph.getText())) {
                Assert.assertEquals(skew, glyph.getX(), EPS);
                foundAccent = true;
            }
        }
        Assert.assertTrue("应包含基底 x", foundBase);
        Assert.assertTrue("应包含重音（U+0302）", foundAccent);
        Assert.assertEquals(0.5F * S + skew, box.getWidth(), EPS);
    }

    @Test
    public void shouldStackBigOperatorLimits() {
        // \sum 行内也是 limits 上下堆叠（TeX SCRIPT_LIMITS）：符号 ink 中心锚定数学轴，
        // 上隙 max(bigop1, bigop3 − sup.d)、下隙 max(bigop2, bigop4 − sub.h)，
        // 外沿 bigop5、上下限水平居中、盒尾补 MEDMUSKIP（BigOperatorAtom limits 路径）
        MathBox box = layout("\\sum_{i=1}^{n}");
        // sub = i=1：3 码点 × 0.35S + 2 个 thick 间距（5/18 × 0.7S）；sup = n：0.35S；base = ∑：0.5S
        float subWidth = 3.0F * 0.35F * S + 2.0F * (MathConstants.THICK_MU / 18.0F) * 0.7F * S;
        // 轴居中（fake 回退盒度量）：inkCenterOffsetY=(0.2S−0.8S)/2=−0.3S（y 向下），inkH=1.0S →
        // baseShift = −axis − inkCenter×scale = 0.3S−axis；refH=inkH/2+axis=0.75S，refD=inkH/2−axis=0.25S
        float baseShift = 0.3F * S - MathConstants.AXIS_HEIGHT_EM * S;
        float refHeight = 0.5F * S + MathConstants.AXIS_HEIGHT_EM * S;
        float refDepth = 0.5F * S - MathConstants.AXIS_HEIGHT_EM * S;
        float overKern = Math.max(MathConstants.BIGOP1_EM * S,
                MathConstants.BIGOP3_EM * S - 0.14F * S);
        float underKern = Math.max(MathConstants.BIGOP2_EM * S,
                MathConstants.BIGOP4_EM * S - 0.56F * S);
        float supY = -(refHeight + overKern + 0.14F * S);
        float subY = refDepth + underKern + 0.56F * S;
        float contentWidth = Math.max(Math.max(0.5F * S, 0.35F * S), subWidth);
        boolean foundSup = false;
        boolean foundSub = false;
        boolean foundBase = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("n".equals(glyph.getText())) {
                Assert.assertEquals(supY, glyph.getY(), EPS);
                Assert.assertEquals((contentWidth - 0.35F * S) / 2.0F, glyph.getX(), EPS);
                foundSup = true;
            }
            if ("i".equals(glyph.getText())) {
                Assert.assertEquals(subY, glyph.getY(), EPS);
                Assert.assertEquals((contentWidth - subWidth) / 2.0F, glyph.getX(), EPS);
                foundSub = true;
            }
            if ("\u2211".equals(glyph.getText())) {
                Assert.assertEquals(baseShift, glyph.getY(), EPS);
                Assert.assertEquals((contentWidth - 0.5F * S) / 2.0F, glyph.getX(), EPS);
                foundBase = true;
            }
        }
        Assert.assertTrue("应包含上标 n", foundSup);
        Assert.assertTrue("应包含下标 i", foundSub);
        Assert.assertTrue("应包含轴居中的 ∑", foundBase);
        Assert.assertEquals(contentWidth + MathConstants.BIG_OPERATOR_TAIL_SPACE_EM * S, box.getWidth(), EPS);
        Assert.assertEquals(MathConstants.BIGOP5_EM * S + 0.7F * S + overKern + refHeight, box.getHeight(), EPS);
        Assert.assertEquals(0.7F * S + underKern + refDepth + MathConstants.BIGOP5_EM * S, box.getDepth(), EPS);
    }

    /** 裸大运算符（无上下标）：TeX ScriptsAtom big-op 分支仍轴居中 + 尾部 MEDMUSKIP。 */
    @Test
    public void shouldCenterBareBigOperatorOnAxis() {
        MathBox box = layout("\\sum");
        float axis = MathConstants.AXIS_HEIGHT_EM * S;
        // fake 回退盒度量：inkCenter = −0.3S、inkH = 1.0S → opScale = 1.0（∑ 目标 1.0em）
        float baseShift = -axis - (-0.3F * S);
        Assert.assertEquals(1, box.getGlyphs().size());
        Assert.assertEquals(baseShift, box.getGlyphs().get(0).getY(), EPS);
        Assert.assertEquals(0.5F * S + MathConstants.BIG_OPERATOR_TAIL_SPACE_EM * S,
                box.getWidth(), EPS);
        Assert.assertEquals(0.5F * S + axis, box.getHeight(), EPS);
        Assert.assertEquals(0.5F * S - axis, box.getDepth(), EPS);
    }

    /** \nolimits：大运算符符号仍轴居中，脚本降级侧挂，尾部 MEDMUSKIP。 */
    @Test
    public void shouldSideHangNolimitsBigOperatorScripts() {
        MathBox box = layout("\\sum\\nolimits_{i=1}^{n}");
        // 轴居中盒（自然尺寸）：inkCenter = −0.3S、inkHalf = 0.5S → baseShift = 0.3S − axis
        float axis = MathConstants.AXIS_HEIGHT_EM * S;
        float baseShift = 0.3F * S - axis;
        // 脚本参照轴居中盒度量：refH = 0.8S、refD = 0.2S（不走单字符归零）
        float subWidth = 3.0F * 0.35F * S + 2.0F * (MathConstants.THICK_MU / 18.0F) * 0.7F * S;
        float subDrop = MathConstants.SCRIPT_SUB_DROP_EM * S;
        float subY = Math.max(Math.max(0.2F * S + subDrop, MathConstants.SUB2_EM * S),
                0.56F * S - 4.0F * 0.45F * S / 5.0F);
        boolean foundBase = false;
        boolean foundSub = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("\u2211".equals(glyph.getText())) {
                Assert.assertEquals(baseShift, glyph.getY(), EPS);
                foundBase = true;
            }
            if ("i".equals(glyph.getText())) {
                Assert.assertEquals(subY, glyph.getY(), EPS);
                Assert.assertEquals(0.5F * S, glyph.getX(), EPS); // 下标左缘贴符号右缘
                foundSub = true;
            }
        }
        Assert.assertTrue(foundBase);
        Assert.assertTrue(foundSub);
        Assert.assertEquals(0.5F * S + subWidth + MathConstants.BIG_OPERATOR_TAIL_SPACE_EM * S,
                box.getWidth(), EPS);
    }

    /** limits 双脚本的符号 italic delta（cmex ∫ 0.194em 语义）：上/下限 ±delta/2。 */
    @Test
    public void shouldApplyItalicDeltaToBigOperatorLimits() {
        MathBox box = SERVICE.layout(LatexParser.parse("\\int_a^b"), S, INK_METRICS);
        // ∫ 目标视觉高 1.11em；INK_METRICS：inkH 回退 1.0S → opScale = 1.11；
        // inkCenter 回退 −0.3S → baseShift = 0.3×1.11S − axis；opItalic = 0.2S（mock ∫ 自然越出）
        float contentWidth = 0.5F * 1.11F * S; // 符号宽 0.5S×opScale 最大
        boolean foundSup = false;
        boolean foundSub = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("b".equals(glyph.getText())) {
                Assert.assertEquals((contentWidth - 0.35F * S) / 2.0F + 0.1F * S, glyph.getX(), EPS);
                foundSup = true;
            }
            if ("a".equals(glyph.getText())) {
                Assert.assertEquals((contentWidth - 0.35F * S) / 2.0F - 0.1F * S, glyph.getX(), EPS);
                foundSub = true;
            }
        }
        Assert.assertTrue(foundSup);
        Assert.assertTrue(foundSub);
        // 双脚本尾部不含 delta（TeX deltaSymbol 之后无 StrutBox）
        Assert.assertEquals(contentWidth + MathConstants.BIG_OPERATOR_TAIL_SPACE_EM * S,
                box.getWidth(), EPS);
    }

    /** 单脚本 limits 的符号 italic delta 补进尾部（StrutBox(delta) 语义）。 */
    @Test
    public void shouldAppendItalicDeltaToSingleScriptLimits() {
        MathBox box = SERVICE.layout(LatexParser.parse("\\int_0"), S, INK_METRICS);
        // 单下标 0（0.35S）：contentWidth = max(符号 0.555S, 0.35S) = 0.555S
        // 尾部 = MEDMUSKIP + 0.2S
        Assert.assertEquals(0.555F * S + MathConstants.BIG_OPERATOR_TAIL_SPACE_EM * S + 0.2F * S,
                box.getWidth(), EPS);
    }

    @Test
    public void shouldPlaceFunctionNameScriptAsSubscript() {
        MathBox box = layout("\\lim_{x}");
        // \lim 等函数名是 SCRIPT_NORMAL（非 SCRIPT_LIMITS）：行内走普通 ScriptsAtom，
        // 下标右下（非单字符基底：shiftDown = depth + subdrop，下限 max(sub1, sub.h − 4·xh/5)）
        float subDrop = MathConstants.SUB_DROP_EM * MathConstants.SCRIPT_SCALE * S;
        float subHeight = 0.56F * S;
        float xHeight = 0.45F * S;
        float subY = Math.max(Math.max(0.2F * S + subDrop, MathConstants.SUB1_EM * S),
                subHeight - 4.0F * xHeight / 5.0F);
        boolean foundSub = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("x".equals(glyph.getText())) {
                Assert.assertEquals(subY, glyph.getY(), EPS);
                // TeX ScriptsAtom：下标左缘紧贴基底右缘（无 scriptspace）
                Assert.assertEquals(1.5F * S, glyph.getX(), EPS);
                foundSub = true;
            }
        }
        Assert.assertTrue("\\lim 应包含右下 x", foundSub);
        Assert.assertEquals(0.8F * S, box.getHeight(), EPS);
        Assert.assertEquals(subY + 0.14F * S, box.getDepth(), EPS);
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

    /**
     * ink 表度量 mock：括号类 ink 高 0.6em（小于字体盒 1.0em）、ink 中心在基线上方 0.4em、
     * ink 左偏移 0.05em、ink 宽 0.4em；∫ 自然右越量 0.2em（cmex 0.194em 量级）。
     * 模拟真机「ink 与字体盒不对称」的字形分布（headless 真机表数据口径）。
     */
    private static final MathMetrics INK_METRICS = new MathMetrics() {
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
        public float inkWidth(String text, float sizePx) {
            if ("(".equals(text) || ")".equals(text) || "{".equals(text) || "}".equals(text)
                    || "|".equals(text)) {
                return 0.4F * sizePx;
            }
            return advance(text, sizePx);
        }

        @Override
        public float inkLeftBearing(String text, float sizePx) {
            if ("(".equals(text) || ")".equals(text) || "{".equals(text) || "}".equals(text)) {
                return 0.05F * sizePx;
            }
            return 0.0F;
        }

        @Override
        public float inkHeight(String text, float sizePx) {
            if ("(".equals(text) || ")".equals(text) || "{".equals(text) || "}".equals(text)
                    || "|".equals(text)) {
                return 0.6F * sizePx;
            }
            return ascent(sizePx) + descent(sizePx);
        }

        @Override
        public float inkCenterOffsetY(String text, float sizePx) {
            if ("(".equals(text) || ")".equals(text) || "{".equals(text) || "}".equals(text)
                    || "|".equals(text)) {
                return -0.4F * sizePx; // 基线上方 0.4em（y 向下口径）
            }
            return (descent(sizePx) - ascent(sizePx)) / 2.0F;
        }

        @Override
        public float italicCorrection(String text, float sizePx) {
            if ("\u222B".equals(text)) {
                return 0.2F * sizePx; // ∫ 自然右越量
            }
            return 0.0F;
        }
    };

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
        // TeX NthRoot（text 口径）：clr = θ + θ/4，再经变体阶梯 delta/2 补偿——
        // 变体选最小 ≥ totalH+clr 档位（cmex 最小变体深度 0.96em），多余一半补入 clr
        float drt = MathConstants.RULE_THICKNESS_EM * S;
        float clr0 = drt + drt / 4.0F;
        float totalH = 1.0F * S;
        float variantDepth = MathConstants.SQRT_VARIANT_DEPTH_EM[1] * S;
        float clr = clr0 + (variantDepth - (totalH + clr0)) / 2.0F;
        float barTop = 0.8F * S + clr + drt;
        float radicalScale = Math.max(1.0F, (totalH + clr + drt) / (1.0F * S));
        float radicalAscent = 0.8F * radicalScale * S;
        float radicalDescent = 0.2F * radicalScale * S;
        // 字形顶对齐横线顶：radicalY = ascent − barTop
        float radicalY = radicalAscent - barTop;
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
        float clr0 = drt + drt / 4.0F;
        float totalH = 1.0F * S;
        float variantDepth = MathConstants.SQRT_VARIANT_DEPTH_EM[1] * S;
        float clr = clr0 + (variantDepth - (totalH + clr0)) / 2.0F;
        float radicalScale = Math.max(1.0F, (totalH + clr + drt) / (1.0F * S));
        float radicalDescent = 0.2F * radicalScale * S;
        float barTop = 0.8F * S + clr + drt;
        float radicalY = 0.8F * radicalScale * S - barTop;
        float height = barTop;
        float depth = Math.max(0.2F * S, radicalY + radicalDescent);
        // TeX NthRoot：指数基线 = sqrtBox.depth − index.depth − 0.55×(sqrtBox 总高)
        float indexY = depth - 0.1F * S - MathConstants.SQRT_INDEX_FACTOR * (height + depth);
        // 水平：10mu 负 kern（scriptscript 字号下 index 宽 = 0.5×0.5S），
        // index 左缘 = max(0, 10mu − index 宽)
        float indexLeft = Math.max(0.0F, MathConstants.SQRT_INDEX_NEG_KERN_MU * S / 18.0F - 0.25F * S);
        boolean foundIndex = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("3".equals(glyph.getText())) {
                // TeX env.rootStyle()：根指数按 scriptscript（0.5）字号
                Assert.assertEquals(0.5F, glyph.getSizeScale(), EPS);
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

    /** 原子盒度量按 ink 边界（TeX 字符级 fontdimen 语义：盒不再虚高到字体 ascent）。 */
    @Test
    public void shouldUseInkAnchoredAtomMetrics() {
        // INK_METRICS：括号 ink 高 0.6S、ink 中心基线上方 0.4S（y 向下 −0.4S）
        MathBox paren = SERVICE.layout(LatexParser.parse("("), S, INK_METRICS);
        // 定界符原子轴锚定：glyphY = −axis − inkCenter = −0.25S + 0.4S = 0.15S
        Assert.assertEquals(0.15F * S, paren.getGlyphs().get(0).getY(), EPS);
        // 盒度量按 ink：height = inkHalf − inkCenter − glyphY = 0.3S+0.4S−0.15S = 0.55S
        Assert.assertEquals(0.55F * S, paren.getHeight(), EPS);
        // depth = max(0, glyphY + inkCenter + inkHalf) = 0.15S−0.4S+0.3S = 0.05S
        Assert.assertEquals(0.05F * S, paren.getDepth(), EPS);
    }

    /** fence 缩放基准改 ink 高（INK_METRICS：括号 ink 高 0.6em < 盒 1.0em → scale 放大）。 */
    @Test
    public void shouldScaleFenceByInkHeight() {
        MathBox box = SERVICE.layout(LatexParser.parse("\\left(x\\right)"), S, INK_METRICS);
        float axis = MathConstants.AXIS_HEIGHT_EM * S;
        float delta = Math.max(0.8F * S - axis, 0.2F * S + axis);
        float minHeight = Math.max(delta * MathConstants.DELIMITER_FACTOR,
                2.0F * delta - MathConstants.DELIMITER_SHORTFALL_EM * S);
        // scale 基准 = ink 高 0.6S（盒总高口径会小 1.667×）
        float scale = Math.max(1.0F, minHeight / (0.6F * S));
        float delimWidth = 0.5F * scale * S;
        Assert.assertEquals(delimWidth + 0.5F * S + delimWidth, box.getWidth(), EPS);
    }

    /** fence 盒度量不再 double-count baselineY：盒高/深 = ink 边界（INK_METRICS 数值锚定）。 */
    @Test
    public void shouldUseInkAnchoredFenceMetrics() {
        MathBox box = SERVICE.layout(LatexParser.parse("\\left(\\right)"), S, INK_METRICS);
        // 空内容：δ = max(0 − axis, 0 + axis) = axis → minHeight = max(0.4505S, −0)S → scale = 1.0
        // baselineY = −axis − (−0.4S) = 0.15S；fence 盒高 = inkHalf − inkCenter = 0.3S + 0.4S = 0.7S
        // addBox 平移后：盒高 = 0.7S − 0.15S = 0.55S（旧实现 0.65S−0.15S=0.5S 少算）、深 = 0.15S
        Assert.assertEquals(0.55F * S, box.getHeight(), EPS);
        Assert.assertEquals(0.15F * S, box.getDepth(), EPS);
        Assert.assertEquals(1.0F * S, box.getWidth(), EPS); // 两侧自然尺寸括号各 0.5S
    }

    /** \middle：段间中间定界符与两侧同 minHeight、轴居中，前后按 REL 间距。 */
    @Test
    public void shouldLayoutMiddleDelimiter() {
        MathBox box = layout("\\left\\{x\\middle|y\\right\\}");
        float delta = Math.max(0.8F * S - MathConstants.AXIS_HEIGHT_EM * S,
                0.2F * S + MathConstants.AXIS_HEIGHT_EM * S);
        float minHeight = Math.max(delta * MathConstants.DELIMITER_FACTOR,
                2.0F * delta - MathConstants.DELIMITER_SHORTFALL_EM * S);
        float scale = Math.max(1.0F, minHeight / (1.0F * S));
        float fenceWidth = 0.5F * scale * S;
        float relGlue = (MathConstants.THICK_MU / 18.0F) * S;
        boolean foundX = false;
        boolean foundMiddle = false;
        boolean foundY = false;
        GlyphElem brace = null;
        GlyphElem middle = null;
        GlyphElem xGlyph = null;
        GlyphElem yGlyph = null;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("{".equals(glyph.getText())) {
                brace = glyph;
            }
            if ("|".equals(glyph.getText())) {
                middle = glyph;
                foundMiddle = true;
            }
            if ("x".equals(glyph.getText())) {
                xGlyph = glyph;
                foundX = true;
            }
            if ("y".equals(glyph.getText())) {
                yGlyph = glyph;
                foundY = true;
            }
        }
        Assert.assertTrue(foundX);
        Assert.assertTrue(foundMiddle);
        Assert.assertTrue(foundY);
        Assert.assertNotNull(brace);
        // 中间定界符与两侧同基线（同 ink 锚定公式）、位于两段之间
        Assert.assertEquals(brace.getY(), middle.getY(), EPS);
        Assert.assertTrue(middle.getX() > xGlyph.getX());
        Assert.assertTrue(middle.getX() < yGlyph.getX());
        // 总宽 = 左括号 + x + glue + 中括号 + glue + y + 右括号
        Assert.assertEquals(2.0F * fenceWidth + 1.0F * S + fenceWidth + 2.0F * relGlue,
                box.getWidth(), EPS);
    }

    /** shiftBox 平移必须保留原盒 ink 越量（\frac{\left(x\right)}{2} 分数横线依赖）。 */
    @Test
    public void shouldKeepInkOverhangThroughShiftBox() {
        MathBox box = SERVICE.layout(LatexParser.parse("\\left(xy\\right."), S, ITALIC_METRICS);
        Assert.assertEquals("左括号 shift 后应保留内容斜体右越量", 0.1F * S, box.getRightInkOverhang(), EPS);
        Assert.assertEquals(0.0F, box.getLeftInkOverhang(), EPS);
    }

    /** accent 盒高不得低于复合基底（\hat{\frac{a}{b}} 盒高 = 基底高，不再被 ascent 压低）。 */
    @Test
    public void shouldNotUnderestimateAccentHeightOverCompoundBase() {
        MathBox box = layout("\\hat{\\frac{a}{b}}");
        // \frac{a}{b} 基底盒高 ≈ 1.01S > 字体 ascent 0.8S
        float drt = MathConstants.RULE_THICKNESS_EM * S;
        float axis = MathConstants.AXIS_HEIGHT_EM * S;
        float kern1 = Math.max(drt, MathConstants.NUM2_EM * S - 0.14F * S - (axis + drt / 2.0F));
        float numY = -(axis + drt / 2.0F + kern1 + 0.14F * S);
        float baseHeight = -(numY - 0.56F * S);
        Assert.assertEquals(baseHeight, box.getHeight(), EPS);
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
        MathBox box = layout("\\begin{cases}x&x>0\\\\-x&x\\\\leq0\\end{cases}");
        boolean foundBrace = false;
        GlyphElem xGlyph = null;
        GlyphElem minusGlyph = null;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("{".equals(glyph.getText())) {
                foundBrace = true;
                // 花括号位于盒内左缘 x=0（内容整体右移左括号宽）
                Assert.assertEquals(0.0F, glyph.getX(), EPS);
            }
            if ("x".equals(glyph.getText()) && xGlyph == null) {
                xGlyph = glyph;
            }
            if ("-".equals(glyph.getText())) {
                minusGlyph = glyph;
            }
        }
        Assert.assertTrue("cases 应包含左花括号", foundBrace);
        Assert.assertNotNull(xGlyph);
        Assert.assertNotNull(minusGlyph);
        // TeX cases = array{ll}：第一列左对齐——x 与 -x 左缘同 x（居中口径会错位半列宽）
        Assert.assertEquals(xGlyph.getX(), minusGlyph.getX(), EPS);
    }

    /** array 列说明 {lr}：第一列左对齐、第二列右对齐（对齐符驱动列内位置）。 */
    @Test
    public void shouldApplyArrayColumnAligns() {
        MathBox box = layout("\\begin{array}{lr}a&bb\\\\cc&d\\end{array}");
        float colSep = MathConstants.MATRIX_COL_GAP_EM * S;
        float colW0 = 1.0F * S; // max(a=0.5S, cc=1.0S)
        float colW1 = 1.0F * S; // max(bb=1.0S, d=0.5S)
        GlyphElem aGlyph = null;
        GlyphElem cGlyph = null;
        GlyphElem dGlyph = null;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("a".equals(glyph.getText())) {
                aGlyph = glyph;
            }
            if ("c".equals(glyph.getText()) && cGlyph == null) {
                cGlyph = glyph;
            }
            if ("d".equals(glyph.getText())) {
                dGlyph = glyph;
            }
        }
        Assert.assertNotNull(aGlyph);
        Assert.assertNotNull(cGlyph);
        Assert.assertNotNull(dGlyph);
        Assert.assertEquals("第一列 l：a 与 cc 左缘同 x（0）", aGlyph.getX(), cGlyph.getX(), EPS);
        Assert.assertEquals(0.0F, aGlyph.getX(), EPS);
        // 第二列 r：d（0.5S）右对齐列右缘 = colW0 + colSep + colW1
        Assert.assertEquals(colW0 + colSep + (colW1 - 0.5F * S), dGlyph.getX(), EPS);
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
        // TeX AccentedAtom（acc=false 路径）：重音按正文字号，vBox [accent][strut(−base.h)][base]
        // 堆叠 → 重音字形底与基底基线重合（同基线），字形自身带上方偏移（CM accent 字形设计）
        boolean foundAccent = false;
        for (GlyphElem glyph : box.getGlyphs()) {
            if ("\u0302".equals(glyph.getText())) {
                Assert.assertEquals(0.0F, glyph.getY(), EPS);
                Assert.assertEquals(1.0F, glyph.getSizeScale(), EPS);
                // fake 度量下重音宽 = 0.5S → diff = 0；斜体基底 skew = 0.125×0.45S
                // → 重音 x = skew（TeX \accent skewchar 语义）
                Assert.assertEquals(MathConstants.ACCENT_SKEW_FACTOR * 0.45F * S, glyph.getX(), EPS);
                foundAccent = true;
            }
        }
        Assert.assertTrue(foundAccent);
        Assert.assertEquals(0.8F * S, box.getHeight(), EPS);
        Assert.assertEquals(0.2F * S, box.getDepth(), EPS);
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
