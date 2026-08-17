package club.heiqi.uilib.font.page;

/**
 * 字符生命周期状态。
 */
public enum GlyphState {
    ABSENT,
    QUEUED,
    RASTERIZING,
    UPLOAD_QUEUED,
    UPLOADING,
    RESIDENT,
    NO_BITMAP,
    FAILED,
    CANCELLED_STALE
}
