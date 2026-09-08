package club.heiqi.uilib.ui.scene.control;

import java.util.Objects;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiBackdropEffect;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 液态玻璃专属样式：统一按钮的透明染色、缘光、圆角与透镜反馈。
 *
 * <p>样式绑定器，不创建交互控件：调用方复用 SceneButtonPrimitive 提供行为、布局与内容，
 * 本类仅消费其交互信号。图标、文字或其他内容使用同一套状态样式，不依赖聊天、HUD 或图片来源。
 * 颜色、圆角与滤镜只影响 paint，内容透明度只影响 composite；不改变命中盒或触发布局。</p>
 */
public final class SceneLiquidGlassStyle {

    private static final int TRANSITION_MS = 160;
    private static final int BORDER_WIDTH = 1;
    private static final int NEUTRAL_TINT = 0x00EAF7FF;
    private static final int PRIMARY_TINT = 0x0079BEFF;
    private static final int DANGER_TINT = 0x00FF8797;
    private static final int FOCUS_EDGE = 0xD0C7EEFF;
    private static final float DISABLED_CONTENT_OPACITY = 0.35F;

    private enum State {
        IDLE, HOVERED, PRESSED, DISABLED
    }

    private SceneLiquidGlassStyle() {}

    /**
     * 将玻璃外观绑定到已有按钮；同一节点只绑定一次，生命周期跟随当前 Owner / runtime。
     *
     * @param rt 运行时（启用 Motion 时平滑过渡，否则立即应用）
     * @param button 交互根，已由按钮 primitive 注册行为
     * @param content 图标或文字内容根；样式管理其可用性透明度
     * @param enabled 可用性信号，禁用态压过 hover、pressed 和 focus
     * @param variant STANDARD / PRIMARY / DANGER 染色，null 视作 STANDARD
     * @param backdrop 宿主提供的玻璃配方；null 表示关闭滤镜，保留轮廓和状态反馈
     */
    public static void bindButton(SceneRuntime rt, SceneNode button, SceneNode content,
            ReadableSignal<Boolean> enabled, SceneButtonVariant variant, UiBackdrop backdrop) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(button, "button");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(enabled, "enabled");
        if (Owner.current() == null) {
            // Computed 也必须归属 runtime，不能只让末端动画绑定获得根 Owner。
            rt.__runRoot(() -> bindButton(rt, button, content, enabled, variant, backdrop));
            return;
        }
        SceneInteractionState interaction = rt.interactionState(button);
        ReadableSignal<State> state = Computed.create(() -> {
            if (!Boolean.TRUE.equals(enabled.get())) return State.DISABLED;
            if (Boolean.TRUE.equals(interaction.pressed().get())) return State.PRESSED;
            if (Boolean.TRUE.equals(interaction.hovered().get())) return State.HOVERED;
            return State.IDLE;
        });
        int tint = variant == SceneButtonVariant.PRIMARY ? PRIMARY_TINT
                : variant == SceneButtonVariant.DANGER ? DANGER_TINT : NEUTRAL_TINT;
        boolean accented = variant == SceneButtonVariant.PRIMARY || variant == SceneButtonVariant.DANGER;
        button.setBorderWidth(BORDER_WIDTH);
        rt.__bindAnimatedColor(() -> background(state.get(), tint, accented),
                button::setBackgroundColor, TRANSITION_MS);
        rt.__bindAnimatedColor(() -> edge(state.get(), Boolean.TRUE.equals(interaction.focused().get())),
                button::setBorderColor, TRANSITION_MS);
        rt.__bindAnimatedFloat(() -> radius(state.get()),
                value -> button.setCornerRadius(Math.round(value)), TRANSITION_MS);
        rt.__bindAnimatedFloat(() -> state.get() == State.DISABLED ? DISABLED_CONTENT_OPACITY : 1.0F,
                content::setOpacity, TRANSITION_MS);
        if (backdrop == null) {
            button.setBackdrop(null);
        } else {
            // 使用宿主同一材质/模糊/饱和度配方，仅改变按钮边缘透镜强度。
            // 配方随动画样本派生，不创建下载缓存、独立计时器或新的渲染通道。
            UiBackdropEffect effect = backdrop.getEffect();
            if (effect != null && effect.getFamily() == UiBackdropEffect.Family.LIQUID_GLASS) {
                rt.__bindAnimatedFloat(() -> lensFactor(state.get()), factor -> button.setBackdrop(UiBackdrop.of(
                        UiBackdropEffect.liquidGlass(effect.getMaterial(), effect.getLensStrength() * factor),
                        backdrop.getBlurRadius(), backdrop.getSaturation())), TRANSITION_MS);
            } else {
                button.setBackdrop(backdrop);
            }
        }
        SceneControlChrome.bindCursor(rt, button, enabled, SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);
    }

    private static int background(State state, int tint, boolean accented) {
        int alpha;
        switch (state) {
            case DISABLED: alpha = 0x06; break;
            case PRESSED: alpha = accented ? 0x58 : 0x30; break;
            case HOVERED: alpha = accented ? 0x40 : 0x20; break;
            default: alpha = accented ? 0x28 : 0x0C; break;
        }
        return (alpha << 24) | tint;
    }

    private static int edge(State state, boolean focused) {
        if (state == State.DISABLED) return 0x12FFFFFF;
        if (focused) return FOCUS_EDGE;
        if (state == State.PRESSED) return 0x50FFFFFF;
        if (state == State.HOVERED) return 0x90FFFFFF;
        return 0x24FFFFFF;
    }

    private static float radius(State state) {
        if (state == State.PRESSED) return 6.0F;
        if (state == State.HOVERED) return 12.0F;
        return 8.0F;
    }

    private static float lensFactor(State state) {
        if (state == State.DISABLED) return 0.0F;
        if (state == State.PRESSED) return 0.15F;
        if (state == State.HOVERED) return 1.0F;
        return 0.35F;
    }
}
