package club.heiqi.uilib.ui.inventory;

import club.heiqi.uilib.ui.slot.SlotGridLayout;

/**
 * 背包格子网格的纯几何布局结果。
 */
public final class InventorySlotGridLayout {

    private static final int DEFAULT_ITEM_ICON_SIZE = 16;

    public final int columnCount;
    public final int rowCount;
    public final int slotSize;
    public final int totalWidth;
    public final int totalHeight;

    private final int slotGap;

    /**
     * 创建布局结果。
     *
     * @param columnCount 列数
     * @param rowCount 行数
     * @param slotSize 格子尺寸
     * @param slotGap 格子间距
     */
    private InventorySlotGridLayout(int columnCount, int rowCount, int slotSize, int slotGap) {
        this.columnCount = Math.max(1, columnCount);
        this.rowCount = Math.max(1, rowCount);
        this.slotSize = Math.max(18, slotSize);
        this.slotGap = Math.max(0, slotGap);
        this.totalWidth = this.columnCount * this.slotSize + Math.max(0, this.columnCount - 1) * this.slotGap;
        this.totalHeight = this.rowCount * this.slotSize + Math.max(0, this.rowCount - 1) * this.slotGap;
    }

    /**
     * 按当前约束计算一份布局方案。
     *
     * @param slotCount 槽位数量
     * @param slotGap 格子间距
     * @param preferredSlotSize 期望格子尺寸
     * @param minSlotSize 最小格子尺寸
     * @param maxSlotSize 最大格子尺寸
     * @param availableWidth 可用宽度
     * @return 布局结果
     */
    public static InventorySlotGridLayout resolve(int slotCount, int slotGap, int preferredSlotSize, int minSlotSize,
            int maxSlotSize, int availableWidth) {
        SlotGridLayout layout = SlotGridLayout.resolve(slotCount, slotGap, preferredSlotSize, minSlotSize,
                maxSlotSize, availableWidth);
        return new InventorySlotGridLayout(layout.columnCount, layout.rowCount, layout.slotSize, slotGap);
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
    public static InventorySlotGridLayout resolvePreferred(int slotCount, int preferredColumns, int slotGap,
            int preferredSlotSize, int minSlotSize, int maxSlotSize) {
        SlotGridLayout layout = SlotGridLayout.resolvePreferred(slotCount, preferredColumns, slotGap,
                preferredSlotSize, minSlotSize, maxSlotSize);
        return new InventorySlotGridLayout(layout.columnCount, layout.rowCount, layout.slotSize, slotGap);
    }

    /**
     * 获取指定槽位的矩形。
     *
     * @param slotIndex 槽位索引
     * @return 相对网格原点的槽位矩形
     */
    public SlotRect getSlotRect(int slotIndex) {
        int normalizedSlotIndex = Math.max(0, slotIndex);
        int column = normalizedSlotIndex % columnCount;
        int row = normalizedSlotIndex / columnCount;
        int left = column * (slotSize + slotGap);
        int top = row * (slotSize + slotGap);
        return new SlotRect(left, top, left + slotSize, top + slotSize);
    }

    /**
     * 获取默认 16x16 物品图标的绘制原点。
     *
     * @param slotIndex 槽位索引
     * @return 图标绘制原点
     */
    ItemIconOrigin getItemIconOrigin(int slotIndex) {
        return getItemIconOrigin(slotIndex, DEFAULT_ITEM_ICON_SIZE);
    }

    /**
     * 获取指定图标尺寸的绘制原点。
     *
     * @param slotIndex 槽位索引
     * @param itemIconSize 图标尺寸
     * @return 图标绘制原点
     */
    ItemIconOrigin getItemIconOrigin(int slotIndex, int itemIconSize) {
        SlotRect slotRect = getSlotRect(slotIndex);
        int normalizedItemIconSize = Math.max(0, itemIconSize);
        int itemX = slotRect.left + Math.max(0, (slotSize - normalizedItemIconSize) / 2);
        int itemY = slotRect.top + Math.max(0, (slotSize - normalizedItemIconSize) / 2);
        return new ItemIconOrigin(itemX, itemY);
    }

    /**
     * 生成物品重放阶段需要的最小槽位几何数据。
     *
     * @param absoluteX 网格绝对 X
     * @param absoluteY 网格绝对 Y
     * @param slotCount 槽位数量
     * @return 物品重放几何数据
     */
    public InventorySlotGridItemGeometry createItemGeometry(int absoluteX, int absoluteY, int slotCount) {
        int normalizedSlotCount = Math.max(0, slotCount);
        int[] slotLefts = new int[normalizedSlotCount];
        int[] slotTops = new int[normalizedSlotCount];
        for (int slotIndex = 0; slotIndex < normalizedSlotCount; slotIndex++) {
            SlotRect slotRect = getSlotRect(slotIndex);
            slotLefts[slotIndex] = absoluteX + slotRect.left;
            slotTops[slotIndex] = absoluteY + slotRect.top;
        }
        return new InventorySlotGridItemGeometry(slotSize, slotLefts, slotTops);
    }

    /**
     * 槽位矩形。
     */
    public static final class SlotRect {

        public final int left;
        public final int top;
        public final int right;
        public final int bottom;

        /**
         * 创建槽位矩形。
         *
         * @param left 左边界
         * @param top 上边界
         * @param right 右边界
         * @param bottom 下边界
         */
        public SlotRect(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    /**
     * 物品图标原点。
     */
    static final class ItemIconOrigin {

        final int x;
        final int y;

        /**
         * 创建物品图标原点。
         *
         * @param x X 坐标
         * @param y Y 坐标
         */
        ItemIconOrigin(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
