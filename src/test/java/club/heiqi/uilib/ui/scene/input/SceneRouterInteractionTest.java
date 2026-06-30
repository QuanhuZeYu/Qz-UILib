package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * SceneInputRouter 交互状态机单元测试（组 B/C/D/E）。
 *
 * <p>覆盖：hover 状态切换、pressed 状态机、零标脏核验、
 * Signal queueWrite/flush 时序、空帧、合并写入。</p>
 */
public class SceneRouterInteractionTest {

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

    /** 构建带修饰键的单指针事件帧 */
    private SceneInputFrame buildFrameWithMods(ScenePointerAction action, int x, int y,
                                                SceneMouseButton button,
                                                boolean ctrl, boolean shift) {
        frameBuilder.push(RawInputEvent.ofPointer(action, x, y,
                button, 0, 0, 0,
                ctrl, shift, false, false, 1000L));
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

    // ==================== B1：首帧 MOVE 命中 target → flush → hovered.get()==true ====================

    @Test
    public void shouldSetHoveredTrueOnFirstMoveHit() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        // 声明关心 hover
        ReadableSignal<Boolean> hoveredSig = router.interactionState(child).hovered();
        Assert.assertEquals("初始值为 false", Boolean.FALSE, hoveredSig.get());

        // 首帧 MOVE 命中 child
        SceneInputFrame frame = buildFrame(ScenePointerAction.MOVE, 40, 40, SceneMouseButton.NONE);
        runtime.route(root, frame, 0, 0);
        runtime.flush();

        Assert.assertEquals("flush 后 hovered 应为 true", Boolean.TRUE, hoveredSig.get());
    }

    // ==================== B2：MOVE 移出（hitTarget=null）→ flush → hovered.get()==false ====================

    @Test
    public void shouldSetHoveredFalseOnMoveOut() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        ReadableSignal<Boolean> hoveredSig = router.interactionState(child).hovered();

        // 先 MOVE 进入
        runtime.route(root, buildFrame(ScenePointerAction.MOVE, 40, 40, SceneMouseButton.NONE), 0, 0);
        runtime.flush();
        Assert.assertEquals("进入后 hovered=true", Boolean.TRUE, hoveredSig.get());

        // MOVE 移出到树外
        runtime.route(root, buildFrame(ScenePointerAction.MOVE, 250, 250, SceneMouseButton.NONE), 0, 0);
        runtime.flush();
        Assert.assertEquals("移出后 hovered=false", Boolean.FALSE, hoveredSig.get());
    }

    // ==================== B3：MOVE 从 A 切到 B → flush → A.hovered=false 且 B.hovered=true ====================

    @Test
    public void shouldSwitchHoverBetweenTwoNodes() {
        SceneNode[] tree = buildSplitTree();
        SceneNode root = tree[0];
        SceneNode btnA = tree[1];
        SceneNode btnB = tree[2];

        ReadableSignal<Boolean> hoverA = router.interactionState(btnA).hovered();
        ReadableSignal<Boolean> hoverB = router.interactionState(btnB).hovered();

        // MOVE 先命中 A
        runtime.route(root, buildFrame(ScenePointerAction.MOVE, 20, 20, SceneMouseButton.NONE), 0, 0);
        runtime.flush();
        Assert.assertEquals("A hovered=true", Boolean.TRUE, hoverA.get());
        Assert.assertEquals("B hovered=false", Boolean.FALSE, hoverB.get());

        // MOVE 切换到 B
        runtime.route(root, buildFrame(ScenePointerAction.MOVE, 110, 110, SceneMouseButton.NONE), 0, 0);
        runtime.flush();
        Assert.assertEquals("A hovered=false（切出）", Boolean.FALSE, hoverA.get());
        Assert.assertEquals("B hovered=true（切入）", Boolean.TRUE, hoverB.get());
    }

    // ==================== B4：bind(PAINT, hovered, node::setBackgroundColor)，MOVE enter → flush → PAINT 脏 + 背景色变 ====================

    @Test
    public void shouldBindHoveredToBackgroundColorAndMarkPaintDirty() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        ReadableSignal<Boolean> hoveredSig = router.interactionState(child).hovered();
        int enterColor = 0xFFFF0000; // 红色
        int leaveColor = 0xFF0000FF; // 蓝色

        // bind: 将 hover state 转换为 background color
        runtime.bind(Invalidation.PAINT, hoveredSig, (hovered) -> {
            child.setBackgroundColor(hovered ? enterColor : leaveColor);
        });

        // 首帧 flush 让 effect 首跑
        runtime.flush();
        Assert.assertEquals("初始 hovered=false → 背景色应为 leaveColor", leaveColor, child.getBackgroundColor());
        clearAllDirtyRecursive(root);

        // MOVE enter → flush
        runtime.route(root, buildFrame(ScenePointerAction.MOVE, 40, 40, SceneMouseButton.NONE), 0, 0);
        runtime.flush();

        Assert.assertEquals("MOVE enter 后背景色应为 enterColor", enterColor, child.getBackgroundColor());
        Assert.assertTrue("节点应被打 PAINT 脏", child.__isSelfPaintDirty());
    }

    // ==================== B5：未声明关心的节点 hover 进出 → 无 signal、无写入 ====================

    @Test
    public void shouldNotAffectNodeWithoutDeclaredInterest() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);

        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        child.setCachedLayout(new LayoutBox(20, 20, 80, 60));

        // 创建 interactionState 但不调 .hovered() —— 未声明关心
        SceneInteractionState state = router.interactionState(child);
        Assert.assertFalse("__hasHoveredSignal 应为 false", state.__hasHoveredSignal());

        // MOVE 划过 child
        runtime.route(root, buildFrame(ScenePointerAction.MOVE, 40, 40, SceneMouseButton.NONE), 0, 0);
        runtime.flush();

        // hovered signal 从未被创建（writeHovered 短路）
        Assert.assertFalse("route+flush 后 signal 仍不应被创建", state.__hasHoveredSignal());
    }

    // ==================== C1：DOWN 命中 → flush → pressed.get()==true ====================

    @Test
    public void shouldSetPressedTrueOnDown() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        ReadableSignal<Boolean> pressedSig = router.interactionState(child).pressed();
        Assert.assertEquals("初始值为 false", Boolean.FALSE, pressedSig.get());

        // DOWN 命中 child
        runtime.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT), 0, 0);
        runtime.flush();

        Assert.assertEquals("flush 后 pressed 应为 true", Boolean.TRUE, pressedSig.get());
    }

    // ==================== C2：UP → flush → pressed.get()==false ====================

    @Test
    public void shouldSetPressedFalseOnUp() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        ReadableSignal<Boolean> pressedSig = router.interactionState(child).pressed();

        // DOWN
        runtime.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT), 0, 0);
        runtime.flush();
        Assert.assertEquals("DOWN 后 pressed=true", Boolean.TRUE, pressedSig.get());

        // UP 在相同位置
        runtime.route(root, buildFrame(ScenePointerAction.BUTTON_UP, 40, 40, SceneMouseButton.LEFT), 0, 0);
        runtime.flush();
        Assert.assertEquals("UP 后 pressed=false", Boolean.FALSE, pressedSig.get());
    }

    // ==================== C3：DOWN 后指针移出整树再 UP（出界）：pressed 仍正确翻 false ====================

    @Test
    public void shouldClearPressedOnOutOfBoundsUp() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        ReadableSignal<Boolean> pressedSig = router.interactionState(child).pressed();

        // DOWN 在 child 内
        runtime.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT), 0, 0);
        runtime.flush();
        Assert.assertEquals("DOWN 后 pressed=true", Boolean.TRUE, pressedSig.get());

        // UP 在树外（出界）
        runtime.route(root, buildFrame(ScenePointerAction.BUTTON_UP, 250, 250, SceneMouseButton.LEFT), 0, 0);
        runtime.flush();
        Assert.assertEquals("出界 UP 后 pressed=false", Boolean.FALSE, pressedSig.get());
        Assert.assertNull("pressedNode 应已清空", router.__getPressedNode());
    }

    // ==================== C4：DOWN → flush → 节点打 PAINT 脏（bind pressed） ====================

    @Test
    public void shouldBindPressedToDirty() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        ReadableSignal<Boolean> pressedSig = router.interactionState(child).pressed();

        // bind pressed 到 background color
        runtime.bind(Invalidation.PAINT, pressedSig, (pressed) -> {
            child.setBackgroundColor(pressed ? 0xFFFF0000 : 0xFF0000FF);
        });

        runtime.flush();
        clearAllDirtyRecursive(root);

        // DOWN
        runtime.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT), 0, 0);
        runtime.flush();

        Assert.assertTrue("DOWN→flush 后应有 PAINT 脏", child.__isSelfPaintDirty());
        Assert.assertEquals("背景色应为 pressed 色（红色）", 0xFFFF0000, child.getBackgroundColor());
    }

    // ==================== D1：★T-zero-dirty —— 无 bind、无 interactionState，route 后 7 脏探针全等 ====================

    @Test
    public void shouldNotDirtyAnyNodeDuringRouteWithoutInteractionState() {
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

        clearAllDirtyRecursive(root);
        Map<SceneNode, boolean[]> before = collectDirtyProbes(root);

        // route：MOVE + DOWN + UP，无 handler、无 interactionState
        SceneInputFrame frame = buildMultiEventFrame();
        runtime.route(root, frame, 0, 0);

        Map<SceneNode, boolean[]> after = collectDirtyProbes(root);

        for (Map.Entry<SceneNode, boolean[]> entry : before.entrySet()) {
            SceneNode node = entry.getKey();
            boolean[] bf = entry.getValue();
            boolean[] af = after.get(node);
            Assert.assertNotNull("after 中应存在节点", af);

            String[] names = {"selfLayout", "descLayout", "selfPaint", "descPaint",
                    "composite", "selfGeom", "descGeom"};
            for (int i = 0; i < 7; i++) {
                Assert.assertEquals("D1 节点 " + System.identityHashCode(node) + " 标记 " + names[i],
                        bf[i], af[i]);
            }
        }
    }

    // ==================== D2：★声明 interactionState + bind → route 返回后(flush前) 7 脏探针仍相等 ====================

    @Test
    public void shouldNotDirtyBeforeFlushEvenWithInteractionState() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        // 声明 interactionState + bind
        ReadableSignal<Boolean> hoveredSig = router.interactionState(child).hovered();
        runtime.bind(Invalidation.PAINT, hoveredSig, (hovered) -> {
            child.setBackgroundColor(hovered ? 0xFFFF0000 : 0xFF0000FF);
        });

        runtime.flush();
        clearAllDirtyRecursive(root);

        Map<SceneNode, boolean[]> before = collectDirtyProbes(root);

        // route MOVE（queueWrite 但未 flush）
        runtime.route(root, buildFrame(ScenePointerAction.MOVE, 40, 40, SceneMouseButton.NONE), 0, 0);

        // ★ route 返回后、flush 之前采集
        Map<SceneNode, boolean[]> afterBeforeFlush = collectDirtyProbes(root);

        for (Map.Entry<SceneNode, boolean[]> entry : before.entrySet()) {
            SceneNode node = entry.getKey();
            boolean[] bf = entry.getValue();
            boolean[] af = afterBeforeFlush.get(node);
            Assert.assertNotNull("after 中应存在节点", af);

            String[] names = {"selfLayout", "descLayout", "selfPaint", "descPaint",
                    "composite", "selfGeom", "descGeom"};
            for (int i = 0; i < 7; i++) {
                Assert.assertEquals("D2 route 返回后(flush前) 节点 " + System.identityHashCode(node)
                                + " 标记 " + names[i] + " 应为旧值",
                        bf[i], af[i]);
            }
        }

        // 同时验证 signal get 返回旧值（queueWrite 未 apply）
        Assert.assertEquals("flush 前 get 返回旧值", Boolean.FALSE, hoveredSig.get());
    }

    // ==================== D3：紧接 D2 调 flush → 此时才出现 PAINT 脏 ====================

    @Test
    public void shouldDirtyAfterFlush() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        ReadableSignal<Boolean> hoveredSig = router.interactionState(child).hovered();
        runtime.bind(Invalidation.PAINT, hoveredSig, (hovered) -> {
            child.setBackgroundColor(hovered ? 0xFFFF0000 : 0xFF0000FF);
        });

        runtime.flush();
        clearAllDirtyRecursive(root);

        // route MOVE
        runtime.route(root, buildFrame(ScenePointerAction.MOVE, 40, 40, SceneMouseButton.NONE), 0, 0);

        // flush 之前不应有脏
        Assert.assertFalse("flush 前 selfPaint 应为 false", child.__isSelfPaintDirty());

        // flush
        runtime.flush();

        Assert.assertTrue("flush 后 selfPaint 应为 true", child.__isSelfPaintDirty());
        Assert.assertEquals("flush 后 get 返回新值", Boolean.TRUE, hoveredSig.get());
    }

    // ==================== E1：set→route→get 返回旧值；set→route→flush→get 返回新值 ====================

    @Test
    public void shouldReturnOldValueBeforeFlushAndNewValueAfterFlush() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        ReadableSignal<Boolean> hoveredSig = router.interactionState(child).hovered();
        Assert.assertEquals("初始值为 false", Boolean.FALSE, hoveredSig.get());

        // set (via route) → get 返回旧值
        runtime.route(root, buildFrame(ScenePointerAction.MOVE, 40, 40, SceneMouseButton.NONE), 0, 0);
        Assert.assertEquals("route 后 flush 前 get 仍为旧值 false", Boolean.FALSE, hoveredSig.get());

        // flush → get 返回新值
        runtime.flush();
        Assert.assertEquals("flush 后 get 返回新值 true", Boolean.TRUE, hoveredSig.get());
    }

    // ==================== E2：空帧 SceneInputFrame.EMPTY 走 route 无副作用无异常 ====================

    @Test
    public void shouldHandleEmptyFrameWithoutSideEffects() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        ReadableSignal<Boolean> hoveredSig = router.interactionState(child).hovered();
        runtime.flush();

        // EMPTY 帧走 route
        runtime.route(root, SceneInputFrame.EMPTY, 0, 0);
        runtime.flush();

        // 值应保持初始 false
        Assert.assertEquals("EMPTY 帧后 hovered 仍为 false", Boolean.FALSE, hoveredSig.get());
        Assert.assertNull("hoveredNode 应为 null", router.__getHoveredNode());
    }

    // ==================== E3：同帧多 MOVE A→B→A → flush 后 A.hovered 最终值正确、flush 合并 ====================

    @Test
    public void shouldMergeMultipleMovesInSameFrame() {
        SceneNode[] tree = buildSplitTree();
        SceneNode root = tree[0];
        SceneNode btnA = tree[1];
        SceneNode btnB = tree[2];

        ReadableSignal<Boolean> hoverA = router.interactionState(btnA).hovered();
        ReadableSignal<Boolean> hoverB = router.interactionState(btnB).hovered();

        // 同帧内三个 MOVE：A → B → A
        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.MOVE,
                20, 20, SceneMouseButton.NONE, 0, 0, 0,
                false, false, false, false, 1000L));
        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.MOVE,
                110, 110, SceneMouseButton.NONE, 0, 0, 0,
                false, false, false, false, 2000L));
        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.MOVE,
                20, 20, SceneMouseButton.NONE, 0, 0, 0,
                false, false, false, false, 3000L));
        SceneInputFrame frame = frameBuilder.drainFrame();

        runtime.route(root, frame, 0, 0);
        runtime.flush();

        // 同帧内节点 hover 往返（A→B→A）：ReactiveScheduler 去重移到 flush 阶段后，
        // 按 signal 合并 pending 并对比帧初值——B 同帧 false→true→false，帧初值 false == 帧末终值 false，
        // 净无变化被正确吸收，B 最终为 false（不再残留 true 一帧）。这是原「Signal 去重瑕疵」被核心修复后的正确语义。
        // 权威 hoveredNode 始终正确（=A），且 B 的 hover 同帧往返不再产生伪 true 残留。
        Assert.assertEquals("A hovered 最终值应为 true", Boolean.TRUE, hoverA.get());
        Assert.assertEquals("B hovered 同帧往返净无变化，去重修复后正确为 false", Boolean.FALSE, hoverB.get());
    }

    // ==================== 辅助方法 ====================

    /** 构建含多事件的帧（DOWN+MOVE+UP）用于 D1 */
    private SceneInputFrame buildMultiEventFrame() {
        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN,
                15, 15, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.MOVE,
                20, 20, SceneMouseButton.NONE,
                0, 0, 0, false, false, false, false, 2000L));
        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP,
                20, 20, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 3000L));
        return frameBuilder.drainFrame();
    }
}
