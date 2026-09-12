package club.heiqi.uilib.ui.scene.control.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.ui.diagnostic.UiPerfMarkers;
import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneGridSnapshot;
import club.heiqi.uilib.ui.scene.control.SceneGridWindow;
import club.heiqi.uilib.ui.scene.control.SceneScrollContainer;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGridNav;
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
import club.heiqi.uilib.ui.scene.node.TextHorizontalAlign;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneRenderProtocolTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SearchResultList —— 无上限普通候选网格（搜索选择器的结果列表）。
 *
 * <h3>定位：非虚拟化的全量列表</h3>
 * <p>与 {@link SceneVirtualGrid} 不同，本控件<b>不做行级虚拟化</b>，也不设项数上限：
 * 数据源全部项按列数直接分行挂载，普通滚动。因此无需窗口/垫片/overscan 数学；
 * 滚动由 {@link SceneScrolls} 的 GEOMETRY 级滚轮与键盘导航的编程滚动驱动，
 * {@code SceneGeometry.maxScrollY} 恒正确（内容高 = 实际挂载行高）。
 * 大数据量（如「全部」分类数千方块）会全量挂载，性能取舍由调用方接受。</p>
 *
 * <h3>单元轨道与字号（S5 ③ 收口，与 SceneVirtualGrid 的差异点）</h3>
 * <p>{@link Props#cellHeight()} 是<b>轨道高下限</b>：实际轨道高 = max(cellHeight,
 * {@code lineHeight(生效字号) + LABEL_GAP + 1(图位最小) + 2*CELL_PADDING})，由控件根的
 * {@code setFontSizeMetric} 随字号重算，行高 / 单元高 / 图位高统一读同一信号。因此字号放大
 * 不会裁标签（本控件不做行级虚拟化，内容高变化由滚动与自动回夹承担）。</p>
 * <p>对照：{@link SceneVirtualGrid} 的 stride 参与 spacer 与窗口数学（公共语义），轨道高仍为
 * 调用方固定值，其字号限制在该类 javadoc 登记。</p>
 *
 * <h3>滚动条</h3>
 * <p>根节点 = {@link SceneScrollContainer} 工厂产出（container 行 = viewport + 右侧滚动条列），
 * 默认滚动条视觉复用 {@link SceneScrollContainer#defaultScrollbarSpec()}。</p>
 *
 * <h3>渲染分级回退</h3>
 * <p>本模块经 {@link ItemRenderFallbackKeys} 共享装配订阅渲染分级：平台渲染层把某 registryKey
 * 分级为 {@code UNRENDERABLE} 后，对应单元图标回退占位底色（与无图片项同款样式），不再继续
 * 尝试渲染；registryKey（注册名:meta）按注册名拆段对齐条目 key（注册名）。</p>
 *
 * <h3>外观归属（液态玻璃迁移，G13）</h3>
 * <p>滚动视口底座沿用 {@link SceneScrollContainer} 默认路径——由 {@code SceneSurfaceBinder}
 * 独占 background/border/borderWidth/cornerRadius/backdrop/surfaceElevation（主题
 * {@code GROUP} 角色配方，低干扰内容底座；本控件是被宿主面板内嵌的结果区而非独立浮层，
 * {@code OVERLAY} 玻璃归宿主浮层根，不在这里叠第二层）。结果单元只做<b>轻量底色覆盖</b>：
 * 仅写 backgroundColor（选中取主题选区背景 alpha 0x59、hover 取主题 accent alpha 0x1F，
 * 与 {@code SceneAutocomplete} 候选行同一口径，
 * 选中强于 hover、不只靠透明度区分；禁用时清空，禁用反馈由文字禁用前景与输入守卫承担），
 * 单元自身不装滤镜、不写边框圆角。行文字取主题 {@code mutedForeground}（旧
 * {@code TEXT_SECONDARY} 同值起步）/ {@code disabledForeground}；物品图像/缩略图渲染协议
 * （占位底色与图位圆角）不改色。</p>
 *
 * <h3>单元视觉与交互</h3>
 * <p>单元结构复刻 {@link SceneVirtualGrid#cellComponent}：占位底色、icon 尺寸
 * （cellHeight - 2*CELL_PADDING - 有label时(lineHeight+LABEL_GAP)）、label 12px 居中、
 * 图位圆角 RADIUS_SM、选中态 = item 在完整列表中的下标 == highlighted（按 key 动态派生，
 * 数据源更新/重排后选中与 hover 不串项）、CLICK 激活 + 高亮回写；悬停/选中底色为上述
 * 主题轻量覆盖，不再走 {@code SceneControlChrome.bindSelectableBackground} 旧实色接缝。</p>
 */
public final class SearchResultList {

    /** 单元内边距。 */
    public static final int CELL_PADDING = 4;
    /** 单元标签字号。 */
    public static final int LABEL_FONT_SIZE = 12;
    /** 图标与标签间距。 */
    public static final int LABEL_GAP = 2;
    /**
     * 无图片项的占位底色（属图像渲染协议，非主题槽位）。
     *
     * <p>集中定义处 = {@link SceneRenderProtocolTokens}（P5 U-P5-3）；本常量保留为公共兼容别名，
     * 值不变、语义不变。</p>
     */
    public static final int DEFAULT_PLACEHOLDER_COLOR = SceneRenderProtocolTokens.IMAGE_PLACEHOLDER_ARGB;
    /** 结果单元默认底色：全透明，露出底座玻璃（单元不各自采样滤镜）。 */
    private static final int CELL_BG_TRANSPARENT = SceneRenderProtocolTokens.TRANSPARENT_ARGB;
    /** 结果单元 hover 覆盖强度：主题 accent 的低透明度轻量覆盖（协议集中定义）。 */
    private static final int CELL_HOVER_ALPHA = SceneRenderProtocolTokens.CELL_HOVER_ALPHA;
    /** 结果单元选中（高亮）覆盖强度：主题选区背景，明显强于 hover（不只靠透明度区分）。 */
    private static final int CELL_SELECTED_ALPHA = SceneRenderProtocolTokens.CELL_SELECTED_ALPHA;
    /**
     * 额外挂载行数（overscan）：v1 固定 1（与 {@link SceneVirtualGrid} 现值一致），
     * 由 {@link SceneGridWindow#compute} 参数化。「滚动速度感知的 1-2 行」启用前必须写明
     * 失效条件（速度回落即回 1）与上界（≤2），否则属无界动态。
     */
    private static final int OVERSCAN_ROWS = 1;
    /**
     * 「已配置」标记圆点的边长下界（逻辑 px）。
     *
     * <p>边长本身由同一份 {@code GridMetrics} 的 {@code paddingPx} 派生（见 {@code cellComponent}），
     * 这里只登记可读性夹取边界 —— 与 {@code PickerDensityTokens} 的比例常量同口径：
     * 不携带任何"某分辨率下的确定尺寸"。</p>
     */
    private static final int MARKER_MIN_PX = 3;
    /** 「已配置」标记圆点的边长上界（逻辑 px）。 */
    private static final int MARKER_MAX_PX = 8;
    /** 「已配置」标记圆点距图位右上角的固定内缩（逻辑 px；纯视觉内缩，不参与布局派生）。 */
    private static final int MARKER_INSET_PX = 1;

    private SearchResultList() {
    }

    /**
     * 结果列表输入契约（不可变）。
     *
     * <p><b>窗口化（ADR §3.2）</b>：未提供 {@link #pageProvider()} 时 {@link #items()} 是
     * <b>全量只读数据源</b>（v1 默认形态；{@link #totalItems()} = -1 时总项数取 {@code items.size()}）；
     * 提供 {@link #pageProvider()} 时数据由控件按窗口模型向宿主<strong>拉取切片</strong>，
     * {@link #items()} 可为空列表。窗口数学（含 offset）只在控件内部发生。</p>
     *
     * @param items             数据源（非 null；未提供 pageProvider 时为全量只读数据源）
     * @param columns           列数；&lt;=0 时按可用宽度推算（availableWidth 预算优先，其次布局后 cachedLayout）
     * @param cellWidth         单元宽（UI 像素，&gt;0）
     * @param cellHeight        单元高下限（UI 像素，&gt;0；实际轨道高随生效字号抬升）
     * @param gapX              列间距（&gt;=0）
     * @param gapY              行间距（&gt;=0）
     * @param enabled           是否启用（禁用时不响应点击/键盘）
     * @param onActivate        点击/回车激活回调（非 null）
     * @param highlighted       受控高亮下标信号（非 null；全局下标）
     * @param onHighlightChange 高亮回写回调（非 null）
     * @param onHoverItem       单元 hover 回调（可为 null = 不回调；hover 时传 item、移出时传 null）
     * @param pageProvider      窗口切片生产者（可为 null = 走 items 全量自切片；主线程、纯拉取、无异步）
     * @param totalItems        数据总项数（全局规模）；{@link #UNSPECIFIED_TOTAL_ITEMS} = 取 items.size()
     * @param windowOffset      窗口切片全局起点的契约校验位（默认 0）：非 0 时必须等于
     *                          {@code windowStartRow * columns}（inv-W1），否则立即失败——宿主不得自算窗口
     * @param visibleRows       视口未布局时的预算可视行数（&gt;=1）：决定首帧挂载量上界；
     *                          布局后以实际视口高度派生的行数为权威
     * @param availableWidth    预算可用宽信号（可为 null = 布局后按 cachedLayout 推算列数；
     *                          非 null 时首帧即正确列数，不再有收敛帧）
     * @param totalItemsSignal  <b>动态总量通道</b>（P4 增补；可为 null = 用 {@code totalItems} 静态值）：
     *                          总量随查询/数据源变化的形态（如惰性候选源「浏览 lane = size() /
     *                          搜索 lane = min(matchCount, maxItems)」）必须经本信号进入窗口数学——
     *                          窗口 Computed 依赖它，总量变化即重派生 totalRows/maxScrollPx 并重新拉片。
     *                          与 {@code totalItems} 同时给出时以本信号为准
     * @param configuredKeys    已配置候选键集合（可为 null = 不做「已配置」标记）：SPI 路径不再排除
     *                          已在当前规则中的候选（P4 偏差 D-P4-3），该状态必须在结果单元上可区分
     *                          （T5 UX-18）—— 命中键的单元挂一颗主题强调色圆点，无额外布局占位
     */
    @Desugar
    public record Props(
            ReadableSignal<? extends List<SceneVirtualGrid.Item>> items,
            int columns, int cellWidth, int cellHeight, int gapX, int gapY,
            ReadableSignal<Boolean> enabled,
            Consumer<SceneVirtualGrid.Item> onActivate,
            ReadableSignal<Integer> highlighted,
            Consumer<Integer> onHighlightChange,
            Consumer<SceneVirtualGrid.Item> onHoverItem,
            PageProvider pageProvider,
            int totalItems,
            int windowOffset,
            int visibleRows,
            ReadableSignal<Integer> availableWidth,
            ReadableSignal<Integer> totalItemsSignal,
            ReadableSignal<GridMetrics> metrics,
            ReadableSignal<Set<String>> configuredKeys,
            Runnable onExitUp) {

        /** 未提供总量时的哨兵：窗口数学取 {@code items.size()}（全量数据源形态）。 */
        /**
         * 旧 18 参形态（P5 兼容，纯加法保留）：无「已配置」标记通道。
         *
         * @param items             数据源
         * @param columns           列数
         * @param cellWidth         单元宽
         * @param cellHeight        单元高下限
         * @param gapX              列间距
         * @param gapY              行间距
         * @param enabled           是否启用
         * @param onActivate        激活回调
         * @param highlighted       受控高亮
         * @param onHighlightChange 高亮回写
         * @param onHoverItem       hover 回调
         * @param pageProvider      窗口切片生产者
         * @param totalItems        数据总项数
         * @param windowOffset      窗口偏移校验位
         * @param visibleRows       预算可视行数
         * @param availableWidth    预算可用宽
         * @param totalItemsSignal  动态总量通道
         * @param metrics           P5 派生度量快照
         */
        public Props(ReadableSignal<? extends List<SceneVirtualGrid.Item>> items,
                     int columns, int cellWidth, int cellHeight, int gapX, int gapY,
                     ReadableSignal<Boolean> enabled,
                     Consumer<SceneVirtualGrid.Item> onActivate,
                     ReadableSignal<Integer> highlighted,
                     Consumer<Integer> onHighlightChange,
                     Consumer<SceneVirtualGrid.Item> onHoverItem,
                     PageProvider pageProvider, int totalItems, int windowOffset, int visibleRows,
                     ReadableSignal<Integer> availableWidth,
                     ReadableSignal<Integer> totalItemsSignal,
                     ReadableSignal<GridMetrics> metrics) {
            this(items, columns, cellWidth, cellHeight, gapX, gapY, enabled, onActivate, highlighted,
                    onHighlightChange, onHoverItem, pageProvider, totalItems, windowOffset,
                    visibleRows, availableWidth, totalItemsSignal, metrics, null, null);
        }

        /**
         * 旧 19 参形态（P5 兼容，纯加法保留）：无「↑ 退回上层控件」通道。
         *
         * @param items             数据源
         * @param columns           列数
         * @param cellWidth         单元宽
         * @param cellHeight        单元高下限
         * @param gapX              列间距
         * @param gapY              行间距
         * @param enabled           是否启用
         * @param onActivate        激活回调
         * @param highlighted       受控高亮
         * @param onHighlightChange 高亮回写
         * @param onHoverItem       hover 回调
         * @param pageProvider      窗口切片生产者
         * @param totalItems        数据总项数
         * @param windowOffset      窗口偏移校验位
         * @param visibleRows       预算可视行数
         * @param availableWidth    预算可用宽
         * @param totalItemsSignal  动态总量通道
         * @param metrics           P5 派生度量快照
         * @param configuredKeys    已配置候选键集合
         */
        public Props(ReadableSignal<? extends List<SceneVirtualGrid.Item>> items,
                     int columns, int cellWidth, int cellHeight, int gapX, int gapY,
                     ReadableSignal<Boolean> enabled,
                     Consumer<SceneVirtualGrid.Item> onActivate,
                     ReadableSignal<Integer> highlighted,
                     Consumer<Integer> onHighlightChange,
                     Consumer<SceneVirtualGrid.Item> onHoverItem,
                     PageProvider pageProvider, int totalItems, int windowOffset, int visibleRows,
                     ReadableSignal<Integer> availableWidth,
                     ReadableSignal<Integer> totalItemsSignal,
                     ReadableSignal<GridMetrics> metrics,
                     ReadableSignal<Set<String>> configuredKeys) {
            this(items, columns, cellWidth, cellHeight, gapX, gapY, enabled, onActivate, highlighted,
                    onHighlightChange, onHoverItem, pageProvider, totalItems, windowOffset,
                    visibleRows, availableWidth, totalItemsSignal, metrics, configuredKeys, null);
        }

        public static final int UNSPECIFIED_TOTAL_ITEMS = -1;
        /** 默认预算可视行数（与 {@code ScenePickerPanel.GridProps.DEFAULT} 对齐）。 */
        public static final int DEFAULT_VISIBLE_ROWS = 5;

        /**
         * 旧 17 参形态（P4 兼容，纯加法保留）：无 P5 派生度量通道。
         *
         * @param items             数据源
         * @param columns           列数
         * @param cellWidth         单元宽
         * @param cellHeight        单元高下限
         * @param gapX              列间距
         * @param gapY              行间距
         * @param enabled           是否启用
         * @param onActivate        激活回调
         * @param highlighted       受控高亮
         * @param onHighlightChange 高亮回写
         * @param onHoverItem       hover 回调
         * @param pageProvider      窗口切片生产者
         * @param totalItems        数据总项数
         * @param windowOffset      窗口偏移校验位
         * @param visibleRows       预算可视行数
         * @param availableWidth    预算可用宽
         * @param totalItemsSignal  动态总量通道
         */
        public Props(ReadableSignal<? extends List<SceneVirtualGrid.Item>> items,
                     int columns, int cellWidth, int cellHeight, int gapX, int gapY,
                     ReadableSignal<Boolean> enabled,
                     Consumer<SceneVirtualGrid.Item> onActivate,
                     ReadableSignal<Integer> highlighted,
                     Consumer<Integer> onHighlightChange,
                     Consumer<SceneVirtualGrid.Item> onHoverItem,
                     PageProvider pageProvider, int totalItems, int windowOffset, int visibleRows,
                     ReadableSignal<Integer> availableWidth,
                     ReadableSignal<Integer> totalItemsSignal) {
            this(items, columns, cellWidth, cellHeight, gapX, gapY, enabled, onActivate, highlighted,
                    onHighlightChange, onHoverItem, pageProvider, totalItems, windowOffset,
                    visibleRows, availableWidth, totalItemsSignal, null, null, null);
        }

        /**
         * 旧 16 参形态（P3 兼容，纯加法保留）：静态总量、无动态总量通道。
         *
         * @param items             数据源
         * @param columns           列数
         * @param cellWidth         单元宽
         * @param cellHeight        单元高下限
         * @param gapX              列间距
         * @param gapY              行间距
         * @param enabled           是否启用
         * @param onActivate        激活回调
         * @param highlighted       受控高亮
         * @param onHighlightChange 高亮回写
         * @param onHoverItem       hover 回调
         * @param pageProvider      窗口切片生产者
         * @param totalItems        数据总项数（静态）
         * @param windowOffset      窗口偏移校验位
         * @param visibleRows       预算可视行数
         * @param availableWidth    预算可用宽
         */
        public Props(ReadableSignal<? extends List<SceneVirtualGrid.Item>> items,
                     int columns, int cellWidth, int cellHeight, int gapX, int gapY,
                     ReadableSignal<Boolean> enabled,
                     Consumer<SceneVirtualGrid.Item> onActivate,
                     ReadableSignal<Integer> highlighted,
                     Consumer<Integer> onHighlightChange,
                     Consumer<SceneVirtualGrid.Item> onHoverItem,
                     PageProvider pageProvider, int totalItems, int windowOffset, int visibleRows,
                     ReadableSignal<Integer> availableWidth) {
            this(items, columns, cellWidth, cellHeight, gapX, gapY, enabled, onActivate, highlighted,
                    onHighlightChange, onHoverItem, pageProvider, totalItems, windowOffset,
                    visibleRows, availableWidth, null, null, null, null);
        }

        /**
         * 旧 11 参形态（T-2 兼容）：全量 items + 默认窗口字段。
         *
         * @param items             全量数据源
         * @param columns           列数
         * @param cellWidth         单元宽
         * @param cellHeight        单元高下限
         * @param gapX              列间距
         * @param gapY              行间距
         * @param enabled           是否启用
         * @param onActivate        激活回调
         * @param highlighted       受控高亮
         * @param onHighlightChange 高亮回写
         * @param onHoverItem       hover 回调
         */
        public Props(ReadableSignal<? extends List<SceneVirtualGrid.Item>> items,
                     int columns, int cellWidth, int cellHeight, int gapX, int gapY,
                     ReadableSignal<Boolean> enabled,
                     Consumer<SceneVirtualGrid.Item> onActivate,
                     ReadableSignal<Integer> highlighted,
                     Consumer<Integer> onHighlightChange,
                     Consumer<SceneVirtualGrid.Item> onHoverItem) {
            this(items, columns, cellWidth, cellHeight, gapX, gapY, enabled, onActivate, highlighted,
                    onHighlightChange, onHoverItem, null, UNSPECIFIED_TOTAL_ITEMS, 0,
                    DEFAULT_VISIBLE_ROWS, null, null, null, null, null);
        }

        /** 显式校验构造器。 */
        public Props {
            Objects.requireNonNull(items, "items");
            Objects.requireNonNull(enabled, "enabled");
            Objects.requireNonNull(onActivate, "onActivate");
            Objects.requireNonNull(highlighted, "highlighted");
            Objects.requireNonNull(onHighlightChange, "onHighlightChange");
            if (cellWidth <= 0) {
                throw new IllegalArgumentException("cellWidth 必须 > 0");
            }
            if (cellHeight <= 0) {
                throw new IllegalArgumentException("cellHeight 必须 > 0");
            }
            if (gapX < 0 || gapY < 0) {
                throw new IllegalArgumentException("gap 不可为负数");
            }
            if (totalItems < 0 && totalItems != UNSPECIFIED_TOTAL_ITEMS) {
                throw new IllegalArgumentException(
                        "totalItems 必须 >= 0 或为 UNSPECIFIED_TOTAL_ITEMS(-1)");
            }
            if (windowOffset < 0) {
                throw new IllegalArgumentException("windowOffset 不可为负数");
            }
            if (visibleRows < 1) {
                throw new IllegalArgumentException("visibleRows 必须 >= 1");
            }
        }
    }

    /**
     * 创建结果。
     *
     * @param root        根节点 = stackHost 行（viewport + 滚动条列），挂到宿主布局树
     * @param viewport    可滚动视口（焦点/滚动语义所在节点）
     * @param windowModel 窗口模型只读观察面（宿主/测试回读；不参与窗口数学）
     */
    @Desugar
    public record Result(SceneNode root, SceneNode viewport,
                         ReadableSignal<SceneGridWindow.WindowModel> windowModel,
                         ReadableSignal<SceneVirtualGrid.Item> highlightedItem) {

        /**
         * 旧 3 参形态（P5 兼容，纯加法保留）：无键盘高亮项观察面。
         *
         * @param root        根节点
         * @param viewport    可滚动视口
         * @param windowModel 窗口模型观察面
         */
        public Result(SceneNode root, SceneNode viewport,
                      ReadableSignal<SceneGridWindow.WindowModel> windowModel) {
            this(root, viewport, windowModel, null);
        }

        /**
         * 旧 2 参形态（兼容面保留，ADR A-24/A-25「零静默破坏」）：无窗口模型、无高亮观察面。
         *
         * <p>4.9.1 起的公共构造器签名，P3 窗口化后一度遗失；此处以委托形式恢复，保证外部
         * 调用方源码与二进制兼容（观察面为 {@code null}，消费者须容忍无观察面的历史形态）。</p>
         *
         * @param root     根节点
         * @param viewport 可滚动视口
         */
        public Result(SceneNode root, SceneNode viewport) {
            this(root, viewport, null, null);
        }
    }

    /**
     * 窗口请求：{@code offset/limit} 由控件按 {@link SceneGridWindow.WindowModel} 产出。
     *
     * <p>宿主不得自行推导 offset（{@code windowStartRow * columns} 是第二份窗口数学，ADR §3.2 明令禁止）。</p>
     *
     * @param offset 全局起始下标（= {@code windowStartRow * columns}）
     * @param limit  期望项数（= 挂载行数 * 列数）
     */
    @Desugar
    public record WindowRequest(int offset, int limit) {

        /** 校验：负偏移/负长度属宿主缺陷，立即失败。 */
        public WindowRequest {
            if (offset < 0 || limit < 0) {
                throw new IllegalArgumentException("offset/limit 不可为负");
            }
        }
    }

    /**
     * 窗口切片：{@code items.size() == min(limit, totalItems - offset)}。
     *
     * @param items      窗口项（非 null；可为空列表）
     * @param totalItems 数据总项数（全局规模，不受窗口裁剪影响）
     */
    @Desugar
    public record WindowPage(List<SceneVirtualGrid.Item> items, int totalItems) {

        /** 防御性：items 非 null、总量非负。 */
        public WindowPage {
            items = items == null ? Collections.<SceneVirtualGrid.Item>emptyList() : items;
            if (totalItems < 0) {
                throw new IllegalArgumentException("totalItems 不可为负");
            }
        }
    }

    /** 窗口切片生产者：纯拉取、主线程、无 Signal、无异步（ADR §3.2）。 */
    public interface PageProvider {

        /**
         * 取窗口切片。
         *
         * @param request 窗口请求（非 null；offset/limit 由控件产出）
         * @return 窗口切片（非 null）
         */
        WindowPage page(WindowRequest request);
    }

    /**
     * 构建结果列表控件。须在组件构建作用域（mount/portal builder）内调用，
     * 以便 effect/forEach 生命周期随组件卸载一并回收。
     *
     * @param rt    场景运行时（须注入文本度量，标签行高依赖度量）
     * @param props 输入契约（非 null）
     * @return 创建结果（root 挂到宿主布局树；viewport 供焦点/滚动观察）
     */
    public static Result create(SceneRuntime rt, Props props) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(props, "props");

        // 标准滚动结构走 SceneScrollContainer 工厂（默认滚动条视觉），不再手写 viewport+attach+scrollbar 样板。
        // P5 第六轮 U-P5-13：滚动条宽度接密度/字号派生（缺度量通道 ⇒ null ⇒ 常量缺省，逐值不变）。
        SceneScrollContainer.Result sc = SceneScrollContainer.createDefault(rt, 0, 0, 0, 0,
                PickerChrome.scrollbarWidthSignalOf(props.metrics()));
        SceneNode viewport = sc.viewport();
        Signal<Integer> scrollSignal = sc.scrollSignal();
        SceneNode stackHost = sc.container();

        // 单元轨道高（S5 ③ 收口）：以调用方 cellHeight 为下限，随生效字号抬升到
        // 「图位最小 + 标签行高 + 标签间距 + 上下内边距」，避免大字号下标签被单元裁切。
        // 本控件不做行级虚拟化（全量挂载、滚动承担高度），故轨道高可以随字号变；
        // 行/单元 preferredHeight 与图位高统一读本信号。
        // 层 4a 回落值：与单元标签的回落值同源（12），使控件根解析出的生效字号与标签一致——
        // 否则未声明字号时根的层 4b 默认 16 会让轨道按 16 算（默认几何漂移）。层 1/2/3 均优先于它。
        // P5 派生度量通道（非 null = 面板已按「逻辑盒 + 字号 + 密度」派生好唯一快照）：
        // 四个几何信号全部从同一份 GridMetrics 取初值并随其变化整体重写 —— 这就是 I-4 的
        // 「单一 GridMetrics 快照」：轨道高/图位边长/内边距/间距/列数不存在第二个真值来源。
        final ReadableSignal<GridMetrics> metricsSignal = props.metrics();
        GridMetrics initialMetrics = metricsSignal == null ? null : metricsSignal.get();
        stackHost.setFallbackFontSize(LABEL_FONT_SIZE);
        final Signal<Integer> trackHeight = Signal.create(Integer.valueOf(
                initialMetrics != null ? initialMetrics.trackHeightPx() : props.cellHeight()));
        final Signal<Integer> cellPadding = Signal.create(Integer.valueOf(
                initialMetrics != null ? initialMetrics.paddingPx() : CELL_PADDING));
        final Signal<Integer> labelGap = Signal.create(Integer.valueOf(
                initialMetrics != null ? initialMetrics.labelGapPx() : LABEL_GAP));
        final Signal<Integer> iconSide = Signal.create(Integer.valueOf(
                initialMetrics != null ? initialMetrics.iconSidePx() : 0));
        final Signal<Integer> cellGap = Signal.create(Integer.valueOf(
                initialMetrics != null ? initialMetrics.gapY() : props.gapY()));
        final Signal<Integer> cellWidth = Signal.create(Integer.valueOf(
                initialMetrics != null ? initialMetrics.cellWidthPx() : props.cellWidth()));
        if (initialMetrics != null) {
            rt.bind(metricsSignal, m -> Effect.untrack(() -> {
                setIfChanged(trackHeight, m.trackHeightPx());
                setIfChanged(cellPadding, m.paddingPx());
                setIfChanged(labelGap, m.labelGapPx());
                setIfChanged(iconSide, m.iconSidePx());
                setIfChanged(cellGap, m.gapY());
                setIfChanged(cellWidth, m.cellWidthPx());
            }));
        } else {
            // 旧路径：轨道高由控件根的字号度量回调抬升（cellHeight 为下限）。
            stackHost.setFontSizeMetric((node, fontSizePx) ->
                    trackHeight.set(Integer.valueOf(minTrackHeightFor(rt, props, fontSizePx))));
        }

        // 行步长（stride）唯一派生：GridMetrics（轨道高 + 行间距）。行高 / spacer / maxScrollPx / 滚动定位全部读它。
        ReadableSignal<Integer> stridePx = Computed.create(() -> Integer.valueOf(
                GridMetrics.stridePxOf(trackHeight.get().intValue(),
                        cellGap.get().intValue())));

        // 视口高度：布局完成后重读（同值早退）。未布局时为 0 —— 窗口数学退回预算行数，
        // 首帧挂载量因此有界（≤ 预算行数 + overscan），与数据规模 N 无关。
        Signal<Integer> viewportHeightPx = Signal.create(Integer.valueOf(0));
        rt.bind(rt.layoutDoneSignal(), epoch -> Effect.untrack(() -> {
            int height = visibleHeight(viewport);
            if (height != viewportHeightPx.get().intValue()) {
                viewportHeightPx.set(Integer.valueOf(height));
            }
        }));

        // 生效列数：availableWidth 预算优先（首帧即正确列数、无收敛帧），
        // 未提供预算时退回「布局完成后按 cachedLayout 宽推算」（ADR §3.4）。
        // 列数优先级（P5）：派生度量 > 显式 columns > availableWidth 预算 > 布局后 cachedLayout。
        // 度量通道存在时列数由 PickerMetrics 用「面板盒 - 导航 - 内边距」预算在<b>挂载前</b>算好，
        // 因此首帧列数 == 稳态列数，不存在「1 列挂载 N 行 → 收敛重建」的收敛帧（ADR §3.4 判据①）。
        Signal<Integer> effectiveColumns =
                Signal.create(Integer.valueOf(Math.max(1,
                        initialMetrics != null ? initialMetrics.columns() : props.columns())));
        if (initialMetrics != null) {
            rt.bind(metricsSignal, m -> Effect.untrack(
                    () -> setIfChanged(effectiveColumns, Math.max(1, m.columns()))));
        } else if (props.columns() <= 0) {
            ReadableSignal<Integer> widthBudget = props.availableWidth();
            if (widthBudget != null) {
                rt.bindComputed(() -> {
                    Integer budget = widthBudget.get();
                    int innerWidth = budget == null ? 0 : budget.intValue();
                    return Integer.valueOf(SceneVirtualGridNav.deriveColumns(innerWidth,
                            props.cellWidth(), props.gapX()));
                }, derived -> {
                    if (derived.intValue() != effectiveColumns.get().intValue()) {
                        effectiveColumns.set(derived);
                    }
                });
            } else {
                rt.bind(rt.layoutDoneSignal(), epoch -> Effect.untrack(() -> {
                    Object cached = viewport.getCachedLayout();
                    if (!(cached instanceof LayoutBox)) {
                        return;
                    }
                    int innerWidth = ((LayoutBox) cached).getWidth();
                    int derived = SceneVirtualGridNav.deriveColumns(innerWidth,
                            props.cellWidth(), props.gapX());
                    if (derived != effectiveColumns.get().intValue()) {
                        effectiveColumns.set(Integer.valueOf(derived));
                    }
                }));
            }
        }

        // 结构：viewport = [content]，content = [topSpacer, rowsContainer, bottomSpacer]。
        // content 三子高之和恒等于 totalRows*stride（spacer 数学），因此
        // SceneGeometry.maxScrollY(viewport) 与 windowModel.maxScrollPx 闭式同源 —— 滚动条零改动。
        // viewport 底座外观归 SceneScrollContainer 默认路径的 SceneSurfaceBinder（GROUP 配方）独占，
        // 本控件不二次绑定、不给行/单元各装滤镜。
        SceneNode content = sc.content();
        // 内容层不参与命中：命中必须落到单元（行层同样 hitTestable=false），
        // 否则高度巨大的 content 会吞掉全部指针事件（窗口化前的 content 即 rowsContainer，同款设置）。
        content.setHitTestable(false);
        SceneNode topSpacer = new SceneNode();
        SceneNode rowsContainer = SceneNode.column();
        rowsContainer.setGap(0);
        rowsContainer.setHitTestable(false);
        SceneNode bottomSpacer = new SceneNode();
        content.appendChild(topSpacer);
        content.appendChild(rowsContainer);
        content.appendChild(bottomSpacer);

        // 主题语义色派生：构建期（来源作用域 Owner 内）解析一次，全部单元共享；
        // 主题切换只重派生（Computed 按值记忆化），不重建节点。
        CellPalette palette = new CellPalette(rt);

        // 渲染分级回退：订阅注册表，不可渲染项写入 unrenderableKeys → 单元回退占位样式（共享装配）。
        // 键空间 = 候选域键（选择器图标源覆写 registryKey() 的返回值）：本列表项 key 即候选 key，
        // 与 PickerIconKey.candidate(key) 同值 ⇒ 恒等映射，无需拆键（拆键是历史缺陷，已删除）。
        Signal<Set<Object>> unrenderableKeys = ItemRenderFallbackKeys.track(registryKey -> registryKey);

        // 窗口模型 + 窗口切片：一个 Computed 派生「窗口模型 + 同源快照（items + index）」，
        // 全量数据源时在内部切片，pageProvider 形态时向宿主拉取切片（offset 由控件产出）。
        ReadableSignal<WindowData> window = Computed.create(() -> {
            List<SceneVirtualGrid.Item> source = safeItems(props.items());
            // 总量解析顺序：动态信号（P4）> 静态字段 > items.size()（T-2 全量形态）。
            // 动态信号是窗口 Computed 的依赖 ⇒ 后端查询总量变化即重派生 totalRows/maxScrollPx 并重拉切片。
            ReadableSignal<Integer> totalSignal = props.totalItemsSignal();
            int totalItems;
            if (totalSignal != null) {
                Integer dynamic = totalSignal.get();
                totalItems = dynamic == null ? 0 : Math.max(0, dynamic.intValue());
            } else {
                totalItems = props.totalItems() >= 0 ? props.totalItems() : source.size();
            }
            int columns = Math.max(1, effectiveColumns.get().intValue());
            int stride = Math.max(1, stridePx.get().intValue());
            int viewportH = viewportHeightPx.get().intValue();
            int actualRows = SceneGridWindow.visibleRowsForViewport(viewportH, stride);
            int visibleRows = actualRows > 0 ? actualRows : Math.max(1, props.visibleRows());
            int mathViewportH = actualRows > 0
                    ? viewportH
                    : SceneGridWindow.viewportHeight(visibleRows, trackHeight.get().intValue(),
                            props.gapY());
            SceneGridWindow.WindowModel model = SceneGridWindow.compute(totalItems, columns,
                    visibleRows, OVERSCAN_ROWS, stride, scrollSignal.get().intValue(), mathViewportH);

            int declaredOffset = Math.max(0, props.windowOffset());
            if (declaredOffset > 0 && declaredOffset != model.windowOffset()) {
                throw new IllegalStateException("inv-W1: Props.windowOffset(" + declaredOffset
                        + ") 必须等于 windowStartRow*columns(" + model.windowOffset()
                        + ")；宿主不得自算窗口偏移（ADR §3.2）");
            }
            List<SceneVirtualGrid.Item> slice;
            if (props.pageProvider() != null) {
                WindowPage page = props.pageProvider().page(new WindowRequest(model.windowOffset(),
                        model.mountedRows() * columns));
                slice = page == null ? Collections.<SceneVirtualGrid.Item>emptyList() : page.items();
            } else {
                slice = windowSlice(source, model.windowOffset(), model.mountedRows() * columns);
            }
            recordListModel(model.totalRows(), model.visibleRows());
            return new WindowData(model, SceneGridSnapshot.of(slice, model.windowOffset()));
        });
        ReadableSignal<SceneGridWindow.WindowModel> windowModelSignal =
                Computed.create(() -> window.get().model());

        // stride 变化（字号/密度令牌）→ 以「锚点行 + 行内偏移」重映射 scroll，视图不跳（P3 §3.3）。
        // 仅在 stride 值变化时执行（同值早退）；越界由回夹 effect 按新 maxScrollPx 处理。
        // 基线在首次 effect 执行时登记（不在构建期立即求值 Computed：Computed 惰性，构建期 get() 无值）。
        final int[] lastStride = { -1 };
        rt.bind(stridePx, stride -> Effect.untrack(() -> {
            int next = Math.max(1, stride == null ? 1 : stride.intValue());
            int previous = lastStride[0];
            if (previous < 0) {
                lastStride[0] = next;
                return;
            }
            if (next == previous) {
                return;
            }
            // 锚点行由「当前 scroll ÷ 旧 stride」反推：stride 变化会先让窗口重算，
            // 此时 windowModel 已是新 stride 的窗口，直接读它会把锚点行算错。
            int scrollNow = Math.max(0, scrollSignal.get().intValue());
            int oldStride = Math.max(1, previous);
            int anchorRow = scrollNow / oldStride;
            int intra = scrollNow - anchorRow * oldStride;
            lastStride[0] = next;
            int remapped = SceneGridWindow.scrollForAnchor(anchorRow, intra, next);
            if (remapped != scrollSignal.get().intValue()) {
                scrollSignal.set(Integer.valueOf(remapped));
            }
        }));

        // 列数变化（窗口/密度）→ 以「条目下标」为锚点重映射，当前首项不跳；同样只在值变化时执行。
        final int[] lastColumns = { effectiveColumns.get().intValue() };
        rt.bind(effectiveColumns, columns -> Effect.untrack(() -> {
            int next = Math.max(1, columns.intValue());
            int previous = Math.max(1, lastColumns[0]);
            if (next == previous) {
                return;
            }
            // 同理用「当前 scroll ÷ stride」反推旧首行与条目锚点（windowModel 已按新列数重算）。
            int stride = Math.max(1, stridePx.get().intValue());
            int scrollNow = Math.max(0, scrollSignal.get().intValue());
            int oldRow = scrollNow / stride;
            int anchorIndex = oldRow * previous;
            int intra = scrollNow - oldRow * stride;
            lastColumns[0] = next;
            int remapped = (anchorIndex / next) * stride + Math.min(intra, stride - 1);
            if (remapped != scrollSignal.get().intValue()) {
                scrollSignal.set(Integer.valueOf(remapped));
            }
        }));

        // 垫片高度：spacer 高之和 + 挂载行高 = totalRows*stride（内容总高守恒）。
        rt.bindComputed(() -> Integer.valueOf(window.get().model().topSpacerPx()),
                topSpacer::setPreferredHeight);
        rt.bindComputed(() -> Integer.valueOf(window.get().model().bottomSpacerPx()),
                bottomSpacer::setPreferredHeight);
        // 滚动回夹：数据收缩 / 视口变化后把 scroll 夹回窗口模型给出的 maxScrollPx（与 maxScrollY 同源）。
        rt.bindComputed(() -> Integer.valueOf(Math.max(0, Math.min(
                window.get().model().maxScrollPx(), scrollSignal.get().intValue()))), clamped -> {
            if (!clamped.equals(scrollSignal.get())) {
                scrollSignal.set(clamped);
            }
        });

        // G4（P5 §5.7）：滚动停止后行不被拦腰裁切 —— 滚轮/拖动结束把偏移吸附到 stride 整数倍。
        // 只在 SCROLL 事件上吸附（键盘导航的居中定位、外部高亮滚动不受影响：ADR A-10 的
        // 「定位语义 = 居中」保持原样）；吸附目标超过 maxScrollPx 时取 maxScrollPx（保证
        // 可滚到底、末项完整可见，G1 不被破坏）。
        // 滚轮/拖动只置「待吸附」标志：框架的滚轮 handler 与我们在同一节点上，注册顺序不保证，
        // 直接读偏移可能取到更新前的值（实测 120px 步长吸附失败）。改为在下一帧布局完成时吸附，
        // 既拿到最终偏移，也天然合并连续滚动（≤1 帧内偏移未对齐，肉眼不可见）。
        final boolean[] snapPending = { false };
        rt.on(viewport, SceneEventType.SCROLL, (ev, ctx) -> {
            snapPending[0] = true;
        });
        rt.on(viewport, SceneEventType.POINTER_UP, (ev, ctx) -> {
            snapPending[0] = true;
        });
        rt.bind(rt.layoutDoneSignal(), epoch -> Effect.untrack(() -> {
            if (!snapPending[0]) {
                return;
            }
            snapPending[0] = false;
            int stride = Math.max(1, stridePx.get().intValue());
            int max = window.get().model().maxScrollPx();
            int current = scrollSignal.get().intValue();
            int snapped = (int) Math.round((double) current / stride) * stride;
            if (current > max - Math.max(1, stride / 2)) {
                // 底部锚定：已贴底时取 maxScrollPx，吸附不得牺牲「可滚到底 / 末项完整可见」（G1）。
                snapped = max;
            } else if (snapped > max) {
                snapped = max;
            }
            if (snapped != current) {
                scrollSignal.set(Integer.valueOf(snapped));
            }
        }));

        // 控件级 hoveredKey：单元 hover 写 key、单元卸载时清同 key（虚拟化下「hover 移出」事件可能随节点
        // 卸载一起消失），onHoverItem 由该 key 经索引 O(1) 解析 → 信息条不残留已卸载/已换代的项。
        Signal<Object> hoveredKey = Signal.<Object>create(null);
        if (props.onHoverItem() != null) {
            rt.bind(hoveredKey, key -> Effect.untrack(() -> {
                SceneVirtualGrid.Item hovered = key == null
                        ? null : window.get().snapshot().index().itemAt(key);
                props.onHoverItem().accept(hovered);
            }));
        }

        // 行模型 = 窗口行区间（行键 = 首项全局下标；前插/删除时整窗重建成本 ≤ 可见行数）。
        ReadableSignal<List<SceneGridWindow.RowRange>> rowsSignal =
                Computed.create(() -> window.get().model().rows());
        rt.forEach(rowsContainer, rowsSignal, SceneGridWindow.RowRange::firstIndex,
                row -> rowComponent(rt, props, row, window, unrenderableKeys, palette,
                        trackHeight, stridePx, hoveredKey,
                        new CellGeometry(cellPadding, labelGap, iconSide, cellGap, cellWidth)));

        rt.on(viewport, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())
                    || ev.getKeyAction() != SceneKeyAction.PRESSED || ev.isRepeat()) {
                return;
            }
            SceneKey key = ev.getKey();
            WindowData data = window.get();
            SceneGridWindow.WindowModel model = data.model();
            int current = props.highlighted().get().intValue();
            int columns = Math.max(1, model.columns());

            // ENTER 激活当前高亮项：按全局下标从当前快照取项（O(1)）。
            // 高亮不在窗口内时不激活——外部/键盘高亮变化已触发居中滚动，下一帧即进入窗口。
            if (key == SceneKey.ENTER) {
                SceneVirtualGrid.Item item = data.snapshot().index().itemAtGlobal(current);
                if (item != null) {
                    ctx.stopPropagation();
                    props.onActivate().accept(item);
                }
                return;
            }

            // PAGE_UP / PAGE_DOWN：整页翻（可视行数 × 列数）；HOME / END：首项 / 末项
            // （P5 §5.3 C2：SceneKey 已定义、网格此前未消费）。无高亮时 PAGE_DOWN 进首项、
            // PAGE_UP 进末项（与 ARROW 的「无高亮进 0」语义一致）。
            if (key == SceneKey.HOME || key == SceneKey.END
                    || key == SceneKey.PAGE_UP || key == SceneKey.PAGE_DOWN) {
                int total = model.totalItems();
                if (total <= 0) {
                    return;
                }
                int target;
                if (key == SceneKey.HOME) {
                    target = 0;
                } else if (key == SceneKey.END) {
                    target = total - 1;
                } else {
                    int page = Math.max(1, model.visibleRows()) * columns;
                    boolean down = key == SceneKey.PAGE_DOWN;
                    target = current < 0 ? (down ? 0 : total - 1)
                            : (down ? current + page : current - page);
                }
                target = Math.max(0, Math.min(total - 1, target));
                if (target == current) {
                    return;
                }
                ctx.stopPropagation();
                props.onHighlightChange().accept(Integer.valueOf(target));
                scrollToRow(target / columns, model, scrollSignal, stridePx, viewportHeightPx);
                return;
            }

            // ARROW_UP 在首行：退回上层控件（P5 §5.3 C3「网格中按 ↑ 回搜索框」）。
            // 只在「无高亮或首行」时触发，避免打断网格内正常的跨行导航。
            if (key == SceneKey.ARROW_UP && props.onExitUp() != null
                    && (current < 0 || current < columns)) {
                ctx.stopPropagation();
                props.onExitUp().run();
                return;
            }

            // ARROW_* 四向导航（全局项范围内，语义由 SceneVirtualGridNav 锚定）
            int next = SceneVirtualGridNav.navigate(current, key, columns, model.totalItems());
            if (next < 0 || next == current) {
                return;
            }
            ctx.stopPropagation();
            props.onHighlightChange().accept(Integer.valueOf(next));

            // 自动滚动到目标行（保持既定居中语义）：target = row*stride - 视口高/2，clamp 到 maxScrollPx
            scrollToRow(next / columns, model, scrollSignal, stridePx, viewportHeightPx);
        });

        // 外部高亮变化 → 居中滚动（ADR §3.8 #5）。仅高亮值变化时执行：同值早退 + 回调内不追加上游依赖，
        // 用户手动滚动不会被"重新居中"打断（内部滚动只改 scrollSignal，高亮值不变）。
        final int[] lastCenteredHighlight = { Integer.MIN_VALUE };
        rt.bind(props.highlighted(), highlighted -> Effect.untrack(() -> {
            int index = highlighted == null ? -1 : highlighted.intValue();
            if (index == lastCenteredHighlight[0]) {
                return;
            }
            lastCenteredHighlight[0] = index;
            SceneGridWindow.WindowModel model = window.get().model();
            if (index < 0 || model.totalItems() <= 0) {
                return;
            }
            int columns = Math.max(1, model.columns());
            int row = Math.min(index, model.totalItems() - 1) / columns;
            scrollToRow(row, model, scrollSignal, stridePx, viewportHeightPx);
        }));

        // D3（P5 §5.4）：键盘高亮项观察面 —— 与 hover 不同源，信息条据此在无指针悬停时
        // 也能显示「当前高亮项」的 label/ID（O(1)：按全局下标从同源快照取项）。
        ReadableSignal<SceneVirtualGrid.Item> highlightedItem = Computed.create(() -> {
            int index = props.highlighted().get().intValue();
            if (index < 0) {
                return null;
            }
            return window.get().snapshot().index().itemAtGlobal(index);
        });

        return new Result(stackHost, viewport, windowModelSignal, highlightedItem);
    }

    /** 滚动居中到目标行：{@code row*stride - 视口高/2}，clamp 到窗口模型的 maxScrollPx。 */
    private static void scrollToRow(int row, SceneGridWindow.WindowModel model,
                                    Signal<Integer> scrollSignal, ReadableSignal<Integer> stridePx,
                                    ReadableSignal<Integer> viewportHeightPx) {
        int stride = Math.max(1, stridePx.get().intValue());
        int target = Math.max(0, row) * stride - Math.max(0, viewportHeightPx.get().intValue()) / 2;
        int clamped = Math.max(0, Math.min(model.maxScrollPx(), target));
        if (clamped != scrollSignal.get().intValue()) {
            scrollSignal.set(Integer.valueOf(clamped));
        }
    }

    /** 一次窗口求值的完整产物：窗口模型 + 同源快照（items 与 index 来自同一份切片）。 */
    @Desugar
    private record WindowData(SceneGridWindow.WindowModel model, SceneGridSnapshot snapshot) {
    }

    /**
     * 单元几何信号组 —— {@code GridMetrics} 单一快照在单元层的五项投影。
     *
     * <p>五个信号由 {@code create()} 从同一份 {@link GridMetrics} 初始化并随之整体重写；
     * 单元/行只订阅它们，<b>不得</b>再读 {@code Props} 的静态几何（那是第二个真值来源，I-4）。</p>
     *
     * @param paddingPx  单元内边距
     * @param labelGapPx 图标与标签间距
     * @param iconSidePx 图位边长（{@code 0} = 旧路径，图位吃轨道剩余空间）
     * @param gapPx      行/列间距（同一个值）
     * @param cellWidthPx 单元宽
     */
    @Desugar
    private record CellGeometry(ReadableSignal<Integer> paddingPx, ReadableSignal<Integer> labelGapPx,
                                ReadableSignal<Integer> iconSidePx, ReadableSignal<Integer> gapPx,
                                ReadableSignal<Integer> cellWidthPx) {
    }

    /** 同值早退写信号（几何信号全部走这里，避免同值 set 触发无谓重算）。 */
    private static void setIfChanged(Signal<Integer> signal, int value) {
        if (signal.get().intValue() != value) {
            signal.set(Integer.valueOf(value));
        }
    }

    /** 从全量数据源截取窗口切片（入参为全局坐标）。 */
    private static List<SceneVirtualGrid.Item> windowSlice(List<SceneVirtualGrid.Item> source,
                                                           int from, int count) {
        int start = Math.max(0, Math.min(from, source.size()));
        int end = Math.max(start, Math.min(source.size(), start + Math.max(0, count)));
        return start >= end
                ? Collections.<SceneVirtualGrid.Item>emptyList()
                : new ArrayList<SceneVirtualGrid.Item>(source.subList(start, end));
    }

    /**
     * 构建一个窗口行（ROW 容器，行高钉定，行间距经 marginBottom 计入主轴占位）。
     *
     * <p>行键 = 首项全局下标（v1 保守策略，与 {@link SceneVirtualGrid} 一致）；行内容按
     * 「全局下标 - windowOffset」映射到窗口切片的局部区间，保证行数据与索引同源。</p>
     */
    private static SceneNode rowComponent(SceneRuntime rt, Props props,
                                          SceneGridWindow.RowRange row,
                                          ReadableSignal<WindowData> window,
                                          ReadableSignal<Set<Object>> unrenderableKeys,
                                          CellPalette palette,
                                          ReadableSignal<Integer> trackHeight,
                                          ReadableSignal<Integer> stridePx,
                                          Signal<Object> hoveredKey,
                                          CellGeometry geometry) {
        SceneNode rowNode = SceneNode.row();
        rowNode.setPreferredHeight(trackHeight.get().intValue());
        // 轨道高随生效字号变 → 行高跟着变；spacer 数学读同一 stride 信号，内容总高守恒。
        rt.bind(trackHeight, height -> rowNode.setPreferredHeight(height.intValue()));
        // 行间距与列间距同源（I-4）：两者都取度量快照的 gap，绝不再读 Props 的静态值，
        // 否则 stride 用新 gap、行 margin 用旧 gap，内容总高与 maxScrollPx 立即漂移。
        rt.bind(geometry.gapPx(), gap -> Effect.untrack(() -> {
            rowNode.setMargin(0, 0, gap.intValue(), 0);
            rowNode.setGap(gap.intValue());
        }));
        rowNode.setMargin(0, 0, geometry.gapPx().get().intValue(), 0);
        rowNode.setGap(geometry.gapPx().get().intValue());
        rowNode.setHitTestable(false);
        // 行节点按首项全局下标复用后，行内容仍须从实时窗口快照派生（避免复用行吃到陈旧切片）。
        ReadableSignal<List<SceneVirtualGrid.Item>> rowItems = Computed.create(() -> {
            WindowData data = window.get();
            int localStart = row.firstIndex() - data.model().windowOffset();
            if (localStart < 0 || localStart >= data.snapshot().size()) {
                return Collections.<SceneVirtualGrid.Item>emptyList();
            }
            // 行宽读**当前**窗口模型的列数，而不是构建期捕获的 RowRange.count：
            // 行按首项下标复用（keyed reconcile），列数收敛（1 → N）时复用行必须跟着变宽。
            int count = Math.min(Math.max(1, data.model().columns()),
                    data.snapshot().size() - localStart);
            return count <= 0
                    ? Collections.<SceneVirtualGrid.Item>emptyList()
                    : new ArrayList<SceneVirtualGrid.Item>(
                            data.snapshot().items().subList(localStart, localStart + count));
        });
        rt.forEach(rowNode, rowItems, SceneVirtualGrid.Item::key,
                item -> cellComponent(rt, props, item, window, unrenderableKeys, palette,
                        trackHeight, stridePx, hoveredKey, geometry));
        recordMountedRow();
        return rowNode;
    }

    /**
     * 图位剩余高 = 单元轨道高 - 上下内边距 - （有标签时）标签行高 + 标签间距；至少 1px。
     *
     * <p><b>为什么必须用生效字号</b>：标签行高随字号变（用户倍率/作用域声明同样参与），
     * 用构建期常量 {@code LABEL_FONT_SIZE} 算出的图位会在字号变大后与标签重叠；
     * 图位是单元的「剩余空间」承担者，标签行高变多少、图位就减多少。</p>
     *
     * <p>轨道高由 {@code setFontSizeMetric} 随字号重算并写入 {@code trackHeight} 信号，
     * 本方法只订阅该信号（不再手写 layoutDone 重算 effect）。</p>
     *
     * @param rt          场景运行时（提供行高度量）
     * @param icon        图位节点（写 preferredHeight）
     * @param label       标签节点；null = 该项无标签
     * @param trackHeight 单元轨道高信号（随生效字号重算）
     */
    /** 图位高 = 轨道高 - 2*内边距 -（有标签时）标签行高 + 间距；至少 1px（旧路径口径）。 */
    private static int iconHeightFor(SceneRuntime rt, int trackHeightPx, SceneNode label,
                                     int paddingPx, int labelGapPx) {
        int available = trackHeightPx - paddingPx * 2;
        if (label != null) {
            available -= rt.lineHeight(label.effectiveFontSize()) + labelGapPx;
        }
        return Math.max(1, available);
    }

    /**
     * 轨道高下限：调用方 cellHeight 与「图位最小 + 标签行高 + 间距 + 内边距」取大。
     *
     * <p>派生唯一落在 {@link GridMetrics}：字号 / 密度令牌 / 内边距口径的换源只改那一处
     * （P5 §2.1 派生链；P5 令牌落地前取标准档常量）。</p>
     */
    private static int minTrackHeightFor(SceneRuntime rt, Props props, int fontSizePx) {
        return GridMetrics.derive(rt, fontSizePx, props.cellHeight(), CELL_PADDING, LABEL_GAP,
                props.gapY()).trackHeightPx();
    }
    /** 构建单个结果单元（结构复刻 SceneVirtualGrid.cellComponent；外观为主题轻量覆盖）。 */
    private static SceneNode cellComponent(SceneRuntime rt, Props props, SceneVirtualGrid.Item item,
                                           ReadableSignal<WindowData> window,
                                           ReadableSignal<Set<Object>> unrenderableKeys,
                                           CellPalette palette,
                                           ReadableSignal<Integer> trackHeight,
                                           ReadableSignal<Integer> stridePx,
                                           Signal<Object> hoveredKey,
                                           CellGeometry geometry) {
        long startedAtNanos = Config.useDebug ? System.nanoTime() : 0L;
        SceneNode cell = SceneNode.column();
        cell.setPreferredWidth(geometry.cellWidthPx().get().intValue());
        rt.bind(geometry.cellWidthPx(), w -> cell.setPreferredWidth(w.intValue()));
        cell.setPreferredHeight(trackHeight.get().intValue());
        rt.bind(trackHeight, h -> cell.setPreferredHeight(h.intValue()));
        cell.setClipChildren(true);
        cell.setGap(geometry.labelGapPx().get().intValue());
        rt.bind(geometry.labelGapPx(), g -> cell.setGap(g.intValue()));
        cell.setPadding(geometry.paddingPx().get().intValue());
        rt.bind(geometry.paddingPx(), p -> cell.setPadding(p.intValue()));
        // 轻量覆盖口径：单元不写圆角/边框（外观写入槽只剩 backgroundColor），不装滤镜。
        SceneInteractionState interaction = rt.interactionState(cell);
        // 时序契约：构建期声明关心 hovered，Router 后续写入才会落到已创建的 signal。
        interaction.hovered();
        // 选中态：item 在完整 items 列表中的下标 == highlighted（按 key 动态派生，
        // 复用/重绑单元不携带旧项选中态）。
        ReadableSignal<Boolean> selected = Computed.create(() ->
                Integer.valueOf(window.get().snapshot().index().indexOf(item.key()))
                        .equals(props.highlighted().get()));
        rt.__bindAnimatedColor(() -> resolveCellBackground(
                        Boolean.TRUE.equals(props.enabled().get()),
                        Boolean.TRUE.equals(selected.get()),
                        Boolean.TRUE.equals(interaction.hovered().get()),
                        palette.accent.get(), palette.selectionBackground.get()),
                cell::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);

        // 「已配置」标记（T5 UX-18 / P4 偏差 D-P4-3）：SPI 路径不再排除已在当前规则中的候选，
        // 因此该状态必须在结果单元上可区分 —— 命中键的单元在标签行右端挂一颗主题强调色圆点
        // （无文字、不引 i18n、不额外占行）；完整语义由信息条文案承担（见 ScenePickerPanel）。
        final ReadableSignal<Boolean> configured = Computed.create(() -> {
            ReadableSignal<Set<String>> keysSignal = props.configuredKeys();
            if (keysSignal == null) {
                return Boolean.FALSE;
            }
            Set<String> keys = keysSignal.get();
            return Boolean.valueOf(keys != null && keys.contains(String.valueOf(item.key())));
        });
        // 标记边长与内边距同源（同一个 GridMetrics 快照的 paddingPx 派生），不引入第二个尺寸真值。
        final ReadableSignal<Integer> markerSide = Computed.create(() -> Integer.valueOf(
                Math.max(MARKER_MIN_PX, Math.min(MARKER_MAX_PX,
                        geometry.paddingPx().get().intValue()))));

        // 标签先建：图位剩余高要按「标签生效字号的行高」扣减，必须先拿到标签节点。
        SceneNode label = null;
        if (item.label() != null) {
            label = new SceneNode();
            label.setHitTestable(false);
            // 私有常量降级为层 4a 回落值（有声明时跟随作用域，无声明时仍落 12）。
            label.setFallbackFontSize(LABEL_FONT_SIZE);
            // 行文字取主题次要前景（旧 TEXT_SECONDARY 同值起步），禁用取禁用前景；
            // 选中区分由底色承担，不靠文字变色（SceneNavList G09 口径）。
            label.setTextHorizontalAlign(TextHorizontalAlign.CENTER);
            label.setText(item.label());
            rt.bindComputed(() -> Boolean.TRUE.equals(props.enabled().get())
                    ? palette.mutedForeground.get() : palette.disabledForeground.get(),
                    label::setTextColor);
            // 溢出策略（INV-GEO-4）：单元轨道由虚拟化 stride 固定，文字超宽必须可见省略，
            // 否则被 cell 的 clipChildren(true) 静默裁掉。
            rt.bindComputed(() -> Integer.valueOf(Math.max(1,
                            geometry.cellWidthPx().get().intValue()
                                    - 2 * geometry.paddingPx().get().intValue())),
                    label::setMaxTextWidth);
            label.setMaxLines(1);
            label.setEllipsis(true);
        }

        // 图位高派生的 lambda 需要 effectively-final 引用（label 在分支里赋值，不能直接捕获）。
        final SceneNode labelRef = label;
        SceneNode icon = new SceneNode();
        icon.setHitTestable(false);
        // I-1 图标正方形：P5 派生路径下图位是<B>正方形</B>且边长 = density.icon × k，
        // 不再用 max(cellWidth - 2*pad) × (轨道剩余高) 的矩形让 ITEM_ICON 走 min(w,h) 兜底
        // （那正是现状「图位 56x39 → 图标实际 39」的成因，T5 UX-15）。
        // 旧路径（iconSidePx == 0）保持原「图位吃剩余空间」语义，兼容旧调用方。
        rt.bindComputed(() -> {
            int side = geometry.iconSidePx().get().intValue();
            return Integer.valueOf(side > 0 ? side
                    : Math.max(1, geometry.cellWidthPx().get().intValue()
                            - 2 * geometry.paddingPx().get().intValue()));
        }, icon::setPreferredWidth);
        rt.bindComputed(() -> {
            int side = geometry.iconSidePx().get().intValue();
            if (side > 0) {
                return Integer.valueOf(side);
            }
            return Integer.valueOf(iconHeightFor(rt, trackHeight.get().intValue(), labelRef,
                    geometry.paddingPx().get().intValue(),
                    geometry.labelGapPx().get().intValue()));
        }, icon::setPreferredHeight);
        icon.setCornerRadius(SceneChromeTokens.RADIUS_SM);
        // 生效图标：不可渲染项回退占位底色（null 图片），其余从实时数据源派生（含渲染分级变化）。
        ReadableSignal<SceneImageSource> effectiveImage = Computed.create(() -> {
            if (unrenderableKeys.get().contains(item.key())) {
                return null;
            }
            // 索引 O(1) 取当前快照项：单元节点按 key 复用（每 key 只建一次），
            // 构建期捕获的 item 实例可能是旧代 ⇒ 图标源按快照实时解析，不读陈旧快照。
            SceneVirtualGrid.Item live = window.get().snapshot().index().itemAt(item.key());
            return live == null ? item.image() : live.image();
        });
        // 两态可区分（ADR A-19）：UNRENDERABLE = 主题派生状态色；「无图」= 静态协议占位色。
        ReadableSignal<Boolean> unrenderable = Computed.create(() ->
                Boolean.valueOf(unrenderableKeys.get().contains(item.key())));
        ReadableSignal<Integer> unrenderableTint = ItemRenderFallbackKeys.unrenderableTint(rt);
        rt.bindComputed(() -> {
            if (Boolean.TRUE.equals(unrenderable.get())) {
                return unrenderableTint.get();
            }
            return effectiveImage.get() == null
                    ? Integer.valueOf(DEFAULT_PLACEHOLDER_COLOR)
                    : Integer.valueOf(SceneRenderProtocolTokens.TRANSPARENT_ARGB);
        }, icon::setBackgroundColor);
        rt.bind(effectiveImage, icon::setImageSource);
        // 「已配置」圆点挂在**图位右上角**（不是新的一行/一列）：单元结构保持 [icon, label]，
        // 既不占标签行宽度、也不动轨道高与既有取节点路径；未命中时宽高与 margin 全 0（零占位）。
        SceneNode marker = SceneNode.column();
        marker.setHitTestable(false);
        marker.setCornerRadius(SceneChromeTokens.RADIUS_PILL);
        rt.bind(palette.accent, marker::setBackgroundColor);
        rt.bind(configured, isConfigured -> Effect.untrack(() -> {
            int side = Boolean.TRUE.equals(isConfigured) ? markerSide.get().intValue() : 0;
            marker.setPreferredWidth(side);
            marker.setPreferredHeight(side);
            marker.setMargin(Boolean.TRUE.equals(isConfigured) ? MARKER_INSET_PX : 0,
                    Boolean.TRUE.equals(isConfigured) ? MARKER_INSET_PX : 0, 0, 0);
        }));
        rt.bind(markerSide, side -> Effect.untrack(() -> {
            if (Boolean.TRUE.equals(configured.get())) {
                marker.setPreferredWidth(side.intValue());
                marker.setPreferredHeight(side.intValue());
            }
        }));
        icon.setMainAxisAlign(MainAxisAlign.END);
        icon.setCrossAxisAlign(CrossAxisAlign.START);
        icon.appendChild(marker);
        cell.appendChild(icon);
        if (label != null) {
            cell.appendChild(label);
        }

        rt.on(cell, SceneEventType.CLICK, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            ctx.stopPropagation();
            props.onActivate().accept(item);
            int index = window.get().snapshot().index().indexOf(item.key());
            if (index >= 0) {
                props.onHighlightChange().accept(Integer.valueOf(index));
            }
        });

        // hover：写控件级 hoveredKey（不直接回调）——回调由 key 统一解析，且卸载时能清同 key。
        if (props.onHoverItem() != null) {
            rt.bind(interaction.hovered(), hovered -> {
                if (Boolean.TRUE.equals(hovered)) {
                    hoveredKey.set(item.key());
                } else if (item.key().equals(hoveredKey.get())) {
                    hoveredKey.set(null);
                }
            });
            // 单元卸载（滚动窗口滑动 / 数据换代）时不再有「hover 移出」事件：显式清同 key 防信息条残留。
            Owner owner = Owner.current();
            if (owner != null) {
                owner.onCleanup(() -> {
                    if (item.key().equals(hoveredKey.get())) {
                        hoveredKey.set(null);
                    }
                });
            }
        }

        recordCellMount(startedAtNanos);
        return cell;
    }

    /**
     * 解析结果单元底色：禁用清空 &gt; 选中（主题选区背景 0x59）&gt; hover（主题 accent 0x1F）
     * &gt; 全透明（露出底座玻璃）。
     *
     * <p>轻量状态覆盖口径与 {@code SceneAutocomplete} 候选行一致：只做半透明染色，不装滤镜；
     * 选中强度明显高于 hover，保证选中不只靠透明度与 hover 混淆。禁用时底色清空，
     * 禁用反馈由文字禁用前景与点击/键盘守卫承担，不伪造可用选中态。</p>
     *
     * @param enabled             是否启用
     * @param selected            是否选中（高亮）
     * @param hovered             是否指针悬停
     * @param accent              主题强调色
     * @param selectionBackground 主题选区背景色
     * @return 单元背景色 ARGB
     */
    private static int resolveCellBackground(boolean enabled, boolean selected, boolean hovered,
                                             int accent, int selectionBackground) {
        if (!enabled) {
            return CELL_BG_TRANSPARENT;
        }
        if (selected) {
            return tint(selectionBackground, CELL_SELECTED_ALPHA);
        }
        if (hovered) {
            return tint(accent, CELL_HOVER_ALPHA);
        }
        return CELL_BG_TRANSPARENT;
    }

    /**
     * 保留色 RGB、替换 alpha 通道（轻量覆盖用）。
     *
     * @param argb  源色
     * @param alpha 目标 alpha（0..255）
     * @return 替换 alpha 后的 ARGB
     */
    private static int tint(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** 单元共享的主题语义色派生集合（构建期在来源作用域内解析一次，主题切换只重派生）。 */
    private static final class CellPalette {
        /** 主题强调色（hover 轻量覆盖源）。 */
        private final ReadableSignal<Integer> accent;
        /** 主题选区背景（选中轻量覆盖源）。 */
        private final ReadableSignal<Integer> selectionBackground;
        /** 主题次要前景（单元标签默认文字色）。 */
        private final ReadableSignal<Integer> mutedForeground;
        /** 主题禁用前景（禁用态单元标签文字色）。 */
        private final ReadableSignal<Integer> disabledForeground;

        /**
         * 在来源作用域内解析四个语义色派生。
         *
         * @param rt 场景运行时
         */
        private CellPalette(SceneRuntime rt) {
            this.accent = SceneThemes.accent(rt);
            this.selectionBackground = SceneThemes.selectionBackground(rt);
            this.mutedForeground = SceneThemes.mutedForeground(rt);
            this.disabledForeground = SceneThemes.disabledForeground(rt);
        }
    }

    /** 可视高度：优先读取已布局的 LayoutBox 高度，否则退回 preferredHeight。 */
    private static int visibleHeight(SceneNode vp) {
        Object cached = vp.getCachedLayout();
        if (cached instanceof LayoutBox) {
            int h = ((LayoutBox) cached).getHeight();
            if (h > 0) {
                return h;
            }
        }
        return Math.max(0, vp.getPreferredHeight());
    }

    private static List<SceneVirtualGrid.Item> safeItems(
            ReadableSignal<? extends List<SceneVirtualGrid.Item>> signal) {
        List<SceneVirtualGrid.Item> items = signal.get();
        return items == null ? Collections.<SceneVirtualGrid.Item>emptyList() : items;
    }

    // ==================== 采样埋点（只加观测，不改渲染与交互语义） ====================

    /**
     * 记录一次窗口派生的规模（ADR §6.2 口径冻结）。
     *
     * <p>{@code picker.list.totalRows} = 数据总行数（由 {@code Props.totalItems} 派生，**不是**挂载行数）；
     * {@code picker.list.visibleRows} = 由视口高度与 stride 派生的可视行数；挂载行数由
     * {@code picker.list.rows} 单独累计。三者即虚拟化比例的完整证据。</p>
     *
     * @param totalRows   数据总行数
     * @param visibleRows 生效可视行数
     */
    private static void recordListModel(int totalRows, int visibleRows) {
        if (!Config.useDebug) {
            return;
        }
        UiPerformanceMonitor monitor = UiPerformanceMonitor.getInstance();
        monitor.recordCounter(UiPerfMarkers.COUNTER_PICKER_TOTAL_ROWS, totalRows);
        monitor.recordCounter(UiPerfMarkers.COUNTER_PICKER_VISIBLE_ROWS, visibleRows);
    }

    /** 累计一个已挂载的结果行。 */
    private static void recordMountedRow() {
        if (!Config.useDebug) {
            return;
        }
        UiPerformanceMonitor.getInstance().recordCounter(UiPerfMarkers.COUNTER_PICKER_LIST_ROWS, 1L);
    }

    /** 累计一个单元的构建耗时与数量（startedAtNanos 为 0 表示采样关闭）。 */
    private static void recordCellMount(long startedAtNanos) {
        if (startedAtNanos == 0L) {
            return;
        }
        UiPerformanceMonitor monitor = UiPerformanceMonitor.getInstance();
        monitor.recordCounter(UiPerfMarkers.COUNTER_PICKER_LIST_CELLS, 1L);
        monitor.recordPhase(UiPerfMarkers.PHASE_PICKER_LIST_MOUNT, System.nanoTime() - startedAtNanos);
    }

}
