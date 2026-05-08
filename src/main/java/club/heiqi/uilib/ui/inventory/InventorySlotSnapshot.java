package club.heiqi.uilib.ui.inventory;

import net.minecraft.item.ItemStack;

import club.heiqi.uilib.ui.image.HostImageSource;

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
    private final String displayName;
    private final int stackSize;

    private InventorySlotSnapshot(boolean occupied, ItemStack runtimeStack) {
        this.occupied = occupied;
        this.runtimeStack = runtimeStack;
        this.displayName = runtimeStack == null ? "" : runtimeStack.getDisplayName();
        this.stackSize = runtimeStack == null ? 0 : Math.max(0, runtimeStack.stackSize);
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
     * 获取槽位物品显示名。
     *
     * @return 物品显示名；空槽或无运行时物品时为空字符串
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取槽位物品堆叠数量。
     *
     * @return 堆叠数量；空槽或无运行时物品时为 0
     */
    public int getStackSize() {
        return stackSize;
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

    /**
     * 将当前槽位快照转换为宿主图片源。
     *
     * <p>只有携带真实运行时 `ItemStack` 的快照才能转换成功；纯占位测试快照会返回 null，
     * 交由调用方决定是否走旧回退路径。</p>
     *
     * @return 对应的宿主图片源；不可转换时返回 null
     */
    public HostImageSource toHostImageSource() {
        return runtimeStack == null || runtimeStack.getItem() == null ? null : HostImageSource.itemStack(runtimeStack);
    }
}
