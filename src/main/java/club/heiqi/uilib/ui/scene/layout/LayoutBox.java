package club.heiqi.uilib.ui.scene.layout;
/** 不可变 logical px 区域；绘制、裁剪和命中使用同一数据。 */
public final class LayoutBox {
    public final int x, y, width, height;
    public LayoutBox(int x, int y, int width, int height) {
        if (width < 0 || height < 0) throw new IllegalArgumentException("区域尺寸不能为负");
        this.x = x; this.y = y; this.width = width; this.height = height;
    }
    public boolean contains(int px, int py) { return px >= x && py >= y && (long) px < (long) x + width && (long) py < (long) y + height; }
    public LayoutBox intersect(LayoutBox other) {
        int left = Math.max(x, other.x), top = Math.max(y, other.y);
        return new LayoutBox(left, top, Math.max(0, Math.min(x + width, other.x + other.width) - left), Math.max(0, Math.min(y + height, other.y + other.height) - top));
    }
}
