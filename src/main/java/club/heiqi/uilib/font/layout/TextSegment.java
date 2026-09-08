package club.heiqi.uilib.font.layout;

import club.heiqi.uilib.font.latex.MathStyleOverride;

/**
 * 文本片段模型：普通文本片段或 LaTeX 公式片段（二者互斥）。
 */
public class TextSegment {

    private final String text;
    private final TextStyle style;
    private final String latexSource;
    private final MathStyleOverride latexMathStyle;

    /**
     * 创建普通文本片段。
     *
     * @param text 片段文本
     * @param style 片段样式
     */
    public TextSegment(String text, TextStyle style) {
        this(text, style, null, MathStyleOverride.INHERIT);
    }

    private TextSegment(String text, TextStyle style, String latexSource, MathStyleOverride latexMathStyle) {
        if (text == null) {
            throw new IllegalArgumentException("text 不能为空");
        }
        if (style == null) {
            throw new IllegalArgumentException("style 不能为空");
        }
        this.text = text;
        this.style = style;
        if (latexMathStyle == null) {
            throw new IllegalArgumentException("latexMathStyle 不能为空");
        }
        this.latexSource = latexSource;
        this.latexMathStyle = latexMathStyle;
    }

    /**
     * 创建 LaTeX 公式片段（{@code <latex>...</latex>} 内容）。
     *
     * @param latexSource TeX 源码（不可为 null）
     * @param style       继承的段落样式
     */
    public static TextSegment forLatex(String latexSource, TextStyle style) {
        return forLatex(latexSource, style, MathStyleOverride.TEXT);
    }

    /** 创建具有明确根数学样式的公式；根 INHERIT 归一 TEXT。 */
    public static TextSegment forLatex(String latexSource, TextStyle style, MathStyleOverride mathStyle) {
        if (mathStyle == null) {
            throw new IllegalArgumentException("mathStyle 不能为空");
        }
        if (latexSource == null) {
            throw new IllegalArgumentException("latexSource 不能为空");
        }
        return new TextSegment("", style, latexSource,
                mathStyle == MathStyleOverride.INHERIT ? MathStyleOverride.TEXT : mathStyle);
    }

    /** 普通段返回 INHERIT，公式段返回归一后的根数学样式。 */
    public MathStyleOverride getLatexMathStyle() {
        return latexMathStyle;
    }

    /** 仅替换样式引用；调用方需要隔离时传入 style.copy()。 */
    public TextSegment withStyle(TextStyle style) {
        return new TextSegment(text, style, latexSource, latexMathStyle);
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
