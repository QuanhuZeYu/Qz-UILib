package club.heiqi.uilib.ui.layout;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiScrollbarWidth;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * `DocumentScrollState` 的 HTML-like 滚动命中契约。
 */
public class DocumentScrollStateTest {

    /**
     * 验证 positioned 后代可越过非 stacking context 祖先接收滚轮。
     */
    @Test
    public void shouldScrollPositionedDescendantInNearestStackingContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode parent = document.div();
        ElementNode raisedScroller = document.div();
        ElementNode raisedContent = document.div();
        ElementNode normalCover = document.div();
        ElementNode coverContent = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        parent.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20));
        configureScroller(raisedScroller);
        raisedScroller.style()
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(12))
                .setZIndex(5);
        raisedContent.style().setHeight(UiStyleLength.px(80));
        configureScroller(normalCover);
        coverContent.style().setHeight(UiStyleLength.px(80));
        raisedScroller.append(raisedContent);
        normalCover.append(coverContent);
        parent.append(raisedScroller);
        root.append(parent).append(normalCover);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 140, 0);
        DocumentScrollState scrollState = new DocumentScrollState();

        Assert.assertTrue(scrollState.handleWheel(rootBox, 10, 22, -120, 1L));
        Assert.assertTrue(scrollState.getScrollTop(raisedScroller) > 0);
        Assert.assertEquals(0, scrollState.getScrollTop(normalCover));
    }

    /**
     * 验证 stacking context 祖先会阻止高 z-index 后代抢占外部 sibling 的滚轮命中。
     */
    @Test
    public void shouldScrollExternalSiblingAboveIsolatedPositionedDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode isolatedParent = document.div();
        ElementNode raisedScroller = document.div();
        ElementNode raisedContent = document.div();
        ElementNode normalCover = document.div();
        ElementNode coverContent = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        isolatedParent.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(0.98F);
        configureScroller(raisedScroller);
        raisedScroller.style()
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(12))
                .setZIndex(99);
        raisedContent.style().setHeight(UiStyleLength.px(80));
        configureScroller(normalCover);
        coverContent.style().setHeight(UiStyleLength.px(80));
        raisedScroller.append(raisedContent);
        normalCover.append(coverContent);
        isolatedParent.append(raisedScroller);
        root.append(isolatedParent).append(normalCover);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 140, 0);
        DocumentScrollState scrollState = new DocumentScrollState();

        Assert.assertTrue(scrollState.handleWheel(rootBox, 10, 22, -120, 1L));
        Assert.assertEquals(0, scrollState.getScrollTop(raisedScroller));
        Assert.assertTrue(scrollState.getScrollTop(normalCover) > 0);
    }

    /**
     * 验证 overflow clip effect boundary 会阻止越界高 z-index scroller 抢占滚轮。
     */
    @Test
    public void shouldScrollExternalSiblingAboveClippedPositionedDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode clippedParent = document.div();
        ElementNode raisedScroller = document.div();
        ElementNode raisedContent = document.div();
        ElementNode normalCover = document.div();
        ElementNode coverContent = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        clippedParent.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        configureScroller(raisedScroller);
        raisedScroller.style()
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(12))
                .setZIndex(99);
        raisedContent.style().setHeight(UiStyleLength.px(80));
        configureScroller(normalCover);
        coverContent.style().setHeight(UiStyleLength.px(80));
        raisedScroller.append(raisedContent);
        normalCover.append(coverContent);
        clippedParent.append(raisedScroller);
        root.append(clippedParent).append(normalCover);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 140, 0);
        DocumentScrollState scrollState = new DocumentScrollState();

        Assert.assertTrue(scrollState.handleWheel(rootBox, 10, 22, -120, 1L));
        Assert.assertEquals(0, scrollState.getScrollTop(raisedScroller));
        Assert.assertTrue(scrollState.getScrollTop(normalCover) > 0);
    }

    /**
     * 验证 fixed 滚动容器在根滚动后仍按视口固定位置接收滚轮。
     */
    @Test
    public void shouldScrollFixedDescendantAtViewportPositionAfterRootScroll() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode spacer = document.div();
        ElementNode fixedScroller = document.div();
        ElementNode fixedContent = document.div();

        root.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(50))
                .setOverflowY(UiOverflow.AUTO);
        spacer.style().setHeight(UiStyleLength.px(140));
        configureScroller(fixedScroller);
        fixedScroller.style()
                .setPosition(UiPosition.FIXED)
                .setTop(UiStyleLength.px(5))
                .setLeft(UiStyleLength.px(5));
        fixedContent.style().setHeight(UiStyleLength.px(80));
        fixedScroller.append(fixedContent);
        root.append(spacer).append(fixedScroller);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 100, 50);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);
        Assert.assertTrue(scrollState.setScrollOffset(root, 0, 36));

        Assert.assertTrue(scrollState.handleWheel(rootBox, 10, 10, -120, 1L));
        Assert.assertEquals(36, scrollState.getScrollTop(root));
        Assert.assertTrue(scrollState.getScrollTop(fixedScroller) > 0);
    }

    /**
     * 验证 HUD 风格固定内容区能计算出正向滚动范围并消费滚轮。
     */
    @Test
    public void shouldComputePositiveScrollRangeForHudLikeScrollHost() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode contentRoot = document.div();

        root.style()
                .setWidth(UiStyleLength.px(320))
                .setHeight(UiStyleLength.px(540));
        contentRoot.style()
                .setWidth(UiStyleLength.px(248))
                .setHeight(UiStyleLength.px(360))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        for (int index = 1; index <= 6; index++) {
            contentRoot.append(createCard(document, index));
        }
        root.append(contentRoot);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 320, 540,
                new DeterministicTextMeasureService());
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);

        Assert.assertTrue(scrollState.getMaxScrollTop(contentRoot) > 0);
        Assert.assertTrue(scrollState.handleWheel(rootBox, 24, 40, -120, 1L));
        Assert.assertTrue(scrollState.getScrollTop(contentRoot) > 0);
    }

    /**
     * 验证 scrollbar-width 会影响滚动条几何，而 none 会隐藏滚动条命中区域。
     */
    @Test
    public void shouldRespectScrollbarWidthInMetrics() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();

        root.style()
                .setWidth(UiStyleLength.px(60))
                .setHeight(UiStyleLength.px(24))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setScrollbarWidth(UiScrollbarWidth.THIN);
        child.style().setHeight(UiStyleLength.px(80));
        root.append(child);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);

        Assert.assertNotNull(scrollState.getVerticalScrollbarMetrics(rootBox, 0, 0, false));

        root.style().setScrollbarWidth(UiScrollbarWidth.NONE);
        rootBox = DocumentLayoutEngine.layout(root, 80, 0);
        scrollState.updateFromLayout(rootBox);

        Assert.assertNull(scrollState.getVerticalScrollbarMetrics(rootBox, 0, 0, false));
    }

    /**
     * 验证 fixed HUD 面板中命中后代文本时，滚轮命中链路会回退到祖先 scroll host。
     */
    @Test
    public void shouldScrollFixedHudAncestorHostWhenPointerHitsDescendantText() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode dragBar = document.div();
        ElementNode heroCard = document.div();
        ElementNode controlCard = document.div();
        ElementNode scrollHost = document.div();
        ElementNode contentBody = document.div();

        root.style()
                .setWidth(UiStyleLength.px(2048))
                .setHeight(UiStyleLength.px(1152));
        panel.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(1648))
                .setTop(UiStyleLength.px(18))
                .setDisplay(club.heiqi.uilib.ui.style.UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.UiAlignItems.START)
                .setWidth(UiStyleLength.px(360))
                .setHeight(UiStyleLength.px(368))
                .setPadding(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(8));
        dragBar.appendText("HUD 工具浮窗 · 拖住这里移动");

        heroCard.style()
                .setDisplay(club.heiqi.uilib.ui.style.UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(4));
        heroCard.append(createAutoWidthTextBlock(document, "INTERACTIVE HUD"));
        heroCard.append(createAutoWidthTextBlock(document, "容器界面可交互"));
        heroCard.append(createAutoWidthTextBlock(document, "主浮窗调试台"));
        heroCard.append(createAutoWidthTextBlock(document,
                "把工具浮窗停在背包右上区域，用于核对 HUD 层可见性、输入接管与滚轮状态。"));

        controlCard.style()
                .setDisplay(club.heiqi.uilib.ui.style.UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(6));
        controlCard.append(createAutoWidthTextBlock(document, "调试开关"));
        ElementNode debugToggleCard = document.div();
        debugToggleCard.style()
                .setDisplay(club.heiqi.uilib.ui.style.UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(6));
        debugToggleCard.append(createAutoWidthTextBlock(document, "显示 HUD 调试信息"));
        ElementNode debugToggleHost = document.div();
        debugToggleHost.style().setDisplay(club.heiqi.uilib.ui.style.UiDisplay.BLOCK)
                .setWidth(UiStyleLength.auto());
        debugToggleHost.append(new club.heiqi.uilib.ui.dom.control.DocumentToggleSwitchControl(document)
                .setToggled(true).getElement());
        debugToggleCard.append(debugToggleHost);
        controlCard.append(debugToggleCard);
        controlCard.append(createAutoWidthTextBlock(document, "底部提示标记：保留"));

        scrollHost.style()
                .setFlexGrow(1.0F)
                .setWidth(UiStyleLength.percent(1.0F))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        contentBody.style()
                .setDisplay(club.heiqi.uilib.ui.style.UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.UiAlignItems.START)
                .setWidth(UiStyleLength.auto())
                .setRowGap(UiStyleLength.px(6));
        scrollHost.append(contentBody);

        ElementNode overviewCard = document.div();
        overviewCard.style()
                .setDisplay(club.heiqi.uilib.ui.style.UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(3));
        overviewCard.append(createAutoWidthTextBlock(document, "会话概览"));
        overviewCard.append(createAutoWidthTextBlock(document, "容器界面上方可见。点击次数 0，备注：把鼠标移到背包界面后尝试编辑我。"));
        contentBody.append(overviewCard);

        ElementNode noteCard = document.div();
        noteCard.style()
                .setDisplay(club.heiqi.uilib.ui.style.UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(4));
        noteCard.append(createAutoWidthTextBlock(document, "容器备注"));
        noteCard.append(new club.heiqi.uilib.ui.dom.control.DocumentTextInputControl(document)
                .setPlaceholder("在容器界面中输入备注")
                .setText("把鼠标移到背包界面后尝试编辑我")
                .getElement());
        noteCard.append(new club.heiqi.uilib.ui.dom.control.DocumentButtonControl(document, "记录一次点击").getElement());
        contentBody.append(noteCard);

        ElementNode debugCard = document.div();
        debugCard.style()
                .setDisplay(club.heiqi.uilib.ui.style.UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(3));
        debugCard.append(createAutoWidthTextBlock(document, "HUD DEBUG"));
        debugCard.append(createAutoWidthTextBlock(document, "滚轮监控：有范围但未命中宿主。偏移 0 / 439。"));
        contentBody.append(debugCard);

        ElementNode tipsCard = document.div();
        tipsCard.style()
                .setDisplay(club.heiqi.uilib.ui.style.UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(3));
        tipsCard.append(createAutoWidthTextBlock(document, "操作建议"));
        for (int index = 1; index <= 8; index++) {
            tipsCard.append(createAutoWidthTextBlock(document,
                    "滚轮停在这里可查看内部内容，第 " + index + " 条示例说明。继续补充中文描述，确保形成明显纵向溢出。"));
        }
        contentBody.append(tipsCard);
        panel.append(dragBar).append(heroCard).append(controlCard).append(scrollHost);
        root.append(panel);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 2048, 1152,
                new DeterministicTextMeasureService());
        DocumentLayoutBox scrollHostBox = rootBox.getChildren().get(0).getChildren().get(3);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);
        int scrollHostHitX = scrollHostBox.getContentLeft() + 10;
        int scrollHostHitY = scrollHostBox.getContentTop() + 10;

        Assert.assertNotNull(DocumentHitTestEngine.hitTest(rootBox, scrollState, 1784, 54));
        Assert.assertTrue(scrollState.getMaxScrollTop(scrollHost) > 0);
        Assert.assertNotNull(DocumentHitTestEngine.hitTest(rootBox, scrollState, scrollHostHitX, scrollHostHitY));
        Assert.assertTrue(scrollState.handleWheel(rootBox, scrollHostHitX, scrollHostHitY, -120, 1L));
        Assert.assertTrue(scrollState.getScrollTop(scrollHost) > 0);
    }

    /**
     * 验证 scroll host 的滚动范围会递归计入后代卡片中的文本高度，而不是只看直接子盒。
     */
    @Test
    public void shouldMeasureNestedScrollableContentHeightRecursively() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode scrollHost = document.div();
        ElementNode contentBody = document.div();
        ElementNode card = document.div();

        root.style()
                .setWidth(UiStyleLength.px(360))
                .setHeight(UiStyleLength.px(180));
        scrollHost.style()
                .setWidth(UiStyleLength.px(320))
                .setHeight(UiStyleLength.px(80))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        contentBody.style()
                .setDisplay(club.heiqi.uilib.ui.style.UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.UiAlignItems.STRETCH)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(6));
        card.style()
                .setDisplay(club.heiqi.uilib.ui.style.UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.UiAlignItems.START)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(6))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(3));
        card.append(createTextBlock(document, "会话概览"));
        card.append(createTextBlock(document,
                "容器界面上方可见。点击次数 0，备注：把鼠标移到背包界面后尝试编辑我。继续补充说明，确保在较窄宽度下发生多行换行。"
                        + "继续补充第二句说明，避免滚动范围只靠卡片自身 padding 恰好通过。"));
        contentBody.append(card);
        scrollHost.append(contentBody);
        root.append(scrollHost);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 360, 180,
                new DeterministicTextMeasureService());
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);

        Assert.assertTrue(scrollState.getMaxScrollTop(scrollHost) > 0);
    }

    private static ElementNode createCard(UiDocument document, int index) {
        ElementNode card = document.div();
        card.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setMargin(UiStyleLength.px(6))
                .setBorderWidth(UiStyleLength.px(1));
        card.append(createTextBlock(document, "卡片 " + index + " 标题"));
        card.append(createTextBlock(document, "卡片 " + index + " 描述：验证 HUD 固定正文区滚动范围计算。"));
        card.append(createTextBlock(document,
                "卡片 " + index + " 正文：这是一段较长的中文说明，用于制造稳定换行与显著高度，"
                        + "确保总高度明显超过 360 像素视口。继续补充第二句说明，避免测试只依赖空白 margin 通过。"));
        return card;
    }

    private static ElementNode createTextBlock(UiDocument document, String text) {
        ElementNode block = document.div();
        block.style().setWidth(UiStyleLength.percent(1.0F));
        block.appendText(text);
        return block;
    }

    private static ElementNode createAutoWidthTextBlock(UiDocument document, String text) {
        ElementNode block = document.div();
        block.style().setWidth(UiStyleLength.auto());
        block.appendText(text);
        return block;
    }

    private static final class DeterministicTextMeasureService implements club.heiqi.uilib.ui.text.TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 4;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }
            int maxLength = Math.max(0, targetWidth / 4);
            return text.substring(0, Math.min(text.length(), maxLength));
        }

        @Override
        public java.util.List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            if (text == null || text.isEmpty() || wrapWidth <= 0) {
                return java.util.Collections.emptyList();
            }
            java.util.List<String> lines = new java.util.ArrayList<String>();
            int maxCharsPerLine = Math.max(1, wrapWidth / 4);
            for (int index = 0; index < text.length(); index += maxCharsPerLine) {
                lines.add(text.substring(index, Math.min(text.length(), index + maxCharsPerLine)));
            }
            return lines;
        }
    }

    private static void configureScroller(ElementNode scroller) {
        scroller.style()
                .setWidth(UiStyleLength.px(70))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
    }
}
