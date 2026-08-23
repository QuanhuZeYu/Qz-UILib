package club.heiqi.uilib.internal.chat3.input;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;

/**
 * 聊天打开拦截(L4 装配点):原版开 GuiChat → 取消并改开 {@link ChatInputScreen}(容器 + 输入条)。
 *
 * <p>覆盖全部打开入口(聊天键 T、斜杠键、其他 mod 直接 displayGuiScreen(new GuiChat))——
 * GuiOpenEvent 在 displayGuiScreen 必经。总开关关闭(逃生舱)时不拦截,原版 GuiChat 照常;
 * 预填文本从 GuiChat.defaultInputFieldText(构造期字段,field_146409_v)读取。</p>
 */
public final class ChatInputOpenListener {

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (event == null || event.gui == null) {
            return;
        }
        if (event.gui instanceof ChatInputScreen) {
            return; // 自开屏幕放行(防递归)
        }
        if (!(event.gui instanceof GuiChat)) {
            return;
        }
        if (!ChatMarkdownSettings.isEnabled()) {
            return; // 逃生舱:原版输入
        }
        String initial = readPrefill(event.gui);
        event.setCanceled(true);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            mc.displayGuiScreen(new ChatInputScreen(initial));
        }
    }

    /** 读取 GuiChat 预填文本(mcp 名优先、srg 兜底;失败返回空串)。 */
    static String readPrefill(GuiScreen gui) {
        Field field = defaultInputTextField();
        if (field == null) {
            return "";
        }
        try {
            Object value = field.get(gui);
            return value == null ? "" : String.valueOf(value);
        } catch (ReflectiveOperationException failure) {
            return "";
        }
    }

    /** 预填字段发现(headless 可测部分:类加载不触发 Minecraft 静态初始化)。 */
    static Field defaultInputTextField() {
        return findField(GuiChat.class, "defaultInputFieldText", "field_146409_v");
    }

    private static Field findField(Class<?> owner, String... names) {
        for (Class<?> current = owner; current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    // 试下一候选
                }
            }
        }
        return null;
    }
}
