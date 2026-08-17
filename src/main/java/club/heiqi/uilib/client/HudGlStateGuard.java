package club.heiqi.uilib.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/** HUD 帧入口的轻量 GL 状态围栏；实例、快照和查询缓冲均跨帧复用。 */
final class HudGlStateGuard {
    /** 可注入的最小 GL 状态访问面。 */
    interface GlAccess {
        void beginCapture();
        void beginRestore();
        boolean supportsActiveTexture();
        boolean supportsBuffers();
        boolean supportsProgram();
        boolean supportsVertexArray();
        boolean isEnabled(int capability);
        int getInteger(int name);
        void readIntegers(int name, int[] target);
        void readFloats(int name, float[] target);
        void readBooleans(int name, boolean[] target);
        void setEnabled(int capability, boolean enabled);
        void matrixMode(int mode);
        void pushMatrix();
        void popMatrix();
        void activeTexture(int unit);
        void bindTexture2d(int texture);
        void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);
        void color(float red, float green, float blue, float alpha);
        void scissor(int x, int y, int width, int height);
        void stencilFunc(int function, int reference, int valueMask);
        void stencilMask(int writeMask);
        void stencilOp(int fail, int depthFail, int depthPass);
        void colorMask(boolean red, boolean green, boolean blue, boolean alpha);
        void depthMask(boolean enabled);
        void viewport(int x, int y, int width, int height);
        void useProgram(int program);
        void bindVertexArray(int vertexArray);
        void bindBuffer(int target, int buffer);
    }

    private final GlAccess gl;
    private final Snapshot snapshot = new Snapshot();
    private boolean active;

    /** 创建生产 LWJGL 状态围栏。 */
    HudGlStateGuard() {
        this(new LwjglGlAccess());
    }

    /** 创建使用指定状态访问面的围栏。 */
    HudGlStateGuard(GlAccess gl) {
        if (gl == null) throw new IllegalArgumentException("gl");
        this.gl = gl;
    }

    /**
     * 在单次 capture/restore 边界内执行完整 HUD 帧。
     * restore 失败优先抛出，业务失败作为 suppressed 保留。
     */
    void run(Runnable frame) {
        if (frame == null) throw new IllegalArgumentException("frame");
        if (active) throw new IllegalStateException("HUD GL 状态围栏不允许重入");
        active = true;
        try {
            capture();
            Throwable frameFailure = null;
            try {
                frame.run();
            } catch (RuntimeException failure) {
                frameFailure = failure;
            } catch (Error failure) {
                frameFailure = failure;
            }

            Throwable restoreFailure = null;
            try {
                restore();
            } catch (RuntimeException failure) {
                restoreFailure = failure;
            } catch (Error failure) {
                restoreFailure = failure;
            }
            if (restoreFailure != null) {
                if (frameFailure != null) restoreFailure.addSuppressed(frameFailure);
                throwUnchecked(restoreFailure);
            }
            if (frameFailure != null) throwUnchecked(frameFailure);
        } finally {
            active = false;
        }
    }

    /** 捕获真实入口状态，并为 projection/modelview 各压入一个围栏帧。 */
    private void capture() {
        gl.beginCapture();
        snapshot.matrixMode = gl.getInteger(GL11.GL_MATRIX_MODE);
        snapshot.depthTest = gl.isEnabled(GL11.GL_DEPTH_TEST);
        snapshot.cullFace = gl.isEnabled(GL11.GL_CULL_FACE);
        snapshot.alphaTest = gl.isEnabled(GL11.GL_ALPHA_TEST);
        snapshot.lighting = gl.isEnabled(GL11.GL_LIGHTING);
        snapshot.blend = gl.isEnabled(GL11.GL_BLEND);
        snapshot.scissorTest = gl.isEnabled(GL11.GL_SCISSOR_TEST);
        snapshot.stencilTest = gl.isEnabled(GL11.GL_STENCIL_TEST);
        snapshot.texture2d = gl.isEnabled(GL11.GL_TEXTURE_2D);
        snapshot.blendSrcRgb = gl.getInteger(GL14.GL_BLEND_SRC_RGB);
        snapshot.blendDstRgb = gl.getInteger(GL14.GL_BLEND_DST_RGB);
        snapshot.blendSrcAlpha = gl.getInteger(GL14.GL_BLEND_SRC_ALPHA);
        snapshot.blendDstAlpha = gl.getInteger(GL14.GL_BLEND_DST_ALPHA);
        gl.readFloats(GL11.GL_CURRENT_COLOR, snapshot.color);
        gl.readIntegers(GL11.GL_SCISSOR_BOX, snapshot.scissor);
        snapshot.stencilFunction = gl.getInteger(GL11.GL_STENCIL_FUNC);
        snapshot.stencilReference = gl.getInteger(GL11.GL_STENCIL_REF);
        snapshot.stencilValueMask = gl.getInteger(GL11.GL_STENCIL_VALUE_MASK);
        snapshot.stencilWriteMask = gl.getInteger(GL11.GL_STENCIL_WRITEMASK);
        snapshot.stencilFail = gl.getInteger(GL11.GL_STENCIL_FAIL);
        snapshot.stencilDepthFail = gl.getInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
        snapshot.stencilDepthPass = gl.getInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
        gl.readBooleans(GL11.GL_COLOR_WRITEMASK, snapshot.colorMask);
        snapshot.depthMask = gl.getInteger(GL11.GL_DEPTH_WRITEMASK) != GL11.GL_FALSE;
        gl.readIntegers(GL11.GL_VIEWPORT, snapshot.viewport);

        snapshot.hasActiveTexture = gl.supportsActiveTexture();
        snapshot.hasProgram = gl.supportsProgram();
        snapshot.hasVertexArray = gl.supportsVertexArray();
        snapshot.hasBuffers = gl.supportsBuffers();
        captureOptionalBindings();
        pushMatrices();
    }

    /** 能力存在时捕获 texture0、入口 active unit 与现代对象 binding。 */
    private void captureOptionalBindings() {
        if (snapshot.hasActiveTexture) {
            snapshot.activeTexture = gl.getInteger(GL13.GL_ACTIVE_TEXTURE);
            snapshot.activeTexture2d = snapshot.texture2d;
            snapshot.activeTextureBinding = gl.getInteger(GL11.GL_TEXTURE_BINDING_2D);
            try {
                if (snapshot.activeTexture != GL13.GL_TEXTURE0) gl.activeTexture(GL13.GL_TEXTURE0);
                snapshot.texture0Enabled = gl.isEnabled(GL11.GL_TEXTURE_2D);
                snapshot.texture0Binding = gl.getInteger(GL11.GL_TEXTURE_BINDING_2D);
            } finally {
                gl.activeTexture(snapshot.activeTexture);
            }
        }
        if (snapshot.hasProgram) snapshot.program = gl.getInteger(GL20.GL_CURRENT_PROGRAM);
        if (snapshot.hasVertexArray) snapshot.vertexArray = gl.getInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        if (snapshot.hasBuffers) {
            snapshot.arrayBuffer = gl.getInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            snapshot.elementBuffer = gl.getInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        }
    }

    /** 矩阵 capture 部分失败时回滚所有已完成的 push，并恢复入口 mode。 */
    private void pushMatrices() {
        boolean projectionPushed = false;
        boolean modelviewPushed = false;
        try {
            gl.matrixMode(GL11.GL_PROJECTION);
            gl.pushMatrix();
            projectionPushed = true;
            gl.matrixMode(GL11.GL_MODELVIEW);
            gl.pushMatrix();
            modelviewPushed = true;
            gl.matrixMode(snapshot.matrixMode);
        } catch (RuntimeException failure) {
            rollbackCaptureMatrices(projectionPushed, modelviewPushed, failure);
        } catch (Error failure) {
            rollbackCaptureMatrices(projectionPushed, modelviewPushed, failure);
        }
    }

    /** 回滚 capture 阶段已压入的矩阵；回滚失败优先抛出。 */
    private void rollbackCaptureMatrices(boolean projectionPushed, boolean modelviewPushed, Throwable captureFailure) {
        Throwable rollbackFailure = null;
        if (modelviewPushed) {
            try {
                gl.matrixMode(GL11.GL_MODELVIEW);
                gl.popMatrix();
            } catch (RuntimeException failure) {
                rollbackFailure = failure;
            } catch (Error failure) {
                rollbackFailure = failure;
            }
        }
        if (projectionPushed) {
            try {
                gl.matrixMode(GL11.GL_PROJECTION);
                gl.popMatrix();
            } catch (RuntimeException failure) {
                rollbackFailure = appendFailure(rollbackFailure, failure);
            } catch (Error failure) {
                rollbackFailure = appendFailure(rollbackFailure, failure);
            }
        }
        try {
            gl.matrixMode(snapshot.matrixMode);
        } catch (RuntimeException failure) {
            rollbackFailure = appendFailure(rollbackFailure, failure);
        } catch (Error failure) {
            rollbackFailure = appendFailure(rollbackFailure, failure);
        }
        if (rollbackFailure != null) {
            rollbackFailure.addSuppressed(captureFailure);
            throwUnchecked(rollbackFailure);
        }
        throwUnchecked(captureFailure);
    }

    /** 恢复所有显式状态；矩阵栈、active texture 与 matrix mode 在末尾恢复。 */
    private void restore() {
        gl.beginRestore();
        Throwable failure = null;
        try {
            restoreOptionalBindings();
        } catch (RuntimeException restoreFailure) {
            failure = restoreFailure;
        } catch (Error restoreFailure) {
            failure = restoreFailure;
        }
        try {
            restoreFixedState();
        } catch (RuntimeException restoreFailure) {
            failure = appendFailure(failure, restoreFailure);
        } catch (Error restoreFailure) {
            failure = appendFailure(failure, restoreFailure);
        }
        try {
            gl.matrixMode(GL11.GL_MODELVIEW);
            gl.popMatrix();
        } catch (RuntimeException restoreFailure) {
            failure = appendFailure(failure, restoreFailure);
        } catch (Error restoreFailure) {
            failure = appendFailure(failure, restoreFailure);
        }
        try {
            gl.matrixMode(GL11.GL_PROJECTION);
            gl.popMatrix();
        } catch (RuntimeException restoreFailure) {
            failure = appendFailure(failure, restoreFailure);
        } catch (Error restoreFailure) {
            failure = appendFailure(failure, restoreFailure);
        }
        if (snapshot.hasActiveTexture) {
            try {
                gl.activeTexture(snapshot.activeTexture);
            } catch (RuntimeException restoreFailure) {
                failure = appendFailure(failure, restoreFailure);
            } catch (Error restoreFailure) {
                failure = appendFailure(failure, restoreFailure);
            }
        }
        try {
            gl.matrixMode(snapshot.matrixMode);
        } catch (RuntimeException restoreFailure) {
            failure = appendFailure(failure, restoreFailure);
        } catch (Error restoreFailure) {
            failure = appendFailure(failure, restoreFailure);
        }
        if (failure != null) throwUnchecked(failure);
    }

    /** 恢复能力感知的 texture、program、VAO 与 buffer binding。 */
    private void restoreOptionalBindings() {
        if (snapshot.hasProgram) gl.useProgram(snapshot.program);
        if (snapshot.hasActiveTexture) {
            gl.activeTexture(GL13.GL_TEXTURE0);
            gl.bindTexture2d(snapshot.texture0Binding);
            gl.setEnabled(GL11.GL_TEXTURE_2D, snapshot.texture0Enabled);
            if (snapshot.activeTexture != GL13.GL_TEXTURE0) {
                gl.activeTexture(snapshot.activeTexture);
                gl.bindTexture2d(snapshot.activeTextureBinding);
                gl.setEnabled(GL11.GL_TEXTURE_2D, snapshot.activeTexture2d);
            }
        } else {
            gl.setEnabled(GL11.GL_TEXTURE_2D, snapshot.texture2d);
        }
        if (snapshot.hasVertexArray) gl.bindVertexArray(snapshot.vertexArray);
        if (snapshot.hasBuffers) {
            gl.bindBuffer(GL15.GL_ARRAY_BUFFER, snapshot.arrayBuffer);
            gl.bindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, snapshot.elementBuffer);
        }
    }

    /** 恢复固定管线、裁切、模板、写掩码、颜色和视口状态。 */
    private void restoreFixedState() {
        gl.blendFuncSeparate(snapshot.blendSrcRgb, snapshot.blendDstRgb,
                snapshot.blendSrcAlpha, snapshot.blendDstAlpha);
        gl.color(snapshot.color[0], snapshot.color[1], snapshot.color[2], snapshot.color[3]);
        gl.scissor(snapshot.scissor[0], snapshot.scissor[1], snapshot.scissor[2], snapshot.scissor[3]);
        gl.stencilFunc(snapshot.stencilFunction, snapshot.stencilReference, snapshot.stencilValueMask);
        gl.stencilMask(snapshot.stencilWriteMask);
        gl.stencilOp(snapshot.stencilFail, snapshot.stencilDepthFail, snapshot.stencilDepthPass);
        gl.colorMask(snapshot.colorMask[0], snapshot.colorMask[1], snapshot.colorMask[2], snapshot.colorMask[3]);
        gl.depthMask(snapshot.depthMask);
        gl.viewport(snapshot.viewport[0], snapshot.viewport[1], snapshot.viewport[2], snapshot.viewport[3]);
        gl.setEnabled(GL11.GL_DEPTH_TEST, snapshot.depthTest);
        gl.setEnabled(GL11.GL_CULL_FACE, snapshot.cullFace);
        gl.setEnabled(GL11.GL_ALPHA_TEST, snapshot.alphaTest);
        gl.setEnabled(GL11.GL_LIGHTING, snapshot.lighting);
        gl.setEnabled(GL11.GL_BLEND, snapshot.blend);
        gl.setEnabled(GL11.GL_SCISSOR_TEST, snapshot.scissorTest);
        gl.setEnabled(GL11.GL_STENCIL_TEST, snapshot.stencilTest);
    }

    /** 合并清理失败，保留第一次失败作为主异常。 */
    private static Throwable appendFailure(Throwable primary, Throwable next) {
        if (primary == null) return next;
        primary.addSuppressed(next);
        return primary;
    }

    /** 仅重抛围栏允许捕获的 unchecked 失败。 */
    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        throw (Error) failure;
    }

    /** 跨帧复用的 HUD GL 状态快照。 */
    private static final class Snapshot {
        private boolean depthTest, cullFace, alphaTest, lighting, blend, scissorTest, stencilTest, texture2d;
        private boolean depthMask;
        private boolean hasActiveTexture, hasProgram, hasVertexArray, hasBuffers;
        private boolean activeTexture2d, texture0Enabled;
        private int matrixMode;
        private int blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha;
        private int stencilFunction, stencilReference, stencilValueMask, stencilWriteMask;
        private int stencilFail, stencilDepthFail, stencilDepthPass;
        private int activeTexture, activeTextureBinding, texture0Binding;
        private int program, vertexArray, arrayBuffer, elementBuffer;
        private final float[] color = new float[4];
        private final int[] scissor = new int[4];
        private final boolean[] colorMask = new boolean[4];
        private final int[] viewport = new int[4];
    }

    /** 生产 LWJGL2 状态访问器；可选 API 只在当前 context 对应能力存在时调用。 */
    private static final class LwjglGlAccess implements GlAccess {
        // LWJGL2 的 glGetInteger/glGetFloat/glGetBoolean(int, XxxBuffer) 重载经 BufferChecks
        // 对缓冲区 remaining 恒定校验 >= 16，容量 4 的查询缓冲会在任何 capture 时崩溃（issue #70）；
        // 查询结果复制仍只取 target.length 个元素，不依赖缓冲满容量。
        private final IntBuffer integers = ByteBuffer.allocateDirect(16 * Integer.BYTES)
                .order(ByteOrder.nativeOrder()).asIntBuffer();
        private final FloatBuffer floats = ByteBuffer.allocateDirect(16 * Float.BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        private final ByteBuffer booleans = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());

        @Override public void beginCapture() { }
        @Override public void beginRestore() { }
        @Override public boolean supportsActiveTexture() { return capabilities().OpenGL13; }
        @Override public boolean supportsBuffers() { return capabilities().OpenGL15; }
        @Override public boolean supportsProgram() { return capabilities().OpenGL20; }
        @Override public boolean supportsVertexArray() { return capabilities().OpenGL30; }
        @Override public boolean isEnabled(int capability) { return GL11.glIsEnabled(capability); }
        @Override public int getInteger(int name) { return GL11.glGetInteger(name); }
        @Override public void readIntegers(int name, int[] target) {
            integers.clear();
            GL11.glGetInteger(name, integers);
            for (int index = 0; index < target.length; index++) target[index] = integers.get(index);
        }
        @Override public void readFloats(int name, float[] target) {
            floats.clear();
            GL11.glGetFloat(name, floats);
            for (int index = 0; index < target.length; index++) target[index] = floats.get(index);
        }
        @Override public void readBooleans(int name, boolean[] target) {
            booleans.clear();
            GL11.glGetBoolean(name, booleans);
            for (int index = 0; index < target.length; index++) target[index] = booleans.get(index) != GL11.GL_FALSE;
        }
        @Override public void setEnabled(int capability, boolean enabled) {
            if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability);
        }
        @Override public void matrixMode(int mode) { GL11.glMatrixMode(mode); }
        @Override public void pushMatrix() { GL11.glPushMatrix(); }
        @Override public void popMatrix() { GL11.glPopMatrix(); }
        @Override public void activeTexture(int unit) { GL13.glActiveTexture(unit); }
        @Override public void bindTexture2d(int texture) { GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); }
        @Override public void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
        @Override public void color(float red, float green, float blue, float alpha) {
            GL11.glColor4f(red, green, blue, alpha);
        }
        @Override public void scissor(int x, int y, int width, int height) { GL11.glScissor(x, y, width, height); }
        @Override public void stencilFunc(int function, int reference, int valueMask) {
            GL11.glStencilFunc(function, reference, valueMask);
        }
        @Override public void stencilMask(int writeMask) { GL11.glStencilMask(writeMask); }
        @Override public void stencilOp(int fail, int depthFail, int depthPass) {
            GL11.glStencilOp(fail, depthFail, depthPass);
        }
        @Override public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
            GL11.glColorMask(red, green, blue, alpha);
        }
        @Override public void depthMask(boolean enabled) { GL11.glDepthMask(enabled); }
        @Override public void viewport(int x, int y, int width, int height) { GL11.glViewport(x, y, width, height); }
        @Override public void useProgram(int program) { GL20.glUseProgram(program); }
        @Override public void bindVertexArray(int vertexArray) { GL30.glBindVertexArray(vertexArray); }
        @Override public void bindBuffer(int target, int buffer) { GL15.glBindBuffer(target, buffer); }

        /** 读取当前线程绑定 context 的能力。 */
        private static ContextCapabilities capabilities() {
            return GLContext.getCapabilities();
        }
    }
}
