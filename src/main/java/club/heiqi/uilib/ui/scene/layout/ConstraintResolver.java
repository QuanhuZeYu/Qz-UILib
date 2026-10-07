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
     * 尺寸计算器：提供 computeWidth（内宽基准权威）+ leafTextHeight（文本叶自然高）
     * + measureMaxLineWidth（文本最大行宽）+ viewportHeight（耦合不变式锚点）。
     *
     * <p>跨类契约 1：本字段持有的 SizingCalculator 实例必须与 FlexLayouter.positionChildren
     * 用的同一实例，确保 computeWidth 的盒宽基准在 buildChildConstraints 与 positionChildren
     * 两处一致（见 SizingCalculator.computeWidth Javadoc）。</p>
     */
    private final SizingCalculator sizing;

    /**
     * 使用指定尺寸计算器创建约束解析器。
     *
     * <p>文本度量（lineHeight / measureWidth / 拆行）一律经 {@link SizingCalculator}
     * 单点触达 measurer，本类不再直接持有度量引用（度量副本去重，2026-09-01）。</p>
     *
     * @param sizing 尺寸计算器（非 null，提供 computeWidth / leafTextHeight /
     *               measureMaxLineWidth 等纯读函数）
     */
    ConstraintResolver(SizingCalculator sizing) {
        if (sizing == null) {
            throw new IllegalArgumentException("SizingCalculator 不得为 null");
        }
        this.sizing = sizing;
    }


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


    public int priorKnownChildHeight(SceneNode child) {
        if (child.getPreferredHeight() > 0) {
            return child.getPreferredHeight();
        }
        // ★ U-P5-17 根除点（内容折叠声明，纯加法）：折叠节点的子树不参与尺寸推导
        //   （SizingCalculator 同口径），故其先验高恒可知 —— 走下方零内容叶口径，
        //   绝不因「有子容器」落回 UNCONSTRAINED。声明未开启的节点逐位不变。
        if (!child.isCollapsed() && !child.__getChildren().isEmpty()) {
            return Constraints.UNCONSTRAINED;
        }
        int padV = child.getPaddingTop() + child.getPaddingBottom();
        String text = child.isCollapsed() ? null : child.getText();
        if (text != null) {
            // wrap 感知：maxTextWidth>0 时拆行后逐行行高求和（与 SizingCalculator 同口径）
            int natural = sizing.leafTextHeight(child) + padV;
            return clampToMax(child, natural);
        }
        return clampToMax(child, padV);
    }


    public int priorKnownChildWidth(SceneNode child) {
        if (child.getPreferredWidth() > 0) {
            return child.getPreferredWidth();
        }
        // ★ 对称于 priorKnownChildHeight 的 U-P5-17 根除点（ROW 主轴孪生陷阱）：
        //   折叠节点先验宽恒可知（零内容叶口径），有子容器不再落回 UNCONSTRAINED。
        if (!child.isCollapsed() && !child.__getChildren().isEmpty()) {
            return Constraints.UNCONSTRAINED;
        }
        int padH = child.getPaddingLeft() + child.getPaddingRight();
        String text = child.isCollapsed() ? null : child.getText();
        if (text != null) {
            // 文本叶：测量各行最大宽 + padH；wrap 节点内容宽即 maxTextWidth。空文本视作 0 宽。
            int wrapWidth = child.getMaxTextWidth();
            int textW = text.isEmpty() ? 0
                    : (wrapWidth > 0 ? wrapWidth : sizing.measureMaxLineWidth(text, child.effectiveFontSize()));
            int natural = textW + padH;
            return clampToMaxWidth(child, natural);
        }
        return clampToMaxWidth(child, padH);
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


    private static int clampToMax(SceneNode node, int natural) {
        int max = node.getMaxHeight();
        return max > 0 ? Math.min(natural, max) : natural;
    }


    public int priorKnownInnerHeight(SceneNode node, Constraints constraints) {
        int padV = node.getPaddingTop() + node.getPaddingBottom();
        // ★ 耦合不变式：本 fill/grow/percent 分支口径必须与 viewportHeight 的 fill 分支一致——
        //   两处共享同一"约束驱动高度的容器高度由约束决定"语义。详见 viewportHeight Javadoc。
        // ★ grow/percent 子收到父分配的 tight 高约束后，其内高同 fill 一样由约束确定，
        //   应作先验内高下传（嵌套 grow 子容器场景：grow 子的 grow 子才能拿到确定高约束）。
        // ★ 对齐 `SizingCalculator#computeHeight` 的 fill/grow/percent 三合流口径
        //   （CSS §9.8 definite 语义：父分配 tight 高 → 子高度 definite）。
        // ★ scrollable 排除：viewport 语义主动忽略内容撑大，内高不作子先验，
        // ★ 纯读静态元数据 + 入参，不回看子 cache（避免父子布局循环依赖）。
        if ((node.isFillParentHeight() || node.getFlexGrow() > 0 || node.getPercentHeight() > 0)
                && !node.isScrollable()
                && constraints.hasHeightConstraint()) {
            // 时子只 fill 到约束高、父底留白。
            int h = Math.max(constraints.getAvailableHeight(), node.getPreferredHeight());
            return Math.max(0, h - padV);
        }
        if (node.getPreferredHeight() > 0) {
            return Math.max(0, node.getPreferredHeight() - padV);
        }
        return Constraints.UNCONSTRAINED;
    }


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
                // computeHeight fill 分支取 max(contentHeight, pctH) = contentHeight，
                // fixedH 贡献须用 max(pctH, priorKnownChildHeight)，否则 fixedH 偏小 →
                // freeH 偏大 → grow 兄弟溢出。
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

        // freeze 主循环（上界+下界对称，Qt qGeomCalc 语义；求解只读静态元数据 + 入参，不回看子 cache）
        // 撞 maxHeight 上界：冻结到 maxHeight，释放空间回流未冻结子
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
                int minH = ch.getPreferredHeight();  // 当前布局算法，目标按 spec 第 4 节迁移。
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

        int resolvedWidth = sizing.computeWidth(node, c, false);
        int innerW = Math.max(0, resolvedWidth - node.getPaddingLeft() - node.getPaddingRight());
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

        // freeze 主循环（与 COLUMN 版本对称；求解只读静态元数据 + 入参，不回看子 cache）
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
                int minW = ch.getPreferredWidth();  // 当前布局算法，目标按 spec 第 4 节迁移。
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
