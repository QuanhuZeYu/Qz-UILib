package club.heiqi.uilib.ui.scene.paint;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.layout.SceneParallelExecutor;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 阶段 2.5 paint 并行化确定性闸门测试。
 *
 * <p>核心验证：并行开/关在相同输入下产出完全一致的 {@link PaintResult}，
 * 包括 {@link PaintPlan} 的命令序列（逐条 {@link PaintCommand#equals}）与
 * {@code regeneratedFragmentCount}。这是 paint 并行化的命门闸门——
 * 命令序列 = DFS 前序 = z-order，appendAll 按 fork 顺序（= children 顺序）合并
 * 必须保证并行/串行结果全等。</p>
 *
 * <h3>测试策略</h3>
 * <ul>
 *   <li><b>核心 determinism</b>：两棵相同结构的大树（≥256 节点），各自先 layout 使
 *       cachedLayout/cachedSubtreeNodeCount 就绪，树A 串行首次 paint、树B 并行首次 paint，
 *       断言命令序列逐条 equals + regenerated 一致。首次 paint 全部 cachedPaint==null
 *       → 全部重生 fragment，验证 worker 各产独立 plan 片段 + appendAll 归并不丢不重不乱序。</li>
 *   <li><b>含 PUSH/POP 边界</b>：构造含 opacity/clip/transform 的子树，验证 PUSH/POP 边界
 *       整段落同一 worker、appendAll 按 children 顺序合并保持嵌套正确，命令序列并行/串行一致。</li>
 *   <li><b>小树全串行</b>：小树（&lt;256）即使 PARALLEL_ENABLED=true 也走串行路径，
 *       验证 paint 正常完成。</li>
 *   <li><b>单子树不 fork</b>：root 仅 1 个大子树（≥64）但 children.size()&lt;2 不 fork，
 *       验证 paint 正常完成。</li>
 * </ul>
 *
 * <p>装配复用 {@link ScenePaintEngineTest} 模式：{@link FixedTextMeasurer} +
 * {@link SceneLayoutEngine} + {@link ScenePaintEngine}。每个测试用独立 engine 实例。</p>
 *
 * <p><b>脏标记处理</b>：paint 会清脏标记并写 cachedPaint，同一棵树 paint 两次第二次全干净
 * （regenerated=0、复用 fragment）。故核心 determinism 用两棵独立树首次 paint 比较，
 * 确保两棵树的重算范围相同（全部 cachedPaint==null → 全部重生）。</p>
 */
public class ScenePaintParallelDeterminismTest {

    private final FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);

    @After
    public void resetParallelSwitch() {
        // 恢复默认关闭，避免影响其他测试
        SceneParallelExecutor.setParallelEnabled(false);
    }

    // ============================================================
    // 辅助：构造大树
    // ============================================================

    /**
     * 构造一棵 ≥256 节点的树：root + containerCount 个容器，每个容器含 leavesPerContainer 个文本叶。
     *
     * <p>每个节点设唯一 text 标签 + 背景色，使 paint 产出有内容的命令序列（TEXT + BACKGROUND），
     * 便于逐条比命令。两棵树用相同 rootLabel 使 text 标签结构相同。</p>
     *
     * @param containerCount    容器数
     * @param leavesPerContainer 每容器叶数
     * @param rootLabel         root 的 text 标签
     * @return 构造好的树根节点
     */
    private SceneNode buildLargeTree(int containerCount, int leavesPerContainer, String rootLabel) {
        SceneNode root = new SceneNode();
        root.setText(rootLabel);
        root.setBackgroundColor(0xFF112233);
        for (int i = 0; i < containerCount; i++) {
            SceneNode container = new SceneNode();
            container.setText(rootLabel + ".C" + i);
            container.setBackgroundColor(0xFF223344 + i);
            for (int j = 0; j < leavesPerContainer; j++) {
                SceneNode leaf = new SceneNode();
                leaf.setText(rootLabel + ".C" + i + ".L" + j);
                leaf.setBackgroundColor(0xFF334455 + j);
                container.appendChild(leaf);
            }
            root.appendChild(container);
        }
        return root;
    }

    /**
     * 构造一棵含 opacity/clip/transform 边界的大树，验证 PUSH/POP 边界并行/串行一致。
     *
     * <p>root + 4 个容器（每个容器设 opacity<1 触发 PUSH_OPACITY/POP_OPACITY，
     * 设 clipWindow 触发 CLIP_PUSH/CLIP_POP，设 transform 触发 PUSH_TRANSFORM/POP_TRANSFORM），
     * 每个容器含 80 个文本叶。总节点数 325 ≥ 256，每容器子树 81 ≥ 64，fork 触发。</p>
     *
     * @param rootLabel root 的 text 标签
     * @return 构造好的树根节点
     */
    private SceneNode buildLargeTreeWithBoundaries(String rootLabel) {
        SceneNode root = new SceneNode();
        root.setText(rootLabel);
        root.setBackgroundColor(0xFF112233);
        for (int i = 0; i < 4; i++) {
            SceneNode container = new SceneNode();
            container.setText(rootLabel + ".B" + i);
            container.setBackgroundColor(0xFF223344 + i);
            // opacity < 1 触发 PUSH_OPACITY/POP_OPACITY
            container.setOpacity(0.5f);
            // clipChildren 触发 CLIP_PUSH/CLIP_POP（isClipWindow = clipChildren || scrollable）
            container.setClipChildren(true);
            // transform 非恒等触发 PUSH_TRANSFORM_LAYER（transform+clip 叠加走 FBO）
            club.heiqi.uilib.ui.scene.node.Transform tf =
                    new club.heiqi.uilib.ui.scene.node.Transform(1.0f * i, 0.0f, 0.0f, 1.1f, 1.0f, 0.5f, 0.5f);
            container.setTransform(tf);
            for (int j = 0; j < 80; j++) {
                SceneNode leaf = new SceneNode();
                leaf.setText(rootLabel + ".B" + i + ".L" + j);
                leaf.setBackgroundColor(0xFF334455 + j);
                container.appendChild(leaf);
            }
            root.appendChild(container);
        }
        return root;
    }

    /**
     * 断言两份 PaintResult 完全一致：命令序列逐条 equals + regenerated 相等。
     *
     * @param message  断言失败时的前缀消息
     * @param serial   串行 paint 结果
     * @param parallel 并行 paint 结果
     */
    private void assertPaintResultEquals(String message, PaintResult serial, PaintResult parallel) {
        List<PaintCommand> serialCmds = serial.getPlan().getCommands();
        List<PaintCommand> parallelCmds = parallel.getPlan().getCommands();
        Assert.assertEquals(message + " 命令数一致", serialCmds.size(), parallelCmds.size());
        for (int i = 0; i < serialCmds.size(); i++) {
            Assert.assertEquals(message + " 第 " + i + " 条命令一致",
                    serialCmds.get(i), parallelCmds.get(i));
        }
        Assert.assertEquals(message + " regeneratedFragmentCount 一致",
                serial.getRegeneratedFragmentCount(),
                parallel.getRegeneratedFragmentCount());
    }

    // ============================================================
    // 测试 1：核心 determinism —— 并行开/关命令序列 + regenerated 完全一致
    // ============================================================

    /**
     * 两棵相同结构的大树（4 容器 × 80 叶 = 325 节点 ≥ 256），各自先 layout 使
     * cachedLayout/cachedSubtreeNodeCount 就绪，树A 串行首次 paint、树B 并行首次 paint。
     * 断言命令序列逐条 equals + regenerated 一致。
     *
     * <p>首次 paint 全部 cachedPaint==null → 全部重生 fragment（regenerated=325），
     * 验证 worker 各产独立 plan 片段 + appendAll 按 fork 顺序归并不丢不重不乱序。
     * 每个容器子树 81 ≥ 64（FORK_THRESHOLD），children.size()=4 ≥ 2，并行路径 fork 4 个子任务。</p>
     */
    @Test
    public void parallelOnOffProducesSameCommandSequence() {
        // 4 容器 × 80 叶 = 325 节点 ≥ 256（WHOLE_TREE_THRESHOLD）
        // 每个容器 count=81 ≥ 64（FORK_THRESHOLD），fork 触发
        SceneNode treeA = buildLargeTree(4, 80, "T");
        SceneNode treeB = buildLargeTree(4, 80, "T");
        Constraints constraints = new Constraints(200);

        // 两棵树各自先 layout（使 cachedLayout/cachedSubtreeNodeCount 就绪）
        SceneLayoutEngine layoutA = new SceneLayoutEngine(measurer);
        SceneLayoutEngine layoutB = new SceneLayoutEngine(measurer);
        layoutA.layout(treeA, constraints);
        layoutB.layout(treeB, constraints);

        // 树A 串行首次 paint
        SceneParallelExecutor.setParallelEnabled(false);
        ScenePaintEngine paintA = new ScenePaintEngine(measurer);
        PaintResult serialResult = paintA.paint(treeA);

        // 树B 并行首次 paint
        SceneParallelExecutor.setParallelEnabled(true);
        ScenePaintEngine paintB = new ScenePaintEngine(measurer);
        PaintResult parallelResult = paintB.paint(treeB);

        // 断言命令序列逐条一致 + regenerated 一致
        assertPaintResultEquals("核心 determinism", serialResult, parallelResult);

        // 期望全部 325 节点都重生 fragment（首次 paint 全部 cachedPaint==null）
        Assert.assertEquals("首次 paint regenerated=325",
                325, serialResult.getRegeneratedFragmentCount());
    }

    // ============================================================
    // 测试 2：含 PUSH/POP 边界 —— opacity/clip/transform 并行/串行命令序列一致
    // ============================================================

    /**
     * 两棵相同结构的含边界大树（root + 4 个 opacity+clip+transform 容器 × 80 叶 = 325 节点），
     * 各自先 layout，树A 串行首次 paint、树B 并行首次 paint。断言命令序列逐条一致。
     *
     * <p>验证 PUSH_OPACITY/POP_OPACITY、CLIP_PUSH/CLIP_POP、PUSH_TRANSFORM_LAYER/POP_TRANSFORM_LAYER
     * 边界命令在并行路径下嵌套正确、顺序与串行一致。fork 粒度=整棵子树（含容器根的 PUSH/POP），
     * PUSH/POP 边界整段落同一 worker，appendAll 按 children 顺序合并保持 PUSH→子片段→POP 嵌套。</p>
     */
    @Test
    public void parallelOnOffProducesSameBoundaryCommands() {
        SceneNode treeA = buildLargeTreeWithBoundaries("B");
        SceneNode treeB = buildLargeTreeWithBoundaries("B");
        Constraints constraints = new Constraints(200);

        SceneLayoutEngine layoutA = new SceneLayoutEngine(measurer);
        SceneLayoutEngine layoutB = new SceneLayoutEngine(measurer);
        layoutA.layout(treeA, constraints);
        layoutB.layout(treeB, constraints);

        SceneParallelExecutor.setParallelEnabled(false);
        ScenePaintEngine paintA = new ScenePaintEngine(measurer);
        PaintResult serialResult = paintA.paint(treeA);

        SceneParallelExecutor.setParallelEnabled(true);
        ScenePaintEngine paintB = new ScenePaintEngine(measurer);
        PaintResult parallelResult = paintB.paint(treeB);

        assertPaintResultEquals("PUSH/POP 边界 determinism", serialResult, parallelResult);

        // 验证命令序列确实含边界命令（非空且含 PUSH_OPACITY 等）
        List<PaintCommand> cmds = serialResult.getPlan().getCommands();
        Assert.assertTrue("命令序列非空", cmds.size() > 0);
        boolean hasPushOpacity = false;
        boolean hasClipPush = false;
        boolean hasPushTransformLayer = false;
        for (PaintCommand cmd : cmds) {
            PaintCommandType type = cmd.getType();
            if (type == PaintCommandType.PUSH_OPACITY) hasPushOpacity = true;
            if (type == PaintCommandType.CLIP_PUSH) hasClipPush = true;
            if (type == PaintCommandType.PUSH_TRANSFORM_LAYER) hasPushTransformLayer = true;
        }
        Assert.assertTrue("含 PUSH_OPACITY 边界命令", hasPushOpacity);
        Assert.assertTrue("含 CLIP_PUSH 边界命令", hasClipPush);
        Assert.assertTrue("含 PUSH_TRANSFORM_LAYER 边界命令", hasPushTransformLayer);
    }

    // ============================================================
    // 测试 3：小树全串行（< 256 即使开并行也走串行路径）
    // ============================================================

    /**
     * 小树（10 节点 < 256），PARALLEL_ENABLED=true，验证 paint 正常完成且结果正确。
     * 小树不达 WHOLE_TREE_THRESHOLD，走串行路径（现状不变）。
     */
    @Test
    public void smallTreeAlwaysSerialEvenWhenParallelEnabled() {
        SceneNode root = new SceneNode();
        root.setText("R");
        root.setBackgroundColor(0xFF112233);
        for (int i = 0; i < 9; i++) {
            SceneNode leaf = new SceneNode();
            leaf.setText("R.L" + i);
            leaf.setBackgroundColor(0xFF334455 + i);
            root.appendChild(leaf);
        }
        // root count=10 < 256，即使开并行也走串行路径
        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
        layoutEngine.layout(root, new Constraints(200));

        SceneParallelExecutor.setParallelEnabled(true);
        ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);
        PaintResult result = paintEngine.paint(root);

        // 首次 paint 全部 cachedPaint==null，10 节点都重生 fragment
        Assert.assertEquals("小树 regenerated=10",
                10, result.getRegeneratedFragmentCount());
        // 验证 paint 正常完成（命令序列非空，含 root 的 BACKGROUND）
        Assert.assertTrue("小树命令序列非空",
                result.getPlan().getCommands().size() > 0);
    }

    // ============================================================
    // 测试 4：单子树不 fork（children.size() < 2）
    // ============================================================

    /**
     * root 仅 1 个大子树（≥64 节点），但 children.size()=1 < 2 不 fork。
     * 验证 paint 正常完成且结果正确。
     *
     * <p>构造：root + 1 个大容器（含 300 叶），root count=302 ≥ 256 走并行路径，
     * 但 children.size()=1 < 2，大容器不 fork，仍串行递归。</p>
     */
    @Test
    public void singleChildSubtreeNotForked() {
        // root + 1 容器 + 300 叶 = 302 节点 ≥ 256（走并行路径）
        // 但 children.size()=1 < 2，大容器不 fork
        SceneNode root = new SceneNode();
        root.setText("R");
        root.setBackgroundColor(0xFF112233);
        SceneNode bigChild = new SceneNode();
        bigChild.setText("R.BC");
        bigChild.setBackgroundColor(0xFF223344);
        for (int j = 0; j < 300; j++) {
            SceneNode leaf = new SceneNode();
            leaf.setText("R.BC.L" + j);
            leaf.setBackgroundColor(0xFF334455 + j);
            bigChild.appendChild(leaf);
        }
        root.appendChild(bigChild);

        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
        layoutEngine.layout(root, new Constraints(200));

        SceneParallelExecutor.setParallelEnabled(true);
        ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);
        PaintResult result = paintEngine.paint(root);

        // 首次 paint 全部 cachedPaint==null，302 节点都重生 fragment
        Assert.assertEquals("单子树 regenerated=302",
                302, result.getRegeneratedFragmentCount());
        Assert.assertTrue("单子树命令序列非空",
                result.getPlan().getCommands().size() > 0);
    }

    // ============================================================
    // 测试 5：并行路径下 paint 命令序列与串行一致（同一棵树标脏重算）
    // ============================================================

    /**
     * 同一棵树，串行首次 paint 后记录命令序列，标脏所有节点（重置到全脏状态），
     * 并行重算，断言命令序列与串行一致。
     *
     * <p>与测试 1 互补：测试 1 用两棵独立树验证首次 paint，本测试用同一棵树验证
     * 「干净后重新标脏」场景下的并行/串行一致性（cachedPaint 已存在但 selfPaintDirty=true 重生）。</p>
     */
    @Test
    public void parallelPaintProducesSameCommandsAsSerialAfterRemarkDirty() {
        SceneNode tree = buildLargeTree(4, 80, "T");
        Constraints constraints = new Constraints(200);

        SceneLayoutEngine layoutEngine = new SceneLayoutEngine(measurer);
        layoutEngine.layout(tree, constraints);

        // 串行首次 paint，记录命令序列
        SceneParallelExecutor.setParallelEnabled(false);
        ScenePaintEngine paintEngine = new ScenePaintEngine(measurer);
        PaintResult serialFirst = paintEngine.paint(tree);
        List<PaintCommand> serialFirstCmds = new ArrayList<>(serialFirst.getPlan().getCommands());

        // 标脏所有节点（重置到全脏状态，使下次 paint 重生全部 fragment）
        markAllSelfPaintDirty(tree);

        // 并行重算
        SceneParallelExecutor.setParallelEnabled(true);
        PaintResult parallelRe = paintEngine.paint(tree);

        // 断言命令序列与串行首次一致（重算结果相同）
        List<PaintCommand> parallelCmds = parallelRe.getPlan().getCommands();
        Assert.assertEquals("重算后命令数一致", serialFirstCmds.size(), parallelCmds.size());
        for (int i = 0; i < serialFirstCmds.size(); i++) {
            Assert.assertEquals("重算后第 " + i + " 条命令一致",
                    serialFirstCmds.get(i), parallelCmds.get(i));
        }
        // 重算后 regenerated 应等于全部节点数（全部 selfPaintDirty=true 重生）
        Assert.assertEquals("重算后 regenerated=325",
                325, parallelRe.getRegeneratedFragmentCount());
    }

    /**
     * 递归标脏整棵树（每个节点调 markSelfPaint），使下次 paint 重生全部 fragment。
     *
     * @param node 子树根
     */
    private void markAllSelfPaintDirty(SceneNode node) {
        node.markSelfPaint();
        for (SceneNode child : node.__getChildren()) {
            markAllSelfPaintDirty(child);
        }
    }
}
