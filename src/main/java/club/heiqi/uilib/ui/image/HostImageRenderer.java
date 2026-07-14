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

    /**
     * 在完整状态围栏中绘制不可信宿主内容。
     *
     * <p>默认实现保留第三方旧 renderer 的源码兼容；生产 Minecraft renderer 会覆盖此方法并验证恢复。</p>
     */
    default HostImageRenderOutcome renderGuarded(HostImageSource source, int left, int top, int right, int bottom) {
        try {
            render(source, left, top, right, bottom);
            return HostImageRenderOutcome.success();
        } catch (RuntimeException exception) {
            return HostImageRenderOutcome.failure("render", exception, true, exception.getClass().getSimpleName());
        } catch (LinkageError error) {
            return HostImageRenderOutcome.failure("render", error, true, error.getClass().getSimpleName());
        }
    }
}
