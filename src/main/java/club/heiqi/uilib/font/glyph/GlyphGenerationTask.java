package club.heiqi.uilib.font.glyph;

import club.heiqi.uilib.font.FontType;

/**
 * 字符生成任务定义。
 */
public class GlyphGenerationTask {

    private final int runtimeVersion;
    private final long generationId;
    private final int codepoint;
    private final FontType fontType;
    private final int glyphSize;
    private final GlyphGenerationPriority priority;

    /**
     * 创建字符生成任务。
     *
     * @param runtimeVersion 运行时版本
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @param glyphSize 字符格大小
     * @param priority 生成优先级
     */
    public GlyphGenerationTask(int runtimeVersion, int codepoint, FontType fontType, int glyphSize,
            GlyphGenerationPriority priority) {
        this(runtimeVersion, 0L, codepoint, fontType, glyphSize, priority);
    }

    /**
     * 创建带生成请求编号的字符生成任务。
     *
     * @param runtimeVersion 运行时版本
     * @param generationId 生成请求编号
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @param glyphSize 字符格大小
     * @param priority 生成优先级
     */
    public GlyphGenerationTask(int runtimeVersion, long generationId, int codepoint, FontType fontType, int glyphSize,
            GlyphGenerationPriority priority) {
        this.runtimeVersion = runtimeVersion;
        this.generationId = generationId;
        this.codepoint = codepoint;
        this.fontType = fontType;
        this.glyphSize = glyphSize;
        this.priority = priority;
    }

    public int getRuntimeVersion() {
        return runtimeVersion;
    }

    public long getGenerationId() {
        return generationId;
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

    /**
     * 派生带生成请求编号的任务。
     *
     * @param generationId 生成请求编号
     * @return 带编号的新任务
     */
    public GlyphGenerationTask withGenerationId(long generationId) {
        return new GlyphGenerationTask(runtimeVersion, generationId, codepoint, fontType, glyphSize, priority);
    }
}
