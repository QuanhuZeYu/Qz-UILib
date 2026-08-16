package club.heiqi.uilib.ui.scene.control;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.config.ui.editor.SearchPickerCategories;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.SearchPickerPresentation;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanelNav.CategoryRow;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanelNav.MemberIssues;

/**
 * {@link ScenePickerPanelNav} 纯函数核心 L2 测试。
 *
 * <p>覆盖：分类过滤与导航行派生（全部行/动态计数/静态回退/空分类隐藏）、变体草稿数学
 * （勾选/排序/可确认性/展示过滤与缺失补位）、高亮夹取与成员问题分析。</p>
 */
public class ScenePickerPanelNavTest {

    private static SearchPickerData.Candidate candidate(String key, String... variantKeys) {
        java.util.ArrayList<SearchPickerData.Variant> variants =
                new java.util.ArrayList<SearchPickerData.Variant>();
        for (String variantKey : variantKeys) {
            variants.add(new SearchPickerData.Variant(variantKey, variantKey + ":label"));
        }
        return new SearchPickerData.Candidate(key, key + ":label", variants);
    }

    // ==================== 分类过滤 ====================

    @Test
    public void filterByCategoryPassesThroughWhenUngrouped() {
        List<SearchPickerData.Candidate> candidates = Arrays.asList(candidate("a"), candidate("b"));
        Assert.assertSame("null 分类不过滤", candidates,
                ScenePickerPanelNav.filterByCategory(candidates, null, key -> "x"));
        Assert.assertSame("空串分类不过滤", candidates,
                ScenePickerPanelNav.filterByCategory(candidates, "", key -> "x"));
        Assert.assertSame("分类器缺省不过滤", candidates,
                ScenePickerPanelNav.filterByCategory(candidates, "x", null));
    }

    @Test
    public void filterByCategoryKeepsOnlyMatchingCategory() {
        List<SearchPickerData.Candidate> candidates = Arrays.asList(candidate("a"), candidate("b"),
                candidate("c"));
        List<SearchPickerData.Candidate> filtered = ScenePickerPanelNav.filterByCategory(candidates,
                "cat1", key -> key.equals("a") || key.equals("c") ? "cat1" : "cat2");
        Assert.assertEquals(2, filtered.size());
        Assert.assertEquals("a", filtered.get(0).key());
        Assert.assertEquals("c", filtered.get(1).key());
    }

    // ==================== 分类导航行 ====================

    @Test
    public void categoryRowsAlwaysLeadWithAllRow() {
        List<SearchPickerCategories.Category> categories = Arrays.asList(
                new SearchPickerCategories.Category("cat1", "Cat1", 3));
        List<CategoryRow> rows = ScenePickerPanelNav.categoryRows(categories,
                Arrays.asList(candidate("a"), candidate("b")), key -> "cat1", "All");
        Assert.assertEquals(2, rows.size());
        Assert.assertTrue(rows.get(0).all());
        Assert.assertEquals(2, rows.get(0).count());
        Assert.assertEquals(ScenePickerPanelNav.ALL_CATEGORY_KEY, rows.get(0).identityKey());
    }

    @Test
    public void categoryRowsUseDynamicCountsAndHideEmptyUnknownCategories() {
        List<SearchPickerCategories.Category> categories = Arrays.asList(
                new SearchPickerCategories.Category("cat1", "Cat1"),           // count 未知
                new SearchPickerCategories.Category("cat2", "Cat2", 9));       // 静态 9
        List<SearchPickerData.Candidate> candidates = Arrays.asList(candidate("a"), candidate("b"),
                candidate("c"));
        List<CategoryRow> rows = ScenePickerPanelNav.categoryRows(categories, candidates,
                key -> key.equals("c") ? "cat1" : "cat2", "All");
        // 全部 + cat1（动态 1）+ cat2（动态 2）
        Assert.assertEquals(3, rows.size());
        Assert.assertEquals(1, rows.get(1).count());
        Assert.assertEquals(2, rows.get(2).count());
    }

    @Test
    public void categoryRowsHideEmptyCategoriesAndFallBackToStaticCount() {
        List<SearchPickerCategories.Category> categories = Arrays.asList(
                new SearchPickerCategories.Category("empty", "Empty"),         // 动态 0 静态未知 → 隐藏
                new SearchPickerCategories.Category("pending", "Pending", 5)); // 动态 0 静态 5 → 显示 5
        List<CategoryRow> rows = ScenePickerPanelNav.categoryRows(categories,
                Arrays.asList(candidate("a")), key -> "elsewhere", "All");
        Assert.assertEquals(2, rows.size());
        Assert.assertTrue(rows.get(0).all());
        Assert.assertEquals("pending", rows.get(1).key());
        Assert.assertEquals(5, rows.get(1).count());
    }

    @Test
    public void categoryRowsCollapseToAllWhenClassifierMissing() {
        List<CategoryRow> rows = ScenePickerPanelNav.categoryRows(
                Arrays.asList(new SearchPickerCategories.Category("cat1", "Cat1", 3)),
                Arrays.asList(candidate("a"), candidate("b")), null, "All");
        Assert.assertEquals(1, rows.size());
        Assert.assertTrue(rows.get(0).all());
        Assert.assertEquals(2, rows.get(0).count());
    }

    // ==================== 变体草稿数学 ====================

    @Test
    public void toggleVariantAddsAndRemoves() {
        List<String> keys = ScenePickerPanelNav.toggleVariant(Collections.<String>emptyList(), "a");
        Assert.assertEquals(Collections.singletonList("a"), keys);
        List<String> removed = ScenePickerPanelNav.toggleVariant(keys, "a");
        Assert.assertTrue(removed.isEmpty());
        List<String> two = ScenePickerPanelNav.toggleVariant(Collections.singletonList("a"), "b");
        Assert.assertEquals(Arrays.asList("a", "b"), two);
    }

    @Test
    public void orderedKeysFollowsCandidateDeclarationOrder() {
        List<SearchPickerData.Variant> variants = Arrays.asList(
                new SearchPickerData.Variant("b", "B"), new SearchPickerData.Variant("a", "A"),
                new SearchPickerData.Variant("c", "C"));
        List<String> ordered = ScenePickerPanelNav.orderedKeys(variants, Arrays.asList("c", "a", "b"));
        Assert.assertEquals(Arrays.asList("b", "a", "c"), ordered);
        List<String> withMissing = ScenePickerPanelNav.orderedKeys(variants, Arrays.asList("c", "x"));
        Assert.assertEquals(Arrays.asList("c", "x"), withMissing);
    }

    @Test
    public void canConfirmRequiresSelectionInSelectedMode() {
        Assert.assertTrue(ScenePickerPanelNav.canConfirm(SearchPickerData.SelectionMode.ALL,
                Collections.<String>emptyList()));
        Assert.assertFalse(ScenePickerPanelNav.canConfirm(SearchPickerData.SelectionMode.SELECTED,
                Collections.<String>emptyList()));
        Assert.assertTrue(ScenePickerPanelNav.canConfirm(SearchPickerData.SelectionMode.SELECTED,
                Collections.singletonList("a")));
    }

    @Test
    public void displayVariantsFiltersByQueryButKeepsSelectedAndUnavailable() {
        List<SearchPickerData.Variant> variants = Arrays.asList(
                new SearchPickerData.Variant("oak", "Oak Planks"),
                new SearchPickerData.Variant("spruce", "Spruce Planks"),
                new SearchPickerData.Variant("birch", "Birch Planks"));
        SearchPickerPresentation presentation = SearchPickerPresentation.defaultEnglish();

        // query 过滤（大小写不敏感、key 或 label 命中）
        List<SearchPickerData.Variant> filtered = ScenePickerPanelNav.displayVariants(variants,
                Collections.<String>emptyList(), "spru", presentation);
        Assert.assertEquals(1, filtered.size());
        Assert.assertEquals("spruce", filtered.get(0).key());

        // 已选项恒显示；已选但缺失的变体以 unavailable 文案补位
        List<SearchPickerData.Variant> kept = ScenePickerPanelNav.displayVariants(variants,
                Arrays.asList("oak", "legacy:missing"), "spru", presentation);
        Assert.assertEquals(3, kept.size());
        Assert.assertEquals("oak", kept.get(0).key());
        Assert.assertEquals("spruce", kept.get(1).key());
        Assert.assertEquals("legacy:missing", kept.get(2).key());
        Assert.assertTrue(kept.get(2).label().contains("legacy:missing"));

        // 空 query 全量显示
        Assert.assertEquals(3, ScenePickerPanelNav.displayVariants(variants,
                Collections.<String>emptyList(), "", presentation).size());
        Assert.assertEquals(3, ScenePickerPanelNav.displayVariants(variants,
                Collections.<String>emptyList(), null, presentation).size());
    }

    // ==================== 高亮夹取 ====================

    @Test
    public void clampHighlightClampsToDataSize() {
        Assert.assertEquals(-1, ScenePickerPanelNav.clampHighlight(3, 0));
        Assert.assertEquals(-1, ScenePickerPanelNav.clampHighlight(-1, 5));
        Assert.assertEquals(0, ScenePickerPanelNav.clampHighlight(0, 5));
        Assert.assertEquals(4, ScenePickerPanelNav.clampHighlight(9, 5));
    }

    // ==================== 成员问题分析 ====================

    @Test
    public void analyzeMemberIssuesCountsInvalidAndDuplicateMembers() {
        // malformed(1) + dup(2) + dup(3) + ok(4)
        List<SearchPickerData.CurrentMember> members = Arrays.asList(
                new SearchPickerData.CurrentMember(1L, null, null, false),
                new SearchPickerData.CurrentMember(2L,
                        new SearchPickerData.Selection("a", SearchPickerData.SelectionMode.ALL,
                                Collections.<String>emptyList()), null, false),
                new SearchPickerData.CurrentMember(3L,
                        new SearchPickerData.Selection("a", SearchPickerData.SelectionMode.ALL,
                                Collections.<String>emptyList()), null, false),
                new SearchPickerData.CurrentMember(4L,
                        new SearchPickerData.Selection("b", SearchPickerData.SelectionMode.ALL,
                                Collections.<String>emptyList()), null, false));
        MemberIssues issues = ScenePickerPanelNav.analyzeMemberIssues(members);
        Assert.assertEquals(1, issues.invalidCount);
        Assert.assertEquals("重复数按成员计", 2, issues.duplicateMemberIds.size());
        Assert.assertTrue(issues.duplicateMemberIds.contains(Long.valueOf(2L)));
        Assert.assertTrue(issues.duplicateMemberIds.contains(Long.valueOf(3L)));
        Assert.assertFalse(issues.duplicateMemberIds.contains(Long.valueOf(4L)));
    }

    @Test
    public void analyzeMemberIssuesEmptyMembersYieldsCleanSnapshot() {
        MemberIssues issues = ScenePickerPanelNav.analyzeMemberIssues(
                Collections.<SearchPickerData.CurrentMember>emptyList());
        Assert.assertEquals(0, issues.invalidCount);
        Assert.assertTrue(issues.duplicateMemberIds.isEmpty());
    }
}
