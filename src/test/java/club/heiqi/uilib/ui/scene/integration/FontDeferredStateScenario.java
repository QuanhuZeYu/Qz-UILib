package club.heiqi.uilib.ui.scene.integration;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.GlfwTestEnvironment;
import club.heiqi.uilib.testing.GlfwTestEnvironment.Profile;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.Lwjgl3GlApi;

import java.util.Arrays;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.host.client.font.context.LwjglFontContextCapture;

/** 延期producer真实GL捕捉A，再把宿主切到B：只验证历史载荷，不替代backend apply/restore或Angelica初始化。 */
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
            require(!b.rasterState.blendEnabled&&!b.depthTest&&b.depthWrite&&b.drawFramebuffer==fboB,"stateB captured");verifyA(a,fboA);
            a.rasterState.blendColor()[0]=99;a.rasterState.depthRange()[0]=99;a.rasterState.colorMask()[0]=false;a.scissor()[0]=99;a.projection.viewport()[0]=99;verifyA(a,fboA);
            require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)==fboB&&!GL11.glIsEnabled(GL11.GL_BLEND),"capture changed driver B");
            effectiveProvider();
            require(GL11.glGetError()==GL11.GL_NO_ERROR,"capture invalid legacy enum");
            System.out.println((compatibility?"compat":"core")+" capturedAImmutable=true driverBUnchanged=true stencilBoth=true blendColorMaskDepthRange=true GL="+GL11.glGetString(GL11.GL_VERSION));
        }
    }
    @Override protected void runTests() throws Exception {
        scenario("值对象复制与兼容构造", FontDeferredStateScenario::valueOnly);

        GlfwTestEnvironment environment=own(new GlfwTestEnvironment());
        scenario("core历史状态捕捉",()->capture(environment,false));
        scenario("compatibility历史状态捕捉",()->capture(environment,true));
    }
    public static void main(String[] args) throws Exception { new FontDeferredStateScenario().run(args); }
}
