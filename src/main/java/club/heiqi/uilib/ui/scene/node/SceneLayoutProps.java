package club.heiqi.uilib.ui.scene.node;

import club.heiqi.uilib.ui.scene.layout.AlignSelf;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;

/**
 * SceneNode 布局/滚动属性值容器。
 *
 * <p>本类只保存字段值，不持有 {@link SceneNode} 引用，不做去重判断，不打脏标记。</p>
 */
final class SceneLayoutProps {

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
    boolean fillParentHeight;

    /**
     * 是否填充父容器宽度。
     *
     * <p>默认 false：宽度由现有宽度决策决定（容器 fill / 文本叶 shrink-to-fit /
     * 无文本叶 fill）。设为 true 时，在 ROW 主轴求解器中作为隐式 grow 权重 1 的桥
     * （与 fillParentHeight 在 COLUMN 主轴的语义对称），使该子在 ROW 容器
     * 有剩余宽度时参与主轴 grow 分配。显式 flexGrow&gt;0 时以 flexGrow 为准。</p>
     *
     * <p><b>对称说明</b>：与 fillParentHeight 形成两轴对称——COLUMN 主轴（高）由
     * fillParentHeight 桥接隐式 grow，ROW 主轴（宽）由 fillParentWidth 桥接隐式 grow。
     * 还清原 ROW/COLUMN 不对称偏离（见 NORTH_STAR 偏离登记 2026-06-30）。</p>
     */
    boolean fillParentWidth;

    /**
     * 首选高度（像素），供无文本/无子节点的叶节点显式指定最小高度。
     *
     * <p>默认 0：回退到内容高度（文本行高或 0）。设非零值时，布局引擎对叶节点
     * 取 {@code Math.max(textHeight, preferredHeight)}，确保背景矩形/hit-test
     * 区域有足够高度。不影响容器节点（容器高度由子节点累加决定）。</p>
     */
    int preferredHeight;

    /**
     * 首选宽度（像素），供节点显式指定盒宽（最终外尺寸，含 padding）。
     *
     * <p>默认 0：不约束，回退到现有宽度决策（容器 fill / 文本叶 shrink-to-fit /
     * 无文本叶 fill）。设非零值时，布局引擎在 {@code computeWidth} 中以最高优先级
     * 直接返回该值，压过容器 fill、文本 shrink-to-fit、无文本 fill 三种现有决策。
     * 与 preferredHeight 对称，语义为「最终盒外尺寸（含 padding）」，
     * 与 {@code LayoutBox.width} 一致。</p>
     */
    int preferredWidth;

    /**
     * 最大高度（像素，外尺寸含 padding），0 = 无上界。
     *
     * <p>声明式元数据，父级 grow 求解器先验可读。非 scrollable 路径下，
     * {@code computeHeight} 出口对最终高做 {@code min(h, maxHeight)} clamp；
     * grow 求解器在 freeze do-while 中将撞顶子冻结到 maxHeight 并把剩余空间
     * 回流到未冻结兄弟。与 preferredHeight 对称：preferredHeight 作下界、
     * maxHeight 作上界，矛盾时 preferredHeight 赢（下限优先）。</p>
     */
    int maxHeight;

    /**
     * 最大宽度（像素，外尺寸含 padding），0 = 无上界。声明式元数据。
     *
     * <p>与 maxHeight 对称。{@code computeWidth} 在 preferredWidth 显式钉死
     * 分支不 clamp（preferredWidth 优先级最高），其余分支返回前做
     * {@code min(w, maxWidth)} clamp。</p>
     */
    int maxWidth;

    /**
     * 高度百分比（0-100，0 = 不启用）。相对父先验内高
     * （{@code priorKnownInnerHeight(parent)}），即 {@code childHeight = parentInnerH * pct / 100}。
     *
     * <p><b>仅在 COLUMN 主轴生效</b>：percentHeight 只在父容器 flexDirection==COLUMN 的
     * grow 求解器里被识别为"percent 子作固定子"。ROW 下 percentHeight 不生效——ROW 主轴是宽，
     * 高是交叉轴，percentHeight 子在 ROW 下被当作普通 fill 子处理（由 crossAxisAlign/STRETCH
     * 决定高），percentHeight 字段被忽略。</p>
     *
     * <p><b>fallback</b>：父高不可先验（Constraints.UNCONSTRAINED）时 percentHeight 失效，
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
    int percentHeight;

    /**
     * 宽度百分比（0-100，0 = 不启用）。相对<b>子可用宽</b>
     * （{@code constraints.getAvailableWidth()}，即父内宽 - 子 marginH），
     * 即 {@code childWidth = (parentInnerW - childMarginH) * pct / 100}。
     *
     * <p><b>fallback</b>：无宽约束（Constraints.UNCONSTRAINED）时 percentWidth 失效，
     * 回退 shrink-to-fit（忽略 percent，走自然宽）。</p>
     *
     * <p>优先级位于 preferredWidth 之后、SHRINK/文本 shrink/fill 之前：preferredWidth 仍最高优先级，
     * percentWidth 仅在无 preferredWidth 且有宽约束时生效。</p>
     */
    int percentWidth;

    /**
     * 容器宽度策略，默认 {@link SceneNode.WidthSizing#FILL}。
     *
     * <p>默认 fill 保持历史行为零回归；需要内容驱动宽度的容器可显式设为
     * {@link SceneNode.WidthSizing#SHRINK}。</p>
     */
    SceneNode.WidthSizing widthSizing = SceneNode.WidthSizing.FILL;

    /**
     * flex 主轴方向，默认 {@link FlexDirection#COLUMN}。
     *
     * <p>默认 COLUMN 保证不设置时与现有引擎垂直堆叠行为一致（零回归）。
     * 改变主轴方向会改变子节点排布，属 LAYOUT 级失效。</p>
     */
    FlexDirection flexDirection = FlexDirection.COLUMN;

    /**
     * flex-grow 权重（LAYOUT 级）。COLUMN 主轴下：默认 0 不参与剩余空间分配；
     * &gt;0 时按权重分得 freeH 份额。fillParentHeight 在 COLUMN 主轴等价 flexGrow=1，
     * 显式 flexGrow&gt;0 时以 flexGrow 为准。int 精度，余数补末位 grow 子（Qt 语义）。
     */
    int flexGrow;

    /** 内边距：上，默认 0 */
    int paddingTop;

    /** 内边距：右，默认 0 */
    int paddingRight;

    /** 内边距：下，默认 0 */
    int paddingBottom;

    /** 内边距：左，默认 0 */
    int paddingLeft;

    /** 上外边距（像素），子节点在父容器内占用的上方空间。 */
    int marginTop;

    /** 右外边距（像素）。 */
    int marginRight;

    /** 下外边距（像素）。 */
    int marginBottom;

    /** 左外边距（像素）。 */
    int marginLeft;

    /** 子节点之间的主轴间距，默认 0 */
    int gap;

    /**
     * 主轴对齐方式，默认 {@link MainAxisAlign#START}。
     *
     * <p>默认 START 保证不设置时子节点靠主轴起点堆叠，与现有行为一致。</p>
     */
    MainAxisAlign mainAxisAlign = MainAxisAlign.START;

    /**
     * 交叉轴对齐方式，默认 {@link CrossAxisAlign#STRETCH}。
     *
     * <p>默认 STRETCH 保证不设置时子节点在交叉轴上拉伸填满父容器，
     * 与现有引擎"子节点宽度填满父宽"行为一致（零回归）。</p>
     */
    CrossAxisAlign crossAxisAlign = CrossAxisAlign.STRETCH;

    /**
     * 子级交叉轴对齐覆盖，默认 {@link AlignSelf#AUTO}。
     *
     * <p>语义对齐 CSS {@code align-self}：非 AUTO 时覆盖父容器 crossAxisAlign
     * 对本子节点的设置；AUTO 时回退父级。默认 AUTO 保证不设置时与现有引擎行为一致（零回归）。
     * 有效对齐解析集中在 {@code FlexLayouter.effectiveCrossAlign}。</p>
     */
    AlignSelf alignSelf = AlignSelf.AUTO;

    /**
     * 纵向滚动偏移（像素），默认 0。
     *
     * <p><b>失效级别：GEOMETRY（几何级）。</b>SceneNode#setScrollOffsetY 去重后<b>只调
     * SceneNode#markGeometryDirty()</b>，绝不调 markSelfLayout 或 markSelfPaint。</p>
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
    int scrollOffsetY;

    /**
     * 横向滚动偏移（像素），默认 0。
     *
     * <p><b>失效级别：GEOMETRY（几何级）。</b>语义与 {@link #scrollOffsetY} 完全对称：
     * 只移动内容显示位置、不重排不重绘。绘制引擎对含 {@code scrollOffsetX != 0} 的节点
     * 在递归后代时注入 {@code -scrollOffsetX} 的 X 基准偏移（无独立 scrollableX 标志：
     * 纵向语义由 scrollable 承载视口高度决策，横向只需偏移注入，任何 clip 容器均可使用）。</p>
     */
    int scrollOffsetX;

    /**
     * 是否为可横向滚动的视口容器，默认 false。
     *
     * <p><b>失效级别：LAYOUT（布局级）。</b>SceneNode#setScrollableX 去重后调 SceneNode#markSelfLayout()。</p>
     *
     * <h3>为何 scrollableX 是 LAYOUT 级</h3>
     * <p>scrollableX 改变视口自身宽度决策（钉死为视口宽，不随内容撑大）并解耦子宽度约束
     * （子内容宽不再 clamp 到视口内宽），是布局输入，与 {@link #scrollable} 的高度语义对称。</p>
     */
    boolean scrollableX;

    /**
     * 是否为可纵向滚动的视口容器，默认 false。
     *
     * <p><b>失效级别：LAYOUT（布局级）。</b>SceneNode#setScrollable 去重后调 SceneNode#markSelfLayout()。</p>
     *
     * <h3>为何 scrollable 是 LAYOUT 级</h3>
     * <p>scrollable 改变 viewport 自身的高度计算语义：scrollable=true 时布局引擎
     * <b>不走</b> {@code max(natural, preferredHeight)} 的内容撑大逻辑，而是<b>直接钉死为视口高</b>
     * （主动忽略内容高，首次解耦 viewport/content）。这是一个布局输入（改变高度决策），
     * 故必须标 LAYOUT，使布局引擎重新计算 viewport 自身高度。</p>
     *
     * <p>横向滚动已提供 scrollOffsetX（GEOMETRY 级偏移注入，见上）；独立 scrollableX
     * 标志仍不实现（YAGNI：无横向视口尺寸语义需求）。contentSize/viewportSize/maxScroll
     * 全部派生不存（守 NORTH_STAR §6：新增缓存必须答出让哪层跳过什么重算，存这些答不上来）。</p>
     */
    boolean scrollable;
}
