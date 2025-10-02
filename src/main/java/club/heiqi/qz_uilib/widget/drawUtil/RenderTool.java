package club.heiqi.qz_uilib.widget.drawUtil;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class RenderTool {
    public static Logger LOG = LogManager.getLogger();
    public static RenderTool instance;

    public static RenderTool getInstance() {
        if (instance == null) {
            instance = new RenderTool();
        }
        return instance;
    }

    public int vbo, tbo, cbo, ebo;

    public RenderTool() {
        init();
    }

    /**
     * 初始化 VBO、TBO、CBO、EBO。
     * 在固定管线中，不需要设置 Attribute Pointer。
     */
    public void init() {
        // 仅检查 VBO, TBO, CBO, EBO
        if (vbo == 0 || tbo == 0 || cbo == 0 || ebo == 0) {
            // 在固定管线中，我们只创建 VBO 来存储数据
            // 不创建 VAO (GL30.glGenVertexArrays)

            // 顶点 (位置) VBO
            vbo = GL15.glGenBuffers();

            // 纹理坐标 TBO
            tbo = GL15.glGenBuffers();

            // 颜色 CBO
            cbo = GL15.glGenBuffers();

            // 索引 EBO
            ebo = GL15.glGenBuffers();
        }

        // 固定管线不需要 ShaderManager
        /*
        if (shaderManager == null) {
            // ...
        }
        */
    }

    private static final int ONE_MB_ELEMENTS = 1048576 / 4;
    private FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(ONE_MB_ELEMENTS);  // 初始化1MB
    private FloatBuffer texCoordBuffer = BufferUtils.createFloatBuffer(ONE_MB_ELEMENTS);
    private FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(ONE_MB_ELEMENTS);
    private IntBuffer indexBuffer = BufferUtils.createIntBuffer(ONE_MB_ELEMENTS);
    /**
     * 检查并扩容缓冲区。如果当前容量不足以容纳所需数据，将按 1MB 的步长逐次扩容。
     * @param currentBuffer 当前缓冲区。
     * @param requiredSize 目标数组的长度（所需容量）。
     * @param bufferName 缓冲区名称，用于输出。
     * @return 扩容或保持不变后的新缓冲区。
     */
    private FloatBuffer checkAndResizeBuffer(FloatBuffer currentBuffer, int requiredSize, String bufferName) {
        if (requiredSize > currentBuffer.capacity()) {
            int currentCapacity = currentBuffer.capacity();
            int newCapacity = currentCapacity;

            LOG.warn("⚠️ **注意：** " + bufferName + " 缓冲区容量不足！");
            LOG.warn("    - 当前容量（元素数）：" + currentCapacity);
            LOG.warn("    - 所需容量（元素数）：" + requiredSize);
            LOG.warn("    - 扩容步进（1MB 元素数）：" + ONE_MB_ELEMENTS);

            // 使用 while 循环逐次增加容量直到满足 requiredSize
            while (newCapacity < requiredSize) {
                newCapacity += ONE_MB_ELEMENTS;
            }

            LOG.warn("    - 扩容后新容量（元素数）：" + newCapacity);
            LOG.warn("    - **正在重新创建缓冲区...**");

            FloatBuffer newBuffer = BufferUtils.createFloatBuffer(newCapacity);

            return newBuffer;
        }
        return currentBuffer;
    }

    /**
     * 检查并扩容缓冲区（IntBuffer 版本）。如果当前容量不足以容纳所需数据，将按 1MB 的步长逐次扩容。
     */
    private IntBuffer checkAndResizeBuffer(IntBuffer currentBuffer, int requiredSize, String bufferName) {
        if (requiredSize > currentBuffer.capacity()) {
            int currentCapacity = currentBuffer.capacity();
            int newCapacity = currentCapacity;

            LOG.warn("⚠️ **注意：** " + bufferName + " 缓冲区容量不足！");
            LOG.warn("    - 当前容量（元素数）：" + currentCapacity);
            LOG.warn("    - 所需容量（元素数）：" + requiredSize);
            LOG.warn("    - 扩容步进（1MB 元素数）：" + ONE_MB_ELEMENTS);

            // 使用 while 循环逐次增加容量直到满足 requiredSize
            while (newCapacity < requiredSize) {
                newCapacity += ONE_MB_ELEMENTS;
            }

            LOG.warn("    - 扩容后新容量（元素数）：" + newCapacity);
            LOG.warn("    - **正在重新创建缓冲区...**");

            IntBuffer newBuffer = BufferUtils.createIntBuffer(newCapacity);
            return newBuffer;
        }
        return currentBuffer;
    }
    /**
     * 渲染方法，使用固定管线和 VBO (Vertex Buffer Object)。
     * * @param vertex 顶点位置数据 (x, y, z)
     * @param uv 纹理坐标数据 (u, v)
     * @param color 颜色数据 (r, g, b, a)
     * @param index 索引数据
     */
    public void render(float[] vertex, float[] uv, float[] color, int[] index) {
        // 清除缓冲区以便写入新数据
        vertexBuffer.clear();
        texCoordBuffer.clear();
        colorBuffer.clear();
        indexBuffer.clear();

        // 固定管线：设置状态和矩阵
        // setUniform(); // 固定管线使用 GL11/GL12/GL14 的矩阵函数，而非 shaderManager.setUniform

        // --- 数据上传到 VBOs ---

        // 1. 顶点 (位置) VBO
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        vertexBuffer = checkAndResizeBuffer(vertexBuffer, vertex.length, "顶点");
        vertexBuffer.put(vertex).flip();
        // 上传数据
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBuffer, GL15.GL_DYNAMIC_DRAW);
        // 使用 GL11.glVertexPointer 告诉 OpenGL 数据在缓冲区中的布局
        GL11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0L); // 3个float, 无步进 (0), 偏移 (0L)

        // 2. 纹理坐标 TBO
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tbo);
        texCoordBuffer = checkAndResizeBuffer(texCoordBuffer, uv.length, "纹理坐标");
        texCoordBuffer.put(uv).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, texCoordBuffer, GL15.GL_DYNAMIC_DRAW);
        // 使用 GL11.glTexCoordPointer 告诉 OpenGL 纹理坐标数据在缓冲区中的布局
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 0, 0L); // 2个float, 无步进 (0), 偏移 (0L)

        // 3. 颜色 CBO
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, cbo);
        colorBuffer = checkAndResizeBuffer(colorBuffer, color.length, "颜色");
        colorBuffer.put(color).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, colorBuffer, GL15.GL_DYNAMIC_DRAW);
        // 使用 GL11.glColorPointer 告诉 OpenGL 颜色数据在缓冲区中的布局
        GL11.glColorPointer(4, GL11.GL_FLOAT, 0, 0L); // 4个float (R G B A), 无步进 (0), 偏移 (0L)

        // 4. 索引 EBO
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        indexBuffer = checkAndResizeBuffer(indexBuffer, index.length, "索引");
        indexBuffer.put(index).flip();
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15.GL_DYNAMIC_DRAW);

        // --- 启用固定管线的数组状态 ---

        // 启用顶点数组
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        // 启用纹理坐标数组
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        // 启用颜色数组
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);

        // --- 绘制 ---

        // 使用索引进行绘制
        GL11.glDrawElements(GL11.GL_TRIANGLES, index.length, GL11.GL_UNSIGNED_INT, 0L); // 0L 表示数据从 EBO 绑定缓冲区的起始位置开始

        // --- 禁用固定管线的数组状态 ---

        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);

        // --- 解绑 VBOs ---

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        // 固定管线中不需要解绑 VAO (GL30.glBindVertexArray(0);)
    }
}
