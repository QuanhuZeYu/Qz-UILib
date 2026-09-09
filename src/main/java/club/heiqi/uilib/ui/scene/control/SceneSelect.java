package club.heiqi.uilib.ui.scene.control;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneSelect —— scene 新栈锚定浮层选择控件。
 *
 * <p>trigger 常驻主树，listbox 通过 {@link SceneRuntime#portalAnchored} 提升为 overlay root。
 * 选中值由外部 {@code selectedIndex} 唯一驱动；展开、收起、键盘高亮和滚动均以本地 signal 表达，
 * handler 只写 signal 或调用 {@code onSelect} 上抛期望下标，浮层显隐由 signal→portal 派生。</p>
 *
 * <h3>外观归属：主题配方是唯一写入者</h3>
 * <ul>
 *   <li>触发器：{@link SceneSurfaceBinder} 从 {@link SceneThemes#surface(SceneRuntime,
 *       SceneTheme.Role) INPUT 角色配方}派生 background/border/borderWidth/cornerRadius/
 *       backdrop/surfaceElevation；选中值文本取主题 {@code foreground}、箭头取 {@code mutedForeground}、
 *       禁用取 {@code disabledForeground}，不再静态设边框/圆角、不叠加 {@code SceneStateColors} 实色绑定。</li>
 *   <li>弹出底座：同一个绑定器从 {@link SceneTheme.Role#OVERLAY OVERLAY 角色配方}派生浮层根表面——
 *       浮层是独立玻璃面，候选行只做 hover/选中/键盘高亮的轻量半透明覆盖，不逐项采样滤镜。</li>
 * </ul>
 */
public final class SceneSelect {

    /**
     * trigger 内边距
     */
    private static final int TRIGGER_PADDING = SceneChromeTokens.PAD_MD;
    /**
     * item 内边距
     */
    private static final int ITEM_PADDING = SceneChromeTokens.PAD_MD;
    /**
     * trigger label 与箭头间距
     */
    private static final int TRIGGER_GAP = SceneChromeTokens.GAP_MD;
    /** 候选行默认背景（全透明，露出浮层玻璃底；行不各自采样滤镜）。 */
    private static final int ITEM_BG_TRANSPARENT = 0x00000000;
    /** 候选行 hover 覆盖强度：主题 accent 的低透明度轻量覆盖。 */
    private static final int ITEM_HOVER_ALPHA = 0x1F;
    /** 候选行选中覆盖强度：主题 accent 半透明作为「选中指示」，明显强于 hover。 */
    private static final int ITEM_SELECTED_ALPHA = 0x33;
    /** 候选行选中且 hover 的覆盖强度：强调色加深，选中不只靠透明度区分。 */
    private static final int ITEM_SELECTED_HOVER_ALPHA = 0x4C;
    /** 候选行键盘高亮覆盖强度：主题选区背景的轻量覆盖，明显强于 hover（不只靠透明度区分）。 */
    private static final int ITEM_HIGHLIGHT_ALPHA = 0x59;
    /** 恒真启用信号：弹出浮层不参与 disabled 语义（禁用时不展开），表面绑定仍需 enabled 通道。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;

    /**
     * 纯静态工厂，禁止实例化。
     */
    private SceneSelect() {
    }

    /**
     * Select 输入契约 —— 多选项单选受控，浮层显隐由控件内部 signal 派生。
     *
     * @param selectedIndex 当前选中项下标，控件绝不修改
     * @param options       构建期固定选项文本，构造期防御性复制为不可变列表
     * @param enabled       是否启用
     * @param onSelect      选择回调，激活选项时上抛期望下标
     */
    @Desugar
    public record Props(
            ReadableSignal<Integer> selectedIndex,
            List<String> options,
            ReadableSignal<Boolean> enabled,
            Consumer<Integer> onSelect
    ) {
        public Props(ReadableSignal<Integer> selectedIndex,
                     List<String> options,
                     ReadableSignal<Boolean> enabled,
                     Consumer<Integer> onSelect) {
            this.selectedIndex = Objects.requireNonNull(selectedIndex, "selectedIndex");
            this.options = SceneListOps.immutableCopy(Objects.requireNonNull(options, "options"));
            this.enabled = Objects.requireNonNull(enabled, "enabled");
            this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
        }
    }

    /**
     * 工厂：构建 Select 组件函数。
     *
     * @param rt    场景运行时
     * @param props Select 输入契约
     * @return 组件函数，交 {@link SceneRuntime#mount} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneSelectPrimitive.ListboxChrome chrome = new SceneSelectChrome(rt);
            SceneSelectPrimitive.Props primitiveProps = new SceneSelectPrimitive.Props(
                    props.selectedIndex(), props.options(), props.enabled(), props.onSelect(), chrome);
            SceneSelectPrimitive.Result result = SceneSelectPrimitive.create(rt, primitiveProps);

            SceneNode trigger = result.trigger();
            trigger.setPadding(TRIGGER_PADDING);

            // 唯一外观写入者：background/border/borderWidth/cornerRadius/backdrop/surfaceElevation
            // 全归 INPUT 表面绑定，控件不再静态设边框宽/圆角、不再叠加 SceneStateColors 实色绑定。
            // 构造期只捕获主题信号（此时 Owner 是来源作用域），取值全部发生在 effect 体内。
            SceneInteractionState interaction = rt.interactionState(trigger);
            SceneSurfaceBinder.bind(rt, trigger, SceneThemes.surface(rt, SceneTheme.Role.INPUT),
                    props.enabled(), interaction);

            // 语义前景：选中值取正文 foreground、箭头取次要 mutedForeground、禁用统一 disabledForeground。
            ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
            ReadableSignal<Integer> mutedForeground = SceneThemes.mutedForeground(rt);
            ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);
            rt.bindComputed(() -> resolveTriggerForeground(props.enabled().get(),
                            foreground.get(), disabledForeground.get()),
                    result.label()::setTextColor);
            rt.bindComputed(() -> resolveArrowForeground(props.enabled().get(),
                            mutedForeground.get(), disabledForeground.get()),
                    result.arrow()::setTextColor);

            SceneControlChrome.bindCursor(rt, trigger, props.enabled(), SceneCursor.POINTER, SceneCursor.DEFAULT);

            return trigger;
        };
    }

    /**
     * 解析 trigger 选中值前景色（主题语义色版）。
     *
     * @param enabled            是否启用
     * @param foreground         主题正文前景
     * @param disabledForeground 主题禁用前景
     * @return 文本色 ARGB
     */
    private static int resolveTriggerForeground(Boolean enabled, int foreground, int disabledForeground) {
        return Boolean.TRUE.equals(enabled) ? foreground : disabledForeground;
    }

    /**
     * 解析 trigger 箭头前景色：箭头是次要指示，启用时取主题次要前景。
     *
     * @param enabled            是否启用
     * @param mutedForeground    主题次要前景
     * @param disabledForeground 主题禁用前景
     * @return 箭头文本色 ARGB
     */
    private static int resolveArrowForeground(Boolean enabled, int mutedForeground, int disabledForeground) {
        return Boolean.TRUE.equals(enabled) ? mutedForeground : disabledForeground;
    }

    /**
     * 解析 item 背景：键盘高亮 &gt; 选中 &gt; hover &gt; 透明（浮层玻璃底可见）。
     *
     * <p>轻量状态覆盖：取主题语义色做半透明染色，行内不各自采样背景滤镜。键盘高亮取主题选区背景、
     * 选中与 hover 取主题 accent，强度依次递减且互不相同，「当前键盘项」「当前选中项」都不只靠
     * 同一强度的透明度区分；默认态全透明，露出浮层玻璃。</p>
     *
     * @param selected           是否选中
     * @param highlighted        是否键盘高亮
     * @param hovered            是否悬停
     * @param accent             主题强调色
     * @param selectionBackground 主题选区背景色
     * @return ARGB 背景色
     */
    private static int resolveItemBackground(boolean selected, boolean highlighted, boolean hovered,
                                             int accent, int selectionBackground) {
        if (highlighted) {
            return tint(selectionBackground, ITEM_HIGHLIGHT_ALPHA);
        }
        if (selected) {
            return tint(accent, hovered ? ITEM_SELECTED_HOVER_ALPHA : ITEM_SELECTED_ALPHA);
        }
        if (hovered) {
            return tint(accent, ITEM_HOVER_ALPHA);
        }
        return ITEM_BG_TRANSPARENT;
    }

    /**
     * 解析 item 文本色：强调覆盖上的选中项用主题强调底前景，其余用主题正文前景。
     *
     * @param selected            是否选中
     * @param highlighted         是否键盘高亮
     * @param hovered             是否悬停
     * @param foreground          主题正文前景
     * @param onAccentForeground  主题强调底前景
     * @return ARGB 文本色
     */
    private static int resolveItemText(boolean selected, boolean highlighted, boolean hovered,
                                       int foreground, int onAccentForeground) {
        if (selected && (highlighted || hovered)) {
            return onAccentForeground;
        }
        return foreground;
    }

    /**
     * 保留色 RGB、替换 alpha 通道（轻量覆盖用）。
     *
     * @param argb  源色
     * @param alpha 目标 alpha（0..255）
     * @return 替换 alpha 后的 ARGB
     */
    private static int tint(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** SceneSelect 默认 listbox chrome 装配器。 */
    private static final class SceneSelectChrome implements SceneSelectPrimitive.ListboxChrome {
        /** 场景运行时，用于注册 PAINT 绑定。 */
        private final SceneRuntime rt;

        /**
         * 创建默认 listbox chrome 装配器。
         *
         * @param rt 场景运行时
         */
        private SceneSelectChrome(SceneRuntime rt) {
            this.rt = rt;
        }

        @Override
        public void decorateListbox(SceneNode listbox) {
            // 弹出底座：OVERLAY 配方画在浮层根（唯一外观写入者）。
            // enabled 恒真：禁用态由 primitive 的 expanded 守卫处理（禁用不展开）；
            // 配方在 portal 构建调用栈内取，延迟打开时继承来源主题。
            SceneSurfaceBinder.bind(rt, listbox, SceneThemes.surface(rt, SceneTheme.Role.OVERLAY),
                    ALWAYS_ENABLED, rt.interactionState(listbox));
        }

        @Override
        public void decorateItem(SceneSelectPrimitive.ItemHandle handle) {
            handle.item().setPadding(ITEM_PADDING);
            handle.item().setCursor(SceneCursor.POINTER);
            // 行只做轻量状态覆盖：hover/选中取主题 accent、键盘高亮取主题选区背景，均为半透明染色；
            // 不逐项采样滤镜，默认透明露出浮层玻璃底。
            ReadableSignal<Integer> accent = SceneThemes.accent(rt);
            ReadableSignal<Integer> selectionBackground = SceneThemes.selectionBackground(rt);
            rt.__bindAnimatedColor(() -> resolveItemBackground(
                            Boolean.TRUE.equals(handle.selected().get()),
                            Boolean.TRUE.equals(handle.highlighted().get()),
                            Boolean.TRUE.equals(handle.interaction().hovered().get()),
                            accent.get(), selectionBackground.get()),
                    handle.item()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
            ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
            ReadableSignal<Integer> onAccentForeground = SceneThemes.onAccentForeground(rt);
            rt.bindComputed(() -> resolveItemText(
                            Boolean.TRUE.equals(handle.selected().get()),
                            Boolean.TRUE.equals(handle.highlighted().get()),
                            Boolean.TRUE.equals(handle.interaction().hovered().get()),
                            foreground.get(), onAccentForeground.get()),
                    handle.label()::setTextColor);
        }
    }
}
