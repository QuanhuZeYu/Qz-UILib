package club.heiqi.config.schema;

/** 搜索选择器 widget 元数据。 */
public final class SearchPickerSpec implements WidgetSpec {
    /** picker 与配置值的绑定粒度。 */
    public enum BindingMode { SINGLE_VALUE, LIST_MEMBERS }

    private final String editorId;
    private final int maxItems;
    private final BindingMode bindingMode;

    /**
     * 创建搜索选择器描述。
     *
     * @param editorId namespaced editor id，格式为 namespace:path
     * @param maxItems 兼容提示值，必须为正；搜索返回完整结果
     */
    public SearchPickerSpec(String editorId, int maxItems) {
        this(editorId, maxItems, BindingMode.SINGLE_VALUE);
    }

    /**
     * 创建指定绑定粒度的搜索选择器描述。
     *
     * @param editorId namespaced editor id，格式为 namespace:path
     * @param maxItems 兼容提示值，必须为正；搜索返回完整结果
     * @param bindingMode 配置值绑定粒度
     */
    public SearchPickerSpec(String editorId, int maxItems, BindingMode bindingMode) {
        if (!isNamespacedId(editorId)) {
            throw new IllegalArgumentException("editorId must use namespace:path with lowercase ASCII characters");
        }
        if (maxItems < 1) {
            throw new IllegalArgumentException("maxItems must be positive");
        }
        this.editorId = editorId;
        this.maxItems = maxItems;
        if (bindingMode == null) {
            throw new IllegalArgumentException("bindingMode must not be null");
        }
        this.bindingMode = bindingMode;
    }

    /** @return namespaced editor id */
    public String editorId() { return editorId; }

    /** @return 单次结果上限 */
    public int maxItems() { return maxItems; }

    /** @return picker 与配置值的绑定粒度 */
    public BindingMode bindingMode() { return bindingMode; }

    private static boolean isNamespacedId(String id) {
        return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }
}
