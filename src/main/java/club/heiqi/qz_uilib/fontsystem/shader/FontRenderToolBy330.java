package club.heiqi.qz_uilib.fontsystem.shader;

import club.heiqi.qz_uilib.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class FontRenderToolBy330 {
    public static Logger LOG = LogManager.getLogger();
    public static FontRenderToolBy330 instance;

    public static FontRenderToolBy330 getInstance() {
        if (instance == null) {
            instance = new FontRenderToolBy330();
        }
        return instance;
    }

    public ShaderManager shaderManager;
    public int vao, vbo, tbo, cbo, ebo;

    public FontRenderToolBy330() {
        init();
    }

    public void init() {
        // vao == 0 || vbo == 0 || ebo == 0 增加 cbo != 0 的检查
        if (vao == 0 || vbo == 0 || tbo == 0 || cbo == 0 || ebo == 0) {
            vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao);

            // 顶点 (位置) VBO - 属性索引 0 (3个float: x, y, z)
            vbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);

            // 纹理坐标 TBO - 属性索引 1 (2个float: u, v)
            tbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tbo);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 0, 0);

            // **新增：颜色 CBO - 属性索引 2 (4个float: r, g, b, a)**
            cbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, cbo);
            GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false, 0, 0); // 颜色通常是 R G B A (4分量)

            // 索引 EBO
            ebo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);

            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        }
        if (shaderManager == null) {
            shaderManager = new ShaderManager().loadFromJar(
                    "shader/fontV.vert",
                    "shader/fontF.frag",
                    null);
        }
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
    public void render(FloatBuffer vertexBuffer, FloatBuffer texCoordBuffer, FloatBuffer colorBuffer, IntBuffer indexBuffer, int indexLength) {
        setUniform();

        GL30.glBindVertexArray(vao);

        // 1. 顶点 (位置)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBuffer, GL15.GL_DYNAMIC_DRAW);

        // 2. 纹理坐标
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, texCoordBuffer, GL15.GL_DYNAMIC_DRAW);

        // **3. 新增：颜色**
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, cbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, colorBuffer, GL15.GL_DYNAMIC_DRAW);

        // 4. 索引
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15.GL_DYNAMIC_DRAW);

        // 启用属性数组
        GL20.glEnableVertexAttribArray(0); // 顶点
        GL20.glEnableVertexAttribArray(1); // 纹理坐标
        GL20.glEnableVertexAttribArray(2); // **新增：颜色**

        GL11.glDrawElements(GL11.GL_TRIANGLES, indexLength, GL11.GL_UNSIGNED_INT, 0);

        // 禁用属性数组
        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
        GL20.glDisableVertexAttribArray(2); // **新增：颜色**

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }
    public void render(float[] vertex, float[] uv, float[] color, int[] index) {
        vertexBuffer.clear();
        texCoordBuffer.clear();
        colorBuffer.clear();
        indexBuffer.clear();

        // 1. 顶点 (位置)
        vertexBuffer = checkAndResizeBuffer(vertexBuffer, vertex.length, "顶点");
        vertexBuffer.put(vertex).flip();

        // 2. 纹理坐标
        texCoordBuffer = checkAndResizeBuffer(texCoordBuffer, uv.length, "纹理坐标");
        texCoordBuffer.put(uv).flip();

        // **3. 新增：颜色**
        colorBuffer = checkAndResizeBuffer(colorBuffer, color.length, "颜色");
        colorBuffer.put(color).flip();

        // 4. 索引
        indexBuffer = checkAndResizeBuffer(indexBuffer, index.length, "索引");
        indexBuffer.put(index).flip();

        render(vertexBuffer,texCoordBuffer,colorBuffer,indexBuffer,index.length);
    }

    private final FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    public void setUniform() {
        modelView.clear(); projection.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        modelView.flip(); projection.flip();

        shaderManager.setUniformM4f("modelview", new Matrix4f(modelView));
        shaderManager.setUniformM4f("projection", new Matrix4f(projection));

        shaderManager.setUniformF("sigma", (float) Config.sigma);
        shaderManager.setUniformF("blurRadius", (float) Config.blurRadius);
        shaderManager.setUniformI("sampleRadius", Config.sampleRadius);
        shaderManager.setUniformVec2("smoothRange", new Vector2f((float) Config.smoothRangeMin, (float) Config.smoothRangeMax));
    }
}
