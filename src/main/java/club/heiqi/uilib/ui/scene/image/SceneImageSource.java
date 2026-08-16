package club.heiqi.uilib.ui.scene.image;

/**
 * 平台中立的 scene 图片源标记。
 *
 * <p>核心层仅保留对象身份并把它固化进绘制命令；具体内容由宿主渲染适配器解释。</p>
 */
public interface SceneImageSource {

    /**
     * 稳定注册键（如物品注册名:meta），供 {@link ItemRenderTierRegistry} 渲染分级映射；
     * 默认 null = 无键（平台适配器按内容覆写）。
     *
     * @return 稳定注册键或 {@code null}
     */
    default String registryKey() {
        return null;
    }
}
