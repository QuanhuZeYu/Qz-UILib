package club.heiqi.uilib.ui.control;

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

    public SegmentedSelectorWidget(String... options) {
        this.options = options == null ? new String[0] : options;
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        int optionCount = Math.max(1, options.length);
        int optionWidth = Math.max(1, getWidth() / optionCount);
        int textY = absoluteY + Math.max(4, (getHeight() - context.getTextLineHeight()) / 2);

        for (int i = 0; i < optionCount; i++) {
            int left = absoluteX + i * optionWidth;
            int right = i == optionCount - 1 ? absoluteX + getWidth() : left + optionWidth;
            boolean selected = i == selectedIndex;
            context.fillRect(left, absoluteY, right, absoluteY + getHeight(), selected ? 0xCC436099 : 0xCC252B33);
            context.drawBorder(left, absoluteY, right, absoluteY + getHeight(), selected ? 0xFFB7D4FF : 0xFF6E7C95);
            String option = i < options.length ? options[i] : "";
            context.drawCenteredText(option, left + (right - left) / 2, textY, 0xFFFFFFFF, true);
        }
    }

    @Override
    public void onMouseUp(UiMouseEvent event) {
        if (event.getButton() != 0 || !contains(event.getMouseX(), event.getMouseY()) || options.length == 0) {
            return;
        }
        int localX = event.getMouseX() - getAbsoluteX();
        int optionWidth = Math.max(1, getWidth() / options.length);
        int newIndex = Math.min(options.length - 1, Math.max(0, localX / optionWidth));
        if (newIndex != selectedIndex) {
            selectedIndex = newIndex;
            if (changeHandler != null) {
                changeHandler.run();
            }
        }
    }

    @Override
    public int getPreferredWidth() {
        return Math.max(260, options.length * 140);
    }

    @Override
    public int getPreferredHeight() {
        return 42;
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
}
