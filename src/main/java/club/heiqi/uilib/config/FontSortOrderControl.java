package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementDragEndHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDragEvent;
import club.heiqi.uilib.ui.dom.DocumentElementDragOverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDragStartHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.control.DocumentTextInputChangeEvent;
import club.heiqi.uilib.ui.control.DocumentTextInputChangeHandler;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiJustifyContent;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleInsets;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * 字体排序二级页使用的分页排序控件。
 */
final class FontSortOrderControl {

    private static final int ROW_HEIGHT = 40;
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int COMPACT_PAGE_SIZE = 50;
    private static final String DRAG_HANDLE_ICON_SRC = "https://img.icons8.com/ios-filled/50/d1d5db/drag-reorder.png";
    private final UiDocument document;
    private final HtmlLikeDocumentWidget documentWidget;
    private final FontSortOrderChangeListener changeListener;
    private final List<String> items = new ArrayList<String>();
    private final List<String> visibleItems = new ArrayList<String>();
    private final List<Integer> visibleGlobalIndexes = new ArrayList<Integer>();
    private final Map<String, FontSortRow> rowsByItem = new LinkedHashMap<String, FontSortRow>();
    private final ElementNode rootElement;
    private final ElementNode listElement;
    private final TextNode stateText;
    private final TextNode pageText;
    private final TextNode resultText;
    private final DocumentTextInputControl searchInput;
    private final DocumentTextInputControl pageInput;
    private final DocumentTextInputControl jumpInput;
    private final DocumentButtonControl clearSearchButton;
    private final DocumentButtonControl previousPageButton;
    private final DocumentButtonControl nextPageButton;
    private final DocumentButtonControl pageSizeButton;
    private final List<Integer> cachedRowMiddleYs = new ArrayList<Integer>();
    private String filterText = "";
    private String draggingItem;
    private int pageIndex;
    private int pageSize = DEFAULT_PAGE_SIZE;

    /**
     * 创建字体排序控件。
     *
     * @param document 所属文档
     * @param documentWidget 文档适配组件
     * @param initialItems 初始字体顺序
     * @param changeListener 排序变更监听器
     */
    FontSortOrderControl(UiDocument document, HtmlLikeDocumentWidget documentWidget, List<String> initialItems,
            FontSortOrderChangeListener changeListener) {
        this.document = Objects.requireNonNull(document, "document");
        this.documentWidget = Objects.requireNonNull(documentWidget, "documentWidget");
        this.changeListener = changeListener;
        replaceItems(initialItems);

        this.rootElement = document.div();
        configureRoot(rootElement);
        ElementNode toolbar = document.div();
        configureToolbar(toolbar);
        this.searchInput = createSearchInput();
        this.clearSearchButton = createToolbarButton("清空");
        this.resultText = appendSearchControls(toolbar);
        this.pageInput = createPageInput();
        this.jumpInput = createJumpInput();
        this.previousPageButton = createToolbarButton("上一页");
        this.nextPageButton = createToolbarButton("下一页");
        this.pageSizeButton = createToolbarButton("每页 25");
        this.pageText = appendPageControls(toolbar);
        rootElement.append(toolbar);

        this.listElement = document.div();
        configureList(listElement);
        rootElement.append(listElement);
        this.stateText = this.resultText;

        refreshView(false);
    }

    /**
     * 返回控件根元素。
     *
     * @return 控件根元素
     */
    ElementNode getElement() {
        return rootElement;
    }

    /**
     * 返回当前字体顺序快照。
     *
     * @return 字体顺序快照
     */
    List<String> getItemsSnapshot() {
        return new ArrayList<String>(items);
    }

    /**
     * 以一组新字体顺序替换当前列表。
     *
     * @param updatedItems 新字体顺序
     */
    void setItems(List<String> updatedItems) {
        replaceItems(updatedItems);
        draggingItem = null;
        pageIndex = 0;
        filterText = "";
        searchInput.setText("");
        refreshView(false);
        fireChange();
    }

    /**
     * 根据用户输入的一基序号移动字体。
     *
     * @param item 字体名称
     * @param targetOrdinal 目标一基序号
     * @return 是否完成移动
     */
    boolean moveItemToOrdinalForTesting(String item, int targetOrdinal) {
        return moveItemToOrdinal(item, targetOrdinal);
    }

    /**
     * 设置筛选文本，仅供测试验证视图语义。
     *
     * @param text 筛选文本
     */
    void setFilterTextForTesting(String text) {
        filterText = normalizeFilterText(text);
        searchInput.setText(text == null ? "" : text);
        pageIndex = 0;
        draggingItem = null;
        refreshView(false);
    }

    /**
     * 返回当前可见字体快照。
     *
     * @return 当前页可见字体
     */
    List<String> getVisibleItemsSnapshotForTesting() {
        return new ArrayList<String>(visibleItems);
    }

    /**
     * 设置每页数量，仅供测试验证分页。
     *
     * @param pageSize 每页数量
     */
    void setPageSizeForTesting(int pageSize) {
        this.pageSize = Math.max(1, pageSize);
        pageIndex = 0;
        refreshView(false);
    }

    /**
     * 返回当前页码，仅供测试验证定位。
     *
     * @return 零基页码
     */
    int getPageIndexForTesting() {
        return pageIndex;
    }

    /**
     * 跳转到全局序号所在页，仅供测试验证定位。
     *
     * @param ordinal 一基全局序号
     * @return 是否完成跳转
     */
    boolean jumpToOrdinalForTesting(int ordinal) {
        return jumpToOrdinal(ordinal);
    }

    private boolean moveItemToOrdinal(String item, int targetOrdinal) {
        if (item == null || targetOrdinal < 1 || targetOrdinal > items.size()) {
            return false;
        }
        int currentIndex = items.indexOf(item);
        int targetIndex = targetOrdinal - 1;
        if (currentIndex < 0 || currentIndex == targetIndex) {
            return false;
        }
        moveItem(currentIndex, targetIndex, true);
        pageIndex = resolvePageIndexForGlobalIndex(targetIndex);
        refreshView(false);
        return true;
    }

    private void replaceItems(List<String> updatedItems) {
        items.clear();
        if (updatedItems == null) {
            return;
        }
        for (String item : updatedItems) {
            String normalized = normalizeItem(item);
            if (!normalized.isEmpty() && !items.contains(normalized)) {
                items.add(normalized);
            }
        }
    }

    private void configureRoot(ElementNode root) {
        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8))
                .setWidth(UiStyleLength.percent(1.0F));
    }

    private void configureToolbar(ElementNode toolbar) {
        toolbar.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xCC111827)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(12));
    }

    private TextNode appendSearchControls(ElementNode toolbar) {
        ElementNode row = createToolbarRow();
        ElementNode label = createToolbarLabel("搜索");
        row.append(label);
        searchInput.getElement().setAttribute("data-font-sort-search-input", "fonts");
        row.append(searchInput.getElement());
        clearSearchButton.getElement().setAttribute("data-font-sort-clear-search", "fonts");
        clearSearchButton.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                clearFilter();
            }
        });
        row.append(clearSearchButton.getElement());
        ElementNode result = createToolbarStatus();
        TextNode text = result.appendText("");
        row.append(result);
        toolbar.append(row);
        return text;
    }

    private TextNode appendPageControls(ElementNode toolbar) {
        ElementNode row = createToolbarRow();
        previousPageButton.getElement().setAttribute("data-font-sort-page-prev", "fonts");
        previousPageButton.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                goToPage(pageIndex - 1);
            }
        });
        row.append(previousPageButton.getElement());
        nextPageButton.getElement().setAttribute("data-font-sort-page-next", "fonts");
        nextPageButton.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                goToPage(pageIndex + 1);
            }
        });
        row.append(nextPageButton.getElement());
        pageInput.getElement().setAttribute("data-font-sort-page-input", "fonts");
        row.append(pageInput.getElement());
        jumpInput.getElement().setAttribute("data-font-sort-jump-input", "fonts");
        row.append(jumpInput.getElement());
        pageSizeButton.getElement().setAttribute("data-font-sort-page-size", "fonts");
        pageSizeButton.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                togglePageSize();
            }
        });
        row.append(pageSizeButton.getElement());
        ElementNode pageStatus = createToolbarStatus();
        TextNode text = pageStatus.appendText("");
        row.append(pageStatus);
        toolbar.append(row);
        return text;
    }

    private ElementNode createToolbarRow() {
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8));
        return row;
    }

    private ElementNode createToolbarLabel(String text) {
        ElementNode label = document.div();
        label.style()
                .setWidth(UiStyleLength.px(36))
                .setTextColor(0xFFBAE6FD)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        label.appendText(text);
        return label;
    }

    private ElementNode createToolbarStatus() {
        ElementNode status = document.div();
        status.style()
                .setFlexGrow(1.0F)
                .setTextColor(0xFFBAE6FD)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        return status;
    }

    private DocumentTextInputControl createSearchInput() {
        DocumentTextInputControl input = createToolbarInput("输入字体名片段", 160)
                .setMaxLength(80)
                .setChangeHandler(new DocumentTextInputChangeHandler() {
                    @Override
                    public void onTextChanged(DocumentTextInputChangeEvent event) {
                        filterText = normalizeFilterText(event.getText());
                        pageIndex = 0;
                        draggingItem = null;
                        refreshView(false);
                    }
                });
        return input;
    }

    private DocumentTextInputControl createPageInput() {
        DocumentTextInputControl input = createToolbarInput("页码", 54)
                .setMaxLength(4)
                .setKeyHandler(new DocumentElementKeyHandler() {
                    @Override
                    public boolean onKey(DocumentElementKeyEvent event) {
                        if (!isSubmitKey(event)) {
                            return false;
                        }
                        applyPageInput(event.getCurrentTarget().getAttribute("value"));
                        return true;
                    }
                });
        input.getElement().style().setJustifyContent(UiJustifyContent.CENTER);
        return input;
    }

    private DocumentTextInputControl createJumpInput() {
        DocumentTextInputControl input = createToolbarInput("跳到序号", 80)
                .setMaxLength(4)
                .setKeyHandler(new DocumentElementKeyHandler() {
                    @Override
                    public boolean onKey(DocumentElementKeyEvent event) {
                        if (!isSubmitKey(event)) {
                            return false;
                        }
                        applyJumpInput(event.getCurrentTarget().getAttribute("value"));
                        return true;
                    }
                });
        input.getElement().style().setJustifyContent(UiJustifyContent.CENTER);
        return input;
    }

    private DocumentTextInputControl createToolbarInput(String placeholder, int width) {
        DocumentTextInputControl input = new DocumentTextInputControl(document)
                .setPlaceholder(placeholder)
                .setNormalBackgroundColor(0xFF0F172A)
                .setNormalBorderColor(0xFF334155)
                .setFocusBorderColor(0xFF93C5FD)
                .setTextColors(0xFFF8FAFC, 0xFF64748B, 0xFF64748B);
        input.getElement().style()
                .setWidth(UiStyleLength.px(width))
                .setHeight(UiStyleLength.px(28))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(6), UiStyleLength.px(8), UiStyleLength.px(6),
                        UiStyleLength.px(8)));
        return input;
    }

    private DocumentButtonControl createToolbarButton(String label) {
        DocumentButtonControl button = new DocumentButtonControl(document, label)
                .setBackgroundColors(0xFF1D4ED8, 0xFF2563EB, 0xFF334155)
                .setFocusBorderColor(0xFFBFDBFE)
                .setTextColors(0xFFFFFFFF, 0xFFCBD5E1);
        button.getElement().style()
                .setHeight(UiStyleLength.px(30))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(6), UiStyleLength.px(10), UiStyleLength.px(6),
                        UiStyleLength.px(10)));
        return button;
    }

    private void configureList(ElementNode list) {
        list.setAttribute("data-font-sort-list", "fonts");
        list.style()
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF2563EB)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(14))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        list.setDragOverHandler(createListDragOverHandler());
    }

    private void refreshView(boolean keepPage) {
        rebuildVisibleItems(keepPage);
        createVisibleRows();
        syncToolbarState();
        updateStateText();
    }

    private void rebuildVisibleItems(boolean keepPage) {
        visibleItems.clear();
        visibleGlobalIndexes.clear();
        List<Integer> matchedIndexes = collectMatchedIndexes();
        int totalPages = resolveTotalPages(matchedIndexes.size());
        if (!keepPage) {
            pageIndex = Math.max(0, Math.min(pageIndex, totalPages - 1));
        } else {
            pageIndex = Math.max(0, Math.min(pageIndex, totalPages - 1));
        }
        int start = Math.max(0, pageIndex * pageSize);
        int end = Math.min(matchedIndexes.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            int globalIndex = matchedIndexes.get(index).intValue();
            visibleGlobalIndexes.add(Integer.valueOf(globalIndex));
            visibleItems.add(items.get(globalIndex));
        }
    }

    private List<Integer> collectMatchedIndexes() {
        List<Integer> matchedIndexes = new ArrayList<Integer>();
        for (int index = 0; index < items.size(); index++) {
            String item = items.get(index);
            if (filterText.isEmpty() || item.toLowerCase(Locale.ROOT).contains(filterText)) {
                matchedIndexes.add(Integer.valueOf(index));
            }
        }
        return matchedIndexes;
    }

    private void createVisibleRows() {
        listElement.clearChildren();
        if (visibleItems.isEmpty()) {
            ElementNode empty = document.div();
            empty.style()
                    .setPadding(UiStyleLength.px(16))
                    .setTextColor(0xFF94A3B8);
            empty.appendText(filterText.isEmpty() ? "当前没有字体可排序。" : "没有匹配当前搜索条件的字体。");
            listElement.append(empty);
            return;
        }
        for (int visibleIndex = 0; visibleIndex < visibleItems.size(); visibleIndex++) {
            String item = visibleItems.get(visibleIndex);
            int globalIndex = visibleGlobalIndexes.get(visibleIndex).intValue();
            FontSortRow row = rowsByItem.get(item);
            if (row == null) {
                row = createRow(item, globalIndex, isDragEnabled());
                rowsByItem.put(item, row);
            } else {
                updateRow(row, item, globalIndex, isDragEnabled());
            }
            listElement.append(row.element);
        }
    }

    private FontSortRow createRow(final String item, int globalIndex, boolean dragEnabled) {
        ElementNode row = document.element("li");
        row.setAttribute("data-font-sort-item", item);
        row.setAttribute("data-font-sort-ordinal", String.valueOf(globalIndex + 1));
        row.setAttribute("draggable", dragEnabled ? "true" : "false");
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8))
                .setHeight(UiStyleLength.px(ROW_HEIGHT))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(4), UiStyleLength.px(0), UiStyleLength.px(4),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(6), UiStyleLength.px(10), UiStyleLength.px(6),
                        UiStyleLength.px(10)))
                .setBackgroundColor(item.equals(draggingItem) ? 0xFF1D4ED8 : 0xFF1E293B)
                .setBorderColor(item.equals(draggingItem) ? 0xFFBFDBFE : 0xFF475569)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(10))
                .setOpacity(item.equals(draggingItem) ? 0.62F : 1.0F)
                .setTextColor(0xFFEAF1FF);

        row.append(createHandle(item, dragEnabled));
        OrdinalBadge ordinalBadge = createOrdinalBadge(globalIndex);
        row.append(ordinalBadge.element);
        DocumentTextInputControl orderInput = createOrderInput(item, globalIndex);
        row.append(orderInput.getElement());
        row.append(createLabel(item));
        if (dragEnabled) {
            row.setDragStartHandler(createItemDragStartHandler(item));
            row.setDragEndHandler(createItemDragEndHandler(item));
        }
        return new FontSortRow(row, orderInput, ordinalBadge.text);
    }

    private void updateRow(FontSortRow row, final String item, int globalIndex, boolean dragEnabled) {
        row.element.setAttribute("data-font-sort-ordinal", String.valueOf(globalIndex + 1));
        row.element.setAttribute("draggable", dragEnabled ? "true" : "false");
        row.element.style()
                .setBackgroundColor(item.equals(draggingItem) ? 0xFF1D4ED8 : 0xFF1E293B)
                .setBorderColor(item.equals(draggingItem) ? 0xFFBFDBFE : 0xFF475569)
                .setOpacity(item.equals(draggingItem) ? 0.62F : 1.0F);
        row.ordinalText.setText("#" + (globalIndex + 1));
        row.orderInput.setPlaceholder(String.valueOf(globalIndex + 1));
        row.orderInput.setText("");
        if (dragEnabled) {
            row.element.setDragStartHandler(createItemDragStartHandler(item));
            row.element.setDragEndHandler(createItemDragEndHandler(item));
        } else {
            row.element.setDragStartHandler(null);
            row.element.setDragEndHandler(null);
        }
    }

    private ElementNode createHandle(String item, boolean enabled) {
        ElementNode handle = document.div();
        handle.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setWidth(UiStyleLength.px(26))
                .setHeight(UiStyleLength.px(24))
                .setBackgroundColor(item.equals(draggingItem) ? 0xFF172554 : 0xFF0F172A)
                .setBorderColor(item.equals(draggingItem) ? 0xFF93C5FD : 0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(999))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        handle.setAttribute("data-font-sort-handle", item);
        ElementNode handleIcon = document.img();
        handleIcon.setAttribute("src", DRAG_HANDLE_ICON_SRC)
                .setAttribute("alt", "拖拽把手")
                .setAttribute("data-font-sort-handle-icon", item);
        handleIcon.style()
                .setWidth(UiStyleLength.px(14))
                .setHeight(UiStyleLength.px(14))
                .setOpacity(enabled ? (item.equals(draggingItem) ? 0.95F : 0.72F) : 0.34F);
        handle.append(handleIcon);
        return handle;
    }

    private OrdinalBadge createOrdinalBadge(int globalIndex) {
        ElementNode badge = document.div();
        badge.style()
                .setWidth(UiStyleLength.px(42))
                .setTextColor(0xFFBAE6FD)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        TextNode text = badge.appendText("#" + (globalIndex + 1));
        return new OrdinalBadge(badge, text);
    }

    private DocumentTextInputControl createOrderInput(final String item, int globalIndex) {
        DocumentTextInputControl input = new DocumentTextInputControl(document)
                .setPlaceholder(String.valueOf(globalIndex + 1))
                .setMaxLength(4)
                .setNormalBackgroundColor(0xFF0F172A)
                .setNormalBorderColor(0xFF334155)
                .setFocusBorderColor(0xFF93C5FD)
                .setTextColors(0xFFF8FAFC, 0xFF64748B, 0xFF64748B)
                .setChangeHandler(new DocumentTextInputChangeHandler() {
                    @Override
                    public void onTextChanged(DocumentTextInputChangeEvent event) {
                        updateStateText("输入目标序号后按回车生效，范围 1 ~ " + items.size() + "。");
                    }
                })
                .setKeyHandler(new DocumentElementKeyHandler() {
                    @Override
                    public boolean onKey(DocumentElementKeyEvent event) {
                        if (!isSubmitKey(event)) {
                            return false;
                        }
                        applyOrdinalInput(item, event.getCurrentTarget().getAttribute("value"));
                        return true;
                    }
                });
        input.getElement().setAttribute("data-font-sort-order-input", item);
        input.getElement().style()
                .setWidth(UiStyleLength.px(52))
                .setHeight(UiStyleLength.px(26))
                .setJustifyContent(UiJustifyContent.CENTER)
                .setPadding(UiStyleInsets.of(UiStyleLength.px(5), UiStyleLength.px(8), UiStyleLength.px(5),
                        UiStyleLength.px(8)));
        return input;
    }

    private ElementNode createLabel(String item) {
        ElementNode label = document.div();
        label.style()
                .setFlexGrow(1.0F)
                .setTextColor(0xFFEAF1FF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        label.appendText(item);
        return label;
    }

    private void applyOrdinalInput(String item, String rawText) {
        Integer ordinal = parseOrdinal(rawText);
        if (ordinal == null || ordinal.intValue() < 1 || ordinal.intValue() > items.size()) {
            updateStateText("序号必须是 1 ~ " + items.size() + " 之间的整数。");
            return;
        }
        if (!moveItemToOrdinal(item, ordinal.intValue())) {
            updateStateText("字体已在目标位置，无需移动。");
            return;
        }
        updateStateText("已将 " + item + " 移到第 " + ordinal + " 位。");
    }

    private void applyPageInput(String rawText) {
        Integer ordinal = parseOrdinal(rawText);
        int totalPages = resolveTotalPages(collectMatchedIndexes().size());
        if (ordinal == null || ordinal.intValue() < 1 || ordinal.intValue() > totalPages) {
            updateStateText("页码必须是 1 ~ " + totalPages + " 之间的整数。");
            return;
        }
        goToPage(ordinal.intValue() - 1);
    }

    private void applyJumpInput(String rawText) {
        Integer ordinal = parseOrdinal(rawText);
        if (ordinal == null || ordinal.intValue() < 1 || ordinal.intValue() > items.size()) {
            updateStateText("跳转序号必须是 1 ~ " + items.size() + " 之间的整数。");
            return;
        }
        if (jumpToOrdinal(ordinal.intValue())) {
            updateStateText("已跳转到第 " + ordinal + " 位。搜索条件已清空，便于查看全局位置。");
        }
    }

    private boolean jumpToOrdinal(int ordinal) {
        if (ordinal < 1 || ordinal > items.size()) {
            return false;
        }
        clearFilterWithoutRefresh();
        pageIndex = resolvePageIndexForGlobalIndex(ordinal - 1);
        refreshView(false);
        return true;
    }

    private void clearFilter() {
        clearFilterWithoutRefresh();
        pageIndex = 0;
        refreshView(false);
    }

    private void clearFilterWithoutRefresh() {
        filterText = "";
        draggingItem = null;
        searchInput.setText("");
    }

    private void goToPage(int nextPageIndex) {
        int totalPages = resolveTotalPages(collectMatchedIndexes().size());
        pageIndex = Math.max(0, Math.min(nextPageIndex, totalPages - 1));
        draggingItem = null;
        refreshView(true);
    }

    private void togglePageSize() {
        pageSize = pageSize == DEFAULT_PAGE_SIZE ? COMPACT_PAGE_SIZE : DEFAULT_PAGE_SIZE;
        pageIndex = 0;
        draggingItem = null;
        refreshView(false);
    }

    private DocumentElementDragStartHandler createItemDragStartHandler(final String item) {
        return new DocumentElementDragStartHandler() {
            @Override
            public boolean onDragStart(DocumentElementDragEvent event) {
                if (!isDragEnabled()) {
                    return false;
                }
                draggingItem = item;
                refreshDragLayoutCache();
                refreshView(true);
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
                cachedRowMiddleYs.clear();
                refreshView(true);
                updateStateText();
                fireChange();
                return true;
            }
        };
    }

    private DocumentElementDragOverHandler createListDragOverHandler() {
        return new DocumentElementDragOverHandler() {
            @Override
            public boolean onDragOver(DocumentElementDragEvent event) {
                if (!isDragEnabled()) {
                    return false;
                }
                moveDraggingItem(resolveTargetVisibleIndex(event));
                return true;
            }
        };
    }

    private int resolveTargetVisibleIndex(DocumentElementDragEvent event) {
        if (event == null) {
            return 0;
        }
        if (!cachedRowMiddleYs.isEmpty()) {
            int documentY = event.getDocumentY();
            for (int index = 0; index < cachedRowMiddleYs.size(); index++) {
                if (documentY < cachedRowMiddleYs.get(index).intValue()) {
                    return index;
                }
            }
            return cachedRowMiddleYs.size();
        }
        DocumentLayoutBox listBox = findLayoutBox(DocumentLayoutEngine.layoutViewportRoot(document.getRootElement(),
                documentWidget.getWidth(), documentWidget.getHeight(), documentWidget.getTextMeasureService()),
                listElement);
        if (listBox == null || listBox.getChildren().isEmpty()) {
            return 0;
        }
        int documentY = event.getDocumentY();
        List<DocumentLayoutBox> itemBoxes = listBox.getChildren();
        for (int index = 0; index < itemBoxes.size(); index++) {
            DocumentLayoutBox itemBox = itemBoxes.get(index);
            if (itemBox.getElement().getAttribute("data-font-sort-item") == null) {
                continue;
            }
            int middleY = (itemBox.getTop() + itemBox.getBottom()) / 2;
            if (documentY < middleY) {
                return index;
            }
        }
        return itemBoxes.size();
    }

    private void moveDraggingItem(int targetVisibleIndex) {
        if (draggingItem == null || visibleGlobalIndexes.isEmpty()) {
            return;
        }
        int currentIndex = items.indexOf(draggingItem);
        if (currentIndex < 0) {
            return;
        }
        int targetIndex = resolveGlobalTargetIndex(targetVisibleIndex, currentIndex);
        if (currentIndex == targetIndex) {
            return;
        }
        moveItem(currentIndex, targetIndex, false);
        refreshView(true);
    }

    private int resolveGlobalTargetIndex(int targetVisibleIndex, int currentIndex) {
        int clampedVisibleIndex = Math.max(0, Math.min(targetVisibleIndex, visibleGlobalIndexes.size()));
        int targetIndex;
        if (clampedVisibleIndex >= visibleGlobalIndexes.size()) {
            targetIndex = visibleGlobalIndexes.get(visibleGlobalIndexes.size() - 1).intValue() + 1;
        } else {
            targetIndex = visibleGlobalIndexes.get(clampedVisibleIndex).intValue();
        }
        if (targetIndex > currentIndex) {
            targetIndex--;
        }
        return Math.max(0, Math.min(targetIndex, items.size() - 1));
    }

    private void moveItem(int currentIndex, int targetIndex, boolean notifyImmediately) {
        targetIndex = Math.max(0, Math.min(targetIndex, items.size() - 1));
        if (currentIndex == targetIndex) {
            return;
        }
        String item = items.remove(currentIndex);
        items.add(targetIndex, item);
        if (notifyImmediately) {
            fireChange();
        }
    }

    private void syncToolbarState() {
        int matchedCount = collectMatchedIndexes().size();
        int totalPages = resolveTotalPages(matchedCount);
        resultText.setText(filterText.isEmpty() ? "共 " + items.size() + " 个字体"
                : "匹配 " + matchedCount + " / " + items.size() + " 个字体");
        pageText.setText("第 " + (pageIndex + 1) + " / " + totalPages + " 页；当前页 " + visibleItems.size() + " 项");
        pageSizeButton.setLabel("每页 " + pageSize);
        previousPageButton.setEnabled(pageIndex > 0);
        nextPageButton.setEnabled(pageIndex + 1 < totalPages);
        clearSearchButton.setEnabled(!filterText.isEmpty());
        pageInput.setPlaceholder("页码");
        pageInput.setText("");
        jumpInput.setText("");
    }

    private void updateStateText() {
        if (stateText != null && !filterText.isEmpty()) {
            stateText.setText("匹配 " + collectMatchedIndexes().size() + " / " + items.size() + " 个字体");
        }
    }

    private void updateStateText(String text) {
        stateText.setText(text == null ? "" : text);
    }

    private void fireChange() {
        if (changeListener != null) {
            changeListener.onOrderChanged(getItemsSnapshot());
        }
    }

    private void refreshDragLayoutCache() {
        cachedRowMiddleYs.clear();
        DocumentLayoutBox listBox = findLayoutBox(DocumentLayoutEngine.layoutViewportRoot(document.getRootElement(),
                documentWidget.getWidth(), documentWidget.getHeight(), documentWidget.getTextMeasureService()),
                listElement);
        if (listBox == null || listBox.getChildren().isEmpty()) {
            return;
        }
        for (DocumentLayoutBox itemBox : listBox.getChildren()) {
            if (itemBox.getElement().getAttribute("data-font-sort-item") != null) {
                cachedRowMiddleYs.add(Integer.valueOf((itemBox.getTop() + itemBox.getBottom()) / 2));
            }
        }
    }

    private int resolvePageIndexForGlobalIndex(int globalIndex) {
        if (pageSize <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(globalIndex, Math.max(0, items.size() - 1)) / pageSize);
    }

    private int resolveTotalPages(int itemCount) {
        return Math.max(1, (itemCount + pageSize - 1) / pageSize);
    }

    private boolean isDragEnabled() {
        return filterText.isEmpty();
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

    private static Integer parseOrdinal(String rawText) {
        String trimmed = rawText == null ? "" : rawText.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(trimmed));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeItem(String item) {
        return item == null ? "" : item.trim();
    }

    private static String normalizeFilterText(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isSubmitKey(DocumentElementKeyEvent event) {
        return event != null && event.getAction() == UiKeyEvent.Action.PRESSED && isEnterKey(event.getKeyCode());
    }

    private static boolean isEnterKey(int keyCode) {
        return keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER;
    }

    static List<String> toItemList(String[] values) {
        return values == null ? new ArrayList<String>() : new ArrayList<String>(Arrays.asList(values));
    }

    static String summarizeItems(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return "空";
        }
        StringBuilder builder = new StringBuilder();
        int resolvedLimit = Math.max(1, limit);
        for (int index = 0; index < values.size() && index < resolvedLimit; index++) {
            if (index > 0) {
                builder.append(" -> ");
            }
            builder.append(values.get(index));
        }
        if (values.size() > resolvedLimit) {
            builder.append(" 等 ").append(values.size()).append(" 项");
        }
        return builder.toString();
    }

    /**
     * 字体排序变更监听器。
     */
    interface FontSortOrderChangeListener {

        /**
         * 当字体排序发生变化时触发。
         *
         * @param orderedItems 最新字体顺序
         */
        void onOrderChanged(List<String> orderedItems);
    }

    /**
     * 可复用的字体排序行节点。
     */
    private static final class FontSortRow {

        private final ElementNode element;
        private final DocumentTextInputControl orderInput;
        private final TextNode ordinalText;

        private FontSortRow(ElementNode element, DocumentTextInputControl orderInput, TextNode ordinalText) {
            this.element = element;
            this.orderInput = orderInput;
            this.ordinalText = ordinalText;
        }
    }

    /**
     * 字体全局序号徽标节点。
     */
    private static final class OrdinalBadge {

        private final ElementNode element;
        private final TextNode text;

        private OrdinalBadge(ElementNode element, TextNode text) {
            this.element = element;
            this.text = text;
        }
    }
}
