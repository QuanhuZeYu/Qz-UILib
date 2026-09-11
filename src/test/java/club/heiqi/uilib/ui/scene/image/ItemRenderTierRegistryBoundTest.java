package club.heiqi.uilib.ui.scene.image;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link ItemRenderTierRegistry} 有界性与 UNRENDERABLE tombstone 测试（判据 A-18 的有界面）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §5.3：活跃表 LRU ≤ 8192；UNRENDERABLE 一旦判定
 * <b>永不逐出</b>（移入上限 1024 的 tombstone，{@code tierOf} 命中 tombstone 直接返回 UNRENDERABLE）；
 * tombstone 满额时新 UNRENDERABLE 键退化为「活跃表内不淘汰」。</p>
 */
public class ItemRenderTierRegistryBoundTest {

    @Before
    public void setUp() {
        ItemRenderTierRegistry.resetForTests();
    }

    @After
    public void tearDown() {
        ItemRenderTierRegistry.resetForTests();
    }

    /** 活跃表有界：塞入远超上限的键后仍 ≤ MAX_ACTIVE_ENTRIES。 */
    @Test
    public void activeTableStaysWithinBound() {
        int inserted = ItemRenderTierRegistry.MAX_ACTIVE_ENTRIES + 800;
        for (int index = 0; index < inserted; index++) {
            classifyRenderable("k" + index);
        }
        Assert.assertTrue("灌入量必须真超过上限（正锚）", inserted > ItemRenderTierRegistry.MAX_ACTIVE_ENTRIES);
        Assert.assertTrue("活跃表必须 ≤ 上限（实测 " + ItemRenderTierRegistry.size() + "）",
                ItemRenderTierRegistry.size() <= ItemRenderTierRegistry.MAX_ACTIVE_ENTRIES);
        Assert.assertTrue("淘汰必须真发生（不能靠没灌进去而通过）",
                ItemRenderTierRegistry.size() < inserted);
    }

    /** UNRENDERABLE 判定后即使被 LRU 挤出活跃表也不回到 TRACKING（tombstone 判据）。 */
    @Test
    public void unrenderableSurvivesEvictionThroughTombstone() {
        classifyUnrenderable("minecraft:bad");
        Assert.assertEquals(ItemRenderTierRegistry.Tier.UNRENDERABLE,
                ItemRenderTierRegistry.tierOf("minecraft:bad"));

        for (int index = 0; index < ItemRenderTierRegistry.MAX_ACTIVE_ENTRIES + 600; index++) {
            classifyRenderable("filler" + index);
        }

        Assert.assertTrue("活跃表仍受上限约束", ItemRenderTierRegistry.size() <= ItemRenderTierRegistry.MAX_ACTIVE_ENTRIES);
        Assert.assertEquals("UNRENDERABLE 必须移入 tombstone 而不是被丢弃",
                ItemRenderTierRegistry.Tier.UNRENDERABLE, ItemRenderTierRegistry.tierOf("minecraft:bad"));
        Assert.assertTrue(ItemRenderTierRegistry.tombstoneSize() >= 1);
    }

    /** tombstone 自身有界（≤ 1024），且已入墓碑的键在后续大量淘汰后仍不可复活。 */
    @Test
    public void tombstoneIsBoundedAndNeverResurrects() {
        classifyUnrenderable("minecraft:first");
        for (int index = 0; index < ItemRenderTierRegistry.MAX_ACTIVE_ENTRIES + 600; index++) {
            classifyRenderable("filler" + index);
        }
        Assert.assertEquals(ItemRenderTierRegistry.Tier.UNRENDERABLE,
                ItemRenderTierRegistry.tierOf("minecraft:first"));

        // 继续灌入大量新键（含更多 UNRENDERABLE）→ tombstone 不得超过上限
        for (int index = 0; index < 300; index++) {
            classifyUnrenderable("bad" + index);
            classifyRenderable("more" + index);
        }

        Assert.assertTrue("tombstone 必须有界（实测 " + ItemRenderTierRegistry.tombstoneSize() + "）",
                ItemRenderTierRegistry.tombstoneSize() <= ItemRenderTierRegistry.MAX_TOMBSTONES);
        Assert.assertEquals("已入墓碑的键不得复活",
                ItemRenderTierRegistry.Tier.UNRENDERABLE, ItemRenderTierRegistry.tierOf("minecraft:first"));

        // 终态键：即使后续上报全干净也不回到 TRACKING（不重新触碰 GL）
        ItemRenderTierRegistry.classify("minecraft:first", ItemRenderTierRegistry.Outcome.OK, null);
        Assert.assertEquals(ItemRenderTierRegistry.Tier.UNRENDERABLE,
                ItemRenderTierRegistry.tierOf("minecraft:first"));
    }

    private static void classifyRenderable(String key) {
        ItemRenderTierRegistry.classify(key, ItemRenderTierRegistry.Outcome.OK, null);
        ItemRenderTierRegistry.classify(key, ItemRenderTierRegistry.Outcome.OK, null);
        ItemRenderTierRegistry.classify(key, ItemRenderTierRegistry.Outcome.OK, null);
    }

    private static void classifyUnrenderable(String key) {
        ItemRenderTierRegistry.classify(key, ItemRenderTierRegistry.Outcome.EXCEPTION, "boom");
        ItemRenderTierRegistry.classify(key, ItemRenderTierRegistry.Outcome.EXCEPTION, "boom");
        ItemRenderTierRegistry.classify(key, ItemRenderTierRegistry.Outcome.EXCEPTION, "boom");
    }
}
