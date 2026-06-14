package club.heiqi.uilib.config;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import club.heiqi.uilib.config.ModernConfigSearchIndex.SearchEntry;
import club.heiqi.uilib.config.ModernConfigSearchIndex.TemplateCategory;
import club.heiqi.uilib.ui.control.DocumentSegmentedSelectionEvent;
import club.heiqi.uilib.ui.control.DocumentSegmentedSelectionHandler;
import club.heiqi.uilib.ui.control.DocumentSegmentedSelectorControl;
import club.heiqi.uilib.ui.control.DocumentTextInputChangeEvent;
import club.heiqi.uilib.ui.control.DocumentTextInputChangeHandler;
import club.heiqi.uilib.ui.control.DocumentTextInputControl;
import club.heiqi.uilib.ui.control.DocumentToggleChangeEvent;
import club.heiqi.uilib.ui.control.DocumentToggleChangeHandler;
import club.heiqi.uilib.ui.control.DocumentToggleSwitchControl;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 现代配置页屏幕级搜索过滤组件。
 *
 * <p>组合搜索框、类型分段选择、只看已修改开关、结果列表与跳转回调，提供配置项快速定位入口。
 * 组件本身不写回配置、不参与草稿与脏状态维护，仅在用户输入或切换过滤条件时调用
 * {@link ModernConfigSearchIndex#search(String, Set, boolean)} 即时刷新结果。</p>
 *
 * <p>查询/过滤逻辑通过 {@link #applyQuery}、{@link #applyTypeFilter}、
 * {@link #applyModifiedOnly} 三个纯方法暴露，UI 控件处理器内部委托给这些方法；
 * 测试可直接调用它们而不依赖控件渲染。</p>
 *
 * <p>类型分段选择提供「全部 + 7 个 {@link TemplateCategory}」单选入口；
 * {@link #applyTypeFilter} 在逻辑层支持任意类别组合（多选），供未来扩展或外部调用。</p>
 */
public final class ModernConfigSearchFilter {

    /** 分段选择器选项标签，下标 0 表示「全部」，其后按 {@link TemplateCategory} 顺序排列。 */
    private static final String[] CATEGORY_OPTION_LABELS = {
            "全部", "字符串", "数字", "布尔", "对象", "列表", "源码", "选择器"
    };

    /** 与 {@link #CATEGORY_OPTION_LABELS} 下标 1..7 对应的 {@link TemplateCategory}。 */
    private static final TemplateCategory[] CATEGORY_VALUES = {
            TemplateCategory.STRING, TemplateCategory.NUMBER, TemplateCategory.BOOLEAN,
            TemplateCategory.OBJECT, TemplateCategory.LIST, TemplateCategory.RAW_EDITOR,
            TemplateCategory.ENHANCED_PICKER
    };

    private static final int MAX_VISIBLE_RESULTS = 200;
    private static final int RESULT_VALUE_SUMMARY_LIMIT = 60;

    private final ModernConfigSearchIndex searchIndex;
    private final Consumer<String> jumpToPathHandler;
    private final ElementNode rootElement;
    private final DocumentTextInputControl queryInput;
    private final DocumentSegmentedSelectorControl categorySelector;
    private final DocumentToggleSwitchControl modifiedOnlyToggle;
    private final ElementNode resultsContainer;
    private TextNode resultCountText;
    private TextNode emptyHintText;

    private String currentQuery = "";
    private Set<TemplateCategory> currentTypeFilter;
    private boolean currentModifiedOnly = false;
    private List<SearchEntry> currentResults = Collections.emptyList();

    /**
     * 创建搜索过滤组件。
     *
     * @param document 所属 HTML-like 文档，用于构建内部控件
     * @param searchIndex 不可变快照搜索索引，提供结果查询
     * @param jumpToPathHandler 跳转回调，选中条目时回传配置路径；为 null 时跳转仅更新本地选中态
     */
    public ModernConfigSearchFilter(UiDocument document, ModernConfigSearchIndex searchIndex,
            Consumer<String> jumpToPathHandler) {
        if (document == null) {
            throw new IllegalArgumentException("document 不能为 null");
        }
        this.searchIndex = searchIndex == null
                ? new ModernConfigSearchIndex(null, null, null)
                : searchIndex;
        this.jumpToPathHandler = jumpToPathHandler;
        this.rootElement = document.div();
        this.queryInput = createQueryInput(document);
        this.categorySelector = createCategorySelector(document);
        this.modifiedOnlyToggle = createModifiedOnlyToggle(document);
        this.resultsContainer = document.div();
        configureRootStyle();
        assembleLayout(document);
        recompute();
        renderResults();
    }

    /**
     * 应用查询关键字并刷新结果。
     *
     * @param query 查询关键字；为 null 时视为空串
     */
    public void applyQuery(String query) {
        String normalized = query == null ? "" : query;
        if (normalized.equals(currentQuery)) {
            return;
        }
        currentQuery = normalized;
        recompute();
        renderResults();
    }

    /**
     * 应用类型过滤集合并刷新结果。
     *
     * @param typeFilter 类型过滤集合；为 null 或空表示不限类型
     */
    public void applyTypeFilter(Set<TemplateCategory> typeFilter) {
        Set<TemplateCategory> normalized = normalizeTypeFilter(typeFilter);
        if (normalized == null && currentTypeFilter == null) {
            return;
        }
        if (normalized != null && normalized.equals(currentTypeFilter)) {
            return;
        }
        currentTypeFilter = normalized;
        recompute();
        renderResults();
    }

    /**
     * 应用「只看已修改」开关并刷新结果。
     *
     * @param modifiedOnly 是否只展示已修改条目
     */
    public void applyModifiedOnly(boolean modifiedOnly) {
        if (modifiedOnly == currentModifiedOnly) {
            return;
        }
        currentModifiedOnly = modifiedOnly;
        recompute();
        renderResults();
    }

    /**
     * 基于索引的最新条件重新查询并刷新结果。
     *
     * <p>在 {@link ModernConfigSearchIndex#refreshDirtyMarkers()} 之后调用，
     * 可让结果反映最新脏状态。</p>
     */
    public void refresh() {
        recompute();
        renderResults();
    }

    /**
     * 返回当前命中结果（不可变副本）。
     *
     * @return 当前命中结果列表
     */
    public List<SearchEntry> getResults() {
        return currentResults;
    }

    /**
     * 返回当前命中结果数量。
     *
     * @return 结果数量
     */
    public int getResultCount() {
        return currentResults.size();
    }

    /**
     * 返回当前查询关键字。
     *
     * @return 当前查询关键字
     */
    public String getQuery() {
        return currentQuery;
    }

    /**
     * 返回当前类型过滤集合（不可变副本）。
     *
     * @return 当前类型过滤集合；为 null 表示不限类型
     */
    public Set<TemplateCategory> getTypeFilter() {
        if (currentTypeFilter == null) {
            return null;
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(currentTypeFilter));
    }

    /**
     * 返回「只看已修改」开关状态。
     *
     * @return true 时仅展示已修改条目
     */
    public boolean isModifiedOnly() {
        return currentModifiedOnly;
    }

    /**
     * 跳转到指定结果条目对应的配置路径。
     *
     * @param index 结果列表下标；越界时忽略
     */
    public void jumpTo(int index) {
        if (index < 0 || index >= currentResults.size()) {
            return;
        }
        String path = currentResults.get(index).getPath();
        if (jumpToPathHandler != null) {
            jumpToPathHandler.accept(path);
        }
    }

    /**
     * 返回组件根元素，用于注入文档流。
     *
     * @return 组件根元素
     */
    public ElementNode getElement() {
        return rootElement;
    }

    /**
     * 设置分段选择器当前选中类别（程序化，不触发事件）。
     *
     * @param category 目标类别；为 null 时回到「全部」
     */
    public void setSelectedCategory(TemplateCategory category) {
        categorySelector.setSelectedIndex(indexFromCategory(category));
    }

    private void configureRootStyle() {
        rootElement.setAttribute("data-modern-config-search", "true");
        rootElement.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(10))
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xFF0F172A)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(16));
    }

    private DocumentTextInputControl createQueryInput(UiDocument document) {
        DocumentTextInputControl input = new DocumentTextInputControl(document)
                .setPlaceholder("输入路径、名称或值关键字进行搜索")
                .setChangeHandler(new DocumentTextInputChangeHandler() {
                    @Override
                    public void onTextChanged(DocumentTextInputChangeEvent event) {
                        applyQuery(queryInput.getText());
                    }
                });
        input.getElement().setAttribute("data-modern-config-control", "search-query");
        input.getElement().style().setWidth(UiStyleLength.percent(1.0F));
        return input;
    }

    private DocumentSegmentedSelectorControl createCategorySelector(UiDocument document) {
        DocumentSegmentedSelectorControl selector =
                new DocumentSegmentedSelectorControl(document, CATEGORY_OPTION_LABELS)
                        .setBackgroundColors(0xFF2563EB, 0xFF1D4ED8, 0xFF334155, 0xFF1E293B, 0xFF1E293B)
                        .setTextColors(0xFFFFFFFF, 0xFFCBD5E1, 0xFF64748B)
                        .setSelectionHandler(new DocumentSegmentedSelectionHandler() {
                            @Override
                            public void onSelectionChanged(DocumentSegmentedSelectionEvent event) {
                                applyTypeFilter(typeFilterFromIndex(event.getSelectedIndex()));
                            }
                        });
        selector.getElement().setAttribute("data-modern-config-control", "search-category");
        selector.getElement().style().setWidth(UiStyleLength.percent(1.0F));
        return selector;
    }

    private DocumentToggleSwitchControl createModifiedOnlyToggle(UiDocument document) {
        DocumentToggleSwitchControl toggle = new DocumentToggleSwitchControl(document)
                .setTrackColors(0xFF475569, 0xFF22C55E, 0xFF334155)
                .setChangeHandler(new DocumentToggleChangeHandler() {
                    @Override
                    public void onToggleChanged(DocumentToggleChangeEvent event) {
                        applyModifiedOnly(modifiedOnlyToggle.isToggled());
                    }
                });
        toggle.getElement().setAttribute("data-modern-config-control", "search-modified-only");
        return toggle;
    }

    private void assembleLayout(UiDocument document) {
        ElementNode title = document.div();
        title.style().setTextColor(0xFFF8FAFC);
        title.appendText("快速定位");
        rootElement.append(title);

        ElementNode description = document.div();
        description.style().setTextColor(0xFF94A3B8);
        description.appendText("按关键字或类型筛选配置项，点击条目跳转到对应卡片。");
        rootElement.append(description);

        rootElement.append(queryInput.getElement());
        rootElement.append(categorySelector.getElement());

        ElementNode toggleRow = document.div();
        toggleRow.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8));
        ElementNode toggleLabel = document.div();
        toggleLabel.style().setTextColor(0xFFE2E8F0);
        toggleLabel.appendText("只看已修改");
        toggleRow.append(toggleLabel);
        toggleRow.append(modifiedOnlyToggle.getElement());
        rootElement.append(toggleRow);

        resultsContainer.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setRowGap(UiStyleLength.px(8))
                .setMaxHeight(UiStyleLength.px(320))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
        rootElement.append(resultsContainer);

        ElementNode footer = document.div();
        footer.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8))
                .setTextColor(0xFF93C5FD);
        resultCountText = footer.appendText("");
        emptyHintText = footer.appendText("");
        rootElement.append(footer);
    }

    private void recompute() {
        currentResults = searchIndex.search(currentQuery, currentTypeFilter, currentModifiedOnly);
    }

    private void renderResults() {
        if (resultsContainer == null) {
            return;
        }
        resultsContainer.clearChildren();
        UiDocument document = resultsContainer.getOwnerDocument();
        int total = currentResults.size();
        int renderCount = Math.min(total, MAX_VISIBLE_RESULTS);
        for (int index = 0; index < renderCount; index++) {
            resultsContainer.append(createResultRow(document, currentResults.get(index), index));
        }
        if (resultCountText != null) {
            resultCountText.setText(formatResultCount(total, renderCount));
        }
        if (emptyHintText != null) {
            emptyHintText.setText(total == 0 ? " | 暂无匹配结果" : "");
        }
    }

    private ElementNode createResultRow(UiDocument document, SearchEntry entry, final int index) {
        ElementNode row = document.div();
        row.setAttribute("data-modern-config-search-path", entry.getPath());
        row.setAttribute("data-modern-config-search-index", Integer.toString(index));
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(4))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xFF162132)
                .setBorderColor(0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(10));

        ElementNode head = document.div();
        head.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8));
        head.append(createTextSpan(document, entry.getDisplayName(), 0xFFF8FAFC));
        head.append(createTextSpan(document, " [" + entry.getTemplateTypeLabel() + "]", 0xFF93C5FD));
        if (entry.isDirty()) {
            head.append(createTextSpan(document, " ●已修改", 0xFFFCA5A5));
        }
        row.append(head);

        ElementNode meta = document.div();
        meta.style().setTextColor(0xFF64748B);
        meta.appendText(entry.getPath());
        row.append(meta);

        String summary = entry.getValueSummary();
        if (summary != null && !summary.isEmpty()) {
            ElementNode valueLine = document.div();
            valueLine.style().setTextColor(0xFFCBD5E1);
            valueLine.appendText(truncateSummary(summary));
            row.append(valueLine);
        }

        row.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                if (event.getButton() != 0) {
                    return false;
                }
                jumpTo(index);
                return true;
            }
        });
        return row;
    }

    private static ElementNode createTextSpan(UiDocument document, String text, int color) {
        ElementNode span = document.div();
        span.style().setTextColor(color);
        span.appendText(text);
        return span;
    }

    private static String formatResultCount(int total, int renderCount) {
        if (total == 0) {
            return "结果 0 项";
        }
        if (total > renderCount) {
            return "结果 " + renderCount + "+ 项（共 " + total + "，仅展示前 " + renderCount + "）";
        }
        return "结果 " + total + " 项";
    }

    private static String truncateSummary(String summary) {
        if (summary == null) {
            return "";
        }
        if (summary.length() <= RESULT_VALUE_SUMMARY_LIMIT) {
            return summary;
        }
        return summary.substring(0, RESULT_VALUE_SUMMARY_LIMIT - 3) + "...";
    }

    private static Set<TemplateCategory> normalizeTypeFilter(Set<TemplateCategory> typeFilter) {
        if (typeFilter == null || typeFilter.isEmpty()) {
            return null;
        }
        Set<TemplateCategory> normalized = EnumSet.noneOf(TemplateCategory.class);
        for (TemplateCategory category : typeFilter) {
            if (category != null) {
                normalized.add(category);
            }
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static Set<TemplateCategory> typeFilterFromIndex(int selectedIndex) {
        if (selectedIndex <= 0 || selectedIndex > CATEGORY_VALUES.length) {
            return null;
        }
        return EnumSet.of(CATEGORY_VALUES[selectedIndex - 1]);
    }

    private static int indexFromCategory(TemplateCategory category) {
        if (category == null) {
            return 0;
        }
        for (int index = 0; index < CATEGORY_VALUES.length; index++) {
            if (CATEGORY_VALUES[index] == category) {
                return index + 1;
            }
        }
        return 0;
    }

    /**
     * 返回分段选择器选项标签（主要用于测试断言与文档同步）。
     *
     * @return 选项标签数组副本
     */
    static String[] getCategoryOptionLabels() {
        return CATEGORY_OPTION_LABELS.clone();
    }

    /**
     * 将 {@link TemplateCategory} 转为中文展示标签。
     *
     * @param category 目标类别；为 null 时返回「全部」
     * @return 中文展示标签
     */
    static String labelOfCategory(TemplateCategory category) {
        int optionIndex = indexFromCategory(category);
        return CATEGORY_OPTION_LABELS[optionIndex];
    }

    /**
     * 规范化关键字为小写（工具方法，便于测试与扩展）。
     *
     * @param query 原始关键字
     * @return 小写关键字
     */
    static String normalizeQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }
}
