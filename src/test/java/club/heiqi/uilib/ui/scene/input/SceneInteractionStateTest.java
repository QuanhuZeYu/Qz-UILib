package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * SceneInteractionState 交互状态容器单元测试（组 A + F）。
 *
 * <p>覆盖：懒创建、null 短路、幂等、onCleanup 生命周期回收。</p>
 */
public class SceneInteractionStateTest {

    private SceneInputRouter router;
    private InputFrameBuilder frameBuilder;
    private SceneRuntime runtime;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        router = new SceneInputRouter();
        frameBuilder = new InputFrameBuilder(0, 0);
        runtime = new SceneRuntime();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== A1：interactionState 返回非 null，未调 .hovered() 时 __hasHoveredSignal()==false ====================

    @Test
    public void shouldReturnNonNullContainerWithoutCreatingSignals() {
        SceneNode node = new SceneNode();

        SceneInteractionState state = router.interactionState(node);
        Assert.assertNotNull("interactionState 应返回非 null 容器", state);

        Assert.assertFalse("未调 hovered() 前不应创建 hovered signal", state.__hasHoveredSignal());
        Assert.assertFalse("未调 pressed() 前不应创建 pressed signal", state.__hasPressedSignal());
        Assert.assertFalse("未调 focused() 前不应创建 focused signal", state.__hasFocusedSignal());
    }

    // ==================== A2：调 .hovered() 后 __hasHoveredSignal()==true，再调返回同一 ReadableSignal ====================

    @Test
    public void shouldCreateHoveredSignalOnFirstAccessAndReturnSameInstance() {
        SceneNode node = new SceneNode();
        SceneInteractionState state = router.interactionState(node);

        Assert.assertFalse("调用前 hovered signal 不存在", state.__hasHoveredSignal());

        ReadableSignal<Boolean> s1 = state.hovered();
        Assert.assertTrue("调用后 hovered signal 应已创建", state.__hasHoveredSignal());
        Assert.assertEquals("初始值应为 false", Boolean.FALSE, s1.get());

        ReadableSignal<Boolean> s2 = state.hovered();
        Assert.assertSame("多次调用应返回同一实例（幂等）", s1, s2);
    }

    // ==================== A3：同 node 多次 interactionState(node) 返回同一容器实例 ====================

    @Test
    public void shouldReturnSameContainerForSameNode() {
        SceneNode node = new SceneNode();

        SceneInteractionState s1 = router.interactionState(node);
        SceneInteractionState s2 = router.interactionState(node);

        Assert.assertSame("同 node 多次调用应返回同一实例", s1, s2);
    }

    // ==================== A4：零开销硬核验 —— 从未声明关心的节点 MOVE 划过，signal 从未 set ====================

    @Test
    public void shouldNotWriteSignalForNodeWithoutDeclaredInterest() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);

        // 树构造完成后设置 LayoutBox
        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        child.setCachedLayout(new LayoutBox(20, 20, 80, 60));

        // 创建交互状态容器但绝不调 .hovered()
        SceneInteractionState state = router.interactionState(child);
        Assert.assertFalse("未声明关心，__hasHoveredSignal 为 false", state.__hasHoveredSignal());

        // MOVE 划过 child
        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.MOVE,
                40, 40, SceneMouseButton.NONE, 0, 0, 0,
                false, false, false, false, 1000L));
        SceneInputFrame frame = frameBuilder.drainFrame();
        router.route(root, frame, 0, 0);

        // ★ 零开销硬保证：writeHovered 因 hovered==null 短路，signal 从未被创建
        Assert.assertFalse("route 后 signal 仍不应被创建（writeHovered 短路）",
                state.__hasHoveredSignal());
    }

    // ==================== F1：mount 内声明 → dispose 子 Owner → entry 被移除 ====================
    @Test
    public void shouldRemoveInteractionStateOnOwnerDisposeWithExplicitOwner() {
        SceneNode node = new SceneNode();
        Owner owner = new Owner();

        // 在 Owner 作用域内声明 interactionState
        owner.run(() -> {
            router.interactionState(node).hovered();
        });

        Assert.assertTrue("Owner 作用域内声名后，interactionStates 应含 node",
                router.__hasInteractionState(node));

        // dispose Owner → 触发 onCleanup → interactionStates.remove(node)
        owner.dispose();
        Assert.assertFalse("Owner dispose 后，interactionStates 不应含 node",
                router.__hasInteractionState(node));
    }
}
