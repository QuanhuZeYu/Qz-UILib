package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.text.SceneTextMode;

/**
 * P1-3 守卫：{@code PaintPlan} 的「片段 + 偏移」条目与旧「组装期逐命令平移」<b>逐像素等价</b>。
 *
 * <p>把平移从组装期推迟到 replay 是纯性能改动，但它是绘制契约的核心路径（N=3000 时每帧
 * 约 6000 个新 {@code PaintCommand}）。本类从三个面钉死等价性：</p>
 * <ol>
 *   <li><b>值面</b>：{@link PaintPlan#getCommands()} 物化结果与「逐命令 translatedBy」逐条 equals；</li>
 *   <li><b>回放面</b>：同一计划用新路径与参考路径 replay 到记录后端，调用序列逐条相同
 *       （真正决定像素的是这一段）；</li>
 *   <li><b>顺序/边界</b>：作用域边界命令豁免平移、条目顺序与片段内顺序保持、计划级包装不丢偏移。</li>
 * </ol>
 */
public class ScenePaintPlanFragmentOffsetEquivalenceTest {

    /** 值面：片段条目的物化结果 == 参考路径（逐命令 translatedBy）。 */
    @Test
    public void fragmentEntriesMaterializeToTranslatedCommands() {
        PaintFragment fragment = sampleFragment();
        PaintPlan plan = new PaintPlan()
                .addClipPush(0, 0, 100, 80, 0)
                .addFragment(fragment, 7, 9)
                .addClipPop();

        List<PaintCommand> expected = new ArrayList<PaintCommand>();
        expected.add(PaintCommand.clipPush(0, 0, 100, 80, 0));
        for (PaintCommand command : fragment.getCommands()) {
            expected.add(command.translatedBy(7, 9));
        }
        expected.add(PaintCommand.clipPop());

        Assert.assertEquals("命令条数必须按片段内命令数计入", expected.size(), plan.size());
        Assert.assertEquals("物化结果必须与逐命令平移逐条相等", expected, plan.getCommands());
    }

    /** 回放面：新路径与参考路径的后端调用序列逐条相同（像素级等价的机器证据）。 */
    @Test
    public void fragmentSlotReplayMatchesPreTranslatedReplay() {
        PaintFragment fragment = sampleFragment();
        int fragmentOffsetX = 13;
        int fragmentOffsetY = -7;
        int screenOffsetX = 5;
        int screenOffsetY = 3;

        PaintPlan byFragment = new PaintPlan().addFragment(fragment, fragmentOffsetX, fragmentOffsetY);
        PaintPlan byTranslation = new PaintPlan();
        for (PaintCommand command : fragment.getCommands()) {
            byTranslation.addCommand(command.translatedBy(fragmentOffsetX, fragmentOffsetY));
        }

        RecordingRenderBackend newPath = new RecordingRenderBackend();
        RecordingRenderBackend reference = new RecordingRenderBackend();
        ScenePaintReplayer replayer = new ScenePaintReplayer();
        replayer.replay(byFragment, newPath, screenOffsetX, screenOffsetY);
        replayer.replay(byTranslation, reference, screenOffsetX, screenOffsetY);

        Assert.assertEquals("回放调用序列必须逐条相同（含坐标）",
                reference.getCalls().toString(), newPath.getCalls().toString());
        Assert.assertFalse("样本必须真的产出绘制调用", newPath.getCalls().isEmpty());
    }

    /** 边界：片段内混入作用域边界命令时同样豁免平移（两条路径一致）。 */
    @Test
    public void scopeBoundaryCommandsInsideFragmentAreNotTranslated() {
        PaintFragment fragment = new PaintFragment(Arrays.asList(
                PaintCommand.background(0, 0, 20, 20, 0xFF112233),
                PaintCommand.pushOpacity(0, 0, 20, 20, 0.5F),
                PaintCommand.popOpacity()));
        int dx = 11;
        int dy = 12;

        PaintPlan plan = new PaintPlan().addFragment(fragment, dx, dy);
        List<PaintCommand> commands = plan.getCommands();

        Assert.assertEquals("边界命令不得被平移（坐标保持绝对）", 0, commands.get(1).getLeft());
        Assert.assertEquals("边界命令不得被平移（坐标保持绝对）", 0, commands.get(1).getTop());
        Assert.assertEquals("普通命令必须被平移", dx, commands.get(0).getLeft());
        Assert.assertEquals("普通命令必须被平移", dy, commands.get(0).getTop());

        RecordingRenderBackend newPath = new RecordingRenderBackend();
        RecordingRenderBackend reference = new RecordingRenderBackend();
        PaintPlan referencePlan = new PaintPlan();
        for (PaintCommand command : fragment.getCommands()) {
            referencePlan.addCommand(command.translatedBy(dx, dy));
        }
        ScenePaintReplayer replayer = new ScenePaintReplayer();
        replayer.replay(plan, newPath, 0, 0);
        replayer.replay(referencePlan, reference, 0, 0);
        Assert.assertEquals("含边界命令的片段回放必须一致",
                reference.getCalls().toString(), newPath.getCalls().toString());
    }

    /** 计划级包装（HUD windowClip 通路）：addPlan 保留片段偏移与整体顺序，且不物化命令。 */
    @Test
    public void addPlanPreservesFragmentOffsetsAndOrder() {
        PaintFragment first = sampleFragment();
        PaintFragment second = new PaintFragment(Arrays.asList(
                PaintCommand.background(0, 0, 4, 4, 0xFF445566)));
        PaintPlan inner = new PaintPlan().addFragment(first, 3, 4).addFragment(second, -2, 6);

        PaintPlan wrapped = new PaintPlan()
                .addClipPush(0, 0, 200, 200, 0)
                .addPlan(inner)
                .addClipPop();

        List<PaintCommand> expected = new ArrayList<PaintCommand>();
        expected.add(PaintCommand.clipPush(0, 0, 200, 200, 0));
        expected.addAll(inner.getCommands());
        expected.add(PaintCommand.clipPop());

        Assert.assertEquals("包装后的计划必须与内层命令序列一致", expected, wrapped.getCommands());
        Assert.assertEquals("命令计数必须整片累加", inner.size() + 2, wrapped.size());
        Assert.assertEquals("addPlan 只是复制条目引用，源计划内容与计数不得改变",
                first.size() + second.size(), inner.size());
    }

    /** 物化缓存：同一计划重复读取返回同一实例；任何写入后失效重建。 */
    @Test
    public void materializedViewIsCachedAndInvalidatedByWrites() {
        PaintPlan plan = new PaintPlan().addFragment(sampleFragment(), 1, 2);
        List<PaintCommand> first = plan.getCommands();
        Assert.assertSame("未写入时重复读取必须复用同一物化视图（零重复分配）", first, plan.getCommands());

        plan.addCommand(PaintCommand.background(0, 0, 1, 1, 0xFF000000));
        List<PaintCommand> afterWrite = plan.getCommands();
        Assert.assertNotSame("写入后缓存必须失效", first, afterWrite);
        Assert.assertEquals("失效后必须反映新条目", first.size() + 1, afterWrite.size());

        plan.clear();
        Assert.assertEquals(0, plan.size());
        Assert.assertTrue(plan.getCommands().isEmpty());
    }

    /** 样本片段：背景（圆角）+ 文本 + 图片位（覆盖三类主要命令）。 */
    private static PaintFragment sampleFragment() {
        return new PaintFragment(Arrays.asList(
                PaintCommand.background(0, 0, 30, 18, 0xFF203040, 4),
                PaintCommand.text(3, 4, "label", new TextStyle(0xFFFFFFFF, 12, SceneTextMode.UILIB_RAW)),
                PaintCommand.border(0, 0, 30, 18, 0xFF808080, 1, 4)));
    }
}
