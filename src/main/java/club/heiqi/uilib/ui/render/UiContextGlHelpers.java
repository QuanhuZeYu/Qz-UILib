package club.heiqi.uilib.ui.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import club.heiqi.uilib.ui.base.cascade.UiBorderRadiusResolver;

/** UiRenderContext 的圆角覆盖率与 GL 批量绘制助手。 */
final class UiContextGlHelpers {

    private UiContextGlHelpers() {}

    static void fillRoundedRect(int left, int top, int right, int bottom,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, int cornerMask, int color,
            Runnable mainLayerContentChangedNotifier) {
        int[] outer = shape(right - left, bottom - top, cornerRadii, cornerMask);
        drawRoundedBandBatch(null, left, top, right, bottom, outer, null, null,
                new int[] {color, color, color, color}, 1F);
        mainLayerContentChangedNotifier.run();
    }

    static void drawRoundedBorder(int left, int top, int right, int bottom,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, int cornerMask, int color,
            Runnable mainLayerContentChangedNotifier) {
        int width = right - left;
        int height = bottom - top;
        int[] outer = shape(width, height, cornerRadii, cornerMask);
        // 延续普通 BORDER 的 1 个 framebuffer px 宽度；内外弧共用覆盖率积分。
        int[] inner = width > 2 && height > 2
                ? new int[] {1, 1, width - 1, height - 1, Math.max(0, outer[4] - 1),
                    Math.max(0, outer[5] - 1), Math.max(0, outer[6] - 1), Math.max(0, outer[7] - 1)}
                : null;
        drawRoundedBandBatch(null, left, top, right, bottom, outer, inner, null,
                new int[] {color, color, color, color}, 1F);
        mainLayerContentChangedNotifier.run();
    }

    private static int[] shape(int width, int height,
            UiBorderRadiusResolver.ResolvedCornerRadii radii, int cornerMask) {
        UiBorderRadiusResolver.ResolvedCornerRadii resolved = UiRoundedRectGeometry.resolveCornerRadii(
                radii, width, height, cornerMask);
        return new int[] {0, 0, width, height, resolved.getTopLeft(), resolved.getTopRight(),
                resolved.getBottomRight(), resolved.getBottomLeft()};
    }

    /** 一层解析装饰只提交一个 GL 批次，保持祖先裁剪，恢复全部触碰的固定管线状态。 */
    static void drawRoundedBandBatch(UiRenderContext context, int left, int top, int right, int bottom,
            int[] outer, int[] inner, int[] bounds, int[] colors, float scale) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            // alpha test 会丢弃低覆盖率边缘，重新制造硬台阶；不依赖宿主上次留下的阈值。
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glBegin(GL11.GL_QUADS);
            try {
                UiRoundedBandRasterizer.draw((UiRoundedBandRasterizer.SpanConsumer) (l, t, r, b, color) -> {
                    applyColor(color);
                    GL11.glVertex2i(r, b);
                    GL11.glVertex2i(r, t);
                    GL11.glVertex2i(l, t);
                    GL11.glVertex2i(l, b);
                }, left, top, right, bottom, outer, inner, bounds, colors, scale);
            } finally {
                GL11.glEnd();
            }
        } finally {
            GL11.glPopAttrib();
        }
        if (context != null) context.notifyMainLayerContentChanged();
    }

    static void applyColor(int color) {
        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        GL11.glColor4f(red, green, blue, alpha);
    }
}
