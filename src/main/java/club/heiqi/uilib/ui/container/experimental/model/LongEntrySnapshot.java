package club.heiqi.uilib.ui.container.experimental.model;

import java.util.Objects;

/** Experimental confirmed Entry 快照；amount 始终为正 long。 */
public final class LongEntrySnapshot {
    private final EntryKey key;
    private final ItemDescriptor item;
    private final long amount;

    /** 创建不可变 Entry 快照；不判断 backend identity 或 itemMax。 */
    public LongEntrySnapshot(EntryKey key, ItemDescriptor item, long amount) {
        this.key = Objects.requireNonNull(key, "key");
        this.item = Objects.requireNonNull(item, "item");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        this.amount = amount;
    }
    /** 返回 key。 */
    public EntryKey key() { return key; }
    /** 返回 descriptor。 */
    public ItemDescriptor item() { return item; }
    /** 返回 long 数量。 */
    public long amount() { return amount; }

    /** 按 key、descriptor 与 amount 比较。 */
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LongEntrySnapshot)) return false;
        LongEntrySnapshot that = (LongEntrySnapshot) other;
        return amount == that.amount && key.equals(that.key) && item.equals(that.item);
    }
    /** 返回与 Entry 值语义一致的哈希。 */
    @Override public int hashCode() { return 31 * (31 * key.hashCode() + item.hashCode()) + (int) (amount ^ (amount >>> 32)); }
    /** 返回可读且不含外部可变状态的表示。 */
    @Override public String toString() { return "LongEntrySnapshot{" + key + ", amount=" + amount + ", item=" + item + "}"; }
}
