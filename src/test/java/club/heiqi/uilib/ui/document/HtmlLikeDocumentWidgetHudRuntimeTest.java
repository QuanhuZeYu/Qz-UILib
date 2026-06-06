package club.heiqi.uilib.ui.document;

import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.appendDynamicTextLine;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.appendHudPanelWithTopCards;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.createAutoWidthTextBlock;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.createTextBlock;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.RecordingUiRenderContext;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;

/**
 * `HtmlLikeDocumentWidget` 的 HUD 风格运行态布局回归测试。
 */
public class HtmlLikeDocumentWidgetHudRuntimeTest {

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
}
