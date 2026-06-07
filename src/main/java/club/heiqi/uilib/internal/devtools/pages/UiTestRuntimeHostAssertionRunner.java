package club.heiqi.uilib.internal.devtools.pages;

import java.util.List;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;

/**
 * `/qzuilib test` RuntimeHost 分组自动断言与人工诊断。
 */
final class UiTestRuntimeHostAssertionRunner {

    /**
     * 判断指定样例是否具备自动断言。
     *
     * @param caseId 样例编号
     * @return 是否自动断言样例
     */
    boolean isAutomatic(String caseId) {
        return "VIS-HOST-003".equals(caseId);
    }

    /**
     * 判断指定样例是否为 RuntimeHost 人工诊断样例。
     *
     * @param caseId 样例编号
     * @return 是否人工诊断样例
     */
    boolean isManual(String caseId) {
        return "VIS-HOST-001".equals(caseId)
                || "VIS-HOST-002".equals(caseId)
                || "VIS-HOST-004".equals(caseId)
                || "VIS-HOST-005".equals(caseId);
    }

    /**
     * 执行 RuntimeHost 自动断言。
     *
     * @param widget 文档组件
     * @param scope 样例舞台
     * @param testCase 样例规格
     * @param diagnostics 诊断摘要
     * @return 是否通过
     */
    boolean runAutomatic(HtmlLikeDocumentWidget widget, ElementNode scope, UiTestCaseSpec testCase,
            List<String> diagnostics) {
        if ("VIS-HOST-003".equals(testCase.getId())) {
            return assertRuntimeStatsSummary(widget, scope, diagnostics);
        }
        diagnostics.add("未知 RuntimeHost 自动样例：" + testCase.getId());
        return false;
    }

    /**
     * 输出 RuntimeHost 人工诊断信息。
     *
     * @param widget 文档组件
     * @param scope 样例舞台
     * @param testCase 样例规格
     * @param diagnostics 诊断摘要
     */
    void diagnoseManual(HtmlLikeDocumentWidget widget, ElementNode scope, UiTestCaseSpec testCase,
            List<String> diagnostics) {
        String id = testCase.getId();
        if ("VIS-HOST-001".equals(id)) {
            diagnoseOpenTiming(widget, scope, diagnostics);
        } else if ("VIS-HOST-002".equals(id)) {
            diagnoseResize(widget, scope, diagnostics);
        } else if ("VIS-HOST-004".equals(id)) {
            diagnoseHudContainerInput(widget, scope, diagnostics);
        } else if ("VIS-HOST-005".equals(id)) {
            diagnoseExceptionPanel(widget, scope, diagnostics);
        } else {
            diagnostics.add("未知 RuntimeHost 人工样例：" + id);
        }
    }

    private boolean assertRuntimeStatsSummary(HtmlLikeDocumentWidget widget, ElementNode scope,
            List<String> diagnostics) {
        ElementNode frame = findByRole(scope, "runtime-frame");
        ElementNode render = findByRole(scope, "runtime-render");
        ElementNode input = findByRole(scope, "runtime-input");
        ElementNode summary = findByRole(scope, "runtime-summary");
        if (frame == null || render == null || input == null || summary == null) {
            diagnostics.add("runtime stats 状态牌缺失");
            return false;
        }
        DocumentLayoutBox frameBox = resolveBox(widget, frame);
        DocumentLayoutBox renderBox = resolveBox(widget, render);
        DocumentLayoutBox inputBox = resolveBox(widget, input);
        diagnostics.add("runtimeFrameText=" + frame.getTextContent());
        diagnostics.add("runtimeRenderText=" + render.getTextContent());
        diagnostics.add("runtimeInputText=" + input.getTextContent());
        diagnostics.add("runtimeSummaryText=" + summary.getTextContent());
        diagnostics.add("runtimeFrameBox=" + summarizeBox(frameBox));
        diagnostics.add("runtimeRenderBox=" + summarizeBox(renderBox));
        diagnostics.add("runtimeInputBox=" + summarizeBox(inputBox));
        diagnostics.add("runtimeStatsDiff=expected frame/render/input stats summary cards with DocumentPageRuntimeView source");
        return frame.getTextContent().contains("frame")
                && render.getTextContent().contains("render")
                && input.getTextContent().contains("input")
                && summary.getTextContent().contains("DocumentPageRuntimeView#getUiRuntimeStats")
                && frameBox.getWidth() > 0
                && renderBox.getWidth() > 0
                && inputBox.getWidth() > 0;
    }

    private void diagnoseOpenTiming(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode command = findByRole(scope, "open-command");
        ElementNode deferred = findByRole(scope, "open-deferred");
        ElementNode mounted = findByRole(scope, "open-mounted");
        appendRoleDiagnostics(widget, diagnostics, "openCommand", command);
        appendRoleDiagnostics(widget, diagnostics, "openDeferred", deferred);
        appendRoleDiagnostics(widget, diagnostics, "openMounted", mounted);
        diagnostics.add("openTimingDiff=状态牌可机器诊断；聊天关闭流程与下一帧开屏需 runClient21 人工确认。 ");
    }

    private void diagnoseResize(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode before = findByRole(scope, "resize-before");
        ElementNode after = findByRole(scope, "resize-after");
        ElementNode fill = findByRole(scope, "viewport-fill");
        appendRoleDiagnostics(widget, diagnostics, "resizeBefore", before);
        appendRoleDiagnostics(widget, diagnostics, "resizeAfter", after);
        appendRoleDiagnostics(widget, diagnostics, "viewportFill", fill);
        diagnostics.add("resizeViewportDiff=预览盒与 fill 摘要可机器诊断；真实窗口调整、滚动位置稳定需人工确认。 ");
    }

    private void diagnoseHudContainerInput(HtmlLikeDocumentWidget widget, ElementNode scope,
            List<String> diagnostics) {
        ElementNode display = findByRole(scope, "hud-display");
        ElementNode interactive = findByRole(scope, "hud-interactive");
        ElementNode fallback = findByRole(scope, "container-fallback");
        appendRoleDiagnostics(widget, diagnostics, "hudDisplay", display);
        appendRoleDiagnostics(widget, diagnostics, "hudInteractive", interactive);
        appendRoleDiagnostics(widget, diagnostics, "containerFallback", fallback);
        diagnostics.add("hostInputDiff=HUD 输入链路状态牌可机器诊断；容器态点击、键盘焦点和原生回退需游戏内确认。 ");
    }

    private void diagnoseExceptionPanel(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode trigger = findByRole(scope, "exception-trigger");
        ElementNode panel = findByRole(scope, "exception-panel");
        ElementNode safe = findByRole(scope, "exception-safe");
        ElementNode stack = findByRole(scope, "exception-stack");
        appendRoleDiagnostics(widget, diagnostics, "exceptionTrigger", trigger);
        appendRoleDiagnostics(widget, diagnostics, "exceptionPanel", panel);
        appendRoleDiagnostics(widget, diagnostics, "exceptionSafe", safe);
        appendRoleDiagnostics(widget, diagnostics, "exceptionStack", stack);
        diagnostics.add("exceptionPanelDiff=面板结构可机器诊断；真实故障时客户端不无提示退出需游戏内确认。 ");
    }

    private void appendRoleDiagnostics(HtmlLikeDocumentWidget widget, List<String> diagnostics, String label,
            ElementNode element) {
        if (element == null) {
            diagnostics.add(label + "=missing");
            return;
        }
        DocumentLayoutBox box = resolveBox(widget, element);
        diagnostics.add(label + "Text=" + element.getTextContent());
        diagnostics.add(label + "Box=" + summarizeBox(box));
    }

    private ElementNode findByRole(ElementNode current, String role) {
        if (current == null || role == null) {
            return null;
        }
        if (role.equals(current.getAttribute(UiTestRuntimeHostVisualFactory.ROLE_ATTRIBUTE))) {
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

    private DocumentLayoutBox resolveBox(HtmlLikeDocumentWidget widget, ElementNode element) {
        DocumentLayoutBox box = findLayoutBox(widget.resolveLayoutBoxForTest(), element);
        if (box == null) {
            throw new IllegalStateException("未找到 RuntimeHost 样例布局盒: " + element.getTagName());
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

    private String summarizeBox(DocumentLayoutBox box) {
        return "x=" + box.getLeft() + ",y=" + box.getTop() + ",w=" + box.getWidth()
                + ",h=" + box.getHeight();
    }
}
