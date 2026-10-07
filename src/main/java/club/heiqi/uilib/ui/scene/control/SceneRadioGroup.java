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
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;


public final class SceneRadioGroup {

    /**
     * dot 未选中/禁用时颜色（全透明，纯 PAINT 切换不重排）
     */
    private static final int DOT_TRANSPARENT = 0x00000000;

    /**
     * circle 固定边长（像素）
     */
    private static final int CIRCLE_SIZE = 16;
    /**
     * dot 固定边长（像素）
     */
    private static final int DOT_SIZE = 8;
    /**
     * dot 圆角（像素，足够大使 dot 呈圆）。
     *
     * <p>circle 圆角不在此设值：它随表面绑定器从主题 INDICATOR 配方读取（默认档 8px，16×16 下即圆）
     * ——圆角属于配方，不属控件。</p>
     */
    private static final int DOT_RADIUS = SceneChromeTokens.RADIUS_PILL;
    /**
     * 边框宽度（像素）
     */
    private static final int BORDER_WIDTH = 1;
    /**
     * option 行圆角（像素）
     */
    private static final int OPTION_RADIUS = SceneChromeTokens.RADIUS_LG;
    /**
     * option 行内边距（像素）
     */
    private static final int OPTION_PADDING = SceneChromeTokens.PAD_SM;
    /**
     * option 行内 circle 与 label 间距（像素）
     */
    private static final int OPTION_GAP = SceneChromeTokens.GAP_SM;
    /**
     * 各 option 行之间的纵向间距（像素）
     */
    private static final int ITEM_GAP = SceneChromeTokens.GAP_SM;


    private SceneRadioGroup() {
    }


    @Desugar
    public record Props(
        ReadableSignal<Integer> selectedIndex,
        List<String> options,
        ReadableSignal<Boolean> enabled,
        Consumer<Integer> onSelect
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
            result.root().setCrossAxisAlign(CrossAxisAlign.START);
            result.root().setGap(ITEM_GAP);

            // 主题语义色派生在构造期捕获一次（来源作用域），循环内所有选项共享同一派生信号。
            ReadableSignal<Integer> onAccentForeground = SceneThemes.onAccentForeground(rt);
            ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
            ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);
            ReadableSignal<Integer> borderDefault = SceneThemes.borderDefault(rt);

            for (SceneSingleSelectPrimitive.ItemHandle handle : result.items()) {
                SceneNode option = handle.item();
                option.setFlexDirection(FlexDirection.ROW);
                option.setCrossAxisAlign(CrossAxisAlign.CENTER);
                option.setGap(OPTION_GAP);
                option.setPadding(OPTION_PADDING);
                // option 行容器不参与表面采样（避免逐行重复滤镜），保留静态圆角/边框宽/padding；
                // 边框色改绑主题 borderDefault——不再静态取 SceneChromeTokens.BORDER_DEFAULT
                // （该值与深色档同值，浅色档下不会跟随）。
                option.setCornerRadius(OPTION_RADIUS);
                option.setBorderWidth(BORDER_WIDTH);
                rt.bind(borderDefault, option::setBorderColor);
                // 宽度收缩到内容：命中外轮廓收至 circle+gap+label+padding，避免 FILL 透明行吞父宽
                option.setWidthSizing(SceneNode.WidthSizing.SHRINK);

                SceneNode circle = SceneNode.row();
                circle.setCrossAxisAlign(CrossAxisAlign.CENTER);
                circle.setMainAxisAlign(MainAxisAlign.CENTER);
                circle.setPreferredWidth(CIRCLE_SIZE);
                circle.setPreferredHeight(CIRCLE_SIZE);
                // 边框宽/圆角/四态染色/滤镜/实体高度全部由表面绑定器从选中配方派生：
                // 构造期不再静态设 borderWidth/cornerRadius/borderColor，也不另绑状态色或标准边框。
                circle.setHitTestable(false);
                option.appendChild(circle);

                SceneNode dot = new SceneNode();
                dot.setPreferredWidth(DOT_SIZE);
                dot.setPreferredHeight(DOT_SIZE);
                dot.setCornerRadius(DOT_RADIUS);
                dot.setHitTestable(false);
                circle.appendChild(dot);

                option.appendChild(handle.label());

                SceneInteractionState interaction = handle.interaction();

                // circle 表面：INDICATOR 角色配方 + selected 派生（选中把 tint RGB 换强调色，禁用取禁用档）。
                // 唯一外观写入者，独占 background/border/borderWidth/cornerRadius/backdrop/surfaceElevation。
                ReadableSignal<SceneSurfaceStyle> surface =
                    SceneThemes.selectableSurface(rt, SceneTheme.Role.INDICATOR, handle.selected());
                SceneSurfaceBinder.bind(rt, circle, surface, props.enabled(), interaction);

                // dot：启用且选中取主题强调底前景色，其余（未选中/禁用）透明——禁用不显示选中标记，
                // 显隐切换仍是纯 PAINT 级。
                rt.bindComputed(() -> Boolean.TRUE.equals(props.enabled().get())
                        && Boolean.TRUE.equals(handle.selected().get())
                        ? onAccentForeground.get() : DOT_TRANSPARENT,
                    dot::setBackgroundColor);

                // label 前景：主题正文色，禁用取主题禁用前景色。
                rt.bindComputed(() -> Boolean.TRUE.equals(props.enabled().get())
                        ? foreground.get() : disabledForeground.get(),
                    handle.label()::setTextColor);

                SceneControlChrome.bindCursor(rt, option, props.enabled(), SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);
            }

            return result.root();
        };
    }
}
