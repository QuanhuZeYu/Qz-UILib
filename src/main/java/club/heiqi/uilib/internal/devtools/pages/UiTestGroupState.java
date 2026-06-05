package club.heiqi.uilib.internal.devtools.pages;

import java.util.Objects;

/**
 * `/qzuilib test` 分组级视觉与语义统计状态。
 */
final class UiTestGroupState {

    private final UiTestGroupSpec group;
    private final int implementedCaseCount;
    private final UiTestVisualStatus visualStatus;
    private final UiTestSemanticStatus semanticStatus;
    private final UiTestSummaryStatus summaryStatus;

    /**
     * 创建分组状态。
     *
     * @param group 分组规格
     * @param implementedCaseCount 已接入样例数
     * @param visualStatus 视觉状态
     * @param semanticStatus 语义状态
     * @param summaryStatus 汇总状态
     */
    UiTestGroupState(UiTestGroupSpec group, int implementedCaseCount, UiTestVisualStatus visualStatus,
            UiTestSemanticStatus semanticStatus, UiTestSummaryStatus summaryStatus) {
        this.group = Objects.requireNonNull(group, "group");
        if (implementedCaseCount < 0) {
            throw new IllegalArgumentException("implementedCaseCount must be non-negative");
        }
        this.implementedCaseCount = implementedCaseCount;
        this.visualStatus = Objects.requireNonNull(visualStatus, "visualStatus");
        this.semanticStatus = Objects.requireNonNull(semanticStatus, "semanticStatus");
        this.summaryStatus = Objects.requireNonNull(summaryStatus, "summaryStatus");
    }

    /**
     * 返回分组规格。
     *
     * @return 分组规格
     */
    UiTestGroupSpec getGroup() {
        return group;
    }

    /**
     * 返回已接入样例数。
     *
     * @return 已接入样例数
     */
    int getImplementedCaseCount() {
        return implementedCaseCount;
    }

    /**
     * 返回剩余缺口数。
     *
     * @return 剩余缺口数
     */
    int getGapCount() {
        return Math.max(0, group.getPlannedCaseCount() - implementedCaseCount);
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
}
