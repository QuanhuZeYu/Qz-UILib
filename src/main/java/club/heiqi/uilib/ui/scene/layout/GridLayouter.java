package club.heiqi.uilib.ui.scene.layout;

import java.util.List;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 网格布局定位协作者 —— 与 {@link FlexLayouter} 同构的纯函数型消费者。
 *
 * <p>消费布局引擎已算好的容器外宽/最终高（引擎先做子节点尺寸测量），按
 * {@link GridSpec} 把子节点按固定列数（或可用宽推算列数）排列成等宽单元并自动换行，
 * 用与 FlexLayouter 完全相同的「几何闸门 + 脏标记」语义写回 {@link LayoutBox}。</p>
 *
 * <h3>与 FlexLayouter 的同构契约</h3>
 * <ul>
 *   <li>同逻辑坐标：产出 {@link LayoutBox} 局部坐标，供 {@link SceneGeometry} 与 paint/hit-test 消费；</li>
 *   <li>同失效语义：仅当 {@code newBox.equals(oldBox)} 为 false 才替换缓存并
 *       {@code markGeometryDirty}，尺寸变化时补 {@code markSelfPaint}，绝不向下递归；</li>
 *   <li>同 bubble 信号：返回 {@link FlexLayouter.SelfBubbleSignal}，容器自身盒写入语义
 *       与 FlexLayouter 步骤 D 逐位一致。</li>
 * </ul>
 *
 * <h3>引擎时序（只新增文件、不改引擎的接入方式）</h3>
 * <p>本协作者不注册进 {@link SceneLayoutEngine} 主流程，而由 {@link GridLayouts#attach} 在
 * 引擎 layout 完成后（{@code layoutDoneSignal} 驱动）作为定位后置步执行。引擎的 flex 定位
 * 结果被网格位置覆盖；网格只写 LayoutBox 与 geometry/paint 脏位，<b>绝不触发 layout 级标脏</b>——
 * 否则下一帧引擎会把网格位置再盖回 flex 位置，形成逐帧振荡。</p>
 *
 * <h3>已知边界</h3>
 * <p>容器自身盒高仍由引擎决定（flex 柱状求和），网格不反推容器高度：内容换行后真实高度低于
 * 引擎盒高时盒底留白。调用方应给网格容器钉定高度（preferredHeight/fill/scrollable 视口）。</p>
 */
class GridLayouter {

    private GridLayouter() {
    }

    /**
     * 消费引擎锁定值执行网格定位（步骤 B/C/D 对齐 FlexLayouter）。
     *
     * @param node            网格容器节点
     * @param spec            网格规格（列数/单元尺寸/间距/对齐）
     * @param outerWidth      容器外宽（引擎 computeWidth 锁定值）
     * @param rootFinalHeight 容器最终高（引擎 computeHeight 锁定值）
     * @return 本节点自身 bubble 信号（geometry / paint）
     */
    static FlexLayouter.SelfBubbleSignal positionChildren(SceneNode node, GridSpec spec,
                                                          int outerWidth, int rootFinalHeight) {
        int padTop = node.getPaddingTop();
        int padRight = node.getPaddingRight();
        int padBottom = node.getPaddingBottom();
        int padLeft = node.getPaddingLeft();
        int innerWidth = Math.max(0, outerWidth - padLeft - padRight);

        int columns = spec.isAutoColumns()
                ? Math.max(1, (innerWidth + spec.gapX()) / (spec.cellWidth() + spec.gapX()))
                : spec.columns();

        List<SceneNode> children = node.__getChildren();
        int count = 0;
        for (SceneNode child : children) {
            if (child.getCachedLayout() instanceof LayoutBox) {
                count++;
            }
        }
        int rowCount = columns <= 0 ? 0 : (count + columns - 1) / columns;

        // ===== 步骤 B：行高汇总 + 主轴/交叉轴起点 =====
        // 固定 cellHeight 时行高恒定；按内容时行高取该行子节点自然高的最大值。
        int[] rowHeights = new int[rowCount];
        if (!spec.isContentRows()) {
            for (int r = 0; r < rowCount; r++) {
                rowHeights[r] = spec.cellHeight();
            }
        } else {
            int index = 0;
            for (SceneNode child : children) {
                LayoutBox cb = (LayoutBox) child.getCachedLayout();
                if (cb == null) {
                    continue;
                }
                int r = index / columns;
                if (cb.getHeight() > rowHeights[r]) {
                    rowHeights[r] = cb.getHeight();
                }
                index++;
            }
        }

        int blockHeight = 0;
        for (int r = 0; r < rowCount; r++) {
            if (r > 0) {
                blockHeight += spec.gapY();
            }
            blockHeight += rowHeights[r];
        }
        int availHeight = Math.max(0, rootFinalHeight - padTop - padBottom);
        int mainStart;
        switch (spec.effectiveMainAxisAlign()) {
            case CENTER:
                mainStart = Math.max(0, (availHeight - blockHeight) / 2);
                break;
            case END:
                mainStart = Math.max(0, availHeight - blockHeight);
                break;
            case START:
            default:
                mainStart = 0;
                break;
        }

        int blockWidth = columns * spec.cellWidth() + (columns - 1) * spec.gapX();
        int crossStart;
        switch (spec.effectiveCrossAxisAlign()) {
            case CENTER:
                crossStart = Math.max(0, (innerWidth - blockWidth) / 2);
                break;
            case END:
                crossStart = Math.max(0, innerWidth - blockWidth);
                break;
            case START:
            default:
                crossStart = 0;
                break;
        }

        // 每行 y 起点（含 gapY 累进）
        int[] rowY = new int[rowCount];
        int cursor = padTop + mainStart;
        for (int r = 0; r < rowCount; r++) {
            rowY[r] = cursor;
            cursor += rowHeights[r] + spec.gapY();
        }

        // ===== 步骤 C：定位子节点（几何闸门 + markGeometryDirty，绝不向下递归） =====
        int index = 0;
        for (SceneNode child : children) {
            LayoutBox cb = (LayoutBox) child.getCachedLayout();
            if (cb == null) {
                continue;
            }
            int r = index / columns;
            int c = index % columns;
            int nx = padLeft + crossStart + c * (spec.cellWidth() + spec.gapX());
            int ny = rowY[r];
            int nw = spec.cellWidth();
            int nh = spec.isContentRows() ? cb.getHeight() : spec.cellHeight();
            LayoutBox newBox = new LayoutBox(nx, ny, nw, nh);
            if (!newBox.equals(cb)) {
                child.setCachedLayout(newBox);
                child.markGeometryDirty();
                if (cb == null || newBox.getWidth() != cb.getWidth() || newBox.getHeight() != cb.getHeight()) {
                    child.markSelfPaint();
                }
            }
            index++;
        }

        // ===== 步骤 D：写容器自身 LayoutBox（复用引擎锁定值，与 FlexLayouter 步骤 D 一致） =====
        boolean selfGeoBubble = false;
        boolean selfPaintBubble = false;
        LayoutBox newSelfBox = new LayoutBox(0, 0, outerWidth, rootFinalHeight);
        LayoutBox oldSelfBox = (LayoutBox) node.getCachedLayout();
        if (!newSelfBox.equals(oldSelfBox)) {
            node.setCachedLayout(newSelfBox);
            selfGeoBubble = node.__setSelfGeometryDirtyNoBubble();
            if (oldSelfBox == null || newSelfBox.getWidth() != oldSelfBox.getWidth()
                    || newSelfBox.getHeight() != oldSelfBox.getHeight()) {
                node.__setSelfPaintDirtyNoBubble();
                selfPaintBubble = true;
            }
        }
        return new FlexLayouter.SelfBubbleSignal(selfGeoBubble, selfPaintBubble);
    }
}
