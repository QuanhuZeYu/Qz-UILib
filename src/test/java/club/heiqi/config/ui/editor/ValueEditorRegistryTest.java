package club.heiqi.config.ui.editor;

import org.junit.Test;

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
        assertSame(SearchPickerData.SearchResult.empty(), provider.search("stone", 8));
        assertNull(provider.visualAdapter().candidateImage(null));
        assertNull(provider.visualAdapter().variantImage(null));
        expectFailure(new Runnable() { public void run() { registry.register(provider("qzuilib:other")); } });
    }


    /** 注册快照固定 codec、visual 与可变 provider 当时的搜索目标。 */
    @Test
    public void registrationFreezesProviderContract() {
        MutableProvider provider = new MutableProvider("qzuilib:mutable");
        Registry registry = new Registry();
        registry.register(provider);
        registry.freeze();
        ValueEditorProvider registered = registry.find("qzuilib:mutable");
        Codec codec = registered.codec();
        VisualAdapter visual = registered.visualAdapter();

        provider.codec = passthroughCodec();
        provider.visual = labelAdapter("changed-");
        provider.searchTarget = query -> result("changed");

        assertSame(codec, registered.codec());
        assertSame(visual, registered.visualAdapter());
        assertEquals("initial", registered.search("", 8).candidates().get(0).key());
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

        private MutableProvider(String id) { this.id = id; }
        public String id() { return id; }
        public Codec codec() { return codec; }
        public VisualAdapter visualAdapter() { return visual; }
        public SearchPickerData.SearchResult search(String query, int maxResults) {
            return searchTarget.search(query);
        }
        public SearchFunction searchFunction() {
            final SearchTarget frozenTarget = searchTarget;
            return (query, maxResults) -> frozenTarget.search(query);
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
