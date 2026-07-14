package club.heiqi.uilib.ui.image;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * 不可信 ItemStack renderer 的完整、可验证状态围栏。
 *
 * <p>测试通过 {@link StateAccess} 注入确定状态；生产访问器按当前 context 能力才调用可选 API。</p>
 */
public final class HostImageGlStateGuard {
    private static final int CLIENT_ALL_ATTRIB_BITS = -1;
    private static final AttribStackOperations SERVER_ATTRIB_OPERATIONS = new LwjglAttribStackOperations(false);
    private static final AttribStackOperations CLIENT_ATTRIB_OPERATIONS = new LwjglAttribStackOperations(true);
    /** 不透明状态快照标记。 */
    public interface Snapshot { }

    /** 状态读取、恢复与验证缝。 */
    public interface StateAccess {
        boolean isTessellatorIdle();
        int consumeGlError();
        Snapshot capture();
        void restore(Snapshot snapshot);
        String findDrift(Snapshot snapshot);
    }

    private final StateAccess stateAccess;

    /** 创建生产 GL 访问器围栏。 */
    public HostImageGlStateGuard() { this(new LwjglStateAccess()); }

    /** 创建注入访问器的围栏。 */
    public HostImageGlStateGuard(StateAccess stateAccess) {
        if (stateAccess == null) throw new IllegalArgumentException("stateAccess");
        this.stateAccess = stateAccess;
    }

    /** 执行 renderer，并在返回前恢复及验证入口状态。 */
    public HostImageRenderOutcome run(Runnable renderer) {
        if (renderer == null) return HostImageRenderOutcome.failure("precheck", null, false, "missing-renderer");
        if (!stateAccess.isTessellatorIdle()) {
            return HostImageRenderOutcome.failure("precheck", null, false, "tessellator-not-idle");
        }
        int entryError = stateAccess.consumeGlError();
        if (entryError != GL11.GL_NO_ERROR) {
            return HostImageRenderOutcome.failure("precheck", null, false, "entry-gl-error=" + entryError);
        }
        Snapshot snapshot;
        try {
            snapshot = stateAccess.capture();
        } catch (RuntimeException exception) {
            return HostImageRenderOutcome.failure("capture", exception, false, "capture-failed");
        } catch (LinkageError error) {
            return HostImageRenderOutcome.failure("capture", error, false, "capture-linkage");
        }

        Throwable renderFailure = null;
        try {
            renderer.run();
        } catch (RuntimeException exception) {
            renderFailure = exception;
        } catch (LinkageError error) {
            renderFailure = error;
        }

        try {
            stateAccess.restore(snapshot);
        } catch (RuntimeException exception) {
            if (renderFailure != null) exception.addSuppressed(renderFailure);
            return HostImageRenderOutcome.failure("restore", exception, false, "restore-failed");
        } catch (LinkageError error) {
            if (renderFailure != null) error.addSuppressed(renderFailure);
            return HostImageRenderOutcome.failure("restore", error, false, "restore-linkage");
        }
        String drift = stateAccess.findDrift(snapshot);
        int exitError = stateAccess.consumeGlError();
        boolean recovered = drift == null && exitError == GL11.GL_NO_ERROR && stateAccess.isTessellatorIdle();
        if (renderFailure != null) {
            return HostImageRenderOutcome.failure("render", renderFailure, recovered,
                    drift == null ? "renderer-failed" : drift);
        }
        if (!recovered) {
            String detail = drift != null ? drift : (exitError != GL11.GL_NO_ERROR
                    ? "exit-gl-error=" + exitError : "tessellator-not-idle");
            return HostImageRenderOutcome.failure("verify", null, false, detail);
        }
        return HostImageRenderOutcome.success();
    }

    /** LWJGL2 固定管线与现代 binding 的能力感知访问器。 */
    static final class LwjglStateAccess implements StateAccess {
        private static final TextureMatrixOperations TEXTURE_MATRIX_OPERATIONS = new LwjglTextureMatrixOperations();
        private Field tessellatorDrawingField;

        @Override
        public boolean isTessellatorIdle() {
            try {
                if (tessellatorDrawingField == null) {
                    try {
                        tessellatorDrawingField = Tessellator.class.getDeclaredField("isDrawing");
                    } catch (NoSuchFieldException deobfuscatedMissing) {
                        tessellatorDrawingField = Tessellator.class.getDeclaredField("field_78415_z");
                    }
                    tessellatorDrawingField.setAccessible(true);
                }
                return !tessellatorDrawingField.getBoolean(Tessellator.instance);
            } catch (NoSuchFieldException exception) {
                return false;
            } catch (IllegalAccessException exception) {
                return false;
            } catch (SecurityException exception) {
                return false;
            }
        }

        @Override public int consumeGlError() { return GL11.glGetError(); }

        @Override
        public Snapshot capture() {
            ContextCapabilities caps = GLContext.getCapabilities();
            LwjglSnapshot state = new LwjglSnapshot();
            state.hasGl13 = caps.OpenGL13;
            state.hasGl15 = caps.OpenGL15;
            state.hasGl20 = caps.OpenGL20;
            state.hasGl30 = caps.OpenGL30;
            state.textureMatrix = probeTextureMatrix(TEXTURE_MATRIX_OPERATIONS);
            state.attribStack = probeAttribStack(SERVER_ATTRIB_OPERATIONS, "server-attrib");
            state.clientAttribStack = probeAttribStack(CLIENT_ATTRIB_OPERATIONS, "client-attrib");
            state.matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            state.modelviewDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
            state.projectionDepth = GL11.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH);
            readInts(GL11.GL_VIEWPORT, state.viewport);
            readInts(GL11.GL_SCISSOR_BOX, state.scissor);
            readFloats(GL11.GL_MODELVIEW_MATRIX, state.modelview);
            readFloats(GL11.GL_PROJECTION_MATRIX, state.projection);
            captureTextureMatrix(TEXTURE_MATRIX_OPERATIONS, state.textureMatrix);
            if (state.hasGl13) {
                state.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
                state.clientActiveTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
                state.texture0 = textureBinding(GL13.GL_TEXTURE0);
                state.activeTextureBinding = state.activeTexture == GL13.GL_TEXTURE0
                        ? state.texture0 : textureBinding(state.activeTexture);
                GL13.glActiveTexture(state.activeTexture);
            }
            if (state.hasGl20) state.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            if (state.hasGl15) {
                state.arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
                state.elementBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
            }
            if (state.hasGl30) {
                state.vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
                state.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                state.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
                state.renderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
            }
            captureAttribStack(SERVER_ATTRIB_OPERATIONS, state.attribStack);
            captureAttribStack(CLIENT_ATTRIB_OPERATIONS, state.clientAttribStack);
            pushMatrix(GL11.GL_MODELVIEW);
            pushMatrix(GL11.GL_PROJECTION);
            GL11.glMatrixMode(state.matrixMode);
            return state;
        }

        @Override
        public void restore(Snapshot snapshot) {
            LwjglSnapshot state = (LwjglSnapshot) snapshot;
            normalizeMatrixDepth(GL11.GL_MODELVIEW, GL11.GL_MODELVIEW_STACK_DEPTH,
                    state.modelviewDepth + 1, "modelview");
            normalizeMatrixDepth(GL11.GL_PROJECTION, GL11.GL_PROJECTION_STACK_DEPTH,
                    state.projectionDepth + 1, "projection");
            restoreTextureMatrix(TEXTURE_MATRIX_OPERATIONS, state.textureMatrix);
            normalizeAttribStack(SERVER_ATTRIB_OPERATIONS, state.attribStack);
            normalizeAttribStack(CLIENT_ATTRIB_OPERATIONS, state.clientAttribStack);
            popMatrix(GL11.GL_PROJECTION);
            popMatrix(GL11.GL_MODELVIEW);
            popAttribStack(CLIENT_ATTRIB_OPERATIONS, state.clientAttribStack);
            popAttribStack(SERVER_ATTRIB_OPERATIONS, state.attribStack);
            if (state.hasGl20) GL20.glUseProgram(state.program);
            if (state.hasGl13) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, state.texture0);
                if (state.activeTexture != GL13.GL_TEXTURE0) {
                    GL13.glActiveTexture(state.activeTexture);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, state.activeTextureBinding);
                }
                GL13.glActiveTexture(state.activeTexture);
                GL13.glClientActiveTexture(state.clientActiveTexture);
            }
            if (state.hasGl30) {
                GL30.glBindVertexArray(state.vao);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, state.drawFramebuffer);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, state.readFramebuffer);
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, state.renderbuffer);
            }
            if (state.hasGl15) {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, state.arrayBuffer);
                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, state.elementBuffer);
            }
            GL11.glViewport(state.viewport[0], state.viewport[1], state.viewport[2], state.viewport[3]);
            GL11.glScissor(state.scissor[0], state.scissor[1], state.scissor[2], state.scissor[3]);
            GL11.glMatrixMode(state.matrixMode);
        }

        @Override
        public String findDrift(Snapshot snapshot) {
            LwjglSnapshot state = (LwjglSnapshot) snapshot;
            if (GL11.glGetInteger(GL11.GL_MATRIX_MODE) != state.matrixMode) return "matrix-mode";
            if (!equalInts(GL11.GL_VIEWPORT, state.viewport)) return "viewport";
            if (!equalInts(GL11.GL_SCISSOR_BOX, state.scissor)) return "scissor";
            if (!equalFloats(GL11.GL_MODELVIEW_MATRIX, state.modelview)) return "modelview";
            if (!equalFloats(GL11.GL_PROJECTION_MATRIX, state.projection)) return "projection";
            if (hasTextureMatrixDrift(TEXTURE_MATRIX_OPERATIONS, state.textureMatrix)) return "texture-matrix";
            if (state.hasGl20 && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != state.program) return "program";
            if (state.hasGl30 && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != state.drawFramebuffer)
                return "draw-fbo";
            if (state.hasGl30 && GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) != state.readFramebuffer)
                return "read-fbo";
            return null;
        }

        private static void normalizeMatrixDepth(int mode, int name, int expected, String label) {
            int actual = GL11.glGetInteger(name);
            if (actual < expected) throw new IllegalStateException(label + " stack underflow " + actual + " < " + expected);
            GL11.glMatrixMode(mode);
            while (actual-- > expected) GL11.glPopMatrix();
        }

        private static void pushMatrix(int mode) { GL11.glMatrixMode(mode); GL11.glPushMatrix(); }
        private static void popMatrix(int mode) { GL11.glMatrixMode(mode); GL11.glPopMatrix(); }
        private static int textureBinding(int unit) {
            GL13.glActiveTexture(unit);
            return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        private static void readInts(int name, int[] target) {
            IntBuffer buffer = BufferUtils.createIntBuffer(target.length);
            GL11.glGetInteger(name, buffer);
            for (int i = 0; i < target.length; i++) target[i] = buffer.get(i);
        }
        private static boolean equalInts(int name, int[] expected) {
            int[] actual = new int[expected.length]; readInts(name, actual);
            for (int i = 0; i < expected.length; i++) if (actual[i] != expected[i]) return false;
            return true;
        }
        private static void readFloats(int name, float[] target) {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(target.length);
            GL11.glGetFloat(name, buffer);
            for (int i = 0; i < target.length; i++) target[i] = buffer.get(i);
        }
        private static boolean equalFloats(int name, float[] expected) {
            float[] actual = new float[expected.length]; readFloats(name, actual);
            for (int i = 0; i < expected.length; i++) if (Math.abs(actual[i] - expected[i]) > 0.0001F) return false;
            return true;
        }
    }

    /** texture matrix 固定管线能力的最小可测试操作面。 */
    interface TextureMatrixOperations {
        int getStackDepth();
        int consumeGlError();
        void readMatrix(float[] target);
        void pushMatrix();
        void popMatrix();
    }

    /** texture matrix 子围栏快照；不支持时不执行任何后续固定管线操作。 */
    static final class TextureMatrixSnapshot implements Snapshot {
        private final boolean supported;
        private final int depth;
        private final float[] matrix = new float[16];

        private TextureMatrixSnapshot(boolean supported, int depth) {
            this.supported = supported;
            this.depth = depth;
        }

        boolean isSupported() { return supported; }
    }

    /**
     * 在入口 GL error 已清洁的前提下探测 texture matrix 能力。
     * 探测查询产生的全部错误会被消费，避免污染其余状态围栏。
     */
    static TextureMatrixSnapshot probeTextureMatrix(TextureMatrixOperations operations) {
        int depth = operations.getStackDepth();
        int error = operations.consumeGlError();
        if (error != GL11.GL_NO_ERROR) {
            while (operations.consumeGlError() != GL11.GL_NO_ERROR) {
                // 入口已验证无错误，因此这里只清理由本次能力查询产生的错误队列。
            }
            return new TextureMatrixSnapshot(false, 0);
        }
        return new TextureMatrixSnapshot(depth >= 1, depth);
    }

    /** 支持时读取 texture matrix 并压入围栏帧。 */
    static void captureTextureMatrix(TextureMatrixOperations operations, TextureMatrixSnapshot snapshot) {
        if (!snapshot.supported) return;
        operations.readMatrix(snapshot.matrix);
        operations.pushMatrix();
    }

    /** 支持时规范化并弹出 texture matrix 围栏帧。 */
    static void restoreTextureMatrix(TextureMatrixOperations operations, TextureMatrixSnapshot snapshot) {
        if (!snapshot.supported) return;
        int actual = operations.getStackDepth();
        int expected = snapshot.depth + 1;
        if (actual < expected) {
            throw new IllegalStateException("texture stack underflow " + actual + " < " + expected);
        }
        while (actual-- > expected) operations.popMatrix();
        operations.popMatrix();
    }

    /** 支持时比较 texture matrix；不支持时该子围栏不参与 drift 判定。 */
    static boolean hasTextureMatrixDrift(TextureMatrixOperations operations, TextureMatrixSnapshot snapshot) {
        if (!snapshot.supported) return false;
        float[] actual = new float[16];
        operations.readMatrix(actual);
        for (int i = 0; i < actual.length; i++) {
            if (Math.abs(actual[i] - snapshot.matrix[i]) > 0.0001F) return true;
        }
        return false;
    }

    /** 生产 LWJGL texture matrix 操作适配器。 */
    private static final class LwjglTextureMatrixOperations implements TextureMatrixOperations {
        @Override public int getStackDepth() { return GL11.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH); }
        @Override public int consumeGlError() { return GL11.glGetError(); }
        @Override public void readMatrix(float[] target) { LwjglStateAccess.readFloats(GL11.GL_TEXTURE_MATRIX, target); }
        @Override public void pushMatrix() { LwjglStateAccess.pushMatrix(GL11.GL_TEXTURE); }
        @Override public void popMatrix() { LwjglStateAccess.popMatrix(GL11.GL_TEXTURE); }
    }

    /** server/client attribute stack 的最小可测试操作面。 */
    interface AttribStackOperations {
        int getStackDepth();
        int consumeGlError();
        void push();
        void pop();
    }

    /** attribute stack 子围栏快照；server/client 能力彼此独立。 */
    static final class AttribStackSnapshot implements Snapshot {
        private final boolean supported;
        private final int depth;
        private final String label;

        private AttribStackSnapshot(boolean supported, int depth, String label) {
            this.supported = supported;
            this.depth = depth;
            this.label = label;
        }

        boolean isSupported() { return supported; }
    }

    /**
     * 在入口 GL error 已清洁的前提下探测单个 attribute stack 能力。
     * 查询错误会被完整消费，使 server/client 探测及退出检查互不污染。
     */
    static AttribStackSnapshot probeAttribStack(AttribStackOperations operations, String label) {
        int depth = operations.getStackDepth();
        int error = operations.consumeGlError();
        if (error != GL11.GL_NO_ERROR) {
            while (operations.consumeGlError() != GL11.GL_NO_ERROR) {
                // 入口已验证无错误，因此这里只清理由本次能力查询产生的错误队列。
            }
            return new AttribStackSnapshot(false, 0, label);
        }
        return new AttribStackSnapshot(depth >= 1, depth, label);
    }

    /** 支持时压入 attribute stack 围栏帧。 */
    static void captureAttribStack(AttribStackOperations operations, AttribStackSnapshot snapshot) {
        if (snapshot.supported) operations.push();
    }

    /** 支持时移除 renderer 额外压入的帧，并对真实下溢保持 fail-closed。 */
    static void normalizeAttribStack(AttribStackOperations operations, AttribStackSnapshot snapshot) {
        if (!snapshot.supported) return;
        int actual = operations.getStackDepth();
        int expected = snapshot.depth + 1;
        if (actual < expected) {
            throw new IllegalStateException(snapshot.label + " stack underflow " + actual + " < " + expected);
        }
        while (actual-- > expected) operations.pop();
    }

    /** 支持时弹出 attribute stack 围栏帧。 */
    static void popAttribStack(AttribStackOperations operations, AttribStackSnapshot snapshot) {
        if (snapshot.supported) operations.pop();
    }

    /** 以内外层一致的能力语义执行单个 attribute stack 子围栏。 */
    static void runWithAttribStackFence(AttribStackOperations operations, String label, Runnable action) {
        AttribStackSnapshot snapshot = probeAttribStack(operations, label);
        captureAttribStack(operations, snapshot);
        try {
            action.run();
        } finally {
            normalizeAttribStack(operations, snapshot);
            popAttribStack(operations, snapshot);
        }
    }

    /** @return 生产 server attribute stack 操作适配器 */
    static AttribStackOperations serverAttribStackOperations() { return SERVER_ATTRIB_OPERATIONS; }

    /** 生产 LWJGL server/client attribute stack 操作适配器。 */
    private static final class LwjglAttribStackOperations implements AttribStackOperations {
        private final boolean client;

        private LwjglAttribStackOperations(boolean client) { this.client = client; }

        @Override public int getStackDepth() {
            return GL11.glGetInteger(client ? GL11.GL_CLIENT_ATTRIB_STACK_DEPTH : GL11.GL_ATTRIB_STACK_DEPTH);
        }
        @Override public int consumeGlError() { return GL11.glGetError(); }
        @Override public void push() {
            if (client) GL11.glPushClientAttrib(CLIENT_ALL_ATTRIB_BITS);
            else GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        }
        @Override public void pop() {
            if (client) GL11.glPopClientAttrib();
            else GL11.glPopAttrib();
        }
    }

    private static final class LwjglSnapshot implements Snapshot {
        private boolean hasGl13, hasGl15, hasGl20, hasGl30;
        private int matrixMode, modelviewDepth, projectionDepth;
        private int activeTexture, clientActiveTexture, texture0, activeTextureBinding;
        private int program, vao, arrayBuffer, elementBuffer, drawFramebuffer, readFramebuffer, renderbuffer;
        private final int[] viewport = new int[4];
        private final int[] scissor = new int[4];
        private final float[] modelview = new float[16];
        private final float[] projection = new float[16];
        private TextureMatrixSnapshot textureMatrix;
        private AttribStackSnapshot attribStack, clientAttribStack;
    }
}
