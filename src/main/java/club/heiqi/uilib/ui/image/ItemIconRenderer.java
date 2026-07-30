package club.heiqi.uilib.ui.image;

import net.minecraft.item.ItemStack;

/**
 * ItemStack 静态图标的窄宿主渲染委托。
 *
 * <p>实现只负责 icon 内容；host render coordinator 在完整 FBO 事务外建立状态围栏。</p>
 */
@FunctionalInterface
public interface ItemIconRenderer {

    /**
     * 在指定正方形中绘制物品图标。
     *
     * @param itemStack 与 source 内部状态隔离的 ItemStack snapshot
     * @param left 正方形左边界
     * @param top 正方形上边界
     * @param side 正方形边长
     * @return 本次绘制结果；只有 publishable 结果允许进入缓存
     */
    HostImageRenderOutcome render(ItemStack itemStack, int left, int top, int side);
}
