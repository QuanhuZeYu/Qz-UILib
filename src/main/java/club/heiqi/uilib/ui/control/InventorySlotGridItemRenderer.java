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

    /**
     * 兼容旧布局协议的默认桥接入口。
     *
     * <p>先让调用方切到几何快照协议，再在后续提交里移除这个桥接方法。</p>
     *
     * @param layout 网格布局结果
     * @param absoluteX 网格绝对 X
     * @param absoluteY 网格绝对 Y
     * @param slotSnapshots 槽位内容快照
     */
    default void renderItems(InventorySlotGridLayout layout, int absoluteX, int absoluteY,
            InventorySlotSnapshot[] slotSnapshots) {
        renderItems(layout.createItemGeometry(absoluteX, absoluteY, slotSnapshots == null ? 0 : slotSnapshots.length),
                slotSnapshots);
    }
}
