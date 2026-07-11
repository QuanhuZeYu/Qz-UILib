package club.heiqi.config.ui.editor;

/** 一个可注册的配置值 editor 契约集合。 */
public interface ValueEditorProvider {
    /** 可在注册时冻结的搜索函数。 */
    interface SearchFunction {
        /** @return query 对应的候选快照 */
        SearchPickerData.SearchResult search(String query, int maxResults);
    }

    /**
     * 返回注册表应冻结的独立搜索函数。实现必须显式捕获注册时的不可变搜索目标，不能返回
     * 继续读取 provider 可变字段的委托。
     *
     * @return 注册时可安全固化的搜索函数
     */
    SearchFunction searchFunction();
    /** @return namespaced editor id */
    String id();

    /** @return 值转换器 */
    Codec codec();

    /** @return 纯展示适配器 */
    VisualAdapter visualAdapter();
}
