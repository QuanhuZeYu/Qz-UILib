package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;

/**
 * `/qzuilib test` 首批运行时断言执行器。
 */
final class UiTestAssertionRunner {

    /**
     * 运行指定样例的首批断言。
     *
     * @param documentWidget 文档组件
     * @param testCase 样例规格
     * @param logger 断言日志记录器
     * @return 断言结果
     */
    UiTestCaseResult run(HtmlLikeDocumentWidget documentWidget, UiTestCaseSpec testCase, UiTestAssertionLogger logger) {
        long now = System.currentTimeMillis();
        logger.log(new UiTestAssertionLogEntry(testCase.getId(), testCase.getGroupCode(), "start",
                "开始运行样例断言", testCase.getSemanticAssertion(), "准备解析舞台", "", now));
        try {
            if (documentWidget == null || documentWidget.getDocument() == null) {
                return buildFailure(testCase, logger, "document", "文档未初始化", "文档已构建", "document=null");
            }
            ElementNode root = documentWidget.getDocument().getRootElement();
            ElementNode scope = findElementByAttribute(root, "data-ui-test-case", testCase.getId());
            if (scope == null) {
                return buildFailure(testCase, logger, "scope", "未找到样例舞台", testCase.getId(), "null");
            }
            DocumentLayoutBox rootBox = documentWidget.resolveLayoutBoxForTest();
            if (rootBox == null) {
                return buildFailure(testCase, logger, "layout", "布局盒不可用", "非空布局盒", "null");
            }
            DocumentLayoutBox scopeBox = findLayoutBox(rootBox, scope);
            if (scopeBox == null) {
                return buildFailure(testCase, logger, "layout", "未找到样例布局盒", testCase.getId(), "null");
            }

            List<String> diagnostics = new ArrayList<String>();
            boolean passed;
            if ("VIS-CSS-001".equals(testCase.getId())) {
                passed = assertCssSpecificity(documentWidget, scope, diagnostics);
            } else if ("VIS-CSS-002".equals(testCase.getId())) {
                passed = assertCssBoxSizing(documentWidget, scope, diagnostics);
            } else if ("VIS-CSS-003".equals(testCase.getId())) {
                passed = assertCssVisibility(documentWidget, scope, diagnostics);
            } else if ("VIS-LAYOUT-001".equals(testCase.getId())) {
                passed = assertLayoutBlockFlow(documentWidget, scope, diagnostics);
            } else if ("VIS-LAYOUT-002".equals(testCase.getId())) {
                passed = assertLayoutFlex(documentWidget, scope, diagnostics);
            } else if ("VIS-LAYOUT-003".equals(testCase.getId())) {
                passed = assertLayoutTable(documentWidget, scope, diagnostics);
            } else if ("VIS-PAINT-001".equals(testCase.getId())) {
                passed = assertPaintStacking(documentWidget, scope, diagnostics);
            } else if ("VIS-PAINT-002".equals(testCase.getId())) {
                passed = assertPaintClip(documentWidget, scope, diagnostics);
            } else {
                diagnostics.add("当前样例未接入自动断言，保持人工待确认。");
                logger.log(new UiTestAssertionLogEntry(testCase.getId(), testCase.getGroupCode(), "skip",
                        "当前样例未接入自动断言", testCase.getSemanticAssertion(), diagnostics.get(0), "", now));
                return new UiTestCaseResult(UiTestVisualStatus.DISPLAYING, UiTestSemanticStatus.MANUAL_PENDING,
                        UiTestSummaryStatus.PENDING, join(diagnostics), "");
            }

            String actual = join(diagnostics);
            if (passed) {
                logger.log(new UiTestAssertionLogEntry(testCase.getId(), testCase.getGroupCode(), "pass",
                        "自动断言通过", testCase.getSemanticAssertion(), actual, "", System.currentTimeMillis()));
                return new UiTestCaseResult(UiTestVisualStatus.DISPLAYING, UiTestSemanticStatus.AUTO_PASSED,
                        UiTestSummaryStatus.PARTIAL_PASSED, actual, "");
            }
            logger.log(new UiTestAssertionLogEntry(testCase.getId(), testCase.getGroupCode(), "fail",
                    "自动断言失败", testCase.getSemanticAssertion(), actual, actual, System.currentTimeMillis()));
            return new UiTestCaseResult(UiTestVisualStatus.DISPLAYING, UiTestSemanticStatus.AUTO_FAILED,
                    UiTestSummaryStatus.FAILED, actual, actual);
        } catch (RuntimeException exception) {
            logger.log(new UiTestAssertionLogEntry(testCase.getId(), testCase.getGroupCode(), "exception",
                    "自动断言异常", testCase.getSemanticAssertion(), exception.getClass().getSimpleName() + ":"
                            + exception.getMessage(), "异常中断", System.currentTimeMillis()));
            return new UiTestCaseResult(UiTestVisualStatus.DISPLAYING, UiTestSemanticStatus.AUTO_FAILED,
                    UiTestSummaryStatus.FAILED, exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                    "断言执行抛出异常");
        }
    }

    private UiTestCaseResult buildFailure(UiTestCaseSpec testCase, UiTestAssertionLogger logger, String phase,
            String message, String expected, String actual) {
        logger.log(new UiTestAssertionLogEntry(testCase.getId(), testCase.getGroupCode(), phase, message, expected,
                actual, expected + " != " + actual, System.currentTimeMillis()));
        return new UiTestCaseResult(UiTestVisualStatus.DISPLAYING, UiTestSemanticStatus.AUTO_FAILED,
                UiTestSummaryStatus.FAILED, message + "；actual=" + actual, expected + " != " + actual);
    }

    private boolean assertCssSpecificity(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode sample = findByTag(scope, "sample");
        ElementNode classNode = findByClass(scope, "specificity-class");
        ElementNode idNode = findById(scope, "specificity-id");
        if (sample == null || classNode == null || idNode == null) {
            diagnostics.add("specificity 节点缺失");
            return false;
        }
        ComputedStyle sampleStyle = resolveBox(widget, sample).getComputedStyle();
        ComputedStyle classStyle = resolveBox(widget, classNode).getComputedStyle();
        ComputedStyle idStyle = resolveBox(widget, idNode).getComputedStyle();
        diagnostics.add("sampleBg=" + toHex(sampleStyle.getBackgroundColor()));
        diagnostics.add("classBg=" + toHex(classStyle.getBackgroundColor()));
        diagnostics.add("idBg=" + toHex(idStyle.getBackgroundColor()));
        return sampleStyle.getBackgroundColor() == 0xFF334155
                && classStyle.getBackgroundColor() == 0xFF2563EB
                && idStyle.getBackgroundColor() == 0xFF059669;
    }

    private boolean assertCssBoxSizing(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode contentBox = findByText(scope, "content-box");
        ElementNode borderBox = findByText(scope, "border-box");
        if (contentBox == null || borderBox == null) {
            diagnostics.add("box-sizing 节点缺失");
            return false;
        }
        DocumentLayoutBox contentBoxLayout = resolveBox(widget, contentBox);
        DocumentLayoutBox borderBoxLayout = resolveBox(widget, borderBox);
        diagnostics.add("contentBoxWidth=" + contentBoxLayout.getWidth());
        diagnostics.add("borderBoxWidth=" + borderBoxLayout.getWidth());
        return contentBoxLayout.getWidth() > borderBoxLayout.getWidth();
    }

    private boolean assertCssVisibility(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode hidden = findByText(scope, "hidden 占位");
        ElementNode pointerNone = findByText(scope, "pointer-events:none");
        if (hidden == null || pointerNone == null) {
            diagnostics.add("visibility 节点缺失");
            return false;
        }
        ComputedStyle hiddenStyle = resolveBox(widget, hidden).getComputedStyle();
        ComputedStyle pointerStyle = resolveBox(widget, pointerNone).getComputedStyle();
        diagnostics.add("hiddenVisibility=" + hiddenStyle.getVisibility());
        diagnostics.add("pointerEvents=" + pointerStyle.getPointerEvents());
        return hiddenStyle.getVisibility().name().equals("HIDDEN")
                && pointerStyle.getPointerEvents().name().equals("NONE");
    }

    private boolean assertLayoutBlockFlow(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode first = findByText(scope, "Block A margin-bottom=18");
        ElementNode second = findByText(scope, "Block B margin-top=24");
        if (first == null || second == null) {
            diagnostics.add("block flow 节点缺失");
            return false;
        }
        DocumentLayoutBox firstBox = resolveBox(widget, first);
        DocumentLayoutBox secondBox = resolveBox(widget, second);
        diagnostics.add("firstTop=" + firstBox.getTop());
        diagnostics.add("secondTop=" + secondBox.getTop());
        return secondBox.getTop() > firstBox.getTop();
    }

    private boolean assertLayoutFlex(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode minContent = findByText(scope, "min-content item keeps long label");
        ElementNode minWidthZero = findByText(scope, "min-width:0 item");
        if (minContent == null || minWidthZero == null) {
            diagnostics.add("flex 节点缺失");
            return false;
        }
        DocumentLayoutBox minContentBox = resolveBox(widget, minContent);
        DocumentLayoutBox minWidthZeroBox = resolveBox(widget, minWidthZero);
        diagnostics.add("minContentWidth=" + minContentBox.getWidth());
        diagnostics.add("minWidthZeroWidth=" + minWidthZeroBox.getWidth());
        return minContentBox.getWidth() >= minWidthZeroBox.getWidth();
    }

    private boolean assertLayoutTable(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode shortCell = findByText(scope, "A");
        ElementNode longCell = findByText(scope, "content driven width");
        if (shortCell == null || longCell == null) {
            diagnostics.add("table 节点缺失");
            return false;
        }
        DocumentLayoutBox shortBox = resolveBox(widget, shortCell);
        DocumentLayoutBox longBox = resolveBox(widget, longCell);
        diagnostics.add("shortCellWidth=" + shortBox.getWidth());
        diagnostics.add("longCellWidth=" + longBox.getWidth());
        return longBox.getWidth() >= shortBox.getWidth();
    }

    private boolean assertPaintStacking(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode red = findByText(scope, "red z=1");
        ElementNode blue = findByText(scope, "blue opacity=.72 z=2");
        ElementNode green = findByText(scope, "green z=3");
        if (red == null || blue == null || green == null) {
            diagnostics.add("stacking 节点缺失");
            return false;
        }
        diagnostics.add("redZ=" + resolveBox(widget, red).getComputedStyle().getZIndex());
        diagnostics.add("blueOpacity=" + resolveBox(widget, blue).getComputedStyle().getOpacity());
        diagnostics.add("greenZ=" + resolveBox(widget, green).getComputedStyle().getZIndex());
        Integer redZ = resolveBox(widget, red).getComputedStyle().getZIndex();
        Integer greenZ = resolveBox(widget, green).getComputedStyle().getZIndex();
        return redZ != null && greenZ != null && greenZ.intValue() > redZ.intValue();
    }

    private boolean assertPaintClip(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode clipBoundary = findByText(scope, "clip boundary");
        ElementNode bar = findByText(scope, "wide child clipped by parent");
        if (clipBoundary == null || bar == null) {
            diagnostics.add("clip 节点缺失");
            return false;
        }
        DocumentLayoutBox clipBox = resolveBox(widget, clipBoundary.getParent() instanceof ElementNode
                ? (ElementNode) clipBoundary.getParent() : clipBoundary);
        DocumentLayoutBox barBox = resolveBox(widget, bar);
        diagnostics.add("clipWidth=" + clipBox.getWidth());
        diagnostics.add("barWidth=" + barBox.getWidth());
        return barBox.getWidth() > clipBox.getWidth();
    }

    private DocumentLayoutBox resolveBox(HtmlLikeDocumentWidget widget, ElementNode element) {
        DocumentLayoutBox box = findLayoutBox(widget.resolveLayoutBoxForTest(), element);
        if (box == null) {
            throw new IllegalStateException("未找到布局盒: " + element.getTagName());
        }
        return box;
    }

    private ElementNode findElementByAttribute(ElementNode current, String name, String value) {
        if (current == null) {
            return null;
        }
        if (value.equals(current.getAttribute(name))) {
            return current;
        }
        for (club.heiqi.uilib.ui.dom.DocumentNode child : current.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findElementByAttribute((ElementNode) child, name, value);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private ElementNode findByText(ElementNode current, String text) {
        if (current == null || text == null) {
            return null;
        }
        for (club.heiqi.uilib.ui.dom.DocumentNode child : current.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findByText((ElementNode) child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        if (text.equals(current.getTextContent())) {
            return current;
        }
        return null;
    }

    private ElementNode findById(ElementNode current, String id) {
        if (current == null || id == null) {
            return null;
        }
        if (id.equals(current.getId())) {
            return current;
        }
        for (club.heiqi.uilib.ui.dom.DocumentNode child : current.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findById((ElementNode) child, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private ElementNode findByClass(ElementNode current, String className) {
        if (current == null || className == null) {
            return null;
        }
        if (current.getClassList().contains(className)) {
            return current;
        }
        for (club.heiqi.uilib.ui.dom.DocumentNode child : current.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findByClass((ElementNode) child, className);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private ElementNode findByTag(ElementNode current, String tagName) {
        if (current == null || tagName == null) {
            return null;
        }
        if (tagName.equals(current.getTagName())) {
            return current;
        }
        for (club.heiqi.uilib.ui.dom.DocumentNode child : current.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findByTag((ElementNode) child, tagName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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

    private String toHex(int color) {
        return String.format(java.util.Locale.ROOT, "0x%08X", Integer.valueOf(color));
    }

    private String join(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "无诊断输出。";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            builder.append(lines.get(index));
        }
        return builder.toString();
    }
}
