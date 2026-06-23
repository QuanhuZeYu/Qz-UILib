package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
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
        return hitTestRecursive(root, pointerX, pointerY, rootAbsX, rootAbsY, false, 0, 0, 0, 0);
    }

    /**
     * 递归命中测试。
     *
     * @param node        当前节点
     * @param pointerX    指针绝对 X
     * @param pointerY    指针绝对 Y
     * @param parentAbsX  父节点绝对 X
     * @param parentAbsY  父节点绝对 Y
     * @param hasClip     是否存在祖先裁剪交集
     * @param clipX       当前祖先裁剪交集 X
     * @param clipY       当前祖先裁剪交集 Y
     * @param clipWidth   当前祖先裁剪交集宽度
     * @param clipHeight  当前祖先裁剪交集高度
     * @return 从当前节点开始的命中链，未命中返回空 List
     */
    private List<SceneNode> hitTestRecursive(SceneNode node,
                                              int pointerX, int pointerY,
                                              int parentAbsX, int parentAbsY,
                                              boolean hasClip,
                                              int clipX, int clipY,
                                              int clipWidth, int clipHeight) {
        Object cachedLayout = node.getCachedLayout();
        if (!(cachedLayout instanceof LayoutBox)) {
            // cachedLayout 缺失：节点连同子树整体跳过
            return Collections.emptyList();
        }
        LayoutBox layout = (LayoutBox) cachedLayout;

        int absX = parentAbsX + layout.getX();
        int absY = parentAbsY + layout.getY();
        int w = layout.getWidth();
        int h = layout.getHeight();

        if (hasClip) {
            int clippedLeft = Math.max(absX, clipX);
            int clippedTop = Math.max(absY, clipY);
            int clippedRight = Math.min(absX + w, clipX + clipWidth);
            int clippedBottom = Math.min(absY + h, clipY + clipHeight);
            if (clippedRight - clippedLeft <= 0 || clippedBottom - clippedTop <= 0) {
                return Collections.emptyList();
            }
        }

        // 左闭右开区间命中判定
        if (pointerX < absX || pointerX >= absX + w
                || pointerY < absY || pointerY >= absY + h) {
            return Collections.emptyList();
        }

        // 深度优先子节点：从尾到头遍历（后添加 = 更高 z-order）
        List<SceneNode> children = node.__getChildren();
        int childAbsYBase = SceneGeometry.childYBase(node, absY);
        boolean childHasClip = hasClip;
        int childClipX = clipX;
        int childClipY = clipY;
        int childClipWidth = clipWidth;
        int childClipHeight = clipHeight;
        if (node.isScrollable()) {
            if (childHasClip) {
                int clippedLeft = Math.max(childClipX, absX);
                int clippedTop = Math.max(childClipY, absY);
                int clippedRight = Math.min(childClipX + childClipWidth, absX + w);
                int clippedBottom = Math.min(childClipY + childClipHeight, absY + h);
                childClipX = clippedLeft;
                childClipY = clippedTop;
                childClipWidth = Math.max(0, clippedRight - clippedLeft);
                childClipHeight = Math.max(0, clippedBottom - clippedTop);
            } else {
                childHasClip = true;
                childClipX = absX;
                childClipY = absY;
                childClipWidth = w;
                childClipHeight = h;
            }
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            SceneNode child = children.get(i);
            List<SceneNode> childChain = hitTestRecursive(child, pointerX, pointerY, absX, childAbsYBase,
                    childHasClip, childClipX, childClipY, childClipWidth, childClipHeight);
            if (!childChain.isEmpty()) {
                List<SceneNode> result = new ArrayList<SceneNode>(childChain.size() + 1);
                result.add(node);
                result.addAll(childChain);
                return result;
            }
        }

        // 无子节点命中：检查本节点是否参与命中（pointer-events:none 语义）
        // hitTestable=false 时本节点退出「叶命中目标」候选，命中穿透到父节点
        // （返回空使父递归继续尝试其它兄弟或回退到父自身）。
        // 注意：本检查仅剔除「叶命中目标」资格，不影响上方子节点循环——
        // 即使本节点 hitTestable=false，其子节点仍可命中，且命中时本节点仍作为
        // 结构锚点出现在命中链路径中（见上方 result.add(node)）。
        if (!node.isHitTestable()) {
            return Collections.emptyList();
        }

        // 无子节点命中，当前节点自身为目标
        List<SceneNode> result = new ArrayList<SceneNode>(1);
        result.add(node);
        return result;
    }
}
