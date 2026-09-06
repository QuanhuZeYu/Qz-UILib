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

    private static ChatMarkdownPipeline.RenderedLine rline(String text) {
        return ChatMarkdownPipeline.renderedForTest(
                Collections.singletonList(seg(text, white())));
    }

    @Test
    public void hudClampKeepsEightAndAppendsEllipsisWithWidthBudget() {
        List<ChatMarkdownPipeline.RenderedLine> shortDoc =
                new ArrayList<ChatMarkdownPipeline.RenderedLine>();
        for (int i = 0; i < 8; i++) {
            shortDoc.add(rline("行" + i));
        }
        Assert.assertSame("恰好 8 行不截断(§5.4 语义)", shortDoc,
                ChatMarkdownPipeline.clampHudLines(shortDoc, null, 13, 140));
        List<ChatMarkdownPipeline.RenderedLine> longDoc =
                new ArrayList<ChatMarkdownPipeline.RenderedLine>();
        for (int i = 0; i < 12; i++) {
            longDoc.add(rline("x"));
        }
        List<ChatMarkdownPipeline.RenderedLine> clamped =
                ChatMarkdownPipeline.clampHudLines(longDoc, null, 13, 140);
        Assert.assertEquals(8, clamped.size());
        List<TextSegment> last = clamped.get(7).segments();
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
        List<ChatMarkdownPipeline.RenderedLine> wide =
                new ArrayList<ChatMarkdownPipeline.RenderedLine>();
        for (int i = 0; i < 9; i++) {
            wide.add(rline("abcdefghij"));
        }
        List<ChatMarkdownPipeline.RenderedLine> cut =
                ChatMarkdownPipeline.clampHudLines(wide, fourPx, 13, 40);
        TextSegment tail = cut.get(7).segments().get(cut.get(7).segments().size() - 1);
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
        List<ChatMarkdownPipeline.RenderedLine> first =
                pipeline.layout("- item", 0xFFFFFFFF, 140, 13, null, counting);
        List<ChatMarkdownPipeline.RenderedLine> second =
                pipeline.layout("- item", 0xFFFFFFFF, 140, 13, null, counting);
        Assert.assertSame("每帧零解析:同参二次调用命中缓存(换行未重算)", first, second);
        Assert.assertEquals(1, wrapCalls.size());
        List<ChatMarkdownPipeline.RenderedLine> otherWidth =
                pipeline.layout("- item", 0xFFFFFFFF, 120, 13, null, counting);
        Assert.assertNotSame("定行宽变化 → 重换行(缓存 key 含宽度)", otherWidth, first);
        Assert.assertEquals(2, wrapCalls.size());
        // 管道产物形状:「• 」+ 内容(经计数换行原样带出;F2 前导空格机制不变)
        Assert.assertEquals("\u2022 ", otherWidth.get(0).segments().get(0).getText());
        Assert.assertEquals("item", otherWidth.get(0).segments().get(1).getText());
    }

    // ==================== C4 输入侧 § 清洗（§ 处理归位 chat3 集成层） ====================

    /** 直通换行替身：每条逻辑行原样出一条视觉行（不进 FontService 生产度量）。 */
    private static final ChatMessageList.SegmentFlowWrapper PASSTHROUGH =
            new ChatMessageList.SegmentFlowWrapper() {
                @Override
                public List<List<TextSegment>> wrap(List<TextSegment> flat, int maxWidthPx,
                        int baseFontSizePx) {
                    List<List<TextSegment>> out = new ArrayList<List<TextSegment>>();
                    out.add(new ArrayList<TextSegment>(flat));
                    return out;
                }
            };

    private static final int WHITE = 0xFFFFFFFF;

    private static String visible(List<ChatMarkdownPipeline.RenderedLine> lines) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            for (TextSegment segment : lines.get(i).segments()) {
                out.append(segment.isLatex()
                        ? "\u27e6" + "?" + "\u27e7" : segment.getText());
            }
        }
        return out.toString();
    }

    /** 清洗函数本体：逐行行首、码集校验、大小写同义、连续多码、幂等、非破坏宽容。 */
    @Test
    public void stripLeadingSectionCodesIsPureLineHeadScopedAndIdempotent() {
        Assert.assertEquals("- x",
                ChatMarkdownPipeline.stripLeadingSectionCodes("\u00a7a- x"));
        Assert.assertEquals("连续多码逐个消费", "- x",
                ChatMarkdownPipeline.stripLeadingSectionCodes("\u00a7a\u00a7b\u00a7c- x"));
        Assert.assertEquals("重置码 r 在码集内", "- x",
                ChatMarkdownPipeline.stripLeadingSectionCodes("\u00a7r\u00a7a- x"));
        Assert.assertEquals("大小写同义", "- x",
                ChatMarkdownPipeline.stripLeadingSectionCodes("\u00a7F\u00a7L- x"));
        Assert.assertEquals("数字码", "- x",
                ChatMarkdownPipeline.stripLeadingSectionCodes("\u00a70- x"));
        // 行首之前的空格不允许（旧行级规则同口径）；行中/非法码/孤立 § 一律不动
        Assert.assertSame("前导空格行不命中", " \u00a7a- x",
                ChatMarkdownPipeline.stripLeadingSectionCodes(" \u00a7a- x"));
        Assert.assertSame("非码字符不消费", "\u00a7z- x",
                ChatMarkdownPipeline.stripLeadingSectionCodes("\u00a7z- x"));
        Assert.assertSame("行尾孤立 § 不吞", "x\u00a7",
                ChatMarkdownPipeline.stripLeadingSectionCodes("x\u00a7"));
        Assert.assertSame("行中 § 不动", "a \u00a7c b",
                ChatMarkdownPipeline.stripLeadingSectionCodes("a \u00a7c b"));
        Assert.assertSame("无 § 文本同引用", "plain",
                ChatMarkdownPipeline.stripLeadingSectionCodes("plain"));
        // 多行：逐行独立、行界与空行原样保序
        Assert.assertEquals("a\n- x\n清空",
                ChatMarkdownPipeline.stripLeadingSectionCodes("a\n\u00a7b- x\n\u00a7r\u6e05\u7a7a"));
        // 幂等（缓存 key 用清洗后文本仍自洽的前提）：二次调用恒同引用
        String once = ChatMarkdownPipeline.stripLeadingSectionCodes("\u00a7a- x\n\u00a7b> y");
        Assert.assertSame(once, ChatMarkdownPipeline.stripLeadingSectionCodes(once));
    }

    /** 归位目的锁：§a- 玩家列表行经清洗后在 chat3 链路仍渲染为列表项（无 § 残留）。 */
    @Test
    public void sectionPrefixedListLineStillRendersAsListItemThroughPipeline() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> out = pipeline.layout(
                "\u00a7a- \u73a9\u5bb6\u5217\u8868\u884c", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals(1, out.size());
        List<TextSegment> segments = out.get(0).segments();
        Assert.assertEquals("• " + "\u73a9\u5bb6\u5217\u8868\u884c", visible(out));
        Assert.assertEquals("标记段", "\u2022 ", segments.get(0).getText());
        Assert.assertTrue("清洗后不得残留 § 字面: " + visible(out),
                visible(out).indexOf('\u00a7') < 0);
    }

    /**
     * 行为变化锁（任务书点名必须交代①）：§f + 4 空格 + 「- item」——桥剥行首 §f 后剩 4 空格，
     * L1 按 CommonMark 0.30 §4.4 判缩进代码块（C1a 语义），不再是列表项。主流正确结果，
     * 相对旧 chat3 观感是变化，钉死之。
     */
    @Test
    public void leadingCodePlusFourSpaceMarkerBecomesIndentedCodeBlock() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> out = pipeline.layout(
                "\u00a7f    - item", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals(1, out.size());
        Assert.assertTrue("身份 = 缩进代码块行（不是列表）", out.get(0).isCode());
        Assert.assertEquals("- item", visible(out));
        Assert.assertEquals("代码行无列表正文列", 0, out.get(0).leftInsetPx());
    }

    /** 行中 § 码不受输入清洗影响，仍由输出侧桥解释成颜色（M4-fix F4 的另一半原样保住）。 */
    @Test
    public void midLineSectionCodesAreStillBridgedToColor() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> out = pipeline.layout(
                "\u7532 \u00a7c\u7ea2", WHITE, 4000, 13, null, PASSTHROUGH);
        List<TextSegment> segments = out.get(0).segments();
        Assert.assertEquals(2, segments.size());
        Assert.assertEquals("\u7532 ", segments.get(0).getText());
        Assert.assertEquals(WHITE, segments.get(0).getStyle().getColor());
        Assert.assertEquals("\u7ea2", segments.get(1).getText());
        Assert.assertEquals(0xFFFF5555, segments.get(1).getStyle().getColor());
    }

    /**
     * 清洗代价登记锁：行首码随剥消失其颜色语义（无条件剥 = 旧行级规则口径，非「命中块标记
     * 才剥」）；行首码之后的行中码仍生效。
     */
    @Test
    public void leadingColorSemanticsAreStrippedAwayByDesign() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> out = pipeline.layout(
                "\u00a7c\u7ea2\u8272\u8b66\u544a", WHITE, 4000, 13, null, PASSTHROUGH);
        List<TextSegment> segments = out.get(0).segments();
        Assert.assertEquals(1, segments.size());
        Assert.assertEquals("\u7ea2\u8272\u8b66\u544a", segments.get(0).getText());
        Assert.assertEquals("行首 §c 不再上色（输入侧已剥）", WHITE,
                segments.get(0).getStyle().getColor());
        // 复合形态：行首码剥、行中码留 —— 「§a- §citem」→ 列表项 + 红内容
        List<ChatMarkdownPipeline.RenderedLine> mix = pipeline.layout(
                "\u00a7a- \u00a7citem", WHITE, 4000, 13, null, PASSTHROUGH);
        List<TextSegment> mixSeg = mix.get(0).segments();
        Assert.assertEquals("\u2022 ", mixSeg.get(0).getText());
        Assert.assertEquals("item", mixSeg.get(1).getText());
        Assert.assertEquals(0xFFFF5555, mixSeg.get(1).getStyle().getColor());
    }

    /**
     * 缓存陷阱锁：两级 key 用清洗后文本 ⇒「§a- x」与「- x」清洗后同串、渲染逐段等值 ⇒
     * 共享条目是去重（assertSame 坐实同 key）；「不同原文清洗后不同串」则互不可见
     * （夹进「§b- y」再回读「§a- x」，内容不被串味）；baseColor 仍分 key。
     */
    @Test
    public void cacheKeyUsesCleanedTextWithoutFlavorMixing() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> secForm = pipeline.layout(
                "\u00a7a- x", WHITE, 4000, 13, null, PASSTHROUGH);
        List<ChatMarkdownPipeline.RenderedLine> plainForm = pipeline.layout(
                "- x", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertSame("清洗后同串 ⇒ 同 key（去重而非两条语义相同的缓存）",
                secForm, plainForm);
        List<ChatMarkdownPipeline.RenderedLine> other = pipeline.layout(
                "\u00a7b- y", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals("• y", visible(other));
        List<ChatMarkdownPipeline.RenderedLine> again = pipeline.layout(
                "\u00a7a- x", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertSame("串味防御：回读仍是自己的产物", secForm, again);
        Assert.assertEquals("• x", visible(again));
        List<ChatMarkdownPipeline.RenderedLine> otherBase = pipeline.layout(
                "\u00a7a- x", 0xFF101010, 4000, 13,
                null, PASSTHROUGH);
        Assert.assertNotSame("baseColor 参与 key，不得与白字条目共享",
                secForm, otherBase);
        Assert.assertEquals("• x", visible(otherBase));
        Assert.assertEquals("基础色进段样式", 0xFF101010,
                otherBase.get(0).segments().get(1).getStyle().getColor());
    }
}
