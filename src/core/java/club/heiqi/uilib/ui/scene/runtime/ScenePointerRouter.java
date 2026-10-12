package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.input.SceneHitTester;

import java.util.HashSet;
import java.util.Set;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** runtime唯一的指针手势权威，复用现有HitTester与挂载token，不维护第二棵树。
 * 所有操作在runtime的ReactiveDomain内；调用route前由帧管线prepare同代几何。
 * owner失活只释放目标，物理集合继续保留；因此第二键尾部不能变成新手势。 */
public final class ScenePointerRouter implements AutoCloseable {
    private final SceneRuntime runtime;
    private final SceneHitTester hits = new SceneHitTester();
    private final Set<Integer> buttons = new HashSet<>();
    private MountHandle owner;
    private Object ownerTarget;
    private int x, y;
    private boolean closed, positionKnown;

    /** 悬停只是已有指针位置在当前范围的投影，不建立另一个输入owner。 */
    public SceneNode hovered(SceneGeometryView geometry) {
        runtime.domain().checkAccess();
        return closed || !positionKnown ? null : hits.hit(runtime, geometry, x, y);
    }

    /** 与 click 命中分开；仅投影当前指针、范围及同代 geometry。 */
    public SceneNode passiveHovered(SceneGeometryView geometry) {
        runtime.domain().checkAccess();
        return closed || !positionKnown ? null : hits.passive(runtime, geometry, x, y);
    }

    public boolean pressed(SceneNode node) {
        runtime.domain().checkAccess();
        return !closed && owner != null && owner.active() && owner.node() == node;
    }

    ScenePointerRouter(SceneRuntime runtime) {
        this.runtime = runtime;
    }

    /** owner是按下呈现的变化来源；所有释放出口只失效paint，保留物理尾部。 */
    private void setOwner(MountHandle target) {
        if (owner == target) return;
        owner = target;
        if (target == null) ownerTarget = null;
        runtime.invalidatePaint();
    }

    /** 即使本帧没有事件，也检查来源更新后的owner资格；失活后本段不再恢复。 */
    public void prepare(SceneGeometryView geometry) {
        checkAccess();
        revalidate();
        if (owner != null && geometry.get(owner.node()) == null) setOwner(null);
    }

    /** 生命周期/声明已生效时即时复核当前owner，不依赖几何重建。
     * 只检查当前owner及既有有效范围；失活随后恢复也不会复活本段。 */
    void revalidate() {
        runtime.domain().checkAccess();
        if (owner != null
                && (!owner.active()
                        || !runtime.interactive(owner.node())
                        || !runtime.pointerTargetValid(owner.node(), ownerTarget))) setOwner(null);
    }

    /** 按采集顺序处理一个事件。仅原owner按钮在有效目标位置UP时发布click。 */
    public void route(ScenePointerEvent event, SceneGeometryView geometry) {
        checkAccess();
        prepare(geometry);
        // 交互投影更新只使paint失效，保持当前几何。
        if (event.kind == ScenePointerEvent.Kind.CANCEL) {
            positionKnown = false;
            runtime.invalidatePaint();
        } else if (event.kind != ScenePointerEvent.Kind.ALL_BUTTONS_RELEASED) {
            boolean changed = !positionKnown || x != event.x || y != event.y;
            positionKnown = true;
            x = event.x;
            y = event.y;
            if (changed
                    || event.kind == ScenePointerEvent.Kind.DOWN
                    || event.kind == ScenePointerEvent.Kind.UP) runtime.invalidatePaint();
        }
        switch (event.kind) {
            case DOWN:
                x = event.x;
                y = event.y;
                boolean idle = buttons.isEmpty();
                if (!buttons.add(event.button)) return;
                if (idle && event.button == 0) {
                    SceneHitTester.Target target = hits.target(runtime, geometry, x, y);
                    if (target != null) {
                        SceneNode hit = target.node;
                        ownerTarget = target.identity;
                        setOwner(runtime.captureMount(hit));
                        if (hit.control() != null && hit.control().capturesPointer()) {
                            if (hit.clickFocus() && hit.focusable())
                                runtime.focusRouter().request(runtime.captureMount(hit));
                            runtime.dispatchControlPointer(hit, event, geometry);
                        }
                    } else {
                        blurOutsideFocus(geometry);
                    }
                }
                return;
            case MOVE:
                x = event.x;
                y = event.y;
                if (owner != null && owner.active())
                    runtime.dispatchControlPointer(owner.node(), event, geometry);
                return;
            case UP:
                x = event.x;
                y = event.y;
                if (!buttons.remove(event.button)) return;
                if (event.button != 0 || owner == null) return;
                MountHandle activation = owner;
                Object identity = ownerTarget;
                setOwner(null); // 在handler前结束owner；业务异常不能留下可复活的目标。
                if (activation.active())
                    runtime.dispatchControlPointer(activation.node(), event, geometry);
                SceneHitTester.Target release = hits.target(runtime, geometry, x, y);
                if (activation.active()
                        && release != null
                        && release.node == activation.node()
                        && release.identity == identity)
                    runtime.dispatchPointerTarget(activation.node(), identity);
                return;
            case CANCEL:
                setOwner(null);
                return;
            case ALL_BUTTONS_RELEASED:
                setOwner(null);
                buttons.clear();
                return;
            case SCROLL:
                SceneNode scrolling = hits.hitScroll(runtime, geometry, event.x, event.y);
                if (scrolling != null && !runtime.dispatchControlScroll(scrolling, event, geometry))
                    runtime.dispatchScroll(scrolling, event.delta);
                return;
            case SYNTHETIC_ACTIVATION:
                if (!buttons.isEmpty()) return;
                SceneHitTester.Target target = hits.target(runtime, geometry, event.x, event.y);
                if (target != null) runtime.dispatchPointerTarget(target.node, target.identity);
                return;
            default:
                throw new AssertionError(event.kind);
        }
    }

    /** 仅首次主键空命中离开；保留物理占位，尾部不能激活拖入的邻控件。
     * clip 与 viewport 均来自同代几何，裁掉的旧 box 不能阻止正常失焦。 */
    private void blurOutsideFocus(SceneGeometryView geometry) {
        if (!geometry.viewport().contains(x, y)) return;
        SceneNode modal = runtime.activeModal();
        if (modal != null && !inside(geometry.get(modal))) return;
        SceneNode focused = runtime.focusRouter().focused().get();
        if (focused != null && !inside(geometry.get(focused))) runtime.focusRouter().clear();
    }

    private boolean inside(SceneGeometryView.Entry entry) {
        return entry != null && entry.box.contains(x, y)
                && (entry.clip == null || entry.clip.contains(x, y));
    }

    /** 本批次handler失败后只记物理尾部，不再执行业务handler；原异常由管线上抛。
     * 已采集第二DOWN不能因为中断批次而丢失，否则下一帧会误建owner。 */
    public void discardAfterFailure(ScenePointerEvent event) {
        runtime.domain().checkAccess();
        setOwner(null);
        if (closed) return;
        switch (event.kind) {
            case DOWN:
                buttons.add(event.button);
                break;
            case UP:
                buttons.remove(event.button);
                break;
            case ALL_BUTTONS_RELEASED:
                buttons.clear();
                break;
            default:
                break;
        }
    }

    boolean idle() {
        runtime.domain().checkAccess();
        return !closed && buttons.isEmpty();
    }

    private void checkAccess() {
        runtime.domain().checkAccess();
        if (closed) throw new IllegalStateException("指针路由器已释放");
    }

    /** runtime关闭时解除目标与按钮引用，不提交业务意图。 */
    @Override
    public void close() {
        runtime.domain().checkAccess();
        setOwner(null);
        buttons.clear();
        closed = true;
    }
}
