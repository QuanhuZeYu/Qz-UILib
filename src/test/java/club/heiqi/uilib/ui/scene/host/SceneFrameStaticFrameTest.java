package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.scene.testkit.SceneTestEnvironments;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintResult;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.ScenePaintReplayer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * P1-4 守卫：静止帧的入口账与计划稳定性（L0 快路径 + 将来 L4 复用的前置契约）。
 *
 * <p>L0（本阶段实现的静止帧优化）：全树干净时 {@code layout()} 走 O(1) 快路径——不建 3 个探针集合、
 * 不建 {@link LayoutResult}、不下潜，返回共享零变化结果；批计数照旧自增、变更纪元不动。</p>
 *
 * <p>另钉死两条「做 plan 跨帧复用时最容易踩坏」的契约：宿主绝对原点变化必须整体重定位；
 * {@code windowClip} 变化必须换裁剪盒（不得沿用旧计划）。</p>
 */
public class SceneFrameStaticFrameTest {

    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    private SceneFramePipeline pipeline;
    private SceneNode root;
    private SceneNode label;
    private RecordingRenderBackend backend;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = SceneTestEnvironments.runtime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        pipeline = new SceneFramePipeline(runtime, layoutEngine, paintEngine,
                new ScenePaintReplayer(), measurer, null);
        root = new SceneNode();
        root.setFillParentHeight(true);
        root.setBackgroundColor(0xFF101010);
        label = new SceneNode();
        label.setText("row");
        label.setBackgroundColor(0xFF203040);
        label.setPreferredHeight(12);
        root.appendChild(label);
        backend = new RecordingRenderBackend();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** ① 干净帧：零重算、零被迫重算、变更纪元不动，且复用共享零变化结果（L0 零分配证据）。 */
    @Test
    public void cleanFrameTakesZeroChangeFastPath() {
        run();
        LayoutResult firstClean = run();
        int changeEpoch = layoutEngine.layoutChangeEpoch();

        LayoutResult secondClean = run();

        Assert.assertEquals("干净帧不得有任何重算", 0, firstClean.getRelayoutCount());
        Assert.assertTrue("干净帧不得有约束被迫重算节点",
                firstClean.getConstraintRelayoutedNodes().isEmpty());
        Assert.assertSame("L0 快路径必须复用共享零变化结果（不新建探针集合与 LayoutResult）",
                firstClean, secondClean);
        Assert.assertEquals("干净帧不得步进变更纪元", changeEpoch, layoutEngine.layoutChangeEpoch());
    }

    /**
     * ② 干净帧仍跑两批（宿主批次协议不变）；入口账为零由 L0 共享结果与登记表探针共同背书
     * （后者在 {@code SceneLayoutEngineEntryFastPathTest} 里以包内可见性断言）。
     */
    @Test
    public void cleanFrameKeepsTwoBatches() {
        run();
        int pass = layoutEngine.layoutEpoch();

        run();

        Assert.assertEquals("干净帧仍参与 pre-flush 与 post-flush 两批", pass + 2,
                layoutEngine.layoutEpoch());
    }

    /** ③ 静止帧回放序列逐条稳定（计划与命令坐标值等价）。 */
    @Test
    public void cleanFrameReplaySequenceIsStable() {
        run();
        run();
        String first = backend.getCalls().toString();

        run();

        Assert.assertEquals("静止帧的回放调用序列必须逐条稳定", first, backend.getCalls().toString());
        Assert.assertFalse("计划必须有内容", first.isEmpty());
    }

    /** ④ 宿主绝对原点变化：回放坐标必须整体平移（不得沿用上一帧位置）。 */
    @Test
    public void hostOriginChangeRepositionsReplay() {
        run(0, 0);
        String atOrigin = backend.getCalls().toString();

        run(10, 6);

        Assert.assertNotEquals("宿主绝对原点变化必须整体平移回放坐标",
                atOrigin, backend.getCalls().toString());
    }

    /** ⑤ windowClip 变化：包装计划的裁剪盒必须换成新矩形（每次都用当前 clip 重建）。 */
    @Test
    public void windowClipChangeRewritesClipWrapper() {
        run();
        run(new AnchorRect(0, 0, 100, 80));
        Assert.assertEquals("包装裁剪盒必须取当前 windowClip 的右边界", 100, firstClipRight());

        run(new AnchorRect(3, 4, 55, 40));
        Assert.assertEquals("windowClip 变化必须重写包装裁剪盒", 58, firstClipRight());
    }

    /** ⑥ 改一叶绘制属性：片段重生成、命令集变化（静止帧优化的反向守卫）。 */
    @Test
    public void dirtyLeafRegeneratesFragmentAndChangesPlan() {
        Constraints constraints = new Constraints(200, 120);
        layoutEngine.layout(root, constraints);
        paintEngine.paint(root);
        String before = paintEngine.paint(root).getPlan().getCommands().toString();

        label.setBackgroundColor(0xFF556677);
        layoutEngine.layout(root, constraints);
        PaintResult result = paintEngine.paint(root);

        Assert.assertTrue("绘制属性变化必须重生成片段（其余片段仍复用）",
                result.getRegeneratedFragmentCount() > 0);
        Assert.assertNotEquals("绘制属性变化必须改变计划",
                before, result.getPlan().getCommands().toString());
    }

    /** 驱动一帧（默认原点 0,0 且无窗口裁剪）。 */
    private LayoutResult run() {
        return run(0, 0);
    }

    /** 驱动一帧；每次清空记录后端，使断言看到的是「本帧」的回放序列。 */
    private LayoutResult run(int absX, int absY) {
        backend.clear();
        return pipeline.run(root, 200, 120, backend, absX, absY, System.nanoTime());
    }

    /** 驱动一帧（带窗口裁剪盒）；每次清空记录后端。 */
    private LayoutResult run(AnchorRect windowClip) {
        backend.clear();
        return pipeline.run(root, 200, 120, backend, 0, 0, System.nanoTime(), windowClip);
    }

    /** 最近一次回放里第一个 pushClip 调用的右边界（窗口裁剪盒的机器证据）。 */
    private int firstClipRight() {
        List<RecordingRenderBackend.RenderCall> calls = backend.getCalls();
        for (int i = 0; i < calls.size(); i++) {
            if ("pushClip".equals(calls.get(i).methodName())) {
                return calls.get(i).getInt(2);
            }
        }
        return Integer.MIN_VALUE;
    }
}
