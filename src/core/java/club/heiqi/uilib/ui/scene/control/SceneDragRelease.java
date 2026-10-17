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
    private static final long VELOCITY_WINDOW = 64_000_000L;
    private static final class Sample {
        final int x, y; final long time;
        Sample(int x, int y, long time) { this.x = x; this.y = y; this.time = time; }
    }
    private final java.util.ArrayDeque<Sample> samples = new java.util.ArrayDeque<>();
    private boolean bounded;
    private club.heiqi.uilib.ui.scene.motion.ElasticOptions elastic;
    void elastic(club.heiqi.uilib.ui.scene.motion.ElasticOptions value) { elastic = value; }
    private double minimumX, maximumX, minimumY, maximumY;
    SceneDragRelease(SceneRuntime runtime) { this.runtime = runtime; active = runtime.domain().signal(false); }
    void sample(int x, int y, long now) {
        // 同时刻只保留最终位置；松手也采样，停手时间属于速度事实。
        if (!samples.isEmpty() && now < samples.peekLast().time) { samples.clear(); }
        if (!samples.isEmpty() && now == samples.peekLast().time) samples.removeLast();
        samples.addLast(new Sample(x, y, now));
        while (samples.size() > 2) {
            java.util.Iterator<Sample> iterator = samples.iterator(); iterator.next();
            if (now - iterator.next().time <= VELOCITY_WINDOW) break;
            samples.removeFirst();
        }
    }
    private double speed(boolean horizontal, long now) {
        if (samples.size() < 2) return 0;
        Sample first = samples.peekFirst(), last = samples.peekLast();
        long beginning = Math.max(first.time, now - VELOCITY_WINDOW);
        if (last.time <= beginning) return 0;
        double origin = horizontal ? first.x : first.y;
        if (first.time < beginning) {
            java.util.Iterator<Sample> iterator = samples.iterator(); iterator.next(); Sample next = iterator.next();
            double fraction = (double) (beginning - first.time) / (next.time - first.time);
            origin += ((horizontal ? next.x : next.y) - origin) * fraction;
        }
        return ((horizontal ? last.x : last.y) - origin) * 1_000_000_000.0 / (last.time - beginning);
    }
    void bounds(club.heiqi.uilib.ui.scene.layout.LayoutBox bounds, int baseX, int baseY, int width, int height) { bounds(bounds, null, baseX, baseY, width, height); }
    void bounds(club.heiqi.uilib.ui.scene.layout.LayoutBox bounds, club.heiqi.uilib.ui.scene.layout.LayoutBox viewport, int baseX, int baseY, int width, int height) {
        if (bounds == null) bounds = viewport;
        bounded = bounds != null;
        if (!bounded) { x.unconstrain(); y.unconstrain(); return; }
        minimumX = (double) bounds.x - baseX; maximumX = minimumX + Math.max(0, bounds.width - width);
        minimumY = (double) bounds.y - baseY; maximumY = minimumY + Math.max(0, bounds.height - height);
        if (elastic != null) {
            double margin = Math.abs(elastic.displacement(Double.MAX_VALUE, runtime.motionPolicy().get()));
            minimumX -= margin; maximumX += margin; minimumY -= margin; maximumY += margin;
        }
        if (viewport != null) {
            minimumX = SceneDragSupport.clamp(Math.round(minimumX + baseX), width, viewport.x, viewport.width) - baseX;
            maximumX = SceneDragSupport.clamp(Math.round(maximumX + baseX), width, viewport.x, viewport.width) - baseX;
            minimumY = SceneDragSupport.clamp(Math.round(minimumY + baseY), height, viewport.y, viewport.height) - baseY;
            maximumY = SceneDragSupport.clamp(Math.round(maximumY + baseY), height, viewport.y, viewport.height) - baseY;
        }
    }
    void begin() { stop(); samples.clear(); }
    void release(int dx, int dy, MotionSpec spec, long eventTime) {
        stop(); long now = runtime.inputNanos();
        MotionSpec strategy = runtime.motionPolicy().get().resolve(spec, true);
        double vx = speed(true, eventTime), vy = speed(false, eventTime);
        x.release(dx, vx, 0, strategy, now); y.release(dy, vy, 0, strategy, now);
        constrain(now);
        if (!x.active() && !y.active()) return;
        active.set(true); runtime.invalidateGeometry(); scheduled = runtime.animate(this::advance);
    }
    private boolean advance(long now) {
        x.advance(now); y.advance(now); constrain(now); runtime.invalidateGeometry();
        if (!x.active() && !y.active()) { active.set(false); scheduled = null; return false; }
        return true;
    }
    private void constrain(long now) {
        if (bounded) { x.constrain(minimumX, maximumX, now); y.constrain(minimumY, maximumY, now); }
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
