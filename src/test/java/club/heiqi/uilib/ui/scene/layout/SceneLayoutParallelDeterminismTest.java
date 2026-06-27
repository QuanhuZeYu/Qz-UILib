package club.heiqi.uilib.ui.scene.layout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 阶段 2.4 layout 并行化确定性闸门测试。
 *
 * <p>核心验证：并行开/关在相同输入下产出完全一致的 {@link LayoutResult}，
 * 包括 {@code relayoutCount}、{@code relayoutedNodes} 内容、
 * {@code constraintRelayoutedNodes} 的 {@link LinkedHashSet} 迭代顺序。</p>
 *
 * <h3>测试策略</h3>
 * <ul>
 *   <li><b>核心 determinism</b>：两棵相同结构的大树（≥256 节点），树A 串行首次 layout、
 *       树B 并行首次 layout，断言 count + relayoutedNodes 文本标签集合完全一致。
 *       首次 layout 全部 selfDirty，验证 worker 探针片段归并不丢不重。</li>
 *   <li><b>LinkedHashSet 顺序</b>：两棵相同结构的大树含 fillParentHeight 子容器分散，
 *       首次 layout 使干净后改约束高度触发 constraintRelayouted，断言两棵树
 *       constraintRelayoutedNodes 的迭代顺序（按文本标签序列）完全一致。
 *       验证 join 点按 fork 顺序（= children 顺序）归并保证确定性。</li>
 *   <li><b>小树全串行</b>：小树（&lt;256）即使 PARALLEL_ENABLED=true 也走串行路径，
 *       验证 layout 正常完成。</li>
 *   <li><b>单子树不 fork</b>：root 仅 1 个大子树（≥64）但 children.size()&lt;2 不 fork，
 *       验证 layout 正常完成。</li>
 * </ul>
 *
 * <p>装配复用 {@link SceneLayoutEngineTest} 模式：{@link FixedTextMeasurer} +
 * {@link SceneLayoutEngine} + {@link Constraints}。每个测试用独立 engine 实例，
 * 避免 {@code lastRootConstraints} 跨测试污染。</p>
 */
public class SceneLayoutParallelDeterminismTest {

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
     * <p>每个节点设唯一 text 标签作为身份标识，便于比 Set 内容（不同树节点引用不同，
     * 但 text 标签相同结构）。</p>
     *
     * @param containerCount    容器数
     * @param leavesPerContainer 每容器叶数
     * @param rootLabel         root 的 text 标签
     * @return 构造好的树根节点
     */
    private SceneNode buildLargeTree(int containerCount, int leavesPerContainer, String rootLabel) {
        SceneNode root = new SceneNode();
        root.setText(rootLabel);
        for (int i = 0; i < containerCount; i++) {
            SceneNode container = new SceneNode();
            container.setText(rootLabel + ".C" + i);
            for (int j = 0; j < leavesPerContainer; j++) {
                SceneNode leaf = new SceneNode();
                leaf.setText(rootLabel + ".C" + i + ".L" + j);
                container.appendChild(leaf);
            }
            root.appendChild(container);
        }
        return root;
    }

    /**
     * 构造一棵 ≥256 节点的树，root 为 ROW + fillParentHeight，含 containerCount 个
     * fillParentHeight 的 COLUMN 子容器，每个子容器含 leavesPerContainer 个文本叶。
     *
     * <p>用于 LinkedHashSet 顺序测试：改 root 约束高度时，root 下传交叉轴高给 fill 子容器，
     * 触发 fill 子容器 constraintRelayouted（selfDirty=false 但 selfConsumesConstraint=true）。</p>
     *
     * @param containerCount    fill 子容器数
     * @param leavesPerContainer 每容器叶数
     * @param rootLabel         root 的 text 标签
     * @return 构造好的树根节点
     */
    private SceneNode buildLargeTreeWithFillContainers(int containerCount, int leavesPerContainer,
                                                       String rootLabel) {
        SceneNode root = new SceneNode();
        root.setText(rootLabel);
        root.setFlexDirection(FlexDirection.ROW);
        root.setFillParentHeight(true);
        for (int i = 0; i < containerCount; i++) {
            SceneNode container = new SceneNode();
            container.setText(rootLabel + ".FC" + i);
            container.setFillParentHeight(true);
            // COLUMN 默认，叶子垂直堆叠
            for (int j = 0; j < leavesPerContainer; j++) {
                SceneNode leaf = new SceneNode();
                leaf.setText(rootLabel + ".FC" + i + ".L" + j);
                container.appendChild(leaf);
            }
            root.appendChild(container);
        }
        return root;
    }

    /**
     * 收集节点的 text 标签集合（用于比不同树节点引用的 Set 内容等价性）。
     *
     * @param nodes 节点集合
     * @return text 标签集合
     */
    private Set<String> collectTexts(Set<SceneNode> nodes) {
        Set<String> texts = new HashSet<>();
        for (SceneNode n : nodes) {
            texts.add(n.getText());
        }
        return texts;
    }

    /**
     * 收集节点的 text 标签有序列表（用于比 LinkedHashSet 迭代顺序）。
     *
     * @param nodes 节点集合（应为 LinkedHashSet 以保留顺序）
     * @return text 标签有序列表
     */
    private List<String> collectOrderedTexts(Set<SceneNode> nodes) {
        List<String> texts = new ArrayList<>();
        for (SceneNode n : nodes) {
            texts.add(n.getText());
        }
        return texts;
    }

    // ============================================================
    // 测试 1：核心 determinism —— 并行开/关 relayoutCount + relayoutedNodes 一致
    // ============================================================

    /**
     * 两棵相同结构的大树（4 容器 × 80 叶 = 325 节点 ≥ 256），树A 串行首次 layout、
     * 树B 并行首次 layout。断言 relayoutCount 相等 + relayoutedNodes 文本标签集合相等。
     *
     * <p>首次 layout 全部 selfDirty（构造默认），验证 worker 探针片段归并不丢不重。
     * 每个容器 count=81 ≥ 64，children.size()=4 ≥ 2，并行路径 fork 4 个子任务。</p>
     */
    @Test
    public void parallelOnOffProducesSameRelayoutCountAndNodes() {
        // 4 容器 × 80 叶 = 325 节点 ≥ 256（WHOLE_TREE_THRESHOLD）
        // 每个容器 count=81 ≥ 64（FORK_THRESHOLD），fork 触发
        // 两棵树用相同 rootLabel，使 text 标签集合可直接比较（验证归并不丢不重）
        SceneNode treeA = buildLargeTree(4, 80, "T");
        SceneNode treeB = buildLargeTree(4, 80, "T");
        Constraints constraints = new Constraints(200);

        // 树A 串行首次 layout
        SceneParallelExecutor.setParallelEnabled(false);
        SceneLayoutEngine engineA = new SceneLayoutEngine(measurer);
        LayoutResult serialResult = engineA.layout(treeA, constraints);

        // 树B 并行首次 layout
        SceneParallelExecutor.setParallelEnabled(true);
        SceneLayoutEngine engineB = new SceneLayoutEngine(measurer);
        LayoutResult parallelResult = engineB.layout(treeB, constraints);

        // 断言 relayoutCount 一致（全部节点 selfDirty，count=325）
        Assert.assertEquals("relayoutCount 并行/串行一致",
                serialResult.getRelayoutCount(),
                parallelResult.getRelayoutCount());

        // 断言 relayoutedNodes 大小一致（验证归并不丢不重）
        Assert.assertEquals("relayoutedNodes 大小并行/串行一致",
                serialResult.getRelayoutedNodes().size(),
                parallelResult.getRelayoutedNodes().size());

        // 断言 relayoutedNodes 内容一致（用 text 标签集合比，两棵树前缀相同）
        Set<String> serialTexts = collectTexts(serialResult.getRelayoutedNodes());
        Set<String> parallelTexts = collectTexts(parallelResult.getRelayoutedNodes());
        Assert.assertEquals("relayoutedNodes 内容并行/串行一致",
                serialTexts, parallelTexts);

        // 期望全部 325 节点都进 relayoutedNodes（首次 layout 全部 selfDirty）
        Assert.assertEquals("relayoutedNodes 应含全部 325 节点",
                325, serialResult.getRelayoutCount());
    }

    // ============================================================
    // 测试 2：LinkedHashSet 顺序 —— constraintRelayoutedNodes 迭代顺序一致
    // ============================================================

    /**
     * 两棵相同结构的大树（root ROW+fill + 4 个 fill COLUMN 子容器 × 80 叶 = 325 节点）。
     * 首次 layout 用 Constraints(200)（无高度约束）使全树干净。
     * 改约束为 Constraints(200, 500)（有高度约束），root 下传交叉轴高 500 给 fill 子容器，
     * 触发 4 个 fill 子容器 constraintRelayouted（selfDirty=false 但 selfConsumesConstraint=true）。
     *
     * <p>断言两棵树 constraintRelayoutedNodes 的迭代顺序（按 text 标签序列）完全一致。
     * 验证 join 点按 fork 顺序（= children 顺序）归并保证 LinkedHashSet 确定性。</p>
     */
    @Test
    public void parallelOnOffProducesSameConstraintRelayoutedOrder() {
        // 两棵树用相同 rootLabel，使 text 标签顺序可直接比较
        SceneNode treeA = buildLargeTreeWithFillContainers(4, 80, "T");
        SceneNode treeB = buildLargeTreeWithFillContainers(4, 80, "T");
        Constraints firstConstraints = new Constraints(200);
        Constraints secondConstraints = new Constraints(200, 500);

        // 树A 串行：首次 layout 使干净，改约束触发 constraintRelayouted
        SceneParallelExecutor.setParallelEnabled(false);
        SceneLayoutEngine engineA = new SceneLayoutEngine(measurer);
        engineA.layout(treeA, firstConstraints);  // 首次全脏，使全树干净
        LayoutResult serialResult = engineA.layout(treeA, secondConstraints);  // 改约束触发

        // 树B 并行：相同流程
        SceneParallelExecutor.setParallelEnabled(true);
        SceneLayoutEngine engineB = new SceneLayoutEngine(measurer);
        engineB.layout(treeB, firstConstraints);
        LayoutResult parallelResult = engineB.layout(treeB, secondConstraints);

        // 断言 constraintRelayoutedNodes 迭代顺序一致（LinkedHashSet 顺序敏感）
        List<String> serialOrder = collectOrderedTexts(serialResult.getConstraintRelayoutedNodes());
        List<String> parallelOrder = collectOrderedTexts(parallelResult.getConstraintRelayoutedNodes());
        Assert.assertEquals("constraintRelayoutedNodes 迭代顺序并行/串行一致",
                serialOrder, parallelOrder);

        // 期望 4 个 fill 子容器进 constraintRelayoutedNodes（root 因入口标脏进 relayoutedNodes）
        Assert.assertEquals("constraintRelayoutedNodes 应含 4 个 fill 子容器",
                4, serialOrder.size());
    }

    // ============================================================
    // 测试 3：小树全串行（< 256 即使开并行也走串行路径）
    // ============================================================

    /**
     * 小树（10 节点 < 256），PARALLEL_ENABLED=true，验证 layout 正常完成且结果正确。
     * 小树不达 WHOLE_TREE_THRESHOLD，走串行路径（现状不变）。
     */
    @Test
    public void smallTreeAlwaysSerialEvenWhenParallelEnabled() {
        SceneNode root = new SceneNode();
        root.setText("R");
        for (int i = 0; i < 9; i++) {
            SceneNode leaf = new SceneNode();
            leaf.setText("R.L" + i);
            root.appendChild(leaf);
        }
        // root count=10 < 256，即使开并行也走串行路径
        SceneParallelExecutor.setParallelEnabled(true);
        SceneLayoutEngine engine = new SceneLayoutEngine(measurer);
        LayoutResult result = engine.layout(root, new Constraints(200));

        // 首次 layout 全部 selfDirty，10 节点都进 relayoutedNodes
        Assert.assertEquals("小树 relayoutCount=10", 10, result.getRelayoutCount());
        Assert.assertEquals("小树 relayoutedNodes 含 10 节点",
                10, result.getRelayoutedNodes().size());
        // 验证 layout 正常完成（root 有 cachedLayout）
        Assert.assertNotNull("root 应有 cachedLayout", root.getCachedLayout());
    }

    // ============================================================
    // 测试 4：单子树不 fork（children.size() < 2）
    // ============================================================

    /**
     * root 仅 1 个大子树（≥64 节点），但 children.size()=1 < 2 不 fork。
     * 验证 layout 正常完成且结果正确。
     *
     * <p>构造：root + 1 个大容器（含 80 叶），root count=82 < 256 走串行路径。
     * 但为测 fork 门槛逻辑，构造 root count ≥ 256 且单子树 ≥ 64：
     * root + 1 个大容器（含 300 叶），root count=302 ≥ 256 走并行路径，
     * 但 children.size()=1 < 2，大容器不 fork，仍串行递归。</p>
     */
    @Test
    public void singleChildSubtreeNotForked() {
        // root + 1 容器 + 300 叶 = 302 节点 ≥ 256（走并行路径）
        // 但 children.size()=1 < 2，大容器不 fork
        SceneNode root = new SceneNode();
        root.setText("R");
        SceneNode bigChild = new SceneNode();
        bigChild.setText("R.BC");
        for (int j = 0; j < 300; j++) {
            SceneNode leaf = new SceneNode();
            leaf.setText("R.BC.L" + j);
            bigChild.appendChild(leaf);
        }
        root.appendChild(bigChild);

        SceneParallelExecutor.setParallelEnabled(true);
        SceneLayoutEngine engine = new SceneLayoutEngine(measurer);
        LayoutResult result = engine.layout(root, new Constraints(200));

        // 首次 layout 全部 selfDirty，302 节点都进 relayoutedNodes
        Assert.assertEquals("单子树 relayoutCount=302", 302, result.getRelayoutCount());
        Assert.assertEquals("单子树 relayoutedNodes 含 302 节点",
                302, result.getRelayoutedNodes().size());
        Assert.assertNotNull("root 应有 cachedLayout", root.getCachedLayout());
    }

    // ============================================================
    // 测试 5：并行路径下 layout 几何结果正确（与串行一致）
    // ============================================================

    /**
     * 并行路径下 layout 产出的几何结果（LayoutBox）应与串行路径一致。
     * 用同一棵树，串行 layout 后记录关键节点 box，标脏重算后并行 layout，
     * 断言 box 不变（重算结果相同）。
     */
    @Test
    public void parallelLayoutProducesSameGeometryAsSerial() {
        SceneNode tree = buildLargeTree(4, 80, "T");
        Constraints constraints = new Constraints(200);

        // 串行首次 layout
        SceneParallelExecutor.setParallelEnabled(false);
        SceneLayoutEngine engine = new SceneLayoutEngine(measurer);
        engine.layout(tree, constraints);

        // 记录 root 和各容器的 box
        LayoutBox rootBoxBefore = (LayoutBox) tree.getCachedLayout();
        List<LayoutBox> containerBoxesBefore = new ArrayList<>();
        for (SceneNode child : tree.__getChildren()) {
            containerBoxesBefore.add((LayoutBox) child.getCachedLayout());
        }

        // 标脏所有节点（重置到全脏状态）
        markAllSelfLayoutDirty(tree);

        // 并行重算
        SceneParallelExecutor.setParallelEnabled(true);
        engine.layout(tree, constraints);

        // 断言 box 不变（重算结果相同）
        LayoutBox rootBoxAfter = (LayoutBox) tree.getCachedLayout();
        Assert.assertEquals("root box 并行重算后不变", rootBoxBefore, rootBoxAfter);
        for (int i = 0; i < tree.__getChildren().size(); i++) {
            LayoutBox before = containerBoxesBefore.get(i);
            LayoutBox after = (LayoutBox) tree.__getChildren().get(i).getCachedLayout();
            Assert.assertEquals("容器 " + i + " box 并行重算后不变", before, after);
        }
    }

    /**
     * 递归标脏整棵树（每个节点调 markSelfLayout），使下次 layout 重算全部节点。
     *
     * @param node 子树根
     */
    private void markAllSelfLayoutDirty(SceneNode node) {
        node.markSelfLayout();
        for (SceneNode child : node.__getChildren()) {
            markAllSelfLayoutDirty(child);
        }
    }
}
