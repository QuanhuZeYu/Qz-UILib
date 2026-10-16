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
        if (revision == next && row == source && (target == position || target != null && target.same(position))) return;
        revision = next; source = row; position = target;
        Map<SceneSortableModel.ContainerEntry<T, K, C>, List<SceneSortableModel.RowEntry<T, K, C>>> order = new LinkedHashMap<>();
        for (SceneSortableModel.ContainerEntry<T, K, C> container : model.containers.values())
            order.put(container, new ArrayList<>(container.rows));
        if (row != null && model.rows.get(row.key) == row && target != null && order.containsKey(target.container)) {
            List<SceneSortableModel.RowEntry<T, K, C>> destination = order.get(target.container);
            SceneSortableModel.RowEntry<T, K, C> before = model.rows.get(target.before);
            if (target.before == null || destination.contains(before)) {
                for (List<SceneSortableModel.RowEntry<T, K, C>> rows : order.values()) rows.remove(row);
                destination.add(before == null ? destination.size() : destination.indexOf(before), row);
            }
        }
        for (Map.Entry<SceneSortableModel.ContainerEntry<T, K, C>, List<SceneSortableModel.RowEntry<T, K, C>>> entry : order.entrySet()) {
            for (SceneSortableModel.RowEntry<T, K, C> item : entry.getValue()) {
                item.placed.set(entry.getKey());
                SceneNode parent = entry.getKey().declaration.items;
                if (item.node.parent() != parent) model.runtime.relocate(item.node, parent);
            }
        }
        for (Map.Entry<SceneSortableModel.ContainerEntry<T, K, C>, List<SceneSortableModel.RowEntry<T, K, C>>> entry : order.entrySet()) {
            List<SceneNode> children = new ArrayList<>();
            for (SceneSortableModel.RowEntry<T, K, C> item : entry.getValue()) children.add(item.node);
            model.runtime.reconcile(entry.getKey().declaration.items, children, entry.getKey().declaration.items, true);
            entry.getKey().displayed = entry.getValue();
        }
    }
}
