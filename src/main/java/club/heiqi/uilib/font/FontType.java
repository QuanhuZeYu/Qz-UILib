package club.heiqi.uilib.font;

/**
 * 字体字面类型：以「字重 × 斜体」两个维度描述一个字面（face）。
 *
 * <p>斜体是<b>独立字面</b>，而非对正体做渲染期斜切；因此需要单独的 NORMAL/BOLD 组合。
 * 惯用取值：普通文本 {@link #NORMAL}，加粗 {@link #BOLD}，斜体 {@link #ITALIC}，粗斜体 {@link #BOLD_ITALIC}。</p>
 */
public enum FontType {
    NORMAL,
    BOLD,
    ITALIC,
    BOLD_ITALIC;

    /** 是否为粗体字重。 */
    public boolean isBold() {
        return this == BOLD || this == BOLD_ITALIC;
    }

    /** 是否为斜体字面。 */
    public boolean isItalic() {
        return this == ITALIC || this == BOLD_ITALIC;
    }

    /** 由「字重 + 斜体」两个布尔合成字面类型。 */
    public static FontType of(boolean bold, boolean italic) {
        if (bold) {
            return italic ? BOLD_ITALIC : BOLD;
        }
        return italic ? ITALIC : NORMAL;
    }
}
