package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import club.heiqi.uilib.ui.event.UiKeyCodes;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.base.values.UiTransform;
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
     * 验证展开的下拉选项会截获点击，不会穿透到视觉下方的按钮。
     */
    @Test
    public void shouldNotClickUnderlyingButtonWhenOptionPopupOverlapsIt() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode clippedPanel = document.div();
        final int[] buttonClicks = new int[1];
        DocumentSelectControl selectControl = new DocumentSelectControl(document, "A", "B", "C");
        DocumentButtonControl buttonControl = new DocumentButtonControl(document, "Under");

        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(180));
        clippedPanel.style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(40))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        selectControl.getElement().style().setWidth(UiStyleLength.px(180));
        buttonControl.getElement().style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(40));
        buttonControl.setActionHandler(event -> buttonClicks[0]++);
        clippedPanel.append(selectControl.getElement());
        root.append(clippedPanel);
        root.append(buttonControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 180,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 180);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 20, 12, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 20, 12, 0, 0, 0, 0, 2L));
        ElementNode popup = findListboxElement(root);
        Assert.assertNotNull(popup);
        Assert.assertSame("展开面板仍应保持 select 逻辑 DOM 归属",
                selectControl.getElement(), popup.getParent());
        Assert.assertTrue("展开面板应由文档运行时注册为 top-layer",
                document.__isTopLayerElement(popup));
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 20, 72, 0, 0, 0, 0, 3L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 20, 72, 0, 0, 0, 0, 4L));

        Assert.assertEquals(1, selectControl.getSelectedIndex());
        Assert.assertEquals(0, buttonClicks[0]);
        Assert.assertFalse(document.__isTopLayerElement(popup));
    }

    /**
     * 验证 fixed HUD 浮窗中的 select 仍按 top-layer 真实命中候选项。
     */
    @Test
    public void shouldSelectOptionInsideFixedShellWithoutClickingUnderlyingButton() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode shell = document.div();
        final int[] buttonClicks = new int[1];
        DocumentSelectControl selectControl = new DocumentSelectControl(document, "A", "B", "C");
        DocumentButtonControl buttonControl = new DocumentButtonControl(document, "Submit");

        root.style()
                .setWidth(UiStyleLength.px(320))
                .setHeight(UiStyleLength.px(240));
        shell.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(60))
                .setTop(UiStyleLength.px(40))
                .setWidth(UiStyleLength.px(180))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE);
        selectControl.getElement().style().setWidth(UiStyleLength.px(180));
        buttonControl.getElement().style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(60))
                .setTop(UiStyleLength.px(100))
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(40));
        buttonControl.setActionHandler(event -> buttonClicks[0]++);
        shell.append(selectControl.getElement());
        root.append(shell);
        root.append(buttonControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 320, 240,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 320, 240);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 70, 52, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 70, 52, 0, 0, 0, 0, 2L));
        ElementNode popup = findListboxElement(root);
        Assert.assertSame(selectControl.getElement(), popup.getParent());
        Assert.assertTrue(document.__isTopLayerElement(popup));

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 70, 112, 0, 0, 0, 0, 3L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 70, 112, 0, 0, 0, 0, 4L));

        Assert.assertEquals(1, selectControl.getSelectedIndex());
        Assert.assertEquals(0, buttonClicks[0]);
    }

    /**
     * 验证 select 锚点在 popup 打开后移动时，top-layer 面板会跟随新的文档坐标。
     */
    @Test
    public void shouldRepositionOpenPopupWhenAnchorMoves() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode shell = document.div();
        DocumentSelectControl selectControl = new DocumentSelectControl(document, "A", "B", "C");

        root.style()
                .setWidth(UiStyleLength.px(320))
                .setHeight(UiStyleLength.px(240));
        shell.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(40))
                .setTop(UiStyleLength.px(30))
                .setWidth(UiStyleLength.px(180))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE);
        selectControl.getElement().style().setWidth(UiStyleLength.px(180));
        shell.append(selectControl.getElement());
        root.append(shell);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 320, 240,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 320, 240);

        click(widget, 48, 38, 1L);
        Assert.assertEquals("true", selectControl.getElement().getAttribute("aria-expanded"));

        shell.style()
                .setLeft(UiStyleLength.px(90))
                .setTop(UiStyleLength.px(54));
        click(widget, 98, 122, 3L);

        Assert.assertEquals("B", selectControl.getSelectedOption());
    }

    /**
     * 验证 transform 祖先下的 select popup 会按视觉坐标锚定。
     */
    @Test
    public void shouldAnchorPopupToTransformedSelectVisualBounds() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode transformed = document.div();
        DocumentSelectControl selectControl = new DocumentSelectControl(document, "A", "B", "C");
        root.style()
                .setWidth(UiStyleLength.px(320))
                .setHeight(UiStyleLength.px(240));
        transformed.style()
                .setWidth(UiStyleLength.px(180))
                .setTransform(UiTransform.translate(80.0F, 40.0F));
        selectControl.getElement().style().setWidth(UiStyleLength.px(180));
        transformed.append(selectControl.getElement());
        root.append(transformed);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 320, 240,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 320, 240);

        click(widget, 90, 52, 1L);
        ElementNode popup = findListboxElement(root);

        Assert.assertTrue(document.__isTopLayerElement(popup));
        Assert.assertEquals(UiStyleLength.px(80), popup.style().getLeft());
        Assert.assertEquals(UiStyleLength.px(74), popup.style().getTop());

        click(widget, 90, 112, 3L);

        Assert.assertEquals("B", selectControl.getSelectedOption());
    }

    /**
     * 验证 top-layer 命中顺序高于普通 fixed 内容，且后注册的顶层元素在更上层。
     */
    @Test
    public void shouldHitLatestTopLayerAboveFixedContent() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode normalFixed = createHitSurface(document, 0xFF334455);
        ElementNode firstTopLayer = createHitSurface(document, 0xFF556677);
        ElementNode secondTopLayer = createHitSurface(document, 0xFF778899);
        final int[] normalClicks = new int[1];
        final int[] firstClicks = new int[1];
        final int[] secondClicks = new int[1];

        root.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(120));
        normalFixed.setClickHandler(event -> {
            normalClicks[0]++;
            return true;
        });
        firstTopLayer.setClickHandler(event -> {
            firstClicks[0]++;
            return true;
        });
        secondTopLayer.setClickHandler(event -> {
            secondClicks[0]++;
            return true;
        });
        root.append(normalFixed).append(firstTopLayer).append(secondTopLayer);
        document.__showTopLayerElement(firstTopLayer);
        document.__showTopLayerElement(secondTopLayer);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 160, 120);

        click(widget, 20, 20, 1L);
        Assert.assertEquals(0, normalClicks[0]);
        Assert.assertEquals(0, firstClicks[0]);
        Assert.assertEquals(1, secondClicks[0]);

        document.__hideTopLayerElement(secondTopLayer);
        secondTopLayer.style().setDisplay(UiDisplay.NONE);
        click(widget, 20, 20, 3L);
        Assert.assertEquals(0, normalClicks[0]);
        Assert.assertEquals(1, firstClicks[0]);

        document.__hideTopLayerElement(firstTopLayer);
        firstTopLayer.style().setDisplay(UiDisplay.NONE);
        click(widget, 20, 20, 5L);
        Assert.assertEquals(1, normalClicks[0]);
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
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_DOWN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 1L));
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 2L));
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_ESCAPE, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 3L));

        Assert.assertEquals(1, selectControl.getSelectedIndex());
        Assert.assertEquals("B", selectControl.getSelectedOption());
        Assert.assertEquals("false", selectControl.getElement().getAttribute("aria-expanded"));
        Assert.assertEquals(1, events.size());
        Assert.assertTrue(events.get(0).isKeyboardTriggered());
        Assert.assertEquals(UiKeyCodes.KEY_DOWN, events.get(0).getKeyCode());
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
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 1L));
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_END, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 2L));

        ElementNode popup = findListboxElement(root);
        Assert.assertEquals(6, selectControl.getSelectedIndex());
        Assert.assertTrue(popup.getMaxScrollTop() > 0);
        Assert.assertTrue(popup.getScrollTop() > 0);
    }

    /**
     * 验证超长 select 只保留可视窗口内的少量 option 节点。
     */
    @Test
    public void shouldVirtualizeLargeOptionListInsideScrollablePopup() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentSelectControl selectControl = new DocumentSelectControl(document, createOptions(10000));
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(220));
        selectControl.getElement().style().setWidth(UiStyleLength.px(180));
        root.append(selectControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 220,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 220);

        click(widget, 20, 12, 1L);
        ElementNode popup = findListboxElement(root);
        widget.resolveLayoutBoxForTest();

        Assert.assertEquals(10000, selectControl.getOptionCount());
        Assert.assertTrue(countOptionElements(popup) <= 12);
        Assert.assertTrue(popup.getMaxScrollTop() > 200000);
        Assert.assertNotNull(findOptionElementByText(popup, "Item 0"));
    }

    /**
     * 验证虚拟化 select 滚动到远端窗口后仍能命中并选择真实选项。
     */
    @Test
    public void shouldSelectVirtualizedOptionAfterProgrammaticScroll() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentSelectControl selectControl = new DocumentSelectControl(document, createOptions(10000));
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(220));
        selectControl.getElement().style().setWidth(UiStyleLength.px(180));
        root.append(selectControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 220,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 220);

        click(widget, 20, 12, 1L);
        ElementNode popup = findListboxElement(root);
        popup.scrollTo(0, 9000 * 28);
        widget.resolveLayoutBoxForTest();
        ElementNode targetOption = findOptionElementByText(popup, "Item 9000");
        Assert.assertNotNull(targetOption);
        club.heiqi.uilib.ui.dom.DocumentElementBounds bounds = targetOption.getDocumentBounds();
        click(widget, bounds.getLeft() + 8, bounds.getTop() + 8, 3L);

        Assert.assertEquals(9000, selectControl.getSelectedIndex());
        Assert.assertEquals("Item 9000", selectControl.getSelectedOption());
    }

    /**
     * 验证键盘跳到末项时，虚拟窗口会同步切到末尾选项附近。
     */
    @Test
    public void shouldRevealVirtualizedEndOptionWhenKeyboardNavigatesLargeList() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentSelectControl selectControl = new DocumentSelectControl(document, createOptions(10000));
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(220));
        selectControl.getElement().style().setWidth(UiStyleLength.px(180));
        root.append(selectControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 220,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 220);

        widget.onFocusTraversalEntered(false);
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 1L));
        widget.onKeyEvent(new UiKeyEvent(UiKeyCodes.KEY_END, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 2L));
        ElementNode popup = findListboxElement(root);

        Assert.assertEquals(9999, selectControl.getSelectedIndex());
        Assert.assertTrue(popup.getScrollTop() > 200000);
        Assert.assertNotNull(findOptionElementByText(popup, "Item 9999"));
    }

    /**
     * 验证移除包含展开 select 的子树时，会同步清理 popup 的 top-layer 注册。
     */
    @Test
    public void shouldDetachTopLayerPopupWhenOpenSelectAncestorIsRemoved() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode shell = document.div();
        DocumentSelectControl selectControl = new DocumentSelectControl(document, "A", "B", "C");
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(160));
        selectControl.getElement().style().setWidth(UiStyleLength.px(180));
        shell.append(selectControl.getElement());
        root.append(shell);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 160,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 160);

        click(widget, 20, 12, 1L);
        ElementNode popup = findListboxElement(root);
        Assert.assertTrue(document.__isTopLayerElement(popup));

        root.removeChild(shell);
        Assert.assertFalse(document.__getTopLayerElements().contains(popup));
        Assert.assertFalse(document.__isTopLayerElement(popup));
        Assert.assertEquals("false", selectControl.getElement().getAttribute("aria-expanded"));
        Assert.assertEquals(UiDisplay.NONE, popup.style().getDisplay());
        Assert.assertEquals(UiPosition.ABSOLUTE, popup.style().getPosition());
        Assert.assertEquals(UiStyleLength.percent(1.0F), popup.style().getTop());

        root.append(shell);
        Assert.assertFalse(document.__isTopLayerElement(popup));
        Assert.assertEquals("false", selectControl.getElement().getAttribute("aria-expanded"));
        Assert.assertEquals(UiDisplay.NONE, popup.style().getDisplay());
    }

    /**
     * 验证选择 popup 关闭后会立即刷新当前鼠标位置的 hover 状态。
     */
    @Test
    public void shouldRefreshHoverWhenPopupClosesAfterOptionClick() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final List<String> hoverEvents = new ArrayList<String>();
        DocumentSelectControl selectControl = new DocumentSelectControl(document, "A", "B", "C");
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(160));
        selectControl.getElement().style().setWidth(UiStyleLength.px(180));
        root.append(selectControl.getElement());
        ElementNode popup = findListboxElement(root);
        ElementNode secondOption = findOptionElement(popup, 1);
        secondOption.setHoverHandler(event -> {
            hoverEvents.add("option:" + event.isHovered());
            return false;
        });
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 160,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 160);

        click(widget, 20, 12, 1L);
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 20, 72, -1, 0, 0, 0, 3L));
        click(widget, 20, 72, 4L);

        Assert.assertEquals("B", selectControl.getSelectedOption());
        Assert.assertEquals("[option:true, option:false]", hoverEvents.toString());
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

    private static int countOptionElements(ElementNode element) {
        int count = "option".equals(element.getTagName()) ? 1 : 0;
        for (club.heiqi.uilib.ui.dom.DocumentNode child : element.getChildren()) {
            if (child instanceof ElementNode) {
                count += countOptionElements((ElementNode) child);
            }
        }
        return count;
    }

    private static ElementNode findOptionElementByText(ElementNode popup, String text) {
        for (club.heiqi.uilib.ui.dom.DocumentNode child : popup.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode childElement = (ElementNode) child;
                if ("option".equals(childElement.getTagName()) && text.equals(childElement.getTextContent())) {
                    return childElement;
                }
            }
        }
        return null;
    }

    private static String[] createOptions(int count) {
        String[] options = new String[count];
        for (int index = 0; index < count; index++) {
            options[index] = "Item " + index;
        }
        return options;
    }

    private static ElementNode findListboxElement(ElementNode element) {
        if ("listbox".equals(element.getAttribute("role"))) {
            return element;
        }
        for (club.heiqi.uilib.ui.dom.DocumentNode child : element.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findListboxElement((ElementNode) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static ElementNode findOptionElement(ElementNode popup, int optionIndex) {
        int index = 0;
        for (club.heiqi.uilib.ui.dom.DocumentNode child : popup.getChildren()) {
            if (child instanceof ElementNode && "option".equals(((ElementNode) child).getTagName())) {
                if (index == optionIndex) {
                    return (ElementNode) child;
                }
                index++;
            }
        }
        return null;
    }

    private static ElementNode createHitSurface(UiDocument document, int color) {
        ElementNode element = document.div();
        element.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(10))
                .setTop(UiStyleLength.px(10))
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setBackgroundColor(color);
        return element;
    }

    private static void click(HtmlLikeDocumentWidget widget, int x, int y, long timeNanos) {
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, x, y, 0, 0, 0, 0, timeNanos));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, x, y, 0, 0, 0, 0, timeNanos + 1L));
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
