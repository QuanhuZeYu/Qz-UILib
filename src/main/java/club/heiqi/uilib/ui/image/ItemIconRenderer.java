package club.heiqi.uilib.ui.image;

import net.minecraft.item.ItemStack;

/**
 * ItemStack 静态图标的当帧直绘宿主委托。
 *
 * <p>实现负责当帧完整绘制 icon 内容：2D 判定、原版 3D block / 多 pass 委托与 GL 状态自净，
 * 不经过 FBO 栅格化、缓存或占位。无法取得宿主渲染能力时应安全跳过而不崩溃。</p>
 */
@FunctionalInterface
public interface ItemIconRenderer {

    /**
     * 在指定正方形中当帧绘制物品图标。
     *
     * @param itemStack 与 source 内部状态隔离的 ItemStack snapshot
     * @param left 正方形左边界
     * @param top 正方形上边界
     * @param side 正方形边长
     */
    void render(ItemStack itemStack, int left, int top, int side);
}
