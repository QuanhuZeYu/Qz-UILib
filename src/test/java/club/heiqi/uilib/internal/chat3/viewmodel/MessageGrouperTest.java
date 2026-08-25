package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;

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

    private static ChatLineRecord record(String text, int id, long millis) {
        return new ChatLineRecord(new ChatComponentText(text), id, millis);
    }

    private static List<ChatLineRecord> newestFirst(ChatLineRecord... records) {
        return Arrays.asList(records); // 参数即 index0=最新
    }
}
