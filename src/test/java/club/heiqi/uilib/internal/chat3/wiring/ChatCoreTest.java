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

    /**
     * C8 通道③ wiring 锁：安装器先例的 sink 注册（setMarkdownSink → core::appendMarkdown）
     * 打通 printMarkdown 端到端——注入进的是 markdown 键组件、历史零装饰（计数装饰器
     * 恒 0）、reader 从入史组件取回 args[0] 原文（含 **、\n、中文、URL 形状，§ 字面零特判）。
     * 对照正例：原版注入路径（appendMessage）同装饰器次数 ≥ 1（防空断言）。
     */
    @Test
    public void printMarkdownEndToEndViaSinkBypassesDecoratorsAndKeepsRawArgs() throws Exception {
        ChatSceneController controller = controller();
        ChatCore core = new ChatCore(controller);
        final int[] calls = new int[1];
        AutoCloseable counting = ChatAccess.getInstance().registerDecorator(component -> {
            calls[0]++;
            return component;
        });
        ChatAccess.getInstance().setMarkdownSink(component -> core.appendMarkdown(component, 0));
        try {
            String md = "# 公告 **粗**\n看 http://a.co 吧 " + (char) 0x00A7 + "ab";
            ChatAccess.getInstance().printMarkdown(md);
            Assert.assertEquals("print 路径装饰器恒 0", 0, calls[0]);
            Assert.assertEquals(1, controller.history().size());
            net.minecraft.util.IChatComponent injected =
                    controller.history().snapshot().get(0).getComponent();
            Assert.assertTrue("历史持有的是 markdown 键翻译组件（结构不丢）",
                    club.heiqi.uilib.internal.chat3.viewmodel.StructuredChatReader
                            .rendersAsMarkdown(injected));
            Assert.assertEquals("reader 取回 args[0] = 递交原文（逐字）", md,
                    club.heiqi.uilib.internal.chat3.viewmodel.StructuredChatReader
                            .markdownContentOf(injected));

            // 对照正例（防空断言）：原版注入路径照常过装饰链
            core.appendMessage(new ChatComponentText("<Steve> hi"), 1);
            Assert.assertTrue("原版路径装饰器计数必须动: calls=" + calls[0], calls[0] >= 1);
        } finally {
            ChatAccess.getInstance().setMarkdownSink(null);
            counting.close();
        }
    }

    /** appendMarkdown 对 null 组件零入史、messageId 语义与 append 一致（同 id 替换不刷屏）。 */
    @Test
    public void appendMarkdownGuardsNullAndKeepsMessageIdSemantics() {
        ChatSceneController controller = controller();
        ChatCore core = new ChatCore(controller);
        core.appendMarkdown(null, 0);
        Assert.assertEquals(0, controller.history().size());
        net.minecraft.util.IChatComponent a = new net.minecraft.util.ChatComponentTranslation(
                ChatAccess.MARKDOWN_CHAT_KEY, new Object[] {"one"});
        core.appendMarkdown(a, 7);
        core.appendMarkdown(a, 7);
        Assert.assertEquals("同 id=7 替换(原版 setChatLine 口径)", 1, controller.history().size());
        core.appendMarkdown(a, 0);
        Assert.assertEquals("id=0 恒追加", 2, controller.history().size());
    }

    /** Facade 的 printChatMessage 路径（会 decorate）与 core.appendMarkdown（不会）语义分离锁。 */
    @Test
    public void facadePrintPathDecoratesWhileMarkdownBypassDoesNot() throws Exception {
        ChatSceneController controller = controller();
        final int[] calls = new int[1];
        AutoCloseable counting = ChatAccess.getInstance().registerDecorator(component -> {
            calls[0]++;
            return component;
        });
        try {
            ChatFacade facade = new ChatFacade(null, controller);
            facade.printChatMessage(new ChatComponentText("<Steve> hi"));
            Assert.assertTrue("Facade.printChatMessage 仍走装饰链（既有行为一字不动）", calls[0] >= 1);
            int after = calls[0];
            facade.core().appendMarkdown(new net.minecraft.util.ChatComponentTranslation(
                    club.heiqi.uilib.api.chat.ChatAccess.MARKDOWN_CHAT_KEY,
                    new Object[] {"plain"}), 0);
            Assert.assertEquals("appendMarkdown 旁路零装饰", after, calls[0]);
        } finally {
            counting.close();
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