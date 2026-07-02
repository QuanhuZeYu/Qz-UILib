package club.heiqi.uilib.ui.scene.control;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneScrollContainer —— 滚动容器高阶工厂，一行建出「viewport + content + 可选 scrollbar」
 * 的标准滚动结构，封装 {@link SceneScrolls#attach} + {@link SceneScrollbar#create} 样板。
 *
 * <h3>定位：易用性工厂（非控件）</h3>
 * <p>本工厂只负责把滚动结构的 5 步样板（建 container/viewport/content、attach、可选建 scrollbar）
 * 收敛为一处，消除调用方手写滚动容器的复发风险。工厂<b>不持有状态、不维护滚动位置</b>——
 * 滚动位置唯一权威源是返回的 {@code scrollSignal}（由 {@link SceneScrolls#attach} 创建）。</p>
 *
 * <h3>结构</h3>
 * <pre>
 * container (ROW, fillParentHeight)
 *   ├─ viewport (COLUMN, fillParentHeight, flexGrow=1, scrollable=true, clipChildren, padding/gap/bg/radius)
 *   │     └─ content (COLUMN, gap)   ← 调用方挂实际内容到这里
 *   └─ scrollbarColumn (SceneScrollbar, 可选)   ← scrollbarSpec != null 时建
 * </pre>
 *
 * <h3>★ 边界：viewport 高度仍需调用方在布局链上保证</h3>
 * <p>工厂<b>无法</b>替调用方解决父链高度问题。container 设了 {@code fillParentHeight}，
 * 但若 container 的父节点本身高度不确定（无确定高约束下传），container 高度仍会被内容撑大，
 * 进而 viewport 也被撑大、{@code maxScrollY == 0}、滚动无效。<b>调用方必须确保 container 的
 * 父链在某处给出确定高</b>（如 container 的父是 fill/grow/percent 容器且收到确定高约束，
 * 或 container 的父给 container 设了 preferredHeight）。工厂只保证 viewport 自身
 * {@code scrollable=true} + {@code flexGrow=1}，使 viewport 在 container 高度确定时
 * 收到确定高约束、不被内容撑大。</p>
 *
 * <h3>守不变量</h3>
 * <ul>
 *   <li><b>I1 signal-first</b>：滚动位置经 scrollSignal 驱动，不命令式写节点。</li>
 *   <li><b>I7 GEOMETRY 级滚动</b>：viewport {@code scrollable=true}，滚动不重排。</li>
 *   <li><b>R1 纯静态工厂</b>：零实例字段，无状态。</li>
 * </ul>
 */
public final class SceneScrollContainer {

    /**
     * 纯静态工厂，禁止实例化（强制无状态）。
     */
    private SceneScrollContainer() {
    }

    /**
     * 滚动条规格 —— 非 null 时工厂建 scrollbar 并挂到 container 右侧；null 时不建滚动条。
     *
     * @param trackColor           轨道背景色（ARGB），0 表示透明轨道
     * @param thumbColor           滑块背景色（ARGB）
     * @param barWidth             滚动条宽度（像素，建议 6-8）
     * @param minThumbHeight       滑块最小高度（像素，避免内容过多时滑块消失）
     */
    @Desugar
    public record ScrollbarSpec(
        int trackColor,
        int thumbColor,
        int barWidth,
        int minThumbHeight
    ) {
    }

    /**
     * 滚动容器输入契约 —— 纯常量 + 可选滚动条规格（契约 R2 允许常量）。
     *
     * @param padding        viewport 四向内边距（像素，0 = 无内边距）
     * @param gap            viewport 内子节点间距 + content 内子节点间距（像素）
     * @param backgroundColor viewport 背景色（ARGB），0 表示透明
     * @param cornerRadius   viewport 圆角（像素，0 = 直角）
     * @param scrollbarSpec  滚动条规格；null = 不建滚动条（仅 attach 滚动能力，无可视滚动条）
     */
    @Desugar
    public record Props(
        int padding,
        int gap,
        int backgroundColor,
        int cornerRadius,
        ScrollbarSpec scrollbarSpec
    ) {
    }

    /**
     * 滚动容器创建结果，暴露结构节点与滚动信号供调用方挂内容、观察位置。
     *
     * @param container     滚动容器根节点（ROW：viewport + 可选 scrollbar），调用方挂到自己的布局树
     * @param viewport      可滚动视口节点（已 setScrollable(true)，content 挂在其内）
     * @param content       内容容器节点（COLUMN，gap），调用方 appendChild 实际内容到这里
     * @param scrollSignal  滚动偏移信号（由 SceneScrolls.attach 创建，唯一滚动位置权威）
     */
    @Desugar
    public record Result(
        SceneNode container,
        SceneNode viewport,
        SceneNode content,
        Signal<Integer> scrollSignal
    ) {
    }

    /**
     * 工厂：构建标准滚动容器（viewport + content + 可选 scrollbar）。
     *
     * <p>封装步骤：
     * <ol>
     *   <li>建 container（ROW，fillParentHeight）—— 承载 viewport 与 scrollbar 列；</li>
     *   <li>建 viewport（COLUMN，fillParentHeight，flexGrow=1，setScrollable(true)，clipChildren，
     *       padding/gap/bg/radius）—— 可滚动视口，高度由 container 分配；</li>
     *   <li>建 content（COLUMN，gap）→ appendChild 到 viewport —— 调用方挂实际内容到这里；</li>
     *   <li>{@link SceneScrolls#attach} 附加滚动能力 → scrollSignal；</li>
     *   <li>若 scrollbarSpec != null：{@link SceneScrollbar#create} 建滚动条列 → appendChild 到 container；</li>
     *   <li>viewport appendChild 到 container（viewport 在左、scrollbar 在右）。</li>
     * </ol>
     * </p>
     *
     * <p><b>★ 边界提醒</b>：viewport 高度仍需调用方在布局链上保证（container 的父必须给确定高），
     * 工厂无法替调用方解决父链高度问题。详见类 Javadoc「边界」段。</p>
     *
     * @param rt    场景运行时
     * @param props 输入契约
     * @return 创建结果（container + viewport + content + scrollSignal）
     */
    public static Result create(SceneRuntime rt, Props props) {
        // 1. container（ROW，fillParentHeight）
        SceneNode container = SceneNode.row();
        container.setFillParentHeight(true);

        // 2. viewport（COLUMN，fillParentHeight，flexGrow=1，scrollable，clipChildren，padding/gap/bg/radius）
        SceneNode viewport = SceneNode.column();
        viewport.setFillParentHeight(true);
        viewport.setFlexGrow(1);
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        if (props.padding() > 0) {
            viewport.setPadding(props.padding());
        }
        viewport.setGap(props.gap());
        if (props.backgroundColor() != 0) {
            viewport.setBackgroundColor(props.backgroundColor());
        }
        if (props.cornerRadius() > 0) {
            viewport.setCornerRadius(props.cornerRadius());
        }

        // 3. content（COLUMN，gap）→ appendChild 到 viewport
        SceneNode content = SceneNode.column();
        content.setGap(props.gap());
        viewport.appendChild(content);

        // 4. attach 滚动能力 → scrollSignal
        Signal<Integer> scrollSignal = SceneScrolls.attach(rt, viewport);

        // 5. viewport appendChild 到 container（viewport 在左）
        container.appendChild(viewport);

        // 6. 若 scrollbarSpec != null：建 scrollbar 列 → appendChild 到 container（scrollbar 在右）
        if (props.scrollbarSpec() != null) {
            ScrollbarSpec spec = props.scrollbarSpec();
            SceneScrollbar.Props sbProps = new SceneScrollbar.Props(
                    viewport, scrollSignal, scrollSignal::set,
                    spec.trackColor(), spec.thumbColor(),
                    spec.barWidth(), spec.minThumbHeight());
            SceneScrollbar.Result sb = SceneScrollbar.create(rt, sbProps);
            container.appendChild(sb.column());
        }

        return new Result(container, viewport, content, scrollSignal);
    }

    // ==================== attach 门面：一行建带 bar 滚动容器并挂到 parent ====================
    //
    // 设计目标：对齐 Compose Box+align / Flutter Scrollbar 模式——「一行建带可视滚动条的
    // 滚动容器并挂到 parent」。8 参 Props 全部工厂内消化，调用方零 Props 知识门槛。
    //
    // 三层分工：
    //   attach（一行门面） ↑ create（中层 Result） ↑ SceneScrollbar（自管 8 参）
    //
    // P0：scrollbar 内部直接订阅 rt.layoutDoneSignal()，host 桥接 epoch 驱动 thumb 几何更新，
    // 调用方无需传 layout 完成通知。

    /**
     * 一行建带可视滚动条的滚动容器并挂到 parent（默认带 bar，对齐 Compose Box+align 模式）。
     *
     * <p>内部建 container(ROW) + viewport(scrollable,clip) + content + scrollbar(兄弟)。
     * 8 参 Props 全部工厂内消化，调用方零 Props 知识门槛。</p>
     *
     * <p>scrollbar 内部直接订阅 {@link SceneRuntime#layoutDoneSignal()}，host 桥接 epoch 驱动
     * thumb 几何更新，调用方无需传 layout 完成通知。</p>
     *
     * <p>container 自动设 flexGrow=1，使其在 COLUMN 父中撑满剩余高（viewport 高度确定的必要条件）。</p>
     *
     * @param rt                   场景运行时
     * @param parent               容器挂载目标
     * @param contentBuilder       content 装填回调（往 content 里 appendChild）
     * @return container（已挂到 parent，ROW + flexGrow=1），供调用方做后续布局微调
     */
    public static SceneNode attach(
            SceneRuntime rt,
            SceneNode parent,
            Consumer<SceneNode> contentBuilder) {
        Props props = new Props(0, 0, 0, 0, defaultScrollbarSpec());
        Result r = create(rt, props);
        contentBuilder.accept(r.content());
        r.container().setFlexGrow(1);
        parent.appendChild(r.container());
        return r.container();
    }

    /**
     * 带样式的完整形态（暴露 padding/gap/bg/radius，bar 默认）。
     *
     * <p>语义与 {@link #attach(SceneRuntime, SceneNode, Consumer)} 一致，
     * 仅额外透传 viewport 的 padding/gap/backgroundColor/cornerRadius 四个外观参。</p>
     *
     * @param rt                   场景运行时
     * @param parent               容器挂载目标
     * @param padding              viewport 四向内边距（像素，0 = 无内边距）
     * @param gap                  viewport 内 + content 内子节点间距（像素）
     * @param backgroundColor      viewport 背景色（ARGB），0 表示透明
     * @param cornerRadius         viewport 圆角（像素，0 = 直角）
     * @param contentBuilder       content 装填回调
     * @return container（已挂到 parent，ROW + flexGrow=1）
     */
    public static SceneNode attach(
            SceneRuntime rt, SceneNode parent,
            int padding, int gap, int backgroundColor, int cornerRadius,
            Consumer<SceneNode> contentBuilder) {
        Props props = new Props(padding, gap, backgroundColor, cornerRadius,
                defaultScrollbarSpec());
        Result r = create(rt, props);
        contentBuilder.accept(r.content());
        r.container().setFlexGrow(1);
        parent.appendChild(r.container());
        return r.container();
    }

    /**
     * 关闭 bar 的变体：裸滚动，无可视 scrollbar（等价于裸 SceneScrolls.attach 但走工厂结构）。
     *
     * <p>结构与 {@link #attach} 一致（container + viewport + content），仅 scrollbarSpec=null。
     * 适用于不需要可视滚动条但仍需滚动能力的场景。</p>
     *
     * @param rt             场景运行时
     * @param parent         容器挂载目标
     * @param contentBuilder content 装填回调
     * @return container（已挂到 parent，ROW + flexGrow=1）
     */
    public static SceneNode attachNoBar(
            SceneRuntime rt, SceneNode parent, Consumer<SceneNode> contentBuilder) {
        Props props = new Props(0, 0, 0, 0, null);
        Result r = create(rt, props);
        contentBuilder.accept(r.content());
        r.container().setFlexGrow(1);
        parent.appendChild(r.container());
        return r.container();
    }

    /**
     * 一行建带滚动条 keyed 列表（无 keyFn，引用做 key）。对齐 Slint ListView 零配置。
     *
     * <p>内部建 container+viewport+scrollbar+forEach，作者只传数据 signal + item 组件。
     * 适用于稳定对象实例列表；值语义/重复引用场景须用 {@link #scrollList(SceneRuntime, SceneNode,
     * ReadableSignal, Function, Function)} 带 keyFn 重载。</p>
     *
     * @param rt            场景运行时
     * @param parent        container 挂载目标
     * @param itemsSignal   数据列表 signal
     * @param itemComponent 每项→SceneNode 的组件函数
     * @return container（已挂 parent，flexGrow=1）
     */
    public static <T> SceneNode scrollList(SceneRuntime rt, SceneNode parent,
                                           ReadableSignal<? extends List<T>> itemsSignal,
                                           Function<? super T, SceneNode> itemComponent) {
        Result r = create(rt, new Props(0, 0, 0, 0, defaultScrollbarSpec()));
        rt.forEach(r.content(), itemsSignal, itemComponent);
        r.container().setFlexGrow(1);
        parent.appendChild(r.container());
        return r.container();
    }

    /**
     * 带 keyFn 重载（值语义列表用）。
     *
     * @param rt            场景运行时
     * @param parent        container 挂载目标
     * @param itemsSignal   数据列表 signal
     * @param keyFn         项→唯一 key 的映射
     * @param itemComponent 每项→SceneNode 的组件函数
     * @return container（已挂 parent，flexGrow=1）
     */
    public static <T> SceneNode scrollList(SceneRuntime rt, SceneNode parent,
                                           ReadableSignal<? extends List<T>> itemsSignal,
                                           Function<? super T, ?> keyFn,
                                           Function<? super T, SceneNode> itemComponent) {
        Result r = create(rt, new Props(0, 0, 0, 0, defaultScrollbarSpec()));
        rt.forEach(r.content(), itemsSignal, keyFn, itemComponent);
        r.container().setFlexGrow(1);
        parent.appendChild(r.container());
        return r.container();
    }

    /**
     * 默认 scrollbar 规格：复用 {@link SceneScrollbar} 的 4 个 DEFAULT_* 常量（颜色/宽度/最小 thumb 高）。
     *
     * @return 默认 scrollbar 规格
     */
    private static ScrollbarSpec defaultScrollbarSpec() {
        return new ScrollbarSpec(
                SceneScrollbar.DEFAULT_TRACK_COLOR,
                SceneScrollbar.DEFAULT_THUMB_COLOR,
                SceneScrollbar.DEFAULT_BAR_WIDTH,
                SceneScrollbar.DEFAULT_MIN_THUMB_HEIGHT);
    }
}
