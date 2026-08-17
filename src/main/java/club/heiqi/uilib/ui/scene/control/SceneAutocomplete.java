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
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * SceneAutocomplete —— scene 新栈带样式自动补全成品控件。
 *
 * <p>包装 {@link SceneAutocompletePrimitive}：在行为核心之上挂载 TextInput 同款输入框 chrome
 * 与 Select 同款 listbox chrome。调用方无需再手写 {@code applyTextInputChrome} /
 * listbox 装饰，直接 {@code SceneAutocomplete.create(rt, props)} 即可。</p>
 *
 * <h3>与 Primitive 的边界</h3>
 * <ul>
 *   <li>本类负责视觉 chrome（padding/border/bg/text/cursor + listbox/item 高亮）。</li>
 *   <li>{@link SceneAutocompletePrimitive} 保留不删：无样式行为核心 + R13 expanded 独立 Signal。</li>
 *   <li>Props 对齐 TextInput + candidates/matchMode/maxVisible/onSelect；不暴露 chrome 注入口
 *       （成品壳内置默认 chrome）。</li>
 * </ul>
 *
 * <h3>合规守护</h3>
 * <ul>
 *   <li>R1：纯静态工厂 + 零实例字段。</li>
 *   <li>R3：create 体只跑一次（建树 + chrome bind）。</li>
 *   <li>R4：外观经 rt.bind/bindComputed 派生。</li>
 *   <li>R13：expanded 由 primitive 持有独立可写 Signal，本类不派生。</li>
 *   <li>I5：候选项 keyed diff 在 primitive 内完成。</li>
 *   <li>零 config 依赖：不 import 任何 {@code club.heiqi.config.*}。</li>
 * </ul>
 */
public final class SceneAutocomplete {

    /** caret 不可见（全透明，纯 PAINT 切换不重排）。 */
    private static final int CARET_TRANSPARENT = 0x00000000;
    /** 输入框边框宽度。 */
    private static final int BORDER_WIDTH = 1;
    /** 输入框圆角。 */
    private static final int CORNER_RADIUS = SceneChromeTokens.RADIUS_MD;
    /** 输入框内边距。 */
    private static final int PADDING = SceneChromeTokens.PAD_MD;
    /** listbox 边框宽度。 */
    private static final int LISTBOX_BORDER_WIDTH = 1;
    /** listbox 圆角。 */
    private static final int LISTBOX_RADIUS = SceneChromeTokens.RADIUS_LG;
    /** listbox item 内边距。 */
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
     * 给内嵌 TextInput primitive 挂载与 {@link SceneTextInput} 同款 chrome。
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
        root.setBorderWidth(BORDER_WIDTH);
        root.setCornerRadius(CORNER_RADIUS);
        SceneInteractionState interaction = rt.interactionState(root);

        rt.bindComputed(() -> resolveTextColor(result.isPlaceholder().get(), props.enabled().get()),
                result.prefixText()::setTextColor);
        rt.bindComputed(() -> resolveTextColor(result.isPlaceholder().get(), props.enabled().get()),
                result.suffixText()::setTextColor);
        rt.__bindAnimatedColor(() -> SceneStateColors.inputBackground(Boolean.TRUE.equals(props.enabled().get())),
                root::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
        SceneControlChrome.bindStandardBorder(rt, root, props.enabled(), interaction);
        rt.__bindAnimatedColor(() -> Boolean.TRUE.equals(result.caretVisible().get())
                        && result.selection().get().focusCp() == result.selection().get().startCp()
                        ? SceneChromeTokens.BORDER_FOCUS : CARET_TRANSPARENT,
                result.caret()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
        rt.__bindAnimatedColor(() -> Boolean.TRUE.equals(result.caretVisible().get())
                        && result.selection().get().isActive()
                        && result.selection().get().focusCp() == result.selection().get().endCp()
                        ? SceneChromeTokens.BORDER_FOCUS : CARET_TRANSPARENT,
                result.caretAfter()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
        rt.bindComputed(() -> Boolean.TRUE.equals(result.selection().get().isActive())
                        ? SceneChromeTokens.SELECTION_BG : CARET_TRANSPARENT,
                result.highlightText()::setBackgroundColor);
        rt.bindComputed(() -> Boolean.TRUE.equals(result.selection().get().isActive())
                        ? SceneChromeTokens.SELECTION_TEXT
                        : resolveTextColor(result.isPlaceholder().get(), props.enabled().get()),
                result.highlightText()::setTextColor);
        SceneControlChrome.bindCursor(rt, root, props.enabled(), SceneCursor.TEXT, SceneCursor.NOT_ALLOWED);
        rt.bind(props.enabled(), e -> root.setHitTestable(Boolean.TRUE.equals(e)));
    }

    /**
     * 解析文本色（placeholder 次级 / 普通标准）。
     *
     * @param placeholder 是否 placeholder
     * @param enabled     是否启用
     * @return 文本色 ARGB
     */
    private static int resolveTextColor(Boolean placeholder, Boolean enabled) {
        if (Boolean.TRUE.equals(placeholder)) {
            return SceneStateColors.secondaryText(Boolean.TRUE.equals(enabled));
        }
        return SceneStateColors.standardText(Boolean.TRUE.equals(enabled), false);
    }

    /**
     * 默认 listbox chrome：复刻 SceneSelect listbox/item 视觉。
     * autocomplete 无持久 selected，selected 恒 false。
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
            listbox.setBackgroundColor(SceneStateColors.inputBackground(true));
            listbox.setBorderWidth(LISTBOX_BORDER_WIDTH);
            listbox.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);
            listbox.setCornerRadius(LISTBOX_RADIUS);
        }

        @Override
        public void decorateItem(SceneAutocompletePrimitive.ItemHandle handle) {
            handle.item().setPadding(ITEM_PADDING);
            handle.item().setCursor(SceneCursor.POINTER);
            rt.__bindAnimatedColor(() -> SceneStateColors.listItemBackground(
                            true, false,
                            Boolean.TRUE.equals(handle.highlighted().get()),
                            Boolean.TRUE.equals(handle.interaction().hovered().get())),
                    handle.item()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
            rt.bindComputed(() -> SceneStateColors.standardText(true, false),
                    handle.label()::setTextColor);
        }
    }
}
