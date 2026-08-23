package club.heiqi.uilib.internal.chat3.wiring;

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;

/**
 * ChatMarkdownInstaller 装配面契约测试(headless 可验证部分):
 * 字段发现(mcp 名 persistantChatGUI)与类型——替换与读回验证依赖真机 Minecraft 实例(S6 真机验证)。
 */
public class ChatMarkdownInstallerTest {

    @Test
    public void shouldDiscoverChatFieldOnGuiIngame() {
        Field field = ChatMarkdownInstaller.findChatField();
        Assert.assertNotNull("应能按 mcp 名发现 GuiIngame.persistantChatGUI 字段", field);
        Assert.assertEquals("字段类型应为 GuiNewChat",
                net.minecraft.client.gui.GuiNewChat.class, field.getType());
    }
}
