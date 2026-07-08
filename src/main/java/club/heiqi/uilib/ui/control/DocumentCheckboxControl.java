package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.base.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 基于 HTML-like 元素实现的复选框控件。
 */
public final class DocumentCheckboxControl {

    private final ElementNode element;
    private final ElementNode boxElement;
    private final ElementNode checkmarkElement;
    private final TextNode checkmarkText;
    private final ElementNode labelElement;
    private final TextNode labelText;
    private DocumentCheckboxChangeHandler changeHandler;
    private boolean checked;
    private boolean indeterminate;
    private boolean enabled = true;
    private boolean focusVisible;
    private boolean spacePressed;
    private boolean hovered;
    private int boxNormalColor = 0xFF1F2937;
    private int boxHoverColor = 0xFF374151;
    private int boxCheckedColor = 0xFF2563EB;
    private int boxDisabledColor = 0xFF334155;
    private int checkmarkColor = 0xFFFFFFFF;
    private int labelColor = 0xFFE5E7EB;
    private int labelDisabledColor = 0xFF64748B;
    private int focusBorderColor = 0xFFBFDBFE;
    private int boxSize = 16;
    private int boxCornerRadius = 3;

    /**
     * 创建无标签复选框控件。
     *
     * @param document 所属 HTML-like 文档
     */
    public DocumentCheckboxControl(UiDocument document) {
        this(document, "");
    }

    /**
     * 创建带标签复选框控件。
     *
     * @param document 所属 HTML-like 文档
     * @param label 标签文本
     */
    public DocumentCheckboxControl(UiDocument document, String label) {
        this.element = document.div();
        this.boxElement = document.div();
        this.checkmarkElement = document.span();
        this.checkmarkText = checkmarkElement.appendText("✓");
        this.labelElement = document.span();
        this.labelText = labelElement.appendText(normalizeLabel(label));
        boxElement.append(checkmarkElement);
        element.append(boxElement);
        element.append(labelElement);
        configureElement();
        installHandlers();
        updateVisualState();
    }

    /**
     * 返回复选框控件根元素。
     *
     * @return 复选框控件根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回标签文本。
     *
     * @return 标签文本
     */
    public String getLabel() {
        return labelText.getText();
    }

    /**
     * 设置标签文本。
     *
     * @param label 标签文本
     * @return 当前复选框控件
     */
    public DocumentCheckboxControl setLabel(String label) {
        labelText.setText(normalizeLabel(label));
        updateVisualState();
        return this;
    }

    /**
     * 设置是否选中，默认不触发变更事件。
     *
     * @param checked 是否选中
     * @return 当前复选框控件
     */
    public DocumentCheckboxControl setChecked(boolean checked) {
        return setChecked(checked, false);
    }

    /**
     * 设置是否选中。
     *
     * @param checked 是否选中
     * @param notify 是否触发变更事件
     * @return 当前复选框控件
     */
    public DocumentCheckboxControl setChecked(boolean checked, boolean notify) {
        if (this.checked == checked) {
            return this;
        }
        this.checked = checked;
        updateVisualState();
        if (notify) {
            fireChange();
        }
        return this;
    }

    /**
     * 判断是否选中。
     *
     * @return 是否选中
     */
    public boolean isChecked() {
        return checked;
    }

    /**
     * 设置半选状态。
     *
     * @param indeterminate 是否半选
     * @return 当前复选框控件
     */
    public DocumentCheckboxControl setIndeterminate(boolean indeterminate) {
        if (this.indeterminate == indeterminate) {
            return this;
        }
        this.indeterminate = indeterminate;
        updateVisualState();
        return this;
    }

    /**
     * 判断是否处于半选状态。
     *
     * @return 是否半选
     */
    public boolean isIndeterminate() {
        return indeterminate;
    }

    /**
     * 设置控件是否启用。
     *
     * @param enabled 是否启用
     * @return 当前复选框控件
     */
    public DocumentCheckboxControl setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return this;
        }
        this.enabled = enabled;
        if (!enabled) {
            focusVisible = false;
            spacePressed = false;
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
     * 设置复选框变更处理器。
     *
     * @param changeHandler 复选框变更处理器；为 null 时清除
     * @return 当前复选框控件
     */
    public DocumentCheckboxControl setChangeHandler(DocumentCheckboxChangeHandler changeHandler) {
        this.changeHandler = changeHandler;
        return this;
    }

    /**
     * 设置方框颜色。
     *
     * @param normal 普通态颜色
     * @param hover 悬停态颜色
     * @param checked 选中态颜色
     * @param disabled 禁用态颜色
     * @return 当前复选框控件
     */
    public DocumentCheckboxControl setBoxColors(int normal, int hover, int checked, int disabled) {
        this.boxNormalColor = normal;
        this.boxHoverColor = hover;
        this.boxCheckedColor = checked;
        this.boxDisabledColor = disabled;
        updateVisualState();
        return this;
    }

    /**
     * 设置对勾颜色。
     *
     * @param checkmarkColor 对勾颜色
     * @return 当前复选框控件
     */
    public DocumentCheckboxControl setCheckmarkColor(int checkmarkColor) {
        this.checkmarkColor = checkmarkColor;
        updateVisualState();
        return this;
    }

    /**
     * 设置标签文本颜色。
     *
     * @param normal 普通态文本颜色
     * @param disabled 禁用态文本颜色
     * @return 当前复选框控件
     */
    public DocumentCheckboxControl setLabelColors(int normal, int disabled) {
        this.labelColor = normal;
        this.labelDisabledColor = disabled;
        updateVisualState();
        return this;
    }

    /**
     * 设置键盘焦点描边颜色。
     *
     * @param focusBorderColor 键盘焦点描边颜色
     * @return 当前复选框控件
     */
    public DocumentCheckboxControl setFocusBorderColor(int focusBorderColor) {
        this.focusBorderColor = focusBorderColor;
        updateVisualState();
        return this;
    }

    /**
     * 设置方框尺寸（同时作为方框宽度和高度，单位 px）。
     *
     * <p>非正值会被夹取为最小 1px。</p>
     *
     * @param sizePx 方框边长（像素）
     * @return 当前复选框控件
     */
    public DocumentCheckboxControl setBoxSize(int sizePx) {
        int resolved = sizePx <= 0 ? 1 : sizePx;
        if (this.boxSize == resolved) {
            return this;
        }
        this.boxSize = resolved;
        boxElement.style()
                .setWidth(UiStyleLength.px(resolved))
                .setHeight(UiStyleLength.px(resolved));
        return this;
    }

    /**
     * 设置方框圆角半径（单位 px）。
     *
     * <p>负值会被夹取为 0。</p>
     *
     * @param radiusPx 方框圆角半径（像素）
     * @return 当前复选框控件
     */
    public DocumentCheckboxControl setBoxCornerRadius(int radiusPx) {
        int resolved = radiusPx < 0 ? 0 : radiusPx;
        if (this.boxCornerRadius == resolved) {
            return this;
        }
        this.boxCornerRadius = resolved;
        boxElement.style().setBorderRadius(UiStyleLength.px(resolved));
        return this;
    }

    private void configureElement() {
        element.setAttribute("role", "checkbox")
                .setAttribute("tabindex", "0");
        element.setFocusable(enabled);
        element.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8))
                .setCursor(UiCursor.POINTER)
                .setTextColor(labelColor);
        boxElement.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setWidth(UiStyleLength.px(boxSize))
                .setHeight(UiStyleLength.px(boxSize))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(boxCornerRadius))
                .setBackgroundColor(boxNormalColor);
        checkmarkElement.style()
                .setDisplay(UiDisplay.NONE)
                .setTextColor(checkmarkColor);
        labelElement.style().setTextColor(labelColor);
    }

    private void installHandlers() {
        element.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                if (event.getButton() != 0) {
                    return false;
                }
                toggle();
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
                if (!event.isFocused()) {
                    spacePressed = false;
                }
                updateVisualState();
            }
        }).setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                if (!isActivationKey(event.getKeyCode())) {
                    return false;
                }
                if (isEnterKey(event.getKeyCode()) && event.getAction() == UiKeyEvent.Action.PRESSED) {
                    if (enabled) {
                        toggle();
                    }
                    return true;
                }
                if (event.getKeyCode() == UiKeyCodes.KEY_SPACE && event.getAction() == UiKeyEvent.Action.PRESSED) {
                    spacePressed = enabled;
                    return true;
                }
                if (event.getKeyCode() == UiKeyCodes.KEY_SPACE && event.getAction() == UiKeyEvent.Action.RELEASED) {
                    boolean shouldToggle = spacePressed && enabled;
                    spacePressed = false;
                    if (shouldToggle) {
                        toggle();
                    }
                    return true;
                }
                return true;
            }
        });
    }

    private void toggle() {
        if (!enabled) {
            return;
        }
        if (indeterminate) {
            indeterminate = false;
        }
        checked = !checked;
        updateVisualState();
        fireChange();
    }

    private void updateVisualState() {
        int resolvedBoxColor;
        if (!enabled) {
            resolvedBoxColor = boxDisabledColor;
        } else if (checked || indeterminate) {
            resolvedBoxColor = boxCheckedColor;
        } else if (hovered) {
            resolvedBoxColor = boxHoverColor;
        } else {
            resolvedBoxColor = boxNormalColor;
        }
        boxElement.style()
                .setBackgroundColor(resolvedBoxColor)
                .setBorderColor(focusVisible ? focusBorderColor : resolvedBoxColor);
        checkmarkText.setText(indeterminate ? "-" : "✓");
        checkmarkElement.style()
                .setDisplay((checked || indeterminate) ? UiDisplay.FLEX : UiDisplay.NONE)
                .setTextColor(checkmarkColor);
        labelElement.style().setTextColor(enabled ? labelColor : labelDisabledColor);
        element.style()
                .setCursor(enabled ? UiCursor.POINTER : UiCursor.NOT_ALLOWED)
                .setTextColor(enabled ? labelColor : labelDisabledColor);
        element.setAttribute("aria-checked", indeterminate ? "mixed" : String.valueOf(checked));
    }

    private void fireChange() {
        if (changeHandler != null) {
            changeHandler.onCheckboxChanged(new DocumentCheckboxChangeEvent(this, element, checked, indeterminate));
        }
    }

    private static boolean isActivationKey(int keyCode) {
        return isEnterKey(keyCode) || keyCode == UiKeyCodes.KEY_SPACE;
    }

    private static boolean isEnterKey(int keyCode) {
        return keyCode == UiKeyCodes.KEY_RETURN || keyCode == UiKeyCodes.KEY_NUMPADENTER;
    }

    private static String normalizeLabel(String label) {
        return label == null ? "" : label;
    }
}
