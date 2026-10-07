package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.nio.charset.StandardCharsets;
import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.RasterSampling;
import club.heiqi.uilib.ui.scene.text.StyleSpan;
import club.heiqi.uilib.ui.scene.text.TextDecoration;
import club.heiqi.uilib.ui.scene.text.TextEffects;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.format.MinecraftFormat;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;
/** 真实属性文本集成，覆盖格式来源、完整簇、逻辑度量/实际采样事实，不运行MC。 */
public final class AttributedTextScenario extends TestApplication {

 static List<TextLayout.Glyph> glyphs(TextLayout layout){List<TextLayout.Glyph> r=new ArrayList<TextLayout.Glyph>();for(TextLayout.Line l:layout.lines())r.addAll(l.glyphs());return r;}
 static final MinecraftFormat.Context CONTEXT=new MinecraftFormat.Context(){
  public int paletteColor(int i){return i==15?0xffffff:i==12?0xff3344:0x335577;}
  public TextEffects effects(boolean b,boolean i,boolean u,boolean s,boolean k){return new TextEffects(b,i,u,s,k,1,.25f);}
  public TextDecoration decoration(){return new TextDecoration(2,1,-1,-3,1);}
 };
 static AttributedText parse(String text,TextStyle style){return MinecraftFormat.parse(text,style,CONTEXT);}
 static void source(FontTextService service,TextStyle style){
  AttributedText a=parse("A§cB",style);require(a.input.text.equals("AB")&&a.input.sourceLeft(1)==1&&a.input.sourceRight(1)==3,"dual source boundary");
  TextLayout layout=service.layout(a,1000,TextPolicy.SINGLE_LINE);List<TextLayout.Glyph> gs=glyphs(layout);
  require(gs.get(0).sourceStart==0&&gs.get(0).sourceEnd==1&&gs.get(1).sourceStart==3&&gs.get(1).sourceEnd==4,"glyph吞入邻接格式token");
  require(gs.get(1).resolvedStyle.color==0x7fff3344,"palette没有保留basealpha");
  a=parse("§lA§r",style);require(a.input.sourceLeft(0)==0&&a.input.sourceRight(0)==2&&a.input.sourceLeft(1)==3&&a.input.sourceRight(1)==5,"leading trailing syntax");
  require(!a.trailingStyle.effects.bold&&a.input.sourceSegments().size()==3,"trailing reset状态或token片段丢失");
  AttributedText bytes=MinecraftFormat.parse(TextInput.utf8("中§c😀".getBytes(StandardCharsets.UTF_8)),null,style,CONTEXT);
  require(bytes.input.sourceUnit==TextInput.SourceUnit.BYTE&&bytes.input.sourceStart(1)==6&&bytes.input.sourceEnd(2)==10,"组合UTF8来源映射");
  require(parse("§zA",style).input.text.equals("A")&&parse("§zA",style).styleAt(0).color==0x7fffffff,"未知token没有原版白色reset");
  require(parse("A§",style).input.text.equals("A§"),"孤立§必须literal");
  require(service.layout("§lA",style,1000,TextPolicy.SINGLE_LINE).logicalText.equals("§lA"),"plain猜格式");
  require(service.layout(parse("§l§r",style),1000,TextPolicy.SINGLE_LINE).logicalText.isEmpty(),"format-only empty丢状态");
  TextInput original=TextInput.string("\uD800§cA");AttributedText invalid=MinecraftFormat.parse(original,"\uD800§cA",style,CONTEXT);
  require(!service.layout(invalid,1000,TextPolicy.SINGLE_LINE).diagnostics().isEmpty(),"mapped invalid输入缺诊断");
  System.out.println("PASS attributed source dual-boundaries/token segments/UTF8/invalid/plain/unknown/trailing");
 }
 static void cluster(FontTextService service,TextStyle style){
  for(String[] pair:new String[][]{{"ب§cـب","بـب"},{"क§l्षि","क्षि"},{"👩§c‍💻X","👩‍💻X"}}){
   TextLayout attributed=service.layout(parse(pair[0],style),1000,TextPolicy.SINGLE_LINE),plain=service.layout(pair[1],style,1000,TextPolicy.SINGLE_LINE);
   List<TextLayout.Glyph> a=glyphs(attributed),b=glyphs(plain);require(a.size()==b.size()&&attributed.advanceWidth==plain.advanceWidth,"样式span拆必要shape范围");
   for(int i=0;i<a.size();i++)require(a.get(i).glyphId==b.get(i).glyphId&&a.get(i).x==b.get(i).x&&a.get(i).logicalStart==b.get(i).logicalStart,"完整簇shape事实不一致");
   if(pair[1].equals("क्षि")){require(attributed.lines().get(0).clusters().size()==1&&!a.get(0).resolvedStyle.effects.bold&&!attributed.diagnostics().isEmpty(),"Indic簇内部bold未leading裁决诊断");}
   if(pair[1].startsWith("👩"))require(a.get(0).resolvedStyle.color==style.color&&a.get(1).resolvedStyle.color!=style.color,"emoji内style裁决污染后续X");
  }
  TextLayout regular=service.layout("क्षि",style,1000,TextPolicy.SINGLE_LINE),bold=service.layout(parse("§lक्षि",style),1000,TextPolicy.SINGLE_LINE);
  require(bold.advanceWidth==regular.advanceWidth+1&&glyphs(bold).size()==2,"多glyph Indic粗体按glyph重复加advance");
  TextLayout ar=service.layout("بـب",style,1000,TextPolicy.SINGLE_LINE),arb=service.layout(parse("§lبـب",style),1000,TextPolicy.SINGLE_LINE);
  require(arb.advanceWidth==ar.advanceWidth+3,"Arabic完整cluster粗体增量错误");
  for(int i=0;i<glyphs(ar).size();i++)require(glyphs(ar).get(i).x==glyphs(arb).get(i).x,"Arabic粗体tracking破坏连接");
  TextLayout reset=service.layout(parse("§lA§cB",style),1000,TextPolicy.SINGLE_LINE);
  require(glyphs(reset).get(0).resolvedStyle.effects.bold&&!glyphs(reset).get(1).resolvedStyle.effects.bold,"颜色未清bold");
  TextInput text=TextInput.string("AB");TextStyle big=new TextStyle("default",18.5f,style.color,null,TextEffects.NORMAL,TextDecoration.NONE);
  AttributedText different=new AttributedText(text,"AB",style,Arrays.asList(new StyleSpan(0,1,style),new StyleSpan(1,2,big)));
  TextLayout mixed=service.layout(different,1000,TextPolicy.SINGLE_LINE);require(glyphs(mixed).get(1).logicalEm==18.5f,"普通span浮点字号未应用");
  AttributedText arabicFonts=new AttributedText(TextInput.string("بـب"),"بـب",style,Arrays.asList(new StyleSpan(0,1,style),new StyleSpan(1,3,big)));
  TextLayout unified=service.layout(arabicFonts,1000,TextPolicy.SINGLE_LINE);
  require(glyphs(unified).get(0).logicalEm==style.logicalEm&&!unified.diagnostics().isEmpty(),"连接范围字体/字号变化缺leading裁决诊断");
  TextPolicy wrap=new TextPolicy(TextPolicy.Direction.AUTO,TextPolicy.Wrap.EMERGENCY,"und",0,0);
  TextLayout wrapped=service.layout(parse("§labc §cאבג §nبـب 👩‍💻",style),18,wrap);StringBuilder joined=new StringBuilder();
  for(TextLayout.Line line:wrapped.lines()){joined.append(line.text);for(TextLayout.Glyph g:line.glyphs())require(g.resolvedStyle!=null&&g.clusterIndex>=0,"属性Bidi/wrap缺实际簇样式");}
  require(joined.toString().equals("abc אבג بـب 👩‍💻"),"属性Bidi/wrap丢逻辑文字或拆emoji");
  System.out.println("PASS attributed Arabic/Indic/ZWJ whole shape logical-leading bold-cluster/no-tracking/floatem/bidi-wrap");
 }
 static void sampling(FontTextService service,TextStyle style)throws Exception{
  TextLayout logical=service.layout(parse("§l§o§n§mA بـب क्षि 👩‍💻",style),1000,TextPolicy.SINGLE_LINE);
  require(!logical.lines().get(0).decorations().isEmpty(),"装饰没有最终geometry");
  TextLayout recolored=logical.withStyle(logical.style.withColor(0x7f112233));
  for(TextLayout.Glyph g:glyphs(recolored))require(g.resolvedStyle.color==0x7f112233,"glyph withStyle未重绑");
  for(TextLayout.Decoration d:recolored.lines().get(0).decorations())require(d.color==0x7f112233,"decoration withStyle未重绑");
  for(float density:new float[]{1,1.5f,2,3,4}){
   TextLayout sampled=service.sample(logical,new RasterSampling(density,1));require(sampled.advanceWidth==logical.advanceWidth&&sampled.advanceHeight==logical.advanceHeight,"采样改变逻辑度量");
   List<TextLayout.Glyph> a=glyphs(logical),b=glyphs(sampled);require(a.size()==b.size(),"采样重排glyph");
   try(FontSession scope=new LwjglFontEngine().openSession()){
    for(int i=0;i<b.size();i++){TextLayout.Glyph g=b.get(i),old=a.get(i);
     require(g.glyphId==old.glyphId&&g.x==old.x&&g.y==old.y&&g.xAdvance==old.xAdvance&&g.size==style.logicalEm*density&&Math.abs(g.rasterToLayout*density-1)<.00001f,"采样glyph身份/位置/比例改变");
     require(g.rasterStyle.boldOffset==density&&g.resolvedStyle!=null,"变体offset不是实际raster单位");
     try(FontFace face=g.source.openFace(scope,g.size)){GlyphBitmap bitmap=face.rasterize(g.glyphId,g.rasterStyle);byte[] pixels=bitmap.pixels();
      for(int y=0;y<bitmap.height;y++)for(int x=0;x<bitmap.width;x++)if(pixels[(y*bitmap.width+x)*4+3]!=0){float px=(bitmap.bearingX+(x+.5f)*bitmap.pixelScale)*g.rasterToLayout,py=(-bitmap.bearingY+(y+.5f)*bitmap.pixelScale)*g.rasterToLayout;
       require(!g.inkBounds.known||px>=g.inkBounds.left-.001f&&px<=g.inkBounds.right+.001f&&py>=g.inkBounds.top-.001f&&py<=g.inkBounds.bottom+.001f,"采样变体实际像素超logicalink");}
     }
    }
   }
  }
  List<TextLayout.Glyph> before=glyphs(logical);service.reload(service.defaultCandidates());TextLayout after=service.sample(logical,new RasterSampling(2,2));require(glyphs(after).get(0).source==before.get(0).source,"sample旧计划绑定新source");
  System.out.println("PASS attributed decorations/recolor/sampling1,1.5,2,3,4 actual variant pixels/bounds/oldplan");
 }
 static void reviewRegressions(FontTextService service,List<FontSource> fonts){
  TextStyle base=new TextStyle("default",24,-1);TextLayout original=service.layout("A",base,1000,TextPolicy.SINGLE_LINE);
  for(TextStyle selector:Arrays.asList(new TextStyle("minecraft",24,0xff112233),new TextStyle("default",24,0xff445566,fonts))){
   TextLayout rebound=service.layout("A",selector,1000,TextPolicy.SINGLE_LINE);TextLayout.Glyph a=glyphs(original).get(0),b=glyphs(rebound).get(0);
   require(rebound.style==selector&&b.resolvedStyle==selector&&a.source==b.source&&a.glyphId==b.glyphId&&a.x==b.x&&a.inkBounds==b.inkBounds,"等价resolved候选缓存未安全重绑selector");
   boolean denied=false;try{original.withStyle(selector);}catch(IllegalArgumentException expected){denied=true;}
   require(denied,"公开withStyle绕过真实selector约束");
  }
  for(TextInput input:Arrays.asList(TextInput.string("\b§cA"),TextInput.utf8("\b§cA".getBytes(StandardCharsets.UTF_8)),TextInput.string("\uD800§cA"),TextInput.utf8(new byte[]{(byte)0xff,(byte)0xc2,(byte)0xa7,'c','A'}))){
   TextLayout layout=service.layout(MinecraftFormat.parse(input,null,base,CONTEXT),1000,TextPolicy.SINGLE_LINE);boolean seen=false;
   for(TextLayout.Diagnostic d:layout.diagnostics())if(d.kind==TextLayout.Diagnostic.Kind.CONTROL_MARKER||d.kind==TextLayout.Diagnostic.Kind.INVALID_INPUT){require(d.sourceStart==0&&d.sourceEnd==1,"控制/无效输入诊断吞入后继syntax token: "+d.sourceStart+".."+d.sourceEnd);seen=true;}
   require(seen,"诊断缺失");
  }
  TextStyle big=new TextStyle("default",48,-1),fractional=new TextStyle("default",18.5f,-1,null,TextEffects.NORMAL,TextDecoration.NONE);
  for(TextStyle active:Arrays.asList(big,fractional)){
   AttributedText input=new AttributedText(TextInput.string("A\n\n"),"A\n\n",base,Arrays.asList(new StyleSpan(0,1,base),new StyleSpan(1,3,active)));
   TextLayout layout=service.layout(input,1000,TextPolicy.SINGLE_LINE),reference=service.layout("",active,1000,TextPolicy.SINGLE_LINE);
   require(layout.style==base&&layout.lines().size()==3,"空行或base样式被覆盖");
   for(int i=1;i<3;i++){TextLayout.Line line=layout.lines().get(i),ref=reference.lines().get(0);require(line.lineHeight==ref.lineHeight&&line.ascent==ref.ascent&&line.descent==ref.descent,"空段忽略活动/末尾浮点样式");}
  }
  System.out.println("PASS review resolved-selector cache rebind/public guard/control-invalid source/empty-active float metrics");
 }
    public static void main(String[] args) throws Exception { new AttributedTextScenario().run(args); }

    private List<FontSource> fonts;
    private TextStyle style;
    private FontTextService service;
    @Override protected void setup() throws Exception {
        fonts = FontTextService.defaultSources(AttributedTextScenario.class.getClassLoader(), 1);
        style = new TextStyle("default", 9f, 0x7fabcdef, null, TextEffects.NORMAL, TextDecoration.NONE);
        service = own(new FontTextService(fonts, new LwjglFontEngine(), 4, 8, 4096));
    }
    @Override protected void runTests() throws Exception {
        scenario("格式token与双来源边界", () -> source(service, style));
        scenario("完整簇与属性断行", () -> cluster(service, style));
        scenario("装饰及实际像素采样", () -> sampling(service, style));
        scenario("selector缓存及诊断空行回归", () -> reviewRegressions(service, fonts));
    }
}
