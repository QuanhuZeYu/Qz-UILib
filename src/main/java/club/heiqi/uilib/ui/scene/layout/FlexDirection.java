package club.heiqi.uilib.ui.scene.layout;

/**
 * flex 主轴方向。
 *
 * <p>决定容器子节点沿哪个轴依次排布：</p>
 * <ul>
 *   <li>{@link #ROW}：水平主轴，子节点从左到右排布。</li>
 *   <li>{@link #COLUMN}：垂直主轴，子节点从上到下排布（默认，兼容现有引擎垂直堆叠）。</li>
 * </ul>
 *
 * <p>默认值为 {@link #COLUMN}，保证不设置该属性时与现有引擎行为一致。</p>
 */
public enum FlexDirection {
    /** 水平主轴，子节点从左到右排布 */
    ROW,
    /** 垂直主轴，子节点从上到下排布（默认，兼容现有垂直堆叠） */
    COLUMN
}
