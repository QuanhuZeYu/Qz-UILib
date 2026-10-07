package club.heiqi.uilib.ui.scene.control;

import java.util.List;
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


public final class SceneNavList {

    /** 各项之间的纵向间距（像素） */
    private static final int ITEM_GAP = SceneChromeTokens.GAP_SM;
    /** 项内边距（像素） */
    private static final int ITEM_PADDING = SceneChromeTokens.PAD_MD;
    /** 选中指示条宽度。 */
    private static final int INDICATOR_WIDTH = 3;
    /** 选中指示条高度。 */
    private static final int INDICATOR_HEIGHT = 18;
    /** 选中标签水平位移。 */
    private static final float SELECTED_LABEL_OFFSET_X = 4.0f;


    private SceneNavList() {
    }


    @Desugar
    public record Props(
        ReadableSignal<Integer> selectedIndex,
        List<String> options,
        ReadableSignal<Boolean> enabled,
        Consumer<Integer> onSelect,
        Integer preferredHeight
    ) {
    }


    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneSingleSelectPrimitive.Props primitiveProps = new SceneSingleSelectPrimitive.Props(
                props.selectedIndex(),
                props.options(),
                props.enabled(),
                props.onSelect(),
                SceneSingleSelectPrimitive.Orientation.VERTICAL);
            SceneSingleSelectPrimitive.Result result = SceneSingleSelectPrimitive.create(rt, primitiveProps);
            result.root().setGap(ITEM_GAP);
            // 可选根高：非 null 透传，null 由布局链决定（NavList 不内置自动推算，纵向 N 项高随项数变化）
            if (props.preferredHeight() != null) {
                result.root().setPreferredHeight(props.preferredHeight());
            }

            // 导航底座：TOOLBAR 角色配方。表面绑定器独占 background/border/borderWidth/cornerRadius/
            // backdrop/surfaceElevation；构造期不再静态设边框/圆角，也不另绑状态色或标准边框。
            SceneInteractionState baseInteraction = rt.interactionState(result.root());
            // 时序契约：Router 的 writeHovered/writePressed/writeFocused 对未创建的 signal 直接
            // 短路，故在构建期声明关心，保证后续 hover/pressed/focus 能驱动配方状态档。
            baseInteraction.hovered();
            baseInteraction.pressed();
            baseInteraction.focused();
            SceneSurfaceBinder.bind(rt, result.root(), SceneThemes.surface(rt, SceneTheme.Role.TOOLBAR),
                    props.enabled(), baseInteraction);

            // 主题语义前景派生在构造期捕获一次（来源作用域），循环内所有选项共享同一派生信号。
            ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
            ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);

            for (SceneSingleSelectPrimitive.ItemHandle handle : result.items()) {
                SceneNode item = handle.item();
                item.setFlexDirection(FlexDirection.ROW);
                // 纵向导航项：文本左对齐（START），交叉轴居中
                item.setMainAxisAlign(MainAxisAlign.START);
                item.setCrossAxisAlign(CrossAxisAlign.CENTER);
                item.setGap(SceneChromeTokens.GAP_MD);
                item.setPadding(ITEM_PADDING);
                // 命中宽度策略不变：导航项填满导航栏宽度（item 全宽可点，非 SHRINK）。
                item.setFillParentWidth(true);

                SceneNode indicator = new SceneNode();
                indicator.setPreferredWidth(INDICATOR_WIDTH);
                indicator.setPreferredHeight(INDICATOR_HEIGHT);
                indicator.setCornerRadius(INDICATOR_WIDTH);
                indicator.setHitTestable(false);
                item.appendChild(indicator);
                item.appendChild(handle.label());

                SceneInteractionState interaction = handle.interaction();

                // 选项表面：INDICATOR 角色配方 + selected 派生（选中把 tint RGB 换强调色、强度 0x59，
                // 禁用仍取角色禁用档）。唯一外观写入者，独占 background/border/borderWidth/
                // cornerRadius/backdrop/surfaceElevation；保留配方自带的轻滤镜（G09 裁决）。
                ReadableSignal<SceneSurfaceStyle> surface =
                    SceneThemes.selectableSurface(rt, SceneTheme.Role.INDICATOR, handle.selected());
                SceneSurfaceBinder.bind(rt, item, surface, props.enabled(), interaction);

                // 选中指示条与标签共用同一前景派生（启用正文色 / 禁用禁用前景色）。
                ReadableSignal<Integer> itemForeground = () -> Boolean.TRUE.equals(props.enabled().get())
                        ? foreground.get() : disabledForeground.get();
                rt.bind(itemForeground, indicator::setBackgroundColor);
                rt.bind(itemForeground, handle.label()::setTextColor);

                // selection indicator：旧/新 item 的 scaleY 各自用 standard Motion 交叉过渡。
                rt.__bindAnimatedFloat(() -> Boolean.TRUE.equals(handle.selected().get()) ? 1.0f : 0.0f,
                        scale -> indicator.setTransform(Transform.scale(1.0f, scale.floatValue())),
                        SceneChromeTokens.MOTION_STANDARD_MS);
                rt.__bindAnimatedFloat(
                        () -> Boolean.TRUE.equals(handle.selected().get()) ? SELECTED_LABEL_OFFSET_X : 0.0f,
                        offset -> handle.label().setTransform(Transform.translate(offset.floatValue(), 0.0f)),
                        SceneChromeTokens.MOTION_STANDARD_MS);
                SceneControlChrome.bindCursor(rt, item, props.enabled(), SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);
            }

            return result.root();
        };
    }
}
