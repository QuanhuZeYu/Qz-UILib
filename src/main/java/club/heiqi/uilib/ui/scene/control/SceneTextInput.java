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
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneTextInput —— scene 新栈字符级单行受控文本输入框（B1 核心版）。
 *
 * <h3>B1 范围</h3>
 * <p>本版提供字符级 caret、点击定位、方向键/Home/End 移动，以及 TEXT_INPUT、Backspace、Delete
 * 编辑键（选区、剪贴板、词跳转、caret 闪烁与横向滚动已在后续批次补齐）。</p>
 *
 * <h3>外观归属：表面绑定是唯一写入者</h3>
 * <p>背景/边框/边框宽/圆角/滤镜/实体高度由 {@link SceneSurfaceBinder} 从
 * {@link SceneThemes#surface(SceneRuntime, SceneTheme.Role) INPUT 角色配方}派生，本控件不再静态
 * 设色、不再叠加第二套实色动画（守「一个属性只有一个外观写入者」）。正文/占位/禁用前景与
 * caret、选区色消费主题语义色；显式 {@code placeholderColor} 仍优先。caret 颜色在显式启用
 * Motion 的 runtime 内使用 fast 过渡，表面过渡时长取配方自身。</p>
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
 */
public final class SceneTextInput {

    /** caret 不可见（全透明，纯 PAINT 切换不重排） */
    private static final int CARET_TRANSPARENT = 0x00000000;

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
     * @param maxLength   最大长度，填满后拒绝新增
     * @param inputType   输入类型，控制字符过滤与密码掩码显示
     * @param onChange    文本变更回调，以期望新值真实 String 调用
     * @param placeholderColor 占位文本色（ARGB）；null = 沿用主题 mutedForeground（禁用态用
     *                    disabledForeground）；显式值优先于主题
     * @param maxLengthUnit 长度口径（{@link MaxLengthUnit#CODEPOINT} 默认；UTF16 按 char 单元，
     *                    与原版 maxStringLength 同口径；向后兼容可选）
     * @param blockChars   块字符集合（每个字符为一项被禁字符；null/空 = 不过滤，向后兼容默认）；
     *                    键入/TEXT_INPUT/粘贴统一逐字符剔除，语义与原版 ChatAllowedCharacters
     *                    拒绝表一致（默认空集 = 既有行为零变化）
     * @param fontSize  控件内文字字号（UI 像素，响应式）；null = 不指定，沿用节点默认字号
     */
    @Desugar
    public record Props(
            ReadableSignal<String> value,
            ReadableSignal<Boolean> enabled,
            ReadableSignal<Boolean> readOnly,
            String placeholder,
            int maxLength,
            SceneInputType inputType,
            Consumer<String> onChange,
            Integer placeholderColor,
            MaxLengthUnit maxLengthUnit,
            String blockChars,
            ReadableSignal<Integer> fontSize
    ) {

        /** 向后兼容 7 参构造：placeholderColor = null、maxLengthUnit = CODEPOINT、blockChars = null（行为与旧版一致）。 */
        public Props(ReadableSignal<String> value,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly,
                     String placeholder,
                     int maxLength,
                     SceneInputType inputType,
                     Consumer<String> onChange) {
            this(value, enabled, readOnly, placeholder, maxLength, inputType, onChange, null,
                    MaxLengthUnit.CODEPOINT, null);
        }

        /** 向后兼容 8 参构造：maxLengthUnit = CODEPOINT、blockChars = null（行为与旧版一致）。 */
        public Props(ReadableSignal<String> value,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly,
                     String placeholder,
                     int maxLength,
                     SceneInputType inputType,
                     Consumer<String> onChange,
                     Integer placeholderColor) {
            this(value, enabled, readOnly, placeholder, maxLength, inputType, onChange,
                    placeholderColor, MaxLengthUnit.CODEPOINT, null);
        }

        /** 向后兼容 9 参构造：blockChars = null（行为与旧版一致）。 */
        public Props(ReadableSignal<String> value,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly,
                     String placeholder,
                     int maxLength,
                     SceneInputType inputType,
                     Consumer<String> onChange,
                     Integer placeholderColor,
                     MaxLengthUnit maxLengthUnit) {
            this(value, enabled, readOnly, placeholder, maxLength, inputType, onChange,
                    placeholderColor, maxLengthUnit, null, null);
        }

        /** 向后兼容 10 参构造：fontSize = null（沿用节点默认字号，既有行为零变化）。 */
        public Props(ReadableSignal<String> value,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly,
                     String placeholder,
                     int maxLength,
                     SceneInputType inputType,
                     Consumer<String> onChange,
                     Integer placeholderColor,
                     MaxLengthUnit maxLengthUnit,
                     String blockChars) {
            this(value, enabled, readOnly, placeholder, maxLength, inputType, onChange,
                    placeholderColor, maxLengthUnit, blockChars, null);
        }

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
            /** 最大长度，填满后拒绝新增；默认 {@code Integer.MAX_VALUE} 表示无限制。 */
            private int maxLength = Integer.MAX_VALUE;
            /** 输入类型，控制字符过滤与密码掩码显示。 */
            private SceneInputType inputType = SceneInputType.TEXT;
            /** 文本变更回调，以期望新值真实 String 调用。 */
            private Consumer<String> onChange;
            /** 占位文本色（ARGB）；null = 沿用主题 mutedForeground（禁用态 disabledForeground）。 */
            private Integer placeholderColor;
            /** 长度上限口径（CODEPOINT 默认 / UTF16 按 char 单元）；向后兼容新增。 */
            private MaxLengthUnit maxLengthUnit = MaxLengthUnit.CODEPOINT;
            /** 块字符集合（null/空 = 不过滤）；向后兼容新增。 */
            private String blockChars;
            /** 控件内文字字号；null = 不指定，沿用节点默认字号。 */
            private ReadableSignal<Integer> fontSize;

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
             * @param maxLength 最大长度（按 maxLengthUnit 口径），填满后拒绝新增
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
             * 设置占位文本色（可选；null = 沿用主题 mutedForeground，禁用态用 disabledForeground）。
             *
             * @param placeholderColor 占位文本色（ARGB）
             * @return 当前 builder
             */
            public Builder placeholderColor(Integer placeholderColor) {
                this.placeholderColor = placeholderColor;
                return this;
            }

            /**
             * 设置长度上限口径（可选；默认 {@link MaxLengthUnit#CODEPOINT} 保持旧行为）。
             *
             * @param maxLengthUnit 长度口径
             * @return 当前 builder
             */
            public Builder maxLengthUnit(MaxLengthUnit maxLengthUnit) {
                this.maxLengthUnit = maxLengthUnit == null
                        ? MaxLengthUnit.CODEPOINT : maxLengthUnit;
                return this;
            }

            /**
             * 设置块字符集合（可选；null/空 = 不过滤，保持旧行为）。
             *
             * <p>输入/粘贴路径中的这些字符会被逐字符剔除（语义与原版 ChatAllowedCharacters
             * 拒绝表一致），例如聊天输入框禁用 §(U+00A7) 以防服务器踢「illegal character in chat」。</p>
             *
             * @param blockChars 被禁字符集合（每个字符为一项）
             * @return 当前 builder
             */
            public Builder blockChars(String blockChars) {
                this.blockChars = blockChars;
                return this;
            }

            /**
             * 设置控件内文字字号（构建期定值）。
             *
             * <p>只影响本控件自己画的文字（文本 / placeholder / caret / 度量）；业务方塞进来的
             * 子控件是独立控件，不受影响。</p>
             *
             * @param fontSizePx UI 像素字号
             * @return 当前 builder
             */
            public Builder fontSizePx(int fontSizePx) {
                this.fontSize = Signal.create(Integer.valueOf(fontSizePx));
                return this;
            }

            /**
             * 设置控件内文字字号（运行时可调）。
             *
             * <p>信号变化时文字、caret 与文本度量在同帧内跟随，无需调用方手动标脏。</p>
             *
             * @param fontSize UI 像素字号信号；null = 不指定
             * @return 当前 builder
             */
            public Builder fontSize(ReadableSignal<Integer> fontSize) {
                this.fontSize = fontSize;
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
                return new Props(value, enabled, readOnly, placeholder, maxLength, inputType,
                        onChange, placeholderColor, maxLengthUnit, blockChars, fontSize);
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
                    props.inputType(), props.onChange(), props.maxLengthUnit(), props.blockChars());
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
                props.inputType(), props.onChange(), props.maxLengthUnit(), props.blockChars());
        SceneTextInputPrimitive.Result result = SceneTextInputPrimitive.create(rt, primitiveProps);
        applyChrome(rt, props, result);
        return new Handle(() -> result.root(), result.moveCaretToEndOf());
    }

    /**
     * 挂载通用 chrome（padding、INPUT 表面、主题语义前景、caret 色、选区高亮、cursor 与
     * hitTestable 绑定）——{@link #create} 与 {@link #createHandle} 共享。
     *
     * <p>构造期只捕获主题信号（{@link SceneThemes#surface} 等必须在 builder 内调用），
     * 不在构造期解引用未求值的 Computed；一切取值都发生在 effect 体内。</p>
     */
    private static void applyChrome(SceneRuntime rt, Props props, SceneTextInputPrimitive.Result result) {
        SceneNode root = result.root();
        root.setPadding(PADDING);
        // 控件级字号入口：Props.fontSize() 写控件根的层 2 声明（与句柄入口同一槽，后写者胜出）；
        // 控件内文字沿父链继承，不再逐点接线；值 null = 该声明缺失（回落下一层）。
        final ReadableSignal<Integer> configuredFontSize = props.fontSize();
        if (configuredFontSize != null) {
            Integer initialFontSize = configuredFontSize.get();
            if (initialFontSize != null) {
                root.setFontScope(initialFontSize.intValue());
            }
            rt.bind(configuredFontSize, current -> {
                if (current == null) {
                    root.resetFontScope();
                } else {
                    root.setFontScope(current.intValue());
                }
            });
        }
        SceneInteractionState interaction = rt.interactionState(root);

        // 唯一外观写入者：background/border/borderWidth/cornerRadius/backdrop/surfaceElevation
        // 全归表面绑定，控件不再静态设边框宽/圆角、不再叠加 SceneStateColors 实色绑定。
        SceneSurfaceBinder.bind(rt, root, SceneThemes.surface(rt, SceneTheme.Role.INPUT),
                props.enabled(), interaction);

        // 语义前景：正文 foreground / 占位 mutedForeground / 禁用 disabledForeground；
        // 显式 placeholderColor 仍优先（含禁用态，保持既有优先级）。
        ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
        ReadableSignal<Integer> mutedForeground = SceneThemes.mutedForeground(rt);
        ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);
        rt.bindComputed(() -> resolveTextColor(result.isPlaceholder().get(), props.enabled().get(),
                props.placeholderColor(), foreground.get(), mutedForeground.get(), disabledForeground.get()),
                result.prefixText()::setTextColor);
        rt.bindComputed(() -> resolveTextColor(result.isPlaceholder().get(), props.enabled().get(),
                props.placeholderColor(), foreground.get(), mutedForeground.get(), disabledForeground.get()),
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
                                props.placeholderColor(), foreground.get(), mutedForeground.get(),
                                disabledForeground.get())),
                result.highlightText()::setTextColor);
        SceneControlChrome.bindCursor(rt, root, props.enabled(), SceneCursor.TEXT, SceneCursor.NOT_ALLOWED);
        rt.bind(props.enabled(),
                e -> root.setHitTestable(Boolean.TRUE.equals(e)));
    }

    /**
     * 解析文本色（主题语义色版）。
     *
     * @param placeholder      是否处于 placeholder 状态
     * @param enabled          是否启用
     * @param placeholderColor 显式占位文本色（ARGB）；null = 跟随主题
     * @param foreground       主题正文前景
     * @param mutedForeground  主题次要/占位前景
     * @param disabledForeground 主题禁用前景
     * @return 文本色 ARGB
     */
    private static int resolveTextColor(Boolean placeholder, Boolean enabled, Integer placeholderColor,
                                        int foreground, int mutedForeground, int disabledForeground) {
        if (Boolean.TRUE.equals(placeholder) && placeholderColor != null) {
            return placeholderColor.intValue();
        }
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
     * @param selectionActive    选区是否激活
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
     * @param selectionActive    选区是否激活
     * @param selectionForeground 主题选区前景色
     * @param normalTextColor    常规态文本色（主题语义前景解析结果）
     * @return 高亮文本色 ARGB
     */
    private static int resolveHighlightTextColor(Boolean selectionActive, int selectionForeground,
                                                 int normalTextColor) {
        if (Boolean.TRUE.equals(selectionActive)) {
            return selectionForeground;
        }
        return normalTextColor;
    }
}
