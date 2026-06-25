package club.heiqi.uilib.ui.scene.control;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;

/**
 * SceneTextArea —— scene 新栈多行受控文本输入框（基础版）。
 *
 * <h3>基础版范围</h3>
 * <p>支持 Enter 换行、Backspace/Delete 跨行删除、方向键跨行移动 caret、Home/End 行首行尾、
 * 点击定位、纵向滚动、placeholder。不支持选区、剪贴板、IME、caret 闪烁、自动换行、横向滚动、
 * caret 滚动跟随视口。</p>
 *
 * <h3>受控契约</h3>
 * <p>文本真值由外部 {@code value} 唯一持有（含 {@code \n}）；控件不缓存 value。内部仅维护
 * {@code caretIndex} 全局码点索引。所有写入经 {@code onChange.accept(next)} 上抛。</p>
 *
 * <h3>结构</h3>
 * <pre>
 * root (COLUMN, clipChildren=true, focusable, padding, border, cornerRadius)
 *   └─ viewport (COLUMN, scrollable, clipChildren, preferredHeight)
 *        └─ content (COLUMN) ← forEach 行 + placeholder show
 * </pre>
 */
public final class SceneTextArea {

    /** 默认视口高度（像素） */
    public static final int DEFAULT_VIEWPORT_HEIGHT = 120;

    /** 边框宽度（像素） */
    private static final int BORDER_WIDTH = 1;
    /** 圆角半径（像素） */
    private static final int CORNER_RADIUS = SceneChromeTokens.RADIUS_MD;
    /** 内边距（像素） */
    private static final int PADDING = SceneChromeTokens.PAD_MD;
    /** 视口内边距（像素） */
    private static final int VIEWPORT_PADDING = SceneChromeTokens.PAD_SM;

    /** 纯静态工厂，禁止实例化。 */
    private SceneTextArea() {
    }

    /**
     * TextArea 输入契约 —— 受控多行文本。
     *
     * @param value       当前文本（含 {@code \n} 换行符）
     * @param enabled     是否启用
     * @param readOnly    是否只读
     * @param placeholder 占位文本，value 空串时显示
     * @param maxLength   最大码点数
     * @param viewportHeight 视口固定高度（像素），非正时用默认值
     * @param onChange    文本变更回调
     */
    @Desugar
    public record Props(
            ReadableSignal<String> value,
            ReadableSignal<Boolean> enabled,
            ReadableSignal<Boolean> readOnly,
            String placeholder,
            int maxLength,
            int viewportHeight,
            Consumer<String> onChange
    ) {

        /**
         * 创建 Props builder。
         *
         * @param value 当前文本（含 {@code \n} 换行符）
         * @return builder 实例
         */
        public static Builder builder(ReadableSignal<String> value) {
            return new Builder(value);
        }

        /** Props 构建器。 */
        public static final class Builder {
            /** 当前文本（含 {@code \n} 换行符）。 */
            private final ReadableSignal<String> value;
            /** 是否启用。 */
            private ReadableSignal<Boolean> enabled = Signal.create(Boolean.TRUE);
            /** 是否只读。 */
            private ReadableSignal<Boolean> readOnly = Signal.create(Boolean.FALSE);
            /** 占位文本，value 空串时显示。 */
            private String placeholder = "";
            /** 最大码点数；默认 {@code Integer.MAX_VALUE} 表示无限制。 */
            private int maxLength = Integer.MAX_VALUE;
            /** 视口固定高度（像素），非正时用 {@link #DEFAULT_VIEWPORT_HEIGHT}。 */
            private int viewportHeight = DEFAULT_VIEWPORT_HEIGHT;
            /** 文本变更回调。 */
            private Consumer<String> onChange;

            /**
             * 创建构建器。
             *
             * @param value 当前文本（含 {@code \n} 换行符）
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
             * @param placeholder 占位文本，value 空串时显示
             * @return 当前 builder
             */
            public Builder placeholder(String placeholder) {
                this.placeholder = placeholder;
                return this;
            }

            /**
             * 设置最大码点数。
             *
             * @param maxLength 最大码点数
             * @return 当前 builder
             */
            public Builder maxLength(int maxLength) {
                this.maxLength = maxLength;
                return this;
            }

            /**
             * 设置视口固定高度。
             *
             * @param viewportHeight 视口固定高度（像素），非正时用默认值
             * @return 当前 builder
             */
            public Builder viewportHeight(int viewportHeight) {
                this.viewportHeight = viewportHeight;
                return this;
            }

            /**
             * 设置文本变更回调。
             *
             * @param onChange 文本变更回调
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
                return new Props(value, enabled, readOnly, placeholder, maxLength, viewportHeight, onChange);
            }
        }
    }

    /**
     * 工厂：构建 TextArea 组件函数。
     *
     * @param rt    场景运行时
     * @param props TextArea 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneTextAreaPrimitive.Props primitiveProps = new SceneTextAreaPrimitive.Props(
                    props.value(), props.enabled(), props.readOnly(), props.placeholder(),
                    props.maxLength(),
                    SceneChromeTokens.BORDER_FOCUS,
                    SceneChromeTokens.TEXT_PRIMARY,
                    SceneChromeTokens.TEXT_SECONDARY,
                    SceneChromeTokens.TEXT_DISABLED,
                    props.onChange());
            SceneTextAreaPrimitive.Result result = SceneTextAreaPrimitive.create(rt, primitiveProps);
            SceneNode root = result.root();
            root.setPadding(PADDING);
            root.setBorderWidth(BORDER_WIDTH);
            root.setCornerRadius(CORNER_RADIUS);

            SceneNode viewport = result.viewport();
            viewport.setPreferredHeight(props.viewportHeight() > 0 ? props.viewportHeight() : DEFAULT_VIEWPORT_HEIGHT);
            viewport.setPadding(VIEWPORT_PADDING);

            SceneInteractionState interaction = rt.interactionState(root);

            // 背景色
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveBackgroundColor(props.enabled().get())),
                    root::setBackgroundColor);
            // 边框色（focus border ring）
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveBorderColor(props.enabled().get(), interaction.focused().get())),
                    root::setBorderColor);
            // viewport 背景用更深一档，营造凹陷感
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveViewportBackground(props.enabled().get())),
                    viewport::setBackgroundColor);
            // cursor + hitTestable 跟随 enabled
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> root.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.TEXT : SceneCursor.NOT_ALLOWED));
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> root.setHitTestable(Boolean.TRUE.equals(e)));

            return root;
        };
    }

    /**
     * 解析根节点背景色。
     */
    private static int resolveBackgroundColor(Boolean enabled) {
        return SceneStateColors.inputBackground(Boolean.TRUE.equals(enabled));
    }

    /**
     * 解析视口背景色（比 root 更深一档）。
     */
    private static int resolveViewportBackground(Boolean enabled) {
        if (!Boolean.TRUE.equals(enabled)) {
            return SceneChromeTokens.BG_DISABLED;
        }
        // 视口用 BG_DEFAULT（Slate-700），root 用 BG_PRESSED（Slate-800 凹陷）
        return SceneChromeTokens.BG_DEFAULT;
    }

    /**
     * 解析边框色。
     */
    private static int resolveBorderColor(Boolean enabled, Boolean focused) {
        return SceneStateColors.standardBorder(Boolean.TRUE.equals(enabled), Boolean.TRUE.equals(focused));
    }
}
