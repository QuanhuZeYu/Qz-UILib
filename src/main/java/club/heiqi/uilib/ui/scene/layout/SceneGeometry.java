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
     * 计算进入 parent 子树时的 Y 基准，统一注入 scrollable 父节点的滚动偏移。
     *
     * <p>scrollOffsetY 只移动 scrollable 节点的子内容，不移动 scrollable 节点自己；
     * 因此判定对象必须是 parent，而不是即将测量的子节点。</p>
     *
     * @param parent 父节点，可为 null
     * @param parentAbsY 父节点自身绝对 Y
     * @return 子节点使用的绝对 Y 基准
     */
    public static int childYBase(SceneNode parent, int parentAbsY) {
        if (parent != null && parent.isScrollable()) {
            return parentAbsY - parent.getScrollOffsetY();
        }
        return parentAbsY;
    }

    /**
     * 计算滚动节点的最大滚动偏移量。
     *
     * <p>闭式推导：内容底边在视口内容坐标 = maxChildBottom（含 padTop 累进）；
     * 视口可视区底边（内容区底）= boxH - padBottom；需滚动量 =
     * maxChildBottom - (boxH - padBottom) = maxChildBottom + padBottom - boxH。
     * 对「视口直接挂单子 content」和「视口直接挂多子 items」天然统一。</p>
     *
     * @param scrollable 滚动节点（isScrollable==true）
     * @return maxScrollY，box==null 或无子或不足视口时返回 0
     */
    public static int maxScrollY(SceneNode scrollable) {
        if (scrollable == null || !scrollable.isScrollable()) {
            return 0;
        }
        Object cachedLayout = scrollable.getCachedLayout();
        if (!(cachedLayout instanceof LayoutBox)) {
            return 0;
        }
        LayoutBox box = (LayoutBox) cachedLayout;
        int maxChildBottom = 0;
        for (SceneNode child : scrollable.__getChildren()) {
            Object childCachedLayout = child.getCachedLayout();
            if (childCachedLayout instanceof LayoutBox) {
                LayoutBox childBox = (LayoutBox) childCachedLayout;
                maxChildBottom = Math.max(maxChildBottom, childBox.getY() + childBox.getHeight());
            }
        }
        return Math.max(0, maxChildBottom + scrollable.getPaddingBottom() - box.getHeight());
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
            SceneNode parent = current.__getParent();
            if (parent != null) {
                y = childYBase(parent, y);
            }
            current = parent;
        }
        return new SceneAnchorResolver.AnchorRect(x, y, selfBox.getWidth(), selfBox.getHeight());
    }

    /**
     * 计算节点与所有 scrollable 祖先视口裁剪框的可见交集。
     *
     * <p>节点盒使用 {@link #absoluteBox(SceneNode, int, int)}，会注入祖先滚动偏移；
     * 祖先视口框使用祖先自身的绝对 LayoutBox，不含其自身 scrollOffsetY。</p>
     *
     * @param node 待测量节点，可为 null
     * @param rootAbsX 根坐标系 X 偏移
     * @param rootAbsY 根坐标系 Y 偏移
     * @return 可见交集盒；节点缺失或完全被裁掉时返回零尺寸盒
     */
    public static SceneAnchorResolver.AnchorRect visibleBoxWithinScrollableAncestors(
            SceneNode node, int rootAbsX, int rootAbsY) {
        SceneAnchorResolver.AnchorRect visible = absoluteBox(node, rootAbsX, rootAbsY);
        if (visible.getWidth() <= 0 || visible.getHeight() <= 0) {
            return visible;
        }

        SceneNode current = node != null ? node.__getParent() : null;
        while (current != null) {
            if (current.isScrollable()) {
                SceneAnchorResolver.AnchorRect viewport = absoluteBox(current, rootAbsX, rootAbsY);
                visible = intersect(visible, viewport);
                if (visible.getWidth() <= 0 || visible.getHeight() <= 0) {
                    return visible;
                }
            }
            current = current.__getParent();
        }
        return visible;
    }

    /**
     * 求两个锚点盒子的矩形交集。
     *
     * @param first 第一个盒子
     * @param second 第二个盒子
     * @return 交集盒；无交集时返回零尺寸盒
     */
    public static SceneAnchorResolver.AnchorRect intersect(
            SceneAnchorResolver.AnchorRect first,
            SceneAnchorResolver.AnchorRect second) {
        int left = Math.max(first.getX(), second.getX());
        int top = Math.max(first.getY(), second.getY());
        int right = Math.min(first.getX() + first.getWidth(), second.getX() + second.getWidth());
        int bottom = Math.min(first.getY() + first.getHeight(), second.getY() + second.getHeight());
        return new SceneAnchorResolver.AnchorRect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }
}
