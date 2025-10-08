package club.heiqi.qz_uilib.fontsystem.shader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**通用型330渲染器*/
public class CommonMesh {
    public static Logger LOG = LogManager.getLogger();
    public static CommonMesh instance;

    public int vaoID, vboVerticesID, vboTexCoordsID, eboID;
    // 绘制时所需信息
    public int indexCount = 0;

    public CommonMesh() {
        setupBuffers();
    }

    public static CommonMesh getInstance() {
        if (instance == null) {
            instance = new CommonMesh();
        }
        return instance;
    }

    /**
     * 初始化 VAO, VBOs 和 EBO 并设置顶点属性指针。
     */
    private void setupBuffers() {
        // 1. 创建 VAO
        vaoID = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoID);

        // 2. 创建 VBOs
        vboVerticesID = GL15.glGenBuffers();
        vboTexCoordsID = GL15.glGenBuffers();

        // 3. 创建 EBO
        eboID = GL15.glGenBuffers();

        // --- 顶点位置 VBO (Attribute Location 0) ---
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboVerticesID);
        // 预分配一个初始空间 (例如，空数据，GL_DYNAMIC_DRAW 表示数据会经常更新)
        // 实际应用中可以根据预估大小分配
        // GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) 0, GL15.GL_DYNAMIC_DRAW);
        // 设置 Attribute Pointer: location 0, 3 components (x, y, z), float, stride 0 (紧密排列)
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);

        // --- 纹理坐标 VBO (Attribute Location 1) ---
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboTexCoordsID);
        // 预分配一个初始空间
        // GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) 0, GL15.GL_DYNAMIC_DRAW);
        // 设置 Attribute Pointer: location 1, 2 components (u, v), float, stride 0
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 0, 0);

        // EBO (暂时不绑定数据，等到 updateIndex 时再绑定)
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboID);
        // GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, (long) 0, GL15.GL_DYNAMIC_DRAW);


        // 解除绑定
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public CommonMesh updateVertex(FloatBuffer data) {
        GL30.glBindVertexArray(vaoID);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboVerticesID);
        // 将新的数据上传到 VBO。使用 GL_DYNAMIC_DRAW
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_DYNAMIC_DRAW);

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return this;
    }

    public CommonMesh updateTexCoord(FloatBuffer data) {
        GL30.glBindVertexArray(vaoID);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboTexCoordsID);
        // 将新的数据上传到 VBO
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_DYNAMIC_DRAW);

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return this;
    }

    public CommonMesh updateIndex(IntBuffer data) {
        // 更新 indexCount
        indexCount = data.limit();

        GL30.glBindVertexArray(vaoID);

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboID);
        // 将新的数据上传到 EBO
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, data, GL15.GL_DYNAMIC_DRAW);

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        return this;
    }

    public void draw() {
        if (indexCount == 0) {
            LOG.error("Warning: Index count is zero, nothing to draw.");
            return;
        }

        // 绑定 VAO
        GL30.glBindVertexArray(vaoID);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboVerticesID);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboTexCoordsID);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboID);

        // VAO 内部已经记录了 VBOs, EBO 的绑定和属性设置
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
        // 绘制元素
        GL11.glDrawElements(GL11.GL_TRIANGLES, // 绘制模式
                indexCount,      // 绘制的索引数量
                GL11.GL_UNSIGNED_INT, // 索引的数据类型 (IntBuffer对应 UNSIGNED_INT)
                0L);             // EBO 中的偏移量 (从开头开始)
        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        // 解除绑定
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public void cleanUp() {
        GL15.glDeleteBuffers(vboVerticesID);
        GL15.glDeleteBuffers(vboTexCoordsID);
        GL15.glDeleteBuffers(eboID);
        GL30.glDeleteVertexArrays(vaoID);
    }
}
