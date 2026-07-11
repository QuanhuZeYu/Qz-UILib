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

    private static SearchPickerData.Candidate candidate(String key, String label) {
        return new SearchPickerData.Candidate(key, label, new ArrayList<SearchPickerData.Variant>());
    }
}
