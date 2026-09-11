package club.heiqi.uilib.ui.scene.layout;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * P1-1/P1-4 守卫：{@code SceneLayoutEngine.layout} 入口快路径的等价性证书。
 *
 * <p>登记表账（epoch 失效链全量遍历）从「每批一次」降为「每个 epoch 值一次」，
 * 依据是 {@code SizingCalculator} 每次登记都用<b>当时</b>的 {@code measurer.epoch()} 打戳，
 * 故 epoch 与上次扫描记录值相同时表内不可能有 stale 成员。本类把该推理变成可回归断言：</p>
 * <ul>
 *   <li>稳态批：{@code __lastSweepEntryCount() == 0}（零遍历）；</li>
 *   <li>epoch 变化批：恰好扫一次全表；下一批回到 0；</li>
 *   <li>快路径期间的<b>新增</b>文本叶仍被正确打上当前 epoch 戳，后续 epoch 变化仍能传导
 *       （这是快路径唯一的前提条件，必须正向钉死）。</li>
 * </ul>
 */
public class SceneLayoutEngineEntryFastPathTest {

    private final FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
    private final SceneLayoutEngine engine = new SceneLayoutEngine(measurer);

    /** 稳态批零遍历：epoch 未变时不再付 |登记表| 次迭代。 */
    @Test
    public void steadyFrameSweepsNothing() {
        SceneNode root = new SceneNode();
        SceneNode label = new SceneNode();
        label.setText("hello");
        root.appendChild(label);

        Constraints constraints = new Constraints(200);
        engine.layout(root, constraints);
        LayoutResult steady = engine.layout(root, constraints);
        Assert.assertEquals("epoch 未变 → 零遍历（登记表已有 1 条也不再迭代）",
                0, engine.__lastSweepEntryCount());
        Assert.assertEquals("稳态批零重算", 0, steady.getRelayoutCount());

        measurer.bumpEpoch();
        engine.layout(root, constraints);
        Assert.assertEquals("epoch 变化才扫表，且扫到的就是那 1 条",
                1, engine.__lastSweepEntryCount());
    }

    /** epoch 变化批恰好扫一次全表，随后立即回到零遍历。 */
    @Test
    public void epochChangeSweepsExactlyOncePerEpochValue() {
        SceneNode root = new SceneNode();
        for (int i = 0; i < 3; i++) {
            SceneNode label = new SceneNode();
            label.setText("row-" + i);
            root.appendChild(label);
        }
        Constraints constraints = new Constraints(200);
        engine.layout(root, constraints);

        measurer.bumpEpoch();
        engine.layout(root, constraints);
        Assert.assertEquals("epoch 变化批必须扫全表（3 条）", 3, engine.__lastSweepEntryCount());

        engine.layout(root, constraints);
        Assert.assertEquals("同 epoch 的下一批必须回到零遍历", 0, engine.__lastSweepEntryCount());
    }

    /** 快路径前提正证：稳态期间新增的文本叶打的是当前 epoch 戳，后续 epoch 变化仍能传导。 */
    @Test
    public void leafAddedDuringFastPathIsStampedWithCurrentEpoch() {
        SceneNode root = new SceneNode();
        SceneNode first = new SceneNode();
        first.setText("first");
        root.appendChild(first);
        Constraints constraints = new Constraints(200);
        engine.layout(root, constraints);
        engine.layout(root, constraints);
        Assert.assertEquals(0, engine.__lastSweepEntryCount());

        // 稳态期间追加新文本叶（epoch 未变，入口不会扫描）
        SceneNode second = new SceneNode();
        second.setText("second");
        root.appendChild(second);
        LayoutResult mounted = engine.layout(root, constraints);
        Assert.assertEquals("新挂载的文本叶被测量并入表", 2, engine.__measuredTextNodeCount());
        Assert.assertTrue(mounted.getRelayoutedNodes().contains(second));

        // epoch 变化：两个叶都必须被失效链触达
        measurer.bumpEpoch();
        LayoutResult result = engine.layout(root, constraints);
        Assert.assertTrue("稳态新增的叶也必须进入失效链",
                result.getRelayoutedNodes().contains(first));
        Assert.assertTrue("稳态新增的叶也必须进入失效链",
                result.getRelayoutedNodes().contains(second));
    }
}
