package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 组合原有轴向视口与独立滚动条；行为见 specs/scene/2026-10-16-uilib-scroll-area-spec.md。 */
public final class SceneScrollArea {
    public enum Axes {
        NONE, HORIZONTAL, VERTICAL, BOTH;
        boolean includes(SceneNode.Axis axis) { return this == BOTH || this == (axis == SceneNode.Axis.ROW ? HORIZONTAL : VERTICAL); }
    }
    /** 容器只声明轴和组合策略，条自身的策略委托给SceneScrollbar.Options。 */
    public static final class Options {
        private Axes axes = Axes.VERTICAL, forceVisible = Axes.NONE;
        private SceneScrollbar.Options bars = new SceneScrollbar.Options();
        public Options() {}
        private Options(Options from) { axes = from.axes; forceVisible = from.forceVisible; bars = from.bars; }
        public Options axes(Axes value) { Options next = new Options(this); next.axes = Objects.requireNonNull(value, "axes"); return next; }
        public Options forceVisible(Axes value) { Options next = new Options(this); next.forceVisible = Objects.requireNonNull(value, "forceVisible"); return next; }
        public Options autoHide(boolean value) { Options next = new Options(this); next.bars = bars.autoHide(value); return next; }
        public Options clickOnTrack(boolean value) { Options next = new Options(this); next.bars = bars.clickOnTrack(value); return next; }
        public Options dimensions(int thickness, int minimumThumb, int inset) {
            Options next = new Options(this); next.bars = bars.dimensions(thickness, minimumThumb, inset); return next;
        }
        public Options timing(long hideDelayMillis, long fadeMillis) {
            Options next = new Options(this); next.bars = bars.timing(hideDelayMillis, fadeMillis); return next;
        }
        public Options colors(int track, int thumb, int active) {
            Options next = new Options(this); next.bars = bars.colors(track, thumb, active); return next;
        }
        boolean includes(SceneNode.Axis axis) { return axes.includes(axis); }
        SceneScrollbar.Options bar(SceneNode.Axis axis) { return bars.forceVisible(forceVisible.includes(axis)); }
    }
    /** 页面仍可声明视口目标；中性port同时支持普通和专用内容，无需获取内部Control。 */
    public static final class Area {
        private final SceneNode node, horizontal, vertical;
        Area(SceneNode node, SceneNode horizontal, SceneNode vertical) { this.node = node; this.horizontal = horizontal; this.vertical = vertical; }
        public SceneNode node() { return node; }
        public SceneNode viewport(SceneNode.Axis axis) { return Objects.requireNonNull(axis, "axis") == SceneNode.Axis.ROW ? horizontal : vertical; }
    }
    private SceneScrollArea() {}
    public static Area create(ScenePageContext context, SceneNode content) { return create(context, content, new Options()); }
    public static Area create(ScenePageContext context, SceneNode content, Options options) {
        Objects.requireNonNull(options, "options"); return create(context, content, () -> options, context.theme());
    }
    public static Area create(ScenePageContext context, SceneNode content, ReadableSignal<Options> options) { return create(context, content, options, context.theme()); }
    public static Area create(ScenePageContext context, SceneNode content, ReadableSignal<Options> options, ReadableSignal<SceneTheme> theme) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(content, "content"); Objects.requireNonNull(options, "options"); Objects.requireNonNull(theme, "theme");
        Composition composition = new Composition(context);
        return composition.create(context, content, options, theme);
    }
    /** 布局只观察轴/厚度投影；样式与活动期限不成为内容测量依赖。 */
    private static final class Composition {
        final Signal<Axes> axes;
        final Signal<Integer> thickness;
        Composition(ScenePageContext context) { axes = context.domain().signal(Axes.VERTICAL); thickness = context.domain().signal(11); }
        Area create(ScenePageContext context, SceneNode content, ReadableSignal<Options> options, ReadableSignal<SceneTheme> theme) {
            SceneNode vertical = new SceneNode().size(Size.natural(), Size.fill(1)).axis(SceneNode.Axis.COLUMN).clip(true).add(content);
            SceneNode horizontal = new SceneNode().size(Size.fill(1), Size.fill(1)).axis(SceneNode.Axis.ROW).clip(true)
                    .control(new Horizontal(vertical, axes)).add(vertical);
            SceneNode root = new SceneNode().size(Size.fill(1), Size.fill(1)).clip(true);
            root.bind(SceneControlBindings.declaration(options, (node, next) -> configure(next, horizontal, vertical)));
            SceneNode xBar = bar(context, root, horizontal, SceneNode.Axis.ROW, options, theme);
            SceneNode yBar = bar(context, root, vertical, SceneNode.Axis.COLUMN, options, theme);
            root.control(new Frame(horizontal, xBar, yBar, axes, thickness)).add(horizontal).add(xBar).add(yBar);
            return new Area(root, horizontal, vertical);
        }
        private void configure(Options next, SceneNode horizontal, SceneNode vertical) {
            Objects.requireNonNull(next, "scroll area options");
            axes.set(next.axes); thickness.set(next.bars.thickness);
            setAxis(horizontal, next.axes.includes(SceneNode.Axis.ROW)); setAxis(vertical, next.axes.includes(SceneNode.Axis.COLUMN));
        }
        private static void setAxis(SceneNode node, boolean enabled) { if (node.scrollable() != enabled) node.scrollable(enabled); }
        private static SceneNode bar(ScenePageContext context, SceneNode scope, SceneNode viewport, SceneNode.Axis axis,
                ReadableSignal<Options> options, ReadableSignal<SceneTheme> theme) {
            return SceneScrollbar.create(context, SceneScrollViewport.of(context, viewport, axis), () -> options.get().bar(axis), theme, scope);
        }
    }
    private static final class Frame implements SceneControl {
        private final SceneNode content, xBar, yBar;
        private final ReadableSignal<Axes> axes;
        private final ReadableSignal<Integer> thickness;
        Frame(SceneNode content, SceneNode xBar, SceneNode yBar, ReadableSignal<Axes> axes, ReadableSignal<Integer> thickness) {
            this.content = content; this.xBar = xBar; this.yBar = yBar; this.axes = axes; this.thickness = thickness;
        }
        @Override public List<ReadableSignal<?>> arrangementDependencies(SceneNode node) { return Arrays.asList(axes, thickness); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text, SceneChildMeasurer children) { return children.measureChild(content, body); }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
            int p = node.padding(), w = Math.max(0, allocated.width - p * 2), h = Math.max(0, allocated.height - p * 2);
            int tx = axes.get().includes(SceneNode.Axis.ROW) ? Math.min(h, thickness.get()) : 0;
            int ty = axes.get().includes(SceneNode.Axis.COLUMN) ? Math.min(w, thickness.get()) : 0;
            return new ChildPlacements(Arrays.asList(
                    new ChildPlacements.Entry(content, new LayoutBox(p, p, w, h)),
                    new ChildPlacements.Entry(xBar, new LayoutBox(p, p + h - tx, Math.max(0, w - ty), tx)),
                    new ChildPlacements.Entry(yBar, new LayoutBox(p + w - ty, p, ty, Math.max(0, h - tx)))));
        }
        @Override public boolean passiveHover() { return true; }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {}
    }
    private static final class Horizontal implements SceneControl {
        private final SceneNode vertical;
        private final ReadableSignal<Axes> axes;
        Horizontal(SceneNode vertical, ReadableSignal<Axes> axes) { this.vertical = vertical; this.axes = axes; }
        @Override public List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Collections.singletonList(axes); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text, SceneChildMeasurer children) { return children.measureChild(vertical, body); }
        @Override public boolean scrollsWithWheel(SceneNode node) { return !axes.get().includes(SceneNode.Axis.COLUMN); }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
            MeasuredSize desired = children.measureChild(vertical, new Constraints(box.width, box.height));
            int width = axes.get().includes(SceneNode.Axis.ROW) ? Math.max(box.width, desired.width) : box.width;
            return new ChildPlacements(Collections.singletonList(new ChildPlacements.Entry(vertical, new LayoutBox(0, 0, width, box.height))));
        }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {}
    }
}
