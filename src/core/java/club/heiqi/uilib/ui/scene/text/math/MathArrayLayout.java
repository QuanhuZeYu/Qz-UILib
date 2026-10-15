package club.heiqi.uilib.ui.scene.text.math;

import static club.heiqi.uilib.ui.scene.text.math.MathFont.Constant.*;

import club.heiqi.uilib.ui.scene.text.math.MathBox.Atom;
import club.heiqi.uilib.ui.scene.text.math.MathContent.ArraySpec;
import club.heiqi.uilib.ui.scene.text.math.MathContent.ColumnSpec;
import club.heiqi.uilib.ui.scene.text.math.MathContent.Kind;
import club.heiqi.uilib.ui.scene.text.math.MathContent.LineSpec;
import club.heiqi.uilib.ui.scene.text.math.MathContent.Node;
import club.heiqi.uilib.ui.scene.text.math.MathContent.RowSpec;

import java.util.ArrayList;
import java.util.List;

/** 本次测量事实先定位行列，再扁平化为同一有序绘制流。 */
final class MathArrayLayout {
    static MathBox layout(MathLayoutEngine.Session session, Node node, MathLayoutContext context) {
        ArraySpec spec = (ArraySpec) node.payload;
        if (spec.displayRequired && !context.display)
            throw new MathLayoutEngine.LayoutFailure(
                    "layout", node, "环境仅允许显示公式: " + spec.environment);
        session.font(context, node);
        // fixed array 给显式 cellStyle 包装 styling；解除数学 font/cramped，空 style 才继承父作用域。
        MathLayoutContext cellsContext =
                spec.cellStyle == null
                        ? context
                        : context.declaredStyle(MathLayoutEngine.style(spec.cellStyle));
        Measurement measured = measure(session, node, context, cellsContext, spec);
        RowCoordinates rows = positionRows(session, node, context, spec, measured);
        ColumnCoordinates columns = positionColumns(context, cellsContext, spec, measured);
        return compose(session, node, context, spec, measured, rows, columns);
    }

    // 测量持有原数组；插入项先求盒，每行的 cell（含嵌套编号）之后立即求该行 tag。
    // 自动编号在此消费一次，坐标和合成阶段只消费这些盒与包络。
    private static Measurement measure(
            MathLayoutEngine.Session session,
            Node node,
            MathLayoutContext context,
            MathLayoutContext cellsContext,
            ArraySpec spec) {
        int columns = 0;
        for (RowSpec row : spec.rows) columns = Math.max(columns, row.cellCount);
        List<ColumnSpec> descriptions = new ArrayList<ColumnSpec>(spec.columns);
        int declared = 0;
        for (ColumnSpec col : descriptions) if (col.type == ColumnSpec.Type.ALIGN) declared++;
        columns = Math.max(columns, declared);
        for (int i = declared; i < columns; i++)
            descriptions.add(
                    new ColumnSpec(ColumnSpec.Type.ALIGN, "c", Double.NaN, Double.NaN, -1));
        int rows = spec.rows.size();
        if (spec.singleRow && rows > 1)
            throw new MathLayoutEngine.LayoutFailure("layout", node, "单行环境收到多行结构");
        MathBox[][] cells = new MathBox[rows][columns];
        MathBox[] inserts = new MathBox[descriptions.size()];
        MathBox[] tags = new MathBox[rows];
        float[] widths = new float[columns], heights = new float[rows], depths = new float[rows];
        float em = context.em;
        float baselineSkip =
                spec.separation.equals("CD") ? 3 * session.xHeight(context, node) : 1.2f * em;
        float strutHeight = (float) (.7 * spec.stretch * baselineSkip),
                strutDepth = (float) (.3 * spec.stretch * baselineSkip);
        for (int d = 0; d < descriptions.size(); d++) {
            ColumnSpec col = descriptions.get(d);
            if (col.type == ColumnSpec.Type.INSERT)
                inserts[d] = session.box(node.children.get(col.insertChild), cellsContext);
        }
        float tagWidth = 0;
        for (int r = 0; r < rows; r++) {
            RowSpec row = spec.rows.get(r);
            heights[r] = strutHeight;
            depths[r] = strutDepth;
            for (int c = 0; c < columns; c++) {
                MathBox cell =
                        c < row.cellCount
                                ? session.box(node.children.get(row.firstCell + c), cellsContext)
                                : new MathBox();
                cells[r][c] = cell;
                widths[c] = Math.max(widths[c], cell.width);
                heights[r] = Math.max(heights[r], cell.ascent);
                depths[r] = Math.max(depths[r], cell.descent);
            }
            for (MathBox insert : inserts)
                if (insert != null) {
                    heights[r] = Math.max(heights[r], insert.ascent);
                    depths[r] = Math.max(depths[r], insert.descent);
                }
            if (row.tagChild >= 0)
                tags[r] =
                        session.box(node.children.get(row.tagChild), context.style(MathStyle.TEXT));
            else if (row.automaticTag)
                tags[r] = session.tagNumber(node, context, session.consumeEquationNumber(node));
            if (tags[r] != null) {
                tagWidth = Math.max(tagWidth, tags[r].width);
                heights[r] = Math.max(heights[r], tags[r].ascent);
                depths[r] = Math.max(depths[r], tags[r].descent);
            }
            float gap = session.dimension(row.gapAfter, context, node);
            if (gap > 0) depths[r] = Math.max(depths[r], strutDepth + gap);
            if (spec.addJot) depths[r] += .3f * em;
            // fixed array 的负 gap 直接改变下一基线；允许用户显式重叠，实际墨迹仍由 MathBox.add 合入包络。
            if (gap < 0) depths[r] += gap;
        }
        return new Measurement(descriptions, cells, inserts, tags, widths, heights, depths, tagWidth);
    }

    private static RowCoordinates positionRows(
            MathLayoutEngine.Session session,
            Node node,
            MathLayoutContext context,
            ArraySpec spec,
            Measurement measured) {
        int rows = measured.cells.length;
        float em = context.em, rule = .04f * em;
        float[] baselines = new float[rows];
        List<Float> horizontalY = new ArrayList<Float>();
        List<Boolean> horizontalDashed = new ArrayList<Boolean>();
        float total = 0;
        for (int r = 0; r <= rows; r++) {
            boolean first = true;
            for (LineSpec line : spec.lines)
                if (line.beforeRow == r) {
                    if (!first) total += .25f * em;
                    // 线厚也进入净空，不能覆盖紧邻的高 Item。
                    horizontalY.add(total);
                    horizontalDashed.add(line.dashed);
                    total += rule;
                    first = false;
                }
            if (r < rows) {
                total += measured.heights[r];
                baselines[r] = total;
                total += measured.depths[r];
            }
        }
        float offset = total / 2 + session.c(context, node, AXIS_HEIGHT);
        return new RowCoordinates(baselines, horizontalY, horizontalDashed, total, offset);
    }

    private static ColumnCoordinates positionColumns(
            MathLayoutContext context,
            MathLayoutContext cellsContext,
            ArraySpec spec,
            Measurement measured) {
        int columns = measured.widths.length;
        List<ColumnSpec> descriptions = measured.descriptions;
        float em = context.em;
        float defaultGap =
                spec.separation.equals("small")
                        ? .2778f * cellsContext.em
                        : spec.separation.equals("subarray") ? 0 : .5f * em;
        float[] columnX = new float[columns], insertX = new float[descriptions.size()];
        List<Float> verticalX = new ArrayList<Float>();
        List<Boolean> verticalDashed = new ArrayList<Boolean>();
        int c = 0;
        float cursor = 0;
        boolean lastSeparator = false;
        for (int d = 0; d < descriptions.size(); d++) {
            ColumnSpec col = descriptions.get(d);
            if (col.type == ColumnSpec.Type.SOLID || col.type == ColumnSpec.Type.DASHED) {
                if (lastSeparator) cursor += .2f * em;
                verticalX.add(cursor);
                verticalDashed.add(col.type == ColumnSpec.Type.DASHED);
                lastSeparator = true;
                continue;
            }
            lastSeparator = false;
            if (col.type == ColumnSpec.Type.INSERT) {
                cursor += gap(col.preGap, 0, em);
                insertX[d] = cursor;
                cursor += measured.inserts[d].width + gap(col.postGap, 0, em);
                continue;
            }
            if (c > 0 || spec.outerSpacing) cursor += gap(col.preGap, defaultGap, em);
            columnX[c] = cursor;
            cursor += measured.widths[c];
            if (c < columns - 1 || spec.outerSpacing) cursor += gap(col.postGap, defaultGap, em);
            c++;
        }
        return new ColumnCoordinates(columnX, insertX, verticalX, verticalDashed, cursor);
    }

    private static MathBox compose(
            MathLayoutEngine.Session session,
            Node node,
            MathLayoutContext context,
            ArraySpec spec,
            Measurement measured,
            RowCoordinates rows,
            ColumnCoordinates columns) {
        float em = context.em, rule = .04f * em;
        float offset = rows.offset, total = rows.total;
        MathBox result = new MathBox();
        result.ascent = Math.max(0, offset);
        result.descent = Math.max(0, total - offset);
        for (int r = 0; r < measured.cells.length; r++) {
            int c = 0;
            for (int d = 0; d < measured.descriptions.size(); d++) {
                ColumnSpec col = measured.descriptions.get(d);
                if (col.type == ColumnSpec.Type.INSERT)
                    result.add(measured.inserts[d], columns.insertX[d], rows.baselines[r] - offset);
                else if (col.type == ColumnSpec.Type.ALIGN) {
                    MathBox cell = measured.cells[r][c];
                    float extra = measured.widths[c] - cell.width;
                    float shift =
                            "l".equals(col.align) ? 0 : "r".equals(col.align) ? extra : extra / 2;
                    result.add(cell, columns.columnX[c] + shift, rows.baselines[r] - offset);
                    c++;
                }
            }
            if (measured.tags[r] != null)
                result.add(
                        measured.tags[r],
                        columns.contentWidth + em + measured.tagWidth - measured.tags[r].width,
                        rows.baselines[r] - offset);
        }
        for (int i = 0; i < columns.verticalX.size(); i++)
            line(
                    result,
                    columns.verticalX.get(i),
                    -offset,
                    columns.verticalX.get(i),
                    total - offset,
                    rule,
                    columns.verticalDashed.get(i),
                    em,
                    context,
                    node);
        for (int i = 0; i < rows.horizontalY.size(); i++)
            line(
                    result,
                    0,
                    rows.horizontalY.get(i) - offset,
                    columns.contentWidth,
                    rows.horizontalY.get(i) - offset,
                    rule,
                    rows.horizontalDashed.get(i),
                    em,
                    context,
                    node);
        result.width = columns.contentWidth + (measured.tagWidth > 0 ? em + measured.tagWidth : 0);
        result.atom = Atom.ORD;
        if (!columns.verticalX.isEmpty()) {
            float left = 0, right = result.width;
            for (float x : columns.verticalX) {
                left = Math.min(left, x - rule / 2);
                right = Math.max(right, x + rule / 2);
            }
            if (left < 0 || right > result.width) result = result.shifted(-left, 0, right - left);
        }
        if (spec.leftDelimiter != null || spec.rightDelimiter != null) {
            float target = session.delimiterTarget(result, context, node);
            MathBox left = fence(session, node, context, spec.leftDelimiter, target, Atom.OPEN);
            MathBox right = fence(session, node, context, spec.rightDelimiter, target, Atom.CLOSE);
            MathBox enclosed = new MathBox();
            enclosed.add(left, 0, 0);
            enclosed.add(result, left.width, 0);
            enclosed.add(right, left.width + result.width, 0);
            enclosed.width = left.width + result.width + right.width;
            enclosed.atom = Atom.INNER;
            result = enclosed;
        }
        return result;
    }

    /** 只在本次求盒内交接原有测量数组，不保存 Session 或增加测量入口。 */
    private static final class Measurement {
        final List<ColumnSpec> descriptions;
        final MathBox[][] cells;
        final MathBox[] inserts, tags;
        final float[] widths, heights, depths;
        final float tagWidth;

        Measurement(
                List<ColumnSpec> descriptions,
                MathBox[][] cells,
                MathBox[] inserts,
                MathBox[] tags,
                float[] widths,
                float[] heights,
                float[] depths,
                float tagWidth) {
            this.descriptions = descriptions;
            this.cells = cells;
            this.inserts = inserts;
            this.tags = tags;
            this.widths = widths;
            this.heights = heights;
            this.depths = depths;
            this.tagWidth = tagWidth;
        }
    }

    /** 含横线净空的基线坐标；offset 将顶部坐标移到本盒基线。 */
    private static final class RowCoordinates {
        final float[] baselines;
        final List<Float> horizontalY;
        final List<Boolean> horizontalDashed;
        final float total, offset;

        RowCoordinates(
                float[] baselines,
                List<Float> horizontalY,
                List<Boolean> horizontalDashed,
                float total,
                float offset) {
            this.baselines = baselines;
            this.horizontalY = horizontalY;
            this.horizontalDashed = horizontalDashed;
            this.total = total;
            this.offset = offset;
        }
    }

    /** 列、插入项和竖线共享横坐标，contentWidth 尚不含行标签与外侧定界符。 */
    private static final class ColumnCoordinates {
        final float[] columnX, insertX;
        final List<Float> verticalX;
        final List<Boolean> verticalDashed;
        final float contentWidth;

        ColumnCoordinates(
                float[] columnX,
                float[] insertX,
                List<Float> verticalX,
                List<Boolean> verticalDashed,
                float contentWidth) {
            this.columnX = columnX;
            this.insertX = insertX;
            this.verticalX = verticalX;
            this.verticalDashed = verticalDashed;
            this.contentWidth = contentWidth;
        }
    }

    private static float gap(double value, float fallback, float em) {
        return Double.isNaN(value) ? fallback : (float) value * em;
    }

    private static MathBox fence(
            MathLayoutEngine.Session session,
            Node source,
            MathLayoutContext context,
            String value,
            float target,
            Atom atom) {
        if (value == null) return new MathBox();
        return session.delimiter(
                new Node(Kind.DELIMITER, source.start, source.end, value, null, null, null, null),
                context,
                target,
                atom);
    }

    private static void line(
            MathBox box,
            float x1,
            float y1,
            float x2,
            float y2,
            float width,
            boolean dashed,
            float em,
            MathLayoutContext context,
            Node source) {
        float length = Math.abs(x2 - x1) + Math.abs(y2 - y1);
        if (!dashed) {
            if (x1 == x2)
                box.rule(
                        x1 - width / 2,
                        y1,
                        width,
                        y2 - y1,
                        context.textStyle.color,
                        source.start,
                        source.end);
            else
                box.rule(x1, y1, x2 - x1, width, context.textStyle.color, source.start, source.end);
            return;
        }
        float dash = .15f * em, step = .3f * em;
        for (float start = 0; start < length; start += step) {
            float end = Math.min(length, start + dash);
            if (x1 == x2)
                box.rule(
                        x1 - width / 2,
                        y1 + start,
                        width,
                        end - start,
                        context.textStyle.color,
                        source.start,
                        source.end);
            else
                box.rule(
                        x1 + start,
                        y1,
                        end - start,
                        width,
                        context.textStyle.color,
                        source.start,
                        source.end);
        }
    }
}
