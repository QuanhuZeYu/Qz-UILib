package club.heiqi.uilib.internal.chat3.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
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

    // ==================== C4-fix 乙′ 输入侧 § 清洗（命中块标记才消费；归位宪法：L1 零认知 §） ====================

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

    /**
     * 清洗函数本体（乙′口径）：行首「≤3 空格 + § 码对」交替视图，命中块标记才采用剥后视图；
     * 不命中一字不动（行首色保住——C4 初版「无条件剥」的改判点）。码集校验、大小写同义、
     * 连续多码、幂等、非破坏宽容逐项复验。
     */
    @Test
    public void stripLeadingSectionCodesConsumesOnlyOnBlockMarkerHitAndIdempotent() {
        // 命中块标记：列表/标题/围栏/引用/分隔线 ⇒ 消费行首码对
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
        Assert.assertEquals("空格交替循环（蓝本 M5 修形态）", " - x",
                ChatMarkdownPipeline.stripLeadingSectionCodes("\u00a7f \u00a7a- x"));
        Assert.assertEquals("§f + 2 空格 + 标记 = 命中且空格保留进视图", "  - x",
                ChatMarkdownPipeline.stripLeadingSectionCodes("\u00a7f  - x"));
        // 未命中块标记：整行保留（行首色语义进输出侧桥——乙′ 相对 C4 初版的改判点）
        Assert.assertSame("§c + 普通文本不剥（行首色保住）", "\u00a7c\u7ea2\u8272\u8b66\u544a",
                ChatMarkdownPipeline.stripLeadingSectionCodes("\u00a7c\u7ea2\u8272\u8b66\u544a"));
        Assert.assertSame("§f + 4 空格 + 标记 ind>3 不算命中", "\u00a7f    - item",
                ChatMarkdownPipeline.stripLeadingSectionCodes("\u00a7f    - item"));
        Assert.assertSame("非法码字符不消费", "\u00a7z- x",
                ChatMarkdownPipeline.stripLeadingSectionCodes("\u00a7z- x"));
        Assert.assertSame("行尾孤立 § 不吞", "x\u00a7",
                ChatMarkdownPipeline.stripLeadingSectionCodes("x\u00a7"));
        Assert.assertSame("行中 § 不动", "a \u00a7c b",
                ChatMarkdownPipeline.stripLeadingSectionCodes("a \u00a7c b"));
        Assert.assertSame("setext 下划线独立行不是块起点（蓝本 isBlockStart 同缺，锁死该口径）",
                "\u00a7f===", ChatMarkdownPipeline.stripLeadingSectionCodes("\u00a7f==="));
        Assert.assertSame("无 § 文本同引用", "plain",
                ChatMarkdownPipeline.stripLeadingSectionCodes("plain"));
        // 多行：逐行独立判定——命中行剥、未命中行保、行界与空行原样保序
        Assert.assertEquals("a\n- x\n\u00a7r\u6e05\u7a7a",
                ChatMarkdownPipeline.stripLeadingSectionCodes(
                        "a\n\u00a7b- x\n\u00a7r\u6e05\u7a7a"));
        // 幂等（缓存 key 用清洗后文本仍自洽的前提）：命中/未命中混合二次调用恒同引用
        String once = ChatMarkdownPipeline.stripLeadingSectionCodes(
                "\u00a7a- x\n\u00a7b> y\n\u00a7c\u7eaf\u6587\u672c");
        Assert.assertSame(once, ChatMarkdownPipeline.stripLeadingSectionCodes(once));
    }

    /** 归位目的锁：§a- 玩家列表行命中块标记、消费行首码后在 chat3 链路仍渲染为列表项。 */
    @Test
    public void sectionPrefixedListLineStillRendersAsListItemThroughPipeline() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> out = pipeline.layout(
                "\u00a7a- \u73a9\u5bb6\u5217\u8868\u884c", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals(1, out.size());
        List<TextSegment> segments = out.get(0).segments();
        Assert.assertEquals("\u2022 " + "\u73a9\u5bb6\u5217\u8868\u884c", visible(out));
        Assert.assertEquals("标记段", "\u2022 ", segments.get(0).getText());
        Assert.assertTrue("命中行清洗后不得残留 § 字面: " + visible(out),
                visible(out).indexOf('\u00a7') < 0);
    }

    /**
     * C4 前语义恢复锁（本任务书点名回退项）：§f- item / §f  - item / §f§l- item 命中块标记
     * 仍剥（消费行首码 ⇒ 列表项、行首色让位于块结构，与 C4 前逐位一致）；§c 纯文本行保留
     * （行首色进桥）。旧 L1 直连口径（markerView 在块层）搬到集成层后观感不漂移。
     */
    @Test
    public void preC4SectionFamilySemanticsRestoredThroughIntegrationLayer() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        Assert.assertEquals("\u2022 item", visible(pipeline.layout(
                "\u00a7f- item", WHITE, 4000, 13, null, PASSTHROUGH)));
        Assert.assertEquals("\u2022 item", visible(pipeline.layout(
                "\u00a7f  - item", WHITE, 4000, 13, null, PASSTHROUGH)));
        Assert.assertEquals("\u2022 item", visible(pipeline.layout(
                "\u00a7f\u00a7l- item", WHITE, 4000, 13, null, PASSTHROUGH)));
        Assert.assertEquals("\u2022 item", visible(pipeline.layout(
                "\u00a7f \u00a7a- item", WHITE, 4000, 13, null, PASSTHROUGH)));
        // 未命中 ⇒ 原样保留、由桥上色（见 leadingColorSemanticsSurviveOnNonHitLines 专项）
        List<ChatMarkdownPipeline.RenderedLine> plain = pipeline.layout(
                "\u00a7c\u7eaf\u6587\u672c\u884c", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals("\u7eaf\u6587\u672c\u884c", visible(plain));
        Assert.assertEquals(0xFFFF5555, plain.get(0).segments().get(0).getStyle().getColor());
    }

    /**
     * 行为变化锁（乙′ 改判，C4 初版「§f+4 空格 ⇒ 缩进代码块」反转回 C4 前形态）：
     * §f + 4 空格 + 「- item」——粗检视图 ind&gt;3 不算命中 ⇒ <b>不剥</b> ⇒ L1 见 §f 行首 ⇒
     * 段落字面（剥除从未发生，谈不上缩进代码）。注释口径必须写明：这是 chat3 集成层的既有
     * 语义差——纯 markdown 消费者走 L1 直连时，「    - item」（4 空格开头）才是缩进代码块
     * （CommonMark 0.30 §4.4 / 本仓 C1a），与本行无关，不是 bug。
     */
    @Test
    public void leadingCodePlusFourSpaceMarkerStaysParagraphLiteral() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> out = pipeline.layout(
                "\u00a7f    - item", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals(1, out.size());
        Assert.assertFalse("身份 = 段落字面（乙′ 不剥 → L1 见行首 § 非空白）", out.get(0).isCode());
        Assert.assertFalse(out.get(0).isRule());
        Assert.assertEquals("    - item", visible(out));
        Assert.assertEquals("空格位置进可见文本（视图未采用）", 0, out.get(0).leftInsetPx());
        Assert.assertEquals("§f 进桥解释为基础色白", WHITE,
                out.get(0).segments().get(out.get(0).segments().size() - 1).getStyle().getColor());
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
     * 行首色保住锁（乙′ 改判，反转 C4 初版「无条件剥」的行首色丢失）：§c红色警告 不命中块
     * 标记 ⇒ 不剥 ⇒ 段文本进桥 ⇒ MC 红上色——与 C4 前逐位一致（旧 ChatMessageList NONE 分支
     * parseCached(renderLine) 含行首 § 码、颜色保留；旧 classify 的剥码只是其检测局部变量）。
     * 对照：命中行（§a- §citem）行首码仍消费、行中码仍上色。
     */
    @Test
    public void leadingColorSemanticsSurviveOnNonHitLines() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> out = pipeline.layout(
                "\u00a7c\u7ea2\u8272\u8b66\u544a", WHITE, 4000, 13, null, PASSTHROUGH);
        List<TextSegment> segments = out.get(0).segments();
        Assert.assertEquals("§c 由桥消费、不再产 § 字面段", 1, segments.size());
        Assert.assertEquals("\u7ea2\u8272\u8b66\u544a", segments.get(0).getText());
        Assert.assertEquals("行首 §c 保住颜色（乙′：未命中不剥，色语义进桥）", 0xFFFF5555,
                segments.get(0).getStyle().getColor());
        // 复合形态：行首码（命中 ⇒ 消费）+ 行中码（保留 ⇒ 桥上色）——「§a- §citem」= 列表项 + 红内容
        List<ChatMarkdownPipeline.RenderedLine> mix = pipeline.layout(
                "\u00a7a- \u00a7citem", WHITE, 4000, 13, null, PASSTHROUGH);
        List<TextSegment> mixSeg = mix.get(0).segments();
        Assert.assertEquals("\u2022 ", mixSeg.get(0).getText());
        Assert.assertEquals("item", mixSeg.get(1).getText());
        Assert.assertEquals(0xFFFF5555, mixSeg.get(1).getStyle().getColor());
    }

    /**
     * 缓存陷阱锁（乙′ 口径）：两级 key 用清洗后文本 ⇒「§a- x」（命中 ⇒ 清洗后「- x」）与
     * 「- x」同串、渲染逐段等值 ⇒ 共享条目是去重（assertSame 坐实同 key）；「不同原文清洗后
     * 不同串」互不可见（夹进「§b- y」再回读「§a- x」不串味）；baseColor 仍分 key。未命中行
     * （§c红色警告）清洗后仍含 §，与命中行天然不同 key，无需额外口径。
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
        Assert.assertEquals("\u2022 y", visible(other));
        List<ChatMarkdownPipeline.RenderedLine> again = pipeline.layout(
                "\u00a7a- x", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertSame("串味防御：回读仍是自己的产物", secForm, again);
        Assert.assertEquals("\u2022 x", visible(again));
        List<ChatMarkdownPipeline.RenderedLine> otherBase = pipeline.layout(
                "\u00a7a- x", 0xFF101010, 4000, 13,
                null, PASSTHROUGH);
        Assert.assertNotSame("baseColor 参与 key，不得与白字条目共享",
                secForm, otherBase);
        Assert.assertEquals("\u2022 x", visible(otherBase));
        Assert.assertEquals("基础色进段样式", 0xFF101010,
                otherBase.get(0).segments().get(1).getStyle().getColor());
    }

    // ==================== 乙′ 一致性锁：chat3 粗检 ⇔ L1 真判据（两份代码防漂移，漂移即红） ====================

    /**
     * 样本行（覆盖任务书点名形态：ATX 1..6、setext 下划线、围栏两定界、引用、无序三标记、
     * 有序两句点右括号、分隔线三种、缩进 &gt;3、纯文本、§z 非法码、行尾孤立 §、§f+4 空格，
     * 外加空格交替、空视图、井号无空格等边界）。断言双向一致：
     * <b>chat3 粗检命中 ⇔ 该行清洗后（markerView 产物）交给 L1 实际产出块级身份</b>
     * （行接缝 kind != TEXT 或 quoteLevel &gt; 0 = 非段落字面）。两份判据代码必然漂移于
     * 未来某次单侧改动——本锁当场红。
     */
    @Test
    public void coarseBlockMarkerAgreesWithL1BlockIdentity() {
        String[] samples = {
            // ATX 1..6 + 边界
            "\u00a7f# h1", "\u00a7f## h2", "\u00a7f### h3", "\u00a7f#### h4",
            "\u00a7f##### h5", "\u00a7f###### h6",
            "\u00a7f####### h7",      // 7 个井号：两边都否
            "\u00a7f#nospace",        // 井号后无空格：两边都否
            // setext 下划线（粗检蓝本不认独立下划线行——两边同为「否」即一致）
            "\u00a7f===", "\u00a7f====", "\u00a7f--", "\u00a7f= =",
            // 围栏
            "\u00a7f\u0060\u0060\u0060 java", "\u00a7f~~~", "\u00a7f\u0060\u0060",
            "\u00a7f\u0060\u0060\u0060a\u0060\u0060\u0060", // info 含反引号：两边都否
            // 引用
            "\u00a7f> q", "\u00a7f>> q2", "\u00a7f>nosp",
            // 无序/有序列表
            "\u00a7f- x", "\u00a7f* x", "\u00a7f+ x", "\u00a7f-",
            "\u00a7f1. x", "\u00a7f12) x", "\u00a7f1.x", "\u00a7f-not",
            "\u00a7f1234567890. x",   // 10 位序号：两边都否
            // 分隔线
            "\u00a7f***", "\u00a7f---", "\u00a7f___", "\u00a7f- - -", "\u00a7f**",
            // 缩进边界
            "\u00a7f  - x", "\u00a7f   - x", "\u00a7f    - x", "\u00a7f     ### h",
            // 空格交替循环
            "\u00a7f \u00a7a- x", " \u00a7f- x", "  \u00a7f# t", "   \u00a7f> q",
            // 纯文本 / 非法码 / 孤立 § / 空视图
            "\u00a7c\u7eaf\u6587\u672c", "\u00a7r", "\u00a7f\u00a7r", "\u00a7z- x",
            "x\u00a7", "\u00a7f\u00a7", "\u00a7f\t- x",
        };
        int hits = 0;
        int misses = 0;
        TextStyle base = new TextStyle();
        base.setColor(WHITE);
        for (int i = 0; i < samples.length; i++) {
            String line = samples[i];
            boolean coarseHit = ChatMarkdownPipeline.coarseHitsBlockMarker(line);
            String eff = ChatMarkdownPipeline.markerView(line);
            // 清洗入口与单行视图同源（防「两级缓存吃的不是判据吃的那个函数」的暗漂移）
            Assert.assertEquals(line + " stripLeadingSectionCodes 与 markerView 同读数",
                    eff, ChatMarkdownPipeline.stripLeadingSectionCodes(line));
            List<MarkdownLayoutLine> logical = MarkdownDocument.parse(eff)
                    .toLayoutLines(MarkdownStyleTable.defaults(), base);
            // 零行 = 空块结构（如未闭合空围栏）而非段落字面——字面段落恒 ≥1 行，故零行算
            // 块级身份为真（这类 eff 只能是命中后的视图，未命中的 § 行恒产 1 行 TEXT）
            boolean blockIdentity = logical.isEmpty();
            for (int k = 0; k < logical.size(); k++) {
                MarkdownLayoutLine l = logical.get(k);
                if (l.getKind() != MarkdownLayoutLine.Kind.TEXT || l.getQuoteLevel() > 0) {
                    blockIdentity = true;
                }
            }
            Assert.assertEquals(line + "：粗检命中与 L1 实际块级身份必须一致（漂移即红）",
                    coarseHit, blockIdentity);
            if (coarseHit) {
                hits++;
            } else {
                misses++;
            }
        }
        // 反空跑地板：判据两边都不许恒真/恒假（全命中或全不命中 = 样本表或粗检退化）
        Assert.assertTrue("命中样本地板（实测 " + Integer.valueOf(hits) + "）", hits >= 15);
        Assert.assertTrue("未命中样本地板（实测 " + Integer.valueOf(misses) + "）", misses >= 10);
    }
}
