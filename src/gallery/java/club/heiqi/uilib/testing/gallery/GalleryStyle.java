package club.heiqi.uilib.testing.gallery;

import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 广场局部视觉词汇；正式控件承担输入，卡片只装饰一个按正式引擎布局的内容节点。 */
final class GalleryStyle {
    static final int LIGHT_BACKGROUND = 0xfffafafa, DARK_BACKGROUND = 0xff09090b;
    private GalleryStyle() {}
    static SceneTheme theme(boolean dark) {
        return new SceneTheme((dark ? SceneTheme.dark() : SceneTheme.light()).textStyle.withEm(15).withColor(dark ? 0xfffafafa : 0xff18181b),
                dark ? 0xff09090b : 0xffffffff, dark ? 0xff27272a : 0xfff4f4f5,
                dark ? 0xff3f3f46 : 0xffe4e4e7, dark ? 0xff52525b : 0xffd4d4d8,
                dark ? 0xffa1a1aa : 0xff71717a, dark ? 0xff27272a : 0xfff4f4f5,
                0xffa1a1aa, dark ? 0xfffafafa : 0xff18181b, dark ? 0xff18181b : 0xfffafafa, 10, 12);
    }
    static SceneNode card(ReadableSignal<SceneTheme> theme) {
        return new SceneNode().size(Size.fill(1), Size.natural()).padding(20).gap(12);
    }
    static SceneNode label(ScenePageContext context, ReadableSignal<String> value, ReadableSignal<SceneTheme> theme, int em, boolean muted) {
        return SceneLabel.create(context, value, () -> theme.get().withTextStyle(theme.get().textStyle.withEm(em).withColor(
                muted ? 0xff71717a : theme.get().textStyle.color)));
    }
    static SceneNode button(ScenePageContext context, ReadableSignal<String> value, ReadableSignal<SceneTheme> theme, Runnable action) {
        return SceneButton.create(context, value, theme, action).size(Size.natural(), Size.natural());
    }
    static SceneNode frame(SceneNode content, ReadableSignal<SceneTheme> theme) {
        return new SceneNode().size(Size.fill(1), Size.natural()).control(new Frame(theme)).add(content);
    }
    static SceneNode grid() { return new SceneNode().size(Size.fill(1), Size.natural()).control(new Grid()); }
    /** 按本次可用宽度排列目录卡片；测量和最终排列均消费正式子测量，不保存窗口快照。 */
    private static final class Grid implements SceneControl {
        private ChildPlacements prepared;
        private MeasuredSize arrangeChildren(SceneNode node, Constraints limits, SceneChildMeasurer children, boolean prepare) {
            int columns = limits.availableWidth >= 1120 ? 3 : limits.availableWidth >= 720 ? 2 : 1;
            int gap = 16, width = Math.max(0, (limits.availableWidth - gap * (columns - 1)) / columns);
            java.util.List<ChildPlacements.Entry> placed = new java.util.ArrayList<>();
            int y = 0;
            for (int offset = 0; offset < node.children().size(); offset += columns) {
                int height = 0;
                for (int column = 0; column < columns && offset + column < node.children().size(); column++) {
                    SceneNode child = node.children().get(offset + column);
                    MeasuredSize size = children.measureChild(child, new Constraints(width, limits.availableHeight));
                    placed.add(new ChildPlacements.Entry(child, new LayoutBox(column * (width + gap), y, width, size.height)));
                    height = Math.max(height, size.height);
                }
                y += height + gap;
            }
            if (prepare) prepared = new ChildPlacements(placed);
            return new MeasuredSize(limits.availableWidth, Math.max(0, y - gap));
        }
        @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) { return arrangeChildren(node, limits, children, false); }
        @Override public TextLayout presentText(SceneNode node, TextLayout full, int width, int height, SceneTextMeasurer text, SceneChildMeasurer children) { arrangeChildren(node, new Constraints(width, height), children, true); return full; }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox allocated) { ChildPlacements result = prepared; prepared = null; return result; }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {}
    }
    private static final class Frame implements SceneControl {
        private final ReadableSignal<SceneTheme> theme;
        private ChildPlacements prepared;
        Frame(ReadableSignal<SceneTheme> theme) { this.theme = theme; }
        @Override public List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Collections.singletonList(theme); }
        @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) {
            MeasuredSize size = children.measureChild(node.children().get(0), new Constraints(Math.max(0, limits.availableWidth - 2), Math.max(0, limits.availableHeight - 2)));
            return new MeasuredSize(size.width + 2, size.height + 2);
        }
        @Override public TextLayout presentText(SceneNode node, TextLayout full, int width, int height, SceneTextMeasurer text, SceneChildMeasurer children) {
            SceneNode content = node.children().get(0);
            int available = Math.max(0, width - 2);
            MeasuredSize measured = children.measureChild(content, new Constraints(available, Math.max(0, height - 2)));
            prepared = new ChildPlacements(Collections.singletonList(new ChildPlacements.Entry(content, new LayoutBox(1, 1, available, measured.height))));
            return full;
        }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox allocated) { ChildPlacements result = prepared; prepared = null; return result; }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {
            SceneGeometryView.Entry entry = geometry.get(node);
            LayoutBox box = entry.box;
            int border = theme.get().panel == 0xffffffff ? 0xffe4e4e7 : 0xff27272a;
            ScenePaintShapes.rounded(paint, box, entry.clip, border, 8);
            ScenePaintShapes.rounded(paint, new LayoutBox(box.x + 1, box.y + 1, Math.max(0, box.width - 2), Math.max(0, box.height - 2)), entry.clip, theme.get().panel, 7);
        }
    }
}
