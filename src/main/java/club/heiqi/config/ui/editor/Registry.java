package club.heiqi.config.ui.editor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

/** 每个配置 screen 独立持有的 value editor registry。 */
public final class Registry {
    private final Map<String, ValueEditorProvider> providers = new LinkedHashMap<String, ValueEditorProvider>();
    private boolean frozen;

    /**
     * 注册 provider；空 id、重复 id 或冻结后注册均立即失败。
     *
     * <p>注册期只固化 provider 的引用与快照属性（codec/visualAdapter/searchFunction/分类/惰性候选源引用），
     * <b>不复制任何候选数据</b>——冻结的必须始终是惰性 source 的引用。</p>
     */
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
        SearchPickerPanelPresentation panelPresentation = provider.panelPresentation();
        CurrentValuePresenter currentValuePresenter = provider.currentValuePresenter();
        if (codec == null || visualAdapter == null || searchFunction == null || presentation == null
                || panelPresentation == null) {
            throw new IllegalArgumentException("provider codec, visualAdapter, searchFunction, presentation"
                    + " and panelPresentation must not be null: " + id);
        }
        java.util.List<SearchPickerCategories.Category> categories = provider instanceof CategorizedValueEditorProvider
                ? SearchPickerCategories.immutableCopy(
                        ((CategorizedValueEditorProvider) provider).categories())
                : Collections.<SearchPickerCategories.Category>emptyList();
        Function<String, String> categoryOf = provider instanceof CategorizedValueEditorProvider
                ? ((CategorizedValueEditorProvider) provider)::categoryOf : null;
        int categoryDimensionCount = provider instanceof CategorizedValueEditorProvider
                ? ((CategorizedValueEditorProvider) provider).categoryDimensionCount() : 0;
        IntFunction<java.util.List<SearchPickerCategories.Category>> categoriesByDimension =
                provider instanceof CategorizedValueEditorProvider
                        ? ((CategorizedValueEditorProvider) provider)::categories
                        : dimension -> Collections.<SearchPickerCategories.Category>emptyList();
        BiFunction<Integer, String, String> categoryOfByDimension = provider instanceof CategorizedValueEditorProvider
                ? ((CategorizedValueEditorProvider) provider)::categoryOf : null;
        // 惰性候选源：只固化引用，禁止在注册期复制候选数据（注册期冻结语义）。
        CandidateSourceValueEditorProvider candidateSourceProvider =
                provider instanceof CandidateSourceValueEditorProvider
                        ? (CandidateSourceValueEditorProvider) provider : null;
        PickerCandidateSource candidateSource = candidateSourceProvider == null
                ? null : candidateSourceProvider.candidateSource();
        PickerIconSource iconSource = candidateSourceProvider == null ? null : candidateSourceProvider.iconSource();
        providers.put(id, new RegisteredProvider(id, codec, visualAdapter, searchFunction, presentation,
                panelPresentation, currentValuePresenter, categories, categoryOf, categoryDimensionCount,
                categoriesByDimension, categoryOfByDimension, candidateSource, iconSource));
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
    private static final class RegisteredProvider implements CategorizedValueEditorProvider,
            CandidateSourceValueEditorProvider {
        private final String id;
        private final Codec codec;
        private final VisualAdapter visualAdapter;
        private final SearchFunction searchFunction;
        private final SearchPickerPresentation presentation;
        private final SearchPickerPanelPresentation panelPresentation;
        private final CurrentValuePresenter currentValuePresenter;
        private final java.util.List<SearchPickerCategories.Category> categories;
        private final Function<String, String> categoryOf;
        private final int categoryDimensionCount;
        private final IntFunction<java.util.List<SearchPickerCategories.Category>> categoriesByDimension;
        private final BiFunction<Integer, String, String> categoryOfByDimension;
        private final PickerCandidateSource candidateSource;
        private final PickerIconSource iconSource;

        private RegisteredProvider(String id, Codec codec, VisualAdapter visualAdapter, SearchFunction searchFunction,
                                   SearchPickerPresentation presentation,
                                   SearchPickerPanelPresentation panelPresentation,
                                   CurrentValuePresenter currentValuePresenter,
                                   java.util.List<SearchPickerCategories.Category> categories,
                                   Function<String, String> categoryOf, int categoryDimensionCount,
                                   IntFunction<java.util.List<SearchPickerCategories.Category>> categoriesByDimension,
                                   BiFunction<Integer, String, String> categoryOfByDimension,
                                   PickerCandidateSource candidateSource,
                                   PickerIconSource iconSource) {
            this.id = id;
            this.codec = codec;
            this.visualAdapter = visualAdapter;
            this.searchFunction = searchFunction;
            this.presentation = presentation;
            this.panelPresentation = panelPresentation;
            this.currentValuePresenter = currentValuePresenter;
            this.categories = categories;
            this.categoryOf = categoryOf;
            this.categoryDimensionCount = categoryDimensionCount;
            this.categoriesByDimension = categoriesByDimension;
            this.categoryOfByDimension = categoryOfByDimension;
            this.candidateSource = candidateSource;
            this.iconSource = iconSource;
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
        public SearchPickerPanelPresentation panelPresentation() { return panelPresentation; }
        /** {@inheritDoc} */
        public CurrentValuePresenter currentValuePresenter() { return currentValuePresenter; }
        /** {@inheritDoc} */
        public java.util.List<SearchPickerCategories.Category> categories() { return categories; }
        /** {@inheritDoc} */
        public String categoryOf(String candidateKey) {
            return categoryOf == null ? null : categoryOf.apply(candidateKey);
        }
        /** {@inheritDoc} */
        public int categoryDimensionCount() { return categoryDimensionCount; }
        /** {@inheritDoc} */
        public java.util.List<SearchPickerCategories.Category> categories(int dimension) {
            if (dimension < 0) throw new IllegalArgumentException("dimension must not be negative: " + dimension);
            return categoriesByDimension.apply(dimension);
        }
        /** {@inheritDoc} */
        public String categoryOf(int dimension, String candidateKey) {
            if (dimension < 0) throw new IllegalArgumentException("dimension must not be negative: " + dimension);
            return categoryOfByDimension == null ? null
                    : categoryOfByDimension.apply(Integer.valueOf(dimension), candidateKey);
        }
        /** {@inheritDoc} */
        public PickerCandidateSource candidateSource() { return candidateSource; }
        /** {@inheritDoc} */
        public PickerIconSource iconSource() { return iconSource; }
    }
}
