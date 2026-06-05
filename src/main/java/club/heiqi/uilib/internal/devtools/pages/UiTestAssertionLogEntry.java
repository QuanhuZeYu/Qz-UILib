package club.heiqi.uilib.internal.devtools.pages;

import java.util.Objects;

/**
 * `/qzuilib test` 运行时断言日志条目。
 */
final class UiTestAssertionLogEntry {

    private final String caseId;
    private final String groupCode;
    private final String phase;
    private final String message;
    private final String expected;
    private final String actual;
    private final String difference;
    private final long timestampMillis;

    /**
     * 创建运行时断言日志条目。
     *
     * @param caseId 样例编号
     * @param groupCode 分组代码
     * @param phase 断言阶段
     * @param message 日志说明
     * @param expected 期望摘要
     * @param actual 实际摘要
     * @param difference 差异摘要
     * @param timestampMillis 时间戳
     */
    UiTestAssertionLogEntry(String caseId, String groupCode, String phase, String message, String expected,
            String actual, String difference, long timestampMillis) {
        this.caseId = requireText(caseId, "caseId");
        this.groupCode = requireText(groupCode, "groupCode");
        this.phase = requireText(phase, "phase");
        this.message = requireText(message, "message");
        this.expected = normalize(expected);
        this.actual = normalize(actual);
        this.difference = normalize(difference);
        this.timestampMillis = Math.max(0L, timestampMillis);
    }

    String getCaseId() {
        return caseId;
    }

    String getGroupCode() {
        return groupCode;
    }

    String getPhase() {
        return phase;
    }

    String getMessage() {
        return message;
    }

    String getExpected() {
        return expected;
    }

    String getActual() {
        return actual;
    }

    String getDifference() {
        return difference;
    }

    long getTimestampMillis() {
        return timestampMillis;
    }

    /**
     * 构建用于页面展示的单行摘要。
     *
     * @return 页面展示摘要
     */
    String toDisplayLine() {
        return phase + " | " + message + " | expected=" + expected + " | actual=" + actual
                + (difference.length() == 0 ? "" : " | diff=" + difference);
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name);
        if (text.length() == 0) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return text;
    }

    private static String normalize(String value) {
        return value == null || value.length() == 0 ? "-" : value;
    }
}
