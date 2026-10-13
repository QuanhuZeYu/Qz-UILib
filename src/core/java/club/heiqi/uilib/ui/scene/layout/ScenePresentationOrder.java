package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.scene.control.SceneLayer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 当批布局结果的临时分组器；发布后仅 SceneGeometry 持有呈现事实。 */
final class ScenePresentationOrder {
    private static final class Part {
        final SceneNode root, layer, scope;
        final SceneLayer.Policy policy;
        final List<SceneGeometryView.Entry> entries = new ArrayList<>();
        Part(SceneNode root, SceneNode layer, SceneNode scope, SceneLayer.Policy policy) {
            this.root = root; this.layer = layer; this.scope = scope; this.policy = policy;
        }
    }
    static SceneGeometry publish(List<SceneNode> roots, List<SceneGeometryView.Entry> entries,
            LayoutBox viewport, Map<SceneNode, Boolean> sources) {
        Map<SceneNode, SceneGeometryView.Entry> index = new IdentityHashMap<>();
        for (SceneGeometryView.Entry entry : entries)
            if (index.put(entry.node, entry) != null) throw new IllegalArgumentException("同批排列重复: node=" + entry.node.id());
        // 选择与分组共用当批有效 modal；裸声明失活后仍按普通内容绘制。
        List<SceneNode> modalOrder = new ArrayList<>();
        for (SceneNode root : roots) collectModals(root, index, false, modalOrder);
        Set<SceneNode> modals = Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
        modals.addAll(modalOrder);
        Map<SceneNode, Integer> scopeRanks = new IdentityHashMap<>();
        scopeRanks.put(null, 0);
        for (int i = 0; i < modalOrder.size(); i++) scopeRanks.put(modalOrder.get(i), i + 1);
        SceneNode modal = modalOrder.isEmpty() ? null : modalOrder.get(modalOrder.size() - 1);
        List<Part> parts = new ArrayList<>();
        partition(entries, parts, modals);
        final SceneNode active = modal;
        parts.removeIf(part -> part.layer != null && active != null
                && !inside(part.layer, active) && !inside(active, part.layer));
        // 绘制必须与有效 modal 选择同序，不同根分支的深度不能让后台范围盖住当前范围。
        // 当批声明序只排完整子树组；组内 placements 与 child/afterChildren 关系保持原样。
        parts.sort(Comparator.comparingInt((Part part) -> scopeRanks.get(part.scope))
                .thenComparingInt(part -> part.layer == null ? 0 : 1));
        List<SceneGeometryView.Group> groups = new ArrayList<>();
        for (Part part : parts) if (!part.entries.isEmpty()) groups.add(new SceneGeometryView.Group(
                part.root, part.layer, part.scope, part.policy, part.entries));
        return new SceneGeometry(groups, viewport, sources, modal, entries);
    }
    private static void collectModals(SceneNode node, Map<SceneNode, SceneGeometryView.Entry> index,
            boolean passive, List<SceneNode> modals) {
        if (!index.containsKey(node) || !node.visible() || !node.enabled() || node.destroyed()) return;
        SceneLayer.Policy policy = policy(node);
        passive |= policy != null && policy.participation == SceneLayer.Participation.PASSIVE;
        if (passive && node.modal()) throw new IllegalArgumentException("PASSIVE 所属层不能嵌套 modal: node=" + node.id());
        if (node.modal()) modals.add(node);
        for (SceneNode child : node.children()) collectModals(child, index, passive, modals);
    }
    private static void partition(List<SceneGeometryView.Entry> entries, List<Part> parts, Set<SceneNode> modals) {
        Map<SceneNode, Part> groups = new IdentityHashMap<>();
        for (SceneGeometryView.Entry entry : entries) {
            SceneNode boundary = null, layer = null, scope = null, root = entry.node;
            for (SceneNode cursor = entry.node; cursor != null; cursor = cursor.parent()) {
                root = cursor;
                SceneLayer.Policy own = policy(cursor);
                if (layer == null && own != null) layer = cursor;
                if (scope == null && modals.contains(cursor)) scope = cursor;
                if (boundary == null && (own != null || modals.contains(cursor))) boundary = cursor;
            }
            if (boundary == null) boundary = root;
            Part part = groups.get(boundary);
            if (part == null) {
                part = new Part(boundary, layer, scope, layer == null ? null : policy(layer));
                groups.put(boundary, part); parts.add(part);
            }
            // 内容投影的排列顺序来自 engine 的 placements，不能由逻辑 children 重建。
            part.entries.add(entry);
        }
    }
    private static SceneLayer.Policy policy(SceneNode node) { return node.control() == null ? null : node.control().layerPolicy(node); }
    private static boolean inside(SceneNode node, SceneNode root) {
        for (SceneNode cursor = node; cursor != null; cursor = cursor.parent()) if (cursor == root) return true;
        return false;
    }
}
