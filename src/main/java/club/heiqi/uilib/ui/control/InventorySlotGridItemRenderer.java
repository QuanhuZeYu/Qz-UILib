package club.heiqi.uilib.ui.control;

/**
 * 背包格子网格的运行时物品渲染委托。
 *
 * <p>该接口提升为 public，便于文档宿主层以显式 provision 的方式向控件注入具体 renderer。</p>
 */
public interface InventorySlotGridItemRenderer {

    /**
     * 按给定布局结果绘制全部物品图标。
     *
     * @param layout 网格布局结果
     * @param absoluteX 网格绝对 X
     * @param absoluteY 网格绝对 Y
     * @param slotSnapshots 槽位内容快照
     */
    void renderItems(InventorySlotGridLayout layout, int absoluteX, int absoluteY,
            InventorySlotSnapshot[] slotSnapshots);
}
