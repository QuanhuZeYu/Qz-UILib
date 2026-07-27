package club.heiqi.uilib.ui.container.experimental.minecraft;

import net.minecraft.item.ItemStack;

/** 服务端主线程上的正常物品掉落出口。 */
public interface DropEmitter {
    /** 生成已确认从 storage/cursor 移出的物品。 */
    void spawn(ItemStack stack);
}
