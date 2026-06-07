package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiTextOverflow;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `/qzuilib test` TextFont 分组视觉样例工厂。
 */
final class UiTestTextVisualFactory {

    static final String ROLE_ATTRIBUTE = "data-ui-text-role";

    /**
     * 判断是否支持指定 TextFont 样例。
     *
     * @param caseId 样例编号
     * @return 是否支持
     */
    boolean supports(String caseId) {
        return "VIS-TEXT-001".equals(caseId)
                || "VIS-TEXT-002".equals(caseId)
                || "VIS-TEXT-003".equals(caseId)
                || "VIS-TEXT-004".equals(caseId)
                || "VIS-TEXT-005".equals(caseId);
    }

    /**
     * 追加 TextFont 样例视觉舞台。
     *
     * @param document 文档实例
     * @param stage 样例舞台
     * @param testCase 样例规格
     */
    void appendCaseDemo(UiDocument document, ElementNode stage, UiTestCaseSpec testCase) {
        String id = testCase.getId();
        if ("VIS-TEXT-001".equals(id)) {
            appendRawFormattedDemo(document, stage);
        } else if ("VIS-TEXT-002".equals(id)) {
            appendMetricsBaselineDemo(document, stage);
        } else if ("VIS-TEXT-003".equals(id)) {
            appendFallbackDemo(document, stage);
        } else if ("VIS-TEXT-004".equals(id)) {
            appendWrapTrimDemo(document, stage);
        } else if ("VIS-TEXT-005".equals(id)) {
            appendObfuscatedDemo(document, stage);
        }
    }

    private void appendRawFormattedDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        ElementNode raw = createTextPanel(document, "RAW 模式", 0xFF1E40AF);
        ElementNode rawLine = createTextLine(document, "raw-line", 0xFFEAF1FF);
        rawLine.appendRawText("RAW: §a绿色按普通字符显示");
        raw.append(rawLine);

        ElementNode formatted = createTextPanel(document, "Minecraft formatted", 0xFF166534);
        ElementNode formattedLine = createTextLine(document, "formatted-line", 0xFFEAF1FF);
        formattedLine.appendMinecraftText("FMT: §a绿色按 Minecraft 格式显示");
        formatted.append(formattedLine);

        row.append(raw).append(formatted);
        stage.append(row);
        appendMutedText(document, stage, "raw 应保留 §a 字符；formatted 进入 Minecraft 格式文本路径。 ");
    }

    private void appendMetricsBaselineDemo(UiDocument document, ElementNode stage) {
        ElementNode stack = createStack(document);
        ElementNode ruler = document.div();
        ruler.setAttribute(ROLE_ATTRIBUTE, "metrics-baseline");
        ruler.style()
                .setHeight(UiStyleLength.px(2))
                .setBackgroundColor(0xFFFFCC00)
                .setWidth(UiStyleLength.px(286));
        ElementNode line = createTextLine(document, "metrics-line", 0xFFEAF1FF);
        line.style()
                .setLineHeight(UiStyleLength.px(18))
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(0xFF0F172A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF38BDF8);
        line.appendRawText("ABC baseline / 中文混排 / ");
        ElementNode bold = createInlineBadge(document, "metrics-bold", "bold", 0xFF2563EB);
        bold.style().setFontWeight(UiFontWeight.BOLD);
        line.append(bold);
        ElementNode italic = createInlineBadge(document, "metrics-italic", "italic", 0xFF7C3AED);
        italic.style().setFontStyle(UiFontStyle.ITALIC);
        line.append(italic);
        stack.append(line).append(ruler);
        stage.append(stack);
        appendMutedText(document, stage, "黄色线作为 baseline 观察标尺；诊断读取测量宽度与 line-height。 ");
    }

    private void appendFallbackDemo(UiDocument document, ElementNode stage) {
        ElementNode panel = createTextPanel(document, "fallback 字形", 0xFF7C2D12);
        ElementNode line = createTextLine(document, "fallback-line", 0xFFFFFFFF);
        line.appendRawText("fallback: ASCII abc / 汉字 / Ω / ☃");
        panel.append(line);
        stage.append(panel);
        appendMutedText(document, stage, "需要截图确认宿主字体 fallback 不留下空白或异常方块。 ");
    }

    private void appendWrapTrimDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        ElementNode trim = createTextPanel(document, "trim / ellipsis", 0xFF334155);
        ElementNode trimLine = createTextLine(document, "trim-line", 0xFFEAF1FF);
        trimLine.style()
                .setWidth(UiStyleLength.px(132))
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setTextOverflow(UiTextOverflow.ELLIPSIS)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        trimLine.appendRawText("Very long raw text should trim inside one line");
        trim.append(trimLine);

        ElementNode wrap = createTextPanel(document, "wrap", 0xFF0F766E);
        ElementNode wrapBox = createTextLine(document, "wrap-box", 0xFFEAF1FF);
        wrapBox.style()
                .setWidth(UiStyleLength.px(132))
                .setWhiteSpace(UiWhiteSpace.NORMAL)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.VISIBLE);
        wrapBox.appendRawText("Very long raw text should wrap into multiple visible lines");
        wrap.append(wrapBox);

        row.append(trim).append(wrap);
        stage.append(row);
        appendMutedText(document, stage, "trim 固定单行裁剪，wrap 固定宽度内自动换行。 ");
    }

    private void appendObfuscatedDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        ElementNode obfuscated = createTextPanel(document, "obfuscated", 0xFF831843);
        ElementNode text = createTextLine(document, "obfuscated-line", 0xFFFFFFFF);
        text.style().setWidth(UiStyleLength.px(188));
        text.appendMinecraftText("§kQZ12345§r 固定宽度动态文本");
        obfuscated.append(text);

        ElementNode epoch = createTextPanel(document, "font epoch", 0xFF1E293B);
        ElementNode epochLine = createTextLine(document, "epoch-line", 0xFFEAF1FF);
        epochLine.appendRawText("epoch 由环境信息和断言读取");
        epoch.append(epochLine);

        row.append(obfuscated).append(epoch);
        stage.append(row);
        appendMutedText(document, stage, "§k 仍保留 formatted 文本模式；布局宽度应由固定面板稳定约束。 ");
    }

    private ElementNode createRow(UiDocument document) {
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(10))
                .setRowGap(UiStyleLength.px(10));
        return row;
    }

    private ElementNode createStack(UiDocument document) {
        ElementNode stack = document.div();
        stack.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6));
        return stack;
    }

    private ElementNode createTextPanel(UiDocument document, String title, int color) {
        ElementNode panel = createStack(document);
        panel.style()
                .setMinWidth(UiStyleLength.px(184))
                .setPadding(UiStyleLength.px(9))
                .setBackgroundColor(color)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF93C5FD)
                .setBorderRadius(UiStyleLength.px(9));
        ElementNode heading = document.div();
        heading.style().setFontWeight(UiFontWeight.BOLD).setTextColor(0xFFFFFFFF);
        heading.appendText(title);
        panel.append(heading);
        return panel;
    }

    private ElementNode createTextLine(UiDocument document, String role, int textColor) {
        ElementNode line = document.div();
        line.setAttribute(ROLE_ATTRIBUTE, role);
        line.style()
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(0xAA020617)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF334155)
                .setBorderRadius(UiStyleLength.px(6))
                .setTextColor(textColor);
        return line;
    }

    private ElementNode createInlineBadge(UiDocument document, String role, String text, int color) {
        ElementNode badge = document.div();
        badge.setAttribute(ROLE_ATTRIBUTE, role);
        badge.style()
                .setDisplay(UiDisplay.INLINE_BLOCK)
                .setPadding(UiStyleLength.px(3))
                .setMargin(UiStyleLength.px(2))
                .setBackgroundColor(color)
                .setBorderRadius(UiStyleLength.px(5))
                .setTextColor(0xFFFFFFFF);
        badge.appendText(text);
        return badge;
    }

    private void appendMutedText(UiDocument document, ElementNode parent, String text) {
        ElementNode line = document.div();
        line.style().setTextColor(0xFFC9D8F8);
        line.appendText(text);
        parent.append(line);
    }
}
