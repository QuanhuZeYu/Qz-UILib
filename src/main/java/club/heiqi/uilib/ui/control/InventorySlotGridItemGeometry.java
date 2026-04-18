package club.heiqi.uilib.ui.control;

import java.util.Arrays;

/**
 * 背包格子物品重放所需的最小几何信息。
 *
 * <p>这里故意不把完整布局对象跨包传出去，
 * 只暴露物品回放真正需要的槽位原点与统一格子尺寸。</p>
 */
public final class InventorySlotGridItemGeometry {

    private final int slotSize;
    private final int[] slotLefts;
    private final int[] slotTops;

    /**
     * 创建物品重放几何数据。
     *
     * @param slotSize 统一格子尺寸
     * @param slotLefts 每个槽位的绝对左边界
     * @param slotTops 每个槽位的绝对上边界
     */
    public InventorySlotGridItemGeometry(int slotSize, int[] slotLefts, int[] slotTops) {
        if (slotLefts == null || slotTops == null) {
            throw new IllegalArgumentException("slotLefts and slotTops must not be null");
        }
        if (slotLefts.length != slotTops.length) {
            throw new IllegalArgumentException("slotLefts and slotTops must have the same length");
        }
        this.slotSize = Math.max(18, slotSize);
        this.slotLefts = Arrays.copyOf(slotLefts, slotLefts.length);
        this.slotTops = Arrays.copyOf(slotTops, slotTops.length);
    }

    /**
     * 返回槽位数量。
     *
     * @return 槽位数量
     */
    public int getSlotCount() {
        return slotLefts.length;
    }

    /**
     * 返回统一格子尺寸。
     *
     * @return 格子尺寸
     */
    public int getSlotSize() {
        return slotSize;
    }

    /**
     * 返回指定槽位的绝对左边界。
     *
     * @param slotIndex 槽位索引
     * @return 槽位左边界
     */
    public int getSlotLeft(int slotIndex) {
        return slotLefts[slotIndex];
    }

    /**
     * 返回指定槽位的绝对上边界。
     *
     * @param slotIndex 槽位索引
     * @return 槽位上边界
     */
    public int getSlotTop(int slotIndex) {
        return slotTops[slotIndex];
    }
}
