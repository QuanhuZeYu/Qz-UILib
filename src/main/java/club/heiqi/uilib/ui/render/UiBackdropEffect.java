package club.heiqi.uilib.ui.render;

/**
 * 一次 backdrop 效果的完整配方（材质家族 + 材质档 + 液态强度）。
 *
 * <p>磨玻璃有两个质感家族，签名效果不同：</p>
 * <ul>
 *   <li>{@link Family#CLASSIC}：iOS 18 及以前的 UIVisualEffectView 磨砂——
 *       vibrancy / tint / 亮边 / 内侧柔光 / 抖噪（{@link UiGlassMaterial} 8 档）；</li>
 *   <li>{@link Family#LIQUID_GLASS}：iOS 26 Liquid Glass——在经典合成链之上叠加
 *       边缘凸透镜折射、边缘厚度 tint 与随动缘光（高光峰值滑向光源方向；
 *       MC 无陀螺仪，宿主以指针为光源）。{@code lensStrength} 越大缘带越"鼓"。</li>
 * </ul>
 *
 * <p>不可变对象；null 家族/材质一律按经典档零强度解析，公开入口的旧签名
 * （仅 {@link UiGlassMaterial} 或纯 saturation）等价于 {@link #classic}，
 * 既有调用方观感不受本类引入影响。</p>
 */
public final class UiBackdropEffect {

    /** 质感家族。 */
    public enum Family {
        /** 经典 iOS 磨砂（UIVisualEffectView）。 */
        CLASSIC,
        /** iOS 26 Liquid Glass（边缘透镜 + 随动缘光）。 */
        LIQUID_GLASS
    }

    private static final UiBackdropEffect CLASSIC_NONE = new UiBackdropEffect(Family.CLASSIC, null, 0.0F);

    private final Family family;
    private final UiGlassMaterial material;
    /** 液态强度 [0,1]；经典家族恒为 0。 */
    private final float lensStrength;

    private UiBackdropEffect(Family family, UiGlassMaterial material, float lensStrength) {
        this.family = family;
        this.material = material;
        this.lensStrength = Math.max(0.0F, Math.min(1.0F, lensStrength));
    }

    /** 经典磨砂配方；material 可为 null（旧的线性饱和度语义）。 */
    public static UiBackdropEffect classic(UiGlassMaterial material) {
        if (material == null) {
            return CLASSIC_NONE;
        }
        return new UiBackdropEffect(Family.CLASSIC, material, 0.0F);
    }

    /** 液态玻璃配方。lensStrength 越界会被夹到 [0,1]。 */
    public static UiBackdropEffect liquidGlass(UiGlassMaterial material, float lensStrength) {
        return new UiBackdropEffect(Family.LIQUID_GLASS, material, lensStrength);
    }

    public Family getFamily() {
        return family;
    }

    /** 底层材质档；可为 null（旧线性饱和度语义，此时液态叠加仍生效于 saturation 支路之外）。 */
    public UiGlassMaterial getMaterial() {
        return material;
    }

    public float getLensStrength() {
        return lensStrength;
    }

    /** 是否启用液态叠加层（透镜折射 + 厚度 tint + 随动缘光）。 */
    public boolean isLiquid() {
        return family == Family.LIQUID_GLASS && lensStrength > 0.0F;
    }

    /** 供宿主判"是否还需自行补 tint 面"：有材质档或液态层时质感全在 shader 内。 */
    public boolean carriesMaterialTint() {
        return material != null;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof UiBackdropEffect)) {
            return false;
        }
        UiBackdropEffect other = (UiBackdropEffect) object;
        return family == other.family && material == other.material
                && Float.compare(lensStrength, other.lensStrength) == 0;
    }

    @Override
    public int hashCode() {
        int result = family.hashCode();
        result = 31 * result + (material == null ? 0 : material.hashCode());
        result = 31 * result + Float.hashCode(lensStrength);
        return result;
    }

    @Override
    public String toString() {
        return "UiBackdropEffect{" + family + (material == null ? "" : "/" + material.name())
                + (lensStrength > 0.0F ? " lens=" + lensStrength : "") + '}';
    }
}
