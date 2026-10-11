package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 已整形文字的纯投影；保留完整源文、源映射和诊断，不再整形业务文案。 */
final class SceneTextPresentation {
    private SceneTextPresentation() {}

    static TextLayout fit(
            TextLayout full,
            int width,
            int height,
            SceneTextPolicy policy,
            SceneTextMeasurer measurer) {
        if (policy == SceneTextPolicy.OVERFLOW) return full;
        List<TextLayout.Line> visible = new ArrayList<>();
        for (TextLayout.Line line : full.lines()) {
            if (policy == SceneTextPolicy.ELLIPSIS_SINGLE_LINE && !visible.isEmpty()) break;
            if (bottom(line) > height) break;
            visible.add(line);
        }
        boolean hidden = visible.size() < full.lines().size();
        boolean wide = false;
        for (TextLayout.Line line : visible) wide |= right(line) > width;
        if (!hidden && !wide) return full;
        TextLayout mark =
                measurer.layout("…", full.style, Integer.MAX_VALUE, TextPolicy.SINGLE_LINE);
        for (int i = 0; i < visible.size(); i++) {
            TextLayout.Line line = visible.get(i);
            if (right(line) > width || hidden && i == visible.size() - 1) {
                TextLayout.Line shortened = shorten(line, mark, width, height);
                if (shortened == null) {
                    visible.subList(i, visible.size()).clear();
                    break;
                }
                visible.set(i, shortened);
            }
        }
        float w = 0, h = 0;
        for (TextLayout.Line line : visible) {
            w = Math.max(w, right(line));
            h = Math.max(h, bottom(line));
        }
        return new TextLayout(
                (int) Math.ceil(w),
                (int) Math.ceil(h),
                w,
                h,
                full.style,
                visible,
                full.logicalText,
                full.originalText,
                full.generation,
                full.diagnostics(),
                full.input);
    }

    static float right(TextLayout.Line line) {
        float right = line.advance;
        for (TextLayout.Glyph g : line.glyphs())
            right = Math.max(right, g.x + (g.inkBounds.known ? g.inkBounds.right : g.xAdvance));
        for (TextLayout.Decoration d : line.decorations()) right = Math.max(right, d.x + d.width);
        return Math.max(0, right);
    }

    static float bottom(TextLayout.Line line) {
        float bottom = Math.max(line.y + line.lineHeight, line.baseline + line.descent);
        for (TextLayout.Glyph g : line.glyphs())
            if (g.inkBounds.known) bottom = Math.max(bottom, g.y + g.inkBounds.bottom);
        for (TextLayout.Decoration d : line.decorations())
            bottom = Math.max(bottom, d.y + d.height);
        return bottom;
    }

    private static TextLayout.Line shorten(
            TextLayout.Line line, TextLayout mark, int width, int height) {
        if (mark.lines().isEmpty()) return null;
        TextLayout.Line dots = mark.lines().get(0);
        float left = 0;
        for (TextLayout.Glyph g : dots.glyphs())
            if (g.inkBounds.known) left = Math.min(left, g.x + g.inkBounds.left);
        float markWidth = right(dots) - left;
        float dy = line.baseline - dots.baseline;
        if (markWidth > width
                || bottom(dots) + dy > height
                || line.y + Math.max(line.lineHeight, dots.lineHeight) > height
                || line.baseline + Math.max(line.descent, dots.descent) > height) return null;
        List<TextLayout.Cluster> candidates = new ArrayList<>(line.clusters());
        candidates.sort(Comparator.comparingDouble(c -> c.x));
        float limit = width - markWidth;
        List<TextLayout.Cluster> keep = new ArrayList<>();
        float end = 0;
        for (TextLayout.Cluster c : candidates) {
            float edge = c.x + c.advance;
            for (int i = c.glyphIndexStart; i < c.glyphIndexEnd; i++) {
                TextLayout.Glyph g = line.glyphs().get(i);
                edge = Math.max(edge, g.x + (g.inkBounds.known ? g.inkBounds.right : g.xAdvance));
            }
            if (edge > limit) break;
            keep.add(c);
            end = Math.max(end, edge);
        }
        // 任何共享glyph的簇必须一起保留或删除，避免切开HB连字。
        boolean[] excluded = new boolean[line.glyphs().size()];
        for (int i = keep.size(); i < candidates.size(); i++) {
            TextLayout.Cluster c = candidates.get(i);
            for (int g = c.glyphIndexStart; g < c.glyphIndexEnd; g++) excluded[g] = true;
        }
        for (int i = keep.size() - 1; i >= 0; i--) {
            TextLayout.Cluster c = keep.get(i);
            boolean overlap = false;
            for (int g = c.glyphIndexStart; g < c.glyphIndexEnd; g++) overlap |= excluded[g];
            if (overlap) {
                keep.remove(i);
                for (int g = c.glyphIndexStart; g < c.glyphIndexEnd; g++) excluded[g] = true;
            }
        }
        end = 0;
        List<TextLayout.Glyph> glyphs = new ArrayList<>();
        List<TextLayout.Cluster> clusters = new ArrayList<>();
        List<TextLayout.Decoration> decorations = new ArrayList<>();
        for (TextLayout.Cluster c : keep) {
            int first = glyphs.size(), ci = clusters.size();
            for (int i = c.glyphIndexStart; i < c.glyphIndexEnd; i++) {
                TextLayout.Glyph g = line.glyphs().get(i);
                glyphs.add(
                        move(
                                g,
                                0,
                                0,
                                ci,
                                g.logicalStart,
                                g.logicalEnd,
                                g.sourceStart,
                                g.sourceEnd));
                end = Math.max(end, g.x + (g.inkBounds.known ? g.inkBounds.right : g.xAdvance));
            }
            end = Math.max(end, c.x + c.advance);
            clusters.add(
                    new TextLayout.Cluster(
                            c.logicalStart,
                            c.logicalEnd,
                            c.sourceStart,
                            c.sourceEnd,
                            first,
                            glyphs.size(),
                            c.x,
                            c.advance,
                            c.style,
                            c.rtl));
            for (TextLayout.Decoration d : line.decorations())
                if (d.logicalStart == c.logicalStart && d.logicalEnd == c.logicalEnd)
                    decorations.add(d);
        }
        int first = glyphs.size(), ci = clusters.size();
        for (TextLayout.Glyph g : dots.glyphs())
            glyphs.add(
                    move(
                            g,
                            end - left,
                            dy,
                            ci,
                            line.logicalEnd,
                            line.logicalEnd,
                            line.sourceEnd,
                            line.sourceEnd));
        clusters.add(
                new TextLayout.Cluster(
                        line.logicalEnd,
                        line.logicalEnd,
                        line.sourceEnd,
                        line.sourceEnd,
                        first,
                        glyphs.size(),
                        end,
                        markWidth,
                        mark.style,
                        line.paragraphRtl));
        for (TextLayout.Decoration d : dots.decorations())
            decorations.add(
                    new TextLayout.Decoration(
                            d.kind,
                            d.x + end - left,
                            d.y + dy,
                            d.width,
                            d.height,
                            d.color,
                            line.logicalEnd,
                            line.logicalEnd,
                            line.sourceEnd,
                            line.sourceEnd));
        // text仍描述源行；实际显示由glyph投影决定，合成省略号不写回业务文案。
        return new TextLayout.Line(
                line.text,
                line.x,
                line.y,
                line.logicalStart,
                line.logicalEnd,
                line.sourceStart,
                line.sourceEnd,
                end + markWidth,
                line.baseline,
                Math.max(line.ascent, dots.ascent),
                Math.max(line.descent, dots.descent),
                Math.max(line.lineHeight, dots.lineHeight),
                glyphs,
                clusters,
                decorations,
                line.paragraphRtl);
    }

    private static TextLayout.Glyph move(
            TextLayout.Glyph g,
            float dx,
            float dy,
            int ci,
            int start,
            int end,
            int sourceStart,
            int sourceEnd) {
        return new TextLayout.Glyph(
                g.source,
                g.size,
                g.glyphId,
                start,
                end,
                sourceStart,
                sourceEnd,
                g.x + dx,
                g.y + dy,
                g.xAdvance,
                g.yAdvance,
                g.color,
                g.replacement,
                g.inkBounds,
                g.logicalEm,
                g.rasterToLayout,
                g.rasterStyle,
                g.resolvedStyle,
                ci,
                g.paintClip);
    }
}
