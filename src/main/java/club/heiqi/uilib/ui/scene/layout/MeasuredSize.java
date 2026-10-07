package club.heiqi.uilib.ui.scene.layout;
public final class MeasuredSize {
    public final int width, height;
    public MeasuredSize(int width, int height) { if (width < 0 || height < 0) throw new IllegalArgumentException("测量尺寸不能为负"); this.width = width; this.height = height; }
}
