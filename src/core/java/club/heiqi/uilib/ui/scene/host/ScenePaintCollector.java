package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 消费已准备几何，收集控件与备用内容；输入结算、布局发布及呈现代际仍归帧管线。 */
final class ScenePaintCollector {
    private final SceneLayoutEngine layout;
    private final SceneTextMeasurer text;
    private long nextPaintNanos=Long.MAX_VALUE;
    long nextPaintNanos() { return nextPaintNanos; }

    ScenePaintCollector(SceneLayoutEngine layout, SceneTextMeasurer text) {
        this.layout = layout;
        this.text = text;
    }

    PaintPlan collect(SceneGeometryView geometry, long metricsRevision) {
        nextPaintNanos=Long.MAX_VALUE;
        PaintPlanBuilder content = new PaintPlanBuilder(), overlay = new PaintPlanBuilder();
        // 捕获标记覆盖整页所有组，避免备用modal后代在后续组无条件再绘制。
        Set<SceneNode> captured = identitySet();
        for (SceneGeometryView.Group group : geometry.groups()) {
            PaintLayers layers = collectEntries(group.entries, geometry, metricsRevision, captured);
            append(content, layers.content);
            append(overlay, layers.overlay);
        }
        // 控件内容保持树序；overlay在全页内容之后保持原overlay树序。
        append(content, overlay.build());
        return content.build();
    }

    private static final class PaintLayers {
        final PaintPlan content, overlay;

        PaintLayers(PaintPlan content, PaintPlan overlay) {
            this.content = content;
            this.overlay = overlay;
        }
    }

    private PaintLayers collectEntries(
            List<SceneGeometryView.Entry> ordered, SceneGeometryView geometry,
            long metricsRevision, Set<SceneNode> captured) {
        PaintPlanBuilder content = new PaintPlanBuilder(), overlay = new PaintPlanBuilder();
        Map<SceneNode, PaintPlan> childOverlays = new IdentityHashMap<>();
        ArrayDeque<SceneNode> open = new ArrayDeque<>();
        for (SceneGeometryView.Entry entry : ordered) {
            if (insideCaptured(entry.node, captured)) continue;
            // 只消费已定位条目；按祖先关系关闭内容栈，不重走children补出幻影。
            while (!open.isEmpty() && !inSubtree(entry.node, open.peek()))
                paintAfterChildren(open.pop(), geometry, content);
            open.push(entry.node);
            if (entry.node.control() == null) {
                paintUncontrolled(entry, content);
                continue;
            }
            entry.node.control().preparePaint(entry.node, geometry, text, metricsRevision);
            PaintLayers fallback = captureFallback(entry, geometry, metricsRevision, captured);
            PaintPlanBuilder own = new PaintPlanBuilder();
            entry.node.control().paint(entry.node, geometry, own,
                    fallback == null ? null : fallback.content);
            PaintPlan ownPlan = own.build();
            append(content, ownPlan);
            if (!entry.node.control().paintChildren(entry.node))
                for (SceneNode child : entry.node.children()) captured.add(child);
            if (fallback != null && !fallback.overlay.commands().isEmpty())
                childOverlays.put(entry.node, branchOverlay(ownPlan, fallback));
        }
        while (!open.isEmpty()) paintAfterChildren(open.pop(), geometry, content);
        for (SceneGeometryView.Entry entry : ordered) {
            if (insideCaptured(entry.node, captured) || entry.node.control() == null) continue;
            entry.node.control().paintOverlay(entry.node, geometry, overlay, text);
            PaintPlan childOverlay = childOverlays.get(entry.node);
            if (childOverlay != null) append(overlay, childOverlay);
            LayoutBox visible=entry.clip==null?entry.box:entry.box.intersect(entry.clip);
            if(visible.width>0&&visible.height>0)nextPaintNanos=Math.min(nextPaintNanos,entry.node.control().nextPaintNanos(entry.node));
        }
        return new PaintLayers(content.build(), overlay.build());
    }

    private PaintLayers captureFallback(
            SceneGeometryView.Entry entry, SceneGeometryView geometry,
            long metricsRevision, Set<SceneNode> captured) {
        SceneNode child = entry.node.control().paintFallbackChild(entry.node);
        if (child == null) return null;
        if (child.parent() != entry.node)
            throw new IllegalArgumentException("占位必须是控件直属逻辑子树");
        SceneLayoutEngine.rejectContentLayers(entry.node, child, "image fallback");
        captured.add(child);
        SceneGeometryView context = geometry;
        List<SceneGeometryView.Entry> source = geometry.sourceOrdered();
        if (geometry.get(child) == null) {
            SceneGeometry local = layout.captureSubtree(child, entry.box, entry.clip, geometry.viewport());
            source = local.ordered();
            context = fallbackGeometry(local, geometry);
        }
        // 可见备用子树可能横跨modal组，沿当批来源顺序完整闭合内容栈。
        List<SceneGeometryView.Entry> entries = new ArrayList<>();
        for (SceneGeometryView.Entry candidate : source)
            if (inSubtree(candidate.node, child)) entries.add(candidate);
        // 每次备用捕获独立消费子树，不继承外层已标记的captured根。
        return collectEntries(entries, context, metricsRevision, identitySet());
    }

    private static SceneGeometryView fallbackGeometry(
            SceneGeometry local, SceneGeometryView geometry) {
        // get提供备用区域，ordered仍为主输入事实，不能以备用子集重新裁决hover。
        return new SceneGeometryView() {
            @Override
            public Entry get(SceneNode node) {
                Entry value = local.get(node);
                return value == null ? geometry.get(node) : value;
            }

            @Override
            public List<Entry> ordered() { return geometry.ordered(); }

            @Override
            public List<Entry> sourceOrdered() { return local.sourceOrdered(); }

            @Override
            public LayoutBox viewport() { return geometry.viewport(); }
        };
    }

    private static PaintPlan branchOverlay(PaintPlan own, PaintLayers fallback) {
        for (PaintCommand command : own.commands())
            if (command.kind == PaintCommand.Kind.IMAGE
                    && command.pendingPaint == fallback.content
                    && command.failedPaint == fallback.content)
                return new PaintPlanBuilder().add(PaintCommand.imageBranchOverlay(
                        command, fallback.overlay, fallback.overlay)).build();
        // CPU没有内容时控件已无条件选择备用；有IMAGE时只投影同一次原图选择。
        return fallback.overlay;
    }

    private static void paintUncontrolled(SceneGeometryView.Entry entry, PaintPlanBuilder target) {
        if ((entry.node.background() >>> 24) != 0)
            target.add(PaintCommand.rect(entry.box, entry.clip, entry.node.background()));
        if (entry.text.lines().isEmpty()) return;
        int padding = entry.node.padding();
        target.add(PaintCommand.text(new LayoutBox(
                entry.box.x + padding, entry.box.y + padding,
                Math.max(0, entry.box.width - padding * 2),
                Math.max(0, entry.box.height - padding * 2)), entry.clip, entry.text));
    }

    private static void paintAfterChildren(
            SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
        if (node.control() != null) node.control().paintAfterChildren(node, geometry, target);
    }

    private static void append(PaintPlanBuilder target, PaintPlan plan) {
        for (PaintCommand command : plan.commands()) target.add(command);
    }

    private static Set<SceneNode> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
    }

    private static boolean insideCaptured(SceneNode node, Set<SceneNode> captured) {
        if (captured.isEmpty()) return false;
        for (SceneNode current = node; current != null; current = current.parent())
            if (captured.contains(current)) return true;
        return false;
    }

    private static boolean inSubtree(SceneNode node, SceneNode root) {
        for (SceneNode current = node; current != null; current = current.parent())
            if (current == root) return true;
        return false;
    }
}
