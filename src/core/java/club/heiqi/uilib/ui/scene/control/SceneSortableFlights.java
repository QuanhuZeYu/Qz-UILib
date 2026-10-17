package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Predicate;

/** 让位只驱动现有相对偏移；插槽从同批几何读取，不保存第二套布局。 */
final class SceneSortableFlights<T, K, C> {
    private static final class Flight {
        final SceneSortableTrack x, y;
        SceneNode anchor;
        int baseX, baseY, worldX, worldY;
        Flight(int x, int y, SceneSortableTrack.Strategy strategy, long now) {
            this.x = new SceneSortableTrack(x, 0, 0, strategy, now);
            this.y = new SceneSortableTrack(y, 0, 0, strategy, now);
        }
        boolean active() { return x.active() || y.active(); }
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
    void synchronize(SceneGeometryView geometry, Predicate<SceneSortableModel.RowEntry<T, K, C>> ghost,
                     SceneSortableTrack.Strategy strategy, long now) {
        boolean reordering = pending != null;
        if (pending != null) {
            for (Map.Entry<SceneSortableModel.RowEntry<T, K, C>, LayoutBox> entry : pending.entrySet()) {
                SceneSortableModel.RowEntry<T, K, C> row = entry.getKey();
                SceneGeometryView.Entry slot = geometry.get(row.node);
                if (slot == null || model.rows.get(row.key) != row || ghost.test(row) || flights.containsKey(row)) continue;
                int x = SceneDragSupport.integer((long) entry.getValue().x - slot.box.x);
                int y = SceneDragSupport.integer((long) entry.getValue().y - slot.box.y);
                if (x == 0 && y == 0) continue;
                Flight flight = new Flight(x, y, strategy, now);
                locate(flight, row, geometry, slot.box, now, false); flights.put(row, flight);
            }
            pending = null;
        }
        Iterator<Map.Entry<SceneSortableModel.RowEntry<T, K, C>, Flight>> iterator = flights.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SceneSortableModel.RowEntry<T, K, C>, Flight> entry = iterator.next();
            SceneSortableModel.RowEntry<T, K, C> row = entry.getKey(); Flight flight = entry.getValue();
            SceneGeometryView.Entry slot = geometry.get(row.node);
            if (slot == null || model.rows.get(row.key) != row || ghost.test(row)) {
                offset(row, 0, 0); iterator.remove(); continue;
            }
            // 原有限让位保留相对偏移，响应布局直接采用新槽；显式运动才续接布局目标变化。
            locate(flight, row, geometry, slot.box, now, reordering || strategy.explicit);
            flight.x.retarget(0, strategy, now); flight.y.retarget(0, strategy, now);
            offset(row, flight.x.integer(), flight.y.integer());
            if (!flight.active()) iterator.remove();
        }
    }
    private void locate(Flight flight, SceneSortableModel.RowEntry<T, K, C> row, SceneGeometryView geometry,
                        LayoutBox slot, long now, boolean rebase) {
        SceneNode anchor = row.placed.get().declaration.items;
        SceneGeometryView.Entry parent = geometry.get(anchor);
        int baseX = slot.x - parent.box.x + scrollX(parent), baseY = slot.y - parent.box.y + scrollY(parent);
        if (rebase) {
            flight.x.advance(now); flight.y.advance(now);
            SceneGeometryView.Entry previousParent = geometry.get(flight.anchor);
            double dx = flight.anchor == anchor ? (double) flight.baseX - baseX
                    : (previousParent == null ? flight.worldX : (double) previousParent.box.x - scrollX(previousParent) + flight.baseX) - slot.x;
            double dy = flight.anchor == anchor ? (double) flight.baseY - baseY
                    : (previousParent == null ? flight.worldY : (double) previousParent.box.y - scrollY(previousParent) + flight.baseY) - slot.y;
            flight.x.shift(dx); flight.y.shift(dy);
        }
        flight.anchor = anchor; flight.baseX = baseX; flight.baseY = baseY; flight.worldX = slot.x; flight.worldY = slot.y;
    }
    private static int scrollX(SceneGeometryView.Entry entry) { return entry.node.axis() == SceneNode.Axis.ROW ? entry.scrollOffset : 0; }
    private static int scrollY(SceneGeometryView.Entry entry) { return entry.node.axis() == SceneNode.Axis.ROW ? 0 : entry.scrollOffset; }
    void policy(SceneSortableTrack.Strategy strategy, long now) {
        Iterator<Map.Entry<SceneSortableModel.RowEntry<T, K, C>, Flight>> iterator = flights.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SceneSortableModel.RowEntry<T, K, C>, Flight> entry = iterator.next(); Flight flight = entry.getValue();
            flight.x.retarget(0, strategy, now); flight.y.retarget(0, strategy, now);
            offset(entry.getKey(), flight.x.integer(), flight.y.integer());
            if (!flight.active()) iterator.remove();
        }
    }
    private static <T, K, C> void offset(SceneSortableModel.RowEntry<T, K, C> row, int x, int y) {
        SceneSortableMotion.Offset old = row.offset.get();
        if (old.x != x || old.y != y)
            row.offset.set(x == 0 && y == 0 ? SceneSortableMotion.Offset.ZERO : new SceneSortableMotion.Offset(x, y));
    }
    /** 临时几何仅为当前交互续接服务；静止后不保留节点和业务快照。 */
    void forget() { previous.clear(); }
    void clear() {
        for (SceneSortableModel.RowEntry<T, K, C> row : flights.keySet()) offset(row, 0, 0);
        flights.clear(); forget(); pending = null;
    }
}
