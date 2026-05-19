package club.heiqi.uilib.ui.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.animation.DocumentAnimationClock;
import club.heiqi.uilib.ui.animation.DocumentAnimationFillMode;
import club.heiqi.uilib.ui.animation.DocumentAnimationImpact;
import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.animation.DocumentKeyframes;
import club.heiqi.uilib.ui.dom.DocumentElementActiveEvent;
import club.heiqi.uilib.ui.dom.DocumentElementActiveHandler;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDragEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusEvent;
import club.heiqi.uilib.ui.dom.DocumentElementFocusHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.dom.control.DocumentButtonControl;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.dom.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.dom.control.DocumentDraggableSupport;
import club.heiqi.uilib.ui.dom.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.dom.control.DocumentToggleSwitchControl;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.input.UiInputRouter;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.UiBorderStyle;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiOverflowWrap;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleInsets;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;

/**
 * `HtmlLikeDocumentWidget` 的后端适配契约测试。
 */
public class HtmlLikeDocumentWidgetTest {

    /**
     * 验证 HTML-like 文档可以通过 widget 后端绘制到 `UiRenderContext`。
     */
    @Test
    public void shouldRenderDocumentThroughWidgetBackend() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setHeight(UiStyleLength.px(24))
                .setBackgroundColor(0xFF102030)
                .setBorderColor(0xFF80A0FF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(6));
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(17, 23, 120, 48);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertSame(document, widget.getDocument());
        Assert.assertEquals(2, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 17, 23, 137, 49, 0xFF102030, 0, 6);
        assertDrawCall(renderContext.drawCalls.get(1), 17, 23, 137, 49, 0, 0xFF80A0FF, 6);
    }

    /**
     * 验证空尺寸组件不会触发绘制。
     */
    @Test
    public void shouldIgnoreEmptyWidgetBounds() {
        UiDocument document = UiDocument.create();
        document.getRootElement().style().setBackgroundColor(0xFFFFFFFF);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 0, 48);
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();

        widget.render(renderContext);

        Assert.assertTrue(renderContext.drawCalls.isEmpty());
    }

    /**
     * 验证 HTML-like 文档适配组件会使用注入的文本测量服务生成多行文本绘制命令。
     */
    @Test
    public void shouldRenderWrappedTextThroughWidgetBackend() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(24))
                .setOverflowWrap(UiOverflowWrap.BREAK_WORD)
                .setTextColor(0xFFEFF6FF);
        root.appendText("abcdefg");
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 80);
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();

        widget.render(renderContext);

        Assert.assertEquals(3, renderContext.textCalls.size());
        assertTextCall(renderContext.textCalls.get(0), "abc", 5, 7, 0xFFEFF6FF, false);
        assertTextCall(renderContext.textCalls.get(1), "def", 5, 25, 0xFFEFF6FF, false);
        assertTextCall(renderContext.textCalls.get(2), "g", 5, 43, 0xFFEFF6FF, false);
    }

    /**
     * 验证 HTML-like 文本节点默认按 UILib 原始文本模式绘制。
     */
    @Test
    public void shouldRenderTextNodesInUiLibRawModeByDefault() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style().setWidth(UiStyleLength.px(80));
        root.appendText("价格：§a100金币");
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 48);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertEquals(1, renderContext.textCalls.size());
        Assert.assertEquals("价格：§a100金币", renderContext.textCalls.get(0).text);
        Assert.assertEquals(TextContentMode.UILIB_RAW, renderContext.textCalls.get(0).textContentMode);
    }

    /**
     * 验证文本节点可显式切回 Minecraft 文本模式。
     */
    @Test
    public void shouldAllowExplicitMinecraftFormattedTextNodes() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style().setWidth(UiStyleLength.px(80));
        root.appendMinecraftText("价格：§a100金币");
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 48);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertFalse(renderContext.textCalls.isEmpty());
        Assert.assertEquals(TextContentMode.MINECRAFT_FORMATTED, renderContext.textCalls.get(0).textContentMode);
    }

    /**
     * 验证 HTML-like 组件会在没有 DOM 变更时继续重绘 paint-only transition。
     */
    @Test
    public void shouldRenderPaintOnlyTransitionAcrossFrames() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setHeight(UiStyleLength.px(24))
                .setBackgroundColor(0xFF000000)
                .setTransition(DocumentAnimationProperty.BACKGROUND_COLOR, 1000L);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 120, 48);

        RecordingUiRenderContext initialContext = new RecordingUiRenderContext();
        widget.render(initialContext);
        Assert.assertEquals(0xFF000000, initialContext.drawCalls.get(0).surfaceStyle.fillColor);

        root.style().setBackgroundColor(0xFFFFFFFF);
        RecordingUiRenderContext startContext = new RecordingUiRenderContext();
        widget.render(startContext);
        Assert.assertEquals(0xFF000000, startContext.drawCalls.get(0).surfaceStyle.fillColor);
        Assert.assertEquals(1, widget.getActiveAnimationCount());

        animationClock.setCurrentTimeNanos(500_000_000L);
        RecordingUiRenderContext halfContext = new RecordingUiRenderContext();
        widget.render(halfContext);
        Assert.assertEquals(0x00808080, halfContext.drawCalls.get(0).surfaceStyle.fillColor & 0x00FFFFFF);

        animationClock.setCurrentTimeNanos(1_000_000_000L);
        RecordingUiRenderContext finishedContext = new RecordingUiRenderContext();
        widget.render(finishedContext);
        Assert.assertEquals(0xFFFFFFFF, finishedContext.drawCalls.get(0).surfaceStyle.fillColor);
        Assert.assertEquals(0, widget.getActiveAnimationCount());
    }

    /**
     * 验证 transition 结束后组件会回到静态 paint command 缓存路径。
     */
    @Test
    public void shouldReturnToStaticPaintCacheAfterTransitionFinishes() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(32))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF000000)
                .setTransition(DocumentAnimationProperty.BACKGROUND_COLOR, 1000L);
        root.appendText("cache");
        ManualAnimationClock animationClock = new ManualAnimationClock();
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40, textMeasureService);
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.render(new RecordingUiRenderContext());
        int initialGeneration = widget.getPaintCacheGenerationForDiagnostics();
        int initialMeasureCount = textMeasureService.getMeasureCount();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot initialSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();
        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(initialGeneration, widget.getPaintCacheGenerationForDiagnostics());
        Assert.assertEquals(initialSnapshot.getStaticLayoutGeneration(),
                widget.getPerformanceDiagnosticsSnapshot().getStaticLayoutGeneration());
        Assert.assertEquals(initialSnapshot.getRuntimeLayoutGeneration(),
                widget.getPerformanceDiagnosticsSnapshot().getRuntimeLayoutGeneration());

        root.style().setBackgroundColor(0xFFFFFFFF);
        widget.render(new RecordingUiRenderContext());
        int transitionStartGeneration = widget.getPaintCacheGenerationForDiagnostics();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot transitionStartSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();

        animationClock.setCurrentTimeNanos(500_000_000L);
        widget.render(new RecordingUiRenderContext());
        Assert.assertTrue(widget.getPaintCacheGenerationForDiagnostics() > transitionStartGeneration);
        Assert.assertEquals(transitionStartSnapshot.getStaticLayoutGeneration(),
                widget.getPerformanceDiagnosticsSnapshot().getStaticLayoutGeneration());
        Assert.assertEquals(transitionStartSnapshot.getRuntimeLayoutGeneration(),
                widget.getPerformanceDiagnosticsSnapshot().getRuntimeLayoutGeneration());
        Assert.assertEquals(initialMeasureCount, textMeasureService.getMeasureCount());

        animationClock.setCurrentTimeNanos(1_000_000_000L);
        widget.render(new RecordingUiRenderContext());
        int finishedGeneration = widget.getPaintCacheGenerationForDiagnostics();
        Assert.assertEquals(0, widget.getActiveAnimationCount());

        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(finishedGeneration, widget.getPaintCacheGenerationForDiagnostics());
        Assert.assertEquals(initialMeasureCount, textMeasureService.getMeasureCount());
    }

    /**
     * 验证 effect-affecting 动画运行期间不会触发重新文本测量布局。
     */
    @Test
    public void shouldRenderEffectTransitionWithoutRecomputingLayout() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(64))
                .setHeight(UiStyleLength.px(28))
                .setBackdropBlurRadius(UiStyleLength.px(4))
                .setTransition(DocumentAnimationProperty.BACKDROP_BLUR_RADIUS, 1000L);
        root.appendText("glass");
        ManualAnimationClock animationClock = new ManualAnimationClock();
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48, textMeasureService);
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 120, 48);

        widget.render(new RecordingUiRenderContext());
        int measureCountAfterInitialRender = textMeasureService.getMeasureCount();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot initialSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();
        root.style().setBackdropBlurRadius(UiStyleLength.px(20));
        widget.render(new RecordingUiRenderContext());
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot transitionStartSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();

        animationClock.setCurrentTimeNanos(500_000_000L);
        widget.render(new RecordingUiRenderContext());
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot runningSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertEquals(measureCountAfterInitialRender, textMeasureService.getMeasureCount());
        Assert.assertEquals(initialSnapshot.getStaticLayoutGeneration(),
                transitionStartSnapshot.getStaticLayoutGeneration());
        Assert.assertEquals(transitionStartSnapshot.getStaticLayoutGeneration(),
                runningSnapshot.getStaticLayoutGeneration());
        Assert.assertEquals(transitionStartSnapshot.getRuntimeLayoutGeneration(),
                runningSnapshot.getRuntimeLayoutGeneration());
        Assert.assertEquals(1, widget.getActiveAnimationCount());
    }

    /**
     * 验证 layout-affecting width transition 会驱动同帧重排并影响后续兄弟元素位置。
     */
    @Test
    public void shouldRelayoutSiblingsDuringWidthTransition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode row = document.div();
        ElementNode animated = document.div();
        ElementNode sibling = document.div();
        root.style().setWidth(UiStyleLength.px(160));
        row.style().setDisplay(UiDisplay.FLEX);
        animated.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF112233)
                .setTransition(DocumentAnimationProperty.WIDTH, 1000L);
        sibling.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF445566);
        row.append(animated).append(sibling);
        root.append(row);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 160, 40);

        widget.render(new RecordingUiRenderContext());
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot initialSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();
        animated.style().setWidth(UiStyleLength.px(80));
        widget.render(new RecordingUiRenderContext());
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot transitionStartSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();

        animationClock.setCurrentTimeNanos(500_000_000L);
        RecordingUiRenderContext halfContext = new RecordingUiRenderContext();
        widget.render(halfContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot runningSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();

        assertDrawCall(halfContext.drawCalls.get(0), 0, 0, 60, 20, 0xFF112233, 0, 0);
        assertDrawCall(halfContext.drawCalls.get(1), 60, 0, 80, 20, 0xFF445566, 0, 0);
        Assert.assertEquals(1, widget.getActiveAnimationCount());
        Assert.assertTrue(widget.hasLayoutRuntimeValueForDiagnostics());
        Assert.assertTrue(transitionStartSnapshot.getStaticLayoutGeneration()
                >= initialSnapshot.getStaticLayoutGeneration());
        Assert.assertTrue(runningSnapshot.getRuntimeLayoutGeneration()
                > transitionStartSnapshot.getRuntimeLayoutGeneration());
    }

    /**
     * 验证 layout runtime 诊断状态会随 layout transition 清理恢复为空。
     */
    @Test
    public void shouldExposeLayoutRuntimeDiagnosticState() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF112233)
                .setTransition(DocumentAnimationProperty.WIDTH, 1000L);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.render(new RecordingUiRenderContext());
        Assert.assertFalse(widget.hasLayoutRuntimeValueForDiagnostics());
        DocumentAnimationTimeline.DiagnosticsSnapshot initialSnapshot = widget.getAnimationDiagnosticsSnapshot();
        Assert.assertEquals(0, initialSnapshot.getActiveAnimationCount());
        Assert.assertFalse(initialSnapshot.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));

        root.style().setWidth(UiStyleLength.px(80));
        widget.render(new RecordingUiRenderContext());

        Assert.assertEquals(1, widget.getActiveAnimationCount());
        Assert.assertTrue(widget.hasLayoutRuntimeValueForDiagnostics());
        DocumentAnimationTimeline.DiagnosticsSnapshot runningSnapshot = widget.getAnimationDiagnosticsSnapshot();
        Assert.assertEquals(1, runningSnapshot.getActiveAnimationCount());
        Assert.assertEquals(1, runningSnapshot.getTotalTransitionCount());
        Assert.assertEquals(0, runningSnapshot.getTotalKeyframeCount());
        Assert.assertEquals(0, runningSnapshot.getTotalForwardsFillCount());
        Assert.assertEquals(1, runningSnapshot.getTransitionCount(DocumentAnimationImpact.LAYOUT));
        Assert.assertTrue(runningSnapshot.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));

        animationClock.setCurrentTimeNanos(1_000_000_000L);
        widget.render(new RecordingUiRenderContext());

        Assert.assertEquals(0, widget.getActiveAnimationCount());
        Assert.assertFalse(widget.hasLayoutRuntimeValueForDiagnostics());
        DocumentAnimationTimeline.DiagnosticsSnapshot finishedSnapshot = widget.getAnimationDiagnosticsSnapshot();
        Assert.assertEquals(0, finishedSnapshot.getActiveAnimationCount());
        Assert.assertFalse(finishedSnapshot.hasRuntimeValue(DocumentAnimationImpact.LAYOUT));
    }

    /**
     * 验证 layout-affecting margin transition 会驱动同帧重排并影响兄弟元素位置。
     */
    @Test
    public void shouldRelayoutSiblingsDuringMarginTransition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode row = document.div();
        ElementNode animated = document.div();
        ElementNode sibling = document.div();
        root.style().setWidth(UiStyleLength.px(160));
        row.style().setDisplay(UiDisplay.FLEX);
        animated.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(6), UiStyleLength.px(0),
                        UiStyleLength.px(4)))
                .setBackgroundColor(0xFF112233)
                .setTransitionProperties(DocumentAnimationProperty.MARGIN_LEFT,
                        DocumentAnimationProperty.MARGIN_RIGHT)
                .setTransitionDurationMillis(1000L);
        sibling.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF445566);
        row.append(animated).append(sibling);
        root.append(row);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 160, 40);

        widget.render(new RecordingUiRenderContext());
        animated.style().setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(16),
                UiStyleLength.px(0), UiStyleLength.px(24)));
        widget.render(new RecordingUiRenderContext());

        animationClock.setCurrentTimeNanos(500_000_000L);
        RecordingUiRenderContext halfContext = new RecordingUiRenderContext();
        widget.render(halfContext);

        assertDrawCall(halfContext.drawCalls.get(0), 14, 0, 54, 20, 0xFF112233, 0, 0);
        assertDrawCall(halfContext.drawCalls.get(1), 65, 0, 85, 20, 0xFF445566, 0, 0);
        Assert.assertEquals(2, widget.getActiveAnimationCount());
    }

    /**
     * 验证 layout-affecting padding transition 会驱动内容位置与兄弟元素同帧重排。
     */
    @Test
    public void shouldRelayoutContentAndSiblingsDuringPaddingTransition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode row = document.div();
        ElementNode animated = document.div();
        TextNode label = animated.appendText("pad");
        ElementNode sibling = document.div();
        root.style().setWidth(UiStyleLength.px(160));
        row.style().setDisplay(UiDisplay.FLEX);
        animated.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(6), UiStyleLength.px(0),
                        UiStyleLength.px(4)))
                .setBackgroundColor(0xFF112233)
                .setTransitionProperties(DocumentAnimationProperty.PADDING_LEFT,
                        DocumentAnimationProperty.PADDING_RIGHT)
                .setTransitionDurationMillis(1000L);
        sibling.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF445566);
        row.append(animated).append(sibling);
        root.append(row);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 160, 40);

        widget.render(new RecordingUiRenderContext());
        animated.style().setPadding(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(16), UiStyleLength.px(0),
                UiStyleLength.px(20)));
        widget.render(new RecordingUiRenderContext());

        animationClock.setCurrentTimeNanos(500_000_000L);
        RecordingUiRenderContext halfContext = new RecordingUiRenderContext();
        widget.render(halfContext);

        assertDrawCall(halfContext.drawCalls.get(0), 0, 0, 63, 20, 0xFF112233, 0, 0);
        assertDrawCall(halfContext.drawCalls.get(1), 63, 0, 83, 20, 0xFF445566, 0, 0);
        assertTextCall(halfContext.textCalls.get(0), label.getText(), 12, 0, 0xFFFFFFFF, false);
        Assert.assertEquals(2, widget.getActiveAnimationCount());
    }

    /**
     * 验证 layout transition 期间 hit-test 使用运行态几何而不是目标静态几何。
     */
    @Test
    public void shouldHitTestRuntimeGeometryDuringWidthTransition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode row = document.div();
        ElementNode animated = document.div();
        ElementNode sibling = document.div();
        root.style().setWidth(UiStyleLength.px(160));
        row.style().setDisplay(UiDisplay.FLEX);
        animated.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF112233)
                .setTransition(DocumentAnimationProperty.WIDTH, 1000L);
        sibling.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF445566);
        row.append(animated).append(sibling);
        root.append(row);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 160, 40);

        widget.render(new RecordingUiRenderContext());
        animated.style().setWidth(UiStyleLength.px(80));
        widget.render(new RecordingUiRenderContext());
        animationClock.setCurrentTimeNanos(500_000_000L);
        widget.render(new RecordingUiRenderContext());

        assertElementUid(sibling, widget.findElementAt(70, 10));
    }

    /**
     * 验证 layout transition 会让 absolute 子树按运行态 containing block 命中。
     */
    @Test
    public void shouldHitAbsoluteChildAgainstRuntimeContainingBlockDuringWidthTransition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode animated = document.div();
        ElementNode absolute = document.div();
        root.style().setWidth(UiStyleLength.px(160));
        animated.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setBackgroundColor(0xFF112233)
                .setTransition(DocumentAnimationProperty.WIDTH, 1000L);
        absolute.style()
                .setWidth(UiStyleLength.px(10))
                .setHeight(UiStyleLength.px(10))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(0))
                .setRight(UiStyleLength.px(0))
                .setBackgroundColor(0xFF445566);
        animated.append(absolute);
        root.append(animated);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 160, 40);

        widget.render(new RecordingUiRenderContext());
        animated.style().setWidth(UiStyleLength.px(80));
        widget.render(new RecordingUiRenderContext());
        animationClock.setCurrentTimeNanos(500_000_000L);
        widget.render(new RecordingUiRenderContext());

        assertElementUid(absolute, widget.findElementAt(55, 5));
        assertElementUid(root, widget.findElementAt(75, 5));
    }

    /**
     * 验证 fixed layout transition 期间 hit-test 使用视口内运行态 fixed 几何。
     */
    @Test
    public void shouldHitFixedRuntimeGeometryDuringWidthTransition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode spacer = document.div();
        ElementNode fixed = document.div();
        root.style().setWidth(UiStyleLength.px(160));
        spacer.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF112233);
        fixed.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(12))
                .setPosition(UiPosition.FIXED)
                .setTop(UiStyleLength.px(4))
                .setLeft(UiStyleLength.px(10))
                .setBackgroundColor(0xFF445566)
                .setTransition(DocumentAnimationProperty.WIDTH, 1000L);
        root.append(spacer).append(fixed);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 160, 40);

        widget.render(new RecordingUiRenderContext());
        fixed.style().setWidth(UiStyleLength.px(80));
        widget.render(new RecordingUiRenderContext());
        animationClock.setCurrentTimeNanos(500_000_000L);
        widget.render(new RecordingUiRenderContext());

        assertElementUid(fixed, widget.findElementAt(65, 8));
        assertElementUid(spacer, widget.findElementAt(85, 8));
    }

    /**
     * 验证 layout transition 结束后组件会恢复静态 paint cache。
     */
    @Test
    public void shouldReturnToStaticPaintCacheAfterLayoutTransitionFinishes() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style().setWidth(UiStyleLength.px(120));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF000000)
                .setTransition(DocumentAnimationProperty.WIDTH, 1000L);
        child.appendText("layout");
        root.append(child);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40, textMeasureService);
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.render(new RecordingUiRenderContext());
        int initialMeasureCount = textMeasureService.getMeasureCount();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot initialSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();
        child.style().setWidth(UiStyleLength.px(80));
        widget.render(new RecordingUiRenderContext());
        int transitionStartGeneration = widget.getPaintCacheGenerationForDiagnostics();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot transitionStartSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();

        animationClock.setCurrentTimeNanos(500_000_000L);
        widget.render(new RecordingUiRenderContext());
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot runningSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();
        Assert.assertTrue(widget.getPaintCacheGenerationForDiagnostics() > transitionStartGeneration);
        Assert.assertTrue(textMeasureService.getMeasureCount() > initialMeasureCount);
        Assert.assertTrue(transitionStartSnapshot.getStaticLayoutGeneration()
                >= initialSnapshot.getStaticLayoutGeneration());
        Assert.assertTrue(runningSnapshot.getRuntimeLayoutGeneration()
                > transitionStartSnapshot.getRuntimeLayoutGeneration());

        animationClock.setCurrentTimeNanos(1_000_000_000L);
        widget.render(new RecordingUiRenderContext());
        int finishedGeneration = widget.getPaintCacheGenerationForDiagnostics();
        int finishedMeasureCount = textMeasureService.getMeasureCount();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot finishedSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();
        Assert.assertEquals(0, widget.getActiveAnimationCount());

        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(finishedGeneration, widget.getPaintCacheGenerationForDiagnostics());
        Assert.assertEquals(finishedMeasureCount, textMeasureService.getMeasureCount());
        Assert.assertEquals(finishedSnapshot.getStaticLayoutGeneration(),
                widget.getPerformanceDiagnosticsSnapshot().getStaticLayoutGeneration());
        Assert.assertEquals(finishedSnapshot.getRuntimeLayoutGeneration(),
                widget.getPerformanceDiagnosticsSnapshot().getRuntimeLayoutGeneration());
    }

    /**
     * 验证 layout keyframe 运行期间和 forwards fill 后都会驱动运行态布局值。
     */
    @Test
    public void shouldRelayoutSiblingsDuringWidthKeyframeAndForwardsFill() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("layoutGrow")
                .setFloat(DocumentAnimationProperty.WIDTH, 40.0F, 80.0F)
                .build());
        ElementNode root = document.getRootElement();
        ElementNode row = document.div();
        ElementNode animated = document.div();
        ElementNode sibling = document.div();
        root.style().setWidth(UiStyleLength.px(160));
        row.style().setDisplay(UiDisplay.FLEX);
        animated.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF112233)
                .setAnimation("layoutGrow", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        sibling.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF445566);
        row.append(animated).append(sibling);
        root.append(row);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 160, 40);

        widget.render(new RecordingUiRenderContext());
        animationClock.setCurrentTimeNanos(500_000_000L);
        RecordingUiRenderContext halfContext = new RecordingUiRenderContext();
        widget.render(halfContext);
        int runningRuntimeLayoutGeneration = widget.getPerformanceDiagnosticsSnapshot()
                .getRuntimeLayoutGeneration();
        assertDrawCall(halfContext.drawCalls.get(0), 0, 0, 60, 20, 0xFF112233, 0, 0);
        assertDrawCall(halfContext.drawCalls.get(1), 60, 0, 80, 20, 0xFF445566, 0, 0);

        animationClock.setCurrentTimeNanos(1_000_000_000L);
        RecordingUiRenderContext filledContext = new RecordingUiRenderContext();
        widget.render(filledContext);
        int filledRuntimeLayoutGeneration = widget.getPerformanceDiagnosticsSnapshot()
                .getRuntimeLayoutGeneration();
        assertDrawCall(filledContext.drawCalls.get(0), 0, 0, 80, 20, 0xFF112233, 0, 0);
        assertDrawCall(filledContext.drawCalls.get(1), 80, 0, 100, 20, 0xFF445566, 0, 0);
        Assert.assertEquals(0, widget.getActiveAnimationCount());
        Assert.assertTrue(filledRuntimeLayoutGeneration > runningRuntimeLayoutGeneration);

        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(filledRuntimeLayoutGeneration, widget.getPerformanceDiagnosticsSnapshot()
                .getRuntimeLayoutGeneration());

        animated.style().setWidth(UiStyleLength.px(50));
        RecordingUiRenderContext authorContext = new RecordingUiRenderContext();
        widget.render(authorContext);
        assertDrawCall(authorContext.drawCalls.get(0), 0, 0, 50, 20, 0xFF112233, 0, 0);
        assertDrawCall(authorContext.drawCalls.get(1), 50, 0, 70, 20, 0xFF445566, 0, 0);
    }

    /**
     * 验证 margin keyframe 运行期间和 forwards fill 后都会驱动运行态布局值。
     */
    @Test
    public void shouldRelayoutSiblingsDuringMarginKeyframeAndForwardsFill() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("marginPush")
                .setFloat(DocumentAnimationProperty.MARGIN_LEFT, 0.0F, 20.0F)
                .build());
        ElementNode root = document.getRootElement();
        ElementNode row = document.div();
        ElementNode animated = document.div();
        ElementNode sibling = document.div();
        root.style().setWidth(UiStyleLength.px(160));
        row.style().setDisplay(UiDisplay.FLEX);
        animated.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF112233)
                .setAnimation("marginPush", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        sibling.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF445566);
        row.append(animated).append(sibling);
        root.append(row);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 160, 40);

        widget.render(new RecordingUiRenderContext());
        animationClock.setCurrentTimeNanos(500_000_000L);
        RecordingUiRenderContext halfContext = new RecordingUiRenderContext();
        widget.render(halfContext);
        assertDrawCall(halfContext.drawCalls.get(0), 10, 0, 50, 20, 0xFF112233, 0, 0);
        assertDrawCall(halfContext.drawCalls.get(1), 50, 0, 70, 20, 0xFF445566, 0, 0);

        animationClock.setCurrentTimeNanos(1_000_000_000L);
        RecordingUiRenderContext filledContext = new RecordingUiRenderContext();
        widget.render(filledContext);
        assertDrawCall(filledContext.drawCalls.get(0), 20, 0, 60, 20, 0xFF112233, 0, 0);
        assertDrawCall(filledContext.drawCalls.get(1), 60, 0, 80, 20, 0xFF445566, 0, 0);

        animated.style().setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(0),
                UiStyleLength.px(0), UiStyleLength.px(4)));
        RecordingUiRenderContext authorContext = new RecordingUiRenderContext();
        widget.render(authorContext);
        assertDrawCall(authorContext.drawCalls.get(0), 4, 0, 44, 20, 0xFF112233, 0, 0);
        assertDrawCall(authorContext.drawCalls.get(1), 44, 0, 64, 20, 0xFF445566, 0, 0);
        Assert.assertEquals(0, widget.getActiveAnimationCount());
    }

    /**
     * 验证 HTML 级拖拽辅助器可以更新 fixed 元素位置。
     */
    @Test
    public void shouldDragFixedElementThroughDocumentDragSupport() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode handle = document.div();

        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(120));
        panel.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(20))
                .setTop(UiStyleLength.px(10))
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(30))
                .setBackgroundColor(0xFF223344);
        handle.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(10))
                .setBackgroundColor(0xFF446688);
        panel.append(handle);
        root.append(panel);
        DocumentDraggableSupport.attach(panel, handle, DocumentDraggableSupport.DragAxis.BOTH);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 120);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 25, 15, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 45, 35, -1, 0, 20, 20, 2L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 45, 35, 0, 0, 0, 0, 3L));

        Assert.assertNotNull(panel.style().getLeft());
        Assert.assertNotNull(panel.style().getTop());
        Assert.assertEquals(UiStyleLength.Type.PIXEL, panel.style().getLeft().getType());
        Assert.assertEquals(UiStyleLength.Type.PIXEL, panel.style().getTop().getType());
        Assert.assertTrue(panel.style().getLeft().getValue() >= 40.0F);
        Assert.assertTrue(panel.style().getTop().getValue() >= 30.0F);
        Assert.assertNull(panel.style().getRight());
        Assert.assertNull(panel.style().getBottom());
    }

    /**
     * 验证 `right/bottom` 锚定的 fixed 浮窗首次拖拽时不会跳到左上角基线。
     */
    @Test
    public void shouldDragRightBottomAnchoredFixedElementWithoutJumping() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode handle = document.div();

        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(120));
        panel.style()
                .setPosition(UiPosition.FIXED)
                .setRight(UiStyleLength.px(20))
                .setBottom(UiStyleLength.px(10))
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(30))
                .setBackgroundColor(0xFF223344);
        handle.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(10))
                .setBackgroundColor(0xFF446688);
        panel.append(handle);
        root.append(panel);
        DocumentDraggableSupport.attach(panel, handle, DocumentDraggableSupport.DragAxis.BOTH);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 120);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 145, 85, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 165, 95, -1, 0, 20, 10, 2L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 165, 95, 0, 0, 0, 0, 3L));

        Assert.assertNull(panel.style().getLeft());
        Assert.assertNull(panel.style().getTop());
        Assert.assertNotNull(panel.style().getRight());
        Assert.assertNotNull(panel.style().getBottom());
        Assert.assertEquals(UiStyleLength.Type.PIXEL, panel.style().getRight().getType());
        Assert.assertEquals(UiStyleLength.Type.PIXEL, panel.style().getBottom().getType());
        Assert.assertEquals(0.0F, panel.style().getRight().getValue(), 0.001F);
        Assert.assertEquals(0.0F, panel.style().getBottom().getValue(), 0.001F);
    }

    /**
     * 验证可拖拽把手在短点击时仍会保留 click 语义。
     */
    @Test
    public void shouldPreserveClickForDraggableHandleWithoutCrossingThreshold() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode panel = document.div();
        final ElementNode handle = document.div();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();

        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(120));
        panel.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(20))
                .setTop(UiStyleLength.px(10))
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(30));
        handle.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(10));
        handle.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        panel.append(handle);
        root.append(panel);
        DocumentDraggableSupport.attach(panel, handle, DocumentDraggableSupport.DragAxis.BOTH);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 240, 120);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 25, 15, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 27, 16, -1, 0, 2, 1, 2L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 27, 16, 0, 0, 0, 0, 3L));

        Assert.assertEquals(1, clickEvents.size());
        assertElementUid(handle, clickEvents.get(0).getTarget());
        Assert.assertEquals(20.0F, panel.style().getLeft().getValue(), 0.001F);
        Assert.assertEquals(10.0F, panel.style().getTop().getValue(), 0.001F);
    }

    /**
     * 验证 drag handler 只有超过阈值后才进入真正拖拽并阻断 click。
     */
    @Test
    public void shouldActivateDragOnlyAfterCrossingThreshold() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode panel = document.div();
        final List<DocumentElementDragEvent.DragPhase> dragPhases =
                new ArrayList<DocumentElementDragEvent.DragPhase>();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();

        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(60));
        panel.style()
                .setWidth(UiStyleLength.px(60))
                .setHeight(UiStyleLength.px(20));
        panel.setDragHandler(new club.heiqi.uilib.ui.dom.DocumentElementDragHandler() {
            @Override
            public boolean onDrag(DocumentElementDragEvent event) {
                dragPhases.add(event.getPhase());
                return true;
            }
        });
        panel.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        root.append(panel);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 60,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 60);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 12, 11, -1, 0, 2, 1, 2L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 12, 11, 0, 0, 0, 0, 3L));

        Assert.assertEquals(2, dragPhases.size());
        Assert.assertEquals(DocumentElementDragEvent.DragPhase.START, dragPhases.get(0));
        Assert.assertEquals(DocumentElementDragEvent.DragPhase.END, dragPhases.get(1));
        Assert.assertEquals(1, clickEvents.size());

        dragPhases.clear();
        clickEvents.clear();

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 4L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 16, 10, -1, 0, 6, 0, 5L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 16, 10, 0, 0, 0, 0, 6L));

        Assert.assertEquals(3, dragPhases.size());
        Assert.assertEquals(DocumentElementDragEvent.DragPhase.START, dragPhases.get(0));
        Assert.assertEquals(DocumentElementDragEvent.DragPhase.DRAG, dragPhases.get(1));
        Assert.assertEquals(DocumentElementDragEvent.DragPhase.END, dragPhases.get(2));
        Assert.assertTrue(clickEvents.isEmpty());
    }

    /**
     * 验证 draggable="true" 会走浏览器式 dragstart / dragover / dragend 事件链。
     */
    @Test
    public void shouldDispatchHtmlLikeDragEventsForDraggableElement() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode list = document.div();
        ElementNode item = document.div();
        final List<String> events = new ArrayList<String>();

        root.style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(120));
        list.style()
                .setWidth(UiStyleLength.px(160))
                .setHeight(UiStyleLength.px(100));
        item.setAttribute("draggable", "true");
        item.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(30));
        item.setDragStartHandler(new club.heiqi.uilib.ui.dom.DocumentElementDragStartHandler() {
            @Override
            public boolean onDragStart(DocumentElementDragEvent event) {
                events.add("start:" + event.getTarget().getAttribute("draggable"));
                return true;
            }
        });
        item.setDragEndHandler(new club.heiqi.uilib.ui.dom.DocumentElementDragEndHandler() {
            @Override
            public boolean onDragEnd(DocumentElementDragEvent event) {
                events.add("end:" + event.getTarget().getAttribute("draggable"));
                return true;
            }
        });
        list.setDragOverHandler(new club.heiqi.uilib.ui.dom.DocumentElementDragOverHandler() {
            @Override
            public boolean onDragOver(DocumentElementDragEvent event) {
                events.add("over:" + event.getTarget().getAttribute("draggable"));
                return true;
            }
        });
        list.append(item);
        root.append(list);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 180, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 180, 120);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 18, 10, -1, 0, 8, 0, 2L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 26, 10, -1, 0, 8, 0, 3L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 26, 10, 0, 0, 0, 0, 4L));

        Assert.assertEquals("start:true", events.get(0));
        Assert.assertEquals("over:true", events.get(1));
        Assert.assertEquals("over:true", events.get(2));
        Assert.assertEquals("end:true", events.get(3));
        Assert.assertEquals(4, events.size());
    }

    /**
     * 验证浏览器式拖拽事件继续沿用 UILib 原生像素坐标，不回退到 MC GUI 缩放坐标。
     */
    @Test
    public void shouldExposeNativeDocumentCoordinatesForHtmlLikeDragEvents() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode item = document.div();
        final List<Integer> coordinates = new ArrayList<Integer>();

        root.style()
                .setWidth(UiStyleLength.px(180))
                .setHeight(UiStyleLength.px(120));
        item.setAttribute("draggable", "true");
        item.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(30));
        item.setDragStartHandler(new club.heiqi.uilib.ui.dom.DocumentElementDragStartHandler() {
            @Override
            public boolean onDragStart(DocumentElementDragEvent event) {
                coordinates.add(Integer.valueOf(event.getStartDocumentX()));
                coordinates.add(Integer.valueOf(event.getStartDocumentY()));
                coordinates.add(Integer.valueOf(event.getDocumentX()));
                coordinates.add(Integer.valueOf(event.getDocumentY()));
                return true;
            }
        });
        root.append(item);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 180, 120,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(100, 200, 180, 120);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 112, 218, 0, 0, 0, 0, 1L));
        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 130, 250, -1, 0, 18, 32, 2L));

        Assert.assertEquals(Integer.valueOf(12), coordinates.get(0));
        Assert.assertEquals(Integer.valueOf(18), coordinates.get(1));
        Assert.assertEquals(Integer.valueOf(30), coordinates.get(2));
        Assert.assertEquals(Integer.valueOf(50), coordinates.get(3));
    }

    /**
     * 验证 padding keyframe 运行期间和 forwards fill 后都会驱动运行态布局值。
     */
    @Test
    public void shouldRelayoutContentAndSiblingsDuringPaddingKeyframeAndForwardsFill() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("paddingPush")
                .setFloat(DocumentAnimationProperty.PADDING_LEFT, 4.0F, 20.0F)
                .setFloat(DocumentAnimationProperty.PADDING_RIGHT, 6.0F, 18.0F)
                .build());
        ElementNode root = document.getRootElement();
        ElementNode row = document.div();
        ElementNode animated = document.div();
        TextNode label = animated.appendText("pad");
        ElementNode sibling = document.div();
        root.style().setWidth(UiStyleLength.px(160));
        row.style().setDisplay(UiDisplay.FLEX);
        animated.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(6), UiStyleLength.px(0),
                        UiStyleLength.px(4)))
                .setBackgroundColor(0xFF112233)
                .setAnimation("paddingPush", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        sibling.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF445566);
        row.append(animated).append(sibling);
        root.append(row);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 160, 40);

        widget.render(new RecordingUiRenderContext());
        animationClock.setCurrentTimeNanos(500_000_000L);
        RecordingUiRenderContext halfContext = new RecordingUiRenderContext();
        widget.render(halfContext);
        assertDrawCall(halfContext.drawCalls.get(0), 0, 0, 64, 20, 0xFF112233, 0, 0);
        assertDrawCall(halfContext.drawCalls.get(1), 64, 0, 84, 20, 0xFF445566, 0, 0);
        assertTextCall(halfContext.textCalls.get(0), label.getText(), 12, 0, 0xFFFFFFFF, false);

        animationClock.setCurrentTimeNanos(1_000_000_000L);
        RecordingUiRenderContext filledContext = new RecordingUiRenderContext();
        widget.render(filledContext);
        assertDrawCall(filledContext.drawCalls.get(0), 0, 0, 78, 20, 0xFF112233, 0, 0);
        assertDrawCall(filledContext.drawCalls.get(1), 78, 0, 98, 20, 0xFF445566, 0, 0);
        assertTextCall(filledContext.textCalls.get(0), label.getText(), 20, 0, 0xFFFFFFFF, false);

        animated.style().setPadding(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(6), UiStyleLength.px(0),
                UiStyleLength.px(8)));
        RecordingUiRenderContext authorContext = new RecordingUiRenderContext();
        widget.render(authorContext);
        assertDrawCall(authorContext.drawCalls.get(0), 0, 0, 66, 20, 0xFF112233, 0, 0);
        assertDrawCall(authorContext.drawCalls.get(1), 66, 0, 86, 20, 0xFF445566, 0, 0);
        assertTextCall(authorContext.textCalls.get(0), label.getText(), 8, 0, 0xFFFFFFFF, false);
        Assert.assertEquals(0, widget.getActiveAnimationCount());
    }

    /**
     * 验证 height keyframe 会驱动后续 block sibling 的纵向重排。
     */
    @Test
    public void shouldRelayoutSiblingsDuringHeightKeyframe() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("heightGrow")
                .setFloat(DocumentAnimationProperty.HEIGHT, 20.0F, 60.0F)
                .build());
        ElementNode root = document.getRootElement();
        ElementNode animated = document.div();
        ElementNode sibling = document.div();
        root.style().setWidth(UiStyleLength.px(120));
        animated.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF112233)
                .setAnimation("heightGrow", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        sibling.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF445566);
        root.append(animated).append(sibling);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.render(new RecordingUiRenderContext());
        animationClock.setCurrentTimeNanos(500_000_000L);
        RecordingUiRenderContext halfContext = new RecordingUiRenderContext();
        widget.render(halfContext);
        assertDrawCall(halfContext.drawCalls.get(0), 0, 0, 40, 40, 0xFF112233, 0, 0);
        assertDrawCall(halfContext.drawCalls.get(1), 0, 40, 40, 60, 0xFF445566, 0, 0);

        animationClock.setCurrentTimeNanos(1_000_000_000L);
        RecordingUiRenderContext filledContext = new RecordingUiRenderContext();
        widget.render(filledContext);
        assertDrawCall(filledContext.drawCalls.get(0), 0, 0, 40, 60, 0xFF112233, 0, 0);
        assertDrawCall(filledContext.drawCalls.get(1), 0, 60, 40, 80, 0xFF445566, 0, 0);
    }

    /**
     * 验证清除 layout keyframe 声明后会恢复静态布局缓存路径。
     */
    @Test
    public void shouldReturnToStaticPaintCacheAfterLayoutKeyframeIsCleared() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("layoutGrow")
                .setFloat(DocumentAnimationProperty.WIDTH, 40.0F, 80.0F)
                .build());
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style().setWidth(UiStyleLength.px(120));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF000000)
                .setAnimation("layoutGrow", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        child.appendText("layout");
        root.append(child);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40, textMeasureService);
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.render(new RecordingUiRenderContext());
        animationClock.setCurrentTimeNanos(1_000_000_000L);
        RecordingUiRenderContext filledContext = new RecordingUiRenderContext();
        widget.render(filledContext);
        assertDrawCall(filledContext.drawCalls.get(0), 0, 0, 80, 20, 0xFF000000, 0, 0);

        child.style().clearAnimationName();
        RecordingUiRenderContext clearedContext = new RecordingUiRenderContext();
        widget.render(clearedContext);
        assertDrawCall(clearedContext.drawCalls.get(0), 0, 0, 40, 20, 0xFF000000, 0, 0);
        int clearedGeneration = widget.getPaintCacheGenerationForDiagnostics();
        int clearedMeasureCount = textMeasureService.getMeasureCount();
        Assert.assertEquals(0, widget.getActiveAnimationCount());

        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(clearedGeneration, widget.getPaintCacheGenerationForDiagnostics());
        Assert.assertEquals(clearedMeasureCount, textMeasureService.getMeasureCount());
    }

    /**
     * 验证 layout 动画期间会按运行态高度刷新 overflow auto 的滚动范围。
     */
    @Test
    public void shouldUpdateScrollRangeDuringHeightTransition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(50))
                .setOverflowY(UiOverflow.AUTO);
        child.style()
                .setHeight(UiStyleLength.px(40))
                .setBackgroundColor(0xFFAA5500)
                .setTransition(DocumentAnimationProperty.HEIGHT, 1000L);
        root.append(child);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 50,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 80, 50);

        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(0, widget.getMaxScrollTop(root));

        child.style().setHeight(UiStyleLength.px(100));
        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(0, widget.getMaxScrollTop(root));

        animationClock.setCurrentTimeNanos(500_000_000L);
        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(20, widget.getMaxScrollTop(root));

        animationClock.setCurrentTimeNanos(1_000_000_000L);
        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(50, widget.getMaxScrollTop(root));
    }

    /**
     * 验证 layout 动画让内容收缩时会夹取已有滚动偏移。
     */
    @Test
    public void shouldClampScrollOffsetWhenHeightTransitionShrinksContent() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(50))
                .setOverflowY(UiOverflow.AUTO);
        child.style()
                .setHeight(UiStyleLength.px(100))
                .setBackgroundColor(0xFFAA5500)
                .setTransition(DocumentAnimationProperty.HEIGHT, 1000L);
        root.append(child);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 50,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 80, 50);

        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(50, widget.getMaxScrollTop(root));
        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -240, 0,
                0, 1L)));
        Assert.assertEquals(50, widget.getScrollTop(root));

        child.style().setHeight(UiStyleLength.px(40));
        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(50, widget.getMaxScrollTop(root));
        Assert.assertEquals(50, widget.getScrollTop(root));

        animationClock.setCurrentTimeNanos(500_000_000L);
        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(20, widget.getMaxScrollTop(root));
        Assert.assertEquals(20, widget.getScrollTop(root));

        animationClock.setCurrentTimeNanos(1_000_000_000L);
        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(0, widget.getMaxScrollTop(root));
        Assert.assertEquals(0, widget.getScrollTop(root));
    }

    /**
     * 验证作者侧 keyframe animation 运行期间不会触发重新文本测量布局。
     */
    @Test
    public void shouldRenderDeclaredKeyframeAnimationWithoutRecomputingLayout() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setFloat(DocumentAnimationProperty.OPACITY, 1.0F, 0.4F)
                .setColor(DocumentAnimationProperty.BACKGROUND_COLOR, 0xFF000000, 0xFFFFFFFF)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(32))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF000000)
                .setOpacity(1.0F)
                .setAnimation("pulse", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        root.appendText("pulse");
        ManualAnimationClock animationClock = new ManualAnimationClock();
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40, textMeasureService);
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.render(new RecordingUiRenderContext());
        int measureCountAfterInitialRender = textMeasureService.getMeasureCount();
        Assert.assertEquals(2, widget.getActiveAnimationCount());

        animationClock.setCurrentTimeNanos(500_000_000L);
        RecordingUiRenderContext halfContext = new RecordingUiRenderContext();
        widget.render(halfContext);

        Assert.assertEquals(measureCountAfterInitialRender, textMeasureService.getMeasureCount());
        Assert.assertEquals(0x00808080, halfContext.drawCalls.get(0).surfaceStyle.fillColor & 0x00FFFFFF);
        Assert.assertTrue(widget.getActiveAnimationCount() > 0);

        animationClock.setCurrentTimeNanos(1_000_000_000L);
        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(0, widget.getActiveAnimationCount());
    }

    /**
     * 验证 keyframe animation 结束后组件会回到静态 paint command 缓存路径。
     */
    @Test
    public void shouldReturnToStaticPaintCacheAfterDeclaredKeyframeAnimationFinishes() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setColor(DocumentAnimationProperty.BACKGROUND_COLOR, 0xFF000000, 0xFFFFFFFF)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(32))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF000000)
                .setAnimation("pulse", 1000L);
        root.appendText("pulse");
        ManualAnimationClock animationClock = new ManualAnimationClock();
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40, textMeasureService);
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.render(new RecordingUiRenderContext());
        int initialMeasureCount = textMeasureService.getMeasureCount();
        int startGeneration = widget.getPaintCacheGenerationForDiagnostics();

        animationClock.setCurrentTimeNanos(500_000_000L);
        widget.render(new RecordingUiRenderContext());
        Assert.assertTrue(widget.getPaintCacheGenerationForDiagnostics() > startGeneration);
        Assert.assertEquals(initialMeasureCount, textMeasureService.getMeasureCount());

        animationClock.setCurrentTimeNanos(1_000_000_000L);
        widget.render(new RecordingUiRenderContext());
        int finishedGeneration = widget.getPaintCacheGenerationForDiagnostics();
        Assert.assertEquals(0, widget.getActiveAnimationCount());

        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(finishedGeneration, widget.getPaintCacheGenerationForDiagnostics());
        Assert.assertEquals(initialMeasureCount, textMeasureService.getMeasureCount());
    }

    /**
     * 验证 keyframe animation 声明被清除后组件会回到静态 paint command 缓存路径。
     */
    @Test
    public void shouldReturnToStaticPaintCacheAfterDeclaredKeyframeAnimationIsCleared() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setColor(DocumentAnimationProperty.BACKGROUND_COLOR, 0xFF000000, 0xFFFFFFFF)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(32))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF000000)
                .setAnimation("pulse", 1000L);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.render(new RecordingUiRenderContext());
        animationClock.setCurrentTimeNanos(500_000_000L);
        widget.render(new RecordingUiRenderContext());
        Assert.assertTrue(widget.getActiveAnimationCount() > 0);

        root.style().clearAnimationName();
        widget.render(new RecordingUiRenderContext());
        int clearedGeneration = widget.getPaintCacheGenerationForDiagnostics();
        Assert.assertEquals(0, widget.getActiveAnimationCount());

        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(clearedGeneration, widget.getPaintCacheGenerationForDiagnostics());
    }

    /**
     * 验证同名 keyframes 定义替换会重启动画但不重新文本测量布局。
     */
    @Test
    public void shouldRestartDeclaredKeyframeWhenRegisteredKeyframesChangeWithoutRecomputingLayout() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setColor(DocumentAnimationProperty.BACKGROUND_COLOR, 0xFF000000, 0xFFFFFFFF)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(32))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF000000)
                .setAnimation("pulse", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.BOTH);
        root.appendText("pulse");
        ManualAnimationClock animationClock = new ManualAnimationClock();
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40, textMeasureService);
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.render(new RecordingUiRenderContext());
        int measureCountAfterInitialRender = textMeasureService.getMeasureCount();
        animationClock.setCurrentTimeNanos(500_000_000L);
        widget.render(new RecordingUiRenderContext());

        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setColor(DocumentAnimationProperty.BACKGROUND_COLOR, 0xFF101010, 0xFF202020)
                .build());
        RecordingUiRenderContext replacedContext = new RecordingUiRenderContext();
        widget.render(replacedContext);

        Assert.assertEquals(measureCountAfterInitialRender, textMeasureService.getMeasureCount());
        Assert.assertEquals(0xFF101010, replacedContext.drawCalls.get(0).surfaceStyle.fillColor);
    }

    /**
     * 验证移除 keyframes 定义会取消动画并回到静态 paint command 缓存路径。
     */
    @Test
    public void shouldReturnToStaticPaintCacheAfterRegisteredKeyframesAreRemoved() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setColor(DocumentAnimationProperty.BACKGROUND_COLOR, 0xFF000000, 0xFFFFFFFF)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(32))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF000000)
                .setAnimation("pulse", 1000L);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.render(new RecordingUiRenderContext());
        animationClock.setCurrentTimeNanos(500_000_000L);
        widget.render(new RecordingUiRenderContext());
        Assert.assertTrue(widget.getActiveAnimationCount() > 0);

        document.unregisterKeyframes("pulse");
        widget.render(new RecordingUiRenderContext());
        int removedGeneration = widget.getPaintCacheGenerationForDiagnostics();
        Assert.assertEquals(0, widget.getActiveAnimationCount());

        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(removedGeneration, widget.getPaintCacheGenerationForDiagnostics());
    }

    /**
     * 验证 forwards fill 后作者修改同属性目标值会回到作者值且不重新文本测量布局。
     */
    @Test
    public void shouldRenderAuthorTargetAfterForwardsFillChangesWithoutRecomputingLayout() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("pulse")
                .setColor(DocumentAnimationProperty.BACKGROUND_COLOR, 0xFF000000, 0xFFFFFFFF)
                .build());
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(32))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF000000)
                .setAnimation("pulse", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        root.appendText("pulse");
        ManualAnimationClock animationClock = new ManualAnimationClock();
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40, textMeasureService);
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.render(new RecordingUiRenderContext());
        int measureCountAfterInitialRender = textMeasureService.getMeasureCount();
        animationClock.setCurrentTimeNanos(1_000_000_000L);
        RecordingUiRenderContext filledContext = new RecordingUiRenderContext();
        widget.render(filledContext);
        Assert.assertEquals(0xFFFFFFFF, filledContext.drawCalls.get(0).surfaceStyle.fillColor);

        root.style().setBackgroundColor(0xFF123456);
        RecordingUiRenderContext authorContext = new RecordingUiRenderContext();
        widget.render(authorContext);

        Assert.assertEquals(measureCountAfterInitialRender, textMeasureService.getMeasureCount());
        Assert.assertEquals(0xFF123456, authorContext.drawCalls.get(0).surfaceStyle.fillColor);
        Assert.assertEquals(0, widget.getActiveAnimationCount());
    }

    /**
     * 验证 paint-only 样式变更只刷新绘制样式，不重新执行文本测量布局。
     */
    @Test
    public void shouldRefreshPaintOnlyStyleWithoutRecomputingLayout() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(24))
                .setOverflowWrap(UiOverflowWrap.BREAK_WORD)
                .setBackgroundColor(0xFF000000)
                .setTextColor(0xFFFFFFFF);
        root.appendText("abcdefg");
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 80, textMeasureService);
        widget.applyLayoutBounds(0, 0, 80, 80);

        RecordingUiRenderContext initialContext = new RecordingUiRenderContext();
        widget.render(initialContext);
        int measureCountAfterInitialRender = textMeasureService.getMeasureCount();
        Assert.assertTrue(measureCountAfterInitialRender > 0);

        root.style()
                .setBackgroundColor(0xFFFFFFFF)
                .setTextColor(0xFF112233);
        RecordingUiRenderContext paintOnlyContext = new RecordingUiRenderContext();
        widget.render(paintOnlyContext);

        Assert.assertEquals(measureCountAfterInitialRender, textMeasureService.getMeasureCount());
        assertDrawCall(paintOnlyContext.drawCalls.get(0), 0, 0, 24, 54, 0xFFFFFFFF, 0, 0);
        Assert.assertEquals(0xFF112233, paintOnlyContext.textCalls.get(0).color);

        root.style().setWidth(UiStyleLength.px(32));
        widget.render(new RecordingUiRenderContext());

        Assert.assertTrue(textMeasureService.getMeasureCount() > measureCountAfterInitialRender);
    }

    /**
     * 验证 HTML-like 组件能消费滚轮事件并移动 overflow auto 内容。
     */
    @Test
    public void shouldScrollOverflowAutoContentWithMouseWheel() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        child.style()
                .setHeight(UiStyleLength.px(80))
                .setBackgroundColor(0xFFAA5500);
        root.append(child);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 20);
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);
        Assert.assertEquals(60, widget.getMaxScrollTop(root));

        boolean consumed = widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L));
        RecordingUiRenderContext scrolledRenderContext = new RecordingUiRenderContext();
        widget.render(scrolledRenderContext);

        Assert.assertTrue(consumed);
        Assert.assertEquals(36, widget.getScrollTop(root));
        Assert.assertEquals(3, scrolledRenderContext.drawCalls.size());
        assertDrawCall(scrolledRenderContext.drawCalls.get(0), 5, -29, 85, 51, 0xFFAA5500, 0, 0);
        assertDrawCall(scrolledRenderContext.drawCalls.get(1), 77, 9, 83, 25, 0x663B4A66, 0, 3);
        assertDrawCall(scrolledRenderContext.drawCalls.get(2), 77, 9, 83, 25, 0xDDBCD7FF, 0, 3);
    }

    /**
     * 验证 HUD 风格固定面板在鼠标命中后代内容区时，仍会滚动祖先 scroll host。
     */
    @Test
    public void shouldScrollHudLikePanelContentWhenWheelOnDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode header = document.div();
        ElementNode contentRoot = document.div();
        ElementNode card = null;

        root.style()
                .setWidth(UiStyleLength.px(320))
                .setHeight(UiStyleLength.px(540));
        panel.style()
                .setWidth(UiStyleLength.px(248))
                .setHeight(UiStyleLength.px(420))
                .setPadding(UiStyleLength.px(12));
        header.style()
                .setHeight(UiStyleLength.px(40));
        header.appendText("HUD Header");
        contentRoot.style()
                .setHeight(UiStyleLength.px(360))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        contentRoot.append(createTextBlock(document, "页面概览：用于复现 HUD 浮窗正文区内部滚动。"));
        contentRoot.append(createTextBlock(document, "摘要：滚轮命中后代卡片正文时，祖先 scroll host 仍应滚动。"));
        for (int index = 1; index <= 6; index++) {
            card = createHudLikeCard(document, index);
            contentRoot.append(card);
        }
        panel.append(header).append(contentRoot);
        root.append(panel);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 320, 540,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 320, 540);
        widget.render(new RecordingUiRenderContext());

        Assert.assertTrue(widget.getMaxScrollTop(contentRoot) > 0);
        Assert.assertNotNull(card);
        int scrollX = 40;
        int scrollY = 220;
        Assert.assertTrue(widget.findElementAt(scrollX, scrollY) != null);

        boolean consumed = widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, scrollX, scrollY, -1,
                -120, 0, 0, 1L));

        Assert.assertTrue(consumed);
        Assert.assertTrue(widget.getScrollTop(contentRoot) > 0);
    }

    /**
     * 验证当鼠标直接命中文本行时，祖先 scroll host 仍会消费滚轮并滚动。
     */
    @Test
    public void shouldScrollAncestorHostWhenPointerHitsNestedTextRun() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode scrollHost = document.div();
        ElementNode card = document.div();
        ElementNode body = document.div();

        root.style()
                .setWidth(UiStyleLength.px(280))
                .setHeight(UiStyleLength.px(220));
        scrollHost.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(140))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        card.style()
                .setPadding(UiStyleLength.px(8))
                .setMargin(UiStyleLength.px(6))
                .setBorderWidth(UiStyleLength.px(1));
        body.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F));
        body.appendText("这是用于验证祖先滚动宿主命中文本行时仍可滚动的长文本内容，必须在较窄宽度下发生多行换行，"
                + "这样测试才能命中真实 text run，而不是只命中空白区域。"
                + "继续补充第二段中文说明，确保滚动区域高度显著超过视口高度。"
                + "继续补充第三段中文说明，模拟 HUD 说明文案与正文卡片。"
                + "继续补充第四段中文说明，确保内部区域形成真实滚动。"
                + "继续补充第五段中文说明，避免只靠边框高度通过测试。");
        card.append(createTextBlock(document, "卡片标题：滚动祖先宿主命中测试"));
        card.append(body);
        scrollHost.append(card);
        root.append(scrollHost);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 280, 220,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 280, 220);
        widget.render(new RecordingUiRenderContext());

        Assert.assertTrue(widget.getMaxScrollTop(scrollHost) > 0);
        Assert.assertNotNull(widget.findElementAt(24, 52));

        boolean consumed = widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 24, 52, -1, -120, 0,
                0, 1L));

        Assert.assertTrue(consumed);
        Assert.assertTrue(widget.getScrollTop(scrollHost) > 0);
    }

    /**
     * 验证 fixed HUD 面板中的内部 scroll host 在命中后代正文时仍可滚动。
     */
    @Test
    public void shouldScrollFixedHudLikePanelContentWhenWheelOnDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode title = document.div();
        ElementNode diagnostics = document.div();
        ElementNode contentRoot = document.div();

        root.style()
                .setWidth(UiStyleLength.px(320))
                .setHeight(UiStyleLength.px(240));
        panel.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(0))
                .setTop(UiStyleLength.px(0))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setWidth(UiStyleLength.px(248))
                .setHeight(UiStyleLength.px(232))
                .setPadding(UiStyleLength.px(12));
        title.appendText("INTERACTIVE HUD");
        diagnostics.appendText("阶段: 有范围但未命中宿主");
        contentRoot.style()
                .setHeight(UiStyleLength.px(118))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        contentRoot.append(createTextBlock(document, "容器界面上方可见。点击次数 0，备注：把鼠标移到背包界面后尝试编辑我。"));
        contentRoot.append(createTextBlock(document, "底部提示标记：保留"));
        contentRoot.append(createTextBlock(document, "把鼠标移到背包界面后尝试编辑我 123"));
        for (int index = 1; index <= 8; index++) {
            contentRoot.append(createTextBlock(document,
                    "滚轮停在这里可查看内部内容，第 " + index + " 条示例说明。继续补充中文描述，确保形成明显纵向溢出。"));
        }
        panel.append(title).append(diagnostics).append(contentRoot);
        root.append(panel);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 320, 240,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 320, 240);
        widget.render(new RecordingUiRenderContext());

        Assert.assertTrue(widget.getMaxScrollTop(contentRoot) > 0);
        Assert.assertNotNull(widget.findElementAt(20, 140));

        boolean consumed = widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 20, 140, -1, -120, 0,
                0, 1L));

        Assert.assertTrue(consumed);
        Assert.assertTrue(widget.getScrollTop(contentRoot) > 0);
    }

    /**
     * 验证当前 HUD demo 等价控件树在正文文本区与输入框区域都能滚动内部 scroll host。
     */
    @Test
    public void shouldScrollCurrentHudDemoLikeTreeOnTextAndInputAreas() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode dragBar = document.div();
        ElementNode heroCard = document.div();
        ElementNode controlCard = document.div();
        ElementNode scrollContent = document.div();
        ElementNode contentBody = document.div();

        root.style()
                .setWidth(UiStyleLength.px(2048))
                .setHeight(UiStyleLength.px(1152));
        panel.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(1648))
                .setTop(UiStyleLength.px(18))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.UiAlignItems.START)
                .setWidth(UiStyleLength.px(360))
                .setHeight(UiStyleLength.px(368))
                .setPadding(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(8));
        dragBar.appendText("HUD 工具浮窗 · 拖住这里移动");

        heroCard.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(4));
        heroCard.append(createAutoWidthTextBlock(document, "INTERACTIVE HUD"));
        heroCard.append(createAutoWidthTextBlock(document, "容器界面可交互"));
        heroCard.append(createAutoWidthTextBlock(document, "主浮窗调试台"));
        heroCard.append(createAutoWidthTextBlock(document,
                "把工具浮窗停在背包右上区域，用于核对 HUD 层可见性、输入接管与滚轮状态。"));

        controlCard.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(6));
        controlCard.append(createAutoWidthTextBlock(document, "调试开关"));

        ElementNode debugToggleCard = document.div();
        debugToggleCard.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(6));
        debugToggleCard.append(createAutoWidthTextBlock(document, "显示 HUD 调试信息"));
        ElementNode debugToggleHost = document.div();
        debugToggleHost.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.auto());
        debugToggleHost.append(new DocumentToggleSwitchControl(document).setToggled(true).getElement());
        debugToggleCard.append(debugToggleHost);
        controlCard.append(debugToggleCard);
        controlCard.append(createAutoWidthTextBlock(document, "底部提示标记：保留"));

        scrollContent.style()
                .setFlexGrow(1.0F)
                .setWidth(UiStyleLength.percent(1.0F))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        contentBody.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.UiAlignItems.START)
                .setWidth(UiStyleLength.auto())
                .setRowGap(UiStyleLength.px(6));
        scrollContent.append(contentBody);

        ElementNode overviewCard = document.div();
        overviewCard.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(3));
        overviewCard.append(createAutoWidthTextBlock(document, "会话概览"));
        overviewCard.append(createAutoWidthTextBlock(document, "容器界面上方可见。点击次数 0，备注：把鼠标移到背包界面后尝试编辑我。"));
        contentBody.append(overviewCard);

        ElementNode noteCard = document.div();
        noteCard.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(4));
        noteCard.append(createAutoWidthTextBlock(document, "容器备注"));

        DocumentTextInputControl input = new DocumentTextInputControl(document)
                .setPlaceholder("在容器界面中输入备注")
                .setText("把鼠标移到背包界面后尝试编辑我");
        input.getElement().style()
                .setDisplay(UiDisplay.BLOCK)
                .setMargin(UiStyleLength.px(0));
        noteCard.append(input.getElement());

        DocumentButtonControl button = new DocumentButtonControl(document, "记录一次点击");
        button.getElement().style()
                .setDisplay(UiDisplay.BLOCK)
                .setMargin(UiStyleLength.px(0));
        noteCard.append(button.getElement());
        contentBody.append(noteCard);

        ElementNode debugCard = document.div();
        debugCard.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(3));
        debugCard.append(createAutoWidthTextBlock(document, "HUD DEBUG"));
        debugCard.append(createAutoWidthTextBlock(document, "滚轮监控：有范围但未命中宿主。偏移 0 / 439。"));
        contentBody.append(debugCard);

        ElementNode tipsCard = document.div();
        tipsCard.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(3));
        tipsCard.append(createAutoWidthTextBlock(document, "操作建议"));

        for (int index = 1; index <= 8; index++) {
            tipsCard.append(createAutoWidthTextBlock(document,
                    "滚轮停在这里可查看内部内容，第 " + index + " 条示例说明。继续补充中文描述，确保形成明显纵向溢出。"));
        }
        contentBody.append(tipsCard);

        panel.append(dragBar).append(heroCard).append(controlCard).append(scrollContent);
        root.append(panel);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 2048, 1152,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 2048, 1152);
        widget.render(new RecordingUiRenderContext());

        Assert.assertTrue(widget.getMaxScrollTop(scrollContent) > 0);
        int[] textPoint = findVisibleElementPoint(widget, scrollContent,
                findElementContainingDirectText(widget, "会话概览"));
        int[] inputPoint = findVisibleElementPoint(widget, scrollContent, input.getElement());
        Assert.assertNotNull(widget.findElementAt(textPoint[0], textPoint[1]));
        Assert.assertNotNull(widget.findElementAt(inputPoint[0], inputPoint[1]));

        boolean consumedOnText = widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, textPoint[0],
                textPoint[1], -1, -120, 0, 0, 1L));
        Assert.assertTrue(consumedOnText);
        Assert.assertTrue(widget.getScrollTop(scrollContent) > 0);

        inputPoint = findVisibleElementPoint(widget, scrollContent, input.getElement());
        boolean consumedOnInput = widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, inputPoint[0],
                inputPoint[1], -1, -120, 0, 0, 2L));
        Assert.assertTrue(consumedOnInput);
        Assert.assertTrue(widget.getScrollTop(scrollContent) > 0);
    }

    /**
     * 验证 HUD 风格卡片中的空文本节点在后续写入长文本后，会触发布局重算并扩展真实高度。
     */
    @Test
    public void shouldRelayoutHudLikeCardsAfterDeferredTextMutation() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode heroCard = document.div();
        ElementNode overviewCard = document.div();
        TextNode summaryText;
        TextNode bodyText;

        root.style()
                .setWidth(UiStyleLength.px(2048))
                .setHeight(UiStyleLength.px(1152));
        panel.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(1648))
                .setTop(UiStyleLength.px(18))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.UiAlignItems.START)
                .setWidth(UiStyleLength.px(360))
                .setPadding(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(8));
        heroCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.UiAlignItems.START)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(4));
        heroCard.append(createAutoWidthTextBlock(document, "INTERACTIVE HUD"));
        summaryText = appendDynamicTextLine(document, heroCard, "");

        overviewCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.UiAlignItems.START)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(6))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(3));
        overviewCard.append(createAutoWidthTextBlock(document, "会话概览"));
        bodyText = appendDynamicTextLine(document, overviewCard, "");

        panel.append(heroCard).append(overviewCard);
        root.append(panel);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 2048, 1152,
                DefaultTextMeasureService.getInstance());
        widget.applyLayoutBounds(0, 0, 2048, 1152);
        widget.render(new RecordingUiRenderContext());

        DocumentLayoutBox initialPanelBox = widget.resolveLayoutBoxForTest().getChildren().get(0);
        DocumentLayoutBox initialHeroCardBox = initialPanelBox.getChildren().get(0);
        DocumentLayoutBox initialOverviewCardBox = initialPanelBox.getChildren().get(1);

        summaryText.setText("把工具浮窗停在背包右上区域，用于核对 HUD 层可见性、输入接管与滚轮状态。");
        bodyText.setText("容器界面上方可见。点击次数 0，备注：把鼠标移到背包界面后尝试编辑我。"
                + "继续补充第二句说明，确保在 360 像素浮窗宽度下发生明显换行。"
                + "继续补充第三句说明，验证动态文本更新后卡片高度会随之扩展。");
        widget.render(new RecordingUiRenderContext());

        DocumentLayoutBox panelBox = widget.resolveLayoutBoxForTest().getChildren().get(0);
        DocumentLayoutBox heroCardBox = panelBox.getChildren().get(0);
        DocumentLayoutBox overviewCardBox = panelBox.getChildren().get(1);

        Assert.assertTrue(heroCardBox.getHeight() > initialHeroCardBox.getHeight());
        Assert.assertTrue(overviewCardBox.getHeight() > initialOverviewCardBox.getHeight());
        Assert.assertTrue(overviewCardBox.getTop() >= heroCardBox.getBottom());
        Assert.assertTrue(heroCardBox.getChildren().get(1).getContentHeight() > 18);
        Assert.assertTrue(overviewCardBox.getChildren().get(1).getContentHeight() > 18);
    }

    /**
     * 验证固定高度 HUD 面板中，顶部动态文本卡片变高后，会把下方 flexGrow 滚动区整体下推并压缩剩余高度。
     */
    @Test
    public void shouldPushFlexGrowScrollAreaDownAfterTopHudTextExpands() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode panel = document.div();
        ElementNode heroCard = document.div();
        ElementNode controlCard = document.div();
        ElementNode scrollContent = document.div();
        ElementNode contentBody = document.div();
        TextNode heroSummaryText;

        root.style()
                .setWidth(UiStyleLength.px(2048))
                .setHeight(UiStyleLength.px(1152));
        panel.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(1648))
                .setTop(UiStyleLength.px(18))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.UiAlignItems.START)
                .setWidth(UiStyleLength.px(360))
                .setHeight(UiStyleLength.px(368))
                .setPadding(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(8));
        heroCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.UiAlignItems.START)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(4));
        heroCard.append(createAutoWidthTextBlock(document, "INTERACTIVE HUD"));
        heroSummaryText = appendDynamicTextLine(document, heroCard, "");

        controlCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.UiAlignItems.START)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(6));
        controlCard.append(createAutoWidthTextBlock(document, "调试开关"));
        controlCard.append(createAutoWidthTextBlock(document, "底部提示标记：保留"));

        scrollContent.style()
                .setFlexGrow(1.0F)
                .setWidth(UiStyleLength.percent(1.0F))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        contentBody.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.UiAlignItems.STRETCH)
                .setWidth(UiStyleLength.percent(1.0F))
                .setRowGap(UiStyleLength.px(6));
        contentBody.append(createTextBlock(document, "会话概览"));
        contentBody.append(createTextBlock(document, "容器界面上方可见。点击次数 0，备注：把鼠标移到背包界面后尝试编辑我。"));
        scrollContent.append(contentBody);

        panel.append(heroCard).append(controlCard).append(scrollContent);
        root.append(panel);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 2048, 1152,
                DefaultTextMeasureService.getInstance());
        widget.applyLayoutBounds(0, 0, 2048, 1152);
        widget.render(new RecordingUiRenderContext());

        DocumentLayoutBox initialPanelBox = widget.resolveLayoutBoxForTest().getChildren().get(0);
        DocumentLayoutBox initialHeroCardBox = initialPanelBox.getChildren().get(0);
        DocumentLayoutBox initialControlCardBox = initialPanelBox.getChildren().get(1);
        DocumentLayoutBox initialScrollContentBox = initialPanelBox.getChildren().get(2);

        heroSummaryText.setText("把工具浮窗停在背包右上区域，用于核对 HUD 层可见性、输入接管与滚轮状态。"
                + "继续补充第二句说明，确保顶部卡片高度明显增长，并观察下方滚动区是否整体下推。"
                + "继续补充第三句说明，避免只增长一行导致问题被掩盖。");
        widget.render(new RecordingUiRenderContext());

        DocumentLayoutBox panelBox = widget.resolveLayoutBoxForTest().getChildren().get(0);
        DocumentLayoutBox heroCardBox = panelBox.getChildren().get(0);
        DocumentLayoutBox controlCardBox = panelBox.getChildren().get(1);
        DocumentLayoutBox scrollContentBox = panelBox.getChildren().get(2);

        Assert.assertTrue(heroCardBox.getHeight() > initialHeroCardBox.getHeight());
        Assert.assertTrue(controlCardBox.getTop() >= heroCardBox.getBottom());
        Assert.assertTrue(scrollContentBox.getTop() >= controlCardBox.getBottom());
        Assert.assertTrue(scrollContentBox.getTop() > initialScrollContentBox.getTop());
        Assert.assertTrue(scrollContentBox.getHeight() < initialScrollContentBox.getHeight());
        Assert.assertTrue(controlCardBox.getTop() >= initialControlCardBox.getTop());
    }

    /**
     * 验证固定高度 HUD 面板中的顶部卡片在声明 flex-shrink:0 后，不会被压缩到小于自然高度。
     */
    @Test
    public void shouldKeepTopHudCardsAtNaturalHeightWhenFlexShrinkIsDisabled() {
        UiDocument shrinkEnabledDocument = UiDocument.create();
        ElementNode shrinkEnabledRoot = shrinkEnabledDocument.getRootElement();
        shrinkEnabledRoot.style()
                .setWidth(UiStyleLength.px(2048))
                .setHeight(UiStyleLength.px(1152));
        appendHudPanelWithTopCards(shrinkEnabledDocument, shrinkEnabledRoot, false);
        HtmlLikeDocumentWidget unconstrainedWidget = new HtmlLikeDocumentWidget(shrinkEnabledDocument, 2048, 1152,
                DefaultTextMeasureService.getInstance());
        unconstrainedWidget.applyLayoutBounds(0, 0, 2048, 1152);
        unconstrainedWidget.render(new RecordingUiRenderContext());
        DocumentLayoutBox unconstrainedPanelBox = unconstrainedWidget.resolveLayoutBoxForTest().getChildren().get(0);
        DocumentLayoutBox shrinkEnabledHeroCardBox = unconstrainedPanelBox.getChildren().get(0);
        DocumentLayoutBox shrinkEnabledControlCardBox = unconstrainedPanelBox.getChildren().get(1);

        UiDocument constrainedDocument = UiDocument.create();
        ElementNode constrainedDocRoot = constrainedDocument.getRootElement();
        constrainedDocRoot.style()
                .setWidth(UiStyleLength.px(2048))
                .setHeight(UiStyleLength.px(1152));
        appendHudPanelWithTopCards(constrainedDocument, constrainedDocRoot, true);
        HtmlLikeDocumentWidget constrainedWidget = new HtmlLikeDocumentWidget(constrainedDocument, 2048, 1152,
                DefaultTextMeasureService.getInstance());
        constrainedWidget.applyLayoutBounds(0, 0, 2048, 1152);
        constrainedWidget.render(new RecordingUiRenderContext());
        DocumentLayoutBox constrainedPanelBox = constrainedWidget.resolveLayoutBoxForTest().getChildren().get(0);
        DocumentLayoutBox constrainedHeroCardBox = constrainedPanelBox.getChildren().get(0);
        DocumentLayoutBox constrainedControlCardBox = constrainedPanelBox.getChildren().get(1);

        Assert.assertTrue(constrainedHeroCardBox.getHeight() >= shrinkEnabledHeroCardBox.getHeight());
        Assert.assertTrue(constrainedControlCardBox.getHeight() >= shrinkEnabledControlCardBox.getHeight());
    }

    /**
     * 验证根视口滚动模式会让根元素承载页面级 overflow auto。
     */
    @Test
    public void shouldUseRootElementAsViewportScrollHost() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        child.style()
                .setHeight(UiStyleLength.px(96))
                .setBackgroundColor(0xFF225577);
        root.append(child);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.setViewportRootScrollingEnabled(true);
        widget.applyLayoutBounds(0, 0, 80, 40);

        Assert.assertTrue(widget.isViewportRootScrollingEnabled());
        Assert.assertEquals(56, widget.getMaxScrollTop(root));
        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L)));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertEquals(36, widget.getScrollTop(root));
        Assert.assertEquals(3, renderContext.drawCalls.size());
        assertDrawCall(renderContext.drawCalls.get(0), 0, -36, 80, 60, 0xFF225577, 0, 0);
        assertDrawCall(renderContext.drawCalls.get(1), 72, 2, 78, 38, 0x663B4A66, 0, 3);
        assertDrawCall(renderContext.drawCalls.get(2), 72, 10, 78, 34, 0xDDBCD7FF, 0, 3);
    }

    /**
     * 验证 HTML-like 根滚动条滑块可以通过真实输入路由拖拽滚动。
     */
    @Test
    public void shouldDragRootScrollbarThumbThroughInputRouter() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setOverflowY(UiOverflow.AUTO);
        child.style().setHeight(UiStyleLength.px(120));
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);
        Assert.assertEquals(80, widget.getMaxScrollTop(root));

        UiInputRouter router = new UiInputRouter();
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 75, 5, 0, 0, 0, 0,
                1L)));
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.MOVE, 75, 17, -1, 0, 0, 12,
                2L)));
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 75, 17, 0, 0, 0, 0,
                3L)));

        Assert.assertEquals(80, widget.getScrollTop(root));
    }

    /**
     * 验证点击滚动条轨道会滚动且不会透传为元素 click。
     */
    @Test
    public void shouldHandleScrollbarTrackClickWithoutDispatchingElementClick() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setOverflowY(UiOverflow.AUTO);
        root.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        child.style().setHeight(UiStyleLength.px(120));
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        UiInputRouter router = new UiInputRouter();
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 75, 34, 0, 0, 0, 0,
                1L)));
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 75, 34, 0, 0, 0, 0,
                2L)));

        Assert.assertTrue(widget.getScrollTop(root) > 0);
        Assert.assertTrue(clickEvents.isEmpty());
    }

    /**
     * 验证当前可见的内部滚动块滚动条也可以拖拽。
     */
    @Test
    public void shouldDragVisibleNestedScrollbarThumbThroughInputRouter() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode scroller = document.div();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(60));
        scroller.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setOverflowY(UiOverflow.AUTO);
        child.style().setHeight(UiStyleLength.px(120));
        scroller.append(child);
        root.append(scroller);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 100, 60,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 100, 60);
        Assert.assertEquals(80, widget.getMaxScrollTop(scroller));

        UiInputRouter router = new UiInputRouter();
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0, 0,
                1L)));
        Assert.assertEquals(36, widget.getScrollTop(scroller));
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 75, 10, 0, 0, 0, 0,
                2L)));
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.MOVE, 75, 17, -1, 0, 0, 7,
                3L)));
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 75, 17, 0, 0, 0, 0,
                4L)));

        Assert.assertEquals(80, widget.getScrollTop(scroller));
    }

    /**
     * 验证点击 HTML-like 子元素不会触发根视口滚动偏移。
     */
    @Test
    public void shouldKeepViewportRootScrollStableWhenFocusableHtmlElementIsClicked() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode focusableElement = document.div();
        ElementNode filler = document.div();
        root.style()
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        focusableElement.style().setHeight(UiStyleLength.px(24));
        focusableElement.setFocusable(true);
        filler.style().setHeight(UiStyleLength.px(160));
        root.append(focusableElement).append(filler);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.setViewportRootScrollingEnabled(true);
        widget.applyLayoutBounds(0, 0, 80, 40);
        widget.render(new RecordingUiRenderContext());

        Assert.assertTrue(widget.getMaxScrollTop(root) > 0);

        UiInputRouter router = new UiInputRouter();
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0,
                1L)));
        router.route(widget, mouseFrame(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0,
                2L)));

        Assert.assertEquals(0, widget.getScrollTop(root));
        assertElementUid(focusableElement, widget.getFocusedElement());
    }

    /**
     * 验证 HTML-like 组件可以命中屏幕坐标下的最深元素。
     */
    @Test
    public void shouldFindDeepestElementAtScreenPoint() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        ElementNode grandChild = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        grandChild.style()
                .setWidth(UiStyleLength.px(16))
                .setHeight(UiStyleLength.px(10));
        child.append(grandChild);
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 40);

        assertElementUid(grandChild, widget.findElementAt(10, 12));
        Assert.assertNull(widget.findElementAt(120, 12));
    }

    /**
     * 验证 HTML-like 组件会把 click 事件分发给命中元素并向父元素冒泡。
     */
    @Test
    public void shouldDispatchClickToHitElementAndBubbleToParent() {
        UiDocument document = UiDocument.create();
        final ElementNode root = document.getRootElement();
        final ElementNode child = document.div();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        root.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 12, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 12, 0, 0, 0, 0, 2L));

        Assert.assertEquals(1, clickEvents.size());
        assertElementUid(child, clickEvents.get(0).getTarget());
        assertElementUid(root, clickEvents.get(0).getCurrentTarget());
        Assert.assertEquals(5, clickEvents.get(0).getDocumentX());
        Assert.assertEquals(5, clickEvents.get(0).getDocumentY());
        Assert.assertEquals(0, clickEvents.get(0).getButton());
        Assert.assertEquals(2L, clickEvents.get(0).getTimeNanos());
    }

    /**
     * 验证 layout 动画期间端到端 click 使用运行态命中目标。
     */
    @Test
    public void shouldDispatchClickToRuntimeGeometryDuringWidthTransition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode row = document.div();
        ElementNode animated = document.div();
        final ElementNode sibling = document.div();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();
        root.style().setWidth(UiStyleLength.px(160));
        row.style().setDisplay(UiDisplay.FLEX);
        animated.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF112233)
                .setTransition(DocumentAnimationProperty.WIDTH, 1000L);
        sibling.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF445566);
        sibling.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        row.append(animated).append(sibling);
        root.append(row);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 160, 40);

        widget.render(new RecordingUiRenderContext());
        animated.style().setWidth(UiStyleLength.px(80));
        widget.render(new RecordingUiRenderContext());
        animationClock.setCurrentTimeNanos(500_000_000L);
        widget.render(new RecordingUiRenderContext());
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 70, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 70, 10, 0, 0, 0, 0, 2L));

        Assert.assertEquals(1, clickEvents.size());
        assertElementUid(sibling, clickEvents.get(0).getTarget());
        Assert.assertEquals(70, clickEvents.get(0).getDocumentX());
        Assert.assertEquals(10, clickEvents.get(0).getDocumentY());
    }

    /**
     * 验证 layout 动画期间 down/up 都命中同一运行态目标时才触发 click。
     */
    @Test
    public void shouldRequireSameRuntimeTargetForClickDuringWidthTransition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode row = document.div();
        final ElementNode animated = document.div();
        final ElementNode sibling = document.div();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();
        root.style().setWidth(UiStyleLength.px(160));
        row.style().setDisplay(UiDisplay.FLEX);
        animated.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF112233)
                .setTransition(DocumentAnimationProperty.WIDTH, 1000L);
        sibling.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF445566);
        animated.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        sibling.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        row.append(animated).append(sibling);
        root.append(row);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 160, 40);

        widget.render(new RecordingUiRenderContext());
        animated.style().setWidth(UiStyleLength.px(80));
        widget.render(new RecordingUiRenderContext());
        animationClock.setCurrentTimeNanos(500_000_000L);
        widget.render(new RecordingUiRenderContext());
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 55, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 70, 10, 0, 0, 0, 0, 2L));

        Assert.assertTrue(clickEvents.isEmpty());
    }

    /**
     * 验证滚动后的命中测试会使用内容偏移后的元素位置。
     */
    @Test
    public void shouldHitTestScrolledContentAtVisualPosition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowY(UiOverflow.AUTO);
        first.style().setHeight(UiStyleLength.px(40));
        second.style().setHeight(UiStyleLength.px(40));
        root.append(first).append(second);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 20);

        assertElementUid(first, widget.findElementAt(10, 10));
        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L)));

        assertElementUid(second, widget.findElementAt(10, 10));
    }

    /**
     * 验证滚轮滚动后 hover 会按当前鼠标位置重新切换。
     */
    @Test
    public void shouldRefreshHoverAfterMouseWheelScrollMovesContent() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();
        final List<String> hoverEvents = new ArrayList<String>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowY(UiOverflow.AUTO);
        first.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        second.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        first.setHoverHandler(new DocumentElementHoverHandler() {
            @Override
            public boolean onHoverChanged(DocumentElementHoverEvent event) {
                hoverEvents.add("first:" + event.isHovered() + ":" + event.getDocumentX() + ":"
                        + event.getDocumentY());
                return true;
            }
        });
        second.setHoverHandler(new DocumentElementHoverHandler() {
            @Override
            public boolean onHoverChanged(DocumentElementHoverEvent event) {
                hoverEvents.add("second:" + event.isHovered() + ":" + event.getDocumentX() + ":"
                        + event.getDocumentY());
                return true;
            }
        });
        root.append(first).append(second);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 20);

        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 10, 10, -1, 0, 0, 0, 1L));
        Assert.assertEquals(1, hoverEvents.size());
        Assert.assertEquals("first:true:10:10", hoverEvents.get(0));

        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 2L)));
        Assert.assertEquals(3, hoverEvents.size());
        Assert.assertEquals("first:false:10:10", hoverEvents.get(1));
        Assert.assertEquals("second:true:10:10", hoverEvents.get(2));
    }

    /**
     * 验证 HTML-like 组件会聚焦命中元素并向其分发文本与键盘事件。
     */
    @Test
    public void shouldFocusHitElementAndDispatchTextAndKeyEvents() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode input = document.div();
        final List<DocumentElementFocusEvent> focusEvents = new ArrayList<DocumentElementFocusEvent>();
        final List<DocumentElementTextInputEvent> textEvents = new ArrayList<DocumentElementTextInputEvent>();
        final List<DocumentElementKeyEvent> keyEvents = new ArrayList<DocumentElementKeyEvent>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        input.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        input.setFocusable(true)
                .setFocusHandler(new DocumentElementFocusHandler() {
                    @Override
                    public void onFocusChanged(DocumentElementFocusEvent event) {
                        focusEvents.add(event);
                    }
                })
                .setTextInputHandler(new DocumentElementTextInputHandler() {
                    @Override
                    public boolean onTextInput(DocumentElementTextInputEvent event) {
                        textEvents.add(event);
                        return true;
                    }
                })
                .setKeyHandler(new DocumentElementKeyHandler() {
                    @Override
                    public boolean onKey(DocumentElementKeyEvent event) {
                        keyEvents.add(event);
                        return true;
                    }
                });
        root.append(input);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 40);

        Assert.assertTrue(widget.isFocusable());
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 12, 0, 0, 0, 0, 1L));
        widget.onTextInput(new UiTextInputEvent("abc", 2L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_BACK, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 3L));

        assertElementUid(input, widget.getFocusedElement());
        Assert.assertEquals(1, focusEvents.size());
        Assert.assertTrue(focusEvents.get(0).isFocused());
        Assert.assertFalse(focusEvents.get(0).isFocusVisible());
        Assert.assertEquals(1, textEvents.size());
        assertElementUid(input, textEvents.get(0).getTarget());
        assertElementUid(input, textEvents.get(0).getCurrentTarget());
        Assert.assertEquals("abc", textEvents.get(0).getText());
        Assert.assertEquals(1, keyEvents.size());
        assertElementUid(input, keyEvents.get(0).getTarget());
        assertElementUid(input, keyEvents.get(0).getCurrentTarget());
        Assert.assertEquals(Keyboard.KEY_BACK, keyEvents.get(0).getKeyCode());
    }

    /**
     * 验证 HTML-like 组件失去 widget 焦点时会清空内部元素焦点。
     */
    @Test
    public void shouldClearFocusedElementWhenWidgetLosesFocus() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode input = document.div();
        final List<Boolean> focusEvents = new ArrayList<Boolean>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        input.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        input.setFocusable(true).setFocusHandler(new DocumentElementFocusHandler() {
            @Override
            public void onFocusChanged(DocumentElementFocusEvent event) {
                focusEvents.add(Boolean.valueOf(event.isFocused()));
            }
        });
        root.append(input);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 12, 0, 0, 0, 0, 1L));
        widget.onFocusChanged(false);

        Assert.assertNull(widget.getFocusedElement());
        Assert.assertEquals(Boolean.TRUE, focusEvents.get(0));
        Assert.assertEquals(Boolean.FALSE, focusEvents.get(1));
    }

    /**
     * 验证 HTML-like 组件会分发鼠标按下与松开的 active 状态。
     */
    @Test
    public void shouldDispatchActiveStateAroundMousePress() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode input = document.div();
        final List<Boolean> activeEvents = new ArrayList<Boolean>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        input.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        input.setActiveHandler(new DocumentElementActiveHandler() {
            @Override
            public boolean onActiveChanged(DocumentElementActiveEvent event) {
                activeEvents.add(Boolean.valueOf(event.isActive()));
                return true;
            }
        });
        root.append(input);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 12, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 12, 0, 0, 0, 0, 2L));

        Assert.assertEquals(Boolean.TRUE, activeEvents.get(0));
        Assert.assertEquals(Boolean.FALSE, activeEvents.get(1));
    }

    /**
     * 验证 HTML-like 组件会按 tabindex 与文档顺序处理内部 Tab 焦点遍历。
     */
    @Test
    public void shouldTraverseFocusableElementsInLayoutOrder() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode firstInput = document.div();
        ElementNode secondInput = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        firstInput.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        secondInput.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        firstInput.setFocusable(true);
        secondInput.setFocusable(true);
        root.append(firstInput).append(secondInput);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(5, 7, 80, 40);

        widget.onFocusTraversalEntered(false);
        assertElementUid(firstInput, widget.getFocusedElement());

        Assert.assertTrue(widget.onFocusTraversal(false));
        assertElementUid(secondInput, widget.getFocusedElement());
        Assert.assertFalse(widget.onFocusTraversal(false));
        assertElementUid(secondInput, widget.getFocusedElement());

        Assert.assertTrue(widget.onFocusTraversal(true));
        assertElementUid(firstInput, widget.getFocusedElement());
        widget.onFocusChanged(false);
        widget.onFocusTraversalEntered(true);
        assertElementUid(secondInput, widget.getFocusedElement());
    }

    /**
     * 验证 tabindex 运行时语义：正数优先，0 保持文档顺序，-1 跳过 Tab 但可鼠标聚焦。
     */
    @Test
    public void shouldRespectTabIndexDuringFocusTraversal() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode normal = document.div();
        ElementNode skipped = document.div();
        ElementNode priority = document.div();
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(80));
        normal.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        skipped.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        priority.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        normal.setFocusable(true).setAttribute("tabindex", "0");
        skipped.setFocusable(true).setAttribute("tabindex", "-1");
        priority.setFocusable(true).setAttribute("tabindex", "2");
        root.append(normal).append(skipped).append(priority);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 80);

        widget.onFocusTraversalEntered(false);
        assertElementUid(priority, widget.getFocusedElement());

        Assert.assertTrue(widget.onFocusTraversal(false));
        assertElementUid(normal, widget.getFocusedElement());
        Assert.assertFalse(widget.onFocusTraversal(false));

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 4, 24, 0, 0, 0, 0, 1L));
        assertElementUid(skipped, widget.getFocusedElement());
    }

    /**
     * 验证 Tab 切换到滚动区外的焦点元素时会自动滚动到可视区域。
     */
    @Test
    public void shouldScrollFocusedElementIntoViewDuringTabTraversal() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode firstInput = document.div();
        ElementNode spacer = document.div();
        ElementNode secondInput = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        firstInput.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        spacer.style().setHeight(UiStyleLength.px(48));
        secondInput.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        firstInput.setFocusable(true);
        secondInput.setFocusable(true);
        root.append(firstInput).append(spacer).append(secondInput);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onFocusTraversalEntered(false);
        Assert.assertEquals(0, widget.getScrollTop(root));

        Assert.assertTrue(widget.onFocusTraversal(false));

        assertElementUid(secondInput, widget.getFocusedElement());
        Assert.assertEquals(48, widget.getScrollTop(root));
    }

    /**
     * 验证 raw button 设置 disabled 属性后，Tab 不聚焦，鼠标不聚焦，移除 disabled 后可重新聚焦。
     */
    @Test
    public void shouldIgnoreRawDisabledButtonInFocusTraversal() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode rawButton = document.button();
        rawButton.setAttribute("disabled", "true");
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        rawButton.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(32));
        root.append(rawButton);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        // Tab 不聚焦 disabled button
        widget.onFocusTraversalEntered(false);
        Assert.assertNull(widget.getFocusedElement());

        // 鼠标点击也不聚焦 disabled button
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 4, 4, 0, 0, 0, 0, 1L));
        Assert.assertNull(widget.getFocusedElement());
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 4, 4, 0, 0, 0, 0, 2L));

        // 移除 disabled 后可重新聚焦
        rawButton.removeAttribute("disabled");
        widget.onFocusTraversalEntered(false);
        assertElementUid(rawButton, widget.getFocusedElement());
    }

    /**
     * 验证 raw input 设置 disabled 属性后，Tab 不聚焦，textInput 不响应。
     */
    @Test
    public void shouldIgnoreRawDisabledInputInFocusAndTextInput() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode rawInput = document.input();
        final List<DocumentElementTextInputEvent> textEvents = new ArrayList<DocumentElementTextInputEvent>();
        rawInput.setAttribute("disabled", "true")
                .setTextInputHandler(new DocumentElementTextInputHandler() {
                    @Override
                    public boolean onTextInput(DocumentElementTextInputEvent event) {
                        textEvents.add(event);
                        return true;
                    }
                });
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        rawInput.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(32));
        root.append(rawInput);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        // Tab 不聚焦 disabled input
        widget.onFocusTraversalEntered(false);
        Assert.assertNull(widget.getFocusedElement());

        // 即使程序化聚焦后，textInput 也不响应（disabled 拦截）
        rawInput.setFocusable(true);
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 4, 4, 0, 0, 0, 0, 1L));
        // disabled 阻止鼠标聚焦
        Assert.assertNull(widget.getFocusedElement());
        widget.onTextInput(new UiTextInputEvent("abc", 2L));
        Assert.assertTrue(textEvents.isEmpty());
    }

    /**
     * 验证 raw button 绑定 click handler 后，Tab 聚焦，Enter 触发 click，Space pressed 不触发，Space released 触发。
     */
    @Test
    public void shouldFireClickOnRawButtonFromKeyboard() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode rawButton = document.button();
        final List<DocumentElementClickEvent> clicks = new ArrayList<DocumentElementClickEvent>();
        rawButton.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clicks.add(event);
                return true;
            }
        });
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        rawButton.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(32));
        root.append(rawButton);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        // Tab 聚焦
        widget.onFocusTraversalEntered(false);
        assertElementUid(rawButton, widget.getFocusedElement());

        // Enter 触发 click
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 1L));
        Assert.assertEquals(1, clicks.size());

        // Space pressed 不触发
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 2L));
        Assert.assertEquals(1, clicks.size());

        // Space released 触发
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.RELEASED, false, false,
                false, false, 3L));
        Assert.assertEquals(2, clicks.size());
    }

    /**
     * 验证 raw button Space pressed 后失焦，released 不触发 click。
     */
    @Test
    public void shouldNotFireClickOnRawButtonSpaceReleaseAfterFocusLost() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode rawButton = document.button();
        final List<DocumentElementClickEvent> clicks = new ArrayList<DocumentElementClickEvent>();
        rawButton.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clicks.add(event);
                return true;
            }
        });
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        rawButton.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(32));
        root.append(rawButton);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onFocusTraversalEntered(false);
        assertElementUid(rawButton, widget.getFocusedElement());

        // Space pressed
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 1L));
        Assert.assertEquals(0, clicks.size());

        // 失焦
        widget.onFocusChanged(false);
        Assert.assertNull(widget.getFocusedElement());

        // Space released 不触发（焦点已丢失，spacePressed 已清理）
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.RELEASED, false, false,
                false, false, 2L));
        Assert.assertEquals(0, clicks.size());
    }

    /**
     * 验证 DocumentButtonControl 的键盘激活不会被 raw button 默认行为重复触发。
     */
    @Test
    public void shouldNotDuplicateClickOnDocumentButtonControlFromDefaultKeyBehavior() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final List<DocumentButtonActionEvent> actions = new ArrayList<DocumentButtonActionEvent>();
        DocumentButtonControl buttonControl = new DocumentButtonControl(document, "OK");
        buttonControl.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                actions.add(event);
            }
        });
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40));
        buttonControl.getElement().style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(32));
        root.append(buttonControl.getElement());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onFocusTraversalEntered(false);
        assertElementUid(buttonControl.getElement(), widget.getFocusedElement());

        // Enter 触发一次（由 DocumentButtonControl 的 key handler 消费，不走默认行为）
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 1L));
        Assert.assertEquals(1, actions.size());

        // Space pressed + released 触发一次
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED, false, false,
                false, false, 2L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.RELEASED, false, false,
                false, false, 3L));
        Assert.assertEquals(2, actions.size());
    }

    private static void assertDrawCall(DrawCall drawCall, int left, int top, int right, int bottom, int fillColor,
            int borderColor, int cornerRadius) {
        Assert.assertEquals(left, drawCall.left);
        Assert.assertEquals(top, drawCall.top);
        Assert.assertEquals(right, drawCall.right);
        Assert.assertEquals(bottom, drawCall.bottom);
        Assert.assertEquals(fillColor, drawCall.surfaceStyle.fillColor);
        Assert.assertEquals(borderColor, drawCall.surfaceStyle.borderColor);
        Assert.assertEquals(cornerRadius, drawCall.surfaceStyle.cornerRadius);
    }

    private static void assertTextCall(TextCall textCall, String text, int x, int y, int color, boolean shadow) {
        Assert.assertEquals(text, textCall.text);
        Assert.assertEquals(x, textCall.x);
        Assert.assertEquals(y, textCall.y);
        Assert.assertEquals(color, textCall.color);
        Assert.assertEquals(shadow, textCall.shadow);
    }

    private static void assertElementUid(ElementNode expectedElement, ElementNode actualElement) {
        Assert.assertNotNull(actualElement);
        Assert.assertEquals(expectedElement.__getElementUid(), actualElement.__getElementUid());
    }

    private static int[] findVisibleElementPoint(HtmlLikeDocumentWidget widget, ElementNode scrollHost,
            ElementNode target) {
        Assert.assertNotNull(target);
        for (int attempt = 0; attempt < 20; attempt++) {
            DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
            DocumentLayoutBox scrollHostBox = findLayoutBox(rootBox, scrollHost);
            DocumentLayoutBox targetBox = findLayoutBox(rootBox, target);
            Assert.assertNotNull(scrollHostBox);
            Assert.assertNotNull(targetBox);
            int scrollTop = widget.getScrollTop(scrollHost);
            int viewportLeft = widget.getAbsoluteX() + scrollHostBox.getContentLeft();
            int viewportTop = widget.getAbsoluteY() + scrollHostBox.getContentTop();
            int viewportRight = viewportLeft + scrollHostBox.getContentWidth();
            int viewportBottom = viewportTop + scrollHostBox.getContentHeight();
            int targetLeft = widget.getAbsoluteX() + targetBox.getContentLeft();
            int targetTop = widget.getAbsoluteY() + targetBox.getContentTop() - scrollTop;
            int targetRight = targetLeft + Math.max(1, targetBox.getContentWidth());
            int targetBottom = targetTop + Math.max(1, targetBox.getContentHeight());
            int left = Math.max(viewportLeft, targetLeft);
            int top = Math.max(viewportTop, targetTop);
            int right = Math.min(viewportRight, targetRight);
            int bottom = Math.min(viewportBottom, targetBottom);
            if (right > left && bottom > top) {
                return new int[] { left + Math.max(1, right - left) / 2, top + Math.max(1, bottom - top) / 2 };
            }
            int wheelDelta = targetTop >= viewportBottom ? -120 : 120;
            widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, viewportLeft + 4,
                    viewportTop + Math.max(1, scrollHostBox.getContentHeight()) / 2, -1, wheelDelta, 0, 0,
                    100L + attempt));
        }
        Assert.fail("目标元素未进入滚动视口");
        return new int[] { widget.getAbsoluteX(), widget.getAbsoluteY() };
    }

    private static DocumentLayoutBox findLayoutBox(DocumentLayoutBox box, ElementNode element) {
        if (box.getElement() == element) {
            return box;
        }
        for (DocumentLayoutBox child : box.getChildren()) {
            DocumentLayoutBox found = findLayoutBox(child, element);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static ElementNode findElementContainingDirectText(HtmlLikeDocumentWidget widget, String expectedText) {
        return findElementContainingDirectText(widget.getDocument().getRootElement(), expectedText);
    }

    private static ElementNode findElementContainingDirectText(ElementNode element, String expectedText) {
        for (club.heiqi.uilib.ui.dom.DocumentNode child : element.getChildren()) {
            if (child instanceof TextNode && expectedText.equals(((TextNode) child).getText())) {
                return element;
            }
            if (child instanceof ElementNode) {
                ElementNode found = findElementContainingDirectText((ElementNode) child, expectedText);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static UiInputFrame mouseFrame(UiMouseEvent event) {
        return new UiInputFrame(event.getMouseX(), event.getMouseY(), Collections.singletonList(event),
                Collections.<UiKeyEvent>emptyList(), Collections.<UiTextInputEvent>emptyList());
    }

    private static ElementNode createHudLikeCard(UiDocument document, int index) {
        ElementNode card = document.div();
        card.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setMargin(UiStyleLength.px(6))
                .setBorderWidth(UiStyleLength.px(1));
        card.append(createTextBlock(document, "卡片 " + index + " 标题"));
        card.append(createTextBlock(document, "卡片 " + index + " 描述：用于构造 HUD 面板内部固定高度滚动区域。"));
        card.append(createTextBlock(document,
                "卡片 " + index + " 正文：这是一段较长的中文说明，用于确保在较窄 HUD 面板宽度下发生多行换行，"
                        + "并且总高度明显超过固定内容区视口。继续补充第二句说明，验证滚轮命中后代卡片正文时，祖先滚动宿主仍会移动。"));
        return card;
    }

    private static ElementNode createTextBlock(UiDocument document, String text) {
        ElementNode block = document.div();
        block.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.percent(1.0F));
        block.appendText(text);
        return block;
    }

    private static ElementNode createAutoWidthTextBlock(UiDocument document, String text) {
        ElementNode block = document.div();
        block.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.auto());
        block.appendText(text);
        return block;
    }

    private static TextNode appendDynamicTextLine(UiDocument document, ElementNode parent, String text) {
        ElementNode line = document.div();
        line.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.auto());
        TextNode textNode = line.appendText(text);
        parent.append(line);
        return textNode;
    }

    private static void appendHudPanelWithTopCards(UiDocument document, ElementNode root,
            boolean disableShrink) {
        ElementNode panel = document.div();
        ElementNode heroCard = document.div();
        ElementNode controlCard = document.div();
        ElementNode scrollContent = document.div();

        panel.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(1648))
                .setTop(UiStyleLength.px(18))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.UiAlignItems.START)
                .setWidth(UiStyleLength.px(360))
                .setHeight(UiStyleLength.px(368))
                .setPadding(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(8));
        heroCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.UiAlignItems.START)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(4));
        if (disableShrink) {
            heroCard.style().setFlexShrink(0.0F);
        }
        heroCard.append(createAutoWidthTextBlock(document, "INTERACTIVE HUD"));
        heroCard.append(createAutoWidthTextBlock(document,
                "把工具浮窗停在背包右上区域，用于核对 HUD 层可见性、输入接管与滚轮状态。继续补充第二句说明，确保顶部卡片出现明显换行。"));

        controlCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.UiAlignItems.START)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(6));
        if (disableShrink) {
            controlCard.style().setFlexShrink(0.0F);
        }
        controlCard.append(createAutoWidthTextBlock(document, "调试开关"));
        controlCard.append(createAutoWidthTextBlock(document, "底部提示标记：保留"));

        scrollContent.style()
                .setFlexGrow(1.0F)
                .setWidth(UiStyleLength.percent(1.0F))
                .setOverflowY(UiOverflow.AUTO);
        scrollContent.append(createTextBlock(document, "会话概览"));

        panel.append(heroCard).append(controlCard).append(scrollContent);
        root.append(panel);
    }

    /**
     * 记录 surface 绘制调用的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<DrawCall> drawCalls = new ArrayList<DrawCall>();
        private final List<TextCall> textCalls = new ArrayList<TextCall>();

        private RecordingUiRenderContext() {
            super(320, 240, 0, 0, 0.0F);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            drawCalls.add(new DrawCall(left, top, right, bottom, surfaceStyle));
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            drawText(text, x, y, color, shadow, TextContentMode.UILIB_RAW);
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow, TextContentMode textContentMode) {
            textCalls.add(new TextCall(text, x, y, color, shadow, textContentMode));
        }

        @Override
        public boolean supportsDeferredTextBatching() {
            return false;
        }

        @Override
        public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
                int cornerRadius) {}

        @Override
        public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {}

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {}

        @Override
        public void pushClip(int left, int top, int right, int bottom,
                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {}

        @Override
        public void popClip() {}
    }

    /**
     * 单次 surface 绘制记录。
     */
    private static final class DrawCall {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final UiSurfaceStyle surfaceStyle;

        private DrawCall(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.surfaceStyle = surfaceStyle;
        }
    }

    /**
     * 单次文本绘制记录。
     */
    private static final class TextCall {

        private final String text;
        private final int x;
        private final int y;
        private final int color;
        private final boolean shadow;
        private final TextContentMode textContentMode;

        private TextCall(String text, int x, int y, int color, boolean shadow, TextContentMode textContentMode) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.shadow = shadow;
            this.textContentMode = textContentMode;
        }
    }

    /**
     * 供动画测试使用的手动时间源。
     */
    private static final class ManualAnimationClock implements DocumentAnimationClock {

        private long currentTimeNanos;

        private void setCurrentTimeNanos(long currentTimeNanos) {
            this.currentTimeNanos = currentTimeNanos;
        }

        @Override
        public long getCurrentTimeNanos() {
            return currentTimeNanos;
        }
    }

    /**
     * 记录测量次数的确定性文本测量服务。
     */
    private static final class CountingTextMeasureService implements TextMeasureService {

        private int measureCount;

        private int getMeasureCount() {
            return measureCount;
        }

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            measureCount++;
            return text == null ? 0 : text.length() * 4;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            measureCount++;
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }
            int maxLength = Math.max(0, targetWidth / 4);
            return text.substring(0, Math.min(text.length(), maxLength));
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            measureCount++;
            if (text == null || text.isEmpty() || wrapWidth <= 0) {
                return Collections.emptyList();
            }
            List<String> lines = new ArrayList<String>();
            int maxCharsPerLine = Math.max(1, wrapWidth / 4);
            for (int index = 0; index < text.length(); index += maxCharsPerLine) {
                lines.add(text.substring(index, Math.min(text.length(), index + maxCharsPerLine)));
            }
            return lines;
        }
    }

    /**
     * 供 widget 测试使用的确定性文本测量服务。
     */
    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 4;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }
            int maxLength = Math.max(0, targetWidth / 4);
            return text.substring(0, Math.min(text.length(), maxLength));
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            if (text == null || text.isEmpty() || wrapWidth <= 0) {
                return Collections.emptyList();
            }
            List<String> lines = new ArrayList<String>();
            int maxCharsPerLine = Math.max(1, wrapWidth / 4);
            for (int index = 0; index < text.length(); index += maxCharsPerLine) {
                lines.add(text.substring(index, Math.min(text.length(), index + maxCharsPerLine)));
            }
            return lines;
        }
    }
}
