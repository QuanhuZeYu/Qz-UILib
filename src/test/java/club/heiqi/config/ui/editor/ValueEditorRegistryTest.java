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
