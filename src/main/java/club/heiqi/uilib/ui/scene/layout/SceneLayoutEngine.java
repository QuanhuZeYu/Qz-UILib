package club.heiqi.uilib.ui.scene.layout;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 增量布局引擎 —— 实施 I7"干净子树三阶段跳过"的布局核心。
 *
 * <h3>核心思想：双标记决定跳过/下沉/重算</h3>
 * <p>每个节点持有两个布局脏标记：
 * {@code selfLayoutDirty}（自身输入变化）和 {@code descendantLayoutDirty}（后代存在脏节点）。
 * DFS 遍历时，引擎读取这两个布尔标记决定行为：</p>
 * <ul>
 *   <li><b>双 false → 整棵跳过</b>：节点自身和所有后代均干净，直接 return，
 *       复用 {@code cachedLayout}。这是 I7 的核心价值：干净子树零开销。</li>
 *   <li><b>selfLayoutDirty==true → 重算本节点</b>：自身的 text/子节点集合等输入变了，
 *       执行后序遍历：先递归子节点，再基于子节点布局结果重算本节点。</li>
 *   <li><b>selfLayoutDirty==false && descendantLayoutDirty==true → 下沉但不重算</b>：
 *       本节点自身输入未变，cachedLayout 可复用。但后代有脏节点，需递归子节点
 *       （子节点各自再过双标记判定，干净的在入口被跳过）。</li>
 * </ul>
 *
 * <h3>I7 根除 vs 旧栈全量重算</h3>
 * <p>旧 DOM 模型在容器增删时调用 {@code markSubtreeLayoutMutation} 无条件向下递归
 * 刷新全部后代布局版本号 → 稳定子节点被判失效 → 全量重算。新引擎<b>绝对没有</b>
 * 任何 version 号比较、任何向下递归刷脏。只读双标记布尔，这是与旧栈的正面翻转。</p>
 *
 * <h3>块级垂直堆叠</h3>
 * <p>当前最小实现：子节点从上到下依次排列，每个子节点宽度填满父容器可用宽度，
 * y 坐标 = 前面所有兄弟高度之和。节点高度 = 子节点累计高度（容器）或固定行高（文本叶节点）。</p>
 *
 * <h3>文本测量占位</h3>
 * <p>当前使用固定行高 {@value #DEFAULT_LINE_HEIGHT}px，不接真实字体度量。
 * Phase 1 后替换为真实 text measure 服务。</p>
 */
public class SceneLayoutEngine {

    /** 占位：固定行高（像素），Phase 1 后接真实字体度量 */
    static final int DEFAULT_LINE_HEIGHT = 16;

    /** 占位：固定字符宽度（像素），供粗略文本宽度估算 */
    static final int DEFAULT_CHAR_WIDTH = 8;

    // ==================== 测试探针 ====================

    /**
     * 本次 {@link #layout} 调用中的重算次数。
     * 每次 {@code performLayout} 被调用时递增。
     * 仅供测试断言，生产代码不应依赖此字段。
     */
    private int relayoutCount = 0;

    /**
     * 本次 {@link #layout} 调用中被重算的节点集合。
     * 仅供测试断言 I7 跳过行为。
     */
    private final Set<SceneNode> relayoutedNodes = new HashSet<>();

    /**
     * 上一次 layout 调用传入的根约束。
     *
     * <p>用于检测约束变化：约束变化时驱动 root 标脏，保证约束增高/降低
     * 能被布局引擎感知。约束不变时不做任何标脏，保持 I7 双 false 跳过。</p>
     */
    private Constraints lastRootConstraints;

    /**
     * 对以 root 为根的子树执行增量布局。
     *
     * <p>调用前应确保 root 的脏标记正确反映变更（各 SceneNode.setter 已自动维护）。
     * 调用后所有被访问节点的 {@code selfLayoutDirty} 和 {@code descendantLayoutDirty}
     * 均被清除，cachedLayout 更新为最新值。</p>
     *
     * @param root            场景树根节点
     * @param rootConstraints 根节点的布局约束（如屏幕可用宽度）
     */
    public void layout(SceneNode root, Constraints rootConstraints) {
        relayoutCount = 0;
        relayoutedNodes.clear();

        // 约束变化感知：约束变化时只标 root 自己 selfLayoutDirty，
        // 绝不触碰任何后代节点（后代脏标记由各自 setter 自行维护）
        if (!Objects.equals(rootConstraints, lastRootConstraints)) {
            root.markSelfLayout();
        }
        lastRootConstraints = rootConstraints;

        layoutInternal(root, rootConstraints);
    }

    // ==================== 内部递归 ====================

    /**
     * DFS 递归布局，实施双标记判定（I7 灵魂）+ 子节点几何变化上传。
     *
     * <h3>返回值</h3>
     * <p>返回 {@code true} 表示以本节点为根的子树几何发生了变化（本节点或后代
     * 的 LayoutBox 被更新）。父节点收集所有子节点的返回值：若任一子节点返回
     * {@code true}，即使父节点自身 {@code selfLayoutDirty==false}，也需要
     * 走 {@link #performLayout} 重新定位子节点 y 坐标 + 重算自身高度。
     * 这使几何变化沿脏链按需上传（O(脏链深度)），但绝不退化为全量。</p>
     *
     * <p>后序遍历：先递归子节点（确保子节点布局已算好），再按需要重算本节点。</p>
     *
     * <p>跳过条件：双标记 false <b>且 cachedLayout 非空</b>。仅双标记 false
     * 但缓存为空（如首次 layout 从未被标脏的干净叶子），仍需进入流程确保
     * 有 LayoutBox 产出。</p>
     *
     * @param node        当前节点
     * @param constraints 父容器传给当前节点的布局约束
     * @return 本子树几何是否发生了变化
     */
    private boolean layoutInternal(SceneNode node, Constraints constraints) {
        // ==== I7 核心判定：缓存有效 + 双 false → 整棵跳过，几何未变 ====
        if (node.getCachedLayout() != null
                && !node.__isSelfLayoutDirty()
                && !node.__isDescendantLayoutDirty()) {
            // 本节点及整棵后代均干净且缓存有效，直接 return false
            return false;
        }

        // ==== 后序遍历：先递归子节点，收集几何变化信号 ====
        Constraints childConstraints = new Constraints(constraints.getAvailableWidth());
        List<SceneNode> children = node.__getChildren();
        boolean anyChildGeometryChanged = false;
        for (SceneNode child : children) {
            if (layoutInternal(child, childConstraints)) {
                anyChildGeometryChanged = true;
            }
        }

        // ==== 判定是否需要重算本节点 ====
        // 需要重算条件：自身脏 / 无缓存 / 子节点几何变化导致需重新定位
        boolean selfDirty = node.__isSelfLayoutDirty() || node.getCachedLayout() == null;
        boolean needRelayout = selfDirty || anyChildGeometryChanged;

        if (needRelayout) {
            // 仅在"节点自身内容变化"时计入重算统计（I7 语义）
            // 因兄弟几何变化导致的"位置顺移"不算入重算计数
            if (selfDirty) {
                relayoutCount++;
                relayoutedNodes.add(node);
            }
            performLayout(node, constraints);
        }

        // ==== 清除本节点布局脏标记 ====
        // 使用 SceneNode.clearLayoutDirty() 只清 layout 两个标记，
        // 不误清 paint/composite 标记
        node.clearLayoutDirty();
        return needRelayout;
    }

    /**
     * 执行单节点布局计算（块级垂直堆叠）。
     *
     * <p>计算节点自身尺寸（width = 约束可用宽度，height = 子节点累积或固定行高），
     * 并为其子节点设置正确的局部坐标（按垂直堆叠依次排列）。</p>
     *
     * <p>为提高缓存引用稳定性（支持 I7 assertSame 断言），仅在新旧 LayoutBox
     * 值不同时才替换，避免因"位置顺移但值不变"而破坏引用。</p>
     *
     * @param node        要计算布局的节点
     * @param constraints 当前节点的布局约束
     */
    private void performLayout(SceneNode node, Constraints constraints) {
        int x = 0;
        int y = 0;
        int width = constraints.getAvailableWidth();
        int height = computeHeight(node, constraints);

        // 容器节点：为其子节点设置正确的局部坐标
        List<SceneNode> children = node.__getChildren();
        if (!children.isEmpty()) {
            int childY = 0; // 垂直堆叠起始 Y
            for (SceneNode child : children) {
                LayoutBox childBox = (LayoutBox) child.getCachedLayout();
                if (childBox != null) {
                    int childHeight = childBox.getHeight();
                    LayoutBox newBox = new LayoutBox(0, childY, width, childHeight);
                    // 仅在位置或尺寸确实变化时才替换，保持缓存引用稳定
                    if (!newBox.equals(childBox)) {
                        child.setCachedLayout(newBox);
                        // 位置变化 → geometry 级标记，让 paint 遍历感知 offset 需更新
                        child.markGeometryDirty();
                    }
                    childY += childHeight;
                }
            }
        }

        // 存入本节点布局结果（值不变时不替换引用）
        LayoutBox newSelfBox = new LayoutBox(x, y, width, height);
        LayoutBox oldSelfBox = (LayoutBox) node.getCachedLayout();
        if (!newSelfBox.equals(oldSelfBox)) {
            node.setCachedLayout(newSelfBox);
            // 自身位置/尺寸变化 → geometry 级标记
            node.markGeometryDirty();
        }
    }

    /**
     * 计算节点高度。
     *
     * <p>先按 shrink-to-fit 计算内容高度：
     * 容器节点（有子节点）= 子节点 cachedLayout 高度之和；
     * 叶节点 = 文本行数 × {@value #DEFAULT_LINE_HEIGHT}px；
     * 无文本叶节点 = 0。</p>
     *
     * <p>如果节点设置了 {@code fillParentHeight} 且约束有高度约束，
     * 则返回 max(内容高度, 约束高度) 实现"至少填满"语义。</p>
     *
     * @param node        节点
     * @param constraints 当前节点的布局约束
     * @return 节点高度（像素）
     */
    private int computeHeight(SceneNode node, Constraints constraints) {
        // 1. 计算内容高度（shrink-to-fit）
        int contentHeight = computeContentHeight(node);

        // 2. fill 分支：内容高度 vs 约束高度取 max
        if (node.isFillParentHeight() && constraints.hasHeightConstraint()) {
            return Math.max(contentHeight, constraints.getAvailableHeight());
        }
        return contentHeight;
    }

    /**
     * 按 shrink-to-fit 计算节点的内容高度（不考虑 fill）。
     *
     * @param node 节点
     * @return 内容高度（像素）
     */
    private int computeContentHeight(SceneNode node) {
        List<SceneNode> children = node.__getChildren();
        if (!children.isEmpty()) {
            // 容器：高度 = 子节点高度之和
            int total = 0;
            for (SceneNode child : children) {
                LayoutBox childBox = (LayoutBox) child.getCachedLayout();
                if (childBox != null) {
                    total += childBox.getHeight();
                }
            }
            return total;
        }

        // 叶节点：文本行数 × 固定行高；无文本 → 高度为 0
        // preferredHeight 作为显式最小高度，与文本高度取 max
        String text = node.getText();
        int textHeight = 0;
        if (text != null && !text.isEmpty()) {
            int lines = 1;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    lines++;
                }
            }
            textHeight = lines * DEFAULT_LINE_HEIGHT;
        }
        int preferred = node.getPreferredHeight();
        return Math.max(textHeight, preferred);
    }

    // ==================== 测试探针 ====================

    /**
     * 返回最近一次 {@link #layout} 调用中的重算次数。
     * 仅供测试断言 I7 跳过行为。
     *
     * @return 重算次数
     */
    public int __getRelayoutCount() {
        return relayoutCount;
    }

    /**
     * 返回最近一次 {@link #layout} 调用中被重算的节点集合（不可变视图）。
     * 仅供测试断言 I7 跳过行为。
     *
     * @return 被重算的节点集合
     */
    public Set<SceneNode> __getRelayoutedNodes() {
        return Collections.unmodifiableSet(relayoutedNodes);
    }
}
