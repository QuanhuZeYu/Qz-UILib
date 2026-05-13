package club.heiqi.uilib.font.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
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

    private final IntBuffer viewportBuffer = ByteBuffer.allocateDirect(16 * Integer.BYTES)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer();

    private int activeTexture;
    private int currentProgram;
    private int currentMatrixMode;
    private int textureBinding2DOnTexture0;
    private int textureBinding2DOnActiveTexture;
    private int vertexArrayBinding;
    private int arrayBufferBinding;
    private int elementArrayBufferBinding;

    /**
     * 保存当前 OpenGL 状态。
     */
    public void push() {
        currentMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        pushMatrixStack(GL11.GL_MODELVIEW);
        pushMatrixStack(GL11.GL_PROJECTION);
        pushMatrixStack(GL11.GL_TEXTURE);

        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        textureBinding2DOnTexture0 = getTextureBinding(GL13.GL_TEXTURE0);
        textureBinding2DOnActiveTexture = activeTexture == GL13.GL_TEXTURE0
                ? textureBinding2DOnTexture0
                : getTextureBinding(activeTexture);
        vertexArrayBinding = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        arrayBufferBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        elementArrayBufferBinding = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        viewportBuffer.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
        viewportBuffer.flip();
    }

    /**
     * 恢复之前保存的 OpenGL 状态。
     */
    public void pop() {
        popMatrixStack(GL11.GL_TEXTURE);
        popMatrixStack(GL11.GL_PROJECTION);
        popMatrixStack(GL11.GL_MODELVIEW);
        GL11.glMatrixMode(currentMatrixMode);
        GL11.glPopAttrib();

        GL20.glUseProgram(currentProgram);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBinding2DOnTexture0);
        if (activeTexture != GL13.GL_TEXTURE0) {
            GL13.glActiveTexture(activeTexture);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBinding2DOnActiveTexture);
        }
        GL13.glActiveTexture(activeTexture);
        GL30.glBindVertexArray(vertexArrayBinding);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBufferBinding);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementArrayBufferBinding);

        if (viewportBuffer.remaining() >= 4) {
            GL11.glViewport(viewportBuffer.get(0), viewportBuffer.get(1), viewportBuffer.get(2), viewportBuffer.get(3));
        }
        GL11.glMatrixMode(currentMatrixMode);
    }

    /**
     * 在保护的 OpenGL 状态边界中执行任务。
     *
     * @param task 要执行的任务
     */
    public void run(Runnable task) {
        Objects.requireNonNull(task, "task");
        push();
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
