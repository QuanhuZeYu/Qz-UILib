package club.heiqi.uilib.font.layout;

/**
 * 文本片段模型。
 */
public class TextSegment {

    private final String text;
    private final TextStyle style;

    /**
     * 创建文本片段。
     *
     * @param text 片段文本
     * @param style 片段样式
     */
    public TextSegment(String text, TextStyle style) {
        this.text = text;
        this.style = style;
    }

    public String getText() {
        return text;
    }

    public TextStyle getStyle() {
        return style;
    }
}
