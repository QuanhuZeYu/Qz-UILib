package club.heiqi.uilib.ui.document;

import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.appendDynamicTextLine;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.appendHudPanelWithTopCards;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertContainsDrawCall;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertDrawCall;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertElementUid;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertTextCall;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.createAutoWidthTextBlock;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.createTextBlock;
import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.findLayoutBox;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.animation.DocumentAnimationFillMode;
import club.heiqi.uilib.ui.animation.DocumentAnimationImpact;
import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.animation.DocumentKeyframes;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.CountingTextMeasureService;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.DeterministicTextMeasureService;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.ManualAnimationClock;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.RecordingUiRenderContext;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.TextCall;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache;
import club.heiqi.uilib.ui.dom.DocumentElementActiveEvent;
import club.heiqi.uilib.ui.dom.DocumentElementActiveHandler;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementContextMenuEvent;
import club.heiqi.uilib.ui.dom.DocumentElementContextMenuHandler;
import club.heiqi.uilib.ui.dom.DocumentElementDoubleClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementDoubleClickHandler;
import club.heiqi.uilib.ui.dom.DocumentEventPhase;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationEvent;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationHandler;
import club.heiqi.uilib.ui.dom.DocumentElementAnimationEndEvent;
import club.heiqi.uilib.ui.dom.DocumentElementAnimationEndHandler;
import club.heiqi.uilib.ui.dom.DocumentElementHoverEvent;
import club.heiqi.uilib.ui.dom.DocumentElementHoverHandler;
import club.heiqi.uilib.ui.dom.DocumentElementMouseUpEvent;
import club.heiqi.uilib.ui.dom.DocumentElementMouseUpHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionEndEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionEndHandler;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.control.DocumentSelectControl;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.cascade.UiStyleSheet;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiOverflowWrap;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiTransform;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextContentMode;

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
     * 验证静态长文本二次渲染复用布局缓存，不因绘制裁剪触发重排。
     */
    @Test
    public void shouldReuseStaticLayoutForLongTextAcrossRenders() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 400; index++) {
            if (index > 0) {
                text.append('\n');
            }
            text.append("line-").append(index);
        }
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(54))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setWhiteSpace(UiWhiteSpace.PRE);
        root.appendText(text.toString());
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 54,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 54);

        RecordingUiRenderContext firstContext = new RecordingUiRenderContext();
        widget.render(firstContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot firstSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();
        RecordingUiRenderContext secondContext = new RecordingUiRenderContext();
        widget.render(secondContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot secondSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertTrue(firstContext.textCalls.size() <= 5);
        Assert.assertEquals(firstSnapshot.getStaticLayoutGeneration(), secondSnapshot.getStaticLayoutGeneration());
        Assert.assertEquals(firstSnapshot.getRuntimeLayoutGeneration(), secondSnapshot.getRuntimeLayoutGeneration());
        Assert.assertEquals(firstSnapshot.getPaintCacheGeneration(), secondSnapshot.getPaintCacheGeneration());
    }

    /**
     * 验证局部文本变更后的静态重排会复用未脏 block 子树。
     */
    @Test
    public void shouldReuseCleanBlockSubtreesDuringDirtyRelayout() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode changed = document.div();
        ElementNode last = document.div();
        first.style().setHeight(UiStyleLength.px(18));
        changed.style().setHeight(UiStyleLength.px(18));
        last.style().setHeight(UiStyleLength.px(18));
        first.appendText("first");
        TextNode changedText = document.text("middle");
        changed.appendChild(changedText);
        last.appendText("last");
        root.append(first).append(changed).append(last);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80, textMeasureService);
        widget.applyLayoutBounds(0, 0, 120, 80);

        widget.render(new RecordingUiRenderContext());
        int firstMeasureCount = textMeasureService.getMeasureCount();
        changedText.setText("center");
        RecordingUiRenderContext secondContext = new RecordingUiRenderContext();
        widget.render(secondContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot secondSnapshot =
                widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertTrue(secondSnapshot.getLastLayoutReusedSubtreeCount() >= 2);
        Assert.assertTrue(textMeasureService.getMeasureCount() - firstMeasureCount <= 2);
        assertTextCall(secondContext.textCalls.get(0), "first", 0, 0, 0xFFFFFFFF, false);
        assertTextCall(secondContext.textCalls.get(1), "center", 0, 18, 0xFFFFFFFF, false);
        assertTextCall(secondContext.textCalls.get(2), "last", 0, 36, 0xFFFFFFFF, false);
    }

    /**
     * 验证上方兄弟高度变化后，未脏 block 子树可以通过纵向平移复用。
     */
    @Test
    public void shouldTranslateCleanBlockSubtreeWhenFlowTopChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode clean = document.div();
        changed.style().setHeight(UiStyleLength.px(18));
        clean.appendText("clean");
        root.append(changed).append(clean);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80, textMeasureService);
        widget.applyLayoutBounds(0, 0, 120, 80);

        widget.render(new RecordingUiRenderContext());
        int measureCountAfterInitialRender = textMeasureService.getMeasureCount();
        changed.style().setHeight(UiStyleLength.px(36));
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertTrue(snapshot.getLastLayoutReusedSubtreeCount() >= 1);
        Assert.assertEquals(measureCountAfterInitialRender, textMeasureService.getMeasureCount());
        Assert.assertEquals(1, changedContext.textCalls.size());
        assertTextCall(changedContext.textCalls.get(0), "clean", 0, 36, 0xFFFFFFFF, false);
    }

    /**
     * 验证可平移复用仍保留子树内首子级 margin collapse 的相对几何。
     */
    @Test
    public void shouldTranslateCleanBlockSubtreeWithCollapsedMargins() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode clean = document.div();
        ElementNode collapsedChild = document.div();
        changed.style().setHeight(UiStyleLength.px(10));
        collapsedChild.style().setMarginTop(UiStyleLength.px(20));
        collapsedChild.appendText("collapsed");
        clean.append(collapsedChild);
        root.append(changed).append(clean);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100, textMeasureService);
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.render(new RecordingUiRenderContext());
        int measureCountAfterInitialRender = textMeasureService.getMeasureCount();
        changed.style().setHeight(UiStyleLength.px(25));
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertTrue(snapshot.getLastLayoutReusedSubtreeCount() >= 1);
        Assert.assertEquals(measureCountAfterInitialRender, textMeasureService.getMeasureCount());
        Assert.assertEquals(1, changedContext.textCalls.size());
        assertTextCall(changedContext.textCalls.get(0), "collapsed", 0, 45, 0xFFFFFFFF, false);
    }

    /**
     * 验证可平移复用不会破坏横向 auto margin 求解结果。
     */
    @Test
    public void shouldTranslateCleanBlockSubtreeWithAutoMargin() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode centered = document.div();
        root.style().setWidth(UiStyleLength.px(120));
        changed.style().setHeight(UiStyleLength.px(18));
        centered.style()
                .setWidth(UiStyleLength.px(40))
                .setMargin(UiStyleInsets.horizontal(UiStyleLength.auto()));
        centered.appendText("center");
        root.append(changed).append(centered);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100, textMeasureService);
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.render(new RecordingUiRenderContext());
        int measureCountAfterInitialRender = textMeasureService.getMeasureCount();
        changed.style().setHeight(UiStyleLength.px(36));
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertEquals(1, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertEquals(measureCountAfterInitialRender, textMeasureService.getMeasureCount());
        Assert.assertEquals(1, changedContext.textCalls.size());
        assertTextCall(changedContext.textCalls.get(0), "center", 40, 36, 0xFFFFFFFF, false);
    }

    /**
     * 验证可平移复用保留 relative 视觉偏移。
     */
    @Test
    public void shouldTranslateCleanRelativeBlockSubtree() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode relative = document.div();
        changed.style().setHeight(UiStyleLength.px(10));
        relative.style()
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(5));
        relative.appendText("relative");
        root.append(changed).append(relative);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100, textMeasureService);
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.render(new RecordingUiRenderContext());
        int measureCountAfterInitialRender = textMeasureService.getMeasureCount();
        changed.style().setHeight(UiStyleLength.px(20));
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertEquals(1, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertEquals(measureCountAfterInitialRender, textMeasureService.getMeasureCount());
        Assert.assertEquals(1, changedContext.textCalls.size());
        assertTextCall(changedContext.textCalls.get(0), "relative", 0, 25, 0xFFFFFFFF, false);
    }

    /**
     * 验证可平移复用保留 sticky 在滚动视口中的视觉偏移。
     */
    @Test
    public void shouldTranslateCleanStickyBlockSubtree() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode sticky = document.div();
        ElementNode tail = document.div();
        root.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(60))
                .setOverflowY(UiOverflow.AUTO);
        changed.style().setHeight(UiStyleLength.px(30));
        sticky.style()
                .setPosition(UiPosition.STICKY)
                .setTop(UiStyleLength.px(0))
                .setHeight(UiStyleLength.px(10))
                .setBackgroundColor(0xFF224466);
        tail.style().setHeight(UiStyleLength.px(120));
        root.append(changed).append(sticky).append(tail);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 100, 60,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 100, 60);

        widget.render(new RecordingUiRenderContext());
        Assert.assertTrue(widget.requestScrollTo(root, 0, 40));
        changed.style().setHeight(UiStyleLength.px(50));
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertTrue(snapshot.getLastLayoutReusedSubtreeCount() >= 1);
        assertContainsDrawCall(changedContext, 0, 10, 100, 20, 0xFF224466, 0, 0);
    }

    /**
     * 验证包含 absolute 后代的子树仍保守重排，避免外部 containing block 坐标误平移。
     */
    @Test
    public void shouldNotTranslateSubtreeContainingAbsolutePositionedBox() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode container = document.div();
        ElementNode absolute = document.div();
        changed.style().setHeight(UiStyleLength.px(10));
        container.style()
                .setPosition(UiPosition.RELATIVE)
                .setHeight(UiStyleLength.px(40));
        absolute.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(7))
                .setTop(UiStyleLength.px(4))
                .setWidth(UiStyleLength.px(12))
                .setHeight(UiStyleLength.px(8));
        container.append(absolute);
        root.append(changed).append(container);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.resolveLayoutBoxForTest();
        changed.style().setHeight(UiStyleLength.px(30));
        widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        DocumentLayoutBox containerBox = findLayoutBox(rootBox, container);
        DocumentLayoutBox absoluteBox = findLayoutBox(rootBox, absolute);

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertNotNull(containerBox);
        Assert.assertNotNull(absoluteBox);
        Assert.assertEquals(30, containerBox.getTop());
        Assert.assertEquals(7, absoluteBox.getLeft());
        Assert.assertEquals(34, absoluteBox.getTop());
    }

    /**
     * 验证包含 viewport fixed 后代的子树仍保守重排，fixed 坐标不随父级普通流位置平移。
     */
    @Test
    public void shouldNotTranslateSubtreeContainingViewportFixedBox() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode container = document.div();
        ElementNode fixed = document.div();
        changed.style().setHeight(UiStyleLength.px(10));
        container.style().setHeight(UiStyleLength.px(40));
        fixed.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(7))
                .setTop(UiStyleLength.px(4))
                .setWidth(UiStyleLength.px(12))
                .setHeight(UiStyleLength.px(8));
        container.append(fixed);
        root.append(changed).append(container);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.resolveLayoutBoxForTest();
        changed.style().setHeight(UiStyleLength.px(30));
        widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        DocumentLayoutBox containerBox = findLayoutBox(rootBox, container);
        DocumentLayoutBox fixedBox = findLayoutBox(rootBox, fixed);

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertNotNull(containerBox);
        Assert.assertNotNull(fixedBox);
        Assert.assertEquals(30, containerBox.getTop());
        Assert.assertEquals(7, fixedBox.getLeft());
        Assert.assertEquals(4, fixedBox.getTop());
    }

    /**
     * 验证 transform fixed containing block 子树仍保守重排，fixed 后代继续相对 transform 祖先定位。
     */
    @Test
    public void shouldNotTranslateTransformFixedContainingBlockSubtree() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode transformed = document.div();
        ElementNode fixed = document.div();
        changed.style().setHeight(UiStyleLength.px(10));
        transformed.style()
                .setHeight(UiStyleLength.px(40))
                .setTransform(UiTransform.translate(1.0F, 0.0F));
        fixed.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(7))
                .setTop(UiStyleLength.px(4))
                .setWidth(UiStyleLength.px(12))
                .setHeight(UiStyleLength.px(8));
        transformed.append(fixed);
        root.append(changed).append(transformed);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.resolveLayoutBoxForTest();
        changed.style().setHeight(UiStyleLength.px(30));
        widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        DocumentLayoutBox transformedBox = findLayoutBox(rootBox, transformed);
        DocumentLayoutBox fixedBox = findLayoutBox(rootBox, fixed);

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertNotNull(transformedBox);
        Assert.assertNotNull(fixedBox);
        Assert.assertEquals(30, transformedBox.getTop());
        Assert.assertEquals(7, fixedBox.getLeft());
        Assert.assertEquals(34, fixedBox.getTop());
    }

    /**
     * 验证可平移复用保留 row flex 主轴 grow 分配与交叉轴居中几何。
     */
    @Test
    public void shouldTranslateCleanRowFlexSubtreeWithMainAndCrossAxisDistribution() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode row = document.div();
        ElementNode grow = document.div();
        ElementNode largerGrow = document.div();
        root.style().setWidth(UiStyleLength.px(100));
        changed.style().setHeight(UiStyleLength.px(10));
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(40));
        grow.style()
                .setFlexGrow(1.0F)
                .setFlexBasis(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10));
        largerGrow.style()
                .setFlexGrow(2.0F)
                .setFlexBasis(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(20));
        row.append(grow).append(largerGrow).appendText("note");
        root.append(changed).append(row);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 100, 100, textMeasureService);
        widget.applyLayoutBounds(0, 0, 100, 100);

        widget.resolveLayoutBoxForTest();
        int measureCountAfterInitialLayout = textMeasureService.getMeasureCount();
        changed.style().setHeight(UiStyleLength.px(26));
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox rowBox = findLayoutBox(rootBox, row);
        DocumentLayoutBox growBox = findLayoutBox(rootBox, grow);
        DocumentLayoutBox largerGrowBox = findLayoutBox(rootBox, largerGrow);

        Assert.assertTrue(snapshot.getLastLayoutReusedSubtreeCount() >= 1);
        Assert.assertNotNull(rowBox);
        Assert.assertNotNull(growBox);
        Assert.assertNotNull(largerGrowBox);
        Assert.assertEquals(measureCountAfterInitialLayout, textMeasureService.getMeasureCount());
        Assert.assertEquals(26, rowBox.getTop());
        Assert.assertEquals(29, growBox.getWidth());
        Assert.assertEquals(10, growBox.getHeight());
        Assert.assertEquals(0, growBox.getLeft());
        Assert.assertEquals(41, growBox.getTop());
        Assert.assertEquals(39, largerGrowBox.getWidth());
        Assert.assertEquals(20, largerGrowBox.getHeight());
        Assert.assertEquals(29, largerGrowBox.getLeft());
        Assert.assertEquals(36, largerGrowBox.getTop());
    }

    /**
     * 验证可平移复用保留 column flex item 主轴尺寸分配。
     */
    @Test
    public void shouldTranslateCleanColumnFlexSubtreeWithFlexItemSizeDistribution() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode column = document.div();
        ElementNode head = document.div();
        ElementNode grow = document.div();
        ElementNode tail = document.div();
        root.style().setWidth(UiStyleLength.px(80));
        changed.style().setHeight(UiStyleLength.px(12));
        column.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.STRETCH)
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(60));
        head.style().setHeight(UiStyleLength.px(10));
        grow.style().setFlexGrow(1.0F);
        tail.style().setHeight(UiStyleLength.px(20));
        column.append(head).append(grow).append(tail);
        root.append(changed).append(column);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 100,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 100);

        widget.resolveLayoutBoxForTest();
        changed.style().setHeight(UiStyleLength.px(22));
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox columnBox = findLayoutBox(rootBox, column);
        DocumentLayoutBox headBox = findLayoutBox(rootBox, head);
        DocumentLayoutBox growBox = findLayoutBox(rootBox, grow);
        DocumentLayoutBox tailBox = findLayoutBox(rootBox, tail);

        Assert.assertTrue(snapshot.getLastLayoutReusedSubtreeCount() >= 1);
        Assert.assertNotNull(columnBox);
        Assert.assertNotNull(headBox);
        Assert.assertNotNull(growBox);
        Assert.assertNotNull(tailBox);
        Assert.assertEquals(22, columnBox.getTop());
        Assert.assertEquals(60, columnBox.getHeight());
        Assert.assertEquals(22, headBox.getTop());
        Assert.assertEquals(10, headBox.getHeight());
        Assert.assertEquals(32, growBox.getTop());
        Assert.assertEquals(30, growBox.getHeight());
        Assert.assertEquals(62, tailBox.getTop());
        Assert.assertEquals(20, tailBox.getHeight());
    }

    /**
     * 验证可平移复用不会破坏 flex item 主轴与交叉轴 auto margin 求解结果。
     */
    @Test
    public void shouldTranslateCleanFlexSubtreeWithMainAndCrossAxisAutoMargin() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode row = document.div();
        ElementNode centered = document.div();
        root.style().setWidth(UiStyleLength.px(100));
        changed.style().setHeight(UiStyleLength.px(8));
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(40));
        centered.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10))
                .setMargin(UiStyleInsets.of(UiStyleLength.auto(), UiStyleLength.auto(), UiStyleLength.auto(),
                        UiStyleLength.auto()));
        row.append(centered);
        root.append(changed).append(row);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 100, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 100, 80);

        widget.resolveLayoutBoxForTest();
        changed.style().setHeight(UiStyleLength.px(18));
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox centeredBox = findLayoutBox(rootBox, centered);

        Assert.assertEquals(1, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertNotNull(centeredBox);
        Assert.assertEquals(40, centeredBox.getLeft());
        Assert.assertEquals(33, centeredBox.getTop());
        Assert.assertEquals(20, centeredBox.getWidth());
    }

    /**
     * 验证包含 absolute/fixed 后代的 flex 子树仍保守重排。
     */
    @Test
    public void shouldNotTranslateFlexSubtreeContainingOutOfFlowPositionedDescendants() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode row = document.div();
        ElementNode absolute = document.div();
        ElementNode fixed = document.div();
        root.style().setWidth(UiStyleLength.px(120));
        changed.style().setHeight(UiStyleLength.px(10));
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setPosition(UiPosition.RELATIVE)
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(40));
        absolute.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(7))
                .setTop(UiStyleLength.px(4))
                .setWidth(UiStyleLength.px(12))
                .setHeight(UiStyleLength.px(8));
        fixed.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(9))
                .setTop(UiStyleLength.px(6))
                .setWidth(UiStyleLength.px(10))
                .setHeight(UiStyleLength.px(6));
        row.append(absolute).append(fixed);
        root.append(changed).append(row);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.resolveLayoutBoxForTest();
        changed.style().setHeight(UiStyleLength.px(30));
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox rowBox = findLayoutBox(rootBox, row);
        DocumentLayoutBox absoluteBox = findLayoutBox(rootBox, absolute);
        DocumentLayoutBox fixedBox = findLayoutBox(rootBox, fixed);

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertNotNull(rowBox);
        Assert.assertNotNull(absoluteBox);
        Assert.assertNotNull(fixedBox);
        Assert.assertEquals(30, rowBox.getTop());
        Assert.assertEquals(7, absoluteBox.getLeft());
        Assert.assertEquals(34, absoluteBox.getTop());
        Assert.assertEquals(9, fixedBox.getLeft());
        Assert.assertEquals(6, fixedBox.getTop());
    }

    /**
     * 验证可平移复用保留 table auto 列宽分配结果。
     */
    @Test
    public void shouldTranslateCleanTableSubtreeWithColumnWidths() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode table = document.table();
        ElementNode row = document.div();
        ElementNode shortCell = document.div();
        ElementNode longCell = document.div();
        root.style().setWidth(UiStyleLength.px(160));
        changed.style().setHeight(UiStyleLength.px(10));
        table.style()
                .setDisplay(UiDisplay.TABLE)
                .setWidth(UiStyleLength.px(160));
        row.style().setDisplay(UiDisplay.TABLE_ROW);
        shortCell.style().setDisplay(UiDisplay.TABLE_CELL);
        longCell.style().setDisplay(UiDisplay.TABLE_CELL);
        shortCell.appendText("A");
        longCell.appendText("ABCDEFGHIJ");
        row.append(shortCell).append(longCell);
        table.append(row);
        root.append(changed).append(table);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 100, textMeasureService);
        widget.applyLayoutBounds(0, 0, 160, 100);

        widget.resolveLayoutBoxForTest();
        int measureCountAfterInitialLayout = textMeasureService.getMeasureCount();
        changed.style().setHeight(UiStyleLength.px(26));
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox tableBox = findLayoutBox(rootBox, table);
        DocumentLayoutBox rowBox = findLayoutBox(rootBox, row);
        DocumentLayoutBox shortBox = findLayoutBox(rootBox, shortCell);
        DocumentLayoutBox longBox = findLayoutBox(rootBox, longCell);

        Assert.assertTrue(snapshot.getLastLayoutReusedSubtreeCount() >= 1);
        Assert.assertEquals(measureCountAfterInitialLayout, textMeasureService.getMeasureCount());
        Assert.assertNotNull(tableBox);
        Assert.assertNotNull(rowBox);
        Assert.assertNotNull(shortBox);
        Assert.assertNotNull(longBox);
        Assert.assertEquals(26, tableBox.getTop());
        Assert.assertEquals(26, rowBox.getTop());
        Assert.assertEquals(26, shortBox.getTop());
        Assert.assertEquals(shortBox.getLeft() + shortBox.getWidth(), longBox.getLeft());
        Assert.assertEquals(160, shortBox.getWidth() + longBox.getWidth());
        Assert.assertTrue(longBox.getWidth() > shortBox.getWidth());
    }

    /**
     * 验证可平移复用保留 table 行高测量与行落位结果。
     */
    @Test
    public void shouldTranslateCleanTableSubtreeWithRowHeights() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode table = document.table();
        ElementNode firstRow = document.div();
        ElementNode secondRow = document.div();
        ElementNode tallCell = document.div();
        ElementNode shortCell = document.div();
        root.style().setWidth(UiStyleLength.px(100));
        changed.style().setHeight(UiStyleLength.px(8));
        table.style()
                .setDisplay(UiDisplay.TABLE)
                .setWidth(UiStyleLength.px(100));
        firstRow.style().setDisplay(UiDisplay.TABLE_ROW);
        secondRow.style().setDisplay(UiDisplay.TABLE_ROW);
        tallCell.style()
                .setDisplay(UiDisplay.TABLE_CELL)
                .setHeight(UiStyleLength.px(24));
        shortCell.style()
                .setDisplay(UiDisplay.TABLE_CELL)
                .setHeight(UiStyleLength.px(12));
        firstRow.append(tallCell);
        secondRow.append(shortCell);
        table.append(firstRow).append(secondRow);
        root.append(changed).append(table);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 100, 100,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 100, 100);

        widget.resolveLayoutBoxForTest();
        changed.style().setHeight(UiStyleLength.px(18));
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox tableBox = findLayoutBox(rootBox, table);
        DocumentLayoutBox firstRowBox = findLayoutBox(rootBox, firstRow);
        DocumentLayoutBox secondRowBox = findLayoutBox(rootBox, secondRow);
        DocumentLayoutBox tallBox = findLayoutBox(rootBox, tallCell);
        DocumentLayoutBox shortBox = findLayoutBox(rootBox, shortCell);

        Assert.assertTrue(snapshot.getLastLayoutReusedSubtreeCount() >= 1);
        Assert.assertNotNull(tableBox);
        Assert.assertNotNull(firstRowBox);
        Assert.assertNotNull(secondRowBox);
        Assert.assertNotNull(tallBox);
        Assert.assertNotNull(shortBox);
        Assert.assertEquals(18, tableBox.getTop());
        Assert.assertEquals(36, tableBox.getHeight());
        Assert.assertEquals(18, firstRowBox.getTop());
        Assert.assertEquals(24, firstRowBox.getHeight());
        Assert.assertEquals(42, secondRowBox.getTop());
        Assert.assertEquals(12, secondRowBox.getHeight());
        Assert.assertEquals(24, tallBox.getHeight());
        Assert.assertEquals(12, shortBox.getHeight());
    }

    /**
     * 验证 table 单元格内容变化会让 table 子树重新布局，而不是复用旧列宽。
     */
    @Test
    public void shouldInvalidateTableSubtreeReuseWhenCellContentChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode table = document.table();
        ElementNode row = document.div();
        ElementNode stableCell = document.div();
        ElementNode changedCell = document.div();
        root.style().setWidth(UiStyleLength.px(120));
        table.style()
                .setDisplay(UiDisplay.TABLE)
                .setWidth(UiStyleLength.px(120));
        row.style().setDisplay(UiDisplay.TABLE_ROW);
        stableCell.style().setDisplay(UiDisplay.TABLE_CELL);
        changedCell.style().setDisplay(UiDisplay.TABLE_CELL);
        stableCell.appendText("A");
        TextNode changedText = changedCell.appendText("BB");
        row.append(stableCell).append(changedCell);
        table.append(row);
        root.append(table);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80, textMeasureService);
        widget.applyLayoutBounds(0, 0, 120, 80);

        widget.resolveLayoutBoxForTest();
        int measureCountAfterInitialLayout = textMeasureService.getMeasureCount();
        changedText.setText("BBBBBBBBBBBB");
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox stableBox = findLayoutBox(rootBox, stableCell);
        DocumentLayoutBox changedBox = findLayoutBox(rootBox, changedCell);

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertTrue(textMeasureService.getMeasureCount() > measureCountAfterInitialLayout);
        Assert.assertNotNull(stableBox);
        Assert.assertNotNull(changedBox);
        Assert.assertTrue(changedBox.getWidth() > stableBox.getWidth());
    }

    /**
     * 验证包含 absolute/fixed 后代的 table 子树仍保守重排。
     */
    @Test
    public void shouldNotTranslateTableSubtreeContainingOutOfFlowPositionedDescendants() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode changed = document.div();
        ElementNode table = document.table();
        ElementNode row = document.div();
        ElementNode cell = document.div();
        ElementNode absolute = document.div();
        ElementNode fixed = document.div();
        root.style().setWidth(UiStyleLength.px(120));
        changed.style().setHeight(UiStyleLength.px(10));
        table.style()
                .setDisplay(UiDisplay.TABLE)
                .setPosition(UiPosition.RELATIVE)
                .setWidth(UiStyleLength.px(120));
        row.style().setDisplay(UiDisplay.TABLE_ROW);
        cell.style()
                .setDisplay(UiDisplay.TABLE_CELL)
                .setHeight(UiStyleLength.px(40));
        absolute.style()
                .setPosition(UiPosition.ABSOLUTE)
                .setLeft(UiStyleLength.px(7))
                .setTop(UiStyleLength.px(4))
                .setWidth(UiStyleLength.px(12))
                .setHeight(UiStyleLength.px(8));
        fixed.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(9))
                .setTop(UiStyleLength.px(6))
                .setWidth(UiStyleLength.px(10))
                .setHeight(UiStyleLength.px(6));
        cell.append(absolute).append(fixed);
        row.append(cell);
        table.append(row);
        root.append(changed).append(table);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.resolveLayoutBoxForTest();
        changed.style().setHeight(UiStyleLength.px(30));
        DocumentLayoutBox rootBox = widget.resolveLayoutBoxForTest();
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();
        DocumentLayoutBox tableBox = findLayoutBox(rootBox, table);
        DocumentLayoutBox absoluteBox = findLayoutBox(rootBox, absolute);
        DocumentLayoutBox fixedBox = findLayoutBox(rootBox, fixed);

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertNotNull(tableBox);
        Assert.assertNotNull(absoluteBox);
        Assert.assertNotNull(fixedBox);
        Assert.assertEquals(30, tableBox.getTop());
        Assert.assertEquals(7, absoluteBox.getLeft());
        Assert.assertEquals(34, absoluteBox.getTop());
        Assert.assertEquals(9, fixedBox.getLeft());
        Assert.assertEquals(6, fixedBox.getTop());
    }

    /**
     * 验证同一父级内移动 DOM 子节点会让布局顺序重新计算。
     */
    @Test
    public void shouldRelayoutWhenChildMovesWithinSameParent() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode moved = document.div();
        ElementNode last = document.div();
        first.style().setHeight(UiStyleLength.px(18));
        moved.style().setHeight(UiStyleLength.px(18));
        last.style().setHeight(UiStyleLength.px(18));
        first.appendText("first");
        moved.appendText("moved");
        last.appendText("last");
        root.append(first).append(moved).append(last);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80,
                new CountingTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 80);

        widget.render(new RecordingUiRenderContext());
        root.insertBefore(last, first);
        RecordingUiRenderContext movedContext = new RecordingUiRenderContext();
        widget.render(movedContext);

        Assert.assertEquals(3, movedContext.textCalls.size());
        assertTextCall(movedContext.textCalls.get(0), "last", 0, 0, 0xFFFFFFFF, false);
        assertTextCall(movedContext.textCalls.get(1), "first", 0, 18, 0xFFFFFFFF, false);
        assertTextCall(movedContext.textCalls.get(2), "moved", 0, 36, 0xFFFFFFFF, false);
    }

    /**
     * 验证跨父级移动 DOM 子节点时旧父级也会标脏并收缩布局高度。
     */
    @Test
    public void shouldInvalidatePreviousParentWhenChildMovesAcrossParents() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode source = document.div();
        ElementNode target = document.div();
        ElementNode moved = document.div();
        ElementNode sourceTail = document.div();
        ElementNode targetHead = document.div();
        moved.style().setHeight(UiStyleLength.px(18));
        sourceTail.style().setHeight(UiStyleLength.px(18));
        targetHead.style().setHeight(UiStyleLength.px(18));
        moved.appendText("moved");
        sourceTail.appendText("source-tail");
        targetHead.appendText("target-head");
        source.append(moved).append(sourceTail);
        target.append(targetHead);
        root.append(source).append(target);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new CountingTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.render(new RecordingUiRenderContext());
        target.append(moved);
        RecordingUiRenderContext movedContext = new RecordingUiRenderContext();
        widget.render(movedContext);

        Assert.assertEquals(3, movedContext.textCalls.size());
        assertTextCall(movedContext.textCalls.get(0), "source-tail", 0, 0, 0xFFFFFFFF, false);
        assertTextCall(movedContext.textCalls.get(1), "target-head", 0, 18, 0xFFFFFFFF, false);
        assertTextCall(movedContext.textCalls.get(2), "moved", 0, 36, 0xFFFFFFFF, false);
    }

    /**
     * 验证继承性 layout 样式变化会让后代子树重新布局，而不是复用旧行高。
     */
    @Test
    public void shouldInvalidateDescendantsWhenInheritedLayoutStyleChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode container = document.div();
        ElementNode inherited = document.div();
        ElementNode after = document.div();
        inherited.appendText("inherited");
        after.appendText("after");
        container.append(inherited).append(after);
        root.append(container);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new CountingTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.render(new RecordingUiRenderContext());
        container.style().setLineHeight(UiStyleLength.px(30));
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);

        Assert.assertEquals(2, changedContext.textCalls.size());
        assertTextCall(changedContext.textCalls.get(0), "inherited", 0, 0, 0xFFFFFFFF, false);
        assertTextCall(changedContext.textCalls.get(1), "after", 0, 30, 0xFFFFFFFF, false);
    }

    /**
     * 验证文档级样式表变化会全局失效布局缓存并应用新规则。
     */
    @Test
    public void shouldInvalidateLayoutCacheWhenStyleSheetChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode target = document.div();
        ElementNode after = document.div();
        target.setClassName("sheet-target");
        target.appendText("sheet");
        after.appendText("after");
        root.append(target).append(after);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 100,
                new CountingTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 100);

        widget.render(new RecordingUiRenderContext());
        document.addStyleSheet(UiStyleSheet.create().addRule(".sheet-target",
                new UiStyleDeclaration().setLineHeight(UiStyleLength.px(30))));
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertEquals(2, changedContext.textCalls.size());
        assertTextCall(changedContext.textCalls.get(0), "sheet", 0, 0, 0xFFFFFFFF, false);
        assertTextCall(changedContext.textCalls.get(1), "after", 0, 30, 0xFFFFFFFF, false);
    }

    /**
     * 验证文本测量 epoch 推进后不会复用旧文本布局盒。
     */
    @Test
    public void shouldInvalidateLayoutReuseWhenTextMeasureEpochChanges() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();
        first.appendText("first");
        second.appendText("second");
        root.append(first).append(second);
        CountingTextMeasureService textMeasureService = new CountingTextMeasureService();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80, textMeasureService);
        widget.applyLayoutBounds(0, 0, 120, 80);

        widget.render(new RecordingUiRenderContext());
        int measureCountAfterInitialRender = textMeasureService.getMeasureCount();
        textMeasureService.advanceEpoch();
        RecordingUiRenderContext changedContext = new RecordingUiRenderContext();
        widget.render(changedContext);
        HtmlLikeDocumentWidget.PerformanceDiagnosticsSnapshot snapshot = widget.getPerformanceDiagnosticsSnapshot();

        Assert.assertEquals(0, snapshot.getLastLayoutReusedSubtreeCount());
        Assert.assertTrue(textMeasureService.getMeasureCount() > measureCountAfterInitialRender);
        Assert.assertEquals(2, changedContext.textCalls.size());
        assertTextCall(changedContext.textCalls.get(0), "first", 0, 0, 0xFFFFFFFF, false);
        assertTextCall(changedContext.textCalls.get(1), "second", 0, 18, 0xFFFFFFFF, false);
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
                .setFlexDirection(club.heiqi.uilib.ui.style.props.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.props.UiAlignItems.START)
                .setWidth(UiStyleLength.px(360))
                .setPadding(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(8));
        heroCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.props.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.props.UiAlignItems.START)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(4));
        heroCard.append(createAutoWidthTextBlock(document, "INTERACTIVE HUD"));
        summaryText = appendDynamicTextLine(document, heroCard, "");

        overviewCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.props.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.props.UiAlignItems.START)
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
                .setFlexDirection(club.heiqi.uilib.ui.style.props.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.props.UiAlignItems.START)
                .setWidth(UiStyleLength.px(360))
                .setHeight(UiStyleLength.px(368))
                .setPadding(UiStyleLength.px(12))
                .setRowGap(UiStyleLength.px(8));
        heroCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.props.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.props.UiAlignItems.START)
                .setWidth(UiStyleLength.percent(1.0F))
                .setPadding(UiStyleLength.px(8))
                .setBorderWidth(UiStyleLength.px(1))
                .setRowGap(UiStyleLength.px(4));
        heroCard.append(createAutoWidthTextBlock(document, "INTERACTIVE HUD"));
        heroSummaryText = appendDynamicTextLine(document, heroCard, "");

        controlCard.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(club.heiqi.uilib.ui.style.props.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.props.UiAlignItems.START)
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
                .setFlexDirection(club.heiqi.uilib.ui.style.props.UiFlexDirection.COLUMN)
                .setAlignItems(club.heiqi.uilib.ui.style.props.UiAlignItems.STRETCH)
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
     * 验证 click 在 AT_TARGET 阶段会先执行 target capture，再执行 target handler；
     * target capture 返回 true 只会阻止祖先冒泡，不会跳过当前 target handler。
     */
    @Test
    public void shouldInvokeTargetClickHandlerAfterTargetCaptureStopsPropagation() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        final List<String> eventLog = new ArrayList<String>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.setCaptureClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                eventLog.add("target-capture:" + event.getEventPhase());
                return true;
            }
        });
        child.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                eventLog.add("target:" + event.getEventPhase());
                return false;
            }
        });
        root.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                eventLog.add("root:" + event.getEventPhase());
                return false;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 2L));

        Assert.assertEquals(2, eventLog.size());
        Assert.assertEquals("target-capture:" + DocumentEventPhase.AT_TARGET, eventLog.get(0));
        Assert.assertEquals("target:" + DocumentEventPhase.AT_TARGET, eventLog.get(1));
    }

    /**
     * 验证 down/up 落在不同后代时，会将最近公共祖先作为 click target。
     */
    @Test
    public void shouldDispatchClickToNearestCommonAncestorWhenPressAndReleaseLandOnDifferentDescendants() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode container = document.div();
        ElementNode first = document.div();
        ElementNode second = document.div();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();
        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(40));
        container.style()
                .setDisplay(UiDisplay.FLEX)
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20));
        first.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        second.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        container.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        container.append(first).append(second);
        root.append(container);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 50, 10, 0, 0, 0, 0, 2L));

        Assert.assertEquals(1, clickEvents.size());
        assertElementUid(container, clickEvents.get(0).getTarget());
        assertElementUid(container, clickEvents.get(0).getCurrentTarget());
        Assert.assertEquals(50, clickEvents.get(0).getDocumentX());
        Assert.assertEquals(10, clickEvents.get(0).getDocumentY());
        Assert.assertEquals(0, clickEvents.get(0).getButton());
        Assert.assertEquals(2L, clickEvents.get(0).getTimeNanos());
    }

    /**
     * 验证 a[href] 在 click 后会触发文档级链接激活回调。
     */
    @Test
    public void shouldDispatchDocumentLinkActivationForAnchorClick() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode link = document.a();
        final List<DocumentLinkActivationEvent> activationEvents = new ArrayList<DocumentLinkActivationEvent>();

        document.setLinkActivationHandler(new DocumentLinkActivationHandler() {
            @Override
            public void onLinkActivated(DocumentLinkActivationEvent event) {
                activationEvents.add(event);
            }
        });
        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(40));
        link.setAttribute("href", "https://example.test/docs");
        link.appendText("Docs");
        root.append(link);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 4, 4, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 4, 4, 0, 0, 0, 0, 2L));

        Assert.assertEquals(1, activationEvents.size());
        Assert.assertEquals("https://example.test/docs", activationEvents.get(0).getHref());
        Assert.assertEquals(link.__getElementUid(), activationEvents.get(0).getElement().__getElementUid());
    }

    /**
     * 验证 img 加载失败时会绘制 alt 文本回退，而不是静默空白。
     */
    @Test
    public void shouldRenderAltFallbackWhenImageLoadFails() {
        DocumentRemoteImageCache.getInstance().clearForTesting();
        DocumentRemoteImageCache.getInstance().putFailedForTesting("https://example.test/missing.png");

        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode image = document.img();
        image.setAttribute("src", "https://example.test/missing.png");
        image.setAttribute("alt", "Missing icon");
        image.style().setWidth(UiStyleLength.px(72)).setHeight(UiStyleLength.px(24));
        root.append(image);

        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 60,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 60);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertTrue(renderContext.hostImageCalls.isEmpty());
        Assert.assertFalse(renderContext.textCalls.isEmpty());
        Assert.assertEquals("Missing icon", renderContext.textCalls.get(0).text);
    }

    /**
     * 验证双击有独立事件，且与单击共存。
     */
    @Test
    public void shouldDispatchDoubleClickAlongsideSingleClicks() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode child = document.div();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();
        final List<DocumentElementDoubleClickEvent> doubleClickEvents = new ArrayList<DocumentElementDoubleClickEvent>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        root.setDoubleClickHandler(new DocumentElementDoubleClickHandler() {
            @Override
            public boolean onDoubleClick(DocumentElementDoubleClickEvent event) {
                doubleClickEvents.add(event);
                return true;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 2L));
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 3L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 4L));

        Assert.assertEquals(2, clickEvents.size());
        Assert.assertEquals(1, doubleClickEvents.size());
        assertElementUid(child, doubleClickEvents.get(0).getTarget());
        assertElementUid(root, doubleClickEvents.get(0).getCurrentTarget());
        Assert.assertEquals(10, doubleClickEvents.get(0).getDocumentX());
        Assert.assertEquals(10, doubleClickEvents.get(0).getDocumentY());
    }

    /**
     * 验证右键菜单事件有独立入口。
     */
    @Test
    public void shouldDispatchContextMenuAsIndependentEvent() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode child = document.div();
        final List<DocumentElementContextMenuEvent> contextMenuEvents = new ArrayList<DocumentElementContextMenuEvent>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        root.setContextMenuHandler(new DocumentElementContextMenuHandler() {
            @Override
            public boolean onContextMenu(DocumentElementContextMenuEvent event) {
                contextMenuEvents.add(event);
                return true;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 1, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 1, 0, 0, 0, 2L));

        Assert.assertEquals(1, contextMenuEvents.size());
        assertElementUid(child, contextMenuEvents.get(0).getTarget());
        assertElementUid(root, contextMenuEvents.get(0).getCurrentTarget());
        Assert.assertEquals(1, contextMenuEvents.get(0).getButton());
    }

    /**
     * 验证右键菜单不会先触发普通 click 行为。
     */
    @Test
    public void shouldNotDispatchClickForContextMenuButton() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode child = document.div();
        final List<DocumentElementClickEvent> clickEvents = new ArrayList<DocumentElementClickEvent>();
        final List<DocumentElementContextMenuEvent> contextMenuEvents = new ArrayList<DocumentElementContextMenuEvent>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clickEvents.add(event);
                return true;
            }
        });
        child.setContextMenuHandler(new DocumentElementContextMenuHandler() {
            @Override
            public boolean onContextMenu(DocumentElementContextMenuEvent event) {
                contextMenuEvents.add(event);
                return true;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 1, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 1, 0, 0, 0, 2L));

        Assert.assertTrue(clickEvents.isEmpty());
        Assert.assertEquals(1, contextMenuEvents.size());
    }

    /**
     * 验证非主按钮不会触发 dblclick。
     */
    @Test
    public void shouldDispatchDoubleClickOnlyForPrimaryButton() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode child = document.div();
        final List<DocumentElementDoubleClickEvent> doubleClickEvents = new ArrayList<DocumentElementDoubleClickEvent>();
        final List<DocumentElementContextMenuEvent> contextMenuEvents = new ArrayList<DocumentElementContextMenuEvent>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.setDoubleClickHandler(new DocumentElementDoubleClickHandler() {
            @Override
            public boolean onDoubleClick(DocumentElementDoubleClickEvent event) {
                doubleClickEvents.add(event);
                return true;
            }
        });
        child.setContextMenuHandler(new DocumentElementContextMenuHandler() {
            @Override
            public boolean onContextMenu(DocumentElementContextMenuEvent event) {
                contextMenuEvents.add(event);
                return true;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 1, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 1, 0, 0, 0, 2L));
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 1, 0, 0, 0, 3L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 1, 0, 0, 0, 4L));

        Assert.assertTrue(doubleClickEvents.isEmpty());
        Assert.assertEquals(2, contextMenuEvents.size());
    }

    /**
     * 验证 dblclick 已接入 capture -> target -> bubble 三阶段链路。
     */
    @Test
    public void shouldDispatchDoubleClickThroughCaptureTargetAndBubble() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode child = document.div();
        final List<String> phases = new ArrayList<String>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        root.setCaptureDoubleClickHandler(new DocumentElementDoubleClickHandler() {
            @Override
            public boolean onDoubleClick(DocumentElementDoubleClickEvent event) {
                phases.add("capture:" + event.getCurrentTarget().getTagName() + ":" + event.getEventPhase());
                return false;
            }
        });
        child.setCaptureDoubleClickHandler(new DocumentElementDoubleClickHandler() {
            @Override
            public boolean onDoubleClick(DocumentElementDoubleClickEvent event) {
                phases.add("target-capture:" + event.getCurrentTarget().getTagName() + ":" + event.getEventPhase());
                return true;
            }
        });
        child.setDoubleClickHandler(new DocumentElementDoubleClickHandler() {
            @Override
            public boolean onDoubleClick(DocumentElementDoubleClickEvent event) {
                phases.add("target:" + event.getCurrentTarget().getTagName() + ":" + event.getEventPhase());
                return false;
            }
        });
        root.setDoubleClickHandler(new DocumentElementDoubleClickHandler() {
            @Override
            public boolean onDoubleClick(DocumentElementDoubleClickEvent event) {
                phases.add("bubble:" + event.getCurrentTarget().getTagName() + ":" + event.getEventPhase());
                return false;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 2L));
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 3L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 4L));

        Assert.assertEquals(3, phases.size());
        Assert.assertEquals("capture:document:CAPTURING", phases.get(0));
        Assert.assertEquals("target-capture:div:AT_TARGET", phases.get(1));
        Assert.assertEquals("target:div:AT_TARGET", phases.get(2));
    }

    /**
     * 验证 contextmenu 已接入 capture -> target -> bubble 三阶段链路。
     */
    @Test
    public void shouldDispatchContextMenuThroughCaptureTargetAndBubble() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode child = document.div();
        final List<String> phases = new ArrayList<String>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        root.setCaptureContextMenuHandler(new DocumentElementContextMenuHandler() {
            @Override
            public boolean onContextMenu(DocumentElementContextMenuEvent event) {
                phases.add("capture:" + event.getCurrentTarget().getTagName() + ":" + event.getEventPhase());
                return false;
            }
        });
        child.setCaptureContextMenuHandler(new DocumentElementContextMenuHandler() {
            @Override
            public boolean onContextMenu(DocumentElementContextMenuEvent event) {
                phases.add("target-capture:" + event.getCurrentTarget().getTagName() + ":" + event.getEventPhase());
                return true;
            }
        });
        child.setContextMenuHandler(new DocumentElementContextMenuHandler() {
            @Override
            public boolean onContextMenu(DocumentElementContextMenuEvent event) {
                phases.add("target:" + event.getCurrentTarget().getTagName() + ":" + event.getEventPhase());
                return false;
            }
        });
        root.setContextMenuHandler(new DocumentElementContextMenuHandler() {
            @Override
            public boolean onContextMenu(DocumentElementContextMenuEvent event) {
                phases.add("bubble:" + event.getCurrentTarget().getTagName() + ":" + event.getEventPhase());
                return false;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 1, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 1, 0, 0, 0, 2L));

        Assert.assertEquals(3, phases.size());
        Assert.assertEquals("capture:document:CAPTURING", phases.get(0));
        Assert.assertEquals("target-capture:div:AT_TARGET", phases.get(1));
        Assert.assertEquals("target:div:AT_TARGET", phases.get(2));
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
     * 验证字体粗细和斜体会进入文本绘制调用。
     */
    @Test
    public void shouldRenderTextWithFontWeightAndFontStyle() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(160))
                .setFontWeight(UiFontWeight.BOLD)
                .setFontStyle(UiFontStyle.ITALIC);
        root.appendText("bold italic");
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 160, 40);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertFalse(renderContext.textCalls.isEmpty());
        for (TextCall textCall : renderContext.textCalls) {
            Assert.assertEquals(UiFontWeight.BOLD, textCall.fontWeight);
            Assert.assertEquals(UiFontStyle.ITALIC, textCall.fontStyle);
        }
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
     * 验证 HTML-like 组件会分发鼠标按下与松开的 active 状态。
     */
    @Test
    public void shouldDispatchActiveStateAroundMousePress() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode input = document.div();
        final List<Boolean> activeEvents = new ArrayList<Boolean>();
        final List<Integer> activeButtons = new ArrayList<Integer>();
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
                activeButtons.add(Integer.valueOf(event.getButton()));
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
        Assert.assertEquals(Integer.valueOf(0), activeButtons.get(1));
    }

    /**
     * 验证 active 状态通知不会被目标 handler 返回值截断，祖先仍能同步 :active 状态。
     */
    @Test
    public void shouldNotifyActiveStateAncestorsEvenWhenTargetConsumes() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode parent = document.div();
        ElementNode child = document.div();
        final List<String> activeEvents = new ArrayList<String>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        parent.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.setActiveHandler(new DocumentElementActiveHandler() {
            @Override
            public boolean onActiveChanged(DocumentElementActiveEvent event) {
                activeEvents.add("child:" + event.isActive());
                return true;
            }
        });
        parent.setActiveHandler(new DocumentElementActiveHandler() {
            @Override
            public boolean onActiveChanged(DocumentElementActiveEvent event) {
                activeEvents.add("parent:" + event.isActive());
                return false;
            }
        });
        parent.append(child);
        root.append(parent);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 2L));

        Assert.assertEquals("[child:true, parent:true, child:false, parent:false]", activeEvents.toString());
    }

    /**
     * 验证 hover enter/leave 状态通知不会被目标 handler 返回值截断。
     */
    @Test
    public void shouldNotifyHoverAncestorsEvenWhenTargetConsumes() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode parent = document.div();
        ElementNode child = document.div();
        final List<String> hoverEvents = new ArrayList<String>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        parent.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.setHoverHandler(new DocumentElementHoverHandler() {
            @Override
            public boolean onHoverChanged(DocumentElementHoverEvent event) {
                hoverEvents.add("child:" + event.isHovered());
                return true;
            }
        });
        parent.setHoverHandler(new DocumentElementHoverHandler() {
            @Override
            public boolean onHoverChanged(DocumentElementHoverEvent event) {
                hoverEvents.add("parent:" + event.isHovered());
                return false;
            }
        });
        parent.append(child);
        root.append(parent);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.onMouseMove(new UiMouseEvent(UiMouseEvent.Action.MOVE, 10, 10, -1, 0, 0, 0, 1L));
        widget.onMouseLeave();

        Assert.assertEquals("[child:true, parent:true, child:false, parent:false]", hoverEvents.toString());
    }

    /**
     * 验证 mouseup 事件会按释放位置命中目标，而不是沿用按下目标。
     */
    @Test
    public void shouldDispatchMouseUpToReleasedElement() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode row = document.div();
        ElementNode first = document.div();
        ElementNode second = document.div();
        final List<DocumentElementMouseUpEvent> mouseUpEvents = new ArrayList<DocumentElementMouseUpEvent>();
        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(40));
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20));
        first.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        second.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        second.setMouseUpHandler(new DocumentElementMouseUpHandler() {
            @Override
            public boolean onMouseUp(DocumentElementMouseUpEvent event) {
                mouseUpEvents.add(event);
                return true;
            }
        });
        row.append(first).append(second);
        root.append(row);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 40);

        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 50, 10, 0, 0, 0, 0, 2L));

        Assert.assertEquals(1, mouseUpEvents.size());
        assertElementUid(second, mouseUpEvents.get(0).getTarget());
        assertElementUid(second, mouseUpEvents.get(0).getCurrentTarget());
        Assert.assertEquals(50, mouseUpEvents.get(0).getDocumentX());
        Assert.assertEquals(10, mouseUpEvents.get(0).getDocumentY());
        Assert.assertEquals(0, mouseUpEvents.get(0).getButton());
        Assert.assertEquals(2L, mouseUpEvents.get(0).getTimeNanos());
    }

    /**
     * 验证鼠标离开组件时会释放按下产生的 active 状态。
     */
    @Test
    public void shouldReleaseActiveStateWhenMouseLeavesWidget() {
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
        widget.onMouseLeave();

        Assert.assertEquals(Boolean.TRUE, activeEvents.get(0));
        Assert.assertEquals(Boolean.FALSE, activeEvents.get(1));
    }

}
