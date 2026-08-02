package club.heiqi.uilib.font.page;

import club.heiqi.uilib.font.glyph.GlyphGenerationResult;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;

/**
 * 待上传到主线程的字符结果。
 */
public final class PendingGlyphUpload {

    private final GlyphGenerationResult generationResult;

    /**
     * 创建待上传记录。
     *
     * @param generationResult 字符生成结果
     */
    public PendingGlyphUpload(GlyphGenerationResult generationResult) {
        if (generationResult == null) {
            throw new IllegalArgumentException("generationResult 不得为 null");
        }
        this.generationResult = generationResult;
    }

    public GlyphRequestToken getToken() {
        return generationResult.getToken();
    }

    public GlyphGenerationResult getGenerationResult() {
        return generationResult;
    }
}
