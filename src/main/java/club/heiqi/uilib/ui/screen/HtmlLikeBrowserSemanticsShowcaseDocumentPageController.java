package club.heiqi.uilib.ui.screen;

import java.util.Objects;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiBorderColors;
import club.heiqi.uilib.ui.style.UiBorderRadius;
import club.heiqi.uilib.ui.style.UiBorderStyle;
import club.heiqi.uilib.ui.style.UiBoxShadow;
import club.heiqi.uilib.ui.style.UiBoxSizing;
import club.heiqi.uilib.ui.style.UiCursor;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiFlexWrap;
import club.heiqi.uilib.ui.style.UiObjectFit;
import club.heiqi.uilib.ui.style.UiOutline;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiPointerEvents;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.UiStyleInsets;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.style.UiStyleSheet;
import club.heiqi.uilib.ui.style.UiStyleVariables;
import club.heiqi.uilib.ui.style.UiTextDecoration;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * 浏览器语义新功能展示页控制器。
 *
 * <p>展示当前已接入运行时的浏览器语义能力：样式表/选择器、事件传播、DOM 查询、
 * pointer-events、文本装饰、宽高比、替换元素适配与变量容器。</p>
 */
final class HtmlLikeBrowserSemanticsShowcaseDocumentPageController extends DocumentPageController {

    private final DocumentPageAuthoringSurface documentPage;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;
    private final UiDocument document;
    private ElementNode eventLogArea;
    private TextNode queryResultText;
    private TextNode themeStatusText;
    private ElementNode varBox;
    private boolean darkTheme = true;
    private int eventLogCount;

    /**
     * 创建浏览器语义展示页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param documentPage 文档页面壳
     */
    HtmlLikeBrowserSemanticsShowcaseDocumentPageController(DocumentUiScope documentUi,
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
    HtmlLikeBrowserSemanticsShowcaseDocumentPageController(DocumentUiScope documentUi,
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
    void configureDocumentPage() {
        documentPage.setContentWidthRange(780, 1200)
                .setMinContentHeight(640)
                .setViewportFillRatio(0.94F, 0.92F);
    }

    @Override
    void buildDocument() {
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
        appendVisualPropertiesDemo(root);
        appendBorderControlDemo(root);
        appendCssVariablesDemo(root);
        appendTextTypographyDemo(root);
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
                        .setTextColor(0xFF88AACC));
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
        summary.appendText("展示已接入运行时的 CSS 选择器、事件传播、DOM 查询、pointer-events、文本装饰、box-shadow、outline、分边 border、分角圆角、宽高比、object-fit 与变量容器能力。");
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

    // ========== 视觉属性展示 ==========

    private void appendVisualPropertiesDemo(ElementNode root) {
        ElementNode section = createSection(root, "4. 交互命中 + 文本装饰");

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
        ElementNode section = createSection(root, "5. 边框与视觉语义");

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
        ElementNode section = createSection(root, "6. 变量容器与主题切换");

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
        ElementNode section = createSection(root, "7. 文本与替换元素");

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

    // ========== 辅助方法 ==========

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
