package club.heiqi.qz_uilib.fontsystem;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.client.RenderTickListener;
import club.heiqi.qz_uilib.fontsystem.shader.FrameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class CharPage {
    public static Logger LOG = LogManager.getLogger();

    public final int textureID;
    public final int textureSize, charSize;
    public final HashMap<Integer, CharInfo> chars = new HashMap<>();

    public CharPage(int textureSize, int charSize) {
        this.textureSize = textureSize;
        this.charSize = charSize;
        // 主纹理
        textureID = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA,
                textureSize,
                textureSize,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null);
        int lerpMode;
        switch (Config.lerpMode) {
            case 0 -> lerpMode = GL11.GL_NEAREST_MIPMAP_NEAREST;
            case 1 -> lerpMode = GL11.GL_LINEAR_MIPMAP_NEAREST;
            case 2 -> lerpMode = GL11.GL_NEAREST_MIPMAP_LINEAR;
            default -> lerpMode = GL11.GL_LINEAR_MIPMAP_LINEAR;
        }
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, lerpMode);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER);

        // 解绑
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // 新增逻辑：添加字符时还需要为这张纹理同步附加一个遮罩纹理，遮罩纹理通过高斯模糊生成
    public void addChar(ByteBuffer image, CharInfo charInfo) {
        int curCount = getCurCharCount();  // 纹理页中的字符数量
        int x, y;
        if (curCount == 0) {
            x = 0;
            y = 0;
        }
        else {
            int totalWidth = charSize * curCount;
            x = totalWidth % textureSize;
            y = (totalWidth / textureSize) * charSize;
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);
        GL11.glTexSubImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                x,
                y,
                charSize,
                charSize,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                image
        );

        charInfo.x = x;
        charInfo.y = y;

        chars.put(charInfo.codepoint, charInfo);

        // 解绑
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        // 延迟执行模糊
        blurPage();
        RenderTickListener.someTasks.add(this::blurPage);
    }

    public void blurPage() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GL11.glPopAttrib();
    }

    public boolean canAddChar() {
        return getCurCharCount() < getMaxCount();
    }

    public int getCurCharCount() {
        return chars.size();
    }

    public int getMaxCount() {
        return (textureSize * textureSize) / (charSize * charSize);
    }



    public void debug_saveImage() {
        BufferedImage mainImage = FrameUtils.getTextureImage(textureID, textureSize, textureSize);
        File savePath = new File("images");
        try {
            if (!savePath.exists()) {
                boolean mkdirs = savePath.mkdirs();
            }
            // 如果无法从文件名确定格式，使用PNG格式并添加后缀
            File pngFile = new File(savePath, textureID + "主.png");
            ImageIO.write(mainImage, "PNG", pngFile);

        } catch (IOException e) {
            LOG.error("Failed to save image: {}", savePath, e);
        } catch (IllegalArgumentException e) {
            LOG.error("Unsupported image format: {}", savePath, e);
        }
    }


    public boolean isCharInPage(int codepoint) {
        CharInfo charInfo = chars.get(codepoint);
        return charInfo != null;
    }

    public CharInfo getCharInfo(int codepoint) {
        CharInfo charInfo = chars.get(codepoint);
        if (charInfo != null) return charInfo;
        LOG.error("字符 【{}】 不在本纹理页 ({}) 中", new String(Character.toChars(codepoint)), textureID);
        // throw new RuntimeException("字符 【"+new String(Character.toChars(codepoint))+"】 不在本纹理页 ("+textureID+") 中");
        return new CharInfo(0,0,0, (int) Config.awtCharSize, (int) Config.awtCharSize,0,0,0);
    }

    @Override
    public int hashCode() {
        return textureID;
    }


    public AtomicBoolean isClosedManually = new AtomicBoolean(false);
    public void close() {
        if (textureID != 0)
            GL11.glDeleteTextures(textureID);
        chars.clear();
        isClosedManually.set(true);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (!isClosedManually.get()) {
            LOG.error("!!!  纹理页未正确释放  !!!");
            RenderTickListener.errorCleaners.add(this::close);
        }
    }
}
