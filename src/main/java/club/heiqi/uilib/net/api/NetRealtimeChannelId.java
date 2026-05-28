package club.heiqi.uilib.net.api;

import java.util.Objects;

/**
 * 实时 Channel 标识。
 */
public final class NetRealtimeChannelId {

    private final String namespace;
    private final String name;

    private NetRealtimeChannelId(String namespace, String name) {
        this.namespace = validate("namespace", namespace);
        this.name = validate("name", name);
    }

    /**
     * 创建实时 Channel 标识。
     *
     * @param namespace 命名空间
     * @param name 名称
     * @return 标识
     */
    public static NetRealtimeChannelId of(String namespace, String name) {
        return new NetRealtimeChannelId(namespace, name);
    }

    /**
     * 返回线协议 key。
     *
     * @return key
     */
    public String asKey() {
        return namespace + ":" + name;
    }

    @Override
    public String toString() {
        return asKey();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NetRealtimeChannelId)) {
            return false;
        }
        NetRealtimeChannelId that = (NetRealtimeChannelId) other;
        return namespace.equals(that.namespace) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, name);
    }

    private static String validate(String label, String value) {
        if (value == null || value.trim().length() == 0) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
