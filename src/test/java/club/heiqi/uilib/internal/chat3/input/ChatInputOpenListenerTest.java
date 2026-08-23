package club.heiqi.uilib.internal.chat3.input;

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;

/**
 * ChatInputOpenListener 装配面契约测试(headless 可验证部分):预填字段发现
 * (mcp 名 defaultInputFieldText,不实例化 GuiChat——其静态初始化会触发 Minecraft)。
 */
public class ChatInputOpenListenerTest {

    @Test
    public void shouldDiscoverDefaultInputTextField() {
        Field field = ChatInputOpenListener.defaultInputTextField();
        Assert.assertNotNull("应能按 mcp/srg 名发现 GuiChat 预填字段", field);
        Assert.assertEquals("字段类型应为 String", String.class, field.getType());
    }
}
