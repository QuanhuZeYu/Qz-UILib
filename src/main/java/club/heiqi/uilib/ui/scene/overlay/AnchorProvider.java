package club.heiqi.uilib.ui.scene.overlay;

/**
 * 只读几何探针：每帧由 host 调用，返回 trigger 在 host 局部坐标系下的绝对盒。
 *
 * <p>属 I11 逃生舱①只读几何测量，只读 LayoutBox，不写 signal、不打脏标记。</p>
 */
@FunctionalInterface
public interface AnchorProvider {

    /**
     * 获取 trigger 在 host 局部坐标系下的绝对盒。
     *
     * @return trigger 的 host 局部绝对盒
     */
    SceneAnchorResolver.AnchorRect get();
}
