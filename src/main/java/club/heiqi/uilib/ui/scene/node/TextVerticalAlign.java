package club.heiqi.uilib.ui.scene.node;

/**
 * 文本在布局盒内的垂直对齐方式。
 * <p>PAINT 级属性，只影响文本绘制偏移，不影响盒尺寸。</p>
 * <p>对齐基准为字体渲染器锚点 em-box（显示高 = 字号），非 CSS 行框，
 * 详见 {@code ScenePaintEngine#calculateTextTop} 的对齐模型说明。</p>
 */
public enum TextVerticalAlign {
    /** 贴顶：textTop = paddingTop（em-box 顶贴内容区顶）。 */
    TOP,
    /** 居中（默认）：textTop = paddingTop + (innerHeight - emHeight) / 2。 */
    CENTER,
    /** 贴底：textTop = paddingTop + (innerHeight - emHeight)。 */
    BOTTOM
}
