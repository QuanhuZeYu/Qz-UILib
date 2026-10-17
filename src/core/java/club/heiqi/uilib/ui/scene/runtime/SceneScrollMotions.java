package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.MotionValue;
import club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.IdentityHashMap;
import java.util.Map;

/** 有效偏移仍归 Runtime；此处只拥有显式释放和视口边界的暂态呈现。 */
final class SceneScrollMotions implements SceneTransitions.Step {
    private static final class State {
        ScrollMotionOptions options;
        final MotionValue bounce = new MotionValue(), inertia = new MotionValue();
        boolean inertial, sampled;
        long sampleTime;
        int lastOffset, emitted, maximum;
        double speed;
    }
    private final SceneRuntime runtime;
    private final Map<SceneNode, State> states = new IdentityHashMap<>();
    SceneScrollMotions(SceneRuntime runtime) { this.runtime = runtime; }
    void configure(SceneNode node, ScrollMotionOptions options) {
        if (options == null) { remove(node); return; }
        State state = states.computeIfAbsent(node, ignored -> new State()); state.options = options;
        long before = Math.round(state.bounce.value());
        MotionPolicy policy = runtime.motionPolicy().get();
        state.bounce.retarget(0, policy.resolve(options.rebound, true), runtime.inputNanos());
        // 减少只终止合成惯性，回弹从当前呈现平稳续接；OFF 的即时策略才清空形变。
        if (policy != MotionPolicy.NORMAL) { state.inertial = false; endSequence(node); }
        if (before != Math.round(state.bounce.value())) runtime.invalidateGeometry();
        schedule();
    }
    int offset(SceneNode node) {
        State state = states.get(node); return state == null ? 0 : (int) Math.round(state.bounce.value());
    }
    void remove(SceneNode node) {
        State removed = states.remove(node);
        if (removed != null && removed.bounce.value() != 0) runtime.invalidateGeometry();
        schedule();
    }
    void endSequence(SceneNode node) {
        State state = states.get(node);
        if (state != null) { state.sampled = false; state.speed = 0; state.sampleTime = 0; state.lastOffset = 0; }
    }
    void cancel(SceneNode node, boolean bounce) {
        State state = states.get(node); if (state == null) return;
        state.inertial = false;
        if (bounce && state.bounce.value() != 0) { state.bounce.snap(0, runtime.inputNanos()); runtime.invalidateGeometry(); }
        schedule();
    }
    void cancelAll() { for (SceneNode node : states.keySet()) { cancel(node, true); endSequence(node); } }
    void input(SceneNode node, ScenePointerEvent event) {
        State state = states.get(node); if (state == null) return;
        int offset = runtime.scrollOffset(node);
        if (event.scrollPhase == ScenePointerEvent.ScrollPhase.UPDATE) {
            if (state.sampled && event.timeNanos > state.sampleTime && event.timeNanos - state.sampleTime < 400_000_000L)
                state.speed = (offset - state.lastOffset) * 1_000_000_000.0 / (event.timeNanos - state.sampleTime);
            else state.speed = 0;
            state.sampled = true; state.sampleTime = event.timeNanos; state.lastOffset = offset;
        } else { state.sampled = false; state.speed = 0; }
    }
    void release(SceneNode node, ScenePointerEvent event) {
        State state = states.get(node);
        if (state == null || state.options.inertia == null || !state.sampled
                || event.timeNanos - state.sampleTime > 100_000_000L
                || runtime.motionPolicy().get() != MotionPolicy.NORMAL) return;
        state.sampled = false;
        int offset = runtime.scrollOffset(node);
        state.inertia.decay(offset, state.speed, state.options.inertia, runtime.inputNanos());
        state.emitted = offset; state.inertial = state.inertia.active(); schedule();
    }
    boolean boundary(SceneNode node, ScenePointerEvent event) {
        State state = states.get(node);
        if (state == null || !node.scrollable()) return false;
        MotionSpec strategy = runtime.motionPolicy().get().resolve(state.options.rebound, true);
        if (strategy.kind == MotionSpec.Kind.INSTANT) return false;
        double scale = runtime.motionPolicy().get() == MotionPolicy.REDUCED ? .25 : 1;
        double impulse = (double) event.delta * node.scrollStep() * .35 * scale;
        double limit = state.options.maximumDisplacement * scale;
        double displacement = Math.max(-limit, Math.min(limit, impulse));
        state.bounce.release(displacement, 0, 0, strategy, runtime.inputNanos());
        runtime.invalidateGeometry(); schedule(); return true;
    }
    void range(SceneNode node, SceneGeometryView.Entry entry) {
        State state = states.get(node); if (state == null) return;
        if (entry.scrollMaximum != state.maximum) {
            state.maximum = entry.scrollMaximum;
            if (state.inertial) state.inertial = false;
            if (state.bounce.value() != 0) state.bounce.retarget(0,
                    runtime.motionPolicy().get().resolve(state.options.rebound, true), runtime.inputNanos());
        }
    }
    public boolean advance(long now) {
        for (Map.Entry<SceneNode, State> entry : states.entrySet()) {
            SceneNode node = entry.getKey(); State state = entry.getValue();
            if (!runtime.interactive(node)) { cancel(node, true); continue; }
            advanceBounce(state, now);
            if (state.inertial) advanceInertia(node, state, now);
        }
        return active();
    }
    private void advanceBounce(State state, long now) {
        long before = Math.round(state.bounce.value()); state.bounce.advance(now);
        if (before != Math.round(state.bounce.value())) runtime.invalidateGeometry();
    }
    private void advanceInertia(SceneNode node, State state, long now) {
        state.inertia.advance(now);
        int desired = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(state.inertia.value())));
        int requested = desired - state.emitted;
        int applied = runtime.scrollByPixels(node, requested); state.emitted += applied;
        if (applied == requested) { state.inertial = state.inertia.active(); return; }
        state.inertial = false;
        if (state.options.rebound.kind == MotionSpec.Kind.INSTANT) return;
        double impulse = Math.max(-state.options.maximumDisplacement,
                Math.min(state.options.maximumDisplacement, -state.inertia.velocity() * .025));
        state.bounce.release(0, impulse * 12, 0, state.options.rebound, now);
    }
    private boolean active() {
        for (State state : states.values()) if (state.inertial || state.bounce.active()) return true;
        return false;
    }
    private void schedule() { if (active()) runtime.transitions().start(this); else runtime.transitions().stop(this); }
}
