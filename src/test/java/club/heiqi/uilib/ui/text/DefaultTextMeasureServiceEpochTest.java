package club.heiqi.uilib.ui.text;

import org.junit.Assert;
import org.junit.Test;

/**
 * 页面层文本测量纪元的复合规则（方案 D 的端口侧）。
 *
 * <p><b>锁什么</b>：{@code DefaultTextMeasureService#getEpoch()} 必须同时反映字体换代代与
 * 宽度收敛代，且两者互不串扰——换代不得被收敛位的进位掩盖，反之亦然。</p>
 *
 * <p>消费方（{@code SceneNode.lastMeasuredEpoch}、{@code ChatLineLayouter.cachedEpoch}、
 * {@code TextLayoutEngine.cachedEpoch}、{@code SceneTextGeometry} 前缀宽缓存）一律做 {@code !=}
 * 比较，故纪元只需「该变时变、不该变时不变」，不需要有序。</p>
 */
public class DefaultTextMeasureServiceEpochTest {

    @Test
    public void widthConvergenceAloneChangesEpoch() {
        int base = DefaultTextMeasureService.composeEpoch(1, 0);
        int converged = DefaultTextMeasureService.composeEpoch(1, 1);
        Assert.assertNotEquals("仅宽度收敛也必须让页面层看见新纪元", base, converged);
    }

    @Test
    public void generationChangeAloneChangesEpoch() {
        Assert.assertNotEquals(DefaultTextMeasureService.composeEpoch(1, 0),
                DefaultTextMeasureService.composeEpoch(2, 0));
    }

    @Test
    public void bothComponentsRemainIndividuallyVisible() {
        int epoch = DefaultTextMeasureService.composeEpoch(7, 5);
        Assert.assertEquals(7, epoch >>> 16);
        Assert.assertEquals(5, epoch & 0xFFFF);
    }

    @Test
    public void steadyEpochIsStableAndConvergenceIsIdempotent() {
        int first = DefaultTextMeasureService.composeEpoch(3, 0);
        Assert.assertEquals("稳态下重复取值必须恒定", first, DefaultTextMeasureService.composeEpoch(3, 0));
        // 收敛代只在债务归零时递增；同一代内重复比较不得再变。
        Assert.assertEquals(first, DefaultTextMeasureService.composeEpoch(3, 0));
    }

    /**
     * 已知边界（钉住事实，不是期望性质）：收敛位只占低 16 位，故同一换代内
     * {@code converge} 与 {@code converge + 65536} 必然别名。一次会话内的收敛次数等于
     * 「冷启动近似 → 真值回填」的完整轮次，量级为个位数，别名不可达。
     *
     * <p>换代位不参与折叠：不同换代代恒可区分。全部消费方做 {@code !=} 比较而非排序，
     * 故换代代大到让复合值翻负（≥ 32768）也不影响失效判定。</p>
     */
    @Test
    public void convergenceWrapAliasesWithinGenerationButNeverAcross() {
        Assert.assertEquals("低 16 位回绕会在同换代内别名（已知边界）",
                DefaultTextMeasureService.composeEpoch(3, 0),
                DefaultTextMeasureService.composeEpoch(3, 65536));
        Assert.assertNotEquals("换代位不得被收敛位进位吞掉",
                DefaultTextMeasureService.composeEpoch(3, 65535),
                DefaultTextMeasureService.composeEpoch(4, 65535));
        Assert.assertNotEquals(DefaultTextMeasureService.composeEpoch(1, 7),
                DefaultTextMeasureService.composeEpoch(2, 7));
    }
}
