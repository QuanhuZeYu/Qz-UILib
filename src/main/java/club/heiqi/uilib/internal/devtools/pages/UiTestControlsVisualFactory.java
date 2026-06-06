package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.control.DocumentCheckboxChangeEvent;
import club.heiqi.uilib.ui.control.DocumentCheckboxChangeHandler;
import club.heiqi.uilib.ui.control.DocumentCheckboxControl;
import club.heiqi.uilib.ui.control.DocumentInputType;
import club.heiqi.uilib.ui.control.DocumentRadioChangeEvent;
import club.heiqi.uilib.ui.control.DocumentRadioChangeHandler;
import club.heiqi.uilib.ui.control.DocumentRadioGroupControl;
import club.heiqi.uilib.ui.control.DocumentSelectChangeEvent;
import club.heiqi.uilib.ui.control.DocumentSelectChangeHandler;
import club.heiqi.uilib.ui.control.DocumentSelectControl;
import club.heiqi.uilib.ui.control.DocumentSliderChangeEvent;
import club.heiqi.uilib.ui.control.DocumentSliderChangeHandler;
import club.heiqi.uilib.ui.control.DocumentSliderControl;
import club.heiqi.uilib.ui.control.DocumentTabChangeEvent;
import club.heiqi.uilib.ui.control.DocumentTabChangeHandler;
import club.heiqi.uilib.ui.control.DocumentTabContentBuilder;
import club.heiqi.uilib.ui.control.DocumentTabControl;
import club.heiqi.uilib.ui.control.DocumentTableControl;
import club.heiqi.uilib.ui.control.DocumentTextAreaChangeEvent;
import club.heiqi.uilib.ui.control.DocumentTextAreaChangeHandler;
import club.heiqi.uilib.ui.control.DocumentTextAreaControl;
import club.heiqi.uilib.ui.control.DocumentTextInputChangeEvent;
import club.heiqi.uilib.ui.control.DocumentTextInputChangeHandler;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.control.DocumentToggleChangeEvent;
import club.heiqi.uilib.ui.control.DocumentToggleChangeHandler;
import club.heiqi.uilib.ui.control.DocumentToggleSwitchControl;
import club.heiqi.uilib.ui.control.UiRadioOrientation;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `/qzuilib test` Controls 分组视觉样例工厂。
 */
final class UiTestControlsVisualFactory {

    static final String ROLE_ATTRIBUTE = "data-ui-control-role";

    /**
     * 判断是否支持指定 Controls 样例。
     *
     * @param caseId 样例编号
     * @return 是否支持
     */
    boolean supports(String caseId) {
        return "VIS-CTRL-001".equals(caseId)
                || "VIS-CTRL-002".equals(caseId)
                || "VIS-CTRL-003".equals(caseId)
                || "VIS-CTRL-004".equals(caseId)
                || "VIS-CTRL-005".equals(caseId)
                || "VIS-CTRL-006".equals(caseId)
                || "VIS-CTRL-007".equals(caseId);
    }

    /**
     * 追加 Controls 样例视觉舞台。
     *
     * @param document 文档实例
     * @param stage 样例舞台
     * @param testCase 样例规格
     */
    void appendCaseDemo(UiDocument document, ElementNode stage, UiTestCaseSpec testCase) {
        String id = testCase.getId();
        if ("VIS-CTRL-001".equals(id)) {
            appendButtonStatesDemo(document, stage);
        } else if ("VIS-CTRL-002".equals(id)) {
            appendInputValueDemo(document, stage);
        } else if ("VIS-CTRL-003".equals(id)) {
            appendTextareaCaretDemo(document, stage);
        } else if ("VIS-CTRL-004".equals(id)) {
            appendChoiceControlsDemo(document, stage);
        } else if ("VIS-CTRL-005".equals(id)) {
            appendSelectTableDemo(document, stage);
        } else if ("VIS-CTRL-006".equals(id)) {
            appendSliderToggleDemo(document, stage);
        } else if ("VIS-CTRL-007".equals(id)) {
            appendTabFocusDisabledDemo(document, stage);
        }
    }

    private void appendButtonStatesDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        TextNode log = appendLogLine(document, stage, "button-log", "log=等待 button action");
        final List<String> events = new ArrayList<String>();
        final int[] clickCount = new int[] { 0 };
        DocumentButtonControl primary = createButton(document, "主按钮 click=0", 0xFF2563EB);
        primary.getElement().setAttribute(ROLE_ATTRIBUTE, "button-primary");
        primary.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                clickCount[0]++;
                events.add("primary-click:" + clickCount[0] + ":"
                        + (event.isKeyboardTriggered() ? "keyboard" : "mouse"));
                primary.setLabel("主按钮 click=" + clickCount[0]);
                updateLog(log, "log=", events);
            }
        });
        DocumentButtonControl focusPreview = createButton(document, "focus/hover 观察", 0xFF059669);
        focusPreview.getElement().setAttribute(ROLE_ATTRIBUTE, "button-focus-preview");
        DocumentButtonControl disabled = createButton(document, "禁用按钮", 0xFF64748B);
        disabled.getElement().setAttribute(ROLE_ATTRIBUTE, "button-disabled");
        disabled.setEnabled(false).setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                events.add("disabled-click-unexpected");
                updateLog(log, "log=", events);
            }
        });
        row.append(primary.getElement()).append(focusPreview.getElement()).append(disabled.getElement());
        stage.append(row);
        appendMutedText(document, stage, "按钮态：默认、focus/hover 观察、disabled；点击主按钮后日志应更新，禁用按钮不触发。 ");
    }

    private void appendInputValueDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        TextNode log = appendLogLine(document, stage, "input-log", "log=等待 input change");
        final List<String> events = new ArrayList<String>();
        DocumentTextInputControl text = createTextInput(document, "input-text", "文本输入");
        text.setChangeHandler(new DocumentTextInputChangeHandler() {
            @Override
            public void onTextChanged(DocumentTextInputChangeEvent event) {
                events.add("text=" + event.getText());
                updateLog(log, "log=", events);
            }
        });
        DocumentTextInputControl password = createTextInput(document, "input-password", "密码输入");
        password.setType(DocumentInputType.PASSWORD).setPasswordMaskCharacter('*');
        password.setChangeHandler(new DocumentTextInputChangeHandler() {
            @Override
            public void onTextChanged(DocumentTextInputChangeEvent event) {
                events.add("password=" + event.getText());
                updateLog(log, "log=", events);
            }
        });
        DocumentTextInputControl number = createTextInput(document, "input-number", "数字输入");
        number.setType(DocumentInputType.NUMBER);
        number.setChangeHandler(new DocumentTextInputChangeHandler() {
            @Override
            public void onTextChanged(DocumentTextInputChangeEvent event) {
                events.add("number=" + event.getText());
                updateLog(log, "log=", events);
            }
        });
        row.append(createLabeledControl(document, "type=text", text.getElement()));
        row.append(createLabeledControl(document, "type=password", password.getElement()));
        row.append(createLabeledControl(document, "type=number", number.getElement()));
        stage.append(row);
        appendMutedText(document, stage, "输入态：文本保留原值，password 显示掩码但 change 日志保留真实值，number 过滤非数字语法字符。 ");
    }

    private void appendTextareaCaretDemo(UiDocument document, ElementNode stage) {
        TextNode log = appendLogLine(document, stage, "textarea-log", "log=等待 textarea change");
        final List<String> events = new ArrayList<String>();
        DocumentTextAreaControl textarea = new DocumentTextAreaControl(document)
                .setText("Alpha\nBeta line\nGamma")
                .setCaretColor(0xFFFFCC00)
                .setTextColors(0xFFEAF1FF, 0xFF94A3B8, 0xFF64748B, 0x7738BDF8);
        textarea.getElement().setAttribute(ROLE_ATTRIBUTE, "textarea-main");
        textarea.getElement().style()
                .setWidth(UiStyleLength.px(280))
                .setHeight(UiStyleLength.px(92));
        textarea.setChangeHandler(new DocumentTextAreaChangeHandler() {
            @Override
            public void onTextChanged(DocumentTextAreaChangeEvent event) {
                events.add("textarea=" + event.getText().replace('\n', '/'));
                updateLog(log, "log=", events);
            }
        });
        stage.append(textarea.getElement());
        appendMutedText(document, stage, "Textarea：多行文本、黄色 caret、蓝色 selection 需截图确认；诊断会执行 Ctrl+A 替换。 ");
    }

    private void appendChoiceControlsDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        TextNode log = appendLogLine(document, stage, "choice-log", "log=等待 checked/change");
        final List<String> events = new ArrayList<String>();
        DocumentCheckboxControl checkbox = new DocumentCheckboxControl(document, "允许合成 change");
        checkbox.getElement().setAttribute(ROLE_ATTRIBUTE, "checkbox-primary");
        checkbox.setChangeHandler(new DocumentCheckboxChangeHandler() {
            @Override
            public void onCheckboxChanged(DocumentCheckboxChangeEvent event) {
                events.add("checkbox=" + event.isChecked());
                updateLog(log, "log=", events);
            }
        });
        DocumentCheckboxControl checked = new DocumentCheckboxControl(document, "已选中").setChecked(true);
        checked.getElement().setAttribute(ROLE_ATTRIBUTE, "checkbox-checked");
        DocumentCheckboxControl mixed = new DocumentCheckboxControl(document, "半选").setIndeterminate(true);
        mixed.getElement().setAttribute(ROLE_ATTRIBUTE, "checkbox-mixed");
        DocumentCheckboxControl disabled = new DocumentCheckboxControl(document, "禁用").setEnabled(false);
        disabled.getElement().setAttribute(ROLE_ATTRIBUTE, "checkbox-disabled");
        DocumentRadioGroupControl radio = new DocumentRadioGroupControl(document, "简单", "困难", "专家")
                .setOrientation(UiRadioOrientation.HORIZONTAL);
        radio.getElement().setAttribute(ROLE_ATTRIBUTE, "radio-group");
        radio.setChangeHandler(new DocumentRadioChangeHandler() {
            @Override
            public void onRadioChanged(DocumentRadioChangeEvent event) {
                events.add("radio=" + event.getSelectedOption() + ":" + event.getSelectedIndex());
                updateLog(log, "log=", events);
            }
        });
        ElementNode stack = createStack(document);
        stack.append(checkbox.getElement()).append(checked.getElement()).append(mixed.getElement())
                .append(disabled.getElement());
        row.append(stack).append(createLabeledControl(document, "radio group", radio.getElement()));
        stage.append(row);
        appendMutedText(document, stage, "选择控件：checked / mixed / disabled 与 radio 单选互斥状态直接展示。 ");
    }

    private void appendSelectTableDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        TextNode log = appendLogLine(document, stage, "select-log", "log=等待 select change");
        final List<String> events = new ArrayList<String>();
        DocumentSelectControl select = new DocumentSelectControl(document, "木材", "石头", "红石")
                .setSelectedIndex(1);
        select.getElement().setAttribute(ROLE_ATTRIBUTE, "select-main");
        select.setChangeHandler(new DocumentSelectChangeHandler() {
            @Override
            public void onSelectionChanged(DocumentSelectChangeEvent event) {
                events.add("select=" + event.getSelectedOption() + ":" + event.getSelectedIndex());
                updateLog(log, "log=", events);
            }
        });
        DocumentTableControl table = new DocumentTableControl(document)
                .setHeader("字段", "当前值")
                .addRow("select value", "石头")
                .addRow("popup", "点击后进入 top-layer")
                .addRow("table", "控件表格对齐");
        table.getElement().setAttribute(ROLE_ATTRIBUTE, "select-table");
        row.append(createLabeledControl(document, "select", select.getElement()))
                .append(createLabeledControl(document, "table", table.getElement()));
        stage.append(row);
        appendMutedText(document, stage, "Select/Table：select 当前值与表格状态联动；下拉 top-layer 位置和遮挡需人工截图确认。 ");
    }

    private void appendSliderToggleDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        TextNode log = appendLogLine(document, stage, "slider-toggle-log", "log=等待 slider/toggle change");
        final List<String> events = new ArrayList<String>();
        DocumentSliderControl slider = new DocumentSliderControl(document)
                .setRange(0.0D, 100.0D)
                .setStep(10.0D)
                .setValue(40.0D)
                .setTrackSize(180, 8)
                .setThumbSize(18);
        slider.getElement().setAttribute(ROLE_ATTRIBUTE, "slider-main");
        slider.setChangeHandler(new DocumentSliderChangeHandler() {
            @Override
            public void onSliderChanged(DocumentSliderChangeEvent event) {
                events.add("slider=" + formatNumber(event.getValue()));
                updateLog(log, "log=", events);
            }
        });
        DocumentToggleSwitchControl toggle = new DocumentToggleSwitchControl(document);
        toggle.getElement().setAttribute(ROLE_ATTRIBUTE, "toggle-main");
        toggle.setChangeHandler(new DocumentToggleChangeHandler() {
            @Override
            public void onToggleChanged(DocumentToggleChangeEvent event) {
                events.add("toggle=" + event.isToggled());
                updateLog(log, "log=", events);
            }
        });
        row.append(createLabeledControl(document, "slider value=40", slider.getElement()));
        row.append(createLabeledControl(document, "toggle off", toggle.getElement()));
        stage.append(row);
        appendMutedText(document, stage, "Slider/Toggle：键盘右箭头推进 slider，Enter 切换 switch，aria-valuenow/aria-checked 同步。 ");
    }

    private void appendTabFocusDisabledDemo(UiDocument document, ElementNode stage) {
        TextNode log = appendLogLine(document, stage, "tab-log", "log=等待 tab change");
        final List<String> events = new ArrayList<String>();
        DocumentTabControl tabs = new DocumentTabControl(document);
        tabs.getElement().setAttribute(ROLE_ATTRIBUTE, "tabs-main");
        tabs.setChangeHandler(new DocumentTabChangeHandler() {
            @Override
            public void onTabChanged(DocumentTabChangeEvent event) {
                events.add("tab=" + event.getActiveLabel() + ":" + event.getActiveIndex());
                updateLog(log, "log=", events);
            }
        });
        tabs.addTab("概览", createPanelBuilder("概览面板：控件默认态"));
        tabs.addTab("事件", createPanelBuilder("事件日志面板：change/focus 状态"));
        tabs.addTab("禁用态", createPanelBuilder("禁用态面板：disabled 控件不可交互"));
        tabs.setActiveIndex(0);
        stage.append(tabs.getElement());
        ElementNode disabledRow = createRow(document);
        DocumentTextInputControl disabledInput = createTextInput(document, "tab-disabled-input", "禁用输入")
                .setText("Locked");
        disabledInput.setEnabled(false);
        DocumentButtonControl disabledButton = createButton(document, "禁用按钮", 0xFF64748B).setEnabled(false);
        disabledButton.getElement().setAttribute(ROLE_ATTRIBUTE, "tab-disabled-button");
        disabledRow.append(disabledInput.getElement()).append(disabledButton.getElement());
        stage.append(disabledRow);
        appendMutedText(document, stage, "Tab/Focus/Disabled：点击事件标签切换面板；禁用 input/button 保留视觉但不可聚焦。 ");
    }

    private DocumentTabContentBuilder createPanelBuilder(final String text) {
        return new DocumentTabContentBuilder() {
            @Override
            public void build(ElementNode panel, UiDocument document) {
                panel.append(createPanel(document, text, 0xFF0F766E));
            }
        };
    }

    private DocumentButtonControl createButton(UiDocument document, String label, int color) {
        DocumentButtonControl button = new DocumentButtonControl(document, label);
        button.setBackgroundColors(color, color, 0xFF334155)
                .setFocusBorderColor(0xFFFFF7AD)
                .setTextColors(0xFFFFFFFF, 0xFF94A3B8);
        button.getElement().style()
                .setMinWidth(UiStyleLength.px(126))
                .setPadding(UiStyleLength.px(9));
        return button;
    }

    private DocumentTextInputControl createTextInput(UiDocument document, String role, String placeholder) {
        DocumentTextInputControl input = new DocumentTextInputControl(document).setPlaceholder(placeholder);
        input.getElement().setAttribute(ROLE_ATTRIBUTE, role);
        input.getElement().style()
                .setWidth(UiStyleLength.px(142))
                .setHeight(UiStyleLength.px(30));
        return input;
    }

    private ElementNode createLabeledControl(UiDocument document, String label, ElementNode control) {
        ElementNode stack = createStack(document);
        ElementNode title = document.div();
        title.style().setTextColor(0xFFBFDBFE).setFontWeight(UiFontWeight.BOLD);
        title.appendText(label);
        stack.append(title).append(control);
        return stack;
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
                .setRowGap(UiStyleLength.px(7));
        return stack;
    }

    private ElementNode createPanel(UiDocument document, String label, int color) {
        ElementNode panel = document.div();
        panel.style()
                .setPadding(UiStyleLength.px(8))
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

    private String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0000001D) {
            return String.format(Locale.ROOT, "%.0f", Double.valueOf(value));
        }
        return String.format(Locale.ROOT, "%.2f", Double.valueOf(value));
    }
}
