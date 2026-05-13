package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * HTML-like 列表元素组件拖拽测试页控制器。
 */
final class HtmlLikeListDragDocumentPageController extends DocumentPageController {

    private final DocumentPageAuthoringSurface documentPage;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;

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
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(createDocument(), 760, 520,
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

    private static UiDocument createDocument() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setPadding(UiStyleLength.px(22))
                .setBackgroundColor(0xF00A1020)
                .setBorderColor(0xFF38BDF8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(24))
                .setTextColor(0xFFE8F7FF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);

        ElementNode header = document.div();
        header.style()
                .setHeight(UiStyleLength.px(108))
                .setPadding(UiStyleLength.px(16))
                .setBackgroundColor(0xFF0F2433)
                .setBorderColor(0xFF67E8F9)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(18))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        header.appendText("列表元素组件拖拽");
        header.appendText("HTML-like 心智：用普通 div 组合列表项，事件层只提供元素拖拽。排序行为由页面状态决定。");
        root.append(header);

        ElementNode placeholder = document.div();
        placeholder.style()
                .setMargin(UiStyleLength.px(16))
                .setPadding(UiStyleLength.px(16))
                .setBackgroundColor(0xFF142235)
                .setBorderColor(0xFF2F6F8F)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(16));
        placeholder.appendText("最小入口已接通，下一步补列表项拖拽排序交互。");
        root.append(placeholder);

        return document;
    }
}
