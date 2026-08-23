package club.heiqi.uilib.internal.chat3.wiring;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.client.gui.GuiNewChat;

/**
 * ChatFacade S0 冒烟:构造可用、类型兼容(GuiNewChat 子类)、全继承原版行为(替换后零回归)。
 *
 * <p>headless 安全:构造不触碰 Minecraft 静态状态;只验证无 mc 依赖的继承行为
 * (发送历史 = 尾部追加 + 相邻重复跳过,与 javap 实测原版字节码语义一致)。</p>
 */
public class ChatFacadeTest {

    @Test
    public void shouldConstructAndStayTypeCompatible() {
        ChatFacade facade = new ChatFacade(null);
        Assert.assertTrue("ChatFacade 必须是 GuiNewChat 子类(类型兼容契约)",
                facade instanceof GuiNewChat);
    }

    @Test
    public void shouldInheritVanillaSentHistoryBehavior() {
        ChatFacade facade = new ChatFacade(null);
        Assert.assertTrue("初始发送历史应为空", facade.getSentMessages().isEmpty());

        facade.addToSentMessages("hello");
        Assert.assertEquals(Arrays.asList("hello"), facade.getSentMessages());

        // 相邻重复跳过(原版语义)
        facade.addToSentMessages("hello");
        Assert.assertEquals(Arrays.asList("hello"), facade.getSentMessages());

        facade.addToSentMessages("world");
        Assert.assertEquals(Arrays.asList("hello", "world"), facade.getSentMessages());
    }

    @Test
    public void shouldInheritVanillaResetScroll() {
        ChatFacade facade = new ChatFacade(null);
        // 继承方法可直接调用不抛(原版 resetScroll 只操作构造期已初始化的私有字段,不依赖 mc;
        // scroll 依赖 mc(经 getLineCount),留待 S4 接线后由自有状态承载,headless 不再触碰)
        facade.resetScroll();
    }
}
