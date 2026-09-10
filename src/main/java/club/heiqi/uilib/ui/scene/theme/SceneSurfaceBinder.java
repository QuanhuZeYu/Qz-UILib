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

/**
 * 通用表面绑定器：把 {@link SceneSurfaceStyle} 配方信号 + 交互态绑定到节点。
 *
 * <p>本类是「主题/显式配方 → 节点属性」的唯一通用写入者。同一节点只调用一次；重复调用会让
 * 两个绑定竞争同一属性槽，违反「一个属性只有一个外观写入者」。</p>
 *
 * <p><b>属性归属</b>：node 的 background / border / borderWidth / cornerRadius /
 * surfaceElevation / backdrop 归本绑定；motionRoot 非 null 时其 transform / opacity 也归本绑定，
 * 业务内容必须挂在其后代并保留自己的变换写入权。文本前景由
 * {@link #bindForeground} 显式绑定，本绑定不遍历后代、不给位图染色。</p>
 *
 * <p><b>状态优先级</b>：disabled &gt; pressed &gt; hovered &gt; idle；focus 只覆盖非禁用态的缘色。
 * 这些状态全部来自 {@link SceneInteractionState} 与调用方传入的 enabled 信号，绑定器不制造
 * 加载/错误等业务状态。</p>
 *
 * <p><b>失效语义</b>：材质变化即刻重派生（PAINT），动画样本只改变当前配方的透镜强度；
 * 不向信号写每帧样本、不捕获旧材质、不因配方切换重复创建订阅或动画轨道。颜色/模糊/折射
 * 变化不触发布局。</p>
 *
 * <p><b>无滤镜替代</b>：{@code backdrop == null} 时不写 backdrop，只写配方自身的 tint/edge/
 * elevation；配方作者负责让 tint 可读（见 {@link SceneTheme#withoutBackdrop()}）。</p>
 */
 *
 * <p><b>接管语义</b>：本绑定对目标节点的 backgroundColor / borderColor / borderWidth / cornerRadius /
 * backdrop / surfaceElevation 行使独占写入。调用方只允许在 bind 之前设置这些属性的初值；bind 之后
 * （尤其首次 flush 之后）再静态写同一属性即违反契约 §4「属性归属表」，表现为主题切换被覆盖、交互时
 * 属性值反复。元素级轻量槽（caret/thumb/dot/scrim 等）不受此约束——它们写的是控件独占子节点，
 * 不与配方争同一节点。</p>
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

        rt.bindComputed(() -> recipe.get().getBorderWidth(), node::setBorderWidth);
        rt.bindComputed(() -> recipe.get().getCornerRadius(), node::setCornerRadius);
        rt.__bindAnimatedColor(() -> surface.get().getTint(), node::setBackgroundColor, duration);
        rt.__bindAnimatedColor(() -> state.get() != State.DISABLED
                        && Boolean.TRUE.equals(interaction.focused().get())
                        ? recipe.get().getFocusEdge() : surface.get().getEdge(),
                node::setBorderColor, duration);
        rt.__bindAnimatedFloat(() -> surface.get().getElevation(), node::__setSurfaceElevation, duration);

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
                node.setBackdrop(UiBackdrop.of(
                        UiBackdropEffect.liquidGlass(effect.getMaterial(), effect.getLensStrength() * factor),
                        base.getBlurRadius(), base.getSaturation()));
            } else {
                node.setBackdrop(base);
            }
        }
    }
}
