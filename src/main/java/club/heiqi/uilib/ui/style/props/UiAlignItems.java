package club.heiqi.uilib.ui.style.props;

/**
 * HTML-like flex 交叉轴对齐方式。
 */
public enum UiAlignItems {
    START,
    CENTER,
    END,
    STRETCH,
    /**
     * 按第一条基线对齐。
     *
     * @apiNote 当前实现等价于 {@link #START}。完整 baseline 对齐需要收集每行 flex item 的真实基线偏移，
     *          首版未承诺此能力，业务代码若依赖基线对齐应改用显式 padding 或 transform 偏移。
     *          后续版本计划在不影响现有 START/CENTER/END 行为的前提下补足真实基线对齐。
     */
    BASELINE
}
