package club.heiqi.uilib.ui.container.experimental.operation;

import java.util.Objects;
import club.heiqi.uilib.ui.container.experimental.model.EntryKey;

/** Experimental 有限语义操作意图；不携带 raw 输入、数量或玩家槽信息。 */
public final class LongContainerIntent {
    /** 支持的七种语义操作。 */
    public enum Kind { TAKE_STACK, TAKE_HALF_STACK, DEPOSIT_ALL, DEPOSIT_ONE, QUICK_EXTRACT, DROP_ONE, DROP_STACK }
    private final Kind kind;
    private final EntryKey key;
    private LongContainerIntent(Kind kind, EntryKey key) { this.kind = kind; this.key = key; }
    /** 创建取整堆意图。 */ public static LongContainerIntent takeStack(EntryKey key) { return keyed(Kind.TAKE_STACK, key); }
    /** 创建取半堆意图。 */ public static LongContainerIntent takeHalfStack(EntryKey key) { return keyed(Kind.TAKE_HALF_STACK, key); }
    /** 创建全量存入意图。 */ public static LongContainerIntent depositAll() { return new LongContainerIntent(Kind.DEPOSIT_ALL, null); }
    /** 创建存入一个意图。 */ public static LongContainerIntent depositOne() { return new LongContainerIntent(Kind.DEPOSIT_ONE, null); }
    /** 创建快捷提取意图。 */ public static LongContainerIntent quickExtract(EntryKey key) { return keyed(Kind.QUICK_EXTRACT, key); }
    /** 创建丢弃一个意图。 */ public static LongContainerIntent dropOne(EntryKey key) { return keyed(Kind.DROP_ONE, key); }
    /** 创建丢弃整堆意图。 */ public static LongContainerIntent dropStack(EntryKey key) { return keyed(Kind.DROP_STACK, key); }
    /** 返回语义种类。 */ public Kind kind() { return kind; }
    /** 返回 key；deposit 意图返回 null。 */ public EntryKey key() { return key; }
    /** 按 kind 与可空 key 比较。 */
    @Override public boolean equals(Object other) { return this == other || other instanceof LongContainerIntent && kind == ((LongContainerIntent) other).kind && Objects.equals(key, ((LongContainerIntent) other).key); }
    /** 返回与 intent 值语义一致的哈希。 */
    @Override public int hashCode() { return 31 * kind.hashCode() + Objects.hashCode(key); }
    /** 返回不含 raw 输入的安全文本表示。 */
    @Override public String toString() { return "LongContainerIntent{" + kind + (key == null ? "" : ", key=" + key) + "}"; }
    private static LongContainerIntent keyed(Kind kind, EntryKey key) { return new LongContainerIntent(kind, Objects.requireNonNull(key, "key")); }
}
