package club.heiqi.uilib.ui.scene.image;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link ItemRenderTierRegistry} 生产失效入口测试（判据 A-18 的失效面）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §5.3：{@code invalidateAll(reason)} 清活跃表 +
 * tombstone + 代际自增；<b>不逐键回调</b> {@code onClassification}，改为一次 {@code onInvalidated} 广播；
 * {@code resetForTests()} 语义不变（测试语义与生产语义分离）。</p>
 */
public class ItemRenderTierRegistryInvalidationTest {

    @Before
    public void setUp() {
        ItemRenderTierRegistry.resetForTests();
    }

    @After
    public void tearDown() {
        ItemRenderTierRegistry.resetForTests();
    }

    /** 失效后旧 UNRENDERABLE 不驻留：同键回到 TRACKING，两张表清空，代际 +1。 */
    @Test
    public void invalidateAllClearsBothTablesAndResetsTiers() {
        makeUnrenderable("minecraft:bad");
        Assert.assertEquals(ItemRenderTierRegistry.Tier.UNRENDERABLE,
                ItemRenderTierRegistry.tierOf("minecraft:bad"));
        long before = ItemRenderTierRegistry.tierGeneration();

        ItemRenderTierRegistry.invalidateAll("resource_reload");

        Assert.assertEquals(before + 1L, ItemRenderTierRegistry.tierGeneration());
        Assert.assertEquals(0, ItemRenderTierRegistry.size());
        Assert.assertEquals(0, ItemRenderTierRegistry.tombstoneSize());
        Assert.assertEquals("旧 UNRENDERABLE 必须不驻留（资源包换图后重新追踪）",
                ItemRenderTierRegistry.Tier.TRACKING, ItemRenderTierRegistry.tierOf("minecraft:bad"));
    }

    /** 广播恰好一次，且不逐键回调单键事件（逐键通知会引发 N 次重取风暴）。 */
    @Test
    public void invalidateBroadcastsOnceWithoutPerKeyCallbacks() {
        final List<String> invalidated = new ArrayList<String>();
        final List<ItemRenderTierRegistry.Classification> perKey = new ArrayList<ItemRenderTierRegistry.Classification>();
        ItemRenderTierRegistry.addListener(new ItemRenderTierRegistry.Listener() {
            @Override
            public void onClassification(ItemRenderTierRegistry.Classification classification) {
                perKey.add(classification);
            }

            @Override
            public void onInvalidated(String reason) {
                invalidated.add(reason);
            }
        });
        makeUnrenderable("minecraft:bad");
        Assert.assertEquals("追踪期分级变化仍逐键通知（未改既有语义）", 1, perKey.size());
        perKey.clear();

        ItemRenderTierRegistry.invalidateAll("world_unload");

        Assert.assertEquals(java.util.Collections.singletonList("world_unload"), invalidated);
        Assert.assertEquals("失效不得逐键回调", 0, perKey.size());
    }

    /** 监听器异常隔离：广播不因单个监听器抛错而中断。 */
    @Test
    public void invalidateIsolatesListenerFailures() {
        final List<String> seen = new ArrayList<String>();
        ItemRenderTierRegistry.addListener(new ItemRenderTierRegistry.Listener() {
            @Override
            public void onClassification(ItemRenderTierRegistry.Classification classification) {
            }

            @Override
            public void onInvalidated(String reason) {
                throw new IllegalStateException("boom");
            }
        });
        ItemRenderTierRegistry.addListener(new ItemRenderTierRegistry.Listener() {
            @Override
            public void onClassification(ItemRenderTierRegistry.Classification classification) {
            }

            @Override
            public void onInvalidated(String reason) {
                seen.add(reason);
            }
        });

        ItemRenderTierRegistry.invalidateAll("x");

        Assert.assertEquals(1, seen.size());
        Assert.assertEquals("x", seen.get(0));
    }

    /** 旧监听器（只实现单键回调）零改动可编译：onInvalidated 是 default 方法。 */
    @Test
    public void legacyListenerStillWorks() {
        final List<String> invalidations = new ArrayList<String>();
        ItemRenderTierRegistry.Listener legacy = classification -> { };
        ItemRenderTierRegistry.addListener(legacy);
        ItemRenderTierRegistry.addListener(new ItemRenderTierRegistry.Listener() {
            @Override
            public void onClassification(ItemRenderTierRegistry.Classification classification) {
            }

            @Override
            public void onInvalidated(String reason) {
                invalidations.add(reason);
            }
        });

        ItemRenderTierRegistry.invalidateAll("legacy");

        Assert.assertEquals(1, invalidations.size());
    }

    static void makeUnrenderable(String key) {
        ItemRenderTierRegistry.classify(key, ItemRenderTierRegistry.Outcome.EXCEPTION, "boom");
        ItemRenderTierRegistry.classify(key, ItemRenderTierRegistry.Outcome.EXCEPTION, "boom");
        ItemRenderTierRegistry.classify(key, ItemRenderTierRegistry.Outcome.EXCEPTION, "boom");
    }
}
