package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementDragEvent;
import club.heiqi.uilib.ui.dom.DocumentElementDragHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.dom.control.DocumentButtonControl;
import club.heiqi.uilib.ui.dom.control.DocumentSegmentedSelectionEvent;
import club.heiqi.uilib.ui.dom.control.DocumentSegmentedSelectionHandler;
import club.heiqi.uilib.ui.dom.control.DocumentSegmentedSelectorControl;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * 有序字符串列表控件基类。
 */
abstract class AbstractOrderedStringListControl {

    private static final int NORMAL_ROW_BACKGROUND = 0xFF0F172A;
    private static final int NORMAL_ROW_BORDER = 0xFF334155;
    private static final int ACTIVE_ROW_BORDER = 0xFF60A5FA;
    private static final int HANDLE_BACKGROUND = 0xFF1E3A5F;
    private static final int HANDLE_ACTIVE_BACKGROUND = 0xFF1D4ED8;
    private static final int HANDLE_DISABLED_BACKGROUND = 0xFF334155;
    private static final int MOVE_BUTTON_BACKGROUND = 0xFF2563EB;
    private static final int MOVE_BUTTON_ACTIVE_BACKGROUND = 0xFF1D4ED8;
    private static final int MOVE_BUTTON_DISABLED_BACKGROUND = 0xFF334155;
    private static final int MESSAGE_TEXT_COLOR = 0xFFFBBF24;

    private final ForgeConfigTemplateScreen ownerScreen;
    private final UiDocument document;
    private final ElementNode element;
    private final TextNode summaryText;
    private final TextNode messageText;
    private final ElementNode listElement;
    private final ElementNode emptyStateElement;
    private final List<RowState> rows = new ArrayList<RowState>();
    private final DocumentSegmentedSelectorControl modeSelector;

    private Runnable changeListener;
    private RowState draggingRow;
    private SortMode currentMode = SortMode.DRAG;

    protected AbstractOrderedStringListControl(UiDocument document, ForgeConfigTemplateScreen ownerScreen) {
        this.document = Objects.requireNonNull(document, "document");
        this.ownerScreen = Objects.requireNonNull(ownerScreen, "ownerScreen");

        this.element = document.div();
        this.element.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8))
                .setWidth(UiStyleLength.percent(1.0F));

        this.modeSelector = new DocumentSegmentedSelectorControl(document, "拖拽排序", "序号排序")
                .setSelectionHandler(new DocumentSegmentedSelectionHandler() {
                    @Override
                    public void onSelectionChanged(DocumentSegmentedSelectionEvent event) {
                        currentMode = event.getSelectedIndex() == 0 ? SortMode.DRAG : SortMode.INDEX;
                        syncRowVisuals();
                    }
                });
        this.modeSelector.getElement().style().setWidth(UiStyleLength.percent(1.0F));
        this.element.append(modeSelector.getElement());

        ElementNode summaryElement = document.div();
        summaryElement.style().setTextColor(0xFFCBD5E1);
        this.summaryText = summaryElement.appendText("");
        this.element.append(summaryElement);

        ElementNode messageElement = document.div();
        messageElement.style().setTextColor(MESSAGE_TEXT_COLOR);
        this.messageText = messageElement.appendText("");
        this.element.append(messageElement);

        this.listElement = document.div();
        this.listElement.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8))
                .setWidth(UiStyleLength.percent(1.0F));
        this.element.append(listElement);

        this.emptyStateElement = document.div();
        this.emptyStateElement.style()
                .setPadding(UiStyleLength.px(12))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderColor(NORMAL_ROW_BORDER)
                .setBorderRadius(UiStyleLength.px(12))
                .setBackgroundColor(NORMAL_ROW_BACKGROUND)
                .setTextColor(0xFF94A3B8)
                .setWidth(UiStyleLength.percent(1.0F));
        this.emptyStateElement.appendText(getEmptyStateText());

        setMessage("");
        syncSummary();
    }

    public ElementNode getElement() {
        return element;
    }

    public AbstractOrderedStringListControl setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
        return this;
    }

    public void setValues(String[] values) {
        List<String> normalizedValues = normalizeValues(values);
        rows.clear();
        listElement.clearChildren();
        draggingRow = null;

        for (String value : normalizedValues) {
            rows.add(createRowState(value));
        }
        setMessage("");
        syncRowVisuals();
    }

    public String[] getValues() {
        String[] values = new String[rows.size()];
        for (int index = 0; index < rows.size(); index++) {
            values[index] = rows.get(index).value;
        }
        return values;
    }

    protected abstract String resolveStatusText(String value);

    protected abstract int resolveStatusTextColor(String value);

    protected abstract int resolveStatusBackgroundColor(String value);

    protected abstract String buildSummaryText(String[] currentValues);

    protected abstract String getEmptyStateText();

    private RowState createRowState(String value) {
        final RowState row = new RowState(value);

        row.rowElement = document.div();
        row.rowElement.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(10))
                .setPadding(UiStyleLength.px(10))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderColor(NORMAL_ROW_BORDER)
                .setBorderRadius(UiStyleLength.px(12))
                .setBackgroundColor(NORMAL_ROW_BACKGROUND)
                .setWidth(UiStyleLength.percent(1.0F));

        ElementNode orderBadge = document.div();
        orderBadge.style()
                .setPadding(UiStyleLength.px(6))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderColor(0xFF475569)
                .setBorderRadius(UiStyleLength.px(999))
                .setBackgroundColor(0xFF111827)
                .setTextColor(0xFFF8FAFC);
        row.orderText = orderBadge.appendText("1");
        row.rowElement.append(orderBadge);

        ElementNode infoShell = document.div();
        infoShell.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8))
                .setWidth(UiStyleLength.percent(1.0F));
        row.nameText = infoShell.appendText(value);

        row.statusElement = document.div();
        row.statusElement.style()
                .setPadding(UiStyleLength.px(6))
                .setBorderRadius(UiStyleLength.px(999));
        row.statusText = row.statusElement.appendText("");
        infoShell.append(row.statusElement);
        row.rowElement.append(infoShell);

        row.dragShell = document.div();
        row.dragShell.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER);
        row.dragHandle = new DocumentButtonControl(document, "拖拽")
                .setBackgroundColors(HANDLE_BACKGROUND, HANDLE_ACTIVE_BACKGROUND, HANDLE_DISABLED_BACKGROUND)
                .setFocusBorderColor(0xFFBFDBFE)
                .setTextColors(0xFFFFFFFF, 0xFFCBD5E1);
        row.dragHandle.getElement().setDragHandler(new DocumentElementDragHandler() {
            @Override
            public boolean onDrag(DocumentElementDragEvent event) {
                return handleDrag(row, event);
            }
        });
        row.dragShell.append(row.dragHandle.getElement());
        row.rowElement.append(row.dragShell);

        row.indexShell = document.div();
        row.indexShell.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(6));
        row.indexInput = new DocumentTextInputControl(document)
                .setPlaceholder("序号")
                .setMaxLength(4)
                .setKeyHandler(new DocumentElementKeyHandler() {
                    @Override
                    public boolean onKey(DocumentElementKeyEvent event) {
                        if (event.getAction() != UiKeyEvent.Action.PRESSED) {
                            return false;
                        }
                        if (event.getKeyCode() != Keyboard.KEY_RETURN
                                && event.getKeyCode() != Keyboard.KEY_NUMPADENTER) {
                            return false;
                        }
                        applyIndexMove(row);
                        return true;
                    }
                });
        row.indexInput.getElement().style().setWidth(UiStyleLength.px(68));
        row.indexMoveButton = new DocumentButtonControl(document, "提交")
                .setBackgroundColors(MOVE_BUTTON_BACKGROUND, MOVE_BUTTON_ACTIVE_BACKGROUND,
                        MOVE_BUTTON_DISABLED_BACKGROUND)
                .setFocusBorderColor(0xFFBFDBFE)
                .setTextColors(0xFFFFFFFF, 0xFFCBD5E1)
                .setActionHandler(new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        applyIndexMove(row);
                    }
                });
        row.indexShell.append(row.indexInput.getElement()).append(row.indexMoveButton.getElement());
        row.rowElement.append(row.indexShell);

        return row;
    }

    private boolean handleDrag(RowState row, DocumentElementDragEvent event) {
        if (event == null || event.getButton() != 0) {
            return false;
        }
        if (event.getPhase() == DocumentElementDragEvent.DragPhase.START) {
            draggingRow = row;
            row.rowElement.style().setBorderColor(ACTIVE_ROW_BORDER);
            setMessage("拖拽到目标位置后松开鼠标，即可调整字体顺序。");
            return true;
        }
        if (draggingRow != row) {
            return false;
        }
        if (event.getPhase() == DocumentElementDragEvent.DragPhase.END) {
            draggingRow = null;
            setMessage("");
            syncRowVisuals();
            return true;
        }
        if (event.getPhase() != DocumentElementDragEvent.DragPhase.DRAG) {
            return false;
        }

        int targetIndex = resolveInsertionIndex(row, event.getDocumentY());
        if (targetIndex >= 0) {
            moveRow(row, targetIndex, true);
        }
        return true;
    }

    private void applyIndexMove(RowState row) {
        if (row == null || row.indexInput == null) {
            return;
        }
        String text = row.indexInput.getText();
        if (text == null || text.trim().isEmpty()) {
            setMessage("请输入要移动到的序号。范围从 1 到 " + Math.max(1, rows.size()) + "。");
            return;
        }
        int targetPosition;
        try {
            targetPosition = Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            setMessage("序号只能填写整数。范围从 1 到 " + Math.max(1, rows.size()) + "。");
            return;
        }
        if (targetPosition < 1 || targetPosition > rows.size()) {
            setMessage("序号超出范围。请输入 1 到 " + rows.size() + " 之间的值。");
            return;
        }
        moveRow(row, targetPosition - 1, true);
        setMessage("");
    }

    private void moveRow(RowState row, int targetIndex, boolean notify) {
        if (row == null) {
            return;
        }
        int currentIndex = rows.indexOf(row);
        if (currentIndex < 0) {
            return;
        }
        int boundedIndex = Math.max(0, Math.min(targetIndex, rows.size() - 1));
        if (currentIndex == boundedIndex) {
            syncRowVisuals();
            return;
        }
        rows.remove(currentIndex);
        rows.add(boundedIndex, row);
        syncRowVisuals();
        fireChange();
    }

    private int resolveInsertionIndex(RowState draggedRow, int documentY) {
        HtmlLikeDocumentWidget widget = ownerScreen.getDocumentWidgetForTesting();
        if (widget == null) {
            return rows.indexOf(draggedRow);
        }
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        if (rootBox == null) {
            return rows.indexOf(draggedRow);
        }

        int insertionIndex = 0;
        for (RowState row : rows) {
            if (row == draggedRow) {
                continue;
            }
            DocumentLayoutBox rowBox = findLayoutBox(rootBox, row.rowElement);
            if (rowBox == null) {
                insertionIndex++;
                continue;
            }
            int middleY = rowBox.getTop() + (rowBox.getHeight() / 2);
            if (documentY < middleY) {
                return insertionIndex;
            }
            insertionIndex++;
        }
        return insertionIndex;
    }

    private void syncRowVisuals() {
        listElement.clearChildren();
        if (rows.isEmpty()) {
            listElement.append(emptyStateElement);
        }
        for (int index = 0; index < rows.size(); index++) {
            RowState row = rows.get(index);
            row.orderText.setText(String.valueOf(index + 1));
            row.nameText.setText(row.value);
            row.statusText.setText(resolveStatusText(row.value));
            row.statusElement.style()
                    .setBackgroundColor(resolveStatusBackgroundColor(row.value))
                    .setTextColor(resolveStatusTextColor(row.value));
            row.indexInput.setText(String.valueOf(index + 1));
            row.dragShell.style().setDisplay(currentMode == SortMode.DRAG ? UiDisplay.FLEX : UiDisplay.NONE);
            row.indexShell.style().setDisplay(currentMode == SortMode.INDEX ? UiDisplay.FLEX : UiDisplay.NONE);
            row.rowElement.style().setBorderColor(draggingRow == row ? ACTIVE_ROW_BORDER : NORMAL_ROW_BORDER);
            listElement.append(row.rowElement);
        }
        syncSummary();
    }

    private void syncSummary() {
        summaryText.setText(buildSummaryText(getValues()));
    }

    private DocumentLayoutBox findLayoutBox(DocumentLayoutBox rootBox, ElementNode targetElement) {
        if (rootBox == null || targetElement == null) {
            return null;
        }
        if (rootBox.getElement().__getElementUid() == targetElement.__getElementUid()) {
            return rootBox;
        }
        for (DocumentLayoutBox child : rootBox.getChildren()) {
            DocumentLayoutBox matched = findLayoutBox(child, targetElement);
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    private void setMessage(String message) {
        messageText.setText(message == null ? "" : message);
    }

    private void fireChange() {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    private static List<String> normalizeValues(String[] values) {
        Set<String> orderedValues = new LinkedHashSet<String>();
        if (values != null) {
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    orderedValues.add(trimmed);
                }
            }
        }
        return new ArrayList<String>(orderedValues);
    }

    private enum SortMode {
        DRAG,
        INDEX
    }

    private static final class RowState {

        private final String value;
        private ElementNode rowElement;
        private TextNode orderText;
        private TextNode nameText;
        private ElementNode statusElement;
        private TextNode statusText;
        private ElementNode dragShell;
        private DocumentButtonControl dragHandle;
        private ElementNode indexShell;
        private DocumentTextInputControl indexInput;
        private DocumentButtonControl indexMoveButton;

        private RowState(String value) {
            this.value = value;
        }
    }
}
