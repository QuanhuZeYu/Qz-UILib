package club.heiqi.uilib.ui.layout;

/**
 * HTML-like 子盒在父 stacking context 中的绘制阶段。
 */
public enum DocumentStackingPhase {
    /**
     * 显式负 z-index 的 positioned 子盒，位于普通流内容下方。
     */
    NEGATIVE_POSITIONED,

    /**
     * 普通流子盒，包含未定位元素和忽略 z-index 的 static 元素。
     */
    NORMAL_FLOW,

    /**
     * positioned 但 z-index 为 auto 或 0 的子盒，位于普通流内容上方。
     */
    POSITIONED_AUTO_OR_ZERO,

    /**
     * 显式正 z-index 的 positioned 子盒。
     */
    POSITIVE_POSITIONED
}
