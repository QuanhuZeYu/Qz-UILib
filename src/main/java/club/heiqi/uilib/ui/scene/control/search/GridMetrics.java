package club.heiqi.uilib.ui.scene.control.search;

import club.heiqi.uilib.ui.scene.control.SceneVirtualGridNav;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * GridMetrics —— 结果网格几何派生链的<strong>唯一实现</strong>（轨道高 / stride / 列数）。
 *
 * <h3>派生链（P5 §2.1）</h3>
 * <pre>
 *   fs       = clamp(round(declaredBase * fontPct/100), FONT_FLOOR, FONT_CEIL)     // PickerMetrics
 *   pad      = clamp(round(fs / 3), 2, 6)
 *   labelGap = max(1, round(fs / 6))
 *   lineH    = rt.lineHeight(fs)                        // 字体服务真值（ascent+descent+lineGap）
 *   iconSide = max(ICON_MIN, round(density.icon * k))
 *   cellW    = max(iconSide + 2*pad, rt.measureTextWidth("MMMM", fs))   // I-3/I-5 同源度量
 *   trackH   = max(cellHeight 下限, iconSide + 2*pad + lineH + labelGap)  // I-2 标签不裁切
 *   gap      = clamp(round(fs / 2), 3, 10)
 *   stride   = trackH + gap
 *   columns  = SceneVirtualGridNav.deriveColumns(innerWidth, cellW, gap)  // 复用既有公式
 * </pre>
 * <p>行高、单元高、图位高、spacer、{@code maxScrollPx}、滚动定位全部读同一份派生结果
 * （P5 I-4「单一 GridMetrics 快照」）；任何第二处 stride 推导都属漂移。</p>
 *
 * <h3>P5 换源已完成（P3 交接 U-4 / S-07）</h3>
 * <p>P3 曾把 {@link #STANDARD_ICON_SIDE_PX} 与 {@link #STANDARD_DENSITY_SCALE} 登记为「标准档唯一常量点」
 * 并注明「P5 令牌与 auto 求解落地时只改这两个常量与 trackHeight 公式」。P5 已按此落地：
 * 正式入口是 {@link #deriveDensity}（图标边长由 {@link PickerDensity} × {@code k} 给，
 * 轨道高由图标求解而非「图位最小 1px」），两个 P3 常量降级为<b>旧调用方的兼容缺省</b>。</p>
 *
 * <h3>公式形状与 P3 的接口（P5 §2.4）</h3>
 * <p>P3 定「窗口与 stride 如何随信号动」，P5 定「cellHeight/gap/pad/icon 的取值从哪来」。
 * {@code cellHeight} 入参仍是<b>轨道高下限</b>：P5 派生下该项恒等于「图标 + 上下 padding + 标签行 + 间距」，
 * 故 {@code max} 恒取该值，P3 的公式形态不变。</p>
 *
 * <h3>取整口径</h3>
 * <p>全部比例取整走 {@link #roundHalfEven}（四舍六入五成双），与 P5 规格验算脚本
 * {@code temp/p5_density_spec.py} 的 Python {@code round()} 语义<b>逐值一致</b>——
 * 否则 fs=15 的 {@code labelGap} 会在 Java(3)/Python(2) 之间分叉，密度证明即失效。</p>
 */
public final class GridMetrics {

    /** 标准档图标边长（P3 兼容缺省；P5 正式路径由 {@link PickerDensity#iconSidePx()} 提供）。 */
    public static final int STANDARD_ICON_SIDE_PX = 40;
    /** 标准档密度倍率（P3 兼容缺省；P5 正式路径由 {@code k} 阶梯提供）。 */
    public static final int STANDARD_DENSITY_SCALE = 1;
    /** 图位最小高：旧公式（P3 前）的口径；{@link #derive} 兼容路径保留，正式路径见 {@link #deriveDensity}。 */
    public static final int MIN_ICON_SIDE_PX = 1;

    private final int fontSizePx;
    private final int lineHeightPx;
    private final int paddingPx;
    private final int labelGapPx;
    private final int iconSidePx;
    private final int cellWidthPx;
    private final int trackHeightPx;
    private final int gapX;
    private final int gapY;
    private final int stridePx;
    private final int columns;

    private GridMetrics(int fontSizePx, int lineHeightPx, int paddingPx, int labelGapPx,
                        int iconSidePx, int cellWidthPx, int trackHeightPx, int gapX, int gapY,
                        int stridePx, int columns) {
        this.fontSizePx = fontSizePx;
        this.lineHeightPx = lineHeightPx;
        this.paddingPx = paddingPx;
        this.labelGapPx = labelGapPx;
        this.iconSidePx = iconSidePx;
        this.cellWidthPx = cellWidthPx;
        this.trackHeightPx = trackHeightPx;
        this.gapX = gapX;
        this.gapY = gapY;
        this.stridePx = stridePx;
        this.columns = columns;
    }

    /**
     * 旧公式派生（P3 兼容路径，纯加法保留）：图位最小 1px + 标签行高 + 间距 + 上下内边距。
     *
     * <p>新调用方应走 {@link #deriveDensity}；本方法只保证 P3 之前登记的调用方零改动。</p>
     *
     * @param rt                场景运行时（提供行高度量；非 null）
     * @param fontSizePx        生效字号（&lt;1 按 1）
     * @param cellHeightFloorPx 调用方轨道高下限（&lt;1 按 1）
     * @param paddingPx         单元内边距（&lt;0 按 0）
     * @param labelGapPx        图标与标签间距（&lt;0 按 0）
     * @param gapY              行间距（&lt;0 按 0）
     * @return 度量快照（非 null）
     */
    public static GridMetrics derive(SceneRuntime rt, int fontSizePx, int cellHeightFloorPx,
                                     int paddingPx, int labelGapPx, int gapY) {
        int fs = Math.max(1, fontSizePx);
        int pad = Math.max(0, paddingPx);
        int gap = Math.max(0, labelGapPx);
        int lineHeight = Math.max(1, rt.lineHeight(fs));
        int iconSide = Math.max(MIN_ICON_SIDE_PX, STANDARD_ICON_SIDE_PX * STANDARD_DENSITY_SCALE);
        int contentFloor = lineHeight + gap + MIN_ICON_SIDE_PX + 2 * pad;
        int trackHeight = Math.max(Math.max(1, cellHeightFloorPx), contentFloor);
        int rowGap = Math.max(0, gapY);
        return new GridMetrics(fs, lineHeight, pad, gap, iconSide, iconSide + 2 * pad, trackHeight,
                rowGap, rowGap, trackHeight + rowGap, 1);
    }

    /**
     * 密度派生（P5 §2.1 正式入口）：字号 + 图标目标边长 + {@code k} + 可用内宽 → 完整网格快照。
     *
     * @param rt                 场景运行时（提供行高与文本宽度量；非 null）
     * @param fontSizePx         生效字号（夹取到 [1, ∞)，调用方通常已按字号域夹取）
     * @param iconTargetPx       档位图标目标边长（&lt;1 按 1）
     * @param iconScalePercent   {@code k} 的百分比形式（100 = 不缩；&lt;1 按 1）
     * @param cellHeightFloorPx  调用方轨道高下限（&lt;1 按 1）
     * @param innerWidthPx       可用内宽（&lt;=0 时列数退化为 1）
     * @return 度量快照（非 null；{@code columns} 已按 {@link #columnsFor} 派生）
     */
    public static GridMetrics deriveDensity(SceneRuntime rt, int fontSizePx, int iconTargetPx,
                                            int iconScalePercent, int cellHeightFloorPx,
                                            int innerWidthPx) {
        int fs = Math.max(1, fontSizePx);
        int pad = clamp(roundHalfEven(fs / PickerDensityTokens.CELL_PAD_DIVISOR),
                PickerDensityTokens.CELL_PAD_MIN, PickerDensityTokens.CELL_PAD_MAX);
        int labelGap = Math.max(1,
                roundHalfEven(fs / PickerDensityTokens.LABEL_GAP_DIVISOR));
        int lineHeight = Math.max(1, rt.lineHeight(fs));
        int iconSide = Math.max(PickerDensityTokens.ICON_MIN,
                roundHalfEven(Math.max(1, iconTargetPx) * iconScalePercent / 100.0));
        int measured = rt.measureTextWidth(PickerDensityTokens.CELL_WIDTH_SAMPLE, fs);
        int cellWidth = Math.max(iconSide + 2 * pad, measured);
        // I-2：轨道高恒 >= 图标 + 上下 padding + 标签行 + 间距 —— 字号放大只抬轨道高，不回缩图标。
        int contentFloor = iconSide + 2 * pad + lineHeight + labelGap;
        int trackHeight = Math.max(Math.max(1, cellHeightFloorPx), contentFloor);
        int gap = clamp(roundHalfEven(fs * PickerDensityTokens.CELL_GAP_RATIO),
                PickerDensityTokens.CELL_GAP_MIN, PickerDensityTokens.CELL_GAP_MAX);
        int columns = columnsFor(innerWidthPx, cellWidth, gap);
        return new GridMetrics(fs, lineHeight, pad, labelGap, iconSide, cellWidth, trackHeight,
                gap, gap, trackHeight + gap, columns);
    }

    /** 行步长闭式：{@code trackHeight + gapY}（&lt;1 收敛到 1）。 */
    public static int stridePxOf(int trackHeightPx, int gapY) {
        return Math.max(1, Math.max(1, trackHeightPx) + Math.max(0, gapY));
    }

    /**
     * 由可用内宽推算列数（复用 {@link SceneVirtualGridNav#deriveColumns}，不新造列数公式）。
     *
     * @param innerWidthPx 可用内宽
     * @param cellWidthPx  单元宽
     * @param gapX         列间距
     * @return 列数（≥1）
     */
    public static int columnsFor(int innerWidthPx, int cellWidthPx, int gapX) {
        return SceneVirtualGridNav.deriveColumns(innerWidthPx, cellWidthPx, Math.max(0, gapX));
    }

    /**
     * 四舍六入五成双（与 P5 验算脚本的 Python {@code round()} 同语义）。
     *
     * @param value 待取整值
     * @return 取整结果（long 范围内可安全转 int）
     */
    public static int roundHalfEven(double value) {
        return (int) Math.rint(value);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** @return 生效字号 */
    public int fontSizePx() {
        return fontSizePx;
    }

    /** @return 标签行高（字体服务真值） */
    public int lineHeightPx() {
        return lineHeightPx;
    }

    /** @return 单元内边距（P5：由字号派生） */
    public int paddingPx() {
        return paddingPx;
    }

    /** @return 图标与标签间距（P5：由字号派生） */
    public int labelGapPx() {
        return labelGapPx;
    }

    /** @return 图位边长（P5：{@code max(ICON_MIN, round(density.icon * k))}，恒为正方形） */
    public int iconSidePx() {
        return iconSidePx;
    }

    /** @return 单元宽（{@code max(iconSide + 2*pad, 实测样本宽)}） */
    public int cellWidthPx() {
        return cellWidthPx;
    }

    /** @return 轨道高（行高/单元高/图位高的共同来源） */
    public int trackHeightPx() {
        return trackHeightPx;
    }

    /** @return 列间距（P5：与行间距同源同值） */
    public int gapX() {
        return gapX;
    }

    /** @return 行间距 */
    public int gapY() {
        return gapY;
    }

    /** @return 行步长 = 轨道高 + 行间距（spacer/maxScrollPx/滚动定位的唯一输入） */
    public int stridePx() {
        return stridePx;
    }

    /** @return 由可用内宽派生的列数（≥1） */
    public int columns() {
        return columns;
    }

    /**
     * 同源改写列数：用于「列数由运行时信号驱动」的调用方（可用内宽变化即换一份快照），
     * 其余分量（字号/图标/轨道高/stride）保持不变。
     *
     * @param newColumns 新列数（&lt;1 按 1）
     * @return 仅列数不同的新快照
     */
    public GridMetrics withColumns(int newColumns) {
        int cols = Math.max(1, newColumns);
        return cols == columns ? this
                : new GridMetrics(fontSizePx, lineHeightPx, paddingPx, labelGapPx, iconSidePx,
                        cellWidthPx, trackHeightPx, gapX, gapY, stridePx, cols);
    }
}
