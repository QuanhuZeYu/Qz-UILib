package club.heiqi.uilib.ui.scene.image;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.image.ItemRenderTierRegistry.Classification;
import club.heiqi.uilib.ui.scene.image.ItemRenderTierRegistry.Listener;
import club.heiqi.uilib.ui.scene.image.ItemRenderTierRegistry.Outcome;
import club.heiqi.uilib.ui.scene.image.ItemRenderTierRegistry.Tier;

/**
 * UNRENDERABLE tombstone 语义测试（ADR §5.3 / §10 R-13，C7 对账点名的类）。
 *
 * <p>判据三条：</p>
 * <ol>
 *   <li><b>上限 1024</b>：tombstone 自身必须有界（{@code tombstoneSize() <= MAX_TOMBSTONES}），
 *       满额时<b>不淘汰既有 tombstone</b> —— 新的 UNRENDERABLE 键退化为「留在活跃表内不淘汰」；</li>
 *   <li><b>不复活</b>：已入墓碑的键恒返回 UNRENDERABLE，后续全干净上报既不改变分级、也不重新入活跃表、
 *       更不逐键重发分级回调（终态键不得再被送进带 GL 检测的渲染路径）；</li>
 *   <li><b>逐出语义</b>：活跃表超限时按最近访问序淘汰，可淘汰条目优先于 UNRENDERABLE；
 *       只有「活跃表全是 UNRENDERABLE」时才把最久未访问者移入墓碑，且仅在有余额时。</li>
 * </ol>
 */
public class ItemRenderTierRegistryTombstoneTest {

    @Before
    public void setUp() {
        ItemRenderTierRegistry.resetForTests();
    }

    @After
    public void tearDown() {
        ItemRenderTierRegistry.resetForTests();
    }

    /** tombstone 收集被逐出的 UNRENDERABLE 键，直到上限 1024 为止。 */
    @Test
    public void tombstonesCollectEvictedUnrenderableKeysUpToTheBound() {
        int unrenderable = ItemRenderTierRegistry.MAX_TOMBSTONES;
        for (int index = 0; index < unrenderable; index++) {
            classifyUnrenderable("bad" + index);
        }
        // 灌入足够多的可渲染键，触发多轮超限淘汰（把最久未访问的 UNRENDERABLE 挤进 tombstone）。
        int fillers = ItemRenderTierRegistry.MAX_ACTIVE_ENTRIES + 4 * 512;
        for (int index = 0; index < fillers; index++) {
            classifyRenderable("filler" + index);
        }

        Assert.assertEquals("tombstone 必须填满到上限（实测 " + ItemRenderTierRegistry.tombstoneSize() + "）",
                ItemRenderTierRegistry.MAX_TOMBSTONES, ItemRenderTierRegistry.tombstoneSize());
        Assert.assertTrue("活跃表仍受上限约束", ItemRenderTierRegistry.size() <= ItemRenderTierRegistry.MAX_ACTIVE_ENTRIES);
        for (int index = 0; index < unrenderable; index += 137) {
            Assert.assertEquals("已入墓碑的键恒为 UNRENDERABLE: bad" + index,
                    Tier.UNRENDERABLE, ItemRenderTierRegistry.tierOf("bad" + index));
        }
    }

    /** 满额后：新 UNRENDERABLE 键不得淘汰既有 tombstone（有界性让位于「绝不复活」）。 */
    @Test
    public void fullTombstoneTableNeverEvictsExistingTombstones() {
        int unrenderable = ItemRenderTierRegistry.MAX_TOMBSTONES;
        for (int index = 0; index < unrenderable; index++) {
            classifyUnrenderable("bad" + index);
        }
        for (int index = 0; index < ItemRenderTierRegistry.MAX_ACTIVE_ENTRIES + 4 * 512; index++) {
            classifyRenderable("filler" + index);
        }
        Assert.assertEquals(ItemRenderTierRegistry.MAX_TOMBSTONES, ItemRenderTierRegistry.tombstoneSize());

        // 继续灌入新的 UNRENDERABLE + 淘汰压力：tombstone 不得超上限，也不得把既有条目挤掉。
        for (int index = 0; index < 400; index++) {
            classifyUnrenderable("extra" + index);
            classifyRenderable("more" + index);
        }
        Assert.assertTrue("tombstone 不得越过上限（实测 " + ItemRenderTierRegistry.tombstoneSize() + "）",
                ItemRenderTierRegistry.tombstoneSize() <= ItemRenderTierRegistry.MAX_TOMBSTONES);
        Assert.assertEquals("既有 tombstone 不得被新键淘汰",
                Tier.UNRENDERABLE, ItemRenderTierRegistry.tierOf("bad0"));
        Assert.assertEquals(Tier.UNRENDERABLE, ItemRenderTierRegistry.tierOf("bad1023"));
        Assert.assertEquals("满额时新 UNRENDERABLE 键退化为活跃表内不淘汰",
                Tier.UNRENDERABLE, ItemRenderTierRegistry.tierOf("extra0"));
    }

    /** 不复活：墓碑键在全干净上报后仍是终态，且不重新入活跃表、不重发分级回调。 */
    @Test
    public void tombstonedKeyNeverRevivesAndStopsNotifying() {
        classifyUnrenderable("minecraft:bad");
        for (int index = 0; index < ItemRenderTierRegistry.MAX_ACTIVE_ENTRIES + 600; index++) {
            classifyRenderable("filler" + index);
        }
        Assert.assertTrue("前提：键已被挤入 tombstone", ItemRenderTierRegistry.tombstoneSize() >= 1);
        Assert.assertEquals(Tier.UNRENDERABLE, ItemRenderTierRegistry.tierOf("minecraft:bad"));

        final List<Classification> notifications = new ArrayList<Classification>();
        Listener listener = new Listener() {
            @Override
            public void onClassification(Classification classification) {
                notifications.add(classification);
            }
        };
        ItemRenderTierRegistry.addListener(listener);
        try {
            for (int attempt = 0; attempt < 5; attempt++) {
                ItemRenderTierRegistry.classify("minecraft:bad", Outcome.OK, null);
            }
            for (int attempt = 0; attempt < 5; attempt++) {
                ItemRenderTierRegistry.classify("minecraft:bad", Outcome.EXCEPTION, "boom");
            }
            Assert.assertTrue("终态键不得重发分级回调: " + notifications, notifications.isEmpty());
            Assert.assertEquals("终态键不得复活", Tier.UNRENDERABLE,
                    ItemRenderTierRegistry.tierOf("minecraft:bad"));
        } finally {
            ItemRenderTierRegistry.removeListener(listener);
        }

        // invalidateAll（资源重载/断连）是唯一复位通道：墓碑清空、代际自增、只广播一次。
        final int[] invalidations = { 0 };
        Listener invalidationListener = new Listener() {
            @Override
            public void onClassification(Classification classification) {
                Assert.fail("无效化不得逐键回调");
            }

            @Override
            public void onInvalidated(String reason) {
                invalidations[0]++;
            }
        };
        ItemRenderTierRegistry.addListener(invalidationListener);
        try {
            ItemRenderTierRegistry.invalidateAll("test");
        } finally {
            ItemRenderTierRegistry.removeListener(invalidationListener);
        }
        Assert.assertEquals("失效必须恰好广播一次", 1, invalidations[0]);
        Assert.assertEquals("失效清空 tombstone", 0, ItemRenderTierRegistry.tombstoneSize());
        Assert.assertEquals("失效后键回到跟踪态（唯一允许的复位路径）",
                Tier.TRACKING, ItemRenderTierRegistry.tierOf("minecraft:bad"));
    }

    /** 逐出语义：活跃表超限即按最近访问序淘汰，最久未访问者先出局、稳态不越上限。 */
    @Test
    public void activeTableEvictsLeastRecentlyAccessedFirstAndStaysBounded() {
        int bound = ItemRenderTierRegistry.MAX_ACTIVE_ENTRIES;
        for (int index = 0; index < bound; index++) {
            classifyRenderable("k" + index);
        }
        Assert.assertEquals("未超限时不得提前淘汰", bound, ItemRenderTierRegistry.size());

        // 触碰最新键使其成为最近访问；随后插入触发淘汰。
        ItemRenderTierRegistry.tierOf("k" + (bound - 1));
        classifyRenderable("overflow");

        Assert.assertTrue("超限必须真淘汰（实测 " + ItemRenderTierRegistry.size() + "）",
                ItemRenderTierRegistry.size() < bound);
        Assert.assertTrue("淘汰后仍受上限约束",
                ItemRenderTierRegistry.size() <= ItemRenderTierRegistry.MAX_ACTIVE_ENTRIES);
        Assert.assertEquals("最久未访问者被淘汰（回到未分级跟踪态）",
                Tier.TRACKING, ItemRenderTierRegistry.tierOf("k0"));
        Assert.assertEquals("最近访问者仍在表中（RENDERABLE 不回落跟踪态）",
                Tier.RENDERABLE, ItemRenderTierRegistry.tierOf("k" + (bound - 1)));
    }

    private static void classifyRenderable(String key) {
        ItemRenderTierRegistry.classify(key, Outcome.OK, null);
        ItemRenderTierRegistry.classify(key, Outcome.OK, null);
        ItemRenderTierRegistry.classify(key, Outcome.OK, null);
    }

    private static void classifyUnrenderable(String key) {
        ItemRenderTierRegistry.classify(key, Outcome.EXCEPTION, "boom");
        ItemRenderTierRegistry.classify(key, Outcome.EXCEPTION, "boom");
        ItemRenderTierRegistry.classify(key, Outcome.EXCEPTION, "boom");
    }
}
