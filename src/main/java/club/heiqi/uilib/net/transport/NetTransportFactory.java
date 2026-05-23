package club.heiqi.uilib.net.transport;

import java.util.Locale;

import club.heiqi.uilib.net.transport.forge.ForgeTransport;
import club.heiqi.uilib.net.transport.vanilla.VanillaMixinTransport;

/**
 * 网络传输适配器工厂。
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
     * @return 传输适配器
     */
    public static ITransport create(String configuredTransport) {
        String selected = resolveName(configuredTransport);
        if (FORGE.equals(selected)) {
            return new ForgeTransport();
        }
        if (VANILLA.equals(selected)) {
            return new VanillaMixinTransport();
        }
        throw new IllegalArgumentException("未知网络传输适配器: " + selected);
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
