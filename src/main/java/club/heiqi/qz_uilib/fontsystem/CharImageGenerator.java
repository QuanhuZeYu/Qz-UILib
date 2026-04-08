package club.heiqi.qz_uilib.fontsystem;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.eventbus.HandlerWrapper;
import club.heiqi.qz_uilib.eventbus.QZEventBus;
import club.heiqi.qz_uilib.eventbus.api.EventHandler;
import club.heiqi.qz_uilib.fontsystem.event.FontReloadDoneEvent;
import club.heiqi.qz_uilib.fontsystem.event.FontReloadEvent;
import club.heiqi.qz_uilib.fontsystem.event.GenerateCharEvent;
import club.heiqi.qz_uilib.fontsystem.event.GenerateDoneEvent;
import club.heiqi.qz_uilib.fontsystem.utils.data.EmojiRange;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * 待优化类，有魔法用法的不规范
 */
public class CharImageGenerator {
    public static Logger LOG = LogManager.getLogger();

    public static CharImageGenerator instance;
    public static CharImageGenerator getInstance() {
        if (instance == null) {
            instance = new CharImageGenerator();
        }
        return instance;
    }
    @Deprecated
    /**请勿使用该构造方法，除非你知道自己在做什么*/
    public CharImageGenerator() {
        QZEventBus.getInstance().register(GenerateCharEvent.class, generateAsync);
        QZEventBus.getInstance().register(FontReloadEvent.class, onReload);
        QZEventBus.getInstance().register(FontReloadDoneEvent.class, onReloadEnd);
    }
    /**生成字符锁*/
    public ReentrantLock genSyncLock = new ReentrantLock();

    public ImageAndInfo generate(int codepoint, int type, int charSize) {
        Font font = FontManager.getInstance().findSuitable(codepoint, type);
        String s = new String(Character.toChars(codepoint));

        // 创建临时图像获取字体渲染上下文
        BufferedImage tempImage = new BufferedImage(charSize, charSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tempGraphics = tempImage.createGraphics();
        FontRenderContext frc = tempGraphics.getFontRenderContext();

        // 自适应调整
        double visualWidth, visualHeight;
        float advance, descent, ascent;
        Rectangle2D visualBounds;
        boolean retry = false;
        do {
            // 获取字符的精确边界
            GlyphVector glyphVector = font.createGlyphVector(frc, s);
            visualBounds = glyphVector.getVisualBounds();
            // 实际边界大小
            visualWidth = visualBounds.getWidth();
            visualHeight = visualBounds.getHeight();

            // 获取度量信息
            GlyphMetrics glyphMetrics = glyphVector.getGlyphMetrics(0);
            advance = glyphMetrics.getAdvance();

            // 逻辑度量
            LineMetrics lineMetrics = font.getLineMetrics(s, frc);
            descent = lineMetrics.getDescent();
            ascent = lineMetrics.getAscent();

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
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

        // 设置字体和颜色
        g2d.setFont(font);
        g2d.setColor(Color.WHITE);

        // ----- 根据步进宽度计算绘制X坐标 -----
        float x = 0, y = 0;
        if (visualWidth > charSize/2) {  // 如果字符宽度大于字符格 1/2 将字符移动到左侧
            x = (float) -visualBounds.getX() + 2;  // 默认左对齐
            advance = (float) (advance - visualBounds.getX() + 2);
        }
        else {  // 如果字符是窄字符，不修改
            x = 0;
            advance = (float) advance;
        }
        // --end 根据步进宽度计算绘制X坐标 end--
        // 如果是表情符号图像要使用边界
        if (EmojiRange.isEmoji(codepoint)) {
            x = (float) -visualBounds.getX()  // 此步将图像对齐到左侧边缘线
                -(float)(visualBounds.getWidth()/2) + (float)(charSize/2)  // 此步将图像水平居中
            ;
            y = (float) (-descent + charSize);  // 将逻辑图像拉到左上角 (-descent 将字符的逻辑底部对齐到字符格的顶部) (+charSize 将字符的逻辑底部对齐到字符格的底部)
        }
        else {
            y = (float) (-descent + charSize);  // 将逻辑图像拉到左上角 (-descent 将字符的逻辑底部对齐到字符格的顶部) (+charSize 将字符的逻辑底部对齐到字符格的底部)
        }
        g2d.drawString(s, x, (float)(y + Config.baseLineOffset));

        // 释放资源并返回图像
        g2d.dispose();
        tempGraphics.dispose();
        return new ImageAndInfo(image, new CharInfo(
                codepoint,
                0,0,
                charSize,charSize,
                advance)
        );
    }

    /**订阅生成事件，异步生成字符图像*/
    public HandlerWrapper generateAsync = new HandlerWrapper((event) -> {
        if (genSyncLock.isLocked()) {
            throw new RuntimeException("此时不应该有生成任务！");
        }
        GenerateCharEvent generateCharEvent = (GenerateCharEvent) event;
        int codepoint = generateCharEvent.codepoint;
        int type = generateCharEvent.type;
        int charSize = generateCharEvent.charSize;
        new Thread(() -> {
            ImageAndInfo imageAndInfo = generate(codepoint, type, charSize);
            // 发布生成结束事件
            GenerateDoneEvent doneEvent = new GenerateDoneEvent(codepoint, type, imageAndInfo.image, imageAndInfo.info);
            QZEventBus.getInstance().post(doneEvent);
        }, "字符"+new String(Character.toChars(codepoint))+"生成器").start();
    }, 3000);
    /**订阅重载开始事件，阻止字符在生成期间被请求*/
    public HandlerWrapper onReload = new HandlerWrapper(event -> {
        genSyncLock.lock();
    }, 3000);
    /**订阅重载结束事件，允许字符被请求*/
    public HandlerWrapper onReloadEnd = new HandlerWrapper(event -> {
        genSyncLock.unlock();
    }, 3000);

    public static class ImageAndInfo {
        public BufferedImage image;
        public CharInfo info;
        public ImageAndInfo(BufferedImage image, CharInfo info) {
            this.image = image;
            this.info = info;
        }
    }


    public static void main(String[] args) {
        ImageAndInfo generated = CharImageGenerator.getInstance().generate("`".codePointAt(0), 0, 64);
        BufferedImage image = generated.image;
        File saveFile = new File("test.png");
        try {
            boolean success = ImageIO.write(image, "png", saveFile);
        } catch (IOException e) {
            // Handle potential errors like file permission issues or I/O problems
            System.err.println("Error saving image: " + e.getMessage());
            e.printStackTrace();
        }
        LOG.info("步进信息: {}", generated.info.advance);
    }
}
