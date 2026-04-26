package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.document.DocumentTextWidget;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiJustifyContent;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * HTML-like 渲染链路的最小可见 smoke 页面控制器。
 */
final class HtmlLikeSmokeDocumentPageController extends DocumentPageController {

    private final DocumentUiScope documentUi;
    private final DocumentPageAuthoringSurface documentPage;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;

    /**
     * 创建 HTML-like smoke 页面控制器。
     *
     * @param documentUi 文档组件作用域
     * @param documentPage 文档页面壳
     */
    HtmlLikeSmokeDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface documentPage) {
        this(documentUi, documentPage, DefaultTextMeasureService.getInstance());
    }

    /**
     * 使用指定文本测量服务创建 HTML-like smoke 页面控制器。
     *
     * @param documentUi 文档组件作用域
     * @param documentPage 文档页面壳
     * @param textMeasureService HTML-like 文本测量服务
     */
    HtmlLikeSmokeDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface documentPage,
            TextMeasureService textMeasureService) {
        this.documentUi = Objects.requireNonNull(documentUi, "documentUi");
        this.documentPage = Objects.requireNonNull(documentPage, "documentPage");
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(createSmokeDocument(), 760, 320,
                Objects.requireNonNull(textMeasureService, "textMeasureService"));
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.px(320)));
    }

    @Override
    void configureDocumentPage() {
        documentPage.setContentWidthRange(680, 1080)
                .setMinContentHeight(520)
                .setViewportFillRatio(0.92F, 0.90F);
    }

    @Override
    void buildDocument() {
        documentPage.addBlock(documentUi.text(DocumentTextWidget.Role.TITLE, "HTML-like Smoke", 2));
        documentPage.addBlock(documentUi.text(DocumentTextWidget.Role.BODY,
                "下方色块由 UiDocument -> style -> layout -> paint command -> UiRenderContext 完整链路绘制。", 8));
        documentPage.addBlock(htmlLikeDocumentWidget);
    }

    /**
     * 返回当前 smoke 页面使用的 HTML-like 适配组件。
     *
     * @return HTML-like 文档适配组件
     */
    HtmlLikeDocumentWidget getHtmlLikeDocumentWidget() {
        return htmlLikeDocumentWidget;
    }

    private static UiDocument createSmokeDocument() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(0xEE151A24)
                .setBorderColor(0xFF4A78D8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(18));

        ElementNode header = document.div();
        header.style()
                .setHeight(UiStyleLength.px(58))
                .setMargin(UiStyleLength.px(0))
                .setBackgroundColor(0xFF213556)
                .setBorderColor(0xFF6B96FF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFEFF6FF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        root.append(header);

        ElementNode clippedStripe = document.div();
        clippedStripe.style()
                .setWidth(UiStyleLength.px(900))
                .setHeight(UiStyleLength.px(22))
                .setBackgroundColor(0xFFED64A6)
                .setBorderRadius(UiStyleLength.px(999));
        header.append(clippedStripe);
        header.appendText("TextNode -> TEXT paint command");

        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.STRETCH)
                .setJustifyContent(UiJustifyContent.START)
                .setColumnGap(UiStyleLength.px(14))
                .setHeight(UiStyleLength.px(112))
                .setMargin(UiStyleLength.px(16));
        root.append(row);

        ElementNode fixedCard = document.div();
        fixedCard.style()
                .setWidth(UiStyleLength.px(126))
                .setBackgroundColor(0xFF805AD5)
                .setBorderRadius(UiStyleLength.px(12));
        row.append(fixedCard);

        ElementNode fluidCard = document.div();
        fluidCard.style()
                .setFlexGrow(1.0F)
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF2C7A7B)
                .setBorderColor(0xFF81E6D9)
                .setBorderWidth(UiStyleLength.px(2))
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFFFFFFF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        row.append(fluidCard);
        fluidCard.appendText("HTML-like text now uses the shared text measurement service and wraps across multiple "
                + "lines when the card becomes narrow. This teal card also has overflow-y auto, so the text is "
                + "clipped by the padding box and can be moved with the mouse wheel when the content exceeds the "
                + "visible card height. Scroll here to verify that the background and border stay fixed while only "
                + "the inner text content moves.");

        ElementNode sideCard = document.div();
        sideCard.style()
                .setWidth(UiStyleLength.px(92))
                .setBackgroundColor(0xFFD69E2E)
                .setBorderRadius(UiStyleLength.px(12));
        row.append(sideCard);

        ElementNode footer = document.div();
        footer.style()
                .setHeight(UiStyleLength.px(46))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(10));
        root.append(footer);

        final ElementNode firstPill = document.div();
        firstPill.style()
                .setFlexGrow(1.0F)
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF38A169)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFFFFFFF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        final TextNode clickText = firstPill.appendText("Click target: 0");
        final int[] clickCount = new int[] { 0 };
        firstPill.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickCount[0]++;
                clickText.setText("Click target: " + clickCount[0]);
                firstPill.style().setBackgroundColor(clickCount[0] % 2 == 0 ? 0xFF38A169 : 0xFF3182CE);
                return true;
            }
        });
        footer.append(firstPill);

        final ElementNode secondPill = document.div();
        secondPill.style()
                .setFlexGrow(2.0F)
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFFE53E3E)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFFFFFFF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        final StringBuilder inputValue = new StringBuilder();
        final TextNode inputText = secondPill.appendText(formatInputLabel(inputValue));
        secondPill.setFocusable(true)
                .setFocusHandler(new DocumentElementFocusHandler() {
                    @Override
                    public void onFocusChanged(DocumentElementFocusEvent event) {
                        secondPill.style().setBackgroundColor(event.isFocused() ? 0xFFD69E2E : 0xFFE53E3E);
                        inputText.setText(formatInputLabel(inputValue));
                    }
                })
                .setTextInputHandler(new DocumentElementTextInputHandler() {
                    @Override
                    public boolean onTextInput(DocumentElementTextInputEvent event) {
                        if (appendAcceptedInputText(inputValue, event.getText())) {
                            inputText.setText(formatInputLabel(inputValue));
                        }
                        return true;
                    }
                })
                .setKeyHandler(new DocumentElementKeyHandler() {
                    @Override
                    public boolean onKey(DocumentElementKeyEvent event) {
                        if (isBackspaceDeleteEvent(event) && inputValue.length() > 0) {
                            int deleteStart = inputValue.offsetByCodePoints(inputValue.length(), -1);
                            inputValue.delete(deleteStart, inputValue.length());
                            inputText.setText(formatInputLabel(inputValue));
                            return true;
                        }
                        return false;
                    }
                });
        footer.append(secondPill);

        final ElementNode tabPill = document.div();
        tabPill.style()
                .setFlexGrow(1.0F)
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF4A5568)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFFFFFFF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        final TextNode tabText = tabPill.appendText(formatTabFocusLabel(false));
        tabPill.setFocusable(true).setFocusHandler(new DocumentElementFocusHandler() {
            @Override
            public void onFocusChanged(DocumentElementFocusEvent event) {
                tabPill.style().setBackgroundColor(event.isFocused() ? 0xFF9F7AEA : 0xFF4A5568);
                tabText.setText(formatTabFocusLabel(event.isFocused()));
            }
        });
        footer.append(tabPill);
        return document;
    }

    /**
     * 把可接受的文本输入追加到 smoke 输入样例值中。
     *
     * @param inputValue 输入样例当前值
     * @param text 本次输入文本
     * @return 是否实际追加了可见字符
     */
    private static boolean appendAcceptedInputText(StringBuilder inputValue, String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (int index = 0; index < text.length();) {
            int codepoint = text.codePointAt(index);
            if (isAcceptedInputCodepoint(codepoint) && inputValue.codePointCount(0, inputValue.length()) < 18) {
                inputValue.appendCodePoint(codepoint);
                changed = true;
            }
            index += Character.charCount(codepoint);
        }
        return changed;
    }

    /**
     * 格式化 smoke 输入样例展示文本。
     *
     * @param inputValue 输入样例当前值
     * @return 展示文本
     */
    private static String formatInputLabel(StringBuilder inputValue) {
        return inputValue.length() == 0 ? "Type target: click then type" : "Type target: " + inputValue.toString();
    }

    /**
     * 格式化 smoke Tab 焦点样例展示文本。
     *
     * @param focused 当前是否聚焦
     * @return 展示文本
     */
    private static String formatTabFocusLabel(boolean focused) {
        return focused ? "Tab target: focused" : "Tab target: idle";
    }

    /**
     * 判断 codepoint 是否可以写入 smoke 输入样例。
     *
     * @param codepoint Unicode codepoint
     * @return 是否接受该字符
     */
    private static boolean isAcceptedInputCodepoint(int codepoint) {
        return !Character.isISOControl(codepoint) && codepoint != '\n' && codepoint != '\r' && codepoint != '\t';
    }

    /**
     * 判断按键事件是否应该触发输入样例退格删除。
     *
     * @param event HTML-like 元素按键事件
     * @return 是否是退格删除事件
     */
    private static boolean isBackspaceDeleteEvent(DocumentElementKeyEvent event) {
        return event.getKeyCode() == Keyboard.KEY_BACK
                && (event.getAction() == UiKeyEvent.Action.PRESSED || event.getAction() == UiKeyEvent.Action.REPEATED);
    }
}
