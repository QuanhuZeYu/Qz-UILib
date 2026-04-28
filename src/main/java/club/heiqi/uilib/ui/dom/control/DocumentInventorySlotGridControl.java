package club.heiqi.uilib.ui.dom.control;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.inventory.InventorySlotGridItemGeometry;
import club.heiqi.uilib.ui.inventory.InventorySlotGridItemRenderer;
import club.heiqi.uilib.ui.inventory.InventorySlotGridLayout;
import club.heiqi.uilib.ui.inventory.InventorySlotSnapshot;
import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;

/**
 * 基于 HTML-like 元素实现的只读背包格子网格控件适配器。
 */
public final class DocumentInventorySlotGridControl {

    /**
     * 槽位内容数据源合约。
     */
    public interface SlotContentProvider {
        InventorySlotSnapshot getSlotSnapshot(int localIndex);
    }

    private final ElementNode element;
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
        this.element = document.div();
    }


    public ElementNode getElement() {
        return element;
    }


    public DocumentInventorySlotGridControl setContentProvider(SlotContentProvider contentProvider) {
        this.contentProvider = contentProvider;
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
        return this;
    }

    private void configureElement() {
        InventorySlotGridLayout preferredLayout = InventorySlotGridLayout.resolvePreferred(slotCount, preferredColumns,
                slotGap, preferredSlotSize, minSlotSize, maxSlotSize);
        element.style()
                .setWidth(UiStyleLength.px(preferredLayout.totalWidth))
                .setHeight(UiStyleLength.px(preferredLayout.totalHeight));
        element.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {
                renderSlots(context, contentLeft, contentTop, contentRight - contentLeft);
            }
        });
    }

    private void renderSlots(UiRenderContext context, int absX, int absY, int availableWidth) {
        if (slotCount <= 0 || availableWidth <= 0) {
            return;
        }
        InventorySlotSnapshot[] snapshots = sampleSlotSnapshots();

        InventorySlotGridLayout layout = InventorySlotGridLayout.resolve(slotCount, slotGap, preferredSlotSize,
                minSlotSize, maxSlotSize, availableWidth);
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            InventorySlotGridLayout.SlotRect slotRect = layout.getSlotRect(slotIndex);
            InventorySlotSnapshot snapshot = snapshots[slotIndex];
            int fillColor = snapshot.isOccupied() ? occupiedSlotFillColor : emptySlotFillColor;
            int borderColor = snapshot.isOccupied() ? occupiedSlotBorderColor : emptySlotBorderColor;
            if (fillColor != 0) {
                context.drawSurface(absX + slotRect.left, absY + slotRect.top,
                        absX + slotRect.right, absY + slotRect.bottom,
                        new UiSurfaceStyle(fillColor, 0, 0));
            }
            if (borderColor != 0) {
                renderSlotBorder(context, absX + slotRect.left, absY + slotRect.top,
                        absX + slotRect.right, absY + slotRect.bottom, borderColor);
            }
        }

        if (itemRenderer != null && hasOccupiedSlot(snapshots)) {
            final InventorySlotGridItemGeometry geometry = layout.createItemGeometry(absX, absY, slotCount);
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

    private static void renderSlotBorder(UiRenderContext context, int left, int top, int right, int bottom,
            int borderColor) {
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            return;
        }
        context.drawSurface(left, top, right, top + 1, new UiSurfaceStyle(0, borderColor, 0));
        context.drawSurface(left, bottom - 1, right, bottom, new UiSurfaceStyle(0, borderColor, 0));
        context.drawSurface(left, top, left + 1, bottom, new UiSurfaceStyle(0, borderColor, 0));
        context.drawSurface(right - 1, top, right, bottom, new UiSurfaceStyle(0, borderColor, 0));
    }
}
