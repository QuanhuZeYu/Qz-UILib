package club.heiqi.uilib.ui.screen.example;

import club.heiqi.uilib.ui.screen.page.DirectDocumentPageAuthoringSurface;

import club.heiqi.uilib.ui.screen.page.DocumentUiScope;

import java.util.ArrayList;
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
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `HtmlLikeListDragDocumentPageController` 的 HTML-like 列表拖拽测试。
 */
public class HtmlLikeListDragDocumentPageControllerTest {

    /**
     * 验证页面会按参考 HTML 构建列表拖拽演示结构。
     */
    @Test
    public void shouldBuildListDragDemoDocumentTree() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        List<String> texts = collectDocumentTexts(fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(containsText(texts, "列表元素组件拖拽"));
        Assert.assertTrue(containsText(texts, "产品设计"));
        Assert.assertTrue(containsText(texts, "用户调研"));
        Assert.assertTrue(containsText(texts, "技术选型"));
        Assert.assertTrue(containsText(texts, "前端开发"));
        Assert.assertTrue(containsText(texts, "测试上线"));
        Assert.assertTrue(containsText(texts, "当前顺序：产品设计 → 用户调研 → 技术选型 → 前端开发 → 测试上线"));
    }

    /**
     * 验证拖拽列表项把手会更新当前顺序。
     */
    @Test
    public void shouldReorderItemsWhenHandleDraggedAcrossRows() {
        TestFixture fixture = new TestFixture();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        ElementNode root = widget.getDocument().getRootElement();
        ElementNode firstItem = findElementByAttribute(root, "data-drag-item", "产品设计");
        ElementNode list = findElementByAttribute(root, "data-drag-list", "draggable-list");

        Assert.assertNotNull(firstItem);
        Assert.assertNotNull(list);
        Assert.assertEquals("true", firstItem.getAttribute("draggable"));
        Assert.assertNotNull(firstItem.getDragStartHandler());
        Assert.assertNotNull(firstItem.getDragEndHandler());
        Assert.assertNotNull(list.getDragOverHandler());
        int startY = resolveItemMiddleY(widget, firstItem);
        int dragY = resolveItemBottomY(widget, findElementByAttribute(root, "data-drag-item", "前端开发")) + 1;

        firstItem.getDragStartHandler().onDragStart(new DocumentElementDragEvent(firstItem, firstItem, 0, startY, 0,
                startY, 0, 0, 0, 1L,
                DocumentElementDragEvent.DragPhase.START));
        list.getDragOverHandler().onDragOver(new DocumentElementDragEvent(firstItem, list, 0, startY, 0, dragY, 0,
                dragY - startY, 0, 2L,
                DocumentElementDragEvent.DragPhase.DRAG));
        firstItem.getDragEndHandler().onDragEnd(new DocumentElementDragEvent(firstItem, firstItem, 0, startY, 0,
                dragY, 0, 0, 0, 3L, DocumentElementDragEvent.DragPhase.END));

        List<String> texts = collectDocumentTexts(widget);
        Assert.assertTrue(containsText(texts, "当前顺序：用户调研 → 技术选型 → 前端开发 → 产品设计 → 测试上线"));
    }

    private static List<String> collectDocumentTexts(HtmlLikeDocumentWidget widget) {
        List<String> texts = new ArrayList<String>();
        collectTextsFromNode(widget.getDocument().getRootElement(), texts);
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
        private final DocumentUiScope documentUi = new DocumentUiScope(textMeasureService, UiRuntimeAdapters.empty());
        private final DirectDocumentPageAuthoringSurface pageSurface = new DirectDocumentPageAuthoringSurface();
        private final HtmlLikeListDragDocumentPageController controller = new HtmlLikeListDragDocumentPageController(
                documentUi, pageSurface, textMeasureService);
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
