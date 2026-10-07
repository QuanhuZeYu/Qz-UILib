package club.heiqi.uilib.ui.scene.text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
/** 同时给布局与绘制消费的不可变文本行结果。 */
public final class TextLayout {
    public static final class Line {
        public final String text;
        public final int x, y;
        public Line(String text, int x, int y) { this.text = Objects.requireNonNull(text, "text"); this.x = x; this.y = y; }
    }
    public final int width, height;
    public final TextStyle style;
    private final List<Line> lines;
    public TextLayout(int width, int height, TextStyle style, List<Line> lines) {
        if (width < 0 || height < 0) throw new IllegalArgumentException("文本度量不能为负");
        this.width = width; this.height = height; this.style = Objects.requireNonNull(style, "style");
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }
    public List<Line> lines() { return lines; }
}
