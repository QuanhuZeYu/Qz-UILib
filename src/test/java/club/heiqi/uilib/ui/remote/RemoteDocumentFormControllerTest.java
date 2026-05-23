package club.heiqi.uilib.ui.remote;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.control.DocumentCheckboxControl;
import club.heiqi.uilib.ui.control.DocumentSelectControl;
import club.heiqi.uilib.ui.control.DocumentTextAreaControl;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * 远程页面表单字段收集测试。
 */
public class RemoteDocumentFormControllerTest {

    @Test
    public void shouldCollectSuccessfulControlsAndSubmitterValue() {
        UiDocument document = UiDocument.create();
        RemoteDocumentFormController.FormState form =
                new RemoteDocumentFormController.FormState("session", "page", "/save", "profile");
        DocumentTextInputControl name = new DocumentTextInputControl(document).setText("alice");
        DocumentCheckboxControl enabled = new DocumentCheckboxControl(document).setChecked(true);
        DocumentCheckboxControl skipped = new DocumentCheckboxControl(document).setChecked(true);
        DocumentTextAreaControl note = new DocumentTextAreaControl(document).setText("hello\nworld");
        DocumentSelectControl select = new DocumentSelectControl(document, "A", "B").setSelectedIndex(1);

        form.addText("name", false, name);
        form.addHidden("token", "abc", false);
        form.addCheckbox("flag", "yes", false, enabled);
        form.addCheckbox("skip", "yes", true, skipped);
        form.addTextarea("note", false, note);
        form.addSelect("choice", false, select, Arrays.asList("a", "b"));

        Map<String, List<String>> values = form.collectValues(
                new RemoteDocumentFormController.Submitter("submit", "go", false));

        Assert.assertEquals("alice", values.get("name").get(0));
        Assert.assertEquals("abc", values.get("token").get(0));
        Assert.assertEquals("yes", values.get("flag").get(0));
        Assert.assertFalse(values.containsKey("skip"));
        Assert.assertEquals("hello\nworld", values.get("note").get(0));
        Assert.assertEquals("b", values.get("choice").get(0));
        Assert.assertEquals("go", values.get("submit").get(0));
    }

    @Test
    public void shouldPreserveSameNameMultipleValuesAndSkipUncheckedCheckbox() {
        UiDocument document = UiDocument.create();
        RemoteDocumentFormController.FormState form =
                new RemoteDocumentFormController.FormState("session", "page", "/save", "profile");
        DocumentCheckboxControl first = new DocumentCheckboxControl(document).setChecked(true);
        DocumentCheckboxControl second = new DocumentCheckboxControl(document).setChecked(false);
        DocumentCheckboxControl third = new DocumentCheckboxControl(document).setChecked(true);

        form.addCheckbox("tag", "a", false, first);
        form.addCheckbox("tag", "b", false, second);
        form.addCheckbox("tag", "c", false, third);

        Map<String, List<String>> values = form.collectValues(null);

        Assert.assertEquals(Arrays.asList("a", "c"), values.get("tag"));
    }

    @Test
    public void shouldSubmitThroughInjectedSink() {
        final AtomicReference<Map<String, List<String>>> capturedValues =
                new AtomicReference<Map<String, List<String>>>();
        RemoteDocumentFormController.FormState form = new RemoteDocumentFormController.FormState("session",
                "page", "/save", "profile", new RemoteFormSubmitSink() {
                    @Override
                    public void submit(String sessionId, String pageId, String action, String formId,
                            Map<String, List<String>> values) {
                        Assert.assertEquals("session", sessionId);
                        Assert.assertEquals("page", pageId);
                        Assert.assertEquals("/save", action);
                        Assert.assertEquals("profile", formId);
                        capturedValues.set(values);
                    }
                });
        form.addHidden("token", "abc", false);

        form.submit(null);

        Assert.assertNotNull(capturedValues.get());
        Assert.assertEquals("abc", capturedValues.get().get("token").get(0));
    }
}
