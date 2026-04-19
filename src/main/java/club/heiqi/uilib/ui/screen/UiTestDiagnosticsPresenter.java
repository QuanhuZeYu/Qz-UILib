package club.heiqi.uilib.ui.screen;

import java.util.Locale;

import club.heiqi.uilib.font.FontRuntimeStats;
import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;

/**
 * `UiLayoutDiagnosticsDocumentPageController` 的页面局部诊断文案 presenter。
 */
final class UiTestDiagnosticsPresenter {

    /**
     * 根据纯数据快照生成页面诊断文案。
     *
     * @param snapshot 页面快照
     * @return 视图文案状态
     */
    ViewState present(Snapshot snapshot) {
        ViewState viewState = new ViewState();
        viewState.viewportText = "窗口 " + snapshot.hostWidth + "x" + snapshot.hostHeight + "；页面壳 "
                + snapshot.pageWidth + "x" + snapshot.pageHeight + "；总览卡片 " + snapshot.overviewCardWidth + "x"
                + snapshot.overviewCardHeight + "；表单卡片 " + snapshot.formCardWidth + "x" + snapshot.formCardHeight
                + "；文本卡片 " + snapshot.wrapCardWidth + "x" + snapshot.wrapCardHeight
                + "。\n如果页面壳仍然明显偏窄，优先检查 `DocumentPageWidget` 对 `ScrollViewportWidget` 框体约束的封装；如果卡片宽度异常，优先检查 `DivWidget` 的盒模型计算和最小宽度传播。 ";

        viewState.scrollText = "滚动偏移 " + snapshot.pageScrollOffset + " / " + snapshot.pageMaxScrollOffset
                + "；可视内容区 " + snapshot.pageVisibleContentWidth + "x" + snapshot.pageVisibleContentHeight
                + "；内容区 " + snapshot.pageContentWidth + "x" + snapshot.pageContentHeight
                + "。如果内容高度已经明显超过可视区，但最大滚动仍为 0，说明页面滚动高度计算仍然有问题。 ";

        viewState.wrapSampleText = "诊断文本：当前布局需要同时处理中文说明、English identifier、路径 `assets/qz_uilib/ui/diagnostic` 以及较长的字段值。只要父宽度变化，文本就应该优先自然换行，而不是继续保持单行并把右侧内容裁掉。当前主题为 “"
                + textOrPlaceholder(snapshot.themeText) + "”，命名空间为 “" + textOrPlaceholder(snapshot.namespaceText) + "”。";
        viewState.wrapMetricsText = "文本卡片宽度 " + snapshot.wrapCardWidth + "；当前操作：" + snapshot.actionStateText
                + "；宽度档位：" + snapshot.widthPresetOption
                + "。如果中文说明不再把整段文本撑成一个极宽最小值，说明 `LabelWidget#getMinContentWidth()` 的修正已经生效。 ";
        viewState.divScrollText = "Div 自滚动偏移 " + snapshot.divScrollOffset + " / " + snapshot.divMaxScrollOffset
                + "；可视内容区 " + snapshot.divVisibleContentWidth + "x" + snapshot.divVisibleContentHeight
                + "；内容区 " + snapshot.divContentWidth + "x" + snapshot.divContentHeight
                + "。如果这里终于出现稳定的内部滚动，说明 Div 组件开始真正读取统一的宽高契约。 ";
        viewState.actionText = "最近状态：" + snapshot.actionStateText;
        viewState.mutationText = "探针状态：" + (snapshot.mutationEnabled ? "运行中" : "已停止")
                + "；模式：" + snapshot.mutationMode
                + "；频率：" + snapshot.mutationRate
                + "；实际 setText 次数：" + snapshot.mutationSetTextCount
                + "；样本文本长度：" + (snapshot.mutationSampleText == null ? 0 : snapshot.mutationSampleText.length())
                + "；样本标签尺寸：" + snapshot.mutationSampleWidth + "x" + snapshot.mutationSampleHeight
                + "。若 `§k渲染` 模式也慢，优先怀疑字体绘制；若只在 `长文重排` 模式慢，更像布局与换行重算。";

        if (snapshot.runtimeStats.getSampledFrameCount() <= 0
                || !snapshot.expectedScreenName.equals(snapshot.runtimeStats.getScreenName())) {
            viewState.performanceFrameText = "性能采样尚未稳定，进入页面后至少完成一帧渲染才会显示当前统计。";
            viewState.performanceWidgetText = "等待统计：组件渲染次数、命中测试访问次数和输入事件数会在本页持续刷新。";
            viewState.performanceHotspotText = "等待热点：最慢组件类型会在完成当前页采样后显示。";
            viewState.performancePhaseText = "等待阶段：布局分阶段耗时会在当前页完成一帧后显示。";
            viewState.performanceFontText = "等待字体：字符页上传、字宽缓存命中和四边形数量会在当前页持续刷新。";
            return viewState;
        }

        viewState.performanceFrameText = String.format(
                Locale.ROOT,
                "当前帧 %.2f ms；近 %d 帧均值 %.2f ms；窗口内最大 %.2f ms；平均 FPS %.1f。若刚重新进入页面，前几十帧仍属于历史窗口热身期，应优先看当前帧与当前热点，而不是立刻看均值。",
                Double.valueOf(snapshot.runtimeStats.getFrameTimeMs()),
                Integer.valueOf(snapshot.runtimeStats.getSampledFrameCount()),
                Double.valueOf(snapshot.runtimeStats.getAverageFrameTimeMs()),
                Double.valueOf(snapshot.runtimeStats.getMaxFrameTimeMs()),
                Double.valueOf(snapshot.runtimeStats.getAverageFps()));
        viewState.performanceWidgetText = String.format(
                Locale.ROOT,
                "渲染 %.2f ms；贴屏 %.2f ms；输入路由 %.2f ms；鼠标/键盘/文本事件 %d/%d/%d；命中测试访问 %d 次；组件渲染 %d 次；最大深度 %d；慢帧 %d/%d。",
                Double.valueOf(snapshot.runtimeStats.getRenderTimeMs()),
                Double.valueOf(snapshot.runtimeStats.getPresentTimeMs()),
                Double.valueOf(snapshot.runtimeStats.getInputRoutingTimeMs()),
                Integer.valueOf(snapshot.runtimeStats.getMouseEventCount()),
                Integer.valueOf(snapshot.runtimeStats.getKeyEventCount()),
                Integer.valueOf(snapshot.runtimeStats.getTextEventCount()),
                Long.valueOf(snapshot.runtimeStats.getHitTestVisitCount()),
                Integer.valueOf(snapshot.runtimeStats.getWidgetRenderCount()),
                Integer.valueOf(snapshot.runtimeStats.getMaxWidgetDepth()),
                Integer.valueOf(snapshot.runtimeStats.getSlowFrameCount()),
                Integer.valueOf(snapshot.runtimeStats.getSampledFrameCount()));
        viewState.performanceHotspotText = String.format(
                Locale.ROOT,
                "最慢自身组件：%s %.2f ms；最慢总计组件：%s %.2f ms；当前视口 %dx%d GUI / %dx%d 原生。若总计热点总是容器类，说明子树整体太重；若自身热点稳定落在单一控件，说明该控件内部逻辑需要单独优化。",
                displayWidgetClass(snapshot.runtimeStats.getSlowestWidgetSelfClassName()),
                Double.valueOf(snapshot.runtimeStats.getSlowestWidgetSelfTimeMs()),
                displayWidgetClass(snapshot.runtimeStats.getSlowestWidgetTotalClassName()),
                Double.valueOf(snapshot.runtimeStats.getSlowestWidgetTotalTimeMs()),
                Integer.valueOf(snapshot.runtimeStats.getGuiWidth()),
                Integer.valueOf(snapshot.runtimeStats.getGuiHeight()),
                Integer.valueOf(snapshot.runtimeStats.getNativeWidth()),
                Integer.valueOf(snapshot.runtimeStats.getNativeHeight()));

        String phaseSummary = snapshot.runtimeStats.getPhaseSummary();
        viewState.performancePhaseText = "阶段热点："
                + (phaseSummary == null || phaseSummary.isEmpty()
                        ? "当前帧暂无阶段采样。若后续出现慢帧，这里会显示 prepare/apply overflow 与 row/column/wrap 测量的累计耗时。"
                        : phaseSummary);
        viewState.performanceFontText = String.format(
                Locale.ROOT,
                "字体统计：待上传 %d；就绪字形 %d；普通/粗体页 %d/%d；最近 1 秒 draw-stage 上传 %d；本帧四边形 %d；字宽缓存命中/未命中 %d/%d。若未命中或待上传在慢帧时突然升高，再优先怀疑字体系统。",
                Integer.valueOf(snapshot.fontStats.getPendingUploadCount()),
                Integer.valueOf(snapshot.fontStats.getReadyGlyphCount()),
                Integer.valueOf(snapshot.fontStats.getNormalPageCount()),
                Integer.valueOf(snapshot.fontStats.getBoldPageCount()),
                Integer.valueOf(snapshot.fontStats.getQueuedDrawStageUploadCount()),
                Integer.valueOf(snapshot.fontStats.getFrameQuadCount()),
                Long.valueOf(snapshot.fontStats.getWidthCacheHitCount()),
                Long.valueOf(snapshot.fontStats.getWidthCacheMissCount()));
        return viewState;
    }

    /**
     * 页面诊断快照。
     */
    static final class Snapshot {

        final int hostWidth;
        final int hostHeight;
        final int pageWidth;
        final int pageHeight;
        final int overviewCardWidth;
        final int overviewCardHeight;
        final int formCardWidth;
        final int formCardHeight;
        final int wrapCardWidth;
        final int wrapCardHeight;
        final int pageScrollOffset;
        final int pageMaxScrollOffset;
        final int pageVisibleContentWidth;
        final int pageVisibleContentHeight;
        final int pageContentWidth;
        final int pageContentHeight;
        final String themeText;
        final String namespaceText;
        final String actionStateText;
        final String widthPresetOption;
        final int divScrollOffset;
        final int divMaxScrollOffset;
        final int divVisibleContentWidth;
        final int divVisibleContentHeight;
        final int divContentWidth;
        final int divContentHeight;
        final boolean mutationEnabled;
        final String mutationMode;
        final String mutationRate;
        final int mutationSetTextCount;
        final String mutationSampleText;
        final int mutationSampleWidth;
        final int mutationSampleHeight;
        final String expectedScreenName;
        final UiRuntimeStats runtimeStats;
        final FontRuntimeStats fontStats;

        Snapshot(
                int hostWidth,
                int hostHeight,
                int pageWidth,
                int pageHeight,
                int overviewCardWidth,
                int overviewCardHeight,
                int formCardWidth,
                int formCardHeight,
                int wrapCardWidth,
                int wrapCardHeight,
                int pageScrollOffset,
                int pageMaxScrollOffset,
                int pageVisibleContentWidth,
                int pageVisibleContentHeight,
                int pageContentWidth,
                int pageContentHeight,
                String themeText,
                String namespaceText,
                String actionStateText,
                String widthPresetOption,
                int divScrollOffset,
                int divMaxScrollOffset,
                int divVisibleContentWidth,
                int divVisibleContentHeight,
                int divContentWidth,
                int divContentHeight,
                boolean mutationEnabled,
                String mutationMode,
                String mutationRate,
                int mutationSetTextCount,
                String mutationSampleText,
                int mutationSampleWidth,
                int mutationSampleHeight,
                String expectedScreenName,
                UiRuntimeStats runtimeStats,
                FontRuntimeStats fontStats) {
            this.hostWidth = hostWidth;
            this.hostHeight = hostHeight;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
            this.overviewCardWidth = overviewCardWidth;
            this.overviewCardHeight = overviewCardHeight;
            this.formCardWidth = formCardWidth;
            this.formCardHeight = formCardHeight;
            this.wrapCardWidth = wrapCardWidth;
            this.wrapCardHeight = wrapCardHeight;
            this.pageScrollOffset = pageScrollOffset;
            this.pageMaxScrollOffset = pageMaxScrollOffset;
            this.pageVisibleContentWidth = pageVisibleContentWidth;
            this.pageVisibleContentHeight = pageVisibleContentHeight;
            this.pageContentWidth = pageContentWidth;
            this.pageContentHeight = pageContentHeight;
            this.themeText = themeText;
            this.namespaceText = namespaceText;
            this.actionStateText = actionStateText;
            this.widthPresetOption = widthPresetOption;
            this.divScrollOffset = divScrollOffset;
            this.divMaxScrollOffset = divMaxScrollOffset;
            this.divVisibleContentWidth = divVisibleContentWidth;
            this.divVisibleContentHeight = divVisibleContentHeight;
            this.divContentWidth = divContentWidth;
            this.divContentHeight = divContentHeight;
            this.mutationEnabled = mutationEnabled;
            this.mutationMode = mutationMode;
            this.mutationRate = mutationRate;
            this.mutationSetTextCount = mutationSetTextCount;
            this.mutationSampleText = mutationSampleText;
            this.mutationSampleWidth = mutationSampleWidth;
            this.mutationSampleHeight = mutationSampleHeight;
            this.expectedScreenName = expectedScreenName == null ? "" : expectedScreenName;
            this.runtimeStats = runtimeStats == null ? UiRuntimeStats.empty() : runtimeStats;
            this.fontStats = fontStats == null ? new FontRuntimeStats(0, 0, 0, 0, 0, 0, 0L, 0L, 0L, 0L) : fontStats;
        }
    }

    /**
     * 页面诊断标签文案集合。
     */
    static final class ViewState {

        String viewportText;
        String scrollText;
        String wrapSampleText;
        String wrapMetricsText;
        String divScrollText;
        String actionText;
        String mutationText;
        String performanceFrameText;
        String performanceWidgetText;
        String performanceHotspotText;
        String performancePhaseText;
        String performanceFontText;
    }

    /**
     * 将空值替换为占位文本。
     *
     * @param value 原始文本
     * @return 占位结果
     */
    private String textOrPlaceholder(String value) {
        return value == null || value.isEmpty() ? "<未填写>" : value;
    }

    /**
     * 将组件类名转为展示文案。
     *
     * @param className 类型名
     * @return 展示文本
     */
    private String displayWidgetClass(String className) {
        return className == null || className.isEmpty() ? "<暂无>" : className;
    }
}
