package club.heiqi.uilib.ui.scene.control;

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
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;


public final class SceneSlider {

    /**
     * fillBox 圆角（足够大呈胶囊）。
     *
     * <p>只服务进度填充条这一处几何：track/thumb 的圆角由各自表面配方的
     * {@code cornerRadius} 提供，不再共用本常量。</p>
     */
    private static final int FILL_RADIUS = SceneChromeTokens.RADIUS_PILL;

    /** track 固定宽度（像素，值↔像素映射的分母，与旧栈固定尺寸范式一致） */
    private static final int TRACK_WIDTH = 200;
    /** fill 填充条固定高度（像素，细轨视觉） */
    private static final int FILL_HEIGHT = 6;
    /** thumb 固定直径（像素），track 容器高由它撑起 */
    private static final int THUMB_SIZE = 16;

    /**
     * fillBox 最小宽度（像素），下限钉 1 而非 0。
     *
     * <p>{@code setPreferredWidth(0)} 是「不约束→回退 fill 父宽」语义而非「宽 0」，
     * 故 progress=0 时必须给 1px 显式约束，否则 fillBox 撑满 track 把 thumb 反挤到最右。
     * 详见 {@link #computeFillWidth} 注释。</p>
     */
    private static final int FILL_MIN_WIDTH = 1;


    private SceneSlider() {
    }


    @Desugar
    public record Props(
            ReadableSignal<Double> value,
            ReadableSignal<Boolean> enabled,
            double min,
            double max,
            double step,
            SceneSliderPrimitive.SliderChange onChange
    ) {

        /**
         * 创建 Props builder。
         *
         * @param value 当前值（响应式只读，受控源），控件绝不自己缓存此值
         * @return builder 实例
         */
        public static Builder builder(ReadableSignal<Double> value) {
            return new Builder(value);
        }

        /** Props 构建器。 */
        public static final class Builder {
            /** 当前值（响应式只读，受控源）。 */
            private final ReadableSignal<Double> value;
            /** 是否启用（响应式只读），false 时禁用拖拽/键盘并切灰态。 */
            private ReadableSignal<Boolean> enabled = Signal.create(Boolean.TRUE);
            /** 最小值（不可变常量）。 */
            private double min = 0.0D;
            /** 最大值（不可变常量，应 &gt; min）。 */
            private double max = 100.0D;
            /** 步进（不可变常量），&lt;=0 表示连续（不量化）。 */
            private double step = 1.0D;
            /** 值变更回调，预览传 committing=false、提交传 committing=true。 */
            private SceneSliderPrimitive.SliderChange onChange;

            /**
             * 创建构建器。
             *
             * @param value 当前值（响应式只读，受控源）
             */
            private Builder(ReadableSignal<Double> value) {
                this.value = value;
            }

            /**
             * 设置是否启用。
             *
             * @param enabled 是否启用（响应式只读）
             * @return 当前 builder
             */
            public Builder enabled(ReadableSignal<Boolean> enabled) {
                this.enabled = enabled;
                return this;
            }

            /**
             * 设置最小值。
             *
             * @param min 最小值（不可变常量）
             * @return 当前 builder
             */
            public Builder min(double min) {
                this.min = min;
                return this;
            }

            /**
             * 设置最大值。
             *
             * @param max 最大值（不可变常量，应 &gt; min）
             * @return 当前 builder
             */
            public Builder max(double max) {
                this.max = max;
                return this;
            }

            /**
             * 设置步进。
             *
             * @param step 步进（不可变常量），&lt;=0 表示连续（不量化）
             * @return 当前 builder
             */
            public Builder step(double step) {
                this.step = step;
                return this;
            }

            /**
             * 设置值变更回调。
             *
             * @param onChange 值变更回调，预览传 committing=false、提交传 committing=true
             * @return 当前 builder
             */
            public Builder onChange(SceneSliderPrimitive.SliderChange onChange) {
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
                return new Props(value, enabled, min, max, step, onChange);
            }
        }
    }


    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneSliderPrimitive.Props primitiveProps = new SceneSliderPrimitive.Props(
                    props.value(), props.enabled(), props.min(), props.max(), props.step(), props.onChange());
            SceneSliderPrimitive.Result result = SceneSliderPrimitive.create(rt, primitiveProps);
            SceneInteractionState interaction = result.interaction();

            SceneNode root = result.root();
            root.setPreferredWidth(TRACK_WIDTH);

            SceneNode track = result.track();
            track.setPreferredWidth(TRACK_WIDTH);
            // track 的边框宽/边框色/圆角/染色/滤镜/实体高度全部由表面绑定器从 INPUT 配方派生
            // （构造期不再静态设 borderWidth/cornerRadius/borderColor，也不另绑状态色或标准边框）。

            SceneNode fillBox = result.fillBox();
            fillBox.setPreferredHeight(FILL_HEIGHT);
            // 进度条是控件自持几何（不参与表面采样）：圆角保持静态常量。
            fillBox.setCornerRadius(FILL_RADIUS);

            SceneNode thumb = result.thumb();
            thumb.setPreferredWidth(THUMB_SIZE);
            thumb.setPreferredHeight(THUMB_SIZE);
            // thumb 的圆角来自 INDICATOR 强调配方；不再静态设圆角或三态实色绑定。

            // 进度宽度仍由数值驱动（布局语义不变，仅取色改为主题）。
            rt.bindComputed(() -> computeFillWidth(result.progress().get(), TRACK_WIDTH, THUMB_SIZE),
                    fillBox::setPreferredWidth);

            // 进度填充底色：启用取主题强调色，禁用取主题禁用前景色（不再取 SceneChromeTokens 实色）。
            ReadableSignal<Integer> accent = SceneThemes.accent(rt);
            ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);
            rt.bindComputed(() -> Boolean.TRUE.equals(props.enabled().get())
                            ? accent.get()
                            : disabledForeground.get(),
                    fillBox::setBackgroundColor);

            // track 表面：INPUT 角色配方（轨道凹陷），禁用走角色禁用档。唯一外观写入者。
            ReadableSignal<SceneSurfaceStyle> trackSurface = SceneThemes.surface(rt, SceneTheme.Role.INPUT);
            SceneSurfaceBinder.bind(rt, track, trackSurface, props.enabled(), interaction);

            // thumb 表面：INDICATOR 强调配方（tint RGB=主题 accent）；motionRoot=null，
            // 数值定位的 transform 仍归 primitive，绑定器不得触碰。
            ReadableSignal<SceneSurfaceStyle> thumbSurface =
                    SceneThemes.accentSurface(rt, SceneTheme.Role.INDICATOR);
            SceneSurfaceBinder.bind(rt, thumb, null, thumbSurface, props.enabled(), interaction);

            // B2：interaction 挂 track（primitive 已改），hover/pressed/focused 写 track。
            // cursor 也设到 track（SceneCursorResolver 读 hoveredNode=track 的 cursor 属性）。
            SceneControlChrome.bindCursor(rt, track, props.enabled(), SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);

            return root;
        };
    }

    // ==================== 纯函数辅助（无副作用，无实例状态） ====================

    /**
     * 由生效值算 fillBox 宽度（像素）。
     *
     * <p>{@code progress = clamp((v-min)/(max-min),0,1)}，
     * {@code width = clamp(round(W*progress) - thumb/2, FILL_MIN_WIDTH, W)}。
     * 减 thumb/2 使 thumb（紧随 fill）中心骑在 {@code round(W*progress)} 处（负偏移近似）。</p>
     *
     * <p><b>★ 下限取 {@link #FILL_MIN_WIDTH}=1 而非 0 的关键原因</b>：
     * {@link SceneNode#setPreferredWidth} 的 0 是「不约束 → 回退无文本叶 fill 父宽」语义
     * （见 SceneNode 属性槽注释），<b>不是「宽 0」</b>。若 progress 小时返回 0，fillBox 会
     * 撑满 track 全宽 200，把紧随它的 thumb 反向挤到最右——progress 越小 thumb 越靠右，完全反了。
     * 故下限钉死 1px（显式约束、绕开 fill 陷阱），视觉上 1px 等同于 0，且保证 thumb 位置随
     * progress 单调右移。</p>
     *
     * @param progress   当前进度比例 [0,1]
     * @param trackWidth track chrome 宽度
     * @param thumbSize  thumb chrome 直径
     * @return fillBox 首选宽度（像素，[FILL_MIN_WIDTH, TRACK_WIDTH]）
     */
    private static int computeFillWidth(double progress, int trackWidth, int thumbSize) {
        int raw = (int) Math.round(trackWidth * progress) - thumbSize / 2;
        if (raw < FILL_MIN_WIDTH) {
            return FILL_MIN_WIDTH;
        }
        if (raw > trackWidth) {
            return trackWidth;
        }
        return raw;
    }

}
