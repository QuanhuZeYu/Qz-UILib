package club.heiqi.uilib.ui.control;

import net.minecraft.item.ItemStack;

/**
 * 背包网格专用的槽位快照。
 *
 * <p>页面层与控件层只通过该快照感知槽位是否占用，
 * 运行时 {@link ItemStack} 仅允许在同包渲染边界附近按需取回。</p>
 */
public final class InventorySlotSnapshot {

    private static final InventorySlotSnapshot EMPTY = new InventorySlotSnapshot(false, null);

    private final boolean occupied;
    private final ItemStack runtimeStack;

    private InventorySlotSnapshot(boolean occupied, ItemStack runtimeStack) {
        this.occupied = occupied;
        this.runtimeStack = runtimeStack;
    }

    /**
     * 创建空槽位快照。
     *
     * @return 空槽位快照
     */
    public static InventorySlotSnapshot empty() {
        return EMPTY;
    }

    /**
     * 创建仅携带占用态的槽位快照。
     *
     * <p>该工厂主要供控件边界测试或非运行时场景使用，
     * 若需要真实物品渲染，应优先使用 {@link #fromRuntimeStack(ItemStack)}。</p>
     *
     * @return 已占用槽位快照
     */
    public static InventorySlotSnapshot occupied() {
        return new InventorySlotSnapshot(true, null);
    }

    /**
     * 根据运行时物品内容创建槽位快照。
     *
     * @param runtimeStack 运行时物品内容
     * @return 对应的槽位快照
     */
    public static InventorySlotSnapshot fromRuntimeStack(ItemStack runtimeStack) {
        if (runtimeStack == null || runtimeStack.getItem() == null) {
            return EMPTY;
        }
        return new InventorySlotSnapshot(true, runtimeStack);
    }

    /**
     * 判断当前槽位是否占用。
     *
     * @return 是否占用
     */
    public boolean isOccupied() {
        return occupied;
    }

    /**
     * 获取渲染边界使用的运行时物品内容。
     *
     * <p>该方法仅供同包 renderer 使用，避免 `ItemStack` 再次扩散回页面 contract。</p>
     *
     * @return 运行时物品内容
     */
    ItemStack getRuntimeStack() {
        return runtimeStack;
    }
}
