package club.heiqi.uilib.ui.scene.text;
import java.util.Objects;
/** 值型文本样式；已发布计划不泄露可变样式。 */
public final class TextStyle {
    public final String font;
    public final int size, color;
    public TextStyle(String font, int size, int color) {
        if (size <= 0) throw new IllegalArgumentException("字号必须为正");
        this.font = Objects.requireNonNull(font, "font"); this.size = size; this.color = color;
    }
}
