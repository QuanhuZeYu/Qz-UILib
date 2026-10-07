package club.heiqi.uilib.ui.scene.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import club.heiqi.uilib.ui.scene.text.font.FontSource;

/** 值型文本样式。名称由字体服务映射到列表；显式列表完整覆盖名称，不隐含追加候选。 */
public final class TextStyle {
    public final String font;
    public final int size, color;
    private final List<FontSource> candidates;
    public TextStyle(String font, int size, int color) { this(font, size, color, null); }
    public TextStyle(String font, int size, int color, List<FontSource> candidates) {
        if (size <= 0) throw new IllegalArgumentException("字号必须为正");
        this.font = Objects.requireNonNull(font, "font"); this.size = size; this.color = color;
        if (candidates == null) this.candidates = null;
        else {
            ArrayList<FontSource> copy = new ArrayList<FontSource>(candidates);
            for (FontSource source : copy) Objects.requireNonNull(source, "candidate");
            this.candidates = Collections.unmodifiableList(copy);
        }
    }
    /** null 表示继承名称/默认列表；空列表表示显式没有候选。 */
    public List<FontSource> candidates() { return candidates; }
    public TextStyle withColor(int color) { return new TextStyle(font, size, color, candidates); }
}
