package club.heiqi.uilib.ui.scene.text;

/** 中性行内对象的本批次度量；offset 指向显式声明的 U+FFFC，坐标不含宿主类型。 */
public final class InlineBox {
    public final int offset;
    public final float width, height, baseline;

    public InlineBox(int offset, float width, float height, float baseline) {
        if (offset < 0
                || !Float.isFinite(width)
                || !Float.isFinite(height)
                || !Float.isFinite(baseline)
                || width < 0
                || height < 0
                || baseline < 0
                || baseline > height) throw new IllegalArgumentException("无效行内对象度量");
        this.offset = offset;
        this.width = width;
        this.height = height;
        this.baseline = baseline;
    }
}
