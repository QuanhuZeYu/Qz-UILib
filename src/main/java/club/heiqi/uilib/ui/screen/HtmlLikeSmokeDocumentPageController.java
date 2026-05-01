package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimingFunction;
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
import club.heiqi.uilib.ui.style.UiPosition;
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
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xF00B1020)
                .setBorderColor(0xFF7C3AED)
                .setBorderWidth(UiStyleLength.px(1))
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
                .setWidth(UiStyleLength.px(178))
                .setHeight(UiStyleLength.px(40))
                .setPosition(UiPosition.FIXED)
                .setBottom(UiStyleLength.px(12))
                .setRight(UiStyleLength.px(14))
                .setZIndex(20)
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(0xEE0F766E)
                .setBorderColor(0xFF99F6E4)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFE6FFFA)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        fixedViewportProbe.appendText("FIXED viewport stays here");
        root.append(fixedViewportProbe);

        appendControlsSection(document, root);

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
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFFFFFFF)
                .setBackdropBlurRadius(UiStyleLength.px(14))
                .setBackdropSaturation(1.4F)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        glassCard.appendText("Backdrop glass overlap: blur 14px / saturate 140%");
        backdropStage.append(glassCard);
        appendAbsoluteStretchAndInlineProbe(document, root);
        appendGroupOpacityProbe(document, root);
        appendStackingContextProbe(document, root);
        return document;
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
                .setHeight(UiStyleLength.px(92))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(14), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF101827)
                .setBorderColor(0xFF22D3EE)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFE0F2FE)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        controlsSection.appendText("Controls probe: click, input, Tab, button, toggle");
        root.append(controlsSection);

        ElementNode controlsRow = document.div();
        controlsRow.style()
                .setHeight(UiStyleLength.px(46))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(10))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)));
        controlsSection.append(controlsRow);

        final ElementNode firstPill = document.div();
        firstPill.style()
                .setFlexGrow(1.0F)
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF38A169)
                .setOpacity(1.0F)
                .setTransitionProperties(DocumentAnimationProperty.BACKGROUND_COLOR, DocumentAnimationProperty.OPACITY,
                        DocumentAnimationProperty.BORDER_RADIUS)
                .setTransitionDurationMillis(450L)
                .setTransitionTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFFFFFFF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        final TextNode clickText = firstPill.appendText("Click target: 0 / fade+morph");
        final int[] clickCount = new int[] { 0 };
        firstPill.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickCount[0]++;
                clickText.setText("Click target: " + clickCount[0] + " / fade+morph");
                firstPill.style().setBackgroundColor(clickCount[0] % 2 == 0 ? 0xFF38A169 : 0xFF3182CE);
                firstPill.style().setOpacity(clickCount[0] % 2 == 0 ? 1.0F : 0.55F);
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
        toggleControl.getElement().style().setFlexGrow(0.6F);
        controlsRow.append(toggleControl.getElement());
    }

    private static void appendAbsoluteStretchAndInlineProbe(UiDocument document, ElementNode root) {
        ElementNode probe = document.div();
        probe.style()
                .setHeight(UiStyleLength.px(172))
                .setMargin(UiStyleLength.px(16))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF102A43)
                .setBorderColor(0xFF38BDF8)
                .setBorderWidth(UiStyleLength.px(1))
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
        inlineLine.appendText("Inline split: text ");
        final ElementNode amberSpan = document.span();
        amberSpan.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(2), UiStyleLength.px(0),
                        UiStyleLength.px(2)))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(2), UiStyleLength.px(4), UiStyleLength.px(2),
                        UiStyleLength.px(4)))
                .setBackgroundColor(0x334F46E5)
                .setBorderColor(0xFFFFD166)
                .setBorderWidth(UiStyleLength.px(1))
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
        inlineLine.appendText(" span should split; only outer split corners stay rounded.");
        probe.append(inlineLine);
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
                .setBorderRadius(UiStyleLength.px(8))
                .setTextColor(0xFFEFF6FF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        blueCover.appendText("blue sibling z=1 should win");
        stackingStage.append(blueCover);
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
