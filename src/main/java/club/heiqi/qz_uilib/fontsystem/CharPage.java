package club.heiqi.qz_uilib.fontsystem;

import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static club.heiqi.qz_uilib.MOD_INFO.LOG;
import static club.heiqi.qz_uilib.fontsystem.FontManager.FONT_PIXEL_SIZE;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

public class CharPage {
    public static int PAGE_SIZE = 2048;
    public static int charCount = (PAGE_SIZE * PAGE_SIZE) / (FONT_PIXEL_SIZE * FONT_PIXEL_SIZE);
    public int charCounter = 0;
    public int textureID = -1;
    public BufferedImage img;
    /** 该页存储的字符 */
    public List<Character> storedChar = new ArrayList<>();

    /**
     * 构造函数创建一张透明空图等待添加字符
     */
    public CharPage() {
        img = new BufferedImage(PAGE_SIZE, PAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
    }

    public boolean addChar(Font font, Character c) {
        if (charCounter >= charCount) {
            return false;
        }
        // 计算左上角坐标
        int 每行数 = PAGE_SIZE / FONT_PIXEL_SIZE;
        // 计算行列时使用 charCounter
        int 行 = charCounter / 每行数;
        int 列 = charCounter % 每行数;
        int x = 列 * FONT_PIXEL_SIZE;
        int y = 行 * FONT_PIXEL_SIZE;
        try {
            Graphics2D g = img.createGraphics();
            BufferedImage charImg = _genCharImage(font, c);
            g.drawImage(charImg, x, y, FONT_PIXEL_SIZE, FONT_PIXEL_SIZE, null);
            g.dispose();
            charCounter++;
            storedChar.add(c);
            uploadGPU();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private BufferedImage _genCharImage(Font font, Character c) {
        BufferedImage image = new BufferedImage(FONT_PIXEL_SIZE, FONT_PIXEL_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(font);
        g.setColor(Color.WHITE);

        FontMetrics metrics = g.getFontMetrics();
        TextLayout layout = new TextLayout(String.valueOf(c), font, g.getFontRenderContext());
        Rectangle2D bounds = layout.getBounds();

        int bx = (int) bounds.getX();
        int by = (int) bounds.getY();
        int charWidth = (int) bounds.getWidth();
        int charHeight = (int) bounds.getHeight();

        // 水平居中 + 垂直基线对齐
        int x = (FONT_PIXEL_SIZE - charWidth) / 2 - bx;
        int y = metrics.getAscent() - by;

        layout.draw(g, x, y);
        g.dispose();
        return image;
    }

    public void _saveImage(String fileName) {
        File outPutDir = new File(Minecraft.getMinecraft().mcDataDir, "图像输出");
        if (!outPutDir.exists()) {
            if (!outPutDir.mkdirs()) {
                LOG.error("创建目录失败: {}", outPutDir.getAbsoluteFile());
            }
        }
        File imageFile = new File(outPutDir, fileName);
        try {
            ImageIO.write(img, "png", imageFile);
        } catch (IOException e) {
            LOG.error("保存图像失败\n栈输出: ", e);
        }
    }

    public void uploadGPU() {
        if (textureID != -1 && textureID != 0) {
            glDeleteTextures(textureID); // 释放不需要的贴图
        }
        // 获取像素数组
        int[] pixels = new int[PAGE_SIZE * PAGE_SIZE];
        img.getRGB(0, 0, PAGE_SIZE, PAGE_SIZE, pixels, 0, PAGE_SIZE);
        // 转换为RGBA ByteBuffer （垂直翻转）
        ByteBuffer buffer = ByteBuffer.allocateDirect(PAGE_SIZE * PAGE_SIZE * 4);
        for (int y = PAGE_SIZE - 1; y >=0; y--) { // 从下往上遍历
            for (int x = 0; x < PAGE_SIZE; x++) {
                int pixel = pixels[y * PAGE_SIZE + x];
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
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, PAGE_SIZE, PAGE_SIZE, 0,
            GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        //生成mipmap
        glGenerateMipmap(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public boolean findChar(Character c) {
        for (Character ch : storedChar) {
            if (ch.equals(c)) {
                return true;
            }
        }
        return false;
    }

    public CharInfo getCharInfo(Character c) {
        for (Character ch : storedChar) {
            if (ch.equals(c)) {
                // 寻找字符所在索引+1
                int index = storedChar.indexOf(c);
                return new CharInfo(this, index);
            }
        }
        return null;
    }
}
