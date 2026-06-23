package club.heiqi.uilib.ui.scene.overlay;

import club.heiqi.uilib.ui.scene.layout.AnchorRect;

/**
 * 浮层锚点解析器。
 *
 * <p>浮层左边对齐 anchor，宽度对齐 trigger，高度限制为所选展开侧的剩余空间。</p>
 */
public final class SceneAnchorResolver {

    private SceneAnchorResolver() {
    }

    /**
     * 解析 P0 浮层位置与尺寸约束。
     *
     * @param anchor 触发器在 host 坐标系中的盒子
     * @param hostWidth host 宽度，当前 P0 不做横向碰撞，仅校验非负
     * @param hostHeight host 高度，用于计算向下剩余高度
     * @return 浮层位置与尺寸约束
     */
    public static ResolvedAnchor resolveDown(AnchorRect anchor, int hostWidth, int hostHeight) {
        if (anchor == null) {
            throw new IllegalArgumentException("anchor must not be null");
        }
        if (hostWidth < 0 || hostHeight < 0) {
            throw new IllegalArgumentException("host size must be non-negative");
        }
        int y = anchor.getBottom();
        int remainingHeight = Math.max(0, hostHeight - y);
        return new ResolvedAnchor(anchor.getX(), y, anchor.getWidth(), remainingHeight);
    }

    /**
     * 自动选择向下或向上展开锚点。
     *
     * <p>比较向下剩余空间与向上剩余空间，结合内容高决定展开方向：向下放得下则向下；
     * 向下放不下但向上够则向上紧贴 trigger 上沿；两侧都不够时选择更大侧 cap 并允许滚动，
     * 相等默认向下以符合用户预期。</p>
     *
     * @param anchor        trigger 绝对盒
     * @param hostWidth     宿主宽
     * @param hostHeight    宿主高
     * @param contentHeight listbox UNCONSTRAINED 量出的完整内容高
     * @return 解析后的锚点（x/y/width/maxHeight）
     */
    public static ResolvedAnchor resolveAuto(AnchorRect anchor, int hostWidth, int hostHeight, int contentHeight) {
        if (anchor == null) {
            throw new IllegalArgumentException("anchor must not be null");
        }
        if (hostWidth < 0 || hostHeight < 0) {
            throw new IllegalArgumentException("host size must be non-negative");
        }
        int safeContentHeight = Math.max(0, contentHeight);
        int spaceBelow = Math.max(0, hostHeight - anchor.getBottom());
        int spaceAbove = Math.max(0, anchor.getY());
        int y;
        int maxHeight;
        if (safeContentHeight <= spaceBelow) {
            y = anchor.getBottom();
            maxHeight = spaceBelow;
        } else if (safeContentHeight <= spaceAbove) {
            y = anchor.getY() - safeContentHeight;
            maxHeight = spaceAbove;
        } else if (spaceBelow >= spaceAbove) {
            y = anchor.getBottom();
            maxHeight = spaceBelow;
        } else {
            y = anchor.getY() - spaceAbove;
            maxHeight = spaceAbove;
        }
        return new ResolvedAnchor(anchor.getX(), y, anchor.getWidth(), maxHeight);
    }

    /**
     * 已解析的浮层位置与尺寸约束。
     */
    public static final class ResolvedAnchor {
        private final int x;
        private final int y;
        private final int width;
        private final int maxHeight;

        private ResolvedAnchor(int x, int y, int width, int maxHeight) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.maxHeight = maxHeight;
        }

        /** @return 浮层左上角 X */
        public int getX() {
            return x;
        }

        /** @return 浮层左上角 Y */
        public int getY() {
            return y;
        }

        /** @return 浮层宽度约束 */
        public int getWidth() {
            return width;
        }

        /** @return 浮层最大高度约束 */
        public int getMaxHeight() {
            return maxHeight;
        }
    }
}
