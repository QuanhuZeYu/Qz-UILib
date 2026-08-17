package club.heiqi.config.ui.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 搜索选择器数据契约的纯加法分组扩展。
 *
 * <p>本阶段不修改 {@link SearchPickerData} 与 {@link ValueEditorProvider} 的任何现有类型、字段与签名：
 * 「分类」概念由本类的不可变 {@link Category} 承载，候选到分类的映射经 {@link Classifier}
 * 函数式接口由 provider / 接线层提供；分组感知的 provider 可选实现
 * {@link CategorizedValueEditorProvider}，旧 provider 实现零改动即可编译并自动退化为无分组模式。</p>
 */
public final class SearchPickerCategories {

    private SearchPickerCategories() { }

    /**
     * 分类快照（不可变）：key 稳定唯一，label 展示，count 为可选条目数。
     *
     * <p>count 为 -1 表示未知（由数据源动态决定）；接线层可经 {@link #withCount(int)}
     * 派生带动态计数的等价快照。</p>
     */
    public static final class Category {
        private final String key;
        private final String label;
        private final int count;

        /** 创建无静态计数的分类（条目数由数据源动态决定）。 */
        public Category(String key, String label) {
            this(key, label, -1);
        }

        /** 创建分类快照。 */
        public Category(String key, String label, int count) {
            this.key = requireText(key, "category key");
            this.label = requireText(label, "category label");
            if (count < -1) throw new IllegalArgumentException("count must be >= -1");
            this.count = count;
        }

        /** @return 分类内稳定 key */
        public String key() { return key; }
        /** @return 展示文本 */
        public String label() { return label; }
        /** @return 可选条目数；-1 表示未知 */
        public int count() { return count; }

        /** @return 以新计数派生的等价分类 */
        public Category withCount(int count) {
            return new Category(key, label, count);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Category)) return false;
            Category that = (Category) other;
            return key.equals(that.key) && label.equals(that.label) && count == that.count;
        }

        @Override
        public int hashCode() { return Objects.hash(key, label, Integer.valueOf(count)); }

        @Override
        public String toString() { return "Category(" + key + ", count=" + count + ")"; }
    }

    /**
     * 候选分组的只读分类器：candidateKey → categoryKey。
     *
     * <p>返回 null 或空串表示该候选未分类（不进入任何分类导航行）。</p>
     */
    @FunctionalInterface
    public interface Classifier {
        /** @return 候选所属分类 key；null/空串表示未分类 */
        String categoryKeyOf(String candidateKey);
    }

    /** 不可变深拷贝分类列表。 */
    public static List<Category> immutableCopy(List<Category> categories) {
        if (categories == null) return Collections.emptyList();
        ArrayList<Category> copy = new ArrayList<Category>(categories.size());
        for (Category category : categories) {
            if (category == null) throw new IllegalArgumentException("categories must not contain null");
            copy.add(category);
        }
        return Collections.unmodifiableList(copy);
    }

    /** @return categories 中 key 匹配的分类；未找到返回 null */
    public static Category find(List<Category> categories, String key) {
        if (categories == null || key == null) return null;
        for (Category category : categories) {
            if (key.equals(category.key())) return category;
        }
        return null;
    }

    /** @return categories 中是否包含 key */
    public static boolean contains(List<Category> categories, String key) {
        return find(categories, key) != null;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException(name + " must not be empty");
        return value;
    }
}
