package club.heiqi.qz_uilib.widget.drawUtil;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

/**
 * 固定功能管线下的 VBO/EBO 封装类。
 * 负责管理服务器端（GPU）的顶点和索引数据，并执行索引绘制。
 *
 * 默认假设使用交错（Interleaved）顶点数据：
 * [位置 (3f) | 法线 (3f) | 纹理坐标 (2f)]，总跨度为 8 * 4 = 32 字节。
 */
public class FixedFunctionMesh {
    public static FixedFunctionMesh mesh;

    // VBO 和 EBO 的 GPU 句柄
    private final int vboId;
    private final int eboId;

    // 渲染参数
    private int indexCount;

    /**
     * 构造函数：初始化并生成 VBO 和 EBO 句柄。
     */
    public FixedFunctionMesh() {
        // 使用 GL15（OpenGL 1.5功能）生成缓冲区句柄
        this.vboId = GL15.glGenBuffers();
        this.eboId = GL15.glGenBuffers();
        this.indexCount = 0;
    }

    public static FixedFunctionMesh getInstance() {
        if (mesh == null) {
            mesh = new FixedFunctionMesh();
        }
        return mesh;
    }

    /**
     * 初始化网格数据：上传顶点数据和索引数据到 GPU。
     *
     * @param vertices 包含交错顶点数据的 FloatBuffer
     * @param indices 包含索引数据的 ShortBuffer
     */
    public void createMesh(FloatBuffer vertices, ShortBuffer indices) {
        // 1. 上传顶点数据 (VBO)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboId);
        // 使用 GL_DYNAMIC_DRAW，表示数据可能经常更新 [1]
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0); // 解绑 VBO

        // 2. 上传索引数据 (EBO)
        this.indexCount = indices.limit(); // 记录索引数量供 glDrawElements 使用
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, this.eboId);
        // 索引数据也使用 GL_DYNAMIC_DRAW 以便后续更新
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_DYNAMIC_DRAW);
        // 注意：在固定管线中，EBO 绑定状态不在 VAO 中存储，
        // 尽管可以解绑 EBO，但必须在每次绘制前重新绑定 [2]。
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * 更新 VBO (顶点) 数据。
     * 如果新缓冲区大小与旧 VBO 相同，此操作将替换数据；否则将重新分配存储。
     *
     * @param newVertices 包含新顶点数据的 FloatBuffer
     */
    public FixedFunctionMesh updateVertices(FloatBuffer newVertices) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboId);
        // 使用 glBufferData 重新上传数据。如果缓冲区大小不变，这很高效 [Implicit].
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, newVertices, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return this;
    }

    /**
     * 更新 EBO (索引) 数据。
     *
     * @param newIndices 包含新索引数据的 ShortBuffer
     */
    public FixedFunctionMesh updateIndices(ShortBuffer newIndices) {
        this.indexCount = newIndices.limit();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, this.eboId);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, newIndices, GL15.GL_DYNAMIC_DRAW);
        // 绘制前会重新绑定，这里解绑保持状态干净
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        return this;
    }

    /**
     * 核心绘制方法：配置指针并调用 glDrawElements。
     */
    public void draw() {
        if (this.indexCount == 0) {
            return;
        }

        // 1. 绑定 VBO 和 EBO
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboId);
        // 必须在 glDrawElements 前绑定 EBO，否则会导致 EXCEPTION_ACCESS_VIOLATION [2]
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, this.eboId);

        // 2. 启用和配置客户端状态（固定管线模式）

        // 启用顶点数组状态
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);

        // 设置指针：最后一个参数是相对于绑定 VBO 的字节偏移量 [1, 3]
        // 顶点位置 (3 float)
        GL11.glVertexPointer(3, GL11.GL_FLOAT, Float.BYTES*3, 0);

        // 3. 执行索引绘制
        // 0L 表示从绑定 EBO 的起始位置开始读取索引
        GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_SHORT, 0L);

        // 4. 清理状态
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * 清理方法：释放 GPU 内存资源。
     */
    public void cleanUp() {
        GL15.glDeleteBuffers(this.vboId);
        GL15.glDeleteBuffers(this.eboId);
    }


}