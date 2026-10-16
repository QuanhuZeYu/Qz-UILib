package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;

/** 候选只是当批几何查询；插槽来自真实盒，既不排版也不保留几何索引。 */
final class SceneSortableSelection<T, K, C> {
    static final class Position<T, K, C> {
        final SceneSortableModel.ContainerEntry<T, K, C> container;
        final K before;
        Position(SceneSortableModel.ContainerEntry<T, K, C> container, K before) { this.container = container; this.before = before; }
        boolean same(Position<T, K, C> other) { return other != null && container == other.container && Objects.equals(before, other.before); }
    }
    static final class Step<T, K, C> {
        final Position<T, K, C> position;
        final SceneSortableModel.RowEntry<T, K, C> anchor;
        Step(Position<T, K, C> position, SceneSortableModel.RowEntry<T, K, C> anchor) { this.position = position; this.anchor = anchor; }
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
        SceneSortableModel.ContainerEntry<T, K, C> chosen = null;
        double best = Double.POSITIVE_INFINITY;
        Map<SceneNode, SceneSortableModel.ContainerEntry<T, K, C>> roots = new IdentityHashMap<>();
        for (SceneSortableModel.ContainerEntry<T, K, C> container : model.containers.values())
            roots.put(container.declaration.root, container);
        List<SceneGeometryView.Entry> ordered = geometry.ordered();
        // 空间目标先于政策；平局消费和 Candidate.order 相同的正式绘制逆序，不从声明顺序推层序。
        for (int index = ordered.size() - 1; index >= 0; index--) {
            SceneGeometryView.Entry entry = ordered.get(index);
            SceneSortableModel.ContainerEntry<T, K, C> container = roots.get(entry.node);
            if (container == null || !model.runtime.interactive(entry.node)) continue;
            LayoutBox box = SceneDragSupport.visibleBox(geometry, entry);
            if (box == null || box.width == 0 || box.height == 0) continue;
            double dx = Math.max(Math.max((double) box.x - x, x - ((double) box.x + box.width)), 0);
            double dy = Math.max(Math.max((double) box.y - y, y - ((double) box.y + box.height)), 0);
            double score = dx * dx + dy * dy;
            if (chosen == null || score < best) { chosen = container; best = score; }
        }
        return chosen == null || !legal.contains(chosen) ? null : insertion(source, chosen, geometry, x, y);
    }
    boolean occupies(SceneSortableModel.RowEntry<T, K, C> source, Position<T, K, C> position,
                     SceneGeometryView geometry, int x, int y) {
        if (!valid(source, position, geometry) || source.node.parent() != position.container.declaration.items) return false;
        LayoutBox box = SceneDragSupport.visibleBox(geometry, geometry.get(source.node));
        return box != null && x >= box.x && (long) x < (long) box.x + box.width
                && y >= box.y && (long) y < (long) box.y + box.height;
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
        List<SceneSortableModel.RowEntry<T, K, C>> rows = rows(container, geometry, source);
        if (container.declaration.layout != SceneSortable.Layout.GRID) {
            boolean horizontal = container.declaration.layout == SceneSortable.Layout.HORIZONTAL;
            for (SceneSortableModel.RowEntry<T, K, C> row : rows) {
                LayoutBox box = geometry.get(row.node).box;
                if ((horizontal ? x : y) < (horizontal ? (double) box.x + box.width / 2.0 : (double) box.y + box.height / 2.0))
                    return new Position<>(container, row.key);
            }
            return new Position<>(container, null);
        }
        // 响应网格按实际行带选择，再沿该行中点插入；变高行的空白仍归同一行带。
        List<SceneSortableModel.RowEntry<T, K, C>> all = rows(container, geometry, null);
        List<SceneSortableModel.RowEntry<T, K, C>> band = new ArrayList<>();
        double bestBand = Double.POSITIVE_INFINITY;
        for (int start = 0; start < all.size();) {
            LayoutBox first = geometry.get(all.get(start).node).box;
            int end = start + 1;
            long bottom = (long) first.y + first.height;
            while (end < all.size() && geometry.get(all.get(end).node).box.y == first.y) {
                LayoutBox box = geometry.get(all.get(end).node).box;
                bottom = Math.max(bottom, (long) box.y + box.height); end++;
            }
            double distance = y < first.y ? (double) first.y - y : y > bottom ? y - (double) bottom : 0;
            if (distance < bestBand) { band = new ArrayList<>(all.subList(start, end)); bestBand = distance; }
            start = end;
        }
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
    boolean valid(SceneSortableModel.RowEntry<T, K, C> source, Position<T, K, C> position, SceneGeometryView geometry) {
        if (position == null || !containers(source, geometry).contains(position.container)) return false;
        if (position.before == null) return true;
        SceneSortableModel.RowEntry<T, K, C> before = model.rows.get(position.before);
        return before != null && before != source && before.container.get() == position.container && geometry.get(before.node) != null;
    }
    Step<T, K, C> step(SceneSortableModel.RowEntry<T, K, C> source, Position<T, K, C> current,
            SceneSortableModel.RowEntry<T, K, C> anchor, SceneGeometryView geometry, SceneEditEvent.Command direction) {
        List<SceneSortableModel.ContainerEntry<T, K, C>> legal = containers(source, geometry);
        SceneSortableModel.ContainerEntry<T, K, C> container = current == null ? source.container.get() : current.container;
        SceneGeometryView.Entry anchorEntry = geometry.get(source.node);
        LayoutBox origin = anchorEntry != null ? anchorEntry.box : geometry.get(container.declaration.root).box;
        boolean horizontal = direction == SceneEditEvent.Command.LEFT || direction == SceneEditEvent.Command.RIGHT;
        boolean positive = direction == SceneEditEvent.Command.RIGHT || direction == SceneEditEvent.Command.DOWN;
        SceneSortableModel.RowEntry<T, K, C> chosen = null;
        SceneSortableModel.ContainerEntry<T, K, C> chosenContainer = null;
        double best = Double.POSITIVE_INFINITY;
        for (SceneSortableModel.ContainerEntry<T, K, C> target : legal) {
            boolean localAxis = target.declaration.layout == SceneSortable.Layout.GRID
                    || horizontal == (target.declaration.layout == SceneSortable.Layout.HORIZONTAL);
            for (SceneSortableModel.RowEntry<T, K, C> row : rows(target, geometry, null)) {
                if (target == container && !localAxis) continue;
                if (row == source) continue;
                double score = directionScore(origin, geometry.get(row.node).box, horizontal, positive);
                if (score < best) { best = score; chosen = row; chosenContainer = target; }
            }
            if (target == container || !rows(target, geometry, source).isEmpty()) continue;
            double score = directionScore(origin, geometry.get(target.declaration.root).box, horizontal, positive);
            if (score < best) { best = score; chosen = null; chosenContainer = target; }
        }
        if (chosenContainer == null) return new Step<>(current, anchor);
        K before = null;
        if (chosen != null) {
            if (chosen == source) before = model.successor(source);
            else if (!positive) before = chosen.key;
            else {
                List<SceneSortableModel.RowEntry<T, K, C>> ordered = chosenContainer.displayed;
                int index = ordered.indexOf(chosen) + 1;
                while (index < ordered.size() && ordered.get(index) == source) index++;
                before = index < ordered.size() ? ordered.get(index).key : null;
            }
        }
        model.runtime.requestInputReveal(source.node);
        return new Step<>(new Position<>(chosenContainer, before), chosen);
    }
    private double directionScore(LayoutBox origin, LayoutBox box, boolean horizontal, boolean positive) {
        double dx = (double) SceneDragSupport.centerX(box) - SceneDragSupport.centerX(origin);
        double dy = (double) SceneDragSupport.centerY(box) - SceneDragSupport.centerY(origin);
        double primary = horizontal ? dx : dy, cross = horizontal ? dy : dx;
        if (positive ? primary <= 0 : primary >= 0) return Double.POSITIVE_INFINITY;
        return primary * primary + 4 * cross * cross;
    }
}
