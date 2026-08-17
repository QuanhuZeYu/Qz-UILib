package club.heiqi.config.ui.editor;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/** ValueEditorRegistry 生命周期与冲突测试。 */
public class ValueEditorRegistryTest {
    /** 缺失查询为空，冻结后仍可查询但不可注册。 */
    @Test
    public void freezePreventsFurtherRegistration() {
        Registry registry = new Registry();
        ValueEditorProvider provider = provider("qzuilib:item");
        assertNull(registry.find("missing:id"));
        registry.register(provider);
        registry.freeze();
        registry.freeze();

        assertTrue(registry.isFrozen());
        assertNotSame(provider, registry.find("qzuilib:item"));
        assertSame(SearchPickerData.SearchResult.empty(), provider.searchFunction().search("stone", 8));
        assertNull(provider.visualAdapter().candidateImage(null));
        assertNull(provider.visualAdapter().variantImage(null));
        expectFailure(new Runnable() { public void run() { registry.register(provider("qzuilib:other")); } });
    }


    /** searchFunction 必须由 provider 显式实现，旧 search 回退路径不再属于 API。 */
    @Test
    public void searchFunctionHasNoDefaultOrLegacySearchPath() throws Exception {
        Method searchFunction = ValueEditorProvider.class.getMethod("searchFunction");
        assertFalse(searchFunction.isDefault());
        try {
            ValueEditorProvider.class.getMethod("search", String.class, Integer.TYPE);
            fail("legacy provider search path must not exist");
        } catch (NoSuchMethodException expected) {
            // expected
        }
    }

    /** 注册快照固定 codec、visual 与显式函数捕获的不可变搜索目标。 */
    @Test
    public void registrationFreezesProviderContract() {
        MutableProvider provider = new MutableProvider("qzuilib:mutable");
        Registry registry = new Registry();
        registry.register(provider);
        registry.freeze();
        ValueEditorProvider registered = registry.find("qzuilib:mutable");
        Codec codec = registered.codec();
        VisualAdapter visual = registered.visualAdapter();
        SearchPickerPresentation presentation = registered.presentation();
        SearchPickerPanelPresentation panelPresentation = registered.panelPresentation();

        provider.codec = passthroughCodec();
        provider.visual = labelAdapter("changed-");
        provider.searchTarget = query -> result("changed");
        provider.failSearchFunctionReads = true;
        provider.presentation = SearchPickerPresentation.builder().title("Changed").build();
        provider.panelPresentation = SearchPickerPanelPresentation.builder().panelTitle("Changed panel").build();

        assertSame(codec, registered.codec());
        assertSame(visual, registered.visualAdapter());
        assertSame(presentation, registered.presentation());
        assertSame(panelPresentation, registered.panelPresentation());
        assertEquals("Initial", registered.presentation().title());
        assertEquals("Initial panel", registered.panelPresentation().panelTitle());
        assertEquals("initial", registered.searchFunction().search("", 8).candidates().get(0).key());
        assertEquals(1, provider.searchFunctionReads);
    }

    /** 未覆盖 panelPresentation 的 provider 在注册快照中取英文默认值。 */
    @Test
    public void panelPresentationDefaultsToEnglish() {
        Registry registry = new Registry();
        registry.register(provider("qzuilib:item"));
        ValueEditorProvider registered = registry.find("qzuilib:item");
        assertSame(SearchPickerPanelPresentation.defaultEnglish(), registered.panelPresentation());
    }

    /** 注册快照冻结多维度分组契约：维度数取值冻结，维度 overload 委托注册时捕获的函数。 */
    @Test
    public void registrationFreezesDimensionSnapshot() {
        MutableCategorizedProvider provider = new MutableCategorizedProvider();
        Registry registry = new Registry();
        registry.register(provider);
        registry.register(provider("qzuilib:item"));
        registry.freeze();
        CategorizedValueEditorProvider registered =
                (CategorizedValueEditorProvider) registry.find("qzuilib:multi");
        assertEquals(2, registered.categoryDimensionCount());
        assertEquals("d0-a", registered.categories(0).get(0).key());
        assertEquals("d1-a", registered.categories(1).get(0).key());
        assertTrue(registered.categories(3).isEmpty());
        assertEquals("c0", registered.categoryOf(0, "any"));
        assertEquals("c1", registered.categoryOf(1, "any"));
        assertNull(registered.categoryOf(3, "any"));
        CategorizedValueEditorProvider plain =
                (CategorizedValueEditorProvider) registry.find("qzuilib:item");
        assertEquals("未实现分组契约的 provider 快照维度数退化为 0", 0, plain.categoryDimensionCount());
        assertNull(plain.categoryOf(0, "any"));
        try {
            registered.categories(-1);
            fail("negative dimension must be rejected on snapshot");
        } catch (IllegalArgumentException expected) { }

        provider.dimensionCount = 1;
        assertEquals("维度数必须在注册时冻结", 2, registered.categoryDimensionCount());
    }

    /** 初始搜索函数为 null 时在注册点拒绝，不能留下半合法注册项。 */
    @Test
    public void nullSearchFunctionFailsRegistration() {
        MutableProvider provider = new MutableProvider("qzuilib:null-search");
        provider.searchTarget = null;
        Registry registry = new Registry();

        expectFailure(new Runnable() { public void run() { registry.register(provider); } });
        assertNull(registry.find("qzuilib:null-search"));
    }

    /** 重复与空 id 在注册点 fail-fast。 */
    @Test
    public void duplicateAndEmptyIdsFailFast() {
        final Registry registry = new Registry();
        registry.register(provider("qzuilib:item"));
        expectFailure(new Runnable() { public void run() { registry.register(provider("qzuilib:item")); } });
        expectFailure(new Runnable() { public void run() { registry.register(provider("")); } });
    }

    private static ValueEditorProvider provider(final String id) {
        return new ValueEditorProvider() {
            public String id() { return id; }
            public Codec codec() {
                return new Codec() {
                    public SearchPickerData.Selection decode(Object value) { return (SearchPickerData.Selection) value; }
                    public Object encode(SearchPickerData.Selection selection) { return selection; }
                };
            }
            public VisualAdapter visualAdapter() {
                return new VisualAdapter() {
                    public String candidateLabel(SearchPickerData.Candidate candidate) { return candidate.label(); }
                    public String variantLabel(SearchPickerData.Variant variant) { return variant.label(); }
                };
            }
            public SearchFunction searchFunction() {
                return (query, maxResults) -> SearchPickerData.SearchResult.empty();
            }
        };
    }

    private interface SearchTarget {
        SearchPickerData.SearchResult search(String query);
    }

    private static final class MutableProvider implements ValueEditorProvider {
        private final String id;
        private Codec codec = passthroughCodec();
        private VisualAdapter visual = labelAdapter("");
        private SearchTarget searchTarget = query -> result("initial");
        private int searchFunctionReads;
        private boolean failSearchFunctionReads;
        private SearchPickerPresentation presentation = SearchPickerPresentation.builder().title("Initial").build();
        private SearchPickerPanelPresentation panelPresentation =
                SearchPickerPanelPresentation.builder().panelTitle("Initial panel").build();

        private MutableProvider(String id) { this.id = id; }
        public String id() { return id; }
        public Codec codec() { return codec; }
        public VisualAdapter visualAdapter() { return visual; }
        public SearchFunction searchFunction() {
            searchFunctionReads++;
            if (failSearchFunctionReads) throw new IllegalStateException("provider getter must not be read after registration");
            final SearchTarget frozenTarget = searchTarget;
            return frozenTarget == null ? null : (query, maxResults) -> frozenTarget.search(query);
        }
        public SearchPickerPresentation presentation() { return presentation; }
        public SearchPickerPanelPresentation panelPresentation() { return panelPresentation; }
    }

    /** 多维度分组 provider：注册后可变更维度数与分类数据。 */
    private static final class MutableCategorizedProvider implements CategorizedValueEditorProvider {
        private final Codec codec = passthroughCodec();
        private final VisualAdapter visual = labelAdapter("");
        private int dimensionCount = 2;
        private java.util.List<SearchPickerCategories.Category> d0Categories = java.util.Collections.singletonList(
                new SearchPickerCategories.Category("d0-a", "D0 A"));
        private java.util.List<SearchPickerCategories.Category> d1Categories = java.util.Collections.singletonList(
                new SearchPickerCategories.Category("d1-a", "D1 A"));
        private String categoryOf0 = "c0";
        private String categoryOf1 = "c1";

        public String id() { return "qzuilib:multi"; }
        public Codec codec() { return codec; }
        public VisualAdapter visualAdapter() { return visual; }
        public SearchFunction searchFunction() {
            return (query, maxResults) -> SearchPickerData.SearchResult.empty();
        }
        public int categoryDimensionCount() { return dimensionCount; }
        public java.util.List<SearchPickerCategories.Category> categories(int dimension) {
            if (dimension == 0) return d0Categories;
            if (dimension == 1) return d1Categories;
            return java.util.Collections.emptyList();
        }
        public String categoryOf(int dimension, String candidateKey) {
            if (dimension == 0) return categoryOf0;
            if (dimension == 1) return categoryOf1;
            return null;
        }
    }

    private static Codec passthroughCodec() {
        return new Codec() {
            public SearchPickerData.Selection decode(Object value) { return (SearchPickerData.Selection) value; }
            public Object encode(SearchPickerData.Selection selection) { return selection; }
        };
    }

    private static VisualAdapter labelAdapter(final String prefix) {
        return new VisualAdapter() {
            public String candidateLabel(SearchPickerData.Candidate candidate) { return prefix + candidate.label(); }
            public String variantLabel(SearchPickerData.Variant variant) { return prefix + variant.label(); }
        };
    }

    private static SearchPickerData.SearchResult result(String key) {
        return new SearchPickerData.SearchResult(java.util.Collections.singletonList(
                new SearchPickerData.Candidate(key, key, java.util.Collections.<SearchPickerData.Variant>emptyList())));
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            fail("expected registration failure");
        } catch (IllegalArgumentException expected) {
            // expected
        } catch (IllegalStateException expected) {
            // expected
        }
    }
}
