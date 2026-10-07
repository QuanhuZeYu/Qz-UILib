package club.heiqi.uilib.host.client.gl;

import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL32C.*;

/** 短world范围的actual GL target/depth/lightmap保护；不改变GLSM有效缓存或固定矩阵。
 * typed RasterState恢复捕获的历史状态；null旧value合同保留回放现场实际stencil/color mask。
 * lightmap UV已由有效provider乘unit1 texture matrix，不能再乘一次。 */
final class GlFontWorldState implements AutoCloseable {
    final FontRenderContext context;
    private final FontRenderContext.RasterState entryRaster=GlFontRasterState.capture();
    private final int fbo=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),depthFunction=glGetInteger(GL_DEPTH_FUNC),unit=glGetInteger(GL_ACTIVE_TEXTURE);
    private final boolean depthWrite=glGetBoolean(GL_DEPTH_WRITEMASK);
    private final int[] viewport=new int[4];
    private final int lightmapTexture,sampler;
    private final boolean samplers=GL.getCapabilities().OpenGL33||GL.getCapabilities().GL_ARB_sampler_objects;
    GlFontWorldState(FontRenderContext context) {
        if(context.contextIdentity!=GL.getCapabilities())throw new IllegalStateException("字体context身份失配，禁止跨context回放");
        this.context=context;glGetIntegerv(GL_VIEWPORT,viewport);
        glActiveTexture(GL_TEXTURE1);lightmapTexture=glGetInteger(GL_TEXTURE_BINDING_2D);
        sampler=samplers?glGetInteger(org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING):0;
        glActiveTexture(unit);
    }
    void applyIris(){
        // Iris Pass.use拥有目标与blend overrides；viewport/depth/scissor仍必须来自caller提交快照。
        if(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)!=context.drawFramebuffer)throw new IllegalStateException("Iris Pass.use目标与捕获font target不一致");
        if(context.rasterState!=null)GlFontRasterState.applyNonBlend(context.rasterState);
        int[] v=context.projection.viewport();glViewport(v[0],v[1],v[2],v[3]);glDepthFunc(context.depthFunction);glDepthMask(context.depthWrite);
        if(context.depthTest)glEnable(GL_DEPTH_TEST);else glDisable(GL_DEPTH_TEST);
        int[] s=context.scissor();glScissor(s[0],s[1],s[2],s[3]);if(context.scissorEnabled)glEnable(GL_SCISSOR_TEST);else glDisable(GL_SCISSOR_TEST);
    }
    void apply(){
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,context.drawFramebuffer);int[] v=context.projection.viewport();glViewport(v[0],v[1],v[2],v[3]);
        glDepthFunc(context.depthFunction);glDepthMask(context.depthWrite);
        if(context.rasterState!=null)GlFontRasterState.apply(context.rasterState);
        if(context.state.lightmapActive){glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,context.state.lightmapTexture);
            if(samplers)org.lwjgl.opengl.GL33C.glBindSampler(1,0);glActiveTexture(GL_TEXTURE0);}
    }
    void uniforms(int program){
        float[] lighting=context.state.lighting(),color=context.state.color();
        glUniform3fv(glGetUniformLocation(program,"hostLighting"),lighting);
        // resolved文字RGB已由adapter固化，不再次乘caller currentColor；emoji同样只受整体alpha与lighting影响。
        glUniform1f(glGetUniformLocation(program,"hostAlpha"),color[3]);
        glUniform1i(glGetUniformLocation(program,"outputStraight"),context.rasterState!=null?1:0);
        glUniform1i(glGetUniformLocation(program,"alphaEnabled"),context.state.alphaTest?1:0);
        glUniform1i(glGetUniformLocation(program,"alphaFunction"),context.state.alphaFunction);
        glUniform1f(glGetUniformLocation(program,"alphaReference"),context.state.alphaReference);
        glUniform1i(glGetUniformLocation(program,"lightmapEnabled"),context.state.lightmapActive?1:0);
        glUniform1i(glGetUniformLocation(program,"lightmapImage"),1);
        glUniform2f(glGetUniformLocation(program,"lightmapUv"),context.state.lightmapU,context.state.lightmapV);
    }
    static void uiUniforms(int program){
        glUniform3f(glGetUniformLocation(program,"hostLighting"),1,1,1);glUniform1f(glGetUniformLocation(program,"hostAlpha"),1);
        glUniform1i(glGetUniformLocation(program,"outputStraight"),0);glUniform1i(glGetUniformLocation(program,"alphaEnabled"),0);glUniform1i(glGetUniformLocation(program,"lightmapEnabled"),0);
        glUniform1i(glGetUniformLocation(program,"lightmapImage"),1);
    }
    @Override public void close(){
        GlFontRasterState.apply(entryRaster);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,fbo);glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);glDepthFunc(depthFunction);glDepthMask(depthWrite);
        glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,lightmapTexture);if(samplers)org.lwjgl.opengl.GL33C.glBindSampler(1,sampler);glActiveTexture(unit);
    }
}
