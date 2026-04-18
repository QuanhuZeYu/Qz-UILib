package club.heiqi.uilib.ui.control;

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
}
