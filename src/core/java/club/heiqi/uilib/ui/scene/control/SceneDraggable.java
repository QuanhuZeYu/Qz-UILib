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
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 单内容的临时拖动；正常占位不变，移动内容仍进入唯一布局、绘制与输入几何。 */
public final class SceneDraggable {
    /** delta 使用 scene 像素：指针模式为起手位移，键盘模式按方向键累积；结束后归零。 */
    public static final class Snapshot {
        public final boolean active;
        public final int deltaX, deltaY;

        private Snapshot(boolean active, int deltaX, int deltaY) {
            this.active = active;
            this.deltaX = deltaX;
            this.deltaY = deltaY;
        }
    }

    /** 一个 State 对应一个拖动声明；外部只读观察，不持有节点或布局结果。 */
    public static final class State {
        private static final Snapshot INACTIVE = new Snapshot(false, 0, 0);
        private final ReactiveDomain domain;
        private final Signal<Snapshot> snapshot;
        private final ReadableSignal<Snapshot> view;
        private boolean bound, keyboard;
        private int startX, startY, pointerX, pointerY, grabX, grabY;

        private State(ReactiveDomain domain) {
            this.domain = domain;
            snapshot = domain.signal(INACTIVE);
            view = snapshot::get;
        }

        public ReadableSignal<Snapshot> snapshot() { return view; }

        private void begin(ScenePointerEvent event, LayoutBox content) {
            keyboard = false;
            startX = pointerX = event.x;
            startY = pointerY = event.y;
            grabX = event.x - content.x;
            grabY = event.y - content.y;
            snapshot.set(new Snapshot(true, 0, 0));
        }

        private void move(ScenePointerEvent event) {
            if (!snapshot.get().active || keyboard || pointerX == event.x && pointerY == event.y) return;
            pointerX = event.x;
            pointerY = event.y;
            snapshot.set(new Snapshot(true, pointerX - startX, pointerY - startY));
        }

        private void beginKeyboard() {
            keyboard = true;
            snapshot.set(new Snapshot(true, 0, 0));
        }

        private void step(int x, int y) {
            Snapshot current = snapshot.get();
            snapshot.set(new Snapshot(true, current.deltaX + x, current.deltaY + y));
        }

        private void end() {
            keyboard = false;
            startX = startY = pointerX = pointerY = grabX = grabY = 0;
            snapshot.set(INACTIVE);
        }
    }

    private SceneDraggable() {}

    public static State state(ReactiveDomain domain) {
        Objects.requireNonNull(domain, "domain").checkWrite();
        return new State(domain);
    }

    /** 内容保持原有样式与交互；内部按钮、编辑框按正式逆绘制序优先取得 owner。 */
    public static SceneNode create(ScenePageContext context, SceneNode content, State state) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(state, "state");
        if (context.domain() != state.domain)
            throw new IllegalArgumentException("拖动状态与控件必须同域");
        if (state.bound) throw new IllegalArgumentException("拖动状态已有所属声明");
        SceneNode node = new SceneNode().focusable(true).clickFocus(true).control(new Control(content, state)).add(content);
        state.bound = true;
        return node;
    }

    private static final class Control implements SceneControl {
        private final SceneNode content;
        private final State state;
        private final Object target = new Object();
        private static final int KEYBOARD_STEP = 25;

        private Control(SceneNode content, State state) {
            this.content = content;
            this.state = state;
        }

        @Override public List<ReadableSignal<?>> arrangementDependencies(SceneNode node) {
            return Collections.singletonList(state.snapshot);
        }

        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) {
            return new MeasuredSize(0, 0);
        }

        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text,
                                               SceneChildMeasurer children) {
            return SceneFlowLayout.measure(node, body, children);
        }

        @Override public ChildPlacements arrange(SceneNode node, LayoutBox allocated, SceneChildMeasurer children) {
            ChildPlacements ordinary = SceneFlowLayout.arrange(node, allocated, children);
            Snapshot current = state.snapshot.get();
            if (!current.active) return ordinary;
            List<ChildPlacements.Entry> placed = new ArrayList<>();
            for (ChildPlacements.Entry entry : ordinary.entries()) {
                LayoutBox box = entry.relativeBox;
                // 只保存指针和内容内抓取点；当前原点来自本批分配，滚动或重排不积累旧坐标。
                placed.add(new ChildPlacements.Entry(entry.child, new LayoutBox(
                        state.keyboard ? box.x + current.deltaX : state.pointerX - allocated.x - state.grabX,
                        state.keyboard ? box.y + current.deltaY : state.pointerY - allocated.y - state.grabY,
                        box.width, box.height)));
            }
            return new ChildPlacements(placed);
        }

        @Override public boolean capturesPointer() { return true; }

        @Override public Object pointerTarget(SceneNode node, SceneGeometryView geometry, int x, int y) {
            SceneGeometryView.Entry entry = geometry.get(content);
            // 包装槽始终占位；只能命中当前移动内容，不能在旧槽留下第二块热区。
            return pointerTargetValid(node, target) && entry != null && entry.box.contains(x, y)
                    && (entry.clip == null || entry.clip.contains(x, y)) ? target : null;
        }

        @Override public boolean pointerTargetValid(SceneNode node, Object candidate) {
            return candidate == target && content.parent() == node && content.visible() && !content.destroyed();
        }

        @Override public void pointer(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            switch (event.kind) {
                case DOWN:
                    SceneGeometryView.Entry entry = geometry.get(content);
                    if (entry != null) state.begin(event, entry.box);
                    break;
                case MOVE:
                    state.move(event);
                    break;
                case UP:
                    state.end();
                    break;
                default:
                    break;
            }
        }

        @Override public boolean edit(SceneNode node, SceneEditEvent event) {
            if (event.command == null || event.composing || event.compositionEvent) return false;
            Snapshot current = state.snapshot.get();
            switch (event.command) {
                case ACTIVATE_ONLY:
                case SPACE:
                    if (current.active) {
                        if (state.keyboard) state.end();
                    } else state.beginKeyboard();
                    return true;
                case ESCAPE:
                    // 指针取消由 router 处理；这里仅结束当前焦点的键盘手势。
                    if (!current.active || !state.keyboard) return false;
                    state.end();
                    return true;
                case LEFT:
                case RIGHT:
                case UP:
                case DOWN:
                    if (!current.active || !state.keyboard) return false;
                    state.step(event.command == SceneEditEvent.Command.LEFT ? -KEYBOARD_STEP
                                    : event.command == SceneEditEvent.Command.RIGHT ? KEYBOARD_STEP : 0,
                            event.command == SceneEditEvent.Command.UP ? -KEYBOARD_STEP
                                    : event.command == SceneEditEvent.Command.DOWN ? KEYBOARD_STEP : 0);
                    return true;
                default:
                    return false;
            }
        }

        @Override public void focusChanged(SceneNode node, boolean focused, boolean eligible) {
            // 起拖使用标准焦点归属；切换到另一输入目标时结束临时呈现，旧capture只继续吞物理尾部。
            if (!focused) state.end();
        }

        @Override public boolean cancelCaptureOnEscape(SceneNode node) {
            return state.snapshot.get().active && !state.keyboard;
        }

        @Override public void captureLost(SceneNode node) { state.end(); }

        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {}
    }
}
