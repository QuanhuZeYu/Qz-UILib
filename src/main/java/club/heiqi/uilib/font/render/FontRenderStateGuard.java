package club.heiqi.uilib.font.render;

import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * 字体渲染状态保护器。
 */
public class FontRenderStateGuard {

    private final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);

    private int activeTexture;
    private int currentProgram;
    private int textureBinding2D;
    private int vertexArrayBinding;
    private int arrayBufferBinding;
    private int elementArrayBufferBinding;

    /**
     * 保存当前 OpenGL 状态。
     */
    public void push() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();

        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        textureBinding2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
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
        GL20.glUseProgram(currentProgram);
        GL13.glActiveTexture(activeTexture);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureBinding2D);
        GL30.glBindVertexArray(vertexArrayBinding);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBufferBinding);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementArrayBufferBinding);

        if (viewportBuffer.remaining() >= 4) {
            GL11.glViewport(viewportBuffer.get(0), viewportBuffer.get(1), viewportBuffer.get(2), viewportBuffer.get(3));
        }

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }
}
