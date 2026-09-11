package club.heiqi.uilib.ui.scene.control.search;

import club.heiqi.uilib.ui.scene.control.SceneVirtualGridNav;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * GridMetrics —— 结果网格几何派生链的<strong>唯一实现</strong>（轨道高 / stride / 列数）。
 *
 * <h3>派生链（P5 §2.1）</h3>
 * <pre>
 *   lineH    = rt.lineHeight(生效字号)                       // 字体服务真值
 *   trackH   = max(cellHeight 下限, lineH + labelGap + 图位最小 + 2*padding)
 *   stridePx = trackH + gapY
 *   columns  = SceneVirtualGridNav.deriveColumns(innerWidth, cellWidth, gapX)   // 复用既有公式
 * </pre>
 * <p>行高、单元高、图位高、spacer、{@code maxScrollPx}、滚动定位全部读同一份派生结果
 * （P5 I-4「单一 GridMetrics 快照」）；任何第二处 stride 推导都属漂移。</p>
 *
 * <h3>密度令牌换源点（P5 §1.4 未落地）</h3>
 * <p>P3 不得自造密度常量：{@link #STANDARD_ICON_SIDE_PX} 与 {@link #STANDARD_DENSITY_SCALE} 是
 * 「标准档」的唯一常量点。P5 令牌与 auto 求解落地时，<b>只改这两个常量与 {@link #derive} 内的
 * trackHeight 公式</b>（改用 iconSide 求解），并复跑 A-11；禁止散落多处。
 * 当前公式保持与窗口化前<b>逐值一致</b>——用户可见几何不变是 P3 的硬约束。</p>
 */
public final class GridMetrics {

    /** 标准档图标边长（P5 §1.4 令牌 {@code density.icon} 落地前的唯一常量点）。 */
    public static final int STANDARD_ICON_SIDE_PX = 40;
    /** 标准档密度倍率（P5 §1.4 令牌 {@code k} 落地前的唯一常量点）。 */
    public static final int STANDARD_DENSITY_SCALE = 1;
    /** 图位最小高：标签行高之外至少保留 1px 图位，防止字号放大时图位被标签吃掉。 */
    public static final int MIN_ICON_SIDE_PX = 1;

    private final int fontSizePx;
    private final int lineHeightPx;
    private final int paddingPx;
    private final int labelGapPx;
    private final int iconSidePx;
    private final int trackHeightPx;
    private final int gapY;
    private final int stridePx;

    private GridMetrics(int fontSizePx, int lineHeightPx, int paddingPx, int labelGapPx,
                        int iconSidePx, int trackHeightPx, int gapY, int stridePx) {
        this.fontSizePx = fontSizePx;
        this.lineHeightPx = lineHeightPx;
        this.paddingPx = paddingPx;
        this.labelGapPx = labelGapPx;
        this.iconSidePx = iconSidePx;
        this.trackHeightPx = trackHeightPx;
        this.gapY = gapY;
        this.stridePx = stridePx;
    }

    /**
     * 派生一次网格度量快照。
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
        // 现状口径：图位最小 MIN_ICON_SIDE_PX(1px) + 标签行高 + 间距 + 上下内边距（与窗口化前逐值一致）。
        int contentFloor = lineHeight + gap + MIN_ICON_SIDE_PX + 2 * pad;
        int trackHeight = Math.max(Math.max(1, cellHeightFloorPx), contentFloor);
        int rowGap = Math.max(0, gapY);
        return new GridMetrics(fs, lineHeight, pad, gap, iconSide, trackHeight, rowGap,
                trackHeight + rowGap);
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

    /** @return 生效字号 */
    public int fontSizePx() {
        return fontSizePx;
    }

    /** @return 标签行高（字体服务真值） */
    public int lineHeightPx() {
        return lineHeightPx;
    }

    /** @return 单元内边距 */
    public int paddingPx() {
        return paddingPx;
    }

    /** @return 图标与标签间距 */
    public int labelGapPx() {
        return labelGapPx;
    }

    /** @return 图位边长（P5 换源点；当前标准档 = {@value #STANDARD_ICON_SIDE_PX}px × k） */
    public int iconSidePx() {
        return iconSidePx;
    }

    /** @return 轨道高（行高/单元高/图位高的共同来源） */
    public int trackHeightPx() {
        return trackHeightPx;
    }

    /** @return 行间距 */
    public int gapY() {
        return gapY;
    }

    /** @return 行步长 = 轨道高 + 行间距（spacer/maxScrollPx/滚动定位的唯一输入） */
    public int stridePx() {
        return stridePx;
    }
}
