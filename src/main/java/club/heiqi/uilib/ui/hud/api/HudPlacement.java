package club.heiqi.uilib.ui.hud.api;

import java.util.Objects;

/**
 * HUD 用户布局的锚点与偏移（单位 = UILib logical px）。
 *
 * <p>偏移描述「窗口对应角」相对「安全区对应角」的距离：左锚点量左边缘、右锚点量右边缘，
 * 上锚点量上边缘、下锚点量下边缘。{@code margin} 只属于默认放置，不叠加进本偏移——
 * 默认放置的偏移恰等于 {@link HudSpec#getMargin()}，故默认位置与历史
 * {@code SceneAnchorResolver.resolveViewport} 结果一致。</p>
 *
 * <p>候选 API（规划《聊天工具栏与HUD布局编辑》P2 最小闭环）：不可变值类型，偏移允许为
 * 任意整数（越界值由 {@link HudLayoutResolver} 在解析时收敛为有效显示位置，不写回偏好）。
 * 首版保留注册时的锚点，不随拖动自动切换锚点。P3 持久化在此之上加 schemaVersion 与序列化，
 * 不改本类型语义。</p>
 */
public final class HudPlacement {
    private final HudAnchor anchor;
    private final int offsetX;
    private final int offsetY;

    private HudPlacement(HudAnchor anchor, int offsetX, int offsetY) {
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    /** 以锚点 + 偏移创建用户布局。 */
    public static HudPlacement of(HudAnchor anchor, int offsetX, int offsetY) {
        return new HudPlacement(anchor, offsetX, offsetY);
    }

    /** 默认放置 = 锚点 + margin（与宿主默认放置数学同值）。 */
    public static HudPlacement defaultOf(HudAnchor anchor, int margin) {
        return new HudPlacement(anchor, margin, margin);
    }

    /** @return 锚点 */
    public HudAnchor getAnchor() { return anchor; }

    /** @return 相对锚定角 X 方向的偏移（logical px） */
    public int getOffsetX() { return offsetX; }

    /** @return 相对锚定角 Y 方向的偏移（logical px） */
    public int getOffsetY() { return offsetY; }

    /**
     * 按屏幕位移换算为新的偏移：左锚点量左边缘（右移 = 偏移增大），右锚点量右边缘
     * （右移 = 偏移减小）；上/下锚点同理。允许产生负值，由解析器收敛。
     *
     * @param dx 指针屏幕 X 位移（logical px）
     * @param dy 指针屏幕 Y 位移（logical px）
     * @return 平移后的放置
     */
    public HudPlacement translate(int dx, int dy) {
        boolean right = anchor == HudAnchor.TOP_RIGHT || anchor == HudAnchor.BOTTOM_RIGHT;
        boolean bottom = anchor == HudAnchor.BOTTOM_LEFT || anchor == HudAnchor.BOTTOM_RIGHT;
        return new HudPlacement(anchor, right ? offsetX - dx : offsetX + dx,
                bottom ? offsetY - dy : offsetY + dy);
    }

    /** 覆盖偏移（同锚点）。 */
    public HudPlacement withOffset(int x, int y) {
        return new HudPlacement(anchor, x, y);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HudPlacement)) return false;
        HudPlacement that = (HudPlacement) other;
        return offsetX == that.offsetX && offsetY == that.offsetY && anchor == that.anchor;
    }

    @Override
    public int hashCode() {
        return (anchor.hashCode() * 31 + offsetX) * 31 + offsetY;
    }

    @Override
    public String toString() {
        return "HudPlacement{" + anchor + ", x=" + offsetX + ", y=" + offsetY + '}';
    }
}
