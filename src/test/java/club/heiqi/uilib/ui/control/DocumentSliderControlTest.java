package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementDragEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `DocumentSliderControl` 的基础行为契约测试。
 */
public class DocumentSliderControlTest {

    /**
     * 验证构造后的 slider 语义和默认范围。
     */
    @Test
    public void shouldCreateSliderElement() {
        UiDocument document = UiDocument.create();
        DocumentSliderControl slider = new DocumentSliderControl(document);

        Assert.assertEquals("slider", slider.getElement().getAttribute("role"));
        Assert.assertEquals("0", slider.getElement().getAttribute("tabindex"));
        Assert.assertEquals(0.0D, slider.getMin(), 0.0001D);
        Assert.assertEquals(100.0D, slider.getMax(), 0.0001D);
        Assert.assertEquals("0.0", slider.getElement().getAttribute("aria-valuenow"));
    }

    /**
     * 验证点击轨道会跳转数值并触发提交态事件。
     */
    @Test
    public void shouldSetValueFromClickPosition() {
        UiDocument document = UiDocument.create();
        final List<DocumentSliderChangeEvent> events = new ArrayList<DocumentSliderChangeEvent>();
        DocumentSliderControl slider = new DocumentSliderControl(document)
                .setChangeHandler(new DocumentSliderChangeHandler() {
                    @Override
                    public void onSliderChanged(DocumentSliderChangeEvent event) {
                        events.add(event);
                    }
                });
        ElementNode element = slider.getElement();

        Assert.assertTrue(element.getClickHandler().onClick(new DocumentElementClickEvent(element, element, 80, 0, 0,
                1L)));

        Assert.assertEquals(50.0D, slider.getValue(), 0.0001D);
        Assert.assertEquals(1, events.size());
        Assert.assertTrue(events.get(0).isCommitting());
        Assert.assertTrue(events.get(0).isUserTriggered());
    }

    /**
     * 验证挂载到非零 X 位置后，点击轨道按元素自身边界计算局部坐标。
     */
    @Test
    public void shouldUseMountedElementBoundsForClickPosition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(300))
                .setHeight(UiStyleLength.px(80));
        ElementNode wrapper = document.div();
        wrapper.style()
                .setMarginLeft(UiStyleLength.px(40))
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(24));
        DocumentSliderControl slider = new DocumentSliderControl(document);
        wrapper.append(slider.getElement());
        root.append(wrapper);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 300, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 300, 80);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 121, 8, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 121, 8, 0, 0, 0, 0, 2L));

        Assert.assertEquals("contentLeft=" + slider.getElement().getDocumentBounds().getContentLeft()
                + ", contentWidth=" + slider.getElement().getDocumentBounds().getContentWidth()
                + ", value=" + slider.getValue(), 50.0D, slider.getValue(), 0.0001D);
    }

    /**
     * 验证键盘方向键按步进修改数值。
     */
    @Test
    public void shouldChangeValueWithKeyboardStep() {
        UiDocument document = UiDocument.create();
        final List<DocumentSliderChangeEvent> events = new ArrayList<DocumentSliderChangeEvent>();
        DocumentSliderControl slider = new DocumentSliderControl(document)
                .setStep(5.0D)
                .setChangeHandler(new DocumentSliderChangeHandler() {
                    @Override
                    public void onSliderChanged(DocumentSliderChangeEvent event) {
                        events.add(event);
                    }
                });
        ElementNode element = slider.getElement();

        Assert.assertTrue(element.getKeyHandler().onKey(keyEvent(element, UiKeyCodes.KEY_RIGHT, 1L)));

        Assert.assertEquals(5.0D, slider.getValue(), 0.0001D);
        Assert.assertEquals(1, events.size());
        Assert.assertTrue(events.get(0).isCommitting());
    }

    /**
     * 验证拖动中产生非提交态事件，释放时产生提交态事件。
     */
    @Test
    public void shouldNotifyDuringDragAndCommitOnDragEnd() {
        UiDocument document = UiDocument.create();
        final List<DocumentSliderChangeEvent> events = new ArrayList<DocumentSliderChangeEvent>();
        DocumentSliderControl slider = new DocumentSliderControl(document)
                .setChangeHandler(new DocumentSliderChangeHandler() {
                    @Override
                    public void onSliderChanged(DocumentSliderChangeEvent event) {
                        events.add(event);
                    }
                });
        ElementNode element = slider.getElement();

        Assert.assertTrue(element.getDragHandler().onDrag(dragEvent(element, 0, 0, 0,
                DocumentElementDragEvent.DragPhase.START, 1L)));
        Assert.assertTrue(element.getDragHandler().onDrag(dragEvent(element, 0, 16, 16,
                DocumentElementDragEvent.DragPhase.DRAG, 2L)));
        Assert.assertTrue(element.getDragHandler().onDrag(dragEvent(element, 0, 16, 0,
                DocumentElementDragEvent.DragPhase.END, 3L)));

        Assert.assertEquals(10.0D, slider.getValue(), 0.0001D);
        Assert.assertEquals(2, events.size());
        Assert.assertFalse(events.get(0).isCommitting());
        Assert.assertTrue(events.get(1).isCommitting());
    }

    /**
     * 验证程序化默认设置不触发事件，显式 notify 会触发。
     */
    @Test
    public void shouldOnlyNotifyProgrammaticValueWhenRequested() {
        UiDocument document = UiDocument.create();
        final List<DocumentSliderChangeEvent> events = new ArrayList<DocumentSliderChangeEvent>();
        DocumentSliderControl slider = new DocumentSliderControl(document)
                .setChangeHandler(new DocumentSliderChangeHandler() {
                    @Override
                    public void onSliderChanged(DocumentSliderChangeEvent event) {
                        events.add(event);
                    }
                });

        slider.setValue(25.0D);
        slider.setValue(30.0D, true);

        Assert.assertEquals(30.0D, slider.getValue(), 0.0001D);
        Assert.assertEquals(1, events.size());
        Assert.assertFalse(events.get(0).isUserTriggered());
    }

    /**
     * 验证禁用时鼠标点击不会修改数值或派发事件。
     */
    @Test
    public void shouldIgnoreClickWhenDisabled() {
        UiDocument document = UiDocument.create();
        final List<DocumentSliderChangeEvent> events = new ArrayList<DocumentSliderChangeEvent>();
        DocumentSliderControl slider = new DocumentSliderControl(document)
                .setEnabled(false)
                .setChangeHandler(new DocumentSliderChangeHandler() {
                    @Override
                    public void onSliderChanged(DocumentSliderChangeEvent event) {
                        events.add(event);
                    }
                });
        ElementNode element = slider.getElement();

        Assert.assertFalse(element.getClickHandler().onClick(new DocumentElementClickEvent(element, element, 80, 0, 0,
                1L)));
        Assert.assertEquals(0.0D, slider.getValue(), 0.0001D);
        Assert.assertTrue(events.isEmpty());
    }

    /**
     * 验证禁用时键盘步进不会修改数值或派发事件。
     */
    @Test
    public void shouldIgnoreKeyboardWhenDisabled() {
        UiDocument document = UiDocument.create();
        final List<DocumentSliderChangeEvent> events = new ArrayList<DocumentSliderChangeEvent>();
        DocumentSliderControl slider = new DocumentSliderControl(document)
                .setStep(5.0D)
                .setEnabled(false)
                .setChangeHandler(new DocumentSliderChangeHandler() {
                    @Override
                    public void onSliderChanged(DocumentSliderChangeEvent event) {
                        events.add(event);
                    }
                });
        ElementNode element = slider.getElement();

        Assert.assertFalse(element.getKeyHandler().onKey(keyEvent(element, UiKeyCodes.KEY_RIGHT, 1L)));
        Assert.assertEquals(0.0D, slider.getValue(), 0.0001D);
        Assert.assertTrue(events.isEmpty());
    }

    /**
     * 验证禁用时拖动不会修改数值或派发事件。
     */
    @Test
    public void shouldIgnoreDragWhenDisabled() {
        UiDocument document = UiDocument.create();
        final List<DocumentSliderChangeEvent> events = new ArrayList<DocumentSliderChangeEvent>();
        DocumentSliderControl slider = new DocumentSliderControl(document)
                .setEnabled(false)
                .setChangeHandler(new DocumentSliderChangeHandler() {
                    @Override
                    public void onSliderChanged(DocumentSliderChangeEvent event) {
                        events.add(event);
                    }
                });
        ElementNode element = slider.getElement();

        Assert.assertFalse(element.getDragHandler().onDrag(dragEvent(element, 0, 0, 0,
                DocumentElementDragEvent.DragPhase.START, 1L)));
        Assert.assertFalse(element.getDragHandler().onDrag(dragEvent(element, 0, 16, 16,
                DocumentElementDragEvent.DragPhase.DRAG, 2L)));
        Assert.assertFalse(element.getDragHandler().onDrag(dragEvent(element, 0, 16, 0,
                DocumentElementDragEvent.DragPhase.END, 3L)));

        Assert.assertEquals(0.0D, slider.getValue(), 0.0001D);
        Assert.assertTrue(events.isEmpty());
    }

    /**
     * 验证真实 widget 拖动释放时即使 button 状态归零也会提交最终值。
     */
    @Test
    public void shouldDragFromWidgetMouseEvents() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(200))
                .setHeight(UiStyleLength.px(40));
        final List<DocumentSliderChangeEvent> events = new ArrayList<DocumentSliderChangeEvent>();
        DocumentSliderControl slider = new DocumentSliderControl(document)
                .setChangeHandler(new DocumentSliderChangeHandler() {
                    @Override
                    public void onSliderChanged(DocumentSliderChangeEvent event) {
                        events.add(event);
                    }
                });
        root.append(slider.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 200, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 200, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 1, 8, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 81, 8, -1, 0, 80, 0, 2L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 81, 8, 0, 0, 0, 0, 3L));

        Assert.assertEquals(50.0D, slider.getValue(), 0.0001D);
        Assert.assertEquals(2, events.size());
        Assert.assertFalse(events.get(0).isCommitting());
        Assert.assertTrue(events.get(1).isCommitting());
    }

    private static DocumentElementKeyEvent keyEvent(ElementNode element, int keyCode, long timeNanos) {
        return new DocumentElementKeyEvent(element, element, new UiKeyEvent(keyCode, 0, 0,
                UiKeyEvent.Action.PRESSED, false, false, false, false, timeNanos));
    }

    private static DocumentElementDragEvent dragEvent(ElementNode element, int startX, int documentX, int deltaX,
            DocumentElementDragEvent.DragPhase phase, long timeNanos) {
        return new DocumentElementDragEvent(element, element, startX, 0, documentX, 0, deltaX, 0, 0, timeNanos,
                phase);
    }

    /**
     * 供挂载交互测试使用的确定性文本测量服务。
     */
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
            if (text == null || text.isEmpty() || wrapWidth <= 0) {
                return Collections.emptyList();
            }
            return Collections.singletonList(text);
        }
    }
}
