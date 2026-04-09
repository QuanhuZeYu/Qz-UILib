package club.heiqi.uilib.font.page;

/**
 * 字符生命周期状态。
 */
public enum GlyphState {
    NEW,
    GENERATING,
    UPLOAD_PENDING,
    READY,
    FAILED
}
