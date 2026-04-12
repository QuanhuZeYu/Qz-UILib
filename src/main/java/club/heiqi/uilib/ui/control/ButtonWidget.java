package club.heiqi.uilib.ui.control;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 最小按钮控件。
 */
public class ButtonWidget extends Widget {

    private String text;
    private Runnable clickHandler;
    private boolean hovered;
    private boolean pressed;
    private boolean focused;
    private UiControlTheme.ButtonStyle style = UiControlTheme.defaultButtonStyle();

    /**
     * 使用文本创建按钮。
     *
     * @param text 按钮文本
     */
    public ButtonWidget(String text) {
        this.text = text == null ? "" : text;
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        UiControlTheme.BoxState state = resolveVisualState();

        context.fillRect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), state.fillColor);
        if (state.accentColor != 0 && style.accentInsetHeight > 0) {
            context.fillRect(absoluteX + 1, absoluteY + style.accentInsetTop, absoluteX + getWidth() - 1,
                    absoluteY + style.accentInsetTop + style.accentInsetHeight, state.accentColor);
        }
        context.drawBorder(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), state.borderColor);
        int textY = absoluteY + Math.max(3, (getHeight() - context.getTextLineHeight()) / 2);
        context.drawCenteredText(text, absoluteX + (getWidth() / 2), textY, style.textColor, false);
    }

    @Override
    public void onMouseEnter() {
        hovered = true;
    }

    @Override
    public void onMouseLeave() {
        hovered = false;
        pressed = false;
    }

    @Override
    public void onMouseDown(UiMouseEvent event) {
        if (event.getButton() == 0) {
            pressed = true;
        }
    }

    @Override
    public void onMouseUp(UiMouseEvent event) {
        boolean shouldClick = pressed && event.getButton() == 0 && contains(event.getMouseX(), event.getMouseY());
        pressed = false;
        if (shouldClick) {
            triggerClick();
        }
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public void onFocusChanged(boolean focused) {
        this.focused = focused;
    }

    @Override
    public void onKeyEvent(UiKeyEvent event) {
        if (event.getAction() != UiKeyEvent.Action.PRESSED) {
            return;
        }
        if (event.getKeyCode() == Keyboard.KEY_RETURN || event.getKeyCode() == Keyboard.KEY_NUMPADENTER
                || event.getKeyCode() == Keyboard.KEY_SPACE) {
            triggerClick();
        }
    }

    public ButtonWidget setText(String text) {
        String normalizedText = text == null ? "" : text;
        if (!normalizedText.equals(this.text)) {
            this.text = normalizedText;
            requestLayout();
            return this;
        }
        this.text = normalizedText;
        return this;
    }

    public ButtonWidget setClickHandler(Runnable clickHandler) {
        this.clickHandler = clickHandler;
        return this;
    }

    /**
     * 设置按钮样式。
     *
     * @param style 按钮样式；为空时恢复默认样式
     * @return 当前按钮
     */
    public ButtonWidget setStyle(UiControlTheme.ButtonStyle style) {
        this.style = style == null ? UiControlTheme.defaultButtonStyle() : style;
        requestLayout();
        return this;
    }

    @Override
    public int getPreferredWidth() {
        return Math.max(style.preferredMinWidth,
                DefaultFontRendererAdapter.getInstance().getStringWidth(text) * 2 + style.preferredExtraWidth);
    }

    @Override
    public int getPreferredHeight() {
        return style.height;
    }

    @Override
    public int getMinContentWidth() {
        return Math.max(style.minContentWidthFloor,
                DefaultFontRendererAdapter.getInstance().getStringWidth(text) * 2 + style.minExtraWidth);
    }

    protected void triggerClick() {
        if (clickHandler != null) {
            clickHandler.run();
        }
    }

    /**
     * 解析当前按钮状态对应的视觉样式。
     *
     * @return 当前视觉状态
     */
    protected UiControlTheme.BoxState resolveVisualState() {
        if (pressed) {
            return style.pressedState;
        }
        if (focused) {
            return style.focusedState;
        }
        if (hovered) {
            return style.hoveredState;
        }
        return style.normalState;
    }
}
