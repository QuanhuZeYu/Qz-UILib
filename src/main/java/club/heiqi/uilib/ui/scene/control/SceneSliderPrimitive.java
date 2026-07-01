package club.heiqi.uilib.ui.scene.control;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
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
 *
 * <p><b>拖拽时序模型（缺陷 D 根治后）</b>：UP/MOVE 的业务值用事件坐标当场算
 * （{@code valueFromPointerX(event)}），绝不读 draggingValue。draggingValue 降级为
 * 纯渲染 signal（只写不读），仅为拖拽期 progress 派生提供视觉接管，松手清 null 回落外部 value。
 * 拖拽会话托管给 Router capture（DOWN 时 requestPointerCapture），capture 期 MOVE 必投递到 track，
 * 故 MOVE handler 不再需要 draggingValue==null 守卫。</p>
 *
 * <h3>B2 hitTestable 改造</h3>
 * <p>root hitTestable=false（命中穿透），track hitTestable=true（交互单元）。
 * interactionState/focusable/on 全挂 track，pressed/hover/focused 写 track。
 * handler 用 {@code ctx.getLocalPointerX()}（track 局部，框架每级重算），rootAbs≠0 不再错位。</p>
 */
public final class SceneSliderPrimitive {

    /** 纯静态工厂，禁止实例化。 */
    private SceneSliderPrimitive() {
    }

    /**
     * 滑块值变更回调 —— 区分预览（拖拽中 committing=false）与提交（释放/键盘 committing=true）。
     *
     * <p>UP 提交的 value 用事件坐标当场算（valueFromPointerX），不依赖 draggingValue 跨帧可见。</p>
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
     * @param interaction 交互状态
     */
    @Desugar
    public record Result(
            SceneNode root,
            SceneNode track,
            SceneNode fillBox,
            SceneNode thumb,
            ReadableSignal<Double> progress,
            SceneInteractionState interaction
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

        SceneNode root = SceneNode.row();
        root.setCrossAxisAlign(CrossAxisAlign.CENTER);
        root.setHitTestable(false);

        SceneNode track = SceneNode.row();
        track.setCrossAxisAlign(CrossAxisAlign.CENTER);
        track.setMainAxisAlign(MainAxisAlign.START);
        track.setHitTestable(true);
        root.appendChild(track);

        SceneNode fillBox = new SceneNode();
        fillBox.setHitTestable(false);
        track.appendChild(fillBox);

        SceneNode thumb = new SceneNode();
        thumb.setHitTestable(false);
        track.appendChild(thumb);

        ReadableSignal<Double> progress = Computed.create(
                () -> progressOf(effectiveValue(draggingValue, props.value(), min), min, max));
        // B2：interactionState/focusable/on 全挂 track（hitTestable=true 的交互单元），
        // 命中 track → pressed/hover 写 track，interactionState(track) 命中 → pressed signal 正确写入。
        SceneInteractionState is = rt.interactionState(track);
        // 显式触发 pressed signal 懒创建：MOVE handler 依赖 pressed 守卫，
        // 必须声明关心 pressed，否则 Router writePressed 因 null 短路跳过，
        // MOVE handler 永远读到 false，守卫误杀正常拖拽。
        is.pressed();

        rt.focusable(track, props.enabled());
        rt.on(track, SceneEventType.POINTER_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            ctx.requestPointerCapture();
            // v 用事件坐标当场算（valueFromPointer），draggingValue.set(v) 仅为渲染。
            // 坐标系（I12 两层）：ctx.getLocalPointerX() = track 局部 X（框架每级重算），rootAbs≠0 不再错位。
            double v = valueFromPointerX(trackWidth(track), ctx.getLocalPointerX(), min, max, step);
            draggingValue.set(v);
            props.onChange().onChange(v, false);
        });
        rt.on(track, SceneEventType.POINTER_MOVE, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            // pressed 守卫：仅 pressed 期处理 MOVE，杜绝松手后非 capture 期 MOVE 污染拖拽态
            // （Router 在 UP 后清 pressedNode/capturedNode，但非 capture 期 MOVE 仍命中 slider track）
            if (!Boolean.TRUE.equals(is.pressed().get())) {
                return;
            }
            // v 用事件坐标当场算，draggingValue.set(v) 仅为渲染（只写不读）。
            // 坐标系（I12 两层）：ctx.getLocalPointerX() = track 局部 X。
            double v = valueFromPointerX(trackWidth(track), ctx.getLocalPointerX(), min, max, step);
            draggingValue.set(v);
            props.onChange().onChange(v, false);
        });
        rt.on(track, SceneEventType.POINTER_UP, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            // 核心修复（缺陷 D）：v 用事件坐标当场算，绝不读 draggingValue。
            // draggingValue 降级为纯渲染 signal（只写不读），UP 不再依赖它跨帧可见。
            // 坐标系（I12 两层）：ctx.getLocalPointerX() = track 局部 X。
            double v = valueFromPointerX(trackWidth(track), ctx.getLocalPointerX(), min, max, step);
            draggingValue.set(null);
            props.onChange().onChange(v, true);
        });
        rt.on(track, SceneEventType.POINTER_CANCEL, (ev, ctx) -> {
            // 只清渲染态，不读 signal，不提交。
            // 无需 enabled 守卫：set(null) 幂等，disabled 时 draggingValue 已是 null。
            draggingValue.set(null);
        });
        rt.on(track, SceneEventType.KEY_DOWN, (ev, ctx) -> {
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

        return new Result(root, track, fillBox, thumb, progress, is);
    }

    /**
     * 计算当前生效值：拖拽期取 draggingValue，否则取外部受控 value。
     *
     * <p>NaN/Infinity 防御：draggingValue 或 value 为非有限值时回退 min，
     * 避免非有限值污染 progress 派生与 fill/thumb 布局。</p>
     *
     * @param draggingValue 瞬态拖拽值 signal（null=未拖拽，纯渲染只写不读）
     * @param value         外部受控 value signal
     * @param min           最小值（value 为 null 或非有限时兜底）
     * @return 当前生效值
     */
    private static double effectiveValue(Signal<Double> draggingValue,
                                         ReadableSignal<Double> value, double min) {
        Double dv = draggingValue.get();
        if (dv != null && Double.isFinite(dv)) {
            return dv;
        }
        Double v = value.get();
        if (v == null || !Double.isFinite(v)) {
            return min;
        }
        return v;
    }

    /**
     * 计算进度比例 {@code clamp((v-min)/(max-min),0,1)}（max&lt;=min 时返回 0）。
     *
     * <p>NaN/Infinity 防御：v 为非有限值时返回 0（等价 progress=min）。</p>
     *
     * @param v   当前值
     * @param min 最小值
     * @param max 最大值
     * @return 进度比例 [0,1]
     */
    private static double progressOf(double v, double min, double max) {
        if (!Double.isFinite(v)) {
            return 0.0D;
        }
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
     * 值↔像素映射：由 track 局部 X 和 track 当前布局宽度算量化后的值。
     *
     * <p>未来 orientation 扩展位：垂直方向时改为读 pointerY 与 track 布局高度，
     * 当前仅水平方向（YAGNI，不实现）。</p>
     *
     * @param trackWidth track 布局宽度
     * @param localX     track 局部 X（= ctx.getLocalPointerX()，框架每级重算）
     * @param min        最小值
     * @param max        最大值
     * @param step       步进
     * @return 量化 + clamp 后的值
     */
    private static double valueFromPointerX(int trackWidth, int localX,
                                            double min, double max, double step) {
        double ratio = (trackWidth <= 0) ? 0.0D : (double) localX / (double) trackWidth;
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
     * 量化 + clamp：先 clamp 到 [min,max]，step&gt;0 时按 step 量化后再 clamp。
     *
     * <p>NaN/Infinity 防御：raw 为非有限值时回退 min。</p>
     *
     * @param raw  原始值
     * @param min  最小值
     * @param max  最大值
     * @param step 步进，&lt;=0 表示连续不量化
     * @return 量化 + clamp 后的值
     */
    private static double normalizeValue(double raw, double min, double max, double step) {
        if (!Double.isFinite(raw)) {
            return min;
        }
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
