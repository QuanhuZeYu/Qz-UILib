package club.heiqi.uilib.ui.scene.integration;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.GlfwTestEnvironment;
import club.heiqi.uilib.testing.GlfwTestEnvironment.Profile;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.host.client.gl.Lwjgl3GlApi;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.lwjgl.system.MemoryUtil;
import club.heiqi.uilib.host.client.gl.GlGlyphAtlas;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.glyph.AsyncGlyphRasterizer;
import club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import static org.lwjgl.opengl.GL32C.*;

/** 正式字体→owned worker→budget atlas→PaintPlan→GL读回，无MC、无JUnit。
 * 软件只合成生产 native 位图；模拟GL_LINEAR透明padding和RGBA8逐次混合，容差3/255。
 */
public final class GlyphPipelineScenario extends TestApplication {
    private static final int W=640,H=280,TOLERANCE=3;
    private static final LayoutBox VIEWPORT=new LayoutBox(0,0,W,H);
    private static final float[] MVP={2f/W,0,0,0,0,-2f/H,0,0,0,0,1,0,-1,1,0,1};
    private static final GlGlyphAtlas.UploadBudget UPLOAD=new GlGlyphAtlas.UploadBudget(4096,10000000,2);
    private static AsyncGlyphRasterizer cpu(){return new AsyncGlyphRasterizer(new AsyncGlyphRasterizer.Budget(128,512,128,8*1024*1024,4,96*1024*1024,16,96),IsolatedFontEngine.shared());}
    private static PaintPlan plan(FontTextService fonts) {
        PaintPlanBuilder builder=new PaintPlanBuilder();builder.add(PaintCommand.rect(VIEWPORT,null,0xff182030));
        String[] texts={"中文 Latin e\u0301 שלום 123 العربية", "कर्म क्षि বাংলা ไทย ภาษา", "👩🏽‍💻 🇨🇳 1️⃣ 😀 ❤️ ♥︎"};
        for(int i=0;i<texts.length;i++) {
            TextLayout layout=fonts.layout(texts[i],new TextStyle("default",24,i==2?0xc030ff50:0xd0ffd060),600,TextPolicy.NORMAL);
            builder.add(PaintCommand.text(new LayoutBox(12,12+i*74,600,70),new LayoutBox(12,12+i*74,580,64),layout));
            if(i==0){builder.add(PaintCommand.rect(new LayoutBox(80,28,140,10),null,0x906030d0));
                builder.add(PaintCommand.text(new LayoutBox(18,18,600,70),new LayoutBox(20,14,230,50),layout.withStyle(layout.style.withColor(0x6030d0ff))));}
        }
        return builder.build();
    }
    private static String signature(PaintPlan plan){StringBuilder out=new StringBuilder();for(PaintCommand command:plan.commands())if(command.text!=null){
        out.append(command.text.width).append('/').append(command.text.height).append('/').append(command.text.generation);
        for(TextLayout.Line line:command.text.lines())for(TextLayout.Glyph g:line.glyphs())out.append(':').append(g.glyphId).append('/').append(g.x).append('/').append(g.y).append('/').append(g.xAdvance).append('/').append(g.yAdvance);
    }return out.toString();}
    private static Set<GlyphKey> keys(PaintPlan plan){Set<GlyphKey> keys=new LinkedHashSet<GlyphKey>();for(PaintCommand c:plan.commands())if(c.text!=null)
        for(TextLayout.Line line:c.text.lines())for(TextLayout.Glyph g:line.glyphs())keys.add(GlyphKey.of(g));return keys;}
    private static int[] uploadState() {
        int[] names={GL_ACTIVE_TEXTURE,GL_PIXEL_UNPACK_BUFFER_BINDING,GL_UNPACK_ALIGNMENT,GL_UNPACK_ROW_LENGTH,GL_UNPACK_IMAGE_HEIGHT,
            GL_UNPACK_SKIP_PIXELS,GL_UNPACK_SKIP_ROWS,GL_UNPACK_SKIP_IMAGES,GL_UNPACK_SWAP_BYTES,GL_UNPACK_LSB_FIRST};
        int[] values=new int[12];for(int i=0;i<names.length;i++)values[i]=glGetInteger(names[i]);
        glActiveTexture(GL_TEXTURE0);values[10]=glGetInteger(GL_TEXTURE_BINDING_2D);values[11]=glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);glActiveTexture(values[0]);return values;
    }
    private static int[] drawState(){int[] names={GL_CURRENT_PROGRAM,GL_VERTEX_ARRAY_BINDING,GL_ARRAY_BUFFER_BINDING,GL_ACTIVE_TEXTURE,
        GL_BLEND_EQUATION_RGB,GL_BLEND_EQUATION_ALPHA,GL_BLEND_SRC_RGB,GL_BLEND_DST_RGB,GL_BLEND_SRC_ALPHA,GL_BLEND_DST_ALPHA};
        int[] values=new int[18];for(int i=0;i<names.length;i++)values[i]=glGetInteger(names[i]);int[] box=new int[4];glGetIntegerv(GL_SCISSOR_BOX,box);System.arraycopy(box,0,values,10,4);
        int[] caps={GL_BLEND,GL_SCISSOR_TEST,GL_CULL_FACE,GL_DEPTH_TEST};for(int i=0;i<4;i++)values[14+i]=glIsEnabled(caps[i])?1:0;return values;}
    private static void clear(){boolean scissor=glIsEnabled(GL_SCISSOR_TEST);glDisable(GL_SCISSOR_TEST);glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);if(scissor)glEnable(GL_SCISSOR_TEST);}
    private static int[] readback(){ByteBuffer data=MemoryUtil.memAlloc(W*H*4);try{glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,data);int[] out=new int[W*H*4];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)for(int c=0;c<4;c++)out[(y*W+x)*4+c]=data.get(((H-1-y)*W+x)*4+c)&255;return out;
    }finally{MemoryUtil.memFree(data);}}
    private static void image(File output,int[] data)throws Exception{BufferedImage image=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=(y*W+x)*4,a=data[i+3];int r=a==0?0:Math.min(255,Math.round(data[i]*255f/a)),g=a==0?0:Math.min(255,Math.round(data[i+1]*255f/a)),b=a==0?0:Math.min(255,Math.round(data[i+2]*255f/a));image.setRGB(x,y,(a<<24)|(r<<16)|(g<<8)|b);}ImageIO.write(image,"png",output);}
    private static void compare(String name,int[] expected,int[] actual){int max=0,bad=0;for(int i=0;i<actual.length;i++){int d=Math.abs(actual[i]-expected[i]);max=Math.max(max,d);if(d>TOLERANCE)bad++;}
        System.out.println(name+" maxChannelDelta="+max+" badChannels="+bad+" tolerance="+TOLERANCE+"/255");if(bad!=0)throw new AssertionError(name+" 读回不符");}
    private static float sample(byte[] pixels,int w,int h,int x,int y,int c){return x<0||x>=w||y<0||y>=h?0:pixels[(y*w+x)*4+c]&255;}
    private static float linear(byte[] p,int w,int h,float x,float y,int c){int ix=(int)Math.floor(x),iy=(int)Math.floor(y);float fx=x-ix,fy=y-iy;
        return (sample(p,w,h,ix,iy,c)*(1-fx)+sample(p,w,h,ix+1,iy,c)*fx)*(1-fy)
            +(sample(p,w,h,ix,iy+1,c)*(1-fx)+sample(p,w,h,ix+1,iy+1,c)*fx)*fy;}
    private static void blend(int[] out,int x,int y,float[] src){int i=(y*W+x)*4;for(int c=0;c<4;c++)out[i+c]=Math.min(255,Math.round(src[c]+out[i+c]*(1-src[3]/255f)));}
    private static int[] software(GlPaintPlanRenderer.Prepared prepared,AsyncGlyphRasterizer cpu) {
        int[] out=new int[W*H*4];for(PaintCommand command:prepared.plan.commands()) {
            LayoutBox clip=command.clip;float alpha=(command.color>>>24)/255f;
            if(command.kind==PaintCommand.Kind.RECT) {
                float[] src={(command.color>>>16&255)*alpha,(command.color>>>8&255)*alpha,(command.color&255)*alpha,255*alpha};
                for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(command.box.contains(x,y)&&(clip==null||clip.contains(x,y)))blend(out,x,y,src);
            }else for(TextLayout.Line line:command.text.lines())for(TextLayout.Glyph glyph:line.glyphs()) {
                GlGlyphAtlas.Resident resident=prepared.resident(glyph);if(resident==null||resident.width==0||resident.height==0)continue;
                GlyphBitmap bitmap=cpu.peek(GlyphKey.of(glyph));if(bitmap==null)throw new AssertionError("软件参考位图意外淘汰");byte[] pixels=bitmap.pixels();
                float left=command.box.x+glyph.x+bitmap.bearingX,top=command.box.y+glyph.y-bitmap.bearingY;
                float right=left+bitmap.width*bitmap.pixelScale,bottom=top+bitmap.height*bitmap.pixelScale;
                int x0=Math.max(0,(int)Math.floor(left)),y0=Math.max(0,(int)Math.floor(top));
                int x1=Math.min(W,(int)Math.ceil(right)),y1=Math.min(H,(int)Math.ceil(bottom));
                for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++) {
                    if(x+.5f<left||x+.5f>=right||y+.5f<top||y+.5f>=bottom||clip!=null&&!clip.contains(x,y))continue;
                    float u=(x+.5f-left)/bitmap.pixelScale-.5f,v=(y+.5f-top)/bitmap.pixelScale-.5f;
                    float[] src=new float[4];src[3]=linear(pixels,bitmap.width,bitmap.height,u,v,3)*alpha;
                    if(bitmap.color)for(int c=0;c<3;c++)src[c]=linear(pixels,bitmap.width,bitmap.height,u,v,c)*alpha;
                    else {src[0]=(command.color>>>16&255)*src[3]/255f;src[1]=(command.color>>>8&255)*src[3]/255f;src[2]=(command.color&255)*src[3]/255f;}
                    blend(out,x,y,src);
                }
            }
        }return out;
    }
    private static PaintPlan hidden(PaintPlan old) {
        TextLayout.Glyph original=null;for(PaintCommand c:old.commands())if(c.text!=null)for(TextLayout.Line l:c.text.lines())for(TextLayout.Glyph g:l.glyphs())if(g.inkBounds.known){original=g;break;}
        if(original==null)throw new AssertionError("没有实际已知墨迹边界");List<TextLayout.Glyph> glyphs=new ArrayList<TextLayout.Glyph>();
        for(int i=0;i<10000;i++)glyphs.add(new TextLayout.Glyph(original.source,original.size,original.glyphId,0,1,0,1,10000+i,10000,original.xAdvance,0,false,false,original.inkBounds));
        TextStyle style=new TextStyle("default",24,-1);TextLayout.Line line=new TextLayout.Line("hidden",0,0,0,1,0,1,10000,0,24,4,28,glyphs);
        TextLayout layout=new TextLayout(20000,20000,style,Collections.singletonList(line));return new PaintPlanBuilder().add(PaintCommand.text(new LayoutBox(0,0,20000,20000),VIEWPORT,layout)).build();
    }
    private int[] run(GlGlyphAtlas.Mode mode,PaintPlan plan,File output) throws Exception {
        AsyncGlyphRasterizer cpu=cpu();
        onClose(()->{cpu.close();require(cpu.awaitClose(10000)&&cpu.stats().terminated&&cpu.stats().faces==0&&cpu.stats().faceBytes==0,"worker原生关闭未完成");});String name=mode==GlGlyphAtlas.Mode.TEXTURE_2D?"2d":"array";
        try(GlGlyphAtlas atlas=new GlGlyphAtlas(new Lwjgl3GlApi(),mode,new GlGlyphAtlas.Budget(256,2,8,256,128,4*1024*1024));
            GlPaintPlanRenderer renderer=new GlPaintPlanRenderer(new Lwjgl3GlApi(),cpu,atlas,64,128,GlProgramProvider.NONE)) {
            try(GlPaintPlanRenderer.Prepared hidden=renderer.prepare(hidden(plan),VIEWPORT,UPLOAD)) {
                if(hidden.demandCount()!=0||cpu.stats().started)throw new AssertionError("clip外一万字形启动队列");
            }
            String initial=signature(plan);int frame=0,previousReady=0,finalReady=0;boolean partialCompared=false;
            int[] finalPixels=null;long start=System.nanoTime();
            for(;;) {
                if(frame>1500||System.nanoTime()-start>30000000000L)throw new AssertionError("字形管线等待超时 "+cpu.workerFailure()+" failures="+cpu.failures().size());
                GlGlyphAtlas.Stats before=atlas.stats();int[] uploadBefore=uploadState();
                try(GlPaintPlanRenderer.Prepared prepared=renderer.prepare(plan,VIEWPORT,frame==0?new GlGlyphAtlas.UploadBudget(0,0,0):UPLOAD)) {
                    if(!Arrays.equals(uploadBefore,uploadState()))throw new AssertionError("upload状态泄漏");
                    GlGlyphAtlas.Stats after=atlas.stats();if(after.uploadedBytes-before.uploadedBytes>UPLOAD.bytes||after.uploadCalls-before.uploadCalls>UPLOAD.calls||after.gpuBytes>4*1024*1024)throw new AssertionError("GPU预算穿透");
                    int ready=prepared.readyCount();if(ready<previousReady)throw new AssertionError("pin当前需求被淘汰");previousReady=ready;
                    if(frame==0&&ready!=0)throw new AssertionError("首次不是blank");
                    clear();int[] drawBefore=drawState();prepared.render(MVP,H,false);
                    if(!Arrays.equals(drawBefore,drawState())||!Arrays.equals(uploadBefore,uploadState()))throw new AssertionError("回放状态泄漏");
                    if(!initial.equals(signature(plan)))throw new AssertionError("ready改变布局");
                    if(frame==0){int[] pixels=readback();compare(name+" first-blank",software(prepared,cpu),pixels);image(new File(output,name+"-first.png"),pixels);}
                    if(!partialCompared&&ready>0&&ready<prepared.demandCount()) {
                        int[] pixels=readback();compare(name+" partial",software(prepared,cpu),pixels);image(new File(output,name+"-partial.png"),pixels);partialCompared=true;
                    }
                    if(ready==prepared.demandCount()) {
                        finalReady=ready;finalPixels=readback();int[] reference=software(prepared,cpu);compare(name+" final",reference,finalPixels);
                        image(new File(output,name+"-final.png"),finalPixels);image(new File(output,name+"-software.png"),reference);
                        // 明确外部委托屏障：退出并恢复宿主状态后委托，再继续原序范围。
                        clear();try(GlPaintPlanRenderer.Replay replay=prepared.begin(MVP,H,false)) {
                            int i=0;for(PaintCommand command:plan.commands()){replay.draw(command);if(++i==2)replay.delegate(()->{if(!Arrays.equals(drawBefore,drawState()))throw new AssertionError("delegate前未恢复");});}
                        }
                        compare(name+" delegate",reference,readback());break;
                    }
                }
                cpu.drainReady(128);if(cpu.workerFailure()!=null||!cpu.failures().isEmpty())throw new AssertionError("worker失败 "+cpu.workerFailure());
                Thread.sleep(2);frame++;
            }
            long rasterized=cpu.stats().rasterized;for(GlyphKey key:keys(plan))for(int i=0;i<20;i++)cpu.request(key);
            if(cpu.stats().rasterized!=rasterized||cpu.stats().pending!=0)throw new AssertionError("相同glyph重复排队");
            AsyncGlyphRasterizer.Stats cs=cpu.stats();GlGlyphAtlas.Stats gs=atlas.stats();
            if(cs.bitmapBytes>8*1024*1024||cs.faceBytes>96*1024*1024||cs.faces>4||cs.pending>128||cs.ready>128||cs.cacheEntries>512)throw new AssertionError("CPU预算穿透");
            System.out.println(name+" frames="+frame+" ready="+finalReady+" rasterized="+cs.rasterized+" deduplicated="+cs.deduplicated
                +" bitmapBytes="+cs.bitmapBytes+" faceBytes="+cs.faceBytes+" faces="+cs.faces+" gpuBytes="+gs.gpuBytes+" uploadedBytes="+gs.uploadedBytes+" uploadCalls="+gs.uploadCalls
                +" groups="+gs.groups+" clippedGlyphs="+renderer.clippedGlyphs()+" blankGlyphs="+renderer.blankGlyphs()+" draw="+renderer.counters().draws+" uploads="+renderer.counters().vertexUploads);
            pressure(cpu,plan,name);if(glGetError()!=GL_NO_ERROR)throw new AssertionError("GL error");return finalPixels;
        }
    }
    private static void pressure(AsyncGlyphRasterizer cpu,PaintPlan plan,String name) {
        List<GlyphKey> small=new ArrayList<GlyphKey>();for(GlyphKey key:keys(plan)) {
            GlyphBitmap b=cpu.peek(key);if(b!=null&&!b.color&&b.width>0&&b.height>0&&b.width<62&&b.height<62)small.add(key);
        }
        if(small.size()<3)throw new AssertionError("不足压力测试字形");
        try(GlGlyphAtlas tiny=new GlGlyphAtlas(new Lwjgl3GlApi(),GlGlyphAtlas.Mode.TEXTURE_2D,new GlGlyphAtlas.Budget(64,1,1,1,32,64*64*4))) {
            GlGlyphAtlas.Resident old=null;long evictions=0;
            for(GlyphKey key:small) {
                try(GlGlyphAtlas.Frame frame=tiny.beginFrame(Collections.singletonList(key))) {
                    frame.prepare(cpu,new GlGlyphAtlas.UploadBudget(16384,1000000000,8));frame.freeze();
                    GlGlyphAtlas.Resident resident=frame.lookup(key);if(resident==null)throw new AssertionError("单字形预算不能逐个复用");
                    if(old!=null)try{tiny.resolve(old.resource,old.generation);throw new AssertionError("淘汰代际仍可resolve");}catch(IllegalArgumentException expected){}
                    tiny.resolve(resident.resource,resident.generation);
                    try{frame.prepare(cpu,UPLOAD);throw new AssertionError("freeze允许upload");}catch(IllegalStateException expected){}
                    old=resident;
                }
                if(tiny.stats().gpuBytes>64*64*4||tiny.stats().residents>1)throw new AssertionError("tiny图集预算穿透");
            }
            evictions=tiny.stats().evictedGroups;
            int texture;
            try(GlGlyphAtlas.Frame frame=tiny.beginFrame(Collections.singletonList(small.get(small.size()-1)))) {
                frame.freeze();texture=tiny.resolve(old.resource,old.generation).id;
            }
            int unit=glGetInteger(GL_ACTIVE_TEXTURE);glActiveTexture(GL_TEXTURE5);int oldBinding=glGetInteger(GL_TEXTURE_BINDING_2D);glBindTexture(GL_TEXTURE_2D,texture);glActiveTexture(unit);
            try(GlGlyphAtlas.Frame frame=tiny.beginFrame(Collections.singletonList(small.get(0)))) {
                frame.prepare(cpu,new GlGlyphAtlas.UploadBudget(16384,1000000000,8));frame.freeze();
                if(frame.lookup(small.get(0))!=null)throw new AssertionError("淘汰仍被其他单元绑定的纹理");
                glActiveTexture(GL_TEXTURE5);if(glGetInteger(GL_TEXTURE_BINDING_2D)!=texture)throw new AssertionError("其他单元绑定被删除污染");glActiveTexture(unit);
            }finally{glActiveTexture(GL_TEXTURE5);glBindTexture(GL_TEXTURE_2D,oldBinding);glActiveTexture(unit);}
            try(GlGlyphAtlas.Frame frame=tiny.beginFrame(small)) {
                frame.prepare(cpu,new GlGlyphAtlas.UploadBudget(16384,1000000000,8));frame.freeze();
                int ready=0;for(GlyphKey key:small)if(frame.lookup(key)!=null)ready++;
                if(ready!=1||tiny.stats().rejected==0)throw new AssertionError("pin预算耗尽未留白");
                System.out.println(name+" pressure residents="+ready+" demanded="+small.size()+" evictedGroups="+evictions+" rejected="+tiny.stats().rejected+" gpuBytes="+tiny.stats().gpuBytes+" freezeUploadBlocked=true staleGenerationRejected=true");
            }
        }
    }
    private void cpuPressure(PaintPlan plan) throws Exception {
        List<GlyphKey> keys=new ArrayList<GlyphKey>(keys(plan));
        AsyncGlyphRasterizer worker=new AsyncGlyphRasterizer(new AsyncGlyphRasterizer.Budget(1,1,1,1024*1024,1,96*1024*1024,1,96),IsolatedFontEngine.shared());
        onClose(()->{worker.close();require(worker.awaitClose(10000)&&worker.stats().faces==0,"tinyCPU原生关闭失败");});
        {
            for(int i=0;i<3;i++) {
                GlyphKey key=keys.get(i);worker.request(key);long end=System.nanoTime()+10000000000L;
                while(worker.peek(key)==null) {
                    worker.drainReady(1);AsyncGlyphRasterizer.Stats s=worker.stats();
                    if(s.pending>1||s.ready>1||s.cacheEntries>1||s.faces>1||s.bitmapBytes>1024*1024)throw new AssertionError("tinyCPU预算穿透");
                    if(System.nanoTime()>end||worker.workerFailure()!=null||!worker.failures().isEmpty())throw new AssertionError("tinyCPU完成失败");Thread.sleep(2);
                }
                worker.drainReady(1);
            }
            if(worker.stats().evicted<2||worker.peek(keys.get(0))!=null)throw new AssertionError("CPU LRU未淘汰");
            worker.request(new GlyphKey(keys.get(0).source,97,keys.get(0).glyphId));if(worker.stats().rejected==0)throw new AssertionError("尺寸预算拒绝不可观察");
            GlyphKey bad=new GlyphKey(keys.get(0).source,24,Integer.MAX_VALUE);worker.request(bad);long end=System.nanoTime()+10000000000L;
            while(worker.failures().isEmpty()){worker.drainReady(1);if(System.nanoTime()>end)throw new AssertionError("光栅错误未发布");Thread.sleep(2);}
            long requests=worker.stats().deduplicated;worker.request(bad);if(worker.stats().deduplicated!=requests+1||worker.stats().pending!=0)throw new AssertionError("失败身份未去重");
            System.out.println("cpuPressure cacheEntries="+worker.stats().cacheEntries+" evicted="+worker.stats().evicted+" rejected="+worker.stats().rejected+" observableError="+worker.failures().get(0).message);
        }
    }
    private void oldSource(FontTextService fonts,PaintPlan old) throws Exception {
        GlyphKey key=keys(old).iterator().next();List<FontSource> newSources=FontTextService.defaultSources(FontTextService.class.getClassLoader(),2);fonts.reload(newSources);
        TextLayout fresh=fonts.layout("A",new TextStyle("default",24,-1),100);GlyphKey newer=GlyphKey.of(fresh.lines().get(0).glyphs().get(0));
        if(newer.source==key.source)throw new AssertionError("reload来源未换代");AsyncGlyphRasterizer worker=cpu();
        onClose(()->{worker.close();require(worker.awaitClose(10000),"reload worker未关闭");});
        {worker.request(key);worker.request(newer);long end=System.nanoTime()+10000000000L;
            while(worker.peek(key)==null||worker.peek(newer)==null){worker.drainReady(128);if(System.nanoTime()>end||worker.workerFailure()!=null||!worker.failures().isEmpty())throw new AssertionError("旧source重开失败");Thread.sleep(2);}
            if(worker.stats().rasterized!=2)throw new AssertionError("旧新source身份合并");System.out.println("reload oldSource="+key.source+" newSource="+newer.source+" workerRasterized=2 oldPlanStable=true");
        }
    }
    @Override protected void runTests() throws Exception {
        String[] args=arguments();
        File output=new File(args.length==0?"build/font-gl-probe/glyph-output":args[0]);if(!output.isDirectory()&&!output.mkdirs())throw new IllegalStateException("不能创建输出目录");
        GlfwTestEnvironment environment=own(new GlfwTestEnvironment());
        own(environment.window(W,H,"GlyphPipelineScenario",Profile.CORE,2,false,true));
            System.out.println("LWJGL="+org.lwjgl.Version.getVersion()+" Java="+System.getProperty("java.version")+" vendor="+glGetString(GL_VENDOR)+" renderer="+glGetString(GL_RENDERER)+" version="+glGetString(GL_VERSION));
            int target=glGenTextures(),fbo=glGenFramebuffers(),pbo=glGenBuffers(),sentinel2d=glGenTextures(),sentinelArray=glGenTextures();
            onClose(()->{glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);glDeleteBuffers(pbo);glBindFramebuffer(GL_FRAMEBUFFER,0);glDeleteFramebuffers(fbo);glDeleteTextures(target);glDeleteTextures(sentinel2d);glDeleteTextures(sentinelArray);});
            FontTextService fonts=own(new FontTextService(IsolatedFontEngine.shared()));
            {
                glBindTexture(GL_TEXTURE_2D,target);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,W,H,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
                glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,target,0);
                require(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"FBO incomplete");glViewport(0,0,W,H);glDisable(GL_DITHER);glDisable(GL_FRAMEBUFFER_SRGB);
                glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,sentinel2d);glBindTexture(GL_TEXTURE_2D_ARRAY,sentinelArray);
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER,pbo);glBufferData(GL_PIXEL_UNPACK_BUFFER,4096,GL_STREAM_DRAW);
                glPixelStorei(GL_UNPACK_ALIGNMENT,8);glPixelStorei(GL_UNPACK_ROW_LENGTH,17);glPixelStorei(GL_UNPACK_IMAGE_HEIGHT,19);
                glPixelStorei(GL_UNPACK_SKIP_PIXELS,3);glPixelStorei(GL_UNPACK_SKIP_ROWS,5);glPixelStorei(GL_UNPACK_SKIP_IMAGES,7);glPixelStorei(GL_UNPACK_SWAP_BYTES,1);glPixelStorei(GL_UNPACK_LSB_FIRST,1);
                glActiveTexture(GL_TEXTURE3);glEnable(GL_SCISSOR_TEST);glScissor(3,5,7,11);glEnable(GL_BLEND);glBlendEquationSeparate(GL_FUNC_SUBTRACT,GL_FUNC_REVERSE_SUBTRACT);
                glBlendFuncSeparate(GL_DST_ALPHA,GL_SRC_ALPHA,GL_ZERO,GL_ONE);glEnable(GL_CULL_FACE);glEnable(GL_DEPTH_TEST);
                PaintPlan plan=plan(fonts);String sig=signature(plan);int[][] routes=new int[2][];
                scenario("2D字形准备回放与图集压力",()->routes[0]=run(GlGlyphAtlas.Mode.TEXTURE_2D,plan,output));
                scenario("array字形准备回放与图集压力",()->routes[1]=run(GlGlyphAtlas.Mode.TEXTURE_ARRAY,plan,output));
                scenario("两宿主字形像素一致",()->compare("2d-vs-array",routes[0],routes[1]));
                scenario("CPU缓存压力与错误可观察",()->cpuPressure(plan));
                scenario("来源换代保留旧计划",()->{oldSource(fonts,plan);require(sig.equals(signature(plan)),"reload修改旧plan");});
                System.out.println("GLYPH_PIPELINE_SCENARIO_PASS firstBlank layoutStable clip10000 queueDedup partialBudget nativeWorkerClose uploadStateRestore replayStateRestore oldSourceReload");
            }
    }
    public static void main(String[] args) throws Exception { new GlyphPipelineScenario().run(args); }
}
