package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.schema.SearchPickerSpec;
import club.heiqi.config.schema.ValueSpec;
import club.heiqi.config.ui.editor.CandidateSourceValueEditorProvider;
import club.heiqi.config.ui.editor.Codec;
import club.heiqi.config.ui.editor.PickerCandidateSource;
import club.heiqi.config.ui.editor.PickerQuery;
import club.heiqi.config.ui.editor.Registry;
import club.heiqi.config.ui.editor.SearchPickerCategories;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.ValueEditorProvider;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 字段侧 SPI 路径测试：查询式求值（browse 无上限 / 搜索 lane 上限与截断真值）、exact 成员解析、
 * 注册期只收引用不收候选、线程断言在新调用点生效。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.5（A5 截断取值链）、§1.7 D-8（exact 取代全表搜索）、
 * §3.7（浏览 lane 无上限 / 搜索 lane 64 上限且明示）、§1.4（注册期只固化惰性引用）。</p>
 */
public class SearchPickerFieldSupportSpiPathTest {

    private static final int SEARCH_MAX_ITEMS = 64;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        PickerSourceGuard.__resetForTests();
        PickerSourceLifecycle.__resetForTests();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
        PickerSourceGuard.__resetForTests();
        PickerSourceLifecycle.__resetForTests();
    }

    /**
     * 字段侧 SPI 路径「不打开不花钱」（ADR §4.1/§4.2）：接通惰性源并创建面板后，
     * 字段侧不得读取任何候选（size/matchCount/page）——候选读取只发生在面板打开后的窗口求值。
     *
     * <p>P4 前形态：字段侧结果 Computed 在装配期即按查询求值（空查询 = 全量分片物化），
     * 本断言即该形态的变异检查点。</p>
     */
    @Test
    public void spiPickerCreationTouchesNoCandidateSourceBeforeOpen() {
        final FakeSource source = new FakeSource(5000);
        Registry registry = new Registry();
        registry.register(spiProvider("test:spi", source), 64);
        registry.freeze();
        SceneRuntime runtime = new SceneRuntime(new FixedTextMeasurer(8, 16));
        try {
            Signal<Object> value = Signal.<Object>create("k1");
            runtime.mount(new SceneNode(), () -> SearchPickerFieldSupport.createControlledIfPresent(
                    runtime, ValueSpec.string().withWidget(new SearchPickerSpec("test:spi", 8)),
                    value, registry, value::set));
            runtime.flush();
            runtime.flush();

            Assert.assertEquals("装配/关闭态不得读取清单长度", 0, source.sizeCalls);
            Assert.assertEquals("装配/关闭态不得统计命中", 0, source.matchCountCalls);
            Assert.assertEquals("装配/关闭态不得物化分片", 0, source.pageCalls);
        } finally {
            runtime.dispose();
        }
    }

    /**
     * P3/T-6：查询条件携带受控分类维度/键——分类过滤是 source 的职责，面板侧 filterByCategory 在
     * SPI 路径关闭（避免对窗口切片二次过滤使 totalItems 失真）。
     */
    @Test
    public void queryForInjectsControlledCategoryDimensionAndKey() {
        SearchPickerFieldSupport.CategoryQueryState state = new SearchPickerFieldSupport.CategoryQueryState();
        // 未注入（无分组 provider / 注入前）：维度 0、分类键 null（= 不做分类过滤）——与旧路径同语义
        PickerQuery plain = SearchPickerFieldSupport.queryFor(" Stone ", state);
        Assert.assertEquals(0, plain.categoryDimension());
        Assert.assertTrue("未注入分类键 = 不做分类过滤（null 折叠为空串）", plain.categoryKey().isEmpty());
        Assert.assertEquals("归一化由 PickerQuery 负责", "stone", plain.normalizedText());

        Signal<Integer> dimension = Signal.create(Integer.valueOf(1));
        Signal<String> categoryKey = Signal.create("cat1");
        state.dimension = dimension;
        state.categoryKey = categoryKey;
        PickerQuery filtered = SearchPickerFieldSupport.queryFor("", state);
        Assert.assertTrue("空文本 = 浏览 lane（无上限）", filtered.isBrowse());
        Assert.assertEquals(1, filtered.categoryDimension());
        Assert.assertEquals("cat1", filtered.categoryKey());

        categoryKey.set("cat2");
        ReactiveScheduler.get().flush();
        Assert.assertEquals("分类切换立即反映到查询条件", "cat2",
                SearchPickerFieldSupport.queryFor("", state).categoryKey());
        dimension.set(Integer.valueOf(-5));
        ReactiveScheduler.get().flush();
        Assert.assertEquals("负维度收敛为 0（PickerQuery 拒绝负维度）",
                0, SearchPickerFieldSupport.queryFor("", state).categoryDimension());
    }

    /** 成员解析走 exact：每个唯一 key 恰好一次，未命中保留 unknown（enumerated=false）。 */
    @Test
    public void memberResolutionUsesExactOncePerUniqueKey() {
        FakeSource source = new FakeSource(200);
        List<SearchPickerData.CurrentMember> decoded = Arrays.asList(
                member(1L, "k1"), member(2L, "k1"), member(3L, "missing"), member(4L, null));

        List<SearchPickerData.CurrentMember> resolved =
                SearchPickerFieldSupport.resolveCurrentMembers(decoded, source);

        Assert.assertEquals("每个唯一 key 一次 exact（k1 重复只查一次）", 2, source.exactCalls);
        Assert.assertTrue(resolved.get(0).enumerated());
        Assert.assertEquals("k1", resolved.get(0).candidate().key());
        Assert.assertTrue(resolved.get(1).enumerated());
        Assert.assertFalse("未命中保留 unknown", resolved.get(2).enumerated());
        Assert.assertNull(resolved.get(2).candidate());
        Assert.assertNull(resolved.get(3).selection());
        Assert.assertEquals("成员解析不得触碰分片", 0, source.pageCalls);
    }

    /** SPI 抛出异常（含线程断言）只让该成员退化为 unknown，不污染其它成员。 */
    @Test
    public void exactFailureDegradesSingleMemberOnly() {
        FakeSource source = new FakeSource(200);
        source.exactFailureKey = "k2";
        List<SearchPickerData.CurrentMember> decoded = Arrays.asList(
                member(1L, "k1"), member(2L, "k2"), member(3L, "k3"));

        List<SearchPickerData.CurrentMember> resolved =
                SearchPickerFieldSupport.resolveCurrentMembers(decoded, source);

        Assert.assertTrue(resolved.get(0).enumerated());
        Assert.assertFalse(resolved.get(1).enumerated());
        Assert.assertTrue(resolved.get(2).enumerated());
    }

    /** 注册期只固化「引用 + 一个 int」：register 不触发任何候选读取（A-05 的 UILib 侧证据）。 */
    @Test
    public void registrationCapturesReferenceWithoutTouchingCandidates() {
        FakeSource source = new FakeSource(5000);
        Registry registry = new Registry();
        registry.register(spiProvider("test:spi", source), 128);
        registry.freeze();

        Assert.assertEquals("注册不得读取清单长度", 0, source.sizeCalls);
        Assert.assertEquals("注册不得触发分片", 0, source.pageCalls);
        Assert.assertEquals("注册不得触发命中统计", 0, source.matchCountCalls);

        CandidateSourceValueEditorProvider registered =
                (CandidateSourceValueEditorProvider) registry.find("test:spi");
        Assert.assertSame("注册期冻结的必须是惰性 source 的引用", source, registered.candidateSource());
        Assert.assertEquals("装配层传入的搜索窗口被收进注册快照", 128, registered.searchMaxItems());
    }

    /**
     * 会话释放账本的<b>接线</b>守卫（U-B1 闭合）：登记点必须是真实字段侧接线
     * （{@code SearchPickerFieldSupport#candidateSourceOf}），断连路径的
     * {@code PickerSourceLifecycle.releaseAll} 才能释放到生产的源 —— 而不是只有测试自己登记过的源。
     */
    @Test
    public void fieldWiringRegistersSourceSoSessionReleaseReachesIt() {
        FakeSource source = new FakeSource(3);
        Registry registry = new Registry();
        registry.register(spiProvider("test:spi", source), 64);
        registry.freeze();
        SceneRuntime runtime = new SceneRuntime(new FixedTextMeasurer(8, 16));
        try {
            Signal<Object> value = Signal.<Object>create("k1");
            runtime.mount(new SceneNode(), () -> SearchPickerFieldSupport.createControlledIfPresent(
                    runtime, ValueSpec.string().withWidget(new SearchPickerSpec("test:spi", 8)),
                    value, registry, value::set));
            runtime.flush();

            Assert.assertEquals("字段侧接线必须把源登记进会话账本（无登记 = release 永不被调用）",
                    1, PickerSourceLifecycle.trackedCount());
            Assert.assertEquals("登记不得触碰候选（注册期零读取语义不因账本改变）", 0, source.sizeCalls);
        } finally {
            runtime.dispose();
        }

        Assert.assertEquals("关屏（屏级 Owner 释放）不得释放进程级常驻源 —— SPI javadoc：不在每次关屏调用",
                0, source.releaseCalls);
        Assert.assertEquals("会话释放必须命中经真实接线登记的源",
                1, PickerSourceLifecycle.releaseAll("client_disconnect"));
        Assert.assertEquals("release 恰一次", 1, source.releaseCalls);
    }

    /** 未传窗口时沿用 provider 自报值；非法窗口立即失败。 */
    @Test
    public void registrationUsesProviderValueAndRejectsIllegalWindow() {
        Registry registry = new Registry();
        registry.register(spiProvider("test:spi", new FakeSource(1)));
        registry.freeze();
        CandidateSourceValueEditorProvider registered =
                (CandidateSourceValueEditorProvider) registry.find("test:spi");
        Assert.assertEquals(CandidateSourceValueEditorProvider.DEFAULT_SEARCH_MAX_ITEMS, registered.searchMaxItems());

        Registry invalid = new Registry();
        try {
            invalid.register(spiProvider("test:bad", new FakeSource(1)), 0);
            Assert.fail("expected non-positive window rejection");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("searchMaxItems"));
        }
    }

    /** 两条默认值来源必须同值（spec 是权威，SPI 常量只是缺省；漂移即红）。 */
    @Test
    public void specAndSpiDefaultsAgree() {
        Assert.assertEquals(SearchPickerSpec.DEFAULT_MAX_ITEMS,
                CandidateSourceValueEditorProvider.DEFAULT_SEARCH_MAX_ITEMS);
    }

    // ==================== 夹具 ====================

    private static CandidateSourceValueEditorProvider spiProvider(String id, final PickerCandidateSource source) {
        return new CandidateSourceValueEditorProvider() {
            @Override
            public PickerCandidateSource candidateSource() {
                return source;
            }

            @Override
            public String id() {
                return id;
            }

            @Override
            public Codec codec() {
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

            @Override
            public VisualAdapter visualAdapter() {
                return new VisualAdapter() {
                    @Override
                    public String candidateLabel(SearchPickerData.Candidate candidate) {
                        return candidate.label();
                    }

                    @Override
                    public String variantLabel(SearchPickerData.Variant variant) {
                        return variant.label();
                    }
                };
            }

            @Override
            public ValueEditorProvider.SearchFunction searchFunction() {
                return (query, maxResults) -> SearchPickerData.SearchResult.empty();
            }
        };
    }

    private static SearchPickerData.CurrentMember member(long id, String key) {
        SearchPickerData.Selection selection = key == null ? null
                : new SearchPickerData.Selection(key, SearchPickerData.SelectionMode.ALL,
                        Collections.<String>emptyList());
        return new SearchPickerData.CurrentMember(id, selection, null, false);
    }

    /** 内存候选源替身：记录调用次数并暴露最近一次 page 窗口。 */
    private static final class FakeSource implements PickerCandidateSource {
        private final int total;
        private int sizeCalls;
        private int pageCalls;
        private int matchCountCalls;
        private int exactCalls;
        private int releaseCalls;
        private int lastPageOffset = -1;
        private int lastPageLimit = -1;
        private boolean duplicateFirst;
        private String exactFailureKey;

        FakeSource(int total) {
            this.total = total;
        }

        @Override
        public int size() {
            sizeCalls++;
            return total;
        }

        @Override
        public long registryRevision() {
            return 0L;
        }

        @Override
        public long nameRevision() {
            return 0L;
        }

        @Override
        public long iconRevision() {
            return 0L;
        }

        @Override
        public int matchCount(PickerQuery query) {
            matchCountCalls++;
            return total;
        }

        @Override
        public List<SearchPickerData.Candidate> page(PickerQuery query, int offset, int limit) {
            pageCalls++;
            lastPageOffset = offset;
            lastPageLimit = limit;
            ArrayList<SearchPickerData.Candidate> out = new ArrayList<SearchPickerData.Candidate>();
            for (int index = offset; index < Math.min(total, offset + limit); index++) {
                out.add(candidate("k" + (index + 1)));
            }
            if (duplicateFirst && !out.isEmpty()) {
                out.add(candidate("k1"));
            }
            return out;
        }

        @Override
        public SearchPickerData.Candidate exact(String candidateKey) {
            exactCalls++;
            if (candidateKey.equals(exactFailureKey)) {
                throw new IllegalStateException("host failure");
            }
            if (candidateKey.startsWith("k")) {
                return candidate(candidateKey);
            }
            return null;
        }

        @Override
        public List<SearchPickerCategories.Category> categories(int dimension) {
            return Collections.emptyList();
        }

        @Override
        public void release() {
            releaseCalls++;
        }

        private static SearchPickerData.Candidate candidate(String key) {
            return new SearchPickerData.Candidate(key, key, Collections.<SearchPickerData.Variant>emptyList());
        }
    }
}
