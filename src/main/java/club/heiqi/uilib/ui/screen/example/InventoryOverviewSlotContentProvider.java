package club.heiqi.uilib.ui.screen.example;

import club.heiqi.uilib.ui.inventory.InventorySlotSnapshot;

/**
 * 背包诊断页使用的槽位内容提供契约。
 *
 * <p>该接口属于页面层，用于避免页面模型直接暴露具体控件的嵌套类型。</p>
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
