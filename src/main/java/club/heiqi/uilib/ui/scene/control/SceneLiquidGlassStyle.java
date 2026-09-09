package club.heiqi.uilib.ui.scene.control;

import java.util.Objects;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;

/**
 * 液态玻璃样式绑定器（按钮配方入口）。
 *
 * <p>行为由 SceneButtonPrimitive 提供，外观只消费交互与配方信号。本类保留既有的
 * {@link SceneGlassButtonStyle} 公共入口与语义（含 {@code backdrop=null} 表示关闭滤镜），
 * 内部把按钮配方适配为通用 {@link SceneSurfaceStyle} 并委托 {@link SceneSurfaceBinder}——
 * 全库只有一套表面绑定实现，按钮不再自持第二份动画/滤镜轨道。</p>
 *
 * <p>染色、轮廓、实体厚度与滤镜只影响 paint，内容微移与透明度只影响 composite。</p>
 */
public final class SceneLiquidGlassStyle {

    private SceneLiquidGlassStyle() {}

    /**
     * 兼容入口：保留原默认外观、null variant/backdrop 语义及 content 属性归属。
     * 样式直接管理 content 的 transform/opacity；调用方同一节点只绑定一次。
     */
    public static void bindButton(SceneRuntime rt, SceneNode button, SceneNode content,
            ReadableSignal<Boolean> enabled, SceneButtonVariant variant, UiBackdrop backdrop) {
        Objects.requireNonNull(content, "content");
        SceneGlassButtonStyle style = SceneGlassButtonStyle.builder().variant(variant).backdrop(backdrop).build();
        bindButton(rt, button, content, enabled, () -> style);
    }

    /**
     * 将响应式玻璃配方绑定到已有按钮；同一节点只调用一次，替换 style 值即可切换外观。
     *
     * <p>button 归本绑定管理 background/border/cornerRadius/borderWidth/surfaceElevation/backdrop/cursor。
     * motionRoot 归本绑定管理 transform/opacity，业务内容应挂在其后代，保留自身属性写入权。
     * 不创建承载层、不遍历后代；可选文字前景使用 bindForeground 显式绑定。
     * 所有订阅和动画随当前 Owner 清理；无当前 Owner 时归 runtime 根 Owner。</p>
     *
     * @param style 不可为 null，且每个已发布配方值不可为 null；backdrop 字段可为 null
     */
    public static void bindButton(SceneRuntime rt, SceneNode button, SceneNode motionRoot,
            ReadableSignal<Boolean> enabled, ReadableSignal<SceneGlassButtonStyle> style) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(button, "button");
        Objects.requireNonNull(motionRoot, "motionRoot");
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(style, "style");
        if (Owner.current() == null) {
            rt.__runRoot(() -> bindButton(rt, button, motionRoot, enabled, style));
            return;
        }
        SceneInteractionState interaction = rt.interactionState(button);
        SceneSurfaceBinder.bind(rt, button, motionRoot, adapt(style), enabled, interaction);
        SceneControlChrome.bindCursor(rt, button, enabled, SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);
    }

    /**
     * 显式绑定一个文本节点的 textColor；foreground 为 null 时恢复 fallbackColor。
     * 不遍历后代，不改变 transform/opacity，不对位图染色。生命周期与 bindButton 相同。
     */
    public static void bindForeground(SceneRuntime rt, SceneNode textNode,
            ReadableSignal<SceneGlassButtonStyle> style, int fallbackColor) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(textNode, "textNode");
        Objects.requireNonNull(style, "style");
        if (Owner.current() == null) {
            rt.__runRoot(() -> bindForeground(rt, textNode, style, fallbackColor));
            return;
        }
        SceneSurfaceBinder.bindForeground(rt, textNode, adapt(style), fallbackColor);
    }

    /**
     * 按钮配方 → 通用表面配方的响应式适配。
     *
     * <p>无初值 Computed：配方值在首次 flush 前尚未求值，构造期不得解引用
     * （下游绑定器同样不在构造期读值）。</p>
     */
    private static ReadableSignal<SceneSurfaceStyle> adapt(ReadableSignal<SceneGlassButtonStyle> style) {
        return Computed.create(() -> adapt(Objects.requireNonNull(style.get(), "style value")));
    }

    private static SceneSurfaceStyle adapt(SceneGlassButtonStyle style) {
        return SceneSurfaceStyle.builder()
                .backdrop(style.getBackdrop())
                .cornerRadius(style.getCornerRadius())
                .borderWidth(style.getBorderWidth())
                .transitionMillis(style.getTransitionMillis())
                .focusEdge(style.getFocusEdge())
                .foreground(style.getForeground())
                .contentLift(style.getContentLift())
                .disabledOpacity(style.getDisabledOpacity())
                .idle(state(style.getIdle()))
                .hovered(state(style.getHovered()))
                .pressed(state(style.getPressed()))
                .disabled(state(style.getDisabled()))
                .build();
    }

    private static SceneSurfaceStyle.StateStyle state(SceneGlassButtonStyle.StateStyle state) {
        return new SceneSurfaceStyle.StateStyle(
                state.getTint(), state.getEdge(), state.getElevation(), state.getLensFactor());
    }
}
