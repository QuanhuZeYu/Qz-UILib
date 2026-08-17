package club.heiqi.uilib.ui.image;

import net.minecraft.item.ItemStack;

/**
 * ItemStack 静态图标的当帧直绘宿主委托。
 *
 * <p>实现负责当帧完整绘制 icon 内容，不经过 FBO 栅格化、缓存或占位。无法取得宿主渲染能力时
 * 应安全跳过而不崩溃。</p>
 *
 * <p>渲染语义见 {@link RenderSemantics}：</p>
 * <ul>
 * <li>{@link #render(ItemStack, int, int, int)} 是默认入口，语义为
 * {@link RenderSemantics#ISOLATED}：调用返回后 GL 状态必须恢复到调用前入口态
 * （含 attrib、纹理绑定、当前颜色、blend 等），异常路径同样恢复。</li>
 * <li>{@link #render(ItemStack, int, int, int, RenderSemantics)} 显式指定语义；
 * {@link RenderSemantics#VANILLA} 要求终态与原版
 * {@code RenderItem.renderItemAndEffectIntoGUI} 逐位一致（保留全部残留，不做任何清理）。</li>
 * </ul>
 *
 * <p>不支持显式语义区分的实现可忽略语义参数并始终执行其默认（ISOLATED）行为。</p>
 */
@FunctionalInterface
public interface ItemIconRenderer {

    /**
     * 以默认 {@link RenderSemantics#ISOLATED} 语义在指定正方形中当帧绘制物品图标。
     *
     * <p>调用返回后 GL 状态必须恢复到调用前入口态（含 attrib、纹理绑定、当前颜色、
     * blend 等），异常路径同样恢复。</p>
     *
     * @param itemStack 与 source 内部状态隔离的 ItemStack snapshot
     * @param left 正方形左边界
     * @param top 正方形上边界
     * @param side 正方形边长
     */
    void render(ItemStack itemStack, int left, int top, int side);

    /**
     * 以指定语义在指定正方形中当帧绘制物品图标。
     *
     * <p>默认实现忽略语义差异，统一委托 {@link #render(ItemStack, int, int, int)}：
     * 不支持显式语义区分的实现保持默认（ISOLATED）行为。支持区分的实现必须遵守——
     * {@code VANILLA}：终态与原版 {@code renderItemAndEffectIntoGUI} 逐位一致（保留全部残留）；
     * {@code ISOLATED}：恢复调用前入口态，异常路径同样恢复。</p>
     *
     * @param itemStack 与 source 内部状态隔离的 ItemStack snapshot
     * @param left 正方形左边界
     * @param top 正方形上边界
     * @param side 正方形边长
     * @param semantics 渲染语义；{@code null} 时回退默认（ISOLATED）
     */
    default void render(ItemStack itemStack, int left, int top, int side, RenderSemantics semantics) {
        render(itemStack, left, top, side);
    }
}
