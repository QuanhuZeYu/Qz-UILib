package club.heiqi.uilib.host.client.gl;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import club.heiqi.uilib.host.client.font.context.FontRenderContext.RasterState;
import club.heiqi.uilib.host.client.font.context.FontRenderContext.StencilFace;
import static org.lwjgl.opengl.GL32C.*;

/** world历史光栅载荷只短时应用；退出恢复actual进入态，不写宿主有效缓存。 */
final class GlFontRasterState {
    private GlFontRasterState(){}
    static RasterState capture(){
        float[] color=new float[4],range=new float[2];glGetFloatv(GL_BLEND_COLOR,color);glGetFloatv(GL_DEPTH_RANGE,range);
        ByteBuffer flags=MemoryUtil.memAlloc(4);boolean[] mask=new boolean[4];try{glGetBooleanv(GL_COLOR_WRITEMASK,flags);for(int i=0;i<4;i++)mask[i]=flags.get(i)!=0;}finally{MemoryUtil.memFree(flags);}
        return new RasterState(glIsEnabled(GL_BLEND),glGetInteger(GL_BLEND_SRC_RGB),glGetInteger(GL_BLEND_DST_RGB),glGetInteger(GL_BLEND_SRC_ALPHA),glGetInteger(GL_BLEND_DST_ALPHA),glGetInteger(GL_BLEND_EQUATION_RGB),glGetInteger(GL_BLEND_EQUATION_ALPHA),color,
            glIsEnabled(GL_STENCIL_TEST),stencil(false),stencil(true),mask,range,glIsEnabled(GL_CULL_FACE),glGetInteger(GL_CULL_FACE_MODE),glGetInteger(GL_FRONT_FACE),glIsEnabled(GL_POLYGON_OFFSET_FILL),glGetFloat(GL_POLYGON_OFFSET_FACTOR),glGetFloat(GL_POLYGON_OFFSET_UNITS));
    }
    private static StencilFace stencil(boolean back){return new StencilFace(glGetInteger(back?GL_STENCIL_BACK_FUNC:GL_STENCIL_FUNC),glGetInteger(back?GL_STENCIL_BACK_REF:GL_STENCIL_REF),glGetInteger(back?GL_STENCIL_BACK_VALUE_MASK:GL_STENCIL_VALUE_MASK),glGetInteger(back?GL_STENCIL_BACK_WRITEMASK:GL_STENCIL_WRITEMASK),glGetInteger(back?GL_STENCIL_BACK_FAIL:GL_STENCIL_FAIL),glGetInteger(back?GL_STENCIL_BACK_PASS_DEPTH_FAIL:GL_STENCIL_PASS_DEPTH_FAIL),glGetInteger(back?GL_STENCIL_BACK_PASS_DEPTH_PASS:GL_STENCIL_PASS_DEPTH_PASS));}
    static void apply(RasterState s){
        enable(GL_BLEND,s.blendEnabled);glBlendFuncSeparate(s.blendSrcRgb,s.blendDstRgb,s.blendSrcAlpha,s.blendDstAlpha);glBlendEquationSeparate(s.blendEquationRgb,s.blendEquationAlpha);
        float[] c=s.blendColor();glBlendColor(c[0],c[1],c[2],c[3]);applyNonBlend(s);
    }
    static void applyNonBlend(RasterState s){
        enable(GL_STENCIL_TEST,s.stencilEnabled);stencil(GL_FRONT,s.frontStencil);stencil(GL_BACK,s.backStencil);
        boolean[] m=s.colorMask();glColorMask(m[0],m[1],m[2],m[3]);float[] range=s.depthRange();glDepthRange(range[0],range[1]);
        enable(GL_CULL_FACE,s.cullEnabled);glCullFace(s.cullFace);glFrontFace(s.frontFace);enable(GL_POLYGON_OFFSET_FILL,s.polygonOffsetFill);glPolygonOffset(s.polygonOffsetFactor,s.polygonOffsetUnits);
    }
    private static void stencil(int face,StencilFace s){glStencilFuncSeparate(face,s.function,s.reference,s.valueMask);glStencilMaskSeparate(face,s.writeMask);glStencilOpSeparate(face,s.fail,s.depthFail,s.depthPass);}
    private static void enable(int capability,boolean enabled){if(enabled)glEnable(capability);else glDisable(capability);}
}
