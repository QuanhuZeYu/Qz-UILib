package club.heiqi.uilib.ui.document;

import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertDrawCall;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertElementUid;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertTextCall;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.animation.DocumentAnimationFillMode;
import club.heiqi.uilib.ui.animation.DocumentAnimationImpact;
import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.animation.DocumentKeyframes;
import club.heiqi.uilib.ui.control.DocumentSelectControl;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.CountingTextMeasureService;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.DeterministicTextMeasureService;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.ManualAnimationClock;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.RecordingUiRenderContext;
import club.heiqi.uilib.ui.dom.DocumentElementAnimationEndEvent;
import club.heiqi.uilib.ui.dom.DocumentElementAnimationEndHandler;
import club.heiqi.uilib.ui.dom.DocumentElementBounds;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionEndEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionEndHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionCancelEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionCancelHandler;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiOverflowWrap;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.values.UiTransform;

/**
 * `HtmlLikeDocumentWidget` 的动画运行时契约测试。
 */
public class HtmlLikeDocumentWidgetAnimationRuntimeTest {

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
     * 验证 display:none 中断运行中 transition 时会派发 transitioncancel。
     */
    @Test
    public void shouldDispatchTransitionCancelWhenDisplayNoneInterruptsRunningTransition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode animated = document.div();
        final List<DocumentAnimationProperty> cancelledProperties = new ArrayList<DocumentAnimationProperty>();
        root.style().setWidth(UiStyleLength.px(80));
        animated.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(1.0F)
                .setTransition(DocumentAnimationProperty.OPACITY, 1000L);
        animated.setTransitionCancelHandler(new DocumentElementTransitionCancelHandler() {
            @Override
            public boolean onTransitionCancel(DocumentElementTransitionCancelEvent event) {
                cancelledProperties.add(event.getProperty());
                return false;
            }
        });
        root.append(animated);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.render(new RecordingUiRenderContext());
        animated.style().setOpacity(0.0F);
        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(1, widget.getActiveAnimationCount());

        animationClock.setCurrentTimeNanos(500_000_000L);
        animated.style().setDisplay(UiDisplay.NONE);
        widget.render(new RecordingUiRenderContext());

        Assert.assertEquals(0, widget.getActiveAnimationCount());
        Assert.assertEquals(1, cancelledProperties.size());
        Assert.assertEquals(DocumentAnimationProperty.OPACITY, cancelledProperties.get(0));
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
     * 验证 transform transition 中间帧会统一 fixed containing block、clip、命中与滚动范围语义。
     */
    @Test
    public void shouldUseRuntimeTransformForFixedDescendantClipHitAndScrollRange() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode scroller = document.div();
        ElementNode transformedClip = document.div();
        ElementNode fixed = document.div();
        root.style().setWidth(UiStyleLength.px(140)).setHeight(UiStyleLength.px(80));
        scroller.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(40))
                .setOverflowY(UiOverflow.AUTO);
        transformedClip.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(40))
                .setMarginLeft(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN)
                .setTransform(UiTransform.translate(40.0F, 0.0F))
                .setTransition(DocumentAnimationProperty.TRANSLATE_X, 1000L);
        fixed.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(60))
                .setPosition(UiPosition.FIXED)
                .setTop(UiStyleLength.px(5))
                .setLeft(UiStyleLength.px(40))
                .setBackgroundColor(0xFF445566);
        transformedClip.append(fixed);
        scroller.append(transformedClip);
        root.append(scroller);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 140, 80,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 140, 80);

        widget.resolveLayoutBoxForTest();
        transformedClip.style().setTransform(UiTransform.identity());
        widget.resolveLayoutBoxForTest();
        animationClock.setCurrentTimeNanos(500_000_000L);
        widget.resolveLayoutBoxForTest();
        DocumentElementBounds fixedBounds = fixed.__getVisualDocumentBounds();

        Assert.assertTrue(widget.hasLayoutRuntimeValueForDiagnostics());
        Assert.assertEquals(80, fixedBounds.getLeft());
        Assert.assertEquals(5, fixedBounds.getTop());
        Assert.assertTrue(widget.getMaxScrollTop(scroller) > 0);
        assertElementUid(fixed, widget.findElementAt(82, 8));
        assertElementUid(scroller, widget.findElementAt(92, 8));
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
     * 验证 transitionend 与 animationend 会向作者派发完成事件。
     */
    @Test
    public void shouldDispatchTransitionEndAndAnimationEndEvents() {
        UiDocument document = UiDocument.create();
        document.registerKeyframes(DocumentKeyframes.named("fade")
                .setColor(DocumentAnimationProperty.BACKGROUND_COLOR, 0xFF000000, 0xFFFFFFFF)
                .build());
        ElementNode root = document.getRootElement();
        final List<DocumentElementTransitionEndEvent> transitionEndEvents =
                new ArrayList<DocumentElementTransitionEndEvent>();
        final List<DocumentElementAnimationEndEvent> animationEndEvents =
                new ArrayList<DocumentElementAnimationEndEvent>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setBackgroundColor(0xFF000000)
                .setOpacity(1.0F)
                .setTransition(DocumentAnimationProperty.OPACITY, 1000L)
                .setAnimation("fade", 1000L);
        root.setTransitionEndHandler(new DocumentElementTransitionEndHandler() {
            @Override
            public boolean onTransitionEnd(DocumentElementTransitionEndEvent event) {
                transitionEndEvents.add(event);
                return true;
            }
        });
        root.setAnimationEndHandler(new DocumentElementAnimationEndHandler() {
            @Override
            public boolean onAnimationEnd(DocumentElementAnimationEndEvent event) {
                animationEndEvents.add(event);
                return true;
            }
        });
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.render(new RecordingUiRenderContext());
        root.style().setOpacity(0.0F);
        widget.render(new RecordingUiRenderContext());
        animationClock.setCurrentTimeNanos(1_000_000_000L);
        widget.render(new RecordingUiRenderContext());

        Assert.assertEquals(1, transitionEndEvents.size());
        Assert.assertEquals(DocumentAnimationProperty.OPACITY, transitionEndEvents.get(0).getProperty());
        Assert.assertEquals(1_000_000_000L, transitionEndEvents.get(0).getElapsedTimeNanos());
        Assert.assertEquals(1, animationEndEvents.size());
        Assert.assertEquals("fade", animationEndEvents.get(0).getAnimationName());
        Assert.assertEquals(1_000_000_000L, animationEndEvents.get(0).getElapsedTimeNanos());
    }

    /**
     * 验证 top-layer popup 也会进入共享动画时间线更新与命令式动画启动链路。
     */
    @Test
    public void shouldAnimateTopLayerPopupThroughSharedScene() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        DocumentSelectControl selectControl = new DocumentSelectControl(document, "A", "B", "C");
        root.style()
                .setWidth(UiStyleLength.px(240))
                .setHeight(UiStyleLength.px(180));
        selectControl.getElement().style().setWidth(UiStyleLength.px(180));
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 240, 180,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 240, 180);
        root.append(selectControl.getElement());

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 20, 12, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 20, 12, 0, 0, 0, 0, 2L));
        ElementNode popup = findListboxElement(root);
        Assert.assertNotNull(popup);
        Assert.assertTrue(document.__isTopLayerElement(popup));

        widget.render(new RecordingUiRenderContext());
        popup.animate(DocumentKeyframes.named("popup-fade")
                .setFloat(DocumentAnimationProperty.OPACITY, 1.0F, 0.0F)
                .build(), 1000L);
        widget.render(new RecordingUiRenderContext());

        Assert.assertTrue(widget.getActiveAnimationCount() > 0);

        animationClock.setCurrentTimeNanos(1_000_000_000L);
        widget.render(new RecordingUiRenderContext());

        Assert.assertEquals(0, widget.getActiveAnimationCount());
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
     * 验证仅 opacity（COMPOSITE 级）变化时端到端命中 composite-only 就地回放（resolvePaintLayoutBox 路②），
     * 命令列表不重建。
     *
     * <p>黑盒断言组合坐实路②命中、排除路①（双未变直接返回）与路③（全量重建）：</p>
     * <ul>
     *   <li>{@code paintCacheGeneration} 前后相等——命令未重建（重建必递增 generation，见
     *       {@code HtmlLikeDocumentWidget.resolvePaintCommands} 仅在重建分支 {@code paintCacheGeneration++}），
     *       排除路③；</li>
     *   <li>{@code paintVersion} 前后相等——COMPOSITE 不污染 paintVersion；</li>
     *   <li>{@code compositeVersion} 前后 +1——确有 COMPOSITE 级变更发生且被独立记账，排除路①。</li>
     * </ul>
     *
     * <p>前置：先渲染一帧建立 paint 命令缓存（{@code cachedPaintScrollVersion >= 0}），否则
     * {@code tryApplyCompositeReplayOnCache} 因「未构建过命令」返回 false 落到路③。优先黑盒信号，
     * 不反射私有字段 {@code compositeReplayAppliedThisResolve}。</p>
     */
    @Test
    public void shouldHitCompositeReplayWhenOnlyOpacityChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style().setWidth(UiStyleLength.px(100)).setHeight(UiStyleLength.px(40));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(10))
                .setOpacity(0.5F)
                .setBackgroundColor(0xFF223344);
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 48);

        // 首帧建立 paint 命令缓存（路②命中的前置条件）。
        widget.render(new RecordingUiRenderContext());
        int baselineGeneration = widget.getPaintCacheGenerationForDiagnostics();

        // 再渲染一帧确认静态缓存命中（无变化时 generation 不变，对应路①）。
        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(baselineGeneration, widget.getPaintCacheGenerationForDiagnostics());

        int paintVersionBefore = document.getPaintVersion();
        int compositeVersionBefore = document.getCompositeVersion();

        // 仅改 opacity（0.5 → 0.25，均 < 0.999 不跨 paint-context 阈值）：COMPOSITE 级变更。
        child.style().setOpacity(0.25F);
        widget.render(new RecordingUiRenderContext());

        // 命令未重建（generation 不变 = 命令列表实例复用，排除路③全量重建）。
        Assert.assertEquals("composite-only 命中不应重建命令",
                baselineGeneration, widget.getPaintCacheGenerationForDiagnostics());
        // paintVersion 不变（COMPOSITE 不污染 paintVersion）。
        Assert.assertEquals("opacity 变化不应触发 PAINT 失效",
                paintVersionBefore, document.getPaintVersion());
        // compositeVersion +1（COMPOSITE 级变更被独立记账，排除路① 双未变）。
        Assert.assertEquals("opacity 变化应触发一次 COMPOSITE 失效",
                compositeVersionBefore + 1, document.getCompositeVersion());
    }

    /**
     * 验证仅 transform（COMPOSITE 级）变化时端到端命中 composite-only 就地回放（resolvePaintLayoutBox 路②），
     * 命令列表不重建。
     *
     * <p>断言组合与 {@link #shouldHitCompositeReplayWhenOnlyOpacityChanges} 一致：generation 不变（排除全量重建）、
     * paintVersion 不变（COMPOSITE 不污染 paintVersion）、compositeVersion +1（COMPOSITE 级变更独立记账）。</p>
     *
     * <p>transform 命令回放经 {@code UiRenderContext.pushTransform}/{@code popTransform}；测试用
     * {@code RecordingUiRenderContext} 已将二者覆写为 no-op（录制上下文只记录逻辑坐标、不应用真实 GL 矩阵），
     * 故 widget 级 transform 端到端渲染可在无 GL 上下文环境运行。transform 数值就地更新的引擎细节另由
     * {@code DocumentPaintEngineTest.shouldApplyCompositeReplayToTransformInPlace} 覆盖。</p>
     */
    @Test
    public void shouldHitCompositeReplayWhenOnlyTransformChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style().setWidth(UiStyleLength.px(100)).setHeight(UiStyleLength.px(40));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(10))
                .setBackgroundColor(0xFF223344)
                .setTransform(UiTransform.translate(8.0F, 4.0F));
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 48);

        // 首帧建立 paint 命令缓存（含 TRANSFORM_START/END 命令对），路②命中前置条件。
        widget.render(new RecordingUiRenderContext());
        int baselineGeneration = widget.getPaintCacheGenerationForDiagnostics();

        // 再渲染一帧确认静态缓存命中（无变化时 generation 不变，对应路①）。
        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(baselineGeneration, widget.getPaintCacheGenerationForDiagnostics());

        int paintVersionBefore = document.getPaintVersion();
        int compositeVersionBefore = document.getCompositeVersion();

        // 仅改 transform（平移量变更，始终非 identity 不跨结构阈值）：COMPOSITE 级变更。
        child.style().setTransform(UiTransform.translate(20.0F, 12.0F));
        widget.render(new RecordingUiRenderContext());

        // 命令未重建（generation 不变 = 命令列表实例复用，排除路③全量重建）。
        Assert.assertEquals("composite-only 命中不应重建命令",
                baselineGeneration, widget.getPaintCacheGenerationForDiagnostics());
        // paintVersion 不变（COMPOSITE 不污染 paintVersion）。
        Assert.assertEquals("transform 变化不应触发 PAINT 失效",
                paintVersionBefore, document.getPaintVersion());
        // compositeVersion +1（COMPOSITE 级变更被独立记账，排除路① 双未变）。
        Assert.assertEquals("transform 变化应触发一次 COMPOSITE 失效",
                compositeVersionBefore + 1, document.getCompositeVersion());
    }

    /**
     * 验证 PAINT 级变更（背景色）触发命令全量重建（paintCacheGeneration 递增），与 composite-only 回放对照。
     */
    @Test
    public void shouldRebuildPaintCommandsWhenPaintVersionChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style().setWidth(UiStyleLength.px(100)).setHeight(UiStyleLength.px(40));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(10))
                .setOpacity(0.5F)
                .setBackgroundColor(0xFF223344);
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 48);

        widget.render(new RecordingUiRenderContext());
        int baselineGeneration = widget.getPaintCacheGenerationForDiagnostics();

        child.style().setBackgroundColor(0xFF445566);
        widget.render(new RecordingUiRenderContext());
        Assert.assertTrue(widget.getPaintCacheGenerationForDiagnostics() > baselineGeneration);
    }

    /**
     * 验证 opacity 跨越 paint-context 阈值（0.5 → 1.0，paint context 消失）属结构性变化，回退全量重建。
     */
    @Test
    public void shouldRebuildPaintCommandsWhenOpacityCrossesPaintContextThreshold() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style().setWidth(UiStyleLength.px(100)).setHeight(UiStyleLength.px(40));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(10))
                .setOpacity(0.5F)
                .setBackgroundColor(0xFF223344);
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 48,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 48);

        widget.render(new RecordingUiRenderContext());
        int baselineGeneration = widget.getPaintCacheGenerationForDiagnostics();

        // opacity 回升到 >= 0.999：PAINT_CONTEXT 命令对应消失，结构变化，composite-only 回退、走全量重建。
        child.style().setOpacity(1.0F);
        widget.render(new RecordingUiRenderContext());
        Assert.assertTrue(widget.getPaintCacheGenerationForDiagnostics() > baselineGeneration);
    }

    private static ElementNode findListboxElement(ElementNode element) {
        if (element == null) {
            return null;
        }
        if ("listbox".equals(element.getAttribute("role"))) {
            return element;
        }
        for (DocumentNode child : element.getChildren()) {
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode found = findListboxElement((ElementNode) child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
