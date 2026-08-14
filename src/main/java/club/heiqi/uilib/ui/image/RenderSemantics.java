package club.heiqi.uilib.ui.image;

/**
 * 物品图标渲染的两种语义。
 *
 * <ul>
 * <li>{@link #VANILLA}（兼容语义）：忠实原版。整个绘制就是原版
 * {@code RenderItem.renderItemAndEffectIntoGUI} 的委托（含 matrix/zLevel/lighting 包装），
 * 渲染结束后的 GL 状态与原版调用逐位一致——保留原版留下的全部残留（blend、alpha test、
 * 当前颜色、纹理绑定、shade model 等），不做任何清理。</li>
 * <li>{@link #ISOLATED}（自净语义）：在 VANILLA 核心外面包一层通用 GL 状态 scope，
 * 入口态快照（{@code glPushAttrib(GL_ALL_ATTRIB_BITS)} + 纹理绑定 / active texture /
 * client-active texture 手动快照），绘制结束后在 {@code finally} 恢复入口态，异常路径同样恢复。</li>
 * </ul>
 *
 * <p>{@link #ISOLATED} 是默认语义。</p>
 */
public enum RenderSemantics {

    /** 忠实原版：终态与原版 {@code renderItemAndEffectIntoGUI} 一致（保留全部残留）。 */
    VANILLA,

    /** 自净：入口态快照并在 {@code finally} 恢复（含 attrib、纹理绑定、颜色、blend 等）。 */
    ISOLATED
}
