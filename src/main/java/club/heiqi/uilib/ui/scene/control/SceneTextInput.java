package club.heiqi.uilib.ui.scene.control;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;

/**
 * SceneTextInput —— scene 新栈字符级单行受控文本输入框（B1 核心版）。
 *
 * <h3>B1 范围</h3>
 * <p>本版提供字符级 caret、点击定位、方向键/Home/End 移动，以及 TEXT_INPUT、Backspace、Delete
 * 编辑键。暂不提供选区、剪贴板、IME 组合态、caret 闪烁与横向滚动；背景、边框和 caret
 * 颜色在显式启用 Motion 的 runtime 内使用 fast 过渡。</p>
 *
 * <h3>受控契约</h3>
 * <p>文本真值仍由外部 {@code value} 唯一持有；控件不缓存 value、不自改 value。内部仅维护
 * {@code caretIndex} 本地 UI 状态，语义为真实文本的码点索引。所有写入都只经
 * {@code onChange.accept(next)} 上抛，handler 内不直接改文本节点属性。</p>
 *
 * <h3>结构</h3>
 * <pre>
 * root (ROW, clipChildren=true, focusable, padding)
 *   ├─ prefixText (caret 前显示文本，hitTestable=false)
 *   ├─ caret      (1px 竖线，hitTestable=false)
 *   └─ suffixText (caret 后显示文本，hitTestable=false)
 * </pre>
 *
 * <h3>已知局限</h3>
 * <p>B1 不做横向滚动：root 继续裁剪超出内容，长文本会被裁剪，caret 也可能在可视区域外。</p>
 */
public final class SceneTextInput {

    /** caret 不可见（全透明，纯 PAINT 切换不重排） */
    private static final int CARET_TRANSPARENT = 0x00000000;

    /** 边框宽度（像素） */
    private static final int BORDER_WIDTH = 1;
    /** 圆角半径（像素，小圆角） */
    private static final int CORNER_RADIUS = SceneChromeTokens.RADIUS_MD;
    /** 内边距（像素） */
    private static final int PADDING = SceneChromeTokens.PAD_MD;
    /** 纯静态工厂，禁止实例化。 */
    private SceneTextInput() {
    }

    /**
     * TextInput 输入契约 —— 受控文本：当前文本由外部只读 signal 驱动，
     * 输入经 onChange 交还期望新值真实 String。
     *
     * @param value       当前文本（响应式只读，受控源），控件绝不自己缓存/修改此值
     * @param enabled     是否启用，false 时不可输入且 handler 兜底早退
     * @param readOnly    是否只读，true 时可聚焦/移动 caret，但阻断文本写入
     * @param placeholder 占位文本，value 空串且未聚焦时显示
     * @param maxLength   最大长度（按码点数），填满后拒绝新增
     * @param inputType   输入类型，控制字符过滤与密码掩码显示
     * @param onChange    文本变更回调，以期望新值真实 String 调用
     */
    @Desugar
    public record Props(
            ReadableSignal<String> value,
            ReadableSignal<Boolean> enabled,
            ReadableSignal<Boolean> readOnly,
            String placeholder,
            int maxLength,
            SceneInputType inputType,
            Consumer<String> onChange
    ) {

        /**
         * 创建 Props builder。
         *
         * @param value 当前文本（响应式只读，受控源），控件绝不自己缓存/修改此值
         * @return builder 实例
         */
        public static Builder builder(ReadableSignal<String> value) {
            return new Builder(value);
        }

        /** Props 构建器。 */
        public static final class Builder {
            /** 当前文本（响应式只读，受控源）。 */
            private final ReadableSignal<String> value;
            /** 是否启用，false 时不可输入且 handler 兜底早退。 */
            private ReadableSignal<Boolean> enabled = Signal.create(Boolean.TRUE);
            /** 是否只读，true 时可聚焦/移动 caret，但阻断文本写入。 */
            private ReadableSignal<Boolean> readOnly = Signal.create(Boolean.FALSE);
            /** 占位文本，value 空串且未聚焦时显示。 */
            private String placeholder = "";
            /** 最大长度（按码点数），填满后拒绝新增；默认 {@code Integer.MAX_VALUE} 表示无限制。 */
            private int maxLength = Integer.MAX_VALUE;
            /** 输入类型，控制字符过滤与密码掩码显示。 */
            private SceneInputType inputType = SceneInputType.TEXT;
            /** 文本变更回调，以期望新值真实 String 调用。 */
            private Consumer<String> onChange;

            /**
             * 创建构建器。
             *
             * @param value 当前文本（响应式只读，受控源）
             */
            private Builder(ReadableSignal<String> value) {
                this.value = value;
            }

            /**
             * 设置是否启用。
             *
             * @param enabled 是否启用
             * @return 当前 builder
             */
            public Builder enabled(ReadableSignal<Boolean> enabled) {
                this.enabled = enabled;
                return this;
            }

            /**
             * 设置是否只读。
             *
             * @param readOnly 是否只读
             * @return 当前 builder
             */
            public Builder readOnly(ReadableSignal<Boolean> readOnly) {
                this.readOnly = readOnly;
                return this;
            }

            /**
             * 设置占位文本。
             *
             * @param placeholder 占位文本，value 空串且未聚焦时显示
             * @return 当前 builder
             */
            public Builder placeholder(String placeholder) {
                this.placeholder = placeholder;
                return this;
            }

            /**
             * 设置最大长度。
             *
             * @param maxLength 最大长度（按码点数），填满后拒绝新增
             * @return 当前 builder
             */
            public Builder maxLength(int maxLength) {
                this.maxLength = maxLength;
                return this;
            }

            /**
             * 设置输入类型。
             *
             * @param inputType 输入类型，控制字符过滤与密码掩码显示
             * @return 当前 builder
             */
            public Builder inputType(SceneInputType inputType) {
                this.inputType = inputType;
                return this;
            }

            /**
             * 设置文本变更回调。
             *
             * @param onChange 文本变更回调，以期望新值真实 String 调用
             * @return 当前 builder
             */
            public Builder onChange(Consumer<String> onChange) {
                this.onChange = onChange;
                return this;
            }

            /**
             * 构建 Props。
             *
             * @return Props 实例
             * @throws IllegalArgumentException 当 onChange 未设置（null）时
             */
            public Props build() {
                if (onChange == null) {
                    throw new IllegalArgumentException("onChange must not be null");
                }
                return new Props(value, enabled, readOnly, placeholder, maxLength, inputType, onChange);
            }
        }
    }

    /**
     * TextInput 组件句柄：根节点组件函数 + autocomplete commit 的 caret 对齐窄操作。
     *
     * <p>向后兼容新增（2026-08 Tab 补全）：{@link #create} 行为不变，需要 autocomplete
     * commit 后 caret 对齐到词尾的调用方改用 {@link #createHandle}。</p>
     */
    @Desugar
    public record Handle(Supplier<SceneNode> component, Consumer<String> moveCaretToEndOf) {
    }

    /**
     * 工厂：构建 TextInput 组件函数。
     *
     * @param rt    场景运行时
     * @param props TextInput 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneTextInputPrimitive.Props primitiveProps = new SceneTextInputPrimitive.Props(
                    props.value(), props.enabled(), props.readOnly(), props.placeholder(), props.maxLength(),
                    props.inputType(), props.onChange());
            SceneTextInputPrimitive.Result result = SceneTextInputPrimitive.create(rt, primitiveProps);
            applyChrome(rt, props, result);
            return result.root();
        };
    }

    /**
     * 工厂：构建 TextInput 组件函数并透出 caret 对齐句柄（向后兼容新增）。
     *
     * <p>与 {@link #create} 同实现，额外透出 primitive 的 {@code moveCaretToEndOf}
     * （autocomplete commit 在外部 value signal flush 前同步对齐 caret 的窄操作）。</p>
     *
     * @param rt    场景运行时
     * @param props TextInput 输入契约
     * @return 组件句柄
     */
    public static Handle createHandle(SceneRuntime rt, Props props) {
        SceneTextInputPrimitive.Props primitiveProps = new SceneTextInputPrimitive.Props(
                props.value(), props.enabled(), props.readOnly(), props.placeholder(), props.maxLength(),
                props.inputType(), props.onChange());
        SceneTextInputPrimitive.Result result = SceneTextInputPrimitive.create(rt, primitiveProps);
        applyChrome(rt, props, result);
        return new Handle(() -> result.root(), result.moveCaretToEndOf());
    }

    /**
     * 挂载通用 chrome（padding/border/圆角、文本色、背景、描边、caret 色、选区高亮、
     * cursor 与 hitTestable 绑定）——{@link #create} 与 {@link #createHandle} 共享。
     */
    private static void applyChrome(SceneRuntime rt, Props props, SceneTextInputPrimitive.Result result) {
        SceneNode root = result.root();
        root.setPadding(PADDING);
        root.setBorderWidth(BORDER_WIDTH);
        root.setCornerRadius(CORNER_RADIUS);
        SceneInteractionState interaction = rt.interactionState(root);

        rt.bindComputed(() -> resolveTextColor(result.isPlaceholder().get(), props.enabled().get()),
                result.prefixText()::setTextColor);
        rt.bindComputed(() -> resolveTextColor(result.isPlaceholder().get(), props.enabled().get()),
                result.suffixText()::setTextColor);

        rt.__bindAnimatedColor(() -> SceneStateColors.inputBackground(
                        Boolean.TRUE.equals(props.enabled().get())),
                root::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
        SceneControlChrome.bindStandardBorder(rt, root, props.enabled(), interaction);
        // caret 双槽位：focus 在选区哪一端，哪端着色（B2 选区结构）
        rt.__bindAnimatedColor(() -> resolveCaretColor(result.caretVisible().get(),
                        result.selection().get().focusCp() == result.selection().get().startCp()),
                result.caret()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
        rt.__bindAnimatedColor(() -> resolveCaretColor(result.caretVisible().get(),
                        result.selection().get().isActive()
                                && result.selection().get().focusCp() == result.selection().get().endCp()),
                result.caretAfter()::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);
        // 选区高亮：激活即显示（失焦保留选区可见），文本色反白
        rt.bindComputed(() -> resolveHighlightBackground(result.selection().get().isActive()),
                result.highlightText()::setBackgroundColor);
        rt.bindComputed(() -> resolveHighlightTextColor(result.selection().get().isActive(),
                        result.isPlaceholder().get(), props.enabled().get()),
                result.highlightText()::setTextColor);
        SceneControlChrome.bindCursor(rt, root, props.enabled(), SceneCursor.TEXT, SceneCursor.NOT_ALLOWED);
        rt.bind(props.enabled(),
                e -> root.setHitTestable(Boolean.TRUE.equals(e)));
    }

    /**
     * 解析文本色。
     *
     * @param placeholder 是否处于 placeholder 状态
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
     * 解析 caret 颜色（B2 槽位感知：仅激活槽位着色）。
     *
     * @param caretVisible caret 是否可见（enabled 且 focused）
     * @param slotActive   槽位是否激活（focus 在本槽侧）
     * @return caret 背景色 ARGB
     */
    private static int resolveCaretColor(Boolean caretVisible, boolean slotActive) {
        if (Boolean.TRUE.equals(caretVisible) && slotActive) {
            return SceneChromeTokens.BORDER_FOCUS;
        }
        return CARET_TRANSPARENT;
    }

    /**
     * 解析选区高亮背景色：选区激活时显示统一 token，否则全透明（纯 PAINT 切换不重排）。
     *
     * @param selectionActive 选区是否激活
     * @return 高亮背景色 ARGB
     */
    private static int resolveHighlightBackground(Boolean selectionActive) {
        if (Boolean.TRUE.equals(selectionActive)) {
            return SceneChromeTokens.SELECTION_BG;
        }
        return CARET_TRANSPARENT;
    }

    /**
     * 解析选区高亮文本色：选区激活时反白，否则退回常规文本色。
     *
     * @param selectionActive 选区是否激活
     * @param placeholder     是否 placeholder 态
     * @param enabled         是否启用
     * @return 高亮文本色 ARGB
     */
    private static int resolveHighlightTextColor(Boolean selectionActive, Boolean placeholder,
                                                 Boolean enabled) {
        if (Boolean.TRUE.equals(selectionActive)) {
            return SceneChromeTokens.SELECTION_TEXT;
        }
        return resolveTextColor(placeholder, enabled);
    }
}
