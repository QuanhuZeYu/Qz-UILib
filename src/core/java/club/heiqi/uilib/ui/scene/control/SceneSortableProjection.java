package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 临时展示序与权威集合分开；同一轮先完成全部迁移，才收束各容器孩子。 */
final class SceneSortableProjection<T, K, C> {
    private final SceneSortableModel<T, K, C> model;
    private SceneSortableModel.RowEntry<T, K, C> source;
    private SceneSortableSelection.Position<T, K, C> position;
    private long revision = -1;
    SceneSortableProjection(SceneSortableModel<T, K, C> model) { this.model = model; }

    void apply(SceneSortableInteraction.Feedback<T, K, C> feedback) {
        long next = model.revision.get();
        SceneSortableModel.RowEntry<T, K, C> row = feedback == null ? null : feedback.row;
        SceneSortableSelection.Position<T, K, C> target = feedback == null ? null : feedback.position;
        if (unchanged(next, row, target)) return;
        revision = next; source = row; position = target;
        DisplayOrder order = new DisplayOrder();
        order.insertSource(row, target);
        order.relocateAll();
        order.reconcileContainers();
    }

    private boolean unchanged(long next, SceneSortableModel.RowEntry<T, K, C> row,
                              SceneSortableSelection.Position<T, K, C> target) {
        return revision == next && row == source && (target == position || target != null && target.same(position));
    }

    /** 一次投影的完整顺序；不把临时顺序写回权威 rows 或 container。 */
    private final class DisplayOrder {
        private final Map<SceneSortableModel.ContainerEntry<T, K, C>, List<SceneSortableModel.RowEntry<T, K, C>>> containers = new LinkedHashMap<>();
        DisplayOrder() {
            for (SceneSortableModel.ContainerEntry<T, K, C> container : model.containers.values())
                containers.put(container, new ArrayList<>(container.rows));
        }
        void insertSource(SceneSortableModel.RowEntry<T, K, C> row, SceneSortableSelection.Position<T, K, C> target) {
            if (row == null || model.rows.get(row.key) != row || target == null) return;
            List<SceneSortableModel.RowEntry<T, K, C>> destination = containers.get(target.container);
            if (destination == null) return;
            SceneSortableModel.RowEntry<T, K, C> before = model.rows.get(target.before);
            if (target.before != null && !destination.contains(before)) return;
            for (List<SceneSortableModel.RowEntry<T, K, C>> rows : containers.values()) rows.remove(row);
            destination.add(before == null ? destination.size() : destination.indexOf(before), row);
        }
        void relocateAll() {
            for (Map.Entry<SceneSortableModel.ContainerEntry<T, K, C>, List<SceneSortableModel.RowEntry<T, K, C>>> entry : containers.entrySet())
                relocateRows(entry.getKey(), entry.getValue());
        }
        private void relocateRows(SceneSortableModel.ContainerEntry<T, K, C> container, List<SceneSortableModel.RowEntry<T, K, C>> rows) {
            SceneNode parent = container.declaration.items;
            for (SceneSortableModel.RowEntry<T, K, C> row : rows) {
                row.placed.set(container);
                if (row.node.parent() != parent) model.runtime.relocate(row.node, parent);
            }
        }
        void reconcileContainers() {
            for (Map.Entry<SceneSortableModel.ContainerEntry<T, K, C>, List<SceneSortableModel.RowEntry<T, K, C>>> entry : containers.entrySet()) {
                SceneNode parent = entry.getKey().declaration.items;
                List<SceneNode> children = new ArrayList<>();
                for (SceneSortableModel.RowEntry<T, K, C> row : entry.getValue()) children.add(row.node);
                model.runtime.reconcile(parent, children, parent, true);
                entry.getKey().displayed = entry.getValue();
            }
        }
    }
}
