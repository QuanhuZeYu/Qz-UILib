package club.heiqi.uilib.ui.screen.example;

import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimation;
import club.heiqi.uilib.ui.animation.DocumentAnimationFillMode;
import club.heiqi.uilib.ui.animation.DocumentAnimationOptions;
import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimingFunction;
import club.heiqi.uilib.ui.animation.DocumentKeyframes;
import club.heiqi.uilib.ui.animation.DocumentTransitionSpec;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementAnimationEndEvent;
import club.heiqi.uilib.ui.dom.DocumentElementAnimationEndHandler;
import club.heiqi.uilib.ui.dom.DocumentElementAnimationIterationEvent;
import club.heiqi.uilib.ui.dom.DocumentElementAnimationIterationHandler;
import club.heiqi.uilib.ui.dom.DocumentElementAnimationStartEvent;
import club.heiqi.uilib.ui.dom.DocumentElementAnimationStartHandler;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionCancelEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionCancelHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionEndEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionEndHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionStartEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionStartHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.screen.page.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiAnimationDirection;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.props.UiTextAlign;
import club.heiqi.uilib.ui.style.values.UiBoxShadow;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.values.UiTransform;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * 动画能力成功展示页控制器。
 *
 * <p>页面基于 {@code REVIEW-20260521-animation-capability-assessment.md} 中已完成内容构建，
 * 在 `/qzuilib test` 菜单内集中展示动画系统当前已经打通的框架能力、运行时样例和已知边界。</p>
 */
public final class UiAnimationCapabilityShowcaseDocumentPageController extends DocumentPageController {

    private static final PhaseCard[] PHASE_CARDS = new PhaseCard[] {
            new PhaseCard("Phase 1", "transform + 标准缓动", 0xFF38BDF8, new String[] {
                    "UiTransform 已接入 translate / scale / rotate 与 transform-origin",
                    "标准 cubic-bezier 取代旧枚举缓动，并支持自定义曲线",
                    "transform 子属性按 PAINT 级运行值处理，不触发布局重排"
            }),
            new PhaseCard("Phase 2", "方向与无限迭代", 0xFF22C55E, new String[] {
                    "animation-direction 支持 NORMAL / REVERSE / ALTERNATE / ALTERNATE_REVERSE",
                    "iteration-count = 0 代表 infinite 并保持长期运行",
                    "top / right / bottom / left 与 box-shadow 子属性已补齐可动画覆盖"
            }),
            new PhaseCard("Phase 3", "高级能力首批闭环", 0xFFF59E0B, new String[] {
                    "DocumentTransitionSpec 支持 per-property duration / delay / timing",
                    "steps(...) 缓动已接入离散阶梯动画",
                    "ElementNode.animate(...) 与 animationstart / iteration / end、transitionstart / cancel 已闭环"
            })
    };

    private static final CapabilityGroup[] CAPABILITY_GROUPS = new CapabilityGroup[] {
            new CapabilityGroup("Paint 14 项", 0xFF60A5FA,
                    "background-color, border-color, text-color, border-radius, box-shadow-color, box-shadow-offset-x, box-shadow-offset-y, box-shadow-blur-radius, box-shadow-spread-radius, translate-x, translate-y, scale-x, scale-y, rotate"),
            new CapabilityGroup("Effect 2 项", 0xFFA78BFA,
                    "opacity, backdrop-blur-radius"),
            new CapabilityGroup("Layout 10 项", 0xFFF97316,
                    "width, height, top, right, bottom, left, margin-left, margin-right, padding-left, padding-right")
    };

    private static final FeatureExpectation[] FEATURE_EXPECTATIONS = new FeatureExpectation[] {
            new FeatureExpectation("PAINT", "background-color", "预期：背景色连续插值，只触发重绘，不改变元素尺寸。", 0xFF60A5FA),
            new FeatureExpectation("PAINT", "border-color", "预期：边框颜色连续插值，边框厚度和布局位置不变。", 0xFF60A5FA),
            new FeatureExpectation("PAINT", "text-color", "预期：文字颜色连续插值，文本测量与换行不重算。", 0xFF60A5FA),
            new FeatureExpectation("PAINT", "border-radius", "预期：圆角半径连续变化，命中圆角仍按当前视觉轮廓处理。", 0xFF60A5FA),
            new FeatureExpectation("PAINT", "box-shadow-color", "预期：阴影颜色插值，阴影仍位于元素外侧，不挤压兄弟元素。", 0xFF60A5FA),
            new FeatureExpectation("PAINT", "box-shadow-offset-x", "预期：阴影水平偏移平滑变化，主体盒位置不动。", 0xFF60A5FA),
            new FeatureExpectation("PAINT", "box-shadow-offset-y", "预期：阴影垂直偏移平滑变化，滚动高度不随阴影变化。", 0xFF60A5FA),
            new FeatureExpectation("PAINT", "box-shadow-blur-radius", "预期：阴影模糊范围变化，只影响绘制外观。", 0xFF60A5FA),
            new FeatureExpectation("PAINT", "box-shadow-spread-radius", "预期：阴影扩张范围变化，不改变 border box。", 0xFF60A5FA),
            new FeatureExpectation("PAINT", "translate-x", "预期：元素水平视觉位移且命中同步位移，普通流占位不变。", 0xFF38BDF8),
            new FeatureExpectation("PAINT", "translate-y", "预期：元素垂直视觉位移且命中同步位移，普通流占位不变。", 0xFF38BDF8),
            new FeatureExpectation("PAINT", "scale-x", "预期：元素围绕 transform-origin 水平缩放，兄弟布局不被推开。", 0xFF38BDF8),
            new FeatureExpectation("PAINT", "scale-y", "预期：元素围绕 transform-origin 垂直缩放，兄弟布局不被推开。", 0xFF38BDF8),
            new FeatureExpectation("PAINT", "rotate", "预期：元素旋转且命中测试反向映射，舞台不应裁掉旋转边角。", 0xFF38BDF8),
            new FeatureExpectation("EFFECT", "opacity", "预期：整组透明度合成，子元素互相重叠处不应被重复乘 alpha。", 0xFFA78BFA),
            new FeatureExpectation("EFFECT", "backdrop-blur-radius", "预期：元素背后的内容被采样模糊，元素自身尺寸与布局不变。", 0xFFA78BFA),
            new FeatureExpectation("LAYOUT", "width", "预期：目标盒宽度参与二次布局，后续兄弟水平位置跟随变化。", 0xFFF97316),
            new FeatureExpectation("LAYOUT", "height", "预期：目标盒高度参与二次布局，父级行高和滚动范围按运行值更新。", 0xFFF97316),
            new FeatureExpectation("LAYOUT", "top", "预期：positioned/relative 元素顶部 inset 作为运行值移动视觉与命中。", 0xFFF97316),
            new FeatureExpectation("LAYOUT", "right", "预期：右侧 inset 参与 positioned 约束，元素应按右侧锚点运行值重排。", 0xFFF97316),
            new FeatureExpectation("LAYOUT", "bottom", "预期：底部 inset 参与 positioned 约束，元素应按底部锚点运行值重排。", 0xFFF97316),
            new FeatureExpectation("LAYOUT", "left", "预期：左侧 inset 作为运行值移动 positioned/relative 元素视觉与命中。", 0xFFF97316),
            new FeatureExpectation("LAYOUT", "margin-left", "预期：左外边距变化会改变目标起点并推动同一行后续元素。", 0xFFF97316),
            new FeatureExpectation("LAYOUT", "margin-right", "预期：右外边距变化会改变目标与后续元素之间的间距。", 0xFFF97316),
            new FeatureExpectation("LAYOUT", "padding-left", "预期：左内边距变化会推动内部内容，并影响 content-box 可用宽度。", 0xFFF97316),
            new FeatureExpectation("LAYOUT", "padding-right", "预期：右内边距变化会收缩内容可用宽度，并参与总宽度计算。", 0xFFF97316),
            new FeatureExpectation("RUNTIME", "transitionstart / transitionend", "预期：属性从旧值过渡到新值时派发 start，完成后派发 end。", 0xFFF59E0B),
            new FeatureExpectation("RUNTIME", "transitioncancel", "预期：过渡中关闭声明或被新过渡替代时派发 cancel。", 0xFFF59E0B),
            new FeatureExpectation("RUNTIME", "animationstart / animationiteration / animationend", "预期：有限动画按 start -> iteration -> end 派发；无限动画持续 iteration。", 0xFF22C55E),
            new FeatureExpectation("RUNTIME", "ElementNode.animate(...)", "预期：命令式启动返回句柄，cancel() 后不再继续运行。", 0xFF22C55E)
    };

    private static final TimingCase[] TIMING_CASES = new TimingCase[] {
            new TimingCase("LINEAR", "匀速", "预期：滑块从左到右保持恒定速度。", DocumentAnimationTimingFunction.LINEAR,
                    0xFF38BDF8),
            new TimingCase("EASE", "标准 ease", "预期：起步和收尾更柔和，中段速度更快。", DocumentAnimationTimingFunction.EASE,
                    0xFF60A5FA),
            new TimingCase("EASE_IN_OUT", "先慢后慢", "预期：两端慢、中间快，适合往返展示。", DocumentAnimationTimingFunction.EASE_IN_OUT,
                    0xFFA78BFA),
            new TimingCase("cubicBezier(.22,1,.36,1)", "自定义曲线", "预期：前段快速靠近目标，后段缓慢收束。",
                    DocumentAnimationTimingFunction.cubicBezier(0.22F, 1.0F, 0.36F, 1.0F), 0xFFF59E0B),
            new TimingCase("steps(5,start)", "离散阶梯", "预期：滑块按 5 段跳变，不应连续滑动。",
                    DocumentAnimationTimingFunction.steps(5, DocumentAnimationTimingFunction.StepPosition.START),
                    0xFF22C55E)
    };

    private final DocumentPageAuthoringSurface documentPage;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;
    private final UiDocument document;
    private ElementNode transitionTarget;
    private ElementNode layoutTarget;
    private ElementNode comboLayoutTarget;
    private ElementNode imperativeTarget;
    private TextNode transitionStatusText;
    private TextNode layoutStatusText;
    private TextNode comboLayoutStatusText;
    private TextNode keyframeStatusText;
    private TextNode imperativeStatusText;
    private boolean transitionExpanded;
    private boolean layoutExpanded;
    private boolean comboLayoutExpanded;
    private boolean transitionSpecsEnabled = true;
    private DocumentAnimation imperativeAnimation;

    /**
     * 创建动画能力成功展示页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param documentPage 文档页面壳
     */
    public UiAnimationCapabilityShowcaseDocumentPageController(DocumentUiScope documentUi,
            DocumentPageAuthoringSurface documentPage) {
        this(Objects.requireNonNull(documentUi, "documentUi"), documentPage, documentUi.getTextMeasureService());
    }

    /**
     * 使用指定文本测量服务创建动画能力成功展示页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param documentPage 文档页面壳
     * @param textMeasureService HTML-like 文本测量服务
     */
    public UiAnimationCapabilityShowcaseDocumentPageController(DocumentUiScope documentUi,
            DocumentPageAuthoringSurface documentPage, TextMeasureService textMeasureService) {
        Objects.requireNonNull(documentUi, "documentUi");
        this.documentPage = Objects.requireNonNull(documentPage, "documentPage");
        this.document = UiDocument.create();
        this.document.setDefaultTextContentMode(documentUi.getDefaultTextContentMode());
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 980, 660,
                Objects.requireNonNull(textMeasureService, "textMeasureService"));
        this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));
        registerShowcaseKeyframes();
        buildShowcaseDocument();
    }

    /**
     * 配置页面壳尺寸策略。
     */
    @Override
    public void configureDocumentPage() {
        documentPage.setContentWidthRange(820, 1240)
                .setMinContentHeight(680)
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
     * 注册展示页需要复用的命名 keyframes。
     */
    private void registerShowcaseKeyframes() {
        document.registerKeyframes(DocumentKeyframes.named("phasePulse")
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 0.0F, 0xFF172554)
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 0.5F, 0xFF2563EB)
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 1.0F, 0xFF7C3AED)
                .setFloatStop(DocumentAnimationProperty.TRANSLATE_Y, 0.0F, 0.0F)
                .setFloatStop(DocumentAnimationProperty.TRANSLATE_Y, 0.5F, -8.0F)
                .setFloatStop(DocumentAnimationProperty.TRANSLATE_Y, 1.0F, 0.0F)
                .setFloatStop(DocumentAnimationProperty.SCALE_X, 0.0F, 0.94F)
                .setFloatStop(DocumentAnimationProperty.SCALE_X, 0.5F, 1.06F)
                .setFloatStop(DocumentAnimationProperty.SCALE_X, 1.0F, 0.94F)
                .setFloatStop(DocumentAnimationProperty.SCALE_Y, 0.0F, 0.94F)
                .setFloatStop(DocumentAnimationProperty.SCALE_Y, 0.5F, 1.06F)
                .setFloatStop(DocumentAnimationProperty.SCALE_Y, 1.0F, 0.94F)
                .setFloatStop(DocumentAnimationProperty.ROTATE, 0.0F, -6.0F)
                .setFloatStop(DocumentAnimationProperty.ROTATE, 0.5F, 6.0F)
                .setFloatStop(DocumentAnimationProperty.ROTATE, 1.0F, -6.0F)
                .build());
        document.registerKeyframes(DocumentKeyframes.named("opacityBreath")
                .setFloatStop(DocumentAnimationProperty.OPACITY, 0.0F, 1.0F)
                .setFloatStop(DocumentAnimationProperty.OPACITY, 0.5F, 0.42F)
                .setFloatStop(DocumentAnimationProperty.OPACITY, 1.0F, 1.0F)
                .build());
        document.registerKeyframes(DocumentKeyframes.named("timingSlide")
                .setFloatStop(DocumentAnimationProperty.TRANSLATE_X, 0.0F, 0.0F)
                .setFloatStop(DocumentAnimationProperty.TRANSLATE_X, 1.0F, 128.0F)
                .build());
        document.registerKeyframes(DocumentKeyframes.named("backdropPulse")
                .setFloatStop(DocumentAnimationProperty.BACKDROP_BLUR_RADIUS, 0.0F, 0.0F)
                .setFloatStop(DocumentAnimationProperty.BACKDROP_BLUR_RADIUS, 0.5F, 14.0F)
                .setFloatStop(DocumentAnimationProperty.BACKDROP_BLUR_RADIUS, 1.0F, 0.0F)
                .build());
    }

    /**
     * 构建展示页文档。
     */
    private void buildShowcaseDocument() {
        ElementNode root = document.getRootElement();
        root.setAttribute("data-diagnostic-page", "animation-capability-showcase");
        root.style()
                .setPadding(UiStyleLength.px(20))
                .setBackgroundColor(0xF0081020)
                .setBorderColor(0xFF3B82F6)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(24))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setTextColor(0xFFE8EEFF);

        appendHero(root);
        appendPhaseBoard(root);
        appendCapabilityCoverage(root);
        appendFeatureExpectationBoard(root);
        appendTransitionLab(root);
        appendTimingFunctionBoard(root);
        appendEffectLab(root);
        appendLayoutLab(root);
        appendKeyframeAndImperativeLab(root);
        appendBoundaryBoard(root);
    }

    /**
     * 追加页头摘要区。
     *
     * @param root 根元素
     */
    private void appendHero(ElementNode root) {
        ElementNode hero = document.div();
        hero.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setColumnGap(UiStyleLength.px(18))
                .setRowGap(UiStyleLength.px(14))
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF60A5FA)
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

        ElementNode badge = appendBadge(copy, "REVIEW-20260521", 0xFF1D4ED8, 0xFFDBEAFE);
        badge.style().setWidth(UiStyleLength.px(154));
        appendTitleText(copy, "动画能力成功展示", 0xFFFFFFFF);
        appendBodyText(copy,
                "该页把动画能力评估报告中已经完成的 Phase 1 / 2 / 3 首批闭环内容转换成游戏内展示板，用于直接验证 transform、transition、keyframe、事件与命令式 API 的可用状态。",
                0xFFBFDBFE);
        appendBodyText(copy,
                "架构原则保持不变：动画运行值不回写 inline style，仍以 PAINT / EFFECT / LAYOUT 分层注入布局与绘制链路。",
                0xFF93C5FD);

        ElementNode metrics = document.div();
        metrics.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(320))
                .setDisplay(UiDisplay.FLEX)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setColumnGap(UiStyleLength.px(10))
                .setRowGap(UiStyleLength.px(10));
        hero.append(metrics);

        appendMetricCard(metrics, "已完成阶段", "3 个阶段", "Phase 1 / Phase 2 / Phase 3 首批闭环");
        appendMetricCard(metrics, "属性覆盖", "26 项", "Paint 14 / Effect 2 / Layout 10");
        appendMetricCard(metrics, "运行时入口", "2 类", "声明式 animation + 命令式 animate() handle");
        appendMetricCard(metrics, "生命周期事件", "6 类", "transition start/end/cancel + animation start/iteration/end");
    }

    /**
     * 追加阶段完成情况看板。
     *
     * @param root 根元素
     */
    private void appendPhaseBoard(ElementNode root) {
        ElementNode section = appendSection(root, "一、已完成阶段", "报告中标记为已完成的内容按阶段拆成三组，方便在 test 页直接对照回归。", 0xFF38BDF8);
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(12));
        section.append(row);

        for (PhaseCard phaseCard : PHASE_CARDS) {
            appendPhaseCard(row, phaseCard);
        }
    }

    /**
     * 追加可动画属性覆盖情况。
     *
     * @param root 根元素
     */
    private void appendCapabilityCoverage(ElementNode root) {
        ElementNode section = appendSection(root, "二、26 个已接通属性", "当前运行时覆盖的属性按影响分层分组展示；页面强调的是已真正进入运行时的闭环，而不是仅有声明值承载。", 0xFF22C55E);
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(12));
        section.append(row);

        for (CapabilityGroup group : CAPABILITY_GROUPS) {
            appendCapabilityGroup(row, group);
        }
    }

    /**
     * 追加能力预期行为总表。
     *
     * @param root 根元素
     */
    private void appendFeatureExpectationBoard(ElementNode root) {
        ElementNode section = appendSection(root, "三、功能预期行为总表",
                "每个能力旁边都标注可观察的正确行为：测试时优先看“是否影响布局、是否派发事件、是否保持运行值不回写 inline style”。", 0xFF0EA5E9);
        ElementNode grid = document.div();
        grid.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setColumnGap(UiStyleLength.px(10))
                .setRowGap(UiStyleLength.px(10));
        section.append(grid);

        for (FeatureExpectation expectation : FEATURE_EXPECTATIONS) {
            appendFeatureExpectationCard(grid, expectation);
        }
    }

    /**
     * 追加缓动函数对照区。
     *
     * @param root 根元素
     */
    private void appendTimingFunctionBoard(ElementNode root) {
        ElementNode section = appendSection(root, "五、Timing Function 对照",
                "同一段 translate-x keyframe 使用不同 timing function：观察速度曲线是否符合右侧预期，尤其是 steps(...) 是否离散跳变。", 0xFF06B6D4);
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(12));
        section.append(row);

        for (TimingCase timingCase : TIMING_CASES) {
            appendTimingCaseCard(row, timingCase);
        }
    }

    /**
     * 追加 EFFECT 动画实验区。
     *
     * @param root 根元素
     */
    private void appendEffectLab(ElementNode root) {
        ElementNode section = appendSection(root, "六、Effect 实验区",
                "Effect 类属性单独展示，避免和 transform 混在一个元素上：opacity 用 group opacity 观察，backdrop-blur-radius 用背景条纹观察采样模糊。", 0xFFA78BFA);
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(12));
        section.append(row);

        appendOpacityEffectCard(row);
        appendBackdropEffectCard(row);
    }

    /**
     * 追加 LAYOUT 动画实验区。
     *
     * @param root 根元素
     */
    private void appendLayoutLab(ElementNode root) {
        ElementNode section = appendSection(root, "七、Layout 动画实验区",
                "第一组展示纯 layout 属性如何推动兄弟元素；第二组展示 rotate + layout 组合模拟，并明确标注“兄弟被推开不是 rotate 本身造成”。",
                0xFFF97316);
        ElementNode controls = document.div();
        controls.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setColumnGap(UiStyleLength.px(10))
                .setRowGap(UiStyleLength.px(10));
        section.append(controls);
        controls.append(createActionButton("切换 layout 动画", 0xFFC2410C, 0xFFFDBA74,
                new DocumentElementClickHandler() {
                    @Override
                    public boolean onClick(DocumentElementClickEvent event) {
                        layoutExpanded = !layoutExpanded;
                        applyLayoutState(layoutExpanded);
                        updateText(layoutStatusText, layoutExpanded
                                ? "layout 已切到目标态：目标盒变宽变高，内边距变大，右侧 sibling 应被推开。"
                                : "layout 已回到初始态：兄弟元素应随运行值回收，不残留最终布局。 ");
                        return true;
                    }
                }));
        controls.append(createActionButton("切换 rotate+layout 组合模拟", 0xFF7C2D12, 0xFFFED7AA,
                new DocumentElementClickHandler() {
                    @Override
                    public boolean onClick(DocumentElementClickEvent event) {
                        comboLayoutExpanded = !comboLayoutExpanded;
                        applyComboLayoutState(comboLayoutExpanded);
                        updateText(comboLayoutStatusText, comboLayoutExpanded
                                ? "组合模拟已切到目标态：目标盒会旋转并变宽，右侧 sibling 被推开来自 width/margin 等布局属性。"
                                : "组合模拟已回到初始态：若只看 rotate，本应只改视觉；这里 sibling 回位也来自布局属性回收。 ");
                        return true;
                    }
                }));

        ElementNode stageRow = document.div();
        stageRow.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(12), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(12));
        section.append(stageRow);

        ElementNode lane = document.div();
        lane.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(420))
                .setHeight(UiStyleLength.px(178))
                .setPadding(UiStyleLength.px(16))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(12))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFFF97316)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(16))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE);
        stageRow.append(lane);

        layoutTarget = document.div();
        layoutTarget.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setTextAlign(UiTextAlign.CENTER)
                .setPosition(UiPosition.RELATIVE)
                .setBackgroundColor(0xFF9A3412)
                .setBorderColor(0xFFFED7AA)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(18))
                .setTextColor(0xFFFFFFFF);
        applyLayoutTransitionSpecs(layoutTarget.style());
        applyLayoutState(false);
        lane.append(layoutTarget);

        ElementNode sibling = document.div();
        sibling.style()
                .setWidth(UiStyleLength.px(142))
                .setHeight(UiStyleLength.px(66))
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setTextAlign(UiTextAlign.CENTER)
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFFFB923C)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFFFEDD5);
        sibling.appendText("Sibling\n应被布局动画推开");
        lane.append(sibling);

        ElementNode expectation = createExpectationNote("Layout 预期行为",
                "宽高、左右 margin、左右 padding 应在动画过程中参与布局；top/left 作为 relative 偏移运行值移动视觉与命中，right/bottom 在总表中同类覆盖。",
                0xFFFFEDD5);
        stageRow.append(expectation);

        layoutStatusText = appendLogLine(section,
                "待操作：点击“切换 layout 动画”，观察目标盒变宽、变高、内部文字向右移动，并推动右侧 sibling。 ");

        ElementNode comboRow = document.div();
        comboRow.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(12), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(12));
        section.append(comboRow);

        ElementNode comboLane = document.div();
        comboLane.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(420))
                .setHeight(UiStyleLength.px(188))
                .setPadding(UiStyleLength.px(16))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(12))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFFEA580C)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(16))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE);
        comboRow.append(comboLane);

        comboLayoutTarget = document.div();
        comboLayoutTarget.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setTextAlign(UiTextAlign.CENTER)
                .setPosition(UiPosition.RELATIVE)
                .setBackgroundColor(0xFF7C2D12)
                .setBorderColor(0xFFFED7AA)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(18))
                .setTextColor(0xFFFFFFFF)
                .setTransform(UiTransform.identity());
        applyComboLayoutTransitionSpecs(comboLayoutTarget.style());
        applyComboLayoutState(false);
        comboLane.append(comboLayoutTarget);

        ElementNode comboSibling = document.div();
        comboSibling.style()
                .setWidth(UiStyleLength.px(142))
                .setHeight(UiStyleLength.px(66))
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setTextAlign(UiTextAlign.CENTER)
                .setBackgroundColor(0xFF172033)
                .setBorderColor(0xFFFFB580)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14))
                .setTextColor(0xFFFFEDD5);
        comboSibling.appendText("Sibling\n不是被 rotate 推开");
        comboLane.append(comboSibling);

        ElementNode comboExpectation = createExpectationNote("组合模拟预期",
                "这里同时动画 rotate(PAINT) 与 width/height/margin/padding/top/left(LAYOUT)。右侧 sibling 被推开来自布局属性；如果只保留 rotate，兄弟元素不应移动。",
                0xFFFFEDD5);
        comboRow.append(comboExpectation);

        comboLayoutStatusText = appendLogLine(section,
                "待操作：点击“切换 rotate+layout 组合模拟”，观察目标盒旋转并变宽，同时 sibling 被推开；推动来源是布局属性，不是 rotate 本身。 ");
    }

    /**
     * 追加单个能力预期卡片。
     *
     * @param parent 父元素
     * @param expectation 预期描述
     */
    private void appendFeatureExpectationCard(ElementNode parent, FeatureExpectation expectation) {
        ElementNode card = document.div();
        card.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(236))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(expectation.color)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14));
        parent.append(card);

        appendBadge(card, expectation.group, 0xFF0B1220, expectation.color);
        appendTitleText(card, expectation.feature, 0xFFFFFFFF);
        appendBodyText(card, expectation.expectedBehavior, 0xFFDBEAFE);
    }

    /**
     * 追加单个缓动函数对照卡片。
     *
     * @param parent 父元素
     * @param timingCase 缓动展示数据
     */
    private void appendTimingCaseCard(ElementNode parent, TimingCase timingCase) {
        ElementNode card = document.div();
        card.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(268))
                .setPadding(UiStyleLength.px(12))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(timingCase.color)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(16))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(8));
        parent.append(card);

        appendTitleText(card, timingCase.name, 0xFFE0F2FE);
        appendSmallLabel(card, timingCase.summary, timingCase.color);

        ElementNode track = document.div();
        track.style()
                .setWidth(UiStyleLength.px(184))
                .setHeight(UiStyleLength.px(36))
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(0xFF0B1224)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(999))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE);
        card.append(track);

        ElementNode marker = document.div();
        marker.style()
                .setWidth(UiStyleLength.px(24))
                .setHeight(UiStyleLength.px(24))
                .setBackgroundColor(timingCase.color)
                .setBorderColor(0xFFFFFFFF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(999))
                .setAnimation("timingSlide", 1600L)
                .setAnimationTimingFunction(timingCase.timingFunction)
                .setAnimationDirection(UiAnimationDirection.ALTERNATE)
                .setAnimationIterationCount(0);
        track.append(marker);

        appendBodyText(card, timingCase.expectedBehavior, 0xFFB6C8E6);
    }

    /**
     * 追加 opacity effect 展示卡片。
     *
     * @param parent 父元素
     */
    private void appendOpacityEffectCard(ElementNode parent) {
        ElementNode card = createDemoCard(parent, "Opacity / group opacity", 0xFFA78BFA, 300);
        appendBodyText(card, "展示 opacity-only keyframe，避免与 transform 混合。", 0xFFE9D5FF);

        ElementNode stack = document.div();
        stack.style()
                .setWidth(UiStyleLength.px(230))
                .setHeight(UiStyleLength.px(86))
                .setPosition(UiPosition.RELATIVE)
                .setBackgroundColor(0xFF0B1224)
                .setBorderColor(0xFF6D28D9)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14));
        card.append(stack);

        ElementNode baseStripe = document.div();
        baseStripe.style()
                .setWidth(UiStyleLength.px(194))
                .setHeight(UiStyleLength.px(18))
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(18))
                .setTop(UiStyleLength.px(34))
                .setBackgroundColor(0xFF38BDF8)
                .setBorderRadius(UiStyleLength.px(999));
        stack.append(baseStripe);

        ElementNode opacityGroup = document.div();
        opacityGroup.style()
                .setWidth(UiStyleLength.px(150))
                .setHeight(UiStyleLength.px(52))
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(38))
                .setTop(UiStyleLength.px(17))
                .setOpacity(1.0F)
                .setAnimation("opacityBreath", 1400L)
                .setAnimationTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                .setAnimationIterationCount(0);
        stack.append(opacityGroup);

        ElementNode redLayer = createAbsoluteChip(0, 8, 84, 36, 0xFFFF4B4B, "red");
        ElementNode blueLayer = createAbsoluteChip(52, 8, 84, 36, 0xFF3B82F6, "blue");
        opacityGroup.append(redLayer);
        opacityGroup.append(blueLayer);

        card.append(createExpectationNote("预期行为",
                "红蓝重叠区域应作为整组一起变透明，不应出现子元素重复乘 alpha 的发暗痕迹；周围布局不移动。",
                0xFFE9D5FF));
    }

    /**
     * 追加 backdrop effect 展示卡片。
     *
     * @param parent 父元素
     */
    private void appendBackdropEffectCard(ElementNode parent) {
        ElementNode card = createDemoCard(parent, "Backdrop blur", 0xFFC084FC, 320);
        appendBodyText(card, "展示 backdrop-blur-radius 动画，观察背后条纹是否被动态模糊。", 0xFFE9D5FF);

        ElementNode stage = document.div();
        stage.style()
                .setWidth(UiStyleLength.px(256))
                .setHeight(UiStyleLength.px(104))
                .setPosition(UiPosition.RELATIVE)
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF7C3AED)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(16))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        card.append(stage);

        appendBackdropStripe(stage, 12, 14, 220, 16, 0xFFFBBF24);
        appendBackdropStripe(stage, 30, 42, 190, 16, 0xFF38BDF8);
        appendBackdropStripe(stage, 16, 70, 218, 16, 0xFFFB7185);

        ElementNode glass = document.div();
        glass.style()
                .setWidth(UiStyleLength.px(154))
                .setHeight(UiStyleLength.px(70))
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(52))
                .setTop(UiStyleLength.px(17))
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setTextAlign(UiTextAlign.CENTER)
                .setBackgroundColor(0x66FFFFFF)
                .setBorderColor(0xFFE9D5FF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(18))
                .setBackdropBlurRadius(UiStyleLength.px(0))
                .setAnimation("backdropPulse", 1600L)
                .setAnimationTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                .setAnimationIterationCount(0)
                .setTextColor(0xFFFFFFFF);
        glass.appendText("backdrop\nblur pulse");
        stage.append(glass);

        card.append(createExpectationNote("预期行为",
                "玻璃区域背后的彩色条纹应周期性变糊再恢复清晰；玻璃自身位置、尺寸和文本不应跳动。", 0xFFE9D5FF));
    }

    /**
     * 创建通用演示卡片。
     *
     * @param parent 父元素
     * @param title 标题
     * @param color 强调色
     * @param minWidth 最小宽度
     * @return 演示卡片
     */
    private ElementNode createDemoCard(ElementNode parent, String title, int color, int minWidth) {
        ElementNode card = document.div();
        card.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(minWidth))
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(color)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(18))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(10));
        parent.append(card);
        appendTitleText(card, title, color);
        return card;
    }

    /**
     * 创建预期行为说明卡。
     *
     * @param title 标题
     * @param body 说明正文
     * @param color 文本色
     * @return 预期行为说明元素
     */
    private ElementNode createExpectationNote(String title, String body, int color) {
        ElementNode note = document.div();
        note.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(260))
                .setPadding(UiStyleLength.px(12))
                .setBackgroundColor(0xFF0B1224)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14));
        appendTitleText(note, title, color);
        appendBodyText(note, body, color);
        return note;
    }

    /**
     * 创建 opacity 叠层演示用的绝对定位色块。
     *
     * @param left 左侧偏移
     * @param top 顶部偏移
     * @param width 宽度
     * @param height 高度
     * @param color 背景色
     * @param text 文本
     * @return 色块元素
     */
    private ElementNode createAbsoluteChip(int left, int top, int width, int height, int color, String text) {
        ElementNode chip = document.div();
        chip.style()
                .setWidth(UiStyleLength.px(width))
                .setHeight(UiStyleLength.px(height))
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(left))
                .setTop(UiStyleLength.px(top))
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setBackgroundColor(color)
                .setBorderRadius(UiStyleLength.px(10))
                .setTextColor(0xFFFFFFFF);
        chip.appendText(text);
        return chip;
    }

    /**
     * 追加 backdrop 演示用背景条纹。
     *
     * @param parent 父元素
     * @param left 左侧偏移
     * @param top 顶部偏移
     * @param width 宽度
     * @param height 高度
     * @param color 背景色
     */
    private void appendBackdropStripe(ElementNode parent, int left, int top, int width, int height, int color) {
        ElementNode stripe = document.div();
        stripe.style()
                .setWidth(UiStyleLength.px(width))
                .setHeight(UiStyleLength.px(height))
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(left))
                .setTop(UiStyleLength.px(top))
                .setBackgroundColor(color)
                .setBorderRadius(UiStyleLength.px(999));
        parent.append(stripe);
    }

    /**
     * 应用 layout 演示的 transition 规格。
     *
     * @param style 样式声明
     */
    private void applyLayoutTransitionSpecs(UiStyleDeclaration style) {
        style.setTransitions(
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.WIDTH, 900L, 0L,
                        DocumentAnimationTimingFunction.EASE_IN_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.HEIGHT, 900L, 0L,
                        DocumentAnimationTimingFunction.EASE_IN_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.TOP, 720L, 0L,
                        DocumentAnimationTimingFunction.EASE_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.LEFT, 720L, 0L,
                        DocumentAnimationTimingFunction.EASE_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.MARGIN_LEFT, 900L, 0L,
                        DocumentAnimationTimingFunction.EASE_IN_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.MARGIN_RIGHT, 900L, 0L,
                        DocumentAnimationTimingFunction.EASE_IN_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.PADDING_LEFT, 900L, 0L,
                        DocumentAnimationTimingFunction.EASE_IN_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.PADDING_RIGHT, 900L, 0L,
                        DocumentAnimationTimingFunction.EASE_IN_OUT));
    }

    /**
     * 应用 rotate + layout 组合模拟的 transition 规格。
     *
     * @param style 样式声明
     */
    private void applyComboLayoutTransitionSpecs(UiStyleDeclaration style) {
        style.setTransitions(
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.WIDTH, 960L, 0L,
                        DocumentAnimationTimingFunction.EASE_IN_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.HEIGHT, 960L, 0L,
                        DocumentAnimationTimingFunction.EASE_IN_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.TOP, 760L, 0L,
                        DocumentAnimationTimingFunction.EASE_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.LEFT, 760L, 0L,
                        DocumentAnimationTimingFunction.EASE_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.MARGIN_LEFT, 960L, 0L,
                        DocumentAnimationTimingFunction.EASE_IN_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.MARGIN_RIGHT, 960L, 0L,
                        DocumentAnimationTimingFunction.EASE_IN_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.PADDING_LEFT, 960L, 0L,
                        DocumentAnimationTimingFunction.EASE_IN_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.PADDING_RIGHT, 960L, 0L,
                        DocumentAnimationTimingFunction.EASE_IN_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.ROTATE, 1080L, 0L,
                        DocumentAnimationTimingFunction.cubicBezier(0.22F, 1.0F, 0.36F, 1.0F)),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.SCALE_X, 920L, 0L,
                        DocumentAnimationTimingFunction.EASE_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.SCALE_Y, 920L, 0L,
                        DocumentAnimationTimingFunction.EASE_OUT),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.BACKGROUND_COLOR, 720L, 0L,
                        DocumentAnimationTimingFunction.EASE_IN_OUT));
    }

    /**
     * 应用 layout 演示目标态。
     *
     * @param expanded 是否展开
     */
    private void applyLayoutState(boolean expanded) {
        UiStyleDeclaration style = layoutTarget.style();
        if (expanded) {
            style.setWidth(UiStyleLength.px(230))
                    .setHeight(UiStyleLength.px(92))
                    .setTop(UiStyleLength.px(-8))
                    .setLeft(UiStyleLength.px(16))
                    .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(22), UiStyleLength.px(0),
                            UiStyleLength.px(20)))
                    .setPadding(UiStyleInsets.of(UiStyleLength.px(10), UiStyleLength.px(28), UiStyleLength.px(10),
                            UiStyleLength.px(28)))
                    .setBackgroundColor(0xFFEA580C)
                    .setBorderColor(0xFFFFEDD5);
            layoutTarget.clearChildren();
            layoutTarget.appendText("目标态\nwidth/height/margin/padding/top/left");
            return;
        }
        style.setWidth(UiStyleLength.px(150))
                .setHeight(UiStyleLength.px(66))
                .setTop(UiStyleLength.px(0))
                .setLeft(UiStyleLength.px(0))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(12), UiStyleLength.px(8),
                        UiStyleLength.px(12)))
                .setBackgroundColor(0xFF9A3412)
                .setBorderColor(0xFFFED7AA);
        layoutTarget.clearChildren();
        layoutTarget.appendText("初始态\nlayout 属性");
    }

    /**
     * 应用 rotate + layout 组合模拟的目标态。
     *
     * @param expanded 是否展开
     */
    private void applyComboLayoutState(boolean expanded) {
        UiStyleDeclaration style = comboLayoutTarget.style();
        if (expanded) {
            style.setWidth(UiStyleLength.px(224))
                    .setHeight(UiStyleLength.px(96))
                    .setTop(UiStyleLength.px(-10))
                    .setLeft(UiStyleLength.px(18))
                    .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(24), UiStyleLength.px(0),
                            UiStyleLength.px(18)))
                    .setPadding(UiStyleInsets.of(UiStyleLength.px(10), UiStyleLength.px(24), UiStyleLength.px(10),
                            UiStyleLength.px(24)))
                    .setBackgroundColor(0xFFEA580C)
                    .setBorderColor(0xFFFFEDD5)
                    .setTransform(UiTransform.of(0.0F, 0.0F, 1.05F, 1.05F, 14.0F));
            comboLayoutTarget.clearChildren();
            comboLayoutTarget.appendText("目标态\nrotate + width + margin");
            return;
        }
        style.setWidth(UiStyleLength.px(150))
                .setHeight(UiStyleLength.px(68))
                .setTop(UiStyleLength.px(0))
                .setLeft(UiStyleLength.px(0))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(12), UiStyleLength.px(8),
                        UiStyleLength.px(12)))
                .setBackgroundColor(0xFF7C2D12)
                .setBorderColor(0xFFFED7AA)
                .setTransform(UiTransform.identity());
        comboLayoutTarget.clearChildren();
        comboLayoutTarget.appendText("初始态\nrotate + layout 组合");
    }

    /**
     * 追加 transition 实验区。
     *
     * @param root 根元素
     */
    private void appendTransitionLab(ElementNode root) {
        ElementNode section = appendSection(root, "四、Transition 实验区", "点击按钮切换同一元素的宽度、transform、圆角和 box-shadow，观察 per-property transition、cubic-bezier 与 transition 生命周期事件；opacity 另在下方独立展示，避免和 transform 混合后干扰可视裁切。", 0xFFF59E0B);

        ElementNode controls = document.div();
        controls.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setColumnGap(UiStyleLength.px(10))
                .setRowGap(UiStyleLength.px(10));
        section.append(controls);

        controls.append(createActionButton("触发 transition", 0xFF1D4ED8, 0xFF93C5FD,
                new DocumentElementClickHandler() {
                    @Override
                    public boolean onClick(DocumentElementClickEvent event) {
                        if (!transitionSpecsEnabled) {
                            applyTransitionSpecs(transitionTarget.style(), true);
                            transitionSpecsEnabled = true;
                        }
                        transitionExpanded = !transitionExpanded;
                        applyTransitionState(transitionExpanded);
                        updateText(transitionStatusText, transitionExpanded
                                ? "transition 已进入目标态：width / transform / border-radius / box-shadow 应按不同节奏过渡"
                                : "transition 已回到初始态：再次验证反向插值与事件派发");
                        return true;
                    }
                }));
        controls.append(createActionButton("恢复初始态", 0xFF0F766E, 0xFF99F6E4,
                new DocumentElementClickHandler() {
                    @Override
                    public boolean onClick(DocumentElementClickEvent event) {
                        if (!transitionSpecsEnabled) {
                            applyTransitionSpecs(transitionTarget.style(), true);
                            transitionSpecsEnabled = true;
                        }
                        transitionExpanded = false;
                        applyTransitionState(false);
                        updateText(transitionStatusText, "transition 声明已保留，目标元素回到初始配置");
                        return true;
                    }
                }));
        controls.append(createActionButton("关闭声明触发 cancel", 0xFF9A3412, 0xFFFECACA,
                new DocumentElementClickHandler() {
                    @Override
                    public boolean onClick(DocumentElementClickEvent event) {
                        applyTransitionSpecs(transitionTarget.style(), false);
                        transitionSpecsEnabled = false;
                        updateText(transitionStatusText,
                                "transition 声明已临时关闭；如果元素仍在过渡中，会收到 transitioncancel。再次点击“触发 transition”会恢复声明。 ");
                        return true;
                    }
                }));

        ElementNode stage = document.div();
        stage.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(12), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(14))
                .setRowGap(UiStyleLength.px(12));
        section.append(stage);

        ElementNode targetStage = createAnimationStage("transition-transform", 372, 176, 0xFFF59E0B);
        stage.append(targetStage);

        transitionTarget = document.div();
        transitionTarget.style()
                .setHeight(UiStyleLength.px(112))
                .setPadding(UiStyleLength.px(14))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setTextColor(0xFFFFFFFF)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(4));
        applyTransitionSpecs(transitionTarget.style(), true);
        installTransitionLifecycleHandlers(transitionTarget);
        applyTransitionState(false);
        targetStage.append(transitionTarget);

        ElementNode note = document.div();
        note.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(260))
                .setPadding(UiStyleLength.px(12))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF475569)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14));
        stage.append(note);
        appendTitleText(note, "当前演示点", 0xFFFDE68A);
        appendBodyText(note, "1. width / transform / shadow 走独立 duration 与 timing。", 0xFFE5E7EB);
        appendBodyText(note, "2. 动画运行值不覆盖 inline style，作者声明仍保持最终目标值。", 0xFFE5E7EB);
        appendBodyText(note, "3. transitioncancel 通过撤销允许条件触发，不靠手动清理状态。", 0xFFE5E7EB);

        transitionStatusText = appendLogLine(section,
                "待操作：点击“触发 transition”以验证 per-property transition 与 transitionstart / transitionend；在过渡过程中点击“关闭声明触发 cancel”可观察 transitioncancel。 ");
    }

    /**
     * 追加 keyframe 与命令式动画实验区。
     *
     * @param root 根元素
     */
    private void appendKeyframeAndImperativeLab(ElementNode root) {
        ElementNode section = appendSection(root, "八、Keyframe 与 animate()", "左侧展示声明式 keyframe + infinite iteration + animation-direction，并用独立 opacity-only 条展示透明度动画；右侧展示命令式 animate() 句柄、steps() 缓动和 cancel()。", 0xFFA78BFA);
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(12));
        section.append(row);

        appendDeclarativeAnimationCard(row);
        appendImperativeAnimationCard(row);

        keyframeStatusText = appendLogLine(section,
                "声明式动画默认持续运行：应不断触发 animationiteration；命令式面板点击“启动 animate()”后会返回句柄并允许 cancel()。 ");
        imperativeStatusText = appendLogLine(section,
                "命令式动画待启动：当前尚未调用 ElementNode.animate(...)。 ");
    }

    /**
     * 追加边界说明区。
     *
     * @param root 根元素
     */
    private void appendBoundaryBoard(ElementNode root) {
        ElementNode section = appendSection(root, "九、当前边界", "该页只展示报告中明确已完成并进入运行时的闭环；下列能力仍保持边界状态，避免误导为“浏览器动画已完整等价”。", 0xFFFB7185);
        appendBoundaryItem(section, "未继续扩展更高阶布局属性", "gap、font-size 等布局属性仍未纳入本轮动画覆盖。", 0xFFFFE4E6);
        appendBoundaryItem(section, "keyframe per-stop timing 未实现", "每段 stop 独立 timing function 仍是后续按需评估项。", 0xFFFFE4E6);
        appendBoundaryItem(section, "完整 Web Animations API 未实现", "当前只提供最小 animate() 句柄，不含 pause / reverse / playbackRate / timeline seek。", 0xFFFFE4E6);
        appendBoundaryItem(section, "能力判断仍以真实需求为先", "后续是否继续扩面，仍以性能、验证与业务价值为准，而不是默认追求 CSS 全量对齐。", 0xFFFFE4E6);
    }

    /**
     * 追加声明式动画卡片。
     *
     * @param parent 父元素
     */
    private void appendDeclarativeAnimationCard(ElementNode parent) {
        ElementNode card = document.div();
        card.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(280))
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF8B5CF6)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(18))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(10));
        parent.append(card);

        appendTitleText(card, "声明式 keyframe", 0xFFF5D0FE);
        appendBodyText(card,
                "phasePulse 通过 animation-name 挂载到元素，持续展示 infinite iteration、ALTERNATE 方向、fill-mode 和 animationstart / iteration / end 事件。",
                0xFFE9D5FF);

        ElementNode stage = createAnimationStage("keyframe-transform", 250, 140, 0xFF8B5CF6);
        card.append(stage);

        ElementNode orb = document.div();
        orb.style()
                .setWidth(UiStyleLength.px(168))
                .setHeight(UiStyleLength.px(96))
                .setPadding(UiStyleLength.px(12))
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setTextAlign(UiTextAlign.CENTER)
                .setBackgroundColor(0xFF172554)
                .setBorderColor(0xFFC4B5FD)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(24))
                .setTransform(UiTransform.of(0.0F, 0.0F, 0.94F, 0.94F, -6.0F))
                .setAnimation("phasePulse", 1800L)
                .setAnimationTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                .setAnimationFillMode(DocumentAnimationFillMode.BOTH)
                .setAnimationDirection(UiAnimationDirection.ALTERNATE)
                .setAnimationIterationCount(0);
        installDeclarativeLifecycleHandlers(orb);
        orb.appendText("animation-direction: alternate\ninfinite iteration");
        stage.append(orb);

        ElementNode opacityProbe = document.div();
        opacityProbe.style()
                .setWidth(UiStyleLength.px(220))
                .setHeight(UiStyleLength.px(30))
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setTextAlign(UiTextAlign.CENTER)
                .setBackgroundColor(0xFF4C1D95)
                .setBorderColor(0xFFD8B4FE)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(999))
                .setOpacity(1.0F)
                .setAnimation("opacityBreath", 1400L)
                .setAnimationTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                .setAnimationIterationCount(0);
        opacityProbe.appendText("Opacity 单独展示：opacity-only keyframe");
        card.append(opacityProbe);

        appendBodyText(card,
                "补充说明：REVERSE / ALTERNATE_REVERSE 同样可用；transform 舞台与 opacity-only 样例拆开展示，避免离屏透明合成干扰旋转内容。",
                0xFFD8B4FE);
    }

    /**
     * 追加命令式动画卡片。
     *
     * @param parent 父元素
     */
    private void appendImperativeAnimationCard(ElementNode parent) {
        ElementNode card = document.div();
        card.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(320))
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF22C55E)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(18))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(10));
        parent.append(card);

        appendTitleText(card, "命令式 animate()", 0xFFBBF7D0);
        appendBodyText(card,
                "点击按钮通过 ElementNode.animate(keyframes, options) 启动一次命令式动画；此处额外使用 steps(5, start)、delay、ALTERNATE_REVERSE、fill-mode:forwards 与句柄 cancel()。",
                0xFFD1FAE5);

        ElementNode stage = createAnimationStage("imperative-transform", 292, 156, 0xFF22C55E);
        card.append(stage);

        imperativeTarget = document.div();
        imperativeTarget.style()
                .setWidth(UiStyleLength.px(188))
                .setHeight(UiStyleLength.px(96))
                .setPadding(UiStyleLength.px(12))
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setTextAlign(UiTextAlign.CENTER)
                .setBackgroundColor(0xFF14532D)
                .setBorderColor(0xFF86EFAC)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(18))
                .setOpacity(1.0F)
                .setTransform(UiTransform.identity());
        installImperativeLifecycleHandlers(imperativeTarget);
        imperativeTarget.appendText("animate() handle\nsteps(5, start)");
        stage.append(imperativeTarget);

        ElementNode controls = document.div();
        controls.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setColumnGap(UiStyleLength.px(10))
                .setRowGap(UiStyleLength.px(10));
        card.append(controls);

        controls.append(createActionButton("启动 animate()", 0xFF166534, 0xFF86EFAC,
                new DocumentElementClickHandler() {
                    @Override
                    public boolean onClick(DocumentElementClickEvent event) {
                        if (imperativeAnimation != null && imperativeAnimation.isRunning()) {
                            imperativeAnimation.cancel();
                        }
                        imperativeAnimation = imperativeTarget.animate(buildImperativeKeyframes(),
                                DocumentAnimationOptions.builder()
                                        .setDurationMillis(1100L)
                                        .setDelayMillis(120L)
                                        .setIterationCount(2)
                                        .setFillMode(DocumentAnimationFillMode.FORWARDS)
                                        .setTimingFunction(DocumentAnimationTimingFunction.steps(5,
                                                DocumentAnimationTimingFunction.StepPosition.START))
                                        .setDirection(UiAnimationDirection.ALTERNATE_REVERSE)
                                        .build());
                        updateText(imperativeStatusText, "animate() 已调用：name="
                                + imperativeAnimation.getAnimationName() + "，running="
                                + imperativeAnimation.isRunning() + "，duration="
                                + (imperativeAnimation.getDurationNanos() / 1_000_000L) + "ms");
                        return true;
                    }
                }));
        controls.append(createActionButton("cancel()", 0xFF7F1D1D, 0xFFFECACA,
                new DocumentElementClickHandler() {
                    @Override
                    public boolean onClick(DocumentElementClickEvent event) {
                        boolean cancelled = imperativeAnimation != null && imperativeAnimation.cancel();
                        updateText(imperativeStatusText, "cancel() 结果=" + cancelled + "，handleRunning="
                                + (imperativeAnimation != null && imperativeAnimation.isRunning()));
                        return true;
                    }
                }));
    }

    /**
     * 为 transition 目标元素安装生命周期处理器。
     *
     * @param target 目标元素
     */
    private void installTransitionLifecycleHandlers(ElementNode target) {
        target.setTransitionStartHandler(new DocumentElementTransitionStartHandler() {
            @Override
            public boolean onTransitionStart(DocumentElementTransitionStartEvent event) {
                updateText(transitionStatusText, "transitionstart: property=" + event.getProperty()
                        + "，elapsed=" + (event.getElapsedTimeNanos() / 1_000_000L) + "ms");
                return false;
            }
        });
        target.setTransitionEndHandler(new DocumentElementTransitionEndHandler() {
            @Override
            public boolean onTransitionEnd(DocumentElementTransitionEndEvent event) {
                updateText(transitionStatusText, "transitionend: property=" + event.getProperty()
                        + "，elapsed=" + (event.getElapsedTimeNanos() / 1_000_000L) + "ms");
                return false;
            }
        });
        target.setTransitionCancelHandler(new DocumentElementTransitionCancelHandler() {
            @Override
            public boolean onTransitionCancel(DocumentElementTransitionCancelEvent event) {
                updateText(transitionStatusText, "transitioncancel: property=" + event.getProperty()
                        + "，elapsed=" + (event.getElapsedTimeNanos() / 1_000_000L) + "ms");
                return false;
            }
        });
    }

    /**
     * 为声明式 keyframe 元素安装生命周期处理器。
     *
     * @param target 目标元素
     */
    private void installDeclarativeLifecycleHandlers(ElementNode target) {
        target.setAnimationStartHandler(new DocumentElementAnimationStartHandler() {
            @Override
            public boolean onAnimationStart(DocumentElementAnimationStartEvent event) {
                updateText(keyframeStatusText, "animationstart: name=" + event.getAnimationName()
                        + "，elapsed=" + (event.getElapsedTimeNanos() / 1_000_000L) + "ms");
                return false;
            }
        });
        target.setAnimationIterationHandler(new DocumentElementAnimationIterationHandler() {
            @Override
            public boolean onAnimationIteration(DocumentElementAnimationIterationEvent event) {
                updateText(keyframeStatusText, "animationiteration: name=" + event.getAnimationName()
                        + "，iteration=" + event.getIterationIndex());
                return false;
            }
        });
        target.setAnimationEndHandler(new DocumentElementAnimationEndHandler() {
            @Override
            public boolean onAnimationEnd(DocumentElementAnimationEndEvent event) {
                updateText(keyframeStatusText, "animationend: name=" + event.getAnimationName()
                        + "，elapsed=" + (event.getElapsedTimeNanos() / 1_000_000L) + "ms");
                return false;
            }
        });
    }

    /**
     * 为命令式动画目标安装生命周期处理器。
     *
     * @param target 目标元素
     */
    private void installImperativeLifecycleHandlers(ElementNode target) {
        target.setAnimationStartHandler(new DocumentElementAnimationStartHandler() {
            @Override
            public boolean onAnimationStart(DocumentElementAnimationStartEvent event) {
                updateText(imperativeStatusText, "animate() animationstart: name=" + event.getAnimationName()
                        + "，elapsed=" + (event.getElapsedTimeNanos() / 1_000_000L) + "ms");
                return false;
            }
        });
        target.setAnimationIterationHandler(new DocumentElementAnimationIterationHandler() {
            @Override
            public boolean onAnimationIteration(DocumentElementAnimationIterationEvent event) {
                updateText(imperativeStatusText, "animate() animationiteration: name="
                        + event.getAnimationName() + "，iteration=" + event.getIterationIndex());
                return false;
            }
        });
        target.setAnimationEndHandler(new DocumentElementAnimationEndHandler() {
            @Override
            public boolean onAnimationEnd(DocumentElementAnimationEndEvent event) {
                updateText(imperativeStatusText, "animate() animationend: name=" + event.getAnimationName()
                        + "，elapsed=" + (event.getElapsedTimeNanos() / 1_000_000L) + "ms");
                return false;
            }
        });
    }

    /**
     * 按目标状态应用 transition 演示样式。
     *
     * @param expanded 是否切到目标态
     */
    private void applyTransitionState(boolean expanded) {
        UiStyleDeclaration style = transitionTarget.style();
        if (expanded) {
            style.setWidth(UiStyleLength.px(320))
                    .setBackgroundColor(0xFF7C3AED)
                    .setBorderColor(0xFFE9D5FF)
                    .setBorderRadius(UiStyleLength.px(30))
                    .setTransform(UiTransform.of(28.0F, -6.0F, 1.08F, 1.08F, 10.0F))
                    .setBoxShadow(UiBoxShadow.of(0, 18, 28, 6, 0x883B82F6));
            transitionTarget.clearChildren();
            transitionTarget.appendText("目标态\nper-property transition");
            return;
        }
        style.setWidth(UiStyleLength.px(188))
                .setBackgroundColor(0xFF1D4ED8)
                .setBorderColor(0xFF93C5FD)
                .setBorderRadius(UiStyleLength.px(14))
                .setTransform(UiTransform.identity())
                .setBoxShadow(UiBoxShadow.of(0, 8, 18, 0, 0x66060F1E));
        transitionTarget.clearChildren();
        transitionTarget.appendText("初始态\nwidth + transform");
    }

    /**
     * 为 transition 目标应用或关闭 per-property 规格。
     *
     * @param style 目标样式声明
     * @param enabled 是否启用
     */
    private void applyTransitionSpecs(UiStyleDeclaration style, boolean enabled) {
        if (enabled) {
            style.setTransitions(
                    DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.WIDTH, 960L, 0L,
                            DocumentAnimationTimingFunction.cubicBezier(0.22F, 1.0F, 0.36F, 1.0F)),
                    DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.TRANSLATE_X, 900L, 0L,
                            DocumentAnimationTimingFunction.EASE_OUT),
                    DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.ROTATE, 1200L, 0L,
                            DocumentAnimationTimingFunction.cubicBezier(0.25F, 0.10F, 0.25F, 1.0F)),
                    DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.SCALE_X, 900L, 0L,
                            DocumentAnimationTimingFunction.EASE_OUT),
                    DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.SCALE_Y, 900L, 0L,
                            DocumentAnimationTimingFunction.EASE_OUT),
                    DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.BACKGROUND_COLOR, 680L, 0L,
                            DocumentAnimationTimingFunction.EASE_IN_OUT),
                    DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.BORDER_RADIUS, 760L, 0L,
                            DocumentAnimationTimingFunction.EASE_IN_OUT),
                    DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.BOX_SHADOW_OFFSET_Y, 760L, 0L,
                            DocumentAnimationTimingFunction.EASE_OUT),
                    DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.BOX_SHADOW_BLUR_RADIUS, 760L, 0L,
                            DocumentAnimationTimingFunction.EASE_OUT),
                    DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.BOX_SHADOW_SPREAD_RADIUS, 760L, 0L,
                            DocumentAnimationTimingFunction.EASE_OUT),
                    DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.BOX_SHADOW_COLOR, 760L, 0L,
                            DocumentAnimationTimingFunction.EASE_OUT));
            return;
        }
        style.setTransitions(
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.WIDTH, 0L),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.TRANSLATE_X, 0L),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.ROTATE, 0L),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.SCALE_X, 0L),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.SCALE_Y, 0L),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.BACKGROUND_COLOR, 0L),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.BORDER_RADIUS, 0L),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.BOX_SHADOW_OFFSET_Y, 0L),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.BOX_SHADOW_BLUR_RADIUS, 0L),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.BOX_SHADOW_SPREAD_RADIUS, 0L),
                DocumentTransitionSpec.ofMillis(DocumentAnimationProperty.BOX_SHADOW_COLOR, 0L));
    }

    /**
     * 构建命令式动画 keyframes。
     *
     * @return 动画定义
     */
    private DocumentKeyframes buildImperativeKeyframes() {
        return DocumentKeyframes.named("commandBurst")
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 0.0F, 0xFF14532D)
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 0.5F, 0xFF0EA5E9)
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 1.0F, 0xFF14532D)
                .setFloatStop(DocumentAnimationProperty.TRANSLATE_X, 0.0F, 0.0F)
                .setFloatStop(DocumentAnimationProperty.TRANSLATE_X, 0.5F, 20.0F)
                .setFloatStop(DocumentAnimationProperty.TRANSLATE_X, 1.0F, 0.0F)
                .setFloatStop(DocumentAnimationProperty.ROTATE, 0.0F, 0.0F)
                .setFloatStop(DocumentAnimationProperty.ROTATE, 0.5F, 12.0F)
                .setFloatStop(DocumentAnimationProperty.ROTATE, 1.0F, 0.0F)
                .setFloatStop(DocumentAnimationProperty.SCALE_X, 0.0F, 1.0F)
                .setFloatStop(DocumentAnimationProperty.SCALE_X, 0.5F, 1.08F)
                .setFloatStop(DocumentAnimationProperty.SCALE_X, 1.0F, 1.0F)
                .setFloatStop(DocumentAnimationProperty.SCALE_Y, 0.0F, 1.0F)
                .setFloatStop(DocumentAnimationProperty.SCALE_Y, 0.5F, 1.08F)
                .setFloatStop(DocumentAnimationProperty.SCALE_Y, 1.0F, 1.0F)
                .build();
    }

    /**
     * 追加阶段卡片。
     *
     * @param parent 父元素
     * @param phaseCard 阶段数据
     */
    private void appendPhaseCard(ElementNode parent, PhaseCard phaseCard) {
        ElementNode card = document.div();
        card.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(236))
                .setPadding(UiStyleLength.px(12))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(phaseCard.color)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(16));
        parent.append(card);

        appendBadge(card, phaseCard.label, 0xFF0B1220, phaseCard.color);
        appendTitleText(card, phaseCard.title, 0xFFFFFFFF);
        for (String line : phaseCard.lines) {
            appendBodyText(card, line, 0xFFE5E7EB);
        }
    }

    /**
     * 追加属性覆盖卡片。
     *
     * @param parent 父元素
     * @param group 分组数据
     */
    private void appendCapabilityGroup(ElementNode parent, CapabilityGroup group) {
        ElementNode card = document.div();
        card.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(250))
                .setPadding(UiStyleLength.px(12))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(group.color)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(16));
        parent.append(card);

        appendTitleText(card, group.title, 0xFFFFFFFF);
        appendBodyText(card, group.body, 0xFFDBEAFE);
    }

    /**
     * 追加边界条目。
     *
     * @param parent 父元素
     * @param title 标题
     * @param body 正文
     * @param color 文本色
     */
    private void appendBoundaryItem(ElementNode parent, String title, String body, int color) {
        ElementNode item = document.div();
        item.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(10), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF7F1D1D)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12));
        parent.append(item);
        appendTitleText(item, title, 0xFFFFE4E6);
        appendBodyText(item, body, color);
    }

    /**
     * 创建通用区段。
     *
     * @param root 根元素
     * @param title 标题
     * @param summary 摘要
     * @param borderColor 边框色
     * @return 区段元素
     */
    private ElementNode appendSection(ElementNode root, String title, String summary, int borderColor) {
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

        appendTitleText(section, title, 0xFFE0F2FE);
        appendBodyText(section, summary, 0xFFB6C8E6);
        return section;
    }

    /**
     * 追加指标卡片。
     *
     * @param parent 父元素
     * @param label 标签
     * @param value 主值
     * @param detail 说明
     */
    private void appendMetricCard(ElementNode parent, String label, String value, String detail) {
        ElementNode card = document.div();
        card.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(146))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF172033)
                .setBorderColor(0xFF2563EB)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14));
        parent.append(card);

        appendSmallLabel(card, label, 0xFF93C5FD);
        appendTitleText(card, value, 0xFFFFFFFF);
        appendBodyText(card, detail, 0xFFDBEAFE);
    }

    /**
     * 创建为 transform 动画预留安全边界的舞台。
     *
     * @param key 舞台诊断标识
     * @param width 舞台宽度
     * @param height 舞台高度
     * @param borderColor 边框色
     * @return 动画舞台元素
     */
    private ElementNode createAnimationStage(String key, int width, int height, int borderColor) {
        ElementNode stage = document.div();
        stage.setAttribute("data-animation-stage", key);
        stage.style()
                .setWidth(UiStyleLength.px(width))
                .setHeight(UiStyleLength.px(height))
                .setPadding(UiStyleLength.px(10))
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setBackgroundColor(0xFF0B1224)
                .setBorderColor(borderColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(16))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE);
        return stage;
    }

    /**
     * 创建操作按钮。
     *
     * @param text 按钮文本
     * @param backgroundColor 背景色
     * @param borderColor 边框色
     * @param clickHandler 点击处理器
     * @return 按钮元素
     */
    private ElementNode createActionButton(String text, int backgroundColor, int borderColor,
            DocumentElementClickHandler clickHandler) {
        ElementNode button = document.button();
        button.style()
                .setPadding(UiStyleInsets.of(UiStyleLength.px(7), UiStyleLength.px(12), UiStyleLength.px(7),
                        UiStyleLength.px(12)))
                .setBackgroundColor(backgroundColor)
                .setBorderColor(borderColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(999))
                .setTextColor(0xFFFFFFFF)
                .setFontWeight(UiFontWeight.BOLD);
        button.appendText(text);
        button.setClickHandler(clickHandler);
        return button;
    }

    /**
     * 追加日志行。
     *
     * @param parent 父元素
     * @param text 初始文本
     * @return 文本节点
     */
    private TextNode appendLogLine(ElementNode parent, String text) {
        ElementNode log = document.div();
        log.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(12), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF111827)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setTextColor(0xFFCBD5E1);
        parent.append(log);
        return log.appendText(text);
    }

    /**
     * 追加胶囊标签。
     *
     * @param parent 父元素
     * @param text 文案
     * @param backgroundColor 背景色
     * @param textColor 文本色
     * @return 标签元素
     */
    private ElementNode appendBadge(ElementNode parent, String text, int backgroundColor, int textColor) {
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
     * @param parent 父元素
     * @param text 文案
     * @param color 文本色
     */
    private void appendTitleText(ElementNode parent, String text, int color) {
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
     * @param parent 父元素
     * @param text 文案
     * @param color 文本色
     */
    private void appendBodyText(ElementNode parent, String text, int color) {
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
     * @param parent 父元素
     * @param text 文案
     * @param color 文本色
     */
    private void appendSmallLabel(ElementNode parent, String text, int color) {
        ElementNode label = document.div();
        label.style()
                .setTextColor(color)
                .setFontWeight(UiFontWeight.BOLD);
        label.appendText(text);
        parent.append(label);
    }

    /**
     * 更新日志文本。
     *
     * @param textNode 文本节点
     * @param text 新文本
     */
    private void updateText(TextNode textNode, String text) {
        if (textNode != null) {
            textNode.setText(text);
        }
    }

    /**
     * 阶段卡片数据。
     */
    private static final class PhaseCard {

        private final String label;
        private final String title;
        private final int color;
        private final String[] lines;

        /**
         * 创建阶段卡片数据。
         *
         * @param label 阶段标签
         * @param title 阶段标题
         * @param color 强调色
         * @param lines 要点列表
         */
        private PhaseCard(String label, String title, int color, String[] lines) {
            this.label = label;
            this.title = title;
            this.color = color;
            this.lines = lines;
        }
    }

    /**
     * 属性覆盖分组数据。
     */
    private static final class CapabilityGroup {

        private final String title;
        private final int color;
        private final String body;

        /**
         * 创建属性覆盖分组。
         *
         * @param title 标题
         * @param color 强调色
         * @param body 正文
         */
        private CapabilityGroup(String title, int color, String body) {
            this.title = title;
            this.color = color;
            this.body = body;
        }
    }

    /**
     * 单项动画能力的预期行为描述。
     */
    private static final class FeatureExpectation {

        private final String group;
        private final String feature;
        private final String expectedBehavior;
        private final int color;

        /**
         * 创建能力预期描述。
         *
         * @param group 能力分组
         * @param feature 功能名
         * @param expectedBehavior 预期行为
         * @param color 强调色
         */
        private FeatureExpectation(String group, String feature, String expectedBehavior, int color) {
            this.group = group;
            this.feature = feature;
            this.expectedBehavior = expectedBehavior;
            this.color = color;
        }
    }

    /**
     * 缓动函数展示数据。
     */
    private static final class TimingCase {

        private final String name;
        private final String summary;
        private final String expectedBehavior;
        private final DocumentAnimationTimingFunction timingFunction;
        private final int color;

        /**
         * 创建缓动函数展示数据。
         *
         * @param name 名称
         * @param summary 摘要
         * @param expectedBehavior 预期行为
         * @param timingFunction 缓动函数
         * @param color 强调色
         */
        private TimingCase(String name, String summary, String expectedBehavior,
                DocumentAnimationTimingFunction timingFunction, int color) {
            this.name = name;
            this.summary = summary;
            this.expectedBehavior = expectedBehavior;
            this.timingFunction = timingFunction;
            this.color = color;
        }
    }
}
