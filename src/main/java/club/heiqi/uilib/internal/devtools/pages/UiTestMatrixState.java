package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * `/qzuilib test` 视觉矩阵结果状态仓库。
 */
final class UiTestMatrixState {

    private final UiTestMatrixRegistry registry;
    private final List<UiTestGroupState> groupStates;
    private final Map<String, UiTestGroupState> groupStateByCode;
    private final Map<String, UiTestCaseResult> caseResultById;

    /**
     * 根据 registry 与 checker 创建初始状态。
     *
     * @param registry 测试矩阵 registry
     * @param semanticChecker 语义 checker
     * @return 初始矩阵状态
     */
    static UiTestMatrixState create(UiTestMatrixRegistry registry, UiTestSemanticChecker semanticChecker) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(semanticChecker, "semanticChecker");
        Map<String, UiTestCaseResult> results = new LinkedHashMap<String, UiTestCaseResult>();
        for (UiTestCaseSpec testCase : registry.getCases()) {
            results.put(testCase.getId(), semanticChecker.createInitialResult(testCase));
        }
        List<UiTestGroupState> states = new ArrayList<UiTestGroupState>();
        for (UiTestGroupSpec group : registry.getGroups()) {
            states.add(createGroupState(group, registry.getCases(group.getCode()), results));
        }
        return new UiTestMatrixState(registry, states, results);
    }

    /**
     * 创建矩阵状态。
     *
     * @param groupStates 分组状态列表
     * @param caseResultById 样例结果映射
     */
    private UiTestMatrixState(UiTestMatrixRegistry registry, List<UiTestGroupState> groupStates,
            Map<String, UiTestCaseResult> caseResultById) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.groupStates = new ArrayList<UiTestGroupState>(groupStates);
        this.caseResultById = new LinkedHashMap<String, UiTestCaseResult>(caseResultById);
        this.groupStateByCode = new LinkedHashMap<String, UiTestGroupState>();
        for (UiTestGroupState state : this.groupStates) {
            this.groupStateByCode.put(state.getGroup().getCode(), state);
        }
    }

    /**
     * 返回所有分组状态。
     *
     * @return 分组状态列表
     */
    List<UiTestGroupState> getGroupStates() {
        return Collections.unmodifiableList(new ArrayList<UiTestGroupState>(groupStates));
    }

    /**
     * 查找指定分组状态。
     *
     * @param groupCode 分组代码
     * @return 分组状态
     */
    UiTestGroupState getGroupState(String groupCode) {
        UiTestGroupState state = groupStateByCode.get(groupCode);
        if (state == null) {
            throw new IllegalArgumentException("未知测试分组：" + groupCode);
        }
        return state;
    }

    /**
     * 返回所有样例结果。
     *
     * @return 样例结果映射
     */
    Map<String, UiTestCaseResult> getCaseResultById() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, UiTestCaseResult>(caseResultById));
    }

    /**
     * 返回指定样例结果。
     *
     * @param caseId 样例编号
     * @return 样例结果；不存在时返回 null
     */
    UiTestCaseResult getCaseResult(String caseId) {
        return caseResultById.get(caseId);
    }

    /**
     * 回写指定样例结果，并刷新所在分组统计。
     *
     * @param testCase 样例规格
     * @param result 样例结果
     */
    void updateCaseResult(UiTestCaseSpec testCase, UiTestCaseResult result) {
        Objects.requireNonNull(testCase, "testCase");
        Objects.requireNonNull(result, "result");
        caseResultById.put(testCase.getId(), result);
        refreshGroupState(testCase.getGroupCode());
    }

    /**
     * 返回首轮计划用例总数。
     *
     * @return 首轮计划用例总数
     */
    int getTotalPlannedCaseCount() {
        int count = 0;
        for (UiTestGroupState state : groupStates) {
            count += state.getGroup().getPlannedCaseCount();
        }
        return count;
    }

    /**
     * 返回已接入样例总数。
     *
     * @return 已接入样例总数
     */
    int getTotalImplementedCaseCount() {
        int count = 0;
        for (UiTestGroupState state : groupStates) {
            count += state.getImplementedCaseCount();
        }
        return count;
    }

    /**
     * 返回剩余缺口总数。
     *
     * @return 剩余缺口总数
     */
    int getTotalGapCount() {
        int count = 0;
        for (UiTestGroupState state : groupStates) {
            count += state.getGapCount();
        }
        return count;
    }

    /**
     * 返回计划自动语义总数。
     *
     * @return 计划自动语义总数
     */
    int getTotalPlannedAutomaticCount() {
        int count = 0;
        for (UiTestGroupState state : groupStates) {
            count += state.getGroup().getPlannedAutomaticCount();
        }
        return count;
    }

    /**
     * 返回计划人工确认总数。
     *
     * @return 计划人工确认总数
     */
    int getTotalPlannedManualCount() {
        int count = 0;
        for (UiTestGroupState state : groupStates) {
            count += state.getGroup().getPlannedManualCount();
        }
        return count;
    }

    /**
     * 刷新指定分组统计状态。
     *
     * @param groupCode 分组代码
     */
    private void refreshGroupState(String groupCode) {
        UiTestGroupSpec group = registry.getGroup(groupCode);
        UiTestGroupState nextState = createGroupState(group, registry.getCases(groupCode), caseResultById);
        groupStateByCode.put(groupCode, nextState);
        for (int index = 0; index < groupStates.size(); index++) {
            if (groupStates.get(index).getGroup().getCode().equals(groupCode)) {
                groupStates.set(index, nextState);
                return;
            }
        }
        groupStates.add(nextState);
    }

    /**
     * 创建分组初始状态。
     *
     * @param group 分组规格
     * @param cases 分组样例列表
     * @param results 样例结果映射
     * @return 分组初始状态
     */
    private static UiTestGroupState createGroupState(UiTestGroupSpec group, List<UiTestCaseSpec> cases,
            Map<String, UiTestCaseResult> results) {
        if (cases.isEmpty()) {
            return new UiTestGroupState(group, 0, UiTestVisualStatus.UNOBSERVED,
                    UiTestSemanticStatus.NOT_ASSERTED, UiTestSummaryStatus.GAP);
        }
        UiTestVisualStatus visualStatus = mergeVisualStatus(cases, results);
        UiTestSemanticStatus semanticStatus = mergeSemanticStatus(cases, results);
        return new UiTestGroupState(group, cases.size(), visualStatus, semanticStatus,
                mergeSummaryStatus(group, cases.size(), visualStatus, semanticStatus));
    }

    /**
     * 合并分组视觉状态。
     *
     * @param cases 分组样例列表
     * @param results 样例结果映射
     * @return 分组视觉状态
     */
    private static UiTestVisualStatus mergeVisualStatus(List<UiTestCaseSpec> cases,
            Map<String, UiTestCaseResult> results) {
        boolean hasFailure = false;
        boolean hasKnownGap = false;
        boolean hasUnobserved = false;
        for (UiTestCaseSpec testCase : cases) {
            UiTestCaseResult result = results.get(testCase.getId());
            UiTestVisualStatus status = result == null ? UiTestVisualStatus.UNOBSERVED : result.getVisualStatus();
            hasFailure |= status == UiTestVisualStatus.VISUAL_FAILED;
            hasKnownGap |= status == UiTestVisualStatus.KNOWN_VISUAL_GAP;
            hasUnobserved |= status == UiTestVisualStatus.UNOBSERVED || status == UiTestVisualStatus.DISPLAYING;
        }
        if (hasFailure) {
            return UiTestVisualStatus.VISUAL_FAILED;
        }
        if (hasKnownGap) {
            return UiTestVisualStatus.KNOWN_VISUAL_GAP;
        }
        return hasUnobserved ? UiTestVisualStatus.DISPLAYING : UiTestVisualStatus.MANUAL_PASSED;
    }

    /**
     * 合并分组语义状态。
     *
     * @param cases 分组样例列表
     * @param results 样例结果映射
     * @return 分组语义状态
     */
    private static UiTestSemanticStatus mergeSemanticStatus(List<UiTestCaseSpec> cases,
            Map<String, UiTestCaseResult> results) {
        boolean hasFailure = false;
        boolean hasKnownGap = false;
        boolean hasManualPending = false;
        boolean hasNotAsserted = false;
        for (UiTestCaseSpec testCase : cases) {
            UiTestCaseResult result = results.get(testCase.getId());
            UiTestSemanticStatus status = result == null ? UiTestSemanticStatus.NOT_ASSERTED
                    : result.getSemanticStatus();
            hasFailure |= status == UiTestSemanticStatus.AUTO_FAILED;
            hasKnownGap |= status == UiTestSemanticStatus.KNOWN_SEMANTIC_GAP;
            hasManualPending |= status == UiTestSemanticStatus.MANUAL_PENDING;
            hasNotAsserted |= status == UiTestSemanticStatus.NOT_ASSERTED;
        }
        if (hasFailure) {
            return UiTestSemanticStatus.AUTO_FAILED;
        }
        if (hasKnownGap) {
            return UiTestSemanticStatus.KNOWN_SEMANTIC_GAP;
        }
        if (hasManualPending) {
            return UiTestSemanticStatus.MANUAL_PENDING;
        }
        return hasNotAsserted ? UiTestSemanticStatus.NOT_ASSERTED : UiTestSemanticStatus.AUTO_PASSED;
    }

    /**
     * 合并分组汇总状态。
     *
     * @param group 分组规格
     * @param implementedCount 已接入样例数
     * @param visualStatus 视觉状态
     * @param semanticStatus 语义状态
     * @return 汇总状态
     */
    private static UiTestSummaryStatus mergeSummaryStatus(UiTestGroupSpec group, int implementedCount,
            UiTestVisualStatus visualStatus, UiTestSemanticStatus semanticStatus) {
        if (visualStatus == UiTestVisualStatus.VISUAL_FAILED
                || semanticStatus == UiTestSemanticStatus.AUTO_FAILED) {
            return UiTestSummaryStatus.FAILED;
        }
        if (visualStatus == UiTestVisualStatus.KNOWN_VISUAL_GAP
                || semanticStatus == UiTestSemanticStatus.KNOWN_SEMANTIC_GAP
                || implementedCount < group.getPlannedCaseCount()) {
            return UiTestSummaryStatus.GAP;
        }
        if (semanticStatus == UiTestSemanticStatus.MANUAL_PENDING
                || semanticStatus == UiTestSemanticStatus.NOT_ASSERTED
                || visualStatus == UiTestVisualStatus.UNOBSERVED
                || visualStatus == UiTestVisualStatus.DISPLAYING) {
            return UiTestSummaryStatus.PENDING;
        }
        return UiTestSummaryStatus.PASSED;
    }
}
