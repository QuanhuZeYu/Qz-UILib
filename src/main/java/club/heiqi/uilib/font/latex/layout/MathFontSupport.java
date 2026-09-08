package club.heiqi.uilib.font.latex.layout;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.latex.MathFontStyle;

/** 数学字体可选能力；几何均为有效整数字号下 logical px，调用侧不得再次按字号换算。 */
public interface MathFontSupport {
    /** 解析实际 face/glyph；无法解析返回 null，不能把 .notdef 当作匹配成功。 */
    MathGlyphRef resolve(int codepoint, MathFontStyle style, FontType weight);

    /** 测量已注册的内容身份；effectiveSizePx 必须为正，不得把未知 faceKey 偷换成另一字体。 */
    MathGlyphMetrics measure(MathGlyphRef glyph, int effectiveSizePx);

    /** 当前数学字体的排版常量；effectiveSizePx 必须为正。 */
    MathFontParameters constants(int effectiveSizePx);

    /** 返回该方向的变体/拼接配方，没有配方时返回 null；effectiveSizePx 必须为正。 */
    MathGlyphConstruction construction(MathGlyphRef glyph, MathStretchAxis axis, int effectiveSizePx);
}
