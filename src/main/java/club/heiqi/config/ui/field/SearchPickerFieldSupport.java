package club.heiqi.config.ui.field;

import java.util.function.Consumer;

import club.heiqi.config.schema.SearchPickerSpec;
import club.heiqi.config.schema.ValueSpec;
import club.heiqi.config.ui.editor.Registry;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.ValueEditorProvider;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneSearchPicker;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 将 ValueSpec 搜索选择器元数据装配为受控 scene 控件。 */
public final class SearchPickerFieldSupport {
    private SearchPickerFieldSupport() { }

    /**
     * 按 widget 声明创建搜索选择器；非搜索 widget 返回 null。
     *
     * @param rt scene runtime
     * @param spec 值规格
     * @param value 当前值
     * @param registry 已冻结的 editor 注册表
     * @param onChange 编码后值回调
     * @return 搜索选择器节点，或 null
     */
    public static SceneNode createIfPresent(SceneRuntime rt, ValueSpec spec, Object value,
                                            Registry registry, Consumer<Object> onChange) {
        if (!(spec.widget() instanceof SearchPickerSpec)) return null;
        SearchPickerSpec pickerSpec = (SearchPickerSpec) spec.widget();
        ValueEditorProvider provider = registry.find(pickerSpec.editorId());
        if (provider == null) {
            throw new IllegalStateException("missing value editor provider: " + pickerSpec.editorId());
        }
        String initialQuery = "";
        try {
            SearchPickerData.Selection selection = provider.codec().decode(value);
            if (selection != null) initialQuery = selection.candidateKey();
        } catch (RuntimeException ignored) {
            // 外部 codec 失败时保留原值，仅以空 query 展示。
        }
        Signal<String> query = Signal.create(initialQuery);
        Computed<SearchPickerData.SearchResult> results = Computed.create(() -> {
            try {
                SearchPickerData.SearchResult searched = provider.search(query.get(), pickerSpec.maxItems());
                return searched == null ? SearchPickerData.SearchResult.empty()
                        : searched.limitedTo(pickerSpec.maxItems());
            } catch (RuntimeException ignored) {
                return SearchPickerData.SearchResult.empty();
            }
        });
        return SceneSearchPicker.create(rt, new SceneSearchPicker.Props(query, results,
                Signal.create(Boolean.TRUE), query::set, selection -> {
                    try {
                        onChange.accept(provider.codec().encode(selection));
                    } catch (RuntimeException ignored) {
                        // 外部 codec 失败时不得用 null 擦除现值。
                    }
                }, provider.visualAdapter())).get();
    }
}
