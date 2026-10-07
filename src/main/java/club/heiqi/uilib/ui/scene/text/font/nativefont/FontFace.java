package club.heiqi.uilib.ui.scene.text.font.nativefont;

/** 固定请求em的线程所属face，创建/使用/关闭均在session所属线程；所有返回值脱离native生命周期。
 * shape读取完整text中的[start,end)，UTF16 cluster仍相对完整串；区间外上下文参与连接，BOT/EOT描述真实行端。
 * 输出顺序及Y向上advance/offset沿整形结果保留，布局决定视觉位置，不在绑定里另建格式/候选引擎。
 * ink/raster使用同glyph与变体且禁止shape；墨迹Y向下，bitmap已premult且bearing/pixelScale为请求单位。
 */
public interface FontFace extends AutoCloseable {
 FontMetrics metrics();
 int glyphIndex(int codePoint);
 ShapeRun shape(String text,int start,int end,ShapeDirection direction,String language,String script,boolean beginningOfText,boolean endOfText);
 default ShapeRun shape(String text,ShapeDirection direction,String language){return shape(text,0,text.length(),direction,language,null,true,true);}
 GlyphBitmap rasterize(int glyphId,GlyphRasterStyle style);
 default GlyphBitmap rasterize(int glyphId){return rasterize(glyphId,GlyphRasterStyle.NORMAL);}
 InkBounds glyphInkBounds(int glyphId,GlyphRasterStyle style);
 default InkBounds glyphInkBounds(int glyphId){return glyphInkBounds(glyphId,GlyphRasterStyle.NORMAL);}
 @Override void close();
}
