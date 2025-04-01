package club.heiqi.qz_uilib.shader;

import club.heiqi.qz_uilib.skija.state.SkiaStore;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL15.glBufferData;

public class FullScreenQuad {
    public static final float[] VERTICES = {
            // 位置     纹理坐标
            -1.0f,  1.0f, 0.0f, 1.0f, // 左上
            -1.0f, -1.0f, 0.0f, 0.0f, // 左下
            1.0f, -1.0f, 1.0f, 0.0f, // 右下
            1.0f,  1.0f, 1.0f, 1.0f  // 右上
    };

    private static int vaoID = -1;
    private static int vboID = -1;

    static {
        init();
    }

    public static void init() {
        if (vaoID != -1) return;

        // 创建缓冲对象
        vaoID = GL30.glGenVertexArrays();
        vboID = GL15.glGenBuffers();

        // 转换为NIO Buffer
        FloatBuffer buffer = BufferUtils.createFloatBuffer(VERTICES.length);
        buffer.put(VERTICES);
        buffer.flip();

        // 配置VAO
        GL30.glBindVertexArray(vaoID);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
        glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);

        // 坐标属性 (位置)
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);

        // 纹理坐标属性
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);

        // 显式解绑
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    public static void render() {
        GL30.glBindVertexArray(vaoID);
        try {
            SkiaStore.glBindVertexArray.invoke(vaoID);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
        GL30.glBindVertexArray(0);
        try {
            SkiaStore.glBindVertexArray.invoke(0);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void cleanup() {
        if (vaoID != -1) {
            GL30.glDeleteVertexArrays(vaoID);
            vaoID = -1;
        }
        if (vboID != -1) {
            GL15.glDeleteBuffers(vboID);
            vboID = -1;
        }
    }
}