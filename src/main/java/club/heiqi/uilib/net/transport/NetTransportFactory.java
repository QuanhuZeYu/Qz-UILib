package club.heiqi.uilib.net.transport;

import java.util.Locale;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.transport.forge.ForgeTransport;
import club.heiqi.uilib.net.transport.vanilla.VanillaMixinTransport;

/**
 * 网络传输适配器工厂。
 *
 * <p>未知适配器名走<b>回落 + WARN</b>，不抛异常：{@link #create(String)} 由
 * {@code CommonProxy.preInit} 直调，客户端与专用服务端的启动都挂在它上面，
 * 手改配置写错一个字母就会带走整个进程，而合法值本来就印在本类的常量里
 * （#71 同族审计 A2）。回落不改变任何合法配置的语义，system property
 * {@code qzuilib.net.transport} 的覆盖优先级也不变。</p>
 */
public final class NetTransportFactory {

    public static final String SYSTEM_PROPERTY = "qzuilib.net.transport";
    public static final String VANILLA = "vanilla";
    public static final String FORGE = "forge";

    private NetTransportFactory() {}

    /**
     * 按配置和 system property 创建传输适配器。
     *
     * @param configuredTransport 配置文件中的适配器名
     * @return 传输适配器；名字不认识时返回默认适配器 {@link #VANILLA}
     */
    public static ITransport create(String configuredTransport) {
        String selected = resolveName(configuredTransport);
        if (FORGE.equals(selected)) {
            return new ForgeTransport();
        }
        if (VANILLA.equals(selected)) {
            return new VanillaMixinTransport();
        }
        MyMod.LOG.warn("未知网络传输适配器 \"{}\"（配置值 \"{}\"），已回落默认适配器 \"{}\"；"
                        + "可选值：{} / {}。未改写配置文件，请自行订正。",
                selected, configuredTransport, VANILLA, VANILLA, FORGE);
        return new VanillaMixinTransport();
    }

    /**
     * 解析最终适配器名称。
     *
     * @param configuredTransport 配置文件中的适配器名
     * @return 适配器名称
     */
    public static String resolveName(String configuredTransport) {
        String requested = System.getProperty(SYSTEM_PROPERTY);
        if (requested == null || requested.trim().length() == 0) {
            requested = configuredTransport;
        }
        if (requested == null || requested.trim().length() == 0) {
            requested = VANILLA;
        }
        return requested.trim().toLowerCase(Locale.ROOT);
    }
}
