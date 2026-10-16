package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.reactive.Signal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 先校验整个输入与全部新 factory 结果，再允许 Controller 改动存续树；异常沿正常清理暴露。 */
final class SceneSortableDeclarations {
    private SceneSortableDeclarations() {}
    static final class Plan<T, K, C> {
        final Map<C, SceneSortableModel.ContainerEntry<T, K, C>> containers = new LinkedHashMap<>();
        final Map<K, SceneSortableModel.RowEntry<T, K, C>> rows = new LinkedHashMap<>();
        final Map<K, T> values = new LinkedHashMap<>();
        final Map<C, List<SceneSortableModel.RowEntry<T, K, C>>> order = new LinkedHashMap<>();
        final List<SceneNode> created = new ArrayList<>();
        void releaseUnattached(Throwable failure) {
            for (SceneNode node : created) {
                if (node.destroyed() || node.runtime() != null || node.parent() != null) continue;
                try { node.destroy(); }
                catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            }
        }
    }
    static <T, K, C> Plan<T, K, C> prepare(SceneSortableModel<T, K, C> model, List<SceneSortable.Group<C, T>> groups) {
        Plan<T, K, C> plan = new Plan<>();
        List<SceneSortable.Group<C, T>> snapshot = new ArrayList<>(Objects.requireNonNull(groups, "groups value"));
        Map<C, List<K>> identities = validate(model, snapshot, plan.values);
        Set<SceneNode> nodes = Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
        try {
            declareContainers(model, snapshot, plan, nodes);
            List<SceneSortableModel.RowEntry<T, K, C>> freshRows = declareRows(model, identities, plan, nodes);
            bindDeclarations(model, plan, freshRows);
            return plan;
        } catch (RuntimeException | Error failure) {
            plan.releaseUnattached(failure);
            throw failure;
        }
    }
    private static <T, K, C> void declareContainers(SceneSortableModel<T, K, C> model,
            List<SceneSortable.Group<C, T>> groups, Plan<T, K, C> plan, Set<SceneNode> nodes) {
        for (SceneSortable.Group<C, T> group : groups) {
            SceneSortableModel.ContainerEntry<T, K, C> container = model.containers.get(group.key);
            if (container == null) {
                SceneSortable.Container declaration = Objects.requireNonNull(model.containerFactory.apply(group.key), "container: " + group.key);
                fresh(declaration.root, "container " + group.key);
                plan.created.add(declaration.root);
                tree(declaration.root, nodes, "container " + group.key);
                if (!SceneDragSupport.within(declaration.items, declaration.root) || !declaration.items.children().isEmpty())
                    throw new IllegalArgumentException("排序 items 必须是 root 内的空子树: " + group.key);
                // items 的孩子声明由排序独占；双重拥有沿 runtime 正式诊断，不猜测 binding 实现类型。
                container = new SceneSortableModel.ContainerEntry<>(group.key, declaration);
            }
            plan.containers.put(group.key, container);
        }
    }
    private static <T, K, C> List<SceneSortableModel.RowEntry<T, K, C>> declareRows(SceneSortableModel<T, K, C> model,
            Map<C, List<K>> identities, Plan<T, K, C> plan, Set<SceneNode> nodes) {
        List<SceneSortableModel.RowEntry<T, K, C>> freshRows = new ArrayList<>();
        for (Map.Entry<C, List<K>> group : identities.entrySet()) {
            SceneSortableModel.ContainerEntry<T, K, C> container = plan.containers.get(group.getKey());
            List<SceneSortableModel.RowEntry<T, K, C>> ordered = new ArrayList<>();
            for (K key : group.getValue()) {
                SceneSortableModel.RowEntry<T, K, C> row = model.rows.get(key);
                if (row == null) {
                    row = declareRow(model, key, container, plan, nodes);
                    freshRows.add(row);
                }
                plan.rows.put(key, row); ordered.add(row);
            }
            plan.order.put(group.getKey(), ordered);
        }
        return freshRows;
    }
    private static <T, K, C> SceneSortableModel.RowEntry<T, K, C> declareRow(SceneSortableModel<T, K, C> model,
            K key, SceneSortableModel.ContainerEntry<T, K, C> container, Plan<T, K, C> plan, Set<SceneNode> nodes) {
        Signal<T> cell = model.context.domain().signal(plan.values.get(key));
        SceneSortable.Row declaration = Objects.requireNonNull(model.rowFactory.apply(cell), "row: " + key);
        fresh(declaration.content, "row " + key);
        plan.created.add(declaration.content);
        tree(declaration.content, nodes, "row " + key);
        Set<SceneNode> handles = Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
        for (SceneNode handle : declaration.handles)
            if (!SceneDragSupport.within(handle, declaration.content) || !handles.add(handle))
                throw new IllegalArgumentException("排序把手必须是当前行内的唯一节点: " + key + " / " + handle.id());
        return new SceneSortableModel.RowEntry<>(model.context.domain(), key, cell, model.context.domain().signal(container), declaration);
    }
    private static <T, K, C> void bindDeclarations(SceneSortableModel<T, K, C> model, Plan<T, K, C> plan,
            List<SceneSortableModel.RowEntry<T, K, C>> freshRows) {
        // 全体 factory 通过校验后才包装与登记声明；现有行在此仍完全不变。
        for (SceneSortableModel.ContainerEntry<T, K, C> container : plan.containers.values())
            if (!model.containers.containsKey(container.key))
                model.bindTarget(container.declaration.root, new SceneSortableKeys.ContainerTarget<>(container.key), source -> model.accepts(container, source));
        for (SceneSortableModel.RowEntry<T, K, C> row : freshRows) {
            model.buildRow(row);
            plan.created.add(row.node);
        }
    }
    private static <T, K, C> Map<C, List<K>> validate(SceneSortableModel<T, K, C> model,
            List<SceneSortable.Group<C, T>> groups, Map<K, T> values) {
        Map<C, List<K>> result = new LinkedHashMap<>();
        Set<K> keys = new HashSet<>();
        for (SceneSortable.Group<C, T> group : groups) {
            Objects.requireNonNull(group, "group"); Objects.requireNonNull(group.key, "group key");
            if (result.containsKey(group.key)) throw new IllegalArgumentException("排序 group 键重复: " + group.key);
            List<K> ordered = new ArrayList<>();
            for (T value : group.items) {
                Objects.requireNonNull(value, "item: " + group.key);
                K key = Objects.requireNonNull(model.key.apply(value), "sortable key: " + group.key);
                if (!keys.add(key)) throw new IllegalArgumentException("排序 scope 条目键重复: group=" + group.key + ", key=" + key);
                values.put(key, value); ordered.add(key);
            }
            result.put(group.key, ordered);
        }
        return result;
    }
    static void fresh(SceneNode node, String role) {
        if (node == null || node.destroyed() || node.runtime() != null || node.parent() != null || node.logicalOwner() != null)
            throw new IllegalArgumentException("排序 factory 返回不可拥有的节点: " + role + " / " + (node == null ? "null" : node.id()));
    }
    private static void tree(SceneNode node, Set<SceneNode> seen, String role) {
        if (node.destroyed() || node.runtime() != null || !seen.add(node))
            throw new IllegalArgumentException("排序 factory 子树身份冲突: " + role + " / " + node.id());
        for (SceneNode child : node.children()) {
            if (child.parent() != node) throw new IllegalArgumentException("排序 factory 子树归属冲突: " + role + " / " + child.id());
            tree(child, seen, role);
        }
    }
}
