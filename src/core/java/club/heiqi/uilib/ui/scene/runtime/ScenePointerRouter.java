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
    private final SceneWheelRouting wheel;
    private final Set<Integer> buttons = new HashSet<>();
    private MountHandle owner;
    private Object ownerTarget;
    private int x, y;
    private boolean closed, positionKnown;
    private long positionRevision;
    private final HoverProjection hover = new HoverProjection(), passive = new HoverProjection();
    private SceneGeometryView preparedGeometry;
    private final java.util.Set<HoverInterest> hoverInterests = new java.util.LinkedHashSet<>();
    private final java.util.Map<SceneNode, Integer> hoverNodes = new java.util.IdentityHashMap<>();

    /** 只订阅正式被动命中到逻辑祖先的投影，不建立新的区域或手势owner。 */
    final class HoverInterest implements AutoCloseable {
        final SceneNode node;
        final Runnable changed;
        boolean inside;
        HoverInterest(SceneNode node,Runnable changed) {this.node=node;this.changed=changed;}
        @Override public void close() {
            if (!hoverInterests.remove(this)) return;
            int count = hoverNodes.get(node);
            if (count == 1) hoverNodes.remove(node); else hoverNodes.put(node, count - 1);
            passive.clear();
        }
    }
    HoverInterest observeHover(SceneNode node,Runnable changed) {
        HoverInterest interest=new HoverInterest(node,changed);hoverInterests.add(interest);
        hoverNodes.put(node, hoverNodes.getOrDefault(node, 0) + 1); passive.clear(); return interest;
    }
    private void publishHover(SceneGeometryView geometry) {
        if(hoverInterests.isEmpty())return;
        SceneNode target = closed || !positionKnown ? null : passive.within(geometry);
        for(HoverInterest interest:new java.util.ArrayList<>(hoverInterests)) {
            boolean inside=false;
            for(SceneNode cursor=target;cursor!=null;cursor=cursor.parent())
                if(cursor==interest.node) {inside=true;break;}
            if(interest.inside!=inside) {interest.inside=inside;interest.changed.run();}
        }
    }

    /** 多个控件在同一呈现中读取同一指针投影；不能为每个文字重新扫描整页和模态树。
     * 几何身份、声明/交互代际和坐标共同失效，仍由正式HitTester裁决。只保留各一种当前查询。 */
    private final class HoverProjection {
        private SceneGeometryView geometry;
        private long revision;
        private int px, py;
        private SceneNode node, within;
        SceneNode within(SceneGeometryView next) { get(next, true); return within; }
        SceneNode get(SceneGeometryView next, boolean passiveQuery) {
            long nextRevision = runtime.revision();
            if (geometry != next || revision != nextRevision || px != x || py != y) {
                if (passiveQuery) {
                    SceneHitTester.PassiveTargets targets = hits.passiveTargets(runtime, next, x, y, hoverNodes::containsKey);
                    node = targets.control; within = targets.within;
                } else node = hits.hit(runtime, next, x, y);
                geometry = next; revision = nextRevision; px = x; py = y;
            }
            return node;
        }
        void clear() { geometry = null; node = null; within = null; }
    }

    /** 悬停只是已有指针位置在当前范围的投影，不建立另一个输入owner。 */
    public SceneNode hovered(SceneGeometryView geometry) {
        runtime.domain().checkAccess();
        return closed || !positionKnown ? null : hover.get(geometry, false);
    }

    /** 与 click 命中分开；仅投影当前指针、范围及同代 geometry。 */
    public SceneNode passiveHovered(SceneGeometryView geometry) {
        runtime.domain().checkAccess();
        return closed || !positionKnown ? null : passive.get(geometry, true);
    }

    long positionRevision(){return positionRevision;}
    SceneNode tooltipHovered(SceneGeometryView geometry){
        if(closed||!positionKnown)return null;
        return passive.within(geometry);
    }
    /** 只观察已发布的提示矩形与连接走廊；不赋予输入资格，也不改变click/wheel命中。 */
    boolean observesTooltip(club.heiqi.uilib.ui.scene.layout.LayoutBox anchor,club.heiqi.uilib.ui.scene.layout.LayoutBox popup){
        if(closed||!positionKnown)return false;
        if(popup.contains(x,y))return true;
        if(popup.y+popup.height<=anchor.y||popup.y>=anchor.y+anchor.height){
            int lo=Math.min(popup.y+popup.height,anchor.y+anchor.height),hi=Math.max(popup.y,anchor.y);
            int left=Math.min(popup.x,anchor.x),right=Math.max(popup.x+popup.width,anchor.x+anchor.width);
            return y>=lo&&y<=hi&&x>=left&&x<=right;
        }
        int lo=Math.min(popup.x+popup.width,anchor.x+anchor.width),hi=Math.max(popup.x,anchor.x);
        int top=Math.min(popup.y,anchor.y),bottom=Math.max(popup.y+popup.height,anchor.y+anchor.height);
        return x>=lo&&x<=hi&&y>=top&&y<=bottom;
    }

    public boolean pressed(SceneNode node) {
        runtime.domain().checkAccess();
        return !closed && owner != null && owner.active() && owner.node() == node;
    }

    ScenePointerRouter(SceneRuntime runtime) {
        this.runtime = runtime;
        wheel = new SceneWheelRouting(runtime, hits);
    }

    /** owner是按下呈现的变化来源；所有释放出口只失效paint，保留物理尾部。 */
    private void setOwner(MountHandle target) {
        if (owner == target) return;
        owner = target;
        if (target == null) ownerTarget = null;
        runtime.invalidatePaint();
    }

    /** 先结束唯一 owner，再通知内部清理；状态失效引发重验也不会重复取消。 */
    private void cancelOwner() {
        MountHandle cancelled = owner;
        setOwner(null);
        if (cancelled != null) runtime.dispatchControlCaptureLost(cancelled.node());
    }

    /** 即使本帧没有事件，也检查来源更新后的owner资格；失活后本段不再恢复。 */
    public void prepare(SceneGeometryView geometry) {
        checkAccess();
        if (preparedGeometry != geometry) { hover.clear(); passive.clear(); preparedGeometry = geometry; }
        revalidate();
        if (owner != null && geometry.get(owner.node()) == null) cancelOwner();
        wheel.prepare(geometry, runtime.inputNanos());
        publishHover(geometry);
    }

    /** 生命周期/声明已生效时即时复核当前owner，不依赖几何重建。
     * 只检查当前owner及既有有效范围；失活随后恢复也不会复活本段。 */
    void revalidate() {
        runtime.domain().checkAccess();
        wheel.revalidate();
        if (owner != null
                && (!owner.active()
                        || !runtime.interactive(owner.node())
                        || !runtime.pointerTargetValid(owner.node(), ownerTarget))) cancelOwner();
    }

    /** 释放坐标先交给原 owner 声明位置；不结束物理段、不合成 MOVE、不建立新 owner。
     * 帧管线在此操作及其 Effect 结算后重新 prepare，再沿 routeUp 正常结束。 */
    public void prepareRelease(ScenePointerEvent event, SceneGeometryView geometry) {
        checkAccess();
        prepare(geometry);
        if (event.kind != ScenePointerEvent.Kind.UP || event.button != 0
                || !buttons.contains(event.button) || owner == null || !owner.active()) return;
        updatePointerPosition(event);
        runtime.prepareControlPointerRelease(owner.node(), event, geometry);
    }

    /** 按采集顺序准备当前范围并分派事件，所有阶段共用本路由器的唯一手势状态。 */
    public void route(ScenePointerEvent event, SceneGeometryView geometry) {
        checkAccess();
        prepare(geometry);
        if (event.kind == ScenePointerEvent.Kind.MOVE || event.kind == ScenePointerEvent.Kind.SCROLL)
            wheel.pointerMoved(event.x, event.y);
        else wheel.clear();
        updatePointerPosition(event);
        if(event.kind==ScenePointerEvent.Kind.MOVE||event.kind==ScenePointerEvent.Kind.CANCEL)positionRevision++;
        if(event.kind==ScenePointerEvent.Kind.DOWN||event.kind==ScenePointerEvent.Kind.SYNTHETIC_ACTIVATION)runtime.tooltips().pointerPressed();
        try {
            switch (event.kind) {
                case DOWN:
                    routeDown(event, geometry);
                    return;
                case MOVE:
                    routeMove(event, geometry);
                    return;
                case UP:
                    routeUp(event, geometry);
                    return;
                case CANCEL:
                    cancelOwner();
                    return;
                case ALL_BUTTONS_RELEASED:
                    releasePhysicalButtons();
                    return;
                case SCROLL:
                    routeScroll(event, geometry);
                    return;
                case SYNTHETIC_ACTIVATION:
                    routeSyntheticActivation(event, geometry);
                    return;
                default:
                    throw new AssertionError(event.kind);
            }
        } finally {
            publishHover(geometry);
        }
    }

    /** 交互投影只使绘制失效；取消丢弃位置，物理全部释放仍保留最后位置。 */
    private void updatePointerPosition(ScenePointerEvent event) {
        if (event.kind == ScenePointerEvent.Kind.CANCEL) {
            positionKnown = false;
            hover.clear();
            passive.clear();
            runtime.invalidatePaint();
            return;
        }
        if (event.kind == ScenePointerEvent.Kind.ALL_BUTTONS_RELEASED) return;
        boolean changed = !positionKnown || x != event.x || y != event.y;
        positionKnown = true;
        x = event.x;
        y = event.y;
        if (changed || event.kind == ScenePointerEvent.Kind.DOWN || event.kind == ScenePointerEvent.Kind.UP)
            runtime.invalidatePaint();
    }

    /** 每次新按下都占物理位置，只有空闲时的主键能建立手势或清除外部焦点。 */
    private void routeDown(ScenePointerEvent event, SceneGeometryView geometry) {
        boolean idle = buttons.isEmpty();
        if (!buttons.add(event.button)) return;
        if (!idle || event.button != 0) return;
        SceneHitTester.Target target = hits.target(runtime, geometry, x, y);
        if (target == null) {
            blurOutsideFocus(geometry);
            return;
        }
        runtime.focusRouter().pointerInput();
        SceneNode captured = capturePointerTarget(target, geometry);
        dispatchCapturedDown(captured, event, geometry);
    }

    /** 只沿命中子树选择明确的祖先 activator；无声明时保留原子控件及其目标身份。 */
    private SceneNode capturePointerTarget(SceneHitTester.Target target, SceneGeometryView geometry) {
        SceneNode hit = target.node;
        Object identity = target.identity;
        for (SceneNode ancestor = hit.parent(); ancestor != null; ancestor = ancestor.parent()) {
            if (!runtime.interactive(ancestor) || ancestor.control() == null) continue;
            SceneNode candidate = ancestor;
            Object delegated = runtime.domain().readOnly(() ->
                    candidate.control().pointerActivator(candidate, target.node, geometry, x, y));
            if (delegated != null && runtime.pointerTargetValid(candidate, delegated)) {
                hit = candidate;
                identity = delegated;
                break;
            }
        }
        ownerTarget = identity;
        setOwner(runtime.captureMount(hit));
        return hit;
    }

    private void dispatchCapturedDown(SceneNode captured, ScenePointerEvent event, SceneGeometryView geometry) {
        if (captured.control() == null || !captured.control().capturesPointer()) return;
        if (captured.clickFocus() && captured.focusable())
            runtime.focusRouter().request(runtime.captureMount(captured));
        runtime.dispatchControlPointer(captured, event, geometry);
    }

    private void routeMove(ScenePointerEvent event, SceneGeometryView geometry) {
        if (owner != null && owner.active())
            runtime.dispatchControlPointer(owner.node(), event, geometry);
    }

    /** 仅已知主键的原目标可激活；handler 前结束 owner，异常也不能留下可复活联系。 */
    private void routeUp(ScenePointerEvent event, SceneGeometryView geometry) {
        if (!buttons.remove(event.button)) return;
        if (event.button != 0 || owner == null) return;
        MountHandle activation = owner;
        Object identity = ownerTarget;
        setOwner(null);
        if (activation.active())
            runtime.dispatchControlPointer(activation.node(), event, geometry);
        SceneHitTester.Target release = hits.target(runtime, geometry, x, y);
        if (activation.active()
                && release != null
                && release.node == activation.node()
                && release.identity == identity)
            runtime.dispatchPointerTarget(activation.node(), identity);
    }

    private void releasePhysicalButtons() {
        try {
            cancelOwner();
        } finally {
            buttons.clear();
        }
    }

    private void routeScroll(ScenePointerEvent event, SceneGeometryView geometry) {
        wheel.route(event, geometry);
    }

    private void routeSyntheticActivation(ScenePointerEvent event, SceneGeometryView geometry) {
        if (!buttons.isEmpty()) return;
        SceneHitTester.Target target = hits.target(runtime, geometry, event.x, event.y);
        if (target != null) runtime.dispatchPointerTarget(target.node, target.identity);
    }

    /** 仅首次主键空命中离开；保留物理占位，尾部不能激活拖入的邻控件。
     * clip 与 viewport 均来自同代几何，裁掉的旧 box 不能阻止正常失焦。 */
    private void blurOutsideFocus(SceneGeometryView geometry) {
        if (!geometry.viewport().contains(x, y)) return;
        SceneNode modal = runtime.activeModal();
        if (modal != null && !inside(geometry.get(modal))) {
            boolean owned = false;
            for (SceneGeometryView.Entry entry : geometry.ordered())
                if (geometry.layerBoundary(entry.node) != null && runtime.interactive(entry.node) && inside(entry)) {
                    owned = true;
                    break;
                }
            if (!owned) return;
        }
        runtime.focusRouter().pointerInput();
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
        wheel.clear();
        try { cancelOwner(); }
        finally {
            // 清理扩展失败也保留物理尾部，不能让第二键变成后来的新手势。
            if (!closed) {
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
        }
    }

    /** 当前范围的捕获可明确消费 ESC；保留物理占位，后续 MOVE/UP 不能复活。 */
    boolean cancelOnEscape() {
        checkAccess();
        wheel.clear();
        revalidate();
        if (owner == null || owner.node().control() == null
                || !owner.node().control().cancelCaptureOnEscape(owner.node())) return false;
        cancelOwner();
        return true;
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
        wheel.clear();
        try { cancelOwner(); }
        finally {
            buttons.clear();
            hover.clear(); passive.clear(); preparedGeometry = null;
            hoverInterests.clear(); hoverNodes.clear();
            closed = true;
        }
    }
}
