package club.heiqi.uilib.ui.component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.component.UiComponentRuntime.ConditionHandle;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * {@link UiComponentRuntime#show} 条件渲染与 {@link UiComponentRuntime#bindText} 文本绑定契约测试
 * （信条二/三，I1/I5/I7）。
 *
 * <p>覆盖：条件首帧挂载/不挂载、布尔翻转增删内容、稳定值不重建（I7）、内容作用域随卸载清理、
 * 条件块整体 dispose、嵌套条件、内容内部 signal 不触发条件重算（I5 红线）、声明顺序位置、
 * bindText 首帧应用/signal 驱动/null 跳过。</p>
 */
public class UiComponentRuntimeShowTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    /** 取 container 下各子节点的 data-id 属性序列（含 anchor 的 data-ui-show-anchor）。 */
    private static List<String> idOrder(ElementNode container) {
        List<String> ids = new ArrayList<>();
        for (DocumentNode child : container.getChildren()) {
            ElementNode el = (ElementNode) child;
            if ("true".equals(el.getAttribute("data-ui-show-anchor"))) {
                ids.add("#anchor");
            } else {
                ids.add(el.getAttribute("data-id"));
            }
        }
        return ids;
    }

    /** container 下非锚点的元素子节点数量（= 实际渲染内容数）。 */
    private static int contentCount(ElementNode container) {
        int n = 0;
        for (DocumentNode child : container.getChildren()) {
            ElementNode el = (ElementNode) child;
            if (!"true".equals(el.getAttribute("data-ui-show-anchor"))) {
                n++;
            }
        }
        return n;
    }

    // ── 首帧挂载 ────────────────────────────────────────────────────────────────

    @Test
    public void mountsContentWhenConditionTrueOnFirstFlush() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<Boolean> show = Signal.create(Boolean.TRUE);
        runtime.show(root, show, doc -> doc.div().setAttribute("data-id", "content"));

        ReactiveScheduler.get().flush();
        Assert.assertEquals(1, contentCount(root));
        Assert.assertEquals(java.util.Arrays.asList("content", "#anchor"), idOrder(root));
    }

    @Test
    public void doesNotMountWhenConditionFalseOnFirstFlush() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<Boolean> show = Signal.create(Boolean.FALSE);
        runtime.show(root, show, doc -> doc.div().setAttribute("data-id", "content"));

        ReactiveScheduler.get().flush();
        Assert.assertEquals("条件为假不挂载内容", 0, contentCount(root));
        // 锚点始终在场
        Assert.assertEquals(java.util.Arrays.asList("#anchor"), idOrder(root));
    }

    @Test
    public void nullConditionTreatedAsFalse() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<Boolean> show = Signal.create(null);
        runtime.show(root, show, doc -> doc.div().setAttribute("data-id", "content"));

        ReactiveScheduler.get().flush();
        Assert.assertEquals(0, contentCount(root));
    }

    // ── 布尔翻转：挂载/卸载 ──────────────────────────────────────────────────────

    @Test
    public void togglingConditionMountsAndUnmountsContent() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<Boolean> show = Signal.create(Boolean.FALSE);
        runtime.show(root, show, doc -> doc.div().setAttribute("data-id", "content"));
        ReactiveScheduler.get().flush();
        Assert.assertEquals(0, contentCount(root));

        show.set(Boolean.TRUE);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("翻转为真 → 挂载", 1, contentCount(root));

        show.set(Boolean.FALSE);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("翻转为假 → 卸载", 0, contentCount(root));

        show.set(Boolean.TRUE);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("再次翻转为真 → 重新挂载", 1, contentCount(root));
    }

    // ── 稳定不重建（I7） ─────────────────────────────────────────────────────────

    @Test
    public void stableConditionDoesNotRebuildContent() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        AtomicInteger builds = new AtomicInteger();
        Signal<Boolean> show = Signal.create(Boolean.TRUE);
        runtime.show(root, show, doc -> {
            builds.incrementAndGet();
            return doc.div().setAttribute("data-id", "content");
        });
        ReactiveScheduler.get().flush();
        ElementNode firstContent = (ElementNode) root.getChildren().get(0);
        Assert.assertEquals(1, builds.get());

        // 条件值保持 true，再 flush 几次：内容不应重建
        show.set(Boolean.TRUE);    // 等值，Signal.set 去重，不会触发
        ReactiveScheduler.get().flush();
        ReactiveScheduler.get().flush();
        Assert.assertEquals("稳定 true 不重建内容", 1, builds.get());
        Assert.assertSame("复用同一 DOM 节点", firstContent, root.getChildren().get(0));
    }

    @Test
    public void rebuildsFreshContentAfterToggleCycle() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        AtomicInteger builds = new AtomicInteger();
        Signal<Boolean> show = Signal.create(Boolean.TRUE);
        runtime.show(root, show, doc -> {
            builds.incrementAndGet();
            return doc.div().setAttribute("data-id", "content");
        });
        ReactiveScheduler.get().flush();
        ElementNode firstContent = (ElementNode) root.getChildren().get(0);

        show.set(Boolean.FALSE);
        ReactiveScheduler.get().flush();
        show.set(Boolean.TRUE);
        ReactiveScheduler.get().flush();

        Assert.assertEquals("卸载后再挂载 → 重新构建一次", 2, builds.get());
        Assert.assertNotSame("是新的 DOM 节点", firstContent, root.getChildren().get(0));
    }

    // ── 内容作用域随卸载清理 ─────────────────────────────────────────────────────

    @Test
    public void unmountDisposesContentScopeEffects() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        List<Integer> effectRuns = new ArrayList<>();
        Signal<Boolean> show = Signal.create(Boolean.TRUE);
        Signal<Integer> inner = Signal.create(0);
        runtime.show(root, show, doc -> {
            ElementNode node = doc.div().setAttribute("data-id", "content");
            // 内容内部建一个 effect，订阅 inner
            runtime.createEffect(club.heiqi.uilib.ui.style.UiStyleChangeImpact.PAINT,
                    () -> effectRuns.add(inner.get()));
            return node;
        });
        ReactiveScheduler.get().flush();   // effectRuns=[0]
        Assert.assertEquals(1, effectRuns.size());

        show.set(Boolean.FALSE);           // 卸载内容 → 其 effect 应被 dispose
        ReactiveScheduler.get().flush();

        inner.set(99);                     // 内容已卸载，其 effect 不应再跑
        ReactiveScheduler.get().flush();
        Assert.assertEquals("卸载后内容 effect 不再重跑", 1, effectRuns.size());
    }

    // ── 条件块整体 dispose ───────────────────────────────────────────────────────

    @Test
    public void handleDisposeRemovesContentAndAnchor() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<Boolean> show = Signal.create(Boolean.TRUE);
        ConditionHandle handle =
                runtime.show(root, show, doc -> doc.div().setAttribute("data-id", "content"));
        ReactiveScheduler.get().flush();
        Assert.assertEquals(2, root.getChildCount());   // content + anchor

        handle.dispose();
        Assert.assertEquals("dispose 后内容与锚点都摘除", 0, root.getChildCount());

        // dispose 后条件变化不再有任何效果
        show.set(Boolean.FALSE);
        ReactiveScheduler.get().flush();
        show.set(Boolean.TRUE);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(0, root.getChildCount());
    }

    @Test
    public void runtimeDisposeCleansShowBlock() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<Boolean> show = Signal.create(Boolean.TRUE);
        runtime.show(root, show, doc -> doc.div().setAttribute("data-id", "content"));
        ReactiveScheduler.get().flush();
        Assert.assertEquals(2, root.getChildCount());

        runtime.dispose();
        Assert.assertEquals("runtime.dispose 清理条件块", 0, root.getChildCount());
    }

    // ── 声明顺序位置 ─────────────────────────────────────────────────────────────

    @Test
    public void contentInsertedAtDeclarationPositionBeforeAnchor() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        // 先 append 一个头部，再 show，再 append 一个尾部 → 内容应落在头尾之间
        root.append(document.div().setAttribute("data-id", "header"));
        Signal<Boolean> show = Signal.create(Boolean.FALSE);
        runtime.show(root, show, doc -> doc.div().setAttribute("data-id", "body"));
        root.append(document.div().setAttribute("data-id", "footer"));

        ReactiveScheduler.get().flush();
        // 未显示：header, #anchor, footer
        Assert.assertEquals(java.util.Arrays.asList("header", "#anchor", "footer"), idOrder(root));

        show.set(Boolean.TRUE);
        ReactiveScheduler.get().flush();
        // 显示：body 落在锚点之前 → header, body, #anchor, footer
        Assert.assertEquals(java.util.Arrays.asList("header", "body", "#anchor", "footer"), idOrder(root));
    }

    // ── 嵌套条件 ─────────────────────────────────────────────────────────────────

    @Test
    public void nestedShowDisposedWhenParentUnmounts() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        List<Integer> innerRuns = new ArrayList<>();
        Signal<Boolean> outer = Signal.create(Boolean.TRUE);
        Signal<Boolean> innerShow = Signal.create(Boolean.TRUE);
        Signal<Integer> innerData = Signal.create(0);

        runtime.show(root, outer, doc -> {
            ElementNode outerNode = doc.div().setAttribute("data-id", "outer");
            runtime.show(outerNode, innerShow, d2 -> {
                ElementNode innerNode = d2.div().setAttribute("data-id", "inner");
                runtime.createEffect(club.heiqi.uilib.ui.style.UiStyleChangeImpact.PAINT,
                        () -> innerRuns.add(innerData.get()));
                return innerNode;
            });
            return outerNode;
        });
        ReactiveScheduler.get().flush();
        Assert.assertEquals(1, innerRuns.size());

        // 卸载外层 → 内层条件块及其 effect 应递归清理
        outer.set(Boolean.FALSE);
        ReactiveScheduler.get().flush();

        innerData.set(42);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("外层卸载 → 内层 effect 递归清理", 1, innerRuns.size());
    }

    // ── I5 红线：内容内部 signal 不触发条件重算 ───────────────────────────────────

    @Test
    public void contentInternalSignalDoesNotReEvaluateCondition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        AtomicInteger builds = new AtomicInteger();
        Signal<Boolean> show = Signal.create(Boolean.TRUE);
        Signal<String> innerText = Signal.create("hi");
        runtime.show(root, show, doc -> {
            builds.incrementAndGet();
            ElementNode node = doc.div().setAttribute("data-id", "content");
            TextNode tn = node.appendText("");
            runtime.bindText(tn, innerText);   // 内容内部读 innerText
            return node;
        });
        ReactiveScheduler.get().flush();
        Assert.assertEquals(1, builds.get());

        // 改内容内部 signal：只应更新文本，不应触发条件重算/内容重建（守 I5）
        innerText.set("bye");
        ReactiveScheduler.get().flush();
        Assert.assertEquals("内容内部 signal 变化不重建内容", 1, builds.get());
    }

    // ── bindText ─────────────────────────────────────────────────────────────────

    @Test
    public void bindTextAppliesInitialValueOnFlush() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        TextNode tn = root.appendText("");
        Signal<String> text = Signal.create("hello");
        runtime.bindText(tn, text);

        ReactiveScheduler.get().flush();
        Assert.assertEquals("hello", tn.getText());
    }

    @Test
    public void bindTextReappliesWhenSignalChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        TextNode tn = root.appendText("");
        Signal<String> text = Signal.create("a");
        runtime.bindText(tn, text);
        ReactiveScheduler.get().flush();

        text.set("b");
        ReactiveScheduler.get().flush();
        Assert.assertEquals("b", tn.getText());
    }

    @Test
    public void bindTextSkipsNullValue() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        TextNode tn = root.appendText("seed");
        Signal<String> text = Signal.create(null);
        runtime.bindText(tn, text);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("null 值跳过，保留原文本", "seed", tn.getText());

        text.set("filled");
        ReactiveScheduler.get().flush();
        Assert.assertEquals("filled", tn.getText());
    }
}
