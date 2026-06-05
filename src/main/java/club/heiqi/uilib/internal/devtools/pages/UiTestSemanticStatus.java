package club.heiqi.uilib.internal.devtools.pages;

/**
 * `/qzuilib test` 浏览器语义断言维度状态。
 */
enum UiTestSemanticStatus {
    NOT_ASSERTED("未断言"),
    AUTO_PASSED("自动通过"),
    AUTO_FAILED("自动失败"),
    MANUAL_PENDING("人工待确认"),
    KNOWN_SEMANTIC_GAP("已知语义缺口");

    private final String displayText;

    /**
     * 创建语义状态。
     *
     * @param displayText 页面展示文本
     */
    UiTestSemanticStatus(String displayText) {
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
