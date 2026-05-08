package club.heiqi.uilib.ui.image;

/**
 * 宿主图片渲染委托。
 */
public interface HostImageRenderer {

    /**
     * 在指定区域渲染宿主图片。
     *
     * @param source 图片源
     * @param left 目标区域左边界
     * @param top 目标区域上边界
     * @param right 目标区域右边界
     * @param bottom 目标区域下边界
     */
    void render(HostImageSource source, int left, int top, int right, int bottom);
}
