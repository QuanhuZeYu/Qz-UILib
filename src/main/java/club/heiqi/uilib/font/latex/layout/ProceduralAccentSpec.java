package club.heiqi.uilib.font.latex.layout;

/** 程序重音的内容身份；每 em 固定 65536 单位，不携带运行代际或光栅尺寸。 */
public final class ProceduralAccentSpec {
    public enum Kind { HAT, TILDE }

    private final Kind kind;
    private final int profileRevision;
    private final int widthUnits;
    private final int heightUnits;
    private final int strokeUnits;

    private ProceduralAccentSpec(Kind kind, int profileRevision, int widthUnits, int heightUnits, int strokeUnits) {
        if (kind == null || profileRevision <= 0 || widthUnits <= 0 || heightUnits <= 0 || strokeUnits <= 0) {
            throw new IllegalArgumentException("kind 不得为空，profile 和固定点尺寸必须为正");
        }
        this.kind = kind;
        this.profileRevision = profileRevision;
        this.widthUnits = widthUnits;
        this.heightUnits = heightUnits;
        this.strokeUnits = strokeUnits;
    }

    public static ProceduralAccentSpec of(Kind kind, int profileRevision,
            int widthUnits, int heightUnits, int strokeUnits) {
        return new ProceduralAccentSpec(kind, profileRevision, widthUnits, heightUnits, strokeUnits);
    }

    public Kind getKind() { return kind; }
    public int getProfileRevision() { return profileRevision; }
    public int getWidthUnits() { return widthUnits; }
    public int getHeightUnits() { return heightUnits; }
    public int getStrokeUnits() { return strokeUnits; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof ProceduralAccentSpec)) { return false; }
        ProceduralAccentSpec spec = (ProceduralAccentSpec) other;
        return kind == spec.kind && profileRevision == spec.profileRevision
                && widthUnits == spec.widthUnits && heightUnits == spec.heightUnits && strokeUnits == spec.strokeUnits;
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + profileRevision;
        result = 31 * result + widthUnits;
        result = 31 * result + heightUnits;
        return 31 * result + strokeUnits;
    }
}
