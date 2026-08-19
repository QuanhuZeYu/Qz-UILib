package club.heiqi.uilib.ui.scene.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
     * 布局诊断日志（与 FlexLayoutHelper 的 {@code QzUiLib/FlexLayout} 同范式，
     * 用 {@code QzUiLib/Layout} 通道）。仅用于早退 WARN，提示调用方修复高度先验缺失。
     */
    private static final Logger LOG = LogManager.getLogger("QzUiLib/Layout");

    /**
     * 已警告"容器自身高度无法先验"的节点集合（per-node 去重，避免每帧重复打 WARN）。
     *
     * <p>用 {@link WeakHashMap} 键集，节点被 GC 回收后条目自动清除，避免引擎长生命周期
     * 下持有强引用导致内存泄漏。ConstraintResolver 为 per-engine 实例（见
     * {@link SceneLayoutEngine} 构造器），引擎通常 per-screen，但保守起见仍用弱引用。</p>
     *
     * <p><b>并发维护</b>：布局引擎当前单线程；若未来重启子树并行 layout，
     * WeakHashMap 非线程安全，需换 {@code ConcurrentHashMap.newKeySet()} 或加锁。</p>
     */
    private final java.util.Set<SceneNode> warnedContainerHeight =
            Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * 已警告"固定兄弟高度无法先验"的节点集合（per-node 去重）。
     *
     * <p>键为容器节点（非固定兄弟），因为同一容器每帧都会撞同一类早退。
     * 用 {@link WeakHashMap} 键集避免内存泄漏，与 {@link #warnedContainerHeight} 同理。</p>
     *
     * <p><b>并发维护</b>：布局引擎当前单线程；若未来重启子树并行 layout，
     * WeakHashMap 非线程安全，需换 {@code ConcurrentHashMap.newKeySet()} 或加锁。</p>
     */
    private final java.util.Set<SceneNode> warnedSiblingHeight =
            Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * 已警告"容器自身宽度无法先验"的节点集合（per-node 去重，ROW 主轴 grow 分配用）。
     *
     * <p>与 {@link #warnedContainerHeight} 对称，键为容器节点。用 {@link WeakHashMap} 键集
     * 避免内存泄漏。</p>
     */
    private final java.util.Set<SceneNode> warnedContainerWidth =
            Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * 已警告"固定兄弟宽度无法先验"的节点集合（per-node 去重，ROW 主轴 grow 分配用）。
     *
     * <p>与 {@link #warnedSiblingHeight} 对称，键为容器节点。</p>
     */
    private final java.util.Set<SceneNode> warnedSiblingWidth =
            Collections.newSetFromMap(new WeakHashMap<>());

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
     * <p>宽度下传口径：ROW 容器在「父级下传确定宽约束 + 固定兄弟宽度均可先验」时
     * 按 grow 权重（显式 flexGrow>0）给各 grow 子下传按比例分配的剩余主轴宽度
     * （余数补末位 grow 子，Qt 语义，与 COLUMN 主轴高度分配对称）。无 grow 子或任一先验
     * 失败时回退现状（统一下传 innerWidth - 子 marginH）。COLUMN 容器宽=父内宽 - 子 marginH
     * （交叉轴宽，扣子 marginH 占用）。</p>
     *
     * @param node        容器节点
     * @param constraints 本节点收到的布局约束
     * @param child       待下传约束的子节点（可为 null，仅 ROW 分支不依赖它）
     * @return 下传给子节点的约束
     */
    public Constraints buildChildConstraints(SceneNode node, Constraints constraints, SceneNode child) {
        int resolvedWidth = sizing.computeWidth(node, constraints, false);
        int innerWidth = Math.max(0, resolvedWidth - node.getPaddingLeft() - node.getPaddingRight());
        // 高度下传口径：ROW 保持原交叉轴行为（扣子 marginV）；COLUMN 按 grow 权重分配剩余主轴高。
        int childHeight = Constraints.UNCONSTRAINED;
        // 宽度下传口径：ROW 按 grow 权重分配剩余主轴宽；COLUMN 保持原交叉轴行为（扣子 marginH）。
        int childWidth;
        if (node.isScrollableX()) {
            // ★ scrollableX 视口：子内容宽不受视口内宽 clamp（横向滚动地基——内容自由溢出，
            //   由 paint CLIP 裁剪 + scrollOffsetX 平移显示；与 scrollable 的高度解耦对称）。
            childWidth = Constraints.UNCONSTRAINED;
            // 高度口径仍走正常 ROW/COLUMN 逻辑（scrollableX 只解耦宽度）
            if (node.getFlexDirection() == FlexDirection.ROW) {
                int priorH = priorKnownInnerHeight(node, constraints);
                if (priorH != Constraints.UNCONSTRAINED) {
                    childHeight = child != null
                            ? Math.max(0, priorH - child.marginV())
                            : priorH;
                }
            } else if (child != null) {
                Map<SceneNode, Integer> alloc = computeColumnGrowHeights(node, constraints);
                Integer h = alloc.get(child);
                if (h != null) childHeight = h;
            }
        } else if (node.getFlexDirection() == FlexDirection.ROW) {
            int priorH = priorKnownInnerHeight(node, constraints);
            if (priorH != Constraints.UNCONSTRAINED) {
                // ROW cross=高：子内容高 = 父内高 - 子 marginV（marginV 占用 cross 轴）
                // null 守卫：child 可为 null（见 Javadoc），防御性编程避免 NPE
                childHeight = child != null
                        ? Math.max(0, priorH - child.marginV())
                        : priorH;
            }
            // ROW main=宽：用 grow 权重分配表取本 child 份额
            // alloc.get(child) 是子自身宽（不含 margin），直接下传
            Map<SceneNode, Integer> alloc = computeRowGrowWidths(node, constraints);
            Integer w = child != null ? alloc.get(child) : null;
            if (w != null) {
                childWidth = w;
            } else {
                // 分配表空（无 grow 子 / 宽度不可先验 / 固定兄弟不可先验）→ 回退现状
                childWidth = child != null ? Math.max(0, innerWidth - child.marginH()) : innerWidth;
            }
        } else if (child != null) {
            // COLUMN：用 grow 权重分配表取本 child 份额（唯一-fill 是 effectiveGrow=1 的特例）
            // alloc.get(child) 是子自身高（不含 margin），直接下传
            Map<SceneNode, Integer> alloc = computeColumnGrowHeights(node, constraints);
            Integer h = alloc.get(child);
            if (h != null) childHeight = h;
            // COLUMN cross=宽：子内容宽 = 父内宽 - 子 marginH（marginH 占用 cross 轴宽）
            childWidth = Math.max(0, innerWidth - child.marginH());
        } else {
            // COLUMN 且 child == null（防御）：宽=父内宽，高=UNCONSTRAINED
            childWidth = innerWidth;
        }
        return new Constraints(childWidth, childHeight);
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
            // preferredHeight 是外尺寸下限，maxHeight 不会压低它（矛盾时下限优先），不 clamp。
            return child.getPreferredHeight();
        }
        if (!child.__getChildren().isEmpty()) {
            // 容器先验高不可知，maxHeight 在 computeHeight 出口 clamp，不在先验阶段处理。
            return Constraints.UNCONSTRAINED;
        }
        int padV = child.getPaddingTop() + child.getPaddingBottom();
        String text = child.getText();
        if (text != null) {
            // wrap 感知：maxTextWidth>0 时拆行后逐行行高求和（与 SizingCalculator 同口径）
            int natural = sizing.leafTextHeight(child) + padV;
            return clampToMax(child, natural);
        }
        return clampToMax(child, padV);
    }

    /**
     * 计算固定兄弟的先验外宽（对称于 {@link #priorKnownChildHeight}）。
     *
     * <p>preferredWidth 最高优先级；文本叶用 measurer 测量文本最大行宽 + 左右 padding；
     * 无文本叶用左右 padding；容器或其他无法先验的节点返回 {@link Constraints#UNCONSTRAINED}。
     * 只读节点属性 + measurer 度量，严禁读取 cachedLayout（守 I7）。</p>
     *
     * <p><b>文本宽度估算说明</b>：与高度分支用 {@code countLines * lineHeight} 估算行高对称，
     * 此处用 {@code measurer.measureWidth} 测量文本各行的最大 UI 像素宽。measurer 已具备
     * measureWidth 能力（见 {@link SceneTextMeasurer#measureWidth}），故文本叶分支可精确估算，
     * 不退化为 padH。这与 SizingCalculator.computeWidth 文本叶 shrink-to-fit 分支同源
     * （均调 measurer.measureWidth），口径一致。</p>
     *
     * @param child 待估算的固定兄弟
     * @return 先验外宽，无法确定时为 UNCONSTRAINED
     */
    public int priorKnownChildWidth(SceneNode child) {
        if (child.getPreferredWidth() > 0) {
            // preferredWidth 是外尺寸下限，maxWidth 不会压低它（矛盾时下限优先），不 clamp。
            return child.getPreferredWidth();
        }
        if (!child.__getChildren().isEmpty()) {
            // 容器先验宽不可知（SHRINK 容器需读子 cache，违反先验铁律），maxWidth 在 computeWidth 出口 clamp。
            return Constraints.UNCONSTRAINED;
        }
        int padH = child.getPaddingLeft() + child.getPaddingRight();
        String text = child.getText();
        if (text != null) {
            // 文本叶：测量各行最大宽 + padH；wrap 节点内容宽即 maxTextWidth。空文本视作 0 宽。
            int wrapWidth = child.getMaxTextWidth();
            int textW = text.isEmpty() ? 0
                    : (wrapWidth > 0 ? wrapWidth : measureMaxLineWidth(text, child.getFontSize()));
            int natural = textW + padH;
            return clampToMaxWidth(child, natural);
        }
        return clampToMaxWidth(child, padH);
    }

    /**
     * 测量多行文本中各行的最大 UI 像素宽度（对称于 SizingCalculator.measureMaxLineWidth）。
     *
     * <p>本类 priorKnownChildWidth 文本叶分支需测量文本宽估算先验外宽。SizingCalculator
     * 的 measureMaxLineWidth 为 private，无法直接复用，故在此对称实现一份。两处实现
     * 必须保持逐位等价（均调 measurer.measureWidth，按 \n 切行取 max）。</p>
     *
     * @param text       文本内容
     * @param fontSizePx 字号（UI 像素）
     * @return 各行测量宽的最大值
     */
    private int measureMaxLineWidth(String text, int fontSizePx) {
        int max = 0;
        int start = 0;
        int len = text.length();
        for (int i = 0; i <= len; i++) {
            if (i == len || text.charAt(i) == '\n') {
                String line = text.substring(start, i);
                int w = measurer.measureWidth(line, fontSizePx);
                if (w > max) {
                    max = w;
                }
                start = i + 1;
            }
        }
        return max;
    }

    /**
     * 自然宽 clamp 到 maxWidth（0 = 无上界），对称于 {@link #clampToMax}。
     *
     * @param node    节点
     * @param natural 自然宽（外尺寸，含 padding）
     * @return clamp 后的宽度
     */
    private static int clampToMaxWidth(SceneNode node, int natural) {
        int max = node.getMaxWidth();
        return max > 0 ? Math.min(natural, max) : natural;
    }

    /**
     * 自然高 clamp 到 maxHeight（0 = 无上界）。
     *
     * <p>仅用于 {@link #priorKnownChildHeight} 自然高分支（文本叶 / 无文本叶）。
     * preferredHeight 分支不 clamp（下限优先），容器分支不 clamp（先验不可知，
     * 由 computeHeight 出口 clamp）。</p>
     *
     * @param node    节点
     * @param natural 自然高（外尺寸，含 padding）
     * @return clamp 后的高度
     */
    private static int clampToMax(SceneNode node, int natural) {
        int max = node.getMaxHeight();
        return max > 0 ? Math.min(natural, max) : natural;
    }

    /**
     * 先验内容高：仅本容器高度先验确定时返回，否则 {@link Constraints#UNCONSTRAINED}。
     *
     * <p>只读 fill/约束/preferredHeight/padding，绝不调用
     * {@code SizingCalculator.computeContentHeight}、不回看子 cache（防循环依赖）。</p>
     *
     * <p><b>★ 跨类契约 2（viewportHeight 与 priorKnownInnerHeight 耦合不变式）</b>：
     * 本方法 fill/grow/percent 分支（{@code (isFillParentHeight || flexGrow>0 || percentHeight>0)
     * && !isScrollable && hasHeightConstraint} 返回
     * {@code max(约束高, preferredHeight) - padV}）必须与
     * {@link SizingCalculator#viewportHeight} 的 fill 分支口径一致——两处共享同一
     * "约束驱动高度的容器（fill/grow/percent，排除 scrollable）高度由约束决定"语义，
     * 改一处必须改另一处。详见
     * {@link SizingCalculator#viewportHeight} Javadoc 的耦合不变式锚点。
     * 注意：viewportHeight 的 fill 分支返回裸约束高（视口语义，主动忽略内容撑大），
     * 而本方法返回 {@code max(约束高, preferredHeight) - padV}（容器下传语义，
     * 与 computeHeight 的 fill 分支 {@code max(内容高, 约束高)} 对齐——preferredHeight
     * 作为外尺寸下限参与 max，避免 fill+大 preferredHeight 时子只 fill 到约束高、父底留白）。
     * 两处口径差异是有意设计，改任一分支前必须同步审视另一处。</p>
     *
     * <p><b>★ 三处同步关系（改本方法闸门时必须同步检查）</b>：
     * <ul>
     *   <li>{@link SizingCalculator#computeHeight} :266 —— fill/grow/percent 三合流口径
     *       （CSS §9.8 definite 语义：父分配 tight 高 → 子高度 definite），
     *       本方法闸门的容器集合必须与 `SizingCalculator#computeHeight` 一致（均排除 scrollable，
     *       computeHeight 靠前置 :254 scrollable 收口排除，本方法靠显式 {@code !isScrollable()} 排除）。</li>
     *   <li>{@link SizingCalculator#isHeightConsumingConstraint} :368 ——
     *       fill/grow/percent/scrollable 四合流口径，判定"约束是否消费高"，
     *       与本方法闸门共享同一"约束驱动高度"语义边界。</li>
     *   <li>{@link SizingCalculator#viewportHeight} :334 —— scrollable 专用视口分支，
     *       通过本方法 {@code !isScrollable()} 排除保持口径不重叠（跨类契约 2）。</li>
     * </ul>
     * 改 {@code priorKnownInnerHeight} 闸门时必须同步检查 {@code computeHeight}
     * 和 {@code isHeightConsumingConstraint} 的口径一致性。</p>
     *
     * @param node        容器节点
     * @param constraints 本节点收到的布局约束
     * @return 先验内容高（已扣上下 padding），无法先验确定时为 UNCONSTRAINED
     */
    public int priorKnownInnerHeight(SceneNode node, Constraints constraints) {
        int padV = node.getPaddingTop() + node.getPaddingBottom();
        // ★ 耦合不变式：本 fill/grow/percent 分支口径必须与 viewportHeight 的 fill 分支一致——
        //   两处共享同一"约束驱动高度的容器高度由约束决定"语义。详见 viewportHeight Javadoc。
        // ★ grow/percent 子收到父分配的 tight 高约束后，其内高同 fill 一样由约束确定，
        //   应作先验内高下传（嵌套 grow 子容器场景：grow 子的 grow 子才能拿到确定高约束）。
        // ★ 对齐 `SizingCalculator#computeHeight` 的 fill/grow/percent 三合流口径
        //   （CSS §9.8 definite 语义：父分配 tight 高 → 子高度 definite）。
        // ★ scrollable 排除：viewport 语义主动忽略内容撑大，内高不作子先验，
        //   与 `SizingCalculator#viewportHeight` 的 scrollable 专用分支对称（跨类契约 2）。
        // ★ 守 I7：纯读静态元数据 + 入参，不回看子 cache。
        if ((node.isFillParentHeight() || node.getFlexGrow() > 0 || node.getPercentHeight() > 0)
                && !node.isScrollable()
                && constraints.hasHeightConstraint()) {
            // 与 computeHeight 口径对齐：fill/grow/percent 自身高取 max(约束高, preferredHeight)，
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
     * <h3>percent 子作固定子（四期）</h3>
     * <p>percentHeight&gt;0 且 effectiveGrow==0（grow 优先，互斥）的子节点作固定子：
     * 高 = {@code innerH * pct / 100}（父先验内高已确定），占用 fixedH，不参与 grow 分配，
     * 也不参与 freeze do-while（已在扫描时作固定子）。percent 子进 percentAlloc map，
     * 最后合并进 alloc，与 grow 子统一下传路径（buildChildConstraints 取 alloc.get(child)）。</p>
     * <p><b>maxHeight clamp</b>：pctH 算出后 clamp 到 maxHeight（&gt;0 时），与 computeHeight
     * 出口 clampHeight 一致，避免 fixedH 偏大导致 grow 兄弟留白。</p>
     * <p><b>内容撑大 / preferredHeight 下界</b>：percent 子的 fixedH 贡献取
     * {@code max(pctH, priorKnownChildHeight(ch))}（priorH != UNCONSTRAINED 时）。
     * priorH 对叶返回文本高/padV、对有 preferredHeight 返回 preferredHeight、对容器返回
     * UNCONSTRAINED（容器子用 pctH，不取 max——容器内容撑大无法先验，属已知边界）。
     * percentAlloc 下传 effectiveFixedH，子 computeHeight fill 分支 max(content, effectiveFixedH)
     * = effectiveFixedH（content 已含在 priorH 里），口径一致。</p>
     * <p>父高不可先验（innerH == UNCONSTRAINED）时整个方法早退返回空 Map，
     * percent 子走 fallback shrink（priorKnownChildHeight 自然高），percent 失效。</p>
     *
     * <h3>freeze do-while 撞顶/撞底重分配（min/max 对称，Qt qGeomCalc 语义）</h3>
     * <p>分配前先按当前 remainingFree/remainingW 试算各 active grow 子的 tentative 高：</p>
     * <ul>
     *   <li>tentative &gt; maxHeight（&gt;0）→ 撞顶，冻结到 maxHeight，释放空间回流未冻结子；</li>
     *   <li>tentative &lt; preferredHeight（&gt;0，作下界）→ 撞底，冻结到 preferredHeight，占用空间；</li>
     *   <li>否则保持 active，进入下一轮按新 remainingFree/remainingW 重算。</li>
     * </ul>
     * <p>每轮至少冻结 1 个，最多 n 轮，三重退出条件（无新冻结 / 无 active / remainingW==0）
     * 保证数学收敛。未冻结 active 子最终按比例分配，余数补末位（沿用现有 Qt 语义）。
     * 不变量：正常约束下 Σfrozen + Σactive分配 == freeH；过约束（多个子撞下界且
     * ΣpreferredHeight &gt; freeH）时退化为 Σfrozen ≥ freeH（下限优先溢出父盒，
     * CSS min-height 语义）——此时 remainingFree 被钳到 0 后仍继续冻结撞底子到
     * preferredHeight，导致 Σfrozen &gt; freeH，行为本身合理（下限优先，溢出父盒）。</p>
     *
     * <p>复杂度：O(n) 单容器；childConstraintsWouldChange 逐子调 buildChildConstraints
     * 叠加每子求解使脏判定为 O(n²)。单容器子数通常 &lt; 10，叠加干净帧 Objects.equals
     * 短路（99% 干净帧不跑求解），本期接受，沿用接受口径（旧决策已删除）。</p>
     *
     * @param node 容器节点（必须 flexDirection==COLUMN）
     * @param c    容器收到的约束
     * @return child -> 分得高度；空 Map 表示本容器不走 grow 分配
     */
    private Map<SceneNode, Integer> computeColumnGrowHeights(SceneNode node, Constraints c) {
        if (node.getFlexDirection() != FlexDirection.COLUMN) return Collections.emptyMap();

        // 预扫 children：是否存在 grow 意图（任一 effectiveGrow(ch) > 0）。
        // 仅当有 grow 子时早退才值得打 WARN（无 grow 子时早退是正常 shrink 路径，非异常）。
        List<SceneNode> children = node.__getChildren();
        boolean hasGrowIntent = false;
        for (SceneNode ch : children) {
            if (effectiveGrow(ch) > 0) {
                hasGrowIntent = true;
                break;
            }
        }

        int innerH = priorKnownInnerHeight(node, c);
        if (innerH == Constraints.UNCONSTRAINED) {
            if (hasGrowIntent) {
                warnContainerHeightUnconstrained(node, children.size());
            }
            return Collections.emptyMap();
        }

        int fixedH = 0, sumW = 0;
        int growMarginTotal = 0;  // grow 子的 marginV 累计（grow 子 margin 也占用主轴）
        List<SceneNode> growChildren = null;
        // percent 子作固定子，高 = innerH * pct / 100；进 percentAlloc 统一下传
        Map<SceneNode, Integer> percentAlloc = new IdentityHashMap<>();
        for (SceneNode ch : children) {
            int w = effectiveGrow(ch);
            if (w > 0) {
                // grow 子（grow 优先，忽略 percent）
                sumW += w;
                growMarginTotal += ch.marginV();
                if (growChildren == null) growChildren = new ArrayList<>();
                growChildren.add(ch);
            } else if (ch.getPercentHeight() > 0) {
                // percent 子作固定子：高 = innerH * pct / 100（父高已先验，innerH != UNCONSTRAINED）
                // 用 long 中间量防 innerH * pct 溢出
                int pctH = (int) ((long) innerH * ch.getPercentHeight() / 100);
                // ★ reviewer 问题 1：maxHeight clamp
                // percent 子实际高 = computeHeight 出口 clampHeight(pctH) = maxHeight，
                // fixedH 贡献也必须用 clamp 后的值，否则 fixedH 偏大 → freeH 偏小 → grow 兄弟留白。
                int maxH = ch.getMaxHeight();
                if (maxH > 0 && pctH > maxH) pctH = maxH;
                // ★ reviewer 问题 2：内容撑大 / preferredHeight 下界保护
                // percent 子内容高（文本叶文本高 / preferredHeight）> pctH 时，
                // computeHeight fill 分支取 max(contentHeight, pctH) = contentHeight，
                // fixedH 贡献须用 max(pctH, priorKnownChildHeight)，否则 fixedH 偏小 →
                // freeH 偏大 → grow 兄弟溢出。
                // priorKnownChildHeight 对容器返回 UNCONSTRAINED（不取 max，用 pctH），
                // 对叶返回文本高/padV，对有 preferredHeight 返回 preferredHeight。
                int priorH = priorKnownChildHeight(ch);
                int effectiveFixedH = (priorH != Constraints.UNCONSTRAINED)
                        ? Math.max(pctH, priorH)
                        : pctH;
                fixedH += effectiveFixedH + ch.marginV();
                // 进 percentAlloc 统一下传（高 = effectiveFixedH），最后合并进 alloc
                // 下传 tight 高 = effectiveFixedH，子 computeHeight fill 分支 max(content, effectiveFixedH)
                // = effectiveFixedH（content 已含在 priorKnownChildHeight 里），口径一致
                percentAlloc.put(ch, effectiveFixedH);
            } else {
                // 固定子（priorKnownChildHeight）
                int h = priorKnownChildHeight(ch);
                if (h == Constraints.UNCONSTRAINED) {
                    if (hasGrowIntent) {
                        warnSiblingHeightUnconstrained(node, ch, children.size());
                    }
                    return Collections.emptyMap();
                }
                // 固定子占用含 marginV：主轴占位 = 先验高 + marginV
                fixedH += h + ch.marginV();
            }
        }
        if (sumW == 0) {
            // 无 grow 子：percent 子仍需下传其固定高（alloc 合并 percentAlloc）
            // 但若仅 percent 子无 grow 子，buildChildConstraints 取 alloc.get(child) 仍能拿到 pctH
            Map<SceneNode, Integer> alloc = new IdentityHashMap<>();
            alloc.putAll(percentAlloc);
            return alloc;
        }

        int childCount = children.size();
        int totalGap = childCount > 1 ? node.getGap() * (childCount - 1) : 0;
        // freeH 扣减：固定子含 margin + grow 子 margin + gap 全部扣减
        // grow 子分配的是 freeH（子自身高，不含 margin），freeze do-while 仍基于 freeH
        int freeH = Math.max(0, innerH - fixedH - growMarginTotal - totalGap);

        // freeze 主循环（上界+下界对称，Qt qGeomCalc 语义，守 I7 数值求解器边界）
        // 撞 maxHeight 上界：冻结到 maxHeight，释放空间回流未冻结子
        // 撞 preferredHeight 下界：冻结到 preferredHeight，占用空间
        // 全程只读 effectiveGrow/maxHeight/preferredHeight，不读子 cachedLayout（守 I7）
        Map<SceneNode, Integer> frozen = new IdentityHashMap<>();
        long remainingFree = freeH;
        long remainingW = sumW;
        List<SceneNode> active = new ArrayList<>(growChildren);
        List<SceneNode> newlyFrozen = new ArrayList<>();
        do {
            newlyFrozen.clear();
            for (SceneNode ch : active) {
                long tentative = remainingFree * effectiveGrow(ch) / remainingW;
                int maxH = ch.getMaxHeight();        // 0 = 无上界
                int minH = ch.getPreferredHeight();  // preferredHeight 作下界，0 = 无下界
                if (maxH > 0 && tentative > maxH) {
                    frozen.put(ch, maxH);
                    newlyFrozen.add(ch);
                } else if (minH > 0 && tentative < minH) {
                    frozen.put(ch, minH);
                    newlyFrozen.add(ch);
                }
            }
            for (SceneNode ch : newlyFrozen) {
                remainingFree -= frozen.get(ch);
                remainingW -= effectiveGrow(ch);
                active.remove(ch);
            }
            if (remainingFree < 0) remainingFree = 0;
        } while (!newlyFrozen.isEmpty() && !active.isEmpty() && remainingW > 0);

        // 未冻结 active 子最终比例分配（余数补末位，沿用现有 Qt 语义）
        // 末位在 active 子集上重新确定，保证 Σactive分配 == remainingFree
        Map<SceneNode, Integer> alloc = new IdentityHashMap<>();
        long distributed = 0;
        for (int i = 0; i < active.size(); i++) {
            SceneNode ch = active.get(i);
            int h;
            if (i == active.size() - 1) {
                h = (int) (remainingFree - distributed);
            } else {
                h = (int) (remainingFree * effectiveGrow(ch) / remainingW);
                distributed += h;
            }
            alloc.put(ch, h);
        }
        alloc.putAll(frozen);
        // percent 子作固定子，下传高 = pctH（与 grow 子统一下传路径）
        alloc.putAll(percentAlloc);
        return alloc;
    }

    /**
     * 计算 ROW 容器各 grow 子（flexGrow>0 或隐式 fillParentWidth）应分得的主轴宽度。
     *
     * <p>对称于 {@link #computeColumnGrowHeights}：COLUMN 主轴是高度 → ROW 主轴是宽度。
     * 一次性按权重分配 freeW，余数补给末位 grow 子保证 Σalloc==freeW（Qt 语义）。
     * 无 grow 子 / 宽度不可先验 / 固定兄弟不可先验 → 返回空 Map，全员回退 shrink。
     * <b>严禁读子 cachedLayout</b>（只读节点属性 + prior 先验，守现有铁律）。</p>
     *
     * <h3>grow 权重映射（ROW 主轴）</h3>
     * <p>ROW 主轴是宽度，grow 意图来自 flexGrow>0 或 fillParentWidth 隐式桥。
     * {@link #effectiveGrowRow} 取 flexGrow>0 优先，否则 fillParentWidth=true 视为隐式
     * 权重 1（与 COLUMN 的 fillParentHeight 隐式 grow=1 桥对称）。多 fill 子按等权分配，
     * 余数补末位 grow 子，Qt 语义。两轴对称，根因是 SceneNode 已提供 fillParentWidth /
     * fillParentHeight 双字段。</p>
     *
     * <h3>percent 子作固定子</h3>
     * <p>percentWidth>0 且 flexGrow==0 的子节点作固定子：宽 = innerW * pct / 100
     * （父先验内宽已确定），占用 fixedW，不参与 grow 分配。percent 子进 percentAlloc map，
     * 最后合并进 alloc，与 grow 子统一下传路径。</p>
     *
     * <h3>freeze do-while 撞顶/撞底重分配</h3>
     * <p>与 COLUMN 版本对称：tentative > maxWidth → 撞顶冻结到 maxWidth；
     * tentative < preferredWidth → 撞底冻结到 preferredWidth。三重退出条件保证收敛。</p>
     *
     * @param node 容器节点（必须 flexDirection==ROW）
     * @param c    容器收到的约束
     * @return child -> 分得宽度；空 Map 表示本容器不走 grow 分配
     */
    private Map<SceneNode, Integer> computeRowGrowWidths(SceneNode node, Constraints c) {
        if (node.getFlexDirection() != FlexDirection.ROW) return Collections.emptyMap();

        // 预扫 children：是否存在 grow 意图（任一 effectiveGrowRow(ch) > 0）。
        List<SceneNode> children = node.__getChildren();
        boolean hasGrowIntent = false;
        for (SceneNode ch : children) {
            if (effectiveGrowRow(ch) > 0) {
                hasGrowIntent = true;
                break;
            }
        }

        // ROW 主轴内宽：computeWidth 已含 preferredWidth/percentWidth 解析，是权威基准
        // （与 buildChildConstraints 的 innerWidth 同源，跨类契约 1）。
        int resolvedWidth = sizing.computeWidth(node, c, false);
        int innerW = Math.max(0, resolvedWidth - node.getPaddingLeft() - node.getPaddingRight());
        // ROW 主轴 grow 需要确定的主轴宽基准。computeWidth 对无 preferredWidth 的容器返回
        // 约束宽（availableWidth）；若 availableWidth == UNCONSTRAINED（父未下传宽约束），
        // innerW 会被 Math.max(0, UNCONSTRAINED - padH) 钳成 0，grow 分配无意义 → 早退。
        // 用 c.getAvailableWidth() 判定更精确：父未下传宽约束时直接早退。
        if (c.getAvailableWidth() == Constraints.UNCONSTRAINED) {
            if (hasGrowIntent) {
                warnContainerWidthUnconstrained(node, children.size());
            }
            return Collections.emptyMap();
        }

        int fixedW = 0, sumW = 0;
        int growMarginTotal = 0;  // grow 子的 marginH 累计（grow 子 margin 也占用主轴）
        List<SceneNode> growChildren = null;
        // percent 子作固定子，宽 = innerW * pct / 100；进 percentAlloc 统一下传
        Map<SceneNode, Integer> percentAlloc = new IdentityHashMap<>();
        for (SceneNode ch : children) {
            int w = effectiveGrowRow(ch);
            if (w > 0) {
                // grow 子（grow 优先，忽略 percent）
                sumW += w;
                growMarginTotal += ch.marginH();
                if (growChildren == null) growChildren = new ArrayList<>();
                growChildren.add(ch);
            } else if (ch.getPercentWidth() > 0) {
                // percent 子作固定子：宽 = innerW * pct / 100（父宽已先验）
                int pctW = (int) ((long) innerW * ch.getPercentWidth() / 100);
                // maxWidth clamp（与 COLUMN percent 子 maxHeight clamp 对称）
                int maxW = ch.getMaxWidth();
                if (maxW > 0 && pctW > maxW) pctW = maxW;
                // 内容撑大 / preferredWidth 下界保护（与 COLUMN 对称）
                int priorW = priorKnownChildWidth(ch);
                int effectiveFixedW = (priorW != Constraints.UNCONSTRAINED)
                        ? Math.max(pctW, priorW)
                        : pctW;
                fixedW += effectiveFixedW + ch.marginH();
                // 下传父内宽 innerW（非预算宽 effectiveFixedW），交由 computeWidth 的 percentWidth
                // 分支乘一次百分比得正确值。若下传 effectiveFixedW 会被 computeWidth 二次应用百分比
                // （effectiveFixedW * pct / 100），与 COLUMN percentHeight 的 fill 语义不对称。
                // fixedW 仍按 effectiveFixedW 扣减，保证 grow 兄弟的 freeW 正确。
                percentAlloc.put(ch, innerW);
            } else {
                // 固定子（priorKnownChildWidth）
                int w2 = priorKnownChildWidth(ch);
                if (w2 == Constraints.UNCONSTRAINED) {
                    if (hasGrowIntent) {
                        warnSiblingWidthUnconstrained(node, ch, children.size());
                    }
                    return Collections.emptyMap();
                }
                // 固定子占用含 marginH：主轴占位 = 先验宽 + marginH
                fixedW += w2 + ch.marginH();
            }
        }
        if (sumW == 0) {
            // 无 grow 子：percent 子仍需下传其固定宽
            Map<SceneNode, Integer> alloc = new IdentityHashMap<>();
            alloc.putAll(percentAlloc);
            return alloc;
        }

        int childCount = children.size();
        int totalGap = childCount > 1 ? node.getGap() * (childCount - 1) : 0;
        // freeW 扣减：固定子含 margin + grow 子 margin + gap 全部扣减
        int freeW = Math.max(0, innerW - fixedW - growMarginTotal - totalGap);

        // freeze 主循环（与 COLUMN 版本对称，守 I7 数值求解器边界）
        Map<SceneNode, Integer> frozen = new IdentityHashMap<>();
        long remainingFree = freeW;
        long remainingW = sumW;
        List<SceneNode> active = new ArrayList<>(growChildren);
        List<SceneNode> newlyFrozen = new ArrayList<>();
        do {
            newlyFrozen.clear();
            for (SceneNode ch : active) {
                long tentative = remainingFree * effectiveGrowRow(ch) / remainingW;
                int maxW = ch.getMaxWidth();        // 0 = 无上界
                int minW = ch.getPreferredWidth();  // preferredWidth 作下界，0 = 无下界
                if (maxW > 0 && tentative > maxW) {
                    frozen.put(ch, maxW);
                    newlyFrozen.add(ch);
                } else if (minW > 0 && tentative < minW) {
                    frozen.put(ch, minW);
                    newlyFrozen.add(ch);
                }
            }
            for (SceneNode ch : newlyFrozen) {
                remainingFree -= frozen.get(ch);
                remainingW -= effectiveGrowRow(ch);
                active.remove(ch);
            }
            if (remainingFree < 0) remainingFree = 0;
        } while (!newlyFrozen.isEmpty() && !active.isEmpty() && remainingW > 0);

        // 未冻结 active 子最终比例分配（余数补末位，沿用 Qt 语义）
        Map<SceneNode, Integer> alloc = new IdentityHashMap<>();
        long distributed = 0;
        for (int i = 0; i < active.size(); i++) {
            SceneNode ch = active.get(i);
            int w;
            if (i == active.size() - 1) {
                w = (int) (remainingFree - distributed);
            } else {
                w = (int) (remainingFree * effectiveGrowRow(ch) / remainingW);
                distributed += w;
            }
            alloc.put(ch, w);
        }
        alloc.putAll(frozen);
        alloc.putAll(percentAlloc);
        return alloc;
    }

    /**
     * 取节点在 ROW 主轴的有效 grow 权重：显式 flexGrow>0 优先；否则 fillParentWidth 视为隐式 1。
     *
     * <p>与 {@link #effectiveGrow}（COLUMN 主轴）对称：COLUMN 主轴有 fillParentHeight 隐式
     * grow=1 桥（向后兼容旧「唯一 fill 子」），ROW 主轴有 fillParentWidth 隐式 grow=1 桥。
     * 两轴对称，根因是 SceneNode 已提供 fillParentWidth / fillParentHeight 双字段。
     * ROW 主轴的「fill 父宽」语义由 widthSizing=FILL 默认覆盖容器自身宽，fillParentWidth
     * 则在 ROW 子节点上表达「参与主轴 grow 分配」的隐式意图。</p>
     *
     * @param ch 子节点
     * @return 有效 grow 权重（flexGrow>0 返回 flexGrow，否则 fillParentWidth 为 true 返回 1，否则 0）
     */
    private static int effectiveGrowRow(SceneNode ch) {
        int g = ch.getFlexGrow();
        return g > 0 ? g : (ch.isFillParentWidth() ? 1 : 0);
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
     * 打印"容器自身高度无法先验导致 grow 分配放弃"的 WARN（per-node 去重）。
     *
     * <p>触发场景：COLUMN 容器有 grow/fill 子，但容器自身高度无法先验确定
     * （非 fill/grow/percent 且无 preferredHeight，或 scrollable）。grow 子将回退
     * shrink-to-fit，若该容器处于 scrollable viewport 的父链，会导致 viewport 被内容撑大、
     * maxScroll=0。日志只打结构特征（不打节点 id，SceneNode 无稳定 id）。</p>
     *
     * @param node       容器节点
     * @param childCount 容器子节点数
     */
    private void warnContainerHeightUnconstrained(SceneNode node, int childCount) {
        if (!warnedContainerHeight.add(node)) {
            return; // 本节点已警告过，去重
        }
        LOG.warn("[QzUiLib/Layout] COLUMN 容器 grow 分配放弃：容器自身高度无法先验"
                + "（非 fill/grow/percent 且无 preferredHeight，或 scrollable），"
                + "但存在 flexGrow/fillParentHeight 子（childCount=" + childCount + "）。"
                + "grow 子将回退 shrink，若该容器是 scrollable viewport 的父链，"
                + "会导致 viewport 被内容撑大、maxScroll=0。"
                + "修复：给该容器设 setPreferredHeight(...) 或确保父链下传确定高约束。");
    }

    /**
     * 打印"固定兄弟高度无法先验导致 grow 分配放弃"的 WARN（per-node 去重）。
     *
     * <p>触发场景：COLUMN 容器有 grow/fill 子，但某个固定兄弟（无 preferredHeight 的容器节点）
     * 高度无法先验。grow 子将回退 shrink-to-fit，若该容器处于 scrollable viewport 的父链，
     * 会导致 viewport 被内容撑大、maxScroll=0。日志只打结构特征（不打节点 id）。</p>
     *
     * @param node        容器节点
     * @param sibling     撞顶的固定兄弟（无法先验高度的节点）
     * @param childCount  容器子节点数
     */
    private void warnSiblingHeightUnconstrained(SceneNode node, SceneNode sibling, int childCount) {
        if (!warnedSiblingHeight.add(node)) {
            return; // 本节点已警告过，去重
        }
        boolean siblingIsContainer = !sibling.__getChildren().isEmpty();
        boolean siblingHasText = sibling.getText() != null;
        LOG.warn("[QzUiLib/Layout] COLUMN 容器 grow 分配放弃：容器有 flexGrow/fillParentHeight 子，"
                + "但固定兄弟（无 preferredHeight 的容器节点，childCount=" + childCount
                + "，siblingIsContainer=" + siblingIsContainer
                + "，siblingHasText=" + siblingHasText + "）高度无法先验。"
                + "grow 子将回退 shrink，若该容器是 scrollable viewport 的父链，"
                + "会导致 viewport 被内容撑大、maxScroll=0。"
                + "修复：给该固定兄弟设 setPreferredHeight(...)。");
    }

    /**
     * 打印"容器自身宽度无法先验导致 ROW grow 分配放弃"的 WARN（per-node 去重）。
     *
     * <p>对称于 {@link #warnContainerHeightUnconstrained}。触发场景：ROW 容器有 flexGrow 子，
     * 但父级未下传确定宽约束（availableWidth == UNCONSTRAINED）。grow 子将回退 shrink-to-fit，
     * 若该容器处于 scrollable viewport 的父链，会导致 viewport 被内容撑大、maxScroll=0。</p>
     *
     * @param node       容器节点
     * @param childCount 容器子节点数
     */
    private void warnContainerWidthUnconstrained(SceneNode node, int childCount) {
        if (!warnedContainerWidth.add(node)) {
            return; // 本节点已警告过，去重
        }
        LOG.warn("[QzUiLib/Layout] ROW 容器 grow 分配放弃：容器自身宽度无法先验"
                + "（父级未下传确定宽约束 availableWidth==UNCONSTRAINED），"
                + "但存在 flexGrow 子（childCount=" + childCount + "）。"
                + "grow 子将回退 shrink，若该容器是 scrollable viewport 的父链，"
                + "会导致 viewport 被内容撑大、maxScroll=0。"
                + "修复：确保父链下传确定宽约束，或给该容器设 setPreferredWidth(...)。");
    }

    /**
     * 打印"固定兄弟宽度无法先验导致 ROW grow 分配放弃"的 WARN（per-node 去重）。
     *
     * <p>对称于 {@link #warnSiblingHeightUnconstrained}。触发场景：ROW 容器有 flexGrow 子，
     * 但某个固定兄弟（无 preferredWidth 的容器节点）宽度无法先验。</p>
     *
     * @param node        容器节点
     * @param sibling     撞顶的固定兄弟（无法先验宽度的节点）
     * @param childCount  容器子节点数
     */
    private void warnSiblingWidthUnconstrained(SceneNode node, SceneNode sibling, int childCount) {
        if (!warnedSiblingWidth.add(node)) {
            return; // 本节点已警告过，去重
        }
        boolean siblingIsContainer = !sibling.__getChildren().isEmpty();
        boolean siblingHasText = sibling.getText() != null;
        LOG.warn("[QzUiLib/Layout] ROW 容器 grow 分配放弃：容器有 flexGrow 子，"
                + "但固定兄弟（无 preferredWidth 的容器节点，childCount=" + childCount
                + "，siblingIsContainer=" + siblingIsContainer
                + "，siblingHasText=" + siblingHasText + "）宽度无法先验。"
                + "grow 子将回退 shrink，若该容器是 scrollable viewport 的父链，"
                + "会导致 viewport 被内容撑大、maxScroll=0。"
                + "修复：给该固定兄弟设 setPreferredWidth(...)。");
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
