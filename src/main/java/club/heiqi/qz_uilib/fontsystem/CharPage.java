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
import java.util.HashMap;
import java.util.Map;

import static club.heiqi.qz_uilib.MOD_INFO.LOG;
import static club.heiqi.qz_uilib.fontsystem.FontManager.FONT_PIXEL_SIZE;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

public class CharPage {
    public static int PAGE_SIZE = 2048;
    public static int charCount = (PAGE_SIZE * PAGE_SIZE) / (FONT_PIXEL_SIZE * FONT_PIXEL_SIZE);
    public volatile short charCounter = 0;
    public int textureID = -1;
    public volatile boolean isDirty = false;
    public volatile BufferedImage img;
    /** 该页存储的字符 */
    public volatile Map<String, CharInfo> storedChar = new HashMap<>();

    /**
     * 构造函数创建一张透明空图等待添加字符
     */
    public CharPage() {
        img = new BufferedImage(PAGE_SIZE, PAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
    }

    public boolean addChar(Font font, String c) {
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
            GenCharInfo gci = _genCharImage(font, c);
            g.drawImage(gci.img, x, y, FONT_PIXEL_SIZE, FONT_PIXEL_SIZE, null);
            g.dispose();
            storedChar.put(c, gci.info);
            charCounter++;
            isDirty = true;
            return true;
        } catch (Exception e) {
            charCounter--;
            return false;
        }
    }

    private GenCharInfo _genCharImage(Font font, String c) {
        BufferedImage image = new BufferedImage(FONT_PIXEL_SIZE, FONT_PIXEL_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(font);
        g.setColor(Color.WHITE);

        FontMetrics metrics = g.getFontMetrics();
        TextLayout layout = new TextLayout(String.valueOf(c), font, g.getFontRenderContext());
        Rectangle2D bounds = layout.getBounds();

        int bx = (int) bounds.getX();
        int by = (int) bounds.getY(); // 左上角Y
        short up = (short) (FONT_PIXEL_SIZE * 0.2);
        short charWidth = (short) bounds.getWidth();
        short charHeight = (short) bounds.getHeight();
        short height = (short) (FONT_PIXEL_SIZE);

        // 水平居中 + 垂直基线对齐
        int x = 0;
        int y = FONT_PIXEL_SIZE - up;

        layout.draw(g, x, y);
        g.dispose();
        return new GenCharInfo(image, new CharInfo(this, charCounter, charWidth, height));
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

        isDirty = false;
    }

    public void renderCharAt(String c, double x, double y, float size) {
        CharInfo info = storedChar.get(c);
        double u1 = info.getU1(), u2 = info.getU2(), v1 = info.getV1(), v2 = info.getV2();
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBindTexture(GL_TEXTURE_2D, textureID);
        // 绘制
        glBegin(GL_QUADS);
        {
            // 左下
            glTexCoord2d(u1, v1);
            glVertex3d(x, y, 0);
            // 左上
            glTexCoord2d(u1, v2);
            glVertex3d(x, y+size, 0);
            // 右上
            glTexCoord2d(u2, v2);
            glVertex3d(x+size, y+size, 0);
            // 右下
            glTexCoord2d(u2, v1);
            glVertex3d(x+size, y, 0);
        }
        glEnd();
    }

    public void _renderDebugRect(double x, double y, float size) {
        // 保存当前颜色、线宽等状态
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glColor4f(1, 0, 0, 1);
        glBegin(GL_LINE_LOOP);
        {
            glVertex3d(x, y, -1);
            glVertex3d(x, y+size, -1);
            glVertex3d(x+size, y+size, -1);
            glVertex3d(x+size, y, -1);
        }
        glEnd();
        // 恢复之前保存的状态
        glPopAttrib();
    }

    public static class GenCharInfo {
        public BufferedImage img;
        public CharInfo info;
        public GenCharInfo(BufferedImage img, CharInfo info) {
            this.img = img;
            this.info = info;
        }
    }
}
