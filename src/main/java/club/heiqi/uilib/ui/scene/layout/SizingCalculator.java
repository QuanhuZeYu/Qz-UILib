package club.heiqi.uilib.ui.scene.layout;

import java.util.List;
import java.util.Set;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.SceneLineClamp;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.SceneTextMode;

/**
 * 尺寸计算器 —— scene 布局算法的纯读函数集（阶段 4.1 从 SceneLayoutEngine 拆出）。
 *
 * <h3>职责边界</h3>
 * <p>只读：节点属性 + 约束 + 子节点 cachedLayout（只读不写）+ measurer 度量。
 * 不引入自己的可变状态字段（仅持有 final 的 measurer 与 measuredTextNodes 引用）。</p>
 *
 * <p><b>关于 measuredTextNodes 的写操作</b>：{@link #computeWidth} 内的
 * {@code measuredTextNodes.add(node)} 与 {@code node.__setLastMeasuredEpoch(...)}
 * 是 epoch 失效链（P0 命门）的核心登记动作，逐字从主引擎搬迁保留。
 * SizingCalculator 自身不持有可变状态字段，measuredTextNodes 由主引擎构造时注入
 * （final 引用），写入的是主引擎的 Set，语义与原主引擎内联时逐位等价（I7/I8）。
 * 删除这两行会破坏 epoch 失效链，导致字体 reload 后干净子树文本不更新。</p>
 *
 * <h3>跨类契约（★最高风险，改一处必须同步另一处）</h3>
 * <p>见 {@link #computeWidth(SceneNode, Constraints, boolean)} 的 Javadoc——
 * computeWidth 返回的 outerWidth 是后续所有"内宽 = outerWidth - padding"计算的权威基准，
 * ConstraintResolver.buildChildConstraints 与 FlexLayouter.positionChildren 必须用同一
 * SizingCalculator 实例的 computeWidth。</p>
 */
class SizingCalculator {

    /**
     * 构造注入：文本度量服务（scene 核心只认窄端口，不 import ui.text.*）
     */
    private final SceneTextMeasurer measurer;

    /**
     * 本帧测量过文本的叶节点集合（由主引擎注入，epoch 失效链遍历器）。
     *
     * <p>SizingCalculator 不持有自己的可变状态；此 Set 由主引擎拥有，
     * computeWidth 内的 add 是 epoch 失效链登记动作（逐字搬迁保留，见类 Javadoc）。</p>
     */
    private final Set<SceneNode> measuredTextNodes;

    /**
     * 使用指定文本度量服务与文本叶登记集合创建尺寸计算器。
     *
     * @param measurer          文本度量服务（非 null）
     * @param measuredTextNodes 文本叶登记集合（非 null，由主引擎拥有并注入）
     */
    SizingCalculator(SceneTextMeasurer measurer, Set<SceneNode> measuredTextNodes) {
        if (measurer == null) {
            throw new IllegalArgumentException("SceneTextMeasurer 不得为 null");
        }
        if (measuredTextNodes == null) {
            throw new IllegalArgumentException("measuredTextNodes 不得为 null");
        }
        this.measurer = measurer;
        this.measuredTextNodes = measuredTextNodes;
    }

    /**
     * 计算节点宽度（解除偏离 1 的核心）。
     *
     * <ul>
     *   <li>显式 preferredWidth（&gt;0）：<b>最高优先级</b>，直接返回该值（外尺寸，含 padding），
     *       压过下列所有现有决策。用于固定宽控件（Checkbox box / slider thumb 等）。</li>
     *   <li>叶节点（无子节点）且有文本：shrink-to-fit，
     *       {@code min(outerWidth, measureWidth(text, fontSize) + padLeft + padRight)}，
     *       使叶节点主轴宽=内在宽（不被 cross-align STRETCH 改写为可用宽），
     *       ROW+CENTER 主轴偏移恢复非 0。</li>
     *   <li>叶节点无文本：宽=outerWidth（保留现状，preferredHeight 矩形仍铺满）。</li>
     *   <li>容器节点：默认宽=outerWidth；设置 {@link SceneNode.WidthSizing#SHRINK} 后，
     *       在子节点已布局时按内容宽回收，并被 outerWidth clamp。</li>
     * </ul>
     *
     * <p>优先级总结：preferredWidth &gt; percentWidth（有宽约束时）&gt; 容器 widthSizing &gt; 文本 shrink-to-fit &gt; 无文本 fill。
     * percentWidth 在无宽约束（{@link Constraints#UNCONSTRAINED}）时回退 shrink（忽略 percent）。</p>
     *
     * <p>注意：父 STRETCH（默认）在 cross 维度仍会把叶 cross 改写为 crossAvail，
     * 故默认 COLUMN+STRETCH 的 fill 宽度行为零回归（叶 cross=宽，被改写填满）；
     * ROW 下叶 main=宽=内在宽不被 cross-align 改写。子节点设了 cross 向 preferred
     * 时则在 STRETCH 分支被豁免改写（见 FlexLayouter.positionChildren）。</p>
     *
     * @param node        节点
     * @param constraints 当前节点的布局约束
     * @return 节点宽度（像素）
     */
    public int computeWidth(SceneNode node, Constraints constraints) {
        return computeWidth(node, constraints, true);
    }

    /**
     * 计算节点宽度。
     *
     * <p>当 {@code allowChildCacheForShrink=false} 时，SHRINK 容器不得读取子节点
     * cachedLayout，必须保守回退到外部约束宽度。该分支仅供下传约束和约束变化判断使用，
     * 防止读取未布局或陈旧子宽度。真正的 shrink-to-fit 宽度只在子节点布局完成后的
     * positionChildren 阶段回收。</p>
     *
     * <p><b>★ 耦合不变式（跨类契约 1：内宽基准权威）</b><br>
     * 本方法返回的 outerWidth 是后续所有"内宽 = outerWidth - padding"计算的权威基准。
     * <ul>
     *   <li>ConstraintResolver.buildChildConstraints 用 {@code computeWidth(node, c, false) - padH}
     *       算下传给子的 innerWidth；</li>
     *   <li>FlexLayouter.positionChildren 步骤 1 用 {@code computeWidth(node, c, true) - padH}
     *       算自己的 innerWidth。</li>
     * </ul>
     * 两处必须用同一 SizingCalculator 实例的 computeWidth，确保固定宽容器（有 preferredWidth）
     * 的子节点不按裸约束宽布局而溢出父盒。<br>
     * <b>改 computeWidth 优先级链时必须同步检查 ConstraintResolver 与 FlexLayouter 的两处调用。</b>
     * 当前调用点：ConstraintResolver.buildChildConstraints（computeWidth(c, false)）
     * 与 SceneLayoutEngine 主流程（computeWidth(c, true) 后传给 FlexLayouter.positionChildren）。</p>
     *
     * @param node                     节点
     * @param constraints              当前节点的布局约束
     * @param allowChildCacheForShrink 是否允许 SHRINK 容器读取子布局缓存
     * @return 节点宽度（像素）
     */
    public int computeWidth(SceneNode node, Constraints constraints, boolean allowChildCacheForShrink) {
        // 最高优先级：显式 preferredWidth 钉死盒宽（外尺寸，含 padding），
        // 压过容器 fill / 文本 shrink-to-fit / 无文本 fill 三种现有决策。
        // 显式钉死时不 clamp maxWidth（preferredWidth 优先级最高，与 preferredHeight 对称）。
        if (node.getPreferredWidth() > 0) {
            return node.getPreferredWidth();
        }

        // percentWidth 分支：相对父内宽（availableWidth），无宽约束时回退 shrink（忽略 percent）。
        // 优先级位于 preferredWidth 之后、SHRINK/文本 shrink/fill 之前。
        // availableWidth == UNCONSTRAINED 时走 fallback（进入下方 SHRINK/fill 分支）。
        if (node.getPercentWidth() > 0 && constraints.getAvailableWidth() != Constraints.UNCONSTRAINED) {
            // 用 long 中间量防 availableWidth * pct 溢出
            int pctW = (int) ((long) constraints.getAvailableWidth() * node.getPercentWidth() / 100);
            return clampWidth(node, pctW);
        }

        // scrollableX 视口：宽度钉死为视口宽（横向滚动地基），子内容宽由约束解耦（见 ConstraintResolver）
        if (node.isScrollableX()) {
            return viewportWidth(node, constraints);
        }

        int outerWidth = constraints.getAvailableWidth();
        List<SceneNode> children = node.__getChildren();
        if (!children.isEmpty()) {
            if (node.getWidthSizing() == SceneNode.WidthSizing.SHRINK && allowChildCacheForShrink) {
                return clampWidth(node, computeShrinkContainerWidth(node, outerWidth));
            }
            // 容器节点默认宽=可用宽（fill 语义）；SHRINK 在下传约束阶段也回退此宽度。
            return clampWidth(node, outerWidth);
        }

        String text = node.getText();
        int padH = node.getPaddingLeft() + node.getPaddingRight();
        if (text == null) {
            // 无文本叶节点：保留装饰/矩形语义，宽=可用宽
            return clampWidth(node, outerWidth);
        }

        if (text.isEmpty()) {
            // 显式空文本叶：内容宽为 0，仅保留自身 padding；仍登记为文本节点参与 epoch 失效链。
            measuredTextNodes.add(node);
            node.__setLastMeasuredEpoch(measurer.epoch());
            return clampWidth(node, padH);
        }

        // 文本叶节点：shrink-to-fit。多行取各行最大测量宽；wrap 节点内容宽即 maxTextWidth。
        int wrapWidth = node.getMaxTextWidth();
        int intrinsicWidth = wrapWidth > 0
                ? wrapWidth + padH
                : measureMaxLineWidth(text, node.getFontSize()) + padH;
        // 记录该叶为本帧测量过的文本节点，供 epoch 失效链向上冒泡使用；
        // 同时写节点级 epoch 快照，供下一帧入口节点级比对（与 lastConstraints 同构）。
        measuredTextNodes.add(node);
        node.__setLastMeasuredEpoch(measurer.epoch());
        // UNCONSTRAINED（scrollableX 视口下传的无宽约束）时不再 clamp，取自然测量宽；
        // 有约束时保持 min(可用宽, 自然宽) 的 shrink-to-fit 语义。
        int boundedOuter = outerWidth == Constraints.UNCONSTRAINED ? Integer.MAX_VALUE : outerWidth;
        return clampWidth(node, Math.min(boundedOuter, intrinsicWidth));
    }

    /**
     * 宽度 clamp：maxWidth &gt; 0 时取 {@code min(w, maxWidth)}，否则原值返回。
     *
     * <p>仅用于 computeWidth 非 preferredWidth 分支出口。preferredWidth 显式钉死分支
     * 不 clamp（优先级最高）。maxWidth 是外尺寸上界（含 padding），与 computeWidth
     * 返回值口径一致。</p>
     *
     * @param node 节点
     * @param w    待 clamp 的宽度（外尺寸，含 padding）
     * @return clamp 后的宽度
     */
    private static int clampWidth(SceneNode node, int w) {
        int max = node.getMaxWidth();
        return max > 0 ? Math.min(w, max) : w;
    }

    /**
     * 基于已布局子节点缓存计算 SHRINK 容器宽度。
     *
     * <p>ROW 容器取子宽之和 + gap + 水平 padding；COLUMN 容器取子最大宽 + 水平 padding。
     * 若任一子节点缓存缺失，说明子布局结果不可用，安全回退外部约束宽度。</p>
     *
     * <p><b>子 marginH 计入占位（CSS box model 语义）</b>：SHRINK 容器应包住子的完整占位
     * （子宽 + marginH），否则子 marginH 部分会溢出 SHRINK 容器边界。ROW 分支累加
     * {@code childBox.getWidth() + child.marginH()}；COLUMN 分支取
     * {@code max(contentWidth, childBox.getWidth() + child.marginH())}。</p>
     *
     * @param node       SHRINK 容器节点
     * @param outerWidth 父级下传的可用外宽
     * @return 被外部可用宽度 clamp 后的容器宽度
     */
    private int computeShrinkContainerWidth(SceneNode node, int outerWidth) {
        boolean row = node.getFlexDirection() == FlexDirection.ROW;
        int contentWidth = 0;
        int childCount = 0;
        for (SceneNode child : node.__getChildren()) {
            LayoutBox childBox = (LayoutBox) child.getCachedLayout();
            if (childBox == null) {
                return outerWidth;
            }
            // 子占位含 marginH：SHRINK 容器包住子完整占位（CSS box model）
            int occupied = childBox.getWidth() + child.marginH();
            if (row) {
                contentWidth += occupied;
            } else if (occupied > contentWidth) {
                contentWidth = occupied;
            }
            childCount++;
        }
        int totalGap = row && childCount > 1 ? node.getGap() * (childCount - 1) : 0;
        int padH = node.getPaddingLeft() + node.getPaddingRight();
        return Math.min(outerWidth, contentWidth + totalGap + padH);
    }

    /**
     * 计算节点高度。
     *
     * <p>先按 shrink-to-fit 计算内容高度：
     * 容器节点（有子节点）= 子节点 cachedLayout 高度之和；
     * 叶节点 = 文本行数 × 行高（{@link SceneTextMeasurer#lineHeight}）；
     * 无文本叶节点 = 0。</p>
     *
     * <p>如果节点设置了 {@code fillParentHeight} 且约束有高度约束，
     * 则返回 max(内容高度, 约束高度) 实现"至少填满"语义。</p>
     *
     * <h3>scrollable 视口钉死分支（纵向滚动地基）</h3>
     * <p>{@code node.isScrollable()==true} 时，<b>不走</b> {@code max(natural, preferredHeight)}
     * 的内容撑大逻辑，而是<b>直接返回视口高</b>，主动忽略内容高——这是 viewport/content 高度
     * 解耦的关键：viewport 自身盒高固定为视口高，子内容子树总高可超视口高（这正是滚动的前提）。
     * 视口高来源优先级：preferredHeight（&gt;0）&gt; fillParentHeight 的约束高。两者皆无时
     * 回退内容高；若收到高度约束，则将该约束作为 maxHeight cap，支持 overlay listbox 等
     * 「内容少时包住、内容多时截断并滚动」场景。</p>
     *
     * <p>注意：本分支只钉死 viewport <b>自身</b>的 LayoutBox.height；子内容仍由
     * positionChildren 步骤 4 按 COLUMN 主轴 START 从 padTop 起累加定位，
     * 总高超视口部分由 paint 阶段的 CLIP 裁剪 + {@code -scrollOffsetY} 平移处理，
     * 布局层绝不感知 scrollOffset（守 I7：滚动不触发重排）。</p>
     *
     * @param node        节点
     * @param constraints 当前节点的布局约束
     * @return 节点高度（像素）
     */
    public int computeHeight(SceneNode node, Constraints constraints) {
        // scrollable 视口：委托唯一决策点 viewportHeight（isScrollable 分支收口，消除散落）
        // viewportHeight 已有自己的 cap 语义（min(内容高, 约束高)），maxHeight 对 scrollable 无意义，不 clamp。
        if (node.isScrollable()) {
            return viewportHeight(node, constraints);
        }

        // 1. 计算内容高度（shrink-to-fit）
        int contentHeight = computeContentHeight(node);

        // 2. fill 分支：内容高度 vs 约束高度取 max
        // ★ 隐式 fill 主轴：flexGrow>0 在 COLUMN 主轴吃父分配空间，等价 fill（CSS flexbox 语义）
        //   与 ConstraintResolver.effectiveGrow 反向对称（fill→隐式 grow=1，grow→隐式 fill 主轴）
        // ★ percent 子隐式 fill：percentHeight>0 收到下传 tight 高约束后，
        //   max(contentHeight, 约束高) 返回约束高（percentHeight），与 grow 子隐式 fill 对称
        if ((node.isFillParentHeight() || node.getFlexGrow() > 0 || node.getPercentHeight() > 0)
                && constraints.hasHeightConstraint()) {
            return clampHeight(node, Math.max(contentHeight, constraints.getAvailableHeight()));
        }
        return clampHeight(node, contentHeight);
    }

    /**
     * scrollableX 视口宽度的唯一决策点（横向滚动地基，与 {@link #viewportHeight} 对称）。
     *
     * <p>优先级口径：</p>
     * <ol>
     *   <li>preferredWidth &gt; 0 → 直接返回（显式钉死）</li>
     *   <li>有宽约束 → 返回约束宽（fill 语义，保持有约束场景外观与现状一致）</li>
     *   <li>无约束 → 回退内容宽（子缓存缺失时回退 0，后续布局收敛帧修正）</li>
     * </ol>
     *
     * @param node        节点（必须是 isScrollableX()==true 的调用方）
     * @param constraints 当前节点收到的约束
     * @return 视口宽度（像素）
     */
    public int viewportWidth(SceneNode node, Constraints constraints) {
        if (node.getPreferredWidth() > 0) {
            return node.getPreferredWidth();
        }
        if (constraints.getAvailableWidth() != Constraints.UNCONSTRAINED) {
            return clampWidth(node, constraints.getAvailableWidth());
        }
        return Math.max(0, computeShrinkContainerWidth(node, constraints.getAvailableWidth()));
    }

    /**
     * 高度 clamp：maxHeight &gt; 0 时取 {@code max(preferredHeight, min(h, maxHeight))}，
     * 否则原值返回。
     *
     * <p>仅用于 computeHeight 非 scrollable 分支出口。scrollable 分支不 clamp
     * （viewportHeight 已有 cap 语义，maxHeight 对 scrollable 无意义）。maxHeight 是
     * 外尺寸上界（含 padding），与 computeHeight 返回值口径一致。</p>
     *
     * <p><b>下限优先（CSS min-height 赢 max-height 语义）</b>：preferredHeight 作下限，
     * maxHeight 作上限，矛盾时（preferredHeight &gt; maxHeight）下限赢，返回 preferredHeight。
     * computeContentHeight 已把 preferredHeight 作下限 max 进 contentHeight，本方法不应破坏它。
     * preferredHeight=0 时 {@code max(0, min(h, max))} = {@code min(h, max)}，退化为纯 min，
     * 不影响无 preferredHeight 节点。</p>
     *
     * <p><b>与 ConstraintResolver.clampToMax 的关系</b>：clamp 公式
     * {@code max(preferredHeight, min(h, maxHeight))}（min 赢 CSS 语义）。clampToMax 实现
     * {@code min(natural, max)}，省略 {@code max(preferredHeight, ...)}，因为 clampToMax 只在
     * priorKnownChildHeight 的自然高分支调用（该分支前提 preferredHeight&lt;=0，无需下限保护）；
     * clampHeight 用在 computeHeight 出口（preferredHeight 可能 &gt;0，需完整三参数 clamp）。</p>
     *
     * @param node 节点
     * @param h    待 clamp 的高度（外尺寸，含 padding）
     * @return clamp 后的高度
     */
    private static int clampHeight(SceneNode node, int h) {
        int max = node.getMaxHeight();
        if (max <= 0) return h;
        int clamped = Math.min(h, max);
        int pref = node.getPreferredHeight();
        return pref > 0 ? Math.max(pref, clamped) : clamped;
    }

    /**
     * scrollable 视口高度的唯一决策点（纯读，不读子 cache）。
     *
     * <p>优先级口径（与 computeHeight 旧 scrollable 分支逐位等价）：</p>
     * <ol>
     *   <li>preferredHeight &gt; 0 → 直接返回（视口高度被显式钉死）</li>
     *   <li>fillParentHeight 且有高度约束 → 返回约束高（吃满父高）</li>
     *   <li>回退内容高，有约束时按 min(内容高, 约束高) 截断（支持 overlay maxHeight 等场景）</li>
     *   <li>无约束 → 返回内容高</li>
     * </ol>
     *
     * <p>主动忽略内容撑大（首次解耦 viewport/content），是布局计算语义的一等例外，
     * NORTH_STAR §4 已转正为正式能力。详见偏离登记 2026-06-21-扩展。</p>
     *
     * <p><b>耦合不变式（跨类契约 2：viewportHeight 与 priorKnownInnerHeight）</b>：
     * 本方法 fill 分支（preferredHeight&lt;=0 且 fillParentHeight 且
     * hasHeightConstraint 返回 availableHeight）必须与主引擎 priorKnownInnerHeight
     * 中 fill 分支的口径一致——两处共享同一"fill 容器高度由约束决定"语义，改一处必须改另一处。
     * priorKnownInnerHeight 已搬到 ConstraintResolver，其 fill 分支口径与
     * 本方法 fill 分支口径一致（详见 ConstraintResolver.priorKnownInnerHeight Javadoc）。</p>
     *
     * @param node        节点（必须是 isScrollable()==true 的调用方）
     * @param constraints 当前节点收到的约束
     * @return 视口高度（像素）
     */
    public int viewportHeight(SceneNode node, Constraints constraints) {
        if (node.getPreferredHeight() > 0) {
            return node.getPreferredHeight();
        }
        if (node.isFillParentHeight() && constraints.hasHeightConstraint()) {
            return constraints.getAvailableHeight();
        }
        // 既无 preferredHeight 也无 fill 约束高 → 回退内容高；
        // 但若存在高度约束，按 min(内容高, 约束高) 截断——支持 overlay maxHeight 等场景。
        int contentHeight = computeContentHeight(node);
        if (constraints.hasHeightConstraint()) {
            return Math.min(contentHeight, constraints.getAvailableHeight());
        }
        return contentHeight;
    }

    /**
     * 判定节点高度是否"被约束驱动"——即节点高度不由子内容决定而是由约束决定，
     * 约束变化时必须重算自身（守 I8）。
     *
     * <p>覆盖四类节点：</p>
     * <ul>
     *   <li>fill 节点：高度取 max(内容高, 约束高)，约束变必重算</li>
     *   <li>grow 节点（flexGrow&gt;0）：COLUMN 主轴吃父分配空间，等价隐式 fill，
     *       约束变必重算（与 computeHeight fill 分支条件对称）</li>
     *   <li>percent 节点（percentHeight&gt;0）：相对父先验内高，收到下传 tight 高约束后
     *       隐式 fill 返回该高（与 grow 子隐式 fill 对称），约束变必重算</li>
     *   <li>scrollable 回退 cap 节点：无 preferredHeight 也无 fill，但有约束时按
     *       min(内容高, 约束高) 截断，约束变需重算</li>
     * </ul>
     *
     * <p>本谓词收口 layoutInternal 中叶/容器两分支的重复子条件
     * （原 :255-258 / :265-268 两段逐字相同的 isScrollable && !isFillParentHeight
     * && preferredHeight&lt;=0 组合），单一权威，改一处即生效两处。</p>
     *
     * @param node 节点
     * @return true 表示本节点高度由约束驱动
     */
    public boolean isHeightConsumingConstraint(SceneNode node) {
        return node.isFillParentHeight()
                || node.getFlexGrow() > 0
                || node.getPercentHeight() > 0
                || (node.isScrollable()
                        && !node.isFillParentHeight()
                        && node.getPreferredHeight() <= 0);
    }

    /**
     * 按 shrink-to-fit 计算节点的内容高度（含上下 padding，不考虑 fill）。
     *
     * <p>按 {@code flexDirection} 区分容器主轴：</p>
     * <ul>
     *   <li>ROW 容器：高度 = 子节点最大高度（crossMax） + 上下 padding。</li>
     *   <li>COLUMN 容器：高度 = 子节点高度之和 + gap*(n-1) + 上下 padding。</li>
     *   <li>叶节点：文本高度 + 上下 padding。</li>
     * </ul>
     *
     * <p><b>preferredHeight 语义（外尺寸下限）</b>：preferredHeight 表示
     * 「最终盒外尺寸（含 padding）」，与 {@code LayoutBox.height} / preferredWidth 对称。
     * 容器分支与叶分支均先算出自然外高（聚合/文本 + padV），再与 preferredHeight 取 max，
     * preferredHeight 不重复叠加 padding。</p>
     *
     * @param node 节点
     * @return 内容高度（像素）
     */
    private int computeContentHeight(SceneNode node) {
        int padV = node.getPaddingTop() + node.getPaddingBottom();
        List<SceneNode> children = node.__getChildren();
        if (!children.isEmpty()) {
            boolean row = node.getFlexDirection() == FlexDirection.ROW;
            if (row) {
                // ROW 容器：高度 = 子节点最大高度（含子 marginV 交叉轴占用）+ 上下 padding
                int crossMax = 0;
                for (SceneNode child : children) {
                    LayoutBox childBox = (LayoutBox) child.getCachedLayout();
                    if (childBox != null) {
                        // cross 轴高含子 marginV：子在交叉轴的占位 = 子高 + marginV
                        int occupied = childBox.getHeight() + child.marginV();
                        if (occupied > crossMax) {
                            crossMax = occupied;
                        }
                    }
                }
                // 自然外高（聚合 + padV）与 preferredHeight（外尺寸下限）取 max
                int natural = crossMax + padV;
                return node.getPreferredHeight() > 0
                        ? Math.max(natural, node.getPreferredHeight())
                        : natural;
            }
            // COLUMN 容器：高度 = 子节点高度之和（含子 marginV 主轴占用）+ gap*(count-1) + 上下 padding
            int total = 0;
            int count = 0;
            for (SceneNode child : children) {
                LayoutBox childBox = (LayoutBox) child.getCachedLayout();
                if (childBox != null) {
                    // main 轴高含子 marginV：子在主轴的占位 = 子高 + marginV
                    total += childBox.getHeight() + child.marginV();
                    count++;
                }
            }
            int totalGap = count > 1 ? node.getGap() * (count - 1) : 0;
            // 自然外高（聚合 + gap + padV）与 preferredHeight（外尺寸下限）取 max
            int natural = total + totalGap + padV;
            return node.getPreferredHeight() > 0
                    ? Math.max(natural, node.getPreferredHeight())
                    : natural;
        }

        // 叶节点：文本高度（wrap 感知：拆行后逐行行高求和；否则行数 × 行高）；无文本 → 高度为 0
        int textHeight = leafTextHeight(node);
        // 自然外高（文本高 + padV）与 preferredHeight（外尺寸下限）取 max，padV 不重复加
        int naturalLeaf = textHeight + padV;
        return node.getPreferredHeight() > 0
                ? Math.max(naturalLeaf, node.getPreferredHeight())
                : naturalLeaf;
    }

    /**
     * 统计文本逻辑行数（按 {@code \n} 切分），空文本视作 1 行。
     *
     * <p><b>可见性说明</b>：本方法原为 private，但主引擎的 {@code priorKnownChildHeight}
     * 仍需直接调用（阶段 4.1 未搬迁该方法），故改为包级（package-private）以允许同包
     * SceneLayoutEngine 跨类访问。其余 private 方法（computeContentHeight /
     * computeShrinkContainerWidth / measureMaxLineWidth）仅本类内部经由 public 路径触达，
     * 保持 private。</p>
     *
     * @param text 文本内容
     * @return 行数（至少 1）
     */
    /**
     * 计算文本叶节点的自然文本高度（wrap 感知）。
     *
     * <p>{@code maxTextWidth>0} 时按 wrap 拆行、逐行行高求和（混排行取该行最大字号行高）；
     * 否则按 {@code \n} 逻辑行数 × 统一行高（旧口径，零回归）。</p>
     *
     * @param node 文本叶节点
     * @return 文本高度（UI 像素，空文本为 0）
     */
    int leafTextHeight(SceneNode node) {
        String text = node.getText();
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int fontSizePx = node.getFontSize();
        int wrapWidth = node.getMaxTextWidth();
        SceneTextMode textMode = node.getTextMode();
        // 拆行（wrap 软换行 / 非 wrap 硬换行）后经 SceneLineClamp 截断（maxLines + 省略号），
        // 与绘制同口径；逐行行高求和（RAW/MINECRAFT 每行同高，富文本每行按行内最大显式字号）。
        List<String> lines = measurer.splitLines(text, fontSizePx, wrapWidth > 0 ? wrapWidth : 0, textMode);
        List<String> clamped = SceneLineClamp.clamp(lines, node.getMaxLines(), node.isEllipsis(),
                measurer, fontSizePx, wrapWidth, textMode);
        int total = 0;
        for (String line : clamped) {
            total += node.resolveLineHeight(measurer.lineHeight(line, fontSizePx, textMode));
        }
        return total;
    }

    int countLines(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    /**
     * 测量多行文本中各行的最大 UI 像素宽度。
     *
     * @param text       文本内容（按 {@code \n} 切分多行）
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
}
