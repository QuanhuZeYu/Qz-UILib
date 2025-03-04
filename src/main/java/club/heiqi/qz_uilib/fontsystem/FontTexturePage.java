package club.heiqi.qz_uilib.fontsystem;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static club.heiqi.qz_uilib.MOD_INFO.LOG;
import static club.heiqi.qz_uilib.fontsystem.FontManager.FONT_SIZE;
import static club.heiqi.qz_uilib.fontsystem.FontManager.outPutDir;

public class FontTexturePage {
    public static int FONT_PIXEL_SIZE = 64;
    public static int TEXTURE_SIZE = 1024;
    public static int CHAR_COUNT = (TEXTURE_SIZE * TEXTURE_SIZE) / (FONT_PIXEL_SIZE * FONT_PIXEL_SIZE);
    public int textureID = -1;
    public int currentX = 0, currentY = 0;
    public BufferedImage page;
    public int start = -1, end = -1;

    public FontTexturePage() {
        page = createBlankPage();
    }

    public BufferedImage createBlankPage() {
        return new BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_INT_ARGB);
    }

    public boolean addCharToPage(Font font, int codePoint, float fontSize) {
        if (start == -1) start = codePoint;
        if (currentY >= TEXTURE_SIZE) return false;
        // 检查代码点合法性
        if (!Character.isValidCodePoint(codePoint)) return false;
        char[] chars = Character.toChars(codePoint);
        String str = new String(chars);
        // 检查换行（基于固定格子）
        if (currentX + FONT_PIXEL_SIZE > TEXTURE_SIZE) {
            currentX = 0;
            currentY += FONT_PIXEL_SIZE;
            if (currentY + FONT_PIXEL_SIZE > TEXTURE_SIZE) { // 此页装不下的情况
                end = codePoint;
                return false;
            }
        }
        // 生成字符图像
        BufferedImage charImage = _genCharImage(font, str, fontSize);
        Graphics2D g = page.createGraphics();
        try {
            g.drawImage(charImage, currentX, currentY, null);
            LOG.debug("已在 ({}, {}) 上绘制 {}", currentX, currentY, str);
        } finally {
            g.dispose();
        }
        currentX += FONT_PIXEL_SIZE;
        return true;
    }

    public BufferedImage _genCharImage(Font font, String c, float fontSize) {
        font = font.deriveFont(fontSize);
        BufferedImage image = new BufferedImage(FONT_PIXEL_SIZE, FONT_PIXEL_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        // 启用抗锯齿
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(font);
        g.setColor(Color.WHITE);
        // 获取字体度量信息
        FontMetrics metrics = g.getFontMetrics();
        // 获取字符的基线位置（相对于图像顶部）
        int baselineY = metrics.getAscent(); // 实际效果：将基准线放到最底部 -> 也就是会超出原始高度一部分
        // 计算字符边界
        Rectangle2D bounds = new TextLayout(c, font, g.getFontRenderContext()).getBounds();
        int charWidth = (int) bounds.getWidth();
        int charHeight = (int) bounds.getHeight();
        // 水平居中：基于边界宽度
        int x = (FONT_PIXEL_SIZE - charWidth) / 2;
        // 垂直居中：将基线对齐到图像中心
        // 公式：底部基准线抬升1/4图像高度
        int y = baselineY - (FONT_PIXEL_SIZE/4);
        // 绘制字符
        g.drawString(c, x, y);
        g.dispose();
        return image;
    }

    public void _saveImage(String fileName) {
        if (!outPutDir.exists()) {
            if (!outPutDir.mkdirs()) {
                LOG.error("创建目录失败: {}", outPutDir.getAbsoluteFile());
            }
        }
        File imageFile = new File(outPutDir, fileName);
        try {
            ImageIO.write(page, "png", imageFile);
        } catch (IOException e) {
            LOG.error("保存图像失败\n栈输出: ", e);
        }
    }
}
