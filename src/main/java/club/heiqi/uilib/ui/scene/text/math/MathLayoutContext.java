package club.heiqi.uilib.ui.scene.text.math;

import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 完整样式作用域；textStyle.logicalEm 与 em 都是当前样式实际字号。 */
final class MathLayoutContext {
    final MathStyle style;
    final String alphabet;
    // 文字 literal SPACE 与 glyph 共享 native run；不以数学 em 间距替代其真实整形度量。
    final boolean cramped, display, textMode;
    final TextStyle textStyle;
    final float em, sizeEm, scriptPercent, scriptScriptPercent;

    MathLayoutContext(
            MathStyle style,
            String alphabet,
            boolean cramped,
            TextStyle scope,
            float sizeEm,
            float scriptPercent,
            float scriptScriptPercent,
            boolean display) {
        this(
                style,
                alphabet,
                cramped,
                scope,
                sizeEm,
                scriptPercent,
                scriptScriptPercent,
                display,
                false);
    }

    private MathLayoutContext(
            MathStyle style,
            String alphabet,
            boolean cramped,
            TextStyle scope,
            float sizeEm,
            float scriptPercent,
            float scriptScriptPercent,
            boolean display,
            boolean textMode) {
        this.textMode = textMode;
        this.style = style;
        this.alphabet = alphabet;
        this.cramped = cramped;
        this.display = display;
        this.sizeEm = sizeEm;
        this.scriptPercent = scriptPercent;
        this.scriptScriptPercent = scriptScriptPercent;
        em =
                sizeEm
                        * (style == MathStyle.SCRIPT
                                ? scriptPercent / 100f
                                : style == MathStyle.SCRIPT_SCRIPT
                                        ? scriptScriptPercent / 100f
                                        : 1);
        textStyle =
                new TextStyle(
                        scope.font,
                        em,
                        scope.color,
                        scope.candidates(),
                        scope.effects,
                        scope.decoration,
                        scope.fontStyle);
    }

    // 自动 frac/script 派生保留数学字族与 cramped；显式声明按 fixed styling 解除两者，不重置普通字体作用域。
    MathLayoutContext style(MathStyle value) {
        return copy(value, alphabet, cramped, textStyle, sizeEm);
    }

    MathLayoutContext declaredStyle(MathStyle value) {
        return copy(value, "mathnormal", false, textStyle, sizeEm);
    }

    MathLayoutContext cramped() {
        return copy(style, alphabet, true, textStyle, sizeEm);
    }

    MathLayoutContext alphabet(String value) {
        return copy(style, value, cramped, textStyle, sizeEm);
    }

    MathLayoutContext display(boolean value) {
        return new MathLayoutContext(
                style,
                alphabet,
                cramped,
                textStyle,
                sizeEm,
                scriptPercent,
                scriptScriptPercent,
                value,
                textMode);
    }

    MathLayoutContext textMode(boolean value) {
        return new MathLayoutContext(
                style,
                alphabet,
                cramped,
                textStyle,
                sizeEm,
                scriptPercent,
                scriptScriptPercent,
                display,
                value);
    }

    MathLayoutContext color(int value) {
        return copy(style, alphabet, cramped, textStyle.withColor(value), sizeEm);
    }

    MathLayoutContext size(float value) {
        return copy(style, alphabet, cramped, textStyle, value);
    }

    MathLayoutContext textStyle(TextStyle value) {
        return copy(style, alphabet, cramped, value, sizeEm);
    }

    private MathLayoutContext copy(
            MathStyle style, String alphabet, boolean cramped, TextStyle scope, float sizeEm) {
        return new MathLayoutContext(
                style,
                alphabet,
                cramped,
                scope,
                sizeEm,
                scriptPercent,
                scriptScriptPercent,
                display,
                textMode);
    }
}
