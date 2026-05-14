package club.heiqi.uilib.ui.screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementDragEvent;
import club.heiqi.uilib.ui.dom.DocumentElementDragEndHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDragOverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDragStartHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiJustifyContent;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleInsets;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * HTML-like 列表元素组件拖拽测试页控制器。
 */
final class HtmlLikeListDragDocumentPageController extends DocumentPageController {

    private static final int ROW_HEIGHT = 48;
    private static final List<String> DEFAULT_ITEMS = Arrays.asList("产品设计", "用户调研", "技术选型", "前端开发", "测试上线");

    private final DocumentPageAuthoringSurface documentPage;
    private final UiDocument document;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;
    private final List<String> items = new ArrayList<String>(DEFAULT_ITEMS);
    private ElementNode listElement;
    private ElementNode stateElement;
    private String draggingItem;

    /**
     * 创建列表元素组件拖拽测试页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param documentPage 文档页面壳
     */
    HtmlLikeListDragDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface documentPage) {
        this(documentUi, documentPage, DefaultTextMeasureService.getInstance());
    }

    /**
     * 使用指定文本测量服务创建列表元素组件拖拽测试页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param documentPage 文档页面壳
     * @param textMeasureService HTML-like 文本测量服务
     */
    HtmlLikeListDragDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface documentPage,
            TextMeasureService textMeasureService) {
        Objects.requireNonNull(documentUi, "documentUi");
        this.documentPage = Objects.requireNonNull(documentPage, "documentPage");
        this.document = UiDocument.create();
        createDocument(document);
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 760, 520,
                Objects.requireNonNull(textMeasureService, "textMeasureService"));
        this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));
    }

    @Override
    void configureDocumentPage() {
        documentPage.setContentWidthRange(700, 1080)
                .setMinContentHeight(540)
                .setViewportFillRatio(0.94F, 0.92F);
    }

    @Override
    void buildDocument() {
        documentPage.addBlock(htmlLikeDocumentWidget);
    }

    /**
     * 返回当前页面使用的 HTML-like 适配组件。
     *
     * @return HTML-like 文档适配组件
     */
    HtmlLikeDocumentWidget getHtmlLikeDocumentWidget() {
        return htmlLikeDocumentWidget;
    }

    private void createDocument(UiDocument document) {
        ElementNode root = document.getRootElement();
        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.CENTER)
                .setPadding(UiStyleLength.px(40))
                .setBackgroundColor(0xFFF5F5F5)
                .setTextColor(0xFF0F172A)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);

        ElementNode section = document.div();
        section.style()
                .setWidth(UiStyleLength.px(600))
                .setBackgroundColor(0xFFFFFFFF)
                .setBorderRadius(UiStyleLength.px(12))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        root.append(section);

        ElementNode header = document.div();
        header.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setHeight(UiStyleLength.px(76))
                .setPadding(UiStyleLength.px(20))
                .setBackgroundColor(0xFF2563EB)
                .setTextColor(0xFFFFFFFF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        header.appendText("列表元素组件拖拽");
        section.append(header);

        listElement = document.div();
        listElement.setAttribute("data-drag-list", "draggable-list");
        listElement.style()
                .setPadding(UiStyleLength.px(10));
        section.append(listElement);

        stateElement = document.div();
        stateElement.setAttribute("data-drag-state", "order");
        stateElement.style()
                .setHeight(UiStyleLength.px(70))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(18), UiStyleLength.px(20), UiStyleLength.px(18),
                        UiStyleLength.px(20)))
                .setBackgroundColor(0xFFF8FAFC)
                .setBorderColor(0xFFE2E8F0)
                .setBorderWidth(UiStyleLength.px(1))
                .setTextColor(0xFF64748B);
        section.append(stateElement);

        rebuildList();
        updateStateText();
    }

    private void rebuildList() {
        listElement.clearChildren();
        for (String item : items) {
            ElementNode row = document.element("li");
            row.setAttribute("data-drag-item", item);
            row.setAttribute("draggable", "true");
            row.style()
                    .setDisplay(UiDisplay.FLEX)
                    .setFlexDirection(UiFlexDirection.ROW)
                    .setAlignItems(UiAlignItems.CENTER)
                    .setHeight(UiStyleLength.px(ROW_HEIGHT))
                    .setMargin(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(10), UiStyleLength.px(8),
                            UiStyleLength.px(10)))
                    .setPadding(UiStyleInsets.of(UiStyleLength.px(14), UiStyleLength.px(20), UiStyleLength.px(14),
                            UiStyleLength.px(20)))
                    .setBackgroundColor(item.equals(draggingItem) ? 0xFFD3E5FF : 0xFFF8FAFC)
                    .setBorderColor(0xFFE2E8F0)
                    .setBorderWidth(UiStyleLength.px(2))
                    .setBorderRadius(UiStyleLength.px(8))
                    .setOpacity(item.equals(draggingItem) ? 0.42F : 1.0F)
                    .setTextColor(0xFF0F172A);

            ElementNode label = document.div();
            label.style()
                    .setFlexGrow(1.0F)
                    .setTextColor(0xFF0F172A);
            label.appendText(item);
            row.append(label);
            row.setDragStartHandler(createItemDragStartHandler(item));
            row.setDragEndHandler(createItemDragEndHandler(item));
            listElement.append(row);
        }
        listElement.setDragOverHandler(createListDragOverHandler());
    }

    private DocumentElementDragStartHandler createItemDragStartHandler(final String item) {
        return new DocumentElementDragStartHandler() {
            @Override
            public boolean onDragStart(DocumentElementDragEvent event) {
                draggingItem = item;
                rebuildList();
                return true;
            }
        };
    }

    private DocumentElementDragEndHandler createItemDragEndHandler(final String item) {
        return new DocumentElementDragEndHandler() {
            @Override
            public boolean onDragEnd(DocumentElementDragEvent event) {
                if (!item.equals(draggingItem)) {
                    return false;
                }
                draggingItem = null;
                rebuildList();
                updateStateText();
                return true;
            }
        };
    }

    private DocumentElementDragOverHandler createListDragOverHandler() {
        return new DocumentElementDragOverHandler() {
            @Override
            public boolean onDragOver(DocumentElementDragEvent event) {
                moveDraggingItem(resolveTargetIndex(event));
                return true;
            }
        };
    }

    private int resolveTargetIndex(DocumentElementDragEvent event) {
        if (event == null) {
            return 0;
        }
        DocumentLayoutBox listBox = findLayoutBox(DocumentLayoutEngine.layoutViewportRoot(document.getRootElement(),
                htmlLikeDocumentWidget.getWidth(), htmlLikeDocumentWidget.getHeight(), htmlLikeDocumentWidget.getTextMeasureService()),
                listElement);
        if (listBox == null || listBox.getChildren().isEmpty()) {
            return 0;
        }
        int documentY = event.getDocumentY();
        List<DocumentLayoutBox> itemBoxes = listBox.getChildren();
        for (int index = 0; index < itemBoxes.size(); index++) {
            DocumentLayoutBox itemBox = itemBoxes.get(index);
            if (itemBox.getElement().getAttribute("data-drag-item") == null) {
                continue;
            }
            int middleY = (itemBox.getTop() + itemBox.getBottom()) / 2;
            if (documentY < middleY) {
                return index;
            }
        }
        return itemBoxes.size();
    }

    private static DocumentLayoutBox findLayoutBox(DocumentLayoutBox currentBox, ElementNode element) {
        if (currentBox == null || element == null) {
            return null;
        }
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

    private void moveDraggingItem(int targetIndex) {
        if (draggingItem == null) {
            return;
        }
        int currentIndex = items.indexOf(draggingItem);
        if (currentIndex < 0 || currentIndex == targetIndex) {
            return;
        }
        String item = items.remove(currentIndex);
        if (targetIndex > currentIndex) {
            targetIndex--;
        }
        targetIndex = Math.max(0, Math.min(targetIndex, items.size()));
        items.add(targetIndex, item);
        rebuildList();
    }

    private void updateStateText() {
        stateElement.clearChildren();
        stateElement.appendText("当前顺序：" + joinItems());
    }

    private String joinItems() {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < items.size(); index++) {
            if (index > 0) {
                builder.append(" → ");
            }
            builder.append(items.get(index));
        }
        return builder.toString();
    }
}
