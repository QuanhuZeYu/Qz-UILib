package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `/qzuilib test` RuntimeHost 分组视觉样例工厂。
 */
final class UiTestRuntimeHostVisualFactory {

    static final String ROLE_ATTRIBUTE = "data-ui-host-role";

    /**
     * 判断是否支持指定 RuntimeHost 样例。
     *
     * @param caseId 样例编号
     * @return 是否支持
     */
    boolean supports(String caseId) {
        return "VIS-HOST-001".equals(caseId)
                || "VIS-HOST-002".equals(caseId)
                || "VIS-HOST-003".equals(caseId)
                || "VIS-HOST-004".equals(caseId)
                || "VIS-HOST-005".equals(caseId);
    }

    /**
     * 追加 RuntimeHost 样例视觉舞台。
     *
     * @param document 文档实例
     * @param stage 样例舞台
     * @param testCase 样例规格
     */
    void appendCaseDemo(UiDocument document, ElementNode stage, UiTestCaseSpec testCase) {
        String id = testCase.getId();
        if ("VIS-HOST-001".equals(id)) {
            appendOpenTimingDemo(document, stage);
        } else if ("VIS-HOST-002".equals(id)) {
            appendResizeDemo(document, stage);
        } else if ("VIS-HOST-003".equals(id)) {
            appendRuntimeStatsDemo(document, stage);
        } else if ("VIS-HOST-004".equals(id)) {
            appendHudContainerInputDemo(document, stage);
        } else if ("VIS-HOST-005".equals(id)) {
            appendExceptionPanelDemo(document, stage);
        }
    }

    private void appendOpenTimingDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        row.append(createStatusCard(document, "open-command", "聊天命令", "/qzuilib test", 0xFF2563EB));
        row.append(createStatusCard(document, "open-deferred", "延后一帧", "post main open gate", 0xFF7C3AED));
        row.append(createStatusCard(document, "open-mounted", "页面挂载", "DocumentPage 已稳定显示", 0xFF059669));
        stage.append(row);
        appendMutedText(document, stage, "从聊天输入到下一帧开屏的时序链路，以三段状态牌展示。 ");
    }

    private void appendResizeDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        row.append(createViewportCard(document, "resize-before", "720x540", 118, 68, 0xFF1E3A8A));
        row.append(createViewportCard(document, "resize-after", "1120x720", 166, 86, 0xFF0F766E));
        row.append(createStatusCard(document, "viewport-fill", "viewport fill", "94% x 92%", 0xFF334155));
        stage.append(row);
        appendMutedText(document, stage, "调整窗口后应重新排布，并保持 viewport fill 与滚动位置稳定。 ");
    }

    private void appendRuntimeStatsDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        row.append(createStatusCard(document, "runtime-frame", "frame", "环境信息逐帧刷新", 0xFF1D4ED8));
        row.append(createStatusCard(document, "runtime-render", "render", "渲染耗时摘要", 0xFF059669));
        row.append(createStatusCard(document, "runtime-input", "input", "鼠标与窗口摘要", 0xFFF59E0B));
        stage.append(row);
        ElementNode summary = createStatusCard(document, "runtime-summary", "stats-source",
                "DocumentPageRuntimeView#getUiRuntimeStats", 0xFF334155);
        summary.style().setMinWidth(UiStyleLength.px(320));
        stage.append(summary);
        appendMutedText(document, stage, "自动断言检查 runtime stats 状态牌与来源摘要，具体数值在环境信息区实时显示。 ");
    }

    private void appendHudContainerInputDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        row.append(createStatusCard(document, "hud-display", "HUD display", "纯游戏内显示层", 0xFF0369A1));
        row.append(createStatusCard(document, "hud-interactive", "HUD input", "容器态命中后接管", 0xFF15803D));
        row.append(createStatusCard(document, "container-fallback", "native fallback", "点击外部归还焦点", 0xFF7C2D12));
        stage.append(row);
        appendMutedText(document, stage, "HUD 输入采用先鼠标命中、后键盘接管；未命中或外部点击放回宿主原生界面。 ");
    }

    private void appendExceptionPanelDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        row.append(createStatusCard(document, "exception-trigger", "故意失败", "throw RuntimeException", 0xFF7F1D1D));
        row.append(createStatusCard(document, "exception-panel", "异常面板", "错误类型 + message", 0xFFB91C1C));
        row.append(createStatusCard(document, "exception-safe", "客户端保活", "页面显示失败摘要", 0xFF334155));
        stage.append(row);
        ElementNode stack = createStackTraceCard(document);
        stage.append(stack);
        appendMutedText(document, stage, "异常样例展示预期面板结构，不在 JVM 自动断言中真实抛错。 ");
    }

    private ElementNode createViewportCard(UiDocument document, String role, String label, int width, int height,
            int color) {
        ElementNode card = createStatusCard(document, role, label, "layout preview", color);
        card.style()
                .setWidth(UiStyleLength.px(width))
                .setHeight(UiStyleLength.px(height))
                .setJustifyContent(UiJustifyContent.CENTER);
        return card;
    }

    private ElementNode createStackTraceCard(UiDocument document) {
        ElementNode stack = document.div();
        stack.setAttribute(ROLE_ATTRIBUTE, "exception-stack");
        stack.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(4))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF0F172A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFFEF4444)
                .setBorderRadius(UiStyleLength.px(9))
                .setTextColor(0xFFFFE4E6);
        appendHeading(document, stack, "UiTestExceptionPanel");
        appendMutedText(document, stack, "RuntimeException: deliberate host failure");
        appendMutedText(document, stack, "at VIS-HOST-005 exception panel smoke");
        return stack;
    }

    private ElementNode createRow(UiDocument document) {
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8))
                .setRowGap(UiStyleLength.px(8));
        return row;
    }

    private ElementNode createStatusCard(UiDocument document, String role, String title, String detail, int color) {
        ElementNode card = document.div();
        card.setAttribute(ROLE_ATTRIBUTE, role);
        card.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setMinWidth(UiStyleLength.px(138))
                .setMinHeight(UiStyleLength.px(54))
                .setPadding(UiStyleLength.px(9))
                .setBackgroundColor(color)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF93C5FD)
                .setBorderRadius(UiStyleLength.px(9))
                .setTextColor(0xFFFFFFFF);
        appendHeading(document, card, title);
        appendMutedText(document, card, detail);
        return card;
    }

    private void appendHeading(UiDocument document, ElementNode parent, String text) {
        ElementNode line = document.div();
        line.style()
                .setFontWeight(UiFontWeight.BOLD)
                .setTextColor(0xFFFFFFFF);
        line.appendText(text);
        parent.append(line);
    }

    private void appendMutedText(UiDocument document, ElementNode parent, String text) {
        ElementNode line = document.div();
        line.style().setTextColor(0xFFEAF1FF);
        line.appendText(text);
        parent.append(line);
    }
}
