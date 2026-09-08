package club.heiqi.uilib.font.latex.layout;

import club.heiqi.uilib.font.internal.LatexFontSize;
import club.heiqi.uilib.font.latex.MathStyleOverride;

/** 内部不可变数学样式；所有字号直接从根字号推导，不递归缩小。 */
final class MathStyle {
    enum Level { DISPLAY, TEXT, SCRIPT, SCRIPTSCRIPT }

    final float rootSize;
    final Level level;
    final boolean cramped;

    MathStyle(float rootSize, Level level, boolean cramped) {
        // 与 LatexFontSize 的最低 1px / 非有限值转换口径一致，避免根倍率出现 NaN 或负值。
        this.rootSize = rootSize > 0.0F && !Float.isInfinite(rootSize)
                ? rootSize : LatexFontSize.effective(rootSize);
        this.level = level;
        this.cramped = cramped;
    }

    float size() {
        float scale = level == Level.SCRIPT ? MathConstants.SCRIPT_SCALE
                : level == Level.SCRIPTSCRIPT ? MathConstants.SCRIPT_SCRIPT_SCALE : 1.0F;
        return LatexFontSize.effective(rootSize * scale);
    }

    boolean isDisplay() {
        return level == Level.DISPLAY;
    }

    boolean isScript() {
        return level == Level.SCRIPT || level == Level.SCRIPTSCRIPT;
    }

    /** 显式声明从根字号选级，并解除继承的 cramped。 */
    MathStyle withOverride(MathStyleOverride override) {
        switch (override) {
            case DISPLAY: return new MathStyle(rootSize, Level.DISPLAY, false);
            case TEXT: return new MathStyle(rootSize, Level.TEXT, false);
            case SCRIPT: return new MathStyle(rootSize, Level.SCRIPT, false);
            case SCRIPTSCRIPT: return new MathStyle(rootSize, Level.SCRIPTSCRIPT, false);
            default: return this;
        }
    }

    MathStyle cramp() {
        return cramped ? this : new MathStyle(rootSize, level, true);
    }

    MathStyle superscript() {
        return new MathStyle(rootSize, isScript() ? Level.SCRIPTSCRIPT : Level.SCRIPT, cramped);
    }

    MathStyle subscript() {
        return superscript().cramp();
    }

    MathStyle numerator() {
        return isDisplay() ? new MathStyle(rootSize, Level.TEXT, cramped) : superscript();
    }

    MathStyle denominator() {
        return numerator().cramp();
    }

    MathStyle rootIndex() {
        return new MathStyle(rootSize, Level.SCRIPTSCRIPT, false);
    }

    MathStyle matrixCell() {
        return new MathStyle(rootSize, Level.TEXT, false);
    }
}
