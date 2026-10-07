package club.heiqi.uilib.ui.scene.text.font.glyph;

import java.util.Objects;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.font.FontSource;

/** 光栅身份绑定实际不可变 source 快照，绝不以字体名称替换旧计划资源。 */
public final class GlyphKey {
    public final FontSource source;
    public final float size;
    public final int glyphId;
    public GlyphKey(FontSource source,float size,int glyphId) {
        this.source=Objects.requireNonNull(source,"source");
        if(!Float.isFinite(size)||size<=0||glyphId<0)throw new IllegalArgumentException("无效字形身份");
        this.size=size;this.glyphId=glyphId;
    }
    public static GlyphKey of(TextLayout.Glyph glyph){return new GlyphKey(glyph.source,glyph.size,glyph.glyphId);}
    @Override public boolean equals(Object other) {
        if(!(other instanceof GlyphKey))return false;GlyphKey k=(GlyphKey)other;
        return source==k.source && Float.floatToIntBits(size)==Float.floatToIntBits(k.size) && glyphId==k.glyphId;
    }
    @Override public int hashCode(){return 31*(31*System.identityHashCode(source)+Float.floatToIntBits(size))+glyphId;}
    @Override public String toString(){return source+"/"+size+"/"+glyphId;}
}
