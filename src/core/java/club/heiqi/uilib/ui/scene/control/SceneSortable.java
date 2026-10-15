package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** 单一集合拥有全部排序身份；拖中只呈现候选，正常放下发布 Move。 */
public final class SceneSortable {
    public enum Layout { VERTICAL, HORIZONTAL, GRID }

    public static final class Group<C, T> {
        public final C key;
        public final List<T> items;
        public Group(C key, List<T> items) {
            this.key = Objects.requireNonNull(key, "group key");
            this.items = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(items, "items")));
        }
    }
    public static final class Row {
        public final SceneNode content;
        public final SceneNode[] handles;
        public Row(SceneNode content, SceneNode... handles) {
            this.content = Objects.requireNonNull(content, "content");
            this.handles = Objects.requireNonNull(handles, "handles").clone();
            for (SceneNode handle : this.handles) Objects.requireNonNull(handle, "handle");
        }
    }
    /** root 可包含标题等固定装饰；items 的孩子声明由本控件独占。 */
    public static final class Container {
        public final SceneNode root, items;
        public final Layout layout;
        public Container(SceneNode root, SceneNode items, Layout layout) {
            this.root = Objects.requireNonNull(root, "root");
            this.items = Objects.requireNonNull(items, "items");
            this.layout = Objects.requireNonNull(layout, "layout");
        }
    }
    public static final class Move<K, C> {
        public final K key, beforeKey;
        public final C containerKey;
        Move(K key, C containerKey, K beforeKey) {
            this.key = key; this.containerKey = containerKey; this.beforeKey = beforeKey;
        }
    }
    public static final class Options<T, C> {
        final Predicate<T> draggable;
        final BiPredicate<C, T> accepts;
        final boolean autoScroll;
        final double autoScrollSpeed;
        public Options() { this(value -> true, (container, value) -> true, true, 1.0); }
        private Options(Predicate<T> draggable, BiPredicate<C, T> accepts, boolean autoScroll, double speed) {
            this.draggable = draggable; this.accepts = accepts; this.autoScroll = autoScroll; autoScrollSpeed = speed;
        }
        public Options<T, C> draggable(Predicate<T> allowed) {
            return new Options<>(Objects.requireNonNull(allowed, "draggable"), accepts, autoScroll, autoScrollSpeed);
        }
        public Options<T, C> accepts(BiPredicate<C, T> allowed) {
            return new Options<>(draggable, Objects.requireNonNull(allowed, "accepts"), autoScroll, autoScrollSpeed);
        }
        public Options<T, C> autoScroll(boolean enabled) { return new Options<>(draggable, accepts, enabled, autoScrollSpeed); }
        public Options<T, C> autoScrollSpeed(double multiplier) {
            if (!Double.isFinite(multiplier) || multiplier < 0) throw new IllegalArgumentException("自动滚动速度倍率无效");
            return new Options<>(draggable, accepts, autoScroll, multiplier);
        }
    }
    private SceneSortable() {}

    public static <T, K, C> SceneNode create(ScenePageContext context, ReadableSignal<List<Group<C, T>>> groups,
            Function<T, K> key, Function<C, Container> containerFactory, Function<ReadableSignal<T>, Row> rowFactory,
            Function<ReadableSignal<T>, SceneNode> previewFactory, Consumer<Move<K, C>> onMove) {
        Options<T, C> defaults = new Options<>();
        return create(context, groups, key, containerFactory, rowFactory, previewFactory, onMove, () -> defaults);
    }
    public static <T, K, C> SceneNode create(ScenePageContext context, ReadableSignal<List<Group<C, T>>> groups,
            Function<T, K> key, Function<C, Container> containerFactory, Function<ReadableSignal<T>, Row> rowFactory,
            Function<ReadableSignal<T>, SceneNode> previewFactory, Consumer<Move<K, C>> onMove,
            ReadableSignal<Options<T, C>> options) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        return new SceneSortableModel<>(context, Objects.requireNonNull(groups, "groups"), Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(containerFactory, "containerFactory"), Objects.requireNonNull(rowFactory, "rowFactory"),
                Objects.requireNonNull(previewFactory, "previewFactory"), Objects.requireNonNull(onMove, "onMove"),
                Objects.requireNonNull(options, "options")).root;
    }
}
