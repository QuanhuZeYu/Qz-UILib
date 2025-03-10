package club.heiqi.qz_uilib.fontsystem;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.EncoderPNG;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Pixmap;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.types.Rect;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static club.heiqi.qz_uilib.fontsystem.FontManager.GLOBAL_CACHE;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;

public class CharPage {
    public static Logger LOG = LogManager.getLogger();
    public static int PAGE_SIZE = 2048;
    public static int GRID_SIZE = 64;
    public static int MAX_CHARS = (PAGE_SIZE*PAGE_SIZE)/(GRID_SIZE*GRID_SIZE);
    public AtomicInteger curChars = new AtomicInteger(0);
    public int textureID = -1;
    public AtomicBoolean isDirty = new AtomicBoolean(false);
    public AtomicBoolean isGenning = new AtomicBoolean(false);
    public final ReentrantLock lock = new ReentrantLock();
    public final FontManager fontManager;
    public volatile Surface surface;
    public volatile Canvas canvas;
    public volatile Paint paint;
    /** 该页存储的字符 */
    public volatile Map<String, CharInfo> cache = new ConcurrentHashMap<>();

    /**
     * 构造函数创建一张透明空图等待添加字符
     */
    public CharPage(FontManager fontManager) {
        this.fontManager = fontManager;
        surface = Surface.makeRaster(ImageInfo.makeN32Premul(PAGE_SIZE, PAGE_SIZE));
        canvas = surface.getCanvas();
        canvas.clear(0x00000000);
        paint = new Paint().setAntiAlias(true);
    }

    public boolean addChar(String t, Font font) {
        if (curChars.get() >= MAX_CHARS) return false;
        lock.lock();
        isGenning.set(true);
        CharInfo mark = new CharInfo(null, (short) 0, GRID_SIZE, GRID_SIZE);
        cache.put(t, mark);
        try {
            int 每行个数 = PAGE_SIZE / GRID_SIZE;
            int 行 = curChars.get() / 每行个数;
            int 列 = curChars.get() % 每行个数;
            int x = 列 * GRID_SIZE; // 左上角X
            int y = 行 * GRID_SIZE; // 左上角Y
            Surface surface1 = _genChar(t, font);
            curChars.getAndAdd(1);
            Image image = surface1.makeImageSnapshot();
            canvas.drawImage(image, x, y);
            surface.flushAndSubmit();
            surface.getCanvas().save();
        } catch (Exception e) {
            LOG.error("处理 {} 时出现异常！", t);
        } finally {
            isDirty.set(true);
            lock.unlock();
            isGenning.set(false);
            LOG.debug("{} 处理完毕", t);
        }
        return true;
    }

    /**
     * 一次性添加所有码点到本页
     * <p>
     * 注意！一次性添加的数量请勿超过 MAX_CHARS
     * @param codepoints 需要添加的码点
     */
    public void addChars(List<Integer> codepoints) {
        for (int codepoint : codepoints) {
            char[] chars = Character.toChars(codepoint);
            String t = new String(chars);
            Font font = fontManager.findValidFont(codepoint);
            GLOBAL_CACHE.add(t);
            addChar(t, font);
        }
    }

    public Surface _genChar(String t, Font font) { // 仅addChar可使用该方法
        // 1.创建画字体的Surface
        Surface surface = Surface.makeRaster(ImageInfo.makeN32Premul(GRID_SIZE, GRID_SIZE));
        Paint paint = new Paint().setColor(0xFFFFFFFF); // 白色画笔
        Canvas canvas = surface.getCanvas();
        canvas.clear(0x00000000); // 设置透明背景
        Rect rect = font.measureText(t); // 获取边界
        float left      = rect.getLeft();
        float top       = rect.getTop();
        float right     = rect.getRight();
        float width     = rect.getWidth();
        float height    = rect.getHeight();
        float x = 0;
        float y = GRID_SIZE-GRID_SIZE*0.2f;
        CharInfo c = new CharInfo(this, (short) curChars.get(), right, height);
        if (cache.replace(t, c) == null) {
            cache.put(t, c);
        }
        canvas.drawString(t, x, y, font, paint);
        return surface;
    }

    public void renderCharAt(String t, double x, double y, double z, float size) {
        CharInfo info = cache.get(t);
        if (info == null) return;
        double u1 = info.getU1(), u2 = info.getU2(), v1 = info.getV1(), v2 = info.getV2();
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND); // 确保混合已启用
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBindTexture(GL_TEXTURE_2D, textureID);
        // 绘制
        glBegin(GL_QUADS);
        {
            // 左下
            glTexCoord2d(u1, v1);
            glVertex3d(x, y, z);
            // 左上
            glTexCoord2d(u1, v2);
            glVertex3d(x, y+size, z);
            // 右上
            glTexCoord2d(u2, v2);
            glVertex3d(x+size, y+size, z);
            // 右下
            glTexCoord2d(u2, v1);
            glVertex3d(x+size, y, z);
        }
        glEnd();
    }

    public void renderDebugRect(double x, double y, double z, float size) {
        // 保存当前颜色、线宽等状态
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glDisable(GL_TEXTURE_2D);
        glColor4f(1, 0, 0, 1);
        glBegin(GL_LINE_LOOP);
        {
            glVertex3d(x, y, z);
            glVertex3d(x, y+size, z);
            glVertex3d(x+size, y+size, z);
            glVertex3d(x+size, y, z);
        }
        glEnd();
        // 恢复之前保存的状态
        glPopAttrib();
    }

    public boolean canAdd() {
        return (curChars.get() < MAX_CHARS) && !isGenning.get();
    }

    public void uploadGPU() {
        if (!isDirty.get()) return;
        lock.lock();
        try {
            // 删除旧纹理（确保线程安全）
            if (textureID != -1 && textureID != 0) {
                glDeleteTextures(textureID);
                textureID = -1; // 重置为无效值
            }
            // 获取图像原始像素数据
            ByteBuffer pixBuffer = ByteBuffer.allocateDirect(PAGE_SIZE*PAGE_SIZE*4);
            Pixmap pixmap = Pixmap.make(surface.getImageInfo(), pixBuffer, PAGE_SIZE*4);
            surface.readPixels(pixmap, 0, 0);
            // 创建新纹理
            textureID = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureID);
            // 设置纹理参数
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            // 上传纹理数据
            glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_RGBA,
                PAGE_SIZE,
                PAGE_SIZE,
                0,
                GL_RGBA,
                GL_UNSIGNED_BYTE,
                pixmap.getBuffer()
            );
            // 生成 Mipmap
            glGenerateMipmap(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, 0);
        } finally {
            // 标记为已同步
            isDirty.set(false);
            lock.unlock();
        }
    }

    public void savePNG(String name) {
        name = name + ".png";
        File outFile = new File(System.getProperty("user.dir"), "图像输出/" + name);
        if (!outFile.getParentFile().exists()) {
            outFile.getParentFile().mkdirs();
        }
        Image image = surface.makeImageSnapshot();
        try {
            Files.write(outFile.toPath(), EncoderPNG.encode(image).getBytes());
        } catch (IOException e) {
            LOG.error("{} 保存失败", outFile.getAbsolutePath());
        }
        LOG.debug("文件已保存至: " + outFile.getAbsolutePath());
    }

    public CharInfo getCharInfo(String t) {
        return cache.get(t);
    }
}
