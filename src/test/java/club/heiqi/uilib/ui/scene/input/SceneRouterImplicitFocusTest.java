package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * SceneInputRouter POINTER_DOWN 隐式聚焦/失焦单元测试（Bug1 验收）。
 *
 * <p>验收语义（用户拍板反转后）：POINTER_DOWN 时焦点完全由"这一下点在哪"决定——
 * 命中 focusable（沿命中链最深处向 root 找首个 focusable，含祖先链）→ 聚焦；
 * 命中树内非 focusable 节点或点在树外空白（hitTarget==null）→ 一律失焦（clearFocus）；
 * handler 内 ctx.requestFocus 可覆盖隐式结果（含命中非 focusable 先 clearFocus 再被 handler 覆盖）；
 * requestFocus 幂等（再点已聚焦节点不误 blur）；route 全程零标脏（I7/I11）。</p>
 */
public class SceneRouterImplicitFocusTest {

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

    /**
     * 构建两层树：root(0,0,200,200) → child(20,20,80,60)。
     * 点击 (40,40) 命中 child。
     */
    private SceneNode buildTwoLayerTree() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);

        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        child.setCachedLayout(new LayoutBox(20, 20, 80, 60));
        return root;
    }

    /**
     * 构建三层嵌套树：root(0,0,300,300) → a(10,10,200,200) → b(10,10,100,100)。
     * 点击 b 区域（绝对 (30,30) 起）命中链为 [root, a, b]。
     */
    private SceneNode buildThreeLayerTree() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        root.appendChild(a);
        a.appendChild(b);

        root.setCachedLayout(new LayoutBox(0, 0, 300, 300));
        a.setCachedLayout(new LayoutBox(10, 10, 200, 200));
        b.setCachedLayout(new LayoutBox(10, 10, 100, 100));
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

    /** 断言两个探针 Map 中所有节点 7 探针全等 */
    private void assertProbesEqual(Map<SceneNode, boolean[]> before, Map<SceneNode, boolean[]> after, String label) {
        String[] names = {"selfLayout", "descLayout", "selfPaint", "descPaint",
                "composite", "selfGeom", "descGeom"};
        for (Map.Entry<SceneNode, boolean[]> entry : before.entrySet()) {
            SceneNode node = entry.getKey();
            boolean[] bf = entry.getValue();
            boolean[] af = after.get(node);
            Assert.assertNotNull(label + " after 中应存在节点", af);
            for (int i = 0; i < 7; i++) {
                Assert.assertEquals(label + " 节点 " + System.identityHashCode(node) + " 标记 " + names[i],
                        bf[i], af[i]);
            }
        }
    }

    // ==================== 验收单测 ====================

    // 1：点击 focusable 节点 → focusedNode == 该节点
    @Test
    public void shouldFocusFocusableNodeOnPointerDown() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        router.registerFocusable(child);
        Assert.assertNull("初始焦点为 null", router.__getFocusedNode());

        // POINTER_DOWN 命中 child（绝对 (40,40) 在 child 范围 [20,100)×[20,80) 内）
        SceneInputFrame frame = buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT);
        runtime.route(root, frame, 0, 0);

        Assert.assertSame("POINTER_DOWN 后焦点应为 child", child, router.__getFocusedNode());
    }

    // 2：点击 focusable 的非 focusable 子节点 → 聚焦到 focusable 祖先
    @Test
    public void shouldFocusFocusableAncestorWhenClickingNonFocusableChild() {
        SceneNode root = buildThreeLayerTree();
        SceneNode a = root.__getChildren().get(0);
        SceneNode b = a.__getChildren().get(0);

        // 只把 a 登记 focusable，b 不登记
        router.registerFocusable(a);
        Assert.assertNull("初始焦点为 null", router.__getFocusedNode());

        // 点击 b 区域（绝对 (30,30) 命中链 [root, a, b]），最深 focusable 为 a
        SceneInputFrame frame = buildFrame(ScenePointerAction.BUTTON_DOWN, 30, 30, SceneMouseButton.LEFT);
        runtime.route(root, frame, 0, 0);

        Assert.assertSame("点击非 focusable 子节点应聚焦 focusable 祖先 a", a, router.__getFocusedNode());
    }

    // 3【反转】：先聚焦 A，点击树内非 focusable 节点 B → 失焦（focusedNode == null）
    @Test
    public void shouldBlurWhenClickingNonFocusableNodeInsideTree() {
        SceneNode[] tree = buildSplitTree();
        SceneNode root = tree[0];
        SceneNode btnA = tree[1];
        SceneNode btnB = tree[2];

        // 仅 A 为 focusable，先聚焦 A
        router.registerFocusable(btnA);
        router.requestFocus(btnA);
        Assert.assertSame("初始焦点为 A", btnA, router.__getFocusedNode());

        // 点击 B（非 focusable，绝对命中 (110,110) 在 B 范围 [100,150) 内）
        SceneInputFrame frame = buildFrame(ScenePointerAction.BUTTON_DOWN, 110, 110, SceneMouseButton.LEFT);
        runtime.route(root, frame, 0, 0);

        Assert.assertNull("点击树内非 focusable 节点应失焦（clearFocus）", router.__getFocusedNode());
    }

    // 4【反转/强化】：先聚焦 child，点击树外空白(hitTarget==null) → 失焦（直击 continue 时序陷阱）
    @Test
    public void shouldBlurWhenClickingOutsideTree() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        router.registerFocusable(child);
        router.requestFocus(child);
        Assert.assertSame("初始焦点为 child", child, router.__getFocusedNode());

        // 点击树外（绝对 (250,250) 超出 root 范围 [0,200)，hitTarget==null）
        // ★直击时序陷阱：去掉 hitTarget!=null 守卫后，本块先执行 clearFocus，再走 hitTarget==null→continue
        SceneInputFrame frame = buildFrame(ScenePointerAction.BUTTON_DOWN, 250, 250, SceneMouseButton.LEFT);
        runtime.route(root, frame, 0, 0);

        Assert.assertNull("点击树外空白应失焦（clearFocus）", router.__getFocusedNode());
    }

    // 5：handler 覆盖——命中非 focusable 节点 X，X 的 POINTER_DOWN handler 调 ctx.requestFocus → focusedNode == X
    @Test
    public void shouldAllowHandlerToOverrideImplicitBlur() {
        SceneNode[] tree = buildSplitTree();
        SceneNode root = tree[0];
        SceneNode btnA = tree[1];
        SceneNode btnB = tree[2];

        // A 不登记 focusable，但在其 POINTER_DOWN handler 内 ctx.requestFocus（聚焦 event.target=A）。
        // 先把 A 登记为 focusable 以便 requestFocus 生效语义清晰；
        // 关键是验证：命中非 focusable 时先 clearFocus → 事件仍 dispatch → handler 内 ctx.requestFocus 在后覆盖。
        // 这里让 A 不在初始命中聚焦（因为登记 focusable 会让隐式聚焦先选中 A），
        // 故构造：A 非 focusable，handler 内通过 router.requestFocus(A) 覆盖。
        router.on(btnA, SceneEventType.POINTER_DOWN, (evt, ctx) -> {
            ctx.requestFocus(); // 聚焦 event.target = A
        });

        // 先让焦点停在别处（B），再点 A 验证覆盖
        router.registerFocusable(btnB);
        router.requestFocus(btnB);
        Assert.assertSame("初始焦点为 B", btnB, router.__getFocusedNode());

        // 点击 A（非 focusable，绝对命中 (20,20) 在 A 范围 [10,60) 内）
        // 隐式逻辑：命中非 focusable → 先 clearFocus（焦点暂为 null）→ dispatch → handler ctx.requestFocus(A) 覆盖
        SceneInputFrame frame = buildFrame(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.LEFT);
        runtime.route(root, frame, 0, 0);

        Assert.assertSame("handler 内 ctx.requestFocus 应覆盖隐式失焦，焦点为 A", btnA, router.__getFocusedNode());
    }

    // 6：零标脏——先聚焦 child（先建 focused signal）→ 点树外失焦 → route 后 flush 前 7 探针全等
    @Test
    public void shouldNotDirtyAnyNodeDuringImplicitBlur() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        router.registerFocusable(child);
        // 声明关心 focus（确保 writeFocused 不短路，更严格验证零标脏）
        router.interactionState(child).focused();
        router.requestFocus(child);
        Assert.assertSame("初始焦点为 child", child, router.__getFocusedNode());

        clearAllDirtyRecursive(root);
        Map<SceneNode, boolean[]> before = collectDirtyProbes(root);

        // POINTER_DOWN 树外（绝对 (250,250) 超 root bounds），触发隐式失焦 clearFocus
        SceneInputFrame frame = buildFrame(ScenePointerAction.BUTTON_DOWN, 250, 250, SceneMouseButton.LEFT);
        runtime.route(root, frame, 0, 0);

        // ★ route 返回后、flush 之前采集（writeFocused 走 queueWrite，flush 才生效）
        Map<SceneNode, boolean[]> after = collectDirtyProbes(root);
        assertProbesEqual(before, after, "ImplicitBlur-ZeroDirty");

        // 隐式失焦真值即时生效
        Assert.assertNull("route 后焦点真值即时为 null", router.__getFocusedNode());
    }

    // 7：无焦点时点树外 → 仍 null（clearFocus 短路安全）
    @Test
    public void shouldNoOpWhenClickingOutsideTreeWithoutFocus() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        router.registerFocusable(child);
        Assert.assertNull("初始焦点为 null", router.__getFocusedNode());

        // 点击树外（hitTarget==null），无焦点时 clearFocus 应短路安全
        SceneInputFrame frame = buildFrame(ScenePointerAction.BUTTON_DOWN, 250, 250, SceneMouseButton.LEFT);
        runtime.route(root, frame, 0, 0);

        Assert.assertNull("无焦点时点树外仍为 null（clearFocus 短路安全）", router.__getFocusedNode());
    }

    // 8：点 focusable A 后点 focusable B → 焦点切到 B
    @Test
    public void shouldSwitchFocusBetweenTwoFocusables() {
        SceneNode[] tree = buildSplitTree();
        SceneNode root = tree[0];
        SceneNode btnA = tree[1];
        SceneNode btnB = tree[2];

        router.registerFocusable(btnA);
        router.registerFocusable(btnB);

        // 点 A → 聚焦 A
        runtime.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 20, 20, SceneMouseButton.LEFT), 0, 0);
        Assert.assertSame("点 A 后焦点为 A", btnA, router.__getFocusedNode());

        // 点 B → 焦点切到 B
        runtime.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 110, 110, SceneMouseButton.LEFT), 0, 0);
        Assert.assertSame("点 B 后焦点切到 B", btnB, router.__getFocusedNode());
    }

    // 9：已聚焦 A 再点 A 自身 → 仍 A（requestFocus 幂等不误 blur）
    @Test
    public void shouldKeepFocusWhenClickingAlreadyFocusedNode() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        router.registerFocusable(child);
        router.requestFocus(child);
        Assert.assertSame("初始焦点为 child", child, router.__getFocusedNode());

        // 再点 child 自身（命中 focusable），requestFocus 幂等，不误 blur
        SceneInputFrame frame = buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT);
        runtime.route(root, frame, 0, 0);

        Assert.assertSame("再点已聚焦节点焦点仍为 child（requestFocus 幂等）", child, router.__getFocusedNode());
    }
}
