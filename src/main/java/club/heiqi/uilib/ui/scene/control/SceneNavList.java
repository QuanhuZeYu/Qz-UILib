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
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneNavList —— scene 新栈纵向受控单选导航列表。
 *
 * <p>用于多分类导航场景（如配置页 &gt;5 section 时的左侧 navPane）。
 * 与 {@link SceneSegmented} 同构（N 选 1 受控，契约 R8），区别仅是纵向排列，
 * 导航项填满导航栏宽度，适合设置页分类。</p>
 *
 * <h3>结构</h3>
 * <pre>
 * root (COLUMN, gap)                                       ← 导航底座，承载 TOOLBAR 表面
 *   └─ item[i] (ROW, mainAxisAlign=START, crossAxisAlign=CENTER, padding, fillParentWidth)  ← 交互单元
 *         ├─ indicator[i] (non-hit, scaleY 随 selection 过渡)  ← 选中指示条
 *         └─ label[i] (text, hitTestable=false，selected 时平移 4px)
 * </pre>
 *
 * <h3>外观归属：底座 TOOLBAR + 每项 INDICATOR 选中配方，唯一写入者是表面绑定器</h3>
 * <p><b>底座</b>（primitive root）走 {@link SceneThemes#surface} 的 {@link SceneTheme.Role#TOOLBAR}
 * 配方：background/border/borderWidth/cornerRadius/backdrop/surfaceElevation 全归
 * {@link SceneSurfaceBinder}。旧的静态边框/圆角设值、{@code SceneStateColors.*Background} 绑定与
 * {@code SceneControlChrome.bindStandardBorder} 写入者已删除，不再有第二套外观写入者。</p>
 *
 * <p><b>每个选项</b>走 {@link SceneThemes#selectableSurface} 的 {@link SceneTheme.Role#INDICATOR}
 * 配方：未选中保持角色配方的极淡 tint（轻量状态覆盖），选中态把 tint 的 RGB 换成主题强调色、强度取
 * 主题统一选中强度 {@code 0x59}——<b>选中是色彩语义，不是仅透明度</b>；禁用态仍取角色禁用档。
 * 选项<b>保留配方自带的轻滤镜</b>（契约 §4.1「导航族选项的滤镜口径（G09 裁决）」）：每个选项恰好
 * 采样一次自己的背景，不在配方之外叠第二层玻璃，也不把配方 backdrop 置空。</p>
 *
 * <p><b>选项文字与选中指示条</b>：启用取 {@link SceneThemes#foreground}，禁用取
 * {@link SceneThemes#disabledForeground}，删除 {@code SceneStateColors} 取色。选中项不取
 * {@link SceneThemes#onAccentForeground}——选中项底色是 {@code 0x59} 半透明强调色叠在玻璃之上
 * 的<b>中间调</b>，不是不透明强调底：{@code onAccentForeground} 在浅色主题下对白字（1.71:1，
 * 见 SceneTheme.liquidGlassLight 的 {@code 0xFFFFFFFF}）远低于可读阈值，而主题正文色在深/浅两档
 * 对同一合成底分别为 9.71:1 / 9.08:1（WCAG 相对亮度对比度，实算见测试注释）。选中指示条与标签
 * 共用同一前景派生（原实现两者同为 {@code TEXT_ON_ACCENT}），选中区分由染色承担，不靠文字变色。</p>
 *
 * <h3>契约</h3>
 * <p>R1 纯静态工厂零实例字段 / R2 Props 只读 signal + 常量 + 回调 / R3 组件函数只执行一次 /
 * R4 外观随状态经 bind 派生 / R5 交互态读 interactionState / R6 段内文字命中穿透到段 /
 * R8 多选项单选受控零状态。</p>
 */
public final class SceneNavList {

    /** 各项之间的纵向间距（像素） */
    private static final int ITEM_GAP = SceneChromeTokens.GAP_SM;
    /** 项内边距（像素） */
    private static final int ITEM_PADDING = SceneChromeTokens.PAD_MD;
    /** 选中指示条宽度。 */
    private static final int INDICATOR_WIDTH = 3;
    /** 选中指示条高度。 */
    private static final int INDICATOR_HEIGHT = 18;
    /** 选中标签水平位移。 */
    private static final float SELECTED_LABEL_OFFSET_X = 4.0f;

    /** 纯静态工厂，禁止实例化（强制无状态，契约 R1） */
    private SceneNavList() {
    }

    /**
     * NavList 输入契约 —— 纵向 N 选 1 受控，与 {@link SceneSegmented.Props} 同构（契约 R2/R8）。
     *
     * @param selectedIndex    当前选中项下标（响应式只读，受控源），控件绝不自己修改此值
     * @param options          项文本列表（构建期固定常量，R2 允许常量）
     * @param enabled          是否启用（响应式只读），false 时禁用点击/键盘并切灰态
     * @param onSelect         选择回调，激活某项时以该项下标调用，由外部 set 回 selectedIndex signal
     * @param preferredHeight  可选根高度（像素）：非 null 时透传 {@code root.setPreferredHeight}；
     *                         null = 不设，由布局链（fill/grow/约束）决定。NavList 不内置自动推算
     *                         —— 纵向 N 项高度随项数变化，强行推算与 fill 语义冲突
     */
    @Desugar
    public record Props(
        ReadableSignal<Integer> selectedIndex,
        List<String> options,
        ReadableSignal<Boolean> enabled,
        Consumer<Integer> onSelect,
        Integer preferredHeight
    ) {
    }

    /**
     * 工厂：构建 NavList 组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（R3）：
     * 体内 for 循环建 N 个 item 节点（options 固定，循环建树无副作用、只跑一次，守 I3）。
     * 动态外观全落 {@code bind(computed(...))}，交互只经 {@code on} 调 {@code onSelect}（R4/R5/R8）。</p>
     *
     * @param rt    场景运行时
     * @param props NavList 输入契约
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
            result.root().setGap(ITEM_GAP);
            // 可选根高：非 null 透传，null 由布局链决定（NavList 不内置自动推算，纵向 N 项高随项数变化）
            if (props.preferredHeight() != null) {
                result.root().setPreferredHeight(props.preferredHeight());
            }

            // 导航底座：TOOLBAR 角色配方。表面绑定器独占 background/border/borderWidth/cornerRadius/
            // backdrop/surfaceElevation；构造期不再静态设边框/圆角，也不另绑状态色或标准边框。
            SceneInteractionState baseInteraction = rt.interactionState(result.root());
            // 时序契约：Router 的 writeHovered/writePressed/writeFocused 对未创建的 signal 直接
            // 短路，故在构建期声明关心，保证后续 hover/pressed/focus 能驱动配方状态档。
            baseInteraction.hovered();
            baseInteraction.pressed();
            baseInteraction.focused();
            SceneSurfaceBinder.bind(rt, result.root(), SceneThemes.surface(rt, SceneTheme.Role.TOOLBAR),
                    props.enabled(), baseInteraction);

            // 主题语义前景派生在构造期捕获一次（来源作用域），循环内所有选项共享同一派生信号。
            ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
            ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);

            for (SceneSingleSelectPrimitive.ItemHandle handle : result.items()) {
                SceneNode item = handle.item();
                item.setFlexDirection(FlexDirection.ROW);
                // 纵向导航项：文本左对齐（START），交叉轴居中
                item.setMainAxisAlign(MainAxisAlign.START);
                item.setCrossAxisAlign(CrossAxisAlign.CENTER);
                item.setGap(SceneChromeTokens.GAP_MD);
                item.setPadding(ITEM_PADDING);
                // 命中宽度策略不变：导航项填满导航栏宽度（item 全宽可点，非 SHRINK）。
                item.setFillParentWidth(true);

                SceneNode indicator = new SceneNode();
                indicator.setPreferredWidth(INDICATOR_WIDTH);
                indicator.setPreferredHeight(INDICATOR_HEIGHT);
                indicator.setCornerRadius(INDICATOR_WIDTH);
                indicator.setHitTestable(false);
                item.appendChild(indicator);
                item.appendChild(handle.label());

                SceneInteractionState interaction = handle.interaction();

                // 选项表面：INDICATOR 角色配方 + selected 派生（选中把 tint RGB 换强调色、强度 0x59，
                // 禁用仍取角色禁用档）。唯一外观写入者，独占 background/border/borderWidth/
                // cornerRadius/backdrop/surfaceElevation；保留配方自带的轻滤镜（G09 裁决）。
                ReadableSignal<SceneSurfaceStyle> surface =
                    SceneThemes.selectableSurface(rt, SceneTheme.Role.INDICATOR, handle.selected());
                SceneSurfaceBinder.bind(rt, item, surface, props.enabled(), interaction);

                // 选中指示条与标签共用同一前景派生（启用正文色 / 禁用禁用前景色）。
                ReadableSignal<Integer> itemForeground = () -> Boolean.TRUE.equals(props.enabled().get())
                        ? foreground.get() : disabledForeground.get();
                rt.bind(itemForeground, indicator::setBackgroundColor);
                rt.bind(itemForeground, handle.label()::setTextColor);

                // selection indicator：旧/新 item 的 scaleY 各自用 standard Motion 交叉过渡。
                rt.__bindAnimatedFloat(() -> Boolean.TRUE.equals(handle.selected().get()) ? 1.0f : 0.0f,
                        scale -> indicator.setTransform(Transform.scale(1.0f, scale.floatValue())),
                        SceneChromeTokens.MOTION_STANDARD_MS);
                rt.__bindAnimatedFloat(
                        () -> Boolean.TRUE.equals(handle.selected().get()) ? SELECTED_LABEL_OFFSET_X : 0.0f,
                        offset -> handle.label().setTransform(Transform.translate(offset.floatValue(), 0.0f)),
                        SceneChromeTokens.MOTION_STANDARD_MS);
                SceneControlChrome.bindCursor(rt, item, props.enabled(), SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);
            }

            return result.root();
        };
    }
}
