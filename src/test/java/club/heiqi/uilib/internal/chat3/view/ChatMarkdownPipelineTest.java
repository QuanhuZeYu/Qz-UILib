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
import club.heiqi.uilib.font.layout.markdown.MarkdownSpan;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;

/**
 * M5 接线本体单元测试(§ → span 转换 / 结构判据 / HUD 截断 / 缓存命中;规划 §三 M5、
 * §二之八 C6b)。
 *
 * <p><b>C6b 方案甲</b>：§ 在进 markdown 前转样式锚点 span 流（{@code toSpanStream}），
 * 本类 § 族锁按甲口径重定（乙′ 的「预清洗 + 输出后置桥」机制锁随机制退役）；§ 转换断言与
 * {@code TextLayoutService.parseSegments}/{@code TextStyle.applyFormat} 语义逐位同源。
 * 甲↔乙′ 的逐段迁移对账在 {@code ChatMarkdownSectionSpanMigrationLockTest}。桥/strip/coarse
 * 直测用例（本类旧段）只存活到 C6b·3 拆除批（其间它们是被迁移锁引用的退役 oracle）。</p>
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

    // ==================== C6b 甲：§ → span 输入转换族（预清洗已退役；归位宪法：L1 零认知 §）。====
    // 本段之下的 stripLeadingSectionCodes/markerView/coarse 直测与桥直测 = 已退役乙′ 机制的
    // 迁移对照 oracle 锁，只存活到 C6b·3 拆除批（机制本体与这些锁同批删）。

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

    /**
     * 归位目的锁（C6b 甲口径）：§a- 玩家列表行的码在进 markdown 前转成样式锚点，文本行首
     * 恒纯「- x」⇒ L1 判列表项；<b>行首色不再随旧清洗丢弃</b>（乙′ 命中块标记即消费色码，
     * 甲 色码转样式由服务端色存活——规划 §二之八 C6b 差异清单第 1 条）。段流不残留任何
     * § 字面（转换侧无 § 进文本，桥已退役）。输入侧结构（kind/标记段文本）与乙′ 逐位一致。
     */
    @Test
    public void sectionPrefixedListLineStillRendersAsListItemThroughPipeline() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> out = pipeline.layout(
                "\u00a7a- \u73a9\u5bb6\u5217\u8868\u884c", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals(1, out.size());
        List<TextSegment> segments = out.get(0).segments();
        Assert.assertEquals("\u2022 " + "\u73a9\u5bb6\u5217\u8868\u884c", visible(out));
        Assert.assertEquals("标记段", "\u2022 ", segments.get(0).getText());
        Assert.assertTrue("转换后段流不得残留 § 字面: " + visible(out),
                visible(out).indexOf('\u00a7') < 0);
        Assert.assertEquals("甲：行首 §a 转锚点存活为服务端绿（旧乙′随清洗丢弃）",
                0xFF55FF55, segments.get(1).getStyle().getColor());
        Assert.assertEquals("合成标记段恒 caller 基色（白）", WHITE,
                segments.get(0).getStyle().getColor());
    }

    /**
     * § 族语义锁（C6b 甲改判，取代乙′ 的「C4 前语义恢复」口径）：§f- item / §f  - item /
     * §f§l- item / §f §a- item 的码对进 markdown 前全部消化 ⇒ 文本行首恒纯「- item」⇒
     * L1 判列表项——「• item」可见形与乙′ 一致；<b>甲 新增</b>：§f 白 = 显式色（引用等块级
     * 色让位）、§l 粗体位在列表行存活（乙′ 命中即连样式位一并丢弃）、§a 绿存活。
     * §c 纯文本行 = 锚点着色（旧乙′ 经桥、结果同色）。改判理由：任务书第三部分第 4 条。
     */
    @Test
    public void sectionFamilySemanticsUnderSpanStreamConversionC6b() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> f1 = pipeline.layout(
                "\u00a7f- item", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals("\u2022 item", visible(f1));
        Assert.assertEquals("§f = 显式基础白（甲：色进锚点，非旧口径的『随消费消失』）",
                WHITE, f1.get(0).segments().get(1).getStyle().getColor());
        Assert.assertTrue("§f 显式色位（非显式底色与 § 着色的分尺点）",
                f1.get(0).segments().get(1).getStyle().isColorExplicit());
        List<ChatMarkdownPipeline.RenderedLine> f2 = pipeline.layout(
                "\u00a7f  - item", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals("≤3 空格进视图 ⇒ 列表项（与 CommonMark 缩进列一致）",
                "\u2022 item", visible(f2));
        List<ChatMarkdownPipeline.RenderedLine> f3 = pipeline.layout(
                "\u00a7f\u00a7l- item", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals("\u2022 item", visible(f3));
        Assert.assertEquals("§l 样式位在列表行存活（乙′ 命中行随剥丢弃，甲 不再丢）",
                club.heiqi.uilib.font.FontType.BOLD,
                f3.get(0).segments().get(1).getStyle().getFontType());
        List<ChatMarkdownPipeline.RenderedLine> f4 = pipeline.layout(
                "\u00a7f \u00a7a- item", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals("\u2022 item", visible(f4));
        Assert.assertEquals(0xFF55FF55, f4.get(0).segments().get(1).getStyle().getColor());
        List<ChatMarkdownPipeline.RenderedLine> plain = pipeline.layout(
                "\u00a7c\u7eaf\u6587\u672c\u884c", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals("\u7eaf\u6587\u672c\u884c", visible(plain));
        Assert.assertEquals(0xFFFF5555, plain.get(0).segments().get(0).getStyle().getColor());
    }

    /**
     * 行为变化锁（C6b 甲改判，任务书第三部分第 4 条点名）：§f + 4 空格 + 「- item」——
     * §f 转样式后文本以 4 空格开头 ⇒ L1 按 CommonMark 0.30 §4.4 判<b>缩进代码块</b>。
     * 这才是主流正确结果：乙′ 的「段落字面」是为保行首色做的妥协（不剥则 § 挡在行首使块
     * 判据失明，剥了则 ind&gt;3 又不可消费——两难皆因清洗看不到块上下文），甲 把色存进
     * 锚点后文本恒纯，妥协随机制一并退役。§f 白随锚点存活（显式色），衬底/行身份走 CODE。
     */
    @Test
    public void leadingCodePlusFourSpaceMarkerBecomesIndentedCodeBlockC6b() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> out = pipeline.layout(
                "\u00a7f    - item", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals(1, out.size());
        Assert.assertTrue("身份 = 缩进代码块（CommonMark §4.4 主流口径）", out.get(0).isCode());
        Assert.assertFalse(out.get(0).isRule());
        // 缩进代码正文剥 4 空格列（C1a 与 String 直连「    - deep」→「- deep」同口径）
        Assert.assertEquals("- item", visible(out));
        Assert.assertEquals("缩进代码不产引用/列表缩进", 0, out.get(0).leftInsetPx());
        Assert.assertTrue("§f 显式白随锚点存活（CODE 路 applied 同款定尺）",
                out.get(0).segments().get(0).getStyle().isColorExplicit());
        Assert.assertEquals(WHITE,
                out.get(0).segments().get(0).getStyle().getColor());
    }

    /**
     * 行中 § 码锁（C6b 改判名：原 midLineSectionCodesAreStillBridgedToColor）：行中码在
     * 输入转换处变身为样式锚点（不再「后置桥上色」）——逐段可见形与旧桥逐位一致
     * （「甲 」基色 + 「红」MC 红；M4-fix F4 的另一半语义不变，只是消费点归位到进
     * markdown 之前）。
     */
    @Test
    public void midLineSectionCodesBecomeSpanAnchorsBeforeParse() {
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
     * 行首色保住锁（C6b 改判名：原 leadingColorSemanticsSurviveOnNonHitLines——「命中/
     * 未命中」概念随预清洗退役）：§c红色警告 的色经锚点存活为 MC 红；复合形态
     * 「§a- §citem」= 列表项 + 红内容——<b>与乙′ 逐段逐色逐位等值</b>（该行两侧行首码都
     * 消失、行中码都上色；差异仅在甲 下 §a 的绿色会停在被列表标记吃掉的「- 」区间上，
     * 见迁移等价锁专项）。可见形不变，消费点从桥移到转换。
     */
    @Test
    public void leadingColorSemanticsSurviveViaSpanAnchors() {
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
     * 缓存口径锁（C6b 重定，任务书第一部分第 4 条）：两级 key 改吃<b>原文</b>（displayText
     * 未转换形态）——转换 = (原文, baseColor) 的纯函数、只在未命中时做，baseColor 与配色代
     * 指纹（cacheKey 吃次级色/链接色现值）都在 key 上 ⇒ <b>同 key ⇒ 同一语义输入</b>，
     * 结构上无「同 key 不同语义」；「不同语义共享条目」同样不可能。副作用 = 「§a- x」与「- x」
     * 不再共享条目（乙′ 靠清洗后同串去重）——只回退去重效率，语义零损失，且本例两者产物本就
     * 不等值（甲 下行首 §a 绿色存活），旧「同串」前提已不成立。
     */
    @Test
    public void cacheKeyUsesRawTextAndNeverSharesAcrossSemantics() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> secForm = pipeline.layout(
                "\u00a7a- x", WHITE, 4000, 13, null, PASSTHROUGH);
        List<ChatMarkdownPipeline.RenderedLine> again = pipeline.layout(
                "\u00a7a- x", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertSame("同原文同参 ⇒ 同实例（每帧零解析不回归）", secForm, again);
        List<ChatMarkdownPipeline.RenderedLine> plainForm = pipeline.layout(
                "- x", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertNotSame("甲 口径：§ 版与纯净版分占条目（去重回退，非串味）",
                secForm, plainForm);
        Assert.assertEquals(0xFF55FF55, secForm.get(0).segments().get(1).getStyle().getColor());
        Assert.assertEquals(WHITE, plainForm.get(0).segments().get(1).getStyle().getColor());
        List<ChatMarkdownPipeline.RenderedLine> other = pipeline.layout(
                "\u00a7b- y", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals("\u2022 y", visible(other));
        Assert.assertSame("串味防御：夹读后回读仍是自己的产物", secForm,
                pipeline.layout("\u00a7a- x", WHITE, 4000, 13, null, PASSTHROUGH));
        List<ChatMarkdownPipeline.RenderedLine> otherBase = pipeline.layout(
                "\u00a7a- x", 0xFF101010, 4000, 13, null, PASSTHROUGH);
        Assert.assertNotSame("baseColor 参与 key，不得与白底条目共享", secForm, otherBase);
        Assert.assertEquals("\u2022 x", visible(otherBase));
        // §r 形态：转换后与纯净版语义等值（锚点底色 = 基色、非显式），但 key 不同 ⇒ 两条
        // 目不同实例、逐段等值——「同语义两条目」= 允许的去重回退，锁死其不串语义。
        List<ChatMarkdownPipeline.RenderedLine> resetForm = pipeline.layout(
                "\u00a7r- x", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertNotSame("等值语义不同原文 ⇒ 不共享条目（无「同 key 不同语义」的另一半）",
                plainForm, resetForm);
        Assert.assertEquals(visible(plainForm), visible(resetForm));
        Assert.assertEquals(plainForm.get(0).segments().get(1).getStyle().getColor(),
                resetForm.get(0).segments().get(1).getStyle().getColor());
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

    // ==================== C6b § → span 转换器单元锁（§ 族按甲改写主体） ====================

    private static TextStyle whiteBase() {
        TextStyle s = new TextStyle();
        s.setColor(WHITE);
        return s;
    }

    private static int colorOf(TextStyle s) {
        return s.getColor();
    }

    /** 码对逐码消费、§ 不进 span 文本（与 splitRunsOnFormatCodes/parseSegments 同源语义）。 */
    @Test
    public void converterSplitsCodePairsIntoStyledSpansWithoutSectionChars() {
        List<MarkdownSpan> spans = ChatMarkdownPipeline.toSpanStream(
                "\u00a7c\u7532\u00a7f\u4e59", whiteBase());
        Assert.assertEquals(2, spans.size());
        Assert.assertEquals("\u7532", spans.get(0).getText());
        Assert.assertEquals(0xFFFF5555, colorOf(spans.get(0).getBaseStyle()));
        Assert.assertTrue("§ 着色 = 显式色（C6b 定序的分尺）",
                spans.get(0).getBaseStyle().isColorExplicit());
        Assert.assertEquals("\u4e59", spans.get(1).getText());
        Assert.assertEquals(WHITE, colorOf(spans.get(1).getBaseStyle()));
        Assert.assertTrue("\u00a7f 也记显式（引用内 §f 压引用色，同旧桥）",
                spans.get(1).getBaseStyle().isColorExplicit());
        // 连续码 §c§l 逐对消费、色清样式位再叠加（MC 语义，applyFormat 原生行为）
        List<MarkdownSpan> pair = ChatMarkdownPipeline.toSpanStream(
                "\u00a7c\u00a7l\u7532", whiteBase());
        Assert.assertEquals(1, pair.size());
        Assert.assertEquals(0xFFFF5555, colorOf(pair.get(0).getBaseStyle()));
        Assert.assertEquals(FontType.BOLD, pair.get(0).getBaseStyle().getFontType());
        // 色码清先前 § 样式位（与 parseSegments 同源）：§l甲§c乙 ⇒ 乙红不粗
        List<MarkdownSpan> clears = ChatMarkdownPipeline.toSpanStream(
                "\u00a7l\u7532\u00a7c\u4e59", whiteBase());
        Assert.assertEquals(FontType.BOLD,
                clears.get(0).getBaseStyle().getFontType());
        Assert.assertEquals("\u00a7c 后 §l 位被色码重置（MC 语义）", FontType.NORMAL,
                clears.get(1).getBaseStyle().getFontType());
    }

    /** 起点样式 = 非显式底色：无 § 着色的文本让位块级色（引用降色），与旧桥零改动段同色。 */
    @Test
    public void converterStartStyleIsNonExplicitCallerBase() {
        List<MarkdownSpan> spans = ChatMarkdownPipeline.toSpanStream(
                "plain \u6587\u672c", whiteBase());
        Assert.assertEquals(1, spans.size());
        Assert.assertEquals(WHITE, colorOf(spans.get(0).getBaseStyle()));
        Assert.assertFalse("底色 span 非显式（甲 定序：不覆盖块级色）",
                spans.get(0).getBaseStyle().isColorExplicit());
        // §r 重置回「非显式底色」：引用内 §c甲§r乙 的乙让位引用色 = 旧桥 resetAll(段起色) 同色
        List<MarkdownSpan> resets = ChatMarkdownPipeline.toSpanStream(
                "\u00a7c\u7532\u00a7r\u4e59", whiteBase());
        Assert.assertTrue(resets.get(0).getBaseStyle().isColorExplicit());
        Assert.assertFalse("§r 后回落非显式", resets.get(1).getBaseStyle().isColorExplicit());
        Assert.assertEquals(WHITE, colorOf(resets.get(1).getBaseStyle()));
    }

    /** 行界与孤立 § 不可吞：紧邻 \n/\r 的 § 与消息尾 § 按字面进文本（换行 = L1 块检测材料）。 */
    @Test
    public void converterNeverEatsLineBoundariesOrLoneSection() {
        List<MarkdownSpan> crlf = ChatMarkdownPipeline.toSpanStream(
                "\u7532\u00a7\n\u4e59", whiteBase());
        Assert.assertEquals(1, crlf.size());
        Assert.assertEquals("\u7532\u00a7\n\u4e59", crlf.get(0).getText());
        List<MarkdownSpan> tail = ChatMarkdownPipeline.toSpanStream(
                "\u7532\u00a7", whiteBase());
        Assert.assertEquals("\u7532\u00a7", tail.get(0).getText());
        List<MarkdownSpan> cr = ChatMarkdownPipeline.toSpanStream(
                "\u7532\u00a7\r\u4e59", whiteBase());
        Assert.assertEquals("\u7532\u00a7\r\u4e59", cr.get(0).getText());
    }

    /** 未知码对走 applyFormat default（= 重置、两字符消费）——与 L0 parseSegments 同形，非另造。 */
    @Test
    public void converterConsumesUnknownPairAsResetLikeL0() {
        List<MarkdownSpan> unknown = ChatMarkdownPipeline.toSpanStream(
                "\u00a7c\u7532\u00a7z\u4e59", whiteBase());
        Assert.assertEquals(2, unknown.size());
        Assert.assertEquals("\u4e59", unknown.get(1).getText());
        Assert.assertFalse("\u00a7z default=重置 ⇒ 非显式底色", 
                unknown.get(1).getBaseStyle().isColorExplicit());
        Assert.assertEquals(WHITE, colorOf(unknown.get(1).getBaseStyle()));
    }

    /** 大小写同义 + 空文本空流。 */
    @Test
    public void converterIsCaseInsensitiveAndEmptySafe() {
        List<MarkdownSpan> upper = ChatMarkdownPipeline.toSpanStream(
                "\u00a7F\u7532", whiteBase());
        Assert.assertEquals(WHITE, colorOf(upper.get(0).getBaseStyle()));
        Assert.assertTrue(upper.get(0).getBaseStyle().isColorExplicit());
        Assert.assertTrue(ChatMarkdownPipeline.toSpanStream("", whiteBase()).isEmpty());
    }

    // ==================== 桥退役前实证：甲 段流上桥 = no-op（任务书第三部分第 3 条） ====================

    /** 甲 产物段文本里 § 只可能作行尾孤立体存在（无可消费码对）⇒ 桥结构上无事可做。 */
    @Test
    public void convertedStreamLeavesNoConsumableSectionPairsForTheBridge() {
        String[] corpus = {
            "\u00a7a- x", "\u00a7c\u7532\u00a7f\u4e59", "> \u00a7c\u7532",
            "```\n\u00a7cfoo\n```", "```\n\u00a7c# \u6807\n```", "> \u00a7a- x",
            "\u00a7c\u7532\u00a7r\u4e59", "\u00a7c\u00a7l\u7532", "\u7532\u00a7", "\u00a7f    - item",
            "\u00a7z- x", "\u00a7c**a**b", "\u00a7c\u7532\n\u4e59", "> \u00a7c\u7532\u00a7r\u4e59",
            "\u00a7a- \u73a9\u5bb6\u5217\u8868\u884c",
            "\u00a7c\u7ea2\u8272\u8b66\u544a \u00a7fplain tail mixed English 123",
            "\u666e\u901a\u6bb5\u843d", "- \u65e0 § \u5217\u8868", "# \u6807\u9898",
        };
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        for (int i = 0; i < corpus.length; i++) {
            String msg = corpus[i];
            List<MarkdownLayoutLine> logical = pipeline.logicalForTest(msg, WHITE);
            for (int k = 0; k < logical.size(); k++) {
                List<TextSegment> segments = logical.get(k).getSegments();
                for (int s = 0; s < segments.size(); s++) {
                    TextSegment segment = segments.get(s);
                    if (segment.isLatex()) {
                        Assert.fail("latex 源含 § 说明消费点漏了（甲在进 markdown 前已全部消化）: "
                                + msg);
                    }
                    String text = segment.getText();
                    int at = text.indexOf('\u00a7');
                    if (at >= 0) {
                        Assert.assertTrue("孤立 § 只许在段文本末位且全段仅此一枚: " + msg
                                        + " / " + text,
                                at == text.length() - 1 && text.indexOf('\u00a7', at + 1) < 0);
                    }
                    // 桥 no-op 实证：调旧桥后段流逐段等值（文本 + 全样式字段）
                    List<TextSegment> bridged = ChatMarkdownPipeline.bridgeSectionCodes(segments);
                    Assert.assertEquals("桥改段数 = 桥未 no-op: " + msg, segments.size(), bridged.size());
                    for (int b = 0; b < segments.size(); b++) {
                        Assert.assertEquals("桥改文本: " + msg,
                                segments.get(b).getText(), bridged.get(b).getText());
                        Assert.assertEquals("桥改样式: " + msg,
                                StyleFieldsKey.of(segments.get(b).getStyle()),
                                StyleFieldsKey.of(bridged.get(b).getStyle()));
                    }
                }
            }
        }
    }
}

