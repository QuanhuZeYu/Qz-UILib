package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.config.ui.editor.CandidateSourceValueEditorProvider;
import club.heiqi.config.ui.editor.PickerCandidateSource;
import club.heiqi.config.ui.editor.PickerQuery;
import club.heiqi.config.ui.editor.PickerSourceVersion;
import club.heiqi.config.ui.editor.SearchPickerCategories;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.SearchPickerPanelPresentation;
import club.heiqi.config.ui.editor.SearchPickerPresentation;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.config.ui.field.PickerSourceGuard;
import club.heiqi.uilib.Config;
import club.heiqi.uilib.ui.diagnostic.UiPerfMarkers;
import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanelNav.CategoryRow;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanelNav.MemberIssues;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid.Item;
import club.heiqi.uilib.ui.scene.control.search.CategoryNavPane;
import club.heiqi.uilib.ui.scene.control.search.MemberGrid;
import club.heiqi.uilib.ui.scene.control.search.GridMetrics;
import club.heiqi.uilib.ui.scene.control.search.PickerDensityPreference;
import club.heiqi.uilib.ui.scene.control.search.PickerDensityTokens;
import club.heiqi.uilib.ui.scene.control.search.PickerInfoBar;
import club.heiqi.uilib.ui.scene.control.search.PickerMetrics;
import club.heiqi.uilib.ui.scene.control.search.SearchResultList;
import club.heiqi.uilib.ui.scene.control.search.VariantChooser;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.input.ClipboardBackend;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.LogicalBox;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.FontSource;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing;
import club.heiqi.uilib.ui.scene.node.TextHorizontalAlign;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.ScenePortalHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * ScenePickerPanel —— 创造物品栏式 70% 居中 picker 面板（通用、平台无关、受控）。
 *
 * <h3>定位</h3>
 * <p>以旧版内联搜索选择器为功能语义基准（SINGLE_VALUE 与 LIST_MEMBERS 两模式、
 * 可拒绝的 selectionCommit、稳定 memberId、无效/重复徽章、变体 ALL/SELECTED 语义、ESC 分层、
 * 焦点意图），重塑为居中 70% 卡片上下分区布局：顶栏（搜索 + 分类维度分段 + 结果统计）、上容器选择区
 * （左分类导航 | 中 SearchResultList 窗口化候选列表：浏览 lane 数据范围无上限、挂载量 ∝ 可视量，
 * 列数随可用宽度自适应）、下容器已选择编辑（仅 listMembers 的全宽底部横带，当前规则列表）。
 * 面板本身不持有业务状态——开合、query、结果、当前分类、当前成员全部受控。</p>
 *
 * <h3>数据面（两条路径，构建期分支、非逐帧门控）</h3>
 * <p>① <b>结果信号路径</b>（{@code Props.candidateSource} == null，T-1 回退）：{@code Props.results()}
 * 自带候选全集，面板侧按分类过滤后派生全量项。② <b>惰性候选源路径</b>（ADR §3.2）：面板在内容 Owner
 * 内自建 {@code pageProvider} 闭包，按控件产出的 {@code WindowRequest} 调
 * {@code PickerCandidateSource.page(query, offset, limit)}；总量 = 浏览 lane 的 {@code size()} /
 * 搜索 lane 的 {@code min(matchCount, searchMaxItems)}；激活经 {@code exact(key)} O(1) 定位。
 * 两条路径共用同一套节点构建（{@link Feed} 是唯一数据出口）。</p>
 *
 * <h3>ESC 分层</h3>
 * <p>主面板 portal 与变体浮层 portal 独立注册；{@code SceneInputRouter} 的 ESC 优先请求栈顶
 * overlay 关闭：变体浮层开时 ESC 只退回主面板，主面板 ESC 先走 {@code onCancel} 再请求关闭
 * （经 {@code onCloseRequest} 上抛，由外部把受控 {@code open} 置 false）。</p>
 *
 * <h3>生命周期</h3>
 * <p>必须在组件构建作用域（mount builder）内创建：全部 signal / effect / portal 归属当前 Owner；
 * 面板关闭时 portal 子树卸载，tooltip 与变体浮层一并清理；网格高亮与滚动在数据收缩时经
 * owner-scoped effect 回夹。</p>
 *
 * <p><b>关闭即停算（ADR §4.1/§4.3）</b>：候选相关派生（{@link Feed} 与其 lane 视图、分类行、
 * 成员问题、回夹、变体浮层）全部在 {@code rt.portal(open, ...)} 的<b>内容 Owner</b> 内创建，
 * 关闭即随 {@code SceneRuntime.disposeMounted()} 递归 dispose —— 停止语义由 owner 作用域与订阅边界
 * 保证，<b>不得</b>用 {@code if(!open)} 逐帧门控（本类不含任何以 open 为条件的求值分支）。</p>
 *
 * <h3>外观归属（液态玻璃迁移，G14 宿主整合，契约 §4.1「PANEL（外）/GROUP（网格）/OVERLAY（浮层）」）</h3>
 * <p><b>宿主外层卡片</b>恰装一颗 {@link SceneTheme.Role#PANEL} 配方表面（FormPageShell 已验收
 * PANEL 先例）：background/border/borderWidth/cornerRadius/surfaceElevation/backdrop 六项由
 * {@link SceneSurfaceBinder} 独占，旧 {@code SceneChromeTokens.applyPanelChrome(root, RADIUS_LG)}
 * 实色四件套写入者已删除；clip 不属绑定器六项属性，裁剪合同由宿主自持。</p>
 *
 * <p><b>表面分层由容器承担、宿主不再包第二层</b>（G14 预裁决 1/2）：中栏外壳原
 * {@code applyPanelChrome(center, RADIUS_MD)} 实底会包住 SearchResultList 自己的 GROUP 底座，
 * 构成两层玻璃语义叠加——该实底外壳的表面写入已删除，中栏只剩布局职责（clip 几何合同与
 * padding/gap 常量保留，契约 §4.2「尺寸/间距常量继续使用」），结果区表面归内容底座一颗。
 * 底部成员横带同理：原 {@code applyOuterShell} 的边框/圆角表面写入已删除，成员区表面归
 * MemberGrid 的 GROUP 底座。各 G13 配件走各自已验收配方（左导航 TOOLBAR、信息条 TOOLBAR、
 * 结果底座 GROUP、成员网格 GROUP、变体浮层 OVERLAY），宿主不复制、不覆写其内部控件样式。</p>
 *
 * <p><b>宿主文字</b>取来源主题语义前景：顶栏标题/成员区标题 {@code foreground}，结果统计、
 * 问题摘要与空态提示 {@code mutedForeground}，错误行 {@code errorText}；禁用一律
 * {@code disabledForeground}（与 CategoryNavPane 同一派生口径）。<b>物品图像不改色</b>
 * （契约 §4.1 + §7.3）：候选/成员/变体图标与占位底属渲染协议，归各配件模块的静态值，
 * 宿主不重染。整树滤镜预算 = 每颗表面各采样一次（见 G14 集成测试的整树 BACKDROP 构成表）。</p>
 */
public final class ScenePickerPanel {

    /** 面板可视阶段（观察用）。 */
    public enum State { CLOSED, MAIN, VARIANTS }

    /** portal 生命周期之间可回放的焦点意图。 */
    private enum FocusIntent { NONE, SEARCH_INPUT, GRID, VARIANTS }

    private static final int PANEL_PADDING = SceneChromeTokens.PAD_MD;
    /** 搜索输入框宽度占顶栏比例（%）：比例常量收口在 {@link PickerDensityTokens}（P5 §4.3 条 2）。 */
    private static final int SEARCH_INPUT_WIDTH_PERCENT = PickerDensityTokens.SEARCH_INPUT_WIDTH_PERCENT;
    /**
     * 无宿主逻辑盒时的面板百分比回退（P5 偏差 D-P5-3）。
     *
     * <p>生产宿主（{@code McScreenBridge} → {@code AbstractSceneHostWidget.render}）每帧发布逻辑盒，
     * 因此派生尺寸路径在生产上恒生效。只有自建 {@link SceneRuntime} 而不发布逻辑盒的装置
     * （历史集成测试直接布局一个固定画布）会走到这里 —— 那种装置里"视口"不是一个已知事实，
     * 派生链没有输入，只能沿用容器百分比合同。</p>
     *
     * <p><b>删除条件与时机</b>：当全部 UILib 测试装置改为发布逻辑盒后删除本回退与
     * {@link #viewportSizing} 分支（P6 收口时随测试装置改造一并清理）。</p>
     */
    private static final int PANEL_WIDTH_PERCENT_FALLBACK = 70;
    /** 无宿主逻辑盒时的面板高度百分比回退（同 {@link #PANEL_WIDTH_PERCENT_FALLBACK}）。 */
    private static final int PANEL_HEIGHT_PERCENT_FALLBACK = 70;
    /** 无宿主逻辑盒时的顶栏高回退（P5 前的固定值）。 */
    private static final int TOP_BAR_HEIGHT_FALLBACK = 48;
    /** 无宿主逻辑盒时的成员带高回退（P5 前的固定值）。 */
    private static final int MEMBERS_PANEL_HEIGHT_FALLBACK = 248;
    /**
     * 主面板关闭策略：ESC 请求关闭；<b>外部点击不由 policy 触发</b>（P5 A6 单一路径）。
     *
     * <p>理由（实测）：主面板的 overlay 根就是全屏 scrim，"点外部"在 Router 的判据里仍然是
     * "命中了 overlay 根"（{@code hitTest(entry.getRoot()).isEmpty() == false}），policy 根本
     * 拿不到外部点击意图 —— 外部点击的检测只能由 scrim 自己的命中回调承担。因此这里关掉
     * policy 的外部点击分支，避免「policy + scrim」两套触发并存（T5 UX-12 的双触发/漏触发）。</p>
     */
    private static final OverlayDismissPolicy MAIN_PANEL_POLICY = new OverlayDismissPolicy(true, false, false);
    /** 恒真 enabled：宿主外层卡片自身没有禁用语义（禁用反馈由内部控件各自表达），与 FormPageShell PANEL 先例同口径。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;

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
        /** 结果是否已在查询层按分类过滤（SPI 路径）：true 时面板侧不再二次过滤（ADR §1.7 D-12/T-6）。 */
        private final boolean resultsCategoryFiltered;
        /**
         * 惰性候选源（SPI 路径，ADR §3.2）：非 null 时面板<b>不持有候选全集</b>，
         * 改在内容 Owner 内自建 {@code pageProvider} 闭包按窗口切片拉取；null = 旧全量结果信号路径（T-1）。
         */
        private final PickerCandidateSource candidateSource;
        /** 搜索 lane 窗口上限（取值链 = {@code SearchPickerSpec.maxItems()}，由装配层注入）。 */
        private final int searchMaxItems;
        /** 当前查询条件信号（归一化 {@link PickerQuery}，装配层受控注入）；SPI 路径必填。 */
        private final ReadableSignal<PickerQuery> sourceQuery;
        /** 候选源版本信号（装配层的 {@code PickerRevisionBridge}）：变化即重查（可为 null = 不订阅）。 */
        private final ReadableSignal<PickerSourceVersion> sourceVersion;
        /**
         * 密度档位用户偏好信号（P5 §1.4 / Q5；可为 null = {@link PickerDensityPreference#AUTO}）。
         *
         * <p>是<b>动态注入</b>面：配置页改档位只重派生几何、不重建面板（Q5「提供手动覆盖入口」的
         * UILib 侧落点；Miner 只负责把偏好喂进这个信号）。</p>
         */
        private final ReadableSignal<PickerDensityPreference> densityPreference;
        /**
         * 撤销最近一次删除的可拒绝提交边界（P5 §5.5 E1；默认恒 false = 不支持撤销）。
         *
         * <p>入参 = 被删成员的稳定 id（陈旧撤销由宿主侧 tombstone 校验拒绝）。</p>
         */
        private final LongPredicate onRestoreCurrent;
        /** tombstone 释放回调（撤销窗口到期 / 面板关闭 / 已被新删除替换时调用）。 */
        private final Runnable onDiscardRemoved;

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
            this.resultsCategoryFiltered = false;
            this.candidateSource = null;
            this.searchMaxItems = CandidateSourceValueEditorProvider.DEFAULT_SEARCH_MAX_ITEMS;
            this.sourceQuery = null;
            this.sourceVersion = null;
            this.densityPreference = null;
            this.onRestoreCurrent = ignored -> false;
            this.onDiscardRemoved = () -> { };
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
            resultsCategoryFiltered = builder.resultsCategoryFiltered;
            candidateSource = builder.candidateSource;
            searchMaxItems = builder.searchMaxItems;
            sourceQuery = builder.sourceQuery;
            sourceVersion = builder.sourceVersion;
            densityPreference = builder.densityPreference;
            onRestoreCurrent = builder.onRestoreCurrent;
            onDiscardRemoved = builder.onDiscardRemoved;
            if (candidateSource != null && sourceQuery == null) {
                throw new IllegalArgumentException("candidateSource 非 null 时必须提供 sourceQuery（面板自建窗口切片）");
            }
            if (candidateSource != null && searchMaxItems < 1) {
                throw new IllegalArgumentException("searchMaxItems 必须为正数");
            }
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
        /** @return 结果是否已在查询层按分类过滤（true 时面板侧不做 filterByCategory，避免二次过滤） */
        public boolean resultsCategoryFiltered() { return resultsCategoryFiltered; }
        /** @return 惰性候选源；null = 旧全量结果信号路径 */
        public PickerCandidateSource candidateSource() { return candidateSource; }
        /** @return 搜索 lane 窗口上限 */
        public int searchMaxItems() { return searchMaxItems; }
        /** @return 受控查询条件信号；SPI 路径必填 */
        public ReadableSignal<PickerQuery> sourceQuery() { return sourceQuery; }
        /** @return 候选源版本信号（可为 null = 不订阅版本变化） */
        public ReadableSignal<PickerSourceVersion> sourceVersion() { return sourceVersion; }
        /** @return 密度档位用户偏好信号（可为 null = AUTO） */
        public ReadableSignal<PickerDensityPreference> densityPreference() { return densityPreference; }
        /** @return 撤销删除的可拒绝提交边界 */
        public LongPredicate onRestoreCurrent() { return onRestoreCurrent; }
        /** @return tombstone 释放回调 */
        public Runnable onDiscardRemoved() { return onDiscardRemoved; }

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
            private ReadableSignal<PickerDensityPreference> densityPreference;
            private LongPredicate onRestoreCurrent = ignored -> false;
            private Runnable onDiscardRemoved = () -> { };
            private boolean variantSearchEnabled;
            private boolean resultsCategoryFiltered;
            private PickerCandidateSource candidateSource;
            private int searchMaxItems = CandidateSourceValueEditorProvider.DEFAULT_SEARCH_MAX_ITEMS;
            private ReadableSignal<PickerQuery> sourceQuery;
            private ReadableSignal<PickerSourceVersion> sourceVersion;

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

            /** 设置中栏网格布局参数（P5 起仅作为「无派生度量通道」时的兼容缺省）。 */
            public Builder grid(GridProps value) {
                grid = Objects.requireNonNull(value, "grid"); return this;
            }

            /**
             * 设置密度档位用户偏好信号（P5 §1.4；null = AUTO）。
             *
             * <p>Q5 的「提供手动覆盖入口」在 UILib 侧的落点：偏好是<b>信号</b>而非构造期常量，
             * 改档位只重派生几何、不重建面板（也无需关闭重开）。</p>
             *
             * @param value 偏好信号（可为 null = AUTO）
             * @return this
             */
            public Builder densityPreference(ReadableSignal<PickerDensityPreference> value) {
                densityPreference = value;
                return this;
            }

            /**
             * 设置「撤销删除」的可拒绝提交边界与 tombstone 释放回调（P5 §5.5 甲形态）。
             *
             * @param onRestore 撤销提交边界（入参 = 成员 id；返回 false = 宿主拒绝，面板保持现状）
             * @param onDiscard 释放回调（窗口到期/关闭/被替换时调用，宿主据此清自己的 tombstone）
             * @return 本 builder
             */
            public Builder onRestoreCurrent(LongPredicate onRestore, Runnable onDiscard) {
                onRestoreCurrent = Objects.requireNonNull(onRestore, "onRestore");
                onDiscardRemoved = Objects.requireNonNull(onDiscard, "onDiscard");
                return this;
            }

            /** 启用变体浮层内的变体搜索输入。 */
            public Builder variantSearchEnabled(boolean value) { variantSearchEnabled = value; return this; }

            /**
             * 声明结果信号已由查询层按分类过滤（ADR §1.7 D-12 T-6）：面板侧不再二次过滤。
             *
             * <p>只作用于**结果信号路径**（未接 {@link #candidateSource}）：上层若自行按分类过滤后再喂
             * {@code Props.results()}，置 true 可避免面板二次过滤。接了候选源的 SPI 路径不使用
             * {@code Props.results()}（切片由面板 pageProvider 拉取），本标志在该路径无效；
             * 旧 provider（未实现 SPI）保持 false，面板侧 filterByCategory 照旧（T-6 保留期）。</p>
             */
            public Builder resultsCategoryFiltered(boolean value) {
                resultsCategoryFiltered = value; return this;
            }

            /**
             * 接入惰性候选源（SPI 路径，ADR §3.2）。
             *
             * <p>面板在内容 Owner 内自建 {@code pageProvider} 闭包：持有查询条件信号 + 候选源引用 +
             * 搜索窗口上限，按控件产出的 {@link SearchResultList.WindowRequest} 调
             * {@code source.page(query, offset, limit)}；总量 = 浏览 lane 的 {@code size()} 或
             * 搜索 lane 的 {@code min(matchCount, searchMaxItems)}。{@code Props.results()} 在
             * 本路径下不参与列表渲染。</p>
             *
             * @param source        惰性候选源（非 null）
             * @param searchMaxItems 搜索 lane 窗口上限（&gt;0；取值链 = SearchPickerSpec.maxItems()）
             * @param query         受控查询条件信号（非 null）
             * @param version       候选源版本信号（可为 null = 不订阅版本变化）
             * @return 本 builder
             */
            public Builder candidateSource(PickerCandidateSource source, int searchMaxItems,
                                           ReadableSignal<PickerQuery> query,
                                           ReadableSignal<PickerSourceVersion> version) {
                candidateSource = Objects.requireNonNull(source, "candidateSource");
                this.searchMaxItems = searchMaxItems;
                sourceQuery = Objects.requireNonNull(query, "sourceQuery");
                sourceVersion = version;
                return this;
            }

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
     * @param windowModel        当前结果列表窗口模型只读观察面（P4 增补：宿主/测试回读
     *                           {@code totalItems/totalRows/windowStartRow/mountedRows}；
     *                           面板关闭时为 null。不参与窗口数学，窗口数学只在列表控件内）
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
            ReadableSignal<SearchPickerData.Candidate> activeCandidate,
            Supplier<SceneGridWindow.WindowModel> windowModel) {

        /**
         * 旧 11 参形态（兼容面保留，ADR A-24/A-25「零静默破坏」）：无窗口模型观察面。
         *
         * <p>P4 增补第 12 参后，旧签名一度遗失；此处以委托形式恢复，保证外部调用方源码与
         * 二进制兼容（{@code windowModel} 观察面为 {@code null}，消费者须容忍无观察面形态）。</p>
         */
        public Result(
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
            this(root, openSignal, open, variantsOpen, firstFocusTarget, grid, currentCategoryKey,
                    gridHighlight, variantMode, variantKeys, activeCandidate, null);
        }
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
        // 宿主树锚点节点就是 Result.root（编写者 mount 后拿到的节点）：面板内建文字跟随它的字号。
        // 注意：面板内容建在 portal 树里（见下方 rt.portal），**不在本节点的子树上** ——
        // 父链继承覆盖不到，必须把「本节点的字号声明」接到 portal 内容根（见 anchorFontSizeSignal）。
        SceneNode root = new SceneNode();
        root.setHitTestable(false);
        // 面板字号声明（唯一真值）：宿主字号链上有显式声明（层 1/2/3）就用它，否则用档位基准字号
        // （宿主未声明时锚点会落到框架常量 16，那不是面板该继承的"默认"—— 面板的默认是档位基准 12）。
        // 同一个信号实例喂两个消费点：① portal 内容根的层 2 声明（= 真正渲染的字号）；
        // ② 度量派生的字号输入（= 几何算的字号）。两处同源，几何与文字不再各算一套。
        ReadableSignal<Integer> panelDeclaredFont = panelDeclaredFontSignal(rt, root);
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
        // 删除撤销闸口（P5 §5.5 E1/E3 甲形态：删除即生效 + 5s 撤销条，至多 1 条 tombstone）。
        Signal<Tombstone> tombstone = Signal.create(null);
        // 徽章 hover 原因（D5）：成员卡徽章 hover 的成员快照（null = 无徽章 hover）；随内容 Owner 释放。
        Signal<SearchPickerData.CurrentMember> hoveredMember = Signal.create(null);
        // D4（P5 §5.4 可选项）：信息条点击复制的反馈态 —— 最近复制的稳定 ID + 反馈窗口截止（帧时间纳秒）。
        // 窗口 ≤ PickerDensityTokens.INFO_COPY_WINDOW_MS，关闭/到期即释放（两信号回到空态，无跨开合残留）。
        Signal<String> copiedId = Signal.create("");
        Signal<Long> copyFeedbackUntil = Signal.create(Long.valueOf(0L));
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
        // 结果列表窗口模型只读观察面：内容构建时登记列表的 windowModel 信号，关闭时置空。
        AtomicReference<ReadableSignal<SceneGridWindow.WindowModel>> windowModelHolder =
                new AtomicReference<ReadableSignal<SceneGridWindow.WindowModel>>();

        // 候选相关内容（memberIssues/filtered/gridItems/categoryRows/clampHighlight/VariantChooser）
        // 一律在 portal 内容 Owner 内创建（见下方 rt.portal 的 builder），随 SceneRuntime.disposeMounted()
        // 一并停止重算 —— 「关闭即停算」由 owner 作用域保证，禁止 if(!open) 逐帧门控（ADR §4.1/§4.3）。

        // 开合状态机：打开时清焦点意图并引导首焦点；关闭时清理全部临时态与陈旧节点引用。
        rt.bind(open, o -> {
            if (Boolean.TRUE.equals(o)) {
                gridHighlight.set(Integer.valueOf(-1));
                focusIntent.set(FocusIntent.SEARCH_INPUT);
            } else {
                // 关闭即释放 tombone（宿主侧缓存一并交还，避免跨开合残留一个原始值）。
                if (tombstone.get() != null) {
                    tombstone.set(null);
                    props.onDiscardRemoved().run();
                }
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
                windowModelHolder.set(null);
                // D4 反馈态随关闭释放（幂等：无反馈时两次同值写入在帧末去重，零成本）。
                copiedId.set("");
                copyFeedbackUntil.set(Long.valueOf(0L));
            }
        });

        // 主面板 portal（全屏透明壳 + 居中 70% 卡片）：ESC/外部点击请求关闭（先 onCancel 再请求受控关闭）。
        // 面板内容与控件根不同树 → 用 portal 入口把「控件根声明」落到内容根（内容根持声明，
        // 顶部/中栏/成员区/空态等全部宿主文字再沿父链继承）；与 Dialog/ContextMenu 的浮层
        // 真值归位是同一机制：声明落在内容根，而不是逐节点写字号。
        ScenePortalHandle panelPortal = rt.portal(open, () -> {
            long startedAtNanos = Config.useDebug ? System.nanoTime() : 0L;
            // ==================== 内容 Owner（每次 open=true 重建，关闭即 dispose） ====================
            // 全部候选相关派生在此创建：Computed 是 effect 驱动（上游变化即重算），放在 create 期会让
            // 「面板关闭但结果信号变化」继续触发派生；移入内容 Owner 后关闭即随 disposeMounted() 停止。
            ReadableSignal<MemberIssues> memberIssues = Computed.create(() ->
                    ScenePickerPanelNav.analyzeMemberIssues(safeMembers(props)));
            // 数据面二选一（构建期分支，非逐帧门控）：
            //   旧路径（无候选源，T-1）= 结果信号自带候选全集，面板侧过滤 + 全量项派生；
            //   SPI 路径 = 面板自建 pageProvider 闭包按窗口切片拉取，面板不持有候选全集（ADR §3.2）。
            Feed feed = props.candidateSource() == null ? legacyFeed(props, categoryKey) : sourceFeed(props);

            // P5 派生度量（三分量：逻辑盒 + 字号倍率 + 密度偏好）：在内容 Owner 内创建 ⇒
            // 关闭即随 disposeMounted() 释放，不做跨开合常驻；打开时算一次、之后只在三个输入
            // 变化时重派生（无静态快照，P5 I-6）。
            ReadableSignal<PickerMetrics> metrics = createMetrics(rt, props, panelDeclaredFont);
            // 撤销条可见性投影（内容 Owner 内 ⇒ 关闭即释放；空串/无 tombstone 时零占位）。
            ReadableSignal<Boolean> undoVisible = Computed.create(() ->
                    Boolean.valueOf(tombstone.get() != null));
            // 视口尺寸事实是否可用（见 PANEL_WIDTH_PERCENT_FALLBACK 的偏差说明）。
            boolean viewportSizing = rt.logicalBox().get().isPresent();
            SceneNode content = mainPanel(rt, props, closeRequest, feed,
                    memberIssues, categoryKey, categoryWriter, gridHighlight,
                    addingMember, editingMember, focusIntent, searchFocusTarget, gridFocusTarget,
                    gridViewportHolder, windowModelHolder, variantsOpen, activeCandidate, mode,
                    selectedKeys, metrics, viewportSizing, tombstone, undoVisible, hoveredMember,
                    copiedId, copyFeedbackUntil);
            // 撤销窗口到期（≤5s）：帧时间信号驱动，O(1)；无 tombstone 时首个判断即返回。
            rt.bind(rt.__frameTimeNanos(), nanos -> Effect.untrack(() -> {
                Tombstone current = tombstone.get();
                if (current != null && nanos.longValue() >= current.deadlineNanos()) {
                    tombstone.set(null);
                    props.onDiscardRemoved().run();
                }
            }));
            recordPhase(UiPerfMarkers.PHASE_PICKER_OPEN_MAIN, startedAtNanos);

            // 变体选择浮层（模块化）：mode/selectedKeys 受控，草稿查询在模块内部。
            // 建在内容 Owner 内 ⇒ 其字号/布局订阅与分级监听随面板关闭一并停止（ADR §4.3）。
            VariantChooser.create(rt, new VariantChooser.Props(
                    variantsOpen, activeCandidate, props.enabled(),
                    props.variantSearchEnabled(),
                    props.panelPresentation().variantPanelTitle(), props.visualAdapter(),
                    mode, mode::set, selectedKeys, selectedKeys::set,
                    draft -> commitSelection(props, closeRequest, variantsOpen, activeCandidate,
                            gridHighlight, addingMember, editingMember,
                            focusIntent, () -> props.selectionCommit().test(draft)),
                    () -> closeVariants(variantsOpen, activeCandidate, focusIntent),
                    props.presentation(), props.panelPresentation(),
                    // 派生卡/行/列表尺寸只在宿主发布逻辑盒后生效（D-P5-3 同口径：
                    // 无视口事实时派生盒宽退化为 1px，会把浮层压成零尺寸）。
                    viewportSizing ? metrics : null));

            // 网格高亮回夹（数据收缩/分类切换后夹到合法范围）：关闭时高亮已由 open bind 重置为 -1，
            // 关闭态无需回夹，故随内容 Owner 建/释放（ADR §4.1）。高亮按 totalItems 夹取（O(1)，
            // 不再读全表长度；SPI 路径下窗口切片给不出全局规模）。
            rt.bindComputed(() -> Integer.valueOf(ScenePickerPanelNav.clampHighlight(
                    gridHighlight.get().intValue(), feed.totalItems().get().intValue())), clamped -> {
                if (!clamped.equals(gridHighlight.get())) gridHighlight.set(clamped);
            });
            return content;
        },
                MAIN_PANEL_POLICY,
                () -> {
                    if (Boolean.TRUE.equals(variantsOpen.get())) {
                        closeVariants(variantsOpen, activeCandidate, focusIntent);
                        return;
                    }
                    cancelPanel(props, closeRequest, variantsOpen, activeCandidate,
                            gridHighlight, addingMember, editingMember, focusIntent);
                });
        panelPortal.fontSize(panelDeclaredFont);

        // 焦点意图消费（只消费 focusIntent/focus 目标数组，O(1)，ADR §4.1 明确保留在 create）。
        bindFocusIntent(rt, focusIntent, searchFocusTarget, gridFocusTarget, variantFocusTarget);

        return new Result(root, openInternal, open, variantsOpen,
                () -> searchFocusTarget[0], () -> gridViewportHolder[0], categoryKey, gridHighlight,
                mode, selectedKeys, activeCandidate, () -> {
                    ReadableSignal<SceneGridWindow.WindowModel> signal = windowModelHolder.get();
                    return signal == null ? null : signal.get();
                });
    }

    /**
     * 信息条视图（D4）：单点派生「显示文案 + 可复制稳定 ID」。
     *
     * <p>把「文案」与「复制载荷」放进同一次派生，是为了让优先级链（徽章原因 > 悬停项 > 键盘高亮 >
     * 空闲提示）只有一份实现 —— 复制通道读的永远是当前真正显示的那条视图的载荷，
     * 不存在「显示 A、复制 B」的第二套判定。</p>
     *
     * @param text   信息条显示文案
     * @param copyId 可复制稳定 ID；空串 = 当前视图无可复制项（点击零操作）
     */
    @Desugar
    private record InfoBarView(String text, String copyId) { }

    /** 构建主面板内容：全屏透明命中穿透壳 + 居中 70% 卡片。 */
    private static SceneNode mainPanel(SceneRuntime rt, Props props, Runnable closeRequest, Feed feed,
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
                                       AtomicReference<ReadableSignal<SceneGridWindow.WindowModel>> windowModelHolder,
                                       Signal<Boolean> variantsOpen,
                                       Signal<SearchPickerData.Candidate> activeCandidate,
                                       Signal<SearchPickerData.SelectionMode> mode,
                                       Signal<List<String>> selectedKeys,
                                       ReadableSignal<PickerMetrics> metrics,
                                       boolean viewportSizing,
                                       Signal<Tombstone> tombstone,
                                       ReadableSignal<Boolean> undoVisible,
                                       Signal<SearchPickerData.CurrentMember> hoveredMember,
                                       Signal<String> copiedId,
                                       Signal<Long> copyFeedbackUntil) {
        SceneNode scrim = SceneNode.column();
        scrim.setFillParentWidth(true);
        scrim.setFillParentHeight(true);
        scrim.setMainAxisAlign(MainAxisAlign.CENTER);
        scrim.setCrossAxisAlign(CrossAxisAlign.CENTER);
        // 透明壳是外部点击的<b>唯一</b>检测点（卡片外按下：关闭 + 吞掉事件不透传到下方配置页）。
        // 幂等：同一次挂载内只请求一次关闭（快速连点 5 次也只有 1 次 onCancel/closeRequest）；
        // 标志随内容 Owner 建/释放，关闭即回收，无跨开合残留。
        final boolean[] outsideDismissed = { false };
        rt.on(scrim, SceneEventType.POINTER_DOWN, (ev, ctx) -> {
            if (ev.getTarget() != scrim) return;
            ctx.stopPropagation();
            if (outsideDismissed[0]) return;
            outsideDismissed[0] = true;
            cancelPanel(props, closeRequest, variantsOpen, activeCandidate,
                    gridHighlight, addingMember, editingMember, focusIntent);
        });

        SceneNode root = SceneNode.column();
        // P5 §1.2：面板盒不再是裸 70% 百分比，而是「逻辑盒 + 字号 + 密度」派生出来的确定尺寸
        // （比例取自 70/78/84/92 阶梯，小盒满屏；由 PickerMetrics 在支配约束下求解）。
        // 用逻辑 px 而非百分比，是因为列数预算必须在挂载前算得出来（ADR §3.4 判据① 无收敛帧）。
        if (viewportSizing) {
            applyPanelBox(rt, root, metrics);
        } else {
            root.setPercentWidth(PANEL_WIDTH_PERCENT_FALLBACK);
            root.setPercentHeight(PANEL_HEIGHT_PERCENT_FALLBACK);
        }
        // 宿主外层恰装一颗 PANEL 配方表面（契约 §4.1「PANEL（外）」，G14 预裁决 2）：
        // background/border/borderWidth/cornerRadius/surfaceElevation/backdrop 六项归
        // SceneSurfaceBinder 独占；旧 applyPanelChrome(root, RADIUS_LG) 实色四件套写入者已删除。
        // 时序契约（FormPageShell PANEL 先例）：构建期声明关心状态，Router 后续写入才会落到
        // 已创建的 signal。clip 不属绑定器六项属性：保持原外壳裁剪语义，由宿主自持。
        ReadableSignal<SceneSurfaceStyle> panelSurface =
                SceneThemes.surface(rt, SceneTheme.Role.PANEL);
        SceneInteractionState panelInteraction = rt.interactionState(root);
        panelInteraction.hovered();
        panelInteraction.pressed();
        panelInteraction.focused();
        SceneSurfaceBinder.bind(rt, root, panelSurface, ALWAYS_ENABLED, panelInteraction);
        root.setClipChildren(true);
        root.setPadding(PANEL_PADDING);
        root.setGap(PANEL_PADDING);

        root.appendChild(topBar(rt, props, feed, gridHighlight, searchFocusTarget, metrics,
                viewportSizing, closeRequest, variantsOpen, activeCandidate,
                addingMember, editingMember, focusIntent));

        // 上容器：选择功能（左分类导航 | 中候选列表 + 信息条），flexGrow 占满剩余高度。
        SceneNode selectionArea = SceneNode.row();
        selectionArea.setFlexGrow(1);
        selectionArea.setGap(PANEL_PADDING);
        // 左栏文案（U-P5-1）：浏览分类标题 + 密度状态（生效档位，随派生即时更新）——
        // 由导航配件自身承载（它是行内的单一节点，宿主不再包一层组合列：那会破坏
        // fillParentHeight 的高度契约，实测导致结果区命中失效）。
        ReadableSignal<String> densityStatus = Computed.create(
                () -> props.panelPresentation().densityLabel()
                        + " " + metrics.get().density().name());
        selectionArea.appendChild(CategoryNavPane.create(rt, new CategoryNavPane.Props(
                feed.categoryRows(), categoryKey, props.enabled(), categoryWriter,
                props.panelPresentation().emptyCategory(),
                viewportSizing
                        ? Computed.create(() -> Integer.valueOf(metrics.get().panel().navWidthPx()))
                        : null,
                props.panelPresentation().categoryDimensionTitle(), densityStatus,
                viewportSizing ? metrics : null)));
        // 悬停项：驱动信息条文本（悬浮 tooltip 已被固定信息条取代）。
        Signal<SceneVirtualGrid.Item> hoveredItem = Signal.create(null);
        selectionArea.appendChild(centerColumn(rt, props, closeRequest, feed,
                categoryKey, gridHighlight, gridFocusTarget, gridViewportHolder, windowModelHolder,
                hoveredItem, variantsOpen, activeCandidate, mode, selectedKeys,
                addingMember, editingMember, focusIntent, metrics, viewportSizing,
                memberIssues, hoveredMember, copiedId, copyFeedbackUntil));
        root.appendChild(selectionArea);

        // 下容器：已选择编辑（仅 listMembers 挂全宽底部横带）。
        if (props.listMembers()) {
            root.appendChild(membersPanel(rt, props, memberIssues, gridHighlight,
                    addingMember, editingMember, focusIntent, variantsOpen,
                    activeCandidate, mode, selectedKeys, metrics, viewportSizing,
                    tombstone, undoVisible, hoveredMember));
        }
        scrim.appendChild(root);
        return scrim;
    }

    /** 顶栏：标题 + 搜索输入 + 分类维度分段 + 结果统计。 */
    private static SceneNode topBar(SceneRuntime rt, Props props, Feed feed,
                                    Signal<Integer> gridHighlight,
                                    SceneNode[] searchFocusTarget,
                                    ReadableSignal<PickerMetrics> metrics,
                                    boolean viewportSizing,
                                    Runnable closeRequest,
                                    Signal<Boolean> variantsOpen,
                                    Signal<SearchPickerData.Candidate> activeCandidate,
                                    Signal<Boolean> addingMember,
                                    Signal<Boolean> editingMember,
                                    Signal<FocusIntent> focusIntent) {
        SceneNode bar = SceneNode.row();
        if (viewportSizing) {
            bar.setPreferredHeight(metrics.get().panel().headerHeightPx());
            rt.bind(metrics, m -> Effect.untrack(
                    () -> bar.setPreferredHeight(m.panel().headerHeightPx())));
        } else {
            bar.setPreferredHeight(TOP_BAR_HEIGHT_FALLBACK);
        }
        bar.setCrossAxisAlign(CrossAxisAlign.CENTER);
        bar.setGap(SceneChromeTokens.GAP_MD);
        bar.setHitTestable(false);

        // 顶栏文字取来源主题语义前景（禁用档与 CategoryNavPane 同一派生口径）；
        // 顶栏自身不装表面——表面分层由容器承担，卡片 PANEL 一颗在外层根（G14 预裁决 2）。
        ReadableSignal<Integer> labelForeground = themedForeground(rt, props, false);
        ReadableSignal<Integer> secondaryForeground = themedForeground(rt, props, true);

        SceneNode title = text(rt, props.panelPresentation().panelTitle());
        title.setWidthSizing(WidthSizing.SHRINK);
        rt.bind(labelForeground, title::setTextColor);
        bar.appendChild(title);

        SceneNode input = SceneTextInput.create(rt, SceneTextInput.Props.builder(props.query())
                .enabled(props.enabled()).placeholder(props.presentation().placeholder())
                .onChange(value -> {
                    props.onQuery().accept(value);
                    gridHighlight.set(Integer.valueOf(-1));
                }).build()).get();
        input.setPercentWidth(SEARCH_INPUT_WIDTH_PERCENT);
        // C3（P5 §5.3）：搜索框 ↓ 进入结果网格并高亮首项（焦点意图单点消费 + 高亮回写）。
        rt.on(input, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())
                    || ev.getKeyAction() != SceneKeyAction.PRESSED
                    || ev.getKey() != SceneKey.ARROW_DOWN) {
                return;
            }
            ctx.stopPropagation();
            gridHighlight.set(Integer.valueOf(0));
            focusIntent.set(FocusIntent.GRID);
        });
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

        SceneNode summary = text(rt, "");
        rt.bind(secondaryForeground, summary::setTextColor);
        // 结果统计 = 当前查询总量（SPI 路径 = source.size()/min(matchCount,maxItems)；旧路径 = 过滤后候选数），
        // 不再读"全表长度"——切片路径下全表根本不存在（ADR §3.5 高亮回夹同口径）。
        // 统计行 = 「N 个结果」+ 同一行右侧的截断提示（P5 §3.3「与统计同行」）。
        // 截断真值来自数据面（SPI 路径 = matchCount > searchMaxItems 的本地判定；旧路径 =
        // results.truncated()），恒 false 时不追加任何字符（不显示空段）。
        rt.bindText(summary, Computed.create(() -> {
            String base = props.presentation().resultSummary(
                    feed.totalItems().get().intValue());
            // 截断省略落顶栏统计行右侧（P5 §3.3），文案走注入键 truncated（U-P5-1）。
            return feed.truncated().get().booleanValue()
                    ? base + "  ·  " + props.presentation().truncated() : base;
        }));
        summary.setWidthSizing(WidthSizing.SHRINK);
        bar.appendChild(summary);

        // 关闭按钮（U-P5-1：presentation.close 的真实显示位）——与 ESC 同一路径（cancelPanel:
        // 先 onCancel 再请求受控关闭），不新增第二套关闭语义。
        SceneNode close = SceneButton.create(rt, new SceneButton.Props(
                Signal.create(props.panelPresentation().close()), props.enabled(),
                () -> cancelPanel(props, closeRequest, variantsOpen, activeCandidate,
                        gridHighlight, addingMember, editingMember, focusIntent))).get();
        close.setWidthSizing(WidthSizing.SHRINK);
        bar.appendChild(close);
        return bar;
    }

    /**
     * 宿主文字的派生前景信号（构造期在来源作用域内解析一次，主题切换只重算、不重建节点）：
     * 启用取主题 {@code foreground}/次要取 {@code mutedForeground}，禁用一律
     * {@code disabledForeground}（与 CategoryNavPane/PickerInfoBar 同口径）。
     *
     * @param rt     场景运行时
     * @param props  面板属性（读启用信号）
     * @param muted  true = 次要信息档（mutedForeground），false = 正文档（foreground）
     * @return 前景色只读信号
     */
    private static ReadableSignal<Integer> themedForeground(SceneRuntime rt, Props props,
                                                            final boolean muted) {
        final ReadableSignal<Integer> normal = muted
                ? SceneThemes.mutedForeground(rt) : SceneThemes.foreground(rt);
        ReadableSignal<Integer> disabled = SceneThemes.disabledForeground(rt);
        return () -> Boolean.TRUE.equals(props.enabled().get())
                ? normal.get() : disabled.get();
    }



    /**
     * 中栏：布局壳（不装表面）包候选列表（SearchResultList）+ 信息条（PickerInfoBar）+ 错误行。
     *
     * <p>G14 预裁决 1：原 {@code applyPanelChrome(center, RADIUS_MD)} 实底包住 SearchResultList
     * 自己的 GROUP 底座，构成两层玻璃语义叠加；该表面写入已删除（背景/边框/圆角归零，表面归
     * 内容底座一颗）。clip 与 padding 属布局合同保留（契约 §4.2「尺寸/间距常量继续使用」）。</p>
     */
    private static SceneNode centerColumn(SceneRuntime rt, Props props, Runnable closeRequest,
                                          Feed feed,
                                          ReadableSignal<String> categoryKey,
                                          Signal<Integer> gridHighlight,
                                          SceneNode[] gridFocusTarget,
                                          SceneNode[] gridViewportHolder,
                                          AtomicReference<ReadableSignal<SceneGridWindow.WindowModel>> windowModelHolder,
                                          Signal<SceneVirtualGrid.Item> hoveredItem,
                                          Signal<Boolean> variantsOpen,
                                          Signal<SearchPickerData.Candidate> activeCandidate,
                                          Signal<SearchPickerData.SelectionMode> mode,
                                          Signal<List<String>> selectedKeys,
                                          Signal<Boolean> addingMember,
                                          Signal<Boolean> editingMember,
                                          Signal<FocusIntent> focusIntent,
                                          ReadableSignal<PickerMetrics> metrics,
                                          boolean viewportSizing,
                                          ReadableSignal<MemberIssues> memberIssues,
                                          Signal<SearchPickerData.CurrentMember> hoveredMember,
                                          Signal<String> copiedId,
                                          Signal<Long> copyFeedbackUntil) {
        SceneNode center = SceneNode.column();
        center.setFlexGrow(1);
        center.setGap(SceneChromeTokens.GAP_SM);
        // G14 预裁决 1：中栏实底外壳表面写入已删除（两层玻璃叠加）。外壳不再自绘底色/边框/
        // 圆角——背景保持默认全透明、不装滤镜，结果区表面归 SearchResultList 的 GROUP 底座一颗；
        // clip 与 padding 属布局合同保留（原 applyPanelChrome 的裁剪语义与 PAD_SM 内边距不变）。
        center.setClipChildren(true);
        center.setPadding(SceneChromeTokens.PAD_SM);

        // 错误行：<b>仅非空时挂载</b>（P5 §3.3）。现状空串也占一行高 ⇒ 结果区常态少一行；
        // 改为条件挂载后，出现错误时结果区自动让位（rt.show 的挂载/卸载即高度让位），
        // 消失即回收节点。错误行取主题 errorText 语义前景（G19/P-02 收编：经
        // SceneThemes.errorText 公共入口，主题切换自动重派生，不重建节点）。
        // 错误行：<b>非空才占位</b>（P5 §3.3）。现状空串也占一行高 ⇒ 结果区常态少一行。
        // 偏离说明（D-P5-4）：规格写「仅非空时挂载」，本实现取「条件占位」—— 节点常驻、
        // 空串时 preferredHeight = 0（不占一行、空文本也不产生命令），非空时取一行行高。
        // 不卸载节点的理由是结构性的：中心列的子下标是既有宿主与集成测试定位结果区的契约
        // （rt.show 会把内容插在 anchor 之后，卸载/挂载会整体移动兄弟下标 —— 实测会让
        // 「宿主变矮后列表收缩」与按子下标取网格的两类用例错位）。可见效果与规格一致：
        // 空错误态不消耗任何垂直空间。
        SceneNode error = text(rt, "");
        error.setHitTestable(false);
        // 错误行取主题 errorText 语义前景（G19/P-02 收编：经 SceneThemes.errorText 公共入口，
        // 主题切换自动重派生，不重建节点）。
        rt.bind(SceneThemes.errorText(rt), error::setTextColor);
        rt.bindText(error, props.error());
        rt.bind(props.error(), value -> Effect.untrack(() -> error.setPreferredHeight(
                safeString(value).isEmpty() ? 0 : metrics.get().grid().lineHeightPx())));
        rt.bind(metrics, m -> Effect.untrack(() -> error.setPreferredHeight(
                safeString(props.error().get()).isEmpty() ? 0 : m.grid().lineHeightPx())));
        center.appendChild(error);

        // 宿主文字前景（空态/横幅与顶栏、成员区同口径：禁用档统一走 disabledForeground）。
        ReadableSignal<Integer> secondaryForeground = themedForeground(rt, props, true);
        // 结果区空态（P5 §3.3 / ADR §1.5 三态严格区分，禁止用「列表长度为 0」代替）：
        //   搜索 lane 无命中 -> emptySearchResults；浏览 lane + 分类过滤为 0 -> emptyCategoryResults；
        //   其余（空查询且无任何候选）-> empty。文案为空即零高（不占垂直空间），列表结构不动。
        ReadableSignal<String> emptyState = Computed.create(() -> {
            if (feed.totalItems().get().intValue() > 0) {
                return "";
            }
            if (!safeString(props.query().get()).isEmpty()) {
                return props.presentation().emptySearchResults();
            }
            if (!safeString(categoryKey.get()).isEmpty()) {
                return props.presentation().emptyCategoryResults();
            }
            return props.presentation().empty();
        });

        // 结构用「节点常驻 + 空串零高」而非 rt.show 挂载：中心列的子下标是既有宿主与集成测试
        // 定位结果区/信息条的契约（D-P5-4 同一理由），条件挂载会随状态改变兄弟下标。
        // 位置在列表之上（结果区状态行），信息条仍恒为第 3 子（下标稳定）。
        SceneNode emptyHint = text(rt, "");
        emptyHint.setTextHorizontalAlign(TextHorizontalAlign.CENTER);
        rt.bind(secondaryForeground, emptyHint::setTextColor);
        rt.bindText(emptyHint, emptyState);
        rt.bindComputed(() -> Integer.valueOf(safeString(emptyState.get()).isEmpty()
                        ? 0 : rt.lineHeight(emptyHint.effectiveFontSize())),
                emptyHint::setPreferredHeight);
        center.appendChild(emptyHint);

        // 挂载前预算宽（ADR §3.4 判据①）：面板盒几何链已在挂载前算得结果区内宽，
        // 网格因此首帧即正确列数，不存在「1 列挂载 N 行 → 收敛重建」的收敛帧（ST-01）。
        // 两个度量投影<b>必须注入同步初值</b>（{@link Computed#create(Object, java.util.function.Supplier)}）：
        // 面板内容是在 portal 打开的那一次 flush 内构建的，而下游 {@code SearchResultList.create}
        // 紧接着<b>同步</b>读一次投影 —— 若初值为 null（{@code Computed.create(Supplier)} 的默认），
        // 它会永久落到回退分支：列数按 {@code GridProps.cellWidth} 推算、图位边长 0，且此后不再订阅
        // 度量通道（P5 §1.2「单一 GridMetrics 快照」在结果网格上静默失效；面板盒/顶栏/信息条/行预算
        // 仍派生正确，故现象隐蔽）。注入同步初值即框架为「下游 applier 首帧前不得收到 null」给出的标准解法。
        ReadableSignal<Integer> widthBudget = viewportSizing
                ? Computed.create(Integer.valueOf(metrics.get().panel().listWidthPx()),
                        () -> Integer.valueOf(metrics.get().panel().listWidthPx())) : null;
        ReadableSignal<GridMetrics> gridMetrics = viewportSizing
                ? Computed.create(metrics.get().grid(), () -> metrics.get().grid()) : null;
        // 已配置候选键集合（T5 UX-18 / P4 偏差 D-P4-3）：SPI 路径不再排除「已在当前规则中」的候选，
        // 因此该状态必须在结果单元（圆点标记）与信息条（标记文案）上可区分。键口径 = 成员 selection 的
        // candidateKey —— 与结果单元 key（= 候选 key）同域，不做任何拆键/拼键。
        ReadableSignal<Set<String>> configuredKeys = Computed.create(() -> {
            List<SearchPickerData.CurrentMember> members = safeMembers(props);
            if (members.isEmpty()) {
                return Collections.<String>emptySet();
            }
            Set<String> keys = new HashSet<String>(members.size() * 2);
            for (SearchPickerData.CurrentMember member : members) {
                SearchPickerData.Selection selection = member.selection();
                if (selection != null) {
                    keys.add(selection.candidateKey());
                }
            }
            return Collections.unmodifiableSet(keys);
        });
        SearchResultList.Result list = SearchResultList.create(rt, new SearchResultList.Props(
                feed.listItems(), props.grid().columns(), props.grid().cellWidth(), props.grid().cellHeight(),
                props.grid().gapX(), props.grid().gapY(),
                props.enabled(),
                item -> activateCandidate(item.key(), props, closeRequest, feed, variantsOpen,
                        activeCandidate, mode, selectedKeys, gridHighlight,
                        addingMember, editingMember, focusIntent),
                gridHighlight, gridHighlight::set,
                hoveredItem::set,
                // 窗口切片生产者（ADR §3.2）：SPI 路径 = 面板自建闭包按控件产出的 WindowRequest 拉片，
                // 旧路径 = null（控件对全量 items 自切片）。offset/limit 由控件产出，宿主/面板都不自算窗口。
                feed.pageProvider(),
                // 旧路径传 -1 = 取 items.size()；SPI 路径经动态总量信号给（见 totalItemsSignal）。
                SearchResultList.Props.UNSPECIFIED_TOTAL_ITEMS, 0,
                // 可视行数预算：优先用 P5 派生的可视行数（与结果区高/stride 同源），
                // 布局后仍以实际视口高度为权威。
                viewportSizing ? Math.max(1, metrics.get().visibleRows())
                        : props.grid().visibleRows(),
                widthBudget, feed.totalItems(), gridMetrics, configuredKeys,
                // C3：网格首行 ↑ 回搜索框（焦点意图单点消费，不直接写焦点）
                () -> focusIntent.set(FocusIntent.SEARCH_INPUT)));
        // root = stackHost（viewport + 右侧滚动条），fillParentHeight 占满中栏剩余高度
        //（scrollable 子节点不能走 flexGrow 分配，模块内已对 root 设置）。
        gridViewportHolder[0] = list.viewport();
        gridFocusTarget[0] = list.viewport();
        rt.focusable(list.viewport(), props.enabled());
        center.appendChild(list.root());
        windowModelHolder.set(list.windowModel());

        // 网格焦点投影（信息条键盘态提示的失效通道）：焦点是 Router 权威状态的按需投影，
        // 必须在任何 requestFocus 写入前声明关心（与 SceneContextMenu 同一时序契约）。
        ReadableSignal<Boolean> gridFocused = rt.interactionState(list.viewport()).focused();

        // 信息条（常驻、内容永不空 —— 偏差 D-P5-2 与 P5 §3.3「不允许空条」）：
        //   悬停 -> 单行「label · ID: key」（稳定 ID 必须可见，修 T5 UX-11 的两行被裁）；
        //   空闲 -> 「搜索结果 (N)」+ 状态提示（截断 > 键盘 > 滚动 > 悬停操作提示）。
        // 单行形态同时是 Q2 的取法：竖向只占 round(fs*2)，不会为第二行再抬高度。
        ReadableSignal<InfoBarView> baseView = Computed.create(() -> {
            // D5（P5 §5.4）：成员徽章 hover 的原因解释优先级最高（标题栏之外唯一语义出口），
            // 原因文案（严重级/问题/稳定 ID/原始 raw）全部经 Presentation 注入，不在面板内拼字面量。
            SearchPickerData.CurrentMember hovered = hoveredMember.get();
            if (hovered != null) {
                String reason = memberIssueReason(props, memberIssues, hovered);
                if (!reason.isEmpty()) {
                    // 原因态展示的是成员域 ID（成员 id / 候选 key），与结果候选域不是同一可复制项：
                    // 该态下复制通道无载荷（点击零操作，不产生假反馈）。
                    return new InfoBarView(reason, "");
                }
            }
            // D3（P5 §5.4）：键盘高亮与指针悬停同权 —— 悬停优先，空闲时回落到当前高亮项。
            SceneVirtualGrid.Item item = hoveredItem.get();
            if (item == null) {
                item = list.highlightedItem() == null ? null : list.highlightedItem().get();
            }
            String prefix = props.panelPresentation().tooltipPrefix();
            if (item == null) {
                // 空闲态 = 「搜索结果 (N)」+ 单一状态提示，提示按优先级取一条：
                //   截断（被搜索上限裁剪是最需要知道的事实）> 键盘（网格持有焦点）> 滚动余量 > 悬停操作提示。
                String hint;
                if (feed.truncated().get().booleanValue()) {
                    hint = props.panelPresentation().truncatedResults();
                } else if (Boolean.TRUE.equals(gridFocused.get())) {
                    hint = props.panelPresentation().keyboardHint();
                } else if (scrollable(windowModelHolder)) {
                    hint = props.panelPresentation().scrollHint();
                } else {
                    hint = props.panelPresentation().hoverHint();
                }
                return new InfoBarView(props.presentation().searchResultsTitle(
                        feed.totalItems().get().intValue()) + "  ·  " + hint, "");
            }
            // O(1)：标签随 Item 携带（渲染层负责省略号），不再对 filtered 全表反查。
            String label = item.label() == null ? String.valueOf(item.key()) : item.label();
            String stableKey = String.valueOf(item.key());
            String text = props.panelPresentation().infoBarIdLabel(label,
                    prefix.isEmpty() ? stableKey : prefix + stableKey);
            // 已配置标记（T5 UX-18）：单元侧是圆点（形态），信息条侧是文案（语义）；
            // 点击行为保持既有激活语义（不做静默丢弃，见 activateCandidate 的契约说明）。
            String shown = configuredKeys.get().contains(stableKey)
                    ? text + "  ·  " + props.panelPresentation().alreadyConfiguredBadge()
                    : text;
            // D4（P5 §5.4 可选项）：可复制载荷 = 该候选的稳定 ID（信息条上唯一「读得到但敲不出」的值）。
            return new InfoBarView(shown, stableKey);
        });
        // D4 反馈窗口：只有「窗口内且有已复制 ID」这一帧分支读帧时间 ⇒ 常规态整链不随帧重算；
        // 窗口外直接返回 baseView 的同值实例（Computed 记忆化按 equals 判定 ⇒ 下游零通知）。
        ReadableSignal<InfoBarView> infoView = Computed.create(() -> {
            long now = rt.__frameTimeNanos().get().longValue();
            String copied = copiedId.get();
            if (!copied.isEmpty() && now < copyFeedbackUntil.get().longValue()) {
                return new InfoBarView(props.panelPresentation().infoBarCopied(copied), copied);
            }
            return baseView.get();
        });
        ReadableSignal<String> infoText = Computed.create(() -> infoView.get().text());
        // 点击复制（D4）：载荷为空 = 零操作；无剪贴板端口 = 静默降级（不崩、不假报成功）；
        // 有载荷才写剪贴板并开反馈窗口（重复点击刷新同一窗口 ⇒ 幂等，不累积第二条反馈）。
        Runnable onCopy = () -> {
            String payload = infoView.get().copyId();
            if (payload.isEmpty() || !Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            ClipboardBackend clipboard = rt.getClipboardBackend();
            if (clipboard == null) {
                return;
            }
            clipboard.setClipboardText(payload);
            copiedId.set(payload);
            copyFeedbackUntil.set(Long.valueOf(rt.__frameTimeNanos().get().longValue()
                    + PickerDensityTokens.INFO_COPY_WINDOW_MS * 1_000_000L));
        };
        SceneNode infoBar = PickerInfoBar.create(rt, new PickerInfoBar.Props(infoText, props.enabled()),
                onCopy);
        if (viewportSizing) {
            applyInfoBarHeight(infoBar, metrics.get().panel().infoBarHeightPx());
            rt.bind(metrics, m -> Effect.untrack(
                    () -> applyInfoBarHeight(infoBar, m.panel().infoBarHeightPx())));
        }
        center.appendChild(infoBar);
        return center;
    }

    /**
     * 信息条高度 / 内容折叠声明（同一入口，避免高度与折叠成为两个真值）。
     *
     * <p>小盒降级下 P5 §1.5.3 要求「信息条不占位」，派生高为 {@code 0}：此时必须同时用
     * {@link SceneNode#setCollapsed(boolean)} 声明内容退出布局域，而不是只把 preferredHeight 写成
     * 0 —— 信息条是「<b>有子容器</b>」，preferredHeight==0 时 {@code ConstraintResolver
     * .priorKnownChildHeight} 返回 UNCONSTRAINED，中栏整条 COLUMN grow 分配被放弃
     * （U-P5-17「grow 先验闸门」复发）⇒ 结果区 viewport 回退 shrink-to-fit：被内容撑大、
     * {@code SceneGeometry.maxScrollY == 0}，末排既滚不到也看不见。声明折叠后先验高与实际高
     * 都是「零内容叶」口径，由构造一致，闸门不再触发（与撤销条 setCollapsed 同一根除方式）。</p>
     *
     * @param infoBar  信息条节点
     * @param heightPx 派生高度（{@code <=0} = 小盒降级不占位）
     */
    private static void applyInfoBarHeight(SceneNode infoBar, int heightPx) {
        infoBar.setPreferredHeight(heightPx);
        infoBar.setCollapsed(heightPx <= 0);
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
                                          Signal<List<String>> selectedKeys,
                                          ReadableSignal<PickerMetrics> metrics,
                                          boolean viewportSizing,
                                          Signal<Tombstone> tombstone,
                                          ReadableSignal<Boolean> undoVisible,
                                          Signal<SearchPickerData.CurrentMember> hoveredMember) {
        SceneNode panel = SceneNode.column();
        // 成员带高度由 PickerMetrics 派生（空态折叠为一行提示、有成员时最多 2 行 + header），
        // 不再是固定 248：720p 下现状 248 占面板 49% 会把结果区压到 1 行（T5 UX-01）。
        if (viewportSizing) {
            panel.setPreferredHeight(metrics.get().panel().membersHeightPx());
            rt.bind(metrics, m -> Effect.untrack(
                    () -> panel.setPreferredHeight(m.panel().membersHeightPx())));
        } else {
            panel.setPreferredHeight(MEMBERS_PANEL_HEIGHT_FALLBACK);
        }
        // G14 预裁决 1 同口径：底部横带原 applyOuterShell 的表面写入（边框/圆角/裁剪底语义）
        // 已删除，成员区表面归 MemberGrid 的 GROUP 底座一颗；clip 属布局合同保留。
        panel.setClipChildren(true);

        ReadableSignal<Integer> labelForeground = themedForeground(rt, props, false);
        ReadableSignal<Integer> secondaryForeground = themedForeground(rt, props, true);

        SceneNode header = SceneNode.row();
        header.setPreferredHeight(viewportSizing
                ? metrics.get().panel().headerHeightPx() : TOP_BAR_HEIGHT_FALLBACK);
        header.setPadding(SceneChromeTokens.PAD_MD);
        header.setCrossAxisAlign(CrossAxisAlign.CENTER);
        header.setGap(SceneChromeTokens.GAP_MD);
        header.setHitTestable(false);
        SceneNode title = text(rt, "");
        title.setFlexGrow(1);
        rt.bind(labelForeground, title::setTextColor);
        rt.bindText(title, Computed.create(() -> props.presentation().currentMembersTitle(
                safeMembers(props).size())));
        header.appendChild(title);
        SceneNode issues = text(rt, "");
        issues.setWidthSizing(WidthSizing.SHRINK);
        rt.bind(secondaryForeground, issues::setTextColor);
        rt.bindText(issues, Computed.create(() -> props.presentation().memberIssueSummary(
                memberIssues.get().invalidCount(), memberIssues.get().duplicateMemberIds().size())));
        header.appendChild(issues);
        // 显式「添加」入口（U-P5-1：presentation.addMember 的真实显示位）：此前只有「点击上方候选
        // 即隐式新增」的隐式语义，用户看不到如何进入新增模式；显式按钮与隐式路径共用同一 arm 逻辑
        // （beginAdd 是单点），不引入第二套新增语义。
        SceneNode addButton = SceneButton.create(rt, new SceneButton.Props(
                Signal.create(props.panelPresentation().addMember()), props.enabled(),
                () -> {
                    beginAdd(props, addingMember, editingMember);
                    focusIntent.set(FocusIntent.GRID);
                })).get();
        addButton.setWidthSizing(WidthSizing.SHRINK);
        header.appendChild(addButton);
        panel.appendChild(header);

        // 模式横幅（U-P5-1：memberAddingBanner / memberEditingBanner 的真实显示位）：
        // 新增/编辑模式各有可见说明（此前只是内部布尔态）；非模式态不挂载 ⇒ 零占位、不挤结果区。
        ReadableSignal<String> bannerText = Computed.create(() -> {
            if (Boolean.TRUE.equals(editingMember.get())) {
                return props.panelPresentation().memberEditingBanner(editingMemberName(props));
            }
            if (Boolean.TRUE.equals(addingMember.get())) {
                return props.panelPresentation().memberAddingBanner();
            }
            return "";
        });
        // 与结果区空态同口径：节点常驻 + 空串零高（避免条件挂载移动兄弟下标）。
        SceneNode banner = text(rt, "");
        rt.bind(secondaryForeground, banner::setTextColor);
        rt.bindText(banner, bannerText);
        rt.bindComputed(() -> Integer.valueOf(safeString(bannerText.get()).isEmpty()
                        ? 0 : rt.lineHeight(banner.effectiveFontSize())),
                banner::setPreferredHeight);
        panel.appendChild(banner);

        // 已选择成员：多列网格 + 可见滚动条（MemberGrid 模块，替代旧单列行）。
        ReadableSignal<List<SearchPickerData.CurrentMember>> members =
                Computed.create(() -> safeMembers(props));
        MemberGrid.Result grid = MemberGrid.create(rt, new MemberGrid.Props(
                members, props.enabled(), props.presentation(), props.visualAdapter(),
                memberIssues,
                memberId -> editMember(props, memberId, addingMember,
                        editingMember, focusIntent, variantsOpen, activeCandidate, mode, selectedKeys,
                        gridHighlight),
                memberId -> removeMember(rt, props, memberId, addingMember, editingMember,
                        variantsOpen, activeCandidate, gridHighlight, focusIntent, tombstone),
                // 卡尺寸兼容位：有度量通道时内部按字号派生（P5 §2.5），此处只作旧口径兜底。
                MemberGrid.DEFAULT_CELL_WIDTH, MemberGrid.DEFAULT_CELL_HEIGHT,
                MemberGrid.DEFAULT_GAP_X, MemberGrid.DEFAULT_GAP_Y,
                viewportSizing ? metrics : null,
                // D5：徽章 hover 原因通道（hover 进入给成员、移出给 null），信息条据此解释原因。
                hoveredMember::set));
        grid.root().setFlexGrow(1);
        panel.appendChild(grid.root());
        rt.show(panel, Computed.create(() -> Boolean.valueOf(members.get().isEmpty())),
                () -> emptyText(rt, props.presentation().emptyCurrentMembers(),
                        secondaryForeground));

        // 撤销条（P5 §5.5 E1 甲形态：删除即生效 + 5s 内可撤销；至多 1 条、到期/关闭即释放）。
        // 结构上追加在成员带末尾：不移动既有子节点下标；无 tombstone 时整行零高。
        //
        // ★ 内容折叠声明（U-P5-17 根除「grow 先验闸门」）—— 成员带「高度有界 + 带内滚动」的前提条件：
        //   ConstraintResolver.computeColumnGrowHeights 为 COLUMN 容器分配 grow 高度前，要求每个
        //   固定兄弟的先验高可知；而「有子节点的容器 + preferredHeight == 0」按旧口径先验不可知
        //   （容器内容撑大无法先验，见 ConstraintResolver.priorKnownChildHeight）⇒ 该容器一出现，
        //   整条 grow 分配即被放弃（运行期 WARN「COLUMN 容器 grow 分配放弃：固定兄弟高度无法先验」），
        //   成员网格容器（flexGrow=1 + fillParentHeight）回退 shrink-to-fit ⇒ viewport 被内容撑大、
        //   SceneGeometry.maxScrollY == 0 ⇒ 成员带高度变成内容高、面板高度溢出宿主逻辑盒，
        //   超出宿主可见区的成员卡片指针不可达（键盘路径不受影响，故此前只在指针侧暴露）。
        //   第五轮曾用「按可见性挂摘子内容」消除触发条件（U-P5-15 临时形态）；本轮改为<b>声明式</b>：
        //   SceneNode.setCollapsed(true) 声明「本行内容退出布局域」—— 行按零内容叶参与父流
        //   （子树与自身文本都不参与尺寸推导，先验高与内容高同口径）⇒ 先验恒可知，闸门不必松动。
        //   子控件常驻（结构稳定，无挂摘）：折叠期不参与布局/绘制/命中/焦点，撤销按钮 enabled 信号
        //   仍为 undoVisible（隐藏期既不在 Tab 环也不可命中）。
        //   等价性由测试逐值钉死（两种形态布局结果一致）：
        //   CollapsedLayoutEquivalenceTest#collapseDeclarationMatchesDetachFormValueByValue。
        //   对外口径不变：撤销条行仍是成员带末位子节点，隐藏态 preferredHeight 仍为 0（零占位）。
        SceneNode toastRow = SceneNode.row();
        toastRow.setCrossAxisAlign(CrossAxisAlign.CENTER);
        toastRow.setGap(SceneChromeTokens.GAP_SM);
        toastRow.setPadding(0, SceneChromeTokens.PAD_MD, 0, SceneChromeTokens.PAD_MD);
        toastRow.setClipChildren(true);
        toastRow.setHitTestable(false);
        ReadableSignal<String> toastText = Computed.create(() -> {
            Tombstone current = tombstone.get();
            return current == null ? "" : props.panelPresentation().removedToast(current.name());
        });
        SceneNode toastLabel = text(rt, "");
        toastLabel.setFlexGrow(1);
        toastLabel.setMaxLines(1);
        toastLabel.setEllipsis(true);
        rt.bind(secondaryForeground, toastLabel::setTextColor);
        rt.bindText(toastLabel, toastText);
        toastRow.appendChild(toastLabel);
        SceneNode undoButton = SceneButton.create(rt, new SceneButton.Props(
                Signal.create(props.panelPresentation().undoAction()), undoVisible,
                () -> undoRemove(props, tombstone))).get();
        undoButton.setWidthSizing(WidthSizing.SHRINK);
        toastRow.appendChild(undoButton);
        rt.bindComputed(() -> Integer.valueOf(Boolean.TRUE.equals(undoVisible.get())
                        ? rt.lineHeight(toastLabel.effectiveFontSize()) : 0),
                toastRow::setPreferredHeight);
        // 折叠式显隐（声明式，替代第五轮的挂摘形态）：不可见 → setCollapsed(true)，子树退出布局/
        // 绘制/命中/焦点四面，行退化为零高叶；可见 → setCollapsed(false)，子控件随行整体重算
        // （折叠期子树保持脏态，无「解折叠补标脏」第二通道）。
        // 幂等：setCollapsed 值未变即短路，信号重复发同值不产生失效。
        rt.bind(undoVisible, visible -> Effect.untrack(
                () -> toastRow.setCollapsed(!Boolean.TRUE.equals(visible))));
        panel.appendChild(toastRow);
        return panel;
    }

    /**
     * 删除成员（MemberGrid 回调）：宿主提交成功后才清理面板临时态（武装/编辑/变体浮层/
     * 网格高亮/焦点意图），与 finishSelection/cancelPanel 的收尾集合对齐；宿主拒绝时零推进。
     */
    private static boolean removeMember(SceneRuntime rt, Props props, long memberId,
                                        Signal<Boolean> addingMember, Signal<Boolean> editingMember,
                                        Signal<Boolean> variantsOpen,
                                        Signal<SearchPickerData.Candidate> activeCandidate,
                                        Signal<Integer> gridHighlight, Signal<FocusIntent> focusIntent,
                                        Signal<Tombstone> tombstone) {
        // 删除前抓展示名（删除成功后成员已不在列表里，名字取不到）。
        SearchPickerData.CurrentMember member = memberById(props, memberId, null);
        String name = member == null ? "" : props.presentation().currentMemberPrimary(member);
        if (!props.onRemoveCurrent().test(memberId)) {
            return false;
        }
        // 闸口：删除即生效 + 5s 撤销窗口（至多 1 条 —— 新删除直接替换旧 tombstone）。
        tombstone.set(new Tombstone(memberId, name,
                rt.__frameTimeNanos().get().longValue()
                        + PickerDensityTokens.REMOVE_UNDO_WINDOW_MS * 1_000_000L));
        addingMember.set(Boolean.FALSE);
        editingMember.set(Boolean.FALSE);
        variantsOpen.set(Boolean.FALSE);
        activeCandidate.set(null);
        gridHighlight.set(Integer.valueOf(-1));
        focusIntent.set(FocusIntent.GRID);
        return true;
    }

    /**
     * 撤销最近一次删除（E1）：走宿主的可拒绝提交边界，成功后释放 tombstone；
     * 宿主拒绝时保留 tombstone（用户可重试，窗口到期自然释放）。
     */
    private static void undoRemove(Props props, Signal<Tombstone> tombstone) {
        Tombstone current = tombstone.get();
        if (current == null) {
            return;
        }
        if (!props.onRestoreCurrent().test(current.memberId())) {
            return;
        }
        tombstone.set(null);
        props.onDiscardRemoved().run();
    }

    /** 删除 tombstone（原值恢复用；展示名用于撤销条文案）。 */
    @Desugar
    private record Tombstone(long memberId, String name, long deadlineNanos) { }

    /**
     * 成员徽章 hover 原因（P5 §5.4 D5）：无效/重复判定 → Presentation 注入的原因文案。
     *
     * <p>稳定 ID 口径：合法成员 = 候选 key；无效成员 = {@code #memberId}（其 raw 无法解析为候选选择）。
     * 原始 raw 经成员副文本口径（{@link SearchPickerPresentation#currentMemberSecondary}）携带 ——
     * 该文本本身就是「原始 raw 的展示形态」，不另立第二份 raw 数据来源。</p>
     *
     * @return 原因文案；该成员当前没有徽章（既非无效也非重复）时返回空串（信息条回落既有优先级）
     */
    private static String memberIssueReason(Props props, ReadableSignal<MemberIssues> memberIssues,
                                            SearchPickerData.CurrentMember member) {
        boolean invalid = member.selection() == null;
        boolean duplicate = !invalid && memberIssues.get().duplicateMemberIds()
                .contains(Long.valueOf(member.memberId()));
        if (!invalid && !duplicate) {
            return "";
        }
        String severity = invalid ? props.presentation().errorSeverity()
                : props.presentation().warningSeverity();
        String issue = invalid ? props.presentation().invalidIssue()
                : props.presentation().duplicateIssue();
        String stableId = invalid ? "#" + member.memberId() : member.selection().candidateKey();
        return props.presentation().memberIssueReason(severity, issue, stableId,
                props.presentation().currentMemberSecondary(member));
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

    /**
     * 点击/ENTER 激活候选：无变体直达 selectionCommit，有变体开变体浮层。
     *
     * <p>候选本体经数据面解析（ADR §3.8 Q12 第 10 行）：SPI 路径 = {@code source.exact(key)} 的
     * O(1) 定位，旧路径 = 过滤后列表线性查（该路径下列表就是全集）。切片路径下不再对「窗口切片」
     * 线性扫描——那既找不到本体，也会随滚动位置失真。</p>
     *
     * <p><b>「已配置」候选的点击契约（T5 UX-18 / P4 偏差 D-P4-3）</b>：SPI 路径不再从结果里排除
     * 已在当前规则中的候选，点击这类候选与点击未配置候选<b>走完全相同的路径</b>（无变体直达提交、
     * 有变体开浮层），<b>不做静默丢弃</b>——重复由成员问题的「重复」徽章如实呈现（F3/F4 口径），
     * 「已配置」状态则由结果单元圆点与信息条文案提前告知。若将来要改成「点击即移除」一类语义，
     * 必须同时改本方法与信息条文案，并补守卫测试。</p>
     */
    private static void activateCandidate(Object key, Props props, Runnable closeRequest,
                                          Feed feed,
                                          Signal<Boolean> variantsOpen,
                                          Signal<SearchPickerData.Candidate> activeCandidate,
                                          Signal<SearchPickerData.SelectionMode> mode,
                                          Signal<List<String>> selectedKeys,
                                          Signal<Integer> gridHighlight,
                                          Signal<Boolean> addingMember,
                                          Signal<Boolean> editingMember,
                                          Signal<FocusIntent> focusIntent) {
        SearchPickerData.Candidate candidate = feed.resolver().apply(key);
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

    // ==================== 采样埋点（只加观测，不改渲染与交互语义） ====================

    /**
     * 记录一次候选窗口派生的规模与耗时（SPI 路径每次 pageProvider 拉片、旧路径每次全量项派生各记一次）。
     *
     * <p>口径与 ADR §6.2 一致：{@code candidateCount} = 本次查询看到的候选总规模
     * （浏览 lane = {@code source.size()}；搜索 lane = 真实命中数 {@code matchCount}，不受窗口上限影响），
     * {@code itemCount} = 本次实际参与挂载的项数 —— 二者之比即虚拟化比例。
     * 采样关闭时本方法在第一道判断即返回。</p>
     *
     * @param startedAtNanos 起始时间戳；0 表示采样关闭（调用方已按 Config.useDebug 取值）
     * @param candidateCount 本次查询的候选总规模（不受窗口上限裁剪）
     * @param itemCount 本次派生产出的网格项数（挂载窗口大小）
     */
    private static void recordGridTransform(long startedAtNanos, int candidateCount, int itemCount) {
        if (startedAtNanos == 0L) {
            return;
        }
        UiPerformanceMonitor monitor = UiPerformanceMonitor.getInstance();
        monitor.recordCounter(UiPerfMarkers.COUNTER_PICKER_CANDIDATES, candidateCount);
        monitor.recordCounter(UiPerfMarkers.COUNTER_PICKER_RESULTS, itemCount);
        monitor.recordPhase(UiPerfMarkers.PHASE_PICKER_GRID_TRANSFORM, System.nanoTime() - startedAtNanos);
    }

    /**
     * 记录一个已完成的阶段耗时。
     *
     * @param phaseName 阶段名常量
     * @param startedAtNanos 起始时间戳；0 表示采样关闭（此时不做任何观测动作）
     */
    private static void recordPhase(String phaseName, long startedAtNanos) {
        if (startedAtNanos == 0L) {
            return;
        }
        UiPerformanceMonitor.getInstance().recordPhase(phaseName, System.nanoTime() - startedAtNanos);
    }

    // ==================== 数据面（候选相关派生的唯一出口） ====================

    /**
     * 面板数据面：候选相关派生的统一出口（两条路径各自构造，节点构建只读本抽象）。
     *
     * <p>生命周期 = 内容 Owner（每次 {@code open=true} 重建、关闭即释放），故字段是「每次挂载一份」
     * 的容器；实现不得跨挂载复用。</p>
     */
    private static final class Feed {
        /** 旧路径：全量项信号；SPI 路径：恒空（切片只经 {@link #pageProvider} 给）。 */
        private final ReadableSignal<List<Item>> listItems;
        /** SPI 路径：窗口切片生产者；旧路径 null（控件对全量 items 自切片）。 */
        private final SearchResultList.PageProvider pageProvider;
        /** 当前查询总量（SPI = {@code size()} / {@code min(matchCount,maxItems)}；旧路径 = 过滤后候选数）。 */
        private final ReadableSignal<Integer> totalItems;
        /** 结果是否被搜索上限截断（信息条常驻提示）。 */
        private final ReadableSignal<Boolean> truncated;
        /** 分类导航行（旧路径由候选列表动态计数；SPI 路径取 {@code source.categories(dimension)}）。 */
        private final ReadableSignal<List<CategoryRow>> categoryRows;
        /** 候选本体解析（激活路径；SPI = {@code source.exact(key)}，旧路径 = 过滤后列表精确查）。 */
        private final Function<Object, SearchPickerData.Candidate> resolver;

        private Feed(ReadableSignal<List<Item>> listItems, SearchResultList.PageProvider pageProvider,
                     ReadableSignal<Integer> totalItems, ReadableSignal<Boolean> truncated,
                     ReadableSignal<List<CategoryRow>> categoryRows,
                     Function<Object, SearchPickerData.Candidate> resolver) {
            this.listItems = listItems;
            this.pageProvider = pageProvider;
            this.totalItems = totalItems;
            this.truncated = truncated;
            this.categoryRows = categoryRows;
            this.resolver = resolver;
        }

        private ReadableSignal<List<Item>> listItems() { return listItems; }
        private SearchResultList.PageProvider pageProvider() { return pageProvider; }
        private ReadableSignal<Integer> totalItems() { return totalItems; }
        private ReadableSignal<Boolean> truncated() { return truncated; }
        private ReadableSignal<List<CategoryRow>> categoryRows() { return categoryRows; }
        private Function<Object, SearchPickerData.Candidate> resolver() { return resolver; }
    }

    /** SPI 路径的 items 占位：窗口切片只经 {@code pageProvider} 给，此信号恒空且零分配。 */
    private static final ReadableSignal<List<Item>> NO_ITEMS = Collections::emptyList;

    /**
     * SPI lane 视图：一次求值给出「查询条件 + 候选规模 + 窗口总量 + 截断真值」
     * （O(1) 或一次 matchCount）。
     *
     * <p>{@code candidateCount} = 本次查询看到的候选总规模（浏览 lane = {@code size()}、
     * 搜索 lane = 真实命中数），是 {@code picker.candidates} 的口径（ADR §6.2）；
     * {@code totalItems} = 窗口数学的总量（搜索 lane 被 {@code searchMaxItems} 截到上限）。</p>
     */
    @Desugar
    private record LaneView(PickerQuery query, int candidateCount, int totalItems, boolean truncated) { }

    /**
     * 旧路径数据面（无候选源，T-1 回退）：结果信号自带候选全集，面板侧过滤 + 全量项派生。
     *
     * <p>分类过滤仅在旧路径发生（ADR §1.7 D-12/T-6）；{@code resultsCategoryFiltered} 供
     * 「上层已按分类过滤的全量结果信号」形态跳过面板二次过滤。</p>
     */
    private static Feed legacyFeed(Props props, ReadableSignal<String> categoryKey) {
        ReadableSignal<List<SearchPickerData.Candidate>> filtered = Computed.create(() -> {
            List<SearchPickerData.Candidate> candidates = safeResults(props).candidates();
            if (props.resultsCategoryFiltered()) {
                return candidates;
            }
            return ScenePickerPanelNav.filterByCategory(candidates, categoryKey.get(), props.categoryOf());
        });
        ReadableSignal<List<Item>> gridItems = Computed.create(() -> {
            long gridStartedAtNanos = Config.useDebug ? System.nanoTime() : 0L;
            List<SearchPickerData.Candidate> candidates = filtered.get();
            List<Item> items = toItems(props, candidates);
            recordGridTransform(gridStartedAtNanos, candidates.size(), items.size());
            return items;
        });
        return new Feed(gridItems, null,
                () -> Integer.valueOf(gridItems.get().size()),
                () -> Boolean.valueOf(safeResults(props).truncated()),
                Computed.create(() -> ScenePickerPanelNav.categoryRows(safeCategories(props),
                        safeResults(props).candidates(), props.categoryOf(),
                        props.panelPresentation().allCategoryLabel())),
                key -> candidateByKey(filtered.get(), key));
    }

    /**
     * SPI 路径数据面（ADR §3.2「唯一实现」）：持有查询条件信号 + 候选源引用 + 搜索窗口上限，
     * 按控件产出的 {@link SearchResultList.WindowRequest} 拉取窗口切片 —— 面板<b>不持有候选全集</b>。
     *
     * <p>总量与截断都来自同一次 lane 求值（浏览 lane = {@code size()}、无上限；搜索 lane =
     * {@code min(matchCount, searchMaxItems)} + {@code truncated = hits > maxItems}）。</p>
     */
    private static Feed sourceFeed(Props props) {
        final PickerCandidateSource source = props.candidateSource();
        final ReadableSignal<LaneView> lane = laneView(props);
        SearchResultList.PageProvider pageProvider = request -> {
            LaneView view = lane.get();
            int offset = Math.max(0, request.offset());
            int limit = Math.max(0, Math.min(request.limit(), view.totalItems() - offset));
            long startedAtNanos = Config.useDebug ? System.nanoTime() : 0L;
            PickerSourceGuard.requireMainThread("page");
            List<SearchPickerData.Candidate> candidates = source.page(view.query(), offset, limit);
            List<Item> items = toItems(props,
                    candidates == null ? Collections.<SearchPickerData.Candidate>emptyList() : candidates);
            recordGridTransform(startedAtNanos, view.candidateCount(), items.size());
            return new SearchResultList.WindowPage(items, view.totalItems());
        };
        return new Feed(NO_ITEMS, pageProvider,
                Computed.create(() -> Integer.valueOf(lane.get().totalItems())),
                Computed.create(() -> Boolean.valueOf(lane.get().truncated())),
                Computed.create(() -> {
                    readVersion(props);
                    PickerQuery query = lane.get().query();
                    PickerSourceGuard.requireMainThread("categories");
                    return ScenePickerPanelNav.categoryRowsFromSource(
                            source.categories(query.categoryDimension()), lane.get().totalItems(),
                            props.panelPresentation().allCategoryLabel());
                }),
                key -> {
                    PickerSourceGuard.requireMainThread("exact");
                    return source.exact(String.valueOf(key));
                });
    }

    /**
     * 当前查询的 lane 视图（SPI 路径唯一查询求值点）。
     *
     * <p>依赖 = 查询条件信号 + 源版本信号。版本信号是**依赖而非逐帧判断**：语言/资源/注册表变化经
     * 装配层 {@code PickerRevisionBridge} 合成为 Signal 后自动重算（ADR §2.4 的「UILib 拉版本号」）。</p>
     */
    private static ReadableSignal<LaneView> laneView(Props props) {
        final PickerCandidateSource source = props.candidateSource();
        final int searchMaxItems = props.searchMaxItems();
        return Computed.create(() -> {
            readVersion(props);
            PickerQuery query = props.sourceQuery().get();
            if (query.isBrowse()) {
                PickerSourceGuard.requireMainThread("size");
                int total = Math.max(0, source.size());
                // 浏览 lane 无上限：候选规模 = 窗口总量 = size()，不存在 cap/分页。
                return new LaneView(query, total, total, false);
            }
            PickerSourceGuard.requireMainThread("matchCount");
            int hits = Math.max(0, source.matchCount(query));
            return new LaneView(query, hits, Math.min(hits, searchMaxItems), hits > searchMaxItems);
        });
    }

    /**
     * 结果区是否仍有滚动余量（信息条 scrollHint 的触发条件）。
     *
     * <p>读结果列表的窗口模型观察面（{@code maxScrollPx > 0}），不新造第二份滚动事实；
     * 窗口模型在关闭时被置空，故对 null 保守返回 false。</p>
     */
    private static boolean scrollable(
            AtomicReference<ReadableSignal<SceneGridWindow.WindowModel>> holder) {
        ReadableSignal<SceneGridWindow.WindowModel> signal = holder.get();
        if (signal == null) {
            return false;
        }
        SceneGridWindow.WindowModel model = signal.get();
        return model != null && model.maxScrollPx() > 0;
    }

    /** 建立「源版本 → 本次求值」依赖（无版本信号时为 no-op）。 */
    private static void readVersion(Props props) {
        ReadableSignal<PickerSourceVersion> version = props.sourceVersion();
        if (version != null) {
            version.get();
        }
    }

    /** 候选 → 网格项：标签保留完整文本（省略归渲染层），单候选图标源失败降级无图占位。 */
    private static List<Item> toItems(Props props, List<SearchPickerData.Candidate> candidates) {
        ArrayList<Item> items = new ArrayList<Item>(candidates.size());
        for (SearchPickerData.Candidate candidate : candidates) {
            String label = props.visualAdapter().candidateLabel(candidate);
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

    /**
     * 编辑态横幅的成员展示名：按受控 {@code currentSelection} 的候选键在当前成员里定位
     * （编辑目标由宿主的 binding 持有，面板不复制该状态）；定位不到时返回空串（横幅仍显示前缀）。
     */
    private static String editingMemberName(Props props) {
        SearchPickerData.Selection selection = props.currentSelection().get();
        if (selection == null) {
            return "";
        }
        for (SearchPickerData.CurrentMember member : safeMembers(props)) {
            SearchPickerData.Selection memberSelection = member.selection();
            if (memberSelection != null
                    && selection.candidateKey().equals(memberSelection.candidateKey())) {
                return props.presentation().currentMemberPrimary(member);
            }
        }
        return "";
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

    /**
     * 控件根字号声明的只读投影：跟随布局纪元重读 {@link SceneNode#declaredFontSize()}。
     *
     * <p>面板内容建在 portal 树里，与控件根（{@code Result.root}）不同树，父链继承覆盖不到。
     * 本信号喂 portal 入口（{@link ScenePortalHandle#fontSize(ReadableSignal)}）把「控件根声明」
     * 落到内容根上，面板内全部文字再沿父链继承 —— 声明只有一处（控件根），不存在逐节点写字号的传播通道。</p>
     *
     * <p>读<b>声明值</b>而非生效值：用户倍率在接收节点自己的解析出口生效一次，传播生效值会重复缩放。</p>
     *
     * @param rt     场景运行时
     * @param anchor 控件根（字号真值所在）
     * @return 控件根声明的只读信号
     */
    private static ReadableSignal<Integer> panelDeclaredFontSignal(SceneRuntime rt, SceneNode anchor) {
        if (Owner.current() != null) {
            return createPanelDeclaredFont(rt, anchor);
        }
        // 在 mount 回调之外调用控件工厂时（如直接构造）与 rt.bind 一样归 runtime 根 Owner，避免独立 Computed 泄漏。
        AtomicReference<ReadableSignal<Integer>> holder = new AtomicReference<ReadableSignal<Integer>>();
        rt.__runRoot(() -> holder.set(createPanelDeclaredFont(rt, anchor)));
        return holder.get();
    }

    /**
     * 解析面板内容根的<b>声明</b>字号：宿主字号链上的显式声明优先，未声明时用档位基准字号。
     *
     * <p><b>为什么不能直接继承锚点解析值</b>：锚点在 {@link #create} 期还没挂树，字号解析会落到
     * 层 4b 框架常量（{@code FontSizeLimits.DEFAULT_FONT_SIZE_PX} = 16），把它写进 portal 内容根
     * 就等于"面板默认字号 = 16"；而 P5 的整套几何按档位基准字号（标准档 12）派生
     * —— 同一面板里宽度/行高按 12 算、文字按 16 画，标签槽 40px 在 16px 字号下只放得下
     * 1 个汉字 + 省略号（用户症状「每个物品只显示第一个字」）。</p>
     *
     * <p>层 1/2/3 命中时说明宿主（配置屏/宿主字号线）确实声明了字号，面板跟随它是正确的继承语义；
     * 落到层 4a/4b 则说明"没有人声明"，此时面板该用自己的默认值而不是框架兜底常量。</p>
     *
     * @param rt     场景运行时
     * @param anchor 控件根（Result.root）
     * @return 面板声明字号（未乘用户倍率；逻辑 px）
     */
    private static int resolvePanelDeclaredFont(SceneNode anchor) {
        FontSource source = anchor.fontSizeSource();
        if (source == FontSource.EXPLICIT || source == FontSource.SCOPE
                || source == FontSource.ENVIRONMENT) {
            return anchor.declaredFontSize();
        }
        return PickerMetrics.defaultPanelDeclaredFontPx();
    }

    /**
     * 把派生面板盒写到宿主外层卡片（P5 §1.2）。
     *
     * <p>尺寸用<b>逻辑 px</b>：百分比在挂载前无法回答"结果区有多宽"，而列数预算必须在挂载前
     * 拿到（ADR §3.4 判据①）。窗口缩放时 {@code metrics} 重派生 → 卡片尺寸即时跟随。</p>
     *
     * @param rt      场景运行时
     * @param root    宿主外层卡片
     * @param metrics 派生度量信号
     */
    private static void applyPanelBox(SceneRuntime rt, SceneNode root,
                                      ReadableSignal<PickerMetrics> metrics) {
        root.setPreferredWidth(metrics.get().panel().widthPx());
        root.setPreferredHeight(metrics.get().panel().heightPx());
        rt.bind(metrics, m -> Effect.untrack(() -> {
            root.setPreferredWidth(m.panel().widthPx());
            root.setPreferredHeight(m.panel().heightPx());
        }));
    }

    /**
     * 创建 P5 派生度量信号（三分量：逻辑盒 + 字号倍率 + 密度偏好）。
     *
     * <p>形态是「Signal 持初值 + effect 只在派生输入变化时重写」：初值在挂载前<b>同步</b>算好，
     * 因此首帧几何（面板尺寸/列数/可视行数）就是稳态值，不存在收敛帧；之后的失效通道是三条 ——
     * {@link SceneRuntime#logicalBox()}（窗口缩放）、{@link SceneRuntime#fontEpochSignal()}（字号
     * 倍率/默认字号变化）、密度偏好信号与成员数信号（用户改档位 / 成员增减）。</p>
     *
     * <p>用「输入等价」而非对象相等做同值早退：{@code PickerMetrics} 是按输入确定性派生的值对象，
     * 输入不变则结果逐值相同，无需为它实现 {@code equals}。</p>
     *
     * @param rt    场景运行时
     * @param props 面板属性
     * @return 派生度量只读信号（非 null）
     */
    private static ReadableSignal<PickerMetrics> createMetrics(SceneRuntime rt, Props props,
                                                              ReadableSignal<Integer> panelDeclaredFont) {
        ReadableSignal<PickerDensityPreference> preference = props.densityPreference();
        LogicalBox initialBox = rt.logicalBox().get();
        Signal<PickerMetrics> metrics = Signal.create(PickerMetrics.derive(rt,
                initialBox.widthPx(), initialBox.heightPx(),
                panelFontSizePx(panelDeclaredFont, rt),
                preference == null ? PickerDensityPreference.AUTO : preference.get(),
                memberRowsFor(props)));
        rt.bindComputed(() -> {
            LogicalBox box = rt.logicalBox().get();
            rt.fontEpochSignal().get();
            PickerDensityPreference pref = preference == null
                    ? PickerDensityPreference.AUTO : preference.get();
            // 字号真值 = 面板内容根的「声明值 × 环境倍率」，与 SceneNode.effectiveFontSize() 的解析
            // 出口同式（见 PickerMetrics.fontSizeFor）：派生几何与真正渲染的文字字号逐值相同。
            // panelDeclaredFont 参与订阅 ⇒ 宿主改字号声明时几何随之重派生。
            return PickerMetrics.derive(rt, box.widthPx(), box.heightPx(),
                    panelFontSizePx(panelDeclaredFont, rt), pref, memberRowsFor(props));
        }, derived -> Effect.untrack(() -> {
            if (!sameDerivation(metrics.get(), derived)) {
                metrics.set(derived);
            }
        }));
        return metrics;
    }

    /**
     * 面板生效字号 = 面板声明字号 × 环境倍率（与 {@code SceneNode.effectiveFontSize()} 的解析出口同式）。
     *
     * <p>声明值经 {@code anchor} 的父链解析得到（{@link #resolvePanelDeclaredFont}），倍率取
     * {@link SceneRuntime#getFontScalePercent()}；两者相乘再夹取到字号域，因此派生几何消费的字号
     * 与内容根真正渲染的字号逐值相同。</p>
     *
     * @param panelDeclaredFont 面板声明字号投影（非 null）
     * @param rt                场景运行时
     * @return 生效字号（逻辑 px）
     */
    private static int panelFontSizePx(ReadableSignal<Integer> panelDeclaredFont, SceneRuntime rt) {
        return PickerMetrics.fontSizeFor(panelDeclaredFont.get().intValue(),
                rt.getFontScalePercent());
    }

    /** 派生输入等价判定（输入相同 ⇒ 派生结果逐值相同）。 */
    private static boolean sameDerivation(PickerMetrics a, PickerMetrics b) {
        return a.logicalWidthPx() == b.logicalWidthPx()
                && a.logicalHeightPx() == b.logicalHeightPx()
                && a.fontSizePx() == b.fontSizePx()
                && a.preference() == b.preference()
                && a.membersRows() == b.membersRows();
    }

    /**
     * 成员行数预算（P5 §1.2）：无成员带 = -1（不占高）、无成员 = 0（折叠为一行提示）、
     * 有成员 = 最多 2 行（超出由 MemberGrid 内部滚动，成员带不再随成员数无界长高）。
     *
     * @param props 面板属性
     * @return 成员行数预算
     */
    private static int memberRowsFor(Props props) {
        if (!props.listMembers()) {
            return -1;
        }
        return safeMembers(props).isEmpty() ? 0 : PickerDensityTokens.MEMBER_ROWS_MAX;
    }

    /** null 安全字符串（空串兜底）。 */
    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    /** 面板声明字号的响应式投影：布局纪元变化即重读（同值由 Computed 记忆化去重）。 */
    private static ReadableSignal<Integer> createPanelDeclaredFont(SceneRuntime rt, SceneNode anchor) {
        return Computed.create(Integer.valueOf(resolvePanelDeclaredFont(anchor)), () -> {
            rt.layoutDoneSignal().get();
            return Integer.valueOf(resolvePanelDeclaredFont(anchor));
        });
    }

    private static List<String> immutableKeys(List<String> keys) {
        return Collections.unmodifiableList(new ArrayList<String>(keys));
    }

    private static SceneNode text(SceneRuntime rt, String value) {
        SceneNode node = new SceneNode();
        node.setText(value == null ? "" : value);
        node.setHitTestable(false);
        return node;
    }

    /** 空态提示：次要前景（muted/disabled 派生档，CategoryNavPane 空态同口径）。 */
    private static SceneNode emptyText(SceneRuntime rt, String value,
                                       ReadableSignal<Integer> secondaryForeground) {
        SceneNode node = text(rt, value);
        node.setPadding(SceneChromeTokens.PAD_MD);
        rt.bind(secondaryForeground, node::setTextColor);
        return node;
    }
}
