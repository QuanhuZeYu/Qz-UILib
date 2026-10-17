package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.MotionValue;

/** 每个呈现量只选一个驱动；旧曲线允许过冲，显式运动复用公共解析求解器。 */
final class SceneSortableTrack {
    static final class Strategy {
        final SceneSortable.Transition finite;
        final MotionSpec motion;
        final boolean explicit;
        private Strategy(SceneSortable.Transition finite, MotionSpec motion, boolean explicit) {
            this.finite = finite; this.motion = motion; this.explicit = explicit;
        }
        static Strategy resolve(SceneSortable.Transition finite, MotionSpec motion, MotionPolicy policy) {
            if (motion != null) return new Strategy(null, policy.resolve(motion, true), true);
            if (finite == null || finite.millis == 0 || policy == MotionPolicy.OFF)
                return new Strategy(null, MotionSpec.instant(), false);
            if (policy == MotionPolicy.REDUCED)
                return new Strategy(null, MotionSpec.tween(Math.min(160, finite.millis) * 1_000_000L, MotionSpec.SMOOTH), false);
            return new Strategy(finite, null, false);
        }
    }
    private final MotionValue motion = new MotionValue();
    private SceneSortable.Transition finite;
    private double origin, destination, value, velocity;
    private long started, sampled;
    private boolean active;

    SceneSortableTrack(double initial, double speed, double target, Strategy strategy, long now) {
        motion.snap(initial, now);
        if (strategy.finite == null) motion.release(initial, speed, target, strategy.motion, now);
        else retarget(target, strategy, now);
    }
    double value() { return finite == null ? motion.value() : value; }
    double target() { return finite == null ? motion.target() : destination; }
    boolean active() { return finite == null ? motion.active() : active; }
    int integer() { return SceneDragSupport.integer(Math.round(value())); }
    void advance(long now) {
        if (finite == null) { motion.advance(now); return; }
        if (now - sampled <= 0) return;
        long elapsed = now - started, duration = finite.millis * 1_000_000L;
        if (elapsed >= duration) { value = destination; velocity = 0; active = false; }
        else if (active) {
            double factor = finite.easing.applyAsDouble(Math.max(0, (double) elapsed / duration));
            if (!Double.isFinite(factor)) throw new IllegalArgumentException("排序过渡曲线必须返回有限值");
            double next = origin + (destination - origin) * factor;
            velocity = (next - value) * 1_000_000_000.0 / (now - sampled); value = next;
        }
        sampled = now;
    }
    void retarget(double target, Strategy strategy, long now) {
        advance(now);
        if (strategy.finite != null) {
            if (finite == strategy.finite && destination == target) return;
            double current = value();
            finite = strategy.finite; origin = value = current; destination = target;
            velocity = 0; started = sampled = now; active = current != target;
        } else {
            if (finite != null) {
                motion.release(value, velocity, target, strategy.motion, now); finite = null;
            } else motion.retarget(target, strategy.motion, now);
        }
    }
    /** 插槽换基只平移当前状态及目标；随后重定零偏移，弹簧速度继续。 */
    void shift(double delta) {
        if (finite == null) motion.shift(delta);
        else { origin += delta; destination += delta; value += delta; }
    }
}
