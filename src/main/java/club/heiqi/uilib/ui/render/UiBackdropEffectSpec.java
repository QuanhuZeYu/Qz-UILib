package club.heiqi.uilib.ui.render;

/**
 * backdrop effect 的声明式配置。
 *
 * <p>该对象只表达“这个容器希望拥有怎样的背景特效”，
 * 不直接承担 FBO、shader 或宿主调度职责。</p>
 */
public final class UiBackdropEffectSpec {

    private static final UiBackdropEffectSpec NONE = new UiBackdropEffectSpec(false, 0, 0, 0);

    public final boolean enabled;
    public final int strength;
    public final int tintColor;
    public final int cornerRadius;

    public UiBackdropEffectSpec(boolean enabled, int strength, int tintColor, int cornerRadius) {
        this.enabled = enabled;
        this.strength = Math.max(0, strength);
        this.tintColor = tintColor;
        this.cornerRadius = Math.max(0, cornerRadius);
    }

    /**
     * 返回关闭状态的 backdrop effect 配置。
     *
     * @return 空 effect 配置
     */
    public static UiBackdropEffectSpec none() {
        return NONE;
    }

    /**
     * 创建一个仅用于宿主占位回放的玻璃层配置。
     *
     * <p>当前骨架阶段暂不执行真实 blur shader，`strength` 仅作为后续 runtime 扩展保留。</p>
     *
     * @param tintColor 玻璃层叠加色
     * @param cornerRadius 玻璃层圆角提示
     * @return 启用状态的 effect 配置
     */
    public static UiBackdropEffectSpec glass(int tintColor, int cornerRadius) {
        return new UiBackdropEffectSpec(true, 0, tintColor, cornerRadius);
    }
}
