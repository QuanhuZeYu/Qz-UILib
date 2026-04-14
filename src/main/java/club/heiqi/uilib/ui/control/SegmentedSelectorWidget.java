package club.heiqi.uilib.ui.control;

import java.util.Objects;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 最小分段选择控件。
 */
public class SegmentedSelectorWidget extends Widget {

    private final TextMeasureService textMeasureService;
    private final String[] options;
    private int selectedIndex;
    private Runnable changeHandler;
    private boolean focused;
    private int hoveredIndex = -1;
    private UiControlTheme.SegmentedSelectorStyle style;

    public SegmentedSelectorWidget(UiControlTheme.SegmentedSelectorStyle style, String... options) {
        this(DefaultTextMeasureService.getInstance(), style, options);
    }

    public SegmentedSelectorWidget(TextMeasureService textMeasureService,
            UiControlTheme.SegmentedSelectorStyle style, String... options) {
        this.textMeasureService = Objects.requireNonNull(textMeasureService, "textMeasureService");
        this.options = options == null ? new String[0] : options;
        this.style = Objects.requireNonNull(style, "style");
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        int optionCount = Math.max(1, options.length);
        int optionWidth = Math.max(1, getWidth() / optionCount);
        int textY = absoluteY + Math.max(3, (getHeight() - context.getTextLineHeight()) / 2);
        UiControlTheme.BoxState containerState = focused ? style.focusedState : style.normalState;

        context.fillRect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), containerState.fillColor);
        if (containerState.accentColor != 0) {
            context.fillRect(absoluteX + 1, absoluteY + 1, absoluteX + getWidth() - 1, absoluteY + 3,
                    containerState.accentColor);
        }
        context.drawBorder(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), containerState.borderColor);

        for (int i = 0; i < optionCount; i++) {
            int left = absoluteX + i * optionWidth;
            int right = i == optionCount - 1 ? absoluteX + getWidth() : left + optionWidth;
            boolean selected = i == selectedIndex;
            int segmentLeft = i == 0 ? left + style.segmentInset : left + 1;
            int segmentRight = i == optionCount - 1 ? right - style.segmentInset : right - 1;
            if (i > 0) {
                context.fillRect(left, absoluteY + style.dividerInset, left + 1, absoluteY + getHeight() - style.dividerInset,
                        style.dividerColor);
            }
            if (selected) {
                context.fillRect(segmentLeft, absoluteY + style.segmentInset, segmentRight,
                        absoluteY + getHeight() - style.segmentInset,
                        focused ? style.focusedSelectedFillColor : style.selectedFillColor);
            } else if (i == hoveredIndex) {
                context.fillRect(segmentLeft, absoluteY + style.segmentInset, segmentRight,
                        absoluteY + getHeight() - style.segmentInset, style.hoveredSegmentFillColor);
            }
            String option = i < options.length && options[i] != null ? options[i] : "";
            context.drawCenteredText(trimToVisibleText(option, Math.max(1, segmentRight - segmentLeft - style.segmentTextPadding)),
                    left + (right - left) / 2, textY,
                    selected ? style.selectedTextColor : (i == hoveredIndex ? style.hoveredTextColor : style.textColor), false);
        }
    }

    @Override
    public void onMouseEnter() {
        hoveredIndex = -1;
    }

    @Override
    public void onMouseLeave() {
        hoveredIndex = -1;
    }

    @Override
    public void onMouseMove(UiMouseEvent event) {
        hoveredIndex = resolveOptionIndex(event.getMouseX(), event.getMouseY());
    }

    @Override
    public void onMouseUp(UiMouseEvent event) {
        if (event.getButton() != 0 || !contains(event.getMouseX(), event.getMouseY()) || options.length == 0) {
            return;
        }
        int localX = event.getMouseX() - getAbsoluteX();
        int optionWidth = Math.max(1, getWidth() / options.length);
        int newIndex = Math.min(options.length - 1, Math.max(0, localX / optionWidth));
        applySelection(newIndex);
    }

    @Override
    public boolean isFocusable() {
        return options.length > 0;
    }

    @Override
    public void onFocusChanged(boolean focused) {
        this.focused = focused;
    }

    @Override
    public void onKeyEvent(UiKeyEvent event) {
        if (options.length == 0 || event.getAction() != UiKeyEvent.Action.PRESSED) {
            return;
        }
        if (event.getKeyCode() == Keyboard.KEY_LEFT) {
            applySelection(Math.max(0, selectedIndex - 1));
        } else if (event.getKeyCode() == Keyboard.KEY_RIGHT) {
            applySelection(Math.min(options.length - 1, selectedIndex + 1));
        } else if (event.getKeyCode() == Keyboard.KEY_SPACE || event.getKeyCode() == Keyboard.KEY_RETURN
                || event.getKeyCode() == Keyboard.KEY_NUMPADENTER) {
            applySelection((selectedIndex + 1) % options.length);
        }
    }

    @Override
    public int getPreferredWidth() {
        return Math.max(style.preferredMinWidth, options.length * (getWidestOptionWidth() + style.preferredOptionExtraWidth));
    }

    @Override
    public int getPreferredHeight() {
        return style.height;
    }

    @Override
    public int getMinContentWidth() {
        return Math.max(style.minContentWidthFloor, options.length * (getWidestOptionWidth() + style.minOptionExtraWidth));
    }

    public SegmentedSelectorWidget setSelectedIndex(int selectedIndex) {
        this.selectedIndex = options.length == 0 ? 0 : Math.max(0, Math.min(options.length - 1, selectedIndex));
        return this;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public String getSelectedOption() {
        return options.length == 0 ? "" : options[selectedIndex] == null ? "" : options[selectedIndex];
    }

    public SegmentedSelectorWidget setChangeHandler(Runnable changeHandler) {
        this.changeHandler = changeHandler;
        return this;
    }

    /**
     * 设置分段选择器样式。
     *
     * @param style 分段选择器样式；为空时恢复默认样式
     * @return 当前选择器
     */
    public SegmentedSelectorWidget setStyle(UiControlTheme.SegmentedSelectorStyle style) {
        this.style = Objects.requireNonNull(style, "style");
        requestLayout();
        return this;
    }

    private void applySelection(int newIndex) {
        if (newIndex == selectedIndex) {
            return;
        }
        selectedIndex = newIndex;
        if (changeHandler != null) {
            changeHandler.run();
        }
    }

    private int resolveOptionIndex(int mouseX, int mouseY) {
        if (options.length == 0 || !contains(mouseX, mouseY)) {
            return -1;
        }
        int localX = mouseX - getAbsoluteX();
        int optionWidth = Math.max(1, getWidth() / options.length);
        return Math.min(options.length - 1, Math.max(0, localX / optionWidth));
    }

    private int getWidestOptionWidth() {
        int widest = 52;
        for (String option : options) {
            widest = Math.max(widest, textMeasureService.getStringWidth(option == null ? "" : option) * 2);
        }
        return widest;
    }

    private String trimToVisibleText(String source, int uiWidth) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        int rawWidth = Math.max(1, Math.round(uiWidth / 2.0F));
        if (textMeasureService.getStringWidth(source) <= rawWidth) {
            return source;
        }
        int ellipsisWidth = textMeasureService.getStringWidth("...");
        String trimmed = textMeasureService.trimStringToWidth(source, Math.max(0, rawWidth - ellipsisWidth));
        return trimmed.isEmpty() ? "..." : trimmed + "...";
    }
}
