package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleInsets;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * HTML-like 大面积磨玻璃测试页控制器。
 */
final class HtmlLikeGlassDocumentPageController extends DocumentPageController {

    private final DocumentPageAuthoringSurface documentPage;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;

    /**
     * 创建大面积磨玻璃测试页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param documentPage 文档页面壳
     */
    HtmlLikeGlassDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface documentPage) {
        this(documentUi, documentPage, DefaultTextMeasureService.getInstance());
    }

    /**
     * 使用指定文本测量服务创建大面积磨玻璃测试页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param documentPage 文档页面壳
     * @param textMeasureService HTML-like 文本测量服务
     */
    HtmlLikeGlassDocumentPageController(DocumentUiScope documentUi, DocumentPageAuthoringSurface documentPage,
            TextMeasureService textMeasureService) {
        Objects.requireNonNull(documentUi, "documentUi");
        this.documentPage = Objects.requireNonNull(documentPage, "documentPage");
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(createGlassDocument(), 860, 560,
                Objects.requireNonNull(textMeasureService, "textMeasureService"));
        this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));
    }

    @Override
    void configureDocumentPage() {
        documentPage.setContentWidthRange(760, 1180)
                .setMinContentHeight(620)
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

    private static UiDocument createGlassDocument() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(0xE80B1020)
                .setBorderColor(0xFF60A5FA)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(22))
                .setTextColor(0xFFEFF6FF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);

        appendHeader(document, root);
        appendGlassStage(document, root);
        appendNotes(document, root);
        return document;
    }

    private static void appendHeader(UiDocument document, ElementNode root) {
        ElementNode header = document.div();
        header.style()
                .setHeight(UiStyleLength.px(74))
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF93C5FD)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(16))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        header.appendText("HTML-like Glass Lab");
        header.appendText("Dedicated large-area backdrop-filter page for UI layer sampling tests.");
        root.append(header);
    }

    private static void appendGlassStage(UiDocument document, ElementNode root) {
        ElementNode stage = document.div();
        stage.style()
                .setHeight(UiStyleLength.px(430))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(16), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(0xFF172033)
                .setBorderColor(0xFF2563EB)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(20))
                .setTextColor(0xFFE0F2FE)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        root.append(stage);

        appendSampleRow(document, stage, 0xFFEC4899, 0xFF38BDF8, 0xFFFBBF24,
                "UI layer sampling field / magenta + cyan + amber");
        appendSampleRow(document, stage, 0xFF22C55E, 0xFFA855F7, 0xFFF97316,
                "Large glass covers this text and these color blocks");
        appendSampleRow(document, stage, 0xFF06B6D4, 0xFF84CC16, 0xFFEF4444,
                "Watch the stripes under the slab while resizing or scrolling");
        appendSampleRow(document, stage, 0xFF6366F1, 0xFFEAB308, 0xFF14B8A6,
                "Backdrop should blur the already-painted UI layer only");

        ElementNode glassSlab = document.div();
        glassSlab.style()
                .setWidth(UiStyleLength.percent(0.86F))
                .setHeight(UiStyleLength.px(260))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(-316), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.percent(0.07F)))
                .setPadding(UiStyleLength.px(16))
                .setBackgroundColor(0x44FFFFFF)
                .setBorderColor(0xDDFFFFFF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(20))
                .setTextColor(0xFFFFFFFF)
                .setBackdropBlurRadius(UiStyleLength.px(24))
                .setBackdropSaturation(1.6F)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        glassSlab.appendText("Large backdrop slab: blur 24px / saturate 160%");
        glassSlab.appendText("This slab intentionally covers most of the sampling field so visual regressions are easy to see.");
        glassSlab.appendText("Element text remains sharp; only previously painted UI behind this element is sampled.");
        stage.append(glassSlab);
    }

    private static void appendSampleRow(UiDocument document, ElementNode parent, int firstColor, int secondColor,
            int thirdColor, String label) {
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(12))
                .setHeight(UiStyleLength.px(72))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(0), UiStyleLength.px(12),
                        UiStyleLength.px(0)));
        parent.append(row);

        appendSampleBlock(document, row, firstColor, "A");
        appendSampleBlock(document, row, secondColor, "B");
        appendSampleBlock(document, row, thirdColor, label);
    }

    private static void appendSampleBlock(UiDocument document, ElementNode parent, int color, String label) {
        ElementNode block = document.div();
        block.style()
                .setFlexGrow(1.0F)
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(color)
                .setBorderColor(0x66FFFFFF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(16))
                .setTextColor(0xFFFFFFFF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        block.appendText(label);
        parent.append(block);
    }

    private static void appendNotes(UiDocument document, ElementNode root) {
        ElementNode notes = document.div();
        notes.style()
                .setHeight(UiStyleLength.px(76))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(16), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(16))
                .setTextColor(0xFFCBD5E1)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        notes.appendText("Validation notes: resize the screen, compare the large slab against the uncovered color grid, and watch for clip leaks or sampling offsets.");
        root.append(notes);
    }
}
