package club.heiqi.config.ui.editor;

/** 一个可注册的配置值 editor 契约集合。 */
public interface ValueEditorProvider {
    /** 可在注册时冻结的搜索函数。 */
    interface SearchFunction {
        /** @return query 对应的候选快照 */
        SearchPickerData.SearchResult search(String query, int maxResults);
    }

    /** 默认无候选搜索，兼容不提供搜索能力的既有 editor。 */
    default SearchPickerData.SearchResult search(String query, int maxResults) {
        return SearchPickerData.SearchResult.empty();
    }

    /**
     * 返回注册表应冻结的搜索函数。默认委托 {@link #search(String, int)}，因此该实现必须无状态且不可变；
     * 持有可变搜索目标的 provider 应覆写本方法，并返回捕获当前目标的独立函数。
     *
     * @return 注册时可安全固化的搜索函数
     */
    default SearchFunction searchFunction() {
        return this::search;
    }
    /** @return namespaced editor id */
    String id();

    /** @return 值转换器 */
    Codec codec();

    /** @return 纯展示适配器 */
    VisualAdapter visualAdapter();
}
