package club.heiqi.uilib.ui.scene.layout;

import java.util.List;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import com.github.bsideup.jabel.Desugar;

/**
 * Flex 布局定位协作者（阶段 4.3 拆出）—— positionChildren 步骤 B/C/D 的消费者。
 *
 * <p>职责：消费 SizingCalculator 算好的 outerWidth / rootFinalHeight（步骤 A 的锁定值
 * 由主引擎传入），执行 flex 主轴/交叉轴定位：</p>
 * <ul>
 *   <li>步骤 B：主轴汇总 + 主轴起点 + 交叉轴可用空间计算；</li>
 *   <li>步骤 C：定位子节点（消费 A 的 padding + B 的 mainStart/crossAvail），
 *       含 STRETCH 改写与几何闸门；</li>
 *   <li>步骤 D：写容器自身 LayoutBox + 产出 self bubble 信号。</li>
 * </ul>
 *
 * <p><b>纯函数型消费者，不持状态</b>：所有输入经方法入参传入（node + constraints +
 * outerWidth + rootFinalHeight），构造器无依赖。主引擎 relayoutSelfIfNeeded 编排为
 * {@code outer = sizing.computeWidth(); h = sizing.computeHeight();
 * signal = flex.positionChildren(node, c, outer, h)}——把原 performLayout 内
 * 「步骤 A 锁定 → 步骤 D 复用」的隐式时序，显式化为「主引擎先算尺寸再传给 FlexLayouter」
 * 的参数依赖。</p>
 *
 * <h3>跨类契约 1（computeWidth 内宽基准权威）——三向锚定</h3>
 * <p>本类 {@link #positionChildren} 步骤 1 用入参 {@code outerWidth} 算
 * {@code innerWidth = outerWidth - padH}，<b>不再调 sizing.computeWidth</b>（outerWidth
 * 已由主引擎调 {@code sizing.computeWidth(node, c, true)} 算好传入）。该 outerWidth 与
 * {@link ConstraintResolver#buildChildConstraints} 内 {@code sizing.computeWidth(node, c, false)}
 * 同源同一 SizingCalculator 实例，确保 computeWidth 的盒宽基准在「buildChildConstraints」
 * 与「positionChildren 步骤 1」两处一致。三向锚定：
 * SizingCalculator.computeWidth Javadoc ↔ ConstraintResolver.buildChildConstraints Javadoc
 * ↔ FlexLayouter.positionChildren Javadoc。</p>
 *
 * <h3>斩断自反馈类型约束（oracle 裁决的类型约束升级）</h3>
 * <p>rootFinalHeight 作为 {@link #positionChildren} 入参传入，FlexLayouter <b>物理上拿不到
 * 「重算高度」的能力</b>（不持 SizingCalculator 引用，无 computeHeight 调用路径）。步骤 C
 * STRETCH 改子高发生在 rootFinalHeight 锁定之后，步骤 D 复用此入参值不回算，天然斩断
 * 自反馈放大。原 performLayout 内「步骤 A 锁定 rootFinalHeight、步骤 C STRETCH 改子高
 * 不回灌」的注释约束，由此升格为类型约束——rootFinalHeight 是入参，无法被重算。</p>
 *
 * <h3>D1 命门</h3>
 * <p>本类产出的 {@link SelfBubbleSignal} 经主引擎 layoutInternal 主体读，主引擎在子循环
 * join 点补 bubble。本机制不动。</p>
 */
class FlexLayouter {

    /**
     * 无依赖构造器：FlexLayouter 是纯函数型消费者，所有输入经方法入参传入，
     * 不持 SizingCalculator / ConstraintResolver / measurer 等任何引用。
     */
    FlexLayouter() {
        // 无依赖，纯函数型消费者
    }

    /**
     * positionChildren 回传的本节点自身 bubble 信号。
     *
     * <ul>
     *   <li>{@code geometry}：本节点自身几何变化需补 descendantGeometryDirty 冒泡。</li>
     *   <li>{@code paint}：本节点自身尺寸变化需补 descendantPaintDirty 冒泡（paint 无短路恒补）。</li>
     * </ul>
     *
     * <p>阶段 4.3 从 SceneLayoutEngine 搬迁至 FlexLayouter（生产者持有最自然），
     * 主引擎作为消费者引用 {@code FlexLayouter.SelfBubbleSignal}。包级嵌套 record，
     * 仅同包可见。</p>
     */
    @Desugar
    record SelfBubbleSignal(boolean geometry, boolean paint) {}

    /**
     * 消费 SizingCalculator 算好的 outerWidth / rootFinalHeight（步骤 A 的锁定值传入），
     * 执行 positionChildren 步骤 B/C/D：主轴汇总 + 起点 + 交叉轴可用 + 定位子 + STRETCH 改写 +
     * 写自身 LayoutBox + self bubble 信号产出。
     *
     * <p>按 {@code flexDirection} 划分主轴/交叉轴，应用 padding / gap / 主轴对齐 /
     * 交叉轴对齐，为子节点设置局部坐标，并写本节点自身 LayoutBox。</p>
     *
     * <p><b>STRETCH preferred 豁免</b>：cross 对齐为 STRETCH（默认）时，若子节点在
     * cross 维度设置了显式 preferred 尺寸（row 看 preferredHeight、column 看
     * preferredWidth），则保持其内在 cross 尺寸不被拉满；否则照旧填满 crossAvail。</p>
     *
     * <h3>跨类契约 1 反向锚定（computeWidth 内宽基准权威）</h3>
     * <p>步骤 1 用入参 {@code outerWidth} 算 {@code innerWidth = outerWidth - padH}。
     * outerWidth 来源 = 主引擎调 {@code sizing.computeWidth(node, constraints, true)}，
     * 与 {@link ConstraintResolver#buildChildConstraints} 内
     * {@code sizing.computeWidth(node, c, false)} 同源同一 SizingCalculator 实例。
     * 三向锚定详见类级 Javadoc。本方法<b>不再调 sizing.computeWidth</b>。</p>
     *
     * <h3>斩断自反馈类型约束（oracle 裁决的类型约束升级）</h3>
     * <p>rootFinalHeight 是入参，FlexLayouter 物理上拿不到「重算高度」的能力
     * （不持 SizingCalculator 引用）。步骤 C STRETCH 改子高发生在锁定之后，步骤 D 复用
     * 此入参值不回算，天然斩断自反馈放大——从注释约束升格为类型约束。</p>
     *
     * <h3>I7 铁律</h3>
     * <p>仍走 {@code newBox.equals(childBox)} 几何闸门 + {@code markGeometryDirty}：
     * 仅在 LayoutBox 值确实变化时才替换缓存并标记 geometry 脏。<b>绝不调用任何子节点的
     * {@code markSelfLayout}，绝不向下递归触碰后代。</b>padding/gap 等容器属性变化
     * 通过本节点 selfLayoutDirty 触发重定位，干净子节点的 LayoutBox 若值不变则引用复用。</p>
     *
     * @param node            要计算布局的节点
     * @param constraints     当前节点的布局约束
     * @param outerWidth      步骤 A 锁定的容器外宽（主引擎调 sizing.computeWidth(node, c, true) 算出）
     * @param rootFinalHeight 步骤 A 锁定的容器最终高度（主引擎在 layoutChildren 后序递归完成后
     *                        调 sizing.computeHeight(node, c) 算出——基于子节点原始 cachedLayout.height，
     *                        步骤 C STRETCH 改子高不回灌此值）
     * @return 本节点自身 bubble 信号（geometry / paint），供父 join 点补 descendant 路标
     */
    SelfBubbleSignal positionChildren(SceneNode node, Constraints constraints,
                                      int outerWidth, int rootFinalHeight) {
        // ===== 步骤 A：容器自身盒尺寸（纯读，用子原始 height） =====
        // outerWidth 取本节点「解析后的盒宽」而非裸约束宽：computeWidth 已让
        // preferredWidth 以最高优先级压过 fill/shrink，故有显式 preferredWidth 的容器
        // 会在自身盒宽（含 padding）内排布子节点，而非裸约束宽内。普通 fill/shrink 节点
        // computeWidth 仍返回约束宽/内在宽，与旧行为完全一致（零回归）。
        //
        // ★ 耦合不变式：此处 innerWidth 的盒宽基准，必须与 layoutInternal 给子节点的
        // childConstraints 用同一基准 computeWidth(node, constraints)，否则固定宽容器的
        // 「依赖约束宽」子节点会按裸约束宽布局而溢出父盒。两处务必同步修改。
        //
        // ★ 阶段 4.3：outerWidth / rootFinalHeight 改由主引擎调 sizing.computeWidth /
        //   sizing.computeHeight 算好后作为入参传入（斩断自反馈类型约束：rootFinalHeight
        //   是入参，FlexLayouter 物理上拿不到「重算高度」的能力）。步骤 C STRETCH 改子高
        //   发生在锁定之后，步骤 D 复用此入参值不回算，天然斩断自反馈放大。
        boolean row = node.getFlexDirection() == FlexDirection.ROW;
        int padTop = node.getPaddingTop();
        int padRight = node.getPaddingRight();
        int padBottom = node.getPaddingBottom();
        int padLeft = node.getPaddingLeft();
        int gap = node.getGap();
        int innerWidth = Math.max(0, outerWidth - padLeft - padRight);

        List<SceneNode> children = node.__getChildren();

        // ===== 步骤 B：可用空间（主轴汇总 + 主轴起点 + 交叉轴可用） =====
        // 汇总主轴总尺寸（crossMax 已是死变量：交叉轴基准改用 rootFinalHeight，
        // performLayout 局部无人再读 crossMax，故删除累加逻辑简化代码）。
        int mainContentSize = 0;
        int childCount = 0;
        for (SceneNode child : children) {
            LayoutBox cb = (LayoutBox) child.getCachedLayout();
            if (cb == null) {
                continue;
            }
            int childMain = row ? cb.getWidth() : cb.getHeight();
            mainContentSize += childMain;
            childCount++;
        }
        int totalGap = childCount > 1 ? gap * (childCount - 1) : 0;
        int mainContentWithGap = mainContentSize + totalGap;

        // 主轴可用空间与主轴起点偏移
        int mainAvail = row
                ? innerWidth
                : containerMainExtent(node, constraints, mainContentWithGap, padTop, padBottom);
        int mainStart;
        switch (node.getMainAxisAlign()) {
            case CENTER:
                mainStart = Math.max(0, (mainAvail - mainContentWithGap) / 2);
                break;
            case END:
                mainStart = Math.max(0, mainAvail - mainContentWithGap);
                break;
            case START:
            default:
                mainStart = 0;
                break;
        }

        // 交叉轴可用空间：ROW 用容器自身最终内高（rootFinalHeight - padV），
        // COLUMN 用 innerWidth。rootFinalHeight 与 padV 均为步骤 A/B 的定值，
        // 不随子循环变化，故在步骤 B 算一次即可，无需循环内每子重算。
        // ★ ROW 交叉轴基准：必须用容器自身最终内高（含 preferredHeight/fill 撑高），
        //   不能用子节点最大高。否则固定高 ROW 容器在 CENTER/END 对齐时，
        //   子节点会贴在 crossMax 顶部，多出空间全沉盒底，垂直居中失效。
        int crossAvail = row ? Math.max(0, rootFinalHeight - padTop - padBottom) : innerWidth;

        // ===== 步骤 C：定位子节点（消费 A 的 padding + B 的 mainStart/crossAvail） =====
        // 几何闸门 + markGeometryDirty，绝不向下递归标脏。
        int cursor = (row ? padLeft : padTop) + mainStart;
        for (SceneNode child : children) {
            LayoutBox cb = (LayoutBox) child.getCachedLayout();
            if (cb == null) {
                continue;
            }
            int childMain = row ? cb.getWidth() : cb.getHeight();
            int childCrossSize = row ? cb.getHeight() : cb.getWidth();

            int crossPos;
            int finalCrossSize = childCrossSize;
            switch (effectiveCrossAlign(node, child)) {
                case START:
                    crossPos = 0;
                    break;
                case CENTER:
                    crossPos = Math.max(0, (crossAvail - childCrossSize) / 2);
                    break;
                case END:
                    crossPos = Math.max(0, crossAvail - childCrossSize);
                    break;
                case STRETCH:
                default:
                    crossPos = 0;
                    // 子节点在 cross 维度有显式 preferred 尺寸时，豁免 STRETCH 改写
                    // （保其内在 cross 尺寸）：row 容器 cross=高→看 preferredHeight，
                    // column 容器 cross=宽→看 preferredWidth。
                    int childCrossPreferred = row ? child.getPreferredHeight() : child.getPreferredWidth();
                    // COLUMN+SHRINK 子节点保持自身内容宽，避免父 STRETCH 反向抹平 shrink 结果。
                    boolean shrinkWidthExempt = !row
                            && child.getWidthSizing() == SceneNode.WidthSizing.SHRINK;
                    // stretched：真正被 STRETCH 改写为拉满 crossAvail 的子（未被 preferred/SHRINK 豁免）。
                    // 用 boolean 精确区分「被 STRETCH 改写为拉满」与「豁免子内在尺寸恰好等于 crossAvail」，
                    // 避免豁免子（preferredWidth==crossAvail 且 maxWidth<preferredWidth）被误 clamp 到 maxWidth。
                    boolean stretched = (childCrossPreferred <= 0 && !shrinkWidthExempt);
                    finalCrossSize = stretched ? crossAvail : childCrossSize;
                    // 回填一期边界 2：COLUMN 容器（cross=宽）下，被 STRETCH 改写的子需尊重 maxWidth 上界，
                    // 不拉超过 maxWidth。ROW 容器（cross=高）下 maxWidth 不影响高，maxHeight 已在
                    // computeHeight 出口 clamp，不在此处理。preferred/SHRINK 豁免已在上方生效
                    // （stretched=false），maxWidth clamp 只对真正被 STRETCH 改写的子生效。
                    if (!row && stretched) {
                        int maxW = child.getMaxWidth();
                        if (maxW > 0 && finalCrossSize > maxW) {
                            finalCrossSize = maxW;
                        }
                    }
                    break;
            }

            int nx;
            int ny;
            int nw;
            int nh;
            if (row) {
                nx = cursor;
                ny = padTop + crossPos;
                nw = childMain;
                nh = finalCrossSize;
            } else {
                nx = padLeft + crossPos;
                ny = cursor;
                nw = finalCrossSize;
                nh = childMain;
            }

            LayoutBox newBox = new LayoutBox(nx, ny, nw, nh);
            // 仅在位置或尺寸确实变化时才替换，保持缓存引用稳定（I7 几何闸门）
            if (!newBox.equals(cb)) {
                child.setCachedLayout(newBox);
                // 位置/尺寸变化 → geometry 级标记，让 paint 遍历感知 offset 需更新
                child.markGeometryDirty();
                // 尺寸变化时 paint fragment 已编码旧 width/height，必须同步失效
                if (cb == null || newBox.getWidth() != cb.getWidth() || newBox.getHeight() != cb.getHeight()) {
                    child.markSelfPaint();
                }
            }
            cursor += childMain + gap;
        }

        // ===== 步骤 D：写容器自身 LayoutBox（直接用步骤 A 的结果） =====
        // 宽度复用步骤 A 的 outerWidth（避免对文本叶重复测量）。
        // 高度复用步骤 A 锁定的 rootFinalHeight：步骤 A 已锁定，步骤 C STRETCH 改子高不回灌，
        // 天然斩断自反馈放大，是正确性要求而非单纯清晰度优化。
        // ★ 阶段 4.3：outerWidth / rootFinalHeight 均为入参（主引擎步骤 A 算好传入），
        //   FlexLayouter 物理上无法重算，斩断自反馈从注释约束升格为类型约束。
        //
        // ★ 阶段 2.2 并行前置：标本节点改为「只置 self 位不 bubble」，
        //   bubble 延迟到父 join 点串行补（见 layoutInternal 子循环）。
        //   单线程下与原 markGeometryDirty/markSelfPaint 逐位等价：
        //   - geometry：__setSelfGeometryDirtyNoBubble 保留短路语义（已脏返回 false，父不补 bubble）
        //   - paint：__setSelfPaintDirtyNoBubble 保留无短路语义（每次清 cachedPaint、恒返回 true）
        boolean selfGeoBubble = false;
        boolean selfPaintBubble = false;
        int width = outerWidth;
        int height = rootFinalHeight;
        LayoutBox newSelfBox = new LayoutBox(0, 0, width, height);
        LayoutBox oldSelfBox = (LayoutBox) node.getCachedLayout();
        if (!newSelfBox.equals(oldSelfBox)) {
            node.setCachedLayout(newSelfBox);
            // 自身位置/尺寸变化 → geometry 级标记（置 self 位不 bubble，父 join 点补）
            selfGeoBubble = node.__setSelfGeometryDirtyNoBubble();
            // 尺寸变化时 paint fragment 已编码旧 width/height，必须同步失效
            if (oldSelfBox == null || newSelfBox.getWidth() != oldSelfBox.getWidth()
                    || newSelfBox.getHeight() != oldSelfBox.getHeight()) {
                // paint 无短路恒补：置 selfPaintDirty + 清 cachedPaint，返回恒 true
                node.__setSelfPaintDirtyNoBubble();
                selfPaintBubble = true;
            }
        }
        return new SelfBubbleSignal(selfGeoBubble, selfPaintBubble);
    }

    /**
     * 取子节点有效交叉轴对齐：{@link AlignSelf} 非 AUTO 时覆盖父级
     * {@link SceneNode#getCrossAxisAlign()}，否则继承父级。
     *
     * <p>二期 align-self 核心解析点：把「父级 crossAxisAlign」与「子级 alignSelf 覆盖」
     * 的回退逻辑集中在此处，positionChildren 步骤 C 的交叉轴 switch 统一消费本方法
     * 的返回值，不直接读父级 crossAxisAlign。AUTO 回退父级保证零回归。</p>
     *
     * <h3>I7 不变量</h3>
     * <p>纯交叉轴定位读取，不改 buildChildConstraints 下传约束，
     * childConstraintsWouldChange 不受影响。</p>
     *
     * @param parent 父容器节点
     * @param child  当前子节点
     * @return 子节点有效交叉轴对齐（AUTO 回退父级 crossAxisAlign）
     */
    private static CrossAxisAlign effectiveCrossAlign(SceneNode parent, SceneNode child) {
        AlignSelf self = child.getAlignSelf();
        if (self == AlignSelf.AUTO) return parent.getCrossAxisAlign();
        return switch (self) {
            case START -> CrossAxisAlign.START;
            case CENTER -> CrossAxisAlign.CENTER;
            case END -> CrossAxisAlign.END;
            case STRETCH -> CrossAxisAlign.STRETCH;
            default -> parent.getCrossAxisAlign();
        };
    }

    /**
     * 计算 COLUMN 容器主轴（高度）方向上的可用空间。
     *
     * <p>主轴可用空间 = 容器最终高度减上下 padding。为避免与 {@link SizingCalculator#computeHeight}
     * 形成循环依赖，采用最简解：</p>
     * <ul>
     *   <li>shrink-to-fit（非 fill 或无高度约束）时 extent==contentExtent,
     *       mainAvail==mainContentWithGap，CENTER/END 的 offset 算出 0 → 退化为 START，
     *       零行为漂移。</li>
     *   <li>只有 fill 容器有高度盈余时 CENTER/END 才产生可见偏移。</li>
     * </ul>
     *
     * @param node               容器节点
     * @param constraints        容器布局约束
     * @param mainContentWithGap 子节点主轴总尺寸（含 gap）
     * @param padStart           主轴起点 padding（COLUMN 为上）
     * @param padEnd             主轴终点 padding（COLUMN 为下）
     * @return 主轴可用空间（像素）
     */
    private int containerMainExtent(SceneNode node, Constraints constraints,
                                    int mainContentWithGap, int padStart, int padEnd) {
        int contentExtent = mainContentWithGap + padStart + padEnd;
        int extent = contentExtent;
        if (node.isFillParentHeight() && constraints.hasHeightConstraint()) {
            extent = Math.max(contentExtent, constraints.getAvailableHeight());
        }
        return Math.max(0, extent - padStart - padEnd);
    }
}
