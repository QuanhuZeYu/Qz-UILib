package club.heiqi.uilib.font.latex.layout;

import club.heiqi.uilib.font.latex.MathFontStyle;

/**
 * 公式盒内的字形绘制单元：一段文本 + 相对盒基线的偏移 + 字号缩放。
 *
 * <p>渲染侧（M3）据此做一次 prepareGlyphs：文本字形按 (x, y) 写入 xOffsets/yOffsets，
 * 实际字号 = 基础字号 × sizeScale。</p>
 */
public final class GlyphElem {

    private final String text;
    private final float x;
    private final float y;
    private final float sizeScale;
    /** 本地数学斜体选择，渲染侧复用既有几何斜切。 */
    private final boolean italic;
    /** 是否接受宿主 TextStyle 的斜体叠加；显式数学字体选择关闭继承。 */
    private final boolean inheritTextItalic;
    private final MathFontStyle mathFontStyle;

    /**
     * 创建字形单元（直体）。
     *
     * @param text      显示文本（一个或多个码点）
     * @param x         相对盒基线的水平偏移
     * @param y         相对盒基线的垂直偏移（负 = 基线上方）
     * @param sizeScale 字号缩放（1.0 正文、0.7 script、0.49 scriptscript）
     */
    public GlyphElem(String text, float x, float y, float sizeScale) {
        this(text, x, y, sizeScale, false);
    }

    /**
     * 创建字形单元。
     *
     * @param text      显示文本（一个或多个码点）
     * @param x         相对盒基线的水平偏移
     * @param y         相对盒基线的垂直偏移（负 = 基线上方）
     * @param sizeScale 字号缩放（1.0 正文、0.7 script、0.49 scriptscript）
     * @param italic    数学变量斜体（渲染侧斜切几何）
     */
    public GlyphElem(String text, float x, float y, float sizeScale, boolean italic) {
        this(text, x, y, sizeScale, italic, true);
    }

    /** 创建携带本地斜体与宿主继承开关的字形；旧构造器默认继承宿主。 */
    public GlyphElem(String text, float x, float y, float sizeScale, boolean italic, boolean inheritTextItalic) {
        this(text, x, y, sizeScale, italic, inheritTextItalic, MathFontStyle.INHERIT);
    }

    /** 字体选择同时交给测量和绘制；不得为 null。 */
    public GlyphElem(String text, float x, float y, float sizeScale, boolean italic,
            boolean inheritTextItalic, MathFontStyle mathFontStyle) {
        if (mathFontStyle == null) {
            throw new IllegalArgumentException("mathFontStyle 不能为空");
        }
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("text 不能为空");
        }
        this.text = text;
        this.x = x;
        this.y = y;
        this.sizeScale = sizeScale;
        this.italic = italic;
        this.inheritTextItalic = inheritTextItalic;
        this.mathFontStyle = mathFontStyle;
    }

    public String getText() {
        return text;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getSizeScale() {
        return sizeScale;
    }

    public boolean isItalic() {
        return italic;
    }

    public boolean isInheritTextItalic() {
        return inheritTextItalic;
    }

    public MathFontStyle getMathFontStyle() {
        return mathFontStyle;
    }

    @Override
    public String toString() {
        return "Glyph(" + text + ", x=" + x + ", y=" + y + ", s=" + sizeScale + ", italic=" + italic + ")";
    }
}
