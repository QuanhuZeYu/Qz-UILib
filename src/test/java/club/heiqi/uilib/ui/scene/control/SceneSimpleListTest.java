package club.heiqi.uilib.ui.scene.control;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * SceneSimpleList 端到端单元测试。
 *
 * <p>覆盖受控列表初始渲染、增删编辑、边界限制，以及 draggable 的中线换位、跟手与回滚。</p>
 */
public class SceneSimpleListTest {

    /** 场景根节点。 */
    private SceneNode sceneRoot;
    /** 场景运行时。 */
    private SceneRuntime runtime;
    /** 语义化交互注入 harness（route 根 + click/typeText 入口）；其 runtime 即上方 runtime 字段。 */
    private SceneInteractionHarness harness;
    /** 受控列表 signal。 */
    private Signal<List<SceneSimpleList.ListItem>> itemsSignal;
    /** 变更回调次数。 */
    private AtomicInteger changeCount;
    /** 最近一次变更列表。 */
    private List<SceneSimpleList.ListItem> lastChangedItems;
    /** 挂载句柄。 */
    private MountHandle handle;
    /** 控件根节点。 */
    private SceneNode simpleListRoot;

    /** 测试画布宽度。 */
    private static final int CANVAS_WIDTH = 360;
    /** 测试画布高度。 */
    private static final int CANVAS_HEIGHT = 180;

    /** 初始化测试场景。 */
    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        sceneRoot = new SceneNode();
        changeCount = new AtomicInteger(0);
    }

    /** 清理运行时。 */
    @After
    public void tearDown() {
        harness.dispose();
        ReactiveScheduler.get().reset();
    }

    /**
     * 初始渲染 N 行。
     */
    @Test
    public void initialRenderShouldCreateRowsForItems() {
        mountList(items("alpha", "beta"), 0, 0);
        doFrame();

        Assert.assertTrue("含标题时 root 应包含 show anchor、label、list、add", simpleListRoot.__getChildren().size() >= 4);
        Assert.assertEquals("初始应渲染 2 行", 2, listViewport().__getChildren().size());
        Assert.assertEquals("首行文本应来自 items[0]", "alpha", textInputValue(rowAt(0)));
        Assert.assertEquals("第二行文本应来自 items[1]", "beta", textInputValue(rowAt(1)));
        Assert.assertTrue("列表区域应可滚动", listViewport().isScrollable());
    }

    /**
     * showScrollbar 默认 false 时，stackHost 只含 viewport（结构向后兼容）。
     */
    @Test
    public void showScrollbarFalseByDefault_stackHostHasOnlyViewport() {
        mountList(items("alpha", "beta"), 0, 0);
        Assert.assertEquals("showScrollbar 默认 false 时 stackHost 应只含 viewport",
                1, stackHost().__getChildren().size());
    }

    /**
     * showScrollbar 为 true 时，stackHost 含 viewport 与 scrollbar column。
     */
    @Test
    public void showScrollbarTrue_stackHostHasViewportAndScrollbarColumn() {
        itemsSignal = Signal.create(items("alpha", "beta"));
        SceneSimpleList.Props props = SceneSimpleList.Props.builder(itemsSignal)
                .label("列表")
                .showScrollbar(true)
                .build();
        handle = runtime.mount(sceneRoot, SceneSimpleList.create(runtime, props));
        simpleListRoot = handle.getRoot();
        runtime.flush();
        Assert.assertEquals("showScrollbar=true 时 stackHost 应含 viewport 与 scrollbar column",
                2, stackHost().__getChildren().size());
    }

    /**
     * 点击添加按钮后写回 items signal 并通知回调。
     */
    @Test
    public void addButtonShouldAppendEmptyItem() {
        mountList(items("alpha"), 0, 0);
        doFrame();

        harness.click(addButton());
        runtime.flush();

        Assert.assertEquals("添加后 signal 增加空行", Arrays.asList("alpha", ""), values(itemsSignal.get()));
        Assert.assertEquals("添加应通知一次回调", 1, changeCount.get());
        Assert.assertEquals("回调收到同一版新列表", itemsSignal.get(), lastChangedItems);
    }

    /**
     * 点击删除按钮后移除对应行。
     */
    @Test
    public void deleteButtonShouldRemoveItem() {
        mountList(items("alpha", "beta", "gamma"), 0, 0);
        doFrame();

        harness.click(deleteButton(rowAt(1)));
        runtime.flush();

        Assert.assertEquals("删除第二行后列表收缩", Arrays.asList("alpha", "gamma"), values(itemsSignal.get()));
        Assert.assertEquals("删除应通知一次回调", 1, changeCount.get());
    }

    /**
     * 行文本输入应复制列表、替换下标并写回 signal。
     */
    @Test
    public void rowTextInputShouldReplaceItem() {
        mountList(items("alpha", "beta"), 0, 0);
        doFrame();
        SceneNode input = textInput(rowAt(1));
        harness.click(input);
        runtime.flush();
        Assert.assertSame("点击应聚焦行内输入框", input, runtime.getFocusedNode());

        harness.typeText("X");
        runtime.flush();

        Assert.assertEquals("编辑第二行应替换 items[1]", Arrays.asList("alpha", "betaX"), values(itemsSignal.get()));
        Assert.assertEquals("编辑应通知一次回调", 1, changeCount.get());
        Assert.assertEquals("回调收到替换后列表", itemsSignal.get(), lastChangedItems);
    }

    /**
     * maxItems 达到上限后添加按钮不再写 signal。
     */
    @Test
    public void maxItemsShouldBlockAppend() {
        mountList(items("alpha", "beta"), 2, 0);
        doFrame();

        harness.click(addButton());
        runtime.flush();

        Assert.assertEquals("达到 maxItems 后不添加", Arrays.asList("alpha", "beta"), values(itemsSignal.get()));
        Assert.assertEquals("被限制的添加不通知回调", 0, changeCount.get());
    }

    /**
     * 连续输入应保留行身份与 caret，不因值变化重建输入行。
     */
    @Test
    public void continuousTypingShouldKeepRowAndAppendAtCaret() {
        mountList(items("alpha", "beta"), 0, 0);
        doFrame();
        SceneNode row = rowAt(1);
        SceneNode input = textInput(row);
        long originalId = itemsSignal.get().get(1).getId();

        harness.click(input);
        runtime.flush();
        harness.typeText("X");
        runtime.flush();
        harness.typeText("Y");
        runtime.flush();
        harness.typeText("Z");
        runtime.flush();

        Assert.assertEquals("连续输入应追加到原文本末尾", Arrays.asList("alpha", "betaXYZ"), values(itemsSignal.get()));
        Assert.assertSame("编辑行节点不应重建", row, rowAt(1));
        Assert.assertSame("输入节点不应重建", input, textInput(rowAt(1)));
        Assert.assertEquals("编辑后行 id 应保持不变", originalId, itemsSignal.get().get(1).getId());
    }

    /**
     * 重复值列表编辑第二行时，第一行不应因值匹配启发式被误复用或重建。
     */
    @Test
    public void duplicateValueEditShouldKeepFirstRowStable() {
        mountList(items("a", "a"), 0, 0);
        doFrame();
        SceneNode firstRow = rowAt(0);
        SceneNode secondRow = rowAt(1);
        long firstId = itemsSignal.get().get(0).getId();
        long secondId = itemsSignal.get().get(1).getId();

        harness.click(textInput(secondRow));
        runtime.flush();
        harness.typeText("b");
        runtime.flush();

        Assert.assertEquals("仅第二个重复值应被编辑", Arrays.asList("a", "ab"), values(itemsSignal.get()));
        Assert.assertSame("第一行节点不应重建", firstRow, rowAt(0));
        Assert.assertSame("第二行节点不应重建", secondRow, rowAt(1));
        Assert.assertEquals("第一行 id 应保持", firstId, itemsSignal.get().get(0).getId());
        Assert.assertEquals("第二行 id 应保持", secondId, itemsSignal.get().get(1).getId());
    }

    /**
     * minItems 达到边界时删除按钮应禁用且不写回列表。
     */
    @Test
    public void minItemsShouldDisableDeleteAtBoundary() {
        mountList(items("alpha", "beta"), 0, 2);
        doFrame();

        harness.click(deleteButton(rowAt(0)));
        runtime.flush();

        Assert.assertEquals("达到 minItems 后不删除", Arrays.asList("alpha", "beta"), values(itemsSignal.get()));
        Assert.assertEquals("被限制的删除不通知回调", 0, changeCount.get());
    }

    /**
     * 空列表初始态不渲染行，仅保留添加入口。
     */
    @Test
    public void emptyListShouldRenderOnlyAddEntry() {
        mountList(items(), 0, 0);
        doFrame();

        Assert.assertEquals("空列表不应渲染行", 0, listViewport().__getChildren().size());
        Assert.assertNotNull("空列表仍应显示添加按钮", addButton());
    }

    /**
     * 控件级 enabled=FALSE 时，行内 TextInput 编辑器应阻断文本输入。
     */
    @Test
    public void disabledShouldBlockRowEdit() {
        mountList(items("alpha"), 0, 0, Signal.create(Boolean.FALSE), null);
        doFrame();

        SceneNode input = textInput(rowAt(0));
        runtime.requestFocus(input);
        runtime.flush();
        harness.typeText("X");
        runtime.flush();

        Assert.assertEquals("disabled 时行内编辑器应阻断输入，items 保持不变",
                Arrays.asList("alpha"), values(itemsSignal.get()));
        Assert.assertEquals("disabled 时不应触发变更回调", 0, changeCount.get());
    }

    /**
     * 挂载待测控件。
     *
     * @param initialItems 初始列表
     * @param maxItems     最大条目数
     * @param minItems     最小条目数
     */
    private void mountList(List<SceneSimpleList.ListItem> initialItems, int maxItems, int minItems) {
        mountList(initialItems, maxItems, minItems, null, null);
    }

    /**
     * 挂载待测控件并注入控件级 enabled/readOnly 信号。
     *
     * @param initialItems 初始列表
     * @param maxItems     最大条目数
     * @param minItems     最小条目数
     * @param enabled      启用信号，null 时默认恒 true
     * @param readOnly     只读信号，null 时默认恒 false
     */
    private void mountList(List<SceneSimpleList.ListItem> initialItems, int maxItems, int minItems,
                           club.heiqi.uilib.ui.reactive.ReadableSignal<Boolean> enabled,
                           club.heiqi.uilib.ui.reactive.ReadableSignal<Boolean> readOnly) {
        itemsSignal = Signal.create(initialItems);
        lastChangedItems = null;
        SceneSimpleList.Props.Builder builder = SceneSimpleList.Props.builder(itemsSignal)
                .label("列表")
                .placeholder("输入条目")
                .maxItems(maxItems)
                .minItems(minItems)
                .onItemsChanged(next -> {
                    changeCount.incrementAndGet();
                    lastChangedItems = next;
                });
        if (enabled != null) {
            builder.enabled(enabled);
        }
        if (readOnly != null) {
            builder.readOnly(readOnly);
        }
        SceneSimpleList.Props props = builder.build();
        handle = runtime.mount(sceneRoot, SceneSimpleList.create(runtime, props));
        simpleListRoot = handle.getRoot();
        runtime.flush();
    }

    /** 执行 flush + layout（layout 经 harness.mountRoot，刷新路由根 + absoluteBox，供 harness.click 取中心）。 */
    private void doFrame() {
        runtime.flush();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    /** @return 列表视口节点 */
    private SceneNode listViewport() {
        SceneNode found = findScrollable(simpleListRoot);
        if (found == null) {
            throw new AssertionError("未找到滚动列表区域");
        }
        return found;
    }

    /**
     * 递归查找子树中第一个 isScrollable 节点。
     *
     * <p>viewport 现嵌套在 stackHost(ROW) 内，不再是 root 直接子，需递归定位。</p>
     *
     * @param node 子树根
     * @return 第一个可滚动节点，未找到返回 null
     */
    private SceneNode findScrollable(SceneNode node) {
        if (node.isScrollable()) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findScrollable(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** @return 承载 viewport 与可选滚动条的 stackHost（viewport 的父节点） */
    private SceneNode stackHost() {
        return listViewport().__getParent();
    }

    /** @return 添加按钮节点 */
    private SceneNode addButton() {
        for (SceneNode child : simpleListRoot.__getChildren()) {
            if (!child.__getChildren().isEmpty() && "添加".equals(child.__getChildren().get(0).getText())) {
                return child;
            }
        }
        throw new AssertionError("未找到添加按钮");
    }

    /**
     * 返回指定行。
     *
     * @param index 行下标
     * @return 行节点
     */
    private SceneNode rowAt(int index) {
        return listViewport().__getChildren().get(index);
    }

    /**
     * 返回行内文本输入根节点。
     *
     * @param row 行节点
     * @return 文本输入根节点
     */
    private SceneNode textInput(SceneNode row) {
        return row.__getChildren().get(0);
    }

    /**
     * 返回行内删除按钮。
     *
     * @param row 行节点
     * @return 删除按钮节点
     */
    private SceneNode deleteButton(SceneNode row) {
        return row.__getChildren().get(1);
    }

    /**
     * 返回文本输入当前展示文本。
     *
     * @param row 行节点
     * @return prefix 与 suffix 拼接文本
     */
    private String textInputValue(SceneNode row) {
        SceneNode input = textInput(row);
        return input.__getChildren().get(0).getText() + input.__getChildren().get(2).getText();
    }

    /**
     * 创建测试行列表。
     *
     * @param values 文本值
     * @return 行列表
     */
    private List<SceneSimpleList.ListItem> items(String... values) {
        SceneSimpleList.ListItem[] result = new SceneSimpleList.ListItem[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = new SceneSimpleList.ListItem(values[i]);
        }
        return Arrays.asList(result);
    }

    /**
     * 提取行文本。
     *
     * @param items 行列表
     * @return 文本列表
     */
    private List<String> values(List<SceneSimpleList.ListItem> items) {
        String[] result = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            result[i] = items.get(i).getValue();
        }
        return Arrays.asList(result);
    }

    // ==================== draggable 拖拽排序（中线插槽换位） ====================

    /**
     * 挂载 draggable=true 列表。
     *
     * @param initialItems 初始列表
     */
    private void mountDraggable(List<SceneSimpleList.ListItem> initialItems) {
        itemsSignal = Signal.create(initialItems);
        lastChangedItems = null;
        SceneSimpleList.Props props = SceneSimpleList.Props.builder(itemsSignal)
                .label("列表")
                .placeholder("输入条目")
                .draggable(true)
                .onItemsChanged(next -> {
                    changeCount.incrementAndGet();
                    lastChangedItems = next;
                })
                .build();
        handle = runtime.mount(sceneRoot, SceneSimpleList.create(runtime, props));
        simpleListRoot = handle.getRoot();
        runtime.flush();
    }

    /**
     * 返回行内拖拽把手节点（draggable=true 时行结构 = [handle, input, deleteButton]）。
     *
     * @param row 行节点
     * @return 把手节点
     */
    private SceneNode dragHandle(SceneNode row) {
        return row.__getChildren().get(0);
    }

    /**
     * 返回节点中心 Y（rootAbs=0,0）。
     *
     * @param node 节点
     * @return 中心 Y
     */
    private int centerY(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getY() + box.getHeight() / 2;
    }

    /**
     * 返回节点中心 X（rootAbs=0,0）。
     *
     * @param node 节点
     * @return 中心 X
     */
    private int centerX(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getX() + box.getWidth() / 2;
    }

    /**
     * draggable=true 时每行行首应渲染拖拽把手节点。
     */
    @Test
    public void draggableTrueShouldRenderHandlePerRow() {
        mountDraggable(items("a", "b"));
        doFrame();
        Assert.assertEquals("draggable=true 时行结构应为 [handle, input, deleteButton]",
                3, rowAt(0).__getChildren().size());
        Assert.assertTrue("把手应 hitTestable=true（独立交互单元）",
                dragHandle(rowAt(0)).isHitTestable());
    }

    /**
     * draggable=false（默认）时不渲染把手，行结构向后兼容。
     */
    @Test
    public void draggableFalseByDefaultShouldNotRenderHandle() {
        mountList(items("a", "b"), 0, 0);
        doFrame();
        Assert.assertEquals("draggable 默认 false 时行结构应为 [input, deleteButton]",
                2, rowAt(0).__getChildren().size());
    }

    /**
     * 拖拽第 0 行到第 2 行位置：items 顺序改变，被拖行 id 保留（keyed diff 锚点稳定），onItemsChanged 触发。
     */
    @Test
    public void dragRowZeroToRowTwoShouldReorder() {
        mountDraggable(items("a", "b", "c"));
        doFrame();
        long draggedId = itemsSignal.get().get(0).getId();
        SceneNode draggedRow = rowAt(0);

        SceneNode handle0 = dragHandle(draggedRow);
        int hx = centerX(handle0);
        int hy = centerY(handle0);
        // DOWN 到 row0 把手中心 → 启动拖拽 + capture
        harness.pressAt(hx, hy);
        // MOVE 到 row2 中线下方 → 被拖行中心跨过 row2 中线，落点 index=2
        int targetY = pointerYForDraggedCenter(rowAt(0), handle0, centerY(rowAt(2)) + 1);
        harness.moveAt(hx, targetY);
        Assert.assertEquals("MOVE 期外部 items 暂不提交",
                Arrays.asList("a", "b", "c"), values(itemsSignal.get()));
        Assert.assertEquals("MOVE 期视口显示预览顺序",
                Arrays.asList("b", "c", "a"), draggableViewportValues());
        Assert.assertEquals("MOVE 期不触发 onItemsChanged", 0, changeCount.get());
        // UP 释放
        harness.releaseAt(hx, targetY);

        Assert.assertEquals("UP 后被拖行 transform 应归零", 0f, translateY(draggedRow), 0.01f);
        Assert.assertEquals("拖拽 row0→row2 后顺序应为 [b,c,a]",
                Arrays.asList("b", "c", "a"), values(itemsSignal.get()));
        Assert.assertEquals("被拖行 id 应保留在 items 中（keyed diff 锚点稳定）",
                draggedId, itemsSignal.get().get(2).getId());
        Assert.assertEquals("拖拽应恰好触发一次 onItemsChanged 回调", 1, changeCount.get());
        Assert.assertEquals("回调收到同一版新列表", itemsSignal.get(), lastChangedItems);
    }

    /** DOWN 后、阈值激活前的受控列表更新必须成为真实拖拽起点，不得被旧 DOWN 快照回写。 */
    @Test
    public void controlledUpdateBeforeActivationShouldBecomeDragStartOrder() {
        mountDraggable(items("a", "b", "c"));
        doFrame();
        SceneNode draggedHandle = dragHandle(rowAt(0));
        int x = centerX(draggedHandle);
        int downY = centerY(draggedHandle);

        harness.pressAt(x, downY);
        List<SceneSimpleList.ListItem> current = itemsSignal.get();
        List<SceneSimpleList.ListItem> external = Arrays.asList(current.get(1), current.get(0), current.get(2));
        itemsSignal.set(external);
        doFrame();
        Assert.assertEquals(Arrays.asList("b", "a", "c"), draggableViewportValues());

        harness.moveAt(x, downY + 6);
        harness.releaseAt(x, downY + 6);

        Assert.assertEquals("实际激活必须读取受控更新后的顺序",
                Arrays.asList("b", "a", "c"), values(itemsSignal.get()));
        Assert.assertEquals("原位结束不得把外部更新伪装为控件提交", 0, changeCount.get());
    }

    /** 已激活拖拽期间的受控更新必须成为新的 CANCEL 基线，preview 不得回落到旧手势快照。 */
    @Test
    public void controlledUpdateDuringDragShouldWinOnCancel() {
        mountDraggable(items("a", "b", "c"));
        doFrame();
        SceneNode draggedRow = rowAt(0);
        SceneNode draggedHandle = dragHandle(draggedRow);
        int x = centerX(draggedHandle);
        int targetY = pointerYForDraggedCenter(draggedRow, draggedHandle, centerY(rowAt(2)) + 1);

        harness.pressAt(x, centerY(draggedHandle));
        harness.moveAt(x, targetY);
        Assert.assertEquals(Arrays.asList("b", "c", "a"), draggableViewportValues());

        List<SceneSimpleList.ListItem> current = itemsSignal.get();
        itemsSignal.set(Arrays.asList(current.get(2), current.get(0), current.get(1)));
        doFrame();
        routePointer(ScenePointerAction.CANCEL, x, targetY);

        Assert.assertEquals("CANCEL 不得覆盖拖拽期间的受控 authority",
                Arrays.asList("c", "a", "b"), values(itemsSignal.get()));
        Assert.assertEquals("preview 必须与受控 authority 重新收敛",
                Arrays.asList("c", "a", "b"), draggableViewportValues());
        Assert.assertEquals(0, changeCount.get());
    }

    /** authority 恰好等于当前 preview 时也属于外部更新，CANCEL 不得恢复更旧的手势起点。 */
    @Test
    public void controlledUpdateMatchingPreviewShouldWinOnCancel() {
        mountDraggable(items("a", "b", "c"));
        doFrame();
        SceneNode draggedRow = rowAt(0);
        SceneNode draggedHandle = dragHandle(draggedRow);
        int x = centerX(draggedHandle);
        int targetY = pointerYForDraggedCenter(draggedRow, draggedHandle, centerY(rowAt(1)) + 1);

        harness.pressAt(x, centerY(draggedHandle));
        harness.moveAt(x, targetY);
        Assert.assertEquals(Arrays.asList("b", "a", "c"), draggableViewportValues());
        List<SceneSimpleList.ListItem> authority = itemsSignal.get();
        itemsSignal.set(Arrays.asList(authority.get(1), authority.get(0), authority.get(2)));
        Assert.assertEquals("用例必须保留未 flush authority 窗口",
                Arrays.asList("a", "b", "c"), values(itemsSignal.get()));

        routePointer(ScenePointerAction.CANCEL, x, targetY);

        Assert.assertEquals(Arrays.asList("b", "a", "c"), values(itemsSignal.get()));
        Assert.assertEquals(Arrays.asList("b", "a", "c"), draggableViewportValues());
        Assert.assertEquals(0, changeCount.get());
    }

    /** authority 等于 preview 后在旧槽位 UP，也不得把 authority 覆盖回拖拽前顺序。 */
    @Test
    public void controlledUpdateMatchingPreviewShouldWinOnUp() {
        mountDraggable(items("a", "b", "c"));
        doFrame();
        Signal<List<SceneSimpleList.ListItem>> resetSource = Signal.create(itemsSignal.get());
        runtime.bind(resetSource, itemsSignal::set);
        runtime.flush();
        SceneNode draggedRow = rowAt(0);
        SceneNode draggedHandle = dragHandle(draggedRow);
        int x = centerX(draggedHandle);
        int startY = centerY(draggedHandle);
        int targetY = pointerYForDraggedCenter(draggedRow, draggedHandle, centerY(rowAt(1)) + 1);

        harness.pressAt(x, startY);
        harness.moveAt(x, targetY);
        List<SceneSimpleList.ListItem> authority = itemsSignal.get();
        resetSource.set(Arrays.asList(authority.get(1), authority.get(0), authority.get(2)));
        Assert.assertEquals("用例必须保留未 flush 的一跳 reset bridge 窗口",
                Arrays.asList("a", "b", "c"), values(itemsSignal.get()));

        harness.releaseAt(x, startY);

        Assert.assertEquals(Arrays.asList("b", "a", "c"), values(itemsSignal.get()));
        Assert.assertEquals(Arrays.asList("b", "a", "c"), draggableViewportValues());
        Assert.assertEquals("外部 authority 不应触发控件提交", 0, changeCount.get());
    }

    /** UP 坐标可直接反转最后一次 MOVE；即使最终 authority no-op，也必须发布最终 preview。 */
    @Test
    public void upAtStartSlotShouldRestorePreviewWhenCommitIsNoOp() {
        mountDraggable(items("a", "b", "c"));
        doFrame();
        SceneNode draggedRow = rowAt(0);
        SceneNode draggedHandle = dragHandle(draggedRow);
        int x = centerX(draggedHandle);
        int startY = centerY(draggedHandle);
        int previewY = pointerYForDraggedCenter(draggedRow, draggedHandle, centerY(rowAt(1)) + 1);

        harness.pressAt(x, startY);
        harness.moveAt(x, previewY);
        Assert.assertEquals(Arrays.asList("b", "a", "c"), draggableViewportValues());
        harness.releaseAt(x, startY);

        Assert.assertEquals(Arrays.asList("a", "b", "c"), values(itemsSignal.get()));
        Assert.assertEquals("UP 最终 preview 必须覆盖上一帧 MOVE 预览",
                Arrays.asList("a", "b", "c"), draggableViewportValues());
        Assert.assertEquals("最终 authority 未变化时不通知提交", 0, changeCount.get());
    }

    /** UP 后同帧卸载列表时，已完成手势的终态不得随行 Owner 一起丢失。 */
    @Test
    public void dropShouldSettleAfterListOwnerDisposesBeforeFlush() {
        mountDraggable(items("a", "b", "c"));
        doFrame();
        SceneNode draggedRow = rowAt(0);
        SceneNode draggedHandle = dragHandle(draggedRow);
        int x = centerX(draggedHandle);
        int targetY = pointerYForDraggedCenter(draggedRow, draggedHandle, centerY(rowAt(2)) + 1);

        harness.pressAt(x, centerY(draggedHandle));
        harness.moveAt(x, targetY);
        routePointerWithoutFlush(ScenePointerAction.BUTTON_UP, x, targetY);
        handle.dispose();
        runtime.flush();

        Assert.assertEquals(Arrays.asList("b", "c", "a"), values(itemsSignal.get()));
        Assert.assertEquals(1, changeCount.get());
    }

    /** 整个 runtime 在终态 flush 前关闭时，应取消迟到提交并回收短生命周期 effect。 */
    @Test
    public void dropShouldCancelWhenRuntimeDisposesBeforeFlush() {
        mountDraggable(items("a", "b", "c"));
        doFrame();
        SceneNode draggedRow = rowAt(0);
        SceneNode draggedHandle = dragHandle(draggedRow);
        int x = centerX(draggedHandle);
        int targetY = pointerYForDraggedCenter(draggedRow, draggedHandle, centerY(rowAt(2)) + 1);

        harness.pressAt(x, centerY(draggedHandle));
        harness.moveAt(x, targetY);
        routePointerWithoutFlush(ScenePointerAction.BUTTON_UP, x, targetY);
        runtime.dispose();
        ReactiveScheduler.get().flush();

        Assert.assertEquals(Arrays.asList("a", "b", "c"), values(itemsSignal.get()));
        Assert.assertEquals(0, changeCount.get());
    }

    /**
     * 拖拽末行到首行位置：被拖行落到 index 0。
     */
    @Test
    public void dragLastRowToHeadShouldMoveToZero() {
        mountDraggable(items("a", "b", "c"));
        doFrame();
        long draggedId = itemsSignal.get().get(2).getId();

        SceneNode handleLast = dragHandle(rowAt(2));
        int hx = centerX(handleLast);
        int hy = centerY(handleLast);
        harness.pressAt(hx, hy);
        // MOVE 到 row0 中线上方 → 被拖行中心位于 row0 前，落点 index=0
        int topY = pointerYForDraggedCenter(rowAt(2), handleLast, centerY(rowAt(0)) - 1);
        harness.moveAt(hx, topY);
        Assert.assertEquals("MOVE 期外部 items 暂不提交",
                Arrays.asList("a", "b", "c"), values(itemsSignal.get()));
        Assert.assertEquals("MOVE 期视口显示预览顺序",
                Arrays.asList("c", "a", "b"), draggableViewportValues());
        Assert.assertEquals("MOVE 期不触发 onItemsChanged", 0, changeCount.get());
        harness.releaseAt(hx, topY);

        Assert.assertEquals("拖拽末行→首行后顺序应为 [c,a,b]",
                Arrays.asList("c", "a", "b"), values(itemsSignal.get()));
        Assert.assertEquals("被拖行应落到 index 0",
                draggedId, itemsSignal.get().get(0).getId());
    }

    /** 向上换位使用目标槽位 top 分支，layout 后抓取点同样不得跳离指针。 */
    @Test
    public void dragUpwardKeepsGrabPointAfterLayout() {
        mountDraggable(items("a", "b", "c"));
        doFrame();
        SceneNode draggedRow = rowAt(2);
        SceneNode handle = dragHandle(draggedRow);
        int x = centerX(handle);
        int startY = centerY(handle);
        int grabOffset = startY - topY(draggedRow);
        int targetY = pointerYForDraggedCenter(draggedRow, handle, centerY(rowAt(1)) - 1);

        harness.pressAt(x, startY);
        harness.moveAt(x, targetY);
        Assert.assertEquals(Arrays.asList("a", "c", "b"), draggableViewportValues());

        doFrame();
        Assert.assertEquals("向上换位 layout 后抓取点仍应贴住指针",
                targetY - grabOffset, topY(draggedRow) + translateY(draggedRow), 0.01f);
        harness.releaseAt(x, targetY);
        Assert.assertEquals(Arrays.asList("a", "c", "b"), values(itemsSignal.get()));
    }

    /**
     * 单行列表拖拽：无其他行可换位，items 不变，回调不触发。
     */
    @Test
    public void singleRowDragShouldNotChange() {
        mountDraggable(items("only"));
        doFrame();

        SceneNode h = dragHandle(rowAt(0));
        int hx = centerX(h);
        int hy = centerY(h);
        harness.pressAt(hx, hy);
        harness.moveAt(hx, hy + 50);
        Assert.assertEquals("单行 MOVE 期不触发回调", 0, changeCount.get());
        harness.releaseAt(hx, hy + 50);

        Assert.assertEquals("单行列表拖拽 items 不变",
                Arrays.asList("only"), values(itemsSignal.get()));
        Assert.assertEquals("单行列表拖拽不触发回调", 0, changeCount.get());
    }

    /**
     * draggable=false 时即使按下把手区域（无把手）也不触发拖拽重排。
     */
    @Test
    public void draggableFalseShouldNotReorderOnDrag() {
        mountList(items("a", "b", "c"), 0, 0);
        doFrame();

        // draggable=false 时行首无把手，在行首区域 DOWN+MOVE+UP 不应改 items
        AnchorRect rowBox = SceneGeometry.absoluteBox(rowAt(0), 0, 0);
        int x = rowBox.getX() + 5;
        int y = centerY(rowAt(0));
        harness.pressAt(x, y);
        harness.moveAt(x, centerY(rowAt(2)));
        harness.releaseAt(x, centerY(rowAt(2)));

        Assert.assertEquals("draggable=false 拖拽不重排",
                Arrays.asList("a", "b", "c"), values(itemsSignal.get()));
        Assert.assertEquals("draggable=false 不触发回调", 0, changeCount.get());
    }

    /**
     * 拖拽中节点经 keyed diff 平移复用：被拖行的把手节点引用稳定（不重建）。
     */
    @Test
    public void dragShouldReuseRowNodeViaKeyedDiff() {
        mountDraggable(items("a", "b", "c"));
        doFrame();
        SceneNode handle0 = dragHandle(rowAt(0));
        SceneNode row0Node = rowAt(0);

        int hx = centerX(handle0);
        int hy = centerY(handle0);
        harness.pressAt(hx, hy);
        harness.moveAt(hx, pointerYForDraggedCenter(rowAt(0), handle0, centerY(rowAt(2)) + 1));
        Assert.assertEquals("MOVE 期外部 items 暂不提交",
                Arrays.asList("a", "b", "c"), values(itemsSignal.get()));

        // 拖拽后原 row0 节点应仍存在于 viewport 子列表（keyed diff 平移，非重建）
        boolean reused = false;
        for (SceneNode child : listViewport().__getChildren()) {
            if (child == row0Node) {
                reused = true;
                break;
            }
        }
        Assert.assertTrue("被拖行节点应经 keyed diff 复用（不重建）", reused);
        harness.releaseAt(hx, pointerYForDraggedCenter(rowAt(0), handle0, centerY(rowAt(2)) + 1));
    }

    /** 被拖行越过相邻中线即换位；keyed layout 后抓取点仍贴住指针。 */
    @Test
    public void dragCrossesAdjacentCenterAndKeepsGrabPointAfterLayout() {
        mountDraggable(items("a", "b", "c"));
        doFrame();
        SceneNode row0 = rowAt(0);
        SceneNode handle0 = dragHandle(row0);
        int hx = centerX(handle0);
        int hy = centerY(handle0);
        int rowOneCenter = centerY(rowAt(1));
        int grabOffset = hy - topY(row0);

        harness.pressAt(hx, hy);
        harness.moveAt(hx, pointerYForDraggedCenter(row0, handle0, rowOneCenter - 1));
        Assert.assertTrue("未跨中线时 transform 已浮起，但落点仍按 layoutBox 判定",
                Math.abs(translateY(row0)) > 0.1f);
        Assert.assertEquals("被拖行中心未跨过 row1 中线时不重排",
                Arrays.asList("a", "b", "c"), values(itemsSignal.get()));

        int crossedPointerY = pointerYForDraggedCenter(row0, handle0, rowOneCenter + 1);
        harness.moveAt(hx, crossedPointerY);
        Assert.assertEquals("被拖行中心跨过 row1 中线后预览移到 row1 后",
                Arrays.asList("b", "a", "c"), draggableViewportValues());
        Assert.assertEquals("预览期外部 items 暂不提交",
                Arrays.asList("a", "b", "c"), values(itemsSignal.get()));

        doFrame();
        Assert.assertEquals("换位 layout 后被拖行抓取点仍应贴住指针",
                crossedPointerY - grabOffset, topY(row0) + translateY(row0), 0.01f);
        harness.moveAt(hx, pointerYForDraggedCenter(row0, handle0, rowOneCenter - 1));
        Assert.assertEquals("相邻项移开后的小幅反向移动不应翻回",
                Arrays.asList("b", "a", "c"), draggableViewportValues());
        harness.releaseAt(hx, pointerYForDraggedCenter(row0, handle0, rowOneCenter - 1));
    }

    /**
     * 拖拽取消时应回落到拖拽起始顺序，且不提交外部 items。
     */
    @Test
    public void dragCancelShouldRollbackPreviewWithoutCommit() {
        mountDraggable(items("a", "b", "c"));
        doFrame();
        SceneNode row0 = rowAt(0);
        SceneNode handle0 = dragHandle(row0);
        int hx = centerX(handle0);
        int hy = centerY(handle0);
        int targetY = pointerYForDraggedCenter(row0, handle0, centerY(rowAt(2)) + 1);

        harness.pressAt(hx, hy);
        harness.moveAt(hx, targetY);
        Assert.assertEquals("CANCEL 前已有预览顺序",
                Arrays.asList("b", "c", "a"), draggableViewportValues());

        routePointer(ScenePointerAction.CANCEL, hx, targetY);
        Assert.assertEquals("CANCEL 后被拖行 transform 应归零", 0f, translateY(row0), 0.01f);
        Assert.assertEquals("CANCEL 后外部 items 保持起始顺序",
                Arrays.asList("a", "b", "c"), values(itemsSignal.get()));
        Assert.assertEquals("CANCEL 后视口回落起始顺序",
                Arrays.asList("a", "b", "c"), draggableViewportValues());
        Assert.assertEquals("CANCEL 不触发 onItemsChanged", 0, changeCount.get());
    }

    /**
     * 返回节点上边缘 Y（rootAbs=0,0）。
     *
     * @param node 节点
     * @return 上边缘 Y
     */
    private int topY(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getY();
    }

    /**
     * 将目标拖拽中心 Y 换算为指针 Y。
     *
     * @param draggedRow     被拖行节点
     * @param handle         被拖行把手
     * @param draggedCenterY 目标拖拽中心 Y
     * @return 指针 Y
     */
    private int pointerYForDraggedCenter(SceneNode draggedRow, SceneNode handle, int draggedCenterY) {
        return draggedCenterY - (centerY(draggedRow) - centerY(handle));
    }

    /**
     * 读取节点 translateY；未设置 transform 视为 0。
     */
    private float translateY(SceneNode node) {
        return node.getTransform() == null ? 0f : node.getTransform().translateY;
    }

    /**
     * 读取 draggable 行的视口展示顺序。
     */
    private List<String> draggableViewportValues() {
        String[] out = new String[listViewport().__getChildren().size()];
        for (int i = 0; i < out.length; i++) {
            SceneNode input = listViewport().__getChildren().get(i).__getChildren().get(1);
            out[i] = input.__getChildren().get(0).getText() + input.__getChildren().get(2).getText();
        }
        return Arrays.asList(out);
    }

    /**
     * 白盒回退（精确 localX/坐标）：投递 POINTER_CANCEL 以覆盖拖拽取消回落。
     */
    private void routePointer(ScenePointerAction action, int x, int y) {
        routePointerWithoutFlush(action, x, y);
        runtime.flush();
    }

    private void routePointerWithoutFlush(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }
}
