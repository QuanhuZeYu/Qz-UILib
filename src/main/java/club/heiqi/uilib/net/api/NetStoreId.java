package club.heiqi.uilib.net.api;

import java.util.Objects;

/**
 * Store 标识。
 */
public final class NetStoreId {

    private final String namespace;
    private final String name;

    private NetStoreId(String namespace, String name) {
        this.namespace = validate("namespace", namespace);
        this.name = validate("name", name);
    }

    /**
     * 创建 Store 标识。
     *
     * @param namespace 命名空间
     * @param name 名称
     * @return 标识
     */
    public static NetStoreId of(String namespace, String name) {
        return new NetStoreId(namespace, name);
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
        if (!(other instanceof NetStoreId)) {
            return false;
        }
        NetStoreId that = (NetStoreId) other;
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
