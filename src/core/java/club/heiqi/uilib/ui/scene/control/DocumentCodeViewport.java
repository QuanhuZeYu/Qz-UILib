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
        if (node.children().isEmpty()) return new MeasuredSize(0, 0);
        // 无限内容宽度只用于代码块自然度量，视口本身仍遵守父宽约束。
        MeasuredSize content =
                children.measureChild(
                        node.children().get(0),
                        new Constraints(Integer.MAX_VALUE, constraints.availableHeight));
        // 控件返回内容尺寸，框架统一加自身padding。
        return new MeasuredSize(constraints.availableWidth, content.height);
    }

    private ChildPlacements prepared;

    @Override
    public club.heiqi.uilib.ui.scene.text.TextLayout presentText(
            SceneNode node,
            club.heiqi.uilib.ui.scene.text.TextLayout full,
            int availableWidth,
            int availableHeight,
            SceneTextMeasurer text,
            SceneChildMeasurer children) {
        if (node.children().isEmpty()) {
            prepared = new ChildPlacements(Collections.emptyList());
            return full;
        }
        LayoutBox box = new LayoutBox(0, 0, availableWidth, availableHeight);
        SceneNode content = node.children().get(0);
        // presentText收到的是框架已扣除padding的内容宽度。
        int width = box.width;
        MeasuredSize measured =
                children.measureChild(
                        content, new Constraints(Integer.MAX_VALUE, Integer.MAX_VALUE));
        int maximum = Math.max(0, measured.width - width);
        // 字体、源码和视口改变后从本次测量求范围，不保留已失效旧上限。
        offset = SceneControlViewport.clamp(offset, maximum);
        prepared =
                new ChildPlacements(
                        Collections.singletonList(
                                new ChildPlacements.Entry(
                                        content,
                                        new LayoutBox(
                                                node.padding() - offset,
                                                node.padding(),
                                                Math.max(width, measured.width),
                                                measured.height))));
        return full;
    }

    @Override
    public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) {
        return new MeasuredSize(0, 0);
    }

    @Override
    public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer childMeasurer) {
        ChildPlacements result = prepared;
        prepared = null;
        return result;
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
    public boolean scroll(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
        SceneGeometryView.Entry viewport = geometry.get(node);
        if (viewport == null || node.children().isEmpty()) return false;
        SceneGeometryView.Entry content = geometry.get(node.children().get(0));
        if (content == null) return false;
        int width = Math.max(0, viewport.box.width - node.padding() * 2);
        int maximum = Math.max(0, content.box.width - width);
        int next = SceneControlViewport.scrolled(offset, maximum, event.delta, node.scrollStep());
        if (next == offset) return false;
        offset = next;
        return true;
    }
}
