package club.heiqi.uilib.ui.container.experimental.model;

import java.util.Arrays;
import java.util.Objects;

/** Experimental 物品身份值；不携带 EntryKey、live ItemStack 或 UI 状态。 */
public final class ItemDescriptor {
    private final String typeId;
    private final String codecId;
    private final byte[] payload;

    /** 创建不可变 descriptor，并复制 payload。 */
    public ItemDescriptor(String typeId, String codecId, byte[] payload) {
        this.typeId = nonEmpty(typeId, "typeId");
        this.codecId = nonEmpty(codecId, "codecId");
        this.payload = Arrays.copyOf(Objects.requireNonNull(payload, "payload"), payload.length);
    }

    /** 返回物品类型标识。 */
    public String typeId() { return typeId; }
    /** 返回编码器标识。 */
    public String codecId() { return codecId; }
    /** 返回 payload 的防御性副本。 */
    public byte[] payload() { return Arrays.copyOf(payload, payload.length); }

    /** 按标识字符串与 payload 字节内容比较。 */
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ItemDescriptor)) return false;
        ItemDescriptor that = (ItemDescriptor) other;
        return typeId.equals(that.typeId) && codecId.equals(that.codecId)
                && Arrays.equals(payload, that.payload);
    }
    /** 返回与结构值语义一致的哈希。 */
    @Override public int hashCode() { return 31 * (31 * typeId.hashCode() + codecId.hashCode()) + Arrays.hashCode(payload); }
    /** 返回不泄露 payload 内容的安全文本表示。 */
    @Override public String toString() { return "ItemDescriptor{" + typeId + ", codec=" + codecId + ", payloadLength=" + payload.length + "}"; }

    private static String nonEmpty(String text, String name) {
        Objects.requireNonNull(text, name);
        if (text.isEmpty()) throw new IllegalArgumentException(name + " must not be empty");
        return text;
    }
}
