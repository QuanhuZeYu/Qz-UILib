package club.heiqi.uilib.testing.gallery;

import java.util.ArrayList;
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
import club.heiqi.uilib.ui.scene.layout.SceneFlowLayout;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 广场局部视觉词汇；正式控件承担输入，卡片只装饰一个按正式引擎布局的内容节点。 */
final class GalleryStyle {
    static final int LIGHT_BACKGROUND = club.heiqi.uilib.ui.scene.control.SceneAppearance.LIGHT_CANVAS, DARK_BACKGROUND = club.heiqi.uilib.ui.scene.control.SceneAppearance.DARK_CANVAS;
    private GalleryStyle() {}
    static SceneTheme theme(boolean dark) {return club.heiqi.uilib.ui.scene.control.SceneAppearance.theme(dark);}
    static SceneNode card(ReadableSignal<SceneTheme> theme) {
        return new SceneNode().size(Size.fill(1), Size.natural()).padding(20).gap(12);
    }
    static SceneNode label(ScenePageContext context, ReadableSignal<String> value, ReadableSignal<SceneTheme> theme, int em, boolean muted) {
        return SceneLabel.create(context, value, () -> theme.get().withTextStyle(theme.get().textStyle.withEm(em).withFontStyle(new club.heiqi.uilib.ui.scene.text.FontStyle(em>=18?600:400,club.heiqi.uilib.ui.scene.text.FontStyle.Posture.NORMAL)).withColor(
                muted ? new club.heiqi.uilib.ui.scene.control.SceneAppearance.Palette(theme.get()).muted : theme.get().textStyle.color)));
    }
    static SceneNode button(ScenePageContext context, ReadableSignal<String> value, ReadableSignal<SceneTheme> theme, Runnable action) {
        return club.heiqi.uilib.ui.scene.control.SceneAppearance.button(context,value,theme,action,club.heiqi.uilib.ui.scene.control.SceneAppearance.Role.OUTLINE);
    }
    static SceneNode primary(ScenePageContext context,ReadableSignal<String> text,ReadableSignal<SceneTheme> theme,Runnable action){
        return club.heiqi.uilib.ui.scene.control.SceneAppearance.button(context,text,theme,action,club.heiqi.uilib.ui.scene.control.SceneAppearance.Role.PRIMARY);
    }
    static SceneNode header(ScenePageContext context,ReadableSignal<SceneTheme> theme,String title,String description,Runnable back){
        SceneNode heading=centeredRow().size(Size.fill(1),Size.natural()).gap(16);
        heading.add(label(context,()->title,theme,28,false).size(Size.fill(1),Size.natural()));
        if(back!=null)heading.add(button(context,()->"返回场景概览",theme,back));
        return new SceneNode().size(Size.fill(1),Size.natural()).gap(8).add(heading)
                .add(label(context,()->description,theme,15,true));
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
    static SceneNode dragGrip(ReadableSignal<SceneTheme> theme) {
        return new SceneNode().size(Size.natural(), Size.natural()).control(new DragGrip(theme))
                .bind(NodeBinding.value(() -> theme.get().textStyle.withEm(20), SceneNode::style));
    }
    /** 把手是几何图标；冒号的墨迹偏离行盒中心，不能用字体基线充当图标对齐。 */
    private static final class DragGrip implements SceneControl {
        private final ReadableSignal<SceneTheme> theme;
        DragGrip(ReadableSignal<SceneTheme> theme) { this.theme = theme; }
        @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) {
            float em = node.style().logicalEm;
            return new MeasuredSize((int) Math.ceil(em / 2), (int) Math.ceil(em * 1.5));
        }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
            return new ChildPlacements(Collections.emptyList());
        }
        @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Collections.singletonList(theme); }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry == null) return;
            int dot = Math.max(1, Math.round(node.style().logicalEm / 10));
            int gap = Math.max(1, Math.round(node.style().logicalEm * .15f));
            int side = dot * 2 + gap;
            int x = entry.box.x + (entry.box.width - side) / 2;
            int y = entry.box.y + (entry.box.height - side) / 2;
            boolean enabled = true;
            for (SceneNode at = node; at != null; at = at.parent()) enabled &= at.enabled();
            int color = enabled ? node.style().color : theme.get().disabledText;
            for (int row = 0; row < 2; row++) for (int column = 0; column < 2; column++)
                ScenePaintShapes.rounded(paint, new LayoutBox(x + column * (dot + gap),
                        y + row * (dot + gap), dot, dot), entry.clip, color, dot / 2);
        }
    }
    static SceneNode centeredRow() {
        return new SceneNode().axis(SceneNode.Axis.ROW).control(new CenteredRow());
    }
    /** 混合字号按同批自然行盒居中；位置归正式排列，不给字形或宿主基线加补偿。 */
    private static final class CenteredRow implements SceneControl {
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {}
        @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) {
            return new MeasuredSize(0, 0);
        }
        @Override public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text, SceneChildMeasurer children) {
            return SceneFlowLayout.measure(node, limits, children);
        }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
            int height = Math.max(0, allocated.height - node.padding() * 2);
            List<ChildPlacements.Entry> centered = new ArrayList<>();
            for (ChildPlacements.Entry entry : SceneFlowLayout.arrange(node, allocated, children).entries()) {
                LayoutBox box = entry.relativeBox;
                centered.add(new ChildPlacements.Entry(entry.child, new LayoutBox(box.x,
                        node.padding() + Math.max(0, height - box.height) / 2, box.width, box.height)));
            }
            return new ChildPlacements(centered);
        }
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
            int border = new club.heiqi.uilib.ui.scene.control.SceneAppearance.Palette(theme.get()).border;
            ScenePaintShapes.rounded(paint, box, entry.clip, border, club.heiqi.uilib.ui.scene.control.SceneAppearance.CARD_RADIUS);
            // 动态填色归同一圆角表面；正文整盒背景会在随后绘制时盖掉边框圆弧。
            int fill = (node.background() >>> 24) == 0 ? theme.get().panel : node.background();
            ScenePaintShapes.rounded(paint, new LayoutBox(box.x + 1, box.y + 1, Math.max(0, box.width - 2), Math.max(0, box.height - 2)), entry.clip, fill, club.heiqi.uilib.ui.scene.control.SceneAppearance.CARD_RADIUS-1);
        }
    }
}
