package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `DocumentSelectControl` 的基础行为契约测试。
 */
public class DocumentSelectControlTest {

    /**
     * 验证下拉选择控件使用真实 select 语义。
     */
    @Test
    public void shouldUseSelectElementSemantics() {
        UiDocument document = UiDocument.create();
        DocumentSelectControl selectControl = new DocumentSelectControl(document, "A", "B", "C");

        Assert.assertEquals("select", selectControl.getElement().getTagName());
        Assert.assertEquals("combobox", selectControl.getElement().getSemanticRole());
        Assert.assertEquals("A", selectControl.getElement().getAttribute("value"));
        Assert.assertEquals("false", selectControl.getElement().getAttribute("aria-expanded"));
    }

    /**
     * 验证鼠标可展开并选择候选项。
     */
    @Test
    public void shouldOpenAndSelectOptionByMouse() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final List<DocumentSelectChangeEvent> events = new ArrayList<DocumentSelectChangeEvent>();
        DocumentSelectControl selectControl = new DocumentSelectControl(document, "A", "B", "C")
                .setChangeHandler(new DocumentSelectChangeHandler() {
                    @Override
                    public void onSelectionChanged(DocumentSelectChangeEvent event) {
                        events.add(event);
                    }
                });
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(160));
        selectControl.getElement().style().setWidth(UiStyleLength.px(180));
        root.append(selectControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 160,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 160);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 20, 12, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 20, 12, 0, 0, 0, 0, 2L));
        Assert.assertEquals("true", selectControl.getElement().getAttribute("aria-expanded"));

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 20, 72, 0, 0, 0, 0, 3L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 20, 72, 0, 0, 0, 0, 4L));

        Assert.assertEquals(1, selectControl.getSelectedIndex());
        Assert.assertEquals("B", selectControl.getSelectedOption());
        Assert.assertEquals("false", selectControl.getElement().getAttribute("aria-expanded"));
        Assert.assertEquals(1, events.size());
        Assert.assertEquals("B", events.get(0).getSelectedOption());
        Assert.assertFalse(events.get(0).isKeyboardTriggered());
    }

    /**
     * 验证下拉面板展开后 option 的直接文本会被绘制。
     */
    @Test
    public void shouldRenderOptionTextWhenPopupIsOpen() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentSelectControl selectControl = new DocumentSelectControl(document, "A", "B", "C");
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(160));
        selectControl.getElement().style().setWidth(UiStyleLength.px(180));
        root.append(selectControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 160,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 160);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 20, 12, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 20, 12, 0, 0, 0, 0, 2L));
        ControlTestRenderContext renderContext = new ControlTestRenderContext(240, 160);
        widget.render(renderContext);

        Assert.assertTrue(countTextCalls(renderContext, "A") >= 2);
        Assert.assertTrue(containsTextCall(renderContext, "B"));
        Assert.assertTrue(containsTextCall(renderContext, "C"));
    }

    /**
     * 验证键盘方向键可以切换当前值，Enter 可以展开/收起。
     */
    @Test
    public void shouldSupportKeyboardNavigation() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final List<DocumentSelectChangeEvent> events = new ArrayList<DocumentSelectChangeEvent>();
        DocumentSelectControl selectControl = new DocumentSelectControl(document, "A", "B", "C")
                .setChangeHandler(new DocumentSelectChangeHandler() {
                    @Override
                    public void onSelectionChanged(DocumentSelectChangeEvent event) {
                        events.add(event);
                    }
                });
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(120));
        selectControl.getElement().style().setWidth(UiStyleLength.px(180));
        root.append(selectControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 120);

        widget.onFocusTraversalEntered(false);
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_DOWN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 1L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 2L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_ESCAPE, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 3L));

        Assert.assertEquals(1, selectControl.getSelectedIndex());
        Assert.assertEquals("B", selectControl.getSelectedOption());
        Assert.assertEquals("false", selectControl.getElement().getAttribute("aria-expanded"));
        Assert.assertEquals(1, events.size());
        Assert.assertTrue(events.get(0).isKeyboardTriggered());
        Assert.assertEquals(Keyboard.KEY_DOWN, events.get(0).getKeyCode());
    }

    /**
     * 验证长列表打开后，键盘导航会把当前选项滚入下拉面板可视区域。
     */
    @Test
    public void shouldRevealSelectedOptionWhenKeyboardNavigatesLongList() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentSelectControl selectControl = new DocumentSelectControl(document, "A", "B", "C", "D", "E", "F",
                "G");
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(260));
        selectControl.getElement().style().setWidth(UiStyleLength.px(180));
        root.append(selectControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 260,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 260);

        widget.onFocusTraversalEntered(false);
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 1L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_END, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 2L));

        ElementNode popup = (ElementNode) selectControl.getElement().getChildren().get(1);
        Assert.assertEquals(6, selectControl.getSelectedIndex());
        Assert.assertTrue(popup.getMaxScrollTop() > 0);
        Assert.assertTrue(popup.getScrollTop() > 0);
    }

    private static boolean containsTextCall(ControlTestRenderContext renderContext, String text) {
        return countTextCalls(renderContext, text) > 0;
    }

    private static int countTextCalls(ControlTestRenderContext renderContext, String text) {
        int count = 0;
        for (ControlTestRenderContext.TextCall textCall : renderContext.textCalls) {
            if (text.equals(textCall.text)) {
                count++;
            }
        }
        return count;
    }

    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 6;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            return text == null || targetWidth <= 0 ? "" : text.substring(0,
                    Math.min(text.length(), targetWidth / 6));
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            List<String> lines = new ArrayList<String>();
            lines.add(text == null ? "" : text);
            return lines;
        }
    }
}
