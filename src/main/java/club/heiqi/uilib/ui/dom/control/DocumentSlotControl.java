package club.heiqi.uilib.ui.dom.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
import club.heiqi.uilib.ui.slot.SlotContentSnapshot;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * 基于 HTML-like 元素实现的通用槽位控件。
 */
public final class DocumentSlotControl {

    private static final int SLOT_BORDER_WIDTH = 1;
    private static final HostImageSource PLACEHOLDER_IMAGE_SOURCE = HostImageSource.textureRegion(
            new net.minecraft.util.ResourceLocation("minecraft", "textures/gui/widgets.png"), 256, 256, 0, 0, 1,
            1);

    /**
     * 槽位点击处理器。
     */
    public interface SlotClickHandler {
        boolean onSlotClick(int button, long timeNanos);
    }

    /**
     * 槽位 hover 状态处理器。
     */
    public interface SlotHoverHandler {
        void onSlotHoverChanged(boolean hovered, List<String> tooltipLines, int documentX, int documentY,
                long timeNanos);
    }

    /**
     * 槽位 tooltip 内容提供器。
     */
    public interface SlotTooltipProvider {
        List<String> getSlotTooltip(SlotContentSnapshot content);
    }

    private final ElementNode element;
    private final DocumentHostImageControl contentImageControl;
    private SlotContentSnapshot content = SlotContentSnapshot.empty();
    private SlotContentSnapshot carriedContent = SlotContentSnapshot.empty();
    private SlotClickHandler slotClickHandler;
    private SlotHoverHandler slotHoverHandler;
    private SlotTooltipProvider slotTooltipProvider;
    private String slotLabel = "槽位";
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
    private boolean selected;
    private boolean hovered;
    private boolean active;
    private List<String> visibleTooltipLines = Collections.emptyList();
    private int lastHoverDocumentX = -1;
    private int lastHoverDocumentY = -1;
    private long lastHoverTimeNanos;
    private boolean lastNotifiedTooltipHovered;
    private List<String> lastNotifiedTooltipLines = Collections.emptyList();

    /**
     * 创建一个默认以 `div` 承载的槽位控件。
     *
     * @param document 所属文档
     */
    public DocumentSlotControl(UiDocument document) {
        this(Objects.requireNonNull(document, "document").div());
    }

    /**
     * 绑定到指定元素上创建槽位控件。
     *
     * @param element 作为槽位根节点的元素
     */
    public DocumentSlotControl(ElementNode element) {
        this.element = Objects.requireNonNull(element, "element");
        this.contentImageControl = new DocumentHostImageControl(element.getOwnerDocument(), PLACEHOLDER_IMAGE_SOURCE);
        configureElement();
        installHandlers();
        appendContentImage();
        setContentBoxSize(30);
        refreshState();
    }

    /**
     * 返回槽位根元素。
     *
     * @return 根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回当前槽位内容。
     *
     * @return 当前槽位内容
     */
    public SlotContentSnapshot getContent() {
        return content;
    }

    /**
     * 设置槽位点击处理器。
     *
     * @param slotClickHandler 点击处理器
     * @return 当前控件
     */
    public DocumentSlotControl setSlotClickHandler(SlotClickHandler slotClickHandler) {
        this.slotClickHandler = slotClickHandler;
        return this;
    }

    /**
     * 设置槽位 hover 处理器。
     *
     * @param slotHoverHandler hover 处理器
     * @return 当前控件
     */
    public DocumentSlotControl setSlotHoverHandler(SlotHoverHandler slotHoverHandler) {
        this.slotHoverHandler = slotHoverHandler;
        refreshVisibleTooltipLines();
        return this;
    }

    /**
     * 设置槽位 tooltip 提供器。
     *
     * @param slotTooltipProvider tooltip 提供器
     * @return 当前控件
     */
    public DocumentSlotControl setTooltipProvider(SlotTooltipProvider slotTooltipProvider) {
        this.slotTooltipProvider = slotTooltipProvider;
        refreshVisibleTooltipLines();
        return this;
    }

    /**
     * 设置槽位标签名。
     *
     * @param slotLabel 槽位标签名
     * @return 当前控件
     */
    public DocumentSlotControl setSlotLabel(String slotLabel) {
        this.slotLabel = slotLabel == null || slotLabel.trim().isEmpty() ? "槽位" : slotLabel.trim();
        applyVisualState();
        return this;
    }

    /**
     * 设置槽位内容。
     *
     * @param content 槽位内容
     * @return 当前控件
     */
    public DocumentSlotControl setContent(SlotContentSnapshot content) {
        this.content = content != null ? content : SlotContentSnapshot.empty();
        refreshState();
        return this;
    }

    /**
     * 设置鼠标携带内容，用于 tooltip 抑制。
     *
     * @param carriedContent 鼠标携带内容
     * @return 当前控件
     */
    public DocumentSlotControl setCarriedContent(SlotContentSnapshot carriedContent) {
        this.carriedContent = carriedContent != null ? carriedContent : SlotContentSnapshot.empty();
        refreshVisibleTooltipLines();
        return this;
    }

    /**
     * 设置是否选中。
     *
     * @param selected 是否选中
     * @return 当前控件
     */
    public DocumentSlotControl setSelected(boolean selected) {
        this.selected = selected;
        applyVisualState();
        return this;
    }

    /**
     * 判断当前是否处于 hover 态。
     *
     * @return 是否 hover
     */
    public boolean isHovered() {
        return hovered;
    }

    /**
     * 设置槽位内容盒尺寸。
     *
     * @param slotContentSize 内容盒尺寸
     * @return 当前控件
     */
    public DocumentSlotControl setContentBoxSize(int slotContentSize) {
        int resolvedSize = Math.max(0, slotContentSize);
        element.style()
                .setWidth(UiStyleLength.px(resolvedSize))
                .setHeight(UiStyleLength.px(resolvedSize))
                .setBorderWidth(UiStyleLength.px(SLOT_BORDER_WIDTH))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        contentImageControl.setSize(resolvedSize);
        contentImageControl.getElement().style()
                .setWidth(UiStyleLength.px(resolvedSize))
                .setHeight(UiStyleLength.px(resolvedSize));
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
    public DocumentSlotControl setSlotColors(int emptySlotFillColor, int emptySlotBorderColor,
            int occupiedSlotFillColor, int occupiedSlotBorderColor) {
        this.emptySlotFillColor = emptySlotFillColor;
        this.emptySlotBorderColor = emptySlotBorderColor;
        this.occupiedSlotFillColor = occupiedSlotFillColor;
        this.occupiedSlotBorderColor = occupiedSlotBorderColor;
        applyVisualState();
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
    public DocumentSlotControl setInteractionSlotColors(int hoveredSlotFillColor, int hoveredSlotBorderColor,
            int selectedSlotFillColor, int selectedSlotBorderColor, int activeSlotFillColor,
            int activeSlotBorderColor) {
        this.hoveredSlotFillColor = hoveredSlotFillColor;
        this.hoveredSlotBorderColor = hoveredSlotBorderColor;
        this.selectedSlotFillColor = selectedSlotFillColor;
        this.selectedSlotBorderColor = selectedSlotBorderColor;
        this.activeSlotFillColor = activeSlotFillColor;
        this.activeSlotBorderColor = activeSlotBorderColor;
        applyVisualState();
        return this;
    }

    /**
     * 刷新当前视觉与 tooltip 状态。
     *
     * @return 当前控件
     */
    public DocumentSlotControl refreshState() {
        applyVisualState();
        syncContentImage();
        refreshVisibleTooltipLines();
        return this;
    }

    private void configureElement() {
        element.setAttribute("role", "button")
                .setAttribute("tabindex", "0")
                .setAttribute("data-slot-control", "true")
                .setFocusable(true);
    }

    private void appendContentImage() {
        ElementNode imageElement = contentImageControl.getElement();
        imageElement.setAttribute("data-slot-image", "true");
        imageElement.style()
                .setPosition(UiPosition.RELATIVE)
                .setDisplay(UiDisplay.NONE);
        element.append(imageElement);
    }

    private void installHandlers() {
        element.setActiveHandler(new DocumentElementActiveHandler() {
            @Override
            public boolean onActiveChanged(DocumentElementActiveEvent event) {
                if (event.getButton() != 0 && event.getButton() != 1) {
                    return false;
                }
                active = event.isActive();
                applyVisualState();
                return true;
            }
        }).setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                return handleSlotClick(event.getButton(), event.getTimeNanos());
            }
        }).setHoverHandler(new DocumentElementHoverHandler() {
            @Override
            public boolean onHoverChanged(DocumentElementHoverEvent event) {
                handleSlotHover(event.isHovered(), event.getDocumentX(), event.getDocumentY(), event.getTimeNanos());
                return true;
            }
        }).setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                return handleSlotKey(event);
            }
        });
    }

    private boolean handleSlotClick(int button, long timeNanos) {
        if (slotClickHandler == null) {
            return false;
        }
        boolean consumed = slotClickHandler.onSlotClick(button, timeNanos);
        if (consumed) {
            refreshState();
        }
        return consumed;
    }

    private void handleSlotHover(boolean hovered, int documentX, int documentY, long timeNanos) {
        this.hovered = hovered;
        this.lastHoverDocumentX = documentX;
        this.lastHoverDocumentY = documentY;
        this.lastHoverTimeNanos = timeNanos;
        refreshState();
    }

    private boolean handleSlotKey(DocumentElementKeyEvent event) {
        if (event == null || !isActivationKey(event.getKeyCode())) {
            return false;
        }
        if (event.getAction() == UiKeyEvent.Action.PRESSED) {
            active = true;
            boolean consumed = handleSlotClick(0, event.getTimeNanos());
            applyVisualState();
            return consumed;
        }
        if (event.getAction() == UiKeyEvent.Action.RELEASED) {
            active = false;
            applyVisualState();
            return true;
        }
        return true;
    }

    private void applyVisualState() {
        boolean occupied = content != null && content.isOccupied();
        int fillColor;
        int borderColor;
        if (active) {
            fillColor = activeSlotFillColor;
            borderColor = activeSlotBorderColor;
        } else if (hovered) {
            fillColor = hoveredSlotFillColor;
            borderColor = hoveredSlotBorderColor;
        } else if (selected) {
            fillColor = selectedSlotFillColor;
            borderColor = selectedSlotBorderColor;
        } else {
            fillColor = occupied ? occupiedSlotFillColor : emptySlotFillColor;
            borderColor = occupied ? occupiedSlotBorderColor : emptySlotBorderColor;
        }
        element.style()
                .setBackgroundColor(fillColor)
                .setBorderColor(borderColor);
        element.setAttribute("data-slot-occupied", String.valueOf(occupied))
                .setAttribute("data-slot-selected", String.valueOf(selected))
                .setAttribute("data-slot-hovered", String.valueOf(hovered))
                .setAttribute("data-slot-content-kind", occupied ? content.getContentKind() : "empty")
                .setAttribute("aria-label", content.describeForAria(slotLabel));
    }

    private void syncContentImage() {
        HostImageSource visualSource = content == null ? null : content.getVisualSource();
        ElementNode imageElement = contentImageControl.getElement();
        if (visualSource == null) {
            imageElement.style().setDisplay(UiDisplay.NONE);
            return;
        }
        contentImageControl.setSource(visualSource);
        imageElement.style().setDisplay(UiDisplay.BLOCK);
    }

    private void refreshVisibleTooltipLines() {
        visibleTooltipLines = hovered && !carriedContent.isOccupied()
                ? resolveTooltipLines() : Collections.<String>emptyList();
        boolean tooltipHovered = hovered;
        if (slotHoverHandler != null && shouldNotifyTooltipHover(tooltipHovered)) {
            slotHoverHandler.onSlotHoverChanged(hovered, new ArrayList<String>(visibleTooltipLines),
                    lastHoverDocumentX, lastHoverDocumentY, lastHoverTimeNanos);
            lastNotifiedTooltipHovered = tooltipHovered;
            lastNotifiedTooltipLines = new ArrayList<String>(visibleTooltipLines);
        }
    }

    private boolean shouldNotifyTooltipHover(boolean tooltipHovered) {
        return tooltipHovered != lastNotifiedTooltipHovered
                || !visibleTooltipLines.equals(lastNotifiedTooltipLines);
    }

    private List<String> resolveTooltipLines() {
        if (slotTooltipProvider != null) {
            List<String> lines = slotTooltipProvider.getSlotTooltip(content);
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

    private static boolean isActivationKey(int keyCode) {
        return keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER || keyCode == Keyboard.KEY_SPACE;
    }
}
