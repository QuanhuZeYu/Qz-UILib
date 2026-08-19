package club.heiqi.uilib.ui.scene.paint;

import java.util.Objects;

/**
 * 文本样式最小不可变值对象，在构建期固化，回放期不反查节点。
 *
 * <p>每条 {@link PaintCommandType#TEXT 文本绘制命令} 携带一个 {@code TextStyle}，
 * 包含渲染所需的最小样式字段。所有字段 final，构造后不可变。</p>
 *
 * <p>{@link #textMode} 以原始 int 编码内容解析模式（scene 核心不 import {@code ui.text.*}，守 I10）：
 * {@link #TEXT_MODE_UILIB_RAW} 原始文本、{@link #TEXT_MODE_MINECRAFT_FORMATTED} Minecraft § 格式码、
 * {@link #TEXT_MODE_RICH_TAGS} 现代富文本标签；渲染层自行映射到 {@code ui.text.TextContentMode}。</p>
 *
 * <p>后续预留扩展：字重（fontWeight）、字体族（fontFamily）、行高（lineHeight）、阴影（textShadow）等。</p>
 */
public final class TextStyle {

    /** 内容模式：UILib 原始文本，{@code §} 等字符按字面量处理。 */
    public static final int TEXT_MODE_UILIB_RAW = 0;

    /** 内容模式：Minecraft {@code §} 格式码（兼容遗产路径）。 */
    public static final int TEXT_MODE_MINECRAFT_FORMATTED = 1;

    /** 内容模式：UILib 现代富文本标签语法（{@code <color=...>} 等）。 */
    public static final int TEXT_MODE_RICH_TAGS = 2;

    /** ARGB 文字颜色，格式 0xAARRGGBB */
    private final int color;

    /** 字号大小（像素整数） */
    private final int fontSize;

    /** 文本内容解析模式（TEXT_MODE_* 编码） */
    private final int textMode;

    /**
     * 创建原始文本模式样式。
     *
     * @param color    ARGB 文字颜色
     * @param fontSize 字号（像素）
     */
    public TextStyle(int color, int fontSize) {
        this(color, fontSize, TEXT_MODE_UILIB_RAW);
    }

    /**
     * 创建指定内容模式的文本样式。
     *
     * @param color    ARGB 文字颜色
     * @param fontSize 字号（像素）
     * @param textMode 内容模式（TEXT_MODE_* 编码，越界回落到原始文本模式）
     */
    public TextStyle(int color, int fontSize, int textMode) {
        this.color = color;
        this.fontSize = fontSize;
        this.textMode = normalizeTextMode(textMode);
    }

    /** @return ARGB 文字颜色 */
    public int getColor() {
        return color;
    }

    /** @return 字号大小（像素） */
    public int getFontSize() {
        return fontSize;
    }

    /** @return 文本内容解析模式（TEXT_MODE_* 编码） */
    public int getTextMode() {
        return textMode;
    }

    private static int normalizeTextMode(int textMode) {
        if (textMode < TEXT_MODE_UILIB_RAW || textMode > TEXT_MODE_RICH_TAGS) {
            return TEXT_MODE_UILIB_RAW;
        }
        return textMode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TextStyle)) {
            return false;
        }
        TextStyle other = (TextStyle) obj;
        return color == other.color && fontSize == other.fontSize && textMode == other.textMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(color), Integer.valueOf(fontSize), Integer.valueOf(textMode));
    }

    @Override
    public String toString() {
        return "TextStyle{color=" + Integer.toHexString(color) + ", fontSize=" + fontSize
                + ", textMode=" + textMode + "}";
    }
}
