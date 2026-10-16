package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.Collections;

/** 代码保留原行和空白，超宽时仅局部横向滚动；所有几何与输入仍由scene管理。 */
final class DocumentCodeViewport implements SceneControl {
    private int offset;

    @Override
    public MeasuredSize measure(
            SceneNode node,
            Constraints constraints,
            SceneTextMeasurer text,
            SceneChildMeasurer children) {
        club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine.rejectContentLayers(node, node, "document code");
        if (children.flowChildren().isEmpty()) return new MeasuredSize(0, 0);
        // 无限内容宽度只用于代码块自然度量，视口本身仍遵守父宽约束。
        MeasuredSize content =
                children.measureChild(
                        children.flowChildren().get(0),
                        new Constraints(Integer.MAX_VALUE, constraints.availableHeight));
        // 控件返回内容尺寸，框架统一加自身padding。
        return new MeasuredSize(constraints.availableWidth, content.height);
    }

    @Override
    public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) {
        return new MeasuredSize(0, 0);
    }

    @Override
    public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
        if (children.flowChildren().isEmpty()) return new ChildPlacements(Collections.emptyList());
        SceneNode content = children.flowChildren().get(0);
        // 普通容器在最终外盒排列；文字投影不再为子布局保存跨阶段prepared。
        int width = Math.max(0, box.width - node.padding() * 2);
        MeasuredSize measured = children.measureChild(
                content, new Constraints(Integer.MAX_VALUE, Integer.MAX_VALUE));
        int maximum = Math.max(0, measured.width - width);
        offset = SceneControlViewport.clamp(offset, maximum);
        return new ChildPlacements(Collections.singletonList(new ChildPlacements.Entry(content,
                new LayoutBox(node.padding() - offset, node.padding(),
                        Math.max(width, measured.width), measured.height))));
    }

    @Override
    public void paint(
            SceneNode node,
            SceneGeometryView geometry,
            club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder target) {
        SceneGeometryView.Entry entry = geometry.get(node);
        if (entry != null && node.background() != 0)
            target.add(
                    club.heiqi.uilib.ui.scene.paint.PaintCommand.rect(
                            entry.box, entry.clip, node.background()));
    }

    @Override
    public boolean scrollableContent() {
        return true;
    }

    @Override
    public SceneScrollArea.Axes scrollbarAxes(SceneNode node) { return SceneScrollArea.Axes.HORIZONTAL; }

        @Override
        public SceneScrollViewport.Snapshot scrollViewport(SceneNode node, SceneNode.Axis axis, SceneGeometryView geometry) {
        if (axis != SceneNode.Axis.ROW || geometry.get(node) == null || node.children().isEmpty()) return null;
        SceneGeometryView.Entry content = geometry.get(node.children().get(0));
        if (content == null) return null;
        SceneGeometryView.Entry entry = geometry.get(node);
        LayoutBox window = SceneControlPaint.inner(entry.box, node.padding());
        return new SceneScrollViewport.Snapshot(window, entry.clip, offset, Math.max(0, content.box.width - window.width), window.width);
    }
    @Override
    public int scrollByPixels(SceneNode node, SceneNode.Axis axis, int pixels, SceneGeometryView geometry) {
        SceneScrollViewport.Snapshot viewport = scrollViewport(node, axis, geometry);
        if (viewport == null) return 0;
        int before = offset;
        offset = SceneControlViewport.moved(offset, viewport.maximum, pixels);
        return offset - before;
    }
    @Override
    public SceneScrollResult scrollResult(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
        return SceneScrollResult.moved(scrollByPixels(node, SceneNode.Axis.ROW,
                SceneControlViewport.wheelPixels(event.delta, node.scrollStep()), geometry));
    }
    @Override
    public boolean scroll(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            return scrollResult(node, event, geometry).consumed;
        }
}
