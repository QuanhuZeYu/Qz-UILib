package club.heiqi.uilib.ui.scene.paint;

import java.util.List;

import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 盒内玻璃浮雕，只生成既有 PaintCommand，不改变布局、内容或交互坐标。
 *
 * <p>圆角条带直接解出每行的安全跨度；fragment 内不能放 CLIP：其边界命令不随
 * translatedBy 平移，scissor 也不跟随祖先顶点变换。所有光影均为可平移的背景几何。
 * 厚底用面外条带，不用不透明底盖；边缘也不依赖 backend 的固定宽度 BORDER。</p>
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

        Shape inner = face.inset();
        if (inner == null) return;
        // 面上只有低透明度渐变，中央保持原始玻璃透射。
        for (int y = inner.top; y < Math.min(inner.bottom, inner.top + 4); y++) {
            row(out, inner.leftAt(y), y, inner.rightAt(y),
                    argb(0xFFFFFF, (4 - (y - inner.top)) * 5));
        }
        for (int y = Math.max(inner.top + 4, inner.bottom - 3); y < inner.bottom; y++) {
            row(out, inner.leftAt(y), y, inner.rightAt(y),
                    argb(0x071320, (y - (inner.bottom - 3) + 1) * 4));
        }

        int border = node.getBorderColor();
        int shine = (border >>> 24) == 0 ? 0xEAF6FF : border & 0xFFFFFF;
        int strength = Math.round(Math.max(48, Math.min(120, border >>> 24))
                * (0.75f + 0.25f * elevation));
        // 外倒角：上沿最亮，左侧次之，底部仅弱反光，右侧压暗；不是均匀白圈。
        ring(out, face, inner, argb(shine, strength), argb(shine, strength / 2),
                argb(0xCEE8FF, strength / 4), argb(0x071320, 38));
        Shape core = inner.inset();
        if (core != null) {
            // 倒角内缘投下细阴影；按下时加深，留下玻璃内凹的反馈。
            int shade = Math.round(30 - 12 * elevation);
            ring(out, inner, core, argb(0x071320, shade), 0, 0, argb(0x071320, shade));
        }
    }

    /** 仅画 outer 减去 face 的可见部分，阴影从不超出原盒。 */
    private static void outside(List<PaintCommand> out, Shape outer, Shape face, Shape bounds,
            int firstRow, int color) {
        for (int y = Math.max(outer.top, firstRow); y < outer.bottom; y++) {
            int left = Math.max(outer.leftAt(y), bounds.leftAt(y));
            int right = Math.min(outer.rightAt(y), bounds.rightAt(y));
            if (y < face.top || y >= face.bottom) {
                row(out, left, y, right, color);
            } else {
                row(out, left, y, Math.min(right, face.leftAt(y)), color);
                row(out, Math.max(left, face.rightAt(y)), y, right, color);
            }
        }
    }

    private static void ring(List<PaintCommand> out, Shape outer, Shape inner,
            int topColor, int leftColor, int bottomColor, int rightColor) {
        // 覆盖常用圆角的完整弧段；每端最多十二行，避免大尺寸表面逐行扫描。
        int topRows = Math.max(3, Math.min(12, Math.max(outer.tl, outer.tr)));
        int bottomRows = Math.max(2, Math.min(12, Math.max(outer.bl, outer.br)));
        for (int band = 0; band < 2; band++) {
            int start = band == 0 ? outer.top : Math.max(outer.top + topRows, outer.bottom - bottomRows);
            int end = band == 0 ? Math.min(outer.bottom, outer.top + topRows) : outer.bottom;
            for (int y = start; y < end; y++) {
                int left = outer.leftAt(y);
                int right = outer.rightAt(y);
                if (y < inner.top) {
                    row(out, left, y, right, topColor);
                } else if (y >= inner.bottom) {
                    row(out, left, y, right, bottomColor);
                } else {
                    row(out, left, y, Math.min(right, inner.leftAt(y)), leftColor);
                    row(out, Math.max(left, inner.rightAt(y)), y, right, rightColor);
                }
            }
        }
        rect(out, outer.left, outer.top + Math.max(topRows, outer.tl), inner.left,
                outer.bottom - Math.max(bottomRows, outer.bl), leftColor);
        rect(out, inner.right, outer.top + Math.max(topRows, outer.tr), outer.right,
                outer.bottom - Math.max(bottomRows, outer.br), rightColor);
    }

    private static int argb(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private static void row(List<PaintCommand> out, int left, int y, int right, int color) {
        rect(out, left, y, right, y + 1, color);
    }

    private static void rect(List<PaintCommand> out, int left, int top, int right, int bottom, int color) {
        if (left < right && top < bottom && (color >>> 24) != 0) {
            out.add(PaintCommand.background(left, top, right, bottom, color));
        }
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
            if (right - left <= 2 || bottom - top <= 2) return null;
            return new Shape(left + 1, top + 1, right - 1, bottom - 1,
                    tl - 1, tr - 1, br - 1, bl - 1);
        }

        int leftAt(int y) {
            return left + Math.max(cornerInset(tl, y - top), cornerInset(bl, bottom - y - 1));
        }

        int rightAt(int y) {
            return right - Math.max(cornerInset(tr, y - top), cornerInset(br, bottom - y - 1));
        }

        // 取整行距圆心最远的边，向内取整；条带的四角都在解析圆弧内。
        private static int cornerInset(int radius, int row) {
            if (radius <= 0 || row >= radius) return 0;
            double distance = radius - row;
            return (int) Math.ceil(radius - Math.sqrt((double) radius * radius - distance * distance));
        }
    }
}
