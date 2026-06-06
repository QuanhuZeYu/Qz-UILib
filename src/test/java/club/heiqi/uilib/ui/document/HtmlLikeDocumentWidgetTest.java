package club.heiqi.uilib.ui.document;

import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.appendDynamicTextLine;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.appendHudPanelWithTopCards;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertDrawCall;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertElementUid;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertTextCall;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.createAutoWidthTextBlock;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.createTextBlock;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.DeterministicTextMeasureService;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.RecordingUiRenderContext;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.TextCall;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache;
import club.heiqi.uilib.ui.dom.DocumentElementActiveEvent;
import club.heiqi.uilib.ui.dom.DocumentElementActiveHandler;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementContextMenuEvent;
import club.heiqi.uilib.ui.dom.DocumentElementContextMenuHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDoubleClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementDoubleClickHandler;
import club.heiqi.uilib.ui.dom.DocumentEventPhase;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationEvent;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementMouseUpEvent;
import club.heiqi.uilib.ui.dom.DocumentElementMouseUpHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiOverflowWrap;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * `HtmlLikeDocumentWidget` 的后端适配契约测试。
 */
public class HtmlLikeDocumentWidgetTest {

    /**
     * 验证 HTML-like 文档可以通过 widget 后端绘制到 `UiRenderContext`。
     */
    @Test
    public void shouldRenderDocumentThroughWidgetBackend() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setHeight(UiStyleLength.px(24))
                .setBackgroundColor(0xFF102030)
                .setBorderColor(0xFF80A0FF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(6));
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(17, 23, 120, 48);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertSame(document, widget.getDocument());
        Assert.assertEquals(2, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 17, 23, 137, 49, 0xFF102030, 0, 6);
        assertDrawCall(renderContext.drawCalls.get(1), 17, 23, 137, 49, 0, 0xFF80A0FF, 6);
    }

    /**
     * 验证空尺寸组件不会触发绘制。
     */
    @Test
    public void shouldIgnoreEmptyWidgetBounds() {
        UiDocument document = UiDocument.create();
        document.getRootElement().style().setBackgroundColor(0xFFFFFFFF);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 0, 48);
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();

        widget.render(renderContext);

        Assert.assertTrue(renderContext.drawCalls.isEmpty());
    }

    /**
     * 验证 HTML-like 文档适配组件会使用注入的文本测量服务生成多行文本绘制命令。
     */
    @Test
    public void shouldRenderWrappedTextThroughWidgetBackend() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(24))
                .setOverflowWrap(UiOverflowWrap.BREAK_WORD)
                .setTextColor(0xFFEFF6FF);
        root.appendText("abcdefg");
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 80);
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();

        widget.render(renderContext);

        Assert.assertEquals(3, renderContext.textCalls.size());
        assertTextCall(renderContext.textCalls.get(0), "abc", 5, 7, 0xFFEFF6FF, false);
        assertTextCall(renderContext.textCalls.get(1), "def", 5, 25, 0xFFEFF6FF, false);
        assertTextCall(renderContext.textCalls.get(2), "g", 5, 43, 0xFFEFF6FF, false);
    }

    /**
     * 验证 HTML-like 文本节点默认按 UILib 原始文本模式绘制。
     */
    @Test
    public void shouldRenderTextNodesInUiLibRawModeByDefault() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style().setWidth(UiStyleLength.px(80));
        root.appendText("价格：§a100金币");
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 48);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertEquals(1, renderContext.textCalls.size());
        Assert.assertEquals("价格：§a100金币", renderContext.textCalls.get(0).text);
        Assert.assertEquals(TextContentMode.UILIB_RAW, renderContext.textCalls.get(0).textContentMode);
    }

    /**
     * 验证文本节点可显式切回 Minecraft 文本模式。
     */
    @Test
    public void shouldAllowExplicitMinecraftFormattedTextNodes() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style().setWidth(UiStyleLength.px(80));
        root.appendMinecraftText("价格：§a100金币");
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 48);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertFalse(renderContext.textCalls.isEmpty());
        Assert.assertEquals(TextContentMode.MINECRAFT_FORMATTED, renderContext.textCalls.get(0).textContentMode);
    }

    /**
     * 验证 HUD 风格卡片中的空文本节点在后续写入长文本后，会触发布局重算并扩展真实高度。
     */
    @Test
    public void shouldRelayoutHudLikeCardsAfterDeferredTextMutation() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode heroCard = document.div();
        ElementNode overviewCard = document.div();
        TextNode summaryText;
        TextNode bodyText;

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
                .setPadding(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(8));
        heroCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.props.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.props.UiAlignItems.START)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(4));
        heroCard.append(createAutoWidthTextBlock(document, "INTERACTIVE HUD"));
        summaryText = appendDynamicTextLine(document, heroCard, "");

        overviewCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.props.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.props.UiAlignItems.START)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(6))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(3));
        overviewCard.append(createAutoWidthTextBlock(document, "会话概览"));
        bodyText = appendDynamicTextLine(document, overviewCard, "");

        panel.append(heroCard).append(overviewCard);
        root.append(panel);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 2048, 1152,
                DefaultTextMeasureService.getInstance());
        widget.applyLayoutBounds(0, 0, 2048, 1152);
        widget.render(new RecordingUiRenderContext());

        DocumentLayoutBox initialPanelBox = widget.resolveLayoutBoxForTest().getChildren().get(0);
        DocumentLayoutBox initialHeroCardBox = initialPanelBox.getChildren().get(0);
        DocumentLayoutBox initialOverviewCardBox = initialPanelBox.getChildren().get(1);

        summaryText.setText("把工具浮窗停在背包右上区域，用于核对 HUD 层可见性、输入接管与滚轮状态。");
        bodyText.setText("容器界面上方可见。点击次数 0，备注：把鼠标移到背包界面后尝试编辑我。"
                + "继续补充第二句说明，确保在 360 像素浮窗宽度下发生明显换行。"
                + "继续补充第三句说明，验证动态文本更新后卡片高度会随之扩展。");
        widget.render(new RecordingUiRenderContext());

        DocumentLayoutBox panelBox = widget.resolveLayoutBoxForTest().getChildren().get(0);
        DocumentLayoutBox heroCardBox = panelBox.getChildren().get(0);
        DocumentLayoutBox overviewCardBox = panelBox.getChildren().get(1);

        Assert.assertTrue(heroCardBox.getHeight() > initialHeroCardBox.getHeight());
        Assert.assertTrue(overviewCardBox.getHeight() > initialOverviewCardBox.getHeight());
        Assert.assertTrue(overviewCardBox.getTop() >= heroCardBox.getBottom());
        Assert.assertTrue(heroCardBox.getChildren().get(1).getContentHeight() > 18);
        Assert.assertTrue(overviewCardBox.getChildren().get(1).getContentHeight() > 18);
    }

    /**
     * 验证固定高度 HUD 面板中，顶部动态文本卡片变高后，会把下方 flexGrow 滚动区整体下推并压缩剩余高度。
     */
    @Test
    public void shouldPushFlexGrowScrollAreaDownAfterTopHudTextExpands() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode heroCard = document.div();
        ElementNode controlCard = document.div();
        ElementNode scrollContent = document.div();
        ElementNode contentBody = document.div();
        TextNode heroSummaryText;

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
        heroCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.props.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.props.UiAlignItems.START)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(4));
        heroCard.append(createAutoWidthTextBlock(document, "INTERACTIVE HUD"));
        heroSummaryText = appendDynamicTextLine(document, heroCard, "");

        controlCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.props.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.props.UiAlignItems.START)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(6));
        controlCard.append(createAutoWidthTextBlock(document, "调试开关"));
        controlCard.append(createAutoWidthTextBlock(document, "底部提示标记：保留"));

        scrollContent.style()
                .setFlexGrow(1.0F)
                .setWidth(UiStyleLength.percent(1.0F))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        contentBody.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.props.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.props.UiAlignItems.STRETCH)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(6));
        contentBody.append(createTextBlock(document, "会话概览"));
        contentBody.append(createTextBlock(document, "容器界面上方可见。点击次数 0，备注：把鼠标移到背包界面后尝试编辑我。"));
        scrollContent.append(contentBody);

        panel.append(heroCard).append(controlCard).append(scrollContent);
        root.append(panel);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 2048, 1152,
                DefaultTextMeasureService.getInstance());
        widget.applyLayoutBounds(0, 0, 2048, 1152);
        widget.render(new RecordingUiRenderContext());

        DocumentLayoutBox initialPanelBox = widget.resolveLayoutBoxForTest().getChildren().get(0);
        DocumentLayoutBox initialHeroCardBox = initialPanelBox.getChildren().get(0);
        DocumentLayoutBox initialControlCardBox = initialPanelBox.getChildren().get(1);
        DocumentLayoutBox initialScrollContentBox = initialPanelBox.getChildren().get(2);

        heroSummaryText.setText("把工具浮窗停在背包右上区域，用于核对 HUD 层可见性、输入接管与滚轮状态。"
                + "继续补充第二句说明，确保顶部卡片高度明显增长，并观察下方滚动区是否整体下推。"
                + "继续补充第三句说明，避免只增长一行导致问题被掩盖。");
        widget.render(new RecordingUiRenderContext());

        DocumentLayoutBox panelBox = widget.resolveLayoutBoxForTest().getChildren().get(0);
        DocumentLayoutBox heroCardBox = panelBox.getChildren().get(0);
        DocumentLayoutBox controlCardBox = panelBox.getChildren().get(1);
        DocumentLayoutBox scrollContentBox = panelBox.getChildren().get(2);

        Assert.assertTrue(heroCardBox.getHeight() > initialHeroCardBox.getHeight());
        Assert.assertTrue(controlCardBox.getTop() >= heroCardBox.getBottom());
        Assert.assertTrue(scrollContentBox.getTop() >= controlCardBox.getBottom());
        Assert.assertTrue(scrollContentBox.getTop() > initialScrollContentBox.getTop());
        Assert.assertTrue(scrollContentBox.getHeight() < initialScrollContentBox.getHeight());
        Assert.assertTrue(controlCardBox.getTop() >= initialControlCardBox.getTop());
    }

    /**
     * 验证固定高度 HUD 面板中的顶部卡片在声明 flex-shrink:0 后，不会被压缩到小于自然高度。
     */
    @Test
    public void shouldKeepTopHudCardsAtNaturalHeightWhenFlexShrinkIsDisabled() {
        UiDocument shrinkEnabledDocument = UiDocument.create();
        ElementNode shrinkEnabledRoot = shrinkEnabledDocument.getRootElement();
        shrinkEnabledRoot.style()
                .setWidth(UiStyleLength.px(2048))
                .setHeight(UiStyleLength.px(1152));
        appendHudPanelWithTopCards(shrinkEnabledDocument, shrinkEnabledRoot, false);
        HtmlLikeDocumentWidget unconstrainedWidget = new HtmlLikeDocumentWidget(shrinkEnabledDocument, 2048, 1152,
                DefaultTextMeasureService.getInstance());
        unconstrainedWidget.applyLayoutBounds(0, 0, 2048, 1152);
        unconstrainedWidget.render(new RecordingUiRenderContext());
        DocumentLayoutBox unconstrainedPanelBox = unconstrainedWidget.resolveLayoutBoxForTest().getChildren().get(0);
        DocumentLayoutBox shrinkEnabledHeroCardBox = unconstrainedPanelBox.getChildren().get(0);
        DocumentLayoutBox shrinkEnabledControlCardBox = unconstrainedPanelBox.getChildren().get(1);

        UiDocument constrainedDocument = UiDocument.create();
        ElementNode constrainedDocRoot = constrainedDocument.getRootElement();
        constrainedDocRoot.style()
                .setWidth(UiStyleLength.px(2048))
                .setHeight(UiStyleLength.px(1152));
        appendHudPanelWithTopCards(constrainedDocument, constrainedDocRoot, true);
        HtmlLikeDocumentWidget constrainedWidget = new HtmlLikeDocumentWidget(constrainedDocument, 2048, 1152,
                DefaultTextMeasureService.getInstance());
        constrainedWidget.applyLayoutBounds(0, 0, 2048, 1152);
        constrainedWidget.render(new RecordingUiRenderContext());
        DocumentLayoutBox constrainedPanelBox = constrainedWidget.resolveLayoutBoxForTest().getChildren().get(0);
        DocumentLayoutBox constrainedHeroCardBox = constrainedPanelBox.getChildren().get(0);
        DocumentLayoutBox constrainedControlCardBox = constrainedPanelBox.getChildren().get(1);

        Assert.assertTrue(constrainedHeroCardBox.getHeight() >= shrinkEnabledHeroCardBox.getHeight());
        Assert.assertTrue(constrainedControlCardBox.getHeight() >= shrinkEnabledControlCardBox.getHeight());
    }

    /**
     * 验证 HTML-like 组件可以命中屏幕坐标下的最深元素。
     */
    @Test
    public void shouldFindDeepestElementAtScreenPoint() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        ElementNode grandChild = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        grandChild.style()
                .setWidth(UiStyleLength.px(16))
                .setHeight(UiStyleLength.px(10));
        child.append(grandChild);
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 40);

        assertElementUid(grandChild, widget.findElementAt(10, 12));
        Assert.assertNull(widget.findElementAt(120, 12));
    }

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
     * 验证 img 加载失败时会绘制 alt 文本回退，而不是静默空白。
     */
    @Test
    public void shouldRenderAltFallbackWhenImageLoadFails() {
        DocumentRemoteImageCache.getInstance().clearForTesting();
        DocumentRemoteImageCache.getInstance().putFailedForTesting("https://example.test/missing.png");

        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode image = document.img();
        image.setAttribute("src", "https://example.test/missing.png");
        image.setAttribute("alt", "Missing icon");
        image.style().setWidth(UiStyleLength.px(72)).setHeight(UiStyleLength.px(24));
        root.append(image);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 60,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 60);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertTrue(renderContext.hostImageCalls.isEmpty());
        Assert.assertFalse(renderContext.textCalls.isEmpty());
        Assert.assertEquals("Missing icon", renderContext.textCalls.get(0).text);
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
     * 验证字体粗细和斜体会进入文本绘制调用。
     */
    @Test
    public void shouldRenderTextWithFontWeightAndFontStyle() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(160))
                .setFontWeight(UiFontWeight.BOLD)
                .setFontStyle(UiFontStyle.ITALIC);
        root.appendText("bold italic");
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 160, 40);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertFalse(renderContext.textCalls.isEmpty());
        for (TextCall textCall : renderContext.textCalls) {
            Assert.assertEquals(UiFontWeight.BOLD, textCall.fontWeight);
            Assert.assertEquals(UiFontStyle.ITALIC, textCall.fontStyle);
        }
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
