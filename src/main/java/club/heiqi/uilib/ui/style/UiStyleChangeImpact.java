package club.heiqi.uilib.ui.style;

/**
 * 样式变更对文档流水线的影响级别。
 */
public enum UiStyleChangeImpact {
    /**
     * 需要重新布局，并隐含需要重建绘制命令。
     */
    LAYOUT,

    /**
     * 只影响绘制命令，不改变布局几何和命中几何。
     */
    PAINT
}
