package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.List;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputHandler;
import club.heiqi.uilib.ui.dom.DocumentElementWheelEvent;
import club.heiqi.uilib.ui.dom.DocumentElementWheelHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `/qzuilib test` Input 分组视觉样例工厂。
 */
final class UiTestInputVisualFactory {

    static final String ROLE_ATTRIBUTE = "data-ui-input-role";

    /**
     * 判断是否支持指定样例。
     *
     * @param caseId 样例编号
     * @return 是否为 Input 样例
     */
    boolean supports(String caseId) {
        return "VIS-INPUT-001".equals(caseId)
                || "VIS-INPUT-002".equals(caseId)
                || "VIS-INPUT-003".equals(caseId)
                || "VIS-INPUT-004".equals(caseId)
                || "VIS-INPUT-005".equals(caseId);
    }

    /**
     * 追加 Input 样例视觉舞台。
     *
     * @param document 文档实例
     * @param stage 样例舞台
     * @param testCase 样例规格
     */
    void appendCaseDemo(UiDocument document, ElementNode stage, UiTestCaseSpec testCase) {
        String id = testCase.getId();
        if ("VIS-INPUT-001".equals(id)) {
            appendPropagationDemo(document, stage);
        } else if ("VIS-INPUT-002".equals(id)) {
            appendPreventDefaultDemo(document, stage);
        } else if ("VIS-INPUT-003".equals(id)) {
            appendWheelDemo(document, stage);
        } else if ("VIS-INPUT-004".equals(id)) {
            appendFocusVisibleDemo(document, stage);
        } else if ("VIS-INPUT-005".equals(id)) {
            appendKeyboardTextInputDemo(document, stage);
        }
    }

    private void appendPropagationDemo(UiDocument document, ElementNode stage) {
        ElementNode root = createPanel(document, "root capture + bubble", 0xFF1E3A8A);
        root.setAttribute(ROLE_ATTRIBUTE, "propagation-root");
        ElementNode target = createPanel(document, "target click", 0xFF059669);
        target.setAttribute(ROLE_ATTRIBUTE, "propagation-target");
        TextNode log = appendLogLine(document, stage, "propagation-log", "log=等待 click");
        final List<String> events = new ArrayList<String>();
        root.setCaptureClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                events.add("root-capture:" + event.getEventPhase());
                updateLog(log, "log=", events);
                return false;
            }
        });
        target.setCaptureClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                events.add("target-capture:" + event.getEventPhase());
                updateLog(log, "log=", events);
                return false;
            }
        });
        target.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                events.add("target:" + event.getEventPhase());
                updateLog(log, "log=", events);
                return false;
            }
        });
        root.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                events.add("root-bubble:" + event.getEventPhase());
                updateLog(log, "log=", events);
                return false;
            }
        });
        root.append(target);
        stage.append(root);
        appendMutedText(document, stage, "点击 target 后，日志应按 capture -> target -> bubble 更新。");
    }

    private void appendPreventDefaultDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        ElementNode defaultButton = createButton(document, "Enter 默认 click", 0xFF2563EB);
        defaultButton.setAttribute(ROLE_ATTRIBUTE, "prevent-default-normal");
        ElementNode preventedButton = createButton(document, "Enter preventDefault", 0xFF7C2D12);
        preventedButton.setAttribute(ROLE_ATTRIBUTE, "prevent-default-blocked");
        TextNode log = appendLogLine(document, stage, "prevent-default-log", "log=等待 Enter");
        final List<String> events = new ArrayList<String>();
        defaultButton.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                events.add("default-click");
                updateLog(log, "log=", events);
                return false;
            }
        });
        preventedButton.setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                if (event.getKeyCode() == Keyboard.KEY_RETURN && event.getAction().name().equals("PRESSED")) {
                    event.preventDefault();
                    events.add("prevent-default:" + event.getEventPhase());
                    updateLog(log, "log=", events);
                }
                return false;
            }
        });
        preventedButton.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                events.add("blocked-click-unexpected");
                updateLog(log, "log=", events);
                return false;
            }
        });
        row.append(defaultButton).append(preventedButton);
        stage.append(row);
        appendMutedText(document, stage, "Enter 默认会合成 click；preventDefault 后不应触发默认 click。");
    }

    private void appendWheelDemo(UiDocument document, ElementNode stage) {
        ElementNode scroller = document.div();
        scroller.setAttribute(ROLE_ATTRIBUTE, "wheel-scroller");
        scroller.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(78))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF020617)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF38BDF8)
                .setBorderRadius(UiStyleLength.px(10));
        ElementNode content = document.div();
        content.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8))
                .setHeight(UiStyleLength.px(150));
        ElementNode target = createPanel(document, "wheel target", 0xFF7C3AED);
        target.setAttribute(ROLE_ATTRIBUTE, "wheel-target");
        target.style().setHeight(UiStyleLength.px(44));
        content.append(target);
        content.append(createPanel(document, "scroll filler", 0xFF334155));
        content.append(createPanel(document, "scroll bottom", 0xFF334155));
        scroller.append(content);
        TextNode log = appendLogLine(document, stage, "wheel-log", "log=等待 wheel；scrollTop=0");
        final List<String> events = new ArrayList<String>();
        scroller.setCaptureWheelHandler(new DocumentElementWheelHandler() {
            @Override
            public boolean onWheel(DocumentElementWheelEvent event) {
                events.add("root-capture:" + event.getEventPhase());
                updateLog(log, "log=", events);
                return false;
            }
        });
        target.setWheelHandler(new DocumentElementWheelHandler() {
            @Override
            public boolean onWheel(DocumentElementWheelEvent event) {
                events.add("target:" + event.getEventPhase() + ":deltaY=" + event.getDeltaY());
                updateLog(log, "log=", events);
                return false;
            }
        });
        scroller.setWheelHandler(new DocumentElementWheelHandler() {
            @Override
            public boolean onWheel(DocumentElementWheelEvent event) {
                events.add("root-bubble:" + event.getEventPhase());
                updateLog(log, "log=", events);
                return false;
            }
        });
        stage.append(scroller);
        appendMutedText(document, stage, "未 preventDefault 时，wheel 日志之后应发生默认滚动。");
    }

    private void appendFocusVisibleDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        ElementNode mouseFocus = createButton(document, "程序化焦点", 0xFF475569);
        mouseFocus.setAttribute(ROLE_ATTRIBUTE, "focus-programmatic");
        ElementNode keyboardFocus = createButton(document, "键盘焦点", 0xFF059669);
        keyboardFocus.setAttribute(ROLE_ATTRIBUTE, "focus-keyboard");
        TextNode log = appendLogLine(document, stage, "focus-visible-log", "log=等待 focus");
        final List<String> events = new ArrayList<String>();
        DocumentElementFocusHandler focusHandler = new DocumentElementFocusHandler() {
            @Override
            public void onFocusChanged(DocumentElementFocusEvent event) {
                if (event.isFocused()) {
                    events.add(roleOf(event.getTarget()) + ":focusVisible=" + event.isFocusVisible());
                    updateLog(log, "log=", events);
                }
            }
        };
        mouseFocus.setFocusHandler(focusHandler);
        keyboardFocus.setFocusHandler(focusHandler);
        row.append(mouseFocus).append(keyboardFocus);
        stage.append(row);
        appendMutedText(document, stage, "程序化/鼠标焦点不显示键盘焦点框；Tab 遍历应显示 focus-visible。 ");
    }

    private void appendKeyboardTextInputDemo(UiDocument document, ElementNode stage) {
        ElementNode root = createPanel(document, "keyboard root", 0xFF1E3A8A);
        root.setAttribute(ROLE_ATTRIBUTE, "keyboard-root");
        ElementNode target = createPanel(document, "focus target", 0xFF0F766E);
        target.setAttribute(ROLE_ATTRIBUTE, "keyboard-target");
        target.setFocusable(true);
        TextNode log = appendLogLine(document, stage, "keyboard-log", "log=等待 key/textInput");
        final List<String> events = new ArrayList<String>();
        root.setCaptureKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                events.add("key-root-capture:" + event.getEventPhase());
                updateLog(log, "log=", events);
                return false;
            }
        });
        target.setCaptureKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                events.add("key-target-capture:" + event.getEventPhase());
                updateLog(log, "log=", events);
                return false;
            }
        });
        target.setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                events.add("key-target:" + event.getEventPhase() + ":code=" + event.getKeyCode());
                updateLog(log, "log=", events);
                return false;
            }
        });
        root.setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                events.add("key-root-bubble:" + event.getEventPhase());
                updateLog(log, "log=", events);
                return false;
            }
        });
        root.setCaptureTextInputHandler(new DocumentElementTextInputHandler() {
            @Override
            public boolean onTextInput(DocumentElementTextInputEvent event) {
                events.add("text-root-capture:" + event.getEventPhase());
                updateLog(log, "log=", events);
                return false;
            }
        });
        target.setCaptureTextInputHandler(new DocumentElementTextInputHandler() {
            @Override
            public boolean onTextInput(DocumentElementTextInputEvent event) {
                events.add("text-target-capture:" + event.getEventPhase());
                updateLog(log, "log=", events);
                return false;
            }
        });
        target.setTextInputHandler(new DocumentElementTextInputHandler() {
            @Override
            public boolean onTextInput(DocumentElementTextInputEvent event) {
                events.add("text-target:" + event.getEventPhase() + ":" + event.getText());
                updateLog(log, "log=", events);
                return false;
            }
        });
        root.setTextInputHandler(new DocumentElementTextInputHandler() {
            @Override
            public boolean onTextInput(DocumentElementTextInputEvent event) {
                events.add("text-root-bubble:" + event.getEventPhase());
                updateLog(log, "log=", events);
                return false;
            }
        });
        root.append(target);
        stage.append(root);
        appendMutedText(document, stage, "聚焦 target 后，key 与 textInput 均应沿焦点链路传播。 ");
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

    private ElementNode createPanel(UiDocument document, String label, int color) {
        ElementNode panel = document.div();
        panel.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setMinWidth(UiStyleLength.px(130))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(color)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF93C5FD)
                .setBorderRadius(UiStyleLength.px(8))
                .setTextColor(0xFFFFFFFF)
                .setFontWeight(UiFontWeight.BOLD);
        panel.appendText(label);
        return panel;
    }

    private ElementNode createButton(UiDocument document, String label, int color) {
        ElementNode button = document.button();
        button.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setMinWidth(UiStyleLength.px(132))
                .setPadding(UiStyleLength.px(9))
                .setBackgroundColor(color)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFFEAF1FF)
                .setBorderRadius(UiStyleLength.px(8))
                .setTextColor(0xFFFFFFFF)
                .setFontWeight(UiFontWeight.BOLD);
        button.appendText(label);
        return button;
    }

    private TextNode appendLogLine(UiDocument document, ElementNode parent, String role, String text) {
        ElementNode line = document.div();
        line.setAttribute(ROLE_ATTRIBUTE, role);
        line.style()
                .setPadding(UiStyleLength.px(7))
                .setBackgroundColor(0xFF0F172A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF334155)
                .setBorderRadius(UiStyleLength.px(8))
                .setTextColor(0xFFEAF1FF);
        TextNode textNode = line.appendText(text);
        parent.append(line);
        return textNode;
    }

    private void appendMutedText(UiDocument document, ElementNode parent, String text) {
        ElementNode line = document.div();
        line.style().setTextColor(0xFFC9D8F8);
        line.appendText(text);
        parent.append(line);
    }

    private void updateLog(TextNode log, String prefix, List<String> events) {
        log.setText(prefix + join(events));
    }

    private String join(List<String> events) {
        if (events.isEmpty()) {
            return "等待事件";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < events.size(); index++) {
            if (index > 0) {
                builder.append(" > ");
            }
            builder.append(events.get(index));
        }
        return builder.toString();
    }

    private String roleOf(ElementNode element) {
        return element == null ? "null" : element.getAttribute(ROLE_ATTRIBUTE);
    }
}
