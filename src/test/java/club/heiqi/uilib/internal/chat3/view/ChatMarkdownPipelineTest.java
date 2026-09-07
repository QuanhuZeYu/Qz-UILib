package club.heiqi.uilib.internal.chat3.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;

/**
 * M5 接线本体单元测试(结构判据 / HUD 截断 / 缓存命中 / § 字面语义;规划 §三 M5、§二之八 C6b→C7)。
 *
 * <p><b>C7 划界</b>：本层不再有任何 § 机制——C6b 方案甲的「§ → 样式锚点 span 流」输入转换器
 * ({@code toSpanStream}) 随「markdown 路径不解释 §」整套退役，入口回到
 * {@code MarkdownDocument.parse(String)}。故本类的 § 族锁改钉「§ 当普通字符」的 chat3 侧后果
 * （定案 4 + 已知边界：非 vanilla 形带进来的 § 字面显示、零样式效果、行首 § 吃掉块标记），
 * 并钉缓存单轨。甲↔乙′ 迁移等价锁 ({@code ChatMarkdownSectionSpanMigrationLockTest}) 与其
 * 只读镜像 {@code RetiredBPrime}、比较尺 {@code StyleFieldsKey} 因「§ 与 markdown 共存输入」
 * 这一对象被划界消灭而一并删除。「§ 是普通字符」的无条件行为锁在 L1 侧
 * {@code MarkdownSectionCodeIsPlainTextLockTest}。</p>
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

    // ==================== C7：markdown 路径对 § 零分支（已知边界 + 缓存单轨） ====================

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
    /** § 本体（(char) 运行期拼装，仓内既有惯例）。 */
    private static final String SECTION = String.valueOf((char) 0x00A7);

    private static String visible(List<ChatMarkdownPipeline.RenderedLine> lines) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            for (TextSegment segment : lines.get(i).segments()) {
                out.append(segment.isLatex() ? "\u27e6?\u27e7" : segment.getText());
            }
        }
        return out.toString();
    }

    private static boolean anyNonBaseColor(List<ChatMarkdownPipeline.RenderedLine> lines) {
        for (ChatMarkdownPipeline.RenderedLine line : lines) {
            for (TextSegment segment : line.segments()) {
                if (segment.getStyle().getColor() != WHITE) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 已知边界（设计行为，不是缺陷）：非 vanilla 聊天格式（服务端自定义 key 或改写
     * {@code chat.type.text}）走正则兜底时内容可能带 §，markdown <b>原样显示字面 §</b>——
     * 行首 § 还会吃掉块标记（{@code §a- x} 是段落不是列表）。配「同一行去掉行首 § 照常成列表」
     * 的正例对照，防本锁被误写成「一切恒字面」的空断言。
     */
    @Test
    public void sectionCodesAreLiteralTextWithZeroStyleEffect() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> hit = pipeline.layout(
                SECTION + "a- x", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals("行首 § 吃掉列表标记 ⇒ 单段落行", 1, hit.size());
        Assert.assertEquals("§ 与其后字符原样进文本，不做任何跳跃切片", SECTION + "a- x",
                visible(hit));
        Assert.assertEquals("单段（§ 不上色也就不再切段）", 1, hit.get(0).segments().size());
        Assert.assertEquals("§ 零样式效果：颜色恒基色", WHITE,
                hit.get(0).segments().get(0).getStyle().getColor());
        Assert.assertEquals("§a 的 a 也不得被当样式位读", FontType.NORMAL,
                hit.get(0).segments().get(0).getStyle().getFontType());

        // 正例对照：同一行去掉行首 § 后照常命中列表（证明上面的「不命中」确是 § 的后果）
        List<ChatMarkdownPipeline.RenderedLine> control = pipeline.layout(
                "- x", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals("\u2022 x", visible(control));
        Assert.assertEquals("标记段 + 正文段", 2, control.get(0).segments().size());
    }

    /** 行中 § 同样零处理：字面进文本、不上色、不切段（旧「桥上色」口径随机作退役）。 */
    @Test
    public void midLineSectionCodesStayLiteralAndColorNothing() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> out = pipeline.layout(
                "甲 " + SECTION + "c红 " + SECTION + "f乙", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertEquals("逐字符原样: " + visible(out),
                "甲 " + SECTION + "c红 " + SECTION + "f乙", visible(out));
        Assert.assertFalse("不得出现任何非基色（§ 不上色）: " + visible(out), anyNonBaseColor(out));
    }

    /**
     * 缓存单轨锁（C7 第 8 条）：两级 key = <b>最终喂进 {@code MarkdownDocument.parse} 的那个
     * 字符串</b>，与 markdown 输入同源同值（装配处三条分支只交这一个值）。转换器退役后
     * 「同 key 不同语义」在结构上不存在；而「§ 版 vs 纯净版」在 C7 下<b>本就语义不同</b>
     * （段落 vs 列表），分占条目是正确后果，不是去重回退。
     */
    @Test
    public void cacheKeyIsExactlyTheStringFedToMarkdown() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        List<ChatMarkdownPipeline.RenderedLine> secForm = pipeline.layout(
                SECTION + "a- x", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertSame("同文本同参 ⇒ 同实例（每帧零解析不回归）", secForm,
                pipeline.layout(SECTION + "a- x", WHITE, 4000, 13, null, PASSTHROUGH));
        List<ChatMarkdownPipeline.RenderedLine> plainForm = pipeline.layout(
                "- x", WHITE, 4000, 13, null, PASSTHROUGH);
        Assert.assertNotSame("§ 版与纯净版语义不同（段落 vs 列表），必须分占条目", secForm, plainForm);
        Assert.assertNotEquals("不存在「转换后等值 ⇒ 共享条目」的第二轨",
                visible(secForm), visible(plainForm));
        Assert.assertNotSame("baseColor 参与 key", secForm,
                pipeline.layout(SECTION + "a- x", 0xFF101010, 4000, 13, null, PASSTHROUGH));
        // 逻辑行接缝（测试工厂）与视觉行同源：同一个 parse 输入读同一个结果
        List<MarkdownLayoutLine> logical = pipeline.logicalForTest(SECTION + "a- x", WHITE);
        Assert.assertEquals(1, logical.size());
        Assert.assertEquals("行身份 = TEXT（无 § 转换 ⇒ L1 看见的就是带 § 的字面行）",
                MarkdownLayoutLine.Kind.TEXT, logical.get(0).getKind());
        Assert.assertEquals(SECTION + "a- x", logical.get(0).getSegments().get(0).getText());
    }

    /**
     * 空/null 入参的形状契约（实测钉现状，划界前后同形）：空文档不产任何逻辑行，也不产视觉行。
     * {@code layout} 的 javadoc 曾写「空文本 → 单空行」，与实测不符——C6b 的 span 路同样是
     * 空流 → 空块表 → 0 行，故本例把现状钉住并就地更正该句（不是 C7 引入的差异）。
     */
    @Test
    public void emptyAndNullInputsProduceNoLinesBothBeforeAndAfterC7() {
        ChatMarkdownPipeline pipeline = new ChatMarkdownPipeline();
        Assert.assertTrue(pipeline.logicalForTest("", WHITE).isEmpty());
        Assert.assertTrue(pipeline.logicalForTest(null, WHITE).isEmpty());
        Assert.assertTrue(pipeline.layout("", WHITE, 4000, 13, null, PASSTHROUGH).isEmpty());
        Assert.assertTrue(pipeline.layout(null, WHITE, 4000, 13, null, PASSTHROUGH).isEmpty());
        // 正例对照：非空文本确实开行，否则上面四个空判是空跑
        Assert.assertEquals(1, pipeline.layout("x", WHITE, 4000, 13, null, PASSTHROUGH).size());
    }
}