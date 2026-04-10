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

    public ToggleSwitchWidget(String label) {
        this.label = label;
    }

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        int fillColor = focused ? 0xDD131B26 : 0xD910151D;
        int borderColor = focused ? 0xFF89B4FF : (hovered ? 0xFF607697 : 0xFF35465D);
        int textY = absoluteY + Math.max(3, (getHeight() - context.getTextLineHeight()) / 2);
        int trackWidth = 46;
        int trackHeight = 22;
        int trackX = absoluteX + getWidth() - trackWidth - 10;
        int trackY = absoluteY + (getHeight() - trackHeight) / 2;
        int thumbWidth = 18;
        int thumbX = checked ? trackX + trackWidth - thumbWidth - 2 : trackX + 2;
        int textWidth = Math.max(0, trackX - absoluteX - 24);

        context.fillRect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), fillColor);
        context.fillRect(absoluteX + 1, absoluteY + 1, absoluteX + getWidth() - 1, absoluteY + 3, checked ? 0x223A84E5 : 0x1A607697);
        context.drawBorder(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), borderColor);

        context.drawText(trimToVisibleText(label, textWidth), absoluteX + 10, textY, checked ? 0xFFF3F7FF : 0xFFB8C5D8, false);
        context.fillRect(trackX, trackY, trackX + trackWidth, trackY + trackHeight, checked ? 0xFF2F78E6 : 0xFF2A3442);
        context.drawBorder(trackX, trackY, trackX + trackWidth, trackY + trackHeight, checked ? 0xFF8EC0FF : 0xFF53657D);
        context.fillRect(thumbX, trackY + 2, thumbX + thumbWidth, trackY + trackHeight - 2, 0xFFF7FAFF);
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
        return Math.max(184, DefaultFontRendererAdapter.getInstance().getStringWidth(label) * 2 + 84);
    }

    @Override
    public int getPreferredHeight() {
        return 38;
    }

    @Override
    public int getMinContentWidth() {
        return Math.max(124, DefaultFontRendererAdapter.getInstance().getStringWidth(label) * 2 + 74);
    }

    public ToggleSwitchWidget setChecked(boolean checked) {
        this.checked = checked;
        return this;
    }

    public boolean isChecked() {
        return checked;
    }

    public ToggleSwitchWidget setLabel(String label) {
        this.label = label;
        return this;
    }

    public ToggleSwitchWidget setToggleHandler(Runnable toggleHandler) {
        this.toggleHandler = toggleHandler;
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
}
