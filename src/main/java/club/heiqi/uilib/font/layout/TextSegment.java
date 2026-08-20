package club.heiqi.uilib.font.layout;

/**
 * 文本片段模型：普通文本片段或 LaTeX 公式片段（二者互斥）。
 */
public class TextSegment {

    private final String text;
    private final TextStyle style;
    private final String latexSource;

    /**
     * 创建普通文本片段。
     *
     * @param text 片段文本
     * @param style 片段样式
     */
    public TextSegment(String text, TextStyle style) {
        this(text, style, null);
    }

    private TextSegment(String text, TextStyle style, String latexSource) {
        if (text == null) {
            throw new IllegalArgumentException("text 不能为空");
        }
        if (style == null) {
            throw new IllegalArgumentException("style 不能为空");
        }
        this.text = text;
        this.style = style;
        this.latexSource = latexSource;
    }

    /**
     * 创建 LaTeX 公式片段（{@code <latex>...</latex>} 内容）。
     *
     * @param latexSource TeX 源码（不可为 null）
     * @param style       继承的段落样式
     */
    public static TextSegment forLatex(String latexSource, TextStyle style) {
        if (latexSource == null) {
            throw new IllegalArgumentException("latexSource 不能为空");
        }
        return new TextSegment("", style, latexSource);
    }

    /** @return 片段文本（LaTeX 片段为空串） */
    public String getText() {
        return text;
    }

    public TextStyle getStyle() {
        return style;
    }

    /** @return LaTeX 源码；普通片段为 null */
    public String getLatexSource() {
        return latexSource;
    }

    /** @return 是否为 LaTeX 公式片段 */
    public boolean isLatex() {
        return latexSource != null;
    }

    @Override
    public String toString() {
        return isLatex() ? "TextSegment(<latex>" + latexSource + ")" : "TextSegment(" + text + ")";
    }
}
