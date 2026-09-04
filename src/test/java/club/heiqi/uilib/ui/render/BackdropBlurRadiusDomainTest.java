package club.heiqi.uilib.ui.render;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * 模糊半径可表达域契约锁（R1 正向锚，2026-09-04）。
 *
 * <p>立项缘由：内聚扫描一度把 {@code UiBackdropFilterRenderer} 的半径换算读成「四层各钳一次、
 * 49..64 静默饱和」，并据此要抬高 {@code maxBlurRadius}。复核时才发现那是**跨行表达式抄断**
 * （漏掉 {@code /downsampleFactor}）造成的误判：聊天声明的 0..64 全区间可达、无饱和。
 * 既然结论靠算术成立，就得由算术测试钉住——否则下一个改换算的人会重犯我的错。</p>
 *
 * <p>口径（默认档）：cap = min(shaderBlurRadiusLimit=56, resolveMaxBlurRadius=48) = 48；
 * 降档 ds = (r &lt; 18 ? 1 : r &lt; 34 ? 2 : 4)；屏幕等效半径 = 0.75r，与 ds 无关。</p>
 */
public class BackdropBlurRadiusDomainTest {

    /** 聊天侧可表达的最大半径（{@code ChatMarkdownSettings} 写时钳的上界）。 */
    private static final int CHAT_MAX_BLUR_RADIUS_PX = 64;

    /** 聊天顶端的换算结果：0.75 * 64 / ds(64=4) = 12。 */
    private static final float CHAT_TOP_APPLIED_RADIUS = 12.0F;

    /** 默认档上限是 48，是聊天顶端换算值的 4 倍；这个余量就是「不饱和」的全部内容。 */
    private static final float EXPECTED_DEFAULT_CAP = 48.0F;

    /** 本包既有做法（{@code BackdropBlurPolicyTest} 同款）：自带 setup，不依赖别人留下的状态。 */
    @Before
    public void resetBackdropDefaults() {
        BackdropBlurConfig.getInstance().resetToDefaults();
    }

    @Test
    public void defaultCapSitsFarAboveTheChatExpressibleTop() {
        BackdropBlurPolicy policy = BackdropBlurPolicy.inheritGlobal();
        Assert.assertEquals("默认档上限仍是那条算术的前提", EXPECTED_DEFAULT_CAP, cap(policy), 0.001F);
        Assert.assertTrue("cap 必须留够余量（>=4x 聊天顶端换算值），否则 0..64 域会开始饱和",
                cap(policy) >= 4.0F * appliedAt(CHAT_MAX_BLUR_RADIUS_PX, policy));
    }

    @Test
    public void chatExpressibleDomainNeverHitsShaderCap() {
        BackdropBlurPolicy policy = BackdropBlurPolicy.inheritGlobal();
        for (int radius = 1; radius <= CHAT_MAX_BLUR_RADIUS_PX; radius++) {
            Assert.assertTrue("r=" + radius + " 必须严格低于 cap（等于即已开始被 min 削）",
                    appliedAt(radius, policy) < cap(policy));
        }
    }

    @Test
    public void screenEquivalentRadiusIsMonotonicAcrossDownsampleShifts() {
        BackdropBlurPolicy policy = BackdropBlurPolicy.inheritGlobal();
        float previous = -1.0F;
        for (int radius = 1; radius <= 128; radius++) {
            float screenEquivalent = appliedAt(radius, policy)
                    * (float) UiMainLayerSnapshotGeometry.resolveDownsampleFactor(radius);
            Assert.assertTrue("r=" + radius + " 屏幕等效半径不得因降档换挡而回退",
                    screenEquivalent >= previous - 0.001F);
            previous = screenEquivalent;
        }
    }

    @Test
    public void chatTopConvertsToTwelveTexels() {
        BackdropBlurPolicy policy = BackdropBlurPolicy.inheritGlobal();
        Assert.assertEquals("r=64 在默认档落在降档 4，换算 12 texel（屏幕等效 48px = 0.75*64）",
                CHAT_TOP_APPLIED_RADIUS, appliedAt(CHAT_MAX_BLUR_RADIUS_PX, policy), 0.001F);
    }

    /** 走生产换算本体，绝不在测试里重抄公式（重抄即是下一次误判的来源）。 */
    private static float appliedAt(int radius, BackdropBlurPolicy policy) {
        return UiBackdropFilterRenderer.resolveBackdropShaderRadius(radius,
                UiMainLayerSnapshotGeometry.resolveDownsampleFactor(radius), policy);
    }

    /** 渲染层实际生效的上限：两条链各自的上界取小（与生产代码同式）。 */
    private static float cap(BackdropBlurPolicy policy) {
        BackdropBlurConfig config = BackdropBlurConfig.getInstance();
        return Math.min(config.getShaderBlurRadiusLimit(), (float) policy.resolveMaxBlurRadius(config));
    }
}
