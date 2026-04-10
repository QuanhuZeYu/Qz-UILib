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
    private int positionBuffer;
    private int uvBuffer;
    private int colorBuffer;
    private int uvBoundsBuffer;
    private int glyphFlagsBuffer;
    private int indexBuffer;

    /**
     * 初始化底层绘制资源。
     */
    public void initialize() {
        if (vao != 0) {
            return;
        }

        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        positionBuffer = GL15.glGenBuffers();
        uvBuffer = GL15.glGenBuffers();
        colorBuffer = GL15.glGenBuffers();
        uvBoundsBuffer = GL15.glGenBuffers();
        glyphFlagsBuffer = GL15.glGenBuffers();
        indexBuffer = GL15.glGenBuffers();

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionBuffer);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0L);
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, uvBuffer);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 0, 0L);
        GL20.glEnableVertexAttribArray(1);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, colorBuffer);
        GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false, 0, 0L);
        GL20.glEnableVertexAttribArray(2);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, uvBoundsBuffer);
        GL20.glVertexAttribPointer(3, 4, GL11.GL_FLOAT, false, 0, 0L);
        GL20.glEnableVertexAttribArray(3);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, glyphFlagsBuffer);
        GL20.glVertexAttribPointer(4, 1, GL11.GL_FLOAT, false, 0, 0L);
        GL20.glEnableVertexAttribArray(4);

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * 提交缓冲区并执行绘制。
     *
     * @param vertexData 顶点缓冲
     * @param uvData 纹理坐标缓冲
     * @param colorData 颜色缓冲
     * @param uvBoundsData UV 边界缓冲
     * @param glyphFlagsData 字形标记缓冲
     * @param indexData 索引缓冲
     * @param indexCount 索引数量
     */
    public void render(
            FloatBuffer vertexData,
            FloatBuffer uvData,
            FloatBuffer colorData,
            FloatBuffer uvBoundsData,
            FloatBuffer glyphFlagsData,
            IntBuffer indexData,
            int indexCount) {
        initialize();
        GL30.glBindVertexArray(vao);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexData, GL15.GL_DYNAMIC_DRAW);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, uvBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, uvData, GL15.GL_DYNAMIC_DRAW);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, colorBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, colorData, GL15.GL_DYNAMIC_DRAW);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, uvBoundsBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, uvBoundsData, GL15.GL_DYNAMIC_DRAW);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, glyphFlagsBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, glyphFlagsData, GL15.GL_DYNAMIC_DRAW);

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexData, GL15.GL_DYNAMIC_DRAW);

        GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0L);

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }
}
