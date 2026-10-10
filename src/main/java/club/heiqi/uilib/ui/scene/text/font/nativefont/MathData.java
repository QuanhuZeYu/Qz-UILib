package club.heiqi.uilib.ui.scene.text.font.nativefont;

import club.heiqi.uilib.ui.scene.text.math.MathAxis;

/** 脱离 native 地址及 face 生命周期的数学字形事实。单位与 FontFace 度量一致。 */
public final class MathData {
    private MathData() {}

    /** 同一请求 em 的轴向部件；start/end 为轴起点/终点，fullAdvance 以字形原点定位。 */
    public static final class Part {
        public final int glyphId;
        public final float startConnector, endConnector, fullAdvance;
        public final boolean extender;

        public Part(
                int glyphId,
                float startConnector,
                float endConnector,
                float fullAdvance,
                boolean extender) {
            this.glyphId = glyphId;
            this.startConnector = startConnector;
            this.endConnector = endConnector;
            this.fullAdvance = fullAdvance;
            this.extender = extender;
        }
    }

    /** 不可变轴向构建事实；所有长度与倾斜修正均为请求 em 的逻辑像素。 */
    public static final class Assembly {
        private final java.util.List<Part> parts;
        public final float minOverlap, italicCorrection;
        public final MathAxis axis;

        public Assembly(java.util.List<Part> parts, float minOverlap, float italicCorrection) {
            this(parts, minOverlap, italicCorrection, MathAxis.VERTICAL);
        }

        public Assembly(
                java.util.List<Part> parts,
                float minOverlap,
                float italicCorrection,
                MathAxis axis) {
            this.axis = java.util.Objects.requireNonNull(axis, "axis");
            java.util.ArrayList<Part> copy = new java.util.ArrayList<Part>(parts);
            for (Part part : copy) java.util.Objects.requireNonNull(part, "part");
            this.parts = java.util.Collections.unmodifiableList(copy);
            this.minOverlap = minOverlap;
            this.italicCorrection = italicCorrection;
        }

        public java.util.List<Part> parts() {
            return parts;
        }
    }

    /** MATH variant 的轴向 advance；glyph 的横向 advance 仍须另查同一 HB font。 */
    public static final class Variant {
        public final int glyphId;
        public final float stretchAdvance;

        /** 旧竖向字段；横向快照无此度量，返回 NaN。 */
        public final float verticalAdvance;

        public final MathAxis axis;

        public Variant(int glyphId, float verticalAdvance) {
            this(glyphId, verticalAdvance, MathAxis.VERTICAL);
        }

        public Variant(int glyphId, float stretchAdvance, MathAxis axis) {
            this.glyphId = glyphId;
            this.axis = java.util.Objects.requireNonNull(axis, "axis");
            this.stretchAdvance = stretchAdvance;
            this.verticalAdvance = axis == MathAxis.VERTICAL ? stretchAdvance : Float.NaN;
        }
    }
}
