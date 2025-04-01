package club.heiqi.qz_uilib.skija.gui.component;

import club.heiqi.qz_uilib.skija.alignment.StringAlignUtils;
import club.heiqi.qz_uilib.skija.state.SkiaStore;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector2f;
import org.lwjgl.opengl.Display;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

public class Utils {
    public static Logger LOG = LogManager.getLogger();

    /**
     * @param canvas 需要绘制的画布
     * @param text 需要绘制的文本
     * @param font 选用字体
     * @param center 文本中心点所在点
     * @param paint 绘制所用的画笔
     */
    public static void drawStringCenter(Canvas canvas, String text, Font font, Vector2f center, Paint paint) {
        Vector2f textPos = StringAlignUtils.textCenterToTarget(text, font, center);
        canvas.drawString(text, textPos.x, textPos.y, font, paint);
    }

    public static ByteBuffer buffer = ByteBuffer.allocateDirect(8192*8192*4);
    public static Image getMinecraftBackground() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            int width = mc.displayWidth;
            int height = mc.displayHeight;
            // 0.切换到framebuffer1
            int curBuffer = (int) SkiaStore.glGetInteger.invoke(GL_FRAMEBUFFER_BINDING);
            glBindFramebuffer(GL_FRAMEBUFFER, 1);
            // 1.读取屏幕像素
            glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,buffer);
            // 2.转换为 Skija Image 计算 OpenGL 的行对齐 Stride（通常按 4 字节对齐）
            int bytesPerPixel = 4;   // RGBA 格式，每个像素 4 字节
            int srcStride = ((width * bytesPerPixel) + 3) & ~3; // 对齐后的行字节数
            // 按行翻转，并处理可能的行对齐
            flipPixelsVertically(buffer, width, height, srcStride, bytesPerPixel);
            byte[] bytes = new byte[width * height * bytesPerPixel];
            buffer.get(bytes, 0, width * height * bytesPerPixel);
            Data data = Data.makeFromBytes(bytes);
            Image image = Image.makeRasterFromData(
                new ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL),
                data,
                width * 4L
            );
            buffer.clear();
            data.close();
            glBindFramebuffer(GL_FRAMEBUFFER, curBuffer);
            return image;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // 直接在 ByteBuffer 中翻转像素行（高效操作）
    public static void flipPixelsVertically(
        ByteBuffer buffer, int width, int height,
        int srcStride, int bytesPerPixel
    ) {
        int rowBytes = width * bytesPerPixel;
        byte[] rowBuffer = new byte[rowBytes]; // 复用单行缓存

        for (int y = 0; y < height / 2; y++) {
            int topRowPos = y * srcStride;
            int bottomRowPos = (height - 1 - y) * srcStride;

            // 交换上下两行数据
            buffer.position(topRowPos);
            buffer.get(rowBuffer);
            buffer.position(bottomRowPos);
            byte[] bottomRow = new byte[rowBytes];
            buffer.get(bottomRow);
            buffer.position(topRowPos);
            buffer.put(bottomRow);
            buffer.position(bottomRowPos);
            buffer.put(rowBuffer);
        }
        buffer.rewind();
    }

    public static ImageFilter blurFilter = ImageFilter.makeBlur(8,8,FilterTileMode.CLAMP);
    public static void drawBlurMCBackground(Canvas canvas, float blur) {
        Image image = getMinecraftBackground();
        Rect srcRect = Rect.makeWH(image.getWidth(), image.getHeight());
        Rect dstRect = Rect.makeXYWH(0,0, Display.getWidth(), Display.getHeight());
        // 3.配置采样
        SamplingMode samplingMode = SamplingMode.LINEAR;
        // 4.应用模糊
        Paint paint = new Paint().setAntiAlias(true);
        paint.setImageFilter(blurFilter);
        canvas.drawImageRect(image,srcRect,dstRect,samplingMode,paint,true);
        // 释放资源
        image.close(); paint.close();
    }
}
