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

/** typed 行列事实一次求盒，随后立即扁平化为同一有序绘制流。 */
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
        float[] widths = new float[columns],
                heights = new float[rows],
                depths = new float[rows],
                baselines = new float[rows];
        float em = context.em, rule = .04f * em;
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
                total += heights[r];
                baselines[r] = total;
                total += depths[r];
            }
        }
        float offset = total / 2 + session.c(context, node, AXIS_HEIGHT);
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
                cursor += inserts[d].width + gap(col.postGap, 0, em);
                continue;
            }
            if (c > 0 || spec.outerSpacing) cursor += gap(col.preGap, defaultGap, em);
            columnX[c] = cursor;
            cursor += widths[c];
            if (c < columns - 1 || spec.outerSpacing) cursor += gap(col.postGap, defaultGap, em);
            c++;
        }
        MathBox result = new MathBox();
        result.ascent = Math.max(0, offset);
        result.descent = Math.max(0, total - offset);
        for (int r = 0; r < rows; r++) {
            c = 0;
            for (int d = 0; d < descriptions.size(); d++) {
                ColumnSpec col = descriptions.get(d);
                if (col.type == ColumnSpec.Type.INSERT)
                    result.add(inserts[d], insertX[d], baselines[r] - offset);
                else if (col.type == ColumnSpec.Type.ALIGN) {
                    MathBox cell = cells[r][c];
                    float extra = widths[c] - cell.width;
                    float shift =
                            "l".equals(col.align) ? 0 : "r".equals(col.align) ? extra : extra / 2;
                    result.add(cell, columnX[c] + shift, baselines[r] - offset);
                    c++;
                }
            }
            if (tags[r] != null)
                result.add(tags[r], cursor + em + tagWidth - tags[r].width, baselines[r] - offset);
        }
        for (int i = 0; i < verticalX.size(); i++)
            line(
                    result,
                    verticalX.get(i),
                    -offset,
                    verticalX.get(i),
                    total - offset,
                    rule,
                    verticalDashed.get(i),
                    em,
                    context,
                    node);
        for (int i = 0; i < horizontalY.size(); i++)
            line(
                    result,
                    0,
                    horizontalY.get(i) - offset,
                    cursor,
                    horizontalY.get(i) - offset,
                    rule,
                    horizontalDashed.get(i),
                    em,
                    context,
                    node);
        result.width = cursor + (tagWidth > 0 ? em + tagWidth : 0);
        result.atom = Atom.ORD;
        if (!verticalX.isEmpty()) {
            float left = 0, right = result.width;
            for (float x : verticalX) {
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
