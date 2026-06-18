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
     * 只影响合成参数（transform、opacity），不改变布局几何，也不需要重建绘制命令列表。
     *
     * <p>已独立连通 composite-only 就地回放路径，不再降级为 {@link #PAINT}：transform/opacity 变更只递增
     * {@code compositeVersion}、不触碰 {@code paintVersion}，由 {@code DocumentPaintEngine.tryApplyCompositeReplay}
     * 在结构守卫通过后就地更新已缓存命令里的 TRANSFORM/PAINT_CONTEXT 值，跳过整批命令重建。仅当发生结构性
     * 翻转（transform 增删 identity、opacity 跨越 paint-context 阈值）时才回退全量重建。</p>
     */
    COMPOSITE
}
