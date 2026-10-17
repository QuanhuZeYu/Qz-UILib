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
    /** 轨迹持有自己的插槽基准；局部坐标去掉滚动，跨容器时再转回世界坐标。 */
    private static final class Flight {
        final SceneSortableTrack x, y;
        private SceneNode anchor;
        private int baseX, baseY, worldX, worldY;
        Flight(int x, int y, SceneSortableTrack.Strategy strategy, long now) {
            this.x = new SceneSortableTrack(x, 0, 0, strategy, now);
            this.y = new SceneSortableTrack(y, 0, 0, strategy, now);
        }
        void locate(SceneNode nextAnchor, SceneGeometryView geometry, LayoutBox slot, long now, boolean rebase) {
            SceneGeometryView.Entry parent = geometry.get(nextAnchor);
            int nextBaseX = slot.x - parent.box.x + scrollX(parent);
            int nextBaseY = slot.y - parent.box.y + scrollY(parent);
            if (rebase) {
                x.advance(now); y.advance(now);
                double dx = (double) baseX - nextBaseX, dy = (double) baseY - nextBaseY;
                if (anchor != nextAnchor) {
                    SceneGeometryView.Entry previousParent = geometry.get(anchor);
                    double previousX = worldX, previousY = worldY;
                    if (previousParent != null) {
                        previousX = (double) previousParent.box.x - scrollX(previousParent) + baseX;
                        previousY = (double) previousParent.box.y - scrollY(previousParent) + baseY;
                    }
                    dx = previousX - slot.x; dy = previousY - slot.y;
                }
                x.shift(dx); y.shift(dy);
            }
            anchor = nextAnchor; baseX = nextBaseX; baseY = nextBaseY; worldX = slot.x; worldY = slot.y;
        }
        void retarget(SceneSortableTrack.Strategy strategy, long now) {
            x.retarget(0, strategy, now); y.retarget(0, strategy, now);
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
        if (reordering) launchPending(geometry, ghost, strategy, now);
        Iterator<Map.Entry<SceneSortableModel.RowEntry<T, K, C>, Flight>> iterator = flights.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SceneSortableModel.RowEntry<T, K, C>, Flight> entry = iterator.next();
            SceneSortableModel.RowEntry<T, K, C> row = entry.getKey(); Flight flight = entry.getValue();
            LayoutBox slot = slot(row, geometry, ghost);
            if (slot == null) {
                offset(row, 0, 0); iterator.remove(); continue;
            }
            // 原有限让位保留相对偏移，响应布局直接采用新槽；显式运动才续接布局目标变化。
            flight.locate(row.placed.get().declaration.items, geometry, slot, now, reordering || strategy.explicit);
            flight.retarget(strategy, now);
            offset(row, flight.x.integer(), flight.y.integer());
            if (!flight.active()) iterator.remove();
        }
    }
    private void launchPending(SceneGeometryView geometry, Predicate<SceneSortableModel.RowEntry<T, K, C>> ghost,
                               SceneSortableTrack.Strategy strategy, long now) {
        for (Map.Entry<SceneSortableModel.RowEntry<T, K, C>, LayoutBox> entry : pending.entrySet()) {
            SceneSortableModel.RowEntry<T, K, C> row = entry.getKey();
            LayoutBox slot = slot(row, geometry, ghost);
            if (slot == null || flights.containsKey(row)) continue;
            int x = SceneDragSupport.integer((long) entry.getValue().x - slot.x);
            int y = SceneDragSupport.integer((long) entry.getValue().y - slot.y);
            if (x == 0 && y == 0) continue;
            Flight flight = new Flight(x, y, strategy, now);
            flight.locate(row.placed.get().declaration.items, geometry, slot, now, false);
            flights.put(row, flight);
        }
        pending = null;
    }
    private LayoutBox slot(SceneSortableModel.RowEntry<T, K, C> row, SceneGeometryView geometry,
                           Predicate<SceneSortableModel.RowEntry<T, K, C>> ghost) {
        SceneGeometryView.Entry entry = geometry.get(row.node);
        if (entry == null || model.rows.get(row.key) != row || ghost.test(row)) return null;
        return entry.box;
    }
    private static int scrollX(SceneGeometryView.Entry entry) { return entry.node.axis() == SceneNode.Axis.ROW ? entry.scrollOffset : 0; }
    private static int scrollY(SceneGeometryView.Entry entry) { return entry.node.axis() == SceneNode.Axis.ROW ? 0 : entry.scrollOffset; }
    void policy(SceneSortableTrack.Strategy strategy, long now) {
        Iterator<Map.Entry<SceneSortableModel.RowEntry<T, K, C>, Flight>> iterator = flights.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SceneSortableModel.RowEntry<T, K, C>, Flight> entry = iterator.next(); Flight flight = entry.getValue();
            flight.retarget(strategy, now);
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
