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
}
