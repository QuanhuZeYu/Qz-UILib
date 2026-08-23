package club.heiqi.uilib.internal.chat3.input;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

/**
 * ChatSentHistory 契约测试:时间序尾插/相邻重复跳过/光标空槽/回显/上限/同步。
 */
public class ChatSentHistoryTest {

    @Test
    public void shouldAppendAndSkipAdjacentDuplicates() {
        ChatSentHistory history = new ChatSentHistory();
        history.add("a");
        history.add("a"); // 相邻重复跳过
        history.add("b");
        Assert.assertEquals(Arrays.asList("a", "b"), history.snapshot());
    }

    @Test
    public void shouldRecallWithEmptySlotSemantics() {
        ChatSentHistory history = new ChatSentHistory();
        Assert.assertEquals("空历史回显空串", "", history.recall(-1));

        history.add("first");
        history.add("second");
        // 光标初始 = size(空槽)
        Assert.assertEquals("上一条 = 最新", "second", history.recall(-1));
        Assert.assertEquals("再上一条 = 更旧", "first", history.recall(-1));
        Assert.assertEquals("到顶后停住", "first", history.recall(-1));
        Assert.assertEquals("下一条", "second", history.recall(1));
        Assert.assertEquals("下到空槽返回空串", "", history.recall(1));
        Assert.assertEquals("空槽再上 = 最新", "second", history.recall(-1));
    }

    @Test
    public void shouldResetCursor() {
        ChatSentHistory history = new ChatSentHistory();
        history.add("a");
        history.recall(-1);
        history.resetCursor();
        Assert.assertEquals("复位空槽后回显空串", "", history.recall(1));
        Assert.assertEquals("a", history.recall(-1));
    }

    @Test
    public void shouldCapEntries() {
        ChatSentHistory history = new ChatSentHistory();
        for (int i = 0; i < ChatSentHistory.MAX_ENTRIES + 10; i++) {
            history.add("msg" + i);
        }
        Assert.assertEquals(ChatSentHistory.MAX_ENTRIES, history.size());
        Assert.assertEquals("最旧的被裁", "msg10", history.snapshot().get(0));
    }

    @Test
    public void shouldSyncFromVanillaList() {
        ChatSentHistory history = new ChatSentHistory();
        history.add("old");
        history.syncFrom(Arrays.asList("v1", "v2", "v3"));
        Assert.assertEquals(Arrays.asList("v1", "v2", "v3"), history.snapshot());
        Assert.assertEquals("同步后光标复位空槽", "", history.recall(1));
        Assert.assertEquals("v3", history.recall(-1));
    }
}
