package club.heiqi.uilib.ui.scene.text.font.nativefont;

/** 脱离 native 地址及 face 生命周期的数学字形事实。单位与 FontFace 度量一致。 */
public final class MathData {
    private MathData() {}

    /** MATH 竖向 variant 的完整高度 advance；横向 advance 必须另查同一 HB font。 */
    public static final class Variant {
        public final int glyphId;
        public final float verticalAdvance;

        public Variant(int glyphId, float verticalAdvance) {
            this.glyphId = glyphId;
            this.verticalAdvance = verticalAdvance;
        }
    }
}
