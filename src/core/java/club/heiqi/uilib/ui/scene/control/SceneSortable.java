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
import java.util.function.DoubleUnaryOperator;

/** 单一集合拥有全部排序身份；拖中投影展示顺序，正常放下发布 Move。 */
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
    /** 排序让位与落下分别声明；null 关闭对应过渡，曲线允许有限的回弹值。 */
    public static final class Transition {
        public final long millis;
        final DoubleUnaryOperator easing;
        public Transition(long millis, DoubleUnaryOperator easing) {
            if (millis < 0 || millis > Long.MAX_VALUE / 1_000_000L) throw new IllegalArgumentException("过渡时长无效");
            this.millis = millis; this.easing = Objects.requireNonNull(easing, "easing");
        }
        public Transition(long millis, double x1, double y1, double x2, double y2) {
            this(millis, curve(x1, y1, x2, y2));
        }
        private static DoubleUnaryOperator curve(double x1, double y1, double x2, double y2) {
            if (!Double.isFinite(x1) || !Double.isFinite(x2) || !Double.isFinite(y1) || !Double.isFinite(y2)
                    || x1 < 0 || x1 > 1 || x2 < 0 || x2 > 1) throw new IllegalArgumentException("Bezier 控制点无效");
            return progress -> {
                if (progress <= 0 || progress >= 1) return progress;
                double low = 0, high = 1;
                for (int i = 0; i < 32; i++) {
                    double t = (low + high) / 2;
                    if (bezier(t, x1, x2) < progress) low = t; else high = t;
                }
                return bezier((low + high) / 2, y1, y2);
            };
        }
        private static double bezier(double t, double a, double b) {
            return 3 * (1 - t) * (1 - t) * t * a + 3 * (1 - t) * t * t * b + t * t * t;
        }
    }
    public static final class Options<T, C> {
        final Predicate<T> draggable;
        final BiPredicate<C, T> accepts;
        final boolean autoScroll;
        final double autoScrollSpeed;
        final Transition transition, dropAnimation;
        public Options() { this(value -> true, (container, value) -> true, true, 1.0,
                new Transition(250, .25, 1, .5, 1), new Transition(250, .25, .1, .25, 1)); }
        private Options(Predicate<T> draggable, BiPredicate<C, T> accepts, boolean autoScroll, double speed,
                        Transition transition, Transition dropAnimation) {
            this.draggable = draggable; this.accepts = accepts; this.autoScroll = autoScroll; autoScrollSpeed = speed;
            this.transition = transition; this.dropAnimation = dropAnimation;
        }
        public Options<T, C> draggable(Predicate<T> allowed) {
            return new Options<>(Objects.requireNonNull(allowed, "draggable"), accepts, autoScroll, autoScrollSpeed, transition, dropAnimation);
        }
        public Options<T, C> accepts(BiPredicate<C, T> allowed) {
            return new Options<>(draggable, Objects.requireNonNull(allowed, "accepts"), autoScroll, autoScrollSpeed, transition, dropAnimation);
        }
        public Options<T, C> transition(Transition value) {
            return new Options<>(draggable, accepts, autoScroll, autoScrollSpeed, value, dropAnimation);
        }
        public Options<T, C> dropAnimation(Transition value) {
            return new Options<>(draggable, accepts, autoScroll, autoScrollSpeed, transition, value);
        }
        public Options<T, C> autoScroll(boolean enabled) { return new Options<>(draggable, accepts, enabled, autoScrollSpeed, transition, dropAnimation); }
        public Options<T, C> autoScrollSpeed(double multiplier) {
            if (!Double.isFinite(multiplier) || multiplier < 0) throw new IllegalArgumentException("自动滚动速度倍率无效");
            return new Options<>(draggable, accepts, autoScroll, multiplier, transition, dropAnimation);
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
