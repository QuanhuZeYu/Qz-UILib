package club.heiqi.uilib.ui.scene.overlay;

import club.heiqi.uilib.ui.scene.layout.AnchorRect;

/**
 * 浮层锚点解析器。
 *
 * <p>默认浮层左边对齐 anchor、宽度对齐 trigger；显式尺寸策略可给出首选宽度、最小宽度与
 * 横向安全边距。高度限制为所选展开侧的剩余空间。</p>
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
        return resolveAuto(anchor, hostWidth, hostHeight, contentHeight, AnchoredPortalLayout.DEFAULT);
    }

    /**
     * 按尺寸策略自动选择展开方向并解析横向位置。
     *
     * @param anchor        trigger 绝对盒
     * @param hostWidth     宿主宽
     * @param hostHeight    宿主高
     * @param contentHeight 在目标宽度下量出的完整内容高
     * @param layout        锚定浮层尺寸策略，不可为 null
     * @return 解析后的锚点（x/y/width/maxHeight）
     */
    public static ResolvedAnchor resolveAuto(AnchorRect anchor, int hostWidth, int hostHeight, int contentHeight,
                                             AnchoredPortalLayout layout) {
        if (anchor == null) {
            throw new IllegalArgumentException("anchor must not be null");
        }
        if (layout == null) {
            throw new IllegalArgumentException("layout must not be null");
        }
        if (hostWidth < 0 || hostHeight < 0) {
            throw new IllegalArgumentException("host size must be non-negative");
        }
        int width = resolveWidth(anchor, hostWidth, layout);
        int x = resolveX(anchor, hostWidth, width, layout);
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
        return new ResolvedAnchor(x, y, width, maxHeight);
    }

    /**
     * 在内容测量前解析目标宽度。
     *
     * @param anchor trigger 绝对盒
     * @param hostWidth 宿主宽
     * @param layout 尺寸策略
     * @return 首遍内容测量应使用的确定宽度
     */
    public static int resolveWidth(AnchorRect anchor, int hostWidth, AnchoredPortalLayout layout) {
        if (anchor == null || layout == null) {
            throw new IllegalArgumentException("anchor/layout must not be null");
        }
        if (hostWidth < 0) {
            throw new IllegalArgumentException("host width must be non-negative");
        }
        if (layout.isTriggerWidth()) {
            return anchor.getWidth();
        }
        int effectiveInset = Math.min(layout.getSafeInset(), hostWidth / 2);
        int availableWidth = Math.max(0, hostWidth - effectiveInset * 2);
        if (availableWidth < layout.getMinWidth()) {
            return availableWidth;
        }
        return Math.min(layout.getPreferredWidth(), availableWidth);
    }

    /** 解析策略浮层的横向位置，默认策略保持旧左对齐行为。 */
    private static int resolveX(AnchorRect anchor, int hostWidth, int width, AnchoredPortalLayout layout) {
        if (layout.isTriggerWidth()) {
            return anchor.getX();
        }
        int effectiveInset = Math.min(layout.getSafeInset(), hostWidth / 2);
        int maxX = Math.max(effectiveInset, hostWidth - effectiveInset - width);
        return Math.max(effectiveInset, Math.min(anchor.getX(), maxX));
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
