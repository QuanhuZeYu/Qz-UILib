package club.heiqi.uilib.internal.devtools.pages;

import java.util.Objects;

/**
 * `/qzuilib test` 视觉矩阵分组规格。
 */
final class UiTestGroupSpec {

    private final String code;
    private final String title;
    private final String coverage;
    private final String visualFocus;
    private final String semanticGoal;
    private final String expectedVisualObservation;
    private final int plannedCaseCount;
    private final int plannedAutomaticCount;
    private final int plannedManualCount;

    /**
     * 创建分组规格。
     *
     * @param code 分组代码
     * @param title 分组标题
     * @param coverage 覆盖范围
     * @param visualFocus 视觉展示重点
     * @param semanticGoal 浏览器语义目标
     * @param expectedVisualObservation 截图验收预期
     * @param plannedCaseCount 首轮计划用例数
     * @param plannedAutomaticCount 首轮计划自动语义数
     * @param plannedManualCount 首轮计划人工确认数
     */
    UiTestGroupSpec(String code, String title, String coverage, String visualFocus, String semanticGoal,
            String expectedVisualObservation, int plannedCaseCount, int plannedAutomaticCount, int plannedManualCount) {
        this.code = requireText(code, "code");
        this.title = requireText(title, "title");
        this.coverage = requireText(coverage, "coverage");
        this.visualFocus = requireText(visualFocus, "visualFocus");
        this.semanticGoal = requireText(semanticGoal, "semanticGoal");
        this.expectedVisualObservation = requireExpectedResult(expectedVisualObservation);
        if (plannedCaseCount < 0 || plannedAutomaticCount < 0 || plannedManualCount < 0) {
            throw new IllegalArgumentException("planned counts must be non-negative");
        }
        if (plannedAutomaticCount + plannedManualCount > plannedCaseCount) {
            throw new IllegalArgumentException("planned automatic/manual counts exceed total count");
        }
        this.plannedCaseCount = plannedCaseCount;
        this.plannedAutomaticCount = plannedAutomaticCount;
        this.plannedManualCount = plannedManualCount;
    }

    /**
     * 返回分组代码。
     *
     * @return 分组代码
     */
    String getCode() {
        return code;
    }

    /**
     * 返回分组标题。
     *
     * @return 分组标题
     */
    String getTitle() {
        return title;
    }

    /**
     * 返回覆盖范围。
     *
     * @return 覆盖范围
     */
    String getCoverage() {
        return coverage;
    }

    /**
     * 返回视觉展示重点。
     *
     * @return 视觉展示重点
     */
    String getVisualFocus() {
        return visualFocus;
    }

    /**
     * 返回浏览器语义目标。
     *
     * @return 浏览器语义目标
     */
    String getSemanticGoal() {
        return semanticGoal;
    }

    /**
     * 返回截图验收预期。
     *
     * @return 截图验收预期
     */
    String getExpectedVisualObservation() {
        return expectedVisualObservation;
    }

    /**
     * 返回首轮计划用例数。
     *
     * @return 首轮计划用例数
     */
    int getPlannedCaseCount() {
        return plannedCaseCount;
    }

    /**
     * 返回首轮计划自动语义数。
     *
     * @return 首轮计划自动语义数
     */
    int getPlannedAutomaticCount() {
        return plannedAutomaticCount;
    }

    /**
     * 返回首轮计划人工确认数。
     *
     * @return 首轮计划人工确认数
     */
    int getPlannedManualCount() {
        return plannedManualCount;
    }

    /**
     * 校验并返回必填文本。
     *
     * @param value 待校验文本
     * @param name 字段名
     * @return 非空文本
     */
    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name);
        if (text.length() == 0) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return text;
    }

    /**
     * 校验截图验收预期前缀。
     *
     * @param value 待校验文本
     * @return 合法预期文本
     */
    private static String requireExpectedResult(String value) {
        String text = requireText(value, "expectedVisualObservation");
        if (!text.startsWith("预期结果：")) {
            throw new IllegalArgumentException("expectedVisualObservation must start with 预期结果：");
        }
        return text;
    }
}
