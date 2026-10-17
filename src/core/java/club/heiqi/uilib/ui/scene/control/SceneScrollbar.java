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
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 某轴内容视口的可组合滚动条；消费中性端口，不接管内容位置或领域显露。 */
public final class SceneScrollbar {
    /** 条自身的不可变策略；布局、绘制与时间分别在挂载期投影。 */
    public static final class Options {
        boolean autoHide = true, clickOnTrack = true, forceVisible;
        int thickness = 11, minimumThumb = 25, inset = 2;
        long hideDelayMillis = 675, fadeMillis = 200;
        Integer trackColor, thumbColor, activeColor;
        public Options() {}
        private Options(Options from) {
            autoHide = from.autoHide; clickOnTrack = from.clickOnTrack; forceVisible = from.forceVisible;
            thickness = from.thickness; minimumThumb = from.minimumThumb; inset = from.inset;
            hideDelayMillis = from.hideDelayMillis; fadeMillis = from.fadeMillis;
            trackColor = from.trackColor; thumbColor = from.thumbColor; activeColor = from.activeColor;
        }
        public Options autoHide(boolean value) { Options next = new Options(this); next.autoHide = value; return next; }
        public Options clickOnTrack(boolean value) { Options next = new Options(this); next.clickOnTrack = value; return next; }
        public Options forceVisible(boolean value) { Options next = new Options(this); next.forceVisible = value; return next; }
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
    private SceneScrollbar() {}
    public static SceneNode create(ScenePageContext context, SceneNode viewport) {
        Objects.requireNonNull(viewport, "viewport");
        return create(context, SceneScrollViewport.of(context, viewport), () -> new Options(), context.theme());
    }
    /** 既有Area配置入口作为适配保留；独立条不依赖容器配置。 */
    public static SceneNode create(ScenePageContext context, SceneNode viewport, ReadableSignal<SceneScrollArea.Options> options) {
        return create(context, viewport, options, context.theme());
    }
    public static SceneNode create(ScenePageContext context, SceneNode viewport, ReadableSignal<SceneScrollArea.Options> options, ReadableSignal<SceneTheme> theme) {
        return create(context, SceneScrollViewport.of(context, viewport).enabled(() -> options.get().includes(viewport.axis())),
                () -> options.get().bar(viewport.axis()), theme);
    }
    public static SceneNode create(ScenePageContext context, SceneScrollViewport viewport, ReadableSignal<Options> options, ReadableSignal<SceneTheme> theme) {
        return create(context, viewport, options, theme, null);
    }
    static SceneNode create(ScenePageContext context, SceneScrollViewport viewport, ReadableSignal<Options> options,
            ReadableSignal<SceneTheme> theme, SceneNode hoverScope) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        if (!Objects.requireNonNull(viewport, "viewport").belongsTo(context.runtime()))
            throw new IllegalArgumentException("滚动条与视口必须属于同一运行时");
        Control control = new Control(context.runtime(), viewport, Objects.requireNonNull(theme, "theme"));
        SceneNode node = control.createNode(Objects.requireNonNull(options, "options"));
        if (hoverScope != null) hoverScope.bind(NodeBinding.hoverWithin(0, (self, inside) -> control.visibility.areaHover(inside)));
        return node;
    }
    /** 运行时原生装饰仍复用同一Control，只有位置与焦点参与方式不同。 */
    public static SceneNode automatic(SceneRuntime runtime, SceneNode host, SceneNode.Axis axis, ReadableSignal<SceneTheme> theme) {
        SceneScrollViewport viewport = new SceneScrollViewport(runtime, host, axis)
                .enabled(() -> !runtime.hasExplicitScrollbar(host, axis));
        Control control = new Control(runtime, viewport, theme); control.automatic = true;
        return control.createNode(Options::new);
    }
    private static final class Control implements SceneControl {
        private final SceneRuntime runtime;
        private final SceneScrollViewport viewport;
        private final ReadableSignal<SceneTheme> theme;
        private final Signal<Options> configuration;
        private final Signal<Integer> thickness;
        private final Signal<SceneNode.Axis> axis;
        private final Signal<Boolean> hasRange, present;
        private final SceneScrollVisibility visibility;
        private final SceneScrollPage page;
        private final Object target = new Object(), trackTarget = new Object();
        private boolean dragging, automatic;
        private int grab;
        Control(SceneRuntime runtime, SceneScrollViewport viewport, ReadableSignal<SceneTheme> theme) {
            this.runtime = runtime; this.viewport = viewport; this.theme = theme;
            configuration = runtime.domain().signal(new Options()); thickness = runtime.domain().signal(11);
            axis = runtime.domain().signal(viewport.axis());
            hasRange = runtime.domain().signal(false); present = runtime.domain().signal(false);
            visibility = new SceneScrollVisibility(runtime, configuration); page = new SceneScrollPage(runtime.domain());
        }
        SceneNode createNode(ReadableSignal<Options> source) {
            SceneNode node = new SceneNode().clickFocus(false).control(this);
            node.bind((active, self, owner) -> {
                // 声明可在挂载前移动；真正的所属关系在激活点核验，同runtime重挂载仍合法。
                if (!viewport.belongsTo(active)) throw new IllegalArgumentException("滚动条挂载运行时与视口不同");
                if (automatic) owner.own(runtime.observeScrollHover(viewport.node(), visibility::areaHover));
                else owner.own(runtime.claimScrollbar(viewport));
                owner.own(viewport.observe(this::viewportChanged));
                owner.own(() -> { stop(); visibility.close(); });
                return new BindingActivation() { public void refresh() {} public void close() {} };
            });
            node.bind(NodeBinding.hoverWithin(0, (self, inside) -> visibility.hover(inside)));
            node.bind(SceneControlBindings.declaration(source, (self, value) -> configure(Objects.requireNonNull(value, "scroll options"))));
            if (!automatic) node.bind(NodeBinding.value(hasRange, SceneNode::focusable));
            return node;
        }
        private void configure(Options next) {
            Options previous = configuration.get(); configuration.set(next); thickness.set(next.thickness);
            if (previous.autoHide != next.autoHide || previous.hideDelayMillis != next.hideDelayMillis || previous.fadeMillis != next.fadeMillis)
                visibility.configure();
            if (!next.clickOnTrack) page.stop();
        }
        private void viewportChanged(SceneScrollViewport.Snapshot next) {
            axis.set(viewport.axis()); present.set(next != null); hasRange.set(next != null && next.maximum > 0);
            if (hasRange.get()) visibility.activity(); else stop();
        }
        private SceneScrollbarGeometry geometry(SceneNode node, SceneGeometryView geometry) {
            return new SceneScrollbarGeometry(geometry.get(node), viewport.snapshot(geometry), viewport.axis(), configuration.get());
        }
        @Override public List<ReadableSignal<?>> measureDependencies(SceneNode node) { return Arrays.asList(thickness, axis); }
        @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) { return Arrays.asList(configuration, theme, visibility.opacity); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) {
            return axis.get() == SceneNode.Axis.ROW ? new MeasuredSize(0, thickness.get()) : new MeasuredSize(thickness.get(), 0);
        }
        @Override public LayoutBox placeScrollDecoration(SceneNode node, SceneGeometryView geometry) {
            if (!automatic) return null;
            SceneScrollViewport.Snapshot view = viewport.layoutSnapshot(geometry);
            if (view == null || view.maximum == 0) return new LayoutBox(0, 0, 0, 0);
            boolean horizontal = viewport.axis() == SceneNode.Axis.ROW;
            SceneNode.Axis otherAxis = horizontal ? SceneNode.Axis.COLUMN : SceneNode.Axis.ROW;
            SceneScrollViewport.Snapshot other = runtime.scrollLayoutSnapshot(viewport.node(), otherAxis, geometry);
            int corner = other != null && other.maximum > 0 && !runtime.hasExplicitScrollbar(viewport.node(), otherAxis) ? thickness.get() : 0;
            LayoutBox window = view.box;
            int cross = Math.min(thickness.get(), horizontal ? window.height : window.width);
            return horizontal ? new LayoutBox(window.x, window.y + window.height - cross, Math.max(0, window.width - corner), cross)
                    : new LayoutBox(window.x + window.width - cross, window.y, cross, Math.max(0, window.height - corner));
        }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) { return new ChildPlacements(Collections.emptyList()); }
        @Override public boolean passiveHover() { return true; }
        @Override public boolean capturesPointer() { return true; }
        @Override public Object pointerTarget(SceneNode node, SceneGeometryView geometry, int x, int y) {
            SceneScrollbarGeometry current = geometry(node, geometry);
            if (!current.contains(x, y)) return null;
            return current.scrollable() ? target : trackTarget;
        }
        @Override public boolean pointerTargetValid(SceneNode node, Object identity) {
            if (!present.get() || !runtime.interactive(viewport.node())) return false;
            return identity == target ? hasRange.get() : identity == trackTarget && (hasRange.get() || configuration.get().forceVisible);
        }
        @Override public boolean cancelCaptureOnEscape(SceneNode node) { return true; }
        @Override public void pointer(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            SceneScrollbarGeometry current = geometry(node, geometry);
            switch (event.kind) {
                case DOWN: begin(current, event); break;
                case MOVE: move(current, event); break;
                case UP: move(current, event); endDrag(); break;
                default: break;
            }
        }
        private void begin(SceneScrollbarGeometry current, ScenePointerEvent event) {
            page.stop();
            if (!current.scrollable()) return;
            visibility.activity(); int coordinate = current.coordinate(event.x, event.y);
            if (current.onThumb(coordinate)) {
                dragging = true; grab = current.grab(coordinate); visibility.drag(true);
            } else if (configuration.get().clickOnTrack) page.begin(current.view, current.direction(coordinate), runtime.inputNanos());
        }
        private void move(SceneScrollbarGeometry current, ScenePointerEvent event) {
            if (!dragging) return;
            if (!current.scrollable()) { endDrag(); return; }
            viewport.moveToPixels(current.offset(current.coordinate(event.x, event.y), grab)); visibility.activity();
        }
        private void endDrag() { dragging = false; grab = 0; visibility.drag(false); }
        private void stop() { dragging = false; grab = 0; page.stop(); visibility.stop(); }
        @Override public void captureLost(SceneNode node) { endDrag(); page.stop(); }
        @Override public long nextInputNanos(SceneNode node) { return page.active.get() ? 0 : visibility.next(); }
        @Override public void inputFrame(SceneNode node, SceneGeometryView geometry, long now) {
            SceneScrollViewport.Snapshot view = viewport.snapshot(geometry);
            if (!runtime.interactive(node) || view == null || view.maximum == 0) { stop(); return; }
            page.advance(viewport, view, now); visibility.advance(now);
        }
        @Override public void inputFrameCancelled(SceneNode node) { stop(); }
        @Override public boolean scrollableContent() { return true; }
        @Override public SceneNode scrollMotionViewport(SceneNode node) { return viewport.node(); }
        @Override public SceneScrollResult scrollResult(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            page.stop();
            return SceneScrollResult.moved(viewport.moveByPixels(SceneControlViewport.wheelPixels(event.delta, viewport.node().scrollStep())));
        }
        @Override public boolean edit(SceneNode node, SceneEditEvent event, SceneGeometryView geometry) {
            Integer delta = geometry(node, geometry).command(event.command, viewport.node().scrollStep());
            if (delta == null) return false;
            page.stop(); viewport.moveByPixels(delta); visibility.activity(); return true;
        }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
            boolean active = dragging || runtime.pointerRouter().passiveHovered(geometry) == node || runtime.focusRouter().focused().get() == node;
            SceneScrollbarPaint.paint(geometry(node, geometry), configuration.get(), theme.get(), active, visibility.alpha(), target);
        }
    }
}
