package club.heiqi.uilib.font.latex.layout;

/**
 * 平台无关的字形内容身份。faceKey 由 provider 绑定资源 SHA、face index 和选择 profile；
 * 同内容允许跨代复用，任务、页槽和绘制计划另持 generation 屏障。
 */
public final class MathGlyphRef {
    public enum Kind { FONT_GLYPH, PROCEDURAL_ACCENT }

    private final Kind kind;
    private final String faceKey;
    private final int glyphId;
    private final ProceduralAccentSpec proceduralAccent;

    private MathGlyphRef(Kind kind, String faceKey, int glyphId, ProceduralAccentSpec proceduralAccent) {
        this.kind = kind;
        this.faceKey = faceKey;
        this.glyphId = glyphId;
        this.proceduralAccent = proceduralAccent;
    }

    public static MathGlyphRef forFontGlyph(String faceKey, int glyphId) {
        if (faceKey == null || faceKey.trim().isEmpty() || glyphId < 0) {
            throw new IllegalArgumentException("faceKey 必须非空，glyphId 必须非负");
        }
        return new MathGlyphRef(Kind.FONT_GLYPH, faceKey, glyphId, null);
    }

    public static MathGlyphRef forProceduralAccent(ProceduralAccentSpec spec) {
        if (spec == null) { throw new IllegalArgumentException("spec 不得为空"); }
        return new MathGlyphRef(Kind.PROCEDURAL_ACCENT, null, 0, spec);
    }

    public Kind getKind() { return kind; }
    public String getFaceKey() { requireKind(Kind.FONT_GLYPH); return faceKey; }
    public int getGlyphId() { requireKind(Kind.FONT_GLYPH); return glyphId; }
    public ProceduralAccentSpec getProceduralAccent() {
        requireKind(Kind.PROCEDURAL_ACCENT);
        return proceduralAccent;
    }

    private void requireKind(Kind expected) {
        if (kind != expected) { throw new IllegalStateException("字形来源类型不匹配: " + kind); }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof MathGlyphRef)) { return false; }
        MathGlyphRef ref = (MathGlyphRef) other;
        if (kind != ref.kind) { return false; }
        return kind == Kind.FONT_GLYPH ? glyphId == ref.glyphId && faceKey.equals(ref.faceKey)
                : proceduralAccent.equals(ref.proceduralAccent);
    }

    @Override
    public int hashCode() {
        return 31 * kind.hashCode() + (kind == Kind.FONT_GLYPH
                ? 31 * faceKey.hashCode() + glyphId : proceduralAccent.hashCode());
    }
}
