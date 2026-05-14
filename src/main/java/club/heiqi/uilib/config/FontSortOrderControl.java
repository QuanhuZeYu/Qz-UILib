package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementDragEndHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDragEvent;
import club.heiqi.uilib.ui.dom.DocumentElementDragOverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDragStartHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputChangeEvent;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputChangeHandler;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputControl;
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
 * 字体排序二级页使用的拖拽排序控件。
 */
final class FontSortOrderControl {

    private static final int ROW_HEIGHT = 48;
    private final UiDocument document;
    private final HtmlLikeDocumentWidget documentWidget;
    private final FontSortOrderChangeListener changeListener;
    private final List<String> items = new ArrayList<String>();
    private final Map<String, FontSortRow> rowsByItem = new LinkedHashMap<String, FontSortRow>();
    private final ElementNode rootElement;
    private final ElementNode listElement;
    private final TextNode stateText;
    private final List<Integer> cachedRowMiddleYs = new ArrayList<Integer>();
    private String draggingItem;

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
        appendGuide(rootElement);
        this.listElement = document.div();
        configureList(listElement);
        rootElement.append(listElement);

        ElementNode state = document.div();
        configureState(state);
        this.stateText = state.appendText("");
        rootElement.append(state);

        createRows();
        rebuildList();
        updateStateText();
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
        createRows();
        draggingItem = null;
        syncListOrder();
        updateStateText();
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

    private void createRows() {
        rowsByItem.clear();
        listElement.clearChildren();
        for (int index = 0; index < items.size(); index++) {
            String item = items.get(index);
            rowsByItem.put(item, createRow(item, index));
        }
    }

    private void configureRoot(ElementNode root) {
        root.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(12))
                .setWidth(UiStyleLength.percent(1.0F));
    }

    private void appendGuide(ElementNode parent) {
        ElementNode guide = document.div();
        guide.style()
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF38BDF8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFD7E4FF);
        guide.appendText("拖住任意字体行可调整优先级；也可以在序号输入框中直接输入 1 ~ " + items.size() + " 的目标位置。");
        parent.append(guide);
    }

    private void configureList(ElementNode list) {
        list.setAttribute("data-font-sort-list", "fonts");
        list.style()
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF2563EB)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(16))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        list.setDragOverHandler(createListDragOverHandler());
    }

    private void configureState(ElementNode state) {
        state.style()
                .setPadding(UiStyleInsets.of(UiStyleLength.px(12), UiStyleLength.px(14), UiStyleLength.px(12),
                        UiStyleLength.px(14)))
                .setBackgroundColor(0xFF162132)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFBAE6FD);
    }

    private void rebuildList() {
        syncRowVisuals();
        syncListOrder();
    }

    private FontSortRow createRow(final String item, int index) {
        ElementNode row = document.element("li");
        row.setAttribute("data-font-sort-item", item);
        row.setAttribute("draggable", "true");
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(10))
                .setHeight(UiStyleLength.px(ROW_HEIGHT))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(6), UiStyleLength.px(0), UiStyleLength.px(6),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(12), UiStyleLength.px(8),
                        UiStyleLength.px(12)))
                .setBackgroundColor(item.equals(draggingItem) ? 0xFF1D4ED8 : 0xFF1E293B)
                .setBorderColor(item.equals(draggingItem) ? 0xFFBFDBFE : 0xFF475569)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(12))
                .setOpacity(item.equals(draggingItem) ? 0.62F : 1.0F)
                .setTextColor(0xFFEAF1FF);

        row.append(createHandle(item));
        DocumentTextInputControl orderInput = createOrderInput(item, index);
        row.append(orderInput.getElement());
        row.append(createLabel(item));
        row.setDragStartHandler(createItemDragStartHandler(item));
        row.setDragEndHandler(createItemDragEndHandler(item));
        return new FontSortRow(row, orderInput);
    }

    private ElementNode createHandle(String item) {
        ElementNode handle = document.div();
        handle.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setWidth(UiStyleLength.px(30))
                .setHeight(UiStyleLength.px(26))
                .setBackgroundColor(item.equals(draggingItem) ? 0xFF172554 : 0xFF0F172A)
                .setBorderColor(item.equals(draggingItem) ? 0xFF93C5FD : 0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFF93C5FD)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        handle.setAttribute("data-font-sort-handle", item);
        handle.appendText("|||");
        return handle;
    }

    private DocumentTextInputControl createOrderInput(final String item, int index) {
        DocumentTextInputControl input = new DocumentTextInputControl(document)
                .setPlaceholder(String.valueOf(index + 1))
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
                        if (event.getAction() != UiKeyEvent.Action.PRESSED || !isEnterKey(event.getKeyCode())) {
                            return false;
                        }
                        applyOrdinalInput(item, event.getCurrentTarget().getAttribute("value"));
                        return true;
                    }
                });
        input.getElement().setAttribute("data-font-sort-order-input", item);
        input.getElement().style()
                .setWidth(UiStyleLength.px(52))
                .setHeight(UiStyleLength.px(28))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(6), UiStyleLength.px(8), UiStyleLength.px(6),
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
        String trimmed = rawText == null ? "" : rawText.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        try {
            int ordinal = Integer.parseInt(trimmed);
            if (ordinal < 1 || ordinal > items.size()) {
                updateStateText("序号必须是 1 ~ " + items.size() + " 之间的整数。");
                return;
            }
            if (!moveItemToOrdinal(item, ordinal)) {
                updateStateText("字体已在目标位置，无需移动。");
            }
        } catch (NumberFormatException ignored) {
            updateStateText("序号必须是 1 ~ " + items.size() + " 之间的整数。");
        }
    }

    private DocumentElementDragStartHandler createItemDragStartHandler(final String item) {
        return new DocumentElementDragStartHandler() {
            @Override
            public boolean onDragStart(DocumentElementDragEvent event) {
                draggingItem = item;
                refreshDragLayoutCache();
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
                cachedRowMiddleYs.clear();
                rebuildList();
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
                moveDraggingItem(resolveTargetIndex(event));
                return true;
            }
        };
    }

    private int resolveTargetIndex(DocumentElementDragEvent event) {
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

    private void moveDraggingItem(int targetIndex) {
        if (draggingItem == null) {
            return;
        }
        int currentIndex = items.indexOf(draggingItem);
        if (currentIndex < 0 || currentIndex == targetIndex) {
            return;
        }
        if (targetIndex > currentIndex) {
            targetIndex--;
        }
        moveItem(currentIndex, targetIndex, false);
    }

    private void moveItem(int currentIndex, int targetIndex, boolean notifyImmediately) {
        targetIndex = Math.max(0, Math.min(targetIndex, items.size() - 1));
        if (currentIndex == targetIndex) {
            return;
        }
        String item = items.remove(currentIndex);
        items.add(targetIndex, item);
        rebuildList();
        updateStateText();
        if (notifyImmediately) {
            fireChange();
        }
    }

    private void updateStateText() {
        updateStateText("当前字体数量：" + items.size() + "。前 5 个：" + summarizeItems(items, 5));
    }

    private void updateStateText(String text) {
        stateText.setText(text == null ? "" : text);
    }

    private void fireChange() {
        if (changeListener != null) {
            changeListener.onOrderChanged(getItemsSnapshot());
        }
    }

    private void syncRowVisuals() {
        for (int index = 0; index < items.size(); index++) {
            String item = items.get(index);
            FontSortRow row = rowsByItem.get(item);
            if (row == null) {
                continue;
            }
            boolean dragging = item.equals(draggingItem);
            row.element.style()
                    .setBackgroundColor(dragging ? 0xFF1D4ED8 : 0xFF1E293B)
                    .setBorderColor(dragging ? 0xFFBFDBFE : 0xFF475569)
                    .setOpacity(dragging ? 0.62F : 1.0F);
            row.orderInput.setPlaceholder(String.valueOf(index + 1));
            row.orderInput.setText("");
        }
    }

    private void syncListOrder() {
        for (String item : items) {
            FontSortRow row = rowsByItem.get(item);
            if (row != null) {
                listElement.append(row.element);
            }
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

    private static String normalizeItem(String item) {
        return item == null ? "" : item.trim();
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
                builder.append(" → ");
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

        private FontSortRow(ElementNode element, DocumentTextInputControl orderInput) {
            this.element = element;
            this.orderInput = orderInput;
        }
    }
}
