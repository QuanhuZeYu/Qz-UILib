package club.heiqi.uilib.testing.gallery;

import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneGrid;
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
    static SceneNode grid(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        club.heiqi.uilib.ui.reactive.Owner scope = context.scope().child();
        ReadableSignal<SceneGrid.Policy> policy = context.domain().computed(scope, () -> {
            SceneTheme current = theme.get();
            return new SceneGrid.Policy((int) Math.ceil(current.textStyle.logicalEm * 22), current.gap + 4, current.gap + 4);
        });
        return SceneGrid.create(context, policy).own(scope);
    }
    private static final class Frame implements SceneControl {
        private final ReadableSignal<SceneTheme> theme;
        Frame(ReadableSignal<SceneTheme> theme) { this.theme = theme; }
        @Override public List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Collections.singletonList(theme); }
        @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) {
            MeasuredSize size = children.measureChild(children.flowChildren().get(0), new Constraints(Math.max(0, limits.availableWidth - 2), Math.max(0, limits.availableHeight - 2)));
            return new MeasuredSize(size.width + 2, size.height + 2);
        }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
            SceneNode content = children.flowChildren().get(0);
            int inset = node.padding() + 1;
            int available = Math.max(0, allocated.width - inset * 2);
            MeasuredSize measured = children.measureChild(content,
                    new Constraints(available, Math.max(0, allocated.height - inset * 2)));
            return new ChildPlacements(Collections.singletonList(new ChildPlacements.Entry(content,
                    new LayoutBox(inset, inset, content.width().mode == Size.Mode.FILL
                            ? content.width().constrain(available) : measured.width, measured.height))));
        }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {
            SceneGeometryView.Entry entry = geometry.get(node);
            LayoutBox box = entry.box;
            int border = theme.get().panel == 0xffffffff ? 0xffe4e4e7 : 0xff27272a;
            ScenePaintShapes.rounded(paint, box, entry.clip, border, 8);
            // 动态填色归同一圆角表面；正文整盒背景会在随后绘制时盖掉边框圆弧。
            int fill = (node.background() >>> 24) == 0 ? theme.get().panel : node.background();
            ScenePaintShapes.rounded(paint, new LayoutBox(box.x + 1, box.y + 1, Math.max(0, box.width - 2), Math.max(0, box.height - 2)), entry.clip, fill, 7);
        }
    }
}
