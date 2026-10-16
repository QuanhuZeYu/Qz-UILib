package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 一次位置查询：先裁定空间容器，再按该容器布局求插槽；不持有跨批几何。 */
final class SceneSortableSelection<T, K, C> {
    static final class Position<T, K, C> {
        final SceneSortableModel.ContainerEntry<T, K, C> container;
        final K before;
        Position(SceneSortableModel.ContainerEntry<T, K, C> container, K before) { this.container = container; this.before = before; }
        boolean same(Position<T, K, C> other) { return other != null && container == other.container && Objects.equals(before, other.before); }
    }
    static boolean same(Position<?, ?, ?> a, Position<?, ?, ?> b) {
        return a == b || a != null && b != null && a.container == b.container && Objects.equals(a.before, b.before);
    }
    private final SceneSortableModel<T, K, C> model;
    SceneSortableSelection(SceneSortableModel<T, K, C> model) { this.model = model; }

    List<SceneSortableModel.ContainerEntry<T, K, C>> containers(SceneSortableModel.RowEntry<T, K, C> source, SceneGeometryView geometry) {
        List<SceneSortableModel.ContainerEntry<T, K, C>> result = new ArrayList<>();
        for (SceneDragDrop.Candidate<Object> candidate : model.scope.candidates(new SceneSortableKeys.SourceKey<>(source.key), source.node, geometry)) {
            if (!(candidate.key instanceof SceneSortableKeys.ContainerTarget)) continue;
            SceneSortableModel.ContainerEntry<T, K, C> container = model.containers.get(((SceneSortableKeys.ContainerTarget<?>) candidate.key).key);
            if (container != null) result.add(container);
        }
        return result;
    }
    Position<T, K, C> pointer(SceneSortableModel.RowEntry<T, K, C> source, SceneGeometryView geometry, int x, int y) {
        List<SceneSortableModel.ContainerEntry<T, K, C>> legal = containers(source, geometry);
        SceneSortableModel.ContainerEntry<T, K, C> target = nearestContainer(geometry, x, y);
        // 政策不能改变空间命中：最近容器拒绝时不吸附远处的合法容器。
        if (target == null || !legal.contains(target)) return null;
        return insertion(source, target, geometry, x, y);
    }
    private SceneSortableModel.ContainerEntry<T, K, C> nearestContainer(SceneGeometryView geometry, int x, int y) {
        Map<SceneNode, SceneSortableModel.ContainerEntry<T, K, C>> roots = new IdentityHashMap<>();
        for (SceneSortableModel.ContainerEntry<T, K, C> container : model.containers.values()) roots.put(container.declaration.root, container);
        SceneSortableModel.ContainerEntry<T, K, C> chosen = null;
        double best = Double.POSITIVE_INFINITY;
        List<SceneGeometryView.Entry> ordered = geometry.ordered();
        // 平局保留正式绘制逆序中的上层，PASSIVE 不参与空间命中。
        for (int index = ordered.size() - 1; index >= 0; index--) {
            SceneGeometryView.Entry entry = ordered.get(index);
            SceneSortableModel.ContainerEntry<T, K, C> container = roots.get(entry.node);
            if (container == null || !model.runtime.interactive(entry.node)) continue;
            LayoutBox box = SceneDragSupport.visibleBox(geometry, entry);
            if (box == null || box.width == 0 || box.height == 0) continue;
            double score = distance(box, x, y);
            if (score < best) { chosen = container; best = score; }
        }
        return chosen;
    }
    private static double distance(LayoutBox box, int x, int y) {
        double dx = Math.max(Math.max((double) box.x - x, x - ((double) box.x + box.width)), 0);
        double dy = Math.max(Math.max((double) box.y - y, y - ((double) box.y + box.height)), 0);
        return dx * dx + dy * dy;
    }
    boolean occupies(SceneSortableModel.RowEntry<T, K, C> source, Position<T, K, C> position,
                     SceneGeometryView geometry, int x, int y) {
        if (!valid(source, position, geometry) || source.node.parent() != position.container.declaration.items) return false;
        LayoutBox box = SceneDragSupport.visibleBox(geometry, geometry.get(source.node));
        return box != null && box.contains(x, y);
    }
    private List<SceneSortableModel.RowEntry<T, K, C>> rows(SceneSortableModel.ContainerEntry<T, K, C> container,
            SceneGeometryView geometry, SceneSortableModel.RowEntry<T, K, C> excluded) {
        List<SceneSortableModel.RowEntry<T, K, C>> result = new ArrayList<>();
        for (SceneSortableModel.RowEntry<T, K, C> row : container.displayed)
            if (row != excluded && geometry.get(row.node) != null && row.node.visible()) result.add(row);
        return result;
    }
    private Position<T, K, C> insertion(SceneSortableModel.RowEntry<T, K, C> source,
            SceneSortableModel.ContainerEntry<T, K, C> container, SceneGeometryView geometry, int x, int y) {
        if (container.declaration.layout == SceneSortable.Layout.GRID) return gridInsertion(source, container, geometry, x, y);
        boolean horizontal = container.declaration.layout == SceneSortable.Layout.HORIZONTAL;
        for (SceneSortableModel.RowEntry<T, K, C> row : rows(container, geometry, source)) {
            LayoutBox box = geometry.get(row.node).box;
            if ((horizontal ? x : y) < (horizontal ? (double) box.x + box.width / 2.0 : (double) box.y + box.height / 2.0))
                return new Position<>(container, row.key);
        }
        return new Position<>(container, null);
    }
    private Position<T, K, C> gridInsertion(SceneSortableModel.RowEntry<T, K, C> source,
            SceneSortableModel.ContainerEntry<T, K, C> container, SceneGeometryView geometry, int x, int y) {
        List<SceneSortableModel.RowEntry<T, K, C>> all = rows(container, geometry, null);
        List<SceneSortableModel.RowEntry<T, K, C>> band = nearestBand(all, geometry, y);
        for (SceneSortableModel.RowEntry<T, K, C> row : band) {
            if (row == source) continue;
            LayoutBox box = geometry.get(row.node).box;
            if (x < (double) box.x + box.width / 2.0) return new Position<>(container, row.key);
        }
        if (band.isEmpty()) return new Position<>(container, null);
        int index = all.indexOf(band.get(band.size() - 1)) + 1;
        while (index < all.size() && all.get(index) == source) index++;
        return new Position<>(container, index < all.size() ? all.get(index).key : null);
    }
    /** 变高网格按整行可见带选择，不能把行内空白当成下一行。 */
    private List<SceneSortableModel.RowEntry<T, K, C>> nearestBand(List<SceneSortableModel.RowEntry<T, K, C>> all,
                                                                 SceneGeometryView geometry, int y) {
        List<SceneSortableModel.RowEntry<T, K, C>> chosen = new ArrayList<>();
        double best = Double.POSITIVE_INFINITY;
        for (int start = 0; start < all.size();) {
            LayoutBox first = geometry.get(all.get(start).node).box;
            int end = start + 1;
            long bottom = (long) first.y + first.height;
            while (end < all.size() && geometry.get(all.get(end).node).box.y == first.y) {
                LayoutBox box = geometry.get(all.get(end).node).box;
                bottom = Math.max(bottom, (long) box.y + box.height); end++;
            }
            double score = y < first.y ? (double) first.y - y : Math.max(0, y - (double) bottom);
            if (score < best) { chosen = all.subList(start, end); best = score; }
            start = end;
        }
        return chosen;
    }
    boolean valid(SceneSortableModel.RowEntry<T, K, C> source, Position<T, K, C> position, SceneGeometryView geometry) {
        if (position == null || !containers(source, geometry).contains(position.container)) return false;
        if (position.before == null) return true;
        SceneSortableModel.RowEntry<T, K, C> before = model.rows.get(position.before);
        return before != null && before != source && before.container.get() == position.container && geometry.get(before.node) != null;
    }
    Position<T, K, C> step(SceneSortableModel.RowEntry<T, K, C> source, Position<T, K, C> current,
                           SceneGeometryView geometry, SceneEditEvent.Command direction) {
        Navigation navigation = new Navigation(source, current, geometry, direction);
        for (SceneSortableModel.ContainerEntry<T, K, C> container : containers(source, geometry)) navigation.consider(container);
        return navigation.position(current);
    }
    /** 一次方向查询持有候选评分；它不改变焦点、滚动或业务集合。 */
    private final class Navigation {
        private final SceneSortableModel.RowEntry<T, K, C> source;
        private final SceneSortableModel.ContainerEntry<T, K, C> current;
        private final SceneGeometryView geometry;
        private final LayoutBox origin;
        private final boolean horizontal, positive;
        private SceneSortableModel.RowEntry<T, K, C> chosen;
        private SceneSortableModel.ContainerEntry<T, K, C> destination;
        private double best = Double.POSITIVE_INFINITY;
        Navigation(SceneSortableModel.RowEntry<T, K, C> source, Position<T, K, C> position,
                   SceneGeometryView geometry, SceneEditEvent.Command direction) {
            this.source = source; this.geometry = geometry;
            current = position == null ? source.container.get() : position.container;
            SceneGeometryView.Entry entry = geometry.get(source.node);
            origin = entry == null ? geometry.get(current.declaration.root).box : entry.box;
            horizontal = direction == SceneEditEvent.Command.LEFT || direction == SceneEditEvent.Command.RIGHT;
            positive = direction == SceneEditEvent.Command.RIGHT || direction == SceneEditEvent.Command.DOWN;
        }
        void consider(SceneSortableModel.ContainerEntry<T, K, C> container) {
            boolean localAxis = container.declaration.layout == SceneSortable.Layout.GRID
                    || horizontal == (container.declaration.layout == SceneSortable.Layout.HORIZONTAL);
            if (container != current || localAxis) considerRows(container);
            if (container != current && rows(container, geometry, source).isEmpty())
                offer(container, null, geometry.get(container.declaration.root).box);
        }
        private void considerRows(SceneSortableModel.ContainerEntry<T, K, C> container) {
            for (SceneSortableModel.RowEntry<T, K, C> row : rows(container, geometry, source)) offer(container, row, geometry.get(row.node).box);
        }
        private void offer(SceneSortableModel.ContainerEntry<T, K, C> container, SceneSortableModel.RowEntry<T, K, C> row, LayoutBox box) {
            double score = SceneDragSupport.directionScore(origin, box, horizontal, positive);
            if (score < best) { best = score; chosen = row; destination = container; }
        }
        Position<T, K, C> position(Position<T, K, C> fallback) {
            if (destination == null) return fallback;
            if (chosen == null) return new Position<>(destination, null);
            if (!positive) return new Position<>(destination, chosen.key);
            List<SceneSortableModel.RowEntry<T, K, C>> ordered = destination.displayed;
            int index = ordered.indexOf(chosen) + 1;
            while (index < ordered.size() && ordered.get(index) == source) index++;
            return new Position<>(destination, index < ordered.size() ? ordered.get(index).key : null);
        }
    }
}
