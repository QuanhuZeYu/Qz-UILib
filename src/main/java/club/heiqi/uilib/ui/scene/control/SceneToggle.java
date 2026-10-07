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


    private SceneToggle() {
    }


    @Desugar
    public record Props(
            ReadableSignal<Boolean> on,
            ReadableSignal<String> label,
            ReadableSignal<Boolean> enabled,
            Consumer<Boolean> onChange
    ) {
    }


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

            // thumb 底色 = 开关态指示（两态必须不同色，理由见类注释「为何 thumb 必须分两色」）：
            // on 取强调底前景（与 SceneCheckbox 勾选标记同一 token）、off 取主题 outline 档
            // （borderDefault，未激活手柄语义）、禁用取禁用前景（与 SceneRadioGroup「禁用不显示
            // 选中标记」同口径，禁用下的开关态仍由 track 位移读出）。transform 仍归控件，
            // 不交给绑定器。
            ReadableSignal<Integer> onThumb = SceneThemes.onAccentForeground(rt);
            ReadableSignal<Integer> offThumb = SceneThemes.borderDefault(rt);
            ReadableSignal<Integer> disabledThumb = SceneThemes.disabledForeground(rt);
            rt.__bindAnimatedColor(() -> resolveThumbColor(props.enabled().get(), props.on().get(),
                            onThumb.get(), offThumb.get(), disabledThumb.get()),
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

    /**
     * 解析 thumb 底色：启用态按开关态取两档主题 token（on 取强调底前景、off 取 outline 档），
     * 禁用态取主题禁用前景。
     *
     * <p>禁用优先级最高且不再区分 on/off——禁用态不开第二套配色，开关态由 track 位移
     * 与轨道禁用档读出（与 {@code SceneRadioGroup}「禁用不显示选中标记」同口径）。</p>
     *
     * @param enabled       是否启用
     * @param on            开关态
     * @param onColor       选中档 thumb 底色（主题强调底前景）
     * @param offColor      未选中档 thumb 底色（主题 outline 档）
     * @param disabledColor 禁用档 thumb 底色（主题禁用前景）
     * @return thumb 底色 ARGB
     */
    private static int resolveThumbColor(Boolean enabled, Boolean on,
                                        int onColor, int offColor, int disabledColor) {
        if (!Boolean.TRUE.equals(enabled)) {
            return disabledColor;
        }
        return Boolean.TRUE.equals(on) ? onColor : offColor;
    }
}
