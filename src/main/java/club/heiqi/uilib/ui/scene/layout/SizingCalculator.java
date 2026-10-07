package club.heiqi.uilib.ui.scene.layout;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLinePlan;

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
 * （final 引用），写入的是主引擎的 Set，语义与原主引擎内联时逐位等价。
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


    public int computeWidth(SceneNode node, Constraints constraints, boolean allowChildCacheForShrink) {
        // 压过容器 fill / 文本 shrink-to-fit / 无文本 fill 三种现有决策。
        if (node.getPreferredWidth() > 0) {
            return node.getPreferredWidth();
        }

        // percentWidth 分支：相对父内宽（availableWidth），无宽约束时回退 shrink（忽略 percent）。
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
        // ★ 内容折叠（纯加法，默认 false 时逐值不变）：折叠节点的子树与自身文本均不参与尺寸推导，
        //   故此处按「无子无文本」处理 —— 与「子树挂摘」形态逐值等价，且使
        //   ConstraintResolver 的先验尺寸恒可知（U-P5-17 grow 先验闸门根除点之一）。
        List<SceneNode> children = node.isCollapsed()
                ? Collections.<SceneNode>emptyList() : node.__getChildren();
        if (!children.isEmpty()) {
            if (node.getWidthSizing() == SceneNode.WidthSizing.SHRINK && allowChildCacheForShrink) {
                return clampWidth(node, computeShrinkContainerWidth(node, outerWidth));
            }
            // 容器节点默认宽=可用宽（fill 语义）；SHRINK 在下传约束阶段也回退此宽度。
            return clampWidth(node, outerWidth);
        }

        String text = node.isCollapsed() ? null : node.getText();
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
                : measureMaxLineWidth(text, node.effectiveFontSize()) + padH;
        // 记录该叶为本帧测量过的文本节点，供 epoch 失效链向上冒泡使用；
        // 同时写节点级 epoch 快照，供下一帧入口节点级比对（与 lastConstraints 同构）。
        measuredTextNodes.add(node);
        node.__setLastMeasuredEpoch(measurer.epoch());
        // UNCONSTRAINED（scrollableX 视口下传的无宽约束）时不再 clamp，取自然测量宽；
        // 有约束时保持 min(可用宽, 自然宽) 的 shrink-to-fit 语义。
        int boundedOuter = outerWidth == Constraints.UNCONSTRAINED ? Integer.MAX_VALUE : outerWidth;
        return clampWidth(node, Math.min(boundedOuter, intrinsicWidth));
    }


    private static int clampWidth(SceneNode node, int w) {
        int max = node.getMaxWidth();
        int clamped = max > 0 ? Math.min(w, max) : w;
        int min = node.getMinWidth();
        return min > 0 ? Math.max(clamped, min) : clamped;
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


    public int viewportWidth(SceneNode node, Constraints constraints) {
        if (node.getPreferredWidth() > 0) {
            return node.getPreferredWidth();
        }
        if (constraints.getAvailableWidth() != Constraints.UNCONSTRAINED) {
            return clampWidth(node, constraints.getAvailableWidth());
        }
        return Math.max(0, computeShrinkContainerWidth(node, constraints.getAvailableWidth()));
    }


    private static int clampHeight(SceneNode node, int h) {
        int max = node.getMaxHeight();
        if (max <= 0) return h;
        int clamped = Math.min(h, max);
        int pref = node.getPreferredHeight();
        return pref > 0 ? Math.max(pref, clamped) : clamped;
    }


    public int viewportHeight(SceneNode node, Constraints constraints) {
        if (node.getPreferredHeight() > 0) {
            return node.getPreferredHeight();
        }
        if (node.isFillParentHeight() && constraints.hasHeightConstraint()) {
            return constraints.getAvailableHeight();
        }
        // 但若存在高度约束，按 min(内容高, 约束高) 截断——支持 overlay maxHeight 等场景。
        int contentHeight = computeContentHeight(node);
        if (constraints.hasHeightConstraint()) {
            return Math.min(contentHeight, constraints.getAvailableHeight());
        }
        return contentHeight;
    }


    public boolean isHeightConsumingConstraint(SceneNode node) {
        return node.isFillParentHeight()
                || node.getFlexGrow() > 0
                || node.getPercentHeight() > 0
                || (node.isScrollable()
                        && !node.isFillParentHeight()
                        && node.getPreferredHeight() <= 0);
    }


    private int computeContentHeight(SceneNode node) {
        int padV = node.getPaddingTop() + node.getPaddingBottom();
        // ★ 内容折叠（纯加法，默认 false 时逐值不变）：与 computeWidth 同口径 ——
        List<SceneNode> children = node.isCollapsed()
                ? Collections.<SceneNode>emptyList() : node.__getChildren();
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
            int natural = total + totalGap + padV;
            return node.getPreferredHeight() > 0
                    ? Math.max(natural, node.getPreferredHeight())
                    : natural;
        }

        // 叶节点：文本高度（wrap 感知：拆行后逐行行高求和；否则行数 × 行高）；无文本 → 高度为 0
        // 折叠节点的自身文本同属「内容」，一并折叠为 0（不登记测量、不产出文本行计划）。
        int textHeight = node.isCollapsed() ? 0 : leafTextHeight(node);
        int naturalLeaf = textHeight + padV;
        return node.getPreferredHeight() > 0
                ? Math.max(naturalLeaf, node.getPreferredHeight())
                : naturalLeaf;
    }

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
        // 一次性行计划（审查报告 §8 B2-4）：拆行 + clamp + 逐行行高 + 链接区域在布局阶段
        // 一次产出并缓存于节点，绘制阶段消费；本方法只取行块总高。
        TextLinePlan plan = TextLinePlan.build(measurer, text, node.effectiveFontSize(), node.getMaxTextWidth(),
                node.getTextMode(), node.getMaxLines(), node.isEllipsis(), node::resolveLineHeight);
        node.setCachedTextPlan(plan);
        return plan.getTotalHeight();
    }

    /**
     * 测量多行文本中各行的最大 UI 像素宽度。
     *
     * <p>包级可见：同包 {@link ConstraintResolver#priorKnownChildWidth} 复用本实现，
     * 全仓唯一的「按换行类扫行取最大行宽」单点（纯读，无副作用）。</p>
     *
     * @param text       文本内容（按 Unicode 换行类切分多行，{@code \r\n} 折叠）
     * @param fontSizePx 字号（UI 像素）
     * @return 各行测量宽的最大值
     */
    int measureMaxLineWidth(String text, int fontSizePx) {
        int max = 0;
        int start = 0;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char ch = text.charAt(i);
            if (measurer.isLineBreak(ch)) {
                String line = text.substring(start, i);
                int w = measurer.measureWidth(line, fontSizePx);
                if (w > max) {
                    max = w;
                }
                if (ch == '\r' && i + 1 < len && text.charAt(i + 1) == '\n') {
                    i++;
                }
                start = i + 1;
            }
        }
        String last = text.substring(start);
        int w = measurer.measureWidth(last, fontSizePx);
        if (w > max) {
            max = w;
        }
        return max;
    }
}
