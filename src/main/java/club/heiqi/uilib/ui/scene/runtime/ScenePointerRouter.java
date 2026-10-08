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
    private int x, y;
    private boolean closed;
    ScenePointerRouter(SceneRuntime runtime) { this.runtime = runtime; }
    /** 即使本帧没有事件，也检查来源更新后的owner资格；失活后本段不再恢复。 */
    public void prepare(SceneGeometryView geometry) {
        checkAccess();
        revalidate();
        if (owner != null && geometry.get(owner.node()) == null) owner = null;
    }
    /** 生命周期/声明已生效时即时复核当前owner，不依赖几何重建。
     * 只检查当前owner及既有有效范围；失活随后恢复也不会复活本段。 */
    void revalidate() {
        runtime.domain().checkAccess();
        if (owner != null && (!owner.active() || !runtime.interactive(owner.node()) || owner.node().clickAction() == null)) owner = null;
    }
    /** 按采集顺序处理一个事件。仅原owner按钮在有效目标位置UP时发布click。 */
    public void route(ScenePointerEvent event, SceneGeometryView geometry) {
        checkAccess();
        prepare(geometry);
        switch (event.kind) {
            case DOWN:
                x = event.x; y = event.y;
                boolean idle = buttons.isEmpty();
                if (!buttons.add(event.button)) return;
                if (idle && event.button == 0) {
                    SceneNode hit = hits.hit(runtime, geometry, x, y);
                    if (hit != null) owner = runtime.captureMount(hit);
                }
                return;
            case MOVE:
                x = event.x; y = event.y;
                return;
            case UP:
                x = event.x; y = event.y;
                if (!buttons.remove(event.button)) return;
                if (event.button != 0 || owner == null) return;
                MountHandle activation = owner;
                owner = null; // 在handler前结束owner；业务异常不能留下可复活的目标。
                if (activation.active() && hits.hit(runtime, geometry, x, y) == activation.node()) runtime.dispatchPointerClick(activation.node());
                return;
            case CANCEL:
                owner = null;
                return;
            case ALL_BUTTONS_RELEASED:
                owner = null; buttons.clear();
                return;
            case SCROLL:
                SceneNode scrolling = hits.hitScroll(runtime, geometry, event.x, event.y);
                if (scrolling != null) runtime.dispatchScroll(scrolling, event.delta);
                return;
            case SYNTHETIC_ACTIVATION:
                if (!buttons.isEmpty()) return;
                SceneNode target = hits.hit(runtime, geometry, event.x, event.y);
                if (target != null) runtime.dispatchClick(target);
                return;
            default: throw new AssertionError(event.kind);
        }
    }
    /** 本批次handler失败后只记物理尾部，不再执行业务handler；原异常由管线上抛。
     * 已采集第二DOWN不能因为中断批次而丢失，否则下一帧会误建owner。 */
    public void discardAfterFailure(ScenePointerEvent event) {
        runtime.domain().checkAccess();
        owner = null;
        if (closed) return;
        switch (event.kind) {
            case DOWN: buttons.add(event.button); break;
            case UP: buttons.remove(event.button); break;
            case ALL_BUTTONS_RELEASED: buttons.clear(); break;
            default: break;
        }
    }
    boolean idle() { runtime.domain().checkAccess(); return !closed && buttons.isEmpty(); }
    private void checkAccess() {
        runtime.domain().checkAccess();
        if (closed) throw new IllegalStateException("指针路由器已释放");
    }
    /** runtime关闭时解除目标与按钮引用，不提交业务意图。 */
    @Override public void close() { runtime.domain().checkAccess(); owner = null; buttons.clear(); closed = true; }
}
