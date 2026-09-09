package club.heiqi.uilib.ui.scene.paint;

import java.util.List;

import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 盒内玻璃浮雕，不改变布局、内容或交互坐标。
 *
 * <p>条带携带解析圆角、挖空与盒内裁限，backend 换算物理像素后才求覆盖率。
 * 不在 logical px 按行取整，否则 HUD 放大会连同圆角阶梯一起放大。
 * 厚底仍为面外条带，中央透明；不使用不透明底盖或 fragment CLIP。</p>
 */
final class SceneSurfaceReliefPainter {

    private SceneSurfaceReliefPainter() {}

    static void paint(SceneNode node, int width, int height, List<PaintCommand> out) {
        if (width <= 0 || height <= 0) return;
        float elevation = node.__getSurfaceElevation();
        // 小盒无法容纳固定上下留白时退化为平面，避免负尺寸或随状态改变面高。
        boolean tiny = width < 5 || height < 7;
        int lift = tiny ? 0 : Math.round(2.0f * elevation);
        Shape face = tiny ? new Shape(node, 0, 0, width, height)
                : new Shape(node, 1, 2 - lift, width - 1, height - 2 - lift, 1);

        if (!tiny) {
            // 只取下缘有限厚度带，并与原盒圆角求交；不依赖祖先 scissor 遮住溢出。
            Shape bounds = new Shape(node, 0, 0, width, height);
            outside(out, new Shape(node, 0, 3, width, height), face, bounds, height - 2,
                    argb(0x050B16, Math.round(18 + 14 * elevation)));
            outside(out, new Shape(node, 1, 2, width - 1, height - 1, 1), face, bounds,
                    face.bottom - 2, argb(0x162638, Math.round(64 + 24 * elevation)));
        }

        UiBackdrop backdrop = node.getBackdrop();
        if (backdrop != null && backdrop.isActive()) {
            out.add(PaintCommand.backdrop(face.left, face.top, face.right, face.bottom,
                    backdrop, 0, face.tl, face.tr, face.br, face.bl));
        }
        int background = node.getBackgroundColor();
        if ((background >>> 24) != 0) {
            out.add(PaintCommand.background(face.left, face.top, face.right, face.bottom,
                    background, face.tl, face.tr, face.br, face.bl));
        }
        if (tiny) return;

        // 表面渐变独立于描边宽度；关闭倒角也保留玻璃实体和面内明暗。
        Shape inner = face.inset();
        if (inner == null) return;
        // 面上只有低透明度渐变，中央保持原始玻璃透射。
        for (int y = inner.top; y < Math.min(inner.bottom, inner.top + 4); y++) {
            band(out, inner, null, null, y, y + 1,
                    argb(0xFFFFFF, (4 - (y - inner.top)) * 5));
        }
        for (int y = Math.max(inner.top + 4, inner.bottom - 3); y < inner.bottom; y++) {
            band(out, inner, null, null, y, y + 1,
                    argb(0x071320, (y - (inner.bottom - 3) + 1) * 4));
        }

        int border = node.getBorderColor();
        // 零宽或全透明边色只关闭倒角及其内缘阴影，不能被最低高光强度重新点亮。
        // 大宽度必须留下非空的透射孔洞；先夹宽度再做 inset，避免极大入参溢出。
        int maxBorderWidth = (Math.min(face.right - face.left, face.bottom - face.top) - 1) / 2;
        int borderWidth = Math.min(Math.max(0, node.getBorderWidth()), maxBorderWidth);
        if (borderWidth == 0 || (border >>> 24) == 0) return;
        Shape bevelInner = face.inset(borderWidth);
        int shine = border & 0xFFFFFF;
        int strength = Math.round(Math.max(48, Math.min(120, border >>> 24))
                * (0.75f + 0.25f * elevation));
        // 外倒角由 borderWidth 控制；保留一像素内缘阴影，默认宽度 1 与原配方一致。
        // 上沿最亮，左侧次之，底部仅弱反光，右侧压暗；不是均匀白圈。
        ring(out, face, bevelInner, argb(shine, strength), argb(shine, strength / 2),
                argb(0xCEE8FF, strength / 4), argb(0x071320, 38));
        Shape core = bevelInner.inset();
        if (core != null) {
            // 倒角内缘投下细阴影；按下时加深，留下玻璃内凹的反馈。
            int shade = Math.round(30 - 12 * elevation);
            ring(out, bevelInner, core, argb(0x071320, shade), 0, 0, argb(0x071320, shade));
        }
    }

    /** 仅画 outer 减去 face 的可见部分，阴影从不超出原盒。 */
    private static void outside(List<PaintCommand> out, Shape outer, Shape face, Shape bounds,
            int firstRow, int color) {
        band(out, outer, face, bounds, firstRow, outer.bottom, color);
    }

    private static void ring(List<PaintCommand> out, Shape outer, Shape inner,
            int topColor, int leftColor, int bottomColor, int rightColor) {
        band(out, outer, inner, null, outer.top, outer.bottom,
                new int[] {topColor, leftColor, bottomColor, rightColor});
    }

    private static int argb(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private static void band(List<PaintCommand> out, Shape outer, Shape inner, Shape bounds,
            int top, int bottom, int color) {
        band(out, outer, inner, bounds, top, bottom, new int[] {color, color, color, color});
    }

    private static void band(List<PaintCommand> out, Shape outer, Shape inner, Shape bounds,
            int top, int bottom, int[] colors) {
        int left = bounds == null ? outer.left : Math.max(outer.left, bounds.left);
        int right = bounds == null ? outer.right : Math.min(outer.right, bounds.right);
        top = Math.max(top, outer.top);
        bottom = Math.min(bottom, outer.bottom);
        if (bounds != null) {
            top = Math.max(top, bounds.top);
            bottom = Math.min(bottom, bounds.bottom);
        }
        if (left >= right || top >= bottom) return;
        out.add(PaintCommand.roundedBand(left, top, right, bottom,
                new RoundedBand(outer.relativeTo(left, top), inner == null ? null : inner.relativeTo(left, top),
                        bounds == null ? null : bounds.relativeTo(left, top), colors)));
    }

    /** 每个角限制到短边一半；嵌套面半径随 inset 缩小。 */
    private static final class Shape {
        final int left;
        final int top;
        final int right;
        final int bottom;
        final int tl;
        final int tr;
        final int br;
        final int bl;

        Shape(SceneNode node, int left, int top, int right, int bottom) {
            this(node, left, top, right, bottom, 0);
        }

        Shape(SceneNode node, int left, int top, int right, int bottom, int inset) {
            this(left, top, right, bottom,
                    radius(node, node.getCornerRadiusTopLeft(), inset),
                    radius(node, node.getCornerRadiusTopRight(), inset),
                    radius(node, node.getCornerRadiusBottomRight(), inset),
                    radius(node, node.getCornerRadiusBottomLeft(), inset));
        }

        private static int radius(SceneNode node, int corner, int inset) {
            int source = node.isPerCornerRadius() ? Math.max(0, corner) : node.getCornerRadius();
            return Math.max(0, source - inset);
        }

        Shape(int left, int top, int right, int bottom, int tl, int tr, int br, int bl) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            int limit = Math.min(right - left, bottom - top) / 2;
            this.tl = Math.max(0, Math.min(limit, tl));
            this.tr = Math.max(0, Math.min(limit, tr));
            this.br = Math.max(0, Math.min(limit, br));
            this.bl = Math.max(0, Math.min(limit, bl));
        }

        Shape inset() {
            return inset(1);
        }

        Shape inset(int amount) {
            if (right - left <= amount * 2 || bottom - top <= amount * 2) return null;
            return new Shape(left + amount, top + amount, right - amount, bottom - amount,
                    tl - amount, tr - amount, br - amount, bl - amount);
        }

        int[] relativeTo(int x, int y) {
            return new int[] {left - x, top - y, right - x, bottom - y, tl, tr, br, bl};
        }
    }
}
