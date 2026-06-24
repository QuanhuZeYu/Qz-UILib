package club.heiqi.uilib.ui.scene.control;

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
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
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;

/**
 * SceneTable 单元测试 —— 验证最小版静态表格的固定列宽、行高、裁剪和纵向滚动。
 */
public class SceneTableTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    private MountHandle handle;
    private SceneNode tableRoot;

    private static final int CANVAS_WIDTH = 500;
    private static final int CANVAS_HEIGHT = 300;
    private static final int STUB_CHAR_WIDTH = 8;
    private static final int ROW_HEIGHT = 30;
    private static final int VIEWPORT_HEIGHT = 90;
    private static final List<Integer> COLUMN_WIDTHS = Arrays.asList(80, 120, 60);

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
        layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer(STUB_CHAR_WIDTH, 16));
        paintEngine = new ScenePaintEngine();
        sceneRoot = new SceneNode();

        SceneTable.Props props = new SceneTable.Props(
                Arrays.asList("名称", "描述", "数量"),
                Arrays.asList(
                        Arrays.asList("石头", "很长很长很长很长很长很长的描述", "64"),
                        Arrays.asList("木头", "短描述", "12"),
                        Arrays.asList("铁锭", "材料", "8"),
                        Arrays.asList("金锭", "材料", "3"),
                        Arrays.asList("钻石", "材料", "1")
                ),
                COLUMN_WIDTHS,
                ROW_HEIGHT,
                VIEWPORT_HEIGHT);
        handle = runtime.mount(sceneRoot, SceneTable.create(runtime, props));
        tableRoot = handle.getRoot();
    }

    @After
    public void tearDown() {
        handle.dispose();
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    private SceneNode viewport() {
        return tableRoot.__getChildren().get(0);
    }

    private SceneNode content() {
        return viewport().__getChildren().get(0);
    }

    private SceneNode row(int index) {
        return content().__getChildren().get(index);
    }

    private SceneNode cell(int rowIndex, int columnIndex) {
        return row(rowIndex).__getChildren().get(columnIndex);
    }

    private SceneNode label(int rowIndex, int columnIndex) {
        return cell(rowIndex, columnIndex).__getChildren().get(0);
    }

    private LayoutBox box(SceneNode node) {
        return (LayoutBox) node.getCachedLayout();
    }

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

    /** 固定列宽应同时作用于表头和数据单元格。 */
    @Test
    public void columnWidthsShouldApplyToHeaderAndDataCells() {
        doLayout();

        for (int i = 0; i < COLUMN_WIDTHS.size(); i++) {
            Assert.assertEquals("表头列宽应等于 Props columnWidths", COLUMN_WIDTHS.get(i).intValue(),
                    box(cell(0, i)).getWidth());
            Assert.assertEquals("数据列宽应等于 Props columnWidths", COLUMN_WIDTHS.get(i).intValue(),
                    box(cell(1, i)).getWidth());
        }
    }

    /** 表头第 i 列与数据行第 i 列 x 坐标应对齐。 */
    @Test
    public void headerAndDataColumnsShouldAlignByX() {
        doLayout();

        for (int i = 0; i < COLUMN_WIDTHS.size(); i++) {
            Assert.assertEquals("表头列与数据列 x 应一致", box(cell(0, i)).getX(), box(cell(2, i)).getX());
        }
    }

    /** 每一行固定高度，不由文本撑开。 */
    @Test
    public void rowsShouldUseFixedHeight() {
        doLayout();

        for (int i = 0; i < content().__getChildren().size(); i++) {
            Assert.assertEquals("每一行高度应固定", ROW_HEIGHT, box(row(i)).getHeight());
            Assert.assertEquals("每个单元格高度应固定", ROW_HEIGHT, box(cell(i, 0)).getHeight());
        }
    }

    /** 视口高度钉死，内容高度允许超过视口。 */
    @Test
    public void viewportShouldPinHeightAndContentCanOverflow() {
        doLayout();

        Assert.assertEquals("视口高度应钉死为 Props viewportHeight", VIEWPORT_HEIGHT, box(viewport()).getHeight());
        Assert.assertTrue("内容高度应超过视口以触发滚动", box(content()).getHeight() > box(viewport()).getHeight());
    }

    /** 滚轮应经 signal 更新 scrollOffsetY，且滚动帧不触发布局重排。 */
    @Test
    public void scrollShouldUpdateOffsetWithoutRelayout() {
        doLayout();
        Assert.assertEquals("初始滚动偏移为 0", 0, viewport().getScrollOffsetY());

        routeScroll(viewport(), -45);
        runtime.flush();
        doLayout();

        Assert.assertEquals("向下滚 wheelDelta<0 应增加 scrollOffsetY", 45, viewport().getScrollOffsetY());
        Assert.assertEquals("滚动只标 geometry，layout 应零重排", 0, layoutEngine.__getRelayoutCount());
    }

    /** 长文本单元格应裁剪子节点，label 不参与命中，绘制计划包含裁剪命令。 */
    @Test
    public void longTextCellShouldClipAndLabelShouldNotHitTest() {
        doLayout();

        SceneNode longTextCell = cell(1, 1);
        Assert.assertTrue("长文本单元格应开启 clipChildren", longTextCell.isClipChildren());
        Assert.assertFalse("单元格文本 label 应命中穿透", label(1, 1).isHitTestable());

        PaintPlan plan = paintEngine.paint(sceneRoot);
        Assert.assertTrue("绘制计划应包含 CLIP_PUSH", hasClipPush(plan));
    }

    /** 行长度不齐时补空/截断，且列数保持稳定。 */
    @Test
    public void raggedRowsShouldNormalizeToStableColumnCount() {
        SceneTable.Props props = new SceneTable.Props(
                Arrays.asList("A", "B", "C"),
                Arrays.asList(
                        Arrays.asList("onlyA"),
                        Arrays.asList("A", "B", "C", "D")
                ),
                COLUMN_WIDTHS,
                ROW_HEIGHT,
                VIEWPORT_HEIGHT);
        MountHandle raggedHandle = runtime.mount(sceneRoot, SceneTable.create(runtime, props));
        SceneNode raggedRoot = raggedHandle.getRoot();
        doLayout();

        SceneNode raggedContent = raggedRoot.__getChildren().get(0).__getChildren().get(0);
        Assert.assertEquals("补空行列数应稳定", 3, raggedContent.__getChildren().get(1).__getChildren().size());
        Assert.assertEquals("截断行列数应稳定", 3, raggedContent.__getChildren().get(2).__getChildren().size());
        Assert.assertEquals("缺失列应补空字符串", "",
                raggedContent.__getChildren().get(1).__getChildren().get(1).__getChildren().get(0).getText());
        Assert.assertEquals("超出列应被截断，只保留第三列", "C",
                raggedContent.__getChildren().get(2).__getChildren().get(2).__getChildren().get(0).getText());

        raggedHandle.dispose();
    }

    private boolean hasClipPush(PaintPlan plan) {
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.CLIP_PUSH) {
                return true;
            }
        }
        return false;
    }
}
