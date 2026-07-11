package club.heiqi.config.ui.field;

import java.util.function.Consumer;

import club.heiqi.config.schema.SearchPickerSpec;
import club.heiqi.config.schema.ValueSpec;
import club.heiqi.config.ui.editor.Registry;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.ValueEditorProvider;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
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
        return createControlledIfPresent(rt, spec, Signal.create(value), registry, onChange);
    }

    /**
     * 按 widget 声明创建受控搜索选择器；当前配置值变化时实时重新解码。
     *
     * @param rt scene runtime
     * @param spec 值规格
     * @param value 当前配置值信号
     * @param registry 已冻结的 editor 注册表
     * @param onChange 编码后值回调
     * @return 搜索选择器节点，或 null
     */
    public static SceneNode createControlledIfPresent(SceneRuntime rt, ValueSpec spec,
                                                       ReadableSignal<Object> value,
                                                       Registry registry, Consumer<Object> onChange) {
        if (!(spec.widget() instanceof SearchPickerSpec)) return null;
        SearchPickerSpec pickerSpec = (SearchPickerSpec) spec.widget();
        ValueEditorProvider provider = registry.find(pickerSpec.editorId());
        if (provider == null) {
            throw new IllegalStateException("missing value editor provider: " + pickerSpec.editorId());
        }
        ValueEditorProvider.SearchFunction searchFunction = provider.searchFunction();
        Computed<SearchPickerData.Selection> current = Computed.create(() -> decode(provider, value.get()));
        SearchPickerData.Selection initial = current.get();
        String initialQuery = initial == null ? "" : initial.candidateKey();
        Signal<String> query = Signal.create(initialQuery);
        SearchPickerData.Selection[] lastSelection = new SearchPickerData.Selection[] { initial };
        Effect.create(() -> {
            SearchPickerData.Selection selection = current.get();
            if (selection != null && !selection.equals(lastSelection[0])) {
                lastSelection[0] = selection;
                query.set(selection.candidateKey());
            }
        });
        Computed<SearchPickerData.SearchResult> results = Computed.create(() -> {
            try {
                SearchPickerData.SearchResult searched = searchFunction.search(query.get(), pickerSpec.maxItems());
                return searched == null ? SearchPickerData.SearchResult.empty()
                        : searched.limitedTo(pickerSpec.maxItems());
            } catch (RuntimeException ignored) {
                return SearchPickerData.SearchResult.empty();
            }
        });
        return SceneSearchPicker.create(rt, SceneSearchPicker.Props.builder(query, results,
                Signal.create(Boolean.TRUE), query::set, selection -> {
                    try {
                        Object encoded = provider.codec().encode(selection);
                        if (encoded != null) onChange.accept(encoded);
                    } catch (RuntimeException ignored) {
                        // 外部 codec 失败时不得用 null 擦除现值。
                    }
                }, provider.visualAdapter()).currentSelection(current).build()).get();
    }

    private static SearchPickerData.Selection decode(ValueEditorProvider provider, Object value) {
        try { return provider.codec().decode(value); } catch (RuntimeException ignored) { return null; }
    }
}
