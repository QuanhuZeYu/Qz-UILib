package club.heiqi.uilib.ui.hud.api;

/** 通用客户端 HUD 注册入口，不暴露 Minecraft、Forge、字体或 scene 内部类型。 */
public abstract class ClientHudService {
    /** 返回客户端 HUD 服务单例。服务端不得调用。 */
    public static ClientHudService getInstance() { return Holder.INSTANCE; }
    /**
     * 注册 HUD 虚拟窗口。工厂在窗口挂载时调用一次，内容树与 UI 页面同机制（scene 控件 + signal）。
     * registration 归调用 mod 所有，跨断线与世界切换保持有效，直到调用方关闭；
     * 调用与关闭均必须发生在客户端主线程。
     */
    public abstract HudRegistration register(HudSpec spec, HudWindowFactory factory);
    /** 注册其它模组已知占位，避免同锚点 HUD 重叠。 */
    public abstract HudRegistration registerAvoidance(String id, HudAvoidanceProvider provider);

    /** 延迟反射隔离客户端实现，避免服务端类加载扫描触发 Minecraft 客户端链。 */
    private static final class Holder {
        private static final ClientHudService INSTANCE = load();
        private static ClientHudService load() {
            try {
                Class<?> type = Class.forName("club.heiqi.uilib.client.hud.ClientHudServiceImpl");
                return (ClientHudService) type.getMethod("getInstance").invoke(null);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("ClientHudService is only available on the Minecraft client", exception);
            }
        }
    }
}
