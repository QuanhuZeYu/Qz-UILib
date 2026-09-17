package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.scene.testkit.SceneTestEnvironments;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * P1-2 守卫：帧管线的 layout 发布活性（layoutDoneSignal 只在真有几何变化时步进）。
 *
 * <p>拆 epoch 的收益必须可证：干净帧不得发布（否则 30 个订阅点每帧空转），
 * 有变化必须发布（否则订阅点漏刷）。本类对四个方向各给一条断言：</p>
 * <ul>
 *   <li>① 连续干净帧：信号值不变、观察者 0 次重跑（注册那次除外）；</li>
 *   <li>② 改一个叶：信号恰好步进 1 次、观察者恰好多跑 1 次；</li>
 *   <li>③ 挂载新子树：首批布局必然发布（新子树可读 LayoutBox）；</li>
 *   <li>④ 干净帧仍跑两批布局（批计数 +2，既有主机协议不变）；</li>
 *   <li>⑤ 仅 overlay 变化：主树纪元不动，但聚合发布仍必须步进（P2-1 聚合）。</li>
 * </ul>
 */
public class SceneFramePipelineLivenessTest {

    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private SceneFramePipeline pipeline;
    private SceneNode root;
    private RecordingRenderBackend backend;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = SceneTestEnvironments.runtime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        pipeline = new SceneFramePipeline(runtime, layoutEngine, new ScenePaintEngine(measurer),
                new ScenePaintReplayer(), measurer, null);
        root = new SceneNode();
        root.setFillParentHeight(true);
        backend = new RecordingRenderBackend();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** ① 连续 3 个干净帧：信号值不变、观察者不再重跑。 */
    @Test
    public void cleanFramesDoNotPublishLayoutDoneSignal() {
        run();
        final int[] runs = {0};
        runtime.bindComputed(() -> runtime.layoutDoneSignal().get(), value -> runs[0]++);
        Integer published = runtime.layoutDoneSignal().get();

        run();
        run();
        run();

        Assert.assertEquals("观察者只应在注册后的首批 flush 跑一次", 1, runs[0]);
        Assert.assertEquals("零几何变化的帧不得发布新的布局版本",
                published, runtime.layoutDoneSignal().get());
    }

    /** ② 改一个叶：信号恰好步进 1 次，观察者恰好多跑 1 次。 */
    @Test
    public void singleGeometryChangePublishesExactlyOnce() {
        SceneNode label = new SceneNode();
        label.setText("row");
        label.setPreferredHeight(10);
        root.appendChild(label);
        run();
        run();

        final int[] runs = {0};
        runtime.bindComputed(() -> runtime.layoutDoneSignal().get(), value -> runs[0]++);
        run();
        Assert.assertEquals(1, runs[0]);
        Integer published = runtime.layoutDoneSignal().get();

        label.setPreferredHeight(30);
        run();

        Assert.assertEquals("一次真实几何变化恰好发布一次",
                Integer.valueOf(published.intValue() + 1), runtime.layoutDoneSignal().get());
        Assert.assertEquals("观察者恰好多跑一次", 2, runs[0]);
    }

    /** ③ 挂载新子树：flush 内挂载的树在首批布局后必然发布（观察者读到 LayoutBox）。 */
    @Test
    public void mountedSubtreePublishesBeforeFirstIncomingPaint() {
        Signal<Boolean> visible = Signal.create(Boolean.FALSE);
        SceneNode[] incoming = new SceneNode[1];
        int[] observedHeight = {-1};
        runtime.show(root, visible, () -> {
            SceneNode node = new SceneNode();
            node.setPreferredHeight(40);
            incoming[0] = node;
            return node;
        });
        runtime.bindComputed(() -> {
            runtime.layoutDoneSignal().get();
            Object cached = incoming[0] == null ? null : incoming[0].getCachedLayout();
            return cached instanceof LayoutBox ? Integer.valueOf(((LayoutBox) cached).getHeight()) : null;
        }, value -> observedHeight[0] = value == null ? -1 : value.intValue());

        run();
        Assert.assertEquals("未挂载时无高度", -1, observedHeight[0]);

        visible.set(Boolean.TRUE);
        run();

        Assert.assertNotNull(incoming[0]);
        Assert.assertEquals("挂载后的首批布局必须发布（新子树可读 LayoutBox）", 40, observedHeight[0]);
    }

    /** ④ 干净帧仍跑两批布局：批计数 +2（既有宿主批次协议不变，只是内部不发布）。 */
    @Test
    public void cleanFrameStillRunsBothLayoutBatches() {
        run();
        int passBefore = layoutEngine.layoutEpoch();

        run();

        Assert.assertEquals("干净帧仍参与 pre-flush 与 post-flush 两批", passBefore + 2,
                layoutEngine.layoutEpoch());
    }

    /** ⑤ 仅 overlay 变化：主树纪元不动，聚合发布仍必须步进。 */
    @Test
    public void overlayOnlyChangeStillPublishesAggregatedEpoch() {
        Signal<Boolean> visible = Signal.create(Boolean.TRUE);
        SceneNode[] overlay = new SceneNode[1];
        runtime.portal(visible, () -> {
            SceneNode node = new SceneNode();
            node.setPreferredWidth(80);
            node.setPreferredHeight(40);
            overlay[0] = node;
            return node;
        });
        run();
        run();
        int mainChange = layoutEngine.layoutChangeEpoch();
        Integer published = runtime.layoutDoneSignal().get();

        overlay[0].setPreferredHeight(60);
        run();

        Assert.assertEquals("overlay 变化不得污染主树布局纪元（per-tree 隔离）",
                mainChange, layoutEngine.layoutChangeEpoch());
        Assert.assertNotEquals("仅 overlay 变化的帧必须经聚合发布（否则 overlay 内几何订阅漏刷）",
                published, runtime.layoutDoneSignal().get());
    }

    /** 驱动一帧（等价 host render 入口，宿主绝对原点恒 0）。 */
    private LayoutResult run() {
        return pipeline.run(root, 200, 120, backend, 0, 0, System.nanoTime());
    }
}
