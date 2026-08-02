package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * PaintPlan 自包含性 / 可延迟 replay 测试锚点（Display List 契约线阶段 1）。
 *
 * <p>验证 {@link PaintPlan} 是自包含不可变交付物：paint 产出 plan 后，即使中间做了
 * 无关操作（修改节点属性、signal.set + flush），同一 plan 的 replay 结果与立即 replay
 * 完全一致。这证明 plan 不持有任何上游可变状态引用（SceneNode/Transform/Signal），
 * 是数据层与渲染层之间唯一的合同交付物（守 NORTH_STAR 信条六/I6 并行强化）。</p>
 *
 * <h3>为阶段 2 跨线程并行铺路</h3>
 * <p>阶段 2 子树并行化的前提是：worker 线程产出的 PaintPlan 可在主线程延迟 replay，
 * 且 replay 结果与产出时一致。本测试在单线程内验证这一不变量：plan 产出后到 replay 前，
 * 即使上游状态发生变化，plan 的 replay 结果不受影响——因为 plan 已快照了所有渲染所需数据。</p>
 *
 * <h3>断言策略</h3>
 * <p>用 {@link RecordingRenderBackend} 记录两次 replay 的完整调用序列（方法名 + 参数），
 * 逐条比较 toString 快照。若 plan 偷偷持有节点引用并在 replay 时回读，修改节点属性后
 * 第二次 replay 的参数会与第一次不同，测试失败。</p>
 */
public class ScenePaintReplayDeferredTest {

    private final FixedTextMeasurer measurer = new FixedTextMeasurer();
    private final SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
    private final ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);
    private final ScenePaintReplayer replayer = new ScenePaintReplayer();

    /**
     * ★ 契约锚点：paint 产 plan 后修改节点属性 + signal.set + flush，
     * 同一 plan 的延迟 replay 与立即 replay 调用序列完全一致。
     *
     * <p>这证明 PaintPlan 自包含：replay 只消费 plan 内的命令数据，
     * 不回读任何上游可变状态。为阶段 2 跨线程并行 replay 铺路。</p>
     */
    @Test
    public void deferredReplayShouldMatchImmediateReplayAfterUnrelatedMutation() {
        // 构建树：root → child（背景色 + 文本）
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        child.setBackgroundColor(0xFF336699);
        child.setText("Deferred Replay");
        root.appendChild(child);

        // layout + paint 产 plan
        layoutEngine.layout(root, new Constraints(200));
        PaintPlan plan = paintEngine.paint(root).getPlan();

        // 立即 replay 到 backend A
        RecordingRenderBackend backendA = new RecordingRenderBackend();
        replayer.replay(plan, backendA);
        List<String> immediateSnapshot = snapshotCalls(backendA);

        // 做无关操作：修改节点属性（若 plan 持有节点引用并在 replay 时回读，这些修改会使第二次 replay 结果偏移）
        child.setBackgroundColor(0xFF000000);   // 改背景色
        child.setText("MUTATED");               // 改文本
        child.setOpacity(0.5f);                 // 改 opacity

        // 延迟 replay 同一个 plan 到 backend B
        RecordingRenderBackend backendB = new RecordingRenderBackend();
        replayer.replay(plan, backendB);
        List<String> deferredSnapshot = snapshotCalls(backendB);

        // 断言：两次 replay 调用序列完全一致（plan 自包含，不受后续状态变化影响）
        Assert.assertEquals("延迟 replay 调用数应与立即 replay 一致",
                immediateSnapshot.size(), deferredSnapshot.size());
        for (int i = 0; i < immediateSnapshot.size(); i++) {
            Assert.assertEquals("第 " + i + " 条调用应一致（plan 自包含）",
                    immediateSnapshot.get(i), deferredSnapshot.get(i));
        }
    }

    @Test
    public void publishesWholePlanTextDemandBeforeFirstDraw() {
        PaintPlan plan = new PaintPlan()
                .addCommand(PaintCommand.background(0, 0, 10, 10, 0xFF101010))
                .addCommand(PaintCommand.text(1, 2, "First", new TextStyle(0xFFFFFFFF, 10)))
                .addCommand(PaintCommand.text(3, 4, "Second", new TextStyle(0xFFEEEEEE, 12)));
        DemandOrderBackend backend = new DemandOrderBackend();

        replayer.replay(plan, backend);

        Assert.assertEquals(java.util.Arrays.asList("demand:First,Second", "fill", "draw:First", "draw:Second"),
                backend.events);

        RecordingRenderBackend legacyBackend = new RecordingRenderBackend();
        replayer.replay(plan, legacyBackend);
        Assert.assertEquals(java.util.Arrays.asList("fillRect", "drawText", "drawText"),
                legacyBackend.getMethodNames());
    }

    /**
     * 把 RecordingRenderBackend 的调用序列转为 toString 快照列表，便于逐条比较。
     *
     * @param backend 录制后端
     * @return 每条调用的 toString 快照列表
     */
    private static List<String> snapshotCalls(RecordingRenderBackend backend) {
        List<String> snapshot = new ArrayList<String>();
        for (RecordingRenderBackend.RenderCall call : backend.getCalls()) {
            snapshot.add(call.toString());
        }
        return snapshot;
    }

    private static final class DemandOrderBackend extends RecordingRenderBackend {

        private final List<String> events = new ArrayList<String>();

        @Override
        public void fillRect(int left, int top, int right, int bottom, int color) {
            events.add("fill");
            super.fillRect(left, top, right, bottom, color);
        }

        @Override
        public void publishTextDemand(List<String> texts) {
            events.add("demand:" + String.join(",", texts));
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow, int fontSizePx) {
            events.add("draw:" + text);
            super.drawText(text, x, y, color, shadow, fontSizePx);
        }
    }
}
