package club.heiqi.uilib.ui.render;

/**
 * 节点级背后滤镜声明（scene 侧声明式玻璃通道的值对象）。
 *
 * <p>把"这块区域要透过它看到什么样的背景"表达成节点绘制属性，与
 * {@code setBackgroundColor} 同构：PAINT 级、不改盒尺寸、不可变、可随 fragment 复用。
 * 之所以不直接复用 {@link UiBackdropEffect}，是因为模糊半径属于"这块玻璃"而非
 * "这个配方"——同一配方（如 REGULAR）可以用不同半径贴在气泡与输入条上。</p>
 *
 * <p>后端不支持 backdrop 能力时静默不绘（宪章信条六：能力经门面探测，不泄进契约）。
 * 节点圆角沿用节点自身 {@code cornerRadius}，不在本对象重复表达。</p>
 */
public final class UiBackdrop {

    private final int blurRadius;
    private final UiBackdropEffect effect;
    /**
     * vibrancy 乘子（材质档生效时）或线性饱和度乘子（旧语义时）。
     * 1.0 = 严格采用配方原值。
     */
    private final float saturation;

    private UiBackdrop(int blurRadius, UiBackdropEffect effect, float saturation) {
        this.blurRadius = Math.max(0, blurRadius);
        this.effect = effect;
        this.saturation = Math.max(0.0F, saturation);
    }

    /** 经典磨砂材质档 + 指定模糊半径。 */
    public static UiBackdrop of(UiGlassMaterial material, int blurRadius) {
        return new UiBackdrop(blurRadius, UiBackdropEffect.classic(material), 1.0F);
    }

    /** Liquid Glass：底材质 + 模糊半径 + 液态强度 [0,1]。 */
    public static UiBackdrop liquidGlass(UiGlassMaterial material, int blurRadius, float lensStrength) {
        return new UiBackdrop(blurRadius, UiBackdropEffect.liquidGlass(material, lensStrength), 1.0F);
    }

    /** 完整配方入口（含自定义 vibrancy 乘子）。 */
    public static UiBackdrop of(UiBackdropEffect effect, int blurRadius, float saturation) {
        return new UiBackdrop(blurRadius, effect, saturation);
    }

    public int getBlurRadius() {
        return blurRadius;
    }

    /** 效果配方，可为 null（旧线性饱和度语义）。 */
    public UiBackdropEffect getEffect() {
        return effect;
    }

    public float getSaturation() {
        return saturation;
    }

    /** 是否有任何可见产出（决定要不要发 BACKDROP 命令）。 */
    public boolean isActive() {
        if (effect != null && effect.isLiquid()) {
            return true;
        }
        if (effect != null && effect.getMaterial() != null) {
            return blurRadius > 0;
        }
        return blurRadius > 0 || Float.compare(saturation, 1.0F) != 0;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof UiBackdrop)) {
            return false;
        }
        UiBackdrop other = (UiBackdrop) object;
        return blurRadius == other.blurRadius
                && Float.compare(saturation, other.saturation) == 0
                && (effect == null ? other.effect == null : effect.equals(other.effect));
    }

    @Override
    public int hashCode() {
        int result = blurRadius;
        result = 31 * result + (effect == null ? 0 : effect.hashCode());
        result = 31 * result + Float.hashCode(saturation);
        return result;
    }

    @Override
    public String toString() {
        return "UiBackdrop{blur=" + blurRadius + ", saturation=" + saturation + ", effect=" + effect + '}';
    }
}
