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
        // 1px 圆角边框：四条直边用 QUADS，四个角弧环带用独立 TRIANGLE_STRIP。
        // 旧实现走 GL_LINE_LOOP，在 Angelica/lwjgl3ify 核心管线里线宽与弧段渲染不可靠，
        // 圆角会退化为直角（分类导航外框圆角丢失即此路径）。
        float width = 1.0F;
        float l = (float) left;
        float t = (float) top;
        float r = (float) right;
        float b = (float) bottom;
        UiBorderRadiusResolver.ResolvedCornerRadii resolved = UiRoundedRectGeometry.resolveCornerRadii(
                cornerRadii, r - l, b - t, cornerMask);
        float tl = resolved.getTopLeft();
        float tr = resolved.getTopRight();
        float br = resolved.getBottomRight();
        float bl = resolved.getBottomLeft();

        GL11.glBegin(GL11.GL_QUADS);
        addQuad(l + tl, t, r - tr, t + width);
        addQuad(r - width, t + tr, r, b - br);
        addQuad(l + bl, b - width, r - br, b);
        addQuad(l, t + tl, l + width, b - bl);
        GL11.glEnd();
        // 角弧环带用独立 TRIANGLE_STRIP 批次：strip 与填充路径同属连续图元族
        // （GLStateManager.isContinuousDraw(5)==true），与已验证可用的圆角填充同机制，
        // 绕开 GL_QUADS 索引转换路径对旋转小四边形的潜在问题。
        addCornerStrip(l + tl, t + tl, tl, 180.0D, 270.0D, width);
        addCornerStrip(r - tr, t + tr, tr, 270.0D, 360.0D, width);
        addCornerStrip(r - br, b - br, br, 0.0D, 90.0D, width);
        addCornerStrip(l + bl, b - bl, bl, 90.0D, 180.0D, width);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        mainLayerContentChangedNotifier.run();
    }

    private static void addQuad(float x0, float y0, float x1, float y1) {
        GL11.glVertex2f(x0, y0);
        GL11.glVertex2f(x1, y0);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x0, y1);
    }

    /** 发射一段角弧环带：外弧 radius、内弧 radius-width，内外弧交替顶点铺 TRIANGLE_STRIP。 */
    private static void addCornerStrip(float centerX, float centerY, float radius, double startAngle,
                                       double endAngle, float width) {
        if (radius <= 0.0F) {
            return;
        }
        float innerRadius = Math.max(0.0F, radius - width);
        int segments = UiRoundedRectGeometry.resolveCornerSegments(radius);
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int index = 0; index <= segments; index++) {
            double angle = Math.toRadians(startAngle + (endAngle - startAngle) * index / (double) segments);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            GL11.glVertex2f(centerX + cos * radius, centerY + sin * radius);
            GL11.glVertex2f(centerX + cos * innerRadius, centerY + sin * innerRadius);
        }
        GL11.glEnd();
    }

    static void applyColor(int color) {
        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        GL11.glColor4f(red, green, blue, alpha);
    }
}
