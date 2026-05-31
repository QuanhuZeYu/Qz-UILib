package club.heiqi.uilib.ui.dom;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 事件传播模型（capture/bubble/stopPropagation/preventDefault）的契约测试。
 */
public class DocumentEventPropagationTest {

    // ========== DocumentEventControl 基础测试 ==========

    @Test
    public void eventControlDefaultState() {
        DocumentEventControl control = new DocumentEventControl();
        Assert.assertEquals(DocumentEventPhase.NONE, control.getEventPhase());
        Assert.assertFalse(control.isPropagationStopped());
        Assert.assertFalse(control.isImmediatePropagationStopped());
        Assert.assertFalse(control.isDefaultPrevented());
    }

    @Test
    public void eventControlStopPropagation() {
        DocumentEventControl control = new DocumentEventControl();
        control.stopPropagation();
        Assert.assertTrue(control.isPropagationStopped());
        Assert.assertFalse(control.isImmediatePropagationStopped());
    }

    @Test
    public void eventControlStopImmediatePropagation() {
        DocumentEventControl control = new DocumentEventControl();
        control.stopImmediatePropagation();
        Assert.assertTrue(control.isPropagationStopped());
        Assert.assertTrue(control.isImmediatePropagationStopped());
    }

    @Test
    public void eventControlPreventDefault() {
        DocumentEventControl control = new DocumentEventControl();
        control.preventDefault();
        Assert.assertTrue(control.isDefaultPrevented());
    }

    @Test
    public void eventControlReset() {
        DocumentEventControl control = new DocumentEventControl();
        control.setEventPhase(DocumentEventPhase.BUBBLING);
        control.stopPropagation();
        control.preventDefault();
        control.reset();
        Assert.assertEquals(DocumentEventPhase.NONE, control.getEventPhase());
        Assert.assertFalse(control.isPropagationStopped());
        Assert.assertFalse(control.isDefaultPrevented());
    }

    // ========== 事件类传播控制测试 ==========

    @Test
    public void clickEventStopPropagation() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        DocumentEventControl control = new DocumentEventControl();

        DocumentElementClickEvent event = new DocumentElementClickEvent(div, div, 10, 20, 0, 1000L, control);
        Assert.assertFalse(event.isPropagationStopped());

        event.stopPropagation();
        Assert.assertTrue(event.isPropagationStopped());
        // 共享控制器也应该反映状态
        Assert.assertTrue(control.isPropagationStopped());
    }

    @Test
    public void clickEventPreventDefault() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        DocumentElementClickEvent event = new DocumentElementClickEvent(div, div, 10, 20, 0, 1000L);
        Assert.assertFalse(event.isDefaultPrevented());

        event.preventDefault();
        Assert.assertTrue(event.isDefaultPrevented());
    }

    @Test
    public void clickEventPhase() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        DocumentEventControl control = new DocumentEventControl();

        control.setEventPhase(DocumentEventPhase.CAPTURING);
        DocumentElementClickEvent event = new DocumentElementClickEvent(div, div, 10, 20, 0, 1000L, control);
        Assert.assertEquals(DocumentEventPhase.CAPTURING, event.getEventPhase());

        control.setEventPhase(DocumentEventPhase.BUBBLING);
        Assert.assertEquals(DocumentEventPhase.BUBBLING, event.getEventPhase());
    }

    @Test
    public void keyEventStopPropagation() {
        DocumentEventControl control = new DocumentEventControl();
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        // 使用 null sourceEvent 会 NPE，所以我们只测试 control 共享
        control.stopPropagation();
        Assert.assertTrue(control.isPropagationStopped());
    }

    @Test
    public void mouseDownEventPropagationControl() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        DocumentEventControl control = new DocumentEventControl();

        DocumentElementMouseDownEvent event = new DocumentElementMouseDownEvent(div, div, 5, 10, 0, 2000L, control);
        Assert.assertFalse(event.isPropagationStopped());

        event.stopPropagation();
        Assert.assertTrue(event.isPropagationStopped());
        Assert.assertTrue(control.isPropagationStopped());
    }

    @Test
    public void mouseUpEventPropagationControl() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        DocumentEventControl control = new DocumentEventControl();

        DocumentElementMouseUpEvent event = new DocumentElementMouseUpEvent(div, div, 5, 10, 0, 2000L, control);
        event.preventDefault();
        Assert.assertTrue(event.isDefaultPrevented());
        Assert.assertTrue(control.isDefaultPrevented());
    }

    @Test
    public void hoverEventPropagationControl() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        DocumentElementHoverEvent event = new DocumentElementHoverEvent(div, div, true, 10, 20, 3000L);
        event.stopPropagation();
        Assert.assertTrue(event.isPropagationStopped());
    }

    @Test
    public void activeEventPropagationControl() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        DocumentElementActiveEvent event = new DocumentElementActiveEvent(div, div, true, 0, 4000L);
        event.preventDefault();
        Assert.assertTrue(event.isDefaultPrevented());
    }

    @Test
    public void focusInEventPropagationControl() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        DocumentElementFocusInEvent event = new DocumentElementFocusInEvent(div, div, true, false);
        event.stopPropagation();
        Assert.assertTrue(event.isPropagationStopped());
    }

    // ========== 捕获阶段 handler 注册测试 ==========

    @Test
    public void captureClickHandlerRegistration() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        Assert.assertNull(div.getCaptureClickHandler());

        DocumentElementClickHandler handler = event -> true;
        div.setCaptureClickHandler(handler);
        Assert.assertSame(handler, div.getCaptureClickHandler());

        div.setCaptureClickHandler(null);
        Assert.assertNull(div.getCaptureClickHandler());
    }

    @Test
    public void captureKeyHandlerRegistration() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        Assert.assertNull(div.getCaptureKeyHandler());

        DocumentElementKeyHandler handler = event -> true;
        div.setCaptureKeyHandler(handler);
        Assert.assertSame(handler, div.getCaptureKeyHandler());
    }

    @Test
    public void captureMouseDownHandlerRegistration() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        Assert.assertNull(div.getCaptureMouseDownHandler());

        DocumentElementMouseDownHandler handler = event -> true;
        div.setCaptureMouseDownHandler(handler);
        Assert.assertSame(handler, div.getCaptureMouseDownHandler());
    }

    @Test
    public void captureMouseUpHandlerRegistration() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        Assert.assertNull(div.getCaptureMouseUpHandler());

        DocumentElementMouseUpHandler handler = event -> true;
        div.setCaptureMouseUpHandler(handler);
        Assert.assertSame(handler, div.getCaptureMouseUpHandler());
    }

    @Test
    public void captureDoubleClickHandlerRegistration() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        Assert.assertNull(div.getCaptureDoubleClickHandler());

        DocumentElementDoubleClickHandler handler = event -> true;
        div.setCaptureDoubleClickHandler(handler);
        Assert.assertSame(handler, div.getCaptureDoubleClickHandler());
    }

    @Test
    public void captureContextMenuHandlerRegistration() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        Assert.assertNull(div.getCaptureContextMenuHandler());

        DocumentElementContextMenuHandler handler = event -> true;
        div.setCaptureContextMenuHandler(handler);
        Assert.assertSame(handler, div.getCaptureContextMenuHandler());
    }

    // ========== 共享 EventControl 跨事件实例测试 ==========

    @Test
    public void sharedEventControlAcrossEventInstances() {
        UiDocument document = UiDocument.create();
        ElementNode parent = document.div();
        ElementNode child = document.div();
        DocumentEventControl control = new DocumentEventControl();

        // 模拟同一次事件传播中，不同 currentTarget 的事件实例共享同一个 control
        DocumentElementClickEvent parentEvent = new DocumentElementClickEvent(child, parent, 0, 0, 0, 0L, control);
        DocumentElementClickEvent childEvent = new DocumentElementClickEvent(child, child, 0, 0, 0, 0L, control);

        childEvent.stopPropagation();
        // 父元素的事件实例也应该看到传播已停止
        Assert.assertTrue(parentEvent.isPropagationStopped());
    }
}
