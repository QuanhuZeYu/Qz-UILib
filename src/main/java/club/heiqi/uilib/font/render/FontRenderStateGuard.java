package club.heiqi.uilib.font.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
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
 */
public class FontRenderStateGuard implements FontRenderStateExecutor {

    private static final class SavedState {

        private final boolean matrixStateSaved;
        private final int[] viewport = new int[4];
        private int activeTexture;
        private int currentProgram;
        private int currentMatrixMode;
        private int textureBinding2DOnTexture0;
        private int textureBinding2DOnActiveTexture;
        private int vertexArrayBinding;
        private int arrayBufferBinding;
        private int elementArrayBufferBinding;

        private SavedState(boolean matrixStateSaved) {
            this.matrixStateSaved = matrixStateSaved;
        }
    }

    private final IntBuffer viewportBuffer = ByteBuffer.allocateDirect(16 * Integer.BYTES)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer();
    private final Deque<SavedState> savedStates = new ArrayDeque<SavedState>();

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
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
        if (includeMatrixState) {
            state.currentMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            pushMatrixStack(GL11.GL_MODELVIEW);
            pushMatrixStack(GL11.GL_PROJECTION);
            pushMatrixStack(GL11.GL_TEXTURE);
        }

        state.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        state.currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        state.textureBinding2DOnTexture0 = getTextureBinding(GL13.GL_TEXTURE0);
        state.textureBinding2DOnActiveTexture = state.activeTexture == GL13.GL_TEXTURE0
                ? state.textureBinding2DOnTexture0
                : getTextureBinding(state.activeTexture);
        GL13.glActiveTexture(state.activeTexture);
        state.vertexArrayBinding = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        state.arrayBufferBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        state.elementArrayBufferBinding = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        viewportBuffer.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
        for (int index = 0; index < state.viewport.length; index++) {
            state.viewport[index] = viewportBuffer.get(index);
        }
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
            GL11.glMatrixMode(state.currentMatrixMode);
        }
        GL11.glPopClientAttrib();
        GL11.glPopAttrib();

        GL20.glUseProgram(state.currentProgram);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, state.textureBinding2DOnTexture0);
        if (state.activeTexture != GL13.GL_TEXTURE0) {
            GL13.glActiveTexture(state.activeTexture);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, state.textureBinding2DOnActiveTexture);
        }
        GL13.glActiveTexture(state.activeTexture);
        GL30.glBindVertexArray(state.vertexArrayBinding);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, state.arrayBufferBinding);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, state.elementArrayBufferBinding);

        GL11.glViewport(state.viewport[0], state.viewport[1], state.viewport[2], state.viewport[3]);
        if (state.matrixStateSaved) {
            GL11.glMatrixMode(state.currentMatrixMode);
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

    private int getTextureBinding(int textureUnit) {
        GL13.glActiveTexture(textureUnit);
        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    private void pushMatrixStack(int matrixMode) {
        GL11.glMatrixMode(matrixMode);
        GL11.glPushMatrix();
    }

    private void popMatrixStack(int matrixMode) {
        GL11.glMatrixMode(matrixMode);
        GL11.glPopMatrix();
    }
}
