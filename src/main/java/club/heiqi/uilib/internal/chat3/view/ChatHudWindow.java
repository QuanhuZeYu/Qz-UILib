package club.heiqi.uilib.internal.chat3.view;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.input.ChatHudEditIntent;
import club.heiqi.uilib.ui.hud.api.ClientHudService;
import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.hud.api.HudRegistration;
import club.heiqi.uilib.ui.hud.api.HudSpec;
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
    }

    /** 测试探针:注入控制器(绕过 HUD 注册,headless 测试接线层用)。 */
    public static synchronized void __setControllerForTest(ChatSceneController value) {
        controller = value;
    }
}
