package club.heiqi.uilib.client;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** HUD 轻量 GL 围栏的状态守恒、能力降级与异常语义测试。 */
public class HudGlStateGuardTest {
    @Test
    public void restoresNonDefaultEntryStateIncludingActiveTextureAndModernBindings() {
        FakeGlAccess gl = new FakeGlAccess();
        EntryState entry = new EntryState(gl);

        new HudGlStateGuard(gl).run(gl::mutateAll);

        entry.assertRestored(gl);
        assertEquals(1, gl.captureCalls);
        assertEquals(1, gl.restoreCalls);
        assertEquals(1, gl.projectionDepth);
        assertEquals(2, gl.modelviewDepth);
    }

    @Test
    public void skipsEveryOptionalModernApiWhenCapabilitiesAreUnavailable() {
        FakeGlAccess gl = new FakeGlAccess();
        gl.activeTextureSupported = false;
        gl.buffersSupported = false;
        gl.programSupported = false;
        gl.vertexArraySupported = false;

        new HudGlStateGuard(gl).run(() -> gl.enabled.put(GL11.GL_DEPTH_TEST, false));

        assertEquals(0, gl.activeTextureCalls);
        assertEquals(0, gl.programCalls);
        assertEquals(0, gl.vertexArrayCalls);
        assertEquals(0, gl.bufferCalls);
        assertTrue(gl.enabled.get(GL11.GL_DEPTH_TEST));
    }

    @Test
    public void preservesRuntimeExceptionAndErrorAfterSuccessfulRestore() {
        assertBusinessFailurePreserved(new IllegalStateException("render"));
        assertBusinessFailurePreserved(new AssertionError("render-error"));
    }

    @Test
    public void captureFailureDoesNotRunFrameOrRestoreAndGuardCanBeReused() {
        FakeGlAccess gl = new FakeGlAccess();
        IllegalStateException captureFailure = new IllegalStateException("capture");
        gl.captureFailure = captureFailure;
        HudGlStateGuard guard = new HudGlStateGuard(gl);
        int[] frameCalls = { 0 };

        try {
            guard.run(() -> frameCalls[0]++);
            fail("capture 失败必须中止 HUD 业务");
        } catch (IllegalStateException actual) {
            assertSame(captureFailure, actual);
        }
        assertEquals(0, frameCalls[0]);
        assertEquals(0, gl.restoreCalls);

        gl.captureFailure = null;
        guard.run(() -> frameCalls[0]++);
        assertEquals(1, frameCalls[0]);
        assertEquals(2, gl.captureCalls);
        assertEquals(1, gl.restoreCalls);
    }

    @Test
    public void partialMatrixCaptureFailureRollsBackCompletedPush() {
        FakeGlAccess gl = new FakeGlAccess();
        gl.failPushNumber = 2;
        int entryMode = gl.matrixMode;
        int entryProjectionDepth = gl.projectionDepth;
        int entryModelviewDepth = gl.modelviewDepth;

        try {
            new HudGlStateGuard(gl).run(() -> fail("不得执行业务"));
            fail("第二次矩阵 push 失败必须向外传播");
        } catch (IllegalStateException expected) {
            assertEquals("push-2", expected.getMessage());
        }

        assertEquals(entryMode, gl.matrixMode);
        assertEquals(entryProjectionDepth, gl.projectionDepth);
        assertEquals(entryModelviewDepth, gl.modelviewDepth);
        assertEquals(1, gl.popCalls);
        assertEquals(0, gl.restoreCalls);
    }

    @Test
    public void restoreFailureDominatesAndSuppressesBusinessFailure() {
        FakeGlAccess gl = new FakeGlAccess();
        AssertionError restoreFailure = new AssertionError("restore");
        IllegalArgumentException frameFailure = new IllegalArgumentException("frame");
        gl.restoreFailure = restoreFailure;

        try {
            new HudGlStateGuard(gl).run(() -> { throw frameFailure; });
            fail("restore 失败必须阻断后续绘制");
        } catch (AssertionError actual) {
            assertSame(restoreFailure, actual);
            assertArrayEquals(new Throwable[] { frameFailure }, actual.getSuppressed());
        }
        assertEquals(1, gl.captureCalls);
        assertEquals(1, gl.restoreCalls);
    }

    @Test
    public void restoreRuntimeExceptionIsPropagatedAfterNormalFrame() {
        FakeGlAccess gl = new FakeGlAccess();
        IllegalStateException restoreFailure = new IllegalStateException("restore");
        gl.restoreFailure = restoreFailure;

        try {
            new HudGlStateGuard(gl).run(() -> { });
            fail("restore 失败必须传播");
        } catch (IllegalStateException actual) {
            assertSame(restoreFailure, actual);
            assertEquals(0, actual.getSuppressed().length);
        }
    }

    @Test
    public void rejectsReentryWithoutStartingSecondCapture() {
        FakeGlAccess gl = new FakeGlAccess();
        HudGlStateGuard guard = new HudGlStateGuard(gl);

        try {
            guard.run(() -> guard.run(() -> { }));
            fail("同一 guard 实例不得重入");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("不允许重入"));
        }
        assertEquals(1, gl.captureCalls);
        assertEquals(1, gl.restoreCalls);
    }

    @Test
    public void eachFrameCapturesAndRestoresOnceUsingSameSnapshotArrays() {
        FakeGlAccess gl = new FakeGlAccess();
        HudGlStateGuard guard = new HudGlStateGuard(gl);

        guard.run(() -> { });
        Object firstViewportTarget = gl.viewportReadTarget;
        Object firstColorTarget = gl.colorReadTarget;
        guard.run(() -> { });

        assertEquals(2, gl.captureCalls);
        assertEquals(2, gl.restoreCalls);
        assertSame(firstViewportTarget, gl.viewportReadTarget);
        assertSame(firstColorTarget, gl.colorReadTarget);
    }

    /** 回归 issue #70：LWJGL2 glGet* buffer 重载恒定校验 remaining >= 16，查询缓冲不得小于该阈值。 */
    @Test
    public void lwjglQueryBuffersProvideAtLeastSixteenRemainingElements() throws Exception {
        Class<?> accessType = Class.forName(HudGlStateGuard.class.getName() + "$LwjglGlAccess");
        Constructor<?> constructor = accessType.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object access = constructor.newInstance();

        assertQueryBufferRemaining(access, "floats");
        assertQueryBufferRemaining(access, "integers");
        assertQueryBufferRemaining(access, "booleans");
    }

    /** 反射断言指定查询缓冲的 remaining 不小于 LWJGL2 恒定校验阈值 16；构造不触发任何真实 GL 调用。 */
    private static void assertQueryBufferRemaining(Object access, String fieldName) throws Exception {
        Field field = access.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Buffer buffer = (Buffer) field.get(access);
        assertTrue(fieldName + " 查询缓冲 remaining 必须 >= 16，实际 " + buffer.remaining(),
                buffer.remaining() >= 16);
    }

    /** 验证业务失败原样传播，且 restore 仍恰好执行一次。 */
    private static void assertBusinessFailurePreserved(Throwable failure) {
        FakeGlAccess gl = new FakeGlAccess();
        try {
            new HudGlStateGuard(gl).run(() -> throwUnchecked(failure));
            fail("业务失败必须传播");
        } catch (RuntimeException actual) {
            assertSame(failure, actual);
        } catch (Error actual) {
            assertSame(failure, actual);
        }
        assertEquals(1, gl.captureCalls);
        assertEquals(1, gl.restoreCalls);
        assertTrue(gl.enabled.get(GL11.GL_DEPTH_TEST));
    }

    /** 重抛测试输入的 unchecked 失败。 */
    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        throw (Error) failure;
    }

    /** 捕获 fake 的完整入口状态，供状态守恒断言使用。 */
    private static final class EntryState {
        private final Map<Integer, Boolean> enabled;
        private final Map<Integer, Boolean> textureEnabled;
        private final Map<Integer, Integer> textureBindings;
        private final int matrixMode, activeTexture, blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha;
        private final int stencilFunction, stencilReference, stencilValueMask, stencilWriteMask;
        private final int stencilFail, stencilDepthFail, stencilDepthPass;
        private final int program, vertexArray, arrayBuffer, elementBuffer;
        private final boolean depthMask;
        private final float[] color;
        private final int[] scissor;
        private final boolean[] colorMask;
        private final int[] viewport;

        EntryState(FakeGlAccess gl) {
            enabled = new HashMap<Integer, Boolean>(gl.enabled);
            textureEnabled = new HashMap<Integer, Boolean>(gl.textureEnabled);
            textureBindings = new HashMap<Integer, Integer>(gl.textureBindings);
            matrixMode = gl.matrixMode; activeTexture = gl.activeTexture;
            blendSrcRgb = gl.blendSrcRgb; blendDstRgb = gl.blendDstRgb;
            blendSrcAlpha = gl.blendSrcAlpha; blendDstAlpha = gl.blendDstAlpha;
            stencilFunction = gl.stencilFunction; stencilReference = gl.stencilReference;
            stencilValueMask = gl.stencilValueMask; stencilWriteMask = gl.stencilWriteMask;
            stencilFail = gl.stencilFail; stencilDepthFail = gl.stencilDepthFail;
            stencilDepthPass = gl.stencilDepthPass; program = gl.program; vertexArray = gl.vertexArray;
            arrayBuffer = gl.arrayBuffer; elementBuffer = gl.elementBuffer; depthMask = gl.depthMask;
            color = gl.color.clone(); scissor = gl.scissor.clone(); colorMask = gl.colorMask.clone();
            viewport = gl.viewport.clone();
        }

        /** 逐项断言所有显式入口状态已恢复。 */
        void assertRestored(FakeGlAccess gl) {
            assertEquals(enabled, gl.enabled);
            assertEquals(textureEnabled, gl.textureEnabled);
            assertEquals(textureBindings, gl.textureBindings);
            assertEquals(matrixMode, gl.matrixMode); assertEquals(activeTexture, gl.activeTexture);
            assertEquals(blendSrcRgb, gl.blendSrcRgb); assertEquals(blendDstRgb, gl.blendDstRgb);
            assertEquals(blendSrcAlpha, gl.blendSrcAlpha); assertEquals(blendDstAlpha, gl.blendDstAlpha);
            assertEquals(stencilFunction, gl.stencilFunction); assertEquals(stencilReference, gl.stencilReference);
            assertEquals(stencilValueMask, gl.stencilValueMask); assertEquals(stencilWriteMask, gl.stencilWriteMask);
            assertEquals(stencilFail, gl.stencilFail); assertEquals(stencilDepthFail, gl.stencilDepthFail);
            assertEquals(stencilDepthPass, gl.stencilDepthPass); assertEquals(program, gl.program);
            assertEquals(vertexArray, gl.vertexArray); assertEquals(arrayBuffer, gl.arrayBuffer);
            assertEquals(elementBuffer, gl.elementBuffer); assertEquals(depthMask, gl.depthMask);
            assertArrayEquals(color, gl.color, 0.0F); assertArrayEquals(scissor, gl.scissor);
            assertArrayEquals(colorMask, gl.colorMask); assertArrayEquals(viewport, gl.viewport);
        }
    }

    /** 不触发 LWJGL 初始化的记录型完整状态访问桩。 */
    private static final class FakeGlAccess implements HudGlStateGuard.GlAccess {
        private final Map<Integer, Boolean> enabled = new HashMap<Integer, Boolean>();
        private final Map<Integer, Boolean> textureEnabled = new HashMap<Integer, Boolean>();
        private final Map<Integer, Integer> textureBindings = new HashMap<Integer, Integer>();
        private boolean activeTextureSupported = true, buffersSupported = true;
        private boolean programSupported = true, vertexArraySupported = true;
        private int matrixMode = GL11.GL_MODELVIEW, activeTexture = GL13.GL_TEXTURE1;
        private int projectionDepth = 1, modelviewDepth = 2;
        private int blendSrcRgb = 31, blendDstRgb = 32, blendSrcAlpha = 33, blendDstAlpha = 34;
        private int stencilFunction = 41, stencilReference = 42, stencilValueMask = 43, stencilWriteMask = 44;
        private int stencilFail = 45, stencilDepthFail = 46, stencilDepthPass = 47;
        private int program = 51, vertexArray = 52, arrayBuffer = 53, elementBuffer = 54;
        private boolean depthMask = false;
        private final float[] color = { 0.1F, 0.2F, 0.3F, 0.4F };
        private final int[] scissor = { 2, 3, 40, 50 };
        private final boolean[] colorMask = { true, false, true, false };
        private final int[] viewport = { 4, 5, 640, 360 };
        private int captureCalls, restoreCalls, activeTextureCalls, programCalls;
        private int vertexArrayCalls, bufferCalls, pushCalls, popCalls, failPushNumber;
        private RuntimeException captureFailure;
        private Throwable restoreFailure;
        private Object viewportReadTarget, colorReadTarget;

        FakeGlAccess() {
            enabled.put(GL11.GL_DEPTH_TEST, true); enabled.put(GL11.GL_CULL_FACE, false);
            enabled.put(GL11.GL_ALPHA_TEST, true); enabled.put(GL11.GL_LIGHTING, false);
            enabled.put(GL11.GL_BLEND, false); enabled.put(GL11.GL_SCISSOR_TEST, true);
            enabled.put(GL11.GL_STENCIL_TEST, false);
            textureEnabled.put(GL13.GL_TEXTURE0, false); textureEnabled.put(GL13.GL_TEXTURE1, true);
            textureBindings.put(GL13.GL_TEXTURE0, 61); textureBindings.put(GL13.GL_TEXTURE1, 62);
        }

        /** 将所有受围栏保护的状态改为不同值。 */
        void mutateAll() {
            for (Integer key : enabled.keySet()) enabled.put(key, !enabled.get(key));
            textureEnabled.put(GL13.GL_TEXTURE0, true); textureEnabled.put(GL13.GL_TEXTURE1, false);
            textureBindings.put(GL13.GL_TEXTURE0, 71); textureBindings.put(GL13.GL_TEXTURE1, 72);
            activeTexture = GL13.GL_TEXTURE0; matrixMode = GL11.GL_PROJECTION;
            blendSrcRgb = 1; blendDstRgb = 2; blendSrcAlpha = 3; blendDstAlpha = 4;
            stencilFunction = 5; stencilReference = 6; stencilValueMask = 7; stencilWriteMask = 8;
            stencilFail = 9; stencilDepthFail = 10; stencilDepthPass = 11;
            program = 12; vertexArray = 13; arrayBuffer = 14; elementBuffer = 15; depthMask = true;
            fill(color, 0.9F); fill(scissor, 99); fill(colorMask, true); fill(viewport, 88);
        }

        @Override public void beginCapture() {
            captureCalls++;
            if (captureFailure != null) throw captureFailure;
        }
        @Override public void beginRestore() {
            restoreCalls++;
            if (restoreFailure instanceof RuntimeException) throw (RuntimeException) restoreFailure;
            if (restoreFailure instanceof Error) throw (Error) restoreFailure;
        }
        @Override public boolean supportsActiveTexture() { return activeTextureSupported; }
        @Override public boolean supportsBuffers() { return buffersSupported; }
        @Override public boolean supportsProgram() { return programSupported; }
        @Override public boolean supportsVertexArray() { return vertexArraySupported; }
        @Override public boolean isEnabled(int capability) {
            if (capability == GL11.GL_TEXTURE_2D) return textureEnabled.get(activeTexture);
            return enabled.get(capability);
        }
        @Override public int getInteger(int name) {
            if (name == GL11.GL_MATRIX_MODE) return matrixMode;
            if (name == GL14.GL_BLEND_SRC_RGB) return blendSrcRgb;
            if (name == GL14.GL_BLEND_DST_RGB) return blendDstRgb;
            if (name == GL14.GL_BLEND_SRC_ALPHA) return blendSrcAlpha;
            if (name == GL14.GL_BLEND_DST_ALPHA) return blendDstAlpha;
            if (name == GL11.GL_STENCIL_FUNC) return stencilFunction;
            if (name == GL11.GL_STENCIL_REF) return stencilReference;
            if (name == GL11.GL_STENCIL_VALUE_MASK) return stencilValueMask;
            if (name == GL11.GL_STENCIL_WRITEMASK) return stencilWriteMask;
            if (name == GL11.GL_STENCIL_FAIL) return stencilFail;
            if (name == GL11.GL_STENCIL_PASS_DEPTH_FAIL) return stencilDepthFail;
            if (name == GL11.GL_STENCIL_PASS_DEPTH_PASS) return stencilDepthPass;
            if (name == GL11.GL_DEPTH_WRITEMASK) return depthMask ? GL11.GL_TRUE : GL11.GL_FALSE;
            if (name == GL13.GL_ACTIVE_TEXTURE) return activeTexture;
            if (name == GL11.GL_TEXTURE_BINDING_2D) return textureBindings.get(activeTexture);
            if (name == GL20.GL_CURRENT_PROGRAM) return program;
            if (name == GL30.GL_VERTEX_ARRAY_BINDING) return vertexArray;
            if (name == GL15.GL_ARRAY_BUFFER_BINDING) return arrayBuffer;
            if (name == GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING) return elementBuffer;
            throw new AssertionError("unexpected integer query " + name);
        }
        @Override public void readIntegers(int name, int[] target) {
            if (name == GL11.GL_SCISSOR_BOX) System.arraycopy(scissor, 0, target, 0, 4);
            else if (name == GL11.GL_VIEWPORT) {
                viewportReadTarget = target; System.arraycopy(viewport, 0, target, 0, 4);
            } else throw new AssertionError("unexpected integer vector " + name);
        }
        @Override public void readFloats(int name, float[] target) {
            if (name != GL11.GL_CURRENT_COLOR) throw new AssertionError("unexpected float vector " + name);
            colorReadTarget = target; System.arraycopy(color, 0, target, 0, 4);
        }
        @Override public void readBooleans(int name, boolean[] target) {
            if (name != GL11.GL_COLOR_WRITEMASK) throw new AssertionError("unexpected boolean vector " + name);
            System.arraycopy(colorMask, 0, target, 0, 4);
        }
        @Override public void setEnabled(int capability, boolean value) {
            if (capability == GL11.GL_TEXTURE_2D) textureEnabled.put(activeTexture, value);
            else enabled.put(capability, value);
        }
        @Override public void matrixMode(int mode) { matrixMode = mode; }
        @Override public void pushMatrix() {
            pushCalls++;
            if (pushCalls == failPushNumber) throw new IllegalStateException("push-" + pushCalls);
            if (matrixMode == GL11.GL_PROJECTION) projectionDepth++; else modelviewDepth++;
        }
        @Override public void popMatrix() {
            popCalls++;
            if (matrixMode == GL11.GL_PROJECTION) projectionDepth--; else modelviewDepth--;
        }
        @Override public void activeTexture(int unit) { activeTextureCalls++; activeTexture = unit; }
        @Override public void bindTexture2d(int texture) { textureBindings.put(activeTexture, texture); }
        @Override public void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
            blendSrcRgb = srcRgb; blendDstRgb = dstRgb; blendSrcAlpha = srcAlpha; blendDstAlpha = dstAlpha;
        }
        @Override public void color(float red, float green, float blue, float alpha) {
            color[0] = red; color[1] = green; color[2] = blue; color[3] = alpha;
        }
        @Override public void scissor(int x, int y, int width, int height) {
            scissor[0] = x; scissor[1] = y; scissor[2] = width; scissor[3] = height;
        }
        @Override public void stencilFunc(int function, int reference, int valueMask) {
            stencilFunction = function; stencilReference = reference; stencilValueMask = valueMask;
        }
        @Override public void stencilMask(int writeMask) { stencilWriteMask = writeMask; }
        @Override public void stencilOp(int fail, int depthFail, int depthPass) {
            stencilFail = fail; stencilDepthFail = depthFail; stencilDepthPass = depthPass;
        }
        @Override public void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
            colorMask[0] = red; colorMask[1] = green; colorMask[2] = blue; colorMask[3] = alpha;
        }
        @Override public void depthMask(boolean value) { depthMask = value; }
        @Override public void viewport(int x, int y, int width, int height) {
            viewport[0] = x; viewport[1] = y; viewport[2] = width; viewport[3] = height;
        }
        @Override public void useProgram(int value) { programCalls++; program = value; }
        @Override public void bindVertexArray(int value) { vertexArrayCalls++; vertexArray = value; }
        @Override public void bindBuffer(int target, int value) {
            bufferCalls++;
            if (target == GL15.GL_ARRAY_BUFFER) arrayBuffer = value; else elementBuffer = value;
        }

        private static void fill(float[] target, float value) {
            for (int index = 0; index < target.length; index++) target[index] = value;
        }
        private static void fill(int[] target, int value) {
            for (int index = 0; index < target.length; index++) target[index] = value;
        }
        private static void fill(boolean[] target, boolean value) {
            for (int index = 0; index < target.length; index++) target[index] = value;
        }
    }
}
