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
 * 编辑键。暂不提供选区、剪贴板、IME 组合态、caret 闪烁、动画与横向滚动。</p>
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
            SceneNode root = result.root();
            root.setPadding(PADDING);
            root.setBorderWidth(BORDER_WIDTH);
            root.setCornerRadius(CORNER_RADIUS);
            SceneInteractionState interaction = rt.interactionState(root);

            rt.bindComputed(() -> resolveTextColor(result.isPlaceholder().get(), props.enabled().get()),
                    result.prefixText()::setTextColor);
            rt.bindComputed(() -> resolveTextColor(result.isPlaceholder().get(), props.enabled().get()),
                    result.suffixText()::setTextColor);

            rt.bindComputed(() -> resolveBackgroundColor(props.enabled().get()),
                    root::setBackgroundColor);
            rt.bindComputed(() -> resolveBorderColor(props.enabled().get(), interaction.focused().get()),
                    root::setBorderColor);
            rt.bindComputed(() -> resolveCaretColor(result.caretVisible().get()),
                    result.caret()::setBackgroundColor);
            SceneControlChrome.bindCursor(rt, root, props.enabled(), SceneCursor.TEXT, SceneCursor.NOT_ALLOWED);
            rt.bind(props.enabled(),
                    e -> root.setHitTestable(Boolean.TRUE.equals(e)));

            return root;
        };
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
     * 解析根节点背景色。
     *
     * @param enabled 是否启用
     * @return 背景色 ARGB
     */
    private static int resolveBackgroundColor(Boolean enabled) {
        return SceneStateColors.inputBackground(Boolean.TRUE.equals(enabled));
    }

    /**
     * 解析边框色。
     *
     * @param enabled 是否启用
     * @param focused 是否聚焦
     * @return 边框色 ARGB
     */
    private static int resolveBorderColor(Boolean enabled, Boolean focused) {
        return SceneStateColors.standardBorder(Boolean.TRUE.equals(enabled), Boolean.TRUE.equals(focused));
    }

    /**
     * 解析 caret 颜色。
     *
     * @param caretVisible caret 是否可见
     * @return caret 背景色 ARGB
     */
    private static int resolveCaretColor(Boolean caretVisible) {
        if (Boolean.TRUE.equals(caretVisible)) {
            return SceneChromeTokens.BORDER_FOCUS;
        }
        return CARET_TRANSPARENT;
    }
}
