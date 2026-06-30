package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SceneInputRouter 路由与派发单元测试。
 *
 * <p>覆盖：target/bubble 路由、stopPropagation、按压捕获、CLICK 合成、
 * on() 注册退订、T16 零标脏核验。</p>
 */
public class SceneInputRouterTest {

    private SceneInputRouter router;
    private InputFrameBuilder frameBuilder;

    @Before
    public void setUp() {
        router = new SceneInputRouter();
        frameBuilder = new InputFrameBuilder(0, 0);
    }

    /**
     * 辅助：构造一棵根+子两层树。
     * 注意：appendChild 会调用 markSelfLayout() 将父 cachedLayout 置 null，
     * 因此必须在所有树操作完成后统一设置 LayoutBox。
     */
    private SceneNode buildTwoLayerTree() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);

        // 树构造完成后设置 LayoutBox
        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        child.setCachedLayout(new LayoutBox(20, 20, 80, 60));
        return root;
    }

    /**
     * 辅助：构建帧（仅含一个指针事件）。
     */
    private SceneInputFrame buildFrame(ScenePointerAction action, int x, int y, SceneMouseButton button) {
        frameBuilder.push(RawInputEvent.ofPointer(action, x, y,
                button, 0, 0, 0,
                false, false, false, false, 1000L));
        return frameBuilder.drainFrame();
    }

    /**
     * 辅助：构建带修饰键的帧。
     */
    private SceneInputFrame buildFrameWithMods(ScenePointerAction action, int x, int y,
                                                SceneMouseButton button,
                                                boolean ctrl, boolean shift) {
        frameBuilder.push(RawInputEvent.ofPointer(action, x, y,
                button, 0, 0, 0,
                ctrl, shift, false, false, 1000L));
        return frameBuilder.drainFrame();
    }

    // ===== T9：target 阶段派发 =====

    @Test
    public void shouldDispatchToTargetOnPointerDown() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        router.on(child, SceneEventType.POINTER_DOWN, (evt, ctx) -> {
            log.add("target:" + evt.getTarget());
        });

        SceneInputFrame frame = buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT);
        router.route(root, frame, 0, 0);

        Assert.assertEquals("应派发 1 条", 1, log.size());
        Assert.assertTrue("target 应为 child", log.get(0).contains("target:"));
    }

    // ===== T10：bubble 冒泡 =====

    @Test
    public void shouldBubbleToAncestors() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        router.on(child, SceneEventType.POINTER_DOWN, (evt, ctx) -> log.add("child"));
        router.on(root, SceneEventType.POINTER_DOWN, (evt, ctx) -> log.add("root"));

        SceneInputFrame frame = buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT);
        router.route(root, frame, 0, 0);

        Assert.assertEquals("child + root 共 2 条", 2, log.size());
        Assert.assertEquals("child 先派发", "child", log.get(0));
        Assert.assertEquals("root 后派发（冒泡）", "root", log.get(1));
    }

    // ===== T11：stopPropagation =====

    @Test
    public void shouldStopPropagation() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        router.on(child, SceneEventType.POINTER_DOWN, (evt, ctx) -> {
            log.add("child");
            ctx.stopPropagation();
        });
        router.on(root, SceneEventType.POINTER_DOWN, (evt, ctx) -> log.add("root"));

        SceneInputFrame frame = buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT);
        router.route(root, frame, 0, 0);

        Assert.assertEquals("仅 child 派发", 1, log.size());
        Assert.assertEquals("child", log.get(0));
    }

    // ===== T12：stopPropagation 不阻断同节点多 handler =====

    @Test
    public void shouldRunAllHandlersOnSameNodeAfterStop() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        router.on(child, SceneEventType.POINTER_DOWN, (evt, ctx) -> {
            log.add("h1");
            ctx.stopPropagation();
        });
        router.on(child, SceneEventType.POINTER_DOWN, (evt, ctx) -> log.add("h2"));
        router.on(root, SceneEventType.POINTER_DOWN, (evt, ctx) -> log.add("root"));

        SceneInputFrame frame = buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT);
        router.route(root, frame, 0, 0);

        Assert.assertEquals("child 上 2 个 handler 都跑", 2, log.size());
        Assert.assertTrue("h1 跑了", log.contains("h1"));
        Assert.assertTrue("h2 跑了", log.contains("h2"));
    }

    // ===== T13：按压捕获 — DOWN 后 MOVE 强制派发到 pressedNode =====

    @Test
    public void shouldCaptureMoveToPressedNode() {
        SceneNode root = new SceneNode();
        SceneNode btnA = new SceneNode();
        SceneNode btnB = new SceneNode();
        root.appendChild(btnA);
        root.appendChild(btnB);

        // 树构造完成后设置 LayoutBox
        root.setCachedLayout(new LayoutBox(0, 0, 300, 300));
        btnA.setCachedLayout(new LayoutBox(10, 10, 50, 50));
        btnB.setCachedLayout(new LayoutBox(100, 100, 50, 50));

        List<String> log = new ArrayList<String>();
        router.on(btnA, SceneEventType.POINTER_DOWN, (evt, ctx) -> log.add("down:A"));
        router.on(btnA, SceneEventType.POINTER_MOVE, (evt, ctx) -> log.add("move:A"));
        router.on(btnB, SceneEventType.POINTER_MOVE, (evt, ctx) -> log.add("move:B"));

        // DOWN 在 btnA (15, 15)
        SceneInputFrame downFrame = buildFrame(ScenePointerAction.BUTTON_DOWN, 15, 15, SceneMouseButton.LEFT);
        router.route(root, downFrame, 0, 0);
        Assert.assertEquals("DOWN 派发到 A", 1, log.size());
        Assert.assertEquals("down:A", log.get(0));

        // MOVE 到 btnB 区域 (110, 110) — 但按压捕获应派发到 btnA
        log.clear();
        SceneInputFrame moveFrame = buildFrame(ScenePointerAction.MOVE, 110, 110, SceneMouseButton.NONE);
        router.route(root, moveFrame, 0, 0);
        Assert.assertEquals("MOVE 应派发到 A（按压捕获）", 1, log.size());
        Assert.assertEquals("move:A", log.get(0));
    }

    // ===== T14：CLICK 合成 =====

    @Test
    public void shouldSynthesizeClickOnUpAtSameTarget() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        router.on(child, SceneEventType.POINTER_DOWN, (evt, ctx) -> log.add("down"));
        router.on(child, SceneEventType.POINTER_UP, (evt, ctx) -> log.add("up"));
        router.on(child, SceneEventType.CLICK, (evt, ctx) -> log.add("click"));

        // DOWN
        SceneInputFrame downFrame = buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT);
        router.route(root, downFrame, 0, 0);

        // UP 在同一位置
        SceneInputFrame upFrame = buildFrame(ScenePointerAction.BUTTON_UP, 40, 40, SceneMouseButton.LEFT);
        router.route(root, upFrame, 0, 0);

        Assert.assertEquals("顺序: down, up, click", 3, log.size());
        Assert.assertEquals("down", log.get(0));
        Assert.assertEquals("up", log.get(1));
        Assert.assertEquals("click", log.get(2));
    }

    // ===== T15：不在同节点 UP 不合成 CLICK =====

    @Test
    public void shouldNotSynthesizeClickOnDifferentTarget() {
        SceneNode root = new SceneNode();
        SceneNode btnA = new SceneNode();
        SceneNode btnB = new SceneNode();
        root.appendChild(btnA);
        root.appendChild(btnB);

        // 树构造完成后设置 LayoutBox
        root.setCachedLayout(new LayoutBox(0, 0, 300, 300));
        btnA.setCachedLayout(new LayoutBox(10, 10, 50, 50));
        btnB.setCachedLayout(new LayoutBox(100, 100, 50, 50));

        List<String> log = new ArrayList<String>();
        router.on(btnA, SceneEventType.CLICK, (evt, ctx) -> log.add("click:A"));
        router.on(btnB, SceneEventType.CLICK, (evt, ctx) -> log.add("click:B"));

        // DOWN 在 A
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 15, 15, SceneMouseButton.LEFT), 0, 0);
        // UP 在 B
        router.route(root, buildFrame(ScenePointerAction.BUTTON_UP, 110, 110, SceneMouseButton.LEFT), 0, 0);

        Assert.assertTrue("不应有 CLICK", log.isEmpty());
    }

    // ===== T16：dispatch 零标脏 I7 核验 =====

    /**
     * 构造树 + layout + 清脏 → DFS 收集每节点 7 探针存 before →
     * route（handler 全留空）→ DFS 再收集 after → 逐节点逐标记断言相等。
     */
    @Test
    public void shouldNotDirtyAnyNodeDuringRoute() {
        // 构造深层树（先搭结构后设 LayoutBox）
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

        // 清所有脏标记
        clearAllDirtyRecursive(root);

        // DFS 收集 before
        Map<SceneNode, boolean[]> before = collectDirtyProbes(root);

        // route：推入 DOWN + MOVE + UP 三个事件，handler 全空
        SceneInputFrame frame = buildMultiEventFrame();
        router.route(root, frame, 0, 0);

        // DFS 收集 after
        Map<SceneNode, boolean[]> after = collectDirtyProbes(root);

        // 逐节点逐标记断言
        for (Map.Entry<SceneNode, boolean[]> entry : before.entrySet()) {
            SceneNode node = entry.getKey();
            boolean[] bf = entry.getValue();
            boolean[] af = after.get(node);
            Assert.assertNotNull("after 中应存在节点", af);

            String[] names = {"selfLayout", "descLayout", "selfPaint", "descPaint",
                    "composite", "selfGeom", "descGeom"};
            for (int i = 0; i < 7; i++) {
                Assert.assertEquals("节点 " + System.identityHashCode(node) + " 标记 " + names[i],
                        bf[i], af[i]);
            }
        }
    }

    // ===== T17：on() 注册和退订 =====

    @Test
    public void shouldRegisterAndDisposeHandler() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        Assert.assertEquals("注册前 count=0", 0, router.__handlerCount(child, SceneEventType.POINTER_DOWN));

        InputBinding binding = router.on(child, SceneEventType.POINTER_DOWN,
                (evt, ctx) -> {});
        Assert.assertEquals("注册后 count=1", 1, router.__handlerCount(child, SceneEventType.POINTER_DOWN));
        Assert.assertFalse("未退订", binding.isDisposed());

        binding.dispose();
        Assert.assertEquals("退订后 count=0", 0, router.__handlerCount(child, SceneEventType.POINTER_DOWN));
        Assert.assertTrue("已退订", binding.isDisposed());

        // 幂等
        binding.dispose();
        Assert.assertTrue("幂等退订", binding.isDisposed());
    }

    // ===== T18：不同事件类型独立注册 =====

    @Test
    public void shouldIsolateHandlersByType() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        router.on(child, SceneEventType.POINTER_DOWN, (evt, ctx) -> {});
        router.on(child, SceneEventType.POINTER_UP, (evt, ctx) -> {});
        router.on(child, SceneEventType.POINTER_MOVE, (evt, ctx) -> {});

        Assert.assertEquals("DOWN count", 1, router.__handlerCount(child, SceneEventType.POINTER_DOWN));
        Assert.assertEquals("UP count", 1, router.__handlerCount(child, SceneEventType.POINTER_UP));
        Assert.assertEquals("MOVE count", 1, router.__handlerCount(child, SceneEventType.POINTER_MOVE));
        Assert.assertEquals("CLICK count=0", 0, router.__handlerCount(child, SceneEventType.CLICK));
    }

    // ===== T19：Scroll 事件正确映射 =====

    @Test
    public void shouldRouteScrollEvents() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        router.on(child, SceneEventType.SCROLL, (evt, ctx) -> {
            log.add("scroll:" + evt.getWheelDelta());
        });

        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, 40, 40,
                SceneMouseButton.NONE, -120, 0, 0,
                false, false, false, false, 2000L));
        SceneInputFrame frame = frameBuilder.drainFrame();
        router.route(root, frame, 0, 0);

        Assert.assertEquals("应派发 SCROLL", 1, log.size());
        Assert.assertTrue("wheelDelta 应为 -120", log.get(0).contains("-120"));
    }

    // ===== T20：修饰键透传到 SceneEvent =====

    @Test
    public void shouldPassModifiersToEvent() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        List<Boolean> mods = new ArrayList<Boolean>();
        router.on(child, SceneEventType.POINTER_DOWN, (evt, ctx) -> {
            mods.add(evt.isControlDown());
            mods.add(evt.isShiftDown());
        });

        SceneInputFrame frame = buildFrameWithMods(ScenePointerAction.BUTTON_DOWN,
                40, 40, SceneMouseButton.LEFT, true, true);
        router.route(root, frame, 0, 0);

        Assert.assertEquals("应有 2 个 mod 值", 2, mods.size());
        Assert.assertTrue("ctrl=true", mods.get(0));
        Assert.assertTrue("shift=true", mods.get(1));
    }

    // ===== T21：按压捕获在移出整树时仍投递 + 不合成 CLICK + 无泄漏 =====

    /**
     * DOWN 命中 btnA → MOVE 到树外（hitChain 空）→ btnA 仍收到 MOVE（捕获投递）。
     * 接树外 UP → btnA 收到 UP，不合成 CLICK，pressedNode 清空无泄漏。
     */
    @Test
    public void shouldCaptureMoveOutsideTreeAndClearOnUp() {
        SceneNode root = new SceneNode();
        SceneNode btnA = new SceneNode();
        root.appendChild(btnA);

        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        btnA.setCachedLayout(new LayoutBox(20, 20, 80, 60));

        List<String> log = new ArrayList<String>();
        router.on(btnA, SceneEventType.POINTER_DOWN, (evt, ctx) -> log.add("down"));
        router.on(btnA, SceneEventType.POINTER_MOVE, (evt, ctx) -> log.add("move"));
        router.on(btnA, SceneEventType.POINTER_UP, (evt, ctx) -> log.add("up"));
        router.on(btnA, SceneEventType.CLICK, (evt, ctx) -> log.add("click"));

        // DOWN 在 btnA 内
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT), 0, 0);
        Assert.assertEquals("DOWN 派发", 1, log.size());
        log.clear();

        // MOVE 到树外（坐标 (250,250) 在 root [0,200) × [0,200) 外）
        router.route(root, buildFrame(ScenePointerAction.MOVE, 250, 250, SceneMouseButton.NONE), 0, 0);
        Assert.assertEquals("树外 MOVE 仍应投递给 pressedNode", 1, log.size());
        Assert.assertEquals("move", log.get(0));
        log.clear();

        // UP 在树外
        router.route(root, buildFrame(ScenePointerAction.BUTTON_UP, 250, 250, SceneMouseButton.LEFT), 0, 0);
        Assert.assertEquals("树外 UP 仍应投递给 pressedNode", 1, log.size());
        Assert.assertEquals("up", log.get(0));
        // 不合成 CLICK（log 只有 "up" 一条）
        Assert.assertNull("pressedNode 应已清空无泄漏", router.__getPressedNode());
    }

    // ===== T22：rootAbsX/Y 非零时命中正确 =====

    /**
     * rootAbsX=50/rootAbsY=30，root layout(0,0,100,80)。
     * 指针画布坐标 (60,40) 命中（相对 root 内 10,10），(40,20) 不命中。
     */
    @Test
    public void shouldHitCorrectlyWithNonZeroRootAbs() {
        SceneNode root = new SceneNode();
        root.setCachedLayout(new LayoutBox(0, 0, 100, 80));

        List<String> log = new ArrayList<String>();
        router.on(root, SceneEventType.POINTER_DOWN, (evt, ctx) -> log.add("hit"));

        // rootAbsX=50, rootAbsY=30，指针 (60,40) 相对 root 内部偏移 (10,10) — 命中
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 60, 40, SceneMouseButton.LEFT), 50, 30);
        Assert.assertEquals("指针在校准区域内应命中", 1, log.size());
        log.clear();

        // 指针 (40,20) 相对 root 内部偏移 (-10,-10) — 不命中
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 20, SceneMouseButton.LEFT), 50, 30);
        Assert.assertEquals("指针偏移为负不应命中", 0, log.size());
    }

    // ===== I12：localPointer 注入正确性 =====

    /**
     * I12 localPointer 注入：rootAbs≠0 时，
     *   rawPointer = 屏幕绝对（含 rootAbs）
     *   hostPointer = raw - rootAbs
     *   localPointer = hostPointer - absoluteBox(effectiveTarget,0,0)
     * 三层关系正确，handler 读到的 localPointer 与手动算 hostPointer - absoluteBox 一致。
     */
    @Test
    public void shouldInjectLocalPointerCorrectlyWithNonZeroRootAbs() {
        // 树：root(0,0,200,200) → child(20,20,80,60)
        // rootAbsX=50, rootAbsY=30
        // 指针 (90,70) → host (40,40) → child absoluteBox(0,0)=(20,20) → local (20,20)
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);
        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        child.setCachedLayout(new LayoutBox(20, 20, 80, 60));

        int[] captured = new int[6]; // rawX,rawY,hostX,hostY,localX,localY
        router.on(child, SceneEventType.POINTER_DOWN, (evt, ctx) -> {
            captured[0] = evt.getPointerX();
            captured[1] = evt.getPointerY();
            captured[2] = evt.getHostPointerX();
            captured[3] = evt.getHostPointerY();
            captured[4] = evt.getLocalPointerX();
            captured[5] = evt.getLocalPointerY();
        });

        // 指针 (90,70)，rootAbs (50,30)
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 90, 70, SceneMouseButton.LEFT), 50, 30);

        Assert.assertEquals("rawX = 屏幕绝对含 rootAbs", 90, captured[0]);
        Assert.assertEquals("rawY = 屏幕绝对含 rootAbs", 70, captured[1]);
        Assert.assertEquals("hostX = raw - rootAbsX", 40, captured[2]);
        Assert.assertEquals("hostY = raw - rootAbsY", 40, captured[3]);
        // child absoluteBox(0,0) = (20,20)（host 局部）
        Assert.assertEquals("localX = hostX - absoluteBox(child,0,0).getX()", 20, captured[4]);
        Assert.assertEquals("localY = hostY - absoluteBox(child,0,0).getY()", 20, captured[5]);
    }

    /**
     * I12 向后兼容：rootAbs=0 且 root layout 在原点时，
     * raw == host == local（三层退化同值），既有 handler 行为不变。
     */
    @Test
    public void shouldDegradeThreeLayersEqualWhenRootAbsZero() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);
        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        child.setCachedLayout(new LayoutBox(20, 20, 80, 60));

        int[] captured = new int[6];
        router.on(child, SceneEventType.POINTER_DOWN, (evt, ctx) -> {
            captured[0] = evt.getPointerX();
            captured[1] = evt.getPointerY();
            captured[2] = evt.getHostPointerX();
            captured[3] = evt.getHostPointerY();
            captured[4] = evt.getLocalPointerX();
            captured[5] = evt.getLocalPointerY();
        });

        // 指针 (40,40)，rootAbs (0,0)，child absoluteBox(0,0)=(20,20)
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 40, 40, SceneMouseButton.LEFT), 0, 0);

        // raw == host（rootAbs=0）
        Assert.assertEquals("rootAbs=0 时 rawX == hostX", captured[0], captured[2]);
        Assert.assertEquals("rootAbs=0 时 rawY == hostY", captured[1], captured[3]);
        // local = host - absoluteBox(child,0,0) = 40 - 20 = 20
        Assert.assertEquals("localX = hostX - 20", 20, captured[4]);
        Assert.assertEquals("localY = hostY - 20", 20, captured[5]);
    }

    /**
     * I12 CLICK 合成也注入 localPointer：rootAbs≠0 时 CLICK 事件 localPointer 正确。
     */
    @Test
    public void shouldInjectLocalPointerInSynthesizedClick() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);
        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        child.setCachedLayout(new LayoutBox(20, 20, 80, 60));

        int[] captured = new int[2];
        router.on(child, SceneEventType.CLICK, (evt, ctx) -> {
            captured[0] = evt.getLocalPointerX();
            captured[1] = evt.getLocalPointerY();
        });

        // DOWN + UP 同位置 (90,70)，rootAbs (50,30) → child local (20,20)
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 90, 70, SceneMouseButton.LEFT), 50, 30);
        router.route(root, buildFrame(ScenePointerAction.BUTTON_UP, 90, 70, SceneMouseButton.LEFT), 50, 30);

        Assert.assertEquals("CLICK localX 注入正确", 20, captured[0]);
        Assert.assertEquals("CLICK localY 注入正确", 20, captured[1]);
    }

    /**
     * I12 CANCEL 块也注入 localPointer：rootAbs≠0 时 CANCEL 事件 localPointer 正确。
     */
    @Test
    public void shouldInjectLocalPointerInCancel() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);
        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        child.setCachedLayout(new LayoutBox(20, 20, 80, 60));

        int[] captured = new int[2];
        router.on(child, SceneEventType.POINTER_CANCEL, (evt, ctx) -> {
            captured[0] = evt.getLocalPointerX();
            captured[1] = evt.getLocalPointerY();
        });

        // DOWN 命中 child → pressedNode=child，再 CANCEL
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 90, 70, SceneMouseButton.LEFT), 50, 30);
        router.route(root, buildFrame(ScenePointerAction.CANCEL, 90, 70, SceneMouseButton.LEFT), 50, 30);

        // pressedNode=child，CANCEL 投递到 pressedNode，local = host(40,40) - absoluteBox(child,0,0)(20,20) = (20,20)
        Assert.assertEquals("CANCEL localX 注入正确（pressedNode 局部）", 20, captured[0]);
        Assert.assertEquals("CANCEL localY 注入正确（pressedNode 局部）", 20, captured[1]);
    }

    // ===== T23：Owner 作用域内 on() 自动退订 =====

    /**
     * 在 Owner 作用域内调用 router.on()，owner.dispose() 后 handler 自动移除。
     */
    @Test
    public void shouldAutoDisposeHandlerOnOwnerDispose() {
        SceneNode root = buildTwoLayerTree();
        SceneNode child = root.__getChildren().get(0);

        Owner owner = new Owner();
        owner.run(() -> {
            router.on(child, SceneEventType.POINTER_DOWN, (evt, ctx) -> {});
        });

        Assert.assertEquals("Owner 作用域内注册后 count=1",
                1, router.__handlerCount(child, SceneEventType.POINTER_DOWN));

        // dispose Owner 应触发 onCleanup → handler 移除
        owner.dispose();
        Assert.assertEquals("Owner dispose 后 count=0",
                0, router.__handlerCount(child, SceneEventType.POINTER_DOWN));
    }

    // ===== 辅助方法 =====

    /** 构建含多事件的帧（DOWN+MOVE+UP）用于 T16 */
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
}
