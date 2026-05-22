package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;

/**
 * `DocumentCheckboxControl` 的基础行为契约测试。
 */
public class DocumentCheckboxControlTest {

    /**
     * 验证构造后的 ARIA 语义与标签文本。
     */
    @Test
    public void shouldCreateCheckboxElementWithLabel() {
        UiDocument document = UiDocument.create();
        DocumentCheckboxControl checkbox = new DocumentCheckboxControl(document, "启用提示");

        Assert.assertEquals("checkbox", checkbox.getElement().getAttribute("role"));
        Assert.assertEquals("0", checkbox.getElement().getAttribute("tabindex"));
        Assert.assertEquals("false", checkbox.getElement().getAttribute("aria-checked"));
        Assert.assertTrue(checkbox.getElement().isFocusable());
        Assert.assertEquals("启用提示", checkbox.getLabel());
        Assert.assertEquals(2, checkbox.getElement().getChildren().size());
    }

    /**
     * 验证点击会切换状态并触发 change handler。
     */
    @Test
    public void shouldToggleCheckedStateOnClick() {
        UiDocument document = UiDocument.create();
        final List<DocumentCheckboxChangeEvent> events = new ArrayList<DocumentCheckboxChangeEvent>();
        DocumentCheckboxControl checkbox = new DocumentCheckboxControl(document, "选项")
                .setChangeHandler(new DocumentCheckboxChangeHandler() {
                    @Override
                    public void onCheckboxChanged(DocumentCheckboxChangeEvent event) {
                        events.add(event);
                    }
                });
        ElementNode element = checkbox.getElement();

        Assert.assertTrue(element.getClickHandler().onClick(new DocumentElementClickEvent(element, element, 0, 0, 0,
                1L)));

        Assert.assertTrue(checkbox.isChecked());
        Assert.assertEquals("true", element.getAttribute("aria-checked"));
        Assert.assertEquals(1, events.size());
        Assert.assertSame(checkbox, events.get(0).getSource());
        Assert.assertSame(element, events.get(0).getElement());
        Assert.assertTrue(events.get(0).isChecked());
        Assert.assertFalse(events.get(0).isIndeterminate());
    }

    /**
     * 验证 Space 在松开时切换状态。
     */
    @Test
    public void shouldToggleCheckedStateOnSpaceReleased() {
        UiDocument document = UiDocument.create();
        final List<Boolean> states = new ArrayList<Boolean>();
        DocumentCheckboxControl checkbox = new DocumentCheckboxControl(document)
                .setChangeHandler(new DocumentCheckboxChangeHandler() {
                    @Override
                    public void onCheckboxChanged(DocumentCheckboxChangeEvent event) {
                        states.add(Boolean.valueOf(event.isChecked()));
                    }
                });
        ElementNode element = checkbox.getElement();

        element.getKeyHandler().onKey(keyEvent(element, Keyboard.KEY_SPACE, UiKeyEvent.Action.PRESSED, 1L));
        Assert.assertFalse(checkbox.isChecked());
        element.getKeyHandler().onKey(keyEvent(element, Keyboard.KEY_SPACE, UiKeyEvent.Action.RELEASED, 2L));

        Assert.assertTrue(checkbox.isChecked());
        Assert.assertEquals(1, states.size());
        Assert.assertTrue(states.get(0).booleanValue());
    }

    /**
     * 验证程序化默认设置不触发事件，显式 notify 才触发。
     */
    @Test
    public void shouldOnlyNotifyWhenProgrammaticSetRequestsNotify() {
        UiDocument document = UiDocument.create();
        final List<Boolean> states = new ArrayList<Boolean>();
        DocumentCheckboxControl checkbox = new DocumentCheckboxControl(document)
                .setChangeHandler(new DocumentCheckboxChangeHandler() {
                    @Override
                    public void onCheckboxChanged(DocumentCheckboxChangeEvent event) {
                        states.add(Boolean.valueOf(event.isChecked()));
                    }
                });

        checkbox.setChecked(true);
        checkbox.setChecked(false, true);

        Assert.assertEquals(1, states.size());
        Assert.assertFalse(states.get(0).booleanValue());
    }

    /**
     * 验证半选状态使用 mixed ARIA 且用户切换会清除半选。
     */
    @Test
    public void shouldExposeIndeterminateStateAndClearItOnUserToggle() {
        UiDocument document = UiDocument.create();
        DocumentCheckboxControl checkbox = new DocumentCheckboxControl(document).setIndeterminate(true);
        ElementNode element = checkbox.getElement();

        Assert.assertTrue(checkbox.isIndeterminate());
        Assert.assertEquals("mixed", element.getAttribute("aria-checked"));

        element.getClickHandler().onClick(new DocumentElementClickEvent(element, element, 0, 0, 0, 1L));

        Assert.assertFalse(checkbox.isIndeterminate());
        Assert.assertTrue(checkbox.isChecked());
        Assert.assertEquals("true", element.getAttribute("aria-checked"));
    }

    /**
     * 验证禁用状态不响应用户点击。
     */
    @Test
    public void shouldIgnoreClickWhenDisabled() {
        UiDocument document = UiDocument.create();
        final List<Boolean> states = new ArrayList<Boolean>();
        DocumentCheckboxControl checkbox = new DocumentCheckboxControl(document)
                .setEnabled(false)
                .setChangeHandler(new DocumentCheckboxChangeHandler() {
                    @Override
                    public void onCheckboxChanged(DocumentCheckboxChangeEvent event) {
                        states.add(Boolean.valueOf(event.isChecked()));
                    }
                });
        ElementNode element = checkbox.getElement();

        Assert.assertTrue(element.getClickHandler().onClick(new DocumentElementClickEvent(element, element, 0, 0, 0,
                1L)));

        Assert.assertFalse(checkbox.isChecked());
        Assert.assertTrue(states.isEmpty());
        Assert.assertFalse(element.isFocusable());
        Assert.assertEquals("true", element.getAttribute("aria-disabled"));
    }

    private static DocumentElementKeyEvent keyEvent(ElementNode element, int keyCode, UiKeyEvent.Action action,
            long timeNanos) {
        return new DocumentElementKeyEvent(element, element, new UiKeyEvent(keyCode, 0, 0, action, false, false,
                false, false, timeNanos));
    }
}
