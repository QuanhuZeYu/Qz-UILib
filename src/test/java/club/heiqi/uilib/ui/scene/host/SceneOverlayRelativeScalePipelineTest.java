package club.heiqi.uilib.ui.scene.host;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.OverlayHandle;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextMeasureServiceSceneAdapter;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;

/**
 * overlay 相对倍率 s 在帧管线中的渲染口径测试（headless）。
 *
 * <p>锚定三件事：① 全屏 overlay 的布局视口 = 宿主逻辑尺寸 ÷ s；② 回放按 s 包装后端并把原点 ÷ s
 * （物理尺寸 = 逻辑尺寸 × s，宿主 ctx 倍率已由调用方施加）；③ s == 1.0F 与改动前逐位等价
 * （不走 scaled、偏移原样、显式 1.0F 与默认路径回放序列一致）。</p>
 */
public class SceneOverlayRelativeScalePipelineTest {

    /** 主树背景色：用于区分主树命令（不参与 overlay 缩放）。 */
    private static final int MAIN_COLOR = 0xFF333333;
    /** overlay 背景色。 */
    private static final int OVERLAY_COLOR = 0xFFFF0000;

    private static SceneTextMeasurer measurer() {
        return new TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance());
    }

    private static Fixture fixture() {
        return new Fixture();
    }

    /** 全屏 overlay（percentWidth=100 + fillParentHeight）的布局视口按 s 收缩。 */
    @Test
    public void relativeScaleShouldShrinkFullScreenOverlayViewport() {
        Fixture fx = fixture();
        SceneNode overlay = fullScreenOverlay();
        OverlayHandle handle = fx.addOverlay(overlay);

        fx.run(200, 120, 0, 0);
        Assert.assertEquals("s == 1.0F 时 overlay 逻辑宽 = 宿主宽",
                200, ((LayoutBox) overlay.getCachedLayout()).getWidth());
        Assert.assertEquals("s == 1.0F 时 overlay 逻辑高 = 宿主高",
                120, ((LayoutBox) overlay.getCachedLayout()).getHeight());

        handle.setRelativeScale(2.0F);
        fx.run(200, 120, 0, 0);
        Assert.assertEquals("s=2 时 overlay 逻辑宽 = w / s",
                100, ((LayoutBox) overlay.getCachedLayout()).getWidth());
        Assert.assertEquals("s=2 时 overlay 逻辑高 = h / s",
                60, ((LayoutBox) overlay.getCachedLayout()).getHeight());

        handle.setRelativeScale(1.5F);
        fx.run(200, 120, 0, 0);
        Assert.assertEquals("取整统一用 Math.round(w / s)",
                Math.round(200 / 1.5F), ((LayoutBox) overlay.getCachedLayout()).getWidth());
        Assert.assertEquals("取整统一用 Math.round(h / s)",
                Math.round(120 / 1.5F), ((LayoutBox) overlay.getCachedLayout()).getHeight());
    }

    /** 回放：物理尺寸 = 逻辑尺寸 × s（后端包一层 scaled(s)），原点 = round(abs / s)。 */
    @Test
    public void relativeScaleShouldScaleReplayAndShrinkOrigin() {
        Fixture fx = fixture();
        SceneNode overlay = fixedOverlay(60, 30);
        OverlayHandle handle = fx.addOverlay(overlay);
        handle.setRelativeScale(2.0F);

        fx.run(200, 120, 8, 4);

        Assert.assertEquals("overlay 逻辑尺寸不随 s 变化（s 只改视口与回放）",
                60, ((LayoutBox) overlay.getCachedLayout()).getWidth());
        Assert.assertEquals(Arrays.asList(Float.valueOf(2.0F)), fx.backend.scales);
        int[] rect = fx.backend.lastFillRect(OVERLAY_COLOR);
        Assert.assertNotNull("overlay 必须产生背景绘制命令", rect);
        Assert.assertEquals("物理 X = round(absX / s) * s", 8, rect[0]);
        Assert.assertEquals("物理 Y = round(absY / s) * s", 4, rect[1]);
        Assert.assertEquals("物理右边界 = (round(absX / s) + 逻辑宽) * s", 128, rect[2]);
        Assert.assertEquals("物理下边界 = (round(absY / s) + 逻辑高) * s", 64, rect[3]);
        int[] mainRect = fx.backend.lastFillRect(MAIN_COLOR);
        // 主树同样按 absX/absY 平移，但不经过 overlay 的 scaled(s)。
        Assert.assertArrayEquals("主树命令不受 overlay 缩放影响", new int[] {8, 4, 208, 124}, mainRect);
    }

    /** s == 1.0F 零回归：不包 scaled(1)，偏移原样（逐位等价）。 */
    @Test
    public void identityRelativeScaleShouldKeepLegacyPath() {
        Fixture fx = fixture();
        SceneNode overlay = fixedOverlay(60, 30);
        fx.addOverlay(overlay);

        fx.run(200, 120, 8, 4);

        Assert.assertTrue("s == 1.0F 不得经过 scaled 装饰器", fx.backend.scales.isEmpty());
        int[] rect = fx.backend.lastFillRect(OVERLAY_COLOR);
        Assert.assertNotNull(rect);
        Assert.assertArrayEquals("s == 1.0F 偏移必须原样", new int[] {8, 4, 68, 34}, rect);
        Assert.assertArrayEquals("主树命令保持原样（仅按 absX/absY 平移）",
                new int[] {8, 4, 208, 124}, fx.backend.lastFillRect(MAIN_COLOR));
    }

    /** 显式 setRelativeScale(1.0F) 与未声明（默认）逐位等价。 */
    @Test
    public void explicitIdentityScaleShouldMatchDefaultBaseline() {
        Fixture baseline = fixture();
        baseline.addOverlay(fixedOverlay(60, 30));
        baseline.run(200, 120, 8, 4);

        Fixture explicit = fixture();
        OverlayHandle handle = explicit.addOverlay(fixedOverlay(60, 30));
        handle.setRelativeScale(1.0F);
        explicit.run(200, 120, 8, 4);

        Assert.assertEquals("显式 1.0F 与默认路径的回放序列必须逐位一致",
                baseline.backend.trace(), explicit.backend.trace());
        Assert.assertTrue(explicit.backend.scales.isEmpty());
    }

    /** relativeScale 变化当帧生效（pipeline 每帧读同一 entry 字段）。 */
    @Test
    public void relativeScaleChangeShouldTakeEffectInSameFrame() {
        Fixture fx = fixture();
        SceneNode overlay = fixedOverlay(60, 30);
        OverlayHandle handle = fx.addOverlay(overlay);

        fx.run(200, 120, 0, 0);
        Assert.assertArrayEquals(new int[] {0, 0, 60, 30}, fx.backend.lastFillRect(OVERLAY_COLOR));
        Assert.assertTrue(fx.backend.scales.isEmpty());

        handle.setRelativeScale(2.0F);
        fx.run(200, 120, 0, 0);
        Assert.assertArrayEquals("下一帧立即按新 s 回放",
                new int[] {0, 0, 120, 60}, fx.backend.lastFillRect(OVERLAY_COLOR));
        Assert.assertEquals(Arrays.asList(Float.valueOf(2.0F)), fx.backend.scales);
    }

    /** 多 overlay 各自使用自身 s（互不串扰），主树保持 1:1。 */
    @Test
    public void multipleOverlaysShouldUseOwnRelativeScale() {
        Fixture fx = fixture();
        SceneNode identity = fixedOverlay(60, 30);
        fx.addOverlay(identity);
        SceneNode doubled = fixedOverlay(60, 30);
        OverlayHandle doubledHandle = fx.addOverlay(doubled);
        doubledHandle.setRelativeScale(2.0F);

        fx.run(200, 120, 0, 0);

        Assert.assertEquals("仅声明 s 的 overlay 走 scaled", Arrays.asList(Float.valueOf(2.0F)),
                fx.backend.scales);
        Assert.assertEquals("identity overlay 逻辑尺寸不变",
                60, ((LayoutBox) identity.getCachedLayout()).getWidth());
        Assert.assertEquals("scaled overlay 逻辑尺寸不变",
                60, ((LayoutBox) doubled.getCachedLayout()).getWidth());
        List<int[]> overlayRects = fx.backend.fillRectsOf(OVERLAY_COLOR);
        Assert.assertEquals("两个 overlay 各产生一条背景命令", 2, overlayRects.size());
        Assert.assertArrayEquals("identity overlay 物理尺寸 = 逻辑尺寸",
                new int[] {0, 0, 60, 30}, overlayRects.get(0));
        Assert.assertArrayEquals("scaled overlay 物理尺寸 = 逻辑尺寸 × s",
                new int[] {0, 0, 120, 60}, overlayRects.get(1));
    }

    private static SceneNode fullScreenOverlay() {
        SceneNode overlay = new SceneNode();
        overlay.setPercentWidth(100);
        overlay.setFillParentHeight(true);
        overlay.setBackgroundColor(OVERLAY_COLOR);
        return overlay;
    }

    private static SceneNode fixedOverlay(int width, int height) {
        SceneNode overlay = new SceneNode();
        overlay.setPreferredWidth(width);
        overlay.setPreferredHeight(height);
        overlay.setBackgroundColor(OVERLAY_COLOR);
        return overlay;
    }

    private static final class Fixture {

        final SceneRuntime runtime;
        final SceneLayoutEngine layoutEngine;
        final ScenePaintEngine paintEngine;
        final SceneFramePipeline pipeline;
        final SceneNode root;
        final ScaledRecordingBackend backend = new ScaledRecordingBackend();

        Fixture() {
            SceneTextMeasurer m = measurer();
            this.runtime = new SceneRuntime(m);
            this.layoutEngine = new SceneLayoutEngine(m);
            this.paintEngine = new ScenePaintEngine(m);
            this.pipeline = new SceneFramePipeline(runtime, layoutEngine, paintEngine,
                    new ScenePaintReplayer(), m, null);
            this.root = new SceneNode();
            root.setFillParentHeight(true);
            root.setBackgroundColor(MAIN_COLOR);
        }

        OverlayHandle addOverlay(SceneNode overlay) {
            return runtime.getOverlayHost().register(overlay);
        }

        void run(int w, int h, int absX, int absY) {
            pipeline.run(root, w, h, backend, absX, absY, 1_000_000L);
        }
    }

    /**
     * 记录型渲染后端：记录 {@link UiRenderBackend#scaled(float)} 调用与 fillRect 物理坐标。
     *
     * <p>scaled 走接口默认实现（返回通用缩放装饰器），因此 fillRect 收到的是**缩放后的物理坐标**，
     * 可直接断言「overlay 实绘尺寸 = 逻辑尺寸 × s」。</p>
     */
    private static final class ScaledRecordingBackend implements UiRenderBackend {

        final List<Float> scales = new ArrayList<Float>();
        private final List<int[]> rects = new ArrayList<int[]>();
        private final List<Integer> colors = new ArrayList<Integer>();

        @Override
        public UiRenderBackend scaled(float scale) {
            scales.add(Float.valueOf(scale));
            return UiRenderBackend.super.scaled(scale);
        }

        @Override
        public void fillRect(int left, int top, int right, int bottom, int color) {
            rects.add(new int[] {left, top, right, bottom});
            colors.add(Integer.valueOf(color));
        }

        int[] lastFillRect(int color) {
            for (int i = colors.size() - 1; i >= 0; i--) {
                if (colors.get(i).intValue() == color) {
                    return rects.get(i);
                }
            }
            return null;
        }

        List<int[]> fillRectsOf(int color) {
            List<int[]> out = new ArrayList<int[]>();
            for (int i = 0; i < colors.size(); i++) {
                if (colors.get(i).intValue() == color) {
                    out.add(rects.get(i));
                }
            }
            return out;
        }

        List<String> trace() {
            List<String> out = new ArrayList<String>(rects.size());
            for (int i = 0; i < rects.size(); i++) {
                int[] r = rects.get(i);
                out.add(colors.get(i) + "@" + r[0] + "," + r[1] + "," + r[2] + "," + r[3]);
            }
            return out;
        }

        // ==================== 其余接口 no-op ====================

        @Override
        public void drawSurface(int left, int top, int right, int bottom, int fillColor,
                int borderColor, int cornerRadius) {
            // no-op
        }

        @Override
        public void drawBorder(int left, int top, int right, int bottom, int color) {
            // no-op
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {
            // no-op
        }

        @Override
        public void popClip() {
            // no-op
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            // no-op
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow, int fontSizePx) {
            // no-op
        }

        @Override
        public void pushGroupOpacity(int left, int top, int right, int bottom, float opacity) {
            // no-op
        }

        @Override
        public void popGroupOpacity() {
            // no-op
        }

        @Override
        public void pushTransform(float translateX, float translateY, float rotateDegrees,
                float scaleX, float scaleY, float originXRatio, float originYRatio,
                int left, int top, int right, int bottom) {
            // no-op
        }

        @Override
        public void popTransform() {
            // no-op
        }

        @Override
        public void pushTransformLayer(float translateX, float translateY, float rotateDegrees,
                float scaleX, float scaleY, float originXRatio, float originYRatio,
                int left, int top, int right, int bottom) {
            // no-op
        }

        @Override
        public void popTransformLayer() {
            // no-op
        }
    }
}
