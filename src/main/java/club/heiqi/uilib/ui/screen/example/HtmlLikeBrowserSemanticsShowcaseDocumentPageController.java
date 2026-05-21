package club.heiqi.uilib.ui.screen.example;

import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;

import java.util.Objects;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentCustomEvent;
import club.heiqi.uilib.ui.dom.DocumentCustomEventHandler;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementScrollEvent;
import club.heiqi.uilib.ui.dom.DocumentElementScrollHandler;
import club.heiqi.uilib.ui.dom.DocumentFragmentNode;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationEvent;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationHandler;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.style.UiStyleProperty;
import club.heiqi.uilib.ui.style.props.UiAlignSelf;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.values.UiBackgroundImage;
import club.heiqi.uilib.ui.style.values.UiBorderColors;
import club.heiqi.uilib.ui.style.props.UiBorderCollapse;
import club.heiqi.uilib.ui.style.values.UiBorderRadius;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.values.UiBoxShadow;
import club.heiqi.uilib.ui.style.props.UiBoxSizing;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiObjectFit;
import club.heiqi.uilib.ui.style.values.UiOutline;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiOverflowWrap;
import club.heiqi.uilib.ui.style.props.UiPointerEvents;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiPseudoElementContent;
import club.heiqi.uilib.ui.style.values.UiScrollbarColor;
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleKeyword;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.cascade.UiStyleSheet;
import club.heiqi.uilib.ui.style.cascade.UiStyleVariables;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiListStyleType;
import club.heiqi.uilib.ui.style.props.UiScrollbarWidth;
import club.heiqi.uilib.ui.style.values.UiTextShadow;
import club.heiqi.uilib.ui.style.props.UiTextAlign;
import club.heiqi.uilib.ui.style.props.UiTextDecoration;
import club.heiqi.uilib.ui.style.props.UiTextOverflow;
import club.heiqi.uilib.ui.style.props.UiTextTransform;
import club.heiqi.uilib.ui.style.values.UiTransform;
import club.heiqi.uilib.ui.style.props.UiVisibility;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.style.props.UiWordBreak;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * 浏览器语义新功能展示页控制器。
 *
 * <p>展示当前已接入运行时的浏览器语义能力：样式表/选择器、事件传播、DOM 查询、
 * pointer-events、文本装饰、宽高比、替换元素适配与变量容器。</p>
 */
public final class HtmlLikeBrowserSemanticsShowcaseDocumentPageController extends DocumentPageController {

    private final DocumentPageAuthoringSurface documentPage;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;
    private final UiDocument document;
    private ElementNode eventLogArea;
    private TextNode queryResultText;
    private TextNode themeStatusText;
    private ElementNode varBox;
    private TextNode selectorStateText;
    private TextNode domMutationStateText;
    private TextNode semanticStateText;
    private TextNode textLayoutStateText;
    private TextNode flexStateText;
    private TextNode scrollStateText;
    private TextNode mediaStateText;
    private boolean darkTheme = true;
    private int eventLogCount;

    /**
     * 创建浏览器语义展示页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param documentPage 文档页面壳
     */
    public HtmlLikeBrowserSemanticsShowcaseDocumentPageController(DocumentUiScope documentUi,
            DocumentPageAuthoringSurface documentPage) {
        this(Objects.requireNonNull(documentUi, "documentUi"), documentPage, documentUi.getTextMeasureService());
    }

    /**
     * 使用指定文本测量服务创建浏览器语义展示页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param documentPage 文档页面壳
     * @param textMeasureService HTML-like 文本测量服务
     */
    public HtmlLikeBrowserSemanticsShowcaseDocumentPageController(DocumentUiScope documentUi,
            DocumentPageAuthoringSurface documentPage, TextMeasureService textMeasureService) {
        Objects.requireNonNull(documentUi, "documentUi");
        this.documentPage = Objects.requireNonNull(documentPage, "documentPage");
        this.document = UiDocument.create();
        document.setDefaultTextContentMode(documentUi.getDefaultTextContentMode());
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 900, 600,
                Objects.requireNonNull(textMeasureService, "textMeasureService"));
        this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));
        buildShowcaseDocument();
    }

    @Override
    public void configureDocumentPage() {
        documentPage.setContentWidthRange(780, 1200)
                .setMinContentHeight(640)
                .setViewportFillRatio(0.94F, 0.92F);
    }

    @Override
    public void buildDocument() {
        documentPage.addBlock(htmlLikeDocumentWidget);
    }

    /**
     * 构建展示文档内容。
     */
    private void buildShowcaseDocument() {
        ElementNode root = document.getRootElement();
        root.style()
                .setPadding(UiStyleLength.px(20))
                .setBackgroundColor(0xF00A1020)
                .setBorderColor(0xFF4A6FA5)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setTextColor(0xFFE8EEFF);

        // 设置样式表
        setupStyleSheet();
        // 设置 CSS Variables
        setupStyleVariables();

        // 标题
        appendTitle(root);
        // 各功能展示区
        appendStyleSheetDemo(root);
        appendEventPropagationDemo(root);
        appendDomQueryDemo(root);
        appendCursorDemo(root);
        appendVisualPropertiesDemo(root);
        appendBorderControlDemo(root);
        appendCssVariablesDemo(root);
        appendTextTypographyDemo(root);
        appendAdvancedSelectorDemo(root);
        appendDomMutationDemo(root);
        appendSemanticElementsDemo(root);
        appendTextLayoutControlsDemo(root);
        appendLayoutAndFlexDetailsDemo(root);
        appendScrollAndVisibilityDemo(root);
        appendVisualMediaDemo(root);
    }

    /**
     * 设置文档级样式表，展示选择器和级联能力。
     */
    private void setupStyleSheet() {
        UiStyleSheet sheet = UiStyleSheet.create()
                .addRule(".section", new UiStyleDeclaration()
                        .setMargin(UiStyleInsets.of(UiStyleLength.px(16), UiStyleLength.px(0),
                                UiStyleLength.px(0), UiStyleLength.px(0)))
                        .setPadding(UiStyleLength.px(14))
                        .setBackgroundColor(0xFF121E30)
                        .setBorderColor(0xFF2E4C7F)
                        .setBorderWidth(UiStyleLength.px(1))
                        .setBorderStyle(UiBorderStyle.SOLID)
                        .setBorderRadius(UiStyleLength.px(12)))
                .addRule(".section-title", new UiStyleDeclaration()
                        .setTextColor(0xFF7EB8FF)
                        .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(0),
                                UiStyleLength.px(8), UiStyleLength.px(0))))
                .addRule(".demo-row", new UiStyleDeclaration()
                        .setDisplay(UiDisplay.FLEX)
                        .setFlexDirection(UiFlexDirection.ROW)
                        .setFlexWrap(UiFlexWrap.WRAP)
                        .setColumnGap(UiStyleLength.px(10))
                        .setRowGap(UiStyleLength.px(10))
                        .setAlignItems(UiAlignItems.CENTER))
                .addRule(".demo-box", new UiStyleDeclaration()
                        .setPadding(UiStyleLength.px(10))
                        .setBackgroundColor(0xFF1A2A44)
                        .setBorderColor(0xFF3B5998)
                        .setBorderWidth(UiStyleLength.px(1))
                        .setBorderStyle(UiBorderStyle.SOLID)
                        .setBorderRadius(UiStyleLength.px(8))
                        .setTextColor(0xFFCCDDFF))
                .addRule("div", new UiStyleDeclaration()
                        .setTextColor(0xFFD8E5FF))
                .addRule(".highlight", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF2A3F5F)
                        .setBorderColor(0xFF5B8DEF)
                        .setTextColor(0xFFFFFFFF))
                .addRule("#special-box", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF334B76)
                        .setBorderColor(0xFFFFD166)
                        .setTextColor(0xFFFFF3BF))
                .addRule(".clickable", new UiStyleDeclaration()
                        .setCursor(UiCursor.POINTER))
                .addRule(".log-area", new UiStyleDeclaration()
                        .setPadding(UiStyleLength.px(8))
                        .setBackgroundColor(0xFF0D1520)
                        .setBorderColor(0xFF1E3050)
                        .setBorderWidth(UiStyleLength.px(1))
                        .setBorderStyle(UiBorderStyle.SOLID)
                        .setBorderRadius(UiStyleLength.px(6))
                        .setTextColor(0xFF88AACC))
                .addRule(".selector-stage > .child-card", new UiStyleDeclaration()
                        .setBorderColor(0xFF38BDF8)
                        .setBackgroundColor(0xFF123047))
                .addRule(".selector-stage .descendant-chip", new UiStyleDeclaration()
                        .setTextColor(0xFFBAE6FD)
                        .setBackgroundColor(0xFF075985))
                .addRule(".pseudo-list > div:first-child", new UiStyleDeclaration()
                        .setBorderColor(0xFF22C55E)
                        .setTextColor(0xFFBBF7D0))
                .addRule(".pseudo-list > div:nth-child(2)", new UiStyleDeclaration()
                        .setBorderColor(0xFFF59E0B)
                        .setTextColor(0xFFFFEDD5))
                .addRule(".pseudo-list > div:last-child", new UiStyleDeclaration()
                        .setBorderColor(0xFFEC4899)
                        .setTextColor(0xFFFBCFE8))
                .addRule("button:disabled", new UiStyleDeclaration()
                        .setTextColor(0xFF94A3B8)
                        .setBackgroundColor(0xFF334155))
                .addRule(".pseudo-card::before", new UiStyleDeclaration()
                        .setContent(UiPseudoElementContent.text("::before "))
                        .setTextColor(0xFF67E8F9)
                        .setFontWeight(UiFontWeight.BOLD))
                .addRule(".pseudo-card::after", new UiStyleDeclaration()
                        .setContent(UiPseudoElementContent.text(" ::after"))
                        .setTextColor(0xFFF9A8D4)
                        .setFontStyle(UiFontStyle.ITALIC))
                .addRule(".important-low", new UiStyleDeclaration()
                        .setTextColor(0xFF64748B))
                .addRule(".important-low", new UiStyleDeclaration()
                        .setTextColor(0xFFFFD166)
                        .setImportant(UiStyleProperty.TEXT_COLOR))
                .addRule(".keyword-parent", new UiStyleDeclaration()
                        .setTextColor(0xFF86EFAC))
                .addRule(".keyword-child", new UiStyleDeclaration()
                        .setKeyword(UiStyleProperty.TEXT_COLOR, UiStyleKeyword.INHERIT));
        document.addStyleSheet(sheet);
    }

    /**
     * 设置 CSS Variables（主题变量）。
     */
    private void setupStyleVariables() {
        UiStyleVariables vars = UiStyleVariables.create()
                .setColor("--primary", 0xFF4488FF)
                .setColor("--primary-hover", 0xFF66AAFF)
                .setColor("--bg-surface", 0xFF1A2A44)
                .setColor("--bg-elevated", 0xFF243B5C)
                .setColor("--text-primary", 0xFFE8EEFF)
                .setColor("--text-secondary", 0xFF88AACC)
                .setColor("--border-default", 0xFF2E4C7F)
                .setLength("--spacing-sm", UiStyleLength.px(6))
                .setLength("--spacing-md", UiStyleLength.px(12))
                .setLength("--radius-sm", UiStyleLength.px(6))
                .setLength("--radius-md", UiStyleLength.px(12));
        document.setStyleVariables(vars);
    }

    // ========== 标题区 ==========

    private void appendTitle(ElementNode root) {
        ElementNode title = document.div();
        title.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6))
                .setPadding(UiStyleLength.px(16))
                .setBackgroundColor(0xFF162238)
                .setBorderRadius(UiStyleLength.px(14))
                .setBorderColor(0xFF4A7ADB)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID);
        ElementNode heading = document.div();
        heading.appendText("浏览器语义新功能展示");
        title.append(heading);
        ElementNode summary = document.div();
        summary.style().setTextColor(0xFFAFC7F5);
        summary.appendText("展示已接入运行时的 CSS 选择器、事件传播、DOM 查询、cursor、pointer-events、文本装饰、box-shadow、outline、分边 border、分角圆角、宽高比、object-fit、文本排版、DOM 操作、语义元素、滚动条、background-image、transform 与变量容器能力。");
        title.append(summary);
        root.append(title);
    }

    // ========== 样式表 + 选择器展示 ==========

    private void appendStyleSheetDemo(ElementNode root) {
        ElementNode section = createSection(root, "1. CSS 选择器 + 样式表级联");

        ElementNode desc = document.div();
        desc.appendText("以下元素通过 className 和样式表规则获得样式（非 inline style）：");
        section.append(desc);

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        // 普通 demo-box（通过 .demo-box 规则获得样式）
        ElementNode box1 = document.div();
        box1.setClassName("demo-box");
        box1.appendText(".demo-box");
        row.append(box1);

        // highlight 叠加（.demo-box.highlight 两个 class 同时匹配）
        ElementNode box2 = document.div();
        box2.setClassName("demo-box highlight");
        box2.appendText(".demo-box.highlight");
        row.append(box2);

        // 带 id 的元素
        ElementNode box3 = document.div();
        box3.setClassName("demo-box");
        box3.setId("special-box");
        box3.appendText("#special-box");
        row.append(box3);

        // 展示特异性：id > class > tag，且 inline style 覆盖样式表
        ElementNode note = document.div();
        note.setClassName("demo-box");
        note.style().setTextColor(0xFFFFB4A2);
        note.appendText("级联优先级：inline 文本色 > #id > .class > tag");
        section.append(note);
    }

    // ========== 事件传播展示 ==========

    private void appendEventPropagationDemo(ElementNode root) {
        ElementNode section = createSection(root, "2. 事件传播模型 (capture / bubble / stopPropagation)");

        ElementNode desc = document.div();
        desc.appendText("点击内层元素，观察事件在捕获和冒泡阶段的传播路径：");
        section.append(desc);

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        // ---- 捕获阶段示例 ----
        ElementNode captureOuter = document.div();
        captureOuter.setClassName("demo-box");
        captureOuter.setId("event-capture-outer");
        captureOuter.appendText("外层（捕获）");
        captureOuter.setCaptureClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                updateEventLog("捕获阶段: 外层拦截 | phase=" + event.getEventPhase());
                return false; // 不消费，继续向下传播
            }
        });
        row.append(captureOuter);

        ElementNode captureInner = document.div();
        captureInner.setClassName("demo-box highlight clickable");
        captureInner.appendText("点击我（观察捕获）");
        captureInner.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                updateEventLog("目标/冒泡阶段: 内层收到 | phase=" + event.getEventPhase());
                return false; // 不消费，继续冒泡
            }
        });
        captureOuter.append(captureInner);

        // ---- 冒泡阶段示例 ----
        ElementNode outer = document.div();
        outer.setClassName("demo-box");
        outer.setId("event-outer");
        outer.appendText("外层（冒泡）");
        outer.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                updateEventLog("冒泡到外层 | phase=" + event.getEventPhase());
                return false; // 不消费
            }
        });
        row.append(outer);

        ElementNode inner = document.div();
        inner.setClassName("demo-box highlight clickable");
        inner.appendText("点击我（冒泡）");
        inner.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                updateEventLog("内层点击 | phase=" + event.getEventPhase());
                return false; // 不消费，事件继续冒泡到外层
            }
        });
        outer.append(inner);

        // ---- stopPropagation 示例 ----
        ElementNode outer2 = document.div();
        outer2.setClassName("demo-box");
        outer2.setId("event-outer2");
        outer2.appendText("外层（不会收到）");
        outer2.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                updateEventLog("外层2收到事件（不应出现）");
                return false;
            }
        });
        row.append(outer2);

        ElementNode inner2 = document.div();
        inner2.setClassName("demo-box highlight clickable");
        inner2.appendText("点击我（stopPropagation）");
        inner2.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                event.stopPropagation();
                updateEventLog("内层点击 + stopPropagation | 外层不会收到");
                return false; // 返回值无关紧要，stopPropagation 已阻止传播
            }
        });
        outer2.append(inner2);

        // 事件日志区
        ElementNode logArea = document.div();
        logArea.setClassName("log-area");
        logArea.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(3));
        ElementNode initialLogLine = document.div();
        initialLogLine.appendText("（点击上方元素查看事件日志）");
        logArea.append(initialLogLine);
        eventLogArea = logArea;
        section.append(logArea);
    }

    // ========== DOM 查询展示 ==========

    private void appendDomQueryDemo(ElementNode root) {
        ElementNode section = createSection(root, "3. DOM 查询 (getElementById / querySelector)");

        ElementNode desc = document.div();
        desc.appendText("点击按钮执行 DOM 查询，结果显示在下方：");
        section.append(desc);

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        // getElementById 按钮
        ElementNode btn1 = document.button();
        btn1.setClassName("demo-box highlight clickable");
        btn1.appendText("getElementById('special-box')");
        btn1.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                ElementNode found = document.getElementById("special-box");
                updateQueryResult("getElementById('special-box') => " +
                        (found != null ? "找到: tag=" + found.getTagName() + ", class=" + found.getClassName() : "null"));
                return true;
            }
        });
        row.append(btn1);

        // querySelector 按钮
        ElementNode btn2 = document.button();
        btn2.setClassName("demo-box highlight clickable");
        btn2.appendText("querySelector('.highlight')");
        btn2.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                ElementNode found = document.querySelector(".highlight");
                updateQueryResult("querySelector('.highlight') => " +
                        (found != null ? "找到: tag=" + found.getTagName() + ", id=" + found.getId() : "null"));
                return true;
            }
        });
        row.append(btn2);

        // querySelectorAll 按钮
        ElementNode btn3 = document.button();
        btn3.setClassName("demo-box highlight clickable");
        btn3.appendText("querySelectorAll('.demo-box')");
        btn3.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                int count = document.querySelectorAll(".demo-box").size();
                updateQueryResult("querySelectorAll('.demo-box') => 找到 " + count + " 个元素");
                return true;
            }
        });
        row.append(btn3);

        // 查询结果区
        ElementNode logArea = document.div();
        logArea.setClassName("log-area");
        queryResultText = logArea.appendText("（点击上方按钮查看查询结果）");
        section.append(logArea);
    }

    // ========== cursor 展示 ==========

    private void appendCursorDemo(ElementNode root) {
        ElementNode section = createSection(root, "4. cursor 与系统光标");

        ElementNode desc = document.div();
        desc.appendText("把鼠标移到下列卡片上，观察 Minecraft/LWJGL3ify 宿主的真实系统光标变化；移出卡片后应恢复默认箭头。重叠区域会采用最上层命中元素的 cursor。");
        section.append(desc);

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        row.append(createCursorProbe("pointer", UiCursor.POINTER, 0xFF24435F, 0xFF66CCFF));
        row.append(createCursorProbe("text", UiCursor.TEXT, 0xFF243B2A, 0xFF6EE7B7));
        row.append(createCursorProbe("move", UiCursor.MOVE, 0xFF3B2A24, 0xFFF59E0B));
        row.append(createCursorProbe("not-allowed", UiCursor.NOT_ALLOWED, 0xFF40212A, 0xFFF87171));

        ElementNode overlapCard = document.div();
        overlapCard.setClassName("demo-box");
        overlapCard.style()
                .setWidth(UiStyleLength.px(276))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8));
        overlapCard.appendText("重叠命中验证：左下蓝色单独区域应显示 pointer，中央重叠区应显示 text");

        ElementNode overlapStage = document.div();
        overlapStage.style()
                .setPosition(UiPosition.RELATIVE)
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.px(108))
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setBackgroundColor(0xFF101B2D)
                .setBorderColor(0xFF4A6FA5)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.DASHED)
                .setBorderRadius(UiStyleLength.px(10));
        overlapCard.append(overlapStage);

        ElementNode bottomLayer = document.div();
        bottomLayer.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setCursor(UiCursor.POINTER)
                .setLeft(UiStyleLength.px(18))
                .setTop(UiStyleLength.px(44))
                .setWidth(UiStyleLength.px(176))
                .setHeight(UiStyleLength.px(42))
                .setBackgroundColor(0xFF24435F)
                .setBorderColor(0xFF66CCFF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID);
        bottomLayer.appendText("底层 pointer");
        overlapStage.append(bottomLayer);

        ElementNode topLayer = document.div();
        topLayer.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setCursor(UiCursor.TEXT)
                .setLeft(UiStyleLength.px(88))
                .setTop(UiStyleLength.px(20))
                .setWidth(UiStyleLength.px(124))
                .setHeight(UiStyleLength.px(34))
                .setBackgroundColor(0xEE1F3A2B)
                .setBorderColor(0xFF6EE7B7)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID);
        topLayer.appendText("顶层 text");
        overlapStage.append(topLayer);
        row.append(overlapCard);

        ElementNode fallbackNote = document.div();
        fallbackNote.setClassName("log-area");
        fallbackNote.appendText("当前宿主已映射：default / pointer / text / move / not-allowed / wait / crosshair / ew-resize / ns-resize / none。grab 与 grabbing 会降级为 move，help 会降级为 default；不支持自定义图片光标。");
        section.append(fallbackNote);
    }

    private ElementNode createCursorProbe(String label, UiCursor cursor, int backgroundColor, int borderColor) {
        ElementNode probe = document.div();
        probe.setClassName("demo-box");
        probe.style()
                .setCursor(cursor)
                .setWidth(UiStyleLength.px(128))
                .setHeight(UiStyleLength.px(54))
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setBackgroundColor(backgroundColor)
                .setBorderColor(borderColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID);
        probe.appendText("cursor: " + label);
        return probe;
    }

    // ========== 视觉属性展示 ==========

    private void appendVisualPropertiesDemo(ElementNode root) {
        ElementNode section = createSection(root, "5. 交互命中 + 文本装饰");

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        // text-decoration 展示
        ElementNode underlineBox = document.div();
        underlineBox.setClassName("demo-box");
        underlineBox.style().setTextDecoration(UiTextDecoration.UNDERLINE);
        underlineBox.appendText("text-decoration: underline");
        row.append(underlineBox);

        ElementNode lineThroughBox = document.div();
        lineThroughBox.setClassName("demo-box");
        lineThroughBox.style().setTextDecoration(UiTextDecoration.LINE_THROUGH);
        lineThroughBox.appendText("text-decoration: line-through");
        row.append(lineThroughBox);

        ElementNode pointerHost = document.div();
        pointerHost.setClassName("demo-box clickable");
        pointerHost.style()
                .setPosition(UiPosition.RELATIVE)
                .setWidth(UiStyleLength.px(230))
                .setHeight(UiStyleLength.px(64))
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setBackgroundColor(0xFF24435F)
                .setBorderColor(0xFF66CCFF);
        pointerHost.appendText("底层可点击：pointer-events:none 覆盖层不会抢事件");
        pointerHost.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                updateEventLog("pointer-events:none 覆盖层已穿透到底层元素");
                return true;
            }
        });
        ElementNode pointerOverlay = document.div();
        pointerOverlay.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(12))
                .setTop(UiStyleLength.px(12))
                .setRight(UiStyleLength.px(12))
                .setBottom(UiStyleLength.px(12))
                .setBackgroundColor(0xAAFF6B6B)
                .setBorderColor(0xFFFFD6A5)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setPointerEvents(UiPointerEvents.NONE);
        pointerOverlay.appendText("覆盖层 pointer-events:none");
        pointerHost.append(pointerOverlay);
        row.append(pointerHost);
    }

    // ========== Border 控制展示 ==========

    private void appendBorderControlDemo(ElementNode root) {
        ElementNode section = createSection(root, "6. 边框与视觉语义");

        ElementNode desc = document.div();
        desc.appendText("展示分边 border-width / border-color、分角圆角、outline 与 box-shadow 的运行时效果；下方仍保留 content-box 与 border-box 对比。");
        section.append(desc);

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        ElementNode shadowCard = document.div();
        shadowCard.setClassName("demo-box");
        shadowCard.style()
                .setPadding(UiStyleInsets.of(UiStyleLength.px(14), UiStyleLength.px(16), UiStyleLength.px(14),
                        UiStyleLength.px(16)))
                .setBackgroundColor(0xFF1E293B)
                .setBorderRadiusCorners(UiBorderRadius.of(UiStyleLength.px(18), UiStyleLength.px(6),
                        UiStyleLength.px(18), UiStyleLength.px(6)))
                .setBoxShadow(UiBoxShadow.of(6, 8, 4, 2, 0x6638BDF8));
        shadowCard.appendText("box-shadow + 分角圆角");
        row.append(shadowCard);

        ElementNode outlineCard = document.div();
        outlineCard.setClassName("demo-box");
        outlineCard.style()
                .setBackgroundColor(0xFF111827)
                .setBorderWidth(UiStyleLength.px(2))
                .setBorderColor(0xFF334155)
                .setBorderStyle(UiBorderStyle.DOUBLE)
                .setOutline(UiOutline.of(2, 0xFF67E8F9, UiBorderStyle.DASHED, 2));
        outlineCard.appendText("double border + dashed outline");
        row.append(outlineCard);

        ElementNode splitBorderCard = document.div();
        splitBorderCard.setClassName("demo-box");
        splitBorderCard.style()
                .setBackgroundColor(0xFF0F172A)
                .setBorderWidthSides(UiStyleInsets.of(UiStyleLength.px(1), UiStyleLength.px(4),
                        UiStyleLength.px(7), UiStyleLength.px(2)))
                .setBorderColors(UiBorderColors.of(0xFF38BDF8, 0xFFF97316, 0xFF22C55E, 0xFFE879F9))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadiusCorners(UiBorderRadius.of(UiStyleLength.px(12), UiStyleLength.px(20),
                        UiStyleLength.px(4), UiStyleLength.px(16)));
        splitBorderCard.appendText("分边 border-width / border-color");
        row.append(splitBorderCard);

        ElementNode contentBox = document.div();
        contentBox.setClassName("demo-box");
        contentBox.style()
                .setWidth(UiStyleLength.px(160))
                .setPadding(UiStyleLength.px(12))
                .setBorderWidth(UiStyleLength.px(2))
                .setBorderColor(0xFF7EB8FF)
                .setBorderStyle(UiBorderStyle.SOLID);
        contentBox.appendText("默认 content-box：160px 只约束内容区");
        row.append(contentBox);

        ElementNode borderBox = document.div();
        borderBox.setClassName("demo-box");
        borderBox.style()
                .setWidth(UiStyleLength.px(160))
                .setPadding(UiStyleLength.px(12))
                .setBorderWidth(UiStyleLength.px(2))
                .setBorderColor(0xFFFFD166)
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBoxSizing(UiBoxSizing.BORDER_BOX);
        borderBox.appendText("border-box：padding/border 收进 160px");
        row.append(borderBox);
    }

    // ========== CSS Variables 展示 ==========

    private void appendCssVariablesDemo(ElementNode root) {
        ElementNode section = createSection(root, "7. 变量容器与主题切换");

        ElementNode desc = document.div();
        desc.appendText("当前提供的是文档级变量容器。示例会在点击后读取变量值并手动回写到预览卡片，用来演示主题变量的组织方式；页面尚未实现 CSS `var(...)` 声明级自动解析：");
        section.append(desc);

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        // 主题切换按钮
        ElementNode themeBtn = document.button();
        themeBtn.setClassName("demo-box highlight clickable");
        themeBtn.appendText("切换主题 (Dark/Light)");
        themeBtn.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                toggleTheme();
                return true; // 消费事件，无需冒泡
            }
        });
        row.append(themeBtn);

        // 使用变量的展示元素（主题切换时会同步更新）
        UiStyleVariables vars = document.getStyleVariables();
        varBox = document.div();
        varBox.setClassName("demo-box");
        if (vars != null) {
            varBox.style()
                    .setBackgroundColor(vars.getColor("--bg-elevated"))
                    .setBorderColor(vars.getColor("--primary"));
        }
        varBox.appendText("使用 --primary 和 --bg-elevated 变量");
        row.append(varBox);

        // 主题状态显示
        ElementNode statusArea = document.div();
        statusArea.setClassName("log-area");
        themeStatusText = statusArea.appendText("当前主题: Dark | 变量数: " + getVariableCount());
        section.append(statusArea);
    }

    // ========== 文本排版展示 ==========

    private void appendTextTypographyDemo(ElementNode root) {
        ElementNode section = createSection(root, "8. 文本与替换元素");

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        // aspect-ratio 展示
        ElementNode aspectBox = document.div();
        aspectBox.setClassName("demo-box");
        aspectBox.style()
                .setAspectRatio(16.0f / 9.0f)
                .setWidth(UiStyleLength.px(128))
                .setBackgroundColor(0xFF2A3F5F);
        aspectBox.appendText("aspect-ratio: 16/9");
        row.append(aspectBox);

        // object-fit 展示
        ElementNode fitCard = document.div();
        fitCard.setClassName("demo-box");
        fitCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6));
        ElementNode fitImage = document.img();
        fitImage.setAttribute("src", "minecraft:textures/gui/options_background.png");
        fitImage.setAttribute("alt", "object-fit 预览图");
        fitImage.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(72))
                .setObjectFit(UiObjectFit.CONTAIN)
                .setBackgroundColor(0xFF0D1520)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderColor(0xFF4A6FA5)
                .setBorderStyle(UiBorderStyle.SOLID);
        fitCard.append(fitImage);
        ElementNode fitLabel = document.div();
        fitLabel.appendText("img + object-fit: contain");
        fitCard.append(fitLabel);
        row.append(fitCard);
    }

    // ========== 高级选择器展示 ==========

    private void appendAdvancedSelectorDemo(ElementNode root) {
        ElementNode section = createSection(root, "9. 高级选择器、伪类与伪元素");

        ElementNode desc = document.div();
        desc.appendText("补齐后代/子代选择器、结构伪类、交互伪类、::before/::after、!important 和 inherit 关键字的可视化样例：");
        section.append(desc);

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        ElementNode selectorStage = document.div();
        selectorStage.setClassName("demo-box selector-stage");
        selectorStage.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6))
                .setWidth(UiStyleLength.px(230));
        selectorStage.appendText(".selector-stage > .child-card / 后代 .descendant-chip");
        ElementNode directChild = createDemoBox("直接子代：由 child combinator 命中");
        directChild.setClassName("demo-box child-card");
        selectorStage.append(directChild);
        ElementNode nested = createDemoBox("嵌套容器");
        ElementNode descendant = createDemoBox("后代 chip：由 descendant selector 命中");
        descendant.setClassName("demo-box descendant-chip");
        nested.append(descendant);
        selectorStage.append(nested);
        row.append(selectorStage);

        ElementNode pseudoList = document.div();
        pseudoList.setClassName("demo-box pseudo-list");
        pseudoList.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6))
                .setWidth(UiStyleLength.px(180));
        pseudoList.append(createDemoBox(":first-child（应是绿色边框）"));
        pseudoList.append(createDemoBox(":nth-child(2)（应是橙色边框）"));
        pseudoList.append(createDemoBox(":last-child（应是粉色边框）"));
        row.append(pseudoList);

        ElementNode hoverCard = createDemoBox("hover 我：应明显变亮并上移");
        hoverCard.setClassName("demo-box hover-card clickable");
        hoverCard.style().setWidth(UiStyleLength.px(178));
        hoverCard.setHoverHandler(new DocumentElementHoverHandler() {
            @Override
            public boolean onHoverChanged(DocumentElementHoverEvent event) {
                if (event.isHovered()) {
                    hoverCard.style()
                            .setBackgroundColor(0xFF38BDF8)
                            .setBorderColor(0xFFE0F2FE)
                            .setTextColor(0xFF082F49)
                            .setTransform(UiTransform.translate(0.0F, -4.0F));
                } else {
                    hoverCard.style()
                            .setBackgroundColor(0xFF1A2A44)
                            .setBorderColor(0xFF3B5998)
                            .setTextColor(0xFFCCDDFF)
                            .setTransform(UiTransform.identity());
                }
                updateText(selectorStateText, event.isHovered()
                        ? ":hover 已进入：当前卡片应变亮、上移并切换为深色文字"
                        : ":hover 已离开：当前卡片应恢复默认深色卡片样式");
                return false;
            }
        });
        row.append(hoverCard);

        ElementNode focusCard = createDemoBox("点击聚焦：:focus / :focus-visible");
        focusCard.setClassName("demo-box focus-card clickable");
        focusCard.setFocusable(true);
        focusCard.style().setWidth(UiStyleLength.px(196));
        focusCard.setFocusHandler(new DocumentElementFocusHandler() {
            @Override
            public void onFocusChanged(DocumentElementFocusEvent event) {
                if (event.isFocused()) {
                    focusCard.style()
                            .setBorderColor(0xFFFFD166)
                            .setOutline(UiOutline.of(2, 0xFFFFD166, UiBorderStyle.SOLID, 2))
                            .setTextColor(event.isFocusVisible() ? 0xFFF5D0FE : 0xFFFFFFFF)
                            .setBackgroundColor(event.isFocusVisible() ? 0xFF3B0764 : 0xFF1A2A44);
                } else {
                    focusCard.style()
                            .setBorderColor(0xFF3B5998)
                            .clearOutline()
                            .setTextColor(0xFFCCDDFF)
                            .setBackgroundColor(0xFF1A2A44);
                }
                updateText(selectorStateText, event.isFocused()
                        ? ":focus 已生效，focusVisible=" + event.isFocusVisible() + "；有焦点时应出现金色描边"
                        : "焦点已移出 focus-card");
            }
        });
        focusCard.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                boolean focused = event.getCurrentTarget().focus();
                updateText(selectorStateText, "focus() 调用结果: " + focused + "，键盘 Tab 聚焦时应触发 :focus-visible 样式");
                return true;
            }
        });
        row.append(focusCard);

        ElementNode disabledButton = document.button();
        disabledButton.setClassName("demo-box");
        disabledButton.setAttribute("disabled", "true");
        disabledButton.style()
                .setBackgroundColor(0xFF334155)
                .setTextColor(0xFF94A3B8)
                .setBorderColor(0xFF64748B);
        disabledButton.appendText("disabled 属性按钮（应灰化）");
        row.append(disabledButton);

        ElementNode pseudoCard = createDemoBox("伪元素正文");
        pseudoCard.setClassName("demo-box pseudo-card");
        row.append(pseudoCard);

        ElementNode importantCard = createDemoBox("!important 文本色覆盖后声明");
        importantCard.setClassName("demo-box important-low");
        row.append(importantCard);

        ElementNode keywordParent = createDemoBox("inherit 父文本色");
        keywordParent.setClassName("demo-box keyword-parent");
        ElementNode keywordChild = document.span();
        keywordChild.setClassName("keyword-child");
        keywordChild.appendText(" 子元素显式 inherit");
        keywordParent.append(keywordChild);
        row.append(keywordParent);

        selectorStateText = appendLogLine(section,
                "结构伪类验收：first/nth/last 三块分别应呈绿/橙/粉边框；child-card 应是青色边框；descendant chip 应是亮青文字蓝底；disabled 按钮应灰化；hover/focus 反馈当前通过事件回写保证可见。 ");
    }

    // ========== DOM 操作展示 ==========

    private void appendDomMutationDemo(ElementNode root) {
        ElementNode section = createSection(root, "10. DOM 批量操作与自定义事件");

        ElementNode desc = document.div();
        desc.appendText("展示 createDocumentFragment、insertBefore、replaceChild、cloneNode(true)、getNextSibling，以及 addEventListener / removeEventListener / dispatchEvent。");
        section.append(desc);

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        ElementNode mutationList = document.div();
        mutationList.setClassName("demo-box");
        mutationList.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6))
                .setWidth(UiStyleLength.px(260));
        row.append(mutationList);

        DocumentFragmentNode fragment = document.createDocumentFragment();
        fragment.appendChild(createDemoBox("fragment item A"));
        fragment.appendChild(createDemoBox("fragment item B"));
        mutationList.append(fragment);

        ElementNode original = createDemoBox("replaceChild 原始节点");
        mutationList.append(original);
        ElementNode inserted = createDemoBox("insertBefore 插入到第一位");
        mutationList.insertBefore(inserted, mutationList.getFirstChild());
        ElementNode replacement = createDemoBox("replaceChild 替换结果");
        mutationList.replaceChild(replacement, original);
        ElementNode clone = (ElementNode) replacement.cloneNode(true);
        clone.setClassName("demo-box highlight");
        clone.appendText(" / cloneNode(true)");
        mutationList.append(clone);

        DocumentNode nextSibling = inserted.getNextSibling();
        domMutationStateText = appendLogLine(section, "DOM 操作结果：childCount=" + mutationList.getChildCount()
                + "，first.getNextSibling=" + formatNodeName(nextSibling));

        final ElementNode customTarget = createDemoBox("自定义事件目标：等待 dispatchEvent");
        customTarget.setClassName("demo-box clickable");
        row.append(customTarget);

        final int[] eventCount = new int[] { 0 };
        final DocumentCustomEventHandler customHandler = new DocumentCustomEventHandler() {
            @Override
            public boolean onEvent(DocumentCustomEvent event) {
                eventCount[0]++;
                event.preventDefault();
                customTarget.clearChildren();
                customTarget.appendText("收到 " + event.getType() + " #" + eventCount[0]);
                updateText(domMutationStateText, "dispatchEvent: detail=" + event.getDetail()
                        + "，defaultPrevented=" + event.isDefaultPrevented());
                return false;
            }
        };
        final boolean[] listenerAttached = new boolean[] { true };
        customTarget.addEventListener("uilib:ping", customHandler);

        ElementNode dispatchButton = document.button();
        dispatchButton.setClassName("demo-box highlight clickable");
        dispatchButton.appendText("dispatchEvent('uilib:ping')");
        dispatchButton.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                boolean defaultAllowed = customTarget.dispatchEvent(new DocumentCustomEvent("uilib:ping",
                        "payload-" + (eventCount[0] + 1), true, true));
                updateText(domMutationStateText, "dispatchEvent 返回 defaultAllowed=" + defaultAllowed
                        + "，listenerAttached=" + listenerAttached[0]);
                return true;
            }
        });
        row.append(dispatchButton);

        ElementNode toggleButton = document.button();
        toggleButton.setClassName("demo-box clickable");
        toggleButton.appendText("remove/add listener");
        toggleButton.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                if (listenerAttached[0]) {
                    customTarget.removeEventListener("uilib:ping", customHandler);
                    listenerAttached[0] = false;
                } else {
                    customTarget.addEventListener("uilib:ping", customHandler);
                    listenerAttached[0] = true;
                }
                updateText(domMutationStateText, "自定义事件 listenerAttached=" + listenerAttached[0]);
                return true;
            }
        });
        row.append(toggleButton);
    }

    // ========== 语义元素展示 ==========

    private void appendSemanticElementsDemo(ElementNode root) {
        ElementNode section = createSection(root, "11. HTML-like 语义元素");
        document.setLinkActivationHandler(new DocumentLinkActivationHandler() {
            @Override
            public void onLinkActivated(DocumentLinkActivationEvent event) {
                updateText(semanticStateText, "link 激活: href=" + event.getHref() + "，target="
                        + event.getLinkTarget() + "，defaultPrevented=" + event.isDefaultPrevented());
            }
        });

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        ElementNode linkCard = createDemoBox("a[href] 默认可点击、可聚焦、cursor:pointer，并支持 #id 片段跳转：");
        linkCard.style()
                .setWidth(UiStyleLength.px(272))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6));
        ElementNode link = document.a();
        link.setAttribute("href", "#semantic-link-target");
        link.setAttribute("target", "_self");
        link.appendText("跳到第 15 节底部锚点");
        linkCard.append(link);
        ElementNode linkHint = document.div();
        linkHint.style().setTextColor(0xFFFFD166);
        linkHint.appendText("预期：点击后页面应滚动到第 15 节底部的 #semantic-link-target");
        linkCard.append(linkHint);
        row.append(linkCard);

        ElementNode listCard = createDemoBox("list-style-type");
        listCard.style()
                .setWidth(UiStyleLength.px(210))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6));
        ElementNode unordered = document.ul();
        unordered.style().setListStyleType(UiListStyleType.SQUARE);
        appendListItem(unordered, "square marker");
        appendListItem(unordered, "继承列表标记");
        ElementNode ordered = document.ol();
        ordered.style().setListStyleType(UiListStyleType.DECIMAL);
        appendListItem(ordered, "decimal 1");
        appendListItem(ordered, "decimal 2");
        listCard.append(unordered);
        listCard.append(ordered);
        row.append(listCard);

        ElementNode tableCard = createDemoBox("table + border-collapse: collapse");
        tableCard.style()
                .setWidth(UiStyleLength.px(300))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6));
        ElementNode table = document.table();
        table.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setBorderCollapse(UiBorderCollapse.COLLAPSE)
                .setBorderColor(0xFF38BDF8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID);
        appendTableRow(table, "能力", "展示", true);
        appendTableRow(table, "ul/ol/li", "真实标记", false);
        appendTableRow(table, "table", "合并边线", false);
        tableCard.append(table);
        row.append(tableCard);

        semanticStateText = appendLogLine(section, "语义元素展示已加载：点击链接后应先发生片段滚动，再记录 href/target/defaultPrevented。 ");
    }

    // ========== 文本排版能力展示 ==========

    private void appendTextLayoutControlsDemo(ElementNode root) {
        ElementNode section = createSection(root, "12. 文本排版控制");

        ElementNode desc = document.div();
        desc.appendText("把文本排版拆成更小颗粒度卡片，分别核对省略号、空白保留、对齐/缩进、大小写转换、字距、阴影、粗斜体和长 token 换行。");
        section.append(desc);

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        ElementNode ellipsisPanel = createDemoPanel("A. NOWRAP + ELLIPSIS", 228);
        ElementNode ellipsisSample = document.div();
        ellipsisSample.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setTextOverflow(UiTextOverflow.ELLIPSIS)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setBackgroundColor(0xFF0F172A);
        ellipsisSample.appendText("This is a very long line that should end with ellipsis when width is constrained.");
        ellipsisPanel.append(ellipsisSample);
        ellipsisPanel.append(createExpectationText("应为单行，尾部出现省略号。"));
        row.append(ellipsisPanel);

        ElementNode preWrapPanel = createDemoPanel("B. PRE_WRAP 保留空白", 220);
        ElementNode preWrapSample = document.div();
        preWrapSample.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setWhiteSpace(UiWhiteSpace.PRE_WRAP)
                .setBackgroundColor(0xFF1D2B3A);
        preWrapSample.appendText("保留    空格\n显式换行");
        preWrapPanel.append(preWrapSample);
        preWrapPanel.append(createExpectationText("中间多个空格不应折叠，且应换成两行。"));
        row.append(preWrapPanel);

        ElementNode alignPanel = createDemoPanel("C. 对齐 / 行高 / 缩进 / 大小写", 236);
        ElementNode alignSample = document.div();
        alignSample.style()
                .setWidth(UiStyleLength.px(168))
                .setTextAlign(UiTextAlign.CENTER)
                .setLineHeight(UiStyleLength.px(18))
                .setTextIndent(UiStyleLength.px(14))
                .setBackgroundColor(0xFF1E293B);
        alignSample.appendText("center line-height indent sample should wrap twice");
        ElementNode transformUpper = document.div();
        transformUpper.style()
                .setTextTransform(UiTextTransform.UPPERCASE)
                .setBackgroundColor(0xFF312E81);
        transformUpper.appendText("uppercase sample");
        ElementNode transformCap = document.div();
        transformCap.style()
                .setTextTransform(UiTextTransform.CAPITALIZE)
                .setBackgroundColor(0xFF0F766E);
        transformCap.appendText("capitalize sample words");
        alignPanel.append(alignSample);
        alignPanel.append(transformUpper);
        alignPanel.append(transformCap);
        alignPanel.append(createExpectationText("第一块应居中且首行缩进；第二块应全大写；第三块每个单词首字母大写。"));
        row.append(alignPanel);

        ElementNode fontPanel = createDemoPanel("D. 字距 / 阴影 / 粗斜体", 224);
        ElementNode fontSample = document.div();
        fontSample.style()
                .setLetterSpacing(UiStyleLength.px(1))
                .setTextShadow(UiTextShadow.of(1, 1, 0, 0xFF000000))
                .setFontWeight(UiFontWeight.BOLD)
                .setFontStyle(UiFontStyle.ITALIC)
                .setBackgroundColor(0xFF3B2A24);
        fontSample.appendText("bold italic shadow");
        fontPanel.append(fontSample);
        fontPanel.append(createExpectationText("文字应更粗、更斜，右下方应能看到阴影，字距比默认更开。"));
        row.append(fontPanel);

        ElementNode breakAllPanel = createDemoPanel("E. word-break: break-all", 224);
        ElementNode breakAllSample = document.div();
        breakAllSample.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setWordBreak(UiWordBreak.BREAK_ALL)
                .setBackgroundColor(0xFF243B2A);
        breakAllSample.appendText("SuperLongUnbrokenTokenWithCJK混排混排混排");
        breakAllPanel.append(breakAllSample);
        breakAllPanel.append(createExpectationText("长 token 应被直接拆开换行，而不是整串溢出。"));
        row.append(breakAllPanel);

        ElementNode anywherePanel = createDemoPanel("F. overflow-wrap:anywhere", 246);
        ElementNode anywhereSample = document.div();
        anywhereSample.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setOverflowWrap(UiOverflowWrap.ANYWHERE)
                .setBackgroundColor(0xFF1F2937);
        anywhereSample.appendText("https://example.invalid/a/very/long/path/without/spaces");
        anywherePanel.append(anywhereSample);
        anywherePanel.append(createExpectationText("长 URL 应在任意位置断行，避免横向超出卡片。"));
        row.append(anywherePanel);

        textLayoutStateText = appendLogLine(section, "文本排版展示已拆分：每张卡只验证一组能力，便于肉眼核对实际表现。 ");
    }

    // ========== 布局细节展示 ==========

    private void appendLayoutAndFlexDetailsDemo(ElementNode root) {
        ElementNode section = createSection(root, "13. 布局约束与 flex 细节");

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        ElementNode flexStage = createDemoBox("flex-basis / order / align-self / space-evenly");
        flexStage.style()
                .setWidth(UiStyleLength.px(460))
                .setHeight(UiStyleLength.px(120))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.START)
                .setJustifyContent(UiJustifyContent.SPACE_EVENLY)
                .setColumnGap(UiStyleLength.px(8));
        ElementNode orderTwo = createFlexBadge("order:2 / basis:120", 0xFF1D4ED8);
        orderTwo.style()
                .setOrder(2)
                .setFlexBasis(UiStyleLength.px(120))
                .setAlignSelf(UiAlignSelf.END);
        ElementNode orderOne = createFlexBadge("order:1 / center", 0xFF047857);
        orderOne.style()
                .setOrder(1)
                .setFlexBasis(UiStyleLength.px(110))
                .setAlignSelf(UiAlignSelf.CENTER);
        ElementNode autoMargin = createFlexBadge("margin:auto", 0xFF92400E);
        autoMargin.style()
                .setOrder(3)
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.auto(), UiStyleLength.px(0),
                        UiStyleLength.auto()));
        flexStage.append(orderTwo);
        flexStage.append(orderOne);
        flexStage.append(autoMargin);
        row.append(flexStage);

        ElementNode constraintCard = createDemoBox("calc(100% - 24px) + min/max 尺寸约束");
        constraintCard.style()
                .setWidth(UiStyleLength.px(300))
                .setPadding(UiStyleLength.px(8))
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6));
        ElementNode calcBox = createDemoBox("width: calc(100% - 24px)");
        calcBox.style()
                .setWidth(UiStyleLength.calc(1.0F, -24.0F))
                .setMinWidth(UiStyleLength.px(120))
                .setMaxWidth(UiStyleLength.px(260))
                .setBackgroundColor(0xFF164E63);
        ElementNode minMaxHeight = createDemoBox("min-height / max-height 约束文本高度");
        minMaxHeight.style()
                .setMinHeight(UiStyleLength.px(34))
                .setMaxHeight(UiStyleLength.px(46))
                .setOverflowY(UiOverflow.HIDDEN)
                .setBackgroundColor(0xFF312E81);
        constraintCard.append(calcBox);
        constraintCard.append(minMaxHeight);
        row.append(constraintCard);

        ElementNode stickyScroller = createDemoBox("position:sticky 内部滚动区");
        stickyScroller.style()
                .setWidth(UiStyleLength.px(250))
                .setHeight(UiStyleLength.px(118))
                .setOverflowY(UiOverflow.AUTO)
                .setScrollbarWidth(UiScrollbarWidth.THIN)
                .setScrollbarColor(0xFF67E8F9, 0x55223A4A);
        ElementNode stickyHeader = createDemoBox("sticky top:0");
        stickyHeader.style()
                .setPosition(UiPosition.STICKY)
                .setTop(UiStyleLength.px(0))
                .setZIndex(2)
                .setBackgroundColor(0xFF0F766E);
        stickyScroller.append(stickyHeader);
        for (int index = 1; index <= 8; index++) {
            stickyScroller.append(createDemoBox("滚动行 " + index));
        }
        row.append(stickyScroller);

        flexStateText = appendLogLine(section, "布局细节展示已加载：flex 视觉顺序应为 order:1 -> order:2 -> margin:auto。 ");
    }

    // ========== 滚动条与可见性展示 ==========

    private void appendScrollAndVisibilityDemo(ElementNode root) {
        ElementNode section = createSection(root, "14. 滚动条、程序化滚动与 visibility");

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        final ElementNode scrollHost = createDemoBox("scrollbar-color + scrollbar-width: thin");
        scrollHost.style()
                .setWidth(UiStyleLength.px(250))
                .setHeight(UiStyleLength.px(116))
                .setOverflowY(UiOverflow.SCROLL)
                .setScrollbarWidth(UiScrollbarWidth.THIN)
                .setScrollbarColor(UiScrollbarColor.of(0xFF60A5FA, 0x55334155));
        scrollHost.setScrollHandler(new DocumentElementScrollHandler() {
            @Override
            public void onScroll(DocumentElementScrollEvent event) {
                updateText(scrollStateText, "scroll 事件: top=" + event.getScrollTop() + "，contentHeight="
                        + event.getScrollHeight());
            }
        });
        for (int index = 1; index <= 9; index++) {
            scrollHost.append(createDemoBox("scroll 行 " + index));
        }
        row.append(scrollHost);

        ElementNode hiddenScrollbar = createDemoBox("scrollbar-width:none：只隐藏滚动条，内容仍可见");
        hiddenScrollbar.style()
                .setWidth(UiStyleLength.px(220))
                .setHeight(UiStyleLength.px(116))
                .setOverflowY(UiOverflow.AUTO)
                .setScrollbarWidth(UiScrollbarWidth.NONE);
        for (int index = 1; index <= 7; index++) {
            hiddenScrollbar.append(createDemoBox("内容行 " + index + "（滚动条本身隐藏）"));
        }
        row.append(hiddenScrollbar);

        ElementNode visibilityCard = createDemoBox("visibility:hidden 保留布局空间但不绘制/不命中");
        visibilityCard.style()
                .setWidth(UiStyleLength.px(260))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setColumnGap(UiStyleLength.px(8));
        visibilityCard.append(createFlexBadge("左", 0xFF2563EB));
        ElementNode hidden = createFlexBadge("隐藏", 0xFFDC2626);
        hidden.style().setVisibility(UiVisibility.HIDDEN);
        visibilityCard.append(hidden);
        visibilityCard.append(createFlexBadge("右", 0xFF16A34A));
        row.append(visibilityCard);

        ElementNode scrollToButton = document.button();
        scrollToButton.setClassName("demo-box highlight clickable");
        scrollToButton.appendText("只执行 scrollTo(0, 72)");
        scrollToButton.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                boolean scrollToResult = scrollHost.scrollTo(0, 72);
                updateText(scrollStateText, "scrollTo=" + scrollToResult + "，top=" + scrollHost.getScrollTop()
                        + "/" + scrollHost.getMaxScrollTop());
                return true;
            }
        });
        row.append(scrollToButton);

        ElementNode intoViewButton = document.button();
        intoViewButton.setClassName("demo-box clickable");
        intoViewButton.appendText("只执行末项 scrollIntoView()");
        intoViewButton.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                DocumentNode lastChild = scrollHost.getLastChild();
                boolean intoViewResult = lastChild instanceof ElementNode
                        && ((ElementNode) lastChild).scrollIntoView();
                updateText(scrollStateText, "scrollIntoView=" + intoViewResult + "，top="
                        + scrollHost.getScrollTop() + "/" + scrollHost.getMaxScrollTop());
                return true;
            }
        });
        row.append(intoViewButton);

        scrollStateText = appendLogLine(section,
                "滚动条展示已加载：中间卡片只隐藏滚动条轨道/滑块，内容行应仍可见并可滚动；左键按钮只测 scrollTo，右键按钮只测 scrollIntoView。 ");
    }

    // ========== 背景图与 transform 展示 ==========

    private void appendVisualMediaDemo(ElementNode root) {
        ElementNode section = createSection(root, "15. background-image、transform 与图片回退");

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        ElementNode backgroundCard = createDemoBox("background-image: options_background.png 拉伸填充 border box");
        backgroundCard.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(92))
                .setBackgroundImage(UiBackgroundImage.texture("minecraft:textures/gui/options_background.png", 256, 256))
                .setTextShadow(UiTextShadow.of(1, 1, 0, 0xFF000000))
                .setTextColor(0xFFFFFFFF);
        row.append(backgroundCard);

        ElementNode transformStage = createDemoBox("transform 不参与布局，只影响绘制与命中");
        transformStage.style()
                .setWidth(UiStyleLength.px(280))
                .setHeight(UiStyleLength.px(118))
                .setPosition(UiPosition.RELATIVE)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        ElementNode rotateBox = createDemoBox("rotate + scale");
        rotateBox.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(22))
                .setTop(UiStyleLength.px(50))
                .setWidth(UiStyleLength.px(110))
                .setTransform(UiTransform.of(0.0F, 0.0F, 1.08F, 1.08F, -8.0F));
        ElementNode translateBox = createDemoBox("translate");
        translateBox.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(142))
                .setTop(UiStyleLength.px(48))
                .setWidth(UiStyleLength.px(88))
                .setBackgroundColor(0xFF0F766E)
                .setTransform(UiTransform.translate(22.0F, -18.0F));
        transformStage.append(rotateBox);
        transformStage.append(translateBox);
        row.append(transformStage);

        ElementNode imageFallback = createDemoBox("img[src] 解析失败时绘制 alt 文本回退：");
        imageFallback.style()
                .setWidth(UiStyleLength.px(260))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6));
        ElementNode brokenImage = document.img();
        brokenImage.setAttribute("src", "demo://missing-image");
        brokenImage.setAttribute("alt", "ALT 回退文本");
        brokenImage.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(44))
                .setBackgroundColor(0xFF0F172A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderColor(0xFF94A3B8)
                .setBorderStyle(UiBorderStyle.DASHED)
                .setObjectFit(UiObjectFit.SCALE_DOWN);
        imageFallback.append(brokenImage);
        row.append(imageFallback);

        ElementNode anchorTarget = createDemoBox("#semantic-link-target：第 11 节链接应滚动到这里");
        anchorTarget.setId("semantic-link-target");
        anchorTarget.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(12), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setBackgroundColor(0xFF3B0764)
                .setBorderColor(0xFFF0ABFC)
                .setTextColor(0xFFF5D0FE);
        section.append(anchorTarget);

        mediaStateText = appendLogLine(section, "媒体展示已加载：背景图、transform 与 img alt 回退均走运行时绘制链路；第 11 节链接应滚动到上方锚点。 ");
    }

    // ========== 辅助方法 ==========

    /**
     * 创建标准展示卡片。
     */
    private ElementNode createDemoBox(String text) {
        ElementNode box = document.div();
        box.setClassName("demo-box");
        box.appendText(text);
        return box;
    }

    /**
     * 创建纵向排列的展示面板。
     */
    private ElementNode createDemoPanel(String title, int width) {
        ElementNode panel = createDemoBox(title);
        panel.style()
                .setWidth(UiStyleLength.px(width))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6));
        return panel;
    }

    /**
     * 创建每张测试卡片的预期说明文本。
     */
    private ElementNode createExpectationText(String text) {
        ElementNode note = document.div();
        note.style()
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.DASHED)
                .setTextColor(0xFFBFDBFE);
        note.appendText("预期：" + text);
        return note;
    }

    /**
     * 创建 flex 展示徽章。
     */
    private ElementNode createFlexBadge(String text, int backgroundColor) {
        ElementNode badge = createDemoBox(text);
        badge.style()
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(backgroundColor)
                .setBorderColor(0xFFFFFFFF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(8))
                .setTextColor(0xFFFFFFFF);
        return badge;
    }

    /**
     * 追加标准日志行。
     */
    private TextNode appendLogLine(ElementNode section, String text) {
        ElementNode logArea = document.div();
        logArea.setClassName("log-area");
        TextNode textNode = logArea.appendText(text);
        section.append(logArea);
        return textNode;
    }

    /**
     * 追加列表项。
     */
    private void appendListItem(ElementNode list, String text) {
        ElementNode item = document.li();
        item.appendText(text);
        list.append(item);
    }

    /**
     * 追加表格行。
     */
    private void appendTableRow(ElementNode table, String first, String second, boolean header) {
        ElementNode row = document.tr();
        ElementNode firstCell = header ? document.th() : document.td();
        ElementNode secondCell = header ? document.th() : document.td();
        firstCell.appendText(first);
        secondCell.appendText(second);
        styleTableCell(firstCell, header);
        styleTableCell(secondCell, header);
        row.append(firstCell);
        row.append(secondCell);
        table.append(row);
    }

    /**
     * 设置表格单元格样式。
     */
    private void styleTableCell(ElementNode cell, boolean header) {
        cell.style()
                .setPadding(UiStyleLength.px(6))
                .setBorderColor(0xFF38BDF8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBackgroundColor(header ? 0xFF164E63 : 0xFF0F172A)
                .setTextColor(header ? 0xFFE0F2FE : 0xFFCBD5E1);
    }

    /**
     * 格式化节点名称。
     */
    private String formatNodeName(DocumentNode node) {
        if (node instanceof ElementNode) {
            return ((ElementNode) node).getTagName();
        }
        return node == null ? "null" : node.getNodeType().name();
    }

    /**
     * 安全更新文本节点。
     */
    private void updateText(TextNode textNode, String message) {
        if (textNode != null) {
            textNode.setText(message);
        }
    }

    /**
     * 创建一个标准展示区段。
     */
    private ElementNode createSection(ElementNode parent, String title) {
        ElementNode section = document.div();
        section.setClassName("section");
        parent.append(section);

        ElementNode titleEl = document.div();
        titleEl.setClassName("section-title");
        titleEl.appendText(title);
        section.append(titleEl);

        return section;
    }

    /**
     * 更新事件日志文本。
     */
    private void updateEventLog(String message) {
        if (eventLogArea != null) {
            eventLogCount++;
            if (eventLogCount == 1) {
                eventLogArea.clearChildren();
            }
            ElementNode line = document.div();
            line.appendText("#" + eventLogCount + " [事件] " + message);
            eventLogArea.append(line);
        }
    }

    /**
     * 更新查询结果文本。
     */
    private void updateQueryResult(String message) {
        if (queryResultText != null) {
            queryResultText.setText("[查询] " + message);
        }
    }

    /**
     * 切换主题，并手动刷新变量预览卡片。
     */
    private void toggleTheme() {
        darkTheme = !darkTheme;
        UiStyleVariables vars = document.getStyleVariables();
        if (vars == null) return;

        if (darkTheme) {
            vars.setColor("--primary", 0xFF4488FF)
                    .setColor("--bg-surface", 0xFF1A2A44)
                    .setColor("--bg-elevated", 0xFF243B5C)
                    .setColor("--text-primary", 0xFFE8EEFF)
                    .setColor("--border-default", 0xFF2E4C7F);
        } else {
            vars.setColor("--primary", 0xFF2266CC)
                    .setColor("--bg-surface", 0xFFE8F0FF)
                    .setColor("--bg-elevated", 0xFFFFFFFF)
                    .setColor("--text-primary", 0xFF1A1A2E)
                    .setColor("--border-default", 0xFFAABBDD);
        }

        // 同步更新引用变量的展示元素（当前无 var() 自动解析机制，需手动刷新）
        if (varBox != null) {
            varBox.style()
                    .setBackgroundColor(vars.getColor("--bg-elevated"))
                    .setBorderColor(vars.getColor("--primary"));
        }

        if (themeStatusText != null) {
            themeStatusText.setText("当前主题: " + (darkTheme ? "Dark" : "Light") + " | 变量数: " + getVariableCount());
        }
    }

    /**
     * 获取当前变量总数。
     */
    private int getVariableCount() {
        UiStyleVariables vars = document.getStyleVariables();
        if (vars == null) return 0;
        return vars.getColors().size() + vars.getLengths().size() + vars.getStrings().size();
    }
}
