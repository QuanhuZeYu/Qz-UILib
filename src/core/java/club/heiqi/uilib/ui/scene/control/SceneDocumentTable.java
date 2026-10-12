package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.DocumentContent;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TableContent;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 共同表格按本代内容求列宽和行高；超出父宽度的不可拆分内容仅在表格内滚动。 */
final class SceneDocumentTable implements SceneControl {
    private final ReadableSignal<DocumentContent.Block> source;
    private int offset, maximum;
    private ChildPlacements prepared;
    // 仅在本次测量到排列之间复用；arrange后及卸载时释放，不保活旧测量批次。
    private GridRequest request;

    private SceneDocumentTable(ReadableSignal<DocumentContent.Block> source) {
        this.source = source;
    }

    static SceneNode create(
            ScenePageContext context,
            ReadableSignal<DocumentContent.Block> source,
            ReadableSignal<SceneTheme> theme,
            DocumentResources resources,
            Consumer<String> activate) {
        SceneDocumentTable control = new SceneDocumentTable(source);
        SceneNode table =
                new SceneNode().control(control).clip(true).size(Size.fill(1), Size.natural());
        table.bind(
                (runtime, node, owner) ->
                        new BindingActivation() {
                            public void refresh() {
                                control.request = null;
                            }

                            public void close() {
                                control.request = null;
                                control.prepared = null;
                            }
                        });
        table.bind(
                NodeBinding.value(
                        theme, (node, value) -> node.gap(1).background(value.disabledText)));
        table.bind(
                NodeBinding.repeat(
                        () -> cells(source.get().table),
                        cell -> cell,
                        cell -> {
                            SceneNode cellNode =
                                    new SceneNode()
                                            .control(new Cell())
                                            .size(Size.natural(), Size.natural());
                            cellNode.bind(
                                    NodeBinding.value(
                                            theme,
                                            (node, value) ->
                                                    node.padding(value.padding)
                                                            .gap(value.gap)
                                                            .background(
                                                                    cell.get().header
                                                                            ? value.button
                                                                            : value.panel)));
                            ReadableSignal<SceneTheme> cellTheme =
                                    () -> {
                                        SceneTheme current = theme.get();
                                        if (!cell.get().header) return current;
                                        TextStyle base = current.textStyle;
                                        return current.withTextStyle(
                                                base.withFontStyle(new FontStyle(700, base.fontStyle.posture)));
                                    };
                            cellNode.bind(
                                    NodeBinding.repeat(
                                            () -> cell.get().content(),
                                            block -> block,
                                            block -> {
                                                SceneNode content =
                                                        SceneDocumentBlocks.create(
                                                                context, block, cellTheme,
                                                                resources, activate);
                                                SceneDocument.alignParagraph(
                                                        content, cell.get().alignment);
                                                return content;
                                            }));
                            return cellNode;
                        }));
        return table;
    }

    private static List<TableContent.Cell> cells(TableContent table) {
        List<TableContent.Cell> cells = new ArrayList<>();
        for (TableContent.Row row : table.rows()) cells.addAll(row.cells());
        return cells;
    }

    private static final class Grid {
        final int[] columns, rows;
        final int width, height;

        Grid(int[] columns, int[] rows, int gap) {
            this.columns = columns;
            this.rows = rows;
            int w = Math.max(0, columns.length - 1) * gap, h = Math.max(0, rows.length - 1) * gap;
            for (int v : columns) w = Math.addExact(w, v);
            for (int v : rows) h = Math.addExact(h, v);
            width = w;
            height = h;
        }
    }

    private static final class GridRequest {
        final TableContent table;
        final SceneChildMeasurer children;
        final int gap;
        final int[] minimum, natural;
        int solvedWidth = -1;
        Grid solved;

        GridRequest(TableContent table, SceneChildMeasurer children, int gap) {
            this.table = table;
            this.children = children;
            this.gap = gap;
            minimum = new int[table.columnCount()];
            natural = new int[table.columnCount()];
        }
    }

    private GridRequest intrinsic(SceneNode node, SceneChildMeasurer children) {
        TableContent table = source.get().table;
        int gap = node.gap();
        if (request != null
                && request.table == table
                && request.children == children
                && request.gap == gap) return request;
        GridRequest current = new GridRequest(table, children, gap);
        int index = 0;
        // 同一正式子测量的NORMAL零宽请求暴露不可拆分词/图像/公式，MAX请求给自然宽度。
        for (TableContent.Row row : table.rows()) {
            for (int col = 0; col < row.cells().size(); col++) {
                SceneNode cell = node.children().get(index++);
                MeasuredSize min =
                        children.measureChild(cell, new Constraints(0, Integer.MAX_VALUE));
                MeasuredSize preferred =
                        children.measureChild(
                                cell, new Constraints(Integer.MAX_VALUE, Integer.MAX_VALUE));
                current.minimum[col] = Math.max(current.minimum[col], min.width);
                current.natural[col] =
                        Math.max(current.natural[col], Math.max(min.width, preferred.width));
            }
        }
        request = current;
        return current;
    }

    private Grid grid(SceneNode node, int availableWidth, SceneChildMeasurer children) {
        GridRequest current = intrinsic(node, children);
        if (current.solved != null && current.solvedWidth == availableWidth) return current.solved;
        TableContent table = current.table;
        int count = table.columnCount(), gap = current.gap;
        int[] minimum = current.minimum, natural = current.natural;
        long minTotal = 0, naturalTotal = 0;
        for (int col = 0; col < count; col++) {
            minTotal += minimum[col];
            naturalTotal += natural[col];
        }
        int budget = Math.max(0, availableWidth - Math.max(0, count - 1) * gap);
        int[] widths = minimum.clone();
        if (naturalTotal <= budget && count > 0) {
            widths = natural.clone();
            // 表格视口占据父分配宽度；余宽分给各列，不绘制裸露的网格背景。
            long remaining = budget - naturalTotal;
            for (int col = 0; col < count; col++)
                widths[col] += (int) (remaining / count + (col < remaining % count ? 1 : 0));
        } else if (minTotal < budget && naturalTotal > minTotal) {
            long extra = budget - minTotal, flexible = naturalTotal - minTotal;
            long used = minTotal;
            for (int col = 0; col < count; col++) {
                widths[col] += (int) (extra * (natural[col] - (long) minimum[col]) / flexible);
                used += widths[col] - minimum[col];
            }
            for (int col = 0; used < budget && col < count; col++)
                if (widths[col] < natural[col]) {
                    widths[col]++;
                    used++;
                }
        }
        int[] heights = new int[table.rows().size()];
        int index = 0;
        for (int row = 0; row < heights.length; row++) {
            List<TableContent.Cell> cells = table.rows().get(row).cells();
            for (int col = 0; col < cells.size(); col++) {
                MeasuredSize size =
                        children.measureChild(
                                node.children().get(index++),
                                new Constraints(widths[col], Integer.MAX_VALUE));
                heights[row] = Math.max(heights[row], size.height);
            }
        }
        current.solvedWidth = availableWidth;
        current.solved = new Grid(widths, heights, gap);
        return current.solved;
    }

    @Override
    public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) {
        return new MeasuredSize(0, 0);
    }

    @Override
    public MeasuredSize measure(
            SceneNode node,
            Constraints limits,
            SceneTextMeasurer text,
            SceneChildMeasurer children) {
        Grid grid = grid(node, limits.availableWidth, children);
        // 返回视口宽度，不让内容自然宽度撑大整篇；网格宽度只用于同代排列及滚动范围。
        return new MeasuredSize(Math.min(limits.availableWidth, grid.width), grid.height);
    }

    @Override
    public TextLayout presentText(
            SceneNode node,
            TextLayout full,
            int width,
            int height,
            SceneTextMeasurer text,
            SceneChildMeasurer children) {
        Grid grid = grid(node, width, children);
        maximum = Math.max(0, grid.width - width);
        offset = Math.min(offset, maximum);
        List<ChildPlacements.Entry> entries = new ArrayList<>();
        int index = 0, y = node.padding();
        for (int row = 0; row < grid.rows.length; row++) {
            int x = node.padding() - offset;
            for (int col = 0; col < source.get().table.rows().get(row).cells().size(); col++) {
                entries.add(
                        new ChildPlacements.Entry(
                                node.children().get(index++),
                                new LayoutBox(x, y, grid.columns[col], grid.rows[row])));
                x += grid.columns[col] + node.gap();
            }
            y += grid.rows[row] + node.gap();
        }
        prepared = new ChildPlacements(entries);
        return full;
    }

    @Override
    public ChildPlacements arrange(SceneNode node, LayoutBox box) {
        ChildPlacements result = prepared;
        prepared = null;
        request = null;
        return result;
    }

    @Override
    public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
        background(node, geometry, target);
    }

    private static void background(
            SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
        SceneGeometryView.Entry entry = geometry.get(node);
        if (entry != null && node.background() != 0)
            target.add(PaintCommand.rect(entry.box, entry.clip, node.background()));
    }

    @Override
    public boolean scrollableContent() {
        return true;
    }

    @Override
    public boolean scroll(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
        if (geometry.get(node) == null) return false;
        int next =
                (int)
                        Math.max(
                                0L,
                                Math.min(
                                        (long) maximum,
                                        offset - (long) event.delta * node.scrollStep()));
        if (next == offset) return false;
        offset = next;
        return true;
    }

    /** 单元格聚合实际子内容宽度，不用fill声明掩盖不可拆分原子项的最小宽度。 */
    private static final class Cell implements SceneControl {
        private ChildPlacements prepared;

        @Override
        public MeasuredSize measure(SceneNode node, Constraints limits, SceneTextMeasurer text) {
            return new MeasuredSize(0, 0);
        }

        @Override
        public MeasuredSize measure(
                SceneNode node,
                Constraints limits,
                SceneTextMeasurer text,
                SceneChildMeasurer children) {
            int width = 0, height = 0, visible = 0;
            for (SceneNode child : node.children())
                if (child.visible()) {
                    MeasuredSize measured =
                            children.measureChild(
                                    child,
                                    new Constraints(limits.availableWidth, Integer.MAX_VALUE));
                    width = Math.max(width, measured.width);
                    height += measured.height;
                    visible++;
                }
            return new MeasuredSize(width, height + Math.max(0, visible - 1) * node.gap());
        }

        @Override
        public TextLayout presentText(
                SceneNode node,
                TextLayout full,
                int width,
                int height,
                SceneTextMeasurer text,
                SceneChildMeasurer children) {
            List<ChildPlacements.Entry> entries = new ArrayList<>();
            int y = node.padding();
            for (SceneNode child : node.children())
                if (child.visible()) {
                    MeasuredSize measured =
                            children.measureChild(child, new Constraints(width, Integer.MAX_VALUE));
                    entries.add(
                            new ChildPlacements.Entry(
                                    child,
                                    new LayoutBox(node.padding(), y, width, measured.height)));
                    y += measured.height + node.gap();
                }
            prepared = new ChildPlacements(entries);
            return full;
        }

        @Override
        public ChildPlacements arrange(SceneNode node, LayoutBox box) {
            ChildPlacements result = prepared;
            prepared = null;
            return result;
        }

        @Override
        public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder target) {
            background(node, geometry, target);
        }
    }
}
