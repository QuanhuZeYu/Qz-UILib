package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementDragEvent;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentNodeType;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `FontSortOrderControl` 的字体排序交互测试。
 */
public class FontSortOrderControlTest {

    /**
     * 验证字体排序控件会构建分页工具栏、拖拽列表与序号输入框。
     */
    @Test
    public void shouldBuildFontSortRowsWithDragAndOrdinalInputs() {
        TestFixture fixture = new TestFixture();
        ElementNode root = fixture.document.getRootElement();
        ElementNode firstItem = findElementByAttribute(root, "data-font-sort-item", "Alpha");
        ElementNode firstInput = findElementByAttribute(root, "data-font-sort-order-input", "Alpha");

        Assert.assertNotNull(firstItem);
        Assert.assertNotNull(firstInput);
        Assert.assertEquals("true", firstItem.getAttribute("draggable"));
        Assert.assertNotNull(firstItem.getDragStartHandler());
        Assert.assertNotNull(firstItem.getDragEndHandler());
        Assert.assertNotNull(findElementByAttribute(root, "data-font-sort-list", "fonts").getDragOverHandler());
        Assert.assertNotNull(findElementByAttribute(root, "data-font-sort-search-input", "fonts"));
        Assert.assertNotNull(findElementByAttribute(root, "data-font-sort-page-input", "fonts"));
        Assert.assertNotNull(findElementByAttribute(root, "data-font-sort-jump-input", "fonts"));
        Assert.assertTrue(containsText(collectDocumentTexts(root), "共 4 个字体"));
    }

    /**
     * 验证大量字体时仅渲染当前页，避免 300+ 字体一次性撑成长列表。
     */
    @Test
    public void shouldRenderOnlyCurrentPageForLargeFontLists() {
        TestFixture fixture = new TestFixture(createFontNames(60));

        Assert.assertEquals(25, fixture.control.getVisibleItemsSnapshotForTesting().size());
        Assert.assertNotNull(findElementByAttribute(fixture.document.getRootElement(), "data-font-sort-item", "Font-001"));
        Assert.assertNull(findElementByAttribute(fixture.document.getRootElement(), "data-font-sort-item", "Font-026"));
    }

    /**
     * 验证搜索只影响可见集合，不改变真实排序。
     */
    @Test
    public void shouldFilterVisibleFontsWithoutChangingOrder() {
        TestFixture fixture = new TestFixture(createFontNames(40));

        fixture.control.setFilterTextForTesting("Font-03");

        Assert.assertEquals(Arrays.asList("Font-030", "Font-031", "Font-032", "Font-033", "Font-034", "Font-035",
                "Font-036", "Font-037", "Font-038", "Font-039"), fixture.control.getVisibleItemsSnapshotForTesting());
        Assert.assertEquals("Font-001", fixture.control.getItemsSnapshot().get(0));
    }

    /**
     * 验证目标序号移动支持跨页定位。
     */
    @Test
    public void shouldMoveFontAcrossPagesWhenOrdinalSubmitted() {
        TestFixture fixture = new TestFixture(createFontNames(60));

        Assert.assertTrue(fixture.control.moveItemToOrdinalForTesting("Font-001", 40));

        Assert.assertEquals("Font-001", fixture.control.getItemsSnapshot().get(39));
        Assert.assertEquals(1, fixture.control.getPageIndexForTesting());
        Assert.assertEquals(fixture.control.getItemsSnapshot(), fixture.lastChangedOrder);
    }

    /**
     * 验证跳转序号会定位到对应页。
     */
    @Test
    public void shouldJumpToPageContainingOrdinal() {
        TestFixture fixture = new TestFixture(createFontNames(60));

        Assert.assertTrue(fixture.control.jumpToOrdinalForTesting(52));

        Assert.assertEquals(2, fixture.control.getPageIndexForTesting());
        Assert.assertTrue(fixture.control.getVisibleItemsSnapshotForTesting().contains("Font-052"));
    }

    /**
     * 验证序号输入语义会立即移动字体并回调草稿顺序。
     */
    @Test
    public void shouldMoveFontWhenOrdinalSubmitted() {
        TestFixture fixture = new TestFixture();

        Assert.assertTrue(fixture.control.moveItemToOrdinalForTesting("Alpha", 3));

        Assert.assertEquals(Arrays.asList("Bravo", "Charlie", "Alpha", "Delta"), fixture.control.getItemsSnapshot());
        Assert.assertEquals(Arrays.asList("Bravo", "Charlie", "Alpha", "Delta"), fixture.lastChangedOrder);
    }

    /**
     * 验证拖拽字体行会更新当前顺序。
     */
    @Test
    public void shouldReorderFontsWhenDraggedAcrossRows() {
        TestFixture fixture = new TestFixture();
        ElementNode root = fixture.document.getRootElement();
        ElementNode firstItem = findElementByAttribute(root, "data-font-sort-item", "Alpha");
        ElementNode list = findElementByAttribute(root, "data-font-sort-list", "fonts");
        int initialNodeCount = countElementNodes(root);
        int startY = resolveItemMiddleY(fixture.widget, firstItem);
        int dragY = resolveItemBottomY(fixture.widget, findElementByAttribute(root, "data-font-sort-item", "Charlie")) + 1;

        firstItem.getDragStartHandler().onDragStart(new DocumentElementDragEvent(firstItem, firstItem, 0, startY, 0,
                startY, 0, 0, 0, 1L, DocumentElementDragEvent.DragPhase.START));
        list.getDragOverHandler().onDragOver(new DocumentElementDragEvent(firstItem, list, 0, startY, 0, dragY, 0,
                dragY - startY, 0, 2L, DocumentElementDragEvent.DragPhase.DRAG));
        firstItem.getDragEndHandler().onDragEnd(new DocumentElementDragEvent(firstItem, firstItem, 0, startY, 0,
                dragY, 0, 0, 0, 3L, DocumentElementDragEvent.DragPhase.END));

        Assert.assertEquals(Arrays.asList("Bravo", "Charlie", "Alpha", "Delta"), fixture.control.getItemsSnapshot());
        Assert.assertEquals(Arrays.asList("Bravo", "Charlie", "Alpha", "Delta"), fixture.lastChangedOrder);
        Assert.assertSame(firstItem, findElementByAttribute(root, "data-font-sort-item", "Alpha"));
        Assert.assertEquals(initialNodeCount, countElementNodes(root));
    }

    private static List<String> collectDocumentTexts(ElementNode root) {
        List<String> texts = new ArrayList<String>();
        collectTextsFromNode(root, texts);
        return texts;
    }

    private static void collectTextsFromNode(DocumentNode node, List<String> texts) {
        if (node.getNodeType() == DocumentNodeType.TEXT) {
            String text = ((TextNode) node).getText();
            if (text != null && !text.isEmpty()) {
                texts.add(text);
            }
        }
        if (node.getNodeType() == DocumentNodeType.ELEMENT) {
            ElementNode element = (ElementNode) node;
            for (DocumentNode child : element.getChildren()) {
                collectTextsFromNode(child, texts);
            }
        }
    }

    private static boolean containsText(List<String> texts, String expectedSnippet) {
        for (String text : texts) {
            if (text != null && text.contains(expectedSnippet)) {
                return true;
            }
        }
        return false;
    }

    private static ElementNode findElementByAttribute(ElementNode element, String attributeName, String attributeValue) {
        if (attributeValue.equals(element.getAttribute(attributeName))) {
            return element;
        }
        for (DocumentNode child : element.getChildren()) {
            if (child.getNodeType() == DocumentNodeType.ELEMENT) {
                ElementNode found = findElementByAttribute((ElementNode) child, attributeName, attributeValue);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int countElementNodes(DocumentNode node) {
        int count = node.getNodeType() == DocumentNodeType.ELEMENT ? 1 : 0;
        if (node.getNodeType() == DocumentNodeType.ELEMENT) {
            for (DocumentNode child : ((ElementNode) node).getChildren()) {
                count += countElementNodes(child);
            }
        }
        return count;
    }

    private static int resolveItemMiddleY(HtmlLikeDocumentWidget widget, ElementNode item) {
        DocumentLayoutBox itemBox = resolveItemBox(widget, item);
        return (itemBox.getTop() + itemBox.getBottom()) / 2;
    }

    private static int resolveItemBottomY(HtmlLikeDocumentWidget widget, ElementNode item) {
        return resolveItemBox(widget, item).getBottom();
    }

    private static DocumentLayoutBox resolveItemBox(HtmlLikeDocumentWidget widget, ElementNode item) {
        DocumentLayoutBox rootBox = DocumentLayoutEngine.layoutViewportRoot(widget.getDocument().getRootElement(),
                widget.getWidth(), widget.getHeight(), widget.getTextMeasureService());
        DocumentLayoutBox itemBox = findLayoutBox(rootBox, item);
        Assert.assertNotNull(itemBox);
        return itemBox;
    }

    private static DocumentLayoutBox findLayoutBox(DocumentLayoutBox currentBox, ElementNode element) {
        if (currentBox.getElement() == element) {
            return currentBox;
        }
        for (DocumentLayoutBox childBox : currentBox.getChildren()) {
            DocumentLayoutBox foundBox = findLayoutBox(childBox, element);
            if (foundBox != null) {
                return foundBox;
            }
        }
        return null;
    }

    private static final class TestFixture {

        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final UiDocument document = UiDocument.create();
        private final HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 760, 520, textMeasureService);
        private final FontSortOrderControl control;
        private List<String> lastChangedOrder = Collections.emptyList();

        private TestFixture() {
            this(Arrays.asList("Alpha", "Bravo", "Charlie", "Delta"));
        }

        private TestFixture(List<String> items) {
            control = new FontSortOrderControl(document, widget, items,
                    new FontSortOrderControl.FontSortOrderChangeListener() {
                        @Override
                        public void onOrderChanged(List<String> orderedItems) {
                            lastChangedOrder = orderedItems;
                        }
                    });
            document.getRootElement().append(control.getElement());
        }
    }

    private static List<String> createFontNames(int count) {
        List<String> names = new ArrayList<String>();
        for (int index = 1; index <= count; index++) {
            names.add(String.format("Font-%03d", Integer.valueOf(index)));
        }
        return names;
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
            return text == null ? "" : text;
        }

        @Override
        public java.util.List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }
}
