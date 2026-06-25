package club.heiqi.uilib.ui.scene.node;

/**
 * 文本在布局盒内的垂直对齐方式。
 * <p>PAINT 级属性，只影响文本绘制偏移，不影响盒尺寸。</p>
 */
public enum TextVerticalAlign {
    /** 贴顶：textTop = paddingTop。 */
    TOP,
    /** 居中（默认）：textTop = paddingTop + max(0, (innerHeight - lineHeight) / 2)。 */
    CENTER,
    /** 贴底：textTop = paddingTop + max(0, innerHeight - lineHeight)。 */
    BOTTOM
}
