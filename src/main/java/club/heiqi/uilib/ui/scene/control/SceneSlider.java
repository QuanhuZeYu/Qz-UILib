package club.heiqi.uilib.ui.scene.control;

import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneSlider —— scene 新栈控件层 Phase 4 批 3 迁移控件（水平连续数值滑块）。
 *
 * <h3>定位：受控连续值控件（契约 R7 连续版）</h3>
 * <p>当前值由外部 {@code value} 只读 signal 驱动，控件<b>零内部受控状态</b>。
 * 交互时只经 {@code onChange(value, committing)} 把「期望的新值」交还外部，
 * 由外部决定是否 set 回 value signal。控件自身唯一的本地态是
 * <b>瞬态拖拽值 {@code draggingValue}</b>（{@code create()} 体内 {@link Signal}，
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

    // ==================== 配色（grounded 常量，参考旧栈 DocumentSliderControl） ====================

    /** enabled track 轨道背景（深石板灰） */
    private static final int TRACK_ENABLED = 0xFF334155;
    /** disabled track 轨道背景（更暗灰） */
    private static final int TRACK_DISABLED = 0xFF1F2937;

    /** enabled fill 已填充段背景（亮天蓝） */
    private static final int FILL_ENABLED = 0xFF38BDF8;
    /** disabled fill 已填充段背景（与 disabled track 同色） */
    private static final int FILL_DISABLED = 0xFF1F2937;

    /** enabled + 默认态 thumb 颜色（极浅蓝白） */
    private static final int THUMB_ENABLED = 0xFFE0F2FE;
    /** enabled + hover 态 thumb 颜色（纯白） */
    private static final int THUMB_HOVER = 0xFFFFFFFF;
    /** enabled + pressed/dragging 态 thumb 颜色（浅蓝） */
    private static final int THUMB_PRESSED = 0xFFBAE6FD;
    /** disabled 态 thumb 颜色（灰蓝） */
    private static final int THUMB_DISABLED = 0xFF64748B;

    /** track 圆角（足够大呈胶囊） */
    private static final int CAPSULE_RADIUS = 999;

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
            // 不可变常量 Props 捕获为 final 局部，供闭包与纯函数用（R2）
            final double min = props.min();
            final double max = props.max();
            final double step = props.step();

            // 瞬态拖拽值：null=未拖拽，仅 pointerCapture 生命周期内存活；
            // 被下方 handler 闭包捕获、归 Owner 作用域（非控件类字段，守 R1，与 Toggle 范式同灵魂）
            final Signal<Double> draggingValue = Signal.create((Double) null);

            // ① 建树一次（无副作用，I3）—— 纯结构 + 静态样式
            SceneNode root = new SceneNode();
            root.setFlexDirection(FlexDirection.ROW);
            root.setCrossAxisAlign(CrossAxisAlign.CENTER);
            // ★ root 必须收窄到 track 宽：root 是唯一 hitTestable=true 交互单元（track/fill/thumb 全穿透），
            //   其命中区即整个 LayoutBox。若不设 preferredWidth，容器分支按 fill 语义把 root 拉满可用宽
            //   （demo 被 host STRETCH 拉满、测试拉满 400），而 track 仅 200 宽 START 左对齐——
            //   root 右侧 [200, 可用宽) 空白也是命中区，点它会触发 POINTER_DOWN，
            //   valueFromPointerX 算 localX>200 → ratio clamp 到 1 → 值跳 max（「点右边空白滑块跳满」）。
            //   设 preferredWidth=TRACK_WIDTH 使命中区=视觉区，消除右侧隐形命中带。
            //   注：root 在 demo 的 COLUMN+STRETCH host 内时，computeWidth 对设了 cross 向 preferred 的
            //   子节点在 STRETCH 分支予以豁免，故 root 稳定保持 200 不被拉宽（见 rootHitWidthEqualsTrackWidth 断言）。
            root.setPreferredWidth(TRACK_WIDTH);

            // track：ROW 容器，固定宽 200，子节点 [fillBox, thumb]，装饰穿透
            SceneNode track = new SceneNode();
            track.setFlexDirection(FlexDirection.ROW);
            track.setCrossAxisAlign(CrossAxisAlign.CENTER);
            track.setMainAxisAlign(MainAxisAlign.START);
            track.setPreferredWidth(TRACK_WIDTH);
            track.setCornerRadius(CAPSULE_RADIUS);
            track.setHitTestable(false);
            root.appendChild(track);

            // fillBox：进度填充段，宽随 progress 动态（LAYOUT 级），装饰穿透
            SceneNode fillBox = new SceneNode();
            fillBox.setPreferredHeight(FILL_HEIGHT);
            fillBox.setCornerRadius(CAPSULE_RADIUS);
            fillBox.setHitTestable(false);
            track.appendChild(fillBox);

            // thumb：圆形滑块，紧随 fillBox（MainAxisAlign START），靠 fillBox 宽推到 progress 位置；装饰穿透
            SceneNode thumb = new SceneNode();
            thumb.setPreferredWidth(THUMB_SIZE);
            thumb.setPreferredHeight(THUMB_SIZE);
            thumb.setCornerRadius(CAPSULE_RADIUS);
            thumb.setHitTestable(false);
            track.appendChild(thumb);

            // ② 交互态：读 Router 权威 signal（挂在交互单元 root 上），绝不自维护 boolean（契约 R5）
            SceneInteractionState is = rt.interactionState(root);

            // ③ 动态外观全走 bind（契约 R4）
            //    fillBox 宽：由 effectiveValue 算 progress → round(W*progress)-thumb/2（clamp 不负）（LAYOUT 级）
            rt.bind(Invalidation.LAYOUT,
                    Computed.create(() -> computeFillWidth(
                            effectiveValue(draggingValue, props.value(), min), min, max)),
                    fillBox::setPreferredWidth);

            // track 背景：enabled/disabled（PAINT 级）
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> track.setBackgroundColor(Boolean.TRUE.equals(e) ? TRACK_ENABLED : TRACK_DISABLED));

            // fill 背景：enabled/disabled（PAINT 级）
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> fillBox.setBackgroundColor(Boolean.TRUE.equals(e) ? FILL_ENABLED : FILL_DISABLED));

            // thumb 背景：enabled × pressed × hovered 四态，优先级 disabled > pressed > hover > enabled（PAINT 级）
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveThumbColor(
                            props.enabled().get(),
                            is.pressed().get(),
                            is.hovered().get())),
                    thumb::setBackgroundColor);

            // cursor 声明式附着：enabled 指针手型、disabled 禁止符号
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> root.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.POINTER : SceneCursor.NOT_ALLOWED));

            // ④ 拖拽手势（pointerCapture，committing 双语义）—— handler 只 signal.set / 调 onChange 回调（R4）
            //    POINTER_DOWN：捕获 + 按命中 x 算初值 + 预览
            rt.on(root, SceneEventType.POINTER_DOWN, (ev, ctx) -> {
                if (!Boolean.TRUE.equals(props.enabled().get())) {
                    return;
                }
                ctx.requestPointerCapture();
                double v = valueFromPointerX(track, ev.getPointerX(), min, max, step);
                draggingValue.set(v);
                props.onChange().onChange(v, false);
            });

            // POINTER_MOVE（capture 期间强制投递到 root）：按指针 x 算新值 + 预览
            rt.on(root, SceneEventType.POINTER_MOVE, (ev, ctx) -> {
                if (!Boolean.TRUE.equals(props.enabled().get())) {
                    return;
                }
                // 仅拖拽期（draggingValue 非 null）响应 move，非拖拽期的悬停移动忽略
                if (draggingValue.get() == null) {
                    return;
                }
                double v = valueFromPointerX(track, ev.getPointerX(), min, max, step);
                draggingValue.set(v);
                props.onChange().onChange(v, false);
            });

            // POINTER_UP：提交（committing=true）后清 draggingValue 归还外部 value
            //
            // ★ 时序铁律：Signal.set 走 queueWrite，flush 时才生效；同一 route 内 set 后 get 仍读旧值。
            //   跨帧拖拽（真机常态：DOWN 帧 flush 后 draggingValue 非 null，UP 帧 get 拿到拖拽值）下，
            //   读 draggingValue.get() 提交正确。
            //   边界：同一物理帧内 DOWN+UP（未经 flush 的 0ms 单击）时 get 读到 null，会漏这次提交；
            //   但下方 draggingValue.set(null) <b>无条件执行</b>兜底，覆盖 DOWN 排入的初值写，防止
            //   「draggingValue 永久停在初值、capture 已释放再也清不掉」的泄漏（净结果：状态干净、回落外部 value）。
            rt.on(root, SceneEventType.POINTER_UP, (ev, ctx) -> {
                Double dv = draggingValue.get();
                if (dv != null) {
                    // 提交期不再判 enabled：拖拽已在 DOWN（enabled 时）建立，提交是其自然收尾
                    props.onChange().onChange(dv, true);
                }
                // 无条件清：防同帧 DOWN+UP 泄漏；非拖拽 UP（dv==null）set(null) 为幂等无害写
                draggingValue.set(null);
            });

            // POINTER_CANCEL：取消，不提交，仅清 draggingValue 回落外部 value
            rt.on(root, SceneEventType.POINTER_CANCEL, (ev, ctx) -> {
                if (draggingValue.get() != null) {
                    draggingValue.set(null);
                }
            });

            // ⑤ 键盘步进：focusable + KEY_DOWN，离散提交 committing=true（不走 draggingValue）
            rt.focusable(root);
            rt.on(root, SceneEventType.KEY_DOWN, (ev, ctx) -> {
                if (!Boolean.TRUE.equals(props.enabled().get())) {
                    return;
                }
                SceneKey key = ev.getKey();
                double delta = (step > 0.0D) ? step : keyboardDefaultStep(min, max);
                // 读受控当前值算相邻值（读 signal 合法 I11；null 防御回退 min）
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
                    // 键盘是离散提交：clamp + 量化后上抛 committing=true
                    props.onChange().onChange(normalizeValue(next, min, max, step), true);
                }
            });

            return root;
        };
    }

    // ==================== 纯函数辅助（无副作用，无实例状态） ====================

    /**
     * 计算当前生效值：拖拽期取 draggingValue，否则取外部受控 value（守 R7 受控零状态）。
     *
     * @param draggingValue 瞬态拖拽值 signal（null=未拖拽）
     * @param value         外部受控 value signal
     * @param min           最小值（value 为 null 时的兜底）
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
     * @param effective 当前生效值
     * @param min       最小值
     * @param max       最大值
     * @return fillBox 首选宽度（像素，[FILL_MIN_WIDTH, TRACK_WIDTH]）
     */
    private static int computeFillWidth(double effective, double min, double max) {
        double progress = progressOf(effective, min, max);
        int raw = (int) Math.round(TRACK_WIDTH * progress) - THUMB_SIZE / 2;
        if (raw < FILL_MIN_WIDTH) {
            return FILL_MIN_WIDTH;
        }
        if (raw > TRACK_WIDTH) {
            return TRACK_WIDTH;
        }
        return raw;
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
     * 值↔像素映射：由指针 canvas x 算量化后的值（参考旧栈 valueFromDocumentX 语义）。
     *
     * <p>{@code localX = pointerX - absoluteX(track)}，{@code ratio = clamp(localX/W,0,1)}，
     * {@code raw = min + ratio*(max-min)}，再经 {@link #normalizeValue} 做 step 量化 + clamp。
     * track 绝对 x 由 {@link #absoluteX} 累加 LayoutBox 局部坐标得到（I11 允许只读几何）。</p>
     *
     * <p><b>基准一致，无宿主偏移问题</b>：{@code pointerX} 是画布逻辑坐标（不叠加 route 的 rootAbsX，
     * 见 {@link club.heiqi.uilib.ui.scene.input.SceneEvent} 坐标语义），{@code absoluteX(track)} 是
     * track 累加到场景树根的绝对 x（同样不含 rootAbsX）。二者同基准，相减时 rootAbsX 本就不参与，
     * 故 localX 精确，无宿主整树平移偏差。</p>
     *
     * @param track    track 节点（读其绝对 x 与固定宽 W）
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
        double ratio = localX / (double) TRACK_WIDTH;
        if (ratio < 0.0D) {
            ratio = 0.0D;
        } else if (ratio > 1.0D) {
            ratio = 1.0D;
        }
        double raw = min + ratio * (max - min);
        return normalizeValue(raw, min, max, step);
    }

    /**
     * 累加节点及其所有祖先的 LayoutBox 局部 x，得到相对场景树根的绝对 x（只读几何，I11）。
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
     * 量化 + clamp：先 clamp 到 [min,max]，step&gt;0 时按 {@code min + round((v-min)/step)*step} 量化再 clamp。
     *
     * <p>对齐旧栈 {@code DocumentSliderControl.normalizeValue} 语义。</p>
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

    /**
     * 解析 thumb 四态颜色（纯函数，无副作用）。
     *
     * <p>优先级：disabled &gt; pressed（含拖拽）&gt; hover &gt; enabled 默认。</p>
     *
     * @param enabled 是否启用
     * @param pressed 是否按压/拖拽中
     * @param hovered 是否悬停中
     * @return 当前态对应的 ARGB 颜色
     */
    private static int resolveThumbColor(Boolean enabled, Boolean pressed, Boolean hovered) {
        if (!Boolean.TRUE.equals(enabled)) {
            return THUMB_DISABLED;
        }
        if (Boolean.TRUE.equals(pressed)) {
            return THUMB_PRESSED;
        }
        if (Boolean.TRUE.equals(hovered)) {
            return THUMB_HOVER;
        }
        return THUMB_ENABLED;
    }
}
