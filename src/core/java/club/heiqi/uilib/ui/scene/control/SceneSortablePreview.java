package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneFlowLayout;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Collections;
import java.util.List;

/** 提起反馈只增加装饰阴影，内容尺寸、主题和绘制仍由原声明承担。 */
final class SceneSortablePreview implements SceneControl {
    private final ReadableSignal<Float> elevation;
    private SceneSortablePreview(ReadableSignal<Float> elevation) { this.elevation = elevation; }
    static SceneNode create(SceneNode content, ReadableSignal<Float> elevation) {
        return new SceneNode().size(Size.fill(1), Size.fill(1)).control(new SceneSortablePreview(elevation))
                .add(content.size(Size.fill(1), Size.fill(1)));
    }
    @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Collections.singletonList(elevation); }
    @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
    @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text, SceneChildMeasurer children) {
        return SceneFlowLayout.measure(node, body, children);
    }
    @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
        return SceneFlowLayout.arrange(node, box, children);
    }
    @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {
        SceneGeometryView.Entry entry = geometry.get(node);
        float lift = elevation.get();
        for (int ring = 6; ring >= 0; ring--) {
            int spread = Math.round((ring + 1) * lift), alpha = Math.round((ring == 0 ? 16 : 4) * lift);
            ScenePaintShapes.rounded(paint, new LayoutBox(entry.box.x - spread, entry.box.y + Math.round(8 * lift) - spread,
                    entry.box.width + spread * 2, entry.box.height + spread * 2), entry.clip, alpha << 24, 8 + spread);
        }
    }
}
