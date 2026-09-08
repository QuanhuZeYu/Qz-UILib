package club.heiqi.uilib.internal.chat3.input;

import club.heiqi.uilib.api.chat.ChatAction;
import club.heiqi.uilib.api.chat.ChatActionRegistration;
import club.heiqi.uilib.api.chat.ChatActionService;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * 「编辑 HUD」语义动作的唯一接线点（internal/chat3）：动作 handler 只发布意图，
 * 由当前打开的聊天输入屏（{@link Sink}）消费并切换编辑子模式。
 *
 * <p>动作经公共 {@link ChatActionService} 注册（内置动作与第三方动作同一注册链），
 * 跨聊天开关保持注册；屏幕关闭后 detach，意图自然落空。执行限客户端主线程。</p>
 */
public final class ChatHudEditIntent {

    /** 内置动作 id（全局唯一）。 */
    public static final String ACTION_ID = "qzuilib:hud_edit";
    /** 内置动作排序值（普通动作默认 0，内置入口排在末尾）。 */
    private static final int ACTION_ORDER = 1000;

    /** 编辑意图消费端口。 */
    public interface Sink {
        /** 请求进入 HUD 编辑子模式（当前已编辑则幂等）。 */
        void requestEnterEdit();
    }

    private static volatile Sink sink;
    private static volatile ChatActionRegistration registration;

    private ChatHudEditIntent() {
    }

    /**
     * 幂等注册内置「编辑 HUD」动作（首次打开聊天时调用）。
     *
     * <p>以注册表实际内容为准判幂等（而非只看静态句柄）：外部 {@code ChatActionService.clear()}
     * 后仍能重新装回，测试隔离与接管重装都安全。</p>
     */
    public static synchronized void install() {
        for (ChatAction existing : ChatActionService.getInstance().actions()) {
            if (ACTION_ID.equals(existing.getId())) {
                return;
            }
        }
        registration = ChatActionService.getInstance().register(ChatAction.builder(ACTION_ID)
                .label("编辑 HUD")
                .tooltip("拖动聊天框，调整 HUD 布局")
                .order(ACTION_ORDER)
                .visible(Signal.create(Boolean.TRUE))
                .enabled(Signal.create(Boolean.TRUE))
                .action(ChatHudEditIntent::requestEnterEdit)
                .build());
    }

    /** 绑定当前打开的聊天输入屏（打开时调用）。 */
    static void attach(Sink value) {
        sink = value;
    }

    /** 解绑（仅当仍是同一 sink 时生效，避免旧屏关闭顶掉新屏）。 */
    static void detach(Sink value) {
        if (sink == value) {
            sink = null;
        }
    }

    /** 发布进入编辑意图（无活动屏幕时空操作）。 */
    public static void requestEnterEdit() {
        Sink current = sink;
        if (current != null) {
            current.requestEnterEdit();
        }
    }

    /** 测试探针：当前是否已注册。 */
    static boolean __isInstalledForTest() {
        return registration != null && !registration.isClosed();
    }
}
