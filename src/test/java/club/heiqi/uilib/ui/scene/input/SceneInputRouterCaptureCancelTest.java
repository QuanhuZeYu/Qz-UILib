package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SceneInputRouter 显式指针捕获 + POINTER_CANCEL 收口单元测试（I4d）。
 *
 * <p>覆盖：requestPointerCapture 强制投递、capture 期间 hover 仍跟 hitTarget、
 * UP 后自动释放、CANCEL 投递与状态清理、CANCEL 不合成 CLICK、
 * 零标脏回归、ctx.requestPointerCapture 语义。</p>
 */
public class SceneInputRouterCaptureCancelTest {

    private SceneInputRouter router;
    private InputFrameBuilder frameBuilder;
    private SceneRuntime runtime;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
        router = runtime.getInputRouter();
        frameBuilder = new InputFrameBuilder(0, 0);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    /** 构建单指针事件帧 */
    private SceneInputFrame buildFrame(ScenePointerAction action, int x, int y, SceneMouseButton button) {
        frameBuilder.push(RawInputEvent.ofPointer(action, x, y,
                button, 0, 0, 0,
                false, false, false, false, 1000L));
        return frameBuilder.drainFrame();
    }

    /** 构建两层树并设置 LayoutBox */
    private SceneNode buildTwoLayerTree() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);
        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        child.setCachedLayout(new LayoutBox(20, 20, 80, 60));
        return root;
    }

    /** 构建左右分开的两个子节点树 */
    private SceneNode[] buildSplitTree() {
        SceneNode root = new SceneNode();
        SceneNode btnA = new SceneNode();
        SceneNode btnB = new SceneNode();
        root.appendChild(btnA);
        root.appendChild(btnB);
        root.setCachedLayout(new LayoutBox(0, 0, 300, 300));
        btnA.setCachedLayout(new LayoutBox(10, 10, 50, 50));
        btnB.setCachedLayout(new LayoutBox(100, 100, 50, 50));
        return new SceneNode[]{root, btnA, btnB};
    }

    /** DFS 清所有脏标记 */
    private void clearAllDirtyRecursive(SceneNode node) {
        if (node == null) return;
        node.clearDirtyFlags();
        node.clearGeometryDirty();
        for (SceneNode child : node.__getChildren()) {
            clearAllDirtyRecursive(child);
        }
    }

    /** DFS 收集所有节点的 7 个脏探针 */
    private Map<SceneNode, boolean[]> collectDirtyProbes(SceneNode root) {
        Map<SceneNode, boolean[]> result = new HashMap<SceneNode, boolean[]>();
        collectDirtyProbesRecursive(root, result);
        return result;
    }

    private void collectDirtyProbesRecursive(SceneNode node, Map<SceneNode, boolean[]> result) {
        if (node == null) return;
        boolean[] probes = new boolean[7];
        probes[0] = node.__isSelfLayoutDirty();
        probes[1] = node.__isDescendantLayoutDirty();
        probes[2] = node.__isSelfPaintDirty();
        probes[3] = node.__isDescendantPaintDirty();
        probes[4] = node.__isCompositeDirty();
        probes[5] = node.__isSelfGeometryDirty();
        probes[6] = node.__isDescendantGeometryDirty();
        result.put(node, probes);
        for (SceneNode child : node.__getChildren()) {
            collectDirtyProbesRecursive(child, result);
        }
    }

    // ==================== 组 U：显式 capture 强制投递 ====================

    /**
     * U1：requestPointerCapture 后 MOVE 强制投 capturedNode（即使 hitTarget 是别的节点）。
     */
    @Test
    public void u1_captureMoveForcesDispatchToCapturedNode() {
        SceneNode[] tree = buildSplitTree();
        SceneNode root = tree[0];
        SceneNode btnA = tree[1];
        SceneNode btnB = tree[2];

        List<String> log = new ArrayList<String>();
        router.on(btnA, SceneEventType.POINTER_MOVE, (evt, ctx) -> log.add("move:A"));
        router.on(btnB, SceneEventType.POINTER_MOVE, (evt, ctx) -> log.add("move:B"));

        // 设置显式捕获到 btnA
        router.requestPointerCapture(btnA);

        // MOVE 到 btnB 区域 (110,110) —— 应强制投给 btnA
        SceneInputFrame frame = buildFrame(ScenePointerAction.MOVE, 110, 110, SceneMouseButton.NONE);
        router.route(root, frame, 0, 0);

        Assert.assertEquals("捕获下 MOVE 应投给 capturedNode btnA", 1, log.size());
        Assert.assertEquals("move:A", log.get(0));
    }

    /**
     * U2：capture 期间 hover 仍跟实际 hitTarget（不跟 capturedNode）。
     */
    @Test
    public void u2_captureHoverStillFollowsHitTarget() {
        SceneNode[] tree = buildSplitTree();
        SceneNode root = tree[0];
        SceneNode btnA = tree[1];
        SceneNode btnB = tree[2];

        ReadableSignal<Boolean> hoverA = router.interactionState(btnA).hovered();
        ReadableSignal<Boolean> hoverB = router.interactionState(btnB).hovered();

        // 设置显式捕获到 btnA
        router.requestPointerCapture(btnA);

        // MOVE 到 btnB 区域 —— hover 应跟 btnB（hitTarget），不跟 capturedNode btnA
        runtime.route(root, buildFrame(ScenePointerAction.MOVE, 110, 110, SceneMouseButton.NONE), 0, 0);
        runtime.flush();

        Assert.assertEquals("hoverA 应为 false（指针实际在 btnB 上）", Boolean.FALSE, hoverA.get());
        Assert.assertEquals("hoverB 应为 true（指针实际在 btnB 上）", Boolean.TRUE, hoverB.get());
    }

    /**
     * U3：UP 后 capturedNode 自动清空（D7-A 最小版释放）。
     */
    @Test
    public void u3_capturedNodeClearedAfterUp() {
        SceneNode[] tree = buildSplitTree();
        SceneNode root = tree[0];
        SceneNode btnA = tree[1];

        List<String> log = new ArrayList<String>();
        router.on(btnA, SceneEventType.POINTER_UP, (evt, ctx) -> log.add("up:A"));

        router.requestPointerCapture(btnA);

        // UP 在任意位置
        SceneInputFrame frame = buildFrame(ScenePointerAction.BUTTON_UP, 200, 200, SceneMouseButton.LEFT);
        router.route(root, frame, 0, 0);

        Assert.assertEquals("UP 应投给 capturedNode", 1, log.size());
        Assert.assertEquals("up:A", log.get(0));
        Assert.assertNull("UP 后 capturedNode 应清空", router.__getCapturedNode());
    }

    /**
     * U4：capturedNode 非 null 时，即使指针在树外（hitTarget=null），MOVE 仍投递。
     */
    @Test
    public void u4_captureMoveOutsideTreeStillDispatches() {
        SceneNode root = new SceneNode();
        SceneNode btnA = new SceneNode();
        root.appendChild(btnA);
        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        btnA.setCachedLayout(new LayoutBox(20, 20, 80, 60));

        List<String> log = new ArrayList<String>();
        router.on(btnA, SceneEventType.POINTER_MOVE, (evt, ctx) -> log.add("move:A"));

        router.requestPointerCapture(btnA);

        // MOVE 到树外
        SceneInputFrame frame = buildFrame(ScenePointerAction.MOVE, 250, 250, SceneMouseButton.NONE);
        router.route(root, frame, 0, 0);

        Assert.assertEquals("树外 MOVE 在捕获下仍应投递", 1, log.size());
        Assert.assertEquals("move:A", log.get(0));
    }

    // ==================== 组 V：CANCEL 投递与回滚 ====================

    /**
     * V1：CANCEL 投给 pressedNode。
     */
    @Test
    public void v1_cancelDispatchesToPressedNode() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        router.on(child, SceneEventType.POINTER_DOWN, (evt, ctx) -> log.add("down"));
        router.on(child, SceneEventType.POINTER_CANCEL, (evt, ctx) -> log.add("cancel"));

        // DOWN 命中 child
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT), 0, 0);
        Assert.assertEquals("DOWN 应派发", 1, log.size());
        log.clear();

        // CANCEL
        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.CANCEL,
                40, 40, SceneMouseButton.NONE,
                0, 0, 0, false, false, false, false, 2000L));
        SceneInputFrame cancelFrame = frameBuilder.drainFrame();
        router.route(root, cancelFrame, 0, 0);

        Assert.assertEquals("CANCEL 应投给 pressedNode", 1, log.size());
        Assert.assertEquals("cancel", log.get(0));
    }

    /**
     * V2：CANCEL 不合成 CLICK。
     */
    @Test
    public void v2_cancelDoesNotSynthesizeClick() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        router.on(child, SceneEventType.POINTER_DOWN, (evt, ctx) -> log.add("down"));
        router.on(child, SceneEventType.CLICK, (evt, ctx) -> log.add("click"));
        router.on(child, SceneEventType.POINTER_CANCEL, (evt, ctx) -> log.add("cancel"));

        // DOWN → CANCEL（无 UP）
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT), 0, 0);
        log.clear();

        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.CANCEL,
                40, 40, SceneMouseButton.NONE,
                0, 0, 0, false, false, false, false, 2000L));
        SceneInputFrame cancelFrame = frameBuilder.drainFrame();
        router.route(root, cancelFrame, 0, 0);

        Assert.assertEquals("CANCEL 后不应有 CLICK", 1, log.size());
        Assert.assertEquals("cancel", log.get(0));
    }

    /**
     * V3：CANCEL 后 pressed 翻 false + 清 pressedNode/capturedNode。
     */
    @Test
    public void v3_cancelClearsPressedStateAndCapturedNode() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        ReadableSignal<Boolean> pressedSig = router.interactionState(child).pressed();

        // DOWN 命中 child → pressed=true
        runtime.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT), 0, 0);
        runtime.flush();
        Assert.assertEquals("DOWN 后 pressed=true", Boolean.TRUE, pressedSig.get());
        Assert.assertNotNull("pressedNode 应非 null", router.__getPressedNode());

        // 设置 capturedNode 验证也会清空
        router.requestPointerCapture(child);
        Assert.assertNotNull("capturedNode 应非 null", router.__getCapturedNode());

        // CANCEL
        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.CANCEL,
                40, 40, SceneMouseButton.NONE,
                0, 0, 0, false, false, false, false, 2000L));
        SceneInputFrame cancelFrame = frameBuilder.drainFrame();
        runtime.route(root, cancelFrame, 0, 0);
        runtime.flush();

        Assert.assertNull("CANCEL 后 pressedNode 应清空", router.__getPressedNode());
        Assert.assertNull("CANCEL 后 capturedNode 应清空", router.__getCapturedNode());
        Assert.assertEquals("CANCEL 后 pressed signal=false（flush 后）", Boolean.FALSE, pressedSig.get());
    }

    /**
     * V4：CANCEL 投给 capturedNode（无 pressedNode 时）。
     */
    @Test
    public void v4_cancelDispatchesToCapturedNodeWhenNoPressedNode() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        router.on(child, SceneEventType.POINTER_CANCEL, (evt, ctx) -> log.add("cancel"));

        // 仅设置 capturedNode，无 pressedNode
        router.requestPointerCapture(child);

        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.CANCEL,
                40, 40, SceneMouseButton.NONE,
                0, 0, 0, false, false, false, false, 2000L));
        SceneInputFrame cancelFrame = frameBuilder.drainFrame();
        router.route(root, cancelFrame, 0, 0);

        Assert.assertEquals("CANCEL 应投给 capturedNode", 1, log.size());
        Assert.assertEquals("cancel", log.get(0));
    }

    /**
     * V5：CANCEL 时 pressedNode==capturedNode → 只投一次（去重）。
     */
    @Test
    public void v5_cancelDeduplicatesWhenSameNode() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        router.on(child, SceneEventType.POINTER_CANCEL, (evt, ctx) -> log.add("cancel"));

        // DOWN 设 pressedNode，再 requestPointerCapture 同节点
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT), 0, 0);
        router.requestPointerCapture(child);
        log.clear();

        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.CANCEL,
                40, 40, SceneMouseButton.NONE,
                0, 0, 0, false, false, false, false, 2000L));
        SceneInputFrame cancelFrame = frameBuilder.drainFrame();
        router.route(root, cancelFrame, 0, 0);

        Assert.assertEquals("同节点 CANCEL 只投一次（去重）", 1, log.size());
    }

    /**
     * V6：CANCEL 时 pressedNode≠capturedNode → 两者都投（去重通过不等）。
     */
    @Test
    public void v6_cancelDispatchesToBothWhenDifferentNodes() {
        SceneNode[] tree = buildSplitTree();
        SceneNode root = tree[0];
        SceneNode btnA = tree[1];
        SceneNode btnB = tree[2];

        List<String> log = new ArrayList<String>();
        router.on(btnA, SceneEventType.POINTER_CANCEL, (evt, ctx) -> log.add("cancel:A"));
        router.on(btnB, SceneEventType.POINTER_CANCEL, (evt, ctx) -> log.add("cancel:B"));

        // DOWN 在 btnA
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.LEFT), 0, 0);
        // capture btnB
        router.requestPointerCapture(btnB);
        log.clear();

        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.CANCEL,
                50, 50, SceneMouseButton.NONE,
                0, 0, 0, false, false, false, false, 2000L));
        SceneInputFrame cancelFrame = frameBuilder.drainFrame();
        router.route(root, cancelFrame, 0, 0);

        Assert.assertEquals("不同节点 CANCEL 两者都投", 2, log.size());
        Assert.assertTrue("btnB(capturedNode) 先投", log.get(0).contains("cancel:B"));
        Assert.assertTrue("btnA(pressedNode) 后投", log.get(1).contains("cancel:A"));
    }

    // ==================== 组 W：CANCEL 零标脏回归 ====================

    /**
     * W1：CANCEL route 后 flush 前 7 脏探针全等（零标脏 I7 核验）。
     */
    @Test
    public void w1_cancelRouteZeroDirtyProbes() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        SceneNode c = new SceneNode();
        root.appendChild(a);
        a.appendChild(b);
        b.appendChild(c);
        root.setCachedLayout(new LayoutBox(0, 0, 300, 300));
        a.setCachedLayout(new LayoutBox(10, 10, 200, 200));
        b.setCachedLayout(new LayoutBox(10, 10, 100, 100));
        c.setCachedLayout(new LayoutBox(5, 5, 40, 40));

        // 注册 handler 但不做任何标记操作
        router.on(c, SceneEventType.POINTER_DOWN, (evt, ctx) -> {});
        router.on(c, SceneEventType.POINTER_CANCEL, (evt, ctx) -> {});

        // DOWN 设 pressedNode
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.LEFT), 0, 0);

        // 清所有脏标记
        clearAllDirtyRecursive(root);

        // DFS 收集 before
        Map<SceneNode, boolean[]> before = collectDirtyProbes(root);

        // CANCEL route
        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.CANCEL,
                20, 20, SceneMouseButton.NONE,
                0, 0, 0, false, false, false, false, 2000L));
        SceneInputFrame cancelFrame = frameBuilder.drainFrame();
        router.route(root, cancelFrame, 0, 0);

        // DFS 收集 after
        Map<SceneNode, boolean[]> after = collectDirtyProbes(root);

        // 逐节点逐标记断言
        String[] names = {"selfLayout", "descLayout", "selfPaint", "descPaint",
                "composite", "selfGeom", "descGeom"};
        for (Map.Entry<SceneNode, boolean[]> entry : before.entrySet()) {
            SceneNode node = entry.getKey();
            boolean[] bf = entry.getValue();
            boolean[] af = after.get(node);
            Assert.assertNotNull("after 中应存在节点", af);
            for (int i = 0; i < 7; i++) {
                Assert.assertEquals("节点 " + System.identityHashCode(node) + " 标记 " + names[i],
                        bf[i], af[i]);
            }
        }
    }

    // ==================== 组 X：ctx.requestPointerCapture 语义 ====================

    /**
     * X1：handler 内调 ctx.requestPointerCapture() -> capturedNode = event.target。
     */
    @Test
    public void x1_requestPointerCaptureSetsCapturedNodeToEventTarget() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        router.on(child, SceneEventType.POINTER_DOWN, (evt, ctx) -> {
            log.add("down");
            ctx.requestPointerCapture();
        });
        router.on(child, SceneEventType.POINTER_MOVE, (evt, ctx) -> log.add("move"));

        // DOWN 命中 child → handler 调 ctx.requestPointerCapture()
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT), 0, 0);
        Assert.assertEquals("DOWN 应派发", 1, log.size());
        Assert.assertEquals("down", log.get(0));

        // capturedNode 应为 child
        Assert.assertSame("capturedNode 应为 event.target（child）", child, router.__getCapturedNode());

        // MOVE 到树外 → capturedNode 仍 child，应收到 MOVE
        log.clear();
        router.route(root, buildFrame(ScenePointerAction.MOVE, 250, 250, SceneMouseButton.NONE), 0, 0);
        Assert.assertEquals("捕获后 MOVE 应投给 capturedNode", 1, log.size());
        Assert.assertEquals("move", log.get(0));
    }

    /**
     * X2：ctx.requestPointerCapture target 是事件原始 target（非 currentNode bubble 游标）。
     */
    @Test
    public void x2_requestPointerCaptureTargetIsEventOriginalTarget() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        // 在 root 上注册 DOWN handler（bubble 阶段到达）
        router.on(root, SceneEventType.POINTER_DOWN, (evt, ctx) -> {
            // bubble 阶段 currentNode 是 root，但 target 应是 child
            ctx.requestPointerCapture();
        });

        // DOWN 命中 child → child 无 handler → bubble 到 root
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT), 0, 0);

        // capturedNode 应为 child（event.target），不是 root（currentNode）
        Assert.assertSame("capturedNode 应为 event.target child（非 bubble 游标 root）",
                child, router.__getCapturedNode());
    }

    // ==================== 组 Y：releasePointerCapture + 显式/隐式优先级 ====================

    /**
     * Y1：releasePointerCapture 手动清空捕获。
     */
    @Test
    public void y1_releasePointerCaptureClearsNode() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        router.requestPointerCapture(child);
        Assert.assertNotNull("capturedNode 应非 null", router.__getCapturedNode());

        router.releasePointerCapture();
        Assert.assertNull("releasePointerCapture 后 capturedNode 应为 null", router.__getCapturedNode());
    }

    /**
     * Y2：capturedNode 优先于 pressedNode（effectiveTarget 判定）。
     */
    @Test
    public void y2_capturedNodePriorityOverPressedNode() {
        SceneNode[] tree = buildSplitTree();
        SceneNode root = tree[0];
        SceneNode btnA = tree[1];
        SceneNode btnB = tree[2];

        List<String> log = new ArrayList<String>();
        router.on(btnA, SceneEventType.POINTER_DOWN, (evt, ctx) -> log.add("down:A"));
        router.on(btnA, SceneEventType.POINTER_MOVE, (evt, ctx) -> log.add("move:A"));
        router.on(btnB, SceneEventType.POINTER_MOVE, (evt, ctx) -> log.add("move:B"));

        // DOWN 在 btnA → pressedNode=btnA
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.LEFT), 0, 0);
        log.clear();

        // requestPointerCapture(btnB) → capturedNode=btnB
        router.requestPointerCapture(btnB);

        // MOVE 到 btnA 区域 → capturedNode 优先，应投 btnB
        router.route(root, buildFrame(ScenePointerAction.MOVE, 30, 30, SceneMouseButton.NONE), 0, 0);
        Assert.assertEquals("capturedNode 优先：MOVE 应投 btnB", 1, log.size());
        Assert.assertEquals("move:B", log.get(0));
    }
}
