package club.heiqi.uilib.ui.scene.integration;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.DisplayTestEnvironment;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.font.FramebufferCoordinates;
import club.heiqi.uilib.host.client.gl.Lwjgl2GlApi;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.gl.GlImageResources;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.host.client.image.ItemStateScope;
import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImagePlacement;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 真实原 LWJGL2 compat：IMAGE 像素与独立 item state scope，不启动游戏、不冒称 Forge/Iris/glint 实测。 */
public final class ImageLwjgl2Scenario extends TestApplication {
    private static final int W=240,H=160;
    private final Lwjgl2GlApi gl=new Lwjgl2GlApi();private ClientFontServices fonts;private GlPaintPlanRenderer renderer;private int fbo;private int[] pixels;
    private static ImageContent solid(int rgba){return new ImageContent(2,1,new byte[]{(byte)(rgba>>>24),(byte)(rgba>>>16),(byte)(rgba>>>8),(byte)rgba,(byte)(rgba>>>24),(byte)(rgba>>>16),(byte)(rgba>>>8),(byte)rgba});}
    private static FloatBuffer floats(){return ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();}
    private static float[] value(int name){FloatBuffer b=floats();glGetFloat(name,b);float[] result=new float[16];for(int i=0;i<result.length;i++)result[i]=b.get(i);return result;}
    private PaintCommand image(ImageContent content,LayoutBox box,ImageOptions.Fit fit,LayoutBox clip){return PaintCommand.image(box,clip,ImagePlacement.fit(content,box,ImageOptions.defaults().fit(fit)));}
    private void capture(){ByteBuffer b=ByteBuffer.allocateDirect(W*H*4);glReadPixels(0,0,W,H,GL_RGBA,GL_UNSIGNED_BYTE,b);pixels=new int[W*H];for(int y=0;y<H;y++)for(int x=0;x<W;x++){int i=((H-1-y)*W+x)*4;pixels[y*W+x]=((b.get(i+3)&255)<<24)|((b.get(i)&255)<<16)|((b.get(i+1)&255)<<8)|(b.get(i+2)&255);}}
    private void color(int x,int y,int expected){int actual=pixels[y*W+x];for(int shift:new int[]{0,8,16,24})require(Math.abs(((actual>>>shift)&255)-((expected>>>shift)&255))<=2,"LWJGL2 pixel="+x+","+y+" actual="+Integer.toHexString(actual)+" expected="+Integer.toHexString(expected));}
    private boolean render(PaintPlan plan){glDisable(GL_SCISSOR_TEST);glDisable(GL_LIGHTING);glDisable(GL_ALPHA_TEST);glClearColor(1,1,1,1);glClear(GL_COLOR_BUFFER_BIT);boolean ready;try(GlPaintPlanRenderer.Prepared p=fonts.prepare(renderer,plan,new LayoutBox(0,0,W,H))){p.render(FramebufferCoordinates.orthographic(W,H),H,false);ready=p.readyCount()==p.demandCount();}capture();return ready;}
    @Override protected void setup()throws Exception{own(new DisplayTestEnvironment(W,H,"UILib LWJGL2 image and scope"));fonts=own(new ClientFontServices(getClass().getClassLoader(),1,ClientFontServices.Settings.defaults(),IsolatedFontEngine.shared(),gl,GlProgramProvider.NONE));GlImageResources images=own(new GlImageResources(gl,16,1024*1024));renderer=fonts.createPlanRenderer(images);onClose(()->fonts.releasePlanRenderer(renderer));int texture=glGenTextures();fbo=glGenFramebuffers();onClose(()->{glBindFramebuffer(GL_FRAMEBUFFER,0);glDeleteFramebuffers(fbo);glDeleteTextures(texture);});glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,W,H,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);glBindFramebuffer(GL_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);require(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE,"FBO");glViewport(0,0,W,H);System.out.println("LWJGL2 actual IMAGE vendor="+glGetString(GL_VENDOR)+" renderer="+glGetString(GL_RENDERER)+" version="+glGetString(GL_VERSION));}
    @Override protected void runTests()throws Exception{
        scenario("actual IMAGE透明rect原序及后续实际glyph",()->{TextLayout text=fonts.layout("IMAGE 后的 Text",new TextStyle("default",18,0xff101010),W);PaintPlanBuilder b=new PaintPlanBuilder();b.add(PaintCommand.rect(new LayoutBox(10,10,80,60),null,0xff0000ff));b.add(image(solid(0xff000080),new LayoutBox(10,10,80,60),ImageOptions.Fit.STRETCH,null));b.add(PaintCommand.rect(new LayoutBox(30,30,20,20),null,0xff00ff00));b.add(PaintCommand.text(new LayoutBox(10,90,220,50),null,text));for(int i=0;i<100;i++){if(render(b.build()))break;Thread.sleep(5);}color(20,20,0xff80007f);color(40,40,0xff00ff00);int dark=0;for(int y=90;y<140;y++)for(int x=10;x<230;x++)if((pixels[y*W+x]&0xffffff)<0x606060)dark++;require(dark>20,"IMAGE之后glyph缺失");});
        scenario("actual IMAGE contain cover native有效ancestorclip",()->{ImageContent red=solid(0xff0000ff);PaintPlanBuilder b=new PaintPlanBuilder();for(int i=0;i<3;i++){LayoutBox box=new LayoutBox(10+i*70,10,60,60);b.add(image(red,box,new ImageOptions.Fit[]{ImageOptions.Fit.CONTAIN,ImageOptions.Fit.COVER,ImageOptions.Fit.NATIVE}[i],box));}render(b.build());color(15,15,0xffffffff);color(15,30,0xffff0000);color(85,15,0xffff0000);color(75,15,0xffffffff);color(179,40,0xffff0000);color(155,15,0xffffffff);});
        scenario("compat scope normal状态逐项对照",()->scope(false));
        scenario("compat scope异常漏matrix与attrib push状态逐项对照",()->scope(true));
        require(glGetError()==GL_NO_ERROR,"LWJGL2 IMAGE/scope留下GLerror");
    }
    private static final int[] INTEGER={GL_MATRIX_MODE,GL_PROJECTION_STACK_DEPTH,GL_MODELVIEW_STACK_DEPTH,GL_ATTRIB_STACK_DEPTH,GL_CLIENT_ATTRIB_STACK_DEPTH,GL_ACTIVE_TEXTURE,GL_CURRENT_PROGRAM,GL_VERTEX_ARRAY_BINDING,GL_ARRAY_BUFFER_BINDING,GL_ELEMENT_ARRAY_BUFFER_BINDING,GL_DRAW_FRAMEBUFFER_BINDING,GL_READ_FRAMEBUFFER_BINDING,GL_DEPTH_FUNC,GL_BLEND_SRC_RGB,GL_BLEND_DST_RGB,GL_ALPHA_TEST_FUNC};
    private int[] integers(){int[] out=new int[INTEGER.length];for(int i=0;i<out.length;i++)out[i]=glGetInteger(INTEGER[i]);return out;}
    private void checkError(String stage){int error=glGetError();require(error==GL_NO_ERROR,"scope "+stage+" GLerror="+error);}
    private void scope(boolean fail){
        checkError("entry");
        int vao=glGenVertexArrays(),vbo=glGenBuffers(),ebo=glGenBuffers(),tex0=glGenTextures(),tex1=glGenTextures(),sampler=glGenSamplers();
        glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,ebo);glViewport(3,4,W-8,H-9);glScissor(5,6,70,80);glEnable(GL_SCISSOR_TEST);glEnable(GL_LIGHTING);glEnable(GL_LIGHT0);glEnable(GL_ALPHA_TEST);glAlphaFunc(GL_GREATER,.4f);glEnable(GL_DEPTH_TEST);glDepthFunc(GL_LEQUAL);glDepthMask(false);glEnable(GL_BLEND);glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ZERO);glColor4f(.2f,.3f,.4f,.5f);glNormal3f(.3f,.4f,.5f);glMaterialf(GL_FRONT,GL_SHININESS,17);
        glMatrixMode(GL_PROJECTION);glLoadIdentity();glTranslatef(.1f,.2f,.3f);glPushMatrix();glMatrixMode(GL_MODELVIEW);glLoadIdentity();glScalef(2,3,4);glPushMatrix();
        for(int unit=0;unit<2;unit++){glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,unit==0?tex0:tex1);glBindSampler(unit,sampler);glMatrixMode(GL_TEXTURE);glLoadIdentity();glTranslatef(unit+.1f,.2f,.3f);glPushMatrix();}
        glActiveTexture(GL_TEXTURE1);glMatrixMode(GL_TEXTURE);int[] before=integers();float[] projection=value(GL_PROJECTION_MATRIX),model=value(GL_MODELVIEW_MATRIX),normal=value(GL_CURRENT_NORMAL),color=value(GL_CURRENT_COLOR),textureMatrix=value(GL_TEXTURE_MATRIX);int[] viewport=new int[4],scissor=new int[4];gl.glGetIntegerv(GL_VIEWPORT,viewport);gl.glGetIntegerv(GL_SCISSOR_BOX,scissor);
        checkError("preflight");
        try{try(ItemStateScope state=new ItemStateScope(gl)){checkError("capture");state.place(ImagePlacement.fit(solid(0xff0000ff),new LayoutBox(10,20,80,60),ImageOptions.defaults()),new LayoutBox(10,20,40,30),W,H);checkError("place");glDisable(GL_LIGHTING);glDisable(GL_LIGHT0);glDisable(GL_ALPHA_TEST);glAlphaFunc(GL_ALWAYS,.1f);glDisable(GL_DEPTH_TEST);glDepthFunc(GL_ALWAYS);glDepthMask(true);glDisable(GL_BLEND);glBlendFunc(GL_ONE,GL_ZERO);glColor4f(1,0,0,1);glNormal3f(0,0,-1);glMaterialf(GL_FRONT,GL_SHININESS,2);glBindFramebuffer(GL_FRAMEBUFFER,0);glBindBuffer(GL_ARRAY_BUFFER,0);glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,0);for(int unit=0;unit<2;unit++){glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,0);glBindSampler(unit,0);glMatrixMode(GL_TEXTURE);glLoadIdentity();if(fail)glPushMatrix();}glMatrixMode(GL_PROJECTION);glLoadIdentity();if(fail)glPushMatrix();glMatrixMode(GL_MODELVIEW);glLoadIdentity();if(fail){glPushMatrix();glPushAttrib(GL_ALL_ATTRIB_BITS);glPushClientAttrib(-1);throw new IllegalStateException("probe");}checkError("body");}}catch(IllegalStateException error){if(!fail||!"probe".equals(error.getMessage()))throw error;}
        checkError("restored");
        require(Arrays.equals(before,integers()),"scope integer bindings/depth/mode/alpha/blend不一致");require(Arrays.equals(projection,value(GL_PROJECTION_MATRIX))&&Arrays.equals(model,value(GL_MODELVIEW_MATRIX))&&Arrays.equals(textureMatrix,value(GL_TEXTURE_MATRIX)),"scope矩阵未恢复");require(Arrays.equals(normal,value(GL_CURRENT_NORMAL))&&Arrays.equals(color,value(GL_CURRENT_COLOR)),"scope normal/color未恢复");int[] after=new int[4];gl.glGetIntegerv(GL_VIEWPORT,after);require(Arrays.equals(viewport,after),"scope viewport");gl.glGetIntegerv(GL_SCISSOR_BOX,after);require(Arrays.equals(scissor,after)&&glIsEnabled(GL_SCISSOR_TEST),"scope scissor");require(glIsEnabled(GL_LIGHTING)&&glIsEnabled(GL_LIGHT0)&&glIsEnabled(GL_ALPHA_TEST)&&glIsEnabled(GL_DEPTH_TEST)&&glIsEnabled(GL_BLEND)&&!glGetBoolean(GL_DEPTH_WRITEMASK),"scope enables/depthmask");require(glGetFloat(GL_ALPHA_TEST_REF)==.4f,"scope alpha ref");FloatBuffer mat=floats();glGetMaterial(GL_FRONT,GL_SHININESS,mat);require(mat.get(0)==17,"scope material");for(int unit=0;unit<2;unit++){glActiveTexture(GL_TEXTURE0+unit);require(glGetInteger(GL_TEXTURE_BINDING_2D)==(unit==0?tex0:tex1)&&glGetInteger(GL_SAMPLER_BINDING)==sampler,"scope unit="+unit+" texture="+glGetInteger(GL_TEXTURE_BINDING_2D)+" expectedTexture="+(unit==0?tex0:tex1)+" sampler="+glGetInteger(GL_SAMPLER_BINDING)+" expectedSampler="+sampler);glMatrixMode(GL_TEXTURE);glPopMatrix();}glMatrixMode(GL_PROJECTION);glPopMatrix();glMatrixMode(GL_MODELVIEW);glPopMatrix();glActiveTexture(GL_TEXTURE0);glBindSampler(0,0);glBindSampler(1,0);glBindVertexArray(0);glBindBuffer(GL_ARRAY_BUFFER,0);glDeleteVertexArrays(vao);glDeleteBuffers(vbo);glDeleteBuffers(ebo);glDeleteTextures(tex0);glDeleteTextures(tex1);glDeleteSamplers(sampler);glDisable(GL_LIGHTING);glDisable(GL_ALPHA_TEST);glDisable(GL_DEPTH_TEST);glDisable(GL_SCISSOR_TEST);glDepthMask(true);glViewport(0,0,W,H);checkError("cleanup");
    }
    public static void main(String[] args)throws Exception{new ImageLwjgl2Scenario().run(args);}
}
