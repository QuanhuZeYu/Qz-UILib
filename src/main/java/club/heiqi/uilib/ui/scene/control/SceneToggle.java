package club.heiqi.uilib.ui.scene.control;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;

/**
 * SceneToggle —— scene 新栈控件层 Phase 4 批 1 首批真实迁移控件（开关/拨动控件）。
 *
 * <h3>定位：受控双向控件（契约 R7）</h3>
 * <p>受控双向契约同 {@link SceneCheckbox}：控件<b>零内部状态</b>，当前开关态由外部 {@code on}
 * 只读 signal 驱动；点击时<b>绝不自己翻转</b>，而是经 {@code onChange.accept(!on.get())}
 * 把「期望新值」交还外部，由外部 set 回 on signal（守 R1/R5/I11/R7）。</p>
 *
 * <h3>结构</h3>
 * <p>root（交互单元，hitTestable 默认 true，ROW + SHRINK 内容宽 + 交叉轴 CENTER + gap）
 * + track 子节点（48×24 固定圆角胶囊，装饰穿透，内含 thumb）
 * + label 子节点（文本，装饰穿透）。
 * root 默认 SHRINK，命中外轮廓收至 track+gap+label 内容宽，避免 FILL 透明根吞掉父行整宽。
 * thumb（18 圆点）作为 track 的子节点，layout 永远停在 START；on/off 位置仅用
 * composite 级 {@link Transform#translate(float, float)} 表达，不移动 hit root 或触发布局。</p>
 *
 * <h3>契约</h3>
 * <p>R1 纯静态工厂零实例字段 / R2 Props 只读 signal + 回调 / R3 组件函数只执行一次 /
 * R4 外观随状态经 bind 派生 / R5 交互态读 interactionState / R6 装饰子节点命中穿透 /
 * R7 受控双向零内部状态。</p>
 */
public final class SceneToggle {

    /** track 固定宽度（像素） */
    private static final int TRACK_WIDTH = 48;
    /** track 固定高度（像素） */
    private static final int TRACK_HEIGHT = 24;
    /** track 内边距（让 thumb 不贴边，像素） */
    private static final int TRACK_PADDING = 3;
    /** thumb 固定直径（像素） */
    private static final int THUMB_SIZE = 18;
    /** track/thumb 边框宽度（像素） */
    private static final int BORDER_WIDTH = 1;
    /** 圆角半径（像素，足够大使 track 呈胶囊、thumb 呈圆） */
    private static final int CAPSULE_RADIUS = SceneChromeTokens.RADIUS_PILL;
    /** root 行内间距（track 与 label 之间，像素） */
    private static final int GAP = SceneChromeTokens.GAP_MD;
    /** thumb 从 off 到 on 的 X 平移距离。 */
    private static final float THUMB_TRAVEL = TRACK_WIDTH - 2 * TRACK_PADDING - THUMB_SIZE;

    /** 纯静态工厂，禁止实例化（强制无状态，契约 R1） */
    private SceneToggle() {
    }

    /**
     * Toggle 输入契约 —— 受控双向：当前态由外部只读 signal 驱动，交互经 onChange 交还期望新值（契约 R2/R7）。
     *
     * @param on       开关态（响应式只读，受控源），控件绝不自己翻转或缓存此值
     * @param label    标签文本（响应式只读）
     * @param enabled  是否启用（响应式只读），false 时禁用点击/键盘并切灰态
     * @param onChange 切换回调，激活时以 {@code !on.get()}（期望新值）调用，由外部 set 回 on signal
     */
    @Desugar
    public record Props(
            ReadableSignal<Boolean> on,
            ReadableSignal<String> label,
            ReadableSignal<Boolean> enabled,
            Consumer<Boolean> onChange
    ) {
    }

    /**
     * 工厂：构建 Toggle 组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（R3）：建树 + 设静态属性 +
     * {@code rt.bind/bindText/on/focusable}，动态外观全落 {@code bind(computed(...))}，
     * thumb 位置随 on 态经 composite transform 平滑切换，
     * 交互只经 {@code on} 调 {@code onChange}（R4/R5/R7）。</p>
     *
     * @param rt    场景运行时
     * @param props Toggle 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneToggleablePrimitive.Props primitiveProps = new SceneToggleablePrimitive.Props(
                    props.on(), props.label(), props.enabled(), props.onChange());
            SceneToggleablePrimitive.Result result = SceneToggleablePrimitive.create(rt, primitiveProps);
            SceneInteractionState interaction = result.interaction();

            SceneNode root = result.root();
            root.setGap(GAP);

            SceneNode track = result.indicator();
            track.setFlexDirection(FlexDirection.ROW);
            track.setCrossAxisAlign(CrossAxisAlign.CENTER);
            track.setMainAxisAlign(MainAxisAlign.START);
            track.setPreferredWidth(TRACK_WIDTH);
            track.setPreferredHeight(TRACK_HEIGHT);
            track.setPadding(TRACK_PADDING);
            track.setBorderWidth(BORDER_WIDTH);
            track.setCornerRadius(CAPSULE_RADIUS);

            // thumb：layout 永远靠左；视觉位置只走 translateX，不移动交互根或布局盒。
            SceneNode thumb = new SceneNode();
            thumb.setPreferredWidth(THUMB_SIZE);
            thumb.setPreferredHeight(THUMB_SIZE);
            thumb.setCornerRadius(CAPSULE_RADIUS);
            thumb.setHitTestable(false);
            track.appendChild(thumb);

            // track color：on × 四态优先级 disabled > pressed > hover > default（PAINT 级）。
            rt.__bindAnimatedColor(() -> {
                boolean enabled = Boolean.TRUE.equals(props.enabled().get());
                boolean selected = Boolean.TRUE.equals(props.on().get());
                boolean hovered = Boolean.TRUE.equals(interaction.hovered().get());
                boolean pressed = Boolean.TRUE.equals(interaction.pressed().get());
                return selected
                        ? SceneStateColors.selectedBackground(enabled, hovered, pressed)
                        : SceneStateColors.standardBackground(enabled, hovered, pressed);
            }, track::setBackgroundColor, SceneChromeTokens.MOTION_STANDARD_MS);
            SceneControlChrome.bindStandardBorder(rt, track, props.enabled(), interaction);

            rt.__bindAnimatedFloat(
                    () -> Float.valueOf(Boolean.TRUE.equals(props.on().get()) ? THUMB_TRAVEL : 0.0f),
                    x -> thumb.setTransform(Transform.translate(x.floatValue(), 0.0f)),
                    SceneChromeTokens.MOTION_STANDARD_MS);

            rt.__bindAnimatedColor(() -> SceneStateColors.thumbBackground(
                            Boolean.TRUE.equals(props.enabled().get()),
                            Boolean.TRUE.equals(interaction.hovered().get()),
                            Boolean.TRUE.equals(interaction.pressed().get())),
                    thumb::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);

            rt.bindComputed(() -> SceneStateColors.standardText(
                            Boolean.TRUE.equals(props.enabled().get()), false),
                    result.labelNode()::setTextColor);

            // cursor 声明式附着：enabled 指针手型、disabled 禁止符号
            SceneControlChrome.bindCursor(rt, root, props.enabled(), SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);

            return root;
        };
    }
}
