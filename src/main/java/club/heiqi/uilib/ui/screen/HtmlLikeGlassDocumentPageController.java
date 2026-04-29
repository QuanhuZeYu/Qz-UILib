package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiPosition;
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
    private TextNode backdropPathText;
    private TextNode tileProbeBackdropPathText;

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
        UiDocument document = createGlassDocument();
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 860, 560,
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

    @Override
    void beforeDocumentFrame() {
        String pathText = formatBackdropPathText();
        if (backdropPathText != null) {
            backdropPathText.setText(pathText);
        }
        if (tileProbeBackdropPathText != null) {
            tileProbeBackdropPathText.setText("Tile probe " + pathText);
        }
    }

    /**
     * 返回当前页面使用的 HTML-like 适配组件。
     *
     * @return HTML-like 文档适配组件
     */
    HtmlLikeDocumentWidget getHtmlLikeDocumentWidget() {
        return htmlLikeDocumentWidget;
    }

    private UiDocument createGlassDocument() {
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

        backdropPathText = appendHeader(document, root);
        appendGlassStage(document, root);
        appendNotes(document, root);
        return document;
    }

    private static TextNode appendHeader(UiDocument document, ElementNode root) {
        ElementNode header = document.div();
        header.style()
                .setHeight(UiStyleLength.px(94))
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF93C5FD)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(16))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        header.appendText("HTML-like Glass Lab");
        header.appendText("Dedicated large-area backdrop-filter page for UI layer sampling tests.");
        TextNode pathText = header.appendText("Backdrop path: pending");
        root.append(header);
        return pathText;
    }

    private void appendGlassStage(UiDocument document, ElementNode root) {
        ElementNode stage = document.div();
        stage.style()
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

        ElementNode samplingField = document.div();
        samplingField.style()
                .setHeight(UiStyleLength.px(320))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        stage.append(samplingField);

        appendSampleRow(document, samplingField, 0xFFEC4899, 0xFF38BDF8, 0xFFFBBF24,
                "UI layer sampling field / magenta + cyan + amber");
        appendSampleRow(document, samplingField, 0xFF22C55E, 0xFFA855F7, 0xFFF97316,
                "Large glass covers this text and these color blocks");
        appendSampleRow(document, samplingField, 0xFF06B6D4, 0xFF84CC16, 0xFFEF4444,
                "Watch the stripes under the slab while resizing or scrolling");
        appendSampleRow(document, samplingField, 0xFF6366F1, 0xFFEAB308, 0xFF14B8A6,
                "Backdrop should blur the already-painted UI layer only");

        ElementNode glassSlab = document.div();
        glassSlab.style()
                .setWidth(UiStyleLength.percent(0.86F))
                .setHeight(UiStyleLength.px(260))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(-316))
                .setLeft(UiStyleLength.percent(0.07F))
                .setZIndex(1)
                .setPadding(UiStyleLength.px(16))
                .setBackgroundColor(0x44FFFFFF)
                .setBorderColor(0xDDFFFFFF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(20))
                .setTextColor(0xFFFFFFFF)
                .setBackdropBlurRadius(UiStyleLength.px(36))
                .setBackdropSaturation(1.25F)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        glassSlab.appendText("Large backdrop slab: blur 36px / saturate 125%");
        glassSlab.appendText("This slab intentionally covers most of the sampling field so visual regressions are easy to see.");
        glassSlab.appendText("Element text remains sharp; only previously painted UI behind this element is sampled.");
        samplingField.append(glassSlab);

        appendNestedGlassRegressionScene(document, stage);
        appendTileAtlasProbeScene(document, stage);
    }

    /**
     * 追加多层级磨玻璃回归区，覆盖 6 块 backdrop 组合场景。
     *
     * @param document 文档实例
     * @param stage 父级测试容器
     */
    private static void appendNestedGlassRegressionScene(UiDocument document, ElementNode stage) {
        ElementNode nestedScene = document.div();
        nestedScene.style()
                .setHeight(UiStyleLength.px(372))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(18), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xFF122235)
                .setBorderColor(0xFF3B82F6)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(18))
                .setTextColor(0xFFD6E9FF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        nestedScene.appendText("Nested glass stack / 6 additional backdrop blocks");
        nestedScene.appendText("Three nested shells + three glasses at different hierarchy levels.");
        stage.append(nestedScene);

        ElementNode nestedCanvas = document.div();
        nestedCanvas.style()
                .setHeight(UiStyleLength.px(286))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(10), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        nestedScene.append(nestedCanvas);

        appendSampleRow(document, nestedCanvas, 0xFFFB7185, 0xFF60A5FA, 0xFFFACC15,
                "Nested field backdrop row / stage layer");
        appendSampleRow(document, nestedCanvas, 0xFF34D399, 0xFFC084FC, 0xFFFB923C,
                "Outer and middle hierarchy glasses sample these blocks");
        appendSampleRow(document, nestedCanvas, 0xFF38BDF8, 0xFFA3E635, 0xFFF87171,
                "Inner shell should still blur parent-painted content");

        ElementNode outerGlass = createGlassBlock(document, 0x36FFFFFF, 0xCCFFFFFF, 18, 120,
                "Outer glass shell", "Nested level 1 / backdrop #2");
        outerGlass.style()
                .setWidth(UiStyleLength.percent(0.78F))
                .setHeight(UiStyleLength.px(212))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(-196), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(28)));
        nestedCanvas.append(outerGlass);

        ElementNode outerLevelGlass = createGlassBlock(document, 0x40D946EF, 0xCCF5D0FE, 12, 116,
                "Outer level glass", "Hierarchy probe / backdrop #3");
        outerLevelGlass.style()
                .setWidth(UiStyleLength.percent(0.44F))
                .setHeight(UiStyleLength.px(68))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(10), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.percent(0.42F)));
        outerGlass.append(outerLevelGlass);

        ElementNode middleGlass = createGlassBlock(document, 0x40BFDBFE, 0xCCE0F2FE, 12, 114,
                "Middle glass shell", "Nested level 2 / backdrop #4");
        middleGlass.style()
                .setWidth(UiStyleLength.percent(0.76F))
                .setHeight(UiStyleLength.px(106))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(12), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(22)));
        outerGlass.append(middleGlass);

        ElementNode middleLevelGlass = createGlassBlock(document, 0x40FDE68A, 0xCCFEF3C7, 10, 112,
                "Middle level glass", "Hierarchy probe / backdrop #5");
        middleLevelGlass.style()
                .setWidth(UiStyleLength.percent(0.42F))
                .setHeight(UiStyleLength.px(42))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.percent(0.45F)));
        middleGlass.append(middleLevelGlass);

        ElementNode innerGlass = createGlassBlock(document, 0x406EE7B7, 0xCCDCFCE7, 10, 112,
                "Inner glass shell", "Nested level 3 / backdrop #6");
        innerGlass.style()
                .setWidth(UiStyleLength.percent(0.7F))
                .setHeight(UiStyleLength.px(40))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(16)));
        middleGlass.append(innerGlass);

        ElementNode sceneLevelGlass = createGlassBlock(document, 0x40F9A8D4, 0xCCFCE7F3, 12, 114,
                "Scene level glass", "Direct canvas sibling / additional backdrop");
        sceneLevelGlass.style()
                .setWidth(UiStyleLength.percent(0.34F))
                .setHeight(UiStyleLength.px(58))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(-250), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.percent(0.58F)));
        nestedCanvas.append(sceneLevelGlass);
    }

    /**
     * 追加 tile atlas 诊断区，用于在游戏内观察 block 分桶、tile 计数和 atlas 覆盖诊断。
     *
     * @param document 文档实例
     * @param stage 父级测试容器
     */
    private void appendTileAtlasProbeScene(UiDocument document, ElementNode stage) {
        ElementNode probeScene = document.div();
        probeScene.style()
                .setHeight(UiStyleLength.px(334))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(18), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xFF132033)
                .setBorderColor(0xFF0EA5E9)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(18))
                .setTextColor(0xFFE0F2FE)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        probeScene.appendText("Tile atlas probe / block128 tile diagnostics");
        probeScene.appendText("Header Backdrop path should include region=..., tiles=N covered=M missing=K reused=R copied=C, and filter=...");
        tileProbeBackdropPathText = probeScene.appendText("Tile probe Backdrop path: pending");
        stage.append(probeScene);

        ElementNode probeCanvas = document.div();
        probeCanvas.style()
                .setHeight(UiStyleLength.px(238))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(10), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        probeScene.append(probeCanvas);

        appendSampleRow(document, probeCanvas, 0xFF0284C7, 0xFF65A30D, 0xFFCA8A04,
                "Tile grid row A / watch copied tile count");
        appendSampleRow(document, probeCanvas, 0xFFBE185D, 0xFF7C3AED, 0xFF0F766E,
                "Tile grid row B / atlas coverage target");

        ElementNode sourceGlass = createGlassBlock(document, 0x34FFFFFF, 0xCCBAE6FD, 18, 120,
                "Atlas source slab", "Large source block for tile coverage");
        sourceGlass.style()
                .setWidth(UiStyleLength.percent(0.62F))
                .setHeight(UiStyleLength.px(116))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(-154), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(24)));
        probeCanvas.append(sourceGlass);

        ElementNode coveredTargetGlass = createGlassBlock(document, 0x40C4B5FD, 0xCCEDE9FE, 18, 120,
                "Atlas target inside source", "Stable rev may show atlas-block128 or tile-atlas-block128");
        coveredTargetGlass.style()
                .setWidth(UiStyleLength.percent(0.32F))
                .setHeight(UiStyleLength.px(58))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(-96), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.percent(0.22F)));
        probeCanvas.append(coveredTargetGlass);

        ElementNode tileCountTargetGlass = createGlassBlock(document, 0x40BAE6FD, 0xCCE0F2FE, 36, 125,
                "Tile count target", "Local path line updates with tiles=... and downsample filter");
        tileCountTargetGlass.style()
                .setWidth(UiStyleLength.percent(0.38F))
                .setHeight(UiStyleLength.px(64))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(-64), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.percent(0.56F)));
        probeCanvas.append(tileCountTargetGlass);
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

    /**
     * 创建统一风格的磨玻璃块。
     *
     * @param document 文档实例
     * @param backgroundColor 半透明底色
     * @param borderColor 边框色
     * @param blurRadius 模糊半径
     * @param saturation 饱和度百分比值
     * @param title 标题
     * @param body 文案
     * @return 磨玻璃元素
     */
    private static ElementNode createGlassBlock(UiDocument document, int backgroundColor, int borderColor,
            int blurRadius, int saturation, String title, String body) {
        ElementNode glassBlock = document.div();
        glassBlock.style()
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(backgroundColor)
                .setBorderColor(borderColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFFFFFFF)
                .setBackdropBlurRadius(UiStyleLength.px(blurRadius))
                .setBackdropSaturation((float) saturation / 100.0F)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        glassBlock.appendText(title);
        glassBlock.appendText(body);
        return glassBlock;
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
        notes.appendText("Validation notes: resize the screen, compare the large slab against the uncovered color grid, and watch Backdrop path for region=..., tiles=..., filter=..., clip leaks or sampling offsets.");
        root.append(notes);
    }

    private static String formatBackdropPathText() {
        UiRenderContext.BackdropFilterRenderPath renderPath = UiRenderContext.getLastBackdropFilterRenderPath();
        return "Backdrop path: " + renderPath.getLabel() + " / " + UiRenderContext.getLastBackdropFilterDetail();
    }

}
