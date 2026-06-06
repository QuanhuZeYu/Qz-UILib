package club.heiqi.uilib.ui.document;

import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertElementUid;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.control.DocumentDraggableSupport;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.DeterministicTextMeasureService;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDragEvent;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `HtmlLikeDocumentWidget` 的拖拽语义回归测试。
 */
public class HtmlLikeDocumentWidgetDragTest {

    /**
     * 验证 HTML 级拖拽辅助器可以更新 fixed 元素位置。
     */
    @Test
    public void shouldDragFixedElementThroughDocumentDragSupport() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode handle = document.div();

        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(120));
        panel.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(20))
                .setTop(UiStyleLength.px(10))
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(30))
                .setBackgroundColor(0xFF223344);
        handle.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(10))
                .setBackgroundColor(0xFF446688);
        panel.append(handle);
        root.append(panel);
        DocumentDraggableSupport.attach(panel, handle, DocumentDraggableSupport.DragAxis.BOTH);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 120);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 25, 15, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 45, 35, -1, 0, 20, 20, 2L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 45, 35, 0, 0, 0, 0, 3L));

        Assert.assertNotNull(panel.style().getLeft());
        Assert.assertNotNull(panel.style().getTop());
        Assert.assertEquals(UiStyleLength.Type.PIXEL, panel.style().getLeft().getType());
        Assert.assertEquals(UiStyleLength.Type.PIXEL, panel.style().getTop().getType());
        Assert.assertTrue(panel.style().getLeft().getValue() >= 40.0F);
        Assert.assertTrue(panel.style().getTop().getValue() >= 30.0F);
        Assert.assertNull(panel.style().getRight());
        Assert.assertNull(panel.style().getBottom());
    }

    /**
     * 验证 fixed fallback 作用于已 fixed 元素时只沿用现有 left/top 基线。
     */
    @Test
    public void shouldDragAlreadyFixedElementThroughFixedDragSupportWithoutPositionSwitch() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode handle = document.div();

        root.style()
                .setWidth(UiStyleLength.px(320))
                .setHeight(UiStyleLength.px(180));
        panel.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(96))
                .setTop(UiStyleLength.px(64))
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(48))
                .setBackgroundColor(0xFF223344);
        handle.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF446688);
        panel.append(handle);
        root.append(panel);
        DocumentDraggableSupport.attachFixed(panel, handle, DocumentDraggableSupport.DragAxis.BOTH);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 320, 180,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 320, 180);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 108, 72, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 138, 94, -1, 0, 30, 22, 2L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 138, 94, 0, 0, 0, 0, 3L));

        Assert.assertEquals(UiPosition.FIXED, panel.style().getPosition());
        Assert.assertEquals(126.0F, panel.style().getLeft().getValue(), 0.001F);
        Assert.assertEquals(86.0F, panel.style().getTop().getValue(), 0.001F);
        Assert.assertNull(panel.style().getRight());
        Assert.assertNull(panel.style().getBottom());
    }

    /**
     * 验证 fixed 拖拽模式会从 static 元素当前视觉位置开始移动。
     */
    @Test
    public void shouldDragStaticElementFromCurrentBoundsThroughFixedDragSupport() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode handle = document.div();

        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(120))
                .setPadding(UiStyleLength.px(20));
        panel.style()
                .setPosition(UiPosition.STATIC)
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(30))
                .setBackgroundColor(0xFF223344);
        handle.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(10))
                .setBackgroundColor(0xFF446688);
        panel.append(handle);
        root.append(panel);
        DocumentDraggableSupport.attachFixed(panel, panel, DocumentDraggableSupport.DragAxis.BOTH);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 120);
        widget.findElementAt(25, 25);

        panel.getDragHandler().onDrag(new DocumentElementDragEvent(panel, panel, 25, 25, 25, 25, 0, 0, 0,
                1L, DocumentElementDragEvent.DragPhase.START));
        panel.getDragHandler().onDrag(new DocumentElementDragEvent(panel, panel, 25, 25, 45, 35, 20, 10, 0,
                2L, DocumentElementDragEvent.DragPhase.DRAG));
        panel.getDragHandler().onDrag(new DocumentElementDragEvent(panel, panel, 25, 25, 45, 35, 0, 0, 0,
                3L, DocumentElementDragEvent.DragPhase.END));

        Assert.assertEquals(UiPosition.FIXED, panel.style().getPosition());
        Assert.assertEquals(40.0F, panel.style().getLeft().getValue(), 0.001F);
        Assert.assertEquals(30.0F, panel.style().getTop().getValue(), 0.001F);
        Assert.assertNull(panel.style().getRight());
        Assert.assertNull(panel.style().getBottom());
    }

    /**
     * 验证 fixed 拖拽模式也能从 relative 元素当前视觉位置开始移动。
     */
    @Test
    public void shouldDragRelativeElementFromCurrentBoundsThroughFixedDragSupport() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode handle = document.div();

        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(120))
                .setPadding(UiStyleLength.px(20));
        panel.style()
                .setPosition(UiPosition.RELATIVE)
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(30))
                .setBackgroundColor(0xFF223344);
        handle.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(10))
                .setBackgroundColor(0xFF446688);
        panel.append(handle);
        root.append(panel);
        DocumentDraggableSupport.attachFixed(panel, handle, DocumentDraggableSupport.DragAxis.BOTH);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 120);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 25, 25, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 45, 35, -1, 0, 20, 10, 2L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 45, 35, 0, 0, 0, 0, 3L));

        Assert.assertEquals(UiPosition.FIXED, panel.style().getPosition());
        Assert.assertEquals(40.0F, panel.style().getLeft().getValue(), 0.001F);
        Assert.assertEquals(30.0F, panel.style().getTop().getValue(), 0.001F);
    }

    /**
     * 验证 `right/bottom` 锚定的 fixed 浮窗首次拖拽时不会跳到左上角基线。
     */
    @Test
    public void shouldDragRightBottomAnchoredFixedElementWithoutJumping() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode handle = document.div();

        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(120));
        panel.style()
                .setPosition(UiPosition.FIXED)
                .setRight(UiStyleLength.px(20))
                .setBottom(UiStyleLength.px(10))
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(30))
                .setBackgroundColor(0xFF223344);
        handle.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(10))
                .setBackgroundColor(0xFF446688);
        panel.append(handle);
        root.append(panel);
        DocumentDraggableSupport.attach(panel, handle, DocumentDraggableSupport.DragAxis.BOTH);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 120);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 145, 85, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 165, 95, -1, 0, 20, 10, 2L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 165, 95, 0, 0, 0, 0, 3L));

        Assert.assertNull(panel.style().getLeft());
        Assert.assertNull(panel.style().getTop());
        Assert.assertNotNull(panel.style().getRight());
        Assert.assertNotNull(panel.style().getBottom());
        Assert.assertEquals(UiStyleLength.Type.PIXEL, panel.style().getRight().getType());
        Assert.assertEquals(UiStyleLength.Type.PIXEL, panel.style().getBottom().getType());
        Assert.assertEquals(0.0F, panel.style().getRight().getValue(), 0.001F);
        Assert.assertEquals(0.0F, panel.style().getBottom().getValue(), 0.001F);
    }

    /**
     * 验证可拖拽把手在短点击时仍会保留 click 语义。
     */
    @Test
    public void shouldPreserveClickForDraggableHandleWithoutCrossingThreshold() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode panel = document.div();
        final ElementNode handle = document.div();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();

        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(120));
        panel.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(20))
                .setTop(UiStyleLength.px(10))
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(30));
        handle.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(10));
        handle.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        panel.append(handle);
        root.append(panel);
        DocumentDraggableSupport.attach(panel, handle, DocumentDraggableSupport.DragAxis.BOTH);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 120);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 25, 15, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 27, 16, -1, 0, 2, 1, 2L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 27, 16, 0, 0, 0, 0, 3L));

        Assert.assertEquals(1, clickEvents.size());
        assertElementUid(handle, clickEvents.get(0).getTarget());
        Assert.assertEquals(20.0F, panel.style().getLeft().getValue(), 0.001F);
        Assert.assertEquals(10.0F, panel.style().getTop().getValue(), 0.001F);
    }

    /**
     * 验证 drag handler 只有超过阈值后才进入真正拖拽并阻断 click。
     */
    @Test
    public void shouldActivateDragOnlyAfterCrossingThreshold() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode panel = document.div();
        final List<DocumentElementDragEvent.DragPhase> dragPhases =
                new ArrayList<DocumentElementDragEvent.DragPhase>();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();

        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(60));
        panel.style()
                .setWidth(UiStyleLength.px(60))
                .setHeight(UiStyleLength.px(20));
        panel.setDragHandler(new club.heiqi.uilib.ui.dom.DocumentElementDragHandler() {
            @Override
            public boolean onDrag(DocumentElementDragEvent event) {
                dragPhases.add(event.getPhase());
                return true;
            }
        });
        panel.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        root.append(panel);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 60,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 60);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 12, 11, -1, 0, 2, 1, 2L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 12, 11, 0, 0, 0, 0, 3L));

        Assert.assertEquals(2, dragPhases.size());
        Assert.assertEquals(DocumentElementDragEvent.DragPhase.START, dragPhases.get(0));
        Assert.assertEquals(DocumentElementDragEvent.DragPhase.END, dragPhases.get(1));
        Assert.assertEquals(1, clickEvents.size());

        dragPhases.clear();
        clickEvents.clear();

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 4L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 16, 10, -1, 0, 6, 0, 5L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 16, 10, 0, 0, 0, 0, 6L));

        Assert.assertEquals(3, dragPhases.size());
        Assert.assertEquals(DocumentElementDragEvent.DragPhase.START, dragPhases.get(0));
        Assert.assertEquals(DocumentElementDragEvent.DragPhase.DRAG, dragPhases.get(1));
        Assert.assertEquals(DocumentElementDragEvent.DragPhase.END, dragPhases.get(2));
        Assert.assertTrue(clickEvents.isEmpty());
    }

    /**
     * 验证 draggable="true" 会走浏览器式 dragstart / dragover / dragend 事件链。
     */
    @Test
    public void shouldDispatchHtmlLikeDragEventsForDraggableElement() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode list = document.div();
        ElementNode item = document.div();
        final List<String> events = new ArrayList<String>();

        root.style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(120));
        list.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(100));
        item.setAttribute("draggable", "true");
        item.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(30));
        item.setDragStartHandler(new club.heiqi.uilib.ui.dom.DocumentElementDragStartHandler() {
            @Override
            public boolean onDragStart(DocumentElementDragEvent event) {
                events.add("start:" + event.getTarget().getAttribute("draggable"));
                return true;
            }
        });
        item.setDragEndHandler(new club.heiqi.uilib.ui.dom.DocumentElementDragEndHandler() {
            @Override
            public boolean onDragEnd(DocumentElementDragEvent event) {
                events.add("end:" + event.getTarget().getAttribute("draggable"));
                return true;
            }
        });
        list.setDragOverHandler(new club.heiqi.uilib.ui.dom.DocumentElementDragOverHandler() {
            @Override
            public boolean onDragOver(DocumentElementDragEvent event) {
                events.add("over:" + event.getTarget().getAttribute("draggable"));
                return true;
            }
        });
        list.append(item);
        root.append(list);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 180, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 180, 120);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 18, 10, -1, 0, 8, 0, 2L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 26, 10, -1, 0, 8, 0, 3L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 26, 10, 0, 0, 0, 0, 4L));

        Assert.assertEquals("start:true", events.get(0));
        Assert.assertEquals("over:true", events.get(1));
        Assert.assertEquals("over:true", events.get(2));
        Assert.assertEquals("end:true", events.get(3));
        Assert.assertEquals(4, events.size());
    }

    /**
     * 验证浏览器式拖拽事件继续沿用 UILib 原生像素坐标，不回退到 MC GUI 缩放坐标。
     */
    @Test
    public void shouldExposeNativeDocumentCoordinatesForHtmlLikeDragEvents() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode item = document.div();
        final List<Integer> coordinates = new ArrayList<Integer>();

        root.style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(120));
        item.setAttribute("draggable", "true");
        item.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(30));
        item.setDragStartHandler(new club.heiqi.uilib.ui.dom.DocumentElementDragStartHandler() {
            @Override
            public boolean onDragStart(DocumentElementDragEvent event) {
                coordinates.add(Integer.valueOf(event.getStartDocumentX()));
                coordinates.add(Integer.valueOf(event.getStartDocumentY()));
                coordinates.add(Integer.valueOf(event.getDocumentX()));
                coordinates.add(Integer.valueOf(event.getDocumentY()));
                return true;
            }
        });
        root.append(item);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 180, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(100, 200, 180, 120);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 112, 218, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 130, 250, -1, 0, 18, 32, 2L));

        Assert.assertEquals(Integer.valueOf(12), coordinates.get(0));
        Assert.assertEquals(Integer.valueOf(18), coordinates.get(1));
        Assert.assertEquals(Integer.valueOf(30), coordinates.get(2));
        Assert.assertEquals(Integer.valueOf(50), coordinates.get(3));
    }
}
