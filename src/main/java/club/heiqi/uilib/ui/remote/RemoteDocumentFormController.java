package club.heiqi.uilib.ui.remote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.control.DocumentCheckboxControl;
import club.heiqi.uilib.ui.control.DocumentRadioGroupControl;
import club.heiqi.uilib.ui.control.DocumentSelectControl;
import club.heiqi.uilib.ui.control.DocumentTextAreaControl;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.event.UiKeyEvent;

/**
 * 远程页面表单状态与提交收集器。
 */
final class RemoteDocumentFormController {

    private RemoteDocumentFormController() {}

    /**
     * 表单运行态，负责按 HTML successful controls 语义收集字段。
     */
    static final class FormState {

        private final String sessionId;
        private final String surfaceId;
        private final long contentRevision;
        private final String pageId;
        private final String action;
        private final String formId;
        private final RemoteFormSubmitSink submitSink;
        private final List<FieldBinding> fields = new ArrayList<FieldBinding>();

        FormState(String sessionId, String pageId, String action, String formId) {
            this(sessionId, RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID, 1L, pageId, action, formId,
                    defaultSubmitSink());
        }

        FormState(String sessionId, String pageId, String action, String formId,
                RemoteFormSubmitSink submitSink) {
            this(sessionId, RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID, 1L, pageId, action, formId, submitSink);
        }

        FormState(String sessionId, String surfaceId, long contentRevision, String pageId, String action,
                String formId, RemoteFormSubmitSink submitSink) {
            this.sessionId = safe(sessionId);
            this.surfaceId = safe(surfaceId);
            this.contentRevision = contentRevision <= 0L ? 1L : contentRevision;
            this.pageId = safe(pageId);
            this.action = safe(action);
            this.formId = safe(formId);
            this.submitSink = submitSink == null ? defaultSubmitSink() : submitSink;
        }

        String getAction() {
            return action;
        }

        String getFormId() {
            return formId;
        }

        void addHidden(String name, String value, boolean disabled) {
            addField(new StaticFieldBinding(name, disabled, value));
        }

        void addText(String name, boolean disabled, DocumentTextInputControl control) {
            addField(new TextInputFieldBinding(name, disabled, control));
            control.setKeyHandler(new SubmitOnEnterHandler(this, null));
        }

        void addCheckbox(String name, String value, boolean disabled, DocumentCheckboxControl control) {
            addField(new CheckboxFieldBinding(name, disabled, value, control));
        }

        void addRadio(String name, String value, boolean disabled, DocumentCheckboxControl control) {
            addField(new CheckboxFieldBinding(name, disabled, value, control));
        }

        void addTextarea(String name, boolean disabled, DocumentTextAreaControl control) {
            addField(new TextAreaFieldBinding(name, disabled, control));
        }

        void addSelect(String name, boolean disabled, DocumentSelectControl control, List<String> values) {
            addField(new SelectFieldBinding(name, disabled, control, values));
        }

        void addRadioGroup(String name, boolean disabled, DocumentRadioGroupControl control, List<String> values) {
            addField(new RadioGroupFieldBinding(name, disabled, control, values));
        }

        void installSubmitButton(DocumentButtonControl button, Submitter submitter) {
            button.setActionHandler(new SubmitButtonHandler(this, submitter));
        }

        Map<String, List<String>> collectValues(Submitter submitter) {
            Map<String, List<String>> values = new LinkedHashMap<String, List<String>>();
            for (FieldBinding field : fields) {
                if (!field.isSuccessful()) {
                    continue;
                }
                for (String value : field.resolveValues()) {
                    addValue(values, field.getName(), value);
                }
            }
            if (submitter != null && submitter.isSuccessful()) {
                addValue(values, submitter.getName(), submitter.getValue());
            }
            return values;
        }

        void submit(Submitter submitter) {
            submitSink.submit(sessionId, surfaceId, contentRevision, pageId, action, formId,
                    collectValues(submitter));
        }

        private void addField(FieldBinding field) {
            if (field != null && field.hasName()) {
                fields.add(field);
            }
        }

        private static RemoteFormSubmitSink defaultSubmitSink() {
            return new RemoteFormSubmitSink() {
                @Override
                public void submit(String sessionId, String pageId, String action, String formId,
                        Map<String, List<String>> values) {
                    RemoteDocumentPages.SubmitPayload payload = new RemoteDocumentPages.SubmitPayload();
                    payload.sessionId = sessionId;
                    payload.surfaceId = RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID;
                    payload.contentRevision = 1L;
                    payload.pageId = pageId;
                    payload.action = action;
                    payload.formId = formId;
                    payload.values = values;
                    RemoteDocumentPages.submitFromClient(payload);
                }

                @Override
                public void submit(String sessionId, String surfaceId, long contentRevision, String pageId,
                        String action, String formId, Map<String, List<String>> values) {
                    RemoteDocumentPages.SubmitPayload payload = new RemoteDocumentPages.SubmitPayload();
                    payload.sessionId = sessionId;
                    payload.surfaceId = surfaceId;
                    payload.contentRevision = contentRevision;
                    payload.pageId = pageId;
                    payload.action = action;
                    payload.formId = formId;
                    payload.values = values;
                    RemoteDocumentPages.submitFromClient(payload);
                }
            };
        }
    }

    /**
     * 提交按钮自身的 name/value。
     */
    static final class Submitter {

        private final String name;
        private final String value;
        private final boolean disabled;

        Submitter(String name, String value, boolean disabled) {
            this.name = safe(name);
            this.value = value == null ? "" : value;
            this.disabled = disabled;
        }

        boolean isSuccessful() {
            return !disabled && !name.isEmpty();
        }

        String getName() {
            return name;
        }

        String getValue() {
            return value;
        }
    }

    private interface FieldBinding {

        String getName();

        boolean isDisabled();

        List<String> resolveValues();

        default boolean hasName() {
            return !getName().isEmpty();
        }

        default boolean isSuccessful() {
            return hasName() && !isDisabled();
        }
    }

    private abstract static class AbstractFieldBinding implements FieldBinding {

        private final String name;
        private final boolean disabled;

        AbstractFieldBinding(String name, boolean disabled) {
            this.name = safe(name);
            this.disabled = disabled;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isDisabled() {
            return disabled;
        }
    }

    private static final class StaticFieldBinding extends AbstractFieldBinding {

        private final String value;

        StaticFieldBinding(String name, boolean disabled, String value) {
            super(name, disabled);
            this.value = value == null ? "" : value;
        }

        @Override
        public List<String> resolveValues() {
            return Collections.singletonList(value);
        }
    }

    private static final class TextInputFieldBinding extends AbstractFieldBinding {

        private final DocumentTextInputControl control;

        TextInputFieldBinding(String name, boolean disabled, DocumentTextInputControl control) {
            super(name, disabled);
            this.control = control;
        }

        @Override
        public List<String> resolveValues() {
            return Collections.singletonList(control == null ? "" : control.getText());
        }
    }

    private static final class CheckboxFieldBinding extends AbstractFieldBinding {

        private final String value;
        private final DocumentCheckboxControl control;

        CheckboxFieldBinding(String name, boolean disabled, String value, DocumentCheckboxControl control) {
            super(name, disabled);
            this.value = value == null || value.isEmpty() ? "on" : value;
            this.control = control;
        }

        @Override
        public List<String> resolveValues() {
            if (control == null || !control.isChecked()) {
                return Collections.emptyList();
            }
            return Collections.singletonList(value);
        }
    }

    private static final class TextAreaFieldBinding extends AbstractFieldBinding {

        private final DocumentTextAreaControl control;

        TextAreaFieldBinding(String name, boolean disabled, DocumentTextAreaControl control) {
            super(name, disabled);
            this.control = control;
        }

        @Override
        public List<String> resolveValues() {
            return Collections.singletonList(control == null ? "" : control.getText());
        }
    }

    private static final class SelectFieldBinding extends AbstractFieldBinding {

        private final DocumentSelectControl control;
        private final List<String> values;

        SelectFieldBinding(String name, boolean disabled, DocumentSelectControl control, List<String> values) {
            super(name, disabled);
            this.control = control;
            this.values = values == null ? Collections.<String>emptyList() : new ArrayList<String>(values);
        }

        @Override
        public List<String> resolveValues() {
            int index = control == null ? -1 : control.getSelectedIndex();
            if (index < 0 || index >= values.size()) {
                return Collections.emptyList();
            }
            return Collections.singletonList(values.get(index));
        }
    }

    private static final class RadioGroupFieldBinding extends AbstractFieldBinding {

        private final DocumentRadioGroupControl control;
        private final List<String> values;

        RadioGroupFieldBinding(String name, boolean disabled, DocumentRadioGroupControl control, List<String> values) {
            super(name, disabled);
            this.control = control;
            this.values = values == null ? Collections.<String>emptyList() : new ArrayList<String>(values);
        }

        @Override
        public List<String> resolveValues() {
            int index = control == null ? -1 : control.getSelectedIndex();
            if (index < 0 || index >= values.size()) {
                return Collections.emptyList();
            }
            return Collections.singletonList(values.get(index));
        }
    }

    private static final class SubmitButtonHandler implements DocumentButtonActionHandler {

        private final FormState form;
        private final Submitter submitter;

        SubmitButtonHandler(FormState form, Submitter submitter) {
            this.form = form;
            this.submitter = submitter;
        }

        @Override
        public void onAction(DocumentButtonActionEvent event) {
            form.submit(submitter);
        }
    }

    private static final class SubmitOnEnterHandler implements DocumentElementKeyHandler {

        private final FormState form;
        private final Submitter submitter;

        SubmitOnEnterHandler(FormState form, Submitter submitter) {
            this.form = form;
            this.submitter = submitter;
        }

        @Override
        public boolean onKey(DocumentElementKeyEvent event) {
            if ((event.getKeyCode() == Keyboard.KEY_RETURN || event.getKeyCode() == Keyboard.KEY_NUMPADENTER)
                    && event.getAction() == UiKeyEvent.Action.PRESSED) {
                form.submit(submitter);
                return true;
            }
            return false;
        }
    }

    private static void addValue(Map<String, List<String>> values, String name, String value) {
        List<String> list = values.get(name);
        if (list == null) {
            list = new ArrayList<String>();
            values.put(name, list);
        }
        list.add(value == null ? "" : value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
