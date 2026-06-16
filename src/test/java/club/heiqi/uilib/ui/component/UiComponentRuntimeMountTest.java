package club.heiqi.uilib.ui.component;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.component.UiComponentRuntime.MountHandle;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * {@link UiComponentRuntime} 的组件挂载模型契约测试（信条二，I3：组件函数只跑一次）。
 *
 * <p>验证：mount 的组件函数只执行一次；组件内的 effect 自动归属组件作用域；unmount 递归清理
 * effect 并把组件根节点从 DOM 摘除；嵌套 mount 的子组件随父组件卸载一并清理；dispose 清理全部。</p>
 */
public class UiComponentRuntimeMountTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    // ── 组件只跑一次 + 节点挂载 ──────────────────────────────────────────────────

    @Test
    public void componentFunctionRunsExactlyOnceAndAppendsRoot() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        AtomicInteger buildCount = new AtomicInteger(0);
        MountHandle handle = runtime.mount(root, doc -> {
            buildCount.incrementAndGet();
            return doc.div();
        });

        Assert.assertEquals("组件函数应只跑一次", 1, buildCount.get());
        Assert.assertSame("组件根应挂到 parent 下", root, handle.getRoot().getParent());

        ReactiveScheduler.get().flush();
        ReactiveScheduler.get().flush();
        Assert.assertEquals(1, buildCount.get());
    }

    // ── 组件内 effect 自动归属组件作用域 ─────────────────────────────────────────

    @Test
    public void effectInsideComponentAutoAttachesToComponentScope() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<Integer> bg = Signal.create(0xFF111111);
        MountHandle handle = runtime.mount(root, doc -> {
            ElementNode card = doc.div();
            runtime.bindBackgroundColor(card, bg);
            return card;
        });
        ElementNode card = handle.getRoot();

        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(0xFF111111), card.style().getBackgroundColor());

        bg.set(0xFF222222);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(0xFF222222), card.style().getBackgroundColor());

        handle.unmount();
        bg.set(0xFF333333);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("卸载后 effect 应停止写入",
                Integer.valueOf(0xFF222222), card.style().getBackgroundColor());
    }

    // ── unmount 从 DOM 摘除节点 ─────────────────────────────────────────────────

    @Test
    public void unmountRemovesRootFromDom() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        MountHandle handle = runtime.mount(root, doc -> doc.div());
        ElementNode mounted = handle.getRoot();
        Assert.assertSame(root, mounted.getParent());

        handle.unmount();
        Assert.assertNull("卸载后组件根应从 DOM 摘除", mounted.getParent());
    }

    @Test
    public void unmountIsIdempotent() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        MountHandle handle = runtime.mount(root, doc -> doc.div());
        handle.unmount();
        handle.unmount();
        Assert.assertNull(handle.getRoot().getParent());
    }

    // ── 嵌套 mount ──────────────────────────────────────────────────────────────

    @Test
    public void nestedComponentDisposedWhenParentUnmounts() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<Integer> childBg = Signal.create(0xFFAAAAAA);
        ElementNode[] childHolder = new ElementNode[1];
        MountHandle parentHandle = runtime.mount(root, doc -> {
            ElementNode parentEl = doc.div();
            MountHandle childHandle = runtime.mount(parentEl, childDoc -> {
                ElementNode childEl = childDoc.div();
                runtime.bindBackgroundColor(childEl, childBg);
                return childEl;
            });
            childHolder[0] = childHandle.getRoot();
            return parentEl;
        });

        ReactiveScheduler.get().flush();
        ElementNode child = childHolder[0];
        Assert.assertEquals(Integer.valueOf(0xFFAAAAAA), child.style().getBackgroundColor());

        parentHandle.unmount();
        childBg.set(0xFFBBBBBB);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("父组件卸载应递归清理子组件 effect",
                Integer.valueOf(0xFFAAAAAA), child.style().getBackgroundColor());
        Assert.assertNull("父组件卸载应摘除整棵子树", parentHandle.getRoot().getParent());
    }

    // ── dispose 清理全部 ────────────────────────────────────────────────────────

    @Test
    public void runtimeDisposeDisposesAllMountedComponents() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<Integer> bg = Signal.create(0xFF010101);
        MountHandle handle = runtime.mount(root, doc -> {
            ElementNode el = doc.div();
            runtime.bindBackgroundColor(el, bg);
            return el;
        });
        ElementNode el = handle.getRoot();
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(0xFF010101), el.style().getBackgroundColor());

        runtime.dispose();
        bg.set(0xFF020202);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("runtime.dispose 应清理全部组件 effect",
                Integer.valueOf(0xFF010101), el.style().getBackgroundColor());
    }
}
