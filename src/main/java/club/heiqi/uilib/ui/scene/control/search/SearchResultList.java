package club.heiqi.uilib.ui.scene.control.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneScrollContainer;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGridNav;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextHorizontalAlign;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
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
    /** 无图片项的占位底色（与 SceneVirtualGrid 同色，属图像渲染协议，非主题槽位）。 */
    public static final int DEFAULT_PLACEHOLDER_COLOR = 0xFF454B54;
    /** 结果单元默认底色：全透明，露出底座玻璃（单元不各自采样滤镜）。 */
    private static final int CELL_BG_TRANSPARENT = 0x00000000;
    /** 结果单元 hover 覆盖强度：主题 accent 的低透明度轻量覆盖。 */
    private static final int CELL_HOVER_ALPHA = 0x1F;
    /** 结果单元选中（高亮）覆盖强度：主题选区背景，明显强于 hover（不只靠透明度区分）。 */
    private static final int CELL_SELECTED_ALPHA = 0x59;

    private SearchResultList() {
    }

    /**
     * 结果列表输入契约（不可变）。
     *
     * @param items             数据源（非 null；List 值变更经 keyed reconcile 最小重建）
     * @param columns           列数；&lt;=0 时按 viewport 可用宽度自动推算（同 SceneVirtualGrid）
     * @param cellWidth         单元宽（UI 像素，&gt;0）
     * @param cellHeight        单元高（UI 像素，&gt;0）
     * @param gapX              列间距（&gt;=0）
     * @param gapY              行间距（&gt;=0）
     * @param enabled           是否启用（禁用时不响应点击/键盘）
     * @param onActivate        点击/回车激活回调（非 null）
     * @param highlighted       受控高亮下标信号（非 null；下标 = item 在完整列表中的下标）
     * @param onHighlightChange 高亮回写回调（非 null）
     * @param onHoverItem       单元 hover 回调（可为 null = 不回调；hover 时传 item、移出时传 null）
     */
    @Desugar
    public record Props(
            ReadableSignal<? extends List<SceneVirtualGrid.Item>> items,
            int columns, int cellWidth, int cellHeight, int gapX, int gapY,
            ReadableSignal<Boolean> enabled,
            Consumer<SceneVirtualGrid.Item> onActivate,
            ReadableSignal<Integer> highlighted,
            Consumer<Integer> onHighlightChange,
            Consumer<SceneVirtualGrid.Item> onHoverItem) {

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
        }
    }

    /**
     * 创建结果。
     *
     * @param root     根节点 = stackHost 行（viewport + 滚动条列），挂到宿主布局树
     * @param viewport 可滚动视口（焦点/滚动语义所在节点）
     */
    @Desugar
    public record Result(SceneNode root, SceneNode viewport) {
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
        SceneScrollContainer.Result sc = SceneScrollContainer.createDefault(rt, 0, 0, 0, 0);
        SceneNode viewport = sc.viewport();
        Signal<Integer> scrollSignal = sc.scrollSignal();
        SceneNode stackHost = sc.container();

        // 单元轨道高（S5 ③ 收口）：以调用方 cellHeight 为下限，随生效字号抬升到
        // 「图位最小 + 标签行高 + 标签间距 + 上下内边距」，避免大字号下标签被单元裁切。
        // 本控件不做行级虚拟化（全量挂载、滚动承担高度），故轨道高可以随字号变；
        // 行/单元 preferredHeight 与图位高统一读本信号。
        // 层 4a 回落值：与单元标签的回落值同源（12），使控件根解析出的生效字号与标签一致——
        // 否则未声明字号时根的层 4b 默认 16 会让轨道按 16 算（默认几何漂移）。层 1/2/3 均优先于它。
        stackHost.setFallbackFontSize(LABEL_FONT_SIZE);
        final Signal<Integer> trackHeight = Signal.create(Integer.valueOf(props.cellHeight()));
        stackHost.setFontSizeMetric((node, fontSizePx) ->
                trackHeight.set(Integer.valueOf(minTrackHeightFor(rt, props, fontSizePx))));

        // 数据收缩/视口变化回夹：布局完成后把 scroll 夹回 maxScrollY（非虚拟化，maxScrollY 随内容高即时变化）。
        rt.bind(rt.layoutDoneSignal(), epoch -> Effect.untrack(() -> {
            int max = SceneGeometry.maxScrollY(viewport);
            int clamped = Math.max(0, Math.min(max, scrollSignal.get().intValue()));
            if (clamped != scrollSignal.get().intValue()) {
                scrollSignal.set(Integer.valueOf(clamped));
            }
        }));

        // 生效列数：columns <= 0 时按 viewport 可用宽度自动推导（布局完成后读 cachedLayout 宽）。
        Signal<Integer> effectiveColumns =
                Signal.create(Integer.valueOf(Math.max(1, props.columns())));
        if (props.columns() <= 0) {
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

        // 结构：viewport = [content]（gap=0，行间距由行 marginBottom 承担）。
        // viewport 底座外观已归 SceneScrollContainer 默认路径的 SceneSurfaceBinder（GROUP 配方）
        // 独占，本控件不再二次绑定、不给单元各装滤镜。
        SceneNode rowsContainer = sc.content();
        rowsContainer.setHitTestable(false);

        // 主题语义色派生：构建期（来源作用域 Owner 内）解析一次，全部单元共享；
        // 主题切换只重派生（Computed 按值记忆化），不重建节点。
        CellPalette palette = new CellPalette(rt);

        // 渲染分级回退：订阅注册表，不可渲染项写入 unrenderableKeys → 单元回退占位样式（共享装配）。
        Signal<Set<Object>> unrenderableKeys = ItemRenderFallbackKeys.track(
                registryKey -> itemKeyForRegistryKey(safeItems(props.items()), registryKey));
        // 全量行模型：items 全部项按生效列数分行（无上限、无截断）。
        // 行键用该行首项在完整列表中的下标（稳定唯一）。
        ReadableSignal<List<Row>> rowsSignal =
                Computed.create(() -> toRows(safeItems(props.items()), effectiveColumns.get().intValue()));

        rt.forEach(rowsContainer, rowsSignal, Row::firstIndex,
                row -> rowComponent(rt, props, row, effectiveColumns, unrenderableKeys, palette, trackHeight));

        rt.on(viewport, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())
                    || ev.getKeyAction() != SceneKeyAction.PRESSED || ev.isRepeat()) {
                return;
            }
            SceneKey key = ev.getKey();
            int current = props.highlighted().get().intValue();

            // ENTER 激活当前高亮项
            if (key == SceneKey.ENTER) {
                List<SceneVirtualGrid.Item> items = safeItems(props.items());
                if (current >= 0 && current < items.size()) {
                    ctx.stopPropagation();
                    props.onActivate().accept(items.get(current));
                }
                return;
            }

            // ARROW_* 四向导航（全部项范围内）
            int cols = Math.max(1, effectiveColumns.get().intValue());
            int total = safeItems(props.items()).size();
            int next = SceneVirtualGridNav.navigate(current, key, cols, total);
            if (next < 0 || next == current) {
                return;
            }
            ctx.stopPropagation();
            props.onHighlightChange().accept(Integer.valueOf(next));

            // 自动滚动到目标行：target = rowIndex*(cellHeight+gapY) - 可视高度/2，clamp 到 [0, maxScrollY]
            int rowIndex = next / cols;
            int stride = trackHeight.get().intValue() + props.gapY();
            int viewportH = visibleHeight(viewport);
            int target = rowIndex * stride - viewportH / 2;
            int maxScroll = SceneGeometry.maxScrollY(viewport);
            int clamped = Math.max(0, Math.min(maxScroll, target));
            if (clamped != scrollSignal.get().intValue()) {
                scrollSignal.set(Integer.valueOf(clamped));
            }
        });

        return new Result(stackHost, viewport);
    }

    /** 全量行（非虚拟化行模型，全部项分行）。 */
    @Desugar
    public record Row(int firstIndex, List<SceneVirtualGrid.Item> items) {
    }

    private static List<Row> toRows(List<SceneVirtualGrid.Item> items, int cols) {
        cols = Math.max(1, cols);
        int rowCount = items.size() <= 0 ? 0 : (items.size() + cols - 1) / cols;
        List<Row> rows = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            int firstIndex = i * cols;
            int to = Math.min(items.size(), firstIndex + cols);
            rows.add(new Row(firstIndex, new ArrayList<>(items.subList(firstIndex, to))));
        }
        return rows;
    }

    /** 构建一个完整结果行（ROW 容器，行高钉定，行间距经 marginBottom 计入主轴占位）。 */
    private static SceneNode rowComponent(SceneRuntime rt, Props props, Row row,
                                          ReadableSignal<Integer> effectiveColumns,
                                          ReadableSignal<Set<Object>> unrenderableKeys,
                                          CellPalette palette,
                                          ReadableSignal<Integer> trackHeight) {
        SceneNode rowNode = SceneNode.row();
        rowNode.setPreferredHeight(trackHeight.get().intValue());
        // 轨道高随生效字号变 → 行高跟着变（本控件不做虚拟化，内容高变化由滚动承担）。
        rt.bind(trackHeight, h -> rowNode.setPreferredHeight(h.intValue()));
        rowNode.setMargin(0, 0, props.gapY(), 0);
        rowNode.setGap(props.gapX());
        rowNode.setHitTestable(false);
        // 行节点按 firstIndex 复用后，行内容必须从实时数据源 + 实时列数派生
        //（避免复用行吃到创建时的陈旧快照——旧虚拟网格的同款陷阱）。
        ReadableSignal<List<SceneVirtualGrid.Item>> rowItems = Computed.create(() -> {
            List<SceneVirtualGrid.Item> items = safeItems(props.items());
            int cols = Math.max(1, effectiveColumns.get().intValue());
            int start = row.firstIndex();
            if (start < 0 || start >= items.size()) {
                return Collections.<SceneVirtualGrid.Item>emptyList();
            }
            int to = Math.min(items.size(), start + cols);
            return new ArrayList<SceneVirtualGrid.Item>(items.subList(start, to));
        });
        rt.forEach(rowNode, rowItems, SceneVirtualGrid.Item::key,
                item -> cellComponent(rt, props, item, unrenderableKeys, palette, trackHeight));
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
    private static void bindIconHeight(SceneRuntime rt, SceneNode icon, SceneNode label,
                                       ReadableSignal<Integer> trackHeight) {
        rt.bind(trackHeight, height -> icon.setPreferredHeight(
                iconHeightFor(rt, height.intValue(), label)));
        icon.setPreferredHeight(iconHeightFor(rt, trackHeight.get().intValue(), label));
    }

    /** 图位高 = 轨道高 - 2*内边距 -（有标签时）标签行高 + 间距；至少 1px。 */
    private static int iconHeightFor(SceneRuntime rt, int trackHeightPx, SceneNode label) {
        int available = trackHeightPx - CELL_PADDING * 2;
        if (label != null) {
            available -= rt.lineHeight(label.effectiveFontSize()) + LABEL_GAP;
        }
        return Math.max(1, available);
    }

    /** 轨道高下限：调用方 cellHeight 与「图位最小 + 标签行高 + 间距 + 内边距」取大。 */
    private static int minTrackHeightFor(SceneRuntime rt, Props props, int fontSizePx) {
        return Math.max(props.cellHeight(),
                rt.lineHeight(fontSizePx) + LABEL_GAP + 1 + CELL_PADDING * 2);
    }
    /** 构建单个结果单元（结构复刻 SceneVirtualGrid.cellComponent；外观为主题轻量覆盖）。 */
    private static SceneNode cellComponent(SceneRuntime rt, Props props, SceneVirtualGrid.Item item,
                                           ReadableSignal<Set<Object>> unrenderableKeys,
                                           CellPalette palette,
                                           ReadableSignal<Integer> trackHeight) {
        SceneNode cell = SceneNode.column();
        cell.setPreferredWidth(props.cellWidth());
        cell.setPreferredHeight(trackHeight.get().intValue());
        rt.bind(trackHeight, h -> cell.setPreferredHeight(h.intValue()));
        cell.setClipChildren(true);
        cell.setGap(LABEL_GAP);
        cell.setPadding(CELL_PADDING);
        // 轻量覆盖口径：单元不写圆角/边框（外观写入槽只剩 backgroundColor），不装滤镜。
        SceneInteractionState interaction = rt.interactionState(cell);
        // 时序契约：构建期声明关心 hovered，Router 后续写入才会落到已创建的 signal。
        interaction.hovered();
        // 选中态：item 在完整 items 列表中的下标 == highlighted（按 key 动态派生，
        // 复用/重绑单元不携带旧项选中态）。
        ReadableSignal<Boolean> selected = Computed.create(() ->
                Integer.valueOf(itemIndex(safeItems(props.items()), item.key()))
                        .equals(props.highlighted().get()));
        rt.__bindAnimatedColor(() -> resolveCellBackground(
                        Boolean.TRUE.equals(props.enabled().get()),
                        Boolean.TRUE.equals(selected.get()),
                        Boolean.TRUE.equals(interaction.hovered().get()),
                        palette.accent.get(), palette.selectionBackground.get()),
                cell::setBackgroundColor, SceneChromeTokens.MOTION_FAST_MS);

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
            label.setMaxTextWidth(Math.max(1, props.cellWidth() - CELL_PADDING * 2));
            label.setMaxLines(1);
            label.setEllipsis(true);
        }

        SceneNode icon = new SceneNode();
        icon.setHitTestable(false);
        icon.setPreferredWidth(Math.max(1, props.cellWidth() - CELL_PADDING * 2));
        bindIconHeight(rt, icon, label, trackHeight);
        icon.setCornerRadius(SceneChromeTokens.RADIUS_SM);
        // 生效图标：不可渲染项回退占位底色（null 图片），其余从实时数据源派生（含渲染分级变化）。
        ReadableSignal<SceneImageSource> effectiveImage = Computed.create(() -> {
            if (unrenderableKeys.get().contains(item.key())) {
                return null;
            }
            return imageAt(safeItems(props.items()), item.key());
        });
        rt.bind(effectiveImage, src -> {
            icon.setBackgroundColor(src == null ? DEFAULT_PLACEHOLDER_COLOR : 0x00000000);
            icon.setImageSource(src);
        });
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
            int index = itemIndex(safeItems(props.items()), item.key());
            if (index >= 0) {
                props.onHighlightChange().accept(Integer.valueOf(index));
            }
        });

        // hover 回调：hovered 已在构建期声明（懒创建时序契约），再经 effect 回写 item/null
        if (props.onHoverItem() != null) {
            rt.bind(interaction.hovered(), h -> props.onHoverItem().accept(
                    Boolean.TRUE.equals(h) ? item : null));
        }

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

    /**
     * 按 registryKey 反查条目 key（registryKey = 注册名:meta，条目 key = 注册名，拆末段冒号对齐）。
     */
    private static Object itemKeyForRegistryKey(List<SceneVirtualGrid.Item> items, String registryKey) {
        String[] parts = ItemRenderFallbackKeys.splitRegistryKey(registryKey);
        if (parts == null) {
            return null;
        }
        for (SceneVirtualGrid.Item item : items) {
            if (parts[0].equals(item.key())) {
                return item.key();
            }
        }
        return null;
    }

    private static List<SceneVirtualGrid.Item> safeItems(
            ReadableSignal<? extends List<SceneVirtualGrid.Item>> signal) {
        List<SceneVirtualGrid.Item> items = signal.get();
        return items == null ? Collections.<SceneVirtualGrid.Item>emptyList() : items;
    }

    private static SceneImageSource imageAt(List<SceneVirtualGrid.Item> items, Object key) {
        for (SceneVirtualGrid.Item item : items) {
            if (item.key().equals(key)) {
                return item.image();
            }
        }
        return null;
    }

    private static int itemIndex(List<SceneVirtualGrid.Item> items, Object key) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }
}
