package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * `/qzuilib test` 运行时断言日志记录器。
 */
final class UiTestAssertionLogger {

    private static final Logger LOG = LogManager.getLogger("QzUiLib/UiTestAssertion");
    private static final int DEFAULT_TAIL_LIMIT = 12;

    private final List<UiTestAssertionLogEntry> entries = new ArrayList<UiTestAssertionLogEntry>();

    /**
     * 记录一条断言日志。
     *
     * @param entry 日志条目
     */
    void log(UiTestAssertionLogEntry entry) {
        UiTestAssertionLogEntry resolved = entry;
        synchronized (entries) {
            entries.add(resolved);
        }
        LOG.info("[{}][{}][{}] {} | expected={} | actual={} | diff={} | context={}", resolved.getGroupCode(),
                resolved.getCaseId(), resolved.getPhase(), resolved.getMessage(), resolved.getExpected(),
                resolved.getActual(), resolved.getDifference(), resolved.getContext());
    }

    /**
     * 返回指定样例最近日志尾部。
     *
     * @param caseId 样例编号
     * @param limit 最大条数
     * @return 日志尾部
     */
    List<UiTestAssertionLogEntry> getCaseTail(String caseId, int limit) {
        int resolvedLimit = Math.max(1, limit);
        List<UiTestAssertionLogEntry> matches = new ArrayList<UiTestAssertionLogEntry>();
        synchronized (entries) {
            for (UiTestAssertionLogEntry entry : entries) {
                if (entry.getCaseId().equals(caseId)) {
                    matches.add(entry);
                }
            }
        }
        if (matches.size() <= resolvedLimit) {
            return Collections.unmodifiableList(matches);
        }
        return Collections.unmodifiableList(new ArrayList<UiTestAssertionLogEntry>(
                matches.subList(matches.size() - resolvedLimit, matches.size())));
    }

    /**
     * 返回默认日志尾部大小。
     *
     * @return 默认日志尾部大小
     */
    int getDefaultTailLimit() {
        return DEFAULT_TAIL_LIMIT;
    }
}
