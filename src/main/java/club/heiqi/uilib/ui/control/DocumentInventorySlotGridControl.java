package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.inventory.InventorySlotGridItemRenderer;
import club.heiqi.uilib.ui.inventory.InventorySlotSnapshot;
import club.heiqi.uilib.ui.slot.SlotContentSnapshot;

/**
 * 背包槽位网格的 HTML-like 适配层。
 *
 * <p>该类型保留背包语义接口，对外继续使用 `InventorySlotSnapshot`，
 * 内部委托给通用 `DocumentSlotGridControl`。</p>
 */
public final class DocumentInventorySlotGridControl {

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

    private final DocumentSlotGridControl slotGridControl;
    private SlotContentProvider contentProvider;

    /**
     * 创建背包格子网格控件。
     *
     * @param document 所属 HTML-like 文档
     * @param slotCount 槽位数量
     * @param preferredColumns 期望列数
     */
    public DocumentInventorySlotGridControl(UiDocument document, int slotCount, int preferredColumns) {
        this.slotGridControl = new DocumentSlotGridControl(document, slotCount, preferredColumns)
                .setContentProvider(new DocumentSlotGridControl.SlotContentProvider() {
                    @Override
                    public SlotContentSnapshot getSlotContent(int localIndex) {
                        return convertSnapshot(resolveSnapshot(localIndex));
                    }
                });
    }

    public ElementNode getElement() {
        return slotGridControl.getElement();
    }

    public DocumentInventorySlotGridControl setContentProvider(SlotContentProvider contentProvider) {
        this.contentProvider = contentProvider;
        slotGridControl.refreshSlotStates();
        return this;
    }

    /**
     * 旧物品 renderer 仅保留兼容入口；纯 HTML-like 路径不再主动使用该能力。
     */
    public DocumentInventorySlotGridControl setItemRenderer(InventorySlotGridItemRenderer itemRenderer) {
        return this;
    }

    public DocumentInventorySlotGridControl setSlotClickHandler(final SlotClickHandler slotClickHandler) {
        slotGridControl.setSlotClickHandler(slotClickHandler == null ? null
                : new DocumentSlotGridControl.SlotClickHandler() {
                    @Override
                    public boolean onSlotClick(int localIndex, int button, long timeNanos) {
                        return slotClickHandler.onSlotClick(localIndex, button, timeNanos);
                    }
                });
        return this;
    }

    public DocumentInventorySlotGridControl setSlotTooltipProvider(final SlotTooltipProvider slotTooltipProvider) {
        slotGridControl.setSlotTooltipProvider(slotTooltipProvider == null ? null
                : new DocumentSlotGridControl.SlotTooltipProvider() {
                    @Override
                    public List<String> getSlotTooltip(int localIndex) {
                        return slotTooltipProvider.getSlotTooltip(localIndex);
                    }
                });
        return this;
    }

    public DocumentInventorySlotGridControl setSlotHoverHandler(final SlotHoverHandler slotHoverHandler) {
        slotGridControl.setSlotHoverHandler(slotHoverHandler == null ? null
                : new DocumentSlotGridControl.SlotHoverHandler() {
                    @Override
                    public void onSlotHoverChanged(int localIndex, boolean hovered, List<String> tooltipLines,
                            int documentX, int documentY, long timeNanos) {
                        slotHoverHandler.onSlotHoverChanged(localIndex, hovered, tooltipLines,
                                documentX, documentY, timeNanos);
                    }
                });
        return this;
    }

    public DocumentInventorySlotGridControl setSelectedSlotIndex(int selectedSlotIndex) {
        slotGridControl.setSelectedSlotIndex(selectedSlotIndex);
        return this;
    }

    public DocumentInventorySlotGridControl setCarriedSnapshot(InventorySlotSnapshot carriedSnapshot) {
        slotGridControl.setCarriedContent(convertSnapshot(carriedSnapshot));
        return this;
    }

    public DocumentInventorySlotGridControl setCarriedItemOverlayEnabled(boolean carriedItemOverlayEnabled) {
        return this;
    }

    public int getHoveredSlotIndex() {
        return slotGridControl.getHoveredSlotIndex();
    }

    public DocumentInventorySlotGridControl setSlotGap(int slotGap) {
        slotGridControl.setSlotGap(slotGap);
        return this;
    }

    public DocumentInventorySlotGridControl setPreferredSlotSize(int preferredSlotSize) {
        slotGridControl.setPreferredSlotSize(preferredSlotSize);
        return this;
    }

    public DocumentInventorySlotGridControl setSlotSizeRange(int minSlotSize, int maxSlotSize) {
        slotGridControl.setSlotSizeRange(minSlotSize, maxSlotSize);
        return this;
    }

    public DocumentInventorySlotGridControl setSlotColors(int emptySlotFillColor, int emptySlotBorderColor,
            int occupiedSlotFillColor, int occupiedSlotBorderColor) {
        slotGridControl.setSlotColors(emptySlotFillColor, emptySlotBorderColor,
                occupiedSlotFillColor, occupiedSlotBorderColor);
        return this;
    }

    public DocumentInventorySlotGridControl setInteractionSlotColors(int hoveredSlotFillColor,
            int hoveredSlotBorderColor, int selectedSlotFillColor, int selectedSlotBorderColor,
            int activeSlotFillColor, int activeSlotBorderColor) {
        slotGridControl.setInteractionSlotColors(hoveredSlotFillColor, hoveredSlotBorderColor,
                selectedSlotFillColor, selectedSlotBorderColor, activeSlotFillColor, activeSlotBorderColor);
        return this;
    }

    public DocumentInventorySlotGridControl commitLayout() {
        slotGridControl.commitLayout();
        return this;
    }

    public DocumentInventorySlotGridControl refreshSlotStates() {
        slotGridControl.refreshSlotStates();
        return this;
    }

    private InventorySlotSnapshot resolveSnapshot(int localIndex) {
        InventorySlotSnapshot snapshot = contentProvider == null ? InventorySlotSnapshot.empty()
                : contentProvider.getSlotSnapshot(localIndex);
        return snapshot != null ? snapshot : InventorySlotSnapshot.empty();
    }

    private static SlotContentSnapshot convertSnapshot(InventorySlotSnapshot snapshot) {
        if (snapshot == null || !snapshot.isOccupied()) {
            return SlotContentSnapshot.empty();
        }
        List<String> tooltipLines = new ArrayList<String>();
        if (snapshot.getDisplayName() != null && !snapshot.getDisplayName().isEmpty()) {
            tooltipLines.add(snapshot.getDisplayName());
        }
        if (snapshot.getStackSize() > 1) {
            tooltipLines.add("数量 " + snapshot.getStackSize());
        }
        return SlotContentSnapshot.builder()
                .setOccupied(true)
                .setContentKind("item")
                .setVisualSource(snapshot.toHostImageSource())
                .setDisplayName(snapshot.getDisplayName())
                .setPrimaryCount(snapshot.getStackSize())
                .setTooltipLines(tooltipLines)
                .build();
    }
}
