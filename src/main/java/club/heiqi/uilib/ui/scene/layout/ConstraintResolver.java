package club.heiqi.uilib.ui.scene.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/**
 * 约束解析器 —— scene 布局算法的约束构造下传 + 约束变化感知 + 高度先验计算协作者
 * （阶段 4.2 从 SceneLayoutEngine 拆出）。
 *
 * <h3>职责边界</h3>
 * <ul>
 *   <li>构造下传给子节点的约束（{@link #buildChildConstraints}）：宽度内宽基准来自
 *       {@link SizingCalculator#computeWidth}，高度下传按 ROW/COLUMN 分支处理。</li>
 *   <li>约束变化感知（{@link #childConstraintsWouldChange}）：判断新旧约束是否会改变
 *       下传给子的约束，驱动 I7 干净帧短路判定。</li>
*   <li>高度先验计算（{@link #priorKnownInnerHeight} / {@link #priorKnownChildHeight}）：
*       在子节点布局前估算容器/固定兄弟的先验高度，供 COLUMN grow 子权重分配求解。</li>
 * </ul>
 *
 * <h3>铁律：严禁读子 cachedLayout</h3>
 * <p>本类所有方法只读节点属性 + 约束 + measurer 度量，<b>绝不读取任何子节点的
 * cachedLayout</b>，避免父子布局循环依赖（与原主引擎内联时的铁律逐位等价）。
 * {@link #priorKnownChildHeight} 只读节点属性（含 getPreferredHeight / measurer.lineHeight），
 * {@link #priorKnownInnerHeight} 只读 fill/约束/preferredHeight/padding，均不碰子 cache。</p>
 *
 * <h3>跨类契约（★最高风险，改一处必须同步另一处）</h3>
 * <p>见 {@link SizingCalculator#computeWidth(SceneNode, Constraints, boolean)} 的 Javadoc——
 * computeWidth 返回的 outerWidth 是后续所有"内宽 = outerWidth - padding"计算的权威基准。
 * 本类 {@link #buildChildConstraints} 用 {@code sizing.computeWidth(node, c, false) - padH}
 * 算下传给子的 innerWidth，必须与 FlexLayouter.positionChildren 步骤 1
 * （{@code sizing.computeWidth(node, c, true) - padH}）同源，且共用同一 SizingCalculator 实例。</p>
 */
class ConstraintResolver {

    /**
     * 尺寸计算器（阶段 4.1 拆出）：提供 computeWidth（内宽基准权威）+ countLines
     * （priorKnownChildHeight 文本行数统计）+ viewportHeight（耦合不变式锚点）。
     *
     * <p>跨类契约 1：本字段持有的 SizingCalculator 实例必须与 FlexLayouter.positionChildren
     * 用的同一实例，确保 computeWidth 的盒宽基准在 buildChildConstraints 与 positionChildren
     * 两处一致（见 SizingCalculator.computeWidth Javadoc）。</p>
     */
    private final SizingCalculator sizing;

    /**
     * 文本度量服务：仅 {@link #priorKnownChildHeight} 计算文本叶先验高时需
     * {@code measurer.lineHeight(fontSize)}。与 SizingCalculator 持有的 measurer 同源
     * （主引擎构造时注入同一引用），逐位等价（I7/I8）。
     */
    private final SceneTextMeasurer measurer;

    /**
     * 使用指定尺寸计算器与文本度量服务创建约束解析器。
     *
     * @param sizing   尺寸计算器（非 null，提供 computeWidth / countLines 等纯读函数）
     * @param measurer 文本度量服务（非 null，提供 lineHeight）
     */
    ConstraintResolver(SizingCalculator sizing, SceneTextMeasurer measurer) {
        if (sizing == null) {
            throw new IllegalArgumentException("SizingCalculator 不得为 null");
        }
        if (measurer == null) {
            throw new IllegalArgumentException("SceneTextMeasurer 不得为 null");
        }
        this.sizing = sizing;
        this.measurer = measurer;
    }

    /**
     * 构造下传给指定子节点的约束。
     *
     * <p>宽度下传：innerWidth = {@code computeWidth(node, constraints, false) - padH}，
     * clamp 到不小于 0。</p>
     *
     * <p>宽度口径与 {@code FlexLayouter.positionChildren} 步骤 1 的 innerWidth 同源
     * （均基于 {@code computeWidth(node, constraints)} 含 preferredWidth 解析），
     * 保证固定宽容器的「依赖约束宽」子节点不溢出父盒。</p>
     *
     * <p><b>★ 跨类契约 1（内宽基准权威）反向锚定</b>：本方法用
     * {@code sizing.computeWidth(node, constraints, false) - padH} 算下传给子的 innerWidth，
     * 必须与 FlexLayouter.positionChildren 步骤 1
     * （{@code sizing.computeWidth(node, constraints, true) - padH}）同源且共用同一
     * SizingCalculator 实例。详见 {@link SizingCalculator#computeWidth(SceneNode, Constraints, boolean)}
     * 的 Javadoc 跨类契约 1。改 computeWidth 优先级链时必须同步检查两处调用。</p>
     *
     * <p>高度下传口径：ROW 容器且本容器高度先验确定时下传交叉轴高；COLUMN 容器默认
     * {@link Constraints#UNCONSTRAINED}，在「容器高度可先验 + 固定兄弟高度均可先验」时
     * 按 grow 权重（显式 flexGrow>0 优先，否则 fillParentHeight 视为隐式 1）给各 grow 子
     * 下传按比例分配的剩余主轴高度（余数补末位 grow 子，Qt 语义）。无 grow 子或任一先验
     * 失败时全员回退 shrink-to-fit。</p>
     *
     * @param node        容器节点
     * @param constraints 本节点收到的布局约束
     * @param child       待下传约束的子节点（可为 null，仅 ROW 分支不依赖它）
     * @return 下传给子节点的约束
     */
    public Constraints buildChildConstraints(SceneNode node, Constraints constraints, SceneNode child) {
        int resolvedWidth = sizing.computeWidth(node, constraints, false);
        int innerWidth = Math.max(0, resolvedWidth - node.getPaddingLeft() - node.getPaddingRight());
        // 高度下传口径：ROW 保持原交叉轴行为；COLUMN 按 grow 权重分配剩余主轴高。
        int childHeight = Constraints.UNCONSTRAINED;
        if (node.getFlexDirection() == FlexDirection.ROW) {
            int priorH = priorKnownInnerHeight(node, constraints);
            if (priorH != Constraints.UNCONSTRAINED) {
                childHeight = priorH;
            }
        } else if (child != null) {
            // COLUMN：用 grow 权重分配表取本 child 份额（唯一-fill 是 effectiveGrow=1 的特例）
            Map<SceneNode, Integer> alloc = computeColumnGrowHeights(node, constraints);
            Integer h = alloc.get(child);
            if (h != null) childHeight = h;
        }
        return new Constraints(innerWidth, childHeight);
    }

    /**
     * 计算固定兄弟的先验外高。
     *
     * <p>preferredHeight 最高优先级；文本叶用行数×行高+上下 padding；无文本叶用上下 padding；
     * 容器或其他无法先验的节点返回 {@link Constraints#UNCONSTRAINED}。只读节点属性，严禁读取
     * cachedLayout。</p>
     *
     * @param child 待估算的固定兄弟
     * @return 先验外高，无法确定时为 UNCONSTRAINED
     */
    public int priorKnownChildHeight(SceneNode child) {
        if (child.getPreferredHeight() > 0) {
            return child.getPreferredHeight();
        }
        if (!child.__getChildren().isEmpty()) {
            return Constraints.UNCONSTRAINED;
        }
        int padV = child.getPaddingTop() + child.getPaddingBottom();
        String text = child.getText();
        if (text != null) {
            return sizing.countLines(text) * measurer.lineHeight(child.getFontSize()) + padV;
        }
        return padV;
    }

    /**
     * 先验内容高：仅本容器高度先验确定时返回，否则 {@link Constraints#UNCONSTRAINED}。
     *
     * <p>只读 fill/约束/preferredHeight/padding，绝不调用
     * {@code SizingCalculator.computeContentHeight}、不回看子 cache（防循环依赖）。</p>
     *
     * <p><b>★ 跨类契约 2（viewportHeight 与 priorKnownInnerHeight 耦合不变式）</b>：
     * 本方法 fill 分支（{@code isFillParentHeight && hasHeightConstraint} 返回
     * {@code max(约束高, preferredHeight) - padV}）必须与
     * {@link SizingCalculator#viewportHeight} 的 fill 分支口径一致——两处共享同一
     * "fill 容器高度由约束决定"语义，改一处必须改另一处。详见
     * {@link SizingCalculator#viewportHeight} Javadoc 的耦合不变式锚点。
     * 注意：viewportHeight 的 fill 分支返回裸约束高（视口语义，主动忽略内容撑大），
     * 而本方法返回 {@code max(约束高, preferredHeight) - padV}（容器下传语义，
     * 与 computeHeight 的 fill 分支 {@code max(内容高, 约束高)} 对齐——preferredHeight
     * 作为外尺寸下限参与 max，避免 fill+大 preferredHeight 时子只 fill 到约束高、父底留白）。
     * 两处口径差异是有意设计，改任一分支前必须同步审视另一处。</p>
     *
     * @param node        容器节点
     * @param constraints 本节点收到的布局约束
     * @return 先验内容高（已扣上下 padding），无法先验确定时为 UNCONSTRAINED
     */
    public int priorKnownInnerHeight(SceneNode node, Constraints constraints) {
        int padV = node.getPaddingTop() + node.getPaddingBottom();
        // ★ 耦合不变式：本 fill 分支口径必须与 viewportHeight 的 fill 分支一致——
        //   两处共享同一"fill 容器高度由约束决定"语义。详见 viewportHeight Javadoc。
        if (node.isFillParentHeight() && constraints.hasHeightConstraint()) {
            // 与 computeHeight 口径对齐：fill 自身高取 max(约束高, preferredHeight)，
            // 故下传给子的先验内高也须 max preferredHeight，否则 fill+大 preferredHeight
            // 时子只 fill 到约束高、父底留白。
            int h = Math.max(constraints.getAvailableHeight(), node.getPreferredHeight());
            return Math.max(0, h - padV);
        }
        if (node.getPreferredHeight() > 0) {
            return Math.max(0, node.getPreferredHeight() - padV);
        }
        return Constraints.UNCONSTRAINED;
    }

    /**
     * 计算 COLUMN 容器各 grow 子（flexGrow>0 或隐式 fill）应分得的主轴高度。
     *
     * <p>一次性按权重分配 freeH，余数补给末位 grow 子保证 Σalloc==freeH（Qt 语义）。
     * 无 grow 子 / 高度不可先验 / 固定兄弟不可先验 → 返回空 Map，全员回退 shrink。
     * <b>严禁读子 cachedLayout</b>（只读节点属性 + prior 先验，守现有铁律）。</p>
     *
     * <p>复杂度：O(n) 单容器；childConstraintsWouldChange 逐子调 buildChildConstraints
     * 叠加每子求解使脏判定为 O(n²)。单容器子数通常 &lt; 10，叠加干净帧 Objects.equals
     * 短路（99% 干净帧不跑求解），本期接受，沿用 DECISION-20260626-b4 口径。</p>
     *
     * @param node 容器节点（必须 flexDirection==COLUMN）
     * @param c    容器收到的约束
     * @return child -> 分得高度；空 Map 表示本容器不走 grow 分配
     */
    private Map<SceneNode, Integer> computeColumnGrowHeights(SceneNode node, Constraints c) {
        if (node.getFlexDirection() != FlexDirection.COLUMN) return Collections.emptyMap();

        int innerH = priorKnownInnerHeight(node, c);
        if (innerH == Constraints.UNCONSTRAINED) return Collections.emptyMap();

        List<SceneNode> children = node.__getChildren();
        int fixedH = 0, sumW = 0;
        List<SceneNode> growChildren = null;
        for (SceneNode ch : children) {
            int w = effectiveGrow(ch);
            if (w > 0) {
                sumW += w;
                if (growChildren == null) growChildren = new ArrayList<>();
                growChildren.add(ch);
            } else {
                int h = priorKnownChildHeight(ch);
                if (h == Constraints.UNCONSTRAINED) return Collections.emptyMap();
                fixedH += h;
            }
        }
        if (sumW == 0) return Collections.emptyMap();

        int childCount = children.size();
        int totalGap = childCount > 1 ? node.getGap() * (childCount - 1) : 0;
        int freeH = Math.max(0, innerH - fixedH - totalGap);

        Map<SceneNode, Integer> alloc = new IdentityHashMap<>();
        int distributed = 0;
        for (int i = 0; i < growChildren.size(); i++) {
            SceneNode ch = growChildren.get(i);
            int h;
            if (i == growChildren.size() - 1) {
                h = freeH - distributed;
            } else {
                h = (int) ((long) freeH * effectiveGrow(ch) / sumW);
                distributed += h;
            }
            alloc.put(ch, h);
        }
        return alloc;
    }

    /**
     * 取节点的有效 grow 权重：显式 flexGrow>0 优先；否则 fillParentHeight 视为隐式 1。
     *
     * <p>该映射仅在 COLUMN 主轴求解器内做，不污染 ROW 路径（交叉轴 fill 由 STRETCH 处理）。
     * 这是向后兼容桥：旧「唯一 fill 子」→ effectiveGrow=1、Σw=1、吃满 freeH，与旧路径数学等价。
     * 旧「多 fill 子」→ 各 effectiveGrow=1、按等权分配（新行为，还偏离 2026-06-20 的债）。</p>
     */
    private static int effectiveGrow(SceneNode ch) {
        int g = ch.getFlexGrow();
        if (g > 0) return g;
        return ch.isFillParentHeight() ? 1 : 0;
    }

    /**
     * 约束变化是否会改变下传给子的约束（决定是否值得为后代下沉递归）。
     *
     * <p>约束未变 → false（99% 干净帧短路）；无子 → false；
     * 否则比较新旧两套 childConstraints。</p>
     *
     * @param node 容器节点
     * @param cur  本帧收到的约束
     * @param prev 上一帧约束快照（可能为 null）
     * @return 下传约束是否会变化
     */
    public boolean childConstraintsWouldChange(SceneNode node, Constraints cur, Constraints prev) {
        if (Objects.equals(cur, prev)) return false;
        if (node.__getChildren().isEmpty()) return false;
        for (SceneNode child : node.__getChildren()) {
            Constraints newCC = buildChildConstraints(node, cur, child);
            Constraints oldCC = (prev == null) ? null : buildChildConstraints(node, prev, child);
            if (!Objects.equals(newCC, oldCC)) {
                return true;
            }
        }
        return false;
    }
}
