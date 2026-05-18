package club.heiqi.uilib.ui.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.animation.DocumentAnimationClock;
import club.heiqi.uilib.ui.animation.DocumentAnimationImpact;
import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentNodeType;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.paint.DocumentPaintCommand;
import club.heiqi.uilib.ui.paint.DocumentPaintCommandType;
import club.heiqi.uilib.ui.paint.DocumentPaintEngine;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.style.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.UiBorderStyle;
import club.heiqi.uilib.ui.style.UiPosition;
import club.heiqi.uilib.ui.style.UiStyleInsets;
import club.heiqi.uilib.ui.style.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.theme.UiSurfaceStyle;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `HtmlLikeSmokeDocumentPageController` 的页面集成契约测试。
 */
public class HtmlLikeSmokeDocumentPageControllerTest {

    /**
     * 验证 smoke 子页会挂接 HTML-like 文档适配组件。
     */
    @Test
    public void shouldBuildHtmlLikeSmokeDocumentTree() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();

        List<Widget> blocks = fixture.pageSurface.getBlocks();
        Assert.assertEquals(1, blocks.size());
        Assert.assertTrue(blocks.get(0) instanceof HtmlLikeDocumentWidget);
        Assert.assertSame(blocks.get(0), fixture.controller.getHtmlLikeDocumentWidget());
        Assert.assertTrue(fixture.controller.getHtmlLikeDocumentWidget().isViewportRootScrollingEnabled());

        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        List<String> texts = collectDocumentTexts(widget);
        Assert.assertTrue(containsText(texts, "HTML-like Smoke Lab"));
        Assert.assertTrue(containsText(texts, "UiDocument -> style -> layout -> paint command -> UiRenderContext"));
        Assert.assertTrue(containsText(texts, "FIXED viewport"));
        Assert.assertTrue(containsText(texts, "Controls probe: click, input, Tab, button, toggle"));
        Assert.assertTrue(containsText(texts, "Animation diagnostics: pill=paint bg+radius"));
        Assert.assertTrue(containsText(texts, "opacity uses separate group probe"));
        Assert.assertTrue(containsText(texts, "Keyframe diagnostics: first pill runs smokePulse bg+radius 0/50/100 stops"));
        Assert.assertTrue(containsText(texts, "Opacity FBO probe: click card, auto card"));
        Assert.assertTrue(containsText(texts, "Opacity FBO card: click fade"));
        Assert.assertTrue(containsText(texts, "Opacity FBO auto: initial fade"));
        Assert.assertTrue(containsText(texts, "Opacity FBO combo: 3-stop + click"));
        Assert.assertTrue(containsText(texts, "Layout animation probe: click cards"));
        Assert.assertTrue(containsText(texts, "Layout card: small"));
        Assert.assertTrue(containsText(texts, "Sibling shifts while layout transition runs"));
        Assert.assertTrue(containsText(texts, "Margin card: tight"));
        Assert.assertTrue(containsText(texts, "Margin sibling shifts from margin"));
        Assert.assertTrue(containsText(texts, "Padding card: tight"));
        Assert.assertTrue(containsText(texts, "Padding sibling shifts from padding"));
        Assert.assertTrue(containsText(texts, "Keyframe card: idle"));
        Assert.assertTrue(containsText(texts, "Keyframe sibling holds forwards fill"));
        Assert.assertTrue(containsText(texts, "Layout animation coverage: WIDTH/HEIGHT/MARGIN_LEFT/MARGIN_RIGHT/PADDING_LEFT/PADDING_RIGHT"));
        Assert.assertTrue(containsText(texts, "Animation runtime: active="));
        Assert.assertTrue(containsText(texts, "Runtime by impact: paint t="));
        Assert.assertTrue(containsText(texts, "Same-layer sampling grid"));
        Assert.assertTrue(containsText(texts, "ABS containing probe"));
        Assert.assertTrue(containsText(texts, "static wrapper is not anchor"));
        Assert.assertTrue(containsText(texts, "ABS OK"));
        Assert.assertTrue(containsText(texts, "ABS badge"));
        Assert.assertTrue(containsText(texts, "pink stripe behind glass"));
        Assert.assertTrue(containsText(texts, "amber UI behind this card"));
        Assert.assertTrue(containsText(texts, "Backdrop glass transition: click blur 4/22px"));
        Assert.assertTrue(containsText(texts, "Absolute stretch + inline span probe"));
        Assert.assertTrue(containsText(texts, "ABS stretch fill: left+right / top+bottom"));
        Assert.assertTrue(containsText(texts, "amber span hit: 0"));
        Assert.assertTrue(containsText(texts, "Vertical-align probe:"));
        Assert.assertTrue(containsText(texts, "align:"));
        Assert.assertTrue(containsText(texts, "Group opacity probe: overlap should stay flat blue"));
        Assert.assertTrue(containsText(texts, "Stacking context probe: blue cover must stay above red z-99 child"));
        Assert.assertTrue(containsText(texts, "red child z=99"));
        Assert.assertTrue(containsText(texts, "blue sibling z=1 should win"));

        ElementNode glassCard = findElementContainingDirectText(widget, "Backdrop glass transition: click blur 4/22px");
        Assert.assertNotNull(glassCard);
        Assert.assertEquals(UiPosition.ABSOLUTE, glassCard.style().getPosition());
        Assert.assertEquals(UiPosition.RELATIVE, ((ElementNode) glassCard.getParent()).style().getPosition());
    }

    /**
     * 验证 smoke 子页中的 HTML-like 组件能产生真实 surface 绘制调用。
     */
    @Test
    public void shouldRenderSmokeDocumentToUiRenderContext() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(31, 47, 760, 320);
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();

        widget.render(renderContext);

        Assert.assertFalse(renderContext.drawCalls.isEmpty());
        DrawCall firstCall = renderContext.drawCalls.get(0);
        Assert.assertEquals(31, firstCall.left);
        Assert.assertEquals(47, firstCall.top);
        Assert.assertEquals(791, firstCall.right);
        Assert.assertTrue(firstCall.bottom > firstCall.top);
        Assert.assertEquals(0xF00B1020, firstCall.surfaceStyle.fillColor);
        Assert.assertFalse(renderContext.clipCalls.isEmpty());
        Assert.assertTrue(renderContext.popClipCount > 0);
        Assert.assertEquals(1, renderContext.backdropCalls.size());
        Assert.assertEquals(14, renderContext.backdropCalls.get(0).blurRadius);
        Assert.assertEquals(1.4F, renderContext.backdropCalls.get(0).saturation, 0.001F);
        Assert.assertEquals(12, renderContext.backdropCalls.get(0).cornerRadius);
        Assert.assertTrue(containsFillColor(renderContext.drawCalls, 0xFFFFD166));
        Assert.assertTrue(containsFillColor(renderContext.drawCalls, 0xFF0EA5E9));
        Assert.assertTrue(containsFillColor(renderContext.drawCalls, 0x334F46E5));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "ABS OK"));
        assertAbsoluteProbeIsVisibleOutsideStaticWrapper(widget, fixture.textMeasureService);
        assertGroupOpacityProbeKeepsChildColorsOpaque(widget, fixture.textMeasureService);
        assertStackingContextProbeKeepsHighZChildIsolated(widget, fixture.textMeasureService);
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "FIXED viewport"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Animation diagnostics"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Keyframe diagnostics"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Layout animation probe"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Layout card: small"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Margin card: tight"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Padding card: tight"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Keyframe card: idle"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Keyframe sibling holds forwards fill"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Layout animation coverage: WIDTH/HEIGHT/MARGIN_LEFT/MARGIN_RIGHT/PADDING_LEFT/PADDING_RIGHT"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Animation runtime: active="));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "transition="));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "keyframe="));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "fill="));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Runtime by impact: paint t="));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "layout t=0 k=0 f=0 active=false"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "ABS stretch fill"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "amber span hit: 0"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Vertical-align probe"));
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "bot"));
        assertSmokeInlineProbeSplitsAmberSpan(widget, fixture.textMeasureService);
        Assert.assertTrue(containsTextCall(renderContext.textCalls, "TEXT paint command"));
    }

    /**
     * 验证 Smoke 页需要可见边框的旧设计元素显式声明 solid 样式。
     */
    @Test
    public void shouldDeclareSolidBorderStyleForVisibleSmokeBorders() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();

        assertSolidBorder(widget.getDocument().getRootElement());
        assertSolidBorder(findElementContainingDirectText(widget, "HTML-like Smoke Lab"));
        assertSolidBorder(findElementContainingDirectText(widget, "FIXED viewport"));
        assertSolidBorder(findElementContainingDirectText(widget, "ABS containing probe"));
        assertSolidBorder(findElementContainingDirectText(widget, "ABS OK"));
        assertSolidBorder((ElementNode) findElementContainingDirectText(widget, "Floating scroll probe").getParent());
        assertSolidBorder(findElementContainingDirectText(widget, "Opacity FBO card: click fade"));
        assertSolidBorder(findElementContainingDirectText(widget, "Layout card: small"));
        assertSolidBorder(findElementContainingDirectText(widget, "amber span hit: 0"));
        assertSolidBorder(findElementContainingDirectText(widget, "blue sibling z=1 should win"));
    }

    /**
     * 验证 smoke 子页中的 HTML-like 点击目标会产生可见反馈。
     */
    @Test
    public void shouldUpdateSmokeClickTargetWhenClicked() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(31, 47, 760, 320);
        RecordingUiRenderContext initialRenderContext = new RecordingUiRenderContext();

        widget.render(initialRenderContext);
        Assert.assertTrue(containsTextCall(initialRenderContext.textCalls, "Click target: 0"));

        ElementNode clickTarget = findElementContainingDirectText(widget, "Click target: 0");
        clickElementCenter(widget, fixture.textMeasureService, clickTarget, 1L, 2L);
        RecordingUiRenderContext clickedRenderContext = new RecordingUiRenderContext();
        widget.render(clickedRenderContext);

        Assert.assertTrue(containsTextCall(clickedRenderContext.textCalls, "Click target: 1"));
        Assert.assertFalse(clickTarget.style().getTransitionProperties().contains(DocumentAnimationProperty.OPACITY));
        Assert.assertEquals(Float.valueOf(1.0F), clickTarget.style().getOpacity());
        Assert.assertTrue(widget.getActiveAnimationCount() >= 2);
    }

    /**
     * 验证 Smoke 页首个交互 pill 会通过作者侧 animation-name 运行 keyframes。
     */
    @Test
    public void shouldRunSmokeKeyframeAnimationOnFirstPill() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(31, 47, 760, 320);
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();

        widget.render(renderContext);

        ElementNode clickTarget = findElementContainingDirectText(widget, "Click target: 0");
        Assert.assertEquals("smokePulse", clickTarget.style().getAnimationName());
        Assert.assertTrue(widget.getDocument().getKeyframesRegistry().containsKey("smokePulse"));
        Assert.assertEquals(3, widget.getDocument().getKeyframes("smokePulse")
                .getColorTracks().get(DocumentAnimationProperty.BACKGROUND_COLOR).getStops().size());
        Assert.assertEquals(3, widget.getDocument().getKeyframes("smokePulse")
                .getFloatTracks().get(DocumentAnimationProperty.BORDER_RADIUS).getStops().size());
        Assert.assertFalse(widget.getDocument().getKeyframes("smokePulse").getFloatTracks()
                .containsKey(DocumentAnimationProperty.OPACITY));
        Assert.assertTrue(widget.getActiveAnimationCount() >= 2);
    }

    /**
     * 验证 Smoke 页包含独立 opacity FBO 探针，并可通过点击进入 opacity transition。
     */
    @Test
    public void shouldExposeOpacityFboProbeForManualSmokeValidation() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(31, 47, 760, 320);
        widget.render(new RecordingUiRenderContext());

        ElementNode opacityCard = findElementContainingDirectText(widget, "Opacity FBO card: click fade");
        ElementNode autoOpacityCard = findElementContainingDirectText(widget, "Opacity FBO auto: initial fade");
        ElementNode comboOpacityCard = findElementContainingDirectText(widget, "Opacity FBO combo: 3-stop + click");
        Assert.assertNotNull(opacityCard);
        Assert.assertNotNull(autoOpacityCard);
        Assert.assertNotNull(comboOpacityCard);
        Assert.assertTrue(widget.getDocument().getKeyframesRegistry().containsKey("opacityFboAuto"));
        Assert.assertTrue(widget.getDocument().getKeyframes("opacityFboAuto").getFloatTracks()
                .containsKey(DocumentAnimationProperty.OPACITY));
        Assert.assertEquals(3, widget.getDocument().getKeyframes("opacityFboAuto").getFloatTracks()
                .get(DocumentAnimationProperty.OPACITY).getStops().size());
        Assert.assertEquals("opacityFboAuto", autoOpacityCard.style().getAnimationName());
        Assert.assertNull(autoOpacityCard.style().getTransitionProperties());
        Assert.assertEquals("opacityFboAuto", comboOpacityCard.style().getAnimationName());
        Assert.assertTrue(comboOpacityCard.style().getTransitionProperties().contains(DocumentAnimationProperty.OPACITY));
        Assert.assertTrue(opacityCard.style().getTransitionProperties().contains(DocumentAnimationProperty.OPACITY));
        Assert.assertEquals(Float.valueOf(1.0F), opacityCard.style().getOpacity());
        Assert.assertEquals(Float.valueOf(1.0F), comboOpacityCard.style().getOpacity());

        Assert.assertTrue(opacityCard.getClickHandler().onClick(new DocumentElementClickEvent(opacityCard,
                opacityCard, 0, 0, 0, 2L)));
        widget.render(new RecordingUiRenderContext());

        Assert.assertEquals(Float.valueOf(0.45F), opacityCard.style().getOpacity());
        Assert.assertTrue(comboOpacityCard.getClickHandler().onClick(new DocumentElementClickEvent(comboOpacityCard,
                comboOpacityCard, 0, 0, 0, 3L)));
        widget.render(new RecordingUiRenderContext());

        Assert.assertEquals(Float.valueOf(0.45F), comboOpacityCard.style().getOpacity());
        Assert.assertTrue(widget.getActiveAnimationCount() >= 1);
    }

    /**
     * 验证 smoke 页玻璃卡片会触发 backdrop blur 长度类 transition。
     */
    @Test
    public void shouldAnimateSmokeBackdropBlurWhenGlassCardClicked() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(31, 47, 760, 320);
        widget.render(new RecordingUiRenderContext());

        ElementNode glassCard = findElementContainingDirectText(widget, "Backdrop glass transition: click blur 4/22px");
        Assert.assertNotNull(glassCard);
        Assert.assertTrue(glassCard.getClickHandler().onClick(new DocumentElementClickEvent(glassCard, glassCard,
                0, 0, 0, 2L)));
        RecordingUiRenderContext clickedRenderContext = new RecordingUiRenderContext();
        widget.render(clickedRenderContext);

        Assert.assertFalse(clickedRenderContext.backdropCalls.isEmpty());
        Assert.assertEquals(UiStyleLength.px(22), glassCard.style().getBackdropBlurRadius());
    }

    /**
     * 验证 Smoke 页包含可见 layout-affecting 动画探针，并可通过点击修改 width/height 与 margin。
     */
    @Test
    public void shouldExposeLayoutAnimationProbeForManualSmokeValidation() {
        TestFixture fixture = new TestFixture();
        ManualAnimationClock animationClock = new ManualAnimationClock();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(31, 47, 760, 1280);
        RecordingUiRenderContext initialRenderContext = new RecordingUiRenderContext();
        widget.render(initialRenderContext);

        ElementNode layoutCard = findElementContainingDirectText(widget, "Layout card: small");
        ElementNode sibling = findElementContainingDirectText(widget, "Sibling shifts while layout transition runs");
        ElementNode marginCard = findElementContainingDirectText(widget, "Margin card: tight");
        ElementNode marginSibling = findElementContainingDirectText(widget, "Margin sibling shifts from margin");
        ElementNode paddingCard = findElementContainingDirectText(widget, "Padding card: tight");
        ElementNode paddingSibling = findElementContainingDirectText(widget, "Padding sibling shifts from padding");
        ElementNode keyframeCard = findElementContainingDirectText(widget, "Keyframe card: idle");
        ElementNode keyframeSibling = findElementContainingDirectText(widget,
                "Keyframe sibling holds forwards fill");
        Assert.assertNotNull(layoutCard);
        Assert.assertNotNull(sibling);
        Assert.assertNotNull(marginCard);
        Assert.assertNotNull(marginSibling);
        Assert.assertNotNull(paddingCard);
        Assert.assertNotNull(paddingSibling);
        Assert.assertNotNull(keyframeCard);
        Assert.assertNotNull(keyframeSibling);
        Assert.assertTrue(layoutCard.style().getTransitionProperties().contains(DocumentAnimationProperty.WIDTH));
        Assert.assertTrue(layoutCard.style().getTransitionProperties().contains(DocumentAnimationProperty.HEIGHT));
        Assert.assertTrue(marginCard.style().getTransitionProperties().contains(DocumentAnimationProperty.MARGIN_LEFT));
        Assert.assertTrue(marginCard.style().getTransitionProperties().contains(DocumentAnimationProperty.MARGIN_RIGHT));
        Assert.assertTrue(paddingCard.style().getTransitionProperties().contains(DocumentAnimationProperty.PADDING_LEFT));
        Assert.assertTrue(paddingCard.style().getTransitionProperties().contains(DocumentAnimationProperty.PADDING_RIGHT));
        Assert.assertEquals(UiStyleLength.px(92), layoutCard.style().getWidth());
        Assert.assertEquals(UiStyleLength.px(34), layoutCard.style().getHeight());
        Assert.assertEquals(UiStyleLength.px(4), marginCard.style().getMargin().getLeft());
        Assert.assertEquals(UiStyleLength.px(4), marginCard.style().getMargin().getRight());
        Assert.assertEquals(UiStyleLength.px(4), paddingCard.style().getPadding().getLeft());
        Assert.assertEquals(UiStyleLength.px(4), paddingCard.style().getPadding().getRight());
        Assert.assertTrue(widget.getDocument().getKeyframesRegistry().containsKey("layoutFillProbe"));
        Assert.assertTrue(widget.getDocument().getKeyframes("layoutFillProbe").getFloatTracks()
                .containsKey(DocumentAnimationProperty.WIDTH));
        Assert.assertNull(keyframeCard.style().getAnimationName());
        Assert.assertFalse(widget.hasLayoutRuntimeValueForDiagnostics());
        Assert.assertTrue(containsTextCall(initialRenderContext.textCalls,
                "Layout animation coverage: WIDTH/HEIGHT/MARGIN_LEFT/MARGIN_RIGHT/PADDING_LEFT/PADDING_RIGHT"));
        Assert.assertTrue(containsTextCall(initialRenderContext.textCalls, "layout t=0 k=0 f=0 active=false"));
        Assert.assertTrue(containsTextCall(initialRenderContext.textCalls, "Cache runtime: paintGen="));
        Assert.assertTrue(containsTextCall(initialRenderContext.textCalls, "staticLayout="));
        Assert.assertTrue(containsTextCall(initialRenderContext.textCalls, "runtimeLayout="));
        Assert.assertTrue(containsTextCall(initialRenderContext.textCalls, "textEpoch="));
        Assert.assertTrue(containsTextCall(initialRenderContext.textCalls,
                "Cache note: click-frame static +1 is OK; running paint/effect must not grow runtimeLayout."));

        int siblingLeftBefore = findElementBackgroundCommand(widget, fixture.textMeasureService, sibling).getLeft();
        clickElementCenter(widget, fixture.textMeasureService, layoutCard, 1L, 2L);
        RecordingUiRenderContext layoutClickedRenderContext = new RecordingUiRenderContext();
        widget.render(layoutClickedRenderContext);
        int siblingLeftAfter = findElementBackgroundCommand(widget, fixture.textMeasureService, sibling).getLeft();
        int marginSiblingLeftBefore = findElementBackgroundCommand(widget, fixture.textMeasureService,
                marginSibling).getLeft();
        clickElementCenter(widget, fixture.textMeasureService, marginCard, 3L, 4L);
        widget.render(new RecordingUiRenderContext());
        int marginSiblingLeftAfter = findElementBackgroundCommand(widget, fixture.textMeasureService,
                marginSibling).getLeft();
        int paddingSiblingLeftBefore = findElementBackgroundCommand(widget, fixture.textMeasureService,
                paddingSibling).getLeft();
        clickElementCenter(widget, fixture.textMeasureService, paddingCard, 5L, 6L);
        widget.render(new RecordingUiRenderContext());
        int paddingSiblingLeftAfter = findElementBackgroundCommand(widget, fixture.textMeasureService,
                paddingSibling).getLeft();

        Assert.assertEquals(UiStyleLength.px(190), layoutCard.style().getWidth());
        Assert.assertEquals(UiStyleLength.px(58), layoutCard.style().getHeight());
        Assert.assertEquals(UiStyleLength.px(34), marginCard.style().getMargin().getLeft());
        Assert.assertEquals(UiStyleLength.px(18), marginCard.style().getMargin().getRight());
        Assert.assertEquals(UiStyleInsets.of(UiStyleLength.px(7), UiStyleLength.px(24), UiStyleLength.px(7),
                UiStyleLength.px(28)), paddingCard.style().getPadding());
        Assert.assertTrue(siblingLeftAfter > siblingLeftBefore);
        Assert.assertTrue(marginSiblingLeftAfter > marginSiblingLeftBefore);
        Assert.assertTrue(paddingSiblingLeftAfter > paddingSiblingLeftBefore);
        Assert.assertTrue(widget.getActiveAnimationCount() >= 6);
        Assert.assertTrue(widget.hasLayoutRuntimeValueForDiagnostics());
        Assert.assertTrue(containsTextCall(layoutClickedRenderContext.textCalls, "layout t="));
        Assert.assertTrue(containsTextCall(layoutClickedRenderContext.textCalls, "active=true"));

        ElementNode activeKeyframeCard = findElementContainingDirectText(widget, "Keyframe card: idle");
        clickElementCenter(widget, fixture.textMeasureService, activeKeyframeCard, 7L, 8L);
        RecordingUiRenderContext keyframeRunningRenderContext = new RecordingUiRenderContext();
        widget.render(keyframeRunningRenderContext);
        DocumentAnimationTimeline.DiagnosticsSnapshot keyframeRunningSnapshot = widget.getAnimationDiagnosticsSnapshot();
        Assert.assertEquals("layoutFillProbe", activeKeyframeCard.style().getAnimationName());
        Assert.assertTrue(keyframeRunningSnapshot.getKeyframeCount(DocumentAnimationImpact.LAYOUT) >= 1);
        Assert.assertTrue(containsTextCall(keyframeRunningRenderContext.textCalls, "layout t="));
        Assert.assertTrue(containsTextCall(keyframeRunningRenderContext.textCalls, "k="));

        animationClock.setCurrentTimeNanos(1_000_000_000L);
        RecordingUiRenderContext keyframeFilledRenderContext = new RecordingUiRenderContext();
        widget.render(keyframeFilledRenderContext);
        DocumentAnimationTimeline.DiagnosticsSnapshot keyframeFilledSnapshot = widget.getAnimationDiagnosticsSnapshot();
        int filledRuntimeLayoutGeneration = widget.getPerformanceDiagnosticsSnapshot().getRuntimeLayoutGeneration();
        Assert.assertEquals(0, keyframeFilledSnapshot.getKeyframeCount(DocumentAnimationImpact.LAYOUT));
        Assert.assertTrue(keyframeFilledSnapshot.getForwardsFillCount(DocumentAnimationImpact.LAYOUT) >= 1);
        Assert.assertTrue(containsTextCall(keyframeFilledRenderContext.textCalls, "f="));

        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(filledRuntimeLayoutGeneration,
                widget.getPerformanceDiagnosticsSnapshot().getRuntimeLayoutGeneration());

        ElementNode clearKeyframeCard = findElementContainingDirectText(widget, "Keyframe card: clear fill");
        Assert.assertNotNull(clearKeyframeCard);
        clickElementCenter(widget, fixture.textMeasureService, clearKeyframeCard, 9L, 10L);
        RecordingUiRenderContext keyframeClearedRenderContext = new RecordingUiRenderContext();
        widget.render(keyframeClearedRenderContext);
        DocumentAnimationTimeline.DiagnosticsSnapshot keyframeClearedSnapshot = widget.getAnimationDiagnosticsSnapshot();
        Assert.assertNull(clearKeyframeCard.style().getAnimationName());
        Assert.assertEquals(0, keyframeClearedSnapshot.getKeyframeCount(DocumentAnimationImpact.LAYOUT));
        Assert.assertEquals(0, keyframeClearedSnapshot.getForwardsFillCount(DocumentAnimationImpact.LAYOUT));
        Assert.assertTrue(containsTextCall(keyframeClearedRenderContext.textCalls, "Keyframe card: idle"));
    }

    /**
     * 验证 smoke 子页中的 HTML-like 输入目标会响应焦点、文本输入和退格键。
     */
    @Test
    public void shouldUpdateSmokeInputTargetWhenFocusedAndTyped() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(31, 47, 760, 320);
        RecordingUiRenderContext initialRenderContext = new RecordingUiRenderContext();

        widget.render(initialRenderContext);
        Assert.assertTrue(containsTextCall(initialRenderContext.textCalls, "Type target: click then type"));

        ElementNode inputTarget = findElementContainingDirectText(widget, "Type target: click then type");
        clickElementCenter(widget, fixture.textMeasureService, inputTarget, 1L, 2L);
        widget.onTextInput(new UiTextInputEvent("A\nB", 2L));
        RecordingUiRenderContext typedRenderContext = new RecordingUiRenderContext();
        widget.render(typedRenderContext);

        Assert.assertTrue(containsTextCall(typedRenderContext.textCalls, "AB"));
        Assert.assertTrue(containsFillColor(typedRenderContext.drawCalls, 0xFFC53030));
        Assert.assertTrue(containsBorderColor(typedRenderContext.drawCalls, 0xFFD69E2E));

        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_BACK, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 3L));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_BACK, 0, 0, UiKeyEvent.Action.REPEATED, false, false, false,
                false, 4L));
        RecordingUiRenderContext deletedRenderContext = new RecordingUiRenderContext();
        widget.render(deletedRenderContext);

        Assert.assertTrue(containsTextCall(deletedRenderContext.textCalls, "Type target: click then type"));
    }

    /**
     * 验证 smoke 子页中的 HTML-like Tab 目标会响应内部焦点遍历。
     */
    @Test
    public void shouldUpdateSmokeTabTargetWhenTraversed() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(31, 47, 760, 320);

        widget.onFocusTraversalEntered(false);
        RecordingUiRenderContext inputFocusedRenderContext = new RecordingUiRenderContext();
        widget.render(inputFocusedRenderContext);

        Assert.assertTrue(containsTextCall(inputFocusedRenderContext.textCalls, "Type target: click then type"));
        Assert.assertTrue(containsFillColor(inputFocusedRenderContext.drawCalls, 0xFFD69E2E));

        Assert.assertTrue(widget.onFocusTraversal(false));
        RecordingUiRenderContext tabFocusedRenderContext = new RecordingUiRenderContext();
        widget.render(tabFocusedRenderContext);

        Assert.assertTrue(containsTextCall(tabFocusedRenderContext.textCalls, "Tab target: focused"));
        Assert.assertTrue(containsBorderColor(tabFocusedRenderContext.drawCalls, 0xFFD6BCFA));

        Assert.assertTrue(widget.onFocusTraversal(true));
        RecordingUiRenderContext reverseRenderContext = new RecordingUiRenderContext();
        widget.render(reverseRenderContext);

        Assert.assertTrue(containsTextCall(reverseRenderContext.textCalls, "Tab target: idle"));
        Assert.assertTrue(containsFillColor(reverseRenderContext.drawCalls, 0xFFD69E2E));
    }

    /**
     * 验证 smoke 子页中的 HTML-like 按钮控件可通过键盘激活。
     */
    @Test
    public void shouldUpdateSmokeButtonControlWhenKeyboardActivated() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(31, 47, 760, 320);

        widget.onFocusTraversalEntered(false);
        Assert.assertTrue(widget.onFocusTraversal(false));
        Assert.assertTrue(widget.onFocusTraversal(false));
        widget.onKeyEvent(new UiKeyEvent(Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED, false, false, false,
                false, 4L));
        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        widget.render(renderContext);

        Assert.assertTrue(containsTextCall(renderContext.textCalls, "Button ctrl: 1"));
        Assert.assertTrue(containsFillColor(renderContext.drawCalls, 0xFF2C5282));
        Assert.assertTrue(containsBorderColor(renderContext.drawCalls, 0xFFBEE3F8));
    }

    /**
     * 验证 smoke 子页中的 HTML-like 开关控件已按样例约定接入并初始化为开启态。
     */
    @Test
    public void shouldExposeSmokeToggleControlInEnabledState() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(31, 47, 760, 320);

        widget.onFocusTraversalEntered(false);
        RecordingUiRenderContext onRenderContext = new RecordingUiRenderContext();
        widget.render(onRenderContext);
        Assert.assertTrue(containsFillColor(onRenderContext.drawCalls, 0xFF48BB78));

        ElementNode toggleTarget = findElementByAttribute(widget.getDocument().getRootElement(), "data-smoke-control",
                "toggle");
        Assert.assertNotNull(toggleTarget);
        Assert.assertEquals(Integer.valueOf(0xFF48BB78), toggleTarget.style().getBackgroundColor());
        Assert.assertTrue(toggleTarget.isFocusable());
    }

    /**
     * 验证 Smoke 页内 inline span 可以通过文本 fragment 命中并触发 click handler。
     */
    @Test
    public void shouldUpdateSmokeInlineSpanWhenClicked() {
        TestFixture fixture = new TestFixture();

        fixture.controller.configureDocumentPage();
        fixture.controller.buildDocument();
        HtmlLikeDocumentWidget widget = fixture.controller.getHtmlLikeDocumentWidget();
        widget.applyLayoutBounds(31, 47, 760, 900);
        RecordingUiRenderContext initialRenderContext = new RecordingUiRenderContext();

        widget.render(initialRenderContext);
        Assert.assertTrue(containsTextCall(initialRenderContext.textCalls, "amber span hit: 0"));

        ElementNode inlineTarget = findElementContainingDirectText(widget, "amber span hit: 0");
        Assert.assertTrue(inlineTarget.getClickHandler().onClick(new DocumentElementClickEvent(inlineTarget,
                inlineTarget, 0, 0, 0, 2L)));
        RecordingUiRenderContext clickedRenderContext = new RecordingUiRenderContext();
        widget.render(clickedRenderContext);

        Assert.assertTrue(containsTextCall(clickedRenderContext.textCalls, "amber span hit: 1"));
        Assert.assertTrue(containsFillColor(clickedRenderContext.drawCalls, 0x5538BDF8));
    }

    private static List<String> collectDocumentTexts(HtmlLikeDocumentWidget widget) {
        List<String> texts = new ArrayList<String>();
        if (widget == null || widget.getDocument() == null) {
            return texts;
        }
        collectTextsFromNode(widget.getDocument().getRootElement(), texts);
        return texts;
    }

    private static void collectTextsFromNode(DocumentNode node, List<String> texts) {
        if (node.getNodeType() == DocumentNodeType.TEXT) {
            String text = ((TextNode) node).getText();
            if (text != null && !text.isEmpty()) {
                texts.add(text);
            }
        }
        if (node.getNodeType() == DocumentNodeType.ELEMENT) {
            ElementNode element = (ElementNode) node;
            for (DocumentNode child : element.getChildren()) {
                collectTextsFromNode(child, texts);
            }
        }
    }

    private static boolean containsText(List<String> labelTexts, String expectedSnippet) {
        for (String labelText : labelTexts) {
            if (labelText != null && labelText.contains(expectedSnippet)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTextCall(List<TextCall> textCalls, String expectedSnippet) {
        for (TextCall textCall : textCalls) {
            if (textCall.text != null && textCall.text.contains(expectedSnippet)) {
                return true;
            }
        }
        return false;
    }

    private static void assertSolidBorder(ElementNode element) {
        Assert.assertNotNull(element);
        Assert.assertEquals(UiBorderStyle.SOLID, element.style().getBorderStyle());
    }

    private static boolean containsFillColor(List<DrawCall> drawCalls, int expectedColor) {
        for (DrawCall drawCall : drawCalls) {
            if (drawCall.surfaceStyle.fillColor == expectedColor) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsBorderColor(List<DrawCall> drawCalls, int expectedColor) {
        for (DrawCall drawCall : drawCalls) {
            if (drawCall.surfaceStyle.borderColor == expectedColor) {
                return true;
            }
        }
        return false;
    }

    private static void assertSmokeInlineProbeSplitsAmberSpan(HtmlLikeDocumentWidget widget,
            TextMeasureService textMeasureService) {
        ElementNode amberSpan = findElementContainingDirectText(widget, "amber span hit: 0");
        DocumentLayoutBox rootBox = DocumentLayoutEngine.layoutViewportRoot(widget.getDocument().getRootElement(),
                widget.getWidth(), widget.getHeight(), textMeasureService);
        List<DocumentPaintCommand> commands = DocumentPaintEngine.buildPaintCommands(rootBox);
        int amberSurfaceCount = 0;
        boolean sawLeftCornerSlice = false;
        boolean sawRightCornerSlice = false;
        for (DocumentPaintCommand command : commands) {
            if (command.getType() != DocumentPaintCommandType.BACKGROUND
                    || command.getElement().__getElementUid() != amberSpan.__getElementUid()
                    || command.getColor() != 0x334F46E5) {
                continue;
            }
            amberSurfaceCount++;
            int cornerMask = command.getCornerMask();
            sawLeftCornerSlice = sawLeftCornerSlice || cornerMask == (UiSurfaceStyle.CORNER_TOP_LEFT
                    | UiSurfaceStyle.CORNER_BOTTOM_LEFT);
            sawRightCornerSlice = sawRightCornerSlice || cornerMask == (UiSurfaceStyle.CORNER_TOP_RIGHT
                    | UiSurfaceStyle.CORNER_BOTTOM_RIGHT);
        }
        Assert.assertTrue(amberSurfaceCount >= 2);
        Assert.assertTrue(sawLeftCornerSlice);
        Assert.assertTrue(sawRightCornerSlice);
    }

    private static void assertAbsoluteProbeIsVisibleOutsideStaticWrapper(HtmlLikeDocumentWidget widget,
            TextMeasureService textMeasureService) {
        ElementNode staticWrapperElement = findElementContainingDirectText(widget, "static wrapper is not anchor");
        ElementNode nestedAbsoluteElement = findElementContainingDirectText(widget, "ABS OK");
        Assert.assertNotNull(staticWrapperElement);
        Assert.assertNotNull(nestedAbsoluteElement);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layoutViewportRoot(widget.getDocument().getRootElement(),
                widget.getWidth(), widget.getHeight(), textMeasureService);
        List<DocumentPaintCommand> paintCommands = DocumentPaintEngine.buildPaintCommands(rootBox);
        long staticWrapperUid = staticWrapperElement.__getElementUid();
        long nestedAbsoluteUid = nestedAbsoluteElement.__getElementUid();
        DocumentPaintCommand staticWrapperBackground = findPaintCommand(paintCommands,
                DocumentPaintCommandType.BACKGROUND, staticWrapperUid);
        DocumentPaintCommand nestedAbsoluteBackground = findPaintCommand(paintCommands,
                DocumentPaintCommandType.BACKGROUND, nestedAbsoluteUid);

        Assert.assertNotNull(staticWrapperBackground);
        Assert.assertNotNull(nestedAbsoluteBackground);
        Assert.assertTrue(nestedAbsoluteBackground.getTop() < staticWrapperBackground.getTop());
        Assert.assertTrue(nestedAbsoluteBackground.getRight() <= staticWrapperBackground.getRight());
        Assert.assertNull(findPaintCommand(paintCommands, DocumentPaintCommandType.CLIP_START, staticWrapperUid));
    }

    private static void assertGroupOpacityProbeKeepsChildColorsOpaque(HtmlLikeDocumentWidget widget,
            TextMeasureService textMeasureService) {
        ElementNode opacityGroupElement = findElementWithOpacity(widget.getDocument().getRootElement(), 0.55F);
        ElementNode redLayerElement = findElementWithBackgroundColor(widget.getDocument().getRootElement(), 0xFFFF4B4B);
        ElementNode blueLayerElement = findElementWithBackgroundColor(widget.getDocument().getRootElement(), 0xFF3B82F6);
        Assert.assertNotNull(opacityGroupElement);
        Assert.assertNotNull(redLayerElement);
        Assert.assertNotNull(blueLayerElement);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layoutViewportRoot(widget.getDocument().getRootElement(),
                widget.getWidth(), widget.getHeight(), textMeasureService);
        List<DocumentPaintCommand> paintCommands = DocumentPaintEngine.buildPaintCommands(rootBox);
        DocumentPaintCommand contextStart = findPaintCommand(paintCommands, DocumentPaintCommandType.PAINT_CONTEXT_START,
                opacityGroupElement.__getElementUid());
        DocumentPaintCommand redBackground = findPaintCommand(paintCommands, DocumentPaintCommandType.BACKGROUND,
                redLayerElement.__getElementUid());
        DocumentPaintCommand blueBackground = findPaintCommand(paintCommands, DocumentPaintCommandType.BACKGROUND,
                blueLayerElement.__getElementUid());

        Assert.assertNotNull(contextStart);
        Assert.assertEquals(0.55F, contextStart.getPaintContextOpacity(), 0.0F);
        Assert.assertNotNull(redBackground);
        Assert.assertNotNull(blueBackground);
        Assert.assertEquals(0xFFFF4B4B, redBackground.getColor());
        Assert.assertEquals(0xFF3B82F6, blueBackground.getColor());
        Assert.assertTrue(redBackground.getRight() > blueBackground.getLeft());
        Assert.assertTrue(indexOfCommand(paintCommands, redBackground) < indexOfCommand(paintCommands,
                blueBackground));
    }

    private static void assertStackingContextProbeKeepsHighZChildIsolated(HtmlLikeDocumentWidget widget,
            TextMeasureService textMeasureService) {
        ElementNode isolatedShellElement = findElementContainingDirectText(widget, "isolated z=0 shell");
        ElementNode redHighChildElement = findElementContainingDirectText(widget, "red child z=99");
        ElementNode blueCoverElement = findElementContainingDirectText(widget, "blue sibling z=1 should win");
        Assert.assertNotNull(isolatedShellElement);
        Assert.assertNotNull(redHighChildElement);
        Assert.assertNotNull(blueCoverElement);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layoutViewportRoot(widget.getDocument().getRootElement(),
                widget.getWidth(), widget.getHeight(), textMeasureService);
        List<DocumentPaintCommand> paintCommands = DocumentPaintEngine.buildPaintCommands(rootBox);
        DocumentPaintCommand isolatedContextStart = findPaintCommand(paintCommands,
                DocumentPaintCommandType.PAINT_CONTEXT_START, isolatedShellElement.__getElementUid());
        DocumentPaintCommand redBackground = findPaintCommand(paintCommands, DocumentPaintCommandType.BACKGROUND,
                redHighChildElement.__getElementUid());
        DocumentPaintCommand blueBackground = findPaintCommand(paintCommands, DocumentPaintCommandType.BACKGROUND,
                blueCoverElement.__getElementUid());

        Assert.assertNotNull(isolatedContextStart);
        Assert.assertNotNull(redBackground);
        Assert.assertNotNull(blueBackground);
        Assert.assertTrue(redBackground.getRight() > blueBackground.getLeft());
        Assert.assertTrue(redBackground.getBottom() > blueBackground.getTop());
        Assert.assertTrue(indexOfCommand(paintCommands, redBackground) < indexOfCommand(paintCommands,
                blueBackground));
    }

    private static DocumentPaintCommand findPaintCommand(List<DocumentPaintCommand> paintCommands,
            DocumentPaintCommandType type, long elementUid) {
        for (DocumentPaintCommand paintCommand : paintCommands) {
            if (paintCommand.getType() == type && paintCommand.getElement().__getElementUid() == elementUid) {
                return paintCommand;
            }
        }
        return null;
    }

    private static DocumentPaintCommand findElementBackgroundCommand(HtmlLikeDocumentWidget widget,
            TextMeasureService textMeasureService, ElementNode element) {
        DocumentLayoutBox rootBox = DocumentLayoutEngine.layoutViewportRoot(widget.getDocument().getRootElement(),
                widget.getWidth(), widget.getHeight(), textMeasureService);
        List<DocumentPaintCommand> paintCommands = DocumentPaintEngine.buildPaintCommands(rootBox);
        DocumentPaintCommand command = findPaintCommand(paintCommands, DocumentPaintCommandType.BACKGROUND,
                element.__getElementUid());
        Assert.assertNotNull(command);
        return command;
    }

    private static void clickElementCenter(HtmlLikeDocumentWidget widget, TextMeasureService textMeasureService,
            ElementNode element, long downTimeNanos, long upTimeNanos) {
        Assert.assertNotNull(element);
        DocumentLayoutBox rootBox = DocumentLayoutEngine.layoutViewportRoot(widget.getDocument().getRootElement(),
                widget.getWidth(), widget.getHeight(), textMeasureService);
        List<DocumentPaintCommand> paintCommands = DocumentPaintEngine.buildPaintCommands(rootBox);
        DocumentPaintCommand command = findPaintCommand(paintCommands, DocumentPaintCommandType.BACKGROUND,
                element.__getElementUid());
        if (command == null) {
            command = findPaintCommand(paintCommands, DocumentPaintCommandType.BORDER, element.__getElementUid());
        }
        int screenX;
        int screenY;
        if (command == null) {
            DocumentLayoutBox box = findLayoutBox(rootBox, element);
            Assert.assertNotNull(box);
            screenX = widget.getAbsoluteX() + box.getContentLeft() + Math.max(1, box.getContentWidth()) / 2;
            screenY = widget.getAbsoluteY() + box.getContentTop() + Math.max(1, box.getContentHeight()) / 2;
        } else {
            screenX = findClickablePointX(widget, element, command);
            screenY = findClickablePointY(widget, element, command, screenX);
        }
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, screenX, screenY, 0, 0, 0, 0,
                downTimeNanos));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, screenX, screenY, 0, 0, 0, 0,
                upTimeNanos));
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

    private static int findClickablePointX(HtmlLikeDocumentWidget widget, ElementNode element,
            DocumentPaintCommand command) {
        int startX = widget.getAbsoluteX() + command.getLeft();
        int endX = widget.getAbsoluteX() + command.getRight() - 1;
        int y = widget.getAbsoluteY() + command.getTop() + Math.max(0, command.getHeight() / 2);
        for (int x = startX; x <= endX; x++) {
            if (widget.findElementAt(x, y) == element) {
                return x;
            }
        }
        return widget.getAbsoluteX() + command.getLeft() + Math.max(1, command.getWidth()) / 2;
    }

    private static int findClickablePointY(HtmlLikeDocumentWidget widget, ElementNode element,
            DocumentPaintCommand command, int screenX) {
        int startY = widget.getAbsoluteY() + command.getTop();
        int endY = widget.getAbsoluteY() + command.getBottom() - 1;
        for (int y = startY; y <= endY; y++) {
            if (widget.findElementAt(screenX, y) == element) {
                return y;
            }
        }
        return widget.getAbsoluteY() + command.getTop() + Math.max(1, command.getHeight()) / 2;
    }

    private static ElementNode findElementContainingDirectText(HtmlLikeDocumentWidget widget, String expectedText) {
        return findElementContainingDirectText(widget.getDocument().getRootElement(), expectedText);
    }

    private static ElementNode findElementContainingDirectText(ElementNode element, String expectedText) {
        for (DocumentNode child : element.getChildren()) {
            if (child.getNodeType() == DocumentNodeType.TEXT && expectedText.equals(((TextNode) child).getText())) {
                return element;
            }
            if (child.getNodeType() == DocumentNodeType.ELEMENT) {
                ElementNode found = findElementContainingDirectText((ElementNode) child, expectedText);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static ElementNode findElementWithOpacity(ElementNode element, float expectedOpacity) {
        Float opacity = element.style().getOpacity();
        if (opacity != null && Math.abs(opacity.floatValue() - expectedOpacity) < 0.001F) {
            return element;
        }
        for (DocumentNode child : element.getChildren()) {
            if (child.getNodeType() == DocumentNodeType.ELEMENT) {
                ElementNode found = findElementWithOpacity((ElementNode) child, expectedOpacity);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static ElementNode findElementWithBackgroundColor(ElementNode element, int expectedColor) {
        Integer backgroundColor = element.style().getBackgroundColor();
        if (backgroundColor != null && backgroundColor.intValue() == expectedColor) {
            return element;
        }
        for (DocumentNode child : element.getChildren()) {
            if (child.getNodeType() == DocumentNodeType.ELEMENT) {
                ElementNode found = findElementWithBackgroundColor((ElementNode) child, expectedColor);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static ElementNode findElementByAttribute(ElementNode element, String attributeName, String attributeValue) {
        if (attributeValue.equals(element.getAttribute(attributeName))) {
            return element;
        }
        for (DocumentNode child : element.getChildren()) {
            if (child.getNodeType() == DocumentNodeType.ELEMENT) {
                ElementNode found = findElementByAttribute((ElementNode) child, attributeName, attributeValue);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int indexOfCommand(List<DocumentPaintCommand> paintCommands, DocumentPaintCommand expectedCommand) {
        for (int index = 0; index < paintCommands.size(); index++) {
            if (paintCommands.get(index) == expectedCommand) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 页面控制器测试夹具。
     */
    private static final class TestFixture {

        private final TextMeasureService textMeasureService = new DeterministicTextMeasureService();
        private final DocumentUiScope documentUi = new DocumentUiScope(textMeasureService, UiRuntimeAdapters.empty());
        private final DirectDocumentPageAuthoringSurface pageSurface = new DirectDocumentPageAuthoringSurface();
        private final HtmlLikeSmokeDocumentPageController controller = new HtmlLikeSmokeDocumentPageController(
                documentUi, pageSurface, textMeasureService);
    }

    /**
     * 记录 surface 绘制调用的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<DrawCall> drawCalls = new ArrayList<DrawCall>();
        private final List<ClipCall> clipCalls = new ArrayList<ClipCall>();
        private final List<TextCall> textCalls = new ArrayList<TextCall>();
        private final List<BackdropCall> backdropCalls = new ArrayList<BackdropCall>();
        private int popClipCount;

        private RecordingUiRenderContext() {
            super(1024, 768, 0, 0, 0.0F);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, UiSurfaceStyle surfaceStyle) {
            drawCalls.add(new DrawCall(left, top, right, bottom, surfaceStyle));
        }

        @Override
        public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
                int cornerRadius) {
            backdropCalls.add(new BackdropCall(left, top, right, bottom, blurRadius, saturation, cornerRadius));
        }

        @Override
        public void drawBackdropFilter(int left, int top, int right, int bottom, int blurRadius, float saturation,
                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
            int cornerRadius = cornerRadii == null ? 0 : cornerRadii.getUniformRadius();
            backdropCalls.add(new BackdropCall(left, top, right, bottom, blurRadius, saturation, cornerRadius));
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {
            clipCalls.add(new ClipCall(left, top, right, bottom, cornerRadius));
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom,
                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
            int cornerRadius = cornerRadii == null ? 0 : cornerRadii.getUniformRadius();
            clipCalls.add(new ClipCall(left, top, right, bottom, cornerRadius));
        }

        @Override
        public void popClip() {
            popClipCount++;
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            textCalls.add(new TextCall(text, x, y, color, shadow));
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow,
                club.heiqi.uilib.ui.text.TextContentMode textContentMode) {
            textCalls.add(new TextCall(text, x, y, color, shadow));
        }

        @Override
        public boolean supportsDeferredTextBatching() {
            return false;
        }

        @Override
        public void fillRect(int left, int top, int right, int bottom, int color) {}

        @Override
        public int measureTextWidth(String text) {
            return text == null ? 0 : text.length() * 12;
        }

        @Override
        public int getTextLineHeight() {
            return 18;
        }

        @Override
        public void pushPaintContext(int left, int top, int right, int bottom, float opacity) {}

        @Override
        public boolean isCurrentPaintContextLayerActive() {
            return false;
        }

        @Override
        public void popPaintContext() {}
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
     * 单次 clip 投影记录。
     */
    private static final class ClipCall {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final int cornerRadius;

        private ClipCall(int left, int top, int right, int bottom, int cornerRadius) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.cornerRadius = cornerRadius;
        }
    }

    /**
     * 单次 HTML-like 文本绘制记录。
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
     * 单次 backdrop filter 投影记录。
     */
    private static final class BackdropCall {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final int blurRadius;
        private final float saturation;
        private final int cornerRadius;

        private BackdropCall(int left, int top, int right, int bottom, int blurRadius, float saturation,
                int cornerRadius) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.blurRadius = blurRadius;
            this.saturation = saturation;
            this.cornerRadius = cornerRadius;
        }
    }

    /**
     * 供测试推进动画时间的手动时钟。
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
     * 供测试使用的确定性文本测量服务。
     */
    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 6;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            return text == null ? "" : text;
        }

        @Override
        public List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            return Collections.singletonList(text == null ? "" : text);
        }
    }
}
