package club.heiqi.qz_uilib.widget;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenAccessor;
import aurelienribon.tweenengine.equations.Quad;
import club.heiqi.qz_fontrender.fontsystem.impl.ReplaceFontRender;
import club.heiqi.qz_uilib.widget.drawUtil.RenderTool;
import club.heiqi.qz_uilib.widget.drawUtil.RoundedRectangle;
import org.joml.Vector2d;
import org.lwjgl.opengl.GL11;

import java.util.Set;

public class ButtonWithTextWidget extends Widget {
    RoundedRectangle rectangle = new RoundedRectangle();

    public int rectangleColor = 0xff484848;
    public int targetColorCache = rectangleColor;

    public Runnable callBack = () -> {};


    public String text = "";
    public float textSize = 32;
    public int textColor = 0xffffffff;

    public ButtonWithTextWidget setCallBack(Runnable callBack) {
        this.callBack = callBack;
        return this;
    }

    public ButtonWithTextWidget setText(String text) {
        this.text = text;
        width = fontRenderer.getStringWidth(text);
        height = textSize;
        this.setPerfectSize(width,height);
        this.setSize(width,height);
        return this;
    }
    public ButtonWithTextWidget setTextSize(float size) {
        this.textSize = size;
        height = size;
        width = fontRenderer.getStringWidth(text);
        this.setPerfectSize(width,height);
        this.setSize(width,height);
        return this;
    }
    public ButtonWithTextWidget setTextColor(int color) {
        this.textColor = color;
        return this;
    }

    @Override
    public void drawSelf() {
        super.drawSelf();

        // 渲染按钮
        rectangle.gen(width, height, 20, 3, new Vector2d(x,y), rectangleColor);

        GL11.glDisable(GL11.GL_CULL_FACE);
        // GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        RenderTool.getInstance().render(
                rectangle.getVertexArray(),
                rectangle.getTexCoordArray(),
                rectangle.getColorArray(),
                rectangle.getIndexArray());

        // 渲染文字
        fontRenderer.setCharSize(textSize);
        float stringWidth = fontRenderer.getStringWidth(text);
        float x = this.x + (width / 2) - (stringWidth / 2);
        float y = this.y + (height / 2) - (textSize / 2);
        fontRenderer.drawString(text, (int) x, (int) y,textColor);
    }

    /**鼠标在内部时会高频触发*/
    @Override
    public void onHover(float x, float y) {
        setColor(0xffc0d8d8);
    }

    /**鼠标在外部时会高频触发*/
    @Override
    public void onLeave(float x, float y) {
        setColor(0xff484848);
    }

    /**考虑可能高频触发场景*/
    @Override
    public void onPress(float x, float y, int buttonID) {
        setColor(0xfff0f0f0);
    }

    @Override
    public void onRelease(float x, float y, int buttonID) {
        setColor(0xffc0d8d8);
        callBack.run();
    }

    @Override
    public void registerTween() {
        Tween.registerAccessor(ButtonWithTextWidget.class, new ButtonWithTextWidget.WidgetAnim());
    }

    /**检查设置颜色是否与目标颜色一致，如果一致什么都不需要做*/
    public void setColor(int color) {
        if (color == this.targetColorCache) return;
        targetColorCache = color;
        animateColor(color);
    }

    // 辅助方法：启动颜色动画
    private void animateColor(int targetColor) {
        // 杀死此对象上所有正在运行的颜色动画
        TWEEN_MANAGER.killTarget(this, ButtonWidget.WidgetAnim.COLOR);

        // 将目标颜色分解为浮点数组
        float a = ((targetColor >> 24) & 0xFF) / 255.0f;
        float r = ((targetColor >> 16) & 0xFF) / 255.0f;
        float g = ((targetColor >> 8) & 0xFF) / 255.0f;
        float b = (targetColor & 0xFF) / 255.0f;

        // 启动 Tween 动画
        Tween.to(this, ButtonWidget.WidgetAnim.COLOR, 500) // 持续 1 秒
                .target(a, r, g, b) // 目标 ARGB 浮点值
                .ease(Quad.OUT) // 使用二次缓出效果
                .start(TWEEN_MANAGER);
    }

    public static class WidgetAnim implements TweenAccessor<ButtonWithTextWidget> {

        // 1. 定义 Tween Type 常量
        public static final int COLOR = 1;

        @Override
        public int getValues(ButtonWithTextWidget target, int tweenType, float[] returnValues) {
            if (tweenType == COLOR) {
                int color = target.rectangleColor;

                // 将 0xAARRGGBB 颜色分解为四个 [0, 1] 范围的浮点数
                // Alpha
                returnValues[0] = ((color >> 24) & 0xFF) / 255.0f;
                // Red
                returnValues[1] = ((color >> 16) & 0xFF) / 255.0f;
                // Green
                returnValues[2] = ((color >> 8) & 0xFF) / 255.0f;
                // Blue
                returnValues[3] = (color & 0xFF) / 255.0f;

                return 4; // 返回 4 个值
            }
            return 0;
        }

        @Override
        public void setValues(ButtonWithTextWidget target, int tweenType, float[] newValues) {
            if (tweenType == COLOR) {
                // 从四个 [0, 1] 范围的浮点数合成 0xAARRGGBB 颜色
                int a = (int) (newValues[0] * 255.0f) & 0xFF;
                int r = (int) (newValues[1] * 255.0f) & 0xFF;
                int g = (int) (newValues[2] * 255.0f) & 0xFF;
                int b = (int) (newValues[3] * 255.0f) & 0xFF;

                // 2. 调用目标对象的 setter 方法应用新值
                target.rectangleColor = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
    }
}
