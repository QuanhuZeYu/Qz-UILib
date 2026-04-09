package club.heiqi.uilib.font.glyph;

import club.heiqi.uilib.font.FontType;

/**
 * 字符生成任务定义。
 */
public class GlyphGenerationTask {

    private final int codepoint;
    private final FontType fontType;
    private final int glyphSize;
    private final GlyphGenerationPriority priority;

    /**
     * 创建字符生成任务。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @param glyphSize 字符格大小
     * @param priority 生成优先级
     */
    public GlyphGenerationTask(int codepoint, FontType fontType, int glyphSize, GlyphGenerationPriority priority) {
        this.codepoint = codepoint;
        this.fontType = fontType;
        this.glyphSize = glyphSize;
        this.priority = priority;
    }

    public int getCodepoint() {
        return codepoint;
    }

    public FontType getFontType() {
        return fontType;
    }

    public int getGlyphSize() {
        return glyphSize;
    }

    public GlyphGenerationPriority getPriority() {
        return priority;
    }
}
