package club.heiqi.qz_uilib.skija.gui.component.defaultStyle;

import aurelienribon.tweenengine.TweenAccessor;
import club.heiqi.qz_uilib.skija.alignment.StringAlignUtils;
import club.heiqi.qz_uilib.skija.font.FontLoader;
import club.heiqi.qz_uilib.skija.gui.component.UIComponent;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import org.joml.Vector2f;
import org.lwjgl.opengl.Display;

import java.awt.*;

public class Label extends UIComponent {
    public String text;
    public StringAlignUtils.Align align = StringAlignUtils.Align.CENTER_TO_TARGET; // 默认中心对齐
    public int defaultStrColor = 0xFFFFFFFF;

    public int defaultHoverStrColor = 0xFFFFFFFF;

    public int strColor = 0xFFFFFFFF;

    public boolean useUnderline = false;
    public boolean useBold = false;

    /**
     * 默认使用左上角坐标，四个参数均为归一化值
     * @param x
     * @param y
     * @param width 可省略或为0
     * @param height 可省略或为0
     */
    public Label(float x, float y, float width, float height) {
        super(x, y, width, height);
    }
    public Label(float x,float y) {
        super(x,y,0,0);
    }

    public Label setText(String text) {this.text = text; return this;}
    public Label setStrColor(int color) {this.strColor = color; return this;}
    public Label setUnderLine(boolean underline) {this.useUnderline = underline; return this;}
    public Label setBold(boolean bold) {this.useBold = bold; return this;}

    public Label setAlign(StringAlignUtils.Align align) {
        this.align = align;
        return this;
    }

    @Override
    public void draw(Canvas canvas) {
        Font font = new Font(FontLoader.getDefaultFont().getTypeface()).setSize((0.01f+FontLoader.DYNAMIC_FONT_SIZE)* Display.getHeight());
        Paint strPaint = new Paint().setColor(strColor).setAntiAlias(true);
        Vector2f strPos;
        switch (align) {
            case CENTER_TO_TARGET -> {strPos = StringAlignUtils.textCenterToTarget(text,font,new Vector2f(x,y));}
            case TOP_LEFT_TO_TARGET -> {strPos = StringAlignUtils.textTLToTarget(text,font,new Vector2f(x,y));}
            case TOP_RIGHT_TO_TARGET -> {strPos = StringAlignUtils.textTRToTarget(text,font,new Vector2f(x,y));}
            case TOP_CENTER_TO_TARGET -> {strPos = StringAlignUtils.textTopCenterToTarget(text,font,new Vector2f(x,y));}
            case BOTTOM_LEFT_TO_TARGET -> {strPos = StringAlignUtils.textBLToTarget(text,font,new Vector2f(x,y));}
            case LEFT_CENTER_TO_TARGET -> {strPos = StringAlignUtils.textLeftCenterToTarget(text,font,new Vector2f(x,y));}
            case BOTTOM_RIGHT_TO_TARGET -> {strPos = StringAlignUtils.textBRToTarget(text,font,new Vector2f(x,y));}
            case RIGHT_CENTER_TO_TARGET -> {strPos = StringAlignUtils.textRightCenterToTarget(text,font,new Vector2f(x,y));}
            case BOTTOM_CENTER_TO_TARGET -> {strPos = StringAlignUtils.textBottomCenterToTarget(text,font,new Vector2f(x,y));}
            default -> {strPos = StringAlignUtils.textCenterToTarget(text,font,new Vector2f(x,y));}
        }
        canvas.drawString(text,strPos.x,strPos.y,font,strPaint);
        font.close(); strPaint.close();
        super.draw(canvas);
    }

    @Override
    public void onDragTick() {

    }

    public static class LabelTween implements TweenAccessor<Label> {
        public static final int STR_COLOR = 1;

        @Override
        public int getValues(Label target, int tweenType, float[] returnValues) {
            switch (tweenType) {
                case STR_COLOR -> {
                    float alpha = (target.strColor >> 24)&0xFF;
                    float[] hsb = Color.RGBtoHSB((target.strColor>>16)&0xff,(target.strColor>>8)&0xff,(target.strColor)&0xff,null);
                    returnValues[0] = alpha;
                    returnValues[1] = hsb[0];
                    returnValues[2] = hsb[1];
                    returnValues[3] = hsb[2];
                }
            }
            return 0;
        }

        @Override
        public void setValues(Label target, int tweenType, float[] newValues) {
            switch (tweenType) {
                case STR_COLOR -> {
                    int alpha = Math.round(newValues[0])<<24;
                    int rgb = Color.HSBtoRGB(newValues[1],newValues[2],newValues[3]);
                    target.strColor = (alpha<<24) | (rgb&0x00ffffff);
                }
            }
        }
    }
}
