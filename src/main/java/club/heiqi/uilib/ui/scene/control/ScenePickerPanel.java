package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.config.ui.editor.SearchPickerCategories;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.SearchPickerPanelPresentation;
import club.heiqi.config.ui.editor.SearchPickerPresentation;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanelNav.CategoryRow;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanelNav.MemberIssues;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.Item;
import club.heiqi.uilib.ui.scene.control.search.CategoryNavPane;
import club.heiqi.uilib.ui.scene.control.search.MemberGrid;
import club.heiqi.uilib.ui.scene.control.search.PickerInfoBar;
import club.heiqi.uilib.ui.scene.control.search.SearchResultList;
import club.heiqi.uilib.ui.scene.control.search.VariantChooser;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.text.TextEllipsizer;

/**
 * ScenePickerPanel —— 创造物品栏式 70% 居中 picker 面板（通用、平台无关、受控）。
 *
 * <h3>定位</h3>
 * <p>以旧版内联搜索选择器为功能语义基准（SINGLE_VALUE 与 LIST_MEMBERS 两模式、
 * 可拒绝的 selectionCommit、稳定 memberId、无效/重复徽章、变体 ALL/SELECTED 语义、ESC 分层、
 * 焦点意图），重塑为居中 70% 卡片上下分区布局：顶栏（搜索 + 分类维度分段 + 结果统计）、上容器选择区
 * （左分类导航 | 中 SearchResultList 无上限候选列表，列数随可用宽度自适应）、
 * 下容器已选择编辑（仅 listMembers 的全宽底部横带，当前规则列表）。面板本身不持有业务状态——
 * 开合、query、结果、当前分类、当前成员全部受控。</p>
 *
 * <h3>ESC 分层</h3>
 * <p>主面板 portal 与变体浮层 portal 独立注册；{@code SceneInputRouter} 的 ESC 优先请求栈顶
 * overlay 关闭：变体浮层开时 ESC 只退回主面板，主面板 ESC 先走 {@code onCancel} 再请求关闭
 * （经 {@code onCloseRequest} 上抛，由外部把受控 {@code open} 置 false）。</p>
 *
 * <h3>生命周期</h3>
 * <p>必须在组件构建作用域（mount builder）内创建：全部 signal / effect / portal 归属当前
 * Owner；面板关闭时 portal 子树卸载，tooltip 与变体浮层一并清理；网格高亮与滚动在数据收缩时
 * 经 owner-scoped effect 回夹。</p>
 */
public final class ScenePickerPanel {

    /** 面板可视阶段（观察用）。 */
    public enum State { CLOSED, MAIN, VARIANTS }

    /** portal 生命周期之间可回放的焦点意图。 */
    private enum FocusIntent { NONE, SEARCH_INPUT, GRID, VARIANTS }

    private static final int LABEL_FONT_SIZE = 12;
    private static final int CELL_LABEL_PADDING = 8;
    private static final int PANEL_PADDING = SceneChromeTokens.PAD_MD;
    private static final int PANEL_WIDTH_PERCENT = 70;
    private static final int PANEL_HEIGHT_PERCENT = 70;
    private static final int SEARCH_INPUT_WIDTH_PERCENT = 35;
    private static final int TOP_BAR_HEIGHT = 48;
    /** 已选择编辑底部横带高：header 48 + 两行成员卡片（96×2 + gap 8）。 */
    private static final int MEMBERS_PANEL_HEIGHT = 248;
    /** 底部横带 header 行高（含 PAD_MD 上下 padding 与 32 高按钮）。 */
    private static final int MEMBERS_HEADER_HEIGHT = 48;
    private static final OverlayDismissPolicy MAIN_PANEL_POLICY = new OverlayDismissPolicy(true, true, false);

    private ScenePickerPanel() { }

    /** 中栏虚拟网格布局参数（不可变）。 */
    @Desugar
    public record GridProps(int columns, int cellWidth, int cellHeight, int gapX, int gapY, int visibleRows) {

        /** 默认网格：自动列数铺满可用宽度、64x64 单元、5 可见行。 */
        public static final GridProps DEFAULT = new GridProps(0, 64, 64, 8, 8, 5);

        /** 创建网格布局参数。 */
        public GridProps(int columns, int cellWidth, int cellHeight, int gapX, int gapY, int visibleRows) {
            this.columns = columns;
            if (cellWidth <= 0) throw new IllegalArgumentException("cellWidth 必须 > 0");
            if (cellHeight <= 0) throw new IllegalArgumentException("cellHeight 必须 > 0");
            if (gapX < 0 || gapY < 0) throw new IllegalArgumentException("gap 不可为负数");
            if (visibleRows < 1) throw new IllegalArgumentException("visibleRows 必须 >= 1");
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.gapX = gapX;
            this.gapY = gapY;
            this.visibleRows = visibleRows;
        }

        /** 便捷工厂：指定主要布局参数。 */
        public static GridProps of(int columns, int cellWidth, int cellHeight, int gapX, int gapY,
                                   int visibleRows) {
            return new GridProps(columns, cellWidth, cellHeight, gapX, gapY, visibleRows);
        }
    }

    /** 居中 70% picker 面板输入契约。 */
    public static final class Props {
        private final ReadableSignal<String> query;
        private final ReadableSignal<SearchPickerData.SearchResult> results;
        private final ReadableSignal<Boolean> enabled;
        private final Consumer<String> onQuery;
        private final Consumer<SearchPickerData.Selection> onSelect;
        private final Predicate<SearchPickerData.Selection> selectionCommit;
        private final VisualAdapter visualAdapter;
        private final SearchPickerPresentation presentation;
        private final SearchPickerPanelPresentation panelPresentation;
        private final ReadableSignal<String> error;
        private final ReadableSignal<SearchPickerData.Selection> currentSelection;
        private final ReadableSignal<List<SearchPickerData.CurrentMember>> currentMembers;
        private final LongConsumer onEditCurrent;
        private final LongPredicate onRemoveCurrent;
        private final Runnable onBeginAdd;
        private final Runnable onCancel;
        private final boolean listMembers;
        private final ReadableSignal<Boolean> open;
        private final Runnable onCloseRequest;
        private final ReadableSignal<List<SearchPickerCategories.Category>> categories;
        private final Function<String, String> categoryOf;
        private final ReadableSignal<String> currentCategoryKey;
        private final Consumer<String> onCategoryChange;
        private final ReadableSignal<Integer> dimensionIndex;
        private final Consumer<Integer> onDimensionChange;
        private final GridProps grid;
        private final boolean variantSearchEnabled;

        /**
         * 创建受控居中 70% picker 面板属性（保留旧组件六参必填语义）。
         *
         * <p>默认：open 内部自管（经 {@link Result#openSignal()} 写入）、无分组、无维度切换、
         * 默认网格与英文文案。</p>
         */
        public Props(ReadableSignal<String> query,
                     ReadableSignal<SearchPickerData.SearchResult> results,
                     ReadableSignal<Boolean> enabled,
                     Consumer<String> onQuery,
                     Consumer<SearchPickerData.Selection> onSelect,
                     VisualAdapter visualAdapter) {
            this.query = Objects.requireNonNull(query, "query");
            this.results = Objects.requireNonNull(results, "results");
            this.enabled = Objects.requireNonNull(enabled, "enabled");
            this.onQuery = Objects.requireNonNull(onQuery, "onQuery");
            this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
            this.selectionCommit = selection -> { this.onSelect.accept(selection); return true; };
            this.visualAdapter = Objects.requireNonNull(visualAdapter, "visualAdapter");
            this.presentation = SearchPickerPresentation.defaultEnglish();
            this.panelPresentation = SearchPickerPanelPresentation.defaultEnglish();
            this.error = Signal.create("");
            this.currentSelection = Signal.create(null);
            this.currentMembers = Signal.create(Collections.<SearchPickerData.CurrentMember>emptyList());
            this.onEditCurrent = ignored -> { };
            this.onRemoveCurrent = ignored -> false;
            this.onBeginAdd = () -> { };
            this.onCancel = () -> { };
            this.listMembers = false;
            this.open = null;
            this.onCloseRequest = null;
            this.categories = Signal.create(Collections.<SearchPickerCategories.Category>emptyList());
            this.categoryOf = ignored -> null;
            this.currentCategoryKey = null;
            this.onCategoryChange = ignored -> { };
            this.dimensionIndex = null;
            this.onDimensionChange = ignored -> { };
            this.grid = GridProps.DEFAULT;
            this.variantSearchEnabled = false;
        }

        private Props(Builder builder) {
            query = builder.query; results = builder.results; enabled = builder.enabled;
            onQuery = builder.onQuery; onSelect = builder.onSelect;
            selectionCommit = builder.selectionCommit;
            visualAdapter = builder.visualAdapter;
            presentation = builder.presentation;
            panelPresentation = builder.panelPresentation;
            error = builder.error;
            currentSelection = builder.currentSelection;
            currentMembers = builder.currentMembers;
            onEditCurrent = builder.onEditCurrent;
            onRemoveCurrent = builder.onRemoveCurrent;
            onBeginAdd = builder.onBeginAdd;
            onCancel = builder.onCancel;
            listMembers = builder.listMembers;
            open = builder.open;
            onCloseRequest = builder.onCloseRequest;
            categories = builder.categories;
            categoryOf = builder.categoryOf;
            currentCategoryKey = builder.currentCategoryKey;
            onCategoryChange = builder.onCategoryChange;
            dimensionIndex = builder.dimensionIndex;
            onDimensionChange = builder.onDimensionChange;
            grid = builder.grid;
            variantSearchEnabled = builder.variantSearchEnabled;
        }

        /** 创建保留旧六参必填项的 builder。 */
        public static Builder builder(ReadableSignal<String> query,
                                      ReadableSignal<SearchPickerData.SearchResult> results,
                                      ReadableSignal<Boolean> enabled, Consumer<String> onQuery,
                                      Consumer<SearchPickerData.Selection> onSelect,
                                      VisualAdapter visualAdapter) {
            return new Builder(query, results, enabled, onQuery, onSelect, visualAdapter);
        }

        /** @return 查询文本信号 */
        public ReadableSignal<String> query() { return query; }
        /** @return 搜索结果信号 */
        public ReadableSignal<SearchPickerData.SearchResult> results() { return results; }
        /** @return 启用信号 */
        public ReadableSignal<Boolean> enabled() { return enabled; }
        /** @return 查询变更回调 */
        public Consumer<String> onQuery() { return onQuery; }
        /** @return 选择回调 */
        public Consumer<SearchPickerData.Selection> onSelect() { return onSelect; }
        /** @return 可拒绝的原子提交边界 */
        public Predicate<SearchPickerData.Selection> selectionCommit() { return selectionCommit; }
        /** @return 纯展示适配器 */
        public VisualAdapter visualAdapter() { return visualAdapter; }
        /** @return 基础领域文案 */
        public SearchPickerPresentation presentation() { return presentation; }
        /** @return 面板扩展文案 */
        public SearchPickerPanelPresentation panelPresentation() { return panelPresentation; }
        /** @return 本地错误信号 */
        public ReadableSignal<String> error() { return error; }
        /** @return 受控当前选择信号 */
        public ReadableSignal<SearchPickerData.Selection> currentSelection() { return currentSelection; }
        /** @return 当前列表成员信号 */
        public ReadableSignal<List<SearchPickerData.CurrentMember>> currentMembers() { return currentMembers; }
        /** @return 编辑当前成员回调 */
        public LongConsumer onEditCurrent() { return onEditCurrent; }
        /** @return 可拒绝的删除成员提交边界 */
        public LongPredicate onRemoveCurrent() { return onRemoveCurrent; }
        /** @return 新增目标回调 */
        public Runnable onBeginAdd() { return onBeginAdd; }
        /** @return 取消/Escape/dismiss 闭合回调 */
        public Runnable onCancel() { return onCancel; }
        /** @return 是否 LIST_MEMBERS 模式 */
        public boolean listMembers() { return listMembers; }
        /** @return 受控开合信号；null 表示内部自管 */
        public ReadableSignal<Boolean> open() { return open; }
        /** @return 面板请求关闭上抛回调；null 时内部形态自关 */
        public Runnable onCloseRequest() { return onCloseRequest; }
        /** @return 分类列表信号 */
        public ReadableSignal<List<SearchPickerCategories.Category>> categories() { return categories; }
        /** @return 候选到分类的只读分类器 */
        public Function<String, String> categoryOf() { return categoryOf; }
        /** @return 受控当前分类 key；null 表示内部自管 */
        public ReadableSignal<String> currentCategoryKey() { return currentCategoryKey; }
        /** @return 分类切换回调 */
        public Consumer<String> onCategoryChange() { return onCategoryChange; }
        /** @return 受控分类维度下标；null 表示无维度切换 */
        public ReadableSignal<Integer> dimensionIndex() { return dimensionIndex; }
        /** @return 维度切换回调 */
        public Consumer<Integer> onDimensionChange() { return onDimensionChange; }
        /** @return 中栏网格布局参数 */
        public GridProps grid() { return grid; }
        /** @return 是否启用变体搜索输入 */
        public boolean variantSearchEnabled() { return variantSearchEnabled; }

        /** 全屏 picker 面板可选属性 builder。 */
        public static final class Builder {
            private final ReadableSignal<String> query;
            private final ReadableSignal<SearchPickerData.SearchResult> results;
            private final ReadableSignal<Boolean> enabled;
            private final Consumer<String> onQuery;
            private final Consumer<SearchPickerData.Selection> onSelect;
            private Predicate<SearchPickerData.Selection> selectionCommit;
            private final VisualAdapter visualAdapter;
            private SearchPickerPresentation presentation = SearchPickerPresentation.defaultEnglish();
            private SearchPickerPanelPresentation panelPresentation =
                    SearchPickerPanelPresentation.defaultEnglish();
            private ReadableSignal<String> error = Signal.create("");
            private ReadableSignal<SearchPickerData.Selection> currentSelection = Signal.create(null);
            private ReadableSignal<List<SearchPickerData.CurrentMember>> currentMembers =
                    Signal.create(Collections.<SearchPickerData.CurrentMember>emptyList());
            private LongConsumer onEditCurrent = ignored -> { };
            private LongPredicate onRemoveCurrent = ignored -> false;
            private Runnable onBeginAdd = () -> { };
            private Runnable onCancel = () -> { };
            private boolean listMembers;
            private ReadableSignal<Boolean> open;
            private Runnable onCloseRequest;
            private ReadableSignal<List<SearchPickerCategories.Category>> categories =
                    Signal.create(Collections.<SearchPickerCategories.Category>emptyList());
            private Function<String, String> categoryOf = ignored -> null;
            private ReadableSignal<String> currentCategoryKey;
            private Consumer<String> onCategoryChange = ignored -> { };
            private ReadableSignal<Integer> dimensionIndex;
            private Consumer<Integer> onDimensionChange = ignored -> { };
            private GridProps grid = GridProps.DEFAULT;
            private boolean variantSearchEnabled;

            private Builder(ReadableSignal<String> query, ReadableSignal<SearchPickerData.SearchResult> results,
                            ReadableSignal<Boolean> enabled, Consumer<String> onQuery,
                            Consumer<SearchPickerData.Selection> onSelect, VisualAdapter visualAdapter) {
                this.query = Objects.requireNonNull(query, "query");
                this.results = Objects.requireNonNull(results, "results");
                this.enabled = Objects.requireNonNull(enabled, "enabled");
                this.onQuery = Objects.requireNonNull(onQuery, "onQuery");
                this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
                this.selectionCommit = selection -> { this.onSelect.accept(selection); return true; };
                this.visualAdapter = Objects.requireNonNull(visualAdapter, "visualAdapter");
            }

            /** 设置受控开合信号；null 表示内部自管（经 Result.openSignal 写入）。 */
            public Builder open(ReadableSignal<Boolean> value) {
                open = value; return this;
            }

            /** 设置面板请求关闭（ESC/成功提交）上抛回调；受控 open 时必须设置以真正关闭面板。 */
            public Builder onCloseRequest(Runnable value) {
                onCloseRequest = Objects.requireNonNull(value, "onCloseRequest"); return this;
            }

            /** 设置不可变领域文案。 */
            public Builder presentation(SearchPickerPresentation value) {
                presentation = Objects.requireNonNull(value, "presentation"); return this;
            }

            /** 设置不可变面板扩展文案。 */
            public Builder panelPresentation(SearchPickerPanelPresentation value) {
                panelPresentation = Objects.requireNonNull(value, "panelPresentation"); return this;
            }

            /** 设置本地错误信号。 */
            public Builder error(ReadableSignal<String> value) {
                error = Objects.requireNonNull(value, "error"); return this;
            }

            /** 设置受控当前选择。 */
            public Builder currentSelection(ReadableSignal<SearchPickerData.Selection> value) {
                currentSelection = Objects.requireNonNull(value, "currentSelection"); return this;
            }

            /** 设置可拒绝的原子提交边界；返回 false 时面板保持展开。 */
            public Builder selectionCommit(Predicate<SearchPickerData.Selection> value) {
                selectionCommit = Objects.requireNonNull(value, "selectionCommit"); return this;
            }

            /** 启用当前列表成员右侧栏，并提供稳定 memberId 点击回调。 */
            public Builder currentMembers(ReadableSignal<List<SearchPickerData.CurrentMember>> value,
                                          LongConsumer onEdit) {
                currentMembers = Objects.requireNonNull(value, "currentMembers");
                onEditCurrent = Objects.requireNonNull(onEdit, "onEditCurrent");
                listMembers = true;
                return this;
            }

            /** 设置可拒绝的稳定成员删除提交边界。 */
            public Builder onRemoveCurrent(LongPredicate value) {
                onRemoveCurrent = Objects.requireNonNull(value, "onRemoveCurrent"); return this;
            }

            /** 设置打开候选时的新增目标回调。 */
            public Builder onBeginAdd(Runnable value) {
                onBeginAdd = Objects.requireNonNull(value, "onBeginAdd"); return this;
            }

            /** 设置取消、Escape 与 dismiss 的状态闭合回调。 */
            public Builder onCancel(Runnable value) {
                onCancel = Objects.requireNonNull(value, "onCancel"); return this;
            }

            /** 设置分类列表信号（可为空列表 = 无分组）。 */
            public Builder categories(ReadableSignal<List<SearchPickerCategories.Category>> value) {
                categories = Objects.requireNonNull(value, "categories"); return this;
            }

            /** 设置候选到分类的只读分类器；缺省时全部候选视为未分类。 */
            public Builder categoryOf(Function<String, String> value) {
                categoryOf = value == null ? ignored -> null : value; return this;
            }

            /** 设置受控当前分类 key（null/空串 = 全部）与切换回调。 */
            public Builder currentCategoryKey(ReadableSignal<String> value, Consumer<String> onChange) {
                currentCategoryKey = Objects.requireNonNull(value, "currentCategoryKey");
                onCategoryChange = Objects.requireNonNull(onChange, "onCategoryChange");
                return this;
            }

            /** 设置分类维度切换（受控下标信号 + 回调）；缺省时不渲染维度分段。 */
            public Builder dimension(ReadableSignal<Integer> value, Consumer<Integer> onChange) {
                dimensionIndex = Objects.requireNonNull(value, "dimensionIndex");
                onDimensionChange = Objects.requireNonNull(onChange, "onDimensionChange");
                return this;
            }

            /** 设置中栏网格布局参数。 */
            public Builder grid(GridProps value) {
                grid = Objects.requireNonNull(value, "grid"); return this;
            }

            /** 启用变体浮层内的变体搜索输入。 */
            public Builder variantSearchEnabled(boolean value) { variantSearchEnabled = value; return this; }

            /** 构建不可变属性。 */
            public Props build() { return new Props(this); }
        }
    }

    /**
     * 创建结果。
     *
     * @param root               宿主树锚点节点（挂载到宿主布局树；面板内容经全屏 portal 提升）
     * @param openSignal         面板开合写入信号；内部自管形态（open=null）时可写，受控形态为 null
     * @param open               生效开合只读信号（受控时与外部同源）
     * @param variantsOpen       变体浮层开合只读信号
     * @param firstFocusTarget   面板当前首焦点目标（搜索输入框）；面板关闭时返回 null
     * @param grid               面板当前结果列表 viewport（SearchResultList）；面板关闭时返回 null
     * @param currentCategoryKey 生效当前分类 key（null/空串 = 全部）
     * @param gridHighlight      网格高亮只读信号
     * @param variantMode        变体草稿选择模式只读信号
     * @param variantKeys        变体草稿已选 key 只读信号
     * @param activeCandidate    变体草稿候选只读信号
     */
    @Desugar
    public record Result(
            SceneNode root,
            Signal<Boolean> openSignal,
            ReadableSignal<Boolean> open,
            ReadableSignal<Boolean> variantsOpen,
            Supplier<SceneNode> firstFocusTarget,
            Supplier<SceneNode> grid,
            ReadableSignal<String> currentCategoryKey,
            ReadableSignal<Integer> gridHighlight,
            ReadableSignal<SearchPickerData.SelectionMode> variantMode,
            ReadableSignal<List<String>> variantKeys,
            ReadableSignal<SearchPickerData.Candidate> activeCandidate) {
    }

    /**
     * 构建居中 70% picker 面板。
     *
     * <p>应在组件构建作用域（mount builder）内调用，以便所有 signal/effect/portal 归属该
     * Owner、随组件卸载一并回收；不在作用域内调用时 effect 归属 rootOwner（由
     * {@link SceneRuntime#dispose()} 兜底清理）。调用方负责把 {@link Result#root()} 挂到宿主布局树。</p>
     *
     * @param rt    场景运行时（须注入文本度量）
     * @param props 输入契约（非 null）
     * @return 创建结果（含宿主树锚点节点与观察信号）
     */
    public static Result create(SceneRuntime rt, Props props) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(props, "props");
        Signal<Boolean> openInternal = props.open() == null ? Signal.create(Boolean.FALSE) : null;
        ReadableSignal<Boolean> open = props.open() != null ? props.open() : openInternal;
        Runnable closeSignal = () -> {
            if (openInternal != null) openInternal.set(Boolean.FALSE);
        };
        Runnable closeRequest = props.onCloseRequest() != null ? props.onCloseRequest() : closeSignal;
        Signal<Boolean> variantsOpen = Signal.create(Boolean.FALSE);
        Signal<SearchPickerData.Candidate> activeCandidate = Signal.create(null);
        Signal<SearchPickerData.SelectionMode> mode = Signal.create(SearchPickerData.SelectionMode.ALL);
        Signal<List<String>> selectedKeys = Signal.create(Collections.<String>emptyList());
        Signal<Integer> gridHighlight = Signal.create(Integer.valueOf(-1));
        Signal<Boolean> addingMember = Signal.create(Boolean.FALSE);
        Signal<Boolean> editingMember = Signal.create(Boolean.FALSE);
        Signal<FocusIntent> focusIntent = Signal.create(FocusIntent.NONE);
        Signal<String> categoryInternal = Signal.create(null);
        ReadableSignal<String> categoryKey = props.currentCategoryKey() != null
                ? props.currentCategoryKey() : categoryInternal;
        Consumer<String> categoryWriter = props.currentCategoryKey() != null
                ? props.onCategoryChange() : categoryInternal::set;
        SceneNode[] searchFocusTarget = new SceneNode[1];
        SceneNode[] gridFocusTarget = new SceneNode[1];
        SceneNode[] variantFocusTarget = new SceneNode[1];
        SceneNode[] gridViewportHolder = new SceneNode[1];

        ReadableSignal<MemberIssues> memberIssues = Computed.create(() ->
                ScenePickerPanelNav.analyzeMemberIssues(safeMembers(props)));
        ReadableSignal<List<SearchPickerData.Candidate>> filtered = Computed.create(() ->
                ScenePickerPanelNav.filterByCategory(safeResults(props).candidates(),
                        categoryKey.get(), props.categoryOf()));
        ReadableSignal<List<Item>> gridItems = Computed.create(() -> {
            List<SearchPickerData.Candidate> candidates = filtered.get();
            ArrayList<Item> items = new ArrayList<Item>(candidates.size());
            for (SearchPickerData.Candidate candidate : candidates) {
                String label = TextEllipsizer.ellipsize(
                        t -> rt.measureTextWidth(t, LABEL_FONT_SIZE),
                        props.visualAdapter().candidateLabel(candidate),
                        Math.max(0, props.grid().cellWidth() - CELL_LABEL_PADDING));
                SceneImageSource image = null;
                try {
                    image = props.visualAdapter().candidateImage(candidate);
                } catch (RuntimeException exception) {
                    // 单个候选的图片源创建失败：降级无图占位，不中断整张网格。
                } catch (LinkageError error) {
                    // 同上（可选宿主类型链接失败）。
                }
                items.add(new Item(candidate.key(), image, label));
            }
            return items;
        });
        ReadableSignal<List<CategoryRow>> categoryRows = Computed.create(() ->
                ScenePickerPanelNav.categoryRows(safeCategories(props),
                        safeResults(props).candidates(), props.categoryOf(),
                        props.panelPresentation().allCategoryLabel()));

        // 开合状态机：打开时清焦点意图并引导首焦点；关闭时清理全部临时态与陈旧节点引用。
        rt.bind(open, o -> {
            if (Boolean.TRUE.equals(o)) {
                gridHighlight.set(Integer.valueOf(-1));
                focusIntent.set(FocusIntent.SEARCH_INPUT);
            } else {
                addingMember.set(Boolean.FALSE);
                editingMember.set(Boolean.FALSE);
                variantsOpen.set(Boolean.FALSE);
                activeCandidate.set(null);
                gridHighlight.set(Integer.valueOf(-1));
                focusIntent.set(FocusIntent.NONE);
                searchFocusTarget[0] = null;
                gridFocusTarget[0] = null;
                variantFocusTarget[0] = null;
                gridViewportHolder[0] = null;
            }
        });

        // 主面板 portal（全屏透明壳 + 居中 70% 卡片）：ESC/外部点击请求关闭（先 onCancel 再请求受控关闭）。
        rt.portal(open, () -> mainPanel(rt, props, closeRequest, filtered, gridItems, categoryRows,
                memberIssues, categoryKey, categoryWriter, gridHighlight,
                addingMember, editingMember, focusIntent, searchFocusTarget, gridFocusTarget,
                gridViewportHolder, variantsOpen, activeCandidate, mode, selectedKeys),
                MAIN_PANEL_POLICY,
                () -> {
                    if (Boolean.TRUE.equals(variantsOpen.get())) {
                        closeVariants(variantsOpen, activeCandidate, focusIntent);
                        return;
                    }
                    cancelPanel(props, closeRequest, variantsOpen, activeCandidate,
                            gridHighlight, addingMember, editingMember, focusIntent);
                });

        // 变体选择浮层（模块化）：mode/selectedKeys 受控，草稿查询在模块内部。
        VariantChooser.create(rt, new VariantChooser.Props(
                variantsOpen, activeCandidate, props.enabled(),
                props.variantSearchEnabled(),
                props.panelPresentation().variantPanelTitle(), props.visualAdapter(),
                mode, mode::set, selectedKeys, selectedKeys::set,
                draft -> commitSelection(props, closeRequest, variantsOpen, activeCandidate,
                        gridHighlight, addingMember, editingMember,
                        focusIntent, () -> props.selectionCommit().test(draft)),
                () -> closeVariants(variantsOpen, activeCandidate, focusIntent)));

        // 焦点意图消费（在 portal effect 之后创建：面板挂载完成后才请求权威焦点）。
        bindFocusIntent(rt, focusIntent, searchFocusTarget, gridFocusTarget, variantFocusTarget);

        // 网格高亮回夹（数据收缩/分类切换后夹到合法范围）。
        rt.bindComputed(() -> Integer.valueOf(ScenePickerPanelNav.clampHighlight(
                gridHighlight.get().intValue(), gridItems.get().size())), clamped -> {
            if (!clamped.equals(gridHighlight.get())) gridHighlight.set(clamped);
        });

        SceneNode root = new SceneNode();
        root.setHitTestable(false);
        return new Result(root, openInternal, open, variantsOpen,
                () -> searchFocusTarget[0], () -> gridViewportHolder[0], categoryKey, gridHighlight,
                mode, selectedKeys, activeCandidate);
    }

    /** 构建主面板内容：全屏透明命中穿透壳 + 居中 70% 卡片。 */
    private static SceneNode mainPanel(SceneRuntime rt, Props props, Runnable closeRequest,
                                       ReadableSignal<List<SearchPickerData.Candidate>> filtered,
                                       ReadableSignal<List<Item>> gridItems,
                                       ReadableSignal<List<CategoryRow>> categoryRows,
                                       ReadableSignal<MemberIssues> memberIssues,
                                       ReadableSignal<String> categoryKey,
                                       Consumer<String> categoryWriter,
                                       Signal<Integer> gridHighlight,
                                       Signal<Boolean> addingMember,
                                       Signal<Boolean> editingMember,
                                       Signal<FocusIntent> focusIntent,
                                       SceneNode[] searchFocusTarget,
                                       SceneNode[] gridFocusTarget,
                                       SceneNode[] gridViewportHolder,
                                       Signal<Boolean> variantsOpen,
                                       Signal<SearchPickerData.Candidate> activeCandidate,
                                       Signal<SearchPickerData.SelectionMode> mode,
                                       Signal<List<String>> selectedKeys) {
        SceneNode scrim = SceneNode.column();
        scrim.setFillParentWidth(true);
        scrim.setFillParentHeight(true);
        scrim.setMainAxisAlign(MainAxisAlign.CENTER);
        scrim.setCrossAxisAlign(CrossAxisAlign.CENTER);
        // 透明壳作为叶命中目标兜底：卡片外按下只关闭面板，不透传到下方配置页。
        rt.on(scrim, SceneEventType.POINTER_DOWN, (ev, ctx) -> {
            if (ev.getTarget() != scrim) return;
            cancelPanel(props, closeRequest, variantsOpen, activeCandidate,
                    gridHighlight, addingMember, editingMember, focusIntent);
            ctx.stopPropagation();
        });

        SceneNode root = SceneNode.column();
        root.setPercentWidth(PANEL_WIDTH_PERCENT);
        root.setPercentHeight(PANEL_HEIGHT_PERCENT);
        SceneChromeTokens.applyPanelChrome(root, SceneChromeTokens.RADIUS_LG);
        root.setPadding(PANEL_PADDING);
        root.setGap(PANEL_PADDING);

        root.appendChild(topBar(rt, props, filtered, gridItems, gridHighlight,
                addingMember, editingMember, focusIntent, searchFocusTarget));

        // 上容器：选择功能（左分类导航 | 中候选列表 + 信息条），flexGrow 占满剩余高度。
        SceneNode selectionArea = SceneNode.row();
        selectionArea.setFlexGrow(1);
        selectionArea.setGap(PANEL_PADDING);
        selectionArea.appendChild(CategoryNavPane.create(rt, new CategoryNavPane.Props(
                categoryRows, categoryKey, props.enabled(), categoryWriter,
                props.panelPresentation().emptyCategory())));
        // 悬停项：驱动信息条文本（悬浮 tooltip 已被固定信息条取代）。
        Signal<SceneVirtualGrid.Item> hoveredItem = Signal.create(null);
        selectionArea.appendChild(centerColumn(rt, props, closeRequest, filtered, gridItems,
                gridHighlight, gridFocusTarget, gridViewportHolder, hoveredItem,
                variantsOpen, activeCandidate, mode, selectedKeys,
                addingMember, editingMember, focusIntent));
        root.appendChild(selectionArea);

        // 下容器：已选择编辑（仅 listMembers 挂全宽底部横带）。
        if (props.listMembers()) {
            root.appendChild(membersPanel(rt, props, memberIssues, gridHighlight,
                    addingMember, editingMember, focusIntent, variantsOpen,
                    activeCandidate, mode, selectedKeys));
        }
        scrim.appendChild(root);
        return scrim;
    }

    /** 顶栏：标题 + 搜索输入 + 分类维度分段 + 结果统计。 */
    private static SceneNode topBar(SceneRuntime rt, Props props,
                                    ReadableSignal<List<SearchPickerData.Candidate>> filtered,
                                    ReadableSignal<List<Item>> gridItems,
                                    Signal<Integer> gridHighlight,
                                    Signal<Boolean> addingMember,
                                    Signal<Boolean> editingMember,
                                    Signal<FocusIntent> focusIntent,
                                    SceneNode[] searchFocusTarget) {
        SceneNode bar = SceneNode.row();
        bar.setPreferredHeight(TOP_BAR_HEIGHT);
        bar.setCrossAxisAlign(CrossAxisAlign.CENTER);
        bar.setGap(SceneChromeTokens.GAP_MD);
        bar.setHitTestable(false);

        SceneNode title = text(props.panelPresentation().panelTitle());
        title.setWidthSizing(WidthSizing.SHRINK);
        bar.appendChild(title);

        SceneNode input = SceneTextInput.create(rt, SceneTextInput.Props.builder(props.query())
                .enabled(props.enabled()).placeholder(props.presentation().placeholder())
                .onChange(value -> {
                    props.onQuery().accept(value);
                    gridHighlight.set(Integer.valueOf(-1));
                }).build()).get();
        input.setPercentWidth(SEARCH_INPUT_WIDTH_PERCENT);
        searchFocusTarget[0] = input;
        bar.appendChild(input);

        if (!props.panelPresentation().categoryDimensions().isEmpty() && props.dimensionIndex() != null) {
            SceneNode segmented = SceneSegmented.create(rt, new SceneSegmented.Props(
                    props.dimensionIndex(), props.panelPresentation().categoryDimensions(),
                    props.enabled(), index -> {
                        props.onDimensionChange().accept(Integer.valueOf(index));
                        gridHighlight.set(Integer.valueOf(-1));
                    })).get();
            segmented.setWidthSizing(WidthSizing.SHRINK);
            bar.appendChild(segmented);
        }

        SceneNode summary = text("");
        rt.bindText(summary, Computed.create(() -> props.presentation().resultSummary(
                filtered.get().size())));
        summary.setWidthSizing(WidthSizing.SHRINK);
        bar.appendChild(summary);
        return bar;
    }

    /** 左栏：分类导航列表（带线框外壳 + 内嵌滚动视口，选中态高亮、数量徽章、空分类隐藏）。 */
    /** 中栏：实底圆角 + 1px 外边框外壳包候选列表（SearchResultList）+ 信息条（PickerInfoBar）+ 错误行。 */
    private static SceneNode centerColumn(SceneRuntime rt, Props props, Runnable closeRequest,
                                          ReadableSignal<List<SearchPickerData.Candidate>> filtered,
                                          ReadableSignal<List<Item>> gridItems,
                                          Signal<Integer> gridHighlight,
                                          SceneNode[] gridFocusTarget,
                                          SceneNode[] gridViewportHolder,
                                          Signal<SceneVirtualGrid.Item> hoveredItem,
                                          Signal<Boolean> variantsOpen,
                                          Signal<SearchPickerData.Candidate> activeCandidate,
                                          Signal<SearchPickerData.SelectionMode> mode,
                                          Signal<List<String>> selectedKeys,
                                          Signal<Boolean> addingMember,
                                          Signal<Boolean> editingMember,
                                          Signal<FocusIntent> focusIntent) {
        SceneNode center = SceneNode.column();
        center.setFlexGrow(1);
        center.setGap(SceneChromeTokens.GAP_SM);
        SceneChromeTokens.applyPanelChrome(center, SceneChromeTokens.RADIUS_MD);
        center.setPadding(SceneChromeTokens.PAD_SM);

        SceneNode error = text("");
        error.setHitTestable(false);
        rt.bindText(error, props.error());
        center.appendChild(error);

        SearchResultList.Result list = SearchResultList.create(rt, new SearchResultList.Props(
                gridItems, props.grid().columns(), props.grid().cellWidth(), props.grid().cellHeight(),
                props.grid().gapX(), props.grid().gapY(),
                props.enabled(),
                item -> activateCandidate(item.key(), props, closeRequest, filtered, variantsOpen,
                        activeCandidate, mode, selectedKeys, gridHighlight,
                        addingMember, editingMember, focusIntent),
                gridHighlight, gridHighlight::set,
                hoveredItem::set));
        // root = stackHost（viewport + 右侧滚动条），fillParentHeight 占满中栏剩余高度
        //（scrollable 子节点不能走 flexGrow 分配，模块内已对 root 设置）。
        gridViewportHolder[0] = list.viewport();
        gridFocusTarget[0] = list.viewport();
        rt.focusable(list.viewport(), props.enabled());
        center.appendChild(list.root());

        // 固定信息条：悬停项完整 label + 稳定 key（悬浮 tooltip 的替代物，无浮层生命周期）。
        ReadableSignal<String> infoText = Computed.create(() -> {
            SceneVirtualGrid.Item item = hoveredItem.get();
            if (item == null) return "";
            String label = fullLabelAt(filtered.get(), item.key());
            String stableKey = String.valueOf(item.key());
            String prefix = props.panelPresentation().tooltipPrefix();
            return prefix.isEmpty() ? label + "\n" + stableKey : label + "\n" + prefix + stableKey;
        });
        center.appendChild(PickerInfoBar.create(rt, new PickerInfoBar.Props(infoText, props.enabled())));
        return center;
    }

    /** 下容器（listMembers）：已选择编辑全宽底部横带。 */
    private static SceneNode membersPanel(SceneRuntime rt, Props props,
                                          ReadableSignal<MemberIssues> memberIssues,
                                          Signal<Integer> gridHighlight,
                                          Signal<Boolean> addingMember,
                                          Signal<Boolean> editingMember,
                                          Signal<FocusIntent> focusIntent,
                                          Signal<Boolean> variantsOpen,
                                          Signal<SearchPickerData.Candidate> activeCandidate,
                                          Signal<SearchPickerData.SelectionMode> mode,
                                          Signal<List<String>> selectedKeys) {
        SceneNode panel = SceneNode.column();
        panel.setPreferredHeight(MEMBERS_PANEL_HEIGHT);
        SceneChromeTokens.applyOuterShell(panel, SceneChromeTokens.RADIUS_MD);

        SceneNode header = SceneNode.row();
        header.setPreferredHeight(MEMBERS_HEADER_HEIGHT);
        header.setPadding(SceneChromeTokens.PAD_MD);
        header.setCrossAxisAlign(CrossAxisAlign.CENTER);
        header.setGap(SceneChromeTokens.GAP_MD);
        header.setHitTestable(false);
        SceneNode title = text("");
        title.setFlexGrow(1);
        rt.bindText(title, Computed.create(() -> props.presentation().currentMembersTitle(
                safeMembers(props).size())));
        header.appendChild(title);
        SceneNode issues = text("");
        issues.setWidthSizing(WidthSizing.SHRINK);
        rt.bindText(issues, Computed.create(() -> props.presentation().memberIssueSummary(
                memberIssues.get().invalidCount(), memberIssues.get().duplicateMemberIds().size())));
        header.appendChild(issues);
        // 无「添加」按钮：点击上方候选即隐式新增（armed/unarmed 语义已并入 prepare 逻辑）。
        panel.appendChild(header);

        // 已选择成员：多列网格 + 可见滚动条（MemberGrid 模块，替代旧单列行）。
        ReadableSignal<List<SearchPickerData.CurrentMember>> members =
                Computed.create(() -> safeMembers(props));
        MemberGrid.Result grid = MemberGrid.create(rt, new MemberGrid.Props(
                members, props.enabled(), props.presentation(), props.visualAdapter(),
                memberIssues,
                memberId -> editMember(props, memberId, addingMember,
                        editingMember, focusIntent, variantsOpen, activeCandidate, mode, selectedKeys,
                        gridHighlight),
                memberId -> removeMember(props, memberId, addingMember, editingMember,
                        variantsOpen, activeCandidate, gridHighlight, focusIntent),
                MemberGrid.DEFAULT_CELL_WIDTH, MemberGrid.DEFAULT_CELL_HEIGHT,
                MemberGrid.DEFAULT_GAP_X, MemberGrid.DEFAULT_GAP_Y));
        grid.root().setFlexGrow(1);
        panel.appendChild(grid.root());
        rt.show(panel, Computed.create(() -> Boolean.valueOf(members.get().isEmpty())),
                () -> emptyText(props.presentation().emptyCurrentMembers()));
        return panel;
    }

    /**
     * 删除成员（MemberGrid 回调）：宿主提交成功后才清理面板临时态（武装/编辑/变体浮层/
     * 网格高亮/焦点意图），与 finishSelection/cancelPanel 的收尾集合对齐；宿主拒绝时零推进。
     */
    private static boolean removeMember(Props props, long memberId,
                                        Signal<Boolean> addingMember, Signal<Boolean> editingMember,
                                        Signal<Boolean> variantsOpen,
                                        Signal<SearchPickerData.Candidate> activeCandidate,
                                        Signal<Integer> gridHighlight, Signal<FocusIntent> focusIntent) {
        if (!props.onRemoveCurrent().test(memberId)) {
            return false;
        }
        addingMember.set(Boolean.FALSE);
        editingMember.set(Boolean.FALSE);
        variantsOpen.set(Boolean.FALSE);
        activeCandidate.set(null);
        gridHighlight.set(Integer.valueOf(-1));
        focusIntent.set(FocusIntent.GRID);
        return true;
    }

    /**
     * 编辑成员（MemberGrid 回调）：进入编辑态；带变体的成员预开变体浮层，否则引导回网格。
     */
    private static void editMember(Props props, long memberId,
                                   Signal<Boolean> addingMember, Signal<Boolean> editingMember,
                                   Signal<FocusIntent> focusIntent, Signal<Boolean> variantsOpen,
                                   Signal<SearchPickerData.Candidate> activeCandidate,
                                   Signal<SearchPickerData.SelectionMode> mode,
                                   Signal<List<String>> selectedKeys, Signal<Integer> gridHighlight) {
        addingMember.set(Boolean.FALSE);
        editingMember.set(Boolean.TRUE);
        props.onEditCurrent().accept(memberId);
        SearchPickerData.CurrentMember current = memberById(props, memberId, null);
        SearchPickerData.Candidate candidate = current == null ? null : current.candidate();
        if (candidate != null && !candidate.variants().isEmpty()) {
            SearchPickerData.Selection selection = current.selection();
            mode.set(selection == null ? SearchPickerData.SelectionMode.ALL : selection.mode());
            selectedKeys.set(selection == null ? Collections.<String>emptyList()
                    : immutableKeys(selection.variantKeys()));
            activeCandidate.set(candidate);
            variantsOpen.set(Boolean.TRUE);
            focusIntent.set(FocusIntent.VARIANTS);
        } else {
            activeCandidate.set(null);
            gridHighlight.set(Integer.valueOf(-1));
            focusIntent.set(FocusIntent.GRID);
        }
    }

    /** 点击/ENTER 激活候选：无变体直达 selectionCommit，有变体开变体浮层。 */
    private static void activateCandidate(Object key, Props props, Runnable closeRequest,
                                          ReadableSignal<List<SearchPickerData.Candidate>> filtered,
                                          Signal<Boolean> variantsOpen,
                                          Signal<SearchPickerData.Candidate> activeCandidate,
                                          Signal<SearchPickerData.SelectionMode> mode,
                                          Signal<List<String>> selectedKeys,
                                          Signal<Integer> gridHighlight,
                                          Signal<Boolean> addingMember,
                                          Signal<Boolean> editingMember,
                                          Signal<FocusIntent> focusIntent) {
        SearchPickerData.Candidate candidate = candidateByKey(filtered.get(), key);
        if (candidate == null) return;
        if (candidate.variants().isEmpty()) {
            // 无变体直达提交：listMembers 未武装（非新增/非编辑）时点击即隐式新增。
            // 隐式武装与重武装决策由 commitSelection 单点承载（局部布尔规避帧末批处理陷阱）。
            commitSelection(props, closeRequest, variantsOpen, activeCandidate, gridHighlight,
                    addingMember, editingMember, focusIntent,
                    () -> props.selectionCommit().test(new SearchPickerData.Selection(candidate.key(),
                            SearchPickerData.SelectionMode.ALL, Collections.<String>emptyList())));
        } else {
            SearchPickerData.Selection current = props.currentSelection().get();
            boolean restore = current != null && candidate.key().equals(current.candidateKey());
            mode.set(restore ? current.mode() : SearchPickerData.SelectionMode.ALL);
            selectedKeys.set(restore ? immutableKeys(current.variantKeys())
                    : Collections.<String>emptyList());
            activeCandidate.set(candidate);
            variantsOpen.set(Boolean.TRUE);
            focusIntent.set(FocusIntent.VARIANTS);
        }
    }

    /**
     * 选择提交单点：listMembers 未武装（非新增/非编辑）时隐式进入新增态，提交成功后按
     * 「listMembers 且本次处于新增」重武装留在面板，否则请求关闭。
     *
     * <p>隐式武装与重武装决策用局部布尔计算，不依赖同帧读回（Signal.set 帧末批处理生效）。</p>
     */
    private static void commitSelection(Props props, Runnable closeRequest, Signal<Boolean> variantsOpen,
                                        Signal<SearchPickerData.Candidate> activeCandidate,
                                        Signal<Integer> gridHighlight,
                                        Signal<Boolean> addingMember,
                                        Signal<Boolean> editingMember, Signal<FocusIntent> focusIntent,
                                        Supplier<Boolean> tryCommit) {
        boolean armedNow = Boolean.TRUE.equals(addingMember.get());
        boolean implicitArm = props.listMembers() && !armedNow
                && !Boolean.TRUE.equals(editingMember.get());
        if (implicitArm) {
            beginAdd(props, addingMember, editingMember);
        }
        if (Boolean.TRUE.equals(tryCommit.get())) {
            finishSelection(props, closeRequest, variantsOpen, activeCandidate,
                    gridHighlight, addingMember, editingMember,
                    props.listMembers() && (armedNow || implicitArm), focusIntent);
        }
    }

    /** 成功提交后的收尾：listMembers 新增成功留在面板重新武装，其余请求关闭。 */
    private static void finishSelection(Props props, Runnable closeRequest, Signal<Boolean> variantsOpen,
                                        Signal<SearchPickerData.Candidate> activeCandidate,
                                        Signal<Integer> gridHighlight,
                                        Signal<Boolean> addingMember,
                                        Signal<Boolean> editingMember, boolean rearmAdd,
                                        Signal<FocusIntent> focusIntent) {
        if (rearmAdd) {
            beginAdd(props, addingMember, editingMember);
            gridHighlight.set(Integer.valueOf(-1));
            variantsOpen.set(Boolean.FALSE);
            activeCandidate.set(null);
            focusIntent.set(FocusIntent.GRID);
            return;
        }
        variantsOpen.set(Boolean.FALSE);
        activeCandidate.set(null);
        gridHighlight.set(Integer.valueOf(-1));
        addingMember.set(Boolean.FALSE);
        editingMember.set(Boolean.FALSE);
        focusIntent.set(FocusIntent.NONE);
        closeRequest.run();
    }

    /** ESC/dismiss 取消：先 onCancel 再请求受控关闭（恒走关闭分支，不落入新增重武装）。 */
    private static void cancelPanel(Props props, Runnable closeRequest, Signal<Boolean> variantsOpen,
                                    Signal<SearchPickerData.Candidate> activeCandidate,
                                    Signal<Integer> gridHighlight,
                                    Signal<Boolean> addingMember,
                                    Signal<Boolean> editingMember, Signal<FocusIntent> focusIntent) {
        props.onCancel().run();
        variantsOpen.set(Boolean.FALSE);
        activeCandidate.set(null);
        gridHighlight.set(Integer.valueOf(-1));
        addingMember.set(Boolean.FALSE);
        editingMember.set(Boolean.FALSE);
        focusIntent.set(FocusIntent.NONE);
        closeRequest.run();
    }

    private static void closeVariants(Signal<Boolean> variantsOpen,
                                      Signal<SearchPickerData.Candidate> activeCandidate,
                                      Signal<FocusIntent> focusIntent) {
        variantsOpen.set(Boolean.FALSE);
        activeCandidate.set(null);
        focusIntent.set(FocusIntent.GRID);
    }

    private static void beginAdd(Props props,
                                 Signal<Boolean> addingMember, Signal<Boolean> editingMember) {
        addingMember.set(Boolean.TRUE);
        editingMember.set(Boolean.FALSE);
        props.onBeginAdd().run();
    }

    private static void bindFocusIntent(SceneRuntime rt, Signal<FocusIntent> intent,
                                        SceneNode[] search, SceneNode[] grid, SceneNode[] variants) {
        rt.bind(intent, value -> {
            SceneNode target = null;
            if (value == FocusIntent.SEARCH_INPUT) target = search[0];
            else if (value == FocusIntent.GRID) target = grid[0];
            else if (value == FocusIntent.VARIANTS) target = variants[0];
            if (target != null && rt.requestFocus(target)) intent.set(FocusIntent.NONE);
        });
    }

    // ==================== 纯读取助手 ====================

    private static SearchPickerData.SearchResult safeResults(Props props) {
        SearchPickerData.SearchResult value = props.results().get();
        return value == null ? SearchPickerData.SearchResult.empty() : value;
    }

    private static List<SearchPickerData.CurrentMember> safeMembers(Props props) {
        List<SearchPickerData.CurrentMember> value = props.currentMembers().get();
        return value == null ? Collections.<SearchPickerData.CurrentMember>emptyList() : value;
    }

    private static List<SearchPickerCategories.Category> safeCategories(Props props) {
        List<SearchPickerCategories.Category> value = props.categories().get();
        return value == null ? Collections.<SearchPickerCategories.Category>emptyList() : value;
    }

    private static SearchPickerData.CurrentMember memberById(
            Props props, long memberId, SearchPickerData.CurrentMember fallback) {
        for (SearchPickerData.CurrentMember member : safeMembers(props)) {
            if (member.memberId() == memberId) return member;
        }
        return fallback;
    }

    private static SearchPickerData.Candidate candidateByKey(
            List<SearchPickerData.Candidate> candidates, Object key) {
        String target = String.valueOf(key);
        for (SearchPickerData.Candidate candidate : candidates) {
            if (candidate.key().equals(target)) return candidate;
        }
        return null;
    }

    private static String fullLabelAt(List<SearchPickerData.Candidate> candidates, Object key) {
        String target = String.valueOf(key);
        for (SearchPickerData.Candidate candidate : candidates) {
            if (candidate.key().equals(target)) return candidate.label();
        }
        return target;
    }

    private static List<String> immutableKeys(List<String> keys) {
        return Collections.unmodifiableList(new ArrayList<String>(keys));
    }

    private static SceneNode text(String value) {
        SceneNode node = new SceneNode();
        node.setText(value == null ? "" : value);
        node.setHitTestable(false);
        return node;
    }

    private static SceneNode emptyText(String value) {
        SceneNode node = text(value);
        node.setPadding(SceneChromeTokens.PAD_MD);
        return node;
    }
}
