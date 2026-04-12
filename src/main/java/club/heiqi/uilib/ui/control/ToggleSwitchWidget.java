package club.heiqi.uilib.ui.control;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 最小开关控件。
 */
public class ToggleSwitchWidget extends Widget {

    private String label;
    private boolean checked;
    private Runnable toggleHandler;
    private boolean hovered;
    private boolean focused;
    private UiControlTheme.ToggleSwitchStyle style = UiControlTheme.defaultToggleSwitchStyle();

    public ToggleSwitchWidget(String label) {
        this.label = label == null ? "" : label;
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        UiControlTheme.BoxState containerState = resolveContainerState();
        UiControlTheme.BoxState trackState = checked ? style.checkedTrackState : style.uncheckedTrackState;
        int accentColor = checked ? style.checkedAccentColor : style.uncheckedAccentColor;
        int textY = absoluteY + Math.max(3, (getHeight() - context.getTextLineHeight()) / 2);
        int trackX = absoluteX + getWidth() - style.trackWidth - style.contentPaddingRight;
        int trackY = absoluteY + (getHeight() - style.trackHeight) / 2;
        int thumbX = checked ? trackX + style.trackWidth - style.thumbWidth - 2 : trackX + 2;
        int textX = absoluteX + style.contentPaddingLeft;
        int textWidth = Math.max(0, trackX - textX - style.textTrackGap);

        context.fillRect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), containerState.fillColor);
        if (accentColor != 0 && style.accentInsetHeight > 0) {
            context.fillRect(absoluteX + 1, absoluteY + style.accentInsetTop, absoluteX + getWidth() - 1,
                    absoluteY + style.accentInsetTop + style.accentInsetHeight, accentColor);
        }
        context.drawBorder(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), containerState.borderColor);

        context.drawText(trimToVisibleText(label, textWidth), textX, textY,
                checked ? style.checkedTextColor : style.uncheckedTextColor, false);
        context.fillRect(trackX, trackY, trackX + style.trackWidth, trackY + style.trackHeight, trackState.fillColor);
        context.drawBorder(trackX, trackY, trackX + style.trackWidth, trackY + style.trackHeight, trackState.borderColor);
        context.fillRect(thumbX, trackY + 2, thumbX + style.thumbWidth, trackY + style.trackHeight - 2, style.thumbColor);
    }

    @Override
    public void onMouseEnter() {
        hovered = true;
    }

    @Override
    public void onMouseLeave() {
        hovered = false;
    }

    @Override
    public void onMouseUp(UiMouseEvent event) {
        if (event.getButton() != 0 || !contains(event.getMouseX(), event.getMouseY())) {
            return;
        }
        toggle();
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
        if (event.getKeyCode() == Keyboard.KEY_SPACE || event.getKeyCode() == Keyboard.KEY_RETURN
                || event.getKeyCode() == Keyboard.KEY_NUMPADENTER) {
            toggle();
        }
    }

    @Override
    public int getPreferredWidth() {
        return Math.max(style.preferredMinWidth,
                DefaultFontRendererAdapter.getInstance().getStringWidth(label) * 2 + style.preferredExtraWidth);
    }

    @Override
    public int getPreferredHeight() {
        return style.height;
    }

    @Override
    public int getMinContentWidth() {
        return Math.max(style.minContentWidthFloor,
                DefaultFontRendererAdapter.getInstance().getStringWidth(label) * 2 + style.minExtraWidth);
    }

    public ToggleSwitchWidget setChecked(boolean checked) {
        this.checked = checked;
        return this;
    }

    public boolean isChecked() {
        return checked;
    }

    public ToggleSwitchWidget setLabel(String label) {
        String normalizedLabel = label == null ? "" : label;
        if (!normalizedLabel.equals(this.label)) {
            this.label = normalizedLabel;
            requestLayout();
            return this;
        }
        this.label = normalizedLabel;
        return this;
    }

    public ToggleSwitchWidget setToggleHandler(Runnable toggleHandler) {
        this.toggleHandler = toggleHandler;
        return this;
    }

    /**
     * 设置开关样式。
     *
     * @param style 开关样式；为空时恢复默认样式
     * @return 当前开关
     */
    public ToggleSwitchWidget setStyle(UiControlTheme.ToggleSwitchStyle style) {
        this.style = style == null ? UiControlTheme.defaultToggleSwitchStyle() : style;
        requestLayout();
        return this;
    }

    private void toggle() {
        checked = !checked;
        if (toggleHandler != null) {
            toggleHandler.run();
        }
    }

    private String trimToVisibleText(String source, int uiWidth) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        DefaultFontRendererAdapter adapter = DefaultFontRendererAdapter.getInstance();
        int rawWidth = Math.max(1, Math.round(uiWidth / 2.0F));
        if (adapter.getStringWidth(source) <= rawWidth) {
            return source;
        }
        int ellipsisWidth = adapter.getStringWidth("...");
        String trimmed = adapter.trimStringToWidth(source, Math.max(0, rawWidth - ellipsisWidth));
        return trimmed.isEmpty() ? "..." : trimmed + "...";
    }

    /**
     * 解析当前容器视觉状态。
     *
     * @return 当前容器视觉状态
     */
    private UiControlTheme.BoxState resolveContainerState() {
        if (focused) {
            return style.focusedState;
        }
        if (hovered) {
            return style.hoveredState;
        }
        return style.normalState;
    }
}
