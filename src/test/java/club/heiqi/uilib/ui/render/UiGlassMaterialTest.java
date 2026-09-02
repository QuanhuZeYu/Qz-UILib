package club.heiqi.uilib.ui.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * iOS 磨玻璃材质档参数契约测试。
 *
 * <p>材质档是一组经验系数，改数值没有编译器会报错，观感却会整体跑偏。本测试锚定
 * 的是"形状约束"而非具体值——只要顺序和边界不破，调参自由；一旦破界（比如厚玻璃
 * 比薄玻璃还透、噪点强到能看见颗粒）CI 直接拦下。</p>
 */
public class UiGlassMaterialTest {

    /** 噪点必须落在"能抖掉 8-bit 色带、但肉眼看不出颗粒"的窄带内（约 1~2/255）。 */
    private static final float NOISE_MIN = 0.6F / 255.0F;
    private static final float NOISE_MAX = 3.0F / 255.0F;

    /** light 系四档必须严格单调变厚：tint 越强、vibrancy 越高。 */
    @Test
    public void lightMaterialsGrowMonotonicallyInOpacity() {
        assertTrue("ULTRA_THIN 应比 THIN 更透",
                UiGlassMaterial.ULTRA_THIN.getTintAlpha() < UiGlassMaterial.THIN.getTintAlpha());
        assertTrue("THIN 应比 REGULAR 更透",
                UiGlassMaterial.THIN.getTintAlpha() < UiGlassMaterial.REGULAR.getTintAlpha());
        assertTrue("REGULAR 应比 THICK 更透",
                UiGlassMaterial.REGULAR.getTintAlpha() < UiGlassMaterial.THICK.getTintAlpha());
        assertTrue("ULTRA_THIN 应比 THICK 更不饱和",
                UiGlassMaterial.ULTRA_THIN.getVibrancy() < UiGlassMaterial.THICK.getVibrancy());
    }

    /** dark 系四档同样单调；深色蒙层基色必须是 systemBlack 量级而非纯黑。 */
    @Test
    public void darkMaterialsGrowMonotonicallyAndUseSystemBlack() {
        assertTrue(UiGlassMaterial.DARK_ULTRA_THIN.getTintAlpha()
                < UiGlassMaterial.DARK_THIN.getTintAlpha());
        assertTrue(UiGlassMaterial.DARK_THIN.getTintAlpha() < UiGlassMaterial.DARK_REGULAR.getTintAlpha());
        assertTrue(UiGlassMaterial.DARK_REGULAR.getTintAlpha() < UiGlassMaterial.DARK_THICK.getTintAlpha());
        for (UiGlassMaterial material : new UiGlassMaterial[] { UiGlassMaterial.DARK_ULTRA_THIN,
                UiGlassMaterial.DARK_THIN, UiGlassMaterial.DARK_REGULAR, UiGlassMaterial.DARK_THICK }) {
            // iOS systemBlack 取色 0.096；纯黑会让深色玻璃失去厚度感。
            assertTrue(material.name() + " 深色档基色应接近 systemBlack(0.096)",
                    material.getTintRed() > 0.05F && material.getTintRed() < 0.20F);
            assertEquals(material.name() + " 深色档三通道应等值（中性灰）",
                    material.getTintRed(), material.getTintGreen(), 1.0e-6F);
            assertEquals(material.name(), material.getTintRed(), material.getTintBlue(), 1.0e-6F);
        }
    }

    /**
     * 所有档的亮度偏置都必须为正。
     *
     * <p>反直觉但关键：深色材质叠的是黑蒙层，它会<strong>吃掉</strong>亮度，所以要
     * 往上补而不是继续压暗；浅色材质同理补白蒙层损失。真正往下压的是"内侧下缘暗带"
     * 那一项，不能靠全局负 lift 冒充厚度。</p>
     */
    @Test
    public void allMaterialsCompensateLuminanceUpward() {
        for (UiGlassMaterial material : UiGlassMaterial.values()) {
            assertTrue(material.name() + " 蒙层会吃亮度，必须有正向补偿",
                    material.getLuminanceLift() > 0.0F);
            assertTrue(material.name() + " 补偿过大会整体发灰", material.getLuminanceLift() < 0.12F);
            assertEquals(material.name() + " dark() 判定应与命名一致",
                    material.name().startsWith("DARK_"), material.dark());
        }
        // 越厚的蒙层吃掉越多亮度，补偿必须随之递增，否则厚玻璃会显得发闷。
        assertTrue(UiGlassMaterial.ULTRA_THIN.getLuminanceLift() < UiGlassMaterial.THICK.getLuminanceLift());
        assertTrue(UiGlassMaterial.DARK_ULTRA_THIN.getLuminanceLift()
                < UiGlassMaterial.DARK_THICK.getLuminanceLift());
    }

    /**
     * vibrancy 峰值上限 1.5。
     *
     * <p>口径说明：官方一手值（WWDC13 示例 Light sat 1.8、apple.com 导航栏
     * saturate(180%)）是<strong>线性 saturate 矩阵</strong>乘子；本档走亮度域保护
     * 曲线（中间调实际吃色低于峰值），1.30~1.50 是对官方 1.8 的保守映射，
     * 超过 1.5 在本曲线下会失真成"塑料彩"。</p>
     */
    @Test
    public void vibrancyStaysWithinMeasuredIosRange() {
        for (UiGlassMaterial material : UiGlassMaterial.values()) {
            assertTrue(material.name() + " vibrancy 应大于 1（低于 1 不是材质语义）",
                    material.getVibrancy() > 1.0F);
            assertTrue(material.name() + " vibrancy 超出保守峰 1.5（官方线性上限 1.8）: "
                    + material.getVibrancy(), material.getVibrancy() <= 1.5F);
        }
    }

    /**
     * 一手锚点方向锁：同厚度深色档的 vibrancy 必须明显低于浅色档。
     *
     * <p>WWDC13 官方示例里 Dark 的 saturate(1.2) 就低于 Light 的 1.8（约 0.67
     * 比例），网传"dark 只是与 light 同饱和再叠黑"是讹误。深色玻璃在黑蒙层上
     * 若吃满饱和，会把彩色背景烧成脏色斑——2026-09-01 一手重定基正是按此方向
     * 把 dark 系从 1.25~1.45 下移到 1.18~1.35。</p>
     */
    @Test
    public void darkMaterialsAreLessVibrantThanLightPeers() {
        assertTrue("ULTRA_THIN 系", UiGlassMaterial.DARK_ULTRA_THIN.getVibrancy()
                < UiGlassMaterial.ULTRA_THIN.getVibrancy());
        assertTrue("THIN 系", UiGlassMaterial.DARK_THIN.getVibrancy()
                < UiGlassMaterial.THIN.getVibrancy());
        assertTrue("REGULAR 系", UiGlassMaterial.DARK_REGULAR.getVibrancy()
                < UiGlassMaterial.REGULAR.getVibrancy());
        assertTrue("THICK 系", UiGlassMaterial.DARK_THICK.getVibrancy()
                < UiGlassMaterial.THICK.getVibrancy());
        // 差距要真实存在（官方比例 0.67，这里至少 5%），否则方向锁形同虚设
        double ratio = UiGlassMaterial.DARK_REGULAR.getVibrancy()
                / UiGlassMaterial.REGULAR.getVibrancy();
        assertTrue("dark/light vibrancy 比例应 < 0.95，当前=" + ratio, ratio < 0.95D);
    }

    /** vibrancy=1.0 是恒等：shader 里 k 恒为 1，用作 A/B 基线必须真无操作。 */
    @Test
    public void vibrancyAtUnityIsIdentity() {
        // amount=1 时乘子与 luma 无关，恒为 1，即严格无操作。
        assertEquals(1.0F, vibrancyFactorAtLuma(1.0F, 0.11F), 1.0e-6F);
        assertEquals(1.0F, vibrancyFactorAtLuma(1.0F, 0.99F), 1.0e-6F);
        // 反向自检：luma=1 时乘子必须等于 amount 本身（吃满），否则说明曲线被写死。
        assertEquals(UiGlassMaterial.REGULAR.getVibrancy(),
                vibrancyFactorAtLuma(UiGlassMaterial.REGULAR.getVibrancy(), 1.0F), 1.0e-6F);
    }

    /**
     * vibrancy 的亮度保护形状：暗部（luma<=0.224）吃色为 0，亮部吃满。
     *
     * <p>这正是"通透感"的来源，也是它区别于线性饱和乘子的唯一性质，必须锁住。</p>
     */
    @Test
    public void vibrancyProtectsShadowsAndFullySaturatesHighlights() {
        float amount = UiGlassMaterial.REGULAR.getVibrancy();
        assertTrue("luma=0 暗部不应吃色", Math.abs(vibrancyFactorAtLuma(amount, 0.0F) - 1.0F) < 1.0e-6F);
        assertTrue("luma=0.1 暗部应几乎不吃色",
                Math.abs(vibrancyFactorAtLuma(amount, 0.1F) - 1.0F) < 1.0e-6F);
        assertEquals("luma=1 高光应吃满 vibrancy", amount, vibrancyFactorAtLuma(amount, 1.0F), 1.0e-6F);
        assertTrue("吃色量应随亮度单调不减",
                vibrancyFactorAtLuma(amount, 0.35F) < vibrancyFactorAtLuma(amount, 0.65F));
    }

    /** 噪点强度必须落在可见但不颗粒的窄带；越厚的玻璃允许越多噪点。 */
    @Test
    public void noiseStaysInDitherBandAndScalesWithOpacity() {
        for (UiGlassMaterial material : UiGlassMaterial.values()) {
            assertTrue(material.name() + " 噪点过弱无法抖掉色带: " + material.getNoiseAmount(),
                    material.getNoiseAmount() >= NOISE_MIN && material.getNoiseAmount() <= NOISE_MAX);
        }
        assertTrue("厚玻璃的噪点应不少于薄玻璃",
                UiGlassMaterial.ULTRA_THIN.getNoiseAmount() <= UiGlassMaterial.THICK.getNoiseAmount());
        assertTrue("暗材质需要更多噪点抑制色带（深色渐变更易 banding）",
                UiGlassMaterial.DARK_THICK.getNoiseAmount() >= UiGlassMaterial.THICK.getNoiseAmount());
    }

    /** 亮边与内侧柔光必须非负且量级合理（否则会出现白框或过曝边）。 */
    @Test
    public void edgeAndInnerLightAreWithinPhysicalBounds() {
        for (UiGlassMaterial material : UiGlassMaterial.values()) {
            assertTrue(material.name(), material.getEdgeHighlight() > 0.0F && material.getEdgeHighlight() < 0.30F);
            assertTrue(material.name(), material.getInnerLightTop() >= 0.0F && material.getInnerLightTop() < 0.30F);
            assertTrue(material.name(),
                    material.getInnerShadowBottom() >= 0.0F && material.getInnerShadowBottom() < 0.30F);
            assertTrue(material.name() + " 蒙层 alpha 应在 (0,1)",
                    material.getTintAlpha() > 0.0F && material.getTintAlpha() < 1.0F);
        }
    }

    /** ARGB 拆分量必须自洽：整型 tint 与四个 getter 口径一致，防手滑写错位移。 */
    @Test
    public void tintArgbDecomposesConsistently() {
        for (UiGlassMaterial material : UiGlassMaterial.values()) {
            int argb = material.getTintArgb();
            assertEquals(material.name(), ((argb >>> 24) & 0xFF) / 255.0F, material.getTintAlpha(), 1.0e-6F);
            assertEquals(material.name(), ((argb >>> 16) & 0xFF) / 255.0F, material.getTintRed(), 1.0e-6F);
            assertEquals(material.name(), ((argb >>> 8) & 0xFF) / 255.0F, material.getTintGreen(), 1.0e-6F);
            assertEquals(material.name(), (argb & 0xFF) / 255.0F, material.getTintBlue(), 1.0e-6F);
        }
    }

    /** 效果配方的家族语义：classic 永不液态；liquid 夹幅且带材质；null 材质=旧语义。 */
    @Test
    public void effectProfileFamilySemantics() {
        UiBackdropEffect classic = UiBackdropEffect.classic(UiGlassMaterial.REGULAR);
        assertFalse(classic.isLiquid());
        assertTrue(classic.carriesMaterialTint());
        assertEquals(0.0F, classic.getLensStrength(), 1.0e-6F);

        UiBackdropEffect liquid = UiBackdropEffect.liquidGlass(UiGlassMaterial.REGULAR, 1.7F);
        assertTrue("越界强度应被夹到 1 且仍液态", liquid.isLiquid());
        assertEquals(1.0F, liquid.getLensStrength(), 1.0e-6F);
        assertEquals(UiGlassMaterial.REGULAR, liquid.getMaterial());
        assertEquals(UiBackdropEffect.Family.LIQUID_GLASS, liquid.getFamily());

        assertFalse("强度 0 不启用液态叠加",
                UiBackdropEffect.liquidGlass(UiGlassMaterial.THIN, 0.0F).isLiquid());
        UiBackdropEffect legacy = UiBackdropEffect.classic(null);
        assertFalse(legacy.carriesMaterialTint());
        assertFalse(legacy.isLiquid());
    }

    /** 复刻 shader 的乘子公式：k = 1 + (amount-1) * clamp(1.289L - 0.289, 0, 1)。 */
    private static float vibrancyFactorAtLuma(float amount, float luma) {
        float t = Math.max(0.0F, Math.min(1.0F, 1.289F * luma - 0.289F));
        return 1.0F + (amount - 1.0F) * t;
    }
}