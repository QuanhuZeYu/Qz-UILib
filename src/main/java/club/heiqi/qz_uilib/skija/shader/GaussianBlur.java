package club.heiqi.qz_uilib.skija.shader;

import club.heiqi.qz_uilib.shader.FullScreenQuad;
import club.heiqi.qz_uilib.shader.ShaderManager;
import club.heiqi.qz_uilib.skija.FrameBuffer;
import club.heiqi.qz_uilib.skija.state.SkiaStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.*;

public class GaussianBlur {
    public static ShaderManager gaussianBlur;
    public static FrameBuffer downscaleFBO;  // 降采样缓冲
    public static FrameBuffer[] pingpongFBOs = new FrameBuffer[2]; // 乒乓缓冲

    // 预计算Uniform位置（类初始化时执行）
    private static int horizontalLoc,sigmaLoc;

    static {
        gaussianBlur = new ShaderManager("高斯模糊着色器","shaders/gaussianBlur/gaussianBlur.vert","shaders/gaussianBlur/gaussianBlur.glsl");
        downscaleFBO = new FrameBuffer(Display.getWidth()/2, Display.getHeight()/2,GL12.GL_CLAMP_TO_EDGE)
            .setFBOName("高斯模糊降采样FBO");
        pingpongFBOs[0] = new FrameBuffer(Display.getWidth()/2, Display.getHeight()/2,GL12.GL_CLAMP_TO_EDGE)
            .setFBOName("高斯模糊乒乓FBO-01");
        pingpongFBOs[1] = new FrameBuffer(Display.getWidth()/2, Display.getHeight()/2,GL12.GL_CLAMP_TO_EDGE)
            .setFBOName("高斯模糊乒乓FBO-02");
        horizontalLoc = gaussianBlur.getUniformLocation("horizontal");
        sigmaLoc = gaussianBlur.getUniformLocation("sigma");
    }

    public static void drawBlur(float sigma, int fboID) {
        try {
            int oldProgram = (int) SkiaStore.glGetInteger.invoke(GL20.GL_CURRENT_PROGRAM);
            int oldRead = (int) SkiaStore.glGetInteger.invoke(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int oldDraw = (int) SkiaStore.glGetInteger.invoke(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

            Minecraft mc = Minecraft.getMinecraft();
            Framebuffer mcFbo = mc.getFramebuffer();

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, downscaleFBO.fboID);
            GL11.glClearColor(0,0,0,0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mcFbo.framebufferObject);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, downscaleFBO.fboID);
            // 将MC画面填入降低采样fbo
            GL30.glBlitFramebuffer(
                    0, 0, mcFbo.framebufferWidth, mcFbo.framebufferHeight,
                    0, 0, downscaleFBO.width, downscaleFBO.height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
            );
            GL20.glUseProgram(gaussianBlur.shaderID);
            GL20.glUniform1f(sigmaLoc, sigma);
            // 从降低采样fbo中水平模糊一次
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, pingpongFBOs[0].fboID);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,downscaleFBO.colorTextureID);
            GL20.glUniform1i(horizontalLoc, 1);
            FullScreenQuad.render();
            // 从水平模糊fbo垂直模糊
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, pingpongFBOs[1].fboID);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,pingpongFBOs[0].colorTextureID);
            GL20.glUniform1i(horizontalLoc, 0);
            FullScreenQuad.render();
            // 上采样回目标FBO（如果需要全分辨率）-----------------
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, pingpongFBOs[1].fboID);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fboID);
            GL30.glBlitFramebuffer(
                    0, 0, pingpongFBOs[1].width, pingpongFBOs[1].height,
                    0, 0,Display.getWidth(), Display.getHeight(),
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR
            );

            // 恢复状态
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
            GL20.glUseProgram(oldProgram);
            SkiaStore.glUseProgram.invoke(oldProgram);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // 计算高斯权重
    public static float[] calculateWeights(float sigma) {
        // 自动计算需要的采样半径（限制最大为32）
        int radius = (int) Math.min(Math.ceil(sigma * 2.5f), 32);
        float[] weights = new float[radius + 1];
        float total = 0.0f;

        // 计算离散高斯分布
        for (int i = 0; i <= radius; i++) {
            weights[i] = (float) Math.exp(-(i*i)/(2.0*sigma*sigma));
            total += (i == 0) ? weights[i] : 2 * weights[i];
        }

        // 归一化处理
        for (int i = 0; i <= radius; i++) {
            weights[i] /= total;
        }
        return weights;
    }
}
