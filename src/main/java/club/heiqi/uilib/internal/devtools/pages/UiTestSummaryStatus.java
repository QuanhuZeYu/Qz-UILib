package club.heiqi.uilib.internal.devtools.pages;

/**
 * `/qzuilib test` 视觉与语义双维度汇总状态。
 */
enum UiTestSummaryStatus {
    PASSED("通过"),
    PARTIAL_PASSED("部分通过"),
    FAILED("失败"),
    PENDING("待确认"),
    GAP("缺口");

    private final String displayText;

    /**
     * 创建汇总状态。
     *
     * @param displayText 页面展示文本
     */
    UiTestSummaryStatus(String displayText) {
        this.displayText = displayText;
    }

    /**
     * 返回页面展示文本。
     *
     * @return 页面展示文本
     */
    String getDisplayText() {
        return displayText;
    }
}
