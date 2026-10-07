package club.heiqi.uilib.ui.scene.integration;
import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import club.heiqi.uilib.host.client.font.vanilla.VanillaTextService;
import club.heiqi.uilib.host.client.font.vanilla.VanillaTextService.Settings;
import club.heiqi.uilib.ui.scene.text.RasterSampling;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;

/** 原版适配CPU真实集成，替代一次标准文字API实机测量；不宣称GL/MC性能验收。 */
public final class VanillaTextScenario {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static List<TextLayout.Glyph> glyphs(TextLayout l){List<TextLayout.Glyph> result=new ArrayList<TextLayout.Glyph>();for(TextLayout.Line line:l.lines())result.addAll(line.glyphs());return result;}
    private static int[] palette(){int[] result=new int[32];for(int i=0;i<32;i++){int boost=(i>>3&1)*85,r=(i>>2&1)*170+boost,g=(i>>1&1)*170+boost,b=(i&1)*170+boost;if(i==6)r+=85;if(i>=16){r/=4;g/=4;b/=4;}result[i]=r<<16|g<<8|b;}return result;}
    private static void calibration(FontTextService font){for(int em:new int[]{8,9,10,12}){TextStyle style=new TextStyle("minecraft",em,-1);for(String raw:new String[]{"AiW .中","Hello, world!","بـب क्षि 👩‍💻"}){TextLayout l=font.layout(raw,style,1000,TextPolicy.SINGLE_LINE);StringBuilder detail=new StringBuilder();for(TextLayout.Glyph g:glyphs(l))detail.append(g.xAdvance).append('/').append(g.inkBounds.top).append("..").append(g.inkBounds.bottom).append(' ');System.out.println("CALIBRATION em="+em+" raw="+raw+" advance="+l.advanceWidth+" actual="+detail);}}}
    private static void actualAlpha(FontTextService font)throws Exception{
        try(FontSession nativeService=new LwjglFontEngine().openSession()){
            for(int em:new int[]{8,12})for(TextLayout.Glyph glyph:glyphs(font.layout("AiW中",new TextStyle("minecraft",em,-1),1000,TextPolicy.SINGLE_LINE))){
                try(FontFace face=glyph.source.openFace(nativeService,em)){GlyphBitmap bitmap=face.rasterize(glyph.glyphId);byte[] pixels=bitmap.pixels();int minY=bitmap.height,maxY=-1;
                    for(int y=0;y<bitmap.height;y++)for(int x=0;x<bitmap.width;x++)if(pixels[(y*bitmap.width+x)*4+3]!=0){minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
                    System.out.println("ALPHA em="+em+" source="+glyph.logicalStart+" advance="+glyph.xAdvance+" actualtop="+(-bitmap.bearingY+minY*bitmap.pixelScale)+" actualbottom="+(-bitmap.bearingY+(maxY+1)*bitmap.pixelScale));
                }
            }
        }
    }
    private static void semantics(VanillaTextService adapter,Settings s){
        require(adapter.charWidth('§',s)==-1&&adapter.width(null,s)==0,"原版格式字符/null宽度");
        int a=adapter.width("A",s);require(adapter.width("§lA§rA",s)==a*2+1,"reset未清bold同度量");
        for(String raw:new String[]{"A§cB","§lABC§r","A👩‍💻B","Aक्षिB","بـبA","אבג A","\uD800§cA","A§zB§","A\tB"}){
            TextLayout l=adapter.layout(raw,s,0x7f112233,1000,TextPolicy.SINGLE_LINE);require(adapter.width(raw,s)==(int)l.advanceWidth,"width/draw度量分叉");
            for(int maximum=0;maximum<adapter.width(raw,s)+2;maximum++)for(boolean reverse:new boolean[]{false,true}){String trimmed=adapter.trim(raw,maximum,reverse,s);require(adapter.width(trimmed,s)<=maximum,"trim超宽 raw="+raw+" max="+maximum+" got="+trimmed);require(!trimmed.endsWith("\u200d"),"trim拆ZWJ");}
        }
        require(adapter.trim("A§cB",a,false,s).equals("A"),"prefix吞邻接token");
        require(adapter.trim("A§cB",a,true,s).equals("§cB"),"suffix未保簇前原始tokenownership");
        require(adapter.trim("§lABCD",a+1,true,s).equals("D"),"reverse trim伪造非原始prefix");
        String raw="§lAB §cCD §nEF\n§oGH";List<String> lines=adapter.list(raw,a*3+1,s);require(lines.size()>1,"wrap无实际行");
        for(String line:lines)require(adapter.width(line,s)<=a*3+1,"wrap未恢复活动style或超宽: "+line);
        require(adapter.splitHeight(raw,a*3+1,s)==(int)(lines.size()*s.lineHeight),"splitheight未用caller FONT_HEIGHT");
        Settings ltr=new Settings(s.logicalEm,s.lineHeight,s.baseline,false,s.unicode,palette());require(adapter.bidiString("אבג A",ltr).equals("אבג A"),"falsebidi未LTR");
        TextLayout colored=adapter.layout("A§fB§nC",s,0x7f123456,1000,TextPolicy.SINGLE_LINE);List<TextLayout.Glyph> g=glyphs(colored);require(g.get(0).resolvedStyle.color==0x7f123456&&g.get(1).resolvedStyle.color==0x7fffffff,"basecolor/explicitwhite合并污染");
        TextLayout normal=adapter.layout("ABC",s,-1,1000,TextPolicy.SINGLE_LINE);for(TextLayout.Line line:normal.lines())require(line.lineHeight==s.lineHeight&&line.baseline==s.baseline,"原版callerbaseline/lineheight");
        System.out.println("PASS vanilla width/char/raw safe trim/token ownership/formatwrap/bidi/fixedbaseline/palette");
    }
    private static void sampling(VanillaTextService adapter,Settings s){
        String raw="§k§lAB §cC §kبـب क्षि 👩‍💻";TextLayout logical=adapter.layout(raw,s,-1,1000,TextPolicy.SINGLE_LINE);TextLayout first=null;boolean changed=false;
        for(long tick=0;tick<8;tick++){
            TextLayout plan=adapter.sample(raw,s,-1,1000,TextPolicy.SINGLE_LINE,new RasterSampling(2,1),tick,7);require(plan.advanceWidth==logical.advanceWidth&&plan.advanceHeight==logical.advanceHeight&&plan.logicalText.equals(logical.logicalText),"混淆改变度量/原文");
            require(adapter.sample(raw,s,-1,1000,TextPolicy.SINGLE_LINE,new RasterSampling(2,1),tick,7)==plan,"hot sample未复用实际计划");
            for(TextLayout.Glyph g:glyphs(plan))require(g.resolvedStyle!=null&&g.sourceStart<g.sourceEnd&&g.logicalStart<g.logicalEnd,"混淆丢真实身份/source");
            if(first==null)first=plan;else if(glyphs(first).get(0).glyphId!=glyphs(plan).get(0).glyphId)changed=true;
        }
        require(changed,"tick混淆未变实际glyph");
        TextLayout frozen=adapter.layout("§kA§rB§c",s,0x7f123456,1000,TextPolicy.SINGLE_LINE);
        require(glyphs(frozen).get(0).resolvedStyle.effects.obfuscated&&frozen.lines().get(0).clusters().get(0).style.effects.obfuscated,"冻结plan丢混淆flag");
        TextLayout replay=adapter.sample(frozen,new RasterSampling(3,12),4,2);require(adapter.sample(frozen,new RasterSampling(3,12),4,2)==replay&&replay.advanceWidth==frozen.advanceWidth,"冻结plan重放采样不稳定");
        require(adapter.terminalColor("A§c",s,0x7f123456)==(0x7f000000|s.paletteColor(12))&&adapter.terminalColor("",s,0x7f123456)==0x7f123456,"terminal尾token/empty错");
        require(adapter.bidiString("אבג",s).equals("גבא")&&adapter.bidiString("\nA",s).equals("\nA"),"视觉字符串未用完整簇或丢首空行");
        String emoji="§k👩‍💻";TextLayout changedEmoji=adapter.sample(emoji,s,-1,1000,TextPolicy.SINGLE_LINE,RasterSampling.LOGICAL,3,1);require(glyphs(changedEmoji).size()==1&&!glyphs(changedEmoji).get(0).color&&glyphs(changedEmoji).get(0).logicalEnd==5,"emoji混淆拆簇或silent忽略");
        TextLayout ar=adapter.sample("§kبـب",s,-1,1000,TextPolicy.SINGLE_LINE,RasterSampling.LOGICAL,3,1);require(glyphs(ar).size()==1&&glyphs(ar).get(0).logicalStart==0&&glyphs(ar).get(0).logicalEnd==3,"Arabic必要连接range混淆半簇");
        TextLayout partial=adapter.sample("§kب§rـب",s,-1,1000,TextPolicy.SINGLE_LINE,RasterSampling.LOGICAL,3,1);require(glyphs(partial).size()==1&&!partial.diagnostics().isEmpty(),"必要range混淆未logicalleading裁决/诊断");
        System.out.println("PASS vanilla true obfuscation same tick identity/tick change/whole emoji/necessary Arabic/source/advance");
    }
    private static void shadowReuse(VanillaTextService adapter,Settings normal){
        adapter.clear();Settings shadow=normal.shadow();String raw="§lA§fB§cC§rD §nבـب";
        TextLayout first=adapter.layout(raw,normal,0x7f123456,1000,TextPolicy.SINGLE_LINE),second=adapter.layout(raw,shadow,0x7f123456,1000,TextPolicy.SINGLE_LINE);
        require(adapter.cachedLayoutCount()==1&&first.advanceWidth==second.advanceWidth,"阴影palette重复建立几何布局");
        List<TextLayout.Glyph> a=glyphs(first),b=glyphs(second);
        for(int i=0;i<a.size();i++){TextLayout.Glyph x=a.get(i),y=b.get(i);require(x.inkBounds==y.inkBounds&&x.source==y.source&&x.glyphId==y.glyphId&&x.x==y.x&&x.y==y.y&&x.xAdvance==y.xAdvance&&x.rasterStyle==y.rasterStyle,"normal/shadow未复用实际定位与variant身份");}
        require(a.get(1).resolvedStyle.color==(0x7f000000|normal.paletteColor(15))&&b.get(1).resolvedStyle.color==(0x7f000000|shadow.paletteColor(15))&&a.get(2).resolvedStyle.color!=(b.get(2).resolvedStyle.color),"阴影palette paintkey污染");
        TextLayout sampleA=adapter.sample(raw,normal,0x7f123456,1000,TextPolicy.SINGLE_LINE,new RasterSampling(2,1),0,0),sampleB=adapter.sample(raw,shadow,0x7f123456,1000,TextPolicy.SINGLE_LINE,new RasterSampling(2,1),0,0);
        require(glyphs(sampleA).get(1).resolvedStyle.color!=glyphs(sampleB).get(1).resolvedStyle.color&&adapter.cachedLayoutCount()==1,"samplekey未保留阴影palette");
        require(adapter.layout(raw,normal,0x7f123456,1000,TextPolicy.SINGLE_LINE)==first&&adapter.layout(raw,shadow,0x7f123456,1000,TextPolicy.SINGLE_LINE)==second,"颜色绑定hotcache身份分叉");
        String animated="§kAB§c§kCD";TextLayout normalPlan=adapter.layout(animated,normal,0x7fabcdef,1000,TextPolicy.SINGLE_LINE),shadowPlan=adapter.layout(animated,shadow,0x7f223344,1000,TextPolicy.SINGLE_LINE);
        long sourceSalt=normalPlan.input.text.hashCode();require(sourceSalt==shadowPlan.input.text.hashCode(),"normal/shadow源动画salt不一致");
        for(long tick=0;tick<8;tick++){
            List<TextLayout.Glyph> normalGlyphs=glyphs(adapter.sample(normalPlan,new RasterSampling(2,1),tick,sourceSalt)),shadowGlyphs=glyphs(adapter.sample(shadowPlan,new RasterSampling(2,1),tick,sourceSalt));
            require(normalGlyphs.size()==shadowGlyphs.size(),"阴影混淆glyph数量不同");
            for(int i=0;i<normalGlyphs.size();i++){TextLayout.Glyph x=normalGlyphs.get(i),y=shadowGlyphs.get(i);require(x.glyphId==y.glyphId&&x.source==y.source&&x.x==y.x&&x.y==y.y&&x.xAdvance==y.xAdvance&&x.rasterStyle.equals(y.rasterStyle),"同源同tick阴影混淆错形");}
            require(normalGlyphs.get(0).resolvedStyle.color==0x7fabcdef&&shadowGlyphs.get(0).resolvedStyle.color==0x7f223344&&normalGlyphs.get(2).resolvedStyle.color==(0x7f000000|normal.paletteColor(12))&&shadowGlyphs.get(2).resolvedStyle.color==(0x7f000000|shadow.paletteColor(12)),"同步混淆丢base/显式palette阴影颜色");
        }
        System.out.println("PASS vanilla same-source same-tick normal/shadow obfuscation glyph pattern with distinct colors");
        System.out.println("PASS vanilla normal/shadow share actual geometry variant/ink identities; distinct palette paint/sample");
    }
    private static void performance(VanillaTextService adapter,Settings s){
        String raw="§lHello §cworld§r بـب 👩‍💻";for(int i=0;i<300;i++){adapter.width(raw,s);adapter.sample(raw,s,-1,1000,TextPolicy.SINGLE_LINE,new RasterSampling(2,1),0,0);}
        long[] ns=new long[1000];for(int i=0;i<ns.length;i++){long start=System.nanoTime();adapter.width(raw,s);adapter.sample(raw,s,-1,1000,TextPolicy.SINGLE_LINE,new RasterSampling(2,1),0,0);ns[i]=System.nanoTime()-start;}Arrays.sort(ns);
        System.out.println("CPU HOT width+sample iterations="+ns.length+" p50us="+ns[499]/1000.0+" p95us="+ns[949]/1000.0+" p99us="+ns[989]/1000.0+" (非MC门槛)");
        for(int i=0;i<256;i++)adapter.sample("A"+i,s,-1,1000,TextPolicy.SINGLE_LINE,RasterSampling.LOGICAL,i,0);
        require(adapter.cachedLayoutCount()<=128&&adapter.cachedParseCount()<=128&&adapter.cachedSampleCount()<=128,"adapter cache无界");
    }
    public static void main(String[] args)throws Exception{
        List<FontSource> fonts=FontTextService.defaultSources(VanillaTextScenario.class.getClassLoader(),1);
        try(FontTextService font=new FontTextService(fonts,new LwjglFontEngine(),8,32,8192)){
            calibration(font);actualAlpha(font);VanillaTextService adapter=new VanillaTextService(font);Settings s=new Settings(12,9,9,true,false,palette());semantics(adapter,s);sampling(adapter,s);shadowReuse(adapter,s);performance(adapter,s);
            TextLayout before=adapter.sample("ABC",s,-1,1000,TextPolicy.SINGLE_LINE,RasterSampling.LOGICAL,0,0);font.reload(fonts);TextLayout after=adapter.sample("ABC",s,-1,1000,TextPolicy.SINGLE_LINE,RasterSampling.LOGICAL,0,0);require(after!=before&&after.generation>before.generation,"资源generation没有失效缓存");
        }
        final FontTextService[] holder={new FontTextService(fonts,new LwjglFontEngine(),8,32,8192)};VanillaTextService adapter=new VanillaTextService(()->holder[0]);Settings settings=new Settings(12,9,9,true,false,palette());
        TextLayout frozen=adapter.layout("§k👩‍💻",settings,-1,1000,TextPolicy.SINGLE_LINE);FontSource expected=frozen.style.candidates().get(0);holder[0].close();
        holder[0]=new FontTextService(FontTextService.defaultSources(VanillaTextScenario.class.getClassLoader(),2),new LwjglFontEngine(),8,32,8192);
        try{TextLayout replay=adapter.sample(frozen,new RasterSampling(2,3),9,0);require(glyphs(replay).get(0).source==expected,"旧混淆plan重放偷用新代际候选");}finally{holder[0].close();}
        System.out.println("PASS vanilla old frozen plan new service keeps explicit source snapshot");
        System.out.println("VanillaTextScenario PASS real Java8 FT/HB CPU；MC视觉/GL性能待宿主验收");
    }
}
