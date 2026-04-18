package club.heiqi.uilib.ui.control;

import java.util.Objects;

import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 只读背包格子网格控件。
 */
public class InventorySlotGridWidget extends Widget {

    /**
     * 槽位内容提供器。
     */
    public interface SlotContentProvider {

        /**
         * 获取指定本地索引的槽位内容。
         *
         * @param localIndex 本地索引
         * @return 槽位快照
         */
        InventorySlotSnapshot getSlotSnapshot(int localIndex);
    }

    private final int slotCount;
    private final int preferredColumns;
    private final SlotContentProvider slotContentProvider;

    private int slotGap = 8;
    private int preferredSlotSize = 34;
    private int minSlotSize = 22;
    private int maxSlotSize = 46;
    private UiControlTheme.InventorySlotGridStyle style;
    private InventorySlotGridItemRenderer itemRenderer;

    /**
     * 创建一个只读物品格子网格。
     *
     * @param slotCount 槽位数量
     * @param preferredColumns 期望列数
     * @param slotContentProvider 槽位内容提供器
     */
    public InventorySlotGridWidget(int slotCount, int preferredColumns, UiControlTheme.InventorySlotGridStyle style,
            SlotContentProvider slotContentProvider) {
        this(slotCount, preferredColumns, style, slotContentProvider, null);
    }

    /**
     * 创建一个可选注入运行时渲染委托的只读物品格子网格。
     *
     * @param slotCount 槽位数量
     * @param preferredColumns 期望列数
     * @param style 网格样式
     * @param slotContentProvider 槽位内容提供器
     * @param itemRenderer 物品渲染委托；为空时仅绘制槽背景/边框，不绘制物品图标
     */
    public InventorySlotGridWidget(int slotCount, int preferredColumns, UiControlTheme.InventorySlotGridStyle style,
            SlotContentProvider slotContentProvider, InventorySlotGridItemRenderer itemRenderer) {
        this.slotCount = Math.max(0, slotCount);
        this.preferredColumns = Math.max(1, preferredColumns);
        this.style = Objects.requireNonNull(style, "style");
        this.slotContentProvider = Objects.requireNonNull(slotContentProvider, "slotContentProvider");
        this.itemRenderer = itemRenderer;
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        final InventorySlotGridLayout layout = resolveLayout(Math.max(1, getWidth()));
        final int absoluteX = getAbsoluteX() + Math.max(0, (getWidth() - layout.totalWidth) / 2);
        final int absoluteY = getAbsoluteY();
        final InventorySlotSnapshot[] slotSnapshots = snapshotSlotSnapshots();
        boolean hasRenderableItems = false;

        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            InventorySlotGridLayout.SlotRect slotRect = layout.getSlotRect(slotIndex);
            InventorySlotSnapshot snapshot = slotSnapshots[slotIndex];
            boolean occupied = snapshot.isOccupied();
            int fillColor = occupied ? style.occupiedSlotFillColor : style.emptySlotFillColor;
            int borderColor = occupied ? style.occupiedSlotBorderColor : style.emptySlotBorderColor;
            context.fillRect(absoluteX + slotRect.left, absoluteY + slotRect.top, absoluteX + slotRect.right,
                    absoluteY + slotRect.bottom, fillColor);
            context.drawBorder(absoluteX + slotRect.left, absoluteY + slotRect.top, absoluteX + slotRect.right,
                    absoluteY + slotRect.bottom, borderColor);
            hasRenderableItems = hasRenderableItems || occupied;
        }

        if (hasRenderableItems) {
            final InventorySlotGridItemRenderer activeItemRenderer = resolveItemRenderer();
            if (activeItemRenderer != null) {
                final InventorySlotGridItemGeometry geometry = layout.createItemGeometry(absoluteX, absoluteY,
                        slotSnapshots.length);
                context.enqueueDeferredPostMainPass(new UiRenderContext.DeferredPostMainPassReplay() {
                    @Override
                    public void replay() {
                        activeItemRenderer.renderItems(geometry, slotSnapshots);
                    }
                });
            }
        }
    }

    @Override
    public int getPreferredWidth() {
        return resolvePreferredLayout().totalWidth;
    }

    @Override
    public int getPreferredHeight() {
        return resolvePreferredLayout().totalHeight;
    }

    @Override
    public int getPreferredHeightForWidth(int width) {
        return resolveLayout(Math.max(1, width)).totalHeight;
    }

    @Override
    public int getMinContentWidth() {
        return minSlotSize;
    }

    /**
     * 设置格子间距。
     *
     * @param slotGap 间距
     * @return 当前控件
     */
    public InventorySlotGridWidget setSlotGap(int slotGap) {
        this.slotGap = Math.max(0, slotGap);
        requestLayout();
        return this;
    }

    /**
     * 设置格子尺寸上下限。
     *
     * @param minSlotSize 最小尺寸
     * @param maxSlotSize 最大尺寸
     * @return 当前控件
     */
    public InventorySlotGridWidget setSlotSizeRange(int minSlotSize, int maxSlotSize) {
        this.minSlotSize = Math.max(18, minSlotSize);
        this.maxSlotSize = Math.max(this.minSlotSize, maxSlotSize);
        requestLayout();
        return this;
    }

    /**
     * 设置期望格子尺寸，实际列数会围绕这个尺寸自动重排。
     *
     * @param preferredSlotSize 期望格子尺寸
     * @return 当前控件
     */
    public InventorySlotGridWidget setPreferredSlotSize(int preferredSlotSize) {
        this.preferredSlotSize = Math.max(18, preferredSlotSize);
        requestLayout();
        return this;
    }

    /**
     * 设置背包格子网格样式。
     *
     * @param style 网格样式；为空时恢复默认样式
     * @return 当前控件
     */
    public InventorySlotGridWidget setStyle(UiControlTheme.InventorySlotGridStyle style) {
        this.style = Objects.requireNonNull(style, "style");
        return this;
    }

    /**
     * 按当前宽度解析布局结果。
     *
     * @param availableWidth 可用宽度
     * @return 布局结果
     */
    private InventorySlotGridLayout resolveLayout(int availableWidth) {
        return InventorySlotGridLayout.resolve(slotCount, slotGap, preferredSlotSize, minSlotSize, maxSlotSize,
                availableWidth);
    }

    /**
     * 获取偏好宽度下的布局结果。
     *
     * @return 偏好布局结果
     */
    private InventorySlotGridLayout resolvePreferredLayout() {
        return InventorySlotGridLayout.resolvePreferred(slotCount, preferredColumns, slotGap, preferredSlotSize,
                minSlotSize, maxSlotSize);
    }

    /**
     * 抓取当前帧使用的槽位内容快照。
     *
     * @return 槽位内容数组
     */
    private InventorySlotSnapshot[] snapshotSlotSnapshots() {
        InventorySlotSnapshot[] slotSnapshots = new InventorySlotSnapshot[slotCount];
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            InventorySlotSnapshot snapshot = slotContentProvider.getSlotSnapshot(slotIndex);
            slotSnapshots[slotIndex] = snapshot == null ? InventorySlotSnapshot.empty() : snapshot;
        }
        return slotSnapshots;
    }

    /**
     * 获取实际使用的物品渲染委托。
     *
     * @return 物品渲染委托；为空时当前控件只绘制槽背景/边框
     */
    private InventorySlotGridItemRenderer resolveItemRenderer() {
        return itemRenderer;
    }
}
