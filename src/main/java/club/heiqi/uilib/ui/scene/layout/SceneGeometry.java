package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver;

/**
 * 场景只读几何工具。
 *
 * <p>本类只读取 {@link LayoutBox} 与 parent 链，不写节点、不标脏，属于 I11 逃生舱①只读几何测量。</p>
 */
public final class SceneGeometry {

    private SceneGeometry() {
    }

    /**
     * 沿 parent 链累加 LayoutBox 偏移，返回 node 在指定根坐标系下的绝对盒。
     *
     * <p>{@code rootAbsX/rootAbsY} 传 0 即得 host 局部坐标；全程只读，不写节点、不打脏标记。</p>
     *
     * @param node 待测量节点，可为 null
     * @param rootAbsX 根坐标系 X 偏移
     * @param rootAbsY 根坐标系 Y 偏移
     * @return 节点绝对盒；节点或自身布局缺失时返回零盒
     */
    public static SceneAnchorResolver.AnchorRect absoluteBox(SceneNode node, int rootAbsX, int rootAbsY) {
        if (node == null || !(node.getCachedLayout() instanceof LayoutBox)) {
            return new SceneAnchorResolver.AnchorRect(0, 0, 0, 0);
        }

        LayoutBox selfBox = (LayoutBox) node.getCachedLayout();
        int x = rootAbsX;
        int y = rootAbsY;
        SceneNode current = node;
        while (current != null) {
            Object cachedLayout = current.getCachedLayout();
            if (cachedLayout instanceof LayoutBox) {
                LayoutBox box = (LayoutBox) cachedLayout;
                x += box.getX();
                y += box.getY();
            }
            current = current.__getParent();
        }
        return new SceneAnchorResolver.AnchorRect(x, y, selfBox.getWidth(), selfBox.getHeight());
    }
}
