package club.heiqi.uilib.ui.scene.layout;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import com.github.bsideup.jabel.Desugar;

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
 * <h3>真实字体度量（解除偏离 1）</h3>
 * <p>叶节点宽度由 {@link SceneTextMeasurer#measureWidth} 提供 shrink-to-fit 语义，
 * 行高由 {@link SceneTextMeasurer#lineHeight} 驱动。叶节点主轴宽不再被 cross-align 改写，
 * 使 ROW+CENTER 主轴偏移恢复非 0（叶节点内在宽 &lt; 可用宽时居中可见）。</p>
 *
 * <h3>I10 接缝纯净</h3>
 * <p>引擎只认 scene 端口 {@link SceneTextMeasurer}，绝不 import 任何平台类、
 * 渲染上下文或 {@code ui.text.*} 度量实现。真实度量由装配层 adapter 委托完成（I6）。</p>
 *
 * <h3>epoch 失效链（I7 铁律：只向上冒泡）</h3>
 * <p>字体运行时 epoch 变化时，遍历上一帧测量过的文本叶节点，对每个
 * {@code node.__getLastMeasuredEpoch() != currentEpoch} 的节点 {@code markSelfLayout()}
 * （只向上冒泡，O(文本节点数)，<b>绝不向下递归标脏</b>）。</p>
 *
 * <p><b>阶段 1.5：epoch 比对状态外置。</b>引擎不再持 {@code lastMeasureEpoch}，
 * epoch 比对权威下放到每个文本叶节点的 {@code lastMeasuredEpoch}（与 {@code lastConstraints}
 * 同构）。但失效触发仍在 layout 入口遍历前冒泡，与约束变化的「纯局部比对」机制有意不同，
 * 原因是 epoch 无自上而下下传载体——若删掉入口冒泡只靠遍历时自查，干净子树（双 false）
 * 会在 layoutInternal 入口被整棵跳过，永远到不了文本叶的自查点，导致字体 reload 后
 * 干净子树文本不更新（P0 命门）。入口冒泡点亮 descendantLayoutDirty，使干净中间层
 * 下沉到文本叶自查点。</p>
 *
 * <p><b>measuredTextNodes 持续累积，不在入口清空。</b>与原机制语义等价（原机制只在
 * epoch 变化帧清空+重填，平时保留累积清单）。若每帧清空，会因 I7 干净跳过导致干净
 * 文本叶不走 computeWidth、不重填，下一帧 measuredTextNodes 丢失这些节点，失效链断裂。</p>
 */
public class SceneLayoutEngine {

    /**
     * 构造注入：文本度量服务（scene 核心只认窄端口，不 import ui.text.*）。
     *
     * <p>阶段 4.1 后，尺寸计算逻辑（computeWidth/computeHeight/viewportHeight 等）已搬迁至
     * {@link SizingCalculator}，本字段仅保留给 layout() 入口的 epoch 失效链
     * （{@code measurer.epoch()}）与 {@code priorKnownChildHeight} 的
     * {@code measurer.lineHeight} 使用，其余尺寸计算一律走 {@link #sizing}。</p>
     */
    private final SceneTextMeasurer measurer;

    /**
     * 尺寸计算器（阶段 4.1 拆出）：computeWidth/computeHeight/viewportHeight/
     * isHeightConsumingConstraint/countLines 等纯读函数集的协作者。
     *
     * <p>跨类契约：本字段持有的 SizingCalculator 实例是 ConstraintResolver（4.2 拆出）
     * 与 FlexLayouter（4.3 拆出）计算内宽基准时的同一权威实例——三者必须共享同一
     * SizingCalculator，确保 computeWidth 的盒宽基准在 buildChildConstraints 与
     * performLayout 两处一致（见 SizingCalculator.computeWidth Javadoc 跨类契约 1）。</p>
     */
    private final SizingCalculator sizing;

    /**
     * 本帧测量过文本的叶节点集合（{@code ConcurrentHashMap.newKeySet()}，按引用相等去重）。
     *
     * <p>阶段 1.5 后职责为「曾测量过的文本叶清单」累积遍历器：epoch 比对状态已下放
     * 到每个文本叶节点的 {@link SceneNode#__getLastMeasuredEpoch()}，引擎不再持有
     * {@code lastMeasureEpoch}。每帧 layout 入口遍历此集合做节点级 epoch 比对，
     * 比对不成立的节点 {@code markSelfLayout()}（只向上冒泡，O(文本节点数)，
     * <b>绝不向下递归</b>）。</p>
     *
     * <p><b>持续累积，不在入口清空</b>：与原机制语义等价（原机制只在 epoch 变化帧
     * 清空+重填）。若每帧清空，I7 干净跳过会导致干净文本叶不走 computeWidth、不重填，
     * 下一帧 measuredTextNodes 丢失这些节点，失效链断裂。文本叶测量时幂等 add，
     * detached 节点累积无害（冒泡到 null parent 无害）。</p>
     *
     * <p><b>阶段 2 并行前置（线程安全）</b>：换用 {@code ConcurrentHashMap.newKeySet()}
     * 取代 {@code Collections.newSetFromMap(new IdentityHashMap<>())}，使 add 与遍历
     * 在多线程并发下安全。去重语义等价前提：{@link SceneNode} 不重写 equals/hashCode
     * （默认 Object.equals = 引用相等），故 ConcurrentHashMap 的 equals/hashCode 桶定位
     * 与 IdentityHashMap 的 == 定位结果一致。一旦 SceneNode 重写 equals，去重语义会从
     * identity 漂移到值相等，失效链会丢节点——此约束已在 SceneNode 类注释锚定。</p>
     *
     * <p><b>阶段 4.1：注入 SizingCalculator</b>。本 Set 仍由主引擎拥有（layout 入口
     * 遍历它做 epoch 比对），但 {@link SizingCalculator#computeWidth} 内的 add 登记
     * 动作通过构造器注入的同一引用完成，语义与原主引擎内联时逐位等价（I7/I8）。</p>
     */
    private final Set<SceneNode> measuredTextNodes = ConcurrentHashMap.newKeySet();

    // ==================== 阶段 2.2 并行前置：信号 record ====================
    //
    // 以下两个 record 是 deepwork 并发框架阶段 2 第一批步骤 2.2 的产物：把
    // performLayout / layoutInternal 原本「即时冒泡」的几何/paint 信号拆成
    // 「worker 内只置 self 位 + 回传信号 → 父 join 点串行补 bubble」两段，
    // 从根上消除多 worker 并发冒泡写共享祖先 boolean 的竞态（D1 命门）。
    //
    // 本步为纯重构：单线程行为逐位不变，不引入任何线程/线程池。

    /**
     * 子树布局后向父回传的几何传播信号（join 点点亮 descendant 路标的依据）。
     *
     * <ul>
     *   <li>{@code needRelayout}：以本节点为根的子树几何是否变化（对应原 layoutInternal
     *       的 boolean 返回值，驱动父的 anyChildGeometryChanged）。</li>
     *   <li>{@code selfGeometryBubble}：本节点自身是否需要父在 join 点补发
     *       descendantGeometryDirty 冒泡（即 __setSelfGeometryDirtyNoBubble 的返回值）。</li>
     *   <li>{@code selfPaintBubble}：本节点自身是否需要父在 join 点补发
     *       descendantPaintDirty 冒泡（即 __setSelfPaintDirtyNoBubble 的返回值，恒 true）。</li>
     * </ul>
     *
     * <p>注意：本类与同包公开类 {@link LayoutResult} 同名，故内部 record 命名为
     * {@code SubtreeLayoutResult} 以避冲突，语义与 Oracle 裁决方案一致。</p>
     */
    @Desugar
    private record SubtreeLayoutResult(boolean needRelayout,
                                       boolean selfGeometryBubble,
                                       boolean selfPaintBubble) {
        /** 整棵子树干净未变：跳过分支回传。 */
        static final SubtreeLayoutResult CLEAN = new SubtreeLayoutResult(false, false, false);
    }

    /**
     * performLayout 回传的本节点自身 bubble 信号。
     *
     * <ul>
     *   <li>{@code geometry}：本节点自身几何变化需补 descendantGeometryDirty 冒泡。</li>
     *   <li>{@code paint}：本节点自身尺寸变化需补 descendantPaintDirty 冒泡（paint 无短路恒补）。</li>
     * </ul>
     */
@Desugar
    private record SelfBubbleSignal(boolean geometry, boolean paint) {}

    /**
     * I7 跳过判定结果载体。
     *
     * <ul>
     *   <li>{@code canSkip}：三道闸门合取（cleanSelf && !childConstraintsWouldChange
     *       && !selfConsumesConstraint），true 表示整棵子树可安全跳过。</li>
     *   <li>{@code selfConsumesConstraint}：闸门 3 的原值。调用方必须经本字段复用，
     *       不得在重算判定处重算（oracle 阶段 2 关键陷阱：只算一次）。</li>
     * </ul>
     */
    @Desugar
    private record SkipDecision(boolean canSkip, boolean selfConsumesConstraint) {}

    // ==================== 构造器 ====================

    /**
     * 使用指定文本度量服务创建布局引擎。
     *
     * @param measurer 文本度量服务（非 null）
     */
    public SceneLayoutEngine(SceneTextMeasurer measurer) {
        if (measurer == null) {
            throw new IllegalArgumentException("SceneTextMeasurer 不可为 null");
        }
        this.measurer = measurer;
        // 阶段 4.1：尺寸计算协作者，注入同一 measurer 与 measuredTextNodes 引用。
        // measuredTextNodes 在下方字段初始化后已就绪（字段初始化先于构造器体执行）。
        this.sizing = new SizingCalculator(measurer, measuredTextNodes);
    }

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
     * @return layout 产出的不可变结果，携带 I7/I8 测试探针
     */
    public LayoutResult layout(SceneNode root, Constraints rootConstraints) {
        // 局部累加器（per-call 探针，替代实例字段）
        int[] relayoutCount = {0};
        Set<SceneNode> relayoutedNodes = new HashSet<>();
        Set<SceneNode> constraintRelayoutedNodes = new java.util.LinkedHashSet<>();

        // epoch 失效链：遍历上一帧测量过的文本叶，做节点级 epoch 比对。
        // 比对不成立的节点 markSelfLayout()（只向上冒泡，O(文本节点数)，严禁向下递归 I7）。
        // detached 节点冒泡到 null parent 无害。
        //
        // ★ P0 命门：此入口遍历前冒泡不可删除。若删掉只靠遍历时自查，干净子树
        //   （selfLayoutDirty==false && descendantLayoutDirty==false）会在 layoutInternal
        //   入口被整棵跳过，永远到不了文本叶的自查点，导致字体 reload 后干净子树文本不更新。
        //   入口冒泡点亮 descendantLayoutDirty，使干净中间层下沉到文本叶自查点。
        //
        // epoch 未变时所有节点比对成立（lastMeasuredEpoch == epoch），零标脏，无性能损失。
        // 不再持 lastMeasureEpoch，epoch 比对权威下放到节点（与 lastConstraints 同构）。
        //
        // ★ 不清空 measuredTextNodes：与原机制语义等价。原机制只在 epoch 变化帧清空+重填
        //   （if 保护），平时保留累积清单。新机制若每帧清空，会因 I7 干净跳过导致干净文本叶
        //   不走 computeWidth、不重填，下一帧 measuredTextNodes 丢失这些节点，失效链断裂。
        //   故 measuredTextNodes 持续累积所有曾被测量的文本叶，文本叶测量时幂等 add。
        //   detached 节点累积无害（冒泡到 null parent 无害，ConcurrentHashMap.newKeySet() 不会无限增长）。
        int epoch = measurer.epoch();
        for (SceneNode textNode : measuredTextNodes) {
            if (textNode.__getLastMeasuredEpoch() != epoch) {
                textNode.markSelfLayout();
            }
        }

        // 约束变化感知：约束变化时只标 root 自己 selfLayoutDirty，
        // 绝不触碰任何后代节点（后代脏标记由各自 setter 自行维护）
        if (!Objects.equals(rootConstraints, lastRootConstraints)) {
            root.markSelfLayout();
        }
        lastRootConstraints = rootConstraints;

        // 串行路径：直接调 layoutInternal（2.2 形态，无 fork-join）
        layoutInternal(root, rootConstraints, relayoutCount, relayoutedNodes, constraintRelayoutedNodes);

        LayoutResult result = new LayoutResult(relayoutCount[0], relayoutedNodes, constraintRelayoutedNodes);
        return result;
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
     * @param node                     当前节点
     * @param constraints              父容器传给当前节点的布局约束
     * @param relayoutCount            重算次数累加器（int[1]，per-call 探针）
     * @param relayoutedNodes          因 selfLayoutDirty 被重算的节点集合累加器（per-call 探针）
     * @param constraintRelayoutedNodes 因约束变化被迫重算的节点集合累加器（per-call 探针）
     * @return 本子树布局结果（needRelayout + self bubble 信号）
     */
    private SubtreeLayoutResult layoutInternal(SceneNode node, Constraints constraints,
                                               int[] relayoutCount, Set<SceneNode> relayoutedNodes,
                                               Set<SceneNode> constraintRelayoutedNodes) {
        // ==== I7 核心判定：缓存有效 + 双 false → 整棵跳过，几何未变 ====
        // 在原「缓存有效 + 双 false」基础上，叠加两道与约束相关的放行条件：
        //   1. childConstraintsWouldChange：约束变化是否会改变下传给子的约束
        //      （决定是否值得为后代下沉递归，约束未变/无子 → false，99% 干净帧短路）；
        //   2. selfConsumesConstraint：本节点自身高度直接吃约束高、且约束变了
        //      → 必须重算自己（fill 节点与 scrollable 回退 cap 节点感知父高变化的关键）。
        // 任一为 true → 不跳过；均为 false → 整棵安全跳过（仍刷新约束快照）。
        // 三道闸门由 canSkipClean 统一计算；selfConsumesConstraint 经 SkipDecision
        // 载体回传，主流程复用同一值做重算判定，绝不重算（oracle 阶段 2 关键陷阱）。
        SkipDecision skip = canSkipClean(node, constraints);
        if (skip.canSkip()) {
            // 干净 + 约束对本节点与子均无影响 → 整棵跳过（仅刷新约束快照）
            node.__setLastConstraints(constraints);
            return SubtreeLayoutResult.CLEAN;
        }

        // ==== 后序遍历：先递归子节点，收集几何变化信号 ====
        // 按 flexDirection + padding 扣减内容宽：COLUMN/ROW 子节点都拿父内容宽作可用宽约束。
        // ROW 不做 grow 比例分配（YAGNI）。
        //
        // ★ 耦合不变式：layoutChildren 内 childConstraints 的内宽基准，必须与 performLayout 步骤1
        // 的 innerWidth 用同一盒宽基准 computeWidth(node, constraints)（含 preferredWidth 解析），
        // 否则有 preferredWidth 的固定宽容器，其「依赖约束宽」的子节点会按裸约束宽布局而溢出父盒。
        //
        // ==== 阶段 2.2 串行单循环 + join 点补 bubble ====
        // 单线程递归：每个 child 调 layoutInternal，回传 SubtreeLayoutResult，
        // 父（本节点）在循环内串行补 bubble（消除多 worker 并发冒泡写共享祖先
        // boolean 的竞态，D1 命门）。探针在 layoutInternal 内直接写入共享累加器。
        boolean anyChildGeometryChanged = layoutChildren(node, constraints,
                relayoutCount, relayoutedNodes, constraintRelayoutedNodes);

        // ==== 判定是否需要重算本节点 ====
        // 需要重算条件：自身脏 / 无缓存 / 子节点几何变化导致需重新定位 / 约束逼自身重算高度
        boolean selfDirty = node.__isSelfLayoutDirty() || node.getCachedLayout() == null;
        boolean constraintForcesSelf = skip.selfConsumesConstraint();   // 约束变化逼自身重算高度（复用 canSkipClean 计算结果，不重算）
        boolean needRelayout = selfDirty || anyChildGeometryChanged || constraintForcesSelf;

        // 本节点自身 bubble 信号（performLayout 步骤 D 收集，join 点由父补 bubble）
        boolean selfGeoBubble = false;
        boolean selfPaintBubble = false;

        if (needRelayout) {
            // 仅在"节点自身内容变化"时计入重算统计（I7 语义）
            // 因兄弟几何变化导致的"位置顺移"不算入重算计数
            if (selfDirty) {                       // 计数口径维持只认 selfDirty，零回归现存测试
                relayoutCount[0]++;
                relayoutedNodes.add(node);
            }
            if (constraintForcesSelf && !selfDirty) {   // 因约束被迫重算，进独立探针集合
                constraintRelayoutedNodes.add(node);
            }
            SelfBubbleSignal sb = performLayout(node, constraints);
            selfGeoBubble = sb.geometry();
            selfPaintBubble = sb.paint();
        }

        // ==== 清除本节点布局脏标记 ====
        // 使用 SceneNode.clearLayoutDirty() 只清 layout 两个标记，
        // 不误清 paint/composite 标记
        node.clearLayoutDirty();
        // 刷新约束快照：作为下一帧「约束变更」判定的订阅缓存（绝不参与脏标记冒泡）
        node.__setLastConstraints(constraints);

        // ==== 阶段 2 铺路：后序顺带重算子树节点数缓存 ====
        // 后序遍历至此，所有子节点 count 已是最新（子节点已先于本节点完成重算）。
        // 仅 subtreeCountDirty==true 时重算（O(children) 加法，复用已访问子列表），
        // 干净帧（subtreeCountDirty==false）直接 return，零开销（守 I7）。
        // 不变量：结构变化入口同时调 markSelfLayout() + markSubtreeCountDirty()，
        // 故 subtreeCountDirty==true 时 selfLayoutDirty 必曾为 true，本节点不会走
        // 上方「整棵跳过」分支，必然到达此处 → count 重算时机有保证。
        node.__recomputeSubtreeCountIfDirty();

        return new SubtreeLayoutResult(needRelayout, selfGeoBubble, selfPaintBubble);
    }

    /**
     * I7 跳过判定：计算"干净子树整棵跳过"的三道闸门，并携带 selfConsumesConstraint 回传。
     *
     * <h3>三道闸门（全 true 才跳过）</h3>
     * <ol>
     *   <li>{@code cleanSelf}：本节点缓存有效（cachedLayout 非空）且双 false
     *       （selfLayoutDirty / descendantLayoutDirty 均为 false）。</li>
     *   <li>{@code !childConstraintsWouldChange}：约束变化不会改变下传给子的约束
     *       （决定是否值得为后代下沉递归，约束未变/无子 → false，99% 干净帧短路）。</li>
     *   <li>{@code !selfConsumesConstraint}：本节点自身高度不直接吃约束高、或约束未变。
     *       叶节点额外补宽度消费判定（避免依赖父宽的叶节点复用陈旧 LayoutBox）；
     *       容器只保留高度消费判定（宽度维度由 childConstraintsWouldChange 下沉）。</li>
     * </ol>
     * 任一为 true → 不跳过；均为 false → 整棵安全跳过（仍刷新约束快照）。
     *
     * <h3>返回值载体</h3>
     * <p>返回 {@link SkipDecision}，携带 {@code canSkip}（三道闸门合取）与
     * {@code selfConsumesConstraint}（闸门 3 的原值）。</p>
     *
     * <h3>调用方义务（★关键陷阱）</h3>
     * <p>{@code selfConsumesConstraint} 在本方法内计算一次，调用方必须经
     * {@code SkipDecision.selfConsumesConstraint()} 复用，<b>不得在重算判定处重算</b>。
     * 原因：跳过判定与重算判定都依赖该值，主流程若在重算判定处再算一遍会导致双算，
     * 破坏"只算一次"约束（oracle 阶段 2 标注）。</p>
     *
     * @param node        当前节点
     * @param constraints 父容器传给当前节点的布局约束
     * @return 跳过判定结果 + selfConsumesConstraint 值
     */
    private SkipDecision canSkipClean(SceneNode node, Constraints constraints) {
        Constraints prev = node.__getLastConstraints();
        boolean cleanSelf = node.getCachedLayout() != null
                && !node.__isSelfLayoutDirty()
                && !node.__isDescendantLayoutDirty();
        List<SceneNode> kids = node.__getChildren();
        boolean constraintsChanged = !Objects.equals(constraints, prev);
        boolean selfConsumesConstraint;
        if (kids.isEmpty()) {
            // 叶节点无子约束下沉兜底：补宽度消费判定，避免依赖父宽的叶节点复用陈旧 LayoutBox。
            boolean widthChanged = prev == null
                    || constraints.getAvailableWidth() != prev.getAvailableWidth();
            boolean leafConsumesWidth = node.getPreferredWidth() <= 0;
            boolean selfConsumesWidth = constraintsChanged && widthChanged && leafConsumesWidth;
            boolean selfConsumesHeight = constraintsChanged
                    && sizing.isHeightConsumingConstraint(node)
                    && (constraints.hasHeightConstraint()
                    || (prev != null && prev.hasHeightConstraint()));
            selfConsumesConstraint = selfConsumesWidth || selfConsumesHeight;
        } else {
            // 容器宽度维度仍由 childConstraintsWouldChange 下沉；这里只保留原高度消费判定。
            selfConsumesConstraint = constraintsChanged
                    && sizing.isHeightConsumingConstraint(node)
                    && (constraints.hasHeightConstraint()
                    || (prev != null && prev.hasHeightConstraint()));
        }

        boolean canSkip = cleanSelf
                && !childConstraintsWouldChange(node, constraints, prev)
                && !selfConsumesConstraint;
        return new SkipDecision(canSkip, selfConsumesConstraint);
    }

    /**
     * 后序递归子节点 + join 点补 bubble。
     *
     * <h3>后序递归语义</h3>
     * <p>对每个 child：先用 {@link #buildChildConstraints} 构造下传约束 → 递归
     * {@link #layoutInternal} → 收集 {@code cr.needRelayout} 汇总为
     * {@code anyChildGeometryChanged}。子节点先于本节点完成布局，保证本节点
     * performLayout 步骤可读到子的最新 LayoutBox。</p>
     *
     * <h3>join 点补 bubble（D1 命门消除机制）</h3>
     * <p>worker 内不即时冒泡（避免多 worker 并发冒泡写共享祖先 boolean 的竞态），
     * 改由父（本节点）在串行循环内收到子的 {@link SubtreeLayoutResult} 后，
     * 调 {@code child.__bubbleDescendantGeometryFromSelf()} /
     * {@code child.__bubbleDescendantPaintFromSelf()} 补点亮"子自身"的 descendant 路标。
     * 单线程下与原即时冒泡逐位等价：bubble 仍发生，只是时机从"子内"移到"父 join"。</p>
     *
     * <h3>与 buildChildConstraints 的耦合（★不变式）</h3>
     * <p>childConstraints 的内宽基准必须与 {@link #performLayout} 步骤 1 的 innerWidth
     * 同源——均基于 {@code computeWidth(node, constraints)}（含 preferredWidth 解析），
     * 否则有 preferredWidth 的固定宽容器，其「依赖约束宽」的子节点会按裸约束宽布局而溢出父盒。
     * 该不变式由 {@link #buildChildConstraints} 内部保证，本方法只负责调用。</p>
     *
     * <p>叶节点（无子）不进入循环，零额外测量开销；容器 computeWidth 走
     * preferredWidth/outerWidth 分支，亦不触发文本测量。</p>
     *
     * @param node                      当前节点（父）
     * @param constraints               父容器传给当前节点的布局约束
     * @param relayoutCount             重算次数累加器（int[1]，per-call 探针）
     * @param relayoutedNodes           因 selfLayoutDirty 被重算的节点集合累加器
     * @param constraintRelayoutedNodes 因约束变化被迫重算的节点集合累加器
     * @return 任一子节点几何是否变化（汇总 cr.needRelayout）
     */
    private boolean layoutChildren(SceneNode node, Constraints constraints,
                                   int[] relayoutCount, Set<SceneNode> relayoutedNodes,
                                   Set<SceneNode> constraintRelayoutedNodes) {
        List<SceneNode> children = node.__getChildren();
        if (children.isEmpty()) {
            return false;
        }
        boolean anyChildGeometryChanged = false;
        for (SceneNode child : children) {
            Constraints childConstraints = buildChildConstraints(node, constraints, child);
            SubtreeLayoutResult cr = layoutInternal(child, childConstraints,
                    relayoutCount, relayoutedNodes, constraintRelayoutedNodes);
            if (cr.needRelayout()) {
                anyChildGeometryChanged = true;
            }
            // ===== join 点：串行补点亮"子自身"的 descendant 路标 =====
            // bubble 延迟到父（本节点）串行补，消除并发冒泡写共享祖先 boolean 的竞态（D1 命门）。
            // 单线程下与原即时冒泡逐位等价：bubble 仍发生，只是时机从"子内"移到"父 join"。
            if (cr.selfGeometryBubble()) {
                child.__bubbleDescendantGeometryFromSelf();
            }
            if (cr.selfPaintBubble()) {
                child.__bubbleDescendantPaintFromSelf();
            }
        }
        return anyChildGeometryChanged;
    }

    /**
     * 构造下传给子节点的布局约束。
     *
     * <p>宽度口径与 {@link #performLayout} 步骤 1 的 innerWidth 同源
     * （均基于 {@code computeWidth(node, constraints)} 含 preferredWidth 解析），
     * 保证固定宽容器的「依赖约束宽」子节点不溢出父盒。</p>
     *
     * <p>高度下传口径：ROW 容器且本容器高度先验确定时下传交叉轴高；COLUMN 容器默认
     * {@link Constraints#UNCONSTRAINED}，仅在「唯一 fill 子 + 固定兄弟高度均可先验」时
     * 给该 fill 子下传剩余主轴高度。</p>
     *
     * @param node        容器节点
     * @param constraints 本节点收到的布局约束
     * @return 下传给子节点的约束
     */
    private Constraints buildChildConstraints(SceneNode node, Constraints constraints, SceneNode child) {
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
    private int priorKnownChildHeight(SceneNode child) {
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
     * @param node        容器节点
     * @param constraints 本节点收到的布局约束
     * @return 先验内容高（已扣上下 padding），无法先验确定时为 UNCONSTRAINED
     */
    private int priorKnownInnerHeight(SceneNode node, Constraints constraints) {
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
    private boolean childConstraintsWouldChange(SceneNode node, Constraints cur, Constraints prev) {
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

    /**
     * 执行单节点布局计算（flex 主轴/交叉轴定位）。
     *
     * <p>按 {@code flexDirection} 划分主轴/交叉轴，应用 padding / gap / 主轴对齐 /
     * 交叉轴对齐，为子节点设置局部坐标，并计算本节点自身尺寸。</p>
     *
     * <p><b>STRETCH preferred 豁免</b>：cross 对齐为 STRETCH（默认）时，若子节点在
     * cross 维度设置了显式 preferred 尺寸（row 看 preferredHeight、column 看
     * preferredWidth），则保持其内在 cross 尺寸不被拉满；否则照旧填满 crossAvail。</p>
     *
     * <h3>I7 铁律</h3>
     * <p>仍走 {@code newBox.equals(childBox)} 几何闸门 + {@code markGeometryDirty}：
     * 仅在 LayoutBox 值确实变化时才替换缓存并标记 geometry 脏。<b>绝不调用任何子节点的
     * {@code markSelfLayout}，绝不向下递归触碰后代。</b>padding/gap 等容器属性变化
     * 通过本节点 selfLayoutDirty 触发重定位，干净子节点的 LayoutBox 若值不变则引用复用。</p>
     *
     * @param node        要计算布局的节点
     * @param constraints 当前节点的布局约束
     * @return 本节点自身 bubble 信号（geometry / paint），供父 join 点补 descendant 路标
     */
    private SelfBubbleSignal performLayout(SceneNode node, Constraints constraints) {
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
        // rootFinalHeight 在此锁定（基于子节点原始 cachedLayout.height，后序递归已就绪）。
        // computeHeight 是纯读函数（只读子节点 cachedLayout + 节点属性 + 约束，
        // 不写状态、不读 root 自身 LayoutBox），提前调用安全。步骤 C 的 STRETCH 改写
        // 子高发生在锁定之后，步骤 D 复用此值不回算，天然斩断自反馈放大。
        int outerWidth = sizing.computeWidth(node, constraints, true);
        boolean row = node.getFlexDirection() == FlexDirection.ROW;
        int padTop = node.getPaddingTop();
        int padRight = node.getPaddingRight();
        int padBottom = node.getPaddingBottom();
        int padLeft = node.getPaddingLeft();
        int gap = node.getGap();
        int innerWidth = Math.max(0, outerWidth - padLeft - padRight);
        int rootFinalHeight = sizing.computeHeight(node, constraints);

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
            switch (node.getCrossAxisAlign()) {
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
                    finalCrossSize = (childCrossPreferred > 0 || shrinkWidthExempt)
                            ? childCrossSize
                            : crossAvail;
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
