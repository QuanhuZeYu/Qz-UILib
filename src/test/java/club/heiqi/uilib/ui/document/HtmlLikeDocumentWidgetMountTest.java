package club.heiqi.uilib.ui.document;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.DeterministicTextMeasureService;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * `HtmlLikeDocumentWidget` 的组件挂载模型契约测试（信条二，I3：组件函数只跑一次）。
 *
 * <p>验证：mount 的组件函数只执行一次；组件内的 effect 自动归属组件作用域；unmount 递归清理
 * effect 并把组件根节点从 DOM 摘除；嵌套 mount 的子组件随父组件卸载一并清理；widget.close 清理全部。</p>
 *
 * <p>纯数据层路径（手动 {@link ReactiveScheduler#flush()}），不调 {@code render}（依赖 LWJGL native）。</p>
 */
public class HtmlLikeDocumentWidgetMountTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    private HtmlLikeDocumentWidget newWidget(UiDocument document) {
        return new HtmlLikeDocumentWidget(document, 80, 40, new DeterministicTextMeasureService());
    }

    // ── 组件只跑一次 + 节点挂载 ──────────────────────────────────────────────────

    @Test
    public void componentFunctionRunsExactlyOnceAndAppendsRoot() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        AtomicInteger buildCount = new AtomicInteger(0);
        HtmlLikeDocumentWidget.MountHandle handle = widget.mount(root, doc -> {
            buildCount.incrementAndGet();
            return doc.div();
        });

        Assert.assertEquals("组件函数应只跑一次", 1, buildCount.get());
        Assert.assertSame("组件根应挂到 parent 下", root, handle.getRoot().getParent());

        // 多次 flush 不应重跑组件函数（I3：只跑一次，动态行为在 effect 里）
        ReactiveScheduler.get().flush();
        ReactiveScheduler.get().flush();
        Assert.assertEquals(1, buildCount.get());
    }

    // ── 组件内 effect 自动归属组件作用域 ─────────────────────────────────────────

    @Test
    public void effectInsideComponentAutoAttachesToComponentScope() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<Integer> bg = Signal.create(0xFF111111);
        HtmlLikeDocumentWidget.MountHandle handle = widget.mount(root, doc -> {
            ElementNode card = doc.div();
            // 组件内 bind，应自动归属组件作用域，不泄漏到 widget 根
            widget.bindBackgroundColor(card, bg);
            return card;
        });
        ElementNode card = handle.getRoot();

        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(0xFF111111), card.style().getBackgroundColor());

        bg.set(0xFF222222);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(0xFF222222), card.style().getBackgroundColor());

        // 卸载后 effect 随组件作用域一并 dispose，signal 变化不再写入
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
        HtmlLikeDocumentWidget widget = newWidget(document);

        HtmlLikeDocumentWidget.MountHandle handle = widget.mount(root, doc -> doc.div());
        ElementNode mounted = handle.getRoot();
        Assert.assertSame(root, mounted.getParent());

        handle.unmount();
        Assert.assertNull("卸载后组件根应从 DOM 摘除", mounted.getParent());
    }

    @Test
    public void unmountIsIdempotent() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        HtmlLikeDocumentWidget.MountHandle handle = widget.mount(root, doc -> doc.div());
        handle.unmount();
        handle.unmount(); // 重复调用安全
        Assert.assertNull(handle.getRoot().getParent());
    }

    // ── 嵌套 mount ──────────────────────────────────────────────────────────────

    @Test
    public void nestedComponentDisposedWhenParentUnmounts() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<Integer> childBg = Signal.create(0xFFAAAAAA);
        ElementNode[] childHolder = new ElementNode[1];
        HtmlLikeDocumentWidget.MountHandle parentHandle = widget.mount(root, doc -> {
            ElementNode parentEl = doc.div();
            // 父组件构建期内嵌套挂载子组件，子组件作用域应归属父组件作用域
            HtmlLikeDocumentWidget.MountHandle childHandle = widget.mount(parentEl, childDoc -> {
                ElementNode childEl = childDoc.div();
                widget.bindBackgroundColor(childEl, childBg);
                return childEl;
            });
            childHolder[0] = childHandle.getRoot();
            return parentEl;
        });

        ReactiveScheduler.get().flush();
        ElementNode child = childHolder[0];
        Assert.assertEquals(Integer.valueOf(0xFFAAAAAA), child.style().getBackgroundColor());

        // 卸载父组件，子组件的 effect 应随之清理
        parentHandle.unmount();
        childBg.set(0xFFBBBBBB);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("父组件卸载应递归清理子组件 effect",
                Integer.valueOf(0xFFAAAAAA), child.style().getBackgroundColor());
        Assert.assertNull("父组件卸载应摘除整棵子树", parentHandle.getRoot().getParent());
    }

    // ── widget.close 清理全部 ───────────────────────────────────────────────────

    @Test
    public void widgetCloseDisposesAllMountedComponents() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        HtmlLikeDocumentWidget widget = newWidget(document);

        Signal<Integer> bg = Signal.create(0xFF010101);
        HtmlLikeDocumentWidget.MountHandle handle = widget.mount(root, doc -> {
            ElementNode el = doc.div();
            widget.bindBackgroundColor(el, bg);
            return el;
        });
        ElementNode el = handle.getRoot();
        ReactiveScheduler.get().flush();
        Assert.assertEquals(Integer.valueOf(0xFF010101), el.style().getBackgroundColor());

        widget.close();
        bg.set(0xFF020202);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("widget.close 应清理全部组件 effect",
                Integer.valueOf(0xFF010101), el.style().getBackgroundColor());
    }
}
