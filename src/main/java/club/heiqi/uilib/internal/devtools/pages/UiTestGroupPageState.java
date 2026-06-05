package club.heiqi.uilib.internal.devtools.pages;

/**
 * `/qzuilib test` 分组页内状态。
 */
final class UiTestGroupPageState {

    private final String groupCode;
    private int caseIndex;

    /**
     * 创建分组页状态。
     *
     * @param groupCode 分组代码
     */
    UiTestGroupPageState(String groupCode) {
        this.groupCode = groupCode;
    }

    String getGroupCode() {
        return groupCode;
    }

    int getCaseIndex() {
        return caseIndex;
    }

    void clampToCaseCount(int caseCount) {
        if (caseCount <= 0) {
            caseIndex = 0;
            return;
        }
        caseIndex = Math.max(0, Math.min(caseIndex, caseCount - 1));
    }

    void previous(int caseCount) {
        clampToCaseCount(caseCount);
        if (caseCount > 0) {
            caseIndex = Math.max(0, caseIndex - 1);
        }
    }

    void next(int caseCount) {
        clampToCaseCount(caseCount);
        if (caseCount > 0) {
            caseIndex = Math.min(caseCount - 1, caseIndex + 1);
        }
    }
}
