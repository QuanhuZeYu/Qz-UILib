package club.heiqi.uilib.ui.dom.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.dom.DocumentElementActiveEvent;
import club.heiqi.uilib.ui.dom.DocumentElementActiveHandler;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.image.HostImageSource;
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

    /**
     * 槽位 hover 提示展示状态处理器。
     */
    public interface SlotHoverHandler {
        void onSlotHoverChanged(int localIndex, boolean hovered, List<String> tooltipLines, int documentX,
                int documentY, long timeNanos);
    }

    private final ElementNode element;
    private final DocumentTableControl tableControl;
    private final List<ElementNode> slotElements = new ArrayList<ElementNode>();
    private final int slotCount;
    private final int preferredColumns;
    private SlotContentProvider contentProvider;
    private SlotClickHandler slotClickHandler;
    private SlotTooltipProvider slotTooltipProvider;
    private SlotHoverHandler slotHoverHandler;
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
    private int lastHoverDocumentX = -1;
    private int lastHoverDocumentY = -1;
    private long lastHoverTimeNanos;
    private int lastNotifiedTooltipSlotIndex = -1;
    private boolean lastNotifiedTooltipHovered;
    private List<String> lastNotifiedTooltipLines = Collections.emptyList();
    private boolean carriedItemOverlayEnabled = true;
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


    public DocumentInventorySlotGridControl setSlotHoverHandler(SlotHoverHandler slotHoverHandler) {
        this.slotHoverHandler = slotHoverHandler;
        return this;
    }


    public DocumentInventorySlotGridControl setSelectedSlotIndex(int selectedSlotIndex) {
        this.selectedSlotIndex = selectedSlotIndex >= 0 && selectedSlotIndex < slotCount ? selectedSlotIndex : -1;
        refreshSlotStates();
        return this;
    }


    public DocumentInventorySlotGridControl setCarriedSnapshot(InventorySlotSnapshot carriedSnapshot) {
        this.carriedSnapshot = carriedSnapshot != null ? carriedSnapshot : InventorySlotSnapshot.empty();
        refreshVisibleTooltipLines();
        return this;
    }


    public DocumentInventorySlotGridControl setCarriedItemOverlayEnabled(boolean carriedItemOverlayEnabled) {
        this.carriedItemOverlayEnabled = carriedItemOverlayEnabled;
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
            applySlotStyle(slotElements.get(slotIndex), slotIndex, snapshot);
        }
        refreshVisibleTooltipLines();
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
                slotElement.setAttribute("role", "button")
                        .setAttribute("tabindex", "0")
                        .setAttribute("data-slot-index", String.valueOf(currentSlotIndex))
                        .setFocusable(true);
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
                        handleSlotHover(currentSlotIndex, event.isHovered(), event.getDocumentX(),
                                event.getDocumentY(), event.getTimeNanos());
                        return true;
                    }
                }).setKeyHandler(new DocumentElementKeyHandler() {
                    @Override
                    public boolean onKey(DocumentElementKeyEvent event) {
                        return handleSlotKey(currentSlotIndex, event);
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

    private boolean handleSlotKey(int localIndex, DocumentElementKeyEvent event) {
        if (event == null || !isActivationKey(event.getKeyCode())) {
            return false;
        }
        if (event.getAction() == UiKeyEvent.Action.PRESSED) {
            activeSlotIndex = localIndex;
            boolean consumed = handleSlotClick(localIndex, 0, event.getTimeNanos());
            refreshSlotStates();
            return consumed;
        }
        if (event.getAction() == UiKeyEvent.Action.RELEASED) {
            if (activeSlotIndex == localIndex) {
                activeSlotIndex = -1;
                refreshSlotStates();
            }
            return true;
        }
        return true;
    }

    private static boolean isActivationKey(int keyCode) {
        return keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER || keyCode == Keyboard.KEY_SPACE;
    }

    private void applySlotStyle(ElementNode slotElement, int slotIndex, InventorySlotSnapshot snapshot) {
        boolean occupied = snapshot != null && snapshot.isOccupied();
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
        slotElement.setAttribute("data-slot-occupied", String.valueOf(occupied))
                .setAttribute("data-slot-selected", String.valueOf(slotIndex == selectedSlotIndex))
                .setAttribute("data-slot-hovered", String.valueOf(slotIndex == hoveredSlotIndex))
                .setAttribute("aria-label", formatSlotAriaLabel(slotIndex, snapshot));
    }

    private static String formatSlotAriaLabel(int slotIndex, InventorySlotSnapshot snapshot) {
        int slotNumber = slotIndex + 1;
        if (snapshot == null || !snapshot.isOccupied()) {
            return "槽位 " + slotNumber + "，空";
        }
        String displayName = snapshot.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            return "槽位 " + slotNumber + "，已占用";
        }
        if (snapshot.getStackSize() > 1) {
            return "槽位 " + slotNumber + "，" + displayName + "，数量 " + snapshot.getStackSize();
        }
        return "槽位 " + slotNumber + "，" + displayName;
    }

    private void renderItems(UiRenderContext context, int absX, int absY) {
        if (slotCount <= 0 || currentLayout == null) {
            return;
        }
        InventorySlotSnapshot[] snapshots = sampleSlotSnapshots();

        if (renderItemsThroughHostImages(context, absX, absY, snapshots)) {
            enqueueCarriedItemOverlay(context);
            return;
        }

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
        enqueueCarriedItemOverlay(context);
    }

    private boolean renderItemsThroughHostImages(UiRenderContext context, int absX, int absY,
            InventorySlotSnapshot[] snapshots) {
        if (context == null || currentLayout == null || snapshots == null || snapshots.length <= 0) {
            return false;
        }
        boolean renderedAny = false;
        for (int slotIndex = 0; slotIndex < snapshots.length; slotIndex++) {
            InventorySlotSnapshot snapshot = snapshots[slotIndex];
            HostImageSource hostImageSource = snapshot == null ? null : snapshot.toHostImageSource();
            if (hostImageSource == null) {
                continue;
            }
            InventorySlotGridLayout.SlotRect slotRect = currentLayout.getSlotRect(slotIndex);
            context.drawHostImage(hostImageSource, absX + slotRect.left, absY + slotRect.top,
                    absX + slotRect.right, absY + slotRect.bottom);
            renderedAny = true;
        }
        return renderedAny;
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

    private void handleSlotHover(int localIndex, boolean hovered, int documentX, int documentY, long timeNanos) {
        hoveredSlotIndex = hovered ? localIndex : -1;
        lastHoverDocumentX = documentX;
        lastHoverDocumentY = documentY;
        lastHoverTimeNanos = timeNanos;
        refreshSlotStates();
    }

    private void refreshVisibleTooltipLines() {
        visibleTooltipLines = hoveredSlotIndex >= 0 && hoveredSlotIndex < slotCount && !carriedSnapshot.isOccupied()
                ? resolveTooltipLines(hoveredSlotIndex) : Collections.<String>emptyList();
        boolean tooltipHovered = hoveredSlotIndex >= 0;
        if (slotHoverHandler != null && shouldNotifyTooltipHover(tooltipHovered)) {
            slotHoverHandler.onSlotHoverChanged(hoveredSlotIndex, hoveredSlotIndex >= 0,
                    new ArrayList<String>(visibleTooltipLines), lastHoverDocumentX, lastHoverDocumentY,
                    lastHoverTimeNanos);
            lastNotifiedTooltipSlotIndex = hoveredSlotIndex;
            lastNotifiedTooltipHovered = tooltipHovered;
            lastNotifiedTooltipLines = new ArrayList<String>(visibleTooltipLines);
        }
    }

    private boolean shouldNotifyTooltipHover(boolean tooltipHovered) {
        return tooltipHovered != lastNotifiedTooltipHovered
                || hoveredSlotIndex != lastNotifiedTooltipSlotIndex
                || !visibleTooltipLines.equals(lastNotifiedTooltipLines);
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

    private void enqueueCarriedItemOverlay(final UiRenderContext context) {
        if (!carriedItemOverlayEnabled || itemRenderer == null || carriedSnapshot == null
                || !carriedSnapshot.isOccupied()) {
            if (!carriedItemOverlayEnabled || carriedSnapshot == null || !carriedSnapshot.isOccupied()) {
                return;
            }
        }
        HostImageSource hostImageSource = carriedSnapshot == null ? null : carriedSnapshot.toHostImageSource();
        if (hostImageSource != null) {
            final HostImageSource capturedSource = hostImageSource;
            final int mouseX = context.getMouseX();
            final int mouseY = context.getMouseY();
            enqueueOverlayPass(context, new UiRenderContext.DeferredPostMainPassReplay() {
                @Override
                public void replay() {
                    context.drawHostImage(capturedSource, mouseX - 12, mouseY - 12, mouseX + 12, mouseY + 12);
                }
            });
            return;
        }
        if (itemRenderer == null) {
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
