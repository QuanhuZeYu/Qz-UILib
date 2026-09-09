package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
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
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintFragment;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneDataTable 单元测试 —— 验证 keyed 行复用、固定布局、滚动零重排和只读文本列绑定。
 *
 * <p>并验证液态玻璃迁移口径：外壳走主题 GROUP 配方、表头走 TOOLBAR 配方、数据行只做
 * accent 半透明轻量交替覆盖（不装滤镜）、编辑单元复用已主题化的 SceneTextInput/SceneSelect
 * （INPUT/OVERLAY）、主题切换只重派生外观且不丢草稿/编辑态/滚动偏移、卸载回收绑定。</p>
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
    /** 数据行交替覆盖强度（与 SceneDataTable 内部口径一致，行只写 backgroundColor）。 */
    private static final int ROW_ALTERNATE_ALPHA = 0x14;
    /** 库默认主题的 INPUT 配方（编辑单元表面）。 */
    private static final SceneSurfaceStyle INPUT = SceneThemes.DEFAULT.surface(SceneTheme.Role.INPUT);
    /** 列定义列表。 */
    private static final List<SceneDataTable.Column> COLUMNS = Arrays.asList(
            SceneDataTable.Column.text("名称", 80),
            SceneDataTable.Column.text("描述", 120),
            SceneDataTable.Column.text("数量", 60));

    /** 场景根。 */
    private SceneNode sceneRoot;
    /** 场景运行时。 */
    private SceneRuntime runtime;
    /** 布局引擎（doLayout 需额外 layout overlay，harness.mountRoot 只 layout 主树，故保留独立引擎）。 */
    private SceneLayoutEngine layoutEngine;
    /** 语义化交互注入 harness（route 根 + scroll/typeText/click 入口）；其 runtime 即上方 runtime 字段。 */
    private SceneInteractionHarness harness;
    /** 受控行数据源。 */
    private Signal<List<SceneDataTable.Row>> rowsSignal;
    /** mount 句柄。 */
    private MountHandle handle;
    /** 控件根节点。 */
    private SceneNode tableRoot;
    /** 绘制引擎（断言每颗表面只采样一次 BACKDROP、行/单元格零滤镜）。 */
    private ScenePaintEngine paintEngine;

    /** 初始化响应式 DataTable 测试场景。 */
    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();
        rowsSignal = Signal.create(Collections.unmodifiableList(Arrays.asList(
                new SceneDataTable.Row(Arrays.asList("石头", "很长很长很长很长很长很长的描述", "64")),
                new SceneDataTable.Row(Arrays.asList("木头", "短描述", "12")),
                new SceneDataTable.Row(Arrays.asList("铁锭", "材料", "8")),
                new SceneDataTable.Row(Arrays.asList("金锭", "材料", "3")),
                new SceneDataTable.Row(Arrays.asList("钻石", "材料", "1")))));
        SceneDataTable.Props props = SceneDataTable.Props.builder(rowsSignal)
                .columns(COLUMNS)
                .rowHeight(ROW_HEIGHT)
                .viewportHeight(VIEWPORT_HEIGHT)
                .build();
        handle = runtime.mount(sceneRoot, SceneDataTable.create(runtime, props));
        tableRoot = handle.getRoot();
        runtime.flush();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
        doLayout();
    }

    /** 清理响应式运行时。 */
    @After
    public void tearDown() {
        handle.dispose();
        harness.dispose();
        ReactiveScheduler.get().reset();
    }

    /**
     * showScrollbar 默认 false 时，stackHost 只含 viewport（结构向后兼容）。
     */
    @Test
    public void showScrollbarFalseByDefault_stackHostHasOnlyViewport() {
        Assert.assertEquals("showScrollbar 默认 false 时 stackHost 应只含 viewport",
                1, stackHost().__getChildren().size());
    }

    /**
     * showScrollbar 为 true 时，stackHost 含 viewport 与 scrollbar column。
     */
    @Test
    public void showScrollbarTrue_stackHostHasViewportAndScrollbarColumn() {
        handle.dispose();
        SceneDataTable.Props props = SceneDataTable.Props.builder(rowsSignal)
                .columns(COLUMNS)
                .rowHeight(ROW_HEIGHT)
                .viewportHeight(VIEWPORT_HEIGHT)
                .showScrollbar(true)
                .build();
        handle = runtime.mount(sceneRoot, SceneDataTable.create(runtime, props));
        tableRoot = handle.getRoot();
        runtime.flush();
        doLayout();
        Assert.assertEquals("showScrollbar=true 时 stackHost 应含 viewport 与 scrollbar column",
                2, stackHost().__getChildren().size());
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

        harness.scroll(viewport(), -45);
        runtime.flush();
        LayoutResult result = doLayout();

        Assert.assertEquals("向下滚 wheelDelta<0 应增加 scrollOffsetY", 45, viewport().getScrollOffsetY());
        Assert.assertEquals("滚动只标 geometry，layout 应零重排", 0, result.getRelayoutCount());
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
        Assert.assertEquals("TextInput root 应包含 prefix/caret/highlight/caretAfter/suffix 五个子节点", 5, input.__getChildren().size());
        Assert.assertTrue("TextInput root 应可命中以接收输入", input.isHitTestable());
        Assert.assertEquals("DataTable TextInput 应使用输入槽横向 padding", 4, input.getPaddingLeft());
        Assert.assertEquals("DataTable TextInput 表面边框宽 = INPUT 配方", INPUT.getBorderWidth(), input.getBorderWidth());
        Assert.assertEquals("DataTable TextInput 表面圆角 = INPUT 配方", INPUT.getCornerRadius(), input.getCornerRadius());
        Assert.assertEquals("DataTable TextInput 默认背景 = INPUT 配方 idle tint",
                INPUT.getIdle().getTint(), input.getBackgroundColor());

        runtime.requestFocus(input);
        runtime.flush();

        Assert.assertEquals("TextInput 聚焦后 caret 应使用主题聚焦色", SceneThemes.DEFAULT.borderFocus(),
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

        harness.click(dataInput(0, 0));
        harness.typeText("新值");
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

        harness.click(dataInput(0, 0));
        harness.typeText("新");
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
        Assert.assertEquals("DataTable Select 表面边框宽 = INPUT 配方", INPUT.getBorderWidth(), select.getBorderWidth());
        Assert.assertEquals("DataTable Select 表面圆角 = INPUT 配方", INPUT.getCornerRadius(), select.getCornerRadius());
        Assert.assertEquals("DataTable Select 默认背景 = INPUT 配方 idle tint",
                INPUT.getIdle().getTint(), select.getBackgroundColor());
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

        harness.click(dataInput(0, 0));
        harness.typeText("X");
        runtime.flush();

        Assert.assertEquals("disabled 时行内编辑器应阻断输入，cell 保持空",
                "", rowsSignal.get().get(0).cells().get(0));
    }

    /** Builder.build() 构建的 Props 应保留必填字段与可选字段。 */
    @Test
    public void builderShouldKeepConfiguredProps() {
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
        Assert.assertSame("rows 引用一致", rows, fromBuilder.rows());
        Assert.assertEquals("columns 等价", columns, fromBuilder.columns());
        Assert.assertEquals("rowHeight 一致", ROW_HEIGHT, fromBuilder.rowHeight());
        Assert.assertEquals("viewportHeight 一致", VIEWPORT_HEIGHT, fromBuilder.viewportHeight());
        Assert.assertSame("enabled 引用一致", enabled, fromBuilder.enabled());
        Assert.assertSame("readOnly 引用一致", readOnly, fromBuilder.readOnly());
    }

    // ==================== 液态玻璃迁移：默认路径角色配方 ====================

    /**
     * 默认路径：外壳 = GROUP 配方、表头 = TOOLBAR 配方、编辑单元（TextInput/Select trigger）
     * = INPUT 配方、Select 弹出底座 = OVERLAY 配方，逐项（tint/缘色/边框宽/圆角/elevation/
     * backdrop 材质与模糊）等于所选角色配方；数据行只做 accent 半透明轻量交替覆盖、
     * 单元格与表头单元格自身不写表面属性。
     */
    @Test
    public void defaultSurfacesShouldUseSelectedRoleRecipes() {
        SceneSurfaceStyle group = SceneThemes.DEFAULT.surface(SceneTheme.Role.GROUP);
        SceneSurfaceStyle toolbar = SceneThemes.DEFAULT.surface(SceneTheme.Role.TOOLBAR);
        SceneSurfaceStyle overlay = SceneThemes.DEFAULT.surface(SceneTheme.Role.OVERLAY);
        mountRowsAndColumns(
                Arrays.asList(
                        new SceneDataTable.Row(Arrays.asList("A", "", "A")),
                        new SceneDataTable.Row(Arrays.asList("B", "", "B"))),
                Arrays.asList(
                        SceneDataTable.Column.text("名称", 80),
                        SceneDataTable.Column.textInput("备注", 120),
                        SceneDataTable.Column.select("等级", 100, Arrays.asList("A", "B", "C"))));

        // 外壳
        assertRecipe("外壳", group, viewport());
        // 表头
        assertRecipe("表头", toolbar, headerRow());
        assertNoSurface("表头单元格", headerCell(0));
        // 数据行：轻量交替覆盖
        SceneNode evenRow = dataRow(0);
        SceneNode oddRow = dataRow(1);
        assertLightOverlay("偶数行", evenRow);
        Assert.assertEquals("偶数行默认透明（露出底座玻璃）", 0, evenRow.getBackgroundColor());
        assertLightOverlay("奇数行", oddRow);
        Assert.assertEquals("奇数行 = 主题 accent 半透明交替覆盖",
                tint(SceneThemes.DEFAULT.accent(), ROW_ALTERNATE_ALPHA), oddRow.getBackgroundColor());
        // 单元格自身不写表面
        assertNoSurface("数据单元格", dataCell(0, 0));
        // 只读列文字取主题正文前景
        Assert.assertEquals("只读列文字 = 主题正文前景",
                SceneThemes.DEFAULT.foreground(), dataLabel(0, 0).getTextColor());
        // 编辑单元：复用已主题化控件
        assertRecipe("编辑单元 TextInput", INPUT, dataInput(0, 1));
        assertRecipe("编辑单元 Select trigger", INPUT, dataSelect(0, 2));
        // 弹出底座：OVERLAY 配方 + 候选行轻量覆盖
        openSelect(0, 2);
        assertRecipe("Select 弹出底座", overlay, overlayRoot());
        // 候选行只做轻量覆盖：第 0 行选中（值 "A"）→ accent 半透明；其余默认透明
        Assert.assertEquals("选中候选行 = 主题 accent 半透明覆盖",
                tint(SceneThemes.DEFAULT.accent(), 0x33), overlayItem(0).getBackgroundColor());
        assertLightOverlay("选中候选行", overlayItem(0));
        for (int i = 1; i < 3; i++) {
            assertLightOverlay("候选行[" + i + "]", overlayItem(i));
            Assert.assertEquals("候选行[" + i + "] 默认透明（露出浮层玻璃底）", 0,
                    overlayItem(i).getBackgroundColor());
        }
    }

    /**
     * 每颗语义表面只采样一次：外壳 GROUP 1 + 表头 TOOLBAR 1 + 每个编辑单元 INPUT 1；
     * 数据行与单元格零 BACKDROP（行只写 backgroundColor，不装滤镜）。
     */
    @Test
    public void eachSurfaceShouldSampleBackdropExactlyOnce() {
        mountRowsAndColumns(
                Arrays.asList(
                        new SceneDataTable.Row(Collections.singletonList("A")),
                        new SceneDataTable.Row(Collections.singletonList("B"))),
                Collections.singletonList(SceneDataTable.Column.textInput("名称", 120)));
        paintEngine.paint(sceneRoot);

        Assert.assertEquals("外壳自身恰好一条 BACKDROP", 1, backdropCount(viewport()));
        Assert.assertEquals("表头自身恰好一条 BACKDROP", 1, backdropCount(headerRow()));
        for (int row = 0; row < 2; row++) {
            Assert.assertEquals("行[" + row + "] 自身零 BACKDROP", 0, backdropCount(dataRow(row)));
            Assert.assertEquals("单元格[" + row + "] 自身零 BACKDROP", 0, backdropCount(dataCell(row, 0)));
            Assert.assertEquals("编辑单元[" + row + "] 自身恰好一条 BACKDROP", 1, backdropCount(dataInput(row, 0)));
        }
        PaintPlan plan = paintEngine.paint(sceneRoot).getPlan();
        Assert.assertEquals("整树 BACKDROP = 外壳 1 + 表头 1 + 每行编辑单元 1（每颗表面只采样一次）",
                1 + 1 + 2, countType(plan.getCommands(), PaintCommandType.BACKDROP));
    }

    /**
     * 真实进入编辑模式：点击聚焦并输入后，编辑单元外观仍是 INPUT 配方（染色/圆角/边框宽/滤镜
     * 不变，缘色切到配方 focusEdge），caret 取主题聚焦色，草稿写入受控 rows signal。
     */
    @Test
    public void editModeShouldKeepThemedEditorAppearance() {
        SceneDataTable.Row first = new SceneDataTable.Row(Collections.singletonList(""));
        mountRowsAndColumns(
                Collections.singletonList(first),
                Collections.singletonList(SceneDataTable.Column.textInput("名称", 120)));
        SceneNode editor = dataInput(0, 0);

        harness.click(editor);
        harness.typeText("新值");
        runtime.flush();
        Assert.assertEquals("编辑中草稿写入受控 signal", "新值", rowsSignal.get().get(0).cells().get(0));
        // 指针移出表格清除 hover，验证非 hover 档仍是 INPUT 配方
        harness.moveAt(CANVAS_WIDTH + 20, CANVAS_HEIGHT + 20);

        Assert.assertEquals("编辑中染色仍 = INPUT 配方 idle tint", INPUT.getIdle().getTint(), editor.getBackgroundColor());
        Assert.assertEquals("编辑中圆角仍 = INPUT 配方", INPUT.getCornerRadius(), editor.getCornerRadius());
        Assert.assertEquals("编辑中边框宽仍 = INPUT 配方", INPUT.getBorderWidth(), editor.getBorderWidth());
        Assert.assertEquals("编辑中实体高度仍 = INPUT 配方 idle elevation",
                INPUT.getIdle().getElevation(), editor.__getSurfaceElevation(), 0.0001F);
        Assert.assertEquals("聚焦缘色 = INPUT 配方 focusEdge", INPUT.getFocusEdge(), editor.getBorderColor());
        Assert.assertNotNull("编辑中仍带 INPUT 滤镜", editor.getBackdrop());
        Assert.assertEquals("编辑中滤镜材质仍 = INPUT 配方",
                INPUT.getBackdrop().getEffect().getMaterial(), editor.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("编辑中滤镜模糊仍 = INPUT 配方",
                INPUT.getBackdrop().getBlurRadius(), editor.getBackdrop().getBlurRadius());
        Assert.assertEquals("caret 取主题聚焦色", SceneThemes.DEFAULT.borderFocus(),
                editor.__getChildren().get(1).getBackgroundColor());
    }

    /**
     * 主题切换：外壳/表头/行/编辑单元外观更新；节点身份不变、草稿与编辑中状态、滚动偏移、
     * 行序保留；effect 数不增长。
     */
    @Test
    public void themeSwitchShouldUpdateSurfacesWithoutLosingState() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneSurfaceStyle darkGroup = dark.surface(SceneTheme.Role.GROUP);
        SceneSurfaceStyle lightGroup = light.surface(SceneTheme.Role.GROUP);
        SceneSurfaceStyle darkToolbar = dark.surface(SceneTheme.Role.TOOLBAR);
        SceneSurfaceStyle lightToolbar = light.surface(SceneTheme.Role.TOOLBAR);
        SceneSurfaceStyle darkInput = dark.surface(SceneTheme.Role.INPUT);
        SceneSurfaceStyle lightInput = light.surface(SceneTheme.Role.INPUT);
        Assert.assertNotEquals("两档 GROUP 配方必须不同，否则切换不传播",
                darkGroup.getIdle(), lightGroup.getIdle());

        List<SceneDataTable.Row> many = new ArrayList<SceneDataTable.Row>();
        for (int i = 0; i < 20; i++) {
            many.add(new SceneDataTable.Row(Arrays.asList("row" + i, "")));
        }
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        mountRowsAndColumnsInTheme(pageTheme, many, Arrays.asList(
                SceneDataTable.Column.text("名称", 80),
                SceneDataTable.Column.textInput("备注", 120)));

        // 草稿：编辑第一行（主题切换不得丢）
        SceneNode editor = dataInput(0, 1);
        harness.click(editor);
        harness.typeText("X");
        runtime.flush();
        Assert.assertEquals("前置：草稿已写入受控 signal", "X", rowsSignal.get().get(0).cells().get(1));

        // 滚动偏移：长列表向下滚
        int maxScroll = Math.max(0, box(content()).getHeight() - box(viewport()).getHeight());
        Assert.assertTrue("前置：长列表可滚动，maxScroll=" + maxScroll, maxScroll > 0);
        int offset = Math.min(45, maxScroll);
        harness.scroll(viewport(), -offset);
        Assert.assertEquals("前置：滚动偏移已应用", offset, viewport().getScrollOffsetY());
        // 指针移出表格清除 hover，使断言落在 idle 档
        harness.moveAt(CANVAS_WIDTH + 20, CANVAS_HEIGHT + 20);

        SceneNode viewport = viewport();
        SceneNode headerRow = headerRow();
        SceneNode firstRow = dataRow(0);
        SceneNode firstEditor = dataInput(0, 1);
        Assert.assertEquals("初始外壳 = 深色 GROUP 配方 idle tint", darkGroup.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertEquals("初始表头 = 深色 TOOLBAR 配方 idle tint", darkToolbar.getIdle().getTint(), headerRow.getBackgroundColor());
        Assert.assertEquals("初始编辑单元 = 深色 INPUT 配方 idle tint", darkInput.getIdle().getTint(), firstEditor.getBackgroundColor());
        Assert.assertEquals("初始奇数行 = 深色 accent 交替覆盖",
                tint(dark.accent(), ROW_ALTERNATE_ALPHA), dataRow(1).getBackgroundColor());
        Assert.assertEquals("初始只读文字 = 深色正文前景",
                dark.foreground(), dataLabel(0, 0).getTextColor());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertEquals("外壳染色随主题更新", lightGroup.getIdle().getTint(), viewport.getBackgroundColor());
        Assert.assertEquals("外壳圆角随主题更新", lightGroup.getCornerRadius(), viewport.getCornerRadius());
        Assert.assertEquals("外壳缘色随主题更新", lightGroup.getIdle().getEdge(), viewport.getBorderColor());
        Assert.assertEquals("外壳滤镜材质随主题更新",
                lightGroup.getBackdrop().getEffect().getMaterial(), viewport.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("表头染色随主题更新", lightToolbar.getIdle().getTint(), headerRow.getBackgroundColor());
        Assert.assertEquals("表头滤镜模糊随主题更新",
                lightToolbar.getBackdrop().getBlurRadius(), headerRow.getBackdrop().getBlurRadius());
        Assert.assertEquals("编辑单元染色随主题更新", lightInput.getIdle().getTint(), firstEditor.getBackgroundColor());
        Assert.assertEquals("编辑单元滤镜材质随主题更新",
                lightInput.getBackdrop().getEffect().getMaterial(), firstEditor.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("奇数行随主题更新为浅色 accent 覆盖",
                tint(light.accent(), ROW_ALTERNATE_ALPHA), dataRow(1).getBackgroundColor());
        Assert.assertEquals("只读文字随主题更新", light.foreground(), dataLabel(0, 0).getTextColor());

        Assert.assertSame("主题切换不重建 viewport", viewport, viewport());
        Assert.assertSame("主题切换不重建表头", headerRow, headerRow());
        Assert.assertSame("主题切换不重建行节点", firstRow, dataRow(0));
        Assert.assertSame("主题切换不重建编辑单元", firstEditor, dataInput(0, 1));
        Assert.assertEquals("主题切换不丢草稿", "X", rowsSignal.get().get(0).cells().get(1));
        Assert.assertEquals("主题切换保留滚动偏移", offset, viewport.getScrollOffsetY());
        Assert.assertEquals("主题切换保留行序（第 2 行仍是 row1）", "row1", dataLabel(1, 0).getText());
        Assert.assertEquals("主题切换不新增 effect",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());

        // 编辑中状态保留：主题切换后继续输入仍写入同一编辑器草稿
        harness.typeText("Y");
        runtime.flush();
        Assert.assertEquals("主题切换后编辑中状态保留（继续输入仍生效）",
                "XY", rowsSignal.get().get(0).cells().get(1));
    }

    /**
     * 关闭滤镜档（withoutBackdrop）：外壳/表头/编辑单元仍取角色配方的不透明底色与圆角/边框，
     * 整树零 BACKDROP；数据行仍是轻量覆盖。
     */
    @Test
    public void withoutBackdropThemeShouldKeepSurfacesReadable() {
        SceneTheme noBackdrop = SceneTheme.liquidGlassDark().withoutBackdrop();
        SceneSurfaceStyle group = noBackdrop.surface(SceneTheme.Role.GROUP);
        SceneSurfaceStyle toolbar = noBackdrop.surface(SceneTheme.Role.TOOLBAR);
        SceneSurfaceStyle input = noBackdrop.surface(SceneTheme.Role.INPUT);
        Signal<SceneTheme> pageTheme = Signal.create(noBackdrop);
        mountRowsAndColumnsInTheme(pageTheme,
                Arrays.asList(
                        new SceneDataTable.Row(Collections.singletonList("A")),
                        new SceneDataTable.Row(Collections.singletonList("B"))),
                Collections.singletonList(SceneDataTable.Column.textInput("名称", 120)));

        Assert.assertNull("外壳关闭滤镜", viewport().getBackdrop());
        Assert.assertEquals("外壳无滤镜档底色 = 配方不透明 tint", group.getIdle().getTint(), viewport().getBackgroundColor());
        Assert.assertNull("表头关闭滤镜", headerRow().getBackdrop());
        Assert.assertEquals("表头无滤镜档底色 = 配方不透明 tint", toolbar.getIdle().getTint(), headerRow().getBackgroundColor());
        SceneNode editor = dataInput(0, 0);
        Assert.assertNull("编辑单元关闭滤镜", editor.getBackdrop());
        Assert.assertEquals("编辑单元无滤镜档底色 = 配方不透明 tint", input.getIdle().getTint(), editor.getBackgroundColor());
        assertLightOverlay("关闭滤镜档数据行", dataRow(0));

        PaintPlan plan = paintEngine.paint(sceneRoot).getPlan();
        Assert.assertEquals("关闭滤镜档整树零 BACKDROP", 0, countType(plan.getCommands(), PaintCommandType.BACKDROP));
    }

    /**
     * 卸载后表面绑定 effect 回收，主题更新不再写入旧节点。
     */
    @Test
    public void unmountShouldReleaseSurfaceBindings() {
        handle.dispose();
        runtime.flush();
        int before = ReactiveTestProbe.registeredEffectCount();

        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        sceneRoot = new SceneNode();
        rowsSignal = Signal.create(Collections.singletonList(
                new SceneDataTable.Row(Collections.singletonList("A"))));
        SceneDataTable.Props props = SceneDataTable.Props.builder(rowsSignal)
                .columns(Collections.singletonList(SceneDataTable.Column.textInput("名称", 120)))
                .rowHeight(ROW_HEIGHT)
                .viewportHeight(VIEWPORT_HEIGHT)
                .build();
        handle = runtime.mount(sceneRoot, () -> {
            SceneNode[] holder = new SceneNode[1];
            SceneThemes.withTheme(pageTheme, () -> holder[0] = SceneDataTable.create(runtime, props).get());
            return holder[0];
        });
        tableRoot = handle.getRoot();
        runtime.flush();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
        doLayout();

        Assert.assertTrue("默认路径应注册响应式外观绑定",
                ReactiveTestProbe.registeredEffectCount() > before);
        SceneNode viewport = viewport();
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
     * 兼容入口：SceneDataTableEditorChrome 的纯函数解析器与 primitive 装饰方法全部改为消费主题
     * （不再返回静态色板值）。
     */
    @Test
    public void editorChromeCompatEntryShouldDeriveFromTheme() {
        SceneSurfaceStyle input = SceneThemes.DEFAULT.surface(SceneTheme.Role.INPUT);
        Assert.assertEquals("resolveEditSlotBackground 未聚焦 = INPUT 配方 idle tint",
                input.getIdle().getTint(),
                SceneDataTableEditorChrome.resolveEditSlotBackground(Boolean.FALSE, Boolean.FALSE));
        Assert.assertEquals("resolveEditSlotBackground 聚焦 = INPUT 配方 hovered tint",
                input.getHovered().getTint(),
                SceneDataTableEditorChrome.resolveEditSlotBackground(Boolean.TRUE, Boolean.FALSE));
        Assert.assertEquals("resolveEditBorder 聚焦 = INPUT 配方 focusEdge",
                input.getFocusEdge(),
                SceneDataTableEditorChrome.resolveEditBorder(Boolean.TRUE, Boolean.FALSE));
        Assert.assertEquals("resolveEditBorder 默认 = INPUT 配方 idle edge",
                input.getIdle().getEdge(),
                SceneDataTableEditorChrome.resolveEditBorder(Boolean.FALSE, Boolean.FALSE));
        Assert.assertEquals("resolveEditTextColor 默认 = 主题正文前景",
                SceneThemes.DEFAULT.foreground(),
                SceneDataTableEditorChrome.resolveEditTextColor(Boolean.FALSE, Boolean.TRUE));
        Assert.assertEquals("resolveEditTextColor 占位 = 主题次要前景",
                SceneThemes.DEFAULT.mutedForeground(),
                SceneDataTableEditorChrome.resolveEditTextColor(Boolean.TRUE, Boolean.TRUE));
        Assert.assertEquals("resolveEditTextColor 禁用 = 主题禁用前景",
                SceneThemes.DEFAULT.disabledForeground(),
                SceneDataTableEditorChrome.resolveEditTextColor(Boolean.FALSE, Boolean.FALSE));
        Assert.assertEquals("resolveSelectArrowColor 展开 = 主题聚焦色",
                SceneThemes.DEFAULT.borderFocus(),
                SceneDataTableEditorChrome.resolveSelectArrowColor(Boolean.TRUE, Boolean.TRUE));
        Assert.assertEquals("resolveItemBackground 选中 = 主题 accent 半透明覆盖",
                tint(SceneThemes.DEFAULT.accent(), 0x33),
                SceneDataTableEditorChrome.resolveItemBackground(true, false, Boolean.FALSE));
        Assert.assertEquals("resolveItemBackground 默认透明",
                0, SceneDataTableEditorChrome.resolveItemBackground(false, false, Boolean.FALSE));
    }

    /**
     * 兼容入口仍能主题化自行装配的 primitive：表面取 INPUT 配方、文字取主题正文前景。
     */
    @Test
    public void compatEditorChromeShouldThemeRawPrimitive() {
        handle.dispose();
        sceneRoot = new SceneNode();
        Signal<String> value = Signal.create("x");
        Signal<Boolean> enabled = Signal.create(Boolean.TRUE);
        handle = runtime.mount(sceneRoot, () -> {
            SceneTextInputPrimitive.Result result = SceneTextInputPrimitive.create(runtime,
                    new SceneTextInputPrimitive.Props(value, enabled, Signal.create(Boolean.FALSE), "",
                            Integer.MAX_VALUE, SceneInputType.TEXT, next -> { }));
            SceneDataTableEditorChrome.decorateTextInputEditor(runtime, result, 20, enabled);
            return result.root();
        });
        runtime.flush();

        SceneNode root = handle.getRoot();
        Assert.assertEquals("兼容入口表面染色 = INPUT 配方 idle tint", INPUT.getIdle().getTint(), root.getBackgroundColor());
        Assert.assertEquals("兼容入口表面圆角 = INPUT 配方", INPUT.getCornerRadius(), root.getCornerRadius());
        Assert.assertNotNull("兼容入口仍带 INPUT 滤镜", root.getBackdrop());
        Assert.assertEquals("兼容入口文字 = 主题正文前景",
                SceneThemes.DEFAULT.foreground(), root.__getChildren().get(0).getTextColor());
    }

    /** 跑一帧布局。 */
    private LayoutResult doLayout() {
        LayoutResult result = layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        for (int i = 0; i < runtime.getOverlayHost().bottomFirst().size(); i++) {
            SceneNode overlay = runtime.getOverlayHost().bottomFirst().get(i).getRoot();
            result = layoutEngine.layout(overlay, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        }
        return result;
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
                                     ReadableSignal<Boolean> enabled,
                                     ReadableSignal<Boolean> readOnly) {
        handle.dispose();
        sceneRoot = new SceneNode();
        rowsSignal = Signal.create(rows);
        SceneDataTable.Props props = SceneDataTable.Props.builder(rowsSignal)
                .columns(columns)
                .rowHeight(ROW_HEIGHT)
                .viewportHeight(VIEWPORT_HEIGHT)
                .enabled(enabled)
                .readOnly(readOnly)
                .build();
        handle = runtime.mount(sceneRoot, SceneDataTable.create(runtime, props));
        tableRoot = handle.getRoot();
        runtime.flush();
        // 新 sceneRoot 需重新挂载 harness 路由根，否则 harness.scroll/typeText route 到旧根
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
        doLayout();
    }

    /**
     * 在来源主题作用域内挂载 DataTable。
     *
     * <p>{@code SceneThemes.withTheme} 必须在 mount builder 内执行（此时 {@code Owner.current()}
     * 是来源作用域），否则退化为 runtime 默认主题。</p>
     *
     * @param pageTheme 页面主题信号
     * @param rows      行数据
     * @param columns   列定义
     */
    private void mountRowsAndColumnsInTheme(ReadableSignal<SceneTheme> pageTheme,
                                            List<SceneDataTable.Row> rows, List<SceneDataTable.Column> columns) {
        handle.dispose();
        sceneRoot = new SceneNode();
        rowsSignal = Signal.create(rows);
        SceneDataTable.Props props = SceneDataTable.Props.builder(rowsSignal)
                .columns(columns)
                .rowHeight(ROW_HEIGHT)
                .viewportHeight(VIEWPORT_HEIGHT)
                .build();
        handle = runtime.mount(sceneRoot, () -> {
            SceneNode[] holder = new SceneNode[1];
            SceneThemes.withTheme(pageTheme, () -> holder[0] = SceneDataTable.create(runtime, props).get());
            return holder[0];
        });
        tableRoot = handle.getRoot();
        runtime.flush();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
        doLayout();
    }

    /**
     * 断言节点表面逐项等于角色配方（tint/缘色/边框宽/圆角/elevation/backdrop 材质与模糊）。
     *
     * @param what   断言描述前缀
     * @param recipe 期望角色配方
     * @param node   被断言节点
     */
    private static void assertRecipe(String what, SceneSurfaceStyle recipe, SceneNode node) {
        Assert.assertEquals(what + " 染色 = 配方 idle tint", recipe.getIdle().getTint(), node.getBackgroundColor());
        Assert.assertEquals(what + " 缘色 = 配方 idle edge", recipe.getIdle().getEdge(), node.getBorderColor());
        Assert.assertEquals(what + " 边框宽 = 配方", recipe.getBorderWidth(), node.getBorderWidth());
        Assert.assertEquals(what + " 圆角 = 配方", recipe.getCornerRadius(), node.getCornerRadius());
        Assert.assertEquals(what + " 实体高度 = 配方 idle elevation",
                recipe.getIdle().getElevation(), node.__getSurfaceElevation(), 0.0001F);
        Assert.assertNotNull(what + " 默认带液态玻璃滤镜", node.getBackdrop());
        Assert.assertEquals(what + " 滤镜模糊半径 = 配方",
                recipe.getBackdrop().getBlurRadius(), node.getBackdrop().getBlurRadius());
        Assert.assertEquals(what + " 滤镜材质 = 配方",
                recipe.getBackdrop().getEffect().getMaterial(), node.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals(what + " 透镜强度 = 配方 backdrop lens × idle lensFactor",
                recipe.getBackdrop().getEffect().getLensStrength() * recipe.getIdle().getLensFactor(),
                node.getBackdrop().getEffect().getLensStrength(), 0.0001F);
    }

    /**
     * 断言节点只做轻量覆盖：不装滤镜、不写边框宽/圆角/实体高度（只写 backgroundColor）。
     *
     * @param what 断言描述前缀
     * @param node 被断言节点
     */
    private static void assertLightOverlay(String what, SceneNode node) {
        Assert.assertNull(what + " 不装滤镜", node.getBackdrop());
        Assert.assertEquals(what + " 不写边框宽（外观归轻量覆盖）", 0, node.getBorderWidth());
        Assert.assertEquals(what + " 不写圆角", 0, node.getCornerRadius());
        Assert.assertEquals(what + " 不写实体高度", -1.0F, node.__getSurfaceElevation(), 0.0001F);
    }

    /**
     * 断言节点不写任何表面属性（纯布局容器/单元格）。
     *
     * @param what 断言描述前缀
     * @param node 被断言节点
     */
    private static void assertNoSurface(String what, SceneNode node) {
        Assert.assertNull(what + " 不装滤镜", node.getBackdrop());
        Assert.assertEquals(what + " 不写背景", 0, node.getBackgroundColor());
        Assert.assertEquals(what + " 不写边框宽", 0, node.getBorderWidth());
        Assert.assertEquals(what + " 不写圆角", 0, node.getCornerRadius());
        Assert.assertEquals(what + " 不写实体高度", -1.0F, node.__getSurfaceElevation(), 0.0001F);
    }

    /** 保留色 RGB、替换 alpha 通道（轻量覆盖口径）。 */
    private static int tint(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
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

    /** 获取滚动视口。 */
    private SceneNode viewport() {
        SceneNode found = findScrollable(tableRoot);
        if (found == null) {
            throw new AssertionError("未找到滚动视口");
        }
        return found;
    }

    /**
     * 递归查找子树中第一个 isScrollable 节点。
     *
     * <p>viewport 现嵌套在 stackHost(ROW) 内，不再是 tableRoot 直接子，需递归定位。</p>
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

    /** @return 承载 viewport 与可选滚动条的 stackHost（viewport 的父节点）。 */
    private SceneNode stackHost() {
        return viewport().__getParent();
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

    /** 路由鼠标指针事件（白盒回退（overlay 树外路由）：Select overlay 用例，harness 不接管 overlay 路由）。 */
    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }
}
