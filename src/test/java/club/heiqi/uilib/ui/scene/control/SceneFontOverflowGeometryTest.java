package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLinePlan;

/**
 * S5 几何与溢出断言：字号驱动的几何必须跟着变，文字溢出必须可见（INV-GEO-4「不得静默截断」）。
 *
 * <p>度量替身刻意让<b>宽与行高都随字号变</b>（宽 = 码点 × 字号 / 2，行高 = 字号）：</p>
 * <ul>
 *   <li>用 {@code FixedTextMeasurer}（行高恒 16）会让「行高随字号」的断言恒绿——本类必须用缩放度量；</li>
 *   <li>box 断言一律 {@code ((LayoutBox) node.getCachedLayout())}，不只钉字号字段。</li>
 * </ul>
 *
 * <p>覆盖：① DataTable（表头/只读单元格省略号 + 行高自适应 + 编辑器与只读同字号）、
 * ② KeyValueMap 表头与 ObjectField 标签轨道溢出、③ VirtualGrid 图位高按生效字号让位。</p>
 */
public class SceneFontOverflowGeometryTest {

    /** 画布尺寸。 */
    private static final int CANVAS_WIDTH = 640;
    private static final int CANVAS_HEIGHT = 480;

    /** 被测控件根字号：默认 16；①③ 用例调到 32（翻倍），④ 尺寸收口用例按 16 → 24。 */
    private static final int SMALL_FONT = 16;
    private static final int LARGE_FONT = 32;
    /** ④ 段宽/条高收口口径的字号（Lead 指定 16 → 24）。 */
    private static final int METRIC_FONT = 24;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    // ==================== ① SceneDataTable ====================

    /** DataTable 夹具：runtime / 布局引擎 / 根 / 句柄 + 列宽常量。 */
    private static final class TableFixture {
        static final int NAME_TRACK = 80;
        static final int DESC_TRACK = 120;
        static final int ROW_HEIGHT = 28;
        static final int CELL_PADDING = 4;
        static final String LONG_TEXT = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

        final FontMeasurer measurer = new FontMeasurer();
        final SceneRuntime rt = new SceneRuntime(measurer);
        final SceneLayoutEngine engine = new SceneLayoutEngine(measurer);
        final SceneNode sceneRoot = new SceneNode();
        final Signal<List<SceneDataTable.Row>> rows = Signal.create(Collections.unmodifiableList(
                Arrays.asList(new SceneDataTable.Row(Arrays.asList(LONG_TEXT, "edit")))));
        final MountHandle handle;

        TableFixture() {
            SceneDataTable.Props props = SceneDataTable.Props.builder(rows)
                    .columns(Arrays.asList(SceneDataTable.Column.text("名称", NAME_TRACK),
                            SceneDataTable.Column.textInput("描述", DESC_TRACK)))
                    .rowHeight(ROW_HEIGHT)
                    .viewportHeight(160)
                    .build();
            handle = rt.mount(sceneRoot, SceneDataTable.create(rt, props));
            rt.flush();
            frame();
        }

        /** 跑一帧：布局 + 布局纪元桥接 + flush（字号声明 → 几何 → 效果收敛）。 */
        void frame() {
            engine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
            rt.__bridgeLayoutEpoch(engine.layoutEpoch());
            rt.flush();
        }

        SceneNode viewport() {
            return findScrollable(handle.getRoot());
        }

        SceneNode content() {
            return viewport().__getChildren().get(0);
        }

        SceneNode headerRow() {
            return content().__getChildren().get(0);
        }

        SceneNode dataRow() {
            return content().__getChildren().get(1).__getChildren().get(0);
        }

        SceneNode dataCell(int col) {
            return dataRow().__getChildren().get(col);
        }

        SceneNode readonlyLabel() {
            return dataCell(0).__getChildren().get(0);
        }

        SceneNode editor() {
            return dataCell(1).__getChildren().get(0);
        }

        void dispose() {
            handle.dispose();
            rt.dispose();
        }
    }

    /**
     * ① 横向：列宽是结构轨道，字号 32 时超宽文本不得被单元格静默裁掉——
     * 必须在槽位内以省略号收尾，且实际行宽 ≤ 槽位内框宽。
     */
    @Test
    public void dataTableTextIsEllipsizedInsideTrackAtLargeFont() {
        TableFixture f = new TableFixture();
        try {
            int innerWidth = TableFixture.NAME_TRACK - 2 * TableFixture.CELL_PADDING;
            // 声明面：只读列与表头都必须显式声明「单行 + 省略号 + 槽位换行宽」，否则 clipChildren 静默截断。
            Assert.assertEquals("只读列 maxTextWidth 必须等于槽位内框宽", innerWidth, f.readonlyLabel().getMaxTextWidth());
            Assert.assertEquals("只读列必须限 1 行", 1, f.readonlyLabel().getMaxLines());
            Assert.assertTrue("只读列必须开启省略号", f.readonlyLabel().isEllipsis());
            Assert.assertEquals("表头 label maxTextWidth 必须等于槽位内框宽", innerWidth,
                    f.headerRow().__getChildren().get(0).__getChildren().get(0).getMaxTextWidth());

            // 几何面：字号 16 → 32 后，绘制行必须是「省略号收尾 + 行宽 ≤ 内框宽」。
            f.handle.fontSize(LARGE_FONT);
            f.rt.flush();
            f.frame();

            Assert.assertEquals("字号声明必须生效到只读文本", LARGE_FONT, f.readonlyLabel().effectiveFontSize());
            TextLinePlan plan = f.readonlyLabel().getCachedTextPlan();
            Assert.assertNotNull("布局必须产出文本行计划", plan);
            Assert.assertEquals("单行槽位只允许 1 行", 1, plan.getLines().size());
            String line = plan.getLines().get(0);
            Assert.assertTrue("超宽文本必须以省略号可见截断：" + line, line.endsWith("..."));
            Assert.assertTrue("省略号行宽不得超过槽位内框宽（实测 " + f.measurer.measureWidth(line, LARGE_FONT) + "）",
                    f.measurer.measureWidth(line, LARGE_FONT) <= innerWidth);
        } finally {
            f.dispose();
        }
    }

    /**
     * ① 纵向：行高 = max(Props.rowHeight, lineHeight(生效字号) + 2*CELL_PADDING)。
     * 字号 16 → 32 时，行高必须从下限 28 涨到 32 + 8 = 40，否则大字号会被行框裁字。
     */
    @Test
    public void dataTableRowHeightGrowsWithResolvedFontSize() {
        TableFixture f = new TableFixture();
        try {
            Assert.assertEquals("字号 16 时行高取下限（16 + 8 < 28）",
                    TableFixture.ROW_HEIGHT, box(f.dataRow()).getHeight());
            Assert.assertEquals("字号 16 时表头行高同样取下限",
                    TableFixture.ROW_HEIGHT, box(f.headerRow()).getHeight());

            f.handle.fontSize(LARGE_FONT);
            f.rt.flush();
            f.frame();

            int expected = LARGE_FONT + 2 * TableFixture.CELL_PADDING;
            Assert.assertEquals("字号 32 时数据行高必须 = lineHeight(32) + 2*内边距",
                    expected, box(f.dataRow()).getHeight());
            Assert.assertEquals("字号 32 时表头行高必须 = lineHeight(32) + 2*内边距",
                    expected, box(f.headerRow()).getHeight());
            Assert.assertEquals("字号 32 时单元格高必须跟着行高", expected, box(f.dataCell(0)).getHeight());
        } finally {
            f.dispose();
        }
    }

    /**
     * ① INV-GEO-5 同槽同字号：同一行内只读单元格与内联编辑器必须解析出同一字号，
     * 且编辑器高度不低于所在字号的行高（不再被 20px 内容高钉死而裁字）。
     */
    @Test
    public void dataTableEditorSharesResolvedFontSizeAndGrowsWithIt() {
        TableFixture f = new TableFixture();
        try {
            Assert.assertEquals("字号 16 时只读与编辑器同字号",
                    f.readonlyLabel().effectiveFontSize(), f.editor().effectiveFontSize());

            f.handle.fontSize(LARGE_FONT);
            f.rt.flush();
            f.frame();

            Assert.assertEquals("字号 32 时只读文本生效字号", LARGE_FONT, f.readonlyLabel().effectiveFontSize());
            Assert.assertEquals("字号 32 时编辑器生效字号必须与只读文本一致（INV-GEO-5）",
                    f.readonlyLabel().effectiveFontSize(), f.editor().effectiveFontSize());
            Assert.assertTrue("编辑器盒高必须 ≥ 该字号行高（实测 " + box(f.editor()).getHeight() + "）",
                    box(f.editor()).getHeight() >= LARGE_FONT);
        } finally {
            f.dispose();
        }
    }

    // ==================== ② KeyValueMap 表头 / ObjectField 标签 ====================

    /** ② KeyValueMap 表头单元格：固定轨道 + 可见省略号声明；表头行高随字号。 */
    @Test
    public void keyValueMapHeaderDeclaresOverflowAndGrowsWithFont() {
        FontMeasurer measurer = new FontMeasurer();
        SceneRuntime rt = new SceneRuntime(measurer);
        SceneLayoutEngine engine = new SceneLayoutEngine(measurer);
        SceneNode sceneRoot = new SceneNode();
        Signal<List<KeyValueRow>> rows = Signal.create(Collections.unmodifiableList(Arrays.asList(
                new KeyValueRow("name", "qz", ValueType.STRING))));
        SceneKeyValueMap.Props props = SceneKeyValueMap.Props.builder(rows).label("属性").build();
        MountHandle handle = rt.mount(sceneRoot, SceneKeyValueMap.create(rt, props));
        try {
            rt.flush();
            engine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
            rt.__bridgeLayoutEpoch(engine.layoutEpoch());
            rt.flush();

            SceneNode header = findHeaderRow(handle.getRoot(), 4);
            SceneNode firstCell = header.__getChildren().get(0);
            Assert.assertEquals("表头单元格 maxTextWidth 必须等于固定轨道宽",
                    firstCell.getPreferredWidth(), firstCell.getMaxTextWidth());
            Assert.assertEquals("表头单元格必须限 1 行", 1, firstCell.getMaxLines());
            Assert.assertTrue("表头单元格必须开启省略号", firstCell.isEllipsis());
            Assert.assertEquals("字号 16 时表头行高 = 行高(16)", SMALL_FONT, box(header).getHeight());

            handle.fontSize(LARGE_FONT);
            rt.flush();
            engine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
            rt.__bridgeLayoutEpoch(engine.layoutEpoch());
            rt.flush();

            Assert.assertEquals("字号 32 时表头行高必须变高（文字变了框必须变）",
                    LARGE_FONT, box(header).getHeight());
            Assert.assertEquals("表头生效字号", LARGE_FONT, firstCell.effectiveFontSize());
        } finally {
            handle.dispose();
            rt.dispose();
        }
    }

    /** ② ObjectField 标签轨道：固定宽 + 可见省略号；长字段名必须在 132px 轨道内省略。 */
    @Test
    public void objectFieldLabelTrackEllipsizesWithinFixedWidth() {
        FontMeasurer measurer = new FontMeasurer();
        SceneRuntime rt = new SceneRuntime(measurer);
        SceneLayoutEngine engine = new SceneLayoutEngine(measurer);
        SceneNode sceneRoot = new SceneNode();
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("a_very_long_field_name_that_overflows_the_track", "v");
        Signal<Map<String, Object>> valueSignal = Signal.create(value);
        SceneObjectField.Props props = SceneObjectField.Props.builder(valueSignal)
                .label("对象")
                .expandedPaths(Signal.create(Collections.<String>emptySet()))
                .maxDepth(5)
                .build();
        MountHandle handle = rt.mount(sceneRoot, SceneObjectField.create(rt, props));
        try {
            rt.flush();
            engine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
            rt.__bridgeLayoutEpoch(engine.layoutEpoch());
            rt.flush();

            SceneNode label = findLabelTrack(handle.getRoot());
            Assert.assertNotNull("必须存在 132px 标签轨道", label);
            Assert.assertEquals("标签必须限 1 行", 1, label.getMaxLines());
            Assert.assertTrue("标签必须开启省略号", label.isEllipsis());
            Assert.assertEquals("标签 maxTextWidth 必须等于轨道宽", 132, label.getMaxTextWidth());

            handle.fontSize(LARGE_FONT);
            rt.flush();
            engine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
            rt.__bridgeLayoutEpoch(engine.layoutEpoch());
            rt.flush();

            TextLinePlan plan = label.getCachedTextPlan();
            Assert.assertNotNull("标签必须有行计划", plan);
            Assert.assertTrue("长字段名必须在轨道内省略：" + plan.getLines().get(0),
                    plan.getLines().get(0).endsWith("..."));
            Assert.assertTrue("省略后行宽不得超过轨道宽",
                    measurer.measureWidth(plan.getLines().get(0), LARGE_FONT) <= 132);
        } finally {
            handle.dispose();
            rt.dispose();
        }
    }

    // ==================== ③ SceneVirtualGrid 图位高 ====================

    /**
     * ③ 图位高 = 单元轨道高 - 内边距 - 标签行高（生效字号） - 间距：
     * 字号 12 → 32 时图位必须从 42 缩到 22，图位与标签不得重叠；标签同时声明槽内省略号。
     */
    @Test
    public void virtualGridIconHeightYieldsToLabelLineHeight() {
        FontMeasurer measurer = new FontMeasurer();
        SceneRuntime rt = new SceneRuntime(measurer);
        SceneLayoutEngine engine = new SceneLayoutEngine(measurer);
        SceneNode sceneRoot = new SceneNode();
        final int cellW = 64;
        final int cellH = 64;
        final int cellPadding = 4;
        final int labelGap = 2;
        Signal<List<SceneVirtualGrid.Item>> items = Signal.create(Collections.unmodifiableList(
                Arrays.asList(new SceneVirtualGrid.Item(Integer.valueOf(0), null, "item0"))));
        SceneVirtualGrid.Props props = SceneVirtualGrid.Props.of(items, 3, cellW, cellH, 8, 8, 2,
                Signal.create(Boolean.TRUE), item -> { });
        SceneVirtualGrid.Result result = SceneVirtualGrid.create(rt, props);
        sceneRoot.appendChild(result.root());
        try {
            rt.flush();
            frame(engine, rt, sceneRoot);

            SceneNode cell = result.viewport().__getChildren().get(1).__getChildren().get(0)
                    .__getChildren().get(0);
            SceneNode icon = cell.__getChildren().get(0);
            SceneNode label = cell.__getChildren().get(1);
            Assert.assertEquals("默认（无声明）时标签落层 4a 回落值 12", 12, label.effectiveFontSize());
            Assert.assertEquals("字号 12 时图位高 = 64 - 8 - (12 + 2)", 42, box(icon).getHeight());
            Assert.assertEquals("标签必须声明槽内单行省略", cellW - 2 * cellPadding, label.getMaxTextWidth());
            Assert.assertEquals("标签限 1 行", 1, label.getMaxLines());
            Assert.assertTrue("标签开启省略号", label.isEllipsis());

            result.root().setFontScope(LARGE_FONT);
            rt.flush();
            frame(engine, rt, sceneRoot);
            frame(engine, rt, sceneRoot);

            Assert.assertEquals("字号 32 时标签生效字号", LARGE_FONT, label.effectiveFontSize());
            Assert.assertEquals("字号 32 时图位高必须让位 = 64 - 8 - (32 + 2)", 22, box(icon).getHeight());
            Assert.assertEquals("字号 32 时标签盒高 = 行高(32)", LARGE_FONT, box(label).getHeight());
            Assert.assertTrue("图位与标签不得重叠：图标底 + 间距 ≤ 标签顶",
                    box(icon).getY() + box(icon).getHeight() + labelGap <= box(label).getY());
            Assert.assertEquals("默认配置 200%：单元盒高保持调用方轨道（stride 不破）", cellH, box(cell).getHeight());
            Assert.assertEquals("默认配置 200%：内容恰好填满轨道（图位 + 间距 + 标签行高 + 2*内边距）",
                    cellH, box(icon).getHeight() + labelGap + box(label).getHeight() + 2 * cellPadding);
        } finally {
            rt.dispose();
        }
    }

    // ==================== ③ 遗留：SearchResultList 轨道高随字号（无虚拟化，可纳入 metric） ====================

    /**
     * SearchResultList 轨道高 = max(调用方 cellHeight, lineHeight(生效字号) + 间距 + 图位最小 + 内边距)：
     * 小轨道（24）在 16 → 32 时必须从 24 涨到 43，标签完整不被裁；大轨道（64）保持 64（200% 不裁切）。
     */
    @Test
    public void searchResultListTrackHeightGrowsWithFontAndKeepsLabelIntact() {
        FontMeasurer measurer = new FontMeasurer();
        SceneRuntime rt = new SceneRuntime(measurer);
        SceneLayoutEngine engine = new SceneLayoutEngine(measurer);
        SceneNode sceneRoot = new SceneNode();
        final int cellW = 64;
        final int cellH = 24;
        final int cellPadding = 4;
        final int labelGap = 2;
        Signal<List<SceneVirtualGrid.Item>> items = Signal.create(Collections.unmodifiableList(
                Arrays.asList(new SceneVirtualGrid.Item(Integer.valueOf(0), null, "i0"))));
        Signal<Boolean> enabled = Signal.create(Boolean.TRUE);
        Signal<Integer> highlighted = Signal.create(Integer.valueOf(-1));
        club.heiqi.uilib.ui.scene.control.search.SearchResultList.Props props =
                new club.heiqi.uilib.ui.scene.control.search.SearchResultList.Props(
                        items, 1, cellW, cellH, 8, 8, enabled, item -> { }, highlighted,
                        highlighted::set, null);
        SceneNode wrapper = new SceneNode();
        wrapper.setPreferredHeight(200);
        club.heiqi.uilib.ui.scene.control.search.SearchResultList.Result result =
                club.heiqi.uilib.ui.scene.control.search.SearchResultList.create(rt, props);
        wrapper.appendChild(result.root());
        sceneRoot.appendChild(wrapper);
        try {
            rt.flush();
            frame(engine, rt, sceneRoot);

            SceneNode row = result.viewport().__getChildren().get(0).__getChildren().get(0);
            SceneNode cell = row.__getChildren().get(0);
            SceneNode icon = cell.__getChildren().get(0);
            SceneNode label = cell.__getChildren().get(1);
            Assert.assertEquals("默认字号下轨道 = max(24, lineHeight(12)+2+1+8=23) = 24",
                    cellH, box(cell).getHeight());

            result.root().setFontScope(LARGE_FONT);
            rt.flush();
            frame(engine, rt, sceneRoot);
            frame(engine, rt, sceneRoot);

            int expectedTrack = LARGE_FONT + labelGap + 1 + 2 * cellPadding;
            Assert.assertEquals("32 号下轨道必须抬升到 lineHeight(32)+间距+图位最小+内边距",
                    expectedTrack, box(cell).getHeight());
            Assert.assertEquals("行高同样跟随轨道", expectedTrack, box(row).getHeight());
            Assert.assertEquals("标签盒高 = 行高(32)，完整不被裁", LARGE_FONT, box(label).getHeight());
            Assert.assertEquals("图位高 = 轨道 - 内边距 - 标签行高 - 间距", 1, box(icon).getHeight());
            Assert.assertTrue("图位与标签不得重叠",
                    box(icon).getY() + box(icon).getHeight() + labelGap <= box(label).getY());
        } finally {
            rt.dispose();
        }
    }

    // ==================== ④ Segmented / Tab：字号派生几何（setFontSizeMetric） ====================

    /**
     * ④ Segmented：条高 = lineHeight(生效字号) + 2*PAD（root，metric）；段宽 = 标签文本宽 + 2*PAD（SHRINK 派生）。
     *
     * <p>16 → 24 期望变化：条高 40 → 48；段宽 Day 48 → 60、Week 56 → 72、Month 64 → 84。
     * 字号声明后<b>只跑一帧布局</b>即断言（不 bridge/flush 重算 effect）——若几何仍靠手写
     * layoutDone effect，这里会读到旧值而判红。</p>
     */
    @Test
    public void segmentedGeometryFollowsFontSizeViaMetric() {
        FontMeasurer measurer = new FontMeasurer();
        SceneRuntime rt = new SceneRuntime(measurer);
        SceneLayoutEngine engine = new SceneLayoutEngine(measurer);
        SceneNode sceneRoot = new SceneNode();
        final List<String> options = Arrays.asList("Day", "Week", "Month");
        SceneSegmented.Props props = new SceneSegmented.Props(Signal.create(Integer.valueOf(0)),
                options, Signal.create(Boolean.TRUE), index -> { });
        MountHandle handle = rt.mount(sceneRoot, SceneSegmented.create(rt, props));
        try {
            rt.flush();
            engine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
            rt.__bridgeLayoutEpoch(engine.layoutEpoch());
            rt.flush();

            SceneNode root = handle.getRoot();
            int pad = SceneChromeTokens.PAD_LG;
            Assert.assertEquals("字号 16 时条高 = lineHeight(16) + 2*PAD", SMALL_FONT + 2 * pad, box(root).getHeight());
            assertSegmentWidths(root, options, SMALL_FONT, pad, "字号 16");

            handle.fontSize(METRIC_FONT);
            // 只跑一帧布局：metric 在声明失效周期内同步重算，本帧就必须是新值。
            engine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));

            Assert.assertEquals("字号 24 时条高 = 24 + 2*12 = 48", METRIC_FONT + 2 * pad, box(root).getHeight());
            assertSegmentWidths(root, options, METRIC_FONT, pad, "字号 24");
        } finally {
            handle.dispose();
            rt.dispose();
        }
    }

    /**
     * ④ Tab（fill 模式）：tabBar 高 = lineHeight(生效字号) + 2*PAD（metric）；
     * 段宽 = SHRINK(标签宽 + 2*PAD) 且不少于最小宽 72。
     *
     * <p>16 → 24 期望变化：条高 40 → 48；段宽 {72,72,72} → {72,72,84}（"Month" 撑开、短标签保持 72）。</p>
     */
    @Test
    public void tabGeometryFollowsFontSizeViaMetricWithMinWidth() {
        FontMeasurer measurer = new FontMeasurer();
        SceneRuntime rt = new SceneRuntime(measurer);
        SceneLayoutEngine engine = new SceneLayoutEngine(measurer);
        SceneNode sceneRoot = new SceneNode();
        final List<String> labels = Arrays.asList("Day", "Week", "Month");
        final List<java.util.function.Supplier<SceneNode>> panels = Arrays.asList(
                (java.util.function.Supplier<SceneNode>) (() -> new SceneNode()),
                (java.util.function.Supplier<SceneNode>) (() -> new SceneNode()),
                (java.util.function.Supplier<SceneNode>) (() -> new SceneNode()));
        SceneTab.Props props = new SceneTab.Props(Signal.create(Integer.valueOf(0)), labels, panels,
                Signal.create(Boolean.TRUE), index -> { }, true, null);
        MountHandle handle = rt.mount(sceneRoot, SceneTab.create(rt, props));
        try {
            rt.flush();
            engine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
            rt.__bridgeLayoutEpoch(engine.layoutEpoch());
            rt.flush();

            SceneNode root = handle.getRoot();
            SceneNode tabBar = root.__getChildren().get(0);
            int pad = SceneChromeTokens.PAD_LG;
            final int tabMinWidth = 72;
            Assert.assertEquals("字号 16 时 fill 模式条高 = lineHeight(16) + 2*PAD",
                    SMALL_FONT + 2 * pad, box(tabBar).getHeight());
            // 16 号下自然宽 48/56/64 全部低于最小宽 72 → 三段都被 72 兜住（等宽节奏）。
            for (int i = 0; i < 3; i++) {
                Assert.assertEquals("字号 16 时段[" + i + "] 被最小宽 72 兜住", tabMinWidth,
                        box(tabBar.__getChildren().get(i)).getWidth());
            }

            handle.fontSize(METRIC_FONT);
            engine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));

            Assert.assertEquals("字号 24 时条高 = 24 + 2*12 = 48", METRIC_FONT + 2 * pad, box(tabBar).getHeight());
            Assert.assertEquals("'Day' 自然宽 3*12+24=60 → 被最小宽 72 兜住", tabMinWidth,
                    box(tabBar.__getChildren().get(0)).getWidth());
            Assert.assertEquals("'Week' 自然宽 4*12+24=72 → 恰等于最小宽", tabMinWidth,
                    box(tabBar.__getChildren().get(1)).getWidth());
            Assert.assertEquals("'Month' 自然宽 5*12+24=84 → 超过最小宽，SHRINK 撑开", 84,
                    box(tabBar.__getChildren().get(2)).getWidth());
        } finally {
            handle.dispose();
            rt.dispose();
        }
    }

    /** 断言每段 box 宽 = 标签码点数 × 字号 / 2 + 2*PAD（SHRINK 派生口径）。 */
    private static void assertSegmentWidths(SceneNode root, List<String> options, int fontSize,
                                            int pad, String label) {
        for (int i = 0; i < options.size(); i++) {
            int expected = options.get(i).codePointCount(0, options.get(i).length()) * fontSize / 2 + 2 * pad;
            Assert.assertEquals(label + " 时段[" + i + "] '" + options.get(i) + "' box 宽",
                    expected, box(root.__getChildren().get(i)).getWidth());
        }
    }

    // ==================== 夹具与工具 ====================

    /** 缩放度量替身：宽 = 码点 × 字号 / 2，行高 = 字号（字号错误无法被固定行高掩盖）。 */
    private static final class FontMeasurer implements SceneTextMeasurer {

        @Override
        public int measureWidth(String text, int fontSizePx) {
            return text == null ? 0 : text.codePointCount(0, text.length()) * fontSizePx / 2;
        }

        @Override
        public int lineHeight(int fontSizePx) {
            return fontSizePx;
        }

        @Override
        public int epoch() {
            return 0;
        }

        /**
         * 按宽度硬拆（默认实现返回单行、不换行，会让「省略号」断言失去意义）。
         *
         * @param text       文本
         * @param fontSizePx 字号
         * @param wrapWidth  换行宽；&lt;=0 视为不换行
         * @param textMode   内容模式编码
         * @return 行列表（至少一行）
         */
        @Override
        public List<String> splitLines(String text, int fontSizePx, int wrapWidth, int textMode) {
            String value = text == null ? "" : text;
            if (wrapWidth <= 0) {
                return Arrays.asList(value.split("\n", -1));
            }
            int perLine = Math.max(1, wrapWidth / Math.max(1, fontSizePx / 2));
            List<String> lines = new ArrayList<String>();
            for (int start = 0; start < value.length(); start += perLine) {
                lines.add(value.substring(start, Math.min(value.length(), start + perLine)));
            }
            return lines.isEmpty() ? Collections.singletonList("") : lines;
        }
    }

    private static void frame(SceneLayoutEngine engine, SceneRuntime rt, SceneNode sceneRoot) {
        engine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        rt.__bridgeLayoutEpoch(engine.layoutEpoch());
        rt.flush();
    }

    private static LayoutBox box(SceneNode node) {
        Object cached = node.getCachedLayout();
        if (!(cached instanceof LayoutBox)) {
            throw new AssertionError("节点尚未布局：" + node);
        }
        return (LayoutBox) cached;
    }

    private static SceneNode findScrollable(SceneNode node) {
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

    private static SceneNode findHeaderRow(SceneNode node, int childCount) {
        for (SceneNode child : node.__getChildren()) {
            if (child.__getChildren().size() == childCount && !child.isScrollable()) {
                return child;
            }
        }
        throw new AssertionError("未找到表头行（子节点数 " + childCount + "）");
    }

    private static SceneNode findLabelTrack(SceneNode node) {
        if (node.getMaxTextWidth() == 132 && node.getMaxLines() == 1) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findLabelTrack(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
