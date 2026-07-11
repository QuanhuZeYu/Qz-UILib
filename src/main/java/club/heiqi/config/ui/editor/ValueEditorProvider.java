package club.heiqi.config.ui.editor;

/** 一个可注册的配置值 editor 契约集合。 */
public interface ValueEditorProvider {
    /** 默认无候选搜索，兼容不提供搜索能力的既有 editor。 */
    default SearchPickerData.SearchResult search(String query, int maxResults) {
        return SearchPickerData.SearchResult.empty();
    }
    /** @return namespaced editor id */
    String id();

    /** @return 值转换器 */
    Codec codec();

    /** @return 纯展示适配器 */
    VisualAdapter visualAdapter();
}
