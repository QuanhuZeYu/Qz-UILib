package club.heiqi.uilib.ui.env;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.i18n.LanguageEpochService;
import club.heiqi.uilib.resource.ResourceReloadService;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
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
        Assert.assertFalse("缺席 = 不显示调试浮层（与 Config.uiDebug 字段初值同）",
                empty.diagnostics().debugOverlayEnabled());
        Assert.assertFalse("缺席订阅源恒 false", empty.diagnostics().debugOverlayChanges().get().booleanValue());
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

    /**
     * 缺席订阅源必须是<b>共享单例</b>：每次调用新建 signal 会在依赖图里留下永不释放的节点。
     */
    @Test
    public void absentSubscriptionIsSharedInstance() {
        Assert.assertSame("EMPTY 每次调用必须返回同一源",
                DiagnosticsEnvironment.ALWAYS_DISABLED, DiagnosticsEnvironment.EMPTY.debugOverlayChanges());
        Assert.assertSame("未覆盖诊断域的既定实现同样拿到共享源",
                DiagnosticsEnvironment.ALWAYS_DISABLED, UiEnvironment.empty().diagnostics().debugOverlayChanges());
        Assert.assertSame("未覆盖诊断域的实现同样拿到共享源",
                DiagnosticsEnvironment.ALWAYS_DISABLED,
                new UiEnvironment() {
                }.diagnostics().debugOverlayChanges());
    }

    /**
     * 订阅通道由<b>唯一写入口</b>投影：写完字段再 publish，订阅方即可见。
     *
     * <p>两步是本仓的既定口径（字段 = 值读权威、通道 = 订阅投影），故测试也照两步走；
     * 只写字段会让订阅方停留在旧值 —— 这正是「唯一写入口」必须存在的原因。
     * 生产路径上这两步合并在 {@code ConfigValueBridge.applyGeneral} 内。</p>
     */
    @Test
    public void debugOverlayChannelIsProjectedByPublish() {
        boolean savedUseDebug = Config.useDebug;
        boolean savedUiDebug = Config.uiDebug;
        try {
            Config.uiDebug = true;
            ProcessUiEnvironment.publishUiDebug(true);
            ReactiveScheduler.get().flush();
            Assert.assertTrue("值读 = 字段当前值", ProcessUiEnvironment.INSTANCE.diagnostics().debugOverlayEnabled());
            Assert.assertTrue("订阅方 = 投影后的通道值",
                    ProcessUiEnvironment.INSTANCE.diagnostics().debugOverlayChanges().get().booleanValue());

            Config.uiDebug = false;
            ProcessUiEnvironment.publishUiDebug(false);
            ReactiveScheduler.get().flush();
            Assert.assertFalse("关闭同样必须投影到通道",
                    ProcessUiEnvironment.INSTANCE.diagnostics().debugOverlayChanges().get().booleanValue());
        } finally {
            Config.useDebug = savedUseDebug;
            Config.uiDebug = savedUiDebug;
            ProcessUiEnvironment.publishUiDebug(savedUiDebug);
            ReactiveScheduler.get().flush();
        }
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
