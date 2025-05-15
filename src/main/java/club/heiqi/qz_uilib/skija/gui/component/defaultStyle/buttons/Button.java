package club.heiqi.qz_uilib.skija.gui.component.defaultStyle.buttons;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenAccessor;
import aurelienribon.tweenengine.equations.Quad;
import club.heiqi.qz_uilib.skija.gui.component.UIComponent;
import club.heiqi.qz_uilib.skija.gui.component.Utils;
import club.heiqi.qz_uilib.skija.font.FontLoader;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector2f;

import java.awt.*;

import static club.heiqi.qz_uilib.ConstField.debugLog;

/**
 * 默认基础样式Button - 蓝色按钮
 */
public class Button extends UIComponent {
    public static Logger LOG = LogManager.getLogger();
    public String text = "默认文本"; // 默认样式为蓝色按钮

    public int defaultFillColor = 0xFF0a59f7;
    public int defaultStrokeColor = 0x00000000;
    public int defaultTextColor = 0xFFFFFFFF;

    public int defaultHoverFillColor = 0xFF97b7f6;
    public int defaultHoverStrokeColor = 0x00000000;
    public int defaultHoverTextColor = 0xFFe9e9e9;

    public int defaultPressFillColor = 0xFF2045d4;
    public int defaultPressStrokeColor = 0x00000000;
    public int defaultPressTextColor = 0xFFFFFFFF;

    public int fillColor = defaultFillColor;
    public int strokeColor = defaultStrokeColor;
    public int textColor = defaultTextColor;

    /**
     * 默认创建一个填充蓝色-白色文本的按钮组件
     * @param x
     * @param y
     * @param width
     * @param height
     */
    public Button(float x, float y, float width, float height) {
        super(x, y, width, height);
        setPenetrate(false);
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
    public Button setDefaultPressFillColor(int color) {this.defaultPressFillColor = color; return this;};
    public Button setDefaultPressStrokeColor(int color) {this.defaultPressStrokeColor = color; return this;};
    public Button setDefaultPressTextColor(int color) {this.defaultPressTextColor = color; return this;};

    /**
     * 将该类添加到动画管理器中 - 动画效果依赖动画管理器，没有注册动画管理器就是无动画效果组件
     * @return
     */
    public Button setDefaultTween() {
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
        super.draw(canvas);
    }

    /**
     * 定义鼠标停在组件上方的效果
     */
    @Override
    public void onHoverTick(boolean transmit) {
        if (!mouseContain) {
            debugLog(LOG,"过渡到悬停");
            tweenToHover();
        }
        super.onHoverTick(false);
    }

    @Override
    public void onMouseOutTick() {
        if (mouseContain) {
            debugLog(LOG,"鼠标移出过渡到默认");
            tweenToDefault();
        }
        super.onMouseOutTick();
    }

    @Override
    public void onDragTick() {

    }

    @Override
    public boolean onRelease(boolean transmit) {
        super.onRelease(false);
        tweenToDefault();
        debugLog(LOG,"pressed: {}", pressed);
        if (clickedTask != null && pressed) {
            debugLog(LOG,"运行回调");
            clickedTask.run();
        }
        return false;
    }

    @Override
    public boolean onPressTick(boolean transmit) {
        super.onPressTick(transmit);
        tweenToPress();
        return false;
    }

    public void tweenToHover() {
        //debugLog(LOG,"过渡到悬停");
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

    public void tweenToPress() {
        //debugLog(LOG,"过渡到按压");
        manager.killAll();
        float[] hsb = Color.RGBtoHSB((defaultPressFillColor)>>16&0xff,(defaultPressFillColor)>>8&0xff,(defaultPressFillColor)&0xff,null);
        // 启动新的补间动画回到默认颜色
        Tween.to(this, ButtonTween.FILL_COLOR, 0.01f)
            .target(
                (defaultFillColor >> 24) & 0xFF,
                hsb[0],
                hsb[1],
                hsb[2]
            )
            .ease(Quad.INOUT)
            .start(manager);
        hsb = Color.RGBtoHSB((defaultPressStrokeColor)>>16&0xff,(defaultPressStrokeColor)>>8&0xff,(defaultPressStrokeColor)&0xff,null);
        Tween.to(this, ButtonTween.STROKE_COLOR, 1f)
            .target(
                (defaultPressStrokeColor >> 24) & 0xFF,
                hsb[0],
                hsb[1],
                hsb[2]
            )
            .ease(Quad.INOUT)
            .start(manager);
        hsb = Color.RGBtoHSB((defaultPressTextColor)>>16&0xff,(defaultPressTextColor)>>8&0xff,(defaultPressTextColor)&0xff,null);
        Tween.to(this, ButtonTween.TEXT_COLOR, 1f)
            .target(
                (defaultPressTextColor >> 24) & 0xFF,
                hsb[0],
                hsb[1],
                hsb[2]
            )
            .ease(Quad.INOUT)
            .start(manager);
    }

    public void tweenToDefault() {
        //debugLog(LOG,"过渡到默认");
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
