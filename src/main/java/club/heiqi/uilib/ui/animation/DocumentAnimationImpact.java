package club.heiqi.uilib.ui.animation;

/**
 * HTML-like 动画属性对渲染流水线的影响范围。
 */
public enum DocumentAnimationImpact {
    /**
     * 只需要重建 paint command，不需要重排。
     */
    PAINT,

    /**
     * 会影响效果合成、backdrop 或 paint context 运行时 pass。
     */
    EFFECT,

    /**
     * 会影响布局几何，当前仅作为后续 layout-affecting 动画的分类预留。
     */
    LAYOUT
}
