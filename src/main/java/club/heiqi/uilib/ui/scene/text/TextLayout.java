package club.heiqi.uilib.ui.scene.text;
import club.heiqi.uilib.ui.scene.text.font.nativefont.InkBounds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphRasterStyle;

/** 度量与绘制共享的不可变定位。int 尺寸为真实浮点边界向上取整，不把 advance 逐字取整。 */
public final class TextLayout {
    public static final class Glyph {
        public final FontSource source;
        public final float size;
        public final float logicalEm, rasterToLayout;
        public final GlyphRasterStyle rasterStyle;
        public final TextStyle resolvedStyle;
        public final int clusterIndex;
        public final int glyphId, logicalStart, logicalEnd, sourceStart, sourceEnd;
        /** layout 左上坐标，Y 向下；x/y 包含 HB offset，y 已含基线。 */
        public final float x, y, xAdvance, yAdvance;
        public final boolean color, replacement;
        /** 实际glyph原点的不可变墨迹边界（Y向下），UNKNOWN时不能据此剔除需求。 */
        public final InkBounds inkBounds;
        public Glyph(FontSource source, float size, int glyphId, int logicalStart, int logicalEnd,
                int sourceStart, int sourceEnd, float x, float y, float xAdvance, float yAdvance,
                boolean color, boolean replacement) {
            this(source, size, glyphId, logicalStart, logicalEnd, sourceStart, sourceEnd, x, y, xAdvance, yAdvance,
                color, replacement, InkBounds.UNKNOWN);
        }
        public Glyph(FontSource source, float size, int glyphId, int logicalStart, int logicalEnd,
                int sourceStart, int sourceEnd, float x, float y, float xAdvance, float yAdvance,
                boolean color, boolean replacement, InkBounds inkBounds) {
            this(source,size,glyphId,logicalStart,logicalEnd,sourceStart,sourceEnd,x,y,xAdvance,yAdvance,color,replacement,inkBounds,size,1,GlyphRasterStyle.NORMAL,null,-1);
        }
        public Glyph(FontSource source,float size,int glyphId,int logicalStart,int logicalEnd,int sourceStart,int sourceEnd,
                float x,float y,float xAdvance,float yAdvance,boolean color,boolean replacement,InkBounds inkBounds,
                float logicalEm,float rasterToLayout,GlyphRasterStyle rasterStyle,TextStyle resolvedStyle,int clusterIndex) {
            if(!Float.isFinite(size)||size<=0||!Float.isFinite(logicalEm)||logicalEm<=0||!Float.isFinite(rasterToLayout)||rasterToLayout<=0)throw new IllegalArgumentException("无效glyph采样尺度");
            this.logicalEm=logicalEm;this.rasterToLayout=rasterToLayout;this.rasterStyle=Objects.requireNonNull(rasterStyle,"rasterStyle");this.resolvedStyle=resolvedStyle;this.clusterIndex=clusterIndex;
            this.source = Objects.requireNonNull(source, "source"); this.size = size; this.glyphId = glyphId;
            this.logicalStart = logicalStart; this.logicalEnd = logicalEnd;
            this.sourceStart = sourceStart; this.sourceEnd = sourceEnd;
            this.x = x; this.y = y; this.xAdvance = xAdvance; this.yAdvance = yAdvance;
            this.color = color; this.replacement = replacement;
            this.inkBounds = Objects.requireNonNull(inkBounds, "inkBounds");
        }
    }
    public static final class Diagnostic {
        public enum Kind { INVALID_INPUT, CONTROL_MARKER, MISSING_GLYPH, FONT_RESOURCE, STYLE_FALLBACK, STYLE_COALESCED, UNSUPPORTED }
        public final Kind kind;
        public final int sourceStart, sourceEnd;
        public final String message;
        public Diagnostic(Kind kind, int sourceStart, int sourceEnd, String message) {
            this.kind = Objects.requireNonNull(kind, "kind"); this.sourceStart = sourceStart;
            this.sourceEnd = sourceEnd; this.message = Objects.requireNonNull(message, "message");
        }
    }
    /** visual cell与墨迹glyph原点是不同事实。 */
    public static final class Cluster {
        public final int logicalStart,logicalEnd,sourceStart,sourceEnd,glyphIndexStart,glyphIndexEnd;
        public final float x,advance;public final TextStyle style;
        /** 最终视觉行的方向事实；投影、采样和重色必须保留。 */
        public final boolean rtl;
        public float leadingX(){return x+(rtl?advance:0);}
        public float trailingX(){return x+(rtl?0:advance);}
        public Cluster(int start,int end,int sourceStart,int sourceEnd,int glyphStart,int glyphEnd,float x,float advance,TextStyle style){this(start,end,sourceStart,sourceEnd,glyphStart,glyphEnd,x,advance,style,false);}
        public Cluster(int start,int end,int sourceStart,int sourceEnd,int glyphStart,int glyphEnd,float x,float advance,TextStyle style,boolean rtl){this.rtl=rtl;logicalStart=start;logicalEnd=end;this.sourceStart=sourceStart;this.sourceEnd=sourceEnd;glyphIndexStart=glyphStart;glyphIndexEnd=glyphEnd;this.x=x;this.advance=advance;this.style=Objects.requireNonNull(style,"style");}
    }
    public static final class Decoration {
        public enum Kind { UNDERLINE, STRIKE }
        public final Kind kind;public final float x,y,width,height;public final int color,logicalStart,logicalEnd,sourceStart,sourceEnd;
        public Decoration(Kind kind,float x,float y,float width,float height,int color,int start,int end,int sourceStart,int sourceEnd){this.kind=kind;this.x=x;this.y=y;this.width=width;this.height=height;this.color=color;logicalStart=start;logicalEnd=end;this.sourceStart=sourceStart;this.sourceEnd=sourceEnd;}
    }
    public static final class Line {
        /** 仍为源文逻辑顺序，控制符可视化不写回此字段。 */
        public final String text;
        public final int x, y, logicalStart, logicalEnd, sourceStart, sourceEnd;
        public final float advance, baseline, ascent, descent, lineHeight;
        private final List<Glyph> glyphs;
        private final List<Cluster> clusters;
        private final List<Decoration> decorations;
        public final boolean paragraphRtl;
        public Line(String text, int x, int y) {
            this(text, x, y, 0, text.length(), 0, text.length(), 0, y, 0, 0, 0, Collections.<Glyph>emptyList());
        }
        public Line(String text, int x, int y, int logicalStart, int logicalEnd, int sourceStart, int sourceEnd,
                float advance, float baseline, float ascent, float descent, float lineHeight, List<Glyph> glyphs) {
            this(text,x,y,logicalStart,logicalEnd,sourceStart,sourceEnd,advance,baseline,ascent,descent,lineHeight,glyphs,Collections.<Cluster>emptyList(),Collections.<Decoration>emptyList());
        }
        public Line(String text,int x,int y,int logicalStart,int logicalEnd,int sourceStart,int sourceEnd,float advance,float baseline,float ascent,float descent,float lineHeight,List<Glyph> glyphs,List<Cluster> clusters,List<Decoration> decorations) {
            this(text,x,y,logicalStart,logicalEnd,sourceStart,sourceEnd,advance,baseline,ascent,descent,lineHeight,glyphs,clusters,decorations,false);
        }
        public Line(String text,int x,int y,int logicalStart,int logicalEnd,int sourceStart,int sourceEnd,float advance,float baseline,float ascent,float descent,float lineHeight,List<Glyph> glyphs,List<Cluster> clusters,List<Decoration> decorations,boolean paragraphRtl) {
            this.paragraphRtl=paragraphRtl;this.clusters=immutable(clusters);this.decorations=immutable(decorations);
            this.text = Objects.requireNonNull(text, "text"); this.x = x; this.y = y;
            this.logicalStart = logicalStart; this.logicalEnd = logicalEnd; this.sourceStart = sourceStart; this.sourceEnd = sourceEnd;
            this.advance = advance; this.baseline = baseline; this.ascent = ascent; this.descent = descent; this.lineHeight = lineHeight;
            this.glyphs = immutable(glyphs);
        }
        public List<Glyph> glyphs() { return glyphs; }
        public List<Cluster> clusters(){return clusters;}
        public List<Decoration> decorations(){return decorations;}
    }
    public final int width, height;
    public final float advanceWidth, advanceHeight;
    public final TextStyle style;
    /** logicalText 为 TextInput 清理后的原逻辑文；originalText 对字符串入口保留业务原串。 */
    public final String logicalText, originalText;
    public final long generation;
    public final TextInput input;
    private final List<Line> lines;
    private final List<Diagnostic> diagnostics;
    public TextLayout(int width, int height, TextStyle style, List<Line> lines) {
        this(width, height, width, height, style, lines, "", "", 0, Collections.<Diagnostic>emptyList());
    }
    public TextLayout(int width, int height, float advanceWidth, float advanceHeight, TextStyle style,
            List<Line> lines, String logicalText, String originalText, long generation, List<Diagnostic> diagnostics) {
        this(width,height,advanceWidth,advanceHeight,style,lines,logicalText,originalText,generation,diagnostics,null);
    }
    public TextLayout(int width,int height,float advanceWidth,float advanceHeight,TextStyle style,List<Line> lines,String logicalText,String originalText,long generation,List<Diagnostic> diagnostics,TextInput input) {
        this.input=input;
        if (width < 0 || height < 0) throw new IllegalArgumentException("文本度量不能为负");
        this.width = width; this.height = height; this.advanceWidth = advanceWidth; this.advanceHeight = advanceHeight;
        this.style = Objects.requireNonNull(style, "style"); this.lines = immutable(lines); this.diagnostics = immutable(diagnostics);
        this.logicalText = Objects.requireNonNull(logicalText, "logicalText"); this.originalText = originalText; this.generation = generation;
    }
    private static <T> List<T> immutable(List<T> values) { return Collections.unmodifiableList(new ArrayList<T>(values)); }
    public List<Line> lines() { return lines; }
    public List<Diagnostic> diagnostics() { return diagnostics; }
    /** 颜色变化只替换样式，不重走匹配、整形及光栅。 */
    public TextLayout withStyle(TextStyle style) {
        Objects.requireNonNull(style,"style");if(style.equals(this.style))return this;
        if(!style.sameFont(this.style)||!style.effects.equals(this.style.effects)||!style.decoration.equals(this.style.decoration))throw new IllegalArgumentException("已定位计划只允许统一颜色重绑定");
        List<Line> recolored=new ArrayList<Line>();
        for(Line line:lines){List<Glyph> glyphs=new ArrayList<Glyph>();List<Cluster> clusters=new ArrayList<Cluster>();List<Decoration> decorations=new ArrayList<Decoration>();
            for(Glyph g:line.glyphs()){TextStyle paint=g.resolvedStyle==null?style:g.resolvedStyle.withColor(style.color);
                glyphs.add(new Glyph(g.source,g.size,g.glyphId,g.logicalStart,g.logicalEnd,g.sourceStart,g.sourceEnd,g.x,g.y,g.xAdvance,g.yAdvance,g.color,g.replacement,g.inkBounds,g.logicalEm,g.rasterToLayout,g.rasterStyle,paint,g.clusterIndex));}
            for(Cluster c:line.clusters())clusters.add(new Cluster(c.logicalStart,c.logicalEnd,c.sourceStart,c.sourceEnd,c.glyphIndexStart,c.glyphIndexEnd,c.x,c.advance,c.style.withColor(style.color),c.rtl));
            for(Decoration d:line.decorations())decorations.add(new Decoration(d.kind,d.x,d.y,d.width,d.height,style.color,d.logicalStart,d.logicalEnd,d.sourceStart,d.sourceEnd));
            recolored.add(new Line(line.text,line.x,line.y,line.logicalStart,line.logicalEnd,line.sourceStart,line.sourceEnd,line.advance,line.baseline,line.ascent,line.descent,line.lineHeight,glyphs,clusters,decorations,line.paragraphRtl));
        }
        return new TextLayout(width,height,advanceWidth,advanceHeight,style,recolored,logicalText,originalText,generation,diagnostics,input);
    }
}
