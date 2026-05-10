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
import club.heiqi.uilib.ui.dom.control.DocumentDraggableSupport;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.input.UiInputRouter;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleInsets;
import club.heiqi.uilib.ui.style.UiStyleLength;
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
     * 验证 HTML-like 组件会按布局树顺序处理内部 Tab 焦点遍历。
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

    private static UiInputFrame mouseFrame(UiMouseEvent event) {
        return new UiInputFrame(event.getMouseX(), event.getMouseY(), Collections.singletonList(event),
                Collections.<UiKeyEvent>emptyList(), Collections.<UiTextInputEvent>emptyList());
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
            textCalls.add(new TextCall(text, x, y, color, shadow));
        }

        @Override
        public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
                int cornerRadius) {}

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {}

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

        private TextCall(String text, int x, int y, int color, boolean shadow) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.shadow = shadow;
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
