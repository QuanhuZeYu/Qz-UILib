package club.heiqi.uilib.font.render.software;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.GlyphRuntimeTablesView;
import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
import club.heiqi.uilib.font.layout.markdown.MarkdownTableModel;
import club.heiqi.uilib.font.render.GlyphBatchCollector;
import club.heiqi.uilib.ui.markdown.MarkdownPainter;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;

/**
 * 冷启动宽度预算契约测试。
 *
 * <p><b>锁什么</b>：{@code FontConfig.widthCacheMissBudgetPerWindow} 收紧时，表格列宽必须与
 * 无预算时逐列一致。该预算是「渲染线程每 16ms 窗口内允许的宽度测量 miss 次数」，
 * 耗尽时 {@code TextLayoutService#measureCodepointWidth} 会返回近似宽度且不写缓存
 * （{@code TextLayoutService.java} 的 budget 分支）。冷启动首帧一屏内容的不同码点数
 * 常超过预算，列宽随之被算窄、文字压过列分隔竖线；布局产物又被缓存，错误会一直留到
 * 下一次重新布局——这正是「同一次启动内第一次打开错误、第二次正确」的机制。</p>
 *
 * <p><b>判据是关系形，不写绝对像素</b>：断言「收紧预算的列宽向量 == 无预算的列宽向量」，
 * 两端在同一 JVM 内当场量出，因此与平台字体无关（{@code 踩坑记录.md} 2026-09-07 C9 条）。</p>
 *
 * <p><b>负向验证</b>：把 {@code measureCodepointWidth} 的预算耗尽分支改回固定
 * {@code getSpaceWidth()}，本测试必红（列0 宽度由无预算时的实测值塌到约一半）。</p>
 */
public class MarkdownTableColdWidthTest {

    private static final int BASE = 14;
    private static final int CONTAINER = 459;
    private static final int TIGHT_BUDGET = 8;

    /** 内容含中英文混排与标点：不同码点数刻意超过收紧后的预算。 */
    private static final String SOURCE =
            "表格：对齐与自动换行\n表格前的说明。\n\n"
            + "| 项目 | 说明 | 数量 |\n| :--- | :--- | ---: |\n"
            + "| 基础材料 | 随窗口宽度自动换行的长说明，含中文与 English words. | 128 |\n"
            + "| 使用指南 | \u0060code\u0060 与 ~~旧~~名称 | 16 |\n"
            + "| 公式 | $e = mc^2$ | 1 |\n\n"
            + "表格后的正文继续显示。\n左对齐、居中和右对齐；缩窄窗口可观察单元格换行。";

    private int savedBudget;

    @After
    public void restoreBudget() {
        FontConfig.widthCacheMissBudgetPerWindow = savedBudget;
    }

    @AfterClass
    public static void release() {
        LatexSoftwareRenderKit.resetShared();
    }

    @Test
    public void tightenedWidthBudgetMustNotShrinkTableColumns() {
        savedBudget = FontConfig.widthCacheMissBudgetPerWindow;

        FontConfig.widthCacheMissBudgetPerWindow = 0;
        LatexSoftwareRenderKit.resetShared();
        int[] unbudgeted = tableVerticals("budget=0");

        FontConfig.widthCacheMissBudgetPerWindow = TIGHT_BUDGET;
        LatexSoftwareRenderKit.resetShared();
        int[] tightened = tableVerticals("budget=" + TIGHT_BUDGET);

        Assert.assertArrayEquals("收紧宽度预算不得改变表格列宽（unbudgeted="
                + java.util.Arrays.toString(unbudgeted) + " tightened="
                + java.util.Arrays.toString(tightened) + "）", unbudgeted, tightened);
    }

    /** 渲染一次表格，返回纵向网格线的 x 坐标（列边界）。 */
    private static int[] tableVerticals(String label) {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        MarkdownStyleTable styles = MarkdownStyleTable.defaults();
        styles.setDefaultFontSizePx(BASE);
        TextStyle base = new TextStyle();
        base.setColor(0xFFF2EDF5);
        MarkdownDocument.LayoutContent content = MarkdownDocument.parse(SOURCE).toLayoutContent(styles, base);
        // 表格单元格的段流不在 getLines() 里（MarkdownTableSoftwareRenderTest 同口径分开收集），
        // 漏收会让表格字形未装配、未回填宽度缓存，本测试就测不到冷启动预算的真实影响。
        List<TextSegment> segments = new ArrayList<TextSegment>();
        for (MarkdownLayoutLine line : content.getLines()) {
            segments.addAll(line.getSegments());
        }
        for (MarkdownDocument.TableUnit unit : content.getTables()) {
            List<MarkdownTableModel.Row> rows = new ArrayList<MarkdownTableModel.Row>();
            rows.add(unit.getModel().getHeader());
            rows.addAll(unit.getModel().getRows());
            for (MarkdownTableModel.Row row : rows) {
                for (MarkdownTableModel.Cell cell : row.getCells()) {
                    segments.addAll(cell.getSegments());
                }
            }
        }
        LatexSoftwareRenderKit.assembleGlyphs(shared, segments);

        MarkdownPainter.ContentLayout layout = MarkdownPainter.layoutContent(content, shared.service, CONTAINER,
                BASE);
        GlyphRuntimeTablesView view = GlyphRuntimeTablesView.snapshot(shared.tables, shared.manager,
                shared.runtimeVersion);
        GlyphBatchCollector collector = new GlyphBatchCollector();
        for (PaintCommand command : layout.getCommands()) {
            if (command.getType() != PaintCommandType.SEGMENTS) {
                continue;
            }
            DefaultFontRendererAdapter.getInstance().renderSegmentsToCollector(command.getSegments(),
                    shared.settings, shared.service, view, (float) command.getLeft(), (float) command.getTop(),
                    false, 1.0F, (float) BASE, collector);
        }
        int height = Math.max(16, layout.getHeightPx() + 8);
        SoftwareRenderFrame frame = new SoftwareRenderFrame(CONTAINER + 64, height, 0xFF17151B);
        if (!collector.getMarkBackgroundBatch().isEmpty()) {
            frame.addBatch(collector.getMarkBackgroundBatch());
        }
        for (int index = 0; index < collector.getActivePageCount(); index++) {
            frame.addBatch(collector.getActiveBatch(index));
        }
        if (!collector.getDecorationBatch().isEmpty()) {
            frame.addBatch(collector.getDecorationBatch());
        }
        FontSoftwareRasterizer.render(frame, shared.gl);

        List<Integer> verticals = new ArrayList<Integer>();
        for (PaintCommand command : layout.getCommands()) {
            if (command.getType() == PaintCommandType.BACKGROUND
                    && command.getBottom() - command.getTop() > 50) {
                verticals.add(Integer.valueOf(command.getLeft()));
            }
        }
        Assert.assertTrue(label + " 必须识别出表格纵向网格线", verticals.size() >= 3);
        int[] result = new int[verticals.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = verticals.get(index).intValue();
        }
        return result;
    }
}
