package club.heiqi.uilib.ui.scene.integration;
import club.heiqi.uilib.host.client.gl.Lwjgl3GlApi;

import java.nio.FloatBuffer;
import java.util.HashSet;
import java.util.Set;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import club.heiqi.uilib.host.client.font.context.AngelicaMatrixStateProvider;
import club.heiqi.uilib.host.client.font.context.FontProjection;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.host.client.font.context.LwjglFontContextCapture;
import club.heiqi.uilib.host.client.font.context.RasterPolicy;
import club.heiqi.uilib.host.client.font.context.FontRenderContext.MatrixState;

/** CPU数学与真实hidden GL context捕捉；不替代文字world producer或Angelica/Iris实机验收。 */
public final class FontProjectionScenario {
    static float[] identity(){float[] m=new float[16];m[0]=m[5]=m[10]=m[15]=1;return m;}
    static float[] ortho(){float[] m=identity();m[0]=2f/800;m[5]=2f/600;return m;}
    static float[] perspective(){float[] m=new float[16];m[0]=1.5f;m[5]=2;m[10]=-1.02f;m[11]=-1;m[14]=-.202f;return m;}
    static float[] atDepth(float depth){float[] m=identity();m[14]=-depth;return m;}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void near(double actual,double expected,double tolerance){check(Math.abs(actual-expected)<=tolerance,"actual="+actual+" expected="+expected);}
    static void reject(Runnable action){try{action.run();}catch(IllegalArgumentException|IllegalStateException expected){return;}throw new AssertionError("应明确拒绝无效状态");}
    static void cpu(){
        float[] p=ortho(),mv=identity();int[] v={10,20,800,600};FontProjection projection=new FontProjection(p,mv,v);
        p[0]=99;mv[0]=99;v[2]=1;projection.mvp()[0]=0;projection.viewport()[2]=1;near(projection.pixelsPerUnitAt(0,0,0),1,1e-6);
        float[] shear=identity();shear[4]=1;FontProjection s=new FontProjection(ortho(),shear,new int[]{0,0,800,600});near(s.pixelsPerUnitAt(0,0,0),(1+Math.sqrt(5))/2,1e-6);
        float[] rotated=identity();rotated[0]=0;rotated[1]=2;rotated[4]=-3;rotated[5]=0;near(new FontProjection(ortho(),rotated,new int[]{0,0,800,600}).pixelsPerUnitAt(0,0,0),3,1e-6);
        FontProjection nearView=new FontProjection(perspective(),atDepth(2),new int[]{0,0,800,600}),farView=new FontProjection(perspective(),atDepth(8),new int[]{0,0,800,600});near(nearView.pixelsPerUnitAt(0,0,0),300,1e-5);near(farView.pixelsPerUnitAt(0,0,0),75,1e-5);
        near(nearView.clipAt(0,0,0)[3],2,0);check(nearView.clipAt(0,0,0)[2]!=0,"depth保留");
        reject(()->new FontProjection(perspective(),identity(),new int[]{0,0,800,600}).pixelsPerUnitAt(0,0,0));
        reject(()->new FontProjection(perspective(),atDepth(-2),new int[]{0,0,800,600}).pixelsPerUnitAt(0,0,0));
        RasterPolicy policy=new RasterPolicy(16,32,64);FontProjection.SamplingPlan cap=nearView.samplingPlanAt(0,0,0,16,policy,7);check(cap.capped&&cap.actualRasterEm==64,"cap必须可观察");
        Set<Integer> sizes=new HashSet<>();for(int i=0;i<10000;i++){FontProjection world=new FontProjection(perspective(),atDepth(2+i*.01f),new int[]{0,0,800,600});sizes.add(world.samplingPlanAt(0,0,0,1,policy,7).actualRasterEm);}check(sizes.size()<=policy.sizes().length,"camera变化不能无限size keys");
        reject(()->new RasterPolicy(32,16));reject(()->new FontProjection(identity(),identity(),new int[]{0,0,0,1}));
        System.out.println("CPU_PASS copy shear rotation perspective clipw cap boundedCameraBuckets="+sizes.size());
    }
    static void gl(boolean core){
        GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,core?GLFW.GLFW_OPENGL_CORE_PROFILE:GLFW.GLFW_OPENGL_COMPAT_PROFILE);
        long window=GLFW.glfwCreateWindow(800,600,"font-context",0,0);check(window!=0,"hidden context创建失败");
        try{
            GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();GL11.glViewport(10,20,800,600);GL11.glScissor(1,2,100,200);GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);
            int fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);
            try{
                FontRenderContext context;
                if(core){MatrixState state=MatrixState.ui(perspective(),atDepth(2));context=new LwjglFontContextCapture(new Lwjgl3GlApi(),LwjglFontContextCapture.explicit(new Lwjgl3GlApi(),state)).capture(768,9,1);reject(()->LwjglFontContextCapture.compatibility(new Lwjgl3GlApi()).capture());}
                else{FloatBuffer p=BufferUtils.createFloatBuffer(16),mv=BufferUtils.createFloatBuffer(16);p.put(perspective()).flip();mv.put(atDepth(2)).flip();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadMatrixf(p);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadMatrixf(mv);GL11.glColor4f(.2f,.3f,.4f,.5f);GL11.glEnable(GL11.GL_ALPHA_TEST);GL11.glAlphaFunc(GL11.GL_GREATER,.2f);
                    context=new LwjglFontContextCapture(new Lwjgl3GlApi(),AngelicaMatrixStateProvider.optional(FontProjectionScenario.class.getClassLoader(),new Lwjgl3GlApi())).capture(768,9,1);GL11.glLoadIdentity();GL11.glColor4f(1,1,1,1);near(context.state.color()[3],.5,1e-6);check(context.state.alphaTest,"compatalpha");}
                GL11.glViewport(0,0,1,1);GL11.glDepthMask(true);check(context.drawFramebuffer==fbo&&!context.depthWrite&&context.scissorEnabled,"target state捕捉");check(context.sourceFramebufferHeight==768&&context.projection.viewport()[2]==800,"target高度非viewport猜测");near(context.projection.pixelsPerUnitAt(0,0,0),300,1e-5);near(context.projection.clipAt(0,0,0)[3],2,0);
                check(GL11.glGetError()==GL11.GL_NO_ERROR,"capture不应产生legacy invalidenum");System.out.println((core?"CORE":"COMPAT")+"_PASS "+GL11.glGetString(GL11.GL_VERSION)+" immutableCapture fbo="+fbo+" density=300");
            }finally{GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,0);GL30.glDeleteFramebuffers(fbo);}
        }finally{GL.setCapabilities(null);GLFW.glfwMakeContextCurrent(0);GLFW.glfwDestroyWindow(window);}
    }
    public static void main(String[] args){cpu();if(args.length>0&&args[0].equals("cpu"))return;check(GLFW.glfwInit(),"GLFW init");try{gl(true);gl(false);}finally{GLFW.glfwTerminate();}}
}
