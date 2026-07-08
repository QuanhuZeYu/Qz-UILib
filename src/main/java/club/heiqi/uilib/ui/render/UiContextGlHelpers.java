package club.heiqi.uilib.ui.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import club.heiqi.uilib.ui.base.cascade.UiBorderRadiusResolver;

/**
 * UiRenderContext 使用的 OpenGL 绘制助手。
 */
final class UiContextGlHelpers {

    private UiContextGlHelpers() {
    }

    /**
     * 填充圆角矩形。
     *
     * @param left                            左侧坐标
     * @param top                             顶部坐标
     * @param right                           右侧坐标
     * @param bottom                          底部坐标
     * @param cornerRadii                     四角圆角
     * @param cornerMask                      圆角掩码
     * @param color                           ARGB 颜色
     * @param mainLayerContentChangedNotifier 主图层内容变更通知
     */
    static void fillRoundedRect(int left, int top, int right, int bottom,
                                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, int cornerMask, int color,
                                Runnable mainLayerContentChangedNotifier) {
        applyColor(color);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        UiRoundedRectGeometry.drawRoundedRectGeometry(left, top, right, bottom, cornerRadii, true, cornerMask);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mainLayerContentChangedNotifier.run();
    }

    /**
     * 绘制圆角边框。
     *
     * @param left                            左侧坐标
     * @param top                             顶部坐标
     * @param right                           右侧坐标
     * @param bottom                          底部坐标
     * @param cornerRadii                     四角圆角
     * @param cornerMask                      圆角掩码
     * @param color                           ARGB 颜色
     * @param mainLayerContentChangedNotifier 主图层内容变更通知
     */
    static void drawRoundedBorder(int left, int top, int right, int bottom,
                                  UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, int cornerMask, int color,
                                  Runnable mainLayerContentChangedNotifier) {
        applyColor(color);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(1.0F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        // 线框边界若直接落在整数像素边缘，会让左/上侧描边有一半落在 clip 外，
        // 在真实运行时看起来像左侧边线被吞掉。这里统一把描边中心内缩到像素中心，
        // 让四条边都以同样的方式落在边框盒内部。
        UiRoundedRectGeometry.drawRoundedRectGeometry(left + 0.5F, top + 0.5F, right - 0.5F, bottom - 0.5F,
            cornerRadii, false, cornerMask);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mainLayerContentChangedNotifier.run();
    }

    static void applyColor(int color) {
        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        GL11.glColor4f(red, green, blue, alpha);
    }
}
