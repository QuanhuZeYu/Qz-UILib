package club.heiqi.uilib.internal.chat3.wiring;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.ChatComponentText;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.view.ChatSceneController;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter;

/**
 * ChatFacade S4 契约测试:类型兼容 + 继承原版行为(发送历史)+ override 单行转发全表。
 */
public class ChatFacadeTest {

    private static ChatSceneController testController() {
        ChatLineLayouter.Measure measure = new ChatLineLayouter.Measure() {
            @Override
            public float advance(String text, int fontSizePx) {
                return text.length() * 2.0F;
            }

            @Override
            public int epoch() {
                return 1;
            }
        };
        return new ChatSceneController(measure, new ChatSceneController.SelfNameProvider() {
            @Override
            public String selfName() {
                return "Alex";
            }
        }, new ChatSceneController.SegmentParser() {
            @Override
            public List<TextSegment> parse(String text, int baseColor) {
                TextStyle style = new TextStyle();
                style.setColor(baseColor);
                return Collections.singletonList(new TextSegment(text, style));
            }
        });
    }

    @Test
    public void shouldConstructAndStayTypeCompatible() {
        ChatFacade facade = new ChatFacade(null, testController());
        Assert.assertTrue("ChatFacade 必须是 GuiNewChat 子类(类型兼容契约)",
                facade instanceof GuiNewChat);
    }

    @Test
    public void shouldInheritVanillaSentHistoryBehavior() {
        ChatFacade facade = new ChatFacade(null, testController());
        Assert.assertTrue("初始发送历史应为空", facade.getSentMessages().isEmpty());
        facade.addToSentMessages("hello");
        facade.addToSentMessages("hello"); // 相邻重复跳过
        facade.addToSentMessages("world");
        Assert.assertEquals(Arrays.asList("hello", "world"), facade.getSentMessages());
    }

    @Test
    public void shouldForwardMessagesToCore() {
        ChatFacade facade = new ChatFacade(null, testController());
        ChatSceneController controller = facade.__coreForTest().controller();

        facade.printChatMessage(new ChatComponentText("<Steve> hello"));
        Assert.assertEquals(1, controller.history().size());

        facade.printChatMessageWithOptionalDeletion(new ChatComponentText("<Steve> again"), 42);
        Assert.assertEquals(2, controller.history().size());
        Assert.assertEquals(42, controller.history().snapshot().get(0).getMessageId());

        facade.deleteChatLine(42);
        Assert.assertEquals(1, controller.history().size());

        facade.clearChatMessages();
        Assert.assertEquals(0, controller.history().size());
    }

    @Test
    public void shouldForwardScrollAndLayout() {
        ChatFacade facade = new ChatFacade(null, testController());
        ChatSceneController controller = facade.__coreForTest().controller();

        facade.scroll(3);
        Assert.assertEquals(3, controller.history().getScroll());
        facade.resetScroll();
        Assert.assertEquals(0, controller.history().getScroll());
        facade.refreshChat(); // 不抛
        facade.drawChat(0);    // 不抛(渲染 tick 幂等)
    }

    @Test
    public void shouldExposeGeometryAndState() {
        ChatFacade facade = new ChatFacade(null, testController());

        Assert.assertFalse("初始未打开聊天", facade.getChatOpen());
        Assert.assertEquals(ChatMarkdownSettings.getContainerHeightPx(), facade.func_146228_f());
        Assert.assertEquals(ChatMarkdownSettings.getContainerHeightPx(), facade.func_146246_g());
        Assert.assertEquals(ChatMarkdownSettings.getChatLineHeightPx(), facade.func_146244_h(), 0.001F);
        Assert.assertEquals(ChatMarkdownSettings.getContainerHeightPx()
                / ChatMarkdownSettings.getChatLineHeightPx(), facade.func_146232_i());
        Assert.assertNull("未布局/无消息时命中为空", facade.func_146236_a(0, 0));
    }
}
