package club.heiqi.uilib.ui.scene.layout;

/**
 * 主轴对齐方式（子节点沿主轴的分布）。
 *
 * <p>控制子节点在主轴（由 {@link FlexDirection} 决定）上的整体分布位置：</p>
 * <ul>
 *   <li>{@link #START}：子节点靠主轴起点对齐（默认，兼容现有从顶/左堆叠）。</li>
 *   <li>{@link #CENTER}：子节点整体居中。</li>
 *   <li>{@link #END}：子节点靠主轴终点对齐。</li>
 * </ul>
 *
 * <p>默认值为 {@link #START}，保证不设置该属性时与现有引擎行为一致。</p>
 */
public enum MainAxisAlign {
    /** 靠主轴起点对齐（默认，兼容现有堆叠行为） */
    START,
    /** 沿主轴整体居中 */
    CENTER,
    /** 靠主轴终点对齐 */
    END
}
