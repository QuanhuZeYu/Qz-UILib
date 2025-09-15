package club.heiqi.qz_uilib.client;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

public class MatrixUtils {
    public static FloatBuffer orthoBuf = BufferUtils.createFloatBuffer(16);

    public static void setDisplaySizeProjection() {
        float w2 = (float) Display.getWidth() /2;
        float h2 = (float) Display.getHeight() /2;
        Matrix4f orthoMat = new Matrix4f().identity().ortho(-w2,w2,-h2,h2,-1000,1000);
        orthoBuf.clear();
        orthoBuf.put(orthoMat.get(new float[16])).flip();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadMatrix(orthoBuf);
        // // 设置正交投影（参数：左、右、下、上、近、远）
        // GL11.glMatrixMode(GL11.GL_PROJECTION);
        // GL11.glLoadIdentity();
        // GL11.glOrtho(0, Display.getWidth(), Display.getHeight(), 0, -1, 1); // 原点在左上角

        // 切回模型视图矩阵
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }
}
