package club.heiqi.uilib.ui.scene.control;

import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;


public final class SceneButton {

    /**
     * 内边距（像素）
     */
    private static final int PADDING = SceneChromeTokens.PAD_MD;


    private SceneButton() {
    }


    @Desugar
    public record Props(
        ReadableSignal<String> label,
        ReadableSignal<Boolean> enabled,
        Runnable onClick,
        SceneButtonVariant variant,
        ReadableSignal<SceneSurfaceStyle> surface,
        ReadableSignal<Integer> fontSize,
        boolean stopClickPropagation
    ) {
        /**
         * 兼容三参构造器：variant 默认 STANDARD、配方跟随主题，保持旧调用方零改动。
         *
         * @param label   文本内容
         * @param enabled 启用信号
         * @param onClick 点击回调
         */
        public Props(ReadableSignal<String> label, ReadableSignal<Boolean> enabled, Runnable onClick) {
            this(label, enabled, onClick, SceneButtonVariant.STANDARD, null, null, false);
        }

        /**
         * 兼容四参构造器：配方跟随主题。
         *
         * @param label   文本内容
         * @param enabled 启用信号
         * @param onClick 点击回调
         * @param variant 视觉变体
         */
        public Props(ReadableSignal<String> label, ReadableSignal<Boolean> enabled, Runnable onClick,
                SceneButtonVariant variant) {
            this(label, enabled, onClick, variant, null, null, false);
        }

        /**
         * 兼容五参构造器：字号不指定（沿用节点默认字号）。
         *
         * @param label   文本内容
         * @param enabled 启用信号
         * @param onClick 点击回调
         * @param variant 视觉变体
         * @param surface 显式表面配方（null = 跟随当前主题的按钮角色）
         */
        public Props(ReadableSignal<String> label, ReadableSignal<Boolean> enabled, Runnable onClick,
                SceneButtonVariant variant, ReadableSignal<SceneSurfaceStyle> surface) {
            this(label, enabled, onClick, variant, surface, null, false);
        }

        /**
         * 创建 Props builder。
         *
         * @param label 文本内容（响应式只读）
         * @return builder 实例
         */
        public static Builder builder(ReadableSignal<String> label) {
            return new Builder(label);
        }

        /**
         * Props 构建器 —— 收敛可选参数，避免 record 构造器随分量增长而继续膨胀。
         *
         * <p>未设置的字段与旧三参构造路径同义（启用、STANDARD、配方跟随主题、不指定字号），
         * 因此经 builder 构建且不调任何可选方法的 Props，行为与 {@code new Props(label, enabled, onClick)} 一致。</p>
         */
        public static final class Builder {
            /** 文本内容（响应式只读）。 */
            private final ReadableSignal<String> label;
            /** 是否启用；默认启用（与旧三参构造路径一致）。 */
            private ReadableSignal<Boolean> enabled = Signal.create(Boolean.TRUE);
            /** 动作输出回调；默认 null，由调用方提供（record 构造器同样不校验）。 */
            private Runnable onClick;
            /** 视觉变体；默认 {@link SceneButtonVariant#STANDARD}。 */
            private SceneButtonVariant variant = SceneButtonVariant.STANDARD;
            /** 显式表面配方；null = 跟随当前主题的按钮角色。 */
            private ReadableSignal<SceneSurfaceStyle> surface;
            /** 控件内文字字号；null = 不指定，沿用节点默认字号。 */
            private ReadableSignal<Integer> fontSize;
            /** CLICK 是否止于按钮；默认 false = 照常冒泡（与旧构造路径一致）。 */
            private boolean stopClickPropagation;

            /**
             * 创建构建器。
             *
             * @param label 文本内容（响应式只读）
             */
            private Builder(ReadableSignal<String> label) {
                this.label = label;
            }

            /** @param enabled 是否启用（响应式只读）
             * @return 当前 builder */
            public Builder enabled(ReadableSignal<Boolean> enabled) {
                this.enabled = enabled;
                return this;
            }

            /** @param onClick 点击或 Enter/Space 激活时触发的回调
             * @return 当前 builder */
            public Builder onClick(Runnable onClick) {
                this.onClick = onClick;
                return this;
            }

            /** @param variant 视觉变体（null = STANDARD）
             * @return 当前 builder */
            public Builder variant(SceneButtonVariant variant) {
                this.variant = variant;
                return this;
            }

            /** @param surface 显式表面配方（null = 跟随当前主题的按钮角色）
             * @return 当前 builder */
            public Builder surface(ReadableSignal<SceneSurfaceStyle> surface) {
                this.surface = surface;
                return this;
            }

            /**
             * 设置 CLICK 是否止于本按钮。
             *
             * <p>行内 / 卡内等「容器自身也响应点击」的位置传 {@code true}，避免点按钮时
             * CLICK 冒泡到容器、连带触发容器动作。</p>
             *
             * @param stopClickPropagation true = 不向祖先链冒泡
             * @return 当前 builder
             */
            public Builder stopClickPropagation(boolean stopClickPropagation) {
                this.stopClickPropagation = stopClickPropagation;
                return this;
            }

            /**
             * 设置控件内文字字号（构建期定值）。
             *
             * <p>只影响本控件自己画的文字（内部 label / caret / 度量）；业务方塞进来的
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
             */
            public Props build() {
                return new Props(label, enabled, onClick, variant, surface, fontSize, stopClickPropagation);
            }
        }
    }


    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneButtonPrimitive.Props primitiveProps = new SceneButtonPrimitive.Props(
                props.label(), props.enabled(), props.onClick(), props.stopClickPropagation());
            SceneButtonPrimitive.Result result = SceneButtonPrimitive.create(rt, primitiveProps);
            SceneNode root = result.root();
            SceneInteractionState interaction = result.interaction();
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

            final SceneButtonVariant variant = props.variant() == null
                    ? SceneButtonVariant.STANDARD : props.variant();
            ReadableSignal<SceneSurfaceStyle> surface = props.surface() != null
                    ? props.surface()
                    : SceneThemes.surface(rt, role(variant));

            // 唯一外观写入者：表面绑定独占 background/border/borderWidth/cornerRadius/backdrop/
            // surfaceElevation；motionRoot=label 承担内容微移与禁用透明度，业务内容保留自身变换。
            SceneSurfaceBinder.bind(rt, root, result.label(), surface, props.enabled(), interaction);
            // 文字色：配方前景优先（按钮角色默认已带主题语义色），否则回落正文色。
            SceneSurfaceBinder.bindForeground(rt, result.label(), surface, SceneChromeTokens.TEXT_PRIMARY);

            SceneControlChrome.bindCursor(rt, root, props.enabled(), SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);

            return root;
        };
    }

    /** variant → 主题按钮角色映射。 */
    private static SceneTheme.Role role(SceneButtonVariant variant) {
        switch (variant) {
            case PRIMARY: return SceneTheme.Role.BUTTON_PRIMARY;
            case DANGER: return SceneTheme.Role.BUTTON_DANGER;
            default: return SceneTheme.Role.BUTTON_STANDARD;
        }
    }
}
