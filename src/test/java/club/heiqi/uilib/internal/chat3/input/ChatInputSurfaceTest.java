package club.heiqi.uilib.internal.chat3.input;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;

/**
 * ChatInputSurface 滚轮换算契约:锁定「滚轮向上 = 向旧消息方向(正 scrollBy 量)」的符号语义。
 *
 * <p>wheelDelta 符号遵循 LwjglInputSource(正 = 滚轮向上);正行数经
 * {@code history.scrollBy(+n)} 进入「自底部向上偏移」域,与原版
 * GuiNewChat.func_146229_b(scroll &gt; 0 查看更早消息)同语义,再经 ChatContainer 的
 * 聊天↔scene 倒置映射转为视口 scrollOffsetY 减小(内容向旧消息方向滚动,见
 * ChatContainerTest#viewportScrollOffsetInvertsChatScrollDirection)。</p>
 */
public class ChatInputSurfaceTest {

    @Test
    public void wheelUpYieldsPositiveScrollTowardOlderMessages() {
        Assert.assertEquals("滚轮向上(正增量)= 向旧消息的正行数",
                ChatMarkdownSettings.getScrollWheelLines(),
                ChatInputSurface.wheelScrollLines(120, false));
        Assert.assertEquals("滚轮向上超量 clamp 到 ±1 后仍为 ×7 正量",
                ChatMarkdownSettings.getScrollWheelLines(),
                ChatInputSurface.wheelScrollLines(240, false));
    }

    @Test
    public void wheelDownYieldsNegativeScrollBackToLatest() {
        Assert.assertEquals("滚轮向下(负增量)= 回新消息的负行数",
                -ChatMarkdownSettings.getScrollWheelLines(),
                ChatInputSurface.wheelScrollLines(-120, false));
        Assert.assertEquals("滚轮向下超量 clamp 到 ±1 后仍为 ×7 负量",
                -ChatMarkdownSettings.getScrollWheelLines(),
                ChatInputSurface.wheelScrollLines(-240, false));
    }

    @Test
    public void shiftWheelUsesSingleLineStep() {
        Assert.assertEquals("Shift+滚轮向上 = 1 行(向旧)", 1,
                ChatInputSurface.wheelScrollLines(120, true));
        Assert.assertEquals("Shift+滚轮向下 = -1 行(向新)", -1,
                ChatInputSurface.wheelScrollLines(-120, true));
    }

    @Test
    public void zeroDeltaYieldsZero() {
        Assert.assertEquals("零增量 = 不滚动", 0,
                ChatInputSurface.wheelScrollLines(0, false));
        Assert.assertEquals("零增量(Shift)= 不滚动", 0,
                ChatInputSurface.wheelScrollLines(0, true));
    }
}
