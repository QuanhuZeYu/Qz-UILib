package club.heiqi.uilib.ui.render;

import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;

/**
 * 圆角裁剪区域快照。
 *
 * <p>记录一个带圆角的矩形裁剪区域，供裁剪栈和延迟回放阶段复用。</p>
 */
public final class RoundedClipRegion {

    private final int left;
    private final int top;
    private final int right;
    private final int bottom;
    private final UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii;

    RoundedClipRegion(int left, int top, int right, int bottom,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.cornerRadii = cornerRadii;
    }

    public int getLeft() {
        return left;
    }

    public int getTop() {
        return top;
    }

    public int getRight() {
        return right;
    }

    public int getBottom() {
        return bottom;
    }

    public UiBorderRadiusResolver.ResolvedCornerRadii getCornerRadii() {
        return cornerRadii;
    }
}
