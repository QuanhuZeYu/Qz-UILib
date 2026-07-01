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
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneSimpleList 端到端单元测试。
 *
 * <p>覆盖受控列表初始渲染、添加、删除、行文本编辑与最大条目数限制。</p>
 */
public class SceneSimpleListTest {

    /** 场景根节点。 */
    private SceneNode sceneRoot;
    /** 场景运行时。 */
    private SceneRuntime runtime;
    /** 布局引擎。 */
    private SceneLayoutEngine layoutEngine;
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
    /** 固定字符宽度。 */
    private static final int STUB_CHAR_WIDTH = 8;

    /** 初始化测试场景。 */
    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        runtime = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
        changeCount = new AtomicInteger(0);
    }

    /** 清理运行时。 */
    @After
    public void tearDown() {
        if (runtime != null) {
            runtime.dispose();
        }
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
     * scrollbarContentSignal 默认 null 时，stackHost 只含 viewport（结构向后兼容）。
     */
    @Test
    public void scrollbarContentSignalNullByDefault_stackHostHasOnlyViewport() {
        mountList(items("alpha", "beta"), 0, 0);
        Assert.assertEquals("scrollbarContentSignal 默认 null 时 stackHost 应只含 viewport",
                1, stackHost().__getChildren().size());
    }

    /**
     * scrollbarContentSignal 非 null 时，stackHost 含 viewport 与 scrollbar column。
     */
    @Test
    public void scrollbarContentSignalSet_stackHostHasViewportAndScrollbarColumn() {
        Signal<Integer> contentSignal = Signal.create(Integer.valueOf(0));
        itemsSignal = Signal.create(items("alpha", "beta"));
        SceneSimpleList.Props props = SceneSimpleList.Props.builder(itemsSignal)
                .label("列表")
                .scrollbarContentSignal(contentSignal)
                .build();
        handle = runtime.mount(sceneRoot, SceneSimpleList.create(runtime, props));
        simpleListRoot = handle.getRoot();
        runtime.flush();
        Assert.assertEquals("scrollbarContentSignal 非 null 时 stackHost 应含 viewport 与 scrollbar column",
                2, stackHost().__getChildren().size());
    }

    /**
     * 点击添加按钮后写回 items signal 并通知回调。
     */
    @Test
    public void addButtonShouldAppendEmptyItem() {
        mountList(items("alpha"), 0, 0);
        doFrame();

        clickCenter(addButton());
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

        clickCenter(deleteButton(rowAt(1)));
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
        clickCenter(input);
        runtime.flush();
        Assert.assertSame("点击应聚焦行内输入框", input, runtime.getFocusedNode());

        routeText("X");
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

        clickCenter(addButton());
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

        clickCenter(input);
        runtime.flush();
        routeText("X");
        runtime.flush();
        routeText("Y");
        runtime.flush();
        routeText("Z");
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

        clickCenter(textInput(secondRow));
        runtime.flush();
        routeText("b");
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

        clickCenter(deleteButton(rowAt(0)));
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
        routeText("X");
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
                    itemsSignal.set(next);
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

    /** 执行 flush + layout。 */
    private void doFrame() {
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
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
     * 路由文本输入事件。
     *
     * @param text 文本
     */
    private void routeText(String text) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofText(text, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    /**
     * 点击节点中心。
     *
     * @param node 目标节点
     */
    private void clickCenter(SceneNode node) {
        int[] center = absCenter(node);
        routePointer(ScenePointerAction.BUTTON_DOWN, center[0], center[1]);
        routePointer(ScenePointerAction.BUTTON_UP, center[0], center[1]);
    }

    /**
     * 路由指针事件。
     *
     * @param action 指针动作
     * @param x      x 坐标
     * @param y      y 坐标
     */
    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    /**
     * 计算节点中心的绝对坐标。
     *
     * @param node 目标节点
     * @return [x, y]
     */
    private int[] absCenter(SceneNode node) {
        LayoutBox b = (LayoutBox) node.getCachedLayout();
        int ax = b.getX();
        int ay = b.getY();
        SceneNode parent = node.__getParent();
        while (parent != null) {
            LayoutBox parentBox = (LayoutBox) parent.getCachedLayout();
            if (parentBox != null) {
                ax += parentBox.getX();
                ay += parentBox.getY();
            }
            parent = parent.__getParent();
        }
        return new int[]{ax + b.getWidth() / 2, ay + b.getHeight() / 2};
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
}
