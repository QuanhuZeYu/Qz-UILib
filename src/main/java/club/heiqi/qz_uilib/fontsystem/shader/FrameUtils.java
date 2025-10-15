package club.heiqi.qz_uilib.fontsystem.shader;

import club.heiqi.qz_uilib.client.FBO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class FrameUtils {
    public static Logger LOG = LogManager.getLogger();
    private static ByteBuffer buffer;
    public static ByteBuffer getBuffer(int width, int height) {
        int cap = width * height * 4;
        if (buffer == null) {
            buffer = BufferUtils.createByteBuffer(cap);
        }
        else {
            buffer.clear();
            if (buffer.capacity() < cap) {
                buffer = BufferUtils.createByteBuffer(cap);
            }
        }
        return buffer;
    }
    /**
     * 获取对应FrameBufferObject的图像
     */
    public static BufferedImage getFrameImage(int frameID, int width, int height) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, frameID);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

        ByteBuffer byteBuffer = getBuffer(width, height);
        GL11.glReadPixels(0,0,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,byteBuffer);

        // 解绑
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);

        return bufferToImage(byteBuffer, width, height);
    }

    /**
     * 从给定的 OpenGL 纹理 ID (textureID) 读取像素数据并返回 BufferedImage。
     * * @param textureID 要读取的纹理的 OpenGL ID
     * @param width 纹理的宽度
     * @param height 纹理的高度
     * @return 包含纹理内容的 BufferedImage
     */
    public static BufferedImage getTextureImage(int textureID, int width, int height) {
        ByteBuffer textureBuffer = BufferUtils.createByteBuffer(width * height * 4);
        // 3. 读取像素
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, textureBuffer);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return bufferToImage(textureBuffer, width, height);
    }

    /**
     * 将 ByteBuffer 中的 RGBA 像素数据转换为垂直翻转的 BufferedImage。
     * (从 getFrameImage 中提取的通用逻辑)
     */
    private static BufferedImage bufferToImage(ByteBuffer buffer, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width * height];
        int k = 0;

        // 注意：OpenGL 图像通常是垂直倒置的，因此需要翻转
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 垂直翻转后的索引: (height - 1 - y)
                // int i = (height - 1 - y) * width * 4 + x * 4;

                int i = y * width * 4 + x * 4;
                int r = buffer.get(i) & 0xFF;
                int g = buffer.get(i + 1) & 0xFF;
                int b = buffer.get(i + 2) & 0xFF;
                int a = buffer.get(i + 3) & 0xFF;

                // AARRGGBB 格式
                pixels[k++] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    /**
     * 将给定的图像保存到指定路径（作为调试用途）
     */
    public static void debugSave(BufferedImage image, String savePath) {
        File saveFile = new File(savePath + ".png");
        try {
            // 将 BufferedImage 写入到文件，使用 "png" 格式
            ImageIO.write(image, "png", saveFile);
            LOG.info("图像已保存到: {}", saveFile.getAbsolutePath());
        } catch (IOException e) {
            LOG.error("保存图像失败: {}", e.getMessage());
        }
    }

    public static void copyTexture(int sourceTextureID, int sW, int sH, int targetTextureID, int tW, int tH) {
        FBO sourceTemp = new FBO(sW,sH).initByOutColorAndGenDepth(sourceTextureID);
        FBO targetTemp = new FBO(tW,tH).initByOutColorAndGenDepth(targetTextureID);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceTemp.fboID);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetTemp.fboID);

        GL30.glBlitFramebuffer(
                0,0,sW,sH,
                0,0,tW,tH,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST
        );

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        sourceTemp.close();
        targetTemp.close();
    }

    public static FBO temp;
    public static void copyTextureByFrame(FBO sourceFrame, int targetTextureID, int targetWidth, int targetHeight) {
        if (temp == null) {
            temp = new FBO(targetWidth, targetHeight).initByOutColorAndGenDepth(targetTextureID);
        }
        else {
            temp.resize(targetWidth, targetHeight);
            temp.attachColorTexture(targetTextureID);
        }

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFrame.fboID);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, temp.fboID);

        GL30.glBlitFramebuffer(
                0,0,sourceFrame.width,sourceFrame.height,
                0,0,targetWidth,targetHeight,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST
        );

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    public static int getTextureWidth(int textureID) {
        // 绑定纹理
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);
        int width =  GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WIDTH);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return width;
    }

    public static int getTextureHeight(int textureID) {
        // 绑定纹理
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);
        int height =  GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_HEIGHT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return height;
    }
}
