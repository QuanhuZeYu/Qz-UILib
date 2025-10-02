package club.heiqi.qz_uilib.widget;

import club.heiqi.qz_fontrender.fontsystem.impl.ReplaceFontRender;

import java.util.Set;

public class LabelWidget extends Widget {

    public String text = "";

    public LabelWidget() {
        super();
        height = 64;
    }

    public LabelWidget setText(String text) {
        this.text = text;
        width = fontRenderer.getStringWidth(text);
        return this;
    }

    @Override
    public void drawSelf() {
        super.drawSelf();
        ((ReplaceFontRender)fontRenderer).setCharSize(64);
        fontRenderer.drawString(text, (int) x, (int) y,0xffffffff);
    }

    @Override
    public void onDragged(float newX, float newY, Set<Integer> clicked) {
        super.onDragged(newX, newY, clicked);
    }

    @Override
    public void onHover(float x, float y) {
        super.onHover(x, y);
    }

    @Override
    public void onLeave(float x, float y) {
        super.onLeave(x, y);
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
