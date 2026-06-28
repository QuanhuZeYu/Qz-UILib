package club.heiqi.uilib.ui.scene.layout;

import java.util.List;
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
 *       在子节点布局前估算容器/固定兄弟的先验高度，供 COLUMN 唯一 fill 子剩余高度求解。</li>
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
 * 算下传给子的 innerWidth，必须与 FlexLayouter.performLayout 步骤 1
 * （{@code sizing.computeWidth(node, c, true) - padH}）同源，且共用同一 SizingCalculator 实例。</p>
 */
class ConstraintResolver {

    /**
     * 尺寸计算器（阶段 4.1 拆出）：提供 computeWidth（内宽基准权威）+ countLines
     * （priorKnownChildHeight 文本行数统计）+ viewportHeight（耦合不变式锚点）。
     *
     * <p>跨类契约 1：本字段持有的 SizingCalculator 实例必须与 FlexLayouter.performLayout
     * 用的同一实例，确保 computeWidth 的盒宽基准在 buildChildConstraints 与 performLayout
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
     * <p>宽度口径与 {@code performLayout} 步骤 1 的 innerWidth 同源
     * （均基于 {@code computeWidth(node, constraints)} 含 preferredWidth 解析），
     * 保证固定宽容器的「依赖约束宽」子节点不溢出父盒。</p>
     *
     * <p><b>★ 跨类契约 1（内宽基准权威）反向锚定</b>：本方法用
     * {@code sizing.computeWidth(node, constraints, false) - padH} 算下传给子的 innerWidth，
     * 必须与 FlexLayouter.performLayout 步骤 1
     * （{@code sizing.computeWidth(node, constraints, true) - padH}）同源且共用同一
     * SizingCalculator 实例。详见 {@link SizingCalculator#computeWidth(SceneNode, Constraints, boolean)}
     * 的 Javadoc 跨类契约 1。改 computeWidth 优先级链时必须同步检查两处调用。</p>
     *
     * <p>高度下传口径：ROW 容器且本容器高度先验确定时下传交叉轴高；COLUMN 容器默认
     * {@link Constraints#UNCONSTRAINED}，仅在「唯一 fill 子 + 固定兄弟高度均可先验」时
     * 给该 fill 子下传剩余主轴高度。</p>
     *
     * @param node        容器节点
     * @param constraints 本节点收到的布局约束
     * @param child       待下传约束的子节点（可为 null，仅 ROW 分支不依赖它）
     * @return 下传给子节点的约束
     */
    public Constraints buildChildConstraints(SceneNode node, Constraints constraints, SceneNode child) {
        int resolvedWidth = sizing.computeWidth(node, constraints, false);
        int innerWidth = Math.max(0, resolvedWidth - node.getPaddingLeft() - node.getPaddingRight());
        // 高度下传口径：ROW 保持原交叉轴行为；COLUMN 只对唯一 fill 子下传剩余主轴高。
        int childHeight = Constraints.UNCONSTRAINED;
        if (node.getFlexDirection() == FlexDirection.ROW) {
            int priorH = priorKnownInnerHeight(node, constraints);
            if (priorH != Constraints.UNCONSTRAINED) {
                childHeight = priorH;
            }
        } else if (child != null && child == findUniqueColumnFillChild(node)) {
            int remainingHeight = computeRemainingHeightForUniqueColumnFillChild(node, constraints, child);
            if (remainingHeight != Constraints.UNCONSTRAINED) {
                childHeight = remainingHeight;
            }
        }
        return new Constraints(innerWidth, childHeight);
    }

    /**
     * 查找 COLUMN 容器中唯一的 fillParentHeight 子节点。
     *
     * <p>多个 fill 子节点需要 flex-grow/权重分配求解器，本期有意不支持，返回 null 使其全部
     * 回退 shrink-to-fit。只读节点属性，绝不读取任何子 cachedLayout。</p>
     *
     * @param node 容器节点
     * @return 唯一 fill 子节点；不存在或多于一个时返回 null
     */
    private SceneNode findUniqueColumnFillChild(SceneNode node) {
        if (node.getFlexDirection() != FlexDirection.COLUMN) {
            return null;
        }
        SceneNode fillChild = null;
        for (SceneNode child : node.__getChildren()) {
            if (!child.isFillParentHeight()) {
                continue;
            }
            if (fillChild != null) {
                return null;
            }
            fillChild = child;
        }
        return fillChild;
    }

    /**
     * 计算 COLUMN 唯一 fill 子节点应获得的剩余高度。
     *
     * <p>公式：父先验内高 - 固定兄弟先验高之和 - gap*(childCount-1)，结果 clamp 到不小于 0。
     * 任一固定兄弟高度不可先验时返回 {@link Constraints#UNCONSTRAINED}，整体回退 shrink。
     * 本方法严禁读取子 cachedLayout，避免父子布局循环依赖。</p>
     *
     * @param node        COLUMN 容器节点
     * @param constraints 容器收到的约束
     * @param fillChild   唯一 fill 子节点
     * @return 剩余高度，无法可靠计算时为 UNCONSTRAINED
     */
    private int computeRemainingHeightForUniqueColumnFillChild(SceneNode node, Constraints constraints,
                                                               SceneNode fillChild) {
        int innerHeight = priorKnownInnerHeight(node, constraints);
        if (innerHeight == Constraints.UNCONSTRAINED) {
            return Constraints.UNCONSTRAINED;
        }

        List<SceneNode> children = node.__getChildren();
        int fixedHeight = 0;
        for (SceneNode child : children) {
            if (child == fillChild) {
                continue;
            }
            int childHeight = priorKnownChildHeight(child);
            if (childHeight == Constraints.UNCONSTRAINED) {
                return Constraints.UNCONSTRAINED;
            }
            fixedHeight += childHeight;
        }

        int totalGap = children.size() > 1 ? node.getGap() * (children.size() - 1) : 0;
        return Math.max(0, innerHeight - fixedHeight - totalGap);
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
