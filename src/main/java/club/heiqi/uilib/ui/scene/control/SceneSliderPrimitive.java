package club.heiqi.uilib.ui.scene.control;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneSliderPrimitive —— 无样式连续值滑块行为核心。
 *
 * <p>该 primitive 只负责 slider 的四节点结构、受控连续值拖拽行为、键盘步进、焦点注册和交互态暴露，
 * 不设置任何尺寸、颜色、圆角、背景或 cursor chrome。</p>
 */
public final class SceneSliderPrimitive {

    /** 纯静态工厂，禁止实例化。 */
    private SceneSliderPrimitive() {
    }

    /**
     * 滑块值变更回调 —— 区分预览（拖拽中 committing=false）与提交（释放/键盘 committing=true）。
     */
    @FunctionalInterface
    public interface SliderChange {

        /**
         * 值变更回调。
         *
         * @param value      期望的新值（已 clamp + step 量化）
         * @param committing true=提交（释放/键盘），false=预览（拖拽中）
         */
        void onChange(double value, boolean committing);
    }

    /**
     * Slider primitive 输入契约 —— 受控连续值由外部 signal 驱动，交互经 onChange 输出。
     *
     * @param value    当前值（响应式只读，受控源）
     * @param enabled  是否启用
     * @param min      最小值
     * @param max      最大值
     * @param step     步进，&lt;=0 表示连续不量化
     * @param onChange 值变更回调
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
     * Slider primitive 创建结果，暴露无样式结构节点和 wrapper 挂 chrome 所需派生态。
     *
     * @param root     交互根节点
     * @param track    轨道容器节点
     * @param fillBox  已填充段节点
     * @param thumb    拖拽滑块节点
     * @param progress 当前进度比例 [0,1]
     * @param pressed  是否按压中
     * @param hovered  是否悬停中
     */
    @Desugar
    public record Result(
            SceneNode root,
            SceneNode track,
            SceneNode fillBox,
            SceneNode thumb,
            ReadableSignal<Double> progress,
            ReadableSignal<Boolean> pressed,
            ReadableSignal<Boolean> hovered
    ) {
    }

    /**
     * 创建无样式 Slider primitive。
     *
     * @param rt    场景运行时
     * @param props primitive 输入契约
     * @return 创建结果，供 wrapper 挂载样式
     */
    public static Result create(SceneRuntime rt, Props props) {
        final double min = props.min();
        final double max = props.max();
        final double step = props.step();
        final Signal<Double> draggingValue = Signal.create((Double) null);

        SceneNode root = new SceneNode();
        root.setFlexDirection(FlexDirection.ROW);
        root.setCrossAxisAlign(CrossAxisAlign.CENTER);
        root.setHitTestable(true);

        SceneNode track = new SceneNode();
        track.setFlexDirection(FlexDirection.ROW);
        track.setCrossAxisAlign(CrossAxisAlign.CENTER);
        track.setMainAxisAlign(MainAxisAlign.START);
        track.setHitTestable(false);
        root.appendChild(track);

        SceneNode fillBox = new SceneNode();
        fillBox.setHitTestable(false);
        track.appendChild(fillBox);

        SceneNode thumb = new SceneNode();
        thumb.setHitTestable(false);
        track.appendChild(thumb);

        ReadableSignal<Double> progress = Computed.create(
                () -> progressOf(effectiveValue(draggingValue, props.value(), min), min, max));
        SceneInteractionState is = rt.interactionState(root);

        rt.focusable(root);
        rt.on(root, SceneEventType.POINTER_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            ctx.requestPointerCapture();
            double v = valueFromPointerX(track, ev.getPointerX(), min, max, step);
            draggingValue.set(v);
            props.onChange().onChange(v, false);
        });
        rt.on(root, SceneEventType.POINTER_MOVE, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            if (draggingValue.get() == null) {
                return;
            }
            double v = valueFromPointerX(track, ev.getPointerX(), min, max, step);
            draggingValue.set(v);
            props.onChange().onChange(v, false);
        });
        rt.on(root, SceneEventType.POINTER_UP, (ev, ctx) -> {
            Double dv = draggingValue.get();
            if (dv != null) {
                props.onChange().onChange(dv, true);
            }
            draggingValue.set(null);
        });
        rt.on(root, SceneEventType.POINTER_CANCEL, (ev, ctx) -> {
            if (draggingValue.get() != null) {
                draggingValue.set(null);
            }
        });
        rt.on(root, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            SceneKey key = ev.getKey();
            double delta = (step > 0.0D) ? step : keyboardDefaultStep(min, max);
            Double curObj = props.value().get();
            double cur = (curObj == null) ? min : curObj;
            Double next = null;
            if (key == SceneKey.ARROW_LEFT || key == SceneKey.ARROW_DOWN) {
                next = cur - delta;
            } else if (key == SceneKey.ARROW_RIGHT || key == SceneKey.ARROW_UP) {
                next = cur + delta;
            } else if (key == SceneKey.PAGE_UP) {
                next = cur + delta * 10.0D;
            } else if (key == SceneKey.PAGE_DOWN) {
                next = cur - delta * 10.0D;
            } else if (key == SceneKey.HOME) {
                next = min;
            } else if (key == SceneKey.END) {
                next = max;
            }
            if (next != null) {
                props.onChange().onChange(normalizeValue(next, min, max, step), true);
            }
        });

        return new Result(root, track, fillBox, thumb, progress, is.pressed(), is.hovered());
    }

    /**
     * 计算当前生效值：拖拽期取 draggingValue，否则取外部受控 value。
     *
     * @param draggingValue 瞬态拖拽值 signal（null=未拖拽）
     * @param value         外部受控 value signal
     * @param min           最小值（value 为 null 时兜底）
     * @return 当前生效值
     */
    private static double effectiveValue(Signal<Double> draggingValue,
                                         ReadableSignal<Double> value, double min) {
        Double dv = draggingValue.get();
        if (dv != null) {
            return dv;
        }
        Double v = value.get();
        return (v == null) ? min : v;
    }

    /**
     * 计算进度比例 {@code clamp((v-min)/(max-min),0,1)}（max&lt;=min 时返回 0）。
     *
     * @param v   当前值
     * @param min 最小值
     * @param max 最大值
     * @return 进度比例 [0,1]
     */
    private static double progressOf(double v, double min, double max) {
        double range = max - min;
        if (range <= 0.0D) {
            return 0.0D;
        }
        double p = (v - min) / range;
        if (p < 0.0D) {
            return 0.0D;
        }
        if (p > 1.0D) {
            return 1.0D;
        }
        return p;
    }

    /**
     * 值↔像素映射：由指针 canvas x 和 track 当前布局宽度算量化后的值。
     *
     * @param track    track 节点（读其绝对 x 与布局宽度）
     * @param pointerX 指针 canvas 逻辑 x
     * @param min      最小值
     * @param max      最大值
     * @param step     步进
     * @return 量化 + clamp 后的值
     */
    private static double valueFromPointerX(SceneNode track, int pointerX,
                                            double min, double max, double step) {
        int trackAbsX = absoluteX(track);
        int localX = pointerX - trackAbsX;
        double trackWidth = trackWidth(track);
        double ratio = (trackWidth <= 0.0D) ? 0.0D : localX / trackWidth;
        if (ratio < 0.0D) {
            ratio = 0.0D;
        } else if (ratio > 1.0D) {
            ratio = 1.0D;
        }
        double raw = min + ratio * (max - min);
        return normalizeValue(raw, min, max, step);
    }

    /**
     * 读取 track 当前布局宽度，无缓存时回退 0。
     *
     * @param track track 节点
     * @return track 布局宽度
     */
    private static int trackWidth(SceneNode track) {
        Object cached = track.getCachedLayout();
        if (cached instanceof LayoutBox) {
            return ((LayoutBox) cached).getWidth();
        }
        return 0;
    }

    /**
     * 累加节点及其所有祖先的 LayoutBox 局部 x，得到相对场景树根的绝对 x。
     *
     * @param node 目标节点
     * @return 相对场景树根的绝对 x（像素），无布局缓存的节点按 0 累加
     */
    private static int absoluteX(SceneNode node) {
        int x = 0;
        SceneNode cur = node;
        while (cur != null) {
            Object cached = cur.getCachedLayout();
            if (cached instanceof LayoutBox) {
                x += ((LayoutBox) cached).getX();
            }
            cur = cur.__getParent();
        }
        return x;
    }

    /**
     * 量化 + clamp：先 clamp 到 [min,max]，step&gt;0 时按 step 量化后再 clamp。
     *
     * @param raw  原始值
     * @param min  最小值
     * @param max  最大值
     * @param step 步进，&lt;=0 表示连续不量化
     * @return 量化 + clamp 后的值
     */
    private static double normalizeValue(double raw, double min, double max, double step) {
        double clamped = Math.max(min, Math.min(raw, max));
        if (step <= 0.0D || max <= min) {
            return clamped;
        }
        double stepped = min + Math.round((clamped - min) / step) * step;
        return Math.max(min, Math.min(stepped, max));
    }

    /**
     * 连续模式（step&lt;=0）下的键盘默认步长 = (max-min)/100（range&lt;=0 时回退 1.0）。
     *
     * @param min 最小值
     * @param max 最大值
     * @return 键盘默认步长
     */
    private static double keyboardDefaultStep(double min, double max) {
        double range = max - min;
        return (range <= 0.0D) ? 1.0D : range / 100.0D;
    }
}
