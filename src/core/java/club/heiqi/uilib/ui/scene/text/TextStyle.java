package club.heiqi.uilib.ui.scene.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import club.heiqi.uilib.ui.scene.text.font.FontSource;

/** 值型文本样式。名称由字体服务映射到列表；显式列表完整覆盖名称，不隐含追加候选。 */
public final class TextStyle {
    public final String font;
    public final int size, color;
    public final float logicalEm;
    public final TextEffects effects;
    public final FontStyle fontStyle;
    public final TextDecoration decoration;
    /** 正值基线下移；字距只作用于完整安全簇之间，不能使簇 advance 为负。 */
    public final float baselineShift, letterSpacing;
    /** null 表示无背景；0 是有效透明背景。默认色供未显式着色的 mark 动态继承。 */
    public final Integer highlightColor;
    public final int defaultHighlightColor;
    public final TextScript script;
    private final List<FontSource> candidates;

    public TextStyle(String font, int size, int color) {
        this(font, size, color, null);
    }

    public TextStyle(String font, int size, int color, List<FontSource> candidates) {
        this(font, (float) size, color, candidates, TextEffects.NORMAL, TextDecoration.NONE);
    }

    public TextStyle(
            String font,
            float logicalEm,
            int color,
            List<FontSource> candidates,
            TextEffects effects,
            TextDecoration decoration) {
        this(
                font,
                logicalEm,
                color,
                candidates,
                effects,
                decoration,
                FontStyle.minecraft(effects.bold, effects.italic));
    }

    public TextStyle(
            String font,
            float logicalEm,
            int color,
            List<FontSource> candidates,
            TextEffects effects,
            TextDecoration decoration,
            FontStyle fontStyle) {
        this(font, logicalEm, color, candidates, effects, decoration, fontStyle,
                0, 0, null, 0x66FFD54F, TextScript.NONE);
    }

    public TextStyle(String font, float logicalEm, int color, List<FontSource> candidates,
            TextEffects effects, TextDecoration decoration, FontStyle fontStyle,
            float baselineShift, float letterSpacing, Integer highlightColor, int defaultHighlightColor) {
        this(font, logicalEm, color, candidates, effects, decoration, fontStyle,
                baselineShift, letterSpacing, highlightColor, defaultHighlightColor, TextScript.NONE);
    }

    public TextStyle(String font, float logicalEm, int color, List<FontSource> candidates,
            TextEffects effects, TextDecoration decoration, FontStyle fontStyle,
            float baselineShift, float letterSpacing, Integer highlightColor, int defaultHighlightColor,
            TextScript script) {
        if (!Float.isFinite(baselineShift) || !Float.isFinite(letterSpacing))
            throw new IllegalArgumentException("基线与字距必须为有限 logical px");
        this.baselineShift = baselineShift;
        this.letterSpacing = letterSpacing;
        this.highlightColor = highlightColor;
        this.defaultHighlightColor = defaultHighlightColor;
        this.script = Objects.requireNonNull(script, "script");
        this.fontStyle = Objects.requireNonNull(fontStyle, "fontStyle");
        if (!Float.isFinite(logicalEm) || logicalEm <= 0 || logicalEm > 32767)
            throw new IllegalArgumentException("字号必须为有效正数");
        this.font = Objects.requireNonNull(font, "font");
        this.logicalEm = logicalEm;
        this.size = (int) Math.ceil(logicalEm);
        this.color = color;
        Objects.requireNonNull(effects, "effects");
        // 字重/字姿仅由 FontStyle 决定；遗留布尔字段是派生视图。
        this.effects =
                new TextEffects(
                        fontStyle.weight >= 700,
                        fontStyle.posture != FontStyle.Posture.NORMAL,
                        effects.underline,
                        effects.strike,
                        effects.obfuscated,
                        effects.boldAdvance,
                        effects.italicShear);
        this.decoration = Objects.requireNonNull(decoration, "decoration");
        if (candidates == null) this.candidates = null;
        else {
            ArrayList<FontSource> copy = new ArrayList<FontSource>(candidates);
            for (FontSource source : copy) Objects.requireNonNull(source, "candidate");
            this.candidates = Collections.unmodifiableList(copy);
        }
    }

    /** null 表示继承名称/默认列表；空列表表示显式没有候选。 */
    public List<FontSource> candidates() {
        return candidates;
    }

    public TextStyle withEm(float value) {
        return new TextStyle(font, value, color, candidates, effects, decoration, fontStyle,
                baselineShift, letterSpacing, highlightColor, defaultHighlightColor, script);
    }
    public TextStyle withColor(int color) {
        return new TextStyle(font, logicalEm, color, candidates, effects, decoration, fontStyle, baselineShift, letterSpacing, highlightColor, defaultHighlightColor, script);
    }

    public TextStyle withEffects(TextEffects value) {
        return new TextStyle(font, logicalEm, color, candidates, value, decoration, fontStyle, baselineShift, letterSpacing, highlightColor, defaultHighlightColor, script);
    }

    public TextStyle withFontStyle(FontStyle value) {
        return new TextStyle(font, logicalEm, color, candidates, effects, decoration, value, baselineShift, letterSpacing, highlightColor, defaultHighlightColor, script);
    }

    public TextStyle withBaselineShift(float value) {
        return new TextStyle(font, logicalEm, color, candidates, effects, decoration, fontStyle,
                value, letterSpacing, highlightColor, defaultHighlightColor, script);
    }
    public TextStyle withLetterSpacing(float value) {
        return new TextStyle(font, logicalEm, color, candidates, effects, decoration, fontStyle,
                baselineShift, value, highlightColor, defaultHighlightColor, script);
    }
    public TextStyle withHighlight(Integer value) {
        return new TextStyle(font, logicalEm, color, candidates, effects, decoration, fontStyle,
                baselineShift, letterSpacing, value, defaultHighlightColor, script);
    }
    public TextStyle withDefaultHighlightColor(int value) {
        return new TextStyle(font, logicalEm, color, candidates, effects, decoration, fontStyle,
                baselineShift, letterSpacing, highlightColor, value, script);
    }
    public TextStyle withScript(TextScript value) {
        return new TextStyle(font, logicalEm, color, candidates, effects, decoration, fontStyle,
                baselineShift, letterSpacing, highlightColor, defaultHighlightColor, value);
    }
    public boolean sameFont(TextStyle s) {
        return fontStyle.equals(s.fontStyle)
                && logicalEm == s.logicalEm
                && baselineShift == s.baselineShift
                && letterSpacing == s.letterSpacing
                && script.equals(s.script)
                && font.equals(s.font)
                && Objects.equals(candidates, s.candidates);
    }

    /** 正式节点与已定位文字共用的保守布局分类；默认mark色在内容解析时消费，
     * 已解析的实际highlightColor仍参与定位键，不在此推断富文本颜色继承。 */
    public boolean sameLayout(TextStyle s) {
        return sameFont(s)&&effects.equals(s.effects)&&decoration.equals(s.decoration)
                &&Objects.equals(highlightColor,s.highlightColor);
    }
    /** 普通文字请求的有界缓存键；前景和默认mark色保留在当前绘制声明。 */
    public TextStyle layoutStyle() {
        if(color==0&&defaultHighlightColor==0)return this;
        return new TextStyle(font,logicalEm,0,candidates,effects,decoration,fontStyle,
                baselineShift,letterSpacing,highlightColor,0,script);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TextStyle)) return false;
        TextStyle s = (TextStyle) o;
        return sameFont(s)
                && color == s.color
                && effects.equals(s.effects)
                && decoration.equals(s.decoration)
                && Objects.equals(highlightColor, s.highlightColor)
                && defaultHighlightColor == s.defaultHighlightColor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(font, logicalEm, color, candidates, effects, decoration, fontStyle, baselineShift, letterSpacing, highlightColor, defaultHighlightColor, script);
    }
}
