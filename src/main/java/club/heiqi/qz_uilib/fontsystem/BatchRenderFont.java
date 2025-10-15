package club.heiqi.qz_uilib.fontsystem;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.client.FBO;
import club.heiqi.qz_uilib.fontsystem.shader.ShaderManager;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class BatchRenderFont {
    public static ShaderManager shaderManager;
    public static ShaderManager getShaderManagerInstance() {
        if (shaderManager == null) {
            shaderManager = new ShaderManager()
                    .loadFromJar("shader/fontV.vert","shader/fontF.frag", null);
        }
        return shaderManager;
    }

    // 每次扩容增加的元素数量，例如 256KB 的 float/int 空间
    public static final int DEFAULT_CAPACITY_INCREMENT = 1024 * 64;
    public int vertexCount = 0; // 记录已提交的逻辑顶点数量 (vertex.length / 3)
    // 新增：记录每个 Buffer 中实际写入的元素总数
    private int vertDataCount = 0;
    private int texCoordDataCount = 0;
    private int colorDataCount = 0;
    private int indexDataCount = 0;

    // 使用 NIO Buffers 存储原始数据，初始化时使用默认增量
    public FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(DEFAULT_CAPACITY_INCREMENT * 3);
    public FloatBuffer texCoordBuffer = BufferUtils.createFloatBuffer(DEFAULT_CAPACITY_INCREMENT * 2);
    public FloatBuffer colorBuffer = BufferUtils.createFloatBuffer(DEFAULT_CAPACITY_INCREMENT * 4);
    public IntBuffer indexBuffer = BufferUtils.createIntBuffer(DEFAULT_CAPACITY_INCREMENT * 6);

    /**
     * 检查并调整 Buffer 容量的方法。
     * 当 Buffer 的剩余空间不足以容纳新数据时，进行扩容。
     * * @param requiredVertFloats 顶点所需 float 数量 (x,y,z)
     * @param requiredTexFloats 纹理坐标所需 float 数量 (u,v)
     * @param requiredColorFloats 颜色所需 float 数量 (r,g,b,a)
     * @param requiredIndexInts 索引所需 int 数量
     */
    public void checkSize(int requiredVertFloats, int requiredTexFloats, int requiredColorFloats, int requiredIndexInts) {

        // --- 扩容核心方法 ---
        // 参数：1. 当前 Buffer 2. 扩容增量 3. 需求量
        FloatBuffer newVertex = resizeFloatBuffer(vertexBuffer, 3 * DEFAULT_CAPACITY_INCREMENT, requiredVertFloats);
        if (newVertex != vertexBuffer) vertexBuffer = newVertex;

        FloatBuffer newTexCoord = resizeFloatBuffer(texCoordBuffer, 2 * DEFAULT_CAPACITY_INCREMENT, requiredTexFloats);
        if (newTexCoord != texCoordBuffer) texCoordBuffer = newTexCoord;

        FloatBuffer newColor = resizeFloatBuffer(colorBuffer, 4 * DEFAULT_CAPACITY_INCREMENT, requiredColorFloats);
        if (newColor != colorBuffer) colorBuffer = newColor;

        IntBuffer newIndex = resizeIntBuffer(indexBuffer, 6 * DEFAULT_CAPACITY_INCREMENT, requiredIndexInts);
        if (newIndex != indexBuffer) indexBuffer = newIndex;
        // -----------------------
    }

    /** 辅助方法：float Buffer 扩容 (修改为使用 BufferUtils) */
    private FloatBuffer resizeFloatBuffer(FloatBuffer currentBuffer, int increment, int required) {
        if (currentBuffer.remaining() < required) {
            int newCapacity = currentBuffer.limit() + increment;
            while (newCapacity - currentBuffer.limit() < required) {
                newCapacity += increment;
            }

            // 1. 分配新的 Direct Buffer (关键修改)
            FloatBuffer newBuffer = BufferUtils.createFloatBuffer(newCapacity);

            // 2. 准备从旧 Buffer 读取数据：limit = position, position = 0
            currentBuffer.flip();

            // 3. 复制数据
            newBuffer.put(currentBuffer);

            // 4. 新 Buffer 保持在写入模式 (position 在复制数据末尾)
            System.out.println("Buffer resized from " + currentBuffer.limit() + " to " + newCapacity);
            return newBuffer;
        }
        return currentBuffer;
    }

    /** 辅助方法：int Buffer 扩容 (修改为使用 BufferUtils) */
    private IntBuffer resizeIntBuffer(IntBuffer currentBuffer, int increment, int required) {
        if (currentBuffer.remaining() < required) {
            int newCapacity = currentBuffer.limit() + increment;
            while (newCapacity - currentBuffer.limit() < required) {
                newCapacity += increment;
            }

            // 1. 分配新的 Direct Buffer (关键修改)
            IntBuffer newBuffer = BufferUtils.createIntBuffer(newCapacity);

            currentBuffer.flip();
            newBuffer.put(currentBuffer);

            System.out.println("Index Buffer resized from " + currentBuffer.limit() + " to " + newCapacity);
            return newBuffer;
        }
        return currentBuffer;
    }

    /**键为 纹理ID 值为数据收集指令*/
    public HashMap<CharPage, ArrayList<Runnable>> callRenders = new HashMap<>();

    public void collectRender(float x, float y, float charSize, CharPage page, CharInfo info, int inColor, boolean italic) {

        Runnable call = () -> {
            float u0 = (float)info.getU0(page.textureSize);
            float u1 = (float)info.getU1(page.textureSize);
            float v0 = (float)info.getV0(page.textureSize);
            float v1 = (float)info.getV1(page.textureSize);
            float alpha = (float) (inColor >> 24) / 255;
            float red = (float) ((inColor >> 16) & 255) / 255;
            float green = (float) ((inColor >> 8) & 255) / 255;
            float blue = (float) (inColor & 255) / 255;

            float[] vertex = new float[] {
                    italic ? x+2 : x, y, (float) Config.renderOffset,
                    x, y+charSize, (float) Config.renderOffset,
                    x+charSize, y+charSize, (float) Config.renderOffset,
                    italic ? x+charSize+2 : x+charSize, y, (float) Config.renderOffset
            };
            float[] texCoord = new float[] {
                    u0, v0, u0, v1, u1, v1, u1, v0,
            };
            float[] color = new float[] {
                    red, green, blue, alpha, red, green, blue, alpha,
                    red, green, blue, alpha, red, green, blue, alpha,
            };
            int[] index_offset = new int[] {
                    0, 1, 2, 2, 3, 0
            };

            int vertLen = vertex.length;
            int texLen = texCoord.length;
            int colorLen = color.length;
            int indexLen = index_offset.length;

            // 1. 确保 Buffer 有足够的空间
            this.checkSize(vertLen, texLen, colorLen, indexLen);

            // 2. 更新计数值
            int preVertexCount = this.vertexCount;
            this.vertexCount += vertLen / 3;

            this.vertDataCount += vertLen;
            this.texCoordDataCount += texLen;
            this.colorDataCount += colorLen;
            this.indexDataCount += indexLen;

            // 3. 将数组写入高性能 Buffer 中
            vertexBuffer.put(vertex);
            texCoordBuffer.put(texCoord);
            colorBuffer.put(color);

            // 4. 索引需要加上偏移量
            for (int i : index_offset) {
                indexBuffer.put(i + preVertexCount);
            }
        };
        callRenders.computeIfAbsent(page, k -> new ArrayList<>()).add(call);
    }

    public void flush() {
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        // 绑定着色器
        getShaderManagerInstance().bind();
        setUniform_PipeLine0();
        for (Map.Entry<CharPage, ArrayList<Runnable>> entry : callRenders.entrySet()) {

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, entry.getKey().textureID);
            shaderManager.setUniformI("mainTex", 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, entry.getKey().maskID);
            shaderManager.setUniformI("maskTex", 1);

            // 1. 执行所有渲染指令，将数据填充到 Buffers 中
            for (Runnable call : entry.getValue()) {
                call.run();
            }

            // 2. 准备 Buffers 进行读取 (Flip)
            vertexBuffer.flip();
            texCoordBuffer.flip();
            colorBuffer.flip();
            indexBuffer.flip();

            // 3. 渲染：使用 array() 获取底层数组，并使用 DataCount 限制有效数据长度
            FontRenderTool.getInstance().render(
                    vertexBuffer,
                    texCoordBuffer,
                    colorBuffer,
                    indexBuffer,
                    indexDataCount);

            // 4. 清理 Buffers 准备下一轮 (Clear)
            clean();
        }
        callRenders.clear();

        // 解绑着色器
        getShaderManagerInstance().unbind();

        // 恢复纹理
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
    }

    private final FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    public void setUniform_PipeLine0() {
        modelView.clear(); projection.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        modelView.flip(); projection.flip();

        getShaderManagerInstance().setUniformM4f("modelview", new Matrix4f(modelView));
        getShaderManagerInstance().setUniformM4f("projection", new Matrix4f(projection));
        getShaderManagerInstance().setUniformF("colorGain", (float) Config.colorGain);
        getShaderManagerInstance().setUniformF("alphaGain", (float) Config.alphaGain);
        getShaderManagerInstance().setUniformI("shrink", (int) Config.shrink);

        getShaderManagerInstance().setUniformVec2("textureSize", new Vector2f((float) (Config.awtCharSize * 64)));
        getShaderManagerInstance().setUniformF("sigma", (float) Config.sigma);
        getShaderManagerInstance().setUniformF("blurRadius", (float) 1);
        getShaderManagerInstance().setUniformI("sampleRadius", Config.sampleRadius);
    }

    public void clean() {
        // 重置 Buffer 的位置和限制，准备下一轮写入
        vertexBuffer.clear();
        texCoordBuffer.clear();
        colorBuffer.clear();
        indexBuffer.clear();

        // 重置所有计数器
        vertexCount = 0;
        vertDataCount = 0;
        texCoordDataCount = 0;
        colorDataCount = 0;
        indexDataCount = 0;
    }
}
