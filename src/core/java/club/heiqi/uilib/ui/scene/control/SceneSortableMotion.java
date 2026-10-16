package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;

/** 协调让位与落下两个有限生命周期；交互只声明几何、位置变化和结束。 */
final class SceneSortableMotion<T, K, C> {
    static final class Offset {
        static final Offset ZERO = new Offset(0, 0);
        final int x, y;
        Offset(int x, int y) { this.x = x; this.y = y; }
    }
    private static final class Landing {
        final long started;
        final LayoutBox from;
        Landing(long started, LayoutBox from) { this.started = started; this.from = from; }
    }
    final Signal<SceneSortableInteraction.Feedback<T, K, C>> preview;
    final Signal<Boolean> busy;
    final Signal<Float> elevation;
    private final SceneSortableModel<T, K, C> model;
    private final SceneSortableFlights<T, K, C> flights;
    private Landing landing;
    SceneSortableMotion(SceneSortableModel<T, K, C> model) {
        this.model = model; flights = new SceneSortableFlights<>(model);
        preview = model.context.domain().signal(null); busy = model.context.domain().signal(false);
        elevation = model.context.domain().signal(1f);
    }
    void geometryChanged(SceneGeometryView geometry) {
        synchronize(geometry);
        flights.remember(geometry);
    }
    void positionChanged(SceneSortableSelection.Position<T, K, C> from, SceneSortableSelection.Position<T, K, C> to) {
        if (!SceneSortableSelection.same(from, to)) prepare();
    }
    private void prepare() { flights.prepare(); busy.set(true); }
    void prepareIfShowing() { if (preview.get() != null) prepare(); }
    void show(SceneSortableInteraction.Feedback<T, K, C> value) {
        landing = null; preview.set(value); elevation.set(1f);
    }
    void finish() {
        SceneSortableInteraction.Feedback<T, K, C> value = preview.get();
        if (value == null || landing != null) return;
        prepare(); landing = new Landing(model.runtime.inputNanos(), value.box);
    }
    boolean ghost(SceneSortableModel.RowEntry<T, K, C> row) {
        SceneSortableInteraction.Feedback<T, K, C> value = preview.get();
        return value != null && value.row == row;
    }
    void synchronize(SceneGeometryView geometry) {
        flights.synchronize(geometry, this::ghost);
        publishActivity();
    }
    void advance(SceneGeometryView geometry, long now) {
        synchronize(geometry);
        flights.advance(now, model.options().transition);
        if (landing != null) advanceDrop(geometry, now);
        publishActivity();
    }
    private void publishActivity() {
        boolean active = landing != null || flights.active();
        busy.set(active);
        if (!active && preview.get() == null) flights.forget();
    }
    private void advanceDrop(SceneGeometryView geometry, long now) {
        SceneSortableInteraction.Feedback<T, K, C> value = preview.get();
        SceneGeometryView.Entry target = value == null ? null : geometry.get(value.row.declaration.content);
        if (target == null || !model.runtime.interactive(value.row.node)) { endPreview(); return; }
        double progress = progress(now, landing.started, model.options().dropAnimation);
        if (progress >= 1) { endPreview(); return; }
        double factor = factor(progress, model.options().dropAnimation);
        elevation.set((float) Math.max(0, Math.min(1, 1 - factor)));
        LayoutBox box = interpolate(landing.from, target.box, factor);
        preview.set(new SceneSortableInteraction.Feedback<>(value.token, value.row, null, box));
    }
    private static LayoutBox interpolate(LayoutBox from, LayoutBox to, double factor) {
        return new LayoutBox(mix(from.x, to.x, factor), mix(from.y, to.y, factor),
                Math.max(0, mix(from.width, to.width, factor)), Math.max(0, mix(from.height, to.height, factor)));
    }
    private static int mix(int from, int to, double factor) { return SceneDragSupport.integer(Math.round(from + ((double) to - from) * factor)); }
    static double progress(long now, long started, SceneSortable.Transition transition) {
        return transition == null || transition.millis == 0 ? 1 : Math.min(1, Math.max(0, (double) (now - started) / (transition.millis * 1_000_000L)));
    }
    static double factor(double progress, SceneSortable.Transition transition) {
        double value = progress >= 1 ? 1 : transition.easing.applyAsDouble(progress);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("排序过渡曲线必须返回有限值");
        return value;
    }
    private void endPreview() { preview.set(null); landing = null; }
    void clear() { flights.clear(); endPreview(); busy.set(false); }
}
