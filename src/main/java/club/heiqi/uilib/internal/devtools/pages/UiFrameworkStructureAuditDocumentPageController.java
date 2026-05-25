package club.heiqi.uilib.internal.devtools.pages;

import java.util.Objects;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.screen.page.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiBoxSizing;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiTextAlign;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * UI 框架结构审查内容的专属测试展示页控制器。
 *
 * <p>页面内容来源于 {@code REVIEW-20260520-ui-framework-structure-audit.md} 的审查快照，
 * 用游戏内 HTML-like 页面把分层链路、优先级问题和后续动作做成可视化看板。</p>
 */
public final class UiFrameworkStructureAuditDocumentPageController extends DocumentPageController {

    private static final AuditMetric[] AUDIT_METRICS = new AuditMetric[] {
            new AuditMetric("审查范围", "21 个 UI 子包", "src/main/java/club/heiqi/uilib/ui/**"),
            new AuditMetric("代码规模", "约 187 文件", "约 4.5 万行 UI 层代码"),
            new AuditMetric("核心判断", "方向合理", "保留 DOM/CSS/事件语义主线"),
            new AuditMetric("主要风险", "结构性技术债", "重点是拆分、迁移和约束命名")
    };

    private static final String[] ARCHITECTURE_STEPS = new String[] {
            "dom + style",
            "layout",
            "paint command",
            "renderer",
            "render(GL/FBO)"
    };

    private static final FindingGroup[] FINDING_GROUPS = new FindingGroup[] {
            new FindingGroup("P0", "低风险高收益", 0xFF22C55E, new String[] {
                    "删除 layout/UiAlignSelf 死代码",
                    "抽出 AbstractDocumentElementEvent 基类",
                    "统一事件取消语义",
                    "评估示例 controller 从主产物迁出"
            }),
            new FindingGroup("P1", "包结构整理", 0xFF38BDF8, new String[] {
                    "screen 拆 host / page / example",
                    "style 拆 values / props / cascade / selector",
                    "dom/control 迁入 ui/control",
                    "DocumentCursorHost 归入 ui.host"
            }),
            new FindingGroup("P2", "god class 拆分", 0xFFF59E0B, new String[] {
                    "DocumentLayoutEngine 拆布局子流程",
                    "HtmlLikeDocumentWidget 拆事件/焦点/拖拽/光标",
                    "UiRenderContext 提取裁剪与后处理协作者",
                    "ElementNode 拆事件、焦点与语义桥接"
            }),
            new FindingGroup("P3", "跨切约束", 0xFFF472B6, new String[] {
                    "input 包避免反向依赖 hud / screen",
                    "事件 API 长期收敛到 addEventListener",
                    "host 抽象升级为公共宿主契约",
                    "命名前缀写入使用文档"
            })
    };

    private static final HeatItem[] HEAT_ITEMS = new HeatItem[] {
            new HeatItem("DocumentLayoutEngine", "2964 行", 1.00F, "布局核心：block / flex / table / inline / positioned 混在同一类"),
            new HeatItem("HtmlLikeDocumentWidget", "2107 行", 0.71F, "Widget 桥接：缓存、命中、事件、焦点、拖拽、光标都在主体内"),
            new HeatItem("UiRenderContext", "1376 行", 0.46F, "渲染上下文：裁剪栈、绘制 API、post pass 和 backdrop 调度职责过多"),
            new HeatItem("DocumentAnimationTimeline", "1380 行", 0.46F, "动画时间线：运行状态、事件与样式回写可继续拆协作者"),
            new HeatItem("ElementNode", "1155 行", 0.39F, "节点模型：属性、事件、ARIA、焦点和滚动桥接集中")
    };

    private final DocumentPageAuthoringSurface documentPage;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;

    /**
     * 创建 UI 框架结构审查展示页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param documentPage 文档页面壳
     */
    public UiFrameworkStructureAuditDocumentPageController(DocumentUiScope documentUi,
            DocumentPageAuthoringSurface documentPage) {
        this(Objects.requireNonNull(documentUi, "documentUi"), documentPage, documentUi.getTextMeasureService());
    }

    /**
     * 使用指定文本测量服务创建 UI 框架结构审查展示页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param documentPage 文档页面壳
     * @param textMeasureService HTML-like 文本测量服务
     */
    public UiFrameworkStructureAuditDocumentPageController(DocumentUiScope documentUi,
            DocumentPageAuthoringSurface documentPage, TextMeasureService textMeasureService) {
        Objects.requireNonNull(documentUi, "documentUi");
        this.documentPage = Objects.requireNonNull(documentPage, "documentPage");
        UiDocument document = UiDocument.create();
        document.setDefaultTextContentMode(documentUi.getDefaultTextContentMode());
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 980, 640,
                Objects.requireNonNull(textMeasureService, "textMeasureService"));
        this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));
        buildAuditDocument(document);
    }

    /**
     * 配置页面壳尺寸策略。
     */
    @Override
    public void configureDocumentPage() {
        documentPage.setContentWidthRange(820, 1240)
                .setMinContentHeight(660)
                .setViewportFillRatio(0.95F, 0.93F);
    }

    /**
     * 把 HTML-like 文档挂入页面壳。
     */
    @Override
    public void buildDocument() {
        documentPage.addBlock(htmlLikeDocumentWidget);
    }

    /**
     * 返回当前展示页使用的 HTML-like 文档适配组件。
     *
     * @return HTML-like 文档适配组件
     */
    HtmlLikeDocumentWidget getHtmlLikeDocumentWidget() {
        return htmlLikeDocumentWidget;
    }

    /**
     * 构建结构审查看板文档。
     *
     * @param document HTML-like 文档
     */
    private void buildAuditDocument(UiDocument document) {
        ElementNode root = document.getRootElement();
        root.setAttribute("data-diagnostic-page", "ui-framework-structure-audit");
        root.style()
                .setPadding(UiStyleLength.px(20))
                .setBackgroundColor(0xF0091020)
                .setBorderColor(0xFF7C3AED)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(24))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setTextColor(0xFFE8EEFF);

        appendHero(document, root);
        appendArchitecturePipeline(document, root);
        appendFindingsBoard(document, root);
        appendGodClassHeatmap(document, root);
        appendActionBoard(document, root);
        appendBoundaryNote(document, root);
    }

    /**
     * 追加审查标题与关键指标。
     *
     * @param document HTML-like 文档
     * @param root 根元素
     */
    private void appendHero(UiDocument document, ElementNode root) {
        ElementNode hero = document.div();
        hero.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setColumnGap(UiStyleLength.px(18))
                .setRowGap(UiStyleLength.px(14))
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF8B5CF6)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(20));
        root.append(hero);

        ElementNode copy = document.div();
        copy.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(360))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8));
        hero.append(copy);

        ElementNode tag = appendBadge(document, copy, "REVIEW-20260520", 0xFF312E81, 0xFFC4B5FD);
        tag.style().setWidth(UiStyleLength.px(148));
        appendTitleText(document, copy, "UI 框架结构审查展示", 0xFFFFFFFF);
        appendBodyText(document, copy, "把已完成的 UI 框架结构审查转换成游戏内专属测试展示页，便于在 `/qzuilib test` 中直接检查分层链路、问题优先级与后续重构建议。", 0xFFD8B4FE);
        appendBodyText(document, copy, "页面展示的是审查快照，不直接代表当前源码整改状态；当前状态仍以源码、使用文档和后续审查记录为准。", 0xFFA5B4FC);

        ElementNode metrics = document.div();
        metrics.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(340))
                .setDisplay(UiDisplay.FLEX)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setColumnGap(UiStyleLength.px(10))
                .setRowGap(UiStyleLength.px(10));
        hero.append(metrics);

        for (AuditMetric metric : AUDIT_METRICS) {
            appendMetricCard(document, metrics, metric);
        }
    }

    /**
     * 追加架构分层链路展示区。
     *
     * @param document HTML-like 文档
     * @param root 根元素
     */
    private void appendArchitecturePipeline(UiDocument document, ElementNode root) {
        ElementNode section = appendSection(document, root, "一、主线架构", "审查结论认为核心方向不需要推翻，重点是沿既有浏览器语义主线继续整理结构。", 0xFF38BDF8);

        ElementNode pipeline = document.div();
        pipeline.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8))
                .setRowGap(UiStyleLength.px(8));
        section.append(pipeline);

        for (int index = 0; index < ARCHITECTURE_STEPS.length; index++) {
            appendPipelineStep(document, pipeline, index + 1, ARCHITECTURE_STEPS[index]);
            if (index < ARCHITECTURE_STEPS.length - 1) {
                appendArrow(document, pipeline);
            }
        }

        ElementNode sideNote = document.div();
        sideNote.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(10), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF164E63)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFBAE6FD);
        sideNote.appendText("旁路能力：命中测试独立于绘制回放；DocumentEffectChain 复用 overflow / clip / backdrop / stacking 判定，避免布局、绘制、滚动和命中各自维护隐式顺序。");
        section.append(sideNote);
    }

    /**
     * 追加问题优先级看板。
     *
     * @param document HTML-like 文档
     * @param root 根元素
     */
    private void appendFindingsBoard(UiDocument document, ElementNode root) {
        ElementNode section = appendSection(document, root, "二、问题优先级", "按审查报告中的 P0 到 P3 分组展示结构性技术债，便于逐项回归。", 0xFF22C55E);
        ElementNode columns = document.div();
        columns.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(12));
        section.append(columns);

        for (FindingGroup group : FINDING_GROUPS) {
            appendFindingGroup(document, columns, group);
        }
    }

    /**
     * 追加 god class 热区展示。
     *
     * @param document HTML-like 文档
     * @param root 根元素
     */
    private void appendGodClassHeatmap(UiDocument document, ElementNode root) {
        ElementNode section = appendSection(document, root, "三、God Class 热区", "审查中点名的超大类和接近超大类按相对规模绘制为条形图。", 0xFFF59E0B);
        ElementNode list = document.div();
        list.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(10));
        section.append(list);

        for (HeatItem item : HEAT_ITEMS) {
            appendHeatItem(document, list, item);
        }
    }

    /**
     * 追加后续动作看板。
     *
     * @param document HTML-like 文档
     * @param root 根元素
     */
    private void appendActionBoard(UiDocument document, ElementNode root) {
        ElementNode section = appendSection(document, root, "四、后续动作建议", "报告建议每个 P0/P1 项独立提交，P2 拆分前先补核心引擎单元测试。", 0xFF60A5FA);
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setColumnGap(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(12));
        section.append(row);

        appendActionCard(document, row, "先小后大", "从 P0 死代码清理、事件基类抽取和事件取消语义统一开始，降低回滚成本。", 0xFF1D4ED8);
        appendActionCard(document, row, "只移动不改行为", "P1 包整理以文件移动和 import 调整为主，避免混入行为重构。", 0xFF0F766E);
        appendActionCard(document, row, "拆分前补测试", "P2 涉及布局、渲染和事件主链，先覆盖核心引擎单元测试再拆。", 0xFFB45309);
        appendActionCard(document, row, "文档固化命名", "Document* / Ui* / Internal* / 无前缀类的使用边界需要写入使用文档。", 0xFFBE185D);
    }

    /**
     * 追加审查边界说明。
     *
     * @param document HTML-like 文档
     * @param root 根元素
     */
    private void appendBoundaryNote(UiDocument document, ElementNode root) {
        ElementNode note = document.div();
        note.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(14), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(12))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF475569)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFCBD5E1);
        note.appendText("审查边界：本页不覆盖 uilib/font/** 字体服务，也不把审查正文作为整改状态源；整改进度应写入审查索引、对应使用文档或后续专项记录。");
        root.append(note);
    }

    /**
     * 创建通用内容区段。
     *
     * @param document HTML-like 文档
     * @param root 根元素
     * @param title 区段标题
     * @param summary 区段摘要
     * @param borderColor 区段边框颜色
     * @return 新创建的区段元素
     */
    private ElementNode appendSection(UiDocument document, ElementNode root, String title, String summary,
            int borderColor) {
        ElementNode section = document.div();
        section.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(14), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xEE0F172A)
                .setBorderColor(borderColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(18));
        root.append(section);

        appendTitleText(document, section, title, 0xFFE0F2FE);
        appendBodyText(document, section, summary, 0xFFB6C8E6);
        return section;
    }

    /**
     * 追加指标卡片。
     *
     * @param document HTML-like 文档
     * @param parent 父元素
     * @param metric 指标数据
     */
    private void appendMetricCard(UiDocument document, ElementNode parent, AuditMetric metric) {
        ElementNode card = document.div();
        card.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(154))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF1E1B4B)
                .setBorderColor(0xFF4C1D95)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14));
        parent.append(card);

        appendSmallLabel(document, card, metric.label, 0xFFC4B5FD);
        appendTitleText(document, card, metric.value, 0xFFFFFFFF);
        appendBodyText(document, card, metric.detail, 0xFFA5B4FC);
    }

    /**
     * 追加架构链路节点。
     *
     * @param document HTML-like 文档
     * @param parent 父元素
     * @param index 节点序号
     * @param label 节点文案
     */
    private void appendPipelineStep(UiDocument document, ElementNode parent, int index, String label) {
        ElementNode step = document.div();
        step.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(128))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF123047)
                .setBorderColor(0xFF38BDF8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14))
                .setTextAlign(UiTextAlign.CENTER);
        parent.append(step);
        appendSmallLabel(document, step, "0" + index, 0xFF7DD3FC);
        appendTitleText(document, step, label, 0xFFE0F2FE);
    }

    /**
     * 追加链路箭头。
     *
     * @param document HTML-like 文档
     * @param parent 父元素
     */
    private void appendArrow(UiDocument document, ElementNode parent) {
        ElementNode arrow = document.div();
        arrow.style()
                .setWidth(UiStyleLength.px(28))
                .setTextAlign(UiTextAlign.CENTER)
                .setTextColor(0xFF67E8F9);
        arrow.appendText(">");
        parent.append(arrow);
    }

    /**
     * 追加单个优先级分组。
     *
     * @param document HTML-like 文档
     * @param parent 父元素
     * @param group 分组数据
     */
    private void appendFindingGroup(UiDocument document, ElementNode parent, FindingGroup group) {
        ElementNode column = document.div();
        column.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(226))
                .setPadding(UiStyleLength.px(12))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(group.color)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(16));
        parent.append(column);

        appendBadge(document, column, group.priority + " / " + group.title, 0xFF0B1220, group.color);
        for (int index = 0; index < group.items.length; index++) {
            appendFindingItem(document, column, index + 1, group.items[index], group.color);
        }
    }

    /**
     * 追加优先级条目。
     *
     * @param document HTML-like 文档
     * @param parent 父元素
     * @param index 条目序号
     * @param text 条目文案
     * @param accentColor 强调色
     */
    private void appendFindingItem(UiDocument document, ElementNode parent, int index, String text, int accentColor) {
        ElementNode item = document.div();
        item.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF1E293B)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(10));
        parent.append(item);
        appendSmallLabel(document, item, "#" + index, accentColor);
        appendBodyText(document, item, text, 0xFFE2E8F0);
    }

    /**
     * 追加热区条形项。
     *
     * @param document HTML-like 文档
     * @param parent 父元素
     * @param item 热区数据
     */
    private void appendHeatItem(UiDocument document, ElementNode parent, HeatItem item) {
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(10))
                .setRowGap(UiStyleLength.px(6))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12));
        parent.append(row);

        ElementNode label = document.div();
        label.style()
                .setWidth(UiStyleLength.px(218))
                .setFlexShrink(0.0F);
        appendTitleText(document, label, item.name, 0xFFFFF7ED);
        appendSmallLabel(document, label, item.sizeLabel, 0xFFFCD34D);
        row.append(label);

        ElementNode track = document.div();
        track.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(220))
                .setHeight(UiStyleLength.px(18))
                .setPadding(UiStyleLength.px(2))
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setBackgroundColor(0xFF0B1220)
                .setBorderRadius(UiStyleLength.px(999));
        row.append(track);

        ElementNode bar = document.div();
        bar.style()
                .setWidth(UiStyleLength.percent(item.ratio))
                .setHeight(UiStyleLength.percent(1.0F))
                .setBackgroundColor(0xFFF59E0B)
                .setBorderRadius(UiStyleLength.px(999));
        track.append(bar);

        ElementNode detail = document.div();
        detail.style()
                .setWidth(UiStyleLength.percent(1.0F))
                .setTextColor(0xFFFFE4B5);
        detail.appendText(item.detail);
        row.append(detail);
    }

    /**
     * 追加动作卡片。
     *
     * @param document HTML-like 文档
     * @param parent 父元素
     * @param title 标题
     * @param body 正文
     * @param accentColor 强调色
     */
    private void appendActionCard(UiDocument document, ElementNode parent, String title, String body, int accentColor) {
        ElementNode card = document.div();
        card.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(220))
                .setPadding(UiStyleLength.px(12))
                .setBackgroundColor(0xFF172033)
                .setBorderColor(accentColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14));
        parent.append(card);
        appendTitleText(document, card, title, 0xFFFFFFFF);
        appendBodyText(document, card, body, 0xFFD6E4FF);
    }

    /**
     * 追加胶囊标签。
     *
     * @param document HTML-like 文档
     * @param parent 父元素
     * @param text 标签文本
     * @param backgroundColor 背景色
     * @param textColor 文本色
     * @return 新创建的标签元素
     */
    private ElementNode appendBadge(UiDocument document, ElementNode parent, String text, int backgroundColor,
            int textColor) {
        ElementNode badge = document.div();
        badge.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setPadding(UiStyleInsets.of(UiStyleLength.px(4), UiStyleLength.px(9), UiStyleLength.px(4),
                        UiStyleLength.px(9)))
                .setBackgroundColor(backgroundColor)
                .setBorderColor(textColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(textColor);
        badge.appendText(text);
        parent.append(badge);
        return badge;
    }

    /**
     * 追加标题文本。
     *
     * @param document HTML-like 文档
     * @param parent 父元素
     * @param text 文本
     * @param color 文本色
     */
    private void appendTitleText(UiDocument document, ElementNode parent, String text, int color) {
        ElementNode line = document.div();
        line.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(4), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setFontWeight(UiFontWeight.BOLD)
                .setTextColor(color);
        line.appendText(text);
        parent.append(line);
    }

    /**
     * 追加正文文本。
     *
     * @param document HTML-like 文档
     * @param parent 父元素
     * @param text 文本
     * @param color 文本色
     */
    private void appendBodyText(UiDocument document, ElementNode parent, String text, int color) {
        ElementNode line = document.div();
        line.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(4), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setTextColor(color);
        line.appendText(text);
        parent.append(line);
    }

    /**
     * 追加小号标签文本。
     *
     * @param document HTML-like 文档
     * @param parent 父元素
     * @param text 文本
     * @param color 文本色
     */
    private void appendSmallLabel(UiDocument document, ElementNode parent, String text, int color) {
        ElementNode label = document.div();
        label.style()
                .setTextColor(color)
                .setFontWeight(UiFontWeight.BOLD);
        label.appendText(text);
        parent.append(label);
    }

    /**
     * 审查指标展示数据。
     */
    private static final class AuditMetric {

        private final String label;
        private final String value;
        private final String detail;

        /**
         * 创建审查指标。
         *
         * @param label 指标名
         * @param value 指标值
         * @param detail 指标说明
         */
        private AuditMetric(String label, String value, String detail) {
            this.label = label;
            this.value = value;
            this.detail = detail;
        }
    }

    /**
     * 问题优先级分组展示数据。
     */
    private static final class FindingGroup {

        private final String priority;
        private final String title;
        private final int color;
        private final String[] items;

        /**
         * 创建问题优先级分组。
         *
         * @param priority 优先级
         * @param title 分组标题
         * @param color 分组强调色
         * @param items 分组条目
         */
        private FindingGroup(String priority, String title, int color, String[] items) {
            this.priority = priority;
            this.title = title;
            this.color = color;
            this.items = items;
        }
    }

    /**
     * 大类热区展示数据。
     */
    private static final class HeatItem {

        private final String name;
        private final String sizeLabel;
        private final float ratio;
        private final String detail;

        /**
         * 创建大类热区条目。
         *
         * @param name 类名
         * @param sizeLabel 规模标签
         * @param ratio 相对最大规模的比例
         * @param detail 说明
         */
        private HeatItem(String name, String sizeLabel, float ratio, String detail) {
            this.name = name;
            this.sizeLabel = sizeLabel;
            this.ratio = ratio;
            this.detail = detail;
        }
    }
}
