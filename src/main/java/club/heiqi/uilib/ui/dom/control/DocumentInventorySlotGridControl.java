package club.heiqi.uilib.ui.dom.control;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.inventory.InventorySlotGridItemGeometry;
import club.heiqi.uilib.ui.inventory.InventorySlotGridItemRenderer;
import club.heiqi.uilib.ui.inventory.InventorySlotGridLayout;
import club.heiqi.uilib.ui.inventory.InventorySlotSnapshot;
import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * 基于 HTML-like 元素实现的只读背包格子网格控件适配器。
 */
public final class DocumentInventorySlotGridControl {

    private static final int SLOT_BORDER_WIDTH = 1;

    /**
     * 槽位内容数据源合约。
     */
    public interface SlotContentProvider {
        InventorySlotSnapshot getSlotSnapshot(int localIndex);
    }

    private final ElementNode element;
    private final DocumentTableControl tableControl;
    private final List<ElementNode> slotElements = new ArrayList<ElementNode>();
    private final int slotCount;
    private final int preferredColumns;
    private SlotContentProvider contentProvider;
    private InventorySlotGridItemRenderer itemRenderer;
    private int slotGap = 8;
    private int preferredSlotSize = 34;
    private int minSlotSize = 22;
    private int maxSlotSize = 46;
    private int emptySlotFillColor = 0xAA171C24;
    private int emptySlotBorderColor = 0xFF465468;
    private int occupiedSlotFillColor = 0xCC202A38;
    private int occupiedSlotBorderColor = 0xFF9AB8F2;
    private InventorySlotGridLayout currentLayout;
    private boolean layoutDirty = true;

    /**
     * 创建背包格子网格控件。
     *
     * @param document 所属 HTML-like 文档
     * @param slotCount 槽位数量
     * @param preferredColumns 期望列数
     */
    public DocumentInventorySlotGridControl(UiDocument document, int slotCount, int preferredColumns) {
        this.slotCount = Math.max(0, slotCount);
        this.preferredColumns = Math.max(1, preferredColumns);
        this.tableControl = new DocumentTableControl(document)
                .setCellPadding(0)
                .setBorderWidth(SLOT_BORDER_WIDTH);
        this.element = tableControl.getElement();
    }


    public ElementNode getElement() {
        return element;
    }


    public DocumentInventorySlotGridControl setContentProvider(SlotContentProvider contentProvider) {
        this.contentProvider = contentProvider;
        refreshSlotStates();
        return this;
    }


    public DocumentInventorySlotGridControl setItemRenderer(InventorySlotGridItemRenderer itemRenderer) {
        this.itemRenderer = itemRenderer;
        return this;
    }


    public DocumentInventorySlotGridControl setSlotGap(int slotGap) {
        this.slotGap = Math.max(0, slotGap);
        layoutDirty = true;
        return this;
    }


    public DocumentInventorySlotGridControl setPreferredSlotSize(int preferredSlotSize) {
        this.preferredSlotSize = Math.max(18, preferredSlotSize);
        layoutDirty = true;
        return this;
    }

    public DocumentInventorySlotGridControl setSlotSizeRange(int minSlotSize, int maxSlotSize) {
        this.minSlotSize = Math.max(18, minSlotSize);
        this.maxSlotSize = Math.max(this.minSlotSize, maxSlotSize);
        layoutDirty = true;
        return this;
    }


    public DocumentInventorySlotGridControl setSlotColors(int emptySlotFillColor, int emptySlotBorderColor,
            int occupiedSlotFillColor, int occupiedSlotBorderColor) {
        this.emptySlotFillColor = emptySlotFillColor;
        this.emptySlotBorderColor = emptySlotBorderColor;
        this.occupiedSlotFillColor = occupiedSlotFillColor;
        this.occupiedSlotBorderColor = occupiedSlotBorderColor;
        refreshSlotStates();
        return this;
    }

    /**
     * 提交尚未生效的布局配置，将 slot 参数映射为元素样式。
     *
     * @return 当前控件
     */
    public DocumentInventorySlotGridControl commitLayout() {
        if (layoutDirty) {
            configureElement();
            layoutDirty = false;
        }
        refreshSlotStates();
        return this;
    }

    /**
     * 刷新槽位 DOM 表面的占用状态样式。
     *
     * @return 当前控件
     */
    public DocumentInventorySlotGridControl refreshSlotStates() {
        if (slotElements.isEmpty()) {
            return this;
        }
        InventorySlotSnapshot[] snapshots = sampleSlotSnapshots();
        for (int slotIndex = 0; slotIndex < slotElements.size(); slotIndex++) {
            InventorySlotSnapshot snapshot = slotIndex < snapshots.length
                    ? snapshots[slotIndex] : InventorySlotSnapshot.empty();
            applySlotStyle(slotElements.get(slotIndex), snapshot.isOccupied());
        }
        return this;
    }

    private void configureElement() {
        currentLayout = InventorySlotGridLayout.resolvePreferred(slotCount, preferredColumns,
                slotGap, preferredSlotSize, minSlotSize, maxSlotSize);
        tableControl.setCellGap(slotGap, slotGap);
        element.style()
                .setWidth(UiStyleLength.px(currentLayout.totalWidth))
                .setHeight(UiStyleLength.px(currentLayout.totalHeight));
        rebuildSlotElements();
        element.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {
                renderItems(context, contentLeft, contentTop);
            }
        });
    }

    private void rebuildSlotElements() {
        slotElements.clear();
        tableControl.clearRows();
        if (slotCount <= 0 || currentLayout == null) {
            return;
        }

        int slotIndex = 0;
        for (int rowIndex = 0; rowIndex < currentLayout.rowCount && slotIndex < slotCount; rowIndex++) {
            int rowSlotCount = Math.min(currentLayout.columnCount, slotCount - slotIndex);
            List<ElementNode> rowCells = tableControl.addEmptyRow(rowSlotCount);
            for (int columnIndex = 0; columnIndex < rowCells.size(); columnIndex++) {
                ElementNode slotElement = rowCells.get(columnIndex);
                int slotContentSize = resolveSlotContentSize();
                slotElement.style()
                        .setWidth(UiStyleLength.px(slotContentSize))
                        .setHeight(UiStyleLength.px(slotContentSize))
                        .setBorderWidth(UiStyleLength.px(SLOT_BORDER_WIDTH))
                        .setOverflowX(UiOverflow.HIDDEN)
                        .setOverflowY(UiOverflow.HIDDEN);
                slotElements.add(slotElement);
                slotIndex++;
            }
        }
    }

    private int resolveSlotContentSize() {
        int slotSize = currentLayout == null ? preferredSlotSize : currentLayout.slotSize;
        return Math.max(0, slotSize - SLOT_BORDER_WIDTH * 2);
    }

    private void applySlotStyle(ElementNode slotElement, boolean occupied) {
        slotElement.style()
                .setBackgroundColor(occupied ? occupiedSlotFillColor : emptySlotFillColor)
                .setBorderColor(occupied ? occupiedSlotBorderColor : emptySlotBorderColor);
    }

    private void renderItems(UiRenderContext context, int absX, int absY) {
        if (slotCount <= 0 || currentLayout == null) {
            return;
        }
        InventorySlotSnapshot[] snapshots = sampleSlotSnapshots();

        if (itemRenderer != null && hasOccupiedSlot(snapshots)) {
            final InventorySlotGridItemGeometry geometry = currentLayout.createItemGeometry(absX, absY, slotCount);
            final InventorySlotSnapshot[] capturedSnapshots = snapshots;
            final InventorySlotGridItemRenderer capturedRenderer = itemRenderer;
            context.enqueueDeferredPostMainPass(new UiRenderContext.DeferredPostMainPassReplay() {
                @Override
                public void replay() {
                    capturedRenderer.renderItems(geometry, capturedSnapshots);
                }
            });
        }
    }

    private InventorySlotSnapshot[] sampleSlotSnapshots() {
        InventorySlotSnapshot[] snapshots = new InventorySlotSnapshot[slotCount];
        if (contentProvider != null) {
            for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                InventorySlotSnapshot snapshot = contentProvider.getSlotSnapshot(slotIndex);
                snapshots[slotIndex] = snapshot != null ? snapshot : InventorySlotSnapshot.empty();
            }
        } else {
            for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                snapshots[slotIndex] = InventorySlotSnapshot.empty();
            }
        }
        return snapshots;
    }

    private static boolean hasOccupiedSlot(InventorySlotSnapshot[] snapshots) {
        for (InventorySlotSnapshot snapshot : snapshots) {
            if (snapshot.isOccupied()) {
                return true;
            }
        }
        return false;
    }
}
