package club.heiqi.uilib.ui.dom.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.dom.DocumentElementActiveEvent;
import club.heiqi.uilib.ui.dom.DocumentElementActiveHandler;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
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

    /**
     * 槽位点击处理器。
     */
    public interface SlotClickHandler {
        boolean onSlotClick(int localIndex, int button, long timeNanos);
    }

    /**
     * 槽位 tooltip 内容提供器。
     */
    public interface SlotTooltipProvider {
        List<String> getSlotTooltip(int localIndex);
    }

    private final ElementNode element;
    private final DocumentTableControl tableControl;
    private final List<ElementNode> slotElements = new ArrayList<ElementNode>();
    private final int slotCount;
    private final int preferredColumns;
    private SlotContentProvider contentProvider;
    private SlotClickHandler slotClickHandler;
    private SlotTooltipProvider slotTooltipProvider;
    private InventorySlotGridItemRenderer itemRenderer;
    private int slotGap = 8;
    private int preferredSlotSize = 34;
    private int minSlotSize = 22;
    private int maxSlotSize = 46;
    private int emptySlotFillColor = 0xAA171C24;
    private int emptySlotBorderColor = 0xFF465468;
    private int occupiedSlotFillColor = 0xCC202A38;
    private int occupiedSlotBorderColor = 0xFF9AB8F2;
    private int hoveredSlotFillColor = 0xDD263349;
    private int hoveredSlotBorderColor = 0xFFE6F0FF;
    private int selectedSlotFillColor = 0xDD273B20;
    private int selectedSlotBorderColor = 0xFFFFD166;
    private int activeSlotFillColor = 0xEE334155;
    private int activeSlotBorderColor = 0xFFFFFFFF;
    private InventorySlotGridLayout currentLayout;
    private InventorySlotSnapshot carriedSnapshot = InventorySlotSnapshot.empty();
    private List<String> visibleTooltipLines = Collections.emptyList();
    private int selectedSlotIndex = -1;
    private int hoveredSlotIndex = -1;
    private int activeSlotIndex = -1;
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


    public DocumentInventorySlotGridControl setSlotClickHandler(SlotClickHandler slotClickHandler) {
        this.slotClickHandler = slotClickHandler;
        return this;
    }


    public DocumentInventorySlotGridControl setSlotTooltipProvider(SlotTooltipProvider slotTooltipProvider) {
        this.slotTooltipProvider = slotTooltipProvider;
        return this;
    }


    public DocumentInventorySlotGridControl setSelectedSlotIndex(int selectedSlotIndex) {
        this.selectedSlotIndex = selectedSlotIndex >= 0 && selectedSlotIndex < slotCount ? selectedSlotIndex : -1;
        refreshSlotStates();
        return this;
    }


    public DocumentInventorySlotGridControl setCarriedSnapshot(InventorySlotSnapshot carriedSnapshot) {
        this.carriedSnapshot = carriedSnapshot != null ? carriedSnapshot : InventorySlotSnapshot.empty();
        return this;
    }


    public int getHoveredSlotIndex() {
        return hoveredSlotIndex;
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


    public DocumentInventorySlotGridControl setInteractionSlotColors(int hoveredSlotFillColor,
            int hoveredSlotBorderColor, int selectedSlotFillColor, int selectedSlotBorderColor,
            int activeSlotFillColor, int activeSlotBorderColor) {
        this.hoveredSlotFillColor = hoveredSlotFillColor;
        this.hoveredSlotBorderColor = hoveredSlotBorderColor;
        this.selectedSlotFillColor = selectedSlotFillColor;
        this.selectedSlotBorderColor = selectedSlotBorderColor;
        this.activeSlotFillColor = activeSlotFillColor;
        this.activeSlotBorderColor = activeSlotBorderColor;
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
            applySlotStyle(slotElements.get(slotIndex), slotIndex, snapshot.isOccupied());
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
                final int currentSlotIndex = slotIndex;
                ElementNode slotElement = rowCells.get(columnIndex);
                int slotContentSize = resolveSlotContentSize();
                slotElement.style()
                        .setWidth(UiStyleLength.px(slotContentSize))
                        .setHeight(UiStyleLength.px(slotContentSize))
                        .setBorderWidth(UiStyleLength.px(SLOT_BORDER_WIDTH))
                        .setOverflowX(UiOverflow.HIDDEN)
                        .setOverflowY(UiOverflow.HIDDEN);
                slotElement.setActiveHandler(new DocumentElementActiveHandler() {
                    @Override
                    public boolean onActiveChanged(DocumentElementActiveEvent event) {
                        if (event.getButton() != 0 && event.getButton() != 1) {
                            return false;
                        }
                        activeSlotIndex = event.isActive() ? currentSlotIndex : -1;
                        refreshSlotStates();
                        return true;
                    }
                }).setClickHandler(new DocumentElementClickHandler() {
                    @Override
                    public boolean onClick(DocumentElementClickEvent event) {
                        return handleSlotClick(currentSlotIndex, event.getButton(), event.getTimeNanos());
                    }
                }).setHoverHandler(new DocumentElementHoverHandler() {
                    @Override
                    public boolean onHoverChanged(DocumentElementHoverEvent event) {
                        handleSlotHover(currentSlotIndex, event.isHovered());
                        return true;
                    }
                });
                slotElements.add(slotElement);
                slotIndex++;
            }
        }
    }

    private int resolveSlotContentSize() {
        int slotSize = currentLayout == null ? preferredSlotSize : currentLayout.slotSize;
        return Math.max(0, slotSize - SLOT_BORDER_WIDTH * 2);
    }

    private void applySlotStyle(ElementNode slotElement, int slotIndex, boolean occupied) {
        int fillColor;
        int borderColor;
        if (slotIndex == activeSlotIndex) {
            fillColor = activeSlotFillColor;
            borderColor = activeSlotBorderColor;
        } else if (slotIndex == hoveredSlotIndex) {
            fillColor = hoveredSlotFillColor;
            borderColor = hoveredSlotBorderColor;
        } else if (slotIndex == selectedSlotIndex) {
            fillColor = selectedSlotFillColor;
            borderColor = selectedSlotBorderColor;
        } else {
            fillColor = occupied ? occupiedSlotFillColor : emptySlotFillColor;
            borderColor = occupied ? occupiedSlotBorderColor : emptySlotBorderColor;
        }
        slotElement.style()
                .setBackgroundColor(fillColor)
                .setBorderColor(borderColor);
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
        enqueueTooltipOverlay(context);
        enqueueCarriedItemOverlay(context);
    }

    private boolean handleSlotClick(int localIndex, int button, long timeNanos) {
        if (localIndex < 0 || localIndex >= slotCount || slotClickHandler == null) {
            return false;
        }
        boolean consumed = slotClickHandler.onSlotClick(localIndex, button, timeNanos);
        if (consumed) {
            refreshSlotStates();
        }
        return consumed;
    }

    private void handleSlotHover(int localIndex, boolean hovered) {
        hoveredSlotIndex = hovered ? localIndex : -1;
        visibleTooltipLines = hovered ? resolveTooltipLines(localIndex) : Collections.<String>emptyList();
        refreshSlotStates();
    }

    private List<String> resolveTooltipLines(int localIndex) {
        if (slotTooltipProvider != null) {
            List<String> lines = slotTooltipProvider.getSlotTooltip(localIndex);
            if (lines != null) {
                return new ArrayList<String>(lines);
            }
        }
        InventorySlotSnapshot snapshot = contentProvider == null ? InventorySlotSnapshot.empty()
                : contentProvider.getSlotSnapshot(localIndex);
        if (snapshot == null || !snapshot.isOccupied() || snapshot.getDisplayName().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        lines.add(snapshot.getDisplayName());
        if (snapshot.getStackSize() > 1) {
            lines.add("数量 " + snapshot.getStackSize());
        }
        return lines;
    }

    private void enqueueTooltipOverlay(final UiRenderContext context) {
        if (visibleTooltipLines.isEmpty() || context == null || carriedSnapshot.isOccupied()) {
            return;
        }
        final List<String> capturedLines = new ArrayList<String>(visibleTooltipLines);
        enqueueOverlayPass(context, new UiRenderContext.DeferredPostMainPassReplay() {
            @Override
            public void replay() {
                renderTooltip(context, capturedLines);
            }
        });
    }

    private void renderTooltip(UiRenderContext context, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int maxLineWidth = 0;
        for (String line : lines) {
            maxLineWidth = Math.max(maxLineWidth, context.measureTextWidth(line));
        }
        int lineHeight = Math.max(1, context.getTextLineHeight());
        int contentHeight = lines.size() * lineHeight + Math.max(0, lines.size() - 1) * 2;
        int left = context.getMouseX() + 14;
        int top = context.getMouseY() - 12;
        int width = maxLineWidth + 14;
        int height = contentHeight + 12;
        if (left + width > context.getScreenWidth()) {
            left = context.getMouseX() - width - 16;
        }
        if (top + height > context.getScreenHeight()) {
            top = context.getScreenHeight() - height - 4;
        }
        left = Math.max(4, left);
        top = Math.max(4, top);

        context.fillRect(left, top, left + width, top + height, 0xF0181024);
        context.drawBorder(left, top, left + width, top + height, 0xFF8B5CF6);
        int textY = top + 6;
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            context.drawText(lines.get(lineIndex), left + 7, textY, lineIndex == 0 ? 0xFFFFFFFF : 0xFFD1D5DB,
                    true);
            textY += lineHeight + 2;
        }
    }

    private void enqueueCarriedItemOverlay(final UiRenderContext context) {
        if (itemRenderer == null || carriedSnapshot == null || !carriedSnapshot.isOccupied()) {
            return;
        }
        final InventorySlotGridItemRenderer capturedRenderer = itemRenderer;
        final InventorySlotSnapshot capturedSnapshot = carriedSnapshot;
        final int mouseX = context.getMouseX();
        final int mouseY = context.getMouseY();
        enqueueOverlayPass(context, new UiRenderContext.DeferredPostMainPassReplay() {
            @Override
            public void replay() {
                capturedRenderer.renderCursorItem(capturedSnapshot, mouseX, mouseY);
            }
        });
    }

    private static void enqueueOverlayPass(UiRenderContext context, UiRenderContext.DeferredPostMainPassReplay replay) {
        context.enqueueDeferredPostMainOverlayPass(replay);
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
