package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanelNav.CategoryRow;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanelNav.MemberIssues;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.Item;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.WindowModel;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.WindowRow;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.text.TextEllipsizer;

/**
 * ScenePickerPanel —— 创造物品栏式 70% 居中 picker 面板（通用、平台无关、受控）。
 *
 * <h3>定位</h3>
 * <p>以旧版内联搜索选择器为功能语义基准（SINGLE_VALUE 与 LIST_MEMBERS 两模式、
 * 可拒绝的 selectionCommit、稳定 memberId、无效/重复徽章、变体 ALL/SELECTED 语义、ESC 分层、
 * 焦点意图），重塑为居中 70% 卡片上下分区布局：顶栏（搜索 + 分类维度分段 + 结果统计）、上容器选择区
 * （左分类导航 | 中 {@link SceneVirtualGrid} 候选网格，网格列数随可用宽度自适应、行数随选择区高度自适应）、
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
    private static final int CATEGORY_NAV_WIDTH = 168;
    private static final int CATEGORY_ROW_HEIGHT = 34;
    /** 已选择编辑底部横带高：保证 3~4 行 MEMBER_ROW_HEIGHT 可见。 */
    private static final int MEMBERS_PANEL_HEIGHT = 192;
    /** 底部横带 header 行高（含 PAD_MD 上下 padding 与 32 高按钮）。 */
    private static final int MEMBERS_HEADER_HEIGHT = 48;
    private static final int MEMBER_ROW_HEIGHT = 48;
    private static final int MEMBER_ICON_SIZE = 24;
    private static final int MEMBER_ACTIONS_WIDTH = 180;
    private static final int VARIANT_CARD_WIDTH = 440;
    private static final int VARIANT_LIST_HEIGHT = 240;
    private static final int VARIANT_ROW_HEIGHT = 34;
    private static final int VARIANT_ICON_SIZE = 18;
    private static final int OVERLAY_SCRIM = 0xCC000000;
    private static final int PLACEHOLDER_COLOR = SceneVirtualGrid.DEFAULT_PLACEHOLDER_COLOR;
    private static final OverlayDismissPolicy MAIN_PANEL_POLICY = new OverlayDismissPolicy(true, true, false);
    private static final OverlayDismissPolicy VARIANT_PANEL_POLICY = new OverlayDismissPolicy(true, false, false);

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
     * @param grid               面板当前网格结果；面板关闭时返回 null
     * @param currentCategoryKey 生效当前分类 key（null/空串 = 全部）
     * @param gridHighlight      网格高亮只读信号
     * @param variantMode        变体草稿选择模式只读信号
     * @param variantKeys        变体草稿已选 key 只读信号
     * @param activeCandidate    变体草稿候选只读信号
     * @param dynamicRows        网格动态可见行数只读信号（初值 = GridProps.visibleRows() 兜底，
     *                           布局完成后随选择区高度自适应）
     */
    @Desugar
    public record Result(
            SceneNode root,
            Signal<Boolean> openSignal,
            ReadableSignal<Boolean> open,
            ReadableSignal<Boolean> variantsOpen,
            Supplier<SceneNode> firstFocusTarget,
            Supplier<SceneVirtualGrid.Result> grid,
            ReadableSignal<String> currentCategoryKey,
            ReadableSignal<Integer> gridHighlight,
            ReadableSignal<SearchPickerData.SelectionMode> variantMode,
            ReadableSignal<List<String>> variantKeys,
            ReadableSignal<SearchPickerData.Candidate> activeCandidate,
            ReadableSignal<Integer> dynamicRows) {
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
        Signal<String> variantQuery = Signal.create("");
        Signal<Integer> gridHighlight = Signal.create(Integer.valueOf(-1));
        Signal<Long> pendingDeleteMemberId = Signal.create(null);
        Signal<Boolean> addingMember = Signal.create(Boolean.FALSE);
        Signal<FocusIntent> focusIntent = Signal.create(FocusIntent.NONE);
        Signal<String> categoryInternal = Signal.create(null);
        // 网格动态可见行数：首帧布局前用 GridProps.visibleRows() 兜底，布局完成后随选择区高度自适应。
        Signal<Integer> dynamicRows = Signal.create(Integer.valueOf(props.grid().visibleRows()));
        ReadableSignal<String> categoryKey = props.currentCategoryKey() != null
                ? props.currentCategoryKey() : categoryInternal;
        Consumer<String> categoryWriter = props.currentCategoryKey() != null
                ? props.onCategoryChange() : categoryInternal::set;
        SceneNode[] searchFocusTarget = new SceneNode[1];
        SceneNode[] gridFocusTarget = new SceneNode[1];
        SceneNode[] variantFocusTarget = new SceneNode[1];
        SceneVirtualGrid.Result[] gridHolder = new SceneVirtualGrid.Result[1];

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
                items.add(new Item(candidate.key(), props.visualAdapter().candidateImage(candidate),
                        label));
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
                pendingDeleteMemberId.set(null);
                addingMember.set(Boolean.FALSE);
                variantsOpen.set(Boolean.FALSE);
                activeCandidate.set(null);
                variantQuery.set("");
                gridHighlight.set(Integer.valueOf(-1));
                focusIntent.set(FocusIntent.NONE);
                searchFocusTarget[0] = null;
                gridFocusTarget[0] = null;
                variantFocusTarget[0] = null;
                gridHolder[0] = null;
            }
        });

        // 主面板 portal（全屏透明壳 + 居中 70% 卡片）：ESC/外部点击请求关闭（先 onCancel 再请求受控关闭）。
        rt.portal(open, () -> mainPanel(rt, props, closeRequest, filtered, gridItems, categoryRows,
                memberIssues, categoryKey, categoryWriter, gridHighlight, pendingDeleteMemberId,
                addingMember, focusIntent, searchFocusTarget, gridFocusTarget, gridHolder,
                variantsOpen, activeCandidate, mode, selectedKeys, variantQuery, dynamicRows),
                MAIN_PANEL_POLICY,
                () -> {
                    if (Boolean.TRUE.equals(variantsOpen.get())) {
                        closeVariants(variantsOpen, activeCandidate, variantQuery, focusIntent);
                        return;
                    }
                    cancelPanel(props, closeRequest, variantsOpen, activeCandidate, variantQuery,
                            gridHighlight, pendingDeleteMemberId, addingMember, focusIntent);
                });

        // 变体浮层次级 portal：ESC 只退回主面板。
        rt.portal(variantsOpen, () -> variantPanel(rt, props, closeRequest, activeCandidate, mode,
                selectedKeys, variantQuery, variantsOpen, gridHighlight, pendingDeleteMemberId,
                addingMember, focusIntent, variantFocusTarget), VARIANT_PANEL_POLICY,
                () -> closeVariants(variantsOpen, activeCandidate, variantQuery, focusIntent));

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
                () -> searchFocusTarget[0], () -> gridHolder[0], categoryKey, gridHighlight,
                mode, selectedKeys, activeCandidate, dynamicRows);
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
                                       Signal<Long> pendingDeleteMemberId,
                                       Signal<Boolean> addingMember,
                                       Signal<FocusIntent> focusIntent,
                                       SceneNode[] searchFocusTarget,
                                       SceneNode[] gridFocusTarget,
                                       SceneVirtualGrid.Result[] gridHolder,
                                       Signal<Boolean> variantsOpen,
                                       Signal<SearchPickerData.Candidate> activeCandidate,
                                       Signal<SearchPickerData.SelectionMode> mode,
                                       Signal<List<String>> selectedKeys,
                                       Signal<String> variantQuery,
                                       Signal<Integer> dynamicRows) {
        SceneNode scrim = SceneNode.column();
        scrim.setFillParentWidth(true);
        scrim.setFillParentHeight(true);
        scrim.setMainAxisAlign(MainAxisAlign.CENTER);
        scrim.setCrossAxisAlign(CrossAxisAlign.CENTER);
        // 透明壳作为叶命中目标兜底：卡片外按下只关闭面板，不透传到下方配置页。
        rt.on(scrim, SceneEventType.POINTER_DOWN, (ev, ctx) -> {
            if (ev.getTarget() != scrim) return;
            cancelPanel(props, closeRequest, variantsOpen, activeCandidate, variantQuery,
                    gridHighlight, pendingDeleteMemberId, addingMember, focusIntent);
            ctx.stopPropagation();
        });

        SceneNode root = SceneNode.column();
        root.setPercentWidth(PANEL_WIDTH_PERCENT);
        root.setPercentHeight(PANEL_HEIGHT_PERCENT);
        root.setBackgroundColor(SceneChromeTokens.BG_DEFAULT);
        root.setBorderWidth(1);
        root.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);
        root.setCornerRadius(SceneChromeTokens.RADIUS_LG);
        root.setClipChildren(true);
        root.setPadding(PANEL_PADDING);
        root.setGap(PANEL_PADDING);

        root.appendChild(topBar(rt, props, closeRequest, filtered, gridItems, gridHighlight,
                pendingDeleteMemberId, addingMember, focusIntent, searchFocusTarget, gridHolder,
                variantsOpen, activeCandidate, mode, selectedKeys, variantQuery));

        // 上容器：选择功能（左分类导航 | 中候选网格），flexGrow 占满剩余高度。
        SceneNode selectionArea = SceneNode.row();
        selectionArea.setFlexGrow(1);
        selectionArea.setGap(PANEL_PADDING);
        selectionArea.appendChild(categoryNav(rt, props, categoryRows, categoryKey, categoryWriter,
                gridHighlight, gridHolder));
        selectionArea.appendChild(centerColumn(rt, props, closeRequest, filtered, gridItems,
                gridHighlight, gridFocusTarget, gridHolder, variantsOpen, activeCandidate, mode,
                selectedKeys, variantQuery, pendingDeleteMemberId, addingMember, focusIntent,
                dynamicRows));
        root.appendChild(selectionArea);

        // 下容器：已选择编辑（仅 listMembers 挂全宽底部横带）。
        if (props.listMembers()) {
            root.appendChild(membersPanel(rt, props, memberIssues, gridHighlight,
                    pendingDeleteMemberId, addingMember, focusIntent, variantsOpen, activeCandidate,
                    mode, selectedKeys, variantQuery));
        }
        attachCellTooltips(rt, props, filtered, gridItems, gridHolder);
        scrim.appendChild(root);
        return scrim;
    }

    /** 顶栏：标题 + 搜索输入 + 分类维度分段 + 结果统计。 */
    private static SceneNode topBar(SceneRuntime rt, Props props, Runnable closeRequest,
                                    ReadableSignal<List<SearchPickerData.Candidate>> filtered,
                                    ReadableSignal<List<Item>> gridItems,
                                    Signal<Integer> gridHighlight,
                                    Signal<Long> pendingDeleteMemberId,
                                    Signal<Boolean> addingMember,
                                    Signal<FocusIntent> focusIntent,
                                    SceneNode[] searchFocusTarget,
                                    SceneVirtualGrid.Result[] gridHolder,
                                    Signal<Boolean> variantsOpen,
                                    Signal<SearchPickerData.Candidate> activeCandidate,
                                    Signal<SearchPickerData.SelectionMode> mode,
                                    Signal<List<String>> selectedKeys,
                                    Signal<String> variantQuery) {
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
                    if (props.listMembers() && pendingDeleteMemberId.get() != null) {
                        beginAdd(props, pendingDeleteMemberId, addingMember);
                    }
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
                        SceneVirtualGrid.Result grid = gridHolder[0];
                        if (grid != null) grid.scrollSignal().set(Integer.valueOf(0));
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
    private static SceneNode categoryNav(SceneRuntime rt, Props props,
                                         ReadableSignal<List<CategoryRow>> categoryRows,
                                         ReadableSignal<String> categoryKey,
                                         Consumer<String> categoryWriter,
                                         Signal<Integer> gridHighlight,
                                         SceneVirtualGrid.Result[] gridHolder) {
        SceneNode nav = SceneNode.column();
        nav.setPreferredWidth(CATEGORY_NAV_WIDTH);
        nav.setFillParentHeight(true);
        nav.setBorderWidth(1);
        nav.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);
        nav.setCornerRadius(SceneChromeTokens.RADIUS_MD);
        nav.setPadding(1);
        nav.setClipChildren(true);
        nav.setHitTestable(false);

        // 滚动视口嵌在 1px 线框内侧，行背景不会覆盖外壳边框。
        SceneNode viewport = SceneNode.column();
        viewport.setFillParentHeight(true);
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        viewport.setHitTestable(false);
        SceneScrolls.attach(rt, viewport);
        nav.appendChild(viewport);

        SceneNode rows = SceneNode.column();
        rows.setHitTestable(false);
        viewport.appendChild(rows);
        rt.forEach(rows, categoryRows, CategoryRow::identityKey,
                row -> categoryRow(rt, props, row, categoryRows, categoryKey, categoryWriter,
                        gridHighlight, gridHolder));
        rt.show(viewport, Computed.create(() -> Boolean.valueOf(categoryRows.get().isEmpty())),
                () -> emptyText(props.panelPresentation().emptyCategory()));
        return nav;
    }

    private static SceneNode categoryRow(SceneRuntime rt, Props props, CategoryRow initialRow,
                                         ReadableSignal<List<CategoryRow>> categoryRows,
                                         ReadableSignal<String> categoryKey,
                                         Consumer<String> categoryWriter,
                                         Signal<Integer> gridHighlight,
                                         SceneVirtualGrid.Result[] gridHolder) {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(CATEGORY_ROW_HEIGHT);
        row.setCrossAxisAlign(CrossAxisAlign.CENTER);
        row.setGap(SceneChromeTokens.GAP_SM);
        row.setPadding(0, SceneChromeTokens.PAD_MD, 0, SceneChromeTokens.PAD_MD);
        SceneInteractionState interaction = rt.interactionState(row);
        ReadableSignal<Boolean> selected = Computed.create(() -> {
            String current = categoryKey.get();
            boolean currentAll = current == null || current.isEmpty();
            return Boolean.valueOf(initialRow.all ? currentAll : initialRow.key.equals(current));
        });
        SceneControlChrome.bindSelectableBackground(rt, row, props.enabled(), selected, interaction);
        SceneNode label = text("");
        label.setFlexGrow(1);
        label.setHitTestable(false);
        rt.bindText(label, Computed.create(() -> labelAt(categoryRows.get(), initialRow)));
        row.appendChild(label);
        SceneNode count = text("");
        count.setWidthSizing(WidthSizing.SHRINK);
        count.setHitTestable(false);
        count.setFontSize(LABEL_FONT_SIZE);
        count.setTextColor(SceneChromeTokens.TEXT_SECONDARY);
        rt.bindText(count, Computed.create(() -> String.valueOf(countAt(categoryRows.get(), initialRow))));
        row.appendChild(count);
        rt.on(row, SceneEventType.CLICK, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) return;
            String next = initialRow.all ? null : initialRow.key;
            categoryWriter.accept(next);
            gridHighlight.set(Integer.valueOf(-1));
            SceneVirtualGrid.Result grid = gridHolder[0];
            if (grid != null) grid.scrollSignal().set(Integer.valueOf(0));
            ctx.stopPropagation();
        });
        return row;
    }

    /** 中栏：候选网格 + 错误行；网格可见行数随本列高度自适应（经 dynamicRows 覆盖传入）。 */
    private static SceneNode centerColumn(SceneRuntime rt, Props props, Runnable closeRequest,
                                          ReadableSignal<List<SearchPickerData.Candidate>> filtered,
                                          ReadableSignal<List<Item>> gridItems,
                                          Signal<Integer> gridHighlight,
                                          SceneNode[] gridFocusTarget,
                                          SceneVirtualGrid.Result[] gridHolder,
                                          Signal<Boolean> variantsOpen,
                                          Signal<SearchPickerData.Candidate> activeCandidate,
                                          Signal<SearchPickerData.SelectionMode> mode,
                                          Signal<List<String>> selectedKeys,
                                          Signal<String> variantQuery,
                                          Signal<Long> pendingDeleteMemberId,
                                          Signal<Boolean> addingMember,
                                          Signal<FocusIntent> focusIntent,
                                          Signal<Integer> dynamicRows) {
        SceneNode center = SceneNode.column();
        center.setFlexGrow(1);
        center.setGap(SceneChromeTokens.GAP_SM);

        SceneNode error = text("");
        error.setHitTestable(false);
        rt.bindText(error, props.error());

        // 高度自适应：布局完成后经 layoutDoneSignal 读取本列 LayoutBox 高度（参考
        // SceneVirtualGrid 自动列数的读取范式），按「可用高 - 错误行高余量」折算可见行数。
        // 仅在行数变化时写 dynamicRows，避免无谓的 viewport 重排。
        rt.bind(rt.layoutDoneSignal(), epoch -> Effect.untrack(() -> {
            Object cached = center.getCachedLayout();
            if (!(cached instanceof LayoutBox)) return;
            int available = ((LayoutBox) cached).getHeight();
            int stride = props.grid().cellHeight() + props.grid().gapY();
            int reserve = rt.lineHeight(error.getFontSize()) + SceneChromeTokens.GAP_SM;
            int rows = Math.max(1, (available - reserve) / stride);
            if (rows != dynamicRows.get().intValue()) {
                dynamicRows.set(Integer.valueOf(rows));
            }
        }));

        SceneVirtualGrid.Result grid = SceneVirtualGrid.create(rt, new SceneVirtualGrid.Props(
                gridItems, props.grid().columns(), props.grid().cellWidth(), props.grid().cellHeight(),
                props.grid().gapX(), props.grid().gapY(), props.grid().visibleRows(), props.enabled(),
                item -> activateCandidate(item.key(), props, closeRequest, filtered, variantsOpen,
                        activeCandidate, mode, selectedKeys, variantQuery, gridHighlight,
                        pendingDeleteMemberId, addingMember, focusIntent),
                gridHighlight, gridHighlight::set), dynamicRows);
        gridHolder[0] = grid;
        gridFocusTarget[0] = grid.viewport();
        rt.focusable(grid.viewport(), props.enabled());
        center.appendChild(grid.viewport());

        rt.on(grid.viewport(), SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())
                    || ev.getKeyAction() != SceneKeyAction.PRESSED || ev.isRepeat()) return;
            if (ev.getKey() == SceneKey.ENTER) {
                List<Item> items = gridItems.get();
                int index = gridHighlight.get().intValue();
                if (index >= 0 && index < items.size()) {
                    activateCandidate(items.get(index).key(), props, closeRequest, filtered,
                            variantsOpen, activeCandidate, mode, selectedKeys, variantQuery,
                            gridHighlight, pendingDeleteMemberId, addingMember, focusIntent);
                    ctx.stopPropagation();
                }
            }
        });

        center.appendChild(error);
        return center;
    }

    /** 下容器（listMembers）：已选择编辑全宽底部横带。 */
    private static SceneNode membersPanel(SceneRuntime rt, Props props,
                                          ReadableSignal<MemberIssues> memberIssues,
                                          Signal<Integer> gridHighlight,
                                          Signal<Long> pendingDeleteMemberId,
                                          Signal<Boolean> addingMember,
                                          Signal<FocusIntent> focusIntent,
                                          Signal<Boolean> variantsOpen,
                                          Signal<SearchPickerData.Candidate> activeCandidate,
                                          Signal<SearchPickerData.SelectionMode> mode,
                                          Signal<List<String>> selectedKeys,
                                          Signal<String> variantQuery) {
        SceneNode panel = SceneNode.column();
        panel.setPreferredHeight(MEMBERS_PANEL_HEIGHT);
        panel.setBorderWidth(1);
        panel.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);
        panel.setCornerRadius(SceneChromeTokens.RADIUS_MD);
        panel.setClipChildren(true);

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
                memberIssues.get().invalidCount, memberIssues.get().duplicateMemberIds.size())));
        header.appendChild(issues);
        SceneNode add = SceneButton.create(rt, new SceneButton.Props(
                Signal.create(props.panelPresentation().addMember()), props.enabled(), () -> {
                    props.onQuery().accept("");
                    beginAdd(props, pendingDeleteMemberId, addingMember);
                    gridHighlight.set(Integer.valueOf(-1));
                    focusIntent.set(FocusIntent.GRID);
                })).get();
        add.setWidthSizing(WidthSizing.SHRINK);
        header.appendChild(add);
        panel.appendChild(header);

        SceneNode rows = SceneNode.column();
        rows.setFlexGrow(1);
        rows.setScrollable(true);
        rows.setClipChildren(true);
        rows.setHitTestable(false);
        Signal<Integer> scroll = SceneScrolls.attach(rt, rows);
        rt.bind(rt.layoutDoneSignal(), epoch -> Effect.untrack(() -> {
            int max = SceneGeometry.maxScrollY(rows);
            int clamped = Math.max(0, Math.min(max, scroll.get().intValue()));
            if (clamped != scroll.get().intValue()) scroll.set(Integer.valueOf(clamped));
        }));
        ReadableSignal<List<SearchPickerData.CurrentMember>> members =
                Computed.create(() -> safeMembers(props));
        rt.forEach(rows, members, SearchPickerData.CurrentMember::memberId,
                member -> memberRow(rt, props, member.memberId(), member, memberIssues,
                        pendingDeleteMemberId, addingMember, focusIntent, variantsOpen, activeCandidate,
                        mode, selectedKeys, variantQuery, gridHighlight));
        panel.appendChild(rows);
        rt.show(panel, Computed.create(() -> Boolean.valueOf(members.get().isEmpty())),
                () -> emptyText(props.presentation().emptyCurrentMembers()));
        return panel;
    }

    /** 当前成员行：图标、主/副文本、无效/重复徽章、编辑、删除二次确认。 */
    private static SceneNode memberRow(SceneRuntime rt, Props props, long memberId,
                                       SearchPickerData.CurrentMember initialMember,
                                       ReadableSignal<MemberIssues> memberIssues,
                                       Signal<Long> pendingDeleteMemberId,
                                       Signal<Boolean> addingMember,
                                       Signal<FocusIntent> focusIntent,
                                       Signal<Boolean> variantsOpen,
                                       Signal<SearchPickerData.Candidate> activeCandidate,
                                       Signal<SearchPickerData.SelectionMode> mode,
                                       Signal<List<String>> selectedKeys,
                                       Signal<String> variantQuery,
                                       Signal<Integer> gridHighlight) {
        ReadableSignal<SearchPickerData.CurrentMember> currentMember = Computed.create(() ->
                memberById(props, memberId, initialMember));
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(MEMBER_ROW_HEIGHT);
        row.setCrossAxisAlign(CrossAxisAlign.CENTER);
        row.setGap(2);
        row.setPadding(0, SceneChromeTokens.PAD_MD, 0, SceneChromeTokens.PAD_MD);
        row.setHitTestable(false);

        SceneNode icon = new SceneNode();
        icon.setPreferredWidth(MEMBER_ICON_SIZE).setPreferredHeight(MEMBER_ICON_SIZE).setHitTestable(false);
        rt.bindComputed(() -> {
            SearchPickerData.CurrentMember member = currentMember.get();
            return member.candidate() == null ? null : props.visualAdapter().candidateImage(member.candidate());
        }, src -> {
            icon.setBackgroundColor(src == null ? PLACEHOLDER_COLOR : SceneChromeTokens.TRANSPARENT);
            icon.setImageSource(src);
        });
        row.appendChild(icon);

        SceneNode info = SceneNode.column();
        info.setFlexGrow(1);
        info.setGap(2);
        info.setHitTestable(false);
        SceneNode firstLine = SceneNode.row();
        firstLine.setHitTestable(false);
        SceneNode primary = text("");
        primary.setFlexGrow(1);
        primary.setClipChildren(true);
        rt.bindText(primary, Computed.create(() -> props.presentation().currentMemberPrimary(
                currentMember.get())));
        firstLine.appendChild(primary);

        ReadableSignal<Boolean> malformed = Computed.create(() -> Boolean.valueOf(
                currentMember.get().selection() == null));
        ReadableSignal<Boolean> duplicate = Computed.create(() -> Boolean.valueOf(
                !Boolean.TRUE.equals(malformed.get())
                        && memberIssues.get().duplicateMemberIds.contains(Long.valueOf(memberId))));
        SceneNode badge = text("");
        badge.setWidthSizing(WidthSizing.SHRINK);
        badge.setFontSize(LABEL_FONT_SIZE);
        rt.bindText(badge, Computed.create(() -> Boolean.TRUE.equals(malformed.get())
                ? props.presentation().invalidMemberBadge()
                : Boolean.TRUE.equals(duplicate.get()) ? props.presentation().duplicateMemberBadge() : ""));
        rt.bindComputed(() -> Boolean.TRUE.equals(malformed.get())
                ? SceneChromeTokens.DANGER_BG_SUBTLE : SceneChromeTokens.TRANSPARENT,
                badge::setBackgroundColor);
        rt.bindComputed(() -> Boolean.TRUE.equals(duplicate.get()) ? SceneChromeTokens.WARNING_TEXT
                : SceneChromeTokens.TEXT_PRIMARY, badge::setTextColor);
        firstLine.appendChild(badge);
        info.appendChild(firstLine);

        SceneNode secondLine = SceneNode.row();
        secondLine.setHitTestable(false);
        SceneNode secondary = text("");
        secondary.setFontSize(LABEL_FONT_SIZE);
        secondary.setTextColor(SceneChromeTokens.TEXT_SECONDARY);
        secondary.setClipChildren(true);
        rt.bindText(secondary, Computed.create(() -> props.presentation().currentMemberSecondary(
                currentMember.get())));
        secondLine.appendChild(secondary);
        info.appendChild(secondLine);
        row.appendChild(info);

        ReadableSignal<Boolean> pending = Computed.create(() -> Boolean.valueOf(
                pendingDeleteMemberId.get() != null
                        && pendingDeleteMemberId.get().longValue() == memberId));
        Runnable editAction = () -> {
            pendingDeleteMemberId.set(null);
            addingMember.set(Boolean.FALSE);
            props.onEditCurrent().accept(memberId);
            SearchPickerData.CurrentMember current = currentMember.get();
            SearchPickerData.Candidate candidate = current.candidate();
            if (candidate != null && !candidate.variants().isEmpty()) {
                SearchPickerData.Selection selection = current.selection();
                mode.set(selection == null ? SearchPickerData.SelectionMode.ALL : selection.mode());
                selectedKeys.set(selection == null ? Collections.<String>emptyList()
                        : immutableKeys(selection.variantKeys()));
                activeCandidate.set(candidate);
                variantQuery.set("");
                variantsOpen.set(Boolean.TRUE);
                focusIntent.set(FocusIntent.VARIANTS);
            } else {
                activeCandidate.set(null);
                gridHighlight.set(Integer.valueOf(-1));
                focusIntent.set(FocusIntent.GRID);
            }
        };
        SceneNode actions = SceneNode.row();
        actions.setPreferredWidth(MEMBER_ACTIONS_WIDTH);
        actions.setGap(2);
        actions.setMainAxisAlign(MainAxisAlign.END);
        actions.setHitTestable(false);
        SceneNode edit = SceneButton.create(rt, new SceneButton.Props(
                Computed.create(() -> Boolean.TRUE.equals(pending.get())
                        ? props.presentation().cancelRemove() : props.presentation().edit()),
                Signal.create(Boolean.TRUE), () -> {
                    if (Boolean.TRUE.equals(pending.get())) pendingDeleteMemberId.set(null);
                    else editAction.run();
                })).get();
        edit.setWidthSizing(WidthSizing.SHRINK);
        actions.appendChild(edit);
        SceneNode remove = SceneButton.create(rt, new SceneButton.Props(
                Computed.create(() -> Boolean.TRUE.equals(pending.get())
                        ? props.presentation().confirmRemove() : props.presentation().remove()),
                Signal.create(Boolean.TRUE), () -> {
                    if (!Boolean.TRUE.equals(pending.get())) {
                        pendingDeleteMemberId.set(Long.valueOf(memberId));
                    } else if (props.onRemoveCurrent().test(memberId)) {
                        pendingDeleteMemberId.set(null);
                    }
                })).get();
        remove.setWidthSizing(WidthSizing.SHRINK);
        actions.appendChild(remove);
        row.appendChild(actions);
        return row;
    }

    /** 变体浮层：全屏遮罩 + 居中卡片（标题/搜索/ALL-SELECTED 分段/勾选列表/取消-确认）。 */
    private static SceneNode variantPanel(SceneRuntime rt, Props props, Runnable closeRequest,
                                          Signal<SearchPickerData.Candidate> activeCandidate,
                                          Signal<SearchPickerData.SelectionMode> mode,
                                          Signal<List<String>> selectedKeys,
                                          Signal<String> variantQuery,
                                          Signal<Boolean> variantsOpen,
                                          Signal<Integer> gridHighlight,
                                          Signal<Long> pendingDeleteMemberId,
                                          Signal<Boolean> addingMember,
                                          Signal<FocusIntent> focusIntent,
                                          SceneNode[] variantFocusTarget) {
        SceneNode scrim = SceneNode.row();
        scrim.setFillParentWidth(true);
        scrim.setFillParentHeight(true);
        scrim.setBackgroundColor(OVERLAY_SCRIM);
        scrim.setMainAxisAlign(MainAxisAlign.CENTER);
        scrim.setCrossAxisAlign(CrossAxisAlign.CENTER);
        scrim.setPadding(PANEL_PADDING);

        SceneNode card = SceneNode.column();
        card.setPreferredWidth(VARIANT_CARD_WIDTH);
        card.setClipChildren(true);
        card.setBackgroundColor(SceneChromeTokens.BG_DEFAULT);
        card.setBorderWidth(1);
        card.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);
        card.setCornerRadius(SceneChromeTokens.RADIUS_LG);
        card.setPadding(SceneChromeTokens.PAD_MD);
        card.setGap(SceneChromeTokens.GAP_MD);
        scrim.appendChild(card);

        SceneNode header = SceneNode.row();
        header.setCrossAxisAlign(CrossAxisAlign.CENTER);
        header.setGap(SceneChromeTokens.GAP_SM);
        header.setHitTestable(false);
        SceneNode title = text(props.panelPresentation().variantPanelTitle());
        title.setWidthSizing(WidthSizing.SHRINK);
        header.appendChild(title);
        SceneNode candidateLabel = text("");
        candidateLabel.setFlexGrow(1);
        candidateLabel.setClipChildren(true);
        rt.bindText(candidateLabel, Computed.create(() -> {
            SearchPickerData.Candidate candidate = activeCandidate.get();
            return candidate == null ? "" : props.visualAdapter().candidateLabel(candidate);
        }));
        header.appendChild(candidateLabel);
        card.appendChild(header);

        if (props.variantSearchEnabled()) {
            SceneNode search = SceneTextInput.create(rt, SceneTextInput.Props.builder(variantQuery)
                    .enabled(props.enabled())
                    .placeholder(props.panelPresentation().variantSearchPlaceholder())
                    .onChange(variantQuery::set).build()).get();
            variantFocusTarget[0] = search;
            card.appendChild(search);
        }

        SceneNode segmented = SceneSegmented.create(rt, new SceneSegmented.Props(
                Computed.create(() -> Integer.valueOf(mode.get().ordinal())),
                Arrays.asList(props.presentation().all(), props.presentation().selected()),
                props.enabled(), index -> mode.set(SearchPickerData.SelectionMode.values()[index.intValue()]))).get();
        card.appendChild(segmented);

        SceneNode list = SceneNode.column();
        list.setScrollable(true);
        list.setClipChildren(true);
        list.setPreferredHeight(VARIANT_LIST_HEIGHT);
        list.setHitTestable(false);
        SceneScrolls.attach(rt, list);
        ReadableSignal<List<SearchPickerData.Variant>> shown = Computed.create(() -> {
            SearchPickerData.Candidate candidate = activeCandidate.get();
            return candidate == null ? Collections.<SearchPickerData.Variant>emptyList()
                    : ScenePickerPanelNav.displayVariants(candidate.variants(), selectedKeys.get(),
                            variantQuery.get(), props.presentation());
        });
        rt.forEach(list, shown, SearchPickerData.Variant::key,
                variant -> variantRow(rt, props, variant, mode, selectedKeys));
        card.appendChild(list);

        SceneNode footer = SceneNode.row();
        footer.setGap(SceneChromeTokens.GAP_MD);
        footer.setMainAxisAlign(MainAxisAlign.END);
        footer.setHitTestable(false);
        SceneNode back = SceneButton.create(rt, new SceneButton.Props(
                Signal.create(props.panelPresentation().back()), Signal.create(Boolean.TRUE),
                () -> closeVariants(variantsOpen, activeCandidate, variantQuery, focusIntent))).get();
        back.setWidthSizing(WidthSizing.SHRINK);
        footer.appendChild(back);
        SceneNode confirm = SceneButton.create(rt, new SceneButton.Props(
                Signal.create(props.presentation().confirm()),
                Computed.create(() -> Boolean.valueOf(ScenePickerPanelNav.canConfirm(
                        mode.get(), selectedKeys.get()))), () -> {
                    SearchPickerData.Candidate candidate = activeCandidate.get();
                    if (candidate == null) return;
                    SearchPickerData.Selection draft = new SearchPickerData.Selection(
                            candidate.key(), mode.get(),
                            ScenePickerPanelNav.orderedKeys(candidate.variants(), selectedKeys.get()));
                    if (props.selectionCommit().test(draft)) {
                        finishSelection(props, closeRequest, variantsOpen, activeCandidate, variantQuery,
                                gridHighlight, pendingDeleteMemberId, addingMember, focusIntent);
                    }
                })).get();
        confirm.setWidthSizing(WidthSizing.SHRINK);
        footer.appendChild(confirm);
        card.appendChild(footer);
        return scrim;
    }

    /** 变体勾选行：SELECTED 模式可勾，ALL 模式只读展示。 */
    private static SceneNode variantRow(SceneRuntime rt, Props props, SearchPickerData.Variant variant,
                                        Signal<SearchPickerData.SelectionMode> mode,
                                        Signal<List<String>> selectedKeys) {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(VARIANT_ROW_HEIGHT);
        row.setCrossAxisAlign(CrossAxisAlign.CENTER);
        row.setGap(SceneChromeTokens.GAP_MD);
        row.setPadding(SceneChromeTokens.PAD_MD);
        SceneInteractionState interaction = rt.interactionState(row);
        ReadableSignal<Boolean> checked = Computed.create(() -> Boolean.valueOf(
                selectedKeys.get().contains(variant.key())));
        ReadableSignal<Boolean> selectable = Computed.create(() -> Boolean.valueOf(
                mode.get() == SearchPickerData.SelectionMode.SELECTED));
        SceneControlChrome.bindSelectableBackground(rt, row, props.enabled(), checked, interaction);
        SceneNode icon = new SceneNode();
        icon.setPreferredWidth(VARIANT_ICON_SIZE).setPreferredHeight(VARIANT_ICON_SIZE).setHitTestable(false);
        SceneImageSource image = props.visualAdapter().variantImage(variant);
        if (image == null) icon.setBackgroundColor(PLACEHOLDER_COLOR); else icon.setImageSource(image);
        row.appendChild(icon);
        SceneNode label = text(props.visualAdapter().variantLabel(variant));
        label.setFlexGrow(1);
        label.setHitTestable(false);
        row.appendChild(label);
        SceneNode indicator = new SceneNode();
        indicator.setPreferredWidth(16).setPreferredHeight(16).setBorderWidth(1).setHitTestable(false);
        rt.bindComputed(() -> Boolean.TRUE.equals(checked.get()) ? SceneChromeTokens.TEXT_ON_ACCENT
                : PLACEHOLDER_COLOR, indicator::setBackgroundColor);
        row.appendChild(indicator);
        rt.on(row, SceneEventType.CLICK, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get()) || !Boolean.TRUE.equals(selectable.get())) return;
            selectedKeys.set(ScenePickerPanelNav.toggleVariant(selectedKeys.get(), variant.key()));
            ctx.stopPropagation();
        });
        return row;
    }

    /** 遍历当前挂载网格单元，为每个单元挂一次 hover tooltip（label + 稳定 key）。 */
    private static void attachCellTooltips(SceneRuntime rt, Props props,
                                           ReadableSignal<List<SearchPickerData.Candidate>> filtered,
                                           ReadableSignal<List<Item>> gridItems,
                                           SceneVirtualGrid.Result[] gridHolder) {
        Owner owner = Owner.current();
        if (owner == null) return;
        Set<SceneNode> attached = new HashSet<SceneNode>();
        Effect.create(() -> {
            SceneVirtualGrid.Result grid = gridHolder[0];
            if (grid == null) return;
            WindowModel model = grid.windowModel().get();
            Effect.untrack(() -> {
                SceneNode rowsContainer = grid.viewport().__getChildren().get(1);
                List<SceneNode> rowNodes = rowsContainer.__getChildren();
                List<WindowRow> windowRows = model.rows();
                for (int rowIndex = 0; rowIndex < rowNodes.size(); rowIndex++) {
                    if (rowIndex >= windowRows.size()) break;
                    List<SceneNode> cells = rowNodes.get(rowIndex).__getChildren();
                    List<Item> rowItems = windowRows.get(rowIndex).items();
                    for (int col = 0; col < cells.size() && col < rowItems.size(); col++) {
                        SceneNode cell = cells.get(col);
                        if (!attached.add(cell)) continue;
                        Item item = rowItems.get(col);
                        Owner captured = owner;
                        captured.run(() -> attachCellTooltip(rt, props, cell, item.key(),
                                filtered, gridItems));
                    }
                }
            });
        });
    }

    private static void attachCellTooltip(SceneRuntime rt, Props props, SceneNode cell, Object key,
                                          ReadableSignal<List<SearchPickerData.Candidate>> filtered,
                                          ReadableSignal<List<Item>> gridItems) {
        ReadableSignal<String> textSignal = Computed.create(() -> {
            String label = fullLabelAt(filtered.get(), key);
            String stableKey = String.valueOf(key);
            String prefix = props.panelPresentation().tooltipPrefix();
            return prefix.isEmpty() ? label + "\n" + stableKey : label + "\n" + prefix + stableKey;
        });
        SceneTooltip.attach(rt, SceneTooltip.Props.of(cell, textSignal));
    }

    /** 点击/ENTER 激活候选：无变体直达 selectionCommit，有变体开变体浮层。 */
    private static void activateCandidate(Object key, Props props, Runnable closeRequest,
                                          ReadableSignal<List<SearchPickerData.Candidate>> filtered,
                                          Signal<Boolean> variantsOpen,
                                          Signal<SearchPickerData.Candidate> activeCandidate,
                                          Signal<SearchPickerData.SelectionMode> mode,
                                          Signal<List<String>> selectedKeys,
                                          Signal<String> variantQuery,
                                          Signal<Integer> gridHighlight,
                                          Signal<Long> pendingDeleteMemberId,
                                          Signal<Boolean> addingMember,
                                          Signal<FocusIntent> focusIntent) {
        SearchPickerData.Candidate candidate = candidateByKey(filtered.get(), key);
        if (candidate == null) return;
        if (candidate.variants().isEmpty()) {
            if (props.selectionCommit().test(new SearchPickerData.Selection(candidate.key(),
                    SearchPickerData.SelectionMode.ALL, Collections.<String>emptyList()))) {
                finishSelection(props, closeRequest, variantsOpen, activeCandidate, variantQuery,
                        gridHighlight, pendingDeleteMemberId, addingMember, focusIntent);
            }
        } else {
            pendingDeleteMemberId.set(null);
            SearchPickerData.Selection current = props.currentSelection().get();
            boolean restore = current != null && candidate.key().equals(current.candidateKey());
            mode.set(restore ? current.mode() : SearchPickerData.SelectionMode.ALL);
            selectedKeys.set(restore ? immutableKeys(current.variantKeys())
                    : Collections.<String>emptyList());
            activeCandidate.set(candidate);
            variantQuery.set("");
            variantsOpen.set(Boolean.TRUE);
            focusIntent.set(FocusIntent.VARIANTS);
        }
    }

    /** 成功提交后的收尾：listMembers 新增成功留在面板重新武装，其余请求关闭。 */
    private static void finishSelection(Props props, Runnable closeRequest, Signal<Boolean> variantsOpen,
                                        Signal<SearchPickerData.Candidate> activeCandidate,
                                        Signal<String> variantQuery, Signal<Integer> gridHighlight,
                                        Signal<Long> pendingDeleteMemberId, Signal<Boolean> addingMember,
                                        Signal<FocusIntent> focusIntent) {
        if (props.listMembers() && Boolean.TRUE.equals(addingMember.get())) {
            beginAdd(props, pendingDeleteMemberId, addingMember);
            gridHighlight.set(Integer.valueOf(-1));
            variantsOpen.set(Boolean.FALSE);
            activeCandidate.set(null);
            focusIntent.set(FocusIntent.GRID);
            return;
        }
        variantsOpen.set(Boolean.FALSE);
        activeCandidate.set(null);
        variantQuery.set("");
        gridHighlight.set(Integer.valueOf(-1));
        pendingDeleteMemberId.set(null);
        addingMember.set(Boolean.FALSE);
        focusIntent.set(FocusIntent.NONE);
        closeRequest.run();
    }

    /** ESC/dismiss 取消：先 onCancel 再请求受控关闭（恒走关闭分支，不落入新增重武装）。 */
    private static void cancelPanel(Props props, Runnable closeRequest, Signal<Boolean> variantsOpen,
                                    Signal<SearchPickerData.Candidate> activeCandidate,
                                    Signal<String> variantQuery, Signal<Integer> gridHighlight,
                                    Signal<Long> pendingDeleteMemberId, Signal<Boolean> addingMember,
                                    Signal<FocusIntent> focusIntent) {
        props.onCancel().run();
        variantsOpen.set(Boolean.FALSE);
        activeCandidate.set(null);
        variantQuery.set("");
        gridHighlight.set(Integer.valueOf(-1));
        pendingDeleteMemberId.set(null);
        addingMember.set(Boolean.FALSE);
        focusIntent.set(FocusIntent.NONE);
        closeRequest.run();
    }

    private static void closeVariants(Signal<Boolean> variantsOpen,
                                      Signal<SearchPickerData.Candidate> activeCandidate,
                                      Signal<String> variantQuery, Signal<FocusIntent> focusIntent) {
        variantsOpen.set(Boolean.FALSE);
        activeCandidate.set(null);
        variantQuery.set("");
        focusIntent.set(FocusIntent.GRID);
    }

    private static void beginAdd(Props props, Signal<Long> pendingDeleteMemberId,
                                 Signal<Boolean> addingMember) {
        pendingDeleteMemberId.set(null);
        addingMember.set(Boolean.TRUE);
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

    private static String labelAt(List<CategoryRow> rows, CategoryRow initial) {
        for (CategoryRow row : rows) {
            if (row.identityKey().equals(initial.identityKey())) return row.label;
        }
        return initial.label;
    }

    private static int countAt(List<CategoryRow> rows, CategoryRow initial) {
        for (CategoryRow row : rows) {
            if (row.identityKey().equals(initial.identityKey())) return row.count;
        }
        return initial.count;
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
