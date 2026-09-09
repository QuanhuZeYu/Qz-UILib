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
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

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
 * + track 子节点（48×24 指示器，装饰穿透，内含 thumb）
 * + label 子节点（文本，装饰穿透）。
 * root 默认 SHRINK，命中外轮廓收至 track+gap+label 内容宽，避免 FILL 透明根吞掉父行整宽。
 * thumb（18 圆点）作为 track 的子节点，layout 永远停在 START；on/off 位置仅用
 * composite 级 {@link Transform#translate(float, float)} 表达，不移动 hit root 或触发布局。</p>
 *
 * <h3>外观归属：表面绑定是唯一写入者</h3>
 * <p>track 的 background / borderColor / borderWidth / cornerRadius / backdrop /
 * surfaceElevation 全部由 {@link SceneSurfaceBinder} 从选中配方派生——配方来自
 * {@link SceneThemes#selectableSurface(SceneRuntime, SceneTheme.Role, ReadableSignal)}
 * 的 INDICATOR 角色：未选中取角色配方，选中态把 tint 的 RGB 换成主题强调色（保留原 alpha，
 * 故「选中」是色彩语义而非仅透明度），禁用态仍取角色禁用档。控件不再静态设 track 边框宽/圆角，
 * 也不再叠加 {@code SceneStateColors.*Background} 与 {@code SceneControlChrome.bindStandardBorder}。</p>
 *
 * <p>thumb 是控件自持的强调指示：底色取 {@link SceneThemes#accent(SceneRuntime)}
 * （禁用取 {@link SceneThemes#disabledForeground(SceneRuntime)}），其 {@code transform}
 * 仍归控件（on/off 位移由控件写 translateX，不交给绑定器）；label 前景取
 * {@link SceneThemes#foreground(SceneRuntime)} / 禁用取 disabledForeground。</p>
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
    /**
     * thumb 圆角半径（像素，足够大使 thumb 呈圆）。
     *
     * <p>track 圆角不在此设值：它随表面绑定器从主题 INDICATOR 配方读取（默认档 8px 圆角矩形，
     * 不再是旧的胶囊常量）——圆角属于配方，不属控件。</p>
     */
    private static final int THUMB_RADIUS = SceneChromeTokens.RADIUS_PILL;
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
     * {@code rt.bind/bindText/on/focusable}，动态外观全落主题配方派生，
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
            // 边框宽/圆角/四态染色/滤镜/实体高度全部由表面绑定器从选中配方派生：
            // 构造期不再静态设 borderWidth/cornerRadius，也不另绑状态色或标准边框。

            // thumb：layout 永远靠左；视觉位置只走 translateX，不移动交互根或布局盒。
            SceneNode thumb = new SceneNode();
            thumb.setPreferredWidth(THUMB_SIZE);
            thumb.setPreferredHeight(THUMB_SIZE);
            thumb.setCornerRadius(THUMB_RADIUS);
            thumb.setHitTestable(false);
            track.appendChild(thumb);

            // track 表面：INDICATOR 角色配方 + on 选中派生（选中把 tint RGB 换强调色，禁用取禁用档）。
            // 唯一外观写入者，独占 background/border/borderWidth/cornerRadius/backdrop/surfaceElevation。
            ReadableSignal<SceneSurfaceStyle> surface =
                    SceneThemes.selectableSurface(rt, SceneTheme.Role.INDICATOR, props.on());
            SceneSurfaceBinder.bind(rt, track, surface, props.enabled(), interaction);

            rt.__bindAnimatedFloat(
                    () -> Float.valueOf(Boolean.TRUE.equals(props.on().get()) ? THUMB_TRAVEL : 0.0f),
                    x -> thumb.setTransform(Transform.translate(x.floatValue(), 0.0f)),
                    SceneChromeTokens.MOTION_STANDARD_MS);

            // thumb 底色：启用取主题强调色，禁用取禁用前景色（对比选中/未选中的轨道染色，
            // 选中不只靠透明度区分）。transform 仍归控件，不交给绑定器。
            ReadableSignal<Integer> accent = SceneThemes.accent(rt);
            ReadableSignal<Integer> disabledThumb = SceneThemes.disabledForeground(rt);
            rt.__bindAnimatedColor(() -> Boolean.TRUE.equals(props.enabled().get())
                            ? accent.get() : disabledThumb.get(),
                    thumb::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);

            // label 前景：主题正文色，禁用取主题禁用前景色。
            ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
            ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);
            rt.bindComputed(() -> Boolean.TRUE.equals(props.enabled().get())
                            ? foreground.get() : disabledForeground.get(),
                    result.labelNode()::setTextColor);

            // cursor 声明式附着：enabled 指针手型、disabled 禁止符号
            SceneControlChrome.bindCursor(rt, root, props.enabled(), SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);

            return root;
        };
    }
}
