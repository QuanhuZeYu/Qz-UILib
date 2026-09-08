package club.heiqi.uilib.font.latex.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 同一物理 face 的有序变体及可选拼接配方；所有长度为有效字号下 logical px。 */
public final class MathGlyphConstruction {
    private final List<Variant> variants;
    private final Assembly assembly;

    public MathGlyphConstruction(List<Variant> variants, Assembly assembly) {
        if (variants == null) { throw new IllegalArgumentException("variants 不得为空引用"); }
        List<Variant> copy = new ArrayList<Variant>(variants);
        String face = null;
        float previous = -1;
        for (Variant variant : copy) {
            if (variant == null) { throw new IllegalArgumentException("variant 不得为空"); }
            face = requireSameFace(face, variant.getGlyphRef());
            if (variant.getStretchAdvance() < previous) {
                throw new IllegalArgumentException("variants 必须按 stretchAdvance 非递减排列");
            }
            previous = variant.getStretchAdvance();
        }
        if (assembly != null) {
            for (Part part : assembly.getParts()) { face = requireSameFace(face, part.getGlyphRef()); }
        }
        this.variants = Collections.unmodifiableList(copy);
        this.assembly = assembly;
    }

    public List<Variant> getVariants() { return variants; }
    /** 没有拼接配方时返回 null。 */
    public Assembly getAssembly() { return assembly; }

    private static String requireSameFace(String face, MathGlyphRef glyphRef) {
        if (glyphRef == null || glyphRef.getKind() != MathGlyphRef.Kind.FONT_GLYPH) {
            throw new IllegalArgumentException("字体配方需要真实 FONT_GLYPH 引用");
        }
        String actual = glyphRef.getFaceKey();
        if (face != null && !face.equals(actual)) {
            throw new IllegalArgumentException("结构变体和部件不得跨 face 拼接");
        }
        return actual;
    }

    private static void requireFinite(float value, String name) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException(name + " 必须为有限值");
        }
    }

    private static void requireNonNegative(float value, String name) {
        requireFinite(value, name);
        if (value < 0) { throw new IllegalArgumentException(name + " 不得为负"); }
    }

    public static final class Variant {
        private final MathGlyphRef glyphRef;
        private final float stretchAdvance;

        public Variant(MathGlyphRef glyphRef, float stretchAdvance) {
            requireSameFace(null, glyphRef);
            requireNonNegative(stretchAdvance, "stretchAdvance");
            this.glyphRef = glyphRef;
            this.stretchAdvance = stretchAdvance;
        }

        public MathGlyphRef getGlyphRef() { return glyphRef; }
        public float getStretchAdvance() { return stretchAdvance; }
    }

    public static final class Assembly {
        private final List<Part> parts;
        private final float minConnectorOverlap;
        private final float italicCorrection;

        public Assembly(List<Part> parts, float minConnectorOverlap, float italicCorrection) {
            if (parts == null || parts.isEmpty()) {
                throw new IllegalArgumentException("assembly 必须包含部件");
            }
            requireNonNegative(minConnectorOverlap, "minConnectorOverlap");
            requireFinite(italicCorrection, "italicCorrection");
            List<Part> copy = new ArrayList<Part>(parts);
            String face = null;
            Part previous = null;
            for (Part part : copy) {
                if (part == null) { throw new IllegalArgumentException("part 不得为空"); }
                face = requireSameFace(face, part.getGlyphRef());
                if (previous != null && (previous.getEndConnector() < minConnectorOverlap
                        || part.getStartConnector() < minConnectorOverlap)) {
                    throw new IllegalArgumentException("相邻部件连接长度不足 minConnectorOverlap");
                }
                if (part.isExtender() && (part.getStartConnector() < minConnectorOverlap
                        || part.getEndConnector() < minConnectorOverlap)) {
                    throw new IllegalArgumentException("重复部件的两端必须支持 minConnectorOverlap");
                }
                previous = part;
            }
            this.parts = Collections.unmodifiableList(copy);
            this.minConnectorOverlap = minConnectorOverlap;
            this.italicCorrection = italicCorrection;
        }

        public List<Part> getParts() { return parts; }
        public float getMinConnectorOverlap() { return minConnectorOverlap; }
        public float getItalicCorrection() { return italicCorrection; }
    }

    public static final class Part {
        private final MathGlyphRef glyphRef;
        private final float startConnector;
        private final float endConnector;
        private final float fullAdvance;
        private final boolean extender;

        public Part(MathGlyphRef glyphRef, float startConnector, float endConnector,
                float fullAdvance, boolean extender) {
            requireSameFace(null, glyphRef);
            requireNonNegative(startConnector, "startConnector");
            requireNonNegative(endConnector, "endConnector");
            requireNonNegative(fullAdvance, "fullAdvance");
            if (fullAdvance == 0 || startConnector > fullAdvance || endConnector > fullAdvance) {
                throw new IllegalArgumentException("fullAdvance 必须为正，connector 不得超过 fullAdvance");
            }
            this.glyphRef = glyphRef;
            this.startConnector = startConnector;
            this.endConnector = endConnector;
            this.fullAdvance = fullAdvance;
            this.extender = extender;
        }

        public MathGlyphRef getGlyphRef() { return glyphRef; }
        public float getStartConnector() { return startConnector; }
        public float getEndConnector() { return endConnector; }
        public float getFullAdvance() { return fullAdvance; }
        public boolean isExtender() { return extender; }
    }
}
