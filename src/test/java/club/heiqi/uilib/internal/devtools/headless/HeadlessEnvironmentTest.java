package club.heiqi.uilib.internal.devtools.headless;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.env.DiagnosticsEnvironment;
import club.heiqi.uilib.ui.env.UiEnvironment;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 请求级环境事实的契约守卫：环境端口实现的三态，以及请求侧字段的缺省与校验。
 *
 * <p>只守两件会真出事的回归：<b>缺省值不再等价于「未声明」</b>（同一命令的旧行为静默漂移）与
 * <b>越界值被静默接受</b>（{@code --font-scale=50} 出出一张 100% 的图，而使用者以为它是 50%）。
 * 不测具体页面的观感 —— 那是出图矩阵的验收范围。</p>
 */
public class HeadlessEnvironmentTest {

    /** 关闭态必须与缺席实现逐位等价，且复用同一实例（新建匿名实例会在依赖图里留下无用节点）。 */
    @Test
    public void disabledIsBitwiseEquivalentToAbsent() {
        UiEnvironment off = HeadlessEnvironment.of(false);
        Assert.assertSame("关闭态必须复用缺席诊断域，而不是新建等价实例",
                DiagnosticsEnvironment.EMPTY, off.diagnostics());
        Assert.assertFalse(off.diagnostics().debugEnabled());
        Assert.assertFalse(off.diagnostics().debugOverlayEnabled());
        Assert.assertFalse(off.diagnostics().debugOverlayChanges().get().booleanValue());
        Assert.assertSame("语言域不覆盖：headless 进程没有语言服务，伪造语言码即伪造事实",
                club.heiqi.uilib.ui.env.LocaleEnvironment.EMPTY, off.locale());
        Assert.assertSame("资源域不覆盖：同上",
                club.heiqi.uilib.ui.env.ResourceEnvironment.EMPTY, off.resources());
    }

    /**
     * 开启态只打开「采集」，不打开「显示浮层」。
     *
     * <p>浮层在生产是 client HUD 注册表里的一份内容，headless 没有注册表 —— 声明 true 会让消费者读到
     * 不存在的事实。此断言把「headless 无浮层」钉成有意的语义，而不是遗漏。</p>
     */
    @Test
    public void enabledTurnsOnSamplingButNotTheOverlay() {
        UiEnvironment on = HeadlessEnvironment.of(true);
        Assert.assertTrue(on.diagnostics().debugEnabled());
        Assert.assertFalse("headless 无调试浮层可显示，不得声明为 true",
                on.diagnostics().debugOverlayEnabled());
        Assert.assertSame("未覆盖的订阅通道必须仍是共享缺席源",
                DiagnosticsEnvironment.ALWAYS_DISABLED, on.diagnostics().debugOverlayChanges());
    }

    /** 请求缺省：字号不缩放、不采样 —— 与「未声明」逐位等价。 */
    @Test
    public void requestDefaultsMatchUndeclaredEnvironment() {
        HeadlessRequest request = HeadlessRequest.builder().build();
        Assert.assertEquals("缺省字号倍率 = 不缩放",
                SceneRuntime.FONT_SCALE_NONE_PERCENT, request.fontScalePercent());
        Assert.assertFalse("缺省不采样", request.diagnostics());
        Assert.assertFalse(request.summary().contains("fontScale=null"));
    }

    /** 越界倍率必须快速失败：静默钳制会让使用者拿到与请求不符的图。 */
    @Test
    public void outOfRangeFontScaleFailsFast() {
        for (int bad : new int[] {SceneRuntime.FONT_SCALE_MIN_PERCENT - 1, SceneRuntime.FONT_SCALE_MAX_PERCENT + 1}) {
            try {
                HeadlessRequest.builder().fontScale(bad).build();
                Assert.fail("越界字号倍率必须被拒绝：" + bad);
            } catch (IllegalArgumentException expected) {
                Assert.assertTrue("错误信息需含合法区间，便于命令行使用者自助",
                        expected.getMessage().contains("fontScalePercent"));
            }
        }
        Assert.assertEquals("区间端点必须被接受", SceneRuntime.FONT_SCALE_MAX_PERCENT,
                HeadlessRequest.builder().fontScale(SceneRuntime.FONT_SCALE_MAX_PERCENT).build()
                        .fontScalePercent());
    }
}
