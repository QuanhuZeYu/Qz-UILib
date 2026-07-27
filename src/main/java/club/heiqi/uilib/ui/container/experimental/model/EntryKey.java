package club.heiqi.uilib.ui.container.experimental.model;

import java.util.Objects;

/** Experimental long Entry 的稳定、不透明值 key；不解释为槽位或索引。 */
public final class EntryKey {
    private final String namespace;
    private final String value;

    /** 创建 experimental Entry key；两个字段均必须非空且不作规范化。 */
    public EntryKey(String namespace, String value) {
        this.namespace = nonEmpty(namespace, "namespace");
        this.value = nonEmpty(value, "value");
    }

    /** 返回原样 namespace。 */
    public String namespace() { return namespace; }

    /** 返回原样 value。 */
    public String value() { return value; }

    /** 按两个原始字符串字段比较。 */
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EntryKey)) return false;
        EntryKey that = (EntryKey) other;
        return namespace.equals(that.namespace) && value.equals(that.value);
    }

    /** 返回与严格字段值语义一致的哈希。 */
    @Override public int hashCode() { return 31 * namespace.hashCode() + value.hashCode(); }

    /** 返回不含敏感外部状态的安全文本表示。 */
    @Override public String toString() { return "EntryKey{" + namespace + ":" + value + "}"; }

    private static String nonEmpty(String text, String name) {
        Objects.requireNonNull(text, name);
        if (text.isEmpty()) throw new IllegalArgumentException(name + " must not be empty");
        return text;
    }
}
