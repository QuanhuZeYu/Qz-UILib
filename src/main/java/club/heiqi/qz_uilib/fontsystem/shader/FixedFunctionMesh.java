package club.heiqi.qz_uilib.fontsystem.shader;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * 固定功能管线下的 VBO/TBO/EBO 封装类。
 * 负责管理服务器端（GPU）的顶点和索引数据，并执行索引绘制。
 *
 * 数据结构假设：
 * - VBO (this.vboId): 仅包含顶点位置 [位置 (3f)]
 * - TBO (this.tboId): 仅包含纹理坐标 [纹理坐标 (2f)]
 * - EBO (this.eboId): 索引数据
 */
public class FixedFunctionMesh {
    public static FixedFunctionMesh mesh;

    // VBO (顶点位置) 和 EBO (索引) 的 GPU 句柄
    private final int vboId;
    private final int eboId;
    // 新增 TBO (纹理坐标) 的 GPU 句柄
    private final int tboId;

    // 渲染参数
    private int indexCount;

    // 定义顶点数据的步长 (Stride) 和偏移量 (Offset)
    // 因为数据是分离的，步长现在是紧凑的 (Packed)
    private static final int TEXTURE_COMPONENTS = 2; // 纹理坐标分量数


    /**
     * 构造函数：初始化并生成 VBO, TBO 和 EBO 句柄。
     */
    public FixedFunctionMesh() {
        // 使用 GL15（OpenGL 1.5功能）生成缓冲区句柄
        this.vboId = GL15.glGenBuffers();
        this.eboId = GL15.glGenBuffers();
        // 初始化 TBO
        this.tboId = GL15.glGenBuffers();
        this.indexCount = 0;
    }

    public static FixedFunctionMesh getInstance() {
        if (mesh == null) {
            mesh = new FixedFunctionMesh();
        }
        return mesh;
    }

    /**
     * 初始化网格数据：上传**顶点**、**纹理坐标**和**索引**数据到 GPU。
     *
     * @param vertices 包含顶点位置数据的 FloatBuffer
     * @param textureCoords 包含纹理坐标数据的 FloatBuffer
     * @param indices 包含索引数据的 ShortBuffer
     */
    public void createMesh(FloatBuffer vertices, FloatBuffer textureCoords, ShortBuffer indices) {
        // 1. 上传顶点数据 (VBO)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        // 2. 上传纹理坐标数据 (TBO)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.tboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, textureCoords, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0); // 解绑 TBO

        // 3. 上传索引数据 (EBO)
        this.indexCount = indices.limit();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, this.eboId);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * 更新 VBO (顶点) 数据。
     *
     * @param newVertices 包含新顶点数据的 FloatBuffer
     */
    public FixedFunctionMesh updateVertices(FloatBuffer newVertices) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.vboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, newVertices, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        return this;
    }

    /**
     * **新增**：更新 TBO (纹理坐标) 数据。
     *
     * @param newTextureCoords 包含新纹理坐标数据的 FloatBuffer
     */
    public FixedFunctionMesh updateTextureCoords(FloatBuffer newTextureCoords) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.tboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, newTextureCoords, GL15.GL_DYNAMIC_DRAW);
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

        // 1. 绑定 VBO TBO EBO

        // 2. 启用客户端状态
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

        // 3. 配置顶点位置指针
        // 绑定 VBO 后，步长是 VBO_STRIDE_IN_BYTES (12 bytes)，偏移量为 0
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL11.glVertexPointer(
                3,                          // size: 3
                GL11.GL_FLOAT,              // type: GL_FLOAT
                Float.BYTES*3,              // stride: 12 bytes (紧凑存储)
                0L                          // offset: 0L
        );

        // 3. 配置纹理坐标指针
        // 绑定 TBO 后，步长是 TBO_STRIDE_IN_BYTES (8 bytes)，偏移量为 0
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tboId);
        GL11.glTexCoordPointer(
                2,                          // size: 2
                GL11.GL_FLOAT,              // type: GL_FLOAT
                Float.BYTES*2,              // stride: 8 bytes (紧凑存储)
                0L                          // offset: 0L
        );

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);

        // 4. 执行索引绘制
        GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_SHORT, 0L);

        // 5. 清理状态
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * 清理方法：释放 GPU 内存资源。
     */
    public void cleanUp() {
        GL15.glDeleteBuffers(this.vboId);
        GL15.glDeleteBuffers(this.tboId); // 删除 TBO
        GL15.glDeleteBuffers(this.eboId);
    }
}