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
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;


public final class SceneCheckbox {

    /** 勾号文本，节点常驻，靠颜色透明/强调底前景切换显隐。 */
    private static final String CHECK_MARK_TEXT = "✓";
    /** 透明色，用于未勾选时隐藏勾号。 */
    private static final int CHECK_MARK_TRANSPARENT = 0x00000000;

    /** box 固定边长（像素） */
    private static final int BOX_SIZE = 16;
    /** root 行内间距（box 与 label 之间，像素） */
    private static final int GAP = SceneChromeTokens.GAP_MD;


    private SceneCheckbox() {
    }


    @Desugar
    public record Props(
            ReadableSignal<Boolean> checked,
            ReadableSignal<String> label,
            ReadableSignal<Boolean> enabled,
            Consumer<Boolean> onChange
    ) {
    }


    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneToggleablePrimitive.Props primitiveProps = new SceneToggleablePrimitive.Props(
                    props.checked(), props.label(), props.enabled(), props.onChange());
            SceneToggleablePrimitive.Result result = SceneToggleablePrimitive.create(rt, primitiveProps);
            SceneInteractionState interaction = result.interaction();

            SceneNode root = result.root();
            root.setGap(GAP);

            SceneNode box = result.indicator();
            box.setPreferredWidth(BOX_SIZE);
            box.setPreferredHeight(BOX_SIZE);
            box.setFlexDirection(FlexDirection.ROW);
            box.setCrossAxisAlign(CrossAxisAlign.CENTER);
            box.setMainAxisAlign(MainAxisAlign.CENTER);

            SceneNode checkMark = new SceneNode();
            checkMark.setText(CHECK_MARK_TEXT);
            checkMark.setTextColor(CHECK_MARK_TRANSPARENT);
            checkMark.setHitTestable(false);
            box.appendChild(checkMark);

            // box 表面：选中/未选中共享 INDICATOR 角色配方（选中态只换强调色 tint，不靠透明度区分），
            // 圆角/边框宽/边框色/滤镜/浮雕高度全归表面绑定器；本控件不再静态设值、不再叠加实色动画。
            ReadableSignal<SceneSurfaceStyle> surface =
                    SceneThemes.selectableSurface(rt, SceneTheme.Role.INDICATOR, props.checked());
            SceneSurfaceBinder.bind(rt, box, surface, props.enabled(), interaction);

            // 勾选标记：选中取主题强调底前景，未选中保持透明（节点常驻，结构不变）。
            ReadableSignal<Integer> onAccentForeground = SceneThemes.onAccentForeground(rt);
            rt.bindComputed(() -> Boolean.TRUE.equals(props.checked().get())
                            ? onAccentForeground.get() : CHECK_MARK_TRANSPARENT,
                    checkMark::setTextColor);

            // label 前景：启用取主题正文色，禁用取主题禁用色（不再走 SceneStateColors 静态取色）。
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
