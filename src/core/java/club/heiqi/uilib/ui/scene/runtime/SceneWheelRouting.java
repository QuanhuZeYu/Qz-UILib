package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.input.SceneHitTester;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 唯一指针路由的滚轮协作者：连续输入保持实际消费者，只向祖先交接，不因内容移入指针而深入。 */
final class SceneWheelRouting {
    private static final long PAUSE_NANOS = 400_000_000L;
    private static final int MOVE_TOLERANCE = 8;
    private final SceneRuntime runtime;
    private final SceneHitTester hits;
    private MountHandle target;
    private SceneNode layer, scope, modal;
    private long lastInput;
    private int anchorX, anchorY;

    SceneWheelRouting(SceneRuntime runtime, SceneHitTester hits) { this.runtime = runtime; this.hits = hits; }

    void route(ScenePointerEvent event, SceneGeometryView geometry) {
        if (event.scrollPhase == ScenePointerEvent.ScrollPhase.END) {
            if (target != null && target.active()) runtime.scrollMotionRelease(target.node(), event);
            return;
        }
        if (event.delta == 0) return;
        long now = runtime.inputNanos();
        prepare(geometry, now);
        pointerMoved(event.x, event.y);
        SceneNode hit = hits.hitScroll(runtime, geometry, event.x, event.y);
        boolean continuing = continuation(hit, geometry);
        SceneNode start = continuing ? target.node() : hit;
        int originX = continuing ? anchorX : event.x, originY = continuing ? anchorY : event.y;
        if (continuing) clearLink(); else clear();
        if (start == null) return;
        SceneNode boundary = geometry.layerBoundary(start);
        MountHandle initial = runtime.captureMount(start);
        for (SceneNode node = start; node != null; node = node == boundary ? null : node.parent()) runtime.cancelScrollMotion(node);
        MountHandle consumer = consume(start, boundary, event, geometry);
        if (consumer != null) runtime.scrollMotionInput(consumer.node(), event);
        else for (SceneNode node = start; node != null; node = node == boundary ? null : node.parent())
            if (runtime.interactive(node) && runtime.scrollMotionBoundary(node, event)) break;
        remember(consumer == null ? initial : consumer, geometry, boundary, now, originX, originY);
    }

    /** 当前消费路径只向祖先；选区/联系保留与事件派发分开，回调失败仍保持旧联系已清理。 */
    private MountHandle consume(SceneNode start, SceneNode boundary, ScenePointerEvent event, SceneGeometryView geometry) {
        for (SceneNode node = start; node != null; node = node == boundary ? null : node.parent()) {
            if (!runtime.interactive(node) || !inside(geometry.get(node), event.x, event.y)) continue;
            // 控件可在回调中改变资格或树；消费前记录挂载，不能把迟到结果附到新挂载上。
            MountHandle consumer = runtime.captureMount(node);
            if (runtime.dispatchControlScroll(node, event, geometry).consumed || runtime.tryDispatchScroll(node, event.delta)) {
                return consumer;
            }
        }
        // 未有祖先消费时由调用者保留原起点，反向不重新掉回子区。
        return null;
    }

    private boolean continuation(SceneNode hit, SceneGeometryView geometry) {
        return target != null && hit != null && geometry.layerBoundary(hit) == layer
                && (within(hit, target.node()) || within(target.node(), hit));
    }

    private void remember(MountHandle consumer, SceneGeometryView geometry, SceneNode boundary, long now, int x, int y) {
        if (!consumer.active() || !runtime.interactive(consumer.node())) return;
        target = consumer; layer = boundary; scope = geometry.logicalScope(consumer.node()); modal = runtime.activeModal();
        lastInput = now; anchorX = x; anchorY = y;
    }

    void pointerMoved(int x, int y) {
        if (target != null && (Math.abs((long) x - anchorX) > MOVE_TOLERANCE
                || Math.abs((long) y - anchorY) > MOVE_TOLERANCE)) clear();
    }

    void revalidate() {
        if (target != null && (!target.active() || !runtime.interactive(target.node())
                || runtime.activeModal() != modal || !scrollable(target.node()))) clear();
    }

    void prepare(SceneGeometryView geometry, long now) {
        revalidate();
        if (target != null && (now - lastInput >= PAUSE_NANOS || geometry.get(target.node()) == null
                || geometry.layerBoundary(target.node()) != layer || geometry.logicalScope(target.node()) != scope)) clear();
    }

    void clear() {
        if (target != null) runtime.endScrollMotionSequence(target.node());
        clearLink();
    }
    private void clearLink() { target = null; layer = null; scope = null; modal = null; }

    private static boolean scrollable(SceneNode node) {
        return node.scrollable() || node.scrollAction() != null || node.control() != null && node.control().scrollableContent();
    }
    private static boolean within(SceneNode node, SceneNode ancestor) {
        for (SceneNode cursor = node; cursor != null; cursor = cursor.parent()) if (cursor == ancestor) return true;
        return false;
    }
    private static boolean inside(SceneGeometryView.Entry entry, int x, int y) {
        return entry != null && entry.box.contains(x, y) && (entry.clip == null || entry.clip.contains(x, y));
    }
}
