package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.component.UiComponentRuntime;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;

/**
 * {@link ReactiveTriadDemoView} 声明式三基石（show / forEach / bindText）端到端数据层契约测试。
 *
 * <p>该 view 是 demo 页的纯组件层（不依赖 GuiScreen），故可直接 {@code new UiComponentRuntime(document)}
 * 构造并手动 {@code flush()} 走纯数据层路径验证三基石真机行为的<b>数据侧</b>（DOM 结构 + 节点复用 + 派生刷新），
 * 不调用渲染（LWJGL native 沙箱缺失）。真机视觉/帧率仍由 runClient 人工验证。</p>
 *
 * <p>覆盖：forEach 首帧渲染 / 增行复用未变行 / 删行 / 打乱时稳定项零重建（LIS）/ 切换完成只重建该行；
 * bindText 计数随任务 signal 派生刷新；show 说明区块显隐 + 稳定不重建（I7）+ 空态条件；dispose 收口。</p>
 */
public class ReactiveTriadDemoViewTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    /** 构造 view 并把根挂入文档（模拟 Screen 的注入），返回 view。 */
    private static ReactiveTriadDemoView mountView(UiDocument document, UiComponentRuntime runtime) {
        ReactiveTriadDemoView view = new ReactiveTriadDemoView(document, runtime);
        document.getRootElement().append(view.getRootElement());
        return view;
    }

    /** 深度优先查找首个带指定 data-reactive-demo 值的元素。 */
    private static ElementNode findByDemoTag(ElementNode root, String tag) {
        if (tag.equals(root.getAttribute("data-reactive-demo"))) {
            return root;
        }
        for (DocumentNode child : root.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findByDemoTag((ElementNode) child, tag);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** 取列表容器下全部任务行（data-reactive-demo=task-row），按 DOM 顺序。 */
    private static List<ElementNode> taskRows(ElementNode listContainer) {
        List<ElementNode> rows = new ArrayList<>();
        for (DocumentNode child : listContainer.getChildren()) {
            if (child instanceof ElementNode
                    && "task-row".equals(((ElementNode) child).getAttribute("data-reactive-demo"))) {
                rows.add((ElementNode) child);
            }
        }
        return rows;
    }

    /** 取列表容器下任务行的 data-task-id 序列（DOM 实际顺序）。 */
    private static List<String> rowIdOrder(ElementNode listContainer) {
        List<String> ids = new ArrayList<>();
        for (ElementNode row : taskRows(listContainer)) {
            ids.add(row.getAttribute("data-task-id"));
        }
        return ids;
    }

    private static ElementNode taskList(UiDocument document) {
        return findByDemoTag(document.getRootElement(), "task-list");
    }

    private static String countText(UiDocument document) {
        ElementNode footer = findByDemoTag(document.getRootElement(), "count-footer");
        for (DocumentNode child : footer.getChildren()) {
            if (child instanceof TextNode) {
                return ((TextNode) child).getText();
            }
        }
        return null;
    }

    // ── forEach：首帧渲染初始任务 ───────────────────────────────────────────────

    @Test
    public void rendersInitialTasksAfterFirstFlush() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);
        mountView(document, runtime);

        ReactiveScheduler.get().flush();

        ElementNode list = taskList(document);
        Assert.assertEquals("初始应渲染 3 行任务", 3, taskRows(list).size());
        Assert.assertEquals("共 3 项 · 已完成 2 项", countText(document));
        Assert.assertNull("非空列表不显示空态提示", findByDemoTag(document.getRootElement(), "empty-hint"));
        Assert.assertNull("默认不显示说明区块", findByDemoTag(document.getRootElement(), "details"));
    }

    // ── forEach：增行只追加新行、复用未变行 ──────────────────────────────────────

    @Test
    public void addTaskAppendsRowAndReusesExistingNodes() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);
        ReactiveTriadDemoView view = mountView(document, runtime);
        ReactiveScheduler.get().flush();

        ElementNode list = taskList(document);
        List<ElementNode> before = taskRows(list);
        Assert.assertEquals(3, before.size());

        view.addTask();
        ReactiveScheduler.get().flush();

        List<ElementNode> after = taskRows(list);
        Assert.assertEquals("增行后应有 4 行", 4, after.size());
        for (int i = 0; i < 3; i++) {
            Assert.assertSame("前 3 行应复用同一 DOM 节点（不重建）", before.get(i), after.get(i));
        }
        Assert.assertEquals("计数随之刷新", "共 4 项 · 已完成 2 项", countText(document));
    }

    // ── forEach：删末项 ─────────────────────────────────────────────────────────

    @Test
    public void removeLastTaskDropsTrailingRow() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);
        ReactiveTriadDemoView view = mountView(document, runtime);
        ReactiveScheduler.get().flush();

        ElementNode list = taskList(document);
        List<ElementNode> before = taskRows(list);

        view.removeLastTask();
        ReactiveScheduler.get().flush();

        List<ElementNode> after = taskRows(list);
        Assert.assertEquals("删末项后剩 2 行", 2, after.size());
        Assert.assertSame("前两行复用", before.get(0), after.get(0));
        Assert.assertSame("前两行复用", before.get(1), after.get(1));
        Assert.assertEquals("共 2 项 · 已完成 2 项", countText(document));
    }

    // ── forEach：打乱顺序时稳定项零重建（LIS 最小移动，I7） ───────────────────────

    @Test
    public void shuffleReordersWithoutRebuildingRows() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);
        ReactiveTriadDemoView view = mountView(document, runtime);
        ReactiveScheduler.get().flush();

        ElementNode list = taskList(document);
        List<ElementNode> before = taskRows(list);
        List<String> idsBefore = rowIdOrder(list);

        view.shuffleTasks(); // 末项移到首位
        ReactiveScheduler.get().flush();

        List<String> idsAfter = rowIdOrder(list);
        Assert.assertEquals("打乱后末项 id 应排到首位",
                idsBefore.get(2), idsAfter.get(0));
        Assert.assertEquals(idsBefore.get(0), idsAfter.get(1));
        Assert.assertEquals(idsBefore.get(1), idsAfter.get(2));

        // 全部行节点应被复用（仅移动 DOM 顺序，零重建）。
        List<ElementNode> after = taskRows(list);
        for (ElementNode row : after) {
            Assert.assertTrue("打乱只移动不重建，所有行节点应来自旧集合", before.contains(row));
        }
    }

    // ── forEach：切换完成态只重建该行（key 含完成态），其余行复用 ──────────────────

    @Test
    public void toggleDoneRebuildsOnlyThatRow() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);
        ReactiveTriadDemoView view = mountView(document, runtime);
        ReactiveScheduler.get().flush();

        ElementNode list = taskList(document);
        List<ElementNode> before = taskRows(list);
        // 初始第三项（id=2）未完成，切换它。
        ElementNode firstRowBefore = before.get(0);
        ElementNode thirdRowBefore = before.get(2);

        view.toggleTaskDone(2);
        ReactiveScheduler.get().flush();

        List<ElementNode> after = taskRows(list);
        Assert.assertEquals("行数不变", 3, after.size());
        Assert.assertSame("未变行（id=0）复用同一节点", firstRowBefore, after.get(0));
        Assert.assertNotSame("切换完成态的行（id=2）应重建为新节点", thirdRowBefore, after.get(2));
        Assert.assertEquals("切换后已完成数 +1", "共 3 项 · 已完成 3 项", countText(document));
    }

    // ── bindText：计数文本随任务 signal 派生刷新 ─────────────────────────────────

    @Test
    public void countTextTracksTaskSignalDerivation() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);
        ReactiveTriadDemoView view = mountView(document, runtime);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("共 3 项 · 已完成 2 项", countText(document));

        // 每次变更后 flush 一帧（与真机一致：每个输入帧 drawSelf 都 flush；同帧多次写同一 signal 会合并，
        // 且变更方法读的是已应用值，故必须逐帧推进——这正是 signal「set 后未 flush 时 get 取旧值」契约）。
        view.addTask();
        ReactiveScheduler.get().flush();
        view.addTask();
        ReactiveScheduler.get().flush();
        Assert.assertEquals("共 5 项 · 已完成 2 项", countText(document));

        view.toggleTaskDone(2); // 把初始未完成项标记完成
        ReactiveScheduler.get().flush();
        Assert.assertEquals("共 5 项 · 已完成 3 项", countText(document));
    }

    // ── show：开关控制说明区块显隐 + 稳定不重建 ─────────────────────────────────

    @Test
    public void detailsSectionShowsAndHidesByToggle() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);
        ReactiveTriadDemoView view = mountView(document, runtime);
        ReactiveScheduler.get().flush();
        Assert.assertNull("默认隐藏", findByDemoTag(document.getRootElement(), "details"));

        view.setDetailsVisible(true);
        ReactiveScheduler.get().flush();
        ElementNode detailsFirst = findByDemoTag(document.getRootElement(), "details");
        Assert.assertNotNull("开关打开后挂载说明区块", detailsFirst);

        // 稳定 true：再 flush 不重建（同一 DOM 节点）。
        ReactiveScheduler.get().flush();
        Assert.assertSame("条件稳定为真时不重建（I7）",
                detailsFirst, findByDemoTag(document.getRootElement(), "details"));

        view.setDetailsVisible(false);
        ReactiveScheduler.get().flush();
        Assert.assertNull("开关关闭后卸载说明区块", findByDemoTag(document.getRootElement(), "details"));
    }

    // ── show：列表清空显示空态提示，新增后空态消失 ────────────────────────────────

    @Test
    public void emptyHintShowsWhenListEmptied() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);
        ReactiveTriadDemoView view = mountView(document, runtime);
        ReactiveScheduler.get().flush();
        Assert.assertNull("初始非空，无空态提示", findByDemoTag(document.getRootElement(), "empty-hint"));

        view.removeLastTask();
        ReactiveScheduler.get().flush();
        view.removeLastTask();
        ReactiveScheduler.get().flush();
        view.removeLastTask();
        ReactiveScheduler.get().flush();

        ElementNode list = taskList(document);
        Assert.assertEquals("列表已清空", 0, taskRows(list).size());
        Assert.assertNotNull("空列表显示空态提示", findByDemoTag(document.getRootElement(), "empty-hint"));
        Assert.assertEquals("共 0 项 · 已完成 0 项", countText(document));

        view.addTask();
        ReactiveScheduler.get().flush();
        Assert.assertNull("新增后空态提示消失", findByDemoTag(document.getRootElement(), "empty-hint"));
        Assert.assertEquals(1, taskRows(taskList(document)).size());
    }

    // ── dispose：runtime.dispose 拆除三基石并停止协调 ───────────────────────────

    @Test
    public void disposeTearsDownTriadAndStopsReconciliation() {
        UiDocument document = UiDocument.create();
        UiComponentRuntime runtime = new UiComponentRuntime(document);
        ReactiveTriadDemoView view = mountView(document, runtime);
        ReactiveScheduler.get().flush();

        ElementNode list = taskList(document);
        Assert.assertEquals(3, taskRows(list).size());

        runtime.dispose();
        view.dispose();
        // dispose 递归清理列表/条件作用域，各项 onCleanup 摘除 DOM，容器内 forEach 行与 show 内容清空。
        Assert.assertEquals("dispose 后任务行全部拆除", 0, taskRows(list).size());

        // dispose 后改 signal，reconcile effect 已停，不再重协调。
        view.addTask();
        ReactiveScheduler.get().flush();
        Assert.assertEquals("dispose 后协调停止，不再重建行", 0, taskRows(list).size());
    }
}
