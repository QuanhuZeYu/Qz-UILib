package club.heiqi.uilib.font.page;

import club.heiqi.uilib.font.glyph.GlyphGenerationResult;

/**
 * 待上传到主线程的字符结果。
 */
public class PendingGlyphUpload {

    private final int runtimeVersion;
    private final GlyphCacheKey key;
    private final GlyphGenerationResult generationResult;

    /**
     * 创建待上传记录。
     *
     * @param runtimeVersion 运行时版本
     * @param key 字符缓存键
     * @param generationResult 字符生成结果
     */
    public PendingGlyphUpload(int runtimeVersion, GlyphCacheKey key, GlyphGenerationResult generationResult) {
        this.runtimeVersion = runtimeVersion;
        this.key = key;
        this.generationResult = generationResult;
    }

    public int getRuntimeVersion() {
        return runtimeVersion;
    }

    public GlyphCacheKey getKey() {
        return key;
    }

    public GlyphGenerationResult getGenerationResult() {
        return generationResult;
    }
}
