package club.heiqi.uilib.ui.control;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.component.UiComponentRuntime;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * {@link ReactiveControlBindings} 控件事件桥接（输入半环：控件事件 → signal）契约测试。
 *
 * <p>因 {@link DocumentButtonControl}/{@link DocumentToggleSwitchControl} 未暴露 handler getter，
 * 退订验证经「卸载后触发控件元素的点击 → 桥接回调不再执行」间接坐实（控件内部 click handler 会调用
 * action/change handler，setXxxHandler(null) 后不再回调）。</p>
 */
public class ReactiveControlBindingsTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    /** 模拟左键点击控件根元素（走控件内部 click handler → action/change）。 */
    private static void clickElement(ElementNode element) {
        element.getClickHandler()
                .onClick(new DocumentElementClickEvent(element, element, 0, 0, 0, 1L));
    }

    // ── onAction：桥接按钮 action → 回调 ─────────────────────────────────────────

    @Test
    public void onActionBridgesButtonActionToCallback() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        DocumentButtonControl button = new DocumentButtonControl(document, "btn");
        AtomicInteger actions = new AtomicInteger(0);
        ReactiveControlBindings.onAction(runtime, button, actions::incrementAndGet);

        clickElement(button.getElement());
        Assert.assertEquals("点击按钮应触发桥接回调", 1, actions.get());
    }

    // ── onAction：卸载后退订（不再回调） ────────────────────────────────────────

    @Test
    public void onActionUnsubscribesAfterRuntimeDispose() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        DocumentButtonControl button = new DocumentButtonControl(document, "btn");
        AtomicInteger actions = new AtomicInteger(0);
        ReactiveControlBindings.onAction(runtime, button, actions::incrementAndGet);

        clickElement(button.getElement());
        Assert.assertEquals(1, actions.get());

        runtime.dispose();
        clickElement(button.getElement());   // actionHandler 已退订，桥接回调不再触发
        Assert.assertEquals("卸载后再点击不应回调（actionHandler 已退订）", 1, actions.get());
    }

    // ── onToggle：桥接开关变更 → 把新开关态喂给 consumer（改 signal） ──────────────

    @Test
    public void onToggleBridgesChangeToSignal() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        DocumentToggleSwitchControl toggle = new DocumentToggleSwitchControl(document);
        Signal<Boolean> state = Signal.create(Boolean.FALSE);
        ReactiveControlBindings.onToggle(runtime, toggle, state::set);

        clickElement(toggle.getElement());   // toggle off → on，喂 true
        ReactiveScheduler.get().flush();
        Assert.assertEquals("开关切换应把新态写入 signal", Boolean.TRUE, state.get());

        clickElement(toggle.getElement());   // toggle on → off，喂 false
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Boolean.FALSE, state.get());
    }

    // ── onToggle：卸载后退订（不再回调） ────────────────────────────────────────

    @Test
    public void onToggleUnsubscribesAfterRuntimeDispose() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        DocumentToggleSwitchControl toggle = new DocumentToggleSwitchControl(document);
        AtomicReference<Boolean> last = new AtomicReference<>(null);
        ReactiveControlBindings.onToggle(runtime, toggle, last::set);

        clickElement(toggle.getElement());
        Assert.assertEquals(Boolean.TRUE, last.get());

        runtime.dispose();
        last.set(null);
        clickElement(toggle.getElement());   // changeHandler 已退订，consumer 不再被调
        Assert.assertNull("卸载后开关变更不再回调（changeHandler 已退订）", last.get());
    }

    // ── Binding.dispose 提前退订 ────────────────────────────────────────────────

    @Test
    public void bindingDisposeUnsubscribesEagerly() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        DocumentButtonControl button = new DocumentButtonControl(document, "btn");
        AtomicInteger actions = new AtomicInteger(0);
        UiComponentRuntime.Binding binding =
                ReactiveControlBindings.onAction(runtime, button, actions::incrementAndGet);

        clickElement(button.getElement());
        Assert.assertEquals(1, actions.get());

        binding.dispose();
        clickElement(button.getElement());
        Assert.assertEquals("Binding.dispose 后不再回调", 1, actions.get());

        // 幂等：再次 dispose 不抛、不重复退订
        binding.dispose();
    }
}
