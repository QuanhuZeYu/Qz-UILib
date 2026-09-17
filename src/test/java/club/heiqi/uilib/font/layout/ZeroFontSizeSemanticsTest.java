package club.heiqi.uilib.font.layout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMode;
import club.heiqi.uilib.ui.scene.text.TextLinePlan;

/**
 * 零字号语义守卫：字号域下界为 {@code 0}，且 0 必须在<b>两个维度</b>都成立 ——
 * 不上屏（绘制域）与不占空间（布局域）。
 *
 * <h3>为什么布局域这一条是必需的</h3>
 * <p>下界从 1 改成 0 只是「允许写 0」。若下游把 0 重新解释成最小值，现象是「设了 0 字号，文字还占位」。
 * 这正是独立审核在 {@code af52f4a4} 上抓到的缺陷：{@code TextLinePlan} 对行高做 {@code Math.max(1, …)}
 * 保底，而它位于<span>布局</span>路径（{@code SizingCalculator.leafTextHeight} 直接返回其 totalHeight）——
 * 字号 0 时行块总高被抬成 1 px，Markdown 页出图 bounds 反而从 751 涨到 893（块几何按 1 px 行高重排）。</p>
 *
 * <p>故本测试断的是<b>行为</b>（行块总高、度量宽度），不是源码里出现过哪些字符串：
 * 前者在回归时真的会红，也不会因正常重构误报。</p>
 */
public class ZeroFontSizeSemanticsTest {

    /** 域：0 合法；负数仍归一到下界；非零输入恒等（本轮等价判据）。 */
    @Test
    public void domainAcceptsZeroAndKeepsNonZeroInputsIdentical() {
        assertEquals("下界必须是 0（本轮放开的正是它）", 0, FontSizeLimits.MIN_FONT_SIZE_PX);
        assertEquals("0 必须是域内值", 0, FontSizeLimits.clampFontSize(0));
        assertEquals("0 必须通过调用点校验", 0, FontSizeLimits.requireValidFontSize(0));
        assertEquals("负数仍被归一（下界是 0，不是「无下界」）", 0, FontSizeLimits.clampFontSize(-5));
        try {
            FontSizeLimits.requireValidFontSize(-1);
            fail("调用点参数校验对负数仍应快速失败");
        } catch (IllegalArgumentException expected) {
            assertTrue("报错需带合法区间", expected.getMessage().contains("合法区间"));
        }
        for (int size = 1; size <= FontSizeLimits.MAX_FONT_SIZE_PX; size++) {
            assertEquals("非零字号必须逐位不变：" + size, size, FontSizeLimits.clampFontSize(size));
        }
        assertEquals("倍率下界必须与字号下界一起放开（否则倍率 0 折算出 0 仍被抬到 1）",
                0, SceneRuntime.FONT_SCALE_MIN_PERCENT);
        assertEquals("不缩放水位不变", 100, SceneRuntime.FONT_SCALE_NONE_PERCENT);
    }

    /**
     * 布局域：字号 0 的文本必须不占空间（行块总高 0、度量宽 0），非零字号逐位不变。
     *
     * <p>{@link FixedTextMeasurer} 的行高与字号无关（固定 16），正好把「行高保底」这段逻辑单独暴露出来：
     * 修复前 {@code TextLinePlan} 会把它抬成 1 px，修复后字号 0 直接走 0。</p>
     */
    @Test
    public void zeroFontSizeTextTakesNoLayoutSpace() {
        FixedTextMeasurer measurer = new FixedTextMeasurer();
        TextLinePlan zero = TextLinePlan.build(measurer, "abc", 0, 0, SceneTextMode.UILIB_RAW, 0, false,
                height -> height);
        assertEquals("字号 0 的文本不得占布局空间", 0, zero.getTotalHeight());
        assertEquals("字号 0 时行高必须是 0（不得保底 1 px）", 0, zero.getLineHeights()[0]);
        // 度量宽为 0 是「度量适配器」的短路（TextMeasureServiceSceneAdapter）——本测试用的
        // FixedTextMeasurer 是替身、按字符宽度计算而与字号无关，故不在此断言宽度（断言对象必须
        // 是真实实现，否则测的是替身行为）。

        TextLinePlan normal = TextLinePlan.build(measurer, "abc", 16, 0, SceneTextMode.UILIB_RAW, 0, false,
                height -> height);
        assertEquals("非零字号的行高保底仍生效（不得因本修复改变既有布局）", 16, normal.getTotalHeight());
    }
}
