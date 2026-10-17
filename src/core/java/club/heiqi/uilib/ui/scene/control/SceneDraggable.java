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
import club.heiqi.uilib.ui.scene.motion.ElasticOptions;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
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
        private MotionSpec releaseMotion;
        private ElasticOptions elastic;
        private Axis axis = Axis.BOTH;
        private Bounds bounds = Bounds.NONE;
        private int gridX, gridY, distance, tolerance;
        private long delayMillis;
        private boolean activationExplicit;
        private SceneNode[] handles = new SceneNode[0];
        private SceneNode overlay, sourceAnchor, sourceContent;
        private boolean preserveSlot;
        private Consumer<Snapshot> onDrop;
        private SceneDragSupport.Observer observer;
        private boolean autoScroll = true;
        private double autoScrollSpeed = 1.0;
        public Options() {}
        private Options(Options value) {
            releaseMotion = value.releaseMotion; elastic = value.elastic;
            axis = value.axis; bounds = value.bounds; gridX = value.gridX; gridY = value.gridY;
            distance = value.distance; tolerance = value.tolerance; delayMillis = value.delayMillis;
            handles = value.handles; overlay = value.overlay; onDrop = value.onDrop; activationExplicit = value.activationExplicit;
            observer = value.observer; autoScroll = value.autoScroll; autoScrollSpeed = value.autoScrollSpeed;
            sourceAnchor = value.sourceAnchor; preserveSlot = value.preserveSlot; sourceContent = value.sourceContent;
        }
        public Options releaseMotion(MotionSpec value) {
            Objects.requireNonNull(value, "释放策略");
            if (value.kind == MotionSpec.Kind.DECAY) throw new IllegalArgumentException("回槽使用目标跟随策略，自由惯性归显式滚动释放");
            Options next = new Options(this); next.releaseMotion = value; return next;
        }
        /** 显式软边缘；不声明时保持原硬边界，放下意图仍使用合法坐标。 */
        public Options elastic(ElasticOptions value) { Options next = new Options(this); next.elastic = value; return next; }
        private MotionSpec releaseStrategy() { return releaseMotion != null ? releaseMotion : elastic == null ? null : elastic.releaseMotion; }
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
        public Options autoScroll(boolean enabled) { Options next = new Options(this); next.autoScroll = enabled; return next; }
        public Options autoScrollSpeed(double multiplier) {
            if (!Double.isFinite(multiplier) || multiplier < 0) throw new IllegalArgumentException("自动滚动速度倍率无效");
            Options next = new Options(this); next.autoScrollSpeed = multiplier; return next;
        }
        Options observer(SceneDragSupport.Observer value) { Options next = new Options(this); next.observer = value; return next; }
        SceneDragSupport.Observer observer() { return observer; }
        SceneNode sourceAnchor() { return sourceAnchor; }
        Options sourceAnchor(SceneNode anchor) { Options next = new Options(this); next.sourceAnchor = Objects.requireNonNull(anchor, "anchor"); return next; }
        /** 额外呈现包装不改变实际内容的输入资格。 */
        Options sourceContent(SceneNode content) { Options next = new Options(this); next.sourceContent = Objects.requireNonNull(content, "content"); return next; }
        /** 排序保留容器分配的当前插槽，拖动反馈由所属层呈现。 */
        Options preserveSlot() { Options next = new Options(this); next.preserveSlot = true; return next; }
    }

    /** 位移单位为 scene 像素。State.snapshot 表达当前拖动呈现，显式弹性允许暂态越界；
     *  onDrop 表达正常结束的合法位移，软边缘呈现先投影回有效范围。 */
    public static final class Snapshot {
        public final boolean active, pending;
        public final int deltaX, deltaY;
        private Snapshot(boolean active, boolean pending, int deltaX, int deltaY) {
            this.active = active; this.pending = pending; this.deltaX = deltaX; this.deltaY = deltaY;
        }
    }
    private static final class Offset {
        static final Offset ZERO = new Offset(0, 0);
        final int x, y;
        Offset(int x, int y) { this.x = x; this.y = y; }
    }
    /** 一个 State 对应一个声明；外部只读观察，不持有节点或布局结果。 */
    public static final class State {
        private static final Snapshot INACTIVE = new Snapshot(false, false, 0, 0);
        private final ReactiveDomain domain;
        private final Signal<Snapshot> snapshot;
        // 输入意图与公开有效位移分开；边缘限位后的反馈不能反过来成为下一次排列输入。
        private final Signal<Offset> preferred;
        private final ReadableSignal<Snapshot> view;
        private boolean bound, keyboard, delayAllowed;
        private long pressedNanos;
        private Runnable cancelSession;
        private int startX, startY, pointerX, pointerY, grabX, grabY, keyboardX, keyboardY;
        private final SceneDragSupport.AutoScroll scrolling = new SceneDragSupport.AutoScroll();
        private State(ReactiveDomain domain) { this.domain = domain; snapshot = domain.signal(INACTIVE); preferred = domain.signal(Offset.ZERO); view = snapshot::get; }
        public ReadableSignal<Snapshot> snapshot() { return view; }
        private void publish(boolean active, boolean pending, int x, int y) {
            Snapshot old = snapshot.get();
            if (old.active != active || old.pending != pending || old.deltaX != x || old.deltaY != y)
                snapshot.set(new Snapshot(active, pending, x, y));
        }
        private void prefer(Offset next) {
            Offset old = preferred.get();
            if (old.x != next.x || old.y != next.y) preferred.set(next);
        }
        private void end() {
            keyboard = false; delayAllowed = false;
            startX = startY = pointerX = pointerY = grabX = grabY = keyboardX = keyboardY = 0;
            try { preferred.set(Offset.ZERO); snapshot.set(INACTIVE); }
            finally { if (cancelSession != null) cancelSession.run(); }
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
        // 静态无预览无需所属层；动态配置保留正式拥有与切换能力。
        if (withOverlay) control.installOverlay(context, node);
        control.bindLifecycle(node);
        node.bind(NodeBinding.value(options, (self, value) -> control.syncObserver(self, Objects.requireNonNull(value, "drag options"))));
        state.bound = true;
        return node;
    }

    private static final class Control implements SceneControl {
        private final SceneRuntime runtime;
        private final SceneNode content;
        private final State state;
        private final ReadableSignal<Options> source;
        private final Object bodyTarget = new Object();
        private final SceneDragRelease release;
        private long releaseEventTime;
        private final Placement placement = new Placement();
        private SceneNode layer;
        private boolean mounted;
        private SceneDragSupport.Observer observer;
        Control(SceneRuntime runtime, SceneNode content, State state, ReadableSignal<Options> source) {
            this.runtime = runtime; this.content = content; this.state = state; this.source = source;
            release = new SceneDragRelease(runtime);
        }
        void installOverlay(ScenePageContext context, SceneNode node) {
            layer = SceneLayer.create(context, () -> new SceneLayer.Policy(0, SceneLayer.Participation.PASSIVE),
                    (self, sourceBox, viewport, measured) -> placement.overlay(node, sourceBox, viewport, measured),
                    Arrays.asList(state.snapshot, state.preferred, source, release.active));
            layer.bind(NodeBinding.value(() -> (state.snapshot.get().active || release.active.get()) && options().overlay != null, SceneNode::visible));
            layer.bind(NodeBinding.ownedChildren(() -> {
                SceneNode preview = options().overlay;
                if (preview != null && (preview == content || SceneDragSupport.within(preview, content)))
                    throw new IllegalArgumentException("overlay 必须是独立预览声明");
                return preview == null ? Collections.emptyList() : Collections.singletonList(preview);
            }));
            node.add(layer);
        }
        void bindLifecycle(SceneNode node) {
            node.bind(NodeBinding.value(() -> {
                runtime.motionPolicy().get(); runtime.interactionRevision().get();
                return options().releaseStrategy();
            }, (self, motion) -> {
                if (motion == null || !runtime.interactive(self)) release.stop(); else release.policy(motion);
            }));
            node.bind((runtime, self, owner) -> {
                mounted = true;
                state.cancelSession = () -> { if (observer != null) observer.cancel(self); };
                owner.own(() -> unmount(self));
                return new BindingActivation() { public void refresh() {} public void close() {} };
            });
        }
        private void unmount(SceneNode node) {
            try { release.stop(); state.end(); }
            finally {
                mounted = false;
                if (observer != null) observer.detach(node);
                observer = null; state.cancelSession = null;
            }
        }
        Options options() { return Objects.requireNonNull(source.get(), "drag options"); }
        private void syncObserver(SceneNode node, Options options) {
            SceneDragSupport.Observer next = options.observer;
            if (!mounted || observer == next) return;
            if (observer != null && observer.same(next)) {
                // 合法移动保持来源身份及挂载，只刷新 anchor 等当前资格配置。
                observer.configure(node, next);
                return;
            }
            // scope/key 切换先取消旧手势；新注册不能继承旧 capture 尾部。
            state.end();
            if (observer != null) observer.detach(node);
            observer = null;
            if (next != null) { next.attach(runtime, node, state::end); observer = next; }
        }
        private SceneNode sourceContent() { return options().sourceContent == null ? content : options().sourceContent; }
        private boolean eligible(SceneNode node) {
            return SceneDragSupport.within(sourceContent(), content) && SceneDragSupport.eligible(sourceContent(), node);
        }
        private boolean valid(SceneNode node, SceneGeometryView geometry) {
            SceneNode anchor = options().sourceAnchor == null ? node : options().sourceAnchor;
            return geometry != null && runtime.interactive(node) && eligible(node)
                    && SceneDragSupport.within(node, anchor) && geometry.get(node) != null
                    && SceneDragSupport.visible(geometry, anchor);
        }
        @Override public List<ReadableSignal<?>> arrangementDependencies(SceneNode node) { return Arrays.asList(state.snapshot, state.preferred, source, release.active); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text, SceneChildMeasurer children) {
            return SceneFlowLayout.measure(node, body, children);
        }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) {
            return arrange(node, box, children, null, null);
        }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children,
                LayoutBox parentAllocation, LayoutBox viewport) {
            Options current = options();
            placement.configure(node, current, parentAllocation, viewport);
            ChildPlacements ordinary = SceneFlowLayout.arrange(node, box, children);
            placement.observeContent(box, ordinary, current);
            if ((!state.snapshot.get().active && !release.active.get()) || current.overlay != null || current.preserveSlot) return ordinary;
            Offset offset = release.active.get() ? new Offset(release.x(), release.y()) : state.preferred.get();
            return placement.translate(box, ordinary, offset);
        }
        /** 同一边界分别服务释放约束、呈现排列和业务投影；软边缘只放宽呈现。 */
        private final class Placement {
            private LayoutBox bounds;
            void configure(SceneNode node, Options current, LayoutBox parentAllocation, LayoutBox viewport) {
                release.elastic(current.elastic);
                switch (current.bounds) {
                    case NONE: bounds = null; break;
                    case VIEWPORT: bounds = viewport; break;
                    case PARENT: bounds = parentAllocation == null ? viewport
                            : SceneControlPaint.inner(parentAllocation, node.parent().padding()); break;
                }
            }
            void observeContent(LayoutBox box, ChildPlacements ordinary, Options current) {
                if (current.overlay != null) return;
                // 预览的最终范围由所属层提供，普通内容不得覆盖或撤销该轨迹约束。
                for (ChildPlacements.Entry entry : ordinary.entries()) {
                    if (entry.child != content) continue;
                    LayoutBox child = entry.relativeBox;
                    release.bounds(bounds, box.x + child.x, box.y + child.y, child.width, child.height);
                }
            }
            private boolean hardRelease() { return release.active.get() && bounds != null && options().elastic == null; }
            ChildPlacements translate(LayoutBox box, ChildPlacements ordinary, Offset offset) {
                boolean hard = hardRelease();
                List<ChildPlacements.Entry> placed = new ArrayList<>();
                for (ChildPlacements.Entry entry : ordinary.entries()) {
                    LayoutBox child = entry.relativeBox;
                    int x = SceneDragSupport.integer((long) child.x + offset.x), y = SceneDragSupport.integer((long) child.y + offset.y);
                    if (hard) {
                        x = SceneDragSupport.integer((long) SceneDragSupport.clamp((long) box.x + x, child.width, bounds.x, bounds.width) - box.x);
                        y = SceneDragSupport.integer((long) SceneDragSupport.clamp((long) box.y + y, child.height, bounds.y, bounds.height) - box.y);
                    }
                    placed.add(new ChildPlacements.Entry(entry.child, new LayoutBox(x, y, child.width, child.height)));
                }
                return new ChildPlacements(placed);
            }
            LayoutBox overlay(SceneNode node, LayoutBox sourceBox, LayoutBox viewport, MeasuredSize measured) {
                Offset current = release.active.get() ? new Offset(release.x(), release.y()) : state.preferred.get();
                int x = SceneDragSupport.integer((long) sourceBox.x + node.padding() + current.x);
                int y = SceneDragSupport.integer((long) sourceBox.y + node.padding() + current.y);
                release.bounds(bounds, viewport, sourceBox.x + node.padding(), sourceBox.y + node.padding(), measured.width, measured.height);
                if (hardRelease()) {
                    x = SceneDragSupport.clamp(x, measured.width, bounds.x, bounds.width);
                    y = SceneDragSupport.clamp(y, measured.height, bounds.y, bounds.height);
                }
                return new LayoutBox(x, y, measured.width, measured.height);
            }
            Snapshot legalDrop(SceneNode node, SceneGeometryView geometry, Snapshot value, LayoutBox dragBox) {
                if (options().elastic == null || bounds == null) return value;
                LayoutBox slot = geometry.get(node).box;
                int baseX = slot.x + node.padding(), baseY = slot.y + node.padding();
                int legalX = SceneDragSupport.clamp((long) baseX + value.deltaX, dragBox.width, bounds.x, bounds.width) - baseX;
                int legalY = SceneDragSupport.clamp((long) baseY + value.deltaY, dragBox.height, bounds.y, bounds.height) - baseY;
                return new Snapshot(true, false, legalX, legalY);
            }
        }
        @Override public boolean capturesPointer() { return true; }
        @Override public Object pointerTarget(SceneNode node, SceneGeometryView geometry, int x, int y) {
            if (!eligible(node)) return null;
            Options options = options();
            if (options.handles.length > 0) return SceneDragSupport.handle(node, options.handles, geometry, x, y);
            return SceneDragSupport.inside(geometry.get(sourceContent()), x, y) ? bodyTarget : null;
        }
        @Override public Object pointerActivator(SceneNode node, SceneNode hit, SceneGeometryView geometry, int x, int y) {
            SceneNode handle = SceneDragSupport.handle(node, options().handles, geometry, x, y);
            return handle != null && SceneDragSupport.within(hit, handle) ? handle : null;
        }
        @Override public boolean pointerTargetValid(SceneNode node, Object candidate) {
            if (!eligible(node)) return false;
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
        private DragGeometry preferPosition(SceneNode node, SceneGeometryView geometry, SceneDragSupport.AutoScroll.Shift shift) {
            if (!state.snapshot.get().active) return null;
            if (!valid(node, geometry)) { state.end(); return null; }
            SceneGeometryView.Entry wrapper = geometry.get(node), sourceBox = geometry.get(sourceContent());
            if (wrapper == null || sourceBox == null) { state.end(); return null; }
            DragGeometry drag = new DragGeometry(node, geometry, shift, wrapper.box, sourceBox.box);
            drag.alignKeyboard();
            state.prefer(drag.resolve());
            return drag;
        }
        private void update(SceneNode node, SceneGeometryView geometry, SceneDragSupport.AutoScroll.Shift shift) {
            DragGeometry drag = preferPosition(node, geometry, shift);
            if (drag == null) return;
            Offset preferred = state.preferred.get();
            if (options().preserveSlot && options().overlay == null) {
                // 通用排序由既有 board 所属层执行预览；这里继续向该控件递交位置意图。
                state.publish(true, false, preferred.x, preferred.y);
                LayoutBox intent = new LayoutBox(SceneDragSupport.integer((long) drag.baseX + preferred.x),
                        SceneDragSupport.integer((long) drag.baseY + preferred.y), drag.width, drag.height);
                if (observer != null) observer.update(node, geometry, state.keyboard, intent,
                        state.keyboard ? SceneDragSupport.centerX(intent) : state.pointerX,
                        state.keyboard ? SceneDragSupport.centerY(intent) : state.pointerY);
            } else synchronize(node, geometry);
        }
        private LayoutBox feedbackBox(SceneNode node, SceneGeometryView geometry) {
            if (options().preserveSlot && options().overlay == null) {
                SceneGeometryView.Entry slot = geometry.get(node);
                SceneGeometryView.Entry content = geometry.get(sourceContent());
                if (slot == null || content == null) return null;
                Snapshot current = state.snapshot.get();
                return new LayoutBox(SceneDragSupport.integer((long) slot.box.x + slot.node.padding() + current.deltaX),
                        SceneDragSupport.integer((long) slot.box.y + slot.node.padding() + current.deltaY), content.box.width, content.box.height);
            }
            SceneGeometryView.Entry entry = geometry.get(options().overlay == null ? sourceContent() : layer);
            return entry == null ? null : entry.box;
        }
        private void synchronize(SceneNode node, SceneGeometryView geometry) {
            LayoutBox box = feedbackBox(node, geometry);
            SceneGeometryView.Entry slot = geometry.get(node);
            if (box == null || slot == null) return;
            // 反馈和接收只消费框架已发布的最终盒，不在这里重做所属层限位。
            state.publish(true, false, SceneDragSupport.integer((long) box.x - slot.box.x - node.padding()),
                    SceneDragSupport.integer((long) box.y - slot.box.y - node.padding()));
            if (observer != null) observer.update(node, geometry, state.keyboard, box,
                    state.keyboard ? SceneDragSupport.centerX(box) : state.pointerX,
                    state.keyboard ? SceneDragSupport.centerY(box) : state.pointerY);
        }
        /** 来源内容、槽基准与预览尺寸各有职责；求解只消费本次共同几何。 */
        private final class DragGeometry {
            private final SceneNode node;
            private final SceneGeometryView geometry;
            private final SceneDragSupport.AutoScroll.Shift shift;
            private final Options options;
            private final int baseX, baseY, width, height, scrollX, scrollY;
            private final boolean allowX, allowY;
            DragGeometry(SceneNode node, SceneGeometryView geometry, SceneDragSupport.AutoScroll.Shift shift,
                         LayoutBox wrapper, LayoutBox content) {
                this.node = node; this.geometry = geometry; this.shift = shift; options = options();
                scrollX = shift.xScroller != null && SceneDragSupport.within(node, shift.xScroller) ? shift.x : 0;
                scrollY = shift.yScroller != null && SceneDragSupport.within(node, shift.yScroller) ? shift.y : 0;
                baseX = SceneDragSupport.integer((long) wrapper.x + node.padding() + scrollX);
                baseY = SceneDragSupport.integer((long) wrapper.y + node.padding() + scrollY);
                SceneGeometryView.Entry overlay = options.overlay == null ? null : geometry.get(layer);
                width = overlay == null ? content.width : overlay.box.width;
                height = overlay == null ? content.height : overlay.box.height;
                allowX = options.axis != Axis.Y; allowY = options.axis != Axis.X;
            }
            void alignKeyboard() {
                if (!state.keyboard || observer == null) return;
                LayoutBox target = observer.keyboardTarget(node, geometry, null);
                if (target == null) return;
                state.keyboardX = SceneDragSupport.integer((long) SceneDragSupport.centerX(target) - baseX - width / 2);
                state.keyboardY = SceneDragSupport.integer((long) SceneDragSupport.centerY(target) - baseY - height / 2);
            }
            void preserveElasticGrab(LayoutBox current) {
                if (options.elastic == null) return;
                LayoutBox bounds = bounds(); if (bounds == null) return;
                state.grabX -= grabCorrection(current.x, width, bounds.x, bounds.width);
                state.grabY -= grabCorrection(current.y, height, bounds.y, bounds.height);
            }
            private int grabCorrection(int position, int size, int edge, int available) {
                int legal = SceneDragSupport.clamp(position, size, edge, available);
                double visible = (double) position - legal;
                double limit = Math.abs(options.elastic.displacement(Double.MAX_VALUE, runtime.motionPolicy().get()));
                if (visible == 0 || limit == 0) return 0;
                // 共同几何已取整到像素；在同一像素内反求阻力前的位置，接管不重复压缩软边缘。
                double amount = Math.min(Math.abs(visible), Math.max(0, limit - .25));
                double input = Math.copySign(limit * amount / (.55 * (limit - amount)), visible);
                return SceneDragSupport.integer(Math.round(input - visible));
            }
            Offset resolve() {
                int rawX = state.keyboard ? state.keyboardX : SceneDragSupport.integer((long) state.pointerX - state.grabX - baseX);
                int rawY = state.keyboard ? state.keyboardY : SceneDragSupport.integer((long) state.pointerY - state.grabY - baseY);
                LayoutBox bounds = bounds();
                int dx = constrainedDelta(rawX, allowX, options.gridX, baseX, width, bounds, true);
                int dy = constrainedDelta(rawY, allowY, options.gridY, baseY, height, bounds, false);
                return new Offset(dx, dy);
            }
            private LayoutBox bounds() {
                if (options.bounds == Bounds.VIEWPORT) return geometry.viewport();
                if (options.bounds != Bounds.PARENT) return null;
                SceneGeometryView.Entry parent = node.parent() == null ? null : geometry.get(node.parent());
                if (parent == null) return geometry.viewport();
                LayoutBox inner = SceneDragSupport.inner(parent);
                return new LayoutBox(SceneDragSupport.integer((long) inner.x + (shift.xScroller != node.parent() ? scrollX : 0)),
                        SceneDragSupport.integer((long) inner.y + (shift.yScroller != node.parent() ? scrollY : 0)), inner.width, inner.height);
            }
            private int constrainedDelta(int raw, boolean allowed, int grid, int base, int size, LayoutBox bounds, boolean horizontal) {
                if (!allowed) return 0;
                int snapped = SceneDragSupport.snap(raw, grid);
                if (bounds == null) return snapped;
                int legal = SceneDragSupport.integer((long) SceneDragSupport.clamp((long) base + snapped, size,
                        horizontal ? bounds.x : bounds.y, horizontal ? bounds.width : bounds.height) - base);
                if (options.elastic == null || state.keyboard) return legal;
                return SceneDragSupport.integer(Math.round(legal + options.elastic.displacement((double) snapped - legal, runtime.motionPolicy().get())));
            }
        }
        private void drop(SceneNode node, SceneGeometryView geometry) {
            if (state.snapshot.get().active && valid(node, geometry)
                    && !(options().preserveSlot && options().overlay == null)) synchronize(node, geometry);
            Snapshot finalValue = state.snapshot.get();
            LayoutBox dragBox = feedbackBox(node, geometry);
            Drop completed = null;
            try {
                if (finalValue.active && valid(node, geometry) && dragBox != null) {
                    completed = new Drop(node, geometry, finalValue, dragBox);
                    completed.receive();
                }
            } finally { state.end(); }
            if (completed != null) completed.finish();
        }
        /** 收拢一次正常放下的数据与动作；接收区先结算，输入会话清理后发布业务出口。 */
        private final class Drop {
            private final SceneNode node;
            private final SceneGeometryView geometry;
            private final Snapshot value;
            private final LayoutBox box;
            private final Consumer<Snapshot> action;
            private Runnable receiving;
            Drop(SceneNode node, SceneGeometryView geometry, Snapshot value, LayoutBox box) {
                this.node = node; this.geometry = geometry; this.value = value; this.box = box;
                action = options().onDrop;
            }
            void receive() {
                receiving = observer == null ? null : observer.normalDrop(node, geometry, state.keyboard, box,
                        state.keyboard ? SceneDragSupport.centerX(box) : state.pointerX,
                        state.keyboard ? SceneDragSupport.centerY(box) : state.pointerY);
            }
            void finish() {
                Options current = options();
                MotionSpec strategy = current.releaseStrategy();
                if (strategy != null && !current.preserveSlot)
                    release.release(value.deltaX, value.deltaY, strategy, releaseEventTime);
                // 两种会话均已清理；回调抛错及迟到 UP 都不能重新提交。
                if (receiving != null) receiving.run();
                if (action != null) action.accept(placement.legalDrop(node, geometry, value, box));
            }
        }
        @Override public void pointer(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            syncObserver(node, options());
            releaseEventTime = event.timeNanos;
            switch (event.kind) {
                case DOWN: beginPointer(node, event, geometry); break;
                case MOVE: continuePointer(node, event, geometry); break;
                case UP: finishPointer(node, event, geometry); break;
                default: break;
            }
        }
        private void beginPointer(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            SceneGeometryView.Entry entry = geometry.get(sourceContent());
            if (entry == null) return;
            release.begin();
            state.end(); state.keyboard = false; state.delayAllowed = true;
            state.startX = state.pointerX = event.x; state.startY = state.pointerY = event.y;
            state.grabX = event.x - entry.box.x; state.grabY = event.y - entry.box.y;
            state.pressedNanos = runtime.inputNanos();
            Options options = options();
            new DragGeometry(node, geometry, SceneDragSupport.AutoScroll.Shift.NONE, geometry.get(node).box, entry.box).preserveElasticGrab(entry.box);
            if (options.handles.length > 0 && !options.activationExplicit || options.distance == 0 && options.delayMillis == 0) activate(state.pressedNanos);
            else state.publish(false, true, 0, 0);
            update(node, geometry, SceneDragSupport.AutoScroll.Shift.NONE);
            Offset point = state.preferred.get(); release.sample(point.x, point.y, event.timeNanos);
        }
        private void continuePointer(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            Snapshot current = state.snapshot.get();
            if (state.keyboard || !current.active && !current.pending) return;
            state.pointerX = event.x; state.pointerY = event.y;
            if (current.pending) pending(runtime.inputNanos());
            update(node, geometry, SceneDragSupport.AutoScroll.Shift.NONE);
            Offset point = state.preferred.get(); release.sample(point.x, point.y, event.timeNanos);
        }
        @Override public void preparePointerRelease(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            syncObserver(node, options());
            if (state.keyboard || !state.snapshot.get().active) return;
            state.pointerX = event.x; state.pointerY = event.y;
            // 只声明最终坐标意图；正式层限位与排列由框架在派 UP 之前完成。
            preferPosition(node, geometry, SceneDragSupport.AutoScroll.Shift.NONE);
        }
        private void finishPointer(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            if (state.keyboard) return;
            state.pointerX = event.x; state.pointerY = event.y;
            // 普通预览的 UP 只消费已发布盒；排序继续在自身正式控制器中结算位置意图。
            if (options().preserveSlot && options().overlay == null) update(node, geometry, SceneDragSupport.AutoScroll.Shift.NONE);
            Offset preferred = state.preferred.get();
            release.sample(preferred.x, preferred.y, event.timeNanos);
            drop(node, geometry);
        }
        @Override public boolean edit(SceneNode node, SceneEditEvent event, SceneGeometryView geometry) {
            if (!SceneDragSupport.dragCommand(event)) return false;
            try { return editDrag(node, event, geometry); }
            catch (RuntimeException | Error failure) {
                // 编辑事件尚未加入本批输入帧，失败边界只能在本控件结束刚拾取的键盘会话。
                try { state.end(); }
                catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
        private boolean editDrag(SceneNode node, SceneEditEvent event, SceneGeometryView geometry) {
            syncObserver(node, options());
            switch (event.command) {
                case ACTIVATE_ONLY: case SPACE: return toggleKeyboard(node, geometry);
                case ESCAPE: return cancelKeyboard();
                case LEFT: case RIGHT: case UP: case DOWN: return navigateKeyboard(node, event.command, geometry);
                default: return false;
            }
        }
        private boolean toggleKeyboard(SceneNode node, SceneGeometryView geometry) {
            Snapshot current = state.snapshot.get();
            if (!current.active && !current.pending) beginKeyboard(node, geometry);
            else if (state.keyboard) {
                if (options().preserveSlot && options().overlay == null) update(node, geometry, SceneDragSupport.AutoScroll.Shift.NONE);
                drop(node, geometry);
            }
            return true;
        }
        private void beginKeyboard(SceneNode node, SceneGeometryView geometry) {
            Offset takeover = keyboardTakeover(node, geometry);
            release.begin();
            state.end(); state.keyboard = true; activate(runtime.inputNanos());
            state.keyboardX = takeover.x; state.keyboardY = takeover.y; state.preferred.set(takeover);
            if (geometry != null) update(node, geometry, SceneDragSupport.AutoScroll.Shift.NONE);
        }
        private Offset keyboardTakeover(SceneNode node, SceneGeometryView geometry) {
            if (!release.active.get() || geometry == null) return Offset.ZERO;
            LayoutBox current = feedbackBox(node, geometry);
            SceneGeometryView.Entry slot = geometry.get(node);
            if (current == null || slot == null) return Offset.ZERO;
            return new Offset(current.x - slot.box.x - node.padding(), current.y - slot.box.y - node.padding());
        }
        private boolean cancelKeyboard() {
            Snapshot current = state.snapshot.get();
            if (!state.keyboard || !current.active && !current.pending) return false;
            state.end(); return true;
        }
        private boolean navigateKeyboard(SceneNode node, SceneEditEvent.Command direction, SceneGeometryView geometry) {
            if (!state.snapshot.get().active || !state.keyboard) return false;
            if (observer == null || geometry == null) moveKeyboard(direction);
            else if (!navigationAllowed(direction)) return true;
            else observer.keyboardTarget(node, geometry, direction);
            if (geometry != null) update(node, geometry, SceneDragSupport.AutoScroll.Shift.NONE);
            return true;
        }
        private boolean navigationAllowed(SceneEditEvent.Command direction) {
            boolean horizontal = direction == SceneEditEvent.Command.LEFT || direction == SceneEditEvent.Command.RIGHT;
            return horizontal ? options().axis != Axis.Y : options().axis != Axis.X;
        }
        private void moveKeyboard(SceneEditEvent.Command direction) {
            state.keyboardX = SceneDragSupport.integer((long) state.keyboardX
                    + (direction == SceneEditEvent.Command.LEFT ? -25 : direction == SceneEditEvent.Command.RIGHT ? 25 : 0));
            state.keyboardY = SceneDragSupport.integer((long) state.keyboardY
                    + (direction == SceneEditEvent.Command.UP ? -25 : direction == SceneEditEvent.Command.DOWN ? 25 : 0));
        }
        @Override public long nextInputNanos(SceneNode node) {
            Snapshot current = state.snapshot.get(); Options options = options();
            if (current.active) return 0;
            return current.pending && state.delayAllowed && options.delayMillis > 0
                    ? state.pressedNanos + options.delayMillis * 1_000_000L : Long.MAX_VALUE;
        }
        @Override public void inputFrame(SceneNode node, SceneGeometryView geometry, long now) {
            syncObserver(node, options());
            if (!valid(node, geometry)) { state.end(); return; }
            if (state.keyboard) {
                SceneNode focused = runtime.focusRouter().focused().get();
                if (focused != node && !SceneDragSupport.handleFocus(node, options().handles, focused)) { state.end(); return; }
            }
            if (state.snapshot.get().pending) pending(now);
            if (!state.snapshot.get().active) return;
            update(node, geometry, SceneDragSupport.AutoScroll.Shift.NONE);
            if (!state.snapshot.get().active) return;
            Options options = options();
            SceneNode scrollSource = observer == null ? node.parent() : observer.scrollSource(node, geometry);
            if (!state.keyboard && options.autoScroll)
                state.scrolling.advance(runtime, scrollSource, geometry, state.pointerX, state.pointerY, now,
                        options.axis != Axis.Y, options.axis != Axis.X, options.autoScrollSpeed);
            // 滚动已使正式几何失效；候选不能在旧 clip/box 上推算，由重排后的几何同步更新。
        }
        @Override public void inputGeometryChanged(SceneNode node, SceneGeometryView geometry) {
            Snapshot current = state.snapshot.get();
            if (!current.active && !current.pending) return;
            syncObserver(node, options());
            if (!valid(node, geometry)) { state.end(); return; }
            if (state.snapshot.get().active) update(node, geometry, SceneDragSupport.AutoScroll.Shift.NONE);
        }
        @Override public void focusChanged(SceneNode node, boolean focused, boolean eligible) { if (!focused) state.end(); }
        @Override public boolean cancelCaptureOnEscape(SceneNode node) {
            Snapshot current = state.snapshot.get(); return !state.keyboard && (current.active || current.pending);
        }
        @Override public void captureLost(SceneNode node) { release.stop(); state.end(); }
        @Override public void inputFrameCancelled(SceneNode node) { release.stop(); state.end(); }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {}
    }
}
