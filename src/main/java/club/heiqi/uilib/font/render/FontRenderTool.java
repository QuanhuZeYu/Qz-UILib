package club.heiqi.uilib.font.render;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * 字体底层绘制工具。
 */
public class FontRenderTool {

    private int vao;
    private int vertexBuffer;
    private int indexBuffer;
    private int uploadedVertexBytes;
    private int uploadedIndexBytes;

    /**
     * 初始化底层绘制资源。
     */
    public void initialize() {
        if (vao != 0) {
            return;
        }

        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        vertexBuffer = GL15.glGenBuffers();
        indexBuffer = GL15.glGenBuffers();

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
        GL20.glVertexAttribPointer(0, GlyphRenderBatch.POSITION_COMPONENT_COUNT, GL11.GL_FLOAT, false,
                GlyphRenderBatch.VERTEX_STRIDE_BYTES, (long) GlyphRenderBatch.POSITION_OFFSET_BYTES);
        GL20.glEnableVertexAttribArray(0);

        GL20.glVertexAttribPointer(1, GlyphRenderBatch.UV_COMPONENT_COUNT, GL11.GL_FLOAT, false,
                GlyphRenderBatch.VERTEX_STRIDE_BYTES, (long) GlyphRenderBatch.UV_OFFSET_BYTES);
        GL20.glEnableVertexAttribArray(1);

        GL20.glVertexAttribPointer(2, GlyphRenderBatch.COLOR_COMPONENT_COUNT, GL11.GL_FLOAT, false,
                GlyphRenderBatch.VERTEX_STRIDE_BYTES, (long) GlyphRenderBatch.COLOR_OFFSET_BYTES);
        GL20.glEnableVertexAttribArray(2);

        GL20.glVertexAttribPointer(3, GlyphRenderBatch.UV_BOUNDS_COMPONENT_COUNT, GL11.GL_FLOAT, false,
                GlyphRenderBatch.VERTEX_STRIDE_BYTES, (long) GlyphRenderBatch.UV_BOUNDS_OFFSET_BYTES);
        GL20.glEnableVertexAttribArray(3);

        GL20.glVertexAttribPointer(4, GlyphRenderBatch.GLYPH_FLAGS_COMPONENT_COUNT, GL11.GL_FLOAT, false,
                GlyphRenderBatch.VERTEX_STRIDE_BYTES, (long) GlyphRenderBatch.GLYPH_FLAGS_OFFSET_BYTES);
        GL20.glEnableVertexAttribArray(4);

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * 提交缓冲区并执行绘制。
     *
     * @param vertexData interleaved 顶点缓冲
     * @param indexData 索引缓冲
     * @param indexCount 索引数量
     */
    public void render(FloatBuffer vertexData, IntBuffer indexData, int indexCount) {
        initialize();
        GL30.glBindVertexArray(vao);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
        uploadedVertexBytes = uploadBuffer(GL15.GL_ARRAY_BUFFER, vertexData, uploadedVertexBytes,
                vertexData.limit() * Float.BYTES);

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        uploadedIndexBytes = uploadBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexData, uploadedIndexBytes,
                indexData.limit() * Integer.BYTES);

        GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0L);

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * 释放底层 GL 资源。
     */
    public void dispose() {
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            vao = 0;
        }
        vertexBuffer = deleteBuffer(vertexBuffer);
        indexBuffer = deleteBuffer(indexBuffer);
        uploadedVertexBytes = 0;
        uploadedIndexBytes = 0;
    }

    private int uploadBuffer(int target, FloatBuffer data, int currentBytes, int requiredBytes) {
        if (requiredBytes > currentBytes) {
            GL15.glBufferData(target, data, GL15.GL_DYNAMIC_DRAW);
            return requiredBytes;
        }
        GL15.glBufferSubData(target, 0L, data);
        return currentBytes;
    }

    private int uploadBuffer(int target, IntBuffer data, int currentBytes, int requiredBytes) {
        if (requiredBytes > currentBytes) {
            GL15.glBufferData(target, data, GL15.GL_DYNAMIC_DRAW);
            return requiredBytes;
        }
        GL15.glBufferSubData(target, 0L, data);
        return currentBytes;
    }

    private int deleteBuffer(int bufferId) {
        if (bufferId != 0) {
            GL15.glDeleteBuffers(bufferId);
        }
        return 0;
    }
}
