package club.heiqi.uilib.ui.slot;

/**
 * 通用槽位网格的纯几何布局结果。
 */
public final class SlotGridLayout {

    public final int columnCount;
    public final int rowCount;
    public final int slotSize;
    public final int totalWidth;
    public final int totalHeight;

    private final int slotGap;

    private SlotGridLayout(int columnCount, int rowCount, int slotSize, int slotGap) {
        this.columnCount = Math.max(1, columnCount);
        this.rowCount = Math.max(1, rowCount);
        this.slotSize = Math.max(18, slotSize);
        this.slotGap = Math.max(0, slotGap);
        this.totalWidth = this.columnCount * this.slotSize + Math.max(0, this.columnCount - 1) * this.slotGap;
        this.totalHeight = this.rowCount * this.slotSize + Math.max(0, this.rowCount - 1) * this.slotGap;
    }

    /**
     * 按当前约束计算布局方案。
     *
     * @param slotCount 槽位数量
     * @param slotGap 格子间距
     * @param preferredSlotSize 期望格子尺寸
     * @param minSlotSize 最小格子尺寸
     * @param maxSlotSize 最大格子尺寸
     * @param availableWidth 可用宽度
     * @return 布局结果
     */
    public static SlotGridLayout resolve(int slotCount, int slotGap, int preferredSlotSize, int minSlotSize,
            int maxSlotSize, int availableWidth) {
        int normalizedSlotCount = Math.max(0, slotCount);
        int normalizedSlotGap = Math.max(0, slotGap);
        int normalizedPreferredSlotSize = Math.max(18, preferredSlotSize);
        int normalizedMinSlotSize = Math.max(18, minSlotSize);
        int normalizedMaxSlotSize = Math.max(normalizedMinSlotSize, maxSlotSize);
        int normalizedAvailableWidth = Math.max(1, availableWidth);

        int columnCount = resolveColumnCount(normalizedSlotCount, normalizedSlotGap, normalizedPreferredSlotSize,
                normalizedMinSlotSize, normalizedAvailableWidth);
        int rowCount = Math.max(1, (normalizedSlotCount + columnCount - 1) / columnCount);
        int totalGap = Math.max(0, columnCount - 1) * normalizedSlotGap;
        int rawSlotSize = Math.max(18, (normalizedAvailableWidth - totalGap) / columnCount);
        int slotSize = Math.max(normalizedMinSlotSize, Math.min(normalizedMaxSlotSize, rawSlotSize));
        return new SlotGridLayout(columnCount, rowCount, slotSize, normalizedSlotGap);
    }

    /**
     * 按期望列数与期望尺寸解析偏好布局。
     *
     * @param slotCount 槽位数量
     * @param preferredColumns 期望列数
     * @param slotGap 格子间距
     * @param preferredSlotSize 期望格子尺寸
     * @param minSlotSize 最小格子尺寸
     * @param maxSlotSize 最大格子尺寸
     * @return 偏好布局结果
     */
    public static SlotGridLayout resolvePreferred(int slotCount, int preferredColumns, int slotGap,
            int preferredSlotSize, int minSlotSize, int maxSlotSize) {
        int normalizedSlotCount = Math.max(0, slotCount);
        int normalizedPreferredColumns = Math.max(1, preferredColumns);
        int normalizedPreferredSlotSize = Math.max(18, preferredSlotSize);
        int preferredColumnCount = Math.max(1, Math.min(normalizedSlotCount, normalizedPreferredColumns));
        int preferredWidth = preferredColumnCount * normalizedPreferredSlotSize
                + Math.max(0, preferredColumnCount - 1) * Math.max(0, slotGap);
        return resolve(slotCount, slotGap, preferredSlotSize, minSlotSize, maxSlotSize, preferredWidth);
    }

    private static int resolveColumnCount(int slotCount, int slotGap, int preferredSlotSize, int minSlotSize,
            int availableWidth) {
        if (slotCount <= 0) {
            return 1;
        }
        int fitByPreferredSize = Math.max(1, (availableWidth + slotGap) / Math.max(1, preferredSlotSize + slotGap));
        int fitByMinimumSize = Math.max(1, (availableWidth + slotGap) / Math.max(1, minSlotSize + slotGap));
        int columnCount = Math.max(1, Math.min(slotCount, fitByPreferredSize));
        return Math.min(columnCount, Math.max(1, Math.min(slotCount, fitByMinimumSize)));
    }
}
