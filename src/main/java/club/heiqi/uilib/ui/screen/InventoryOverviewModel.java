package club.heiqi.uilib.ui.screen;

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
     * 返回原版背包界面。
     */
    void returnToVanillaInventory();
}
