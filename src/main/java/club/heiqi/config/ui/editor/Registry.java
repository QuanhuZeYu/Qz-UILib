package club.heiqi.config.ui.editor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** 每个配置 screen 独立持有的 value editor registry。 */
public final class Registry {
    private final Map<String, ValueEditorProvider> providers = new LinkedHashMap<String, ValueEditorProvider>();
    private boolean frozen;

    /** 注册 provider；空 id、重复 id 或冻结后注册均立即失败。 */
    public void register(ValueEditorProvider provider) {
        if (frozen) throw new IllegalStateException("value editor registry is frozen");
        if (provider == null) throw new IllegalArgumentException("provider must not be null");
        String id = provider.id();
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("provider id must not be empty");
        if (providers.containsKey(id)) throw new IllegalArgumentException("duplicate value editor id: " + id);
        Codec codec = provider.codec();
        VisualAdapter visualAdapter = provider.visualAdapter();
        ValueEditorProvider.SearchFunction searchFunction = provider.searchFunction();
        SearchPickerPresentation presentation = provider.presentation();
        CurrentValuePresenter currentValuePresenter = provider.currentValuePresenter();
        if (codec == null || visualAdapter == null || searchFunction == null || presentation == null) {
            throw new IllegalArgumentException("provider codec, visualAdapter, searchFunction and presentation must not be null: " + id);
        }
        java.util.List<SearchPickerCategories.Category> categories = provider instanceof CategorizedValueEditorProvider
                ? SearchPickerCategories.immutableCopy(
                        ((CategorizedValueEditorProvider) provider).categories())
                : Collections.<SearchPickerCategories.Category>emptyList();
        Function<String, String> categoryOf = provider instanceof CategorizedValueEditorProvider
                ? ((CategorizedValueEditorProvider) provider)::categoryOf : null;
        providers.put(id, new RegisteredProvider(id, codec, visualAdapter, searchFunction, presentation,
                currentValuePresenter, categories, categoryOf));
    }

    /** 冻结 registry；可重复调用。 */
    public void freeze() { frozen = true; }
    /** @return 是否已冻结 */
    public boolean isFrozen() { return frozen; }
    /** 查询 provider；缺失返回 null。 */
    public ValueEditorProvider find(String id) { return providers.get(id); }
    /** @return 注册项保序只读快照 */
    public Map<String, ValueEditorProvider> providers() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, ValueEditorProvider>(providers));
    }


    /** 注册时固化的 provider 快照，避免冻结后重新读取原 provider 的可变属性。 */
    private static final class RegisteredProvider implements CategorizedValueEditorProvider {
        private final String id;
        private final Codec codec;
        private final VisualAdapter visualAdapter;
        private final SearchFunction searchFunction;
        private final SearchPickerPresentation presentation;
        private final CurrentValuePresenter currentValuePresenter;
        private final java.util.List<SearchPickerCategories.Category> categories;
        private final Function<String, String> categoryOf;

        private RegisteredProvider(String id, Codec codec, VisualAdapter visualAdapter, SearchFunction searchFunction,
                                   SearchPickerPresentation presentation, CurrentValuePresenter currentValuePresenter,
                                   java.util.List<SearchPickerCategories.Category> categories,
                                   Function<String, String> categoryOf) {
            this.id = id;
            this.codec = codec;
            this.visualAdapter = visualAdapter;
            this.searchFunction = searchFunction;
            this.presentation = presentation;
            this.currentValuePresenter = currentValuePresenter;
            this.categories = categories;
            this.categoryOf = categoryOf;
        }

        /** {@inheritDoc} */
        public String id() { return id; }
        /** {@inheritDoc} */
        public Codec codec() { return codec; }
        /** {@inheritDoc} */
        public VisualAdapter visualAdapter() { return visualAdapter; }
        /** {@inheritDoc} */
        public SearchFunction searchFunction() { return searchFunction; }
        /** {@inheritDoc} */
        public SearchPickerPresentation presentation() { return presentation; }
        /** {@inheritDoc} */
        public CurrentValuePresenter currentValuePresenter() { return currentValuePresenter; }
        /** {@inheritDoc} */
        public java.util.List<SearchPickerCategories.Category> categories() { return categories; }
        /** {@inheritDoc} */
        public String categoryOf(String candidateKey) {
            return categoryOf == null ? null : categoryOf.apply(candidateKey);
        }
    }
}
