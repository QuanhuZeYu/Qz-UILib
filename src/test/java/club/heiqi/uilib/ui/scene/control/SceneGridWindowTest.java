package club.heiqi.uilib.ui.scene.control;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.control.SceneGridWindow.RowRange;
import club.heiqi.uilib.ui.scene.control.SceneGridWindow.WindowModel;

/**
 * {@link SceneGridWindow} 纯函数窗口数学测试（8 条不变式 × 全参数扫描 + 边界用例）。
 *
 * <p>参数扫描覆盖 N=0/1/小于列数/整行/巨量、列数 1..34、可视行数 1..15、overscan 0..2、
 * stride 1/20/72/144、滚动 0/边界/超量、视口高 0/100/360/3000。任何一条不变式在任一组合下
 * 失败即视为窗口数学错误（宿主与两个控件共用本实现，错一处即全错）。</p>
 */
public class SceneGridWindowTest {

    @Test
    public void invariantsHoldAcrossParameterSweep() {
        int[] counts = {0, 1, 2, 3, 4, 5, 6, 7, 8, 17, 100, 3000, 4988};
        int[] columns = {1, 2, 3, 7, 15, 34};
        int[] visible = {1, 2, 3, 5, 15};
        int[] overscan = {0, 1, 2};
        int[] strides = {1, 20, 72, 144};
        int[] scrolls = {0, 1, 71, 72, 73, 1000000};
        int[] viewports = {0, 100, 360, 3000};
        int checked = 0;
        for (int count : counts) {
            for (int cols : columns) {
                for (int vis : visible) {
                    for (int over : overscan) {
                        for (int stride : strides) {
                            for (int scroll : scrolls) {
                                for (int viewport : viewports) {
                                    WindowModel m = SceneGridWindow.compute(count, cols, vis, over,
                                            stride, scroll, viewport);
                                    assertInvariants(m, count, cols, vis, over, stride, viewport);
                                    checked++;
                                }
                            }
                        }
                    }
                }
            }
        }
        Assert.assertTrue("参数扫描至少覆盖 10 万组", checked >= 100000);
    }

    private static void assertInvariants(WindowModel m, int count, int cols, int vis, int over,
                                         int stride, int viewport) {
        String tag = "参数 count=" + count + " cols=" + cols + " vis=" + vis + " over=" + over
                + " stride=" + stride + " scroll=" + m.windowStartRow() * stride + " viewport=" + viewport;
        int expectedTotalRows = count == 0 ? 0 : (count + cols - 1) / cols;
        // 行数/规模派生
        Assert.assertEquals(tag + " totalItems", count, m.totalItems());
        Assert.assertEquals(tag + " totalRows", expectedTotalRows, m.totalRows());
        Assert.assertEquals(tag + " columns", cols, m.columns());
        // inv1
        Assert.assertTrue(tag + " inv1 mounted≥0", m.mountedRows() >= 0);
        Assert.assertTrue(tag + " inv1 mounted≤totalRows", m.mountedRows() <= m.totalRows());
        // inv2：内容总高恒等于 totalRows*stride（spacer 数学）
        Assert.assertEquals(tag + " inv2 内容总高", m.totalRows() * stride,
                m.topSpacerPx() + m.mountedRows() * stride + m.bottomSpacerPx());
        // inv3
        Assert.assertTrue(tag + " inv3", m.windowStartRow() + m.mountedRows() <= m.totalRows());
        // inv4：挂载量与数据规模 N 无关
        Assert.assertTrue(tag + " inv4", m.mountedRows() <= m.visibleRows() + over);
        // inv5
        Assert.assertEquals(tag + " inv5", Math.min(m.visibleRows() + over,
                m.totalRows() - m.windowStartRow()), m.mountedRows());
        // inv6
        Assert.assertEquals(tag + " inv6", Math.max(0, m.totalRows() * stride - viewport),
                m.maxScrollPx());
        // inv7：可见区恒被覆盖
        Assert.assertTrue(tag + " inv7", m.windowStartRow() + Math.min(m.visibleRows(), m.totalRows())
                <= m.totalRows());
        // inv8：底部无空白
        Assert.assertTrue(tag + " inv8", m.mountedRows()
                >= Math.min(m.visibleRows(), m.totalRows() - m.windowStartRow()));
        // 行区间与规模一致，且与 windowStartRow/windowOffset 对齐
        Assert.assertEquals(tag + " rows 长度", m.mountedRows(), m.rows().size());
        Assert.assertEquals(tag + " windowOffset", m.windowStartRow() * m.columns(), m.windowOffset());
        for (int i = 0; i < m.rows().size(); i++) {
            RowRange row = m.rows().get(i);
            Assert.assertEquals(tag + " 行首", m.windowOffset() + i * m.columns(), row.firstIndex());
            Assert.assertTrue(tag + " 行项数", row.count() >= 1 && row.count() <= m.columns());
            Assert.assertTrue(tag + " 行不越界", row.endIndex() <= m.totalItems());
        }
        if (m.mountedRows() > 0) {
            RowRange last = m.rows().get(m.rows().size() - 1);
            Assert.assertTrue(tag + " 末行非空", last.count() >= 1);
        }
    }

    @Test
    public void emptyDataYieldsEmptyWindow() {
        WindowModel m = SceneGridWindow.compute(0, 4, 5, 1, 72, 0, 200);
        Assert.assertEquals(0, m.totalItems());
        Assert.assertEquals(0, m.totalRows());
        Assert.assertEquals(0, m.mountedRows());
        Assert.assertEquals(0, m.maxStartRow());
        Assert.assertEquals(0, m.maxScrollPx());
        Assert.assertEquals(0, m.windowStartRow());
        Assert.assertEquals(0, m.windowOffset());
        Assert.assertTrue(m.rows().isEmpty());
    }

    @Test
    public void fewerItemsThanOneRowMountsSingleRow() {
        WindowModel m = SceneGridWindow.compute(2, 4, 5, 1, 72, 0, 400);
        Assert.assertEquals(1, m.totalRows());
        Assert.assertEquals(1, m.mountedRows());
        Assert.assertEquals(1, m.rows().size());
        Assert.assertEquals(2, m.rows().get(0).count());
        Assert.assertEquals(0, m.maxStartRow());
        Assert.assertEquals(0, m.maxScrollPx());
    }

    @Test
    public void rowsFewerThanVisibleMountsAllRows() {
        // 8 项 / 4 列 = 2 行 < 5 可视行：全挂、不可滚
        WindowModel m = SceneGridWindow.compute(8, 4, 5, 1, 72, 0, 400);
        Assert.assertEquals(2, m.totalRows());
        Assert.assertEquals(2, m.mountedRows());
        Assert.assertEquals(0, m.maxScrollPx());
        Assert.assertEquals(0, m.bottomSpacerPx());
    }

    @Test
    public void overscanExtraRowIsMountedAndCountedInSpacers() {
        // 100 项 / 4 列 = 25 行；vis=5 over=1 → 首窗 6 行
        WindowModel m = SceneGridWindow.compute(100, 4, 5, 1, 72, 0, 360);
        Assert.assertEquals(6, m.mountedRows());
        Assert.assertEquals(0, m.topSpacerPx());
        Assert.assertEquals((25 - 6) * 72, m.bottomSpacerPx());
    }

    @Test
    public void scrollToBottomKeepsVisibleRowsCoveredAndLastItemReachable() {
        // 4988 项 / 34 列 = 147 行；滚到超量 → 夹到 maxStartRow
        WindowModel m = SceneGridWindow.compute(4988, 34, 5, 1, 72, 1000000, 360);
        Assert.assertEquals(147, m.totalRows());
        Assert.assertEquals(142, m.windowStartRow());
        Assert.assertEquals(5, m.mountedRows());
        Assert.assertTrue("末项在窗口内",
                m.rows().get(m.rows().size() - 1).endIndex() == 4988);
        Assert.assertEquals(142 * 72, m.topSpacerPx());
        Assert.assertEquals(0, m.bottomSpacerPx());
        // 内容总高守恒
        Assert.assertEquals(147 * 72,
                m.topSpacerPx() + m.mountedRows() * 72 + m.bottomSpacerPx());
    }

    @Test
    public void mountedCountIsIndependentOfDataSize() {
        for (int count : new int[]{50, 500, 5000, 50000}) {
            WindowModel m = SceneGridWindow.compute(count, 7, 5, 1, 72, 0, 360);
            Assert.assertTrue("N=" + count + " 挂载 ≤ 6 行", m.mountedRows() <= 6);
            Assert.assertEquals("N=" + count + " 总量不受窗口裁剪", count, m.totalItems());
            int mountedCells = 0;
            for (RowRange row : m.rows()) {
                mountedCells += row.count();
            }
            Assert.assertTrue("N=" + count + " 挂载单元 ≤ 6*7", mountedCells <= 42);
        }
    }

    @Test
    public void strideChangeScalesContentAndScrollBound() {
        WindowModel small = SceneGridWindow.compute(100, 4, 5, 1, 40, 0, 200);
        WindowModel large = SceneGridWindow.compute(100, 4, 5, 1, 80, 0, 200);
        Assert.assertEquals(25 * 40 - 200, small.maxScrollPx());
        Assert.assertEquals(25 * 80 - 200, large.maxScrollPx());
        Assert.assertTrue(large.maxScrollPx() > small.maxScrollPx());
    }

    @Test
    public void illegalInputsCollapseToMinimums() {
        WindowModel m = SceneGridWindow.compute(-5, 0, 0, -3, 0, -100, -1);
        Assert.assertEquals(0, m.totalItems());
        Assert.assertEquals(1, m.columns());
        Assert.assertEquals(1, m.visibleRows());
        Assert.assertEquals(0, m.mountedRows());
        Assert.assertEquals(0, m.maxScrollPx());
    }

    @Test
    public void viewportHeightFormulaMatchesGridContract() {
        Assert.assertEquals(5 * 64 + 4 * 8, SceneGridWindow.viewportHeight(5, 64, 8));
        Assert.assertEquals(64, SceneGridWindow.viewportHeight(1, 64, 8));
        // 非法输入收敛
        Assert.assertEquals(1, SceneGridWindow.viewportHeight(0, 0, -4));
    }

    @Test
    public void visibleRowsForViewportRoundsUpAndReportsUnlaidOutViewport() {
        Assert.assertEquals(0, SceneGridWindow.visibleRowsForViewport(0, 72));
        Assert.assertEquals(1, SceneGridWindow.visibleRowsForViewport(1, 72));
        Assert.assertEquals(1, SceneGridWindow.visibleRowsForViewport(72, 72));
        Assert.assertEquals(2, SceneGridWindow.visibleRowsForViewport(73, 72));
        Assert.assertEquals(5, SceneGridWindow.visibleRowsForViewport(360, 72));
        Assert.assertEquals(6, SceneGridWindow.visibleRowsForViewport(361, 72));
    }

    @Test
    public void windowStartRowForScrollFloorsAndClamps() {
        Assert.assertEquals(0, SceneGridWindow.windowStartRowForScroll(0, 72, 10));
        Assert.assertEquals(0, SceneGridWindow.windowStartRowForScroll(71, 72, 10));
        Assert.assertEquals(1, SceneGridWindow.windowStartRowForScroll(72, 72, 10));
        Assert.assertEquals(1, SceneGridWindow.windowStartRowForScroll(143, 72, 10));
        Assert.assertEquals(10, SceneGridWindow.windowStartRowForScroll(100000, 72, 10));
        Assert.assertEquals(0, SceneGridWindow.windowStartRowForScroll(-5, 0, -1));
    }

    @Test
    public void scrollForAnchorKeepsIntraRowOffsetAcrossStrideChange() {
        // 同一锚点行 + 行内偏移：stride 变大后行位置同步放大，行内偏移被夹到新 stride 内
        Assert.assertEquals(3 * 80 + 17, SceneGridWindow.scrollForAnchor(3, 17, 80));
        Assert.assertEquals(3 * 80 + 79, SceneGridWindow.scrollForAnchor(3, 200, 80));
        Assert.assertEquals(3 * 80, SceneGridWindow.scrollForAnchor(3, -5, 80));
        Assert.assertEquals(0, SceneGridWindow.scrollForAnchor(-2, 0, 0));
    }

    @Test
    public void rowRangesDeriveFromWindowOffsetNotFromLocalSlice() {
        WindowModel m = SceneGridWindow.compute(100, 4, 5, 1, 72, 5 * 72, 360);
        Assert.assertEquals(5, m.windowStartRow());
        Assert.assertEquals(20, m.windowOffset());
        List<RowRange> rows = m.rows();
        Assert.assertEquals(6, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Assert.assertEquals(20 + i * 4, rows.get(i).firstIndex());
        }
    }
}
