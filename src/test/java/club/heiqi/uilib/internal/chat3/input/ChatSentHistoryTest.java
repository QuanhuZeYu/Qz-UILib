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

    @Test
    public void shouldRestoreDraftWhenReturningToBottom() {
        ChatSentHistory history = new ChatSentHistory();
        history.add("first");
        history.add("second");

        // 首次从空槽翻入历史:暂存当前输入(原版 historyBuffer)
        Assert.assertEquals("second", history.recall(-1, "草稿文本"));
        Assert.assertEquals("first", history.recall(-1, "草稿文本"));
        Assert.assertEquals("翻回一格仍是历史", "second", history.recall(1, "草稿文本"));
        Assert.assertEquals("翻回最底恢复暂存草稿而非清空", "草稿文本", history.recall(1, "草稿文本"));
        // 恢复后暂存清空:再翻回最底无草稿时返回空串
        Assert.assertEquals("", history.recall(1, ""));
        // 再次进入历史:重新暂存当前输入
        Assert.assertEquals("second", history.recall(-1, "新草稿"));
        Assert.assertEquals("新草稿", history.recall(1, "新草稿"));
    }

    @Test
    public void shouldKeepFirstStashAndClearOnReset() {
        ChatSentHistory history = new ChatSentHistory();
        history.add("only");
        // 翻入历史后中途再翻不覆盖首份暂存(原版 historyBuffer 语义)
        Assert.assertEquals("only", history.recall(-1, "首份"));
        Assert.assertEquals("only", history.recall(-1, "覆盖无效"));
        Assert.assertEquals("首份", history.recall(1, "覆盖无效"));

        // 打开输入屏复位:草稿清空
        history.recall(-1, "第二份");
        history.resetCursor();
        Assert.assertEquals("复位后无草稿", "", history.recall(1, ""));
    }
}
