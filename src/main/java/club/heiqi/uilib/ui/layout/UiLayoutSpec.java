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
    private UiAlignSelf alignSelf = UiAlignSelf.AUTO;
    private boolean fill;

    /**
     * @deprecated 仅保留给旧式定位容器使用，网页主线布局不应再依赖 anchor。
     */
    @Deprecated
    public UiAnchor getAnchor() {
        return anchor;
    }

    /**
     * @deprecated 仅保留给旧式定位容器使用，网页主线布局不应再依赖 anchor。
     */
    @Deprecated
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

    /**
     * @deprecated 仅保留给旧式定位容器使用，网页主线布局不应再依赖 offsetX。
     */
    @Deprecated
    public int getOffsetX() {
        return offsetX;
    }

    /**
     * @deprecated 仅保留给旧式定位容器使用，网页主线布局不应再依赖 offsetX。
     */
    @Deprecated
    public UiLayoutSpec setOffsetX(int offsetX) {
        this.offsetX = offsetX;
        return this;
    }

    /**
     * @deprecated 仅保留给旧式定位容器使用，网页主线布局不应再依赖 offsetY。
     */
    @Deprecated
    public int getOffsetY() {
        return offsetY;
    }

    /**
     * @deprecated 仅保留给旧式定位容器使用，网页主线布局不应再依赖 offsetY。
     */
    @Deprecated
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

    /**
     * 获取子项交叉轴自对齐方式。
     *
     * @return 自对齐方式
     */
    public UiAlignSelf getAlignSelf() {
        return alignSelf;
    }

    /**
     * 设置子项交叉轴自对齐方式。
     *
     * @param alignSelf 自对齐方式
     * @return 当前规格
     */
    public UiLayoutSpec setAlignSelf(UiAlignSelf alignSelf) {
        this.alignSelf = alignSelf == null ? UiAlignSelf.AUTO : alignSelf;
        return this;
    }

    /**
     * @deprecated 仅保留给旧式响应式容器兼容路径使用，网页主线优先使用 width/height/stretch 语义。
     */
    @Deprecated
    public boolean isFill() {
        return fill;
    }

    /**
     * @deprecated 仅保留给旧式响应式容器兼容路径使用，网页主线优先使用 width/height/stretch 语义。
     */
    @Deprecated
    public UiLayoutSpec setFill(boolean fill) {
        this.fill = fill;
        return this;
    }
}
