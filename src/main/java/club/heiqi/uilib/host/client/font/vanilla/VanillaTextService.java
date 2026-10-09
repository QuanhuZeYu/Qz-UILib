package club.heiqi.uilib.host.client.font.vanilla;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Supplier;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.Bidi;
import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.RasterSampling;
import club.heiqi.uilib.ui.scene.text.StyleSpan;
import club.heiqi.uilib.ui.scene.text.TextDecoration;
import club.heiqi.uilib.ui.scene.text.TextEffects;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.format.MinecraftFormat;

/** 原版标准文字语义适配。格式解析与度量均复用共享服务，不建立逐char排字引擎或scene。
 * 创建/使用在线程专属FontTextService所属线程。host负责PresentationEpoch、GL和资源生命周期。
 */
public final class VanillaTextService {
    private static final int ENTRY_LIMIT=128, TEXT_LIMIT=8192;
    private final Supplier<FontTextService> provider;
    private final LinkedHashMap<IdentitySampleKey,TextLayout> planSamples=new LinkedHashMap<IdentitySampleKey,TextLayout>(16,.75f,true);
    private final LinkedHashMap<SampleKey,TextLayout> painted=new LinkedHashMap<SampleKey,TextLayout>(16,.75f,true);
    private FontTextService current;
    private long generation;
    private final LinkedHashMap<ParseKey,AttributedText> parsed=new LinkedHashMap<ParseKey,AttributedText>(16,.75f,true);
    private final LinkedHashMap<LayoutKey,TextLayout> layouts=new LinkedHashMap<LayoutKey,TextLayout>(16,.75f,true);
    private final LinkedHashMap<SampleKey,TextLayout> samples=new LinkedHashMap<SampleKey,TextLayout>(16,.75f,true);

    /** palette含实际宿主32色；em与lineHeight分别表达字体尺寸和可写FONT_HEIGHT合同。 */
    public static final class Settings {
        /** baseline保存caller纵向锚点：旧构造器为基线，bottomAligned工厂为框底；实际基线见Line.baseline。 */
        public final float logicalEm,lineHeight,baseline;
        public final boolean bidi,unicode;
        private final boolean bottomAligned;
        private final int[] palette;
        public Settings(float logicalEm,float lineHeight,float baseline,boolean bidi,boolean unicode,int[] palette){
            this(logicalEm,lineHeight,baseline,bidi,unicode,palette,false);
        }
        /** bottom为caller贴图框底部；使用行的真实下降部换算基线，不按每字墨迹底部摆字。 */
        public static Settings bottomAligned(float logicalEm,float lineHeight,float bottom,boolean bidi,boolean unicode,int[] palette){
            return new Settings(logicalEm,lineHeight,bottom,bidi,unicode,palette,true);
        }
        private Settings(float logicalEm,float lineHeight,float baseline,boolean bidi,boolean unicode,int[] palette,boolean bottomAligned){
            if(!Float.isFinite(logicalEm)||logicalEm<=0||logicalEm>32767||!Float.isFinite(lineHeight)||lineHeight<=0||!Float.isFinite(baseline)||palette.length!=32)throw new IllegalArgumentException("无效原版字体设置");
            this.logicalEm=logicalEm;this.lineHeight=lineHeight;this.baseline=baseline;this.bidi=bidi;this.unicode=unicode;this.palette=palette.clone();this.bottomAligned=bottomAligned;
        }
        public int paletteColor(int index){return palette[index];}
        /** 阴影调用使用同一快照的后16色；baseColor由调用者应用旧alpha/shadow规则。 */
        public Settings shadow(){int[] p=palette.clone();System.arraycopy(palette,16,p,0,16);return new Settings(logicalEm,lineHeight,baseline,bidi,unicode,p,bottomAligned);}
        @Override public boolean equals(Object o){if(!(o instanceof Settings))return false;Settings s=(Settings)o;return logicalEm==s.logicalEm&&lineHeight==s.lineHeight&&baseline==s.baseline&&bottomAligned==s.bottomAligned&&bidi==s.bidi&&unicode==s.unicode&&Arrays.equals(palette,s.palette);}
        @Override public int hashCode(){return Objects.hash(logicalEm,lineHeight,baseline,bottomAligned,bidi,unicode,Arrays.hashCode(palette));}
    }
    public VanillaTextService(FontTextService service){this(()->service);}
    public VanillaTextService(Supplier<FontTextService> provider){this.provider=Objects.requireNonNull(provider,"provider");}
    private FontTextService service(){FontTextService s=Objects.requireNonNull(provider.get(),"font service");long g=s.generation();if(s!=current||g!=generation){clear();current=s;generation=g;}return s;}
    public void clear(){parsed.clear();layouts.clear();samples.clear();painted.clear();planSamples.clear();}
    public int cachedLayoutCount(){return layouts.size();}
    public int cachedParseCount(){return parsed.size();}
    public int cachedSampleCount(){return samples.size()+planSamples.size();}
    private static <K,V> void put(LinkedHashMap<K,V> cache,K key,V value){cache.put(key,value);while(cache.size()>ENTRY_LIMIT)cache.remove(cache.keySet().iterator().next());}
    private static TextEffects withoutObfuscation(TextEffects e){return new TextEffects(e.bold,e.italic,e.underline,e.strike,false,e.boldAdvance,e.italicShear);}
    private AttributedText parse(String raw,Settings settings,int color){
        ParseKey key=new ParseKey(raw,settings,color);AttributedText cached=parsed.get(key);if(cached!=null)return cached;
        TextDecoration decoration=new TextDecoration(settings.lineHeight-1-settings.baseline,1,-1,settings.lineHeight/2-1-settings.baseline,1);
        TextStyle base=new TextStyle("minecraft",settings.logicalEm,color,current.defaultCandidates(),TextEffects.NORMAL,decoration);
        AttributedText result=MinecraftFormat.parse(raw,base,new MinecraftFormat.Context(){
            public int paletteColor(int i){return settings.paletteColor(i);}
            public TextEffects effects(boolean b,boolean i,boolean u,boolean s,boolean k){return new TextEffects(b,i,u,s,k,1,.25f);}
            public TextDecoration decoration(){return decoration;}
        });
        if(raw.length()<=TEXT_LIMIT)put(parsed,key,result);return result;
    }
    private static AttributedText measurable(AttributedText request){
        List<StyleSpan> spans=new ArrayList<StyleSpan>();for(StyleSpan s:request.spans())spans.add(new StyleSpan(s.logicalStart,s.logicalEnd,s.style.withEffects(withoutObfuscation(s.style.effects))));
        return new AttributedText(request.input,request.originalText,request.baseStyle.withEffects(withoutObfuscation(request.baseStyle.effects)),spans,request.trailingStyle.withEffects(withoutObfuscation(request.trailingStyle.effects)),request.diagnostics());
    }
    private static TextPolicy policy(Settings settings,TextPolicy input){return new TextPolicy(settings.bidi?TextPolicy.Direction.AUTO:TextPolicy.Direction.LTR,input.wrap,input.language,input.tabSize,settings.lineHeight);}
    /** caller原点按显式基线或贴图框底部对齐；底部模式扣除真实下降部，保留整行共享基线。 */
    private static TextLayout align(TextLayout layout,Settings settings){
        List<TextLayout.Line> lines=new ArrayList<TextLayout.Line>();float top=0;
        for(TextLayout.Line line:layout.lines()){
            float baseline=top+settings.baseline-(settings.bottomAligned?line.descent:0);
            float delta=baseline-line.baseline,decorationDelta=top+settings.baseline-line.baseline;List<TextLayout.Glyph> glyphs=new ArrayList<TextLayout.Glyph>();List<TextLayout.Decoration> decorations=new ArrayList<TextLayout.Decoration>();
            for(TextLayout.Glyph g:line.glyphs())glyphs.add(new TextLayout.Glyph(g.source,g.size,g.glyphId,g.logicalStart,g.logicalEnd,g.sourceStart,g.sourceEnd,g.x,g.y+delta,g.xAdvance,g.yAdvance,g.color,g.replacement,g.inkBounds,g.logicalEm,g.rasterToLayout,g.rasterStyle,g.resolvedStyle,g.clusterIndex));
            for(TextLayout.Decoration d:line.decorations())decorations.add(new TextLayout.Decoration(d.kind,d.x,d.y+decorationDelta,d.width,d.height,d.color,d.logicalStart,d.logicalEnd,d.sourceStart,d.sourceEnd));
            lines.add(new TextLayout.Line(line.text,line.x,(int)top,line.logicalStart,line.logicalEnd,line.sourceStart,line.sourceEnd,line.advance,baseline,line.ascent,line.descent,settings.lineHeight,glyphs,line.clusters(),decorations));top+=settings.lineHeight;
        }
        return new TextLayout(layout.width,(int)Math.ceil(top),layout.advanceWidth,top,layout.style,lines,layout.logicalText,layout.originalText,layout.generation,layout.diagnostics(),layout.input);
    }
    /** 彩色码与base颜色重绑到簇，不走HB；显式§f不会误继承新的base颜色。 */
    private static TextLayout paint(TextLayout geometry,AttributedText request){
        List<TextLayout.Line> lines=new ArrayList<TextLayout.Line>();
        for(TextLayout.Line line:geometry.lines()){
            List<TextLayout.Glyph> glyphs=new ArrayList<TextLayout.Glyph>();List<TextLayout.Cluster> clusters=new ArrayList<TextLayout.Cluster>();List<TextLayout.Decoration> decorations=new ArrayList<TextLayout.Decoration>();
            for(TextLayout.Glyph g:line.glyphs()){TextStyle style=g.resolvedStyle.withColor(request.styleAt(g.logicalStart).color).withEffects(request.styleAt(g.logicalStart).effects);glyphs.add(new TextLayout.Glyph(g.source,g.size,g.glyphId,g.logicalStart,g.logicalEnd,g.sourceStart,g.sourceEnd,g.x,g.y,g.xAdvance,g.yAdvance,g.color,g.replacement,g.inkBounds,g.logicalEm,g.rasterToLayout,g.rasterStyle,style,g.clusterIndex));}
            for(TextLayout.Cluster c:line.clusters())clusters.add(new TextLayout.Cluster(c.logicalStart,c.logicalEnd,c.sourceStart,c.sourceEnd,c.glyphIndexStart,c.glyphIndexEnd,c.x,c.advance,c.style.withColor(request.styleAt(c.logicalStart).color).withEffects(request.styleAt(c.logicalStart).effects)));
            for(TextLayout.Decoration d:line.decorations())decorations.add(new TextLayout.Decoration(d.kind,d.x,d.y,d.width,d.height,request.styleAt(d.logicalStart).color,d.logicalStart,d.logicalEnd,d.sourceStart,d.sourceEnd));
            lines.add(new TextLayout.Line(line.text,line.x,line.y,line.logicalStart,line.logicalEnd,line.sourceStart,line.sourceEnd,line.advance,line.baseline,line.ascent,line.descent,line.lineHeight,glyphs,clusters,decorations));
        }
        return new TextLayout(geometry.width,geometry.height,geometry.advanceWidth,geometry.advanceHeight,request.baseStyle,lines,geometry.logicalText,request.originalText,geometry.generation,geometry.diagnostics(),request.input);
    }
    public TextLayout layout(String raw,Settings settings,int baseColor,float width,TextPolicy inputPolicy){
        FontTextService font=service();Objects.requireNonNull(raw,"raw");Objects.requireNonNull(settings,"settings");
        TextPolicy p=policy(settings,inputPolicy);LayoutKey key=new LayoutKey(raw,settings,width,p);TextLayout geometry=layouts.get(key);
        if(geometry==null){int canonical=canonicalColor(settings);geometry=align(font.layout(measurable(parse(raw,settings,canonical)),width,p),settings);if(raw.length()<=TEXT_LIMIT)put(layouts,key,geometry);}
        SampleKey paintKey=new SampleKey(key,baseColor,RasterSampling.LOGICAL,0,0);TextLayout result=painted.get(paintKey);
        if(result==null){result=paint(geometry,parse(raw,settings,baseColor));if(raw.length()<=TEXT_LIMIT)put(painted,paintKey,result);}return result;
    }
    public TextLayout sample(String raw,Settings settings,int baseColor,float width,TextPolicy policy,RasterSampling sampling,long tick,long salt){
        FontTextService font=service();AttributedText request=parse(raw,settings,baseColor);boolean obfuscated=false;for(StyleSpan span:request.spans())obfuscated|=span.style.effects.obfuscated;
        SampleKey key=new SampleKey(new LayoutKey(raw,settings,width,policy(settings,policy)),baseColor,sampling,obfuscated?tick:0,obfuscated?salt:0);
        TextLayout result=samples.get(key);if(result!=null)return result;
        TextLayout logical=layout(raw,settings,baseColor,width,policy);result=sample(logical,sampling,tick,salt);if(raw.length()<=TEXT_LIMIT)put(samples,key,result);while(samples.size()+planSamples.size()>ENTRY_LIMIT&&!planSamples.isEmpty())planSamples.remove(planSamples.keySet().iterator().next());return result;
    }
    /** 播放时仅使用已冻结的logical glyph/cluster/source/style快照；不重新读取raw或宿主设置。 */
    public TextLayout sample(TextLayout logical,RasterSampling sampling,long tick,long salt){
        FontTextService font=service();boolean obfuscated=false;for(TextLayout.Line line:logical.lines())for(TextLayout.Cluster cluster:line.clusters())obfuscated|=cluster.style.effects.obfuscated;
        IdentitySampleKey key=new IdentitySampleKey(logical,sampling,obfuscated?tick:0,obfuscated?salt:0);TextLayout result=planSamples.get(key);if(result!=null)return result;
        TextLayout prepared=obfuscated?font.obfuscate(logical,tick,salt):logical;result=font.sample(prepared,sampling);
        if(logical.logicalText.length()<=TEXT_LIMIT){planSamples.put(key,result);while(planSamples.size()+samples.size()>ENTRY_LIMIT){if(samples.isEmpty())planSamples.remove(planSamples.keySet().iterator().next());else samples.remove(samples.keySet().iterator().next());}}
        return result;
    }
    public int terminalColor(String raw,Settings settings,int baseColor){service();return parse(raw,settings,baseColor).trailingStyle.color;}
    private static final class IdentitySampleKey {
        final TextLayout logical;final float density;final long revision,tick,salt;
        IdentitySampleKey(TextLayout logical,RasterSampling sample,long tick,long salt){this.logical=logical;density=sample.pixelsPerLayoutUnit;revision=sample.revision;this.tick=tick;this.salt=salt;}
        @Override public boolean equals(Object o){if(!(o instanceof IdentitySampleKey))return false;IdentitySampleKey k=(IdentitySampleKey)o;return logical==k.logical&&density==k.density&&revision==k.revision&&tick==k.tick&&salt==k.salt;}
        @Override public int hashCode(){return Objects.hash(System.identityHashCode(logical),density,revision,tick,salt);}
    }
    private static int canonicalColor(Settings settings){int rgb=0;while(true){boolean found=false;for(int i=0;i<16;i++)found|=(settings.paletteColor(i)&0xffffff)==rgb;if(!found)return 0xff000000|rgb;rgb++;}}
    public int width(String raw,Settings settings){return raw==null?0:(int)layout(raw,settings,-1,Integer.MAX_VALUE,TextPolicy.SINGLE_LINE).advanceWidth;}
    /** Java char接口只表达一个UTF16单元；半代理项沿正常无效输入替代，不能伪称完整emoji。 */
    public int charWidth(char c,Settings settings){return c==167?-1:width(String.valueOf(c),settings);}
    /** sourceRight去掉簇前token，续行从完整活动状态恢复；prefix保原串0起点及内部token。 */
    private String slice(String raw,AttributedText parsed,int a,int b,Settings settings){
        if(a==b)return parsed.input.text.isEmpty()?raw:"";int end=b==parsed.input.text.length()?raw.length():parsed.input.sourceLeft(b);
        if(a==0)return raw.substring(0,end);
        return StyleSerializer.prefix(parsed.styleAt(a),settings)+raw.substring(parsed.input.sourceRight(a),end);
    }
    private int boundary(String raw,int maximum,boolean reverse,Settings settings){
        if(maximum<=0)return reverse?parse(raw,settings,-1).input.text.length():0;
        AttributedText request=parse(raw,settings,-1);TextLayout layout=layout(raw,settings,-1,Integer.MAX_VALUE,TextPolicy.SINGLE_LINE);
        TreeSet<Integer> safe=new TreeSet<Integer>();safe.add(0);safe.add(request.input.text.length());Map<Integer,Float> advances=new java.util.TreeMap<Integer,Float>();
        for(TextLayout.Line line:layout.lines())for(TextLayout.Cluster c:line.clusters()){safe.add(c.logicalStart);safe.add(c.logicalEnd);advances.put(c.logicalStart,c.advance);}
        List<Integer> points=new ArrayList<Integer>(safe);double used=0;int candidate=reverse?request.input.text.length():0;
        if(reverse){for(int i=points.size()-2;i>=0;i--){int point=points.get(i);used+=advances.containsKey(point)?advances.get(point):0;if(used>maximum)break;candidate=point;}}
        else for(int i=1;i<points.size();i++){int previous=points.get(i-1);used+=advances.containsKey(previous)?advances.get(previous):0;if(used>maximum)break;candidate=points.get(i);}
        // 行端HB连接形需以同服务最终片段验证；只局部缩短/扩展相邻安全点。
        int index=Collections.binarySearch(points,candidate);
        while(candidate!=(reverse?request.input.text.length():0)&&width(slice(raw,request,reverse?candidate:0,reverse?request.input.text.length():candidate,settings),settings)>maximum){index+=reverse?1:-1;candidate=points.get(index);}
        while(reverse?index>0:index<points.size()-1){int next=points.get(index+(reverse?-1:1));if(width(slice(raw,request,reverse?next:0,reverse?request.input.text.length():next,settings),settings)>maximum)break;index+=reverse?-1:1;candidate=next;}
        return candidate;
    }
    public String trim(String raw,int maximum,boolean reverse,Settings settings){Objects.requireNonNull(raw,"raw");service();AttributedText request=parse(raw,settings,-1);int cut=boundary(raw,maximum,reverse,settings);if(reverse)return cut==request.input.text.length()?"":raw.substring(request.input.sourceLeft(cut));return slice(raw,request,0,cut,settings);}
    public int sizeToWidth(String raw,int maximum,Settings settings){service();int cut=boundary(raw,maximum,false,settings);AttributedText request=parse(raw,settings,-1);return cut==request.input.text.length()?raw.length():request.input.sourceLeft(cut);}
    public List<String> list(String raw,int maximum,Settings settings){
        service();AttributedText request=parse(raw,settings,-1);TextLayout layout=layout(raw,settings,-1,Math.max(0,maximum),TextPolicy.NORMAL);List<String> result=new ArrayList<String>();
        for(TextLayout.Line line:layout.lines()){int end=line.logicalEnd;if(end<request.input.text.length())while(end>line.logicalStart&&request.input.text.charAt(end-1)==' ')end--;result.add(slice(raw,request,line.logicalStart,end,settings));}
        while(result.size()>1&&result.get(result.size()-1).isEmpty())result.remove(result.size()-1);return Collections.unmodifiableList(result);
    }
    public String wrap(String raw,int maximum,Settings settings){return String.join("\n",list(raw,maximum,settings));}
    public int splitHeight(String raw,int maximum,Settings settings){return (int)(list(raw,maximum,settings).size()*settings.lineHeight);}
    /** 显式legacy字符串视觉API；layout始终读取原逻辑串，不能把此结果再次送去重排。 */
    public String bidiString(String raw,Settings settings){if(!settings.bidi)return raw;service();AttributedText request=parse(raw,settings,-1);TextLayout fact=layout(raw,settings,-1,Integer.MAX_VALUE,TextPolicy.SINGLE_LINE);StringBuilder result=new StringBuilder();boolean formatted=false;for(TextInput.SourceSegment segment:request.input.sourceSegments())formatted|=segment.syntax;
        boolean first=true;for(TextLayout.Line line:fact.lines()){if(!first)result.append('\n');first=false;Bidi paragraph=new Bidi();paragraph.setPara(request.input.text.substring(line.logicalStart,line.logicalEnd),Bidi.LEVEL_DEFAULT_LTR,null);TextStyle active=null;
            for(TextLayout.Cluster cluster:line.clusters()){String text=request.input.text.substring(cluster.logicalStart,cluster.logicalEnd);if(formatted&&!cluster.style.equals(active)){result.append(StyleSerializer.prefix(request.styleAt(cluster.logicalStart),settings));active=cluster.style;}boolean rtl=(paragraph.getLevelAt(cluster.logicalStart-line.logicalStart)&1)!=0;for(int i=0;i<text.length();){int cp=text.codePointAt(i);i+=Character.charCount(cp);if(cp==0x061c||cp==0x200e||cp==0x200f||cp>=0x202a&&cp<=0x202e||cp>=0x2066&&cp<=0x2069)continue;result.appendCodePoint(rtl?UCharacter.getMirror(cp):cp);}}}return result.toString();}
    private static final class ParseKey {
        final String raw;final Settings settings;final int color;
        ParseKey(String raw,Settings settings,int color){this.raw=raw;this.settings=settings;this.color=color;}
        @Override public boolean equals(Object o){if(!(o instanceof ParseKey))return false;ParseKey k=(ParseKey)o;return raw.equals(k.raw)&&settings.equals(k.settings)&&color==k.color;}
        @Override public int hashCode(){return Objects.hash(raw,settings,color);}
    }
    /** palette只改变paint，normal/shadow共用一次实际HB定位；SampleKey保留完整Settings颜色身份。 */
    private static final class LayoutKey {
        final String raw;final Settings settings;final float width;final TextPolicy policy;
        LayoutKey(String raw,Settings settings,float width,TextPolicy policy){this.raw=raw;this.settings=settings;this.width=width;this.policy=policy;}
        @Override public boolean equals(Object o){if(!(o instanceof LayoutKey))return false;LayoutKey k=(LayoutKey)o;return raw.equals(k.raw)&&settings.logicalEm==k.settings.logicalEm&&settings.lineHeight==k.settings.lineHeight&&settings.baseline==k.settings.baseline&&settings.bottomAligned==k.settings.bottomAligned&&settings.bidi==k.settings.bidi&&settings.unicode==k.settings.unicode&&width==k.width&&policy.equals(k.policy);}
        @Override public int hashCode(){return Objects.hash(raw,settings.logicalEm,settings.lineHeight,settings.baseline,settings.bottomAligned,settings.bidi,settings.unicode,width,policy);}
    }
    private static final class SampleKey {
        final LayoutKey layout;final int color;final float density;final long revision,tick,salt;
        SampleKey(LayoutKey layout,int color,RasterSampling sample,long tick,long salt){this.layout=layout;this.color=color;density=sample.pixelsPerLayoutUnit;revision=sample.revision;this.tick=tick;this.salt=salt;}
        @Override public boolean equals(Object o){if(!(o instanceof SampleKey))return false;SampleKey k=(SampleKey)o;return layout.equals(k.layout)&&layout.settings.equals(k.layout.settings)&&color==k.color&&density==k.density&&revision==k.revision&&tick==k.tick&&salt==k.salt;}
        @Override public int hashCode(){return Objects.hash(layout,layout.settings,color,density,revision,tick,salt);}
    }
}
