package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
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
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 组合既有单轴视口，不另建位置权威；行为见 specs/2026-10-16-uilib-scroll-area-spec.md。 */
public final class SceneScrollArea {
    public enum Axes {
        NONE, HORIZONTAL, VERTICAL, BOTH;
        boolean includes(SceneNode.Axis axis) { return this == BOTH || this == (axis == SceneNode.Axis.ROW ? HORIZONTAL : VERTICAL); }
    }
    /** 不可变配置，几何单位均为 logical px；颜色为空时使用当前主题。 */
    public static final class Options {
        Axes axes = Axes.VERTICAL, forceVisible = Axes.NONE;
        boolean autoHide = true, clickOnTrack = true;
        int thickness = 11, minimumThumb = 25, inset = 2;
        long hideDelayMillis = 675, fadeMillis = 200;
        Integer trackColor, thumbColor, activeColor;
        public Options() {}
        private Options(Options from) {
            axes = from.axes; forceVisible = from.forceVisible; autoHide = from.autoHide; clickOnTrack = from.clickOnTrack;
            thickness = from.thickness; minimumThumb = from.minimumThumb; inset = from.inset;
            hideDelayMillis = from.hideDelayMillis; fadeMillis = from.fadeMillis;
            trackColor = from.trackColor; thumbColor = from.thumbColor; activeColor = from.activeColor;
        }
        public Options axes(Axes value) { Options next = new Options(this); next.axes = Objects.requireNonNull(value, "axes"); return next; }
        public Options forceVisible(Axes value) { Options next = new Options(this); next.forceVisible = Objects.requireNonNull(value, "forceVisible"); return next; }
        public Options autoHide(boolean value) { Options next = new Options(this); next.autoHide = value; return next; }
        public Options clickOnTrack(boolean value) { Options next = new Options(this); next.clickOnTrack = value; return next; }
        public Options dimensions(int thickness, int minimumThumb, int inset) {
            if (inset < 0 || minimumThumb <= 0 || (long) thickness <= (long) inset * 2)
                throw new IllegalArgumentException("滚动条宽度、最小滑块及内缩无效");
            Options next = new Options(this); next.thickness = thickness; next.minimumThumb = minimumThumb; next.inset = inset; return next;
        }
        public Options timing(long hideDelayMillis, long fadeMillis) {
            if (hideDelayMillis < 0 || fadeMillis < 0 || hideDelayMillis > Long.MAX_VALUE / 4_000_000L || fadeMillis > Long.MAX_VALUE / 4_000_000L)
                throw new IllegalArgumentException("滚动条隐藏时间无效");
            Options next = new Options(this); next.hideDelayMillis = hideDelayMillis; next.fadeMillis = fadeMillis; return next;
        }
        public Options colors(int track, int thumb, int active) {
            Options next = new Options(this); next.trackColor = track; next.thumbColor = thumb; next.activeColor = active; return next;
        }
    }
    /** 节点用于页面组合；视口用于显式滚动声明、滚动事实读取及正式像素移动，不暴露内部滑块状态。 */
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
    public static Area create(ScenePageContext context, SceneNode content, ReadableSignal<Options> options) {
        return create(context, content, options, context.theme());
    }
    public static Area create(ScenePageContext context, SceneNode content, ReadableSignal<Options> options, ReadableSignal<SceneTheme> theme) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(content, "content"); Objects.requireNonNull(options, "options"); Objects.requireNonNull(theme, "theme");
        SceneNode vertical = new SceneNode().size(Size.natural(), Size.fill(1)).axis(SceneNode.Axis.COLUMN).clip(true).add(content);
        vertical.bind(NodeBinding.value(() -> options.get().axes.includes(SceneNode.Axis.COLUMN), SceneNode::scrollable));
        SceneNode horizontal = new SceneNode().size(Size.fill(1), Size.fill(1)).axis(SceneNode.Axis.ROW).clip(true)
                .control(new Horizontal(vertical, options)).add(vertical);
        horizontal.bind(NodeBinding.value(() -> options.get().axes.includes(SceneNode.Axis.ROW), SceneNode::scrollable));
        SceneScrollbar.Control x = new SceneScrollbar.Control(context, horizontal, options, theme);
        SceneScrollbar.Control y = new SceneScrollbar.Control(context, vertical, options, theme);
        SceneNode xBar = x.createNode(), yBar = y.createNode();
        SceneNode root = new SceneNode().size(Size.fill(1), Size.fill(1)).clip(true)
                .control(new Frame(horizontal, xBar, yBar, options)).add(horizontal).add(xBar).add(yBar);
        root.bind(NodeBinding.hoverWithin(0, (node, inside) -> { x.visibility.areaHover(inside); y.visibility.areaHover(inside); }));
        return new Area(root, horizontal, vertical);
    }
    private static final class Frame implements SceneControl {
        private final SceneNode content, xBar, yBar;
        private final ReadableSignal<Options> options;
        Frame(SceneNode content, SceneNode xBar, SceneNode yBar, ReadableSignal<Options> options) {
            this.content = content; this.xBar = xBar; this.yBar = yBar; this.options = options;
        }
        @Override public List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Collections.singletonList(options); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text, SceneChildMeasurer children) {
            return children.measureChild(content, body);
        }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
            int p = node.padding(), w = Math.max(0, allocated.width - p * 2), h = Math.max(0, allocated.height - p * 2);
            Options current = options.get();
            int tx = current.axes.includes(SceneNode.Axis.ROW) ? Math.min(h, current.thickness) : 0;
            int ty = current.axes.includes(SceneNode.Axis.COLUMN) ? Math.min(w, current.thickness) : 0;
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
        private final ReadableSignal<Options> options;
        Horizontal(SceneNode vertical, ReadableSignal<Options> options) { this.vertical = vertical; this.options = options; }
        @Override public List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Collections.singletonList(options); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text, SceneChildMeasurer children) {
            return children.measureChild(vertical, body);
        }
        @Override public boolean scrollsWithWheel(SceneNode node) { return !options.get().axes.includes(SceneNode.Axis.COLUMN); }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
            MeasuredSize desired = children.measureChild(vertical, new Constraints(box.width, box.height));
            int width = options.get().axes.includes(SceneNode.Axis.ROW) ? Math.max(box.width, desired.width) : box.width;
            return new ChildPlacements(Collections.singletonList(new ChildPlacements.Entry(vertical, new LayoutBox(0, 0, width, box.height))));
        }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {}
    }
}
