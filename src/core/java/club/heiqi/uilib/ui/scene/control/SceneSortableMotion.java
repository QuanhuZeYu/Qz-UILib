package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/** 只保留当次过渡端点；时间来自正式输入帧，落点始终查询当前共同几何。 */
final class SceneSortableMotion<T, K, C> {
    static final class Offset {
        static final Offset ZERO = new Offset(0, 0);
        final int x, y;
        Offset(int x, int y) { this.x = x; this.y = y; }
    }
    private static final class Flight {
        final int x, y;
        final long started;
        Flight(int x, int y, long started) { this.x = x; this.y = y; this.started = started; }
    }
    final Signal<SceneSortableInteraction.Feedback<T, K, C>> preview;
    final Signal<Boolean> busy;
    final Signal<Float> elevation;
    private final SceneSortableModel<T, K, C> model;
    private final Map<SceneSortableModel.RowEntry<T, K, C>, Flight> flights = new IdentityHashMap<>();
    private Map<SceneSortableModel.RowEntry<T, K, C>, LayoutBox> previous = new IdentityHashMap<>(), pending;
    private boolean ending, showing;
    private long dropped;
    private LayoutBox dropFrom;
    SceneSortableMotion(SceneSortableModel<T, K, C> model) {
        this.model = model; preview = model.context.domain().signal(null); busy = model.context.domain().signal(false);
        elevation = model.context.domain().signal(1f);
    }
    void remember(SceneGeometryView geometry) {
        Map<SceneSortableModel.RowEntry<T, K, C>, LayoutBox> visible = new IdentityHashMap<>();
        for (SceneSortableModel.RowEntry<T, K, C> row : model.rows.values()) {
            SceneGeometryView.Entry entry = geometry.get(row.declaration.content);
            if (entry != null) visible.put(row, entry.box);
        }
        previous = visible;
    }
    void prepare() {
        if (pending == null) pending = new IdentityHashMap<>(previous);
        busy.set(true);
    }
    void prepareIfShowing() { if (showing) prepare(); }
    void show(SceneSortableInteraction.Feedback<T, K, C> value) {
        ending = false; showing = true; dropFrom = null;
        preview.set(value); elevation.set(1f);
    }
    void finish() {
        if (preview.get() == null || ending) return;
        prepare(); ending = true; dropped = model.runtime.inputNanos(); dropFrom = preview.get().box;
    }
    boolean ghost(SceneSortableModel.RowEntry<T, K, C> row) {
        SceneSortableInteraction.Feedback<T, K, C> value = preview.get();
        return value != null && value.row == row;
    }
    void synchronize(SceneGeometryView geometry) {
        if (pending == null) return;
        long now = model.runtime.inputNanos();
        for (Map.Entry<SceneSortableModel.RowEntry<T, K, C>, LayoutBox> entry : pending.entrySet()) {
            SceneSortableModel.RowEntry<T, K, C> row = entry.getKey();
            SceneGeometryView.Entry target = geometry.get(row.node);
            if (target == null || model.rows.get(row.key) != row) continue;
            int x = SceneDragSupport.integer((long) entry.getValue().x - target.box.x);
            int y = SceneDragSupport.integer((long) entry.getValue().y - target.box.y);
            if (ghost(row) || model.options().transition == null || x == 0 && y == 0) {
                flights.remove(row); offset(row, 0, 0);
            } else {
                flights.put(row, new Flight(x, y, now)); offset(row, x, y);
            }
        }
        pending = null;
        busy.set(ending || !flights.isEmpty());
    }
    void advance(SceneGeometryView geometry, long now) {
        synchronize(geometry);
        SceneSortable.Transition transition = model.options().transition;
        Iterator<Map.Entry<SceneSortableModel.RowEntry<T, K, C>, Flight>> iterator = flights.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SceneSortableModel.RowEntry<T, K, C>, Flight> entry = iterator.next();
            SceneSortableModel.RowEntry<T, K, C> row = entry.getKey(); Flight flight = entry.getValue();
            double progress = progress(now, flight.started, transition);
            double factor = factor(progress, transition);
            offset(row, (int) Math.round(flight.x * (1 - factor)), (int) Math.round(flight.y * (1 - factor)));
            if (progress >= 1 || row.node.runtime() == null) iterator.remove();
        }
        if (ending) advanceDrop(geometry, now);
        busy.set(pending != null || ending || !flights.isEmpty());
    }
    private void advanceDrop(SceneGeometryView geometry, long now) {
        SceneSortableInteraction.Feedback<T, K, C> value = preview.get();
        SceneGeometryView.Entry target = value == null ? null : geometry.get(value.row.declaration.content);
        if (target == null || !model.runtime.interactive(value.row.node)) { endPreview(); return; }
        double progress = progress(now, dropped, model.options().dropAnimation);
        if (progress >= 1) { endPreview(); return; }
        double factor = factor(progress, model.options().dropAnimation);
        elevation.set((float) Math.max(0, Math.min(1, 1 - factor)));
        LayoutBox box = new LayoutBox(mix(dropFrom.x, target.box.x, factor), mix(dropFrom.y, target.box.y, factor),
                Math.max(0, mix(dropFrom.width, target.box.width, factor)), Math.max(0, mix(dropFrom.height, target.box.height, factor)));
        preview.set(new SceneSortableInteraction.Feedback<>(value.token, value.row, null, box));
    }
    private static int mix(int from, int to, double factor) { return SceneDragSupport.integer(Math.round(from + ((double) to - from) * factor)); }
    private static double progress(long now, long started, SceneSortable.Transition transition) {
        return transition == null || transition.millis == 0 ? 1 : Math.min(1, Math.max(0, (double) (now - started) / (transition.millis * 1_000_000L)));
    }
    private static double factor(double progress, SceneSortable.Transition transition) {
        double value = progress >= 1 ? 1 : transition.easing.applyAsDouble(progress);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("排序过渡曲线必须返回有限值");
        return value;
    }
    private static <T, K, C> void offset(SceneSortableModel.RowEntry<T, K, C> row, int x, int y) {
        Offset old = row.offset.get();
        if (old.x != x || old.y != y) row.offset.set(x == 0 && y == 0 ? Offset.ZERO : new Offset(x, y));
    }
    private void endPreview() { preview.set(null); ending = false; showing = false; dropFrom = null; }
    void clear() {
        for (SceneSortableModel.RowEntry<T, K, C> row : flights.keySet()) offset(row, 0, 0);
        flights.clear(); previous.clear(); pending = null; endPreview(); busy.set(false);
    }
}
