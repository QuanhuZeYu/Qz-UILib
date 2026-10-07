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
     * 段标签默认字号（UI 像素），与 {@link SceneNode} 默认 fontSize 对齐：登记为 root 的层 4a 回落值
     * （数值与框架层 4b 默认同值，不遮蔽层 1/2/3 声明）。
     * <p>调用方可用 {@link Props#fontSize()} 覆盖；本常量是未指定时的回落值，也是与下游对齐的基准。
     * <p>维护约束：修改此值需同步 club.heiqi.config.ui.theme.ConfigTheme.NAV_TAB_FONT_SIZE。
     * 因 uilib 不能反向依赖 config 模块，此处仅以文字引用全限定名，不 import。
     */
    private static final int SEG_LABEL_FONT_SIZE = 16;


    private SceneSegmented() {
    }


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


    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneSingleSelectPrimitive.Props primitiveProps = new SceneSingleSelectPrimitive.Props(
                props.selectedIndex(),
                props.options(),
                props.enabled(),
                props.onSelect(),
                SceneSingleSelectPrimitive.Orientation.HORIZONTAL);
            SceneSingleSelectPrimitive.Result result = SceneSingleSelectPrimitive.create(rt, primitiveProps);
            SceneNode root = result.root();
            root.setCrossAxisAlign(CrossAxisAlign.STRETCH);
            root.setGap(SEG_GAP);
            // 字号唯一真值是 root（与 Button/TextInput/TextArea 同口径）。
            // Props.fontSize() 写 root 的层 2 声明（与句柄入口同一槽，后写者胜出）；
            // 未指定时不写层 2 声明：回落值走层 4a（见下方 setFallbackFontSize），不遮蔽句柄/作用域。
            // 段标签是 root 的后代，沿父链继承，不再逐点接线。
            final ReadableSignal<Integer> configuredFontSize = props.fontSize();
            final Integer configuredFontSizeValue =
                    configuredFontSize == null ? null : configuredFontSize.get();
            // 层 4a 回落值：数值与框架层 4b 默认同值（16），本控件自持以表达「未声明时落 16」；
            // 层 1/2/3 均优先于它，故不遮蔽句柄 / 作用域 / 环境默认。
            root.setFallbackFontSize(SEG_LABEL_FONT_SIZE);
            if (configuredFontSize != null) {
                if (configuredFontSizeValue != null) {
                    root.setFontScope(configuredFontSizeValue.intValue());
                }
                rt.bind(configuredFontSize, current -> {
                    if (current == null) {
                        root.resetFontScope();
                    } else {
                        root.setFontScope(current.intValue());
                    }
                });
            }
            // 内置默认高：段自然高 = 标签行高 + 2 * 段内边距（与 ConfigScreen 原手动算口径同源）。
            // 容器型固定子须显式设 preferredHeight，否则 ConstraintResolver.computeColumnGrowHeights
            // 命中 priorKnownChildHeight 容器分支返回 UNCONSTRAINED 早退，grow 兄弟收不到分配高。
            // 内置后调用方无需再手动设高（YAGNI：本轮不开 prop 覆盖）。
            //
            // 条高由字号派生（S5 收口）：setFontSizeMetric 登记即按当前生效字号算一次，
            // 其后仅在「生效字号变化」时由框架重算（同字号去重），不再手写 layoutDone 重算 effect。
            root.setFontSizeMetric((node, fontSizePx) ->
                    node.setPreferredHeight(rt.lineHeight(fontSizePx) + 2 * SEGMENT_PADDING));

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
                // 段宽 = 标签文本宽 + 2*内边距，由字号派生声明给出（S5 收口）：
                // setFontSizeMetric 登记即按当前生效字号算一次，其后字号变化由框架重算 —— 不再手写
                // layoutDone effect，也不再靠「构建期一次性测量」。
                //
                // 为什么仍要显式声明 preferredWidth（而不是纯 SHRINK 靠标签自然宽）：标签文本由 primitive
                // 经 rt.bindText 绑定，首帧布局可能早于响应式落值（实测首帧 label.getText()==null）——
                // 此时文本叶退化为主轴填满，SHRINK 段会被算成整条宽并把后续段推离画布（命中盒失效）。
                // 段宽取 options（构建期常量）测量，与标签文本时序解耦，首帧即正确。
                final String title = props.options().get(handle.index());
                segment.setWidthSizing(SceneNode.WidthSizing.SHRINK);
                segment.setFontSizeMetric((node, fontSizePx) -> node.setPreferredWidth(
                        rt.measureTextWidth(title, fontSizePx) + 2 * SEGMENT_PADDING));
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

            return root;
        };
    }
}
