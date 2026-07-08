package club.heiqi.uilib.ui.document;

import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertDrawCall;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertElementUid;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.createAutoWidthTextBlock;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.createHudLikeCard;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.createTextBlock;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.findElementContainingDirectText;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.findVisibleElementPoint;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.mouseFrame;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.control.DocumentToggleSwitchControl;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.DeterministicTextMeasureService;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.RecordingUiRenderContext;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementScrollEvent;
import club.heiqi.uilib.ui.dom.DocumentElementScrollHandler;
import club.heiqi.uilib.ui.dom.DocumentElementWheelEvent;
import club.heiqi.uilib.ui.dom.DocumentElementWheelHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.host.UiCursorHost;
import club.heiqi.uilib.ui.input.UiInputRouter;
import club.heiqi.uilib.ui.base.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPointerEvents;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.props.UiVisibility;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.base.values.UiTransform;

/**
 * `HtmlLikeDocumentWidget` 的滚动语义回归测试。
 */
public class HtmlLikeDocumentWidgetScrollTest {

    /**
     * 验证 HTML-like 组件能消费滚轮事件并移动 overflow auto 内容。
     */
    @Test
    public void shouldScrollOverflowAutoContentWithMouseWheel() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        child.style()
                .setHeight(UiStyleLength.px(80))
                .setBackgroundColor(0xFFAA5500);
        root.append(child);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 20);
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);
        Assert.assertEquals(60, widget.getMaxScrollTop(root));

        boolean consumed = widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L));
        RecordingUiRenderContext scrolledRenderContext = new RecordingUiRenderContext();
        widget.render(scrolledRenderContext);

        Assert.assertTrue(consumed);
        Assert.assertEquals(36, widget.getScrollTop(root));
        Assert.assertEquals(3, scrolledRenderContext.drawCalls.size());
        assertDrawCall(scrolledRenderContext.drawCalls.get(0), 5, -29, 85, 51, 0xFFAA5500, 0, 0);
        assertDrawCall(scrolledRenderContext.drawCalls.get(1), 77, 9, 83, 25, 0x663B4A66, 0, 3);
        assertDrawCall(scrolledRenderContext.drawCalls.get(2), 77, 9, 83, 25, 0xDDBCD7FF, 0, 3);
    }

    /**
     * 验证默认滚轮滚动会分发元素 scroll 事件。
     */
    @Test
    public void shouldDispatchScrollEventAfterMouseWheelDefaultScroll() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        final List<String> events = new ArrayList<String>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        child.style().setHeight(UiStyleLength.px(80));
        root.setScrollHandler(new DocumentElementScrollHandler() {
            @Override
            public void onScroll(DocumentElementScrollEvent event) {
                events.add(event.getScrollTop() + ":" + event.getScrollHeight());
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 20);

        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L)));

        Assert.assertEquals("[36:80]", events.toString());
    }

    /**
     * 验证滚动条拖拽改变偏移时会分发元素 scroll 事件。
     */
    @Test
    public void shouldDispatchScrollEventWhenScrollbarDragScrolls() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        final List<Integer> scrollTops = new ArrayList<Integer>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        child.style().setHeight(UiStyleLength.px(200));
        root.setScrollHandler(new DocumentElementScrollHandler() {
            @Override
            public void onScroll(DocumentElementScrollEvent event) {
                scrollTops.add(Integer.valueOf(event.getScrollTop()));
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);
        Assert.assertTrue(widget.getMaxScrollTop(root) > 0);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 75, 5, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 75, 30, -1, 0, 0, 0, 2L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 75, 30, 0, 0, 0, 0, 3L));

        Assert.assertFalse(scrollTops.isEmpty());
        Assert.assertTrue(scrollTops.get(scrollTops.size() - 1).intValue() > 0);
    }

    /**
     * 验证 transform 后的滚动容器只在视觉位置响应滚轮。
     */
    @Test
    public void shouldScrollTransformedOverflowAutoContentAtVisualPosition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode scroller = document.div();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(140))
                .setHeight(UiStyleLength.px(40));
        scroller.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setTransform(UiTransform.translate(40, 0));
        child.style().setHeight(UiStyleLength.px(80));
        scroller.append(child);
        root.append(scroller);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 140, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 140, 40);
        Assert.assertTrue(widget.getMaxScrollTop(scroller) > 0);

        Assert.assertFalse(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L)));
        Assert.assertEquals(0, widget.getScrollTop(scroller));
        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 50, 10, -1, -120, 0,
                0, 2L)));
        Assert.assertTrue(widget.getScrollTop(scroller) > 0);
    }

    /**
     * 验证默认滚轮滚动会跳过 passthrough overlay 并滚动底层容器。
     */
    @Test
    public void shouldSkipPassthroughOverlayForDefaultWheelScroll() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode bottomScroller = document.div();
        ElementNode bottomContent = document.div();
        ElementNode overlayScroller = document.div();
        ElementNode overlayContent = document.div();
        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(80));
        configureSmallScroller(bottomScroller);
        bottomContent.style().setHeight(UiStyleLength.px(100));
        configureOverlayScroller(overlayScroller);
        overlayScroller.setAttribute("data-hit-test-passthrough", "true");
        overlayContent.style().setHeight(UiStyleLength.px(100));
        bottomScroller.append(bottomContent);
        overlayScroller.append(overlayContent);
        root.append(bottomScroller).append(overlayScroller);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 80);

        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L)));

        Assert.assertTrue(widget.getScrollTop(bottomScroller) > 0);
        Assert.assertEquals(0, widget.getScrollTop(overlayScroller));
    }

    /**
     * 验证默认滚轮滚动会跳过 pointer-events:none overlay 并滚动底层容器。
     */
    @Test
    public void shouldSkipPointerEventsNoneOverlayForDefaultWheelScroll() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode bottomScroller = document.div();
        ElementNode bottomContent = document.div();
        ElementNode overlayScroller = document.div();
        ElementNode overlayContent = document.div();
        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(80));
        configureSmallScroller(bottomScroller);
        bottomContent.style().setHeight(UiStyleLength.px(100));
        configureOverlayScroller(overlayScroller);
        overlayScroller.style().setPointerEvents(UiPointerEvents.NONE);
        overlayContent.style().setHeight(UiStyleLength.px(100));
        bottomScroller.append(bottomContent);
        overlayScroller.append(overlayContent);
        root.append(bottomScroller).append(overlayScroller);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 80);

        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L)));

        Assert.assertTrue(widget.getScrollTop(bottomScroller) > 0);
        Assert.assertEquals(0, widget.getScrollTop(overlayScroller));
    }

    /**
     * 验证 wheel 事件会在默认滚动前按 capture、target、bubble 顺序分发。
     */
    @Test
    public void shouldDispatchWheelEventBeforeDefaultScrollInDomOrder() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        final List<String> events = new ArrayList<String>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowY(UiOverflow.AUTO);
        child.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(80));
        root.setCaptureWheelHandler(new DocumentElementWheelHandler() {
            @Override
            public boolean onWheel(DocumentElementWheelEvent event) {
                events.add("root-capture:" + event.getEventPhase());
                return false;
            }
        });
        child.setCaptureWheelHandler(new DocumentElementWheelHandler() {
            @Override
            public boolean onWheel(DocumentElementWheelEvent event) {
                events.add("child-capture:" + event.getEventPhase());
                return false;
            }
        });
        child.setWheelHandler(new DocumentElementWheelHandler() {
            @Override
            public boolean onWheel(DocumentElementWheelEvent event) {
                events.add("child:" + event.getEventPhase() + ":" + event.getDocumentX() + ":"
                        + event.getDocumentY() + ":" + event.getWheelDelta() + ":" + event.getDeltaY());
                return false;
            }
        });
        root.setWheelHandler(new DocumentElementWheelHandler() {
            @Override
            public boolean onWheel(DocumentElementWheelEvent event) {
                events.add("root-bubble:" + event.getEventPhase());
                return false;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 20);

        boolean consumed = widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L));

        Assert.assertTrue(consumed);
        Assert.assertEquals(36, widget.getScrollTop(root));
        Assert.assertEquals("[root-capture:CAPTURING, child-capture:AT_TARGET, child:AT_TARGET:10:10:-120:120, "
                + "root-bubble:BUBBLING]", events.toString());
    }

    /**
     * 验证 wheel handler 返回 true 只停止传播，不会隐式取消默认滚动。
     */
    @Test
    public void shouldKeepDefaultWheelScrollWhenHandlerOnlyStopsPropagation() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        final List<String> events = new ArrayList<String>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowY(UiOverflow.AUTO);
        child.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(80));
        child.setWheelHandler(new DocumentElementWheelHandler() {
            @Override
            public boolean onWheel(DocumentElementWheelEvent event) {
                events.add("child");
                return true;
            }
        });
        root.setWheelHandler(new DocumentElementWheelHandler() {
            @Override
            public boolean onWheel(DocumentElementWheelEvent event) {
                events.add("root");
                return false;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 20);

        boolean consumed = widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L));

        Assert.assertTrue(consumed);
        Assert.assertEquals(36, widget.getScrollTop(root));
        Assert.assertEquals("[child]", events.toString());
    }

    /**
     * 验证 wheel 事件调用 preventDefault 后会阻止默认滚动。
     */
    @Test
    public void shouldPreventDefaultWheelScrollFromWheelEvent() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowY(UiOverflow.AUTO);
        child.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(80));
        child.setWheelHandler(new DocumentElementWheelHandler() {
            @Override
            public boolean onWheel(DocumentElementWheelEvent event) {
                event.preventDefault();
                return false;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 20);

        boolean consumed = widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L));

        Assert.assertTrue(consumed);
        Assert.assertEquals(0, widget.getScrollTop(root));
    }

    /**
     * 验证 HUD 风格固定面板在鼠标命中后代内容区时，仍会滚动祖先 scroll host。
     */
    @Test
    public void shouldScrollHudLikePanelContentWhenWheelOnDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode header = document.div();
        ElementNode contentRoot = document.div();
        ElementNode card = null;

        root.style()
                .setWidth(UiStyleLength.px(320))
                .setHeight(UiStyleLength.px(540));
        panel.style()
                .setWidth(UiStyleLength.px(248))
                .setHeight(UiStyleLength.px(420))
                .setPadding(UiStyleLength.px(12));
        header.style()
                .setHeight(UiStyleLength.px(40));
        header.appendText("HUD Header");
        contentRoot.style()
                .setHeight(UiStyleLength.px(360))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        contentRoot.append(createTextBlock(document, "页面概览：用于复现 HUD 浮窗正文区内部滚动。"));
        contentRoot.append(createTextBlock(document, "摘要：滚轮命中后代卡片正文时，祖先 scroll host 仍应滚动。"));
        for (int index = 1; index <= 6; index++) {
            card = createHudLikeCard(document, index);
            contentRoot.append(card);
        }
        panel.append(header).append(contentRoot);
        root.append(panel);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 320, 540,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 320, 540);
        widget.render(new RecordingUiRenderContext());

        Assert.assertTrue(widget.getMaxScrollTop(contentRoot) > 0);
        Assert.assertNotNull(card);
        int scrollX = 40;
        int scrollY = 220;
        Assert.assertTrue(widget.findElementAt(scrollX, scrollY) != null);

        boolean consumed = widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, scrollX, scrollY, -1,
                -120, 0, 0, 1L));

        Assert.assertTrue(consumed);
        Assert.assertTrue(widget.getScrollTop(contentRoot) > 0);
    }

    /**
     * 验证当鼠标直接命中文本行时，祖先 scroll host 仍会消费滚轮并滚动。
     */
    @Test
    public void shouldScrollAncestorHostWhenPointerHitsNestedTextRun() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode scrollHost = document.div();
        ElementNode card = document.div();
        ElementNode body = document.div();

        root.style()
                .setWidth(UiStyleLength.px(280))
                .setHeight(UiStyleLength.px(220));
        scrollHost.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(140))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        card.style()
                .setPadding(UiStyleLength.px(8))
                .setMargin(UiStyleLength.px(6))
                .setBorderWidth(UiStyleLength.px(1));
        body.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F));
        body.appendText("这是用于验证祖先滚动宿主命中文本行时仍可滚动的长文本内容，必须在较窄宽度下发生多行换行，"
                + "这样测试才能命中真实 text run，而不是只命中空白区域。"
                + "继续补充第二段中文说明，确保滚动区域高度显著超过视口高度。"
                + "继续补充第三段中文说明，模拟 HUD 说明文案与正文卡片。"
                + "继续补充第四段中文说明，确保内部区域形成真实滚动。"
                + "继续补充第五段中文说明，避免只靠边框高度通过测试。");
        card.append(createTextBlock(document, "卡片标题：滚动祖先宿主命中测试"));
        card.append(body);
        scrollHost.append(card);
        root.append(scrollHost);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 280, 220,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 280, 220);
        widget.render(new RecordingUiRenderContext());

        Assert.assertTrue(widget.getMaxScrollTop(scrollHost) > 0);
        Assert.assertNotNull(widget.findElementAt(24, 52));

        boolean consumed = widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 24, 52, -1, -120, 0,
                0, 1L));

        Assert.assertTrue(consumed);
        Assert.assertTrue(widget.getScrollTop(scrollHost) > 0);
    }

    /**
     * 验证 fixed HUD 面板中的内部 scroll host 在命中后代正文时仍可滚动。
     */
    @Test
    public void shouldScrollFixedHudLikePanelContentWhenWheelOnDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode title = document.div();
        ElementNode diagnostics = document.div();
        ElementNode contentRoot = document.div();

        root.style()
                .setWidth(UiStyleLength.px(320))
                .setHeight(UiStyleLength.px(240));
        panel.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(0))
                .setTop(UiStyleLength.px(0))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.props.UiFlexDirection.COLUMN)
                .setWidth(UiStyleLength.px(248))
                .setHeight(UiStyleLength.px(232))
                .setPadding(UiStyleLength.px(12));
        title.appendText("INTERACTIVE HUD");
        diagnostics.appendText("阶段: 有范围但未命中宿主");
        contentRoot.style()
                .setHeight(UiStyleLength.px(118))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        contentRoot.append(createTextBlock(document, "容器界面上方可见。点击次数 0，备注：把鼠标移到背包界面后尝试编辑我。"));
        contentRoot.append(createTextBlock(document, "底部提示标记：保留"));
        contentRoot.append(createTextBlock(document, "把鼠标移到背包界面后尝试编辑我 123"));
        for (int index = 1; index <= 8; index++) {
            contentRoot.append(createTextBlock(document,
                    "滚轮停在这里可查看内部内容，第 " + index + " 条示例说明。继续补充中文描述，确保形成明显纵向溢出。"));
        }
        panel.append(title).append(diagnostics).append(contentRoot);
        root.append(panel);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 320, 240,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 320, 240);
        widget.render(new RecordingUiRenderContext());

        Assert.assertTrue(widget.getMaxScrollTop(contentRoot) > 0);
        Assert.assertNotNull(widget.findElementAt(20, 140));

        boolean consumed = widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 20, 140, -1, -120, 0,
                0, 1L));

        Assert.assertTrue(consumed);
        Assert.assertTrue(widget.getScrollTop(contentRoot) > 0);
    }

    /**
     * 验证当前 HUD demo 等价控件树在正文文本区与输入框区域都能滚动内部 scroll host。
     */
    @Test
    public void shouldScrollCurrentHudDemoLikeTreeOnTextAndInputAreas() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode dragBar = document.div();
        ElementNode heroCard = document.div();
        ElementNode controlCard = document.div();
        ElementNode scrollContent = document.div();
        ElementNode contentBody = document.div();

        root.style()
                .setWidth(UiStyleLength.px(2048))
                .setHeight(UiStyleLength.px(1152));
        panel.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(1648))
                .setTop(UiStyleLength.px(18))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.props.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.props.UiAlignItems.START)
                .setWidth(UiStyleLength.px(360))
                .setHeight(UiStyleLength.px(368))
                .setPadding(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(8));
        dragBar.appendText("HUD 工具浮窗 · 拖住这里移动");

        heroCard.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(4));
        heroCard.append(createAutoWidthTextBlock(document, "INTERACTIVE HUD"));
        heroCard.append(createAutoWidthTextBlock(document, "容器界面可交互"));
        heroCard.append(createAutoWidthTextBlock(document, "主浮窗调试台"));
        heroCard.append(createAutoWidthTextBlock(document,
                "把工具浮窗停在背包右上区域，用于核对 HUD 层可见性、输入接管与滚轮状态。"));

        controlCard.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(6));
        controlCard.append(createAutoWidthTextBlock(document, "调试开关"));

        ElementNode debugToggleCard = document.div();
        debugToggleCard.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(6));
        debugToggleCard.append(createAutoWidthTextBlock(document, "显示 HUD 调试信息"));
        ElementNode debugToggleHost = document.div();
        debugToggleHost.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.auto());
        debugToggleHost.append(new DocumentToggleSwitchControl(document).setToggled(true).getElement());
        debugToggleCard.append(debugToggleHost);
        controlCard.append(debugToggleCard);
        controlCard.append(createAutoWidthTextBlock(document, "底部提示标记：保留"));

        scrollContent.style()
                .setFlexGrow(1.0F)
                .setWidth(UiStyleLength.percent(1.0F))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        contentBody.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.props.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.props.UiAlignItems.START)
                .setWidth(UiStyleLength.auto())
                .setRowGap(UiStyleLength.px(6));
        scrollContent.append(contentBody);

        ElementNode overviewCard = document.div();
        overviewCard.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(3));
        overviewCard.append(createAutoWidthTextBlock(document, "会话概览"));
        overviewCard.append(createAutoWidthTextBlock(document, "容器界面上方可见。点击次数 0，备注：把鼠标移到背包界面后尝试编辑我。"));
        contentBody.append(overviewCard);

        ElementNode noteCard = document.div();
        noteCard.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(4));
        noteCard.append(createAutoWidthTextBlock(document, "容器备注"));

        DocumentTextInputControl input = new DocumentTextInputControl(document)
                .setPlaceholder("在容器界面中输入备注")
                .setText("把鼠标移到背包界面后尝试编辑我");
        input.getElement().style()
                .setDisplay(UiDisplay.BLOCK)
                .setMargin(UiStyleLength.px(0));
        noteCard.append(input.getElement());

        DocumentButtonControl button = new DocumentButtonControl(document, "记录一次点击");
        button.getElement().style()
                .setDisplay(UiDisplay.BLOCK)
                .setMargin(UiStyleLength.px(0));
        noteCard.append(button.getElement());
        contentBody.append(noteCard);

        ElementNode debugCard = document.div();
        debugCard.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(3));
        debugCard.append(createAutoWidthTextBlock(document, "HUD DEBUG"));
        debugCard.append(createAutoWidthTextBlock(document, "滚轮监控：有范围但未命中宿主。偏移 0 / 439。"));
        contentBody.append(debugCard);

        ElementNode tipsCard = document.div();
        tipsCard.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(3));
        tipsCard.append(createAutoWidthTextBlock(document, "操作建议"));

        for (int index = 1; index <= 8; index++) {
            tipsCard.append(createAutoWidthTextBlock(document,
                    "滚轮停在这里可查看内部内容，第 " + index + " 条示例说明。继续补充中文描述，确保形成明显纵向溢出。"));
        }
        contentBody.append(tipsCard);

        panel.append(dragBar).append(heroCard).append(controlCard).append(scrollContent);
        root.append(panel);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 2048, 1152,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 2048, 1152);
        widget.render(new RecordingUiRenderContext());

        Assert.assertTrue(widget.getMaxScrollTop(scrollContent) > 0);
        int[] textPoint = findVisibleElementPoint(widget, scrollContent,
                findElementContainingDirectText(widget, "会话概览"));
        int[] inputPoint = findVisibleElementPoint(widget, scrollContent, input.getElement());
        Assert.assertNotNull(widget.findElementAt(textPoint[0], textPoint[1]));
        Assert.assertNotNull(widget.findElementAt(inputPoint[0], inputPoint[1]));

        boolean consumedOnText = widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, textPoint[0],
                textPoint[1], -1, -120, 0, 0, 1L));
        Assert.assertTrue(consumedOnText);
        Assert.assertTrue(widget.getScrollTop(scrollContent) > 0);

        inputPoint = findVisibleElementPoint(widget, scrollContent, input.getElement());
        boolean consumedOnInput = widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, inputPoint[0],
                inputPoint[1], -1, -120, 0, 0, 2L));
        Assert.assertTrue(consumedOnInput);
        Assert.assertTrue(widget.getScrollTop(scrollContent) > 0);
    }

    /**
     * 验证根视口滚动模式会让根元素承载页面级 overflow auto。
     */
    @Test
    public void shouldUseRootElementAsViewportScrollHost() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        child.style()
                .setHeight(UiStyleLength.px(96))
                .setBackgroundColor(0xFF225577);
        root.append(child);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.setViewportRootScrollingEnabled(true);
        widget.applyLayoutBounds(0, 0, 80, 40);

        Assert.assertTrue(widget.isViewportRootScrollingEnabled());
        Assert.assertEquals(56, widget.getMaxScrollTop(root));
        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L)));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertEquals(36, widget.getScrollTop(root));
        Assert.assertEquals(3, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 0, -36, 80, 60, 0xFF225577, 0, 0);
        assertDrawCall(renderContext.drawCalls.get(1), 72, 2, 78, 38, 0x663B4A66, 0, 3);
        assertDrawCall(renderContext.drawCalls.get(2), 72, 10, 78, 34, 0xDDBCD7FF, 0, 3);
    }

    /**
     * 验证 HTML-like 根滚动条滑块可以通过真实输入路由拖拽滚动。
     */
    @Test
    public void shouldDragRootScrollbarThumbThroughInputRouter() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setOverflowY(UiOverflow.AUTO);
        child.style().setHeight(UiStyleLength.px(120));
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);
        Assert.assertEquals(80, widget.getMaxScrollTop(root));

        UiInputRouter router = new UiInputRouter();
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 75, 5, 0, 0, 0, 0,
                1L)));
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.MOVE, 75, 17, -1, 0, 0, 12,
                2L)));
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 75, 17, 0, 0, 0, 0,
                3L)));

        Assert.assertEquals(80, widget.getScrollTop(root));
    }

    /**
     * 验证点击滚动条轨道会滚动且不会透传为元素 click。
     */
    @Test
    public void shouldHandleScrollbarTrackClickWithoutDispatchingElementClick() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setOverflowY(UiOverflow.AUTO);
        root.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        child.style().setHeight(UiStyleLength.px(120));
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        UiInputRouter router = new UiInputRouter();
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 75, 34, 0, 0, 0, 0,
                1L)));
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 75, 34, 0, 0, 0, 0,
                2L)));

        Assert.assertTrue(widget.getScrollTop(root) > 0);
        Assert.assertTrue(clickEvents.isEmpty());
    }

    /**
     * 验证 scrollbar mousedown 不会被 suppressed overlay 捕获。
     */
    @Test
    public void shouldSkipSuppressedOverlayScrollbarOnMouseDown() {
        assertSuppressedOverlayScrollbarMouseDownIsSkipped("data-hit-test-hidden", true, false);
        assertSuppressedOverlayScrollbarMouseDownIsSkipped("data-hit-test-passthrough", true, false);
        assertSuppressedOverlayScrollbarMouseDownIsSkipped(null, false, true);
    }

    /**
     * 验证当前可见的内部滚动块滚动条也可以拖拽。
     */
    @Test
    public void shouldDragVisibleNestedScrollbarThumbThroughInputRouter() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode scroller = document.div();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(60));
        scroller.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setOverflowY(UiOverflow.AUTO);
        child.style().setHeight(UiStyleLength.px(120));
        scroller.append(child);
        root.append(scroller);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 100, 60,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 100, 60);
        Assert.assertEquals(80, widget.getMaxScrollTop(scroller));

        UiInputRouter router = new UiInputRouter();
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0, 0,
                1L)));
        Assert.assertEquals(36, widget.getScrollTop(scroller));
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 75, 10, 0, 0, 0, 0,
                2L)));
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.MOVE, 75, 17, -1, 0, 0, 7,
                3L)));
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 75, 17, 0, 0, 0, 0,
                4L)));

        Assert.assertEquals(80, widget.getScrollTop(scroller));
    }

    /**
     * 验证点击 HTML-like 子元素不会触发根视口滚动偏移。
     */
    @Test
    public void shouldKeepViewportRootScrollStableWhenFocusableHtmlElementIsClicked() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode focusableElement = document.div();
        ElementNode filler = document.div();
        root.style()
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        focusableElement.style().setHeight(UiStyleLength.px(24));
        focusableElement.setFocusable(true);
        filler.style().setHeight(UiStyleLength.px(160));
        root.append(focusableElement).append(filler);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.setViewportRootScrollingEnabled(true);
        widget.applyLayoutBounds(0, 0, 80, 40);
        widget.render(new RecordingUiRenderContext());

        Assert.assertTrue(widget.getMaxScrollTop(root) > 0);

        UiInputRouter router = new UiInputRouter();
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0,
                1L)));
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0,
                2L)));

        Assert.assertEquals(0, widget.getScrollTop(root));
        assertElementUid(focusableElement, widget.getFocusedElement());
    }

    /**
     * 验证滚动后的命中测试会使用内容偏移后的元素位置。
     */
    @Test
    public void shouldHitTestScrolledContentAtVisualPosition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowY(UiOverflow.AUTO);
        first.style().setHeight(UiStyleLength.px(40));
        second.style().setHeight(UiStyleLength.px(40));
        root.append(first).append(second);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 20);

        assertElementUid(first, widget.findElementAt(10, 10));
        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L)));

        assertElementUid(second, widget.findElementAt(10, 10));
    }

    /**
     * 验证滚轮滚动后 hover 会按当前鼠标位置重新切换。
     */
    @Test
    public void shouldRefreshHoverAfterMouseWheelScrollMovesContent() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();
        final List<String> hoverEvents = new ArrayList<String>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowY(UiOverflow.AUTO);
        first.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        second.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        first.setHoverHandler(new DocumentElementHoverHandler() {
            @Override
            public boolean onHoverChanged(DocumentElementHoverEvent event) {
                hoverEvents.add("first:" + event.isHovered() + ":" + event.getDocumentX() + ":"
                        + event.getDocumentY());
                return true;
            }
        });
        second.setHoverHandler(new DocumentElementHoverHandler() {
            @Override
            public boolean onHoverChanged(DocumentElementHoverEvent event) {
                hoverEvents.add("second:" + event.isHovered() + ":" + event.getDocumentX() + ":"
                        + event.getDocumentY());
                return true;
            }
        });
        root.append(first).append(second);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 20);

        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 10, 10, -1, 0, 0, 0, 1L));
        Assert.assertEquals(1, hoverEvents.size());
        Assert.assertEquals("first:true:10:10", hoverEvents.get(0));

        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 2L)));
        Assert.assertEquals(3, hoverEvents.size());
        Assert.assertEquals("first:false:10:10", hoverEvents.get(1));
        Assert.assertEquals("second:true:10:10", hoverEvents.get(2));
    }

    /**
     * 验证滚轮滚动重命中后 cursor effect 据新 hovered 元素派生新光标，
     * 钉死 scroll→影子→signal→cursor 同步闭环。
     */
    @Test
    public void shouldDeriveCursorFromRehoveredElementAfterMouseWheelScroll() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowY(UiOverflow.AUTO);
        first.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        second.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setCursor(UiCursor.POINTER);
        root.append(first).append(second);

        RecordingCursorHost cursorHost = new RecordingCursorHost();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService()).setCursorHost(cursorHost);
        widget.applyLayoutBounds(0, 0, 80, 20);

        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 10, 10, -1, 0, 0, 0, 1L));
        widget.flushInteractionFrameForTest();
        Assert.assertEquals(UiCursor.DEFAULT, cursorHost.getLatestCursor());

        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 2L)));
        widget.flushInteractionFrameForTest();
        Assert.assertEquals(UiCursor.POINTER, cursorHost.getLatestCursor());
    }

    /**
     * 验证 ElementNode 公开 scrollTo API 会夹取滚动偏移并拒绝不可滚元素。
     */
    @Test
    public void shouldScrollElementToOffsetThroughElementNodeApi() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        ElementNode detached = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setOverflowY(UiOverflow.AUTO);
        child.style().setHeight(UiStyleLength.px(120));
        detached.style().setHeight(UiStyleLength.px(120));
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        Assert.assertTrue(root.scrollTo(0, 30));
        Assert.assertEquals(30, widget.getScrollTop(root));
        Assert.assertEquals(0, widget.getScrollLeft(root));

        Assert.assertTrue(root.scrollTo(0, 999));
        Assert.assertEquals(80, widget.getScrollTop(root));
        Assert.assertFalse(child.scrollTo(0, 10));
        Assert.assertFalse(detached.scrollTo(0, 10));
    }

    /**
     * 验证 ElementNode 公开 scrollIntoView API 会滚动最近可滚祖先。
     */
    @Test
    public void shouldScrollElementIntoViewThroughElementNodeApi() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode spacer = document.div();
        ElementNode target = document.div();
        ElementNode hidden = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setOverflowY(UiOverflow.AUTO);
        first.style().setHeight(UiStyleLength.px(20));
        spacer.style().setHeight(UiStyleLength.px(48));
        target.style().setHeight(UiStyleLength.px(20));
        hidden.style()
                .setHeight(UiStyleLength.px(20))
                .setVisibility(UiVisibility.HIDDEN);
        root.append(first).append(spacer).append(target).append(hidden);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        Assert.assertTrue(target.scrollIntoView());
        Assert.assertEquals(48, widget.getScrollTop(root));
        Assert.assertTrue(target.scrollIntoView());
        Assert.assertEquals(48, widget.getScrollTop(root));
        Assert.assertFalse(hidden.scrollIntoView());
    }

    /**
     * 方案2 核心守护：普通滚动容器（无 positioned 后代，eligible）滚动后绘制命令不重建，仅靠回放期偏移栈
     * 实时叠加偏移得到正确视觉位置。断言：① 滚动后 paintCacheGeneration 不变（命令未重建）；② 内容背景
     * 仍位移到正确屏幕坐标（与重建路径逐像素一致）。
     */
    @Test
    public void shouldNotRebuildPaintCommandsWhenScrollingEligibleContainer() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        child.style()
                .setHeight(UiStyleLength.px(80))
                .setBackgroundColor(0xFFAA5500);
        root.append(child);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 20);
        widget.render(new RecordingUiRenderContext());
        int generationBeforeScroll = widget.getPaintCacheGenerationForDiagnostics();

        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L)));
        RecordingUiRenderContext scrolledRenderContext = new RecordingUiRenderContext();
        widget.render(scrolledRenderContext);

        // 命令未重建：滚动只改回放参数。
        Assert.assertEquals(generationBeforeScroll, widget.getPaintCacheGenerationForDiagnostics());
        // 但视觉位置已正确位移（与重建路径逐像素一致：scrollTop=36 => 内容上移 36）。
        Assert.assertEquals(36, widget.getScrollTop(root));
        assertDrawCall(scrolledRenderContext.drawCalls.get(0), 5, -29, 85, 51, 0xFFAA5500, 0, 0);
    }

    /**
     * 方案2 守护：免重建滚动后 hit-test 仍按当前滚动偏移定位元素。命令免重建不代表 hit-test 失准——
     * hit-test 走 scrollState 实时坐标，与绘制命令缓存解耦。断言滚动后某文档坐标命中的元素随滚动改变。
     */
    @Test
    public void shouldKeepHitTestAccurateAfterRebuildFreeScroll() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode top = document.div();
        ElementNode bottom = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        top.style().setHeight(UiStyleLength.px(20));
        bottom.style().setHeight(UiStyleLength.px(60));
        root.append(top).append(bottom);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 20);
        widget.render(new RecordingUiRenderContext());

        // 滚动前：视口顶部 (10,2) 命中 top 子元素。
        ElementNode beforeScroll = widget.findElementAt(10, 2);
        Assert.assertSame(top, beforeScroll);

        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L)));
        widget.render(new RecordingUiRenderContext());

        // 滚动后：top 已被滚出视口，同一屏幕点命中 bottom 子元素——hit-test 跟随滚动实时刷新。
        ElementNode afterScroll = widget.findElementAt(10, 2);
        Assert.assertSame(bottom, afterScroll);
    }

    /**
     * 方案2 守护：含 sticky 后代的滚动容器（ineligible，被登记进滚动依赖快照）滚动时回退重建命令。
     * 这类容器内容坐标已烘焙构建期 scroll、又不发 SCROLL_OFFSET 作用域，免重建会脏渲染，故必须重建。
     * 断言滚动后 paintCacheGeneration 增加（命令已重建）。
     */
    @Test
    public void shouldRebuildPaintCommandsWhenScrollingContainerWithStickyDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode sticky = document.div();
        ElementNode filler = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        sticky.style()
                .setHeight(UiStyleLength.px(10))
                .setPosition(UiPosition.STICKY)
                .setTop(UiStyleLength.px(0));
        filler.style().setHeight(UiStyleLength.px(80));
        root.append(sticky).append(filler);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 20);
        widget.render(new RecordingUiRenderContext());
        int generationBeforeScroll = widget.getPaintCacheGenerationForDiagnostics();

        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L)));
        widget.render(new RecordingUiRenderContext());

        Assert.assertTrue(widget.getScrollTop(root) > 0);
        // sticky 后代使容器 ineligible，滚动依赖快照非空且偏移变化 => 命令重建。
        Assert.assertTrue("含 sticky 后代的滚动容器滚动应触发重建",
                widget.getPaintCacheGenerationForDiagnostics() > generationBeforeScroll);
    }

    /**
     * 方向F 治本守护：含 position:relative 后代的滚动根容器（rootBox 收集自身 positioned 后代）滚动时免重建。
     * relative 不脱流、随内容线性滚动，落在 appendBoxCommands 为 positioned 阶段补包的 SCROLL_OFFSET 作用域内，
     * 回放期实时叠加 -scroll 即可，无需重建。断言滚动后 paintCacheGeneration 不变，且 relative 后代视觉位置随
     * 内容上移 scrollTop（与重建路径逐像素一致）。这是 ModernConfig 含 select（触发器 position:relative）页面
     * 滚动免重建的核心收益场景。
     */
    @Test
    public void shouldNotRebuildPaintCommandsWhenScrollingContainerWithRelativeDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode filler = document.div();
        ElementNode relativeChild = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        filler.style().setHeight(UiStyleLength.px(40));
        relativeChild.style()
                .setHeight(UiStyleLength.px(40))
                .setPosition(UiPosition.RELATIVE)
                .setBackgroundColor(0xFF3366CC);
        root.append(filler).append(relativeChild);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 20);
        RecordingUiRenderContext beforeScrollContext = new RecordingUiRenderContext();
        widget.render(beforeScrollContext);
        int generationBeforeScroll = widget.getPaintCacheGenerationForDiagnostics();
        int relativeTopBeforeScroll = findFillTop(beforeScrollContext, 0xFF3366CC);

        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L)));
        RecordingUiRenderContext scrolledContext = new RecordingUiRenderContext();
        widget.render(scrolledContext);
        int scrollTop = widget.getScrollTop(root);

        // relative 后代是唯一 positioned 后代，rootBox 在自身作用域收集它 => 容器 eligible => 免重建。
        Assert.assertTrue("滚动应实际发生", scrollTop > 0);
        Assert.assertEquals("含 relative 后代的滚动根容器滚动应免重建",
                generationBeforeScroll, widget.getPaintCacheGenerationForDiagnostics());
        // relative 后代视觉位置随内容线性上移 scrollTop（回放期叠加 -scroll 生效，逐像素正确）。
        int relativeTopAfterScroll = findFillTop(scrolledContext, 0xFF3366CC);
        Assert.assertEquals("relative 后代应随内容上移 scrollTop",
                relativeTopBeforeScroll - scrollTop, relativeTopAfterScroll);
    }

    /**
     * 方向F 治本守护：含 position:absolute 后代的滚动容器仍 ineligible、滚动重建。absolute 的 containing block
     * 可能在滚动容器外，滚动语义与线性叠加 -scroll 不一致，保守回退重建避免错位。断言滚动后 paintCacheGeneration 增加。
     */
    @Test
    public void shouldRebuildPaintCommandsWhenScrollingContainerWithAbsoluteDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode absoluteChild = document.div();
        ElementNode filler = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        absoluteChild.style()
                .setHeight(UiStyleLength.px(10))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(0));
        filler.style().setHeight(UiStyleLength.px(80));
        root.append(absoluteChild).append(filler);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 20);
        widget.render(new RecordingUiRenderContext());
        int generationBeforeScroll = widget.getPaintCacheGenerationForDiagnostics();

        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L)));
        widget.render(new RecordingUiRenderContext());

        Assert.assertTrue(widget.getScrollTop(root) > 0);
        Assert.assertTrue("含 absolute 后代的滚动容器滚动应触发重建",
                widget.getPaintCacheGenerationForDiagnostics() > generationBeforeScroll);
    }

    /**
     * 方向F 治本守护：非 stacking context 的嵌套滚动容器（static + overflow:auto，走 else 分支、不收集自身
     * positioned 后代）即使只含 relative 后代也仍 ineligible、滚动重建。因为它的 relative 后代会被祖先 stacking
     * context 收集、脱离本容器的 SCROLL_OFFSET 作用域，免重建会错位，故 eligibility 第二判据要求子树完全无 positioned
     * 后代。断言滚动该嵌套容器后命令重建。
     */
    @Test
    public void shouldRebuildPaintCommandsWhenScrollingNonStackingContainerWithRelativeDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode nestedScroller = document.div();
        ElementNode filler = document.div();
        ElementNode relativeChild = document.div();
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(80));
        // 嵌套滚动容器：static + overflow:auto，不建立 stacking context => 走 else 分支、不收集自身 positioned 后代。
        nestedScroller.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        filler.style().setHeight(UiStyleLength.px(40));
        relativeChild.style()
                .setHeight(UiStyleLength.px(40))
                .setPosition(UiPosition.RELATIVE);
        nestedScroller.append(filler).append(relativeChild);
        root.append(nestedScroller);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 80);
        widget.render(new RecordingUiRenderContext());
        int generationBeforeScroll = widget.getPaintCacheGenerationForDiagnostics();

        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L)));
        widget.render(new RecordingUiRenderContext());

        Assert.assertTrue(widget.getScrollTop(nestedScroller) > 0);
        Assert.assertTrue("非 stacking context 嵌套滚动容器含 relative 后代仍应回退重建",
                widget.getPaintCacheGenerationForDiagnostics() > generationBeforeScroll);
    }

    /**
     * 在记录的绘制调用中查找首个指定填充色的命令顶边（文档/屏幕坐标）。
     *
     * @param context 渲染记录上下文
     * @param fillColor 目标填充色
     * @return 首个匹配命令的 top 值
     */
    private static int findFillTop(RecordingUiRenderContext context, int fillColor) {
        for (HtmlLikeDocumentWidgetTestSupport.DrawCall drawCall : context.drawCalls) {
            if (drawCall.surfaceStyle.fillColor == fillColor) {
                return drawCall.top;
            }
        }
        throw new AssertionError("未找到填充色为 " + Integer.toHexString(fillColor) + " 的绘制命令");
    }

    private static void configureSmallScroller(ElementNode scroller) {
        scroller.style()
                .setWidth(UiStyleLength.px(70))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
    }

    private static void configureOverlayScroller(ElementNode scroller) {
        configureSmallScroller(scroller);
        scroller.style()
                .setPosition(UiPosition.FIXED)
                .setTop(UiStyleLength.px(0))
                .setLeft(UiStyleLength.px(0))
                .setZIndex(10);
    }

    private static void assertSuppressedOverlayScrollbarMouseDownIsSkipped(String attributeName, boolean setAttribute,
            boolean pointerEventsNone) {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode bottomScroller = document.div();
        ElementNode bottomContent = document.div();
        ElementNode overlayScroller = document.div();
        ElementNode overlayContent = document.div();
        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(80));
        configureSmallScroller(bottomScroller);
        bottomContent.style().setHeight(UiStyleLength.px(100));
        configureOverlayScroller(overlayScroller);
        overlayContent.style().setHeight(UiStyleLength.px(100));
        if (setAttribute) {
            overlayScroller.setAttribute(attributeName, "true");
        }
        if (pointerEventsNone) {
            overlayScroller.style().setPointerEvents(UiPointerEvents.NONE);
        }
        bottomScroller.append(bottomContent);
        overlayScroller.append(overlayContent);
        root.append(bottomScroller).append(overlayScroller);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 80);
        widget.render(new RecordingUiRenderContext());

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 65, 18, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 65, 19, -1, 0, 0, 1, 2L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 65, 19, 0, 0, 0, 0, 3L));

        Assert.assertEquals(0, widget.getScrollTop(overlayScroller));
    }

    /**
     * 记录光标变更的测试宿主。
     */
    private static final class RecordingCursorHost implements UiCursorHost {

        private final List<UiCursor> appliedCursors = new ArrayList<UiCursor>();

        @Override
        public void applyCursor(UiCursor cursor) {
            appliedCursors.add(cursor == null ? UiCursor.DEFAULT : cursor);
        }

        private UiCursor getLatestCursor() {
            return appliedCursors.isEmpty() ? UiCursor.DEFAULT : appliedCursors.get(appliedCursors.size() - 1);
        }
    }
}
