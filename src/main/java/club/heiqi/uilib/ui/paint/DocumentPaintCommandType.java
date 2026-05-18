package club.heiqi.uilib.ui.paint;

/**
 * HTML-like 绘制命令类型。
 */
public enum DocumentPaintCommandType {
    PAINT_CONTEXT_START,
    PAINT_CONTEXT_END,
    BACKDROP_FILTER,
    BACKGROUND,
    BOX_SHADOW,
    BOX_SHADOW_INSET,
    BORDER,
    TEXT_DECORATION,
    TEXT,
    CLIP_START,
    CLIP_END,
    OUTLINE,
    SCROLLBAR_TRACK,
    SCROLLBAR_THUMB,
    CUSTOM
}
