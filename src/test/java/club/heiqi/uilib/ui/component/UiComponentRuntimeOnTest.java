package club.heiqi.uilib.ui.component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.component.UiComponentRuntime.Binding;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * {@link UiComponentRuntime#on(Runnable, Runnable)} / {@link UiComponentRuntime#onClick(ElementNode, Runnable)}
 * 事件订阅绑定契约测试（输入半环：外部事件源 → signal，信条一/I1）。
 *
 * <p>覆盖：建立时 register 立即执行、所属 Owner dispose 后 unregister 被调、Binding.dispose 幂等、
 * forEach 项作用域内 on 随项移除退订、onClick 触发 action + 卸载后清空 handler。</p>
 */
public class UiComponentRuntimeOnTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    // ── register 立即执行（订阅生效） ────────────────────────────────────────────

    @Test
    public void registerRunsImmediatelyOnBind() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        AtomicInteger registers = new AtomicInteger(0);
        AtomicInteger unregisters = new AtomicInteger(0);
        runtime.on(registers::incrementAndGet, unregisters::incrementAndGet);

        Assert.assertEquals("建立时 register 立即执行一次", 1, registers.get());
        Assert.assertEquals("建立时不退订", 0, unregisters.get());
    }

    // ── runtime 根作用域 dispose 后 unregister 被调 ──────────────────────────────

    @Test
    public void unregisterRunsWhenRootOwnerDisposed() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        AtomicInteger unregisters = new AtomicInteger(0);
        runtime.on(() -> { }, unregisters::incrementAndGet);

        Assert.assertEquals(0, unregisters.get());
        runtime.dispose();
        Assert.assertEquals("根作用域 dispose 自动退订", 1, unregisters.get());
    }

    // ── 组件挂载作用域 dispose 后 unregister 被调 ────────────────────────────────

    @Test
    public void unregisterRunsWhenMountScopeDisposed() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        AtomicInteger unregisters = new AtomicInteger(0);
        UiComponentRuntime.MountHandle handle = runtime.mount(root, doc -> {
            ElementNode panel = doc.div();
            runtime.on(() -> { }, unregisters::incrementAndGet);
            return panel;
        });

        Assert.assertEquals("挂载期 on 落到组件作用域，尚未退订", 0, unregisters.get());
        handle.unmount();
        Assert.assertEquals("组件卸载自动退订", 1, unregisters.get());
    }

    // ── Binding.dispose 幂等：多次调用只退订一次 ─────────────────────────────────

    @Test
    public void bindingDisposeIsIdempotent() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        AtomicInteger unregisters = new AtomicInteger(0);
        Binding binding = runtime.on(() -> { }, unregisters::incrementAndGet);

        binding.dispose();
        binding.dispose();
        binding.dispose();
        Assert.assertEquals("Binding.dispose 幂等：退订只跑一次", 1, unregisters.get());
    }

    // ── Binding.dispose 提前退订后，作用域 dispose 不再重复退订 ───────────────────

    @Test
    public void manualDisposeThenOwnerDisposeStillUnregistersOnce() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        AtomicInteger unregisters = new AtomicInteger(0);
        Binding binding = runtime.on(() -> { }, unregisters::incrementAndGet);

        binding.dispose();
        Assert.assertEquals(1, unregisters.get());
        runtime.dispose();   // 根作用域 onCleanup 再跑 binding.dispose → 不应重复退订
        Assert.assertEquals("手动退订 + 作用域自动退订总计只跑一次", 1, unregisters.get());
    }

    @Test
    public void ownerDisposeThenManualDisposeStillUnregistersOnce() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        AtomicInteger unregisters = new AtomicInteger(0);
        Binding binding = runtime.on(() -> { }, unregisters::incrementAndGet);

        runtime.dispose();   // 先由根作用域自动退订
        Assert.assertEquals(1, unregisters.get());
        binding.dispose();   // 再手动退订 → 幂等，不重复
        Assert.assertEquals("作用域自动退订 + 手动退订总计只跑一次", 1, unregisters.get());
    }

    // ── forEach 项作用域内 on：移除该项 → 该项 unregister 被调 ─────────────────────

    @Test
    public void onInForEachItemScopeUnregistersWhenItemRemoved() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        AtomicInteger bUnregisters = new AtomicInteger(0);
        Signal<List<String>> items = Signal.create(Arrays.asList("a", "b"));
        runtime.forEach(root, items, s -> s, (doc, s) -> {
            ElementNode el = doc.div().setAttribute("data-key", s);
            if (s.equals("b")) {
                // on 在 item owner 作用域内建立 → 行被移除时随该作用域退订
                runtime.on(() -> { }, bUnregisters::incrementAndGet);
            }
            return el;
        });
        ReactiveScheduler.get().flush();
        Assert.assertEquals("项构建期 on 尚未退订", 0, bUnregisters.get());

        // 移除 b → 其作用域 dispose，on 的 unregister 被调
        items.set(Arrays.asList("a"));
        ReactiveScheduler.get().flush();
        Assert.assertEquals("行移除即注销（坐实输入半环对称性）", 1, bUnregisters.get());
    }

    // ── onClick：左键触发 action ─────────────────────────────────────────────────

    @Test
    public void onClickRunsActionOnLeftClick() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        ElementNode el = document.div();
        root.append(el);
        AtomicInteger clicks = new AtomicInteger(0);
        runtime.onClick(el, clicks::incrementAndGet);

        Assert.assertNotNull("建立后 clickHandler 已注册", el.getClickHandler());
        boolean consumed = el.getClickHandler().onClick(
                new DocumentElementClickEvent(el, el, 0, 0, 0, 1L));
        Assert.assertTrue("左键点击应被消费", consumed);
        Assert.assertEquals("左键点击触发 action", 1, clicks.get());
    }

    // ── onClick：非左键不触发、不消费 ─────────────────────────────────────────────

    @Test
    public void onClickIgnoresNonLeftButton() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        ElementNode el = document.div();
        root.append(el);
        AtomicInteger clicks = new AtomicInteger(0);
        runtime.onClick(el, clicks::incrementAndGet);

        boolean consumed = el.getClickHandler().onClick(
                new DocumentElementClickEvent(el, el, 0, 0, 1, 1L));   // 右键
        Assert.assertFalse("非左键不消费，继续冒泡", consumed);
        Assert.assertEquals("非左键不触发 action", 0, clicks.get());
    }

    // ── onClick：卸载后 handler 被清空 ───────────────────────────────────────────

    @Test
    public void onClickClearsHandlerWhenScopeDisposed() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        ElementNode el = document.div();
        root.append(el);
        runtime.onClick(el, () -> { });
        Assert.assertNotNull(el.getClickHandler());

        runtime.dispose();
        Assert.assertNull("卸载后 clickHandler 被清空（自动退订）", el.getClickHandler());
    }

    @Test
    public void onClickClearsHandlerWhenBindingDisposed() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        ElementNode el = document.div();
        root.append(el);
        Binding binding = runtime.onClick(el, () -> { });
        Assert.assertNotNull(el.getClickHandler());

        binding.dispose();
        Assert.assertNull("Binding.dispose 提前清空 clickHandler", el.getClickHandler());
    }
}
