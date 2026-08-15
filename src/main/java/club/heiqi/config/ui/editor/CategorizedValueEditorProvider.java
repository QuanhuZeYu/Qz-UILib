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
 * 无分组模式（分类导航只显示单一「全部」行）。两个 default 方法均返回
 * 「不支持分组 / 未分类」的保守值，实现者按需覆写。</p>
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
}
