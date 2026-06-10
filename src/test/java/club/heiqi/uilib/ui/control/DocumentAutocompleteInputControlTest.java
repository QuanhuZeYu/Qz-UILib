package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementBounds;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.values.UiTransform;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * `DocumentAutocompleteInputControl` 的基础行为契约测试。
 */
public class DocumentAutocompleteInputControlTest {

    /**
     * 验证 query 变化后会调用 provider 并刷新候选列表。
     */
    @Test
    public void shouldRefreshSuggestionsWhenQueryChanges() {
        UiDocument document = UiDocument.create();
        RecordingProvider provider = new RecordingProvider("Apple", "Apricot", "Banana", "Berry");
        DocumentAutocompleteInputControl autocompleteControl = new DocumentAutocompleteInputControl(document)
                .setSuggestionProvider(provider);
        HtmlLikeDocumentWidget widget = mount(document, autocompleteControl, 240, 180);

        provider.clearQueries();
        widget.onFocusTraversalEntered(false);
        widget.onTextInput(new UiTextInputEvent("a", 1L));
        widget.onTextInput(new UiTextInputEvent("p", 2L));

        Assert.assertEquals("ap", autocompleteControl.getText());
        Assert.assertEquals("[, a, ap]", provider.getQueries().toString());
        Assert.assertEquals(2, autocompleteControl.getSuggestionCount());
        Assert.assertEquals(2, countOptionElements(findListboxElement(document.getRootElement())));
    }

    /**
     * 验证空 query 默认展示全部固定候选，并可通过开关关闭。
     */
    @Test
    public void shouldShowAllSuggestionsForEmptyQueryWhenEnabled() {
        UiDocument document = UiDocument.create();
        DocumentAutocompleteInputControl autocompleteControl = new DocumentAutocompleteInputControl(document,
                "Alpha", "Beta", "Gamma");
        HtmlLikeDocumentWidget widget = mount(document, autocompleteControl, 240, 180);

        widget.onFocusTraversalEntered(false);

        ElementNode popup = findListboxElement(document.getRootElement());
        Assert.assertTrue(autocompleteControl.isOpen());
        Assert.assertEquals(3, autocompleteControl.getSuggestionCount());
        Assert.assertEquals(3, countOptionElements(popup));
        Assert.assertTrue(document.__isTopLayerElement(popup));

        autocompleteControl.setShowAllWhenQueryEmpty(false);

        Assert.assertFalse(autocompleteControl.isOpen());
        Assert.assertEquals(0, autocompleteControl.getSuggestionCount());
    }

    /**
     * 验证鼠标点击候选会回填完整文本并触发选择事件。
     */
    @Test
    public void shouldFillTextAndFireSelectionWhenSuggestionClicked() {
        UiDocument document = UiDocument.create();
        final List<DocumentAutocompleteSelectionEvent> selectionEvents =
                new ArrayList<DocumentAutocompleteSelectionEvent>();
        final List<String> changeTexts = new ArrayList<String>();
        DocumentAutocompleteInputControl autocompleteControl = new DocumentAutocompleteInputControl(document,
                "Alpha", "Beta", "Gamma")
                .setSelectionHandler(new DocumentAutocompleteSelectionHandler() {
                    @Override
                    public void onSuggestionSelected(DocumentAutocompleteSelectionEvent event) {
                        selectionEvents.add(event);
                    }
                })
                .setChangeHandler(new DocumentAutocompleteInputChangeHandler() {
                    @Override
                    public void onTextChanged(DocumentAutocompleteInputChangeEvent event) {
                        changeTexts.add(event.getText());
                    }
                });
        HtmlLikeDocumentWidget widget = mount(document, autocompleteControl, 240, 180);

        widget.onFocusTraversalEntered(false);
        ElementNode popup = findListboxElement(document.getRootElement());
        ElementNode secondOption = findOptionElement(popup, 1);
        DocumentElementBounds bounds = secondOption.getDocumentBounds();
        click(widget, bounds.getLeft() + 8, bounds.getTop() + 8, 3L);

        Assert.assertEquals("Beta", autocompleteControl.getText());
        Assert.assertFalse(autocompleteControl.isOpen());
        Assert.assertEquals(1, selectionEvents.size());
        Assert.assertEquals("Beta", selectionEvents.get(0).getSelectedSuggestion());
        Assert.assertEquals("", selectionEvents.get(0).getQuery());
        Assert.assertFalse(selectionEvents.get(0).isKeyboardTriggered());
        Assert.assertEquals(0, selectionEvents.get(0).getButton());
        Assert.assertEquals("[Beta]", changeTexts.toString());
        Assert.assertSame(autocompleteControl.getElement(), widget.getFocusedElement());
    }

    /**
     * 验证选择候选后，公开候选快照会与回填后的文本保持一致。
     */
    @Test
    public void shouldRefreshSuggestionStateAfterSelection() {
        UiDocument document = UiDocument.create();
        DocumentAutocompleteInputControl autocompleteControl = new DocumentAutocompleteInputControl(document,
                "Alpha", "Beta", "Betamax");
        HtmlLikeDocumentWidget widget = mount(document, autocompleteControl, 240, 180);

        widget.onFocusTraversalEntered(false);
        ElementNode popup = findListboxElement(document.getRootElement());
        ElementNode secondOption = findOptionElement(popup, 1);
        DocumentElementBounds bounds = secondOption.getDocumentBounds();
        click(widget, bounds.getLeft() + 8, bounds.getTop() + 8, 3L);

        Assert.assertEquals("Beta", autocompleteControl.getText());
        Assert.assertFalse(autocompleteControl.isOpen());
        Assert.assertEquals(2, autocompleteControl.getSuggestionCount());
        Assert.assertEquals(2, countOptionElements(popup));
    }

    /**
     * 验证 Up / Down / Enter 可移动高亮并选择候选。
     */
    @Test
    public void shouldSupportKeyboardNavigationAndSelection() {
        UiDocument document = UiDocument.create();
        final List<DocumentAutocompleteSelectionEvent> selectionEvents =
                new ArrayList<DocumentAutocompleteSelectionEvent>();
        DocumentAutocompleteInputControl autocompleteControl = new DocumentAutocompleteInputControl(document,
                "Alpha", "Beta", "Gamma")
                .setSelectionHandler(new DocumentAutocompleteSelectionHandler() {
                    @Override
                    public void onSuggestionSelected(DocumentAutocompleteSelectionEvent event) {
                        selectionEvents.add(event);
                    }
                });
        HtmlLikeDocumentWidget widget = mount(document, autocompleteControl, 240, 180);

        widget.onFocusTraversalEntered(false);
        key(widget, Keyboard.KEY_DOWN, 1L);
        key(widget, Keyboard.KEY_DOWN, 2L);
        key(widget, Keyboard.KEY_UP, 3L);
        key(widget, Keyboard.KEY_RETURN, 4L);

        Assert.assertEquals("Beta", autocompleteControl.getText());
        Assert.assertFalse(autocompleteControl.isOpen());
        Assert.assertEquals(1, selectionEvents.size());
        Assert.assertTrue(selectionEvents.get(0).isKeyboardTriggered());
        Assert.assertEquals(Keyboard.KEY_RETURN, selectionEvents.get(0).getKeyCode());
        Assert.assertEquals(-1, selectionEvents.get(0).getButton());
    }

    /**
     * 验证 Escape 只关闭候选面板，不改写当前输入文本。
     */
    @Test
    public void shouldClosePopupWithoutChangingTextWhenEscapePressed() {
        UiDocument document = UiDocument.create();
        final List<DocumentAutocompleteSelectionEvent> selectionEvents =
                new ArrayList<DocumentAutocompleteSelectionEvent>();
        DocumentAutocompleteInputControl autocompleteControl = new DocumentAutocompleteInputControl(document,
                "Alpha", "Beta", "Gamma")
                .setSelectionHandler(new DocumentAutocompleteSelectionHandler() {
                    @Override
                    public void onSuggestionSelected(DocumentAutocompleteSelectionEvent event) {
                        selectionEvents.add(event);
                    }
                });
        HtmlLikeDocumentWidget widget = mount(document, autocompleteControl, 240, 180);

        widget.onFocusTraversalEntered(false);
        widget.onTextInput(new UiTextInputEvent("Al", 1L));
        Assert.assertTrue(autocompleteControl.isOpen());

        key(widget, Keyboard.KEY_ESCAPE, 2L);

        Assert.assertFalse(autocompleteControl.isOpen());
        Assert.assertEquals("Al", autocompleteControl.getText());
        Assert.assertTrue(selectionEvents.isEmpty());
    }

    /**
     * 验证 disabled 状态下不会聚焦、输入或打开候选面板。
     */
    @Test
    public void shouldNotOpenPopupWhenDisabled() {
        UiDocument document = UiDocument.create();
        DocumentAutocompleteInputControl autocompleteControl = new DocumentAutocompleteInputControl(document,
                "Alpha", "Beta")
                .setEnabled(false);
        HtmlLikeDocumentWidget widget = mount(document, autocompleteControl, 240, 180);

        widget.onFocusTraversalEntered(false);
        click(widget, 16, 16, 1L);
        widget.onTextInput(new UiTextInputEvent("A", 3L));

        Assert.assertFalse(autocompleteControl.isOpen());
        Assert.assertEquals("", autocompleteControl.getText());
        Assert.assertNull(widget.getFocusedElement());
    }

    /**
     * 验证焦点移到外部元素时会关闭候选面板。
     */
    @Test
    public void shouldClosePopupWhenFocusMovesOutside() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode outsideElement = document.div();
        DocumentAutocompleteInputControl autocompleteControl = new DocumentAutocompleteInputControl(document,
                "Alpha", "Beta");
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(180));
        autocompleteControl.getElement().style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(32));
        outsideElement.setFocusable(true);
        outsideElement.style()
                .setDisplay(UiDisplay.BLOCK)
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(200))
                .setTop(UiStyleLength.px(0))
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(24));
        root.append(autocompleteControl.getElement()).append(outsideElement);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 180,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 180);

        widget.onFocusTraversalEntered(false);
        Assert.assertTrue(autocompleteControl.isOpen());
        click(widget, 208, 8, 2L);

        Assert.assertFalse(autocompleteControl.isOpen());
        Assert.assertSame(outsideElement, widget.getFocusedElement());
    }

    /**
     * 验证大量候选保留全部 option 节点，并通过滚动面板承载。
     */
    @Test
    public void shouldKeepAllSuggestionNodesInsideScrollablePopup() {
        UiDocument document = UiDocument.create();
        DocumentAutocompleteInputControl autocompleteControl = new DocumentAutocompleteInputControl(document,
                createOptions(20))
                .setMaxVisibleOptions(4);
        HtmlLikeDocumentWidget widget = mount(document, autocompleteControl, 240, 220);

        widget.onFocusTraversalEntered(false);
        ElementNode popup = findListboxElement(document.getRootElement());
        widget.resolveLayoutBoxForTest();

        Assert.assertTrue(autocompleteControl.isOpen());
        Assert.assertEquals(20, autocompleteControl.getSuggestionCount());
        Assert.assertEquals(20, countOptionElements(popup));
        Assert.assertEquals(UiOverflow.AUTO, popup.style().getOverflowY());
        Assert.assertEquals(UiStyleLength.px(112), popup.style().getMaxHeight());
        Assert.assertTrue(popup.getMaxScrollTop() > 0);
    }

    /**
     * 验证候选数量上限会限制实际渲染的 option 节点数量。
     */
    @Test
    public void shouldLimitRenderedSuggestions() {
        UiDocument document = UiDocument.create();
        DocumentAutocompleteInputControl autocompleteControl = new DocumentAutocompleteInputControl(document,
                createOptions(20))
                .setMaxSuggestionCount(8);
        HtmlLikeDocumentWidget widget = mount(document, autocompleteControl, 240, 220);

        widget.onFocusTraversalEntered(false);
        ElementNode popup = findListboxElement(document.getRootElement());

        Assert.assertEquals(8, autocompleteControl.getSuggestionCount());
        Assert.assertEquals(8, countOptionElements(popup));
    }

    /**
     * 验证长列表键盘导航会把高亮项滚入候选面板可视区域。
     */
    @Test
    public void shouldRevealHighlightedOptionWhenKeyboardNavigatesLongList() {
        UiDocument document = UiDocument.create();
        DocumentAutocompleteInputControl autocompleteControl = new DocumentAutocompleteInputControl(document,
                createOptions(20))
                .setMaxVisibleOptions(4);
        HtmlLikeDocumentWidget widget = mount(document, autocompleteControl, 240, 220);

        widget.onFocusTraversalEntered(false);
        for (int index = 0; index < 8; index++) {
            key(widget, Keyboard.KEY_DOWN, index + 1L);
        }
        ElementNode popup = findListboxElement(document.getRootElement());

        Assert.assertTrue(autocompleteControl.getHighlightedIndex() > 0);
        Assert.assertTrue(popup.getScrollTop() > 0);
    }

    /**
     * 验证移除展开控件的祖先时，会同步清理 autocomplete popup 的 top-layer 注册。
     */
    @Test
    public void shouldDetachTopLayerPopupWhenOpenAutocompleteAncestorIsRemoved() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode shell = document.div();
        DocumentAutocompleteInputControl autocompleteControl = new DocumentAutocompleteInputControl(document,
                "Alpha", "Beta");
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(180));
        autocompleteControl.getElement().style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(32));
        shell.append(autocompleteControl.getElement());
        root.append(shell);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 180,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 180);

        widget.onFocusTraversalEntered(false);
        ElementNode popup = findListboxElement(root);
        Assert.assertTrue(document.__isTopLayerElement(popup));

        root.removeChild(shell);

        Assert.assertFalse(document.__isTopLayerElement(popup));
        Assert.assertFalse(autocompleteControl.isOpen());
        Assert.assertEquals("false", autocompleteControl.getElement().getAttribute("aria-expanded"));
        Assert.assertEquals(UiDisplay.NONE, popup.style().getDisplay());
        Assert.assertEquals(UiPosition.ABSOLUTE, popup.style().getPosition());
    }

    /**
     * 验证 transform 祖先下的 autocomplete popup 会按输入框视觉边界锚定并命中候选。
     */
    @Test
    public void shouldAnchorPopupToTransformedInputVisualBounds() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode transformed = document.div();
        DocumentAutocompleteInputControl autocompleteControl = new DocumentAutocompleteInputControl(document,
                "Alpha", "Beta", "Gamma");
        root.style()
                .setWidth(UiStyleLength.px(320))
                .setHeight(UiStyleLength.px(240));
        transformed.style()
                .setWidth(UiStyleLength.px(180))
                .setTransform(UiTransform.translate(80.0F, 40.0F));
        autocompleteControl.getElement().style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(32));
        transformed.append(autocompleteControl.getElement());
        root.append(transformed);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 320, 240,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 320, 240);

        click(widget, 90, 52, 1L);
        ElementNode popup = findListboxElement(root);
        DocumentElementBounds inputBounds = autocompleteControl.getElement().__getVisualDocumentBounds();

        Assert.assertTrue(document.__isTopLayerElement(popup));
        Assert.assertEquals(UiStyleLength.px(inputBounds.getLeft()), popup.style().getLeft());
        Assert.assertEquals(UiStyleLength.px(inputBounds.getTop() + inputBounds.getHeight()),
                popup.style().getTop());

        ElementNode secondOption = findOptionElement(popup, 1);
        DocumentElementBounds optionBounds = secondOption.getDocumentBounds();
        click(widget, optionBounds.getLeft() + 8, optionBounds.getTop() + 8, 3L);

        Assert.assertEquals("Beta", autocompleteControl.getText());
    }

    private static HtmlLikeDocumentWidget mount(UiDocument document,
            DocumentAutocompleteInputControl autocompleteControl, int width, int height) {
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(width))
                .setHeight(UiStyleLength.px(height));
        autocompleteControl.getElement().style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(32));
        root.append(autocompleteControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, width, height,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, width, height);
        return widget;
    }

    private static void click(HtmlLikeDocumentWidget widget, int x, int y, long timeNanos) {
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, x, y, 0, 0, 0, 0, timeNanos));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, x, y, 0, 0, 0, 0, timeNanos + 1L));
    }

    private static void key(HtmlLikeDocumentWidget widget, int keyCode, long timeNanos) {
        widget.onKeyEvent(new UiKeyEvent(keyCode, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false, false,
                timeNanos));
    }

    private static ElementNode findListboxElement(ElementNode element) {
        if ("listbox".equals(element.getAttribute("role"))) {
            return element;
        }
        for (DocumentNode child : element.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findListboxElement((ElementNode) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static ElementNode findOptionElement(ElementNode popup, int optionIndex) {
        int index = 0;
        for (DocumentNode child : popup.getChildren()) {
            if (child instanceof ElementNode && "option".equals(((ElementNode) child).getTagName())) {
                if (index == optionIndex) {
                    return (ElementNode) child;
                }
                index++;
            }
        }
        return null;
    }

    private static int countOptionElements(ElementNode popup) {
        int count = 0;
        for (DocumentNode child : popup.getChildren()) {
            if (child instanceof ElementNode && "option".equals(((ElementNode) child).getTagName())) {
                count++;
            }
        }
        return count;
    }

    private static String[] createOptions(int count) {
        String[] options = new String[count];
        for (int index = 0; index < count; index++) {
            options[index] = "Option " + index;
        }
        return options;
    }

    /**
     * 记录 query 并按大小写不敏感包含关系返回候选的测试 provider。
     */
    private static final class RecordingProvider implements DocumentAutocompleteSuggestionProvider {

        private final List<String> options = new ArrayList<String>();
        private final List<String> queries = new ArrayList<String>();

        private RecordingProvider(String... options) {
            if (options != null) {
                for (int index = 0; index < options.length; index++) {
                    this.options.add(options[index]);
                }
            }
        }

        @Override
        public List<String> getSuggestions(String query) {
            String normalizedQuery = query == null ? "" : query;
            queries.add(normalizedQuery);
            if (normalizedQuery.isEmpty()) {
                return new ArrayList<String>(options);
            }
            String lowerQuery = normalizedQuery.toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<String>();
            for (int index = 0; index < options.size(); index++) {
                String option = options.get(index);
                if (option.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                    matches.add(option);
                }
            }
            return matches;
        }

        private List<String> getQueries() {
            return queries;
        }

        private void clearQueries() {
            queries.clear();
        }
    }

    /**
     * 供测试使用的确定性文本测量服务。
     */
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
            return text == null || targetWidth <= 0 ? "" : text.substring(0,
                    Math.min(text.length(), targetWidth / 6));
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }
}
