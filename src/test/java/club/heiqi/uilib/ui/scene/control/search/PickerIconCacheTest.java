package club.heiqi.uilib.ui.scene.control.search;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.ui.editor.PickerIconSource;
import club.heiqi.config.ui.field.PickerSourceGuard;
import club.heiqi.uilib.ui.scene.image.ItemRenderTierRegistry;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;

/**
 * {@link PickerIconCache} 有界/失效/释放测试（判据 A-09 的图标缓存面）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §2.5/§5.5（A8：两层 LRU 512+1024；
 * 键 = {@code registryKey()} 原样值；失效 = 图标代际清空 + 分级代际按 key 重取；释放后仍可用）。</p>
 */
public class PickerIconCacheTest {

    @Before
    public void setUp() {
        ItemRenderTierRegistry.resetForTests();
        PickerSourceGuard.__resetForTests();
    }

    @After
    public void tearDown() {
        ItemRenderTierRegistry.resetForTests();
        PickerSourceGuard.__resetForTests();
    }

    /** 两层独立且各有上限：塞入上限 + K 个键后逐层仍 ≤ 上限。 */
    @Test
    public void layersAreBoundedIndependently() {
        PickerIconCache cache = new PickerIconCache(4, 8);
        CountingSource source = new CountingSource();
        for (int index = 0; index < 10; index++) {
            cache.candidateIcon("c" + index, source);
        }
        for (int index = 0; index < 20; index++) {
            cache.variantIcon("c" + index, "v" + index, source);
        }
        Assert.assertEquals("候选层上限", 4, cache.candidateSize());
        Assert.assertEquals("变体层上限", 8, cache.variantSize());
        Assert.assertTrue("两层键空间不得混用", cache.candidateSize() != cache.variantSize());
    }

    /** LRU 语义：最久未访问者先被淘汰，最近访问者保留。 */
    @Test
    public void lruEvictsLeastRecentlyUsed() {
        PickerIconCache cache = new PickerIconCache(2, 2);
        CountingSource source = new CountingSource();
        cache.candidateIcon("a", source);
        cache.candidateIcon("b", source);
        cache.candidateIcon("a", source); // 触碰 a → b 成为最久未访问
        cache.candidateIcon("c", source); // 触发淘汰 b

        CountingSource probe = new CountingSource();
        cache.candidateIcon("c", probe);
        Assert.assertEquals("最近插入的 c 必须命中", 0, probe.created);
        cache.candidateIcon("a", probe);
        Assert.assertEquals("被触碰过的 a 必须命中", 0, probe.created);
        cache.candidateIcon("b", probe);
        Assert.assertEquals("最久未访问的 b 必须已被淘汰并按 key 重取", 1, probe.created);
    }

    /** 命中计数：同键第二次不重建（Miner 侧不再自建无界 HashMap 的直接收益）。 */
    @Test
    public void repeatedRequestsHitCache() {
        PickerIconCache cache = new PickerIconCache();
        CountingSource source = new CountingSource();
        cache.candidateIcon("a", source);
        cache.candidateIcon("a", source);
        cache.variantIcon("a", "3", source);
        cache.variantIcon("a", "3", source);
        Assert.assertEquals("两次不同键各建一次", 2, source.created);
        Assert.assertEquals(2, cache.createdCount());
        Assert.assertEquals(2, cache.hitCount());
    }

    /** 无图（null）不缓存缺省结论，但不得污染命中计数。 */
    @Test
    public void nullIconsAreNotCached() {
        PickerIconCache cache = new PickerIconCache();
        CountingSource source = new CountingSource();
        source.returnNull = true;
        Assert.assertNull(cache.candidateIcon("a", source));
        Assert.assertNull(cache.candidateIcon("a", source));
        Assert.assertEquals("无图不缓存 → 每次重新询问源", 2, source.created);
        Assert.assertEquals(0, cache.candidateSize());
    }

    /** 图标代际失效：清空两层（资源包重载后按 key 重取）。 */
    @Test
    public void iconEpochInvalidationClearsBothLayers() {
        PickerIconCache cache = new PickerIconCache();
        CountingSource source = new CountingSource();
        cache.candidateIcon("a", source);
        cache.variantIcon("a", "3", source);
        cache.invalidateAll("resource_reload");
        Assert.assertEquals(0, cache.candidateSize());
        Assert.assertEquals(0, cache.variantSize());

        int before = source.created;
        cache.candidateIcon("a", source);
        Assert.assertEquals("失效后按 key 重取", before + 1, source.created);
    }

    /** 分级代际失效：只丢弃当前判为 UNRENDERABLE 的键，其余保留。 */
    @Test
    public void tierInvalidationDropsOnlyUnrenderableKeys() {
        PickerIconCache cache = new PickerIconCache();
        CountingSource source = new CountingSource();
        cache.candidateIcon("bad", source);
        cache.candidateIcon("good", source);
        for (int index = 0; index < 3; index++) {
            ItemRenderTierRegistry.classify("bad", ItemRenderTierRegistry.Outcome.EXCEPTION, "boom");
        }
        cache.onTierGenerationChanged(ItemRenderTierRegistry.tierGeneration());

        Assert.assertEquals("不可渲染键被丢弃", 1, cache.candidateSize());
        int before = source.created;
        cache.candidateIcon("good", source);
        Assert.assertEquals("可渲染键仍命中缓存", before, source.created);
        cache.candidateIcon("bad", source);
        Assert.assertEquals("不可渲染键按 key 重取", before + 1, source.created);
    }

    /** 释放：清空两层且释放后仍可用（不进坏态）。 */
    @Test
    public void releaseClearsAndStaysUsable() {
        PickerIconCache cache = new PickerIconCache();
        CountingSource source = new CountingSource();
        SceneImageSource first = cache.candidateIcon("a", source);
        cache.variantIcon("a", "3", source);
        cache.release();
        Assert.assertEquals(0, cache.candidateSize());
        Assert.assertEquals(0, cache.variantSize());

        SceneImageSource again = cache.candidateIcon("a", source);
        Assert.assertNotNull("释放后再次请求仍可用", again);
        Assert.assertNotSame(first, again);
        Assert.assertEquals(1, cache.candidateSize());
    }

    /** 容量非法立即失败（缓存必须有界是硬约束，不接受 0/负数）。 */
    @Test
    public void nonPositiveCapacityIsRejected() {
        try {
            new PickerIconCache(0, 1);
            Assert.fail("expected capacity rejection");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    /** 图标源替身：记录物化次数，可配置返回 null。 */
    private static final class CountingSource implements PickerIconSource {
        private int created;
        private boolean returnNull;

        @Override
        public SceneImageSource candidateIcon(String candidateKey) {
            created++;
            return returnNull ? null : new Marker(candidateKey);
        }

        @Override
        public SceneImageSource variantIcon(String candidateKey, String variantKeyOrNull) {
            created++;
            return returnNull ? null : new Marker(PickerIconKey.variant(candidateKey, variantKeyOrNull));
        }
    }

    /** 只带键标识的图片源替身。 */
    private static final class Marker implements SceneImageSource {
        private final String key;

        Marker(String key) {
            this.key = key;
        }

        @Override
        public String registryKey() {
            return key;
        }
    }
}
