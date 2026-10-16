package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneFlowLayout;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 插槽参与正式排列，内容沿相同 ChildPlacements 让位；来源保持挂载及命中，只隐藏重复绘制。 */
final class SceneSortableRow<T, K, C> implements SceneControl {
    private final SceneSortableModel<T, K, C> model;
    private final SceneSortableModel.RowEntry<T, K, C> row;
    SceneSortableRow(SceneSortableModel<T, K, C> model, SceneSortableModel.RowEntry<T, K, C> row) { this.model = model; this.row = row; }
    @Override public List<ReadableSignal<?>> arrangementDependencies(SceneNode node) { return Arrays.asList(row.offset); }
    @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Arrays.asList(model.motion.preview); }
    @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
    @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text, SceneChildMeasurer children) {
        return SceneFlowLayout.measure(node, body, children);
    }
    @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
        SceneSortableMotion.Offset shift = row.offset.get();
        List<ChildPlacements.Entry> result = new ArrayList<>();
        for (ChildPlacements.Entry entry : SceneFlowLayout.arrange(node, box, children).entries()) {
            LayoutBox at = entry.relativeBox;
            result.add(new ChildPlacements.Entry(entry.child, new LayoutBox(SceneDragSupport.integer((long) at.x + shift.x),
                    SceneDragSupport.integer((long) at.y + shift.y), at.width, at.height)));
        }
        return new ChildPlacements(result);
    }
    @Override public boolean paintChildren(SceneNode node) { return !model.motion.ghost(row); }
    @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {}
}
