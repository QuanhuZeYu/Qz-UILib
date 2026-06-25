package club.heiqi.uilib.ui.scene.node;

/**
 * 文本在布局盒内的水平对齐方式。
 * <p>PAINT 级属性，只影响文本绘制偏移，不影响盒尺寸。</p>
 */
public enum TextHorizontalAlign {
    /** 贴左（默认）：textLeft = paddingLeft。 */
    LEFT,
    /** 居中：textLeft = paddingLeft + max(0, (innerWidth - textWidth) / 2)。 */
    CENTER,
    /** 贴右：textLeft = paddingLeft + max(0, innerWidth - textWidth)。 */
    RIGHT
}
