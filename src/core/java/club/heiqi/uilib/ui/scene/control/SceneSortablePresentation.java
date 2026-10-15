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
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Collections;
import java.util.List;

/** 普通 Flow board 与被动预览、候选线；不处理输入，不在绘制中改变结构。 */
final class SceneSortablePresentation<T, K, C> implements SceneControl {
    private final SceneSortableModel<T, K, C> model;
    SceneSortablePresentation(SceneSortableModel<T, K, C> model) { this.model = model; }
    static <T, K, C> SceneNode layer(SceneSortableModel<T, K, C> model) {
        SceneNode layer = SceneLayer.create(model.context, () -> new SceneLayer.Policy(0, SceneLayer.Participation.PASSIVE),
                (node, source, viewport, measured) -> {
                    SceneSortableInteraction.Feedback<T, K, C> value = model.interaction.feedback.get();
                    LayoutBox box = value == null ? source : value.box;
                    return new LayoutBox(SceneDragSupport.clamp(box.x, measured.width, viewport.x, viewport.width),
                            SceneDragSupport.clamp(box.y, measured.height, viewport.y, viewport.height), measured.width, measured.height);
                }, Collections.singletonList(model.interaction.feedback));
        layer.bind(NodeBinding.value(() -> model.interaction.feedback.get() != null, SceneNode::visible));
        layer.bind(NodeBinding.value(() -> {
            SceneSortableInteraction.Feedback<T, K, C> value = model.interaction.feedback.get();
            return value == null ? 0 : value.box.width;
        }, (node, width) -> {
            if (node.width().mode != Size.Mode.FIXED || node.width().value != width) node.size(Size.fixed(width), Size.natural());
        }));
        layer.bind(NodeBinding.repeat(() -> {
            SceneSortableInteraction.Feedback<T, K, C> value = model.interaction.feedback.get();
            return value == null ? Collections.emptyList() : Collections.singletonList(value);
        }, value -> value.token, cell -> {
            SceneSortableInteraction.Feedback<T, K, C> value = cell.get();
            SceneNode preview = model.previewFactory.apply(value.row.value);
            SceneSortableDeclarations.fresh(preview, "preview " + value.row.key);
            return preview;
        }));
        return layer;
    }
    @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Collections.singletonList(model.interaction.feedback); }
    @Override public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
    @Override public MeasuredSize measure(SceneNode node, Constraints constraints, SceneTextMeasurer text, SceneChildMeasurer children) {
        return SceneFlowLayout.measure(node, constraints, children);
    }
    @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
        return SceneFlowLayout.arrange(node, box, children);
    }
    @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {}
    @Override public void paintAfterChildren(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
        SceneSortableInteraction.Feedback<T, K, C> value = model.interaction.feedback.get();
        if (value == null || value.position == null) return;
        SceneSortableModel.ContainerEntry<T, K, C> container = value.position.container;
        SceneGeometryView.Entry entry = geometry.get(container.declaration.items);
        if (entry == null) return;
        SceneControlPaint.border(target, entry.box, entry.clip, node.style().color);
        SceneSortableModel.RowEntry<T, K, C> before = model.rows.get(value.position.before);
        LayoutBox anchor = before == null ? null : geometry.get(before.node) == null ? null : geometry.get(before.node).box;
        boolean after = false;
        if (anchor == null) {
            for (SceneSortableModel.RowEntry<T, K, C> row : container.rows) {
                if (row == value.row || geometry.get(row.node) == null) continue;
                anchor = geometry.get(row.node).box; after = true;
            }
        }
        if (anchor == null) anchor = SceneDragSupport.inner(entry);
        boolean horizontal = container.declaration.layout != SceneSortable.Layout.VERTICAL;
        int x = horizontal ? SceneDragSupport.integer((long) anchor.x + (after ? anchor.width : 0)) : anchor.x;
        int y = horizontal ? anchor.y : SceneDragSupport.integer((long) anchor.y + (after ? anchor.height : 0));
        LayoutBox marker = new LayoutBox(x, y, horizontal ? 2 : anchor.width, horizontal ? anchor.height : 2);
        target.add(PaintCommand.rect(marker, entry.clip, node.style().color));
    }
}
