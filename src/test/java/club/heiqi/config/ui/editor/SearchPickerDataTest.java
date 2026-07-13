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

    /** 重复 key 首项胜，唯一结果完整保留。 */
    @Test
    public void searchResultKeepsFirstAndPreservesAllUniqueCandidates() {
        List<SearchPickerData.Candidate> input = new ArrayList<SearchPickerData.Candidate>();
        input.add(candidate("same", "first"));
        input.add(candidate("same", "second"));
        for (int i = 0; i < 70; i++) input.add(candidate("key-" + i, "item-" + i));

        SearchPickerData.SearchResult result = new SearchPickerData.SearchResult(input);

        assertEquals(71, result.candidates().size());
        assertEquals("first", result.candidates().get(0).label());
        assertFalse(result.truncated());
        input.clear();
        assertEquals(71, result.candidates().size());
    }

    /** 只有重复项被丢弃时不标记预算截断。 */
    @Test
    public void duplicateOnlyDoesNotMarkTruncated() {
        SearchPickerData.SearchResult result = new SearchPickerData.SearchResult(Arrays.asList(
                candidate("same", "first"), candidate("same", "second")));
        assertFalse(result.truncated());
    }

    /** 共享空结果与旧预算参数均返回完整去重结果。 */
    @Test
    public void emptyAndLimitedToRespectBudget() {
        assertSame(SearchPickerData.SearchResult.empty(), SearchPickerData.SearchResult.empty());
        assertTrue(SearchPickerData.SearchResult.empty().candidates().isEmpty());
        SearchPickerData.SearchResult result = SearchPickerData.SearchResult.limitedTo(Arrays.asList(
                candidate("a", "A"), candidate("a", "duplicate"), candidate("b", "B")), 1);
        assertEquals(2, result.candidates().size());
        assertEquals("a", result.candidates().get(0).key());
        assertFalse(result.truncated());
    }

    /** 实例级旧预算参数不再截断结果。 */
    @Test
    public void instanceLimitPreservesUpstreamTruncation() {
        List<SearchPickerData.Candidate> input = new ArrayList<SearchPickerData.Candidate>();
        for (int i = 0; i < 65; i++) input.add(candidate("key-" + i, "Item " + i));
        SearchPickerData.SearchResult upstream = new SearchPickerData.SearchResult(input).limitedTo(2);

        SearchPickerData.SearchResult limited = upstream.limitedTo(8);

        assertEquals(65, limited.candidates().size());
        assertFalse(limited.truncated());
    }

    /** 旧二参构造保持 null=ALL、非 null=SELECTED。 */
    @Test public void legacySelectionMapsModes() {
        assertEquals(SearchPickerData.SelectionMode.ALL, new SearchPickerData.Selection("c", null).mode());
        assertEquals(SearchPickerData.SelectionMode.SELECTED, new SearchPickerData.Selection("c", "v").mode());
    }

    /** 两种模式分别执行数量强校验。 */
    @Test public void selectionModeCardinalityIsStrict() {
        assertInvalid(SearchPickerData.SelectionMode.ALL, Arrays.asList("a"));
        assertInvalid(SearchPickerData.SelectionMode.SELECTED, new ArrayList<String>());
    }

    /** key 必须非空且唯一。 */
    @Test public void selectionKeysMustBeUniqueAndNonEmpty() {
        assertInvalid(SearchPickerData.SelectionMode.SELECTED, Arrays.asList("a", "a"));
        assertInvalid(SearchPickerData.SelectionMode.SELECTED, Arrays.asList(""));
    }

    /** Selection 深拷贝、只读且按值相等。 */
    @Test public void selectionIsImmutableValue() {
        List<String> keys = new ArrayList<String>(Arrays.asList("a", "b"));
        SearchPickerData.Selection first = new SearchPickerData.Selection("c",
                SearchPickerData.SelectionMode.SELECTED, keys);
        keys.clear();
        SearchPickerData.Selection second = new SearchPickerData.Selection("c",
                SearchPickerData.SelectionMode.SELECTED, Arrays.asList("a", "b"));
        assertEquals(first, second); assertEquals(first.hashCode(), second.hashCode());
        try { first.variantKeys().clear(); fail("expected immutable keys"); }
        catch (UnsupportedOperationException expected) { }
    }

    /** 多项 SELECTED 不允许经旧单 key getter 静默降级。 */
    @Test public void multipleSelectedVariantKeyFailsFast() {
        try {
            new SearchPickerData.Selection("c", SearchPickerData.SelectionMode.SELECTED,
                    Arrays.asList("a", "b")).variantKey();
            fail("expected fail-fast");
        } catch (IllegalStateException expected) { }
    }

    /** 当前成员以 memberId 区分，重复 candidate key 不会合并身份。 */
    @Test public void currentMembersKeepIndependentStableIdentity() {
        SearchPickerData.Selection selection = new SearchPickerData.Selection("same", (String) null);
        SearchPickerData.Candidate candidate = candidate("same", "Same");
        SearchPickerData.CurrentMember first = new SearchPickerData.CurrentMember(10L, selection, candidate, true);
        SearchPickerData.CurrentMember second = new SearchPickerData.CurrentMember(11L, selection, candidate, true);

        assertEquals(10L, first.memberId());
        assertEquals(11L, second.memberId());
        assertEquals("same", first.selection().candidateKey());
        assertEquals("same", second.selection().candidateKey());
        assertNotSame(candidate, first.candidate());
        assertEquals("Same", first.candidate().label());
        assertTrue(first.enumerated());
    }

    /** malformed 成员可用 null selection 表达，且不能伪装成已枚举候选。 */
    @Test public void currentMemberRepresentsMalformedValue() {
        SearchPickerData.CurrentMember malformed = new SearchPickerData.CurrentMember(0L, null, null, false);
        assertNull(malformed.selection());
        assertNull(malformed.candidate());
        assertFalse(malformed.enumerated());
        assertEquals("Unable to read this value",
                SearchPickerPresentation.defaultEnglish().currentMember(malformed));
        try {
            new SearchPickerData.CurrentMember(0L, null, candidate("same", "Same"), true);
            fail("expected invalid enumerated member");
        } catch (IllegalArgumentException expected) { }
    }

    /** 未枚举成员保留选择快照，通用 presentation 可按成员定制文案。 */
    @Test public void currentMemberPresentationIsCustomizable() {
        SearchPickerData.CurrentMember member = new SearchPickerData.CurrentMember(7L,
                new SearchPickerData.Selection("missing", (String) null), null, false);
        SearchPickerPresentation presentation = SearchPickerPresentation.builder()
                .currentMembersTitle("Configured")
                .currentMemberFormatter(value -> value.memberId() + ":" + value.selection().candidateKey())
                .build();
        assertEquals("Configured", presentation.currentMembersTitle());
        assertEquals("7:missing", presentation.currentMember(member));
    }

    private static void assertInvalid(SearchPickerData.SelectionMode mode, List<String> keys) {
        try { new SearchPickerData.Selection("c", mode, keys); fail("expected invalid selection"); }
        catch (IllegalArgumentException expected) { }
    }

    private static SearchPickerData.Candidate candidate(String key, String label) {
        return new SearchPickerData.Candidate(key, label, new ArrayList<SearchPickerData.Variant>());
    }
}
