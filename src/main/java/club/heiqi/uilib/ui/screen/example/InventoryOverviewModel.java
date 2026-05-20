package club.heiqi.uilib.ui.screen.example;

import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.inventory.InventorySlotSnapshot;

/**
 * 背包诊断页的数据与导航模型。
 */
public interface InventoryOverviewModel {

    /**
     * 获取快捷栏槽位内容提供器。
     *
     * @return 快捷栏槽位内容提供器
     */
    InventoryOverviewSlotContentProvider getHotbarSlotProvider();

    /**
     * 获取主背包槽位内容提供器。
     *
     * @return 主背包槽位内容提供器
     */
    InventoryOverviewSlotContentProvider getBackpackSlotProvider();

    /**
     * 获取快捷栏占用数量。
     *
     * @return 占用数量
     */
    int getHotbarOccupiedCount();

    /**
     * 获取主背包占用数量。
     *
     * @return 占用数量
     */
    int getBackpackOccupiedCount();

    /**
     * 获取当前热键栏选中槽位。
     *
     * @return 选中槽位本地索引；没有有效选中时返回 -1
     */
    default int getSelectedHotbarSlotIndex() {
        return -1;
    }

    /**
     * 获取当前鼠标携带物品快照。
     *
     * @return 鼠标携带物品快照
     */
    default InventorySlotSnapshot getCarriedSlotSnapshot() {
        return InventorySlotSnapshot.empty();
    }

    /**
     * 获取指定槽位 tooltip 文本。
     *
     * @param hotbar 是否快捷栏槽位
     * @param localIndex 分区内本地索引
     * @return tooltip 文本行
     */
    default List<String> getSlotTooltip(boolean hotbar, int localIndex) {
        return Collections.emptyList();
    }

    /**
     * 处理槽位点击。
     *
     * @param hotbar 是否快捷栏槽位
     * @param localIndex 分区内本地索引
     * @param button 鼠标按钮
     * @return 是否消费点击
     */
    default boolean handleSlotClick(boolean hotbar, int localIndex, int button) {
        return false;
    }

    /**
     * 返回原版背包界面。
     */
    void returnToVanillaInventory();
}
