package club.heiqi.qz_uilib.fontsystem;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.fontsystem.shader.ShaderManager;
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

public class FontRenderTool {
    public static Logger LOG = LogManager.getLogger();
    public static FontRenderTool instance;
    public static ShaderManager shaderManager;

    public static FontRenderTool getInstance() {
        if (instance == null) {
            instance = new FontRenderTool();
        }
        return instance;
    }

    public static ShaderManager getShaderManagerInstance() {
        if (shaderManager == null) {
            shaderManager = new ShaderManager()
                    .loadFromJar("shader/fontV.vert","shader/fontF.frag", null);
        }
        return shaderManager;
    }

    // VBO: 顶点缓冲对象 | EBO: 索引缓冲对象
    // VAO: 顶点数组对象，在现代OpenGL中必须使用
    public int vao, vbo, tbo, cbo, ebo;

    public FontRenderTool() {
        init();
    }

    /**
     * 初始化 VAO, VBO、TBO、CBO、EBO。
     */
    /**
     * 初始化 VAO, VBO、TBO、CBO、EBO，并设置属性指针和启用属性。
     */
    public void init() {
        // 检查 VAO, VBO, TBO, CBO, EBO
        if (vbo == 0 || tbo == 0 || cbo == 0 || ebo == 0 || vao == 0) {

            // 1. 创建 VAO
            vao = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vao); // 绑定 VAO，所有后续操作将记录到此 VAO

            // 2. 创建 VBOs
            vbo = GL15.glGenBuffers(); // 顶点 (位置) VBO
            tbo = GL15.glGenBuffers(); // 纹理坐标 TBO
            cbo = GL15.glGenBuffers(); // 颜色 CBO
            ebo = GL15.glGenBuffers(); // 索引 EBO

            // --- VBOs 绑定和属性设置 (一次性完成) ---

            // VBO 0: 顶点位置 (Location 0, 3分量)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0L);
            GL20.glEnableVertexAttribArray(0); // 启用属性！

            // VBO 1: 纹理坐标 (Location 1, 2分量)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tbo);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 0, 0L);
            GL20.glEnableVertexAttribArray(1); // 启用属性！

            // VBO 2: 颜色 (Location 2, 4分量)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, cbo);
            GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false, 0, 0L);
            GL20.glEnableVertexAttribArray(2); // 启用属性！

            // EBO 绑定 (索引)
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);

            // 解绑：解除 VAO 绑定，同时也会解除 EBO 的绑定（如果EBO绑定到了VAO），VBO 绑定也解除。
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            // GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0); // EBO 绑定状态已随 VAO 解除
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
    /**
     * 渲染方法，使用固定管线和 VBO (Vertex Buffer Object)。
     * * @param vertex 顶点位置数据 (x, y, z)
     * @param uv 纹理坐标数据 (u, v)
     * @param color 颜色数据 (r, g, b, a)
     * @param index 索引数据
     */
    /**
     * 渲染方法，使用可编程管线和 VAO/VBO。
     * @param vertex 顶点位置数据 (x, y, z)
     * @param uv 纹理坐标数据 (u, v)
     * @param color 颜色数据 (r, g, b, a)
     * @param index 索引数据
     */
    public void render(float[] vertex, float[] uv, float[] color, int[] index) {
        // ... (Buffer 清除和扩容代码保持不变) ...

        // 1. 激活着色器 (保留)
        int previousShader = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(shaderManager.shaderProgramID);

        // 2. 绑定 VAO
        GL30.glBindVertexArray(vao);

        // --- 数据上传到 VBOs (只需上传数据，不需要再设置属性指针) ---

        // 1. 顶点
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        vertexBuffer = checkAndResizeBuffer(vertexBuffer, vertex.length, "顶点");
        vertexBuffer.put(vertex).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBuffer, GL15.GL_DYNAMIC_DRAW);
        // !!! 删除：GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0L);
        // !!! 删除：GL20.glEnableVertexAttribArray(0);

        // 2. 纹理坐标
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tbo);
        texCoordBuffer = checkAndResizeBuffer(texCoordBuffer, uv.length, "纹理坐标");
        texCoordBuffer.put(uv).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, texCoordBuffer, GL15.GL_DYNAMIC_DRAW);
        // !!! 删除：GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 0, 0L);
        // !!! 删除：GL20.glEnableVertexAttribArray(1);

        // 3. 颜色
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, cbo);
        colorBuffer = checkAndResizeBuffer(colorBuffer, color.length, "颜色");
        colorBuffer.put(color).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, colorBuffer, GL15.GL_DYNAMIC_DRAW);
        // !!! 删除：GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false, 0, 0L);
        // !!! 删除：GL20.glEnableVertexAttribArray(2);

        // 4. 索引 EBO
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        indexBuffer = checkAndResizeBuffer(indexBuffer, index.length, "索引");
        indexBuffer.put(index).flip();
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15.GL_DYNAMIC_DRAW);

        // --- Uniform 设置和绘制 (保持不变) ---

        // --- 清理 ---

        // !!! 删除：GL20.glDisableVertexAttribArray(0);
        // !!! 删除：GL20.glDisableVertexAttribArray(1);
        // !!! 删除：GL20.glDisableVertexAttribArray(2);

        // 解绑
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0); // EBO 解绑
        GL20.glUseProgram(previousShader);
    }


    /**
     * 使用外部提供的 FloatBuffer 和 IntBuffer 中的数据进行渲染。
     * 此方法直接将外部 Buffer 内容上传到 VBOs/EBO，避免内部数据拷贝。
     *
     * ⚠️ 前提条件：
     * 1. 外部 Buffer 必须已经填充数据，并调用了 .flip()。
     * 2. VAO 必须已经配置好属性指针 (例如，通过调用 setupAttributes())。
     * * @param vertexData 包含顶点数据的 FloatBuffer (已flip)
     * @param uvData 包含纹理坐标数据的 FloatBuffer (已flip)
     * @param colorData 包含颜色数据的 FloatBuffer (已flip)
     * @param indexData 包含索引数据的 IntBuffer (已flip)
     * @param indexDataCount 要绘制的索引数量 (即 indexData.limit())
     */
    public void render(
            FloatBuffer vertexData,
            FloatBuffer uvData,
            FloatBuffer colorData,
            IntBuffer indexData,
            int indexDataCount) {

        // 1. 记录并激活着色器 (保留)
        int previousShader = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(shaderManager.shaderProgramID);

        // 2. 绑定 VAO (加载已配置的属性状态)
        GL30.glBindVertexArray(vao);

        // --- 数据更新到 VBOs (只更新数据) ---

        // 1. 顶点 VBO
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexData, GL15.GL_DYNAMIC_DRAW);

        // 2. 纹理坐标 TBO
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, uvData, GL15.GL_DYNAMIC_DRAW);

        // 3. 颜色 CBO
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, cbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, colorData, GL15.GL_DYNAMIC_DRAW);

        // 4. 索引 EBO
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexData, GL15.GL_DYNAMIC_DRAW);

        // --- 绘制 (保持不变) ---
        GL11.glDrawElements(GL11.GL_TRIANGLES, indexDataCount, GL11.GL_UNSIGNED_INT, 0L);

        // --- 清理 ---

        // 解绑
        GL30.glBindVertexArray(0);
        GL20.glUseProgram(previousShader);

        // 额外解绑 GL_ARRAY_BUFFER，避免影响后续操作
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private final FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    public void setUniform_PipeLine0() {
        modelView.clear(); projection.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        modelView.flip(); projection.flip();

        shaderManager.setUniformM4f("modelview", new Matrix4f(modelView));
        shaderManager.setUniformM4f("projection", new Matrix4f(projection));
        shaderManager.setUniformF("colorGain", (float) Config.colorGain);

        shaderManager.setUniformVec2("textureSize", new Vector2f((float) (Config.awtCharSize * 64)));
        shaderManager.setUniformF("sigma", (float) Config.sigma);
        shaderManager.setUniformF("blurRadius", (float) Config.blurRadius);
        shaderManager.setUniformI("sampleRadius", Config.sampleRadius);
        shaderManager.setUniformVec2("smoothRange", new Vector2f((float) Config.smoothRangeMin, (float) Config.smoothRangeMax));
    }
}
