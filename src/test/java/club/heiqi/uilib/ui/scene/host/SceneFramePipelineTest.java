package club.heiqi.uilib.ui.scene.host;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextMeasureServiceSceneAdapter;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;

/**
 * 帧管线序列容器契约测试（阶段 1：行为对拍）。
 *
 * <p>锚定三件事：① 一帧 11 阶段顺序契约（与旧 render 16 步 1:1）；② run 产出主树
 * LayoutResult 且 settle 内完成 layoutDoneSignal 的 epoch 桥接；③ flush 挂载的新子树
 * 在 settle 后布局收敛（row-rekey 类「收敛帧」的管线级锚）。</p>
 */
public class SceneFramePipelineTest {

    /** 干净帧的顺序契约：与旧 {@code AbstractSceneHostWidget.render} 的 16 步逐位对应。 */
    private static final List<SceneFramePipeline.FramePhase> EXPECTED_ORDER = Arrays.asList(
            SceneFramePipeline.FramePhase.INPUT_DRAIN,
            SceneFramePipeline.FramePhase.LAYOUT_PRE_ROUTE,
            SceneFramePipeline.FramePhase.ROUTE,
            SceneFramePipeline.FramePhase.FLUSH,
            SceneFramePipeline.FramePhase.MOTION_SAMPLE,
            SceneFramePipeline.FramePhase.LAYOUT_POST_FLUSH,
            SceneFramePipeline.FramePhase.SETTLE,
            SceneFramePipeline.FramePhase.HOVER_RECONCILE,
            SceneFramePipeline.FramePhase.DISMISS_INVISIBLE,
            SceneFramePipeline.FramePhase.PAINT,
            SceneFramePipeline.FramePhase.REPLAY);

    private static SceneTextMeasurer measurer() {
        return new TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance());
    }

    private static Fixture fixture() {
        return new Fixture();
    }

    private static final class Fixture {

        final SceneRuntime runtime;
        final SceneLayoutEngine layoutEngine;
        final ScenePaintEngine paintEngine;
        final SceneFramePipeline pipeline;
        final SceneNode root;

        Fixture() {
            SceneTextMeasurer m = measurer();
            this.runtime = new SceneRuntime(m);
            this.layoutEngine = new SceneLayoutEngine(m);
            this.paintEngine = new ScenePaintEngine(m);
            this.pipeline = new SceneFramePipeline(runtime, layoutEngine, paintEngine,
                    new ScenePaintReplayer(), m, null);
            this.root = new SceneNode();
            root.setFillParentHeight(true);
        }

        LayoutResult run(int w, int h) {
            return pipeline.run(root, w, h, new NoopBackend(), 0, 0, System.nanoTime());
        }
    }

    /** 顺序契约：一帧 11 阶段依次进入，与旧 render 16 步对拍。 */
    @Test
    public void traceOrderMatchesLegacyRenderSequence() {
        Fixture fx = fixture();
        fx.pipeline.setTraceEnabled(true);
        fx.run(200, 120);
        Assert.assertEquals("阶段序列必须与旧 render 1:1 对拍",
                EXPECTED_ORDER, fx.pipeline.lastTrace());
    }

    /** run 必须产出主树 LayoutResult，且 settle 内完成 layoutDoneSignal 的 epoch 桥接。 */
    @Test
    public void runProducesLayoutResultAndBridgesLayoutEpoch() {
        Fixture fx = fixture();
        LayoutResult result = fx.run(200, 120);
        Assert.assertNotNull("run 后必须有主树 LayoutResult", result);
        Assert.assertEquals("layoutDoneSignal 必须桥接到最终 layout epoch",
                Integer.valueOf(fx.layoutEngine.layoutEpoch()),
                fx.runtime.layoutDoneSignal().get());
    }

    /** flush 挂载的新子树在 settle 后布局收敛（row-rekey 类收敛帧的管线级锚）。 */
    @Test
    public void settleConvergesSubtreeMountedDuringFlush() {
        Fixture fx = fixture();
        Signal<Boolean> visible = Signal.create(Boolean.TRUE);
        SceneNode[] content = new SceneNode[1];
        fx.runtime.show(fx.root, visible, () -> {
            SceneNode node = new SceneNode();
            node.setPreferredHeight(40);
            content[0] = node;
            return node;
        });

        fx.run(200, 120);

        Assert.assertNotNull("show 内容应在首帧 run 内挂载", content[0]);
        Object cached = content[0].getCachedLayout();
        Assert.assertTrue("挂载子树必须布局收敛", cached instanceof LayoutBox);
        Assert.assertEquals("收敛后高度为声明值", 40, ((LayoutBox) cached).getHeight());
        Assert.assertFalse("收敛后主树无自身布局脏", fx.root.__isSelfLayoutDirty());
        Assert.assertFalse("收敛后主树无后代布局脏", fx.root.__isDescendantLayoutDirty());
    }

    /** trace 默认关闭：零记录零分配。 */
    @Test
    public void traceDisabledByDefaultRecordsNothing() {
        Fixture fx = fixture();
        Assert.assertFalse("trace 默认关闭", fx.pipeline.isTraceEnabled());
        fx.run(200, 120);
        Assert.assertTrue("关闭时 trace 为空", fx.pipeline.lastTrace().isEmpty());
    }

    /** 平台无关渲染出口 no-op 实现：只消费 PaintPlan，不触碰任何 GL 状态。 */
    private static final class NoopBackend implements UiRenderBackend {

        @Override
        public void fillRect(int left, int top, int right, int bottom, int color) {
            // no-op
        }

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
