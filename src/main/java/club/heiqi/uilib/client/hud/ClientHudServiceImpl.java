package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.hud.api.ClientHudService;
import club.heiqi.uilib.ui.hud.api.HudAvoidanceProvider;
import club.heiqi.uilib.ui.hud.api.HudRegistration;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.hud.api.HudWindowFactory;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import net.minecraft.client.Minecraft;

/** Minecraft 客户端 HUD 服务实现。 */
public final class ClientHudServiceImpl extends ClientHudService {
    private static final ClientHudServiceImpl INSTANCE = new ClientHudServiceImpl();
    private final HudRegistry registry = new HudRegistry();
    private volatile SceneHudHost host;
    private ClientHudServiceImpl() {}
    /** 返回客户端实现单例。 */
    public static ClientHudServiceImpl getInstance() { return INSTANCE; }

    @Override public HudRegistration register(HudSpec spec, HudWindowFactory factory) {
        assertClientThread(); return threadChecked(registry.register(spec, factory));
    }
    @Override public HudRegistration registerAvoidance(String id, HudAvoidanceProvider provider) {
        assertClientThread(); return threadChecked(registry.registerAvoidance(id, provider));
    }
    /** 返回内部注册表供唯一 Forge bridge 消费。 */
    HudRegistry registry() { return registry; }

    /** 由唯一生产 HUD host 构造时自附（后附覆盖先附，生产仅一实例）。 */
    void attachHost(SceneHudHost value) { this.host = value; }

    /**
     * 最近一帧某 HUD 窗口的权威放置盒（视口逻辑 px）；host 未运行或未放置该窗时 null。
     *
     * <p>供仓内投放方（chat3 命中检测）以宿主实际放置为准，替代自行反推锚点数学的第二事实源。
     * 非 mod 面向 API（mod 用 {@code ui.hud.api}）；与 render 同为客户端主线程。</p>
     */
    public AnchorRect currentPlacement(String hudId) {
        SceneHudHost value = host;
        return value == null ? null : value.currentPlacement(hudId);
    }
    private static void assertClientThread() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && !minecraft.func_152345_ab()) {
            throw new IllegalStateException("HUD register/close must run on the client main thread");
        }
    }

    private static HudRegistration threadChecked(HudRegistration delegate) {
        return new HudRegistration() {
            @Override public void close() { assertClientThread(); delegate.close(); }
            @Override public boolean isClosed() { return delegate.isClosed(); }
        };
    }
}
