package club.heiqi.qz_uilib.fontsystem.shader;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.client.FBO;
import club.heiqi.qz_uilib.client.RenderTickListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.lwjgl.opengl.*;

import java.util.concurrent.atomic.AtomicBoolean;

public class Bluer {
    public static Logger LOG = LogManager.getLogger();

    // 顶点坐标 (x, y) 对应于 GL11.glOrtho(0, 1, 1, 0)
    public static final float[] VERTICES = new float[]{
            0f, 1f, 0, // 左上
            1f, 1f, 0, // 右上
            1f, 0f, 0, // 右下
            0f, 0f, 0, // 左下
    };

    // 纹理坐标 (u, v)
    public static final float[] TEX_COORDS = new float[]{
            0.0f, 0.0f, // 左上
            1.0f, 0.0f, // 右上
            1.0f, 1.0f, // 右下
            0.0f, 1.0f, // 左下
    };
    public static final float[] COLOR = new float[] {
            1,1,1,1,
            1,1,1,1,
            1,1,1,1,
            1,1,1,1,
    };
    public static final int[] INDEX = new int[]{
            0,1,2,2,3,0
    };



    public FBO vertical;
    public FBO horizon;
    public ShaderManager shaderManager;
    public RenderToolVAO renderTool;

    public int quadVboId; // 顶点缓冲对象 (VBO)
    public int quadTcoId; // 纹理坐标缓冲对象 (可选，可以合并到 VBO)

    public Bluer(int width, int height) {
        vertical = new FBO(width, height).initByDefaultColorAndDepth();
        horizon = new FBO(width, height).initByDefaultColorAndDepth();
        shaderManager = new ShaderManager();
        shaderManager.setCustomLocation(() -> {
                    // 0 号位置绑定给 position
                    GL20.glBindAttribLocation(shaderManager.shaderProgramID, 0, "position");
                    // 1 号位置绑定给 texCoord
                    GL20.glBindAttribLocation(shaderManager.shaderProgramID, 1, "texCoord");
                    // 2 号位置绑定给 color
                    GL20.glBindAttribLocation(shaderManager.shaderProgramID, 2, "color");
                })
                .loadFromJar("shader/gaussV.vert","shader/gaussF.frag",null);
        renderTool = new RenderToolVAO();
    }

    public Bluer(int width, int height, int resultTexture) {
        vertical = new FBO(width, height).initByOutColorAndGenDepth(resultTexture);
        horizon = new FBO(width, height).initByDefaultColorAndDepth();
        shaderManager = new ShaderManager();
        shaderManager.setCustomLocation(() -> {
                    // 0 号位置绑定给 position
                    GL20.glBindAttribLocation(shaderManager.shaderProgramID, 0, "position");
                    // 1 号位置绑定给 texCoord
                    GL20.glBindAttribLocation(shaderManager.shaderProgramID, 1, "texCoord");
                    // 2 号位置绑定给 color
                    GL20.glBindAttribLocation(shaderManager.shaderProgramID, 2, "color");
                })
                .loadFromJar("shader/gaussV.vert","shader/gaussF.frag",null);
        renderTool = new RenderToolVAO();
    }

    public FBO blurTexture(int textureID, int width, int height, float blur) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // 激活着色器
        shaderManager.bind();
        shaderManager.setUniformF("radius", blur);

        // BufferedImage frameImage = FrameUtils.getTextureImage(textureID, width, height);
        // FrameUtils.debugSave(frameImage, "0");

        // --- 1. 水平模糊 (Horizontal Blur) ---
        horizon.bindAndRecordPreviousFBO();
        GL11.glClearColor(0,0,0,0);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        // 绑定输入纹理 (textureID)
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);
        // 设置 uniform 变量
        shaderManager.setUniformVec2("direction", new Vector2f(1.0f, 0.0f)); // 模糊方向: 水平 (1, 0)
        shaderManager.setUniformVec2("targetResolution", new Vector2f(horizon.width, horizon.height)); // FBO 尺寸
        drawQuad(horizon.width, horizon.height);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        // 解绑 horizon FBO
        horizon.unbindAndRestorePreviousFBO();
        // ---------------------------------

        // DEBUG 测试图像
        // BufferedImage frameImage1 = FrameUtils.getFrameImage(horizon.fboID, horizon.width, horizon.height);
        // FrameUtils.debugSave(frameImage1, "1");
        // --- 2. 垂直模糊 (Vertical Blur) ---

        vertical.bindAndRecordPreviousFBO();

        // 清除 FBO 内容
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        // 绑定输入纹理 (使用上一步的结果：horizon FBO 的纹理)
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, horizon.colorTextureID);
        // 设置 uniform 变量
        shaderManager.setUniformVec2("direction", new Vector2f(0.0f, 1.0f)); // 模糊方向: 垂直 (0, 1)
        shaderManager.setUniformVec2("targetResolution", new Vector2f(vertical.width, vertical.height)); // FBO 尺寸

        // 渲染一个覆盖整个屏幕的四边形
        drawQuad(vertical.width, vertical.height);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        // 解绑 vertical FBO
        vertical.unbindAndRestorePreviousFBO();

        // DEBUG 测试图像
        // BufferedImage blurImage = FrameUtils.getTextureImage(vertical.colorTextureID, vertical.width, vertical.height);
        // FrameUtils.debugSave(blurImage, "2");
        shaderManager.unbind();

        return vertical;
    }

    /**
     * 使用 VBO 绘制一个覆盖整个视口的四边形。
     * @param width 目标视口宽度
     * @param height 目标视口高度
     */
    public void drawQuad(int width, int height) {
        GL11.glViewport(0,0,width,height);

        // 2. 设置投影矩阵 (正交投影)
        Matrix4f projection = new Matrix4f().ortho(0,1,1,0,-1000,1000);
        Matrix4f modelView = new Matrix4f().identity();
        shaderManager.setUniformM4f("modelView", modelView);
        shaderManager.setUniformM4f("projection", projection);
        shaderManager.setUniformVec2("smoothRange", new Vector2f((float) Config.smoothRangeMin, (float) Config.smoothRangeMax));

        renderTool.render(VERTICES, TEX_COORDS, COLOR, INDEX);

        // 恢复原来的 Viewport
        GL11.glViewport(0,0,Display.getWidth(),Display.getHeight());
    }

    public void resize(int width, int height) {
        if (vertical.width != width || vertical.height != height) {
            vertical.resize(width, height);
            horizon.resize(width, height);
        }
    }


    // 兜底释放内存 -> 极端情况下可能造成卡顿
    public final AtomicBoolean isCloseManually = new AtomicBoolean(false);
    public void close() {
        vertical.close();
        horizon.close();
        GL15.glDeleteBuffers(quadVboId);
        GL15.glDeleteBuffers(quadTcoId);
        isCloseManually.set(true);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (!isCloseManually.get()) {
            RenderTickListener.errorCleaners.add(this::close);
        }
    }
}
