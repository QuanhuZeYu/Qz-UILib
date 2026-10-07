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
    public interface MatrixStateProvider {
        MatrixState capture();
        default boolean depthWrite() { return GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK); }
    }
    private final MatrixStateProvider provider;
    public LwjglFontContextCapture(MatrixStateProvider provider) { this.provider=Objects.requireNonNull(provider,"provider"); }
    public FontRenderContext capture(int sourceFramebufferHeight,long revision,int passKey) {
        if(sourceFramebufferHeight<=0)throw new IllegalArgumentException("须提供当前target的framebuffer像素高度");
        Object identity=GL.getCapabilities();
        MatrixState state=Objects.requireNonNull(provider.capture(),"provider返回null");
        IntBuffer viewport=BufferUtils.createIntBuffer(4),scissor=BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,scissor);
        int[] v=new int[4],s=new int[4];for(int i=0;i<4;i++){v[i]=viewport.get(i);s[i]=scissor.get(i);}
        if(!GL.getCapabilities().OpenGL30)throw new IllegalStateException("capture要求OpenGL3实际draw framebuffer，旧context须显式target adapter");
        int fbo=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        return new FontRenderContext(new FontProjection(state.projection(),state.modelView(),v),fbo,sourceFramebufferHeight,
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),GL11.glGetInteger(GL11.GL_DEPTH_FUNC),provider.depthWrite(),
                GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),s,state,identity,revision,passKey);
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
