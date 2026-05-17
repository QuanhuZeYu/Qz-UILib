package club.heiqi.uilib.ui.dom.control;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiJustifyContent;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * 基于 HTML-like 元素实现的开关控件适配器。
 */
public final class DocumentToggleSwitchControl {

    private final ElementNode element;
    private final ElementNode thumb;
    private DocumentToggleChangeHandler changeHandler;
    private boolean toggled;
    private boolean enabled = true;
    private boolean focusVisible;
    private int trackWidth = 48;
    private int trackHeight = 24;
    private int thumbSize = 18;
    private int trackOffColor = 0xFF4A5568;
    private int trackOnColor = 0xFF38A169;
    private int trackDisabledColor = 0xFF333344;
    private int thumbColor = 0xFFFFFFFF;
    private int thumbDisabledColor = 0xFF888899;
    private int focusBorderColor = 0xFFBEE3F8;

    /**
     * 创建开关控件。
     *
     * @param document 所属 HTML-like 文档
     */
    public DocumentToggleSwitchControl(UiDocument document) {
        this.trackWidth = 48;
        this.element = document.div();
        this.thumb = document.div();
        element.append(thumb);
        configureElement();
        installHandlers();
        updateVisualState();
    }

    /**
     * 返回开关控件根元素。
     *
     * @return 开关控件根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回当前开关状态。
     *
     * @return 是否已切换为开
     */
    public boolean isToggled() {
        return toggled;
    }

    /**
     * 设置开关状态。
     *
     * @param toggled 开关状态
     * @return 当前开关控件
     */
    public DocumentToggleSwitchControl setToggled(boolean toggled) {
        if (this.toggled == toggled) {
            return this;
        }
        this.toggled = toggled;
        updateVisualState();
        return this;
    }

    /**
     * 设置开关是否启用。
     *
     * @param enabled 是否启用
     * @return 当前开关控件
     */
    public DocumentToggleSwitchControl setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return this;
        }
        this.enabled = enabled;
        if (!enabled) {
            focusVisible = false;
            element.setAttribute("aria-disabled", "true");
        } else {
            element.removeAttribute("aria-disabled");
        }
        element.setFocusable(enabled);
        updateVisualState();
        return this;
    }

    /**
     * 判断开关是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置开关变更处理器。
     *
     * @param changeHandler 开关变更处理器；为 null 时清除
     * @return 当前开关控件
     */
    public DocumentToggleSwitchControl setChangeHandler(DocumentToggleChangeHandler changeHandler) {
        this.changeHandler = changeHandler;
        return this;
    }

    /**
     * 设置轨道尺寸。
     *
     * @param width 轨道宽度
     * @param height 轨道高度
     * @return 当前开关控件
     */
    public DocumentToggleSwitchControl setTrackSize(int width, int height) {
        this.trackWidth = width;
        this.trackHeight = height;
        configureElement();
        updateVisualState();
        return this;
    }

    /**
     * 设置拇指尺寸。
     *
     * @param thumbSize 拇指尺寸
     * @return 当前开关控件
     */
    public DocumentToggleSwitchControl setThumbSize(int thumbSize) {
        this.thumbSize = thumbSize;
        configureElement();
        updateVisualState();
        return this;
    }

    /**
     * 设置轨道颜色。
     *
     * @param offColor 关闭态轨道颜色
     * @param onColor 开启态轨道颜色
     * @param disabledColor 禁用态轨道颜色
     * @return 当前开关控件
     */
    public DocumentToggleSwitchControl setTrackColors(int offColor, int onColor, int disabledColor) {
        this.trackOffColor = offColor;
        this.trackOnColor = onColor;
        this.trackDisabledColor = disabledColor;
        updateVisualState();
        return this;
    }

    /**
     * 设置拇指颜色。
     *
     * @param thumbColor 拇指颜色
     * @param disabledColor 禁用态拇指颜色
     * @return 当前开关控件
     */
    public DocumentToggleSwitchControl setThumbColors(int thumbColor, int disabledColor) {
        this.thumbColor = thumbColor;
        this.thumbDisabledColor = disabledColor;
        updateVisualState();
        return this;
    }

    /**
     * 设置键盘焦点描边颜色。
     *
     * @param focusBorderColor 键盘焦点描边颜色
     * @return 当前开关控件
     */
    public DocumentToggleSwitchControl setFocusBorderColor(int focusBorderColor) {
        this.focusBorderColor = focusBorderColor;
        updateVisualState();
        return this;
    }

    private void configureElement() {
        element.setAttribute("role", "switch")
                .setAttribute("tabindex", "0");
        element.setFocusable(enabled);
        element.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setWidth(UiStyleLength.px(trackWidth))
                .setHeight(UiStyleLength.px(trackHeight))
                .setPadding(UiStyleLength.px(2))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(999))
                .setBackgroundColor(trackOffColor)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        thumb.style()
                .setWidth(UiStyleLength.px(thumbSize))
                .setHeight(UiStyleLength.px(thumbSize))
                .setBorderRadius(UiStyleLength.px(999))
                .setBackgroundColor(thumbColor);
    }

    private void installHandlers() {
        element.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                if (event.getButton() != 0) {
                    return false;
                }
                toggle(false);
                return true;
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
                if (!isActivationKey(event.getKeyCode())) {
                    return false;
                }
                if (event.getAction() == UiKeyEvent.Action.PRESSED) {
                    if (enabled) {
                        toggle(true);
                    }
                    return true;
                }
                return true;
            }
        });
    }

    private void toggle(boolean keyboardTriggered) {
        if (!enabled) {
            return;
        }
        toggled = !toggled;
        updateVisualState();
        fireChange();
    }

    private void updateVisualState() {
        int trackColor;
        int resolvedThumbColor;
        if (!enabled) {
            trackColor = trackDisabledColor;
            resolvedThumbColor = thumbDisabledColor;
        } else if (toggled) {
            trackColor = trackOnColor;
            resolvedThumbColor = thumbColor;
        } else {
            trackColor = trackOffColor;
            resolvedThumbColor = thumbColor;
        }
        element.style()
                .setBackgroundColor(trackColor)
                .setBorderColor(focusVisible ? focusBorderColor : 0)
                .setJustifyContent(toggled ? UiJustifyContent.END : UiJustifyContent.START);
        element.setAttribute("aria-checked", String.valueOf(toggled));
        thumb.style().setBackgroundColor(resolvedThumbColor);
    }

    private void fireChange() {
        if (changeHandler != null) {
            changeHandler.onToggleChanged(new DocumentToggleChangeEvent(this, element, toggled));
        }
    }

    private static boolean isActivationKey(int keyCode) {
        return keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER || keyCode == Keyboard.KEY_SPACE;
    }
}
