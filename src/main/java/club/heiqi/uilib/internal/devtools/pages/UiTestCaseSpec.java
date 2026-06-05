package club.heiqi.uilib.internal.devtools.pages;

import java.util.Objects;

/**
 * `/qzuilib test` 单个视觉语义样例规格。
 */
final class UiTestCaseSpec {

    private final String id;
    private final String groupCode;
    private final String displayTarget;
    private final String browserSemantic;
    private final String visualSample;
    private final String observationPoint;
    private final String semanticAssertion;
    private final String manualReason;

    /**
     * 创建单个视觉语义样例规格。
     *
     * @param id 样例编号
     * @param groupCode 分组代码
     * @param displayTarget 展示目标
     * @param browserSemantic 浏览器语义
     * @param visualSample 视觉样例说明
     * @param observationPoint 观察要点，必须以“预期结果：”开头
     * @param semanticAssertion 语义断言说明
     * @param manualReason 人工确认原因；无需人工时传空串
     */
    UiTestCaseSpec(String id, String groupCode, String displayTarget, String browserSemantic, String visualSample,
            String observationPoint, String semanticAssertion, String manualReason) {
        this.id = requireText(id, "id");
        this.groupCode = requireText(groupCode, "groupCode");
        this.displayTarget = requireText(displayTarget, "displayTarget");
        this.browserSemantic = requireText(browserSemantic, "browserSemantic");
        this.visualSample = requireText(visualSample, "visualSample");
        this.observationPoint = requireExpectedResult(observationPoint);
        this.semanticAssertion = requireText(semanticAssertion, "semanticAssertion");
        this.manualReason = manualReason == null ? "" : manualReason;
    }

    /**
     * 返回样例编号。
     *
     * @return 样例编号
     */
    String getId() {
        return id;
    }

    /**
     * 返回分组代码。
     *
     * @return 分组代码
     */
    String getGroupCode() {
        return groupCode;
    }

    /**
     * 返回展示目标。
     *
     * @return 展示目标
     */
    String getDisplayTarget() {
        return displayTarget;
    }

    /**
     * 返回浏览器语义。
     *
     * @return 浏览器语义
     */
    String getBrowserSemantic() {
        return browserSemantic;
    }

    /**
     * 返回视觉样例说明。
     *
     * @return 视觉样例说明
     */
    String getVisualSample() {
        return visualSample;
    }

    /**
     * 返回观察要点。
     *
     * @return 观察要点
     */
    String getObservationPoint() {
        return observationPoint;
    }

    /**
     * 返回语义断言说明。
     *
     * @return 语义断言说明
     */
    String getSemanticAssertion() {
        return semanticAssertion;
    }

    /**
     * 返回人工确认原因。
     *
     * @return 人工确认原因
     */
    String getManualReason() {
        return manualReason;
    }

    /**
     * 判断样例是否需要人工确认。
     *
     * @return 需要人工确认时返回 true
     */
    boolean requiresManualConfirmation() {
        return manualReason.length() > 0;
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
     * @return 合法观察要点
     */
    private static String requireExpectedResult(String value) {
        String text = requireText(value, "observationPoint");
        if (!text.startsWith("预期结果：")) {
            throw new IllegalArgumentException("observationPoint must start with 预期结果：");
        }
        return text;
    }
}
