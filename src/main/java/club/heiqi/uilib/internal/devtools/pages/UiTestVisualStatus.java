package club.heiqi.uilib.internal.devtools.pages;

/**
 * `/qzuilib test` 视觉观察维度状态。
 */
enum UiTestVisualStatus {
    UNOBSERVED("未观察"),
    DISPLAYING("展示中"),
    MANUAL_PASSED("人工通过"),
    VISUAL_FAILED("视觉失败"),
    KNOWN_VISUAL_GAP("已知视觉缺口");

    private final String displayText;

    /**
     * 创建视觉状态。
     *
     * @param displayText 页面展示文本
     */
    UiTestVisualStatus(String displayText) {
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
