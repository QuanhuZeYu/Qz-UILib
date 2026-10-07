package club.heiqi.uilib.ui.scene.text;

import java.util.Objects;

/** 组件请求的排版语义。裁剪/滚动由 scene 执行；当前不实现省略，调用方不能将截断冒充省略。 */
public final class TextPolicy {
    public enum Direction { AUTO, LTR, RTL }
    public enum Wrap { NONE, NORMAL, EMERGENCY }
    public final Direction direction;
    public final Wrap wrap;
    public final String language;
    /** 正数像素制表位；0 使用四个真实空格 advance。 */
    public final float tabSize;
    /** 正数为显式行高；0 使用本行实际 face metrics。 */
    public final float lineHeight;
    public static final TextPolicy NORMAL = new TextPolicy(Direction.AUTO, Wrap.NORMAL, "und", 0, 0);
    public static final TextPolicy SINGLE_LINE = new TextPolicy(Direction.AUTO, Wrap.NONE, "und", 0, 0);
    public TextPolicy(Direction direction, Wrap wrap, String language, float tabSize, float lineHeight) {
        this.direction = Objects.requireNonNull(direction, "direction");
        this.wrap = Objects.requireNonNull(wrap, "wrap");
        this.language = Objects.requireNonNull(language, "language");
        if (!Float.isFinite(tabSize) || tabSize < 0 || !Float.isFinite(lineHeight) || lineHeight < 0)
            throw new IllegalArgumentException("制表位和行高必须为有限非负数");
        this.tabSize = tabSize; this.lineHeight = lineHeight;
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof TextPolicy)) return false;
        TextPolicy p = (TextPolicy) other;
        return direction == p.direction && wrap == p.wrap && language.equals(p.language)
            && Float.compare(tabSize, p.tabSize) == 0 && Float.compare(lineHeight, p.lineHeight) == 0;
    }
    @Override public int hashCode() { return Objects.hash(direction, wrap, language, tabSize, lineHeight); }
}
