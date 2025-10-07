package club.heiqi.qz_uilib.widget;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenAccessor;
import aurelienribon.tweenengine.equations.Quad;
import club.heiqi.qz_uilib.widget.drawUtil.RenderTool;
import club.heiqi.qz_uilib.widget.drawUtil.RoundedRectangle;
import net.minecraft.client.Minecraft;
import org.joml.Vector2d;
import org.lwjgl.opengl.GL11;

import java.util.Set;

public class ButtonWidget extends Widget {
    RoundedRectangle rectangle = new RoundedRectangle();

    public int rectangleColor = 0xff484848;
    public int targetColorCache = rectangleColor;

    public Runnable callBack = () -> {};

    public ButtonWidget setCallBack(Runnable callBack) {
        this.callBack = callBack;
        return this;
    }

    @Override
    public void drawSelf() {
        super.drawSelf();

        rectangle.gen(width, height, 20, 3, new Vector2d(x,y), rectangleColor);

        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        // GL11.glDisable(GL11.GL_DEPTH_TEST);
        RenderTool.getInstance().render(
                rectangle.getVertexArray(),
                rectangle.getTexCoordArray(),
                rectangle.getColorArray(),
                rectangle.getIndexArray());
    }

    @Override
    public void onDragged(float newX, float newY, Set<Integer> clicked) {
        super.onDragged(newX, newY, clicked);
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
        Tween.registerAccessor(ButtonWidget.class, new ButtonWidget.WidgetAnim());
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
        TWEEN_MANAGER.killTarget(this, WidgetAnim.COLOR);

        // 将目标颜色分解为浮点数组
        float a = ((targetColor >> 24) & 0xFF) / 255.0f;
        float r = ((targetColor >> 16) & 0xFF) / 255.0f;
        float g = ((targetColor >> 8) & 0xFF) / 255.0f;
        float b = (targetColor & 0xFF) / 255.0f;

        // 启动 Tween 动画
        Tween.to(this, WidgetAnim.COLOR, 500) // 持续 1 秒
                .target(a, r, g, b) // 目标 ARGB 浮点值
                .ease(Quad.OUT) // 使用二次缓出效果
                .start(TWEEN_MANAGER);
    }

    public static class WidgetAnim implements TweenAccessor<ButtonWidget> {

        // 1. 定义 Tween Type 常量
        public static final int COLOR = 1;

        @Override
        public int getValues(ButtonWidget target, int tweenType, float[] returnValues) {
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
        public void setValues(ButtonWidget target, int tweenType, float[] newValues) {
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
