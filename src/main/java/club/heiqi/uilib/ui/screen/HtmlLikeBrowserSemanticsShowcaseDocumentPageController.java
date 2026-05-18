package club.heiqi.uilib.ui.screen;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.style.ComputedStyle;
import club.heiqi.uilib.ui.style.UiAlignItems;
import club.heiqi.uilib.ui.style.UiBorderColors;
import club.heiqi.uilib.ui.style.UiBorderRadius;
import club.heiqi.uilib.ui.style.UiBorderStyle;
import club.heiqi.uilib.ui.style.UiBoxShadow;
import club.heiqi.uilib.ui.style.UiCursor;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiFlexDirection;
import club.heiqi.uilib.ui.style.UiFlexWrap;
import club.heiqi.uilib.ui.style.UiJustifyContent;
import club.heiqi.uilib.ui.style.UiObjectFit;
import club.heiqi.uilib.ui.style.UiOutline;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiPointerEvents;
import club.heiqi.uilib.ui.style.UiPseudoClass;
import club.heiqi.uilib.ui.style.UiSelector;
import club.heiqi.uilib.ui.style.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.UiStyleInsets;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.style.UiStyleResolver;
import club.heiqi.uilib.ui.style.UiStyleSheet;
import club.heiqi.uilib.ui.style.UiStyleVariables;
import club.heiqi.uilib.ui.style.UiTextDecoration;
import club.heiqi.uilib.ui.style.UiWordBreak;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * 浏览器语义新功能展示页控制器。
 *
 * <p>展示本次新增的浏览器语义能力：样式表/选择器、事件传播、DOM 查询、
 * 视觉增强属性、伪类、CSS Variables 等。</p>
 */
final class HtmlLikeBrowserSemanticsShowcaseDocumentPageController extends DocumentPageController {

    private final DocumentPageAuthoringSurface documentPage;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;
    private final UiDocument document;
    private TextNode eventLogText;
    private TextNode queryResultText;
    private TextNode themeStatusText;
    private boolean darkTheme = true;

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
                        .setBorderRadius(UiStyleLength.px(8))
                        .setTextColor(0xFFCCDDFF))
                .addRule(".highlight", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF2A3F5F)
                        .setBorderColor(0xFF5B8DEF)
                        .setTextColor(0xFFFFFFFF))
                .addRule(".clickable", new UiStyleDeclaration()
                        .setCursor(UiCursor.POINTER))
                .addRule(".log-area", new UiStyleDeclaration()
                        .setPadding(UiStyleLength.px(8))
                        .setBackgroundColor(0xFF0D1520)
                        .setBorderColor(0xFF1E3050)
                        .setBorderWidth(UiStyleLength.px(1))
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
                .setPadding(UiStyleLength.px(16))
                .setBackgroundColor(0xFF162238)
                .setBorderRadius(UiStyleLength.px(14))
                .setBorderColor(0xFF4A7ADB)
                .setBorderWidth(UiStyleLength.px(1));
        title.appendText("浏览器语义新功能展示");
        title.appendText("展示本次新增的 CSS 选择器、事件传播、DOM 查询、视觉增强、伪类、CSS Variables 等能力。");
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
        box3.style().setTextDecoration(UiTextDecoration.UNDERLINE);
        row.append(box3);

        // 展示特异性：id > class > tag
        ElementNode note = document.div();
        note.setClassName("demo-box");
        note.appendText("级联优先级：inline > #id > .class > tag");
        section.append(note);
    }

    // ========== 事件传播展示 ==========

    private void appendEventPropagationDemo(ElementNode root) {
        ElementNode section = createSection(root, "2. 事件传播模型 (capture/bubble/stopPropagation)");

        ElementNode desc = document.div();
        desc.appendText("点击内层元素，观察事件传播路径和 stopPropagation 效果：");
        section.append(desc);

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        // 外层容器（冒泡 handler）
        ElementNode outer = document.div();
        outer.setClassName("demo-box");
        outer.setId("event-outer");
        outer.appendText("外层（冒泡）");
        outer.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                updateEventLog("冒泡到外层 | phase=" + event.getEventPhase());
                return false;
            }
        });
        row.append(outer);

        // 内层元素（点击触发）
        ElementNode inner = document.div();
        inner.setClassName("demo-box highlight clickable");
        inner.appendText("点击我（冒泡）");
        inner.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                updateEventLog("内层点击 | phase=" + event.getEventPhase());
                return false; // 不阻止冒泡
            }
        });
        outer.append(inner);

        // stopPropagation 示例
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
                return false;
            }
        });
        outer2.append(inner2);

        // 事件日志区
        ElementNode logArea = document.div();
        logArea.setClassName("log-area");
        eventLogText = logArea.appendText("（点击上方元素查看事件日志）");
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
        ElementNode btn1 = document.div();
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
        ElementNode btn2 = document.div();
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
        ElementNode btn3 = document.div();
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
        ElementNode section = createSection(root, "4. 视觉增强属性 (box-shadow / outline / text-decoration)");

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        // box-shadow 展示
        ElementNode shadowBox = document.div();
        shadowBox.setClassName("demo-box");
        shadowBox.style().setBoxShadow(UiBoxShadow.of(3, 3, 10, 0x80000000));
        shadowBox.appendText("box-shadow");
        row.append(shadowBox);

        // box-shadow inset 展示
        ElementNode insetBox = document.div();
        insetBox.setClassName("demo-box");
        insetBox.style().setBoxShadow(UiBoxShadow.inset(2, 2, 8, 0, 0x60000000));
        insetBox.appendText("box-shadow inset");
        row.append(insetBox);

        // outline 展示
        ElementNode outlineBox = document.div();
        outlineBox.setClassName("demo-box");
        outlineBox.style().setOutline(UiOutline.of(2, 0xFF4488FF, UiBorderStyle.SOLID, 2));
        outlineBox.appendText("outline: 2px solid");
        row.append(outlineBox);

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

        // pointer-events 展示
        ElementNode pointerBox = document.div();
        pointerBox.setClassName("demo-box");
        pointerBox.style().setPointerEvents(UiPointerEvents.NONE);
        pointerBox.appendText("pointer-events: none（穿透）");
        row.append(pointerBox);
    }

    // ========== Border 控制展示 ==========

    private void appendBorderControlDemo(ElementNode root) {
        ElementNode section = createSection(root, "5. Border 分边控制 + 分角圆角");

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        // 分角圆角
        ElementNode cornerBox = document.div();
        cornerBox.setClassName("demo-box");
        cornerBox.style().setBorderRadiusCorners(UiBorderRadius.of(
                UiStyleLength.px(16), UiStyleLength.px(4),
                UiStyleLength.px(16), UiStyleLength.px(4)));
        cornerBox.appendText("分角圆角 16/4/16/4");
        row.append(cornerBox);

        // 分边 border-color
        ElementNode colorBox = document.div();
        colorBox.setClassName("demo-box");
        colorBox.style()
                .setBorderColors(UiBorderColors.of(0xFFFF4444, 0xFF44FF44, 0xFF4444FF, 0xFFFFFF44))
                .setBorderWidthSides(UiStyleInsets.of(
                        UiStyleLength.px(3), UiStyleLength.px(3),
                        UiStyleLength.px(3), UiStyleLength.px(3)));
        colorBox.appendText("分边 border-color");
        row.append(colorBox);

        // border-style 展示
        ElementNode dashedBox = document.div();
        dashedBox.setClassName("demo-box");
        dashedBox.style()
                .setBorderStyle(UiBorderStyle.DASHED)
                .setBorderWidth(UiStyleLength.px(2))
                .setBorderColor(0xFF88AAFF);
        dashedBox.appendText("border-style: dashed");
        row.append(dashedBox);

        ElementNode dottedBox = document.div();
        dottedBox.setClassName("demo-box");
        dottedBox.style()
                .setBorderStyle(UiBorderStyle.DOTTED)
                .setBorderWidth(UiStyleLength.px(2))
                .setBorderColor(0xFFFFAA44);
        dottedBox.appendText("border-style: dotted");
        row.append(dottedBox);
    }

    // ========== CSS Variables 展示 ==========

    private void appendCssVariablesDemo(ElementNode root) {
        ElementNode section = createSection(root, "6. CSS Variables (主题切换)");

        ElementNode desc = document.div();
        desc.appendText("点击按钮切换主题，CSS Variables 变更会触发全局样式重算：");
        section.append(desc);

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        // 主题切换按钮
        ElementNode themeBtn = document.div();
        themeBtn.setClassName("demo-box highlight clickable");
        themeBtn.appendText("切换主题 (Dark/Light)");
        themeBtn.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                toggleTheme();
                return true;
            }
        });
        row.append(themeBtn);

        // 使用变量的展示元素
        UiStyleVariables vars = document.getStyleVariables();
        if (vars != null) {
            ElementNode varBox = document.div();
            varBox.setClassName("demo-box");
            varBox.style()
                    .setBackgroundColor(vars.getColor("--bg-elevated"))
                    .setBorderColor(vars.getColor("--primary"));
            varBox.appendText("使用 --primary 和 --bg-elevated 变量");
            row.append(varBox);
        }

        // 主题状态显示
        ElementNode statusArea = document.div();
        statusArea.setClassName("log-area");
        themeStatusText = statusArea.appendText("当前主题: Dark | 变量数: " + getVariableCount());
        section.append(statusArea);
    }

    // ========== 文本排版展示 ==========

    private void appendTextTypographyDemo(ElementNode root) {
        ElementNode section = createSection(root, "7. 文本排版控制 (letter-spacing / word-break)");

        ElementNode row = document.div();
        row.setClassName("demo-row");
        section.append(row);

        // letter-spacing 展示
        ElementNode spacingBox = document.div();
        spacingBox.setClassName("demo-box");
        spacingBox.style().setLetterSpacing(UiStyleLength.px(3));
        spacingBox.appendText("letter-spacing: 3px");
        row.append(spacingBox);

        // word-break 展示
        ElementNode breakBox = document.div();
        breakBox.setClassName("demo-box");
        breakBox.style()
                .setWordBreak(UiWordBreak.BREAK_ALL)
                .setWidth(UiStyleLength.px(120));
        breakBox.appendText("word-break:break-all LongWordThatShouldBreak");
        row.append(breakBox);

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
        ElementNode fitBox = document.div();
        fitBox.setClassName("demo-box");
        fitBox.style().setObjectFit(UiObjectFit.CONTAIN);
        fitBox.appendText("object-fit: contain");
        row.append(fitBox);
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
        if (eventLogText != null) {
            eventLogText.setText("[事件] " + message);
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
     * 切换主题。
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
