package club.heiqi.uilib.internal.chat3.view;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.input.ChatHudEditIntent;
import club.heiqi.uilib.internal.chat3.input.ChatToolbar;
import club.heiqi.uilib.ui.hud.api.ClientHudService;
import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.hud.api.HudRegistration;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.hud.api.HudToolbarService;
import club.heiqi.uilib.ui.hud.api.HudToolbarSide;
import club.heiqi.uilib.ui.hud.api.HudToolbarSpec;
import club.heiqi.uilib.ui.hud.api.HudVisibility;

/**
 * 聊天 3.0 HUD 窗口注册(L3 渲染层装配点):把聊天内容树注册为 BOTTOM_LEFT 悬浮窗口。
 *
 * <p>chrome = false:无宿主外壳,气泡直接浮在画面上(现代观感)。visibility = IN_WORLD:
 * 打开聊天输入屏(普通 GuiScreen)时窗口仍渲染——容器形态在输入屏期间可见(双形态需求)。
 * 渲染驱动点 = 接线层每帧调 {@link ChatSceneController#tick}(S4),不依赖 HUD 服务异步帧循环。</p>
 */
public final class ChatHudWindow {

    /** 注册 id(全局唯一)。 */
    public static final String HUD_ID = "qzuilib:chat3";

    /** BOTTOM_LEFT 同锚点堆叠顺序:聊天是最底常驻窗口(stackOrder 越小越靠下)。 */
    private static final int STACK_ORDER = -1000;

    /**
     * 宿主权威放置盒查询端口：由 client 装配层注入（composition root 在接线层，
     * internal 包不反向依赖 client 包）。未注入时 chat3 命中走 SceneAnchorResolver 兜底。
     */
    @FunctionalInterface
    public interface HudPlacementSource {
        /** @return 该 id 最近一帧的放置盒（视口逻辑 px）；未放置时 null */
        AnchorRect placement(String hudId);
    }

    /**
     * 宿主安全区查询端口（由 client 装配层注入）：打开态聊天容器与关闭态 HUD 共用同一份
     * 安全区事实，避免两套坐标/占位口径（规划 P2「双形态共用宿主坐标转换与安全区事实」）。
     */
    @FunctionalInterface
    public interface HudSafeAreaSource {
        /** @return 最近一帧宿主安全区；不可用时返回 {@link HudInsets#NONE} */
        HudInsets insets();
    }

    private static volatile HudRegistration registration;
    private static volatile ChatSceneController controller;
    private static volatile HudPlacementSource placementSource;
    private static volatile HudSafeAreaSource safeAreaSource;
    /** 聊天外接工具栏挂载边（默认下边；改动只影响后续注册/打开的层，已挂载层不热切换）。 */
    private static volatile HudToolbarSide toolbarSide = HudToolbarSide.DEFAULT;
    /** 工具栏可见性真值（同步读写）：聊天输入屏打开时为真；关闭态 HUD 不显示工具栏。 */
    private static volatile boolean toolbarVisible;
    /** 可见性变更通知（Signal 帧末才生效，只用来驱动外接层的挂/摘 effect）。 */
    private static final Signal<Boolean> TOOLBAR_VISIBLE_EPOCH = Signal.create(Boolean.FALSE);
    /**
     * 工具栏可见性端口：同步返回真值（挂载期即可判定首帧外框），并订阅变更信号驱动挂/摘。
     *
     * <p>直接暴露 {@link Signal} 会让"同一次调用里 attach 后立刻挂载"读到帧末才生效的旧值，
     * 首帧外框少算工具栏厚度；这里把真值放 volatile、把变更放 Signal，两件事各归其位。</p>
     */
    private static final ReadableSignal<Boolean> TOOLBAR_VISIBLE = new ReadableSignal<Boolean>() {
        @Override
        public Boolean get() {
            TOOLBAR_VISIBLE_EPOCH.get(); // 响应式上下文内登记依赖（驱动挂/摘）
            return Boolean.valueOf(toolbarVisible);
        }
    };
    /** 当前打开态聊天屏的工具栏宿主端口（编辑态信号 + 编辑动作）；未打开时为 null。 */
    private static volatile ChatToolbar.Host toolbarHost;
    private static volatile HudRegistration toolbarRegistration;

    private ChatHudWindow() {
    }

    /**
     * 幂等注册聊天 HUD 窗口,返回全局控制器(同一实例供接线层共用)。
     *
     * @return 聊天场景控制器(非 null)
     */
    public static synchronized ChatSceneController ensureRegistered() {
        // 内置「编辑 HUD」动作与第三方动作走同一注册链（幂等；跨聊天开关保持注册）。
        ChatHudEditIntent.install();
        // HUD 级外接工具栏：聊天 HUD 是首个使用者（规格 + 工厂只此一份）。
        ensureToolbarRegistered();
        if (registration == null || registration.isClosed()) {
            ChatSceneController instance = new ChatSceneController();
            instance.attachPlacementSource(placementSource);
            HudSpec spec = HudSpec.builder(HUD_ID)
                    .anchor(HudAnchor.BOTTOM_LEFT)
                    .visibility(HudVisibility.IN_WORLD)
                    .stackOrder(STACK_ORDER)
                    .margin(ChatMarkdownSettings.getChatMarginPx())
                    .maxWidth(4096) // 内容根宽度随视口动态(chatWidthFor),此处只给硬上限
                    .chrome(false)
                    .build();
            registration = ClientHudService.getInstance().register(spec, rt -> instance.buildContent(rt));
            controller = instance;
        }
        return controller;
    }

    /** @return 当前控制器;未注册时 null */
    public static ChatSceneController controller() {
        return controller;
    }

    /** 装配层注入宿主放置端口（幂等；同步到当前与后续控制器实例）。 */
    public static synchronized void setPlacementSource(HudPlacementSource source) {
        placementSource = source;
        ChatSceneController instance = controller;
        if (instance != null) {
            instance.attachPlacementSource(source);
        }
    }

    /** 装配层注入宿主安全区端口（幂等）。 */
    public static synchronized void setSafeAreaSource(HudSafeAreaSource source) {
        safeAreaSource = source;
    }

    /** @return 最近一帧宿主安全区（未注入/不可用时 NONE，调用方无需判空） */
    public static HudInsets currentSafeInsets() {
        HudSafeAreaSource source = safeAreaSource;
        if (source == null) {
            return HudInsets.NONE;
        }
        try {
            HudInsets insets = source.insets();
            return insets == null ? HudInsets.NONE : insets;
        } catch (RuntimeException failure) {
            return HudInsets.NONE;
        }
    }

    /** 关闭窗口(总开关关闭/逃生舱回退原版时调用;幂等)。 */
    public static synchronized void close() {
        if (registration != null) {
            registration.close();
        }
        registration = null;
        controller = null;
        if (toolbarRegistration != null) {
            toolbarRegistration.close();
        }
        toolbarRegistration = null;
    }

    // ==================== 外接工具栏（HUD 级注册；规划 P1/P2 增量） ====================

    /**
     * 幂等注册聊天外接工具栏（{@link HudToolbarService}；聊天 HUD 是首个使用者）。
     *
     * <p>以注册表实际内容判幂等（而非只看静态句柄）：外部 {@code clear()} 后仍能装回，
     * 测试隔离与接管重装都安全。工具栏工厂在装配时取"当前打开态聊天屏"的宿主端口，
     * 关闭态 HUD 取惰性宿主（且该形态工具栏不可见）。</p>
     */
    private static void ensureToolbarRegistered() {
        if (toolbarRegistration != null && !toolbarRegistration.isClosed()) {
            return;
        }
        if (HudToolbarService.getInstance().hasToolbar(HUD_ID)) {
            return;
        }
        toolbarRegistration = HudToolbarService.getInstance().register(HUD_ID, chatToolbarSpec(),
                rt -> ChatToolbar.mount(rt, currentToolbarHost()));
    }

    /**
     * 聊天 HUD 的外接工具栏规格（纯函数：不注册、不碰宿主，供装配与测试读取）。
     *
     * @return 规格：边 = 当前 {@link #getToolbarSide()}，间隙/厚度取
     *         {@link HudToolbarSpec} 默认值，可见性 = 聊天输入屏打开信号
     */
    public static HudToolbarSpec chatToolbarSpec() {
        return HudToolbarSpec.builder(toolbarSide).visible(TOOLBAR_VISIBLE).build();
    }

    /** @return 当前聊天工具栏挂载边 */
    public static HudToolbarSide getToolbarSide() {
        return toolbarSide;
    }

    /**
     * 设置聊天工具栏挂载边（"聊天配置边位"入口）。
     *
     * <p>仅影响后续注册/打开的工具栏层：已挂载的层持有旧规格，重新打开聊天屏后生效。
     * 已注册时同步重装注册项，让关闭态 HUD 的保留窗口在下一次注册表版本变化时重建。</p>
     */
    public static synchronized void setToolbarSide(HudToolbarSide side) {
        if (side == null || side == toolbarSide) {
            return;
        }
        toolbarSide = side;
        if (toolbarRegistration != null && !toolbarRegistration.isClosed()) {
            toolbarRegistration.close();
            toolbarRegistration = null;
            ensureToolbarRegistered();
        }
    }

    /** 打开态聊天屏装配时绑定工具栏宿主（同步置可见，供同一次调用里的外接层装配读首帧外框）。 */
    public static synchronized void attachToolbarHost(ChatToolbar.Host host) {
        toolbarHost = host;
        toolbarVisible = true;
        TOOLBAR_VISIBLE_EPOCH.set(Boolean.TRUE);
    }

    /** 打开态聊天屏关闭时解绑（仅当仍是同一宿主，避免旧屏关闭顶掉新屏）。 */
    public static synchronized void detachToolbarHost(ChatToolbar.Host host) {
        if (toolbarHost == host) {
            toolbarHost = null;
            toolbarVisible = false;
            TOOLBAR_VISIBLE_EPOCH.set(Boolean.FALSE);
        }
    }

    /** @return 当前工具栏宿主；无打开态屏幕时为惰性宿主（编辑动作为空操作） */
    private static ChatToolbar.Host currentToolbarHost() {
        ChatToolbar.Host host = toolbarHost;
        return host != null ? host : ChatToolbar.inertHost();
    }

    /** 测试探针:注入控制器(绕过 HUD 注册,headless 测试接线层用)。 */
    public static synchronized void __setControllerForTest(ChatSceneController value) {
        controller = value;
    }
}
