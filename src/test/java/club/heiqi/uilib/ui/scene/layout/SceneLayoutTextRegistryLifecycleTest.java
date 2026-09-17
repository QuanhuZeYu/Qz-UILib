package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.scene.testkit.SceneTestEnvironments;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneListHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * P1-1 守卫：{@code SceneLayoutEngine} 文本叶登记表（measuredTextNodes）的生命周期三不变量。
 *
 * <h3>不变量</h3>
 * <ul>
 *   <li><b>索引完备性</b>：已附着且可能被布局短路跳过的文本叶必须在表内，
 *       measurer epoch 变化能传导到它（④/⑤ 正向守卫）。</li>
 *   <li><b>有界性</b>：顶层祖先已断开已知布局根的条目必须被剪掉；开合 20 次回基线（②/③）。</li>
 *   <li><b>确定性</b>：剪枝只看树结构与已知根 LRU，不看 GC 时机；同一输入必得同一表（⑦）。</li>
 * </ul>
 *
 * <p>全部断言不依赖 GC、真机、帧率与 sleep：只用 {@link FixedTextMeasurer}（可控 epoch）
 * 与手工 {@code layout()}。不调用 {@code System.gc()}，不做内存断言。</p>
 */
public class SceneLayoutTextRegistryLifecycleTest {

    private final FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
    private final SceneLayoutEngine engine = new SceneLayoutEngine(measurer);
    private SceneRuntime runtime;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = SceneTestEnvironments.runtime(measurer);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** ① 挂载含 K 个文本叶的子树 + layout → 登记表恰好 K 条。 */
    @Test
    public void mountedTextLeavesAreRegisteredExactlyOnce() {
        SceneNode root = new SceneNode();
        SceneNode panel = new SceneNode();
        SceneNode first = textLeaf(root, panel, "row-1");
        SceneNode second = textLeaf(root, panel, "row-2");
        SceneNode third = textLeaf(root, panel, "row-3");

        engine.layout(root, new Constraints(200));

        Assert.assertEquals("3 个文本叶应登记 3 条（幂等 add，无重复）",
                3, engine.__measuredTextNodeCount());
        Assert.assertNotNull(first.getCachedLayout());
        Assert.assertNotNull(second.getCachedLayout());
        Assert.assertNotNull(third.getCachedLayout());
    }

    /** ②-a 摘除子树（removeChild 路径）+ 结构变更后 layout → 条目归零，且断开子树缓存被作废。 */
    @Test
    public void removeChildPathPrunesDetachedTextLeaves() {
        SceneNode root = new SceneNode();
        SceneNode panel = new SceneNode();
        textLeaf(root, panel, "row-1");
        textLeaf(root, panel, "row-2");
        engine.layout(root, new Constraints(200));
        Assert.assertEquals(2, engine.__measuredTextNodeCount());

        root.removeChild(panel);
        engine.layout(root, new Constraints(200));

        Assert.assertEquals("摘除的子树不得继续钉在登记表内",
                0, engine.__measuredTextNodeCount());
        Assert.assertNull("断开子树的布局缓存必须被作废（重挂载后才会重算并重新登记）",
                panel.getCachedLayout());
    }

    /** ②-b 摘除子树（rt.forEach 项移除路径）+ 结构变更后 layout → 仅移除项归零。 */
    @Test
    public void forEachItemRemovalPrunesDetachedTextLeaves() {
        SceneNode root = new SceneNode();
        SceneNode container = new SceneNode();
        root.appendChild(container);
        Signal<List<Item>> items = Signal.create(new ArrayList<Item>(
                Arrays.asList(new Item("a"), new Item("b"), new Item("c"))));
        SceneListHandle handle = runtime.forEach(container, items, item -> {
            SceneNode node = new SceneNode();
            node.setText(item.name);
            return node;
        });
        runtime.flush();
        engine.layout(root, new Constraints(200));
        Assert.assertEquals("3 行文本各登记 1 条", 3, engine.__measuredTextNodeCount());

        items.set(new ArrayList<Item>(Arrays.asList(new Item("a"))));
        runtime.flush();
        engine.layout(root, new Constraints(200));

        Assert.assertEquals("被 reconcile 摘除的行不得继续钉在登记表内（仅剩 1 行）",
                1, engine.__measuredTextNodeCount());
        handle.dispose();
    }

    /** ③ 20 次「挂载 → layout → 摘除 → layout」循环：每轮回基线，不单调增长。 */
    @Test
    public void twentyMountUnmountCyclesReturnToBaseline() {
        SceneNode root = new SceneNode();
        for (int cycle = 0; cycle < 20; cycle++) {
            SceneNode panel = new SceneNode();
            textLeaf(root, panel, "label-" + cycle);
            engine.layout(root, new Constraints(200));
            Assert.assertEquals("第 " + cycle + " 轮挂载后 1 条", 1,
                    engine.__measuredTextNodeCount());

            root.removeChild(panel);
            engine.layout(root, new Constraints(200));
            Assert.assertEquals("第 " + cycle + " 轮卸载后必须回基线 0",
                    0, engine.__measuredTextNodeCount());
        }
    }

    /** ④ 反向完备性：剪枝之后，附着中的文本叶仍能被 measurer epoch 变化传导重排。 */
    @Test
    public void epochChangeStillRelayoutsAttachedTextLeavesAfterPruning() {
        SceneNode root = new SceneNode();
        SceneNode livePanel = new SceneNode();
        SceneNode liveLabel = textLeaf(root, livePanel, "live");
        SceneNode deadPanel = new SceneNode();
        textLeaf(root, deadPanel, "dead");
        engine.layout(root, new Constraints(200));
        Assert.assertEquals(2, engine.__measuredTextNodeCount());

        root.removeChild(deadPanel);
        engine.layout(root, new Constraints(200));
        Assert.assertEquals("断开的一半被剪掉，附着的一半保留",
                1, engine.__measuredTextNodeCount());

        measurer.bumpEpoch();
        LayoutResult result = engine.layout(root, new Constraints(200));

        Assert.assertTrue("附着中的文本叶必须仍被 epoch 失效链触达（索引完备性不可牺牲）",
                result.getRelayoutedNodes().contains(liveLabel));
        Assert.assertEquals("剪枝不得误剪仍附着的条目", 1, engine.__measuredTextNodeCount());
    }

    /** ⑤ 摘除—重挂载往返自愈：重挂载的子树必然重算并重新登记，随后 epoch 仍能传导。 */
    @Test
    public void reattachedSubtreeIsReRegisteredAndEpochReachable() {
        SceneNode root = new SceneNode();
        SceneNode panel = new SceneNode();
        SceneNode label = textLeaf(root, panel, "round-trip");
        engine.layout(root, new Constraints(200));

        root.removeChild(panel);
        engine.layout(root, new Constraints(200));
        Assert.assertEquals("断开后被剪枝", 0, engine.__measuredTextNodeCount());

        root.appendChild(panel);
        engine.layout(root, new Constraints(200));
        Assert.assertEquals("重挂载后必须重新登记（缓存已作废 → 布局下潜到文本叶）",
                1, engine.__measuredTextNodeCount());

        measurer.bumpEpoch();
        LayoutResult result = engine.layout(root, new Constraints(200));
        Assert.assertTrue("重挂载后的文本叶必须重新进入 epoch 失效链",
                result.getRelayoutedNodes().contains(label));
    }

    /** ⑥ 多根复用：同一引擎先后 layout rootA/rootB，rootA 的条目不得被 rootB 的剪枝丢弃。 */
    @Test
    public void knownRootLruKeepsOtherRootsTextLeaves() {
        SceneNode rootA = new SceneNode();
        SceneNode rootB = new SceneNode();
        SceneNode labelA = textLeaf(rootA, new SceneNode(), "a");
        SceneNode labelB = textLeaf(rootB, new SceneNode(), "b");

        engine.layout(rootA, new Constraints(200));
        engine.layout(rootB, new Constraints(200));
        // 制造一次结构变更（触发剪枝）后再核对两个根的条目都在
        textLeaf(rootA, new SceneNode(), "a2");
        engine.layout(rootA, new Constraints(200));

        Assert.assertEquals("两个已知根下的条目都必须保留（防误剪）",
                3, engine.__measuredTextNodeCount());
        Assert.assertTrue(engine.__knownRootCount() <= 8);
        Assert.assertNotNull(labelA.getCachedLayout());
        Assert.assertNotNull(labelB.getCachedLayout());
    }

    /** ⑦ 确定性：剪枝只由结构决定，重复调用幂等；已知根 LRU 有界。 */
    @Test
    public void pruneIsDeterministicIdempotentAndBounded() {
        SceneNode root = new SceneNode();
        SceneNode panel = new SceneNode();
        textLeaf(root, panel, "label");
        engine.layout(root, new Constraints(200));
        root.removeChild(panel);

        Assert.assertEquals("首次剪枝移除 1 条", 1, engine.__pruneDetachedEntryCountForTest());
        Assert.assertEquals("重复剪枝零移除（同一输入必得同一结果）",
                0, engine.__pruneDetachedEntryCountForTest());
        Assert.assertEquals(0, engine.__measuredTextNodeCount());

        for (int i = 0; i < 12; i++) {
            SceneNode extraRoot = new SceneNode();
            textLeaf(extraRoot, new SceneNode(), "r" + i);
            engine.layout(extraRoot, new Constraints(200));
        }
        Assert.assertTrue("已知根 LRU 必须有界（容量 8）", engine.__knownRootCount() <= 8);
    }

    /** 建一条 root → panel → 文本叶 的链路并返回文本叶（唯一测量落点断言用）。 */
    private static SceneNode textLeaf(SceneNode root, SceneNode panel, String text) {
        if (panel.__getParent() == null && panel != root) {
            root.appendChild(panel);
        }
        SceneNode leaf = new SceneNode();
        leaf.setText(text);
        panel.appendChild(leaf);
        return leaf;
    }

    /** forEach 行的值对象（引用做 key）。 */
    private static final class Item {

        final String name;

        Item(String name) {
            this.name = name;
        }
    }
}
