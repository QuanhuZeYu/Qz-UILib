package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageRuntimeView;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.lwjgl.opengl.GL11;

import club.heiqi.uilib.font.FontRuntimeStats;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.ui.diagnostic.UiRuntimeStats;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.control.DocumentSegmentedSelectionEvent;
import club.heiqi.uilib.ui.control.DocumentSegmentedSelectionHandler;
import club.heiqi.uilib.ui.control.DocumentSegmentedSelectorControl;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * 字体性能基线诊断页控制器。
 *
 * <p>本页只作为内部 GUI 压测工具使用，通过固定尺寸自定义绘制区域把大量字符压缩到可视范围内，
 * 不扩展对外文档 API，也不触碰字体底层渲染实现。</p>
 */
public final class UiFontPerformanceBaselineDocumentPageController extends DocumentPageController {

    private static final long DEBUG_REFRESH_INTERVAL_NANOS = 500_000_000L;
    private static final int[] COUNT_PRESETS = new int[] { 1000, 5000, 10000, 20000 };
    private static final String SCENE_ASCII = "ASCII 热渲染";
    private static final String SCENE_CHINESE = "中文常用字";
    private static final String SCENE_MIXED = "中英符号混排";
    private static final String SCENE_UNIQUE = "唯一字符冷启动";
    private static final String SCENE_MINECRAFT_RANDOM = "格式码随机样式";
    private static final String[] SCENE_OPTIONS = new String[] {
            SCENE_ASCII,
            SCENE_CHINESE,
            SCENE_MIXED,
            SCENE_UNIQUE,
            SCENE_MINECRAFT_RANDOM
    };
    private static final String[] COUNT_OPTIONS = new String[] { "1000", "5000", "10000", "20000" };
    private static final String[] MINECRAFT_ROW_COLORS = new String[] {
            "§a", "§b", "§e", "§d", "§f", "§6"
    };
    private static final String ASCII_PATTERN = "QzUILib ASCII hot render 0123456789 ABCDEF xyz +-*/[]{}<> ";
    private static final String CHINESE_PATTERN = "的一是在不了有和人这中大为上个国我以要他时来用们生到作地于出就分对成会可主发年动同工也能下过子说产种面而方后多定行学法所民得经十三之进着等部度家电力里如水化高自二理起小物现实加量都两体制机当使点从业本去把性好应开它合还因由其些然前外天政四日那社义事平形相全表间样与关各重新线内数正心反你明看原又么利比或但质气第向道命此变条只没结解问意建月公无系军很情者最立代想已通并提直题党程展五果料象员革位入常文总次品式活设及管特件长求老头基资边流路级少图山统接知较将组见计别她手角期根论运农指几九区强放决西被干做必战先回则任取据处理世告很情";
    private static final String MIXED_PATTERN = "Qz-UILib 字体 Baseline 0123456789 ABC xyz ΔλΩ -> []{}() #$%&!? 中文混排：渲染、缓存、上传、四边形。";
    private static final String RANDOM_STYLE_PATTERN = "QzUILib Minecraft random style 0123456789 ABC xyz 中文格式码随机样式 ";

    private final DocumentPageAuthoringSurface diagnosticPage;
    private final DocumentPageRuntimeView runtimeView;
    private final String screenName;
    private final FontRuntimeStatsSource fontRuntimeStatsSource;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;
    private final DocumentSegmentedSelectorControl sceneSelector;
    private final DocumentSegmentedSelectorControl countSelector;
    private final DocumentButtonControl rebuildButton;
    private final DocumentButtonControl pauseButton;
    private final TextNode stateText;
    private final TextNode fontStatsText;
    private final TextNode runtimeFrameText;
    private final TextNode runtimeHotspotText;
    private final TextNode runtimePhaseText;
    private final TextNode rendererNoteText;

    private StressPayload currentPayload;
    private boolean running = true;
    private int generation;
    private long lastDebugRefreshNanos = Long.MIN_VALUE;

    public UiFontPerformanceBaselineDocumentPageController(DocumentUiScope documentUi,
            DocumentPageAuthoringSurface diagnosticPage, DocumentPageRuntimeView runtimeView, String screenName) {
        this(documentUi, diagnosticPage, runtimeView, screenName, new FontRuntimeStatsSource() {
            @Override
            public FontRuntimeStats getRuntimeStats() {
                return FontService.getInstance().getRuntimeStats();
            }
        });
    }

    public UiFontPerformanceBaselineDocumentPageController(DocumentUiScope documentUi,
            DocumentPageAuthoringSurface diagnosticPage, DocumentPageRuntimeView runtimeView, String screenName,
            FontRuntimeStatsSource fontRuntimeStatsSource) {
        DocumentUiScope resolvedDocumentUi = Objects.requireNonNull(documentUi, "documentUi");
        this.diagnosticPage = Objects.requireNonNull(diagnosticPage, "diagnosticPage");
        this.runtimeView = Objects.requireNonNull(runtimeView, "runtimeView");
        this.screenName = Objects.requireNonNull(screenName, "screenName");
        this.fontRuntimeStatsSource = Objects.requireNonNull(fontRuntimeStatsSource, "fontRuntimeStatsSource");

        UiDocument document = UiDocument.create();
        document.setDefaultTextContentMode(resolvedDocumentUi.getDefaultTextContentMode());
        this.sceneSelector = new DocumentSegmentedSelectorControl(document, SCENE_OPTIONS);
        this.countSelector = new DocumentSegmentedSelectorControl(document, COUNT_OPTIONS);
        this.rebuildButton = new DocumentButtonControl(document, "重建文本");
        this.pauseButton = new DocumentButtonControl(document, "暂停");
        configureControls();

        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 920, 680,
                resolvedDocumentUi.getTextMeasureService());
        this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));

        DocumentBundle bundle = createDocumentContent(document, document.getRootElement());
        this.stateText = bundle.stateText;
        this.fontStatsText = bundle.fontStatsText;
        this.runtimeFrameText = bundle.runtimeFrameText;
        this.runtimeHotspotText = bundle.runtimeHotspotText;
        this.runtimePhaseText = bundle.runtimePhaseText;
        this.rendererNoteText = bundle.rendererNoteText;
        rebuildPayload();
    }

    @Override
    public void configureDocumentPage() {
        diagnosticPage.setContentWidthRange(760, 1180)
                .setMinContentHeight(640)
                .setViewportFillRatio(0.96F, 0.94F);
    }

    @Override
    public void buildDocument() {
        diagnosticPage.addBlock(htmlLikeDocumentWidget);
    }

    @Override
    public void afterDocumentBuilt() {
        refreshDebugText(true);
    }

    @Override
    public void onDocumentResized() {
        refreshDebugText(true);
    }

    @Override
    public void beforeDocumentFrame() {
        refreshDebugText(false);
    }

    /**
     * 返回当前页面的 HTML-like 文档适配组件。
     *
     * @return HTML-like 文档适配组件
     */
    HtmlLikeDocumentWidget getHtmlLikeDocumentWidget() {
        return htmlLikeDocumentWidget;
    }

    private void configureControls() {
        sceneSelector.setSelectedIndex(0);
        sceneSelector.setBackgroundColors(0xFF14B8A6, 0xFF0F766E, 0xFF1F2937, 0xFF111827, 0xFF111827)
                .setFocusBorderColor(0xFF99F6E4)
                .setSelectionHandler(new DocumentSegmentedSelectionHandler() {
                    @Override
                    public void onSelectionChanged(DocumentSegmentedSelectionEvent event) {
                        rebuildPayload();
                        refreshDebugText(true);
                    }
                });
        sceneSelector.getElement().setAttribute("data-font-baseline-control", "scene");

        countSelector.setSelectedIndex(0);
        countSelector.setBackgroundColors(0xFF6366F1, 0xFF4338CA, 0xFF1F2937, 0xFF111827, 0xFF111827)
                .setFocusBorderColor(0xFFC4B5FD)
                .setSelectionHandler(new DocumentSegmentedSelectionHandler() {
                    @Override
                    public void onSelectionChanged(DocumentSegmentedSelectionEvent event) {
                        rebuildPayload();
                        refreshDebugText(true);
                    }
                });
        countSelector.getElement().setAttribute("data-font-baseline-control", "count");

        rebuildButton.setBackgroundColors(0xFF2563EB, 0xFF1D4ED8, 0xFF334155)
                .setFocusBorderColor(0xFFBFDBFE)
                .setActionHandler(new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        generation++;
                        rebuildPayload();
                        refreshDebugText(true);
                    }
                });
        rebuildButton.getElement().setAttribute("data-font-baseline-control", "rebuild");

        pauseButton.setBackgroundColors(0xFFF97316, 0xFFEA580C, 0xFF334155)
                .setFocusBorderColor(0xFFFED7AA)
                .setActionHandler(new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        running = !running;
                        pauseButton.setLabel(running ? "暂停" : "运行");
                        refreshDebugText(true);
                    }
                });
        pauseButton.getElement().setAttribute("data-font-baseline-control", "pause-toggle");
    }

    private DocumentBundle createDocumentContent(UiDocument document, ElementNode root) {
        root.style()
                .setPadding(UiStyleLength.px(18))
                .setBackgroundColor(0xF0081020)
                .setBorderColor(0xFF38BDF8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(22))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setTextColor(0xFFE5F1FF);

        appendHero(document, root);
        appendControls(document, root);

        ElementNode mainRow = document.div();
        mainRow.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.STRETCH)
                .setColumnGap(UiStyleLength.px(14))
                .setHeight(UiStyleLength.px(430))
                .setMargin(UiStyleLength.px(14));
        root.append(mainRow);

        ElementNode debugCard = appendCard(document, mainRow, 0xFF101827, 0xFF60A5FA);
        debugCard.style().setFlexGrow(1.0F);
        debugCard.appendText("Debug 信息");
        debugCard.appendText("文本每 500ms 刷新一次，避免诊断文案自身持续触发布局和字体测量压力。 ");
        TextNode stateText = debugCard.appendText("");
        TextNode fontStatsText = debugCard.appendText("");
        TextNode runtimeFrameText = debugCard.appendText("");
        TextNode runtimeHotspotText = debugCard.appendText("");
        TextNode runtimePhaseText = debugCard.appendText("");
        TextNode rendererNoteText = debugCard.appendText("");

        ElementNode stressCard = appendCard(document, mainRow, 0xFF1A120B, 0xFFF59E0B);
        stressCard.style().setWidth(UiStyleLength.px(418));
        stressCard.appendText("固定尺寸字符压测区");
        stressCard.appendText("运行时通过局部缩放让字符叠压在可视区域内，避免用 display:none、不可见节点或滚出视口规避绘制。 ");
        ElementNode stressStage = createStressStage(document);
        stressCard.append(stressStage);

        return new DocumentBundle(stateText, fontStatsText, runtimeFrameText, runtimeHotspotText,
                runtimePhaseText, rendererNoteText);
    }

    private void appendHero(UiDocument document, ElementNode root) {
        ElementNode hero = document.div();
        hero.style()
                .setHeight(UiStyleLength.px(110))
                .setPadding(UiStyleLength.px(16))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF67E8F9)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(18))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setTextColor(0xFFF8FAFC);
        hero.appendText("字体性能基线");
        hero.appendText("真实 GUI 环境字体绘制压测，仅采样现有 FontRuntimeStats 与 UiRuntimeStats，不做字体系统优化。 ");
        hero.appendText("默认 1000 字符，必要时手动切到 10000 / 20000，避免打开页面即卡死。 ");
        root.append(hero);
    }

    private void appendControls(UiDocument document, ElementNode root) {
        ElementNode controlsCard = appendCard(document, root, 0xFF111C2E, 0xFF22D3EE);
        controlsCard.appendText("压测控制");
        appendControlRow(document, controlsCard, "场景", sceneSelector.getElement());
        appendControlRow(document, controlsCard, "字符数", countSelector.getElement());

        ElementNode buttonRow = document.div();
        buttonRow.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.STRETCH)
                .setJustifyContent(UiJustifyContent.START)
                .setColumnGap(UiStyleLength.px(10))
                .setHeight(UiStyleLength.px(42))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(8), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)));
        rebuildButton.getElement().style().setWidth(UiStyleLength.px(150));
        pauseButton.getElement().style().setWidth(UiStyleLength.px(120));
        buttonRow.append(rebuildButton.getElement());
        buttonRow.append(pauseButton.getElement());
        controlsCard.append(buttonRow);
    }

    private ElementNode appendCard(UiDocument document, ElementNode parent, int backgroundColor, int borderColor) {
        ElementNode card = document.div();
        card.style()
                .setPadding(UiStyleLength.px(14))
                .setMargin(UiStyleLength.px(12))
                .setBackgroundColor(backgroundColor)
                .setBorderColor(borderColor)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(16))
                .setTextColor(0xFFE6F1FF)
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        parent.append(card);
        return card;
    }

    private void appendControlRow(UiDocument document, ElementNode parent, String label, ElementNode field) {
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(10))
                .setHeight(UiStyleLength.px(44))
                .setMargin(UiStyleLength.px(6));
        ElementNode labelElement = document.div();
        labelElement.style()
                .setWidth(UiStyleLength.px(76))
                .setTextColor(0xFFBAE6FD);
        labelElement.appendText(label);
        field.style().setFlexGrow(1.0F);
        row.append(labelElement);
        row.append(field);
        parent.append(row);
    }

    private ElementNode createStressStage(UiDocument document) {
        ElementNode stage = document.div();
        stage.style()
                .setHeight(UiStyleLength.px(292))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(10), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF070A12)
                .setBorderColor(0xFFFBBF24)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12))
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE)
                .setTextColor(0xFFFFF7D6);
        stage.setAttribute("data-font-baseline-stage", "stress");
        stage.setCustomRenderer(new FontStressRenderer());
        return stage;
    }

    private void refreshDebugText(boolean force) {
        long now = System.nanoTime();
        if (!force && lastDebugRefreshNanos != Long.MIN_VALUE
                && now - lastDebugRefreshNanos < DEBUG_REFRESH_INTERVAL_NANOS) {
            return;
        }
        lastDebugRefreshNanos = now;

        StressPayload payload = currentPayload == null ? StressPayload.empty() : currentPayload;
        updateText(stateText, formatStateText(payload));
        updateText(fontStatsText, formatFontStats(fontRuntimeStatsSource.getRuntimeStats()));
        UiRuntimeStats runtimeStats = runtimeView.getUiRuntimeStats();
        updateText(runtimeFrameText, formatRuntimeFrameStats(runtimeStats));
        updateText(runtimeHotspotText, formatRuntimeHotspotStats(runtimeStats));
        updateText(runtimePhaseText, formatRuntimePhaseStats(runtimeStats));
        updateText(rendererNoteText, formatRendererNote(payload));
    }

    private String formatStateText(StressPayload payload) {
        return "状态：" + (running ? "运行中" : "已暂停")
                + "；场景：" + payload.sceneName
                + "；字符数：" + payload.visibleCharacterCount
                + "；唯一字符数：" + payload.uniqueCodepointCount
                + "；绘制行：" + payload.rows.size()
                + "；重建代数：" + generation
                + "。";
    }

    private String formatFontStats(FontRuntimeStats stats) {
        FontRuntimeStats resolvedStats = stats == null ? FontRuntimeStats.empty() : stats;
        return String.format(Locale.ROOT,
                "字体统计：pending upload %d；ready glyph %d；normal/bold page %d/%d；direct table codepoints/slots %d/%d；draw-stage upload %d；frame quad %d；last flush page-batch/draw/texture-bind %d/%d/%d；font match hit/miss %d/%d；derived font hit/miss %d/%d；width cache hit/miss %d/%d。",
                Integer.valueOf(resolvedStats.getPendingUploadCount()),
                Integer.valueOf(resolvedStats.getReadyGlyphCount()),
                Integer.valueOf(resolvedStats.getNormalPageCount()),
                Integer.valueOf(resolvedStats.getBoldPageCount()),
                Integer.valueOf(resolvedStats.getDirectTableCodepointCount()),
                Integer.valueOf(resolvedStats.getDirectTableSlotsPerPage()),
                Integer.valueOf(resolvedStats.getQueuedDrawStageUploadCount()),
                Integer.valueOf(resolvedStats.getFrameQuadCount()),
                Integer.valueOf(resolvedStats.getLastFlushPageSubmitCount()),
                Integer.valueOf(resolvedStats.getLastFlushDrawCallCount()),
                Integer.valueOf(resolvedStats.getLastFlushTextureBindCount()),
                Long.valueOf(resolvedStats.getFontMatchCacheHitCount()),
                Long.valueOf(resolvedStats.getFontMatchCacheMissCount()),
                Long.valueOf(resolvedStats.getDerivedFontCacheHitCount()),
                Long.valueOf(resolvedStats.getDerivedFontCacheMissCount()),
                Long.valueOf(resolvedStats.getWidthCacheHitCount()),
                Long.valueOf(resolvedStats.getWidthCacheMissCount()));
    }

    private String formatRuntimeFrameStats(UiRuntimeStats stats) {
        UiRuntimeStats resolvedStats = stats == null ? UiRuntimeStats.empty() : stats;
        if (resolvedStats.getSampledFrameCount() <= 0 || !screenName.equals(resolvedStats.getScreenName())) {
            return "UI 统计：等待当前页面完成采样；进入页面后至少完成一帧渲染才会显示帧耗时。";
        }
        return String.format(Locale.ROOT,
                "UI 帧：当前 %.2f ms；均值 %.2f ms；最大 %.2f ms；平均 FPS %.1f；慢帧 %d/%d；GUI/native %dx%d / %dx%d。",
                Double.valueOf(resolvedStats.getFrameTimeMs()),
                Double.valueOf(resolvedStats.getAverageFrameTimeMs()),
                Double.valueOf(resolvedStats.getMaxFrameTimeMs()),
                Double.valueOf(resolvedStats.getAverageFps()),
                Integer.valueOf(resolvedStats.getSlowFrameCount()),
                Integer.valueOf(resolvedStats.getSampledFrameCount()),
                Integer.valueOf(resolvedStats.getGuiWidth()),
                Integer.valueOf(resolvedStats.getGuiHeight()),
                Integer.valueOf(resolvedStats.getNativeWidth()),
                Integer.valueOf(resolvedStats.getNativeHeight()));
    }

    private String formatRuntimeHotspotStats(UiRuntimeStats stats) {
        UiRuntimeStats resolvedStats = stats == null ? UiRuntimeStats.empty() : stats;
        if (resolvedStats.getSampledFrameCount() <= 0 || !screenName.equals(resolvedStats.getScreenName())) {
            return "UI 热点：等待组件渲染、输入路由和命中测试采样。";
        }
        return String.format(Locale.ROOT,
                "UI 阶段：render %.2f ms；present %.2f ms；input %.2f ms；组件渲染 %d；命中测试 %d；最大深度 %d；事件 mouse/key/text %d/%d/%d；最慢自身 %s %.2f ms；最慢总计 %s %.2f ms。",
                Double.valueOf(resolvedStats.getRenderTimeMs()),
                Double.valueOf(resolvedStats.getPresentTimeMs()),
                Double.valueOf(resolvedStats.getInputRoutingTimeMs()),
                Integer.valueOf(resolvedStats.getWidgetRenderCount()),
                Long.valueOf(resolvedStats.getHitTestVisitCount()),
                Integer.valueOf(resolvedStats.getMaxWidgetDepth()),
                Integer.valueOf(resolvedStats.getMouseEventCount()),
                Integer.valueOf(resolvedStats.getKeyEventCount()),
                Integer.valueOf(resolvedStats.getTextEventCount()),
                displayWidgetClass(resolvedStats.getSlowestWidgetSelfClassName()),
                Double.valueOf(resolvedStats.getSlowestWidgetSelfTimeMs()),
                displayWidgetClass(resolvedStats.getSlowestWidgetTotalClassName()),
                Double.valueOf(resolvedStats.getSlowestWidgetTotalTimeMs()));
    }

    private String formatRuntimePhaseStats(UiRuntimeStats stats) {
        UiRuntimeStats resolvedStats = stats == null ? UiRuntimeStats.empty() : stats;
        if (resolvedStats.getSampledFrameCount() <= 0 || !screenName.equals(resolvedStats.getScreenName())) {
            return "阶段热点：等待当前页面运行时统计。";
        }
        String phaseSummary = resolvedStats.getPhaseSummary();
        return "阶段热点：" + (phaseSummary == null || phaseSummary.isEmpty() ? "当前帧暂无阶段采样。" : phaseSummary);
    }

    private String formatRendererNote(StressPayload payload) {
        return "绘制说明：" + (running ? "压测区每帧绘制当前文本。" : "压测区暂停大批量绘制，仅保留当前文本与少量预览。")
                + "样本前缀：" + payload.sampleText;
    }

    private String displayWidgetClass(String className) {
        return className == null || className.isEmpty() ? "<暂无>" : className;
    }

    private void updateText(TextNode textNode, String text) {
        if (textNode != null) {
            textNode.setText(text == null ? "" : text);
        }
    }

    private void rebuildPayload() {
        String sceneName = sceneSelector.getSelectedOption();
        int visibleCharacterCount = resolveSelectedCharacterCount();
        String visibleText = buildVisibleText(sceneName, visibleCharacterCount);
        boolean minecraftFormatted = SCENE_MINECRAFT_RANDOM.equals(sceneName);
        this.currentPayload = StressPayload.create(sceneName, visibleCharacterCount,
                countUniqueCodepoints(visibleText), splitRows(visibleText, visibleCharacterCount, minecraftFormatted),
                minecraftFormatted ? TextContentMode.MINECRAFT_FORMATTED : TextContentMode.UILIB_RAW,
                trimCodepoints(visibleText, 96));
    }

    private int resolveSelectedCharacterCount() {
        int selectedIndex = Math.max(0, Math.min(countSelector.getSelectedIndex(), COUNT_PRESETS.length - 1));
        return COUNT_PRESETS[selectedIndex];
    }

    private String buildVisibleText(String sceneName, int visibleCharacterCount) {
        if (SCENE_CHINESE.equals(sceneName)) {
            return repeatPattern(CHINESE_PATTERN, visibleCharacterCount);
        }
        if (SCENE_MIXED.equals(sceneName)) {
            return repeatPattern(MIXED_PATTERN, visibleCharacterCount);
        }
        if (SCENE_UNIQUE.equals(sceneName)) {
            return buildUniqueColdText(visibleCharacterCount);
        }
        if (SCENE_MINECRAFT_RANDOM.equals(sceneName)) {
            return repeatPattern(RANDOM_STYLE_PATTERN, visibleCharacterCount);
        }
        return repeatPattern(ASCII_PATTERN, visibleCharacterCount);
    }

    private String repeatPattern(String pattern, int visibleCharacterCount) {
        String resolvedPattern = pattern == null || pattern.isEmpty() ? "?" : pattern;
        StringBuilder builder = new StringBuilder(Math.max(0, visibleCharacterCount));
        int patternIndex = 0;
        for (int count = 0; count < visibleCharacterCount; count++) {
            if (patternIndex >= resolvedPattern.length()) {
                patternIndex = 0;
            }
            int codepoint = resolvedPattern.codePointAt(patternIndex);
            builder.appendCodePoint(codepoint);
            patternIndex += Character.charCount(codepoint);
        }
        return builder.toString();
    }

    private String buildUniqueColdText(int visibleCharacterCount) {
        int startCodepoint = 0x4E00;
        int rangeSize = 0x9FFF - startCodepoint + 1;
        int offset = Math.abs(generation * 431) % rangeSize;
        StringBuilder builder = new StringBuilder(Math.max(0, visibleCharacterCount));
        for (int index = 0; index < visibleCharacterCount; index++) {
            builder.appendCodePoint(startCodepoint + ((offset + index) % rangeSize));
        }
        return builder.toString();
    }

    private List<String> splitRows(String visibleText, int visibleCharacterCount, boolean minecraftFormatted) {
        int rowCount = resolveRowCount(visibleCharacterCount);
        int charsPerRow = Math.max(1, (visibleCharacterCount + rowCount - 1) / rowCount);
        List<String> rows = new ArrayList<String>();
        StringBuilder currentRow = new StringBuilder(charsPerRow + 8);
        int currentRowChars = 0;
        int rowIndex = 0;
        for (int index = 0; index < visibleText.length();) {
            int codepoint = visibleText.codePointAt(index);
            currentRow.appendCodePoint(codepoint);
            currentRowChars++;
            index += Character.charCount(codepoint);
            if (currentRowChars >= charsPerRow) {
                rows.add(formatRow(currentRow.toString(), rowIndex, minecraftFormatted));
                rowIndex++;
                currentRow.setLength(0);
                currentRowChars = 0;
            }
        }
        if (currentRow.length() > 0) {
            rows.add(formatRow(currentRow.toString(), rowIndex, minecraftFormatted));
        }
        return rows;
    }

    private int resolveRowCount(int visibleCharacterCount) {
        if (visibleCharacterCount <= 1000) {
            return 12;
        }
        if (visibleCharacterCount <= 5000) {
            return 22;
        }
        if (visibleCharacterCount <= 10000) {
            return 32;
        }
        return 44;
    }

    private String formatRow(String row, int rowIndex, boolean minecraftFormatted) {
        if (!minecraftFormatted) {
            return row;
        }
        String color = MINECRAFT_ROW_COLORS[rowIndex % MINECRAFT_ROW_COLORS.length];
        return color + "§k" + row + "§r";
    }

    private int countUniqueCodepoints(String text) {
        Set<Integer> codepoints = new HashSet<Integer>();
        if (text == null) {
            return 0;
        }
        for (int index = 0; index < text.length();) {
            int codepoint = text.codePointAt(index);
            codepoints.add(Integer.valueOf(codepoint));
            index += Character.charCount(codepoint);
        }
        return codepoints.size();
    }

    private String trimCodepoints(String text, int maxCodepoints) {
        if (text == null || text.isEmpty() || maxCodepoints <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (int index = 0; index < text.length() && count < maxCodepoints;) {
            int codepoint = text.codePointAt(index);
            builder.appendCodePoint(codepoint);
            index += Character.charCount(codepoint);
            count++;
        }
        if (text.codePointCount(0, text.length()) > maxCodepoints) {
            builder.append("...");
        }
        return builder.toString();
    }

    /**
     * 字符压测区自定义绘制器。
     */
    private final class FontStressRenderer implements DocumentCustomRenderer {

        @Override
        public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                int contentBottom) {
            StressPayload payload = currentPayload;
            if (payload == null || payload.rows.isEmpty()) {
                return;
            }
            if (!running) {
                renderPausedPreview(context, payload, contentLeft, contentTop);
                return;
            }

            int contentWidth = Math.max(1, contentRight - contentLeft);
            int contentHeight = Math.max(1, contentBottom - contentTop);
            int lineHeight = Math.max(1, context.getTextLineHeight());
            int maxVisibleCharsInRow = Math.max(1, payload.maxVisibleCharsInRow);
            float scaleX = clamp((contentWidth - 8.0F) / (maxVisibleCharsInRow * 18.0F), 0.045F, 0.85F);
            float scaleY = clamp((contentHeight - 8.0F) / (payload.rows.size() * (float) lineHeight), 0.16F, 0.72F);

            GL11.glPushMatrix();
            try {
                GL11.glTranslatef((float) contentLeft + 4.0F, (float) contentTop + 4.0F, 0.0F);
                GL11.glScalef(scaleX, scaleY, 1.0F);
                for (int rowIndex = 0; rowIndex < payload.rows.size(); rowIndex++) {
                    int color = rowIndex % 2 == 0 ? 0xFFFFF7D6 : 0xFFBFDBFE;
                    context.drawText(payload.rows.get(rowIndex), 0, rowIndex * lineHeight, color, false,
                            payload.textContentMode);
                }
            } finally {
                GL11.glPopMatrix();
            }
        }

        private void renderPausedPreview(UiRenderContext context, StressPayload payload, int contentLeft,
                int contentTop) {
            context.drawText("已暂停：压测文本保留，未执行大批量绘制", contentLeft + 4, contentTop + 4,
                    0xFFFFD7A8, false, TextContentMode.UILIB_RAW);
            context.drawText(trimCodepoints(payload.sampleText, 48), contentLeft + 4, contentTop + 28,
                    0xFFBAE6FD, false, TextContentMode.UILIB_RAW);
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 当前压测文本快照。
     */
    private static final class StressPayload {

        final String sceneName;
        final int visibleCharacterCount;
        final int uniqueCodepointCount;
        final List<String> rows;
        final TextContentMode textContentMode;
        final String sampleText;
        final int maxVisibleCharsInRow;

        private StressPayload(String sceneName, int visibleCharacterCount, int uniqueCodepointCount,
                List<String> rows, TextContentMode textContentMode, String sampleText, int maxVisibleCharsInRow) {
            this.sceneName = sceneName == null ? "" : sceneName;
            this.visibleCharacterCount = Math.max(0, visibleCharacterCount);
            this.uniqueCodepointCount = Math.max(0, uniqueCodepointCount);
            this.rows = rows == null ? new ArrayList<String>() : rows;
            this.textContentMode = textContentMode == null ? TextContentMode.UILIB_RAW : textContentMode;
            this.sampleText = sampleText == null ? "" : sampleText;
            this.maxVisibleCharsInRow = Math.max(1, maxVisibleCharsInRow);
        }

        static StressPayload create(String sceneName, int visibleCharacterCount, int uniqueCodepointCount,
                List<String> rows, TextContentMode textContentMode, String sampleText) {
            return new StressPayload(sceneName, visibleCharacterCount, uniqueCodepointCount, rows, textContentMode,
                    sampleText, resolveMaxVisibleCharsInRow(rows, textContentMode));
        }

        static StressPayload empty() {
            return new StressPayload("<未生成>", 0, 0, new ArrayList<String>(), TextContentMode.UILIB_RAW, "", 1);
        }

        private static int resolveMaxVisibleCharsInRow(List<String> rows, TextContentMode textContentMode) {
            int max = 1;
            if (rows == null) {
                return max;
            }
            for (String row : rows) {
                max = Math.max(max, countVisibleCodepoints(row, textContentMode));
            }
            return max;
        }

        private static int countVisibleCodepoints(String text, TextContentMode textContentMode) {
            if (text == null || text.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (int index = 0; index < text.length();) {
                int codepoint = text.codePointAt(index);
                if (textContentMode == TextContentMode.MINECRAFT_FORMATTED && codepoint == '§'
                        && index + 1 < text.length()) {
                    index += 1 + Character.charCount(text.codePointAt(index + 1));
                    continue;
                }
                count++;
                index += Character.charCount(codepoint);
            }
            return count;
        }
    }

    /**
     * 页面构建后需要保留的节点引用。
     */
    private static final class DocumentBundle {

        final TextNode stateText;
        final TextNode fontStatsText;
        final TextNode runtimeFrameText;
        final TextNode runtimeHotspotText;
        final TextNode runtimePhaseText;
        final TextNode rendererNoteText;

        DocumentBundle(TextNode stateText, TextNode fontStatsText, TextNode runtimeFrameText,
                TextNode runtimeHotspotText, TextNode runtimePhaseText, TextNode rendererNoteText) {
            this.stateText = stateText;
            this.fontStatsText = fontStatsText;
            this.runtimeFrameText = runtimeFrameText;
            this.runtimeHotspotText = runtimeHotspotText;
            this.runtimePhaseText = runtimePhaseText;
            this.rendererNoteText = rendererNoteText;
        }
    }
}
