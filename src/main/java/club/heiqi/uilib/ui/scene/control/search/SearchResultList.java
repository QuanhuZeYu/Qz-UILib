package club.heiqi.uilib.ui.scene.control.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneControlChrome;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGridNav;
import club.heiqi.uilib.ui.scene.image.ItemRenderTierRegistry;
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
 * <h3>滚动条</h3>
 * <p>根节点 = stackHost 行（viewport + 右侧 {@link SceneScrollbar} 叠加列），
 * 与 SceneSimpleList/SceneKeyValueMap 等通用滚动容器同构；滚动条复用默认视觉常量。</p>
 *
 * <h3>渲染分级回退</h3>
 * <p>本模块订阅 {@link ItemRenderTierRegistry}：平台渲染层把某 registryKey 分级为
 * {@code UNRENDERABLE} 后，对应单元图标回退占位底色（与无图片项同款样式），
 * 不再继续尝试渲染；registryKey 与条目 key 的匹配约定为 {@code image.registryKey() == item.key()}
 * （物品注册名:meta，与下游候选 key 同格式）。</p>
 *
 * <h3>单元视觉与交互</h3>
 * <p>单元完整复刻 {@link SceneVirtualGrid#cellComponent}：占位底色、icon 尺寸
 * （cellHeight - 2*CELL_PADDING - 有label时(lineHeight+LABEL_GAP)）、label 12px 居中
 * TEXT_SECONDARY、cornerRadius RADIUS_SM、选中态 = item 在完整列表中的下标 == highlighted、
 * CLICK 激活 + 高亮回写，悬停/选中底色走 {@link SceneControlChrome#bindSelectableBackground}。</p>
 */
public final class SearchResultList {

    private static final Logger LOG = LogManager.getLogger("QzUiLib/SearchResultList");

    /** 单元内边距。 */
    public static final int CELL_PADDING = 4;
    /** 单元标签字号。 */
    public static final int LABEL_FONT_SIZE = 12;
    /** 图标与标签间距。 */
    public static final int LABEL_GAP = 2;
    /** 无图片项的占位底色（与 SceneVirtualGrid 同色）。 */
    public static final int DEFAULT_PLACEHOLDER_COLOR = 0xFF454B54;

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

        SceneNode viewport = SceneNode.column();
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        viewport.setFlexGrow(1);
        viewport.setFillParentHeight(true);
        viewport.setGap(0);

        Signal<Integer> scrollSignal = SceneScrolls.attach(rt, viewport);

        // stackHost：viewport 右侧叠加滚动条列（与通用滚动容器同构）。
        SceneNode stackHost = SceneNode.row();
        stackHost.setFillParentHeight(true);
        stackHost.setGap(0);
        stackHost.appendChild(viewport);
        SceneScrollbar.Result scrollbar = SceneScrollbar.createDefault(rt, viewport, scrollSignal);
        stackHost.appendChild(scrollbar.column());

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

        // 结构：viewport = [rowsContainer]（gap=0，行间距由行 marginBottom 承担）。
        SceneNode rowsContainer = SceneNode.column();
        rowsContainer.setGap(0);
        rowsContainer.setHitTestable(false);
        viewport.appendChild(rowsContainer);

        // 渲染分级回退：订阅注册表，不可渲染项写入 unrenderableKeys → 单元回退占位样式。
        Signal<Set<Object>> unrenderableKeys = Signal.create(Collections.<Object>emptySet());
        ItemRenderTierRegistry.Listener listener = classification -> {
            if (classification.tier() != ItemRenderTierRegistry.Tier.UNRENDERABLE) {
                return;
            }
            Object itemKey = itemKeyForRegistryKey(safeItems(props.items()), classification.registryKey());
            if (itemKey == null) {
                return;
            }
            Set<Object> current = unrenderableKeys.get();
            if (current.contains(itemKey)) {
                return;
            }
            Set<Object> next = new HashSet<>(current);
            next.add(itemKey);
            unrenderableKeys.set(Collections.unmodifiableSet(next));
            LOG.warn("[qz-picker-icon] 物品 {} 渲染失败，已标记不可渲染并回退占位样式：{}",
                    itemKey, classification.detail());
        };
        // 全量行模型：items 全部项按生效列数分行（无上限、无截断）。
        // 行键用该行首项在完整列表中的下标（稳定唯一）。
        ReadableSignal<List<Row>> rowsSignal =
                Computed.create(() -> toRows(safeItems(props.items()), effectiveColumns.get().intValue()));

        rt.forEach(rowsContainer, rowsSignal, Row::firstIndex,
                row -> rowComponent(rt, props, row, effectiveColumns, unrenderableKeys));

        ItemRenderTierRegistry.addListener(listener);
        Owner owner = Owner.current();
        if (owner != null) {
            owner.onCleanup(() -> ItemRenderTierRegistry.removeListener(listener));
        }

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
            int stride = props.cellHeight() + props.gapY();
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
                                          ReadableSignal<Set<Object>> unrenderableKeys) {
        SceneNode rowNode = SceneNode.row();
        rowNode.setPreferredHeight(props.cellHeight());
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
                item -> cellComponent(rt, props, item, unrenderableKeys));
        return rowNode;
    }

    /** 构建单个结果单元（视觉与交互完整复刻 SceneVirtualGrid.cellComponent）。 */
    private static SceneNode cellComponent(SceneRuntime rt, Props props, SceneVirtualGrid.Item item,
                                           ReadableSignal<Set<Object>> unrenderableKeys) {
        SceneNode cell = SceneNode.column();
        cell.setPreferredWidth(props.cellWidth());
        cell.setPreferredHeight(props.cellHeight());
        cell.setClipChildren(true);
        cell.setGap(LABEL_GAP);
        cell.setPadding(CELL_PADDING);
        cell.setCornerRadius(SceneChromeTokens.RADIUS_SM);
        SceneInteractionState interaction = rt.interactionState(cell);
        // 选中态：item 在完整 items 列表中的下标 == highlighted
        ReadableSignal<Boolean> selected = Computed.create(() ->
                Integer.valueOf(itemIndex(safeItems(props.items()), item.key()))
                        .equals(props.highlighted().get()));
        SceneControlChrome.bindSelectableBackground(rt, cell, props.enabled(), selected, interaction);

        SceneNode icon = new SceneNode();
        icon.setHitTestable(false);
        int lineHeight = rt.lineHeight(LABEL_FONT_SIZE);
        int iconHeight = Math.max(1, props.cellHeight() - CELL_PADDING * 2
                - (item.label() != null ? lineHeight + LABEL_GAP : 0));
        icon.setPreferredWidth(Math.max(1, props.cellWidth() - CELL_PADDING * 2));
        icon.setPreferredHeight(iconHeight);
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

        if (item.label() != null) {
            SceneNode label = new SceneNode();
            label.setHitTestable(false);
            label.setFontSize(LABEL_FONT_SIZE);
            label.setTextColor(SceneChromeTokens.TEXT_SECONDARY);
            label.setTextHorizontalAlign(TextHorizontalAlign.CENTER);
            label.setText(item.label());
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

        // hover 回调：先声明 hovered（懒创建时序契约），再经 effect 回写 item/null
        if (props.onHoverItem() != null) {
            ReadableSignal<Boolean> hovered = rt.interactionState(cell).hovered();
            rt.bind(hovered, h -> props.onHoverItem().accept(
                    Boolean.TRUE.equals(h) ? item : null));
        }

        return cell;
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

    /** 按 registryKey 反查条目 key（约定：{@code image.registryKey() == item.key()}）。 */
    private static Object itemKeyForRegistryKey(List<SceneVirtualGrid.Item> items, String registryKey) {
        if (registryKey == null) {
            return null;
        }
        for (SceneVirtualGrid.Item item : items) {
            SceneImageSource image = item.image();
            if (image != null && registryKey.equals(image.registryKey())) {
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
