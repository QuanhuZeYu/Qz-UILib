package club.heiqi.uilib.font.layout;

import club.heiqi.uilib.font.FontType;

/**
 * 文本样式状态。
 */
public class TextStyle {

    /** 上/下标相对正文字号的缩放比（与 CSS 默认 smaller 语义对齐）。 */
    public static final double SUP_SUB_SCALE = 0.75D;

    /** 上标基线抬升量（em，相对有效字号）。 */
    public static final float SUP_RAISE_EM = 0.4F;

    /** 下标基线下沉量（em，相对有效字号）。 */
    public static final float SUB_DROP_EM = 0.25F;

    private int color = 0xFFFFFFFF;
    private FontType fontType = FontType.NORMAL;
    private boolean colorExplicit;
    private boolean randomStyle;
    private boolean underline;
    private boolean strikethrough;
    private boolean italic;
    private FontType baseFontType = FontType.NORMAL;
    private boolean baseItalic;

    /** 绝对像素字号；0 表示未指定，渲染/测量时继承运行时基准字号。 */
    private int fontSizePx;

    /** 行内高亮背景色（ARGB）；0 表示无高亮。 */
    private int markColor;

    /** 上标标记（与 {@link #subscript} 互斥，后设置者生效）。 */
    private boolean superscript;

    /** 下标标记（与 {@link #superscript} 互斥，后设置者生效）。 */
    private boolean subscript;

    /**
     * 复制当前样式。
     *
     * @return 样式副本
     */
    public TextStyle copy() {
        TextStyle style = new TextStyle();
        style.color = color;
        style.fontType = fontType;
        style.colorExplicit = colorExplicit;
        style.randomStyle = randomStyle;
        style.underline = underline;
        style.strikethrough = strikethrough;
        style.italic = italic;
        style.baseFontType = baseFontType;
        style.baseItalic = baseItalic;
        style.fontSizePx = fontSizePx;
        style.markColor = markColor;
        style.superscript = superscript;
        style.subscript = subscript;
        return style;
    }

    /**
     * 按格式码更新样式状态。
     *
     * @param code 格式码
     * @param baseColor 默认颜色
     */
    public void applyFormat(char code, int baseColor) {
        switch (code) {
            case '0': case '1': case '2': case '3': case '4': case '5': case '6': case '7':
            case '8': case '9': case 'a': case 'b': case 'c': case 'd': case 'e': case 'f':
                resetFlags(baseColor);
                color = MinecraftColorTable.getColor(code, false, (baseColor >> 24) & 255);
                colorExplicit = true;
                break;
            case 'k':
                randomStyle = true;
                break;
            case 'l':
                fontType = FontType.BOLD;
                break;
            case 'm':
                strikethrough = true;
                break;
            case 'n':
                underline = true;
                break;
            case 'o':
                italic = true;
                break;
            case 'r':
            default:
                resetAll(baseColor);
                break;
        }
    }

    /**
     * 重置全部样式。
     *
     * @param baseColor 默认颜色
     */
    public void resetAll(int baseColor) {
        color = baseColor;
        fontType = baseFontType == null ? FontType.NORMAL : baseFontType;
        colorExplicit = false;
        randomStyle = false;
        underline = false;
        strikethrough = false;
        italic = baseItalic;
        markColor = 0;
        superscript = false;
        subscript = false;
    }

    private void resetFlags(int baseColor) {
        color = baseColor;
        fontType = baseFontType == null ? FontType.NORMAL : baseFontType;
        colorExplicit = false;
        randomStyle = false;
        underline = false;
        strikethrough = false;
        italic = baseItalic;
        markColor = 0;
        superscript = false;
        subscript = false;
    }

    public int getColor() {
        return color;
    }

    /**
     * 设置显式文字颜色。
     *
     * @param color ARGB 文字颜色
     */
    public void setColor(int color) {
        this.color = color;
        this.colorExplicit = true;
    }

    /**
     * 当前颜色是否为显式指定。
     *
     * @return 显式颜色标记
     */
    public boolean isColorExplicit() {
        return colorExplicit;
    }

    public FontType getFontType() {
        return fontType;
    }

    public void setFontType(FontType fontType) {
        FontType resolvedType = fontType == null ? FontType.NORMAL : fontType;
        this.fontType = resolvedType;
        this.baseFontType = resolvedType;
    }

    public boolean isRandomStyle() {
        return randomStyle;
    }

    public boolean isUnderline() {
        return underline;
    }

    public void setUnderline(boolean underline) {
        this.underline = underline;
    }

    public boolean isStrikethrough() {
        return strikethrough;
    }

    public void setStrikethrough(boolean strikethrough) {
        this.strikethrough = strikethrough;
    }

    public boolean isItalic() {
        return italic;
    }

    public void setItalic(boolean italic) {
        this.italic = italic;
        this.baseItalic = italic;
    }

    /**
     * 获取段落的绝对像素字号；0 表示未指定。
     *
     * @return 像素字号
     */
    public int getFontSizePx() {
        return fontSizePx;
    }

    /**
     * 设置段落的绝对像素字号。
     *
     * @param fontSizePx 像素字号，0 表示未指定
     */
    public void setFontSizePx(int fontSizePx) {
        this.fontSizePx = Math.max(0, fontSizePx);
    }

    /**
     * 获取行内高亮背景色；0 表示无高亮。
     *
     * @return ARGB 高亮色
     */
    public int getMarkColor() {
        return markColor;
    }

    /**
     * 设置行内高亮背景色。
     *
     * @param markColor ARGB 高亮色，0 表示关闭高亮
     */
    public void setMarkColor(int markColor) {
        this.markColor = markColor;
    }

    /** @return 是否上标 */
    public boolean isSuperscript() {
        return superscript;
    }

    /** 设置上标（同时关闭下标）。 */
    public void setSuperscript(boolean superscript) {
        this.superscript = superscript;
        if (superscript) {
            this.subscript = false;
        }
    }

    /** @return 是否下标 */
    public boolean isSubscript() {
        return subscript;
    }

    /** 设置下标（同时关闭上标）。 */
    public void setSubscript(boolean subscript) {
        this.subscript = subscript;
        if (subscript) {
            this.superscript = false;
        }
    }

    /**
     * 解析段的有效渲染/测量字号：显式字号优先，否则用基准字号；
     * 上/下标按 {@link #SUP_SUB_SCALE} 缩放，至少 1 像素。测量侧与渲染侧共用本方法保证同口径。
     *
     * @param fallback 未显式指定字号时的基准字号
     * @return 有效字号（UI 像素，>=1）
     */
    public int resolveEffectiveFontSizePx(int fallback) {
        int base = fontSizePx > 0 ? fontSizePx : fallback;
        if (superscript || subscript) {
            return Math.max(1, (int) Math.round(base * SUP_SUB_SCALE));
        }
        return Math.max(1, base);
    }

    /**
     * 将当前活跃样式编码成原版格式码前缀，用于跨行续传。
     *
     * @param baseColor 默认颜色
     * @return 可直接拼接到文本前面的格式码
     */
    public String toFormattingCodes(int baseColor) {
        StringBuilder builder = new StringBuilder();
        char colorCode = colorExplicit ? MinecraftColorTable.findCodeByColor(color, (baseColor >> 24) & 255) : 0;
        if (colorCode != 0) {
            builder.append('§').append(colorCode);
        }
        if (randomStyle) {
            builder.append("§k");
        }
        if (fontType == FontType.BOLD) {
            builder.append("§l");
        }
        if (strikethrough) {
            builder.append("§m");
        }
        if (underline) {
            builder.append("§n");
        }
        if (italic) {
            builder.append("§o");
        }
        return builder.toString();
    }
}
