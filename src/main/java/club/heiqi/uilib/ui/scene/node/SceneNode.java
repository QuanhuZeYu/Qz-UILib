package club.heiqi.uilib.ui.scene.node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.layout.AlignSelf;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;

/**
 * 场景树节点 —— 新 UI 数据模型的地基，承载"反转脏标记方向"的灵魂设计。
 *
 * <h3>核心原则：脏标记只向上冒泡（O(深度)），绝不向下递归（O(子树)）</h3>
 *
 * <p>旧 DOM 模型（{@code DocumentNode.markSubtreeLayoutMutation}）在容器增删时
 * 无条件向下递归刷新全部后代的布局版本号，导致未变的稳定子节点被布局层判定复用失败、
 * 全量重算——这是 I7 债的根源。本类的设计正面翻转此方向：</p>
 * <ul>
 *   <li>节点自身变化时，只标自己（{@code selfLayoutDirty / selfPaintDirty / compositeDirty}）
 *       并通过祖先链向上点亮路标（{@code descendantLayoutDirty / descendantPaintDirty}），
 *       告知布局/绘制/合成遍历"需要下沉到我这里"。</li>
 *   <li>绝对不触碰任何兄弟节点、任何后代节点。</li>
 *   <li>稳定复用的子节点（如列表 keyed diff 中未变的项）零标脏，保证 I7：
 *       干净子树在布局、绘制、合成三阶段都被跳过。</li>
 * </ul>
 *
 * <h3>属性 setter 的设计意图</h3>
 * <p>每个 setter（{@link #setText}, {@link #setBackgroundColor}, {@link #setOpacity},
 * {@link #setTransform}）内部自动打出对应失效级别，调用方无需手选级别，
 * 从而降低 I4"打错级别"的风险。setter 先去重（值与当前相等则直接 return），
 * 对齐 reactive 层"对已应用值去重"的铁律，避免无谓标脏。</p>
 *
 * <h3>批量结构变更（{@link #applyChildReconcile}）</h3>
 * <p>这是根除 I7 债的关键 API。reconciler 一次性提交最终子节点序列 + 其中新增/移动的项。
 * 容器自身因子序列变化标一次脏；稳定复用节点零标脏——正面翻转旧
 * {@code markSubtreeLayoutMutation} 的递归全标行为。</p>
 *
 * <h3>禁止重写 equals/hashCode（identity 语义锚定）</h3>
 * <p><b>禁止重写 equals/hashCode</b>：{@code SceneLayoutEngine.measuredTextNodes} 用
 * {@code ConcurrentHashMap.newKeySet()} 做引用去重，失效链依赖引用相等。一旦重写 equals，
 * 去重语义从 identity 漂移到值相等，失效链会丢节点。ConcurrentHashMap.newKeySet() 的桶定位
 * 走 equals/hashCode，SceneNode 默认 Object.equals = 引用相等，故与原 IdentityHashMap 行为等价；
 * 此等价前提即「SceneNode 永不重写 equals/hashCode」，任何子类亦不得重写。</p>
 */
public class SceneNode {

    /**
     * 容器宽度策略。
     *
     * <p>{@link #FILL} 保持默认填满父约束宽度；{@link #SHRINK} 让容器在未设置
     * preferredWidth 时按已布局子节点内容回收宽度。该策略仅影响有子节点的容器，
     * 叶节点仍沿用文本 shrink / 装饰 fill 的既有语义。</p>
     */
    public enum WidthSizing {
        /** 容器宽度填满父级下传的可用宽度。 */
        FILL,
        /** 容器宽度按子内容 shrink-to-fit，并被父级可用宽度 clamp。 */
        SHRINK
    }

    // ==================== 树关系 ====================

    /** 父节点，根节点为 null */
    SceneNode parent;

    /** 子节点列表，用 ArrayList 初始化 */
    List<SceneNode> children;

    // ==================== 脏标记 ====================

    /** 自身布局输入变化（尺寸/约束/文本/子节点集合变化） */
    boolean selfLayoutDirty;

    /**
     * 后代中存在布局脏节点，布局遍历需下沉到本节点。
     * 这是"路标"而非"脏标记"——本节点自己不一定要重算。
     */
    boolean descendantLayoutDirty;

    /** 自身绘制属性变化（颜色/背景/边框等） */
    boolean selfPaintDirty;

    /** 后代中存在绘制脏节点，绘制遍历需下沉的路标 */
    boolean descendantPaintDirty;

    /** 自身合成属性变化（transform/opacity） */
    boolean compositeDirty;

    /** 后代中存在合成脏节点，合成遍历需下沉的路标 */
    boolean descendantCompositeDirty;

    /**
     * 自身位置/尺寸变化（layout 引擎产出），paint 遍历需下沉更新 offset。
     * <p>这是独立的 COMPOSITE 子级别标记，语义单一：位置变，不重绘但需重定位。
     * 与 compositeDirty（transform/opacity）严格分离，保证 Phase 3 语义纯净。</p>
     */
    boolean selfGeometryDirty;

    /**
     * 后代中存在几何变化节点，paint 遍历需下沉的路标。
     * {@link #markGeometryDirty()} 沿祖先链向上点亮。
     */
    boolean descendantGeometryDirty;

    // ==================== 子树节点数缓存（阶段 2 fork 决策铺路） ====================

    /**
     * 缓存的子树节点数（含自身），供阶段 2 ForkJoinPool fork 决策阈值使用。
     *
     * <p>叶子=1；容器=1+各子节点子树数之和。由 {@code SceneLayoutEngine.layoutInternal}
     * 后序遍历顺带重算（后序保证子节点 count 已是最新），<b>绝不向下递归专门遍历</b>。
     * 初始 1：新建节点无子，count=1 已正确，故 {@link #subtreeCountDirty} 初始 false。</p>
     *
     * <p><b>维护纪律</b>：此字段只由 {@link #__recomputeSubtreeCountIfDirty()} 写入，
     * 绝不外部设（无 setter）。结构变化入口沿祖先链冒泡 {@link #subtreeCountDirty}，
     * layout 后序遍历读到 dirty 时重算并清 false——与现有脏标记冒泡机制同构，
     * 复用后序遍历零额外遍历成本（守 I7）。</p>
     */
    private int cachedSubtreeNodeCount = 1;

    /**
     * 子树节点数缓存失效标记。
     *
     * <p>仅在结构变化入口（{@link #appendChild} / {@link #removeChild} /
     * {@link #insertBefore} / {@link #applyChildReconcile}）沿祖先链冒泡点亮。
     * layout 后序遍历若读到 true 则重算 {@link #cachedSubtreeNodeCount} 并清 false。</p>
     *
     * <p><b>与 selfLayoutDirty 严格独立</b>：非结构变化（setText/setPadding 等属性 setter）
     * 调 {@link #markSelfLayout()} 但<b>不</b>触碰此标记，使干净帧（属性变化后）count 不重算，
     * 守 I7 干净帧零开销。结构变化入口同时调两者，故结构变化后 layout 必然走后序重算分支
     * （selfLayoutDirty==true 不会整棵跳过），count 重算时机有保证。</p>
     */
    private boolean subtreeCountDirty = false;

    // ==================== 缓存占位（T4/T5 填充） ====================

    /** 布局结果缓存，无效时为 null */
    Object cachedLayout;

    /** 绘制结果缓存，无效时为 null */
    Object cachedPaint;

    /** 上一次 layoutInternal 传入本节点的约束快照。null=从未布局过。
     *  仅布局引擎读写,作为「约束变更」这一自身布局输入的订阅缓存。
     *  语义类比引擎的 lastRootConstraints,但下放到每个节点,使深层节点
     *  也能感知收到的约束变化。绝不参与脏标记冒泡,绝不触碰后代。 */
    private Constraints lastConstraints;   // 默认 null

    public Constraints __getLastConstraints() { return lastConstraints; }
    public void __setLastConstraints(Constraints c) { this.lastConstraints = c; }

    /** 上一次完成文本测量时的字体 epoch。-1=从未测量。仅布局引擎读写，
     *  作为「epoch 变化」这一布局输入的订阅快照。语义类比 lastConstraints，
     *  下放到每个文本叶节点。绝不参与脏标记冒泡、绝不触碰后代。 */
    private int lastMeasuredEpoch = -1;

    public int __getLastMeasuredEpoch() { return lastMeasuredEpoch; }
    public void __setLastMeasuredEpoch(int epoch) { this.lastMeasuredEpoch = epoch; }

    // ==================== 强类型属性槽 ====================

    /** 文本内容，默认 null */
    private String text;

    /**
     * 字号（UI 像素），默认 16。
     *
     * <p>默认 16 保零回归（原绘制层 fontSize hack 的默认值即 16）。字号既影响布局
     * 几何（文本叶 shrink-to-fit 宽度与行高），又影响绘制输出（TEXT 命令 fontSize），
     * 故 {@link #setFontSize} 与 {@link #setText} 同级，标 LAYOUT+PAINT。</p>
     */
    private int fontSizePx = 16;

    /** 背景颜色（ARGB），默认 0（透明） */
    private int backgroundColor;

    /** 不透明度，默认 1.0f（完全不透明） */
    private float opacity = 1.0f;

    /** 合成级变换，默认 null（无变换） */
    private Transform transform;

    /**
     * 是否填充父容器高度。
     *
     * <p>默认 false：高度 = 内容高度（shrink-to-fit），兼容现有行为。
     * 设为 true 时，布局引擎在计算高度时取 max(内容高, 约束可用高度)，
     * 使容器至少填满父容器给定的高度空间。</p>
     *
     * <p><b>硬约束：fillParentHeight 只应用于容器节点。</b>
     * ScenePaintEngine 已接入节点 fontSizePx，不再依赖 height 做 fontSize 回退，
     * 故本约束已解除：fill 文本节点不会导致 fontSize 异常。</p>
     */
    private boolean fillParentHeight = false;

    /**
     * 首选高度（像素），供无文本/无子节点的叶节点显式指定最小高度。
     *
     * <p>默认 0：回退到内容高度（文本行高或 0）。设非零值时，布局引擎对叶节点
     * 取 {@code Math.max(textHeight, preferredHeight)}，确保背景矩形/hit-test
     * 区域有足够高度。不影响容器节点（容器高度由子节点累加决定）。</p>
     */
    private int preferredHeight = 0;

    /**
     * 首选宽度（像素），供节点显式指定盒宽（最终外尺寸，含 padding）。
     *
     * <p>默认 0：不约束，回退到现有宽度决策（容器 fill / 文本叶 shrink-to-fit /
     * 无文本叶 fill）。设非零值时，布局引擎在 {@code computeWidth} 中以最高优先级
     * 直接返回该值，压过容器 fill、文本 shrink-to-fit、无文本 fill 三种现有决策。
     * 与 {@link #preferredHeight} 对称，语义为「最终盒外尺寸（含 padding）」，
     * 与 {@code LayoutBox.width} 一致。</p>
     */
    private int preferredWidth = 0;

    /**
     * 最大高度（像素，外尺寸含 padding），0 = 无上界。
     *
     * <p>声明式元数据，父级 grow 求解器先验可读。非 scrollable 路径下，
     * {@code computeHeight} 出口对最终高做 {@code min(h, maxHeight)} clamp；
     * grow 求解器在 freeze do-while 中将撞顶子冻结到 maxHeight 并把剩余空间
     * 回流到未冻结兄弟。与 {@link #preferredHeight} 对称：preferredHeight 作下界、
     * maxHeight 作上界，矛盾时 preferredHeight 赢（下限优先）。</p>
     */
    private int maxHeight = 0;

    /**
     * 最大宽度（像素，外尺寸含 padding），0 = 无上界。声明式元数据。
     *
     * <p>与 {@link #maxHeight} 对称。{@code computeWidth} 在 preferredWidth 显式钉死
     * 分支不 clamp（preferredWidth 优先级最高），其余分支返回前做
     * {@code min(w, maxWidth)} clamp。</p>
     */
    private int maxWidth = 0;

    /**
     * 高度百分比（0-100，0 = 不启用）。相对父先验内高
     * （{@code priorKnownInnerHeight(parent)}），即 {@code childHeight = parentInnerH * pct / 100}。
     *
     * <p><b>仅在 COLUMN 主轴生效</b>：percentHeight 只在父容器 flexDirection==COLUMN 的
     * grow 求解器里被识别为"percent 子作固定子"。ROW 下 percentHeight 不生效——ROW 主轴是宽，
     * 高是交叉轴，percentHeight 子在 ROW 下被当作普通 fill 子处理（由 crossAxisAlign/STRETCH
     * 决定高），percentHeight 字段被忽略。</p>
     *
     * <p><b>fallback</b>：父高不可先验（{@link Constraints#UNCONSTRAINED}）时 percentHeight 失效，
     * 回退 shrink-to-fit（忽略 percent，走自然高）。</p>
     *
     * <p><b>与 flexGrow 互斥，grow 优先</b>：同一子同时设 flexGrow&gt;0 和 percentHeight 时，
     * effectiveGrow 走 grow 分支，percent 被忽略。fillParentHeight 隐式 effectiveGrow=1 同样优先于
     * percentHeight（fillParentHeight + percentHeight → grow 优先，percent 忽略）。</p>
     *
     * <p><b>作固定子</b>：在父 COLUMN grow 求解器里，percent 子占用固定高 = percentHeight，
     * 不参与 grow 分配，也不参与 freeze do-while（已在扫描时作固定子）。</p>
     *
     * <p><b>maxHeight clamp + 内容撑大下界</b>：pctH 算出后 clamp 到 maxHeight；fixedH 贡献取
     * {@code max(pctH, priorKnownChildHeight)}（priorH != UNCONSTRAINED 时），避免内容撑大时
     * fixedH 偏小导致 grow 兄弟溢出。详见 ConstraintResolver.computeColumnGrowHeights Javadoc。</p>
     *
     * <p><b>隐式 fill</b>：percent 子收到下传的 tight 高约束后，{@code computeHeight}
     * 取 {@code max(contentHeight, percentHeight)} 返回 percentHeight（与 grow 子隐式 fill 对称）。</p>
     */
    private int percentHeight = 0;

    /**
     * 宽度百分比（0-100，0 = 不启用）。相对<b>子可用宽</b>
     * （{@code constraints.getAvailableWidth()}，即父内宽 - 子 marginH），
     * 即 {@code childWidth = (parentInnerW - childMarginH) * pct / 100}。
     *
     * <p><b>fallback</b>：无宽约束（{@link Constraints#UNCONSTRAINED}）时 percentWidth 失效，
     * 回退 shrink-to-fit（忽略 percent，走自然宽）。</p>
     *
     * <p>优先级位于 preferredWidth 之后、SHRINK/文本 shrink/fill 之前：preferredWidth 仍最高优先级，
     * percentWidth 仅在无 preferredWidth 且有宽约束时生效。</p>
     */
    private int percentWidth = 0;

    /**
     * 容器宽度策略，默认 {@link WidthSizing#FILL}。
     *
     * <p>默认 fill 保持历史行为零回归；需要内容驱动宽度的容器可显式设为
     * {@link WidthSizing#SHRINK}。</p>
     */
    private WidthSizing widthSizing = WidthSizing.FILL;

    /**
     * 光标样式声明（I4c cursor 投影能力）。
     *
     * <p>默认 null：表示未声明，沿祖先链向上查找首个声明。null 即"继承"语义，
     * 最终回退到 {@link club.heiqi.uilib.ui.scene.input.SceneCursor#DEFAULT}。
     * 组件在 {@code SceneRuntime.mount} 构建阶段通过 {@link #setCursor} 声明。</p>
     *
     * <p><b>⚠ 唯一不标脏的属性槽 setter</b>：cursor 是纯交互投影，不影响
     * layout/paint/composite 任何阶段。{@link #setCursor} 内部不走
     * {@code markSelfLayout/markSelfPaint/markComposite}，是项目唯一例外。
     * 理由详见 I4c 设计（用户拍板 D6-A、oracle 纠偏①）。</p>
     */
    private SceneCursor cursor;

    /**
     * 是否参与命中测试，默认 true（pointer-events 投影）。
     *
     * <p>默认 true：零行为漂移，与现有 hit-test 完全一致。设为 false 时
     * （pointer-events:none 语义），hit-test 跳过本节点作为「叶命中目标」，
     * 命中穿透到父节点；但子节点仍可命中，且子节点命中时本节点仍作为结构锚点
     * 出现在命中链路径中。</p>
     *
     * <p><b>⚠ 纯交互投影属性</b>：只影响输入路由，不影响 layout/paint/composite。
     * 其 setter {@link #setHitTestable} 与 {@link #setCursor} 同为项目有意不标脏的例外。</p>
     */
    private boolean hitTestable = true;

    // ==================== flex 布局属性槽（LAYOUT 级，影响盒模型尺寸/子节点排布） ====================

    /**
     * flex 主轴方向，默认 {@link FlexDirection#COLUMN}。
     *
     * <p>默认 COLUMN 保证不设置时与现有引擎垂直堆叠行为一致（零回归）。
     * 改变主轴方向会改变子节点排布，属 LAYOUT 级失效。</p>
     */
    private FlexDirection flexDirection = FlexDirection.COLUMN;

    /**
     * flex-grow 权重（LAYOUT 级）。COLUMN 主轴下：默认 0 不参与剩余空间分配；
     * >0 时按权重分得 freeH 份额。fillParentHeight 在 COLUMN 主轴等价 flexGrow=1，
     * 显式 flexGrow>0 时以 flexGrow 为准。int 精度，余数补末位 grow 子（Qt 语义）。
     */
    private int flexGrow = 0;

    /** 内边距：上，默认 0 */
    private int paddingTop = 0;

    /** 内边距：右，默认 0 */
    private int paddingRight = 0;

    /** 内边距：下，默认 0 */
    private int paddingBottom = 0;

    /** 内边距：左，默认 0 */
    private int paddingLeft = 0;

    /** 上外边距（像素），子节点在父容器内占用的上方空间。 */
    private int marginTop = 0;

    /** 右外边距（像素）。 */
    private int marginRight = 0;

    /** 下外边距（像素）。 */
    private int marginBottom = 0;

    /** 左外边距（像素）。 */
    private int marginLeft = 0;

    /** 子节点之间的主轴间距，默认 0 */
    private int gap = 0;

    /**
     * 主轴对齐方式，默认 {@link MainAxisAlign#START}。
     *
     * <p>默认 START 保证不设置时子节点靠主轴起点堆叠，与现有行为一致。</p>
     */
    private MainAxisAlign mainAxisAlign = MainAxisAlign.START;

    /**
     * 交叉轴对齐方式，默认 {@link CrossAxisAlign#STRETCH}。
     *
     * <p>默认 STRETCH 保证不设置时子节点在交叉轴上拉伸填满父容器，
     * 与现有引擎"子节点宽度填满父宽"行为一致（零回归）。</p>
     */
    private CrossAxisAlign crossAxisAlign = CrossAxisAlign.STRETCH;

    /**
     * 子级交叉轴对齐覆盖，默认 {@link AlignSelf#AUTO}。
     *
     * <p>语义对齐 CSS {@code align-self}：非 AUTO 时覆盖父容器
     * {@link #crossAxisAlign} 对本子节点的设置；AUTO 时回退父级。
     * 默认 AUTO 保证不设置时与现有引擎行为一致（零回归）。
     * 有效对齐解析集中在 {@code FlexLayouter.effectiveCrossAlign}。</p>
     */
    private AlignSelf alignSelf = AlignSelf.AUTO;

    // ==================== 绘制属性槽（PAINT 级，只改绘制输出不改盒模型尺寸） ====================

    /**
     * 边框颜色（ARGB），默认 0（无边框）。
     *
     * <p>第 0 段裁决：边框不占布局空间（box-sizing: border-box 简化），
     * 故边框相关属性只标 PAINT，绝不标 LAYOUT。</p>
     */
    private int borderColor = 0;

    /** 边框宽度（像素），默认 0（无边框）。第 0 段裁决：边框不占布局空间，只标 PAINT */
    private int borderWidth = 0;

    /** 圆角半径（像素），默认 0（直角）。只影响绘制输出，标 PAINT */
    private int cornerRadius = 0;

    /** 是否裁剪超出本节点边界的子节点绘制，默认 false。只影响绘制裁剪，标 PAINT */
    private boolean clipChildren = false;

    /**
     * 文本颜色（ARGB），默认 0xFFFFFFFF（白色，兼容现有默认）。
     *
     * <p>文本颜色变化只改绘制输出、不改文字尺寸，故只标 PAINT，
     * 绝不像 {@link #setText} 那样标 LAYOUT+PAINT。</p>
     */
    private int textColor = 0xFFFFFFFF;

    /** 文本在布局盒内的水平对齐方式，默认贴左。PAINT 级属性，不影响盒尺寸。 */
    private TextHorizontalAlign textHorizontalAlign = TextHorizontalAlign.LEFT;

    /** 文本在布局盒内的垂直对齐方式，默认居中。PAINT 级属性，不影响盒尺寸。 */
    private TextVerticalAlign textVerticalAlign = TextVerticalAlign.CENTER;

    // ==================== 滚动属性槽（视口/视口基础设施地基，纵向滚动） ====================

    /**
     * 纵向滚动偏移（像素），默认 0。
     *
     * <p><b>失效级别：GEOMETRY（几何级）。</b>{@link #setScrollOffsetY} 去重后<b>只调
     * {@link #markGeometryDirty()}</b>，绝不调 {@link #markSelfLayout()} 或 {@link #markSelfPaint()}。</p>
     *
     * <h3>为何 scrollOffsetY 是 geometry 而非 paint/layout</h3>
     * <ul>
     *   <li><b>不是 LAYOUT</b>：滚动只是把内容子树整体上移/下移显示，绝不改变任何节点的盒模型
     *       尺寸或子节点排布。若标 LAYOUT，每次滚动都会触发整棵 viewport 子树重排（破 I7：
     *       滚动即重排），且会把 scrollOffset「烤进」LayoutBox 的 y 坐标，与「布局结果稳定、
     *       滚动只是绘制平移」的解耦原则冲突。</li>
     *   <li><b>不是 PAINT</b>：滚动不改变任何节点的绘制属性（颜色/文字/边框），后代 fragment
     *       内容完全不变，只是叠加的屏幕偏移变了。若标 PAINT，每次滚动都会让 viewport 内所有
     *       后代 selfPaintDirty=true 而重新生成 fragment（污染 I8 缓存复用），白白重绘。</li>
     *   <li><b>是 GEOMETRY</b>：滚动的语义本质就是「位置变、不重绘、不重排」——这正是 geometry
     *       级标记的语义（paint 遍历下沉、复用 fragment、仅用新 offset 重新叠加坐标）。绘制引擎
     *       对 scrollable 节点在递归后代时注入 {@code -scrollOffsetY} 的 Y 基准偏移，后代复用
     *       fragment + 新偏移自动正确，与现有几何重定位通路同构。</li>
     * </ul>
     */
    private int scrollOffsetY = 0;

    /**
     * 是否为可纵向滚动的视口容器，默认 false。
     *
     * <p><b>失效级别：LAYOUT（布局级）。</b>{@link #setScrollable} 去重后调 {@link #markSelfLayout()}。</p>
     *
     * <h3>为何 scrollable 是 LAYOUT 级</h3>
     * <p>scrollable 改变 viewport 自身的高度计算语义：scrollable=true 时布局引擎
     * <b>不走</b> {@code max(natural, preferredHeight)} 的内容撑大逻辑，而是<b>直接钉死为视口高</b>
     * （主动忽略内容高，首次解耦 viewport/content）。这是一个布局输入（改变高度决策），
     * 故必须标 LAYOUT，使布局引擎重新计算 viewport 自身高度。</p>
     *
     * <p>横向滚动（scrollOffsetX/scrollableX）本期不实现（YAGNI）。
     * contentSize/viewportSize/maxScroll 全部派生不存（守 NORTH_STAR §6：新增缓存
     * 必须答出让哪层跳过什么重算，存这些答不上来）。</p>
     */
    private boolean scrollable = false;

    // ==================== 构造器 ====================

    /** 创建一个空的场景树节点 */
    public SceneNode() {
        this.children = new ArrayList<>();
    }

    // ==================== 树操作 ====================

    /**
     * 在末尾追加一个子节点。
     *
     * <p>如果子节点已有父节点（且不是本节点），先从旧父移除。
     * 追加后本容器调用 {@link #markSelfLayout()}（子节点集合变化影响本容器布局），
     * 绝不递归标记 child 的后代。</p>
     *
     * @param child 要添加的子节点
     */
    public void appendChild(SceneNode child) {
        if (child == null) return;
        // 如果 child 已有父节点（且不是本节点），先从旧父移除，并标旧父脏
        if (child.parent != null && child.parent != this) {
            SceneNode oldParent = child.parent;
            oldParent.children.remove(child);
            oldParent.markSelfLayout();
            oldParent.markSubtreeCountDirty();
        }
        children.add(child);
        child.parent = this;
        markSelfLayout();
        markSubtreeCountDirty();
    }

    /**
     * 移除一个子节点。
     *
     * <p>移除后子节点 parent 置 null，本容器调用 {@link #markSelfLayout()}。
     * 绝不递归标记 child 的后代。</p>
     *
     * @param child 要移除的子节点
     */
    public void removeChild(SceneNode child) {
        if (child == null) return;
        if (children.remove(child)) {
            child.parent = null;
            markSelfLayout();
            markSubtreeCountDirty();
        }
    }

    /**
     * 在指定锚点前插入一个子节点。
     *
     * <p>本容器调用 {@link #markSelfLayout()}，不递归标记后代。</p>
     *
     * @param child  要插入的子节点
     * @param anchor 锚点节点，child 将插在其之前
     */
    public void insertBefore(SceneNode child, SceneNode anchor) {
        if (child == null || anchor == null) return;
        int idx = children.indexOf(anchor);
        if (idx < 0) return;
        // 如果 child 已有父节点（且不是本节点），先从旧父移除，并标旧父脏
        if (child.parent != null && child.parent != this) {
            SceneNode oldParent = child.parent;
            oldParent.children.remove(child);
            oldParent.markSelfLayout();
            oldParent.markSubtreeCountDirty();
        }
        children.add(idx, child);
        child.parent = this;
        markSelfLayout();
        markSubtreeCountDirty();
    }

    /**
     * 批量结构变更入口 —— 根除 I7 债的关键 API。
     *
     * <h3>语义</h3>
     * <p>reconciler 一次性提交"最终子节点序列" + "其中哪些是新插入或被移动的项"。
     * 本方法将 {@link #children} 整体替换为 {@code finalOrder}，维护好每个节点的
     * parent 指针：出现在 finalOrder 中的节点 parent=this；不再出现的旧 child parent=null。</p>
     *
     * <h3>脏标记策略（与旧模型的核心差异）</h3>
     * <ul>
     *   <li>容器自身因子序列变化调一次 {@link #markSelfLayout()}（标容器自己 + 向上冒泡）。</li>
     *   <li><b>对 finalOrder 中不在 insertedOrMoved 里的稳定复用节点：零标脏</b>
     *       （既不标它们 selfLayoutDirty，也不碰它们的后代）。这是与旧
     *       {@code markSubtreeLayoutMutation} 递归全标行为的正面翻转。</li>
     *   <li>对 insertedOrMoved 中的节点：不在此方法内递归标其子树。
     *       它们位置变了需要后续布局重排，但这是它们各自的事，
     *       由 {@code markSelfLayout()} 的向上冒泡机制保证布局遍历会下沉到本容器。</li>
     * </ul>
     *
     * <h3>对照旧 DOM 的 I7 债</h3>
     * <p>旧 {@code DocumentNode.recordStructuralMutation} 在每次 appendChild/removeChild
     * 时调用 {@code markSubtreeLayoutMutation}，无条件向下递归刷新全部后代的脏版本，
     * 导致未变的稳定子节点被布局层判定复用失败、全量重算。
     * 本方法通过"稳定子项零标脏"正面消除此债。</p>
     *
     * <h3>insertedOrMoved 的定位：声明性元数据，不驱动标脏</h3>
     * <p>{@code insertedOrMoved} 当前仅为<b>声明性元数据</b>——本方法体<b>不读取它、
     * 不据此驱动任何标脏</b>（方法内对该参数零引用）。零标脏是靠"稳定子项不被
     * {@link #markSelfLayout()}"隐式达成的，与本参数无关。</p>
     * <p>几何变化的<b>唯一权威判定源</b>是 {@code SceneLayoutEngine} 的
     * {@code newBox.equals(childBox)} 几何闸门：layout 阶段会重新发现谁的几何真正发生变化，
     * 由它单独决定谁需要重排。</p>
     * <p><b>严禁</b>后续在本方法内补"读取 insertedOrMoved 并对其中节点
     * {@code markGeometryDirty / markSelfLayout}"之类的消费逻辑——那会与 layout 几何闸门
     * 形成两个独立的"谁移动了"权威源，迟早彼此漂移（语义双载反模式，对照
     * {@code NORTH_STAR} 反模式"万能脏标记"）。本参数的价值仅在于让调用方表达意图，
     * 不参与本方法的任何控制流。</p>
     *
     * <h3>前置约束</h3>
     * <p>本方法<b>假定 finalOrder 中的所有节点都同属当前容器（this）</b>。
     * 被移除的旧 child（不在 finalOrder）其旧父就是 this，已由方法末尾的
     * {@link #markSelfLayout()} 兜底标脏。<b>跨容器移动（child 旧父非 this）的标脏当前未处理</b>，
     * 是已知边界：forEach 单容器场景不会触发；若 Phase 3 复用本 API 做跨容器移动，
     * 需先补"旧父标脏"逻辑后再使用。</p>
     *
     * @param finalOrder       最终子节点序列（顺序有意义）
     * @param insertedOrMoved  声明性元数据：标注哪些是新插入或被移动的节点（可为空集）。
     *                         <b>本方法体不读取、不消费此参数</b>，详见上方"声明性元数据"小节。
     */
    public void applyChildReconcile(List<SceneNode> finalOrder, Set<SceneNode> insertedOrMoved) {
        if (finalOrder == null) return;

        // 1. 将不再出现在 finalOrder 中的旧 child 的 parent 置 null
        //    用基于引用相等的 IdentityHashMap 集合做 O(1) 判定，避免 List.contains 的 O(n²) 线性查找
        Set<SceneNode> finalOrderSet = Collections.newSetFromMap(new IdentityHashMap<>());
        finalOrderSet.addAll(finalOrder);
        for (SceneNode child : children) {
            if (!finalOrderSet.contains(child)) {
                child.parent = null;
            }
        }

        // 2. 替换子节点列表
        children.clear();
        children.addAll(finalOrder);

        // 3. 维护 finalOrder 中每个节点的 parent 指针
        for (SceneNode child : finalOrder) {
            child.parent = this;
        }

        // 4. 容器自身因子序列变化标脏一次（只标自己 + 向上冒泡）
        //    绝不递归标记任何子节点或后代
        markSelfLayout();
        markSubtreeCountDirty();
    }

    // ==================== 核心失效方法 ====================

    /**
     * 标记自身布局脏。
     *
     * <p>设置 {@code selfLayoutDirty = true}，使本节点 layout 缓存失效，
     * 然后沿祖先链向上点亮 {@code descendantLayoutDirty} 路标（O(深度)）。</p>
     */
    public void markSelfLayout() {
        if (selfLayoutDirty) return; // 已标脏，跳过重复冒泡
        selfLayoutDirty = true;
        cachedLayout = null;
        bubbleDescendantLayout();
    }

    /**
     * 标记自身绘制脏。
     *
     * <p>设置 {@code selfPaintDirty = true}，使本节点 paint 缓存失效，
     * 然后沿祖先链向上点亮 {@code descendantPaintDirty} 路标（O(深度)）。</p>
     */
    public void markSelfPaint() {
        selfPaintDirty = true;
        cachedPaint = null;
        bubbleDescendantPaint();
    }

    /**
     * 标记合成脏。
     *
     * <p>设置 {@code compositeDirty = true}（transform/opacity 变化），
     * 然后沿祖先链向上点亮独立的 {@code descendantCompositeDirty} 路标（O(深度)）。
     * 合成遍历据此下沉到本节点，无需重排布局、无需重建 fragment——只调整
     * group opacity / transform offset（守宪章信条五：60fps 合成级动画绝不触碰
     * 布局层或绘制层）。</p>
     *
     * <p>composite 路标与 paint 路标严格分离：纯 opacity/transform 变化绝不
     * 污染 paint 失效链，从而保证合成动画帧零重绘、零重排。</p>
     */
    public void markComposite() {
        if (compositeDirty) return; // 已标脏，跳过重复冒泡（与 markSelfLayout/markGeometryDirty 对齐）
        compositeDirty = true;
        bubbleDescendantComposite();
    }

    /**
     * 标记几何变化（位置/尺寸变化，layout 引擎产出）。
     *
     * <p>设置 {@code selfGeometryDirty = true}，然后沿祖先链向上点亮
     * {@code descendantGeometryDirty} 路标（O(深度)）。paint 遍历据此
     * 下沉到本节点，复用 fragment（selfPaintDirty==false 时不重绘）
     * 但用新的绝对偏移重新叠加坐标。</p>
     *
     * <p>核心不变量：<b>绝不向下递归触碰任何后代</b>。</p>
     */
    public void markGeometryDirty() {
        if (selfGeometryDirty) return; // 已标脏，跳过重复冒泡
        selfGeometryDirty = true;
        bubbleDescendantGeometry();
    }

    /**
     * 仅置 selfGeometryDirty，绝不向上冒泡（并行 layout 专用）。
     *
     * <p>与 {@link #markGeometryDirty()} 的唯一区别：拆掉 bubble 步骤。
     * worker 内调用，只写本节点自己的字段；向上点亮 descendant 路标延迟到
     * 父的 join 点经 {@link #__bubbleDescendantGeometryFromSelf()} 串行补齐
     * （守 I7 并行强化）。</p>
     *
     * @return 本次调用是否实际点亮了 selfGeometryDirty（false=已脏短路，父无需补 bubble）
     */
    public boolean __setSelfGeometryDirtyNoBubble() {
        if (selfGeometryDirty) return false;
        selfGeometryDirty = true;
        return true;
    }

    /**
     * 从本节点出发，向上点亮祖先链 descendantGeometryDirty 路标（join 点串行补 bubble 用）。
     *
     * <p>语义与 {@link #markGeometryDirty()} 的 bubble 阶段完全一致，仅拆分出来供
     * 父节点 join 点串行调用，避免多 worker 并发冒泡写共享祖先 boolean。</p>
     */
    public void __bubbleDescendantGeometryFromSelf() {
        bubbleDescendantGeometry();
    }

    /**
     * 仅置 selfPaintDirty + 清 cachedPaint，绝不向上冒泡（并行 layout 专用）。
     *
     * <p>★ 关键：{@link #markSelfPaint()} 无短路（每次都重置位并清缓存），
     * 本方法忠实保留无短路语义：<b>不加 {@code if(selfPaintDirty) return}</b>，
     * 每次调用必清 {@code cachedPaint=null}，返回恒 true。</p>
     *
     * @return 恒为 true（无短路语义：被调即需父补 bubble）
     */
    public boolean __setSelfPaintDirtyNoBubble() {
        selfPaintDirty = true;
        cachedPaint = null;
        return true;
    }

    /**
     * 从本节点出发，向上点亮祖先链 descendantPaintDirty 路标（join 点串行补 bubble 用）。
     *
     * <p>语义与 {@link #markSelfPaint()} 的 bubble 阶段完全一致，仅拆分出来供
     * 父节点 join 点串行调用。</p>
     */
    public void __bubbleDescendantPaintFromSelf() {
        bubbleDescendantPaint();
    }

    /**
     * 沿祖先链向上点亮 {@code descendantLayoutDirty} 路标。
     *
     * <p>核心不变量：<b>绝对不触碰任何 children / 任何后代</b>。
     * 从 parent 开始沿祖先链向上，遇某祖先的 {@code descendantLayoutDirty}
     * 已为 true 则立即停止（路标已点亮，上面都已知晓），否则置 true 继续上溯。
     * 复杂度 O(深度)。</p>
     */
    private void bubbleDescendantLayout() {
        SceneNode current = parent;
        while (current != null) {
            if (current.descendantLayoutDirty) {
                // 路标已点亮，祖先链上面都已经知道有后代脏了，停止
                break;
            }
            current.descendantLayoutDirty = true;
            current = current.parent;
        }
    }

    /**
     * 沿祖先链向上点亮 {@code descendantPaintDirty} 路标。
     *
     * <p>与 {@link #bubbleDescendantLayout()} 同理：遇已点亮即停，O(深度)。
     * 绝对不触碰任何后代。</p>
     */
    private void bubbleDescendantPaint() {
        SceneNode current = parent;
        while (current != null) {
            if (current.descendantPaintDirty) {
                break;
            }
            current.descendantPaintDirty = true;
            current = current.parent;
        }
    }

    /**
     * 沿祖先链向上点亮 {@code descendantGeometryDirty} 路标。
     *
     * <p>与 {@link #bubbleDescendantLayout()} 同构：遇已点亮即停，O(深度)。
     * 绝对不触碰任何后代。paint 遍历读取此路标决定下沉范围，
     * 到达 selfGeometryDirty==true 的节点时复用 fragment 但更新 offset。</p>
     */
    private void bubbleDescendantGeometry() {
        SceneNode current = parent;
        while (current != null) {
            if (current.descendantGeometryDirty) {
                break;
            }
            current.descendantGeometryDirty = true;
            current = current.parent;
        }
    }

    /**
     * 沿祖先链向上点亮 {@code descendantCompositeDirty} 路标。
     *
     * <p>与 {@link #bubbleDescendantPaint()} 同构：遇已点亮即停，O(深度)。
     * 绝对不触碰任何后代。合成遍历读取此路标决定下沉范围，
     * 到达 compositeDirty==true 的节点时调整 group opacity / transform offset。</p>
     */
    private void bubbleDescendantComposite() {
        SceneNode current = parent;
        while (current != null) {
            if (current.descendantCompositeDirty) {
                break;
            }
            current.descendantCompositeDirty = true;
            current = current.parent;
        }
    }

    /**
     * 标记本节点子树节点数缓存失效，并沿祖先链冒泡 {@link #subtreeCountDirty}。
     *
     * <p>仅在结构变化入口（{@link #appendChild} / {@link #removeChild} /
     * {@link #insertBefore} / {@link #applyChildReconcile}）调用：本节点子节点集合
     * 变化 → 本节点 count 变 → 祖先 count 也变。与 {@link #markSelfLayout()} 独立调用，
     * 使非结构变化的属性 setter 不触碰 count 标记，守 I7 干净帧零开销。</p>
     *
     * <p>冒泡与 {@link #bubbleDescendantLayout()} 同构：遇已点亮即停，O(深度)。
     * 绝不触碰任何后代。本节点自身无条件置 true（每次结构变化 count 必变），
     * 祖先链遇已点亮即停（短路优化）。</p>
     */
    private void markSubtreeCountDirty() {
        subtreeCountDirty = true;
        SceneNode current = parent;
        while (current != null) {
            if (current.subtreeCountDirty) {
                break;
            }
            current.subtreeCountDirty = true;
            current = current.parent;
        }
    }

    // ==================== 清除方法 ====================

    /**
     * 清除所有脏标记（自身 + 后代路标 + 合成标记）。
     *
     * <p>布局/绘制遍历完成后调用，将节点恢复"干净"状态。
     * T4/T5 将需要更细粒度的分别清除，当前提供统一入口。</p>
     */
    public void clearDirtyFlags() {
        selfLayoutDirty = false;
        descendantLayoutDirty = false;
        selfPaintDirty = false;
        descendantPaintDirty = false;
        compositeDirty = false;
        descendantCompositeDirty = false;
    }

    /**
     * 清除布局相关脏标记。
     *
     * <p>清除 {@code selfLayoutDirty} 和 {@code descendantLayoutDirty}，
     * 供布局遍历完成后调用。</p>
     */
    public void clearLayoutDirty() {
        selfLayoutDirty = false;
        descendantLayoutDirty = false;
    }

    /**
     * 清除绘制相关脏标记。
     *
     * <p>清除 {@code selfPaintDirty} 和 {@code descendantPaintDirty}，
     * 供绘制遍历完成后调用。</p>
     *
     * <p>不再顺手清 {@code compositeDirty}：composite 路标已在 Phase 3
     * 独立化（拥有独立 bubble/clear），合成标记的清理由
     * {@link #clearCompositeDirty()} 单独负责，与 paint 严格分离。</p>
     */
    public void clearPaintDirty() {
        selfPaintDirty = false;
        descendantPaintDirty = false;
    }

    /**
     * 清除合成相关脏标记。
     *
     * <p>清除 {@code compositeDirty} 和 {@code descendantCompositeDirty}，
     * 供合成遍历完成（opacity/transform 消费后）调用。
     * 不碰 layout/paint/geometry 任何其他标记类别。</p>
     */
    public void clearCompositeDirty() {
        compositeDirty = false;
        descendantCompositeDirty = false;
    }

    /**
     * 清除几何变化脏标记。
     *
     * <p>清除 {@code selfGeometryDirty} 和 {@code descendantGeometryDirty}，
     * 供 paint 遍历完成（位置→offset 叠加）后调用。
     * 不碰 layout/paint/composite 任何其他标记类别。</p>
     */
    public void clearGeometryDirty() {
        selfGeometryDirty = false;
        descendantGeometryDirty = false;
    }

    // ==================== 缓存管理 ====================

    /** 使布局缓存失效 */
    public void invalidateLayoutCache() {
        cachedLayout = null;
    }

    /** 使绘制缓存失效 */
    public void invalidatePaintCache() {
        cachedPaint = null;
    }

    /** @return 布局缓存，可能为 null */
    public Object getCachedLayout() {
        return cachedLayout;
    }

    /** @param cachedLayout 布局缓存值 */
    public void setCachedLayout(Object cachedLayout) {
        this.cachedLayout = cachedLayout;
    }

    /** @return 绘制缓存，可能为 null */
    public Object getCachedPaint() {
        return cachedPaint;
    }

    /** @param cachedPaint 绘制缓存值 */
    public void setCachedPaint(Object cachedPaint) {
        this.cachedPaint = cachedPaint;
    }

    // ==================== 属性访问器（强类型，自动打失效级别） ====================

    /**
     * 设置文本内容。
     *
     * <p>先判值是否真变化（与当前值相等则直接 return），
     * 变化时自动调用 {@link #markSelfLayout()} + {@link #markSelfPaint()}：
     * 文本既影响布局盒尺寸/行高（LAYOUT），又影响绘制输出/实际字符串（PAINT）。</p>
     *
     * @param text 新的文本内容
     */
    public void setText(String text) {
        if (Objects.equals(this.text, text)) return;
        this.text = text;
        markSelfLayout();
        markSelfPaint();
    }

    /** @return 当前文本内容 */
    public String getText() {
        return text;
    }

    /**
     * 设置字号（UI 像素）。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfLayout()} + {@link #markSelfPaint()}
     * （字号既改几何又改绘制，与 {@link #setText} 同级）。</p>
     *
     * @param fontSizePx 字号（UI 像素），应大于 0
     */
    public void setFontSize(int fontSizePx) {
        if (this.fontSizePx == fontSizePx) return;
        this.fontSizePx = fontSizePx;
        markSelfLayout();
        markSelfPaint();
    }

    /** @return 当前字号（UI 像素），默认 16 */
    public int getFontSize() {
        return fontSizePx;
    }

    /**
     * 设置背景颜色（ARGB）。
     *
     * <p>值不变则跳过，变化时调用 {@link #markSelfPaint()}。</p>
     *
     * @param backgroundColor ARGB 颜色值
     */
    public void setBackgroundColor(int backgroundColor) {
        if (this.backgroundColor == backgroundColor) return;
        this.backgroundColor = backgroundColor;
        markSelfPaint();
    }

    /** @return 当前背景颜色（ARGB） */
    public int getBackgroundColor() {
        return backgroundColor;
    }

    /**
     * 设置不透明度。
     *
     * <p>值不变则跳过，变化时调用 {@link #markComposite()}。</p>
     *
     * @param opacity 不透明度，范围 0.0f-1.0f
     */
    public void setOpacity(float opacity) {
        if (Float.compare(this.opacity, opacity) == 0) return;
        this.opacity = opacity;
        markComposite();
    }

    /** @return 当前不透明度 */
    public float getOpacity() {
        return opacity;
    }

    /**
     * 设置合成级变换。
     *
     * <p>值不变则跳过，变化时调用 {@link #markComposite()}。</p>
     *
     * @param transform 变换对象，可为 null
     */
    public void setTransform(Transform transform) {
        if (Objects.equals(this.transform, transform)) return;
        this.transform = transform;
        markComposite();
    }

    /** @return 当前合成级变换，可能为 null */
    public Transform getTransform() {
        return transform;
    }

    /**
     * 设置是否填充父容器高度。
     *
     * <p>遵循现有 setter 范式：值不变则直接 return（去重），
     * 值变化时调用 {@link #markSelfLayout()}（fill 意图变化影响自身布局）。</p>
     *
     * <p><b>硬约束：fillParentHeight 只应用于容器节点，绝不用于文本叶节点。</b></p>
     *
     * <p><b>支持范围（有意 YAGNI 边界）：</b>当前支持 root 节点 fill、ROW 容器交叉轴
     * （高）方向的深层 fill 子节点穿透下传，以及 COLUMN 容器中 fill 子在固定兄弟
     * 高度均可先验时按 effectiveGrow 权重分配剩余主轴高度（flexGrow=0 时 fill 视为
     * 隐式权重 1，显式 flexGrow>0 时以 flexGrow 为准；多 fill 子按等权分配，
     * 余数补末位 grow 子，Qt 语义）。详见 ConstraintResolver.computeColumnGrowHeights。</p>
     *
     * @param fillParentHeight 是否填充父容器高度
     */
    public void setFillParentHeight(boolean fillParentHeight) {
        if (this.fillParentHeight == fillParentHeight) return;
        this.fillParentHeight = fillParentHeight;
        markSelfLayout();
    }

    /** @return 是否填充父容器高度 */
    public boolean isFillParentHeight() {
        return fillParentHeight;
    }

    /**
     * 设置首选高度（像素），供无文本/无子节点的叶节点显式指定最小高度。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfLayout()}
     * （尺寸变化影响自身布局，级别 LAYOUT）。与 {@link #setPreferredWidth} 对称。</p>
     *
     * @param preferredHeight 首选高度，非负整数，0 表示不设最小值
     */
    public void setPreferredHeight(int preferredHeight) {
        if (this.preferredHeight == preferredHeight) return;
        this.preferredHeight = preferredHeight;
        markSelfLayout();
    }

    /** @return 当前首选高度（像素），默认 0 */
    public int getPreferredHeight() {
        return preferredHeight;
    }

    /**
     * 设置首选宽度（像素），显式指定盒宽（最终外尺寸，含 padding）。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfLayout()}
     * （尺寸变化影响自身布局，级别 LAYOUT）。结构与 {@link #setPreferredHeight}
     * 对称。设非零值时压过 {@code computeWidth} 中所有现有宽度决策（最高优先级）。</p>
     *
     * <p><b>I7 不变量</b>：只调用本节点 {@code markSelfLayout()}（脏向上冒泡），
     * 绝不触碰子节点、绝不向下递归标脏。</p>
     *
     * @param preferredWidth 首选宽度，非负整数，0 表示不约束（回退现有宽度决策）
     */
    public void setPreferredWidth(int preferredWidth) {
        if (this.preferredWidth == preferredWidth) return;
        this.preferredWidth = preferredWidth;
        markSelfLayout();
    }

    /** @return 当前首选宽度（像素），默认 0 */
    public int getPreferredWidth() {
        return preferredWidth;
    }

    /**
     * 设置最大高度（像素，外尺寸含 padding），0 = 无上界。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfLayout()}
     * （尺寸上界变化影响自身布局，级别 LAYOUT）。结构与 {@link #setPreferredHeight}
     * 对称。{@code markSelfLayout} 已含向上冒泡，无需额外递归。</p>
     *
     * <p><b>I7 不变量</b>：只调用本节点 {@code markSelfLayout()}，绝不向下递归标脏。</p>
     *
     * @param maxHeight 最大高度，非负整数，0 表示无上界
     */
    public void setMaxHeight(int maxHeight) {
        if (this.maxHeight == maxHeight) return;
        this.maxHeight = maxHeight;
        markSelfLayout();
    }

    /** @return 当前最大高度（像素），默认 0 表示无上界 */
    public int getMaxHeight() {
        return maxHeight;
    }

    /**
     * 设置最大宽度（像素，外尺寸含 padding），0 = 无上界。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfLayout()}。
     * 与 {@link #setMaxHeight} 对称。</p>
     *
     * @param maxWidth 最大宽度，非负整数，0 表示无上界
     */
    public void setMaxWidth(int maxWidth) {
        if (this.maxWidth == maxWidth) return;
        this.maxWidth = maxWidth;
        markSelfLayout();
    }

    /** @return 当前最大宽度（像素），默认 0 表示无上界 */
    public int getMaxWidth() {
        return maxWidth;
    }

    /**
     * 设置高度百分比（0-100，0 = 不启用）。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfLayout()}
     * （尺寸变化影响自身布局，级别 LAYOUT）。结构与 {@link #setMaxHeight} 对称。
     * percentHeight 相对父先验内高，无父高约束时回退 shrink；与 flexGrow 互斥，grow 优先。</p>
     *
     * <p><b>I7 不变量</b>：只调用本节点 {@code markSelfLayout()}，绝不向下递归标脏。</p>
     *
     * @param percentHeight 高度百分比，0-100，0 表示不启用
     */
    public void setPercentHeight(int percentHeight) {
        if (this.percentHeight == percentHeight) return;
        this.percentHeight = percentHeight;
        markSelfLayout();
    }

    /** @return 当前高度百分比，默认 0 表示不启用 */
    public int getPercentHeight() {
        return percentHeight;
    }

    /**
     * 设置宽度百分比（0-100，0 = 不启用）。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfLayout()}
     * （尺寸变化影响自身布局，级别 LAYOUT）。与 {@link #setPercentHeight} 对称。
     * percentWidth 相对父内宽，无宽约束时回退 shrink。</p>
     *
     * <p><b>I7 不变量</b>：只调用本节点 {@code markSelfLayout()}，绝不向下递归标脏。</p>
     *
     * @param percentWidth 宽度百分比，0-100，0 表示不启用
     */
    public void setPercentWidth(int percentWidth) {
        if (this.percentWidth == percentWidth) return;
        this.percentWidth = percentWidth;
        markSelfLayout();
    }

    /** @return 当前宽度百分比，默认 0 表示不启用 */
    public int getPercentWidth() {
        return percentWidth;
    }

    /**
     * 设置容器宽度策略。
     *
     * <p>值不变则直接 return（去重），变化时只调用 {@link #markSelfLayout()}。
     * 该 setter 仅标记本节点布局脏并向上冒泡，绝不触碰子节点或向下递归标脏，
     * 守住 I7。传入 null 时按 {@link WidthSizing#FILL} 处理。</p>
     *
     * @param widthSizing 容器宽度策略，null 表示恢复默认 FILL
     */
    public void setWidthSizing(WidthSizing widthSizing) {
        WidthSizing normalized = widthSizing == null ? WidthSizing.FILL : widthSizing;
        if (this.widthSizing == normalized) return;
        this.widthSizing = normalized;
        markSelfLayout();
    }

    /** @return 当前容器宽度策略，默认 {@link WidthSizing#FILL} */
    public WidthSizing getWidthSizing() {
        return widthSizing;
    }

    /**
     * 设置光标样式（I4c cursor 投影能力）。
     *
     * <p><b>⚠ 项目唯一不标脏的属性 setter</b>：cursor 是纯交互投影，
     * 不影响 layout/paint/composite 任何阶段。此 setter 内部不走
     * {@code markSelfLayout/markSelfPaint/markComposite}，也不会点亮
     * 任何祖先路标。这是有意的设计例外（用户拍板 D6-A、oracle 纠偏①）。</p>
     *
     * <p>null 表示"未声明/继承"语义，祖先链上溯至根均无声明时回退
     * {@link SceneCursor#DEFAULT}。解析由 {@code SceneCursorResolver} 负责。</p>
     *
     * @param cursor 光标样式枚举值，可为 null 表示未声明
     */
    public void setCursor(SceneCursor cursor) {
        // ★ 去重但绝不标脏：cursor 不影响 layout/paint/composite（D6-A）
        if (this.cursor == cursor) return;
        this.cursor = cursor;
    }

    /** @return 当前光标样式声明，null 表示未声明/继承 */
    public SceneCursor getCursor() {
        return cursor;
    }

    /**
     * 设置是否参与命中测试（pointer-events 投影能力）。
     *
     * <p><b>⚠ 项目第二个有意不标脏的属性 setter</b>（首个为 {@link #setCursor}）：
     * hitTestable 是纯输入路由投影，只影响 hit-test 命中候选，绝不影响
     * layout/paint/composite 任何渲染阶段。此 setter 内部不走
     * {@code markSelfLayout/markSelfPaint/markComposite}，也不会点亮任何祖先路标。</p>
     *
     * <p>设为 false（pointer-events:none 语义）时，hit-test 跳过本节点作为
     * 「叶命中目标」，命中穿透到父节点；但本节点的子节点仍可命中，且子节点命中时
     * 本节点仍作为结构锚点出现在命中链路径中。用于复合控件中纯装饰子节点
     * （标签文字/图标），使命中穿透到控件根节点（交互单元）——见控件契约 R6。</p>
     *
     * @param hitTestable 是否参与命中测试，false 表示命中穿透
     */
    public void setHitTestable(boolean hitTestable) {
        // ★ 去重但绝不标脏：hitTestable 不影响 layout/paint/composite（与 setCursor 同例外）
        if (this.hitTestable == hitTestable) return;
        this.hitTestable = hitTestable;
    }

    /** @return 是否参与命中测试，默认 true */
    public boolean isHitTestable() {
        return hitTestable;
    }

    // ==================== flex 布局属性访问器（LAYOUT 级） ====================

    /**
     * 设置 flex 主轴方向。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfLayout()}
     * （主轴方向改变子节点排布，属 LAYOUT 级失效）。</p>
     *
     * @param flexDirection 主轴方向，不应为 null
     */
    public void setFlexDirection(FlexDirection flexDirection) {
        if (this.flexDirection == flexDirection) return;
        this.flexDirection = flexDirection;
        markSelfLayout();
    }

    /** @return 当前 flex 主轴方向，默认 {@link FlexDirection#COLUMN} */
    public FlexDirection getFlexDirection() {
        return flexDirection;
    }

    /** @return 当前 flex-grow 权重，默认 0 */
    public int getFlexGrow() {
        return flexGrow;
    }

    /**
     * 设置 flex-grow 权重。去重 + 标 selfLayout（绝不向下递归，守 I7）。
     *
     * <p>COLUMN 主轴下：flexGrow=0 不分配；>0 按 weight 比例一次性下传 tight 高约束。
     * fillParentHeight==true 时若 flexGrow=0，被求解器视为隐式 flexGrow=1（向后兼容）。</p>
     *
     * @param flexGrow flex-grow 权重，非负整数，0 表示不参与剩余空间分配
     */
    public void setFlexGrow(int flexGrow) {
        if (this.flexGrow == flexGrow) return;
        this.flexGrow = flexGrow;
        markSelfLayout();
    }

    /**
     * 设置四向内边距（像素）。
     *
     * <p>任一边发生变化即视为变化：四边全相等则直接 return（去重），
     * 否则更新并调用 {@link #markSelfLayout()}（内边距改变盒模型可用空间，属 LAYOUT 级）。</p>
     *
     * @param top    上内边距
     * @param right  右内边距
     * @param bottom 下内边距
     * @param left   左内边距
     */
    public void setPadding(int top, int right, int bottom, int left) {
        if (this.paddingTop == top && this.paddingRight == right
            && this.paddingBottom == bottom && this.paddingLeft == left) {
            return;
        }
        this.paddingTop = top;
        this.paddingRight = right;
        this.paddingBottom = bottom;
        this.paddingLeft = left;
        markSelfLayout();
    }

    /**
     * 设置四向相等的内边距（便捷重载）。
     *
     * @param all 四边统一的内边距值
     */
    public void setPadding(int all) {
        setPadding(all, all, all, all);
    }

    /** @return 上内边距（像素），默认 0 */
    public int getPaddingTop() {
        return paddingTop;
    }

    /** @return 右内边距（像素），默认 0 */
    public int getPaddingRight() {
        return paddingRight;
    }

    /** @return 下内边距（像素），默认 0 */
    public int getPaddingBottom() {
        return paddingBottom;
    }

    /** @return 左内边距（像素），默认 0 */
    public int getPaddingLeft() {
        return paddingLeft;
    }

    /**
     * 设置四向外边距（像素）。
     *
     * <p>margin 是子节点的外边距，在父容器的布局空间里占用（CSS box model 语义）。
     * 任一边发生变化即视为变化：四边全相等则直接 return（去重），
     * 否则更新并调用 {@link #markSelfLayout()}（外边距改变子在父容器内的占用空间，属 LAYOUT 级）。</p>
     *
     * <p><b>I7 不变量</b>：只调用本节点 {@code markSelfLayout()}（脏向上冒泡），
     * 绝不触碰子节点、绝不向下递归标脏。margin 的实际消费由父容器布局协作者
     * （SizingCalculator / ConstraintResolver / FlexLayouter）在读子 margin 时完成。</p>
     *
     * @param top    上外边距
     * @param right  右外边距
     * @param bottom 下外边距
     * @param left   左外边距
     */
    public void setMargin(int top, int right, int bottom, int left) {
        if (this.marginTop == top && this.marginRight == right
            && this.marginBottom == bottom && this.marginLeft == left) {
            return;
        }
        this.marginTop = top;
        this.marginRight = right;
        this.marginBottom = bottom;
        this.marginLeft = left;
        markSelfLayout();
    }

    /**
     * 设置四向相等的外边距（便捷重载）。
     *
     * @param all 四向统一的外边距值
     */
    public void setMargin(int all) {
        setMargin(all, all, all, all);
    }

    /** @return 上外边距（像素），默认 0 */
    public int getMarginTop() {
        return marginTop;
    }

    /** @return 右外边距（像素），默认 0 */
    public int getMarginRight() {
        return marginRight;
    }

    /** @return 下外边距（像素），默认 0 */
    public int getMarginBottom() {
        return marginBottom;
    }

    /** @return 左外边距（像素），默认 0 */
    public int getMarginLeft() {
        return marginLeft;
    }

    /**
     * 垂直方向外边距合计（marginTop + marginBottom），供布局协作者计算
     * 子在父容器主轴/交叉轴的占用高度时使用。
     *
     * @return marginTop + marginBottom
     */
    public int marginV() {
        return marginTop + marginBottom;
    }

    /**
     * 水平方向外边距合计（marginLeft + marginRight），供布局协作者计算
     * 子在父容器主轴/交叉轴的占用宽度时使用。
     *
     * @return marginLeft + marginRight
     */
    public int marginH() {
        return marginLeft + marginRight;
    }

    /**
     * 设置子节点之间的主轴间距。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfLayout()}
     * （间距改变子节点排布，属 LAYOUT 级）。</p>
     *
     * @param gap 主轴间距（像素），非负
     */
    public void setGap(int gap) {
        if (this.gap == gap) return;
        this.gap = gap;
        markSelfLayout();
    }

    /** @return 当前主轴间距（像素），默认 0 */
    public int getGap() {
        return gap;
    }

    /**
     * 设置主轴对齐方式。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfLayout()}
     * （对齐方式改变子节点分布，属 LAYOUT 级）。</p>
     *
     * @param mainAxisAlign 主轴对齐方式，不应为 null
     */
    public void setMainAxisAlign(MainAxisAlign mainAxisAlign) {
        if (this.mainAxisAlign == mainAxisAlign) return;
        this.mainAxisAlign = mainAxisAlign;
        markSelfLayout();
    }

    /** @return 当前主轴对齐方式，默认 {@link MainAxisAlign#START} */
    public MainAxisAlign getMainAxisAlign() {
        return mainAxisAlign;
    }

    /**
     * 设置交叉轴对齐方式。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfLayout()}
     * （对齐方式改变子节点在交叉轴上的尺寸/位置，属 LAYOUT 级）。</p>
     *
     * @param crossAxisAlign 交叉轴对齐方式，不应为 null
     */
    public void setCrossAxisAlign(CrossAxisAlign crossAxisAlign) {
        if (this.crossAxisAlign == crossAxisAlign) return;
        this.crossAxisAlign = crossAxisAlign;
        markSelfLayout();
    }

    /** @return 当前交叉轴对齐方式，默认 {@link CrossAxisAlign#STRETCH} */
    public CrossAxisAlign getCrossAxisAlign() {
        return crossAxisAlign;
    }

    /**
     * 设置子级交叉轴对齐覆盖。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfLayout()}
     * （align-self 改变本子节点在父容器交叉轴上的尺寸/位置，属 LAYOUT 级）。
     * 注意：本标记只标自身 selfLayoutDirty + 向上冒泡 descendantLayoutDirty，
     * 不向下递归触碰后代（守 I7）。</p>
     *
     * @param alignSelf 子级交叉轴对齐覆盖，不应为 null
     */
    public void setAlignSelf(AlignSelf alignSelf) {
        if (this.alignSelf == alignSelf) return;
        this.alignSelf = alignSelf;
        markSelfLayout();
    }

    /** @return 当前子级交叉轴对齐覆盖，默认 {@link AlignSelf#AUTO} */
    public AlignSelf getAlignSelf() {
        return alignSelf;
    }

    // ==================== 绘制属性访问器（PAINT 级） ====================

    /**
     * 设置边框颜色（ARGB）。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfPaint()}。
     * 第 0 段裁决：边框不占布局空间，只改绘制输出，绝不标 LAYOUT。</p>
     *
     * @param borderColor ARGB 颜色值，0 表示无边框
     */
    public void setBorderColor(int borderColor) {
        if (this.borderColor == borderColor) return;
        this.borderColor = borderColor;
        markSelfPaint();
    }

    /** @return 当前边框颜色（ARGB），默认 0（无边框） */
    public int getBorderColor() {
        return borderColor;
    }

    /**
     * 设置边框宽度（像素）。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfPaint()}。
     * 第 0 段裁决：边框不占布局空间（box-sizing: border-box 简化），只标 PAINT。</p>
     *
     * @param borderWidth 边框宽度（像素），非负，0 表示无边框
     */
    public void setBorderWidth(int borderWidth) {
        if (this.borderWidth == borderWidth) return;
        this.borderWidth = borderWidth;
        markSelfPaint();
    }

    /** @return 当前边框宽度（像素），默认 0 */
    public int getBorderWidth() {
        return borderWidth;
    }

    /**
     * 设置圆角半径（像素）。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfPaint()}
     * （圆角只改绘制输出，不改盒模型尺寸）。</p>
     *
     * @param cornerRadius 圆角半径（像素），非负，0 表示直角
     */
    public void setCornerRadius(int cornerRadius) {
        if (this.cornerRadius == cornerRadius) return;
        this.cornerRadius = cornerRadius;
        markSelfPaint();
    }

    /** @return 当前圆角半径（像素），默认 0 */
    public int getCornerRadius() {
        return cornerRadius;
    }

    /**
     * 设置是否裁剪超出本节点边界的子节点绘制。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfPaint()}
     * （裁剪只改绘制输出，不改盒模型尺寸）。</p>
     *
     * @param clipChildren true 表示裁剪超出边界的子节点绘制
     */
    public void setClipChildren(boolean clipChildren) {
        if (this.clipChildren == clipChildren) return;
        this.clipChildren = clipChildren;
        markSelfPaint();
    }

    /** @return 是否裁剪子节点绘制，默认 false */
    public boolean isClipChildren() {
        return clipChildren;
    }

    /**
     * 设置文本颜色（ARGB）。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfPaint()}。
     * <b>注意：只标 PAINT，绝不像 {@link #setText} 那样标 LAYOUT+PAINT——
     * 文本颜色变化不改文字尺寸，故不触发布局失效。</b></p>
     *
     * @param textColor ARGB 颜色值
     */
    public void setTextColor(int textColor) {
        if (this.textColor == textColor) return;
        this.textColor = textColor;
        markSelfPaint();
    }

    /** @return 当前文本颜色（ARGB），默认 0xFFFFFFFF（白色） */
    public int getTextColor() {
        return textColor;
    }

    /**
     * 设置文本在布局盒内的垂直对齐方式。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfPaint()}。
     * <b>注意：PAINT 级属性，只影响文本绘制偏移，不影响布局盒尺寸。</b></p>
     *
     * @param textVerticalAlign 文本垂直对齐方式，不可为 null
     */
    public void setTextVerticalAlign(TextVerticalAlign textVerticalAlign) {
        if (textVerticalAlign == null) {
            throw new IllegalArgumentException("TextVerticalAlign 不可为 null");
        }
        if (this.textVerticalAlign == textVerticalAlign) return;
        this.textVerticalAlign = textVerticalAlign;
        markSelfPaint();
    }

    /** @return 当前文本垂直对齐方式，默认 {@link TextVerticalAlign#CENTER} */
    public TextVerticalAlign getTextVerticalAlign() {
        return textVerticalAlign;
    }

    /**
     * 设置文本在布局盒内的水平对齐方式。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfPaint()}。
     * <b>注意：PAINT 级属性，只影响文本绘制偏移，不影响布局盒尺寸。</b></p>
     *
     * @param textHorizontalAlign 文本水平对齐方式，不可为 null
     */
    public void setTextHorizontalAlign(TextHorizontalAlign textHorizontalAlign) {
        if (textHorizontalAlign == null) {
            throw new IllegalArgumentException("TextHorizontalAlign 不可为 null");
        }
        if (this.textHorizontalAlign == textHorizontalAlign) return;
        this.textHorizontalAlign = textHorizontalAlign;
        markSelfPaint();
    }

    /** @return 当前文本水平对齐方式，默认 {@link TextHorizontalAlign#LEFT} */
    public TextHorizontalAlign getTextHorizontalAlign() {
        return textHorizontalAlign;
    }

    // ==================== 滚动属性访问器（scrollOffsetY=GEOMETRY 级；scrollable=LAYOUT 级） ====================

    /**
     * 设置纵向滚动偏移（像素）。
     *
     * <p>值不变则直接 return（去重），变化时<b>只调 {@link #markGeometryDirty()}</b>。</p>
     *
     * <p><b>失效级别铁律：仅 GEOMETRY，绝不标 LAYOUT/PAINT。</b>滚动只把内容整体平移显示，
     * 不改任何盒模型尺寸（非 LAYOUT，否则滚动即重排破 I7），不改任何绘制属性
     * （非 PAINT，否则后代 fragment 被无谓重生成污染 I8）。geometry 级让 paint 遍历下沉、
     * 复用 fragment、仅用新 offset 重新叠加坐标——这正是滚动「位置变、不重绘、不重排」的语义。
     * 详见 {@link #scrollOffsetY} 字段 javadoc。</p>
     *
     * <p><b>I7 不变量</b>：只调用本节点 {@code markGeometryDirty()}（脏向上冒泡），
     * 绝不触碰子节点、绝不向下递归标脏。</p>
     *
     * @param scrollOffsetY 纵向滚动偏移（像素），通常由控件 handler clamp 到 [0, maxScroll] 后写入
     */
    public void setScrollOffsetY(int scrollOffsetY) {
        if (this.scrollOffsetY == scrollOffsetY) return;
        this.scrollOffsetY = scrollOffsetY;
        markGeometryDirty();
    }

    /** @return 当前纵向滚动偏移（像素），默认 0 */
    public int getScrollOffsetY() {
        return scrollOffsetY;
    }

    /**
     * 设置是否为可纵向滚动的视口容器。
     *
     * <p>值不变则直接 return（去重），变化时调用 {@link #markSelfLayout()}
     * （scrollable 改变 viewport 高度计算语义，是布局输入，属 LAYOUT 级）。</p>
     *
     * <p>scrollable=true 时布局引擎将 viewport 自身高度<b>钉死为视口高</b>（忽略内容撑大逻辑），
     * 使 viewport 盒高固定、内容子树总高可超视口高——这是纵向滚动的前提。
     * 详见 {@link #scrollable} 字段 javadoc 与布局引擎 computeHeight。</p>
     *
     * @param scrollable true 表示本节点为可纵向滚动的视口容器
     */
    public void setScrollable(boolean scrollable) {
        if (this.scrollable == scrollable) return;
        this.scrollable = scrollable;
        markSelfLayout();
    }

    /** @return 是否为可纵向滚动的视口容器，默认 false */
    public boolean isScrollable() {
        return scrollable;
    }

    /**
     * 判定本节点是否为「裁剪窗口」——其后代超出本节点 LayoutBox 边界的部分
     * 在 paint 与 hit-test 两个阶段都应被裁掉。
     *
     * <p>语义 = {@link #isClipChildren()} || {@link #isScrollable()}。
     * paint 与 hit-test 必须共用此谓词，避免「视觉裁了但鼠标仍能命中」的口径分裂
     * （B3/I7 邻域缺口）。</p>
     *
     * @return true 表示本节点是裁剪窗口，paint 建 CLIP、hit-test 建 clip bounds
     */
    public boolean isClipWindow() {
        return clipChildren || scrollable;
    }

    // ==================== 只读探针（供单测断言，命名对齐项目 __ 前缀惯例） ====================

    /** @return 自身布局脏标记 */
    public boolean __isSelfLayoutDirty() {
        return selfLayoutDirty;
    }

    /** @return 后代布局路标 */
    public boolean __isDescendantLayoutDirty() {
        return descendantLayoutDirty;
    }

    /** @return 自身绘制脏标记 */
    public boolean __isSelfPaintDirty() {
        return selfPaintDirty;
    }

    /** @return 后代绘制路标 */
    public boolean __isDescendantPaintDirty() {
        return descendantPaintDirty;
    }

    /** @return 自身合成脏标记（opacity/transform 变化） */
    public boolean __isCompositeDirty() {
        return compositeDirty;
    }

    /** @return 后代合成路标 */
    public boolean __isDescendantCompositeDirty() {
        return descendantCompositeDirty;
    }

    /** @return 自身几何脏标记（位置/尺寸变化） */
    public boolean __isSelfGeometryDirty() {
        return selfGeometryDirty;
    }

    /** @return 后代几何路标 */
    public boolean __isDescendantGeometryDirty() {
        return descendantGeometryDirty;
    }

    /**
     * @return 子节点列表的不可变视图
     */
    public List<SceneNode> __getChildren() {
        return Collections.unmodifiableList(children);
    }

    /** @return 父节点，可能为 null */
    public SceneNode __getParent() {
        return parent;
    }

    // ==================== 子树节点数探针与重算（阶段 2 fork 决策铺路） ====================

    /**
     * @return 缓存的子树节点数（含自身），叶子=1。供测试断言与阶段 2 fork 决策读取。
     *         此值仅在 layout 后序遍历重算后保证正确；结构变化后、layout 前，
     *         此值为旧值（{@link #__isSubtreeCountDirty()}==true 时）。
     */
    public int __getCachedSubtreeNodeCount() {
        return cachedSubtreeNodeCount;
    }

    /**
     * @return 子树节点数缓存是否失效（结构变化已冒泡、layout 尚未重算）。
     *         供测试断言冒泡与清脏行为。
     */
    public boolean __isSubtreeCountDirty() {
        return subtreeCountDirty;
    }

    /**
     * 若子树节点数缓存失效，则基于已布局完成的子节点 count 重算本节点 count 并清脏。
     *
     * <p><b>调用时机契约</b>：必须在本节点后序遍历完成之后调用（即所有子节点的
     * {@code cachedSubtreeNodeCount} 已是最新值）。{@code SceneLayoutEngine.layoutInternal}
     * 在后序遍历返回前调用此方法，天然满足契约。</p>
     *
     * <p>重算公式：{@code sum = 1（含自身）+ 各子节点 cachedSubtreeNodeCount 之和}。
     * O(children) 加法，复用后序遍历已访问的子节点列表，零额外遍历。
     * 若 {@link #subtreeCountDirty}==false 则直接 return（干净帧零开销，守 I7）。</p>
     *
     * <p><b>无 setter 纪律</b>：{@code cachedSubtreeNodeCount} 字段只由此方法写入，
     * 绝不外部设，保证 count 维护逻辑单一权威源。</p>
     */
    public void __recomputeSubtreeCountIfDirty() {
        if (!subtreeCountDirty) {
            return;
        }
        int sum = 1; // 含自身
        for (SceneNode child : children) {
            sum += child.cachedSubtreeNodeCount;
        }
        cachedSubtreeNodeCount = sum;
        subtreeCountDirty = false;
    }
}
