package club.heiqi.uilib.ui.scene.text;

import java.util.Objects;

/** 字体选择意图；不包含光栅合成量。 */
public final class FontStyle {
    public enum Posture { NORMAL, ITALIC, OBLIQUE }
    public static final FontStyle NORMAL = new FontStyle(400, Posture.NORMAL);
    public final int weight;
    public final Posture posture;

    public FontStyle(int weight, Posture posture) {
        if (weight < 1 || weight > 1000) throw new IllegalArgumentException("字重范围1..1000");
        this.weight = weight;
        this.posture = Objects.requireNonNull(posture, "posture");
    }

    public static FontStyle minecraft(boolean bold, boolean italic) {
        return new FontStyle(bold ? 700 : 400, italic ? Posture.ITALIC : Posture.NORMAL);
    }

    /** CSS Fonts 4：先字姿后字重；normal先找oblique，italic与oblique互为第一替代。 */
    public int postureRank(Posture actual) {
        if (actual == posture) return 0;
        if (posture == Posture.NORMAL) return actual == Posture.OBLIQUE ? 1 : 2;
        return actual == Posture.NORMAL ? 2 : 1;
    }

    /** CSS Fonts 4固定字重规则：400..500先向500，再向下，最后向上；其它先沿请求方向。 */
    public int weightRank(int actual) {
        if (actual == weight) return 0;
        if (weight >= 400 && weight <= 500) {
            if (actual >= weight && actual <= 500) return actual - weight;
            if (actual < weight) return 1000 + weight - actual;
            return 2000 + actual - 500;
        }
        if (weight < 400) return actual < weight ? weight - actual : 1000 + actual - weight;
        return actual > weight ? actual - weight : 1000 + weight - actual;
    }

    @Override public boolean equals(Object value) {
        if (!(value instanceof FontStyle)) return false;
        FontStyle other = (FontStyle) value;
        return weight == other.weight && posture == other.posture;
    }
    @Override public int hashCode() { return Objects.hash(weight, posture); }
    @Override public String toString() { return weight + "/" + posture; }
}
