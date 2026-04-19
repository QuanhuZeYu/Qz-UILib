package club.heiqi.uilib.ui.document;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.render.UiBackdropEffectSpec;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.theme.UiDocumentTheme;
import club.heiqi.uilib.ui.theme.UiDocumentThemes;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * `DocumentPageWidget` 的结构裁剪与构建契约测试。
 */
public class DocumentPageWidgetTest {

    /**
     * 验证文档页壳继续保留矩形 viewport clip，并保持现有块级构建契约。
     */
    @Test
    public void shouldKeepBlockBuildContractAndUseRectangularViewportClip() {
        UiDocumentTheme theme = UiDocumentThemes.current();
        DocumentPageWidget pageWidget = new DocumentPageWidget(theme, new DeterministicTextMeasureService());
        DeferredProbeWidget probeWidget = new DeferredProbeWidget();

        pageWidget.setShellPadding(16)
                .setContentWidthRange(320, 640)
                .setMinContentHeight(180)
                .setViewportFillRatio(0.95F, 0.90F)
                .addBlock(probeWidget);
        pageWidget.applyLayoutBounds(0, 0, 420, 260);

        Assert.assertEquals(1, pageWidget.getChildren().size());
        Widget content = pageWidget.getChildren().get(0);
        Assert.assertEquals(1, content.getChildren().size());
        Assert.assertSame(probeWidget, content.getChildren().get(0));

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        pageWidget.render(renderContext);

        Assert.assertEquals(1, renderContext.deferredPostMainPasses.size());
        RecordedDeferredPostMainPass deferredPass = renderContext.deferredPostMainPasses.get(0);
        Assert.assertNotNull(deferredPass.clipRect);
        Assert.assertArrayEquals(new int[] { 16, 16, 404, 244 }, deferredPass.clipRect);
        Assert.assertTrue(deferredPass.roundedClipRegions.isEmpty());
        Assert.assertTrue(renderContext.backdropEffectRequests.isEmpty());
    }

    /**
     * 验证页面壳 effect 只登记宿主请求，不改变矩形 viewport clip 契约。
     */
    @Test
    public void shouldRegisterShellBackdropEffectWithoutChangingViewportClipContract() {
        UiDocumentTheme theme = UiDocumentThemes.current();
        DocumentPageWidget pageWidget = new DocumentPageWidget(theme, new DeterministicTextMeasureService());
        DeferredProbeWidget probeWidget = new DeferredProbeWidget();

        pageWidget.setShellPadding(16)
                .setContentWidthRange(320, 640)
                .setMinContentHeight(180)
                .setViewportFillRatio(0.95F, 0.90F)
                .applyShellBackdropEffect(UiBackdropEffectSpec.glass(0x66DDE7FF, theme.getShellSurface().cornerRadius))
                .addBlock(probeWidget);
        pageWidget.applyLayoutBounds(0, 0, 420, 260);

        RecordingUiRenderContext renderContext = new RecordingUiRenderContext();
        pageWidget.render(renderContext);

        Assert.assertEquals(1, renderContext.backdropEffectRequests.size());
        RecordedBackdropEffectRequest effectRequest = renderContext.backdropEffectRequests.get(0);
        Assert.assertArrayEquals(new int[] { 0, 0, 420, 260 }, effectRequest.bounds);
        Assert.assertEquals(0x66DDE7FF, effectRequest.effectSpec.tintColor);
        Assert.assertEquals(theme.getShellSurface().cornerRadius, effectRequest.effectSpec.cornerRadius);

        Assert.assertEquals(1, renderContext.deferredPostMainPasses.size());
        RecordedDeferredPostMainPass deferredPass = renderContext.deferredPostMainPasses.get(0);
        Assert.assertNotNull(deferredPass.clipRect);
        Assert.assertArrayEquals(new int[] { 16, 16, 404, 244 }, deferredPass.clipRect);
        Assert.assertTrue(deferredPass.roundedClipRegions.isEmpty());
    }

    /**
     * 记录裁剪快照的渲染上下文。
     */
    private static final class RecordingUiRenderContext extends UiRenderContext {

        private final List<RecordedDeferredPostMainPass> deferredPostMainPasses = new ArrayList<RecordedDeferredPostMainPass>();
        private final List<RecordedBackdropEffectRequest> backdropEffectRequests = new ArrayList<RecordedBackdropEffectRequest>();
        private final Deque<RecordedClipState> clipStates = new ArrayDeque<RecordedClipState>();

        private RecordingUiRenderContext() {
            super(640, 480, 0, 0, 0.0F);
        }

        @Override
        public void fillRect(int left, int top, int right, int bottom, int color) {}

        @Override
        public void drawBorder(int left, int top, int right, int bottom, int color) {}

        @Override
        public void drawSurface(int left, int top, int right, int bottom,
                club.heiqi.uilib.ui.theme.UiSurfaceStyle surfaceStyle) {}

        @Override
        public void pushClip(int left, int top, int right, int bottom) {
            pushClip(left, top, right, bottom, 0);
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {
            clipStates.push(new RecordedClipState(new int[] { left, top, right, bottom }, Math.max(0, cornerRadius)));
        }

        @Override
        public void popClip() {
            if (!clipStates.isEmpty()) {
                clipStates.pop();
            }
        }

        @Override
        public void enqueueDeferredPostMainPass(DeferredPostMainPassReplay replay) {
            int[] clipRect = clipStates.isEmpty() ? null : clipStates.peek().clipRect.clone();
            List<RecordedRoundedClipRegion> roundedClipRegions = new ArrayList<RecordedRoundedClipRegion>();
            for (RecordedClipState clipState : clipStates) {
                if (clipState.cornerRadius <= 0) {
                    continue;
                }
                int[] clip = clipState.clipRect;
                roundedClipRegions.add(new RecordedRoundedClipRegion(clip[0], clip[1], clip[2], clip[3],
                        clipState.cornerRadius));
            }
            Collections.reverse(roundedClipRegions);
            deferredPostMainPasses.add(new RecordedDeferredPostMainPass(replay, clipRect, roundedClipRegions));
        }

        @Override
        public void enqueueBackdropEffect(int left, int top, int right, int bottom, UiBackdropEffectSpec effectSpec) {
            backdropEffectRequests.add(new RecordedBackdropEffectRequest(new int[] { left, top, right, bottom }, effectSpec));
        }
    }

    /**
     * 供测试使用的固定尺寸延迟回放探针。
     */
    private static final class DeferredProbeWidget extends Widget {

        @Override
        protected void drawSelf(UiRenderContext context) {
            context.enqueueDeferredPostMainPass(new UiRenderContext.DeferredPostMainPassReplay() {
                @Override
                public void replay() {}
            });
        }

        @Override
        public int getPreferredWidth() {
            return 240;
        }

        @Override
        public int getPreferredHeight() {
            return 96;
        }
    }

    private static final class RecordedClipState {

        private final int[] clipRect;
        private final int cornerRadius;

        private RecordedClipState(int[] clipRect, int cornerRadius) {
            this.clipRect = clipRect;
            this.cornerRadius = cornerRadius;
        }
    }

    private static final class RecordedDeferredPostMainPass {

        private final int[] clipRect;
        private final List<RecordedRoundedClipRegion> roundedClipRegions;

        private RecordedDeferredPostMainPass(UiRenderContext.DeferredPostMainPassReplay replay, int[] clipRect,
                List<RecordedRoundedClipRegion> roundedClipRegions) {
            this.clipRect = clipRect;
            this.roundedClipRegions = roundedClipRegions;
        }
    }

    private static final class RecordedRoundedClipRegion {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final int cornerRadius;

        private RecordedRoundedClipRegion(int left, int top, int right, int bottom, int cornerRadius) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.cornerRadius = cornerRadius;
        }

        private int getLeft() {
            return left;
        }

        private int getTop() {
            return top;
        }

        private int getRight() {
            return right;
        }

        private int getBottom() {
            return bottom;
        }

        private int getCornerRadius() {
            return cornerRadius;
        }
    }

    private static final class RecordedBackdropEffectRequest {

        private final int[] bounds;
        private final UiBackdropEffectSpec effectSpec;

        private RecordedBackdropEffectRequest(int[] bounds, UiBackdropEffectSpec effectSpec) {
            this.bounds = bounds;
            this.effectSpec = effectSpec;
        }
    }

    /**
     * 供测试使用的确定性文本测量桩。
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
