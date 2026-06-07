package club.heiqi.uilib.internal.devtools.pages;

import java.util.List;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiTextOverflow;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `/qzuilib test` TextFont 分组自动断言与人工诊断。
 */
final class UiTestTextAssertionRunner {

    /**
     * 判断指定样例是否具备自动断言。
     *
     * @param caseId 样例编号
     * @return 是否自动断言样例
     */
    boolean isAutomatic(String caseId) {
        return "VIS-TEXT-001".equals(caseId)
                || "VIS-TEXT-002".equals(caseId)
                || "VIS-TEXT-004".equals(caseId)
                || "VIS-TEXT-005".equals(caseId);
    }

    /**
     * 判断指定样例是否为 TextFont 人工诊断样例。
     *
     * @param caseId 样例编号
     * @return 是否人工诊断样例
     */
    boolean isManual(String caseId) {
        return "VIS-TEXT-003".equals(caseId);
    }

    /**
     * 执行 TextFont 自动断言。
     *
     * @param widget 文档组件
     * @param scope 样例舞台
     * @param testCase 样例规格
     * @param diagnostics 诊断摘要
     * @return 是否通过
     */
    boolean runAutomatic(HtmlLikeDocumentWidget widget, ElementNode scope, UiTestCaseSpec testCase,
            List<String> diagnostics) {
        String id = testCase.getId();
        if ("VIS-TEXT-001".equals(id)) {
            return assertRawFormatted(widget, scope, diagnostics);
        }
        if ("VIS-TEXT-002".equals(id)) {
            return assertMetricsBaseline(widget, scope, diagnostics);
        }
        if ("VIS-TEXT-004".equals(id)) {
            return assertWrapTrim(widget, scope, diagnostics);
        }
        if ("VIS-TEXT-005".equals(id)) {
            return assertObfuscated(widget, scope, diagnostics);
        }
        diagnostics.add("未知 TextFont 自动样例：" + id);
        return false;
    }

    /**
     * 输出 TextFont 人工诊断信息。
     *
     * @param widget 文档组件
     * @param scope 样例舞台
     * @param testCase 样例规格
     * @param diagnostics 诊断摘要
     */
    void diagnoseManual(HtmlLikeDocumentWidget widget, ElementNode scope, UiTestCaseSpec testCase,
            List<String> diagnostics) {
        if ("VIS-TEXT-003".equals(testCase.getId())) {
            diagnoseFallback(widget, scope, diagnostics);
            return;
        }
        diagnostics.add("未知 TextFont 人工样例：" + testCase.getId());
    }

    private boolean assertRawFormatted(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode raw = findByRole(scope, "raw-line");
        ElementNode formatted = findByRole(scope, "formatted-line");
        TextNode rawText = firstTextNode(raw);
        TextNode formattedText = firstTextNode(formatted);
        if (rawText == null || formattedText == null) {
            diagnostics.add("raw/formatted 文本节点缺失");
            return false;
        }
        TextMeasureService measureService = widget.getTextMeasureService();
        int rawWidth = measureService.getStringWidth(rawText.getText(), rawText.getTextContentMode());
        int formattedWidth = measureService.getStringWidth(formattedText.getText(), formattedText.getTextContentMode());
        diagnostics.add("rawMode=" + rawText.getTextContentMode() + ", formattedMode="
                + formattedText.getTextContentMode());
        diagnostics.add("rawText=" + rawText.getText() + ", formattedText=" + formattedText.getText());
        diagnostics.add("rawWidth=" + rawWidth + ", formattedWidth=" + formattedWidth);
        diagnostics.add("rawFormattedDiff=expected raw=UILIB_RAW and formatted=MINECRAFT_FORMATTED with original §a text retained");
        return rawText.getTextContentMode() == TextContentMode.UILIB_RAW
                && formattedText.getTextContentMode() == TextContentMode.MINECRAFT_FORMATTED
                && rawText.getText().contains("§a")
                && formattedText.getText().contains("§a")
                && rawWidth > 0
                && formattedWidth > 0;
    }

    private boolean assertMetricsBaseline(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode line = findByRole(scope, "metrics-line");
        ElementNode bold = findByRole(scope, "metrics-bold");
        ElementNode italic = findByRole(scope, "metrics-italic");
        if (line == null || bold == null || italic == null) {
            diagnostics.add("metrics/baseline 节点缺失");
            return false;
        }
        TextMeasureService measureService = widget.getTextMeasureService();
        DocumentLayoutBox lineBox = resolveBox(widget, line);
        DocumentLayoutBox boldBox = resolveBox(widget, bold);
        DocumentLayoutBox italicBox = resolveBox(widget, italic);
        ComputedStyle boldStyle = boldBox.getComputedStyle();
        ComputedStyle italicStyle = italicBox.getComputedStyle();
        int latinWidth = measureService.getStringWidth("ABC", TextContentMode.UILIB_RAW,
                UiFontWeight.NORMAL, UiFontStyle.NORMAL);
        int boldWidth = measureService.getStringWidth("bold", TextContentMode.UILIB_RAW,
                UiFontWeight.BOLD, UiFontStyle.NORMAL);
        diagnostics.add("fontLineHeight=" + measureService.getLineHeight() + ", latinWidth=" + latinWidth
                + ", boldWidth=" + boldWidth);
        diagnostics.add("lineBox=w=" + lineBox.getWidth() + ",h=" + lineBox.getHeight()
                + ", boldBox=w=" + boldBox.getWidth() + ", italicBox=w=" + italicBox.getWidth());
        diagnostics.add("fontStyles=bold=" + boldStyle.getFontWeight() + ", italic=" + italicStyle.getFontStyle());
        diagnostics.add("metricsDiff=expected measurement width > 0, line-height > 0 and styled inline badges present");
        return measureService.getLineHeight() > 0
                && latinWidth > 0
                && boldWidth > 0
                && lineBox.getHeight() > 0
                && boldStyle.getFontWeight() == UiFontWeight.BOLD
                && italicStyle.getFontStyle() == UiFontStyle.ITALIC;
    }

    private void diagnoseFallback(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode fallback = findByRole(scope, "fallback-line");
        TextNode text = firstTextNode(fallback);
        if (fallback == null || text == null) {
            diagnostics.add("fallback 节点缺失，保持人工待确认。 ");
            return;
        }
        DocumentLayoutBox box = resolveBox(widget, fallback);
        int measuredWidth = widget.getTextMeasureService().getStringWidth(text.getText(), text.getTextContentMode());
        diagnostics.add("fallbackText=" + text.getText());
        diagnostics.add("fallbackWidth=" + measuredWidth + ", box=w=" + box.getWidth() + ",h=" + box.getHeight());
        diagnostics.add("fallbackDiff=文本内容与测量可机器诊断；真实 fallback 字形是否可见需游戏内截图确认。 ");
    }

    private boolean assertWrapTrim(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode trim = findByRole(scope, "trim-line");
        ElementNode wrap = findByRole(scope, "wrap-box");
        TextNode trimText = firstTextNode(trim);
        TextNode wrapText = firstTextNode(wrap);
        if (trim == null || wrap == null || trimText == null || wrapText == null) {
            diagnostics.add("wrap/trim 节点缺失");
            return false;
        }
        DocumentLayoutBox trimBox = resolveBox(widget, trim);
        DocumentLayoutBox wrapBox = resolveBox(widget, wrap);
        ComputedStyle trimStyle = trimBox.getComputedStyle();
        ComputedStyle wrapStyle = wrapBox.getComputedStyle();
        TextMeasureService measureService = widget.getTextMeasureService();
        String trimmed = measureService.trimStringToWidth(trimText.getText(), 64, trimText.getTextContentMode());
        List<String> wrapped = measureService.listFormattedStringToWidth(wrapText.getText(), 64,
                wrapText.getTextContentMode());
        diagnostics.add("trimStyle=whiteSpace=" + trimStyle.getWhiteSpace() + ", overflow="
                + trimStyle.getTextOverflow());
        diagnostics.add("wrapStyle=whiteSpace=" + wrapStyle.getWhiteSpace() + ", wrapLines=" + wrapped.size());
        diagnostics.add("trimmedText=" + trimmed + ", trimBox=w=" + trimBox.getWidth() + ", wrapBox=w="
                + wrapBox.getWidth());
        diagnostics.add("wrapTrimDiff=expected trim NOWRAP/ELLIPSIS and wrap NORMAL within fixed width");
        return trimStyle.getWhiteSpace() == UiWhiteSpace.NOWRAP
                && trimStyle.getTextOverflow() == UiTextOverflow.ELLIPSIS
                && wrapStyle.getWhiteSpace() == UiWhiteSpace.NORMAL
                && trimBox.getWidth() > 0
                && wrapBox.getWidth() > 0
                && wrapped.size() > 0;
    }

    private boolean assertObfuscated(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode obfuscated = findByRole(scope, "obfuscated-line");
        ElementNode epoch = findByRole(scope, "epoch-line");
        TextNode text = firstTextNode(obfuscated);
        if (obfuscated == null || epoch == null || text == null) {
            diagnostics.add("obfuscated 节点缺失");
            return false;
        }
        DocumentLayoutBox obfuscatedBox = resolveBox(widget, obfuscated);
        int epochValue = widget.getTextMeasureService().getEpoch();
        int measuredWidth = widget.getTextMeasureService().getStringWidth(text.getText(), text.getTextContentMode());
        diagnostics.add("obfuscatedMode=" + text.getTextContentMode() + ", text=" + text.getText());
        diagnostics.add("fontEpoch=" + epochValue + ", measuredWidth=" + measuredWidth
                + ", obfuscatedBox=w=" + obfuscatedBox.getWidth() + ",h=" + obfuscatedBox.getHeight());
        diagnostics.add("obfuscatedDiff=expected §k formatted text, stable fixed-width layout box and non-negative font epoch");
        return text.getTextContentMode() == TextContentMode.MINECRAFT_FORMATTED
                && text.getText().contains("§k")
                && epochValue >= 0
                && measuredWidth > 0
                && obfuscatedBox.getWidth() >= 160;
    }

    private ElementNode findByRole(ElementNode current, String role) {
        if (current == null || role == null) {
            return null;
        }
        if (role.equals(current.getAttribute(UiTestTextVisualFactory.ROLE_ATTRIBUTE))) {
            return current;
        }
        for (DocumentNode child : current.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findByRole((ElementNode) child, role);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private TextNode firstTextNode(ElementNode element) {
        if (element == null) {
            return null;
        }
        for (DocumentNode child : element.getChildren()) {
            if (child instanceof TextNode) {
                return (TextNode) child;
            }
            if (child instanceof ElementNode) {
                TextNode found = firstTextNode((ElementNode) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private DocumentLayoutBox resolveBox(HtmlLikeDocumentWidget widget, ElementNode element) {
        DocumentLayoutBox box = findLayoutBox(widget.resolveLayoutBoxForTest(), element);
        if (box == null) {
            throw new IllegalStateException("未找到 TextFont 样例布局盒: " + element.getTagName());
        }
        return box;
    }

    private DocumentLayoutBox findLayoutBox(DocumentLayoutBox current, ElementNode element) {
        if (current == null || element == null) {
            return null;
        }
        if (current.getElement() == element) {
            return current;
        }
        for (DocumentLayoutBox child : current.getChildren()) {
            DocumentLayoutBox found = findLayoutBox(child, element);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
