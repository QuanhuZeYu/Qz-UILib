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
    private final List<FontSource> candidates;
    public TextStyle(String font, int size, int color) { this(font, size, color, null); }
    public TextStyle(String font, int size, int color, List<FontSource> candidates) {
        this(font, (float)size, color, candidates, TextEffects.NORMAL, TextDecoration.NONE);
    }
    public TextStyle(String font, float logicalEm, int color, List<FontSource> candidates, TextEffects effects, TextDecoration decoration) {
        this(font,logicalEm,color,candidates,effects,decoration,FontStyle.minecraft(effects.bold,effects.italic));
    }
    public TextStyle(String font,float logicalEm,int color,List<FontSource> candidates,TextEffects effects,TextDecoration decoration,FontStyle fontStyle) {
        this.fontStyle=Objects.requireNonNull(fontStyle,"fontStyle");
        if (!Float.isFinite(logicalEm) || logicalEm <= 0 || logicalEm > 32767) throw new IllegalArgumentException("字号必须为有效正数");
        this.font = Objects.requireNonNull(font, "font"); this.logicalEm=logicalEm;this.size=(int)Math.ceil(logicalEm);this.color=color;
        Objects.requireNonNull(effects,"effects");
        // 字重/字姿仅由 FontStyle 决定；遗留布尔字段是派生视图。
        this.effects=new TextEffects(fontStyle.weight>=700,fontStyle.posture!=FontStyle.Posture.NORMAL,effects.underline,effects.strike,effects.obfuscated,effects.boldAdvance,effects.italicShear);
        this.decoration=Objects.requireNonNull(decoration,"decoration");
        if (candidates == null) this.candidates = null;
        else {
            ArrayList<FontSource> copy = new ArrayList<FontSource>(candidates);
            for (FontSource source : copy) Objects.requireNonNull(source, "candidate");
            this.candidates = Collections.unmodifiableList(copy);
        }
    }
    /** null 表示继承名称/默认列表；空列表表示显式没有候选。 */
    public List<FontSource> candidates() { return candidates; }
    public TextStyle withColor(int color) { return new TextStyle(font, logicalEm, color, candidates, effects, decoration,fontStyle); }
    public TextStyle withEffects(TextEffects value) { return new TextStyle(font,logicalEm,color,candidates,value,decoration,fontStyle); }
    public TextStyle withFontStyle(FontStyle value) { return new TextStyle(font,logicalEm,color,candidates,effects,decoration,value); }
    public boolean sameFont(TextStyle s) { return fontStyle.equals(s.fontStyle) && logicalEm==s.logicalEm && font.equals(s.font) && Objects.equals(candidates,s.candidates); }
    @Override public boolean equals(Object o) { if(!(o instanceof TextStyle))return false;TextStyle s=(TextStyle)o;return sameFont(s)&&color==s.color&&effects.equals(s.effects)&&decoration.equals(s.decoration); }
    @Override public int hashCode(){return Objects.hash(font,logicalEm,color,candidates,effects,decoration,fontStyle);}
}
