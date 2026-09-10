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
 * SceneSegmented —— scene 新栈控件层 Phase 4 批 2 分段单选控件（水平段式）。
 *
 * <h3>定位：多选项单选受控控件（契约 R8，与 {@link SceneRadioGroup} 同构）</h3>
 * <p>Props 与 RadioGroup 完全同构（selectedIndex + options 固定 + enabled + onSelect），复用 R8：
 * 当前选中段由外部 {@code selectedIndex} 只读 signal 唯一驱动；激活某段时<b>只经
 * {@code onSelect.accept(i)} 上抛期望选中下标</b>，控件<b>绝不自己维护或修改 selectedIndex</b>。</p>
 *
 * <h3>结构</h3>
 * <pre>
 * root (ROW, crossAxisAlign=STRETCH, gap)                  ← 导航底座，承载 TOOLBAR 表面
 *   └─ segment[i] (ROW, mainAxisAlign=CENTER, crossAxisAlign=CENTER, padding, preferredWidth=固定段宽)  ← 交互单元 hitTestable=true
 *         └─ label[i] (text)   ← 装饰 hitTestable=false
 * </pre>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>不复用 SceneButton</b>：它是试金石不是积木，嵌套会让交互态归属混乱，直接建段节点。</li>
 *   <li><b>段宽按标题文本自适应</b>：构建期一次性测量每段标题文本宽度（options 构建期固定，守 R2），
 *       段宽 = 文本宽 + 2*SEGMENT_PADDING，短标题不留白、长标题不截断。测量值固化进
 *       preferredWidth（LAYOUT 级属性），构建期一次性写入，不引入每段脏标记瀑布（守 I7）。</li>
 *   <li><b>R6 段穿透权威落地</b>：段本身 hitTestable=true，段内 label 文字 hitTestable=false 穿透到所属段。</li>
 * </ul>
 *
 * <h3>外观归属：底座 TOOLBAR + 段 INDICATOR 选中配方，唯一写入者是表面绑定器</h3>
 * <p><b>底座</b>（primitive root）走 {@link SceneThemes#surface} 的 {@link SceneTheme.Role#TOOLBAR}
 * 配方：background/border/borderWidth/cornerRadius/backdrop/surfaceElevation 全归
 * {@link SceneSurfaceBinder}。旧的静态边框/圆角设值与
 * {@code SceneControlChrome.bindStandardBorder}/{@code SceneStateColors} 写入者已删除。</p>
 *
 * <p><b>每段</b>走 {@link SceneThemes#selectableSurface} 的 {@link SceneTheme.Role#INDICATOR} 配方：
 * 未选中保持角色配方的极淡 tint（轻量状态覆盖），选中态把 tint 的 RGB 换成主题强调色、强度取
 * 主题统一选中强度 {@code 0x59}——<b>选中是色彩语义，不是仅透明度</b>；禁用态仍走角色禁用档。
 * 段<b>保留配方自带的轻滤镜</b>（与 G05 选中族 RadioGroup circle / Checkbox box / Toggle track
 * 同口径，每段恰好一条 BACKDROP，见契约 §4.1「导航族选项的滤镜口径（G09 裁决）」）；
 * 「不给每个子项重复安装滤镜」的语义是<b>不得在配方之外再叠第二层玻璃、也不得让段内文字或内容
 * 各自采样背景</b>，不是把选项配方的 backdrop 置空。</p>
 *
 * <p><b>段文字</b>：启用取 {@link SceneThemes#foreground}，禁用取
 * {@link SceneThemes#disabledForeground}，删除 {@code SceneStateColors} 取色。选中项不使用
 * {@link SceneThemes#onAccentForeground}——选中段是叠在 TOOLBAR 玻璃上的 {@code 0x59} 半透明
 * 强调染色而非不透明强调底，合成底色仍由玻璃主导；{@code onAccentForeground} 是给不透明强调底
 * （如 RadioGroup 的 dot 标记）用的，浅色主题下它在浅紫合成底上对比度不足（约 1.7:1），而主题
 * 正文色正是主题作者保证在自身玻璃上可读的正文色（契约 §4.1 给 Segmented 的前景映射同为
 * {@code foreground}）。选中区分由染色承担，不靠文字变色。</p>
 *
 * <h3>契约</h3>
 * <p>R1 纯静态工厂零实例字段 / R2 Props 只读 signal + 常量 + 回调 / R3 组件函数只执行一次 /
 * R4 外观随状态经 bind 派生 / R5 交互态读 interactionState / R6 段内文字命中穿透到段 /
 * R8 多选项单选受控零状态。</p>
 */
public final class SceneSegmented {

    /**
     * 段内边距（像素）
     * <p>维护约束：修改此值需同步 club.heiqi.config.ui.theme.ConfigTheme.NAV_TAB_PADDING。
     * 因 uilib 不能反向依赖 config 模块，此处仅以文字引用全限定名，不 import。
     */
    private static final int SEGMENT_PADDING = SceneChromeTokens.PAD_LG;
    /**
     * 各段之间的横向间距（像素）
     */
    private static final int SEG_GAP = SceneChromeTokens.GAP_SM;
    /**
     * 段标签默认字号（UI 像素），与 {@link SceneNode} 默认 fontSize 对齐，用于构建期文本宽度测量。
     * <p>调用方可用 {@link Props#fontSize()} 覆盖；本常量是未指定时的回落值，也是与下游对齐的基准。
     * <p>维护约束：修改此值需同步 club.heiqi.config.ui.theme.ConfigTheme.NAV_TAB_FONT_SIZE。
     * 因 uilib 不能反向依赖 config 模块，此处仅以文字引用全限定名，不 import。
     */
    private static final int SEG_LABEL_FONT_SIZE = 16;

    /**
     * 纯静态工厂，禁止实例化（强制无状态，契约 R1）
     */
    private SceneSegmented() {
    }

    /**
     * Segmented 输入契约 —— 多选项单选受控，与 {@link SceneRadioGroup.Props} 同构（契约 R2/R8）。
     *
     * @param selectedIndex 当前选中段下标（响应式只读，受控源），控件绝不自己修改此值
     * @param options       段文本列表（构建期固定常量，R2 允许常量）
     * @param enabled       是否启用（响应式只读），false 时禁用点击/键盘并切灰态
     * @param onSelect      选择回调，激活某段时以该段下标调用，由外部 set 回 selectedIndex signal
     * @param fontSize      段标签字号（UI 像素，响应式）；null = 不指定，沿用
     *                      {@link #SEG_LABEL_FONT_SIZE}
     */
    @Desugar
    public record Props(
        ReadableSignal<Integer> selectedIndex,
        List<String> options,
        ReadableSignal<Boolean> enabled,
        Consumer<Integer> onSelect,
        ReadableSignal<Integer> fontSize
    ) {

        /**
         * 兼容四参构造器：字号不指定（沿用 {@link #SEG_LABEL_FONT_SIZE}）。
         *
         * @param selectedIndex 当前选中段下标
         * @param options       段文本列表
         * @param enabled       是否启用
         * @param onSelect      选择回调
         */
        public Props(ReadableSignal<Integer> selectedIndex, List<String> options,
                ReadableSignal<Boolean> enabled, Consumer<Integer> onSelect) {
            this(selectedIndex, options, enabled, onSelect, null);
        }
    }

    /**
     * 工厂：构建 Segmented 组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（R3）：
     * 体内 for 循环建 N 个 segment 节点（options 固定，循环建树无副作用、只跑一次，守 I3）。
     * 动态外观全落 {@code bind(computed(...))}，交互只经 {@code on} 调 {@code onSelect}（R4/R5/R8）。</p>
     *
     * @param rt    场景运行时
     * @param props Segmented 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneSingleSelectPrimitive.Props primitiveProps = new SceneSingleSelectPrimitive.Props(
                props.selectedIndex(),
                props.options(),
                props.enabled(),
                props.onSelect(),
                SceneSingleSelectPrimitive.Orientation.HORIZONTAL);
            SceneSingleSelectPrimitive.Result result = SceneSingleSelectPrimitive.create(rt, primitiveProps);
            result.root().setCrossAxisAlign(CrossAxisAlign.STRETCH);
            result.root().setGap(SEG_GAP);
            // 内置默认高：段自然高 = 标签行高 + 2 * 段内边距（与 ConfigScreen 原手动算口径同源）。
            // 容器型固定子须显式设 preferredHeight，否则 ConstraintResolver.computeColumnGrowHeights
            // 命中 priorKnownChildHeight 容器分支返回 UNCONSTRAINED 早退，grow 兄弟收不到分配高。
            // 内置后调用方无需再手动设高（YAGNI：本轮不开 prop 覆盖）。
            // 标签字号入口：段宽与条高都按字号测量，故构建期先取有效字号（未指定回落常量）。
            final int labelFontSize = SceneControlTypography.fontSizeOrDefault(
                    props.fontSize(), SEG_LABEL_FONT_SIZE);
            result.root().setPreferredHeight(rt.lineHeight(labelFontSize) + 2 * SEGMENT_PADDING);

            // 导航底座：TOOLBAR 角色配方。表面绑定器独占 background/border/borderWidth/
            // cornerRadius/backdrop/surfaceElevation；构造期不再静态设边框/圆角，
            // 也不另绑状态色或标准边框。
            SceneInteractionState baseInteraction = rt.interactionState(result.root());
            // 时序契约：Router 的 writeHovered/writePressed/writeFocused 对未创建的 signal 直接
            // 短路，故在构建期声明关心，保证后续 hover/pressed/focus 能驱动配方状态档。
            baseInteraction.hovered();
            baseInteraction.pressed();
            baseInteraction.focused();
            SceneSurfaceBinder.bind(rt, result.root(), SceneThemes.surface(rt, SceneTheme.Role.TOOLBAR),
                    props.enabled(), baseInteraction);

            // 主题语义前景派生在构造期捕获一次（来源作用域），循环内所有段共享同一派生信号。
            ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
            ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);

            for (SceneSingleSelectPrimitive.ItemHandle handle : result.items()) {
                SceneNode segment = handle.item();
                segment.setFlexDirection(FlexDirection.ROW);
                segment.setMainAxisAlign(MainAxisAlign.CENTER);
                segment.setCrossAxisAlign(CrossAxisAlign.CENTER);
                segment.setPadding(SEGMENT_PADDING);
                // 段宽按标题文本自适应：构建期一次性测量（options 固定，守 R2/I7），
                // 段宽 = 文本宽 + 2*内边距，短标题不留白、长标题不截断。测量值固化进
                // preferredWidth（LAYOUT 级属性），构建期一次性写入，运行期不再重测。
                String title = props.options().get(handle.index());
                int textWidth = rt.measureTextWidth(title, labelFontSize);
                segment.setPreferredWidth(textWidth + 2 * SEGMENT_PADDING);
                handle.label().setFontSize(labelFontSize);
                segment.appendChild(handle.label());

                SceneInteractionState interaction = handle.interaction();

                // 选中指示：INDICATOR 角色配方 + selected 派生（选中把 tint RGB 换强调色、
                // 强度 0x59，禁用仍取角色禁用档）。唯一外观写入者，独占 background/border/
                // borderWidth/cornerRadius/backdrop/surfaceElevation。
                // 滤镜口径（G09 裁决）：段保留配方自带的轻滤镜，与 G05 选中族同口径；
                // 「不重复安装滤镜」指不得在配方之外再叠第二层玻璃，也不给段内文字采样背景。
                ReadableSignal<SceneSurfaceStyle> surface =
                    SceneThemes.selectableSurface(rt, SceneTheme.Role.INDICATOR, handle.selected());
                SceneSurfaceBinder.bind(rt, segment, surface, props.enabled(), interaction);

                // 段文字：启用取主题正文色（选中/未选中同色，见类注释对比度说明），
                // 禁用取主题禁用前景色；删除 SceneStateColors 取色。
                rt.bindComputed(() -> Boolean.TRUE.equals(props.enabled().get())
                        ? foreground.get() : disabledForeground.get(),
                    handle.label()::setTextColor);

                SceneControlChrome.bindCursor(rt, segment, props.enabled(), SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);
            }

            // 运行期改字号：标签字号与「按字号测出来的几何」必须同时重算，否则段宽/条高会留在旧字号上。
            // 不传字号时不建立任何绑定，构建期尺寸即最终尺寸（视觉零变化）。
            if (props.fontSize() != null) {
                final List<SceneSingleSelectPrimitive.ItemHandle> items = result.items();
                final List<String> options = props.options();
                final SceneNode base = result.root();
                rt.bind(props.fontSize(), value -> {
                    int fontSize = SceneControlTypography.fontSizeOrDefault(value, SEG_LABEL_FONT_SIZE);
                    base.setPreferredHeight(rt.lineHeight(fontSize) + 2 * SEGMENT_PADDING);
                    for (int i = 0; i < items.size(); i++) {
                        SceneSingleSelectPrimitive.ItemHandle handle = items.get(i);
                        handle.label().setFontSize(fontSize);
                        handle.item().setPreferredWidth(
                                rt.measureTextWidth(options.get(i), fontSize) + 2 * SEGMENT_PADDING);
                    }
                });
            }

            return result.root();
        };
    }
}
