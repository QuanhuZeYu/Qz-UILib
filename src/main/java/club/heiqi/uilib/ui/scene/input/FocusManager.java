package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.scene.node.SceneNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 焦点管理器 —— I4a 全局唯一焦点 + focusable 注册表 + Tab 遍历。
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li><b>全局唯一焦点</b>：持有一个 {@link #focusedNode}，同一时刻最多一个节点有焦点。</li>
 *   <li><b>外挂注册表</b>：{@code focusables} 显式登记可聚焦节点，强引用 + {@link Owner#onCleanup} 回收，
 *       与 handler registry / interactionStates 同款生命周期。</li>
 *   <li><b>焦点切换写 signal</b>：通过共享的 {@link #interactionStates} 引用，
 *       对旧焦点调用 {@code writeFocused(false)}、新焦点调用 {@code writeFocused(true)}，
 *       接通 I3 留下的 dead code（I11 白名单②）。</li>
 *   <li><b>Tab/Shift+Tab 遍历</b>：按根节点 DOM 前序排序 focusables 后循环遍历。</li>
 * </ul>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>不维护常驻"焦点链"——key event bubble 沿 {@code __getParent()} 实时冒泡即可。</li>
 *   <li>tabindex 不做（YAGNI），仅注册过的节点参与 Tab 环。</li>
 *   <li>focusedNode 真值即时更新（requestFocus 内即时改），signal 暴露延迟到 flush——与 hover 同构。</li>
 * </ul>
 */
public class FocusManager {

    /** 全局唯一焦点节点，初始 null */
    private SceneNode focusedNode;

    /** 外挂可聚焦节点注册表（强引用，Owner.onCleanup 回收） */
    private final Set<SceneNode> focusables;

    /** 共享的交互状态表（Router 构造注入），用于写 focused signal */
    private final Map<SceneNode, SceneInteractionState> interactionStates;

    /**
     * 当前帧的根节点（Router 在 route 开始时设置，供 focusNext/focusPrevious 做 DOM 前序遍历）。
     * 仅在 route 进行期间有效，不可跨帧或离线使用。
     */
    private SceneNode root;

    /**
     * 构造焦点管理器。
     *
     * @param interactionStates Router 持有的交互状态表（焦点切换时写 focused signal）
     */
    public FocusManager(Map<SceneNode, SceneInteractionState> interactionStates) {
        if (interactionStates == null) {
            throw new IllegalArgumentException("interactionStates 不可为 null");
        }
        this.interactionStates = interactionStates;
        this.focusables = new HashSet<SceneNode>();
        this.focusedNode = null;
        this.root = null;
    }

    // ==================== 查询 ====================

    /**
     * @return 当前焦点节点，无焦点时返回 null
     */
    public SceneNode getFocusedNode() {
        return focusedNode;
    }

    /**
     * 设置当前帧根节点（包级，Router 在 route 开始时调用）。
     *
     * @param root 场景树根节点
     */
    void setRoot(SceneNode root) {
        this.root = root;
    }

    /**
     * 判断指定节点是否已注册为 focusable（测试探针）。
     *
     * @param node 目标节点
     * @return true 表示节点在 focusables 注册表中
     */
    boolean __isFocusable(SceneNode node) {
        return focusables.contains(node);
    }

    /** 判断指定 occurrence 树内是否存在可参与 Tab 的节点。 */
    boolean __hasFocusableInRoot(SceneNode root) {
        return !getSortedFocusables(root).isEmpty();
    }

    /**
     * 从命中链最深处向 root 找首个已注册 focusable。
     * @param hitChain hitTester 返回的命中链（index 0=root，末尾=最深命中）
     * @return 首个 focusable 节点，无则 null
     */
    SceneNode findDeepestFocusable(List<SceneNode> hitChain) {
        if (hitChain == null) {
            return null;
        }
        for (int i = hitChain.size() - 1; i >= 0; i--) {
            SceneNode node = hitChain.get(i);
            if (focusables.contains(node)) {
                return node;
            }
        }
        return null;
    }

    // ==================== 注册 ====================

    /**
     * 将节点登记为可聚焦（纳入 Tab 环）。
     *
     * <p>若当前处于 {@link Owner} 作用域内，自动登记 {@code onCleanup} 回调，
     * 随组件卸载自动从 focusables 中移除。</p>
     *
     * @param node 目标节点
     */
    public void registerFocusable(SceneNode node) {
        if (node == null) return;
        focusables.add(node);
        Owner current = Owner.current();
        if (current != null) {
            current.onCleanup(() -> unregisterFocusable(node));
        }
    }

    /**
     * 仅将节点加入 focusables，不登记 onCleanup 回调。
     *
     * <p>供 {@link club.heiqi.uilib.ui.scene.runtime.SceneRuntime#focusable} 的
     * signal 驱动重载使用：该重载自己登记一次卸载兜底 cleanup，effect 每次 enabled=true
     * 重跑时调本方法纯 add，避免重复登记 cleanup 累积。</p>
     *
     * @param node 目标节点
     */
    void addFocusable(SceneNode node) {
        if (node == null) return;
        focusables.add(node);
    }

    /**
     * 将节点从可聚焦注册表中移除（退出 Tab 环）。
     *
     * <p>用于兑现 package-info R9 契约「disabled 不可聚焦」：enabled=false 时控件应退出 Tab 环，
     * 既不再被 Tab 遍历命中，也不再被隐式 POINTER_DOWN 聚焦（findDeepestFocusable 只查注册表）。
     * 若该节点恰为当前焦点，立即清失焦点（writeFocused(false) + focusedNode=null），
     * 避免焦点滞留在一个已退出 Tab 环的节点上。</p>
     *
     * <p>重复调用安全（Set.remove 幂等）。组件卸载时由 {@link #registerFocusable} 登记的
     * onCleanup 兜底也会调本方法等价的 remove，幂等无副作用。</p>
     *
     * @param node 目标节点
     */
    public void unregisterFocusable(SceneNode node) {
        if (node == null) return;
        focusables.remove(node);
        // 若该节点是当前焦点，清失焦点（守 R9：disabled 不可聚焦）
        if (focusedNode == node) {
            SceneInteractionState st = interactionStates.get(node);
            if (st != null) {
                st.writeFocused(false);
            }
            focusedNode = null;
        }
    }

    // ==================== 焦点切换 ====================

    /**
     * 请求将焦点切换到指定节点。
     *
     * <p>对旧焦点写 {@code writeFocused(false)}，新焦点写 {@code writeFocused(true)}。
     * 若节点未声明关心 focus（interactionStates 中无该节点的状态容器），短路跳过写 signal。</p>
     *
     * <p>focusedNode 真值即时更新本字段，signal 暴露延迟到帧末 flush——与 hover 同构。</p>
     *
     * @param node 要聚焦的节点
     * @return true 表示焦点切换成功（或已经是当前焦点）
     */
    public boolean requestFocus(SceneNode node) {
        if (node == null) return false;
        // 已经是当前焦点，无需切换
        if (focusedNode == node) return true;

        // 旧焦点 blur
        SceneNode old = focusedNode;
        if (old != null) {
            SceneInteractionState oldState = interactionStates.get(old);
            if (oldState != null) {
                oldState.writeFocused(false);
            }
        }

        // 新焦点 focus
        focusedNode = node;
        SceneInteractionState newState = interactionStates.get(node);
        if (newState != null) {
            newState.writeFocused(true);
        }
        return true;
    }

    // ==================== Tab 遍历 ====================

    /**
     * 按 Tab 顺序聚焦下一个 focusable（正向）。
     *
     * <p>按当前 {@link #root} 的 DOM 前序遍历排序 focusables，找当前焦点之后的下一个，
     * 循环至列表头。无焦点时聚焦首个 focusable（用户拍板 D2-A）。</p>
     *
     * <p>若 focusables 为空、root 为 null 或排序后列表为空，无副作用。</p>
     */
    public void focusNext() {
        if (root == null) return;
        List<SceneNode> sorted = getSortedFocusables(root);
        if (sorted.isEmpty()) return;

        int idx;
        if (focusedNode == null) {
            // D2-A：无焦点时聚焦首个
            idx = -1; // -1 表示无焦点，取首个
        } else {
            idx = sorted.indexOf(focusedNode);
            if (idx < 0) {
                // 当前焦点不在排序列表中（可能已 unregister），取首个
                idx = -1;
            }
        }
        int nextIdx = idx + 1;
        if (nextIdx >= sorted.size()) {
            nextIdx = 0; // 循环至头
        }
        requestFocus(sorted.get(nextIdx));
    }

    /**
     * 按 Shift+Tab 顺序聚焦上一个 focusable（反向）。
     *
     * <p>与 {@link #focusNext} 对称，方向相反。无焦点时聚焦最后一个 focusable。</p>
     */
    public void focusPrevious() {
        if (root == null) return;
        List<SceneNode> sorted = getSortedFocusables(root);
        if (sorted.isEmpty()) return;

        int idx;
        if (focusedNode == null) {
            // 无焦点时取最后一个
            idx = sorted.size(); // size 表示"倒数第一个"
        } else {
            idx = sorted.indexOf(focusedNode);
            if (idx < 0) {
                idx = sorted.size();
            }
        }
        int prevIdx = idx - 1;
        if (prevIdx < 0) {
            prevIdx = sorted.size() - 1; // 循环至尾
        }
        requestFocus(sorted.get(prevIdx));
    }

    // ==================== 清空焦点 ====================

    /**
     * 清空当前焦点（blur）。
     *
     * <p>对当前焦点节点写 {@code writeFocused(false)}，然后将 {@code focusedNode} 置为 null。</p>
     */
    public void clearFocus() {
        if (focusedNode != null) {
            SceneInteractionState st = interactionStates.get(focusedNode);
            if (st != null) {
                st.writeFocused(false);
            }
            focusedNode = null;
        }
    }

    // ==================== 测试探针 ====================

    /**
     * @return 当前焦点节点（测试探针，等价于 getFocusedNode）
     */
    SceneNode __getFocusedNode() {
        return focusedNode;
    }

    /**
     * @return focusables 的不可变快照（测试探针）
     */
    Set<SceneNode> __getFocusables() {
        return new HashSet<SceneNode>(focusables);
    }

    // ==================== 内部排序 ====================

    /**
     * 按 DOM 前序遍历排序所有已注册且仍在树中的 focusable 节点。
     *
     * <p>遍历策略：先访问 node 自身，若在 focusables 中则加入结果；
     * 再递归遍历子节点（深度优先）。结果列表保持 DOM 前序。</p>
     *
     * @param root 遍历根节点
     * @return DOM 前序排序后的 focusable 列表
     */
    private List<SceneNode> getSortedFocusables(SceneNode root) {
        List<SceneNode> result = new ArrayList<SceneNode>();
        if (root == null) return result;
        collectFocusablesPreOrder(root, result);
        return result;
    }

    /**
     * 递归 DOM 前序遍历，收集属于 focusables 的节点。
     */
    private void collectFocusablesPreOrder(SceneNode node, List<SceneNode> result) {
        if (node == null) return;
        // 前序：先访问自身
        if (focusables.contains(node)) {
            result.add(node);
        }
        // 再递归子节点
        for (SceneNode child : node.__getChildren()) {
            collectFocusablesPreOrder(child, result);
        }
    }
}
