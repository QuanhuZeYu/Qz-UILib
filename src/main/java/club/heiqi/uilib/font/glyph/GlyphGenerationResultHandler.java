package club.heiqi.uilib.font.glyph;

/**
 * 字符生成结果回调。
 */
public interface GlyphGenerationResultHandler {

    /**
     * 处理生成结果。
     *
     * @param result 生成结果
     */
    void handle(GlyphGenerationResult result);
}
