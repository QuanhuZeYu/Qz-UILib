package club.heiqi.uilib.ui.scene.testkit;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * {@link SceneInteractionHarness} 自测 —— 锁定语义化交互注入范式的正确性。
 *
 * <p>4 用例覆盖 click / moveTo / scroll / clickAt 四个核心入口，断言交互事件确实被
 * 正确 route 到目标节点并触发预期状态（CLICK 合成 / hovered / SCROLL 派发）。
 * 与现有 {@code SceneButtonTest} / {@code SceneScrollViewportTest} 范式等价但消除坐标硬编码。</p>
 */
public class SceneInteractionHarnessTest {

    private SceneInteractionHarness harness;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
    }

    @After
    public void tearDown() {
        harness.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== click：CLICK 合成 ====================

    /**
     * click(node) 在节点中心 DOWN+UP，Router 合成 CLICK 派发到 on(node, CLICK)。
     */
    @Test
    public void clickShouldTriggerOnClickSignal() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setPreferredWidth(60);
        child.setPreferredHeight(40);
        root.appendChild(child);
        harness.mountRoot(root, 200, 200);

        AtomicInteger clickCount = new AtomicInteger(0);
        harness.getRuntime().on(child, SceneEventType.CLICK,
                (evt, ctx) -> clickCount.incrementAndGet());

        harness.click(child);

        Assert.assertEquals("click(node) 应触发一次 CLICK", 1, clickCount.get());
    }

    // ==================== moveTo：hover 进入 ====================

    /**
     * moveTo(node) 触发 hover 进入，hovered signal 翻 true。
     *
     * <p>hovered 须在 route 前声明关心（懒创建时序契约）。</p>
     */
    @Test
    public void moveToShouldSetHovered() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setPreferredWidth(60);
        child.setPreferredHeight(40);
        root.appendChild(child);
        harness.mountRoot(root, 200, 200);

        // 先声明关心 hovered（懒创建时序契约，须在 route 之前）
        ReadableSignal<Boolean> hovered = harness.getRuntime().interactionState(child).hovered();
        Assert.assertFalse("初始未 hover", hovered.get().booleanValue());

        harness.moveTo(child);

        Assert.assertTrue("moveTo(node) 后应进入 hovered", hovered.get().booleanValue());
    }

    // ==================== scroll：SCROLL 派发 ====================

    /**
     * scroll(node, wheelDelta) 在节点中心 SCROLL，wheelDelta 透传到 on(node, SCROLL)。
     */
    @Test
    public void scrollShouldDispatchScrollEvent() {
        SceneNode root = new SceneNode();
        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setPreferredWidth(100);
        viewport.setPreferredHeight(100);
        SceneNode content = new SceneNode();
        content.setPreferredHeight(400);
        viewport.appendChild(content);
        root.appendChild(viewport);
        harness.mountRoot(root, 200, 200);

        AtomicInteger scrollCount = new AtomicInteger(0);
        AtomicInteger capturedDelta = new AtomicInteger(0);
        harness.getRuntime().on(viewport, SceneEventType.SCROLL, (evt, ctx) -> {
            scrollCount.incrementAndGet();
            capturedDelta.set(evt.getWheelDelta());
        });

        harness.scroll(viewport, -120);

        Assert.assertEquals("scroll 应派发一次 SCROLL", 1, scrollCount.get());
        Assert.assertEquals("SCROLL wheelDelta 应透传 -120", -120, capturedDelta.get());
    }

    // ==================== clickAt：坐标型入口冒烟 ====================

    /**
     * clickAt(x, y) 绕过节点中心计算，直接坐标点击，同样触发 CLICK 合成。
     *
     * <p>child 块级堆叠在 (0,0)，60x40，几何中心 (30,20)。</p>
     */
    @Test
    public void clickAtShouldTriggerClick() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setPreferredWidth(60);
        child.setPreferredHeight(40);
        root.appendChild(child);
        harness.mountRoot(root, 200, 200);

        AtomicInteger clickCount = new AtomicInteger(0);
        harness.getRuntime().on(child, SceneEventType.CLICK,
                (evt, ctx) -> clickCount.incrementAndGet());

        harness.clickAt(30, 20);

        Assert.assertEquals("clickAt 坐标型点击应触发 CLICK", 1, clickCount.get());
    }
}
