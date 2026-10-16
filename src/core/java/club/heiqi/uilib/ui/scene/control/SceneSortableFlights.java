package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Predicate;

/** 让位时间线拥有可见起点、待同步快照与位移；落下反馈由 Motion 单独负责。 */
final class SceneSortableFlights<T, K, C> {
    private static final class Flight {
        final int x, y;
        final long started;
        Flight(int x, int y, long started) { this.x = x; this.y = y; this.started = started; }
    }
    private final SceneSortableModel<T, K, C> model;
    private final Map<SceneSortableModel.RowEntry<T, K, C>, Flight> flights = new IdentityHashMap<>();
    private Map<SceneSortableModel.RowEntry<T, K, C>, LayoutBox> previous = new IdentityHashMap<>(), pending;
    SceneSortableFlights(SceneSortableModel<T, K, C> model) { this.model = model; }
    void remember(SceneGeometryView geometry) {
        Map<SceneSortableModel.RowEntry<T, K, C>, LayoutBox> visible = new IdentityHashMap<>();
        for (SceneSortableModel.RowEntry<T, K, C> row : model.rows.values()) {
            SceneGeometryView.Entry entry = geometry.get(row.declaration.content);
            if (entry != null) visible.put(row, entry.box);
        }
        previous = visible;
    }
    void prepare() { if (pending == null) pending = new IdentityHashMap<>(previous); }
    boolean active() { return pending != null || !flights.isEmpty(); }
    void synchronize(SceneGeometryView geometry, Predicate<SceneSortableModel.RowEntry<T, K, C>> ghost) {
        if (pending == null) return;
        long now = model.runtime.inputNanos();
        for (Map.Entry<SceneSortableModel.RowEntry<T, K, C>, LayoutBox> entry : pending.entrySet())
            retarget(entry.getKey(), entry.getValue(), geometry, ghost, now);
        pending = null;
    }
    private void retarget(SceneSortableModel.RowEntry<T, K, C> row, LayoutBox from, SceneGeometryView geometry,
                          Predicate<SceneSortableModel.RowEntry<T, K, C>> ghost, long now) {
        SceneGeometryView.Entry target = geometry.get(row.node);
        if (target == null || model.rows.get(row.key) != row) return;
        int x = SceneDragSupport.integer((long) from.x - target.box.x), y = SceneDragSupport.integer((long) from.y - target.box.y);
        if (ghost.test(row) || model.options().transition == null || x == 0 && y == 0) {
            flights.remove(row); offset(row, 0, 0);
        } else {
            flights.put(row, new Flight(x, y, now)); offset(row, x, y);
        }
    }
    void advance(long now, SceneSortable.Transition transition) {
        Iterator<Map.Entry<SceneSortableModel.RowEntry<T, K, C>, Flight>> iterator = flights.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SceneSortableModel.RowEntry<T, K, C>, Flight> entry = iterator.next();
            if (advance(entry.getKey(), entry.getValue(), now, transition)) iterator.remove();
        }
    }
    private boolean advance(SceneSortableModel.RowEntry<T, K, C> row, Flight flight, long now, SceneSortable.Transition transition) {
        double progress = SceneSortableMotion.progress(now, flight.started, transition);
        double factor = SceneSortableMotion.factor(progress, transition);
        offset(row, (int) Math.round(flight.x * (1 - factor)), (int) Math.round(flight.y * (1 - factor)));
        return progress >= 1 || row.node.runtime() == null;
    }
    private static <T, K, C> void offset(SceneSortableModel.RowEntry<T, K, C> row, int x, int y) {
        SceneSortableMotion.Offset old = row.offset.get();
        if (old.x != x || old.y != y)
            row.offset.set(x == 0 && y == 0 ? SceneSortableMotion.Offset.ZERO : new SceneSortableMotion.Offset(x, y));
    }
    /** 快照只为下一次投影续接服务，交互与飞行结束后不保留旧业务值。 */
    void forget() { previous.clear(); }
    void clear() {
        for (SceneSortableModel.RowEntry<T, K, C> row : flights.keySet()) offset(row, 0, 0);
        flights.clear(); forget(); pending = null;
    }
}
