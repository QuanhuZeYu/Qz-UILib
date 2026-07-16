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
     * <p>默认实现保留第三方旧 renderer 的源码兼容，但 ItemStack 在没有真实围栏时拒绝绘制，
     * 不得伪报状态已恢复。生产运行时会在适配器边界统一添加围栏包装。</p>
     */
    default HostImageRenderOutcome renderGuarded(HostImageSource source, int left, int top, int right, int bottom) {
        if (source != null && source.getKind() == HostImageSource.Kind.ITEM_STACK) {
            return HostImageRenderOutcome.failure("guard", null, false, "item-stack-requires-guard");
        }
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
