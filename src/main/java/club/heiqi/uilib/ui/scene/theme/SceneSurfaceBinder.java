package club.heiqi.uilib.ui.scene.theme;

import java.util.Objects;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiBackdropEffect;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;


public final class SceneSurfaceBinder {

    private enum State { IDLE, HOVERED, PRESSED, DISABLED }

    private SceneSurfaceBinder() {
    }

    /**
     * 绑定表面（无内容微移根）。
     *
     * @param rt          场景运行时
     * @param node        承载表面的节点
     * @param style       配方信号（其值不得为 null；未求值的 Computed 在首次 flush 前为 null 也不会被构造期解引用）
     * @param enabled     是否启用
     * @param interaction 交互态容器
     */
    public static void bind(SceneRuntime rt, SceneNode node,
            ReadableSignal<SceneSurfaceStyle> style,
            ReadableSignal<Boolean> enabled,
            SceneInteractionState interaction) {
        bind(rt, node, null, style, enabled, interaction);
    }

    /**
     * 绑定表面，并把内容微移/禁用透明度交给 motionRoot。
     *
     * @param rt          场景运行时
     * @param node        承载表面的节点
     * @param motionRoot  内容微移根，可为 null（不写 transform/opacity）
     * @param style       配方信号
     * @param enabled     是否启用
     * @param interaction 交互态容器
     */
    public static void bind(SceneRuntime rt, SceneNode node, SceneNode motionRoot,
            ReadableSignal<SceneSurfaceStyle> style,
            ReadableSignal<Boolean> enabled,
            SceneInteractionState interaction) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(interaction, "interaction");
        if (Owner.current() == null) {
            rt.__runRoot(() -> bind(rt, node, motionRoot, style, enabled, interaction));
            return;
        }

        // 绑定释放（Owner/Binding dispose）时归还写入权：清接管标记，公开 setter 重新可写。
        // 没有绑定器在写就不该拒绝应用侧写——dispose 之后静态写是合法用法。
        rt.__onCleanup(() -> node.__releaseSurfaceOwnership());

        // 无初值：配方值在首次 flush 前尚未求值，构造期不得解引用（规划 3.1 既往缺陷）。
        ReadableSignal<SceneSurfaceStyle> recipe = Computed.create(
                () -> Objects.requireNonNull(style.get(), "style value"));
        ReadableSignal<State> state = Computed.create(() -> {
            if (!Boolean.TRUE.equals(enabled.get())) return State.DISABLED;
            if (Boolean.TRUE.equals(interaction.pressed().get())) return State.PRESSED;
            if (Boolean.TRUE.equals(interaction.hovered().get())) return State.HOVERED;
            return State.IDLE;
        });
        ReadableSignal<SceneSurfaceStyle.StateStyle> surface = Computed.create(
                () -> surface(recipe.get(), state.get()));
        Supplier<Integer> duration = () -> recipe.get().getTransitionMillis();

        // 保障），绑定器自身必须绕过守卫——否则首个 effect 就会拦住自己（G20 第 3 步）。
        rt.bindComputed(() -> recipe.get().getBorderWidth(), node::__writeBorderWidth);
        rt.bindComputed(() -> recipe.get().getCornerRadius(), node::__writeCornerRadius);
        rt.__bindAnimatedColor(() -> surface.get().getTint(), node::__writeBackgroundColor, duration);
        rt.__bindAnimatedColor(() -> state.get() != State.DISABLED
                        && Boolean.TRUE.equals(interaction.focused().get())
                        ? recipe.get().getFocusEdge() : surface.get().getEdge(),
                node::__writeBorderColor, duration);
        // 浮雕豁免位（P-05）：配方声明不走浮雕通道时恒写 -1（普通绘制路径），不消费 StateStyle.elevation。
        // 短路求值使 reliefDisabled=true 的配方不建立对 surface 的 elevation 依赖，状态变化不触发本 effect。
        rt.__bindAnimatedFloat(
                () -> recipe.get().isReliefDisabled() ? -1.0F : surface.get().getElevation(),
                node::__setSurfaceElevation, duration);

        if (motionRoot != null) {
            rt.__bindAnimatedFloat(() -> -recipe.get().getContentLift() * surface.get().getElevation(),
                    value -> motionRoot.setTransform(Transform.translate(0.0F, value)), duration);
            rt.__bindAnimatedFloat(() -> state.get() == State.DISABLED
                            ? recipe.get().getDisabledOpacity() : 1.0F,
                    motionRoot::setOpacity, duration);
        }

        // 两个独立失效来源：材质变化即刻重派生，动画样本只改变当前配方的透镜强度。
        // 不向信号写每帧样本，不捕获旧材质，也不因配方切换重复创建订阅或动画轨道。
        BackdropBinding backdrop = new BackdropBinding(node);
        rt.bindComputed(() -> recipe.get().getBackdrop(), backdrop::setBase);
        rt.__bindAnimatedFloat(() -> surface.get().getLensFactor(), backdrop::setFactor, duration);
    }

    /**
     * 绑定文本前景：{@code style.foreground()} 为 null 时回落 fallbackColor。
     *
     * <p>不遍历后代、不改变 transform/opacity、不对位图染色。生命周期与 {@link #bind} 相同。</p>
     *
     * @param rt            场景运行时
     * @param textNode      文本节点
     * @param style         配方信号
     * @param fallbackColor 配方不管理前景时的回落色
     */
    public static void bindForeground(SceneRuntime rt, SceneNode textNode,
            ReadableSignal<SceneSurfaceStyle> style, int fallbackColor) {
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

    private static SceneSurfaceStyle.StateStyle surface(SceneSurfaceStyle style, State state) {
        switch (state) {
            case DISABLED: return style.getDisabled();
            case PRESSED: return style.getPressed();
            case HOVERED: return style.getHovered();
            default: return style.getIdle();
        }
    }

    /** 滤镜声明 + 动画样本的合成：只改当前配方的透镜强度，不改材质或模糊半径。 */
    private static final class BackdropBinding {
        private final SceneNode node;
        private UiBackdrop base;
        private float factor = 1.0F;

        private BackdropBinding(SceneNode node) {
            this.node = node;
        }
        private void setBase(UiBackdrop value) {
            base = value;
            apply();
        }
        private void setFactor(float value) {
            factor = value;
            apply();
        }
        private void apply() {
            UiBackdropEffect effect = base == null ? null : base.getEffect();
            if (effect != null && effect.getFamily() == UiBackdropEffect.Family.LIQUID_GLASS) {
                node.__writeBackdrop(UiBackdrop.of(
                        UiBackdropEffect.liquidGlass(effect.getMaterial(), effect.getLensStrength() * factor),
                        base.getBlurRadius(), base.getSaturation()));
            } else {
                node.__writeBackdrop(base);
            }
        }
    }
}
