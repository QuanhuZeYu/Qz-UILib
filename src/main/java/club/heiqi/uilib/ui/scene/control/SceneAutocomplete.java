package club.heiqi.uilib.ui.scene.control;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneAutocomplete —— scene 新栈带样式自动补全成品控件。
 *
 * <p>包装 {@link SceneAutocompletePrimitive}：在行为核心之上挂载输入区 chrome 与候选浮层
 * chrome。调用方无需再手写 {@code applyTextInputChrome} / listbox 装饰，直接
 * {@code SceneAutocomplete.create(rt, props)} 即可。</p>
 *
 * <h3>外观归属：主题配方是唯一写入者</h3>
 * <ul>
 *   <li>输入区：{@link SceneSurfaceBinder} 从 {@link SceneThemes#surface(SceneRuntime,
 *       SceneTheme.Role) INPUT 角色配方}派生 background/border/borderWidth/cornerRadius/
 *       backdrop/surfaceElevation；正文/占位/禁用前景与 caret、选区色消费主题语义色
 *       （与 {@link SceneTextInput} 同一接缝，不静态设色、不叠加第二套实色动画）。</li>
 *   <li>候选浮层：同一个绑定器从 {@link SceneTheme.Role#OVERLAY OVERLAY 角色配方}派生浮层根的
 *       表面属性——浮层是独立玻璃面，候选行只做 hover/键盘高亮的轻量染色覆盖，不逐项采样滤镜。</li>
 * </ul>
 *
 * <h3>与 Primitive 的边界</h3>
 * <ul>
 *   <li>本类负责视觉 chrome（padding/INPUT 表面/主题前景/cursor + OVERLAY 表面/item 覆盖）。</li>
 *   <li>{@link SceneAutocompletePrimitive} 保留不删：无样式行为核心 + R13 expanded 独立 Signal；
 *       过滤、补全、键盘导航、延迟打开/关闭语义全在 primitive，本类不改。</li>
 *   <li>Props 对齐 TextInput + candidates/matchMode/maxVisible/onSelect；不暴露 chrome 注入口
 *       （成品壳内置默认 chrome）。</li>
 * </ul>
 *
 * <h3>合规守护</h3>
 * <ul>
 *   <li>R1：纯静态工厂 + 零实例字段。</li>
 *   <li>R3：create 体只跑一次（建树 + chrome bind）。</li>
 *   <li>R4：外观经 rt.bind/bindComputed 派生；构造期只捕获主题信号，不解引用未求值的 Computed。</li>
 *   <li>R13：expanded 由 primitive 持有独立可写 Signal，本类不派生。</li>
 *   <li>I5：候选项 keyed diff 在 primitive 内完成。</li>
 *   <li>零 config 依赖：不 import 任何 {@code club.heiqi.config.*}。</li>
 * </ul>
 */
public final class SceneAutocomplete {

    /** caret 不可见（全透明，纯 PAINT 切换不重排）。 */
    private static final int CARET_TRANSPARENT = 0x00000000;
    /** 候选行默认背景（全透明，露出浮层玻璃底；行不各自采样滤镜）。 */
    private static final int ITEM_BG_TRANSPARENT = 0x00000000;
    /** 候选行 hover 覆盖强度：主题 accent 的低透明度轻量覆盖。 */
    private static final int ITEM_HOVER_ALPHA = 0x1F;
    /** 候选行键盘高亮覆盖强度：主题选区背景的轻量覆盖，明显强于 hover（不只靠透明度区分）。 */
    private static final int ITEM_HIGHLIGHT_ALPHA = 0x59;
    /** 恒真启用信号：候选浮层不参与 disabled 语义（禁用时不展开），表面绑定仍需 enabled 通道。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;
    /** 输入框内边距（非颜色语义，主题不接管布局）。 */
    private static final int PADDING = SceneChromeTokens.PAD_MD;
    /** listbox item 内边距（非颜色语义，主题不接管布局）。 */
    private static final int ITEM_PADDING = SceneChromeTokens.PAD_MD;

    /** 纯静态工厂，禁止实例化。 */
    private SceneAutocomplete() {
    }

    /**
     * Autocomplete 成品输入契约。
     *
     * @param value       当前文本（受控源，R9）
     * @param enabled     是否启用
     * @param readOnly    是否只读
     * @param placeholder 占位文本
     * @param maxLength   最大长度（码点数）
     * @param candidates  构建期固定候选列表
     * @param matchMode   匹配模式，null 时默认 PREFIX
     * @param maxVisible  浮层最多候选数，{@code <=0} 时用 primitive 默认
     * @param onChange    文本变更上抛
     * @param onSelect    选中候选上抛；null 时回退 onChange
     */
    @Desugar
    public record Props(
            ReadableSignal<String> value,
            ReadableSignal<Boolean> enabled,
            ReadableSignal<Boolean> readOnly,
            String placeholder,
            int maxLength,
            List<String> candidates,
            SceneAutocompletePrimitive.MatchMode matchMode,
            int maxVisible,
            Consumer<String> onChange,
            Consumer<String> onSelect
    ) {
        /**
         * 紧凑构造：matchMode=PREFIX、maxVisible=默认、onSelect=onChange。
         *
         * @param value       受控文本源
         * @param enabled     启用信号
         * @param readOnly    只读信号
         * @param placeholder 占位文本
         * @param maxLength   最大长度
         * @param candidates  候选列表
         * @param onChange    文本变更回调
         */
        public Props(ReadableSignal<String> value,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly,
                     String placeholder,
                     int maxLength,
                     List<String> candidates,
                     Consumer<String> onChange) {
            this(value, enabled, readOnly, placeholder, maxLength, candidates,
                    SceneAutocompletePrimitive.MatchMode.PREFIX, 0, onChange, onChange);
        }

        /**
         * 指定 matchMode/maxVisible，onSelect=onChange。
         *
         * @param value       受控文本源
         * @param enabled     启用信号
         * @param readOnly    只读信号
         * @param placeholder 占位文本
         * @param maxLength   最大长度
         * @param candidates  候选列表
         * @param matchMode   匹配模式
         * @param maxVisible  浮层最多候选数
         * @param onChange    文本变更回调
         */
        public Props(ReadableSignal<String> value,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly,
                     String placeholder,
                     int maxLength,
                     List<String> candidates,
                     SceneAutocompletePrimitive.MatchMode matchMode,
                     int maxVisible,
                     Consumer<String> onChange) {
            this(value, enabled, readOnly, placeholder, maxLength, candidates,
                    matchMode, maxVisible, onChange, onChange);
        }

        /**
         * 全参紧凑构造：null 校验与缺省补全。
         *
         * @param value       受控文本源
         * @param enabled     启用信号
         * @param readOnly    只读信号
         * @param placeholder 占位文本
         * @param maxLength   最大长度
         * @param candidates  候选列表
         * @param matchMode   匹配模式
         * @param maxVisible  浮层最多候选数
         * @param onChange    文本变更回调
         * @param onSelect    选中回调（null 时回退 onChange）
         */
        public Props(ReadableSignal<String> value,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly,
                     String placeholder,
                     int maxLength,
                     List<String> candidates,
                     SceneAutocompletePrimitive.MatchMode matchMode,
                     int maxVisible,
                     Consumer<String> onChange,
                     Consumer<String> onSelect) {
            this.value = Objects.requireNonNull(value, "value");
            this.enabled = Objects.requireNonNull(enabled, "enabled");
            this.readOnly = Objects.requireNonNull(readOnly, "readOnly");
            this.placeholder = placeholder == null ? "" : placeholder;
            this.maxLength = maxLength;
            this.candidates = Objects.requireNonNull(candidates, "candidates");
            this.matchMode = matchMode == null
                    ? SceneAutocompletePrimitive.MatchMode.PREFIX : matchMode;
            this.maxVisible = maxVisible;
            this.onChange = Objects.requireNonNull(onChange, "onChange");
            this.onSelect = onSelect != null ? onSelect : onChange;
        }
    }

    /**
     * 工厂：构建带 chrome 的 Autocomplete 组件函数。
     *
     * <p>返回 {@code Supplier<SceneNode>}，与 {@link SceneTextInput} / {@link SceneSelect}
     * 作者体验一致，交 {@code rt.mount(parent, ...)} 挂载。</p>
     *
     * @param rt    场景运行时
     * @param props 输入契约
     * @return 组件函数
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(props, "props");
        return () -> {
            SceneAutocompletePrimitive.ListboxChrome chrome = new DefaultListboxChrome(rt);
            int maxVisible = props.maxVisible() <= 0 ? 8 : props.maxVisible();
            SceneAutocompletePrimitive.Props primitiveProps = new SceneAutocompletePrimitive.Props(
                    props.value(),
                    props.enabled(),
                    props.readOnly(),
                    props.placeholder(),
                    props.maxLength(),
                    props.candidates(),
                    props.matchMode(),
                    maxVisible,
                    props.onChange(),
                    props.onSelect(),
                    chrome);
            SceneAutocompletePrimitive.Result result =
                    SceneAutocompletePrimitive.create(rt, primitiveProps);
            applyTextInputChrome(rt, result.textInput(), props);
            return result.root();
        };
    }

    /**
     * 给内嵌 TextInput primitive 挂载与 {@link SceneTextInput} 同款 chrome（同一接缝）。
     *
     * <p>构造期只捕获主题信号（{@link SceneThemes#surface} 等必须在 builder 内调用），
     * 不在构造期解引用未求值的 Computed；一切取值都发生在 effect 体内。</p>
     *
     * @param rt     场景运行时
     * @param result textInput primitive 结果
     * @param props  成品 Props（读 enabled）
     */
    private static void applyTextInputChrome(SceneRuntime rt,
                                             SceneTextInputPrimitive.Result result,
                                             Props props) {
        SceneNode root = result.root();
        root.setPadding(PADDING);
        SceneInteractionState interaction = rt.interactionState(root);

        // 唯一外观写入者：background/border/borderWidth/cornerRadius/backdrop/surfaceElevation
        // 全归表面绑定，控件不再静态设边框宽/圆角、不再叠加 SceneStateColors 实色绑定。
        SceneSurfaceBinder.bind(rt, root, SceneThemes.surface(rt, SceneTheme.Role.INPUT),
                props.enabled(), interaction);

        // 语义前景：正文 foreground / 占位 mutedForeground / 禁用 disabledForeground
        ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
        ReadableSignal<Integer> mutedForeground = SceneThemes.mutedForeground(rt);
        ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);
        rt.bindComputed(() -> resolveTextColor(result.isPlaceholder().get(), props.enabled().get(),
                        foreground.get(), mutedForeground.get(), disabledForeground.get()),
                result.prefixText()::setTextColor);
        rt.bindComputed(() -> resolveTextColor(result.isPlaceholder().get(), props.enabled().get(),
                        foreground.get(), mutedForeground.get(), disabledForeground.get()),
                result.suffixText()::setTextColor);

        // caret 双槽位：focus 在选区哪一端，哪端着色（B2 选区结构）；色值取主题聚焦色
        ReadableSignal<Integer> caretColor = SceneThemes.borderFocus(rt);
        rt.__bindAnimatedColor(() -> resolveCaretColor(result.caretVisible().get(),
                        result.selection().get().focusCp() == result.selection().get().startCp(),
                        caretColor.get()),
                result.caret()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
        rt.__bindAnimatedColor(() -> resolveCaretColor(result.caretVisible().get(),
                        result.selection().get().isActive()
                                && result.selection().get().focusCp() == result.selection().get().endCp(),
                        caretColor.get()),
                result.caretAfter()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);

        // 选区高亮：激活即显示（失焦保留选区可见），文本色反白；背景/前景取主题选区语义色
        ReadableSignal<Integer> selectionBackground = SceneThemes.selectionBackground(rt);
        ReadableSignal<Integer> selectionForeground = SceneThemes.selectionForeground(rt);
        rt.bindComputed(() -> resolveHighlightBackground(result.selection().get().isActive(),
                        selectionBackground.get()),
                result.highlightText()::setBackgroundColor);
        rt.bindComputed(() -> resolveHighlightTextColor(result.selection().get().isActive(),
                        selectionForeground.get(),
                        resolveTextColor(result.isPlaceholder().get(), props.enabled().get(),
                                foreground.get(), mutedForeground.get(), disabledForeground.get())),
                result.highlightText()::setTextColor);
        SceneControlChrome.bindCursor(rt, root, props.enabled(), SceneCursor.TEXT, SceneCursor.NOT_ALLOWED);
        rt.bind(props.enabled(), e -> root.setHitTestable(Boolean.TRUE.equals(e)));
    }

    /**
     * 解析文本色（主题语义色版）。
     *
     * @param placeholder       是否处于 placeholder 状态
     * @param enabled           是否启用
     * @param foreground        主题正文前景
     * @param mutedForeground   主题次要/占位前景
     * @param disabledForeground 主题禁用前景
     * @return 文本色 ARGB
     */
    private static int resolveTextColor(Boolean placeholder, Boolean enabled,
                                        int foreground, int mutedForeground, int disabledForeground) {
        if (!Boolean.TRUE.equals(enabled)) {
            return disabledForeground;
        }
        return Boolean.TRUE.equals(placeholder) ? mutedForeground : foreground;
    }

    /**
     * 解析 caret 颜色（B2 槽位感知：仅激活槽位着色）。
     *
     * @param caretVisible caret 是否可见（enabled 且 focused）
     * @param slotActive   槽位是否激活（focus 在本槽侧）
     * @param caretColor   主题聚焦色
     * @return caret 背景色 ARGB
     */
    private static int resolveCaretColor(Boolean caretVisible, boolean slotActive, int caretColor) {
        if (Boolean.TRUE.equals(caretVisible) && slotActive) {
            return caretColor;
        }
        return CARET_TRANSPARENT;
    }

    /**
     * 解析选区高亮背景色：选区激活时显示主题选区背景，否则全透明（纯 PAINT 切换不重排）。
     *
     * @param selectionActive     选区是否激活
     * @param selectionBackground 主题选区背景色
     * @return 高亮背景色 ARGB
     */
    private static int resolveHighlightBackground(Boolean selectionActive, int selectionBackground) {
        if (Boolean.TRUE.equals(selectionActive)) {
            return selectionBackground;
        }
        return CARET_TRANSPARENT;
    }

    /**
     * 解析选区高亮文本色：选区激活时用主题选区前景，否则退回常规文本色。
     *
     * @param selectionActive     选区是否激活
     * @param selectionForeground 主题选区前景色
     * @param normalTextColor     常规态文本色（主题语义前景解析结果）
     * @return 高亮文本色 ARGB
     */
    private static int resolveHighlightTextColor(Boolean selectionActive, int selectionForeground,
                                                 int normalTextColor) {
        if (Boolean.TRUE.equals(selectionActive)) {
            return selectionForeground;
        }
        return normalTextColor;
    }

    /**
     * 解析候选行背景：键盘高亮 &gt; hover &gt; 透明（浮层玻璃底可见）。
     *
     * <p>轻量状态覆盖：取主题语义色做半透明染色，行内不各自采样背景滤镜；键盘高亮强度明显高于
     * hover，保证「当前键盘项」不只靠透明度区分。</p>
     *
     * @param highlighted         是否键盘高亮
     * @param hovered             是否指针悬停
     * @param accent              主题强调色
     * @param selectionBackground 主题选区背景色
     * @return 候选行背景色 ARGB
     */
    private static int resolveItemBackground(boolean highlighted, boolean hovered,
                                             int accent, int selectionBackground) {
        if (highlighted) {
            return tint(selectionBackground, ITEM_HIGHLIGHT_ALPHA);
        }
        if (hovered) {
            return tint(accent, ITEM_HOVER_ALPHA);
        }
        return ITEM_BG_TRANSPARENT;
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

    /**
     * 默认 listbox chrome：浮层根走 OVERLAY 配方，候选行只做轻量状态覆盖。
     * autocomplete 无持久 selected，只有键盘高亮。
     */
    private static final class DefaultListboxChrome implements SceneAutocompletePrimitive.ListboxChrome {
        /** 场景运行时。 */
        private final SceneRuntime rt;

        /**
         * @param rt 场景运行时
         */
        private DefaultListboxChrome(SceneRuntime rt) {
            this.rt = rt;
        }

        @Override
        public void decorateListbox(SceneNode listbox) {
            // 候选浮层：OVERLAY 配方画在浮层根（唯一外观写入者）。
            // enabled 恒真：禁用态由 primitive 的 expanded 守卫处理（禁用不展开）；
            // 配方在 portal 构建调用栈内取，延迟打开时继承来源主题。
            SceneSurfaceBinder.bind(rt, listbox, SceneThemes.surface(rt, SceneTheme.Role.OVERLAY),
                    ALWAYS_ENABLED, rt.interactionState(listbox));
        }

        @Override
        public void decorateItem(SceneAutocompletePrimitive.ItemHandle handle) {
            handle.item().setPadding(ITEM_PADDING);
            handle.item().setCursor(SceneCursor.POINTER);
            ReadableSignal<Integer> accent = SceneThemes.accent(rt);
            ReadableSignal<Integer> selectionBackground = SceneThemes.selectionBackground(rt);
            rt.__bindAnimatedColor(() -> resolveItemBackground(
                            Boolean.TRUE.equals(handle.highlighted().get()),
                            Boolean.TRUE.equals(handle.interaction().hovered().get()),
                            accent.get(), selectionBackground.get()),
                    handle.item()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
            ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
            rt.bindComputed(foreground::get, handle.label()::setTextColor);
        }
    }
}
