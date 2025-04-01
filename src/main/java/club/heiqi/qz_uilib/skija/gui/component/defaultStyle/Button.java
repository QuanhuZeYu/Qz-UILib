package club.heiqi.qz_uilib.skija.gui.component.defaultStyle;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenAccessor;
import aurelienribon.tweenengine.equations.Quad;
import club.heiqi.qz_uilib.skija.gui.BaseGUI;
import club.heiqi.qz_uilib.skija.gui.component.UIComponent;
import club.heiqi.qz_uilib.skija.gui.component.Utils;
import club.heiqi.qz_uilib.skija.font.FontLoader;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;
import org.joml.Vector2f;

import java.awt.*;

/**
 * 默认基础样式Button
 */
public class Button extends UIComponent {
    public String text = "默认文本";

    public int defaultFillColor = 0xFF1689db;
    public int defaultStrokeColor = 0x00000000;
    public int defaultTextColor = 0xFFFFFFFF;

    public int defaultHoverFillColor = 0xFFa3bbcc;
    public int defaultHoverStrokeColor = 0x00000000;
    public int defaultHoverTextColor = 0xFFfff1e7;

    public int fillColor = defaultFillColor;
    public int strokeColor = defaultStrokeColor;
    public int textColor = defaultTextColor;

    public Button(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    public Button setText(String text) {
        this.text = text; return this;
    }
    public Button setDefaultFillColor(int color) {this.defaultFillColor = color; fillColor = color; return this;}
    public Button setDefaultStrokeColor(int color) {this.defaultStrokeColor = color; strokeColor = color; return this;}
    public Button setDefaultTextColor(int color) {this.defaultTextColor = color; textColor = color; return this;}
    public Button setDefaultHoverFillColor(int color) {this.defaultHoverFillColor = color; return this;}
    public Button setDefaultHoverStrokeColor(int color) {this.defaultHoverStrokeColor = color; return this;}
    public Button setDefaultHoverTextColor(int color) {this.defaultHoverTextColor = color; return this;}

    public Button setTween() {
        Tween.registerAccessor(Button.class, new ButtonTween());
        return this;
    }

    @Override
    public void draw(Canvas canvas) {
        Paint fillPaint = new Paint().setColor(fillColor);
        Paint strokePaint = new Paint().setStroke(true).setColor(strokeColor);
        Paint textPaint = new Paint().setColor(textColor);
        // 绘制背景
        canvas.drawRRect(RRect.makeXYWH(x, y, width, height, 20), fillPaint);
        // 绘制外边框
        canvas.drawRRect(RRect.makeXYWH(x, y, width, height, 20), strokePaint);
        Font font = FontLoader.getDefaultFont();
        Vector2f center = new Vector2f(x+width/2, y+height/2);
        Utils.drawStringCenter(canvas, text, font, center, textPaint);

        // 释放资源
        fillPaint.close(); strokePaint.close(); textPaint.close();
    }

    @Override
    public void onClick() {

    }

    @Override
    public void onHover(BaseGUI.MouseInfo mouseInfo) {
        manager.killAll();
        float[] hsb = Color.RGBtoHSB((defaultHoverFillColor)>>16&0xff,(defaultHoverFillColor)>>8&0xff,(defaultHoverFillColor)&0xff,null);
        Tween.to(this, ButtonTween.FILL_COLOR, 0.3f)
                .target(
                        (defaultHoverFillColor >> 24) & 0xFF,
                        hsb[0],
                        hsb[1],
                        hsb[2]
                )
                .ease(Quad.INOUT)
                .start(manager);
        hsb = Color.RGBtoHSB((defaultHoverStrokeColor)>>16&0xff,(defaultHoverStrokeColor)>>8&0xff,(defaultHoverStrokeColor)&0xff,null);
        Tween.to(this, ButtonTween.STROKE_COLOR, 0.3f)
                .target(
                        (defaultHoverStrokeColor >> 24) & 0xFF,
                        hsb[0],
                        hsb[1],
                        hsb[2]
                )
                .ease(Quad.INOUT)
                .start(manager);
        hsb = Color.RGBtoHSB((defaultHoverTextColor)>>16&0xff,(defaultHoverTextColor)>>8&0xff,(defaultHoverTextColor)&0xff,null);
        Tween.to(this, ButtonTween.TEXT_COLOR, 0.3f)
                .target(
                        (defaultHoverTextColor >> 24) & 0xFF,
                        hsb[0],
                        hsb[1],
                        hsb[2]
                )
                .ease(Quad.INOUT)
                .start(manager);
    }

    @Override
    public void onMouseOut(BaseGUI.MouseInfo mouseInfo) {
        manager.killAll();
        float[] hsb = Color.RGBtoHSB((defaultFillColor)>>16&0xff,(defaultFillColor)>>8&0xff,(defaultFillColor)&0xff,null);
        // 启动新的补间动画回到默认颜色
        Tween.to(this, ButtonTween.FILL_COLOR, 0.3f)
                .target(
                        (defaultFillColor >> 24) & 0xFF,
                        hsb[0],
                        hsb[1],
                        hsb[2]
                )
                .ease(Quad.INOUT)
                .start(manager);
        hsb = Color.RGBtoHSB((defaultStrokeColor)>>16&0xff,(defaultStrokeColor)>>8&0xff,(defaultStrokeColor)&0xff,null);
        Tween.to(this, ButtonTween.STROKE_COLOR, 0.3f)
                .target(
                        (defaultStrokeColor >> 24) & 0xFF,
                        hsb[0],
                        hsb[1],
                        hsb[2]
                )
                .ease(Quad.INOUT)
                .start(manager);
        hsb = Color.RGBtoHSB((defaultTextColor)>>16&0xff,(defaultTextColor)>>8&0xff,(defaultTextColor)&0xff,null);
        Tween.to(this, ButtonTween.TEXT_COLOR, 0.3f)
                .target(
                        (defaultTextColor >> 24) & 0xFF,
                        hsb[0],
                        hsb[1],
                        hsb[2]
                )
                .ease(Quad.INOUT)
                .start(manager);
    }

    @Override
    public void onPress() {

    }

    @Override
    public void onTick() {
        super.onTick();
    }

    public static class ButtonTween implements TweenAccessor<Button> {
        public static final int FILL_COLOR = 1;
        public static final int STROKE_COLOR = 2;
        public static final int TEXT_COLOR = 3;

        @Override
        public int getValues(Button target, int tweenType, float[] returnValues) {
            switch (tweenType) {
                case FILL_COLOR -> {
                    int alpha = (target.fillColor >> 24)&0xFF;
                    float[] hsb = Color.RGBtoHSB((target.fillColor >> 16)&0xFF, (target.fillColor>>8)&0xFF,target.fillColor&0xFF,null);
                    returnValues[0] = alpha;
                    returnValues[1] = hsb[0];
                    returnValues[2] = hsb[1];
                    returnValues[3] = hsb[2];
                    return 4;
                }
                case STROKE_COLOR -> {
                    int alpha = (target.strokeColor >> 24)&0xFF;
                    float[] hsb = Color.RGBtoHSB((target.strokeColor >> 16)&0xFF, (target.strokeColor>>8)&0xFF,target.strokeColor&0xFF,null);
                    returnValues[0] = alpha;
                    returnValues[1] = hsb[0];
                    returnValues[2] = hsb[1];
                    returnValues[3] = hsb[2];
                    return 4;
                }
                case TEXT_COLOR -> {
                    int alpha = (target.textColor >> 24)&0xFF;
                    float[] hsb = Color.RGBtoHSB((target.textColor >> 16)&0xFF, (target.textColor>>8)&0xFF,target.textColor&0xFF,null);
                    returnValues[0] = alpha;
                    returnValues[1] = hsb[0];
                    returnValues[2] = hsb[1];
                    returnValues[3] = hsb[2];
                }
            }
            return 0;
        }

        @Override
        public void setValues(Button target, int tweenType, float[] newValues) {
            switch (tweenType) {
                case FILL_COLOR -> {
                    int rgb = Color.HSBtoRGB(newValues[1], newValues[2], newValues[3]);
                    target.fillColor = ((Math.round(newValues[0]) << 24)&0xff000000) | (rgb & 0x00FFFFFF);
                }
                case STROKE_COLOR -> {
                    int rgb = Color.HSBtoRGB(newValues[1], newValues[2], newValues[3]);
                    target.strokeColor = ((Math.round(newValues[0]) << 24)&0xff000000) | (rgb & 0x00FFFFFF);
                }
                case TEXT_COLOR -> {
                    int rgb = Color.HSBtoRGB(newValues[1], newValues[2], newValues[3]);
                    target.textColor = ((Math.round(newValues[0]) << 24)&0xff000000) | (rgb & 0x00FFFFFF);
                }
            }
        }
    }
}
