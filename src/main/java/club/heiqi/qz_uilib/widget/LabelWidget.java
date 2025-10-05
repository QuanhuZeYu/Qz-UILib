package club.heiqi.qz_uilib.widget;

import club.heiqi.qz_fontrender.fontsystem.impl.ReplaceFontRender;
import club.heiqi.qz_uilib.widget.layout.VerticalLayout;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import java.util.Set;

public class LabelWidget extends Widget {

    public String text = "";
    public float textSize = 32;
    public int textColor = 0xffffffff;
    public static final String[] ALIGN = {"center", "left"};
    public String align = ALIGN[0];
    public boolean isHover = false;

    public LabelWidget() {
        super();
        height = textSize;
    }

    public LabelWidget setText(String text) {
        this.text = text;
        fontRenderer.setCharSize(textSize);
        width = fontRenderer.getStringWidth(text);
        height = textSize;
        this.setPerfectSize(width,height);
        this.setSize(width,height);
        return this;
    }
    public LabelWidget setTextSize(float size) {
        this.textSize = size;
        height = size;
        return this;
    }
    public LabelWidget setTextColor(int color) {
        this.textColor = color;
        return this;
    }
    public LabelWidget setCenterAlign() {
        align = ALIGN[1];
        return this;
    }
    public LabelWidget setLeftAlign() {
        align = ALIGN[0];
        return this;
    }
    @Override
    public LabelWidget setPerfectSize(float width, float height) {
        super.setPerfectSize(width, height);
        return this;
    }

    @Override
    public void drawSelf() {
        super.drawSelf();
        fontRenderer.setCharSize(textSize);
        float stringWidth = fontRenderer.getStringWidth(text);
        float x = this.x, y = this.y;
        x = this.x;
        y = this.y + (height / 2) - (textSize / 2);
        if (align.equals(ALIGN[0])) {
            x = this.x;
            y = this.y + (height / 2) - (textSize / 2);
        }
        else if (align.equals(ALIGN[1])) {
            x = this.x + (width / 2) - (stringWidth / 2);
            y = this.y + (height / 2) - (textSize / 2);
        }
        fontRenderer.drawString(text, (int) x, (int) y,textColor);

        if (isHover)
            renderTooltip(Mouse.getX(), Display.getHeight() - Mouse.getY());
    }

    @Override
    public void onDragged(float newX, float newY, Set<Integer> clicked) {
        super.onDragged(newX, newY, clicked);
    }

    @Override
    public void onHover(float x, float y) {
        super.onHover(x, y);
        isHover = true;
    }

    @Override
    public void onLeave(float x, float y) {
        super.onLeave(x, y);
        isHover = false;
    }

    @Override
    public void onPress(float x, float y, int buttonID) {
        super.onPress(x, y, buttonID);
    }

    @Override
    public void onRelease(float x, float y, int buttonID) {
        super.onRelease(x, y, buttonID);
    }
}
