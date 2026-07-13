package club.heiqi.config.ui.field;

import java.util.function.Consumer;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.config.schema.SearchPickerSpec;
import club.heiqi.config.schema.ValueSpec;
import club.heiqi.config.ui.editor.Registry;
import club.heiqi.config.ui.editor.ListMemberCodec;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.SearchPickerPresentation;
import club.heiqi.config.ui.editor.ValueEditorProvider;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneSearchPicker;
import club.heiqi.uilib.ui.scene.control.SceneSimpleList;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 将 ValueSpec 搜索选择器元数据装配为受控 scene 控件。 */
public final class SearchPickerFieldSupport {
    private static final Logger LOG = LogManager.getLogger("QzUiLib/ConfigUI");
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
        if (pickerSpec.bindingMode() == SearchPickerSpec.BindingMode.LIST_MEMBERS) {
            throw new IllegalArgumentException("LIST_MEMBERS requires explicit list item binding");
        }
        ValueEditorProvider provider = registry.find(pickerSpec.editorId());
        if (provider == null) {
            throw new IllegalStateException("missing value editor provider: " + pickerSpec.editorId());
        }
        ValueEditorProvider.SearchFunction searchFunction = provider.searchFunction();
        SearchPickerPresentation presentation = provider.presentation();
        Signal<String> decodeError = Signal.create("");
        Signal<String> searchError = Signal.create("");
        Signal<String> encodeError = Signal.create("");
        Computed<String> error = Computed.create(() -> firstError(encodeError.get(), searchError.get(), decodeError.get()));
        Computed<SearchPickerData.Selection> current = Computed.create(() -> {
            try {
                SearchPickerData.Selection decoded = provider.codec().decode(value.get());
                if (decoded == null) return fail(decodeError, pickerSpec.editorId(), "decode", presentation.decodeError(), null);
                decodeError.set("");
                return decoded;
            } catch (RuntimeException exception) {
                return fail(decodeError, pickerSpec.editorId(), "decode", presentation.decodeError(), exception);
            }
        });
        Signal<String> query = Signal.create("");
        Computed<SearchPickerData.SearchResult> results = Computed.create(() -> {
            try {
                SearchPickerData.SearchResult searched = searchFunction.search(query.get(), Integer.MAX_VALUE);
                if (searched == null) return fail(searchError, pickerSpec.editorId(), "search",
                        presentation.searchError(), null);
                searchError.set("");
                return searched;
            } catch (RuntimeException exception) {
                fail(searchError, pickerSpec.editorId(), "search", presentation.searchError(), exception);
                return SearchPickerData.SearchResult.empty();
            }
        });
        return SceneSearchPicker.create(rt, SceneSearchPicker.Props.builder(query, results,
                Signal.create(Boolean.TRUE), nextQuery -> {
                    decodeError.set(""); searchError.set(""); encodeError.set(""); query.set(nextQuery);
                }, selection -> {
                    try {
                        Object encoded = provider.codec().encode(value.get(), selection);
                        if (encoded != null) {
                            onChange.accept(encoded);
                            query.set("");
                            decodeError.set(""); searchError.set(""); encodeError.set("");
                        } else {
                            fail(encodeError, pickerSpec.editorId(), "encode", presentation.encodeError(), null);
                        }
                    } catch (RuntimeException exception) {
                        fail(encodeError, pickerSpec.editorId(), "encode", presentation.encodeError(), exception);
                    }
                }, provider.visualAdapter()).currentSelection(current).presentation(presentation).error(error).build()).get();
    }

    /**
     * 显式装配列表成员 picker；稳定成员身份由调用方持有的 ListItem signal 提供。
     *
     * @return LIST_MEMBERS picker，非 picker 或非 LIST_MEMBERS 时返回 null
     */
    public static SceneNode createListMembersIfPresent(SceneRuntime rt, ValueSpec spec,
                                                        ReadableSignal<Object> value,
                                                        Signal<List<SceneSimpleList.ListItem>> items,
                                                        Registry registry, Consumer<Object> onChange) {
        if (!(spec.widget() instanceof SearchPickerSpec)) return null;
        SearchPickerSpec pickerSpec = (SearchPickerSpec) spec.widget();
        if (pickerSpec.bindingMode() != SearchPickerSpec.BindingMode.LIST_MEMBERS) return null;
        ValueEditorProvider provider = registry.find(pickerSpec.editorId());
        if (provider == null) throw new IllegalStateException("missing value editor provider: " + pickerSpec.editorId());
        if (!(provider.codec() instanceof ListMemberCodec)) {
            throw new IllegalStateException("LIST_MEMBERS requires ListMemberCodec: " + pickerSpec.editorId());
        }
        SearchPickerPresentation presentation = provider.presentation();
        Signal<String> query = Signal.create("");
        Signal<String> searchError = Signal.create("");
        Signal<String> encodeError = Signal.create("");
        Computed<String> error = Computed.create(() -> firstError(encodeError.get(), searchError.get(), ""));
        Computed<SearchPickerData.SearchResult> results = Computed.create(() -> {
            try {
                SearchPickerData.SearchResult searched = provider.searchFunction().search(query.get(), Integer.MAX_VALUE);
                if (searched == null) return fail(searchError, pickerSpec.editorId(), "search",
                        presentation.searchError(), null);
                searchError.set("");
                return searched;
            } catch (RuntimeException exception) {
                fail(searchError, pickerSpec.editorId(), "search", presentation.searchError(), exception);
                return SearchPickerData.SearchResult.empty();
            }
        });
        SearchPickerListBinding binding = new SearchPickerListBinding(value, items,
                (ListMemberCodec) provider.codec(), onChange);
        Computed<List<SearchPickerData.CurrentMember>> currentMembers = Computed.create(
                () -> binding.currentMembers(results.get()));
        Computed<SearchPickerData.Selection> currentSelection = Computed.create(binding::currentSelection);
        return SceneSearchPicker.create(rt, SceneSearchPicker.Props.builder(query, results,
                Signal.create(Boolean.TRUE), next -> { searchError.set(""); encodeError.set(""); query.set(next); },
                selection -> { }, provider.visualAdapter()).selectionCommit(selection -> {
                    if (!binding.confirm(selection)) {
                        fail(encodeError, pickerSpec.editorId(), "encode", presentation.encodeError(), null);
                        return false;
                    }
                    query.set(""); searchError.set(""); encodeError.set("");
                    return true;
                }).currentSelection(currentSelection).presentation(presentation)
                .error(error).currentMembers(currentMembers, binding::edit)
                .onRemoveCurrent(memberId -> {
                    if (!binding.remove(memberId)) {
                        fail(encodeError, pickerSpec.editorId(), "remove", presentation.encodeError(), null);
                        return false;
                    }
                    searchError.set(""); encodeError.set("");
                    return true;
                })
                .onBeginAdd(binding::add).onCancel(() -> {
                    binding.cancel();
                    query.set(""); searchError.set(""); encodeError.set("");
                }).build()).get();
    }

    private static <T> T fail(Signal<String> error, String editorId, String phase, String message,
                              RuntimeException exception) {
        error.set(message);
        if (exception == null) LOG.warn("[QzUiLib/ConfigUI] search picker failed: editorId={}, phase={}, result=null",
                editorId, phase);
        else LOG.warn("[QzUiLib/ConfigUI] search picker failed: editorId={}, phase={}", editorId, phase, exception);
        return null;
    }

    private static String firstError(String first, String second, String third) {
        if (first != null && !first.isEmpty()) return first;
        if (second != null && !second.isEmpty()) return second;
        return third == null ? "" : third;
    }
}
