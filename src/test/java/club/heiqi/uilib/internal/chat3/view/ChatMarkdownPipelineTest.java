package club.heiqi.uilib.internal.chat3.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownSpan;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;

/**
 * M5 接线本体单元测试(§ → span 转换 / 结构判据 / HUD 截断 / 缓存命中;规划 §三 M5、
 * §二之八 C6b)。
 *
 * <p><b>C6b 方案甲</b>：§ 在进 markdown 前转样式锚点 span 流（{@code toSpanStream}），
 * 本类 § 族锁按甲口径重定（乙′ 的「预清洗 + 输出后置桥」机制锁随机制退役）；§ 转换断言与
 * {@code TextLayoutService.parseSegments}/{@code TextStyle.applyFormat} 语义逐位同源。
 * 甲↔乙′ 的逐段迁移对账在 {@code ChatMarkdownSectionSpanMigrationLockTest}（C6b·3 起乙′
 * 机制已从生产拆除，其只读镜像随 oracle 住进该锁的 {@code RetiredBPrime} 嵌套类——本类
 * 不再挂桥/strip/coarse 直测锁）。</p>
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

    // ==================== C6b 甲：§ → span 输入转换族（预清洗与输出桥已整套退役；
    // 归位宪法不变：L1 零认知 §，MC 特有格式只在集成层作为输入转换存在）。====

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
}
