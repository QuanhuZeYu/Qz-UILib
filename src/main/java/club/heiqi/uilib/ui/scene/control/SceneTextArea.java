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
 * SceneTextArea —— scene 新栈多行受控文本输入框（D4：soft wrap 视觉行模型）。
 *
 * <h3>能力范围</h3>
 * <p>支持 Enter 换行、Backspace/Delete 跨行删除、方向键跨视觉行移动 caret、Home/End 视觉行首尾、
 * 点击定位、纵向滚动、placeholder；跨视觉行拖选、Shift 扩展、双击选词、三击选逻辑行、
 * Ctrl+A 全选与选区替换/删除；Ctrl+C/X/V 剪贴板、Ctrl+←/→ 词跳转、Ctrl+Home/End 文首尾、
 * Ctrl+Backspace/Delete 删词、caret 闪烁与纵向跟随；soft wrap 按视口可用宽软换行
 * （横向滚动需求由换行消除）。暂不支持 IME 组合态。</p>
 *
 * <h3>受控契约</h3>
 * <p>文本真值由外部 {@code value} 唯一持有（含 {@code \n}）；控件不缓存 value。内部维护
 * {@code caretIndex} 与 {@link TextSelection} 两个本地 UI 态（caret≡selection.focus）。
 * 所有写入经 {@code onChange.accept(next)} 上抛。</p>
 *
 * <h3>外观归属：表面绑定是唯一写入者</h3>
 * <p>背景/边框/边框宽/圆角/滤镜/实体高度由 {@link SceneSurfaceBinder} 从
 * {@link SceneThemes#surface(SceneRuntime, SceneTheme.Role) INPUT 角色配方}派生；本控件不再
 * 静态设边框宽/圆角、不再叠加 {@code SceneStateColors.inputBackground} 与
 * {@code SceneControlChrome.bindStandardBorder} 第二套写入者。行文本/占位/禁用前景与 caret 色
 * 由 wrapper 捕获的主题语义色信号供给 primitive（行文本由 primitive 内部 forEach 创建，
 * Result 不暴露行节点，故只能经 Props 信号消费，契约 §2.8）。视口不再自绘内层底色，
 * root 玻璃即唯一表面。</p>
 *
 * <h3>结构</h3>
 * <pre>
 * root (COLUMN, clipChildren=true, focusable, padding, INPUT 玻璃表面)
 *   └─ viewport (COLUMN, scrollable, clipChildren, preferredHeight, 透明)
 *        └─ content (COLUMN) ← forEach 行 + placeholder show
 * </pre>
 */
public final class SceneTextArea {

    /** 默认视口高度（像素） */
    public static final int DEFAULT_VIEWPORT_HEIGHT = 120;

    /** 内边距（像素） */
    private static final int PADDING = SceneChromeTokens.PAD_MD;
    /** 视口内边距（像素） */
    private static final int VIEWPORT_PADDING = SceneChromeTokens.PAD_SM;
    /** 视口透明底色：不再自绘内层实色，让 root 的 INPUT 玻璃透出（唯一表面）。 */
    private static final int VIEWPORT_TRANSPARENT = 0x00000000;

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
     * @param fontSize    控件内文字字号（UI 像素，响应式）；null = 不指定，沿用节点默认字号
     */
    @Desugar
    public record Props(
            ReadableSignal<String> value,
            ReadableSignal<Boolean> enabled,
            ReadableSignal<Boolean> readOnly,
            String placeholder,
            int maxLength,
            int viewportHeight,
            Consumer<String> onChange,
            ReadableSignal<Integer> fontSize
    ) {

        /** 向后兼容 7 参构造：fontSize = null（沿用节点默认字号，既有行为零变化）。 */
        public Props(ReadableSignal<String> value,
                     ReadableSignal<Boolean> enabled,
                     ReadableSignal<Boolean> readOnly,
                     String placeholder,
                     int maxLength,
                     int viewportHeight,
                     Consumer<String> onChange) {
            this(value, enabled, readOnly, placeholder, maxLength, viewportHeight, onChange, null);
        }

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
            /** 控件内文字字号；null = 不指定，沿用节点默认字号。 */
            private ReadableSignal<Integer> fontSize;

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
             * 设置控件内文字字号（构建期定值）。
             *
             * <p>只影响本控件自己画的文字（文本 / placeholder / caret / 换行度量）；业务方塞进来的
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
             * <p>信号变化时文字、caret 与换行度量在同帧内跟随，无需调用方手动标脏。</p>
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
                return new Props(value, enabled, readOnly, placeholder, maxLength, viewportHeight, onChange,
                        fontSize);
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
            // 构造期捕获主题信号（此时 Owner.current() 是来源作用域）；一切取值都发生在 effect 体内。
            ReadableSignal<Integer> caretColor = SceneThemes.borderFocus(rt);
            ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
            ReadableSignal<Integer> mutedForeground = SceneThemes.mutedForeground(rt);
            ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);
            // 选区两色与单行输入/自动补全/下拉列表同源（契约 §2.8：不得直接取 SceneChromeTokens.SELECTION_*）。
            ReadableSignal<Integer> selectionBackground = SceneThemes.selectionBackground(rt);
            ReadableSignal<Integer> selectionForeground = SceneThemes.selectionForeground(rt);

            SceneTextAreaPrimitive.Props primitiveProps = new SceneTextAreaPrimitive.Props(
                    props.value(), props.enabled(), props.readOnly(), props.placeholder(),
                    props.maxLength(),
                    // int 色槽仅作旧构造路径的兼容回落：六个语义信号恒非 null，故取值永不参与上色。
                    // 此处读到的是 SceneThemes 带初值的主题派生信号（非「未求值 Computed」）。
                    initialOf(caretColor), initialOf(foreground),
                    initialOf(mutedForeground), initialOf(disabledForeground),
                    props.onChange(),
                    caretColor, foreground, mutedForeground, disabledForeground,
                    selectionBackground, selectionForeground);
            SceneTextAreaPrimitive.Result result = SceneTextAreaPrimitive.create(rt, primitiveProps);
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

            SceneNode viewport = result.viewport();
            viewport.setPreferredHeight(props.viewportHeight() > 0 ? props.viewportHeight() : DEFAULT_VIEWPORT_HEIGHT);
            viewport.setPadding(VIEWPORT_PADDING);
            // 视口不再自绘内层底色（旧 SceneChromeTokens.BG_DEFAULT/BG_DISABLED 已删）：root 的
            // INPUT 玻璃即唯一表面，内层再叠一层实色会挡死玻璃并形成第二个表面。
            viewport.setBackgroundColor(VIEWPORT_TRANSPARENT);

            // B2：interaction 挂 content（primitive 已改），focused 写 content，表面绑定据此派生 focus 缘色。
            SceneInteractionState interaction = rt.interactionState(result.content());

            // 唯一外观写入者：background/border/borderWidth/cornerRadius/backdrop/surfaceElevation
            // 全归表面绑定；不再静态设边框宽/圆角、不再叠加 SceneStateColors.inputBackground
            // 与 SceneControlChrome.bindStandardBorder。
            SceneSurfaceBinder.bind(rt, root, SceneThemes.surface(rt, SceneTheme.Role.INPUT),
                    props.enabled(), interaction);

            // cursor + hitTestable 跟随 enabled
            // B2：cursor 设到 content（hover 写 content，resolver 读 content.cursor）；root hitTestable 保留控制 padding 区命中。
            SceneNode content = result.content();
            SceneControlChrome.bindCursor(rt, content, props.enabled(), SceneCursor.TEXT, SceneCursor.NOT_ALLOWED);
            rt.bind(props.enabled(),
                    e -> root.setHitTestable(Boolean.TRUE.equals(e)));

            return root;
        };
    }

    /**
     * 读取主题派生信号的构造期初值，用作 primitive 的 int 兼容回落值。
     *
     * <p>{@link SceneThemes} 的语义色派生均以「带初值的 {@link club.heiqi.uilib.ui.reactive.Computed}」
     * 构造，构造期 {@code get()} 返回该初值而非 null，因此不违反「构造期不解引用未求值 Computed」。
     * 真正生效的颜色由 primitive 在 effect 派生内读取同名信号决定；主题切换后信号更新，
     * int 回落值不参与。</p>
     *
     * @param signal 主题语义色只读信号
     * @return 构造期初值；异常情况下（信号值未就绪）回落 0
     */
    private static int initialOf(ReadableSignal<Integer> signal) {
        Integer value = signal.get();
        return value != null ? value.intValue() : 0;
    }

}
