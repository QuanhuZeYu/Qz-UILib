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

/**
 * B8 滚动后 hover 滞留修复测试（方案 Y'：Router 内部协议方法 reconcileHoverAfterScroll）。
 *
 * <p>验证：滚动容器内容滚动后，指针下方的实际节点已变，flush + layout 之后由
 * {@link SceneInputRouter#reconcileHoverAfterScroll} 用末次指针坐标重做 hit-test，
 * hover 正确切换到新节点，不再滞留在滚动前的旧节点。</p>
 *
 * <p>测试模拟 host render 时序：route → flush → (scrollOffsetY 生效) → reconcile → flush。
 * 几何用手动 LayoutBox 精确控制（同 {@link SceneRouterInteractionTest} 风格），
 * 不依赖真实布局引擎，scrollOffsetY 直接 setScrollOffsetY 模拟 flush 后生效状态。</p>
 */
public class SceneRouterScrollHoverTest {

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

    /**
     * 构建可滚动视口树：root → viewport(scrollable,clip) → itemA/itemB/itemC/itemD 纵向堆叠。
     *
     * <p>视口高 100，4 个 item 各高 50，内容总高 200 > 视口，可滚动。
     * 几何用手动 LayoutBox 精确设定，不跑真实布局引擎。</p>
     *
     * @return [root, viewport, itemA, itemB, itemC, itemD]
     */
    private SceneNode[] buildScrollableTree() {
        SceneNode root = new SceneNode();
        SceneNode viewport = new SceneNode();
        SceneNode itemA = new SceneNode();
        SceneNode itemB = new SceneNode();
        SceneNode itemC = new SceneNode();
        SceneNode itemD = new SceneNode();

        root.appendChild(viewport);
        viewport.appendChild(itemA);
        viewport.appendChild(itemB);
        viewport.appendChild(itemC);
        viewport.appendChild(itemD);

        viewport.setScrollable(true);

        root.setCachedLayout(new LayoutBox(0, 0, 100, 100));
        // viewport 占据 root 全部
        viewport.setCachedLayout(new LayoutBox(0, 0, 100, 100));
        // 4 个 item 纵向堆叠，各高 50，总高 200
        itemA.setCachedLayout(new LayoutBox(0, 0, 100, 50));
        itemB.setCachedLayout(new LayoutBox(0, 50, 100, 50));
        itemC.setCachedLayout(new LayoutBox(0, 100, 100, 50));
        itemD.setCachedLayout(new LayoutBox(0, 150, 100, 50));

        return new SceneNode[]{root, viewport, itemA, itemB, itemC, itemD};
    }

    /** 构建单指针事件帧（指针粘滞坐标 = 事件坐标） */
    private SceneInputFrame buildFrame(ScenePointerAction action, int x, int y,
                                       SceneMouseButton button, int wheelDelta) {
        frameBuilder.push(RawInputEvent.ofPointer(action, x, y,
                button, wheelDelta, 0, 0,
                false, false, false, false, 1000L));
        return frameBuilder.drainFrame();
    }

    // ==================== B8-1：滚动后 hover 切换到新节点 ====================

    /**
     * 滚动前指针在 itemA 上 → MOVE hover itemA；
     * SCROLL 后 scrollOffsetY 生效，同一指针位置下方变为 itemB；
     * reconcileHoverAfterScroll 应将 hover 从 itemA 切到 itemB。
     */
    @Test
    public void shouldSwitchHoverToNewNodeAfterScrollReconcile() {
        SceneNode[] tree = buildScrollableTree();
        SceneNode root = tree[0];
        SceneNode viewport = tree[1];
        SceneNode itemA = tree[2];
        SceneNode itemB = tree[3];

        ReadableSignal<Boolean> hoverA = router.interactionState(itemA).hovered();
        ReadableSignal<Boolean> hoverB = router.interactionState(itemB).hovered();

        // 指针位于 (50, 25)：scrollOffsetY=0 时落在 itemA（content y 0-50）
        int px = 50;
        int py = 25;

        // === 帧 1：MOVE 进入 itemA ===
        runtime.route(root, buildFrame(ScenePointerAction.MOVE, px, py, SceneMouseButton.NONE, 0), 0, 0);
        runtime.flush();
        Assert.assertEquals("MOVE 后 itemA.hovered 应为 true", Boolean.TRUE, hoverA.get());
        Assert.assertEquals("MOVE 后 itemB.hovered 应为 false", Boolean.FALSE, hoverB.get());
        Assert.assertSame("hoveredNode 应为 itemA", itemA, router.__getHoveredNode());

        // === 帧 2：SCROLL 向下滚 50 ===
        // route 内 SCROLL 派发到当前 hitTarget（itemA），并置 pendingHoverReconcile=true。
        // 真实场景下 SceneScrolls handler 会 scrollSignal.set(50) → bind → flush 后 scrollOffsetY=50；
        // 此处不挂 SceneScrolls，直接在 flush 后 setScrollOffsetY 模拟滚动已生效状态。
        runtime.route(root, buildFrame(ScenePointerAction.SCROLL, px, py, SceneMouseButton.NONE, -50), 0, 0);
        runtime.flush();
        // 模拟 flush 后 scrollOffsetY 生效（内容上移 50，itemB 现位于视口 y 0-50）
        viewport.setScrollOffsetY(50);

        // 此时若不重算，hover 仍滞留在 itemA（B8 bug 现象）
        Assert.assertEquals("reconcile 前 itemA.hovered 仍滞留 true", Boolean.TRUE, hoverA.get());
        Assert.assertSame("reconcile 前 hoveredNode 仍滞留 itemA", itemA, router.__getHoveredNode());

        // === flush + (layout) 后调用 reconcileHoverAfterScroll ===
        // 用帧末粘滞指针坐标 (50, 25) 重做 hit-test：scrollOffsetY=50 时 itemB 在视口 y 0-50，命中 itemB
        runtime.reconcileHoverAfterScroll(root, px, py, 0, 0);

        // hover signal 走 queueWrite，需 flush 才生效
        runtime.flush();

        Assert.assertEquals("reconcile+flush 后 itemA.hovered 应切为 false", Boolean.FALSE, hoverA.get());
        Assert.assertEquals("reconcile+flush 后 itemB.hovered 应切为 true", Boolean.TRUE, hoverB.get());
        Assert.assertSame("reconcile 后 hoveredNode 应为 itemB", itemB, router.__getHoveredNode());
    }

    // ==================== B8-2：无 SCROLL 时 reconcile 是无副作用 no-op ====================

    /**
     * 本帧不含 SCROLL 事件（pendingHoverReconcile==false）时，
     * reconcileHoverAfterScroll 应直接返回，不改变任何 hover 状态。
     */
    @Test
    public void shouldNoOpWhenNoScrollInFrame() {
        SceneNode[] tree = buildScrollableTree();
        SceneNode root = tree[0];
        SceneNode itemA = tree[2];

        ReadableSignal<Boolean> hoverA = router.interactionState(itemA).hovered();

        // MOVE 进入 itemA
        runtime.route(root, buildFrame(ScenePointerAction.MOVE, 50, 25, SceneMouseButton.NONE, 0), 0, 0);
        runtime.flush();
        Assert.assertEquals("MOVE 后 itemA.hovered=true", Boolean.TRUE, hoverA.get());

        // 无 SCROLL 的帧：reconcile 应 no-op
        runtime.reconcileHoverAfterScroll(root, 50, 25, 0, 0);
        runtime.flush();

        Assert.assertEquals("无 SCROLL 时 reconcile 后 itemA.hovered 仍为 true", Boolean.TRUE, hoverA.get());
        Assert.assertSame("hoveredNode 仍为 itemA", itemA, router.__getHoveredNode());
    }

    // ==================== B8-3：reconcile 消费标记后第二次调用 no-op ====================

    /**
     * reconcileHoverAfterScroll 消费 pendingHoverReconcile 后清零，
     * 再次调用（无新 SCROLL）应为 no-op，不会重复切换。
     */
    @Test
    public void shouldClearPendingFlagAfterReconcile() {
        SceneNode[] tree = buildScrollableTree();
        SceneNode root = tree[0];
        SceneNode viewport = tree[1];
        SceneNode itemA = tree[2];
        SceneNode itemB = tree[3];

        router.interactionState(itemA).hovered();
        ReadableSignal<Boolean> hoverB = router.interactionState(itemB).hovered();

        // MOVE 进入 itemA
        runtime.route(root, buildFrame(ScenePointerAction.MOVE, 50, 25, SceneMouseButton.NONE, 0), 0, 0);
        runtime.flush();

        // SCROLL + flush + 模拟 scrollOffsetY 生效
        runtime.route(root, buildFrame(ScenePointerAction.SCROLL, 50, 25, SceneMouseButton.NONE, -50), 0, 0);
        runtime.flush();
        viewport.setScrollOffsetY(50);

        // 第一次 reconcile：切换到 itemB
        runtime.reconcileHoverAfterScroll(root, 50, 25, 0, 0);
        runtime.flush();
        Assert.assertEquals("首次 reconcile 后 itemB.hovered=true", Boolean.TRUE, hoverB.get());
        Assert.assertSame("hoveredNode=itemB", itemB, router.__getHoveredNode());

        // 第二次 reconcile（无新 SCROLL）：应 no-op，hover 不变
        runtime.reconcileHoverAfterScroll(root, 50, 25, 0, 0);
        runtime.flush();
        Assert.assertEquals("第二次 reconcile 后 itemB.hovered 仍为 true", Boolean.TRUE, hoverB.get());
        Assert.assertSame("hoveredNode 仍为 itemB", itemB, router.__getHoveredNode());
    }

    // ==================== B8-4：滚动后指针移出整树 → hover 清空 ====================

    /**
     * 滚动后指针位置下方无任何节点（移出视口/整树）时，
     * reconcile 应将 hover 清空（hoveredNode=null，旧节点 hovered=false）。
     */
    @Test
    public void shouldClearHoverWhenPointerOutOfTreeAfterScroll() {
        SceneNode[] tree = buildScrollableTree();
        SceneNode root = tree[0];
        SceneNode viewport = tree[1];
        SceneNode itemA = tree[2];

        ReadableSignal<Boolean> hoverA = router.interactionState(itemA).hovered();

        // MOVE 进入 itemA
        runtime.route(root, buildFrame(ScenePointerAction.MOVE, 50, 25, SceneMouseButton.NONE, 0), 0, 0);
        runtime.flush();
        Assert.assertEquals("MOVE 后 itemA.hovered=true", Boolean.TRUE, hoverA.get());

        // SCROLL + flush + scrollOffsetY 生效
        runtime.route(root, buildFrame(ScenePointerAction.SCROLL, 50, 25, SceneMouseButton.NONE, -50), 0, 0);
        runtime.flush();
        viewport.setScrollOffsetY(50);

        // 用树外坐标 reconcile（200,200 在 root 100x100 之外）
        runtime.reconcileHoverAfterScroll(root, 200, 200, 0, 0);
        runtime.flush();

        Assert.assertEquals("移出整树后 itemA.hovered 应为 false", Boolean.FALSE, hoverA.get());
        Assert.assertNull("hoveredNode 应为 null", router.__getHoveredNode());
    }
}