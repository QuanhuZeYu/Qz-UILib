package club.heiqi.uilib.ui.scene.control.search;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneVirtualGrid;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * SearchResultList —— 虚拟化候选网格（搜索选择器的结果列表）。
 *
 * <h3>定位：虚拟化网格薄封装</h3>
 * <p>列表机制完整复用 {@link SceneVirtualGrid} 的行级虚拟化：无项数上限，只有可见窗口内的行
 * 实际挂载（含 overscan 一行），滚动由窗口数学（topSpacer/rowsContainer/bottomSpacer）驱动，
 * 数据收缩时 scroll 自动回夹，ARROW_* 四向导航与自动滚动进入视野由网格内建。本模块只补充
 * 两件事：悬停经 {@link SceneVirtualGrid.Props#onCellMount()} 在单元 item 作用域内回写
 * {@link Props#onHoverItem()}（供外壳信息条派生文本，取代悬浮 tooltip），以及 ENTER 激活
 * 当前高亮项。</p>
 *
 * <h3>可见行数</h3>
 * <p>{@link Props#visibleRows()} 为静态兜底行数；{@link Props#visibleRowsOverride()} 非 null 时
 * 生效行数随该信号动态变化（viewport 高度 = {@code rows*cellHeight + (rows-1)*gapY} 闭式），
 * 供外壳按中栏可用高度自适应。列数 {@code columns <= 0} 时按 viewport 内宽自动推导。</p>
 */
public final class SearchResultList {

    private SearchResultList() {
    }

    /**
     * 结果列表输入契约（不可变）。
     *
     * @param items                数据源（非 null；List 值变更经 keyed reconcile 最小重建）
     * @param columns              列数；&lt;=0 时按 viewport 可用宽度自动推算（同 SceneVirtualGrid）
     * @param cellWidth            单元宽（UI 像素，&gt;0）
     * @param cellHeight           单元高（UI 像素，&gt;0）
     * @param gapX                 列间距（&gt;=0）
     * @param gapY                 行间距（&gt;=0）
     * @param visibleRows          静态兜底可见行数（&gt;=1）
     * @param enabled              是否启用（禁用时不响应点击/键盘）
     * @param onActivate           点击/回车激活回调（非 null）
     * @param highlighted          受控高亮下标信号（非 null；下标 = item 在完整列表中的下标）
     * @param onHighlightChange    高亮回写回调（非 null）
     * @param onHoverItem          单元 hover 回调（可为 null = 不回调；hover 时传 item、移出时传 null）
     * @param visibleRowsOverride  动态可见行数信号（可为 null = 走 visibleRows 静态行数）
     */
    @Desugar
    public record Props(
            ReadableSignal<? extends List<SceneVirtualGrid.Item>> items,
            int columns, int cellWidth, int cellHeight, int gapX, int gapY, int visibleRows,
            ReadableSignal<Boolean> enabled,
            Consumer<SceneVirtualGrid.Item> onActivate,
            ReadableSignal<Integer> highlighted,
            Consumer<Integer> onHighlightChange,
            Consumer<SceneVirtualGrid.Item> onHoverItem,
            ReadableSignal<Integer> visibleRowsOverride) {

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
            if (visibleRows < 1) {
                throw new IllegalArgumentException("visibleRows 必须 >= 1");
            }
        }
    }

    /**
     * 构建结果列表控件（虚拟化网格薄封装）。须在组件构建作用域（mount/portal builder）内调用，
     * 以便 effect/forEach 生命周期随组件卸载一并回收。
     *
     * @param rt    场景运行时（须注入文本度量，标签行高依赖度量）
     * @param props 输入契约（非 null）
     * @return 虚拟网格创建结果（viewport 挂到宿主布局树；scrollSignal/windowModel 供外壳观察）
     */
    public static SceneVirtualGrid.Result create(SceneRuntime rt, Props props) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(props, "props");

        // 悬停：经 onCellMount 在单元 item 作用域内挂载（随虚拟化单元卸载一并回收，
        // 生命周期与单元严格对齐），hover 进入回写 item、移出回写 null。
        BiConsumer<SceneNode, SceneVirtualGrid.Item> onCellMount =
                props.onHoverItem() == null ? null : (cell, item) -> {
                    ReadableSignal<Boolean> hovered = rt.interactionState(cell).hovered();
                    rt.bind(hovered, h -> props.onHoverItem().accept(
                            Boolean.TRUE.equals(h) ? item : null));
                };

        SceneVirtualGrid.Result grid = SceneVirtualGrid.create(rt, new SceneVirtualGrid.Props(
                props.items(), props.columns(), props.cellWidth(), props.cellHeight(),
                props.gapX(), props.gapY(), props.visibleRows(), props.enabled(),
                props.onActivate(), props.highlighted(), props.onHighlightChange(), onCellMount),
                props.visibleRowsOverride());

        // ENTER 激活当前高亮项（ARROW_* 导航、越界夹取与自动滚动由 SceneVirtualGrid 内建）。
        rt.on(grid.viewport(), SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())
                    || ev.getKeyAction() != SceneKeyAction.PRESSED || ev.isRepeat()
                    || ev.getKey() != SceneKey.ENTER) {
                return;
            }
            List<SceneVirtualGrid.Item> items = props.items().get();
            int current = grid.highlighted().get().intValue();
            if (items == null || current < 0 || current >= items.size()) {
                return;
            }
            ctx.stopPropagation();
            props.onActivate().accept(items.get(current));
        });

        return grid;
    }
}
