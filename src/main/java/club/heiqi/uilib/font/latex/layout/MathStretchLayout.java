package club.heiqi.uilib.font.latex.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.latex.MathFontStyle;
import club.heiqi.uilib.font.latex.layout.MathGlyphConstruction.Assembly;
import club.heiqi.uilib.font.latex.layout.MathGlyphConstruction.Part;
import club.heiqi.uilib.font.latex.layout.MathGlyphConstruction.Variant;

/** 平台无关的原生数学字形选择与 OpenType assembly 几何。 */
final class MathStretchLayout {
    private static final int MAX_PARTS = 4096;

    private MathStretchLayout() {}

    static MathBox glyphBox(String text, MathGlyphRef ref, MathFontSupport support,
            int effectiveSizePx, float parentSize) {
        float scale = validate(text, ref, support, effectiveSizePx, parentSize);
        Bounds bounds = new Bounds();
        bounds.add(measure(support, ref, effectiveSizePx), 0, 0);
        return bounds.box(bounds.advance, Collections.singletonList(glyph(text, ref, 0, 0, scale)));
    }

    static MathBox stretch(String text, MathGlyphRef ref, MathFontSupport support, MathStretchAxis axis,
            float target, int effectiveSizePx, float parentSize) {
        float scale = validate(text, ref, support, effectiveSizePx, parentSize);
        if (axis == null || !Float.isFinite(target) || target <= 0) {
            throw new IllegalArgumentException("axis 必须非空，target 必须为有限正值");
        }
        MathGlyphConstruction construction = support.construction(ref, axis, effectiveSizePx);
        if (construction == null) { return glyphBox(text, ref, support, effectiveSizePx, parentSize); }
        List<Variant> variants = construction.getVariants();
        Assembly assembly = construction.getAssembly();
        if (variants.size() > MAX_PARTS || (assembly != null && assembly.getParts().size() > MAX_PARTS)) {
            throw new IllegalArgumentException("字体配方超过部件上限");
        }
        // construction 只校验配方内部同 face；还必须与调用方的根引用比对。
        for (Variant variant : variants) { sameFace(ref, variant.getGlyphRef()); }
        if (assembly != null) {
            for (Part part : assembly.getParts()) { sameFace(ref, part.getGlyphRef()); }
        }
        for (Variant variant : variants) {
            if (variant.getStretchAdvance() >= target) {
                return glyphBox(text, variant.getGlyphRef(), support, effectiveSizePx, parentSize);
            }
        }
        if (assembly == null) {
            MathGlyphRef largest = variants.isEmpty() ? ref : variants.get(variants.size() - 1).getGlyphRef();
            return glyphBox(text, largest, support, effectiveSizePx, parentSize);
        }
        List<Part> source = assembly.getParts();
        int extenders = 0;
        for (Part part : source) { if (part.isExtender()) { extenders++; } }
        int fixed = source.size() - extenders;
        double minimumOverlap = assembly.getMinConnectorOverlap();
        // 每个 extender 位置使用相同份数；从零份起寻找最小可达 target 的合法序列。
        for (int repeats = 0; repeats <= MAX_PARTS; repeats++) {
            long count = (long) fixed + (long) repeats * extenders;
            if (count > MAX_PARTS) { break; }
            List<Part> parts = new ArrayList<Part>((int) count);
            for (Part part : source) {
                int copies = part.isExtender() ? repeats : 1;
                for (int i = 0; i < copies; i++) { parts.add(part); }
            }
            if (!parts.isEmpty()) {
                double maximumLength = 0;
                double shrinkCapacity = 0;
                boolean valid = true;
                for (int i = 0; i < parts.size(); i++) {
                    maximumLength += parts.get(i).getFullAdvance();
                    if (i > 0) {
                        double connector = connector(parts.get(i - 1), parts.get(i));
                        if (connector < minimumOverlap) { valid = false; break; }
                        maximumLength -= minimumOverlap;
                        shrinkCapacity += connector - minimumOverlap;
                    }
                }
                if (valid && maximumLength >= target) {
                    double length = Math.max((double) target, maximumLength - shrinkCapacity);
                    double fraction = shrinkCapacity == 0 ? 0 : (maximumLength - length) / shrinkCapacity;
                    return assemble(text, support, axis, effectiveSizePx, scale, parts,
                            minimumOverlap, fraction, length);
                }
            }
            if (extenders == 0) { break; }
        }
        throw new IllegalArgumentException("assembly 无法在部件/重复上限内达到 target");
    }

    private static MathBox assemble(String text, MathFontSupport support, MathStretchAxis axis, int size,
            float scale, List<Part> parts, double minimumOverlap, double fraction, double length) {
        List<GlyphElem> glyphs = new ArrayList<GlyphElem>(parts.size());
        Bounds bounds = new Bounds();
        double cursor = 0;
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            float x = axis == MathStretchAxis.HORIZONTAL ? finite(cursor) : 0;
            // OT 的 assembly origin 就是首部件 glyph origin，不能减去各部件 inkBottom。
            float y = axis == MathStretchAxis.VERTICAL ? finite(-cursor) : 0;
            MathGlyphMetrics metrics = measure(support, part.getGlyphRef(), size);
            bounds.add(metrics, x, y);
            glyphs.add(glyph(text, part.getGlyphRef(), x, y, scale));
            cursor += part.getFullAdvance();
            if (i + 1 < parts.size()) {
                double capacity = connector(part, parts.get(i + 1)) - minimumOverlap;
                cursor -= minimumOverlap + fraction * capacity;
            }
        }
        // 合法 connector overlap 仍会让 AA 边缘重复 source-over。仅切可见范围，完整 glyph
        // 保留采样 padding。共享切线位于 fullAdvance 端点与下一 origin 之间，不能看 ink 边缘。
        float[] seams = new float[Math.max(0, parts.size() - 1)];
        for (int i = 0; i < seams.length; i++) {
            GlyphElem current = glyphs.get(i);
            GlyphElem next = glyphs.get(i + 1);
            double end = axis == MathStretchAxis.HORIZONTAL
                    ? (double) current.getX() + parts.get(i).getFullAdvance()
                    : (double) current.getY() - parts.get(i).getFullAdvance();
            double nextOrigin = axis == MathStretchAxis.HORIZONTAL ? next.getX() : next.getY();
            seams[i] = finite((end + nextOrigin) / 2);
        }
        for (int i = 0; i < glyphs.size(); i++) {
            GlyphElem source = glyphs.get(i);
            float left = Float.NEGATIVE_INFINITY;
            float top = Float.NEGATIVE_INFINITY;
            float right = Float.POSITIVE_INFINITY;
            float bottom = Float.POSITIVE_INFINITY;
            if (axis == MathStretchAxis.HORIZONTAL) {
                if (i > 0) { left = finite((double) seams[i - 1] - source.getX()); }
                if (i < seams.length) { right = finite((double) seams[i] - source.getX()); }
            } else {
                // OT vertical 按 bottom-to-top 排列：前一个接缝是本部件的 bottom。
                if (i > 0) { bottom = finite((double) seams[i - 1] - source.getY()); }
                if (i < seams.length) { top = finite((double) seams[i] - source.getY()); }
            }
            MathGlyphClip clip = new MathGlyphClip(left, top, right, bottom);
            glyphs.set(i, new GlyphElem(source.getText(), source.getX(), source.getY(), source.getSizeScale(),
                    source.isItalic(), source.isInheritTextItalic(), source.getMathFontStyle(),
                    source.getMathGlyphRef(), clip));
        }
        // 水平推进按拼接配方；垂直伸缩仍使用真实水平 advance，绝不以 ink 代替。
        return bounds.box(axis == MathStretchAxis.HORIZONTAL ? finite(length) : bounds.advance, glyphs);
    }

    private static double connector(Part left, Part right) {
        return Math.min(left.getEndConnector(), right.getStartConnector());
    }

    private static float validate(String text, MathGlyphRef ref, MathFontSupport support, int size, float parentSize) {
        if (text == null || text.isEmpty() || ref == null || support == null || size <= 0
                || !Float.isFinite(parentSize) || parentSize <= 0) {
            throw new IllegalArgumentException("字形、support 和文本必须非空，字号必须为有限正值");
        }
        float scale = size / parentSize;
        if (!Float.isFinite(scale) || scale <= 0) { throw new IllegalArgumentException("字号比例越界"); }
        return scale;
    }

    private static void sameFace(MathGlyphRef root, MathGlyphRef part) {
        if (root.getKind() != MathGlyphRef.Kind.FONT_GLYPH
                || !root.getFaceKey().equals(part.getFaceKey())) {
            throw new IllegalArgumentException("根字形与字体配方不得跨 face");
        }
    }

    private static MathGlyphMetrics measure(MathFontSupport support, MathGlyphRef ref, int size) {
        MathGlyphMetrics metrics = support.measure(ref, size);
        if (metrics == null) { throw new IllegalArgumentException("字体未提供字形度量"); }
        return metrics;
    }

    private static GlyphElem glyph(String text, MathGlyphRef ref, float x, float y, float scale) {
        return new GlyphElem(text, x, y, scale, false, false, MathFontStyle.UPRIGHT, ref);
    }

    private static float finite(double value) {
        float result = (float) value;
        if (!Float.isFinite(result)) { throw new IllegalArgumentException("拼接几何越界"); }
        return result;
    }

    private static final class Bounds {
        private double left;
        private double right;
        private double top;
        private double bottom;
        private float advance;

        void add(MathGlyphMetrics metrics, float x, float y) {
            advance = Math.max(advance, metrics.getAdvance());
            left = Math.min(left, (double) x + metrics.getInkLeft());
            right = Math.max(right, (double) x + metrics.getInkRight());
            top = Math.min(top, (double) y + metrics.getInkTop());
            bottom = Math.max(bottom, (double) y + metrics.getInkBottom());
        }

        MathBox box(float width, List<GlyphElem> glyphs) {
            return new MathBox(width, finite(-top), finite(bottom), glyphs, null,
                    finite(-left), finite(Math.max(0, right - width)));
        }
    }
}
