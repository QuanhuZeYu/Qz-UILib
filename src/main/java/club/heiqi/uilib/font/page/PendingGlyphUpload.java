package club.heiqi.uilib.font.page;

import club.heiqi.uilib.font.glyph.GlyphGenerationResult;

/**
 * 待上传到主线程的字符结果。
 */
public class PendingGlyphUpload {

    private final int runtimeVersion;
    private final long generationId;
    private final int codepoint;
    private final GlyphGenerationResult generationResult;

    /**
     * 创建待上传记录。
     *
     * @param runtimeVersion 运行时版本
     * @param generationResult 字符生成结果
     */
    public PendingGlyphUpload(int runtimeVersion, GlyphGenerationResult generationResult) {
        this.runtimeVersion = runtimeVersion;
        this.generationId = generationResult.getGenerationId();
        this.codepoint = generationResult.getCodepoint();
        this.generationResult = generationResult;
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

    public GlyphGenerationResult getGenerationResult() {
        return generationResult;
    }
}
