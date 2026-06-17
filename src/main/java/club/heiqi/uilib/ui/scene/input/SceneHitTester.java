package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 只读命中测试器。
 *
 * <p>深度优先 + 子节点优先遍历场景树，根据指针绝对坐标确定命中链。
 * 全程只读，绝不调用任何 setter/markXxx/appendChild/removeChild，
 * 保证零副作用（I7/I11 硬不变量）。</p>
 *
 * <h3>坐标体系</h3>
 * <p>{@code pointerX/pointerY} 为画布逻辑坐标（不预先叠加 rootAbsX）。
 * {@code rootAbsX/rootAbsY} 是整树的宿主平移量，hitTester 内部将其累加到各节点
 * 的 {@link LayoutBox} 偏移上得到绝对像素位置，再与原始逻辑坐标比较。
 * 调用方无需手动变换指针坐标，整树平移零成本命中。</p>
 *
 * <h3>命中判定</h3>
 * <p>bounds 为左闭右开区间 {@code [absX, absX+w) × [absY, absY+h)}。
 * 同层级从尾到头遍历（后添加子节点优先，对应更高 z-order）。
 * cachedLayout 为 null 的节点连同子树整体跳过。</p>
 */
public class SceneHitTester {

    /**
     * 对场景树执行命中测试。
     *
     * @param root      场景树根节点
     * @param pointerX  指针画布逻辑 X 坐标（不预先叠加 rootAbsX）
     * @param pointerY  指针画布逻辑 Y 坐标（不预先叠加 rootAbsY）
     * @param rootAbsX  根节点在宿主中的绝对 X 偏移（整树平移口）
     * @param rootAbsY  根节点在宿主中的绝对 Y 偏移（整树平移口）
     * @return root→target 命中链（索引 0=root，末尾=最深 target），未命中返回空 List
     */
    public List<SceneNode> hitTest(SceneNode root, int pointerX, int pointerY,
                                    int rootAbsX, int rootAbsY) {
        if (root == null) {
            return Collections.emptyList();
        }
        return hitTestRecursive(root, pointerX, pointerY, rootAbsX, rootAbsY);
    }

    /**
     * 递归命中测试。
     *
     * @param node        当前节点
     * @param pointerX    指针绝对 X
     * @param pointerY    指针绝对 Y
     * @param parentAbsX  父节点绝对 X
     * @param parentAbsY  父节点绝对 Y
     * @return 从当前节点开始的命中链，未命中返回空 List
     */
    private List<SceneNode> hitTestRecursive(SceneNode node,
                                              int pointerX, int pointerY,
                                              int parentAbsX, int parentAbsY) {
        LayoutBox layout = (LayoutBox) node.getCachedLayout();
        if (layout == null) {
            // cachedLayout 缺失：节点连同子树整体跳过
            return Collections.emptyList();
        }

        int absX = parentAbsX + layout.getX();
        int absY = parentAbsY + layout.getY();
        int w = layout.getWidth();
        int h = layout.getHeight();

        // 左闭右开区间命中判定
        if (pointerX < absX || pointerX >= absX + w
                || pointerY < absY || pointerY >= absY + h) {
            return Collections.emptyList();
        }

        // 深度优先子节点：从尾到头遍历（后添加 = 更高 z-order）
        List<SceneNode> children = node.__getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            SceneNode child = children.get(i);
            List<SceneNode> childChain = hitTestRecursive(child, pointerX, pointerY, absX, absY);
            if (!childChain.isEmpty()) {
                List<SceneNode> result = new ArrayList<SceneNode>(childChain.size() + 1);
                result.add(node);
                result.addAll(childChain);
                return result;
            }
        }

        // 无子节点命中，当前节点自身为目标
        List<SceneNode> result = new ArrayList<SceneNode>(1);
        result.add(node);
        return result;
    }
}
