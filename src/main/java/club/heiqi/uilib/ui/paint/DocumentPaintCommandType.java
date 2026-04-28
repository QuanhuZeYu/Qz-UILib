package club.heiqi.uilib.ui.paint;

/**
 * HTML-like 绘制命令类型。
 */
public enum DocumentPaintCommandType {
    PAINT_CONTEXT_START,
    PAINT_CONTEXT_END,
    BACKDROP_FILTER,
    BACKGROUND,
    BORDER,
    TEXT,
    CLIP_START,
    CLIP_END,
    SCROLLBAR_TRACK,
    SCROLLBAR_THUMB,
    CUSTOM
}
