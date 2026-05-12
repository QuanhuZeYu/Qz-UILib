package club.heiqi.uilib.ui.layout;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiPosition;
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
