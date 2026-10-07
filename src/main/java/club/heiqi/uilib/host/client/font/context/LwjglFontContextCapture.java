package club.heiqi.uilib.host.client.font.context;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Objects;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import club.heiqi.uilib.host.client.font.context.FontRenderContext.MatrixState;

/** 捕捉调用线程当前LWJGL context的实际target/viewport/depth/scissor。
 * 固定管线矩阵必须由adapter声明来源；core profile不允许猜测driver legacy矩阵。
 * 此类不切FBO、不画文字，也不是可恢复所有GL状态的guard。延期回放另须保序和producer合同。 */
public final class LwjglFontContextCapture {
    /** 可由GLSM覆盖；只查询合法GL3光栅枚举，不碰legacy矩阵/alpha/light。 */
    public interface RasterQueries {
        default int integer(int name){return GL11.glGetInteger(name);}
        default int[] integers(int name,int count){IntBuffer out=BufferUtils.createIntBuffer(count);GL11.glGetIntegerv(name,out);int[] a=new int[count];for(int i=0;i<count;i++)a[i]=out.get(i);return a;}
        default boolean enabled(int name){return GL11.glIsEnabled(name);}
        default boolean[] booleans(int name,int count){java.nio.ByteBuffer out=BufferUtils.createByteBuffer(count);GL11.glGetBooleanv(name,out);boolean[] a=new boolean[count];for(int i=0;i<count;i++)a[i]=out.get(i)!=0;return a;}
        default float[] floats(int name,int count){return read(name,count);}
    }
    public static FontRenderContext.RasterState driverCapture(){return captureRaster(new RasterQueries(){});}
    static FontRenderContext.RasterState captureRaster(RasterQueries q){
        return new FontRenderContext.RasterState(q.enabled(GL11.GL_BLEND),q.integer(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB),q.integer(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB),
            q.integer(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA),q.integer(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA),q.integer(org.lwjgl.opengl.GL20.GL_BLEND_EQUATION_RGB),q.integer(org.lwjgl.opengl.GL20.GL_BLEND_EQUATION_ALPHA),
            q.floats(org.lwjgl.opengl.GL14.GL_BLEND_COLOR,4),q.enabled(GL11.GL_STENCIL_TEST),stencil(q,false),stencil(q,true),q.booleans(GL11.GL_COLOR_WRITEMASK,4),
            q.floats(GL11.GL_DEPTH_RANGE,2),q.enabled(GL11.GL_CULL_FACE),q.integer(GL11.GL_CULL_FACE_MODE),q.integer(GL11.GL_FRONT_FACE),q.enabled(GL11.GL_POLYGON_OFFSET_FILL),
            q.floats(GL11.GL_POLYGON_OFFSET_FACTOR,1)[0],q.floats(GL11.GL_POLYGON_OFFSET_UNITS,1)[0]);
    }
    private static FontRenderContext.StencilFace stencil(RasterQueries q,boolean back){return new FontRenderContext.StencilFace(
        q.integer(back?org.lwjgl.opengl.GL20.GL_STENCIL_BACK_FUNC:GL11.GL_STENCIL_FUNC),q.integer(back?org.lwjgl.opengl.GL20.GL_STENCIL_BACK_REF:GL11.GL_STENCIL_REF),
        q.integer(back?org.lwjgl.opengl.GL20.GL_STENCIL_BACK_VALUE_MASK:GL11.GL_STENCIL_VALUE_MASK),q.integer(back?org.lwjgl.opengl.GL20.GL_STENCIL_BACK_WRITEMASK:GL11.GL_STENCIL_WRITEMASK),
        q.integer(back?org.lwjgl.opengl.GL20.GL_STENCIL_BACK_FAIL:GL11.GL_STENCIL_FAIL),q.integer(back?org.lwjgl.opengl.GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL:GL11.GL_STENCIL_PASS_DEPTH_FAIL),
        q.integer(back?org.lwjgl.opengl.GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS:GL11.GL_STENCIL_PASS_DEPTH_PASS));}
    public interface MatrixStateProvider extends RasterQueries {
        default FontRenderContext.RasterState rasterState(){return captureRaster(this);}
        MatrixState capture();
        default boolean depthWrite() { return booleans(GL11.GL_DEPTH_WRITEMASK,1)[0]; }
    }
    private final MatrixStateProvider provider;
    public LwjglFontContextCapture(MatrixStateProvider provider) { this.provider=Objects.requireNonNull(provider,"provider"); }
    public FontRenderContext capture(int sourceFramebufferHeight,long revision,int passKey) {
        if(sourceFramebufferHeight<=0)throw new IllegalArgumentException("须提供当前target的framebuffer像素高度");
        Object identity=GL.getCapabilities();
        MatrixState state=Objects.requireNonNull(provider.capture(),"provider返回null");
        int[] v=provider.integers(GL11.GL_VIEWPORT,4),s=provider.integers(GL11.GL_SCISSOR_BOX,4);
        FontRenderContext.RasterState raster=Objects.requireNonNull(provider.rasterState(),"provider rasterState");
        if(!GL.getCapabilities().OpenGL30)throw new IllegalStateException("capture要求OpenGL3实际draw framebuffer，旧context须显式target adapter");
        int fbo=provider.integer(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        return new FontRenderContext(new FontProjection(state.projection(),state.modelView(),v),fbo,sourceFramebufferHeight,
                provider.enabled(GL11.GL_DEPTH_TEST),provider.integer(GL11.GL_DEPTH_FUNC),provider.depthWrite(),
                provider.enabled(GL11.GL_SCISSOR_TEST),s,state,identity,revision,passKey,raster);
    }
    /** core/own scene由其根空间提供真实P/MV及状态，无legacy查询。 */
    public static MatrixStateProvider explicit(MatrixState state) { Objects.requireNonNull(state,"state");return ()->state; }
    /** 只有合法compatibility context可调用；无宿主协议时不猜lightmap/blockEntity/FFP灯光。 */
    public static MatrixStateProvider compatibility() { return ()->{
        requireCompatibility();
        if(GL11.glIsEnabled(GL11.GL_LIGHTING))throw new IllegalStateException("driver FFP lighting须由显式world provider提供，不可用UI默认lighting假代替");
        return new MatrixState(read(GL11.GL_PROJECTION_MATRIX,16),read(GL11.GL_MODELVIEW_MATRIX,16),read(GL11.GL_CURRENT_COLOR,4),
                GL11.glIsEnabled(GL11.GL_ALPHA_TEST),GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC),GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF),
                false,0,0,0,0,read(GL11.GL_CURRENT_NORMAL,3),new float[]{1,1,1},-1);
    }; }
    private static void requireCompatibility() {
        if(GL.getCapabilities().OpenGL32 && (GL11.glGetInteger(GL32.GL_CONTEXT_PROFILE_MASK)&GL32.GL_CONTEXT_CORE_PROFILE_BIT)!=0)
            throw new IllegalStateException("core context必须提供显式或宿主有效矩阵provider，禁止legacy glGetFloat");
        if(GL.getCapabilities().OpenGL30 && (GL11.glGetInteger(GL30.GL_CONTEXT_FLAGS)&GL30.GL_CONTEXT_FLAG_FORWARD_COMPATIBLE_BIT)!=0)
            throw new IllegalStateException("forward-compatible context禁止固定管线矩阵读取");
    }
    private static float[] read(int name,int size) {FloatBuffer buffer=BufferUtils.createFloatBuffer(size);GL11.glGetFloatv(name,buffer);float[] values=new float[size];for(int i=0;i<size;i++)values[i]=buffer.get(i);return values;}
}
