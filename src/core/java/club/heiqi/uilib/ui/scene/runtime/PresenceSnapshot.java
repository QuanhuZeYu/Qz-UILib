package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import java.util.function.ToDoubleFunction;

/** 退场片段的来源空间与祖先事实；只投影被动画面，不持有输入或活跃身份。 */
final class PresenceSnapshot {
    private final PresencePaint paint;
    private final SceneNode parent;
    private final LayoutBox parentBox;
    private final int scroll;

    PresenceSnapshot(SceneRuntime runtime, SceneNode node, PresencePaint paint, SceneGeometryView geometry) {
        this.paint = paint; parent = node.parent();
        SceneGeometryView.Entry entry = parent == null ? null : geometry.get(parent);
        parentBox = entry == null ? null : entry.box;
        scroll = entry == null ? 0 : entry.scrollOffset - runtime.scrollPresentationOffset(parent);
    }
    // 几何基准来自捕获时刻，资格与资源归属沿持续挂载的来源父链，明确移动不能冻结旧祖先。
    boolean descendedFrom(SceneNode node) {
        for (SceneNode ancestor = parent; ancestor != null; ancestor = ancestor.parent())
            if (ancestor == node) return true;
        return false;
    }
    boolean available(SceneRuntime runtime) {
        for (SceneNode ancestor = parent; ancestor != null; ancestor = ancestor.parent())
            if (ancestor.runtime() != runtime || !ancestor.visible() || ancestor.destroyed()) return false;
        return true;
    }
    void append(SceneRuntime runtime, PaintPlanBuilder target, double factor, SceneGeometryView geometry,
                ToDoubleFunction<SceneNode> opacityOf) {
        if (!available(runtime)) return;
        float opacity = (float) Math.max(0, Math.min(1, factor));
        LayoutBox viewport = geometry.viewport();
        if (parent == null) { appendParts(target, 0, 0, opacity, viewport, viewport); return; }
        SceneGeometryView.Entry current = geometry.get(parent);
        if (current == null || parentBox == null) return;
        int dx = current.box.x - parentBox.x + scrollShift(runtime, current, SceneNode.Axis.ROW);
        int dy = current.box.y - parentBox.y + scrollShift(runtime, current, SceneNode.Axis.COLUMN);
        LayoutBox clip = current.clip == null ? viewport : viewport.intersect(current.clip);
        appendParts(target, dx, dy, inheritedOpacity(opacity, opacityOf), clip, viewport);
    }
    private int scrollShift(SceneRuntime runtime, SceneGeometryView.Entry current, SceneNode.Axis axis) {
        // 与正式子排列使用同一有效偏移：合法 scroll 减去暂态回弹，不能冻结捕获时的形变。
        return parent.scrollable() && parent.axis() == axis
                ? scroll - (current.scrollOffset - runtime.scrollPresentationOffset(parent)) : 0;
    }
    private float inheritedOpacity(float opacity, ToDoubleFunction<SceneNode> opacityOf) {
        for (SceneNode ancestor = parent; ancestor != null; ancestor = ancestor.parent()) opacity *= ancestor.opacity() * (float) opacityOf.applyAsDouble(ancestor);
        return opacity;
    }
    private void appendParts(PaintPlanBuilder target, int dx, int dy, float opacity, LayoutBox clip, LayoutBox viewport) {
        PaintPlan.Presentation ordinary = PaintPlan.presentation(dx, dy, opacity, clip);
        PaintPlan.Presentation escaped = PaintPlan.presentation(dx, dy, opacity, viewport);
        for (PresencePaint.Part part : paint.parts) {
            PaintPlan.Presentation projection = part.viewportClipped ? escaped : ordinary;
            for (PaintCommand command : projection.apply(part.paint).commands()) target.add(command);
        }
    }
}
