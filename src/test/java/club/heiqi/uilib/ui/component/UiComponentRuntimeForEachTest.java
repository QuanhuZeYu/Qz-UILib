package club.heiqi.uilib.ui.component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.component.UiComponentRuntime.ListHandle;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * {@link UiComponentRuntime#forEach} 的 keyed 列表协调契约测试（信条三，I5）。
 *
 * <p>覆盖：按 key 对齐复用、增删项、最小移动（稳定项零重建/零移动）、每项作用域随移除/列表卸载清理、
 * I5 红线（item 内部 signal 变化不触发整列表重协调）、重复 key 检测。</p>
 */
public class UiComponentRuntimeForEachTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    /** 取 container 下各子节点的 data-key 属性序列（DOM 实际顺序）。 */
    private static List<String> keyOrder(ElementNode container) {
        List<String> keys = new ArrayList<>();
        for (DocumentNode child : container.getChildren()) {
            keys.add(((ElementNode) child).getAttribute("data-key"));
        }
        return keys;
    }

    /** 构建一个以 item 字符串为 key、带 data-key 标记的简单项组件。 */
    private static ElementNode stringItem(UiDocument doc, String value) {
        return doc.div().setAttribute("data-key", value);
    }

    // ── 初次挂载：按顺序建项 ─────────────────────────────────────────────────────

    @Test
    public void initialMountBuildsItemsInOrder() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<List<String>> items = Signal.create(Arrays.asList("a", "b", "c"));
        runtime.forEach(root, items, s -> s, UiComponentRuntimeForEachTest::stringItem);

        ReactiveScheduler.get().flush();
        Assert.assertEquals(Arrays.asList("a", "b", "c"), keyOrder(root));
    }

    // ── 追加项：已存在项复用（不重建） ───────────────────────────────────────────

    @Test
    public void appendReusesExistingItemNodes() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        AtomicInteger builds = new AtomicInteger(0);
        Signal<List<String>> items = Signal.create(Arrays.asList("a", "b"));
        runtime.forEach(root, items, s -> s, (doc, s) -> {
            builds.incrementAndGet();
            return stringItem(doc, s);
        });
        ReactiveScheduler.get().flush();
        Assert.assertEquals(2, builds.get());

        ElementNode a = (ElementNode) root.getChildren().get(0);
        ElementNode b = (ElementNode) root.getChildren().get(1);

        items.set(Arrays.asList("a", "b", "c"));
        ReactiveScheduler.get().flush();

        Assert.assertEquals("只新建 c，a/b 复用", 3, builds.get());
        Assert.assertSame("a 节点复用", a, root.getChildren().get(0));
        Assert.assertSame("b 节点复用", b, root.getChildren().get(1));
        Assert.assertEquals(Arrays.asList("a", "b", "c"), keyOrder(root));
    }

    // ── 删除项：消失的 key 摘除 DOM ──────────────────────────────────────────────

    @Test
    public void removeDropsVanishedItems() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<List<String>> items = Signal.create(Arrays.asList("a", "b", "c"));
        runtime.forEach(root, items, s -> s, UiComponentRuntimeForEachTest::stringItem);
        ReactiveScheduler.get().flush();
        ElementNode b = (ElementNode) root.getChildren().get(1);

        items.set(Arrays.asList("a", "c"));
        ReactiveScheduler.get().flush();

        Assert.assertEquals(Arrays.asList("a", "c"), keyOrder(root));
        Assert.assertNull("被删除项应从 DOM 摘除", b.getParent());
    }

    // ── 重排：完全逆序，节点复用且顺序正确 ───────────────────────────────────────

    @Test
    public void reverseReordersWithoutRebuild() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        AtomicInteger builds = new AtomicInteger(0);
        Signal<List<String>> items = Signal.create(Arrays.asList("a", "b", "c", "d"));
        runtime.forEach(root, items, s -> s, (doc, s) -> {
            builds.incrementAndGet();
            return stringItem(doc, s);
        });
        ReactiveScheduler.get().flush();
        ElementNode a = (ElementNode) root.getChildren().get(0);
        ElementNode d = (ElementNode) root.getChildren().get(3);
        Assert.assertEquals(4, builds.get());

        items.set(Arrays.asList("d", "c", "b", "a"));
        ReactiveScheduler.get().flush();

        Assert.assertEquals("逆序不重建任何项", 4, builds.get());
        Assert.assertEquals(Arrays.asList("d", "c", "b", "a"), keyOrder(root));
        Assert.assertSame("a 节点复用", a, root.getChildren().get(3));
        Assert.assertSame("d 节点复用", d, root.getChildren().get(0));
    }

    // ── 最小移动：单项前插，稳定项零移动（LIS 守 I7/I8） ─────────────────────────

    @Test
    public void minimalMoveKeepsStableItemsInPlace() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<List<String>> items = Signal.create(Arrays.asList("a", "b", "c", "d"));
        // 记录每次 insertBefore 触及的节点：用 onMove 探针无法直接观测，改为验证最终顺序 + 节点同一性。
        runtime.forEach(root, items, s -> s, UiComponentRuntimeForEachTest::stringItem);
        ReactiveScheduler.get().flush();
        ElementNode a = (ElementNode) root.getChildren().get(0);
        ElementNode b = (ElementNode) root.getChildren().get(1);
        ElementNode c = (ElementNode) root.getChildren().get(2);
        ElementNode d = (ElementNode) root.getChildren().get(3);

        // 把 d 移到最前：[d, a, b, c]。稳定子序列是 a,b,c（相对顺序不变），只 d 移动。
        items.set(Arrays.asList("d", "a", "b", "c"));
        ReactiveScheduler.get().flush();

        Assert.assertEquals(Arrays.asList("d", "a", "b", "c"), keyOrder(root));
        Assert.assertSame(d, root.getChildren().get(0));
        Assert.assertSame(a, root.getChildren().get(1));
        Assert.assertSame(b, root.getChildren().get(2));
        Assert.assertSame(c, root.getChildren().get(3));
    }

    // ── 增删移动混合 ────────────────────────────────────────────────────────────

    @Test
    public void mixedInsertRemoveMove() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<List<String>> items = Signal.create(Arrays.asList("a", "b", "c", "d", "e"));
        runtime.forEach(root, items, s -> s, UiComponentRuntimeForEachTest::stringItem);
        ReactiveScheduler.get().flush();
        ElementNode a = (ElementNode) root.getChildren().get(0);
        ElementNode c = (ElementNode) root.getChildren().get(2);

        // 删 b,d；移动 c 到首；新增 x,y
        items.set(Arrays.asList("c", "a", "x", "e", "y"));
        ReactiveScheduler.get().flush();

        Assert.assertEquals(Arrays.asList("c", "a", "x", "e", "y"), keyOrder(root));
        Assert.assertSame("a 复用", a, root.getChildren().get(1));
        Assert.assertSame("c 复用", c, root.getChildren().get(0));
    }

    // ── 每项作用域：移除项时其 effect 被清理 ─────────────────────────────────────

    @Test
    public void removedItemScopeIsDisposed() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<Integer> bColor = Signal.create(0xFF111111);
        Signal<List<String>> items = Signal.create(Arrays.asList("a", "b"));
        ElementNode[] bHolder = new ElementNode[1];
        runtime.forEach(root, items, s -> s, (doc, s) -> {
            ElementNode el = stringItem(doc, s);
            if (s.equals("b")) {
                runtime.bindBackgroundColor(el, bColor);
                bHolder[0] = el;
            }
            return el;
        });
        ReactiveScheduler.get().flush();
        ElementNode b = bHolder[0];
        Assert.assertEquals(Integer.valueOf(0xFF111111), b.style().getBackgroundColor());

        // 移除 b → 其作用域 dispose，绑定 effect 停止
        items.set(Arrays.asList("a"));
        ReactiveScheduler.get().flush();
        Assert.assertNull(b.getParent());

        bColor.set(0xFF222222);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("被移除项的 effect 应已停止写入",
                Integer.valueOf(0xFF111111), b.style().getBackgroundColor());
    }

    // ── I5 红线：item 内部 signal 变化不触发整列表重协调 ──────────────────────────

    @Test
    public void itemInternalSignalChangeDoesNotReReconcileList() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        AtomicInteger builds = new AtomicInteger(0);
        Signal<Integer> itemColor = Signal.create(0xFF000000);
        Signal<List<String>> items = Signal.create(Arrays.asList("a", "b", "c"));
        runtime.forEach(root, items, s -> s, (doc, s) -> {
            builds.incrementAndGet();
            ElementNode el = stringItem(doc, s);
            // 每项都读取同一个 item 级 signal —— 若它泄漏成列表依赖，下面 set 会触发整列表重协调
            runtime.bindBackgroundColor(el, itemColor);
            return el;
        });
        ReactiveScheduler.get().flush();
        Assert.assertEquals(3, builds.get());

        itemColor.set(0xFF999999);
        ReactiveScheduler.get().flush();

        Assert.assertEquals("item 内部 signal 变化不得重建任何项（守 I5：不退化全列表 diff）",
                3, builds.get());
        Assert.assertEquals(Integer.valueOf(0xFF999999),
                ((ElementNode) root.getChildren().get(0)).style().getBackgroundColor());
    }

    // ── 重复 key 检测 ───────────────────────────────────────────────────────────

    @Test
    public void duplicateKeyThrows() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<List<String>> items = Signal.create(Arrays.asList("a", "a"));
        runtime.forEach(root, items, s -> s, UiComponentRuntimeForEachTest::stringItem);
        try {
            ReactiveScheduler.get().flush();
            Assert.fail("重复 key 应抛 IllegalStateException");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("key"));
        }
    }

    // ── 清空到空列表 ────────────────────────────────────────────────────────────

    @Test
    public void clearToEmptyRemovesAll() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        Signal<List<String>> items = Signal.create(Arrays.asList("a", "b", "c"));
        runtime.forEach(root, items, s -> s, UiComponentRuntimeForEachTest::stringItem);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(3, root.getChildCount());

        items.set(new ArrayList<>());
        ReactiveScheduler.get().flush();
        Assert.assertEquals(0, root.getChildCount());
    }

    // ── 列表卸载：dispose 清理全部项与协调 effect ────────────────────────────────

    @Test
    public void listHandleDisposeClearsEverything() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        AtomicInteger builds = new AtomicInteger(0);
        Signal<List<String>> items = Signal.create(Arrays.asList("a", "b"));
        ListHandle handle = runtime.forEach(root, items, s -> s, (doc, s) -> {
            builds.incrementAndGet();
            return stringItem(doc, s);
        });
        ReactiveScheduler.get().flush();
        Assert.assertEquals(2, builds.get());
        Assert.assertEquals(2, root.getChildCount());

        handle.dispose();
        Assert.assertEquals("列表卸载应摘除全部项", 0, root.getChildCount());

        // 卸载后改 items，协调 effect 已停止，不再建项
        items.set(Arrays.asList("a", "b", "c", "d"));
        ReactiveScheduler.get().flush();
        Assert.assertEquals("协调 effect 应已停止", 2, builds.get());
        Assert.assertEquals(0, root.getChildCount());
    }

    // ── 列表嵌套在 mount 组件内：父组件卸载连带清理列表 ──────────────────────────

    @Test
    public void forEachNestedInMountDisposedWithParent() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        UiComponentRuntime runtime = new UiComponentRuntime(document);

        AtomicInteger builds = new AtomicInteger(0);
        Signal<List<String>> items = Signal.create(Arrays.asList("a", "b"));
        UiComponentRuntime.MountHandle parent = runtime.mount(root, doc -> {
            ElementNode panel = doc.div();
            runtime.forEach(panel, items, s -> s, (d, s) -> {
                builds.incrementAndGet();
                return stringItem(d, s);
            });
            return panel;
        });
        ReactiveScheduler.get().flush();
        ElementNode panel = parent.getRoot();
        Assert.assertEquals(2, builds.get());
        Assert.assertEquals(2, panel.getChildCount());

        parent.unmount();
        // 父卸载后改 items：列表协调 effect 应随父作用域已清理
        items.set(Arrays.asList("a", "b", "c"));
        ReactiveScheduler.get().flush();
        Assert.assertEquals("父组件卸载应连带停止列表协调", 2, builds.get());
        Assert.assertNull("父组件根应从 DOM 摘除", panel.getParent());
    }
}
