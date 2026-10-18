package club.heiqi.uilib.ui.scene.runtime;

import java.util.Objects;
import java.util.function.Supplier;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 消费一次主动显露意图，沿所属层边界由内到外移动唯一滚动位置。
 * 核心规格第 6.2 节：普通重排不追焦；内层移动后先重排，外层再消费新几何。 */
final class SceneNavigationReveal {
    private final SceneRuntime runtime;
    private MountHandle pending;

    SceneNavigationReveal(SceneRuntime runtime) { this.runtime = runtime; }

    void request(MountHandle target) { pending = target; }
    void clear() { pending = null; }

    SceneGeometry reveal(SceneGeometry geometry, Supplier<SceneGeometry> relayout) {
        MountHandle focused = runtime.focusRouter().takeReveal();
        MountHandle target = focused == null ? pending : focused;
        pending = null;
        if (!active(target)) return geometry;
        if (prepareTarget(target.node(), geometry)) geometry = Objects.requireNonNull(relayout.get(), "geometry");
        SceneNode boundary = geometry.layerBoundary(target.node());
        for (SceneNode ancestor = parentWithin(target.node(), boundary); ancestor != null;
                ancestor = parentWithin(ancestor, boundary)) {
            if (focused != null ? !runtime.focusRouter().owns(target) : !active(target)) break;
            if (ancestor.scrollable() && revealIn(ancestor, target.node(), geometry))
                geometry = Objects.requireNonNull(relayout.get(), "geometry");
        }
        return geometry;
    }

    private boolean active(MountHandle target) {
        return target != null && target.active() && runtime.interactive(target.node());
    }

    private boolean prepareTarget(SceneNode node, SceneGeometry geometry) {
        if (node.control() == null || !node.control().prepareNavigation(node, geometry)) return false;
        runtime.invalidateGeometry();
        return true;
    }

    private static SceneNode parentWithin(SceneNode node, SceneNode boundary) {
        return node == boundary ? null : node.parent();
    }

    private boolean revealIn(SceneNode ancestor, SceneNode target, SceneGeometry geometry) {
        SceneGeometryView.Entry entry = geometry.get(ancestor);
        LayoutBox region = runtime.domain().readOnly(() -> SceneGeometry.revealRegion(target, ancestor, geometry));
        if (entry == null || empty(region)) return false;
        LayoutBox window = SceneGeometry.revealWindow(ancestor, geometry);
        if (window.width == 0 || window.height == 0) return false;
        boolean row = ancestor.axis() == SceneNode.Axis.ROW;
        long delta = SceneGeometry.revealDelta(row ? region.x : region.y, row ? region.width : region.height,
                row ? window.x : window.y, row ? window.width : window.height);
        return runtime.revealScrollPosition(ancestor, entry, delta);
    }

    private static boolean empty(LayoutBox box) { return box == null || box.width == 0 || box.height == 0; }
}
