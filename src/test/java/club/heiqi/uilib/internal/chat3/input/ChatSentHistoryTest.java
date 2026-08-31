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
        Assert.assertNull("空历史回显无效操作哨兵", history.recall(-1));

        history.add("first");
        history.add("second");
        // 光标初始 = size(空槽)
        Assert.assertEquals("上一条 = 最新", "second", history.recall(-1));
        Assert.assertEquals("再上一条 = 更旧", "first", history.recall(-1));
        Assert.assertEquals("到顶后停住", "first", history.recall(-1));
        Assert.assertEquals("下一条", "second", history.recall(1));
        Assert.assertNull("下到空槽无草稿返回 null 哨兵", history.recall(1));
        Assert.assertEquals("空槽再上 = 最新", "second", history.recall(-1));
    }

    @Test
    public void shouldResetCursor() {
        ChatSentHistory history = new ChatSentHistory();
        history.add("a");
        history.recall(-1);
        history.resetCursor();
        Assert.assertNull("复位空槽后无草稿返回 null 哨兵", history.recall(1));
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
        Assert.assertNull("同步后光标复位空槽无草稿返回 null", history.recall(1));
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
        // 恢复后暂存清空:再翻回最底无草稿时返回 null
        Assert.assertNull("恢复后暂存清空,再回底槽无草稿返回 null", history.recall(1, ""));
        // 再次进入历史:重新暂存当前输入
        Assert.assertEquals("second", history.recall(-1, "新草稿"));
        Assert.assertEquals("新草稿", history.recall(1, "新草稿"));
    }

    // ==================== I3 草稿清空修复(null 哨兵契约) ====================

    /**
     * I3:底槽且无暂存时返回 null(无效操作哨兵,vanilla getSentHistory 越界返回 null 同款);
     * 空串是合法暂存值,恢复仍返回空串。
     */
    @Test
    public void shouldReturnNullWhenAtBottomWithoutDraft() {
        ChatSentHistory history = new ChatSentHistory();
        // 空历史 + draft == null → null(底槽按 ↓ 或空历史按 ↑ 均无效)
        Assert.assertNull("空历史按 ↑ 返回 null", history.recall(-1, ""));
        Assert.assertNull("空历史按 ↓ 返回 null", history.recall(1, ""));
        history.add("first");
        // 传 null 不暂存:进历史回底槽 → null(draft == null)
        Assert.assertEquals("first", history.recall(-1, null));
        Assert.assertNull("底槽无暂存返回 null", history.recall(1, null));
        // 底槽 + draft == ""(空串是合法暂存值)→ 恢复 ""
        Assert.assertEquals("first", history.recall(-1, ""));
        Assert.assertEquals("空草稿合法暂存,恢复返回空串", "", history.recall(1, ""));
    }

    /**
     * I3:底槽返回 null 哨兵时不移动光标、不清草稿(无效操作零副作用)。
     */
    @Test
    public void nullReturnDoesNotMutateCursorOrDraft() {
        ChatSentHistory history = new ChatSentHistory();
        history.add("first");
        history.add("second");
        Assert.assertEquals("second", history.recall(-1, "草稿"));
        Assert.assertEquals("first", history.recall(-1, "草稿"));
        // 已在历史中,非底槽:正常命中不返回 null
        Assert.assertEquals("second", history.recall(1, "草稿"));
        Assert.assertEquals("回到底槽恢复草稿", "草稿", history.recall(1, "草稿"));
        // 已到底槽且草稿已清:再按 ↓ 返回 null,光标保持在底槽、草稿仍为 null
        Assert.assertNull("底槽再按 ↓ 返回 null", history.recall(1, ""));
        Assert.assertNull("重复无效操作仍返回 null", history.recall(1, ""));
        // 随后按 ↑ 仍可正常翻入历史(光标未被 null 哨兵带出底槽)
        Assert.assertEquals("second", history.recall(-1, "新草稿"));
        Assert.assertEquals("新草稿", history.recall(1, "新草稿"));
    }

    /**
     * I3:单参 recall(direction) 不暂存草稿(语义与双参传 null 一致)。
     */
    @Test
    public void singleArgRecallDoesNotStashDraft() {
        ChatSentHistory history = new ChatSentHistory();
        history.add("first");
        Assert.assertEquals("单参 ↑ 命中历史", "first", history.recall(-1));
        // 单参路径不暂存:回到底槽返回 null 而非 ""
        Assert.assertNull("单参回到底槽无暂存返回 null", history.recall(1));
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
        Assert.assertNull("复位后无草稿返回 null", history.recall(1, ""));
    }
}
