package club.heiqi.qz_uilib.skija.gui.component.defaultStyle;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenAccessor;
import aurelienribon.tweenengine.TweenManager;
import aurelienribon.tweenengine.equations.Linear;
import aurelienribon.tweenengine.equations.Quad;
import club.heiqi.qz_uilib.skija.gui.BaseGUI;
import club.heiqi.qz_uilib.skija.gui.component.UIComponent;
import club.heiqi.qz_uilib.skija.shader.GaussianBlur;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;

import java.awt.*;
import java.util.function.Consumer;

public class BackGround extends UIComponent {
    // 模糊效果
    public boolean useBlur = false;
    public float blurAnimTime = 1f;
    public final float blurIntensity = 32f;
    public float blur;
    // 填充颜色
    public int fillColor = 0xCC0A1212, strokeColor = 0xFF63FFC1;
    public float colorHSB = 0, colorHSB2=0.3f, colorHSB3=0.6f;
    // 圆角
    public boolean useRound = true;
    public float round = 20;


    public BackGround(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    public BackGround setFillColor(int color) {
        fillColor = color;
        return this;
    }

    public BackGround setStrokeColor(int color) {
        strokeColor = color;
        return this;
    }

    /**
     * 使用默认的动画配置
     * @return
     */
    public BackGround setTween() {
        // 将对象注册到Tween中
        Tween.registerAccessor(BackGround.class, new BackGroundTween());
        // 注册动画
        Tween.to(this,0,blurAnimTime)
                .target(blurIntensity).ease(Quad.INOUT).start(manager);
        Tween.to(this, 1, 5f)
                .target(1f).ease(Linear.INOUT)
                .repeat(-1, 0).start(manager);
        return this;
    }

    /**
     * 只需要使用Tween.to设置动画即可
     * <br>
     * 示例:<br>
     *      Tween.to(this,0,blurAnimTime)
     *                 .target(blurIntensity).ease(Quad.INOUT).start(manager);<br>
     * 详细使用请参考 {@code Universal Tween Engine 库}
     * @param consumer
     * @return
     */
    public BackGround setCustomTween(Consumer<TweenManager> consumer) {
        Tween.registerAccessor(BackGround.class, new BackGroundTween());
        consumer.accept(manager);
        return this;
    }

    /**
     * 选择是否开启背景模糊效果
     */
    public BackGround setBlur(boolean blur) {
        this.useBlur = blur; return this;
    }

    @Override
    public void draw(Canvas canvas) {
        // 制造动画背景模糊
        int fboID = Minecraft.getMinecraft().getFramebuffer().framebufferObject;
        if (useBlur) GaussianBlur.drawBlur(blur,fboID);
        // 1.创建圆角矩形
        RRect rectR = RRect.makeXYWH(x, y, width, height, round);
        RRect rectRStroke = RRect.makeXYWH(x, y, width, height, round);
        Rect rect = Rect.makeXYWH(x, y, width, height);
        Rect rectStroke = Rect.makeXYWH(x, y, width, height);
        // 2.创建线性渐变画笔
        Point start = new Point(x, y); // 左上
        Point end = new Point(x + width, y + height);
        int rgb1 = 0xFF000000 | Color.HSBtoRGB(colorHSB,1,1);
        int rgb2 = 0xFF000000 | Color.HSBtoRGB(colorHSB +colorHSB2,1,1);
        int rgb3 = 0xFF000000 | Color.HSBtoRGB(colorHSB +colorHSB3,1,1);
        Shader shader = Shader.makeLinearGradient(start, end, new int[]{rgb1,rgb2,rgb3});
        Paint paint = new Paint().setAntiAlias(true).setColor(fillColor);
        Paint paintStroke = new Paint().setShader(shader).setAntiAlias(true).setStroke(true).setStrokeWidth(5);
        if (useRound) {
            canvas.drawRRect(rectR, paint);
            canvas.drawRRect(rectRStroke, paintStroke);
        }
        else {
            canvas.drawRect(rect, paint);
            canvas.drawRect(rectStroke,paintStroke);
        }
        shader.close(); paint.close(); paintStroke.close();
    }

    @Override
    public void onClick() {

    }

    @Override
    public void onHover(BaseGUI.MouseInfo mouseInfo) {

    }

    @Override
    public void onMouseOut(BaseGUI.MouseInfo mouseInfo) {

    }

    @Override
    public void onPress() {

    }

    @Override
    public void onTick() {
        super.onTick();
    }




    // Tween 动画插值
    public static class BackGroundTween implements TweenAccessor<BackGround> {
        public static final int BLUR = 0;
        public static final int HUE_1 = 1; // 单独控制第一个H值

        @Override
        public int getValues(BackGround target, int tweenType, float[] returnValues) {
            switch (tweenType) {
                case BLUR -> {
                    returnValues[0] = target.blur;
                    return 1;
                }
                case HUE_1 -> {
                    returnValues[0] = target.colorHSB;
                    return 1;
                }
            }
            return 0;
        }

        @Override
        public void setValues(BackGround target, int tweenType, float[] newValues) {
            switch (tweenType) {
                case BLUR -> target.blur = newValues[0];
                case HUE_1 -> target.colorHSB = newValues[0];
            }
        }
    }
}
