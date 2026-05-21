package club.heiqi.uilib.ui.render;

import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.values.UiSurfaceStyle;

/**
 * 圆角矩形几何工具。
 *
 * <p>从 {@code UiRenderContext} 抽出的纯静态几何辅助类，专注于把"圆角矩形"按
 * 当前 OpenGL 即时模式画出来或者发射顶点，不持有任何渲染上下文状态。颜色、混合、
 * 纹理状态等仍由调用方负责。</p>
 */
final class UiRoundedRectGeometry {

    private UiRoundedRectGeometry() {}

    /**
     * 判断四角是否存在任何非零圆角。
     */
    static boolean hasAnyCornerRadius(UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
        return cornerRadii != null && (cornerRadii.getTopLeft() > 0 || cornerRadii.getTopRight() > 0
                || cornerRadii.getBottomRight() > 0 || cornerRadii.getBottomLeft() > 0);
    }

    /**
     * 从 surface 样式与目标尺寸解析最终四角圆角。
     */
    static UiBorderRadiusResolver.ResolvedCornerRadii resolveCornerRadii(UiSurfaceStyle surfaceStyle,
            int width, int height) {
        if (surfaceStyle == null) {
            return UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0);
        }
        return resolveCornerRadii(surfaceStyle.cornerRadii, width, height, surfaceStyle.cornerMask);
    }

    /**
     * 按 corner mask 与目标尺寸求得最终四角圆角。
     */
    static UiBorderRadiusResolver.ResolvedCornerRadii resolveCornerRadii(
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, float width, float height, int cornerMask) {
        UiBorderRadiusResolver.ResolvedCornerRadii resolvedCornerRadii = UiBorderRadiusResolver.scaleToFit(
                cornerRadii == null ? UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0) : cornerRadii,
                Math.round(width), Math.round(height));
        if ((cornerMask & UiSurfaceStyle.CORNER_TOP_LEFT) == 0 && (cornerMask & UiSurfaceStyle.CORNER_TOP_RIGHT) == 0
                && (cornerMask & UiSurfaceStyle.CORNER_BOTTOM_RIGHT) == 0
                && (cornerMask & UiSurfaceStyle.CORNER_BOTTOM_LEFT) == 0) {
            return UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0);
        }
        return UiBorderRadiusResolver.ResolvedCornerRadii.of(
                (cornerMask & UiSurfaceStyle.CORNER_TOP_LEFT) == 0 ? 0 : resolvedCornerRadii.getTopLeft(),
                (cornerMask & UiSurfaceStyle.CORNER_TOP_RIGHT) == 0 ? 0 : resolvedCornerRadii.getTopRight(),
                (cornerMask & UiSurfaceStyle.CORNER_BOTTOM_RIGHT) == 0 ? 0 : resolvedCornerRadii.getBottomRight(),
                (cornerMask & UiSurfaceStyle.CORNER_BOTTOM_LEFT) == 0 ? 0 : resolvedCornerRadii.getBottomLeft());
    }

    /**
     * 绘制带分角圆角的矩形几何。
     */
    static void drawRoundedRectGeometry(int left, int top, int right, int bottom,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, boolean filled, int cornerMask) {
        drawRoundedRectGeometry((float) left, (float) top, (float) right, (float) bottom, cornerRadii, filled,
                cornerMask);
    }

    /**
     * 绘制统一半径圆角矩形几何。
     */
    static void drawRoundedRectGeometry(int left, int top, int right, int bottom, int radius, boolean filled,
            int cornerMask) {
        drawRoundedRectGeometry((float) left, (float) top, (float) right, (float) bottom, (float) radius, filled,
                cornerMask);
    }

    /**
     * 绘制带分角圆角的矩形几何（浮点坐标）。
     */
    static void drawRoundedRectGeometry(float left, float top, float right, float bottom,
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii, boolean filled, int cornerMask) {
        UiBorderRadiusResolver.ResolvedCornerRadii resolvedCornerRadii = resolveCornerRadii(cornerRadii,
                right - left, bottom - top, cornerMask);
        float tl = resolvedCornerRadii.getTopLeft();
        float tr = resolvedCornerRadii.getTopRight();
        float br = resolvedCornerRadii.getBottomRight();
        float bl = resolvedCornerRadii.getBottomLeft();
        if (tl <= 0.0F && tr <= 0.0F && br <= 0.0F && bl <= 0.0F) {
            if (filled) {
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(right, bottom);
                GL11.glVertex2f(right, top);
                GL11.glVertex2f(left, top);
                GL11.glVertex2f(left, bottom);
                GL11.glEnd();
            } else {
                GL11.glVertex2f(left, bottom);
                GL11.glVertex2f(left, top);
                GL11.glVertex2f(right, top);
                GL11.glVertex2f(right, bottom);
            }
            return;
        }

        float startX = left + Math.max(0.0F, tl);
        float startY = top;
        if (filled) {
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2f((left + right) / 2.0F, (top + bottom) / 2.0F);
        }

        // 上边
        GL11.glVertex2f(startX, startY);
        GL11.glVertex2f(right - tr, top);
        emitCornerVertices(right, top, tr, cornerMask, UiSurfaceStyle.CORNER_TOP_RIGHT,
                right - tr, top + tr, 270.0D, 360.0D);
        GL11.glVertex2f(right, bottom - br);
        emitCornerVertices(right, bottom, br, cornerMask, UiSurfaceStyle.CORNER_BOTTOM_RIGHT,
                right - br, bottom - br, 0.0D, 90.0D);
        GL11.glVertex2f(left + bl, bottom);
        emitCornerVertices(left, bottom, bl, cornerMask, UiSurfaceStyle.CORNER_BOTTOM_LEFT,
                left + bl, bottom - bl, 90.0D, 180.0D);
        GL11.glVertex2f(left, top + tl);
        emitCornerVertices(left, top, tl, cornerMask, UiSurfaceStyle.CORNER_TOP_LEFT,
                left + tl, top + tl, 180.0D, 270.0D);

        if (filled) {
            GL11.glVertex2f(startX, startY);
            GL11.glEnd();
        }
    }

    /**
     * 绘制统一半径圆角矩形几何（浮点坐标）。
     */
    static void drawRoundedRectGeometry(float left, float top, float right, float bottom, float radius,
            boolean filled, int cornerMask) {
        float resolvedRadius = clampCornerRadius(right - left, bottom - top, radius);
        int resolvedCornerMask = cornerMask & UiSurfaceStyle.CORNER_ALL;
        if (resolvedRadius <= 0.0F || resolvedCornerMask == 0) {
            if (filled) {
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glVertex2f(right, bottom);
                GL11.glVertex2f(right, top);
                GL11.glVertex2f(left, top);
                GL11.glVertex2f(left, bottom);
                GL11.glEnd();
            } else {
                GL11.glVertex2f(left, bottom);
                GL11.glVertex2f(left, top);
                GL11.glVertex2f(right, top);
                GL11.glVertex2f(right, bottom);
            }
            return;
        }

        drawRoundedRectGeometry(left, top, right, bottom,
                UiBorderRadiusResolver.ResolvedCornerRadii.uniform(Math.round(resolvedRadius)), filled,
                resolvedCornerMask);
    }

    /**
     * 发射圆角矩形整圈顶点。
     */
    static void emitRoundedRectVertices(float left, float top, float right, float bottom, float radius,
            int cornerMask) {
        emitCornerVertices(left, top, radius, cornerMask, UiSurfaceStyle.CORNER_TOP_LEFT,
                left + radius, top + radius, 180.0D, 270.0D);
        emitCornerVertices(right, top, radius, cornerMask, UiSurfaceStyle.CORNER_TOP_RIGHT,
                right - radius, top + radius, 270.0D, 360.0D);
        emitCornerVertices(right, bottom, radius, cornerMask, UiSurfaceStyle.CORNER_BOTTOM_RIGHT,
                right - radius, bottom - radius, 0.0D, 90.0D);
        emitCornerVertices(left, bottom, radius, cornerMask, UiSurfaceStyle.CORNER_BOTTOM_LEFT,
                left + radius, bottom - radius, 90.0D, 180.0D);
    }

    /**
     * 发射单角顶点序列；不在 cornerMask 中时退化为直角顶点。
     */
    static void emitCornerVertices(float sharpX, float sharpY, float radius, int cornerMask, int cornerBit,
            float centerX, float centerY, double startAngle, double endAngle) {
        if ((cornerMask & cornerBit) == 0) {
            GL11.glVertex2f(sharpX, sharpY);
            return;
        }
        emitArcVertices(centerX, centerY, radius, startAngle, endAngle, false);
    }

    /**
     * 发射圆角矩形首个顶点（用于线框路径起点）。
     */
    static void emitFirstRoundedRectVertex(float left, float top, float radius, int cornerMask) {
        if ((cornerMask & UiSurfaceStyle.CORNER_TOP_LEFT) == 0) {
            GL11.glVertex2f(left, top);
            return;
        }
        GL11.glVertex2f(left, top + radius);
    }

    /**
     * 发射一段弧上的顶点序列。
     */
    static void emitArcVertices(float centerX, float centerY, float radius, double startAngle, double endAngle,
            boolean skipFirst) {
        int segments = resolveCornerSegments(radius);
        for (int index = skipFirst ? 1 : 0; index <= segments; index++) {
            double angle = Math.toRadians(startAngle + (endAngle - startAngle) * index / (double) segments);
            GL11.glVertex2f((float) (centerX + Math.cos(angle) * radius), (float) (centerY + Math.sin(angle) * radius));
        }
    }

    /**
     * 按半径估算弧分段数。
     */
    static int resolveCornerSegments(float radius) {
        return Math.max(6, Math.min(18, Math.round(radius)));
    }

    /**
     * 把整数圆角约束到不超过盒尺寸的一半。
     */
    static int clampCornerRadius(int width, int height, int radius) {
        return Math.max(0, Math.min(radius, Math.min(Math.max(0, width), Math.max(0, height)) / 2));
    }

    /**
     * 把浮点圆角约束到不超过盒尺寸的一半。
     */
    static float clampCornerRadius(float width, float height, float radius) {
        return Math.max(0.0F, Math.min(radius, Math.min(Math.max(0.0F, width), Math.max(0.0F, height)) / 2.0F));
    }
}
