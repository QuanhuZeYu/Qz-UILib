package club.heiqi.uilib.internal.chat3.input;

import org.junit.Assert;
import org.junit.Test;

/**
 * 聊天输入屏键盘节流键映射测试(L4 纯函数):LWJGL 键码 → 节流动作全表,
 * 非 Tab 键清补全循环态(原版 GuiChat:91)。
 */
public class ChatInputKeyActionTest {

    @Test
    public void mapsLwjglKeyCodes() {
        Assert.assertEquals(ChatInputKeyAction.Action.SUBMIT, ChatInputKeyAction.of(28)); // Return
        Assert.assertEquals(ChatInputKeyAction.Action.HISTORY_UP, ChatInputKeyAction.of(200)); // Up
        Assert.assertEquals(ChatInputKeyAction.Action.HISTORY_DOWN, ChatInputKeyAction.of(208)); // Down
        Assert.assertEquals(ChatInputKeyAction.Action.TAB, ChatInputKeyAction.of(15)); // Tab
        Assert.assertEquals(ChatInputKeyAction.Action.PAGE_UP, ChatInputKeyAction.of(201)); // PageUp
        Assert.assertEquals(ChatInputKeyAction.Action.PAGE_DOWN, ChatInputKeyAction.of(209)); // PageDown
        Assert.assertEquals("方向键左走原版输入管线",
                ChatInputKeyAction.Action.PASS_THROUGH, ChatInputKeyAction.of(203));
        Assert.assertEquals("方向键右走原版输入管线",
                ChatInputKeyAction.Action.PASS_THROUGH, ChatInputKeyAction.of(205));
        Assert.assertEquals("字符键走原版输入管线",
                ChatInputKeyAction.Action.PASS_THROUGH, ChatInputKeyAction.of('a'));
    }

    @Test
    public void nonTabKeysClearCompletionCycle() {
        Assert.assertFalse("Tab 不清循环态", ChatInputKeyAction.clearsCompletionCycle(15));
        Assert.assertTrue("方向键左清循环态", ChatInputKeyAction.clearsCompletionCycle(203));
        Assert.assertTrue("方向键右清循环态", ChatInputKeyAction.clearsCompletionCycle(205));
        Assert.assertTrue("Enter 清循环态", ChatInputKeyAction.clearsCompletionCycle(28));
        Assert.assertTrue("Up 清循环态", ChatInputKeyAction.clearsCompletionCycle(200));
        Assert.assertTrue("字符键清循环态", ChatInputKeyAction.clearsCompletionCycle('a'));
    }
}
