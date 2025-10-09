package club.heiqi.qz_uilib.fontsystem;

import club.heiqi.qz_uilib.Config;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.BufferUtils;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 待优化类，有魔法用法的不规范
 */
public class CharImageGenerator {
    public static CharImageGenerator instance;
    public static CharImageGenerator getInstance() {
        if (instance == null) {
            instance = new CharImageGenerator();
        }
        return instance;
    }
    /**任务列表 码点：对应回调*/
    public ConcurrentHashMap<Integer, Consumer<ImageAndInfo>> normalConsumerHashMap = new ConcurrentHashMap<>(),
            boldConsumerHashMap = new ConcurrentHashMap<>();
    /**多线程结果存放与主线程获取通道 码点：对应生成结果等待回调消费*/
    public ConcurrentHashMap<Integer, ImageAndInfo> normalResults = new ConcurrentHashMap<>(),
            boldResults = new ConcurrentHashMap<>();

    public void reload(boolean reloadFontManager) {
        if (reloadFontManager) {
            FontManager.getInstance().reload((float) (Config.awtCharSize * 0.8f));
        }

        normalConsumerHashMap.clear();
        boldConsumerHashMap.clear();

        normalResults.clear();
        boldResults.clear();
    }

    public ImageAndInfo generate(int codepoint, int type, int charSize) {
        Font font = FontManager.getInstance().findSuitable(codepoint, type);
        String s = new String(Character.toChars(codepoint));

        // 创建临时图像获取字体渲染上下文
        BufferedImage tempImage = new BufferedImage(charSize, charSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tempGraphics = tempImage.createGraphics();
        FontRenderContext frc = tempGraphics.getFontRenderContext();

        // 自适应调整
        double visualWidth, visualHeight;
        float advance, descent;
        boolean retry = false;
        do {
            // 获取字符的精确边界
            GlyphVector glyphVector = font.createGlyphVector(frc, s);
            Rectangle2D visualBounds = glyphVector.getVisualBounds();
            // 实际边界大小
            visualWidth = visualBounds.getWidth();
            visualHeight = visualBounds.getHeight();

            // 获取度量信息
            GlyphMetrics glyphMetrics = glyphVector.getGlyphMetrics(0);
            advance = glyphMetrics.getAdvance();

            // 逻辑度量
            LineMetrics lineMetrics = font.getLineMetrics(s, frc);
            descent = lineMetrics.getDescent();

            if ((visualWidth > charSize || visualHeight > charSize)) {
                font = font.deriveFont(font.getSize2D() - .5f);
                retry = true;
            }
            else {
                retry = false;
            }
        } while(retry);

        // 创建最终图像
        BufferedImage image = new BufferedImage(charSize, charSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // 设置渲染质量
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // 设置字体和颜色
        g2d.setFont(font);
        g2d.setColor(Color.WHITE);

        float x = (float) 0;
        float y = (float) (-descent + charSize);  // 将图像拉到左上角
        g2d.drawString(s, x, y);

        // 释放资源并返回图像
        g2d.dispose();
        tempGraphics.dispose();
        return new ImageAndInfo(image, new CharInfo(codepoint,0,0,charSize,charSize, advance));
    }

    /**注意，consumer运行在主线程，可放心使用*/
    public void generateAsync(int codepoint, int type, int charSize, Consumer<ImageAndInfo> consumer) {
        if (type == PageManager.NORMAL)
            normalConsumerHashMap.put(codepoint, consumer);
        else if (type == PageManager.BOLD)
            boldConsumerHashMap.put(codepoint, consumer);
        new Thread(() -> {
            if (type == PageManager.NORMAL)
                normalResults.put(codepoint, generate(codepoint, type, charSize));  // 将结果添加到结果容器中
            else if (type == PageManager.BOLD)
                boldResults.put(codepoint, generate(codepoint, type, charSize));
        }, "字符"+new String(Character.toChars(codepoint))+"生成器").start();
    }

    @SubscribeEvent
    public void mainThreadCaller(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;  // 只在开始阶段运行
        // 已经执行过的结果
        ArrayList<Integer> toRemove = new ArrayList<>();
        // 常规: 遍历消费者
        for (Map.Entry<Integer, Consumer<ImageAndInfo>> entry : normalConsumerHashMap.entrySet()) {
            // 如果结果出现
            Integer codepoint = entry.getKey();
            if (normalResults.containsKey(codepoint)) {
                toRemove.add(codepoint);
                ImageAndInfo imageAndInfo = normalResults.get(codepoint);
                entry.getValue().accept(imageAndInfo);
            }
        }
        // 将已经执行的结果移除掉
        for (Integer codepoint : toRemove) {
            normalResults.remove(codepoint);
        }

        // 粗体:
        toRemove.clear();
        for (Map.Entry<Integer, Consumer<ImageAndInfo>> entry : boldConsumerHashMap.entrySet()) {
            // 如果结果出现
            Integer codepoint = entry.getKey();
            if (boldResults.containsKey(codepoint)) {
                toRemove.add(codepoint);
                ImageAndInfo imageAndInfo = boldResults.get(codepoint);
                entry.getValue().accept(imageAndInfo);
            }
        }
        // 将已经执行的结果移除掉
        for (Integer codepoint : toRemove) {
            boldResults.remove(codepoint);
        }
    }

    public boolean isRegister = false;
    public void register() {
        if (!isRegister) {
            FMLCommonHandler.instance().bus().register(this);
            MinecraftForge.EVENT_BUS.register(this);
            isRegister = true;
        }
    }

    public static class ImageAndInfo {
        public BufferedImage image;
        public CharInfo info;
        public ImageAndInfo(BufferedImage image, CharInfo info) {
            this.image = image;
            this.info = info;
        }

        public ByteBuffer getImageByteBuffer() {
            if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
                // 推荐：如果不是预期的格式，先转换为 TYPE_INT_ARGB 以确保正确的像素布局
                BufferedImage temp = new BufferedImage(
                        image.getWidth(),
                        image.getHeight(),
                        BufferedImage.TYPE_INT_ARGB
                );
                temp.getGraphics().drawImage(image, 0, 0, null);
                image = temp;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            // 1. 获取所有像素的 ARGB 整数数组
            int[] pixels = new int[width * height];
            image.getRGB(0, 0, width, height, pixels, 0, width);

            // 2. 创建一个用于存储 RGBA 字节的 ByteBuffer
            // 每个像素 4 个字节 (R, G, B, A)
            final int BYTES_PER_PIXEL = 4;
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * BYTES_PER_PIXEL);

            // 3. 遍历像素数组，进行 ARGB -> RGBA 的转换和写入
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // 注意：BufferedImage 的 (0,0) 是左上角，而 OpenGL 纹理通常是左下角。
                    // 循环时按从上到下、从左到右的顺序读取，如果需要 Y 轴翻转，
                    // 可以在此处修改 y 的索引，例如：(height - 1 - y) * width + x
                    int pixel = pixels[y * width + x];

                    // TYPE_INT_ARGB 整数格式: [A: 24-31位] [R: 16-23位] [G: 8-15位] [B: 0-7位]

                    // 提取 R, G, B, A 分量 (使用位运算和 & 0xFF 确保只取 8 位)
                    byte alpha = (byte) ((pixel >> 24) & 0xFF);
                    byte red = (byte) ((pixel >> 16) & 0xFF);
                    byte green = (byte) ((pixel >> 8) & 0xFF);
                    byte blue = (byte) ((pixel) & 0xFF);

                    // 按照 RGBA 的顺序写入 ByteBuffer
                    buffer.put(red);
                    buffer.put(green);
                    buffer.put(blue);
                    buffer.put(alpha);
                }
            }

            // 4. 翻转 Buffer，将其位置 (position) 设置为 0，限制 (limit) 设置为当前位置。
            // 这样 OpenGL 就可以从头开始读取数据。
            buffer.flip();

            return buffer;
        }
    }
}
