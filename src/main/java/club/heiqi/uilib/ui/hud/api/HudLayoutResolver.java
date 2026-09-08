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

    private static int clampInt(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
