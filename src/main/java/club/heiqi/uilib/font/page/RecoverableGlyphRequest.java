package club.heiqi.uilib.font.page;

import club.heiqi.uilib.font.FontType;

/**
 * 资源重载时需要迁移到新运行时的字形请求。
 */
public final class RecoverableGlyphRequest {

    private final int codepoint;
    private final FontType fontType;

    /**
     * 创建可恢复字形请求。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     */
    public RecoverableGlyphRequest(int codepoint, FontType fontType) {
        this.codepoint = codepoint;
        this.fontType = fontType;
    }

    public int getCodepoint() {
        return codepoint;
    }

    public FontType getFontType() {
        return fontType;
    }
}
