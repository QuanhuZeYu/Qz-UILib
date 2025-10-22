package club.heiqi.qz_uilib.fontsystem;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.eventbus.HandlerWrapper;
import club.heiqi.qz_uilib.eventbus.QZEventBus;
import club.heiqi.qz_uilib.eventbus.api.EventHandler;
import club.heiqi.qz_uilib.fontsystem.event.FontReloadDoneEvent;
import club.heiqi.qz_uilib.fontsystem.event.FontReloadEvent;
import club.heiqi.qz_uilib.fontsystem.event.GenerateCharEvent;
import club.heiqi.qz_uilib.fontsystem.event.GenerateDoneEvent;
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
import java.util.concurrent.locks.ReentrantLock;
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
    @Deprecated
    /**请勿使用该构造方法，除非你知道自己在做什么*/
    public CharImageGenerator() {
        QZEventBus.getInstance().register(GenerateCharEvent.class, generateAsync);
        QZEventBus.getInstance().register(FontReloadEvent.class, onReload);
        QZEventBus.getInstance().register(FontReloadDoneEvent.class, onReloadEnd);
    }
    /**生成字符锁*/
    public ReentrantLock genSyncLock = new ReentrantLock();

    protected ImageAndInfo generate(int codepoint, int type, int charSize) {
        Font font = FontManager.getInstance().findSuitable(codepoint, type);
        String s = new String(Character.toChars(codepoint));

        // 创建临时图像获取字体渲染上下文
        BufferedImage tempImage = new BufferedImage(charSize, charSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tempGraphics = tempImage.createGraphics();
        FontRenderContext frc = tempGraphics.getFontRenderContext();

        // 自适应调整
        double visualWidth, visualHeight, boundsX;
        float advance, descent;
        boolean retry = false;
        do {
            // 获取字符的精确边界
            GlyphVector glyphVector = font.createGlyphVector(frc, s);
            Rectangle2D visualBounds = glyphVector.getVisualBounds();
            // 实际边界大小
            visualWidth = visualBounds.getWidth();
            visualHeight = visualBounds.getHeight();
            boundsX = visualBounds.getX();

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

        float x = (float) -boundsX + 2;  // 固定向右偏移2像素避免shrink时截断字符本体
        float y = (float) (-descent + charSize);  // 将图像拉到左上角
        g2d.drawString(s, x, y);

        // 释放资源并返回图像
        g2d.dispose();
        tempGraphics.dispose();
        return new ImageAndInfo(image, new CharInfo(codepoint,0,0,charSize,charSize, (float) advance));
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
}
