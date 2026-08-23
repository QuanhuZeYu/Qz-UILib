package club.heiqi.uilib.internal.chat3.wiring;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiNewChat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.api.chat.ChatAccess;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.view.ChatHudWindow;
import club.heiqi.uilib.internal.chat3.view.ChatSceneController;

/**
 * 聊天系统 3.0 安装器:把 GuiIngame.persistantChatGUI 替换为 {@link ChatFacade}(架空原版),
 * 原版实例留存引用;开关关闭时写回原版实例(逃生舱,零残留)。
 *
 * <p>装配语义:</p>
 * <ul>
 *   <li>每渲染 tick 调用一次(幂等);启用 → 确保字段为 ChatFacade;关闭 → 确保字段为原版实例;</li>
 *   <li>final 字段:先尝试移除 FINAL 修饰符(Java 8 可行;被拒绝时直接 set 仍可能成功);</li>
 *   <li><b>写后读回验证</b>:JIT 可能把 final 实例字段读折叠,静默吞掉替换(第一轮实证),读回非本实例即视为失败并告警;</li>
 *   <li>S0 阶段 Facade 全继承原版行为,替换后与原版零回归。</li>
 * </ul>
 */
public final class ChatMarkdownInstaller {

    private static final Logger LOG = LogManager.getLogger("QzUILib Chat3");

    /** GuiIngame 聊天实例字段(mcp 名 persistantChatGUI,原版拼写;沿父类链发现)。 */
    private static final Field CHAT_FIELD = findChatField();

    /** 首次替换时留存的原版实例(逃生舱写回目标)。 */
    private static GuiNewChat originalChat = null;

    /** 当前接管状态(status 命令用;installIfNeeded 内维护)。 */
    private static volatile boolean installed = false;

    /** 聊天打开感知(输入屏开关;每渲染帧由 tickController 同步)。 */
    private static boolean lastChatOpen = false;

    private static boolean finalRemoved = false;

    private ChatMarkdownInstaller() {
    }

    /**
     * 每渲染 tick 调用:按总开关安装/恢复,幂等,任何装配异常不向上抛(聊天渲染永不被本安装器打断)。
     */
    public static synchronized void installIfNeeded() {
        if (CHAT_FIELD == null) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null || mc.ingameGUI == null) {
                return;
            }
            if (ChatMarkdownSettings.isEnabled()) {
                ensureInstalled(mc);
            } else {
                ensureRestored(mc);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOG.warn("聊天 3.0 接管装配失败: {}", failure.toString());
        }
    }

    /**
     * 每渲染帧推进(接线层在渲染 tick 调用):聊天打开感知 + 淡出/动画时钟 + 宿主视口写入。
     * GuiChat 打开时 GuiIngame 不再调 drawChat,容器动画由本入口持续驱动。
     */
    public static synchronized void tickController() {
        ChatSceneController controller = ChatHudWindow.controller();
        if (controller == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        boolean open = mc.currentScreen instanceof GuiChat;
        if (open != lastChatOpen) {
            lastChatOpen = open;
            controller.setChatOpen(open);
        }
        controller.setHostViewport(mc.displayWidth, mc.displayHeight);
        controller.tick(System.currentTimeMillis());
    }

    /** 启用:确保字段为 ChatFacade 实例;首次替换留存原版实例。 */
    private static void ensureInstalled(Minecraft mc) throws ReflectiveOperationException {
        Object current = CHAT_FIELD.get(mc.ingameGUI);
        if (current instanceof ChatFacade) {
            installed = true;
            return;
        }
        if (originalChat == null && current instanceof GuiNewChat) {
            originalChat = (GuiNewChat) current;
        }
        tryRemoveFinal();
        CHAT_FIELD.set(mc.ingameGUI, new ChatFacade(mc, ChatHudWindow.ensureRegistered()));
        Object verified = CHAT_FIELD.get(mc.ingameGUI);
        if (verified instanceof ChatFacade) {
            installed = true;
            ChatAccess.getInstance().setTakeoverActive(true);
            LOG.info("聊天系统 3.0 接管已安装(架空原版)");
        } else {
            installed = false;
            ChatAccess.getInstance().setTakeoverActive(false);
            LOG.warn("聊天 3.0 读回验证失败(字段仍被占用): {}", verified);
        }
    }

    /** 关闭:把留存的原版实例写回,零残留。 */
    private static void ensureRestored(Minecraft mc) throws ReflectiveOperationException {
        Object current = CHAT_FIELD.get(mc.ingameGUI);
        if (!(current instanceof ChatFacade)) {
            installed = false;
            return;
        }
        if (originalChat == null) {
            return;
        }
        tryRemoveFinal();
        CHAT_FIELD.set(mc.ingameGUI, originalChat);
        Object verified = CHAT_FIELD.get(mc.ingameGUI);
        if (!(verified instanceof ChatFacade)) {
            installed = false;
            ChatAccess.getInstance().setTakeoverActive(false);
            ChatHudWindow.close();
            LOG.info("聊天 3.0 已回退原版对话框(逃生舱)");
        }
    }

    /** @return 当前接管状态(status 命令用) */
    public static boolean isInstalled() {
        return installed;
    }

    /** 字段发现(mcp 名 persistantChatGUI,沿父类链;headless 可测)。 */
    static Field findChatField() {
        for (Class<?> current = GuiIngame.class; current != null && current != Object.class;
                current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField("persistantChatGUI");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // 继续向父类找
            }
        }
        return null;
    }

    /** 尝试移除 final 修饰符(Java 8 可行;被拒绝时忽略——直接 set 仍可能成功)。 */
    private static void tryRemoveFinal() {
        if (finalRemoved) {
            return;
        }
        try {
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(CHAT_FIELD, CHAT_FIELD.getModifiers() & ~Modifier.FINAL);
        } catch (ReflectiveOperationException ignored) {
            // 忽略:实例 final 字段(非编译期常量)直接 set 仍可能成功
        }
        finalRemoved = true;
    }
}
