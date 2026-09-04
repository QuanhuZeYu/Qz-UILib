package club.heiqi.uilib.font.layout.markdown;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;

/**
 * chat3 旧行级规则 / code 切分器契约的 L1 承接钉死（M5 删除 {@code ChatMarkdownLineRule} 与
 * {@code ChatCodeSpanSplitter} 后，其全部行为契约经 {@link MarkdownDocument} 公共接缝复验——
 * 覆盖不随实现消失，规划 §三 M5「行级规则由 L1 块层承接」与 §二之五 F1-F4 的可追溯证据）。
 *
 * <p>期望为 B 路裁定行为：与旧垫片语义有差处按规划裁定登记（未闭合标记字面宽容 = M1 裁定；
 * 深缩进独立列表行绝对层级 = M5 F2 补全；code 段样式 = F1）。§ 颜色解释属 § 桥
 * （chat3 侧，见 {@code ChatMarkdownPipelineTest}），本类只钉 L1 段流形状。</p>
 */
public class MarkdownChat3RuleInheritanceTest {

    private static final String TICK = String.valueOf((char) 0x60);

    private static List<TextSegment> flat(String src) {
        TextStyle base = new TextStyle();
        base.setColor(0xFFFFFFFF);
        return MarkdownDocument.parse(src).toSegments(MarkdownStyleTable.defaults(), base);
    }

    private static String joined(List<TextSegment> segments) {
        StringBuilder out = new StringBuilder();
        for (TextSegment segment : segments) {
            out.append(segment.isLatex() ? "\u27e6" + segment.getLatexSource() + "\u27e7"
                    : segment.getText());
        }
        return out.toString();
    }

    // ==================== 旧 ChatMarkdownLineRule 契约 ====================

    @Test
    public void bulletMarkersNormalizeToDotPrefix() {
        for (String marker : new String[] {"-", "*", "+"}) {
            List<TextSegment> out = flat(marker + " item");
            Assert.assertEquals(marker + " 归一为「• 」前缀段", 2, out.size());
            Assert.assertEquals("\u2022 ", out.get(0).getText());
            Assert.assertEquals("item", out.get(1).getText());
        }
    }

    @Test
    public void indentLevelsMapLeadingSpacesPerTwoAsAbsoluteOrRelativeNesting() {
        // 旧契约「2 空格 = 1 级、每级 2 空格」逐档复验（M5 F2 补全后独立深缩进行同样成立）
        Assert.assertEquals("\u2022 ", flat("- a").get(0).getText());
        Assert.assertEquals("  \u2022 ", flat("  - a").get(0).getText());
        Assert.assertEquals("  \u2022 ", flat("   - a").get(0).getText());
        Assert.assertEquals("    \u2022 ", flat("    - a").get(0).getText());
        Assert.assertEquals("    \u2022 ", flat("     - a").get(0).getText());
        // 多行相对嵌套（门禁 P08 同语料）不回归
        List<TextSegment> nested = flat("- 甲\n  - 乙\n    - 丙");
        Assert.assertEquals("\u2022 甲\n  \u2022 乙\n    \u2022 丙", joined(nested));
    }

    @Test
    public void orderedListKeepsSourceOrdinalMarker() {
        List<TextSegment> out = flat("12. keep me");
        Assert.assertEquals("12. ", out.get(0).getText());
        Assert.assertEquals("keep me", out.get(1).getText());
    }

    @Test
    public void wholeLineFormulasBecomeLatexAtoms() {
        List<TextSegment> dd = flat("$$x^2$$");
        Assert.assertEquals(1, dd.size());
        Assert.assertTrue(dd.get(0).isLatex());
        Assert.assertEquals("x^2", dd.get(0).getLatexSource());
        List<TextSegment> single = flat("$x^2$");
        Assert.assertEquals(1, single.size());
        Assert.assertEquals("x^2", single.get(0).getLatexSource());
    }

    @Test
    public void nonDedicatedFormulaLinesStayInlineLiteralOrAtoms() {
        // 行内混排:公式成为行内原子但绝不整行原子化(块级判据 = 单段整行,见 M5 结构判据)
        List<TextSegment> mix = flat("foo $x$ bar");
        Assert.assertTrue(mix.size() > 1);
        Assert.assertEquals("foo  bar", joined(mix).replace("\u27e6x\u27e7", ""));
        // $5.99 防误伤(数字邻居)与未闭合 $ 恒字面(M1 裁定沿用)
        Assert.assertEquals("价格 $5.99 稳", joined(flat("价格 $5.99 稳")));
        Assert.assertEquals("未闭合 $$ 字面宽容(M1 裁定沿用;旧垫片曾剥前导 $$,B 恒按未闭合字面)",
                "$$x^2", joined(flat("$$x^2")));
    }

    @Test
    public void sectionPrefixedListLinesConsumeCodesWithMarker() {
        // 真机同款:行首 §f 残留 + 列表标记（旧 classify 先行、F4 承接）
        List<TextSegment> plain = flat("\u00a7f- item");
        Assert.assertEquals("\u2022 item", joined(plain));
        Assert.assertEquals("  \u2022 item", joined(flat("\u00a7f  - item")));
        Assert.assertEquals("\u2022 item", joined(flat("\u00a7f\u00a7l- item")));
        // 未命中块标记:§ 序列一字不动（F4 定稿口径;颜色解释属 § 桥）
        Assert.assertEquals("\u00a7f\u00a7r", joined(flat("\u00a7f\u00a7r")));
        Assert.assertEquals("\u00a7f-not 列表", joined(flat("\u00a7f-not 列表")));
    }

    @Test
    public void notAListLinesRemainLiteral() {
        Assert.assertEquals("-not-list", joined(flat("-not-list")));
        Assert.assertEquals("a - b", joined(flat("a - b")));
        Assert.assertEquals("1.5 不是列表", joined(flat("1.5 不是列表")));
        Assert.assertEquals("", joined(flat("")));
        Assert.assertEquals("", joined(flat("   ")));
    }

    // ==================== 旧 ChatCodeSpanSplitter 契约（F1 落点） ====================

    @Test
    public void closedBacktickPairBecomesCodeSpanWithChat3Style() {
        List<TextSegment> out = flat("a " + TICK + "code" + TICK + " b");
        Assert.assertEquals(3, out.size());
        Assert.assertEquals("code", out.get(1).getText());
        Assert.assertTrue(out.get(1).getStyle().isCodeSpan());
        Assert.assertEquals(0x26FFFFFF, out.get(1).getStyle().getCodeBackgroundColor());
        Assert.assertEquals(12, out.get(1).getStyle().getFontSizePx());
        Assert.assertFalse(out.get(0).getStyle().isCodeSpan());
        Assert.assertFalse(out.get(2).getStyle().isCodeSpan());
    }

    @Test
    public void codeSpanClearsLinkAndNeverParsesInnerMarks() {
        // code 内 URL/加粗/公式全字面、link 位为 null（下游 linkify 见 codeSpan 恒跳过）
        String src = TICK + "curl http://x.y/z -s**b**$x$" + TICK;
        List<TextSegment> out = flat(src);
        Assert.assertEquals(1, out.size());
        Assert.assertEquals("curl http://x.y/z -s**b**$x$", out.get(0).getText());
        Assert.assertTrue(out.get(0).getStyle().isCodeSpan());
        Assert.assertNull(out.get(0).getStyle().getLink());
    }

    @Test
    public void unclosedAndEmptyPairsFallBackToLiteral() {
        Assert.assertEquals("a " + TICK + " b", joined(flat("a " + TICK + " b")));
        // 空配/四连按行内裁定：`` 生成 code 内容为空由解析器按宽容处理，此处只钉「不吞字」
        String doubled = "x" + TICK + TICK + "y";
        Assert.assertTrue("未闭合/空配不吞可见字符: " + joined(flat(doubled)),
                joined(flat(doubled)).contains("x") && joined(flat(doubled)).contains("y"));
    }
}
