package club.heiqi.uilib.ui.scene.testkit;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneInputType;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

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

    // ==================== press/release：分帧 CLICK 合成 ====================

    /**
     * press(node) 后 pressed=true；release(node) 后 CLICK 触发 + pressed=false。
     *
     * <p>验证跨 flush 的 CLICK 合成——press 与 release 分两帧 route，Router 仍能在同节点
     * DOWN+UP 时合成 CLICK。</p>
     */
    @Test
    public void press_releaseShouldSynthesizeClick() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setPreferredWidth(60);
        child.setPreferredHeight(40);
        root.appendChild(child);
        harness.mountRoot(root, 200, 200);

        ReadableSignal<Boolean> pressed = harness.getRuntime().interactionState(child).pressed();
        AtomicInteger clickCount = new AtomicInteger(0);
        harness.getRuntime().on(child, SceneEventType.CLICK,
                (evt, ctx) -> clickCount.incrementAndGet());

        harness.press(child);
        Assert.assertEquals("press 后 pressed 应为 true", Boolean.TRUE, pressed.get());
        Assert.assertEquals("press 后尚未 release，CLICK 不应触发", 0, clickCount.get());

        harness.release(child);
        Assert.assertEquals("release 后 pressed 应翻 false", Boolean.FALSE, pressed.get());
        Assert.assertEquals("release 后应合成一次 CLICK", 1, clickCount.get());
    }

    // ==================== typeText：文本注入到焦点节点 ====================

    /**
     * click(textInput) 聚焦后 typeText("hello")，文本应进入输入框触发 onChange。
     */
    @Test
    public void typeTextShouldRouteToFocusedNode() {
        SceneNode root = new SceneNode();
        Signal<String> valueSignal = Signal.create("");
        Signal<Boolean> enabledSignal = Signal.create(Boolean.TRUE);
        Signal<Boolean> readOnlySignal = Signal.create(Boolean.FALSE);
        AtomicInteger changeCount = new AtomicInteger(0);
        AtomicInteger lastChangeCount = new AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<String> lastChangeValue =
                new java.util.concurrent.atomic.AtomicReference<>(null);
        SceneTextInput.Props props = new SceneTextInput.Props(
                valueSignal, enabledSignal, readOnlySignal,
                "", 32, SceneInputType.TEXT,
                next -> {
                    changeCount.incrementAndGet();
                    lastChangeValue.set(next);
                });
        MountHandle handle = harness.getRuntime().mount(root, SceneTextInput.create(harness.getRuntime(), props));
        SceneNode inputRoot = handle.getRoot();
        harness.mountRoot(root, 400, 100);

        // click 聚焦输入框（mountRoot 已 layout，inputRoot 有非零盒）
        harness.click(inputRoot);
        // 若 click 未建立焦点（hit-test 边界差异），显式 requestFocus 兜底
        if (harness.getRuntime().getFocusedNode() != inputRoot) {
            harness.getRuntime().requestFocus(inputRoot);
            harness.flush();
        }

        harness.typeText("hello");

        Assert.assertEquals("typeText 应触发一次 onChange", 1, changeCount.get());
        Assert.assertEquals("onChange 上抛新值 hello", "hello", lastChangeValue.get());
    }

    // ==================== moveAt：hover-out 坐标版 ====================

    /**
     * moveTo(node) 进入 hover，moveAt 到节点外坐标触发 hover 退出。
     */
    @Test
    public void moveAtShouldTriggerHoverOut() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setPreferredWidth(60);
        child.setPreferredHeight(40);
        root.appendChild(child);
        harness.mountRoot(root, 200, 200);

        ReadableSignal<Boolean> hovered = harness.getRuntime().interactionState(child).hovered();
        Assert.assertFalse("初始未 hover", hovered.get().booleanValue());

        harness.moveTo(child);
        Assert.assertTrue("moveTo(node) 后应进入 hovered", hovered.get().booleanValue());

        // 移到节点外（child 块在 (0,0)~(60,40)，(-10,-10) 在树外）
        harness.moveAt(-10, -10);
        Assert.assertFalse("moveAt 到节点外应退出 hovered", hovered.get().booleanValue());
    }

    // ==================== create(measurer)：度量可用 ====================

    /**
     * create(measurer) 创建后 runtime.measureTextWidth 不抛异常。
     */
    @Test
    public void createWithMeasurerShouldAllowMeasureTextWidth() {
        SceneTextMeasurer measurer = new club.heiqi.uilib.ui.scene.FixedTextMeasurer();
        SceneInteractionHarness h = SceneInteractionHarness.create(measurer);
        try {
            int width = h.getRuntime().measureTextWidth("hello", 16);
            Assert.assertTrue("measureTextWidth 应返回非负值", width >= 0);
        } finally {
            h.dispose();
        }
    }
}
