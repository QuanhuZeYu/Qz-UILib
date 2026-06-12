package club.heiqi.uilib.ui.text;

import java.util.Objects;

import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;

/**
 * UI 文本测量与绘制的语义化样式快照。
 */
public final class TextMeasureStyle {

    public static final int DEFAULT_FONT_SIZE_PX = 18;
    public static final TextMeasureStyle DEFAULT = new TextMeasureStyle(DEFAULT_FONT_SIZE_PX,
            TextContentMode.UILIB_RAW, UiFontWeight.NORMAL, UiFontStyle.NORMAL);

    private final int fontSizePx;
    private final TextContentMode textContentMode;
    private final UiFontWeight fontWeight;
    private final UiFontStyle fontStyle;

    /**
     * 创建文本样式快照。
     *
     * @param fontSizePx UI 像素字号
     * @param textContentMode 文本内容解析模式
     * @param fontWeight 字体粗细
     * @param fontStyle 字体样式
     */
    public TextMeasureStyle(int fontSizePx, TextContentMode textContentMode, UiFontWeight fontWeight,
            UiFontStyle fontStyle) {
        this.fontSizePx = Math.max(1, fontSizePx);
        this.textContentMode = textContentMode == null ? TextContentMode.UILIB_RAW : textContentMode;
        this.fontWeight = fontWeight == null ? UiFontWeight.NORMAL : fontWeight;
        this.fontStyle = fontStyle == null ? UiFontStyle.NORMAL : fontStyle;
    }

    /**
     * 创建指定 UI 像素字号的默认样式。
     *
     * @param fontSizePx UI 像素字号
     * @return 文本样式快照
     */
    public static TextMeasureStyle fontSizePx(int fontSizePx) {
        return new TextMeasureStyle(fontSizePx, TextContentMode.UILIB_RAW, UiFontWeight.NORMAL,
                UiFontStyle.NORMAL);
    }

    /**
     * 返回复制当前样式但替换解析模式后的快照。
     *
     * @param textContentMode 文本内容解析模式
     * @return 文本样式快照
     */
    public TextMeasureStyle withTextContentMode(TextContentMode textContentMode) {
        return new TextMeasureStyle(fontSizePx, textContentMode, fontWeight, fontStyle);
    }

    /**
     * 返回复制当前样式但替换基础字体样式后的快照。
     *
     * @param fontWeight 字体粗细
     * @param fontStyle 字体样式
     * @return 文本样式快照
     */
    public TextMeasureStyle withFontStyle(UiFontWeight fontWeight, UiFontStyle fontStyle) {
        return new TextMeasureStyle(fontSizePx, textContentMode, fontWeight, fontStyle);
    }

    public int getFontSizePx() {
        return fontSizePx;
    }

    public TextContentMode getTextContentMode() {
        return textContentMode;
    }

    public UiFontWeight getFontWeight() {
        return fontWeight;
    }

    public UiFontStyle getFontStyle() {
        return fontStyle;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TextMeasureStyle)) {
            return false;
        }
        TextMeasureStyle other = (TextMeasureStyle) obj;
        return fontSizePx == other.fontSizePx
                && textContentMode == other.textContentMode
                && fontWeight == other.fontWeight
                && fontStyle == other.fontStyle;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(fontSizePx), textContentMode, fontWeight, fontStyle);
    }
}
