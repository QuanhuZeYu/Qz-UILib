package club.heiqi.uilib.ui.control;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 最小分段选择控件。
 */
public class SegmentedSelectorWidget extends Widget {

    private final String[] options;
    private int selectedIndex;
    private Runnable changeHandler;
    private boolean focused;
    private int hoveredIndex = -1;

    public SegmentedSelectorWidget(String... options) {
        this.options = options == null ? new String[0] : options;
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        int optionCount = Math.max(1, options.length);
        int optionWidth = Math.max(1, getWidth() / optionCount);
        int textY = absoluteY + Math.max(3, (getHeight() - context.getTextLineHeight()) / 2);

        context.fillRect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), 0xD910151D);
        context.fillRect(absoluteX + 1, absoluteY + 1, absoluteX + getWidth() - 1, absoluteY + 3,
                focused ? 0x337EB1FF : 0x1A607697);
        context.drawBorder(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(),
                focused ? 0xFF89B4FF : 0xFF35465D);

        for (int i = 0; i < optionCount; i++) {
            int left = absoluteX + i * optionWidth;
            int right = i == optionCount - 1 ? absoluteX + getWidth() : left + optionWidth;
            boolean selected = i == selectedIndex;
            int segmentLeft = i == 0 ? left + 2 : left + 1;
            int segmentRight = i == optionCount - 1 ? right - 2 : right - 1;
            if (i > 0) {
                context.fillRect(left, absoluteY + 6, left + 1, absoluteY + getHeight() - 6, 0xFF2E3A4A);
            }
            if (selected) {
                context.fillRect(segmentLeft, absoluteY + 2, segmentRight, absoluteY + getHeight() - 2,
                        focused ? 0xFF315E94 : 0xFF2B4F7C);
            } else if (i == hoveredIndex) {
                context.fillRect(segmentLeft, absoluteY + 2, segmentRight, absoluteY + getHeight() - 2, 0x332F435D);
            }
            String option = i < options.length ? options[i] : "";
            context.drawCenteredText(trimToVisibleText(option, Math.max(1, segmentRight - segmentLeft - 12)),
                    left + (right - left) / 2, textY, selected ? 0xFFF7FAFF : (i == hoveredIndex ? 0xFFE4ECFF : 0xFFB7C3D6), false);
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
        return Math.max(220, options.length * (getWidestOptionWidth() + 28));
    }

    @Override
    public int getPreferredHeight() {
        return 38;
    }

    @Override
    public int getMinContentWidth() {
        return Math.max(140, options.length * (getWidestOptionWidth() + 16));
    }

    public SegmentedSelectorWidget setSelectedIndex(int selectedIndex) {
        this.selectedIndex = options.length == 0 ? 0 : Math.max(0, Math.min(options.length - 1, selectedIndex));
        return this;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public String getSelectedOption() {
        return options.length == 0 ? "" : options[selectedIndex];
    }

    public SegmentedSelectorWidget setChangeHandler(Runnable changeHandler) {
        this.changeHandler = changeHandler;
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
        DefaultFontRendererAdapter adapter = DefaultFontRendererAdapter.getInstance();
        for (String option : options) {
            widest = Math.max(widest, adapter.getStringWidth(option) * 2);
        }
        return widest;
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
}
