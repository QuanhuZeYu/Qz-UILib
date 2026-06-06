package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.List;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;

/**
 * `/qzuilib test` Controls 分组自动断言与人工诊断。
 */
final class UiTestControlsAssertionRunner {

    /**
     * 判断指定样例是否具备自动断言。
     *
     * @param caseId 样例编号
     * @return 是否自动断言样例
     */
    boolean isAutomatic(String caseId) {
        return "VIS-CTRL-001".equals(caseId)
                || "VIS-CTRL-002".equals(caseId)
                || "VIS-CTRL-004".equals(caseId)
                || "VIS-CTRL-006".equals(caseId)
                || "VIS-CTRL-007".equals(caseId);
    }

    /**
     * 判断指定样例是否为 Controls 人工诊断样例。
     *
     * @param caseId 样例编号
     * @return 是否人工诊断样例
     */
    boolean isManual(String caseId) {
        return "VIS-CTRL-003".equals(caseId) || "VIS-CTRL-005".equals(caseId);
    }

    /**
     * 执行 Controls 自动断言。
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
        if ("VIS-CTRL-001".equals(id)) {
            return assertButtonStates(widget, scope, diagnostics);
        }
        if ("VIS-CTRL-002".equals(id)) {
            return assertInputValues(widget, scope, diagnostics);
        }
        if ("VIS-CTRL-004".equals(id)) {
            return assertChoiceControls(widget, scope, diagnostics);
        }
        if ("VIS-CTRL-006".equals(id)) {
            return assertSliderToggle(widget, scope, diagnostics);
        }
        if ("VIS-CTRL-007".equals(id)) {
            return assertTabFocusDisabled(widget, scope, diagnostics);
        }
        diagnostics.add("未知 Controls 自动样例：" + id);
        return false;
    }

    /**
     * 输出 Controls 人工诊断信息。
     *
     * @param widget 文档组件
     * @param scope 样例舞台
     * @param testCase 样例规格
     * @param diagnostics 诊断摘要
     */
    void diagnoseManual(HtmlLikeDocumentWidget widget, ElementNode scope, UiTestCaseSpec testCase,
            List<String> diagnostics) {
        ensureWidgetBounds(widget);
        String id = testCase.getId();
        if ("VIS-CTRL-003".equals(id)) {
            diagnoseTextareaCaret(widget, scope, diagnostics);
            return;
        }
        if ("VIS-CTRL-005".equals(id)) {
            diagnoseSelectTable(widget, scope, diagnostics);
            return;
        }
        diagnostics.add("未知 Controls 人工样例：" + id);
    }

    private boolean assertButtonStates(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode primary = findByRole(scope, "button-primary");
        ElementNode disabled = findByRole(scope, "button-disabled");
        ElementNode logNode = findByRole(scope, "button-log");
        if (primary == null || disabled == null || logNode == null) {
            diagnostics.add("button 节点缺失");
            return false;
        }
        clickElement(widget, primary, 101L);
        clickElement(widget, disabled, 103L);
        String log = logNode.getTextContent();
        diagnostics.add("buttonLog=" + log);
        diagnostics.add("buttonDisabled=" + disabled.isDisabled() + ", buttonDisabledAttr="
                + disabled.getAttribute("disabled"));
        diagnostics.add("buttonDiff=expected primary-click once and disabled-click-unexpected absent");
        return log.contains("primary-click:1:mouse")
                && !log.contains("disabled-click-unexpected")
                && disabled.isDisabled();
    }

    private boolean assertInputValues(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode text = findByRole(scope, "input-text");
        ElementNode password = findByRole(scope, "input-password");
        ElementNode number = findByRole(scope, "input-number");
        ElementNode logNode = findByRole(scope, "input-log");
        if (text == null || password == null || number == null || logNode == null) {
            diagnostics.add("input 节点缺失");
            return false;
        }
        text.focus();
        widget.onTextInput(new UiTextInputEvent("Alpha", 111L));
        password.focus();
        widget.onTextInput(new UiTextInputEvent("Secret", 112L));
        number.focus();
        widget.onTextInput(new UiTextInputEvent("-12.5abcE3", 113L));
        String log = logNode.getTextContent();
        diagnostics.add("inputLog=" + log);
        diagnostics.add("textValue=" + text.getAttribute("value"));
        diagnostics.add("passwordValue=" + password.getAttribute("value"));
        diagnostics.add("numberValue=" + number.getAttribute("value"));
        diagnostics.add("inputDiff=expected text=Alpha, password log keeps Secret but value masked, number filters abc");
        return "Alpha".equals(text.getAttribute("value"))
                && "******".equals(password.getAttribute("value"))
                && "-12.5E3".equals(number.getAttribute("value"))
                && log.contains("text=Alpha")
                && log.contains("password=Secret")
                && log.contains("number=-12.5E3");
    }

    private void diagnoseTextareaCaret(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode textarea = findByRole(scope, "textarea-main");
        ElementNode logNode = findByRole(scope, "textarea-log");
        if (textarea == null || logNode == null) {
            diagnostics.add("textarea 节点缺失，保持人工待确认。 ");
            return;
        }
        textarea.focus();
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_A, 0, 0, UiKeyEvent.Action.PRESSED, true, false, false,
                false, 121L));
        widget.onTextInput(new UiTextInputEvent("Done\nNext", 122L));
        DocumentLayoutBox textareaBox = resolveBox(widget, textarea);
        diagnostics.add("textareaLog=" + logNode.getTextContent());
        diagnostics.add("textareaValue=" + textarea.getAttribute("value").replace('\n', '/'));
        diagnostics.add("textareaBox=w=" + textareaBox.getWidth() + ",h=" + textareaBox.getHeight()
                + ",scrollTop=" + widget.getScrollTop(textarea) + ",maxScrollTop=" + widget.getMaxScrollTop(textarea));
        diagnostics.add("textareaCaretDiff=selection/value 可机器诊断；黄色 caret、蓝色 selection 与滚动后的视觉位置需截图确认。 ");
    }

    private boolean assertChoiceControls(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode checkbox = findByRole(scope, "checkbox-primary");
        ElementNode mixed = findByRole(scope, "checkbox-mixed");
        ElementNode disabled = findByRole(scope, "checkbox-disabled");
        ElementNode radioGroup = findByRole(scope, "radio-group");
        ElementNode logNode = findByRole(scope, "choice-log");
        List<ElementNode> radioOptions = new ArrayList<ElementNode>();
        collectByAttribute(radioGroup, "role", "radio", radioOptions);
        if (checkbox == null || mixed == null || disabled == null || radioOptions.size() < 3 || logNode == null) {
            diagnostics.add("choice 节点缺失");
            return false;
        }
        checkbox.focus();
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 131L));
        clickElement(widget, radioOptions.get(2), 133L);
        String log = logNode.getTextContent();
        diagnostics.add("choiceLog=" + log);
        diagnostics.add("checkboxAria=" + checkbox.getAttribute("aria-checked") + ", mixed="
                + mixed.getAttribute("aria-checked") + ", disabledAria=" + disabled.getAttribute("aria-disabled"));
        diagnostics.add("radioSelected=" + radioOptions.get(2).getAttribute("aria-checked"));
        diagnostics.add("choiceDiff=expected checkbox=true, mixed aria=mixed, disabled aria=true, radio expert selected");
        return log.contains("checkbox=true")
                && log.contains("radio=专家:2")
                && "true".equals(checkbox.getAttribute("aria-checked"))
                && "mixed".equals(mixed.getAttribute("aria-checked"))
                && "true".equals(disabled.getAttribute("aria-disabled"))
                && "true".equals(radioOptions.get(2).getAttribute("aria-checked"));
    }

    private void diagnoseSelectTable(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode select = findByRole(scope, "select-main");
        ElementNode table = findByRole(scope, "select-table");
        ElementNode logNode = findByRole(scope, "select-log");
        if (select == null || table == null || logNode == null) {
            diagnostics.add("select/table 节点缺失，保持人工待确认。 ");
            return;
        }
        clickElement(widget, select, 141L);
        ElementNode popup = findByAttribute(select, "role", "listbox");
        boolean popupTopLayer = popup != null && widget.getDocument().__isTopLayerElement(popup);
        DocumentLayoutBox selectBox = resolveBox(widget, select);
        DocumentLayoutBox tableBox = resolveBox(widget, table);
        diagnostics.add("selectValue=" + select.getAttribute("value") + ", expanded="
                + select.getAttribute("aria-expanded"));
        diagnostics.add("selectPopupTopLayer=" + popupTopLayer);
        diagnostics.add("selectLog=" + logNode.getTextContent());
        diagnostics.add("selectBox=w=" + selectBox.getWidth() + ",h=" + selectBox.getHeight()
                + "; tableBox=w=" + tableBox.getWidth() + ",h=" + tableBox.getHeight());
        diagnostics.add("selectTableDiff=select value 与 table 布局可机器诊断；popup top-layer 位置、遮挡与选项命中需截图确认。 ");
    }

    private boolean assertSliderToggle(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode slider = findByRole(scope, "slider-main");
        ElementNode toggle = findByRole(scope, "toggle-main");
        ElementNode logNode = findByRole(scope, "slider-toggle-log");
        if (slider == null || toggle == null || logNode == null) {
            diagnostics.add("slider/toggle 节点缺失");
            return false;
        }
        slider.focus();
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RIGHT, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 151L));
        toggle.focus();
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 152L));
        String log = logNode.getTextContent();
        diagnostics.add("sliderToggleLog=" + log);
        diagnostics.add("sliderValue=" + slider.getAttribute("aria-valuenow") + ", toggle="
                + toggle.getAttribute("aria-checked"));
        diagnostics.add("sliderToggleDiff=expected slider 40->50 by keyboard and toggle false->true");
        return "50.0".equals(slider.getAttribute("aria-valuenow"))
                && "true".equals(toggle.getAttribute("aria-checked"))
                && log.contains("slider=50")
                && log.contains("toggle=true");
    }

    private boolean assertTabFocusDisabled(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode tabs = findByRole(scope, "tabs-main");
        ElementNode disabledInput = findByRole(scope, "tab-disabled-input");
        ElementNode disabledButton = findByRole(scope, "tab-disabled-button");
        ElementNode logNode = findByRole(scope, "tab-log");
        List<ElementNode> tabOptions = new ArrayList<ElementNode>();
        collectByAttribute(tabs, "role", "tab", tabOptions);
        if (tabs == null || tabOptions.size() < 2 || disabledInput == null || disabledButton == null
                || logNode == null) {
            diagnostics.add("tab/focus/disabled 节点缺失");
            return false;
        }
        clickElement(widget, tabOptions.get(1), 161L);
        boolean disabledInputFocused = attemptProgrammaticFocus(widget, disabledInput);
        boolean disabledButtonFocused = attemptProgrammaticFocus(widget, disabledButton);
        String log = logNode.getTextContent();
        diagnostics.add("tabLog=" + log);
        diagnostics.add("tabSelected=" + tabOptions.get(1).getAttribute("aria-selected"));
        diagnostics.add("disabledInputFocus=" + disabledInputFocused + ", disabledButtonFocus="
                + disabledButtonFocused + ", inputDisabled=" + disabledInput.isDisabled()
                + ", buttonDisabled=" + disabledButton.isDisabled());
        diagnostics.add("tabFocusDisabledDiff=expected tab event selects index 1 and disabled controls reject focus");
        return log.contains("tab=事件:1")
                && "true".equals(tabOptions.get(1).getAttribute("aria-selected"))
                && disabledInput.isDisabled()
                && disabledButton.isDisabled()
                && !disabledInputFocused
                && !disabledButtonFocused;
    }

    private void clickElement(HtmlLikeDocumentWidget widget, ElementNode element, long timeNanos) {
        int[] center = resolveElementCenter(widget, element);
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, center[0], center[1], 0, 0, 0, 0,
                timeNanos));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, center[0], center[1], 0, 0, 0, 0,
                timeNanos + 1L));
    }

    private boolean attemptProgrammaticFocus(HtmlLikeDocumentWidget widget, ElementNode element) {
        return element.focus() && widget.getFocusedElement() == element;
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
            throw new IllegalStateException("未找到 Controls 样例布局盒: " + element.getTagName());
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
        if (role.equals(current.getAttribute(UiTestControlsVisualFactory.ROLE_ATTRIBUTE))) {
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

    private ElementNode findByAttribute(ElementNode current, String name, String value) {
        if (current == null || name == null || value == null) {
            return null;
        }
        if (value.equals(current.getAttribute(name))) {
            return current;
        }
        for (DocumentNode child : current.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findByAttribute((ElementNode) child, name, value);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void collectByAttribute(ElementNode current, String name, String value, List<ElementNode> result) {
        if (current == null || name == null || value == null) {
            return;
        }
        if (value.equals(current.getAttribute(name))) {
            result.add(current);
        }
        for (DocumentNode child : current.getChildren()) {
            if (child instanceof ElementNode) {
                collectByAttribute((ElementNode) child, name, value, result);
            }
        }
    }

    private void ensureWidgetBounds(HtmlLikeDocumentWidget widget) {
        if (widget.getWidth() <= 0 || widget.getHeight() <= 0) {
            widget.applyLayoutBounds(0, 0, 760, 520);
        }
    }
}
