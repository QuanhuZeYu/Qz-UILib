package club.heiqi.uilib.ui.dom.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.slot.SlotContentSnapshot;
import club.heiqi.uilib.ui.slot.SlotGridLayout;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * 基于 HTML-like table 结构实现的通用槽位网格控件。
 */
public final class DocumentSlotGridControl {

    /**
     * 槽位内容数据源合约。
     */
    public interface SlotContentProvider {
        SlotContentSnapshot getSlotContent(int localIndex);
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
     * 槽位 hover 提示处理器。
     */
    public interface SlotHoverHandler {
        void onSlotHoverChanged(int localIndex, boolean hovered, List<String> tooltipLines, int documentX,
                int documentY, long timeNanos);
    }

    private final ElementNode element;
    private final DocumentTableControl tableControl;
    private final List<ElementNode> slotElements = new ArrayList<ElementNode>();
    private final List<DocumentSlotControl> slotControls = new ArrayList<DocumentSlotControl>();
    private final int slotCount;
    private final int preferredColumns;
    private SlotContentProvider contentProvider;
    private SlotClickHandler slotClickHandler;
    private SlotTooltipProvider slotTooltipProvider;
    private SlotHoverHandler slotHoverHandler;
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
    private SlotGridLayout currentLayout;
    private SlotContentSnapshot carriedContent = SlotContentSnapshot.empty();
    private int selectedSlotIndex = -1;
    private int hoveredSlotIndex = -1;
    private boolean layoutDirty = true;

    /**
     * 创建槽位网格控件。
     *
     * @param document 所属 HTML-like 文档
     * @param slotCount 槽位数量
     * @param preferredColumns 期望列数
     */
    public DocumentSlotGridControl(UiDocument document, int slotCount, int preferredColumns) {
        this.slotCount = Math.max(0, slotCount);
        this.preferredColumns = Math.max(1, preferredColumns);
        this.tableControl = new DocumentTableControl(document)
                .setCellPadding(0)
                .setBorderWidth(1);
        this.element = tableControl.getElement();
    }

    /**
     * 返回网格根元素。
     *
     * @return 根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 设置内容数据源。
     *
     * @param contentProvider 内容数据源
     * @return 当前控件
     */
    public DocumentSlotGridControl setContentProvider(SlotContentProvider contentProvider) {
        this.contentProvider = contentProvider;
        refreshSlotStates();
        return this;
    }

    /**
     * 设置槽位点击处理器。
     *
     * @param slotClickHandler 点击处理器
     * @return 当前控件
     */
    public DocumentSlotGridControl setSlotClickHandler(SlotClickHandler slotClickHandler) {
        this.slotClickHandler = slotClickHandler;
        return this;
    }

    /**
     * 设置槽位 tooltip 提供器。
     *
     * @param slotTooltipProvider tooltip 提供器
     * @return 当前控件
     */
    public DocumentSlotGridControl setSlotTooltipProvider(SlotTooltipProvider slotTooltipProvider) {
        this.slotTooltipProvider = slotTooltipProvider;
        refreshSlotStates();
        return this;
    }

    /**
     * 设置槽位 hover 处理器。
     *
     * @param slotHoverHandler hover 处理器
     * @return 当前控件
     */
    public DocumentSlotGridControl setSlotHoverHandler(SlotHoverHandler slotHoverHandler) {
        this.slotHoverHandler = slotHoverHandler;
        refreshSlotStates();
        return this;
    }

    /**
     * 设置选中槽位索引。
     *
     * @param selectedSlotIndex 选中索引
     * @return 当前控件
     */
    public DocumentSlotGridControl setSelectedSlotIndex(int selectedSlotIndex) {
        this.selectedSlotIndex = selectedSlotIndex >= 0 && selectedSlotIndex < slotCount ? selectedSlotIndex : -1;
        refreshSlotStates();
        return this;
    }

    /**
     * 设置鼠标携带内容。
     *
     * @param carriedContent 鼠标携带内容
     * @return 当前控件
     */
    public DocumentSlotGridControl setCarriedContent(SlotContentSnapshot carriedContent) {
        this.carriedContent = carriedContent != null ? carriedContent : SlotContentSnapshot.empty();
        refreshSlotStates();
        return this;
    }

    /**
     * 返回当前 hover 的槽位索引。
     *
     * @return hover 的槽位索引；没有时返回 -1
     */
    public int getHoveredSlotIndex() {
        return hoveredSlotIndex;
    }

    /**
     * 设置格子间距。
     *
     * @param slotGap 格子间距
     * @return 当前控件
     */
    public DocumentSlotGridControl setSlotGap(int slotGap) {
        this.slotGap = Math.max(0, slotGap);
        layoutDirty = true;
        return this;
    }

    /**
     * 设置期望槽位尺寸。
     *
     * @param preferredSlotSize 期望槽位尺寸
     * @return 当前控件
     */
    public DocumentSlotGridControl setPreferredSlotSize(int preferredSlotSize) {
        this.preferredSlotSize = Math.max(18, preferredSlotSize);
        layoutDirty = true;
        return this;
    }

    /**
     * 设置槽位尺寸范围。
     *
     * @param minSlotSize 最小槽位尺寸
     * @param maxSlotSize 最大槽位尺寸
     * @return 当前控件
     */
    public DocumentSlotGridControl setSlotSizeRange(int minSlotSize, int maxSlotSize) {
        this.minSlotSize = Math.max(18, minSlotSize);
        this.maxSlotSize = Math.max(this.minSlotSize, maxSlotSize);
        layoutDirty = true;
        return this;
    }

    /**
     * 设置槽位颜色。
     *
     * @param emptySlotFillColor 空槽背景色
     * @param emptySlotBorderColor 空槽边框色
     * @param occupiedSlotFillColor 占用槽背景色
     * @param occupiedSlotBorderColor 占用槽边框色
     * @return 当前控件
     */
    public DocumentSlotGridControl setSlotColors(int emptySlotFillColor, int emptySlotBorderColor,
            int occupiedSlotFillColor, int occupiedSlotBorderColor) {
        this.emptySlotFillColor = emptySlotFillColor;
        this.emptySlotBorderColor = emptySlotBorderColor;
        this.occupiedSlotFillColor = occupiedSlotFillColor;
        this.occupiedSlotBorderColor = occupiedSlotBorderColor;
        for (DocumentSlotControl slotControl : slotControls) {
            slotControl.setSlotColors(emptySlotFillColor, emptySlotBorderColor, occupiedSlotFillColor,
                    occupiedSlotBorderColor);
        }
        refreshSlotStates();
        return this;
    }

    /**
     * 设置交互态颜色。
     *
     * @param hoveredSlotFillColor hover 背景色
     * @param hoveredSlotBorderColor hover 边框色
     * @param selectedSlotFillColor 选中背景色
     * @param selectedSlotBorderColor 选中边框色
     * @param activeSlotFillColor 按下背景色
     * @param activeSlotBorderColor 按下边框色
     * @return 当前控件
     */
    public DocumentSlotGridControl setInteractionSlotColors(int hoveredSlotFillColor,
            int hoveredSlotBorderColor, int selectedSlotFillColor, int selectedSlotBorderColor,
            int activeSlotFillColor, int activeSlotBorderColor) {
        this.hoveredSlotFillColor = hoveredSlotFillColor;
        this.hoveredSlotBorderColor = hoveredSlotBorderColor;
        this.selectedSlotFillColor = selectedSlotFillColor;
        this.selectedSlotBorderColor = selectedSlotBorderColor;
        this.activeSlotFillColor = activeSlotFillColor;
        this.activeSlotBorderColor = activeSlotBorderColor;
        for (DocumentSlotControl slotControl : slotControls) {
            slotControl.setInteractionSlotColors(hoveredSlotFillColor, hoveredSlotBorderColor,
                    selectedSlotFillColor, selectedSlotBorderColor, activeSlotFillColor, activeSlotBorderColor);
        }
        refreshSlotStates();
        return this;
    }

    /**
     * 提交布局配置。
     *
     * @return 当前控件
     */
    public DocumentSlotGridControl commitLayout() {
        if (layoutDirty) {
            configureElement();
            layoutDirty = false;
        }
        refreshSlotStates();
        return this;
    }

    /**
     * 刷新所有槽位状态。
     *
     * @return 当前控件
     */
    public DocumentSlotGridControl refreshSlotStates() {
        if (slotControls.isEmpty()) {
            return this;
        }
        SlotContentSnapshot[] contents = sampleSlotContents();
        for (int slotIndex = 0; slotIndex < slotControls.size(); slotIndex++) {
            SlotContentSnapshot content = slotIndex < contents.length ? contents[slotIndex] : SlotContentSnapshot.empty();
            DocumentSlotControl slotControl = slotControls.get(slotIndex);
            slotControl.setSelected(slotIndex == selectedSlotIndex)
                    .setCarriedContent(carriedContent)
                    .setContent(content);
        }
        return this;
    }

    private void configureElement() {
        currentLayout = SlotGridLayout.resolvePreferred(slotCount, preferredColumns, slotGap,
                preferredSlotSize, minSlotSize, maxSlotSize);
        tableControl.setCellGap(slotGap, slotGap);
        element.style()
                .setWidth(UiStyleLength.px(currentLayout.totalWidth))
                .setHeight(UiStyleLength.px(currentLayout.totalHeight));
        rebuildSlotElements();
    }

    private void rebuildSlotElements() {
        slotElements.clear();
        slotControls.clear();
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
                DocumentSlotControl slotControl = new DocumentSlotControl(slotElement)
                        .setSlotLabel("槽位 " + (currentSlotIndex + 1))
                        .setContentBoxSize(resolveSlotContentSize())
                        .setSlotColors(emptySlotFillColor, emptySlotBorderColor,
                                occupiedSlotFillColor, occupiedSlotBorderColor)
                        .setInteractionSlotColors(hoveredSlotFillColor, hoveredSlotBorderColor,
                                selectedSlotFillColor, selectedSlotBorderColor, activeSlotFillColor,
                                activeSlotBorderColor)
                        .setTooltipProvider(new DocumentSlotControl.SlotTooltipProvider() {
                            @Override
                            public List<String> getSlotTooltip(SlotContentSnapshot content) {
                                return resolveTooltipLines(currentSlotIndex, content);
                            }
                        }).setSlotClickHandler(new DocumentSlotControl.SlotClickHandler() {
                            @Override
                            public boolean onSlotClick(int button, long timeNanos) {
                                return handleSlotClick(currentSlotIndex, button, timeNanos);
                            }
                        }).setSlotHoverHandler(new DocumentSlotControl.SlotHoverHandler() {
                            @Override
                            public void onSlotHoverChanged(boolean hovered, List<String> tooltipLines,
                                    int documentX, int documentY, long timeNanos) {
                                hoveredSlotIndex = hovered ? currentSlotIndex : -1;
                                if (slotHoverHandler != null) {
                                    slotHoverHandler.onSlotHoverChanged(currentSlotIndex, hovered,
                                            new ArrayList<String>(tooltipLines), documentX, documentY, timeNanos);
                                }
                            }
                        });
                slotElement.setAttribute("data-slot-index", String.valueOf(currentSlotIndex));
                slotElements.add(slotElement);
                slotControls.add(slotControl);
                slotIndex++;
            }
        }
    }

    private int resolveSlotContentSize() {
        int slotSize = currentLayout == null ? preferredSlotSize : currentLayout.slotSize;
        return Math.max(0, slotSize - 2);
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

    private List<String> resolveTooltipLines(int localIndex, SlotContentSnapshot content) {
        if (slotTooltipProvider != null) {
            List<String> lines = slotTooltipProvider.getSlotTooltip(localIndex);
            if (lines != null) {
                return new ArrayList<String>(lines);
            }
        }
        if (content == null || !content.isOccupied()) {
            return Collections.emptyList();
        }
        if (!content.getTooltipLines().isEmpty()) {
            return new ArrayList<String>(content.getTooltipLines());
        }
        if (content.getDisplayName().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        lines.add(content.getDisplayName());
        if (content.getPrimaryCount() > 1) {
            lines.add("数量 " + content.getPrimaryCount());
        }
        return lines;
    }

    private SlotContentSnapshot[] sampleSlotContents() {
        SlotContentSnapshot[] snapshots = new SlotContentSnapshot[slotCount];
        if (contentProvider != null) {
            for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                SlotContentSnapshot snapshot = contentProvider.getSlotContent(slotIndex);
                snapshots[slotIndex] = snapshot != null ? snapshot : SlotContentSnapshot.empty();
            }
            return snapshots;
        }
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            snapshots[slotIndex] = SlotContentSnapshot.empty();
        }
        return snapshots;
    }
}
