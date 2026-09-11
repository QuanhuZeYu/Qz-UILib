package club.heiqi.config.ui.field;

import java.util.Collections;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.ui.editor.CandidateSourceValueEditorProvider;
import club.heiqi.config.ui.editor.Codec;
import club.heiqi.config.ui.editor.PickerIconSource;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.ValueEditorProvider;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.resource.ResourceReloadService;
import club.heiqi.uilib.ui.scene.control.search.PickerIconKey;
import club.heiqi.uilib.ui.scene.image.ItemRenderTierRegistry;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;

/**
 * {@link PickerIconResolver} 适配测试：候选图标走图标源 + 有界缓存；文本与变体图标原样委托。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.2 A8/§5.5。</p>
 */
public class PickerIconResolverTest {

    @Before
    public void setUp() {
        ItemRenderTierRegistry.resetForTests();
        ResourceReloadService.getInstance().__resetForTests();
        PickerSourceGuard.__resetForTests();
    }

    @After
    public void tearDown() {
        ItemRenderTierRegistry.resetForTests();
        ResourceReloadService.getInstance().__resetForTests();
        PickerSourceGuard.__resetForTests();
    }

    /** 候选图标走图标源并命中缓存；文本与变体图标仍来自 provider 自己的适配器。 */
    @Test
    public void candidateImagesGoThroughIconSourceAndCache() {
        DelegateAdapter delegate = new DelegateAdapter();
        CountingIconSource iconSource = new CountingIconSource();
        PickerIconResolver resolver = new PickerIconResolver(delegate, iconSource);

        SearchPickerData.Candidate candidate = candidate("minecraft:stone");
        SceneImageSource first = resolver.candidateImage(candidate);
        SceneImageSource second = resolver.candidateImage(candidate);
        Assert.assertSame("第二次必须命中缓存", first, second);
        Assert.assertEquals(1, iconSource.candidateCalls);
        Assert.assertEquals("键 = PickerIconKey.candidate(key)", "minecraft:stone", first.registryKey());
        Assert.assertEquals(1, resolver.cache().createdCount());
        Assert.assertEquals(1, resolver.cache().hitCount());

        Assert.assertEquals("标签委托", "label:minecraft:stone", resolver.candidateLabel(candidate));
        Assert.assertEquals("变体标签委托", "variant-label", resolver.variantLabel(variant("3")));
        Assert.assertSame("变体图标本阶段委托（无候选上下文）", delegate.variantImage, resolver.variantImage(variant("3")));
    }

    /** 资源代际变化：取图标时清空缓存（按 key 重取）。 */
    @Test
    public void resourceEpochChangeClearsCache() {
        PickerIconResolver resolver = new PickerIconResolver(new DelegateAdapter(), new CountingIconSource());
        resolver.candidateImage(candidate("a"));
        Assert.assertEquals(1, resolver.cache().candidateSize());

        ResourceReloadService.getInstance().onResourceManagerReload(null);
        resolver.candidateImage(candidate("a"));
        Assert.assertEquals("失效后只保留本次重取的条目", 1, resolver.cache().candidateSize());
        Assert.assertEquals("重取发生（created=2）", 2, resolver.cache().createdCount());
    }

    /**
     * 分级代际推进（invalidateAll 后）：可渲染键的缓存条目保持，不因分级代际而重取图标内容
     * （tier 代际只驱动回退集合重派生；不可渲染键的条目丢弃由 {@code PickerIconCache} 自测覆盖）。
     */
    @Test
    public void tierGenerationAdvanceKeepsRenderableEntries() {
        PickerIconResolver resolver = new PickerIconResolver(new DelegateAdapter(), new CountingIconSource());
        resolver.candidateImage(candidate("bad"));
        resolver.candidateImage(candidate("good"));
        for (int index = 0; index < 3; index++) {
            ItemRenderTierRegistry.classify("bad", ItemRenderTierRegistry.Outcome.EXCEPTION, "boom");
        }
        long createdBefore = resolver.cache().createdCount();
        ItemRenderTierRegistry.invalidateAll("test");

        resolver.candidateImage(candidate("good"));
        Assert.assertEquals("可渲染键仍命中缓存", createdBefore, resolver.cache().createdCount());
        Assert.assertEquals(2, resolver.cache().candidateSize());
    }

    /** release() 清空缓存（屏级释放链）。 */
    @Test
    public void releaseClearsCache() {
        PickerIconResolver resolver = new PickerIconResolver(new DelegateAdapter(), new CountingIconSource());
        resolver.candidateImage(candidate("a"));
        resolver.release();
        Assert.assertEquals(0, resolver.cache().candidateSize());
        Assert.assertNotNull("释放后仍可用", resolver.candidateImage(candidate("a")));
    }

    /**
     * P6/U-B1：屏级释放链上的 release 可重复调用（幂等），且释放后再次取图标必须重建缓存
     * （「释放后再次打开可重建」，SPI/缓存契约：释放不进坏态）。
     */
    @Test
    public void repeatedReleaseIsIdempotentAndRebuildsCacheOnNextRequest() {
        CountingIconSource iconSource = new CountingIconSource();
        PickerIconResolver resolver = new PickerIconResolver(new DelegateAdapter(), iconSource);
        resolver.candidateImage(candidate("a"));
        long createdAfterFirstFill = resolver.cache().createdCount();
        Assert.assertEquals("首次取值：图标源调用 1 次", 1, iconSource.candidateCalls);

        resolver.release();
        resolver.release();

        Assert.assertEquals("重复释放幂等：缓存仍为空", 0, resolver.cache().candidateSize());
        Assert.assertEquals("重复释放不新建图标源", 1, iconSource.candidateCalls);

        Assert.assertNotNull("释放后再次打开仍可用", resolver.candidateImage(candidate("a")));
        Assert.assertEquals("重建后缓存恰 1 条", 1, resolver.cache().candidateSize());
        Assert.assertEquals("重建 = 图标源再调用一次", 2, iconSource.candidateCalls);
        Assert.assertEquals("重建生成新的图标源实例", createdAfterFirstFill + 1, resolver.cache().createdCount());
    }

    /** 未实现 SPI 或无图标源时 of() 返回 null（保持原适配器，零行为变化）。 */
    @Test
    public void ofReturnsNullWithoutIconSource() {
        Assert.assertNull("未实现 SPI", PickerIconResolver.of(legacyProvider()));
        Assert.assertNull("实现 SPI 但无图标源", PickerIconResolver.of(spiProvider(null)));
        Assert.assertNotNull("实现 SPI 且给出图标源", PickerIconResolver.of(spiProvider(new CountingIconSource())));
    }

    // ==================== 夹具 ====================

    private static SearchPickerData.Candidate candidate(String key) {
        return new SearchPickerData.Candidate(key, "label:" + key, Collections.<SearchPickerData.Variant>emptyList());
    }

    private static SearchPickerData.Variant variant(String key) {
        return new SearchPickerData.Variant(key, "variant-label");
    }

    private static class DelegateAdapter implements VisualAdapter {
        private final SceneImageSource variantImage = new Marker("delegate-variant");

        @Override
        public SceneImageSource variantImage(SearchPickerData.Variant variant) {
            return variantImage;
        }

        @Override
        public String candidateLabel(SearchPickerData.Candidate candidate) {
            return "label:" + candidate.key();
        }

        @Override
        public String variantLabel(SearchPickerData.Variant variant) {
            return "variant-label";
        }
    }

    private static final class CountingIconSource implements PickerIconSource {
        private int candidateCalls;

        @Override
        public SceneImageSource candidateIcon(String candidateKey) {
            candidateCalls++;
            return new Marker(PickerIconKey.candidate(candidateKey));
        }

        @Override
        public SceneImageSource variantIcon(String candidateKey, String variantKeyOrNull) {
            return new Marker(PickerIconKey.variant(candidateKey, variantKeyOrNull));
        }
    }

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

    private static Codec codec() {
        return new Codec() {
            @Override
            public SearchPickerData.Selection decode(Object raw) {
                return null;
            }

            @Override
            public Object encode(Object current, SearchPickerData.Selection selected) {
                return null;
            }
        };
    }

    private static VisualAdapter adapter() {
        return new DelegateAdapter();
    }

    private static ValueEditorProvider legacyProvider() {
        return new ValueEditorProvider() {
            @Override
            public String id() {
                return "test:legacy";
            }

            @Override
            public Codec codec() {
                return codec();
            }

            @Override
            public VisualAdapter visualAdapter() {
                return adapter();
            }

            @Override
            public SearchFunction searchFunction() {
                return (query, maxResults) -> SearchPickerData.SearchResult.empty();
            }
        };
    }

    private static CandidateSourceValueEditorProvider spiProvider(final PickerIconSource iconSource) {
        return new CandidateSourceValueEditorProvider() {
            @Override
            public PickerIconSource iconSource() {
                return iconSource;
            }

            @Override
            public String id() {
                return "test:spi";
            }

            @Override
            public Codec codec() {
                return codec();
            }

            @Override
            public VisualAdapter visualAdapter() {
                return adapter();
            }

            @Override
            public ValueEditorProvider.SearchFunction searchFunction() {
                return (query, maxResults) -> SearchPickerData.SearchResult.empty();
            }
        };
    }
}
