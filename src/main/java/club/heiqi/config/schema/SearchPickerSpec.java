package club.heiqi.config.schema;

/** 搜索选择器 widget 元数据。 */
public final class SearchPickerSpec implements WidgetSpec {
    private final String editorId;
    private final int maxItems;

    /**
     * 创建搜索选择器描述。
     *
     * @param editorId namespaced editor id，格式为 namespace:path
     * @param maxItems 兼容提示值，必须为正；搜索返回完整结果
     */
    public SearchPickerSpec(String editorId, int maxItems) {
        if (!isNamespacedId(editorId)) {
            throw new IllegalArgumentException("editorId must use namespace:path with lowercase ASCII characters");
        }
        if (maxItems < 1) {
            throw new IllegalArgumentException("maxItems must be positive");
        }
        this.editorId = editorId;
        this.maxItems = maxItems;
    }

    /** @return namespaced editor id */
    public String editorId() { return editorId; }

    /** @return 单次结果上限 */
    public int maxItems() { return maxItems; }

    private static boolean isNamespacedId(String id) {
        return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }
}
