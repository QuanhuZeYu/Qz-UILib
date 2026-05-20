package club.heiqi.uilib.ui.screen.example;

import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;

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
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * HTML-like 列表元素组件拖拽测试页控制器。
 */
public final class HtmlLikeListDragDocumentPageController extends DocumentPageController {

    private static final int ROW_HEIGHT = 48;
    private static final String DRAG_HANDLE_ICON_SRC = "https://img.icons8.com/ios-filled/50/93c5fd/drag-reorder.png";
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
    public HtmlLikeListDragDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface documentPage) {
        this(Objects.requireNonNull(documentUi, "documentUi"), documentPage, documentUi.getTextMeasureService());
    }

    /**
     * 使用指定文本测量服务创建列表元素组件拖拽测试页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param documentPage 文档页面壳
     * @param textMeasureService HTML-like 文本测量服务
     */
    public HtmlLikeListDragDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface documentPage,
            TextMeasureService textMeasureService) {
        Objects.requireNonNull(documentUi, "documentUi");
        this.documentPage = Objects.requireNonNull(documentPage, "documentPage");
        this.document = UiDocument.create();
        this.document.setDefaultTextContentMode(documentUi.getDefaultTextContentMode());
        createDocument(document);
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 760, 520,
                Objects.requireNonNull(textMeasureService, "textMeasureService"));
        this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));
    }

    @Override
    public void configureDocumentPage() {
        documentPage.setContentWidthRange(700, 1080)
                .setMinContentHeight(540)
                .setViewportFillRatio(0.94F, 0.92F);
    }

    @Override
    public void buildDocument() {
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
                .setAlignItems(UiAlignItems.STRETCH)
                .setPadding(UiStyleLength.px(20))
                .setBackgroundColor(0xF00A1020)
                .setBorderColor(0xFF60A5FA)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(22))
                .setTextColor(0xFFE8EEFF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);

        ElementNode section = document.div();
        section.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(16))
                .setBackgroundColor(0xFF101827)
                .setBorderColor(0xFF405F9C)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(18))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        root.append(section);

        ElementNode header = document.div();
        header.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.STRETCH)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setHeight(UiStyleLength.px(118))
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF93C5FD)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(16))
                .setTextColor(0xFFF8FAFC)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        header.appendText("列表元素组件拖拽");
        header.appendText("HTML-like sortable list / dragstart -> dragover -> dragend");
        header.appendText("拖住任意列表项上下移动，验证 DOM 顺序与状态文本同步。 ");
        section.append(header);

        listElement = document.div();
        listElement.setAttribute("data-drag-list", "draggable-list");
        listElement.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(14), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF17233B)
                .setBorderColor(0xFF2563EB)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(16))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        section.append(listElement);

        stateElement = document.div();
        stateElement.setAttribute("data-drag-state", "order");
        stateElement.style()
                .setHeight(UiStyleLength.px(70))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(14), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(18), UiStyleLength.px(20), UiStyleLength.px(18),
                        UiStyleLength.px(20)))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF38BDF8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFBAE6FD)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
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
                    .setMargin(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(0), UiStyleLength.px(8),
                            UiStyleLength.px(0)))
                    .setPadding(UiStyleInsets.of(UiStyleLength.px(12), UiStyleLength.px(16), UiStyleLength.px(12),
                            UiStyleLength.px(14)))
                    .setBackgroundColor(item.equals(draggingItem) ? 0xFF1D4ED8 : 0xFF1E293B)
                    .setBorderColor(item.equals(draggingItem) ? 0xFFBFDBFE : 0xFF475569)
                    .setBorderWidth(UiStyleLength.px(1))
                    .setBorderStyle(UiBorderStyle.SOLID)
                    .setBorderRadius(UiStyleLength.px(12))
                    .setOpacity(item.equals(draggingItem) ? 0.62F : 1.0F)
                    .setTextColor(0xFFEAF1FF);

            ElementNode handle = document.div();
            handle.style()
                    .setDisplay(UiDisplay.FLEX)
                    .setFlexDirection(UiFlexDirection.ROW)
                    .setAlignItems(UiAlignItems.CENTER)
                    .setJustifyContent(UiJustifyContent.CENTER)
                    .setWidth(UiStyleLength.px(28))
                    .setHeight(UiStyleLength.px(24))
                    .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(12), UiStyleLength.px(0),
                            UiStyleLength.px(0)))
                    .setBackgroundColor(item.equals(draggingItem) ? 0xFF172554 : 0xFF0F172A)
                    .setBorderColor(item.equals(draggingItem) ? 0xFF93C5FD : 0xFF334155)
                    .setBorderWidth(UiStyleLength.px(1))
                    .setBorderStyle(UiBorderStyle.SOLID)
                    .setBorderRadius(UiStyleLength.px(999))
                    .setTextColor(0xFF93C5FD)
                    .setOverflowX(UiOverflow.HIDDEN)
                    .setOverflowY(UiOverflow.HIDDEN);
            ElementNode handleIcon = document.img();
            handleIcon.setAttribute("src", DRAG_HANDLE_ICON_SRC)
                    .setAttribute("alt", "拖拽把手")
                    .setAttribute("data-drag-handle-icon", item);
            handleIcon.style()
                    .setWidth(UiStyleLength.px(14))
                    .setHeight(UiStyleLength.px(14))
                    .setOpacity(item.equals(draggingItem) ? 0.92F : 0.62F);
            handle.append(handleIcon);
            row.append(handle);

            ElementNode label = document.div();
            label.style()
                    .setFlexGrow(1.0F)
                    .setTextColor(0xFFEAF1FF);
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
