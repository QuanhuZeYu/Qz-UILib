package club.heiqi.uilib.host.client.gl;

import static club.heiqi.uilib.host.client.gl.GlTokens.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import club.heiqi.uilib.host.client.font.context.FontRenderContext.RasterState;
import club.heiqi.uilib.host.client.font.context.FontRenderContext.StencilFace;

/** world历史光栅载荷只短时应用；退出恢复actual进入态，不写宿主有效缓存。 */
final class GlFontRasterState {
    private GlFontRasterState() {}

    static RasterState capture(GlApi gl) {
        float[] color = new float[4], range = new float[2];
        gl.glGetFloatv(GL_BLEND_COLOR, color);
        gl.glGetFloatv(GL_DEPTH_RANGE, range);
        boolean[] mask = new boolean[4];
        for (int i = 0; i < 4; i++) mask[i] = false;
        int[] maskInts = new int[4];
        gl.glGetIntegerv(GL_COLOR_WRITEMASK, maskInts);
        for (int i = 0; i < 4; i++) mask[i] = maskInts[i] != 0;
        return new RasterState(
                gl.glIsEnabled(GL_BLEND),
                gl.glGetInteger(GL_BLEND_SRC_RGB),
                gl.glGetInteger(GL_BLEND_DST_RGB),
                gl.glGetInteger(GL_BLEND_SRC_ALPHA),
                gl.glGetInteger(GL_BLEND_DST_ALPHA),
                gl.glGetInteger(GL_BLEND_EQUATION_RGB),
                gl.glGetInteger(GL_BLEND_EQUATION_ALPHA),
                color,
                gl.glIsEnabled(GL_STENCIL_TEST),
                stencil(gl, false),
                stencil(gl, true),
                mask,
                range,
                gl.glIsEnabled(GL_CULL_FACE),
                gl.glGetInteger(GL_CULL_FACE_MODE),
                gl.glGetInteger(GL_FRONT_FACE),
                gl.glIsEnabled(GL_POLYGON_OFFSET_FILL),
                gl.glGetFloat(GL_POLYGON_OFFSET_FACTOR),
                gl.glGetFloat(GL_POLYGON_OFFSET_UNITS));
    }

    private static StencilFace stencil(GlApi gl, boolean back) {
        return new StencilFace(
                gl.glGetInteger(back ? GL_STENCIL_BACK_FUNC : GL_STENCIL_FUNC),
                gl.glGetInteger(back ? GL_STENCIL_BACK_REF : GL_STENCIL_REF),
                gl.glGetInteger(back ? GL_STENCIL_BACK_VALUE_MASK : GL_STENCIL_VALUE_MASK),
                gl.glGetInteger(back ? GL_STENCIL_BACK_WRITEMASK : GL_STENCIL_WRITEMASK),
                gl.glGetInteger(back ? GL_STENCIL_BACK_FAIL : GL_STENCIL_FAIL),
                gl.glGetInteger(
                        back ? GL_STENCIL_BACK_PASS_DEPTH_FAIL : GL_STENCIL_PASS_DEPTH_FAIL),
                gl.glGetInteger(
                        back ? GL_STENCIL_BACK_PASS_DEPTH_PASS : GL_STENCIL_PASS_DEPTH_PASS));
    }

    static void apply(GlApi gl, RasterState s) {
        enable(gl, GL_BLEND, s.blendEnabled);
        gl.glBlendFuncSeparate(s.blendSrcRgb, s.blendDstRgb, s.blendSrcAlpha, s.blendDstAlpha);
        gl.glBlendEquationSeparate(s.blendEquationRgb, s.blendEquationAlpha);
        float[] c = s.blendColor();
        gl.glBlendColor(c[0], c[1], c[2], c[3]);
        applyNonBlend(gl, s);
    }

    static void applyNonBlend(GlApi gl, RasterState s) {
        enable(gl, GL_STENCIL_TEST, s.stencilEnabled);
        stencil(gl, GL_FRONT, s.frontStencil);
        stencil(gl, GL_BACK, s.backStencil);
        boolean[] m = s.colorMask();
        gl.glColorMask(m[0], m[1], m[2], m[3]);
        float[] range = s.depthRange();
        gl.glDepthRange(range[0], range[1]);
        enable(gl, GL_CULL_FACE, s.cullEnabled);
        gl.glCullFace(s.cullFace);
        gl.glFrontFace(s.frontFace);
        enable(gl, GL_POLYGON_OFFSET_FILL, s.polygonOffsetFill);
        gl.glPolygonOffset(s.polygonOffsetFactor, s.polygonOffsetUnits);
    }

    private static void stencil(GlApi gl, int face, StencilFace s) {
        gl.glStencilFuncSeparate(face, s.function, s.reference, s.valueMask);
        gl.glStencilMaskSeparate(face, s.writeMask);
        gl.glStencilOpSeparate(face, s.fail, s.depthFail, s.depthPass);
    }

    private static void enable(GlApi gl, int capability, boolean enabled) {
        if (enabled) gl.glEnable(capability);
        else gl.glDisable(capability);
    }
}
