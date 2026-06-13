package club.heiqi.uilib.internal.devtools.pages;

import java.util.List;

import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;

/**
 * `/qzuilib test` Input 分组自动断言与人工诊断。
 */
final class UiTestInputAssertionRunner {

    /**
     * 判断指定样例是否具备自动断言。
     *
     * @param caseId 样例编号
     * @return 是否自动断言样例
     */
    boolean isAutomatic(String caseId) {
        return "VIS-INPUT-001".equals(caseId)
                || "VIS-INPUT-002".equals(caseId)
                || "VIS-INPUT-003".equals(caseId)
                || "VIS-INPUT-005".equals(caseId);
    }

    /**
     * 判断指定样例是否为 Input 人工诊断样例。
     *
     * @param caseId 样例编号
     * @return 是否人工诊断样例
     */
    boolean isManual(String caseId) {
        return "VIS-INPUT-004".equals(caseId);
    }

    /**
     * 执行 Input 自动断言。
     *
     * @param widget 文档组件
     * @param scope 样例舞台
     * @param testCase 样例规格
     * @param diagnostics 诊断摘要
     * @return 是否通过
     */
    boolean runAutomatic(HtmlLikeDocumentWidget widget, ElementNode scope, UiTestCaseSpec testCase,
            List<String> diagnostics) {
        ensureWidgetBounds(widget);
        String id = testCase.getId();
        if ("VIS-INPUT-001".equals(id)) {
            return assertPropagation(widget, scope, diagnostics);
        }
        if ("VIS-INPUT-002".equals(id)) {
            return assertPreventDefault(widget, scope, diagnostics);
        }
        if ("VIS-INPUT-003".equals(id)) {
            return assertWheel(widget, scope, diagnostics);
        }
        if ("VIS-INPUT-005".equals(id)) {
            return assertKeyboardTextInput(widget, scope, diagnostics);
        }
        diagnostics.add("未知 Input 自动样例：" + id);
        return false;
    }

    /**
     * 输出 Input 人工诊断信息。
     *
     * @param widget 文档组件
     * @param scope 样例舞台
     * @param testCase 样例规格
     * @param diagnostics 诊断摘要
     */
    void diagnoseManual(HtmlLikeDocumentWidget widget, ElementNode scope, UiTestCaseSpec testCase,
            List<String> diagnostics) {
        ensureWidgetBounds(widget);
        if ("VIS-INPUT-004".equals(testCase.getId())) {
            diagnoseFocusVisible(widget, scope, diagnostics);
            return;
        }
        diagnostics.add("未知 Input 人工样例：" + testCase.getId());
    }

    private boolean assertPropagation(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode target = findByRole(scope, "propagation-target");
        ElementNode logNode = findByRole(scope, "propagation-log");
        if (target == null || logNode == null) {
            diagnostics.add("propagation 节点缺失");
            return false;
        }
        clickElement(widget, target, 11L);
        String log = logNode.getTextContent();
        diagnostics.add("propagationLog=" + log);
        diagnostics.add("propagationDiff=expected root-capture -> target-capture -> target -> root-bubble");
        return log.contains("root-capture:CAPTURING")
                && log.contains("target-capture:AT_TARGET")
                && log.contains("target:AT_TARGET")
                && log.contains("root-bubble:BUBBLING")
                && log.indexOf("root-capture") < log.indexOf("target-capture")
                && log.indexOf("target-capture") < log.indexOf("target:AT_TARGET")
                && log.indexOf("target:AT_TARGET") < log.indexOf("root-bubble");
    }

    private boolean assertPreventDefault(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode normal = findByRole(scope, "prevent-default-normal");
        ElementNode blocked = findByRole(scope, "prevent-default-blocked");
        ElementNode logNode = findByRole(scope, "prevent-default-log");
        if (normal == null || blocked == null || logNode == null) {
            diagnostics.add("preventDefault 节点缺失");
            return false;
        }
        normal.focus();
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_RETURN, UiKeyCodes.KEY_RETURN, 0, UiKeyEvent.Action.PRESSED,
                false, false, false, false, 21L));
        blocked.focus();
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_RETURN, UiKeyCodes.KEY_RETURN, 0, UiKeyEvent.Action.PRESSED,
                false, false, false, false, 22L));
        String log = logNode.getTextContent();
        diagnostics.add("preventDefaultLog=" + log);
        diagnostics.add("preventDefaultDiff=expected default-click present, blocked-click-unexpected absent");
        return log.contains("default-click")
                && log.contains("prevent-default:AT_TARGET")
                && !log.contains("blocked-click-unexpected");
    }

    private boolean assertWheel(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode scroller = findByRole(scope, "wheel-scroller");
        ElementNode target = findByRole(scope, "wheel-target");
        ElementNode logNode = findByRole(scope, "wheel-log");
        if (scroller == null || target == null || logNode == null) {
            diagnostics.add("wheel 节点缺失");
            return false;
        }
        int before = widget.getScrollTop(scroller);
        int max = widget.getMaxScrollTop(scroller);
        wheelElement(widget, target, -120, 31L);
        int after = widget.getScrollTop(scroller);
        String log = logNode.getTextContent();
        HtmlLikeDocumentWidget.ScrollInputDiagnosticsSnapshot snapshot = widget.getScrollInputDiagnosticsSnapshot();
        diagnostics.add("wheelLog=" + log);
        diagnostics.add("wheelScrollBefore=" + before + ", after=" + after + ", max=" + max);
        diagnostics.add("wheelInputSnapshot=count=" + snapshot.getEventCount() + ", delta="
                + snapshot.getLastWheelDelta() + ", consumed=" + snapshot.isLastConsumed());
        diagnostics.add("wheelDiff=expected wheel log before default scroll and scrollTop increase");
        return max > 0
                && after > before
                && log.contains("root-capture:CAPTURING")
                && log.contains("target:AT_TARGET:deltaY=120")
                && log.contains("root-bubble:BUBBLING")
                && snapshot.isLastConsumed();
    }

    private boolean assertKeyboardTextInput(HtmlLikeDocumentWidget widget, ElementNode scope,
            List<String> diagnostics) {
        ElementNode target = findByRole(scope, "keyboard-target");
        ElementNode logNode = findByRole(scope, "keyboard-log");
        if (target == null || logNode == null) {
            diagnostics.add("keyboard/textInput 节点缺失");
            return false;
        }
        target.focus();
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_A, UiKeyCodes.KEY_A, 0, UiKeyEvent.Action.PRESSED,
                false, false, false, false, 41L));
        widget.onTextInput(new UiTextInputEvent("A字", 42L));
        String log = logNode.getTextContent();
        diagnostics.add("keyboardTextLog=" + log);
        diagnostics.add("keyboardTextDiff=expected key capture/target/bubble before textInput capture/target/bubble");
        return log.contains("key-root-capture:CAPTURING")
                && log.contains("key-target-capture:AT_TARGET")
                && log.contains("key-target:AT_TARGET:code=" + UiKeyCodes.KEY_A)
                && log.contains("key-root-bubble:BUBBLING")
                && log.contains("text-root-capture:CAPTURING")
                && log.contains("text-target-capture:AT_TARGET")
                && log.contains("text-target:AT_TARGET:A字")
                && log.contains("text-root-bubble:BUBBLING")
                && log.indexOf("key-root-capture") < log.indexOf("text-root-capture");
    }

    private void diagnoseFocusVisible(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode programmatic = findByRole(scope, "focus-programmatic");
        ElementNode keyboard = findByRole(scope, "focus-keyboard");
        ElementNode logNode = findByRole(scope, "focus-visible-log");
        if (programmatic == null || keyboard == null || logNode == null) {
            diagnostics.add("focus-visible 节点缺失，保持人工待确认。 ");
            return;
        }
        programmatic.focus();
        widget.onFocusTraversal(false);
        String log = logNode.getTextContent();
        ElementNode focused = widget.getFocusedElement();
        diagnostics.add("focusVisibleLog=" + log);
        diagnostics.add("focusVisibleFocusedRole=" + (focused == null ? "null" : focused.getAttribute(
                UiTestInputVisualFactory.ROLE_ATTRIBUTE)));
        diagnostics.add("focusVisibleDiff=事件日志可机器诊断；focus ring 颜色、键鼠切换手感需游戏内人工确认。 ");
    }

    private void clickElement(HtmlLikeDocumentWidget widget, ElementNode element, long timeNanos) {
        int[] center = resolveElementCenter(widget, element);
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, center[0], center[1], 0, 0, 0, 0,
                timeNanos));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, center[0], center[1], 0, 0, 0, 0,
                timeNanos + 1L));
    }

    private void wheelElement(HtmlLikeDocumentWidget widget, ElementNode element, int wheelDelta, long timeNanos) {
        int[] center = resolveElementCenter(widget, element);
        widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, center[0], center[1], -1, wheelDelta, 0, 0,
                timeNanos));
    }

    private int[] resolveElementCenter(HtmlLikeDocumentWidget widget, ElementNode element) {
        DocumentLayoutBox box = resolveBox(widget, element);
        int x = widget.getAbsoluteX() + box.getLeft() + Math.max(1, box.getWidth() / 2);
        int y = widget.getAbsoluteY() + box.getTop() + Math.max(1, box.getHeight() / 2);
        return new int[] { x, y };
    }

    private DocumentLayoutBox resolveBox(HtmlLikeDocumentWidget widget, ElementNode element) {
        DocumentLayoutBox box = findLayoutBox(widget.resolveLayoutBoxForTest(), element);
        if (box == null) {
            throw new IllegalStateException("未找到 Input 样例布局盒: " + element.getTagName());
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

    private ElementNode findByRole(ElementNode current, String role) {
        if (current == null || role == null) {
            return null;
        }
        if (role.equals(current.getAttribute(UiTestInputVisualFactory.ROLE_ATTRIBUTE))) {
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

    private void ensureWidgetBounds(HtmlLikeDocumentWidget widget) {
        if (widget.getWidth() <= 0 || widget.getHeight() <= 0) {
            widget.applyLayoutBounds(0, 0, 760, 520);
        }
    }
}
