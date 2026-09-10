package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintFragment;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneSimpleList 端到端单元测试。
 *
 * <p>覆盖受控列表初始渲染、增删编辑、边界限制，以及 draggable 的中线换位、跟手与回滚；
 * 并验证液态玻璃迁移口径：底座走主题 GROUP 配方、行只做 accent 半透明轻量覆盖且不装滤镜、
 * 标题/按钮文字随主题更新、主题切换不重建节点不丢草稿与滚动偏移、卸载回收绑定。</p>
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
    /** 绘制引擎（断言滤镜采样预算与行不装滤镜）。 */
    private ScenePaintEngine paintEngine;

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
        paintEngine = new ScenePaintEngine(new FixedTextMeasurer(8, 16));
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
        return input.__getChildren().get(0).getText() + input.__getChildren().get(2).getText()
                + input.__getChildren().get(4).getText();
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
            out[i] = input.__getChildren().get(0).getText() + input.__getChildren().get(2).getText()
                    + input.__getChildren().get(4).getText();
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

    // ==================== 液态玻璃迁移：底座 GROUP + 行轻量覆盖 ====================

    /**
     * 在可切换局部主题作用域内挂载待测控件（主题信号变化只重派生外观，不重建节点）。
     *
     * @param pageTheme     页面主题信号
     * @param initialItems  初始列表
     * @param maxItems      最大条目数
     * @param minItems      最小条目数
     * @param draggable     是否启用拖拽排序
     * @param showScrollbar 是否建滚动条
     */
    private void mountListInTheme(Signal<SceneTheme> pageTheme, List<SceneSimpleList.ListItem> initialItems,
                                  int maxItems, int minItems, boolean draggable, boolean showScrollbar) {
        itemsSignal = Signal.create(initialItems);
        lastChangedItems = null;
        SceneSimpleList.Props props = SceneSimpleList.Props.builder(itemsSignal)
                .label("列表")
                .placeholder("输入条目")
                .maxItems(maxItems)
                .minItems(minItems)
                .draggable(draggable)
                .showScrollbar(showScrollbar)
                .onItemsChanged(next -> {
                    changeCount.incrementAndGet();
                    lastChangedItems = next;
                })
                .build();
        final SceneNode[] holder = new SceneNode[1];
        handle = runtime.mount(sceneRoot, () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] = SceneSimpleList.create(runtime, props).get());
            return holder[0];
        });
        simpleListRoot = handle.getRoot();
        runtime.flush();
    }

    /**
     * 递归查找第一个文本等于 {@code text} 的节点。
     *
     * @param node 子树根
     * @param text 目标文本
     * @return 命中节点，未找到返回 null
     */
    private SceneNode findText(SceneNode node, String text) {
        if (text.equals(node.getText())) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findText(child, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** @return 控件标题文字节点 */
    private SceneNode titleLabel() {
        SceneNode label = findText(simpleListRoot, "列表");
        if (label == null) {
            throw new AssertionError("未找到控件标题节点");
        }
        return label;
    }

    /**
     * 行内所有子节点右缘之外、仍在行盒内的探测点 X（行只在这些区域才成为最深命中目标，
     * 从而把 hover 写到行自身）。
     *
     * @param row 行节点
     * @return 探测点 X（rootAbs=0,0）
     */
    private int rowHoverProbeX(SceneNode row) {
        AnchorRect rowBox = SceneGeometry.absoluteBox(row, 0, 0);
        int rightMostChild = rowBox.getX();
        for (SceneNode child : row.__getChildren()) {
            AnchorRect childBox = SceneGeometry.absoluteBox(child, 0, 0);
            rightMostChild = Math.max(rightMostChild, childBox.getX() + childBox.getWidth());
        }
        int probe = rowBox.getX() + rowBox.getWidth() - 2;
        Assert.assertTrue("行右缘应留出子节点之外的空白区供 hover 探测，probe=" + probe
                + ", rightMostChild=" + rightMostChild, probe >= rightMostChild);
        return probe;
    }

    /** 节点自身 PaintFragment 内的 BACKDROP 命令数；无 fragment（无绘制内容）视为 0。 */
    private static int backdropCount(SceneNode node) {
        Object cached = node.getCachedPaint();
        if (!(cached instanceof PaintFragment)) {
            return 0;
        }
        int count = 0;
        for (PaintCommand command : ((PaintFragment) cached).getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    /** PaintPlan 中指定类型的命令数。 */
    private static int countType(List<PaintCommand> commands, PaintCommandType type) {
        int count = 0;
        for (PaintCommand command : commands) {
            if (command.getType() == type) {
                count++;
            }
        }
        return count;
    }

    /** 保留色 RGB、替换 alpha 通道（行轻量覆盖口径）。 */
    private static int tint(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * 在给定坐标投递 SCROLL 事件（{@code wheelDelta < 0} 向下滚），坐标取行内空白区以避开
     * 行内输入框自身的滚动语义。
     */
    private void scrollAt(int x, int y, int wheelDelta) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, x, y, SceneMouseButton.NONE,
                wheelDelta, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
        runtime.flush();
    }

    /**
     * 默认路径：底座 background/border/borderWidth/cornerRadius/backdrop/surfaceElevation 全部
     * 等于 {@code SceneThemes.DEFAULT.surface(Role.GROUP)} 的对应值；行默认透明、不装滤镜，
     * 底座自身恰好一条 BACKDROP（每颗表面只采样一次，行不额外装玻璃）。
     */
    @Test
    public void defaultBaseShouldUseGroupRecipeAndRowsCarryNoBackdrop() {
        SceneSurfaceStyle group = SceneThemes.DEFAULT.surface(SceneTheme.Role.GROUP);
        Assert.assertNotNull("前置：GROUP 配方自带滤镜", group.getBackdrop());
        mountList(items("alpha", "beta"), 0, 0);
        doFrame();

        SceneNode viewport = listViewport();
        Assert.assertEquals("底座染色 = GROUP 配方 idle tint",
                group.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertEquals("底座圆角 = GROUP 配方", group.getCornerRadius(), viewport.getCornerRadius());
        Assert.assertEquals("底座边框宽 = GROUP 配方", group.getBorderWidth(), viewport.getBorderWidth());
        Assert.assertEquals("底座缘色 = GROUP 配方 idle edge",
                group.getIdle().getEdge(), viewport.getBorderColor());
        Assert.assertEquals("底座实体高度 = GROUP 配方 idle elevation",
                group.getIdle().getElevation(), viewport.__getSurfaceElevation(), 0.0001F);
        Assert.assertNotNull("底座默认带液态玻璃滤镜", viewport.getBackdrop());
        Assert.assertEquals("底座滤镜模糊半径 = 配方",
                group.getBackdrop().getBlurRadius(), viewport.getBackdrop().getBlurRadius());
        Assert.assertEquals("底座滤镜材质 = 配方",
                group.getBackdrop().getEffect().getMaterial(), viewport.getBackdrop().getEffect().getMaterial());

        for (int i = 0; i < 2; i++) {
            SceneNode row = rowAt(i);
            Assert.assertNull("行[" + i + "] 不装滤镜", row.getBackdrop());
            Assert.assertEquals("行[" + i + "] 默认透明", 0, row.getBackgroundColor());
            Assert.assertEquals("行[" + i + "] 不写边框宽（外观归轻量覆盖）", 0, row.getBorderWidth());
            Assert.assertEquals("行[" + i + "] 不写圆角", 0, row.getCornerRadius());
        }

        PaintPlan plan = paintEngine.paint(sceneRoot).getPlan();
        Assert.assertEquals("底座自身恰好一条 BACKDROP", 1, backdropCount(viewport));
        for (int i = 0; i < 2; i++) {
            Assert.assertEquals("行[" + i + "] 自身零 BACKDROP", 0, backdropCount(rowAt(i)));
        }
        Assert.assertEquals("整树 BACKDROP = 底座 1 + 每行输入 1 + 每行删除 1 + 添加 1（每颗表面只采样一次）",
                1 + 2 * 2 + 1, countType(plan.getCommands(), PaintCommandType.BACKDROP));
    }

    /**
     * 行轻量状态覆盖：默认透明，hover 取主题 accent 半透明，拖拽把手按下时取更高强度；
     * 三种状态下行都不装滤镜（{@code getBackdrop() == null}）。
     */
    @Test
    public void rowHoverAndDragShouldUseAccentOverlayWithoutBackdrop() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        mountListInTheme(pageTheme, items("alpha", "beta"), 0, 0, true, false);
        doFrame();

        SceneNode row = rowAt(0);
        int probeX = rowHoverProbeX(row);
        int probeY = centerY(row);
        Assert.assertEquals("前置：行默认透明", 0, row.getBackgroundColor());

        harness.moveAt(probeX, probeY);
        runtime.flush();
        Assert.assertEquals("行 hover = 主题 accent 半透明轻量覆盖",
                tint(dark.accent(), 0x1F), row.getBackgroundColor());
        Assert.assertNull("行 hover 也不装滤镜", row.getBackdrop());

        // hover 只由 MOVE 驱动：先移出整行，再验证按下把手时的拖拽态。
        harness.moveAt(probeX, probeY + CANVAS_HEIGHT + 10);
        runtime.flush();
        Assert.assertEquals("指针移出后行背景回落透明", 0, row.getBackgroundColor());

        SceneNode handle = dragHandle(row);
        harness.pressAt(centerX(handle), centerY(handle));
        Assert.assertEquals("拖拽把手按下 → 行取 accent 更高强度覆盖",
                tint(dark.accent(), 0x33), row.getBackgroundColor());
        Assert.assertNull("拖拽中的行仍不装滤镜", row.getBackdrop());
        harness.releaseAt(centerX(handle), centerY(handle));
        Assert.assertEquals("拖拽终止后行回落透明", 0, row.getBackgroundColor());
    }

    /**
     * 主题切换：{@code withTheme} 来源主题信号变化 + flush 后底座/行/文字更新，草稿与滚动偏移
     * 保留、节点身份不变、effect 数不增长（外观重派生不重建节点、不重复订阅）。
     */
    @Test
    public void themeSwitchUpdatesBaseRowAndTextWithoutLosingDraftOrScroll() {
        List<SceneSimpleList.ListItem> many = new ArrayList<SceneSimpleList.ListItem>();
        for (int i = 0; i < 20; i++) {
            many.add(new SceneSimpleList.ListItem("row" + i));
        }
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneSurfaceStyle darkGroup = dark.surface(SceneTheme.Role.GROUP);
        SceneSurfaceStyle lightGroup = light.surface(SceneTheme.Role.GROUP);
        Assert.assertNotEquals("两档 GROUP 配方必须不同，否则切换不传播",
                darkGroup.getIdle(), lightGroup.getIdle());

        Signal<SceneTheme> pageTheme = Signal.create(dark);
        // sceneRoot 需从 Constraints 收到确定高，且控件根需自带确定高（生产由 FormFieldShell 传
        // theme.listHeight()），viewport 才不会被内容撑大（否则 maxScrollY 恒 0）。
        sceneRoot.setFillParentHeight(true);
        mountListInTheme(pageTheme, many, 0, 0, false, false);
        simpleListRoot.setPreferredHeight(100);
        doFrame();

        SceneNode viewport = listViewport();
        SceneNode firstRow = rowAt(0);
        SceneNode firstInput = textInput(firstRow);
        SceneNode title = titleLabel();
        SceneNode addLabel = addButton().__getChildren().get(0);
        Assert.assertEquals("初始底座 = 深色 GROUP 配方 idle tint",
                darkGroup.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertEquals("初始标题 = 深色正文前景", dark.foreground(), title.getTextColor());
        Assert.assertEquals("初始添加按钮文字 = 深色按钮配方前景",
                dark.surface(SceneTheme.Role.BUTTON_STANDARD).getForeground().intValue(),
                addLabel.getTextColor());

        // 草稿：编辑第一行（主题切换不得丢）
        harness.click(firstInput);
        runtime.flush();
        harness.typeText("X");
        runtime.flush();
        Assert.assertEquals("前置：草稿已写入受控 signal", "row0X", itemsSignal.get().get(0).getValue());

        // 滚动偏移：长列表向下滚，坐标取行内空白区
        int maxScroll = SceneGeometry.maxScrollY(viewport);
        Assert.assertTrue("前置：长列表可滚动，maxScroll=" + maxScroll, maxScroll > 0);
        int offset = Math.min(60, maxScroll);
        AnchorRect viewportBox = SceneGeometry.absoluteBox(viewport, 0, 0);
        scrollAt(viewportBox.getX() + viewportBox.getWidth() - 2,
                viewportBox.getY() + viewportBox.getHeight() / 2, -offset);
        Assert.assertEquals("前置：滚动偏移已应用", offset, viewport.getScrollOffsetY());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertEquals("底座染色随主题更新", lightGroup.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertEquals("底座圆角随主题更新", lightGroup.getCornerRadius(), viewport.getCornerRadius());
        Assert.assertEquals("底座缘色随主题更新", lightGroup.getIdle().getEdge(), viewport.getBorderColor());
        Assert.assertEquals("底座滤镜材质随主题更新",
                lightGroup.getBackdrop().getEffect().getMaterial(),
                viewport.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("标题文字随主题更新", light.foreground(), title.getTextColor());
        Assert.assertEquals("添加按钮文字随主题更新",
                light.surface(SceneTheme.Role.BUTTON_STANDARD).getForeground().intValue(),
                addLabel.getTextColor());
        Assert.assertSame("主题切换不重建 viewport", viewport, listViewport());
        Assert.assertSame("主题切换不重建行节点", firstRow, rowAt(0));
        Assert.assertSame("主题切换不重建行内输入", firstInput, textInput(rowAt(0)));
        Assert.assertEquals("主题切换不丢草稿", "row0X", itemsSignal.get().get(0).getValue());
        Assert.assertEquals("主题切换保留滚动偏移", offset, viewport.getScrollOffsetY());
        Assert.assertEquals("主题切换不新增 effect",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
        for (int i = 0; i < 20; i++) {
            Assert.assertNull("主题切换后行[" + i + "] 仍不装滤镜", rowAt(i).getBackdrop());
        }
    }

    /**
     * 长列表（20+ 行）：底座只装在 viewport 一次，行节点不各装滤镜、不写背景。
     */
    @Test
    public void longListRowsShouldNotInstallBackdrop() {
        List<SceneSimpleList.ListItem> many = new ArrayList<SceneSimpleList.ListItem>();
        for (int i = 0; i < 24; i++) {
            many.add(new SceneSimpleList.ListItem("row" + i));
        }
        mountList(many, 0, 0);
        doFrame();

        SceneNode viewport = listViewport();
        Assert.assertNotNull("底座只装在 viewport 上", viewport.getBackdrop());
        List<SceneNode> rows = viewport.__getChildren();
        Assert.assertEquals("长列表行数 == 数据量", 24, rows.size());

        paintEngine.paint(sceneRoot);
        Assert.assertEquals("底座自身恰好一条 BACKDROP", 1, backdropCount(viewport));
        for (int i = 0; i < rows.size(); i++) {
            SceneNode row = rows.get(i);
            Assert.assertNull("行[" + i + "] 不得各装背景滤镜", row.getBackdrop());
            Assert.assertEquals("行[" + i + "] 背景保持透明", 0, row.getBackgroundColor());
            Assert.assertEquals("行[" + i + "] 自身零 BACKDROP", 0, backdropCount(row));
        }
    }

    /**
     * 内部生成控件只读复用其已主题化外观：滚动条自身不装表面（无滤镜），滑块 idle 取主题次要前景，
     * 主题切换只重派生；底座 BACKDROP 仍恰好一条（控件不重复绑定内部控件外观）。
     */
    @Test
    public void scrollbarShouldReuseThemedAppearanceWithoutDuplicateBinding() {
        List<SceneSimpleList.ListItem> many = new ArrayList<SceneSimpleList.ListItem>();
        for (int i = 0; i < 20; i++) {
            many.add(new SceneSimpleList.ListItem("row" + i));
        }
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        sceneRoot.setFillParentHeight(true);
        mountListInTheme(pageTheme, many, 0, 0, false, true);
        simpleListRoot.setPreferredHeight(100);
        doFrame();

        SceneNode viewport = listViewport();
        SceneNode scrollbarColumn = stackHost().__getChildren().get(1);
        SceneNode thumb = scrollbarColumn.__getChildren().get(0);
        Assert.assertNull("滚动条列不装表面（无滤镜）", scrollbarColumn.getBackdrop());
        Assert.assertNull("滑块不装滤镜（只做状态覆盖）", thumb.getBackdrop());
        Assert.assertEquals("滑块 idle 取主题次要前景 + 中性 alpha",
                tint(dark.mutedForeground(), SceneScrollbar.THUMB_IDLE_ALPHA), thumb.getBackgroundColor());
        paintEngine.paint(sceneRoot);
        Assert.assertEquals("底座自身仍恰好一条 BACKDROP", 1, backdropCount(viewport));

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();
        Assert.assertEquals("滑块随主题重派生",
                tint(light.mutedForeground(), SceneScrollbar.THUMB_IDLE_ALPHA), thumb.getBackgroundColor());
        paintEngine.paint(sceneRoot);
        Assert.assertEquals("主题切换后底座仍恰好一条 BACKDROP", 1, backdropCount(viewport));
        Assert.assertEquals("主题切换不新增 effect",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
    }

    /**
     * 卸载后表面绑定 effect 回收，主题更新不再写入旧节点。
     */
    @Test
    public void unmountShouldReleaseSurfaceBindings() {
        int before = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        mountListInTheme(pageTheme, items("alpha", "beta"), 0, 0, false, false);
        doFrame();
        Assert.assertTrue("默认路径应注册响应式外观绑定",
                ReactiveTestProbe.registeredEffectCount() > before);

        SceneNode viewport = listViewport();
        int colorBeforeDispose = viewport.getBackgroundColor();
        handle.dispose();
        Assert.assertEquals("卸载后外观绑定 effect 应回收",
                before, ReactiveTestProbe.registeredEffectCount());

        pageTheme.set(SceneTheme.liquidGlassLight());
        runtime.flush();
        Assert.assertEquals("卸载后主题更新不再写入旧 viewport",
                colorBeforeDispose, viewport.getBackgroundColor());
    }

    /**
     * 控件内文字跟随控件根字号（字号真值在控件根节点）：缺省未设字号时标题与按钮文字仍是节点
     * 默认 16；{@code handle.fontSize(24)} 后同一条通道把它们一起调到 24。
     *
     * <p>证据取自绘制产物（paint plan 里 TEXT 命令的 {@code TextStyle.fontSize}），不是节点属性回读。</p>
     */
    @Test
    public void paintedTextShouldFollowControlFontSize() {
        mountList(items("alpha"), 0, 0);
        doFrame();
        assertPaintedFontSize(16, "列表", "添加", "×");

        handle.fontSize(24);
        runtime.flush();
        doFrame();
        // 真实 host 由帧管线在 SETTLE 阶段桥接 layout epoch（SceneFramePipeline#__setLayoutDoneEpoch）；
        // 裸 harness 只 layout 不桥接，需显式补这一步——控件内文字的字号跟随走的正是这条 epoch 通道。
        runtime.__setLayoutDoneEpoch(1);
        runtime.flush();
        doFrame();

        assertPaintedFontSize(24, "列表", "添加", "×");
    }

    /**
     * 从绘制产物取文本字号证据。
     *
     * @param expected 期望字号（UI 像素）
     * @param texts    期望出现的文本
     */
    private void assertPaintedFontSize(int expected, String... texts) {
        PaintPlan plan = paintEngine.paint(sceneRoot).getPlan();
        for (String text : texts) {
            PaintCommand command = paintedText(plan, text);
            Assert.assertEquals("绘制产物文本 '" + text + "' 的字号",
                    expected, command.getTextStyle().getFontSize());
        }
    }

    /**
     * 绘制产物中文本等于 {@code text} 的 TEXT 命令。
     *
     * @param plan 绘制计划
     * @param text 文本
     * @return 命中命令
     */
    private static PaintCommand paintedText(PaintPlan plan, String text) {
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.TEXT && text.equals(command.getText())) {
                return command;
            }
        }
        throw new AssertionError("绘制产物中未找到文本：" + text);
    }
}
