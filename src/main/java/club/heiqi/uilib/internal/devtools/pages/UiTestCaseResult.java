package club.heiqi.uilib.internal.devtools.pages;

import java.util.Objects;

/**
 * `/qzuilib test` 单个样例的视觉与语义双维度结果。
 */
final class UiTestCaseResult {

    private final UiTestVisualStatus visualStatus;
    private final UiTestSemanticStatus semanticStatus;
    private final UiTestSummaryStatus summaryStatus;
    private final String actualResult;
    private final String difference;

    /**
     * 创建双维度结果。
     *
     * @param visualStatus 视觉状态
     * @param semanticStatus 语义状态
     * @param summaryStatus 汇总状态
     * @param actualResult 实际结果
     * @param difference 差异说明
     */
    UiTestCaseResult(UiTestVisualStatus visualStatus, UiTestSemanticStatus semanticStatus,
            UiTestSummaryStatus summaryStatus, String actualResult, String difference) {
        this.visualStatus = Objects.requireNonNull(visualStatus, "visualStatus");
        this.semanticStatus = Objects.requireNonNull(semanticStatus, "semanticStatus");
        this.summaryStatus = Objects.requireNonNull(summaryStatus, "summaryStatus");
        this.actualResult = actualResult == null || actualResult.length() == 0 ? "尚未记录。" : actualResult;
        this.difference = difference == null ? "" : difference;
    }

    /**
     * 返回视觉状态。
     *
     * @return 视觉状态
     */
    UiTestVisualStatus getVisualStatus() {
        return visualStatus;
    }

    /**
     * 返回语义状态。
     *
     * @return 语义状态
     */
    UiTestSemanticStatus getSemanticStatus() {
        return semanticStatus;
    }

    /**
     * 返回汇总状态。
     *
     * @return 汇总状态
     */
    UiTestSummaryStatus getSummaryStatus() {
        return summaryStatus;
    }

    /**
     * 返回实际结果。
     *
     * @return 实际结果
     */
    String getActualResult() {
        return actualResult;
    }

    /**
     * 返回差异说明。
     *
     * @return 差异说明
     */
    String getDifference() {
        return difference;
    }
}
