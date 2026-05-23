package club.heiqi.uilib.net.api;

import java.util.Objects;

/**
 * Fetch endpoint 标识。
 */
public final class NetEndpointId {

    private final String namespace;
    private final String name;

    private NetEndpointId(String namespace, String name) {
        this.namespace = validate("namespace", namespace);
        this.name = validate("name", name);
    }

    /**
     * 创建 endpoint 标识。
     *
     * @param namespace 命名空间
     * @param name 名称
     * @return 标识
     */
    public static NetEndpointId of(String namespace, String name) {
        return new NetEndpointId(namespace, name);
    }

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
        if (!(other instanceof NetEndpointId)) {
            return false;
        }
        NetEndpointId that = (NetEndpointId) other;
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
