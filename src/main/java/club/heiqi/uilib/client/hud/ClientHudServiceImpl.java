package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.hud.api.ClientHudService;
import club.heiqi.uilib.ui.hud.api.HudAvoidanceProvider;
import club.heiqi.uilib.ui.hud.api.HudRegistration;
import club.heiqi.uilib.ui.hud.api.HudSnapshotProvider;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import net.minecraft.client.Minecraft;

/** Minecraft 客户端 HUD 服务实现。 */
public final class ClientHudServiceImpl extends ClientHudService {
    private static final ClientHudServiceImpl INSTANCE = new ClientHudServiceImpl();
    private final HudRegistry registry = new HudRegistry();
    private ClientHudServiceImpl() {}
    /** 返回客户端实现单例。 */
    public static ClientHudServiceImpl getInstance() { return INSTANCE; }

    @Override public HudRegistration register(HudSpec spec, HudSnapshotProvider provider) {
        assertClientThread(); return threadChecked(registry.register(spec, provider));
    }
    @Override public HudRegistration registerAvoidance(String id, HudAvoidanceProvider provider) {
        assertClientThread(); return threadChecked(registry.registerAvoidance(id, provider));
    }
    /** 返回内部注册表供唯一 Forge bridge 消费。 */
    HudRegistry registry() { return registry; }
    /** 断线或世界卸载时释放注册资源。 */
    public void clearWorld() { assertClientThread(); registry.clear(); }

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
