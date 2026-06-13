package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;

/**
 * `DocumentRadioGroupControl` 的基础行为契约测试。
 */
public class DocumentRadioGroupControlTest {

    /**
     * 验证构造后的 radiogroup 语义与默认选中项。
     */
    @Test
    public void shouldCreateRadioGroupWithOptions() {
        UiDocument document = UiDocument.create();
        DocumentRadioGroupControl radioGroup = new DocumentRadioGroupControl(document, "低", "中", "高");

        Assert.assertEquals("radiogroup", radioGroup.getElement().getAttribute("role"));
        Assert.assertEquals(3, radioGroup.getElement().getChildren().size());
        ElementNode firstOption = optionAt(radioGroup, 0);
        Assert.assertEquals("radio", firstOption.getAttribute("role"));
        Assert.assertEquals("true", firstOption.getAttribute("aria-checked"));
        Assert.assertEquals(0, radioGroup.getSelectedIndex());
        Assert.assertEquals("低", radioGroup.getSelectedOption());
    }

    /**
     * 验证鼠标点击会切换选项并触发事件。
     */
    @Test
    public void shouldSelectOptionOnClick() {
        UiDocument document = UiDocument.create();
        final List<DocumentRadioChangeEvent> events = new ArrayList<DocumentRadioChangeEvent>();
        DocumentRadioGroupControl radioGroup = new DocumentRadioGroupControl(document, "低", "中", "高")
                .setChangeHandler(new DocumentRadioChangeHandler() {
                    @Override
                    public void onRadioChanged(DocumentRadioChangeEvent event) {
                        events.add(event);
                    }
                });
        ElementNode secondOption = optionAt(radioGroup, 1);

        Assert.assertTrue(secondOption.getClickHandler().onClick(new DocumentElementClickEvent(secondOption,
                secondOption, 0, 0, 0, 1L)));

        Assert.assertEquals(1, radioGroup.getSelectedIndex());
        Assert.assertEquals("中", radioGroup.getSelectedOption());
        Assert.assertEquals("true", secondOption.getAttribute("aria-checked"));
        Assert.assertEquals(1, events.size());
        Assert.assertEquals(1, events.get(0).getSelectedIndex());
        Assert.assertEquals("中", events.get(0).getSelectedOption());
        Assert.assertFalse(events.get(0).isKeyboardTriggered());
    }

    /**
     * 验证垂直方向使用上下方向键切换。
     */
    @Test
    public void shouldSelectOptionWithVerticalArrowKeys() {
        UiDocument document = UiDocument.create();
        final List<DocumentRadioChangeEvent> events = new ArrayList<DocumentRadioChangeEvent>();
        DocumentRadioGroupControl radioGroup = new DocumentRadioGroupControl(document, "低", "中", "高")
                .setChangeHandler(new DocumentRadioChangeHandler() {
                    @Override
                    public void onRadioChanged(DocumentRadioChangeEvent event) {
                        events.add(event);
                    }
                });
        ElementNode element = radioGroup.getElement();

        DocumentElementKeyEvent keyEvent = keyEvent(optionAt(radioGroup, 0), element, UiKeyCodes.KEY_DOWN, 1L);
        Assert.assertTrue(element.getKeyHandler().onKey(keyEvent));

        Assert.assertEquals(1, radioGroup.getSelectedIndex());
        Assert.assertEquals(1, events.size());
        Assert.assertTrue(events.get(0).isKeyboardTriggered());
        Assert.assertSame(optionAt(radioGroup, 1), keyEvent.getPendingFocusTarget());
    }

    /**
     * 验证水平方向使用左右方向键切换。
     */
    @Test
    public void shouldSelectOptionWithHorizontalArrowKeys() {
        UiDocument document = UiDocument.create();
        DocumentRadioGroupControl radioGroup = new DocumentRadioGroupControl(document, "低", "中", "高")
                .setOrientation(UiRadioOrientation.HORIZONTAL);
        ElementNode element = radioGroup.getElement();

        Assert.assertTrue(element.getKeyHandler().onKey(keyEvent(optionAt(radioGroup, 0), element,
                UiKeyCodes.KEY_RIGHT, 1L)));

        Assert.assertEquals(1, radioGroup.getSelectedIndex());
    }

    /**
     * 验证程序化默认设置不触发事件。
     */
    @Test
    public void shouldNotNotifyForProgrammaticSelectionByDefault() {
        UiDocument document = UiDocument.create();
        final List<DocumentRadioChangeEvent> events = new ArrayList<DocumentRadioChangeEvent>();
        DocumentRadioGroupControl radioGroup = new DocumentRadioGroupControl(document, "低", "中")
                .setChangeHandler(new DocumentRadioChangeHandler() {
                    @Override
                    public void onRadioChanged(DocumentRadioChangeEvent event) {
                        events.add(event);
                    }
                });

        radioGroup.setSelectedIndex(1);

        Assert.assertEquals(1, radioGroup.getSelectedIndex());
        Assert.assertTrue(events.isEmpty());
    }

    /**
     * 验证禁用状态不响应点击。
     */
    @Test
    public void shouldIgnoreClickWhenDisabled() {
        UiDocument document = UiDocument.create();
        final List<DocumentRadioChangeEvent> events = new ArrayList<DocumentRadioChangeEvent>();
        DocumentRadioGroupControl radioGroup = new DocumentRadioGroupControl(document, "低", "中")
                .setChangeHandler(new DocumentRadioChangeHandler() {
                    @Override
                    public void onRadioChanged(DocumentRadioChangeEvent event) {
                        events.add(event);
                    }
                })
                .setEnabled(false);
        ElementNode secondOption = optionAt(radioGroup, 1);

        Assert.assertFalse(secondOption.getClickHandler().onClick(new DocumentElementClickEvent(secondOption,
                secondOption, 0, 0, 0, 1L)));

        Assert.assertEquals(0, radioGroup.getSelectedIndex());
        Assert.assertTrue(events.isEmpty());
        Assert.assertFalse(secondOption.isFocusable());
    }

    private static ElementNode optionAt(DocumentRadioGroupControl radioGroup, int index) {
        return (ElementNode) radioGroup.getElement().getChildren().get(index);
    }

    private static DocumentElementKeyEvent keyEvent(ElementNode target, ElementNode currentTarget, int keyCode,
            long timeNanos) {
        return new DocumentElementKeyEvent(target, currentTarget, new UiKeyEvent(keyCode, 0, 0,
                UiKeyEvent.Action.PRESSED, false, false, false, false, timeNanos));
    }
}
