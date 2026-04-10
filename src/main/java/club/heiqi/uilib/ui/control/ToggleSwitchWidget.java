package club.heiqi.uilib.ui.control;

import org.lwjglx.input.Keyboard;

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
        int textY = absoluteY + Math.max(4, (getHeight() - context.getTextLineHeight()) / 2);
        int trackWidth = 70;
        int trackHeight = 28;
        int trackX = absoluteX + getWidth() - trackWidth - 10;
        int trackY = absoluteY + (getHeight() - trackHeight) / 2;
        int thumbWidth = 30;
        int thumbX = checked ? trackX + trackWidth - thumbWidth - 3 : trackX + 3;

        context.drawText(label, absoluteX + 10, textY, 0xFFFFFFFF, true);
        context.fillRect(trackX, trackY, trackX + trackWidth, trackY + trackHeight, checked ? 0xCC3B6EA5 : 0xCC2D3139);
        context.drawBorder(trackX, trackY, trackX + trackWidth, trackY + trackHeight, focused ? 0xFFBFD7FF : (hovered ? 0xFFB3D1FF : 0xFF7D8CA3));
        context.fillRect(thumbX, trackY + 3, thumbX + thumbWidth, trackY + trackHeight - 3, checked ? 0xFFFFFFFF : 0xFFBFC7D6);
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
        return 320;
    }

    @Override
    public int getPreferredHeight() {
        return 42;
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
}
