package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import club.heiqi.config.ui.editor.SearchPickerCategories;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.SearchPickerPresentation;

/**
 * ScenePickerPanel 的纯函数核心（零副作用），供 L2 单元测试直接锚定。
 *
 * <p>涵盖：分类过滤与导航行派生、当前成员问题分析、变体草稿数学
 * （勾选/排序/可确认性/展示过滤）与高亮夹取。全部方法平台无关、无信号依赖。</p>
 */
final class ScenePickerPanelNav {

    /** 「全部」分类行的稳定 key（永远不可能与真实分类 key 冲突的哨兵）。 */
    static final String ALL_CATEGORY_KEY = "\u0000all";

    private ScenePickerPanelNav() {
    }

    /** 分类导航行快照。 */
    static final class CategoryRow {
        final String key;
        final String label;
        final int count;
        final boolean all;

        private CategoryRow(String key, String label, int count, boolean all) {
            this.key = key;
            this.label = label;
            this.count = count;
            this.all = all;
        }

        static CategoryRow allRow(String label, int count) {
            return new CategoryRow(ALL_CATEGORY_KEY, label, count, true);
        }

        static CategoryRow categoryRow(String key, String label, int count) {
            return new CategoryRow(key, label, count, false);
        }

        /** @return 供 keyed forEach 使用的唯一稳定 key */
        String identityKey() {
            return all ? ALL_CATEGORY_KEY : key;
        }
    }

    /**
     * 按当前分类过滤候选；null/空串分类或分类器缺省时不过滤。
     */
    static List<SearchPickerData.Candidate> filterByCategory(
            List<SearchPickerData.Candidate> candidates, String categoryKey,
            Function<String, String> categoryOf) {
        if (categoryKey == null || categoryKey.isEmpty() || categoryOf == null) {
            return candidates;
        }
        ArrayList<SearchPickerData.Candidate> out = new ArrayList<SearchPickerData.Candidate>();
        for (SearchPickerData.Candidate candidate : candidates) {
            if (categoryKey.equals(categoryOf.apply(candidate.key()))) out.add(candidate);
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * 派生分类导航行：首行恒为「全部」（计数 = 候选总数），随后按 categories 声明顺序排列。
     *
     * <ul>
     *   <li>行计数优先取当前候选中的动态计数；动态为 0 时回退静态 {@code Category.count()}（&gt;=0 时）。</li>
     *   <li>空分类隐藏：动态计数为 0 且静态计数未知/为 0（{@code count() <= 0}）的分类不出现。</li>
     * </ul>
     */
    static List<CategoryRow> categoryRows(List<SearchPickerCategories.Category> categories,
                                          List<SearchPickerData.Candidate> candidates,
                                          Function<String, String> categoryOf, String allLabel) {
        ArrayList<CategoryRow> rows = new ArrayList<CategoryRow>();
        rows.add(CategoryRow.allRow(allLabel, candidates.size()));
        if (categoryOf == null) {
            return Collections.unmodifiableList(rows);
        }
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (SearchPickerData.Candidate candidate : candidates) {
            String categoryKey = categoryOf.apply(candidate.key());
            if (categoryKey == null || categoryKey.isEmpty()) continue;
            Integer current = counts.get(categoryKey);
            counts.put(categoryKey, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
        }
        for (SearchPickerCategories.Category category : categories) {
            Integer dynamic = counts.get(category.key());
            int dynamicCount = dynamic == null ? 0 : dynamic.intValue();
            int staticCount = category.count();
            if (dynamicCount == 0 && staticCount <= 0) continue;
            int shown = dynamicCount > 0 ? dynamicCount : staticCount;
            rows.add(CategoryRow.categoryRow(category.key(), category.label(), shown));
        }
        return Collections.unmodifiableList(rows);
    }

    /** 勾选/取消一个变体 key。 */
    static List<String> toggleVariant(List<String> keys, String key) {
        ArrayList<String> next = new ArrayList<String>(keys);
        if (next.contains(key)) next.remove(key); else next.add(key);
        return Collections.unmodifiableList(next);
    }

    /** 按候选声明顺序重排已选 key，未声明但已选的 key 保序补在尾部。 */
    static List<String> orderedKeys(List<SearchPickerData.Variant> variants, List<String> keys) {
        ArrayList<String> ordered = new ArrayList<String>();
        for (SearchPickerData.Variant variant : variants) {
            if (keys.contains(variant.key())) ordered.add(variant.key());
        }
        for (String key : keys) {
            if (!ordered.contains(key)) ordered.add(key);
        }
        return Collections.unmodifiableList(ordered);
    }

    /** ALL 恒可确认；SELECTED 至少勾选一个。 */
    static boolean canConfirm(SearchPickerData.SelectionMode mode, List<String> keys) {
        return mode == SearchPickerData.SelectionMode.ALL || !keys.isEmpty();
    }

    /**
     * 派生变体展示列表：已选 key 恒显示（缺失时以 unavailableVariant 文案补位），
     * 其余按 query 对 key/label 大小写不敏感过滤。
     */
    static List<SearchPickerData.Variant> displayVariants(List<SearchPickerData.Variant> variants,
                                                          List<String> keys, String query,
                                                          SearchPickerPresentation presentation) {
        ArrayList<SearchPickerData.Variant> displayed = new ArrayList<SearchPickerData.Variant>();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (SearchPickerData.Variant variant : variants) {
            if (keys.contains(variant.key())) {
                displayed.add(variant);
                continue;
            }
            if (!needle.isEmpty() && !variant.key().toLowerCase(Locale.ROOT).contains(needle)
                    && !variant.label().toLowerCase(Locale.ROOT).contains(needle)) continue;
            displayed.add(variant);
        }
        ArrayList<String> known = new ArrayList<String>();
        for (SearchPickerData.Variant variant : variants) known.add(variant.key());
        for (String key : keys) {
            if (!known.contains(key)) {
                displayed.add(new SearchPickerData.Variant(key, presentation.unavailableVariant(key)));
            }
        }
        return Collections.unmodifiableList(displayed);
    }

    /** 高亮夹取：空数据 -1，否则夹到 [0, size-1]。 */
    static int clampHighlight(int highlight, int totalItems) {
        if (totalItems <= 0 || highlight < 0) return -1;
        return Math.min(highlight, totalItems - 1);
    }

    /** 单次响应式统计得到的只读成员问题快照。 */
    static final class MemberIssues {
        final int invalidCount;
        final Set<Long> duplicateMemberIds;

        MemberIssues(int invalidCount, Set<Long> duplicateMemberIds) {
            this.invalidCount = invalidCount;
            this.duplicateMemberIds = Collections.unmodifiableSet(duplicateMemberIds);
        }
    }

    /**
     * 统计仅供展示的成员问题；malformed 不进入重复计算，重复数按成员数而非 key 组数计。
     */
    static MemberIssues analyzeMemberIssues(List<SearchPickerData.CurrentMember> members) {
        Map<String, Integer> keyCounts = new HashMap<String, Integer>();
        int invalidCount = 0;
        for (SearchPickerData.CurrentMember member : members) {
            if (member.selection() == null) {
                invalidCount++;
            } else {
                String key = member.selection().candidateKey();
                Integer count = keyCounts.get(key);
                keyCounts.put(key, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
            }
        }
        Set<Long> duplicateMemberIds = new HashSet<Long>();
        for (SearchPickerData.CurrentMember member : members) {
            if (member.selection() != null
                    && keyCounts.get(member.selection().candidateKey()).intValue() > 1) {
                duplicateMemberIds.add(Long.valueOf(member.memberId()));
            }
        }
        return new MemberIssues(invalidCount, duplicateMemberIds);
    }
}
