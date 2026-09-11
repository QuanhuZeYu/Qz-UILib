package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.bsideup.jabel.Desugar;

/**
 * SceneGridWindow —— 网格窗口数学（虚拟化的唯一实现，零 UI 依赖、可单测）。
 *
 * <h3>职责</h3>
 * <p>由 {@code (itemCount, columns, visibleRows, overscanRows, stridePx, scrollPx, viewportHeightPx)}
 * 产出只读窗口快照 {@link WindowModel}：窗口首行、挂载行数、上下垫片高、最大滚动偏移与窗口行区间。
 * {@link SearchResultList} 与 {@link SceneVirtualGrid} 共用本类，禁止任何第二份窗口数学
 * （宿主不得自算 {@code windowStartRow * columns} —— 那是第二份实现）。</p>
 *
 * <h3>不变量（由 {@code SceneGridWindowTest} 全参数化守卫）</h3>
 * <ul>
 *   <li>inv1 {@code mountedRows ∈ [0, totalRows]}；</li>
 *   <li>inv2 {@code topSpacerPx + mountedRows*stridePx + bottomSpacerPx == totalRows*stridePx}
 *       （内容总高恒等于 {@code totalRows*stride} ⇒ {@code SceneGeometry.maxScrollY} 同源）；</li>
 *   <li>inv3 {@code windowStartRow + mountedRows ≤ totalRows}；</li>
 *   <li>inv4 {@code mountedRows ≤ visibleRows + overscanRows}（挂载量与数据规模 N 无关）；</li>
 *   <li>inv5 {@code mountedRows == min(visibleRows + overscanRows, totalRows - windowStartRow)}；</li>
 *   <li>inv6 {@code maxScrollPx == max(0, totalRows*stridePx - viewportHeightPx)}；</li>
 *   <li>inv7 {@code windowStartRow + min(visibleRows, totalRows) ≤ totalRows}（可见区恒被覆盖）；</li>
 *   <li>inv8 {@code mountedRows ≥ min(visibleRows, totalRows - windowStartRow)}（底部无空白）。</li>
 * </ul>
 *
 * <h3>输入容错</h3>
 * <p>窗口数学是渲染热路径：非法输入一律按「最小合法值」收敛（{@code columns<1→1}、
 * {@code visibleRows<1→1}、{@code overscanRows<0→0}、{@code stridePx<1→1}、负 {@code itemCount}/
 * {@code scrollPx}/{@code viewportHeightPx} → 0），不抛异常、不返回 null。</p>
 */
public final class SceneGridWindow {

    private SceneGridWindow() {
    }

    /**
     * 窗口行的全局下标区间：{@code [firstIndex, firstIndex + count)}。
     *
     * <p>只携带下标不携带子表：行内容由调用方从实时数据源的同一份窗口切片里按区间读取，
     * 避免每帧拷贝子表。</p>
     *
     * @param firstIndex 行首项在全局序列中的下标（≥0）
     * @param count      本行项数（≥0；末行可少于列数）
     */
    @Desugar
    public record RowRange(int firstIndex, int count) {

        /** 显式校验：负下标/负项数属调用方缺陷，立即失败。 */
        public RowRange {
            if (firstIndex < 0) {
                throw new IllegalArgumentException("firstIndex 不可为负: " + firstIndex);
            }
            if (count < 0) {
                throw new IllegalArgumentException("count 不可为负: " + count);
            }
        }

        /** @return 行尾（不含）全局下标 */
        public int endIndex() {
            return firstIndex + count;
        }
    }

    /**
     * 窗口模型（只读派生快照）。
     *
     * @param columns        生效列数（≥1）
     * @param totalItems     数据总项数（全局规模，不受窗口裁剪影响）
     * @param totalRows      总行数 {@code ceil(totalItems/columns)}
     * @param windowStartRow 窗口首行
     * @param mountedRows    实际挂载行数（含 overscan）
     * @param maxStartRow    最大窗口首行（{@code max(0, totalRows - visibleRows)}）
     * @param windowOffset   窗口切片在全局序列中的起始下标 = {@code windowStartRow * columns}
     * @param visibleRows    生效可视行数（窗口数学输入）
     * @param topSpacerPx    顶部垫片高 = {@code windowStartRow * stridePx}
     * @param bottomSpacerPx 底部垫片高 = {@code (totalRows - windowStartRow - mountedRows) * stridePx}
     * @param maxScrollPx    最大滚动偏移（与 {@code SceneGeometry.maxScrollY} 闭式同源）
     * @param rows           窗口行区间（长度 = {@code mountedRows}）
     */
    @Desugar
    public record WindowModel(
            int columns,
            int totalItems,
            int totalRows,
            int windowStartRow,
            int mountedRows,
            int maxStartRow,
            int windowOffset,
            int visibleRows,
            int topSpacerPx,
            int bottomSpacerPx,
            int maxScrollPx,
            List<RowRange> rows) {

        /** 防御性不可变：窗口模型不参与任何写路径。 */
        public WindowModel {
            rows = rows == null
                    ? Collections.<RowRange>emptyList()
                    : Collections.unmodifiableList(rows);
        }
    }

    /**
     * 计算窗口模型（唯一窗口数学实现）。
     *
     * @param itemCount         数据总项数（全局规模；&lt;0 按 0）
     * @param columns           生效列数（&lt;1 按 1）
     * @param visibleRows       生效可视行数（&lt;1 按 1）
     * @param overscanRows      额外挂载行数（&lt;0 按 0；v1 调用方传 1）
     * @param stridePx          行步长 = 轨道高 + 行间距（&lt;1 按 1）
     * @param scrollPx          当前滚动偏移（&lt;0 按 0）
     * @param viewportHeightPx  视口高度（&lt;0 按 0；未布局时为 0）
     * @return 窗口模型（非 null）
     */
    public static WindowModel compute(int itemCount, int columns, int visibleRows, int overscanRows,
                                      int stridePx, int scrollPx, int viewportHeightPx) {
        int count = Math.max(0, itemCount);
        int cols = Math.max(1, columns);
        int rows = Math.max(1, visibleRows);
        int overscan = Math.max(0, overscanRows);
        int stride = Math.max(1, stridePx);
        int scroll = Math.max(0, scrollPx);
        int viewportH = Math.max(0, viewportHeightPx);

        int totalRows = count == 0 ? 0 : (count + cols - 1) / cols;
        int maxStartRow = Math.max(0, totalRows - rows);
        int windowStartRow = Math.max(0, Math.min(maxStartRow, scroll / stride));
        int mountedRows = Math.max(0, Math.min(rows + overscan, totalRows - windowStartRow));
        int windowOffset = windowStartRow * cols;
        int topSpacerPx = windowStartRow * stride;
        int bottomSpacerPx = Math.max(0,
                (totalRows - windowStartRow - mountedRows) * stride);
        int maxScrollPx = Math.max(0, totalRows * stride - viewportH);

        List<RowRange> windowRows = new ArrayList<RowRange>(mountedRows);
        for (int i = 0; i < mountedRows; i++) {
            int firstIndex = (windowStartRow + i) * cols;
            windowRows.add(new RowRange(firstIndex, Math.min(cols, count - firstIndex)));
        }
        return new WindowModel(cols, count, totalRows, windowStartRow, mountedRows, maxStartRow,
                windowOffset, rows, topSpacerPx, bottomSpacerPx, maxScrollPx, windowRows);
    }

    /**
     * 视口高度闭式：{@code visibleRows * trackHeight + (visibleRows - 1) * gapY}。
     *
     * @param visibleRows 可视行数（&lt;1 按 1）
     * @param trackHeight 单元轨道高（&lt;1 按 1）
     * @param gapY        行间距（&lt;0 按 0）
     * @return 视口高度像素
     */
    public static int viewportHeight(int visibleRows, int trackHeight, int gapY) {
        int rows = Math.max(1, visibleRows);
        int track = Math.max(1, trackHeight);
        return rows * track + (rows - 1) * Math.max(0, gapY);
    }

    /**
     * 由视口高度与行步长反推可视行数（向上取整，至少 1 行）。
     *
     * @param viewportHeightPx 视口高度（&lt;=0 表示未布局，返回 0）
     * @param stridePx         行步长（&lt;1 按 1）
     * @return 可视行数；未布局时 0
     */
    public static int visibleRowsForViewport(int viewportHeightPx, int stridePx) {
        if (viewportHeightPx <= 0) {
            return 0;
        }
        int stride = Math.max(1, stridePx);
        return Math.max(1, (viewportHeightPx + stride - 1) / stride);
    }

    /**
     * 滚动偏移换算窗口首行（{@code floor(scroll / stride)} 夹取到 [0, maxStartRow]）。
     *
     * @param scrollPx    滚动偏移（&lt;0 按 0）
     * @param stridePx    行步长（&lt;1 按 1）
     * @param maxStartRow 最大首行（&lt;0 按 0）
     * @return 窗口首行
     */
    public static int windowStartRowForScroll(int scrollPx, int stridePx, int maxStartRow) {
        int stride = Math.max(1, stridePx);
        return Math.max(0, Math.min(Math.max(0, maxStartRow), Math.max(0, scrollPx) / stride));
    }

    /**
     * 滚动锚点重映射：保持 {@code anchorRow} 的行内偏移在 stride 变化前后不跳。
     *
     * <p>实现为 {@code anchorRow * stridePx + clamp(intraOffset, 0, stridePx - 1)}；
     * 调用方随后自行按新 {@code maxScrollPx} 回夹。仅用于 stride/列数变化时的显式重映射，
     * 不得在每帧路径调用。</p>
     *
     * @param anchorRow   锚点行（&lt;0 按 0）
     * @param intraOffset 锚点行内偏移（&lt;0 按 0；≥stride 夹到 stride-1）
     * @param stridePx    新行步长（&lt;1 按 1）
     * @return 重映射后的滚动偏移
     */
    public static int scrollForAnchor(int anchorRow, int intraOffset, int stridePx) {
        int stride = Math.max(1, stridePx);
        int intra = Math.max(0, Math.min(stride - 1, intraOffset));
        return Math.max(0, anchorRow) * stride + intra;
    }
}
