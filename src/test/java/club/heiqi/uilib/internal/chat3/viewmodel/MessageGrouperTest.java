package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;

import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;

/**
 * MessageGrouper 契约测试:相邻同发送者合并/不同发送者切断/系统消息切断且独立/自己判定/时间正序。
 */
public class MessageGrouperTest {

    private final MessageGrouper grouper = new MessageGrouper();

    @Test
    public void shouldMergeAdjacentSameSender() {
        List<ChatLineRecord> records = newestFirst(
                record("<Steve> 第二条", 2, 2000),
                record("<Steve> 第一条", 1, 1000));
        List<MessageGroupModel> groups = grouper.group(records, "Alex");

        Assert.assertEquals(1, groups.size());
        Assert.assertEquals("Steve", groups.get(0).getSender());
        Assert.assertEquals(MessageGroupModel.Alignment.OTHER_LEFT, groups.get(0).getAlignment());
        Assert.assertEquals(2, groups.get(0).getLines().size());
        Assert.assertEquals("第一条", groups.get(0).getLines().get(0).getRest());
        Assert.assertEquals("第二条", groups.get(0).getLines().get(1).getRest());
        Assert.assertEquals(2000, groups.get(0).getLatestMillis());
    }

    @Test
    public void shouldSplitOnDifferentSenders() {
        List<ChatLineRecord> records = newestFirst(
                record("<Bob> b2", 3, 3000),
                record("<Steve> s1", 2, 2000),
                record("<Steve> s0", 1, 1000));
        List<MessageGroupModel> groups = grouper.group(records, "Alex");

        Assert.assertEquals(2, groups.size());
        Assert.assertEquals("Steve", groups.get(0).getSender());
        Assert.assertEquals(2, groups.get(0).getLines().size());
        Assert.assertEquals("Bob", groups.get(1).getSender());
        Assert.assertEquals(1, groups.get(1).getLines().size());
    }

    @Test
    public void shouldCutMergeOnSystemMessages() {
        List<ChatLineRecord> records = newestFirst(
                record("<Steve> after", 3, 3000),
                record("[公告] 维护通知", 2, 2000),
                record("<Steve> before", 1, 1000));
        List<MessageGroupModel> groups = grouper.group(records, "Alex");

        Assert.assertEquals(3, groups.size());
        Assert.assertEquals("Steve", groups.get(0).getSender());
        Assert.assertEquals(1, groups.get(0).getLines().size());
        Assert.assertEquals(MessageGroupModel.Alignment.SYSTEM_CENTER, groups.get(1).getAlignment());
        Assert.assertNull(groups.get(1).getSender());
        Assert.assertEquals("[公告] 维护通知", groups.get(1).getLines().get(0).getRest());
        Assert.assertEquals("Steve", groups.get(2).getSender());
        Assert.assertEquals(1, groups.get(2).getLines().size());
    }

    @Test
    public void shouldMarkSelfAlignment() {
        List<ChatLineRecord> records = newestFirst(
                record("<Steve> hi", 1, 1000));
        List<MessageGroupModel> groups = grouper.group(records, "Steve");
        Assert.assertEquals(MessageGroupModel.Alignment.SELF_RIGHT, groups.get(0).getAlignment());
    }

    @Test
    public void shouldOutputTimeAscendingOrder() {
        List<ChatLineRecord> records = newestFirst(
                record("<A> 3", 3, 3000),
                record("<B> 2", 2, 2000),
                record("<A> 1", 1, 1000));
        List<MessageGroupModel> groups = grouper.group(records, null);
        Assert.assertEquals("A", groups.get(0).getSender());
        Assert.assertEquals("B", groups.get(1).getSender());
        Assert.assertEquals("A", groups.get(2).getSender());
    }

    // ==================== P1-3:120s 并组时间窗(设计稿 §3.3) ====================

    @Test
    public void shouldMergeSameSenderWithinTimeWindow() {
        // 间隔 119_999ms ≤ 120s → 并组
        List<ChatLineRecord> records = newestFirst(
                record("<Steve> b", 2, 2000L + MessageGrouper.MERGE_WINDOW_MILLIS - 1L),
                record("<Steve> a", 1, 2000L));
        List<MessageGroupModel> groups = grouper.group(records, "Alex");
        Assert.assertEquals("同发送者 120s 内并组", 1, groups.size());
        Assert.assertEquals(2, groups.get(0).getLines().size());
    }

    @Test
    public void shouldSplitSameSenderBeyondTimeWindow() {
        // 间隔 120_001ms > 120s → 断开开新组
        List<ChatLineRecord> records = newestFirst(
                record("<Steve> b", 2, 2000L + MessageGrouper.MERGE_WINDOW_MILLIS + 1L),
                record("<Steve> a", 1, 2000L));
        List<MessageGroupModel> groups = grouper.group(records, "Alex");
        Assert.assertEquals("同发送者 >120s 断开开新组", 2, groups.size());
        Assert.assertEquals("Steve", groups.get(0).getSender());
        Assert.assertEquals(1, groups.get(0).getLines().size());
        Assert.assertEquals("Steve", groups.get(1).getSender());
        Assert.assertEquals(1, groups.get(1).getLines().size());
    }

    @Test
    public void shouldMergeSameSenderAtExactWindowBoundary() {
        // 边界:间隔恰 120_000ms → 并组(≤ 判定)
        List<ChatLineRecord> records = newestFirst(
                record("<Steve> b", 2, 2000L + MessageGrouper.MERGE_WINDOW_MILLIS),
                record("<Steve> a", 1, 2000L));
        List<MessageGroupModel> groups = grouper.group(records, "Alex");
        Assert.assertEquals("边界值 120s 仍并组", 1, groups.size());
        Assert.assertEquals(2, groups.get(0).getLines().size());
    }

    @Test
    public void shouldSplitMixedSenderRunWithWindowBetweenAdjacentMessages() {
        // 时间窗只看相邻消息:A(0s) → A(200s) 断开;中间夹 B(100s) 则 B 单独一组
        List<ChatLineRecord> records = newestFirst(
                record("<Steve> a3", 3, 200_000L),
                record("<Bob> b1", 2, 100_000L),
                record("<Steve> a1", 1, 0L));
        List<MessageGroupModel> groups = grouper.group(records, "Alex");
        Assert.assertEquals(3, groups.size());
        Assert.assertEquals("Steve", groups.get(0).getSender());
        Assert.assertEquals(1, groups.get(0).getLines().size());
        Assert.assertEquals("Bob", groups.get(1).getSender());
        Assert.assertEquals("Steve", groups.get(2).getSender());
    }

// ==================== C7：结构优先、正则兜底 ====================

    /** 结构命中：sender 与本体直取 {@code chat.type.text} 结构参数，正则不参与。 */
    @Test
    public void prefersVanillaChatTypeTextStructureOverRegex() {
        ChatLineRecord record = new ChatLineRecord(new ChatComponentTranslation("chat.type.text",
                new Object[] {new ChatComponentText("Bob"), "第一行\n第二行 *粗*"}), 1, 1000L);
        List<MessageGroupModel> groups = grouper.group(Arrays.asList(record), "Alex");

        Assert.assertEquals(1, groups.size());
        Assert.assertEquals("Bob", groups.get(0).getSender());
        Assert.assertEquals(MessageGroupModel.Alignment.OTHER_LEFT, groups.get(0).getAlignment());
        MessageGroupModel.GroupLine line = groups.get(0).getLines().get(0);
        Assert.assertTrue("本体来源标记必须是结构通道", line.isStructured());
        Assert.assertEquals("本体 = getFormatArgs()[1] 原文（连换行与 markdown 定界都不动）",
                "第一行\n第二行 *粗*", line.getRest());
    }

    /** 结构命中路径必须零组件文本渲染（不读 plain/formatted，翻译组件上那是语言表查找）。 */
    @Test
    public void structuralHitNeverRendersComponentText() {
        RenderCountingComponent root = new RenderCountingComponent("chat.type.text",
                new Object[] {new ChatComponentText("Bob"), "hi"});
        grouper.group(Arrays.asList(new ChatLineRecord(root, 1, 1000L)), "Alex");
        Assert.assertEquals("结构命中路径的渲染入口调用数必须 0: " + root.report(), 0, root.calls);
        // 正对照（反空跑）：同一对象亲手渲染一次，计数器必须动
        root.getUnformattedText();
        Assert.assertTrue("计数器正对照失效: " + root.report(), root.calls > 0);
    }

    /** 结构行与正则行同发送者照常并组（结构优先不改合并语义）。 */
    @Test
    public void structuralAndRegexLinesFromSameSenderStillMerge() {
        List<ChatLineRecord> records = newestFirst(
                new ChatLineRecord(new ChatComponentText("<Bob> 旧的那条（非 vanilla 形）"), 2, 2000L),
                new ChatLineRecord(new ChatComponentTranslation("chat.type.text",
                        new Object[] {new ChatComponentText("Bob"), "新的那条"}), 1, 1000L));
        List<MessageGroupModel> groups = grouper.group(records, "Alex");

        Assert.assertEquals("同发送者仍并成一组的两个气泡行", 1, groups.size());
        Assert.assertEquals(2, groups.get(0).getLines().size());
        Assert.assertTrue("首行走结构通道", groups.get(0).getLines().get(0).isStructured());
        Assert.assertEquals("新的那条", groups.get(0).getLines().get(0).getRest());
        Assert.assertFalse("次行走正则兜底通道", groups.get(0).getLines().get(1).isStructured());
        Assert.assertEquals("旧的那条（非 vanilla 形）", groups.get(0).getLines().get(1).getRest());
    }

    /**
     * 已知边界（设计行为，不是缺陷）：非 vanilla 形（自定义 key / 改写 chat.type.text）走正则兜底时
     * 内容可能带 §，本体照旧是 plain 的 rest，§ 原样带着走——交给 markdown 当普通字符显示。
     */
    @Test
    public void fallbackContentKeepsSectionCodeVerbatim() {
        String section = String.valueOf((char) 0x00A7);
        List<ChatLineRecord> records = newestFirst(
                new ChatLineRecord(new ChatComponentText("<Bob> " + section + "a- item"), 1, 1000L));
        List<MessageGroupModel> groups = grouper.group(records, "Alex");

        Assert.assertEquals(1, groups.size());
        MessageGroupModel.GroupLine line = groups.get(0).getLines().get(0);
        Assert.assertFalse("非 vanilla 形不配结构命中", line.isStructured());
        Assert.assertEquals("§ 原样保留在兜底本体里（不剥不转）", section + "a- item", line.getRest());
    }

    /** 渲染入口计数组件：{@code getUnformattedText()}/{@code getFormattedText()} 都是 final，
     *  但两者第一步都是 {@code iterator()}，覆写它即覆盖两条入口。 */
    private static final class RenderCountingComponent extends ChatComponentTranslation {

        private int calls;

        RenderCountingComponent(String key, Object[] args) {
            super(key, args);
        }

        @Override
        public java.util.Iterator<net.minecraft.util.IChatComponent> iterator() {
            calls++;
            return super.iterator();
        }

        @Override
        public String getUnformattedTextForChat() {
            calls++;
            return super.getUnformattedTextForChat();
        }

        String report() {
            return "calls=" + calls;
        }
    }

    private static ChatLineRecord record(String text, int id, long millis) {
        return new ChatLineRecord(new ChatComponentText(text), id, millis);
    }

    private static List<ChatLineRecord> newestFirst(ChatLineRecord... records) {
        return Arrays.asList(records); // 参数即 index0=最新
    }
}
