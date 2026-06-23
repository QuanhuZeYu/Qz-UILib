package club.heiqi.uilib.ui.scene.overlay;

import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 只读几何探针：每帧由 host 调用，返回 trigger 在 host 局部坐标系下的绝对盒。
 *
 * <p>属 I11 逃生舱①只读几何测量，只读 LayoutBox，不写 signal、不打脏标记。</p>
 */
@FunctionalInterface
public interface AnchorProvider {

    /**
     * 基于 trigger 节点创建节点感知锚点探针。
     *
     * @param trigger 锚点节点
     * @return 可返回 trigger 节点的只读锚点探针
     */
    static AnchorProvider forNode(SceneNode trigger) {
        return new AnchorProvider() {
            @Override
            public AnchorRect get() {
                return SceneGeometry.absoluteBox(trigger, 0, 0);
            }

            @Override
            public SceneNode getNode() {
                return trigger;
            }
        };
    }

    /**
     * 获取 trigger 在 host 局部坐标系下的绝对盒。
     *
     * @return trigger 的 host 局部绝对盒
     */
    AnchorRect get();

    /**
     * 返回锚点节点；旧式矩形探针可返回 null。
     *
     * @return 锚点节点，未知时返回 null
     */
    default SceneNode getNode() {
        return null;
    }
}
