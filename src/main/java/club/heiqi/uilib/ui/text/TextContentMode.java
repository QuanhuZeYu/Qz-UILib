package club.heiqi.uilib.ui.text;

/**
 * 文本内容解析模式。
 */
public enum TextContentMode {

    /**
     * 按 UILib 原始文本处理，`§` 等字符不再被当作 Minecraft 格式码解析。
     */
    UILIB_RAW,

    /**
     * 按 Minecraft 文本格式处理，解析 `§` 颜色与样式码。
     */
    MINECRAFT_FORMATTED
}
