package club.heiqi.uilib.ui.inventory;

/**
 * 背包概览使用的槽位内容提供契约。
 *
 * <p>该接口属于 inventory 语义层，用于避免页面模型直接暴露具体控件的嵌套类型。</p>
 */
public interface InventoryOverviewSlotContentProvider {

    /**
     * 获取指定本地索引的槽位内容。
     *
     * @param localIndex 本地索引
     * @return 槽位快照
     */
    InventorySlotSnapshot getSlotSnapshot(int localIndex);
}
