package club.heiqi.uilib.ui.container.experimental.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Experimental 有序、不可变容器快照；key 在列表内唯一。 */
public final class LongContainerSnapshot {
    private final List<LongEntrySnapshot> entries;

    /** 复制并冻结 entries，拒绝 null Entry 与重复 key。 */
    public LongContainerSnapshot(List<LongEntrySnapshot> entries) {
        Objects.requireNonNull(entries, "entries");
        List<LongEntrySnapshot> copy = new ArrayList<LongEntrySnapshot>(entries.size());
        Set<EntryKey> keys = new HashSet<EntryKey>();
        for (LongEntrySnapshot entry : entries) {
            Objects.requireNonNull(entry, "entry");
            if (!keys.add(entry.key())) throw new IllegalArgumentException("duplicate entry key: " + entry.key());
            copy.add(entry);
        }
        this.entries = Collections.unmodifiableList(copy);
    }
    /** 返回有序且不可修改的 entries 视图。 */
    public List<LongEntrySnapshot> entries() { return entries; }
    /** 按 entries 顺序和值比较。 */
    @Override public boolean equals(Object other) { return this == other || other instanceof LongContainerSnapshot && entries.equals(((LongContainerSnapshot) other).entries); }
    /** 返回与有序 entries 值语义一致的哈希。 */
    @Override public int hashCode() { return entries.hashCode(); }
    /** 返回有序快照的安全文本表示。 */
    @Override public String toString() { return "LongContainerSnapshot{" + entries + "}"; }
}
