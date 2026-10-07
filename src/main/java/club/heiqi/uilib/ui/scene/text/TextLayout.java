package club.heiqi.uilib.ui.scene.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.nativefont.NativeFontFace;

/** 度量与绘制共享的不可变定位。int 尺寸为真实浮点边界向上取整，不把 advance 逐字取整。 */
public final class TextLayout {
    public static final class Glyph {
        public final FontSource source;
        public final float size;
        public final int glyphId, logicalStart, logicalEnd, sourceStart, sourceEnd;
        /** layout 左上坐标，Y 向下；x/y 包含 HB offset，y 已含基线。 */
        public final float x, y, xAdvance, yAdvance;
        public final boolean color, replacement;
        /** 实际glyph原点的不可变墨迹边界（Y向下），UNKNOWN时不能据此剔除需求。 */
        public final NativeFontFace.InkBounds inkBounds;
        public Glyph(FontSource source, float size, int glyphId, int logicalStart, int logicalEnd,
                int sourceStart, int sourceEnd, float x, float y, float xAdvance, float yAdvance,
                boolean color, boolean replacement) {
            this(source, size, glyphId, logicalStart, logicalEnd, sourceStart, sourceEnd, x, y, xAdvance, yAdvance,
                color, replacement, NativeFontFace.InkBounds.UNKNOWN);
        }
        public Glyph(FontSource source, float size, int glyphId, int logicalStart, int logicalEnd,
                int sourceStart, int sourceEnd, float x, float y, float xAdvance, float yAdvance,
                boolean color, boolean replacement, NativeFontFace.InkBounds inkBounds) {
            this.source = Objects.requireNonNull(source, "source"); this.size = size; this.glyphId = glyphId;
            this.logicalStart = logicalStart; this.logicalEnd = logicalEnd;
            this.sourceStart = sourceStart; this.sourceEnd = sourceEnd;
            this.x = x; this.y = y; this.xAdvance = xAdvance; this.yAdvance = yAdvance;
            this.color = color; this.replacement = replacement;
            this.inkBounds = Objects.requireNonNull(inkBounds, "inkBounds");
        }
    }
    public static final class Diagnostic {
        public enum Kind { INVALID_INPUT, CONTROL_MARKER, MISSING_GLYPH, FONT_RESOURCE, UNSUPPORTED }
        public final Kind kind;
        public final int sourceStart, sourceEnd;
        public final String message;
        public Diagnostic(Kind kind, int sourceStart, int sourceEnd, String message) {
            this.kind = Objects.requireNonNull(kind, "kind"); this.sourceStart = sourceStart;
            this.sourceEnd = sourceEnd; this.message = Objects.requireNonNull(message, "message");
        }
    }
    public static final class Line {
        /** 仍为源文逻辑顺序，控制符可视化不写回此字段。 */
        public final String text;
        public final int x, y, logicalStart, logicalEnd, sourceStart, sourceEnd;
        public final float advance, baseline, ascent, descent, lineHeight;
        private final List<Glyph> glyphs;
        public Line(String text, int x, int y) {
            this(text, x, y, 0, text.length(), 0, text.length(), 0, y, 0, 0, 0, Collections.<Glyph>emptyList());
        }
        public Line(String text, int x, int y, int logicalStart, int logicalEnd, int sourceStart, int sourceEnd,
                float advance, float baseline, float ascent, float descent, float lineHeight, List<Glyph> glyphs) {
            this.text = Objects.requireNonNull(text, "text"); this.x = x; this.y = y;
            this.logicalStart = logicalStart; this.logicalEnd = logicalEnd; this.sourceStart = sourceStart; this.sourceEnd = sourceEnd;
            this.advance = advance; this.baseline = baseline; this.ascent = ascent; this.descent = descent; this.lineHeight = lineHeight;
            this.glyphs = immutable(glyphs);
        }
        public List<Glyph> glyphs() { return glyphs; }
    }
    public final int width, height;
    public final float advanceWidth, advanceHeight;
    public final TextStyle style;
    /** logicalText 为 TextInput 清理后的原逻辑文；originalText 对字符串入口保留业务原串。 */
    public final String logicalText, originalText;
    public final long generation;
    private final List<Line> lines;
    private final List<Diagnostic> diagnostics;
    public TextLayout(int width, int height, TextStyle style, List<Line> lines) {
        this(width, height, width, height, style, lines, "", "", 0, Collections.<Diagnostic>emptyList());
    }
    public TextLayout(int width, int height, float advanceWidth, float advanceHeight, TextStyle style,
            List<Line> lines, String logicalText, String originalText, long generation, List<Diagnostic> diagnostics) {
        if (width < 0 || height < 0) throw new IllegalArgumentException("文本度量不能为负");
        this.width = width; this.height = height; this.advanceWidth = advanceWidth; this.advanceHeight = advanceHeight;
        this.style = Objects.requireNonNull(style, "style"); this.lines = immutable(lines); this.diagnostics = immutable(diagnostics);
        this.logicalText = Objects.requireNonNull(logicalText, "logicalText"); this.originalText = originalText; this.generation = generation;
    }
    private static <T> List<T> immutable(List<T> values) { return Collections.unmodifiableList(new ArrayList<T>(values)); }
    public List<Line> lines() { return lines; }
    public List<Diagnostic> diagnostics() { return diagnostics; }
    /** 颜色变化只替换样式，不重走匹配、整形及光栅。 */
    public TextLayout withStyle(TextStyle style) {
        if (Objects.requireNonNull(style, "style").size != this.style.size) throw new IllegalArgumentException("已定位计划不能更改字号");
        return new TextLayout(width, height, advanceWidth, advanceHeight, style, lines, logicalText, originalText, generation, diagnostics);
    }
}
