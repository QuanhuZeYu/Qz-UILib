package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextHorizontalAlign;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneVirtualGrid —— 虚拟化图标网格控件（创造物品栏式方块选择器的网格地基）。
 *
 * <h3>行级虚拟化</h3>
 * <p>只有可见窗口内的行实际挂载子节点（每行是完整网格行，含 overscan 一行避免滚动半行露白）：
 * viewport（scrollable + 固定 preferredHeight）内挂 {@code topSpacer + rowsContainer + bottomSpacer}，
 * spacer 高度由窗口数学推导，使布局引擎算出的内容总高恒等于 {@code totalRows * stride}，
 * {@code SceneGeometry.maxScrollY} 全程正确，{@link SceneScrolls} 的滚轮 clamp 无需特判。</p>
 *
 * <h3>滚动驱动窗口</h3>
 * <p>滚动唯一权威是 {@link SceneScrolls#attach} 返回的 scrollSignal（GEOMETRY 级滚动不重排）；
 * 窗口首行由 {@code floor(scroll / stride)} 派生（Computed），数据收缩时经 owner 作用域 effect
 * 把 scroll 回夹到新 maxScroll（参考旧版搜索选择器的 windowFor/bindScrollClamp 范式）。</p>
 *
 * <h3>高亮（受控/内部双形态）</h3>
 * <p>{@link Props#highlighted()} 非 null 时高亮完全受控（显示用外部 signal、导航经
 * {@link Props#onHighlightChange()} 回写）；null 时控件内部自管。点击与四向键盘导航都会推进高亮，
 * 高亮行自动滚动进入视野（越界换行/夹取语义见 {@link SceneVirtualGridNav}）。</p>
 *
 * <h3>外观归属（液态玻璃迁移，G14，契约 §4.1「VirtualGrid：GROUP（网格）」与 §4.1 复用行轻量口径）</h3>
 * <p><b>网格底座</b>：root 即滚动 viewport，按 G07 {@link SceneScrollContainer} 先例自持一颗主题
 * {@code GROUP} 配方表面——background/border/borderWidth/cornerRadius/backdrop/surfaceElevation
 * 六项由 {@link SceneSurfaceBinder} 独占（每颗表面只采样一次滤镜）；不再外包第二层容器、不给
 * 行/单元各装滤镜，避免叠层。</p>
 *
 * <p><b>复用单元轻量覆盖</b>（虚拟化复用行口径，G13 裁决）：单元（回收再绑数据的那层）外观写入槽
 * 只剩 {@code backgroundColor}——选中取主题选区背景（alpha {@code 0x59}）、hover 取主题 accent
 * （alpha {@code 0x1F}，与 {@code SceneAutocomplete}/{@code SearchResultList} 候选行同一口径，
 * 选中明显强于 hover、不只靠透明度区分）；禁用时底色清空（禁用反馈由文字禁用前景与点击/键盘守卫
 * 承担）。单元零 {@code BACKDROP}、零边框、零圆角、elevation 保持 -1（普通绘制）。选中与底色
 * 按 key 从实时数据源派生，回收/重绑节点不残留上一项的 hover/选中色。</p>
 *
 * <p><b>不改渲染协议</b>（契约 §4.1「物品图像不改色」+ §7.3）：图位圆角与占位底色
 * （{@link #DEFAULT_PLACEHOLDER_COLOR}）属图像渲染协议，保持静态值、不随主题重染；行标签文字取主题
 * {@code mutedForeground}/{@code disabledForeground}（旧 {@code TEXT_SECONDARY} 同值起步）。</p>
 */
public final class SceneVirtualGrid {

    /** 无图片项的占位底色（与旧版搜索选择器图标占位同色；属图像渲染协议，非主题槽位）。 */
    public static final int DEFAULT_PLACEHOLDER_COLOR = 0xFF454B54;

    private static final int LABEL_FONT_SIZE = 12;
    private static final int CELL_PADDING = 4;
    private static final int LABEL_GAP = 2;
    private static final int OVERSCAN_ROWS = 1;
    /** 恒真 enabled：网格底座自身没有禁用语义（禁用反馈由单元与输入守卫各自表达），与 G07 先例同口径。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;
    /** 单元默认底色：全透明，露出底座玻璃（单元不各自采样滤镜）。 */
    private static final int CELL_BG_TRANSPARENT = 0x00000000;
    /** 单元 hover 覆盖强度：主题 accent 的低透明度轻量覆盖。 */
    private static final int CELL_HOVER_ALPHA = 0x1F;
    /** 单元选中（高亮）覆盖强度：主题选区背景，明显强于 hover（不只靠透明度区分）。 */
    private static final int CELL_SELECTED_ALPHA = 0x59;

    private SceneVirtualGrid() {
    }

    /**
     * 网格项 —— 不可变快照（key + 平台中立图片源 + 标签）。
     *
     * @param key   稳定唯一键（跨列表变更保持身份，供 keyed reconcile 复用）
     * @param image 平台中立图片源，可为 null（占位底色）
     * @param label 标签文本，可为 null（纯图标单元）
     */
    @Desugar
    public record Item(Object key, SceneImageSource image, String label) {
        /**
         * 创建网格项。
         */
        public Item(Object key, SceneImageSource image, String label) {
            this.key = Objects.requireNonNull(key, "key");
            this.image = image;
            this.label = label;
        }
    }

    /**
     * 虚拟网格输入契约（不可变）。
     *
     * @param items             数据源（非 null；List 值变更经 keyed reconcile 最小重建）
     * @param columns           固定列数；&lt;=0 时按 cellWidth 与可用宽自动推算
     * @param cellWidth         单元宽（UI 像素，&gt;0）
     * @param cellHeight        单元高（UI 像素，&gt;0）
     * @param gapX              列间距（&gt;=0）
     * @param gapY              行间距（&gt;=0）
     * @param visibleRows       视口可见行数（&gt;=1，决定 viewport 高度）
     * @param enabled           是否启用（禁用时不响应点击/键盘）
     * @param onActivate        点击激活回调（非 null）
     * @param highlighted       受控高亮（可为 null = 内部自管）
     * @param onHighlightChange 受控模式导航回写（受控模式可为 null = 只读显示）
     * @param onCellMount       单元挂载回调（在单元 item 作用域内调用，随单元卸载回收；
     *                          可为 null = 不挂装饰。供宿主把 hover 浮层等生命周期绑定到单元）
     */
    @Desugar
    public record Props(
            ReadableSignal<? extends List<Item>> items,
            int columns,
            int cellWidth,
            int cellHeight,
            int gapX,
            int gapY,
            int visibleRows,
            ReadableSignal<Boolean> enabled,
            Consumer<Item> onActivate,
            ReadableSignal<Integer> highlighted,
            Consumer<Integer> onHighlightChange,
            BiConsumer<SceneNode, Item> onCellMount) {

        /**
         * 创建虚拟网格属性。
         */
        public Props(ReadableSignal<? extends List<Item>> items,
                     int columns,
                     int cellWidth,
                     int cellHeight,
                     int gapX,
                     int gapY,
                     int visibleRows,
                     ReadableSignal<Boolean> enabled,
                     Consumer<Item> onActivate,
                     ReadableSignal<Integer> highlighted,
                     Consumer<Integer> onHighlightChange,
                     BiConsumer<SceneNode, Item> onCellMount) {
            this.items = Objects.requireNonNull(items, "items");
            this.enabled = Objects.requireNonNull(enabled, "enabled");
            this.onActivate = Objects.requireNonNull(onActivate, "onActivate");
            if (cellWidth <= 0) {
                throw new IllegalArgumentException("cellWidth 必须 > 0");
            }
            if (cellHeight <= 0) {
                throw new IllegalArgumentException("cellHeight 必须 > 0");
            }
            if (visibleRows < 1) {
                throw new IllegalArgumentException("visibleRows 必须 >= 1");
            }
            if (gapX < 0 || gapY < 0) {
                throw new IllegalArgumentException("gap 不可为负数");
            }
            this.columns = columns;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.gapX = gapX;
            this.gapY = gapY;
            this.visibleRows = visibleRows;
            this.highlighted = highlighted;
            this.onHighlightChange = onHighlightChange;
            this.onCellMount = onCellMount;
        }

        /** 便捷工厂：内部自管高亮。 */
        public static Props of(ReadableSignal<? extends List<Item>> items, int columns,
                               int cellWidth, int cellHeight, int gapX, int gapY,
                               int visibleRows, ReadableSignal<Boolean> enabled,
                               Consumer<Item> onActivate) {
            return new Props(items, columns, cellWidth, cellHeight, gapX, gapY, visibleRows,
                    enabled, onActivate, null, null, null);
        }
    }

    /**
     * 可见窗口行（行级虚拟化挂载单元）。
     *
     * @param firstIndex 行首项在完整列表中的下标
     * @param items      该行完整单元快照（列数对齐后可能截尾）
     */
    @Desugar
    public record WindowRow(int firstIndex, List<Item> items) {
    }

    /**
     * 窗口模型（只读派生快照，供宿主/测试观察虚拟化状态）。
     *
     * @param columns        生效列数（固定或自动推算）
     * @param totalItems     数据项总数
     * @param totalRows      总行数
     * @param windowStartRow 可见窗口首行
     * @param mountedRows    实际挂载行数（含 overscan）
     * @param maxStartRow    最大窗口首行（数据收缩回夹上界）
     * @param maxScrollPx    最大滚动偏移（与 SceneGeometry.maxScrollY 口径一致）
     * @param rows           当前挂载窗口行快照
     */
    @Desugar
    public record WindowModel(
            int columns,
            int totalItems,
            int totalRows,
            int windowStartRow,
            int mountedRows,
            int maxStartRow,
            int maxScrollPx,
            List<WindowRow> rows) {
    }

    /**
     * 创建结果。
     *
     * @param root         控件根节点（即可滚动 viewport，挂到宿主布局树）
     * @param viewport     可滚动视口（与 root 同节点）
     * @param highlighted  生效高亮只读信号（受控模式为外部信号，否则内部信号）
     * @param scrollSignal 滚动偏移信号（滚动唯一权威，可编程滚动）
     * @param windowModel  窗口模型派生信号（观察虚拟化状态）
     */
    @Desugar
    public record Result(
            SceneNode root,
            SceneNode viewport,
            ReadableSignal<Integer> highlighted,
            Signal<Integer> scrollSignal,
            ReadableSignal<WindowModel> windowModel) {
    }

    /**
     * 构建虚拟网格控件。须在组件构建作用域（mount/portal builder）内调用，
     * 以便 effect/forEach 生命周期随组件卸载一并回收。
     *
     * @param rt    场景运行时（须注入文本度量，标签行高依赖度量）
     * @param props 输入契约（非 null）
     * @return 创建结果
     */
    public static Result create(SceneRuntime rt, Props props) {
        return create(rt, props, null);
    }

    /**
     * 构建虚拟网格控件（支持动态可见行数覆盖）。
     *
     * <p>{@code visibleRowsOverride} 为 null 时行为与 {@link #create(SceneRuntime, Props)}
     * 完全一致（viewport 高度与全部窗口数学取自 {@link Props#visibleRows()}）。
     * 非 null 时：生效行数 = {@code clamp(override.get(), >= 1)}，viewport 的
     * preferredHeight 经 {@code rt.bindComputed} 按 {@code rows * cellHeight + (rows - 1) * gapY}
     * 动态设置；windowModel 的 maxStartRow/mounted/maxScrollPx 与 KEY_DOWN 的
     * scrollTargetForRow 全部改用生效行数。数据收缩回夹、overscan、spacer 数学不变。</p>
     *
     * @param rt                 场景运行时（须注入文本度量）
     * @param props              输入契约（非 null）
     * @param visibleRowsOverride 动态可见行数只读信号；null 表示走 props.visibleRows() 静态行数
     * @return 创建结果
     */
    public static Result create(SceneRuntime rt, Props props,
                                ReadableSignal<Integer> visibleRowsOverride) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(props, "props");
        int stride = props.cellHeight() + props.gapY();

        ReadableSignal<Integer> visibleRows = visibleRowsOverride == null ? null
                : Computed.create(() -> {
                    Integer override = visibleRowsOverride.get();
                    return Integer.valueOf(Math.max(1, override == null ? 1 : override.intValue()));
                });

        SceneNode viewport = SceneNode.column();
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        if (visibleRows == null) {
            viewport.setPreferredHeight(viewportHeight(props, props.visibleRows()));
        } else {
            rt.bindComputed(() -> Integer.valueOf(viewportHeight(props, visibleRows.get().intValue())),
                    viewport::setPreferredHeight);
        }
        viewport.setGap(0);

        // 网格底座：root 即滚动 viewport，按 G07 ScrollContainer 先例自持一颗 GROUP 配方表面。
        // background/border/borderWidth/cornerRadius/backdrop/surfaceElevation 六项归
        // SceneSurfaceBinder 独占（契约 §4），主题切换只重派生、不重建节点；底座只装这一颗，
        // 行/单元零滤镜（§4.1 复用行轻量口径），不外包第二层容器避免叠层。
        // 时序契约（G07 同款）：构建期声明关心状态，Router 后续写入才会落到已创建的 signal。
        SceneInteractionState gridInteraction = rt.interactionState(viewport);
        gridInteraction.hovered();
        gridInteraction.pressed();
        gridInteraction.focused();
        SceneSurfaceBinder.bind(rt, viewport, SceneThemes.surface(rt, SceneTheme.Role.GROUP),
                ALWAYS_ENABLED, gridInteraction);

        // 主题语义色派生：构建期（来源作用域 Owner 内）解析一次，全部单元共享；
        // 主题切换只重派生（Computed 按值记忆化），不重建节点。
        CellPalette palette = new CellPalette(rt);

        Signal<Integer> scrollSignal = SceneScrolls.attach(rt, viewport);
        Signal<Integer> highlight = Signal.create(Integer.valueOf(-1));
        Signal<Integer> columns = Signal.create(Integer.valueOf(Math.max(1, props.columns())));

        if (props.columns() <= 0) {
            // 自动列数：布局完成后按 viewport 内宽推算（引擎宽度为权威，只读 LayoutBox）
            rt.bind(rt.layoutDoneSignal(), epoch -> Effect.untrack(() -> {
                Object cached = viewport.getCachedLayout();
                if (!(cached instanceof LayoutBox)) {
                    return;
                }
                int innerWidth = ((LayoutBox) cached).getWidth();
                int derived = SceneVirtualGridNav.deriveColumns(innerWidth, props.cellWidth(), props.gapX());
                if (derived != columns.get().intValue()) {
                    columns.set(Integer.valueOf(derived));
                }
            }));
        }

        ReadableSignal<WindowModel> windowModel = Computed.create(() -> {
            List<Item> items = safeItems(props.items());
            int cols = Math.max(1, columns.get().intValue());
            int totalItems = items.size();
            int totalRows = SceneVirtualGridNav.totalRows(totalItems, cols);
            int effectiveRows = visibleRows == null ? props.visibleRows() : visibleRows.get().intValue();
            int maxStartRow = Math.max(0, totalRows - effectiveRows);
            int scroll = scrollSignal.get().intValue();
            int ws = SceneVirtualGridNav.windowStartRowForScroll(scroll, stride, maxStartRow);
            int mounted = Math.min(effectiveRows + OVERSCAN_ROWS, totalRows - ws);
            int maxScrollPx = Math.max(0, totalRows * stride - viewportHeight(props, effectiveRows));
            List<WindowRow> rows = new ArrayList<>(Math.max(0, mounted));
            for (int i = 0; i < mounted; i++) {
                int firstIndex = (ws + i) * cols;
                int to = Math.min(totalItems, firstIndex + cols);
                rows.add(new WindowRow(firstIndex, new ArrayList<>(items.subList(firstIndex, to))));
            }
            return new WindowModel(cols, totalItems, totalRows, ws, mounted, maxStartRow,
                    maxScrollPx, rows);
        });

        // 结构：viewport = [topSpacer, rowsContainer, bottomSpacer]（gap=0，行间距由行 marginBottom 承担）
        SceneNode topSpacer = new SceneNode();
        SceneNode rowsContainer = SceneNode.column();
        rowsContainer.setGap(0);
        SceneNode bottomSpacer = new SceneNode();
        viewport.appendChild(topSpacer);
        viewport.appendChild(rowsContainer);
        viewport.appendChild(bottomSpacer);

        rt.bindComputed(() -> Integer.valueOf(
                windowModel.get().windowStartRow() * stride), topSpacer::setPreferredHeight);
        rt.bindComputed(() -> Integer.valueOf(Math.max(0,
                (windowModel.get().totalRows() - windowModel.get().windowStartRow()
                        - windowModel.get().mountedRows()) * stride)), bottomSpacer::setPreferredHeight);

        // 数据收缩回夹：scroll 超出新 maxScroll 时经 effect 拉回（参考旧版搜索选择器的 bindScrollClamp）
        rt.bindComputed(() -> Integer.valueOf(Math.max(0,
                Math.min(windowModel.get().maxScrollPx(), scrollSignal.get().intValue()))),
                clamped -> {
                    if (!clamped.equals(scrollSignal.get())) {
                        scrollSignal.set(clamped);
                    }
                });

        ReadableSignal<Integer> displayHighlight =
                props.highlighted() != null ? props.highlighted() : highlight;
        Consumer<Integer> highlightWriter = props.highlighted() != null
                ? (props.onHighlightChange() != null ? props.onHighlightChange() : ignored -> { })
                : highlight::set;

        ReadableSignal<List<WindowRow>> rowsSignal =
                Computed.create(() -> windowModel.get().rows());
        rt.forEach(rowsContainer, rowsSignal, WindowRow::firstIndex,
                row -> rowComponent(rt, props, row, columns, displayHighlight, highlightWriter, palette));

        rt.on(viewport, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())
                    || ev.getKeyAction() != SceneKeyAction.PRESSED || ev.isRepeat()) {
                return;
            }
            WindowModel model = windowModel.get();
            int current = displayHighlight.get().intValue();
            int next = SceneVirtualGridNav.navigate(current, ev.getKey(),
                    model.columns(), model.totalItems());
            if (next < 0 || next == current) {
                return;
            }
            ctx.stopPropagation();
            highlightWriter.accept(Integer.valueOf(next));
            int row = next / Math.max(1, model.columns());
            int effectiveRows = visibleRows == null ? props.visibleRows() : visibleRows.get().intValue();
            int scrollTarget = SceneVirtualGridNav.scrollTargetForRow(row,
                    model.windowStartRow(), effectiveRows, model.totalRows(), stride);
            if (scrollTarget >= 0 && scrollTarget != scrollSignal.get().intValue()) {
                scrollSignal.set(Integer.valueOf(scrollTarget));
            }
        });

        return new Result(viewport, viewport, displayHighlight, scrollSignal, windowModel);
    }

    /** viewport 高度闭式：{@code rows * cellHeight + (rows - 1) * gapY}。 */
    private static int viewportHeight(Props props, int visibleRows) {
        return visibleRows * props.cellHeight() + (visibleRows - 1) * props.gapY();
    }

    /** 构建一个完整网格行（ROW 容器，行高钉定，行间距经 marginBottom 计入主轴占位；行层纯布局、零外观写入）。 */
    private static SceneNode rowComponent(SceneRuntime rt, Props props, WindowRow row,
                                          ReadableSignal<Integer> columns,
                                          ReadableSignal<Integer> displayHighlight,
                                          Consumer<Integer> highlightWriter,
                                          CellPalette palette) {
        SceneNode rowNode = SceneNode.row();
        rowNode.setPreferredHeight(props.cellHeight());
        rowNode.setMargin(0, 0, props.gapY(), 0);
        rowNode.setGap(props.gapX());
        rowNode.setHitTestable(false);
        // 行节点按 firstIndex 复用后，单元内容仍须从实时数据源派生（避免复用行吃到陈旧快照）
        ReadableSignal<List<Item>> rowItems = Computed.create(() ->
                rowAt(row.firstIndex(), safeItems(props.items()), columns.get().intValue()));
        rt.forEach(rowNode, rowItems, Item::key,
                item -> cellComponent(rt, props, item, displayHighlight, highlightWriter, palette));
        return rowNode;
    }

    /** 构建单个网格单元（§4.1 复用行轻量口径：外观写入槽只剩 backgroundColor，主题选区/hover 档派生）。 */
    private static SceneNode cellComponent(SceneRuntime rt, Props props, Item item,
                                           ReadableSignal<Integer> displayHighlight,
                                           Consumer<Integer> highlightWriter,
                                           CellPalette palette) {
        SceneNode cell = SceneNode.column();
        cell.setPreferredWidth(props.cellWidth());
        cell.setPreferredHeight(props.cellHeight());
        cell.setClipChildren(true);
        cell.setGap(LABEL_GAP);
        cell.setPadding(CELL_PADDING);
        // 轻量覆盖口径（契约 §4.1 虚拟化复用行裁决）：单元不写圆角/边框、不装滤镜、
        // elevation 保持 -1（未绑定即普通绘制），外观只剩主题派生的 backgroundColor。
        SceneInteractionState interaction = rt.interactionState(cell);
        // 时序契约：构建期声明关心 hovered，Router 后续写入才会落到已创建的 signal。
        interaction.hovered();
        // 选中态按 key 从实时数据源派生：回收/重绑单元不携带旧项选中态，也不残留旧底色。
        ReadableSignal<Boolean> selected = Computed.create(() ->
                Integer.valueOf(itemIndex(safeItems(props.items()), item.key()))
                        .equals(displayHighlight.get()));
        rt.__bindAnimatedColor(() -> resolveCellBackground(
                        Boolean.TRUE.equals(props.enabled().get()),
                        Boolean.TRUE.equals(selected.get()),
                        Boolean.TRUE.equals(interaction.hovered().get()),
                        palette.accent.get(), palette.selectionBackground.get()),
                cell::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);

        SceneNode icon = new SceneNode();
        icon.setHitTestable(false);
        int lineHeight = rt.lineHeight(LABEL_FONT_SIZE);
        int iconHeight = Math.max(1, props.cellHeight() - CELL_PADDING * 2
                - (item.label() != null ? lineHeight + LABEL_GAP : 0));
        icon.setPreferredWidth(Math.max(1, props.cellWidth() - CELL_PADDING * 2));
        icon.setPreferredHeight(iconHeight);
        // 图位圆角与占位底色属物品图像渲染协议（契约 §4.1「物品图像不改色」+ §7.3）：保持静态值。
        icon.setCornerRadius(SceneChromeTokens.RADIUS_SM);
        rt.bindComputed(() -> imageAt(safeItems(props.items()), item.key()), src -> {
            icon.setBackgroundColor(src == null ? DEFAULT_PLACEHOLDER_COLOR : 0x00000000);
            icon.setImageSource(src);
        });
        cell.appendChild(icon);

        if (item.label() != null) {
            SceneNode label = new SceneNode();
            label.setHitTestable(false);
            // 私有常量降级为层 4a 回落值（有作用域/环境默认时跟随，无声明时仍落 12）。
            label.setFallbackFontSize(LABEL_FONT_SIZE);
            // 行文字取主题次要前景（旧 TEXT_SECONDARY 同值起步），禁用取禁用前景；
            // 选中区分由底色承担，不靠文字变色（G09/SearchResultList 口径）。
            label.setTextHorizontalAlign(TextHorizontalAlign.CENTER);
            rt.bindComputed(() -> Boolean.TRUE.equals(props.enabled().get())
                    ? palette.mutedForeground.get() : palette.disabledForeground.get(),
                    label::setTextColor);
            rt.bindComputed(() -> labelAt(safeItems(props.items()), item.key()), label::setText);
            cell.appendChild(label);
        }

        rt.on(cell, SceneEventType.CLICK, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            ctx.stopPropagation();
            props.onActivate().accept(item);
            int index = itemIndex(safeItems(props.items()), item.key());
            if (index >= 0) {
                highlightWriter.accept(Integer.valueOf(index));
            }
        });

        // 单元装饰回调：在本单元 item 作用域内调用（Owner.current()=itemOwner），
        // 回调内挂载的 effect/浮层随单元卸载一并回收——hover 浮层生命周期与虚拟化单元严格对齐。
        if (props.onCellMount() != null) {
            props.onCellMount().accept(cell, item);
        }
        return cell;
    }

    // ==================== 复用单元轻量覆盖派生（契约 §4.1 虚拟化复用行口径） ====================

    /**
     * 解析复用单元底色：禁用清空 &gt; 选中（主题选区背景 0x59）&gt; hover（主题 accent 0x1F）
     * &gt; 全透明（露出底座玻璃）。
     *
     * <p>轻量状态覆盖口径与 {@code SceneAutocomplete}/{@code SearchResultList} 候选行一致：
     * 只做半透明染色，不装滤镜；选中强度明显高于 hover，保证选中不只靠透明度区分。禁用时底色
     * 清空，禁用反馈由文字禁用前景与点击/键盘守卫承担，不伪造可用选中态。</p>
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

    // ==================== 实时数据源派生（行/单元复用后保持新鲜） ====================

    private static List<Item> safeItems(ReadableSignal<? extends List<Item>> signal) {
        List<Item> items = signal.get();
        return items == null ? java.util.Collections.<Item>emptyList() : items;
    }

    private static List<Item> rowAt(int firstIndex, List<Item> items, int columns) {
        int cols = Math.max(1, columns);
        if (items.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        int from = Math.max(0, Math.min(firstIndex, items.size()));
        int to = Math.min(items.size(), from + cols);
        return from < to ? new ArrayList<>(items.subList(from, to)) : java.util.Collections.<Item>emptyList();
    }

    private static int itemIndex(List<Item> items, Object key) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static SceneImageSource imageAt(List<Item> items, Object key) {
        for (Item item : items) {
            if (item.key().equals(key)) {
                return item.image();
            }
        }
        return null;
    }

    private static String labelAt(List<Item> items, Object key) {
        for (Item item : items) {
            if (item.key().equals(key)) {
                return item.label() == null ? "" : item.label();
            }
        }
        return "";
    }
}
