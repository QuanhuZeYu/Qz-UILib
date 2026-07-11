package club.heiqi.config.ui.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/** SearchPickerData 快照、去重与预算测试。 */
public class SearchPickerDataTest {
    /** 输入列表后续变化不能影响候选快照。 */
    @Test
    public void candidateDefensivelyCopiesVariants() {
        List<SearchPickerData.Variant> variants = new ArrayList<SearchPickerData.Variant>();
        variants.add(new SearchPickerData.Variant("normal", "Normal"));
        SearchPickerData.Candidate candidate = new SearchPickerData.Candidate("stone", "Stone", variants);
        variants.clear();

        assertEquals(1, candidate.variants().size());
        try {
            candidate.variants().clear();
            fail("expected immutable variants");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    /** 候选内 variant key 是 keyed diff 身份，重复时必须立即拒绝。 */
    @Test
    public void candidateRejectsDuplicateVariantKeys() {
        try {
            new SearchPickerData.Candidate("stone", "Stone", Arrays.asList(
                    new SearchPickerData.Variant("same", "First"),
                    new SearchPickerData.Variant("same", "Second")));
            fail("expected duplicate variant key failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unique"));
        }
    }

    /** 重复 key 首项胜，唯一结果超过 64 时截断。 */
    @Test
    public void searchResultKeepsFirstAndTruncatesAt64() {
        List<SearchPickerData.Candidate> input = new ArrayList<SearchPickerData.Candidate>();
        input.add(candidate("same", "first"));
        input.add(candidate("same", "second"));
        for (int i = 0; i < 70; i++) input.add(candidate("key-" + i, "item-" + i));

        SearchPickerData.SearchResult result = new SearchPickerData.SearchResult(input);

        assertEquals(64, result.candidates().size());
        assertEquals("first", result.candidates().get(0).label());
        assertTrue(result.truncated());
        input.clear();
        assertEquals(64, result.candidates().size());
    }

    /** 只有重复项被丢弃时不标记预算截断。 */
    @Test
    public void duplicateOnlyDoesNotMarkTruncated() {
        SearchPickerData.SearchResult result = new SearchPickerData.SearchResult(Arrays.asList(
                candidate("same", "first"), candidate("same", "second")));
        assertFalse(result.truncated());
    }

    /** 共享空结果与调用方预算均保持明确截断语义。 */
    @Test
    public void emptyAndLimitedToRespectBudget() {
        assertSame(SearchPickerData.SearchResult.empty(), SearchPickerData.SearchResult.empty());
        assertTrue(SearchPickerData.SearchResult.empty().candidates().isEmpty());
        SearchPickerData.SearchResult result = SearchPickerData.SearchResult.limitedTo(Arrays.asList(
                candidate("a", "A"), candidate("a", "duplicate"), candidate("b", "B")), 1);
        assertEquals(1, result.candidates().size());
        assertEquals("a", result.candidates().get(0).key());
        assertTrue(result.truncated());
    }

    /** 实例级预算限制必须保留 provider 已声明的截断标志。 */
    @Test
    public void instanceLimitPreservesUpstreamTruncation() {
        List<SearchPickerData.Candidate> input = new ArrayList<SearchPickerData.Candidate>();
        for (int i = 0; i < 65; i++) input.add(candidate("key-" + i, "Item " + i));
        SearchPickerData.SearchResult upstream = new SearchPickerData.SearchResult(input).limitedTo(2);

        SearchPickerData.SearchResult limited = upstream.limitedTo(8);

        assertEquals(2, limited.candidates().size());
        assertTrue(limited.truncated());
    }

    /** 旧二参构造保持 null=ALL、非 null=SINGLE。 */
    @Test public void legacySelectionMapsModes() {
        assertEquals(SearchPickerData.SelectionMode.ALL, new SearchPickerData.Selection("c", null).mode());
        assertEquals(SearchPickerData.SelectionMode.SINGLE, new SearchPickerData.Selection("c", "v").mode());
    }

    /** 三种模式分别执行数量强校验。 */
    @Test public void selectionModeCardinalityIsStrict() {
        assertInvalid(SearchPickerData.SelectionMode.ALL, Arrays.asList("a"));
        assertInvalid(SearchPickerData.SelectionMode.SINGLE, new ArrayList<String>());
        assertInvalid(SearchPickerData.SelectionMode.MULTIPLE, Arrays.asList("a"));
    }

    /** key 必须非空且唯一。 */
    @Test public void selectionKeysMustBeUniqueAndNonEmpty() {
        assertInvalid(SearchPickerData.SelectionMode.MULTIPLE, Arrays.asList("a", "a"));
        assertInvalid(SearchPickerData.SelectionMode.SINGLE, Arrays.asList(""));
    }

    /** Selection 深拷贝、只读且按值相等。 */
    @Test public void selectionIsImmutableValue() {
        List<String> keys = new ArrayList<String>(Arrays.asList("a", "b"));
        SearchPickerData.Selection first = new SearchPickerData.Selection("c",
                SearchPickerData.SelectionMode.MULTIPLE, keys);
        keys.clear();
        SearchPickerData.Selection second = new SearchPickerData.Selection("c",
                SearchPickerData.SelectionMode.MULTIPLE, Arrays.asList("a", "b"));
        assertEquals(first, second); assertEquals(first.hashCode(), second.hashCode());
        try { first.variantKeys().clear(); fail("expected immutable keys"); }
        catch (UnsupportedOperationException expected) { }
    }

    /** MULTIPLE 不允许经旧单 key getter 静默降级。 */
    @Test public void multipleVariantKeyFailsFast() {
        try {
            new SearchPickerData.Selection("c", SearchPickerData.SelectionMode.MULTIPLE,
                    Arrays.asList("a", "b")).variantKey();
            fail("expected fail-fast");
        } catch (IllegalStateException expected) { }
    }

    private static void assertInvalid(SearchPickerData.SelectionMode mode, List<String> keys) {
        try { new SearchPickerData.Selection("c", mode, keys); fail("expected invalid selection"); }
        catch (IllegalArgumentException expected) { }
    }

    private static SearchPickerData.Candidate candidate(String key, String label) {
        return new SearchPickerData.Candidate(key, label, new ArrayList<SearchPickerData.Variant>());
    }
}
