package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 既有单轴视口的可组合滚动条；布局与业务生命周期仍由父节点持有。 */
public final class SceneScrollbar {
    private SceneScrollbar() {}
    public static SceneNode create(ScenePageContext context, SceneNode viewport) {
        Objects.requireNonNull(viewport, "viewport");
        SceneScrollArea.Options options = new SceneScrollArea.Options().axes(
                viewport.axis() == SceneNode.Axis.ROW ? SceneScrollArea.Axes.HORIZONTAL : SceneScrollArea.Axes.VERTICAL);
        return create(context, viewport, () -> options);
    }
    public static SceneNode create(ScenePageContext context, SceneNode viewport, ReadableSignal<SceneScrollArea.Options> options) {
        return create(context, viewport, options, context.theme());
    }
    public static SceneNode create(ScenePageContext context, SceneNode viewport, ReadableSignal<SceneScrollArea.Options> options,
            ReadableSignal<SceneTheme> theme) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        return new Control(context, Objects.requireNonNull(viewport, "viewport"), Objects.requireNonNull(options, "options"),
                Objects.requireNonNull(theme, "theme")).createNode();
    }
    static final class Control implements SceneControl {
        private final SceneRuntime runtime;
        private final SceneNode viewport;
        private final ReadableSignal<SceneScrollArea.Options> source;
        private final ReadableSignal<SceneTheme> theme;
        final SceneScrollVisibility visibility;
        private final Signal<Boolean> paging, hasRange;
        private final Object target = new Object(), trackTarget = new Object();
        private boolean dragging;
        private int grab;
        private Page page;
        Control(ScenePageContext context, SceneNode viewport, ReadableSignal<SceneScrollArea.Options> source, ReadableSignal<SceneTheme> theme) {
            runtime = context.runtime(); this.viewport = viewport; this.source = source; this.theme = theme;
            visibility = new SceneScrollVisibility(runtime, source); paging = context.domain().signal(false);
            hasRange = context.domain().signal(false);
        }
        SceneNode createNode() {
            SceneNode node = new SceneNode().focusable(true).clickFocus(false).control(this);
            node.bind((active, self, owner) -> {
                owner.own(active.observeScroll(viewport, entry -> {
                    hasRange.set(entry != null && entry.scrollMaximum > 0);
                    if (entry == null) reset(); else visibility.activity();
                }));
                owner.own(this::reset);
                return new BindingActivation() { public void refresh() {} public void close() {} };
            });
            node.bind(NodeBinding.hoverWithin(0, (self, inside) -> visibility.hover(inside)));
            // 仅观察配置：透明度更新不能重启隐藏期限，见实际淡出回归。
            node.bind(SceneControlBindings.declaration(source, (self, value) -> visibility.configure()));
            node.bind(NodeBinding.value(() -> source.get().axes.includes(viewport.axis()) && hasRange.get(), SceneNode::focusable));
            return node;
        }
        private SceneScrollArea.Options options() { return Objects.requireNonNull(source.get(), "scroll options"); }
        private boolean horizontal() { return viewport.axis() == SceneNode.Axis.ROW; }
        private boolean available() { return options().axes.includes(viewport.axis()) && viewport.scrollable() && runtime.interactive(viewport); }
        private Metrics metrics(SceneNode node, SceneGeometryView geometry) {
            return new Metrics(geometry.get(node), geometry.get(viewport), horizontal(), options());
        }
        @Override public List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Collections.singletonList(source); }
        @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Arrays.asList(source, theme, visibility.opacity); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) {
            return horizontal() ? new MeasuredSize(0, options().thickness) : new MeasuredSize(options().thickness, 0);
        }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) { return new ChildPlacements(Collections.emptyList()); }
        @Override public boolean passiveHover() { return true; }
        @Override public boolean capturesPointer() { return true; }
        @Override public Object pointerTarget(SceneNode node, SceneGeometryView geometry, int x, int y) {
            Metrics current = metrics(node, geometry);
            if (!available() || !current.trackVisible() || !current.contains(x, y)) return null;
            // 强制轨道也占据自己的输入区域，无溢出时只消费点击，不伪造可拖滑块。
            return current.scrollable() ? target : trackTarget;
        }
        @Override public boolean pointerTargetValid(SceneNode node, Object identity) {
            if (!available()) return false;
            if (identity == target) return hasRange.get();
            return identity == trackTarget && (hasRange.get() || options().forceVisible.includes(viewport.axis()));
        }
        @Override public boolean cancelCaptureOnEscape(SceneNode node) { return true; }
        @Override public void pointer(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            Metrics current = metrics(node, geometry);
            switch (event.kind) {
                case DOWN: begin(current, event); break;
                case MOVE: move(current, event); break;
                case UP: move(current, event); endDrag(); break;
                default: break;
            }
        }
        private void begin(Metrics current, ScenePointerEvent event) {
            stopPage();
            if (!current.scrollable()) return;
            visibility.activity();
            int coordinate = horizontal() ? event.x : event.y;
            if (coordinate >= current.thumbStart && coordinate < current.thumbStart + current.thumbLength) {
                dragging = true; grab = coordinate - current.thumbStart; visibility.drag(true);
            } else if (options().clickOnTrack) {
                int direction = coordinate < current.thumbStart ? -1 : 1;
                int destination = (int) Math.max(0L, Math.min(current.maximum, (long) current.offset + (long) direction * current.viewportLength));
                page = new Page(runtime.inputNanos(), current.offset, destination); paging.set(true);
            }
        }
        private void move(Metrics current, ScenePointerEvent event) {
            if (!dragging) return;
            if (!current.scrollable()) { endDrag(); return; }
            int coordinate = horizontal() ? event.x : event.y;
            int offset = current.offsetAt((long) coordinate - grab - current.trackStart);
            runtime.scrollByPixels(viewport, offset - runtime.scrollOffset(viewport));
            visibility.activity();
        }
        private void endDrag() { dragging = false; grab = 0; visibility.drag(false); }
        private void stopPage() { page = null; paging.set(false); }
        private void reset() { dragging = false; grab = 0; stopPage(); visibility.reset(); }
        @Override public void captureLost(SceneNode node) { endDrag(); stopPage(); }
        @Override public long nextInputNanos(SceneNode node) { return paging.get() ? 0 : visibility.next(); }
        @Override public void inputFrame(SceneNode node, SceneGeometryView geometry, long now) {
            if (!runtime.interactive(node) || !available() || !metrics(node, geometry).scrollable()) { reset(); return; }
            if (!options().clickOnTrack) stopPage();
            if (page != null) advancePage(now);
            visibility.advance(now);
        }
        private void advancePage(long now) {
            Page current = page;
            double progress = Math.max(0, Math.min(1, (double) (now - current.started) / 160_000_000L));
            int position = (int) Math.round(current.from + (current.to - (double) current.from) * (1 - Math.pow(1 - progress, 3)));
            runtime.scrollByPixels(viewport, position - runtime.scrollOffset(viewport));
            if (progress >= 1) stopPage();
        }
        @Override public void inputFrameCancelled(SceneNode node) { reset(); }
        @Override public boolean scrollableContent() { return true; }
        @Override public boolean scroll(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            if (!available()) return false;
            stopPage();
            long delta = -(long) event.delta * viewport.scrollStep();
            return runtime.scrollByPixels(viewport, (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, delta))) != 0;
        }
        @Override public boolean edit(SceneNode node, SceneEditEvent event, SceneGeometryView geometry) {
            if (event.command == null || !available()) return false;
            Metrics current = metrics(node, geometry);
            int delta;
            switch (event.command) {
                case LEFT: if (!horizontal()) return false; delta = -viewport.scrollStep(); break;
                case RIGHT: if (!horizontal()) return false; delta = viewport.scrollStep(); break;
                case UP: if (horizontal()) return false; delta = -viewport.scrollStep(); break;
                case DOWN: if (horizontal()) return false; delta = viewport.scrollStep(); break;
                case PAGE_UP: delta = -current.viewportLength; break;
                case PAGE_DOWN: delta = current.viewportLength; break;
                case HOME: case DOCUMENT_HOME: delta = -current.offset; break;
                case END: case DOCUMENT_END: delta = current.maximum - current.offset; break;
                default: return false;
            }
            stopPage(); runtime.scrollByPixels(viewport, delta); visibility.activity(); return true;
        }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
            Metrics current = metrics(node, geometry);
            if (!available() || !current.trackVisible()) return;
            SceneScrollArea.Options options = options(); SceneTheme style = theme.get();
            int track = options.trackColor == null ? 0 : options.trackColor;
            target.add(PaintCommand.rect(current.track, current.clip, track));
            if (!current.scrollable()) return;
            boolean active = dragging || runtime.pointerRouter().passiveHovered(geometry) == node || runtime.focusRouter().focused().get() == node;
            int color = active ? (options.activeColor == null ? style.focus : options.activeColor)
                    : (options.thumbColor == null ? (style.textStyle.color & 0x00ffffff) | 0x80000000 : options.thumbColor);
            float opacity = active ? 1f : visibility.alpha();
            int alpha = Math.round((color >>> 24) * opacity);
            rounded(target, current.thumb, current.clip, (color & 0x00ffffff) | alpha << 24);
        }
    }
    private static final class Page {
        final long started;
        final int from, to;
        Page(long started, int from, int to) { this.started = started; this.from = from; this.to = to; }
    }
    /** 同一纯几何值同时服务 paint、hit、抓取点和端点映射。 */
    private static final class Metrics {
        final LayoutBox track, thumb, clip;
        final int offset, maximum, viewportLength, trackStart, thumbStart, thumbLength, travel;
        final boolean forced;
        Metrics(SceneGeometryView.Entry bar, SceneGeometryView.Entry viewport, boolean horizontal, SceneScrollArea.Options options) {
            track = bar == null ? new LayoutBox(0, 0, 0, 0) : bar.box;
            clip = bar == null ? null : bar.clip;
            offset = viewport == null ? 0 : viewport.scrollOffset; maximum = viewport == null ? 0 : viewport.scrollMaximum;
            viewportLength = viewport == null ? 0 : horizontal ? viewport.box.width : viewport.box.height;
            trackStart = (horizontal ? track.x : track.y) + options.inset;
            int length = Math.max(0, (horizontal ? track.width : track.height) - options.inset * 2);
            thumbLength = maximum == 0 ? 0 : Math.min(length, Math.max(options.minimumThumb,
                    (int) ((long) length * viewportLength / ((long) viewportLength + maximum))));
            travel = length - thumbLength;
            thumbStart = trackStart + (maximum == 0 ? 0 : (int) Math.round((double) offset * travel / maximum));
            int cross = Math.max(0, (horizontal ? track.height : track.width) - options.inset * 2);
            thumb = horizontal ? new LayoutBox(thumbStart, track.y + options.inset, thumbLength, cross)
                    : new LayoutBox(track.x + options.inset, thumbStart, cross, thumbLength);
            forced = options.forceVisible.includes(horizontal ? SceneNode.Axis.ROW : SceneNode.Axis.COLUMN);
        }
        boolean scrollable() { return maximum > 0 && viewportLength > 0 && thumbLength > 0 && thumb.width > 0 && thumb.height > 0; }
        boolean trackVisible() { return track.width > 0 && track.height > 0 && (maximum > 0 || forced); }
        boolean contains(int x, int y) { return track.contains(x, y) && (clip == null || clip.contains(x, y)); }
        int offsetAt(long position) { return travel <= 0 ? offset : (int) Math.round(Math.max(0d, Math.min(travel, (double) position)) * maximum / travel); }
    }
    /** 圆角只组合正式矩形命令，所有 backend 继续消费同一载荷。 */
    private static void rounded(PaintPlanBuilder target, LayoutBox box, LayoutBox clip, int color) {
        if ((color >>> 24) == 0 || box.width == 0 || box.height == 0) return;
        int radius = Math.min(3, Math.min(box.width, box.height) / 2);
        target.add(PaintCommand.rect(new LayoutBox(box.x, box.y + radius, box.width, box.height - radius * 2), clip, color));
        for (int y = 0; y < radius; y++) {
            double dy = radius - y - .5;
            int inset = (int) Math.ceil(radius - Math.sqrt(radius * radius - dy * dy));
            target.add(PaintCommand.rect(new LayoutBox(box.x + inset, box.y + y, box.width - inset * 2, 1), clip, color));
            target.add(PaintCommand.rect(new LayoutBox(box.x + inset, box.y + box.height - y - 1, box.width - inset * 2, 1), clip, color));
        }
    }
}
