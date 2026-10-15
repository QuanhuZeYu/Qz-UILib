package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneFlowLayout;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** 单内容的临时拖动；原槽不变，正式排列或独立被动层发布唯一几何。 */
public final class SceneDraggable {
    public enum Axis { BOTH, X, Y }
    public enum Bounds { NONE, VIEWPORT, PARENT }

    /** 不可变链式声明；每次配置返回副本，外部策略 Signal 可在挂载后替换。 */
    public static final class Options {
        private Axis axis = Axis.BOTH;
        private Bounds bounds = Bounds.NONE;
        private int gridX, gridY, distance, tolerance;
        private long delayMillis;
        private boolean activationExplicit;
        private SceneNode[] handles = new SceneNode[0];
        private SceneNode overlay;
        private Consumer<Snapshot> onDrop;
        public Options() {}
        private Options(Options value) {
            axis = value.axis; bounds = value.bounds; gridX = value.gridX; gridY = value.gridY;
            distance = value.distance; tolerance = value.tolerance; delayMillis = value.delayMillis;
            handles = value.handles; overlay = value.overlay; onDrop = value.onDrop; activationExplicit = value.activationExplicit;
        }
        public Options axis(Axis value) { Options next = new Options(this); next.axis = Objects.requireNonNull(value, "axis"); return next; }
        public Options bounds(Bounds value) { Options next = new Options(this); next.bounds = Objects.requireNonNull(value, "bounds"); return next; }
        public Options grid(int x, int y) {
            if (x < 0 || y < 0) throw new IllegalArgumentException("网格步长不能为负");
            Options next = new Options(this); next.gridX = x; next.gridY = y; return next;
        }
        /** distance 与 delay 任一满足即可；零表示未声明该分支，两者均零时立即激活。 */
        public Options activation(int distance, long delayMillis, int tolerance) {
            if (distance < 0 || delayMillis < 0 || delayMillis > Long.MAX_VALUE / 1_000_000L || tolerance < 0)
                throw new IllegalArgumentException("拖动激活约束无效");
            Options next = new Options(this); next.distance = distance; next.delayMillis = delayMillis;
            next.tolerance = tolerance; next.activationExplicit = true; return next;
        }
        public Options handles(SceneNode... value) {
            Options next = new Options(this); next.handles = Objects.requireNonNull(value, "handles").clone();
            for (SceneNode handle : next.handles) Objects.requireNonNull(handle, "handle");
            return next;
        }
        /** preview 必须是独立声明，由本拖动控件拥有；原内容继续在原槽呈现。 */
        public Options overlay(SceneNode preview) { Options next = new Options(this); next.overlay = preview; return next; }
        public Options onDrop(Consumer<Snapshot> action) { Options next = new Options(this); next.onDrop = action; return next; }
    }

    /** effective delta 为网格与约束后的 scene 像素，和当次发布的移动几何一致。 */
    public static final class Snapshot {
        public final boolean active, pending;
        public final int deltaX, deltaY;
        private Snapshot(boolean active, boolean pending, int deltaX, int deltaY) {
            this.active = active; this.pending = pending; this.deltaX = deltaX; this.deltaY = deltaY;
        }
    }
    /** 一个 State 对应一个声明；外部只读观察，不持有节点或布局结果。 */
    public static final class State {
        private static final Snapshot INACTIVE = new Snapshot(false, false, 0, 0);
        private final ReactiveDomain domain;
        private final Signal<Snapshot> snapshot;
        private final ReadableSignal<Snapshot> view;
        private boolean bound, keyboard, delayAllowed;
        private long pressedNanos;
        private int startX, startY, pointerX, pointerY, grabX, grabY, keyboardX, keyboardY;
        private final SceneDragSupport.AutoScroll scrolling = new SceneDragSupport.AutoScroll();
        private State(ReactiveDomain domain) { this.domain = domain; snapshot = domain.signal(INACTIVE); view = snapshot::get; }
        public ReadableSignal<Snapshot> snapshot() { return view; }
        private void publish(boolean active, boolean pending, int x, int y) {
            Snapshot old = snapshot.get();
            if (old.active != active || old.pending != pending || old.deltaX != x || old.deltaY != y)
                snapshot.set(new Snapshot(active, pending, x, y));
        }
        private void end() {
            keyboard = false; delayAllowed = false;
            startX = startY = pointerX = pointerY = grabX = grabY = keyboardX = keyboardY = 0;
            snapshot.set(INACTIVE);
        }
    }
    private SceneDraggable() {}
    public static State state(ReactiveDomain domain) {
        Objects.requireNonNull(domain, "domain").checkWrite(); return new State(domain);
    }
    /** 保留即时起拖、结束回原槽的原接口。 */
    public static SceneNode create(ScenePageContext context, SceneNode content, State state) {
        return create(context, content, state, new Options());
    }
    public static SceneNode create(ScenePageContext context, SceneNode content, State state, Options options) {
        Objects.requireNonNull(options, "options");
        return createInternal(context, content, state, () -> options, options.overlay != null);
    }
    public static SceneNode create(ScenePageContext context, SceneNode content, State state, ReadableSignal<Options> options) {
        return createInternal(context, content, state, options, true);
    }
    private static SceneNode createInternal(ScenePageContext context, SceneNode content, State state,
            ReadableSignal<Options> options, boolean withOverlay) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(content, "content"); Objects.requireNonNull(state, "state"); Objects.requireNonNull(options, "options");
        if (context.domain() != state.domain) throw new IllegalArgumentException("拖动状态与控件必须同域");
        if (state.bound) throw new IllegalArgumentException("拖动状态已有所属声明");
        Control control = new Control(context.runtime(), content, state, options);
        SceneNode node = new SceneNode().focusable(true).clickFocus(true).control(control).add(content);
        // 静态声明没有 preview 时无需隐藏所属层；动态 options 保留切换预览的正式拥有。
        if (withOverlay) {
            SceneNode layer = SceneLayer.create(context, () -> new SceneLayer.Policy(0, SceneLayer.Participation.PASSIVE),
                    (self, source, viewport, measured) -> {
                        Snapshot current = state.snapshot.get();
                        return new LayoutBox(SceneDragSupport.integer((long) source.x + node.padding() + current.deltaX),
                                SceneDragSupport.integer((long) source.y + node.padding() + current.deltaY), measured.width, measured.height);
                    }, Arrays.asList(state.snapshot, options));
            layer.bind(NodeBinding.value(() -> state.snapshot.get().active && control.options().overlay != null, SceneNode::visible));
            layer.bind(NodeBinding.ownedChildren(() -> {
                SceneNode preview = control.options().overlay;
                if (preview != null && (preview == content || SceneDragSupport.within(preview, content)))
                    throw new IllegalArgumentException("overlay 必须是独立预览声明");
                return preview == null ? Collections.emptyList() : Collections.singletonList(preview);
            }));
            node.add(layer);
            control.layer = layer;
        }
        node.bind((runtime, self, owner) -> {
            owner.own(state::end);
            return new BindingActivation() { public void refresh() {} public void close() {} };
        });
        state.bound = true;
        return node;
    }

    private static final class Control implements SceneControl {
        private final SceneRuntime runtime;
        private final SceneNode content;
        private final State state;
        private final ReadableSignal<Options> source;
        private final Object bodyTarget = new Object();
        private SceneNode layer;
        Control(SceneRuntime runtime, SceneNode content, State state, ReadableSignal<Options> source) {
            this.runtime = runtime; this.content = content; this.state = state; this.source = source;
        }
        Options options() { return Objects.requireNonNull(source.get(), "drag options"); }
        @Override public List<ReadableSignal<?>> arrangementDependencies(SceneNode node) { return Arrays.asList(state.snapshot, source); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text, SceneChildMeasurer children) {
            return SceneFlowLayout.measure(node, body, children);
        }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
            ChildPlacements ordinary = SceneFlowLayout.arrange(node, box, children);
            Snapshot current = state.snapshot.get();
            if (!current.active || options().overlay != null) return ordinary;
            List<ChildPlacements.Entry> placed = new ArrayList<>();
            for (ChildPlacements.Entry entry : ordinary.entries()) {
                LayoutBox child = entry.relativeBox;
                placed.add(new ChildPlacements.Entry(entry.child, new LayoutBox(
                        SceneDragSupport.integer((long) child.x + current.deltaX),
                        SceneDragSupport.integer((long) child.y + current.deltaY), child.width, child.height)));
            }
            return new ChildPlacements(placed);
        }
        @Override public boolean capturesPointer() { return true; }
        @Override public Object pointerTarget(SceneNode node, SceneGeometryView geometry, int x, int y) {
            if (!SceneDragSupport.eligible(content, node)) return null;
            Options options = options();
            if (options.handles.length > 0) return SceneDragSupport.handle(node, options.handles, geometry, x, y);
            return SceneDragSupport.inside(geometry.get(content), x, y) ? bodyTarget : null;
        }
        @Override public Object pointerActivator(SceneNode node, SceneNode hit, SceneGeometryView geometry, int x, int y) {
            SceneNode handle = SceneDragSupport.handle(node, options().handles, geometry, x, y);
            return handle != null && SceneDragSupport.within(hit, handle) ? handle : null;
        }
        @Override public boolean pointerTargetValid(SceneNode node, Object candidate) {
            if (!SceneDragSupport.eligible(content, node)) return false;
            Options options = options();
            if (candidate == bodyTarget) return options.handles.length == 0;
            for (SceneNode handle : options.handles) if (candidate == handle && SceneDragSupport.eligible(handle, node)) return true;
            return false;
        }
        @Override public boolean editActivator(SceneNode node, SceneNode focused, SceneEditEvent event) {
            return SceneDragSupport.dragCommand(event) && SceneDragSupport.handleFocus(node, options().handles, focused);
        }
        private void activate(long now) {
            state.scrolling.reset(now);
            state.publish(true, false, 0, 0);
        }
        private void pending(long now) {
            Options options = options();
            long dx = (long) state.pointerX - state.startX, dy = (long) state.pointerY - state.startY;
            double distance = Math.hypot(dx, dy);
            // OR 中先检测距离；超出 tolerance 只终止尚未满足的 delay 分支。
            if (options.distance > 0 && distance >= options.distance) { activate(now); return; }
            if (distance > options.tolerance) state.delayAllowed = false;
            if (state.delayAllowed && options.delayMillis > 0
                    && now - state.pressedNanos >= options.delayMillis * 1_000_000L) { activate(now); return; }
            if (options.distance == 0 && (!state.delayAllowed || options.delayMillis == 0)) state.end();
        }
        private void update(SceneNode node, SceneGeometryView geometry, SceneDragSupport.AutoScroll.Shift shift) {
            if (!state.snapshot.get().active) return;
            SceneGeometryView.Entry wrapper = geometry.get(node), sourceBox = geometry.get(content);
            if (wrapper == null || sourceBox == null) { state.end(); return; }
            int sx = shift.x, sy = shift.y;
            int baseX = SceneDragSupport.integer((long) wrapper.box.x + node.padding() + sx);
            int baseY = SceneDragSupport.integer((long) wrapper.box.y + node.padding() + sy);
            int width = sourceBox.box.width, height = sourceBox.box.height;
            SceneGeometryView.Entry overlay = options().overlay == null ? null : geometry.get(layer);
            if (overlay != null) { width = overlay.box.width; height = overlay.box.height; }
            int rawX = state.keyboard ? state.keyboardX : SceneDragSupport.integer((long) state.pointerX - state.grabX - baseX);
            int rawY = state.keyboard ? state.keyboardY : SceneDragSupport.integer((long) state.pointerY - state.grabY - baseY);
            Options options = options();
            boolean x = options.axis != Axis.Y, y = options.axis != Axis.X;
            int dx = x ? SceneDragSupport.snap(rawX, options.gridX) : 0;
            int dy = y ? SceneDragSupport.snap(rawY, options.gridY) : 0;
            LayoutBox bounds = null;
            if (options.bounds == Bounds.VIEWPORT) bounds = geometry.viewport();
            else if (options.bounds == Bounds.PARENT) {
                SceneGeometryView.Entry parent = node.parent() == null ? null : geometry.get(node.parent());
                if (parent != null) {
                    LayoutBox inner = SceneDragSupport.inner(parent);
                    boolean moves = shift.scroller != null && shift.scroller != node.parent();
                    bounds = new LayoutBox(SceneDragSupport.integer((long) inner.x + (moves ? sx : 0)),
                            SceneDragSupport.integer((long) inner.y + (moves ? sy : 0)), inner.width, inner.height);
                } else bounds = geometry.viewport();
            }
            if (bounds != null) {
                if (x) dx = SceneDragSupport.integer((long) SceneDragSupport.clamp((long) baseX + dx, width, bounds.x, bounds.width) - baseX);
                if (y) dy = SceneDragSupport.integer((long) SceneDragSupport.clamp((long) baseY + dy, height, bounds.y, bounds.height) - baseY);
            }
            state.publish(true, false, dx, dy);
        }
        private void drop() {
            Snapshot finalValue = state.snapshot.get();
            Consumer<Snapshot> action = options().onDrop;
            state.end();
            if (finalValue.active && action != null) action.accept(finalValue);
        }
        @Override public void pointer(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            switch (event.kind) {
                case DOWN:
                    SceneGeometryView.Entry entry = geometry.get(content);
                    if (entry == null) return;
                    state.end(); state.keyboard = false; state.delayAllowed = true;
                    state.startX = state.pointerX = event.x; state.startY = state.pointerY = event.y;
                    state.grabX = event.x - entry.box.x; state.grabY = event.y - entry.box.y;
                    state.pressedNanos = runtime.inputNanos();
                    Options options = options();
                    if (options.handles.length > 0 && !options.activationExplicit || options.distance == 0 && options.delayMillis == 0) activate(state.pressedNanos);
                    else state.publish(false, true, 0, 0);
                    update(node, geometry, SceneDragSupport.AutoScroll.Shift.NONE);
                    break;
                case MOVE:
                    if (state.keyboard || !state.snapshot.get().active && !state.snapshot.get().pending) return;
                    state.pointerX = event.x; state.pointerY = event.y;
                    if (state.snapshot.get().pending) pending(runtime.inputNanos());
                    update(node, geometry, SceneDragSupport.AutoScroll.Shift.NONE);
                    break;
                case UP:
                    if (state.keyboard) return;
                    state.pointerX = event.x; state.pointerY = event.y;
                    update(node, geometry, SceneDragSupport.AutoScroll.Shift.NONE);
                    drop(); break;
                default: break;
            }
        }
        @Override public boolean edit(SceneNode node, SceneEditEvent event, SceneGeometryView geometry) {
            if (!SceneDragSupport.dragCommand(event)) return false;
            Snapshot current = state.snapshot.get();
            switch (event.command) {
                case ACTIVATE_ONLY: case SPACE:
                    if (current.active || current.pending) { if (state.keyboard) drop(); }
                    else {
                        state.end(); state.keyboard = true; activate(runtime.inputNanos());
                        if (geometry != null) update(node, geometry, SceneDragSupport.AutoScroll.Shift.NONE);
                    }
                    return true;
                case ESCAPE:
                    if (!current.active && !current.pending || !state.keyboard) return false;
                    state.end(); return true;
                case LEFT: case RIGHT: case UP: case DOWN:
                    if (!current.active || !state.keyboard) return false;
                    state.keyboardX = SceneDragSupport.integer((long) state.keyboardX
                            + (event.command == SceneEditEvent.Command.LEFT ? -25 : event.command == SceneEditEvent.Command.RIGHT ? 25 : 0));
                    state.keyboardY = SceneDragSupport.integer((long) state.keyboardY
                            + (event.command == SceneEditEvent.Command.UP ? -25 : event.command == SceneEditEvent.Command.DOWN ? 25 : 0));
                    if (geometry != null) update(node, geometry, SceneDragSupport.AutoScroll.Shift.NONE);
                    return true;
                default: return false;
            }
        }
        @Override public long nextInputNanos(SceneNode node) {
            Snapshot current = state.snapshot.get(); Options options = options();
            if (current.active) return 0;
            return current.pending && state.delayAllowed && options.delayMillis > 0
                    ? state.pressedNanos + options.delayMillis * 1_000_000L : Long.MAX_VALUE;
        }
        @Override public void inputFrame(SceneNode node, SceneGeometryView geometry, long now) {
            if (!runtime.interactive(node) || !SceneDragSupport.eligible(content, node)) { state.end(); return; }
            if (state.keyboard) {
                SceneNode focused = runtime.focusRouter().focused().get();
                if (focused != node && !SceneDragSupport.handleFocus(node, options().handles, focused)) { state.end(); return; }
            }
            if (state.snapshot.get().pending) pending(now);
            if (!state.snapshot.get().active) return;
            SceneDragSupport.AutoScroll.Shift shift = state.keyboard ? SceneDragSupport.AutoScroll.Shift.NONE
                    : state.scrolling.advance(runtime, node.parent(), geometry, state.pointerX, state.pointerY, now,
                            options().axis != Axis.Y, options().axis != Axis.X);
            update(node, geometry, shift);
        }
        @Override public void focusChanged(SceneNode node, boolean focused, boolean eligible) { if (!focused) state.end(); }
        @Override public boolean cancelCaptureOnEscape(SceneNode node) {
            Snapshot current = state.snapshot.get(); return !state.keyboard && (current.active || current.pending);
        }
        @Override public void captureLost(SceneNode node) { state.end(); }
        @Override public void inputFrameCancelled(SceneNode node) { state.end(); }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {}
    }
}
