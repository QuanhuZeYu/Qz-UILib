package club.heiqi.uilib.font.glyph;

import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.Path2D;

import club.heiqi.uilib.font.latex.layout.ProceduralAccentSpec;

/** 固定笔画的数学重音 profile；尺寸先作用于中心路径，描边后绝不横向缩放。 */
public final class ProceduralAccentShape {
    public static final int PROFILE_REVISION = 1;

    private ProceduralAccentShape() {}

    public static Shape create(ProceduralAccentSpec spec, int rasterSize) {
        if (spec == null || rasterSize <= 0 || spec.getProfileRevision() != PROFILE_REVISION) {
            throw new IllegalArgumentException("不支持的程序重音 profile 或字号");
        }
        double unit = rasterSize / 65536.0;
        double width = spec.getWidthUnits() * unit;
        double height = spec.getHeightUnits() * unit;
        double stroke = spec.getStrokeUnits() * unit;
        if (stroke >= width || stroke >= height) {
            throw new IllegalArgumentException("程序重音 stroke 必须小于 width 和 height");
        }
        double inset = stroke / 2.0;
        double span = width - stroke;
        Path2D.Double path = new Path2D.Double();
        if (spec.getKind() == ProceduralAccentSpec.Kind.HAT) {
            path.moveTo(inset, -inset);
            path.lineTo(inset + span / 2.0, -height + inset);
            path.lineTo(inset + span, -inset);
        } else {
            path.moveTo(inset, -height * .3);
            path.curveTo(inset + span * .2, -height * 1.1,
                    inset + span * .3, -height * 1.1, inset + span * .5, -height * .5);
            path.curveTo(inset + span * .7, height * .1,
                    inset + span * .8, height * .1, inset + span, -height * .7);
        }
        Shape stroked = new BasicStroke((float) stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)
                .createStrokedShape(path);
        // AWT 对 cubic 的 clip-dependent subdivision 会使同一长波浪在各 tile 产生不同覆盖率。
        // 在全局坐标按固定 raster profile 展平一次，所有 tile 接收完全相同的直线段。
        Path2D.Double flattened = new Path2D.Double();
        flattened.append(new java.awt.geom.FlatteningPathIterator(stroked.getPathIterator(null), 1.0 / 64.0, 16), false);
        return flattened;
    }
}
