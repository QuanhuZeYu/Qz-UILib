package club.heiqi.uilib.ui.scene.control;

import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;

/**
 * SceneSlider —— scene 新栈控件层 Phase 4 批 3 迁移控件（水平连续数值滑块）。
 *
 * <h3>定位：受控连续值控件（契约 R7 连续版）</h3>
 * <p>当前值由外部 {@code value} 只读 signal 驱动，控件<b>零内部受控状态</b>。
 * 交互时只经 {@code onChange(value, committing)} 把「期望的新值」交还外部，
 * 由外部决定是否 set 回 value signal。控件自身唯一的本地态是
 * <b>瞬态拖拽值 {@code draggingValue}</b>（由 {@link SceneSliderPrimitive} 内部创建，
 * null=未拖拽，仅 pointerCapture 生命周期内存活，被 handler 闭包捕获、归 Owner 作用域），
 * 它不是控件类字段（守 R1 零实例字段），仅在拖拽期临时接管渲染，松手清 null 自动回落外部 value。</p>
 *
 * <h3>渲染派生（守 R7 受控零状态）</h3>
 * <p>统一读 {@code effectiveValue = draggingValue!=null ? draggingValue : value}，
 * 由它算 {@code progress = clamp((effectiveValue-min)/(max-min),0,1)}。非拖拽期 draggingValue==null，
 * 完全由外部 value 驱动；拖拽期临时接管，松手清 null 回落外部 value（即使外部不回写）。
 * 所有外观变化经 {@code rt.bind(computed(...))}，绝不在 handler 里直接 setXxx（R4）。</p>
 *
 * <h3>结构</h3>
 * <pre>
 * root (ROW, crossAxisAlign=CENTER, 交互单元 hitTestable=true)   ← 绑 interactionState
 *   └─ track (ROW, crossAxisAlign=CENTER, preferredWidth=200, 圆角, 装饰穿透)
 *         ├─ fillBox (叶, preferredWidth 动态=round(W*progress)-thumb/2, 装饰穿透)  ← 进度填充
 *         └─ thumb   (叶, THUMB_SIZE 圆, 装饰穿透)                                  ← 紧随 fill 推到 progress 位置
 * </pre>
 * <p>track/fill/thumb 全部 {@code setHitTestable(false)}，命中穿透到 root（交互单元）——R6。
 * thumb 骑中心用负偏移近似：fillBox 宽减 thumbSize/2，使 thumb 中心落在 {@code round(W*progress)}。</p>
 *
 * <h3>拖拽手势（pointerCapture，committing 双语义）</h3>
 * <ul>
 *   <li>POINTER_DOWN：{@code requestPointerCapture}，按命中 x 算初值，draggingValue.set(初值)，onChange(初值, committing=false)</li>
 *   <li>POINTER_MOVE（capture 期间强制投递到 root）：按指针 x 算新值，draggingValue.set(newV)，onChange(newV, committing=false)——预览</li>
 *   <li>POINTER_UP：onChange(draggingValue, committing=true)，再 draggingValue.set(null)——提交后归还外部</li>
 *   <li>POINTER_CANCEL：draggingValue.set(null)——取消不提交</li>
 * </ul>
 *
 * <h3>键盘步进（focusable + KEY_DOWN，离散提交 committing=true，不走 draggingValue）</h3>
 * <p>←/↓ 减、→/↑ 加（步长 = step>0?step:(max-min)/100）；PageUp 加 10×、PageDown 减 10×；Home→min、End→max。
 * 每次读 {@code value.get()} 算相邻值（读 signal 合法 I11），onChange(量化+clamp 后的 newV, committing=true)。</p>
 *
 * <h3>契约</h3>
 * <p>R1 纯静态工厂零实例字段 / R2 Props 只读 signal + 不可变常量 + 回调 / R3 组件函数只执行一次 /
 * R4 外观随状态经 bind 派生 / R5 交互态读 interactionState / R6 装饰子节点命中穿透 /
 * R7 受控连续零内部受控状态。</p>
 */
public final class SceneSlider {

    /** track 圆角（足够大呈胶囊） */
    private static final int SLIDER_RADIUS = SceneChromeTokens.RADIUS_PILL;

    /** focus ring 边框宽度（像素） */
    private static final int BORDER_WIDTH = 1;

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

    /** 纯静态工厂，禁止实例化（强制无状态，契约 R1） */
    private SceneSlider() {
    }

    /**
     * 滑块值变更回调 —— 区分预览（拖拽中 committing=false）与提交（释放/键盘 committing=true）。
     */
    @FunctionalInterface
    public interface SliderChange extends SceneSliderPrimitive.SliderChange {

        /**
         * 值变更回调。
         *
         * @param value      期望的新值（已 clamp + step 量化）
         * @param committing true=提交（释放/键盘），false=预览（拖拽中）
         */
        void onChange(double value, boolean committing);
    }

    /**
     * Slider 输入契约 —— 受控连续：当前值由外部只读 signal 驱动，
     * 交互经 onChange 交还期望新值（契约 R2/R7）。
     *
     * @param value    当前值（响应式只读，受控源），控件绝不自己缓存此值
     * @param enabled  是否启用（响应式只读），false 时禁用拖拽/键盘并切灰态
     * @param min      最小值（不可变常量）
     * @param max      最大值（不可变常量，应 &gt; min）
     * @param step     步进（不可变常量），&lt;=0 表示连续（不量化）
     * @param onChange 值变更回调，预览传 committing=false、提交传 committing=true
     */
    @Desugar
    public record Props(
            ReadableSignal<Double> value,
            ReadableSignal<Boolean> enabled,
            double min,
            double max,
            double step,
            SliderChange onChange
    ) {
    }

    /**
     * 工厂：构建 Slider 组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（R3）：
     * 建树 + 设静态属性 + 体内创建瞬态 {@code draggingValue} signal（被 handler 闭包捕获，
     * 归 Owner 作用域，非控件类字段守 R1）。动态外观全落 {@code bind(computed(...))}，
     * 拖拽/键盘交互只经 {@code draggingValue.set} 或 {@code onChange} 回调（R4/R5/R7）。</p>
     *
     * @param rt    场景运行时
     * @param props Slider 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
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
            track.setCornerRadius(SLIDER_RADIUS);
            track.setBorderWidth(BORDER_WIDTH);
            track.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);

            SceneNode fillBox = result.fillBox();
            fillBox.setPreferredHeight(FILL_HEIGHT);
            fillBox.setCornerRadius(SLIDER_RADIUS);

            SceneNode thumb = result.thumb();
            thumb.setPreferredWidth(THUMB_SIZE);
            thumb.setPreferredHeight(THUMB_SIZE);
            thumb.setCornerRadius(SLIDER_RADIUS);

            rt.bind(Invalidation.LAYOUT,
                    Computed.create(() -> computeFillWidth(result.progress().get(), TRACK_WIDTH, THUMB_SIZE)),
                    fillBox::setPreferredWidth);
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> SceneStateColors.standardBackground(
                            Boolean.TRUE.equals(props.enabled().get()), false, false)),
                    track::setBackgroundColor);
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> Boolean.TRUE.equals(props.enabled().get())
                            ? SceneChromeTokens.ACCENT_PROGRESS
                            : SceneChromeTokens.BG_DISABLED),
                    fillBox::setBackgroundColor);
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> SceneStateColors.standardBorder(
                            Boolean.TRUE.equals(props.enabled().get()),
                            Boolean.TRUE.equals(interaction.focused().get()))),
                    track::setBorderColor);
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> SceneStateColors.thumbBackground(
                            Boolean.TRUE.equals(props.enabled().get()),
                            Boolean.TRUE.equals(interaction.hovered().get()),
                            Boolean.TRUE.equals(interaction.pressed().get()))),
                    thumb::setBackgroundColor);
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> root.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.POINTER : SceneCursor.NOT_ALLOWED));

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
