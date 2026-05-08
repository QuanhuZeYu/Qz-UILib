package club.heiqi.uilib.ui.screen;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontRuntimeStats;
import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;

/**
 * `UiTestDiagnosticsPresenter` 的纯 JVM 测试。
 */
public class UiTestDiagnosticsPresenterTest {

    /**
     * 验证无有效采样时进入等待统计分支，同时保留 wrap/action/mutation 文案。
     */
    @Test
    public void shouldShowWaitingBranchForInitialStats() {
        UiTestDiagnosticsPresenter presenter = new UiTestDiagnosticsPresenter();

        UiTestDiagnosticsPresenter.ViewState viewState = presenter.present(createSnapshot(
                UiDiagnosticsScreens.UI_TEST.getPageId(),
                UiRuntimeStats.empty(),
                new FontRuntimeStats(1, 2, 3, 4, 5, 6, 7L, 8L, 9L, 10L),
                "已开启自动换行提示",
                false,
                "§k渲染",
                "50ms",
                "探针未启用。开启后可以直接观察 `§k` 混淆文本、同长度替换和长文重排在当前容器中的表现。"));

        Assert.assertTrue(viewState.performanceFrameText.contains("性能采样尚未稳定"));
        Assert.assertTrue(viewState.performanceWidgetText.contains("等待统计"));
        Assert.assertTrue(viewState.performanceHotspotText.contains("等待热点"));
        Assert.assertTrue(viewState.performancePhaseText.contains("等待阶段"));
        Assert.assertTrue(viewState.performanceFontText.contains("等待字体"));
        Assert.assertTrue(viewState.wrapMetricsText.contains("当前操作：已开启自动换行提示"));
        Assert.assertTrue(viewState.actionText.contains("最近状态：已开启自动换行提示"));
        Assert.assertTrue(viewState.mutationText.contains("探针状态：已停止；模式：§k渲染；频率：50ms"));
        Assert.assertTrue(viewState.wrapSampleText.contains("当前主题为 “Qz Layout Probe”"));
    }

    /**
     * 验证 screenName 不匹配时仍保持等待统计分支。
     */
    @Test
    public void shouldShowWaitingBranchWhenScreenNameMismatches() {
        UiTestDiagnosticsPresenter presenter = new UiTestDiagnosticsPresenter();
        UiRuntimeStats mismatchedStats = createRuntimeStats("OtherScreen");

        UiTestDiagnosticsPresenter.ViewState viewState = presenter.present(createSnapshot(
                UiDiagnosticsScreens.UI_TEST.getPageId(),
                mismatchedStats,
                new FontRuntimeStats(3, 5, 1, 1, 2, 40, 0L, 0L, 12L, 1L),
                "已切换宽度档位到 中页",
                true,
                "同长替换",
                "每帧",
                "同长替换样本 000001"));

        Assert.assertTrue(viewState.performanceFrameText.contains("性能采样尚未稳定"));
        Assert.assertFalse(viewState.performanceFrameText.contains("当前帧 12.00 ms"));
        Assert.assertTrue(viewState.mutationText.contains("探针状态：运行中；模式：同长替换；频率：每帧"));
        Assert.assertTrue(viewState.wrapMetricsText.contains("宽度档位：中页"));
    }

    /**
     * 验证 screenName 命中时输出性能与字体统计详情。
     */
    @Test
    public void shouldRenderRuntimeAndFontStatsWhenScreenMatches() {
        UiTestDiagnosticsPresenter presenter = new UiTestDiagnosticsPresenter();
        String mutationSampleText = "长文重排样本 000001：当前路径片段为 assets/qz_uilib/ui/diagnostic/ABCDEF";

        UiTestDiagnosticsPresenter.ViewState viewState = presenter.present(createSnapshot(
                UiDiagnosticsScreens.UI_TEST.getPageId(),
                createRuntimeStats(UiDiagnosticsScreens.UI_TEST.getPageId()),
                new FontRuntimeStats(7, 80, 2, 1, 6, 128, 0L, 0L, 33L, 4L),
                "已切换变更模式到 长文重排",
                true,
                "长文重排",
                "200ms",
                mutationSampleText));

        Assert.assertTrue(viewState.viewportText.contains("窗口 1280x720；页面壳 960x680"));
        Assert.assertTrue(viewState.scrollText.contains("HTML-like 页面滚动偏移 12 / 80"));
        Assert.assertTrue(viewState.divScrollText.contains("HTML-like 自滚动偏移 20 / 120"));
        Assert.assertTrue(viewState.performanceFrameText.contains("当前帧 12.00 ms"));
        Assert.assertTrue(viewState.performanceWidgetText.contains("渲染 8.00 ms；贴屏 1.50 ms；输入路由 0.50 ms"));
        Assert.assertTrue(viewState.performanceHotspotText.contains("最慢自身组件：HtmlLikeDocumentWidget 3.25 ms"));
        Assert.assertTrue(viewState.performanceHotspotText.contains("DocumentPaintRenderer 6.50 ms"));
        Assert.assertTrue(viewState.performancePhaseText.contains("阶段热点：measure=4.0ms, layout=2.0ms"));
        Assert.assertTrue(viewState.performanceFontText.contains("字体统计：待上传 7；就绪字形 80；普通/粗体页 2/1"));
        Assert.assertTrue(viewState.performanceFontText.contains("字宽缓存命中/未命中 33/4"));
        Assert.assertTrue(viewState.mutationText.contains("实际 setText 次数：3"));
        Assert.assertTrue(viewState.mutationText.contains("样本文本长度：" + mutationSampleText.length()));
    }

    /**
     * 创建 presenter 测试用快照。
     */
    private static UiTestDiagnosticsPresenter.Snapshot createSnapshot(
            String expectedScreenName,
            UiRuntimeStats runtimeStats,
            FontRuntimeStats fontStats,
            String actionStateText,
            boolean mutationEnabled,
            String mutationMode,
            String mutationRate,
            String mutationSampleText) {
        return new UiTestDiagnosticsPresenter.Snapshot(
                1280,
                720,
                960,
                680,
                940,
                180,
                460,
                240,
                320,
                260,
                12,
                80,
                900,
                620,
                920,
                1080,
                "Qz Layout Probe",
                "qz_uilib",
                actionStateText,
                "中页",
                20,
                120,
                300,
                220,
                300,
                680,
                mutationEnabled,
                mutationMode,
                mutationRate,
                3,
                mutationSampleText,
                360,
                92,
                expectedScreenName,
                runtimeStats,
                fontStats);
    }

    /**
     * 创建命中分支测试用运行时统计。
     */
    private static UiRuntimeStats createRuntimeStats(String screenName) {
        return new UiRuntimeStats(
                screenName,
                960,
                540,
                1920,
                1080,
                12_000_000L,
                10_000_000L,
                18_000_000L,
                83.3D,
                8_000_000L,
                7_500_000L,
                1_500_000L,
                3,
                2,
                1,
                500_000L,
                42L,
                77,
                9,
                "HtmlLikeDocumentWidget",
                3_250_000L,
                "DocumentPaintRenderer",
                6_500_000L,
                "measure=4.0ms, layout=2.0ms",
                2,
                30);
    }
}
