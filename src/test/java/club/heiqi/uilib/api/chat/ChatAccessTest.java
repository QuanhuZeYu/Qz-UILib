package club.heiqi.uilib.api.chat;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * ChatAccess 公共 API 契约测试:装饰器链应用/丢弃/异常隔离/句柄注销/接管状态。
 */
public class ChatAccessTest {

    @Test
    public void shouldApplyDecoratorsInRegistrationOrder() throws Exception {
        ChatAccess access = ChatAccess.getInstance();
        AutoCloseable first = access.registerDecorator(component ->
                new ChatComponentText("[A]" + component.getUnformattedText()));
        AutoCloseable second = access.registerDecorator(component ->
                new ChatComponentText("[B]" + component.getUnformattedText()));
        try {
            IChatComponent result = access.decorate(new ChatComponentText("hi"));
            Assert.assertEquals("[B][A]hi", result.getUnformattedText());
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    public void shouldReturnNullOnDropDecorator() throws Exception {
        ChatAccess access = ChatAccess.getInstance();
        AutoCloseable drop = access.registerDecorator(component -> null);
        try {
            Assert.assertNull("装饰器返回 null 表示丢弃", access.decorate(new ChatComponentText("hi")));
        } finally {
            drop.close();
        }
    }

    @Test
    public void shouldIsolateDecoratorFailure() throws Exception {
        ChatAccess access = ChatAccess.getInstance();
        AutoCloseable failing = access.registerDecorator(component -> {
            throw new IllegalStateException("boom");
        });
        AutoCloseable normal = access.registerDecorator(component ->
                new ChatComponentText("ok:" + component.getUnformattedText()));
        try {
            IChatComponent result = access.decorate(new ChatComponentText("hi"));
            Assert.assertEquals("ok:hi", result.getUnformattedText());
            Assert.assertEquals("异常装饰器应被移除", 1, access.decoratorCount());
        } finally {
            normal.close();
            failing.close(); // 已被移除,幂等
        }
    }

    @Test
    public void shouldUnregisterViaHandle() throws Exception {
        ChatAccess access = ChatAccess.getInstance();
        AutoCloseable handle = access.registerDecorator(component -> component);
        Assert.assertEquals(1, access.decoratorCount());
        handle.close();
        handle.close(); // 幂等
        Assert.assertEquals(0, access.decoratorCount());
    }

    @Test
    public void shouldRejectNullDecorator() {
        try {
            ChatAccess.getInstance().registerDecorator(null);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }

    @Test
    public void shouldTrackTakeoverState() {
        ChatAccess access = ChatAccess.getInstance();
        access.setTakeoverActive(true);
        Assert.assertTrue(access.isTakeoverActive());
        access.setTakeoverActive(false);
        Assert.assertFalse(access.isTakeoverActive());
    }

    @Test
    public void shouldPassThroughUnmodifiedWhenNoDecorators() {
        ChatAccess access = ChatAccess.getInstance();
        Assert.assertEquals(0, access.decoratorCount());
        IChatComponent original = new ChatComponentText("plain");
        Assert.assertSame(original, access.decorate(original));
    }

    // ==================== C8 通道③：printMarkdown（显式递交、装饰链解耦、永不发送） ====================

    /** 键常量单一定义点：值必须恰为 uilib.markdown（Reader 侧消费同一常量，禁两处字面量）。 */
    @Test
    public void markdownKeyConstantIsSingleSourceOfTruth() {
        Assert.assertEquals(ChatAccess.MARKDOWN_CHAT_KEY, "uilib.markdown");
    }

    /**
     * 锁 3（print 恒纯）：注册计数装饰器后 printMarkdown(String) 注入 ⇒ 装饰器调用恒 0；
     * 注入物 = markdown 键翻译组件且 args[0] = 原文。对照正例：同一装饰器下显式 decorate
     * 入口（原版注入路径 ChatCore.appendMessage 所调的那条）次数 ≥ 1——防空断言。
     */
    @Test
    public void printMarkdownNeverTouchesDecoratorChain() throws Exception {
        final int[] calls = new int[1];
        ChatAccess access = ChatAccess.getInstance();
        final java.util.List<IChatComponent> injected = new java.util.ArrayList<IChatComponent>();
        AutoCloseable counting = access.registerDecorator(component -> {
            calls[0]++;
            return component;
        });
        AutoCloseable sink = installCapturingSink(injected);
        try {
            access.printMarkdown("**bold** 公告\nhttp://a.co");
            Assert.assertEquals("print 路径装饰器调用恒 0", 0, calls[0]);
            Assert.assertEquals(1, injected.size());
            IChatComponent component = injected.get(0);
            Assert.assertTrue("注入形 = markdown 键翻译组件",
                    component instanceof net.minecraft.util.ChatComponentTranslation);
            net.minecraft.util.ChatComponentTranslation translation =
                    (net.minecraft.util.ChatComponentTranslation) component;
            Assert.assertEquals(ChatAccess.MARKDOWN_CHAT_KEY, translation.getKey());
            Object[] args = translation.getFormatArgs();
            Assert.assertEquals(1, args.length);
            Assert.assertEquals("**bold** 公告\nhttp://a.co", args[0]);

            // 对照正例（防空断言）：显式过链入口在同一装饰器下必须计数
            access.decorate(new ChatComponentText("vanilla path"));
            Assert.assertTrue("正对照:decorate 显式调用计数必须动: calls=" + calls[0], calls[0] >= 1);
        } finally {
            sink.close();
            counting.close();
        }
    }

    /**
     * 锁 4（显式装饰 = 调用方主动过一次链，print 路径仍零自动调用）：
     * decorated = decorate(raw) → 链上恰 1 次；printMarkdown(decorated) 注入成功且
     * 链上总次数恒为那 1 次（print 不再叠加自动装饰），注入物 = 调用方递进来的组件本体。
     */
    @Test
    public void decoratedComponentGoesThroughPrintWithExactlyOneDecorationCall() throws Exception {
        final int[] calls = new int[1];
        ChatAccess access = ChatAccess.getInstance();
        final java.util.List<IChatComponent> injected = new java.util.ArrayList<IChatComponent>();
        AutoCloseable counting = access.registerDecorator(component -> {
            calls[0]++;
            return new ChatComponentText("[D]" + component.getUnformattedText());
        });
        AutoCloseable sink = installCapturingSink(injected);
        try {
            IChatComponent raw = new ChatComponentText("# 标题");
            IChatComponent decorated = access.decorate(raw);
            Assert.assertEquals("显式 decorate 恰一次", 1, calls[0]);
            Assert.assertEquals("[D]# 标题", decorated.getUnformattedText());

            access.printMarkdown(decorated);
            Assert.assertEquals("print 组件形零自动装饰（总数仍是手调那次）", 1, calls[0]);
            Assert.assertEquals(1, injected.size());
            Assert.assertSame("注入 = 调用方递进来的那个组件本体（print 不改写不重装饰）",
                    decorated, injected.get(0));
        } finally {
            sink.close();
            counting.close();
        }
    }

    /**
     * 锁 7（未接管降级）：sink 缺席 ⇒ 降级路径产物必须是 ChatComponentText 且文本 == md 原文，
     * 断言不出现 "uilib.markdown" 字面。观察接法 = 把「直连 mc」的降级出口抽成可换字段
     * （{@code __setVanillaPrintForTest}），headless 注入捕获器断言产物类型与文本；
     * 真机默认出口行为不变（同一条 printChatMessage 调用）。组件形降级 = 直接递原组件
     * （不包壳、不改写；若它是 markdown 键组件，原版渲染 key 字面属固有形状）。
     */
    @Test
    public void fallbackWithoutSinkShowsPlainTextViaVanillaPrint() throws Exception {
        final java.util.List<IChatComponent> printed = new java.util.ArrayList<IChatComponent>();
        ChatAccess access = ChatAccess.getInstance();
        ChatAccess.__setVanillaPrintForTest(component -> printed.add(component));
        try {
            String md = "\u00a77 公告 **b**";
            access.printMarkdown(md);
            Assert.assertEquals(1, printed.size());
            IChatComponent shown = printed.get(0);
            Assert.assertEquals("String 形降级产物恰为 ChatComponentText",
                    ChatComponentText.class, shown.getClass());
            Assert.assertEquals("降级文本 = md 原文", md, shown.getUnformattedText());
            Assert.assertFalse("降级零 markdown 键字面: " + shown.getUnformattedText(),
                    shown.getUnformattedText().contains(ChatAccess.MARKDOWN_CHAT_KEY));

            IChatComponent custom = new ChatComponentText("custom");
            access.printMarkdown(custom);
            Assert.assertEquals(2, printed.size());
            Assert.assertSame("组件形降级 = 原组件直递(不包壳)", custom, printed.get(1));
        } finally {
            ChatAccess.__setVanillaPrintForTest(null);
            printed.clear();
        }
    }

    /** 两形 null 输入都忽略（sink 在场与缺席两侧一致）。 */
    @Test
    public void printMarkdownIgnoresNullInputs() throws Exception {
        ChatAccess access = ChatAccess.getInstance();
        final java.util.List<IChatComponent> injected = new java.util.ArrayList<IChatComponent>();
        final java.util.List<IChatComponent> printed = new java.util.ArrayList<IChatComponent>();
        AutoCloseable sink = installCapturingSink(injected);
        ChatAccess.__setVanillaPrintForTest(component -> printed.add(component));
        try {
            access.printMarkdown((String) null);
            access.printMarkdown((IChatComponent) null);
            Assert.assertTrue("接管态:null 两形零注入", injected.isEmpty());
            Assert.assertTrue("降级出口未被触达", printed.isEmpty());
        } finally {
            sink.close();
            ChatAccess.__setVanillaPrintForTest(null);
            injected.clear();
            printed.clear();
        }
        // sink 撤销后（= 未接管）null 仍零触达降级出口
        ChatAccess.__setVanillaPrintForTest(component -> printed.add(component));
        try {
            access.printMarkdown((String) null);
            access.printMarkdown((IChatComponent) null);
            Assert.assertTrue("未接管态:null 两形零降级", printed.isEmpty());
        } finally {
            ChatAccess.__setVanillaPrintForTest(null);
            printed.clear();
        }
    }

    /**
     * 锁 9（永不发送；代码路径锁）：printMarkdown 家族与其注入实现所在分节的源码零
     * ChatBridge / sendChatMessage / addToSentMessages / getSentMessages 引用；正对照 = 同
     * 一把尺在 send() 方法体必须真命中 ChatBridge（扫描器不瞎）。printMarkdown 只注入或
     * 降级显示，发送链与已发送历史由结构上不可达保证。
     */
    @Test
    public void printMarkdownCodePathNeverTouchesSendChain() throws Exception {
        String raw = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/main/java/club/heiqi/uilib/api/chat/ChatAccess.java")),
                java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder code = new StringBuilder();
        boolean inBlockComment = false;
        for (String line : raw.split("\\r?\\n")) {
            String t = line.trim();
            if (inBlockComment) {
                if (t.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }
            if (t.startsWith("/*")) {
                if (!t.contains("*/")) {
                    inBlockComment = true;
                }
                continue;
            }
            if (t.startsWith("//") || t.startsWith("*")) {
                continue;
            }
            code.append(t).append(0x0a);
        }
        String src = code.toString();
        int printStart = src.indexOf("public void printMarkdown(String md)");
        int sendStart = src.indexOf("public void send(String message)");
        Assert.assertTrue("printMarkdown(String) 必须存在（本锁的被检对象）", printStart >= 0);
        Assert.assertTrue("send(String) 必须存在（正对照锚）", sendStart >= printStart);
        String printSection = src.substring(printStart, sendStart);
        for (String forbidden : new String[] {"ChatBridge", "sendChatMessage", "addToSentMessages",
                "getSentMessages"}) {
            Assert.assertFalse("print 家族代码路径出现发送链成员: " + forbidden,
                    printSection.contains(forbidden));
        }
        // 正对照:同一把尺对 send() 方法体必须真扫到 ChatBridge
        Assert.assertTrue("扫描器正对照:send 方法体仍含 ChatBridge.send",
                src.substring(sendStart).contains("ChatBridge.send(message)"));
    }

    /** 测试助手:装一个把注入组件记进 list 的 sink，返回撤销句柄（照 setTakeoverActive 先例）。 */
    private static AutoCloseable installCapturingSink(
            final java.util.List<IChatComponent> sink) {
        ChatAccess.getInstance().setMarkdownSink(component -> sink.add(component));
        return new AutoCloseable() {
            @Override
            public void close() {
                ChatAccess.getInstance().setMarkdownSink(null);
                sink.clear();
            }
        };
    }
}
