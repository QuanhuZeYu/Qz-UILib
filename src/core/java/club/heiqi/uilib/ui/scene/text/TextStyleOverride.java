package club.heiqi.uilib.ui.scene.text;

import java.util.Objects;

/** 稳定的属性意图；未声明的属性在 resolve 时继承当前基础样式，不冻结字体资源。 */
public final class TextStyleOverride {
    public static final TextStyleOverride INHERIT = builder().build();
    private final Integer color, weight;
    private final String font;
    private final boolean inheritAlpha;
    private final FontStyle.Posture posture;
    private final Float em, letterSpacing;
    private final Boolean highlighted;
    private final Integer highlightColor;
    private final TextScript script;
    private final Boolean underline, strike, obfuscated;
    private final boolean decorationFromEm;

    private TextStyleOverride(Builder b) {
        color = b.color;
        font = b.font;
        weight = b.weight;
        inheritAlpha = b.inheritAlpha;
        posture = b.posture;
        em = b.em;
        letterSpacing = b.letterSpacing;
        highlighted = b.highlighted;
        highlightColor = b.highlightColor;
        script = b.script;
        underline = b.underline;
        strike = b.strike;
        obfuscated = b.obfuscated;
        decorationFromEm = b.decorationFromEm;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public TextStyle resolve(TextStyle base) {
        Objects.requireNonNull(base, "base");
        TextScript operations = base.script.then(script);
        float size = base.logicalEm;
        if (!operations.hasScript()) {
            for (TextScript.Step step : operations.steps()) size = step.em;
            operations = TextScript.NONE;
        }
        Integer background = highlighted == null ? base.highlightColor
                : !highlighted ? null : highlightColor == null ? base.defaultHighlightColor : highlightColor;
        int tint =
                color == null
                        ? base.color
                        : inheritAlpha ? (base.color & 0xff000000) | (color & 0xffffff) : color;
        FontStyle font =
                new FontStyle(
                        weight == null ? base.fontStyle.weight : weight,
                        posture == null ? base.fontStyle.posture : posture);
        TextEffects old = base.effects;
        TextEffects effects =
                new TextEffects(
                        old.bold,
                        old.italic,
                        underline == null ? old.underline : underline,
                        strike == null ? old.strike : strike,
                        obfuscated == null ? old.obfuscated : obfuscated,
                        old.boldAdvance,
                        old.italicShear);
        TextDecoration d = base.decoration;
        if (decorationFromEm)
            d = d.fromEm(size, Boolean.TRUE.equals(underline), Boolean.TRUE.equals(strike));
        else d = d.resolveEm(size);
        return new TextStyle(
                this.font == null ? base.font : this.font,
                size,
                tint,
                this.font == null ? base.candidates() : null,
                effects,
                d,
                font, base.baselineShift, letterSpacing == null ? base.letterSpacing : letterSpacing,
                background, base.defaultHighlightColor, operations);
    }

    public static final class Builder {
        private Integer color, weight;
        private String font;
        private boolean inheritAlpha;
        private FontStyle.Posture posture;
        private Float em, letterSpacing;
        private Boolean highlighted;
        private Integer highlightColor;
        private TextScript script = TextScript.NONE;
        private Boolean underline, strike, obfuscated;
        private boolean decorationFromEm;

        private Builder() {}

        private Builder(TextStyleOverride s) {
            color = s.color;
            font = s.font;
            weight = s.weight;
            inheritAlpha = s.inheritAlpha;
            posture = s.posture;
            em = s.em;
            letterSpacing = s.letterSpacing;
            highlighted = s.highlighted;
            highlightColor = s.highlightColor;
            script = s.script;
            underline = s.underline;
            strike = s.strike;
            obfuscated = s.obfuscated;
            decorationFromEm = s.decorationFromEm;
        }

        /** 显式名称在每次resolve后由字体服务动态映射；不沿用覆盖其他族的base候选。 */
        public Builder font(String value) {
            font = Objects.requireNonNull(value, "font");
            if (font.isEmpty()) throw new IllegalArgumentException("字体名称不能为空");
            return this;
        }

        public Builder rgb(int value) {
            color = value & 0xffffff;
            inheritAlpha = true;
            return this;
        }

        public Builder argb(int value) {
            color = value;
            inheritAlpha = false;
            return this;
        }

        public Builder weight(int value) {
            new FontStyle(value, FontStyle.Posture.NORMAL);
            weight = value;
            return this;
        }

        public Builder posture(FontStyle.Posture value) {
            posture = Objects.requireNonNull(value, "posture");
            return this;
        }

        public Builder em(float value) {
            if (!Float.isFinite(value) || value <= 0 || value > 32767)
                throw new IllegalArgumentException("无效字号");
            em = value;
            script = script.appendEm(value);
            return this;
        }

        /** 每层脚本保留独立步骤，闭合由声明作用域恢复。 */
        public Builder superscript() { script = script.appendScript(true); return this; }
        public Builder subscript() { script = script.appendScript(false); return this; }
        /** 未显式着色的高亮在每次 resolve 时读取当前主题默认色。 */
        public Builder highlight() { highlighted = true; highlightColor = null; return this; }
        public Builder highlight(int argb) { highlighted = true; highlightColor = argb; return this; }
        public Builder noHighlight() { highlighted = false; highlightColor = null; return this; }
        public Builder letterSpacing(float value) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("字距必须为有限 logical px");
            letterSpacing = value;
            return this;
        }
        public Builder underline(boolean value) {
            underline = value;
            return this;
        }

        public Builder strike(boolean value) {
            strike = value;
            return this;
        }

        public Builder obfuscated(boolean value) {
            obfuscated = value;
            return this;
        }

        public Builder decorationFromEm(boolean value) {
            decorationFromEm = value;
            return this;
        }

        public TextStyleOverride build() {
            return new TextStyleOverride(this);
        }
    }
}
