package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementBounds;
import club.heiqi.uilib.ui.dom.DocumentElementDragEvent;
import club.heiqi.uilib.ui.dom.DocumentElementDragHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 基于 HTML-like 元素实现的水平数值滑块控件。
 */
public final class DocumentSliderControl {

    private static final double EPSILON = 0.0000001D;

    private final ElementNode element;
    private final ElementNode trackElement;
    private final ElementNode fillElement;
    private final ElementNode thumbElement;
    private DocumentSliderChangeHandler changeHandler;
    private UiSliderOrientation orientation = UiSliderOrientation.HORIZONTAL;
    private double value;
    private double min;
    private double max = 100.0D;
    private double step;
    private boolean enabled = true;
    private boolean dragging;
    private boolean focusVisible;
    private boolean hovered;
    private double dragStartValue;
    private int trackWidth = 160;
    private int trackHeight = 6;
    private int thumbSize = 16;
    private int trackColor = 0xFF334155;
    private int fillColor = 0xFF38BDF8;
    private int trackDisabledColor = 0xFF1F2937;
    private int thumbColor = 0xFFE0F2FE;
    private int thumbHoverColor = 0xFFFFFFFF;
    private int thumbDraggingColor = 0xFFBAE6FD;
    private int thumbDisabledColor = 0xFF64748B;
    private int focusBorderColor = 0xFFBFDBFE;

    /**
     * 创建水平滑块控件。
     *
     * @param document 所属 HTML-like 文档
     */
    public DocumentSliderControl(UiDocument document) {
        this.element = document.div();
        this.trackElement = document.div();
        this.fillElement = document.div();
        this.thumbElement = document.div();
        trackElement.append(fillElement);
        element.append(trackElement);
        element.append(thumbElement);
        configureElement();
        installHandlers();
        updateVisualState();
    }

    /**
     * 返回滑块控件根元素。
     *
     * @return 滑块控件根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 设置数值范围。
     *
     * @param min 最小值
     * @param max 最大值
     * @return 当前滑块控件
     */
    public DocumentSliderControl setRange(double min, double max) {
        if (max < min) {
            double swap = min;
            min = max;
            max = swap;
        }
        this.min = min;
        this.max = max;
        this.value = normalizeValue(value);
        updateVisualState();
        return this;
    }

    /**
     * 返回最小值。
     *
     * @return 最小值
     */
    public double getMin() {
        return min;
    }

    /**
     * 返回最大值。
     *
     * @return 最大值
     */
    public double getMax() {
        return max;
    }

    /**
     * 设置步进值，0 表示连续。
     *
     * @param step 步进值
     * @return 当前滑块控件
     */
    public DocumentSliderControl setStep(double step) {
        this.step = step <= 0.0D ? 0.0D : step;
        this.value = normalizeValue(value);
        updateVisualState();
        return this;
    }

    /**
     * 返回步进值。
     *
     * @return 步进值
     */
    public double getStep() {
        return step;
    }

    /**
     * 设置当前值，默认不触发变更事件。
     *
     * @param value 当前值
     * @return 当前滑块控件
     */
    public DocumentSliderControl setValue(double value) {
        return setValue(value, false);
    }

    /**
     * 设置当前值。
     *
     * @param value 当前值
     * @param notify 是否触发提交态变更事件
     * @return 当前滑块控件
     */
    public DocumentSliderControl setValue(double value, boolean notify) {
        setValueInternal(value, notify, true, false);
        return this;
    }

    /**
     * 返回当前值。
     *
     * @return 当前值
     */
    public double getValue() {
        return value;
    }

    /**
     * 设置控件是否启用。
     *
     * @param enabled 是否启用
     * @return 当前滑块控件
     */
    public DocumentSliderControl setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return this;
        }
        this.enabled = enabled;
        if (!enabled) {
            dragging = false;
            focusVisible = false;
            hovered = false;
            element.setAttribute("aria-disabled", "true");
        } else {
            element.removeAttribute("aria-disabled");
        }
        element.setFocusable(enabled);
        updateVisualState();
        return this;
    }

    /**
     * 判断控件是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置滑块方向；首版仅支持水平。
     *
     * @param orientation 滑块方向；为 null 时使用水平
     * @return 当前滑块控件
     */
    public DocumentSliderControl setOrientation(UiSliderOrientation orientation) {
        this.orientation = orientation == null ? UiSliderOrientation.HORIZONTAL : orientation;
        updateVisualState();
        return this;
    }

    /**
     * 设置变更处理器。
     *
     * @param changeHandler 变更处理器；为 null 时清除
     * @return 当前滑块控件
     */
    public DocumentSliderControl setChangeHandler(DocumentSliderChangeHandler changeHandler) {
        this.changeHandler = changeHandler;
        return this;
    }

    /**
     * 设置轨道尺寸。
     *
     * @param width 轨道宽度
     * @param height 轨道高度
     * @return 当前滑块控件
     */
    public DocumentSliderControl setTrackSize(int width, int height) {
        this.trackWidth = Math.max(1, width);
        this.trackHeight = Math.max(1, height);
        configureElement();
        updateVisualState();
        return this;
    }

    /**
     * 设置滑块按钮尺寸。
     *
     * @param thumbSize 滑块按钮尺寸
     * @return 当前滑块控件
     */
    public DocumentSliderControl setThumbSize(int thumbSize) {
        this.thumbSize = Math.max(1, thumbSize);
        configureElement();
        updateVisualState();
        return this;
    }

    /**
     * 设置轨道颜色。
     *
     * @param normal 普通轨道颜色
     * @param fill 已填充颜色
     * @param disabled 禁用轨道颜色
     * @return 当前滑块控件
     */
    public DocumentSliderControl setTrackColors(int normal, int fill, int disabled) {
        this.trackColor = normal;
        this.fillColor = fill;
        this.trackDisabledColor = disabled;
        updateVisualState();
        return this;
    }

    /**
     * 设置滑块按钮颜色。
     *
     * @param normal 普通态颜色
     * @param hover 悬停态颜色
     * @param dragging 拖动态颜色
     * @param disabled 禁用态颜色
     * @return 当前滑块控件
     */
    public DocumentSliderControl setThumbColors(int normal, int hover, int dragging, int disabled) {
        this.thumbColor = normal;
        this.thumbHoverColor = hover;
        this.thumbDraggingColor = dragging;
        this.thumbDisabledColor = disabled;
        updateVisualState();
        return this;
    }

    /**
     * 设置键盘焦点描边颜色。
     *
     * @param focusBorderColor 键盘焦点描边颜色
     * @return 当前滑块控件
     */
    public DocumentSliderControl setFocusBorderColor(int focusBorderColor) {
        this.focusBorderColor = focusBorderColor;
        updateVisualState();
        return this;
    }

    private void configureElement() {
        element.setAttribute("role", "slider")
                .setAttribute("tabindex", "0");
        element.setFocusable(enabled);
        element.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setPosition(UiPosition.RELATIVE)
                .setWidth(UiStyleLength.px(trackWidth))
                .setHeight(UiStyleLength.px(thumbSize))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(999))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE)
                .setCursor(UiCursor.POINTER);
        int trackTop = Math.max(0, (thumbSize - trackHeight) / 2);
        trackElement.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(0))
                .setTop(UiStyleLength.px(trackTop))
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.px(trackHeight))
                .setBorderRadius(UiStyleLength.px(999))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        fillElement.style()
                .setHeight(UiStyleLength.percent(1.0F))
                .setBorderRadius(UiStyleLength.px(999));
        thumbElement.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(0))
                .setWidth(UiStyleLength.px(thumbSize))
                .setHeight(UiStyleLength.px(thumbSize))
                .setBorderRadius(UiStyleLength.px(999))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID);
    }

    private void installHandlers() {
        element.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                if (!enabled || event.getButton() != 0) {
                    return false;
                }
                setValueInternal(valueFromDocumentX(event.getDocumentX()), true, true, true);
                return true;
            }
        }).setHoverHandler(new DocumentElementHoverHandler() {
            @Override
            public boolean onHoverChanged(DocumentElementHoverEvent event) {
                hovered = event.isHovered() && enabled;
                updateVisualState();
                return false;
            }
        }).setFocusHandler(new DocumentElementFocusHandler() {
            @Override
            public void onFocusChanged(DocumentElementFocusEvent event) {
                focusVisible = event.isFocused() && event.isFocusVisible() && enabled;
                updateVisualState();
            }
        }).setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                if (!enabled || event.getAction() != UiKeyEvent.Action.PRESSED) {
                    return false;
                }
                return handleKey(event.getKeyCode());
            }
        }).setDragHandler(new DocumentElementDragHandler() {
            @Override
            public boolean onDrag(DocumentElementDragEvent event) {
                return handleDrag(event);
            }
        });
    }

    private boolean handleKey(int keyCode) {
        double delta = resolveKeyboardStep();
        if (keyCode == UiKeyCodes.KEY_LEFT || keyCode == UiKeyCodes.KEY_DOWN) {
            setValueInternal(value - delta, true, true, true);
            return true;
        }
        if (keyCode == UiKeyCodes.KEY_RIGHT || keyCode == UiKeyCodes.KEY_UP) {
            setValueInternal(value + delta, true, true, true);
            return true;
        }
        if (keyCode == UiKeyCodes.KEY_PRIOR) {
            setValueInternal(value + delta * 10.0D, true, true, true);
            return true;
        }
        if (keyCode == UiKeyCodes.KEY_NEXT) {
            setValueInternal(value - delta * 10.0D, true, true, true);
            return true;
        }
        if (keyCode == UiKeyCodes.KEY_HOME) {
            setValueInternal(min, true, true, true);
            return true;
        }
        if (keyCode == UiKeyCodes.KEY_END) {
            setValueInternal(max, true, true, true);
            return true;
        }
        return false;
    }

    private boolean handleDrag(DocumentElementDragEvent event) {
        if (!enabled) {
            return false;
        }
        if (event.getPhase() == DocumentElementDragEvent.DragPhase.START && event.getButton() != 0) {
            return false;
        }
        if (event.getPhase() == DocumentElementDragEvent.DragPhase.START) {
            dragging = false;
            dragStartValue = value;
            updateVisualState();
            return true;
        }
        if (event.getPhase() == DocumentElementDragEvent.DragPhase.DRAG) {
            dragging = true;
            double deltaRatio = (event.getDocumentX() - event.getStartDocumentX()) / (double) resolveTrackWidth();
            setValueInternal(dragStartValue + deltaRatio * (max - min), true, false, true);
            return true;
        }
        if (event.getPhase() == DocumentElementDragEvent.DragPhase.END) {
            boolean wasDragging = dragging;
            dragging = false;
            updateVisualState();
            if (wasDragging) {
                fireChange(true, true);
            }
            return wasDragging;
        }
        return false;
    }

    private void setValueInternal(double nextValue, boolean notify, boolean committing, boolean userTriggered) {
        double normalizedValue = normalizeValue(nextValue);
        boolean changed = Math.abs(value - normalizedValue) > EPSILON;
        value = normalizedValue;
        updateVisualState();
        if (notify && changed) {
            fireChange(committing, userTriggered);
        }
    }

    private double normalizeValue(double rawValue) {
        double clamped = Math.max(min, Math.min(rawValue, max));
        if (step <= 0.0D || max <= min) {
            return clamped;
        }
        double stepped = min + Math.round((clamped - min) / step) * step;
        return Math.max(min, Math.min(stepped, max));
    }

    private double valueFromDocumentX(int documentX) {
        DocumentElementBounds bounds = element.getDocumentBounds();
        int localX = bounds.isAvailable() ? documentX - bounds.getContentLeft() : documentX;
        double ratio = Math.max(0.0D, Math.min(localX / (double) resolveTrackWidth(), 1.0D));
        return min + ratio * (max - min);
    }

    private int resolveTrackWidth() {
        DocumentElementBounds bounds = element.getDocumentBounds();
        if (bounds.isAvailable() && bounds.getContentWidth() > 0) {
            return bounds.getContentWidth();
        }
        return trackWidth;
    }

    private double resolveKeyboardStep() {
        if (step > 0.0D) {
            return step;
        }
        double range = max - min;
        return range <= 0.0D ? 1.0D : range / 100.0D;
    }

    private void updateVisualState() {
        double range = max - min;
        double progress = range <= 0.0D ? 0.0D : (value - min) / range;
        progress = Math.max(0.0D, Math.min(progress, 1.0D));
        int resolvedTrackColor = enabled ? trackColor : trackDisabledColor;
        int resolvedFillColor = enabled ? fillColor : trackDisabledColor;
        int resolvedThumbColor;
        if (!enabled) {
            resolvedThumbColor = thumbDisabledColor;
        } else if (dragging) {
            resolvedThumbColor = thumbDraggingColor;
        } else if (hovered) {
            resolvedThumbColor = thumbHoverColor;
        } else {
            resolvedThumbColor = thumbColor;
        }
        element.style()
                .setBorderColor(focusVisible ? focusBorderColor : 0)
                .setCursor(enabled ? (dragging ? UiCursor.GRABBING : UiCursor.POINTER) : UiCursor.NOT_ALLOWED);
        trackElement.style().setBackgroundColor(resolvedTrackColor);
        fillElement.style()
                .setWidth(UiStyleLength.percent((float) progress))
                .setBackgroundColor(resolvedFillColor);
        thumbElement.style()
                .setLeft(UiStyleLength.calc((float) progress, -thumbSize / 2.0F))
                .setBackgroundColor(resolvedThumbColor)
                .setBorderColor(focusVisible ? focusBorderColor : resolvedThumbColor)
                .setCursor(enabled ? (dragging ? UiCursor.GRABBING : UiCursor.GRAB) : UiCursor.NOT_ALLOWED);
        element.setAttribute("aria-valuemin", Double.toString(min));
        element.setAttribute("aria-valuemax", Double.toString(max));
        element.setAttribute("aria-valuenow", Double.toString(value));
    }

    private void fireChange(boolean committing, boolean userTriggered) {
        if (changeHandler != null) {
            changeHandler.onSliderChanged(new DocumentSliderChangeEvent(this, element, value, committing,
                    userTriggered));
        }
    }
}
