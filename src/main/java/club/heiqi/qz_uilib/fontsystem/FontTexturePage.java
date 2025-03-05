package club.heiqi.qz_uilib.fontsystem;

import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static club.heiqi.qz_uilib.MOD_INFO.LOG;
import static club.heiqi.qz_uilib.fontsystem.FontManager.outPutDir;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

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

    public boolean addCharToPage(Font font, int codePoint, boolean up) {
        if (start == -1) start = codePoint;
        int endCopy = end;
        end = codePoint;
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
                end = endCopy;
                return false;
            }
        }
        // 生成字符图像
        BufferedImage charImage = _genCharImage(font, str, up);
        Graphics2D g = page.createGraphics();
        try {
            g.drawImage(charImage, currentX, currentY, null);
        } finally {
            g.dispose();
        }
        currentX += FONT_PIXEL_SIZE;
        return true;
    }

    public BufferedImage _genCharImage(Font font, String c, boolean up) {
        BufferedImage image = new BufferedImage(FONT_PIXEL_SIZE, FONT_PIXEL_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        // 启用抗锯齿
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(font);
        g.setColor(Color.WHITE);
        // 获取字体度量信息
        FontMetrics metrics = g.getFontMetrics();
        // 计算字符边界
        Rectangle2D bounds = new TextLayout(c, font, g.getFontRenderContext()).getBounds();
        int bx = (int) bounds.getX(); // 起始x
        int by = (int) bounds.getY();
        int charWidth = (int) bounds.getWidth();
        int charHeight = (int) bounds.getHeight();
        LOG.debug("字符宽: {} 高: {} 左上角坐标: {}, {}", charWidth, charHeight, bx, by);
        // 水平居中：基于边界宽度
        int x = 0;
        int y = FONT_PIXEL_SIZE;
        if (up) {
            // 公式：底部基准线抬升1/5图像高度
            y = FONT_PIXEL_SIZE - (FONT_PIXEL_SIZE/5*1);
        }
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

    public void uploadGPU() {

        // 获取像素数组
        int[] pixels = new int[TEXTURE_SIZE * TEXTURE_SIZE];
        page.getRGB(0, 0, TEXTURE_SIZE, TEXTURE_SIZE, pixels, 0, TEXTURE_SIZE);
        // 转换为RGBA ByteBuffer （垂直翻转）
        ByteBuffer buffer = ByteBuffer.allocateDirect(TEXTURE_SIZE * TEXTURE_SIZE * 4);
        for (int y = TEXTURE_SIZE - 1; y >=0; y--) { // 从下往上遍历
            for (int x = 0; x < TEXTURE_SIZE; x++) {
                int pixel = pixels[y * TEXTURE_SIZE + x];
                buffer.put((byte) ((pixel >> 16) & 0xFF)); // R
                buffer.put((byte) ((pixel >> 8) & 0xFF));  // G
                buffer.put((byte) (pixel & 0xFF));         // B
                buffer.put((byte) ((pixel >> 24) & 0xFF)); // A
            }
        }
        buffer.flip();
        textureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureID);
        // 设置纹理参数
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        // 上传到GPU
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, TEXTURE_SIZE, TEXTURE_SIZE, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        //生成mipmap
        glGenerateMipmap(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public List<Integer> findChar(int codepoint) {
        if (codepoint < start || codepoint > end) {
            LOG.error("该纹理页中不存在 {}", (char) codepoint);
            return null;
        }
        int count = codepoint - start; // 偏移数量
        int hangCount = (int) ((double) TEXTURE_SIZE / FONT_PIXEL_SIZE);
        int hang = (int) Math.ceil((double) count / hangCount);
        int y = hang * (FONT_PIXEL_SIZE); // 字符贴图左上角Y
        int x = (count - (hangCount * hang)) * FONT_PIXEL_SIZE; // 字符贴图左上角X
        return Arrays.asList(x, y);
    }
}
