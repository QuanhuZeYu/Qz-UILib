package club.heiqi.uilib.ui.layout;

/**
 * 组件响应式布局规格。
 */
public class UiLayoutSpec {

    private UiAnchor anchor = UiAnchor.TOP_LEFT;
    private UiInsets margin = UiInsets.ZERO;
    private UiLength width = UiLength.auto();
    private UiLength height = UiLength.auto();
    private UiLength flexBasis = UiLength.auto();
    private int minWidth;
    private int minHeight;
    private int maxWidth = Integer.MAX_VALUE;
    private int maxHeight = Integer.MAX_VALUE;
    private int offsetX;
    private int offsetY;
    private float grow;
    private boolean fill;

    public UiAnchor getAnchor() {
        return anchor;
    }

    public UiLayoutSpec setAnchor(UiAnchor anchor) {
        this.anchor = anchor == null ? UiAnchor.TOP_LEFT : anchor;
        return this;
    }

    public UiInsets getMargin() {
        return margin;
    }

    public UiLayoutSpec setMargin(UiInsets margin) {
        this.margin = margin == null ? UiInsets.ZERO : margin;
        return this;
    }

    public UiLength getWidth() {
        return width;
    }

    public UiLayoutSpec setWidth(UiLength width) {
        this.width = width == null ? UiLength.auto() : width;
        return this;
    }

    public UiLength getHeight() {
        return height;
    }

    public UiLayoutSpec setHeight(UiLength height) {
        this.height = height == null ? UiLength.auto() : height;
        return this;
    }

    /**
     * 获取 flex 主轴初始尺寸。
     *
     * @return 主轴 basis
     */
    public UiLength getFlexBasis() {
        return flexBasis;
    }

    /**
     * 设置 flex 主轴初始尺寸。
     *
     * @param flexBasis 主轴 basis
     * @return 当前规格
     */
    public UiLayoutSpec setFlexBasis(UiLength flexBasis) {
        this.flexBasis = flexBasis == null ? UiLength.auto() : flexBasis;
        return this;
    }

    public int getMinWidth() {
        return minWidth;
    }

    public UiLayoutSpec setMinWidth(int minWidth) {
        this.minWidth = Math.max(0, minWidth);
        return this;
    }

    public int getMinHeight() {
        return minHeight;
    }

    public UiLayoutSpec setMinHeight(int minHeight) {
        this.minHeight = Math.max(0, minHeight);
        return this;
    }

    public int getMaxWidth() {
        return maxWidth;
    }

    public UiLayoutSpec setMaxWidth(int maxWidth) {
        this.maxWidth = Math.max(0, maxWidth);
        return this;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public UiLayoutSpec setMaxHeight(int maxHeight) {
        this.maxHeight = Math.max(0, maxHeight);
        return this;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public UiLayoutSpec setOffsetX(int offsetX) {
        this.offsetX = offsetX;
        return this;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public UiLayoutSpec setOffsetY(int offsetY) {
        this.offsetY = offsetY;
        return this;
    }

    public float getGrow() {
        return grow;
    }

    public UiLayoutSpec setGrow(float grow) {
        this.grow = Math.max(0.0F, grow);
        return this;
    }

    public boolean isFill() {
        return fill;
    }

    public UiLayoutSpec setFill(boolean fill) {
        this.fill = fill;
        return this;
    }
}
