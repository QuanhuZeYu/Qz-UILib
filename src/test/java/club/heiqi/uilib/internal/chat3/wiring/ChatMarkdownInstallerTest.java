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

    /**
     * R3-C：感知闩的边沿契约 + 换实例必重推。
     *
     * <p>headless 起不了 Minecraft，故直接测闩本身(owner 只比引用身份，语义与真控制器一致)。
     * 旧实现是 {@code private static boolean lastChatOpen} + 只比布尔值：
     * {@code ChatHudWindow} 注销把 controller 置 null 后再接管，新实例 chatOpen 初值 FALSE，
     * 而闩记得 true → 聊天屏开着切总开关时新容器永远停在未打开态。</p>
     */
    @Test
    public void chatOpenLatchReappliesAfterControllerInstanceChanges() {
        ChatMarkdownInstaller.ChatOpenLatch latch = new ChatMarkdownInstaller.ChatOpenLatch();
        Object first = new Object();
        Object second = new Object();

        Assert.assertTrue("首次必须推一次(旧实现因初值同为 false 而永不推)", latch.shouldApply(first, true));
        Assert.assertFalse("同实例同值不重复推(边沿契约)", latch.shouldApply(first, true));
        Assert.assertTrue("同实例开→关是边沿，推", latch.shouldApply(first, false));
        Assert.assertFalse("同实例同值不重复推", latch.shouldApply(first, false));
        Assert.assertTrue("换实例即使同值也必须重推(新容器初值不继承旧闩)", latch.shouldApply(second, false));
        Assert.assertFalse("新实例随后的同值调用回到不推", latch.shouldApply(second, false));
    }
}
