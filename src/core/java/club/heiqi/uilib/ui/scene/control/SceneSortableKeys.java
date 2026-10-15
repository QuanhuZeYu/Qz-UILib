package club.heiqi.uilib.ui.scene.control;

import java.util.Objects;

/** 来源、行目标、容器目标各自带角色；C 与 K 即使 equals 也不会互相排除。 */
final class SceneSortableKeys {
    private SceneSortableKeys() {}
    static final class SourceKey<K> {
        final K key;
        SourceKey(K key) { this.key = key; }
        @Override public int hashCode() { return key.hashCode(); }
        @Override public boolean equals(Object other) {
            return other instanceof SourceKey && Objects.equals(key, ((SourceKey<?>) other).key);
        }
        @Override public String toString() { return "sortable source " + key; }
    }
    static final class RowTarget<K> {
        final K key;
        RowTarget(K key) { this.key = key; }
        @Override public int hashCode() { return key.hashCode(); }
        @Override public boolean equals(Object other) {
            return other instanceof RowTarget && Objects.equals(key, ((RowTarget<?>) other).key);
        }
        @Override public String toString() { return "sortable row " + key; }
    }
    static final class ContainerTarget<C> {
        final C key;
        ContainerTarget(C key) { this.key = key; }
        @Override public int hashCode() { return key.hashCode(); }
        @Override public boolean equals(Object other) {
            return other instanceof ContainerTarget && Objects.equals(key, ((ContainerTarget<?>) other).key);
        }
        @Override public String toString() { return "sortable container " + key; }
    }
}
