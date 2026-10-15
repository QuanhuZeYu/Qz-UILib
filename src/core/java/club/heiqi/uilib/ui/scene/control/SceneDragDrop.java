package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** 页面拥有的拖放作用域；来源、目标注册归挂载，几何只消费当前正式输入批次。 */
public final class SceneDragDrop<K> {
    public enum Collision { POINTER, INTERSECTION, CLOSEST_CENTER }

    public static final class Snapshot<K> {
        public final boolean active, keyboard;
        public final K source, target;
        public final LayoutBox box;
        public final int x, y;
        private Snapshot(boolean active, boolean keyboard, K source, K target, LayoutBox box, int x, int y) {
            this.active = active; this.keyboard = keyboard; this.source = source; this.target = target;
            this.box = box; this.x = x; this.y = y;
        }
    }
    /** 一次正常放下的意图；业务决定数据与归属是否变化。 */
    public static final class Drop<K> {
        public final K source, target;
        public final LayoutBox box;
        public final int x, y;
        public final boolean keyboard;
        private Drop(Snapshot<K> value) {
            source = value.source; target = value.target; box = value.box;
            x = value.x; y = value.y; keyboard = value.keyboard;
        }
    }
    /** 同批几何的短期查询结果，供同包排序控件消费，不保存另一棵几何树。 */
    static final class Candidate<K> {
        final K key;
        final SceneNode node;
        final LayoutBox box, visibleBox;
        final int priority, order;
        Candidate(K key, SceneNode node, LayoutBox box, LayoutBox visibleBox, int priority, int order) {
            this.key = key; this.node = node; this.box = box; this.visibleBox = visibleBox;
            this.priority = priority; this.order = order;
        }
    }
    private static final class SourceRegistration<K> {
        final K key;
        final SceneNode node;
        final MountHandle mount;
        final Runnable cancel;
        SceneNode anchor;
        SourceRegistration(K key, SceneNode node, MountHandle mount, Runnable cancel, SceneNode anchor) {
            this.key = key; this.node = node; this.mount = mount; this.cancel = cancel; this.anchor = anchor;
        }
    }
    private static final class Target<K> {
        final K key;
        final SceneNode node;
        final MountHandle mount;
        final ReadableSignal<SceneDroppable.Options<K>> options;
        Target(K key, SceneNode node, MountHandle mount, ReadableSignal<SceneDroppable.Options<K>> options) {
            this.key = key; this.node = node; this.mount = mount; this.options = options;
        }
    }
    private final SceneRuntime runtime;
    private final Collision collision;
    private final Consumer<Drop<K>> onDrop;
    private final Signal<Snapshot<K>> state;
    private final ReadableSignal<Snapshot<K>> view;
    private final Map<K, SourceRegistration<K>> sources = new HashMap<>();
    private final Map<K, Target<K>> targets = new HashMap<>();
    private final Map<SceneNode, Target<K>> targetsByNode = new IdentityHashMap<>();
    private SourceRegistration<K> session;
    private K keyboardTarget;
    private BiFunction<K, SceneGeometryView, SceneNode> scrollSource;
    private boolean closed;

    private SceneDragDrop(ScenePageContext context, Collision collision, Consumer<Drop<K>> onDrop) {
        runtime = context.runtime(); this.collision = collision; this.onDrop = onDrop;
        state = context.domain().signal(inactive()); view = state::get;
        context.own(this::dispose);
    }
    public static <K> SceneDragDrop<K> create(ScenePageContext context, Collision collision, Consumer<Drop<K>> onDrop) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        return new SceneDragDrop<>(context, Objects.requireNonNull(collision, "collision"), Objects.requireNonNull(onDrop, "onDrop"));
    }
    public ReadableSignal<Snapshot<K>> snapshot() { return view; }
    public SceneDraggable.Options draggable(K key, SceneDraggable.Options options) {
        runtime.domain().checkAccess();
        return Objects.requireNonNull(options, "options").observer(new Source<>(this, Objects.requireNonNull(key, "key"), options.sourceAnchor()));
    }
    private static <K> Snapshot<K> inactive() { return new Snapshot<>(false, false, null, null, null, 0, 0); }
    private static boolean sameBox(LayoutBox a, LayoutBox b) {
        return a == b || a != null && b != null && a.x == b.x && a.y == b.y && a.width == b.width && a.height == b.height;
    }
    private void publish(Snapshot<K> next) {
        Snapshot<K> old = state.get();
        if (old.active != next.active || old.keyboard != next.keyboard || !Objects.equals(old.source, next.source)
                || !Objects.equals(old.target, next.target) || !sameBox(old.box, next.box) || old.x != next.x || old.y != next.y)
            state.set(next);
    }
    private void requireRuntime(SceneRuntime candidate) {
        if (closed) throw new IllegalStateException("拖放作用域已结束");
        if (runtime != candidate) throw new IllegalArgumentException("拖放声明与作用域必须属于同一页面 runtime");
    }
    private static IllegalArgumentException duplicate(String role, Object key, SceneNode old, SceneNode next) {
        return new IllegalArgumentException("拖放" + role + "键冲突: key=" + key + ", node=" + old.id() + ", duplicate=" + next.id());
    }
    AutoCloseable target(SceneRuntime candidate, K key, SceneNode node, ReadableSignal<SceneDroppable.Options<K>> options) {
        requireRuntime(candidate);
        Target<K> old = targets.get(key);
        if (old != null) throw duplicate("目标", key, old.node, node);
        Target<K> next = new Target<>(key, node, runtime.captureMount(node), options);
        targets.put(key, next); targetsByNode.put(node, next);
        return () -> {
            if (targets.get(key) != next) return;
            targets.remove(key); targetsByNode.remove(node);
            if (Objects.equals(keyboardTarget, key)) keyboardTarget = null;
            Snapshot<K> current = state.get();
            if (current.active && Objects.equals(current.target, key))
                publish(new Snapshot<>(true, current.keyboard, current.source, null, current.box, current.x, current.y));
        };
    }
    /** 只在活动输入或明确的排序查询调用；无会话不登记逐帧目标扫描。 */
    List<Candidate<K>> candidates(K sourceKey, SceneNode sourceNode, SceneGeometryView geometry) {
        List<Candidate<K>> result = new ArrayList<>();
        List<SceneGeometryView.Entry> ordered = geometry.ordered();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            SceneGeometryView.Entry entry = ordered.get(i);
            Target<K> target = targetsByNode.get(entry.node);
            if (target == null || !target.mount.active() || Objects.equals(sourceKey, target.key)
                    || SceneDragSupport.within(target.node, sourceNode) || !runtime.interactive(target.node)) continue;
            LayoutBox visible = SceneDragSupport.visibleBox(geometry, entry);
            if (visible.width == 0 || visible.height == 0) continue;
            SceneDroppable.Options<K> options = Objects.requireNonNull(target.options.get(), "drop options");
            // accepts 本身也可以读取 Signal；不能把谓词结果缓存到挂载期。
            if (options.accepts.test(sourceKey)) result.add(new Candidate<>(target.key, target.node, entry.box, visible, options.priority, i));
        }
        return Collections.unmodifiableList(result);
    }
    /** 排序可将目标条目映射到实际容器；滚动仍由正式 AutoScroll/ScrollPosition 执行。 */
    void scrollSource(BiFunction<K, SceneGeometryView, SceneNode> resolver) { scrollSource = Objects.requireNonNull(resolver, "resolver"); }
    private boolean valid(SourceRegistration<K> source, SceneGeometryView geometry) {
        return source != null && source.mount.active() && sources.get(source.key) == source && runtime.interactive(source.node)
                && geometry.get(source.node) != null && SceneDragSupport.within(source.node, source.anchor)
                && SceneDragSupport.visible(geometry, source.anchor);
    }
    private void cancel(SceneNode node) {
        if (session == null || node != null && session.node != node) return;
        session = null; keyboardTarget = null; publish(inactive());
    }
    private void dispose() {
        closed = true;
        SourceRegistration<K> current = session;
        try { if (current != null) current.cancel.run(); }
        finally { cancel(null); sources.clear(); targets.clear(); targetsByNode.clear(); scrollSource = null; }
    }
    private void update(SourceRegistration<K> source, SceneGeometryView geometry, boolean keyboard, LayoutBox box, int x, int y) {
        if (!valid(source, geometry)) { if (source != null) source.cancel.run(); return; }
        if (session != source) {
            SourceRegistration<K> old = session;
            if (old != null) old.cancel.run();
            session = source; keyboardTarget = null;
        }
        Candidate<K> chosen = null;
        double best = Double.NEGATIVE_INFINITY;
        for (Candidate<K> candidate : candidates(source.key, source.node, geometry)) {
            double score;
            switch (collision) {
                case POINTER: score = candidate.visibleBox.contains(x, y) ? 0 : Double.NEGATIVE_INFINITY; break;
                case INTERSECTION:
                    LayoutBox intersection = candidate.visibleBox.intersect(box);
                    score = intersection.width == 0 || intersection.height == 0 ? Double.NEGATIVE_INFINITY
                            : (double) intersection.width * intersection.height;
                    break;
                case CLOSEST_CENTER:
                    double dx = (double) SceneDragSupport.centerX(candidate.box) - SceneDragSupport.centerX(box);
                    double dy = (double) SceneDragSupport.centerY(candidate.box) - SceneDragSupport.centerY(box);
                    score = -(dx * dx + dy * dy); break;
                default: throw new AssertionError(collision);
            }
            if (score == Double.NEGATIVE_INFINITY) continue;
            if (chosen == null || candidate.priority > chosen.priority || candidate.priority == chosen.priority
                    && (score > best || score == best && candidate.order > chosen.order)) { chosen = candidate; best = score; }
        }
        publish(new Snapshot<>(true, keyboard, source.key, chosen == null ? null : chosen.key, box, x, y));
    }
    private Runnable normalDrop(SourceRegistration<K> source, SceneGeometryView geometry, boolean keyboard, LayoutBox box, int x, int y) {
        if (session != source || source == null) return null;
        try {
            // UP 必须重新读取接收资格和同批最终几何，旧 target 不是提交依据。
            update(source, geometry, keyboard, box, x, y);
            Snapshot<K> current = state.get();
            Drop<K> drop = session == source && current.target != null ? new Drop<>(current) : null;
            return drop == null ? null : () -> onDrop.accept(drop);
        } finally { cancel(null); }
    }
    private LayoutBox keyboardTarget(SourceRegistration<K> source, SceneGeometryView geometry, SceneEditEvent.Command direction) {
        if (session != source || !valid(source, geometry)) return null;
        List<Candidate<K>> candidates = candidates(source.key, source.node, geometry);
        Candidate<K> selected = null;
        for (Candidate<K> candidate : candidates) if (Objects.equals(candidate.key, keyboardTarget)) { selected = candidate; break; }
        // 键盘落点取可见部分中心，完整盒中心可能被纯 clip 裁掉且无法滚动显露。
        if (direction == null) {
            if (selected == null) keyboardTarget = null;
            return selected == null ? null : selected.visibleBox;
        }
        LayoutBox origin = selected == null ? state.get().box : selected.visibleBox;
        Candidate<K> next = null;
        double best = Double.POSITIVE_INFINITY;
        boolean horizontal = direction == SceneEditEvent.Command.LEFT || direction == SceneEditEvent.Command.RIGHT;
        boolean positive = direction == SceneEditEvent.Command.RIGHT || direction == SceneEditEvent.Command.DOWN;
        for (Candidate<K> candidate : candidates) {
            if (candidate == selected) continue;
            double dx = (double) SceneDragSupport.centerX(candidate.visibleBox) - SceneDragSupport.centerX(origin);
            double dy = (double) SceneDragSupport.centerY(candidate.visibleBox) - SceneDragSupport.centerY(origin);
            double primary = horizontal ? dx : dy, cross = horizontal ? dy : dx;
            if (positive ? primary <= 0 : primary >= 0) continue;
            // 半平面中优先同方向近邻；跨轴偏移参与评分，支持横纵布局和不等尺寸网格。
            double score = primary * primary + 4 * cross * cross;
            if (next == null || score < best || score == best && (candidate.priority > next.priority
                    || candidate.priority == next.priority && candidate.order > next.order)) { next = candidate; best = score; }
        }
        if (next == null) return selected == null ? null : selected.visibleBox;
        keyboardTarget = next.key; runtime.requestInputReveal(next.node);
        return next.visibleBox;
    }
    private SceneNode scrollSource(SourceRegistration<K> source, SceneGeometryView geometry) {
        if (session != source) return null;
        K key = state.get().target;
        if (key == null) return null;
        Target<K> target = targets.get(key);
        SceneNode node = scrollSource == null ? target == null ? null : target.node : scrollSource.apply(key, geometry);
        // 自定义祖先仍须属于目标的呈现路径，不能跨所属层或滚动后台。
        if (target == null || node == null || !SceneDragSupport.within(target.node, node) || !runtime.interactive(node)
                || geometry.layerBoundary(target.node) != geometry.layerBoundary(node)) return null;
        return node;
    }
    private static final class Source<K> implements SceneDragSupport.Observer {
        private final SceneDragDrop<K> scope;
        private final K key;
        private final SceneNode anchor;
        Source(SceneDragDrop<K> scope, K key, SceneNode anchor) { this.scope = scope; this.key = key; this.anchor = anchor; }
        @Override public boolean same(SceneDragSupport.Observer other) {
            return other instanceof Source && ((Source<?>) other).scope == scope && Objects.equals(((Source<?>) other).key, key);
        }
        @Override public void configure(SceneNode node, SceneDragSupport.Observer next) {
            SourceRegistration<K> source = registration(node);
            if (source == null) return;
            SceneNode currentAnchor = ((Source<?>) next).anchor;
            // anchor 是随逻辑移动变化的当前配置；注册/MountHandle 和活动会话保持同一个对象。
            source.anchor = currentAnchor == null ? node : currentAnchor;
        }
        @Override public void attach(SceneRuntime runtime, SceneNode node, Runnable cancel) {
            scope.requireRuntime(runtime);
            SourceRegistration<K> old = scope.sources.get(key);
            if (old != null) throw duplicate("来源", key, old.node, node);
            scope.sources.put(key, new SourceRegistration<>(key, node, runtime.captureMount(node), cancel, anchor == null ? node : anchor));
        }
        private SourceRegistration<K> registration(SceneNode node) {
            SourceRegistration<K> source = scope.sources.get(key);
            return source != null && source.node == node ? source : null;
        }
        @Override public void detach(SceneNode node) {
            SourceRegistration<K> source = registration(node);
            if (source != null) { scope.cancel(node); scope.sources.remove(key); }
        }
        @Override public void cancel(SceneNode node) { scope.cancel(node); }
        @Override public void update(SceneNode node, SceneGeometryView geometry, boolean keyboard, LayoutBox box, int x, int y) {
            scope.update(registration(node), geometry, keyboard, box, x, y);
        }
        @Override public Runnable normalDrop(SceneNode node, SceneGeometryView geometry, boolean keyboard, LayoutBox box, int x, int y) {
            return scope.normalDrop(registration(node), geometry, keyboard, box, x, y);
        }
        @Override public LayoutBox keyboardTarget(SceneNode node, SceneGeometryView geometry, SceneEditEvent.Command direction) {
            return scope.keyboardTarget(registration(node), geometry, direction);
        }
        @Override public SceneNode scrollSource(SceneNode node, SceneGeometryView geometry) { return scope.scrollSource(registration(node), geometry); }
    }
}
