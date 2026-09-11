package club.heiqi.uilib.ui.hud.api;

import club.heiqi.uilib.ui.scene.layout.AnchorRect;

/**
 * HUD 用户布局 → 视口放置盒的唯一解析数学（宿主与打开态容器共用，禁止第二套坐标算法）。
 *
 * <p>规则（与历史 {@code SceneAnchorResolver.resolveViewport} 的默认路径同值）：</p>
 * <ul>
 *   <li>内容盒先按安全区收敛（安全区 + 偏移不重复叠加 margin）；</li>
 *   <li>锚点决定贴边方向：左/上锚点 = 安全区边 + 偏移，右/下锚点 = 视口边 - 安全区 - 偏移 - 内容尺寸；</li>
 *   <li>结果 clamp 进视口——<b>只限制有效显示位置，不写回用户偏好</b>；</li>
 *   <li>{@link #clamp} 供拖动路径把新偏移收敛进可放置区间，避免越界偏好落盘。</li>
 * </ul>
 */
public final class HudLayoutResolver {

    private HudLayoutResolver() {
    }

    /**
     * 解析放置盒（视口逻辑 px）。
     *
     * @param placement       用户布局（不可为 null）
     * @param viewportWidth   视口宽
     * @param viewportHeight  视口高
     * @param contentWidth    内容盒宽
     * @param contentHeight   内容盒高
     * @param safeInsets      安全区（不可为 null）
     * @return 已 clamp 进视口的放置盒
     */
    public static AnchorRect resolve(HudPlacement placement, int viewportWidth, int viewportHeight,
            int contentWidth, int contentHeight, HudInsets safeInsets) {
        if (placement == null) throw new IllegalArgumentException("placement must not be null");
        if (safeInsets == null) throw new IllegalArgumentException("safeInsets must not be null");
        int width = Math.max(1, viewportWidth);
        int height = Math.max(1, viewportHeight);
        int availableWidth = Math.max(1, width - safeInsets.getLeft() - safeInsets.getRight());
        int availableHeight = Math.max(1, height - safeInsets.getTop() - safeInsets.getBottom());
        int boxWidth = clampInt(contentWidth, 1, availableWidth);
        int boxHeight = clampInt(contentHeight, 1, availableHeight);
        boolean right = placement.getAnchor() == HudAnchor.TOP_RIGHT
                || placement.getAnchor() == HudAnchor.BOTTOM_RIGHT;
        boolean bottom = placement.getAnchor() == HudAnchor.BOTTOM_LEFT
                || placement.getAnchor() == HudAnchor.BOTTOM_RIGHT;
        int x = right
                ? width - safeInsets.getRight() - placement.getOffsetX() - boxWidth
                : safeInsets.getLeft() + placement.getOffsetX();
        int y = bottom
                ? height - safeInsets.getBottom() - placement.getOffsetY() - boxHeight
                : safeInsets.getTop() + placement.getOffsetY();
        x = clampInt(x, 0, Math.max(0, width - boxWidth));
        y = clampInt(y, 0, Math.max(0, height - boxHeight));
        return new AnchorRect(x, y, boxWidth, boxHeight);
    }

    /**
     * 把偏移收敛进「内容完整可见」区间（拖动路径使用）：偏移 ∈ [0, 可用尺寸 - 内容尺寸]，
     * 内容超出可用尺寸时区间塌缩为 0（只保留 clamp 后的有效位置）。
     *
     * @return 同锚点、偏移已收敛的放置
     */
    public static HudPlacement clamp(HudPlacement placement, int viewportWidth, int viewportHeight,
            int contentWidth, int contentHeight, HudInsets safeInsets) {
        if (placement == null) throw new IllegalArgumentException("placement must not be null");
        if (safeInsets == null) throw new IllegalArgumentException("safeInsets must not be null");
        int availableWidth = Math.max(1, Math.max(1, viewportWidth)
                - safeInsets.getLeft() - safeInsets.getRight());
        int availableHeight = Math.max(1, Math.max(1, viewportHeight)
                - safeInsets.getTop() - safeInsets.getBottom());
        int maxX = Math.max(0, availableWidth - Math.max(1, contentWidth));
        int maxY = Math.max(0, availableHeight - Math.max(1, contentHeight));
        return placement.withOffset(clampInt(placement.getOffsetX(), 0, maxX),
                clampInt(placement.getOffsetY(), 0, maxY));
    }

    /**
     * 该轴可拖动行程（百分比分母）：{@code max(0, 可用空间 - 内容盒)}，与 {@link #clamp} 的
     * 可行上界 {@code max(0, available - max(1, content))} 逐点相等——于是
     * {@code fraction ∈ [0,1]} 与 {@code offset ∈ [0, travelSpan]} 严格双向对应，
     * {@code fraction = 0} 贴锚点侧安全边、{@code fraction = 1} 贴对侧安全边、
     * 内容超出可用空间时行程为 0（位置自由度为零）。
     *
     * @param viewportExtent 视口该轴尺寸
     * @param contentExtent  内容该轴物理尺寸
     * @param safeLeading    安全区锚点侧插入（X 用 left、Y 用 top）
     * @param safeTrailing   安全区对侧插入（X 用 right、Y 用 bottom）
     * @return 行程（恒 ≥ 0）
     */
    static int travelSpan(int viewportExtent, int contentExtent, int safeLeading, int safeTrailing) {
        int available = Math.max(1, viewportExtent - safeLeading - safeTrailing);
        int box = clampInt(contentExtent, 1, available);
        return Math.max(0, available - box);
    }

    /**
     * 内容盒中心所在象限 → 最近角锚点（提交编辑时决定锚定方式）。
     *
     * <p>规则：{@code cx <= 安全区中线 → LEFT，否则 RIGHT}；{@code cy <= 中线 → TOP，否则 BOTTOM}。
     * 平局（中心恰在中线）取 LEFT/TOP，该规则与「四角欧氏距离最近 + 按
     * {@link HudAnchor} 声明序平局优先」完全等价（含退化视口；Python 穷举验证）。</p>
     *
     * @param box          已解析内容盒
     * @param viewportWidth  视口宽
     * @param viewportHeight 视口高
     * @param safeInsets   安全区
     * @return 最近角锚点
     */
    static HudAnchor nearestAnchor(AnchorRect box, int viewportWidth, int viewportHeight,
            HudInsets safeInsets) {
        if (box == null) throw new IllegalArgumentException("box must not be null");
        if (safeInsets == null) throw new IllegalArgumentException("safeInsets must not be null");
        int availableWidth = Math.max(1, viewportWidth - safeInsets.getLeft() - safeInsets.getRight());
        int availableHeight = Math.max(1, viewportHeight - safeInsets.getTop() - safeInsets.getBottom());
        int centerX2 = 2 * box.getX() + box.getWidth();
        int centerY2 = 2 * box.getY() + box.getHeight();
        boolean left = centerX2 <= 2 * safeInsets.getLeft() + availableWidth;
        boolean top = centerY2 <= 2 * safeInsets.getTop() + availableHeight;
        if (top) {
            return left ? HudAnchor.TOP_LEFT : HudAnchor.TOP_RIGHT;
        }
        return left ? HudAnchor.BOTTOM_LEFT : HudAnchor.BOTTOM_RIGHT;
    }

    /**
     * {@link #resolve} 的逆换算：把已解析盒转换为指定锚点下的放置（整数精确，不重复解析数学）。
     *
     * <p>与 {@link #resolve} 严格互逆：对任意已解析盒，{@code resolve(anchored(...))} 逐位等于原盒
     * （Python 穷举验证）。返回偏移为解析空间量，允许越界（调用方按 {@link #clamp} 收敛或编码百分比）。</p>
     *
     * @param anchor         目标锚点
     * @param box            已解析盒
     * @param viewportWidth  视口宽
     * @param viewportHeight 视口高
     * @param safeInsets     安全区
     * @return 目标锚点下的放置
     */
    static HudPlacement anchored(HudAnchor anchor, AnchorRect box, int viewportWidth, int viewportHeight,
            HudInsets safeInsets) {
        if (anchor == null) throw new IllegalArgumentException("anchor must not be null");
        if (box == null) throw new IllegalArgumentException("box must not be null");
        if (safeInsets == null) throw new IllegalArgumentException("safeInsets must not be null");
        boolean right = anchor == HudAnchor.TOP_RIGHT || anchor == HudAnchor.BOTTOM_RIGHT;
        boolean bottom = anchor == HudAnchor.BOTTOM_LEFT || anchor == HudAnchor.BOTTOM_RIGHT;
        int offsetX = right
                ? viewportWidth - safeInsets.getRight() - box.getX() - box.getWidth()
                : box.getX() - safeInsets.getLeft();
        int offsetY = bottom
                ? viewportHeight - safeInsets.getBottom() - box.getY() - box.getHeight()
                : box.getY() - safeInsets.getTop();
        return HudPlacement.of(anchor, offsetX, offsetY);
    }

    private static int clampInt(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
