package club.heiqi.uilib.ui.scene.text;

/** 捕获调用者的采样需求；核心不读取GUI或world状态。 */
public final class RasterSampling {
    public static final RasterSampling LOGICAL = new RasterSampling(1, 0);
    public final float pixelsPerLayoutUnit;
    public final long revision;

    public RasterSampling(float density, long revision) {
        if (!Float.isFinite(density) || density <= 0) throw new IllegalArgumentException("无效采样密度");
        pixelsPerLayoutUnit = density;
        this.revision = revision;
    }
}
