package club.heiqi.uilib.ui.scene.runtime;

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
    private static final class Visibility {
        final MotionValue value = new MotionValue();
        PresenceOptions options;
        boolean initialized, present;
        long exited;
        PresenceSnapshot snapshot;
    }
    private static final class Source {
        final SceneNode parent;
        PresenceOptions options;
        Source(SceneNode parent) { this.parent = parent; }
    }
    private static final class Exit {
        final SceneNode node;
        final Object scope, key;
        final PresenceSnapshot snapshot;
        final MotionValue value = new MotionValue();
        final long started;
        long maximum;
        MotionSpec requested;
        Exit(SceneNode node, Object scope, Object key, PresenceSnapshot snapshot, double opacity,
                PresenceOptions options, MotionSpec spec, long now) {
            this.node = node; this.scope = scope; this.key = key; this.snapshot = snapshot;
            started = now; maximum = options.maximumExitNanos; requested = options.exit;
            value.snap(opacity, now); value.retarget(0, spec, now);
        }
    }
    private final SceneRuntime runtime;
    private final Map<SceneNode, Visibility> visible = new IdentityHashMap<>();
    private final Map<Object, Source> sources = new IdentityHashMap<>();
    private final Map<SceneNode, PresenceSnapshot> snapshots = new IdentityHashMap<>();
    private final List<Exit> exits = new ArrayList<>();
    private MotionPolicy policy = MotionPolicy.NORMAL;
    ScenePresence(SceneRuntime runtime) { this.runtime = runtime; }
    AutoCloseable registerSource(SceneNode parent, Object scope) {
        sources.put(scope, new Source(parent));
        return () -> { sources.remove(scope); finishScope(scope); };
    }
    /** 拥有声明的来源通知也作用于已退出条目；不依赖子序列再次变化。 */
    void configureSource(Object scope, PresenceOptions options) {
        Source source = sources.get(scope);
        if (source == null || source.options == options) return;
        source.options = options;
        long now = runtime.inputNanos();
        for (Exit exit : new ArrayList<>(exits)) {
            if (exit.scope != scope) continue;
            if (options == null) { finish(exit); continue; }
            exit.requested = options.exit;
            exit.maximum = options.maximumExitNanos;
            exit.value.retarget(0, policy.resolve(exit.requested, false), now);
            if (!exit.value.active() || now - exit.started >= exit.maximum) finish(exit);
        }
        if (options != null) trimExits(scope, options.retainedLimit);
        schedule();
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
            PresenceSnapshot snapshot = new PresenceSnapshot(runtime, entry.getKey(), entry.getValue(), geometry);
            snapshots.put(entry.getKey(), snapshot);
            Visibility state = visible.get(entry.getKey()); if (state != null) state.snapshot = snapshot;
        }
    }
    boolean retain(SceneNode node, Object scope, Object key, PresenceOptions options) {
        if (options == null) return false;
        MotionSpec spec = policy.resolve(options.exit, false);
        PresenceSnapshot snapshot = snapshots.get(node);
        if (snapshot == null || spec.kind == MotionSpec.Kind.INSTANT) return false;
        finishPrevious(scope, key);
        trimExits(scope, options.retainedLimit - 1);
        Visibility state = visible.get(node);
        double opacity = state == null ? 1 : Math.max(0, Math.min(1, state.value.value()));
        Exit exit = new Exit(node, scope, key, snapshot, opacity, options, spec, runtime.inputNanos());
        if (!exit.value.active()) return false;
        exits.add(exit); schedule(); return true;
    }
    private void finishPrevious(Object scope, Object key) {
        for (Exit old : new ArrayList<>(exits))
            if (old.scope == scope && Objects.equals(old.key, key)) finish(old);
    }
    private void trimExits(Object scope, int limit) {
        int count = 0;
        for (Exit old : exits) if (old.scope == scope) count++;
        for (Exit old : new ArrayList<>(exits)) {
            if (count <= limit) return;
            if (old.scope != scope) continue;
            finish(old); count--;
        }
    }
    void release(SceneNode node) {
        removeVisibility(node); snapshots.remove(node);
        for (Exit exit : new ArrayList<>(exits)) if (exit.snapshot.descendedFrom(node)) finish(exit);
    }
    void syncPolicy() {
        MotionPolicy next = runtime.motionPolicy().get(); if (next == policy) return; policy = next;
        long now = runtime.inputNanos();
        for (Visibility state : visible.values()) {
            state.value.retarget(state.present ? 1 : 0, next.resolve(state.present ? state.options.enter : state.options.exit, false), now);
            if (!state.present && !state.value.active()) state.snapshot = null;
        }
        for (Exit exit : new ArrayList<>(exits)) {
            exit.value.retarget(0, next.resolve(exit.requested, false), now);
            if (!exit.value.active()) finish(exit);
        }
        runtime.invalidatePaint(); schedule();
    }
    PaintPlan paint(SceneGeometryView geometry) {
        PaintPlanBuilder target = new PaintPlanBuilder();
        for (Visibility state : visible.values())
            if (!state.present && state.snapshot != null) state.snapshot.append(runtime, target, state.value.value(), geometry, this::opacity);
        for (Exit exit : exits) exit.snapshot.append(runtime, target, exit.value.value(), geometry, this::opacity);
        return target.build();
    }
    public boolean advance(long now) {
        for (Visibility state : visible.values()) advanceVisibility(state, now);
        for (Exit exit : new ArrayList<>(exits)) advanceExit(exit, now);
        return active();
    }
    private void advanceVisibility(Visibility state, long now) {
        double previous = state.value.value(); state.value.advance(now);
        if (!state.present && (now - state.exited >= state.options.maximumExitNanos
                || state.snapshot != null && !state.snapshot.available(runtime))) state.value.snap(0, now);
        if (state.value.value() != previous) runtime.invalidatePaint();
        if (!state.present && !state.value.active()) state.snapshot = null;
    }
    private void advanceExit(Exit exit, long now) {
        exit.value.advance(now);
        if (!exit.value.active() || now - exit.started >= exit.maximum || !exit.snapshot.available(runtime)) finish(exit);
        runtime.invalidatePaint();
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
