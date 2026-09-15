package club.heiqi.uilib.internal.chat3.input;

import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatComponentText;

import club.heiqi.uilib.api.chat.ChatAction;
import club.heiqi.uilib.api.chat.ChatActionRegistration;
import club.heiqi.uilib.api.chat.ChatActionService;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * 「切换聊天框形态」语义动作的唯一接线点（internal/chat3）：工具栏按钮只发布切换意图，
 * 写盘与配置真源由装配层注入的端口承担（实现在 {@code config.modern}，internal 不反向
 * 依赖配置包——与 {@code ChatHudWindow.HudPlacementSource} 同一注入范式）。
 *
 * <p>动作经公共 {@link ChatActionService} 注册（与内置「编辑 HUD」同一注册链），跨聊天开关
 * 保持注册；执行限客户端主线程。切到原版后自定义聊天框（含其下挂工具栏）整体退场，本按钮
 * 随之消失，因此它天然是单向门：回切只能走配置页 / 手改 yaml 的
 * {@code general.chatFrame=custom}。</p>
 *
 * <h3>时序（为什么分三步而不是一步）</h3>
 * <ol>
 *   <li><b>先写盘</b>（{@link Persister#persistVanilla()}，不改运行态）；</li>
 *   <li><b>再把自定义聊天输入屏收回去</b>——复用既有 CLOSING 动画与渲染栈外真正关屏的路径；</li>
 *   <li><b>关屏完成后才回灌运行态</b>（{@code applyVanilla}），下一渲染帧安装器回退原版。</li>
 * </ol>
 * <p>第 3 步不能提前到第 2 步之前：输入屏还开着时运行态一旦切走，渲染帧安装器会立刻注销
 * 聊天 HUD，而该输入屏仍要渲染几帧关闭动画——那几帧会落在「HUD 已注销、输入屏仍活」的窗口里。
 * 也不能反过来先关屏再写盘：写盘失败时形态已经变了，会出现「看到的形态 ≠ 配置里的形态」。</p>
 *
 * <h3>失败语义</h3>
 * <p>写盘返回 false 时<b>不改运行态、不关屏</b>，只在聊天栏给一条提示。</p>
 */
public final class ChatFrameIntent {

    /** 内置动作 id（全局唯一）。 */
    public static final String ACTION_ID = "qzuilib:chat_frame_toggle";

    /** 排序值：第三方动作默认 0，内置「编辑 HUD」= 1000，本按钮排在其前。 */
    private static final int ACTION_ORDER = 900;

    private static final Logger LOG = LogManager.getLogger("QzUILib Chat3Frame");

    /** 写盘端口：把配置写成 vanilla（<b>不改运行态</b>）；返回是否成功。 */
    public interface Persister {
        /**
         * @return true = 已落盘；false = 未写盘，调用方不得回灌运行态
         */
        boolean persistVanilla();
    }

    /**
     * 关屏缝：接收「关屏完成回调」。生产实现 = 复用自定义聊天输入屏的既有关闭动画
     * （动画结束、真正关屏后回调）；当时没有聊天输入屏时立即回调。
     */
    private static volatile Consumer<Runnable> screenCloser = ChatFrameIntent::closeOpenChatScreen;

    /** 反馈缝（生产 = 打印到聊天栏；headless 测试可换，照 {@code ChatAccess} 的可测缝先例）。 */
    private static volatile Consumer<String> notifier = ChatFrameIntent::notifyChat;

    private static volatile Persister persister;
    private static volatile Runnable frameApplier;
    private static volatile ChatActionRegistration registration;

    private ChatFrameIntent() {
    }

    /**
     * 幂等注册内置「切换聊天框形态」动作并注入端口（客户端装配层调用，装配期一次）。
     *
     * <p>以注册表实际内容判幂等（而非只看静态句柄）：外部 {@code ChatActionService.clear()}
     * 后仍能重新装回（与 {@code ChatHudEditIntent} 同口径）。端口每次调用都覆盖，便于重装。</p>
     *
     * @param persist      写盘端口（不可为 null——显式传 null 属漏接线，立即失败而不是静默留个点不动的按钮）
     * @param applyVanilla 回灌端口的原版形态分支（不可为 null）
     */
    public static synchronized void install(Persister persist, Runnable applyVanilla) {
        if (persist == null || applyVanilla == null) {
            throw new IllegalArgumentException("persist/applyVanilla must not be null");
        }
        persister = persist;
        frameApplier = applyVanilla;
        for (ChatAction existing : ChatActionService.getInstance().actions()) {
            if (ACTION_ID.equals(existing.getId())) {
                return;
            }
        }
        registration = ChatActionService.getInstance().register(ChatAction.builder(ACTION_ID)
                .label("切换聊天框形态")
                .tooltip("切换到原版聊天框")
                .order(ACTION_ORDER)
                .visible(Signal.create(Boolean.TRUE))
                .enabled(Signal.create(Boolean.TRUE))
                .action(ChatFrameIntent::switchToVanilla)
                .build());
    }

    /** 执行切换（工具栏回调入口；写盘失败或端口异常都不改变运行态）。 */
    static void switchToVanilla() {
        Persister persist = persister;
        Runnable apply = frameApplier;
        if (persist == null || apply == null) {
            LOG.warn("聊天框形态切换端口未装配（ClientProxy 未注入），忽略本次点击");
            return;
        }
        boolean persisted;
        try {
            persisted = persist.persistVanilla();
        } catch (RuntimeException failure) {
            persisted = false;
            LOG.warn("聊天框形态切换异常", failure);
        }
        if (!persisted) {
            notifier.accept("切换原版聊天框失败：配置写入未成功，仍保持自定义聊天框。");
            return;
        }
        // 写盘已成功：关屏（含既有 CLOSING 动画）完成后才回灌运行态（见类注释「时序」）。
        screenCloser.accept(apply);
    }

    /** 生产关屏实现：有自定义聊天输入屏就走它的安全关闭路径，否则立即回调。 */
    private static void closeOpenChatScreen(Runnable onClosed) {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen screen = minecraft == null ? null : minecraft.currentScreen;
        if (screen instanceof ChatInputScreen) {
            ((ChatInputScreen) screen).closeForFrameSwitch(onClosed);
            return;
        }
        onClosed.run();
    }

    /** 生产反馈实现：原版聊天栏（接管态下即自定义聊天框）打印一行提示。 */
    private static void notifyChat(String message) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.ingameGUI == null || minecraft.ingameGUI.getChatGUI() == null) {
            return;
        }
        minecraft.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(message));
    }

    /** 测试缝回写：传 null 恢复生产实现。 */
    static void __setScreenCloserForTest(Consumer<Runnable> value) {
        screenCloser = value == null ? ChatFrameIntent::closeOpenChatScreen : value;
    }

    /** 测试缝回写：传 null 恢复生产实现。 */
    static void __setNotifierForTest(Consumer<String> value) {
        notifier = value == null ? ChatFrameIntent::notifyChat : value;
    }

    /** 测试探针：当前是否已注册。 */
    static boolean __isInstalledForTest() {
        return registration != null && !registration.isClosed();
    }
}
