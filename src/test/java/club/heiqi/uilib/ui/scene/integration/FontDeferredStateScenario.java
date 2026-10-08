package club.heiqi.uilib.ui.scene.integration;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.GlfwTestEnvironment;
import club.heiqi.uilib.testing.GlfwTestEnvironment.Profile;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.Lwjgl3GlApi;

import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Paths;
import java.io.File;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.Collections;
import org.lwjgl.BufferUtils;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.font.context.StandardFontContext;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.font.FontSource;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.host.client.font.context.LwjglFontContextCapture;

/** 延期producer历史捕捉，以及标准字体用正式native/atlas/renderer的真实GPU coverage回读；不替代MC/Angelica初始化。 */
public final class FontDeferredStateScenario extends TestApplication {
    private static final float[] IDENTITY={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
    private static void stateA(int framebuffer){
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,framebuffer);GL11.glViewport(3,5,41,43);
        GL11.glEnable(GL11.GL_BLEND);GL14.glBlendFuncSeparate(GL14.GL_CONSTANT_COLOR,GL14.GL_ONE_MINUS_CONSTANT_COLOR,GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_SUBTRACT,GL14.GL_FUNC_REVERSE_SUBTRACT);GL14.glBlendColor(0.25f,0.5f,0.75f,1);
        GL11.glEnable(GL11.GL_STENCIL_TEST);GL20.glStencilFuncSeparate(GL11.GL_FRONT,GL11.GL_EQUAL,3,0x35);GL20.glStencilMaskSeparate(GL11.GL_FRONT,0x53);GL20.glStencilOpSeparate(GL11.GL_FRONT,GL11.GL_REPLACE,GL11.GL_INCR,GL11.GL_DECR);
        GL20.glStencilFuncSeparate(GL11.GL_BACK,GL11.GL_NOTEQUAL,7,0x27);GL20.glStencilMaskSeparate(GL11.GL_BACK,0x72);GL20.glStencilOpSeparate(GL11.GL_BACK,GL11.GL_ZERO,GL11.GL_INVERT,GL11.GL_KEEP);
        GL11.glColorMask(true,false,true,false);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_GREATER);GL11.glDepthMask(false);GL11.glDepthRange(0.125,0.875);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(7,9,11,13);GL11.glEnable(GL11.GL_CULL_FACE);GL11.glCullFace(GL11.GL_FRONT);GL11.glFrontFace(GL11.GL_CW);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);GL11.glPolygonOffset(1.25f,-2.5f);
    }
    private static void stateB(int framebuffer){
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,framebuffer);GL11.glViewport(0,0,64,64);GL11.glDisable(GL11.GL_BLEND);GL14.glBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE,GL11.GL_ZERO);
        GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD);GL14.glBlendColor(0,0,0,0);GL11.glDisable(GL11.GL_STENCIL_TEST);GL20.glStencilFuncSeparate(GL11.GL_FRONT_AND_BACK,GL11.GL_ALWAYS,0,-1);GL20.glStencilMaskSeparate(GL11.GL_FRONT_AND_BACK,-1);GL20.glStencilOpSeparate(GL11.GL_FRONT_AND_BACK,GL11.GL_KEEP,GL11.GL_KEEP,GL11.GL_KEEP);
        GL11.glColorMask(false,true,false,true);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LESS);GL11.glDepthMask(true);GL11.glDepthRange(0,1);GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glScissor(0,0,64,64);
        GL11.glDisable(GL11.GL_CULL_FACE);GL11.glCullFace(GL11.GL_BACK);GL11.glFrontFace(GL11.GL_CCW);GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);GL11.glPolygonOffset(0,0);
    }
    private static void verifyA(FontRenderContext a,int framebuffer){
        FontRenderContext.RasterState r=a.rasterState;
        require(a.drawFramebuffer==framebuffer&&Arrays.equals(a.projection.viewport(),new int[]{3,5,41,43}),"historical target/viewport");
        require(a.depthTest&&!a.depthWrite&&a.depthFunction==GL11.GL_GREATER&&a.scissorEnabled&&Arrays.equals(a.scissor(),new int[]{7,9,11,13}),"historical depth/scissor");
        require(r.blendEnabled&&r.blendSrcRgb==GL14.GL_CONSTANT_COLOR&&r.blendDstRgb==GL14.GL_ONE_MINUS_CONSTANT_COLOR&&r.blendSrcAlpha==GL11.GL_SRC_ALPHA&&r.blendDstAlpha==GL11.GL_ONE_MINUS_SRC_ALPHA&&r.blendEquationRgb==GL14.GL_FUNC_SUBTRACT&&r.blendEquationAlpha==GL14.GL_FUNC_REVERSE_SUBTRACT,"historical blend");
        require(Arrays.equals(r.blendColor(),new float[]{0.25f,0.5f,0.75f,1})&&Arrays.equals(r.depthRange(),new float[]{0.125f,0.875f})&&Arrays.equals(r.colorMask(),new boolean[]{true,false,true,false}),"historical vectors");
        require(r.stencilEnabled&&r.frontStencil.function==GL11.GL_EQUAL&&r.frontStencil.reference==3&&r.frontStencil.valueMask==0x35&&r.frontStencil.writeMask==0x53&&r.frontStencil.fail==GL11.GL_REPLACE&&r.frontStencil.depthFail==GL11.GL_INCR&&r.frontStencil.depthPass==GL11.GL_DECR,"front stencil");
        require(r.backStencil.function==GL11.GL_NOTEQUAL&&r.backStencil.reference==7&&r.backStencil.valueMask==0x27&&r.backStencil.writeMask==0x72&&r.backStencil.fail==GL11.GL_ZERO&&r.backStencil.depthFail==GL11.GL_INVERT&&r.backStencil.depthPass==GL11.GL_KEEP,"back stencil");
        require(r.cullEnabled&&r.cullFace==GL11.GL_FRONT&&r.frontFace==GL11.GL_CW&&r.polygonOffsetFill&&r.polygonOffsetFactor==1.25f&&r.polygonOffsetUnits==-2.5f,"historical cull/polygon");
    }
    private static void valueOnly(){
        float[] blend={0.25f,0.5f,0.75f,1},range={0.125f,0.875f};boolean[] mask={true,false,true,false};
        FontRenderContext.StencilFace s=new FontRenderContext.StencilFace(GL11.GL_ALWAYS,0,-1,-1,GL11.GL_KEEP,GL11.GL_KEEP,GL11.GL_KEEP);
        FontRenderContext.RasterState r=new FontRenderContext.RasterState(true,1,0,1,0,GL14.GL_FUNC_ADD,GL14.GL_FUNC_ADD,blend,false,s,s,mask,range,false,GL11.GL_BACK,GL11.GL_CCW,false,0,0);
        blend[0]=99;range[0]=99;mask[0]=false;require(r.blendColor()[0]==0.25f&&r.depthRange()[0]==0.125f&&r.colorMask()[0],"constructor borrowed mutable array");
        FontRenderContext old=new FontRenderContext(new club.heiqi.uilib.host.client.font.context.FontProjection(IDENTITY,IDENTITY,new int[]{0,0,64,64}),0,64,false,GL11.GL_LESS,true,false,new int[]{0,0,64,64},FontRenderContext.MatrixState.ui(IDENTITY,IDENTITY),new Object(),0,0);
        require(old.rasterState==null,"legacy constructor synthesized driver snapshot without context");
    }
    private static void effectiveProvider(){
        LwjglFontContextCapture.MatrixStateProvider effective=new LwjglFontContextCapture.MatrixStateProvider(){
            public GlApi gl(){return new Lwjgl3GlApi();}
            public FontRenderContext.MatrixState capture(){return FontRenderContext.MatrixState.ui(IDENTITY,IDENTITY);}
            public int[] integers(int name,int count){if(name==GL11.GL_VIEWPORT)return new int[]{1,2,30,40};if(name==GL11.GL_SCISSOR_BOX)return new int[]{4,6,8,10};return LwjglFontContextCapture.MatrixStateProvider.super.integers(name,count);}
            public int integer(int name){if(name==GL11.GL_DEPTH_FUNC)return GL11.GL_GEQUAL;return LwjglFontContextCapture.MatrixStateProvider.super.integer(name);}
            public boolean enabled(int name){if(name==GL11.GL_DEPTH_TEST||name==GL11.GL_SCISSOR_TEST)return true;return LwjglFontContextCapture.MatrixStateProvider.super.enabled(name);}
            public boolean[] booleans(int name,int count){if(name==GL11.GL_DEPTH_WRITEMASK)return new boolean[]{false};return LwjglFontContextCapture.MatrixStateProvider.super.booleans(name,count);}
        };
        FontRenderContext a=new LwjglFontContextCapture(new Lwjgl3GlApi(),effective).capture(64,7,11);
        require(a.depthTest&&!a.depthWrite&&a.depthFunction==GL11.GL_GEQUAL&&a.scissorEnabled&&Arrays.equals(a.scissor(),new int[]{4,6,8,10})&&Arrays.equals(a.projection.viewport(),new int[]{1,2,30,40}),"effective provider ignored for historical depth/viewport/scissor");
        require(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&GL11.glGetInteger(GL11.GL_DEPTH_FUNC)==GL11.GL_LESS,"provider wrote driver");
    }
    private void capture(GlfwTestEnvironment environment,boolean compatibility){
        own(environment.window(64,64,"font-deferred",compatibility?Profile.COMPATIBILITY:Profile.CORE,compatibility?3:2));
        int fboA=GL30.glGenFramebuffers(),fboB=GL30.glGenFramebuffers();
        onClose(()->{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,0);GL30.glDeleteFramebuffers(fboA);GL30.glDeleteFramebuffers(fboB);});
        {
            LwjglFontContextCapture capture=new LwjglFontContextCapture(new Lwjgl3GlApi(),compatibility?LwjglFontContextCapture.compatibility(new Lwjgl3GlApi()):LwjglFontContextCapture.explicit(new Lwjgl3GlApi(),FontRenderContext.MatrixState.ui(IDENTITY,IDENTITY)));
            if(compatibility){GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glDisable(GL11.GL_LIGHTING);}
            stateA(fboA);FontRenderContext a=capture.capture(64,5,9);verifyA(a,fboA);stateB(fboB);FontRenderContext b=capture.capture(64,6,10);
            FontRenderContext font=StandardFontContext.from(a);FontRenderContext.RasterState fr=font.rasterState,ar=a.rasterState;
            require(font.drawFramebuffer==a.drawFramebuffer&&font.depthTest==a.depthTest&&font.depthWrite==a.depthWrite&&font.depthFunction==a.depthFunction&&font.scissorEnabled==a.scissorEnabled&&Arrays.equals(font.scissor(),a.scissor())&&font.projection==a.projection&&font.contextIdentity==a.contextIdentity&&font.revision==a.revision&&font.passKey==a.passKey,"标准字体丢失target/depth/scissor身份");
            require(fr.stencilEnabled==ar.stencilEnabled&&fr.frontStencil==ar.frontStencil&&fr.backStencil==ar.backStencil&&Arrays.equals(fr.colorMask(),ar.colorMask())&&Arrays.equals(fr.depthRange(),ar.depthRange())&&Arrays.equals(fr.blendColor(),ar.blendColor())&&fr.cullEnabled==ar.cullEnabled&&fr.cullFace==ar.cullFace&&fr.frontFace==ar.frontFace&&fr.polygonOffsetFill==ar.polygonOffsetFill&&fr.polygonOffsetFactor==ar.polygonOffsetFactor&&fr.polygonOffsetUnits==ar.polygonOffsetUnits,"标准字体丢失nonblend历史限制");
            require(!b.rasterState.blendEnabled&&!b.depthTest&&b.depthWrite&&b.drawFramebuffer==fboB,"stateB captured");verifyA(a,fboA);
            a.rasterState.blendColor()[0]=99;a.rasterState.depthRange()[0]=99;a.rasterState.colorMask()[0]=false;a.scissor()[0]=99;a.projection.viewport()[0]=99;verifyA(a,fboA);
            require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==fboB&&!GL11.glIsEnabled(GL11.GL_BLEND),"capture changed driver B");
            effectiveProvider();
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"capture invalid legacy enum");
            System.out.println((compatibility?"compat":"core")+" capturedAImmutable=true driverBUnchanged=true stencilBoth=true blendColorMaskDepthRange=true GL="+GL11.glGetString(GL11.GL_VERSION));
        }
    }
    private static final int WIDTH=480,HEIGHT=96;
    private static int[] pixels(int fbo) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,fbo);
        ByteBuffer data=BufferUtils.createByteBuffer(WIDTH*HEIGHT*4);
        GL11.glReadPixels(0,0,WIDTH,HEIGHT,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
        int[] result=new int[WIDTH*HEIGHT*4];for(int i=0;i<result.length;i++)result[i]=data.get(i)&255;
        return result;
    }
    private static int grayPixels(int[] data) {
        int count=0;for(int i=0;i<data.length;i+=4)if(data[i]>0&&data[i]<255)count++;return count;
    }
    private static void image(int[] data,String name) throws Exception {
        BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){int i=(y*WIDTH+x)*4;image.setRGB(x,HEIGHT-1-y,data[i]<<16|data[i+1]<<8|data[i+2]);}
        File directory=new File("build/font-coverage-review");if(!directory.isDirectory()&&!directory.mkdirs())throw new AssertionError("输出目录创建失败");
        ImageIO.write(image,"png",new File(directory,name+".png"));
    }
    private static int[] render(ClientFontServices owner,GlPaintPlanRenderer renderer,PaintPlan plan,FontRenderContext context) throws Exception {
        return render(owner,renderer,plan,context,false);
    }
    private static int[] render(ClientFontServices owner,GlPaintPlanRenderer renderer,PaintPlan plan,FontRenderContext context,boolean opaque) throws Exception {
        long end=System.nanoTime()+20000000000L;
        for(int frame=0;;frame++) {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,context.drawFramebuffer);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);GL11.glColorMask(true,true,true,true);GL11.glClearColor(0,0,0,opaque?1:0);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            // 回放入口故意与捕获目标不同；scope必须恢复实际入口blend/viewport/FBO。
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,0);GL11.glViewport(2,4,30,40);
            GL11.glDisable(GL11.GL_BLEND);GL14.glBlendFuncSeparate(GL11.GL_ONE,GL11.GL_ZERO,GL11.GL_ONE,GL11.GL_ZERO);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_SUBTRACT,GL14.GL_FUNC_REVERSE_SUBTRACT);
            owner.beginPresentation(frame);boolean ready;
            try(GlPaintPlanRenderer.Prepared prepared=owner.prepare(renderer,plan,context)){ready=prepared.readyCount()==prepared.demandCount();prepared.render(context);}
            finally{owner.endPresentation();}
            require(!GL11.glIsEnabled(GL11.GL_BLEND)&&GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)==GL11.GL_ONE&&GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB)==GL14.GL_FUNC_SUBTRACT,"文字回放污染入口blend");
            int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
            require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==0&&Arrays.equals(viewport,new int[]{2,4,30,40}),"文字回放未恢复目标/viewport");
            if(ready)return pixels(context.drawFramebuffer);
            if(System.nanoTime()>end)throw new AssertionError("真实字形未就绪");Thread.sleep(2);
        }
    }
    private void coverage(GlfwTestEnvironment environment,boolean compatibility) throws Exception {
        own(environment.window(WIDTH,HEIGHT,"standard-font-coverage",compatibility?Profile.COMPATIBILITY:Profile.CORE,compatibility?3:2));
        Lwjgl3GlApi gl=new Lwjgl3GlApi();
        ClientFontServices owner=new ClientFontServices(getClass().getClassLoader(),1,ClientFontServices.Settings.defaults(),IsolatedFontEngine.shared(),gl,GlProgramProvider.NONE);
        onClose(()->{owner.close();require(owner.awaitRasterizerClose(10000),"worker关闭失败");});
        GlPaintPlanRenderer renderer=owner.sharedPlanRenderer();
        int texture=GL11.glGenTextures(),fbo=GL30.glGenFramebuffers();
        onClose(()->{GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);GL30.glDeleteFramebuffers(fbo);GL11.glDeleteTextures(texture);});
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,WIDTH,HEIGHT,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);
        require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)==GL30.GL_FRAMEBUFFER_COMPLETE,"coverage FBO不完整");
        GL11.glDisable(GL11.GL_DITHER);GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glColorMask(true,true,true,true);GL11.glViewport(0,0,WIDTH,HEIGHT);
        float[] projection=IDENTITY.clone();projection[0]=2f/WIDTH;projection[5]=-2f/HEIGHT;projection[12]=-1;projection[13]=1;
        FontRenderContext.MatrixState state=new FontRenderContext.MatrixState(projection,IDENTITY,new float[]{1,1,1,1},true,GL11.GL_GREATER,.1f,false,0,0,0,0,new float[]{0,0,1},new float[]{1,1,1},-1);
        if(compatibility){FloatBuffer matrix=BufferUtils.createFloatBuffer(16);matrix.put(projection).flip();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadMatrixf(matrix);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glColor4f(1,1,1,1);GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,.1f);}
        LwjglFontContextCapture capture=new LwjglFontContextCapture(gl,compatibility?LwjglFontContextCapture.compatibility(gl):LwjglFontContextCapture.explicit(gl,state));
        FontRenderContext raw=capture.capture(HEIGHT,1,0),standard=StandardFontContext.from(raw);
        require(!GL11.glIsEnabled(GL11.GL_BLEND),"标准快照写宿主driver");
        String[] args=arguments();FontSource source=args.length>0?FontSource.file("coverage-system",1,Paths.get(args[0]),0,false,Collections.<String>emptySet()):null;
        TextStyle style=new TextStyle("default",18,0xffffffff,source==null?null:Collections.singletonList(source));
        TextLayout text=owner.layout("Minecraft 1.7.10  微软雅黑 中文 ABC 123",style,WIDTH-20);
        if(source!=null)for(TextLayout.Line line:text.lines())for(TextLayout.Glyph glyph:line.glyphs())require(glyph.source.equals(source),"未命中指定系统字体");
        PaintPlan plan=new PaintPlanBuilder().add(PaintCommand.text(new LayoutBox(10,12,WIDTH-20,60),null,text)).build();
        int[] bad=render(owner,renderer,plan,raw);String profile=(compatibility?"compat":"core")+(source==null?"-packaged":"");image(bad,profile+"-bad");
        int badVisible=0,badPartialAlpha=0;for(int i=0;i<bad.length;i+=4)if(bad[i+3]>0){badVisible++;if(bad[i+3]<255)badPartialAlpha++;require(bad[i]==255&&bad[i+1]==255&&bad[i+2]==255,"no-blend未复现直色硬边");}
        require(badVisible>0&&badPartialAlpha>0&&grayPixels(bad)==0,"no-blend反例没有覆盖率");
        System.out.println(profile+" BAD_REAL_GPU visible="+badVisible+" partialAlpha="+badPartialAlpha+" grayRgb="+grayPixels(bad));
        int[] good=render(owner,renderer,plan,standard);image(good,profile+"-good");int goodGray=grayPixels(good);
        require(goodGray>0,"标准字体混合关闭导致覆盖率丢失");
        for(int i=0;i<good.length;i+=4)require(Math.abs(good[i]-good[i+3])<=1&&good[i]==good[i+1]&&good[i]==good[i+2],"coverage合成RGB/alpha不一致");
        int[] opaque=render(owner,renderer,plan,standard,true);image(opaque,profile+"-opaque");
        for(int i=0;i<opaque.length;i+=4)require(opaque[i]==good[i]&&opaque[i+3]==255,"不透明背景RGB coverage错误");
        require(grayPixels(opaque)==goodGray,"不透明背景灰度丢失");
        TextLayout translucent=owner.layout(text.input.text,style.withColor(0x80ffffff),WIDTH-20);
        PaintPlan half=new PaintPlanBuilder().add(PaintCommand.text(new LayoutBox(10,12,WIDTH-20,60),null,translucent)).build();
        int[] halfPixels=render(owner,renderer,half,standard);image(halfPixels,profile+"-half");int maximum=0;for(int i=0;i<halfPixels.length;i+=4){maximum=Math.max(maximum,halfPixels[i]);require(halfPixels[i]<=128&&Math.abs(halfPixels[i]-halfPixels[i+3])<=1,"半透明文字被双乘alpha或丢alpha");}
        require(maximum>=127&&grayPixels(halfPixels)>0,"半透明真实文字未绘制");
        require(GL11.glGetError()==GL11.GL_NO_ERROR,"coverage GL错误");
        System.out.println(profile+" GOOD_REAL_GPU grayRgb="+goodGray+" halfMaximum="+maximum+" restoredBlendDisabled=true source="+(source==null?"packaged":source.origin)+" renderer="+GL11.glGetString(GL11.GL_RENDERER)+" GL="+GL11.glGetString(GL11.GL_VERSION));
    }
    @Override protected void runTests() throws Exception {
        scenario("值对象复制与兼容构造", FontDeferredStateScenario::valueOnly);

        GlfwTestEnvironment environment=own(new GlfwTestEnvironment());
        scenario("core历史状态捕捉",()->capture(environment,false));
        scenario("compatibility历史状态捕捉",()->capture(environment,true));
        scenario("core标准字体真实coverage",()->coverage(environment,false));
        scenario("compatibility标准字体真实coverage",()->coverage(environment,true));
    }
    public static void main(String[] args) throws Exception { new FontDeferredStateScenario().run(args); }
}
