package club.heiqi.uilib.ui.scene.layout;

/**
 * 交叉轴对齐方式（子节点沿交叉轴的对齐）。
 *
 * <p>控制子节点在交叉轴（与 {@link FlexDirection} 主轴垂直的方向）上的对齐方式：</p>
 * <ul>
 *   <li>{@link #START}：子节点靠交叉轴起点对齐。</li>
 *   <li>{@link #CENTER}：子节点沿交叉轴居中。</li>
 *   <li>{@link #END}：子节点靠交叉轴终点对齐。</li>
 *   <li>{@link #STRETCH}：子节点沿交叉轴拉伸填满（默认，兼容现有子节点宽度填满父宽）。</li>
 * </ul>
 *
 * <p>默认值为 {@link #STRETCH}，保证不设置该属性时与现有引擎行为一致
 * （现引擎让子节点宽度填满父宽）。</p>
 */
public enum CrossAxisAlign {
    /** 靠交叉轴起点对齐 */
    START,
    /** 沿交叉轴居中 */
    CENTER,
    /** 靠交叉轴终点对齐 */
    END,
    /** 沿交叉轴拉伸填满（默认，兼容现有子节点填满父宽行为） */
    STRETCH
}
