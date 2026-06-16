package club.heiqi.uilib.ui.style;

/**
 * 样式变更对文档流水线的影响级别。
 *
 * <p>优先级从高到低：{@link #LAYOUT} &gt; {@link #PAINT} &gt; {@link #COMPOSITE}。</p>
 */
public enum UiStyleChangeImpact {
    /**
     * 需要重新布局，并隐含需要重建绘制命令。
     */
    LAYOUT,

    /**
     * 只影响绘制命令，不改变布局几何和命中几何。
     */
    PAINT,

    /**
     * 只影响合成参数（transform、opacity），不需要重建绘制命令列表，仅重新回放。
     *
     * <p>当前阶段降级处理为 {@link #PAINT}（与 PAINT 行为等同），待分级脏标记完整实现后
     * 将独立走 composite-only 回放路径，彻底避免 transform/opacity 变化重建命令。</p>
     */
    COMPOSITE
}
