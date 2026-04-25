package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.document.DocumentTextWidget;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiJustifyContent;
import club.heiqi.uilib.ui.style.UiStyleLength;

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
        this.documentUi = Objects.requireNonNull(documentUi, "documentUi");
        this.documentPage = Objects.requireNonNull(documentPage, "documentPage");
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(createSmokeDocument(), 760, 320);
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
                "下方色块不是旧 Widget 直接排布，而是由 UiDocument -> style -> layout -> paint command -> UiRenderContext 完整链路绘制。", 8));
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
                .setBorderRadius(UiStyleLength.px(14));
        root.append(header);

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
                .setBackgroundColor(0xFF2C7A7B)
                .setBorderColor(0xFF81E6D9)
                .setBorderWidth(UiStyleLength.px(2))
                .setBorderRadius(UiStyleLength.px(12));
        row.append(fluidCard);

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

        ElementNode firstPill = document.div();
        firstPill.style()
                .setFlexGrow(1.0F)
                .setBackgroundColor(0xFF38A169)
                .setBorderRadius(UiStyleLength.px(999));
        footer.append(firstPill);

        ElementNode secondPill = document.div();
        secondPill.style()
                .setFlexGrow(2.0F)
                .setBackgroundColor(0xFFE53E3E)
                .setBorderRadius(UiStyleLength.px(999));
        footer.append(secondPill);
        return document;
    }
}
