package club.heiqi.uilib.ui.scene.control;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneRadioGroup —— scene 新栈控件层 Phase 4 批 2 首个迁移控件（单选组，VERTICAL）。
 *
 * <h3>定位：多选项单选受控控件范本（契约 R8 确立者）</h3>
 * <p>本控件确立「多选项单选受控零状态」契约 R8：带「N 选 1」语义的受控控件，当前选中项由外部
 * {@code selectedIndex} 只读 signal 唯一驱动；激活某选项时<b>只经 {@code onSelect.accept(targetIndex)}
 * 上抛期望选中项</b>，控件<b>绝不自己维护或修改 selectedIndex</b>。这是 R7 从二值布尔到 N 值下标的推广
 * ——同一灵魂（外部唯一源 + 期望值上抛），杜绝「内部选中态」与「外部 signal」双源（守 R1/R5/I11/R8）。</p>
 *
 * <h3>结构（VERTICAL only）</h3>
 * <pre>
 * root (COLUMN, crossAxisAlign=START, gap)                  ← 容器，非交互单元
 *   └─ option[i] (ROW + SHRINK 内容宽, crossAxisAlign=CENTER, gap, padding, cornerRadius, borderWidth)  ← 交互单元 hitTestable=true
 *         ├─ circle[i] (16×16, INDICATOR 配方: 圆角/边框/四态染色)  ← 装饰 hitTestable=false
 *         │     └─ dot[i] (8×8, 圆, 选中标记)                ← 装饰 hitTestable=false
 *         └─ label[i] (text)                                ← 装饰 hitTestable=false
 * </pre>
 * <p>option 默认 SHRINK，命中外轮廓收至 circle+gap+label+padding 内容宽，避免 FILL 透明行吞掉父宽。</p>
 *
 * <h3>选中表达：主题配方染色 + 透明背景而非 display:none（纯 PAINT 级零重排）</h3>
 * <p>dot 节点常驻占位，靠「选中且启用 → 主题强调底前景色，其余 → 透明」切换显隐，绝不增删节点
 * ——保证选中切换帧零重排（I7）。circle 的选中染色同样只改 tint，不动几何。</p>
 *
 * <h3>外观归属：表面绑定是 circle 的唯一写入者</h3>
 * <p>circle 的 background / borderColor / borderWidth / cornerRadius / backdrop /
 * surfaceElevation 全部由 {@link SceneSurfaceBinder} 从选中配方派生——配方来自
 * {@link SceneThemes#selectableSurface(SceneRuntime, SceneTheme.Role, ReadableSignal)}
 * 的 INDICATOR 角色：未选中取角色配方，选中态把 tint 的 RGB 换成主题强调色（保留原 alpha，
 * 故「选中」是色彩语义而非仅透明度），禁用态仍取角色禁用档。控件不再静态设 circle 边框宽/圆角，
 * 也不再叠加 {@code SceneStateColors.*Background} 与 {@code SceneControlChrome.bindStandardBorder}。</p>
 *
 * <p>dot 是控件自持的选中标记：取 {@link SceneThemes#onAccentForeground(SceneRuntime)}，
 * 未选中/禁用保持透明；label 前景取 {@link SceneThemes#foreground(SceneRuntime)} /
 * 禁用取 {@link SceneThemes#disabledForeground(SceneRuntime)}。option 行容器不参与表面采样
 * （避免逐行重复滤镜），仍保留静态 padding / 圆角 / 边框。</p>
 *
 * <h3>契约</h3>
 * <p>R1 纯静态工厂零实例字段 / R2 Props 只读 signal + 常量 + 回调 / R3 组件函数只执行一次 /
 * R4 外观随状态经 bind 派生 / R5 交互态读 interactionState / R6 装饰子节点命中穿透 /
 * R8 多选项单选受控零状态。</p>
 */
public final class SceneRadioGroup {

    /**
     * dot 未选中/禁用时颜色（全透明，纯 PAINT 切换不重排）
     */
    private static final int DOT_TRANSPARENT = 0x00000000;

    /**
     * circle 固定边长（像素）
     */
    private static final int CIRCLE_SIZE = 16;
    /**
     * dot 固定边长（像素）
     */
    private static final int DOT_SIZE = 8;
    /**
     * dot 圆角（像素，足够大使 dot 呈圆）。
     *
     * <p>circle 圆角不在此设值：它随表面绑定器从主题 INDICATOR 配方读取（默认档 8px，16×16 下即圆）
     * ——圆角属于配方，不属控件。</p>
     */
    private static final int DOT_RADIUS = SceneChromeTokens.RADIUS_PILL;
    /**
     * 边框宽度（像素）
     */
    private static final int BORDER_WIDTH = 1;
    /**
     * option 行圆角（像素）
     */
    private static final int OPTION_RADIUS = SceneChromeTokens.RADIUS_LG;
    /**
     * option 行内边距（像素）
     */
    private static final int OPTION_PADDING = SceneChromeTokens.PAD_SM;
    /**
     * option 行内 circle 与 label 间距（像素）
     */
    private static final int OPTION_GAP = SceneChromeTokens.GAP_SM;
    /**
     * 各 option 行之间的纵向间距（像素）
     */
    private static final int ITEM_GAP = SceneChromeTokens.GAP_SM;

    /**
     * 纯静态工厂，禁止实例化（强制无状态，契约 R1）
     */
    private SceneRadioGroup() {
    }

    /**
     * RadioGroup 输入契约 —— 多选项单选受控：当前选中项由外部只读 signal 驱动，
     * 激活经 onSelect 交还期望选中下标（契约 R2/R8）。
     *
     * @param selectedIndex 当前选中项下标（响应式只读，受控源），控件绝不自己修改此值
     * @param options       选项文本列表（构建期固定常量，R2 允许常量）
     * @param enabled       是否启用（响应式只读），false 时禁用点击/键盘并切灰态
     * @param onSelect      选择回调，激活某选项时以该项下标调用，由外部 set 回 selectedIndex signal
     */
    @Desugar
    public record Props(
        ReadableSignal<Integer> selectedIndex,
        List<String> options,
        ReadableSignal<Boolean> enabled,
        Consumer<Integer> onSelect
    ) {
    }

    /**
     * 工厂：构建 RadioGroup 组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（R3）：
     * 体内 for 循环建 N 个 option 节点（options 固定，循环建树无副作用、只跑一次，守 I3）。
     * 动态外观全落 {@code bind(computed(...))}，交互只经 {@code on} 调 {@code onSelect}（R4/R5/R8）。</p>
     *
     * @param rt    场景运行时
     * @param props RadioGroup 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneSingleSelectPrimitive.Props primitiveProps = new SceneSingleSelectPrimitive.Props(
                props.selectedIndex(),
                props.options(),
                props.enabled(),
                props.onSelect(),
                SceneSingleSelectPrimitive.Orientation.VERTICAL);
            SceneSingleSelectPrimitive.Result result = SceneSingleSelectPrimitive.create(rt, primitiveProps);
            result.root().setCrossAxisAlign(CrossAxisAlign.START);
            result.root().setGap(ITEM_GAP);

            // 主题语义色派生在构造期捕获一次（来源作用域），循环内所有选项共享同一派生信号。
            ReadableSignal<Integer> onAccentForeground = SceneThemes.onAccentForeground(rt);
            ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
            ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);
            ReadableSignal<Integer> borderDefault = SceneThemes.borderDefault(rt);

            for (SceneSingleSelectPrimitive.ItemHandle handle : result.items()) {
                SceneNode option = handle.item();
                option.setFlexDirection(FlexDirection.ROW);
                option.setCrossAxisAlign(CrossAxisAlign.CENTER);
                option.setGap(OPTION_GAP);
                option.setPadding(OPTION_PADDING);
                // option 行容器不参与表面采样（避免逐行重复滤镜），保留静态圆角/边框宽/padding；
                // 边框色改绑主题 borderDefault——不再静态取 SceneChromeTokens.BORDER_DEFAULT
                // （该值与深色档同值，浅色档下不会跟随）。
                option.setCornerRadius(OPTION_RADIUS);
                option.setBorderWidth(BORDER_WIDTH);
                rt.bind(borderDefault, option::setBorderColor);
                // 宽度收缩到内容：命中外轮廓收至 circle+gap+label+padding，避免 FILL 透明行吞父宽
                option.setWidthSizing(SceneNode.WidthSizing.SHRINK);

                SceneNode circle = SceneNode.row();
                circle.setCrossAxisAlign(CrossAxisAlign.CENTER);
                circle.setMainAxisAlign(MainAxisAlign.CENTER);
                circle.setPreferredWidth(CIRCLE_SIZE);
                circle.setPreferredHeight(CIRCLE_SIZE);
                // 边框宽/圆角/四态染色/滤镜/实体高度全部由表面绑定器从选中配方派生：
                // 构造期不再静态设 borderWidth/cornerRadius/borderColor，也不另绑状态色或标准边框。
                circle.setHitTestable(false);
                option.appendChild(circle);

                SceneNode dot = new SceneNode();
                dot.setPreferredWidth(DOT_SIZE);
                dot.setPreferredHeight(DOT_SIZE);
                dot.setCornerRadius(DOT_RADIUS);
                dot.setHitTestable(false);
                circle.appendChild(dot);

                option.appendChild(handle.label());

                SceneInteractionState interaction = handle.interaction();

                // circle 表面：INDICATOR 角色配方 + selected 派生（选中把 tint RGB 换强调色，禁用取禁用档）。
                // 唯一外观写入者，独占 background/border/borderWidth/cornerRadius/backdrop/surfaceElevation。
                ReadableSignal<SceneSurfaceStyle> surface =
                    SceneThemes.selectableSurface(rt, SceneTheme.Role.INDICATOR, handle.selected());
                SceneSurfaceBinder.bind(rt, circle, surface, props.enabled(), interaction);

                // dot：启用且选中取主题强调底前景色，其余（未选中/禁用）透明——禁用不显示选中标记，
                // 显隐切换仍是纯 PAINT 级。
                rt.bindComputed(() -> Boolean.TRUE.equals(props.enabled().get())
                        && Boolean.TRUE.equals(handle.selected().get())
                        ? onAccentForeground.get() : DOT_TRANSPARENT,
                    dot::setBackgroundColor);

                // label 前景：主题正文色，禁用取主题禁用前景色。
                rt.bindComputed(() -> Boolean.TRUE.equals(props.enabled().get())
                        ? foreground.get() : disabledForeground.get(),
                    handle.label()::setTextColor);

                SceneControlChrome.bindCursor(rt, option, props.enabled(), SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);
            }

            return result.root();
        };
    }
}
