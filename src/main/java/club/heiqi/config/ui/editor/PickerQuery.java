package club.heiqi.config.ui.editor;

import java.util.Locale;
import java.util.Objects;

/**
 * 候选源查询条件值类型（不可变）：消除「null / 空串 / 非空」三态歧义。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.2/§1.5。两条 lane：</p>
 * <ul>
 *   <li>{@link #browse(int, String)}：文本为空 = 分类浏览 lane（无上限）；{@code matchCount} =
 *       <b>无分类收窄</b>（{@link #hasCategoryFilter()} == false）时 {@code size()}，<b>带分类过滤</b>时
 *       该分类命中数（清单序子序列规模）；</li>
 *   <li>{@link #text(String, int, String)}：非空文本 = 搜索 lane（上限由装配层 {@code searchMaxItems} 决定）。</li>
 * </ul>
 *
 * <p><b>归一化口径（写死，与候选源实现同源）</b>：
 * {@code normalize(raw) = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)}；
 * 文本与分类 key 的归一化结果即本类型的 {@link #equals(Object)}/{@link #hashCode()} 口径，
 * 可直接作为候选源命中序缓存的键。{@link #categoryKey()} 的 null 与空串等价，
 * 一律折叠为空串（空串 = 不做分类过滤）。</p>
 *
 * <p>本类型是纯值类型：无 signal、无跨线程发布、无线程所有权；平台无关（不依赖 uilib 任何类型）。</p>
 */
public final class PickerQuery {

    /** 不做分类过滤的分类 key（空串，null 与空串归一化后同值）。 */
    public static final String NO_CATEGORY = "";

    private final String normalizedText;
    private final int categoryDimension;
    private final String categoryKey;

    private PickerQuery(String normalizedText, int categoryDimension, String categoryKey) {
        if (categoryDimension < 0) {
            throw new IllegalArgumentException("categoryDimension must not be negative: " + categoryDimension);
        }
        this.normalizedText = normalizedText;
        this.categoryDimension = categoryDimension;
        this.categoryKey = categoryKey;
    }

    /**
     * 创建分类浏览查询（文本为空）。
     *
     * @param categoryDimension 分类维度（语义沿用 {@link CategorizedValueEditorProvider#categories(int)}）；不得为负
     * @param categoryKey       分类 key；null/空串 = 不做分类过滤
     * @return 浏览 lane 查询值
     */
    public static PickerQuery browse(int categoryDimension, String categoryKey) {
        return new PickerQuery("", categoryDimension, normalize(categoryKey));
    }

    /**
     * 创建文本查询（归一化后为空串时语义等于 {@link #browse(int, String)}）。
     *
     * @param raw               原始文本；null 视为空串
     * @param categoryDimension 分类维度；不得为负
     * @param categoryKey       分类 key；null/空串 = 不做分类过滤
     * @return 查询值
     */
    public static PickerQuery text(String raw, int categoryDimension, String categoryKey) {
        return new PickerQuery(normalize(raw), categoryDimension, normalize(categoryKey));
    }

    /**
     * 归一化文本：{@code raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)}。
     *
     * @param raw 原始文本
     * @return 归一化文本（非 null）
     */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /** @return 是否分类浏览 lane（归一化文本为空） */
    public boolean isBrowse() {
        return normalizedText.isEmpty();
    }

    /** @return 归一化文本（browse 时恒为空串，非 null） */
    public String normalizedText() {
        return normalizedText;
    }

    /** @return 分类维度 */
    public int categoryDimension() {
        return categoryDimension;
    }

    /**
     * @return 归一化分类 key；空串（{@link #NO_CATEGORY}）= 不做分类过滤，非 null
     */
    public String categoryKey() {
        return categoryKey;
    }

    /** @return 是否带分类过滤 */
    public boolean hasCategoryFilter() {
        return !categoryKey.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PickerQuery)) {
            return false;
        }
        PickerQuery that = (PickerQuery) other;
        return categoryDimension == that.categoryDimension
                && normalizedText.equals(that.normalizedText)
                && categoryKey.equals(that.categoryKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedText, Integer.valueOf(categoryDimension), categoryKey);
    }

    @Override
    public String toString() {
        return "PickerQuery(text=\"" + normalizedText + "\", dimension=" + categoryDimension
                + ", category=\"" + categoryKey + "\")";
    }
}
