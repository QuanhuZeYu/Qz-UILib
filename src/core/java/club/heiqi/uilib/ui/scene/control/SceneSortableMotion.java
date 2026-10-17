package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import java.util.ArrayDeque;
import java.util.Iterator;

/** 让位和落下沿正式输入帧推进，运动不拥有业务顺序或输入会话。 */
final class SceneSortableMotion<T, K, C> {
    static final class Offset {
        static final Offset ZERO = new Offset(0, 0);
        final int x, y;
        Offset(int x, int y) { this.x = x; this.y = y; }
    }
    private static final class Landing {
        final SceneSortableTrack x, y, width, height, elevation;
        Landing(LayoutBox from, double vx, double vy, SceneSortableTrack.Strategy strategy, long now) {
            x = new SceneSortableTrack(from.x, vx, from.x, strategy, now);
            y = new SceneSortableTrack(from.y, vy, from.y, strategy, now);
            width = new SceneSortableTrack(from.width, 0, from.width, strategy, now);
            height = new SceneSortableTrack(from.height, 0, from.height, strategy, now);
            elevation = new SceneSortableTrack(1, 0, 0, strategy, now);
        }
        void retarget(LayoutBox to, SceneSortableTrack.Strategy strategy, long now) {
            x.retarget(to.x, strategy, now); y.retarget(to.y, strategy, now);
            width.retarget(to.width, strategy, now); height.retarget(to.height, strategy, now);
            elevation.retarget(0, strategy, now);
        }
        void policy(SceneSortableTrack.Strategy strategy, long now) {
            x.retarget(x.target(), strategy, now); y.retarget(y.target(), strategy, now);
            width.retarget(width.target(), strategy, now); height.retarget(height.target(), strategy, now);
            elevation.retarget(0, strategy, now);
        }
        boolean active() { return x.active() || y.active() || width.active() || height.active() || elevation.active(); }
        LayoutBox box() { return new LayoutBox(x.integer(), y.integer(), Math.max(0, width.integer()), Math.max(0, height.integer())); }
    }
    private static final long VELOCITY_WINDOW = 64_000_000L;
    private static final class Sample {
        final LayoutBox box;
        final long time;
        Sample(LayoutBox box, long time) { this.box = box; this.time = time; }
    }
    final Signal<SceneSortableInteraction.Feedback<T, K, C>> preview;
    final Signal<Boolean> busy;
    final Signal<Float> elevation;
    private final SceneSortableModel<T, K, C> model;
    private final SceneSortableFlights<T, K, C> flights;
    private SceneSortableTrack.Strategy reorderStrategy, dropStrategy;
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private Object sampleToken;
    private Landing landing;
    SceneSortableMotion(SceneSortableModel<T, K, C> model) {
        this.model = model; flights = new SceneSortableFlights<>(model);
        preview = model.context.domain().signal(null); busy = model.context.domain().signal(false);
        elevation = model.context.domain().signal(1f);
        configure(model.options());
    }
    void geometryChanged(SceneGeometryView geometry) { synchronize(geometry); }
    void positionChanged(SceneSortableSelection.Position<T, K, C> from, SceneSortableSelection.Position<T, K, C> to) {
        if (!SceneSortableSelection.same(from, to)) prepare();
    }
    private void prepare() { flights.prepare(); busy.set(true); }
    void prepareIfShowing() { if (preview.get() != null) prepare(); }
    void sample(Object token, LayoutBox box) {
        long now = model.runtime.inputNanos();
        if (sampleToken != token) { samples.clear(); sampleToken = token; }
        if (!samples.isEmpty() && now == samples.peekLast().time) samples.removeLast();
        samples.addLast(new Sample(box, now));
        while (samples.size() > 2) {
            Iterator<Sample> iterator = samples.iterator(); iterator.next();
            if (now - iterator.next().time <= VELOCITY_WINDOW) break;
            samples.removeFirst();
        }
    }
    private double speed(boolean horizontal, long now) {
        if (samples.size() < 2) return 0;
        Sample first = samples.peekFirst(), last = samples.peekLast();
        long beginning = Math.max(first.time, now - VELOCITY_WINDOW);
        if (last.time <= beginning) return 0;
        double origin = horizontal ? first.box.x : first.box.y;
        if (first.time < beginning) {
            Iterator<Sample> iterator = samples.iterator(); iterator.next(); Sample next = iterator.next();
            double fraction = (double) (beginning - first.time) / (next.time - first.time);
            origin += ((horizontal ? next.box.x : next.box.y) - origin) * fraction;
        }
        return ((horizontal ? last.box.x : last.box.y) - origin) * 1_000_000_000.0 / (last.time - beginning);
    }
    void show(SceneSortableInteraction.Feedback<T, K, C> value) {
        landing = null; preview.set(value); elevation.set(1f);
    }
    private void configure(SceneSortable.Options<T, C> options) {
        MotionPolicy policy = model.context.motionPolicy().get();
        reorderStrategy = SceneSortableTrack.Strategy.resolve(options.transition, options.reorderMotion, policy);
        dropStrategy = SceneSortableTrack.Strategy.resolve(options.dropAnimation, options.dropMotion, policy);
    }
    void finish() {
        SceneSortableInteraction.Feedback<T, K, C> value = preview.get();
        if (value == null || landing != null) return;
        prepare(); long now = model.runtime.inputNanos();
        landing = new Landing(value.box, speed(true, now), speed(false, now), dropStrategy, now);
        samples.clear(); sampleToken = null;
        if (!landing.active()) endPreview();
    }
    boolean ghost(SceneSortableModel.RowEntry<T, K, C> row) {
        SceneSortableInteraction.Feedback<T, K, C> value = preview.get();
        return value != null && value.row == row;
    }
    void synchronize(SceneGeometryView geometry) { advance(geometry, model.runtime.inputNanos()); }
    void advance(SceneGeometryView geometry, long now) {
        flights.synchronize(geometry, this::ghost, reorderStrategy, now);
        if (landing != null) {
            SceneSortableInteraction.Feedback<T, K, C> value = preview.get();
            SceneGeometryView.Entry target = value == null ? null : geometry.get(value.row.declaration.content);
            if (target == null || !model.runtime.interactive(value.row.node)) endPreview();
            else { landing.retarget(target.box, dropStrategy, now); publishLanding(); }
        }
        publishActivity();
        if (preview.get() != null || flights.active()) flights.remember(geometry);
    }
    /** 只在来源通知后解析策略；逐帧重求 supplier 的等价有限曲线会反复重启轨迹。
     *  声明阶段先采样旧轨迹再切策略，同一时刻的几何同步不重复推进。 */
    void policyChanged(SceneSortable.Options<T, C> options) {
        configure(options);
        long now = model.runtime.inputNanos();
        flights.policy(reorderStrategy, now);
        if (landing != null) { landing.policy(dropStrategy, now); publishLanding(); }
        publishActivity();
    }
    private void publishLanding() {
        if (!landing.active()) { endPreview(); return; }
        SceneSortableInteraction.Feedback<T, K, C> value = preview.get();
        elevation.set((float) Math.max(0, Math.min(1, landing.elevation.value())));
        LayoutBox box = landing.box(), old = value.box;
        if (box.x != old.x || box.y != old.y || box.width != old.width || box.height != old.height)
            preview.set(new SceneSortableInteraction.Feedback<>(value.token, value.row, null, box));
    }
    private void publishActivity() {
        boolean active = landing != null || flights.active();
        busy.set(active);
        if (!active && preview.get() == null) flights.forget();
    }
    private void endPreview() { preview.set(null); landing = null; samples.clear(); sampleToken = null; }
    void clear() { flights.clear(); endPreview(); busy.set(false); }
}
