package club.heiqi.uilib.internal.chat3.wiring;

import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;

import club.heiqi.uilib.api.chat.ChatAccess;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.internal.chat3.view.ChatMessageList;
import club.heiqi.uilib.internal.chat3.view.ChatSceneController;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter;

/**
 * ChatCore 编排契约测试:消息进入/装饰器链应用/丢弃/清空/删除/滚动/几何。
 */
public class ChatCoreTest {

    private static ChatSceneController controller() {
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
        }, new ChatMessageList.SegmentParser() {
            @Override
            public List<TextSegment> parse(String text, int baseColor) {
                TextStyle style = new TextStyle();
                style.setColor(baseColor);
                return Collections.singletonList(new TextSegment(text, style));
            }
        });
    }

    @Test
    public void shouldApplyDecoratorChainOnAppend() throws Exception {
        ChatSceneController controller = controller();
        ChatCore core = new ChatCore(controller);

        AutoCloseable prefix = ChatAccess.getInstance().registerDecorator(component ->
                new ChatComponentText("[D] " + component.getUnformattedText()));
        try {
            core.appendMessage(new ChatComponentText("<Steve> hi"), 1);
            Assert.assertEquals("[D] <Steve> hi", controller.history().snapshot().get(0).getPlainText());
        } finally {
            prefix.close();
        }
    }

    @Test
    public void shouldDropMessageWhenDecoratorReturnsNull() throws Exception {
        ChatSceneController controller = controller();
        ChatCore core = new ChatCore(controller);
        core.appendMessage(new ChatComponentText("<Steve> keep"), 1);
        Assert.assertEquals(1, controller.history().size());

        AutoCloseable drop = ChatAccess.getInstance().registerDecorator(component -> null);
        try {
            core.appendMessage(new ChatComponentText("<Steve> drop"), 2);
            Assert.assertEquals("丢弃语义:消息不入历史", 1, controller.history().size());
        } finally {
            drop.close();
        }
    }

    @Test
    public void shouldForwardClearDeleteAndScroll() {
        ChatSceneController controller = controller();
        ChatCore core = new ChatCore(controller);
        core.appendMessage(new ChatComponentText("<Steve> a"), 1);
        core.appendMessage(new ChatComponentText("<Steve> b"), 2);

        Assert.assertTrue(core.deleteById(2));
        Assert.assertEquals(1, controller.history().size());
        Assert.assertFalse(core.deleteById(99));

        core.scrollBy(5);
        Assert.assertEquals(5, controller.history().getScroll());
        core.resetScroll();
        Assert.assertEquals(0, controller.history().getScroll());

        core.clear();
        Assert.assertEquals(0, controller.history().size());
    }

    @Test
    public void shouldExposeGeometryAndState() {
        ChatCore core = new ChatCore(controller());
        Assert.assertFalse(core.getChatOpen());
        Assert.assertEquals(club.heiqi.uilib.internal.chat3.ChatMarkdownSettings.containerHeightFor(0),
                core.chatHeight());
        Assert.assertEquals((float) club.heiqi.uilib.internal.chat3.ChatMarkdownSettings.getChatLineHeightPx(),
                core.chatLineHeight(), 0.001F);
        Assert.assertTrue(core.visibleLineCount() > 0);
        Assert.assertNull("未布局无命中", core.hitTest(0, 0));
    }
}
