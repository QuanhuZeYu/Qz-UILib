package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.dom.control.DocumentButtonControl;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputChangeEvent;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputChangeHandler;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.dom.control.DocumentToggleSwitchControl;
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
 * HTML-like 渲染链路的最小可见 smoke 页面控制器。
 */
final class HtmlLikeSmokeDocumentPageController extends DocumentPageController {

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
        Objects.requireNonNull(documentUi, "documentUi");
        this.documentPage = Objects.requireNonNull(documentPage, "documentPage");
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(createSmokeDocument(), 760, 320,
                Objects.requireNonNull(textMeasureService, "textMeasureService"));
        this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));
    }

    @Override
    void configureDocumentPage() {
        documentPage.setContentWidthRange(680, 1080)
                .setMinContentHeight(520)
                .setViewportFillRatio(0.92F, 0.90F);
    }

    @Override
    void buildDocument() {
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
                .setBackgroundColor(0xF00B1020)
                .setBorderColor(0xFF7C3AED)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(18))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);

        ElementNode header = document.div();
        header.style()
                .setHeight(UiStyleLength.px(58))
                .setMargin(UiStyleLength.px(0))
                .setBackgroundColor(0xFF1E1B4B)
                .setBorderColor(0xFFA78BFA)
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
        header.appendText("HTML-like Smoke Lab");
        header.appendText("UiDocument -> style -> layout -> paint command -> UiRenderContext / TEXT paint command");

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

        ElementNode backdropStage = document.div();
        backdropStage.style()
                .setWidth(UiStyleLength.px(218))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF1A202C)
                .setBorderColor(0xFF4FD1C5)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFE6FFFA)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        row.append(backdropStage);

        ElementNode sampleTitle = document.div();
        sampleTitle.style()
                .setHeight(UiStyleLength.px(18))
                .setTextColor(0xFFB2F5EA);
        sampleTitle.appendText("Same-layer sampling grid");
        backdropStage.append(sampleTitle);

        ElementNode hotStripe = document.div();
        hotStripe.style()
                .setWidth(UiStyleLength.px(246))
                .setHeight(UiStyleLength.px(18))
                .setBackgroundColor(0xFFED64A6)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFFFFFFF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        hotStripe.appendText("pink stripe behind glass");
        backdropStage.append(hotStripe);

        ElementNode tealStripe = document.div();
        tealStripe.style()
                .setWidth(UiStyleLength.px(156))
                .setHeight(UiStyleLength.px(18))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(6), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(54)))
                .setBackgroundColor(0xFF38B2AC)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFFFFFFF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        tealStripe.appendText("teal text target");
        backdropStage.append(tealStripe);

        ElementNode amberStripe = document.div();
        amberStripe.style()
                .setWidth(UiStyleLength.px(206))
                .setHeight(UiStyleLength.px(18))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(6), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(10)))
                .setBackgroundColor(0xFFD69E2E)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFF1A202C)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        amberStripe.appendText("amber UI behind this card");
        backdropStage.append(amberStripe);

        ElementNode glassCard = document.div();
        glassCard.style()
                .setWidth(UiStyleLength.px(158))
                .setHeight(UiStyleLength.px(62))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(-58), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(28)))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0x55FFFFFF)
                .setBorderColor(0xCCFFFFFF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFFFFFFF)
                .setBackdropBlurRadius(UiStyleLength.px(14))
                .setBackdropSaturation(1.4F)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        glassCard.appendText("Backdrop glass overlap: blur 14px / saturate 140%");
        backdropStage.append(glassCard);

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

        final DocumentTextInputControl textInputControl = new DocumentTextInputControl(document);
        textInputControl.setPlaceholder("Type target: click then type")
                .setMaxLength(18)
                .setNormalBackgroundColor(0xFFE53E3E)
                .setNormalBorderColor(0xFFE53E3E)
                .setFocusBorderColor(0xFFD69E2E)
                .setTextColors(0xFFFFFFFF, 0xFFDDBBBB, 0xFF886666)
                .setChangeHandler(new DocumentTextInputChangeHandler() {
                    @Override
                    public void onTextChanged(DocumentTextInputChangeEvent event) {
                        String text = event.getText();
                        textInputControl.getElement().style()
                                .setBackgroundColor(text.isEmpty() ? 0xFFE53E3E : 0xFFC53030);
                    }
                });
        textInputControl.getElement().style()
                .setFlexGrow(2.0F)
                .setBorderRadius(UiStyleLength.px(999))
                .setPadding(UiStyleLength.px(10));
        footer.append(textInputControl.getElement());

        final ElementNode tabPill = document.div();
        tabPill.style()
                .setFlexGrow(1.0F)
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF4A5568)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFFFFFFF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        final TextNode tabText = tabPill.appendText(formatTabFocusLabel(false));
        tabPill.setFocusable(true).setFocusHandler(new DocumentElementFocusHandler() {
            @Override
            public void onFocusChanged(DocumentElementFocusEvent event) {
                tabPill.style().setBorderColor(event.isFocusVisible() ? 0xFFD6BCFA : 0);
                tabText.setText(formatTabFocusLabel(event.isFocusVisible()));
            }
        });
        footer.append(tabPill);

        final DocumentButtonControl buttonControl = new DocumentButtonControl(document, "Button ctrl: 0");
        final int[] buttonCount = new int[] { 0 };
        buttonControl.setBackgroundColors(0xFF2B6CB0, 0xFF2C5282, 0xFF4A5568)
                .setFocusBorderColor(0xFFBEE3F8)
                .setActionHandler(new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        buttonCount[0]++;
                        event.getSource().setLabel("Button ctrl: " + buttonCount[0]);
                    }
                });
        buttonControl.getElement().style().setFlexGrow(1.0F);
        footer.append(buttonControl.getElement());

        final DocumentToggleSwitchControl toggleControl = new DocumentToggleSwitchControl(document);
        toggleControl.setToggled(true)
                .setTrackColors(0xFF718096, 0xFF48BB78, 0xFF333344)
                .setFocusBorderColor(0xFFBEE3F8);
        toggleControl.getElement().style().setFlexGrow(0.6F);
        footer.append(toggleControl.getElement());
        return document;
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
}
