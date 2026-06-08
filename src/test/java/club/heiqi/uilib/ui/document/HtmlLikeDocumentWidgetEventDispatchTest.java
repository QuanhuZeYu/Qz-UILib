package club.heiqi.uilib.ui.document;

import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertElementUid;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.DeterministicTextMeasureService;
import club.heiqi.uilib.ui.dom.DocumentElementActiveEvent;
import club.heiqi.uilib.ui.dom.DocumentElementActiveHandler;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementContextMenuEvent;
import club.heiqi.uilib.ui.dom.DocumentElementContextMenuHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDoubleClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementDoubleClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementMouseUpEvent;
import club.heiqi.uilib.ui.dom.DocumentElementMouseUpHandler;
import club.heiqi.uilib.ui.dom.DocumentEventPhase;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationEvent;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `HtmlLikeDocumentWidget` 的鼠标事件分发语义回归测试。
 */
public class HtmlLikeDocumentWidgetEventDispatchTest {

    /**
     * 验证 HTML-like 组件会把 click 事件分发给命中元素并向父元素冒泡。
     */
    @Test
    public void shouldDispatchClickToHitElementAndBubbleToParent() {
        UiDocument document = UiDocument.create();
        final ElementNode root = document.getRootElement();
        final ElementNode child = document.div();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        root.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 12, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 12, 0, 0, 0, 0, 2L));

        Assert.assertEquals(1, clickEvents.size());
        assertElementUid(child, clickEvents.get(0).getTarget());
        assertElementUid(root, clickEvents.get(0).getCurrentTarget());
        Assert.assertEquals(5, clickEvents.get(0).getDocumentX());
        Assert.assertEquals(5, clickEvents.get(0).getDocumentY());
        Assert.assertEquals(0, clickEvents.get(0).getButton());
        Assert.assertEquals(2L, clickEvents.get(0).getTimeNanos());
    }

    /**
     * 验证 raw disabled 表单控件不会通过鼠标路径派发 click。
     */
    @Test
    public void shouldNotDispatchMouseClickForRawDisabledButton() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode button = document.button();
        final int[] clickCount = new int[] { 0 };
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        button.setAttribute("disabled", "true");
        button.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        button.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickCount[0]++;
                return true;
            }
        });
        root.append(button);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 2L));
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 3L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 4L));

        Assert.assertEquals(0, clickCount[0]);
    }

    /**
     * 验证 click 在 AT_TARGET 阶段会先执行 target capture，再执行 target handler；
     * target capture 返回 true 只会阻止祖先冒泡，不会跳过当前 target handler。
     */
    @Test
    public void shouldInvokeTargetClickHandlerAfterTargetCaptureStopsPropagation() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        final List<String> eventLog = new ArrayList<String>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.setCaptureClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                eventLog.add("target-capture:" + event.getEventPhase());
                return true;
            }
        });
        child.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                eventLog.add("target:" + event.getEventPhase());
                return false;
            }
        });
        root.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                eventLog.add("root:" + event.getEventPhase());
                return false;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 2L));

        Assert.assertEquals(2, eventLog.size());
        Assert.assertEquals("target-capture:" + DocumentEventPhase.AT_TARGET, eventLog.get(0));
        Assert.assertEquals("target:" + DocumentEventPhase.AT_TARGET, eventLog.get(1));
    }

    /**
     * 验证 down/up 落在不同后代时，会将最近公共祖先作为 click target。
     */
    @Test
    public void shouldDispatchClickToNearestCommonAncestorWhenPressAndReleaseLandOnDifferentDescendants() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode container = document.div();
        ElementNode first = document.div();
        ElementNode second = document.div();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();
        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(40));
        container.style()
                .setDisplay(UiDisplay.FLEX)
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20));
        first.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        second.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        container.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        container.append(first).append(second);
        root.append(container);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 50, 10, 0, 0, 0, 0, 2L));

        Assert.assertEquals(1, clickEvents.size());
        assertElementUid(container, clickEvents.get(0).getTarget());
        assertElementUid(container, clickEvents.get(0).getCurrentTarget());
        Assert.assertEquals(50, clickEvents.get(0).getDocumentX());
        Assert.assertEquals(10, clickEvents.get(0).getDocumentY());
        Assert.assertEquals(0, clickEvents.get(0).getButton());
        Assert.assertEquals(2L, clickEvents.get(0).getTimeNanos());
    }

    /**
     * 验证 a[href] 在 click 后会触发文档级链接激活回调。
     */
    @Test
    public void shouldDispatchDocumentLinkActivationForAnchorClick() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode link = document.a();
        final List<DocumentLinkActivationEvent> activationEvents = new ArrayList<DocumentLinkActivationEvent>();

        document.setLinkActivationHandler(new DocumentLinkActivationHandler() {
            @Override
            public void onLinkActivated(DocumentLinkActivationEvent event) {
                activationEvents.add(event);
            }
        });
        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(40));
        link.setAttribute("href", "https://example.test/docs");
        link.appendText("Docs");
        root.append(link);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 4, 4, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 4, 4, 0, 0, 0, 0, 2L));

        Assert.assertEquals(1, activationEvents.size());
        Assert.assertEquals("https://example.test/docs", activationEvents.get(0).getHref());
        Assert.assertEquals(link.__getElementUid(), activationEvents.get(0).getElement().__getElementUid());
    }

    /**
     * 验证双击有独立事件，且与单击共存。
     */
    @Test
    public void shouldDispatchDoubleClickAlongsideSingleClicks() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode child = document.div();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();
        final List<DocumentElementDoubleClickEvent> doubleClickEvents = new ArrayList<DocumentElementDoubleClickEvent>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        root.setDoubleClickHandler(new DocumentElementDoubleClickHandler() {
            @Override
            public boolean onDoubleClick(DocumentElementDoubleClickEvent event) {
                doubleClickEvents.add(event);
                return true;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 2L));
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 3L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 4L));

        Assert.assertEquals(2, clickEvents.size());
        Assert.assertEquals(1, doubleClickEvents.size());
        assertElementUid(child, doubleClickEvents.get(0).getTarget());
        assertElementUid(root, doubleClickEvents.get(0).getCurrentTarget());
        Assert.assertEquals(10, doubleClickEvents.get(0).getDocumentX());
        Assert.assertEquals(10, doubleClickEvents.get(0).getDocumentY());
    }

    /**
     * 验证右键菜单事件有独立入口。
     */
    @Test
    public void shouldDispatchContextMenuAsIndependentEvent() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode child = document.div();
        final List<DocumentElementContextMenuEvent> contextMenuEvents = new ArrayList<DocumentElementContextMenuEvent>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        root.setContextMenuHandler(new DocumentElementContextMenuHandler() {
            @Override
            public boolean onContextMenu(DocumentElementContextMenuEvent event) {
                contextMenuEvents.add(event);
                return true;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 1, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 1, 0, 0, 0, 2L));

        Assert.assertEquals(1, contextMenuEvents.size());
        assertElementUid(child, contextMenuEvents.get(0).getTarget());
        assertElementUid(root, contextMenuEvents.get(0).getCurrentTarget());
        Assert.assertEquals(1, contextMenuEvents.get(0).getButton());
    }

    /**
     * 验证右键菜单不会先触发普通 click 行为。
     */
    @Test
    public void shouldNotDispatchClickForContextMenuButton() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode child = document.div();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();
        final List<DocumentElementContextMenuEvent> contextMenuEvents = new ArrayList<DocumentElementContextMenuEvent>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        child.setContextMenuHandler(new DocumentElementContextMenuHandler() {
            @Override
            public boolean onContextMenu(DocumentElementContextMenuEvent event) {
                contextMenuEvents.add(event);
                return true;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 1, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 1, 0, 0, 0, 2L));

        Assert.assertTrue(clickEvents.isEmpty());
        Assert.assertEquals(1, contextMenuEvents.size());
    }

    /**
     * 验证非主按钮不会触发 dblclick。
     */
    @Test
    public void shouldDispatchDoubleClickOnlyForPrimaryButton() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode child = document.div();
        final List<DocumentElementDoubleClickEvent> doubleClickEvents = new ArrayList<DocumentElementDoubleClickEvent>();
        final List<DocumentElementContextMenuEvent> contextMenuEvents = new ArrayList<DocumentElementContextMenuEvent>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.setDoubleClickHandler(new DocumentElementDoubleClickHandler() {
            @Override
            public boolean onDoubleClick(DocumentElementDoubleClickEvent event) {
                doubleClickEvents.add(event);
                return true;
            }
        });
        child.setContextMenuHandler(new DocumentElementContextMenuHandler() {
            @Override
            public boolean onContextMenu(DocumentElementContextMenuEvent event) {
                contextMenuEvents.add(event);
                return true;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 1, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 1, 0, 0, 0, 2L));
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 1, 0, 0, 0, 3L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 1, 0, 0, 0, 4L));

        Assert.assertTrue(doubleClickEvents.isEmpty());
        Assert.assertEquals(2, contextMenuEvents.size());
    }

    /**
     * 验证 dblclick 已接入 capture -> target -> bubble 三阶段链路。
     */
    @Test
    public void shouldDispatchDoubleClickThroughCaptureTargetAndBubble() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode child = document.div();
        final List<String> phases = new ArrayList<String>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        root.setCaptureDoubleClickHandler(new DocumentElementDoubleClickHandler() {
            @Override
            public boolean onDoubleClick(DocumentElementDoubleClickEvent event) {
                phases.add("capture:" + event.getCurrentTarget().getTagName() + ":" + event.getEventPhase());
                return false;
            }
        });
        child.setCaptureDoubleClickHandler(new DocumentElementDoubleClickHandler() {
            @Override
            public boolean onDoubleClick(DocumentElementDoubleClickEvent event) {
                phases.add("target-capture:" + event.getCurrentTarget().getTagName() + ":" + event.getEventPhase());
                return true;
            }
        });
        child.setDoubleClickHandler(new DocumentElementDoubleClickHandler() {
            @Override
            public boolean onDoubleClick(DocumentElementDoubleClickEvent event) {
                phases.add("target:" + event.getCurrentTarget().getTagName() + ":" + event.getEventPhase());
                return false;
            }
        });
        root.setDoubleClickHandler(new DocumentElementDoubleClickHandler() {
            @Override
            public boolean onDoubleClick(DocumentElementDoubleClickEvent event) {
                phases.add("bubble:" + event.getCurrentTarget().getTagName() + ":" + event.getEventPhase());
                return false;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 2L));
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 3L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 4L));

        Assert.assertEquals(3, phases.size());
        Assert.assertEquals("capture:document:CAPTURING", phases.get(0));
        Assert.assertEquals("target-capture:div:AT_TARGET", phases.get(1));
        Assert.assertEquals("target:div:AT_TARGET", phases.get(2));
    }

    /**
     * 验证 contextmenu 已接入 capture -> target -> bubble 三阶段链路。
     */
    @Test
    public void shouldDispatchContextMenuThroughCaptureTargetAndBubble() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode child = document.div();
        final List<String> phases = new ArrayList<String>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        root.setCaptureContextMenuHandler(new DocumentElementContextMenuHandler() {
            @Override
            public boolean onContextMenu(DocumentElementContextMenuEvent event) {
                phases.add("capture:" + event.getCurrentTarget().getTagName() + ":" + event.getEventPhase());
                return false;
            }
        });
        child.setCaptureContextMenuHandler(new DocumentElementContextMenuHandler() {
            @Override
            public boolean onContextMenu(DocumentElementContextMenuEvent event) {
                phases.add("target-capture:" + event.getCurrentTarget().getTagName() + ":" + event.getEventPhase());
                return true;
            }
        });
        child.setContextMenuHandler(new DocumentElementContextMenuHandler() {
            @Override
            public boolean onContextMenu(DocumentElementContextMenuEvent event) {
                phases.add("target:" + event.getCurrentTarget().getTagName() + ":" + event.getEventPhase());
                return false;
            }
        });
        root.setContextMenuHandler(new DocumentElementContextMenuHandler() {
            @Override
            public boolean onContextMenu(DocumentElementContextMenuEvent event) {
                phases.add("bubble:" + event.getCurrentTarget().getTagName() + ":" + event.getEventPhase());
                return false;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 1, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 1, 0, 0, 0, 2L));

        Assert.assertEquals(3, phases.size());
        Assert.assertEquals("capture:document:CAPTURING", phases.get(0));
        Assert.assertEquals("target-capture:div:AT_TARGET", phases.get(1));
        Assert.assertEquals("target:div:AT_TARGET", phases.get(2));
    }

    /**
     * 验证 HTML-like 组件会分发鼠标按下与松开的 active 状态。
     */
    @Test
    public void shouldDispatchActiveStateAroundMousePress() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode input = document.div();
        final List<Boolean> activeEvents = new ArrayList<Boolean>();
        final List<Integer> activeButtons = new ArrayList<Integer>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        input.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        input.setActiveHandler(new DocumentElementActiveHandler() {
            @Override
            public boolean onActiveChanged(DocumentElementActiveEvent event) {
                activeEvents.add(Boolean.valueOf(event.isActive()));
                activeButtons.add(Integer.valueOf(event.getButton()));
                return true;
            }
        });
        root.append(input);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 12, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 12, 0, 0, 0, 0, 2L));

        Assert.assertEquals(Boolean.TRUE, activeEvents.get(0));
        Assert.assertEquals(Boolean.FALSE, activeEvents.get(1));
        Assert.assertEquals(Integer.valueOf(0), activeButtons.get(1));
    }

    /**
     * 验证 active 状态通知不会被目标 handler 返回值截断，祖先仍能同步 :active 状态。
     */
    @Test
    public void shouldNotifyActiveStateAncestorsEvenWhenTargetConsumes() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode parent = document.div();
        ElementNode child = document.div();
        final List<String> activeEvents = new ArrayList<String>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        parent.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.setActiveHandler(new DocumentElementActiveHandler() {
            @Override
            public boolean onActiveChanged(DocumentElementActiveEvent event) {
                activeEvents.add("child:" + event.isActive());
                return true;
            }
        });
        parent.setActiveHandler(new DocumentElementActiveHandler() {
            @Override
            public boolean onActiveChanged(DocumentElementActiveEvent event) {
                activeEvents.add("parent:" + event.isActive());
                return false;
            }
        });
        parent.append(child);
        root.append(parent);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 2L));

        Assert.assertEquals("[child:true, parent:true, child:false, parent:false]", activeEvents.toString());
    }

    /**
     * 验证 hover enter/leave 状态通知不会被目标 handler 返回值截断。
     */
    @Test
    public void shouldNotifyHoverAncestorsEvenWhenTargetConsumes() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode parent = document.div();
        ElementNode child = document.div();
        final List<String> hoverEvents = new ArrayList<String>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        parent.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.setHoverHandler(new DocumentElementHoverHandler() {
            @Override
            public boolean onHoverChanged(DocumentElementHoverEvent event) {
                hoverEvents.add("child:" + event.isHovered());
                return true;
            }
        });
        parent.setHoverHandler(new DocumentElementHoverHandler() {
            @Override
            public boolean onHoverChanged(DocumentElementHoverEvent event) {
                hoverEvents.add("parent:" + event.isHovered());
                return false;
            }
        });
        parent.append(child);
        root.append(parent);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 10, 10, -1, 0, 0, 0, 1L));
        widget.onMouseLeave();

        Assert.assertEquals("[child:true, parent:true, child:false, parent:false]", hoverEvents.toString());
    }

    /**
     * 验证 mouseup 事件会按释放位置命中目标，而不是沿用按下目标。
     */
    @Test
    public void shouldDispatchMouseUpToReleasedElement() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode row = document.div();
        ElementNode first = document.div();
        ElementNode second = document.div();
        final List<DocumentElementMouseUpEvent> mouseUpEvents = new ArrayList<DocumentElementMouseUpEvent>();
        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(40));
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20));
        first.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        second.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        second.setMouseUpHandler(new DocumentElementMouseUpHandler() {
            @Override
            public boolean onMouseUp(DocumentElementMouseUpEvent event) {
                mouseUpEvents.add(event);
                return true;
            }
        });
        row.append(first).append(second);
        root.append(row);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 50, 10, 0, 0, 0, 0, 2L));

        Assert.assertEquals(1, mouseUpEvents.size());
        assertElementUid(second, mouseUpEvents.get(0).getTarget());
        assertElementUid(second, mouseUpEvents.get(0).getCurrentTarget());
        Assert.assertEquals(50, mouseUpEvents.get(0).getDocumentX());
        Assert.assertEquals(10, mouseUpEvents.get(0).getDocumentY());
        Assert.assertEquals(0, mouseUpEvents.get(0).getButton());
        Assert.assertEquals(2L, mouseUpEvents.get(0).getTimeNanos());
    }

    /**
     * 验证鼠标离开组件时会释放按下产生的 active 状态。
     */
    @Test
    public void shouldReleaseActiveStateWhenMouseLeavesWidget() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode input = document.div();
        final List<Boolean> activeEvents = new ArrayList<Boolean>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        input.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        input.setActiveHandler(new DocumentElementActiveHandler() {
            @Override
            public boolean onActiveChanged(DocumentElementActiveEvent event) {
                activeEvents.add(Boolean.valueOf(event.isActive()));
                return true;
            }
        });
        root.append(input);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 12, 0, 0, 0, 0, 1L));
        widget.onMouseLeave();

        Assert.assertEquals(Boolean.TRUE, activeEvents.get(0));
        Assert.assertEquals(Boolean.FALSE, activeEvents.get(1));
    }
}
