package club.heiqi.uilib.ui.scene.text.font.nativefont;

/** cluster为完整上下文串UTF16索引；HB顺序，Y向上，advance/offset为请求em单位。 */
public final class ShapedGlyph {
 public final int glyphId,cluster;public final float xAdvance,yAdvance,xOffset,yOffset;
 public ShapedGlyph(int glyphId,int cluster,float xAdvance,float yAdvance,float xOffset,float yOffset){this.glyphId=glyphId;this.cluster=cluster;this.xAdvance=xAdvance;this.yAdvance=yAdvance;this.xOffset=xOffset;this.yOffset=yOffset;}
}
