package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.MotionValue;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 跟手与释放共用呈现通道，采样只消费正式输入事实。 */
final class SceneDragRelease {
    private final SceneRuntime runtime;
    private final MotionValue x = new MotionValue(), y = new MotionValue();
    final Signal<Boolean> active;
    private AutoCloseable scheduled;
    private long sampleTime;
    private int sampleX, sampleY;
    private double speedX, speedY;
    private boolean sampled;
    SceneDragRelease(SceneRuntime runtime) { this.runtime = runtime; active = runtime.domain().signal(false); }
    void sample(int x, int y, long now) {
        if (sampled && now > sampleTime) {
            double seconds = (now - sampleTime) / 1_000_000_000.0;
            speedX = (x - sampleX) / seconds; speedY = (y - sampleY) / seconds;
        }
        sampleTime = now; sampleX = x; sampleY = y; sampled = true;
    }
    void begin() { stop(); sampled = false; speedX = speedY = 0; }
    void release(int dx, int dy, MotionSpec spec, long eventTime) {
        stop(); long now = runtime.inputNanos();
        MotionSpec strategy = runtime.motionPolicy().get().resolve(spec, true);
        double vx = sampled && eventTime - sampleTime <= 100_000_000L ? speedX : 0;
        double vy = sampled && eventTime - sampleTime <= 100_000_000L ? speedY : 0;
        x.release(dx, vx, 0, strategy, now); y.release(dy, vy, 0, strategy, now);
        if (!x.active() && !y.active()) return;
        active.set(true); runtime.invalidateGeometry(); scheduled = runtime.animate(this::advance);
    }
    private boolean advance(long now) {
        x.advance(now); y.advance(now); runtime.invalidateGeometry();
        if (!x.active() && !y.active()) { active.set(false); scheduled = null; return false; }
        return true;
    }
    int x() { return (int) Math.round(x.value()); }
    int y() { return (int) Math.round(y.value()); }
    void policy(MotionSpec spec) {
        if (!active.get()) return;
        MotionSpec next = runtime.motionPolicy().get().resolve(spec, true);
        x.retarget(0, next, runtime.inputNanos()); y.retarget(0, next, runtime.inputNanos());
        if (!x.active() && !y.active()) stop();
        runtime.invalidateGeometry();
    }
    void stop() {
        if (scheduled != null) {
            try { scheduled.close(); } catch (Exception failure) { throw new IllegalStateException("释放运动结束失败", failure); }
            scheduled = null;
        }
        if (active.get()) { active.set(false); runtime.invalidateGeometry(); }
    }
}
