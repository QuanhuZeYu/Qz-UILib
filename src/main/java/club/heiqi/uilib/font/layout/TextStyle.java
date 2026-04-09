package club.heiqi.uilib.font.layout;

import club.heiqi.uilib.font.FontType;

/**
 * 文本样式状态。
 */
public class TextStyle {

    private int color = 0xFFFFFFFF;
    private FontType fontType = FontType.NORMAL;
    private boolean randomStyle;
    private boolean underline;
    private boolean strikethrough;
    private boolean italic;

    /**
     * 复制当前样式。
     *
     * @return 样式副本
     */
    public TextStyle copy() {
        TextStyle style = new TextStyle();
        style.color = color;
        style.fontType = fontType;
        style.randomStyle = randomStyle;
        style.underline = underline;
        style.strikethrough = strikethrough;
        style.italic = italic;
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
        fontType = FontType.NORMAL;
        randomStyle = false;
        underline = false;
        strikethrough = false;
        italic = false;
    }

    private void resetFlags(int baseColor) {
        color = baseColor;
        fontType = FontType.NORMAL;
        randomStyle = false;
        underline = false;
        strikethrough = false;
        italic = false;
    }

    public int getColor() {
        return color;
    }

    public FontType getFontType() {
        return fontType;
    }

    public boolean isRandomStyle() {
        return randomStyle;
    }

    public boolean isUnderline() {
        return underline;
    }

    public boolean isStrikethrough() {
        return strikethrough;
    }

    public boolean isItalic() {
        return italic;
    }
}
