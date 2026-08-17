package club.heiqi.config.ui.editor;

import java.util.Collections;
import java.util.List;

/**
 * 分组感知的值编辑器 provider 契约（纯加法扩展）。
 *
 * <p><b>为何不改 {@link ValueEditorProvider}：</b>本阶段要求所有现有文件零改动，
 * 且旧 provider 实现（含 Miner 的 BlockPickerProvider）必须不修改即可编译。
 * 因此分组能力以本独立接口提供，接线层用
 * {@code provider instanceof CategorizedValueEditorProvider} 探测；未实现时自动退化为
 * 无分组模式（分类导航只显示单一「全部」行）。default 方法均返回
 * 「不支持分组 / 未分类」的保守值，实现者按需覆写。</p>
 *
 * <p><b>多分类维度：</b>实现者可经 {@link #categoryDimensionCount()} 声明维度数，并按维度
 * 覆写 {@link #categories(int)} 与 {@link #categoryOf(int, String)}；只覆写单维度方法的实现
 * 保持 count=0/1 退化语义，接线层据此决定是否渲染维度分段切换。</p>
 */
public interface CategorizedValueEditorProvider extends ValueEditorProvider {

    /** @return 注册时冻结的分类快照；空列表表示不支持分组 */
    default List<SearchPickerCategories.Category> categories() {
        return Collections.emptyList();
    }

    /** @return 候选 key 所属分类 key；null/空串表示未分类 */
    default String categoryOf(String candidateKey) {
        return null;
    }

    /** @return 分组维度数量；缺省为 0（无分组）或 1（只有 categories() 单一维度） */
    default int categoryDimensionCount() {
        return categories().isEmpty() ? 0 : 1;
    }

    /**
     * @return 指定维度的分类快照；缺省 dimension 0 返回 {@link #categories()}，其余空列表
     * @throws IllegalArgumentException dimension 为负数
     */
    default List<SearchPickerCategories.Category> categories(int dimension) {
        if (dimension < 0) throw new IllegalArgumentException("dimension must not be negative: " + dimension);
        return dimension == 0 ? categories() : Collections.<SearchPickerCategories.Category>emptyList();
    }

    /**
     * @return 指定维度下候选 key 所属分类 key；缺省 dimension 0 委托 {@link #categoryOf(String)}，
     *         其余返回 null（未分类）
     * @throws IllegalArgumentException dimension 为负数
     */
    default String categoryOf(int dimension, String candidateKey) {
        if (dimension < 0) throw new IllegalArgumentException("dimension must not be negative: " + dimension);
        return dimension == 0 ? categoryOf(candidateKey) : null;
    }
}
