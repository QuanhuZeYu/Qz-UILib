package club.heiqi.uilib.ui.scene.paint;

import java.util.Objects;

/**
 * 文本样式最小不可变值对象，在构建期固化，回放期不反查节点。
 *
 * <p>每条 {@link PaintCommandType#TEXT 文本绘制命令} 携带一个 {@code TextStyle}，
 * 包含渲染所需的最小样式字段。所有字段 final，构造后不可变。</p>
 *
 * <p>后续预留扩展：字重（fontWeight）、字体族（fontFamily）、文本装饰（textDecoration）、
 * 行高（lineHeight）、阴影（textShadow）等。</p>
 */
public final class TextStyle {

    /** ARGB 文字颜色，格式 0xAARRGGBB */
    private final int color;

    /** 字号大小（像素整数） */
    private final int fontSize;

    /**
     * 创建文本样式。
     *
     * @param color    ARGB 文字颜色
     * @param fontSize 字号（像素）
     */
    public TextStyle(int color, int fontSize) {
        this.color = color;
        this.fontSize = fontSize;
    }

    /** @return ARGB 文字颜色 */
    public int getColor() {
        return color;
    }

    /** @return 字号大小（像素） */
    public int getFontSize() {
        return fontSize;
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
        return color == other.color && fontSize == other.fontSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(color, fontSize);
    }

    @Override
    public String toString() {
        return "TextStyle{color=" + Integer.toHexString(color) + ", fontSize=" + fontSize + "}";
    }
}
