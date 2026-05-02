package club.heiqi.uilib.ui.inventory;

/**
 * 背包格子网格的运行时物品渲染委托。
 */
public interface InventorySlotGridItemRenderer {

    /**
     * 按给定几何结果绘制全部物品图标。
     *
     * @param geometry 物品回放几何数据
     * @param slotSnapshots 槽位内容快照
     */
    void renderItems(InventorySlotGridItemGeometry geometry, InventorySlotSnapshot[] slotSnapshots);

    /**
     * 在鼠标位置绘制当前携带的物品。
     *
     * @param carriedSnapshot 当前鼠标携带物品快照
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     */
    default void renderCursorItem(InventorySlotSnapshot carriedSnapshot, int mouseX, int mouseY) {}
}
