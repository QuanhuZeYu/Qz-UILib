package club.heiqi.uilib.ui.diagnostic;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextMeasureServiceSceneAdapter;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;

/**
 * 采样开关<b>不得改变渲染与输入语义</b>的守卫测试。
 *
 * <p>判据：同一棵场景树在 {@code debug=false} 与 {@code debug=true} 各跑一帧，
 * 两帧的①阶段序列、②回放调用序列（方法名 + 参数）必须逐位相同。采样开启时额外断言
 * 帧级计数点确实产出，关闭时额外断言没有建会话——把"零成本"与"不改语义"两侧都钉住。</p>
 */
public class UiSamplingRenderSemanticsTest {

    private static final String SCREEN = "semantics-guard";

    private boolean originalUseDebug;

    @Before
    public void setUp() {
        originalUseDebug = Config.useDebug;
        UiPerformanceMonitor.getInstance().resetHistory(SCREEN);
        UiPerformanceMonitor.getInstance().finishFrame();
    }

    @After
    public void tearDown() {
        UiPerformanceMonitor.getInstance().finishFrame();
        UiPerformanceMonitor.getInstance().resetHistory(SCREEN);
        Config.useDebug = originalUseDebug;
    }

    /** 采样开启与关闭：阶段序列、回放调用序列、根布局盒必须完全一致。 */
    @Test
    public void samplingDoesNotChangeFrameSemantics() {
        Frame off = runFrame(false);
        Frame on = runFrame(true);

        Assert.assertEquals("阶段序列必须与采样开关无关", off.trace, on.trace);
        Assert.assertEquals("回放调用序列必须逐位一致（采样不得改变渲染语义）",
                off.renderCalls, on.renderCalls);
        Assert.assertEquals("根布局盒宽度必须一致", off.rootWidth, on.rootWidth);
        Assert.assertEquals("根布局盒高度必须一致", off.rootHeight, on.rootHeight);

        Assert.assertTrue("采样关闭时不得建会话", off.statsIsEmpty);
        Assert.assertEquals("采样开启时必须结算一帧", 1, on.stats.getSampledFrameCount());
        Assert.assertTrue("采样开启时必须产出帧级命令数计数",
                on.stats.getCounterSummary().contains(UiPerfMarkers.COUNTER_FRAME_COMMANDS));
        Assert.assertTrue("采样开启时必须产出帧级节点数计数",
                on.stats.getCounterSummary().contains(UiPerfMarkers.COUNTER_FRAME_NODES));
        Assert.assertTrue("采样开启时必须产出阶段耗时（frame.* 常量名）",
                on.stats.getPhaseSummary().contains("frame."));
    }

    private static Frame runFrame(boolean useDebug) {
        Config.useDebug = useDebug;
        SceneTextMeasurer measurer = new TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance());
        SceneRuntime runtime = new SceneRuntime(measurer);
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
        ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);
        SceneFramePipeline pipeline = new SceneFramePipeline(runtime, layoutEngine, paintEngine,
                new ScenePaintReplayer(), measurer, null);
        pipeline.setTraceEnabled(true);

        SceneNode root = new SceneNode();
        root.setFillParentHeight(true);
        root.setFillParentWidth(true);
        SceneNode label = new SceneNode();
        label.setText("采样语义守卫");
        label.setPreferredHeight(20);
        root.appendChild(label);

        RecordingRenderBackend backend = new RecordingRenderBackend();
        UiPerformanceMonitor monitor = UiPerformanceMonitor.getInstance();
        monitor.beginFrame(SCREEN, 320, 240, 640, 480);
        try {
            pipeline.run(root, 320, 240, backend, 0, 0, 1_000_000L);
        } finally {
            monitor.finishFrame();
        }

        Frame frame = new Frame();
        frame.trace = pipeline.lastTrace().toString();
        frame.renderCalls = renderCalls(backend.getCalls());
        Object cached = root.getCachedLayout();
        if (cached instanceof LayoutBox) {
            frame.rootWidth = ((LayoutBox) cached).getWidth();
            frame.rootHeight = ((LayoutBox) cached).getHeight();
        }
        UiRuntimeStats stats = monitor.getRuntimeStats();
        frame.stats = stats;
        frame.statsIsEmpty = stats == UiRuntimeStats.empty();
        return frame;
    }

    private static String renderCalls(List<RecordingRenderBackend.RenderCall> calls) {
        StringBuilder builder = new StringBuilder();
        for (RecordingRenderBackend.RenderCall call : calls) {
            builder.append(call.toString()).append('\n');
        }
        return builder.toString();
    }

    private static final class Frame {

        private String trace;
        private String renderCalls;
        private int rootWidth;
        private int rootHeight;
        private UiRuntimeStats stats;
        private boolean statsIsEmpty;
    }
}
