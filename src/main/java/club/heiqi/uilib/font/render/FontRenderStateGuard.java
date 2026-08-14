package club.heiqi.uilib.font.render;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * 字体渲染状态保护器。
 *
 * <p>TEXTURE_2D enable 是 per-texture-unit 状态，{@code glPushAttrib}/{@code glPopAttrib} 只保存/恢复
 * push/pop 时刻 active unit 的 per-unit 状态。flush 会在中途 {@code glActiveTexture(GL_TEXTURE0)} 并
 * 在 push 时的 active unit 上启用 TEXTURE_2D，导致 attrib pop 与单元错位。因此 push 时显式记录 unit0
 * 与 push 时 active unit 的 TEXTURE_2D enable，pop 时在各自 unit 上显式恢复，再切回 push 时 active unit。</p>
 */
public class FontRenderStateGuard implements FontRenderStateExecutor {

    /** 可注入的最小 GL 状态访问面，供同包测试在不初始化 LWJGL 的情况下验证状态守恒。 */
    interface GlAccess {

        void pushAttrib(int mask);

        void pushClientAttrib(int mask);

        void popAttrib();

        void popClientAttrib();

        int getInteger(int name);

        void readIntegers(int name, int[] target);

        boolean isEnabled(int capability);

        void setEnabled(int capability, boolean enabled);

        void matrixMode(int mode);

        void pushMatrix();

        void popMatrix();

        void activeTexture(int unit);

        void bindTexture2d(int texture);

        void useProgram(int program);

        void bindVertexArray(int vertexArray);

        void bindBuffer(int target, int buffer);

        void viewport(int x, int y, int width, int height);
    }

    private static final class SavedState {

        private final boolean matrixStateSaved;
        private final int[] viewport = new int[4];
        private int activeTexture;
        private int currentProgram;
        private int currentMatrixMode;
        private int textureBinding2DOnTexture0;
        private int textureBinding2DOnActiveTexture;
        private boolean texture2DEnabledOnTexture0;
        private boolean texture2DEnabledOnActiveTexture;
        private int vertexArrayBinding;
        private int arrayBufferBinding;
        private int elementArrayBufferBinding;

        private SavedState(boolean matrixStateSaved) {
            this.matrixStateSaved = matrixStateSaved;
        }
    }

    private final GlAccess gl;
    private final int[] viewportScratch = new int[4];
    private final Deque<SavedState> savedStates = new ArrayDeque<SavedState>();

    /** 创建生产 LWJGL 状态保护器。 */
    public FontRenderStateGuard() {
        this(new LwjglGlAccess());
    }

    /** 创建使用指定状态访问面的保护器。 */
    FontRenderStateGuard(GlAccess gl) {
        if (gl == null) {
            throw new IllegalArgumentException("gl 不得为 null");
        }
        this.gl = gl;
    }

    /**
     * 保存当前 OpenGL 状态。
     */
    public void push() {
        push(true);
    }

    /**
     * 保存当前 OpenGL 状态。
     *
     * @param includeMatrixState 是否同时保存固定管线矩阵栈
     */
    public void push(boolean includeMatrixState) {
        SavedState state = new SavedState(includeMatrixState);
        gl.pushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        gl.pushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
        if (includeMatrixState) {
            state.currentMatrixMode = gl.getInteger(GL11.GL_MATRIX_MODE);
            pushMatrixStack(GL11.GL_MODELVIEW);
            pushMatrixStack(GL11.GL_PROJECTION);
            pushMatrixStack(GL11.GL_TEXTURE);
        }

        state.activeTexture = gl.getInteger(GL13.GL_ACTIVE_TEXTURE);
        state.currentProgram = gl.getInteger(GL20.GL_CURRENT_PROGRAM);
        gl.activeTexture(GL13.GL_TEXTURE0);
        state.textureBinding2DOnTexture0 = gl.getInteger(GL11.GL_TEXTURE_BINDING_2D);
        state.texture2DEnabledOnTexture0 = gl.isEnabled(GL11.GL_TEXTURE_2D);
        if (state.activeTexture != GL13.GL_TEXTURE0) {
            gl.activeTexture(state.activeTexture);
            state.textureBinding2DOnActiveTexture = gl.getInteger(GL11.GL_TEXTURE_BINDING_2D);
            state.texture2DEnabledOnActiveTexture = gl.isEnabled(GL11.GL_TEXTURE_2D);
        } else {
            state.textureBinding2DOnActiveTexture = state.textureBinding2DOnTexture0;
            state.texture2DEnabledOnActiveTexture = state.texture2DEnabledOnTexture0;
        }
        gl.activeTexture(state.activeTexture);
        state.vertexArrayBinding = gl.getInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        state.arrayBufferBinding = gl.getInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        state.elementArrayBufferBinding = gl.getInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        gl.readIntegers(GL11.GL_VIEWPORT, viewportScratch);
        System.arraycopy(viewportScratch, 0, state.viewport, 0, state.viewport.length);
        savedStates.push(state);
    }

    /**
     * 恢复之前保存的 OpenGL 状态。
     */
    public void pop() {
        if (savedStates.isEmpty()) {
            throw new IllegalStateException("字体渲染状态恢复缺少对应的保存边界");
        }
        SavedState state = savedStates.pop();
        if (state.matrixStateSaved) {
            popMatrixStack(GL11.GL_TEXTURE);
            popMatrixStack(GL11.GL_PROJECTION);
            popMatrixStack(GL11.GL_MODELVIEW);
            gl.matrixMode(state.currentMatrixMode);
        }
        gl.popClientAttrib();
        gl.popAttrib();

        gl.useProgram(state.currentProgram);
        gl.activeTexture(GL13.GL_TEXTURE0);
        gl.bindTexture2d(state.textureBinding2DOnTexture0);
        gl.setEnabled(GL11.GL_TEXTURE_2D, state.texture2DEnabledOnTexture0);
        if (state.activeTexture != GL13.GL_TEXTURE0) {
            gl.activeTexture(state.activeTexture);
            gl.bindTexture2d(state.textureBinding2DOnActiveTexture);
            gl.setEnabled(GL11.GL_TEXTURE_2D, state.texture2DEnabledOnActiveTexture);
        }
        gl.activeTexture(state.activeTexture);
        gl.bindVertexArray(state.vertexArrayBinding);
        gl.bindBuffer(GL15.GL_ARRAY_BUFFER, state.arrayBufferBinding);
        gl.bindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, state.elementArrayBufferBinding);

        gl.viewport(state.viewport[0], state.viewport[1], state.viewport[2], state.viewport[3]);
        if (state.matrixStateSaved) {
            gl.matrixMode(state.currentMatrixMode);
        }
    }

    /**
     * 在保护的 OpenGL 状态边界中执行任务。
     *
     * @param task 要执行的任务
     */
    public void run(Runnable task) {
        Objects.requireNonNull(task, "task");
        push(true);
        try {
            task.run();
        } finally {
            pop();
        }
    }

    /**
     * 在保护的 OpenGL 状态边界中执行任务，可跳过矩阵栈保存。
     *
     * @param task 要执行的任务
     * @param includeMatrixState 是否同时保存固定管线矩阵栈
     */
    public void run(Runnable task, boolean includeMatrixState) {
        Objects.requireNonNull(task, "task");
        push(includeMatrixState);
        try {
            task.run();
        } finally {
            pop();
        }
    }

    private void pushMatrixStack(int matrixMode) {
        gl.matrixMode(matrixMode);
        gl.pushMatrix();
    }

    private void popMatrixStack(int matrixMode) {
        gl.matrixMode(matrixMode);
        gl.popMatrix();
    }

    /** 生产 LWJGL2 状态访问器；查询缓冲跨调用复用。 */
    private static final class LwjglGlAccess implements GlAccess {

        private final java.nio.IntBuffer integers = java.nio.ByteBuffer.allocateDirect(4 * Integer.BYTES)
                .order(java.nio.ByteOrder.nativeOrder())
                .asIntBuffer();

        @Override
        public void pushAttrib(int mask) {
            GL11.glPushAttrib(mask);
        }

        @Override
        public void pushClientAttrib(int mask) {
            GL11.glPushClientAttrib(mask);
        }

        @Override
        public void popAttrib() {
            GL11.glPopAttrib();
        }

        @Override
        public void popClientAttrib() {
            GL11.glPopClientAttrib();
        }

        @Override
        public int getInteger(int name) {
            return GL11.glGetInteger(name);
        }

        @Override
        public void readIntegers(int name, int[] target) {
            integers.clear();
            GL11.glGetInteger(name, integers);
            for (int index = 0; index < target.length; index++) {
                target[index] = integers.get(index);
            }
        }

        @Override
        public boolean isEnabled(int capability) {
            return GL11.glIsEnabled(capability);
        }

        @Override
        public void setEnabled(int capability, boolean enabled) {
            if (enabled) {
                GL11.glEnable(capability);
            } else {
                GL11.glDisable(capability);
            }
        }

        @Override
        public void matrixMode(int mode) {
            GL11.glMatrixMode(mode);
        }

        @Override
        public void pushMatrix() {
            GL11.glPushMatrix();
        }

        @Override
        public void popMatrix() {
            GL11.glPopMatrix();
        }

        @Override
        public void activeTexture(int unit) {
            GL13.glActiveTexture(unit);
        }

        @Override
        public void bindTexture2d(int texture) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        }

        @Override
        public void useProgram(int program) {
            GL20.glUseProgram(program);
        }

        @Override
        public void bindVertexArray(int vertexArray) {
            GL30.glBindVertexArray(vertexArray);
        }

        @Override
        public void bindBuffer(int target, int buffer) {
            GL15.glBindBuffer(target, buffer);
        }

        @Override
        public void viewport(int x, int y, int width, int height) {
            GL11.glViewport(x, y, width, height);
        }
    }
}
