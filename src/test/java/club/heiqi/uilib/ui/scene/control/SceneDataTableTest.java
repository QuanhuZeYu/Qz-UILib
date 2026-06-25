package club.heiqi.uilib.ui.scene.control;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.component.MountHandle;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
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
 * SceneDataTable 单元测试 —— 验证 keyed 行复用、固定布局、滚动零重排和只读文本列绑定。
 */
public class SceneDataTableTest {

    /** 画布宽度。 */
    private static final int CANVAS_WIDTH = 500;
    /** 画布高度。 */
    private static final int CANVAS_HEIGHT = 300;
    /** 固定字符宽度。 */
    private static final int STUB_CHAR_WIDTH = 8;
    /** 固定行高。 */
    private static final int ROW_HEIGHT = 30;
    /** 固定视口高度。 */
    private static final int VIEWPORT_HEIGHT = 90;
    /** 列定义列表。 */
    private static final List<SceneDataTable.Column> COLUMNS = Arrays.asList(
            SceneDataTable.Column.text("名称", 80),
            SceneDataTable.Column.text("描述", 120),
            SceneDataTable.Column.text("数量", 60));

    /** 场景根。 */
    private SceneNode sceneRoot;
    /** 场景运行时。 */
    private SceneRuntime runtime;
    /** 布局引擎。 */
    private SceneLayoutEngine layoutEngine;
    /** 受控行数据源。 */
    private Signal<List<SceneDataTable.Row>> rowsSignal;
    /** mount 句柄。 */
    private MountHandle handle;
    /** 控件根节点。 */
    private SceneNode tableRoot;

    /** 初始化响应式 DataTable 测试场景。 */
    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        runtime = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
        rowsSignal = Signal.create(Collections.unmodifiableList(Arrays.asList(
                new SceneDataTable.Row(Arrays.asList("石头", "很长很长很长很长很长很长的描述", "64")),
                new SceneDataTable.Row(Arrays.asList("木头", "短描述", "12")),
                new SceneDataTable.Row(Arrays.asList("铁锭", "材料", "8")),
                new SceneDataTable.Row(Arrays.asList("金锭", "材料", "3")),
                new SceneDataTable.Row(Arrays.asList("钻石", "材料", "1")))));
        SceneDataTable.Props props = new SceneDataTable.Props(rowsSignal, COLUMNS, ROW_HEIGHT, VIEWPORT_HEIGHT);
        handle = runtime.mount(sceneRoot, SceneDataTable.create(runtime, props));
        tableRoot = handle.getRoot();
        runtime.flush();
        doLayout();
    }

    /** 清理响应式运行时。 */
    @After
    public void tearDown() {
        handle.dispose();
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 同 rowId 换新行对象时应复用原行节点。 */
    @Test
    public void sameRowIdUpdateShouldReuseRowNode() {
        SceneNode firstRow = dataRow(0);
        SceneDataTable.Row first = rowsSignal.get().get(0);

        rowsSignal.set(Collections.unmodifiableList(Arrays.asList(
                first.withCell(1, "更新描述"),
                rowsSignal.get().get(1),
                rowsSignal.get().get(2),
                rowsSignal.get().get(3),
                rowsSignal.get().get(4))));
        runtime.flush();
        doLayout();

        Assert.assertSame("同 rowId 更新应复用原行节点", firstRow, dataRow(0));
        Assert.assertEquals("复用节点内文本应更新", "更新描述", dataLabel(0, 1).getText());
    }

    /** 滚动只更新 scrollOffsetY，不触发布局重排。 */
    @Test
    public void scrollShouldUpdateOffsetWithoutRelayout() {
        Assert.assertEquals("初始滚动偏移为 0", 0, viewport().getScrollOffsetY());

        routeScroll(viewport(), -45);
        runtime.flush();
        doLayout();

        Assert.assertEquals("向下滚 wheelDelta<0 应增加 scrollOffsetY", 45, viewport().getScrollOffsetY());
        Assert.assertEquals("滚动只标 geometry，layout 应零重排", 0, layoutEngine.__getRelayoutCount());
    }

    /** 表头列与数据列应按相同列宽保持 x 坐标对齐。 */
    @Test
    public void headerAndDataColumnsShouldAlignByX() {
        for (int col = 0; col < COLUMNS.size(); col++) {
            Assert.assertEquals("表头列与数据列 x 应一致", box(headerCell(col)).getX(), box(dataCell(1, col)).getX());
        }
    }

    /** 表头行和数据行都应使用固定行高。 */
    @Test
    public void rowsShouldUseFixedHeight() {
        Assert.assertEquals("表头行高度应固定", ROW_HEIGHT, box(headerRow()).getHeight());
        for (int row = 0; row < dataContainer().__getChildren().size(); row++) {
            Assert.assertEquals("每一行高度应固定", ROW_HEIGHT, box(dataRow(row)).getHeight());
            Assert.assertEquals("每个单元格高度应固定", ROW_HEIGHT, box(dataCell(row, 0)).getHeight());
        }
    }

    /** 只读文本列应随对应 cell 值更新，未改单元格文本保持不变。 */
    @Test
    public void readonlyTextColumnShouldBindCellValue() {
        String otherColumnText = dataLabel(0, 0).getText();
        String otherRowText = dataLabel(1, 1).getText();
        SceneDataTable.Row first = rowsSignal.get().get(0);

        rowsSignal.set(Collections.unmodifiableList(Arrays.asList(
                first.withCell(1, "新描述"),
                rowsSignal.get().get(1),
                rowsSignal.get().get(2),
                rowsSignal.get().get(3),
                rowsSignal.get().get(4))));
        runtime.flush();

        Assert.assertEquals("目标 cell label 应更新", "新描述", dataLabel(0, 1).getText());
        Assert.assertEquals("同行其它列文本不变", otherColumnText, dataLabel(0, 0).getText());
        Assert.assertEquals("其它行同列文本不变", otherRowText, dataLabel(1, 1).getText());
    }

    /** 视口高度应钉死，内容高度允许超过视口以支持滚动。 */
    @Test
    public void viewportShouldPinHeightAndContentCanOverflow() {
        Assert.assertEquals("视口高度应钉死为 Props viewportHeight", VIEWPORT_HEIGHT, box(viewport()).getHeight());
        Assert.assertTrue("内容高度应超过视口以触发滚动", box(content()).getHeight() > box(viewport()).getHeight());
    }

    /** 新增行后应创建新行节点，并保持已有 keyed 行节点复用。 */
    @Test
    public void appendedRowShouldAppearWithoutBreakingExistingRowReuse() {
        SceneDataTable.Row first = new SceneDataTable.Row(Arrays.asList("石头", "基础方块", "64"));
        SceneDataTable.Row second = new SceneDataTable.Row(Arrays.asList("木头", "基础材料", "12"));
        rowsSignal.set(Collections.unmodifiableList(Arrays.asList(first, second)));
        runtime.flush();
        doLayout();
        SceneNode firstRow = dataRow(0);
        SceneNode secondRow = dataRow(1);

        SceneDataTable.Row third = new SceneDataTable.Row(Arrays.asList("铁锭", "追加材料", "8"));
        rowsSignal.set(Collections.unmodifiableList(Arrays.asList(first, second, third)));
        runtime.flush();
        doLayout();

        Assert.assertEquals("追加后行数应为 3", 3, dataContainer().__getChildren().size());
        Assert.assertSame("第 1 行应复用原节点", firstRow, dataRow(0));
        Assert.assertSame("第 2 行应复用原节点", secondRow, dataRow(1));
        Assert.assertNotSame("第 3 行应为新增节点", firstRow, dataRow(2));
        Assert.assertNotSame("第 3 行应为新增节点", secondRow, dataRow(2));
    }

    /** 删除中间行后对应节点应消失，并保持剩余 keyed 行节点复用。 */
    @Test
    public void removedMiddleRowShouldDisappearWithoutBreakingRemainingRowReuse() {
        SceneDataTable.Row first = new SceneDataTable.Row(Arrays.asList("石头", "基础方块", "64"));
        SceneDataTable.Row second = new SceneDataTable.Row(Arrays.asList("木头", "待删除材料", "12"));
        SceneDataTable.Row third = new SceneDataTable.Row(Arrays.asList("铁锭", "保留材料", "8"));
        rowsSignal.set(Collections.unmodifiableList(Arrays.asList(first, second, third)));
        runtime.flush();
        doLayout();
        SceneNode firstRow = dataRow(0);
        SceneNode secondRow = dataRow(1);
        SceneNode thirdRow = dataRow(2);

        rowsSignal.set(Collections.unmodifiableList(Arrays.asList(first, third)));
        runtime.flush();
        doLayout();

        Assert.assertEquals("删除后行数应为 2", 2, dataContainer().__getChildren().size());
        Assert.assertSame("第 1 行应复用原节点", firstRow, dataRow(0));
        Assert.assertSame("原第 3 行应复用原节点", thirdRow, dataRow(1));
        Assert.assertFalse("原第 2 行节点应从数据容器移除", dataContainer().__getChildren().contains(secondRow));
    }

    /** 行顺序变化但 rowId 不变时，应按 key 复用节点并同步新顺序。 */
    @Test
    public void reorderedRowsShouldKeepNodeReferencesByRowId() {
        SceneDataTable.Row first = new SceneDataTable.Row(Arrays.asList("石头", "第一行", "64"));
        SceneDataTable.Row second = new SceneDataTable.Row(Arrays.asList("木头", "第二行", "12"));
        SceneDataTable.Row third = new SceneDataTable.Row(Arrays.asList("铁锭", "第三行", "8"));
        rowsSignal.set(Collections.unmodifiableList(Arrays.asList(first, second, third)));
        runtime.flush();
        doLayout();
        SceneNode firstRow = dataRow(0);
        SceneNode secondRow = dataRow(1);
        SceneNode thirdRow = dataRow(2);

        rowsSignal.set(Collections.unmodifiableList(Arrays.asList(third, second, first)));
        runtime.flush();
        doLayout();

        Assert.assertEquals("重排后行数应保持 3", 3, dataContainer().__getChildren().size());
        Assert.assertSame("原第 3 行节点应移动到第 1 位", thirdRow, dataRow(0));
        Assert.assertSame("原第 2 行节点应保持在第 2 位", secondRow, dataRow(1));
        Assert.assertSame("原第 1 行节点应移动到第 3 位", firstRow, dataRow(2));
    }

    /** TextInput 编辑列应在单元格内渲染输入框三段子树。 */
    @Test
    public void textInputColumnShouldRenderEditor() {
        mountRowsAndColumns(
                Collections.singletonList(new SceneDataTable.Row(Collections.singletonList("石头"))),
                Collections.singletonList(SceneDataTable.Column.textInput("名称", 120)));

        SceneNode input = dataInput(0, 0);
        Assert.assertEquals("TextInput root 应包含 prefix/caret/suffix 三个子节点", 3, input.__getChildren().size());
        Assert.assertTrue("TextInput root 应可命中以接收输入", input.isHitTestable());
        Assert.assertEquals("DataTable TextInput 应使用输入槽横向 padding", 4, input.getPaddingLeft());
        Assert.assertEquals("DataTable TextInput 应使用输入槽边框宽度", 1, input.getBorderWidth());
        Assert.assertEquals("DataTable TextInput 应使用输入槽圆角", 2, input.getCornerRadius());
        Assert.assertEquals("DataTable TextInput 默认背景应为输入槽底色", 0xFF0F1A2E, input.getBackgroundColor());

        runtime.requestFocus(input);
        runtime.flush();

        Assert.assertEquals("TextInput 聚焦后 caret 应使用聚焦蓝", 0xFF60A5FA,
                input.__getChildren().get(1).getBackgroundColor());
    }

    /** TextInput onChange 应提交到 rows signal 并只替换目标行 cell。 */
    @Test
    public void textInputOnChangeShouldUpdateRows() {
        SceneDataTable.Row first = new SceneDataTable.Row(Collections.singletonList(""));
        SceneDataTable.Row second = new SceneDataTable.Row(Collections.singletonList("保留"));
        mountRowsAndColumns(
                Collections.unmodifiableList(Arrays.asList(first, second)),
                Collections.singletonList(SceneDataTable.Column.textInput("名称", 120)));

        routeTextToInput(dataInput(0, 0), "新值");
        runtime.flush();

        Assert.assertEquals("第 1 行 cell 应更新为输入值", "新值", rowsSignal.get().get(0).cells().get(0));
        Assert.assertEquals("第 2 行 cell 应保持不变", "保留", rowsSignal.get().get(1).cells().get(0));
        Assert.assertEquals("更新后应保留第 1 行 rowId", first.getRowId(), rowsSignal.get().get(0).getRowId());
    }

    /** 编辑单个 TextInput cell 不应重建其它 keyed 行节点或改动其它行数据。 */
    @Test
    public void editingOneTextInputCellShouldKeepOtherRowNode() {
        SceneDataTable.Row first = new SceneDataTable.Row(Collections.singletonList(""));
        SceneDataTable.Row second = new SceneDataTable.Row(Collections.singletonList("B"));
        mountRowsAndColumns(
                Collections.unmodifiableList(Arrays.asList(first, second)),
                Collections.singletonList(SceneDataTable.Column.textInput("名称", 120)));
        SceneNode secondRow = dataRow(1);

        routeTextToInput(dataInput(0, 0), "新");
        runtime.flush();
        doLayout();

        Assert.assertEquals("第 1 行 cell 应更新", "新", rowsSignal.get().get(0).cells().get(0));
        Assert.assertEquals("第 2 行数据不变", "B", rowsSignal.get().get(1).cells().get(0));
        Assert.assertSame("第 2 行节点引用应保持不变", secondRow, dataRow(1));
    }

    /** Select 编辑列应在单元格内渲染 trigger 子树。 */
    @Test
    public void selectColumnShouldRenderDropdown() {
        mountRowsAndColumns(
                Collections.singletonList(new SceneDataTable.Row(Collections.singletonList("A"))),
                Collections.singletonList(SceneDataTable.Column.select("等级", 120, Arrays.asList("A", "B", "C"))));

        SceneNode select = dataSelect(0, 0);
        Assert.assertEquals("Select trigger 应包含 label 与 arrow", 2, select.__getChildren().size());
        Assert.assertEquals("Select label 应显示当前 cell 值", "A", select.__getChildren().get(0).getText());
        Assert.assertEquals("Select arrow 应显示展开箭头", "▼", select.__getChildren().get(1).getText());
        Assert.assertEquals("DataTable Select 应使用输入槽横向 padding", 4, select.getPaddingLeft());
        Assert.assertEquals("DataTable Select 应使用输入槽边框宽度", 1, select.getBorderWidth());
        Assert.assertEquals("DataTable Select 应使用输入槽圆角", 2, select.getCornerRadius());
        Assert.assertEquals("DataTable Select 默认背景应为输入槽底色", 0xFF0F1A2E, select.getBackgroundColor());
    }

    /** Select onSelect 应提交到 rows signal 并只替换目标行 cell。 */
    @Test
    public void selectOnSelectShouldUpdateRows() {
        SceneDataTable.Row first = new SceneDataTable.Row(Collections.singletonList("A"));
        SceneDataTable.Row second = new SceneDataTable.Row(Collections.singletonList("C"));
        mountRowsAndColumns(
                Collections.unmodifiableList(Arrays.asList(first, second)),
                Collections.singletonList(SceneDataTable.Column.select("等级", 120, Arrays.asList("A", "B", "C"))));

        openSelect(0, 0);
        clickOverlayItem(1);
        runtime.flush();

        Assert.assertEquals("第 1 行 cell 应更新为选中值", "B", rowsSignal.get().get(0).cells().get(0));
        Assert.assertEquals("第 2 行 cell 应保持不变", "C", rowsSignal.get().get(1).cells().get(0));
        Assert.assertEquals("更新后应保留第 1 行 rowId", first.getRowId(), rowsSignal.get().get(0).getRowId());
    }

    /** 控件级 enabled=FALSE 时，行内 TextInput 编辑器应阻断文本输入。 */
    @Test
    public void disabledShouldBlockTextInputEdit() {
        mountRowsAndColumns(
                Collections.singletonList(new SceneDataTable.Row(Collections.singletonList(""))),
                Collections.singletonList(SceneDataTable.Column.textInput("名称", 120)),
                Signal.create(Boolean.FALSE), null);

        routeTextToInput(dataInput(0, 0), "X");
        runtime.flush();

        Assert.assertEquals("disabled 时行内编辑器应阻断输入，cell 保持空",
                "", rowsSignal.get().get(0).cells().get(0));
    }

    /** Builder.build() 构建的 Props 与 canonical 构造器构建的 Props 各字段等价。 */
    @Test
    public void builderShouldMatchCanonicalProps() {
        Signal<List<SceneDataTable.Row>> rows = Signal.create(
                Collections.singletonList(new SceneDataTable.Row(Collections.singletonList("x"))));
        List<SceneDataTable.Column> columns = Collections.singletonList(
                SceneDataTable.Column.text("名称", 80));
        Signal<Boolean> enabled = Signal.create(Boolean.TRUE);
        Signal<Boolean> readOnly = Signal.create(Boolean.FALSE);

        SceneDataTable.Props fromBuilder = SceneDataTable.Props.builder(rows)
                .columns(columns).rowHeight(ROW_HEIGHT).viewportHeight(VIEWPORT_HEIGHT)
                .enabled(enabled).readOnly(readOnly)
                .build();
        SceneDataTable.Props fromCanonical = new SceneDataTable.Props(
                rows, columns, ROW_HEIGHT, VIEWPORT_HEIGHT, enabled, readOnly);

        Assert.assertSame("rows 引用一致", rows, fromBuilder.rows());
        Assert.assertEquals("columns 等价", fromCanonical.columns(), fromBuilder.columns());
        Assert.assertEquals("rowHeight 一致", fromCanonical.rowHeight(), fromBuilder.rowHeight());
        Assert.assertEquals("viewportHeight 一致", fromCanonical.viewportHeight(), fromBuilder.viewportHeight());
        Assert.assertSame("enabled 引用一致", fromCanonical.enabled(), fromBuilder.enabled());
        Assert.assertSame("readOnly 引用一致", fromCanonical.readOnly(), fromBuilder.readOnly());
    }

    /** 跑一帧布局。 */
    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        for (int i = 0; i < runtime.getOverlayHost().bottomFirst().size(); i++) {
            SceneNode overlay = runtime.getOverlayHost().bottomFirst().get(i).getRoot();
            layoutEngine.layout(overlay, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        }
    }

    /** 重新挂载指定行和列。 */
    private void mountRowsAndColumns(List<SceneDataTable.Row> rows, List<SceneDataTable.Column> columns) {
        mountRowsAndColumns(rows, columns, null, null);
    }

    /**
     * 重新挂载指定行和列，并注入控件级 enabled/readOnly 信号。
     *
     * @param rows     行数据
     * @param columns  列定义
     * @param enabled  启用信号，null 时默认恒 true
     * @param readOnly 只读信号，null 时默认恒 false
     */
    private void mountRowsAndColumns(List<SceneDataTable.Row> rows, List<SceneDataTable.Column> columns,
                                     club.heiqi.uilib.ui.reactive.ReadableSignal<Boolean> enabled,
                                     club.heiqi.uilib.ui.reactive.ReadableSignal<Boolean> readOnly) {
        handle.dispose();
        sceneRoot = new SceneNode();
        rowsSignal = Signal.create(rows);
        SceneDataTable.Props props = new SceneDataTable.Props(
                rowsSignal, columns, ROW_HEIGHT, VIEWPORT_HEIGHT, enabled, readOnly);
        handle = runtime.mount(sceneRoot, SceneDataTable.create(runtime, props));
        tableRoot = handle.getRoot();
        runtime.flush();
        doLayout();
    }

    /** 获取滚动视口。 */
    private SceneNode viewport() {
        return tableRoot.__getChildren().get(0);
    }

    /** 获取内容容器。 */
    private SceneNode content() {
        return viewport().__getChildren().get(0);
    }

    /** 获取表头行。 */
    private SceneNode headerRow() {
        return content().__getChildren().get(0);
    }

    /** 获取数据行容器。 */
    private SceneNode dataContainer() {
        return content().__getChildren().get(1);
    }

    /** 获取表头单元格。 */
    private SceneNode headerCell(int col) {
        return headerRow().__getChildren().get(col);
    }

    /** 获取数据行。 */
    private SceneNode dataRow(int rowIndex) {
        return dataContainer().__getChildren().get(rowIndex);
    }

    /** 获取数据单元格。 */
    private SceneNode dataCell(int rowIndex, int col) {
        return dataRow(rowIndex).__getChildren().get(col);
    }

    /** 获取数据单元格 label。 */
    private SceneNode dataLabel(int rowIndex, int col) {
        return dataCell(rowIndex, col).__getChildren().get(0);
    }

    /** 获取数据单元格内 TextInput root。 */
    private SceneNode dataInput(int rowIndex, int col) {
        return dataCell(rowIndex, col).__getChildren().get(0);
    }

    /** 获取数据单元格内 Select root。 */
    private SceneNode dataSelect(int rowIndex, int col) {
        return dataCell(rowIndex, col).__getChildren().get(0);
    }

    /** 获取 Select overlay 根节点。 */
    private SceneNode overlayRoot() {
        return runtime.getOverlayHost().bottomFirst().get(0).getRoot();
    }

    /** 获取 Select overlay 选项节点。 */
    private SceneNode overlayItem(int index) {
        return overlayRoot().__getChildren().get(index);
    }

    /** 获取节点布局盒。 */
    private LayoutBox box(SceneNode node) {
        return (LayoutBox) node.getCachedLayout();
    }

    /** 点击展开 Select。 */
    private void openSelect(int rowIndex, int col) {
        clickCenter(dataSelect(rowIndex, col));
        runtime.flush();
        doLayout();
    }

    /** 点击 overlay 选项。 */
    private void clickOverlayItem(int index) {
        clickCenter(overlayItem(index));
    }

    /** 点击节点中心点。 */
    private void clickCenter(SceneNode node) {
        int[] center = absCenter(node);
        routePointer(ScenePointerAction.BUTTON_DOWN, center[0], center[1]);
        routePointer(ScenePointerAction.BUTTON_UP, center[0], center[1]);
    }

    /** 获取节点绝对中心点。 */
    private int[] absCenter(SceneNode node) {
        LayoutBox b = box(node);
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

    /** 路由鼠标指针事件。 */
    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    /** 向目标节点路由滚轮事件。 */
    private void routeScroll(SceneNode target, int wheelDelta) {
        LayoutBox targetBox = box(target);
        int centerX = targetBox.getX() + targetBox.getWidth() / 2;
        int centerY = targetBox.getY() + targetBox.getHeight() / 2;
        InputFrameBuilder fb = new InputFrameBuilder(centerX, centerY);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, centerX, centerY,
                SceneMouseButton.NONE, wheelDelta, 0, 0,
                false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }

    /** 向 TextInput 节点路由文本输入。 */
    private void routeTextToInput(SceneNode input, String text) {
        runtime.requestFocus(input);
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofText(text, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }
}
