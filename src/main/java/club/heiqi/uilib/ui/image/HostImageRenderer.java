package club.heiqi.uilib.ui.image;

/**
 * 普通宿主图片渲染委托。
 *
 * <p>该轻量路径只处理 texture 与 bitmap；ItemStack 图标由 {@link ItemIconRenderer} 当帧直绘。
 * 自定义实现属于受信任的窄委托：架构禁令禁用原版包装类（Tessellator 等），绘制走直接 GL
 * 或 UILib 自有管线；返回前不得遗留 program、VAO/VBO、client array、matrix stack
 * 或其它无 GL error 的宿主状态漂移。</p>
 */
public interface HostImageRenderer extends AutoCloseable {

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

    /** 释放 renderer 自身拥有的宿主资源。 */
    @Override
    default void close() {
        // 大多数普通图片 renderer 不持有资源。
    }
}
