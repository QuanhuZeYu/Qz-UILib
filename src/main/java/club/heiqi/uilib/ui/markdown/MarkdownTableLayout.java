package club.heiqi.uilib.ui.markdown;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.markdown.MarkdownLineLayout.VisualLine;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument.TableUnit;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.font.layout.markdown.MarkdownTableModel;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;

/** 表格像素 pass；文本、公式、链接均复用既有行布局，零语法识别。 */
final class MarkdownTableLayout {
    private MarkdownTableLayout() {}

    static final class Result {
        final List<PaintCommand> commands;
        final int width;
        final int height;
        final int[] columnWidths;
        final int[] rowHeights;

        Result(List<PaintCommand> commands, int width, int height, int[] columns, int[] rows) {
            this.commands = Collections.unmodifiableList(commands);
            this.width = width;
            this.height = height;
            this.columnWidths = columns;
            this.rowHeights = rows;
        }
    }

    static Result layout(TableUnit unit, TextLayoutService measurer, int maxWidthPx, int font) {
        MarkdownTableModel model = unit.getModel();
        List<MarkdownTableModel.Row> rows = new ArrayList<MarkdownTableModel.Row>();
        rows.add(model.getHeader());
        rows.addAll(model.getRows());
        int columns = model.getAlignments().size();
        int px = unit.getPaddingXPx();
        int py = unit.getPaddingYPx();
        int border = unit.getBorderPx();
        // 空上下文只向既有布局器询问列表正文列；不把它画成一行。
        MarkdownLayoutLine context = MarkdownLineLayout.layoutLines(
                Collections.singletonList(unit.getContext()), measurer, maxWidthPx, font).get(0);
        int inset = context.getLeftInsetPx();
        int[] preferred = new int[columns];
        int[] minimum = new int[columns];
        List<List<List<TextSegment>>> cells = new ArrayList<List<List<TextSegment>>>();
        // 1. 完整度量所有 cell，再决定列宽。最小宽是既有换行器不可拆的码点/公式原子。
        for (MarkdownTableModel.Row row : rows) {
            List<List<TextSegment>> rowCells = new ArrayList<List<TextSegment>>();
            for (int c = 0; c < columns; c++) {
                List<TextSegment> segments = row.getCells().get(c).getSegments();
                rowCells.add(segments);
                preferred[c] = Math.max(preferred[c], MarkdownLineLayout.lineWidthPx(segments, measurer, font));
                minimum[c] = Math.max(minimum[c], atomicWidth(segments, measurer, font));
            }
            cells.add(rowCells);
        }
        int decorations = border * (columns + 1) + 2 * px * columns;
        int available = maxWidthPx <= 0 ? Integer.MAX_VALUE : Math.max(0, maxWidthPx - inset - decorations);
        int[] widths = allocate(minimum, preferred, available);
        int tableWidth = decorations;
        for (int width : widths) {
            tableWidth += width;
        }
        // 2. 列分配完成才换行；整行高取所有 cell 的实际行高最大值。
        List<List<List<VisualLine>>> wrapped = new ArrayList<List<List<VisualLine>>>();
        int[] heights = new int[rows.size()];
        int tableHeight = border;
        for (int r = 0; r < rows.size(); r++) {
            List<List<VisualLine>> wrappedRow = new ArrayList<List<VisualLine>>();
            int contentHeight = 0;
            for (int c = 0; c < columns; c++) {
                List<List<TextSegment>> lines = MarkdownLineLayout.wrapCell(cells.get(r).get(c), measurer, widths[c], font);
                List<VisualLine> measured = new ArrayList<VisualLine>();
                wrappedRow.add(measured);
                int height = 0;
                for (List<TextSegment> line : lines) {
                    VisualLine cellLine = new VisualLine(line, measurer, font);
                    measured.add(cellLine);
                    height += cellLine.height;
                }
                contentHeight = Math.max(contentHeight, height);
            }
            wrapped.add(wrappedRow);
            heights[r] = contentHeight + 2 * py;
            tableHeight += heights[r] + border;
        }
        // 3. 行高落定后才定位边框、文字及链接命中区。
        List<PaintCommand> out = new ArrayList<PaintCommand>();
        for (PaintCommand accent : MarkdownLineLayout.blockCommands(
                Collections.singletonList(context), measurer, maxWidthPx, font)) {
            out.add(PaintCommand.background(accent.getLeft(), 0, accent.getRight(), tableHeight,
                    context.getAccentArgb()));
        }
        out.add(PaintCommand.background(inset + border, border, inset + tableWidth - border,
                border + heights[0], unit.getHeaderArgb()));
        int y = 0;
        for (int r = 0; r <= rows.size(); r++) {
            out.add(PaintCommand.background(inset, y, inset + tableWidth, y + border, unit.getBorderArgb()));
            if (r < rows.size()) {
                y += heights[r] + border;
            }
        }
        int x = inset;
        for (int c = 0; c <= columns; c++) {
            out.add(PaintCommand.background(x, 0, x + border, tableHeight, unit.getBorderArgb()));
            if (c < columns) {
                x += widths[c] + 2 * px + border;
            }
        }
        y = border;
        for (int r = 0; r < rows.size(); r++) {
            x = inset + border + px;
            for (int c = 0; c < columns; c++) {
                int lineY = y + py;
                for (VisualLine measured : wrapped.get(r).get(c)) {
                    List<TextSegment> line = measured.segments;
                    int height = measured.height;
                    int textTop = lineY + measured.textOffsetY;
                    int slack = Math.max(0, widths[c] - measured.width);
                    MarkdownTableModel.Alignment alignment = model.getAlignments().get(c);
                    int lineX = x + (alignment == MarkdownTableModel.Alignment.RIGHT ? slack
                            : alignment == MarkdownTableModel.Alignment.CENTER ? slack / 2 : 0);
                    if (!line.isEmpty()) {
                        out.add(PaintCommand.segments(line, lineX, textTop, Math.max(1, font)));
                        MarkdownLineLayout.appendLinkRegions(out, line, measurer, font, textTop,
                                measured.textHeight, lineX, lineY, height);
                    }
                    lineY += height;
                }
                x += widths[c] + 2 * px + border;
            }
            y += heights[r] + border;
        }
        return new Result(out, inset + tableWidth, tableHeight, widths, heights);
    }

    private static int atomicWidth(List<TextSegment> segments, TextLayoutService measurer, int font) {
        double widest = 1;
        for (TextSegment segment : segments) {
            if (segment.isLatex()) {
                widest = Math.max(widest, measurer.getSegmentWidth(segment, Math.max(1, font)));
            } else {
                String text = segment.getText();
                for (int i = 0; i < text.length();) {
                    int cp = text.codePointAt(i);
                    widest = Math.max(widest, measurer.resolveAdvance(cp, segment.getStyle(), font));
                    i += Character.charCount(cp);
                }
            }
        }
        return (int) Math.ceil(widest);
    }

    /**
     * 保留不可拆原子的最小宽，剩余预算在未达到 intrinsic 宽的列之间均分。
     * 短列达到 natural 宽后退出，余量交给长列；不按长文本长度挤压短列。
     * 极窄容器容不下原子/边框时保留最小宽并报告真实溢出宽，不缩字、不拆公式。
     */
    private static int[] allocate(int[] minimum, int[] preferred, int available) {
        int[] out = minimum.clone();
        long minimumSum = 0;
        long growth = 0;
        for (int c = 0; c < out.length; c++) {
            preferred[c] = Math.max(minimum[c], preferred[c]);
            minimumSum += minimum[c];
            growth += preferred[c] - minimum[c];
        }
        long budget = Math.min(growth, Math.max(0L, (long) available - minimumSum));
        while (budget > 0) {
            int active = 0;
            for (int c = 0; c < out.length; c++) {
                if (out[c] < preferred[c]) { active++; }
            }
            long share = Math.max(1L, budget / active);
            // 每轮封顶至少一列，或均分后只余不足 active 个像素；后者下一轮即耗尽。
            // 不逐像素试探，循环次数受列数控制。整数余数按文档列序发放。
            for (int c = 0; c < out.length && budget > 0; c++) {
                int extra = (int) Math.min(preferred[c] - out[c], Math.min(share, budget));
                out[c] += extra;
                budget -= extra;
            }
        }
        return out;
    }
}
