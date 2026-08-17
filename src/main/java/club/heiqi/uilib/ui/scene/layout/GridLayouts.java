package club.heiqi.uilib.ui.scene.layout;

import java.util.Objects;

import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 网格布局门面 —— 网格容器的简洁工厂 + 布局后置步接入。
 *
 * <h3>定位与接入</h3>
 * <p>本阶段只新增文件、不改 {@link SceneLayoutEngine} 与 {@link SceneNode}，因此「SceneNode 侧
 * 网格工厂」以本类静态工厂形式提供（{@link #container}/{@link #autoWrapContainer}）。
 * {@link #attach} 订阅 {@link SceneRuntime#layoutDoneSignal()}，在引擎 layout 完成后
 * （host 桥接 epoch 的同一 flush 内）由 {@link GridLayouter} 按 {@link GridSpec} 重定位子节点——
 * 与 {@code SceneScrollbar} 的 layoutDoneSignal 驱动几何模式同构。</p>
 *
 * <h3>调用约定</h3>
 * <ol>
 *   <li>{@code SceneNode grid = GridLayouts.container(spec); parent.appendChild(grid);}</li>
 *   <li>把等宽子项 append 到 grid；</li>
 *   <li>{@code GridLayouts.attach(rt, grid, spec);}（组件构建作用域内调用，effect 随 Owner 回收）；</li>
 *   <li>容器自身高度由引擎决定：建议给容器钉定高度（preferredHeight / fill / scrollable 视口）。</li>
 * </ol>
 *
 * <p><b>边界</b>：网格重定位只发生在 host 桥接 {@code layoutDoneSignal} 之后；未桥接的宿主
 * （如仅调 {@code engine.layout} 不桥接）看不到网格定位结果。现有宿主（AbstractSceneHostWidget
 * 路径）已桥接（SceneScrollbar 依赖同机制）。</p>
 */
public final class GridLayouts {

    private GridLayouts() {
    }

    /**
     * 创建网格容器节点（COLUMN 语义占位，实际定位由 {@link #attach} 接管）。
     *
     * <p>节点默认开启 {@code clipChildren}，gap 取 {@code spec.gapY()} 使引擎首轮 flex
     * 定位与盒高计算带间距（盒高以引擎口径为准，见 {@link GridLayouter} 已知边界）。</p>
     *
     * @param spec 网格规格（非 null）
     * @return 网格容器节点
     */
    public static SceneNode container(GridSpec spec) {
        Objects.requireNonNull(spec, "spec");
        SceneNode node = SceneNode.column();
        node.setClipChildren(true);
        node.setGap(Math.max(0, spec.gapY()));
        return node;
    }

    /** 便捷工厂：固定列数网格容器。 */
    public static SceneNode container(int columns, int cellWidth, int cellHeight, int gapX, int gapY) {
        return container(GridSpec.of(columns, cellWidth, cellHeight, gapX, gapY));
    }

    /** 便捷工厂：按可用宽自动推算列数的网格容器。 */
    public static SceneNode autoWrapContainer(int cellWidth, int cellHeight, int gapX, int gapY) {
        return container(GridSpec.autoColumns(cellWidth, cellHeight, gapX, gapY));
    }

    /**
     * 把网格重定位管线挂到运行时：订阅 {@code layoutDoneSignal}，每帧布局完成后按
     * {@link GridSpec} 对容器子节点做网格定位。
     *
     * <p>effect 归属当前 Owner 作用域（组件卸载自动退订）；写回仅走几何闸门 + geometry/paint
     * 脏位，绝不触发 layout 级标脏（避免与引擎 flex 定位逐帧振荡）。</p>
     *
     * @param rt            场景运行时（非 null）
     * @param gridContainer 网格容器节点（非 null，须已进入场景树）
     * @param spec          网格规格（非 null）
     */
    public static void attach(SceneRuntime rt, SceneNode gridContainer, GridSpec spec) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(gridContainer, "gridContainer");
        Objects.requireNonNull(spec, "spec");
        rt.bind(rt.layoutDoneSignal(), epoch -> Effect.untrack(() -> {
            Object cached = gridContainer.getCachedLayout();
            if (!(cached instanceof LayoutBox)) {
                return;
            }
            LayoutBox box = (LayoutBox) cached;
            GridLayouter.positionChildren(gridContainer, spec, box.getWidth(), box.getHeight());
        }));
    }
}
