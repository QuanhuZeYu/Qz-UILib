package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.MotionValue;
import club.heiqi.uilib.ui.scene.motion.PresenceOptions;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 逻辑身份、输入与被动片段分开；保留只延后所属逻辑资源销毁，不延续挂载订阅。 */
final class ScenePresence implements SceneTransitions.Step {
    private static final class Snapshot {
        final PresencePaint paint;
        final List<SceneNode> ancestors = new ArrayList<>();
        final SceneNode parent;
        final LayoutBox parentBox;
        final int scroll;
        Snapshot(SceneNode node, PresencePaint paint, SceneGeometryView geometry) {
            this.paint = paint; parent = node.parent();
            SceneGeometryView.Entry entry = parent == null ? null : geometry.get(parent);
            parentBox = entry == null ? null : entry.box; scroll = entry == null ? 0 : entry.scrollOffset;
            for (SceneNode at = parent; at != null; at = at.parent()) ancestors.add(at);
        }
    }
    private static final class Visibility {
        final MotionValue value = new MotionValue();
        PresenceOptions options;
        boolean initialized, present;
        long exited;
        Snapshot snapshot;
    }
    private static final class Source {
        final SceneNode parent;
        Source(SceneNode parent) { this.parent = parent; }
    }
    private static final class Exit {
        final SceneNode node;
        final Object scope, key;
        final Snapshot snapshot;
        final MotionValue value = new MotionValue();
        final long started, maximum;
        Exit(SceneNode node, Object scope, Object key, Snapshot snapshot, double opacity,
                PresenceOptions options, MotionSpec spec, long now) {
            this.node = node; this.scope = scope; this.key = key; this.snapshot = snapshot;
            started = now; maximum = options.maximumExitNanos;
            value.snap(opacity, now); value.retarget(0, spec, now);
        }
    }
    private final SceneRuntime runtime;
    private final Map<SceneNode, Visibility> visible = new IdentityHashMap<>();
    private final Map<Object, Source> sources = new IdentityHashMap<>();
    private final Map<SceneNode, Snapshot> snapshots = new IdentityHashMap<>();
    private final List<Exit> exits = new ArrayList<>();
    private MotionPolicy policy = MotionPolicy.NORMAL;
    ScenePresence(SceneRuntime runtime) { this.runtime = runtime; }
    AutoCloseable registerSource(SceneNode parent, Object scope) {
        sources.put(scope, new Source(parent));
        return () -> { sources.remove(scope); finishScope(scope); };
    }
    void declare(SceneNode node, boolean present, PresenceOptions options) {
        long now = runtime.inputNanos(); Visibility state = visible.computeIfAbsent(node, ignored -> new Visibility());
        state.options = Objects.requireNonNull(options, "存在性策略");
        if (!state.initialized) {
            state.initialized = true; state.value.snap(present && !options.initialEnter ? 1 : 0, now);
        }
        if (state.present && !present) state.exited = now;
        state.present = present;
        state.value.retarget(present ? 1 : 0, policy.resolve(present ? options.enter : options.exit, false), now);
        node.visible(present);
        if (!present && !state.value.active()) { state.snapshot = null; snapshots.remove(node); }
        runtime.invalidatePaint(); schedule();
    }
    void removeVisibility(SceneNode node) { visible.remove(node); snapshots.remove(node); schedule(); }
    float opacity(SceneNode node) {
        Visibility state = visible.get(node);
        return state == null ? 1 : (float) Math.max(0, Math.min(1, state.value.value()));
    }
    List<SceneNode> captureNodes() {
        LinkedHashSet<SceneNode> nodes = new LinkedHashSet<>();
        for (Map.Entry<SceneNode, Visibility> entry : visible.entrySet()) if (entry.getValue().present) nodes.add(entry.getKey());
        for (Source source : sources.values()) nodes.addAll(source.parent.children());
        return new ArrayList<>(nodes);
    }
    void captured(Map<SceneNode, PresencePaint> plans, SceneGeometryView geometry) {
        for (Map.Entry<SceneNode, PresencePaint> entry : plans.entrySet()) {
            Snapshot snapshot = new Snapshot(entry.getKey(), entry.getValue(), geometry);
            snapshots.put(entry.getKey(), snapshot);
            Visibility state = visible.get(entry.getKey()); if (state != null) state.snapshot = snapshot;
        }
    }
    boolean retain(SceneNode node, Object scope, Object key, PresenceOptions options) {
        if (options == null) return false;
        MotionSpec spec = policy.resolve(options.exit, false);
        Snapshot snapshot = snapshots.get(node);
        if (snapshot == null || spec.kind == MotionSpec.Kind.INSTANT) return false;
        for (Exit old : new ArrayList<>(exits))
            if (old.scope == scope && Objects.equals(old.key, key)) finish(old);
        int count = 0;
        for (Exit old : exits) if (old.scope == scope) count++;
        if (count >= options.retainedLimit)
            for (Exit old : new ArrayList<>(exits)) if (old.scope == scope) { finish(old); break; }
        Visibility state = visible.get(node);
        double opacity = state == null ? 1 : Math.max(0, Math.min(1, state.value.value()));
        Exit exit = new Exit(node, scope, key, snapshot, opacity, options, spec, runtime.inputNanos());
        if (!exit.value.active()) return false;
        exits.add(exit); schedule(); return true;
    }
    void release(SceneNode node) {
        removeVisibility(node); snapshots.remove(node);
        for (Exit exit : new ArrayList<>(exits)) if (exit.snapshot.ancestors.contains(node)) finish(exit);
    }
    void syncPolicy() {
        MotionPolicy next = runtime.motionPolicy().get(); if (next == policy) return; policy = next;
        long now = runtime.inputNanos();
        for (Visibility state : visible.values()) {
            state.value.retarget(state.present ? 1 : 0, next.resolve(state.present ? state.options.enter : state.options.exit, false), now);
            if (!state.present && !state.value.active()) state.snapshot = null;
        }
        if (next == MotionPolicy.OFF) for (Exit exit : new ArrayList<>(exits)) finish(exit);
        runtime.invalidatePaint(); schedule();
    }
    private boolean available(Snapshot snapshot) {
        for (SceneNode ancestor : snapshot.ancestors)
            if (ancestor.runtime() != runtime || !ancestor.visible() || ancestor.destroyed()) return false;
        return true;
    }
    PaintPlan paint(SceneGeometryView geometry) {
        PaintPlanBuilder target = new PaintPlanBuilder();
        for (Visibility state : visible.values())
            if (!state.present && state.snapshot != null) append(target, state.snapshot, state.value.value(), geometry);
        for (Exit exit : exits) append(target, exit.snapshot, exit.value.value(), geometry);
        return target.build();
    }
    private void append(PaintPlanBuilder target, Snapshot snapshot, double factor, SceneGeometryView geometry) {
        if (!available(snapshot)) return;
        int dx = 0, dy = 0; float opacity = (float) Math.max(0, Math.min(1, factor));
        LayoutBox clip = geometry.viewport();
        if (snapshot.parent != null) {
            SceneGeometryView.Entry parent = geometry.get(snapshot.parent);
            if (parent == null || snapshot.parentBox == null) return;
            dx = parent.box.x - snapshot.parentBox.x; dy = parent.box.y - snapshot.parentBox.y;
            if (snapshot.parent.scrollable()) {
                if (snapshot.parent.axis() == SceneNode.Axis.ROW) dx += snapshot.scroll - parent.scrollOffset;
                else dy += snapshot.scroll - parent.scrollOffset;
            }
            if (parent.clip != null) clip = clip.intersect(parent.clip);
            for (SceneNode ancestor : snapshot.ancestors) opacity *= ancestor.opacity() * opacity(ancestor);
        }
        PaintPlan.Presentation ordinary = PaintPlan.presentation(dx, dy, opacity, clip);
        PaintPlan.Presentation escaped = PaintPlan.presentation(dx, dy, opacity, geometry.viewport());
        for (PresencePaint.Part part : snapshot.paint.parts) {
            PaintPlan.Presentation projection = part.viewportClipped ? escaped : ordinary;
            for (club.heiqi.uilib.ui.scene.paint.PaintCommand command : projection.apply(part.paint).commands()) target.add(command);
        }
    }
    public boolean advance(long now) {
        for (Visibility state : visible.values()) {
            double previous = state.value.value(); state.value.advance(now);
            if (!state.present && (now - state.exited >= state.options.maximumExitNanos
                    || state.snapshot != null && !available(state.snapshot))) state.value.snap(0, now);
            if (state.value.value() != previous) runtime.invalidatePaint();
            if (!state.present && !state.value.active()) state.snapshot = null;
        }
        for (Exit exit : new ArrayList<>(exits)) {
            exit.value.advance(now);
            if (!exit.value.active() || now - exit.started >= exit.maximum || !available(exit.snapshot)) finish(exit);
            runtime.invalidatePaint();
        }
        return active();
    }
    private boolean active() {
        if (!exits.isEmpty()) return true;
        for (Visibility state : visible.values()) if (state.value.active()) return true;
        return false;
    }
    private void schedule() { if (active()) runtime.transitions().start(this); else runtime.transitions().stop(this); }
    private void finish(Exit exit) {
        if (!exits.remove(exit)) return;
        try { exit.node.destroy(); } finally { runtime.invalidatePaint(); }
    }
    private void finishScope(Object scope) {
        Throwable failure = null;
        for (Exit exit : new ArrayList<>(exits)) if (exit.scope == scope) {
            try { finish(exit); }
            catch (RuntimeException | Error next) {
                if (failure == null) failure = next; else if (failure != next) failure.addSuppressed(next);
            }
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    }
}
