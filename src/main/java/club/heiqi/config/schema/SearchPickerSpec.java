package club.heiqi.config.schema;

/**
 * 搜索选择器 widget 元数据。
 *
 * <p>只描述 picker 身份与配置值绑定粒度：<b>搜索 lane 的窗口总量 = 候选源返回的真实命中数，
 * 无窗口上限</b>（可见性 = 按窗口几何的惰性分页，{@code PickerCandidateSource#page} 支持任意 offset）。</p>
 */
public final class SearchPickerSpec implements WidgetSpec {
    /** picker 与配置值的绑定粒度。 */
    public enum BindingMode { SINGLE_VALUE, LIST_MEMBERS }

    private final String editorId;
    private final BindingMode bindingMode;

    /**
     * 创建搜索选择器描述（默认 {@link BindingMode#SINGLE_VALUE} 绑定）。
     *
     * @param editorId namespaced editor id，格式为 namespace:path
     */
    public SearchPickerSpec(String editorId) {
        this(editorId, BindingMode.SINGLE_VALUE);
    }

    /**
     * 创建指定绑定粒度的搜索选择器描述。
     *
     * @param editorId namespaced editor id，格式为 namespace:path
     * @param bindingMode 配置值绑定粒度
     */
    public SearchPickerSpec(String editorId, BindingMode bindingMode) {
        if (!isNamespacedId(editorId)) {
            throw new IllegalArgumentException("editorId must use namespace:path with lowercase ASCII characters");
        }
        this.editorId = editorId;
        if (bindingMode == null) {
            throw new IllegalArgumentException("bindingMode must not be null");
        }
        this.bindingMode = bindingMode;
    }

    /** @return namespaced editor id */
    public String editorId() { return editorId; }

    /** @return picker 与配置值的绑定粒度 */
    public BindingMode bindingMode() { return bindingMode; }

    private static boolean isNamespacedId(String id) {
        return id != null && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }
}
