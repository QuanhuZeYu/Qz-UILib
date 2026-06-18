package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 光标解析器 —— 沿 hoveredNode 祖先链查找首个显式声明的光标。
 *
 * <h3>核心不变量</h3>
 * <ul>
 *   <li><b>只读</b>：仅调 {@link SceneNode#__getParent()} + {@link SceneNode#getCursor()}，
 *       绝不写任何节点状态/标脏（I7 零标脏）。</li>
 *   <li><b>纯算法无状态</b>：所有方法 static，不持任何字段。</li>
 *   <li><b>祖先链级联解析</b>：沿 {@code hoveredNode} 的 {@code __getParent()} 链向根查找，
 *       返回首个声明 cursor（非 null）的节点声明的值；都无声明返回 {@link SceneCursor#DEFAULT}。</li>
 *   <li><b>null 安全</b>：{@code hoveredNode == null} → 立即返回 {@link SceneCursor#DEFAULT}。</li>
 * </ul>
 *
 * <h3>与旧栈 DocumentCursorResolver 的差异</h3>
 * <p>旧栈解析涉样式表层（CSS 级联、important、pseudo-states），新栈 cursor 直读
 * SceneNode 属性槽（声明式，组件在 build 时设 cursor）。这是简化正确模型：
 * cursor 是交互投影，不走样式表级联，组件声明即用。</p>
 */
public final class SceneCursorResolver {

    private SceneCursorResolver() {}

    /**
     * 沿 hoveredNode 祖先链向上查找首个声明 cursor 的节点，返回其 cursor 值。
     *
     * @param hoveredNode 当前 hover 节点（可为 null）
     * @return 解析后的光标样式；hoveredNode==null 或整条祖先链无声明返回 {@link SceneCursor#DEFAULT}
     */
    public static SceneCursor resolve(SceneNode hoveredNode) {
        if (hoveredNode == null) {
            return SceneCursor.DEFAULT;
        }
        for (SceneNode current = hoveredNode; current != null; current = current.__getParent()) {
            SceneCursor cursor = current.getCursor();
            if (cursor != null) {
                return cursor;
            }
        }
        return SceneCursor.DEFAULT;
    }
}
