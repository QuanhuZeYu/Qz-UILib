package club.heiqi.uilib.ui.scene.control;

import java.util.Objects;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiBackdropEffect;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 液态玻璃样式绑定器。行为由 SceneButtonPrimitive 提供，外观只消费交互与配方信号。
 * 染色、轮廓、实体厚度与滤镜只影响 paint，内容微移与透明度只影响 composite。
 */
public final class SceneLiquidGlassStyle {
    private enum State { IDLE, HOVERED, PRESSED, DISABLED }

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
     * <p>button 归本绑定管理 background/border/cornerRadius/surfaceElevation/backdrop/cursor。
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
        ReadableSignal<SceneGlassButtonStyle> recipe = Computed.create(
                () -> Objects.requireNonNull(style.get(), "style value"));
        SceneInteractionState interaction = rt.interactionState(button);
        ReadableSignal<State> state = Computed.create(() -> {
            if (!Boolean.TRUE.equals(enabled.get())) return State.DISABLED;
            if (Boolean.TRUE.equals(interaction.pressed().get())) return State.PRESSED;
            if (Boolean.TRUE.equals(interaction.hovered().get())) return State.HOVERED;
            return State.IDLE;
        });
        ReadableSignal<SceneGlassButtonStyle.StateStyle> surface = Computed.create(
                () -> surface(recipe.get(), state.get()));
        Supplier<Integer> duration = () -> recipe.get().getTransitionMillis();
        rt.bindComputed(() -> recipe.get().getBorderWidth(), button::setBorderWidth);
        rt.bindComputed(() -> recipe.get().getCornerRadius(), button::setCornerRadius);
        rt.__bindAnimatedColor(() -> surface.get().getTint(), button::setBackgroundColor, duration);
        rt.__bindAnimatedColor(() -> state.get() != State.DISABLED
                        && Boolean.TRUE.equals(interaction.focused().get())
                        ? recipe.get().getFocusEdge() : surface.get().getEdge(), button::setBorderColor, duration);
        rt.__bindAnimatedFloat(() -> surface.get().getElevation(), button::__setSurfaceElevation, duration);
        rt.__bindAnimatedFloat(() -> -recipe.get().getContentLift() * surface.get().getElevation(),
                value -> motionRoot.setTransform(Transform.translate(0.0F, value)), duration);
        rt.__bindAnimatedFloat(() -> state.get() == State.DISABLED ? recipe.get().getDisabledOpacity() : 1.0F,
                motionRoot::setOpacity, duration);

        // 两个独立失效来源：材质变化即刻重派生，动画样本只改变当前配方的透镜强度。
        // 不向信号写每帧样本，不捕获旧材质，也不因配方切换重复创建订阅或动画轨道。
        // 无初值 Computed 在首次 flush 前尚未求值；构建期不可同步读取 recipe/surface。
        BackdropBinding backdrop = new BackdropBinding(button);
        rt.bindComputed(() -> recipe.get().getBackdrop(), backdrop::setBase);
        rt.__bindAnimatedFloat(() -> surface.get().getLensFactor(), backdrop::setFactor, duration);
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
        rt.__bindAnimatedColor(() -> {
            Integer foreground = Objects.requireNonNull(style.get(), "style value").getForeground();
            return foreground != null ? foreground : fallbackColor;
        }, textNode::setTextColor, () -> Objects.requireNonNull(style.get(), "style value").getTransitionMillis());
    }

    private static SceneGlassButtonStyle.StateStyle surface(SceneGlassButtonStyle style, State state) {
        switch (state) {
            case DISABLED: return style.getDisabled();
            case PRESSED: return style.getPressed();
            case HOVERED: return style.getHovered();
            default: return style.getIdle();
        }
    }

    private static final class BackdropBinding {
        private final SceneNode button;
        private UiBackdrop base;
        private float factor = 1.0F;

        private BackdropBinding(SceneNode button) {
            this.button = button;
        }
        private void setBase(UiBackdrop value) { base = value; apply(); }
        private void setFactor(float value) { factor = value; apply(); }
        private void apply() {
            UiBackdropEffect effect = base == null ? null : base.getEffect();
            if (effect != null && effect.getFamily() == UiBackdropEffect.Family.LIQUID_GLASS) {
                button.setBackdrop(UiBackdrop.of(
                        UiBackdropEffect.liquidGlass(effect.getMaterial(), effect.getLensStrength() * factor),
                        base.getBlurRadius(), base.getSaturation()));
            } else {
                button.setBackdrop(base);
            }
        }
    }
}
