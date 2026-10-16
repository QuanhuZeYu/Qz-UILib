package club.heiqi.uilib.ui.scene.control;

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
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Collections;

/** 普通 Flow board 和被动条目反馈；有限运动只在正式输入帧推进。 */
final class SceneSortablePresentation<T, K, C> implements SceneControl {
    private final SceneSortableModel<T, K, C> model;
    SceneSortablePresentation(SceneSortableModel<T, K, C> model) { this.model = model; }
    static <T, K, C> SceneNode layer(SceneSortableModel<T, K, C> model) {
        SceneNode layer = SceneLayer.create(model.context, () -> new SceneLayer.Policy(0, SceneLayer.Participation.PASSIVE),
                (node, source, viewport, measured) -> {
                    SceneSortableInteraction.Feedback<T, K, C> value = model.motion.preview.get();
                    LayoutBox box = value == null ? source : value.box;
                    return new LayoutBox(SceneDragSupport.clamp(box.x, measured.width, viewport.x, viewport.width),
                            SceneDragSupport.clamp(box.y, measured.height, viewport.y, viewport.height), measured.width, measured.height);
                }, Collections.singletonList(model.motion.preview));
        layer.bind(NodeBinding.value(() -> model.motion.preview.get() != null, SceneNode::visible));
        layer.bind(NodeBinding.value(model.motion.preview, (node, value) -> {
            int width = value == null ? 0 : value.box.width, height = value == null ? 0 : value.box.height;
            if (node.width().mode != Size.Mode.FIXED || node.width().value != width || node.height().value != height)
                node.size(Size.fixed(width), Size.fixed(height));
        }));
        layer.bind(NodeBinding.repeat(() -> {
            SceneSortableInteraction.Feedback<T, K, C> value = model.motion.preview.get();
            return value == null ? Collections.emptyList() : Collections.singletonList(value);
        }, value -> value.token, cell -> {
            SceneSortableInteraction.Feedback<T, K, C> value = cell.get();
            SceneNode preview = model.previewFactory.apply(value.row.value);
            SceneSortableDeclarations.fresh(preview, "preview " + value.row.key);
            return SceneSortablePreview.create(preview, model.motion.elevation);
        }));
        return layer;
    }
    @Override public long nextInputNanos(SceneNode node) {
        return model.interaction.feedback.get() != null || model.motion.busy.get() ? 0 : Long.MAX_VALUE;
    }
    @Override public void inputFrame(SceneNode node, SceneGeometryView geometry, long now) { model.motion.advance(geometry, now); }
    @Override public void inputGeometryChanged(SceneNode node, SceneGeometryView geometry) { model.motion.synchronize(geometry); }
    @Override public void inputFrameCancelled(SceneNode node) { model.interaction.clear(); model.motion.clear(); }
    @Override public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
    @Override public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text, SceneChildMeasurer children) {
        return SceneFlowLayout.measure(node, constraints, children);
    }
    @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
        return SceneFlowLayout.arrange(node, box, children);
    }
    @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {}
}
