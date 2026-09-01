package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.hud.api.ClientHudService;
import club.heiqi.uilib.ui.hud.api.HudAvoidanceProvider;
import club.heiqi.uilib.ui.hud.api.HudRegistration;
import club.heiqi.uilib.ui.hud.api.HudSpec;
import club.heiqi.uilib.ui.hud.api.HudWindowFactory;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Minecraft 客户端 HUD 服务实现。 */
public final class ClientHudServiceImpl extends ClientHudService {
    private static final Logger LOG = LogManager.getLogger("QzUILib HudThread");
    private static final ClientHudServiceImpl INSTANCE = new ClientHudServiceImpl();
    private final HudRegistry registry = new HudRegistry();
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
    private static void assertClientThread() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            // D5a：headless（测试/服务端类加载）不再静默放行——契约不变但留下可诊断痕迹
            LOG.warn("HUD register/close 线程校验被跳过（Minecraft 不可用，headless 环境）");
            return;
        }
        if (!minecraft.func_152345_ab()) {
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
