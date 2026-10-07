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
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import org.lwjgl.system.MemoryUtil;
import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.font.FramebufferCoordinates;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.RasterSampling;
import club.heiqi.uilib.ui.scene.text.StyleSpan;
import club.heiqi.uilib.ui.scene.text.TextDecoration;
import club.heiqi.uilib.ui.scene.text.TextEffects;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import static org.lwjgl.opengl.GL32C.*;

/** generic attributed输入→共享owner→真实GL；软件参考只消费native bitmap和pixel-center双线性定义。 */
public final class AttributedGlyphGlScenario extends TestApplication {
    private static final int W=460,H=90,OX=7,OY=8;
    private static final LayoutBox VIEW=new LayoutBox(0,0,W,H),CLIP=new LayoutBox(9,3,410,70);
    private static TextStyle style(int color,boolean effect){return new TextStyle("default",18.5f,color,null,
        effect?new TextEffects(true,true,true,true,false,0.8f,0.18f):TextEffects.NORMAL,new TextDecoration(3,1,0,-6,1));}
    private static TextLayout logical(ClientFontServices owner){
        String[] chunks={"Red ","Blue ","سل","ام ","क","ि ","😀 ","BoldItalic"};
        int[] colors={0xfff03030,0xff3080f0,0xffee8844,0xff44cccc,0xffcc88ee,0xff55cc88,0xff00ff00,0xffeecc44};
        StringBuilder text=new StringBuilder();List<StyleSpan> spans=new ArrayList<StyleSpan>();
        for(int i=0;i<chunks.length;i++){int start=text.length();text.append(chunks[i]);spans.add(new StyleSpan(start,text.length(),style(colors[i],i==chunks.length-1)));}
        AttributedText input=new AttributedText(TextInput.string(text.toString()),text.toString(),style(-1,false),spans);
        return owner.fonts().layout(input,440,TextPolicy.SINGLE_LINE);
    }
    private static List<TextLayout.Glyph> glyphs(TextLayout layout){List<TextLayout.Glyph> out=new ArrayList<TextLayout.Glyph>();for(TextLayout.Line l:layout.lines())out.addAll(l.glyphs());return out;}
    private static void stable(TextLayout original,TextLayout sampled,float scale){
        if(original.advanceWidth!=sampled.advanceWidth||original.advanceHeight!=sampled.advanceHeight||original.width!=sampled.width||original.height!=sampled.height)throw new AssertionError("采样改变layout尺寸");
        List<TextLayout.Glyph> a=glyphs(original),b=glyphs(sampled);if(a.size()!=b.size())throw new AssertionError("采样重shaping");
        for(int i=0;i<a.size();i++){TextLayout.Glyph x=a.get(i),y=b.get(i);if(x.x!=y.x||x.y!=y.y||x.xAdvance!=y.xAdvance||x.glyphId!=y.glyphId||x.logicalStart!=y.logicalStart||x.logicalEnd!=y.logicalEnd||Math.abs(y.rasterToLayout*scale-1)>0.00001)throw new AssertionError("采样改变logical定位");}
    }
    private static int[] read(){ByteBuffer b=MemoryUtil.memAlloc(W*H*4);try{glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,b);int[] out=new int[W*H*4];for(int y=0;y<H;y++)for(int x=0;x<W;x++)for(int c=0;c<4;c++)out[(y*W+x)*4+c]=b.get(((H-1-y)*W+x)*4+c)&255;return out;}finally{MemoryUtil.memFree(b);}}
    private static void image(File file,int[] rgba)throws Exception{BufferedImage im=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=(y*W+x)*4,a=rgba[i+3];int r=a==0?0:Math.min(255,Math.round(rgba[i]*255f/a)),g=a==0?0:Math.min(255,Math.round(rgba[i+1]*255f/a)),b=a==0?0:Math.min(255,Math.round(rgba[i+2]*255f/a));im.setRGB(x,y,(a<<24)|(r<<16)|(g<<8)|b);}ImageIO.write(im,"png",file);}
    private static int texel(byte[] data,int w,int h,int x,int y,int channel){return x<0||y<0||x>=w||y>=h?0:data[(y*w+x)*4+channel]&255;}
    private static double sample(byte[] data,int w,int h,double x,double y,int c){int ix=(int)Math.floor(x),iy=(int)Math.floor(y);double fx=x-ix,fy=y-iy;return texel(data,w,h,ix,iy,c)*(1-fx)*(1-fy)+texel(data,w,h,ix+1,iy,c)*fx*(1-fy)+texel(data,w,h,ix,iy+1,c)*(1-fx)*fy+texel(data,w,h,ix+1,iy+1,c)*fx*fy;}
    private static void blend(int[] out,int index,double[] src){double alpha=src[3]/255;for(int c=0;c<4;c++)out[index+c]=(int)Math.max(0,Math.min(255,Math.round(src[c]+out[index+c]*(1-alpha))));}
    private static boolean inside(int x,int y){return x>=CLIP.x&&y>=CLIP.y&&x<CLIP.x+CLIP.width&&y<CLIP.y+CLIP.height;}
    private static int[] reference(ClientFontServices owner,TextLayout layout){int[] out=new int[W*H*4];
        for(TextLayout.Line line:layout.lines()){
            for(TextLayout.Glyph g:line.glyphs()){
                GlyphBitmap bm=owner.rasterizer().peek(GlyphKey.of(g));if(bm==null)throw new AssertionError("缺少nativebitmap参考");if(bm.width==0||bm.height==0)continue;
                byte[] data=bm.pixels();double unit=bm.pixelScale*g.rasterToLayout,left=OX+g.x+bm.bearingX*g.rasterToLayout,top=OY+g.y-bm.bearingY*g.rasterToLayout;
                double right=left+bm.width*unit,bottom=top+bm.height*unit;int color=g.resolvedStyle.color;double ca=(color>>>24)/255.0;
                for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(inside(x,y)&&x+0.5>=left&&x+0.5<right&&y+0.5>=top&&y+0.5<bottom){
                    double tx=(x+0.5-left)/unit-0.5,ty=(y+0.5-top)/unit-0.5;double[] src=new double[4];
                    src[3]=sample(data,bm.width,bm.height,tx,ty,3)*ca;
                    for(int c=0;c<3;c++)src[c]=bm.color?sample(data,bm.width,bm.height,tx,ty,c)*ca:src[3]*((color>>>(16-c*8))&255)/255.0;
                    blend(out,(y*W+x)*4,src);
                }
            }
            for(TextLayout.Decoration d:line.decorations()){double left=OX+d.x,top=OY+d.y,alpha=(d.color>>>24)/255.0;double[] src={((d.color>>>16)&255)*alpha,((d.color>>>8)&255)*alpha,(d.color&255)*alpha,255*alpha};
                for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(inside(x,y)&&x+0.5>=left&&x+0.5<left+d.width&&y+0.5>=top&&y+0.5<top+d.height)blend(out,(y*W+x)*4,src);
            }
        }return out;
    }
    private static void compare(ClientFontServices owner,TextLayout layout,int[] actual,int[] expected,float scale){
        // 像素中心恰在quad边界时由驱动subpixel/top-left覆盖决定；独立参考不冒充该离散规则。
        // 仅标记与实际nativebitmap边界距离<=1e-4的点，其余全通道必须满足小容差。
        boolean[] ties=new boolean[W*H];
        for(TextLayout.Glyph g:glyphs(layout)){GlyphBitmap bm=owner.rasterizer().peek(GlyphKey.of(g));double left=OX+g.x+bm.bearingX*g.rasterToLayout,top=OY+g.y-bm.bearingY*g.rasterToLayout;
            double right=left+bm.width*bm.pixelScale*g.rasterToLayout,bottom=top+bm.height*bm.pixelScale*g.rasterToLayout;
            for(int y=0;y<H;y++)for(int x=0;x<W;x++)if((x+0.5>=left-0.0001&&x+0.5<=right+0.0001&&(Math.abs(y+0.5-top)<=0.0001||Math.abs(y+0.5-bottom)<=0.0001))
                ||(y+0.5>=top-0.0001&&y+0.5<=bottom+0.0001&&(Math.abs(x+0.5-left)<=0.0001||Math.abs(x+0.5-right)<=0.0001)))ties[y*W+x]=true;
        }
        int tieDifferences=0,rawMax=0;int max=0,bad=0,lit=0;long sum=0;for(int i=0;i<actual.length;i++){int delta=Math.abs(actual[i]-expected[i]);rawMax=Math.max(rawMax,delta);if(i%4==3&&actual[i]>0)lit++;if(ties[i/4]&&delta>4){tieDifferences++;continue;}max=Math.max(max,delta);sum+=delta;if(delta>4){if(bad<12)System.out.println("diff x="+(i/4%W)+" y="+(i/4/W)+" c="+(i%4)+" actual="+actual[i]+" ref="+expected[i]);bad++;}}
        System.out.println("scale="+scale+" bilinearNativeReference rawMaxDelta="+rawMax+" nonBoundaryMaxDelta="+max+" badChannelsOver4="+bad+" boundaryTieChannels="+tieDifferences+" absoluteError="+sum+" visiblePixels="+lit);
        if(bad!=0||lit==0)throw new AssertionError("scalar pixel-center reference不符");
    }
    private static void decorationClip(ClientFontServices owner,GlPaintPlanRenderer renderer) {
        TextLayout.Decoration d=new TextLayout.Decoration(TextLayout.Decoration.Kind.UNDERLINE,0,40,30,1,0xff33aa77,0,1,0,1);
        TextLayout.Line line=new TextLayout.Line(" ",0,0,0,1,0,1,30,0,0,0,50,Collections.<TextLayout.Glyph>emptyList(),Collections.<TextLayout.Cluster>emptyList(),Collections.singletonList(d));
        TextLayout layout=new TextLayout(30,50,style(-1,false),Collections.singletonList(line));
        LayoutBox narrow=new LayoutBox(20,0,7,H);PaintPlan plan=new PaintPlanBuilder().add(PaintCommand.text(new LayoutBox(0,0,W,H),narrow,layout)).build();
        glClear(GL_COLOR_BUFFER_BIT);try(GlPaintPlanRenderer.Prepared p=owner.prepare(renderer,plan,VIEW)){p.render(FramebufferCoordinates.orthographic(W,H),H,false);}
        int[] actual=read();int lit=0;
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=(y*W+x)*4;boolean expected=y==40&&x>=20&&x<27;
            if((actual[i+3]!=0)!=expected)throw new AssertionError("onepixel decoration clip几何不符");
            if(expected){lit++;if(actual[i]!=0x33||actual[i+1]!=0xaa||actual[i+2]!=0x77||actual[i+3]!=255)throw new AssertionError("decoration颜色不符");}}
        if(lit!=7)throw new AssertionError("decoration裁剪计数");System.out.println("decoration onePixelHeight clippedPixels=7 exactResolvedColor=true");
    }
    private GlfwTestEnvironment environment;
    private ClientFontServices owner;
    private TextLayout logical;
    private GlPaintPlanRenderer renderer;
    private File output;
    private int decorations;

    @Override protected void setup() throws Exception {
        // owner在无context度量后才创建窗口；预留宿主释放位保证owner始终先关闭。
        onClose(()->{if(environment!=null)environment.close();});
        owner=new ClientFontServices(AttributedGlyphGlScenario.class.getClassLoader(),1,ClientFontServices.Settings.defaults(),IsolatedFontEngine.shared(),new Lwjgl3GlApi(),GlProgramProvider.NONE);
        onClose(()->{owner.close();require(owner.awaitRasterizerClose(10000),"workerclose");});
    }
    @Override protected void runTests() throws Exception {
        String[] args=arguments();
        output=new File(args.length==0?"build/font-attributed-gl/output":args[0]);require(output.isDirectory()||output.mkdirs(),"输出目录");
        scenario("无context富文本度量保持lazy",()->{logical=logical(owner);require(!owner.hasGlResources()&&!owner.rasterizer().stats().started,"度量提前启动GLworker");});
        environment=new GlfwTestEnvironment();
        environment.window(W,H,"attributed glyph",Profile.CORE,2);
            System.out.println("LWJGL="+org.lwjgl.Version.getVersion()+" Java="+System.getProperty("java.version")+" renderer="+glGetString(GL_RENDERER)+" version="+glGetString(GL_VERSION));
            int texture=glGenTextures(),fbo=glGenFramebuffers();
            onClose(()->{glBindFramebuffer(GL_FRAMEBUFFER,0);glDeleteFramebuffers(fbo);glDeleteTextures(texture);});
        glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,W,H,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
        if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new AssertionError("FBO");glViewport(0,0,W,H);glDisable(GL_DITHER);glDisable(GL_FRAMEBUFFER_SRGB);
        renderer=owner.sharedPlanRenderer();decorations=0;for(TextLayout.Line l:logical.lines())decorations+=l.decorations().size();if(decorations<2)throw new AssertionError("underline/strike缺失");
        for(float scale:new float[]{1,1.5f,2,3}) scenario("富文本采样与像素参考 scale="+scale,()->sampling(scale));
        scenario("单像素装饰线裁剪",()->decorationClip(owner,renderer));
        require(glGetError()==GL_NO_ERROR,"GLerror");
        System.out.println("ATTRIBUTED_GLYPH_GL_SCENARIO_PASS logicalEm18.5 sampling1_1.5_2_3 layoutStable resolvedColors boldItalic decorations nativePremultBilinearReference");
    }
    private void sampling(float scale) throws Exception {
            TextLayout sampled=owner.fonts().sample(logical,new RasterSampling(scale,1));stable(logical,sampled,scale);
            PaintPlan plan=new PaintPlanBuilder().add(PaintCommand.text(new LayoutBox(OX,OY,W,H),CLIP,sampled)).build();
            long end=System.nanoTime()+20000000000L;int frames=0;
            for(;;){glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);boolean all=true;
                try(GlPaintPlanRenderer.Prepared p=owner.prepare(renderer,plan,VIEW)){all=p.readyCount()==p.demandCount();p.render(FramebufferCoordinates.orthographic(W,H),H,false);}
                if(all)break;if(System.nanoTime()>end)throw new AssertionError("富文本字形未ready");Thread.sleep(2);frames++;
            }
            int[] actual=read(),expected=reference(owner,sampled);
            image(new File(output,"scale-"+scale+"-gl.png"),actual);image(new File(output,"scale-"+scale+"-reference.png"),expected);compare(owner,sampled,actual,expected,scale);
            // 样式色不应染掉emoji原色；真实读回中的黄色像素证明保留RGBA。
            boolean colored=false;for(TextLayout.Glyph g:glyphs(sampled))if(g.color){GlyphBitmap bm=owner.rasterizer().peek(GlyphKey.of(g));if(!bm.color)throw new AssertionError("emoji没有colorbitmap");int yellow=0;
                for(int y=0;y<H;y++)for(int x=Math.max(0,(int)(OX+g.x-3));x<Math.min(W,(int)(OX+g.x+g.xAdvance+3));x++){int i=(y*W+x)*4;if(actual[i]>80&&actual[i+1]>50&&actual[i+2]<actual[i]/2)yellow++;}if(yellow==0)throw new AssertionError("绿色文本emoji原黄色丢失");colored=true;}
            if(!colored)throw new AssertionError("color emoji探针未命中");
            for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(!inside(x,y)&&actual[(y*W+x)*4+3]!=0)throw new AssertionError("clip外像素");
            System.out.println("scale="+scale+" advance="+sampled.advanceWidth+" decorations="+decorations+" readyFrames="+frames+" originalEmojiColor=true clipped=true");
    }
    public static void main(String[] args) throws Exception { new AttributedGlyphGlScenario().run(args); }
}
