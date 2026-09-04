package club.heiqi.uilib.internal.chat3.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;

/**
 * M5 接线本体单元测试(§ 桥 / 结构判据 / HUD 截断 / 缓存命中;规划 §三 M5)。
 *
 * <p>§ 桥断言与 {@code TextLayoutService.parseSegments} 的 § 语义逐位对齐(同
 * {@code TextStyle.applyFormat} 语义源);markdown 样式位保留断言钉「样式叠加不改
 * 颜色、§ 码后生效」旧裁定在接线后的落点。</p>
 */
public class ChatMarkdownPipelineTest {

    private static TextSegment seg(String text, TextStyle style) {
        return new TextSegment(text, style);
    }

    private static TextStyle white() {
        TextStyle s = new TextStyle();
        s.setColor(0xFFFFFFFF);
        return s;
    }

    @Test
    public void bridgeSplitsSectionCodesLikeParseSegments() {
        List<TextSegment> in = Collections.singletonList(
                seg("\u00a7c红色警告 \u00a7fplain tail", white()));
        List<TextSegment> out = ChatMarkdownPipeline.bridgeSectionCodes(in);
        Assert.assertEquals("§ 切换产两段", 2, out.size());
        Assert.assertEquals("红色警告 ", out.get(0).getText());
        Assert.assertEquals("§c = MC 红(与 parseSegments 同色表)", 0xFFFF5555,
                out.get(0).getStyle().getColor());
        Assert.assertEquals("plain tail", out.get(1).getText());
        Assert.assertEquals("§f 回基础色", 0xFFFFFFFF, out.get(1).getStyle().getColor());
        Assert.assertEquals("切换点空格归前段(桥),L2 unifySwitchPointSpaces 再归一(M4-fix F5)",
                " ", out.get(0).getText().substring(out.get(0).getText().length() - 1));
    }

    @Test
    public void bridgePreservesMarkdownBitsAndCodePathResetsLikeMc() {
        // 加粗段中途 §c:颜色切换 + MC 语义「色码清样式位」——与旧 A 路 parseSegments 行为一致
        TextStyle bold = white();
        bold.setFontType(FontType.BOLD);
        List<TextSegment> in = Collections.singletonList(seg("x\u00a7c红y", bold));
        List<TextSegment> out = ChatMarkdownPipeline.bridgeSectionCodes(in);
        Assert.assertEquals(2, out.size());
        Assert.assertEquals("x", out.get(0).getText());
        Assert.assertEquals("§ 前 run 保留 markdown 粗体", FontType.BOLD,
                out.get(0).getStyle().getFontType());
        Assert.assertEquals("红y", out.get(1).getText());
        Assert.assertEquals(0xFFFF5555, out.get(1).getStyle().getColor());
        // 字体位保持 = L0 TextStyle 既有语义(setFontType 同步 baseFontType,§ 色码 resetFlags
        // 回落到 base)——与 TextLayoutService.parseSegments 完全同源,桥不另造第二套 § 规则
        Assert.assertEquals("§ 语义与 parseSegments 逐位同源(base 字体位不被色码清掉)",
                FontType.BOLD, out.get(1).getStyle().getFontType());
        Assert.assertSame("输入段样式对象不被改写", bold, in.get(0).getStyle());
    }

    @Test
    public void bridgeSkipsLatexCodeAndPlainSegmentsZeroCopy() {
        List<TextSegment> in = new ArrayList<TextSegment>();
        in.add(TextSegment.forLatex("\u00a7ax^2", white()));
        TextStyle code = white();
        code.setCodeSpan(true);
        code.setCodeBackgroundColor(0x26FFFFFF);
        TextSegment codeSeg = seg("\u00a7cx\u00a7", code);
        in.add(codeSeg);
        TextSegment plain = seg("无格式码", white());
        in.add(plain);
        List<TextSegment> out = ChatMarkdownPipeline.bridgeSectionCodes(in);
        Assert.assertSame("latex 原子恒透传", in.get(0), out.get(0));
        Assert.assertSame("code 段恒透传(F1/旧裁定 code 内容字面)", codeSeg, out.get(1));
        Assert.assertSame("无 § 段透传", plain, out.get(2));
        Assert.assertEquals("纯 § 无可视文本 → 桥后整段消失(与 parseSegments 空 run 丢弃一致)", 0,
                ChatMarkdownPipeline.bridgeSectionCodes(Collections.singletonList(
                        seg("\u00a7f\u00a7r", white()))).size());
    }

    @Test
    public void structureHeuristicsMatchOldLineRuleSemantics() {
        int secondary = ChatMarkdownSettings.getTextSecondaryArgb();
        TextStyle quoteStyle = new TextStyle();
        quoteStyle.setColor(secondary);
        Assert.assertTrue("引用色首段 = 引用行(L1 F3 承接)",
                ChatMarkdownPipeline.isQuoteRow(Collections.singletonList(seg("quoted", quoteStyle))));
        Assert.assertFalse("普通白字行不是引用行",
                ChatMarkdownPipeline.isQuoteRow(Collections.singletonList(seg("hi", white()))));
        List<TextSegment> withLeadingBlank = new ArrayList<TextSegment>();
        withLeadingBlank.add(seg("", quoteStyle));
        withLeadingBlank.add(seg("quoted", quoteStyle));
        Assert.assertTrue("F6 占位空段不干扰引用判据",
                ChatMarkdownPipeline.isQuoteRow(withLeadingBlank));
        Assert.assertFalse("空行不是引用行",
                ChatMarkdownPipeline.isQuoteRow(new ArrayList<TextSegment>()));
        Assert.assertTrue("单 latex 段 = 块级公式独占行",
                ChatMarkdownPipeline.isBlockMathRow(Collections.singletonList(
                        TextSegment.forLatex("x^2", white()))));
        List<TextSegment> inlineMix = new ArrayList<TextSegment>();
        inlineMix.add(seg("foo ", white()));
        inlineMix.add(TextSegment.forLatex("x", white()));
        Assert.assertFalse("行内混排公式不套块级间距",
                ChatMarkdownPipeline.isBlockMathRow(inlineMix));
    }

    @Test
    public void hudClampKeepsEightAndAppendsEllipsisWithWidthBudget() {
        List<List<TextSegment>> shortDoc = new ArrayList<List<TextSegment>>();
        for (int i = 0; i < 8; i++) {
            shortDoc.add(Collections.singletonList(seg("行" + i, white())));
        }
        Assert.assertSame("恰好 8 行不截断(§5.4 语义)", shortDoc,
                ChatMarkdownPipeline.clampHudLines(shortDoc, null, 13, 140));
        List<List<TextSegment>> longDoc = new ArrayList<List<TextSegment>>();
        for (int i = 0; i < 12; i++) {
            longDoc.add(Collections.singletonList(seg("x", white())));
        }
        List<List<TextSegment>> clamped =
                ChatMarkdownPipeline.clampHudLines(longDoc, null, 13, 140);
        Assert.assertEquals(8, clamped.size());
        List<TextSegment> last = clamped.get(7);
        Assert.assertEquals(1, last.size());
        Assert.assertTrue("无度量注入:末行直接补省略号",
                last.get(0).getText().endsWith("..."));
        // 有度量注入:每码点 4px,8 字行 + 省略号 = 44px > 40px 预算 → 逐字回退到 36px-3ch... */
        ChatMessageList.SegmentMeasurer fourPx = new ChatMessageList.SegmentMeasurer() {
            @Override
            public float widthOf(TextSegment segment, int fontSizePx) {
                return segment.getText().length() * 4.0F;
            }
        };
        List<List<TextSegment>> wide = new ArrayList<List<TextSegment>>();
        for (int i = 0; i < 9; i++) {
            wide.add(Collections.singletonList(seg("abcdefghij", white())));
        }
        List<List<TextSegment>> cut = ChatMarkdownPipeline.clampHudLines(wide, fourPx, 13, 40);
        TextSegment tail = cut.get(7).get(cut.get(7).size() - 1);
        // 10 字行 = 40px 已吃满预算 → 逐码点回退到 7 字 + "..." = 40px
        Assert.assertEquals("abcdefg...", tail.getText());
        Assert.assertTrue("末行含省略号且总宽 ≤ 预算",
                tail.getText().endsWith("...") && tail.getText().length() * 4 <= 40);
    }

    @Test
    public void layoutCachesWrappedLinesPerTextWidthEpoch() {
        final List<Integer> wrapCalls = new ArrayList<Integer>();
        ChatMessageList.SegmentFlowWrapper counting = new ChatMessageList.SegmentFlowWrapper() {
            @Override
            public List<List<TextSegment>> wrap(List<TextSegment> flat, int maxWidthPx,
                    int baseFontSizePx) {
                wrapCalls.add(Integer.valueOf(1));
                List<List<TextSegment>> out = new ArrayList<List<TextSegment>>();
                out.add(new ArrayList<TextSegment>(flat));
                return out;
            }
        };
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<List<TextSegment>> first = pipeline.layout("- item", 0xFFFFFFFF, 140, 13, null, counting);
        List<List<TextSegment>> second = pipeline.layout("- item", 0xFFFFFFFF, 140, 13, null, counting);
        Assert.assertSame("每帧零解析:同参二次调用命中缓存(换行未重算)", first, second);
        Assert.assertEquals(1, wrapCalls.size());
        List<List<TextSegment>> otherWidth =
                pipeline.layout("- item", 0xFFFFFFFF, 120, 13, null, counting);
        Assert.assertNotSame("定行宽变化 → 重换行(缓存 key 含宽度)", otherWidth, first);
        Assert.assertEquals(2, wrapCalls.size());
        // 管道产物形状:「• 」+ 内容(经计数换行原样带出扁平流)
        Assert.assertEquals("\u2022 ", otherWidth.get(0).get(0).getText());
        Assert.assertEquals("item", otherWidth.get(0).get(1).getText());
    }
}