package club.heiqi.skija.component;

import club.heiqi.skija.font.FontLoader;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector2f;
import org.lwjgl.opengl.Display;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

public class Utils {
    public static Logger LOG = LogManager.getLogger();

    // 计算字符串长度 - 计算中心 - 返回字符串左上角的坐标
    public static Vector2f calculateStringPos(String text, Font font, Vector2f center) {
        Rect rect = font.measureText(text);
        float width = rect.getWidth();
        float height = FontLoader.FONT_SIZE;
        float StringCenterX = center.x - width / 2;
        float StringCenterY = center.y + height / 2;
        return new Vector2f(StringCenterX, StringCenterY);
    }

    /**
     * @param canvas 需要绘制的画布
     * @param text 需要绘制的文本
     * @param font 选用字体
     * @param center 文本中心点所在点
     * @param paint 绘制所用的画笔
     */
    public static void drawStringCenter(Canvas canvas, String text, Font font, Vector2f center, Paint paint) {
        Vector2f textPos = calculateStringPos(text, font, center);
        canvas.drawString(text, textPos.x, textPos.y, font, paint);
    }

    public static ByteBuffer buffer = ByteBuffer.allocateDirect(8192*8192*4);
    public static Image getMinecraftBackground() {
        Minecraft mc = Minecraft.getMinecraft();
        int width = mc.displayWidth; int height = mc.displayHeight;
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
        buffer.clear(); data.close();
        return image;
    }

    // 直接在 ByteBuffer 中翻转像素行（高效操作）
    private static void flipPixelsVertically(
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

    public static void drawBlurMCBackground(Canvas canvas, float blur) {
        Image image = getMinecraftBackground();
        Rect srcRect = Rect.makeWH(image.getWidth(), image.getHeight());
        Rect dstRect = Rect.makeXYWH(0,0, Display.getWidth(), Display.getHeight());
        // 3.配置采样
        SamplingMode samplingMode = SamplingMode.LINEAR;
        FilterTileMode clamp = FilterTileMode.CLAMP;
        if (clamp == null) return;
        // 4.应用模糊
        Paint paint = new Paint();
        paint.setImageFilter(ImageFilter.makeBlur(blur,blur, clamp));
        canvas.drawImageRect(image,srcRect,dstRect,samplingMode,paint,true);
        // 释放资源
        image.close(); paint.close();
    }
}
