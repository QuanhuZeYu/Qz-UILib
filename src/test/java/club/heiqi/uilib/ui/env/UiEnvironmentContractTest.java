package club.heiqi.uilib.ui.env;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.i18n.LanguageEpochService;
import club.heiqi.uilib.resource.ResourceReloadService;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 环境端口契约守卫（三条不变量各钉一条）。
 *
 * <p>本测试只守两件会真出事的回归：<b>缺席值被改动</b>（headless / 测试的默认行为静默漂移）
 * 与<b>适配器被写成快照</b>（今日 {@code Computed.create(() -> Config.uiDebug)} 一类事故的同型）。
 * 不测具体域实现的业务语义 —— 那是各域消费者的事。</p>
 */
public class UiEnvironmentContractTest {

    /** 不变量二：缺席态与「未安装态」逐位等价。 */
    @Test
    public void emptyMatchesUninstalledState() {
        UiEnvironment empty = UiEnvironment.empty();
        Assert.assertSame("empty() 必须是同一无状态单例", empty, UiEnvironment.empty());
        Assert.assertFalse("缺席 = 不采样（与 Config.useDebug 字段初值同）", empty.diagnostics().debugEnabled());
        Assert.assertNull("缺席 = 语言码无从判定", empty.locale().languageCode());
        Assert.assertEquals("缺席 = 无语言变更", 0L, empty.locale().nameEpoch());
        Assert.assertEquals("缺席 = 无资源变更", 0L, empty.resources().resourceEpoch());
    }

    /** 不变量（开闭）：未覆盖任何域的实现也必须能安全取到三个域。 */
    @Test
    public void bareImplementationFallsBackToDomainEmpties() {
        UiEnvironment bare = new UiEnvironment() {
        };
        Assert.assertSame(DiagnosticsEnvironment.EMPTY, bare.diagnostics());
        Assert.assertSame(LocaleEnvironment.EMPTY, bare.locale());
        Assert.assertSame(ResourceEnvironment.EMPTY, bare.resources());
    }

    /** 不变量三：环境值是帧内直读 —— 适配器缓存成快照即失败。 */
    @Test
    public void processAdapterReadsThroughInsteadOfSnapshotting() {
        boolean saved = Config.useDebug;
        try {
            Config.useDebug = false;
            Assert.assertFalse(ProcessUiEnvironment.INSTANCE.diagnostics().debugEnabled());
            Config.useDebug = true;
            Assert.assertTrue("适配器不得缓存首次读到的值（快照化即静默失效）",
                    ProcessUiEnvironment.INSTANCE.diagnostics().debugEnabled());
        } finally {
            Config.useDebug = saved;
        }
        Assert.assertEquals("语言代际必须转发进程单例当前值",
                LanguageEpochService.getInstance().nameEpoch(),
                ProcessUiEnvironment.INSTANCE.locale().nameEpoch());
        Assert.assertEquals("资源代际必须转发进程单例当前值",
                ResourceReloadService.getInstance().resourceEpoch(),
                ProcessUiEnvironment.INSTANCE.resources().resourceEpoch());
    }

    /** 不变量一（装配纪律）：漏注入环境必须快速失败，不得静默退化为缺席态。 */
    @Test
    public void nullEnvironmentFailsFast() {
        try {
            new SceneRuntime(null, null);
            Assert.fail("漏注入环境应当抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue("异常信息须指出缺席态入口：" + expected.getMessage(),
                    expected.getMessage().contains("UiEnvironment.empty()"));
        }
    }
}
