package club.heiqi.uilib.ui.scene.integration;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.host.client.font.vanilla.VanillaTextService;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.RasterSampling;
import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.StyleSpan;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapedGlyph;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeDirection;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.FontFamilyResolver;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import club.heiqi.uilib.ui.scene.text.font.glyph.AsyncGlyphRasterizer;
import club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Arrays;
/** 真实系统字体与bundled variable经过正式匹配/FT/HB/光栅，防止粗体只换图片不换排版。 */
public final class FontRealStyleScenario extends TestApplication {
 public static List<TextLayout.Glyph> glyphs(TextLayout layout){List<TextLayout.Glyph> out=new ArrayList<TextLayout.Glyph>();for(TextLayout.Line line:layout.lines())out.addAll(line.glyphs());return out;}
 public static FontSource family(Path path,long revision)throws Exception{return new FontFamilyResolver(Collections.singletonList(path),Collections.<Path>emptyList(),revision,System.out::println).family(path,"configured");}
 private void actual(FontTextService fonts,FontSession session,String raw,TextStyle style,FontStyle expected)throws Exception {
  TextLayout layout=fonts.layout(raw,style,1000,TextPolicy.SINGLE_LINE);float advance=0;for(TextLayout.Glyph glyph:glyphs(layout)){
   require(glyph.source.actualStyle().equals(expected),"请求没有选择预期实际字体: "+glyph.source.metadata()+" "+glyph.source.actualStyle());require(glyph.rasterStyle.normal(),"真实face被重复合成");
   try(FontFace face=glyph.source.openFace(session,style.logicalEm)){require(face.rasterize(glyph.glyphId).width>0,"实际光栅为空");}advance+=glyph.xAdvance;
  }require(advance==layout.advanceWidth,"排版advance与实际glyph不一致");
  try(FontFace face=glyphs(layout).get(0).source.openFace(session,style.logicalEm)){float direct=0;for(ShapedGlyph glyph:face.shape(raw,ShapeDirection.LTR,"und"))direct+=glyph.xAdvance;require(direct==layout.advanceWidth,"实际样式HB整形advance与layout不一致");}
  System.out.println("ACTUAL raw="+raw+" request="+style.fontStyle+" advance="+layout.advanceWidth+" glyph="+glyphs(layout).get(0).source+" metadata="+glyphs(layout).get(0).source.metadata()+" actual="+glyphs(layout).get(0).source.actualStyle()+" diagnostics="+layout.diagnostics().stream().map(d->d.kind+": "+d.message).collect(java.util.stream.Collectors.joining("; ")));
 }
 @Override protected void runTests()throws Exception {
  try(FontSession nativeSession=IsolatedFontEngine.shared().openSession()){
   String windir=System.getenv("WINDIR");Path windows=Paths.get(windir==null?"/nonexistent-windows-fonts":windir,"Fonts");Path msyh=windows.resolve("msyh.ttc"),arial=windows.resolve("arial.ttf");
   if(Files.isRegularFile(msyh))scenario("微软雅黑真实字族及原版样式",()->{
    FontSource family=family(msyh,1);try(FontTextService fonts=new FontTextService(Collections.singletonList(family),IsolatedFontEngine.shared(),32,128,8192)){
     TextStyle regular=new TextStyle("default",9,-1),bold=regular.withFontStyle(FontStyle.minecraft(true,false));
     actual(fonts,nativeSession,"世界AiW",regular,FontStyle.NORMAL);actual(fonts,nativeSession,"世界AiW",bold,bold.fontStyle);
     for(boolean b:new boolean[]{false,true}){TextStyle italic=regular.withFontStyle(FontStyle.minecraft(b,true));TextLayout fallback=fonts.layout("世界",italic,1000,TextPolicy.SINGLE_LINE);require(fallback.diagnostics().stream().anyMatch(d->d.kind==TextLayout.Diagnostic.Kind.STYLE_FALLBACK),"缺斜体没有明确降级");actual(fonts,nativeSession,"世界",italic,FontStyle.minecraft(b,false));}
     VanillaTextService vanilla=new VanillaTextService(fonts);VanillaTextService.Settings settings=new VanillaTextService.Settings(9,9,9,false,false,new int[32]);
     TextLayout mixed=vanilla.layout("世§l世§r世",settings,-1,1000,TextPolicy.SINGLE_LINE);List<TextLayout.Glyph> gs=glyphs(mixed);require(gs.size()==3&&gs.get(0).source.actualStyle().weight==400&&gs.get(1).source.actualStyle().weight==700&&gs.get(2).source.actualStyle().weight==400,"同字不同样式发生匹配污染");
     require(gs.get(1).source.origin.toLowerCase(Locale.ROOT).endsWith("msyhbd.ttc")&&gs.get(1).source.faceIndex==0,"未选真实微软雅黑粗体face0");
     for(int limit=0;limit<vanilla.width("§l世界AiW",settings);limit++)for(boolean reverse:new boolean[]{false,true})require(vanilla.width(vanilla.trim("§l世界AiW",limit,reverse,settings),settings)<=limit,"真实粗体trim越界");
     TextLayout sample=vanilla.sample("§k§l世界AiW",settings,-1,1000,TextPolicy.SINGLE_LINE,RasterSampling.LOGICAL,3,0),shadow=vanilla.sample("§k§l世界AiW",settings.shadow(),-1,1000,TextPolicy.SINGLE_LINE,RasterSampling.LOGICAL,3,0);List<TextLayout.Glyph> sg=glyphs(sample),sh=glyphs(shadow);for(int k=0;k<sg.size();k++){TextLayout.Glyph g=sg.get(k),h=sh.get(k);require(g.source.actualStyle().weight==700,"混淆丢失实际粗体");require(g.source==h.source&&g.glyphId==h.glyphId&&g.x==h.x&&g.xAdvance==h.xAdvance,"shadow未复用真实几何");}
     FontSource old=gs.get(1).source;fonts.reload(Collections.singletonList(family(msyh,2)));require(glyphs(fonts.layout("世",bold,1000)).get(0).source.revision==2&&old.revision==1,"换代污染旧实例");try(FontFace f=old.openFace(nativeSession,9)){require(f.rasterize(gs.get(1).glyphId).width>0,"旧plan无法重放");}
    }
    FontSource explicit=FontSource.file("face",1,msyh,0,false,Collections.<String>emptySet());require(explicit.resolve(FontStyle.minecraft(true,false),true).get(0)==explicit,"显式face隐式扩字族");
   });else System.out.println("SKIP 系统微软雅黑验收：本机没有msyh.ttc，bundled可变字体验收继续");
   if(Files.isRegularFile(arial))scenario("Arial四样式及复杂连接范围",()->{
    FontSource family=family(arial,1);try(FontTextService fonts=new FontTextService(Collections.singletonList(family),IsolatedFontEngine.shared(),32,128,8192)){
     for(boolean b:new boolean[]{false,true})for(boolean i:new boolean[]{false,true}){TextStyle style=new TextStyle("default",24,-1).withFontStyle(FontStyle.minecraft(b,i));actual(fonts,nativeSession,"AVoffice",style,style.fontStyle);}
    }
    FontSource first=FontSource.file("first-regular",1,arial,0,false,Collections.<String>emptySet()),later=FontSource.file("later-bold",1,windows.resolve("arialbd.ttf"),0,false,Collections.<String>emptySet());
    TextStyle request=new TextStyle("default",24,-1).withFontStyle(FontStyle.minecraft(true,false));
    try(FontTextService ordered=new FontTextService(Arrays.asList(first,later),IsolatedFontEngine.shared(),8,8,8192)){
     TextLayout l=ordered.layout("A",request,1000);require(glyphs(l).get(0).source==first&&l.diagnostics().stream().anyMatch(d->d.kind==TextLayout.Diagnostic.Kind.STYLE_FALLBACK),"后面候选精确样式越过前面真实closest");
     ordered.reload(Arrays.asList(later,first));require(glyphs(ordered.layout("A",request,1000)).get(0).source==later,"候选顺序变化未生效");
    }
   });
   scenario("必要连接范围样式边界与完整HB上下文",()->{
    List<FontSource> sources=FontTextService.defaultSources(getClass().getClassLoader(),1);
    try(FontTextService fonts=new FontTextService(sources,IsolatedFontEngine.shared(),32,128,8192)){
     String raw="بـب";TextStyle regular=new TextStyle("default",24,-1),bold=regular.withFontStyle(FontStyle.minecraft(true,false));
     TextLayout mixed=fonts.layout(new AttributedText(TextInput.string(raw),raw,regular,Arrays.asList(new StyleSpan(0,1,regular),new StyleSpan(1,3,bold))),1000,TextPolicy.SINGLE_LINE);
     require(mixed.diagnostics().stream().noneMatch(d->d.kind==TextLayout.Diagnostic.Kind.STYLE_COALESCED),"跨字素样式被连接范围吞掉");
     List<TextLayout.Glyph> gs=glyphs(mixed);require(gs.stream().anyMatch(g->g.resolvedStyle.fontStyle.equals(bold.fontStyle))&&gs.stream().anyMatch(g->g.resolvedStyle.fontStyle.equals(regular.fontStyle)),"连接范围丢失请求样式");
     float advance=0;for(TextLayout.Glyph g:gs)advance+=g.xAdvance;
     float expected=0;for(int[] range:new int[][]{{0,1},{1,3}}){FontSource actual=gs.stream().filter(g->g.logicalStart==range[0]).findFirst().get().source;try(FontFace face=actual.openFace(nativeSession,24)){
      for(ShapedGlyph g:face.shape(raw,range[0],range[1],ShapeDirection.RTL,"und","Arab",range[0]==0,range[1]==raw.length())){expected+=g.xAdvance;require(gs.stream().anyMatch(m->m.glyphId==g.glyphId&&m.logicalStart==g.cluster),"样式边界没有保留完整上下文连接形");}
     }}require(expected==advance&&advance==mixed.advanceWidth,"样式范围实际HB度量不一致");System.out.println("ARABIC_STYLE_CONTEXT advance="+advance+" actualHB="+expected+" requests=400/700");
    }
   });
   scenario("可变NotoEmoji轴身份整形与异步重开",()->{
    FontSource source=FontSource.fromResource("variable",1,"assets/qz_uilib/fonts/noto/NotoEmoji.ttf",false);
    require(source.metadata().axes.containsKey("wght"),"实际bundled缺少wght");
    FontSource normal=source.resolve(FontStyle.NORMAL,true).get(0),bold=source.resolve(FontStyle.minecraft(true,false),true).get(0);
    require(normal!=bold&&normal==source.resolve(FontStyle.NORMAL,true).get(0)&&bold==source.resolve(FontStyle.minecraft(true,false),true).get(0),"同轴实例不canonical或轴身份混合");
    require(bold.axisValues().get("wght")==700&&normal.axisValues().get("wght")==400,"轴值未设置");
    try(FontTextService fonts=new FontTextService(Collections.singletonList(source),IsolatedFontEngine.shared(),32,128,8192)){
     String raw="☺︎";TextStyle regular=new TextStyle("default",32,-1);TextLayout n=fonts.layout(raw,regular,1000),b=fonts.layout(raw,regular.withFontStyle(FontStyle.minecraft(true,false)),1000);
     TextLayout.Glyph ng=glyphs(n).get(0),bg=glyphs(b).get(0);require(ng.source==normal&&bg.source==bold,"匹配未保持轴实例");
     AsyncGlyphRasterizer worker=new AsyncGlyphRasterizer(new AsyncGlyphRasterizer.Budget(8,8,8,1024*1024,4,32*1024*1024,8,96),IsolatedFontEngine.shared());
     onClose(()->{worker.close();require(worker.awaitClose(10000),"variable worker关闭失败");});
     GlyphKey nk=GlyphKey.of(ng),bk=GlyphKey.of(bg);require(!nk.equals(bk),"不同轴实例GlyphKey合并");worker.request(nk);worker.request(bk);
     long deadline=System.nanoTime()+10000000000L;while(worker.peek(nk)==null||worker.peek(bk)==null){require(System.nanoTime()<deadline&&worker.failures().isEmpty()&&worker.workerFailure()==null,"真实轴异步光栅失败");Thread.yield();}
     require(!Arrays.equals(worker.peek(nk).pixels(),worker.peek(bk).pixels())&&worker.stats().faces==2,"异步worker混合轴实例");
     System.out.println("VARIABLE ASYNC faces="+worker.stats().faces+" rasterized="+worker.stats().rasterized+" normalKey="+nk+" boldKey="+bk);
     try(FontFace nf=ng.source.openFace(nativeSession,32);FontFace bf=bg.source.openFace(nativeSession,32)){
      GlyphBitmap nb=nf.rasterize(ng.glyphId),bb=bf.rasterize(bg.glyphId);require(!Arrays.equals(nb.pixels(),bb.pixels()),"真实wght轴未改变光栅");System.out.println("VARIABLE normal="+normal+" advance="+n.advanceWidth+" bold="+bold+" advance="+b.advanceWidth+" bitmap="+nb.width+"x"+nb.height+"/"+bb.width+"x"+bb.height);
     }
    }
   });
  }
 }
 public static void main(String[] args)throws Exception {new FontRealStyleScenario().run(args);}
}
