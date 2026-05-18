package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationImpact;
import club.heiqi.uilib.ui.animation.DocumentAnimationFillMode;
import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimingFunction;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.animation.DocumentKeyframes;
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
import club.heiqi.uilib.ui.dom.control.DocumentDraggableSupport;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputChangeEvent;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputChangeHandler;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.dom.control.DocumentToggleSwitchControl;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiBorderStyle;
import club.heiqi.uilib.ui.style.UiBoxSizing;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiJustifyContent;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleInsets;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.style.UiVerticalAlign;
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
        this(Objects.requireNonNull(documentUi, "documentUi"), documentPage, documentUi.getTextMeasureService());
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
        final HtmlLikeDocumentWidget[] widgetReference = new HtmlLikeDocumentWidget[1];
        UiDocument smokeDocument = createSmokeDocument(new AnimationRuntimeDiagnostics() {
            @Override
            public DocumentAnimationTimeline.DiagnosticsSnapshot getSnapshot() {
                return widgetReference[0] == null ? DocumentAnimationTimeline.DiagnosticsSnapshot.empty()
                        : widgetReference[0].getAnimationDiagnosticsSnapshot();
            }

            @Override
            public HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot getPerformanceSnapshot() {
                return widgetReference[0] == null ? HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot.empty()
                        : widgetReference[0].getPerformanceDiagnosticsSnapshot();
                }
        });
        smokeDocument.setDefaultTextContentMode(documentUi.getDefaultTextContentMode());
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(smokeDocument, 760, 320,
                Objects.requireNonNull(textMeasureService, "textMeasureService"));
        widgetReference[0] = this.htmlLikeDocumentWidget;
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

    private static UiDocument createSmokeDocument(AnimationRuntimeDiagnostics animationRuntimeDiagnostics) {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("smokePulse")
                .setFloatStop(DocumentAnimationProperty.BORDER_RADIUS, 0.0F, 999.0F)
                .setFloatStop(DocumentAnimationProperty.BORDER_RADIUS, 0.5F, 999.0F)
                .setFloatStop(DocumentAnimationProperty.BORDER_RADIUS, 1.0F, 12.0F)
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 0.0F, 0xFF38A169)
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 0.5F, 0xFF68D391)
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 1.0F, 0xFF805AD5)
                .build());
        document.registerKeyframes(DocumentKeyframes.named("opacityFboAuto")
                .setFloatStop(DocumentAnimationProperty.OPACITY, 0.0F, 1.0F)
                .setFloatStop(DocumentAnimationProperty.OPACITY, 0.5F, 0.45F)
                .setFloatStop(DocumentAnimationProperty.OPACITY, 1.0F, 1.0F)
                .build());
        document.registerKeyframes(DocumentKeyframes.named("layoutFillProbe")
                .setFloatStop(DocumentAnimationProperty.WIDTH, 0.0F, 92.0F)
                .setFloatStop(DocumentAnimationProperty.WIDTH, 0.5F, 174.0F)
                .setFloatStop(DocumentAnimationProperty.WIDTH, 1.0F, 144.0F)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xF00B1020)
                .setBorderColor(0xFF7C3AED)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(18))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);

        ElementNode header = document.div();
        header.style()
                .setHeight(UiStyleLength.px(74))
                .setMargin(UiStyleLength.px(0))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF1E1B4B)
                .setBorderColor(0xFFA78BFA)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFEFF6FF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        root.append(header);

        ElementNode clippedStripe = document.div();
        clippedStripe.style()
                .setWidth(UiStyleLength.px(900))
                .setHeight(UiStyleLength.px(14))
                .setBackgroundColor(0xFFED64A6)
                .setBorderRadius(UiStyleLength.px(999));
        header.append(clippedStripe);
        header.appendText("HTML-like Smoke Lab");
        header.appendText("UiDocument -> style -> layout -> paint command -> UiRenderContext / TEXT paint command");

        ElementNode fixedViewportProbe = document.div();
        fixedViewportProbe.style()
                .setWidth(UiStyleLength.px(150))
                .setHeight(UiStyleLength.px(30))
                .setPosition(UiPosition.FIXED)
                .setBottom(UiStyleLength.px(12))
                .setRight(UiStyleLength.px(14))
                .setZIndex(20)
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(0xEE0F766E)
                .setBorderColor(0xFF99F6E4)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFE6FFFA)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        fixedViewportProbe.appendText("FIXED viewport");
        root.append(fixedViewportProbe);

        appendControlsSection(document, root);
        appendOpacityFboProbe(document, root);
        appendLayoutAnimationProbe(document, root, animationRuntimeDiagnostics);
        appendFloatingScrollProbe(document, root);

        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.STRETCH)
                .setJustifyContent(UiJustifyContent.START)
                .setColumnGap(UiStyleLength.px(14))
                .setHeight(UiStyleLength.px(158))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(16), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)));
        root.append(row);

        ElementNode fixedCard = document.div();
        fixedCard.style()
                .setWidth(UiStyleLength.px(188))
                .setPosition(UiPosition.RELATIVE)
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF805AD5)
                .setBorderColor(0xFFD6BCFA)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFEDE9FE)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        row.append(fixedCard);
        fixedCard.appendText("ABS containing probe");

        ElementNode staticWrapperProbe = document.div();
        staticWrapperProbe.style()
                .setHeight(UiStyleLength.px(78))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF7C3AED)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(10))
                .setTextColor(0xFFC4B5FD);
        staticWrapperProbe.appendText("static wrapper is not anchor");
        fixedCard.append(staticWrapperProbe);

        ElementNode nestedAbsoluteProbe = document.div();
        nestedAbsoluteProbe.style()
                .setWidth(UiStyleLength.px(62))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(8))
                .setRight(UiStyleLength.px(8))
                .setZIndex(2)
                .setBackgroundColor(0xFFFFD166)
                .setBorderColor(0xFF1A202C)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFF1A202C)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        nestedAbsoluteProbe.appendText("ABS OK");
        staticWrapperProbe.append(nestedAbsoluteProbe);

        ElementNode fluidCard = document.div();
        fluidCard.style()
                .setFlexGrow(1.0F)
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF2C7A7B)
                .setBorderColor(0xFF81E6D9)
                .setBorderWidth(UiStyleLength.px(2))
                .setBorderStyle(UiBorderStyle.SOLID)
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
                .setWidth(UiStyleLength.px(282))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF1A202C)
                .setBorderColor(0xFF4FD1C5)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
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

        ElementNode absoluteBadge = document.div();
        absoluteBadge.style()
                .setWidth(UiStyleLength.px(72))
                .setHeight(UiStyleLength.px(18))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(8))
                .setRight(UiStyleLength.px(8))
                .setZIndex(2)
                .setBackgroundColor(0xEE2D3748)
                .setBorderColor(0xFFBEE3F8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFE6FFFA)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        absoluteBadge.appendText("ABS badge");
        backdropStage.append(absoluteBadge);

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
                .setWidth(UiStyleLength.px(188))
                .setHeight(UiStyleLength.px(66))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(-58))
                .setLeft(UiStyleLength.px(28))
                .setZIndex(1)
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0x55FFFFFF)
                .setBorderColor(0xCCFFFFFF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFFFFFFF)
                .setBackdropBlurRadius(UiStyleLength.px(14))
                .setBackdropSaturation(1.4F)
                .setTransitionProperties(DocumentAnimationProperty.BACKDROP_BLUR_RADIUS,
                        DocumentAnimationProperty.BORDER_RADIUS)
                .setTransitionDurationMillis(700L)
                .setTransitionTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        glassCard.appendText("Backdrop glass transition: click blur 4/22px");
        final int[] glassClickCount = new int[] { 0 };
        glassCard.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                glassClickCount[0]++;
                boolean expanded = glassClickCount[0] % 2 == 1;
                glassCard.style().setBackdropBlurRadius(UiStyleLength.px(expanded ? 22 : 4));
                glassCard.style().setBorderRadius(UiStyleLength.px(expanded ? 22 : 8));
                return true;
            }
        });
        backdropStage.append(glassCard);
        appendAbsoluteStretchAndInlineProbe(document, root);
        appendGroupOpacityProbe(document, root);
        appendStackingContextProbe(document, root);
        return document;
    }

    /**
     * 追加固定浮窗滚动探针，用于对照 HUD 浮窗与普通 HTML-like fixed 浮窗的滚动语义。
     */
    private static void appendFloatingScrollProbe(UiDocument document, ElementNode root) {
        ElementNode floatingPanel = document.div();
        floatingPanel.style()
                .setWidth(UiStyleLength.px(268))
                .setPosition(UiPosition.FIXED)
                .setTop(UiStyleLength.px(92))
                .setRight(UiStyleLength.px(18))
                .setZIndex(40)
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xEE111827)
                .setBorderColor(0xFF67E8F9)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFE0F2FE)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        root.append(floatingPanel);

        ElementNode dragBar = document.div();
        dragBar.style()
                .setMargin(UiStyleLength.px(0))
                .setPadding(UiStyleLength.px(4))
                .setBackgroundColor(0x2238BDF8)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFE0F2FE);
        dragBar.appendText("Smoke 浮窗：拖住这里移动");
        floatingPanel.append(dragBar);
        DocumentDraggableSupport.attach(floatingPanel, dragBar, DocumentDraggableSupport.DragAxis.BOTH);

        ElementNode title = document.div();
        title.style().setTextColor(0xFFBAE6FD);
        title.appendText("Floating scroll probe");
        floatingPanel.append(title);

        ElementNode summary = document.div();
        summary.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(6), UiStyleLength.px(0), UiStyleLength.px(6),
                        UiStyleLength.px(0)))
                .setTextColor(0xFFBFDBFE);
        summary.appendText("目标：验证同一套 HTML-like fixed 浮窗在普通 document screen 中是否能稳定形成内部滚动。");
        floatingPanel.append(summary);

        ElementNode scrollHost = document.div();
        scrollHost.style()
                .setHeight(UiStyleLength.px(154))
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xAA0F172A)
                .setBorderColor(0xFF38BDF8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(10))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        floatingPanel.append(scrollHost);

        ElementNode heading = document.div();
        heading.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setTextColor(0xFFE0F2FE);
        heading.appendText("正文滚动区");
        scrollHost.append(heading);

        DocumentTextInputControl textInput = new DocumentTextInputControl(document)
                .setPlaceholder("点击后输入，观察滚轮是否仍能滚动祖先")
                .setText("把鼠标放在这里或下方卡片正文上滚动");
        textInput.getElement().style()
                .setDisplay(UiDisplay.BLOCK)
                .setMargin(UiStyleInsets.of(UiStyleLength.px(6), UiStyleLength.px(0), UiStyleLength.px(6),
                        UiStyleLength.px(0)));
        scrollHost.append(textInput.getElement());

        for (int index = 1; index <= 6; index++) {
            ElementNode card = document.div();
            card.style()
                    .setBoxSizing(UiBoxSizing.BORDER_BOX)
                    .setWidth(UiStyleLength.percent(1.0F))
                    .setPadding(UiStyleLength.px(8))
                    .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(0), UiStyleLength.px(8),
                            UiStyleLength.px(0)))
                    .setBackgroundColor(0xCC1E293B)
                    .setBorderColor(0xFF7DD3FC)
                    .setBorderWidth(UiStyleLength.px(1))
                    .setBorderStyle(UiBorderStyle.SOLID)
                    .setBorderRadius(UiStyleLength.px(10))
                    .setTextColor(0xFFE2E8F0);
            card.appendText("Card " + index + " 标题");
            card.appendText("Card " + index + " 描述：用于复现固定浮窗中的内部 scroll host 与后代正文命中。"
                    + " 继续补充中文说明，确保文本会换行并形成明显纵向溢出。"
                    + " 再补充一段内容，验证滚轮命中输入框、卡片正文、卡片边框时，祖先滚动宿主都应滚动。"
                    + " 最后继续拉长文案，避免仅靠 margin 高度形成假性滚动范围。");
            scrollHost.append(card);
        }
    }

    /**
     * 追加独立交互控件测试区，避免控件被定位、backdrop 与布局探针挤在同一行中。
     *
     * @param document HTML-like 文档
     * @param root 文档根元素
     */
    private static void appendControlsSection(UiDocument document, ElementNode root) {
        ElementNode controlsSection = document.div();
        controlsSection.style()
                .setHeight(UiStyleLength.px(112))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(14), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF101827)
                .setBorderColor(0xFF22D3EE)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFE0F2FE)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        controlsSection.appendText("Controls probe: click, input, Tab, button, toggle");
        root.append(controlsSection);

        ElementNode animationDiagnostic = document.div();
        animationDiagnostic.style()
                .setHeight(UiStyleLength.px(14))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(4), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setTextColor(0xFFBAE6FD)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        animationDiagnostic.appendText("Animation diagnostics: pill=paint bg+radius 450ms; glass=blur+radius 700ms; opacity uses separate group probe.");
        controlsSection.append(animationDiagnostic);

        ElementNode keyframeDiagnostic = document.div();
        keyframeDiagnostic.style()
                .setHeight(UiStyleLength.px(14))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(2), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setTextColor(0xFFC4B5FD)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        keyframeDiagnostic.appendText("Keyframe diagnostics: first pill runs smokePulse bg+radius 0/50/100 stops once; fill-mode=both.");
        controlsSection.append(keyframeDiagnostic);

        ElementNode controlsRow = document.div();
        controlsRow.style()
                .setHeight(UiStyleLength.px(46))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(10))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(6), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)));
        controlsSection.append(controlsRow);

        final ElementNode firstPill = document.div();
        firstPill.style()
                .setFlexGrow(1.0F)
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF38A169)
                .setOpacity(1.0F)
                .setTransitionProperties(DocumentAnimationProperty.BACKGROUND_COLOR,
                        DocumentAnimationProperty.BORDER_RADIUS)
                .setTransitionDurationMillis(450L)
                .setTransitionTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                .setAnimation("smokePulse", 1200L)
                .setAnimationFillMode(DocumentAnimationFillMode.BOTH)
                .setAnimationTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
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
                firstPill.style().setBorderRadius(UiStyleLength.px(clickCount[0] % 2 == 0 ? 999 : 10));
                return true;
            }
        });
        controlsRow.append(firstPill);

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
        controlsRow.append(textInputControl.getElement());

        final ElementNode tabPill = document.div();
        tabPill.style()
                .setFlexGrow(1.0F)
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF4A5568)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
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
        controlsRow.append(tabPill);

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
        controlsRow.append(buttonControl.getElement());

        final DocumentToggleSwitchControl toggleControl = new DocumentToggleSwitchControl(document);
        toggleControl.setToggled(true)
                .setTrackColors(0xFF718096, 0xFF48BB78, 0xFF333344)
                .setFocusBorderColor(0xFFBEE3F8);
        toggleControl.getElement().setAttribute("data-smoke-control", "toggle");
        toggleControl.getElement().style().setFlexGrow(0.6F);
        controlsRow.append(toggleControl.getElement());
    }

    /**
     * 追加 opacity FBO 独立测试区，用于验证首次进入 opacity paint context 不会整屏闪烁。
     *
     * @param document HTML-like 文档
     * @param root 文档根元素
     */
    private static void appendOpacityFboProbe(UiDocument document, ElementNode root) {
        ElementNode probe = document.div();
        probe.style()
                .setHeight(UiStyleLength.px(200))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(12), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(10))
                .setPosition(UiPosition.RELATIVE)
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFFFBBF24)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFFFF7ED)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        probe.appendText("Opacity FBO probe: click card, auto card, and combo card cover opacity FBO paths.");
        root.append(probe);

        ElementNode amberStripe = document.div();
        amberStripe.style()
                .setWidth(UiStyleLength.px(360))
                .setHeight(UiStyleLength.px(16))
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(18))
                .setTop(UiStyleLength.px(44))
                .setBackgroundColor(0xFFF59E0B)
                .setBorderRadius(UiStyleLength.px(999));
        probe.append(amberStripe);

        ElementNode cyanStripe = document.div();
        cyanStripe.style()
                .setWidth(UiStyleLength.px(310))
                .setHeight(UiStyleLength.px(16))
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(56))
                .setTop(UiStyleLength.px(64))
                .setBackgroundColor(0xFF22D3EE)
                .setBorderRadius(UiStyleLength.px(999));
        probe.append(cyanStripe);

        ElementNode roseStripe = document.div();
        roseStripe.style()
                .setWidth(UiStyleLength.px(330))
                .setHeight(UiStyleLength.px(16))
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(28))
                .setTop(UiStyleLength.px(102))
                .setBackgroundColor(0xFFFB7185)
                .setBorderRadius(UiStyleLength.px(999));
        probe.append(roseStripe);

        ElementNode greenStripe = document.div();
        greenStripe.style()
                .setWidth(UiStyleLength.px(280))
                .setHeight(UiStyleLength.px(16))
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(76))
                .setTop(UiStyleLength.px(122))
                .setBackgroundColor(0xFF34D399)
                .setBorderRadius(UiStyleLength.px(999));
        probe.append(greenStripe);

        ElementNode violetStripe = document.div();
        violetStripe.style()
                .setWidth(UiStyleLength.px(350))
                .setHeight(UiStyleLength.px(16))
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(22))
                .setTop(UiStyleLength.px(160))
                .setBackgroundColor(0xFFA78BFA)
                .setBorderRadius(UiStyleLength.px(999));
        probe.append(violetStripe);

        ElementNode limeStripe = document.div();
        limeStripe.style()
                .setWidth(UiStyleLength.px(300))
                .setHeight(UiStyleLength.px(16))
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(70))
                .setTop(UiStyleLength.px(180))
                .setBackgroundColor(0xFFA3E635)
                .setBorderRadius(UiStyleLength.px(999));
        probe.append(limeStripe);

        final ElementNode opacityCard = document.div();
        opacityCard.style()
                .setWidth(UiStyleLength.px(238))
                .setHeight(UiStyleLength.px(42))
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(88))
                .setTop(UiStyleLength.px(34))
                .setZIndex(1)
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF7C3AED)
                .setBorderColor(0xFFE9D5FF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFFFFFFF)
                .setOpacity(1.0F)
                .setTransition(DocumentAnimationProperty.OPACITY, 700L)
                .setTransitionTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        opacityCard.appendText("Opacity FBO card: click fade");
        final boolean[] faded = new boolean[] { false };
        opacityCard.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                faded[0] = !faded[0];
                opacityCard.style().setOpacity(faded[0] ? 0.45F : 1.0F);
                return true;
            }
        });
        probe.append(opacityCard);

        ElementNode autoOpacityCard = document.div();
        autoOpacityCard.style()
                .setWidth(UiStyleLength.px(238))
                .setHeight(UiStyleLength.px(42))
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(88))
                .setTop(UiStyleLength.px(92))
                .setZIndex(1)
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF2563EB)
                .setBorderColor(0xFFBFDBFE)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFFFFFFF)
                .setOpacity(1.0F)
                .setAnimation("opacityFboAuto", 900L)
                .setAnimationFillMode(DocumentAnimationFillMode.NONE)
                .setAnimationTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        autoOpacityCard.appendText("Opacity FBO auto: initial fade");
        probe.append(autoOpacityCard);

        final ElementNode comboOpacityCard = document.div();
        comboOpacityCard.style()
                .setWidth(UiStyleLength.px(238))
                .setHeight(UiStyleLength.px(42))
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(88))
                .setTop(UiStyleLength.px(150))
                .setZIndex(1)
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF0F766E)
                .setBorderColor(0xFF99F6E4)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFFFFFFF)
                .setOpacity(1.0F)
                .setTransition(DocumentAnimationProperty.OPACITY, 700L)
                .setTransitionTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                .setAnimation("opacityFboAuto", 900L)
                .setAnimationFillMode(DocumentAnimationFillMode.NONE)
                .setAnimationTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        comboOpacityCard.appendText("Opacity FBO combo: 3-stop + click");
        final boolean[] comboFaded = new boolean[] { false };
        comboOpacityCard.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                comboFaded[0] = !comboFaded[0];
                comboOpacityCard.style().setOpacity(comboFaded[0] ? 0.45F : 1.0F);
                return true;
            }
        });
        probe.append(comboOpacityCard);
    }

    /**
     * 追加 layout-affecting 动画测试区，用于游戏内观察 width/height 运行值触发布局重排。
     *
     * @param document HTML-like 文档
     * @param root 文档根元素
     */
    private static void appendLayoutAnimationProbe(UiDocument document, ElementNode root,
            AnimationRuntimeDiagnostics animationRuntimeDiagnostics) {
        ElementNode probe = document.div();
        probe.style()
                .setHeight(UiStyleLength.px(364))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(12), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF60A5FA)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFDBEAFE)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        probe.appendText("Layout animation probe: click cards; transition/keyframe/fill push siblings.");
        root.append(probe);

        ElementNode coveredProperties = document.div();
        coveredProperties.style()
                .setHeight(UiStyleLength.px(14))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(4), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setTextColor(0xFF93C5FD)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        coveredProperties.appendText("Layout animation coverage: " + formatLayoutAnimationProperties());
        probe.append(coveredProperties);

        ElementNode runtimeSummaryDiagnostic = document.div();
        runtimeSummaryDiagnostic.style()
                .setHeight(UiStyleLength.px(14))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(2), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setTextColor(0x00000000)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        runtimeSummaryDiagnostic.appendText(formatRuntimeSummaryDiagnosticText(animationRuntimeDiagnostics));
        runtimeSummaryDiagnostic.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {
                context.drawText(formatRuntimeSummaryDiagnosticText(animationRuntimeDiagnostics), contentLeft,
                        contentTop, 0xFFDDD6FE, false);
            }
        });
        probe.append(runtimeSummaryDiagnostic);

        ElementNode runtimeImpactDiagnostic = document.div();
        runtimeImpactDiagnostic.style()
                .setHeight(UiStyleLength.px(14))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(2), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setTextColor(0x00000000)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        runtimeImpactDiagnostic.appendText(formatRuntimeImpactDiagnosticText(animationRuntimeDiagnostics));
        runtimeImpactDiagnostic.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {
                context.drawText(formatRuntimeImpactDiagnosticText(animationRuntimeDiagnostics), contentLeft,
                        contentTop, 0xFFC4B5FD, false);
            }
        });
        probe.append(runtimeImpactDiagnostic);

        ElementNode cacheDiagnostic = document.div();
        cacheDiagnostic.style()
                .setHeight(UiStyleLength.px(14))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(2), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setTextColor(0x00000000)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        cacheDiagnostic.appendText(formatPerformanceDiagnosticText(animationRuntimeDiagnostics));
        cacheDiagnostic.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {
                context.drawText(formatPerformanceDiagnosticText(animationRuntimeDiagnostics), contentLeft,
                        contentTop, 0xFFBAE6FD, false);
            }
        });
        probe.append(cacheDiagnostic);

        ElementNode cacheHint = document.div();
        cacheHint.style()
                .setHeight(UiStyleLength.px(14))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(2), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setTextColor(0xFF7DD3FC)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        cacheHint.appendText("Cache note: click-frame static +1 is OK; running paint/effect must not grow runtimeLayout.");
        probe.append(cacheHint);

        ElementNode row = document.div();
        row.style()
                .setHeight(UiStyleLength.px(78))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(10))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(4), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        probe.append(row);

        final ElementNode layoutCard = document.div();
        layoutCard.style()
                .setWidth(UiStyleLength.px(92))
                .setHeight(UiStyleLength.px(34))
                .setFlexShrink(0.0F)
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF2563EB)
                .setBorderColor(0xFFBFDBFE)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFEFF6FF)
                .setTransitionProperties(DocumentAnimationProperty.WIDTH, DocumentAnimationProperty.HEIGHT)
                .setTransitionDurationMillis(800L)
                .setTransitionTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        final TextNode layoutLabel = layoutCard.appendText("Layout card: small");
        final boolean[] expanded = new boolean[] { false };
        layoutCard.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                expanded[0] = !expanded[0];
                layoutCard.style()
                        .setWidth(UiStyleLength.px(expanded[0] ? 190 : 92))
                        .setHeight(UiStyleLength.px(expanded[0] ? 58 : 34));
                layoutLabel.setText(expanded[0] ? "Layout card: large" : "Layout card: small");
                return true;
            }
        });
        row.append(layoutCard);

        ElementNode sibling = document.div();
        sibling.style()
                .setFlexGrow(1.0F)
                .setHeight(UiStyleLength.px(34))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF064E3B)
                .setBorderColor(0xFF6EE7B7)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFD1FAE5)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        sibling.appendText("Sibling shifts while layout transition runs");
        row.append(sibling);

        ElementNode marginRow = document.div();
        marginRow.style()
                .setHeight(UiStyleLength.px(56))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(10))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        probe.append(marginRow);

        final ElementNode marginCard = document.div();
        marginCard.style()
                .setWidth(UiStyleLength.px(92))
                .setHeight(UiStyleLength.px(30))
                .setFlexShrink(0.0F)
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(4), UiStyleLength.px(0),
                        UiStyleLength.px(4)))
                .setPadding(UiStyleLength.px(7))
                .setBackgroundColor(0xFFD97706)
                .setBorderColor(0xFFFDE68A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFFFF7ED)
                .setTransitionProperties(DocumentAnimationProperty.MARGIN_LEFT,
                        DocumentAnimationProperty.MARGIN_RIGHT)
                .setTransitionDurationMillis(800L)
                .setTransitionTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        final TextNode marginLabel = marginCard.appendText("Margin card: tight");
        final boolean[] marginExpanded = new boolean[] { false };
        marginCard.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                marginExpanded[0] = !marginExpanded[0];
                marginCard.style().setMargin(UiStyleInsets.of(UiStyleLength.px(0),
                        UiStyleLength.px(marginExpanded[0] ? 18 : 4), UiStyleLength.px(0),
                        UiStyleLength.px(marginExpanded[0] ? 34 : 4)));
                marginLabel.setText(marginExpanded[0] ? "Margin card: wide" : "Margin card: tight");
                return true;
            }
        });
        marginRow.append(marginCard);

        ElementNode marginSibling = document.div();
        marginSibling.style()
                .setFlexGrow(1.0F)
                .setHeight(UiStyleLength.px(30))
                .setPadding(UiStyleLength.px(7))
                .setBackgroundColor(0xFF7C2D12)
                .setBorderColor(0xFFFED7AA)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFFFEDD5)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        marginSibling.appendText("Margin sibling shifts from margin");
        marginRow.append(marginSibling);

        ElementNode paddingRow = document.div();
        paddingRow.style()
                .setHeight(UiStyleLength.px(56))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(10))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        probe.append(paddingRow);

        final ElementNode paddingCard = document.div();
        paddingCard.style()
                .setWidth(UiStyleLength.px(92))
                .setHeight(UiStyleLength.px(30))
                .setFlexShrink(0.0F)
                .setPadding(UiStyleInsets.of(UiStyleLength.px(7), UiStyleLength.px(4), UiStyleLength.px(7),
                        UiStyleLength.px(4)))
                .setBackgroundColor(0xFF7C3AED)
                .setBorderColor(0xFFD8B4FE)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFF5F3FF)
                .setTransitionProperties(DocumentAnimationProperty.PADDING_LEFT,
                        DocumentAnimationProperty.PADDING_RIGHT)
                .setTransitionDurationMillis(800L)
                .setTransitionTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        final TextNode paddingLabel = paddingCard.appendText("Padding card: tight");
        final boolean[] paddingExpanded = new boolean[] { false };
        paddingCard.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                paddingExpanded[0] = !paddingExpanded[0];
                paddingCard.style().setPadding(UiStyleInsets.of(UiStyleLength.px(7),
                        UiStyleLength.px(paddingExpanded[0] ? 24 : 4), UiStyleLength.px(7),
                        UiStyleLength.px(paddingExpanded[0] ? 28 : 4)));
                paddingLabel.setText(paddingExpanded[0] ? "Padding card: wide" : "Padding card: tight");
                return true;
            }
        });
        paddingRow.append(paddingCard);

        ElementNode paddingSibling = document.div();
        paddingSibling.style()
                .setFlexGrow(1.0F)
                .setHeight(UiStyleLength.px(30))
                .setPadding(UiStyleLength.px(7))
                .setBackgroundColor(0xFF4C1D95)
                .setBorderColor(0xFFE9D5FF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFF3E8FF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        paddingSibling.appendText("Padding sibling shifts from padding");
        paddingRow.append(paddingSibling);

        ElementNode keyframeRow = document.div();
        keyframeRow.style()
                .setHeight(UiStyleLength.px(56))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(10))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        probe.append(keyframeRow);

        final ElementNode keyframeCard = document.div();
        keyframeCard.style()
                .setWidth(UiStyleLength.px(92))
                .setHeight(UiStyleLength.px(30))
                .setFlexShrink(0.0F)
                .setPadding(UiStyleLength.px(7))
                .setBackgroundColor(0xFF0F766E)
                .setBorderColor(0xFF99F6E4)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFE6FFFA)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        final TextNode keyframeLabel = keyframeCard.appendText("Keyframe card: idle");
        final boolean[] keyframeStarted = new boolean[] { false };
        keyframeCard.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                keyframeStarted[0] = !keyframeStarted[0];
                if (keyframeStarted[0]) {
                    keyframeCard.style()
                            .setAnimation("layoutFillProbe", 1000L)
                            .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS)
                            .setAnimationTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT);
                    keyframeLabel.setText("Keyframe card: clear fill");
                } else {
                    keyframeCard.style().clearAnimationName();
                    keyframeLabel.setText("Keyframe card: idle");
                }
                return true;
            }
        });
        keyframeRow.append(keyframeCard);

        ElementNode keyframeSibling = document.div();
        keyframeSibling.style()
                .setFlexGrow(1.0F)
                .setHeight(UiStyleLength.px(30))
                .setPadding(UiStyleLength.px(7))
                .setBackgroundColor(0xFF134E4A)
                .setBorderColor(0xFF5EEAD4)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFCCFBF1)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        keyframeSibling.appendText("Keyframe sibling holds forwards fill");
        keyframeRow.append(keyframeSibling);
    }

    private static void appendAbsoluteStretchAndInlineProbe(UiDocument document, ElementNode root) {
        ElementNode probe = document.div();
        probe.style()
                .setHeight(UiStyleLength.px(226))
                .setMargin(UiStyleLength.px(16))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF102A43)
                .setBorderColor(0xFF38BDF8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFE0F2FE)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        probe.appendText("Absolute stretch + inline span probe");
        root.append(probe);

        ElementNode stretchStage = document.div();
        stretchStage.style()
                .setHeight(UiStyleLength.px(42))
                .setPosition(UiPosition.RELATIVE)
                .setMargin(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(10))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        probe.append(stretchStage);

        ElementNode stretchedFill = document.div();
        stretchedFill.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(14))
                .setRight(UiStyleLength.px(22))
                .setTop(UiStyleLength.px(8))
                .setBottom(UiStyleLength.px(8))
                .setPadding(UiStyleLength.px(5))
                .setBackgroundColor(0xFF0EA5E9)
                .setBorderColor(0xFFBAE6FD)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFFFFFFF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        stretchedFill.appendText("ABS stretch fill: left+right / top+bottom");
        stretchStage.append(stretchedFill);

        ElementNode inlineLine = document.div();
        inlineLine.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setWidth(UiStyleLength.px(240))
                .setTextColor(0xFFE0F2FE);
        inlineLine.appendText("Inline split probe:");
        final ElementNode amberSpan = document.span();
        amberSpan.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(2), UiStyleLength.px(2), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(2), UiStyleLength.px(4), UiStyleLength.px(2),
                        UiStyleLength.px(4)))
                .setBackgroundColor(0x334F46E5)
                .setBorderColor(0xFFFFD166)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(5))
                .setTextColor(0xFFFFD166);
        final TextNode amberSpanText = amberSpan.appendText("amber span hit: 0");
        final int[] amberSpanClickCount = new int[] { 0 };
        amberSpan.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                amberSpanClickCount[0]++;
                amberSpanText.setText("amber span hit: " + amberSpanClickCount[0]);
                amberSpan.style().setBackgroundColor(amberSpanClickCount[0] % 2 == 0 ? 0x334F46E5 : 0x5538BDF8);
                return true;
            }
        });
        inlineLine.append(amberSpan);
        inlineLine.appendText(" split corners only outside.");
        probe.append(inlineLine);

        ElementNode verticalAlignLine = document.div();
        verticalAlignLine.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(10), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setTextColor(0xFFE0F2FE);
        verticalAlignLine.appendText("Vertical-align probe: rail sets row height; top/mid/bot shift inside it.");

        ElementNode verticalAlignPills = document.div();
        verticalAlignPills.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(4), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setWidth(UiStyleLength.px(360))
                .setTextColor(0xFFE0F2FE);
        verticalAlignPills.appendText("align:");
        appendVerticalAlignPill(document, verticalAlignPills, "rail", UiVerticalAlign.BASELINE,
                0x334F46E5, 0xFF38BDF8, UiStyleInsets.of(UiStyleLength.px(7), UiStyleLength.px(5),
                        UiStyleLength.px(7), UiStyleLength.px(5)));
        appendVerticalAlignPill(document, verticalAlignPills, "top", UiVerticalAlign.TOP, 0x5538BDF8, 0xFF93C5FD,
                UiStyleInsets.of(UiStyleLength.px(1), UiStyleLength.px(5), UiStyleLength.px(1),
                        UiStyleLength.px(5)));
        appendVerticalAlignPill(document, verticalAlignPills, "mid", UiVerticalAlign.MIDDLE, 0x55F59E0B,
                0xFFFDE68A, UiStyleInsets.of(UiStyleLength.px(1), UiStyleLength.px(5), UiStyleLength.px(1),
                        UiStyleLength.px(5)));
        appendVerticalAlignPill(document, verticalAlignPills, "bot", UiVerticalAlign.BOTTOM, 0x5522C55E,
                0xFF86EFAC, UiStyleInsets.of(UiStyleLength.px(1), UiStyleLength.px(5), UiStyleLength.px(1),
                        UiStyleLength.px(5)));
        verticalAlignLine.append(verticalAlignPills);
        probe.append(verticalAlignLine);
    }

    private static void appendVerticalAlignPill(UiDocument document, ElementNode line, String label,
            UiVerticalAlign verticalAlign, int backgroundColor, int borderColor, UiStyleInsets padding) {
        ElementNode pill = document.span();
        pill.style()
                .setVerticalAlign(verticalAlign)
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(3), UiStyleLength.px(0),
                        UiStyleLength.px(3)))
                .setPadding(padding)
                .setBackgroundColor(backgroundColor)
                .setBorderColor(borderColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(5))
                .setTextColor(0xFFFFFFFF);
        pill.appendText(label);
        line.append(pill);
    }

    private static void appendGroupOpacityProbe(UiDocument document, ElementNode root) {
        ElementNode groupOpacityProbe = document.div();
        groupOpacityProbe.style()
                .setHeight(UiStyleLength.px(108))
                .setMargin(UiStyleLength.px(16))
                .setPadding(UiStyleLength.px(10))
                .setPosition(UiPosition.RELATIVE)
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF60A5FA)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFEFF6FF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        groupOpacityProbe.appendText("Group opacity probe: overlap should stay flat blue, not dark purple");
        root.append(groupOpacityProbe);

        ElementNode opacitySampleStage = document.div();
        opacitySampleStage.style()
                .setWidth(UiStyleLength.px(300))
                .setHeight(UiStyleLength.px(68))
                .setPosition(UiPosition.RELATIVE)
                .setMargin(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(10))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        groupOpacityProbe.append(opacitySampleStage);

        ElementNode opacityBackStripeA = document.div();
        opacityBackStripeA.style()
                .setWidth(UiStyleLength.px(280))
                .setHeight(UiStyleLength.px(16))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(12))
                .setLeft(UiStyleLength.px(10))
                .setBackgroundColor(0xFFFFD166)
                .setBorderRadius(UiStyleLength.px(999));
        opacitySampleStage.append(opacityBackStripeA);

        ElementNode opacityBackStripeB = document.div();
        opacityBackStripeB.style()
                .setWidth(UiStyleLength.px(250))
                .setHeight(UiStyleLength.px(16))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(38))
                .setLeft(UiStyleLength.px(28))
                .setBackgroundColor(0xFF38BDF8)
                .setBorderRadius(UiStyleLength.px(999));
        opacitySampleStage.append(opacityBackStripeB);

        ElementNode opacityGroup = document.div();
        opacityGroup.style()
                .setWidth(UiStyleLength.px(156))
                .setHeight(UiStyleLength.px(44))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(12))
                .setLeft(UiStyleLength.px(28))
                .setOpacity(0.55F)
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE);
        opacitySampleStage.append(opacityGroup);

        ElementNode redLayer = document.div();
        redLayer.style()
                .setWidth(UiStyleLength.px(86))
                .setHeight(UiStyleLength.px(34))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(5))
                .setLeft(UiStyleLength.px(0))
                .setBackgroundColor(0xFFFF4B4B)
                .setBorderRadius(UiStyleLength.px(8));
        opacityGroup.append(redLayer);

        ElementNode blueLayer = document.div();
        blueLayer.style()
                .setWidth(UiStyleLength.px(86))
                .setHeight(UiStyleLength.px(34))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(5))
                .setLeft(UiStyleLength.px(48))
                .setBackgroundColor(0xFF3B82F6)
                .setBorderRadius(UiStyleLength.px(8));
        opacityGroup.append(blueLayer);
    }

    private static void appendStackingContextProbe(UiDocument document, ElementNode root) {
        ElementNode stackingProbe = document.div();
        stackingProbe.style()
                .setHeight(UiStyleLength.px(128))
                .setMargin(UiStyleLength.px(16))
                .setPadding(UiStyleLength.px(10))
                .setPosition(UiPosition.RELATIVE)
                .setBackgroundColor(0xFF0B1224)
                .setBorderColor(0xFFA78BFA)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFEDE9FE)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        stackingProbe.appendText("Stacking context probe: blue cover must stay above red z-99 child");
        root.append(stackingProbe);

        ElementNode stackingStage = document.div();
        stackingStage.style()
                .setWidth(UiStyleLength.px(360))
                .setHeight(UiStyleLength.px(82))
                .setPosition(UiPosition.RELATIVE)
                .setMargin(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF374151)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(10))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        stackingProbe.append(stackingStage);

        ElementNode isolatedShell = document.div();
        isolatedShell.style()
                .setWidth(UiStyleLength.px(220))
                .setHeight(UiStyleLength.px(56))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(20))
                .setLeft(UiStyleLength.px(18))
                .setZIndex(0)
                .setOpacity(0.98F)
                .setBackgroundColor(0xEE581C87)
                .setBorderColor(0xFFC084FC)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(10))
                .setTextColor(0xFFF5D0FE)
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE);
        isolatedShell.appendText("isolated z=0 shell");
        stackingStage.append(isolatedShell);

        ElementNode redHighChild = document.div();
        redHighChild.style()
                .setWidth(UiStyleLength.px(150))
                .setHeight(UiStyleLength.px(32))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(18))
                .setLeft(UiStyleLength.px(48))
                .setZIndex(99)
                .setBackgroundColor(0xFFFF2D55)
                .setBorderColor(0xFFFFC2CC)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(8))
                .setTextColor(0xFFFFFFFF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        redHighChild.appendText("red child z=99");
        isolatedShell.append(redHighChild);

        ElementNode blueCover = document.div();
        blueCover.style()
                .setWidth(UiStyleLength.px(190))
                .setHeight(UiStyleLength.px(38))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(42))
                .setLeft(UiStyleLength.px(96))
                .setZIndex(1)
                .setBackgroundColor(0xFF2563EB)
                .setBorderColor(0xFFBFDBFE)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(8))
                .setTextColor(0xFFEFF6FF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        blueCover.appendText("blue sibling z=1 should win");
        stackingStage.append(blueCover);
    }

    /**
     * 格式化当前 layout 动画覆盖属性清单。
     *
     * @return layout 动画覆盖属性清单
     */
    private static String formatLayoutAnimationProperties() {
        StringBuilder builder = new StringBuilder();
        for (DocumentAnimationProperty property : DocumentAnimationProperty.values()) {
            if (!property.isLayoutAffecting()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(property.name());
        }
        return builder.toString();
    }

    /**
     * 格式化实时动画来源汇总诊断文本。
     *
     * @param animationRuntimeDiagnostics 动画运行诊断源
     * @return 展示文本
     */
    private static String formatRuntimeSummaryDiagnosticText(AnimationRuntimeDiagnostics animationRuntimeDiagnostics) {
        DocumentAnimationTimeline.DiagnosticsSnapshot snapshot = getRuntimeDiagnosticsSnapshot(
                animationRuntimeDiagnostics);
        return "Animation runtime: active=" + snapshot.getActiveAnimationCount()
                + " transition=" + snapshot.getTotalTransitionCount()
                + " keyframe=" + snapshot.getTotalKeyframeCount()
                + " fill=" + snapshot.getTotalForwardsFillCount();
    }

    /**
     * 格式化实时动画影响范围诊断文本。
     *
     * @param animationRuntimeDiagnostics 动画运行诊断源
     * @return 展示文本
     */
    private static String formatRuntimeImpactDiagnosticText(AnimationRuntimeDiagnostics animationRuntimeDiagnostics) {
        DocumentAnimationTimeline.DiagnosticsSnapshot snapshot = getRuntimeDiagnosticsSnapshot(
                animationRuntimeDiagnostics);
        return "Runtime by impact: paint " + formatImpactDiagnostics(snapshot, DocumentAnimationImpact.PAINT)
                + " | effect " + formatImpactDiagnostics(snapshot, DocumentAnimationImpact.EFFECT)
                + " | layout " + formatImpactDiagnostics(snapshot, DocumentAnimationImpact.LAYOUT)
                + " active=" + snapshot.hasRuntimeValue(DocumentAnimationImpact.LAYOUT);
    }

    private static String formatImpactDiagnostics(DocumentAnimationTimeline.DiagnosticsSnapshot snapshot,
            DocumentAnimationImpact impact) {
        return "t=" + snapshot.getTransitionCount(impact)
                + " k=" + snapshot.getKeyframeCount(impact)
                + " f=" + snapshot.getForwardsFillCount(impact);
    }

    private static DocumentAnimationTimeline.DiagnosticsSnapshot getRuntimeDiagnosticsSnapshot(
            AnimationRuntimeDiagnostics animationRuntimeDiagnostics) {
        return animationRuntimeDiagnostics == null ? DocumentAnimationTimeline.DiagnosticsSnapshot.empty()
                : animationRuntimeDiagnostics.getSnapshot();
    }

    /**
     * 格式化缓存与布局重建诊断文本。
     *
     * @param animationRuntimeDiagnostics 动画运行诊断源
     * @return 展示文本
     */
    private static String formatPerformanceDiagnosticText(AnimationRuntimeDiagnostics animationRuntimeDiagnostics) {
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = getPerformanceDiagnosticsSnapshot(
                animationRuntimeDiagnostics);
        return "Cache runtime: paintGen=" + snapshot.getPaintCacheGeneration()
                + " staticLayout=" + snapshot.getStaticLayoutGeneration()
                + " runtimeLayout=" + snapshot.getRuntimeLayoutGeneration()
                + " textEpoch=" + snapshot.getTextMeasureEpoch();
    }

    private static HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot getPerformanceDiagnosticsSnapshot(
            AnimationRuntimeDiagnostics animationRuntimeDiagnostics) {
        return animationRuntimeDiagnostics == null ? HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot.empty()
                : animationRuntimeDiagnostics.getPerformanceSnapshot();
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
     * Smoke 页面动画运行态诊断源。
     */
    private interface AnimationRuntimeDiagnostics {

        /**
         * 返回当前动画运行态诊断快照。
         *
         * @return 动画运行态诊断快照
         */
        DocumentAnimationTimeline.DiagnosticsSnapshot getSnapshot();

        /**
         * 返回当前缓存与布局重建诊断快照。
         *
         * @return 缓存与布局重建诊断快照
         */
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot getPerformanceSnapshot();
    }
}
