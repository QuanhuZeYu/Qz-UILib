package club.heiqi.config.ui.editor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link SearchPickerCategories} 与 {@link CategorizedValueEditorProvider} 分组契约测试。
 *
 * <p>覆盖：Category 不可变与校验、Classifier 函数式语义、静态工具（find/contains/immutableCopy）、
 * 新 provider 接口 default 方法缺省值，以及旧 {@link ValueEditorProvider} 实现零改动的编译级兼容
 * （匿名实现只满足 ValueEditorProvider 即可）。</p>
 */
public class SearchPickerCategoriesTest {

    // ==================== Category ====================

    @Test
    public void categoryImmutablyHoldsKeyLabelAndCount() {
        SearchPickerCategories.Category category = new SearchPickerCategories.Category("tabs.blocks", "Blocks", 42);
        Assert.assertEquals("tabs.blocks", category.key());
        Assert.assertEquals("Blocks", category.label());
        Assert.assertEquals(42, category.count());
        SearchPickerCategories.Category unknown = new SearchPickerCategories.Category("mods.foo", "Foo");
        Assert.assertEquals(-1, unknown.count());
    }

    @Test
    public void categoryRejectsInvalidInputs() {
        assertCategoryFails(null, "Blocks", 0);
        assertCategoryFails("", "Blocks", 0);
        assertCategoryFails("k", null, 0);
        assertCategoryFails("k", "", 0);
        assertCategoryFails("k", "label", -2);
    }

    @Test
    public void categoryWithCountDerivesEquivalentSnapshot() {
        SearchPickerCategories.Category category = new SearchPickerCategories.Category("a", "A", 1);
        SearchPickerCategories.Category derived = category.withCount(7);
        Assert.assertEquals("a", derived.key());
        Assert.assertEquals("A", derived.label());
        Assert.assertEquals(7, derived.count());
        Assert.assertEquals(1, category.count());
        Assert.assertEquals("原快照不受派生影响", 1, category.count());
    }

    @Test
    public void categoryEqualityIsValueBased() {
        Assert.assertEquals(new SearchPickerCategories.Category("a", "A", 1),
                new SearchPickerCategories.Category("a", "A", 1));
        Assert.assertNotEquals(new SearchPickerCategories.Category("a", "A", 1),
                new SearchPickerCategories.Category("a", "A", 2));
        Assert.assertNotEquals(new SearchPickerCategories.Category("a", "A", 1),
                new SearchPickerCategories.Category("b", "A", 1));
    }

    // ==================== Classifier / 静态工具 ====================

    @Test
    public void classifierIsFunctionalAndNullable() {
        SearchPickerCategories.Classifier classifier = key -> key.startsWith("minecraft:")
                ? "vanilla" : null;
        Assert.assertEquals("vanilla", classifier.categoryKeyOf("minecraft:stone"));
        Assert.assertNull(classifier.categoryKeyOf("thermal:copper"));
    }

    @Test
    public void immutableCopyDefendsAgainstNulls() {
        List<SearchPickerCategories.Category> copy = SearchPickerCategories.immutableCopy(
                Arrays.asList(new SearchPickerCategories.Category("a", "A"),
                        new SearchPickerCategories.Category("b", "B")));
        Assert.assertEquals(2, copy.size());
        try {
            copy.add(new SearchPickerCategories.Category("c", "C"));
            Assert.fail("copy must be immutable");
        } catch (UnsupportedOperationException expected) { }
        try {
            SearchPickerCategories.immutableCopy(Collections.<SearchPickerCategories.Category>singletonList(null));
            Assert.fail("null element must be rejected");
        } catch (IllegalArgumentException expected) { }
        Assert.assertTrue(SearchPickerCategories.immutableCopy(null).isEmpty());
    }

    @Test
    public void findAndContainsResolveByKey() {
        List<SearchPickerCategories.Category> categories = Arrays.asList(
                new SearchPickerCategories.Category("a", "A"), new SearchPickerCategories.Category("b", "B"));
        Assert.assertEquals("A", SearchPickerCategories.find(categories, "a").label());
        Assert.assertNull(SearchPickerCategories.find(categories, "missing"));
        Assert.assertNull(SearchPickerCategories.find(null, "a"));
        Assert.assertTrue(SearchPickerCategories.contains(categories, "b"));
        Assert.assertFalse(SearchPickerCategories.contains(categories, "missing"));
    }

    // ==================== CategorizedValueEditorProvider 纯加法兼容 ====================

    /** 旧 provider 实现：只实现 ValueEditorProvider，不感知分组。 */
    private static final ValueEditorProvider LEGACY_PROVIDER = new ValueEditorProvider() {
        @Override
        public SearchFunction searchFunction() {
            return (query, maxResults) -> SearchPickerData.SearchResult.empty();
        }

        @Override
        public String id() {
            return "legacy:editor";
        }

        @Override
        public Codec codec() {
            return new Codec() {
                @Override
                public SearchPickerData.Selection decode(Object value) { return null; }

                @Override
                public Object encode(Object current, SearchPickerData.Selection selection) { return null; }
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
    };

    @Test
    public void legacyProviderCompilesWithoutGrouping() {
        Assert.assertFalse("旧 provider 无需实现分组接口",
                LEGACY_PROVIDER instanceof CategorizedValueEditorProvider);
        Assert.assertEquals("legacy:editor", LEGACY_PROVIDER.id());
    }

    @Test
    public void groupingProviderDefaultsToUngrouped() {
        CategorizedValueEditorProvider provider = new CategorizedValueEditorProvider() {
            @Override
            public SearchFunction searchFunction() {
                return (query, maxResults) -> SearchPickerData.SearchResult.empty();
            }

            @Override
            public String id() {
                return "grouped:editor";
            }

            @Override
            public Codec codec() {
                return new Codec() {
                    @Override
                    public SearchPickerData.Selection decode(Object value) { return null; }

                    @Override
                    public Object encode(Object current, SearchPickerData.Selection selection) { return null; }
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
        };
        Assert.assertTrue(provider.categories().isEmpty());
        Assert.assertNull(provider.categoryOf("any:key"));
        Assert.assertTrue("分组接口必须是 ValueEditorProvider 子型",
                provider instanceof ValueEditorProvider);
    }

    @Test
    public void groupingProviderCanOverrideCategories() {
        CategorizedValueEditorProvider provider = new CategorizedValueEditorProvider() {
            @Override
            public SearchFunction searchFunction() {
                return (query, maxResults) -> SearchPickerData.SearchResult.empty();
            }

            @Override
            public String id() {
                return "tabbed:editor";
            }

            @Override
            public Codec codec() {
                return new Codec() {
                    @Override
                    public SearchPickerData.Selection decode(Object value) { return null; }

                    @Override
                    public Object encode(Object current, SearchPickerData.Selection selection) { return null; }
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
            public List<SearchPickerCategories.Category> categories() {
                return Collections.singletonList(new SearchPickerCategories.Category("tabs.blocks", "Blocks"));
            }

            @Override
            public String categoryOf(String candidateKey) {
                return "tabs.blocks";
            }
        };
        Assert.assertEquals(1, provider.categories().size());
        Assert.assertEquals("tabs.blocks", provider.categoryOf("anything"));
    }

    // ==================== 多维度缺省契约 ====================

    /** 只覆写单维度方法的实现：维度 overload 缺省委托 dimension 0 行为，其余维度空/未分类。 */
    @Test
    public void dimensionOverloadsDelegateToSingleDimensionByDefault() {
        CategorizedValueEditorProvider provider = groupedProvider(
                Collections.singletonList(new SearchPickerCategories.Category("a", "A")), "a");
        Assert.assertEquals(1, provider.categoryDimensionCount());
        Assert.assertEquals(1, provider.categories(0).size());
        Assert.assertTrue(provider.categories(1).isEmpty());
        Assert.assertTrue(provider.categories(7).isEmpty());
        Assert.assertEquals("a", provider.categoryOf(0, "any:key"));
        Assert.assertNull(provider.categoryOf(1, "any:key"));
    }

    /** 维度数按 categories() 是否为空推导：无分组 0，单一维度 1。 */
    @Test
    public void dimensionCountDerivesFromCategoriesPresence() {
        CategorizedValueEditorProvider ungrouped = groupedProvider(
                Collections.<SearchPickerCategories.Category>emptyList(), null);
        Assert.assertEquals(0, ungrouped.categoryDimensionCount());
        CategorizedValueEditorProvider grouped = groupedProvider(
                Collections.singletonList(new SearchPickerCategories.Category("a", "A")), "a");
        Assert.assertEquals(1, grouped.categoryDimensionCount());
    }

    /** 负数维度参数必须在维度 overload 上 fail-fast。 */
    @Test
    public void negativeDimensionRejected() {
        CategorizedValueEditorProvider provider = groupedProvider(
                Collections.singletonList(new SearchPickerCategories.Category("a", "A")), "a");
        try {
            provider.categories(-1);
            Assert.fail("negative dimension must be rejected");
        } catch (IllegalArgumentException expected) { }
        try {
            provider.categoryOf(-1, "any:key");
            Assert.fail("negative dimension must be rejected");
        } catch (IllegalArgumentException expected) { }
    }

    /** 构建只覆写单维度分组方法的测试 provider。 */
    private static CategorizedValueEditorProvider groupedProvider(
            final List<SearchPickerCategories.Category> categories, final String categoryKey) {
        return new CategorizedValueEditorProvider() {
            @Override
            public SearchFunction searchFunction() {
                return (query, maxResults) -> SearchPickerData.SearchResult.empty();
            }

            @Override
            public String id() {
                return "grouped:editor";
            }

            @Override
            public Codec codec() {
                return new Codec() {
                    @Override
                    public SearchPickerData.Selection decode(Object value) { return null; }

                    @Override
                    public Object encode(Object current, SearchPickerData.Selection selection) { return null; }
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
            public List<SearchPickerCategories.Category> categories() { return categories; }

            @Override
            public String categoryOf(String candidateKey) { return categoryKey; }
        };
    }

    private static void assertCategoryFails(String key, String label, int count) {
        try {
            new SearchPickerCategories.Category(key, label, count);
            Assert.fail("expected IllegalArgumentException for key=" + key + " label=" + label
                    + " count=" + count);
        } catch (IllegalArgumentException expected) { }
    }
}
